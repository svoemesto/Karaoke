package com.svoemesto.karaokeapp

import org.slf4j.LoggerFactory
import java.sql.DriverManager

/**
 * Базовый класс подключения к БД — кеширует по одному физическому
 * `java.sql.Connection` **на поток выполнения** (не одно общее на весь
 * инстанс/приложение, см. specs/087-fix-shared-db-connection).
 *
 * До этой фичи `connection` было общим `@Volatile`-полем: один физический
 * канал, которым конкурентно пользовались и HTTP-потоки, и поток очереди
 * заданий — PostgreSQL JDBC `Connection` не рассчитан на параллельное
 * использование из разных потоков, что приводило к протокольным сбоям
 * (`SocketTimeoutException`/«соединение уже закрыто») и роняло главный цикл
 * очереди. Теперь у каждого потока — своя запись в [ThreadLocal], поэтому
 * потоки не мешают друг другу.
 *
 * Self-healing (specs/236-fix-karaoke-connection-self-healing): если закешированное
 * для текущего потока соединение отсутствует/закрыто/невалидно — пересоздаётся
 * прозрачно для вызывающего кода (сигнатура [getConnection] не менялась). Начиная с
 * этой спеки, при входе в ветку пересоздания ThreadLocal **сбрасывается** до попытки
 * `DriverManager.getConnection(...)` — иначе при исключении в ThreadLocal оставалось
 * закрытое соединение от предыдущего успешного открытия и следующий вызов в этом же
 * потоке пропускал ветку пересоздания, возвращая мёртвый объект (см. KDoc метода).
 *
 * **Логирование сбоев подключения/закрытия (specs/234-db-sync-connection-leak):**
 * в дополнение к существующему `println` (для stdout контейнера и обратной
 * совместимости) добавлен SLF4J `log.warn(...)` с placeholder'ами
 * `target={} thread={} cause={}` — для структурированного парсинга в Kibana/Loki
 * и пост-инцидентной диагностики. Существующие 174+ вызывающих мест
 * `getConnection()` НЕ затронуты (контракт `Connection?` сохранён).
 *
 * @see archive/docs/features/async-process-queue.md
 * @see specs/234-db-sync-connection-leak фикс утечки JDBC-соединений + структурированный warn
 * @see specs/236-fix-karaoke-connection-self-healing сброс ThreadLocal при неудачной попытке + urlHost в warn
 */
abstract class KaraokeConnection(
    open val url: String,
    open val username: String,
    open val password: String,
    open val name: String,
) {
    // SLF4J logger (specs/234-db-sync-connection-leak). Категория = полное имя класса.
    // Spring Boot по умолчанию использует Logback (уже в classpath), без новых зависимостей.
    private val log = LoggerFactory.getLogger(KaraokeConnection::class.java)

    private val threadLocalConnection = ThreadLocal<java.sql.Connection?>()

    /**
     * BR-274: время последней успешной валидации соединения (мс, системное время).
     *
     * `conn.isValid(3)` в pgjdbc — это НЕ локальная проверка флага, а отдельный
     * запрос к серверу (пустой query) с временной подменой network timeout. Если
     * вызывать его на КАЖДОМ `getConnection()`, то каждое обращение к БД
     * удорожается ещё одним round-trip — а на удалённой БД (`Connection.remote()`)
     * это десятки тысяч лишних запросов за прогон.
     *
     * Теперь соединение валидируется:
     *  - при открытии (всегда — это единственный раз, когда нужно проверить живость);
     *  - после `SQLException` (соединение помечается «битым» и переоткрывается);
     *  - при простое дольше [CONNECTION_VALIDATION_IDLE_MS] (5 минут) — чтобы
     *    «протухшее» соединение, о котором никто не знает (например, после
     *    перезапуска БД), всё-таки обнаруживалось.
     */
    private val threadLocalLastValidationMs = ThreadLocal<Long>()

    /**
     * BR-274: соединение помечено «битым» (после `SQLException`) — следующий
     * `getConnection()` переоткроет его, не дожидаясь таймера.
     */
    private val threadLocalPoisoned = ThreadLocal<Boolean>()

    /** Как долго соединение может не использоваться, прежде чем будет проверено снова. */
    private val connectionValidationIdleMs: Long = CONNECTION_VALIDATION_IDLE_MS

    /** BR-274: помечает соединение текущего потока «битым» — вызывать после `SQLException`. */
    fun markConnectionBroken() {
        threadLocalPoisoned.set(true)
    }

    /**
     * BR-274: нужно ли валидировать закешированное соединение.
     *
     * `true` — соединения нет; соединение закрыто (`isClosed` — локальная
     * проверка флага, не round-trip); соединение помечено битым (после
     * `SQLException`); прошло больше [connectionValidationIdleMs] с последней
     * успешной валидации.
     */
    private fun needsValidation(conn: java.sql.Connection?): Boolean {
        if (conn == null) return true
        if (conn.isClosed) return true
        if (threadLocalPoisoned.get() == true) return true
        val lastValidation = threadLocalLastValidationMs.get() ?: return true
        return (System.currentTimeMillis() - lastValidation) > connectionValidationIdleMs
    }

    /**
     * BR-274: обёртка соединения, которая помечает его «битым» при `SQLException`.
     *
     * Это то, что позволяет убрать `isValid(3)` из горячего пути, НЕ потеряв
     * self-healing (specs/236): как только запрос упал с `SQLException` (соединение
     * умерло / сеть отвалилась), следующий вызов `getConnection()` переоткроет
     * соединение. Пока ни одного `SQLException` не было, соединение считается
     * живым — проверять его сервером не нужно.
     */
    private fun wrapForBrokenDetection(connection: java.sql.Connection): java.sql.Connection =
        java.lang.reflect.Proxy
            .newProxyInstance(
                java.sql.Connection::class.java.classLoader,
                arrayOf(java.sql.Connection::class.java),
            ) { proxy, method, args ->
                try {
                    method.invoke(connection, *(args ?: emptyArray()))
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    val cause = e.targetException
                    if (cause is java.sql.SQLException) {
                        // BR-274: помечаем соединение битым — следующий вызов
                        // getConnection() переоткроет его.
                        threadLocalPoisoned.set(true)
                    }
                    throw cause
                }
            } as java.sql.Connection

    /**
     * Возвращает рабочее соединение для текущего потока, пересоздавая его
     * при необходимости (см. KDoc класса).
     *
     * Метод `open` — шов для тестов: наследник может подменить источник соединения
     * (например, вернуть поддельное JDBC-соединение) и не зависеть от живой БД.
     * Контракт для продакшн-кода не меняется: физическое соединение кэшируется
     * в [threadLocalConnection] на поток.
     */
    open fun getConnection(): java.sql.Connection? {
        val conn = threadLocalConnection.get()
        // BR-274: `isValid(3)` — это отдельный запрос к серверу (пустой query) в
        // pgjdbc, а не локальная проверка флага. Вызывать его на КАЖДОМ
        // обращении — значит платить лишним round-trip за каждый SELECT/UPDATE
        // (на удалённой БД это десятки тысяч лишних запросов за прогон).
        //
        // Теперь соединение проверяется только когда это реально нужно:
        // при открытии, после «битого» состояния (SQLException) и после
        // простоя дольше CONNECTION_VALIDATION_IDLE_MS.
        if (needsValidation(conn)) {
            // specs/236-fix-karaoke-connection-self-healing: при входе в ветку пересоздания
            // обязательно сбрасываем ThreadLocal. Без этого, если DriverManager.getConnection
            // бросит исключение, в ThreadLocal останется закрытое/протухшее соединение от
            // предыдущего успешного открытия — следующий вызов в этом же потоке пропустит
            // ветку пересоздания (conn != null && !conn.isClosed) и вернёт мёртвый объект →
            // PSQLException: Соединение уже было закрыто на loadList/createStatement.
            threadLocalConnection.set(null)
            threadLocalPoisoned.set(false)
            Class.forName("org.postgresql.Driver")
            try {
                val opened = DriverManager.getConnection(url, username, password)
                // BR-274: соединение оборачивается в прокси, который помечает его
                // «битым» при SQLException — это заменяет горячую проверку isValid.
                threadLocalConnection.set(wrapForBrokenDetection(opened))
                // BR-274: валидация выполнена — метка времени обновляется, чтобы
                // следующие обращения не платили ещё за один round-trip.
                threadLocalLastValidationMs.set(System.currentTimeMillis())
            } catch (e: Exception) {
                // BR-275: в лог уходит САМ объект исключения (последний аргумент
                // SLF4J) — так сохраняются стек, класс и cause-цепочка. Раньше
                // логировался только `e.message`, и в stdout/централизованных
                // логах не оставалось ни стека, ни причины, ни типа исключения.
                //
                // Сохраняем println для обратной совместимости (docker logs читают
                // stdout), но с полным текстом исключения.
                println("KaraokeConnection getConnection Exception: ${e.message}")
                // FR-004 spec.md: структурированный SLF4J warn для диагностики.
                // specs/236: добавляем urlHost (без credentials) — сразу видно, к какому
                // хосту/порту шла попытка (полезно при пустом DB_REMOTE_HOST в env).
                log.warn(
                    "KaraokeConnection connect failure target={} thread={} urlHost={} cause={}",
                    name,
                    Thread.currentThread().name,
                    url.substringAfter("://").substringBefore("/"),
                    e.message ?: "unknown",
                    e,
                )
            }
        }
        return threadLocalConnection.get()
    }

    /**
     * Явно закрывает и освобождает физическое соединение **текущего потока**, если оно
     * было открыто (specs/091-fix-connection-leak).
     *
     * **Когда вызывать (обязательно):** в `finally` одноразового/недолговечного потока —
     * потока, который больше НИКОГДА не обратится к БД и потому не сможет переиспользовать
     * либо обнаружить как невалидное своё thread-local соединение. Иначе кеш [ThreadLocal]
     * в [getConnection] держит физический канал открытым до недетерминированной
     * финализации сборщиком мусора (а при `max_connections` PostgreSQL это отказ БД для
     * всего приложения). Примеры одноразовых потоков:
     *
     * - `KaraokeProcessThread` — создаётся заново на каждое задание очереди;
     * - поток главного цикла очереди `KaraokeProcessWorker` — создаётся заново на КАЖДЫЙ
     *   `start()` и умирает при остановке очереди (то есть переиспользовать ему нечего);
     * - фоновые задачи API-контроллеров (`thread { … }`).
     *
     * **Когда НЕ вызывать:** из переиспользуемых/долгоживущих потоков — пул потоков
     * Tomcat (HTTP-запросы), потоки `@Scheduled`, Spring-пулы. Там закрытие разрушит
     * thread-local кеш соединения и заставит открывать новое физическое соединение
     * (TCP + auth handshake + `isValid(3)`-проба) на каждое обращение к БД, отменяя
     * смысл specs/087-fix-shared-db-connection.
     *
     * **Канонический способ** для одноразовых потоков — `startOneShotThread`
     * (`com.svoemesto.karaokeapp.services.startOneShotThread`): он сам оборачивает тело
     * в `try/finally` и закрывает соединение потока при любом исходе (успех/исключение).
     *
     * Метод `open` — шов для тестов: наследник может зафиксировать факт закрытия.
     */
    open fun closeThreadConnection() {
        val conn = threadLocalConnection.get() ?: return
        try {
            conn.close()
        } catch (e: Exception) {
            // Сохраняем println для обратной совместимости (docker logs читают stdout).
            println("KaraokeConnection closeThreadConnection Exception: ${e.message}")
            // Симметричный SLF4J warn для диагностики сбоев закрытия.
            log.warn(
                "KaraokeConnection closeThreadConnection failure target={} thread={} cause={}",
                name,
                Thread.currentThread().name,
                e.message ?: "unknown",
            )
        } finally {
            threadLocalConnection.remove()
        }
    }
}

/**
 * BR-274: как долго закешированное соединение может не использоваться, прежде чем
 * будет проверено снова (`isValid`).
 *
 * Раньше проверка выполнялась на КАЖДОМ вызове `getConnection()`, а `isValid(3)` в
 * pgjdbc — это отдельный запрос к серверу (пустой query). Для удалённой БД
 * (`Connection.remote()`) это десятки тысяч лишних round-trip за прогон.
 *
 * 5 минут — компромисс: «протухшее» соединение (перезапуск БД, обрыв сети)
 * обнаруживается за минуты, а не за миллисекунды.
 */
internal const val CONNECTION_VALIDATION_IDLE_MS: Long = 5 * 60 * 1000

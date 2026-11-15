package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.db.DbPool
import com.svoemesto.karaokeapp.db.DbPoolSettings
import com.svoemesto.karaokeapp.db.PooledHandle
import com.svoemesto.karaokeapp.tunnel.CertFailureClassifier
import com.svoemesto.karaokeapp.tunnel.CertFailureReason
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

    private companion object {
        /** Категория логов соединения (как раньше — полное имя класса). */
        const val CATEGORY = "com.svoemesto.karaokeapp.KaraokeConnection"
    }

    private val threadLocalConnection = ThreadLocal<java.sql.Connection?>()

    /**
     * Handle (аренда канала пула) текущего потока — только в пуловом режиме.
     *
     * В режиме без пула (по умолчанию) остаётся `null`, и работает прежний
     * путь: физическое соединение в [threadLocalConnection].
     */
    private val threadLocalHandle = ThreadLocal<PooledHandle?>()

    /**
     * Пул соединений этой роли БД; `null` — пул выключен (прежнее поведение).
     *
     * Создаётся **лениво** (при первом обращении к БД), а не в конструкторе:
     * 10 тестовых подмен `KaraokeConnection` переопределяют `getConnection()`
     * и не должны создавать ни пула, ни сетевых подключений.
     *
     * Пул принадлежит **экземпляру** соединения, а не процессу: у каждой
     * роли (`LOCAL`/`SERVER`) свой набор каналов и свой потолок (FR-001).
     */
    @Volatile
    private var poolInstance: DbPool? = null

    /** Пул уже создан? (создание — ленивое, под блокировкой) */
    @Volatile
    private var poolCreated: Boolean = false

    /**
     * Пул соединений для этой роли БД (создаётся при первом обращении).
     *
     * `null`, если пул выключен ([DbPoolSettings.enabled]) — тогда работает
     * прежний путь через `DriverManager`.
     */
    protected open fun pool(): DbPool? {
        if (poolCreated) return poolInstance
        synchronized(this) {
            if (poolCreated) return poolInstance
            val config = DbPoolSettings.configFor(name)
            // Настройки ещё не применены (Spring-контекст не инициализирован) —
            // НЕ запоминаем решение: пул создастся при следующем обращении.
            if (config == null && !DbPoolSettings.resolved) return null
            poolInstance =
                if (config == null) {
                    null
                } else {
                    DbPool(
                        target = name,
                        jdbcUrl = url,
                        username = username,
                        password = password,
                        config = config,
                    )
                }
            poolCreated = true
            return poolInstance
        }
    }

    /**
     * Реестр физических соединений этого экземпляра: соединение → время последнего
     * использования (мс).
     *
     * Зачем: соединение живёт в [ThreadLocal], то есть привязано к потоку. Потоки
     * пула (Tomcat, `@Scheduled`) живут долго — их соединения переиспользуются, и это
     * нормально. Но потоки `ForkJoinPool` (`parallel-*`, в т.ч. `Dispatchers.IO`) и
     * прочие недолговечные потоки создаются и умирают, а их thread-local соединение
     * НЕ закрывается: `closeThreadConnection()` из чужого потока не вызвать, а
     * финализатор JDBC недетерминирован. Так соединения накапливаются: инцидент
     * 2026-11-12 — 241 простаивающее соединение к LOCAL из 300 (`FATAL: sorry, too
     * many clients already`), очередь встала.
     *
     * Реестр даёт то, чего у [ThreadLocal] нет: увидеть ВСЕ открытые соединения
     * экземпляра и закрыть простаивающие дольше порога ([reapIdleConnections]).
     * Закрытие из другого потока безопасно: владелец обнаружит `isClosed` в
     * [needsValidation] и переоткроет соединение — штатный путь self-healing.
     */
    private val openConnections =
        java.util.concurrent.ConcurrentHashMap<java.sql.Connection, java.util.concurrent.atomic.AtomicLong>()

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
            val pool = pool()
            if (pool != null) {
                // Пул (спека 778): берём АРЕНДУ канала, а не физическое соединение.
                // Handle кэшируется в ThreadLocal — контракт «соединение на поток»
                // сохраняется (ADR-0009), но каналом владеет пул.
                try {
                    // Старый handle освобождаем ДО взятия нового: иначе слот
                    // пула утекал бы на каждом переоткрытии (битое соединение,
                    // истёкший простой) — а слот конечен.
                    threadLocalHandle.get()?.release()
                    threadLocalHandle.remove()
                    val handle = pool.borrowHandle()
                    threadLocalHandle.set(handle)
                    // BR-274: прокси «битого» состояния поверх handle'а — SQLException
                    // помечает handle, следующий getConnection() возьмёт новый.
                    val wrapped = wrapForBrokenDetection(handle.asConnection())
                    threadLocalConnection.set(wrapped)
                    // BR-274: валидация выполнена — метка времени обновляется, чтобы
                    // следующие обращения не платили ещё за один round-trip.
                    threadLocalLastValidationMs.set(System.currentTimeMillis())
                    return wrapped
                } catch (e: Exception) {
                    logConnectionFailure(e)
                    return null
                }
            }
            Class.forName("org.postgresql.Driver")
            try {
                val opened = DriverManager.getConnection(url, username, password)
                // BR-274: соединение оборачивается в прокси, который помечает его
                // «битым» при SQLException — это заменяет горячую проверку isValid.
                val wrapped = wrapForBrokenDetection(opened)
                threadLocalConnection.set(wrapped)
                openConnections[wrapped] =
                    java.util.concurrent.atomic
                        .AtomicLong(System.currentTimeMillis())
                // BR-274: валидация выполнена — метка времени обновляется, чтобы
                // следующие обращения не платили ещё за один round-trip.
                threadLocalLastValidationMs.set(System.currentTimeMillis())
            } catch (e: Exception) {
                logConnectionFailure(e)
            }
        }
        val cached = threadLocalConnection.get()
        if (cached != null) {
            openConnections[cached]?.set(System.currentTimeMillis())
        }
        return cached
    }

    /**
     * Пишет отказ подключения в журнал (BR-275, спека 733).
     *
     * В лог уходит **САМ объект исключения** (последний аргумент SLF4J) — так
     * сохраняются стек, класс и cause-цепочка. Раньше логировался только
     * `e.message`, и в stdout/централизованных логах не оставалось ни стека, ни
     * причины, ни типа исключения.
     *
     * Отдельный метод — потому что одинаково нужен обоим путям: через пул
     * (спека 778) и через прямой `DriverManager`.
     *
     * @param e исключение подключения
     */
    private fun logConnectionFailure(e: Exception) {
        // BR-275: в лог уходит САМ объект исключения (последний аргумент
        // SLF4J) — так сохраняются стек, класс и cause-цепочка. Раньше
        // логировался только `e.message`, и в stdout/централизованных
        // логах не оставалось ни стека, ни причины, ни типа исключения.
        //
        // Неинформативный println убран (2026-11-12): он печатал только текст
        // исключения — без цели (LOCAL/SERVER), хоста и кода события, поэтому
        // по нему нельзя было понять, к какой базе не удалось подключиться.
        // Строка ниже (AppLog) содержит target, host и reason и идёт в тот же
        // stdout через logback — дублировать её не нужно.
        // FR-004 spec.md: структурированный SLF4J warn для диагностики.
        // specs/236: добавляем urlHost (без credentials) — сразу видно, к какому
        // хосту/порту шла попытка (полезно при пустом DB_REMOTE_HOST в env).
        // KARAOKE-387: одна строка с кодом вместо стека Spring/Tomcat.
        AppLog.warn(
            category = CATEGORY,
            code = "DB-CONNECT-001",
            event = "db.connect.failed",
            fields =
                mapOf(
                    "target" to name,
                    "thread" to Thread.currentThread().name,
                    "host" to url.substringAfter("://").substringBefore("/"),
                    "reason" to (e.message ?: "unknown"),
                ),
            error = e,
        )
        // Спека 733 (FR-016): отказ по сертификату MUST попадать в наблюдаемость
        // как событие с указанием причины (просрочен / недоверенный / отозван),
        // а не как безымянная ошибка соединения. Тот же отказ порождает и
        // DB-CONNECT-001 — здесь появляется код участника «сертификат».
        val certReason = CertFailureClassifier.classify(e)
        if (certReason != CertFailureReason.UNKNOWN) {
            AppLog.warn(
                category = CATEGORY,
                code = "DB-CERT-001",
                event = "db.cert.rejected",
                fields =
                    mapOf(
                        "target" to name,
                        "certReason" to certReason.code,
                        "host" to url.substringAfter("://").substringBefore("/"),
                    ),
                error = e,
            )
        }
    }

    /**
     * Закрывает соединения экземпляра, простаивающие дольше [maxIdleMs], и возвращает
     * число закрытых.
     *
     * Вызывается периодически (см. `LocalConnectionReaper`). Уборка нужна для
     * соединений, чьи потоки умерли: их никто не закроет, а место в `max_connections`
     * они занимают (инцидент 2026-11-12).
     *
     * Закрытие чужого соединения не ломает владельца: если поток ещё жив, следующий
     * `getConnection()` увидит `isClosed` и откроет новое — штатный путь self-healing
     * (specs/236), а не ошибка.
     */
    open fun reapIdleConnections(maxIdleMs: Long): Int {
        // Пул (спека 778): убираются АРЕНДЫ мёртвых владельцев, а не каналы.
        // Физические каналы закрывает пул (FR-013).
        val pool = pool()
        if (pool != null) {
            return pool.reapDeadOwnerHandles()
        }
        val now = System.currentTimeMillis()
        var closed = 0
        val iterator = openConnections.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.get() <= maxIdleMs) continue
            iterator.remove()
            try {
                entry.key.close()
            } catch (_: Exception) {
                // соединение уже мертво — считаем освобождённым
            }
            closed++
        }
        return closed
    }

    /**
     * Сообщает о handle'ах, удерживаемых живым владельцем дольше порога
     * (FR-009, событие `DB-POOL-003`).
     *
     * Наблюдательное событие: ничего не отбирается и ничего не закрывается —
     * показывается только место в коде, где соединение удерживается дольше
     * положенного. Без пула события нет: там соединением владеет поток, и его
     * удержание — нормальная часть контракта ADR-0009.
     *
     * @param thresholdMs порог удержания
     * @return сколько handle'ов сообщено (0 — пул выключен)
     */
    open fun reportLongHeldHandles(thresholdMs: Long): Int {
        val pool = pool() ?: return 0
        return pool.reportLongHeldHandles(thresholdMs)
    }

    /**
     * Handle текущего потока (только в пуловом режиме).
     *
     * Шов для тестов: позволяет взять handle в отдельном потоке и проверить,
     * что рейпер вернёт аренду мёртвого владельца. В прикладном коде не
     * используется.
     */
    protected open fun currentHandle(): PooledHandle? = threadLocalHandle.get()

    /**
     * Сколько соединений экземпляра сейчас занято (для наблюдаемости).
     *
     * Без пула — число физических соединений в реестре; с пулом — число занятых
     * аренд (каналов, выданных потокам).
     */
    open fun openConnectionCount(): Int {
        val pool = pool()
        if (pool != null) return pool.activeConnections()
        return openConnections.size
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
        // Пул (спека 778): handle возвращается в пул — канал НЕ закрывается,
        // он общий (FR-002). Физическое соединение закрывает сам пул.
        val handle = threadLocalHandle.get()
        if (handle != null) {
            threadLocalHandle.remove()
            threadLocalConnection.remove()
            handle.release()
            return
        }
        val conn = threadLocalConnection.get() ?: return
        try {
            conn.close()
        } catch (e: Exception) {
            // Сохраняем println для обратной совместимости (docker logs читают stdout).
            println("KaraokeConnection closeThreadConnection Exception: ${e.message}")
            // Симметричный SLF4J warn для диагностики сбоев закрытия.
            AppLog.warn(
                category = CATEGORY,
                code = "DB-CLOSE-001",
                event = "db.close.failed",
                fields =
                    mapOf(
                        "target" to name,
                        "thread" to Thread.currentThread().name,
                        "reason" to (e.message ?: "unknown"),
                    ),
                error = e,
            )
        } finally {
            openConnections.remove(conn)
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

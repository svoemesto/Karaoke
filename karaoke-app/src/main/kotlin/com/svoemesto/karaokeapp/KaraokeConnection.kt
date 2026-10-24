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
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            // specs/236-fix-karaoke-connection-self-healing: при входе в ветку пересоздания
            // обязательно сбрасываем ThreadLocal. Без этого, если DriverManager.getConnection
            // бросит исключение, в ThreadLocal останется закрытое/протухшее соединение от
            // предыдущего успешного открытия — следующий вызов в этом же потоке пропустит
            // ветку пересоздания (conn != null && !conn.isClosed) и вернёт мёртвый объект →
            // PSQLException: Соединение уже было закрыто на loadList/createStatement.
            threadLocalConnection.set(null)
            Class.forName("org.postgresql.Driver")
            try {
                threadLocalConnection.set(DriverManager.getConnection(url, username, password))
            } catch (e: Exception) {
                // Сохраняем println для обратной совместимости (docker logs читают stdout).
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

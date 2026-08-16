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
 * Self-healing не меняется: если закешированное для текущего потока
 * соединение отсутствует/закрыто/невалидно — пересоздаётся прозрачно для
 * вызывающего кода (сигнатура [getConnection] не менялась).
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
     */
    fun getConnection(): java.sql.Connection? {
        val conn = threadLocalConnection.get()
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            Class.forName("org.postgresql.Driver")
            try {
                threadLocalConnection.set(DriverManager.getConnection(url, username, password))
            } catch (e: Exception) {
                // Сохраняем println для обратной совместимости (docker logs читают stdout).
                println("KaraokeConnection getConnection Exception: ${e.message}")
                // FR-004 spec.md: структурированный SLF4J warn для диагностики.
                log.warn(
                    "KaraokeConnection connect failure target={} thread={} cause={}",
                    name,
                    Thread.currentThread().name,
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
     * Вызывать ТОЛЬКО в конце работы одноразовых/недолговечных потоков (например,
     * `KaraokeProcessThread` — создаётся заново на каждое задание очереди и никогда не
     * переиспользуется) — иначе кеш [ThreadLocal] в [getConnection] держит соединение
     * открытым вечно, так как такой поток больше никогда не вызовет `getConnection()`
     * повторно, чтобы его переиспользовать или обнаружить как невалидное.
     *
     * НЕ вызывать из переиспользуемых/долгоживущих потоков (пул потоков Tomcat, главный
     * цикл `KaraokeProcessWorker.doStart()`) — это разрушит их кеш соединения и заставит
     * открывать новое соединение на каждое обращение к БД, отменяя смысл
     * specs/087-fix-shared-db-connection.
     */
    fun closeThreadConnection() {
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

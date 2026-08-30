package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Буферизация INSERT в `tbl_events` (FR-109 спека 274-events-batch-insert).
 *
 * На пиках нагрузки (пользователь листает «Закрома», каждая песня триггерит
 * несколько событий — показ, player, link clicks) — десятки INSERT/сек на одного клиента.
 * `SamplingFilter` + `DedupCache` уже уменьшают поток через sampling 1/N и dedup TTL 30 сек
 * (см. `archive/docs/features/site-traffic-resilience.md`), но сами INSERT всё равно идут
 * по одному — round-trip к БД на каждое событие.
 *
 * Этот сервис буферизует события в `ConcurrentLinkedQueue` и делает flush через JDBC
 * `addBatch()` + `executeBatch()` каждые `FLUSH_INTERVAL_MS` (дефолт 5 сек). Это снижает
 * RPS INSERT к PostgreSQL на ≥80% при типичной нагрузке (50 INSERT/5 сек → 1 batch).
 *
 * Kill-switch: `karaoke.web.events.batch-enabled` через `KaraokeProperties.getBoolean`
 * (default `false` — opt-in). На текущем проде INSERT как раньше; администратор может
 * включить после наблюдения baseline. Это Tier-3 P2, не блокер для текущей нагрузки.
 *
 * **Fail-open**: при ошибке batch INSERT буфер очищается (потеря событий допустима для
 * логирования — это метрики вовлечённости, не транзакционные данные). Ошибка логируется
 * через SLF4J (как в текущем `MainController.insertEvent`).
 *
 * **Backpressure**: при переполнении буфера (> `MAX_BUFFER_SIZE`) срабатывает немедленный
 * flush в том же потоке, что предотвращает бесконтрольный рост памяти при экстремальной
 * нагрузке.
 *
 * Thread-safe: `ConcurrentLinkedQueue` для буфера + `AtomicBoolean flushing` для
 * предотвращения двойного flush. JDBC `addBatch()` + `executeBatch()` для batch INSERT.
 *
 * @see specs/274-events-batch-insert FR-001..FR-007
 * @see specs/241-db-storage-perf-audit FR-109
 * @see DedupCache sister service (паттерн ConcurrentHashMap + lazy cleanup)
 * @see SamplingFilter (sampling 1/N + dedup — уменьшает поток ДО EventsBuffer)
 */
@Service
class EventsBuffer {
    /**
     * Immutable запись события для буфера.
     *
     * `fieldsValues` — список пар `(column_name, value)`, как формируется в
     * [com.svoemesto.karaokeweb.controllers.MainController.doRegisterEvent.insertEvent].
     * Дополнительные поля (`client_ip`, `user_agent`, `anon_id`, `site_user_id`)
     * добавляются в [buildInsertSql] из полей record.
     *
     * @param fieldsValues пары (column_name, value) — без служебных полей (добавляются в buildInsertSql).
     * @param eventType тип события (для логирования при ошибке).
     * @param clientIp IP клиента (из [com.svoemesto.karaokeweb.ClientIpResolver]).
     * @param userAgent User-Agent HTTP-заголовок (может быть null).
     * @param anonId анонимный идентификатор (UUID, может быть null).
     * @param siteUserId ID залогиненного пользователя (0 для анонимов).
     */
    data class EventRecord(
        val fieldsValues: List<Pair<String, Any>>,
        val eventType: String,
        val clientIp: String?,
        val userAgent: String?,
        val anonId: String?,
        val siteUserId: Long,
    )

    companion object {
        private val log = LoggerFactory.getLogger(EventsBuffer::class.java)

        /** Максимальный размер буфера перед backpressure flush (FR-004). */
        private const val DEFAULT_MAX_BUFFER_SIZE = 500

        /** Интервал `@Scheduled` flush в миллисекундах (FR-003). */
        private const val DEFAULT_FLUSH_INTERVAL_MS = 5000L

        /** Ключ kill-switch в [KaraokeProperties] (FR-002). */
        private const val KARAOKE_PROPERTY_BATCH_ENABLED = "karaoke.web.events.batch-enabled"

        /** Ключ размера буфера в [KaraokeProperties] (FR-004). */
        private const val KARAOKE_PROPERTY_MAX_BUFFER_SIZE = "karaoke.web.events.batch-max-buffer-size"

        /**
         * SQL-формирование идентично [com.svoemesto.karaokeweb.controllers.MainController.doRegisterEvent.insertEvent].
         * Каждое событие escape'ится индивидуально ПЕРЕД добавлением в SQL — это per-event
         * семантика, не зависит от flush.
         *
         * @param fieldsValues пары (column, value), включая client_ip/user_agent/anon_id/site_user_id.
         * @return готовый SQL string для `executeUpdate()` / `executeBatch()`.
         */
        internal fun buildInsertSql(fieldsValues: List<Pair<String, Any>>): String =
            "INSERT INTO tbl_events (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
                fieldsValues.joinToString(", ") { (field, value) ->
                    when {
                        value is Long -> "$value"
                        // referer — это URL (document.referrer). rightFileName() искажает его (заменяет
                        // ':' на '-' → 'https-//...'), ломая ссылку и агрегацию источников. Санируем только
                        // SQL-кавычку (значение недоверенное — приходит с клиента), не искажая содержимое.
                        field == "referer" -> "'${value.toString().replace("'", "''")}'"
                        else -> "'${value.toString().rightFileName()}'"
                    }
                }
            })"
    }

    private val buffer = ConcurrentLinkedQueue<EventRecord>()
    private val flushing = AtomicBoolean(false)

    /**
     * Добавляет событие в буфер или делает sync INSERT (если батчинг выключен
     * или буфер переполнен → backpressure).
     *
     * @param record событие для INSERT в `tbl_events` (см. [EventRecord]).
     * @return `true` если событие принято (в буфер или sync INSERT выполнен), `false` если ошибка БД.
     *
     * @see specs/274-events-batch-insert FR-002, FR-006
     */
    fun add(record: EventRecord): Boolean {
        if (!isEnabled()) {
            return executeSingle(record)
        }
        buffer.add(record)
        if (buffer.size >= maxBufferSize()) {
            // FR-004: backpressure — flush немедленно в том же потоке.
            flush()
        }
        return true
    }

    /**
     * Flush буфера через JDBC batch INSERT. Вызывается по `@Scheduled` каждые
     * `FLUSH_INTERVAL_MS` или при переполнении (backpressure из [add]).
     *
     * Алгоритм:
     * 1. `compareAndSet(false, true)` — предотвращаем двойной flush.
     * 2. Drain буфера (до `MAX_BUFFER_SIZE` записей за один flush).
     * 3. Если batch непустой — `executeBatch(batch)`.
     * 4. При ошибке — `buffer.clear()` (FR-005, fail-open) + SLF4J warn.
     *
     * @see specs/274-events-batch-insert FR-003, FR-005
     */
    @Scheduled(fixedDelayString = "\${karaoke.web.events.batch-flush-interval-ms:5000}")
    fun flush() {
        // Уже идёт flush в другом потоке — выходим.
        if (!flushing.compareAndSet(false, true)) return
        val started = System.currentTimeMillis()
        try {
            val batch = mutableListOf<EventRecord>()
            val maxSize = maxBufferSize()
            while (batch.size < maxSize) {
                val item = buffer.poll() ?: break
                batch.add(item)
            }
            if (batch.isEmpty()) return
            executeBatch(batch)
            log.info(
                "[EventsBuffer] flushed {} events in {} ms (buffer remaining: {})",
                batch.size,
                System.currentTimeMillis() - started,
                buffer.size,
            )
        } catch (e: Exception) {
            // FR-005: fail-open — буфер очищается, события теряются (допустимо для логирования).
            log.warn("[EventsBuffer] flush error ({} events lost): {}", buffer.size, e.message, e)
            buffer.clear()
        } finally {
            flushing.set(false)
        }
    }

    /**
     * Выполняет batch INSERT для списка записей. Каждая запись INSERT'ится через
     * `prepareStatement + executeUpdate` (не `addBatch` на уровне PreparedStatement,
     * т.к. SQL у разных eventType разный — `rightFileName()` escape зависит от `field`).
     *
     * Используется одно соединение на весь batch — close() в finally.
     *
     * @param batch список событий для INSERT.
     * @throws SQLException если ошибка БД (вызывающий код ловит и делает fail-open).
     */
    private fun executeBatch(batch: List<EventRecord>) {
        val connection =
            requireNotNull(WORKING_DATABASE.getConnection()) {
                "Нет соединения с БД (${WORKING_DATABASE.name})"
            }
        try {
            for (record in batch) {
                val fields = buildFieldsList(record)
                val sql = buildInsertSql(fields)
                connection.prepareStatement(sql).use { ps ->
                    ps.executeUpdate()
                }
            }
        } finally {
            try {
                connection.close()
            } catch (_: Exception) {
                // ignore — close() не критичен, вернётся в pool
            }
        }
    }

    /**
     * Sync INSERT для случая, когда батчинг выключен (kill-switch false).
     *
     * @param record событие для INSERT.
     * @return `true` если INSERT успешен, `false` если ошибка БД.
     */
    private fun executeSingle(record: EventRecord): Boolean {
        val connection = WORKING_DATABASE.getConnection() ?: return false
        return try {
            val fields = buildFieldsList(record)
            val sql = buildInsertSql(fields)
            connection.prepareStatement(sql).use { ps ->
                ps.executeUpdate()
            }
            true
        } catch (e: SQLException) {
            log.warn(
                "[EventsBuffer] single INSERT error (eventType={}, clientIp={}): {}",
                record.eventType,
                record.clientIp,
                e.message,
                e,
            )
            false
        } finally {
            try {
                connection.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    /**
     * Собирает полный список пар (column, value) для INSERT — добавляет служебные поля
     * (client_ip, user_agent, anon_id, site_user_id) к переданным в [EventRecord.fieldsValues].
     *
     * @param record событие с полями.
     * @return полный список пар для [buildInsertSql].
     */
    private fun buildFieldsList(record: EventRecord): MutableList<Pair<String, Any>> {
        val fields = mutableListOf<Pair<String, Any>>()
        fields.addAll(record.fieldsValues)
        record.clientIp?.let { fields.add(Pair("client_ip", it)) }
        record.userAgent?.let { fields.add(Pair("user_agent", it)) }
        record.anonId?.let { fields.add(Pair("anon_id", it)) }
        if (record.siteUserId > 0) fields.add(Pair("site_user_id", record.siteUserId))
        return fields
    }

    /**
     * Проверяет, разрешён ли батчинг свойством `karaoke.web.events.batch-enabled` в
     * [KaraokeProperties] (дефолт `false` — opt-in, FR-002).
     *
     * Если [KaraokeProperties] по какой-то причине недоступен (ранняя инициализация,
     * проблемы с файлом) — функция возвращает `false` через `try/catch`. Безопасный
     * дефолт = sync INSERT как раньше (минимизируем изменения в типовом сценарии).
     *
     * @return `true` если батчинг разрешён; `false` если явно отключён или свойство недоступно.
     *
     * @see specs/274-events-batch-insert FR-002
     */
    private fun isEnabled(): Boolean =
        try {
            KaraokeProperties.getBoolean(KARAOKE_PROPERTY_BATCH_ENABLED)
        } catch (_: Throwable) {
            false
        }

    /**
     * Читает `karaoke.web.events.batch-max-buffer-size` из [KaraokeProperties] с дефолтом
     * `DEFAULT_MAX_BUFFER_SIZE` (500). При ошибке — дефолт через `try/catch`.
     */
    private fun maxBufferSize(): Int =
        try {
            KaraokeProperties.getInt(KARAOKE_PROPERTY_MAX_BUFFER_SIZE).coerceAtLeast(1)
        } catch (_: Throwable) {
            DEFAULT_MAX_BUFFER_SIZE
        }

    /** Размер буфера (для отладки/метрик). Не сериализуется в JSON. */
    fun bufferSize(): Int = buffer.size

    /** Флаг: идёт ли сейчас flush (для отладки/метрик). */
    fun isFlushing(): Boolean = flushing.get()

    /** Полная очистка (для тестов). */
    fun clear() {
        buffer.clear()
    }
}

package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * Удаляет старые записи из `tbl_events` (FR-011).
 *
 * Запускается ежедневно в 03:00 (UTC, см. cron `"0 0 3 * * *"`). Retention period
 * управляется через `KARAOKE_WEB_EVENTS_RETENTION_DAYS` (default 7 дней).
 *
 * **Почему retention нужен**:
 *  - `tbl_events` используется как append-only event log для аналитики инцидентов.
 *  - Без retention таблица растёт неограниченно (~5000 INSERT/мин при пике = 7M строк/день
 *    БЕЗ sampling/dedup, ~350K/день С sampling 1/20 для анонимов).
 *  - Через 7 дней данные теряют практическую ценность для root cause analysis —
 *    админ либо уже решил инцидент, либо уже собрал нужные метрики.
 *
 * **Почему НЕ через SyncRegistry** (см. D-4 в research.md):
 *  - `tbl_events` намеренно НЕ синхронизируется между LOCAL и SERVER (см. комментарий
 *    `НЕ tbl_events` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt`).
 *  - Таблица живёт только на проде как временный буфер для аналитики.
 *
 * **Безопасность** (FR-012):
 *  - SQL-ошибки логируются через SLF4J `log.warn(...)` с указанием Exception.
 *  - Scheduler НЕ бросает исключение — иначе Spring может выключить задачу.
 *
 * @see archive/docs/features/site-traffic-resilience.md
 * @see archive/docs/features/dual-db-sync.md (контекст почему tbl_events НЕ синхронизируется)
 */
@Component
class EventsRetentionScheduler(
    private val properties: KaraokeProperties,
) {
    private val log = LoggerFactory.getLogger(EventsRetentionScheduler::class.java)

    /**
     * Ежедневный cleanup. Cron `"0 0 3 * * *"` = 03:00:00 каждый день.
     *
     * Cron format: Spring 6-field (second minute hour day-of-month month day-of-week).
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun cleanup() {
        val retentionDays = properties.eventsRetentionDays
        val cutoff = Timestamp.from(Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L))
        val connection = WORKING_DATABASE.getConnection()
        if (connection == null) {
            log.warn("Невозможно установить соединение с БД для cleanup tbl_events (retention=$retentionDays дней)")
            return
        }
        try {
            val sql = "DELETE FROM tbl_events WHERE last_update < ?"
            val ps = connection.prepareStatement(sql)
            ps.setTimestamp(1, cutoff)
            val deletedCount = ps.executeUpdate()
            ps.close()
            log.info("tbl_events retention: удалено $deletedCount строк старше $cutoff (retention=$retentionDays дней)")
        } catch (e: SQLException) {
            log.warn("Ошибка SQL при retention cleanup tbl_events (retention=$retentionDays дней)", e)
        } catch (e: Exception) {
            log.warn("Неожиданная ошибка при retention cleanup tbl_events (retention=$retentionDays дней)", e)
        }
    }
}

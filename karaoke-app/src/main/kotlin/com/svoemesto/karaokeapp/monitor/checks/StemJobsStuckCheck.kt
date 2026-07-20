package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity

/**
 * Премиум-фича «Создать минусовку из аудиофайла» (StemJob) — задания, застрявшие в WAITING (поллер
 * почему-то не забрал) или WORKING (пайплайн демукса не дошёл до финализации) дольше STALE_MINUTES.
 * Реальные задания создают пользователи на прод-сайте — проверка смотрит remote (Connection.remote()),
 * тем же источником, что StemJobPollScheduler. По образцу SubmittedAssignmentsCheck: body без числа
 * (оно в detail, см. MonitorAlert.contentHash()), иначе алерт «мигал» бы read/unread на каждом тике.
 */
object StemJobsStuckCheck : MonitorCheck {
    private const val STALE_MINUTES = 30L

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val db = Connection.remote()
        val stuck =
            try {
                StemJob.countStuck(db, STALE_MINUTES)
            } catch (e: Exception) {
                println("StemJobsStuckCheck: ${e.message}")
                0
            } finally {
                try {
                    db.getConnection()?.close()
                } catch (_: Exception) {
                }
            }

        if (stuck <= 0) return emptyList()

        return listOf(
            MonitorAlert(
                key = "stemjobs.stuck",
                severity = MonitorSeverity.WARNING,
                title = "Застрявшие задания «Минусовки»",
                body = "Есть задания на разделение аудио (премиум-фича), которые дольше $STALE_MINUTES минут не сдвинулись с места.",
                category = "Минусовки",
                detail = "$stuck шт.",
                recommendations = "Проверьте очередь Processes (лейн THREAD_LANE_STEM_JOBS) и доступность karaoke-web (Karaoke.stemJobsWebInternalUrl).",
            ),
        )
    }
}

package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessWorker
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import java.util.concurrent.ConcurrentHashMap

/**
 * Зависание ОТДЕЛЬНОГО thread-лейна очереди рендера: у лейна есть ожидающие (`WAITING`) задания, но
 * ни одно из них не выполняется дольше [STALL_THRESHOLD_MS] - хотя воркер в целом работает
 * (`KaraokeProcessWorker.isWork == true`). В отличие от [RenderQueueStalledCheck] (видит только
 * полную остановку воркера целиком), эта проверка обнаруживает зависание конкретного лейна при
 * работающей очереди - защитная сетка на случай регрессий после устранения гонки данных в
 * `KaraokeProcessWorker` (см. specs/029-fix-queue-lane-stall/research.md).
 *
 * Одноклик-fix - точечно вернуть осиротевшие `WORKING`-записи именно этого лейна в `WAITING`
 * (`KaraokeProcess.setWorkingToWaitingForThread`), не трогая другие лейны и не требуя перезапуска
 * всего воркера.
 *
 * @see docs/features/async-process-queue.md
 * @see docs/features/monitoring.md
 */
object LaneStalledCheck : MonitorCheck {
    private const val STALL_THRESHOLD_MS = 2 * 60 * 1000L

    // threadId -> момент, когда лейн впервые замечен простаивающим (WAITING есть, живого обработчика
    // нет). У tbl_processes нет колонки "waiting since" - отметка живёт в памяти между тиками
    // MonitoringService (раз в минуту, см. MonitoringService.tick()), переживает только пока жив
    // процесс karaoke-app - это ожидаемо для best-effort детектора, не источник истины.
    private val stalledSince = ConcurrentHashMap<Int, Long>()

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        if (!KaraokeProcessWorker.isWork) {
            // Полная остановка воркера уже покрыта RenderQueueStalledCheck - не дублируем алерт.
            stalledSince.clear()
            return emptyList()
        }

        val waitingByLane = KaraokeProcess.getProcessesToStart(ctx.localDb)
        val now = System.currentTimeMillis()
        val lanesWithWaiting = mutableSetOf<Int>()

        val alerts =
            waitingByLane.keys.mapNotNull { threadId ->
                lanesWithWaiting += threadId
                val thread = KaraokeProcessWorker.threadsMap[threadId]
                if (thread != null && thread.isAlive) {
                    // Лейн активен - не зависший, забываем прошлую отметку простоя.
                    stalledSince.remove(threadId)
                    return@mapNotNull null
                }

                val since = stalledSince.getOrPut(threadId) { now }
                val idleMs = now - since
                if (idleMs < STALL_THRESHOLD_MS) return@mapNotNull null

                buildAlert(ctx, threadId, idleMs)
            }

        // Лейны, для которых WAITING-очередь опустела (или лейн снова активен) - забыть отметку
        // простоя, чтобы не "утекала" память и не всплывал устаревший алерт на пустую очередь.
        stalledSince.keys.retainAll(lanesWithWaiting)

        return alerts
    }

    private fun buildAlert(
        ctx: MonitorContext,
        threadId: Int,
        idleMs: Long,
    ): MonitorAlert {
        val waitingCount =
            KaraokeProcess
                .loadList(
                    args = mapOf("thread_id" to threadId.toString(), "process_status" to "WAITING", "filter_notail" to "1"),
                    database = ctx.localDb,
                ).size
        val idleMin = idleMs / 60_000

        return MonitorAlert(
            key = "queue.lane.stalled.$threadId",
            severity = MonitorSeverity.WARNING,
            title = "Лейн очереди «${laneName(threadId)}» завис",
            // detail (не body!) несёт изменчивую часть (кол-во/время) - иначе contentHash() менялся бы
            // на каждом тике и алерт "мигал" бы read/unread (см. комментарий в MonitorAlert.kt).
            body = "В лейне «${laneName(threadId)}» есть ожидающие задания, но обработка не продвигается.",
            category = "Очередь",
            detail = "ждёт заданий: $waitingCount, простаивает уже $idleMin мин",
            resolveAction = {
                val restored = KaraokeProcess.setWorkingToWaitingForThread(ctx.localDb, threadId)
                println("[LaneStalledCheck] Восстановлен лейн threadId=$threadId: возвращено в WAITING записей: $restored")
                stalledSince.remove(threadId)
            },
        )
    }

    private fun laneName(threadId: Int): String =
        when (threadId) {
            KaraokeProcess.THREAD_LANE_HEAVY_RENDER -> "тяжёлый рендер"
            KaraokeProcess.THREAD_LANE_LIGHT_BACKGROUND -> "лёгкие фоновые операции"
            KaraokeProcess.THREAD_LANE_REMOTE_STORE_UPLOAD -> "загрузка в хранилище"
            KaraokeProcess.THREAD_LANE_HEALTH_REPORT -> "автоисправление HealthReport"
            KaraokeProcess.THREAD_LANE_STEM_JOBS -> "премиум-стемы"
            else -> "лейн $threadId"
        }
}

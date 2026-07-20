package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessWorker
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity

/**
 * Очередь рендера (KaraokeProcessWorker) остановлена, хотя есть ждущие задания. Одноклик-fix -
 * запустить очередь (тот же вызов, что и кнопка старт/стоп в хедере webvue3,
 * см. ApiController./api/processes/workerstartstop).
 */
object RenderQueueStalledCheck : MonitorCheck {
    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        if (KaraokeProcessWorker.isWork) return emptyList()
        val waiting = KaraokeProcess.getCountWaiting(ctx.localDb)
        if (waiting <= 0) return emptyList()

        return listOf(
            MonitorAlert(
                key = "queue.stalled",
                severity = MonitorSeverity.WARNING,
                title = "Очередь рендера остановлена",
                body = "Очередь заданий остановлена, хотя есть ждущие задания.",
                category = "Очередь",
                detail = "ждёт заданий: $waiting",
                resolveAction = {
                    KaraokeProcessWorker.start(
                        database = ctx.localDb,
                        storageService = ctx.storageService,
                        storageApiClient = ctx.storageApiClient,
                    )
                },
            ),
        )
    }
}

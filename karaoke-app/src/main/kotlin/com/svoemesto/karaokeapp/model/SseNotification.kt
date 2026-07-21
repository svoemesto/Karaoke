package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.HealthReportDTO
import com.svoemesto.karaokeapp.monitor.MonitorAlertDto

/**
 * Класс Sse Notification.
 *
 * @see docs/features/sse-notifications.md
 */
data class SseNotification(
    val type: SseNotificationType,
    val data: Any,
) {
    companion object {
        fun recordChange(recordChangeMessage: RecordChangeMessage): SseNotification =
            SseNotification(SseNotificationType.RECORD_CHANGE, recordChangeMessage)

        fun recordAdd(recordAddMessage: RecordAddMessage): SseNotification =
            SseNotification(SseNotificationType.RECORD_ADD, recordAddMessage)

        fun recordDelete(recordDeleteMessage: RecordDeleteMessage): SseNotification =
            SseNotification(SseNotificationType.RECORD_DELETE, recordDeleteMessage)

        fun processWorkerState(processWorkerStateMessage: ProcessWorkerStateMessage): SseNotification =
            SseNotification(SseNotificationType.PROCESS_WORKER_STATE, processWorkerStateMessage)

        fun processCountWaiting(processCountWaitingMessage: ProcessCountWaitingMessage): SseNotification =
            SseNotification(SseNotificationType.PROCESS_COUNT_WAITING, processCountWaitingMessage)

        fun message(message: Message): SseNotification = SseNotification(SseNotificationType.MESSAGE, message)

        fun error(error: Message): SseNotification = SseNotification(SseNotificationType.ERROR, error)

        fun dummy(): SseNotification = SseNotification(SseNotificationType.DUMMY, "dummy")

        fun log(text: String): SseNotification = SseNotification(SseNotificationType.LOG, text)

        fun crud(crudMessage: List<List<String>>): SseNotification = SseNotification(SseNotificationType.CRUD, crudMessage)

        fun sync(syncMessage: List<List<String>>): SseNotification = SseNotification(SseNotificationType.SYNC, syncMessage)

        fun healthReports(
            settingsId: Long,
            healthReportDtoList: List<HealthReportDTO>,
        ): SseNotification =
            SseNotification(
                SseNotificationType.HEALTH_REPORTS,
                mapOf(
                    "settingsId" to settingsId,
                    "healthReportDtoList" to healthReportDtoList,
                ),
            )

        fun monitorAlerts(alerts: List<MonitorAlertDto>): SseNotification = SseNotification(SseNotificationType.MONITOR_ALERTS, alerts)
    }
}

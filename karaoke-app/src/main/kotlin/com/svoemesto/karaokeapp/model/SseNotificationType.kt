package com.svoemesto.karaokeapp.model

/**
 * Перечисление возможных значений для sse notification type.
 *
 * @see docs/features/sse-notifications.md
 */
enum class SseNotificationType(
    val value: String,
) {
    RECORD_CHANGE("recordChange"),
    RECORD_ADD("recordAdd"),
    RECORD_DELETE("recordDelete"),
    PROCESS_WORKER_STATE("processWorkerState"),
    PROCESS_COUNT_WAITING("processCountWaiting"),
    MESSAGE("message"),
    ERROR("error"),
    DUMMY("dummy"),
    LOG("log"),
    CRUD("crud"),
    SYNC("sync"),
    HEALTH_REPORTS("healthReports"),
    MONITOR_ALERTS("monitorAlerts"),
}

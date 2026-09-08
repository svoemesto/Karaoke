package com.svoemesto.karaokeapp.model

/**
 * Перечисление возможных значений для sse notification type.
 *
 * @see archive/docs/features/sse-notifications.md
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

    // specs/316-search-timeout-configurable (FR-010, rev 3): сводка массового поиска текста
    // (minIntervalMs между успешными запросами). Broadcast (не addressed) — доставляется всем вкладкам.
    MASS_SEARCH_SUMMARY("massSearchSummary"),
}

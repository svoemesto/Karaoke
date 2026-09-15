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

    // specs/129-hrpool-badge (OpenProject #129): размер приоритетной очереди
    // HealthReportBatchPool. Рассылается при каждом изменении (с подавлением
    // дублей — lastSentQueueSize в HealthReportBatchPool). Фронт рисует зелёный
    // бейдж в ProcessWorker.vue.
    HEALTH_REPORT_POOL_COUNT("healthReportPoolCount"),

    // specs/130-hrwaiting-badge (OpenProject #130): Σ WAITING-записей по всем
    // песням, для которых получен HR. Рассылается при изменении (с подавлением
    // дублей — lastSentWaitingCount в HealthReport.companion). Фронт рисует
    // голубой бейдж в правом верхнем углу кнопки Старт/Стоп (ProcessWorker.vue).
    HEALTH_REPORT_WAITING_COUNT("healthReportWaitingCount"),

    MONITOR_ALERTS("monitorAlerts"),

    // specs/316-search-timeout-configurable (FR-010, rev 3): сводка массового поиска текста
    // (minIntervalMs между успешными запросами). Broadcast (не addressed) — доставляется всем вкладкам.
    MASS_SEARCH_SUMMARY("massSearchSummary"),
}

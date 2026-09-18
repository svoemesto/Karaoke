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

    // specs/118 #397: размер cache-очереди StorageMetadataCache. Отдельный канал —
    // не смешивается с PROCESS_COUNT_WAITING (другая структура данных, другой источник
    // обновлений — submit'ы в StorageMetadataCache, а не KaraokeProcess). Broadcast.
    CACHE_QUEUE_SIZE("cacheQueueSize"),

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

    // specs/132-hrwaiting-pool (OpenProject #132): размер реального backend-пула
    // WAITING-задач HealthReportBatchPool.waitingQueue (второй пул, 20 worker'ов,
    // LIFO, move-to-front). Рассылается при каждом изменении размера (с подавлением
    // дублей — lastSentWaitingPoolSize в HealthReportBatchPool). Фронт рисует
    // голубой бейдж в правом верхнем углу кнопки Старт/Стоп (ProcessWorker.vue).
    HEALTH_REPORT_WAITING_POOL_SIZE("healthReportWaitingPoolSize"),

    MONITOR_ALERTS("monitorAlerts"),

    // specs/316-search-timeout-configurable (FR-010, rev 3): сводка массового поиска текста
    // (minIntervalMs между успешными запросами). Broadcast (не addressed) — доставляется всем вкладкам.
    MASS_SEARCH_SUMMARY("massSearchSummary"),
}

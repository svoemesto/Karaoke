package com.svoemesto.karaokeapp.monitor

/**
 * Серьёзность сообщения мониторинга (аналог HealthReportStatus, но для системных проверок, не
 * привязанных к конкретной песне). rank - для вычисления максимальной серьёзности среди активных
 * сообщений (цвет светофора в хедере webvue3); color - HEX для маркировки строки в модалке.
 */
enum class MonitorSeverity(
    val rank: Int,
    val color: String,
) {
    INFO(0, "#4CAF50"),
    WARNING(1, "#FFC107"),
    ERROR(2, "#F44336"),
    CRITICAL(3, "#D50000"),
}

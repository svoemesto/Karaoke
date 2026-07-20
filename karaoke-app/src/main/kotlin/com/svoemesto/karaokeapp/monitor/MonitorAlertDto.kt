package com.svoemesto.karaokeapp.monitor

import java.io.Serializable

/**
 * Сериализуемое представление MonitorAlert для фронта (SSE + REST) - без лямбды resolveAction
 * (см. MonitorAlert.resolveAction / MonitoringService.resolve). По образцу HealthReportDTO.
 */
data class MonitorAlertDto(
    val key: String,
    val severityName: String,
    val color: String,
    val title: String,
    val body: String,
    val category: String,
    val detail: String? = null,
    val recommendations: String? = null,
    val canResolve: Boolean = false,
    val contentHash: String = "",
    val read: Boolean = false,
) : Serializable

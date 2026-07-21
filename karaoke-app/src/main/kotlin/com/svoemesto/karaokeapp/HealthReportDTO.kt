package com.svoemesto.karaokeapp

import java.io.Serializable

/**
 * DTO для health report: сериализуемое представление для API/UI.
 *
 * @see docs/features/monitoring.md
 */
data class HealthReportDTO(
    val settingsId: Long,
    val settingsFileName: String = "",
    val description: String,
    val healthReportTypeName: String,
    val healthReportStatusName: String,
    val color: String = "",
    val canResolve: Boolean = false,
    val problemText: String = "",
    val solutionText: String = "",
) : Serializable

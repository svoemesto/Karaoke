package com.svoemesto.karaokeapp

/**
 * Перечисление возможных значений для health report type.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
enum class HealthReportType {
    CONSISTENCY_VIOLATION,
    FILE_VIOLATION,
}

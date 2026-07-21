package com.svoemesto.karaokeapp

/**
 * Перечисление возможных значений для health report type.
 *
 * @see docs/features/dual-db-sync.md
 */
enum class HealthReportType {
    CONSISTENCY_VIOLATION,
    FILE_VIOLATION,
}

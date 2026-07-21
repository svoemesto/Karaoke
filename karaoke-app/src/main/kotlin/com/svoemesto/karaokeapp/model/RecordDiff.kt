package com.svoemesto.karaokeapp.model

/**
 * Класс Record Diff.
 *
 * @see docs/features/dual-db-sync.md
 */
data class RecordDiff(
    val recordDiffName: String,
    val recordDiffValueNew: Any?,
    val recordDiffValueOld: Any?,
    val recordDiffRealField: Boolean = true,
)

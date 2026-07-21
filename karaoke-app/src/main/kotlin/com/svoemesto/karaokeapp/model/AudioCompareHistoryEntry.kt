package com.svoemesto.karaokeapp.model

@kotlinx.serialization.Serializable
/**
 * Класс Audio Compare History Entry.
 *
 * @see docs/features/monitoring.md
 */
data class AudioCompareHistoryEntry(
    val id: Long,
    val similarityPercent: Int,
    val deltaMs: Long,
    val ok: Boolean,
    val comparedAt: String,
)

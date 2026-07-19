package com.svoemesto.karaokeapp.model

@kotlinx.serialization.Serializable
data class AudioCompareHistoryEntry(
    val id: Long,
    val similarityPercent: Int,
    val deltaMs: Long,
    val ok: Boolean,
    val comparedAt: String
)

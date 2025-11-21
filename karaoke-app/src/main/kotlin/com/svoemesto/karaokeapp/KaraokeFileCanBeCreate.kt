package com.svoemesto.karaokeapp

data class KaraokeFileCanBeCreate(
    val canBeCreate: Boolean,
    val reason: String,
    val threadId: Int? = null,
    val processPriority: Int? = null
)
package com.svoemesto.karaokeapp

data class KaraokeFileAction (
    val type: KaraokeFileActionType,
    val location: KaraokeFileTypeLocations,
    val action: () -> Unit
)
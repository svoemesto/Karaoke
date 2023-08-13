package com.svoemesto.karaokeapp.controllers

import java.io.Serializable

@kotlinx.serialization.Serializable
data class SseMessage(
    val objType: String = "",
    val objId: Long = 0,
    val eventName: String = ""
): Serializable

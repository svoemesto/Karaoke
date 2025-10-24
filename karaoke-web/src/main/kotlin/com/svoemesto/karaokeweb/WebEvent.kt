package com.svoemesto.karaokeweb

import java.sql.Timestamp

data class WebEvent(
    val eventType: String,
    val eventDescription: String,
    val eventDate: Timestamp,
    val eventReferer: String
)

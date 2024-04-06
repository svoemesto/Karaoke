package com.svoemesto.karaokeweb

import java.sql.Timestamp
import java.util.Date

data class WebEvent(
    val eventType: String,
    val eventDescription: String,
    val eventDate: Timestamp,
    val eventReferer: String
)

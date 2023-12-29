package com.svoemesto.karaokeapp.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import kotlin.properties.Delegates

lateinit var WEBSOCKET: SimpMessagingTemplate
var APP_WORK_IN_CONTAINER by Delegates.notNull<Boolean>()

@Service
//@Component
class KaraokeAppService(
    webSocket: SimpMessagingTemplate,
    @Value("\${work-in-container}") val wic: Long
) {

    init {
        APP_WORK_IN_CONTAINER = (wic != 0L)
        WEBSOCKET = webSocket
    }

}
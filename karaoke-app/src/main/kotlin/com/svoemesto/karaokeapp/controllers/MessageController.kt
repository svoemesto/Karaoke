package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.WEBSOCKET
import com.svoemesto.karaokeapp.model.RecordChangeMessage
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

@Controller
class MessageController(webSocket: SimpMessagingTemplate) {

    init {
        WEBSOCKET = webSocket
    }
    @MessageMapping("/message")
    @SendTo("/messages/recordchange")
    @Throws(Exception::class)
    fun sendRecordChangeMessage(recordChangeMessage: RecordChangeMessage): RecordChangeMessage {
        return recordChangeMessage
    }

}
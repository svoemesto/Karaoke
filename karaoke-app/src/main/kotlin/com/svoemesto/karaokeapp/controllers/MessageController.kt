package com.svoemesto.karaokeapp.controllers

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

@Controller
class MessageController(private val messagingTemplate: SimpMessagingTemplate) {

    @MessageMapping("/messages/send")
    fun sendMessage(message: String) {
        val destination = "/messages" // Путь назначения для отправки сообщения

        messagingTemplate.convertAndSend(destination, message)
    }
}





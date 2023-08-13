package com.svoemesto.karaokeapp.controllers

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/messages") // Настройка эндпоинта WebSocket
            .setAllowedOrigins("*") // Разрешить все источники (для тестирования)
            .withSockJS() // Включение поддержки SockJS
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/") // Включение простого брокера сообщений
        config.setApplicationDestinationPrefixes("/") // Префикс пути назначения
    }
}
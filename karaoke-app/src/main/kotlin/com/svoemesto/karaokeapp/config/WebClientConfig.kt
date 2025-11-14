package com.svoemesto.karaokeapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun smKaraokeWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://sm-karaoke.ru/api/storage") // Устанавливаем базовый URL
            .defaultHeader("User-Agent", "Your-Kotlin-App") // Пример установки заголовка по умолчанию
            // Здесь можно добавить настройки для аутентификации, таймаутов и т.д.
            .build()
    }
}
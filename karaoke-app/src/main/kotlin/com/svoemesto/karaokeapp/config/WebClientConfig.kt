package com.svoemesto.karaokeapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Конфигурация для web client .
 *
 * @see AGENTS.md
 */
@Configuration
class WebClientConfig {
    @Bean
    fun smKaraokeWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl("https://sm-karaoke.ru/api/storage") // Устанавливаем базовый URL
            .defaultHeader("User-Agent", "Your-Kotlin-App") // Пример установки заголовка по умолчанию
            // Здесь можно добавить настройки для аутентификации, таймаутов и т.д.
            .build()
}

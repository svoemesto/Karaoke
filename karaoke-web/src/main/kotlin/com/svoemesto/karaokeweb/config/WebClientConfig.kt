package com.svoemesto.karaokeweb.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    // @Primary: другие места (например StorageApiClientWeb) инжектят WebClient без @Qualifier —
    // без @Primary Spring не сможет выбрать бин из нескольких и приложение не стартует.
    @Bean
    @Primary
    fun smKaraokeWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://sm-karaoke.ru/api/storage") // Устанавливаем базовый URL
            .defaultHeader("User-Agent", "Your-Kotlin-App") // Пример установки заголовка по умолчанию
            // Здесь можно добавить настройки для аутентификации, таймаутов и т.д.
            .build()
    }

    @Bean
    @Qualifier("yandexCaptchaWebClient")
    fun yandexCaptchaWebClient(@Value("\${captcha.proxy-url}") captchaProxyUrl: String): WebClient {
        return WebClient.builder().baseUrl(captchaProxyUrl).build()
    }
}
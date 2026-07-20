package com.svoemesto.karaokeweb.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import java.util.Base64

@Configuration
class WebClientConfig {
    // @Primary: другие места (например StorageApiClientWeb) инжектят WebClient без @Qualifier —
    // без @Primary Spring не сможет выбрать бин из нескольких и приложение не стартует.
    @Bean
    @Primary
    fun smKaraokeWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl("https://sm-karaoke.ru/api/storage") // Устанавливаем базовый URL
            .defaultHeader("User-Agent", "Your-Kotlin-App") // Пример установки заголовка по умолчанию
            // Здесь можно добавить настройки для аутентификации, таймаутов и т.д.
            .build()

    @Bean
    @Qualifier("yandexCaptchaWebClient")
    fun yandexCaptchaWebClient(
        @Value("\${captcha.proxy-url}") captchaProxyUrl: String,
    ): WebClient = WebClient.builder().baseUrl(captchaProxyUrl).build()

    // ЮKassa аутентифицируется Basic-заголовком shopId:secretKey (см. документацию API). Если ключи
    // ещё не заведены (пусто) — заголовок всё равно ставится (пустой), PaymentService сам проверяет
    // их наличие ДО вызова и не должен дёргать этот WebClient без ключей.
    @Bean
    @Qualifier("yookassaWebClient")
    fun yookassaWebClient(
        @Value("\${yookassa.proxy-url}") proxyUrl: String,
        @Value("\${yookassa.shop-id}") shopId: String,
        @Value("\${yookassa.secret-key}") secretKey: String,
    ): WebClient {
        val basicAuth = Base64.getEncoder().encodeToString("$shopId:$secretKey".toByteArray())
        return WebClient
            .builder()
            .baseUrl(proxyUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $basicAuth")
            .build()
    }
}

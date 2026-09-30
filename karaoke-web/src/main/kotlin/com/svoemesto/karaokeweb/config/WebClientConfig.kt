package com.svoemesto.karaokeweb.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import java.util.Base64

/**
 * Конфигурация для web client .
 *
 * @see AGENTS.md
 */
@Configuration
class WebClientConfig {
    // @Primary: другие места (например StorageApiClientWeb) инжектят WebClient без @Qualifier —
    // без @Primary Spring не сможет выбрать бин из нескольких и приложение не стартует.
    //
    // Базовый URL вынесен в настройку storage.web-base-url. Раньше он был зашит
    // строкой в коде, и это означало, что ЛОКАЛЬНЫЙ запуск karaoke-web отправлял
    // все операции хранилища (загрузку, получение URL, удаление) на ПРОДОВЫЙ
    // sm-karaoke.ru. Значение по умолчанию — ровно то, что было в коде, поэтому
    // на проде поведение не меняется ни на байт; но при локальной разработке
    // адрес переопределяется переменной окружения.
    @Bean
    @Primary
    fun smKaraokeWebClient(
        @Value("\${storage.web-base-url:https://sm-karaoke.ru/api/storage}") storageWebBaseUrl: String,
    ): WebClient =
        WebClient
            .builder()
            .baseUrl(storageWebBaseUrl)
            .defaultHeader("User-Agent", "Your-Kotlin-App")
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

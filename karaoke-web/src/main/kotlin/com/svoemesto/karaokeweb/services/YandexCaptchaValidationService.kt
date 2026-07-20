package com.svoemesto.karaokeweb.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class YandexCaptchaValidationService(
    @Qualifier("yandexCaptchaWebClient") private val webClient: WebClient,
    private val captchaConfigService: CaptchaConfigService,
) {
    private data class ValidateResponse(
        val status: String = "",
        val message: String = "",
    )

    // Если серверный ключ ещё не заведён в Properties (капча не настроена) — не блокируем регистрацию,
    // просто пропускаем проверку. Как только ключ появится в настройках, проверка включится сама.
    fun validate(
        token: String,
        ip: String?,
    ): Boolean {
        val serverKey = captchaConfigService.getServerKey()
        if (serverKey.isBlank()) {
            println("Yandex SmartCaptcha: серверный ключ не настроен, проверка пропущена")
            return true
        }
        if (token.isBlank()) return false

        return try {
            var formData = BodyInserters.fromFormData("secret", serverKey).with("token", token)
            if (!ip.isNullOrBlank()) formData = formData.with("ip", ip)

            val response =
                webClient
                    .post()
                    .uri("/validate")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .bodyToMono<ValidateResponse>()
                    .block()
            response?.status == "ok"
        } catch (e: Exception) {
            println("Ошибка проверки Yandex SmartCaptcha: ${e.message}")
            false
        }
    }
}

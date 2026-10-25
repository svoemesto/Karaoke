package com.svoemesto.karaokeweb.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

/**
 * Сервис проверки капчи Yandex SmartCaptcha.
 *
 * **Fail-closed (спека 564, OpenProject #362, аудит BR-518).** До правки пустой серверный
 * ключ означал «проверка пройдена» — массовая регистрация аккаунтов без капчи. Теперь:
 *
 * - серверный ключ отсутствует/пуст → [CaptchaValidationResult.NOT_CONFIGURED] → регистрация
 *   отклоняется с отдельным кодом `captcha_not_configured`;
 * - токен пуст или внешний сервис ответил «не ок» → [CaptchaValidationResult.FAILED];
 * - внешний сервис недоступен (сеть/таймаут/ошибка) → [CaptchaValidationResult.UNAVAILABLE];
 * - внешний сервис подтвердил токен → [CaptchaValidationResult.PASSED].
 *
 * Значение серверного ключа не логируется и не возвращается наружу.
 *
 * @see archive/docs/features/async-process-queue.md
 * @see docs/features/public-settings-perimeter.md
 */
@Service
class YandexCaptchaValidationService(
    @Qualifier("yandexCaptchaWebClient") private val webClient: WebClient,
    private val captchaConfigService: CaptchaConfigService,
) {
    /**
     * Ответ внешнего валидатора (`POST /validate`).
     *
     * @property status `ok` — токен подтверждён.
     * @property message текст ошибки от внешнего сервиса (в ответе клиенту не отдаётся).
     */
    private data class ValidateResponse(
        val status: String = "",
        val message: String = "",
    )

    /**
     * Категория журнала конфигурации/проверки капчи (спека 564, research D9).
     */
    private val log = LoggerFactory.getLogger("infra.captcha.config")

    /**
     * Проверить токен капчи.
     *
     * @param token токен, полученный виджетом на публичной форме.
     * @param ip адрес посетителя (передаётся внешнему валидатору; может быть `null`).
     * @return исход проверки — [CaptchaValidationResult].
     */
    fun validate(
        token: String,
        ip: String?,
    ): CaptchaValidationResult = validateWith(token, ip) { serverKey, captchaToken, address -> callValidator(serverKey, captchaToken, address) }

    /**
     * Тело [validate] с тест-швом [validator].
     *
     * [validator] — seam для unit-тестов: подставляется функция «спросить внешний сервис»,
     * чтобы проверить все четыре исхода без обращения к сети. В рантайме это HTTP-POST к
     * валидатору Yandex.
     *
     * @param validator возвращает `true`, если внешний сервис подтвердил токен; бросает
     *   исключение, если сервис недоступен.
     */
    internal fun validateWith(
        token: String,
        ip: String?,
        validator: (serverKey: String, captchaToken: String, address: String?) -> Boolean,
    ): CaptchaValidationResult {
        // Незаконная конфигурация — fail-closed (FR-009). Раньше здесь был «return true».
        if (!captchaConfigService.isServerKeyConfigured()) {
            log.warn("captcha:config:not_configured — проверка капчи не настроена, регистрация отклоняется")
            return CaptchaValidationResult.NOT_CONFIGURED
        }
        if (token.isBlank()) return CaptchaValidationResult.FAILED

        val serverKey = captchaConfigService.getServerKey()
        return try {
            if (validator(serverKey, token, ip)) {
                CaptchaValidationResult.PASSED
            } else {
                CaptchaValidationResult.FAILED
            }
        } catch (e: Exception) {
            // Внешний сервис недоступен — fail-closed (FR-010). Значение ключа не логируется.
            //
            // BR-522 (аудит #336, OpenProject #401): логируем ИСКЛЮЧЕНИЕ целиком, а не
            // только e.message — без стека причина сбоя (таймаут? DNS? 500 валидатора?)
            // терялась, и «капча недоступна» выглядела одинаково при любой поломке.
            log.warn("captcha:validation:unavailable", e)
            CaptchaValidationResult.UNAVAILABLE
        }
    }

    /**
     * Обращение к внешнему валидатору Yandex SmartCaptcha (`POST /validate`).
     *
     * @return `true`, если сервис ответил `status=ok`.
     * @throws Exception любой сбой сети/таймаута/разбора ответа — вызывающий код трактует
     *   его как [CaptchaValidationResult.UNAVAILABLE].
     */
    private fun callValidator(
        serverKey: String,
        token: String,
        ip: String?,
    ): Boolean {
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
        return response?.status == "ok"
    }
}

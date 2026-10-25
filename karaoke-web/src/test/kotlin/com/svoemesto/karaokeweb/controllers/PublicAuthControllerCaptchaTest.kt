package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.CaptchaConfigService
import com.svoemesto.karaokeweb.services.SiteUserTokenService
import com.svoemesto.karaokeweb.services.YandexCaptchaValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.web.reactive.function.client.WebClient

/**
 * Регрессионные тесты «до/после» для отказа регистрации при незаконной конфигурации
 * проверки капчи (спека 564, OpenProject #362, аудит BR-518).
 *
 * **До правки** при пустом серверном ключе регистрация проходила (fail-open).
 * **После правки** — `400` с отдельным кодом `captcha_not_configured`; обычный отказ по
 * непройденной капче сохраняет прежний код `captcha` (FR-018 — формат ответа прежний).
 *
 * Случай «регистрация проходит» (исход `PASSED`) здесь НЕ проверяется: следующий шаг —
 * создание пользователя в БД, а unit-тест не должен трогать боевую/локальную базу. Проверка
 * самого исхода `PASSED` — в `YandexCaptchaValidationServiceTest`.
 *
 * @see specs/559-close-public-properties-captcha/spec.md FR-009, FR-018
 */
class PublicAuthControllerCaptchaTest {
    private fun controller(
        serverKey: String,
        webClient: WebClient = Mockito.mock(WebClient::class.java),
    ): PublicAuthController {
        val storageService: KaraokeStorageService = Mockito.mock(KaraokeStorageService::class.java)
        val storageApiClient: StorageApiClient = Mockito.mock(StorageApiClient::class.java)
        return PublicAuthController(
            storageService = storageService,
            storageApiClient = storageApiClient,
            passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder(),
            siteUserTokenService = SiteUserTokenService(storageService, storageApiClient),
            captchaConfigService = CaptchaConfigService(serverKey),
            yandexCaptchaValidationService =
                YandexCaptchaValidationService(
                    webClient = webClient,
                    captchaConfigService = CaptchaConfigService(serverKey),
                ),
        )
    }

    private fun request(): MockHttpServletRequest = MockHttpServletRequest().apply { remoteAddr = "10.0.0.1" }

    @Test
    fun `регистрация при незаконной конфигурации отклоняется с кодом captcha_not_configured`() {
        val response =
            controller(serverKey = "")
                .register(
                    email = "user@example.com",
                    password = "secret123",
                    passwordConfirm = "secret123",
                    displayName = "User",
                    captchaToken = "any-token",
                    request = request(),
                )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("captcha_not_configured", response.body?.get("error"))
    }

    @Test
    fun `регистрация без токена капчи отклоняется с прежним кодом captcha`() {
        val response =
            controller(serverKey = "server-key")
                .register(
                    email = "user@example.com",
                    password = "secret123",
                    passwordConfirm = "secret123",
                    displayName = "User",
                    captchaToken = "",
                    request = request(),
                )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("captcha", response.body?.get("error"))
    }

    @Test
    fun `недоступность внешнего сервиса проверки отклоняет регистрацию`() {
        // Поддельный WebClient: webClient.post() возвращает null → NPE внутри обращения к
        // валидатору → исход UNAVAILABLE (fail-closed).
        val response =
            controller(serverKey = "server-key")
                .register(
                    email = "user@example.com",
                    password = "secret123",
                    passwordConfirm = "secret123",
                    displayName = "User",
                    captchaToken = "any-token",
                    request = request(),
                )

        // BR-522 (аудит #336, OpenProject #401): «сервис проверки недоступен» — это 503
        // с кодом `captcha_unavailable`, а НЕ 400 с кодом `captcha` («вы не робот»).
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("captcha_unavailable", response.body?.get("error"))
    }

    @Test
    fun `формат отказа прежний - JSON с ключом error и HTTP 400`() {
        val response =
            controller(serverKey = "")
                .register(
                    email = "user@example.com",
                    password = "secret123",
                    passwordConfirm = "secret123",
                    displayName = "User",
                    captchaToken = "any-token",
                    request = request(),
                )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(setOf("error"), response.body?.keys)
    }
}

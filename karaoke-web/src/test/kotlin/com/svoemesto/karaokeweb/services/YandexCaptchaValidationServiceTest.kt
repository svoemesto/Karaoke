package com.svoemesto.karaokeweb.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient

/**
 * Регрессионные тесты «до/после» для fail-closed проверки капчи
 * (спека 564, OpenProject #362, аудит BR-518).
 *
 * **До правки** проверка возвращала `Boolean` и при пустом серверном ключе отдавала `true` —
 * «проверка пройдена» (fail-open). **После правки** пустой ключ даёт
 * [CaptchaValidationResult.NOT_CONFIGURED], а любая ошибка внешнего сервиса —
 * [CaptchaValidationResult.UNAVAILABLE]; оба приводят к отказу регистрации.
 *
 * @see specs/559-close-public-properties-captcha/spec.md FR-009, FR-010, FR-014
 */
class YandexCaptchaValidationServiceTest {
    private val serverKey = "test-server-key"

    private fun service(serverKeyValue: String = serverKey): YandexCaptchaValidationService =
        YandexCaptchaValidationService(
            webClient = Mockito.mock(WebClient::class.java),
            captchaConfigService = CaptchaConfigService(serverKeyValue),
        )

    @Test
    fun `пустой серверный ключ даёт NOT_CONFIGURED а не PASSED`() {
        var validatorCalled = false
        val result =
            service(serverKeyValue = "").validateWith("token", "10.0.0.1") { _, _, _ ->
                validatorCalled = true
                true
            }

        assertEquals(CaptchaValidationResult.NOT_CONFIGURED, result)
        assertFalse(validatorCalled, "при незаконной конфигурации внешний сервис не опрашивается")
        assertEquals("captcha_not_configured", result.errorCode)
    }

    @Test
    fun `пустой токен даёт FAILED`() {
        var validatorCalled = false
        val result =
            service().validateWith("", "10.0.0.1") { _, _, _ ->
                validatorCalled = true
                true
            }

        assertEquals(CaptchaValidationResult.FAILED, result)
        assertFalse(validatorCalled, "без токена внешний сервис не опрашивается")
        assertEquals("captcha", result.errorCode)
    }

    @Test
    fun `успешный ответ валидатора даёт PASSED`() {
        val result = service().validateWith("token", "10.0.0.1") { _, _, _ -> true }

        assertEquals(CaptchaValidationResult.PASSED, result)
        assertEquals(null, result.errorCode)
    }

    @Test
    fun `неуспешный ответ валидатора даёт FAILED`() {
        val result = service().validateWith("token", "10.0.0.1") { _, _, _ -> false }

        assertEquals(CaptchaValidationResult.FAILED, result)
        assertEquals("captcha", result.errorCode)
    }

    @Test
    fun `недоступность внешнего сервиса даёт UNAVAILABLE а не PASSED`() {
        val result =
            service().validateWith("token", "10.0.0.1") { _, _, _ ->
                throw IllegalStateException("connection reset")
            }

        assertEquals(CaptchaValidationResult.UNAVAILABLE, result)
        // BR-522: «сервис недоступен» и «капча не пройдена» — разные коды отказа.
        assertEquals("captcha_unavailable", result.errorCode)
    }

    @Test
    fun `серверный ключ передаётся в валидатор и не попадает в журнал`() {
        val appender = CapturingAppender()
        appender.start()
        val logger = LoggerFactory.getLogger("infra.captcha.config") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        var passedKey: String? = null
        try {
            service().validateWith("token", "10.0.0.1") { key, _, _ ->
                passedKey = key
                true
            }
            service(serverKeyValue = "").validateWith("token", "10.0.0.1") { _, _, _ -> true }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        assertEquals(serverKey, passedKey, "валидатор должен получить серверный ключ")
        val notConfigured = appender.messages.firstOrNull { it.contains("captcha:config:not_configured") }
        assertNotNull(notConfigured, "в журнале должна быть запись о незаконной конфигурации")
        assertFalse(notConfigured!!.contains(serverKey), "значение серверного ключа в журнал не пишется: $notConfigured")
    }

    /** Собирает отформатированные сообщения журнала категории `infra.captcha.config`. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}

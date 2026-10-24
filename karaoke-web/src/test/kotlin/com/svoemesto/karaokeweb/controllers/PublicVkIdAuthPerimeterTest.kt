package com.svoemesto.karaokeweb.controllers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.util.ReflectionTestUtils

/**
 * Регрессионные тесты «до/после» периметра VK ID-flow (OpenProject #378, аудит BR-001 и BR-002).
 *
 * **BR-001 (SEC).** ДО правки оба endpoint'а (`/api/public/utils/vkIdOAuthUrl` и callback) были
 * анонимны: любой клиент получал authorize-URL, проходил flow своим VK-аккаунтом, и его токен
 * уходил на admin-машину, перезаписывая прод-credentials публикации. ПОСЛЕ правки выдача
 * authorize-URL требует общего секрета администратора (`X-Internal-Secret`, fail-safe: пустой
 * настроенный секрет закрывает канал), а токен сохраняется только для доверенного VK `user_id`
 * (`vk.id.allowed-user-ids`), если список задан.
 *
 * **BR-002 (SEC).** ДО правки тело ответа VK ID (`access_token` в начале) писалось в INFO-лог и
 * вставлялось в HTML страницы ошибки. ПОСЛЕ правки тело не логируется ни на одном уровне и не
 * попадает в HTML — только длина; журнал и страница проверяются здесь capture'ом logback.
 */
class PublicVkIdAuthPerimeterTest {
    private val secret = "test-vk-admin-secret"

    private fun controller(
        adminSecret: String? = secret,
        allowedUserIds: String = "",
    ): PublicVkIdAuthController =
        PublicVkIdAuthController().also {
            ReflectionTestUtils.setField(it, "adminSecret", adminSecret ?: "")
            ReflectionTestUtils.setField(it, "allowedUserIds", allowedUserIds)
        }

    private fun request(header: String? = null): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.5"
            addHeader("X-Real-IP", "203.0.113.7")
            header?.let { addHeader(PublicVkIdAuthController.SECRET_HEADER, it) }
        }

    // ---- BR-001: периметр выдачи authorize-URL ----

    @Test
    fun `выдача authorize-URL без секрета возвращает 403`() {
        val response = controller().getVkIdOAuthUrl(request())

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(false, response.body?.get("success"))
    }

    @Test
    fun `выдача authorize-URL с неверным секретом возвращает 403`() {
        val response = controller().getVkIdOAuthUrl(request("wrong-secret"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertFalse(response.body.toString().contains(secret), "секрет не должен попадать в тело ответа")
    }

    @Test
    fun `не настроенный секрет закрывает канал даже при пустом заголовке`() {
        // Ловушка образца: "" == "" — сравнение без проверки на пустоту открыло бы доступ.
        val response = controller(adminSecret = "").getVkIdOAuthUrl(request(""))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `отказ по периметру попадает в журнал безопасности без значения секрета`() {
        val appender = CapturingAppender().also { it.start() }
        val logger = LoggerFactory.getLogger("infra.vkid.security") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        try {
            controller().getVkIdOAuthUrl(request("wrong-secret"))
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val denial = appender.messages.firstOrNull { it.contains("vkid:security:denied") }
        assertNotNull(denial, "в журнале должна быть запись об отказе: ${appender.messages}")
        assertTrue(denial!!.contains("invalid_secret"), "в записи причина отказа: $denial")
        assertTrue(denial.contains("203.0.113.7"), "в записи адрес источника (X-Real-IP): $denial")
        assertFalse(denial.contains(secret), "значение секрета в журнал не пишется: $denial")
    }

    @Test
    fun `с корректным секретом запрос доходит до проверки настроек`() {
        // clientId/redirectUri/clientSecret в тесте пусты → 200 с прежним форматом {success:false}.
        val response = controller().getVkIdOAuthUrl(request(secret))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body?.get("success"))
    }

    @Test
    fun `пустой список доверенных user_id не ограничивает сохранение токена`() {
        assertTrue(controller(allowedUserIds = "").isUserIdAllowed("12345"))
        assertTrue(controller(allowedUserIds = "  ").isUserIdAllowed(null))
    }

    @Test
    fun `заданный список доверенных user_id пропускает только его`() {
        val vkController = controller(allowedUserIds = "111, 222")

        assertTrue(vkController.isUserIdAllowed("111"))
        assertTrue(vkController.isUserIdAllowed(" 222 "))
        assertFalse(vkController.isUserIdAllowed("333"))
        assertFalse(vkController.isUserIdAllowed(null))
    }

    // ---- BR-002: тело ответа VK ID не попадает в лог и в HTML ----

    @Test
    fun `успешный ответ VK ID не логирует токен`() {
        val token = "vk1.a.SECRET_ACCESS_TOKEN_XYZ"
        val appender = CapturingAppender().also { it.start() }
        val logger = LoggerFactory.getLogger(PublicVkIdAuthController::class.java) as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.TRACE
        logger.addAppender(appender)
        val parsed: JsonNode?
        try {
            val (json, errorHtml) =
                controller().parseTokenResponse(
                    """{"access_token":"$token","refresh_token":"refresh-xyz","expires_in":3600,"user_id":777}""",
                )
            parsed = json
            assertNull(errorHtml, "валидный ответ не должен давать страницу ошибки")
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        assertNotNull(parsed)
        assertEquals("777", parsed!!.get("user_id").asText())
        assertFalse(
            appender.messages.any { it.contains(token) },
            "токен не должен попадать ни в одну запись журнала: ${appender.messages}",
        )
        assertTrue(
            appender.messages.any { it.contains("length=") },
            "в журнале должна быть только длина ответа: ${appender.messages}",
        )
    }

    @Test
    fun `неразбираемый ответ VK ID не утекает в лог и в HTML`() {
        val fakeBody = "not-json-SECRET_ACCESS_TOKEN_XYZ"
        val appender = CapturingAppender().also { it.start() }
        val logger = LoggerFactory.getLogger(PublicVkIdAuthController::class.java) as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.TRACE
        logger.addAppender(appender)
        val errorHtml: String?
        try {
            val (json, html) = controller().parseTokenResponse(fakeBody)
            assertNull(json)
            errorHtml = html
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        assertNotNull(errorHtml, "неразбираемый ответ должен давать страницу ошибки")
        assertFalse(errorHtml!!.contains("SECRET_ACCESS_TOKEN_XYZ"), "тело ответа не показывается в HTML")
        assertFalse(
            appender.messages.any { it.contains("SECRET_ACCESS_TOKEN_XYZ") },
            "тело ответа не пишется в журнал: ${appender.messages}",
        )
    }

    @Test
    fun `ошибка VK ID экранируется в HTML страницы ошибки`() {
        val (json, html) =
            controller().parseTokenResponse(
                """{"error":"invalid_grant","error_description":"<script>alert(1)</script>"}""",
            )

        assertNull(json)
        assertNotNull(html)
        assertFalse(html!!.contains("<script>alert(1)</script>"), "HTML из ответа VK не должен исполняться")
        assertTrue(html.contains("&lt;script&gt;"), "описание ошибки экранируется: $html")
    }

    /** Собирает отформатированные сообщения журнала. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}

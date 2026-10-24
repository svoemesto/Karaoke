package com.svoemesto.karaokeweb.controllers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Регрессионные тесты «до/после» для периметра публичных настроек
 * (спека 564, OpenProject #359, аудит BR-019).
 *
 * **До правки** (код без проверки секрета и без белого списка ключей) тесты падали: запрос
 * без заголовка менял значение в базе, а дайджест отдавал серверный ключ капчи.
 * **После правки** — `403` и ни одной выполненной SQL-команды, секретный ключ не выдаётся
 * никогда.
 *
 * @see specs/559-close-public-properties-captcha/spec.md FR-001, FR-002, FR-007
 */
class PublicSettingsWebControllerTest {
    private val secret = "test-internal-secret"

    /**
     * Поддельная БД: считаем выполненные SQL-команды, отдаём заранее заданные строки.
     *
     * @param rows строки `tbl_public_settings` в формате (key, value, description).
     */
    private class FakeDb(
        private val rows: List<Triple<String, String, String>> = emptyList(),
    ) {
        val executedSql = mutableListOf<String>()
        val connection: Connection = Mockito.mock(Connection::class.java)
        private val statement: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        private val resultSet: ResultSet = Mockito.mock(ResultSet::class.java)
        private var rowIndex = 0

        init {
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
                executedSql.add(invocation.getArgument(0))
                statement
            }
            Mockito.`when`(statement.executeUpdate()).thenReturn(1)
            Mockito.`when`(statement.executeQuery()).thenReturn(resultSet)
            Mockito.`when`(resultSet.next()).thenAnswer {
                if (rowIndex < rows.size) {
                    rowIndex++
                    true
                } else {
                    false
                }
            }
            Mockito.`when`(resultSet.getString(Mockito.anyString())).thenAnswer { invocation ->
                val column = invocation.getArgument<String>(0)
                val row = rows[(rowIndex - 1).coerceAtLeast(0)]
                when (column) {
                    "key" -> row.first
                    "value" -> row.second
                    "description" -> row.third
                    else -> ""
                }
            }
        }
    }

    private fun controller(internalSecret: String = secret): PublicSettingsWebController =
        PublicSettingsWebController(internalSecret = internalSecret)

    private fun request(secretHeader: String? = null): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.5"
            secretHeader?.let { addHeader(PublicSettingsWebController.SECRET_HEADER, it) }
        }

    @BeforeEach
    fun resetCache() {
        // Кеш значений — статический (companion object). Сбрасываем его перед каждым тестом,
        // чтобы значение из одного теста не утекало в другой.
        PublicSettingsWebController.markDirty()
    }

    // --- US1: анонимная запись отклоняется и значение в базе не меняется ---

    @Test
    fun `запись без заголовка внутреннего секрета отклоняется и SQL не выполняется`() {
        val fake = FakeDb()
        val response =
            controller().handleSetProperty("newsAutoPublishKillSwitch", "true", request()) { fake.connection }

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(false, response.body)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
        Mockito.verify(fake.connection, Mockito.never()).prepareStatement(Mockito.anyString())
    }

    @Test
    fun `запись с неверным секретом отклоняется и SQL не выполняется`() {
        val fake = FakeDb()
        val response =
            controller().handleSetProperty("newsAutoPublishKillSwitch", "true", request("wrong-secret")) { fake.connection }

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
    }

    @Test
    fun `пустой настроенный секрет и пустой заголовок дают 403 (ловушка образца)`() {
        val fake = FakeDb()
        val response =
            controller(internalSecret = "").handleSetProperty("newsAutoPublishKillSwitch", "true", request("")) { fake.connection }

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
    }

    @Test
    fun `HTTP-уровень - POST setproperty без секрета возвращает 403`() {
        val mockMvc = MockMvcBuilders.standaloneSetup(controller()).build()

        mockMvc
            .perform(
                post("/api/properties/setproperty")
                    .param("key", "newsAutoPublishKillSwitch")
                    .param("stringValue", "true"),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `запись секретного ключа капчи отклоняется даже с корректным секретом`() {
        val fake = FakeDb()
        val response =
            controller().handleSetProperty("yandexSmartCaptchaServerKey", "stolen", request(secret)) { fake.connection }

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
    }

    @Test
    fun `в ответе при отказе нет текста ошибки БД`() {
        val fake = FakeDb()
        Mockito
            .`when`(fake.connection.prepareStatement(Mockito.anyString()))
            .thenThrow(java.sql.SQLException("""ERROR: relation "tbl_public_settings" does not exist"""))
        val response =
            controller().handleSetProperty("newsAutoPublishKillSwitch", "true", request(secret)) { fake.connection }

        // При корректном секрете запрос доходит до БД, но текст ошибки наружу не уходит:
        // в ответе прежний `false`, текст ошибки — только в журнале.
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body)
        assertFalse(response.body.toString().contains("tbl_public_settings"))
    }

    // --- US3: легитимный административный путь ---

    @Test
    fun `запись с корректным заголовком выполняется и ответ прежнего формата`() {
        val fake = FakeDb()
        val response =
            controller().handleSetProperty("newsAutoPublishKillSwitch", "true", request(secret)) { fake.connection }

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body)
        assertEquals(1, fake.executedSql.size, "выполнена ровно одна команда: ${fake.executedSql}")
        assertTrue(fake.executedSql.first().contains("UPDATE tbl_public_settings"))
    }

    @Test
    fun `после записи чтение возвращает новое значение без ожидания TTL`() {
        val fake = FakeDb(listOf(Triple("newsAutoPublishKillSwitch", "true", "kill-switch")))
        controller().handleSetProperty("newsAutoPublishKillSwitch", "true", request(secret)) { fake.connection }

        val read = controller().handleGetProperty("newsAutoPublishKillSwitch", request(secret)) { fake.connection }

        assertEquals("true", read)
    }

    @Test
    fun `анонимное чтение непубличного ключа возвращает пустую строку`() {
        val fake = FakeDb(listOf(Triple("newsAutoPublishKillSwitch", "true", "kill-switch")))
        val read = controller().handleGetProperty("newsAutoPublishKillSwitch", request()) { fake.connection }

        assertEquals("", read)
    }

    @Test
    fun `чтение непубличного ключа по защищённому каналу возвращает значение`() {
        val fake = FakeDb(listOf(Triple("newsAutoPublishKillSwitch", "true", "kill-switch")))
        val read = controller().handleGetProperty("newsAutoPublishKillSwitch", request(secret)) { fake.connection }

        assertEquals("true", read)
    }

    @Test
    fun `чтение публичного ключа анонимно возвращает значение`() {
        val fake = FakeDb(listOf(Triple("yandexSmartCaptchaClientKey", "client-key-value", "клиентский ключ")))
        val read = controller().handleGetProperty("yandexSmartCaptchaClientKey", request()) { fake.connection }

        assertEquals("client-key-value", read)
    }

    @Test
    fun `чтение секретного ключа не выдаёт значение даже по защищённому каналу`() {
        val fake = FakeDb(listOf(Triple("yandexSmartCaptchaServerKey", "super-secret", "серверный ключ")))
        val read = controller().handleGetProperty("yandexSmartCaptchaServerKey", request(secret)) { fake.connection }

        assertEquals("", read)
        assertFalse(read.contains("super-secret"))
    }

    @Test
    fun `дайджест анонимно содержит только публичные ключи`() {
        val fake =
            FakeDb(
                listOf(
                    Triple("newsAutoPublishKillSwitch", "true", "kill-switch"),
                    Triple("yandexSmartCaptchaClientKey", "client-key-value", "клиентский ключ"),
                    Triple("yandexSmartCaptchaServerKey", "super-secret", "серверный ключ"),
                ),
            )
        val digest = controller().handleDigest(request()) { fake.connection }

        @Suppress("UNCHECKED_CAST")
        val properties = digest["properties"] as List<Map<String, String>>
        val keys = properties.map { it["key"] }
        assertEquals(listOf("yandexSmartCaptchaClientKey"), keys)
        assertFalse(digest.toString().contains("super-secret"), "секретный ключ не должен попадать в дайджест")
    }

    @Test
    fun `дайджест по защищённому каналу содержит все ключи кроме секретных`() {
        val fake =
            FakeDb(
                listOf(
                    Triple("newsAutoPublishKillSwitch", "true", "kill-switch"),
                    Triple("yandexSmartCaptchaClientKey", "client-key-value", "клиентский ключ"),
                    Triple("yandexSmartCaptchaServerKey", "super-secret", "серверный ключ"),
                ),
            )
        val digest = controller().handleDigest(request(secret)) { fake.connection }

        @Suppress("UNCHECKED_CAST")
        val properties = digest["properties"] as List<Map<String, String>>
        val keys = properties.map { it["key"] }
        assertEquals(listOf("newsAutoPublishKillSwitch", "yandexSmartCaptchaClientKey"), keys)
        assertFalse(digest.toString().contains("super-secret"), "секретный ключ не должен попадать в дайджест")
    }

    // --- US4: отказ виден в журнале и не выдаёт деталей ---

    @Test
    fun `отказ попадает в журнал безопасности без значения настройки`() {
        val appender = CapturingAppender()
        appender.start()
        val logger = LoggerFactory.getLogger("infra.settings.security") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        try {
            val request =
                MockHttpServletRequest().apply {
                    remoteAddr = "10.0.0.5"
                    addHeader("X-Real-IP", "203.0.113.7")
                }
            val fake = FakeDb()
            controller().handleSetProperty("newsAutoPublishKillSwitch", "true", request) { fake.connection }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val denial = appender.messages.firstOrNull { it.contains("settings:security:denied") }
        assertNotNull(denial, "в журнале категории infra.settings.security должна быть запись об отказе")
        assertTrue(denial!!.contains("missing_secret"), "в записи должна быть причина отказа: $denial")
        assertTrue(denial.contains("203.0.113.7"), "в записи должен быть адрес источника (X-Real-IP): $denial")
        assertFalse(denial.contains("newsAutoPublishKillSwitch"), "имя настройки в журнале отказа не пишется: $denial")
        assertFalse(denial.contains("true"), "значение настройки в журнал не пишется: $denial")
    }

    /** Собирает отформатированные сообщения журнала категории `infra.settings.security`. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}

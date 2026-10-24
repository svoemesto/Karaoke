package com.svoemesto.karaokeweb.controllers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.EventsBuffer
import com.svoemesto.karaokeweb.services.SamplingFilter
import com.svoemesto.karaokeweb.services.SiteUserResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

/**
 * Регрессионные тесты «до/после» для закрытия публичного доступа к `POST /changerecords`
 * (спека 554, OpenProject #357, аудит BR-011).
 *
 * **До правки** (код без проверки секрета) первый тест падал: запрос без заголовка
 * выполнял SQL-действия из тела. **После правки** — 403 и ни одной выполненной команды.
 *
 * Проверяются оба состояния (FR-010): с корректным ключом (действия применяются) и без
 * ключа (отказ, ничего не применено), плюс ловушка «пустой секрет + пустой заголовок»
 * (research D2) и отсутствие текста ошибки БД в ответе.
 *
 * @see specs/554-close-public-sql-endpoint/spec.md
 */
class MainControllerChangeRecordsTest {
    private val secret = "test-internal-secret"

    /** Поддельное JDBC-соединение: считаем выполненные команды, БД не трогаем. */
    private class FakeConnection {
        val statement: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        val connection: Connection = Mockito.mock(Connection::class.java)
        val executedSql = mutableListOf<String>()

        init {
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
                executedSql.add(invocation.getArgument(0))
                statement
            }
            Mockito.`when`(statement.executeUpdate()).thenReturn(1)
        }
    }

    private fun controller(internalSecret: String = secret): MainController =
        MainController(
            webSocket = Mockito.mock(SimpMessagingTemplate::class.java),
            wic = 0L,
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            samplingFilter = Mockito.mock(SamplingFilter::class.java),
            eventsBuffer = Mockito.mock(EventsBuffer::class.java),
            siteUserResolver = Mockito.mock(SiteUserResolver::class.java),
            syncInternalSecret = internalSecret,
        )

    /** Тело запроса в формате, который ждёт прод (FR-011: формат не менялся). */
    private fun body(): Map<String, Any> =
        mapOf(
            "word" to Crypto.encrypt(Crypto.WORDS_TO_CHECK)!!,
            "dataCreate" to
                listOf(
                    mapOf("sqlToInsert" to Crypto.encrypt("INSERT INTO tbl_test_songs (id) VALUES (42)")!!),
                ),
            "dataUpdate" to emptyList<Map<String, Any>>(),
            "dataDelete" to emptyList<Map<String, Any>>(),
        )

    private fun request(secretHeader: String? = null): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.5"
            secretHeader?.let { addHeader(MainController.SYNC_SECRET_HEADER, it) }
        }

    @Test
    fun `запрос без заголовка внутреннего секрета отклоняется и SQL не выполняется`() {
        val fake = FakeConnection()
        val response = controller().handleChangeRecords(body(), request(), { fake.connection })

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(MainController.FORBIDDEN_BODY, response.body)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
        Mockito.verify(fake.statement, Mockito.never()).executeUpdate()
    }

    @Test
    fun `запрос с корректным заголовком применяет действия`() {
        val fake = FakeConnection()
        val response = controller().handleChangeRecords(body(), request(secret), { fake.connection })

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("Created: 1"), "ответ прежнего формата, получено: ${response.body}")
        assertEquals(1, fake.executedSql.size, "выполнена ровно одна команда: ${fake.executedSql}")
        Mockito.verify(fake.statement, Mockito.times(1)).executeUpdate()
    }

    @Test
    fun `пустой настроенный секрет и пустой заголовок дают 403 (ловушка образца)`() {
        val fake = FakeConnection()
        val response = controller(internalSecret = "").handleChangeRecords(body(), request(""), { fake.connection })

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
    }

    @Test
    fun `неверный секрет даёт 403 и SQL не выполняется`() {
        val fake = FakeConnection()
        val response = controller().handleChangeRecords(body(), request("wrong-secret"), { fake.connection })

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(fake.executedSql.isEmpty(), "ни одна SQL-команда не должна выполняться, выполнено: ${fake.executedSql}")
    }

    @Test
    fun `в ответе при ошибке нет текста ошибки БД`() {
        val fake = FakeConnection()
        Mockito
            .`when`(fake.connection.prepareStatement(Mockito.anyString()))
            .thenThrow(SQLException("""ERROR: relation "tbl_songs" does not exist"""))
        val response = controller().handleChangeRecords(body(), request(secret), { fake.connection })

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val bodyText = response.body ?: ""
        assertFalse(bodyText.contains("tbl_songs"), "наружу не должно уходить имя таблицы: $bodyText")
        assertFalse(bodyText.contains("does not exist"), "наружу не должен уходить текст ошибки БД: $bodyText")
        assertEquals(MainController.INTERNAL_ERROR_BODY, bodyText)
    }

    @Test
    fun `HTTP-уровень - POST changerecords без секрета возвращает 403`() {
        val mockMvc = MockMvcBuilders.standaloneSetup(controller()).build()
        val payload = """{"word":"${Crypto.encrypt(Crypto.WORDS_TO_CHECK)}","dataCreate":[],"dataUpdate":[],"dataDelete":[]}"""

        mockMvc
            .perform(
                post("/changerecords")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `отказ попадает в журнал безопасности без тела запроса`() {
        val appender = CapturingAppender()
        appender.start()
        val logger = LoggerFactory.getLogger("infra.sync.security") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        try {
            val request =
                MockHttpServletRequest().apply {
                    remoteAddr = "10.0.0.5"
                    addHeader("X-Real-IP", "203.0.113.7")
                }
            val fake = FakeConnection()
            controller().handleChangeRecords(body(), request, { fake.connection })
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val denial = appender.messages.firstOrNull { it.contains("sync:security:denied") }
        assertNotNull(denial, "в журнале категории infra.sync.security должна быть запись об отказе")
        assertTrue(denial!!.contains("missing_secret"), "в записи должна быть причина отказа: $denial")
        assertTrue(denial.contains("203.0.113.7"), "в записи должен быть адрес источника (X-Real-IP): $denial")
        assertFalse(denial.contains("sqlToInsert"), "тело запроса в журнал не пишется: $denial")
        assertFalse(denial.contains("INSERT INTO"), "тело запроса в журнал не пишется: $denial")
    }

    /** Собирает отформатированные сообщения журнала категории `infra.sync.security`. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}

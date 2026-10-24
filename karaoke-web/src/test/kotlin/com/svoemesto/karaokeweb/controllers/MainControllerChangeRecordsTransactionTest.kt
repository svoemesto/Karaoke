package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.EventsBuffer
import com.svoemesto.karaokeweb.services.SamplingFilter
import com.svoemesto.karaokeweb.services.SiteUserResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.mock.web.MockHttpServletRequest
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

/**
 * Регрессионный тест «до/после» для аудита BR-012 «no-transaction:changerecords»
 * (OpenProject #378).
 *
 * **ДО правки** батч sync-изменений применялся statement за statement на autocommit-соединении:
 * исключение в середине батча оставляло БД частично изменённой (рассогласование данных), а
 * повторный sync не гарантировал идемпотентность уже применённых действий. Транзакционных
 * вызовов (`setAutoCommit(false)` / `commit()` / `rollback()`) не было вовсе.
 *
 * **ПОСЛЕ правки** весь батч — одна транзакция: autocommit выключается, `commit()` вызывается
 * один раз после успешного применения всего батча, при любой ошибке — `rollback()`, и
 * autocommit возвращается в исходное состояние.
 */
class MainControllerChangeRecordsTransactionTest {
    private val secret = "test-internal-secret"

    /** Поддельное JDBC-соединение: считаем выполненные команды и транзакционные вызовы. */
    private class FakeConnection {
        val statement: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        val connection: Connection = Mockito.mock(Connection::class.java)
        val executedSql = mutableListOf<String>()

        /** Номер команды, на которой нужно упасть (null — не падать). */
        var failOnStatementNumber: Int? = null

        init {
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
                executedSql.add(invocation.getArgument(0))
                statement
            }
            Mockito.`when`(statement.executeUpdate()).thenAnswer {
                val number = executedSql.size
                if (failOnStatementNumber != null && number == failOnStatementNumber) {
                    throw SQLException("""ERROR: relation "tbl_songs" does not exist""")
                }
                1
            }
        }
    }

    private fun controller(): MainController =
        MainController(
            webSocket = Mockito.mock(SimpMessagingTemplate::class.java),
            wic = 0L,
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            samplingFilter = Mockito.mock(SamplingFilter::class.java),
            eventsBuffer = Mockito.mock(EventsBuffer::class.java),
            siteUserResolver = Mockito.mock(SiteUserResolver::class.java),
            syncInternalSecret = secret,
        )

    /** Батч из трёх действий: insert, update, delete. */
    private fun body(): Map<String, Any> =
        mapOf(
            "word" to Crypto.encrypt(Crypto.WORDS_TO_CHECK)!!,
            "dataCreate" to
                listOf(
                    mapOf("sqlToInsert" to Crypto.encrypt("INSERT INTO tbl_test_songs (id) VALUES (42)")!!),
                ),
            "dataUpdate" to
                listOf(
                    mapOf(
                        "tableName" to "tbl_test_songs",
                        "idRecord" to 42,
                        "setText" to Crypto.encrypt("name = 'x'")!!,
                    ),
                ),
            "dataDelete" to
                listOf(
                    mapOf("sqlToDelete" to Crypto.encrypt("DELETE FROM tbl_test_songs WHERE id = 42")!!),
                ),
        )

    private fun request(): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = "10.0.0.5"
            addHeader(MainController.SYNC_SECRET_HEADER, secret)
        }

    @Test
    fun `успешный батч фиксируется одной транзакцией`() {
        val fake = FakeConnection()
        val response = controller().handleChangeRecords(body(), request(), { fake.connection })

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3, fake.executedSql.size, "применены все три действия: ${fake.executedSql}")
        Mockito.verify(fake.connection, Mockito.atLeastOnce()).setAutoCommit(false)
        Mockito.verify(fake.connection, Mockito.times(1)).commit()
        Mockito.verify(fake.connection, Mockito.never()).rollback()
    }

    @Test
    fun `ошибка в середине батча откатывает всю транзакцию`() {
        val fake = FakeConnection()
        // Падаем на втором действии (UPDATE) — первое (INSERT) уже выполнено.
        fake.failOnStatementNumber = 2
        val response = controller().handleChangeRecords(body(), request(), { fake.connection })

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        Mockito.verify(fake.connection, Mockito.atLeastOnce()).setAutoCommit(false)
        Mockito.verify(fake.connection, Mockito.times(1)).rollback()
        Mockito.verify(fake.connection, Mockito.never()).commit()
    }

    @Test
    fun `autocommit соединения возвращается в исходное состояние`() {
        val fake = FakeConnection()
        // Исходное значение autocommit — true (как у реального соединения Postgres).
        Mockito.`when`(fake.connection.autoCommit).thenReturn(true)

        controller().handleChangeRecords(body(), request(), { fake.connection })

        Mockito.verify(fake.connection, Mockito.times(1)).setAutoCommit(true)
    }

    @Test
    fun `ответ не содержит текста ошибки БД`() {
        val fake = FakeConnection()
        fake.failOnStatementNumber = 1
        val response = controller().handleChangeRecords(body(), request(), { fake.connection })

        val bodyText = response.body ?: ""
        assertTrue(
            !bodyText.contains("tbl_songs") && !bodyText.contains("does not exist"),
            "наружу не должен уходить текст ошибки БД: $bodyText",
        )
        assertEquals(MainController.INTERNAL_ERROR_BODY, bodyText)
    }
}

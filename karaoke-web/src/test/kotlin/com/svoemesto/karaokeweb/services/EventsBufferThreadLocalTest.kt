package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeConnection
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Регрессионный тест «до/после» для BR-006 (аудит #336, OpenProject #368):
 * `EventsBuffer` закрывал thread-local соединение переиспользуемого потока.
 *
 * **До правки** `executeBatch`/`executeSingle` делали `connection.close()` в `finally`.
 * Но `WORKING_DATABASE.getConnection()` возвращает физическое соединение, закэшированное
 * в `ThreadLocal` потока (`KaraokeConnection`, specs/087): это НЕ пул. Закрытие
 * разрушало кеш потока, и следующий запрос на нём открывал НОВОЕ физическое соединение
 * (TCP + auth handshake + `isValid(3)`). На публичном пути `POST /registerevent` (дефолт
 * kill-switch `batch-enabled=false` → sync INSERT на КАЖДОЕ событие) это давало churn
 * до N новых соединений в секунду и риск исчерпания `max_connections` PostgreSQL.
 *
 * **После правки** соединение не закрывается — его переиспользует следующий запрос на
 * том же потоке; освобождаются только statements (`use { }`).
 *
 * @see com.svoemesto.karaokeapp.KaraokeConnection.closeThreadConnection
 */
class EventsBufferThreadLocalTest {
    /**
     * Поддельное соединение: отдаёт заранее настроенное JDBC-соединение.
     *
     * Методы `KaraokeConnection.getConnection`/`closeThreadConnection` — `open` ради таких
     * подмен (см. KDoc `KaraokeConnection`).
     */
    private class FakeConnection(
        private val jdbc: Connection,
    ) : KaraokeConnection("jdbc:fake:events-buffer-test", "test", "test", "FAKE") {
        override fun getConnection(): Connection? = jdbc
    }

    /** JDBC-соединение, на котором INSERT возвращает 1. */
    private fun workingJdbcConnection(): Connection {
        val jdbc = Mockito.mock(Connection::class.java)
        val statement = Mockito.mock(PreparedStatement::class.java)
        Mockito.`when`(jdbc.prepareStatement(Mockito.anyString())).thenReturn(statement)
        Mockito.`when`(statement.executeUpdate()).thenReturn(1)
        return jdbc
    }

    private fun record(): EventsBuffer.EventRecord =
        EventsBuffer.EventRecord(
            fieldsValues = listOf("event_type" to "test-event"),
            eventType = "test-event",
            clientIp = "127.0.0.1",
            userAgent = "test-agent",
            anonId = null,
            siteUserId = 0,
        )

    @Test
    fun `sync-вставка не закрывает thread-local соединение потока`() {
        val jdbc = workingJdbcConnection()
        val db = FakeConnection(jdbc)

        val inserted = EventsBuffer().executeSingle(record(), db)

        assertTrue(inserted, "INSERT должен выполняться")
        Mockito.verify(jdbc, Mockito.never()).close()
    }

    @Test
    fun `batch-вставка не закрывает thread-local соединение потока`() {
        val jdbc = workingJdbcConnection()
        val db = FakeConnection(jdbc)

        EventsBuffer().executeBatch(listOf(record()), db)

        Mockito.verify(jdbc, Mockito.never()).close()
    }
}

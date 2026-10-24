package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.Connection

/**
 * Регрессионный тест «до/после» для BR-116 (аудит #336, OpenProject #368):
 * `ChatController.withDb` закрывал thread-local соединение переиспользуемого
 * потока Tomcat.
 *
 * **До правки** `withDb` в `finally` делал `db.getConnection()?.close()`. Но
 * `Connection.local()/remote()` — singleton с thread-local кешем физического соединения
 * (specs/234): закрытие разрушало кеш потока, и следующий запрос на этом потоке
 * открывал НОВОЕ физическое соединение (TCP + auth handshake + `isValid(3)`-проба) —
 * churn соединений к Postgres и рост latency на каждом запросе.
 *
 * **После правки** соединение не закрывается — его переиспользует следующий запрос
 * на том же потоке.
 *
 * @see KaraokeConnection.closeThreadConnection контракт закрытия соединения потока.
 */
class ChatControllerThreadLocalTest {
    @Test
    fun `withDb не закрывает thread-local соединение переиспользуемого потока`() {
        val jdbc = Mockito.mock(Connection::class.java)
        val db = FakeKaraokeConnection(jdbc)

        val returned = ChatController().withDb("local", db) { it.getConnection() }

        assertSame(jdbc, returned, "контроллер должен работать с переданным соединением")
        Mockito.verify(jdbc, Mockito.never()).close()
    }

    @Test
    fun `withDb не открывает и не закрывает соединение, если тело к БД не ходило`() {
        val jdbc = Mockito.mock(Connection::class.java)
        val db = FakeKaraokeConnection(jdbc)

        ChatController().withDb("local", db) { "ответ без обращения к БД" }

        // Прежний код в `finally` делал `db.getConnection()?.close()` — то есть запрос,
        // НЕ ходивший в БД, всё равно ОТКРЫВАЛ физическое соединение и сразу его закрывал.
        assertSame(
            0,
            db.getConnectionCalls.get(),
            "контроллер не должен открывать соединение только ради того, чтобы его закрыть",
        )
        Mockito.verify(jdbc, Mockito.never()).close()
    }
}

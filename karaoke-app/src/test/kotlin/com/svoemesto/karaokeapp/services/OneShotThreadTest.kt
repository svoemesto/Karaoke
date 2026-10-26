package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Регрессионный тест «до/после» для контракта закрытия JDBC-соединения одноразового
 * потока (аудит #336, OpenProject #368, BR-038 + BR-060; OpenProject #410, BR-627 —
 * поток, ходящий в две БД, обязан закрывать оба соединения).
 *
 * **До правки** фоновые задачи API-контроллеров и поток очереди создавались сырым
 * `thread { … }` и НЕ вызывали `closeThreadConnection()`: физическое соединение
 * оставалось в `ThreadLocal` мёртвого потока до недетерминированной финализации
 * сборщиком мусора. При `max_connections` PostgreSQL это отказ БД для всего приложения
 * (прецедент — KDoc `Connection.kt`: «100+ физических каналов → `FATAL: sorry, too many
 * clients already`»).
 *
 * **После правки** все одноразовые фоновые потоки идут через
 * [startOneShotThread], который закрывает thread-local соединение потока в `finally`
 * при ЛЮБОМ исходе (успех/исключение).
 *
 * @see KaraokeConnection.closeThreadConnection контракт закрытия соединения потока.
 */
class OneShotThreadTest {
    /**
     * Поддельное соединение: считает вызовы закрытия thread-local соединения.
     *
     * `getConnection()` возвращает `null` — тесту не нужна живая БД: проверяется
     * именно ФАКТ вызова закрытия, а не успешность SQL.
     */
    private class FakeConnection : KaraokeConnection("jdbc:fake:one-shot-test", "test", "test", "FAKE") {
        /** Счётчик вызовов `closeThreadConnection()`. */
        val closeCalls = AtomicInteger(0)

        override fun getConnection(): java.sql.Connection? = null

        override fun closeThreadConnection() {
            closeCalls.incrementAndGet()
            super.closeThreadConnection()
        }
    }

    @Test
    fun `одноразовый поток закрывает своё thread-local соединение`() {
        val db = FakeConnection()
        val ran = AtomicInteger(0)

        val thread = startOneShotThread(db, "test-one-shot-ok") { ran.incrementAndGet() }
        thread.join(10_000)

        assertEquals(1, ran.get(), "тело одноразовой задачи должно выполниться")
        assertEquals(
            1,
            db.closeCalls.get(),
            "соединение одноразового потока обязано быть закрыто (иначе утечка канала к БД)",
        )
    }

    @Test
    fun `соединение одноразового потока закрывается даже если тело упало`() {
        val db = FakeConnection()

        val thread =
            startOneShotThread(db, "test-one-shot-error") {
                throw IllegalStateException("сбой внутри фоновой задачи")
            }
        thread.join(10_000)

        assertEquals(
            1,
            db.closeCalls.get(),
            "соединение должно закрываться и при исключении внутри тела потока",
        )
    }

    @Test
    fun `поток одноразовой задачи — daemon и запускается`() {
        val db = FakeConnection()

        val thread = startOneShotThread(db, "test-one-shot-daemon") { }

        assertTrue(thread.isDaemon, "фоновый поток не должен держать JVM")
        thread.join(10_000)
        assertTrue(!thread.isAlive, "поток должен завершиться после выполнения тела")
    }

    /**
     * BR-627 (OpenProject #410): поток, который ходит в ДВЕ БД (LOCAL + SERVER),
     * обязан закрывать thread-local соединение **обоих** экземпляров — у каждого
     * `KaraokeConnection` свой `ThreadLocal`, и закрытие только переданного
     * оставило бы канал к второй БД висеть.
     */
    @Test
    fun `закрываются соединения обеих БД, к которым пошёл поток`() {
        val local = FakeConnection()
        val server = FakeConnection()

        val thread =
            startOneShotThread(
                database = local,
                name = "test-one-shot-two-databases",
                alsoClose = listOf(server),
            ) { }

        thread.join(10_000)

        assertEquals(1, local.closeCalls.get(), "соединение основной БД должно быть закрыто")
        assertEquals(1, server.closeCalls.get(), "соединение второй БД должно быть закрыто")
    }

    /** То же — при исключении внутри тела: закрытие не зависит от исхода. */
    @Test
    fun `закрываются соединения обеих БД даже если тело упало`() {
        val local = FakeConnection()
        val server = FakeConnection()

        val thread =
            startOneShotThread(
                database = local,
                name = "test-one-shot-two-databases-error",
                alsoClose = listOf(server),
            ) {
                throw IllegalStateException("сбой внутри фоновой задачи")
            }
        thread.join(10_000)

        assertEquals(1, local.closeCalls.get(), "соединение основной БД должно быть закрыто")
        assertEquals(1, server.closeCalls.get(), "соединение второй БД должно быть закрыто")
    }
}

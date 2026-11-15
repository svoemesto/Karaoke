package com.svoemesto.karaokeapp.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тест handle'а — аренды физического канала (спека 778, KARAOKE-430, FR-002).
 *
 * Без живой БД: источник каналов — `FakePoolProvider`.
 */
class PooledHandleTest {
    private fun handle(
        provider: FakePoolProvider = FakePoolProvider(),
        owner: Thread = Thread.currentThread(),
    ): Pair<PooledHandle, FakePoolProvider> {
        val pool =
            DbPool(
                target = "LOCAL",
                jdbcUrl = "jdbc:postgresql://fake:5432/karaoke",
                username = "u",
                password = "p",
                config = PoolConfig(maxPoolSize = 8),
                providerFactory = { provider },
            )
        return pool.borrowHandle(owner) to provider
    }

    @Test
    fun `close возвращает канал в пул а не закрывает его`() {
        val (h, provider) = handle()
        assertEquals(1, provider.activeCount.get())
        assertEquals(0, provider.releasedCount.get())

        h.asConnection().close()

        // Канал возвращён в пул...
        assertEquals(1, provider.releasedCount.get())
        assertEquals(0, provider.activeCount.get())
        // ...и НЕ закрыт: закрывать его нельзя, он общий.
        assertEquals(0, provider.physicalCloses.get(), "физический канал закрывать нельзя")
    }

    @Test
    fun `повторное закрытие не возвращает канал дважды`() {
        val (h, provider) = handle()
        val conn = h.asConnection()

        conn.close()
        conn.close()

        assertEquals(1, provider.releasedCount.get(), "возврат должен быть ровно один")
    }

    @Test
    fun `isClosed отражает состояние аренды`() {
        val (h, _) = handle()
        val conn = h.asConnection()
        assertFalse(conn.isClosed, "свежий handle не закрыт")
        conn.close()
        assertTrue(conn.isClosed, "после close handle закрыт")
    }

    @Test
    fun `методы handle делегируются физическому каналу`() {
        val (h, provider) = handle()
        val conn = h.asConnection()
        // Вызов, который не является close/isClosed, должен дойти до канала.
        conn.toString()
        assertEquals(1, provider.createdCount.get())
    }

    @Test
    fun `освобождение вызывает колбэк снятия с реестра`() {
        val pool =
            DbPool(
                target = "LOCAL",
                jdbcUrl = "jdbc:postgresql://fake:5432/karaoke",
                username = "u",
                password = "p",
                config = PoolConfig(maxPoolSize = 8),
                providerFactory = { FakePoolProvider() },
            )
        var released: PooledHandle? = null
        val h = pool.borrowHandle()
        val h2 = PooledHandle(provider = FakePoolProvider(), channel = h.channel, owner = h.owner) { released = it }

        assertNull(released)
        h2.release()
        assertSame(h2, released, "колбэк должен получить освобождённый handle")
    }

    @Test
    fun `два handle разных потоков получают разные каналы`() {
        val provider = FakePoolProvider()
        val pool =
            DbPool(
                target = "LOCAL",
                jdbcUrl = "jdbc:postgresql://fake:5432/karaoke",
                username = "u",
                password = "p",
                config = PoolConfig(maxPoolSize = 8),
                providerFactory = { provider },
            )
        val h1 = pool.borrowHandle()
        val h2 = pool.borrowHandle()
        assertNotSame(h1.channel, h2.channel, "занятый канал не выдаётся дважды")
        assertEquals(2, provider.activeCount.get())
    }

    private fun assertNull(value: Any?) = assertTrue(value == null, "ожидался null, получено $value")
}

package com.svoemesto.karaokeapp.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Тест пула соединений (спека 778, KARAOKE-430).
 *
 * **Без живой БД** (в CI PostgreSQL нет): источник каналов — `FakePoolProvider`,
 * поддельный `java.sql.Connection` — `java.lang.reflect.Proxy`. Hikari-специфика
 * (реальные каналы, keepalive, maxLifetime) проверяется на стенде по
 * `quickstart.md`.
 */
class DbPoolTest {
    private fun pool(
        provider: FakePoolProvider = FakePoolProvider(capacity = 4),
        maxPoolSize: Int = 4,
        acquireTimeoutMs: Long = 100,
    ): DbPool =
        DbPool(
            target = "LOCAL",
            jdbcUrl = "jdbc:postgresql://fake:5432/karaoke",
            username = "u",
            password = "p",
            config =
                PoolConfig(
                    maxPoolSize = maxPoolSize,
                    minIdle = 0,
                    acquireTimeoutMs = acquireTimeoutMs,
                    maxLifetimeMs = 1_800_000,
                    keepaliveMs = 60_000,
                    validationTimeoutMs = 3_000,
                ),
            providerFactory = { provider },
        )

    @Test
    fun `пул не создаётся до первого обращения`() {
        val provider = FakePoolProvider()
        val p = pool(provider)
        assertEquals(0, provider.createdCount.get(), "до borrow() каналов быть не должно")
        assertEquals(0, p.totalConnections())
    }

    @Test
    fun `повторное взятие не создаёт новый физический канал`() {
        val provider = FakePoolProvider()
        val p = pool(provider)

        // Взял → отпустил → взял: канал возвращается в пул и берётся снова.
        repeat(10) {
            p.borrowHandle().release()
        }

        assertEquals(1, provider.createdCount.get(), "канал должен переиспользоваться")
        assertEquals(10, provider.releasedCount.get())
    }

    @Test
    fun `число занятых каналов не превышает размер пула`() {
        val provider = FakePoolProvider(capacity = 4)
        val p = pool(provider, maxPoolSize = 4)

        val handles = (1..4).map { p.borrowHandle() }
        assertEquals(4, provider.activeCount.get())
        assertEquals(4, p.activeConnections())

        handles.forEach { it.release() }
        assertEquals(0, provider.activeCount.get())
    }

    @Test
    fun `исчерпание пула даёт таймаут ожидания с числом ожидающих и занятых`() {
        val provider = FakePoolProvider(capacity = 1)
        val p = pool(provider, maxPoolSize = 1)

        val held = p.borrowHandle()
        assertEquals(1, provider.activeCount.get())

        val e =
            org.junit.jupiter.api.Assertions.assertThrows(PoolTimeoutException::class.java) {
                p.borrowHandle()
            }
        assertEquals("LOCAL", e.target)
        assertEquals(1, e.busy, "занятый слот должен быть виден в отказе")
        assertEquals(1, e.maxPoolSize)
        assertTrue(e.timeoutMs > 0)

        held.release()
    }

    @Test
    fun `handle после освобождения слота снова выдаётся`() {
        val provider = FakePoolProvider(capacity = 1)
        val p = pool(provider, maxPoolSize = 1)

        val first = p.borrowHandle()
        val firstChannel = first.channel
        first.release()

        val second = p.borrowHandle()
        assertSame(firstChannel, second.channel, "освобождённый канал должен вернуться в пул")
    }

    @Test
    fun `handle разных потоков получают разные каналы пока заняты`() {
        val provider = FakePoolProvider(capacity = 4)
        val p = pool(provider, maxPoolSize = 4)

        val h1 = p.borrowHandle()
        val h2 = p.borrowHandle()
        assertNotSame(h1.channel, h2.channel)
    }

    @Test
    fun `наблюдаемость пула отдаёт фактические числа`() {
        val provider = FakePoolProvider(capacity = 4)
        val p = pool(provider, maxPoolSize = 4)

        val h = p.borrowHandle()
        assertEquals(1, p.totalConnections())
        assertEquals(1, p.activeConnections())
        assertEquals(0, p.idleConnections())

        h.release()
        assertEquals(1, p.totalConnections(), "канал остаётся в пуле, он не уничтожен")
        assertEquals(0, p.activeConnections())
        assertEquals(1, p.idleConnections())
    }

    @Test
    fun `параллельные обращения не выдают один канал дважды`() {
        val provider = FakePoolProvider(capacity = 4)
        val p = pool(provider, maxPoolSize = 4)
        val distinct = java.util.Collections.synchronizedSet(mutableSetOf<Any>())
        val errors = AtomicInteger(0)
        val start = CountDownLatch(1)

        val threads =
            (1..4).map {
                Thread {
                    start.await(5, TimeUnit.SECONDS)
                    try {
                        val h = p.borrowHandle()
                        distinct.add(h.channel)
                        Thread.sleep(20)
                        h.release()
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join(5_000) }

        assertEquals(0, errors.get(), "ошибок быть не должно")
        assertEquals(4, distinct.size, "параллельные обращения должны получить разные каналы")
    }
}

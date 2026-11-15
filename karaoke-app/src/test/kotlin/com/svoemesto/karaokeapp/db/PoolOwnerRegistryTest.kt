package com.svoemesto.karaokeapp.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Тест реестра владельцев handle'ов (спека 778, KARAOKE-430, FR-003).
 *
 * Без живой БД: handle'ы создаются через `FakePoolProvider`.
 */
class PoolOwnerRegistryTest {
    private fun pool(provider: FakePoolProvider = FakePoolProvider()): DbPool =
        DbPool(
            target = "LOCAL",
            jdbcUrl = "jdbc:postgresql://fake:5432/karaoke",
            username = "u",
            password = "p",
            config = PoolConfig(maxPoolSize = 8),
            providerFactory = { provider },
        )

    @Test
    fun `handle живого потока не считается мёртвым`() {
        val registry = PoolOwnerRegistry()
        val handle = pool().borrowHandle(Thread.currentThread())
        registry.register(handle)

        assertEquals(1, registry.size())
        assertTrue(registry.findDeadOwners().isEmpty(), "владелец жив — возвращать нечего")
        assertEquals(1, registry.liveOwnerCount())
    }

    @Test
    fun `handle умершего потока находится как мёртвый`() {
        val registry = PoolOwnerRegistry()
        val started = CountDownLatch(1)
        var handle: PooledHandle? = null
        val thread =
            Thread {
                started.countDown()
                handle = pool().borrowHandle(Thread.currentThread())
            }
        thread.start()
        started.await(5, TimeUnit.SECONDS)
        thread.join(5_000)

        assertTrue(!thread.isAlive, "поток должен завершиться")
        val h = handle!!
        registry.register(h)

        val dead = registry.findDeadOwners()
        assertEquals(1, dead.size, "handle мёртвого потока должен быть найден")
        assertEquals(h, dead.first())
        assertEquals(0, registry.liveOwnerCount())
    }

    @Test
    fun `снятие с реестра убирает handle из поиска`() {
        val registry = PoolOwnerRegistry()
        val handle = pool().borrowHandle(Thread.currentThread())
        registry.register(handle)
        registry.unregister(handle)

        assertEquals(0, registry.size())
        assertTrue(registry.findDeadOwners().isEmpty())
    }

    @Test
    fun `долгое удержание живым владельцем находится но не считается мёртвым`() {
        val registry = PoolOwnerRegistry()
        val handle = pool().borrowHandle(Thread.currentThread())
        registry.register(handle)

        // Ждём, чтобы handle полежал дольше порога.
        Thread.sleep(20)
        val longHeld = registry.findLongHeld(thresholdMs = 10)
        assertEquals(1, longHeld.size, "долгое удержание должно находиться")
        assertTrue(registry.findDeadOwners().isEmpty(), "владелец жив — возвращать нельзя")
    }
}

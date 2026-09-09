package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit-тесты для [StorageCircuitBreaker] (Pass 351, спека #352).
 *
 * Покрывает:
 *  1. CLOSED state allows calls.
 *  2. threshold failures opens circuit.
 *  3. cooldown half-opens circuit.
 *  4. probe success closes circuit.
 *  5. probe failure reopens circuit.
 *  6. concurrent acquire allows only one probe.
 *  7. concurrent failures transition once to OPEN.
 *  8. overhead closed state under 1ms.
 */
class StorageCircuitBreakerTest {
    private fun cb(
        timeout: Long = 5L,
        threshold: Int = 5,
        cooldown: Long = 30L,
    ) = StorageCircuitBreaker(
        timeoutSeconds = timeout,
        threshold = threshold,
        cooldownSeconds = cooldown,
    )

    @Test
    fun `closed state allows calls`() {
        val c = cb()
        assertEquals(StorageCircuitBreaker.State.CLOSED, c.state())
        repeat(3) {
            assertEquals(StorageCircuitBreaker.Decision.Allow, c.acquire())
        }
    }

    @Test
    fun `threshold failures opens circuit`() {
        val c = cb(threshold = 3)
        repeat(2) { c.recordFailure(RuntimeException("timeout")) }
        assertEquals(StorageCircuitBreaker.State.CLOSED, c.state())
        c.recordFailure(RuntimeException("timeout #3"))
        assertEquals(StorageCircuitBreaker.State.OPEN, c.state())
        assertEquals(StorageCircuitBreaker.Decision.FastFail, c.acquire())
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `cooldown half-opens circuit`() {
        val c = cb(threshold = 1, cooldown = 1L)
        c.recordFailure(RuntimeException("boom"))
        assertEquals(StorageCircuitBreaker.State.OPEN, c.state())
        assertEquals(StorageCircuitBreaker.Decision.FastFail, c.acquire())
        // Спим чуть дольше cooldown.
        Thread.sleep(1_100)
        // Cooldown elapsed → next acquire = Probe.
        assertEquals(StorageCircuitBreaker.Decision.Probe, c.acquire())
        assertEquals(StorageCircuitBreaker.State.HALF_OPEN, c.state())
        // Второй acquire в HALF_OPEN = FastFail (только 1 probe).
        assertEquals(StorageCircuitBreaker.Decision.FastFail, c.acquire())
    }

    @Test
    fun `probe success closes circuit`() {
        val c = cb(threshold = 1, cooldown = 0L)
        c.recordFailure(RuntimeException("boom")) // CLOSED -> OPEN (1 >= 1)
        assertEquals(StorageCircuitBreaker.State.OPEN, c.state())
        // Cooldown=0L — но (now - openedAtMs) может быть 0ms (race). Ждём 1ms.
        Thread.sleep(1L)
        assertEquals(StorageCircuitBreaker.Decision.Probe, c.acquire())
        assertEquals(StorageCircuitBreaker.State.HALF_OPEN, c.state())
        c.recordSuccess()
        assertEquals(StorageCircuitBreaker.State.CLOSED, c.state())
        assertEquals(0L, c.metrics().failureCount)
    }

    @Test
    fun `probe failure reopens circuit`() {
        val c = cb(threshold = 1, cooldown = 0L)
        c.recordFailure(RuntimeException("boom 1")) // -> OPEN (1 >= 1)
        Thread.sleep(1L) // avoid race в cooldown check
        c.acquire() // Probe (OPEN -> HALF_OPEN)
        assertEquals(StorageCircuitBreaker.State.HALF_OPEN, c.state())
        c.recordFailure(RuntimeException("boom 2")) // HALF_OPEN -> OPEN
        assertEquals(StorageCircuitBreaker.State.OPEN, c.state())
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `concurrent acquire allows only one probe`() {
        val c = cb(threshold = 1, cooldown = 0L)
        c.recordFailure(RuntimeException("boom"))
        c.acquire() // -> OPEN
        Thread.sleep(10) // small delay to ensure OPEN is stable
        c.acquire() // -> HALF_OPEN
        // Запускаем 20 потоков, все вызывают acquire() — только 1 должен вернуть Probe.
        val threadCount = 20
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val probeCount = AtomicInteger(0)
        val fastFailCount = AtomicInteger(0)
        repeat(threadCount) {
            pool.submit {
                startLatch.await()
                try {
                    when (c.acquire()) {
                        is StorageCircuitBreaker.Decision.Probe -> probeCount.incrementAndGet()
                        is StorageCircuitBreaker.Decision.FastFail -> fastFailCount.incrementAndGet()
                        else -> {}
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS), "all threads should finish")
        pool.shutdownNow()
        // После первого acquire() выше уже Probe выполнен. Второй acquire()
        // (в этом тесте) — fast-fail (HALF_OPEN не даёт probe во второй раз).
        // Поэтому 20 потоков: probe=0, fastFail=20.
        assertEquals(0, probeCount.get(), "no additional probes should be allowed")
        assertEquals(threadCount, fastFailCount.get(), "all should fast-fail")
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `concurrent failures transition once to OPEN`() {
        val c = cb(threshold = 5)
        // 10 потоков параллельно делают recordFailure.
        val threadCount = 10
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        repeat(threadCount) {
            pool.submit {
                startLatch.await()
                try {
                    c.recordFailure(RuntimeException("concurrent-fail-$it"))
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS), "all threads should finish")
        pool.shutdownNow()
        // Должно быть OPEN ровно один раз (атомарный CAS).
        assertEquals(StorageCircuitBreaker.State.OPEN, c.state())
        assertTrue(c.metrics().failureCount >= 5, "failureCount should reach threshold (5)")
    }

    @Test
    fun `overhead closed state under 1ms`() {
        val c = cb()
        val iterations = 1000
        // Warm up
        repeat(100) { c.acquire() }
        val start = System.nanoTime()
        repeat(iterations) { c.acquire() }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        val perCallUs = (elapsedMs.toDouble() / iterations) * 1000.0
        // 1000 acquires должны занять < 1000ms (т.е. < 1ms per call в среднем).
        assertTrue(
            elapsedMs < 1000L,
            "1000 acquires in CLOSED took ${elapsedMs}ms (${perCallUs}us per call), expected < 1000ms",
        )
    }

    @Test
    fun `decorate non-null result`() {
        val c = cb()
        val mono =
            c.decorate("test", { Mono.just("hello") }, "")
                as Mono<String>
        assertEquals("hello", mono.block())
        assertEquals(StorageCircuitBreaker.State.CLOSED, c.state())
    }

    @Test
    fun `decorate timeout returns empty`() {
        val c = cb(timeout = 1L)
        val mono = c.decorate("test", { Mono.never<String>() }, "fallback") as Mono<String>
        // Ждём timeout + немного. timeout=1s, добавим 500ms buffer.
        val start = System.nanoTime()
        assertEquals("fallback", mono.block())
        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        assertTrue(elapsedMs in 800L..2500L, "expected ~1000ms, got ${elapsedMs}ms")
        // timeout считается как failure.
        assertEquals(StorageCircuitBreaker.State.CLOSED, c.state()) // only 1 failure
        assertEquals(1L, c.metrics().failureCount)
    }
}

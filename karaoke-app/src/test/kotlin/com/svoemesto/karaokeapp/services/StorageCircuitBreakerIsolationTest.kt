package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pass 429 (#153): тесты **изоляции** local/remote circuit breaker'ов и
 * blocking-API [StorageCircuitBreaker.executeBlocking].
 *
 * Ключевая гарантия: сбой/восстановление одного бэкенда не влияет на другой —
 * это два независимых экземпляра с раздельным состоянием.
 */
class StorageCircuitBreakerIsolationTest {
    private fun cb(storageType: String, threshold: Int = 3) =
        StorageCircuitBreaker(
            timeoutSeconds = 5L,
            threshold = threshold,
            cooldownSeconds = 30L,
            watchdogEnabled = false,
            watchdogBufferSeconds = 10L,
            checkIntervalSeconds = 1L,
            storageType = storageType,
        )

    @Test
    fun `local OPEN does not affect remote and vice versa`() {
        val local = cb("local", threshold = 1)
        val remote = cb("remote", threshold = 1)

        local.recordFailure(RuntimeException("local down"))
        assertEquals(StorageCircuitBreaker.State.OPEN, local.state())
        assertEquals(
            StorageCircuitBreaker.State.CLOSED,
            remote.state(),
            "remote must stay CLOSED when local opens",
        )
        assertEquals(0L, remote.metrics().totalNetworkFailures)

        remote.recordFailure(RuntimeException("remote down"))
        assertEquals(StorageCircuitBreaker.State.OPEN, remote.state())
        // local уже открыт — не изменился от remote-сбоя
        assertEquals(StorageCircuitBreaker.State.OPEN, local.state())
    }

    @Test
    fun `isolated counters and openedAt`() {
        val local = cb("local", threshold = 5)
        val remote = cb("remote", threshold = 5)
        repeat(2) { remote.recordFailure(RuntimeException("r$it")) }
        repeat(4) { local.recordFailure(RuntimeException("l$it")) }

        assertEquals(4L, local.metrics().failureCount)
        assertEquals(2L, remote.metrics().failureCount)
        assertEquals(4L, local.metrics().totalNetworkFailures)
        assertEquals(2L, remote.metrics().totalNetworkFailures)
    }

    @Test
    fun `executeBlocking fast-fails when OPEN without calling loader`() {
        val local = cb("local", threshold = 1)
        local.recordFailure(RuntimeException("boom"))
        assertEquals(StorageCircuitBreaker.State.OPEN, local.state())

        val called = AtomicBoolean(false)
        val result =
            local.executeBlocking(
                operation = "fileExists",
                loader = {
                    called.set(true)
                    true
                },
                emptyValue = false,
            )
        assertFalse(result, "OPEN must return emptyValue")
        assertFalse(called.get(), "loader must NOT be called on FastFail")
    }

    @Test
    fun `executeBlocking success records success`() {
        val local = cb("local", threshold = 5)
        val result =
            local.executeBlocking(
                operation = "fileExists",
                loader = { "ok" },
                emptyValue = "empty",
            )
        assertEquals("ok", result)
        assertTrue(local.metrics().totalSuccesses >= 1)
        assertEquals(StorageCircuitBreaker.State.CLOSED, local.state())
    }

    @Test
    fun `executeBlocking failure records failure and returns empty`() {
        val local = cb("local", threshold = 2)
        val attempts = AtomicInteger(0)
        val result =
            local.executeBlocking<String>(
                operation = "getFileStat",
                loader = {
                    attempts.incrementAndGet()
                    throw RuntimeException("minio down")
                },
                emptyValue = "empty",
            )
        assertEquals("empty", result)
        assertEquals(1, attempts.get())
        assertEquals(1L, local.metrics().failureCount)
    }

    @Test
    fun `remote breaker stays usable while local is OPEN`() {
        val local = cb("local", threshold = 1)
        val remote = cb("remote", threshold = 1)
        local.recordFailure(RuntimeException("local down"))

        val called = AtomicBoolean(false)
        val result =
            remote.executeBlocking(
                operation = "fileExists",
                loader = {
                    called.set(true)
                    true
                },
                emptyValue = false,
            )
        assertTrue(called.get(), "remote loader must be called (remote CLOSED)")
        assertTrue(result)
    }
}

package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Юнит-тесты для `PollingCache.kt` (Pass 341 + Pass 343 FIFO hard-cap, спека #344).
 *
 * Счётчики `hits`/`misses` отражают КАЖДЫЙ вызов `getOrCompute`:
 *  - miss инкрементируется ДО loader (если ключ отсутствует или истёк TTL).
 *  - hit инкрементируется при cache hit.
 *
 * Стиль: чтобы соответствовать ktlint `multiline-expression-wrapping`, все loader'ы
 * предварительно объявляются как `val`.
 *
 * @see specs/344-storage-metadata-cache/spec.md (FR-005, FR-006)
 */
@Suppress("MaxLineLength")
class PollingCacheTest {
    @Test
    fun `getOrCompute returns loader result on cache miss`() {
        val cache = PollingCache<String>()
        val calls = AtomicInteger(0)
        val loader: () -> String = {
            calls.incrementAndGet()
            "loaded"
        }
        val r = cache.getOrCompute(key = "key1", ttlSeconds = 300, loader = loader)
        assertEquals("loaded", r)
        assertEquals(1, calls.get())
        assertEquals(1, cache.misses(), "first call is a miss")
        assertEquals(0, cache.hits())
        assertEquals(1, cache.size())
    }

    @Test
    fun `getOrCompute returns cached value on cache hit (within TTL)`() {
        val cache = PollingCache<String>()
        val calls = AtomicInteger(0)
        val firstLoader: () -> String = {
            calls.incrementAndGet()
            "first"
        }
        val secondLoader: () -> String = {
            calls.incrementAndGet()
            "second"
        }
        cache.getOrCompute(key = "key1", ttlSeconds = 300, loader = firstLoader)
        val r = cache.getOrCompute(key = "key1", ttlSeconds = 300, loader = secondLoader)
        assertEquals("first", r, "must return cached value, not second")
        assertEquals(1, calls.get(), "loader should be called exactly once")
        assertEquals(1, cache.misses())
        assertEquals(1, cache.hits())
    }

    @Test
    fun `getOrCompute re-invokes loader after TTL expiry (ttl=0)`() {
        val cache = PollingCache<String>()
        val calls = AtomicInteger(0)
        val firstLoader: () -> String = {
            calls.incrementAndGet()
            "first"
        }
        val secondLoader: () -> String = {
            calls.incrementAndGet()
            "second"
        }
        cache.getOrCompute(key = "key1", ttlSeconds = 0, loader = firstLoader)
        val r = cache.getOrCompute(key = "key1", ttlSeconds = 0, loader = secondLoader)
        assertEquals("second", r)
        assertEquals(2, calls.get())
        assertEquals(2, cache.misses())
        assertEquals(0, cache.hits())
    }

    @Test
    fun `concurrent getOrCompute is thread-safe`() {
        val cache = PollingCache<Int>()
        val threadCount = 10
        val callsPerThread = 100
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        repeat(threadCount) { threadIdx ->
            pool.submit {
                startLatch.await()
                try {
                    repeat(callsPerThread) { i ->
                        val expected = i
                        val loader: () -> Int = { expected }
                        cache.getOrCompute(key = "key-$i", ttlSeconds = 300, loader = loader)
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "all threads should finish")
        pool.shutdownNow()
        assertEquals(callsPerThread, cache.size(), "should have exactly callsPerThread keys")
        assertEquals((threadCount * callsPerThread).toLong(), cache.hits() + cache.misses())
    }

    @Test
    fun `size and clear work as expected`() {
        val cache = PollingCache<String>()
        assertEquals(0, cache.size())
        cache.getOrCompute(key = "a", ttlSeconds = 300, loader = { "1" })
        cache.getOrCompute(key = "b", ttlSeconds = 300, loader = { "2" })
        cache.getOrCompute(key = "c", ttlSeconds = 300, loader = { "3" })
        assertEquals(3, cache.size())
        assertEquals(3, cache.misses(), "three different keys → three misses")
        assertEquals(0, cache.hits())
        cache.clear()
        assertEquals(0, cache.size())
        assertEquals(0L, cache.hits())
        assertEquals(0L, cache.misses())
        assertEquals(0L, cache.evictions())
    }

    /**
     * Test #6 (T030, NFR-002): maxEntries hard cap — при превышении entries удаляются,
     * evictions++ увеличивается.
     */
    @Test
    fun `maxEntries hard cap triggers eviction when size exceeds limit`() {
        val cache = PollingCache<String>(maxEntries = 5)
        val loader: (Int) -> String = { i -> "value-$i" }
        repeat(10) { i ->
            val l: () -> String = { loader(i) }
            cache.getOrCompute(key = "key-$i", ttlSeconds = 300, loader = l)
        }
        assertFalse(cache.size() > 5, "size ${cache.size()} should not exceed maxEntries=5")
        assertTrue(cache.evictions() > 0, "evictions should be > 0, got ${cache.evictions()}")
        assertEquals(10L, cache.misses())
        assertEquals(0L, cache.hits())
        assertEquals(10L, cache.hits() + cache.misses())
    }
}

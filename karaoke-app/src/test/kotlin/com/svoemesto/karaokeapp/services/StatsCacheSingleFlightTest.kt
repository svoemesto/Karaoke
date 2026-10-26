package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.StatsCacheKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Регрессия BR-226 (аудит #336, OpenProject #409): TTL-кеш статистики
 * работает с single-flight — тяжёлый агрегат считает только один поток.
 *
 * ДО правки `StatsController.respondCached` делал `get` → (промах) → `compute`
 * → `put`. Два одновременных запроса одного endpoint'а после истечения TTL
 * оба видели промах и оба выполняли `COUNT(*)` по `tbl_events` (250k+ строк).
 * Доменный инвариант caching: «Single-flight guard: только один поток
 * запускает refresh» (`knowledge/domains/caching/domain.md`).
 *
 * @see specs/409-pkg-s3-35/report.md § BR-226
 */
class StatsCacheSingleFlightTest {
    @Test
    fun `only one thread computes the value on a cache miss`() {
        val key = StatsCacheKey("summary", mapOf("target" to "local"))
        StatsCache.invalidateAll()
        val calls = AtomicInteger(0)
        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        val results = mutableListOf<Any?>()
        try {
            repeat(threads) {
                pool.submit {
                    start.await()
                    val value =
                        StatsCache.getOrCompute(key) {
                            calls.incrementAndGet()
                            Thread.sleep(150)
                            mapOf("total" to 42)
                        }
                    synchronized(results) { results.add(value) }
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(20, TimeUnit.SECONDS), "потоки не завершились за 20 с")
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, calls.get(), "тяжёлый агрегат должен считаться ОДИН раз (BR-226)")
        assertEquals(threads, results.size)
        results.forEach { assertSame(results.first(), it, "все потоки должны получить один результат (BR-226)") }
    }

    @Test
    fun `cached value is reused without recomputation`() {
        val key = StatsCacheKey("timeseries", mapOf("target" to "local"))
        StatsCache.invalidateAll()
        val calls = AtomicInteger(0)
        StatsCache.getOrCompute(key) {
            calls.incrementAndGet()
            mapOf("days" to 7)
        }
        StatsCache.getOrCompute(key) {
            calls.incrementAndGet()
            mapOf("days" to 7)
        }
        assertEquals(1, calls.get(), "повторный запрос должен брать значение из кеша (BR-226)")
    }

    @Test
    fun `failure of the computing thread is propagated to the waiting threads`() {
        val key = StatsCacheKey("channels", mapOf("target" to "local"))
        StatsCache.invalidateAll()
        val calls = AtomicInteger(0)
        val threads = 4
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        val errors = mutableListOf<Throwable?>()
        try {
            repeat(threads) {
                pool.submit {
                    start.await()
                    try {
                        StatsCache.getOrCompute(key) {
                            calls.incrementAndGet()
                            Thread.sleep(150)
                            throw IllegalStateException("db.unavailable")
                        }
                    } catch (e: Throwable) {
                        synchronized(errors) { errors.add(e) }
                    }
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(20, TimeUnit.SECONDS), "потоки не завершились за 20 с")
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, calls.get(), "при ошибке считает только один поток (BR-226)")
        assertEquals(threads, errors.size)
        errors.forEach { assertTrue(it is IllegalStateException, "ошибка должна дойти до ожидающих как есть (BR-226)") }
    }
}

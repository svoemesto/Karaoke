package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.HealthReportBatchPool
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit-тесты для второго пула WAITING-задач в [HealthReportBatchPool]
 * (OpenProject #132, specs/132-hrwaiting-pool).
 *
 * Покрывает:
 *  1. parseWaitingTask: `"<fileType>/<location.name>"` → `(songId, ordinal)`.
 *  2. enqueueWaiting: вставка в голову, дедуп, move-to-front, фильтрация мусора.
 *  3. waitingQueueSize.
 *  4. single-flight: одна задача не берётся двумя worker'ами.
 *  5. sendWaitingPoolSizeMessage: подавление дублей (lastSentWaitingPoolSize).
 *  6. attachHealthReportBatchPool: привязка пула к companion HealthReport.
 *
 * Worker-loop зависит от `HealthReport.recomputeAndBroadcast` (обращения в БД/MinIO),
 * поэтому в тестах `waitingWorkersEnabled = false` — пул проверяется напрямую.
 */
class HealthReportWaitingPoolTest {
    private lateinit var pool: HealthReportBatchPool

    @BeforeEach
    fun setUp() {
        HealthReportBatchPool.resetLastSentQueueSizeForTest()
        HealthReportBatchPool.resetLastSentWaitingPoolSizeForTest()
        pool =
            HealthReportBatchPool(
                storageService = Mockito.mock(KaraokeStorageService::class.java),
                storageApiClient = Mockito.mock(StorageApiClient::class.java),
            )
        pool.workersEnabled = false
        pool.waitingWorkersEnabled = false
    }

    @AfterEach
    fun tearDown() {
        if (::pool.isInitialized) {
            pool.executor.shutdownNow()
            pool.waitingExecutor.shutdownNow()
        }
        HealthReportBatchPool.resetLastSentQueueSizeForTest()
        HealthReportBatchPool.resetLastSentWaitingPoolSizeForTest()
    }

    // --- parseWaitingTask ---

    @Test
    fun `parseWaitingTask extracts REMOTE_STORAGE ordinal`() {
        assertEquals(
            7L to KaraokeFileTypeLocations.REMOTE_STORAGE.ordinal,
            HealthReportBatchPool.parseWaitingTask(7L, "MP3_ACCOMPANIMENT/REMOTE_STORAGE"),
        )
    }

    @Test
    fun `parseWaitingTask extracts LOCAL_STORAGE ordinal`() {
        assertEquals(
            3L to KaraokeFileTypeLocations.LOCAL_STORAGE.ordinal,
            HealthReportBatchPool.parseWaitingTask(3L, "COVER/LOCAL_STORAGE"),
        )
    }

    @Test
    fun `parseWaitingTask returns null for malformed description`() {
        assertNull(HealthReportBatchPool.parseWaitingTask(1L, "no-slash"))
        assertNull(HealthReportBatchPool.parseWaitingTask(1L, "COVER/"))
    }

    @Test
    fun `parseWaitingTask returns null for unknown location name`() {
        assertNull(HealthReportBatchPool.parseWaitingTask(1L, "COVER/NOWHERE"))
    }

    @Test
    fun `parseWaitingTask returns null for non-positive songId`() {
        assertNull(HealthReportBatchPool.parseWaitingTask(0L, "COVER/REMOTE_STORAGE"))
        assertNull(HealthReportBatchPool.parseWaitingTask(-1L, "COVER/REMOTE_STORAGE"))
    }

    // --- enqueueWaiting ---

    @Test
    fun `waitingQueue starts empty by default`() {
        assertEquals(0, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting inserts tasks at head preserving batch order`() {
        pool.enqueueWaiting(listOf(1L to 2, 2L to 2, 3L to 2))
        // Батч целиком в голове, внутренний порядок сохранён.
        assertEquals(listOf(1L to 2, 2L to 2, 3L to 2), drainWaiting())
    }

    @Test
    fun `enqueueWaiting deduplicates by (songId, location)`() {
        pool.enqueueWaiting(listOf(1L to 2, 1L to 2, 2L to 2))
        assertEquals(2, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting move-to-front for existing task`() {
        pool.enqueueWaiting(listOf(1L to 2, 2L to 2, 3L to 2))
        // 3 уже в очереди — при повторе всплывает в голову.
        pool.enqueueWaiting(listOf(3L to 2))
        assertEquals(listOf(3L to 2, 1L to 2, 2L to 2), drainWaiting())
    }

    @Test
    fun `enqueueWaiting distinguishes same song different locations`() {
        pool.enqueueWaiting(listOf(1L to 0, 1L to 1, 1L to 2))
        assertEquals(3, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting empty list is no-op`() {
        pool.enqueueWaiting(emptyList())
        assertEquals(0, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting filters negative songIds and ordinals`() {
        pool.enqueueWaiting(listOf(0L to 2, -1L to 2, 5L to -1, 5L to 2))
        assertEquals(1, pool.waitingQueueSize())
        assertEquals(5L to 2, drainWaiting().single())
    }

    @Test
    fun `waitingQueueSize returns correct value after enqueue and take`() {
        pool.enqueueWaiting(listOf(1L to 2, 2L to 2))
        assertEquals(2, pool.waitingQueueSize())
        pool.waitingQueue.pollFirst()
        assertEquals(1, pool.waitingQueueSize())
    }

    @Test
    fun `concurrent enqueueWaiting from many threads keeps queue consistent`() {
        val threads = 8
        val perThread = 25
        val executors = Executors.newFixedThreadPool(threads)
        val futures =
            (0 until threads).map { t ->
                executors.submit {
                    repeat(perThread) { i ->
                        val id = (t * perThread + i + 1).toLong()
                        pool.enqueueWaiting(listOf(id to 2))
                    }
                }
            }
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executors.shutdown()
        assertEquals(threads * perThread, pool.waitingQueueSize())
    }

    // --- single-flight ---

    @Test
    fun `waitingInFlight single-flight guards two concurrent workers`() {
        val task = 1L to 2
        assertTrue(tryEnterWaiting(task))
        // Второй worker не должен войти.
        assertFalse(tryEnterWaiting(task))
        exitWaiting(task)
        // После exit — снова можно.
        assertTrue(tryEnterWaiting(task))
        exitWaiting(task)
    }

    @Test
    fun `enqueueWaiting skips task currently in-flight`() {
        val task = 1L to 2
        assertTrue(tryEnterWaiting(task))
        // Задача в работе — повторный enqueue не должен возвращать её в очередь
        // (иначе recomputeAndBroadcast изнутри worker'а дал бы busy-loop).
        pool.enqueueWaiting(listOf(task))
        assertEquals(0, pool.waitingQueueSize())
        exitWaiting(task)
    }

    // --- SSE dedup ---

    @Test
    fun `sendWaitingPoolSizeMessage suppresses duplicates`() {
        assertNull(readLastSentWaitingPoolSize())
        HealthReportBatchPool.sendWaitingPoolSizeMessage(3L)
        assertEquals(3L, readLastSentWaitingPoolSize())
        HealthReportBatchPool.sendWaitingPoolSizeMessage(3L)
        assertEquals(3L, readLastSentWaitingPoolSize())
        HealthReportBatchPool.sendWaitingPoolSizeMessage(5L)
        assertEquals(5L, readLastSentWaitingPoolSize())
    }

    @Test
    fun `resetLastSentWaitingPoolSizeForTest clears state`() {
        HealthReportBatchPool.sendWaitingPoolSizeMessage(9L)
        assertEquals(9L, readLastSentWaitingPoolSize())
        HealthReportBatchPool.resetLastSentWaitingPoolSizeForTest()
        assertNull(readLastSentWaitingPoolSize())
    }

    @Test
    fun `enqueueWaiting updates lastSentWaitingPoolSize`() {
        pool.enqueueWaiting(listOf(1L to 2, 2L to 2))
        assertEquals(2L, readLastSentWaitingPoolSize())
    }

    // --- attach ---

    @Test
    fun `attachHealthReportBatchPool wires companion reference`() {
        HealthReport.attachHealthReportBatchPool(pool)
        assertEquals(pool, HealthReport.healthReportBatchPool)
    }

    // --- helpers ---

    private fun tryEnterWaiting(task: Pair<Long, Int>): Boolean =
        invokePrivate("tryEnterWaiting", task)

    private fun exitWaiting(task: Pair<Long, Int>) {
        invokePrivate<Unit>("exitWaiting", task)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokePrivate(name: String, arg: Any): T {
        val m = pool.javaClass.getDeclaredMethod(name, Pair::class.java)
        m.isAccessible = true
        return m.invoke(pool, arg) as T
    }

    private fun drainWaiting(): List<Pair<Long, Int>> {
        val result = mutableListOf<Pair<Long, Int>>()
        while (true) {
            val next = pool.waitingQueue.pollFirst() ?: break
            result.add(next)
            if (result.size > 10000) error("infinite loop in drainWaiting()")
        }
        return result
    }

    private fun readLastSentWaitingPoolSize(): Long? {
        val field = HealthReportBatchPool::class.java.getDeclaredField("lastSentWaitingPoolSize")
        field.isAccessible = true
        return field.get(null) as Long?
    }
}

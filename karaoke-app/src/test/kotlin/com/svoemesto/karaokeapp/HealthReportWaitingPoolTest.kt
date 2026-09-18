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
 * Модель: **одно задание = один файл** ([HealthReportBatchPool.WaitingFileTask]).
 * Покрывает enqueueWaiting (голова/дедуп/move-to-front/фильтр/concurrent),
 * single-flight, SSE-dedup, attach и `@PreDestroy` (оба executor'а).
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

    private fun task(songId: Long, fileName: String, source: String = "REMOTE"): HealthReportBatchPool.WaitingFileTask =
        HealthReportBatchPool.WaitingFileTask(
            songId = songId,
            source = source,
            bucket = "karaoke",
            fileName = fileName,
        )

    // --- enqueueWaiting ---

    @Test
    fun `waitingQueue starts empty by default`() {
        assertEquals(0, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting inserts tasks at head preserving batch order`() {
        val a = task(1, "a.mp3")
        val b = task(1, "b.mp3")
        val c = task(2, "c.mp3")
        pool.enqueueWaiting(listOf(a, b, c))
        assertEquals(listOf(a, b, c), drainWaiting())
    }

    @Test
    fun `enqueueWaiting deduplicates by file task`() {
        pool.enqueueWaiting(listOf(task(1, "a.mp3"), task(1, "a.mp3"), task(2, "a.mp3")))
        assertEquals(2, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting move-to-front for existing task`() {
        val a = task(1, "a.mp3")
        val b = task(1, "b.mp3")
        val c = task(1, "c.mp3")
        pool.enqueueWaiting(listOf(a, b, c))
        // c уже в очереди — при повторе всплывает в голову.
        pool.enqueueWaiting(listOf(c))
        assertEquals(listOf(c, a, b), drainWaiting())
    }

    @Test
    fun `enqueueWaiting distinguishes files of same song`() {
        pool.enqueueWaiting(listOf(task(1, "a.mp3"), task(1, "b.mp3"), task(1, "c.mp3")))
        assertEquals(3, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting empty list is no-op`() {
        pool.enqueueWaiting(emptyList())
        assertEquals(0, pool.waitingQueueSize())
    }

    @Test
    fun `enqueueWaiting filters non-positive songId`() {
        pool.enqueueWaiting(listOf(task(0, "a.mp3"), task(-1, "b.mp3"), task(5, "c.mp3")))
        assertEquals(1, pool.waitingQueueSize())
        assertEquals("c.mp3", drainWaiting().single().fileName)
    }

    @Test
    fun `waitingQueueSize returns correct value after enqueue and take`() {
        pool.enqueueWaiting(listOf(task(1, "a.mp3"), task(2, "b.mp3")))
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
                        pool.enqueueWaiting(listOf(task(id, "file-$id.mp3")))
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
        val t = task(1, "a.mp3")
        assertTrue(tryEnterWaiting(t))
        assertFalse(tryEnterWaiting(t))
        exitWaiting(t)
        assertTrue(tryEnterWaiting(t))
        exitWaiting(t)
    }

    @Test
    fun `enqueueWaiting skips task currently in-flight`() {
        val t = task(1, "a.mp3")
        assertTrue(tryEnterWaiting(t))
        // Задача в работе — повторный enqueue не должен возвращать её в очередь
        // (иначе recomputeAndBroadcast изнутри worker'а дал бы busy-loop).
        pool.enqueueWaiting(listOf(t))
        assertEquals(0, pool.waitingQueueSize())
        exitWaiting(t)
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
        pool.enqueueWaiting(listOf(task(1, "a.mp3"), task(2, "b.mp3")))
        assertEquals(2L, readLastSentWaitingPoolSize())
    }

    // --- attach ---

    @Test
    fun `attachHealthReportBatchPool wires companion reference`() {
        HealthReport.attachHealthReportBatchPool(pool)
        assertEquals(pool, HealthReport.healthReportBatchPool)
    }

    // --- lifecycle ---

    @Test
    fun `stop shuts down both executors`() {
        pool.stop()
        assertTrue(pool.executor.isShutdown)
        assertTrue(pool.waitingExecutor.isShutdown)
    }

    // --- helpers ---

    private fun tryEnterWaiting(t: HealthReportBatchPool.WaitingFileTask): Boolean =
        invokePrivate("tryEnterWaiting", t)

    private fun exitWaiting(t: HealthReportBatchPool.WaitingFileTask) {
        invokePrivate<Unit>("exitWaiting", t)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokePrivate(name: String, arg: Any): T {
        val m = pool.javaClass.getDeclaredMethod(name, HealthReportBatchPool.WaitingFileTask::class.java)
        m.isAccessible = true
        return m.invoke(pool, arg) as T
    }

    private fun drainWaiting(): List<HealthReportBatchPool.WaitingFileTask> {
        val result = mutableListOf<HealthReportBatchPool.WaitingFileTask>()
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

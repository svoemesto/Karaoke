package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Регрессия BR-132 (аудит #336, PKG-S2-11): «побудка» воркеров не создаёт новые
 * бесконечные циклы в executor'е.
 *
 * До правки каждый `enqueue` делал `repeat(POOL_SIZE) { executor.submit { workerLoop() } }`,
 * а `workerLoop` — бесконечный цикл. Все 10 потоков навсегда занимали первые
 * submit'ы, а последующие задачи копились в неограниченной очереди executor'а
 * (тысячи никогда не выполненных FutureTask за одну загрузку страницы).
 *
 * Тест подменяет executor на счётчик: после 20 `enqueue`/`enqueueWaiting` не
 * должно быть НИ ОДНОГО submit'а (воркеры запускаются один раз в `start()`).
 */
class HealthReportBatchPoolWakeupTest {
    /** Executor, который только считает submit'ы и не исполняет задачи. */
    private class CountingExecutor : AbstractExecutorService() {
        val submitted = AtomicInteger()

        override fun execute(command: Runnable) {
            submitted.incrementAndGet()
        }

        override fun shutdown() = Unit

        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()

        override fun isShutdown(): Boolean = false

        override fun isTerminated(): Boolean = false

        override fun awaitTermination(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = true
    }

    private lateinit var pool: HealthReportBatchPool
    private lateinit var createdExecutors: List<ExecutorService>
    private lateinit var workerExecutor: CountingExecutor
    private lateinit var waitingExecutor: CountingExecutor

    @BeforeEach
    fun setUp() {
        HealthReportBatchPool.resetLastSentQueueSizeForTest()
        HealthReportBatchPool.resetLastSentWaitingPoolSizeForTest()
        pool =
            HealthReportBatchPool(
                storageService = mock(KaraokeStorageService::class.java),
                storageApiClient = mock(StorageApiClient::class.java),
            )
        createdExecutors = listOf(pool.executor, pool.waitingExecutor)
        workerExecutor = CountingExecutor()
        waitingExecutor = CountingExecutor()
        pool.executor = workerExecutor
        pool.waitingExecutor = waitingExecutor
        pool.workersEnabled = true
        pool.waitingWorkersEnabled = true
    }

    @AfterEach
    fun tearDown() {
        pool.executor.shutdownNow()
        pool.waitingExecutor.shutdownNow()
        createdExecutors.forEach { it.shutdownNow() }
        HealthReportBatchPool.resetLastSentQueueSizeForTest()
        HealthReportBatchPool.resetLastSentWaitingPoolSizeForTest()
    }

    @Test
    fun `enqueue does not submit new worker loops`() {
        repeat(20) { i -> pool.enqueue(listOf(i + 1L)) }
        assertEquals(
            0,
            workerExecutor.submitted.get(),
            "enqueue не должен submit'ить новые бесконечные workerLoop (BR-132)",
        )
    }

    @Test
    fun `enqueueWaiting does not submit new waiting worker loops`() {
        repeat(5) {
            pool.enqueueWaiting(
                listOf(
                    HealthReportBatchPool.WaitingFileTask(
                        songId = it + 1L,
                        source = "local",
                        bucket = "karaoke",
                        fileName = "f$it",
                    ),
                ),
            )
        }
        assertEquals(
            0,
            waitingExecutor.submitted.get(),
            "enqueueWaiting не должен submit'ить новые waitingWorkerLoop (BR-132)",
        )
    }
}

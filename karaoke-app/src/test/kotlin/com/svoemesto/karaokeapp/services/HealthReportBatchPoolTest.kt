package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit-тесты для [HealthReportBatchPool] (OpenProject #128, specs/128-async-health-report-list).
 *
 * Покрывает:
 *  1. parseSongIds: ; -разделённая строка → List<Long>.
 *  2. enqueue: дедуп по songId.
 *  3. enqueue: повторный enqueue одного id перемещает его в начало.
 *  4. enqueue: новый батч вставляется в начало целиком.
 *  5. parseSongIds: невалидные значения молча отбрасываются.
 *
 * Worker-loop зависит от `HealthReport.recomputeAndBroadcast` (глубокие вызовы
 * в БД/MinIO), поэтому его поведение подменяется через `internal var executor`
 * (см. документацию в `HealthReportBatchPool`).
 */
class HealthReportBatchPoolTest {
    /** Executor, исполняющий задачи в фоне на одном daemon-треде (для теста параллелизма). */
    private val realExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    /** Executor, исполняющий задачи синхронно в вызывающем потоке (для тестов очереди). */
    private val directExecutor: ExecutorService = DirectExecutorService()

    private lateinit var pool: HealthReportBatchPool

    @AfterEach
    fun tearDown() {
        if (::pool.isInitialized) {
            pool.executor.shutdownNow()
        }
        realExecutor.shutdownNow()
        directExecutor.shutdownNow()
    }

    private fun newPoolWithDirectExecutor(): HealthReportBatchPool {
        // Подменяем executor на синхронный И отключаем worker'ы — чтобы тесты
        // дедупа/приоритета не зависели от реального recomputeAndBroadcast
        // (он бьёт в БД/MinIO, мокнуть его нельзя — это companion object).
        val p =
            HealthReportBatchPool(
                storageService = org.mockito.Mockito.mock(KaraokeStorageService::class.java),
                storageApiClient = org.mockito.Mockito.mock(StorageApiClient::class.java),
            )
        p.executor = directExecutor
        p.workersEnabled = false
        return p
    }

    @Test
    fun `parseSongIds semicolon-separated string`() {
        assertEquals(listList(1L, 2L, 3L), HealthReportBatchPool.parseSongIds("1;2;3"))
    }

    @Test
    fun `parseSongIds with surrounding whitespace`() {
        assertEquals(listList(1L, 2L, 3L), HealthReportBatchPool.parseSongIds(" 1 ; 2 ; 3 "))
    }

    @Test
    fun `parseSongIds empty or blank returns empty`() {
        assertEquals(emptyList<Long>(), HealthReportBatchPool.parseSongIds(null))
        assertEquals(emptyList<Long>(), HealthReportBatchPool.parseSongIds(""))
        assertEquals(emptyList<Long>(), HealthReportBatchPool.parseSongIds("   "))
    }

    @Test
    fun `parseSongIds invalid tokens are silently dropped`() {
        // abc и пустой токен отбрасываются; -5 — валидный Long (фильтруется
        // позже, в enqueue, через `if (id <= 0L) continue`).
        assertEquals(listList(1L, 3L, -5L), HealthReportBatchPool.parseSongIds("1;abc;3;;-5"))
    }

    @Test
    fun `enqueue same songId twice does not duplicate`() {
        val p = newPoolWithDirectExecutor()
        p.enqueue(listList(1L, 2L, 3L))
        p.enqueue(listList(2L))
        assertEquals(3, p.queueSize())
    }

    @Test
    fun `enqueue same songId twice moves it to front`() {
        val p = newPoolWithDirectExecutor()
        p.enqueue(listList(1L, 2L, 3L))
        // Забираем первые две (имитируем worker)
        val first = takeNext(p) ?: error("queue should not be empty")
        val second = takeNext(p) ?: error("queue should not be empty")
        assertEquals(1L, first)
        assertEquals(2L, second)
        assertEquals(1, p.queueSize())
        // Теперь ставим id=1 обратно — он должен быть первым
        p.enqueue(listList(1L))
        // Очередь: [1, 3] (1 перемещён в начало)
        assertEquals(2, p.queueSize())
        val a = takeNext(p) ?: error("queue should not be empty")
        val b = takeNext(p) ?: error("queue should not be empty")
        assertEquals(1L, a)
        assertEquals(3L, b)
    }

    @Test
    fun `enqueue batch is inserted at front preserving internal order`() {
        val p = newPoolWithDirectExecutor()
        p.enqueue(listList(1L, 2L, 3L))
        p.enqueue(listList(4L, 5L, 6L))
        // После двух enqueue: [4, 5, 6, 1, 2, 3] — последний батч целиком
        // впереди (asReversed + add(0, ...) → первый id батча = самый первый
        // в под-блоке), затем первый батч в исходном порядке.
        assertEquals(listList(4L, 5L, 6L, 1L, 2L, 3L), drain(p))
    }

    @Test
    fun `enqueue empty list is no-op`() {
        val p = newPoolWithDirectExecutor()
        p.enqueue(emptyList())
        assertEquals(0, p.queueSize())
    }

    @Test
    fun `enqueue with zero or negative ids is filtered`() {
        val p = newPoolWithDirectExecutor()
        p.enqueue(listList(0L, -1L, 5L))
        assertEquals(1, p.queueSize())
        assertEquals(5L, takeNext(p) ?: error("queue should not be empty"))
    }

    @Test
    fun `concurrent enqueue from many threads keeps queue consistent`() {
        val p = newPoolWithDirectExecutor()
        val threads = 8
        val perThread = 50
        val executors = Executors.newFixedThreadPool(threads)
        val futures =
            (0 until threads).map { t ->
                executors.submit {
                    repeat(perThread) { i ->
                        // id начинаются с 1 — id=0 отбрасывается фильтром.
                        val id = (t * perThread + i + 1).toLong()
                        p.enqueue(listList(id))
                    }
                }
            }
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executors.shutdown()
        // Без дубликатов: каждый enqueue использует уникальный id > 0.
        assertEquals(threads * perThread, p.queueSize())
    }

    /** Хелпер: взять первый id из очереди (минуя worker). null если очередь пуста. */
    private fun takeNext(p: HealthReportBatchPool): Long? {
        val cls = p.javaClass
        val m = cls.getDeclaredMethod("takeNext")
        m.isAccessible = true
        return m.invoke(p) as Long?
    }

    /** Хелпер: вынуть всю очередь по порядку. */
    private fun drain(p: HealthReportBatchPool): List<Long> {
        val result = mutableListOf<Long>()
        while (true) {
            val n = takeNext(p) ?: break
            result.add(n)
            if (result.size > 10000) fail("infinite loop in drain()")
        }
        return result
    }

    private fun listList(vararg values: Long): List<Long> = values.toList()

    private fun fail(msg: String): Nothing = throw AssertionError(msg)

    /**
     * Минимальный ExecutorService, исполняющий каждую задачу синхронно в текущем
     * потоке. Используется в тестах очереди, чтобы избежать гонок с реальным
     * worker-loop'ом.
     */
    private class DirectExecutorService : AbstractExecutorService() {
        private val running = AtomicInteger(1)

        override fun execute(command: Runnable) {
            command.run()
        }

        override fun shutdown() {
            running.set(0)
        }

        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()

        override fun isShutdown(): Boolean = running.get() == 0

        override fun isTerminated(): Boolean = running.get() == 0

        override fun awaitTermination(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = true

        @Throws(InterruptedException::class, TimeoutException::class)
        override fun <T : Any?> submit(
            task: Callable<T>,
        ): Future<T> {
            val ft = FutureTask(task)
            ft.run()
            return ft
        }
    }
}

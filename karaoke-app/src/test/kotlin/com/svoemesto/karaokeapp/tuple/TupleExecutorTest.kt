package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * 674 (FR-001/FR-002/FR-003/FR-007/FR-018): цикл шагов кортежа.
 *
 * @see specs/674-kortege-execution/tasks.md T010/T025/T027
 */
class TupleExecutorTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    private fun step(
        id: Long,
        order: Int,
        withoutControl: Boolean = false,
    ): KaraokeProcess =
        KaraokeProcess(
            database = FakeKaraokeConnection(),
            storageService = storageService,
            storageApiClient = storageApiClient,
        ).apply {
            this.id = id
            this.processChainId = 100L
            this.order = order
            this.threadId = 1
            this.withoutControl = withoutControl
            this.status = KaraokeProcessStatuses.WAITING.name
            this.type = "DEMUCS2"
            this.songId = 30390
            this.description = "step $order"
        }

    @Test
    fun `шаги кортежа исполняются подряд и кортеж завершается`() {
        val steps = listOf(step(1, 0), step(2, 1), step(3, 2))
        val store = InMemoryTupleStore(steps)
        val runOrder = mutableListOf<Int>()
        val executor =
            TupleExecutor(store) { s ->
                runOrder += s.order
                store.markDone(s)
                KaraokeProcessStatuses.DONE.name
            }

        val result = executor.execute(100L)

        assertEquals(KaraokeProcessStatuses.DONE.name, result)
        assertEquals(listOf(0, 1, 2), runOrder, "шаги обязаны идти по process_order")
        assertEquals(100L, store.completed)
        assertNull(store.failed)
    }

    @Test
    fun `сбой шага останавливает весь кортеж и не запускает следующий`() {
        val steps = listOf(step(1, 0), step(2, 1), step(3, 2))
        val store = InMemoryTupleStore(steps)
        val runOrder = mutableListOf<Int>()
        val executor =
            TupleExecutor(store) { s ->
                runOrder += s.order
                if (s.order == 1) {
                    store.markError(s, "rc=1")
                    KaraokeProcessStatuses.ERROR.name
                } else {
                    store.markDone(s)
                    KaraokeProcessStatuses.DONE.name
                }
            }

        val result = executor.execute(100L)

        assertEquals(KaraokeProcessStatuses.ERROR.name, result)
        assertEquals(listOf(0, 1), runOrder, "третий шаг не должен запускаться")
        assertEquals(100L, store.failed?.first)
        assertNull(store.completed)
    }

    @Test
    fun `force-stop возвращает кортеж в WAITING и не запускает следующий шаг`() {
        val steps = listOf(step(1, 0), step(2, 1))
        val store = InMemoryTupleStore(steps)
        val runOrder = mutableListOf<Int>()
        val executor =
            TupleExecutor(store) { s ->
                runOrder += s.order
                store.markWaiting(s)
                KaraokeProcessStatuses.WAITING.name
            }

        val result = executor.execute(100L)

        assertEquals(KaraokeProcessStatuses.WAITING.name, result)
        assertEquals(listOf(0), runOrder)
        assertEquals(listOf(1L), store.waiting)
    }

    @Test
    fun `withoutControl не отменяет fail-fast`() {
        val steps = listOf(step(1, 0, withoutControl = true), step(2, 1, withoutControl = true))
        val store = InMemoryTupleStore(steps)
        val runOrder = mutableListOf<Int>()
        val executor =
            TupleExecutor(store) { s ->
                runOrder += s.order
                store.markError(s, "rc=1")
                KaraokeProcessStatuses.ERROR.name
            }

        assertEquals(KaraokeProcessStatuses.ERROR.name, executor.execute(100L))
        assertEquals(listOf(0), runOrder, "withoutControl не разрешает шаг N+1 после сбоя N")
        assertTrue(store.failed != null)
    }
}

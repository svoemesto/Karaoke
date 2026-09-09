package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.HealthReport.Companion.attemptEnterRepair
import com.svoemesto.karaokeapp.HealthReport.Companion.exitRepair
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Юнит-тесты для single-flight guard в repair-loop (OpenProject #65).
 *
 * Контекст (Pass 343, spec 426):
 *  - HealthReport.startRepairAll (HTTP-поток, admin UI "Исправить всё").
 *  - HealthReport.onRepairProcessFinished (worker-поток, после завершения задания).
 *  - Оба модифицируют `autoRepairSongIds` и оба вызывают
 *    recomputeAndBroadcast + executeResolvable.
 *  - Без single-flight guard они МОГУТ запуститься параллельно для одной песни,
 *    что приводит к двойному выполнению actions и нестабильному
 *    state.
 *
 * Решение (Pass 343+): perSong single-flight guard через AtomicBoolean
 * (см. companion object HealthReport.kt).
 *
 * Эти тесты верифицируют:
 *  - attemptEnterRepair: первый вызов true, второй (concurrent) false.
 *  - exitRepair: после exit, следующий attemptEnterRepair снова true.
 *  - Параллельные потоки: только ОДИН получает `true` от attemptEnterRepair.
 *
 * @see specs/426-knowledge-detail-2/spec.md (issue #65)
 */
class HealthReportRepairRaceTest {
    private val testSongId = 42L

    @Test
    fun `attemptEnterRepair returns true on first call, false on second call before exit`() {
        // Первый вызов — true.
        assertTrue(attemptEnterRepair(testSongId))
        // Второй вызов ДО exit — false (single-flight).
        assertFalse(attemptEnterRepair(testSongId))
        // Cleanup для других тестов.
        exitRepair(testSongId)
    }

    @Test
    fun `exitRepair allows re-entry`() {
        assertTrue(attemptEnterRepair(testSongId))
        exitRepair(testSongId)
        // После exit — снова true.
        assertTrue(attemptEnterRepair(testSongId))
        exitRepair(testSongId)
    }

    @Test
    fun `concurrent attemptEnterRepair allows exactly one winner`() {
        // 10 потоков, 1 song — только ОДИН должен выиграть.
        val threadCount = 10
        val startGate = CountDownLatch(1)
        val finishGate = CountDownLatch(threadCount)
        val winnerCount = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            pool.submit {
                startGate.await()  // синхронизация старта
                try {
                    if (attemptEnterRepair(testSongId)) {
                        winnerCount.incrementAndGet()
                    }
                } finally {
                    finishGate.countDown()
                }
            }
        }
        startGate.countDown()  // поехали
        check(finishGate.await(5, TimeUnit.SECONDS)) { "Concurrent test timed out" }
        pool.shutdown()

        // Ровно ОДИН поток должен был выиграть.
        assertTrue(winnerCount.get() == 1, "Expected exactly 1 winner, got ${winnerCount.get()}")
        // Cleanup.
        exitRepair(testSongId)
    }

    @Test
    fun `different songIds do not block each other`() {
        // Два разных songId — НЕ блокируют друг друга.
        val songA = 100L
        val songB = 200L
        assertTrue(attemptEnterRepair(songA))
        assertTrue(attemptEnterRepair(songB))  // разные ключи — оба true
        exitRepair(songA)
        exitRepair(songB)
    }
}
package com.svoemesto.karaokeapp

import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Тесты для in-memory счётчика WAITING-записей `HealthReport.companion.waitingCountBySongId`
 * (OpenProject #130, specs/130-hrwaiting-badge).
 *
 * `waitingCountTotal()` суммирует значения по всем ключам. Тесты НЕ вызывают
 * `recomputeAndBroadcast` напрямую (требует БД/MinIO), а напрямую работают с
 * `waitingCountBySongId` + `lastSentWaitingCount` через тестовые hooks.
 *
 * @see HealthReport.waitingCountBySongId
 * @see HealthReport.waitingCountTotal
 */
class HealthReportWaitingCountTest {
    @AfterEach
    fun tearDown() {
        HealthReport.resetLastSentWaitingCountForTest()
        HealthReport.clearWaitingCountBySongIdForTest()
    }

    @Test
    fun `waitingCountBySongId is empty by default`() {
        assertEquals(0, HealthReport.waitingCountBySongId.size)
        assertEquals(0L, HealthReport.waitingCountTotal())
    }

    @Test
    fun `waitingCountTotal sums values across songs`() {
        // Песня 1: 2 WAITING-записи.
        HealthReport.waitingCountBySongId[1L] = AtomicLong(2L)
        // Песня 2: 0 WAITING (не должна появиться — фильтруется в recomputeAndBroadcast,
        // но если кто-то руками поставил 0 — она не должна учитываться в сумме).
        HealthReport.waitingCountBySongId[2L] = AtomicLong(0L)
        // Песня 3: 3 WAITING-записи.
        HealthReport.waitingCountBySongId[3L] = AtomicLong(3L)
        assertEquals(5L, HealthReport.waitingCountTotal())
    }

    @Test
    fun `compute callback removes entry when count becomes zero`() {
        // Симулируем поведение `recomputeAndBroadcast`: если `waitingCount == 0`,
        // ключ удаляется из map (через `compute { _, _ -> null }`).
        HealthReport.waitingCountBySongId[1L] = AtomicLong(2L)
        HealthReport.waitingCountBySongId
            .compute(1L) { _, _ -> null }
        assertEquals(0, HealthReport.waitingCountBySongId.size)
        assertEquals(0L, HealthReport.waitingCountTotal())
    }

    @Test
    fun `lastSentWaitingCount starts null after reset`() {
        assertNull(readLastSentWaitingCount())
    }

    @Test
    fun `sendWaitingCountMessage suppresses duplicates`() {
        // После первого вызова `lastSentWaitingCount` обновляется (хотя SNS.send
        // бросит UninitializedPropertyAccessException в unit-тестах — мы это
        // ловим в catch и продолжаем).
        HealthReport.sendWaitingCountMessage(5L)
        assertEquals(5L, readLastSentWaitingCount())

        // Повторный вызов с тем же значением — не меняет состояние.
        HealthReport.sendWaitingCountMessage(5L)
        assertEquals(5L, readLastSentWaitingCount())

        // Новое значение — обновляет.
        HealthReport.sendWaitingCountMessage(7L)
        assertEquals(7L, readLastSentWaitingCount())
    }

    @Test
    fun `resetLastSentWaitingCountForTest clears state`() {
        HealthReport.sendWaitingCountMessage(42L)
        assertEquals(42L, readLastSentWaitingCount())

        HealthReport.resetLastSentWaitingCountForTest()
        assertNull(readLastSentWaitingCount())
    }

    /** Рефлексия: прочитать значение companion-поля `lastSentWaitingCount`. */
    private fun readLastSentWaitingCount(): Long? {
        val cls = HealthReport::class.java
        val field = cls.getDeclaredField("lastSentWaitingCount")
        field.isAccessible = true
        return field.get(null) as Long?
    }
}

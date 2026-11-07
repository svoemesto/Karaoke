package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Устойчивость очереди заданий (specs/730-queue-resilience).
 *
 * ИНЦИДЕНТ 2026-11-07: очередь регулярно останавливалась и не поднималась сама.
 * Причины: (1) таймаут Playwright на music.yandex.ru в периодической проверке
 * нового альбома прерывал главный цикл очереди; (2) счётчик попыток восстановления
 * считал ВСЕ падения за время жизни контейнера — пять разовых сбоев за несколько
 * дней выключали очередь навсегда.
 */
class QueueRecoveryTest {
    @Test
    fun `долгая нормальная работа начинает новую серию падений`() {
        assertTrue(
            KaraokeProcessWorker.isNewIncident(600_000),
            "после 10 минут нормальной работы падение — новый инцидент, счётчик сбрасывается",
        )
        assertFalse(
            KaraokeProcessWorker.isNewIncident(2_000),
            "быстрое падение — продолжение той же серии",
        )
    }

    @Test
    fun `пять попыток переводят очередь в режим самоподъёма`() {
        assertFalse(KaraokeProcessWorker.attemptsExhausted(4))
        assertTrue(KaraokeProcessWorker.attemptsExhausted(5))
        assertTrue(KaraokeProcessWorker.attemptsExhausted(6))
    }

    @Test
    fun `пауза между попытками не длиннее пяти минут`() {
        for (attempt in 1..50) {
            assertTrue(
                KaraokeProcessWorker.retryPauseMs(attempt) <= 300_000,
                "пауза для попытки $attempt длиннее 5 минут",
            )
        }
        assertEquals(300_000L, KaraokeProcessWorker.retryPauseMs(5), "в режиме самоподъёма пауза 5 минут")
    }

    @Test
    fun `интервал между попытками растёт`() {
        assertTrue(
            KaraokeProcessWorker.retryPauseMs(3) > KaraokeProcessWorker.retryPauseMs(1),
            "пауза растёт с номером попытки",
        )
    }

    @Test
    fun `сбой внешнего сервиса не выходит из проверки альбома`() {
        var recorded: Exception? = null
        val result =
            guardedAlbumCheck<String>({ e -> recorded = e }) {
                throw RuntimeException("TimeoutError: navigating to https://music.yandex.ru/")
            }
        assertNull(result, "исключение не пробрасывается наружу — очередь продолжает работу")
        assertNotNull(recorded, "сбой зафиксирован")
    }

    @Test
    fun `успешная проверка альбома возвращает результат как есть`() {
        val result =
            guardedAlbumCheck<String>({ e -> throw AssertionError("сбой не ожидался", e) }) {
                "AlbumSearchResult.Success"
            }
        assertEquals("AlbumSearchResult.Success", result)
    }
}

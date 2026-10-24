package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.Song
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

/**
 * Регрессия BR-139 (аудит #336, PKG-S2-11): `saveToDbLocked()` = false нельзя
 * игнорировать.
 *
 * До правки возвращаемый `Boolean` игнорировался во всех четырёх местах
 * `TelegramAutoPublishService`: при `lock_timeout` успешно полученный
 * `message_id` не сохранялся, состояние оставалось `PUBLISHING`, и следующий тик
 * публиковал видео повторно. Тест фиксирует retry-контракт `saveWithRetry`.
 */
class TelegramSaveRetryTest {
    @Test
    fun `retries failed save and reports success`() {
        val song = mock(Song::class.java)
        doReturn(false, true).`when`(song).saveToDbLocked()

        assertTrue(TelegramAutoPublishService.saveWithRetry(song, attempts = 3, backoffMs = 0))
    }

    @Test
    fun `reports failure when every attempt fails`() {
        val song = mock(Song::class.java)
        doReturn(false).`when`(song).saveToDbLocked()

        assertFalse(TelegramAutoPublishService.saveWithRetry(song, attempts = 3, backoffMs = 0))
    }
}

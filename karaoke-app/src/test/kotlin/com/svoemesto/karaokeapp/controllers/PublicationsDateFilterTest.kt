package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.model.SongState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка маппинга токенов endpoint `POST /api/publications/date` → [SongState].
 * Контракт — `specs/155-song-state-colors/contracts/publications-date-filter.md`.
 *
 * Использует [ApiController.paramToSongState], чтобы не поднимать Spring-контекст и не
 * ходить в БД.
 */
class PublicationsDateFilterTest {
    @Test
    fun `STATE_DONE резолвится в SongState DONE`() {
        assertEquals(SongState.DONE, ApiController.paramToSongState("STATE_DONE"))
    }

    @Test
    fun `STATE_TODAY резолвится в SongState TODAY`() {
        assertEquals(SongState.TODAY, ApiController.paramToSongState("STATE_TODAY"))
    }

    @Test
    fun `STATE_ON_AIR резолвится в SongState ON_AIR`() {
        assertEquals(SongState.ON_AIR, ApiController.paramToSongState("STATE_ON_AIR"))
    }

    @Test
    fun `STATE_EXCLUSIVE резолвится в SongState EXCLUSIVE`() {
        assertEquals(SongState.EXCLUSIVE, ApiController.paramToSongState("STATE_EXCLUSIVE"))
    }

    @Test
    fun `STATE_IN_WORK резолвится в SongState IN_WORK`() {
        assertEquals(SongState.IN_WORK, ApiController.paramToSongState("STATE_IN_WORK"))
    }

    @Test
    fun `старые STATE_WO токены возвращают null`() {
        assertNull(ApiController.paramToSongState("STATE_WO_TG"))
        assertNull(ApiController.paramToSongState("STATE_WO_VK"))
        assertNull(ApiController.paramToSongState("STATE_WO_DZEN"))
        assertNull(ApiController.paramToSongState("STATE_WO_VKG"))
    }

    @Test
    fun `старые STATUS токены возвращают null`() {
        assertNull(ApiController.paramToSongState("STATUS_0"))
        assertNull(ApiController.paramToSongState("STATUS_1"))
        assertNull(ApiController.paramToSongState("STATUS_6"))
    }

    @Test
    fun `STATE_ALL_DONE и STATE_OVERDUE возвращают null`() {
        // Эти токены жили в старой 16-значной палитре и в новом контракте отсутствуют.
        assertNull(ApiController.paramToSongState("STATE_ALL_DONE"))
        assertNull(ApiController.paramToSongState("STATE_OVERDUE"))
        assertNull(ApiController.paramToSongState("STATE_ALL_UPLOADED"))
    }

    @Test
    fun `произвольная и пустая строка возвращают null`() {
        assertNull(ApiController.paramToSongState(""))
        assertNull(ApiController.paramToSongState("unknown"))
        assertNull(ApiController.paramToSongState("state_done"))
    }
}

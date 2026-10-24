package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Регрессия BR-128 (аудит #336, PKG-S2-11): при нескольких назначениях одной
 * песни выбор детерминирован — активное (`open`), иначе последнее по `id`.
 *
 * До правки `loadBySongIds` схлопывал строки через `associateBy { songId }` и
 * молча оставлял произвольную (порядок `loadList` без `ORDER BY`). Тест
 * фиксирует порядок [SongAssignment.preferredAssignmentOrder].
 */
class SongAssignmentPreferenceTest {
    private fun assignment(
        id: Long,
        status: String,
    ): SongAssignment =
        SongAssignment(
            database = FakeKaraokeConnection(),
            storageService = mock(KaraokeStorageService::class.java),
            storageApiClient = mock(StorageApiClient::class.java),
        ).also {
            it.id = id
            it.songId = 100L
            it.adminStatus = status
        }

    @Test
    fun `active assignment wins over approved even if approved is newer`() {
        val approved = assignment(10, SongAssignmentStatus.ADMIN_APPROVED)
        val open = assignment(5, SongAssignmentStatus.ADMIN_OPEN)
        assertEquals(open, SongAssignment.preferredAssignment(listOf(approved, open)))
    }

    @Test
    fun `without active assignment the latest id wins`() {
        val older = assignment(10, SongAssignmentStatus.ADMIN_APPROVED)
        val newer = assignment(20, SongAssignmentStatus.ADMIN_REJECTED)
        assertEquals(newer, SongAssignment.preferredAssignment(listOf(older, newer)))
        assertEquals(newer, SongAssignment.preferredAssignment(listOf(newer, older)))
    }
}

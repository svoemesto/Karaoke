package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.SongAssignmentStatus
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Регрессионный тест «до/после» для аудита BR-023 «createDbInstance-result-ignored:editor»
 * (OpenProject #378).
 *
 * **ДО правки** ветка создания черновика редактора игнорировала результат
 * `KaraokeDbTable.createDbInstance(...)`: при сбое INSERT или конкурентной вставке по
 * `UNIQUE(assignment_id)` (двойной клик, две вкладки) метод возвращал `null`, а контроллер всё
 * равно отвечал `200 {"ok":true}` — вторая правка молча терялась.
 *
 * **ПОСЛЕ правки** результат проверяется: успешная вставка возвращает созданный черновик;
 * конкурентная вставка доигрывается как upsert (перечитываем существующий черновик и обновляем
 * его — правка не теряется); если сохранить не удалось — `null`, и контроллер отдаёт 5xx
 * `save_failed`.
 */
class PublicSongEditorDraftPersistenceTest {
    private fun controller(): PublicSongEditorController =
        PublicSongEditorController(
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            gestureUnlockService = PlayerGestureUnlockService(),
        )

    private fun draft(
        assignmentId: Long,
        sourceTexts: String,
        markers: String,
    ): SongAssignmentDraft =
        Mockito.mock(SongAssignmentDraft::class.java).also {
            Mockito.`when`(it.assignmentId).thenReturn(assignmentId)
            Mockito.`when`(it.editedSourceText).thenReturn(sourceTexts)
            Mockito.`when`(it.editedMarkers).thenReturn(markers)
            Mockito.`when`(it.userStatus).thenReturn(SongAssignmentStatus.USER_IN_PROGRESS)
        }

    @Test
    fun `успешная вставка возвращает созданный черновик`() {
        val newDraft = draft(assignmentId = 5, sourceTexts = "[\"a\"]", markers = "[[]]")
        var loadExistingCalled = false

        val result =
            controller().persistDraftOrExisting(
                draft = newDraft,
                create = { newDraft },
                loadExisting = {
                    loadExistingCalled = true
                    null
                },
            )

        assertEquals(newDraft, result)
        assertTrue(!loadExistingCalled, "при успешной вставке повторное чтение не нужно")
    }

    @Test
    fun `конкурентная вставка доигрывается как upsert и правка не теряется`() {
        val values = draft(assignmentId = 5, sourceTexts = "[\"НОВАЯ ПРАВКА\"]", markers = "[[{\"x\":1}]]")
        val existing = Mockito.mock(SongAssignmentDraft::class.java)

        val result =
            controller().persistDraftOrExisting(
                draft = values,
                create = { null },
                loadExisting = { existing },
            )

        assertNotNull(result)
        assertEquals(existing, result)
        // Значения новой правки перенесены в существующий черновик и сохранены.
        Mockito.verify(existing).editedSourceText = "[\"НОВАЯ ПРАВКА\"]"
        Mockito.verify(existing).editedMarkers = "[[{\"x\":1}]]"
        Mockito.verify(existing).userStatus = SongAssignmentStatus.USER_IN_PROGRESS
        Mockito.verify(existing).save()
    }

    @Test
    fun `если создать и перечитать не удалось — null и контроллер отдаёт save_failed`() {
        val values = draft(assignmentId = 5, sourceTexts = "[]", markers = "[[]]")

        val result =
            controller().persistDraftOrExisting(
                draft = values,
                create = { null },
                loadExisting = { null },
            )

        assertNull(result)
    }
}

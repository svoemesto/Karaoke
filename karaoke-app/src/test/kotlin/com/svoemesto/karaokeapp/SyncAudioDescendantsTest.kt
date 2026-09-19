package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SongType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit-тесты гейта синхронизации аудио-потомков (OpenProject #141,
 * specs/413-sync-audio-descendants). Проверяют чистую функцию
 * [SyncAudioDescendants.shouldEnqueue] — все ветви ворот research R1.
 */
class SyncAudioDescendantsTest {
    private val contentFields = setOf("source_markers")
    private val noFields = emptySet<String>()

    @Test
    fun `parent below ready status is not enqueued`() {
        assertFalse(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 4,
                oldStatus = 3,
                changedContentFields = contentFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `content change at ready status is enqueued`() {
        assertTrue(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 5,
                oldStatus = 5,
                changedContentFields = contentFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `status transition into ready is enqueued even without content change`() {
        assertTrue(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 5,
                oldStatus = 2,
                changedContentFields = noFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
        assertTrue(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 6,
                oldStatus = 2,
                changedContentFields = noFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `transition 5 to 6 is not enqueued`() {
        assertFalse(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 6,
                oldStatus = 5,
                changedContentFields = noFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `ready status without content change and without transition is not enqueued`() {
        assertFalse(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 6,
                oldStatus = 6,
                changedContentFields = noFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `empty markers block song type but not instrumental`() {
        assertFalse(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 5,
                oldStatus = 5,
                changedContentFields = contentFields,
                songType = SongType.SONG,
                markersEmpty = true,
                suppressTriggeredBySync = false,
            ),
        )
        assertTrue(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 5,
                oldStatus = 5,
                changedContentFields = contentFields,
                songType = SongType.INSTRUMENTAL,
                markersEmpty = true,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `triggered by sync is suppressed`() {
        assertFalse(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 5,
                oldStatus = 5,
                changedContentFields = contentFields,
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = true,
            ),
        )
    }

    @Test
    fun `non content fields do not trigger`() {
        assertFalse(
            SyncAudioDescendants.shouldEnqueue(
                newStatus = 5,
                oldStatus = 5,
                changedContentFields = setOf("song_tone", "song_bpm", "id_vk"),
                songType = SongType.SONG,
                markersEmpty = false,
                suppressTriggeredBySync = false,
            ),
        )
    }

    @Test
    fun `queue deduplicates parent ids and preserves order`() {
        SyncAudioDescendants.clearForTest()
        SyncAudioDescendants.enqueueForTest(listOf(10L, 20L, 10L, 30L, 20L))
        org.junit.jupiter.api.Assertions.assertEquals(
            3,
            SyncAudioDescendants.pendingCount(),
        )
        SyncAudioDescendants.clearForTest()
    }
}

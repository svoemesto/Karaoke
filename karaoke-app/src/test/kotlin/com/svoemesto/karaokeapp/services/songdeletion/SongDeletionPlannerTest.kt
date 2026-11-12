package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты плана каскадного удаления (KARAOKE-429, спека 739, FR-005…FR-008, FR-046).
 *
 * **Главный рубеж защиты — белый список таблиц.** Тест фиксирует: в плане нет ни
 * одной таблицы вне списка, события чистятся только с `song_id <> 0`, а у потомков
 * обнуляется связь с родителем (сами потомки остаются).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-005, FR-006, FR-007, FR-008
 */
class SongDeletionPlannerTest {
    private val songId = 4242L

    @Test
    fun `план не содержит таблиц вне белого списка`() {
        val plan = SongDeletionPlanner.plan(songId)
        assertTrue(plan.isNotEmpty(), "план каскада не должен быть пустым")
        plan.forEach { step ->
            assertTrue(
                step.table in SongDeletionPlanner.CASCADE_TABLES,
                "таблица ${step.table} вне белого списка",
            )
        }
        assertEquals(
            SongDeletionPlanner.CASCADE_TABLES.toSet(),
            plan.map { it.table }.toSet(),
            "план должен покрывать ровно белый список таблиц",
        )
    }

    @Test
    fun `в белом списке нет ни новостей, ни истории песен, ни аудита`() {
        listOf("tbl_news", "tbl_songs_sync", "audit_log", "songs_history").forEach { table ->
            assertFalse(
                table in SongDeletionPlanner.CASCADE_TABLES,
                "таблица $table не должна чиститься каскадом",
            )
        }
    }

    @Test
    fun `события чистятся только с ненулевым song_id`() {
        val eventsStep = SongDeletionPlanner.plan(songId).first { it.table == SongDeletionPlanner.TABLE_EVENTS }
        assertTrue(
            eventsStep.sql.contains("song_id <> 0"),
            "события без привязки к песне удалять нельзя (FR-007): ${eventsStep.sql}",
        )
    }

    @Test
    fun `черновики удаляются раньше назначений`() {
        val tables = SongDeletionPlanner.plan(songId).map { it.table }
        val drafts = tables.indexOf(SongDeletionPlanner.TABLE_SONG_ASSIGNMENT_DRAFTS)
        val assignments = tables.indexOf(SongDeletionPlanner.TABLE_SONG_ASSIGNMENTS)
        assertTrue(drafts >= 0 && assignments >= 0, "в плане должны быть и черновики, и назначения")
        assertTrue(drafts < assignments, "черновики должны удаляться раньше назначений")
    }

    @Test
    fun `обнуление связи потомков не удаляет их самих`() {
        val step = SongDeletionPlanner.descendantsStep(songId)
        assertEquals(SongDeletionPlanner.TABLE_SONGS, step.table)
        assertTrue(
            step.sql.contains("SET audio_parent_id = 0"),
            "связь обнуляется значением «нет родителя» (схема: NOT NULL DEFAULT 0): ${step.sql}",
        )
        assertFalse(step.sql.contains("DELETE"), "потомки остаются живыми (FR-008): ${step.sql}")
        assertTrue(step.sql.contains("WHERE audio_parent_id = ?"), "затрагиваются только потомки: ${step.sql}")
    }

    @Test
    fun `шаг удаления строки песни удаляет именно её`() {
        val step = SongDeletionPlanner.songStep(songId)
        assertEquals(SongDeletionPlanner.TABLE_SONGS, step.table)
        assertEquals("DELETE FROM tbl_songs WHERE id = ?", step.sql)
        assertEquals(listOf(songId), step.params)
    }

    @Test
    fun `шаг удаления sync-строки работает по таблице синхронизации`() {
        val step = SongDeletionPlanner.songStep(songId, syncTable = true)
        assertEquals("tbl_songs_sync", step.table)
        assertTrue(step.sql.contains("tbl_songs_sync"), "таблица синхронизации: ${step.sql}")
    }
}

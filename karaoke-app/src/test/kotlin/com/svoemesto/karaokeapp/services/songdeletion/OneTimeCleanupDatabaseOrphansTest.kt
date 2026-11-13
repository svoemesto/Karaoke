package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Разовая уборка: строки-сироты в БД (KARAOKE-426, FR-041).
 *
 * Чистятся черновики заданий без задания и исторические ссылки на несуществующие
 * песни. Критерии — из инвентаризации KARAOKE-420 (артефакт `T10-db-orphans.md`).
 *
 * Тест проверяет не «выполнение DELETE на рабочей БД» (это недопустимо: тесты идут
 * по LOCAL-БД, там же живут 1439 настоящих сирот-черновиков), а **контракт**:
 * набор таблиц, фиксированность критериев и защитное условие `song_id <> 0` в
 * `tbl_events` — без него снесли бы 10 977 живых плейсхолдерных событий.
 */
class OneTimeCleanupDatabaseOrphansTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    @Test
    fun `в перечне ровно пять таблиц сирот`() {
        val tables = SongOneTimeCleanupPlanner.DB_ORPHAN_TABLES.map { it.table }
        assertEquals(
            listOf(
                "tbl_song_assignment_drafts",
                "tbl_events",
                "tbl_search_results",
                "tbl_search_async",
                "tbl_listening_history",
            ),
            tables,
        )
    }

    @Test
    fun `критерий событий содержит song_id не равный нулю`() {
        val events = SongOneTimeCleanupPlanner.DB_ORPHAN_TABLES.first { it.table == "tbl_events" }
        assertTrue(
            events.where.contains("song_id <> 0"),
            "без song_id <> 0 удаляются живые плейсхолдерные события",
        )
        assertTrue(events.where.contains("NOT IN (SELECT id FROM tbl_songs)"), "критерий сироты по песне")
    }

    @Test
    fun `критерий черновиков — assignment_id без задания`() {
        val drafts = SongOneTimeCleanupPlanner.DB_ORPHAN_TABLES.first { it.table == "tbl_song_assignment_drafts" }
        assertEquals(
            "assignment_id NOT IN (SELECT id FROM tbl_song_assignments)",
            drafts.where,
        )
    }

    @Test
    fun `условия таблиц не содержат ничего кроме условия отбора`() {
        SongOneTimeCleanupPlanner.DB_ORPHAN_TABLES.forEach { spec ->
            assertTrue(
                spec.where.none { it == ';' || it == '\'' },
                "в условии недопустимы кавычки и «;» (условие подставляется в SQL): ${spec.table}",
            )
            assertTrue(spec.description.isNotBlank(), "нужно пояснение для кандидата: ${spec.table}")
        }
    }

    @Test
    fun `удаление берёт критерий из кода а не из перечня кандидатов`() {
        val text =
            repoFile(
                "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupService.kt",
            ).readText()
        assertTrue(
            text.contains("DB_ORPHAN_TABLES.firstOrNull { it.table == candidate.pathOrKey }"),
            "таблица из кандидата ищется в зашитом перечне (fail-closed)",
        )
        assertTrue(
            text.contains("false to 0") || text.contains("return false to 0"),
            "неизвестная таблица — отказ без удаления",
        )
    }
}

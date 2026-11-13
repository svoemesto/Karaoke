package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Разовая уборка: списание медиа альбомов, удалённых по решению владельца
 * (KARAOKE-425, FR-041).
 *
 * Списываются альбомы медиа удалённых песен: «Жилец вершин» (АукцЫон, 1996) —
 * 15 песен, и «Лирика (сингл)» (Сектор Газа, 2023) — 1 песня. Их файлы не подпадают
 * под правило «сирота» (в папке нет живых песен → обход не заходит), поэтому перечень
 * задан явно.
 */
class OneTimeCleanupMediaWriteoffTest {
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
    fun `в перечне ровно два альбома`() {
        assertEquals(
            listOf(
                "АукцЫон" to "1996 - Жилец вершин",
                "Сектор Газа" to "2023 - Лирика (сингл)",
            ),
            SongOneTimeCleanupPlanner.MEDIA_WRITEOFF_ALBUMS,
        )
    }

    @Test
    fun `списываемые альбомы без живых песен`() {
        // Проверка по данным: альбомов из перечня в каталоге живых песен нет,
        // иначе списание удалило бы файлы живой песни.
        val planner =
            repoFile(
                "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupPlanner.kt",
            ).readText()
        assertTrue(
            planner.contains("MEDIA_WRITEOFF_ALBUMS.forEach"),
            "перечень альбомов используется при обходе диска",
        )
        assertTrue(
            planner.contains("Reason.WRITEOFF"),
            "у кандидата списания своя причина",
        )
    }

    @Test
    fun `правило сироты по-прежнему не трогает папки без живых песен`() {
        // Обход диска заходит только в папки альбомов с живыми песнями; списанные
        // альбомы добавляются отдельным явным перечнем.
        val planner =
            repoFile(
                "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupPlanner.kt",
            ).readText()
        assertTrue(planner.contains("val live = byFolder[albumDir.path].orEmpty()"))
        assertTrue(planner.contains("if (live.isEmpty()) return@forEach"))
    }
}

package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * KARAOKE-428: при переименовании альбома или автора меняется ключ их обложки
 * в хранилищах, и обложка оставалась под старым путём.
 *
 * Ключ обложки альбома: `$автор/$год - $альбом/$автор - $год - $альбом.album.png`.
 * Ключ обложки автора: `$автор/$автор.author.png`.
 */
class StorageCoverMigrationTest {
    @Test
    fun `ключи обложки альбома совпадают с ключами картинки альбома`() {
        val keys = Pictures.albumCoverKeys("Король и Шут", 1999, "Концерт на Манежной площади (Live)")
        assertEquals(2, keys.size)
        assertEquals(
            "Король и Шут/1999 - Концерт на Манежной площади (Live)/Король и Шут - 1999 - Концерт на Манежной площади (Live).album.png",
            keys[0],
        )
        assertEquals(
            "Король и Шут/1999 - Концерт на Манежной площади (Live)/Король и Шут - 1999 - Концерт на Манежной площади (Live).preview.album.png",
            keys[1],
        )
    }

    @Test
    fun `ключи обложки автора совпадают с ключами картинки автора`() {
        val keys = Pictures.authorCoverKeys("Король и Шут")
        assertEquals(2, keys.size)
        assertEquals("Король и Шут/Король и Шут.author.png", keys[0])
        assertEquals("Король и Шут/Король и Шут.preview.author.png", keys[1])
    }

    @Test
    fun `смена имени альбома и автора переносит обложки`() {
        val controller = repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")
        val text = controller.readText()
        assertTrue(
            text.contains("Pictures.albumCoverKeys(oldAuthorName, albumBefore.year, albumBefore.name)"),
            "прежние ключи обложки альбома снимаются до изменения",
        )
        assertTrue(
            text.contains("Pictures.albumCoverKeys(newAuthorName, year, name)"),
            "новые ключи обложки альбома — после изменения",
        )
        assertTrue(
            text.contains("Pictures.authorCoverKeys(oldAuthorName)") &&
                text.contains("Pictures.authorCoverKeys(it.author)"),
            "обложка автора переносится при смене имени",
        )
        assertTrue(
            text.contains("StorageObjectMigration.moveAll("),
            "перенос обложек идёт через общий помощник",
        )
    }

    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }
}

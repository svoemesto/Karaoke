package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Разовая уборка: устаревшие копии объектов живых песен (KARAOKE-427, FR-041).
 *
 * Переименование песни/альбома/автора не мигрирует ключи в MinIO (дефект
 * `renameAllArtifacts`, отдельная задача KARAOKE-428), поэтому в бакете остаются объекты
 * живой песни под прежним путём. Их и вычищаем — но **только если у живой песни на месте
 * актуальный объект**: иначе устаревшая копия единственная, и её удаление сломало бы песню.
 *
 * Тот же проход вычищает обложки альбомов, которых больше нет в каталоге (FR-041).
 */
class OneTimeCleanupStaleCopyTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private val planner =
        repoFile(
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupPlanner.kt",
        )

    @Test
    fun `хвост артефакта снимается с имени объекта`() {
        val stem = SongOneTimeCleanupPlanner.storageTailOf("2010 (01) [Король и Шут] - Лесник.vocals.mp3")
        assertEquals("2010 (01) [Король и Шут] - Лесник" to ".vocals.mp3", stem)

        val picture = SongOneTimeCleanupPlanner.storageTailOf("2010 (01) [Король и Шут] - Лесник [karaoke].png")
        assertEquals("2010 (01) [Король и Шут] - Лесник" to " [karaoke].png", picture)

        assertNull(
            SongOneTimeCleanupPlanner.storageTailOf("2010 - Концертные синглы.album.png"),
            "обложка альбома — не артефакт песни",
        )
    }

    @Test
    fun `устаревшая копия удаляется только при наличии актуального объекта`() {
        val text = planner.readText()
        assertTrue(
            text.contains("canonicalKeys.all { canonical -> objectExists(location, canonical) }"),
            "устаревшая копия — кандидат только если актуальный объект живой песни на месте",
        )
        assertTrue(
            text.contains("objectExists"),
            "нужна проверка существования объекта в хранилище",
        )
    }

    @Test
    fun `обложка альбома которого нет в каталоге становится кандидатом`() {
        val text = planner.readText()
        assertTrue(
            text.contains("обложка альбома, которого больше нет в каталоге"),
            "обложка осиротевшего альбома — кандидат разовой уборки",
        )
        assertTrue(
            text.contains("liveAlbumCovers"),
            "нужен список обложек альбомов каталога",
        )
    }

    @Test
    fun `общая обложка автора и альбома живого альбома не трогается`() {
        val text = planner.readText()
        assertTrue(
            text.contains("if (isSharedCoverKey(key)) return@forEach"),
            "общие обложки альбомов и авторов не трогаются (FR-014)",
        )
    }
}

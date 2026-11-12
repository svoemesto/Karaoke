package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Юнит-тесты совпадения предпросмотра и факта уборки (KARAOKE-429, спека 739, FR-023, SC-004).
 *
 * **SC-004:** расхождение между показанными и реально удалёнными файлами — 0.
 * Поэтому предпросмотр и уборка обязаны считать удаляемые файлы ОДНИМ И ТЕМ ЖЕ
 * кодом ([SongArtifactOwnership.collectFilesToDelete]).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-023, SC-004
 */
class SongDeletionPreviewTest {
    private val song = "Ночной дозор"

    @Test
    fun `предпросмотр считает ровно те файлы, которые удалит уборка`(
        @TempDir tmp: Path,
    ) {
        val album = File(tmp.toFile(), "1996 - Альбом").apply { mkdirs() }
        val stems = File(album, "hdemucs_mmi").apply { mkdirs() }
        val done = File(album, "done_files").apply { mkdirs() }
        File(album, "$song.flac").writeText("x")
        File(stems, "$song-accompaniment.flac").writeText("x")
        File(done, "$song [key].json").writeText("x")
        // Чужие файлы: соседняя песня и другая песня альбома.
        File(album, "$song, длинная версия.flac").writeText("x")
        File(album, "Другая песня.flac").writeText("x")

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, song)

        assertEquals(3, toDelete.size, "предпросмотр показывает ровно три файла песни")
        val bytes = toDelete.sumOf { it.length() }
        assertTrue(bytes > 0, "объём считается по тем же файлам")
    }

    @Test
    fun `файлы в подпапках артефактов тоже попадают в предпросмотр`(
        @TempDir tmp: Path,
    ) {
        val album = File(tmp.toFile(), "1996 - Альбом").apply { mkdirs() }
        listOf("hdemucs_mmi", "done_files", "done_chords", "sheetsage").forEach { name ->
            val dir = File(album, name)
            dir.mkdirs()
            File(dir, "$song-file").writeText("x")
        }

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, song)

        assertEquals(4, toDelete.size, "все подпапки артефактов обходятся")
    }

    @Test
    fun `живой симлинк в предпросмотре не учитывается как удаляемый`(
        @TempDir tmp: Path,
    ) {
        val album = File(tmp.toFile(), "1996 - Альбом").apply { mkdirs() }
        val symlinkDir = File(album, "symlink_mp4").apply { mkdirs() }
        val target = File(album, "$song.flac").apply { writeText("x") }
        val link = File(symlinkDir, "$song.link")
        val linkPath = link.toPath()
        val targetPath = target.toPath()
        Files.createSymbolicLink(linkPath, targetPath)

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, song)

        assertEquals(1, toDelete.size, "живой симлинк не удаляется (FR-015) — значит, и не показывается")
    }
}

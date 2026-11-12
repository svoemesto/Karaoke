package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Юнит-тесты защиты папки альбома (KARAOKE-429, спека 739, FR-020, FR-021, FR-022, SC-005).
 *
 * **SC-005:** удаление альбома происходит только тогда, когда в его папке нет файлов
 * живых песен; ни один файл живой песни не удалён при удалении альбома.
 *
 * Сценарий «альбом-близнец»: в папке альбома лежат файлы ДРУГОЙ (живой) песни —
 * папка остаётся, её содержимое цело.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-020, FR-021, FR-022, SC-005
 */
class AlbumFolderGuardTest {
    private val deletedSong = "Удаляемая песня"
    private val liveSong = "Живая песня"

    private fun album(tmp: Path): File = File(tmp.toFile(), "1996 - Альбом").apply { mkdirs() }

    private fun file(dir: File, name: String): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeText("x")
        }

    @Test
    fun `папка с файлами живой песни остаётся на месте`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        file(album, "$deletedSong.flac")
        file(album, "$liveSong.flac")

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, deletedSong)
        assertEquals(1, toDelete.size, "удаляется только файл удаляемой песни")

        val remaining = SongCleanupExecutor.remainingFilesCount(album.path, toDelete)
        assertEquals(1, remaining, "файл живой песни остаётся — папку удалять нельзя (FR-021)")
    }

    @Test
    fun `пустая после уборки папка альбома удаляется`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        file(album, "$deletedSong.flac")

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, deletedSong)
        val remaining = SongCleanupExecutor.remainingFilesCount(album.path, toDelete)
        assertEquals(0, remaining, "пустая папка альбома удаляется вместе с альбомом (FR-020)")
    }

    @Test
    fun `файлы в подпапках тоже считаются остатком`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        val stems = File(album, "hdemucs_mmi").apply { mkdirs() }
        file(album, "$deletedSong.flac")
        file(stems, "$liveSong-accompaniment.flac")

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, deletedSong)
        val remaining = SongCleanupExecutor.remainingFilesCount(album.path, toDelete)
        assertEquals(1, remaining, "файл живой песни в подпапке тоже защищает папку (FR-021)")
    }

    @Test
    fun `битый симлинк песни удаляется, живой симлинк остаётся`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        val symlinkDir = File(album, "symlink_mp4").apply { mkdirs() }
        val stems = File(album, "hdemucs_mmi").apply { mkdirs() }
        val broken = File(symlinkDir, "$deletedSong.link")
        Files.createSymbolicLink(broken.toPath(), Path.of("../hdemucs_mmi/$deletedSong.video"))
        val alive = File(symlinkDir, "$liveSong.link")
        Files.createSymbolicLink(alive.toPath(), Path.of("../hdemucs_mmi/$liveSong.video"))
        // Цель живого симлинка существует — ссылка рабочая.
        file(stems, "$liveSong.video")

        val toDelete = SongArtifactOwnership.collectFilesToDelete(album.path, deletedSong)
        assertEquals(1, toDelete.size, "удаляется битый симлинк песни (FR-015)")
        assertTrue(toDelete.first().absolutePath.contains(deletedSong))
        assertTrue(alive.exists(), "живой симлинк другой песни не трогаем")
    }
}

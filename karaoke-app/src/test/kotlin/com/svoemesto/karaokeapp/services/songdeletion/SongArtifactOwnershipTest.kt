package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Юнит-тесты строгого правила владения артефактом песни (KARAOKE-429, спека 739, FR-011).
 *
 * Проверяется [SongArtifactOwnership] — единственный модуль правил владения, общий
 * для удаления песни и разовой уборки.
 *
 * **Главный риск фичи — именно здесь.** Правило владения решает, чужое ли мы удаляем:
 * near-match по нормализации (регистр, `ё`/`е`, кавычки, `01`/`1`) НИКОГДА не
 * признаётся своим.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-011
 */
class SongArtifactOwnershipTest {
    private val song = "Песня"

    // ---- свои файлы ---------------------------------------------------

    @Test
    fun `исходник песни - свой файл`() {
        assertTrue(SongArtifactOwnership.isOwnArtifact(song, "Песня.mp3"))
    }

    @Test
    fun `стем - свой файл`() {
        assertTrue(SongArtifactOwnership.isOwnArtifact(song, "Песня.accompaniment.flac"))
    }

    @Test
    fun `публикационная картинка - свой файл`() {
        assertTrue(SongArtifactOwnership.isOwnArtifact(song, "Песня [karaoke].png"))
    }

    @Test
    fun `имя файла песни без продолжения - свой файл`() {
        assertTrue(SongArtifactOwnership.isOwnArtifact(song, "Песня"))
    }

    // ---- чужие файлы ---------------------------------------------------

    @Test
    fun `соседняя песня с запятой - чужой файл`() {
        assertFalse(SongArtifactOwnership.isOwnArtifact(song, "Песня, длинная версия.mp3"))
    }

    @Test
    fun `другой регистр - чужой файл`() {
        assertFalse(SongArtifactOwnership.isOwnArtifact(song, "песня.mp3"))
    }

    @Test
    fun `ё вместо е - чужой файл`() {
        assertFalse(SongArtifactOwnership.isOwnArtifact(song, "Пёсня.mp3"))
        assertFalse(SongArtifactOwnership.isOwnArtifact("Пёсня", "Песня.mp3"))
    }

    @Test
    fun `ведущий ноль в номере - чужой файл`() {
        assertFalse(SongArtifactOwnership.isOwnArtifact(song, "Песня 01.mp3"))
        assertFalse(SongArtifactOwnership.isOwnArtifact("Песня 1", "Песня 01.mp3"))
    }

    @Test
    fun `кавычки в имени - чужой файл`() {
        assertFalse(SongArtifactOwnership.isOwnArtifact(song, "«Песня».mp3"))
        assertFalse(SongArtifactOwnership.isOwnArtifact(song, "\"Песня\".mp3"))
    }

    // ---- служебные случаи ----------------------------------------------

    @Test
    fun `пустое имя песни не владеет ничем`() {
        assertFalse(SongArtifactOwnership.isOwnArtifact("", "Песня.mp3"))
        assertFalse(SongArtifactOwnership.isOwnArtifact("   ", "Песня.mp3"))
    }

    @Test
    fun `обход папки альбома возвращает только файлы песни`(
        @TempDir tmp: Path,
    ) {
        val album = File(tmp.toFile(), "2020 - Альбом").apply { mkdirs() }
        val stems = File(album, "hdemucs_mmi").apply { mkdirs() }
        val done = File(album, "done_files").apply { mkdirs() }
        val symlinkDir = File(album, "symlink_mp4").apply { mkdirs() }
        listOf(
            File(album, "Песня.flac"),
            File(stems, "Песня-accompaniment.flac"),
            File(done, "Песня [key].json"),
            File(symlinkDir, "Песня.link"),
        ).forEach { it.writeText("x") }
        // Чужие файлы: соседняя песня с префиксом и другая песня альбома.
        File(album, "Песня, длинная версия.flac").writeText("x")
        File(album, "Другая песня.flac").writeText("x")

        val own = SongArtifactOwnership.collectOwnArtifacts(album.path, song)

        assertEquals(
            4,
            own.size,
            "в обход должны попасть только файлы песни: исходник, стем, key.json, симлинк",
        )
        assertTrue(own.all { it.name.startsWith("Песня") })
        assertFalse(own.any { it.name.contains("длинная версия") })
        assertFalse(own.any { it.name.startsWith("Другая") })
    }

    @Test
    fun `обход несуществующей папки возвращает пустой список`() {
        assertTrue(SongArtifactOwnership.collectOwnArtifacts("/no/such/folder", song).isEmpty())
    }
}

package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты правила владения объектами хранилища (KARAOKE-429, спека 739, FR-013, FR-014).
 *
 * **FR-014:** обложка альбома и картинка автора — общие для всех песен альбома/автора;
 * при удалении песни они НЕ удаляются.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-013, FR-014
 */
class StorageKeyOwnershipTest {
    private val author = "Автор"
    private val year = "1996"
    private val album = "Альбом"
    private val song = "Ночной дозор"

    private fun own(key: String): Boolean =
        SongArtifactOwnership.isOwnStorageKey(author, year, album, song, key)

    @Test
    fun `свои ключи песни распознаются`() {
        assertTrue(own("$author/$year - $album/$song.vocals.mp3"))
        assertTrue(own("$author/$year - $album/$song.accompaniment.mp3"))
        assertTrue(own("$author/$year - $album/$song [karaoke].png"))
        assertTrue(own("$author/$year - $album/$song"))
    }

    @Test
    fun `обложка альбома не является собственным объектом песни`() {
        val albumCover = "$author/$year - $album/$author - $year - $album.album.png"
        val albumPreview = "$author/$year - $album/$author - $year - $album.preview.album.png"
        assertFalse(own(albumCover), "обложка альбома удаляется только вместе с альбомом (FR-014)")
        assertFalse(own(albumPreview), "превью обложки альбома — тоже (FR-014)")
    }

    @Test
    fun `картинка автора не является собственным объектом песни`() {
        assertFalse(own("$author/$author.author.png"), "картинка автора общая (FR-014)")
        assertFalse(own("$author/$author.preview.author.png"), "превью картинки автора общая (FR-014)")
    }

    @Test
    fun `объекты соседней песни не попадают в выборку`() {
        assertFalse(own("$author/$year - $album/Другая песня.vocals.mp3"))
        assertFalse(own("$author/$year - $album/$song, длинная версия.vocals.mp3"))
        assertFalse(own("$author/$year - $album/песня.vocals.mp3"), "регистр значим")
    }

    @Test
    fun `объекты чужого альбома не попадают в выборку`() {
        assertFalse(own("$author/1997 - Другой альбом/$song.vocals.mp3"))
    }

    @Test
    fun `выборка по нескольким именам песни учитывает прежнее имя`() {
        val keys =
            SongArtifactOwnership.ownStorageKeys(
                author = author,
                year = year,
                album = album,
                songFileNames = listOf("Прежнее имя", song),
                keys = listOf("$author/$year - $album/Прежнее имя.vocals.mp3", "$author/$year - $album/$song.vocals.mp3"),
            )
        assertTrue(keys.contains("$author/$year - $album/Прежнее имя.vocals.mp3"), "прежнее имя тоже своё (FR-013)")
        assertTrue(keys.contains("$author/$year - $album/$song.vocals.mp3"))
    }
}

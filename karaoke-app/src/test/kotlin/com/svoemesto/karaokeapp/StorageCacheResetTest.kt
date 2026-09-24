package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Спека #446 (FR-002): формулы имён файлов песни в хранилище — единый источник,
 * переиспользуемый сбросом кеша. Тесты фиксируют точные формулы (должны совпадать
 * с теми, что строит `HealthReport`) и состав списка (только SONG-типы с storage).
 *
 * Используют примитивы (`author/year/album/fileName`), т.к. конструктор `Song`
 * требует статической инициализации окружения (JDBC/Spring) и в unit-тесте падает.
 */
class StorageCacheResetTest {
    private val author = "Бахыт Компот"
    private val album = "Бог кастрирует слона"
    private val year = "2026"
    private val songFileName = "2026 (01) [Бахыт Компот] - Бог кастрирует слона"

    private fun name(type: KaraokeFileType) =
        StorageCacheReset.storageFileNameFor(author, year, album, songFileName, type)

    @Test
    fun `audio file name uses song storageFileName plus suffix`() {
        assertEquals(
            "Бахыт Компот/2026 - Бог кастрирует слона/2026 (01) [Бахыт Компот] - Бог кастрирует слона.accompaniment.mp3",
            name(KaraokeFileType.MP3_ACCOMPANIMENT),
        )
        assertEquals(
            "Бахыт Компот/2026 - Бог кастрирует слона/2026 (01) [Бахыт Компот] - Бог кастрирует слона.vocals.mp3",
            name(KaraokeFileType.MP3_VOCAL),
        )
    }

    @Test
    fun `album picture name uses author year album form`() {
        assertEquals(
            "Бахыт Компот/2026 - Бог кастрирует слона/Бахыт Компот - 2026 - Бог кастрирует слона.album.png",
            name(KaraokeFileType.PICTURE_ALBUM),
        )
        assertEquals(
            "Бахыт Компот/2026 - Бог кастрирует слона/Бахыт Компот - 2026 - Бог кастрирует слона.preview.album.png",
            name(KaraokeFileType.PICTURE_ALBUM_PREVIEW),
        )
    }

    @Test
    fun `author picture name is author slash author`() {
        assertEquals("Бахыт Компот/Бахыт Компот.author.png", name(KaraokeFileType.PICTURE_AUTHOR))
        assertEquals("Бахыт Компот/Бахыт Компот.preview.author.png", name(KaraokeFileType.PICTURE_AUTHOR_PREVIEW))
    }

    @Test
    fun `song file names contain all storage types and no duplicates`() {
        val names = StorageCacheReset.storageFileNamesForSong(author, year, album, songFileName)
        // 5 MP3-стемов + 2 картинки альбома + 2 картинки автора = 9.
        assertEquals(9, names.size, "expected 5 mp3 + 4 pictures, got: $names")
        assertEquals(names.size, names.toSet().size, "names must be unique")
        assertTrue(names.any { it.endsWith(".accompaniment.mp3") })
        assertTrue(names.any { it.endsWith(".album.png") })
        assertTrue(names.any { it.endsWith(".author.png") })
        assertTrue(names.none { it.endsWith(".flac") }, "flac lives on local filesystem only")
        assertTrue(names.none { it.endsWith(".mp4") }, "video lives on local filesystem only")
    }
}

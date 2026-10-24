package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.model.StemJobMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессионный тест «до/после» для BR-258 (спека 561, OpenProject #361): приватные объекты
 * премиум-задания лежат в ОТДЕЛЬНОМ бакете `stemjobs`, а не в публичном `karaoke`.
 *
 * **До правки** тест падал: `StemJobProcessing.executeFinalizeStemJob` грузил стемы и исходник
 * в бакет `karaoke` по ключу `stemjobs/<id>/<stem>.mp3`, а этот бакет имеет публичную
 * bucket policy (`s3:GetObject` на весь префикс) и раздаётся анонимно через `/minio/`.
 *
 * @see specs/561-impl-stem-bucket/data-model.md
 */
class StemJobStorageLayoutTest {
    @Test
    fun `приватные объекты задания кладутся в отдельный бакет stemjobs`() {
        assertEquals("stemjobs", StemJob.STORAGE_BUCKET)
        assertNotEquals(
            StemJob.PUBLIC_STORAGE_BUCKET,
            StemJob.STORAGE_BUCKET,
            "приватные объекты не должны лежать в публичном бакете — иначе они анонимно читаемы",
        )
    }

    @Test
    fun `публичный бакет karaoke остаётся публичным`() {
        assertEquals("karaoke", StemJob.PUBLIC_STORAGE_BUCKET)
    }

    @Test
    fun `ключ объекта в приватном бакете - id задания и имя файла`() {
        assertEquals("42/vocals.mp3", StemJob.objectKey(42, "vocals", "mp3"))
        assertEquals("42/accompaniment.mp3", StemJob.objectKey(42, "accompaniment", "mp3"))
        assertEquals("42/original.mp3", StemJob.objectKey(42, StemJob.ORIGINAL_STEM, "mp3"))
        assertEquals("42/original.wav", StemJob.objectKey(42, StemJob.ORIGINAL_STEM, "wav"))
    }

    @Test
    fun `legacy-ключ в публичном бакете известен уборке`() {
        assertEquals("stemjobs/42/vocals.mp3", StemJob.legacyObjectKey(42, "vocals", "mp3"))
        assertEquals("stemjobs/42/original.mp3", StemJob.legacyObjectKey(42, StemJob.ORIGINAL_STEM, "mp3"))
    }

    @Test
    fun `имя файла результата учитывает исходное расширение только для original`() {
        assertEquals("vocals.mp3", StemJob.resultFileName("vocals", "wav"))
        assertEquals("original.wav", StemJob.resultFileName(StemJob.ORIGINAL_STEM, "wav"))
        assertEquals("original.mp3", StemJob.resultFileName(StemJob.ORIGINAL_STEM, "MP3"))
    }

    @Test
    fun `allow-list имён файлов принимает стемы и исходник`() {
        assertTrue(StemJob.isValidResultFileName("vocals.mp3"))
        assertTrue(StemJob.isValidResultFileName("accompaniment.mp3"))
        assertTrue(StemJob.isValidResultFileName("original.mp3"))
        assertTrue(StemJob.isValidResultFileName("original.wav"))
    }

    @Test
    fun `allow-list имён файлов отклоняет выход за пределы каталога и чужие расширения`() {
        assertFalse(StemJob.isValidResultFileName("../secret.mp3"))
        assertFalse(StemJob.isValidResultFileName("sub/dir.mp3"))
        assertFalse(StemJob.isValidResultFileName("/etc/passwd"))
        assertFalse(StemJob.isValidResultFileName("vocals.exe"))
        assertFalse(StemJob.isValidResultFileName("unknown.mp3"))
        assertFalse(StemJob.isValidResultFileName("vocals"))
        assertFalse(StemJob.isValidResultFileName(""))
    }

    @Test
    fun `список доступных стемов задания включает original`() {
        assertEquals(listOf("accompaniment", "vocals", "original"), StemJob.validStemNames(StemJobMode.DEMUCS2))
        assertEquals(
            listOf("accompaniment", "vocals", "drums", "bass", "other", "original"),
            StemJob.validStemNames(StemJobMode.DEMUCS5),
        )
    }
}

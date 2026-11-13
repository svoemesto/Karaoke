package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * KARAOKE-428: при смене альбома, автора или года меняется префикс ключа объектов
 * песни в хранилищах (`$автор/$год - $альбом/`), и объекты оставались под старым
 * путём — накапливались устаревшие копии (745 объектов, 4,38 ГБ, замер 2026-11-12).
 *
 * Проверяется чистая часть правила — множество суффиксов и построение ключей.
 * Экземпляр `Song` в юнит-тесте не создать (конструктор требует БД и хранилища),
 * поэтому перенос проверяется по ключам; сам перенос — на живом хранилище.
 */
class StoragePrefixMigrationTest {
    @Test
    fun `в перечень входят mp3-стемы и публикационные картинки`() {
        val suffixes = Song.storageObjectSuffixes()
        assertEquals(14, suffixes.size, "5 mp3-темов + 9 публикационных картинок: $suffixes")
        assertTrue(suffixes.contains(".vocals.mp3"))
        assertTrue(suffixes.contains(".accompaniment.mp3"))
        assertTrue(suffixes.contains(".bass.mp3"))
        assertTrue(suffixes.contains(".drums.mp3"))
        assertTrue(suffixes.contains(".other.mp3"))
        assertTrue(suffixes.contains(" [karaoke].png"))
        assertTrue(suffixes.contains(" [lyrics].png"))
        assertTrue(suffixes.contains(" [VKlink].png"))
    }

    @Test
    fun `ключ объекта строится от префикса и имени песни`() {
        val prefix = "Король и Шут/1999 - Концерт на Манежной площади (Live)/"
        val fileName = "1999 (02) [Король и Шут] - Внезапная Голова"
        val keys = Song.storageObjectKeys(prefix, fileName)
        assertEquals(14, keys.size)
        assertTrue(keys.contains("$prefix$fileName.vocals.mp3"))
        assertTrue(keys.contains("$prefix$fileName [karaoke].png"))
    }

    @Test
    fun `смена префикса переносит объекты песни`() {
        val mapper =
            File(System.getProperty("user.dir")).absoluteFile.let { dir ->
                var root = dir
                while (!File(root, "settings.gradle.kts").isFile) root = root.parentFile
                File(root, "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt")
            }
        assertTrue(mapper.isFile, "файл SongUpdateMapper.kt не найден")
        val text = mapper.readText()
        assertTrue(
            text.contains("val oldStoragePrefix = song.storagePrefix()"),
            "прежний префикс запоминается до правок",
        )
        assertTrue(
            text.contains("song.migrateStorageObjectsToNewPrefix(oldStoragePrefix)"),
            "при смене префикса объекты хранилищ переносятся",
        )
    }
}

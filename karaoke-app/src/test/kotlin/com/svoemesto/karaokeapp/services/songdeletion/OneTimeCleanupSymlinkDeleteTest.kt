package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * ИНЦИДЕНТ 2026-11-13 (прогон разовой уборки №210): 60 битых симлинков в `symlink_sponsr`
 * были помечены как удалённые, но остались на диске.
 *
 * Причина: удаление шло через `!file.exists() || file.delete()`, а `File.exists()` идёт
 * ПО ССЫЛКЕ — у битого симлинка (цель уже удалена) она возвращает `false`, и проверка
 * короткозамыкала: файл не удалялся, но считался удалённым.
 *
 * Контракт: удаление работает и с обычным файлом, и с симлинком (удаляется сам симлинк,
 * а не цель), и с уже отсутствующим путём.
 */
class OneTimeCleanupSymlinkDeleteTest {
    @Test
    fun `битый симлинк удаляется, а не считается удалённым`() {
        val dir = Files.createTempDirectory("karaoke-cleanup-symlink")
        try {
            val target = dir.resolve("target.flac")
            Files.writeString(target, "x")
            val link = dir.resolve("song-accompaniment.flac")
            Files.createSymbolicLink(link, target)
            // цель исчезла — симлинк стал битым
            Files.delete(target)

            assertFalse(Files.exists(link), "битый симлинк: exists() по ссылке = false")
            assertTrue(SongOneTimeCleanupService.deleteLocalFile(link.toString()), "удаление должно быть успешным")
            assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS), "симлинк должен быть удалён")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `обычный файл удаляется`() {
        val dir = Files.createTempDirectory("karaoke-cleanup-file")
        try {
            val file = dir.resolve("song.flac")
            Files.writeString(file, "x")
            assertTrue(SongOneTimeCleanupService.deleteLocalFile(file.toString()))
            assertFalse(Files.exists(file))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `уже отсутствующий файл считается удалённым`() {
        assertTrue(
            SongOneTimeCleanupService.deleteLocalFile("/tmp/karaoke-cleanup-does-not-exist-42"),
            "нечего удалять — это не отказ",
        )
    }
}

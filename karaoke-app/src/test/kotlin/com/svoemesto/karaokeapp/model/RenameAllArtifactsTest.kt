package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Юнит-тесты полного переименования артефактов песни (issue #323).
 *
 * Проверяется [SongArtifactRenamer] — чистая файловая логика, вынесенная из [Song] именно
 * ради тестируемости: экземпляр `Song` в юнит-тесте не создать (конструктор требует БД и
 * хранилища), а правило переименования обязано быть проверяемым.
 *
 * Покрывает: исходник, `[key].json`, стемы в `hdemucs_mmi`, вложения `done_files`, симлинки,
 * защиту соседней песни с именем-префиксом и идемпотентность.
 *
 * @see specs/323-complete-song-rename
 */
class RenameAllArtifactsTest {
    private val oldName = "2026 (01) [Чёрный обелиск] - Пой, фестиваль"
    private val newName = "2026 (01) [Чёрный Обелиск] - Пой, фестиваль"

    private fun album(root: Path): File =
        File(root.toFile(), "Чёрный Обелиск/2026 - Пой, фестиваль (сингл)").apply { mkdirs() }

    private fun file(dir: File, name: String): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeText("x")
        }

    @Test
    fun `переименовывает исходник, key-json и стемы`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        val stems = File(album, "hdemucs_mmi").apply { mkdirs() }
        file(album, "$oldName.flac")
        file(album, "$oldName [key].json")
        file(stems, "$oldName-accompaniment.flac")
        file(stems, "$oldName-vocals.flac")
        file(stems, "$oldName-accompaniment.mp3")

        val renamed = SongArtifactRenamer.renameLocal(album.path, oldName, newName)

        assertEquals(5, renamed.size, "должны переименоваться исходник, key.json и три стема")
        assertTrue(File(album, "$newName.flac").exists())
        assertTrue(File(album, "$newName [key].json").exists())
        assertTrue(File(stems, "$newName-accompaniment.flac").exists())
        assertTrue(File(stems, "$newName-vocals.flac").exists())
        assertTrue(File(stems, "$newName-accompaniment.mp3").exists())
        assertFalse(File(album, "$oldName.flac").exists())
    }

    @Test
    fun `переименовывает вложения done_files`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        val done = File(album, "done_files").apply { mkdirs() }
        file(done, "$oldName [karaoke].png")
        file(done, "$oldName [lyrics].txt")
        file(done, "$oldName [chords].txt")

        SongArtifactRenamer.renameLocal(album.path, oldName, newName)

        assertTrue(File(done, "$newName [karaoke].png").exists())
        assertTrue(File(done, "$newName [lyrics].txt").exists())
        assertTrue(File(done, "$newName [chords].txt").exists())
    }

    @Test
    fun `пересоздаёт симлинк с новым именем и целью`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        val stems = File(album, "hdemucs_mmi").apply { mkdirs() }
        file(stems, "$oldName-accompaniment.flac")
        val symlinkDir = File(album, "symlink_sponsr").apply { mkdirs() }
        val link = File(symlinkDir, "$oldName-accompaniment.flac").toPath()
        Files.createSymbolicLink(link, Path.of("../hdemucs_mmi/$oldName-accompaniment.flac"))

        SongArtifactRenamer.renameLocal(album.path, oldName, newName)

        val newLink = File(symlinkDir, "$newName-accompaniment.flac")
        assertTrue(newLink.exists(), "ссылка должна быть пересоздана под новым именем")
        assertEquals(
            "../hdemucs_mmi/$newName-accompaniment.flac",
            Files.readSymbolicLink(newLink.toPath()).toString(),
            "цель ссылки должна указывать на новое имя файла",
        )
        assertFalse(
            Files.exists(
                File(symlinkDir, "$oldName-accompaniment.flac").toPath(),
                LinkOption.NOFOLLOW_LINKS,
            ),
            "старая ссылка должна быть удалена",
        )
        assertTrue(newLink.exists(), "ссылка должна быть рабочей (цель существует)")
    }

    @Test
    fun `не трогает соседнюю песню с именем-префиксом`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        val sibling = "2026 (02) [Чёрный Обелиск] - Пой, фестиваль длинная версия"
        val prefixSibling = "$oldName, фестиваль"
        file(album, "$prefixSibling.flac")
        file(album, "$sibling.flac")
        file(album, "$oldName.flac")

        SongArtifactRenamer.renameLocal(album.path, oldName, newName)

        assertTrue(File(album, "$prefixSibling.flac").exists(), "файл соседней песни с именем-префиксом не должен переименовываться")
        assertTrue(File(album, "$sibling.flac").exists())
        assertTrue(File(album, "$newName.flac").exists())
    }

    @Test
    fun `не трогает посторонние файлы и каталоги`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        file(album, "LogoAlbum.png")
        file(album, "key_bpm.csv")
        File(album, "$oldName [karaoke].png").mkdirs() // каталог с «нашим» именем — не файл

        val renamed = SongArtifactRenamer.renameLocal(album.path, oldName, newName)

        assertEquals(0, renamed.size)
        assertTrue(File(album, "LogoAlbum.png").exists())
        assertTrue(File(album, "key_bpm.csv").exists())
        assertTrue(File(album, "$oldName [karaoke].png").isDirectory)
    }

    @Test
    fun `повторный вызов идемпотентен`(
        @TempDir tmp: Path,
    ) {
        val album = album(tmp)
        file(album, "$oldName.flac")

        val first = SongArtifactRenamer.renameLocal(album.path, oldName, newName)
        val second = SongArtifactRenamer.renameLocal(album.path, oldName, newName)

        assertEquals(1, first.size)
        assertEquals(0, second.size, "повторный вызов не должен ничего находить")
        assertTrue(File(album, "$newName.flac").exists())
    }

    @Test
    fun `остаток имени признаётся продолжением артефакта только для своих шаблонов`() {
        assertTrue(SongArtifactRenamer.isArtifactContinuation(""))
        assertTrue(SongArtifactRenamer.isArtifactContinuation(".flac"))
        assertTrue(SongArtifactRenamer.isArtifactContinuation("-accompaniment.mp3"))
        assertTrue(SongArtifactRenamer.isArtifactContinuation(" [karaoke].png"))
        assertFalse(SongArtifactRenamer.isArtifactContinuation(", фестиваль.flac"))
        assertFalse(SongArtifactRenamer.isArtifactContinuation(" длинная версия.flac"))
    }
}

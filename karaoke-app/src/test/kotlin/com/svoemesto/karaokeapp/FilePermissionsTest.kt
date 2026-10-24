package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/**
 * BR-115 / BR-247 (аудит #336, OpenProject #407): права на файлы, которые
 * создаёт приложение, не должны быть world-writable.
 *
 * Проверяется фактический режим на диске, а не вызов `chmod`: до правки
 * файлы получали `666`, каталоги `777`, а файл настроек — `666` (в нём лежат
 * токены и пароли). Ожидание: `644` для опубликованных файлов, `755` для
 * каталогов и `600` для файла с секретами.
 */
class FilePermissionsTest {
    private val publishedFile =
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ,
        )

    private val publishedDirectory =
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE,
        )

    private val secretFile =
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )

    @Test
    fun `опубликованный файл — 644, без права записи для группы и остальных`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("song.png")
        Files.write(file, byteArrayOf(1, 2, 3))

        setPublishedFilePermissions(file.toString())

        assertEquals(publishedFile, Files.getPosixFilePermissions(file))
        assertTrue(
            !Files.getPosixFilePermissions(file).contains(PosixFilePermission.OTHERS_WRITE),
            "world-writable файл позволял подменить опубликованную картинку",
        )
    }

    @Test
    fun `каталог публикации — 755, без права записи для группы и остальных`(
        @TempDir dir: Path,
    ) {
        val folder = Files.createDirectory(dir.resolve("pictures"))

        setPublishedDirectoryPermissions(folder.toString())

        assertEquals(publishedDirectory, Files.getPosixFilePermissions(folder))
        assertTrue(
            !Files.getPosixFilePermissions(folder).contains(PosixFilePermission.OTHERS_WRITE),
            "world-writable каталог позволял добавить/подменить файл в нём",
        )
    }

    @Test
    fun `файл с секретами — 600, читает и пишет только владелец`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("Karaoke.properties")
        Files.writeString(file, "secret")

        setSecretFilePermissions(file.toString())

        assertEquals(secretFile, Files.getPosixFilePermissions(file))
    }

    @Test
    fun `запись Karaoke properties оставляет 600 и на файле, и на резервной копии`(
        @TempDir dir: Path,
    ) {
        val target = dir.resolve("Karaoke.properties").toFile()

        writePropertiesFileAtomic(target, "first")
        // Первая запись создаёт файл, вторая — ещё и .bak с предыдущей версией.
        writePropertiesFileAtomic(target, "second")

        assertEquals(secretFile, Files.getPosixFilePermissions(target.toPath()))
        val backup = dir.resolve("Karaoke.properties.bak")
        assertTrue(Files.exists(backup), "резервная копия должна создаваться при перезаписи")
        assertEquals(
            secretFile,
            Files.getPosixFilePermissions(backup),
            ".bak содержит те же секреты, что и основной файл, и не должен быть читаем всем",
        )
    }
}

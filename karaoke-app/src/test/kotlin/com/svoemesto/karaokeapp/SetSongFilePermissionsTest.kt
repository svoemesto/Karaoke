package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/**
 * Тикет #213: файл субтитров создавался `chmod 666` — доступен на запись любому
 * пользователю системы, плюс внешний процесс ради одной операции.
 */
class SetSongFilePermissionsTest {
    @Test
    fun `файл доступен всем на чтение, но не на запись`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("song.voice1.srt")
        Files.writeString(file, "1\n00:00:00,000 --> 00:00:01,000\nтест\n")

        setSongFilePermissions(file.toString())

        val perms = Files.getPosixFilePermissions(file)
        assertTrue(
            perms.containsAll(
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ,
                ),
            ),
            "ожидались права чтения всем и записи владельцу, получено $perms",
        )
        // Главное: мирового права ЗАПИСИ быть не должно.
        assertTrue(
            !perms.contains(PosixFilePermission.OTHERS_WRITE) &&
                !perms.contains(PosixFilePermission.GROUP_WRITE),
            "файл не должен быть доступен на запись другим, получено $perms",
        )
    }

    @Test
    fun `успех chmod равен 664 минус групповая запись`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("check.srt")
        Files.writeString(file, "x")
        setSongFilePermissions(file.toString())
        val perms = Files.getPosixFilePermissions(file)
        assertEquals(4, perms.size, "ровно четыре права, получено $perms")
    }

    @Test
    fun `ошибка не пробрасывается вызывающему коду`(
        @TempDir dir: Path,
    ) {
        // Файла нет — Files.setPosixFilePermissions бросит NoSuchFileException.
        // Сохранение не должно из-за этого падать: файл мог быть уже удалён,
        // а вызывающий код только что успешно записать данные.
        setSongFilePermissions(dir.resolve("нет-такого.srt").toString())
    }
}

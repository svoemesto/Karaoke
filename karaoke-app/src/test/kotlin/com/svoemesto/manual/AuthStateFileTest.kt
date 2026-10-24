package com.svoemesto.manual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration

/**
 * Проверка обвязки ручных сценариев авторизации (BR-532).
 *
 * Файл состояния авторизации содержит cookie Яндекс.Музыки, то есть секрет.
 * Права на него задаёт код, а не umask машины: без явных `600` файл читался бы
 * всеми, кто имеет доступ к ФС. Раньше права не задавались вовсе.
 */
class AuthStateFileTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `файл состояния авторизации создаётся с правами только владельца`() {
        val file = tempDir.resolve("yandex_auth_state.json")

        AuthStateFile.write(file, """{"cookies":[]}""".toByteArray())

        assertTrue(Files.exists(file), "файл состояния должен быть создан")
        val permissions = Files.getPosixFilePermissions(file)
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            permissions,
            "файл состояния авторизации доступен только владельцу",
        )
        assertFalse(
            permissions.any { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") },
            "файл состояния авторизации не должен быть доступен группе или остальным",
        )
    }

    @Test
    fun `содержимое файла состояния сохраняется без изменений`() {
        val file = tempDir.resolve("state.json")
        val content = """{"cookies":[{"name":"Session_id","value":"secret"}]}"""

        AuthStateFile.write(file, content.toByteArray())

        assertEquals(content, Files.readString(file), "содержимое состояния должно сохраниться как есть")
    }

    @Test
    fun `awaitExists возвращает true когда файл уже есть`() {
        val file = tempDir.resolve("state.json")
        Files.writeString(file, "{}")

        assertTrue(
            AuthStateFile.awaitExists(file, Duration.ofSeconds(2)),
            "существующий файл должен быть найден сразу",
        )
    }

    @Test
    fun `awaitExists возвращает false по истечении таймаута`() {
        val file = tempDir.resolve("нет-такого.json")

        assertFalse(
            AuthStateFile.awaitExists(file, Duration.ofMillis(500)),
            "отсутствующий файл не должен «находиться» — сценарий обязан честно упасть по таймауту",
        )
    }

    @Test
    fun `awaitExists дожидается появления файла`() {
        val file = tempDir.resolve("позже.json")
        val writer =
            Thread {
                Thread.sleep(300)
                Files.writeString(file, "{}")
            }
        writer.start()

        assertTrue(
            AuthStateFile.awaitExists(file, Duration.ofSeconds(10)),
            "появление файла должно обнаруживаться",
        )
        writer.join()
    }
}

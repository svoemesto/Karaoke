package com.svoemesto.manual

import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration

/**
 * Файл состояния авторизации браузера (cookies/localStorage Яндекс.Музыки).
 *
 * Файл содержит авторизационные cookie — то есть секрет. Права по умолчанию
 * (umask хоста) для такого файла не подходят: читаемость должна задаваться
 * кодом, а не зависеть от машины, на которой файл создан. Поэтому файл
 * создаётся с правами `600` (только владелец) явно.
 *
 * Обвязка ручных сценариев лежит в `src/test`, чтобы её можно было проверить
 * обычным тестом (`AuthStateFileTest`), а ручные сценарии (source set
 * `manualTest`) — переиспользовали её.
 */
object AuthStateFile {
    /** Права файла состояния: `rw` только для владельца (600). */
    val OWNER_ONLY: Set<PosixFilePermission> =
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )

    /**
     * Записать состояние авторизации в [path] и выставить права «только владелец».
     *
     * @return путь к записанному файлу.
     */
    fun write(
        path: Path,
        content: ByteArray,
    ): Path {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, content)
        setOwnerOnlyPermissions(path)
        return path
    }

    /**
     * Выставить на файл права «только владелец» (600).
     *
     * На ФС без POSIX-прав (Windows) операция не поддерживается — тогда
     * пропускаем её, а не роняем сценарий.
     */
    fun setOwnerOnlyPermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY)
        } catch (_: UnsupportedOperationException) {
            // ФС без POSIX-прав — пропускаем.
        }
    }

    /**
     * Дождаться появления файла состояния [path] (ожидание условия, без сна).
     *
     * @param timeout сколько ждать.
     * @return `true`, если файл появился; `false` — по истечении таймаута.
     */
    fun awaitExists(
        path: Path,
        timeout: Duration,
        pollInterval: Duration = Duration.ofMillis(200),
    ): Boolean =
        try {
            Awaitility
                .await()
                .atMost(timeout)
                .pollInterval(pollInterval)
                .until { Files.isRegularFile(path) }
            true
        } catch (_: ConditionTimeoutException) {
            false
        }
}

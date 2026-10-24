package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.EnumSet

/**
 * Юнит-тесты для [KaraokeProcessWorker.cleanupOldLogsIn].
 * Спека 310 (FR-6). Покрывает SC-2, FR-3, FR-4.
 *
 * @see specs/310-ochistka-papki-logov/spec.md
 */
class KaraokeProcessCleanupTest {
    @TempDir
    lateinit var tempDir: Path

    /** Создаёт файл [p] и ставит ему mtime = now - ageDays дней. */
    private fun touch(p: Path, ageDays: Long) {
        Files.writeString(p, "x")
        val mtime = Instant.now().minus(ageDays * 24L * 60L * 60L * 1000L, ChronoUnit.MILLIS)
        Files.setLastModifiedTime(p, FileTime.from(mtime))
    }

    /** POSIX-набор прав «r-x для всех» (read+execute, без write). */
    private fun readonlyDirPerms(): Set<PosixFilePermission> =
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE,
        )

    /** POSIX-набор прав «rwx для owner, r-x для group/others». */
    private fun writableDirPerms(): Set<PosixFilePermission> =
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE,
        )

    @Test
    fun cleanupRemovesOldFiles() {
        // SC-2: 3 файла с mtime 5, 20, 60 дней назад при retention=30 → удаляется только 60d
        touch(tempDir.resolve("5d.log"), 5)
        touch(tempDir.resolve("20d.log"), 20)
        touch(tempDir.resolve("60d.log"), 60)

        KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30)

        assertTrue(Files.exists(tempDir.resolve("5d.log")), "5-day file should be kept")
        assertTrue(Files.exists(tempDir.resolve("20d.log")), "20-day file should be kept")
        assertFalse(Files.exists(tempDir.resolve("60d.log")), "60-day file should be deleted")
    }

    @Test
    fun cleanupIgnoresFutureFiles() {
        // FR-3: файл с mtime = now + 1h не удаляется (защита от clock skew)
        val p = tempDir.resolve("future.log")
        Files.writeString(p, "x")
        Files.setLastModifiedTime(p, FileTime.from(Instant.now().plus(1L, ChronoUnit.HOURS)))

        KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30)

        assertTrue(Files.exists(p), "future-dated file must not be deleted")
    }

    @Test
    fun cleanupFailsOpenOnLockedFile() {
        // FR-4 (fail-open): когда parent dir read-only (chmod 555),
        // Files.delete бросает AccessDeniedException на каждом файле.
        // cleanupOldLogsIn должен НЕ пробросить исключение наружу (FR-4).
        //
        // Проверка «файлы остались» вынесена в отдельный тест
        // (`cleanupKeepsFilesInReadOnlyDirectory`): она зависит от того,
        // соблюдает ли ядро POSIX-права (под root — нет, CAP_DAC_OVERRIDE),
        // и потому не может быть частью fail-open-проверки.
        val f1 = tempDir.resolve("file1.log")
        val f2 = tempDir.resolve("file2.log")
        touch(f1, 60)
        touch(f2, 60)

        try {
            Files.setPosixFilePermissions(tempDir, readonlyDirPerms())

            // cleanup не должен бросить наружу
            assertDoesNotThrow(
                { KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30) },
                "cleanupOldLogsIn обязан работать fail-open: ошибка удаления файла не должна выходить наружу",
            )
        } finally {
            // Восстанавливаем права для @TempDir cleanup
            Files.setPosixFilePermissions(tempDir, writableDirPerms())
        }
    }

    @Test
    fun cleanupKeepsFilesInReadOnlyDirectory() {
        // Файлы остаются только если ядро соблюдает POSIX-права каталога.
        // Под root (docker-контейнеры, часть CI-образов) права игнорируются
        // (CAP_DAC_OVERRIDE) — файл удаляется, и эта проверка стала бы ложным
        // падением без дефекта в прод-коде. Такое окружение определяется
        // фактической пробой, а не именем пользователя.
        Assumptions.assumeTrue(
            posixPermissionsAreEnforced(tempDir),
            "POSIX-права каталога не соблюдаются (root / CAP_DAC_OVERRIDE) — проверка неприменима",
        )

        val f1 = tempDir.resolve("file1.log")
        val f2 = tempDir.resolve("file2.log")
        touch(f1, 60)
        touch(f2, 60)

        try {
            Files.setPosixFilePermissions(tempDir, readonlyDirPerms())

            KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30)

            // файлы не удалены — parent read-only
            assertTrue(Files.exists(f1), "file1 should still exist (parent read-only)")
            assertTrue(Files.exists(f2), "file2 should still exist (parent read-only)")
        } finally {
            // Восстанавливаем права для @TempDir cleanup
            Files.setPosixFilePermissions(tempDir, writableDirPerms())
        }
    }

    /**
     * Соблюдаются ли POSIX-права каталога в текущем окружении.
     *
     * Определяется пробой: файл из каталога с правами `555` удаляется ли
     * на самом деле. Под root удаление проходит (CAP_DAC_OVERRIDE) — значит
     * проверки «файл остался» быть не может.
     */
    private fun posixPermissionsAreEnforced(dir: Path): Boolean {
        val probe = dir.resolve("perm-probe-${System.nanoTime()}")
        Files.writeString(probe, "x")
        Files.setPosixFilePermissions(dir, readonlyDirPerms())
        return try {
            Files.deleteIfExists(probe)
            false
        } catch (_: java.nio.file.AccessDeniedException) {
            true
        } finally {
            Files.setPosixFilePermissions(dir, writableDirPerms())
            Files.deleteIfExists(probe)
        }
    }

    @Test
    fun cleanupKeepsFreshFilesForDebug() {
        // US-2: свежие логи (≤ 30 дней) сохраняются для диагностики;
        // граница — strict `>` (файл с mtime == threshold удаляется).
        val fresh = tempDir.resolve("fresh.log")
        val boundary = tempDir.resolve("boundary.log")
        val stale = tempDir.resolve("stale.log")
        touch(fresh, 14) // ≤ 30 дней → keep
        touch(boundary, 30) // ровно threshold → delete (strict >)
        touch(stale, 31) // > 30 дней → delete

        KaraokeProcessWorker.cleanupOldLogsIn(tempDir, retentionDays = 30)

        assertTrue(Files.exists(fresh), "14-day file should be kept for debug")
        assertFalse(Files.exists(boundary), "30-day file should be deleted (strict > boundary)")
        assertFalse(Files.exists(stale), "31-day file should be deleted")
    }
}

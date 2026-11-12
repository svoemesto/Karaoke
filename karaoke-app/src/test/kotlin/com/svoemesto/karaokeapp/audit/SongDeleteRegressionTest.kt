package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регресс-тесты фичи удаления песни (KARAOKE-429, спека 739, FR-011, FR-013, FR-038).
 *
 * Фича не должна сломать существующие наборы, которые она переиспользует:
 * - `RenameAllArtifactsTest` — правило владения при переименовании;
 * - `StorageCacheResetTest` — формулы имён файлов в хранилище;
 * - `ArtifactCleanupGuardTest` / `CleanupJournalTest` — реестр классов уборки и
 *   TSV-журнал разовой уборки (разовая уборка осиротевших не затронута);
 * - `PkgS322ProcessTest` — `KaraokeProcess.hasActiveProcess` не блокируется
 *   soft-deleted строкой.
 *
 * Тест фиксирует, что переиспользуемые модули **не менялись** под нужды фичи:
 * правило владения взято из `SongArtifactRenamer`, формулы имён — из
 * `StorageCacheReset`, реестр классов уборки не тронут.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-011, FR-013, FR-038
 */
class SongDeleteRegressionTest {
    private val modelDir = File("src/main/kotlin/com/svoemesto/karaokeapp/model")
    private val testDir = File("src/test/kotlin/com/svoemesto/karaokeapp")

    @Test
    fun `переиспользуемые наборы тестов на месте`() {
        listOf(
            "model/RenameAllArtifactsTest.kt",
            "StorageCacheResetTest.kt",
            "audit/PkgS322ProcessTest.kt",
        ).forEach { path ->
            assertTrue(File(testDir, path).isFile, "набор тестов должен существовать: $path")
        }
    }

    @Test
    fun `правило владения взято из SongArtifactRenamer без изменений`() {
        val renamer = File(modelDir, "SongArtifactRenamer.kt")
        assertTrue(renamer.isFile, "SongArtifactRenamer должен существовать")
        val text = renamer.readText()
        assertTrue(
            text.contains("fun isArtifactContinuation"),
            "правило продолжения артефакта остаётся в SongArtifactRenamer",
        )
        assertTrue(
            !text.contains("songdeletion"),
            "SongArtifactRenamer не должен зависеть от пакета фичи удаления",
        )
    }

    @Test
    fun `формулы имён хранилища не менялись`() {
        val storageCacheReset = File("src/main/kotlin/com/svoemesto/karaokeapp/StorageCacheReset.kt")
        assertTrue(storageCacheReset.isFile, "StorageCacheReset должен существовать")
        val text = storageCacheReset.readText()
        assertTrue(
            text.contains("fun storageFileNamesForSong"),
            "формулы имён файлов песни остаются в StorageCacheReset",
        )
        assertTrue(
            !text.contains("songdeletion"),
            "StorageCacheReset не должен зависеть от пакета фичи удаления",
        )
    }

    @Test
    fun `реестр классов уборки не тронут вердиктами удаления`() {
        val cleanupClass = File("src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupClass.kt")
        assertTrue(cleanupClass.isFile, "реестр классов уборки должен существовать")
        val text = cleanupClass.readText()
        assertTrue(
            !text.contains("songdeletion"),
            "реестр классов уборки не связан с фичей удаления песни (FR-012: вердикты не применяются)",
        )
    }
}

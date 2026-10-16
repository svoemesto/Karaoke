package com.svoemesto.karaokeapp.services.cleanup

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Юнит-тесты условия удаления локального mp3-стема (specs/544-song-artifact-cleanup, T017).
 *
 * Покрывает FR-003: локальная копия удаляется только после успешной загрузки во **все**
 * целевые хранилища; при частичном успехе копия MUST остаться. Проверяется на реальных
 * файлах: правило FR-003 вынесено в [ArtifactCleanupAutomation.deleteLocalCopyIfBothStoresHave],
 * которую вызывают оба исполнителя загрузки (`Utils.kt`: executeUploadToLocalStore /
 * executeUploadToRemoteStore).
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class StemsMp3LocalDeleteTest {
    @TempDir
    lateinit var tempDir: Path

    private fun localCopy(): Path {
        val f = tempDir.resolve("hdemucs_mmi/song-vocals.mp3")
        Files.createDirectories(f.parent)
        Files.writeString(f, "x".repeat(42))
        return f
    }

    @Test
    fun `полный успех - объект в обоих хранилищах, локальная копия удаляется`() {
        val file = localCopy()

        val deleted =
            ArtifactCleanupAutomation.deleteLocalCopyIfBothStoresHave(
                localPath = file.toString(),
                deleteWhenBothStoresHave = true,
                counterpartStoreHasFile = true,
            )

        assertTrue(deleted)
        assertFalse(Files.exists(file), "локальная копия должна быть удалена после успеха в обоих хранилищах")
    }

    @Test
    fun `частичный успех - второго хранилища нет, локальная копия остаётся`() {
        val file = localCopy()

        val deleted =
            ArtifactCleanupAutomation.deleteLocalCopyIfBothStoresHave(
                localPath = file.toString(),
                deleteWhenBothStoresHave = true,
                counterpartStoreHasFile = false,
            )

        assertFalse(deleted)
        assertTrue(Files.exists(file), "при частичном успехе локальная копия — единственная, удалять нельзя")
    }

    @Test
    fun `без признака необязательности локальная копия не удаляется`() {
        // Тип с LOCAL_FILESYSTEM (flac-стемы, картинки, видео) — его локальный файл обязателен
        val file = localCopy()

        val deleted =
            ArtifactCleanupAutomation.deleteLocalCopyIfBothStoresHave(
                localPath = file.toString(),
                deleteWhenBothStoresHave = false,
                counterpartStoreHasFile = true,
            )

        assertFalse(deleted)
        assertTrue(Files.exists(file))
    }

    @Test
    fun `правило FR-003 - таблица истинности`() {
        assertTrue(ArtifactCleanupAutomation.shouldDeleteLocalCopy(true, true))
        assertFalse(ArtifactCleanupAutomation.shouldDeleteLocalCopy(true, false))
        assertFalse(ArtifactCleanupAutomation.shouldDeleteLocalCopy(false, true))
        assertFalse(ArtifactCleanupAutomation.shouldDeleteLocalCopy(false, false))
    }

    @Test
    fun `отсутствующая локальная копия не считается ошибкой`() {
        val deleted =
            ArtifactCleanupAutomation.deleteLocalCopyIfBothStoresHave(
                localPath = tempDir.resolve("hdemucs_mmi/нет-такого.mp3").toString(),
                deleteWhenBothStoresHave = true,
                counterpartStoreHasFile = true,
            )

        assertFalse(deleted)
    }

    @Test
    fun `класс локальных mp3-стемов нацелен на подпапку модели`() {
        // Раскладка удаляемых путей: подпапка hdemucs_mmi, расширение mp3. Корень папки
        // песни (там исходник, FR-010) матчерами класса не покрывается.
        // Проверка через реестр, а не через Song: конструктор Song требует БД/окружения
        // и в юнит-тесте недоступен (Constitution § «Рабочий процесс»).
        val matchers = ArtifactCleanupClass.STEMS_MP3_LOCAL.matchers
        assertTrue(matchers.all { it.subFolder == ArtifactCleanupClass.STEMS_FOLDER })
        assertTrue(matchers.all { ".mp3" in it.extensions })
        // и ни один матчер класса не нацелен на корень папки песни
        assertTrue(matchers.none { it.subFolder.isEmpty() })
    }
}

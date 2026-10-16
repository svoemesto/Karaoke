package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты модели ожидаемых файлов для mp3-стемов (specs/544-song-artifact-cleanup, T015).
 *
 * Покрывает FR-004: модель перестала требовать локальную копию mp3-стема, поэтому проверка
 * здоровья не считает её отсутствие нарушением и не ставит `FF_MP3_*`-задачи (SC-006).
 *
 * Механизм: `HealthReport` обходит только объявленные `locations`, а `actionsLocalFileSystem`
 * дополнительно начинается с `if (!willBeInLocation) return emptyList()`. Пока у типа есть
 * `LOCAL_FILESYSTEM`, отсутствие локального файла порождало задачу перекодирования
 * (`HealthReport.kt`, ветки `MP3_*`); без этой локации ветка недостижима.
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class HealthReportMp3LocationsTest {
    private val mp3Types =
        listOf(
            KaraokeFileType.MP3_ACCOMPANIMENT,
            KaraokeFileType.MP3_VOCAL,
            KaraokeFileType.MP3_BASS,
            KaraokeFileType.MP3_DRUMS,
            KaraokeFileType.MP3_OTHER,
        )

    @Test
    fun `у всех mp3-типов больше нет локальной локации`() {
        mp3Types.forEach { type ->
            assertFalse(
                type.locations.contains(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
                "${type.name}: локальная копия больше не ожидается (FR-004)",
            )
            assertFalse(type.willBeInFileSystem, "${type.name}: willBeInFileSystem должен быть false")
        }
    }

    @Test
    fun `оба хранилища остаются ожидаемыми для mp3-типов`() {
        mp3Types.forEach { type ->
            assertTrue(type.willBeInLocalStorage, "${type.name}: локальное хранилище — источник истины")
            assertTrue(type.willBeInRemoteStorage, "${type.name}: удалённое хранилище — источник истины")
        }
    }

    @Test
    fun `возможность пересоздать mp3 из flac сохраняется`() {
        // research R2: canResolve остаётся ручным/восстановительным путём, просто он больше
        // не запускается автоматически из-за отсутствия локальной копии
        mp3Types.forEach { type ->
            assertTrue(type.canResolve, "${type.name}: перекодирование из flac должно остаться возможным")
        }
    }

    @Test
    fun `защищённые flac-типы по-прежнему требуют локальный файл`() {
        // FR-010: исходник и flac-стемы неприкосновенны — их модель не менялась
        listOf(
            KaraokeFileType.AUDIO_SONG,
            KaraokeFileType.AUDIO_ACCOMPANIMENT,
            KaraokeFileType.AUDIO_VOCAL,
            KaraokeFileType.AUDIO_BASS,
            KaraokeFileType.AUDIO_DRUMS,
            KaraokeFileType.AUDIO_OTHER,
        ).forEach { type ->
            assertTrue(
                type.locations.contains(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
                "${type.name}: flac-класс остаётся локальным и защищённым",
            )
        }
    }

    @Test
    fun `локальная копия не ожидается только у mp3-типов`() {
        // Изменение не должно задеть другие типы: список типов без LOCAL_FILESYSTEM
        // должен состоять ровно из mp3-стемов.
        val withoutLocal =
            KaraokeFileType.entries
                .filter { !it.willBeInFileSystem }
                .map { it.name }
                .sorted()
        assertEquals(mp3Types.map { it.name }.sorted(), withoutLocal)
    }
}

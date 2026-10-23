package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.StorageFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия по specs/552-mp3-stem-actuality: для storage-only mp3-типов дисковая копия
 * не является эталоном актуальности, а согласованность хранилищ проверяется по размеру.
 *
 * Сценарий бага (OpenProject #328): specs/544 удалил локальную копию, рендер демо открыл
 * плеер, `convertFlacToMp3` создал её заново с другим кодировщиком, и HealthReport сравнил
 * её размер с 320k-объектом в хранилище — 4 ложных ERROR на песню.
 *
 * @see specs/552-mp3-stem-actuality/spec.md FR-001…FR-007
 */
class HealthReportMp3ActualityTest {
    private val storageOnlyTypes =
        listOf(
            KaraokeFileType.MP3_ACCOMPANIMENT,
            KaraokeFileType.MP3_VOCAL,
            KaraokeFileType.MP3_BASS,
            KaraokeFileType.MP3_DRUMS,
            KaraokeFileType.MP3_OTHER,
        )

    private fun fileInfo(size: Long): StorageFileInfo = StorageFileInfo("karaoke", "song.accompaniment.mp3", "", size)

    @Test
    fun `для всех storage-only mp3-типов дисковая копия не является эталоном`() {
        storageOnlyTypes.forEach { type ->
            assertFalse(
                diskCopyIsAuthoritative(type),
                "${type.name}: дисковая копия не должна участвовать в проверке актуальности (FR-001)",
            )
        }
    }

    @Test
    fun `для типов с локальной файловой системой диск остаётся эталоном`() {
        listOf(
            KaraokeFileType.AUDIO_SONG,
            KaraokeFileType.AUDIO_ACCOMPANIMENT,
            KaraokeFileType.PICTURE_ALBUM,
        ).forEach { type ->
            assertTrue(diskCopyIsAuthoritative(type), "${type.name}: поведение для дисковых типов не должно меняться (FR-006)")
        }
    }

    @Test
    fun `одинаковые размеры в хранилищах — актуально`() {
        assertEquals(StorageActuality.OK, storageActuality(fileInfo(1_724_357), fileInfo(1_724_357)))
    }

    @Test
    fun `разные размеры в хранилищах — расхождение`() {
        assertEquals(StorageActuality.MISMATCH, storageActuality(fileInfo(3_618_525), fileInfo(1_724_357)))
    }

    @Test
    fun `неизвестный размер не считается расхождением`() {
        assertEquals(StorageActuality.OK, storageActuality(null, fileInfo(1)))
        assertEquals(StorageActuality.OK, storageActuality(fileInfo(1), null))
        assertEquals(StorageActuality.OK, storageActuality(null, null))
    }

    @Test
    fun `текст расхождения не ссылается на отсутствующий диск`() {
        assertFalse(STORAGE_MISMATCH_PROBLEM_TEXT.contains("диск", ignoreCase = true))
        assertFalse(STORAGE_MISMATCH_SOLUTION_TEXT.contains("диск", ignoreCase = true))
    }
}

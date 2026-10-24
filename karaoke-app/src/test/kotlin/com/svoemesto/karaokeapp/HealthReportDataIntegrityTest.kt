package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.file.Files

/**
 * Регрессия по аудиту #336, пакет `PKG-S2-13`, задача #376 (BR-278, BR-279).
 *
 * **BR-278.** «Неактуальность» объекта хранилища определялась сравнением
 * `File(pathToFile).length()` с размером из кеша, но существование файла
 * проверялось раньше — в `getHealthReportList`, до походов в кеш/БД/MinIO.
 * У исчезнувшего файла `length() == 0`, поэтому переименованный параллельно
 * файл (specs/124) получал вердикт «неактуально», и repair удалял валидный
 * объект из LOCAL/REMOTE-хранилища. После правки существование проверяется
 * повторно непосредственно перед сравнением ([diskFileMatchesStoredSize]).
 *
 * **BR-279.** `reconcilePlayerReadinessFlags` переводил 4 персистентных флага
 * готовности плеера в `false`, если в отчётах нет `OK`-записи `LOCAL_STORAGE`.
 * При открытом circuit breaker'е `actionsLocalStorage` файл вообще не
 * проверяет и возвращает `FATAL_ERROR`, поэтому сбой локального MinIO
 * обнулял готовность уже готовых песен. После правки такой отчёт помечается
 * `storageStateUnknown` и флаг сохраняет прежнее значение
 * ([localStorageReadinessVerdict]).
 *
 * @see specs/376-pkg-s2-13/report.md
 */
class HealthReportDataIntegrityTest {
    /** `Song` — final-класс с тяжёлым конструктором; отчёту нужен только держатель. */
    private val song: Song = mock(Song::class.java)

    private fun localReport(
        karaokeFileType: KaraokeFileType,
        status: HealthReportStatus,
        unknown: Boolean = false,
    ): HealthReport =
        HealthReport(
            song = song,
            description = "${karaokeFileType.name}/${KaraokeFileTypeLocations.LOCAL_STORAGE.name}",
            healthReportType = HealthReportType.FILE_VIOLATION,
            healthReportStatus = status,
            storageStateUnknown = unknown,
        )

    @Test
    fun `BR-278 исчезнувший с диска файл не даёт вердикт неактуально`() {
        val dir = Files.createTempDirectory("br278")
        val existing = dir.resolve("song.mp3")
        Files.write(existing, ByteArray(10))
        val vanished = dir.resolve("renamed.mp3")

        // «До»: старая формула `File(path).length() == storedSize` на исчезнувшем
        // файле возвращала false — то есть «неактуально» и удаление объекта.
        assertEquals(0L, vanished.toFile().length())
        assertFalse(vanished.toFile().length() == 10L)

        // «После»: без подтверждённого существования вердикт не выносится.
        assertNull(diskFileMatchesStoredSize(vanished.toString(), 10L))
        assertNull(diskFileMatchesStoredSize(vanished.toString(), 0L))
        assertNull(diskFileMatchesStoredSize("", 10L))

        // Реальные вердикты не изменились.
        assertEquals(true, diskFileMatchesStoredSize(existing.toString(), 10L))
        assertEquals(false, diskFileMatchesStoredSize(existing.toString(), 11L))

        Files.deleteIfExists(existing)
        Files.deleteIfExists(dir)
    }

    @Test
    fun `BR-279 отчёт из недоступного хранилища не сбрасывает флаги готовности`() {
        val readinessTypes =
            listOf(
                KaraokeFileType.MP3_ACCOMPANIMENT,
                KaraokeFileType.MP3_VOCAL,
                KaraokeFileType.PICTURE_ALBUM,
                KaraokeFileType.PICTURE_AUTHOR,
            )

        // Готовые песни: все четыре типа подтверждены OK в LOCAL_STORAGE.
        val readyReports = readinessTypes.map { localReport(it, HealthReportStatus.OK) }
        readinessTypes.forEach { type ->
            assertEquals(true, localStorageReadinessVerdict(readyReports, type), "${type.name}: OK → готов")
        }

        // «До»: circuit breaker отдаёт FATAL_ERROR, OK-записи нет — флаг сбрасывался.
        val circuitReports = readinessTypes.map { localReport(it, HealthReportStatus.FATAL_ERROR, unknown = true) }
        assertFalse(circuitReports.any { it.healthReportStatus == HealthReportStatus.OK })

        // «После»: проверка не выполнялась → состояние неизвестно, флаг не трогаем.
        readinessTypes.forEach { type ->
            assertNull(localStorageReadinessVerdict(circuitReports, type), "${type.name}: circuit breaker → неизвестно")
        }

        // WAITING (кеш ещё не заполнен) — тоже «неизвестно», а не «файла нет».
        val waitingReports = listOf(localReport(KaraokeFileType.MP3_ACCOMPANIMENT, HealthReportStatus.WAITING))
        assertNull(localStorageReadinessVerdict(waitingReports, KaraokeFileType.MP3_ACCOMPANIMENT))

        // Реальная проблема по-прежнему сбрасывает флаг: проверка выполнена, файла нет.
        val errorReports = listOf(localReport(KaraokeFileType.MP3_ACCOMPANIMENT, HealthReportStatus.ERROR))
        assertEquals(false, localStorageReadinessVerdict(errorReports, KaraokeFileType.MP3_ACCOMPANIMENT))
        assertEquals(false, localStorageReadinessVerdict(emptyList(), KaraokeFileType.MP3_ACCOMPANIMENT))
    }
}

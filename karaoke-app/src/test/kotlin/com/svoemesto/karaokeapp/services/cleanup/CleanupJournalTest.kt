package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.model.SongOutputFile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * Юнит-тесты журнала удалений (specs/544-song-artifact-cleanup, T007).
 *
 * Покрывает FR-012: журнал фиксирует каждое удаление — и успешное, и неудачное —
 * с путём, размером, классом, датой, утвердившим и runId.
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class CleanupJournalTest {
    private val videoExt = "." + SongOutputFile.VIDEO.extension

    @TempDir
    lateinit var tempDir: Path

    private fun journal() = CleanupJournal(tempDir.toString())

    private fun lines(classId: String): List<String> =
        Files.readAllLines(journal().journalPath(classId))

    @Test
    fun `первая запись создаёт файл класса с заголовком TSV`() {
        val ok = journal().appendDeleted("/sm-karaoke/done2/A/B/done_files/x [demo]" + videoExt, 100L, "DEMO_MP4", "Сергей Новиков", "r-1")

        assertTrue(ok)
        val rows = lines("DEMO_MP4")
        assertEquals(CleanupJournal.HEADER, rows[0])
        assertEquals(2, rows.size, "заголовок + одна запись")
        val cols = rows[1].split("\t")
        assertEquals(7, cols.size, "колонок ровно 7: ${rows[1]}")
        assertEquals("100", cols[1])
        assertEquals("DEMO_MP4", cols[2])
        assertEquals("deleted", cols[3])
        assertEquals("Сергей Новиков", cols[5])
        assertEquals("r-1", cols[6])
    }

    @Test
    fun `неудачное удаление пишется как failed с причиной`() {
        assertTrue(journal().appendFailed("/x/y.srt", 7L, "SRT", "owner", "r-2", "AccessDeniedException"))

        val row = lines("SRT")[1]
        val cols = row.split("\t")
        assertEquals("failed:AccessDeniedException", cols[3])
        assertEquals("/x/y.srt", cols[0])
        assertEquals("7", cols[1])
    }

    @Test
    fun `повторные записи дописываются, заголовок не дублируется`() {
        val j = journal()
        assertTrue(j.appendDeleted("/a/1.srt", 1L, "SRT", "owner", "r-3"))
        assertTrue(j.appendDeleted("/a/2.srt", 2L, "SRT", "owner", "r-3"))
        assertTrue(j.appendFailed("/a/3.srt", 3L, "SRT", "owner", "r-3", "busy"))

        val rows = lines("SRT")
        assertEquals(4, rows.size, "заголовок + три записи")
        assertEquals(1, rows.count { it == CleanupJournal.HEADER })
        // Колонка result (индекс 3): заголовок содержит подстроку "deleted" в имени
        // колонки deletedAt, поэтому проверяем именно значение колонки, а не вхождение.
        assertEquals(2, rows.drop(1).count { it.split("\t")[3] == "deleted" })
        assertEquals(1, rows.drop(1).count { it.split("\t")[3] == "failed:busy" })
    }

    @Test
    fun `разные классы пишутся в разные файлы`() {
        val j = journal()
        j.appendDeleted("/a/1.srt", 1L, "SRT", "owner", "r-4")
        j.appendDeleted("/a/1" + videoExt, 1L, "DEMO_MP4", "owner", "r-4")

        assertFalse(Files.readAllLines(j.journalPath("SRT")).isEmpty())
        assertFalse(Files.readAllLines(j.journalPath("DEMO_MP4")).isEmpty())
        assertTrue(j.journalPath("SRT").fileName.toString().endsWith("-SRT.tsv"))
        assertTrue(j.journalPath("DEMO_MP4").fileName.toString().endsWith("-DEMO_MP4.tsv"))
    }

    @Test
    fun `путь журнала содержит дату прогона`() {
        val path = journal().journalPath("DEMO_MP4", LocalDate.of(2026, 10, 16))
        assertEquals("2026-10-16-DEMO_MP4.tsv", path.fileName.toString())
    }

    @Test
    fun `табуляции и переводы строк в значениях не ломают формат`() {
        assertTrue(journal().appendFailed("/a/we\tird\nname.srt", 1L, "SRT", "own\ner", "r-5", "bad\treason"))

        val cols = lines("SRT")[1].split("\t")
        assertEquals(7, cols.size, "запись обязана остаться семиколоночной")
    }

    @Test
    fun `недоступный каталог журнала не бросает исключение, а возвращает false`() {
        // Путь, где создать каталог нельзя: родитель — обычный файл.
        val notADir = tempDir.resolve("file.txt")
        Files.writeString(notADir, "x")
        val journal = CleanupJournal(notADir.resolve("logs").toString())

        assertFalse(journal.journalDirWritable())
        assertFalse(journal.appendDeleted("/a/1.srt", 1L, "SRT", "owner", "r-6"))
    }

    @Test
    fun `runId генерируется с датой и классом`() {
        assertEquals("r-20261016-DEMO_MP4", CleanupJournal.newRunId("DEMO_MP4", LocalDate.of(2026, 10, 16)))
    }
}

package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.KaraokeFileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Тесты предохранителей разовой чистки (specs/544-song-artifact-cleanup, T029).
 *
 * Покрывает таблицу «Тестовые обязательства контракта»
 * (`specs/544-song-artifact-cleanup/contracts/cleanup-api.md`): защищённые классы
 * отклоняются, `plan` ничего не удаляет, `apply` требует отпечаток и подтверждение,
 * журнал доступен только когда есть куда писать.
 *
 * Проверяется сервис (не HTTP-слой): коды из [CleanupException] — те же, что возвращает
 * `CleanupController`.
 *
 * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
 */
class ArtifactCleanupGuardTest {
    @TempDir
    lateinit var tempDir: Path

    private fun service(): ArtifactCleanupService {
        val planner = ArtifactCleanupPlanner(roots = listOf(tempDir.toString()))
        val journal = CleanupJournal(tempDir.resolve("logs").toString())
        return ArtifactCleanupService(planner = planner, journal = journal)
    }

    private fun songFile(name: String): Path {
        val album = tempDir.resolve("Author/Album")
        Files.createDirectories(album.resolve("done_files"))
        val f = album.resolve(name)
        Files.writeString(f, "x".repeat(11))
        return f
    }

    @Test
    fun `plan на защищённый класс отклоняется и ничего не удаляет`() {
        val flac = songFile("song.flac")
        val svc = service()

        val e = runCatching { svc.plan("SOURCE_FLAC") }.exceptionOrNull()

        assertTrue(e is CleanupException, "ожидался CleanupException, получено: $e")
        assertEquals(400, (e as CleanupException).code)
        assertEquals("protected_class", e.errorCode)
        assertTrue(Files.exists(flac), "ни один файл не должен быть удалён (FR-010)")
    }

    @Test
    fun `plan на flac-стемы отклоняется`() {
        val stem = tempDir.resolve("Author/Album/hdemucs_mmi/song-vocals.flac")
        Files.createDirectories(stem.parent)
        Files.writeString(stem, "x")

        val e = runCatching { service().plan("STEMS_FLAC") }.exceptionOrNull()

        assertEquals(400, (e as CleanupException).code)
        assertTrue(Files.exists(stem))
    }

    @Test
    fun `plan на неизвестный класс отвечает 404`() {
        val e = runCatching { service().plan("NO_SUCH_CLASS") }.exceptionOrNull()
        assertEquals(404, (e as CleanupException).code)
        assertEquals("unknown_class", e.errorCode)
    }

    @Test
    fun `plan не удаляет ни одного файла и считает отчёт`() {
        val f1 = songFile("a [demo].bin")
        val f2 = songFile("b [demo].bin")
        Files.writeString(f1, "y".repeat(5))

        val plan = service().plan("DEMO_MP4")

        // матчер класса ищет расширение видео и суффикс [demo]; файлы .bin не подходят,
        // поэтому подкладываем настоящие имена ниже — здесь проверяем отсутствие удаления
        assertFalse(plan.entries.any { it.path.endsWith(".bin") }, "посторонние файлы в отчёт не попадают")
        assertTrue(Files.exists(f1))
        assertTrue(Files.exists(f2))
        assertNotNull(plan.fingerprint)
    }

    @Test
    fun `пустой отчёт не даёт применить удаление`() {
        val svc = service()
        val plan = svc.plan("DEMO_MP4")
        assertEquals(0L, plan.fileCount)

        val e =
            runCatching {
                svc.apply(plan.planId, plan.fingerprint, "Сергей Новиков", confirm = true)
            }.exceptionOrNull()

        assertEquals(400, (e as CleanupException).code)
        assertEquals("empty_plan", e.errorCode)
    }

    @Test
    fun `apply требует явного подтверждения`() {
        val svc = service()
        val plan = svc.plan("DEMO_MP4")

        val e = runCatching { svc.apply(plan.planId, plan.fingerprint, "owner", confirm = false) }.exceptionOrNull()
        assertEquals(400, (e as CleanupException).code)
        assertEquals("not_confirmed", e.errorCode)
    }

    @Test
    fun `apply требует утвердившего`() {
        val svc = service()
        val plan = svc.plan("DEMO_MP4")

        val e = runCatching { svc.apply(plan.planId, plan.fingerprint, "   ", confirm = true) }.exceptionOrNull()
        assertEquals(400, (e as CleanupException).code)
        assertEquals("no_approver", e.errorCode)
    }

    @Test
    fun `apply с неизвестным отчётом отвечает 404`() {
        val e = runCatching { service().apply("nope", "sha256:x", "owner", confirm = true) }.exceptionOrNull()
        assertEquals(404, (e as CleanupException).code)
        assertEquals("unknown_plan", e.errorCode)
    }

    @Test
    fun `apply с чужим отпечатком отклоняется и ничего не удаляет`() {
        val svc = service()
        // непустой отчёт: 3 файла, подходящие под матчер класса
        repeat(3) { i ->
            val f = tempDir.resolve("Author/Album/done_files/song$i [demo]" + videoExt())
            Files.createDirectories(f.parent)
            Files.writeString(f, "x".repeat(4))
        }
        val plan = svc.plan("DEMO_MP4")
        assertEquals(3L, plan.fileCount)

        val e =
            runCatching {
                svc.apply(plan.planId, "sha256:deadbeef", "Сергей Новиков", confirm = true)
            }.exceptionOrNull()

        assertEquals(409, (e as CleanupException).code)
        assertEquals("fingerprint_mismatch", e.errorCode)
        val survivors = Files.list(tempDir.resolve("Author/Album/done_files")).use { it.count() }
        assertEquals(3L, survivors, "при несовпадении отпечатка не должно быть удалено ничего")
    }

    @Test
    fun `apply с верным отпечатком удаляет файлы и пишет журнал`() {
        val svc = service()
        val files =
            (0 until 3).map { i ->
                val f = tempDir.resolve("Author/Album/done_files/song$i [demo]" + videoExt())
                Files.createDirectories(f.parent)
                Files.writeString(f, "x".repeat(4))
                f
            }
        val plan = svc.plan("DEMO_MP4")
        assertEquals(3L, plan.fileCount)

        val started = svc.apply(plan.planId, plan.fingerprint, "Сергей Новиков", confirm = true)
        val finished = svc.awaitRun(started.runId)

        assertEquals(CleanupRunState.DONE, finished.state)
        assertEquals(3L, finished.deletedCount)
        assertEquals(12L, finished.freedBytes)
        files.forEach { assertFalse(Files.exists(it), "файл $it должен быть удалён") }
        val journalFile = Path.of(finished.journalPath)
        assertTrue(Files.exists(journalFile), "журнал должен существовать (FR-012)")
        val rows = Files.readAllLines(journalFile)
        assertEquals(CleanupJournal.HEADER, rows[0])
        assertEquals(3, rows.count { it.split("\t")[3] == "deleted" })
        assertTrue(rows.drop(1).all { it.split("\t")[5] == "Сергей Новиков" })
    }

    @Test
    fun `недоступный журнал блокирует и plan, и apply`() {
        val blocker = tempDir.resolve("blocker")
        Files.writeString(blocker, "x")
        val svc =
            ArtifactCleanupService(
                planner = ArtifactCleanupPlanner(roots = listOf(tempDir.toString())),
                journal = CleanupJournal(blocker.resolve("logs").toString()),
            )

        val e = runCatching { svc.plan("DEMO_MP4") }.exceptionOrNull()
        assertEquals(503, (e as CleanupException).code)
        assertEquals("journal_unavailable", e.errorCode)
    }

    @Test
    fun `предпосылка порядка работ для локальных mp3-стемов выполнена`() {
        // FR-004/FR-013: класс STEMS_MP3_LOCAL допускается к чистке только после правки
        // модели ожидаемых файлов. Проверяем, что предпосылка действительно выполнена —
        // иначе сервис вернул бы 409 model_not_fixed.
        assertFalse(
            KaraokeFileType.MP3_ACCOMPANIMENT.willBeInFileSystem,
            "модель ожидаемых файлов должна быть поправлена до чистки класса STEMS_MP3_LOCAL",
        )
        val e = runCatching { service().plan("STEMS_MP3_LOCAL") }.exceptionOrNull()
        assertFalse(e is CleanupException && e.errorCode == "model_not_fixed")
    }

    @Test
    fun `порядок чистки от крупных низкорисковых к мелким`() {
        val ordered = ArtifactCleanupClass.deletable().map { it.name }
        assertEquals("DEMO_MP4", ordered.first())
        assertTrue(ordered.indexOf("DEMO_MP4") < ordered.indexOf("STEMS_MP3_LOCAL"))
        assertTrue(ordered.indexOf("STEMS_MP3_LOCAL") < ordered.indexOf("SRT"))
    }

    private fun videoExt(): String = "." + com.svoemesto.karaokeapp.model.SongOutputFile.VIDEO.extension
}

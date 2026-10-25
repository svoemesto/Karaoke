package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses
import com.svoemesto.karaokeapp.MediaInfo
import com.svoemesto.karaokeapp.MediaInfoException
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

/**
 * Регрессии пакета `PKG-S3-22` (аудит #336, OpenProject #394), часть 1 — порция P-04
 * (`KaraokeProcess`, `MediaInfo`) и `SecurityConfig`.
 *
 * Блоки «до/после»: каждый тест содержит реплику старого поведения (assert на «как было»)
 * и проверку нового production-шва. На старом коде вторая часть падает или не компилируется.
 *
 * @see specs/336-code-audit/findings/P-04.md
 * @see specs/394-pkg-s3-22/report.md
 */
class PkgS322ProcessTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    private class Jdbc {
        val connection: Connection = mock(Connection::class.java)
        val statement: Statement = mock(Statement::class.java)
        val prepared: PreparedStatement = mock(PreparedStatement::class.java)
        val resultSet: ResultSet = mock(ResultSet::class.java)

        init {
            Mockito.`when`(connection.createStatement()).thenReturn(statement)
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenReturn(prepared)
            Mockito.`when`(statement.executeQuery(Mockito.anyString())).thenReturn(resultSet)
            Mockito.`when`(prepared.executeQuery()).thenReturn(resultSet)
            Mockito.`when`(resultSet.next()).thenReturn(false)
        }

        fun sqlOfPrepare(): String {
            val captor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
            Mockito.verify(connection, Mockito.atLeastOnce()).prepareStatement(captor.capture())
            return captor.allValues.first()
        }
    }

    private fun process(id: Long, deletedAt: Timestamp? = null): KaraokeProcess =
        KaraokeProcess(
            database = FakeKaraokeConnection(),
            storageService = storageService,
            storageApiClient = storageApiClient,
        ).apply {
            this.id = id
            name = "p$id"
            deletedAt?.let { processDeletedAt = it }
        }

    // ------------------------------------------------------------------ BR-034
    @Test
    fun `BR-034 filter_limit больше числа строк не роняет запрос`() {
        val list = listOf(process(1), process(2), process(3))

        // Старое поведение: `for (i in 0 until limit) resultLimit.add(result[i])` при limit=1000
        // и трёх строках → IndexOutOfBoundsException (наружу HTTP 500).
        assertThrows<IndexOutOfBoundsException> {
            val old: MutableList<KaraokeProcess> = mutableListOf()
            for (i in 0 until 1000) old.add(list[i])
        }

        // Новое: срез через take(), лишний лимит безвреден.
        assertEquals(3, KaraokeProcess.applyFilterLimit(list, "1000").size)
        assertEquals(2, KaraokeProcess.applyFilterLimit(list, "2").size)
        assertEquals(0, KaraokeProcess.applyFilterLimit(list, "0").size)
    }

    @Test
    fun `BR-034 нечисловой и отрицательный filter_limit — ошибка контракта, а не 500`() {
        val list = listOf(process(1))

        // Старое поведение: `args["filter_limit"]?.toInt()` бросал NumberFormatException.
        assertThrows<NumberFormatException> { "abc".toInt() }

        assertThrows<IllegalArgumentException> { KaraokeProcess.applyFilterLimit(list, "abc") }
        assertThrows<IllegalArgumentException> { KaraokeProcess.applyFilterLimit(list, null) }
        assertThrows<IllegalArgumentException> { KaraokeProcess.applyFilterLimit(list, "-1") }
    }

    // ------------------------------------------------------------------ BR-037
    @Test
    fun `BR-037 loadList по умолчанию не отдаёт soft-deleted задания`() {
        val jdbc = Jdbc()
        val database = FakeKaraokeConnection(jdbc.connection)

        KaraokeProcess.loadList(args = mapOf("song_id" to "7"), database = database)
        val sql = jdbc.sqlOfPrepare()

        // Старое поведение: в WHERE не было ни одного упоминания process_deleted_at —
        // удалённое задание продолжало считаться активным (hasActiveProcess) и висело в UI.
        assertTrue(sql.contains("process_deleted_at"), "loadList должен фильтровать deleted: $sql")
        assertTrue(sql.contains("process_deleted_at IS NULL"), "нужен именно IS NULL: $sql")
    }

    @Test
    fun `BR-037 include_deleted возвращает удалённые для инструментов разбора`() {
        val jdbc = Jdbc()
        val database = FakeKaraokeConnection(jdbc.connection)

        KaraokeProcess.loadList(args = mapOf("song_id" to "7", "include_deleted" to "true"), database = database)
        val sql = jdbc.sqlOfPrepare()

        assertFalse(sql.contains("process_deleted_at IS NULL"), "явный include_deleted должен снимать фильтр: $sql")
    }

    @Test
    fun `BR-037 DTO отдаёт признак удаления`() {
        val deletedAt = Timestamp.from(Instant.parse("2026-10-20T10:00:00Z"))
        val dto = process(9, deletedAt).toDTO()
        assertEquals(deletedAt, dto.processDeletedAt)

        // Старый DTO поля не имел вовсе — клиент не мог отличить удалённое от активного.
        assertNull(process(10).toDTO().processDeletedAt)
    }

    @Test
    fun `BR-037 hasActiveProcess не блокируется soft-deleted строкой`() {
        // SQL-фильтр живёт в loadList; проверяем, что hasActiveProcess идёт через него
        // (иначе удалённое WAITING-задание навсегда блокировало переименование песни).
        val jdbc = Jdbc()
        val database = FakeKaraokeConnection(jdbc.connection)
        assertFalse(KaraokeProcess.hasActiveProcess(7L, database))
        assertTrue(jdbc.sqlOfPrepare().contains("process_deleted_at IS NULL"))
    }

    // ------------------------------------------------------------------ BR-036
    @Test
    fun `BR-036 сбой восстановления при старте виден вызывающему, а не глушится`() {
        val jdbc = Jdbc()
        Mockito
            .`when`(jdbc.connection.prepareStatement(Mockito.anyString()))
            .thenThrow(SQLException("database is locked"))
        val database = FakeKaraokeConnection(jdbc.connection)

        // Старое поведение: catch печатал e.message и функция возвращала Unit —
        // вызывающий не мог узнать, что WORKING-строки остались.
        assertFalse(KaraokeProcess.deleteDone(database), "сбой удаления DONE должен быть виден")
        assertFalse(KaraokeProcess.setWorkingToWaiting(database), "сбой сброса WORKING должен быть виден")
    }

    @Test
    fun `BR-036 причина ERROR дописывается к описанию задания`() {
        val description = "шаг separate"

        val result = KaraokeProcess.appendErrorReason(description, IllegalStateException("ffmpeg упал\nс кодом 1"))

        assertTrue(result.startsWith(description), "исходное описание должно сохраниться: $result")
        assertTrue(result.contains(KaraokeProcess.ERROR_REASON_MARKER), "нужен маркер причины: $result")
        assertTrue(result.contains("IllegalStateException"), "нужен тип исключения: $result")
        assertTrue(result.contains("кодом 1"), "нужно сообщение без перевода строки: $result")
        assertFalse(result.contains("\n"), "описание — одна строка: $result")

        // Идемпотентность: повторный вызов не накапливает хвосты.
        val again = KaraokeProcess.appendErrorReason(result, IllegalStateException("вторая ошибка"))
        assertEquals(1, Regex(Regex.escape(KaraokeProcess.ERROR_REASON_MARKER)).findAll(again).count())
        assertTrue(again.contains("вторая ошибка"))
        assertFalse(again.contains("ffmpeg упал"))

        // null-причина — описание не меняется (форс-стоп/штатный путь).
        assertEquals(description, KaraokeProcess.appendErrorReason(description, null))
    }

    @Test
    fun `BR-036 описание с причиной ограничено по длине`() {
        val longDescription = "x".repeat(2000)
        val result = KaraokeProcess.appendErrorReason(longDescription, RuntimeException("boom"))
        assertTrue(result.length <= KaraokeProcess.ERROR_REASON_MAX_LENGTH, "длина ${result.length}")
        assertTrue(result.endsWith("RuntimeException: boom"))
    }

    // ------------------------------------------------------------------ BR-035
    @Test
    fun `BR-035 зависший mediainfo убивается по таймауту, а не вешает поток`() {
        // Команда запускается напрямую (`/bin/sh -c`), а не через временный скрипт:
        // исполняемость файла в /tmp зависит от окружения (на CI-раннерах временный
        // каталог может быть noexec), и тест становился зависимым от машины.
        val hungCommand = sh("sleep 30; echo '{\"media\":{}}'")

        // Старое поведение: `ProcessBuilder(...).start()` + чтение inputStream до EOF без
        // waitFor() — вызов висел бы все 30 с (и дольше) и держал поток очереди.
        val startedAt = System.currentTimeMillis()
        val error =
            assertThrows<MediaInfoException> {
                MediaInfo.runMediaInfo(hungCommand, timeoutSeconds = 1)
            }
        val elapsed = System.currentTimeMillis() - startedAt

        assertTrue(elapsed < 10_000, "таймаут должен сработать быстро, прошло ${elapsed}мс")
        assertNotNull(error.message)
    }

    @Test
    fun `BR-035 ненулевой код возврата mediainfo — явная ошибка, а не пустой вывод`() {
        val failingCommand = sh("echo \"mediainfo: file not found\" >&2; exit 2")

        val error =
            assertThrows<MediaInfoException> {
                MediaInfo.runMediaInfo(failingCommand, timeoutSeconds = 5)
            }
        val message = error.message.orEmpty()
        assertTrue(message.contains("код"), "в ошибке должен быть код возврата: $message")
        assertTrue(message.contains("file not found"), "хвост вывода должен попадать в ошибку: $message")
    }

    @Test
    fun `BR-035 валидный JSON mediainfo читается как раньше`() {
        val script =
            stubScript(
                """
                echo '{"media":{"track":[{"@type":"Audio","Duration":"214.560"}]}}'
                """.trimIndent(),
            )

        val info = MediaInfo.getInfo("/tmp/whatever.flac", exePath = script.absolutePath)
        assertEquals(
            "214.560",
            ((info["media"] as Map<*, *>)["track"] as List<*>)
                .filterIsInstance<Map<*, *>>()
                .first { it["@type"] == "Audio" }["Duration"],
        )
    }

    @Test
    fun `BR-035 вывод быстро завершившегося процесса не теряется`() {
        // Регресс на гонку: destroyForcibly() закрывает потоки процесса. Если главный
        // поток доходит до него раньше, чем поток-читатель заберёт данные из пайпа,
        // вывод теряется целиком (IOException: Stream closed) — сообщение об ошибке
        // уходило без хвоста, а валидный JSON читался как пустой.
        // На быстрой машине читатель успевает, на CI-раннере (2 ядра) — нет.
        // Отдельной программой воспроизводилось как 13 пустых выводов из 300.
        val script =
            stubScript(
                """
                echo '{"media":{"track":[{"@type":"Audio","Duration":"214.560"}]}}'
                """.trimIndent(),
            )

        var empty = 0
        repeat(100) {
            val out = MediaInfo.runMediaInfo(listOf(script.absolutePath), timeoutSeconds = 5)
            if (out.isBlank()) empty++
        }
        assertEquals(0, empty, "вывод процесса терялся: $empty из 100 запусков")
    }

    /** Команда через /bin/sh: не зависит от прав на временном файле (см. комментарий в тесте). */
    private fun sh(script: String): List<String> = listOf("/bin/sh", "-c", script)

    private fun stubScript(body: String): File {
        val file = File.createTempFile("mediainfo-stub", ".sh")
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        file.deleteOnExit()
        return file
    }

    @Test
    fun `BR-036 статус WORKING остаётся WORKING если сброс не удался`() {
        // Дополнительная страховка: сбой восстановления не должен молча менять статусы в памяти.
        val process = process(11).apply { status = KaraokeProcessStatuses.WORKING.name }
        val jdbc = Jdbc()
        Mockito
            .`when`(jdbc.connection.prepareStatement(Mockito.anyString()))
            .thenThrow(SQLException("timeout"))
        KaraokeProcess.setWorkingToWaiting(FakeKaraokeConnection(jdbc.connection))
        assertEquals(KaraokeProcessStatuses.WORKING.name, process.status)
    }
}

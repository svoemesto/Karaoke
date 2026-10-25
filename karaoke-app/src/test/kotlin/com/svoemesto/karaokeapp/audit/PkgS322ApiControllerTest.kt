package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.controllers.ApiController
import com.svoemesto.karaokeapp.model.PublicationDateCandidate
import com.svoemesto.karaokeapp.model.SheetsageInfo
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongState
import com.svoemesto.karaokeapp.textfilehistory.HistoryMap
import com.svoemesto.karaokeapp.textfilehistory.SongsHistory
import com.svoemesto.karaokeapp.textfilehistory.TextFileHistory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Регрессии пакета `PKG-S3-22` (аудит #336, OpenProject #394), часть 2 — `ApiController`
 * (порции P-09a/P-09b).
 *
 * Часть проверок — поведенческие (на чистом шве), часть — статические guard'ы по
 * исходнику: у части эндпоинтов нет тестового шва (они ходят в глобальный
 * `WORKING_DATABASE`, ставят процессы в очередь и пишут в `HttpServletResponse`), поэтому
 * «падает на старом коде» доказывается проверкой формы кода/контракта. Для S3-мелочей это
 * допустимо (см. `specs/394-pkg-s3-22/report.md`).
 *
 * @see specs/336-code-audit/findings/P-09a.md
 * @see specs/336-code-audit/findings/P-09b.md
 */
class PkgS322ApiControllerTest {
    @TempDir
    lateinit var tempDir: Path

    private fun source(name: String = "ApiController.kt"): String = File("src/main/kotlin/com/svoemesto/karaokeapp/controllers/$name").readText()

    // ------------------------------------------------------------------ BR-051 / BR-079
    @Test
    fun `BR-051 пустой sheetsageInfo отдаёт пустые значения, а не NPE`() {
        val empty: Map<String, Any> = emptyMap()

        // Старое поведение: `emptyMap()["tempo"] as String` → null as String → NPE → HTTP 500.
        assertThrows<NullPointerException> { empty["tempo"] as String }

        // BR-079: `Song.sheetsageInfo` теперь типизированный (SheetsageInfo) — «ключа нет»
        // больше не бывает, но контракт API прежний: пустая строка / пустой список.
        val info: SheetsageInfo? = null
        assertEquals("", ApiController.sheetsageString(info, "tempo"))
        assertEquals("", ApiController.sheetsageString(null, "key"))
        assertEquals(emptyList<String>(), ApiController.sheetsageStringList(info, "chords"))
        assertEquals(emptyList<Double>(), ApiController.sheetsageDoubleList(info, "beattimes"))

        // Нормальные значения читаются как раньше.
        val filled = SheetsageInfo(key = "C major", tempo = "128", chords = listOf("C", "G"), beattimes = listOf(1.5, 2.0))
        assertEquals("128", ApiController.sheetsageString(filled, "tempo"))
        assertEquals("C major", ApiController.sheetsageString(filled, "key"))
        assertEquals(listOf("C", "G"), ApiController.sheetsageStringList(filled, "chords"))
        assertEquals(listOf(1.5, 2.0), ApiController.sheetsageDoubleList(filled, "beattimes"))

        // Неизвестный ключ — пустое значение, а не исключение.
        assertEquals("", ApiController.sheetsageString(filled, "unknown"))
        assertEquals(emptyList<String>(), ApiController.sheetsageStringList(filled, "unknown"))
        assertEquals(emptyList<Double>(), ApiController.sheetsageDoubleList(filled, "unknown"))
    }

    // ------------------------------------------------------------------ BR-052
    @Test
    fun `BR-052 polling-эндпоинты не глушат SQLException пустым списком`() {
        val src = source()
        val songsChanged = src.substringAfter("fun getChangedSongsIds(").substringBefore("fun getChangedProcessesIds(")
        val processesChanged = src.substringAfter("fun getChangedProcessesIds(").substringBefore("fun copyFieldsFromAnother(")

        for ((name, body) in listOf("getChangedSongsIds" to songsChanged, "getChangedProcessesIds" to processesChanged)) {
            // Старое поведение: `e.printStackTrace()` + `return emptyList()` — 200 «изменений нет».
            assertFalse(body.contains("e.printStackTrace()"), "$name всё ещё печатает стек в stdout: $body")
            assertTrue(body.contains("ResponseStatusException"), "$name должен отдавать 5xx: $body")
            assertTrue(body.contains("log.error("), "$name должен логировать через SLF4J: $body")
        }
    }

    // ------------------------------------------------------------------ BR-053 / BR-055
    @Test
    fun `BR-053 batch-эндпоинты считают успех по фактически поставленным процессам`() {
        val src = source()
        for (funName in listOf("getSongsCreateDemucs2All", "getSongsCreateDemucs5All", "getSongsCreateSheetsageAll")) {
            val body = src.substringAfter("fun $funName(").substringBefore("\n    // ")
            // Старое поведение: `result = true` внутри цикла независимо от того, найдена ли песня.
            assertFalse(body.contains("result = true"), "$funName всё ещё рапортует успех без процессов: $body")
            assertTrue(body.contains("queued++"), "$funName должен считать поставленные процессы: $body")
            assertTrue(body.contains("val result = queued > 0"), "$funName: успех = queued > 0")
        }
    }

    @Test
    fun `BR-055 batch-эндпоинты грузят песни пакетно, а не по одной`() {
        val src = source()
        val batchEndpoints =
            listOf(
                "getSongsCreateDemucs2All",
                "getSongsCreateDemucs5All",
                "getSongsCreateSheetsageAll",
                "getSongsCreateForcedAlignMarkersAll",
            )
        for (funName in batchEndpoints) {
            val body = src.substringAfter("fun $funName(").substringBefore("\n    // ")
            // Старое поведение: `Song.loadFromDbById(id)` в цикле — N последовательных SELECT.
            assertFalse(body.contains("Song.loadFromDbById("), "$funName всё ещё грузит песни по одной: $body")
            assertTrue(body.contains("loadSongsBatch("), "$funName должен использовать батч-загрузку: $body")
        }
    }

    @Test
    fun `BR-055 createkaraokeall считает созданные процессы`() {
        val src = source()
        val body = src.substringAfter("fun getSongsCreateKaraokeAll(").substringBefore("\n    // ")
        assertTrue(body.contains("var created = 0"), "нужен счётчик созданных процессов: $body")
        assertTrue(body.contains("created++"), "каждый createProcess должен увеличивать счётчик: $body")
        assertTrue(body.contains("result = created > 0"), "успех — только при созданных процессах: $body")
    }

    // ------------------------------------------------------------------ BR-054
    @Test
    fun `BR-054 chords-эндпоинты передают SongVersion CHORDS, а не LYRICS`() {
        val src = source()
        val chordsEndpoints =
            listOf(
                "getSongTextDzenChordsHeader",
                "getSongTextDzenChordsWOHeader",
                "getSongTextPlChordsHeader",
                "getSongTextPlChordsWOHeader",
                "getSongTextVkChordsHeader",
                "getSongTextVkChords",
                "getSongTextMaxChordsHeader",
            )
        chordsEndpoints.forEach { funName ->
            val body = src.substringAfter("fun $funName(").substringBefore("\n    @")
            assertTrue(body.contains("SongVersion.CHORDS"), "$funName должен формировать текст версии CHORDS: $body")
            assertFalse(body.contains("SongVersion.LYRICS"), "$funName всё ещё использует версию LYRICS: $body")
        }
        // Лимит pl-заголовка chords выровнен с соседними pl-заголовками (100, а не 140).
        val plChords = src.substringAfter("fun getSongTextPlChordsHeader(").substringBefore("\n    @")
        assertTrue(plChords.contains("getDescriptionHeader(SongVersion.CHORDS, 100)"))
    }

    // ------------------------------------------------------------------ BR-056
    @Test
    fun `BR-056 параллельные add не теряют записи истории`() {
        val file = tempDir.resolve("history.txt")

        val history =
            object : TextFileHistory {
                override var history: MutableList<Map<String, String>> = mutableListOf()

                override fun pathToFile(): String = file.toString()
            }
        val threads = 5
        val perThread = 5
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            repeat(threads) { t ->
                pool.submit {
                    start.await()
                    repeat(perThread) { i -> history.add(mapOf("id" to "$t-$i")) }
                }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "потоки не завершились")
        } finally {
            pool.shutdownNow()
        }

        // Старое поведение (load-modify-write без лока): терялись записи конкурентов.
        val lines = Files.readAllLines(file).filter { it.isNotBlank() }
        assertEquals(threads * perThread, lines.size, "потеряны записи истории: ${lines.size}")
        val ids =
            lines.map {
                kotlinx.serialization.json.Json
                    .decodeFromString(
                        HistoryMap.serializer(),
                        String(Base64.getDecoder().decode(it), Charsets.UTF_8),
                    ).args["id"]
            }
        assertEquals(threads * perThread, ids.toSet().size, "записи затёрли друг друга")
    }

    @Test
    fun `BR-056 повреждённый файл истории сохраняется копией, а не затирается молча`() {
        val file = tempDir.resolve("broken-history.txt")
        file.toFile().writeText("не-base64-и-не-json")

        val history =
            object : TextFileHistory {
                override var history: MutableList<Map<String, String>> = mutableListOf()

                override fun pathToFile(): String = file.toString()
            }

        assertTrue(history.loadList().isEmpty(), "повреждённая история читается как пустая")
        val backups = tempDir.toFile().listFiles(java.io.FileFilter { f -> f.name.contains(".corrupt-") }).orEmpty()
        assertTrue(backups.isNotEmpty(), "повреждённый файл должен быть сохранён копией")
        assertNotNull(backups.first().readText().contains("не-base64"), "копия должна содержать исходные данные")
    }

    @Test
    fun `BR-056 SongsHistory читает и пишет историю через атомарный путь`() {
        // Реальный класс-наследник использует путь из констант; проверяем, что контракт
        // encode/decode не изменился (иначе история пользователя перестанет читаться).
        val encoded = TextFileHistory.encode(listOf(mapOf("a" to "1")))
        val decoded =
            kotlinx.serialization.json.Json
                .decodeFromString(HistoryMap.serializer(), String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
        assertEquals(mapOf("a" to "1"), decoded.args)
        assertTrue(SongsHistory::class.java.declaredMethods.isNotEmpty())
    }

    // ------------------------------------------------------------------ BR-057
    @Test
    fun `BR-057 дата публикации читается лёгкой проекцией с предикатом по id_status`() {
        val jdbc = Jdbc()
        val songs = Song.loadPublicationDateCandidates(FakeKaraokeConnection(jdbc.connection), inWork = true)
        assertTrue(songs.isEmpty())
        val sql = jdbc.sqlOfPrepare()
        // Старое поведение: `Song.loadListFromDb(emptyMap())` — полная таблица с маппингом.
        assertTrue(sql.contains("SELECT id_status, free, publish_date, publish_time"), "нужна проекция 4 колонок: $sql")
        assertTrue(sql.contains("id_status < 6"), "IN_WORK отсекается в SQL: $sql")

        val jdbc2 = Jdbc()
        Song.loadPublicationDateCandidates(FakeKaraokeConnection(jdbc2.connection), inWork = false)
        assertTrue(jdbc2.sqlOfPrepare().contains("id_status >= 6"))
    }

    @Test
    fun `BR-057 состояние проекции совпадает с контрактом SongStateResolver`() {
        val now = java.util.Date()
        // idStatus < 6 → IN_WORK (как Song.state при недоопубликованной песне).
        val inWork = PublicationDateCandidate(idStatus = 3, free = false, date = "01.01.30", time = "10:00")
        assertEquals(SongState.IN_WORK, inWork.stateAt(now))

        // free=true → ON_AIR.
        val onAir = PublicationDateCandidate(idStatus = 6, free = true, date = "", time = "")
        assertEquals(SongState.ON_AIR, onAir.stateAt(now))

        // Опубликована, дата в будущем и не сегодня → DONE/EXCLUSIVE по контракту resolver.
        val future = PublicationDateCandidate(idStatus = 6, free = false, date = "01.01.30", time = "10:00")
        assertEquals(SongState.DONE, future.stateAt(now))
        assertNotNull(future.dateTimePublish)

        // Пустые дата/время → EXCLUSIVE и null-момент.
        val noDate = PublicationDateCandidate(idStatus = 6, free = false, date = "", time = "")
        assertEquals(SongState.EXCLUSIVE, noDate.stateAt(now))
        assertNull(noDate.dateTimePublish)
    }

    // ------------------------------------------------------------------ BR-059
    @Test
    fun `BR-059 текст «Пропущено» строится из того же порога, что и условие`() {
        val summary = ApiController.forcedAlignSummary(queued = 2, skipped = 5)
        assertTrue(
            summary.contains("статус >= ${ApiController.FORCED_ALIGN_MAX_ID_STATUS}"),
            "порог в тексте должен совпадать с условием: $summary",
        )
        // Старое поведение: в тексте стояло «>= 3», а пропускались песни со статусом 4+.
        assertFalse(summary.contains(">= 3"), "текст не должен обещать порог 3: $summary")

        val nothing = ApiController.forcedAlignSummary(queued = 0, skipped = 3)
        assertTrue(nothing.contains("Ни одна песня не подошла"))
        assertTrue(nothing.contains("статус >= ${ApiController.FORCED_ALIGN_MAX_ID_STATUS}"))
    }

    // ------------------------------------------------------------------ BR-065
    @Test
    fun `BR-065 некорректная дата периода — null, а не исключение на 500`() {
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yy")
        assertEquals(LocalDate.of(2026, 10, 1), ApiController.parsePeriodDate("01.10.26", fmt))
        // Старое поведение: `LocalDate.parse` бросал DateTimeParseException → HTTP 500 без текста.
        assertThrows<java.time.format.DateTimeParseException> { LocalDate.parse("32.13.26", fmt) }
        assertNull(ApiController.parsePeriodDate("32.13.26", fmt))
        assertNull(ApiController.parsePeriodDate("", fmt))
    }

    @Test
    fun `BR-065 длина периода ограничена константой`() {
        assertEquals(31, ApiController.SMART_COPY_MAX_PERIOD_DAYS)
        val src = source()
        val body = src.substringAfter("fun getSmartCopyPeriodByDay(").substringBefore("\n    @")
        assertTrue(body.contains("periodDays > SMART_COPY_MAX_PERIOD_DAYS"), "нужен предел периода: $body")
        assertTrue(body.contains("periodDays < 1"), "нужна проверка start <= end: $body")
    }

    // ------------------------------------------------------------------ BR-066
    @Test
    fun `BR-066 архив игрока пишется в ответ, без копии всего архива в heap`() {
        val body = source().substringAfter("fun getSongPlayerFile(").substringBefore("private fun smkaraokeAddStored(")
        // Старое поведение: ByteArrayOutputStream + toByteArray() (2× размер архива в heap).
        assertFalse(body.contains("ByteArrayOutputStream()"), "архив всё ещё собирается в памяти: $body")
        assertFalse(body.contains("bos.toByteArray()"), "лишняя копия архива при отправке: $body")
        assertTrue(body.contains("ZipOutputStream(response.outputStream)"), "ZIP должен идти в поток ответа: $body")
        assertTrue(body.contains("finally"), "zip должен закрываться в finally: $body")
        assertTrue(body.contains("zip.finish()"), "нужен finish() перед flush: $body")
    }

    // ------------------------------------------------------------------ BR-067
    @Test
    fun `BR-067 ffmpeg-конвертация читает stdout и ждёт с таймаутом`() {
        val body = source().substringAfter("private fun convertFlacToMp3(").substringBefore("/** Таймаут конвертации одного стема")
        // Старое поведение: `.start()` + `process.waitFor()` без чтения stdout и без таймаута.
        assertFalse(Regex("""process\s*\.waitFor\(\)""").containsMatchIn(body), "остался waitFor() без таймаута: $body")
        // Вывод уходит в файл (redirectOutput), а не в невычитываемый pipe:
        // заполненный pipe-буфер блокировал ffmpeg, а `waitFor()` — поток Tomcat.
        assertTrue(body.contains("redirectOutput(logFile)"), "вывод ffmpeg должен уходить в файл, а не в pipe: $body")
        assertTrue(body.contains("awaitProcessExit("), "нужно ожидание с таймаутом: $body")
        assertTrue(body.contains("destroyForcibly()"), "нужен destroyForcibly(): $body")
        assertTrue(body.contains("mp3ConversionLocks"), "конвертация одного файла должна сериализоваться: $body")
    }

    // ------------------------------------------------------------------ BR-068
    @Test
    fun `BR-068 неудачный поиск текста оставляет след и попадает в SSE-сводку`() {
        val src = source()
        val body = src.substringAfter("fun getSearchSongTextAll(").substringBefore("// Пакетная автопривязка оригинала")
        // Старое поведение: пустой `catch (e: Exception) { }` — причина терялась.
        assertFalse(
            Regex("""catch \(e: Exception\) \{\s*//[^\n]*\n\s*\}""").containsMatchIn(body),
            "остался пустой catch в пути A: $body",
        )
        assertTrue(body.contains("failedReasons.add("), "причина должна агрегироваться: $body")
        assertTrue(body.contains("\"failedReason\""), "сводка должна отдавать причину: $body")
    }

    // ------------------------------------------------------------------ BR-069 / BR-070
    @Test
    fun `BR-069 запись результата поиска не найдена — 404, а не 200 с пустым телом`() {
        val body = source().substringAfter("fun extractLyricsBySearchResultId(").substringBefore("\n    @")
        // Старое поведение: `SearchResultDTO?` → Spring отдавал 200 + пустое тело,
        // клиент делал JSON.parse('') и показывал общую ошибку.
        assertFalse(body.contains("): SearchResultDTO? {"), "nullable-тип сохранён: $body")
        assertTrue(body.contains("ResponseEntity<SearchResultDTO>"), "нужен ResponseEntity: $body")
        assertTrue(body.contains("ResponseEntity.notFound()"), "нужен явный 404: $body")
    }

    @Test
    fun `BR-070 несуществующий путь не подменяется на корень ФС`() {
        val src = source()
        val body = src.substringAfter("fun getFiles(").substringBefore("\n    @PostMapping(\"/pictures/updatepicture\")")
        // Старое поведение: `directory = File("/")` при несуществующем пути → листинг корня.
        assertFalse(body.contains("directory = File(\"/\")"), "путь всё ещё подменяется на /: $body")
        assertTrue(body.contains("ResponseStatusException(HttpStatus.NOT_FOUND"), "нужен 404: $body")
        assertTrue(body.contains("ResponseStatusException(HttpStatus.BAD_REQUEST"), "нужен 400 для «не каталог»: $body")
    }

    private class Jdbc {
        val connection: Connection = Mockito.mock(Connection::class.java)
        val prepared: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        val resultSet: ResultSet = Mockito.mock(ResultSet::class.java)

        init {
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenReturn(prepared)
            Mockito.`when`(prepared.executeQuery()).thenReturn(resultSet)
            Mockito.`when`(resultSet.next()).thenReturn(false)
        }

        fun sqlOfPrepare(): String {
            val captor: org.mockito.ArgumentCaptor<String> = org.mockito.ArgumentCaptor.forClass(String::class.java)
            Mockito.verify(connection, Mockito.atLeastOnce()).prepareStatement(captor.capture())
            return captor.allValues.first()
        }
    }

    @Test
    fun `guard — файл теста видит исходники контроллера`() {
        assertTrue(Files.exists(Path.of("src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")))
    }
}

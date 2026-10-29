package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-25` (аудит #336, OpenProject #397), партии 2–3 —
 * КОНТРАКТНЫЕ проверки по исходникам.
 *
 * На коде «до» правки каждое утверждение падает (в исходнике есть старая
 * конструкция) — это и есть доказательство «до/после».
 *
 * @see specs/397-pkg-s3-25/report.md
 */
class PkgS325Batch2SourceContractsTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    /**
     * Исходник БЕЗ комментариев — иначе утверждение «в файле нет `X`» ловило бы
     * само себя в тексте пояснения к правке.
     *
     * Комментарии вырезаются КОНЕЧНЫМ автоматом (состояния: обычный код, строковый
     * литерал, блочный комментарий, строчный комментарий). Наивная замена по
     * регулярке ломается на маркере начала блочного комментария внутри строкового
     * литерала (например, в `Utils.kt` есть строка с таким маркером — регулярка
     * съедала пол-файла).
     */
    private fun readCode(relativePath: String): String = stripComments(repoFile(relativePath).readText())

    private fun stripComments(source: String): String {
        val sb = StringBuilder(source.length)
        var i = 0
        var inBlockComment = false
        var inLineComment = false
        var inString = false
        var inChar = false
        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else ' '
            when {
                inBlockComment ->
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        sb.append(" ")
                        i++
                    }
                inLineComment ->
                    if (c == '\n') {
                        inLineComment = false
                        sb.append(c)
                    }
                inString ->
                    if (c == '\\') {
                        sb.append(c).append(next)
                        i++
                    } else {
                        sb.append(c)
                        if (c == '"') inString = false
                    }
                inChar ->
                    if (c == '\\') {
                        sb.append(c).append(next)
                        i++
                    } else {
                        sb.append(c)
                        if (c == '\'') inChar = false
                    }
                else ->
                    when {
                        c == '/' && next == '*' -> {
                            inBlockComment = true
                            i++
                        }
                        c == '/' && next == '/' -> {
                            inLineComment = true
                            i++
                        }
                        c == '"' -> {
                            inString = true
                            sb.append(c)
                        }
                        c == '\'' -> {
                            inChar = true
                            sb.append(c)
                        }
                        else -> sb.append(c)
                    }
            }
            i++
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- BR-245

    @Test
    fun `BR-245 значения свойств разбираются по типу без исключений`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertFalse(
            source.contains("stringValue.toLong()"),
            "BR-245: toLong() бросает NumberFormatException → 500",
        )
        assertFalse(
            source.contains("stringValue.toInt()"),
            "BR-245: toInt() бросает NumberFormatException → 500",
        )
        assertFalse(
            source.contains("stringValue.toDouble()"),
            "BR-245: toDouble() бросает NumberFormatException → 500",
        )
        assertFalse(
            source.contains("stringValue.toBoolean()"),
            "BR-245: toBoolean() молча превращает опечатку в false",
        )
        assertTrue(
            source.contains("toLongOrNull()") && source.contains("toIntOrNull()") && source.contains("toDoubleOrNull()"),
            "BR-245: числа должны разбираться через toXxxOrNull",
        )
        assertTrue(
            source.contains("parseBooleanStrict"),
            "BR-245: boolean должен разбираться явно (true/false/1/0)",
        )
    }

    @Test
    fun `BR-245 неизвестный ключ не молчаливый no-op`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertFalse(
            source.contains("if (defaultValue !== null) {"),
            "BR-245: неизвестный ключ должен давать ошибку, а не тихий no-op",
        )
        assertTrue(
            source.contains("KaraokePropertyUnknownKeyException"),
            "BR-245: неизвестный ключ должен быть различим",
        )
    }

    // ---------------------------------------------------------------- BR-246

    @Test
    fun `BR-246 три ключа karaoke-web зарегистрированы`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        listOf(
            "karaoke.public.public-settings-cache.enabled",
            "karaoke.web.events.batch-enabled",
            "karaoke.web.events.batch-max-buffer-size",
        ).forEach { key ->
            assertTrue(source.contains("\"$key\""), "BR-246: ключ «$key» должен быть зарегистрирован")
        }
    }

    // ---------------------------------------------------------------- BR-248

    @Test
    fun `BR-248 флаг загрузки volatile и карта публикуется после наполнения`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertTrue(
            source.contains("@Volatile") && source.contains("propertiesLoadAttempted"),
            "BR-248: флаг загрузки должен быть volatile",
        )
        // Флаг ставится ПОСЛЕ наполнения карты (release barrier), а не до.
        val flagIndex = source.indexOf("propertiesLoadAttempted = true")
        val mapIndex = source.indexOf("karaokePropertiesMap[it.key] = it.defaultValue")
        assertTrue(flagIndex > 0 && mapIndex > 0, "BR-248: не найдены строки загрузки")
        assertTrue(
            mapIndex < flagIndex,
            "BR-248: флаг загрузки должен ставиться ПОСЛЕ наполнения карты",
        )
    }

    // ---------------------------------------------------------------- BR-249

    @Test
    fun `BR-249 результат записи файла свойств наблюдаем`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertTrue(
            source.contains("fun savePropertiesMap(): Boolean"),
            "BR-249: savePropertiesMap должен возвращать признак успеха",
        )
        assertTrue(
            source.contains("log.error(") && source.contains("не удалось сохранить файл"),
            "BR-249: ошибка записи должна логироваться со стеком",
        )
        val api = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")
        assertTrue(
            api.contains("KaraokePropertyValueException") && api.contains("KaraokePropertyUnknownKeyException"),
            "BR-249/245: контроллер должен различать ошибки значения и ключа",
        )
    }

    // ---------------------------------------------------------------- BR-250

    @Test
    fun `BR-250 файл свойств разбирается построчно`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertTrue(
            source.contains("readPropertiesFileTolerant"),
            "BR-250: разбор файла должен быть построчным (битая строка — не весь файл)",
        )
        assertTrue(
            source.contains("brokenCount"),
            "BR-250: число битых строк должно учитываться",
        )
    }

    // ---------------------------------------------------------------- BR-252

    @Test
    fun `BR-252 DELETE и INSERT в tbl_songs_sync — в одной транзакции`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertFalse(
            source.contains("Song.deleteFromDb(id = id, database = Connection.remote(), sync = true)"),
            "BR-252: удаление sync-записи до проверки соединения теряло постановку",
        )
        assertTrue(
            source.contains("connection.autoCommit = false"),
            "BR-252: DELETE+INSERT должны идти в транзакции",
        )
        assertTrue(
            source.contains("connection.rollback()"),
            "BR-252: при ошибке транзакция должна откатываться",
        )
    }

    // ---------------------------------------------------------------- BR-253 / BR-254

    @Test
    fun `BR-253 флаги фоновых задач захватываются атомарно`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertFalse(
            source.contains("if (isFindParentInProgress) return \"ALREADY_RUNNING\""),
            "BR-253: check-then-set на @Volatile-флаге — не атомарно",
        )
        assertTrue(
            source.contains("isFindParentInProgress.compareAndSet(false, true)"),
            "BR-253: захват флага должен быть через compareAndSet",
        )
        assertTrue(
            source.contains("isFindAudioParentInProgress.compareAndSet(false, true)") &&
                source.contains("isCensoredRescanInProgress.compareAndSet(false, true)"),
            "BR-253: все три флага должны захватываться атомарно",
        )
    }

    @Test
    fun `BR-254 флаги снимаются в finally`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertTrue(
            source.contains("onFinally = { isFindParentInProgress.set(false) }") &&
                source.contains("onFinally = { isFindAudioParentInProgress.set(false) }") &&
                source.contains("onFinally = { isCensoredRescanInProgress.set(false) }"),
            "BR-254: флаги должны сниматься в finally потока",
        )
        val oneShot = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/OneShotThread.kt")
        assertTrue(
            oneShot.contains("onFinally: () -> Unit = {}"),
            "BR-254: startOneShotThread должен уметь снять флаг в finally",
        )
    }

    // ---------------------------------------------------------------- BR-255

    @Test
    fun `BR-255 поток обхода файлов закрывается`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val byMasks = source.substringAfter("fun getFileNameByMasks").substringBefore("fun createSongTextFile")
        assertTrue(
            byMasks.contains(".use { stream ->"),
            "BR-255: Files.walk обязан быть закрыт через use",
        )
        assertFalse(
            byMasks.contains("catch (_: Exception)"),
            "BR-255: ошибка обхода не должна глушиться молча",
        )
    }

    // ---------------------------------------------------------------- BR-256

    @Test
    fun `BR-256 битая строка CSV не роняет массовое обновление`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val bpm = source.substringAfter("fun getBpmAndKeyFromCsv").substringBefore("fun delDublicates")
        assertFalse(
            bpm.contains("e.printStackTrace()"),
            "BR-256: printStackTrace вместо логгера со стеком",
        )
        assertTrue(
            bpm.contains("catch (e: Exception)"),
            "BR-256: ловиться должен Exception (RuntimeException тоже), а не только IOException",
        )
        assertTrue(
            bpm.contains("toLongOrNull()"),
            "BR-256: нечисловой BPM должен быть пропуском, а не исключением",
        )
    }

    // ---------------------------------------------------------------- BR-257

    @Test
    fun `BR-257 ветки поиска текста не возвращают HTML-разметку`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val search = source.substringAfter("fun searchSongText").substringBefore("fun fillFormattedFields")
        assertFalse(
            Regex("""var text = lyricsElement\?\.html\(\)""").containsMatchIn(search),
            "BR-257: html() в найденных ветках писал разметку в sourceText",
        )
        assertTrue(
            search.contains("htmlToPlainTextWithNewlines"),
            "BR-257: разметка должна превращаться в текст",
        )
    }

    // ---------------------------------------------------------------- BR-259

    @Test
    fun `BR-259 шаги задания сопоставляются по префиксу с id`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobCleanup.kt")
        assertFalse(
            source.contains("thread.karaokeProcess?.description == description"),
            "BR-259: точное совпадение описания не видело CPU-повтор",
        )
        assertTrue(
            source.contains("isStemJobDescription"),
            "BR-259: сопоставление должно быть по префиксу с id задания",
        )
        val processing = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobProcessing.kt")
        assertTrue(
            processing.contains("job.status == StemJobStatus.ERROR"),
            "BR-259: финализация не должна перезаписывать статус задания в ERROR",
        )
    }

    // ---------------------------------------------------------------- BR-260

    @Test
    fun `BR-260 статус WORKING ставится только после успешной вставки шага`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobPollScheduler.kt")
        val workIndex = source.indexOf("job.status = StemJobStatus.WORKING")
        val createIndex = source.indexOf("KaraokeProcess.createChain(separated)")
        assertTrue(workIndex > 0 && createIndex > 0, "BR-260: не найдены строки постановки шага")
        assertTrue(
            createIndex < workIndex,
            "BR-260: WORKING должен ставиться ПОСЛЕ успешной вставки шага",
        )
        assertTrue(
            source.contains("if (createdChainId == null)"),
            "BR-260: результат createChain должен проверяться",
        )
        val processing = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobProcessing.kt")
        assertTrue(
            processing.contains("retryCreated == null"),
            "BR-260: результат постановки CPU-повтора тоже должен проверяться",
        )
    }

    // ---------------------------------------------------------------- BR-261

    @Test
    fun `BR-261 метка времени IAM-токена обновляется после успешного обновления`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt")
        val token = source.substringAfter("fun createNewIamToken()").substringBefore("class IamTokenUnavailableException")
        assertTrue(
            token.contains("Karaoke.requestIamTokenLastTimeMs = LocalDateTime.now().toEpochMillis()"),
            "BR-261: метка времени должна обновляться сразу после успешного обновления токена",
        )
        assertTrue(
            token.contains("IamTokenUnavailableException"),
            "BR-261: ошибка получения токена должна быть отдельным исходом",
        )
    }

    // ---------------------------------------------------------------- BR-262

    @Test
    fun `BR-262 тело запроса собирается через Jackson, а не интерполяцией`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt")
        assertFalse(
            source.contains("\"queryText\": \"\$queryText\""),
            "BR-262: строковая интерполяция ломала JSON при кавычках в названии песни",
        )
        assertTrue(
            source.contains("buildYandexSearchRequestBody"),
            "BR-262: тело должно собираться через Jackson",
        )
    }

    // ---------------------------------------------------------------- BR-263

    @Test
    fun `BR-263 невозможность измерить длительность отличается от превышения лимита`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobPollScheduler.kt")
        assertFalse(
            source.contains("if (durationSeconds == null || durationSeconds > StemJob.MAX_DURATION_SECONDS)"),
            "BR-263: «не удалось измерить» и «превышен лимит» были одной веткой",
        )
        assertTrue(
            source.contains("Не удалось измерить длительность файла"),
            "BR-263: причина должна попадать в errorMessage",
        )
    }

    // ---------------------------------------------------------------- BR-264

    @Test
    fun `BR-264 сбой выборки не выглядит как «пусто»`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt")
        assertTrue(
            source.contains("SyncAudioSelectionException"),
            "BR-264: сбой выборки должен быть различим",
        )
        assertTrue(
            source.contains("adminErrors"),
            "BR-264: в сводке должен быть счётчик ошибок",
        )
        assertTrue(
            source.contains("\"ERROR: \${e.message}\""),
            "BR-264: syncAll должен отдавать ошибку, а не OK",
        )
    }

    // ---------------------------------------------------------------- BR-265

    @Test
    fun `BR-265 сетевой сбой, не-2xx и пустой документ различаются`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt")
        assertTrue(
            source.contains("sealed class HtmlFetchResult"),
            "BR-265: исходы загрузки HTML должны быть различимы",
        )
        assertTrue(
            source.contains("HtmlFetchResult.NetworkError") &&
                source.contains("HtmlFetchResult.HttpError") &&
                source.contains("HtmlFetchResult.EmptyDocument"),
            "BR-265: нужны все три отдельных исхода",
        )
    }

    // ---------------------------------------------------------------- BR-267

    @Test
    fun `BR-267 ymId сравнивается по значению`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertFalse(
            source.contains("author.ymId !== \"\""),
            "BR-267: сравнение строк по ссылке (===) пропускало автора с пустым ym_id",
        )
        assertTrue(
            source.contains("author.ymId != \"\""),
            "BR-267: сравнение должно быть по значению",
        )
    }

    // ---------------------------------------------------------------- BR-268

    @Test
    fun `BR-268 разбиение на слова обрабатывает все слова`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val syllables = source.substringAfter("fun getSyllables").substringBefore("fun convertMillisecondsToTimecode")
        assertFalse(
            syllables.contains("regexWords.find(text)"),
            "BR-268: find() давал только первое слово — слоги остальных терялись",
        )
        assertTrue(
            syllables.contains("findAll"),
            "BR-268: разбиение должно использовать findAll",
        )
    }

    // ---------------------------------------------------------------- BR-269

    @Test
    fun `BR-269 соединение и поток закрываются`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val country = source.substringAfter("private fun fetchCountryCodeFromService").substringBefore("fun resolveCurrentCountryCode")
        assertTrue(
            country.contains("conn.inputStream") && country.contains(".use { stream ->"),
            "BR-269: тело ответа читается в use{}",
        )
        assertTrue(
            country.contains("conn?.disconnect()"),
            "BR-269: соединение закрывается в finally",
        )
    }

    // ---------------------------------------------------------------- BR-270

    @Test
    fun `BR-270 процесс запускается с таймаутом и гарантированно убивается`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val runCommand = source.substringAfter("fun runCommand(").substringBefore("fun runFunctionWithArgs")
        assertFalse(
            runCommand.contains("process.waitFor()"),
            "BR-270: waitFor() без таймаута вешал поток навсегда",
        )
        assertTrue(
            runCommand.contains("waitFor(timeoutMs"),
            "BR-270: нужен waitFor с таймаутом",
        )
        assertTrue(
            runCommand.contains("destroyForcibly()"),
            "BR-270: процесс должен убиваться при таймауте/исключении",
        )
        assertTrue(
            runCommand.contains("MAX_RUN_COMMAND_OUTPUT_CHARS"),
            "BR-270: объём накапливаемого вывода должен быть ограничен",
        )
    }

    // ---------------------------------------------------------------- BR-271

    @Test
    fun `BR-271 скрипт для хоста не склеивает значения и имеет права 700`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val script = source.substringAfter("fun createScriptForHost").substringBefore("private fun setExecutableScriptPermissions")
        assertFalse(
            script.contains("args.joinToString(\" \")"),
            "BR-271: значения склеивались в shell-строку без экранирования",
        )
        assertFalse(
            script.contains("\"chmod\", \"777\""),
            "BR-271: права 777 позволяли перезаписать исполняемый на хосте скрипт",
        )
        assertTrue(
            script.contains("buildHostScript"),
            "BR-271: скрипт должен строиться через безопасный сборщик",
        )
    }

    // ---------------------------------------------------------------- BR-272

    @Test
    fun `BR-272 транспонирование не разыменовывает null`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val transpose = source.substringAfter("fun getTransposingChord").substringBefore("fun isValidFileName")
        assertFalse(
            transpose.contains("note!!") || transpose.contains("chord!!"),
            "BR-272: !! на null из getChordNote давал NPE при capo > 0",
        )
        assertTrue(
            transpose.contains("chord == null || note == null"),
            "BR-272: null должен обрабатываться (возврат исходного значения)",
        )
    }

    // ---------------------------------------------------------------- BR-273

    @Test
    fun `BR-273 кандидаты-родители загружаются пакетно`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertTrue(
            source.contains("loadParentCandidateIndex"),
            "BR-273: кандидаты должны загружаться одним запросом (индекс), а не сканом на песню",
        )
        assertTrue(
            source.contains("class ParentCandidateIndex"),
            "BR-273: нужен индекс «нормализованное название → кандидаты»",
        )
        assertFalse(
            source.contains("\"SELECT id, song_name, song_author FROM tbl_songs WHERE id <> ?\""),
            "BR-273: полный скан таблицы на КАЖДУЮ песню — O(N²)",
        )
    }

    // ---------------------------------------------------------------- BR-274

    @Test
    fun `BR-274 isValid не вызывается на каждый getConnection`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt")
        assertFalse(
            source.contains("conn.isValid(3)"),
            "BR-274: isValid(3) — это отдельный запрос к серверу на каждое обращение",
        )
        assertTrue(
            source.contains("CONNECTION_VALIDATION_IDLE_MS"),
            "BR-274: валидация должна идти по таймеру простоя",
        )
        assertTrue(
            source.contains("markConnectionBroken") || source.contains("threadLocalPoisoned"),
            "BR-274: после SQLException соединение должно помечаться битым",
        )
    }

    // ---------------------------------------------------------------- BR-275

    @Test
    fun `BR-275 в лог уходит само исключение`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt")
        val warn = source.substringAfter("KaraokeConnection connect failure").substringBefore("return threadLocalConnection.get()")
        assertTrue(
            warn.contains("e,"),
            "BR-275: throwable должен передаваться последним аргументом log.warn (SLF4J добавит стек): $warn",
        )
    }
}

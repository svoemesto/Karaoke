package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-26` (аудит #336, OpenProject #398) — КОНТРАКТНЫЕ проверки
 * по исходникам.
 *
 * Эти записи нельзя проверить поведенческим тестом без БД / браузера / MinIO:
 *  - BR-282 (залипший guard ремонта) — нужен `Song` из БД и сбой `recomputeAndBroadcast`;
 *  - BR-285 (блокирующий MinIO в HTTP-треде) — нужен реальный HTTP-клиент и замер
 *    времени потока Tomcat;
 *  - BR-286 (пропуск проверки каталога ссылок) — нужен каталог с битыми правами;
 *  - BR-288 (ложный успех backfill) — нужна недоступная БД;
 *  - BR-293 (сбой Playwright) — нужен браузер и заблокированный профиль.
 *
 * Для них проверяется КОНТРАКТ исходника: требуемый тип/вызов/лог присутствует,
 * а запрещённая конструкция (глотание ошибки, `println` в catch, синхронное
 * выполнение в HTTP-потоке) — отсутствует. Класс компилируется и на старом коде,
 * поэтому служит настоящим доказательством «до/после».
 *
 * @see specs/398-pkg-s3-26/report.md
 */
class PkgS326SourceContractsTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private fun read(relativePath: String): String = repoFile(relativePath).readText()

    /** Исходник БЕЗ комментариев — иначе утверждение «в файле нет X» ловило бы само себя. */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    /**
     * Строгий фрагмент исходника: если маркер не найден — тест ПАДАЕТ (а не молча
     * проверяет весь файл). Это делает контрактные проверки настоящим «до/после».
     */
    private fun section(
        source: String,
        from: String,
        to: String,
    ): String {
        val start = source.indexOf(from)
        assertTrue(start >= 0, "в исходнике не найден фрагмент: $from")
        val end = source.indexOf(to, start + from.length).takeIf { it > 0 } ?: source.length
        return source.substring(start, end)
    }

    private val healthReport = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt"
    private val apiController = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt"

    // ------------------------------------------------------------------ BR-276

    @Test
    fun `BR-276 сеттер voices пишет в ключ voices а не flashColor`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Karaoke.kt")
        assertTrue(
            source.contains("""KaraokeProperties.set("voices"""),
            "сеттер voices должен писать в ключ \"voices\"",
        )
        assertFalse(
            Regex("""set\(value\)\s*\{\s*KaraokeProperties\.set\("flashColor"\s*,\s*getStringFromVoices""")
                .containsMatchIn(source),
            "сеттер voices не должен писать строку голосов в ключ flashColor",
        )
    }

    // ------------------------------------------------------------------ BR-280

    @Test
    fun `BR-280 задания песни загружаются одним запросом а не по одному на тип файла`() {
        val source = readCode(healthReport)
        // Единственная загрузка заданий песни — в getHealthReportList.
        assertTrue(
            source.contains("""val songProcesses = KaraokeProcess.loadList(args = mapOf("song_id" to song.id.toString())"""),
            "задания песни должны загружаться одним запросом в getHealthReportList",
        )
        // В ветках проверки хранилища больше нет собственного loadList.
        assertFalse(
            source.contains("KaraokeProcess\n                    .loadList("),
            "в actionsLocalStorage/actionsRemoteStorage не должно быть собственного loadList (N+1)",
        )
        assertTrue(
            source.contains("filterSongProcesses("),
            "отбор заданий должен идти через filterSongProcesses по уже загруженному списку",
        )
    }

    // ------------------------------------------------------------------ BR-281

    @Test
    fun `BR-281 неизвестность состояния хранилища больше не значит файл актуален`() {
        val source = readCode(healthReport)
        assertFalse(
            source.contains("localInfo == null ||"),
            "null из cachedFileInfo больше не должен трактоваться как «файл актуален»",
        )
        assertTrue(
            source.contains("sealed interface StorageFileInfoProbe"),
            "результат проверки метаданных должен быть типизированным",
        )
        assertTrue(
            source.contains("FileActualityVerdict.UNKNOWN"),
            "должен быть отдельный вердикт «состояние неизвестно»",
        )
    }

    // ------------------------------------------------------------------ BR-282

    @Test
    fun `BR-282 guard ремонта снимается даже если пересчёт упал`() {
        val source = readCode(healthReport)
        val startRepair = section(source, "fun startRepairAll(", "private fun reportRepairFailure")
        assertTrue(
            startRepair.contains("try {"),
            "тело startRepairAll должно быть в try — иначе исключение оставит guard включённым",
        )
        assertTrue(
            Regex("""catch \(e: Exception\) \{\s*exitRepair\(songId\)""").containsMatchIn(startRepair),
            "в catch должен сниматься guard (exitRepair) до пробрасывания исключения",
        )
        assertTrue(
            startRepair.contains("finally {"),
            "в задаче пула guard должен сниматься в finally",
        )
    }

    // ------------------------------------------------------------------ BR-283

    @Test
    fun `BR-283 ошибка удаления из удалённого хранилища не гасится`() {
        val source = readCode(healthReport)
        assertFalse(
            source.contains("Ошибка при удалении неактуального файла из удалённого хранилища"),
            "ошибка удаления больше не должна гаситься в println",
        )
        assertFalse(
            source.contains("Ошибка при удалении файла из удалённого хранилища"),
            "ошибка удаления больше не должна гаситься в println",
        )
        assertTrue(
            source.contains("deleteRemoteFileOrThrow("),
            "удаление должно идти через deleteRemoteFileOrThrow (лог + проброс)",
        )
        assertTrue(
            Regex("""fun deleteRemoteFileOrThrow[\s\S]{0,1200}?throw e""").containsMatchIn(source),
            "deleteRemoteFileOrThrow должен пробрасывать исключение вызывающему",
        )
    }

    // ------------------------------------------------------------------ BR-284

    @Test
    fun `BR-284 каждое действие ремонта выполняется в своём try-catch`() {
        val source = readCode(healthReport)
        val execute = section(source, "internal fun executeResolvable(", "fun startRepairAll(")
        assertTrue(
            execute.contains("catch (e: Exception)"),
            "каждое действие должно выполняться в своём try/catch — иначе каскад обрывается",
        )
        assertTrue(
            execute.contains("failed++"),
            "число неуспешных действий должно считаться и возвращаться",
        )
        assertTrue(
            execute.contains("): Int"),
            "executeResolvable должен возвращать число ошибок",
        )
    }

    // ------------------------------------------------------------------ BR-285

    @Test
    fun `BR-285 одиночное действие отчёта не выполняется в HTTP-потоке`() {
        val source = readCode(apiController)
        assertFalse(
            Regex("""HealthReport\.getHealthReport\(song = song, dto = healthReportDTO\)\?\.executeSolutionActions\(\)""")
                .containsMatchIn(source),
            "эндпоинт не должен выполнять solutionActions синхронно в потоке HTTP-запроса",
        )
        assertTrue(
            source.contains("HealthReport.executeHealthReportActionsAsync("),
            "эндпоинт должен отдавать действие в repairExecutor",
        )
        assertTrue(
            source.contains("ResponseEntity.accepted().build()"),
            "эндпоинт должен отвечать 202 Accepted",
        )
    }

    // ------------------------------------------------------------------ BR-286

    @Test
    fun `BR-286 непрчитанный каталог ссылок даёт WARNING а не тихий пропуск`() {
        val source = readCode(healthReport)
        assertFalse(
            Regex("""println\("symlinkFolderReports""").containsMatchIn(source),
            "пропуск проверки каталога ссылок не должен уходить только в println",
        )
        val symlink = section(source, "private fun symlinkFolderReports(", "private fun reportRepairFailure")
        assertTrue(
            symlink.contains("healthReportStatus = WARNING"),
            "непрочитанный каталог ссылок должен давать WARNING-запись",
        )
        assertTrue(
            symlink.contains("healthLog.warn("),
            "причина сбоя должна логироваться через SLF4J с throwable",
        )
    }

    // ------------------------------------------------------------------ BR-287

    @Test
    fun `BR-287 пересчёт готовности возвращает и число ошибок`() {
        val source = readCode(healthReport)
        assertTrue(
            source.contains("class ReadinessRecalcResult"),
            "должен быть тип итога пересчёта с числом обработанных песен и ошибок",
        )
        assertTrue(
            source.contains("return ReadinessRecalcResult(processed = total - failed, failed = failed)"),
            "пересчёт должен возвращать фактическое число обработанных песен, а не общее",
        )
        assertTrue(
            source.contains("healthLog.error("),
            "ошибка пересчёта должна логироваться через SLF4J с throwable, а не только e.message",
        )
    }

    // ------------------------------------------------------------------ BR-288

    @Test
    fun `BR-288 ошибка выборки строк кеша не выглядит как пустой успех`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt")
        assertFalse(
            source.contains("cache:backfill loadRows failed"),
            "ошибка выборки больше не должна превращаться в пустой список",
        )
        val loader = section(source, "private fun loadRowsNeedingBackfill(", "private fun fetchInfo(")
        assertTrue(
            loader.contains("throw"),
            "ошибка выборки должна пробрасываться вызывающему (SSE error, а не «завершено»)",
        )
        assertFalse(
            Regex("""catch \(e: Exception\)[\s\S]{0,400}?return result""").containsMatchIn(loader),
            "в loadRowsNeedingBackfill не должно быть catch, превращающего сбой в пустой список",
        )
    }

    // ------------------------------------------------------------------ BR-289

    @Test
    fun `BR-289 значение словаря экранируется перед подстановкой в шаблон`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt")
        assertTrue(
            source.contains("Regex.escape(uncensored)"),
            "значение словаря обязано экранироваться перед подстановкой в regex",
        )
        assertFalse(
            source.contains("\$uncensored(?![\\p{L}\\p{N}_])"),
            "сырое значение словаря не должно попадать в шаблон regex",
        )
    }

    // ------------------------------------------------------------------ BR-290

    @Test
    fun `BR-290 деление на максимальную длительность защищено от нуля`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Functions.kt")
        val unguarded =
            source
                .lines()
                .filter { it.contains("/ maxDuration") && !it.contains("if (maxDuration > 0)") }
        assertTrue(
            unguarded.isEmpty(),
            "деление на maxDuration без проверки должно быть убрано: $unguarded",
        )
        assertTrue(
            source.contains("if (maxDuration > 0)"),
            "деление должно выполняться только при maxDuration > 0",
        )
    }

    // ------------------------------------------------------------------ BR-291

    @Test
    fun `BR-291 ошибки крипты логируются через SLF4J а не в stdout`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Crypto.kt")
        assertFalse(
            source.contains("printStackTrace"),
            "ошибки шифрования не должны уходить в stdout через printStackTrace",
        )
        assertTrue(
            source.contains("log.error(\"crypto: decrypt failed op=decrypt"),
            "ошибка расшифрования должна логироваться через SLF4J с типом операции",
        )
        assertTrue(
            source.contains("log.error(\"crypto: encrypt failed op=encrypt"),
            "ошибка шифрования должна логироваться через SLF4J с типом операции",
        )
        assertTrue(
            source.contains("fun validateKeyMaterial("),
            "длина ключа и IV должна проверяться явно",
        )
    }

    // ------------------------------------------------------------------ BR-292

    @Test
    fun `BR-292 разбор настроек голосов проверяет размер после split`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Converter.kt")
        assertFalse(
            source.contains("val partVoiceFieldFill = partsVoiceFields[1]"),
            "индекс partsVoiceFields[1] без проверки должен быть убран",
        )
        assertFalse(
            source.contains("val partValue = parts[1]"),
            "индекс parts[1] без проверки должен быть убран",
        )
        assertTrue(
            source.contains("getOrNull(1)"),
            "после split должна быть проверка размера (getOrNull)",
        )
        assertTrue(
            source.contains("safeColor("),
            "Color должен строиться в try с fallback на дефолт",
        )
    }

    // ------------------------------------------------------------------ BR-293

    @Test
    fun `BR-293 сбой Яндекс-ветки приводит к NotFound а не к 500`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt")
        val finder = section(source, "fun findYandexAlbumCovers(", "fun downloadImageBytes(")
        assertTrue(
            finder.contains("catch (e: Exception)"),
            "Яндекс-ветка должна быть обёрнута в try/catch",
        )
        assertTrue(
            finder.contains("AlbumCoverSearchOutcome.NotFound("),
            "при сбое должен возвращаться NotFound — тогда работает фолбэк (SearXNG/fourget)",
        )
    }

    // ------------------------------------------------------------------ BR-294

    @Test
    fun `BR-294 пакетное пересканирование читает словарь один раз`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        val rescan = section(source, "fun rescanAllCensoredNames(", "fun isCensoredRescanInProgressReset")
        assertTrue(
            rescan.contains("CensoredDictionaryRules.load("),
            "словарь должен читаться один раз на пакет",
        )
        assertFalse(
            rescan.contains("songName.censored(WORKING_DATABASE)"),
            "в цикле по песням словарь больше не должен читаться заново",
        )
    }
}

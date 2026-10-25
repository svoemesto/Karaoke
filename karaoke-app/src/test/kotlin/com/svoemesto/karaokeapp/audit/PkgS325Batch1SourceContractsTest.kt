package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.controllers.isXhr
import com.svoemesto.karaokeapp.controllers.parseOptionalInt
import com.svoemesto.karaokeapp.mlt.mko.PRODUCER_TYPES_WITHOUT_PRODUCER
import com.svoemesto.karaokeapp.mlt.mko.emptyKdenliveTitle
import com.svoemesto.karaokeapp.mlt.mko.parseStringLad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.server.ResponseStatusException
import java.io.File

/**
 * Регрессия пакета `PKG-S3-25` (аудит #336, OpenProject #397), партия 1 —
 * КОНТРАКТНЫЕ проверки по исходникам и чистым функциям.
 *
 * Класс компилируется и на коде «до» правки (кроме новых чистых функций — они
 * добавлены вместе с правкой), поэтому поведенческие проверки служат настоящим
 * доказательством «до/после»: на старом коде каждое утверждение падает, на
 * текущем — проходит.
 *
 * @see specs/397-pkg-s3-25/report.md
 */
class PkgS325Batch1SourceContractsTest {
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

    private fun repoDir(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isDirectory, "каталог не найден: $relativePath")
        return file
    }

    /**
     * Исходник БЕЗ комментариев — иначе утверждение вида «в файле нет `X`» ловило бы
     * само себя в тексте пояснения к правке.
     */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    // ---------------------------------------------------------------- BR-228

    @Test
    fun `BR-228 загрузка страницы ограничена по размеру тела`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/LyricsFinderService.kt")
        assertTrue(
            source.contains("maxBodySize(MAX_PAGE_BODY_BYTES)"),
            "BR-228: jsoup должен ограничивать размер тела ответа (maxBodySize)",
        )
        assertTrue(
            source.contains("MAX_PAGE_BODY_BYTES = 2 * 1024 * 1024"),
            "BR-228: лимит тела должен быть объявлен константой (2 МБ)",
        )
    }

    // ---------------------------------------------------------------- BR-229

    @Test
    fun `BR-229 SearXNG-путь проходит тот же post-filter, что и fourget`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt")
        val searxng = source.substringAfter("fun searchUrlsViaSearxng").substringBefore("private fun searchUrlsViaScraper")
        assertTrue(
            searxng.contains("filterUselessLyricsUrls(urls)"),
            "BR-229: SearXNG-путь должен фильтровать URL тем же фильтром, что и fourget",
        )
    }

    // ---------------------------------------------------------------- BR-230

    @Test
    fun `BR-230 UUID узлов MLT берутся из кеша, а не по одному SELECT на узел`() {
        // Реализацию дал пакет PKG-S3-24 (BR-197, слит раньше): кеш таблицы
        // tbl_uuids в model/Uuids.kt (loadAll/cachedUuid/putCached). Этот пакет
        // принёс вторую, параллельную реализацию того же исправления — при слиянии
        // оставлена одна (из PKG-S3-24), поэтому проверка здесь — на общий смысл
        // (кеш и запрет точечного SELECT), а не на конкретные имена API.
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Uuids.kt")
        val model = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Uuids.kt")
        assertTrue(
            model.contains("loadAll") && model.contains("cachedUuid"),
            "BR-230: нужен кеш UUID структурных узлов MLT (загрузка таблицы одним запросом)",
        )
        assertFalse(
            source.contains("Uuids.load(id, WORKING_DATABASE)?.uuid ?: \"\""),
            "BR-230: точечный SELECT на каждый узел должен быть заменён кешем",
        )
    }

    @Test
    fun `BR-230 вызовы UUID в цикле генерации идут через кеш`() {
        val mlt = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/Mlt.kt")
        assertTrue(
            mlt.contains("getStoredUuid("),
            "BR-230: Mlt.getMisList берёт UUID через getStoredUuid — функция должна быть кешированной",
        )
        // Реализация кеша — из PKG-S3-24 (BR-197): таблица tbl_uuids читается
        // одним запросом (Uuids.loadAll), а getStoredUuid идёт через кеш
        // (Uuids.cachedUuid), а не точечным SELECT на каждый узел.
        val uuids = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Uuids.kt")
        assertTrue(
            uuids.contains("Uuids.cachedUuid("),
            "BR-230: getStoredUuid должен идти через кеш, а не делать SELECT на каждый узел",
        )
        assertFalse(
            uuids.contains("uuidCache.get(storedUuidId(key))"),
            "BR-230: реализация из PKG-S3-24 заменила дублирующий UuidCache",
        )
    }

    // ---------------------------------------------------------------- BR-231

    @Test
    fun `BR-231 числовые параметры process update разбираются безопасно`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        assertFalse(
            source.contains("process_order.toInt()"),
            "BR-231: process_order больше не разбирается через toInt() без проверки",
        )
        assertFalse(
            source.contains("process_priority.toInt()"),
            "BR-231: process_priority больше не разбирается через toInt() без проверки",
        )
        assertFalse(
            source.contains("val processId: Long = id.toLong()"),
            "BR-231: id больше не разбирается через toLong() без проверки",
        )
        assertTrue(
            source.contains("bad_process_order"),
            "BR-231: нечисловой order должен отклоняться с 400 и причиной",
        )
        assertTrue(
            source.contains("bad_process_priority"),
            "BR-231: нечисловой priority должен отклоняться с 400 и причиной",
        )
        assertTrue(
            source.contains("KaraokeProcessStatuses.entries.none { it.name == value }"),
            "BR-231: process_status должен проверяться по KaraokeProcessStatuses",
        )
    }

    @Test
    fun `BR-231 пустое числовое поле означает не менять значение`() {
        assertNull(parseOptionalInt(null, "bad_process_order"))
        assertNull(parseOptionalInt("", "bad_process_order"))
        assertNull(parseOptionalInt("   ", "bad_process_order"))
        assertEquals(7, parseOptionalInt("7", "bad_process_order"))
        assertEquals(-3, parseOptionalInt(" -3 ", "bad_process_order"))
    }

    @Test
    fun `BR-231 нечисловое значение даёт 400 с причиной, а не 500`() {
        val error =
            assertThrows<ResponseStatusException> {
                parseOptionalInt("abc", "bad_process_order")
            }
        assertEquals(400, error.statusCode.value())
        assertEquals("bad_process_order", error.reason)
    }

    // ---------------------------------------------------------------- BR-232

    @Test
    fun `BR-232 XHR получает короткий JSON, а не полный рендер процессов`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        assertFalse(
            source.contains("return \"redirect:/processes\""),
            "BR-232: /process/update больше не отвечает редиректом на полную страницу /processes",
        )
        assertTrue(
            source.contains("mapOf(\"ok\" to true)"),
            "BR-232: XHR должен получать короткий JSON {ok:true}",
        )
        assertTrue(
            source.contains("isXhr(request)"),
            "BR-232: ответ должен зависеть от типа запроса (XHR или обычный)",
        )
    }

    @Test
    fun `BR-232 XHR определяется по заголовку X-Requested-With`() {
        val request =
            MockHttpServletRequest().apply {
                addHeader("X-Requested-With", "XMLHttpRequest")
            }
        assertTrue(isXhr(request), "BR-232: запрос с X-Requested-With: XMLHttpRequest — это XHR")

        val plain = MockHttpServletRequest()
        assertFalse(isXhr(plain), "BR-232: запрос без X-Requested-With — не XHR")
    }

    // ---------------------------------------------------------------- BR-233

    @Test
    fun `BR-233 Telegram-заголовки используют getDescriptionTelegramHeader`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        val telegramBlock =
            source.substringAfter("fun getSongTextTelegramLyricsHeader").substringBefore("fun getSongTextTelegramLyricsWOHeader")
        assertFalse(
            telegramBlock.contains("getDescriptionVkHeader"),
            "BR-233: Telegram-заголовки не должны собираться через getDescriptionVkHeader",
        )
        assertTrue(
            telegramBlock.contains("getDescriptionTelegramHeader"),
            "BR-233: Telegram-заголовки должны собираться через getDescriptionTelegramHeader",
        )
        assertEquals(
            3,
            Regex("getDescriptionTelegramHeader").findAll(telegramBlock).count(),
            "BR-233: все три Telegram-заголовка (lyrics/karaoke/chords) должны использовать Telegram-вариант",
        )
    }

    // ---------------------------------------------------------------- BR-236

    @Test
    fun `BR-236 ошибка разбора маркеров логируется со стеком и различает коды`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        assertFalse(
            source.contains("println(\"Ошибка при парсинге маркеров для песни \$id"),
            "BR-236: ошибка разбора маркеров не должна уходить в println без стека",
        )
        assertTrue(
            source.contains("bad_source_markers"),
            "BR-236: невалидный JSON маркеров должен давать 400 с причиной",
        )
        assertTrue(
            source.contains("song_not_found"),
            "BR-236: отсутствующая песня должна давать 404, а не 200 с текстом Error",
        )
        assertTrue(
            source.contains("log.warn(\"Ошибка при парсинге маркеров для песни \$id (голос \$voice)\", e)"),
            "BR-236: исключение должно логироваться через SLF4J со стеком и songId/voice",
        )
    }

    // ---------------------------------------------------------------- BR-238

    @Test
    fun `BR-238 сравнение строк по значению, а не по ссылке`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoSplashStart.kt")
        assertFalse(
            source.contains("cds !== \"\""),
            "BR-238: cds !== \"\" — сравнение по ссылке, всегда истинное",
        )
        assertTrue(
            source.contains("cds.isNotBlank()"),
            "BR-238: проверка непустоты описания аккордов должна быть по значению",
        )
    }

    // ---------------------------------------------------------------- BR-239

    @Test
    fun `BR-239 строка табулятуры разбирается только по валидному формату`() {
        val parsed = parseStringLad("3|12")
        assertNotNull(parsed)
        assertEquals(3, parsed!!.first)
        assertEquals("12", parsed.second)

        assertNull(parseStringLad("undefined|undefined"), "BR-239: undefined|undefined не должен разбираться")
        assertNull(parseStringLad("7|3"), "BR-239: строка вне 0..5 не должна разбираться")
        assertNull(parseStringLad("3"), "BR-239: без разделителя не разбирается")
        assertNull(parseStringLad("3|"), "BR-239: пустой лад не разбирается")
        assertNull(parseStringLad("|3"), "BR-239: пустая строка не разбирается")
        assertNull(parseStringLad("a|3"), "BR-239: нечисловая строка не разбирается")
    }

    @Test
    fun `BR-239 невалидный stringLad не роняет генерацию mlt`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMelodyTabs.kt")
        assertFalse(
            source.contains("val stringIndex = stringText.toInt()"),
            "BR-239: разбор stringLad без проверки бросает NumberFormatException и роняет весь .mlt",
        )
        assertTrue(
            source.contains("parseStringLad(stringLadText)"),
            "BR-239: разбор stringLad должен идти через проверяющую функцию",
        )
    }

    // ---------------------------------------------------------------- BR-240

    @Test
    fun `BR-240 сбой БД при загрузке альбомов не выглядит как пустой список`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt")
        assertFalse(
            source.contains("e.printStackTrace()"),
            "BR-240: ошибка выборки альбомов не должна глотаться через printStackTrace",
        )
        assertTrue(
            source.contains("AlbumTilesUnavailableException"),
            "BR-240: сбой выборки альбомов должен быть различимым (AlbumTilesUnavailableException)",
        )
        val web = readCode("karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt")
        assertTrue(
            web.contains("HttpStatus.SERVICE_UNAVAILABLE"),
            "BR-240: публичный эндпоинт должен отдавать 503 при сбое БД",
        )
    }

    // ---------------------------------------------------------------- BR-241

    @Test
    fun `BR-241 отсутствие элемента не превращается в nameless-узел`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMelodyTabs.kt")
        assertFalse(
            source.contains("return MltNode()"),
            "BR-241: MltNode() — узел с пустым именем, его toString() даёт не-well-formed XML",
        )
        assertTrue(
            source.contains("emptyKdenliveTitle()"),
            "BR-241: вместо nameless-узла должен возвращаться валидный пустой kdenlivetitle",
        )
    }

    @Test
    fun `BR-241 пустой kdenlivetitle — валидный XML с именем элемента`() {
        val node = emptyKdenliveTitle()
        assertEquals("kdenlivetitle", node.name)
        val xml = node.toString()
        assertTrue(
            xml.startsWith("<kdenlivetitle"),
            "BR-241: узел должен начинаться с открывающего тега, а не с '< />': $xml",
        )
        assertTrue(xml.contains("</kdenlivetitle>"), "BR-241: узел должен быть закрыт: $xml")
    }

    // ---------------------------------------------------------------- BR-242

    @Test
    fun `BR-242 битых ссылок на несуществующие producer в main_bin нет`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMainBin.kt")
        assertTrue(
            source.contains("PRODUCER_TYPES_WITHOUT_PRODUCER"),
            "BR-242: типы без producer() должны быть перечислены явно",
        )
        assertTrue(
            source.contains("MltGenerator.nameTractor("),
            "BR-242: для типов без producer() ссылка должна вести на существующий трактор",
        )
    }

    @Test
    fun `BR-242 список типов без producer совпадает с классами mko`() {
        // Каждый тип из списка обязан реально не иметь override fun producer() в своём классе.
        val mkoDir = repoDir("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko")
        val classesWithProducer =
            mkoDir
                .listFiles { f -> f.isFile && f.name.endsWith(".kt") }
                .orEmpty()
                .filter { it.readText().contains("override fun producer(") }
                .map { it.name.removeSuffix(".kt") }
                .toSet()

        val expected =
            mapOf(
                "MkoLineTrack" to com.svoemesto.karaokeapp.model.ProducerType.LINETRACK,
                "MkoChordPictureLineTrack" to com.svoemesto.karaokeapp.model.ProducerType.CHORDPICTURELINETRACK,
                "MkoScrollerTrack" to com.svoemesto.karaokeapp.model.ProducerType.SCROLLERTRACK,
            )
        expected.forEach { (className, type) ->
            assertFalse(
                classesWithProducer.contains(className),
                "BR-242: $className переопределяет producer() — тип $type не должен быть в " +
                    "PRODUCER_TYPES_WITHOUT_PRODUCER",
            )
            assertTrue(
                PRODUCER_TYPES_WITHOUT_PRODUCER.contains(type),
                "BR-242: тип $type ($className) без producer() обязан быть в PRODUCER_TYPES_WITHOUT_PRODUCER",
            )
        }
        assertEquals(
            expected.size,
            PRODUCER_TYPES_WITHOUT_PRODUCER.size,
            "BR-242: список типов без producer() разошёлся с классами mko",
        )
    }

    // ---------------------------------------------------------------- BR-243

    @Test
    fun `BR-243 конфигурационные списки счётчика индексируются с проверкой`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoCounter.kt")
        assertFalse(
            source.contains("Karaoke.voices[0]"),
            "BR-243: Karaoke.voices[0] падает на пустом списке голосов",
        )
        assertFalse(
            source.contains("Karaoke.countersColors[childId]"),
            "BR-243: Karaoke.countersColors[childId] падает на коротком списке цветов",
        )
        assertTrue(
            source.contains("colors.getOrNull(childId)"),
            "BR-243: цвет счётчика должен выбираться с проверкой размера списка",
        )
    }
}

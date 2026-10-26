package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-contract-тесты пакета PKG-S2-12 (#375): фиксируют инварианты, которые нельзя
 * проверить юнит-вызовом без БД/Spring (контроллеры, шаблоны, сквозные потоки).
 *
 * Они компилируются и на коде «до» правки, поэтому служат доказательством «до/после»:
 * на старом коде падают на утверждении, на новом — проходят.
 */
class PkgS212SourceContractsTest {
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

    private val mainController = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt"
    private val utils = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt"

    @Test
    fun `BR-188 условие выхода раскладки публикаций читает живое состояние стопок`() {
        val publication = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt")
        assertTrue(publication.contains("while (stacks.any { it.isNotEmpty() })"))
        assertFalse(publication.contains("while (it > 0)"), "захваченный максимум размера — вечный цикл")
    }

    @Test
    fun `BR-195 ключ кеша статистики строится с параметрами запроса`() {
        val controller = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt")
        assertTrue(controller.contains("StatsCacheKey.forEndpoint("))
        assertTrue(controller.contains("\"days\" to days.toString()"))
        assertTrue(controller.contains("\"mode\" to mode"))
        assertFalse(controller.contains("StatsCacheKey(endpoint, emptyMap())"))
    }

    @Test
    fun `BR-200 страница публикаций режется постранично`() {
        val controller = read(mainController)
        assertTrue(controller.contains("pageSlice(allPublications"))
        assertTrue(controller.contains("hasNextPage(allPublications"))
        val template = read("karaoke-app/src/main/resources/templates/publications.html")
        assertTrue(template.contains("id=\"areaPager\""))
        assertTrue(template.contains("filter_page"))
    }

    @Test
    fun `BR-201 в шаблонах karaoke-app нет мёртвого STOMP, статус процессов идёт опросом`() {
        val processes = read("karaoke-app/src/main/resources/templates/processes.html")
        assertTrue(processes.contains("/process/lastupdated"), "polling вместо несуществующего брокера")
        assertFalse(processes.contains("SockJS("))
        assertFalse(processes.contains("stomp.js"))
        for (template in listOf("publications.html", "unpublications.html")) {
            val text = read("karaoke-app/src/main/resources/templates/$template")
            assertFalse(text.contains("SockJS("), "$template: брокера в karaoke-app нет")
        }
    }

    @Test
    fun `BR-205 и BR-213 отсутствующие поля формы не роняют сохранение`() {
        val controller = read(mainController)
        assertTrue(controller.contains("song_idBoostyFiles: String?,"))
        assertTrue(controller.contains("song_resultVersion: String?,"))
        assertTrue(controller.contains("song_resultVersion?.let { songValue.fields[SongField.RESULT_VERSION] = it }"))
        assertTrue(controller.contains("song_idBoostyFiles?.let { songValue.fields[SongField.ID_BOOSTY_FILES] = it }"))
        assertFalse(controller.contains("songValue.fields[SongField.RESULT_VERSION] = song_resultVersion"))
    }

    @Test
    fun `BR-206 и BR-214 списки песен грузятся страницей, а не целиком`() {
        // Легаси-страницы песен (`/songs`, `/songs2`) удалены 2026-10-26: ноль
        // обращений за месяц в прод-логе, возможности перенесены в SPA. Современный
        // контракт пагинации — API-списки: limit задаётся фильтром и валидируется.
        val apiController =
            read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")
        assertTrue(apiController.contains("args[\"filter_limit\"] = parsed.toString()"))
        assertTrue(apiController.contains("Некорректный filterLimit"))
        // Страницы не должны вернуться: они мертвы и тянут за собой шаблоны.
        val controller = read(mainController)
        assertFalse(controller.contains("@GetMapping(\"/songs\")"))
        assertFalse(controller.contains("@GetMapping(\"/songs2\")"))
        assertFalse(controller.contains("@PostMapping(\"/songs2_update\")"))
    }

    @Test
    fun `BR-219 тарифы проверяются до записи, default снимается в транзакции`() {
        val controller = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/TariffsController.kt")
        assertTrue(controller.contains("rejectInvalidTariff("))
        assertTrue(controller.contains("SET is_default = false WHERE scope = ? AND id <> ?"))
        assertTrue(controller.contains("connection.autoCommit = false"))
        val tariff = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/PriceTariff.kt")
        assertTrue(tariff.contains("minWithOrNull(compareByDescending<PriceTariff> { it.isDefault }"))
        assertFalse(tariff.contains("firstOrNull { it.isDefault }"))
    }

    @Test
    fun `BR-227 промпт отделяет данные страницы от инструкций`() {
        val agent = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/ScraperAgent.kt")
        assertTrue(agent.contains("PAGE_DATA_START"))
        assertTrue(agent.contains("UNTRUSTED DATA"))
        assertTrue(agent.contains("buildLyricsUserMessage("))
        val finder = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/LyricsFinderService.kt")
        assertTrue(finder.contains("extractLyrics(pageText, artist = artist, songTitle = songTitle)"))
    }

    @Test
    fun `BR-234 обновление хранилища передаёт реальный список и лейн тяжёлого рендера`() {
        val controller = read(mainController)
        val start = controller.indexOf("fun doCollectStore(): List<Int>")
        assertTrue(start >= 0, "doCollectStore обязан возвращать счётчики, а не Boolean")
        val body = controller.substring(start, controller.indexOf("@PostMapping(\"/utils/censored\")", start))
        assertTrue(body.contains("Song.loadListFromDb("), "пустой список — операция-пустышка")
        assertTrue(body.contains("KaraokeProcess.THREAD_LANE_HEAVY_RENDER"))
        assertFalse(body.contains("emptyList()"))
        assertFalse(body.contains("threadId = 1"))
    }

    @Test
    fun `BR-235 оба сохранения текста и маркеров проверяют voice до паддинга`() {
        val controller = read(mainController)
        assertEqualsCount(2, "isValidVoiceIndex(voice = voice, countVoices = song.countVoices)", controller)
    }

    @Test
    fun `BR-244 файл настроек пишется под блокировкой и с уникальным tmp`() {
        val properties = read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertTrue(properties.contains("propertiesWriteLock.withLock"))
        assertTrue(properties.contains("Files.createTempFile(dir.toPath()"))
        assertFalse(properties.contains("File(dir, \"${'$'}{target.name}.tmp\")"))
    }

    @Test
    fun `BR-251 синхронизация проверяет подтверждение цели и не чистит источник при отказе`() {
        val utilsText = read(utils)
        assertTrue(utilsText.contains("postChunksConfirming("))
        assertTrue(utilsText.contains("if (!targetConfirmed)"))
        assertTrue(utilsText.contains("error = true"))
        assertTrue(utilsText.contains("SyncRemoteClient.postChangeRecords(requestBody)"))
        assertTrue(
            read("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")
                .contains("sync_target_not_confirmed"),
        )
    }

    @Test
    fun `BR-266 выбор автора — один проход без рекурсии`() {
        val utilsText = read(utils)
        assertTrue(utilsText.contains("authorRequestOrder("))
        assertFalse(utilsText.contains("return getAuthorForRequest(authorForRequest)"))
        assertTrue(utilsText.contains("author.ymId != \"\""))
        assertFalse(utilsText.contains("author.ymId !== \"\""))
    }

    private fun assertEqualsCount(
        expected: Int,
        needle: String,
        haystack: String,
    ) {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        assertTrue(count == expected, "ожидалось $expected вхождений '$needle', найдено $count")
    }
}

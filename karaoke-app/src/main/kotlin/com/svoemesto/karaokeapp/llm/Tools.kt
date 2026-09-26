package com.svoemesto.karaokeapp.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.KaraokeProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Класс Search Tool. Ищет URL с текстами песен через self-hosted мета-поисковик
 * fourget (`/api/v1/web`). Скрапперы (движки-источники fourget) перебираются по
 * порядку из `lyricsSearchScrapers`, пока очередной не пройдёт post-filter и
 * порог `lyricsSearchMinResults`.
 *
 * Порядок по умолчанию — `google_cse;yahoo_japan;brave;yep` (проверен на
 * admin-машине 2026-09-26). Он менялся дважды по факту деградации источников:
 * `yandex` (исходный движок fourget) капчится/блокируется с самого начала
 * (specs/014-lyrics-search-replacement/research.md, раздел "Production finding");
 * 2026-09-02 основным сделали `yep`, а `brave` — фолбэком
 * (specs/294-fourget-scraper-order/spec.md); 2026-09-26 оба деградировали
 * (`brave` — `did not return a result object`, `yep` — пустая выдача либо
 * 100 нерелевантных URL, обходящих порог по количеству), и основными стали
 * `google_cse` и `yahoo_japan`.
 *
 * Важно: `status: "ok"` от fourget НЕ означает успех — скраппер умеет
 * деградировать «тихо» (пустая выдача при ok). Признак успеха — только
 * непустой результат, прошедший post-filter.
 *
 * Список scrapers и порог «качества» настраиваются через `KaraokeProperties`:
 * - `lyricsSearchScrapers` (String, дефолт
 *   `"google_cse;yahoo_japan;brave;yep"`) — порядок через `;`.
 * - `lyricsSearchMinResults` (Int, дефолт `2`) — минимальное число URL после
 *   post-filter, ниже — fallback на следующий scraper.
 * - `lyricsSearchUselessUrlPatterns` (String через `;`) — паттерны для
 *   post-filter «мусорных» URL (homepage, sitemap, login-страницы, файлы,
 *   tracking-маркеры).
 *
 * Hot-fix при очередной блокировке — через UI «Свойства» (без передеплоя):
 * `KaraokeProperties.get*` читает значение на каждый запрос.
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
@Component
class SearchTool(
    @Value("\${lyrics-search.base-url:http://fourget:80}")
    private val lyricsSearchBaseUrl: String,
    @Value("\${searxng.base-url:http://searxng:8080}")
    private val searxngBaseUrl: String,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(SearchTool::class.java)

    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    fun searchUrls(query: String): List<String> {
        // Нижняя граница 1, а не 0: при пороге 0 пустой ответ scraper'а проходил бы
        // проверку `urls.size >= minResults` и перебор останавливался бы на первом же
        // scraper'е — failover не срабатывал бы вовсе.
        val minResults = KaraokeProperties.getInt("lyricsSearchMinResults").coerceAtLeast(1)
        for (scraper in lyricsSearchScrapersList()) {
            val urls = searchUrlsViaScraper(query, scraper)
            if (urls.size >= minResults) return urls
        }
        return emptyList()
    }

    /**
     * Прямой поиск URL с текстами песен через self-hosted SearXNG (`searxng.base-url`) —
     * движок `SEARXNG` в [com.svoemesto.karaokeapp.LyricsSearchEngine]
     * (specs/015-search-engine-selection). То же, что делал [searchUrls] до фичи
     * 014-lyrics-search-replacement, но как отдельный, явно называемый метод — теперь
     * `searchUrls` (fourget) не единственная реализация.
     *
     * @see archive/docs/features/llm-lyrics-search.md
     */
    fun searchUrlsViaSearxng(query: String): List<String> =
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$searxngBaseUrl/search?q=$encodedQuery&format=json&language=ru"

            logger.info("🔍 [SearchTool] Запрос к SearXNG: $url")

            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error("❌ [SearchTool] SearXNG вернул статус ${response.statusCode()}")
                emptyList()
            } else {
                val searchResponse = objectMapper.readValue(response.body(), SearxngTextSearchResponse::class.java)
                val urls = searchResponse.results.map { it.url }.filter { it.isNotBlank() }

                logger.info("✅ [SearchTool] SearXNG — найдено URL: ${urls.size}")
                urls.forEach { logger.info("  → $it") }

                urls
            }
        } catch (e: Exception) {
            logger.error("❌ [SearchTool] Ошибка SearXNG: ${e.message}", e)
            emptyList()
        }

    private fun searchUrlsViaScraper(
        query: String,
        scraper: String,
    ): List<String> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$lyricsSearchBaseUrl/api/v1/web?s=$encodedQuery&scraper=$scraper"

            logger.info("🔍 [SearchTool] Запрос к fourget (scraper=$scraper): $url")

            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error("❌ [SearchTool] fourget (scraper=$scraper) вернул статус ${response.statusCode()}")
                return emptyList()
            }

            val searchResponse = objectMapper.readValue(response.body(), LyricsSearchResponse::class.java)
            if (searchResponse.status != "ok") {
                logger.error("❌ [SearchTool] fourget (scraper=$scraper) вернул status='${searchResponse.status}'")
                return emptyList()
            }

            val urls = searchResponse.web.map { it.url }.filter { it.isNotBlank() }

            logger.info("✅ [SearchTool] scraper=$scraper — найдено URL: ${urls.size}")

            val filteredUrls = filterUselessLyricsUrls(urls)
            logger.info("🔧 [SearchTool] post-filter: было ${urls.size}, осталось ${filteredUrls.size} (отброшено ${urls.size - filteredUrls.size})")

            filteredUrls.forEach { logger.info("  → $it") }

            filteredUrls
        } catch (e: Exception) {
            logger.error("❌ [SearchTool] Ошибка (scraper=$scraper): ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        /**
         * Fallback-порядок scrapers, если `lyricsSearchScrapers` не задано или состоит
         * только из пустых токенов.
         *
         * ВНИМАНИЕ: это вторая копия дефолта — первая объявлена как `defaultValue`
         * одноимённого [com.svoemesto.karaokeapp.KaraokeProperty] в
         * `KaraokeProperties.kt`. Списки обязаны совпадать; расхождение ловится
         * тестом `ToolsTest.дефолт scrapers в Tools совпадает с дефолтом в KaraokeProperties`.
         *
         * `internal` — виден из unit-тестов в том же модуле.
         */
        internal val DEFAULT_LYRICS_SEARCH_SCRAPERS = listOf("google_cse", "yahoo_japan", "brave", "yep")

        /**
         * Возвращает список scrapers для lyrics-поиска из [com.svoemesto.karaokeapp.KaraokeProperties].
         * Парсит `lyricsSearchScrapers` (String через `;`), фильтрует пустые токены.
         * Если в результате пустой список — fallback на [DEFAULT_LYRICS_SEARCH_SCRAPERS].
         */
        private fun lyricsSearchScrapersList(): List<String> =
            KaraokeProperties
                .getString("lyricsSearchScrapers")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { DEFAULT_LYRICS_SEARCH_SCRAPERS }
    }
}

/**
 * Класс Lyrics Search Response — ответ fourget (`/api/v1/web`).
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LyricsSearchResponse(
    val status: String = "",
    val web: List<LyricsSearchResult> = emptyList(),
)

/**
 * Класс Lyrics Search Result — один результат веб-поиска fourget.
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LyricsSearchResult(
    val url: String = "",
    val title: String = "",
    val description: String = "",
)

/**
 * Класс Searxng Text Search Response — ответ SearXNG (`/search?format=json`) для
 * прямого текстового поиска (движок `SEARXNG`, см. [SearchTool.searchUrlsViaSearxng]).
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SearxngTextSearchResponse(
    val results: List<SearxngTextSearchResult> = emptyList(),
)

/**
 * Класс Searxng Text Search Result — один результат текстового поиска SearXNG.
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SearxngTextSearchResult(
    val url: String = "",
    val title: String = "",
    val content: String = "",
)

/**
 * Стартовый набор паттернов для post-filter «мусорных» URL — копия дефолта
 * `KaraokeProperties.lyricsSearchUselessUrlPatterns`. Используется как
 * defensive fallback, если в настройках пусто/не задано.
 *
 * Формат — список substring'ов, каждый проверяется case-insensitive в URL.
 * Покрывает:
 * - Служебные path: /login, /signup, /auth, /wp-login.php, /wp-admin,
 *   /administrator, /sitemap.xml, /sitemap, /sitemap_index.xml, /robots.txt,
 *   /feed, /rss, /rss.xml, /atom.xml, /search.
 * - Расширения файлов (не HTML-страницы): .pdf, .doc, .docx, .xls, .xlsx,
 *   .zip, .rar, .7z, .tar, .gz, .mp3, .mp4, .wav, .avi, .mov, .jpg, .jpeg,
 *   .png, .gif, .webp, .svg.
 * - Tracking-маркеры в query: utm_source=, utm_medium=, utm_campaign=,
 *   utm_term=, utm_content=, fbclid=, gclid=, yclid=, msclkid=, _ga=, ref=.
 *
 * @see specs/294-fourget-scraper-order/spec.md (FR-004)
 */
private val DEFAULT_USELESS_URL_PATTERNS: List<String> =
    listOf(
        "/login", "/signup", "/register", "/auth", "/wp-login.php",
        "/wp-admin", "/administrator", "/sitemap.xml", "/sitemap",
        "/sitemap_index.xml", "/robots.txt", "/feed", "/rss",
        "/rss.xml", "/atom.xml", "/search",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".rar",
        ".7z", ".tar", ".gz", ".mp3", ".mp4", ".wav", ".avi",
        ".mov", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
        "utm_source=", "utm_medium=", "utm_campaign=", "utm_term=",
        "utm_content=", "fbclid=", "gclid=", "yclid=", "msclkid=",
        "_ga=", "ref=",
    )

/**
 * Возвращает паттерны для post-filter из
 * [com.svoemesto.karaokeapp.KaraokeProperties]. Парсит
 * `lyricsSearchUselessUrlPatterns` (String через `;`). Если в результате
 * пустой список — fallback на [DEFAULT_USELESS_URL_PATTERNS].
 *
 * `internal` — виден из unit-тестов в
 * `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/`.
 *
 * @see specs/294-fourget-scraper-order/spec.md (FR-007)
 */
internal fun uselessUrlPatternsList(): List<String> =
    KaraokeProperties
        .getString("lyricsSearchUselessUrlPatterns")
        .split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { DEFAULT_USELESS_URL_PATTERNS }

/**
 * Post-filter «мусорных» URL для lyrics-поиска. Чистая функция — без HTTP,
 * без логирования, без side effects. Используется в [SearchTool.searchUrlsViaScraper]
 * между парсингом JSON и возвратом результата.
 *
 * Алгоритм (7 правил из FR-004 спеки 294):
 * 1. Невалидный URL (URI.create throws) → отбрасывается.
 * 2. Схема ≠ `http`/`https` → отбрасывается.
 * 3. Homepage без path или только `/` → отбрасывается.
 * 4. URL содержит служебный path из [patterns] (case-insensitive substring) →
 *    отбрасывается.
 * 5. Расширение файла (из [patterns]) → отбрасывается (покрывается п.4).
 * 6. Tracking-маркер (из [patterns]) → отбрасывается, но НЕ как голая подстрока:
 *    паттерн, оканчивающийся на `=`, обязан начинаться на границе параметра
 *    (`?` или `&`) — иначе `ref=` ловил бы честный `?pref=...`
 *    (см. [matchesUselessUrlPattern], Pass 461).
 * 7. Дубликаты сохраняют порядок первого появления через `LinkedHashSet`.
 *
 * Сложность — O(N) на размер входного списка URL, без regex (substring matching).
 * На 50 URL укладывается в ≤1 мс (NFR-005 спеки).
 *
 * @param urls сырой список URL от scraper'а fourget.
 * @param patterns список паттернов для отбрасывания. По умолчанию —
 *   [uselessUrlPatternsList] (читает из `KaraokeProperties`).
 * @return очищенный список URL в порядке первого появления.
 *
 * @see specs/294-fourget-scraper-order/spec.md (FR-003, FR-004, FR-005)
 * @see archive/docs/features/llm-lyrics-search.md
 */
internal fun filterUselessLyricsUrls(
    urls: List<String>,
    patterns: List<String> = uselessUrlPatternsList(),
): List<String> {
    val patternsLower = patterns.map { it.lowercase() }
    val result = LinkedHashSet<String>()
    for (url in urls) {
        if (url.isBlank()) continue
        val uri =
            try {
                URI.create(url)
            } catch (e: IllegalArgumentException) {
                continue
            }
        if (uri.scheme !in listOf("http", "https")) continue
        val path = uri.path ?: ""
        if (path.isEmpty() || path == "/") continue
        val urlLower = url.lowercase()
        if (patternsLower.any { pattern -> matchesUselessUrlPattern(urlLower, pattern) }) continue
        result.add(url)
    }
    return result.toList()
}

/**
 * Проверяет один паттерн фильтра против URL (оба — в нижнем регистре).
 *
 * Паттерн, оканчивающийся на `=`, — это tracking-маркер query-строки
 * (`utm_source=`, `ref=`, `fbclid=`). Для него одного `contains` мало: подстрока
 * `ref=` находится и внутри совершенно честного параметра `?pref=...`, из-за чего
 * живая ссылка на текст песни отбрасывалась как мусор (Pass 461). Поэтому такой
 * паттерн обязан начинаться на границе параметра — сразу после `?` или `&`.
 *
 * Остальные паттерны (служебные path и расширения файлов) проверяются подстрокой по
 * всему URL, как и раньше.
 *
 * Чистая функция; регекспов нет — сложность O(длина URL) на паттерн, NFR-005 цел.
 */
internal fun matchesUselessUrlPattern(
    urlLower: String,
    patternLower: String,
): Boolean {
    if (!patternLower.endsWith("=")) return urlLower.contains(patternLower)

    // Ведущие '?'/'&' в паттерне — необязательная запись: в дефолтном списке часть
    // маркеров записана как "?utm_source=", а часть как "ref="/"fbclid=". Нормализуем
    // и требуем границу параметра в самом URL — тогда "?utm_source=" и "&utm_source="
    // ловятся одинаково, а "ref=" больше не срабатывает на честном "?pref=".
    val marker = patternLower.trimStart('?', '&')
    if (marker.isEmpty()) return false // паттерн из одних разделителей — ничего не отбрасываем
    return urlLower.contains("?$marker") || urlLower.contains("&$marker")
}

package com.svoemesto.karaokeapp.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.agent.tool.Tool
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
 * fourget (`/api/v1/web`). Движок-источник по умолчанию (`yandex`) на практике
 * оказался заблокирован/капчится на admin-машине (см.
 * specs/014-lyrics-search-replacement/research.md, раздел "Production finding")
 * — используются реально рабочие на этом хостинге `brave` (основной) с
 * фолбэком на `yep`, если `brave` не дал результатов.
 *
 * @see docs/features/llm-lyrics-search.md
 */
@Component
class SearchTool(
    @Value("\${lyrics-search.base-url:http://fourget:80}")
    private val lyricsSearchBaseUrl: String,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(SearchTool::class.java)

    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    @Tool("Search the web for URLs related to a query. Returns a list of URLs.")
    fun searchUrls(query: String): List<String> {
        for (scraper in LYRICS_SEARCH_SCRAPERS) {
            val urls = searchUrlsViaScraper(query, scraper)
            if (urls.isNotEmpty()) return urls
        }
        return emptyList()
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
            urls.forEach { logger.info("  → $it") }

            urls
        } catch (e: Exception) {
            logger.error("❌ [SearchTool] Ошибка (scraper=$scraper): ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        /** Порядок опробования scraper'ов fourget — см. KDoc класса. */
        private val LYRICS_SEARCH_SCRAPERS = listOf("brave", "yep")
    }
}

/**
 * Класс Lyrics Search Response — ответ fourget (`/api/v1/web`).
 *
 * @see docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LyricsSearchResponse(
    val status: String = "",
    val web: List<LyricsSearchResult> = emptyList(),
)

/**
 * Класс Lyrics Search Result — один результат веб-поиска fourget.
 *
 * @see docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LyricsSearchResult(
    val url: String = "",
    val title: String = "",
    val description: String = "",
)

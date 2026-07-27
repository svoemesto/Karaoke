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
 * fourget (`/api/v1/web`, движок-источник — Yandex, лучше индексирует
 * русскоязычные "текст песни" запросы, чем прежний SearXNG-бэкенд).
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
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$lyricsSearchBaseUrl/api/v1/web?s=$encodedQuery&scraper=yandex"

            logger.info("🔍 [SearchTool] Запрос к fourget: $url")

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
                logger.error("❌ [SearchTool] fourget вернул статус ${response.statusCode()}")
                return emptyList()
            }

            val searchResponse = objectMapper.readValue(response.body(), LyricsSearchResponse::class.java)
            if (searchResponse.status != "ok") {
                logger.error("❌ [SearchTool] fourget вернул status='${searchResponse.status}'")
                return emptyList()
            }

            val urls = searchResponse.web.map { it.url }.filter { it.isNotBlank() }

            logger.info("✅ [SearchTool] Найдено URL: ${urls.size}")
            urls.forEach { logger.info("  → $it") }

            urls
        } catch (e: Exception) {
            logger.error("❌ [SearchTool] Ошибка: ${e.message}", e)
            emptyList()
        }
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

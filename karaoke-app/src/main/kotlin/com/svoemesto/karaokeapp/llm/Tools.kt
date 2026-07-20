package com.svoemesto.karaokeapp.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class SearchTool(
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

    @Tool("Search the web for URLs related to a query. Returns a list of URLs.")
    fun searchUrls(query: String): List<String> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$searxngBaseUrl/search?q=$encodedQuery&format=json&language=ru"

            logger.info("🔍 [SearchTool] Запрос к SearXNG: $url")

            val request =
                HttpRequest
                    .newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.error("❌ [SearchTool] SearXNG вернул статус ${response.statusCode()}")
                return emptyList()
            }

            val searchResponse = objectMapper.readValue(response.body(), SearchResponse::class.java)
            val urls = searchResponse.results.map { it.url } // .take(5)

            logger.info("✅ [SearchTool] Найдено URL: ${urls.size}")
            urls.forEach { logger.info("  → $it") }

            urls
        } catch (e: Exception) {
            logger.error("❌ [SearchTool] Ошибка: ${e.message}", e)
            emptyList()
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResponse(
    val results: List<SearchResult> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResult(
    val url: String = "",
    val title: String = "",
    val content: String = "",
)

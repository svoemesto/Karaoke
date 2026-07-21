package com.svoemesto.karaokeapp.llm

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Сервис для lyrics finder .
 *
 * @see docs/features/llm-lyrics-search.md
 */
@Service
class LyricsFinderService(
    private val searchTool: SearchTool,
    private val scraperAgent: ScraperAgent,
) {
    private val logger = LoggerFactory.getLogger(LyricsFinderService::class.java)

    /**
     * Основная функция-оркестратор
     */
    fun findLyrics(
        artist: String,
        songTitle: String,
    ): Map<String, Any> {
        logger.info("🔍 [Оркестратор] Начинаю поиск текста для: $artist - $songTitle")

        // 1. Ищем URL
        val urls = searchUrls(artist, songTitle)

        if (urls.isEmpty()) {
            return mapOf("success" to false, "message" to "Поиск не дал результатов.")
        }

        val variants = mutableListOf<String>()

        // 2. Перебираем URL и пытаемся извлечь текст с каждого
        for (url in urls) {
            val lyrics = extractLyricsFromUrl(url)

            if (lyrics != null) {
                logger.info("🎵 [Оркестратор] Успех! Текст найден на: $url")
                variants.add("Источник: $url\n\n$lyrics")
            }
        }

        return if (variants.isNotEmpty()) {
            mapOf("success" to true, "variants" to variants)
        } else {
            mapOf("success" to false, "message" to "Не удалось извлечь текст песни.")
        }
    }

    /**
     * Функция поиска URL по исполнителю и названию песни
     */
    fun searchUrls(
        author: String,
        songName: String,
    ): List<String> {
        val query = "$author текст песни $songName"
        logger.info("🔍 [Поиск URL] Формирую запрос: '$query'")

        val urls = searchTool.searchUrls(query)
        logger.info("✅ [Поиск URL] Получено URL: ${urls.size}")

        return urls
    }

    /**
     * Функция извлечения текста песни с одного URL
     * @return текст песни или null, если текст не найден
     */
    fun extractLyricsFromUrl(url: String): String? {
        logger.info("📄 [Извлечение текста] Обрабатываю URL: $url")

        // 1. Загружаем и очищаем страницу
        val pageText =
            try {
                loadAndCleanPage(url)
            } catch (e: Exception) {
                logger.warn("⚠️ [Извлечение текста] Ошибка загрузки $url: ${e.message}")
                return null
            }

        // 2. Проверяем, что текст не пустой
        if (pageText.isBlank() || pageText.length < 50) {
            logger.warn("⚠️ [Извлечение текста] Страница $url пуста (${pageText.length} символов). Пропускаю.")
            return null
        }

        logger.info("📄 [Извлечение текста] Загружено ${pageText.length} символов. Передаю в ScraperAgent...")

        // 3. LLM анализирует текст
        val lyrics =
            try {
                scraperAgent.extractLyrics(pageText)
            } catch (e: Exception) {
                logger.error("❌ [Извлечение текста] Ошибка ScraperAgent для $url: ${e.message}")
                return null
            }

        logger.info("📥 [Извлечение текста] ScraperAgent вернул ${lyrics.length} символов")

        // 4. Проверяем результат
        return if (!lyrics.contains("NOT_FOUND") && lyrics.length > 50) {
            lyrics
        } else {
            logger.warn("⚠️ [Извлечение текста] Текст не найден на $url")
            null
        }
    }

    /**
     * Загрузка и очистка HTML страницы
     */
    private fun loadAndCleanPage(url: String): String {
        val doc =
            Jsoup
                .connect(url)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .timeout(10000)
                .get()

        doc.select("script, style, nav, footer, header, .ads, .comments, .sidebar, iframe, noscript").remove()

        val cleanText = doc.body().text()

        return if (cleanText.length > 8000) {
            cleanText.take(8000) + "\n... [текст обрезан]"
        } else {
            cleanText
        }
    }
}

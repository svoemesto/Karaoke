package com.svoemesto.karaokeapp.llm

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Сервис поиска текстов песен и аккордов через LLM.
 *
 * Алгоритм (`findLyrics` / `findChords`):
 * 1. [SearchTool] ищет 5-10 кандидатов (Google/Bing через прокси).
 * 2. [ScraperAgent] (langchain4j + LLM) парсит каждую страницу и
 *    извлекает структурированный текст/аккорды.
 * 3. Результаты ранжируются по эвристикам (длина текста, наличие
 *    аккордов, качество разметки).
 * 4. Лучший результат сохраняется в [com.svoemesto.karaokeapp.model.Song].
 *
 * @property searchTool HTTP-поисковик (Google/Bing через scraping).
 * @property scraperAgent LLM-агент для парсинга HTML.
 * @see archive/docs/features/llm-lyrics-search.md
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
     * То же самое, что [searchUrls], но через движок `SEARXNG`
     * ([com.svoemesto.karaokeapp.LyricsSearchEngine], specs/015-search-engine-selection) —
     * прямой запрос к self-hosted SearXNG вместо fourget.
     *
     * @see archive/docs/features/llm-lyrics-search.md
     */
    fun searchUrlsViaSearxng(
        author: String,
        songName: String,
    ): List<String> {
        val query = "$author текст песни $songName"
        logger.info("🔍 [Поиск URL, SearXNG] Формирую запрос: '$query'")

        val urls = searchTool.searchUrlsViaSearxng(query)
        logger.info("✅ [Поиск URL, SearXNG] Получено URL: ${urls.size}")

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
     * Загрузка и очистка HTML страницы.
     *
     * [fetchPage] качает страницу, [documentToStructuredText] превращает её в текст
     * **с сохранением переводов строк** (см. её KDoc — почему это важно),
     * [truncateForLlm] ограничивает размер по границе строки.
     */
    private fun loadAndCleanPage(url: String): String =
        truncateForLlm(documentToStructuredText(fetchPage(url)))

    private fun fetchPage(url: String): Document =
        Jsoup
            .connect(url)
            .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .timeout(10000)
            .get()
}

/**
 * Маркер перевода строки для jsoup-идиомы «сохранить структуру текста»
 * (см. [htmlToStructuredText]). Два символа — обратный слэш и `n`, а не перевод
 * строки: `Element.text()` нормализует настоящие пробельные символы и уничтожил бы
 * настоящий `\n`, поэтому вставляем маркер, а заменяем его на `\n` уже ПОСЛЕ `text()`.
 */
private const val NEWLINE_MARKER = "\\n"

/**
 * Ограничение размера текста страницы, который уходит в LLM. Было зашито числом
 * `8000` в `loadAndCleanPage`; вынесено сюда, чтобы граница была одна и её видел тест.
 */
internal const val MAX_PAGE_TEXT_CHARS = 8000

/**
 * Превращает HTML в текст для LLM, **сохраняя переводы строк**.
 *
 * Зачем это нужно (Pass 457). Системный промпт `ScraperAgent`
 * (`EXTRACT_LYRICS_SYSTEM_PROMPT`) прямо говорит модели: «Lyrics typically look like
 * short lines separated by line breaks… Preserve the original language and line
 * breaks». Но прежняя реализация брала
 * `doc.body().text()` — а `Element.text()` по определению нормализует пробельные
 * символы и **схлопывает всю страницу в одну строку**. То есть модель получала
 * ровно то, на что опираться ей запрещено: один абзац без единой границы строки,
 * и должна была восстанавливать разбиение по смыслу. Из 26497 заполненных
 * `source_text` в рабочей БД 4926 (18.6%) вообще не имеют переводов строк.
 *
 * Реализация — документированная jsoup-идиома: в текст вставляется маркер
 * [NEWLINE_MARKER] в местах `<br>` и на границах блочных элементов, затем `text()`
 * нормализует пробелы (маркер как обычные символы переживает нормализацию), и
 * только после этого маркер заменяется настоящим переводом строки.
 *
 * Границы блочных элементов дают лишние пустые строки (особенно на вложенных
 * `div`), поэтому результат проходит [normalizeExtractedText].
 */
internal fun htmlToStructuredText(html: String): String = documentToStructuredText(Jsoup.parse(html))

/**
 * Ядро [htmlToStructuredText] — работает с уже распарсенным документом, чтобы рабочий
 * путь не парсил страницу дважды (сеть → `Document` → текст; в тестах документ
 * собирается из строки через [htmlToStructuredText]).
 *
 * **[WARN] Мутирует переданный документ** (удаляет мусорные узлы и вставляет маркеры) —
 * вызывать на документе, который больше нигде не нужен.
 */
internal fun documentToStructuredText(document: Document): String {
    document.select("script, style, nav, footer, header, .ads, .comments, .sidebar, iframe, noscript").remove()
    document.select("br").after(NEWLINE_MARKER)
    document
        .select("p, div, li, tr, h1, h2, h3, h4, h5, h6, blockquote, pre")
        .apply {
            prepend(NEWLINE_MARKER)
            append(NEWLINE_MARKER)
        }
    return normalizeExtractedText(document.body().text().replace(NEWLINE_MARKER, "\n"))
}

/**
 * Нормализует текст, полученный из HTML: обрезает пробелы в каждой строке, схлопывает
 * повторяющиеся пробелы внутри строки и не даёт идти подряд более чем одной пустой
 * строке (пустая строка — разделитель куплетов, её терять нельзя, но и десяток подряд
 * не нужен). Чистая функция — покрыта тестом без сети.
 */
internal fun normalizeExtractedText(raw: String): String {
    val out = mutableListOf<String>()
    for (line in raw.lines()) {
        val cleaned = line.trim().replace(Regex("[ \\t]{2,}"), " ")
        if (cleaned.isEmpty() && (out.isEmpty() || out.last().isEmpty())) continue
        out.add(cleaned)
    }
    return out.joinToString("\n").trim()
}

/**
 * Ограничивает текст для LLM до [limit] символов, обрезая **по границе строки**:
 * прежняя версия резала ровно по символу, поэтому последняя строка уходила в модель
 * обрубленной посередине слова. Если границы строки в пределах лимита нет — режем по
 * символу, как раньше. Признак обрезки остаётся текстовым маркером (его видит модель
 * и может сообщить, что текст неполный).
 */
internal fun truncateForLlm(
    text: String,
    limit: Int = MAX_PAGE_TEXT_CHARS,
): String {
    if (text.length <= limit) return text
    val cut = text.lastIndexOf('\n', limit).takeIf { it > 0 } ?: limit
    return text.take(cut) + "\n... [текст обрезан]"
}

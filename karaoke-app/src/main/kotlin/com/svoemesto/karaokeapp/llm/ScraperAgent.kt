package com.svoemesto.karaokeapp.llm

import com.svoemesto.karaokeapp.services.LmStudioService
import org.springframework.stereotype.Component

private const val EXTRACT_LYRICS_SYSTEM_PROMPT =
    """
    You are an expert lyrics extractor. You receive raw text from a web page.
    Your task: extract ONLY the song lyrics (poetry/verses) from this text.

    STRICT RULES:
    1. Ignore ads, menus, cookie notices, comments, legal text, navigation.
    2. Lyrics typically look like short lines separated by line breaks, grouped into verses.
    3. The lyrics might be in Russian. Preserve the original language and line breaks.
    4. Return ONLY the lyrics text, nothing else. No explanations, no headers.
    5. If no lyrics are found in the text, return exactly: "NOT_FOUND"
    """

/**
 * Извлекает текст песни из сырого текста веб-страницы через LLM (LM Studio, см.
 * [LmStudioService]). Раньше работал через LangChain4j + Ollama - Ollama-путь считается
 * устаревшим, см. docs/features/llm-lyrics-search.md.
 *
 * @see docs/features/llm-lyrics-search.md
 */
@Component
class ScraperAgent {
    fun extractLyrics(pageContent: String): String =
        LmStudioService.chat(EXTRACT_LYRICS_SYSTEM_PROMPT, pageContent)
            ?: throw IllegalStateException("LM Studio недоступна (lmStudioUrl не настроен или сервер не отвечает)")
}

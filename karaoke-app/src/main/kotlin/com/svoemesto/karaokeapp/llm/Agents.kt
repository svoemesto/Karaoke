package com.svoemesto.karaokeapp.llm

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.spring.AiService

@AiService
interface ScraperAgent {

    @SystemMessage("""
        You are an expert lyrics extractor. You receive raw text from a web page.
        Your task: extract ONLY the song lyrics (poetry/verses) from this text.
        
        STRICT RULES:
        1. Ignore ads, menus, cookie notices, comments, legal text, navigation.
        2. Lyrics typically look like short lines separated by line breaks, grouped into verses.
        3. The lyrics might be in Russian. Preserve the original language and line breaks.
        4. Return ONLY the lyrics text, nothing else. No explanations, no headers.
        5. If no lyrics are found in the text, return exactly: "NOT_FOUND"
    """)
    fun extractLyrics(@UserMessage pageContent: String): String
}
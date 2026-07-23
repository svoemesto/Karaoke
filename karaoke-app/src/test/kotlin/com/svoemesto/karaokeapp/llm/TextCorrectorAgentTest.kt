package com.svoemesto.karaokeapp.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка пословной пост-обработки ответа LLM для "Исправить пунктуацию" (без сети/LM
 * Studio) — по образцу YandexLyricsSearchOutcomeTest.
 */
class TextCorrectorAgentTest {
    @Test
    fun `регистр разрешён, изменённые буквы откатываются, пунктуация LLM сохраняется`() {
        val original = "Привет как дола у тебя у мення норм"
        val corrected = "Привет! Как дела у тебя? У меня - норм!"
        assertEquals(
            "Привет! Как дола у тебя? У мення - норм!",
            reconcilePunctuationLine(original, corrected),
        )
    }

    @Test
    fun `слово с изменённой буквой откатывается к исходному целиком`() {
        assertEquals(
            "дела",
            reconcilePunctuationLine("дела", "дила"),
        )
    }

    @Test
    fun `изменение только регистра принимается`() {
        assertEquals(
            "Дела",
            reconcilePunctuationLine("дела", "Дела"),
        )
    }

    @Test
    fun `несовпадение числа слов - строка целиком откатывается`() {
        val original = "привет мир"
        val corrected = "привет большой мир"
        assertEquals(original, reconcilePunctuationLine(original, corrected))
    }

    @Test
    fun `enforcePunctuationOnly сохраняет число строк, недостающая строка откатывается`() {
        val original = "первая строка\nвторая строка"
        val corrected = "Первая строка!"
        // В ответе LLM всего одна строка (перенос строки потерян) - для строки 0 пара есть и она
        // проходит пословную сверку, для строки 1 пары нет вовсе - откатывается к исходной.
        assertEquals("Первая строка!\nвторая строка", enforcePunctuationOnly(original, corrected))
    }

    @Test
    fun `enforcePunctuationOnly применяет правки построчно и независимо`() {
        val original = "привет мир\nкак дила"
        val corrected = "Привет, мир!\nКак дела?"
        assertEquals(
            "Привет, мир!\nКак дила?",
            enforcePunctuationOnly(original, corrected),
        )
    }
}

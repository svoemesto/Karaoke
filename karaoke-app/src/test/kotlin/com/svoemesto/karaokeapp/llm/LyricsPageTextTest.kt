package com.svoemesto.karaokeapp.llm

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-тесты подготовки текста страницы для LLM (Pass 457): [htmlToStructuredText],
 * [normalizeExtractedText], [truncateForLlm]. Сеть не используется — HTML парсится
 * из строки.
 *
 * Повод: `loadAndCleanPage` брала `doc.body().text()`, а `Element.text()` схлопывает
 * пробельные символы, то есть отдавала модели одну строку — при том что системный
 * промпт `ScraperAgent` требует «Preserve … line breaks» и опирается на то, что
 * «lyrics … look like short lines separated by line breaks».
 */
internal class LyricsPageTextTest {
    private fun page(body: String): String = "<html><body>$body</body></html>"

    @Test
    fun `прежнее поведение схлопывало страницу в одну строку — это и был дефект`() {
        val html = page("Сонной рукою,<br>Ударами кисти<br>Жизнь стушевала портрет.")
        // Фиксируем исходную причину: body().text() даёт ОДНУ строку.
        val oldLineCount =
            Jsoup
                .parse(html)
                .body()
                .text()
                .lines()
                .size
        assertEquals(1, oldLineCount)
        // Новая функция сохраняет построчную структуру.
        assertEquals(3, htmlToStructuredText(html).lines().size)
    }

    @Test
    fun `br становится переводом строки`() {
        val html = page("Первая строка<br>Вторая строка<br>Третья строка")
        assertEquals(
            "Первая строка\nВторая строка\nТретья строка",
            htmlToStructuredText(html),
        )
    }

    @Test
    fun `блочные элементы разделяются строками`() {
        val html = page("<p>Первый куплет</p><p>Второй куплет</p><div>Третий</div>")
        val lines = htmlToStructuredText(html).lines().filter { it.isNotBlank() }
        assertEquals(listOf("Первый куплет", "Второй куплет", "Третий"), lines)
    }

    @Test
    fun `пустая строка между куплетами сохраняется, но не размножается`() {
        val html = page("Куплет один<br><br><br><br>Куплет два")
        val text = htmlToStructuredText(html)
        assertEquals("Куплет один\n\nКуплет два", text)
        assertFalse(text.contains("\n\n\n"), "подряд идущих пустых строк быть не должно")
    }

    @Test
    fun `мусорные блоки удаляются`() {
        val body =
            "<nav>Меню сайта</nav><header>Шапка</header>" +
                "<div class='ads'>Купи слона</div><div class='comments'>Комментарий</div>" +
                "<div class='sidebar'>Сбоку</div><iframe>рамка</iframe><noscript>нет js</noscript>" +
                "<script>var secret = 'РЕКЛАМА_В_СКРИПТЕ';</script>" +
                "<style>.x{color:red}</style>" +
                "<div class='lyrics'>Строка текста<br>Ещё строка</div>" +
                "<footer>Подвал</footer>"
        val text = htmlToStructuredText(page(body))
        assertTrue(text.contains("Строка текста"))
        assertTrue(text.contains("Ещё строка"))
        val junkWords =
            listOf(
                "Меню сайта", "Шапка", "Купи слона", "Комментарий",
                "Сбоку", "Подвал", "РЕКЛАМА_В_СКРИПТЕ", "color:red",
            )
        for (junk in junkWords) {
            assertFalse(text.contains(junk), "в тексте для LLM не должно быть: $junk")
        }
    }

    @Test
    fun `повторяющиеся пробелы внутри строки схлопываются, края обрезаются`() {
        val html = page("   Строка     с    пробелами   <br>Вторая   ")
        assertEquals("Строка с пробелами\nВторая", htmlToStructuredText(html))
    }

    @Test
    fun `normalizeExtractedText не оставляет пустых строк по краям`() {
        assertEquals("А\n\nБ", normalizeExtractedText("\n\n  А  \n\n\n\n  Б \n\n"))
    }

    @Test
    fun `короткий текст не обрезается`() {
        val text = "строка один\nстрока два"
        assertEquals(text, truncateForLlm(text, limit = 1000))
    }

    @Test
    fun `обрезка идёт по границе строки, а не по символу`() {
        val text = "а".repeat(10) + "\n" + "б".repeat(10) + "\n" + "в".repeat(10)
        val result = truncateForLlm(text, limit = 15)
        // Режем по последнему переводу строки в пределах лимита (позиция 10).
        assertEquals("а".repeat(10) + "\n... [текст обрезан]", result)
        assertTrue(result.startsWith("а".repeat(10)))
        // Вторая строка не должна попасть целиком (проверяем всю строку: одиночная
        // буква «б» есть и в самом маркере обрезки — «обрезан»).
        assertFalse(result.contains("б".repeat(10)), "вторая строка не должна попасть в обрезанный текст")
    }

    @Test
    fun `если перевода строки в пределах лимита нет — режем по символу`() {
        val text = "х".repeat(50)
        assertEquals("х".repeat(10) + "\n... [текст обрезан]", truncateForLlm(text, limit = 10))
    }

    @Test
    fun `маркер обрезки сообщает модели о неполноте`() {
        val result = truncateForLlm("а\nб\nв", limit = 3)
        assertTrue(result.endsWith("[текст обрезан]"))
    }

    @Test
    fun `дефолтный лимит — 8000 символов`() {
        assertEquals(8000, MAX_PAGE_TEXT_CHARS)
        val long = "а".repeat(9000)
        assertTrue(truncateForLlm(long).length < 9000)
    }
}

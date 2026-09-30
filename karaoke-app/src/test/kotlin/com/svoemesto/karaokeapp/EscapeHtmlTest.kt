package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * #215: экранирование пользовательского текста песни перед вставкой в HTML.
 *
 * Отдельный тест именно на хелпер, а не на билдер: конструктор `Song()` тянет
 * `lateinit KSS_APP` (KaraokeStorageService с реальной БД) и на этапе инициализации
 * класса падает с ExceptionInInitializerError. Хелпер — чистая функция, тестируется
 * без инфраструктуры.
 *
 * Порядок замен проверяется отдельно: если экранировать `&` после `<`, то `&lt;`
 * развернётся в `&amp;lt;` и текст покажет пользователю «&lt;» вместо «<».
 */
class EscapeHtmlTest {
    @Test
    fun `спецсимволы экранируются`() {
        assertEquals("&lt;script&gt;", "<script>".escapeHtml())
        assertEquals("a&amp;b", "a&b".escapeHtml())
        assertEquals("&quot;цитата&quot;", "\"цитата\"".escapeHtml())
        assertEquals("&#39;апостроф&#39;", "'апостроф'".escapeHtml())
    }

    @Test
    fun `обычный текст песни не меняется`() {
        val ordinary = "Прого_во_дал_ся_за_д_верь_"
        assertEquals(ordinary, ordinary.escapeHtml())
        assertEquals("Прогодался", "Прогодался".escapeHtml())
    }

    @Test
    fun `амперсанд экранируется раньше угловых скобок`() {
        // Именно этот порядок гарантирует, что "&lt;" не станет "&amp;lt;"
        assertEquals("&amp;lt;", "&lt;".escapeHtml())
    }

    @Test
    fun `после экранирования в строке нет символов, способных открыть тег`() {
        val payload = "<img src=x onerror=alert(1)>\"'&"
        val escaped = payload.escapeHtml()
        // '&' остаётся — он начинает каждую сущность (&lt;, &amp; ...), поэтому
        // проверять его нельзя. Проверяем то, что реально открывает/закрывает тег.
        assertFalse(escaped.contains("<"), "не должно остаться сырого '<' (открывает тег): $escaped")
        assertFalse(escaped.contains(">"), "не должно остаться сырого '>' (закрывает тег): $escaped")
        assertFalse(escaped.contains("\""), "не должно остаться сырой двойной кавычки: $escaped")
        assertFalse(escaped.contains("'"), "не должно остаться сырой одинарной кавычки: $escaped")
        // Кавычки из payload должны пережить только внутри сущностей.
        // В payload ровно по одной '"', "' и '&'.
        assertEquals(1, Regex("&quot;").findAll(escaped).count())
        assertEquals(1, Regex("&#39;").findAll(escaped).count())
        assertEquals(1, Regex("&amp;").findAll(escaped).count())
    }

    @Test
    fun `подчёркивание не экранируется - оно маркер конца слова`() {
        // Слоги хранят конец слова как "_"; экранирование не должно его ломать,
        // иначе посыплется вся разметка табулатуры.
        assertEquals("про_go_дал_", "про_go_дал_".escapeHtml())
    }
}

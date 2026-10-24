package com.svoemesto.karaokeweb.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессионный тест «до/после» для аудита BR-504 «news-link-scheme-xss:templates» (OpenProject #378).
 *
 * **ДО правки** ссылка новости (`tbl_news.link`) подставлялась в `href` «как есть»: Thymeleaf
 * экранирует HTML-сущности, но не проверяет схему URL, поэтому значение
 * `javascript:alert(document.domain)` исполнялось по клику в origin страницы. Проверки схемы не
 * было ни на записи, ни на рендере.
 *
 * **ПОСЛЕ правки** в модель главной страницы попадают только ссылки со схемой `http(s)`; всё
 * остальное (`javascript:`, `data:`, `vbscript:`, относительные пути, управляющие символы)
 * отбрасывается и рендерится как обычный текст (см. `main.html`).
 */
class SafeLinkTest {
    @Test
    fun `javascript-схема отклоняется`() {
        assertFalse(SafeLink.isSafe("javascript:alert(document.domain)"))
        assertFalse(SafeLink.isSafe("  JavaScript:alert(1)  "))
        // Обфускация переводом строки внутри схемы: браузер склеивает, проверка обязана отклонить.
        assertFalse(SafeLink.isSafe("java\nscript:alert(1)"))
    }

    @Test
    fun `data и vbscript отклоняются`() {
        assertFalse(SafeLink.isSafe("data:text/html,<script>alert(1)</script>"))
        assertFalse(SafeLink.isSafe("vbscript:msgbox(1)"))
    }

    @Test
    fun `http и https разрешены`() {
        assertTrue(SafeLink.isSafe("https://sm-karaoke.ru/news/1"))
        assertTrue(SafeLink.isSafe("http://example.com/path?q=1#frag"))
        assertTrue(SafeLink.isSafe("HTTPS://SM-KARAOKE.RU"))
        assertTrue(SafeLink.isSafe("  https://sm-karaoke.ru  "))
    }

    @Test
    fun `относительные пути и пустые значения отклоняются`() {
        assertFalse(SafeLink.isSafe(null))
        assertFalse(SafeLink.isSafe(""))
        assertFalse(SafeLink.isSafe("   "))
        assertFalse(SafeLink.isSafe("/news/1"))
        assertFalse(SafeLink.isSafe("news/1"))
    }

    @Test
    fun `управляющие символы и пробелы внутри URL отклоняются`() {
        assertFalse(SafeLink.isSafe("https://sm-karaoke.ru/\u0000"))
        assertFalse(SafeLink.isSafe("https://sm-karaoke.ru/a b"))
        assertFalse(SafeLink.isSafe("javascript\u0000:alert(1)"))
    }

    @Test
    fun `схема с ведущими пробелами внутри отклоняется`() {
        // «java script:» не является http(s) ни в одном браузере.
        assertFalse(SafeLink.isSafe("java script:alert(1)"))
        assertFalse(SafeLink.isSafe("ht tp://example.com"))
    }
}

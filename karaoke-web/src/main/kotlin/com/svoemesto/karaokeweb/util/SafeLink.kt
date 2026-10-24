package com.svoemesto.karaokeweb.util

/**
 * Проверка схемы внешней ссылки перед тем, как отдать её в HTML.
 *
 * Аудит BR-504 (OpenProject #378): ссылка новости (`tbl_news.link`) подставлялась в `href`
 * «как есть». Thymeleaf экранирует HTML-сущности, но **не проверяет схему URL** — значение
 * `javascript:…` попадало в атрибут без изменений, и клик по заголовку новости выполнял
 * произвольный скрипт в origin страницы.
 *
 * Правило: ссылка безопасна, только если её схема — `http` или `https`. Всё остальное
 * (`javascript:`, `data:`, `vbscript:`, относительные пути, пустые строки) — небезопасно.
 *
 * @see com.svoemesto.karaokeweb.controllers.MainController — санитайзер применяется к модели
 *   главной страницы перед рендером.
 */
object SafeLink {
    /** Разрешённые схемы внешней ссылки (в нижнем регистре). */
    private val SAFE_SCHEMES = listOf("http://", "https://")

    /**
     * Безопасна ли ссылка для рендера в `href`/`window.open`.
     *
     * @param link исходное значение (может быть null/пустым).
     * @return true — ссылку можно отдавать в разметку.
     */
    fun isSafe(link: String?): Boolean {
        val trimmed = link?.trim() ?: return false
        if (trimmed.isEmpty()) return false
        // Управляющие символы и пробелы внутри URL недопустимы — такой URL браузер трактует
        // непредсказуемо (в т.ч. `java\nscript:`), поэтому отклоняем его целиком.
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return false
        val lower = trimmed.lowercase()
        return SAFE_SCHEMES.any { lower.startsWith(it) }
    }
}

package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.model.PublicationType
import com.svoemesto.karaokeapp.model.Song

/**
 * Рендеринг шаблонов подписей к демо-MP4 в Telegram (capsion, ≤1024 символа).
 *
 * Паттерн — по образцу [VkTemplateService]. Шаблоны хранятся в [KaraokeProperties]
 * (`telegramTemplateAir`, `telegramTemplatePremium`) с плейсхолдерами в фигурных скобках.
 * [render] заменяет известные плейсхолдеры и оставляет неизвестные как literal-текст.
 * [templateFor] возвращает шаблон по [PublicationType] (или дефолт, если ключ пуст).
 * [placeholders] — список плейсхолдеров для UI редактора шаблонов.
 *
 * Отличия от [VkTemplateService]:
 * - Нет `{demoVideo}` — в Telegram видео всегда прикрепляется к сообщению (нельзя
 *   вставить в текст), поэтому маркер не нужен.
 * - Нет `{newsBody}` — для Telegram auto-publish нет связанной новости в tbl_news
 *   (только auto-публикация по расписанию; manual-публикация использует текущий
 *   `buildCaption` без шаблона). Плейсхолдер оставлен для будущей расширяемости.
 * - Лимит длины текста — 1024 (Telegram caption limit), а не 10 000.
 *
 * Доступные плейсхолдеры (см. [PLACEHOLDERS]):
 * - `{author}` — Song.author
 * - `{songName}` — Song.songName (сырое)
 * - `{songNameCensored}` — Song.songName.censored() (цензурированное)
 * - `{year}` — Song.year
 * - `{album}` — Song.album
 * - `{link}` — https://sm-karaoke.ru/song?id={id}
 * - `{id}` — Song.id
 * - `{body}` — News.body (для совместимости с VK; для Telegram обычно пусто)
 * - `{descriptionHeader}` — Song.getTextForDescriptionHeader()
 * - `{descriptionFooter}` — Song.getTextForDescriptionFooter()
 * - `{description}` — Song.getTextForDescription()
 * - `{descriptionWithTimecodes}` — Song.getTextForDescriptionWithTimecodes()
 */
object TelegramTemplateService {
    /** Лимит длины caption в Telegram Bot API. */
    const val TELEGRAM_CAPTION_MAX_LENGTH = 1024

    /** Дефолтный шаблон для типа `air` (если `telegramTemplateAir` пустой). */
    const val DEFAULT_AIR_TEMPLATE = "{songNameCensored} ★♫★ {author}\n{link}\n#караоке #svoemesto"

    /** Дефолтный шаблон для типа `premium` (если `telegramTemplatePremium` пустой). */
    const val DEFAULT_PREMIUM_TEMPLATE =
        "{songNameCensored} ★♫★ {author} (премиум)\n{link}\n#караоке #svoemesto #премиум"

    /**
     * Список плейсхолдеров для UI/endpoint `/api/telegram/templates`.
     */
    val PLACEHOLDERS: List<PlaceholderInfo> =
        listOf(
            PlaceholderInfo("author", "Song.author — автор песни"),
            PlaceholderInfo("songName", "Song.songName — название песни (сырое)"),
            PlaceholderInfo("songNameCensored", "Song.songName.censored() — цензурированное название"),
            PlaceholderInfo("year", "Song.year — год"),
            PlaceholderInfo("album", "Song.album — название альбома"),
            PlaceholderInfo("link", "https://sm-karaoke.ru/song?id={id} — ссылка на страницу песни"),
            PlaceholderInfo("id", "Song.id — идентификатор песни"),
            PlaceholderInfo("body", "News.body — резерв (для Telegram обычно пусто)"),
            PlaceholderInfo("descriptionHeader", "Song.getTextForDescriptionHeader() — заголовок-описание песни"),
            PlaceholderInfo("descriptionFooter", "Song.getTextForDescriptionFooter() — подвал со ссылками/хештегами"),
            PlaceholderInfo("description", "Song.getTextForDescription() — текст-описание песни"),
            PlaceholderInfo("descriptionWithTimecodes", "Song.getTextForDescriptionWithTimecodes() — описание с таймкодами"),
        )

    private val placeholderRegex = Regex("""\{(\w+)}""")

    fun templateFor(type: PublicationType): String =
        when (type) {
            PublicationType.AIR ->
                KaraokeProperties
                    .getString("telegramTemplateAir")
                    .ifBlank { DEFAULT_AIR_TEMPLATE }
            PublicationType.PREMIUM ->
                KaraokeProperties
                    .getString("telegramTemplatePremium")
                    .ifBlank { DEFAULT_PREMIUM_TEMPLATE }
        }

    /**
     * Рендерит [template] с заменой плейсхолдеров. Усекает до [TELEGRAM_CAPTION_MAX_LENGTH].
     * Неизвестные плейсхолдеры остаются literal-текстом.
     *
     * @param database Соединение для чтения словаря «Censored» при построении `songNameCensored`
     *   (specs/139-fix-censored-dictionary) — ДОЛЖЕН совпадать с соединением, из которого
     *   загружен [song], иначе используется дефолтный `karaoke-app`-глобал.
     */
    fun render(
        template: String,
        song: Song,
        database: KaraokeConnection = WORKING_DATABASE,
    ): String {
        val link = "https://sm-karaoke.ru/song?id=${song.id}"
        val replacements: Map<String, String> =
            mapOf(
                "author" to song.author,
                "songName" to song.songName,
                "songNameCensored" to song.songName.censored(database),
                "year" to song.year.toString(),
                "album" to song.album,
                "link" to link,
                "id" to song.id.toString(),
                "body" to "",
                "descriptionHeader" to song.getTextForDescriptionHeader(null),
                "descriptionFooter" to song.getTextForDescriptionFooter(),
                "description" to song.getTextForDescription(),
                "descriptionWithTimecodes" to song.getTextForDescriptionWithTimecodes(),
            )
        val rendered =
            placeholderRegex.replace(template) { mr ->
                val key = mr.groupValues[1]
                replacements[key] ?: mr.value
            }
        return truncate(rendered, TELEGRAM_CAPTION_MAX_LENGTH)
    }

    fun placeholders(): List<Map<String, String>> = PLACEHOLDERS.map { it.toMap() }

    private fun truncate(
        text: String,
        maxLen: Int,
    ): String {
        if (text.length <= maxLen) return text
        val cut = text.substring(0, maxLen - 1)
        val lastSpace = cut.lastIndexOf(' ').takeIf { it > maxLen - 50 } ?: (maxLen - 1)
        return text.substring(0, lastSpace) + "…"
    }
}

package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.PublicationType
import com.svoemesto.karaokeapp.model.Song

/**
 * Рендеринг шаблонов постов ВКонтакте с плейсхолдерами (specs/121-vk-news-auto-publish, FR-023).
 *
 * Шаблоны — многострочные строки в [KaraokeProperties] (`vkTemplateAir`, `vkTemplatePremium`,
 * FR-024) с плейсхолдерами в фигурных скобках. [render] заменяет известные плейсхолдеры на
 * значения полей песни/новости; неизвестные оставляет как literal-текст (FR-023 — не падает,
 * не заменяет). [templateFor] возвращает шаблон по [PublicationType] (или дефолт, если ключ
 * пуст). [placeholders] — список доступных плейсхолдеров с описаниями (для UI редактора
 * шаблонов и endpoint `/api/vk/templates`, FR-025).
 *
 * Доступные плейсхолдеры (см. [PLACEHOLDERS]):
 * - `{author}` — Song.author
 * - `{songName}` — Song.songName (сырое)
 * - `{songNameCensored}` — Song.songNameCensored (цензурированное название из БД, specs/277-song-name-censored)
 * - `{year}` — Song.year (год)
 * - `{album}` — Song.album (название альбома)
 * - `{link}` — https://sm-karaoke.ru/song?id={id}
 * - `{id}` — Song.id
 * - `{newsBody}` — News.body (для air; пусто для premium)
 * - `{descriptionHeader}` — Song.getTextForDescriptionHeader() (заголовок-описание песни)
 * - `{descriptionFooter}` — Song.getTextForDescriptionFooter() (подвал со ссылками/хештегами)
 * - `{description}` — Song.getTextForDescription() (текст-описание песни)
 * - `{descriptionWithTimecodes}` — Song.getTextForDescriptionWithTimecodes() (описание с таймкодами)
 * - `{demoVideo}` — **маркер**: если присутствует в шаблоне, бот прикрепит демо-MP4
 *   через `attachments` VK API (видео нельзя вставить в конкретное место текста поста —
 *   оно прикрепляется к посту целиком). Сам маркер из итогового текста удаляется.
 *   Если `{demoVideo}` отсутствует — бот публикует только текст без видео.
 *
 * @see archive/docs/features/vk-news-auto-publish.md
 */
object VkTemplateService {
    /** Лимит длины текста поста ВК (FR-005). */
    const val VK_POST_MAX_LENGTH = 10_000

    /** Дефолтный шаблон для типа `air` (если `vkTemplateAir` пустой). */
    const val DEFAULT_AIR_TEMPLATE =
        "{songNameCensored} ★♫★ {author}\n{link}\n{demoVideo}\n#караоке #svoemesto"

    /**
     * Дефолтный шаблон для типа `premium` (если `vkTemplatePremium` пустой).
     *
     * **Без маркера `{demoVideo}`** — на 02.08.2026 выяснилось, что метод VK API `video.save`
     * требует user-token с правом `video` (выдаётся VK только в исключительных случаях через
     * запрос в support). У админа есть только Community access token (для `wall.post` от
     * имени группы), но НЕ для `video.save` (error_code=5, "invalid token type"). Поэтому
     * дефолтный premium-шаблон публикуется только текстом + ссылка на песню, без видео.
     * Когда в будущем будет получен user-token с правом `video` — вернуть в шаблон маркер
     * `{demoVideo}`, и `VkAutoPublishService.publishTextOnly` сменится на `publishFile`
     * автоматически (по флагу `includeDemoVideo`).
     */
    const val DEFAULT_PREMIUM_TEMPLATE =
        "{songNameCensored} ★♫★ {author} (премиум)\n{link}\n#караоке #svoemesto #премиум"

    /**
     * Список известных плейсхолдеров с описаниями (для UI/endpoint `/api/vk/templates`).
     * Порядок важен — отображается в редакторе шаблонов.
     */
    val PLACEHOLDERS: List<PlaceholderInfo> =
        listOf(
            PlaceholderInfo("author", "Song.author — автор песни"),
            PlaceholderInfo("songName", "Song.songName — название песни (сырое)"),
            PlaceholderInfo("songNameCensored", "Song.songNameCensored — цензурированное название (из БД, без запроса к tbl_dictionaries)"),
            PlaceholderInfo("year", "Song.year — год"),
            PlaceholderInfo("album", "Song.album — название альбома"),
            PlaceholderInfo("link", "https://sm-karaoke.ru/song?id={id} — ссылка на страницу песни"),
            PlaceholderInfo("id", "Song.id — идентификатор песни"),
            PlaceholderInfo("newsBody", "News.body — текст связанной новости (для air; пусто для premium)"),
            PlaceholderInfo("descriptionHeader", "Song.getTextForDescriptionHeader() — заголовок-описание песни"),
            PlaceholderInfo("descriptionFooter", "Song.getTextForDescriptionFooter() — подвал со ссылками/хештегами"),
            PlaceholderInfo("description", "Song.getTextForDescription() — текст-описание песни"),
            PlaceholderInfo("descriptionWithTimecodes", "Song.getTextForDescriptionWithTimecodes() — описание с таймкодами"),
            PlaceholderInfo(
                "demoVideo",
                "маркер: если присутствует — бот прикрепит демо-MP4 видео к посту; сам маркер из текста удаляется",
            ),
        )

    /** Регулярка для поиска плейсхолдеров вида `{name}`. */
    private val placeholderRegex = Regex("""\{(\w+)}""")

    /**
     * Возвращает шаблон для [type] из [KaraokeProperties] (`vkTemplateAir` / `vkTemplatePremium`),
     * или дефолтный, если ключ пуст (FR-024).
     */
    fun templateFor(type: PublicationType): String =
        when (type) {
            PublicationType.AIR ->
                KaraokeProperties
                    .getString("vkTemplateAir")
                    .ifBlank { DEFAULT_AIR_TEMPLATE }
            PublicationType.PREMIUM ->
                KaraokeProperties
                    .getString("vkTemplatePremium")
                    .ifBlank { DEFAULT_PREMIUM_TEMPLATE }
        }

    /**
     * Рендерит [template] с заменой плейсхолдеров на значения из [song] и (опционально) [news]
     * (FR-023). Неизвестные плейсхолдеры остаются как literal-текст. Усекает итог до
     * [VK_POST_MAX_LENGTH] (FR-005) с разумной границей.
     *
     * Обёртка над [renderWithFlags] — возвращает только текст (без флага `demoVideo`),
     * для preview/обратной совместимости.
     */
    fun render(
        template: String,
        song: Song,
        news: News? = null,
        database: KaraokeConnection = WORKING_DATABASE,
    ): String = renderWithFlags(template, song, news, database).message

    /**
     * Рендерит [template] с заменой плейсхолдеров на значения из [song] и (опционально) [news]
     * (FR-023), с извлечением специальных маркеров:
     * - `{demoVideo}` — если присутствует, устанавливает `includeDemoVideo=true` и
     *   удаляется из итогового текста (видео прикрепляется через `attachments` VK API,
     *   не в тексте).
     *
     * Возвращает [RenderResult] с `message` (отрендеренный текст) и `includeDemoVideo`.
     *
     * @param database Соединение для чтения словаря «Censored» при построении `songNameCensored`
     *   (specs/139-fix-censored-dictionary) — ДОЛЖЕН совпадать с соединением, из которого
     *   загружены [song]/[news], иначе используется дефолтный `karaoke-app`-глобал.
     */
    fun renderWithFlags(
        template: String,
        song: Song,
        news: News? = null,
        database: KaraokeConnection = WORKING_DATABASE,
    ): RenderResult {
        val link = "https://sm-karaoke.ru/song?id=${song.id}"
        val replacements: Map<String, String> =
            mapOf(
                "author" to song.author,
                "songName" to song.songName,
                "songNameCensored" to song.songNameCensored,
                "year" to song.year.toString(),
                "album" to song.album,
                "link" to link,
                "id" to song.id.toString(),
                "newsBody" to (news?.body ?: ""),
                "descriptionHeader" to song.getTextForDescriptionHeader(null),
                "descriptionFooter" to song.getTextForDescriptionFooter(),
                "description" to song.getTextForDescription(),
                "descriptionWithTimecodes" to song.getTextForDescriptionWithTimecodes(),
            )
        // Маркер {demoVideo} — не часть текста, извлекаем отдельно.
        val includeDemoVideo = template.contains("{demoVideo}")
        val templateWithoutDemoVideo = template.replace("{demoVideo}", "")
        val rendered =
            placeholderRegex.replace(templateWithoutDemoVideo) { mr ->
                val key = mr.groupValues[1]
                replacements[key] ?: mr.value // неизвестный плейсхолдер → literal
            }
        // Очистка пустых строк, оставшихся после удаления {demoVideo} (если он был на отдельной строке).
        val cleaned = rendered.replace(Regex("\n{3,}"), "\n\n").trimEnd()
        return RenderResult(
            message = truncate(cleaned, VK_POST_MAX_LENGTH),
            includeDemoVideo = includeDemoVideo,
        )
    }

    /**
     * Список доступных плейсхолдеров с описаниями (для `/api/vk/templates`, FR-025).
     */
    fun placeholders(): List<Map<String, String>> = PLACEHOLDERS.map { it.toMap() }

    /** Усечение до [maxLen] с разумной границей (не разрывая слово) + маркер `…` (FR-005). */
    private fun truncate(
        text: String,
        maxLen: Int,
    ): String {
        if (text.length <= maxLen) return text
        val cut = text.substring(0, maxLen - 1)
        val lastSpace = cut.lastIndexOf(' ').takeIf { it > maxLen - 200 } ?: (maxLen - 1)
        return text.substring(0, lastSpace) + "…"
    }
}

/**
 * Описание одного плейсхолдера (для UI редактора и endpoint `/api/vk/templates`).
 *
 * @see archive/docs/features/vk-news-auto-publish.md
 */
data class PlaceholderInfo(
    val name: String,
    val description: String,
) {
    /** Преобразует в Map для JSON-ответа `/api/vk/templates`. */
    fun toMap(): Map<String, String> = mapOf("name" to name, "description" to description)
}

/**
 * Результат рендеринга шаблона [VkTemplateService.renderWithFlags]:
 * отрендеренный текст поста + флаг прикрепления демо-MP4 (маркер `{demoVideo}`).
 *
 * @see archive/docs/features/vk-news-auto-publish.md
 */
data class RenderResult(
    val message: String,
    val includeDemoVideo: Boolean,
)

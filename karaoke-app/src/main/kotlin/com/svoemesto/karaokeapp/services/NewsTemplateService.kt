package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.Song

/**
 * Рендеринг шаблонов автоматических новостей сайта (`tbl_news.title`/`body`)
 * с плейсхолдерами (specs/128-news-publish-templates, FR-001..FR-016).
 *
 * Шаблоны — 4 строковых ключа в `tbl_public_settings`
 * (`newsTemplateAirTitle`, `newsTemplateAirBody`, `newsTemplatePremiumTitle`,
 * `newsTemplatePremiumBody`), читаются прямым JDBC при создании auto-новости
 * в [SongReleaseAnnouncementService]. При отсутствии/пустом значении
 * возвращается [DEFAULT_AIR_TITLE]/[DEFAULT_AIR_BODY]/[DEFAULT_PREMIUM_TITLE]/
 * [DEFAULT_PREMIUM_BODY] (FR-010, byte-идентичны прежним хардкод-строкам
 * в [SongReleaseAnnouncementService] до фичи — выпуск прозрачный).
 *
 * В отличие от [VkTemplateService]/[TelegramTemplateService]:
 * - Шаблоны хранятся в `tbl_public_settings` (Postgres), а не в `KaraokeProperties` —
 *   потому что [SongReleaseAnnouncementService] живёт на проде в `karaoke-web`,
 *   где файла `KaraokeProperties` нет (FR-016).
 * - Запись идёт через `INSERT ... ON CONFLICT DO UPDATE` (UPSERT) в
 *   `NewsTemplateController` — первый сохранение не требует seed-миграции (R2).
 * - Параметр `target=local|remote` — выбор БД для записи/чтения (по образцу
 *   `PublicSettingsController.resolveDb`, R1).
 *
 * Доступные плейсхолдеры (см. [PLACEHOLDERS]):
 * - `{author}` — Song.author
 * - `{songName}` — Song.songName (сырое)
 * - `{songNameCensored}` — Song.songName.censored() (цензурированное)
 * - `{year}` — Song.year (год)
 * - `{album}` — Song.album (название альбома)
 * - `{albumYearSuffix}` — суффикс `" (альбом «X», Y)"`, пустой, если альбом и год
 *   не заполнены (byte-идентичен хелперу `SongReleaseAnnouncementService.albumYearSuffix`)
 * - `{bodyDetails}` — `author, альбом «X», Y` одной строкой (byte-идентичен
 *   хелперу `SongReleaseAnnouncementService.bodyDetails`)
 * - `{link}` — `https://sm-karaoke.ru/song?id={id}`
 * - `{id}` — Song.id
 * - `{newsBody}` — News.body связанной новости (только для `air`; для `premium` всегда пусто)
 * - `{descriptionHeader}` — Song.getTextForDescriptionHeader()
 * - `{descriptionFooter}` — Song.getTextForDescriptionFooter()
 * - `{description}` — Song.getTextForDescription()
 * - `{descriptionWithTimecodes}` — Song.getTextForDescriptionWithTimecodes()
 *
 * **НЕ включено**: `{demoVideo}` — маркер для прикрепления медиа к посту
 * ВК/Telegram; для `tbl_news` не имеет смысла.
 *
 * @see docs/features/news-templates.md
 */
object NewsTemplateService {
    /** Лимит длины `tbl_news.title` (VARCHAR(500)). Превышение → усечение с `…` (FR-010a, Edge Cases). */
    const val NEWS_TITLE_MAX_LENGTH = 500

    /** Дефолтный шаблон `title` для категории `air` (byte-идентичен прежнему хардкоду). */
    const val DEFAULT_AIR_TITLE = "Новая песня: {author} — {songName}{albumYearSuffix}"

    /** Дефолтный шаблон `body` для категории `air` (byte-идентичен прежнему хардкоду). */
    const val DEFAULT_AIR_BODY = "Песня «{songName}» ({bodyDetails}) вышла в эфир."

    /** Дефолтный шаблон `title` для категории `premium` (byte-идентичен прежнему хардкоду). */
    const val DEFAULT_PREMIUM_TITLE = "Новая песня: {author} — {songName}{albumYearSuffix}"

    /** Дефолтный шаблон `body` для категории `premium` (byte-идентичен прежнему хардкоду). */
    const val DEFAULT_PREMIUM_BODY = "Песня «{songName}» ({bodyDetails}) появилась в коллекции."

    /** Разрешённые ключи `tbl_public_settings` для шаблонов новостей сайта. */
    val ALLOWED_KEYS: Set<String> =
        setOf(
            "newsTemplateAirTitle",
            "newsTemplateAirBody",
            "newsTemplatePremiumTitle",
            "newsTemplatePremiumBody",
        )

    /**
     * Список известных плейсхолдеров с описаниями (для UI редактора
     * и endpoint `/api/news/templates`, FR-009).
     * Порядок важен — отображается в редакторе шаблонов.
     */
    val PLACEHOLDERS: List<PlaceholderInfo> =
        listOf(
            PlaceholderInfo("author", "Song.author — автор песни"),
            PlaceholderInfo("songName", "Song.songName — название песни (сырое)"),
            PlaceholderInfo("songNameCensored", "Song.songName.censored() — цензурированное название"),
            PlaceholderInfo("year", "Song.year — год"),
            PlaceholderInfo("album", "Song.album — название альбома"),
            PlaceholderInfo("albumYearSuffix", "суффикс \" (альбом «X», Y)\" — пустой, если альбом и год не заполнены (byte-идентичен хардкод)"),
            PlaceholderInfo("bodyDetails", "author, альбом «X», Y одной строкой (byte-идентичен хардкод)"),
            PlaceholderInfo("link", "https://sm-karaoke.ru/song?id={id} — ссылка на песню"),
            PlaceholderInfo("id", "Song.id — идентификатор песни"),
            PlaceholderInfo("newsBody", "News.body — текст связанной новости (для air; пусто для premium)"),
            PlaceholderInfo("descriptionHeader", "Song.getTextForDescriptionHeader() — заголовок-описание песни"),
            PlaceholderInfo("descriptionFooter", "Song.getTextForDescriptionFooter() — подвал со ссылками/хештегами"),
            PlaceholderInfo("description", "Song.getTextForDescription() — текст-описание песни"),
            PlaceholderInfo("descriptionWithTimecodes", "Song.getTextForDescriptionWithTimecodes() — описание с таймкодами"),
        )

    /** Регулярка для поиска плейсхолдеров вида `{name}`. */
    private val placeholderRegex = Regex("""\{(\w+)}""")

    /**
     * Возвращает шаблон по [key] из [KaraokeConnection] (по сути — прямой JDBC к
     * `tbl_public_settings`), или дефолтный, если ключ отсутствует/пуст/ошибка JDBC
     * (FR-010, fail-open по образцу `News.isNewsAutoPublishKillSwitchActive`).
     *
     * [key] ДОЛЖЕН быть в [ALLOWED_KEYS] — иначе возвращается пустая строка
     * (валидация делается в [NewsTemplateController] при записи).
     */
    fun template(
        key: String,
        database: KaraokeConnection,
    ): String {
        val defaultValue = defaultFor(key)
        val connection = database.getConnection() ?: return defaultValue
        return try {
            connection.prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getString("value").ifBlank { defaultValue }
                    } else {
                        defaultValue
                    }
                }
            }
        } catch (e: Exception) {
            println("NewsTemplateService.template($key) error: ${e.message}")
            defaultValue
        }
    }

    /**
     * Рендерит [template] с заменой плейсхолдеров на значения из [song] и (опционально) [news]
     * (FR-005, FR-006). Неизвестные плейсхолдеры остаются как literal-текст.
     *
     * Усекает результат до [NEWS_TITLE_MAX_LENGTH] (FR-010a, Edge Cases) с разумной границей —
     * используется для `tbl_news.title` (VARCHAR(500)). Для `body` (TEXT, без лимита) вызывающий
     * код может рендерить без усечения или с заведомо большим `NEWS_TITLE_MAX_LENGTH`-аналогом.
     *
     * Параметр [truncate] = `false` используется для рендера `body` (TEXT без лимита).
     *
     * @param database Соединение для чтения словаря «Censored» при построении `songNameCensored`
     *   (specs/139-fix-censored-dictionary) — ДОЛЖЕН совпадать с соединением, из которого загружена
     *   [song]/[news] (например, `karaoke-web`-вызов ДОЛЖЕН передать свой `WORKING_DATABASE`,
     *   иначе используется дефолтный `karaoke-app`-глобал).
     */
    fun render(
        template: String,
        song: Song,
        news: News? = null,
        truncate: Boolean = true,
        database: KaraokeConnection = WORKING_DATABASE,
    ): String {
        val replacements: Map<String, String> =
            buildReplacements(song, news, database)
        val rendered =
            placeholderRegex.replace(template) { mr ->
                val key = mr.groupValues[1]
                replacements[key] ?: mr.value
            }
        return if (truncate) truncate(rendered, NEWS_TITLE_MAX_LENGTH) else rendered
    }

    /**
     * Список доступных плейсхолдеров с описаниями (для `/api/news/templates`, FR-009).
     */
    fun placeholders(): List<Map<String, String>> = PLACEHOLDERS.map { it.toMap() }

    /** Возвращает дефолтное значение шаблона по [key] (для UI и fallback при пустом/error JDBC). */
    fun defaultFor(key: String): String =
        when (key) {
            "newsTemplateAirTitle" -> DEFAULT_AIR_TITLE
            "newsTemplateAirBody" -> DEFAULT_AIR_BODY
            "newsTemplatePremiumTitle" -> DEFAULT_PREMIUM_TITLE
            "newsTemplatePremiumBody" -> DEFAULT_PREMIUM_BODY
            else -> ""
        }

    /** Описание ключа для UI / endpoint'а (для generic `PublicSettingsTable`). */
    fun descriptionFor(key: String): String =
        when (key) {
            "newsTemplateAirTitle" -> "Шаблон авто-новости «в эфире» — заголовок (плейсхолдеры: {author}, {songName}, {albumYearSuffix})"
            "newsTemplateAirBody" -> "Шаблон авто-новости «в эфире» — тело (плейсхолдеры: {songName}, {bodyDetails})"
            "newsTemplatePremiumTitle" -> "Шаблон авто-новости «в коллекции» — заголовок (плейсхолдеры: {author}, {songName}, {albumYearSuffix})"
            "newsTemplatePremiumBody" -> "Шаблон авто-новости «в коллекции» — тело (плейсхолдеры: {songName}, {bodyDetails})"
            else -> ""
        }

    /** Категория (`air`|`premium`) по ключу (для UI группировки). */
    fun categoryFor(key: String): String =
        when (key) {
            "newsTemplateAirTitle", "newsTemplateAirBody" -> "air"
            "newsTemplatePremiumTitle", "newsTemplatePremiumBody" -> "premium"
            else -> ""
        }

    /** Поле (`title`|`body`) по ключу (для UI группировки). */
    fun fieldFor(key: String): String =
        when (key) {
            "newsTemplateAirTitle", "newsTemplatePremiumTitle" -> "title"
            "newsTemplateAirBody", "newsTemplatePremiumBody" -> "body"
            else -> ""
        }

    /**
     * Суффикс `" (альбом «X», Y)"` для заголовка новости — пустая строка, если ни альбом, ни год не
     * заполнены (byte-идентичен хелперу [SongReleaseAnnouncementService.albumYearSuffix]).
     * **Public** — для использования из [SongReleaseAnnouncementService] и потенциально из других мест.
     */
    fun albumYearSuffix(song: Song): String {
        val parts = mutableListOf<String>()
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return if (parts.isEmpty()) "" else " (" + parts.joinToString(", ") + ")"
    }

    /**
     * Автор + альбом/год одной строкой для тела новости (byte-идентичен хелперу
     * [SongReleaseAnnouncementService.bodyDetails]). **Public** — для использования из
     * [SongReleaseAnnouncementService].
     */
    fun bodyDetails(song: Song): String {
        val parts = mutableListOf(song.author)
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return parts.joinToString(", ")
    }

    /** Усечение до [maxLen] с разумной границей (не разрывая слово) + маркер `…` (по образцу `VkTemplateService.truncate`). */
    private fun truncate(
        text: String,
        maxLen: Int,
    ): String {
        if (text.length <= maxLen) return text
        val cut = text.substring(0, maxLen - 1)
        val lastSpace = cut.lastIndexOf(' ').takeIf { it > maxLen - 200 } ?: (maxLen - 1)
        return text.substring(0, lastSpace) + "…"
    }

    /** Сборка Map плейсхолдер→значение для одной песни/новости. */
    private fun buildReplacements(
        song: Song,
        news: News?,
        database: KaraokeConnection,
    ): Map<String, String> =
        mapOf(
            "author" to song.author,
            "songName" to song.songName,
            "songNameCensored" to song.songName.censored(database),
            "year" to song.year.toString(),
            "album" to song.album,
            "albumYearSuffix" to albumYearSuffix(song),
            "bodyDetails" to bodyDetails(song),
            "link" to "https://sm-karaoke.ru/song?id=${song.id}",
            "id" to song.id.toString(),
            "newsBody" to (news?.body ?: ""),
            "descriptionHeader" to song.getTextForDescriptionHeader(null),
            "descriptionFooter" to song.getTextForDescriptionFooter(),
            "description" to song.getTextForDescription(),
            "descriptionWithTimecodes" to song.getTextForDescriptionWithTimecodes(),
        )
}

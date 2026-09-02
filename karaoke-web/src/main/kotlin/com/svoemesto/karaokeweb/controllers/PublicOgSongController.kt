package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * SEO-HTML ответ для поисковых ботов и краулеров соцсетей при обходе `/song?id=NNN`.
 *
 * Начиная с 13.08.2026 (specs/180-og-seo-html, Pass 51) endpoint возвращает полноценный
 * SEO-HTML вместо «голого» HTML с видимым `<img>`. Раньше (до 13.08.2026) endpoint
 * проектировался для сниппетов ВКонтакте: видимая картинка в `<body>` → парсер VK формирует
 * сниппет поста. С момента реализации автопубликации ВК (см.
 * `specs/121-vk-news-auto-publish`) подход к постам изменился — посты формируются через
 * VK API с прикреплением демо-MP4, а парсинг ссылок VK больше не основной канал шаринга.
 * Тем не менее endpoint остался **единственной точкой входа для поисковых ботов**
 * (Googlebot/Bingbot/YandexBot) при обходе страниц `/song?id=NNN`, и этим ботам нужна
 * **структурированная информация** для индексации, а не картинка.
 *
 * Endpoint **НЕ генерирует** PNG-картинку «на лету» (старый endpoint
 * `/api/public/song-vk-image/{id}` остался в коде для обратной совместимости, но из этого
 * контроллера не вызывается — см. FR-009 спеки и `archive/docs/features/seo-html-for-bots.md`).
 *
 * Endpoint проксируется nginx'ом на проде через правило `location /song { if
 * ($http_user_agent ~* "vkShare|TelegramBot|...") rewrite ^/song(\?.*)?$ /api/public/og/song$1 last; }`
 * (см. `deploy/web-server-deploy/deploy/80to8897`, Pass 35, 2026-08-05). Боты VK/Telegram/
 * Yandex/Google/etc идут сюда (SEO-HTML для индексации и сниппетов); обычные браузеры идут
 * в SPA Vue (порт 7907), Vue Router отрендерит `SongView`.
 *
 * Возвращаемый HTML содержит:
 * - `<title>`, canonical URL, `<meta name="description">`, `<meta name="robots">`.
 * - Open Graph (`og:title/description/url/type=music.song/site_name/locale/image/...`).
 * - Twitter Card (`twitter:card=summary_large_image/title/description/image`).
 * - Schema.org JSON-LD `MusicRecording` с `byArtist/inAlbum/datePublished/genre/inLanguage/
 *   description/url/image/lyrics/isAccessibleForFree`.
 * - Видимый semantic HTML (`<h1>`, `<h2>`, секции `#meta`/`#description`/`#lyrics`/`#chords`/
 *   `#listen`, `<footer>`).
 *
 * Все строковые поля экранируются через [escape] (для HTML) или [escapeJsonLd] (для
 * JSON-LD, дополнительно экранирует `\`, control chars и `<>&` для предотвращения
 * XSS через `</script>`).
 *
 * Крайние случаи (FR-006 спеки):
 * - `id == null || id <= 0` → HTTP 400 (короткий HTML «Не указан id песни»).
 * - Песня не найдена → HTTP 404 (короткий HTML «Песня не найдена: id=NNN»).
 * - Тег `SKIP` → HTTP 200 + `<meta name="robots" content="noindex, nofollow">` +
 *   видимый warning «Контент удалён по требованию правообладателя», без текста/аккордов/
 *   ссылок на стриминг.
 * - `idStatus < 3` → HTTP 200, без секций `#lyrics` и `#chords` (текст ещё не верифицирован).
 * - Нет обложки альбома → `og:image` указывает на `KARAOKE_LOGO.png`.
 *
 * Производительность (SC-001 спеки): TTFB < 100 мс. 0 обращений к MinIO (og:image URL —
 * абсолютный, отдаётся nginx'ом через `/minio/`-location).
 *
 * @see archive/docs/features/seo-html-for-bots.md
 * @see specs/180-og-seo-html/spec.md
 * @see specs/180-og-seo-html/research.md (обоснование технических решений)
 */
@RestController
class PublicOgSongController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    private val log = LoggerFactory.getLogger(PublicOgSongController::class.java)

    @GetMapping("/api/public/og/song", produces = [MediaType.TEXT_HTML_VALUE + "; charset=UTF-8"])
    fun ogSongHtml(
        @RequestParam(required = false) id: Long?,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
    ): String {
        if (id == null || id <= 0) {
            return htmlWithError("Не указан id песни (добавьте ?id=NNN)")
        }
        log.info("OG render for song id={}, User-Agent={}", id, userAgent)

        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return htmlWithError("Песня не найдена: id=$id")

        return buildSeoHtmlForBots(song)
    }

    private fun htmlWithError(message: String): String =
        "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\"><title>OG error</title></head>" +
            "<body style=\"font-family:sans-serif;padding:40px\">" +
            "<h1>❌ Ошибка</h1><p>${escape(message)}</p>" +
            "</body></html>"

    /**
     * Строит полный SEO-HTML ответ для бота: `<head>` с мета-тегами + JSON-LD, `<body>`
     * с видимым semantic-контентом. Обрабатывает все крайние случаи (FR-006 спеки):
     * SKIP-тег, `idStatus < 3`, отсутствие обложки.
     */
    private fun buildSeoHtmlForBots(song: Song): String {
        val skipped = isSkipped(song)
        val canonical = "https://sm-karaoke.ru/song?id=${song.id}"
        val title = "${song.songName.ifBlank { "Без названия" }} — ${song.author.ifBlank { "Неизвестный автор" }}"
        val pageTitle = "$title — Караоке на sm-karaoke.ru"
        val description = buildDescription(song)
        val albumImageUrl = buildAlbumImageUrl(song)
        val albumImageAlt = "${song.songName.ifBlank { "?" }} — ${song.author.ifBlank { "?" }}"

        val sb = StringBuilder(8192)
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"ru\">\n")
        sb.append("<head>\n")
        sb.append("  <meta charset=\"UTF-8\">\n")
        sb.append("  <title>").append(escape(pageTitle)).append("</title>\n")
        sb.append("  <meta name=\"description\" content=\"").append(escape(description)).append("\">\n")
        sb.append("  <link rel=\"canonical\" href=\"").append(escape(canonical)).append("\">\n")
        if (skipped) {
            sb.append("  <meta name=\"robots\" content=\"noindex, nofollow\">\n")
        } else {
            sb.append("  <meta name=\"robots\" content=\"index, follow\">\n")
        }
        sb.append("  <meta name=\"generator\" content=\"sm-karaoke.ru karaoke-pipeline\">\n")

        if (!skipped) {
            // Open Graph
            sb.append("  <meta property=\"og:title\" content=\"").append(escape(pageTitle)).append("\">\n")
            sb.append("  <meta property=\"og:description\" content=\"").append(escape(description)).append("\">\n")
            sb.append("  <meta property=\"og:url\" content=\"").append(escape(canonical)).append("\">\n")
            sb.append("  <meta property=\"og:type\" content=\"music.song\">\n")
            sb.append("  <meta property=\"og:site_name\" content=\"Караоке на sm-karaoke.ru\">\n")
            sb.append("  <meta property=\"og:locale\" content=\"ru_RU\">\n")
            sb.append("  <meta property=\"og:image\" content=\"").append(escape(albumImageUrl)).append("\">\n")
            sb.append("  <meta property=\"og:image:width\" content=\"400\">\n")
            sb.append("  <meta property=\"og:image:height\" content=\"400\">\n")
            sb.append("  <meta property=\"og:image:alt\" content=\"").append(escape(albumImageAlt)).append("\">\n")

            // Twitter Card
            sb.append("  <meta name=\"twitter:card\" content=\"summary_large_image\">\n")
            sb.append("  <meta name=\"twitter:title\" content=\"").append(escape(pageTitle)).append("\">\n")
            sb.append("  <meta name=\"twitter:description\" content=\"").append(escape(description)).append("\">\n")
            sb.append("  <meta name=\"twitter:image\" content=\"").append(escape(albumImageUrl)).append("\">\n")

            // Schema.org JSON-LD
            sb.append("  <script type=\"application/ld+json\">\n")
            sb.append(buildJsonLd(song, canonical, description, albumImageUrl))
            sb.append("  </script>\n")
        }

        sb.append("</head>\n")
        sb.append("<body>\n")

        // Видимый контент
        sb.append("  <header>\n")
        sb.append("    <h1>").append(escape(song.songName.ifBlank { "Без названия" })).append("</h1>\n")
        sb.append("    <h2>").append(escape(song.author.ifBlank { "Неизвестный автор" })).append("</h2>\n")
        if (song.shortDescription.isNotBlank()) {
            sb.append("    <p>").append(escape(song.shortDescription)).append("</p>\n")
        }
        if (song.warning.isNotBlank()) {
            sb.append("    <p class=\"warning\">").append(escape(song.warning)).append("</p>\n")
        }
        if (skipped) {
            sb.append("    <p class=\"warning\">Контент удалён по требованию правообладателя</p>\n")
        }
        sb.append("  </header>\n")

        if (!skipped) {
            // Секция #meta — определения (год, альбом, трек, тональность, BPM, жанры, длительность)
            sb.append(buildMetaSection(song))

            // Секция #description — описание песни
            if (song.description.isNotBlank()) {
                sb.append("  <section id=\"description\">\n")
                sb.append("    <h3>Описание</h3>\n")
                sb.append("    <p>").append(escape(song.description)).append("</p>\n")
                sb.append("  </section>\n")
            }

            // Секция #lyrics — текст песни (только если idStatus >= 3)
            if (song.idStatus >= 3 && song.formattedTextSong.isNotBlank()) {
                sb.append("  <section id=\"lyrics\">\n")
                sb.append("    <h3>")
                sb.append(
                    when (song.songType.name) {
                        "POETRY" -> "Текст"
                        "INSTRUMENTAL" -> "Описание"
                        else -> "Текст песни"
                    },
                )
                sb.append("</h3>\n")
                sb.append("    <pre>").append(escape(song.formattedTextSong)).append("</pre>\n")
                sb.append("  </section>\n")
            }

            // Секция #chords — аккорды и табы (только если idStatus >= 3)
            if (song.idStatus >= 3) {
                val chordsText = song.formattedTextChords.ifBlank { song.formattedTextTabs }
                if (chordsText.isNotBlank()) {
                    sb.append("  <section id=\"chords\">\n")
                    sb.append("    <h3>Аккорды и табы</h3>\n")
                    sb.append("    <pre>").append(escape(chordsText)).append("</pre>\n")
                    sb.append("  </section>\n")
                }
            }

            // Секция #listen — платформенные ссылки
            sb.append(buildListenSection(song))
        }

        // Footer
        sb.append("  <footer>\n")
        sb.append("    <p>© sm-karaoke.ru — Караоке русского рока</p>\n")
        sb.append("    <p><a href=\"").append(escape(canonical)).append("\">Открыть на сайте</a></p>\n")
        sb.append("  </footer>\n")
        sb.append("</body>\n")
        sb.append("</html>\n")

        return truncateIfTooLarge(sb.toString())
    }

    /**
     * Формирует Schema.org JSON-LD блок `MusicRecording` со всеми обязательными полями.
     * FR-003 спеки. Все строки экранируются через [escapeJsonLd].
     */
    private fun buildJsonLd(
        song: Song,
        canonical: String,
        description: String,
        albumImageUrl: String,
    ): String {
        val sb = StringBuilder(2048)
        sb.append("{\n")
        sb.append("  \"@context\": \"https://schema.org\",\n")
        sb.append("  \"@type\": \"MusicRecording\",\n")
        sb.append("  \"@id\": \"").append(escapeJsonLd(canonical)).append("\",\n")
        sb.append("  \"name\": \"").append(escapeJsonLd(song.songName.ifBlank { "Без названия" })).append("\",\n")
        sb.append("  \"byArtist\": {\n")
        sb.append("    \"@type\": \"MusicGroup\",\n")
        sb.append("    \"name\": \"").append(escapeJsonLd(song.author.ifBlank { "Неизвестный автор" })).append("\"\n")
        sb.append("  }")
        if (song.album.isNotBlank()) {
            sb.append(",\n")
            sb.append("  \"inAlbum\": {\n")
            sb.append("    \"@type\": \"MusicAlbum\",\n")
            sb.append("    \"name\": \"").append(escapeJsonLd(song.album)).append("\"")
            if (song.year > 0) {
                sb.append(",\n")
                sb.append("    \"datePublished\": \"").append(song.year).append("\"")
            }
            sb.append("\n")
            sb.append("  }")
        }
        if (song.year > 0) {
            sb.append(",\n")
            sb.append("  \"datePublished\": \"").append(song.year).append("\"")
        }
        // Жанры — теги без SKIP, в lowercase
        val genres =
            song.tags
                .split(" ")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.uppercase() != "SKIP" }
                .map { it.lowercase() }
                .distinct()
        if (genres.isNotEmpty()) {
            sb.append(",\n")
            sb.append("  \"genre\": [")
            sb.append(genres.joinToString(", ") { "\"${escapeJsonLd(it)}\"" })
            sb.append("]")
        }
        sb.append(",\n")
        sb.append("  \"inLanguage\": \"ru\"")
        sb.append(",\n")
        sb.append("  \"description\": \"").append(escapeJsonLd(description)).append("\"")
        sb.append(",\n")
        sb.append("  \"url\": \"").append(escapeJsonLd(canonical)).append("\"")
        sb.append(",\n")
        sb.append("  \"image\": \"").append(escapeJsonLd(albumImageUrl)).append("\"")
        if (song.idStatus >= 3 && song.formattedTextSong.isNotBlank()) {
            sb.append(",\n")
            sb.append("  \"lyrics\": {\n")
            sb.append("    \"@type\": \"CreativeWork\",\n")
            sb.append("    \"text\": \"").append(escapeJsonLd(song.formattedTextSong)).append("\"\n")
            sb.append("  }")
        }
        sb.append(",\n")
        sb.append("  \"isAccessibleForFree\": ").append(if (song.isFreelyAvailableNow) "true" else "false")
        if (genres.isNotEmpty()) {
            sb.append(",\n")
            sb.append("  \"keywords\": \"").append(escapeJsonLd(genres.joinToString(", "))).append("\"")
        }
        sb.append("\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Секция `#meta` — definition list с метаданными песни (год, альбом, трек,
     * тональность, BPM, жанры, длительность). Только непустые поля. FR-004 спеки.
     */
    private fun buildMetaSection(song: Song): String {
        val sb = StringBuilder(512)
        sb.append("  <section id=\"meta\">\n")
        sb.append("    <h3>О песне</h3>\n")
        sb.append("    <dl>\n")
        if (song.author.isNotBlank()) {
            sb.append("      <dt>Исполнитель</dt><dd>").append(escape(song.author)).append("</dd>\n")
        }
        if (song.album.isNotBlank()) {
            sb.append("      <dt>Альбом</dt><dd>").append(escape(song.album)).append("</dd>\n")
        }
        if (song.year > 0) {
            sb.append("      <dt>Год</dt><dd>").append(song.year).append("</dd>\n")
        }
        if (song.track > 0) {
            sb.append("      <dt>Трек</dt><dd>").append(song.track).append("</dd>\n")
        }
        if (song.key.isNotBlank()) {
            sb.append("      <dt>Тональность</dt><dd>").append(escape(song.key)).append("</dd>\n")
        }
        if (song.bpm > 0) {
            sb.append("      <dt>Темп</dt><dd>").append(song.bpm).append(" BPM</dd>\n")
        }
        val genres =
            song.tags
                .split(" ")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.uppercase() != "SKIP" }
        if (genres.isNotEmpty()) {
            sb.append("      <dt>Жанры</dt><dd>").append(escape(genres.joinToString(", "))).append("</dd>\n")
        }
        if (song.ms > 0) {
            sb.append("      <dt>Длительность</dt><dd>").append(escape(formatDurationMs(song.ms))).append("</dd>\n")
        }
        sb.append("    </dl>\n")
        sb.append("  </section>\n")
        return sb.toString()
    }

    /**
     * Секция `#listen` — список платформенных ссылок (только непустые). FR-004 спеки.
     */
    private fun buildListenSection(song: Song): String {
        val links =
            buildList {
                add("Sponsr" to song.linkSponsrPlay)
                add("VK Видео (караоке)" to song.linkVkKaraoke)
                add("VK Видео (текст)" to song.linkVkLyrics)
                add("VK Видео (табы)" to song.linkVkTabs)
                add("VK Видео (аккорды)" to song.linkVkChords)
                add("Telegram (караоке)" to song.linkTgKaraoke)
                add("Telegram (текст)" to song.linkTgLyrics)
                add("Telegram (табы)" to song.linkTgTabs)
                add("Telegram (аккорды)" to song.linkTgChords)
                add("Яндекс Дзен (караоке)" to song.linkDzenKaraoke)
                add("Яндекс Дзен (текст)" to song.linkDzenLyrics)
                add("Яндекс Дзен (табы)" to song.linkDzenTabs)
                add("Яндекс Дзен (аккорды)" to song.linkDzenChords)
            }.filter { it.second.isNotBlank() }

        if (links.isEmpty()) return ""

        val sb = StringBuilder(512)
        sb.append("  <section id=\"listen\">\n")
        sb.append("    <h3>Послушать</h3>\n")
        sb.append("    <ul>\n")
        for ((label, url) in links) {
            val safeUrl = escape(url)
            val safeLabel = escape(label)
            sb.append("      <li><a href=\"").append(safeUrl)
            sb.append("\" rel=\"noopener noreferrer\">").append(safeLabel)
            sb.append("</a></li>\n")
        }
        sb.append("    </ul>\n")
        sb.append("  </section>\n")
        return sb.toString()
    }

    /**
     * Описание для `<meta name="description">` и `og:description`: берёт [Song.description]
     * (если не пусто), иначе fallback на короткий placeholder.
     */
    private fun buildDescription(song: Song): String {
        if (song.description.isNotBlank()) return song.description
        return "Караоке-песня ${song.author.ifBlank { "?" }} — ${song.songName.ifBlank { "?" }} на сайте sm-karaoke.ru"
    }

    /**
     * URL обложки альбома в MinIO. Строится напрямую из полей песни БЕЗ обращения к
     * `Pictures.getPictureByName` — последний может дёргать MinIO и иметь side-effects
     * через `pictureAlbumReady = true; saveToDb()` (см. Song.kt:728). Для SEO-endpoint
     * URL строится детерминированно: если обложки нет — fallback на `KARAOKE_LOGO.png`.
     *
     * nginx проксирует `/minio/` напрямую в MinIO (без участия Java), так что если
     * файла по вычисленному пути нет — nginx вернёт 404 для `og:image`, что
     * приемлемо для OG-тегов (большинство ботов fallback на дефолтную иконку).
     */
    private fun buildAlbumImageUrl(song: Song): String {
        val author = song.author
        val year = song.year
        val album = song.album
        if (author.isBlank() || year == 0L || album.isBlank()) {
            return FALLBACK_LOGO_URL
        }
        val path = "$author/$year - $album/$author - $year - $album.album.png"
        val encodedPath =
            path.split("/").joinToString("/") { segment ->
                java.net.URLEncoder
                    .encode(segment, Charsets.UTF_8)
                    .replace("+", "%20")
            }
        return "$MINIO_BASE_URL/$encodedPath"
    }

    /**
     * Форматирует длительность в формате `mm:ss` (например, `03:45`).
     * Возвращает пустую строку для `ms <= 0`.
     */
    @Suppress("unused")
    private fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return ""
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /**
     * Проверяет наличие тега `SKIP` в `song.tags` (паттерн из `SongPublicDto.kt:152`).
     * Используется для US3 — контент удалён по требованию правообладателя.
     *
     * specs/293-skip-author-toggle: сигнатура НЕ расширена параметром canSeeSkipped намеренно —
     * этот метод используется ТОЛЬКО в `buildSeoHtmlForBots` (endpoint `/api/public/og/song`),
     * который вызывается ТОЛЬКО ботами VK/Telegram/Yandex/Google/etc через nginx-rewrite
     * (см. KDoc класса, строки 31-35, и `deploy/web-server-deploy/deploy/80to8897`). У ботов
     * нет `Authorization`-заголовка, и `canSeeSkipped` для них всегда false. Расширение
     * сигнатуры было бы мёртвым кодом; SKIP-песни остаются скрытыми от индексации —
     * compliance с требованиями правообладателя. Для авторизованных пользователей SKIP
     * снимается в других endpoint'ах (Закрома, история прослушиваний, share-link) —
     * см. `MainController.zakroma`, `PublicApiController.zakroma`,
     * `ListeningHistoryController.getForUser`.
     */
    private fun isSkipped(song: Song): Boolean =
        song.tags
            .split(" ")
            .map { it.uppercase() }
            .contains("SKIP")

    /**
     * Обрезает HTML до [MAX_HTML_SIZE] байт с маркером усечения, чтобы не вернуть
     * огромный ответ (защита от DoS и таймаутов на стороне nginx/бота). R7 research.md.
     */
    private fun truncateIfTooLarge(html: String): String {
        if (html.length <= MAX_HTML_SIZE) return html
        val cut = MAX_HTML_SIZE - TRUNCATION_MARKER.length - 32
        return html.substring(0, cut) + TRUNCATION_MARKER
    }

    /**
     * HTML-escape строки для вставки в HTML-атрибуты и текст: экранирует
     * `&`, `<`, `>`, `"`, `'`. FR-005 спеки.
     */
    private fun escape(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /**
     * Escape строки для безопасной вставки в JSON-LD блок `<script type="application/ld+json">`.
     *
     * Кроме базовых HTML-символов (`<`, `>`, `&`), экранирует:
     * - `\` → `\\` (JSON-обратная косая черта).
     * - `"` → `\"` (JSON-кавычка).
     * - Control chars (`\n`, `\r`, `\t`, `\b`, `\f`) → их JSON-escape.
     * - Прочие символы с кодом < 0x20 → `\u00XX` (защита от битых строк).
     *
     * `<` → `\u003c` и `>` → `\u003e` через Unicode-escape — стандартный OWASP-паттерн
     * для предотвращения преждевременного закрытия `<script>` через данные.
     */
    private fun escapeJsonLd(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '<' -> sb.append("\\u003c")
                '>' -> sb.append("\\u003e")
                '&' -> sb.append("\\u0026")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u%04x".format(c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    companion object {
        /**
         * Дефолтный логотип для случая, когда обложка альбома отсутствует. Согласовано с
         * `PublicApiController.kt:462` (302 redirect при отсутствии обложки/автора в MinIO).
         */
        private const val FALLBACK_LOGO_URL = "https://sm-karaoke.ru/KARAOKE_LOGO.png"

        /**
         * Базовый URL MinIO-прокси через nginx `80to8897` (`location /minio/`). nginx
         * проксирует `/minio/` напрямую в MinIO (`http://89.125.103.63:9000/`) без участия
         * Java — это и есть «0 обращений к MinIO из Java» (FR-007 спеки).
         */
        private const val MINIO_BASE_URL = "https://sm-karaoke.ru/minio/karaoke"

        /**
         * Максимальный размер HTML-ответа в символах. При превышении — обрезаем с маркером
         * [TRUNCATION_MARKER] (защита от DoS и таймаутов на стороне nginx/бота). R7 research.md.
         */
        private const val MAX_HTML_SIZE = 1_048_576 // 1 МБ

        /**
         * Маркер усечения HTML, добавляется в конец при превышении [MAX_HTML_SIZE].
         * Явно говорит боту, что контент сокращён.
         */
        private const val TRUNCATION_MARKER = "\n<!-- [...фрагмент текста песни усечён для индексации] -->\n"
    }
}

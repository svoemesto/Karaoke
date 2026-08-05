package com.svoemesto.karaokeweb.controllers

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
 * Динамический HTML-ответ для VK-парсера при обходе `/song?id=NNN`.
 *
 * Раньше (до 02.08.2026) для бота возвращался «голый» HTML с `<title>` и видимым `<img>` —
 * именно в этом формате сниппет формировался VK. Попытка заменить на стандартные Open Graph
 * `<meta property="og:…">`-теги не дала сниппета в постах автоматической публикации
 * (см. .specify/memory/feature-121-vk-news-auto-publish.md, секция «Превью для
 * автопубликации бота»).
 *
 * Поэтому возвращаем «голый» HTML c `<img src="…">` в `<body>` — это формат, при котором VK
 * видит первую картинку и формирует сниппет. Параметр `?id=NNN` — id песни.
 *
 * Endpoint проксируется nginx'ом на проде через правило `location /song { if
 * ($http_user_agent ~* "vkShare|...") rewrite ^/song(\?.*)?$ /api/public/og/song$1 last; }`
 * (см. `deploy/web-server-deploy/deploy/80to8897`). Боты VK/Telegram/etc идут сюда
 * (OG-картинка для сниппета в мессенджере/посте); обычные браузеры идут на SPA Vue
 * (port 7907), Vue Router отрендерит `SongView`.
 *
 * **Критичный баг (Pass 35, 2026-08-05):** до фикса в `80to8897` (см. коммит и PR для
 * `144-homepage-latest-news` follow-up) nginx проксировал **все** запросы `/song` на
 * этот endpoint независимо от User-Agent. Поэтому прямой URL
 * `https://sm-karaoke.ru/song?id=NNN` (из мессенджера, поста VK, или правого клика
 * «открыть в новой вкладке») показывал только картинку — пользователь не мог попасть
 * на полноценную страницу песни. После фикса nginx раздвоен по User-Agent:
 * боты → сюда, браузеры → SPA.
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
            com.svoemesto.karaokeapp.model.Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return htmlWithError("Песня не найдена: id=$id")

        val author = song.author.ifBlank { "Неизвестный автор" }
        val songName = song.songName.ifBlank { "Без названия" }
        // Формат title, на котором сниппет VK-платформы работал раньше (★ ♫ ★).
        val title = "$songName ★♫★ $author"
        // URL картинки-превью. Генерируется динамически в PublicApiController.songVkImage
        // по картинкам альбома+автора+названия из MinIO (см. createVKLinkPictureWeb в
        // karaoke-app/.../UtilsPictures.kt:136). Размер 537×240 PNG (исходный, как в
        // рабочей версии до 02.08.2026).
        val imageUrl = "https://sm-karaoke.ru/api/public/song-vk-image/$id"
        return buildBareHtmlForVK(title = title, imageUrl = imageUrl)
    }

    private fun htmlWithError(message: String): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>OG error</title></head>" +
            "<body style=\"font-family:sans-serif;padding:40px\">" +
            "<h1>❌ Ошибка</h1><p>$message</p>" +
            "</body></html>"

    /**
     * «Голый» HTML для VK-парсера — тот же формат, что работал до 02.08.2026. Без
     * `<meta property="og:*">`-тегов, только видимые `<title>` и `<img src="…">`. VK
     * формирует сниппет именно по первой видимой картинке в HTML.
     */
    private fun buildBareHtmlForVK(
        title: String,
        imageUrl: String,
    ): String =
        "<!doctype html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>" + escape(title) + "</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <img src=\"" + escape(imageUrl) + "\" style=\"width: 537px; display: block; margin: auto\" />\n" +
            "</body>"

    private fun escape(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

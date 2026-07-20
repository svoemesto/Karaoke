package com.svoemesto.karaokeweb.dto

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Плитка автора для сетки выбора автора в «Закромах» (karaoke-public).
 * URL превью строится детерминированно из имени автора — по той же формуле, что
 * `Pictures.storageFileNamePreview` для картинки-автора (`"$author/$author.preview.author.png"`).
 * Если у автора нет картинки, `authorPictureUrl` всё равно валиден, но эндпоинт превью вернёт
 * пусто/404, а фронтенд по `@error` спрячет `<img>` — плитка останется с одним именем.
 */
data class AuthorTilePublicDto(
    val author: String,
    val authorPictureUrl: String,
    val songCount: Long,
) {
    companion object {
        fun fromAuthorName(
            author: String,
            songCount: Long,
        ): AuthorTilePublicDto {
            val previewFileName = "$author/$author.preview.author.png"
            return AuthorTilePublicDto(
                author = author,
                authorPictureUrl = "/api/public/picture?file=${URLEncoder.encode(previewFileName, StandardCharsets.UTF_8)}",
                songCount = songCount,
            )
        }
    }
}

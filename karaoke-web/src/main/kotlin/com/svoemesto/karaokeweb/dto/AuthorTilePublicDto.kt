package com.svoemesto.karaokeweb.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Плитка автора для сетки выбора автора в «Закромах» (karaoke-public).
 * URL превью строится детерминированно из имени автора — по той же формуле, что
 * `Pictures.storageFileNamePreview` для картинки-автора (`"$author/$author.preview.author.png"`).
 *
 * **MVP-фикс** (FR-001, FR-002, US1): раньше `authorPictureUrl` указывал на
 * `/api/public/picture?file=...`, что приводило к 200+ редиректам через Spring-контроллер
 * (Pass 50 / 2026-07-29, `docs/architecture-notes.md`). Теперь URL указывает напрямую на
 * MinIO через nginx-`/minio/karaoke/...` location, минуя Spring (FR-002).
 *
 * Legacy endpoint `/api/public/picture?file=...` продолжает работать как 302-redirect (FR-001)
 * — старый код, deep-link'и и тесты не ломаются. nginx отдаёт MinIO с
 * `Cache-Control: public, max-age=86400` (FR-003, T024).
 *
 * Если у автора нет картинки, URL всё равно валиден, но nginx/MinIO вернёт 404 (FR-005),
 * а фронтенд по `@error` спрячет `<img>` — плитка останется с одним именем.
 *
 * @see specs/187-site-traffic-anomaly-investigation (spec 187)
 * @see archive/docs/features/site-traffic-resilience.md (FR-001/002/003)
 */
data class AuthorTilePublicDto(
    val author: String,
    val authorPictureUrl: String,
    val songCount: Long,
    /**
     * Флаг "По спецзаказу" — автор с 1-2 песнями, не вся дискография.
     *
     * @JsonProperty нужен, потому что Jackson по умолчанию отбрасывает префикс `is` для boolean-полей.
     */
    @get:JsonProperty("isSpecialOrder")
    val isSpecialOrder: Boolean = false,
) {
    companion object {
        /**
         * Bucket `karaoke` — основной публичный bucket для превью (см. SPEC.md FR-002, C1).
         * URL-encoding: `URLEncoder.encode` заменяет пробелы на `+`, а nginx ожидает `%20`
         * для совместимости с обычной семантикой URL. Поэтому дополнительно заменяем `+` на `%20`.
         */
        private const val BUCKET = "karaoke"

        fun fromAuthorName(
            author: String,
            songCount: Long,
            isSpecialOrder: Boolean = false,
        ): AuthorTilePublicDto {
            val previewFileName = "$author/$author.preview.author.png"
            val encoded = URLEncoder.encode(previewFileName, StandardCharsets.UTF_8).replace("+", "%20")
            return AuthorTilePublicDto(
                author = author,
                // FR-002: прямой URL на MinIO через nginx — без Spring-контроллера.
                authorPictureUrl = "/minio/$BUCKET/$encoded",
                songCount = songCount,
                isSpecialOrder = isSpecialOrder,
            )
        }
    }
}

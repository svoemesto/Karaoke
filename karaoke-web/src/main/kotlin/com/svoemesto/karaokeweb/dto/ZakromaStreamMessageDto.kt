package com.svoemesto.karaokeweb.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * NDJSON-wrapper для chunked-stream endpoint'а `/api/public/zakroma/stream`.
 *
 * Каждое сообщение сериализуется на отдельной строке `\\n`. Поля помечены
 * `@JsonInclude(NON_NULL)`, чтобы null-поля не попадали в JSON — клиент
 * определяет «тип» по полю `type`, остальные поля зависят от него.
 *
 * **Пять типов сообщений** (FR-BE-003):
 * - `meta` — первое сообщение: `{type, author, expectedCount}`.
 *   `expectedCount` = `Song.loadAuthorSongCounts(author, onlyPublished)` —
 *   **MUST** быть идентичной формуле, что используется на тайле автора.
 *   BR-354 (аудит #336, PKG-S3-28): дополнительно несут авторские поля
 *   (`authorDescription`, `authorShortDescription`, `authorWarning`, `authorSkip`) —
 *   раньше они терялись при переходе страницы песен автора на NDJSON-стрим, и блок
 *   автора (описание, предупреждение, бейдж SKIP) показывался пустым.
 *   Поля НЕОБЯЗАТЕЛЬНЫ: старые клиенты их просто не читают.
 * - `album` — `{type, album: ZakromaAlbumMetaPublicDto}`. **БЕЗ** `albumSettings`
 *   (out of scope для NDJSON; см. [ZakromaAlbumMetaPublicDto]).
 * - `song` — `{type, song: ZakromaAlbumSongPublicDto}`. Альбом определяется
 *   **порядком сообщений**: `song` принадлежит последнему пришедшему `album`
 *   (sequential grouping; `albumId` в протоколе НЕ используется).
 * - `done` — `{type, actualCount}`. `actualCount` — число реально
 *   отправленных песен (для sanity check на фронте).
 * - `error` — `{type, message}`. Отправляется при ошибке SQL/IO, HTTP 200
 *   (НЕ 500 — иначе fetch не сможет парсить тело).
 *
 * @see archive/docs/features/zakroma-stream-progress.md
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ZakromaStreamMessageDto(
    val type: String,
    val author: String? = null,
    val expectedCount: Long? = null,
    // BR-354: авторские поля страницы песен автора (см. ZakromaPublicDto).
    val authorDescription: String? = null,
    val authorShortDescription: String? = null,
    val authorWarning: String? = null,
    val authorSkip: Boolean? = null,
    val album: ZakromaAlbumMetaPublicDto? = null,
    val song: ZakromaAlbumSongPublicDto? = null,
    val actualCount: Long? = null,
    val message: String? = null,
) {
    companion object {
        const val TYPE_META: String = "meta"
        const val TYPE_ALBUM: String = "album"
        const val TYPE_SONG: String = "song"
        const val TYPE_DONE: String = "done"
        const val TYPE_ERROR: String = "error"

        fun meta(
            author: String,
            expectedCount: Long,
            // BR-354: авторские поля — nullable, чтобы в JSON попадали только заполненные.
            authorDescription: String? = null,
            authorShortDescription: String? = null,
            authorWarning: String? = null,
            authorSkip: Boolean? = null,
        ): ZakromaStreamMessageDto =
            ZakromaStreamMessageDto(
                type = TYPE_META,
                author = author,
                expectedCount = expectedCount,
                authorDescription = authorDescription,
                authorShortDescription = authorShortDescription,
                authorWarning = authorWarning,
                authorSkip = authorSkip,
            )

        fun album(
            album: ZakromaAlbumMetaPublicDto,
        ): ZakromaStreamMessageDto =
            ZakromaStreamMessageDto(
                type = TYPE_ALBUM,
                album = album,
            )

        fun song(
            song: ZakromaAlbumSongPublicDto,
        ): ZakromaStreamMessageDto =
            ZakromaStreamMessageDto(
                type = TYPE_SONG,
                song = song,
            )

        fun done(
            actualCount: Long,
        ): ZakromaStreamMessageDto =
            ZakromaStreamMessageDto(
                type = TYPE_DONE,
                actualCount = actualCount,
            )

        fun error(
            message: String,
        ): ZakromaStreamMessageDto =
            ZakromaStreamMessageDto(
                type = TYPE_ERROR,
                message = message,
            )
    }
}

package com.svoemesto.karaokeweb.dto

import com.svoemesto.karaokeapp.model.ZakromaAlbum
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Лёгкий DTO метаданных альбома для NDJSON chunked-stream (FR-BE-003).
 *
 * Содержит те же поля, что и [ZakromaAlbumPublicDto], **за исключением**
 * `albumSettings` (списка песен). Используется в сообщении
 * `{"type":"album",...}` — песни приходят отдельными `{"type":"song",...}`
 * сообщениями и группируются по порядку (последний `album` = владелец
 * текущей `song`).
 *
 * FR-BE-003: out of scope — `albumSettings` в NDJSON-стриме **не передаются**;
 * фронт собирает свою статистику альбома из полученных `song`-сообщений.
 *
 * @see docs/features/zakroma-stream-progress.md
 */
data class ZakromaAlbumMetaPublicDto(
    val albumName: String,
    val year: Long,
    val albumPictureUrl: String,
    // "studio"/"live"/"compilation"/"bootleg" (AlbumType.dbValue) — "studio" по умолчанию, если
    // песни альбома ещё не привязаны к реальному Album (specs/011-album-song-rename).
    val albumType: String,
    // Каноническая русская подпись типа (AlbumType.description), показывается под названием
    // альбома — единый источник правды, фронт не хранит собственную RU-мапу (FR-018).
    val albumTypeLabel: String = "",
    // Описание/короткое описание/предупреждение альбома (specs/012-entity-description-fields).
    val description: String = "",
    val shortDescription: String = "",
    val warning: String = "",
) {
    companion object {
        fun fromAlbum(album: ZakromaAlbum): ZakromaAlbumMetaPublicDto =
            ZakromaAlbumMetaPublicDto(
                albumName = album.albumName,
                year = album.year,
                albumPictureUrl =
                    if (album.picturePreviewFileName.isNotEmpty()) {
                        "/api/public/picture?file=${URLEncoder.encode(album.picturePreviewFileName, StandardCharsets.UTF_8)}"
                    } else {
                        ""
                    },
                albumType = album.albumType,
                albumTypeLabel = album.albumTypeLabel,
                description = album.description,
                shortDescription = album.shortDescription,
                warning = album.warning,
            )
    }
}

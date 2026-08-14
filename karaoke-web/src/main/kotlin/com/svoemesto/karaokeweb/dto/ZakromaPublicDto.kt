package com.svoemesto.karaokeweb.dto

import com.svoemesto.karaokeapp.model.AlbumType
import com.svoemesto.karaokeapp.model.Zakroma
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * DTO для zakroma album settings public: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class ZakromaAlbumSongPublicDto(
    val id: Long,
    val track: Long,
    val songName: String,
    val onAir: Boolean,
    val datePublish: String,
    // null, если дата эфира вообще не назначена — см. Zakroma.kt ZakromaAlbumSong.airTimestamp
    val airTimestamp: Long?,
    val songSubscriptionAvailable: Boolean,
    // specs/143-song-free-access-window
    val alwaysFree: Boolean,
    val freelyAvailableNow: Boolean,
    val freeAccessWindowEndText: String?,
)

/**
 * DTO для zakroma album public: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class ZakromaAlbumPublicDto(
    val albumName: String,
    val year: Long,
    val albumPictureUrl: String,
    val albumSettings: List<ZakromaAlbumSongPublicDto>,
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
)

/**
 * Сводка по одному типу альбома для переключателя "по группам"/кнопок быстрого фильтра на
 * Закромах — только типы, для которых у автора есть хотя бы один альбом (FR-025/FR-026).
 *
 * @see specs/012-entity-description-fields/spec.md
 */
data class AlbumTypeSummaryDto(
    val dbValue: String,
    val groupLabel: String,
    val filterLabel: String,
    val count: Int,
)

/**
 * DTO для zakroma public: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class ZakromaPublicDto(
    val author: String,
    val authorPictureUrl: String,
    val albums: List<ZakromaAlbumPublicDto>,
    // Описание/короткое описание/предупреждение автора (specs/012-entity-description-fields).
    val authorDescription: String = "",
    val authorShortDescription: String = "",
    val authorWarning: String = "",
    // Счётчики альбомов по типу для группировки/быстрых фильтров (FR-023/024/025/026), в порядке
    // AlbumType.ZAKROMA_GROUP_ORDER, только типы с count > 0.
    val albumTypeCounts: List<AlbumTypeSummaryDto> = emptyList(),
) {
    companion object {
        fun fromZakroma(list: List<Zakroma>): List<ZakromaPublicDto> =
            list.map { zak ->
                ZakromaPublicDto(
                    author = zak.author,
                    authorPictureUrl =
                        if (zak.picturePreviewFileName.isNotEmpty()) {
                            "/api/public/picture?file=${URLEncoder.encode(zak.picturePreviewFileName, StandardCharsets.UTF_8)}"
                        } else {
                            ""
                        },
                    authorDescription = zak.authorDescription,
                    authorShortDescription = zak.authorShortDescription,
                    authorWarning = zak.authorWarning,
                    albumTypeCounts =
                        zak.albums
                            .groupingBy { it.albumType }
                            .eachCount()
                            .let { counts ->
                                AlbumType.ZAKROMA_GROUP_ORDER.mapNotNull { type ->
                                    val count = counts[type.dbValue] ?: 0
                                    if (count <= 0) {
                                        null
                                    } else {
                                        AlbumTypeSummaryDto(
                                            dbValue = type.dbValue,
                                            groupLabel = type.groupLabel,
                                            filterLabel = type.filterLabel,
                                            count = count,
                                        )
                                    }
                                }
                            },
                    albums =
                        zak.albums.map { alb ->
                            ZakromaAlbumPublicDto(
                                albumName = alb.albumName,
                                year = alb.year,
                                albumPictureUrl =
                                    if (alb.picturePreviewFileName.isNotEmpty()) {
                                        "/api/public/picture?file=${URLEncoder.encode(alb.picturePreviewFileName, StandardCharsets.UTF_8)}"
                                    } else {
                                        ""
                                    },
                                albumSettings =
                                    alb.albumSongs.map { s ->
                                        ZakromaAlbumSongPublicDto(
                                            id = s.id,
                                            track = s.track,
                                            songName = s.songName,
                                            onAir = s.onAir,
                                            datePublish = s.datePublish,
                                            airTimestamp = s.airTimestamp,
                                            songSubscriptionAvailable = s.songSubscriptionAvailable,
                                            alwaysFree = s.alwaysFree,
                                            freelyAvailableNow = s.freelyAvailableNow,
                                            freeAccessWindowEndText = s.freeAccessWindowEndText,
                                        )
                                    },
                                albumType = alb.albumType,
                                albumTypeLabel = alb.albumTypeLabel,
                                description = alb.description,
                                shortDescription = alb.shortDescription,
                                warning = alb.warning,
                            )
                        },
                )
            }
    }
}

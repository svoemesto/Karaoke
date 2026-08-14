package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

// Булево поле muted — без префикса `is` (Jackson bean convention, см. SitePlaylistDto). Поля
// songName/author/album/year — не БД-поля, заполняются контроллером (метаданные песни для UI).

/**
 * DTO для site playlist item: сериализуемое представление для API/UI.
 *
 * Поля `albumPictureUrl` и `authorPictureUrl` (FR-006, см. spec.md и
 * docs/features/playlist-play-button-and-stems-cancel.md) — прямые URL на MinIO через nginx-прокси,
 * формируются контроллером PublicPlaylistController по предсказуемым storage-ключам. Пустая строка
 * означает «файла нет в MinIO» — фронт по `@error` показывает CSS-плейсхолдер (см. Acceptance US2.2/3).
 *
 * @see docs/features/dual-db-sync.md
 */
data class SitePlaylistItemDto(
    val id: Long = 0,
    val playlistId: Long = 0,
    val songId: Long = 0,
    val position: Long = 0,
    val muted: Boolean = false,
    val songName: String = "",
    val author: String = "",
    val album: String = "",
    val year: Long = 0,
    val albumPictureUrl: String = "",
    val authorPictureUrl: String = "",
) : Serializable,
    KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): SitePlaylistItem {
        val entity = SitePlaylistItem(database = database)
        entity.id = id
        entity.playlistId = playlistId
        entity.songId = songId
        entity.position = position
        entity.muted = muted
        entity.albumPictureUrl = albumPictureUrl
        entity.authorPictureUrl = authorPictureUrl
        return entity
    }
}

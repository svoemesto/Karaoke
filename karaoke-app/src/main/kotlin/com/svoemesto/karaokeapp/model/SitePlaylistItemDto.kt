package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

// Булево поле muted — без префикса `is` (Jackson bean convention, см. SitePlaylistDto). Поля
// songName/author/album/year — не БД-поля, заполняются контроллером (метаданные песни для UI).
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
) : Serializable, KaraokeDbTableDto {

    override fun fromDto(database: KaraokeConnection): SitePlaylistItem {
        val entity = SitePlaylistItem(database = database)
        entity.id = id
        entity.playlistId = playlistId
        entity.songId = songId
        entity.position = position
        entity.muted = muted
        return entity
    }
}

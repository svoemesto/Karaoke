package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

// Булевы поля БЕЗ префикса `is` — Jackson сериализует геттер isX() в JSON-ключ "x" (без "is"),
// а фронтенд читает ключ буквально (см. DEVELOPMENT.md про Jackson bean convention). itemsCount —
// не БД-поле, заполняется контроллером для списка плейлистов.

/**
 * DTO для site playlist: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class SitePlaylistDto(
    val id: Long = 0,
    val ownerId: Long = 0,
    val name: String = "",
    val favorites: Boolean = false,
    val sortOrder: Long = 0,
    val continuous: Boolean = true,
    val repeatMode: String = "none",
    val shuffle: Boolean = false,
    val itemsCount: Int = 0,
) : Serializable,
    KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): SitePlaylist {
        val entity = SitePlaylist(database = database)
        entity.id = id
        entity.ownerId = ownerId
        entity.name = name
        entity.isFavorites = favorites
        entity.sortOrder = sortOrder
        entity.continuous = continuous
        entity.repeatMode = repeatMode
        entity.shuffle = shuffle
        return entity
    }
}

package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO для album: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 * @see specs/011-album-song-rename/contracts/api.md
 */
data class AlbumDTO(
    val id: Long,
    val authorId: Long,
    val year: Int,
    val name: String,
    val albumType: String,
    val sortOrder: Int,
) : Serializable,
    Comparable<AlbumDTO>,
    KaraokeDbTableDto {
    override fun compareTo(other: AlbumDTO): Int =
        compareValuesBy(this, other, { it.authorId }, { it.year }, { it.sortOrder }, { it.name })

    override fun isValid(): Boolean = name.isNotBlank() && authorId > 0 && AlbumType.entries.any { it.dbValue == albumType }

    override fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors += "Название альбома не может быть пустым"
        if (authorId <= 0) errors += "Не указан автор альбома"
        if (AlbumType.entries.none { it.dbValue == albumType }) errors += "Недопустимый тип альбома: $albumType"
        return errors
    }

    override fun fromDto(database: KaraokeConnection): Album {
        val entity = Album(database = database)
        entity.id = id
        entity.authorId = authorId
        entity.year = year
        entity.name = name
        entity.albumType = albumType
        entity.sortOrder = sortOrder
        return entity
    }
}

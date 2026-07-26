package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO для song co-author: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 * @see specs/011-album-song-rename/contracts/api.md
 */
data class SongCoAuthorDTO(
    val id: Long,
    val songId: Long,
    val authorId: Long,
) : Serializable,
    Comparable<SongCoAuthorDTO>,
    KaraokeDbTableDto {
    override fun compareTo(other: SongCoAuthorDTO): Int = compareValuesBy(this, other, { it.songId }, { it.authorId })

    override fun isValid(): Boolean = songId > 0 && authorId > 0

    override fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (songId <= 0) errors += "Не указана песня"
        if (authorId <= 0) errors += "Не указан автор"
        return errors
    }

    override fun fromDto(database: KaraokeConnection): SongCoAuthor {
        val entity = SongCoAuthor(database = database)
        entity.id = id
        entity.songId = songId
        entity.authorId = authorId
        return entity
    }
}

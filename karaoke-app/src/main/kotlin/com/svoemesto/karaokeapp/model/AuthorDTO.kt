package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

data class AuthorDTO(
        val id: Long,
        val author: String,
        val ymId: String,
        val vkId: String,
        val lastAlbumYm: String,
        val lastAlbumVk: String,
        val lastAlbumProcessed: String,
        val watched: Boolean,
        val skip: Boolean,
        val haveNewAlbum: Boolean,
        val pictureId: Long = 0,
        val picturePreview: String = ""
): Serializable, Comparable<AuthorDTO>, KaraokeDbTableDto {
    override fun compareTo(other: AuthorDTO): Int {
        return author.compareTo(other.author)
    }

    override fun fromDto(database: KaraokeConnection): Author {
        val entity = Author(database = database)
        entity.id = id
        entity.author = author
        entity.ymId = ymId
        entity.vkId = vkId
        entity.lastAlbumYm = lastAlbumYm
        entity.lastAlbumVk = lastAlbumVk
        entity.lastAlbumProcessed = lastAlbumProcessed
        entity.watched = watched
        entity.skip = skip
        return entity
    }
}


package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.KaraokeStorageServiceImpl
import java.io.Serializable

data class PicturesDTO(
        val id: Long,
        val name: String,
        val preview: String,
        val full: String = "",
        val author: String = "",
        val year: String = "",
        val album: String = "",
        val isAuthorPicture: Boolean = false,
        val isAlbumPicture: Boolean = false,
        val pathToFolder: String = "",
        val fileName: String = "",
): Serializable, Comparable<PicturesDTO>, KaraokeDbTableDto {
    override fun compareTo(other: PicturesDTO): Int {
        return name.compareTo(other.name)
    }

    override fun fromDto(database: KaraokeConnection): Pictures {
        val entity = Pictures(database = database)
        entity.id = id
        entity.name = name
//        entity.preview = preview
        entity.full = full
        return entity
    }
}

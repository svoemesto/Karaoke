package com.svoemesto.karaokeapp.model

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

    override fun isValid(): Boolean {
        return true
    }

    override fun validationErrors(): List<String> {
        TODO("Not yet implemented")
    }

    override fun fromDto(): KaraokeDbTable {
        TODO("Not yet implemented")
    }
}

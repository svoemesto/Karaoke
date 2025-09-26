package com.svoemesto.karaokeapp.model

import java.io.Serializable

data class PicturesDTO(
        val id: Int,
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
): Serializable, Comparable<PicturesDTO> {
    override fun compareTo(other: PicturesDTO): Int {
        return name.compareTo(other.name)
    }
}

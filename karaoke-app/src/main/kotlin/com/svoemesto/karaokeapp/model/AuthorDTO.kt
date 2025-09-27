package com.svoemesto.karaokeapp.model

import java.io.Serializable

data class AuthorDTO(
        val id: Int,
        val author: String,
        val ymId: String,
        val lastAlbumYm: String,
        val lastAlbumProcessed: String,
        val watched: Boolean,
        val skip: Boolean,
        val haveNewAlbum: Boolean,
        val pictureId: Int = 0,
        val picturePreview: String = ""
): Serializable, Comparable<AuthorDTO> {
    override fun compareTo(other: AuthorDTO): Int {
        return author.compareTo(other.author)
    }
}


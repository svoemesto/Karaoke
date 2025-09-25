package com.svoemesto.karaokeapp.model

import java.io.Serializable

data class PicturesDTO(
        val id: Int,
        val name: String,
        val preview: String
): Serializable, Comparable<PicturesDTO> {
    override fun compareTo(other: PicturesDTO): Int {
        return name.compareTo(other.name)
    }
}

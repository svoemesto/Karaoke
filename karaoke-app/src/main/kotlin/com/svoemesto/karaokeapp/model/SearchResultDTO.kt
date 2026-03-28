package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable
import java.sql.Timestamp

class SearchResultDTO(
    val id: Long,
    val searchAsyncId: Long,
    val songId: Long,
    val url: String,
    val html: String,
    val text: String,
    val wrongResult: Boolean,
): Serializable, Comparable<SearchResultDTO>, KaraokeDbTableDto {
    override fun compareTo(other: SearchResultDTO): Int {
        return id.compareTo(other.id)
    }

    override fun fromDto(database: KaraokeConnection): SearchResult {
        val entity = SearchResult(database = database)
        entity.id = id
        entity.searchAsyncId = searchAsyncId
        entity.songId = songId
        entity.url = url
        entity.html = html
        entity.text = text
        entity.wrongResult = wrongResult
        return entity
    }
}
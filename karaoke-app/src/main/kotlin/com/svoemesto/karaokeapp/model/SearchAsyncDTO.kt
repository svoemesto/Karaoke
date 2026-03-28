package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable
import java.sql.Timestamp

class SearchAsyncDTO(
    val id: Long,
    val songId: Long,
    val url: String,
    val iamToken: String,
    val query: String,
    val body: String,
    val responseFormat: String,
    val operationId: String,
    val done: Boolean,
    val rawData: String,
    val lastRequestedAt: Timestamp
): Serializable, Comparable<SearchAsyncDTO>, KaraokeDbTableDto {
    override fun compareTo(other: SearchAsyncDTO): Int {
        return id.compareTo(other.id)
    }

    override fun fromDto(database: KaraokeConnection): SearchAsync {
        val entity = SearchAsync(database = database)
        entity.id = id
        entity.songId = songId
        entity.url = url
        entity.iamToken = iamToken
        entity.query = query
        entity.body = body
        entity.responseFormat = responseFormat
        entity.operationId = operationId
        entity.done = done
        entity.rawData = rawData
        entity.lastRequestedAt = lastRequestedAt
        return entity
    }
}
package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

data class WebEventDTO(
    val id: Long = 0,
    val eventType: String = "",
    val restName: String = "",
    val restParameters: String = "",
    val linkType: String = "",
    val linkName: String = "",
    val songId: Long = 0,
    val songVersion: String = "",
    val lastUpdate: String = "",
    val referer: String = "",
) : Serializable, Comparable<WebEventDTO>, KaraokeDbTableDto {

    override fun compareTo(other: WebEventDTO): Int = lastUpdate.compareTo(other.lastUpdate)

    override fun fromDto(database: KaraokeConnection): WebEvent {
        val entity = WebEvent(database = database)
        entity.id = id
        entity.eventType = eventType
        entity.restName = restName
        entity.restParameters = restParameters
        entity.linkType = linkType
        entity.linkName = linkName
        entity.songId = songId
        entity.songVersion = songVersion
        entity.referer = referer
        return entity
    }
}

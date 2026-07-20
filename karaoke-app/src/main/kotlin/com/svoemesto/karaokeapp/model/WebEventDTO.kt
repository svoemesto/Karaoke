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
    val clientIp: String = "",
    val anonId: String = "",
    val siteUserId: Long = 0,
    val userAgent: String = "",
) : Serializable,
    Comparable<WebEventDTO>,
    KaraokeDbTableDto {
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
        entity.clientIp = clientIp
        entity.anonId = anonId
        entity.siteUserId = siteUserId
        entity.userAgent = userAgent
        return entity
    }
}

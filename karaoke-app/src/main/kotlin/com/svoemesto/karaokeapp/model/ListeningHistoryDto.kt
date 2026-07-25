package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable
import java.sql.Timestamp

/**
 * DTO записи истории прослушиваний для API/UI (в основном для admin sync-просмотра в webvue3).
 *
 * @see specs/009-listening-history/data-model.md
 */
data class ListeningHistoryDto(
    val id: Long = 0,
    val siteUserId: Long = 0,
    val songId: Long = 0,
    val playCount: Long = 1,
    val lastPlayedAt: String = "",
) : Serializable,
    KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): ListeningHistory {
        val entity = ListeningHistory(database = database)
        entity.id = id
        entity.siteUserId = siteUserId
        entity.songId = songId
        entity.playCount = playCount
        entity.lastPlayedAt = if (lastPlayedAt.isNotBlank()) Timestamp.valueOf(lastPlayedAt) else null
        return entity
    }
}

package com.svoemesto.karaokeweb.dto

import com.svoemesto.karaokeapp.model.ListeningHistoryEntry
import java.time.format.DateTimeFormatter

/**
 * Строка «Истории прослушиваний» для API/UI (`GET /api/public/account/history`).
 *
 * @see specs/009-listening-history/contracts/history-api.md
 */
data class HistoryEntryDto(
    val songId: Long,
    val songName: String,
    val songAuthor: String,
    val songAlbum: String,
    val lastPlayed: String,
    val playCount: Int,
) {
    companion object {
        fun fromEntry(entry: ListeningHistoryEntry): HistoryEntryDto =
            HistoryEntryDto(
                songId = entry.songId,
                songName = entry.songName,
                songAuthor = entry.songAuthor,
                songAlbum = entry.songAlbum,
                lastPlayed = entry.lastPlayedAt?.toLocalDateTime()?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "",
                playCount = entry.playCount,
            )
    }
}

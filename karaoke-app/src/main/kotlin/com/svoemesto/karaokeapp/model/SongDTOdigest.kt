package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.HealthReportDTO
import java.io.Serializable
import java.util.*

/**
 * Класс Song DTOdigest.
 *
 * @see archive/docs/features/mlt-generator.md
 * @see archive/docs/features/song-state-colors.md — контракт поля `color` и канонических состояний.
 */
data class SongDTOdigest(
    val id: Long,
    var idPrevious: Long,
    var idNext: Long,
    var idLeft: Long,
    var idRight: Long,
    val idStatus: Long,
    val status: String,
    val tags: String,
    val color: String,
    val songName: String,
    val songNameCensored: String,
    val author: String,
    val album: String,
    val date: String,
    val time: String,
    val timecode: String,
    val ms: Long,
    val dateTimePublish: Date?,
    val year: Long,
    val track: Long,
    val countVoices: Int,
    val firstSongInAlbum: Boolean,
    val flagBoosty: String,
    val flagSponsr: String,
    val flagVk: String,
    val flagDzenLyrics: String,
    val flagDzenKaraoke: String,
    val flagDzenChords: String,
    val flagDzenMelody: String,
    val flagPlayerDemo: String,
    val flagVkLyrics: String,
    val flagVkKaraoke: String,
    val flagVkChords: String,
    val flagVkMelody: String,
    val flagTelegramLyrics: String,
    val flagTelegramKaraoke: String,
    val flagTelegramChords: String,
    val flagTelegramMelody: String,
    val flagPlLyrics: String,
    val flagPlKaraoke: String,
    val flagPlChords: String,
    val flagPlMelody: String,
    val flagMaxLyrics: String,
    val flagMaxKaraoke: String,
    val flagMaxChords: String,
    val flagMaxMelody: String,
    val flagFree: String,
    val processColorPlayerDemo: String,
    val resultVersion: Long,
    val versionBoosty: Int,
    val versionBoostyFiles: Int,
    val versionSponsr: Int,
    val versionDzenLyrics: Int,
    val versionDzenKaraoke: Int,
    val versionDzenChords: Int,
    val versionDzenMelody: Int,
    val versionVkLyrics: Int,
    val versionVkKaraoke: Int,
    val versionVkChords: Int,
    val versionVkMelody: Int,
    val versionTelegramLyrics: Int,
    val versionTelegramKaraoke: Int,
    val versionTelegramChords: Int,
    val versionTelegramMelody: Int,
    val versionPlLyrics: Int,
    val versionPlKaraoke: Int,
    val versionPlChords: Int,
    val versionPlMelody: Int,
    val versionMaxLyrics: Int,
    val versionMaxKaraoke: Int,
    val versionMaxChords: Int,
    val versionMaxMelody: Int,
    val rate: Int,
    val healthReportText: String,
    val healthReportColor: String,
    val healthReportList: List<HealthReportDTO>,
    val formattedTextSong: String,
    val formattedTextTabs: String,
    val formattedTextChords: String,
    val description: String = "",
    val shortDescription: String = "",
    val warning: String = "",
    val rootId: Long,
    val audioParentId: Long,
    val free: Boolean,
    val songType: String,
    val haveSourceText: Boolean,
    val albumId: Long,
    val albumName: String,
    // Фаза 2 автопубликации (specs/113-telegram-demo-publish): для badge в SongsTable —
    // заполнен ли message_id демо-версии в Telegram. Пусто = не опубликовано.
    val idTelegramDemo: String = "",
) : Serializable,
    Comparable<SongDTOdigest> {
    private val sortString: String get() {
//        return listOf(
//                author, year.toString(), album, "%3d".format(track)
//            ).joinToString(" - ")
        return if (dateTimePublish == null) {
            listOf(
                author,
                year.toString(),
                album,
                "%3d".format(track),
            ).joinToString(" - ")
        } else {
            "%15d".format(dateTimePublish.time)
        }
    }

    override fun compareTo(other: SongDTOdigest): Int = sortString.compareTo(other.sortString)
}

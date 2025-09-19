package com.svoemesto.karaokeapp.model

import java.io.Serializable
import java.util.*

data class SettingsDTOdigest(
        val id: Long,
        var idPrevious: Long,
        var idNext: Long,
        var idLeft: Long,
        var idRight: Long,
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
        val processColorBoosty: String,
        val processColorSponsr: String,
        val processColorVk: String,
        val processColorMeltLyrics: String,
        val processColorMeltKaraoke: String,
        val processColorMeltChords: String,
        val processColorMeltMelody: String,
        val processColorDzenLyrics: String,
        val processColorDzenKaraoke: String,
        val processColorDzenChords: String,
        val processColorDzenMelody: String,
        val processColorVkLyrics: String,
        val processColorVkKaraoke: String,
        val processColorVkChords: String,
        val processColorVkMelody: String,
        val processColorTelegramLyrics: String,
        val processColorTelegramKaraoke: String,
        val processColorTelegramChords: String,
        val processColorTelegramMelody: String,
        val processColorPlLyrics: String,
        val processColorPlKaraoke: String,
        val processColorPlChords: String,
        val processColorPlMelody: String,
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
        val rate: Int
): Serializable, Comparable<SettingsDTOdigest> {

    private val sortString: String get() {
        return if (dateTimePublish == null) {
            listOf(
                author, year.toString(), album, "%3d".format(track)
            ).joinToString(" - ")
        } else
        {
            "%15d".format(dateTimePublish.time)
        }
    }

    override fun compareTo(other: SettingsDTOdigest): Int {
        return sortString.compareTo(other.sortString)
    }
}
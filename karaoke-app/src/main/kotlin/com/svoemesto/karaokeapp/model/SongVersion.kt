package com.svoemesto.karaokeapp.model

import java.io.Serializable

fun List<ProducerType>.sortedByLevelsDesc(): List<ProducerType> {
    return this.map { Pair(it.level * (-1),it) }.sortedBy { it.first }.map { it.second }.toList()
}
enum class SongVersion(val text: String, val textForDescription: String, val suffix: String, val producers: List<ProducerType>, val producersInMainBin: List<ProducerType>) : Serializable {
    LYRICS(text = "Lyrics", textForDescription = "Song", suffix = " [lyrics]",
        producers = listOf(
            ProducerType.AUDIOSONG,
            ProducerType.BACKGROUND,
            ProducerType.SPLASHSTART,
            ProducerType.BOOSTY,
            ProducerType.HORIZON,
            ProducerType.FLASH,
            ProducerType.PROGRESS,
            ProducerType.FILLCOLORSONGTEXT,
            ProducerType.SONGTEXT,
            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.FILLCOLORSONGTEXTS,
            ProducerType.COUNTERS,
            ProducerType.VOICE,
            ProducerType.VOICES,
            ProducerType.MAINBIN
        ),
        producersInMainBin = listOf(
            ProducerType.AUDIOSONG,
            ProducerType.BACKGROUND,
            ProducerType.SPLASHSTART,
            ProducerType.BOOSTY,
            ProducerType.HORIZON,
            ProducerType.FLASH,
            ProducerType.PROGRESS,
            ProducerType.VOICES,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK
        )
    ),
    KARAOKE(text = "Karaoke", textForDescription = "Accompaniment", suffix = " [karaoke]",
        producers = listOf(
            ProducerType.AUDIOMUSIC,
            ProducerType.AUDIOVOCAL,
            ProducerType.BACKGROUND,
            ProducerType.SPLASHSTART,
            ProducerType.BOOSTY,
            ProducerType.HORIZON,
            ProducerType.FLASH,
            ProducerType.PROGRESS,
            ProducerType.FILLCOLORSONGTEXT,
            ProducerType.SONGTEXT,
            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.FILLCOLORSONGTEXTS,
            ProducerType.COUNTERS,
            ProducerType.VOICE,
            ProducerType.VOICES,
            ProducerType.MAINBIN
        ),
        producersInMainBin = listOf(
            ProducerType.AUDIOMUSIC,
            ProducerType.AUDIOVOCAL,
            ProducerType.BACKGROUND,
            ProducerType.SPLASHSTART,
            ProducerType.BOOSTY,
            ProducerType.HORIZON,
            ProducerType.FLASH,
            ProducerType.PROGRESS,
            ProducerType.VOICES,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK
        )
    ),
    CHORDS(text = "Chords", textForDescription = "Bass + Drums", suffix = " [chords]",
        producers = listOf(
            ProducerType.AUDIOBASS,
            ProducerType.AUDIODRUMS,
            ProducerType.BACKGROUND,
            ProducerType.SPLASHSTART,
            ProducerType.BOOSTY,
            ProducerType.HORIZON,
            ProducerType.FLASH,
            ProducerType.PROGRESS,
            ProducerType.FILLCOLORSONGTEXT,
            ProducerType.SONGTEXT,
            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.BACKCHORDS,
            ProducerType.FINGERBOARD,
            ProducerType.FADERCHORDS,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.FILLCOLORSONGTEXTS,
            ProducerType.COUNTERS,
            ProducerType.VOICE,
            ProducerType.VOICES,
            ProducerType.MAINBIN
        ),
        producersInMainBin = listOf(
            ProducerType.AUDIOBASS,
            ProducerType.AUDIODRUMS,
            ProducerType.BACKGROUND,
            ProducerType.SPLASHSTART,
            ProducerType.BOOSTY,
            ProducerType.HORIZON,
            ProducerType.FLASH,
            ProducerType.PROGRESS,
            ProducerType.VOICES,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK
        )
    )
}
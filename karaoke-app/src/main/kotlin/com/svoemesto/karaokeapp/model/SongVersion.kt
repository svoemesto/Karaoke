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
            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK,
            ProducerType.COUNTERS,
//            ProducerType.FILLCOLORSONGTEXT,
//            ProducerType.FILLCOLORSONGTEXTS,
//            ProducerType.SONGTEXT,

//            ProducerType.SCROLLER,
//            ProducerType.SCROLLERTRACK,
//            ProducerType.SCROLLERS,

            ProducerType.FILL,
            ProducerType.STRING,
            ProducerType.ELEMENT,
            ProducerType.LINE,
            ProducerType.LINETRACK,
            ProducerType.LINES,

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
//            ProducerType.FILLCOLORSONGTEXT,
//            ProducerType.SONGTEXT,
//            ProducerType.FILLCOLORSONGTEXTS,
            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.COUNTERS,
//            ProducerType.SCROLLER,
//            ProducerType.SCROLLERTRACK,
//            ProducerType.SCROLLERS,

            ProducerType.FILL,
            ProducerType.STRING,
            ProducerType.ELEMENT,
            ProducerType.LINE,
            ProducerType.LINETRACK,
            ProducerType.LINES,

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
//            ProducerType.FILLCOLORSONGTEXT,
//            ProducerType.SONGTEXT,
//            ProducerType.FILLCOLORSONGTEXTS,
            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
//            ProducerType.BACKCHORDS,
//            ProducerType.FINGERBOARD,
//            ProducerType.FADERCHORDS,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.COUNTERS,
//            ProducerType.SCROLLER,
//            ProducerType.SCROLLERTRACK,
//            ProducerType.SCROLLERS,

            ProducerType.FILL,
            ProducerType.STRING,
            ProducerType.ELEMENT,
            ProducerType.LINE,
            ProducerType.LINETRACK,
            ProducerType.LINES,

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
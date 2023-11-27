package com.svoemesto.karaokeapp.model

import java.io.Serializable

enum class SongVersion(val text: String, val textForDescription: String, val suffix: String, val producers: List<ProducerType>) : Serializable {
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
            ProducerType.WATERMARK
        ))
}
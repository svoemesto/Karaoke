package com.svoemesto.karaokeapp.model

import java.io.Serializable

fun List<ProducerType>.sortedByLevelsDesc(): List<ProducerType> {
    return this.map { Pair(it.level * (-1),it) }.sortedBy { it.first }.map { it.second }.toList()
}
enum class SongVersion(
    val text: String,
    val textForDescription: String,
    val suffix: String,
    val producers: List<ProducerType>,
    val producersInMainBin: List<ProducerType>,
    val markertypes: List<Markertype>
) : Serializable {
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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
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

            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.COUNTERS,

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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
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

//            ProducerType.FINGERBOARD,
//            ProducerType.FADERCHORDS,
//            ProducerType.BACKCHORDS,



            ProducerType.COUNTER,
            ProducerType.FADERTEXT,
            ProducerType.HEADER,
            ProducerType.WATERMARK,

            ProducerType.COUNTERS,

            ProducerType.CHORDPICTUREIMAGE,
            ProducerType.CHORDPICTUREELEMENT,
            ProducerType.CHORDPICTURELINE,
            ProducerType.CHORDPICTURELINETRACK,
            ProducerType.CHORDPICTURELINES,
            ProducerType.CHORDPICTUREFADER,
            ProducerType.CHORDSBOARD,

            ProducerType.FILL,
            ProducerType.CHORDS,
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

//            ProducerType.FINGERBOARD,
//            ProducerType.FADERCHORDS,
//            ProducerType.BACKCHORDS,
            ProducerType.CHORDSBOARD,

            ProducerType.HEADER,
            ProducerType.WATERMARK
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
            Markertype.CHORD,
            Markertype.EOL_CHORD,
            Markertype.ENDOF_CHORD,
            Markertype.NEWLINE_CHORD,
        )
    ),
    TABS(text = "TABS", textForDescription = "Melody", suffix = " [tabs]",
        producers = listOf(
            ProducerType.AUDIOMUSIC,
            ProducerType.AUDIOVOCAL,
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

            ProducerType.FILL,
            ProducerType.SEPAR,
            ProducerType.MELODYNOTE,
            ProducerType.MELODYTABS,
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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
            Markertype.NOTE,
            Markertype.EOL_NOTE,
            Markertype.ENDOF_NOTE,
            Markertype.NEWLINE_NOTE,
        )
    ),

    LYRICSVK(text = "Lyrics", textForDescription = "Song", suffix = " [lyricsVk]",
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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
        )
    ),
    KARAOKEVK(text = "Karaoke", textForDescription = "Accompaniment", suffix = " [karaokeVk]",
        producers = listOf(
            ProducerType.AUDIOMUSIC,
            ProducerType.AUDIOVOCAL,
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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
        )
    ),
    CHORDSVK(text = "Chords", textForDescription = "Bass + Drums", suffix = " [chordsVk]",
        producers = listOf(
            ProducerType.AUDIOBASS,
            ProducerType.AUDIODRUMS,
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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
            Markertype.CHORD,
            Markertype.EOL_CHORD,
            Markertype.ENDOF_CHORD,
            Markertype.NEWLINE_CHORD,
        )
    ),
    TABSVK(text = "TABS", textForDescription = "Melody", suffix = " [tabsVk]",
        producers = listOf(
            ProducerType.AUDIOMUSIC,
            ProducerType.AUDIOVOCAL,
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

            ProducerType.FILL,
            ProducerType.SEPAR,
            ProducerType.MELODYNOTE,
            ProducerType.MELODYTABS,
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
        ),
        markertypes = listOf(
            Markertype.SYLLABLES,
            Markertype.ENDOFSYLLABLES,
            Markertype.SETTING,
            Markertype.ENDOFLINE,
            Markertype.NEWLINE,
            Markertype.UNMUTE,
            Markertype.NOTE,
            Markertype.EOL_NOTE,
            Markertype.ENDOF_NOTE,
            Markertype.NEWLINE_NOTE,
        )
    ),
}
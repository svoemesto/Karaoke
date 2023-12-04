package com.svoemesto.karaokeapp.model

import java.io.Serializable

//import com.sun.org.apache.xpath.internal.operations.Bool

enum class ProducerType(val text: String, val onlyOne: Boolean, val ids: List<Int>, val isAudio: Boolean, val isVideo: Boolean, val coeffStatic: Int, val coeffVoice:Int, val isSequence: Boolean, val level: Int) : Serializable {
    NONE(level = 0, text = "none", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = false, coeffStatic = 0, coeffVoice = 0, isSequence = false),
    AUDIOVOCAL(level = 1, text = "audiovocal", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOMUSIC(level = 1, text ="audiomusic", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOSONG(level = 1, text ="audiosong", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOBASS(level = 1, text ="audiobass", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIODRUMS(level = 1, text ="audiodrums", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BACKGROUND(level = 1, text ="background", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    HORIZON(level = 1, text ="horizon", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FLASH(level = 1, text ="flash", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    PROGRESS(level = 1, text ="progress", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FILLCOLORSONGTEXT(level = 4, text ="fillcolorsongtext", onlyOne = false, ids = listOf(0,1), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 2, isSequence = false),
    SONGTEXT(level = 3, text ="songtext", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
    COUNTER(level = 4, text ="counter", onlyOne = false, ids = listOf(4,3,2,1,0), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),
    FADERTEXT(level = 1, text ="fadertext", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FADERCHORDS(level = 1, text ="faderchords", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BACKCHORDS(level = 1, text ="backchords", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FINGERBOARD(level = 1, text ="fingerboard", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    HEADER(level = 1, text ="header", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    WATERMARK(level = 1, text ="watermark", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    SPLASHSTART(level = 1, text ="splashstart", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BOOSTY(level = 1, text ="boosty", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),

    MAINBIN(level = 0, text ="mainbin", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
    VOICES(level = 1, text ="voices", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
    VOICE(level = 2, text ="voice", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    COUNTERS(level = 3, text ="counters", onlyOne = false, ids = listOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    FILLCOLORSONGTEXTS(level = 3, text ="fillcolorsongtexts", onlyOne = false, ids = listOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
}


package com.svoemesto.karaokeapp.model

import java.io.Serializable

//import com.sun.org.apache.xpath.internal.operations.Bool

enum class ProducerType(val text: String, val onlyOne: Boolean, val ids: List<Int>, val isAudio: Boolean, val isVideo: Boolean, val coeffStatic: Int, val coeffVoice:Int, val isSequence: Boolean) : Serializable {
    NONE(text = "none", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = false, coeffStatic = 0, coeffVoice = 0, isSequence = false),
    AUDIOVOCAL(text = "audiovocal", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOMUSIC(text ="audiomusic", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOSONG(text ="audiosong", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOBASS(text ="audiobass", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIODRUMS(text ="audiodrums", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BACKGROUND(text ="background", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    HORIZON(text ="horizon", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FLASH(text ="flash", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    PROGRESS(text ="progress", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FILLCOLORSONGTEXT(text ="fillcolorsongtext", onlyOne = false, ids = listOf(0,1), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 2, isSequence = false),
    SONGTEXT(text ="songtext", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
    SONGTEXTLINE(text ="songtextline", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
    COUNTER(text ="counter", onlyOne = false, ids = listOf(4,3,2,1,0), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),
    FADERTEXT(text ="fadertext", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FADERCHORDS(text ="faderchords", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BACKCHORDS(text ="backchords", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FINGERBOARD(text ="fingerboard", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    HEADER(text ="header", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    WATERMARK(text ="watermark", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    SPLASHSTART(text ="splashstart", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BOOSTY(text ="boosty", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),

    MAINBIN(text ="mainbin", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
    BLACKTRACK(text ="mainbin", onlyOne = false, ids = emptyList(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 3, isSequence = false),
    VOICES(text ="voices", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
    VOICE(text ="voice", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    COUNTERS(text ="counters", onlyOne = false, ids = listOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    FILLCOLORSONGTEXTS(text ="fillcolorsongtexts", onlyOne = false, ids = listOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
}


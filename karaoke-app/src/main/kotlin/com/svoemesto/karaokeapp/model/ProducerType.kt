package com.svoemesto.karaokeapp.model

import java.io.Serializable

//import com.sun.org.apache.xpath.internal.operations.Bool

enum class ProducerType(val text: String, val onlyOne: Boolean, val ids: List<Int>, val isAudio: Boolean, val isVideo: Boolean, val coeffStatic: Int, val coeffVoice:Int) : Serializable {
    NONE(text = "none", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = false, coeffStatic = 0, coeffVoice = 0),
    AUDIOVOCAL(text = "audiovocal", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIOMUSIC(text ="audiomusic", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIOSONG(text ="audiosong", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIOBASS(text ="audiobass", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIODRUMS(text ="audiodrums", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    BACKGROUND(text ="background", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    HORIZON(text ="horizon", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FLASH(text ="flash", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    PROGRESS(text ="progress", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FILLCOLORSONGTEXT(text ="fillcolorsongtext", onlyOne = false, ids = listOf(0,1), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 2),
    SONGTEXT(text ="songtext", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1),
    SONGTEXTLINE(text ="songtextline", onlyOne = false, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1),
    COUNTER(text ="counter", onlyOne = false, ids = listOf(4,3,2,1,0), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5),
    FADERTEXT(text ="fadertext", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FADERCHORDS(text ="faderchords", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    BACKCHORDS(text ="backchords", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FINGERBOARD(text ="fingerboard", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    HEADER(text ="header", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    WATERMARK(text ="watermark", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    SPLASHSTART(text ="splashstart", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    BOOSTY(text ="boosty", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),

    MAINBIN(text ="mainbin", onlyOne = true, ids = emptyList(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    BLACKTRACK(text ="mainbin", onlyOne = false, ids = emptyList(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 3),
    VOICES(text ="voices", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    VOICE(text ="voice", onlyOne = true, ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1),
    COUNTERS(text ="counters", onlyOne = false, ids = listOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1),
    FILLCOLORSONGTEXTS(text ="fillcolorsongtexts", onlyOne = false, ids = listOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1),
}


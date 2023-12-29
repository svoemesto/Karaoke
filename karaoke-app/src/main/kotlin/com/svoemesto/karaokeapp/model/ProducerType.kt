package com.svoemesto.karaokeapp.model

import java.io.Serializable

//import com.sun.org.apache.xpath.internal.operations.Bool

enum class ProducerType(
    val text: String,
    val onlyOne: Boolean,
    val ids: MutableList<Int>,
    val isAudio: Boolean,
    val isVideo: Boolean,
    val coeffStatic: Int,
    val coeffVoice:Int,
    val isSequence: Boolean,
    val level: Int,
    val isCalculatedCount: Boolean
) : Serializable {
    NONE(level = 0, isCalculatedCount = false, text = "none", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = false, coeffStatic = 0, coeffVoice = 0, isSequence = false),
    AUDIOVOCAL(level = 1, isCalculatedCount = false, text = "audiovocal", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOMUSIC(level = 1, isCalculatedCount = false, text ="audiomusic", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOSONG(level = 1, isCalculatedCount = false, text ="audiosong", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIOBASS(level = 1, isCalculatedCount = false, text ="audiobass", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    AUDIODRUMS(level = 1, isCalculatedCount = false, text ="audiodrums", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BACKGROUND(level = 1, isCalculatedCount = false, text ="background", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    HORIZON(level = 1, isCalculatedCount = false, text ="horizon", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FLASH(level = 1, isCalculatedCount = false, text ="flash", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    PROGRESS(level = 1, isCalculatedCount = false, text ="progress", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FILLCOLORSONGTEXT(level = 4, isCalculatedCount = false, text ="fillcolorsongtext", onlyOne = false, ids = mutableListOf(0,1), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 2, isSequence = false),
    SONGTEXT(level = 3, isCalculatedCount = false, text ="songtext", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
    COUNTER(level = 4, isCalculatedCount = false, text ="counter", onlyOne = false, ids = mutableListOf(4,3,2,1,0), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),
    SCROLLER(level = 5, isCalculatedCount = true, text ="scroller", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),
    SCROLLERTRACK(level = 4, isCalculatedCount = true, text ="scrollertrack", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),
    FADERTEXT(level = 1, isCalculatedCount = false, text ="fadertext", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FADERCHORDS(level = 1, isCalculatedCount = false, text ="faderchords", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BACKCHORDS(level = 1, isCalculatedCount = false, text ="backchords", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    FINGERBOARD(level = 1, isCalculatedCount = false, text ="fingerboard", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    HEADER(level = 1, isCalculatedCount = false, text ="header", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    WATERMARK(level = 1, isCalculatedCount = false, text ="watermark", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    SPLASHSTART(level = 1, isCalculatedCount = false, text ="splashstart", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
    BOOSTY(level = 1, isCalculatedCount = false, text ="boosty", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),

    MAINBIN(level = 0, isCalculatedCount = false, text ="mainbin", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
    VOICES(level = 1, isCalculatedCount = false, text ="voices", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
    VOICE(level = 2, isCalculatedCount = false, text ="voice", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    COUNTERS(level = 3, isCalculatedCount = false, text ="counters", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    SCROLLERS(level = 3, isCalculatedCount = false, text ="scrollers", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
    FILLCOLORSONGTEXTS(level = 3, isCalculatedCount = false, text ="fillcolorsongtexts", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
}


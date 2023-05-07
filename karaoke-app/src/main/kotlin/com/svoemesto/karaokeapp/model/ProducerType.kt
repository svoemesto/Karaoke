package com.svoemesto.karaokeapp.model

import java.io.Serializable

//import com.sun.org.apache.xpath.internal.operations.Bool

enum class ProducerType(val text: String, val onlyOne: Boolean, val suffixes: List<String>, val ids: List<Int>, val isAudio: Boolean, val isVideo: Boolean, val coeffStatic: Int, val coeffVoice:Int) : Serializable {
    NONE(text = "none", onlyOne = false, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = false, coeffStatic = 0, coeffVoice = 0),
    AUDIOVOCAL(text = "audiovocal", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIOMUSIC(text ="audiomusic", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIOSONG(text ="audiosong", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIOBASS(text ="audiobass", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    AUDIODRUMS(text ="audiodrums", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0),
    BACKGROUND(text ="background", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    HORIZON(text ="horizon", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FLASH(text ="flash", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    PROGRESS(text ="progress", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FILLCOLORSONGTEXT(text ="fillcolorsongtext", onlyOne = false, suffixes = listOf("even", "odd"), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 2),
    SONGTEXT(text ="songtext", onlyOne = false, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1),
    COUNTER(text ="counter", onlyOne = false, suffixes = emptyList(), ids = listOf(4,3,2,1,0), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5),
    FADERTEXT(text ="fadertext", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FADERCHORDS(text ="faderchords", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    BACKCHORDS(text ="backchords", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    FINGERBOARD(text ="fingerboard", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    HEADER(text ="header", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    WATERMARK(text ="watermark", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    SPLASHSTART(text ="splashstart", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0),
    BOOSTY(text ="boosty", onlyOne = true, suffixes = emptyList(), ids = emptyList(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0)
}
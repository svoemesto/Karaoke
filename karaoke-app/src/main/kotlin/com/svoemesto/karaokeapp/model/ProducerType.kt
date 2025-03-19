package com.svoemesto.karaokeapp.model

import java.io.Serializable

//import com.sun.org.apache.xpath.internal.operations.Bool

enum class ProducerType(
    val parent: ProducerType?,
    val text: String,               // Текст
    val onlyOne: Boolean,           // Слой может быть только один
    val ids: MutableList<Int>,      // Айдишники
    val isAudio: Boolean,           // Слой является аудио
    val isVideo: Boolean,           // Слой является видео
    val coeffStatic: Int,           // Статический коэффициэнт
    val coeffVoice:Int,             // Коэффициэнт для войса
    val isSequence: Boolean,        // Слой является последовательностью
    val level: Int,                 // Уровень вложенности
    val isCalculatedCount: Boolean  // Количество слоёв этого типа является вычисляемым
) : Serializable {
    NONE(parent = null, level = 0, isCalculatedCount = false, text = "none", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = false, coeffStatic = 0, coeffVoice = 0, isSequence = false),
    MAINBIN(parent = null, level = 0, isCalculatedCount = false, text ="mainbin", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
        AUDIOVOCAL(parent = MAINBIN, level = 1, isCalculatedCount = false, text = "audiovocal", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        AUDIOMUSIC(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="audiomusic", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        AUDIOSONG(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="audiosong", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        AUDIOBASS(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="audiobass", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        AUDIODRUMS(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="audiodrums", onlyOne = true, ids = mutableListOf(), isAudio = true, isVideo = false, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        BACKGROUND(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="background", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        HORIZON(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="horizon", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        FLASH(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="flash", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        PROGRESS(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="progress", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        FADERTEXT(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="fadertext", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        FADERCHORDS(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="faderchords", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        BACKCHORDS(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="backchords", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        FINGERBOARD(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="fingerboard", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        HEADER(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="header", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        WATERMARK(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="watermark", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        SPLASHSTART(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="splashstart", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        BOOSTY(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="boosty", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = false),
        VOICES(parent = MAINBIN, level = 1, isCalculatedCount = false, text ="voices", onlyOne = true, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 1, coeffVoice = 0, isSequence = true),
            VOICE(parent = VOICES, level = 2, isCalculatedCount = false, text ="voice", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                COUNTERS(parent = VOICE, level = 3, isCalculatedCount = false, text ="counters", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                    COUNTER(parent = COUNTERS, level = 4, isCalculatedCount = false, text ="counter", onlyOne = false, ids = mutableListOf(4,3,2,1,0), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),

                SCROLLERS(parent = VOICE, level = 3, isCalculatedCount = false, text ="scrollers", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                    SCROLLERTRACK(parent = SCROLLERS, level = 4, isCalculatedCount = true, text ="scrollertrack", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),
                        SCROLLER(parent = SCROLLERTRACK, level = 5, isCalculatedCount = true, text ="scroller", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 5, isSequence = false),

                FILLCOLORSONGTEXTS(parent = VOICE, level = 3, isCalculatedCount = false, text ="fillcolorsongtexts", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                    FILLCOLORSONGTEXT(parent = FILLCOLORSONGTEXTS, level = 4, isCalculatedCount = false, text ="fillcolorsongtext", onlyOne = false, ids = mutableListOf(0,1), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 2, isSequence = false),

                SONGTEXT(parent = VOICE, level = 3, isCalculatedCount = false, text ="songtext", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),

                LINES(parent = VOICE, level = 3, isCalculatedCount = false, text ="lines", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                    LINETRACK(parent = LINES, level = 4, isCalculatedCount = true, text ="linetrack", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
                        LINE(parent = LINES, level = 5, isCalculatedCount = true, text ="line", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                            ELEMENT(parent = LINE, level = 6, isCalculatedCount = true, text ="element", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = true),
                                STRING(parent = ELEMENT, level = 7, isCalculatedCount = true, text ="string", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
                                SEPAR(parent = ELEMENT, level = 8, isCalculatedCount = true, text ="separ", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
                                MELODYNOTE(parent = ELEMENT, level = 9, isCalculatedCount = true, text ="melodynote", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),
                                FILL(parent = ELEMENT, level = 10, isCalculatedCount = true, text ="fill", onlyOne = false, ids = mutableListOf(), isAudio = false, isVideo = true, coeffStatic = 0, coeffVoice = 1, isSequence = false),

}
fun ProducerType.childs() : List<ProducerType> = ProducerType.values().filter { it.parent == this }




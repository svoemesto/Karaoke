package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import java.io.Serializable

data class SettingVoiceLine(
    val rootId: Long,
//    var parentVoice: SettingVoice?,
    var lineStartMs: Long, // Начало линии (в мс) - в этот момент линия встала в центр
    var lineEndMs: Long, // Конец линии (с мс) - в этот момент линия начала уходить с центра
//    var elements: List<SettingVoiceLineElement>
) : Serializable {
//    val elementsForMlt: List<SettingVoiceLineElement> get() = elements.filter { element->  element.type != SettingVoiceLineElementTypes.NEWLINE}
//    val elementsForMlt: List<SettingVoiceLineElement> get() = elements

    private val _elements: MutableList<SettingVoiceLineElement> = mutableListOf()
    fun getElements(songVersion: SongVersion): List<SettingVoiceLineElement> {
        val listOfElementTypes = when (songVersion) {
            SongVersion.KARAOKE, SongVersion.KARAOKEVK -> {
                listOf(
                    SettingVoiceLineElementTypes.TEXT,
                    SettingVoiceLineElementTypes.COMMENT,
                    SettingVoiceLineElementTypes.EMPTY,
                    SettingVoiceLineElementTypes.NEWLINE
                )
            }
            SongVersion.LYRICS, SongVersion.LYRICSVK -> {
                listOf(
                    SettingVoiceLineElementTypes.TEXT,
                    SettingVoiceLineElementTypes.COMMENT,
                    SettingVoiceLineElementTypes.EMPTY,
                    SettingVoiceLineElementTypes.NEWLINE
                )
            }
            SongVersion.CHORDS, SongVersion.CHORDSVK -> {
                listOf(
                    SettingVoiceLineElementTypes.TEXT,
                    SettingVoiceLineElementTypes.COMMENT,
                    SettingVoiceLineElementTypes.EMPTY,
                    SettingVoiceLineElementTypes.NEWLINE,
                    SettingVoiceLineElementTypes.ACCORD,
                    SettingVoiceLineElementTypes.NOTE
                )
            }
            SongVersion.MELODY, SongVersion.MELODYVK -> {
                listOf(
                    SettingVoiceLineElementTypes.TEXT,
                    SettingVoiceLineElementTypes.COMMENT,
                    SettingVoiceLineElementTypes.EMPTY,
                    SettingVoiceLineElementTypes.NEWLINE,
                    SettingVoiceLineElementTypes.NOTE
                )
            }
        }
        return _elements.filter { it.type in listOfElementTypes }
    }
    fun addElement(element: SettingVoiceLineElement) {
        _elements.add(element)
        actuateChilds()
    }
    fun addElements(elements: List<SettingVoiceLineElement>) {
        elements.forEach { element -> _elements.add(element) }
        actuateChilds()
    }

    fun actuateChilds() {
        _elements.forEachIndexed { indexElement, element ->
            element.elementId = indexElement
            element.actuateChilds()
        }
    }


    val isNewLine: Boolean get() = _elements.any { element->  element.type == SettingVoiceLineElementTypes.NEWLINE}
    val isEmptyLine: Boolean get() = _elements.any { element->  element.type == SettingVoiceLineElementTypes.EMPTY}
    val isEmptyLineOrComment: Boolean get() = _elements.any { element->  element.type == SettingVoiceLineElementTypes.EMPTY || element.type == SettingVoiceLineElementTypes.COMMENT}
    companion object {
        fun newLine(rootId: Long, timeMs: Long, groupId: Int = 0) : SettingVoiceLine {
            val settingVoiceLine = SettingVoiceLine(
                rootId = rootId,
                lineStartMs = timeMs,
                lineEndMs = timeMs
            )
            val settingVoiceLineElement = SettingVoiceLineElement(
                rootId = rootId,
                type = SettingVoiceLineElementTypes.NEWLINE
            )
            settingVoiceLineElement.groupId = groupId

            val settingVoiceLineElementSyllable = SettingVoiceLineElementSyllable(
                rootId = rootId,
                text = "",
                note = "",
                chord = "",
                stringlad = "",
                syllableStartMs = timeMs,
                syllableEndMs = timeMs,
                previous = null
            )
            settingVoiceLineElement.addSyllable(settingVoiceLineElementSyllable)
            settingVoiceLine.addElement(settingVoiceLineElement)

            return settingVoiceLine

        }
        fun emptyLine(rootId: Long, timeMs: Long, groupId: Int = 0) : SettingVoiceLine {

            val settingVoiceLine = SettingVoiceLine(
                rootId = rootId,
                lineStartMs = timeMs,
                lineEndMs = timeMs
            )
            val settingVoiceLineElement = SettingVoiceLineElement(
                rootId = rootId,
                type = SettingVoiceLineElementTypes.EMPTY
            )
            settingVoiceLineElement.groupId = groupId

            val settingVoiceLineElementSyllable = SettingVoiceLineElementSyllable(
                rootId = rootId,
                text = "",
                note = "",
                chord = "",
                stringlad = "",
                syllableStartMs = timeMs,
                syllableEndMs = timeMs,
                previous = null
            )
            settingVoiceLineElement.addSyllable(settingVoiceLineElementSyllable)
            settingVoiceLine.addElement(settingVoiceLineElement)

            return settingVoiceLine

        }
    }
    fun isScroll(): Boolean = lineEndMs <= lineStartMs // Если время начала совпадает со временем конца - скролим без остановки

    fun lineDurationMs(): Long = lineEndMs - lineStartMs

    fun w(songVersion: SongVersion): Int = getElements(songVersion).maxOfOrNull { it.w() } ?: 0
    fun h(songVersion: SongVersion): Int = getElements(songVersion).sumOf { it.h(songVersion) }

//    fun y(): Int = parentVoice?.linesForMlt()?.filter { it.lineId  < lineId }?.sumOf { h() } ?: 0
    private var _y: Int? = null
    var y: Int
        get() {
            return _y ?: 0
        }
        set(value) {
            _y = value
        }

    private var _textLineHeight: Int? = null
    var textLineHeight: Int
        get() {
            return _textLineHeight ?: 0
        }
        set(value) {
            _textLineHeight = value
        }


    fun textElement(songVersion: SongVersion): SettingVoiceLineElement? = getElements(songVersion).firstOrNull{it.type == SettingVoiceLineElementTypes.TEXT}
    fun commentElement(songVersion: SongVersion): SettingVoiceLineElement? = getElements(songVersion).firstOrNull{it.type == SettingVoiceLineElementTypes.COMMENT}

    fun haveTextElement(songVersion: SongVersion): Boolean = textElement(songVersion) != null
    fun haveCommentElement(songVersion: SongVersion): Boolean = commentElement(songVersion) != null
    fun haveTextElementOrComment(songVersion: SongVersion): Boolean = haveTextElement(songVersion) || haveCommentElement(songVersion)

    var lineId: Int = -1

    fun getText(songVersion: SongVersion, withTimeCode: Boolean = false): String {
        if (getElements(songVersion).any { it.type == SettingVoiceLineElementTypes.NEWLINE }) return "\n"
        textElement(songVersion)?.let {element ->
            val text = element.getSyllables().joinToString("") { it.text }.trim() + "\n"
            val timecode = if (withTimeCode) {
                convertMillisecondsToYoutubeTimecode(lineStartMs + 8000) + " "
            } else ""
            return "$timecode$text"
        }
        return ""
    }

    fun getTextWoEOF(songVersion: SongVersion, withTimeCode: Boolean = false): String {
        if (getElements(songVersion).any { it.type == SettingVoiceLineElementTypes.NEWLINE }) return ""
        textElement(songVersion)?.let {element ->
            val text = element.getSyllables().joinToString("") { it.text }.trim()
            val timecode = if (withTimeCode) {
                convertMillisecondsToYoutubeTimecode(lineStartMs + 8000) + " "
            } else ""
            return "$timecode$text"
        }
        return ""
    }

    fun isCrossing(otherLine: SettingVoiceLine): Boolean {
        return (this.lineDurationMs() + otherLine.lineDurationMs()) > (Math.max(this.lineEndMs, otherLine.lineEndMs) - Math.min(this.lineStartMs, otherLine.lineStartMs))
    }

    private var _indexLineStart: Int? = null
    var indexLineStart: Int
        get() {
            return _indexLineStart ?: 0
        }
        set(value) {
            _indexLineStart = value
            _elements.forEach { it.indexLineStart = value }
        }

    private var _indexLineEnd: Int? = null
    var indexLineEnd: Int
        get() {
            return _indexLineEnd ?: 0
        }
        set(value) {
            _indexLineEnd = value
        }

    private var _countLineTracks: Int? = null
    var countLineTracks: Int
        get() {
            return _countLineTracks ?: 0
        }
        set(value) {
            _countLineTracks = value
        }

    val trackId: Int get() {
        return if (countLineTracks > 0) lineId % countLineTracks else -1
    }

    private var _startVisibleTime: Long? = null
    var startVisibleTime: Long
        get() {
            return _startVisibleTime ?: 0L
        }
        set(value) {
            _startVisibleTime = value
            _elements.forEach { element -> element.deltaStartMs = value }
        }

    private var _endVisibleTime: Long? = null
    var endVisibleTime: Long
        get() {
            return _endVisibleTime ?: -1
        }
        set(value) {
            _endVisibleTime = value
        }

    fun isOnScreen(timeMs: Long = 0L): Boolean {
//        println("isOnScreen calculating to $timeMs")
        return timeMs in startVisibleTime..endVisibleTime
    }

    val onScreenDurationMs: Long get() {
        val deltaStartMs = startVisibleTime // parentVoice?.linesForMlt()?.get(indexLineStart)?.lineStartMs ?: 0
        val deltaEndMs = endVisibleTime // indexLineEnd?.let {
//            parentVoice?.linesForMlt()?.get(it)?.lineStartMs ?: 0
//        } ?: 0
        return lineDurationMs() - deltaStartMs - deltaEndMs
    }

//    fun deltaY(line: SettingVoiceLine): Int {
//        val indA = Integer.min(line.lineId,this.lineId)
//        val indB = Integer.max(line.lineId,this.lineId)
//        val increment = if (line.lineId > this.lineId) 1 else -1
//        return (parentVoice?.getLines()?.filter { it.lineId in (indA + 1) until indB }?.sumOf { h() } ?: 0) * increment
//    }

    private var _transformProperties: List<TransformProperty>? = null
    var transformProperties: List<TransformProperty>
        get() {
            return _transformProperties ?: emptyList()
        }
        set(value) {
            _transformProperties = value
        }

}
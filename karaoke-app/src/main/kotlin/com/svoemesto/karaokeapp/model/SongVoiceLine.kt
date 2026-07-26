package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import java.io.Serializable

/**
 * Класс Setting Voice Line.
 *
 * @see docs/features/mlt-generator.md
 */
data class SongVoiceLine(
    val rootId: Long,
//    var parentVoice: SongVoice?,
    var lineStartMs: Long, // Начало линии (в мс) - в этот момент линия встала в центр
    var lineEndMs: Long, // Конец линии (с мс) - в этот момент линия начала уходить с центра
    var previousLineEndMs: Long? = null,
    var nextLineStartMs: Long? = null,
//    var elements: List<SongVoiceLineElement>
) : Serializable {
//    val elementsForMlt: List<SongVoiceLineElement> get() = elements.filter { element->  element.type != SongVoiceLineElementTypes.NEWLINE}
//    val elementsForMlt: List<SongVoiceLineElement> get() = elements

    private val _elements: MutableList<SongVoiceLineElement> = mutableListOf()

    fun getElements(songVersion: SongVersion): List<SongVoiceLineElement> {
        val listOfElementTypes =
            when (songVersion) {
                SongVersion.KARAOKE -> {
                    listOf(
                        SongVoiceLineElementTypes.TEXT,
                        SongVoiceLineElementTypes.COMMENT,
                        SongVoiceLineElementTypes.EMPTY,
                        SongVoiceLineElementTypes.NEWLINE,
                    )
                }
                SongVersion.LYRICS -> {
                    listOf(
                        SongVoiceLineElementTypes.TEXT,
                        SongVoiceLineElementTypes.COMMENT,
                        SongVoiceLineElementTypes.EMPTY,
                        SongVoiceLineElementTypes.NEWLINE,
                    )
                }
                SongVersion.CHORDS -> {
                    listOf(
                        SongVoiceLineElementTypes.TEXT,
                        SongVoiceLineElementTypes.COMMENT,
                        SongVoiceLineElementTypes.EMPTY,
                        SongVoiceLineElementTypes.NEWLINE,
                        SongVoiceLineElementTypes.ACCORD,
                    )
                }
                SongVersion.TABS -> {
                    listOf(
                        SongVoiceLineElementTypes.TEXT,
                        SongVoiceLineElementTypes.COMMENT,
                        SongVoiceLineElementTypes.EMPTY,
                        SongVoiceLineElementTypes.NEWLINE,
                        SongVoiceLineElementTypes.NOTE,
                    )
                }
            }
        return _elements.filter { it.type in listOfElementTypes }
    }

    fun addElement(element: SongVoiceLineElement) {
        _elements.add(element)
        actuateChilds()
    }

    fun addElements(elements: List<SongVoiceLineElement>) {
        elements.forEach { element -> _elements.add(element) }
        actuateChilds()
    }

    fun actuateChilds() {
        _elements.forEachIndexed { indexElement, element ->
            element.elementId = indexElement
            element.actuateChilds()
        }
    }

//    val isNewLine: Boolean get() = _elements.any { element->  element.type == SongVoiceLineElementTypes.NEWLINE}
    val isEmptyLine: Boolean get() = _elements.any { element -> element.type == SongVoiceLineElementTypes.EMPTY }
    val isEmptyLineOrComment: Boolean get() =
        _elements.any { element ->
            element.type == SongVoiceLineElementTypes.EMPTY ||
                element.type == SongVoiceLineElementTypes.COMMENT
        }

    companion object {
        fun newLine(
            rootId: Long,
            timeMs: Long,
            groupId: Int = 0,
        ): SongVoiceLine {
            val settingVoiceLine =
                SongVoiceLine(
                    rootId = rootId,
                    lineStartMs = timeMs,
                    lineEndMs = timeMs,
                )
            val settingVoiceLineElement =
                SongVoiceLineElement(
                    rootId = rootId,
                    type = SongVoiceLineElementTypes.NEWLINE,
                )
            settingVoiceLineElement.groupId = groupId

            val settingVoiceLineElementSyllable =
                SongVoiceLineElementSyllable(
                    rootId = rootId,
                    text = "",
                    note = "",
                    chord = "",
                    stringLad = "",
                    lockLad = "",
                    syllableStartMs = timeMs,
                    syllableEndMs = timeMs,
                    previous = null,
                )
            settingVoiceLineElement.addSyllable(settingVoiceLineElementSyllable)
            settingVoiceLine.addElement(settingVoiceLineElement)

            return settingVoiceLine
        }

        fun emptyLine(
            rootId: Long,
            timeMs: Long,
            groupId: Int = 0,
        ): SongVoiceLine {
            val settingVoiceLine =
                SongVoiceLine(
                    rootId = rootId,
                    lineStartMs = timeMs,
                    lineEndMs = timeMs,
                )
            val settingVoiceLineElement =
                SongVoiceLineElement(
                    rootId = rootId,
                    type = SongVoiceLineElementTypes.EMPTY,
                )
            settingVoiceLineElement.groupId = groupId

            val settingVoiceLineElementSyllable =
                SongVoiceLineElementSyllable(
                    rootId = rootId,
                    text = "",
                    note = "",
                    chord = "",
                    stringLad = "",
                    lockLad = "",
                    syllableStartMs = timeMs,
                    syllableEndMs = timeMs,
                    previous = null,
                )
            settingVoiceLineElement.addSyllable(settingVoiceLineElementSyllable)
            settingVoiceLine.addElement(settingVoiceLineElement)

            return settingVoiceLine
        }
    }

    @Suppress("unused")
    fun isScroll(): Boolean = lineEndMs <= lineStartMs // Если время начала совпадает со временем конца - скролим без остановки

    fun lineDurationMs(): Long = lineEndMs - lineStartMs

    fun lineDurationWithNeighboursMs(): Long = lineEndWithNeighboursMs() - lineStartWithNeighboursMs()

    fun lineStartWithNeighboursMs(): Long = ((if (previousLineEndMs != null) previousLineEndMs!! else lineStartMs))

    fun lineEndWithNeighboursMs(): Long = (if (nextLineStartMs != null) nextLineStartMs!! else lineEndMs)

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

    fun textElement(songVersion: SongVersion): SongVoiceLineElement? =
        getElements(songVersion).firstOrNull {
            it.type ==
                SongVoiceLineElementTypes.TEXT
        }

    fun commentElement(songVersion: SongVersion): SongVoiceLineElement? =
        getElements(songVersion).firstOrNull {
            it.type ==
                SongVoiceLineElementTypes.COMMENT
        }

    fun haveTextElement(songVersion: SongVersion): Boolean = textElement(songVersion) != null

    fun haveCommentElement(songVersion: SongVersion): Boolean = commentElement(songVersion) != null

    fun haveTextElementOrComment(songVersion: SongVersion): Boolean = haveTextElement(songVersion) || haveCommentElement(songVersion)

    var lineId: Int = -1

    fun getText(
        songVersion: SongVersion,
        withTimeCode: Boolean = false,
    ): String {
        if (getElements(songVersion).any { it.type == SongVoiceLineElementTypes.NEWLINE }) return "\n"
        textElement(songVersion)?.let { element ->
            val text = element.getSyllables().joinToString("") { it.text }.trim() + "\n"
            val timecode =
                if (withTimeCode) {
                    convertMillisecondsToDzenTimecode(lineStartMs + 8000) + " "
                } else {
                    ""
                }
            return "$timecode$text"
        }
        return ""
    }

    @Suppress("unused")
    fun getTextWoEOF(
        songVersion: SongVersion,
        withTimeCode: Boolean = false,
    ): String {
        if (getElements(songVersion).any { it.type == SongVoiceLineElementTypes.NEWLINE }) return ""
        textElement(songVersion)?.let { element ->
            val text = element.getSyllables().joinToString("") { it.text }.trim()
            val timecode =
                if (withTimeCode) {
                    convertMillisecondsToDzenTimecode(lineStartMs + 8000) + " "
                } else {
                    ""
                }
            return "$timecode$text"
        }
        return ""
    }

    fun isCrossing(otherLine: SongVoiceLine): Boolean =
        (this.lineDurationWithNeighboursMs() + otherLine.lineDurationWithNeighboursMs()) > (
            this
                .lineEndWithNeighboursMs()
                .coerceAtLeast(otherLine.lineEndWithNeighboursMs()) -
                this
                    .lineStartWithNeighboursMs()
                    .coerceAtMost(otherLine.lineStartWithNeighboursMs())
        )

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

    @Suppress("unused")
    val onScreenDurationMs: Long get() {
        val deltaStartMs = startVisibleTime // parentVoice?.linesForMlt()?.get(indexLineStart)?.lineStartMs ?: 0
        val deltaEndMs = endVisibleTime // indexLineEnd?.let {
//            parentVoice?.linesForMlt()?.get(it)?.lineStartMs ?: 0
//        } ?: 0
        return lineDurationMs() - deltaStartMs - deltaEndMs
    }

//    fun deltaY(line: SongVoiceLine): Int {
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

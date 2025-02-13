package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import com.svoemesto.karaokeapp.deepCopy
import java.io.Serializable
import kotlin.properties.Delegates

data class SettingVoice(
    val rootId: Long,
//    var lines: List<SettingVoiceLine>
) : Serializable {
//    val linesForMlt: List<SettingVoiceLine> get() = lines.filter { line->  !line.isNewLine }
    private val _lines: MutableList<SettingVoiceLine> = mutableListOf()
    fun getLines(): List<SettingVoiceLine> = _lines
    fun getLastTextLine(songVersion: SongVersion): SettingVoiceLine? =
        _lines.lastOrNull { it.haveTextElement(songVersion) }

    fun getLinesForCounters(songVersion: SongVersion): List<SettingVoiceLine> {
        return getLines().filterIndexed { index, line -> index > 0 && getLines()[index-1].isEmptyLineOrComment && line.haveTextElement(songVersion) }
    }
    fun addline(songVersion: SongVersion, line: SettingVoiceLine) {
        _lines.add(line)
        actuateChilds(songVersion)
    }
    fun addLines(songVersion: SongVersion, lines: List<SettingVoiceLine>) {
        lines.forEach { line -> _lines.add(line) }
        actuateChilds(songVersion)
    }

    fun actuateChilds(songVersion: SongVersion) {

        println("actuateChilds for voice #${voiceId}")

        var lineY = 0
        val textLineHeight = getLines().firstOrNull{it.haveTextElementOrComment(songVersion)}?.h(songVersion) ?: 0
        getLines().forEachIndexed { indexLine, line ->
            val indexLineStart = getIndexLineStart(songVersion, indexLine)
            val indexLineEnd = getIndexLineEnd(songVersion, indexLine)
            val startVisibleTime = if (indexLineStart >= 0) getLines()[indexLineStart].lineStartMs else 0
            val endVisibleTime = if (indexLineEnd >= 0) getLines()[indexLineEnd].lineStartMs else rootSongLengthMs + rootStartSilentOffsetMs
            line.lineId = indexLine
            line.y = lineY
            line.textLineHeight = if (line.h(songVersion) == 0) textLineHeight else line.h(songVersion)
            line.indexLineStart = indexLineStart
            line.indexLineEnd = indexLineEnd
            line.startVisibleTime = startVisibleTime
            line.endVisibleTime = endVisibleTime
            line.actuateChilds()
//            line.countLineTracks = cnt
            line.transformProperties = getLineTransferProperties(songVersion, indexLine)
            lineY += line.h(songVersion)
        }
        val cnt = getLines().maxOf { line -> getLines().filter { it.isOnScreen(line.lineStartMs) }.size }
        countLineTracks = cnt
    }

    private fun getIndexLineStart(songVersion: SongVersion, lineId: Int) : Int {
        if (lineId == 0) return 0
        val frameHeightPx = Karaoke.frameHeightPx
        val textLineHeight = getLines().firstOrNull{it.haveTextElement(songVersion)}?.h(songVersion) ?: 0
        val zeroHeight = frameHeightPx - (frameHeightPx / 2 - textLineHeight / 2)

        var result = lineId - 1
        var tmpH = 0
        for (i in lineId-1 downTo 0) {
            val tmpLine = getLines()[i]
            tmpH += tmpLine.h(songVersion)
            result = i
            if (tmpH > zeroHeight) break
        }
        return result

//        var index = lineId
//        var linesH = 0
//        val prevLines = getLines().filter { it.lineId < lineId }
//        if (prevLines.isEmpty()) return index
//        for (indexLine in (prevLines.size-1) downTo 0 ) {
//            linesH += prevLines[indexLine].h()
//            index = indexLine
//            if (linesH > zeroHeight) break
//        }
//        return index
    }

    private fun getIndexLineEnd(songVersion: SongVersion, lineId: Int) : Int {
        val thisLine = getLines()[lineId]
        val frameHeightPx = Karaoke.frameHeightPx
        val textLineHeight = getLines().firstOrNull{it.haveTextElement(songVersion)}?.h(songVersion) ?: 0
        val zeroHeight = frameHeightPx / 2 - textLineHeight / 2

        var index = lineId
        var linesH = thisLine.h(songVersion)
        val nextLines = getLines().filter { it.lineId > lineId }
        var wasOutOfScreen = false
        // когда линия цикла встанет в позицию zeroHeight текущая линия должна выйти вверху за границы экрана
        // т.е. сумма высот текущей линии + тех что за ней должна быть больше zeroHeight + высота текущей линии. Следующая линия цикла будет та что нам нужна
        for (indexLine in nextLines.indices) {
            linesH += nextLines[indexLine].h(songVersion) // сумма высот линий цикла + высота текущей линии
            index = indexLine + lineId + 2
            if (index > (getLines().size - 1)) return -1
            if (linesH > (zeroHeight + thisLine.h(songVersion))) {
                wasOutOfScreen = true
                break
            }
        }
        return if(wasOutOfScreen) index else -1
    }
    private fun getLineTransferProperties(songVersion: SongVersion, lineId: Int) : List<TransformProperty> {

        println("Генерируем TransferProperties для ${lineId}...")
        val deltaX = longerElementPreviousVoice?.w() ?: 0
        val offsetX = Karaoke.songtextStartPositionXpx
        val offsetY = 5
        val x = deltaX + offsetX * (voiceId + 1)

        val thisLine = getLines()[lineId]

        if (thisLine.getElements(songVersion).isEmpty()) return emptyList()
        val result: MutableList<TransformProperty> = mutableListOf()

        val startIndex = thisLine.indexLineStart
        val endIndex = if (thisLine.indexLineEnd < thisLine.indexLineStart ) getLines().last().lineId else thisLine.indexLineEnd
        if (endIndex < startIndex) return emptyList()

        println("indexLineStart = ${thisLine.indexLineStart}")
        println("indexLineEnd = ${thisLine.indexLineEnd}")

        val frameWidthPx = Karaoke.frameWidthPx
        val frameHeightPx = Karaoke.frameHeightPx
//        val textLineHeight = parentVoice?.textLines()?.firstOrNull()?.h() ?: 0
        val zeroHeight = frameHeightPx - (frameHeightPx / 2 + thisLine.textLineHeight / 2)

        val lines: MutableList<SettingVoiceLine> = mutableListOf()
        for (indexLine in startIndex..endIndex) {
            lines.add(getLines()[indexLine])
        }

        // lines - это линии, в жизненном цикле которые присутствует текущая линия - от первой до последней

        for(lineIndex in lines.indices) {
            val line = lines[lineIndex]
            var deltaY = 0
            if (line.lineId < lineId) {
                // Если линия цикла стоит перед текущей линией
                // Вычисляем сумму высот линий от линии цикла (включительно) до текущей линии (не включая)
                deltaY = lines.filter { it.lineId >= line.lineId && it.lineId < lineId }.sumOf { thisLine.h(songVersion) }
            } else if (line.lineId > lineId) {
                // Если линия цикла стоит после текущей линией
                // Вычисляем сумму высот линий от текущей линии (включительно) до линии цикла (не включая)
                deltaY = - lines.filter { it.lineId < line.lineId && it.lineId >= lineId }.sumOf { thisLine.h(songVersion) }
            } else {
                // Если линия цикла - это текущая линия
                deltaY = 0
            }
//            val deltaY = line.deltaY(this)
            val tpY = deltaY + zeroHeight + offsetY
            if (tpY + line.h(songVersion)*2 < 0) {
                println("break to: lineId = ${lineId}, endIndex = $endIndex, tpY = $tpY")
                break
            } else {
                println("contunue to: lineId = ${lineId}, endIndex = $endIndex, tpY = $tpY")
            }
            val tpStart = TransformProperty(
                time = convertMillisecondsToTimecode(line.lineStartMs - lines.first().lineStartMs),
                x =  x,
                y = tpY,
                w =  Karaoke.frameWidthPx,
                h =  Karaoke.frameHeightPx,
                opacity = 1.0
            )
            val tpEnd = TransformProperty(
                time = convertMillisecondsToTimecode(line.lineEndMs - lines.first().lineStartMs),
                x =  x,
                y = tpY,
                w =  Karaoke.frameWidthPx,
                h =  Karaoke.frameHeightPx,
                opacity = 1.0
            )
            if (result.isNotEmpty() && result.last().time == tpStart.time) {
                result.removeLast()
            }
            result.add(tpStart)
            if (tpStart.toString() != tpEnd.toString() && lineIndex != lines.size-1) {
                result.add(tpEnd)
            }

        }

//        return result.joinToString(";")
        return result

    }

    fun linesForMlt(): List<SettingVoiceLine> = getLines()
    var longerElementPreviousVoice: SettingVoiceLineElement? = null
    fun textLines(songVersion: SongVersion): List<SettingVoiceLine> = getLines().filter { it.haveTextElement(songVersion) }

    fun longerTextElement(songVersion: SongVersion): SettingVoiceLineElement? {
        return textLines(songVersion).maxBy { it.w(songVersion) }.textElement(songVersion)
    }

    var voiceId: Int = -1

//    val voiceId: Int get() {
//        return Settings.loadFromDbById(rootId, WORKING_DATABASE)?.voicesForMlt?.indexOf(this) ?: 0
//    }

    var _countLineTracks: Int? = null

    var countLineTracks: Int
        get() {
                return _countLineTracks ?: 0
            }
        set(value) {
            _countLineTracks = value
            getLines().forEach { it.countLineTracks = value }
        }
//    fun countLineTracks(): Int = linesForMlt.maxOf { line -> linesForMlt.filter { it.isOnScreen(line.lineStartMs) }.size }



    private var _rootSongLengthMs: Long? = null
    var rootSongLengthMs: Long
        get() {
            return _rootSongLengthMs ?: 0
        }
        set(value) {
            _rootSongLengthMs = value
        }

    private var _rootStartSilentOffsetMs: Long? = null
    var rootStartSilentOffsetMs: Long
        get() {
            return _rootStartSilentOffsetMs ?: 0
        }
        set(value) {
            _rootStartSilentOffsetMs = value
        }

    fun linesTransformProperties(): List<TransformProperty> {
        val result: MutableList<TransformProperty> = mutableListOf()
        val frameWidthPx = Karaoke.frameWidthPx
        val frameHeightPx = Karaoke.frameHeightPx
        val deltaX = longerElementPreviousVoice?.w() ?: 0
        val offsetX = Karaoke.songtextStartPositionXpx
        val offsetY = 5
        val x = deltaX + offsetX * (voiceId + 1)
        val y = offsetY

        val trStart = TransformProperty(
            time = convertMillisecondsToTimecode(0),
            x = x,
            y = y,
            w = frameWidthPx,
            h = frameHeightPx,
            opacity = 0.0
        )
        val trFadeIn = TransformProperty(
            time = convertMillisecondsToTimecode(1000),
            x = x,
            y = y,
            w = frameWidthPx,
            h = frameHeightPx,
            opacity = 1.0
        )
        val trFadeOut = TransformProperty(
            time = convertMillisecondsToTimecode(getLines().last().lineEndMs - 1000),
            x = x,
            y = y,
            w = frameWidthPx,
            h = frameHeightPx,
            opacity = 1.0
        )
        val trEnd = TransformProperty(
            time = convertMillisecondsToTimecode(getLines().last().lineEndMs),
            x = x,
            y = y,
            w = frameWidthPx,
            h = frameHeightPx,
            opacity = 0.0
        )
        result.add(trStart)
        result.add(trFadeIn)
        result.add(trFadeOut)
        result.add(trEnd)

        println("linesTransformProperties = ${result.joinToString(";")}")

        return result
    }

    fun getText(songVersion: SongVersion, maxTimeCodes: Int? = null): String {
        var timeCodeCount = maxTimeCodes ?: 0
        return textLines(songVersion).joinToString("") { textLine ->
            val needTimeCode = if (timeCodeCount > 0) {
                timeCodeCount--
                true
            } else false
            textLine.getText(songVersion, needTimeCode)
        }
    }
//    fun fillParents() {
//        getLines().forEach { line->
//            line.getElements().forEach { element->
//                element.getSyllables().forEach { syllable->
//                }
//            }
//        }
//    }
}
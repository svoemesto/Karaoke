package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.convertMillisecondsToTimecode
import java.io.Serializable

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
            line.transformProperties = getLineTransformProperties(songVersion, indexLine)
            lineY += line.h(songVersion)
        }
        countLineTracks = getLines().maxOf { line -> getLines().filter { it.isOnScreen(line.lineStartMs) }.size }

        if (songVersion in listOf(SongVersion.CHORDS, SongVersion.CHORDSVK)) {
            var chordX = 0
            val chordHeight = Karaoke.frameHeightPx / 4
            val chords = getLines()
                .flatMap { line -> line.getElements(songVersion) }
                .flatMap { element -> element.getSyllables() }
                .filter { syllable -> syllable.chord.isNotEmpty() }

            chords.forEachIndexed { indexChord, chord ->
                val indexChordStart = getIndexChordStart(indexChord)
                val indexChordEnd = getIndexChordEnd(indexChord, chords)
                var startChordVisibleTime = if (indexChordStart >= 0) chords[indexChordStart].syllableStartMs else 0
                var deltaMs = 0L
                if (indexChord < 4) {
                    deltaMs = startChordVisibleTime
                    startChordVisibleTime = 0
                }
                val endChordVisibleTime = if (indexChordEnd >= 0) chords[indexChordEnd].syllableStartMs else rootSongLengthMs + rootStartSilentOffsetMs
                chord.chordId = indexChord
                chord.chordX = chordX
                chord.indexChordStart = indexChordStart
                chord.indexChordEnd = indexChordEnd
                chord.startChordVisibleTime = startChordVisibleTime
                chord.endChordVisibleTime = endChordVisibleTime

                chord.transformChordProperties = getChordTransformProperties(indexChord, chords, deltaMs)
                chordX += chordHeight
            }
            countChordPictureTracks = chords.maxOf { chord -> chords.filter { it.chordIsOnScreen(chord.syllableStartMs) }.size }
        }
    }


    private fun getIndexChordStart(chordId: Int) : Int {
        if (chordId == 0) return 0
        val frameWidthPx = Karaoke.frameWidthPx
        val chordWidth = Karaoke.frameHeightPx / 4
        val zeroWidth = frameWidthPx - (frameWidthPx / 2 - chordWidth / 2)
        var result = chordId - 1
        var tmpW = 0
        for (i in chordId-1 downTo 0) {
            tmpW += chordWidth
            result = i
            if (tmpW > zeroWidth) break
        }
        return result
    }
    private fun getIndexChordEnd(chordId: Int, chords: List<SettingVoiceLineElementSyllable>) : Int {
        val frameWidthPx = Karaoke.frameWidthPx
        val chordWidth = Karaoke.frameHeightPx / 4
        val zeroWidth = frameWidthPx / 2 - chordWidth / 2

        var index = chordId
        var chordsW = chordWidth
        var nextChords = chords.filter { it.chordId > chordId }
        var wasOutOfScreen = false

        for (indexChord in nextChords.indices) {
            chordsW += chordWidth // сумма широт аккордов цикла + ширина текущего аккорда
            index = indexChord + chordId + 2
            if (index > (chords.size - 1)) return -1
            if (chordsW > (zeroWidth + chordWidth)) {
                wasOutOfScreen = true
                break
            }
        }
        return if(wasOutOfScreen) index else -1
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

    private fun getChordTransformProperties(chordId: Int, chords: List<SettingVoiceLineElementSyllable>, deltaMs: Long = 0L) : List<TransformProperty> {
        val frameWidthPx = Karaoke.frameWidthPx
        val chordHeight = Karaoke.frameHeightPx / 4
        val chordWidth = chordHeight
        val offsetX = 0 //frameWidthPx / 2 - chordWidth / 2
        val offsetY = 0

        val y = offsetY
        val thisChord = chords[chordId]

        val result: MutableList<TransformProperty> = mutableListOf()
        val startIndex = thisChord.indexChordStart
        val endIndex = if (thisChord.indexChordEnd < thisChord.indexChordStart ) chords.last().chordId else thisChord.indexChordEnd
        if (endIndex < startIndex) return emptyList()

        val zeroWidth = frameWidthPx - (frameWidthPx / 2 + chordWidth / 2)

        val lstChords: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()
        for (indexChord in startIndex..endIndex) {
            lstChords.add(chords[indexChord])
        }
        // lstChords - это аккорды, в жизненном цикле которых присутствует текущий аккорд - от первой до последней

        for(lineIndex in lstChords.indices) {
            val chord = lstChords[lineIndex]
            val nextChord = if (lineIndex + 1 < lstChords.size) lstChords[lineIndex+1] else null
            var deltaX = 0
            if (chord.chordId < chordId) {
                // Если аккорд цикла стоит перед текущим аккордом
                // Вычисляем сумму широт аккордов от аккорда цикла (включительно) до текущего аккорда (не включая)
                deltaX = lstChords.filter { it.chordId >= chord.chordId && it.chordId < chordId }.sumOf { chordWidth }
            } else if (chord.chordId > chordId) {
                // Если аккорд цикла стоит после текущего аккорда
                // Вычисляем сумму широт аккордов от текущего аккорда (включительно) до аккорда цикла (не включая)
                deltaX = - lstChords.filter { it.chordId < chord.chordId && it.chordId >= chordId }.sumOf { chordWidth }
            } else {
                // Если аккорд цикла - это текущий аккорд
                deltaX = 0
            }
            val tpX = deltaX + zeroWidth + offsetX
            if ((tpX + frameWidthPx) < 0) {
                break
            }

            val chordEndMs = if (nextChord == null) chord.syllableEndMs else nextChord.syllableStartMs - 500

            val tpStart = TransformProperty(
                time = convertMillisecondsToTimecode(chord.syllableStartMs - lstChords.first().syllableStartMs + deltaMs),
                x =  tpX,
                y = y,
                w =  Karaoke.frameWidthPx,
                h =  Karaoke.frameHeightPx,
                opacity = 1.0
            )
            val tpEnd = TransformProperty(
                time = convertMillisecondsToTimecode(chordEndMs - lstChords.first().syllableStartMs + deltaMs),
                x =  tpX,
                y = y,
                w =  Karaoke.frameWidthPx,
                h =  Karaoke.frameHeightPx,
                opacity = 1.0
            )
            if (result.isNotEmpty() && result.last().time == tpStart.time) {
                result.removeLast()
            }
            result.add(tpStart)
            if (tpStart.toString() != tpEnd.toString() && lineIndex != lstChords.size-1) {
                result.add(tpEnd)
            }

        }

        return result
    }
    private fun getLineTransformProperties(songVersion: SongVersion, lineId: Int) : List<TransformProperty> {

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
//                deltaY = lines.filter { it.lineId >= line.lineId && it.lineId < lineId }.sumOf { thisLine.h(songVersion) }
                deltaY = lines.filter { it.lineId >= line.lineId && it.lineId < lineId }.sumOf { it.h(songVersion) }
            } else if (line.lineId > lineId) {
                // Если линия цикла стоит после текущей линией
                // Вычисляем сумму высот линий от текущей линии (включительно) до линии цикла (не включая)
                deltaY = - lines.filter { it.lineId < line.lineId && it.lineId >= lineId }.sumOf { it.h(songVersion) }
            } else {
                // Если линия цикла - это текущая линия
                deltaY = 0
            }
//            val deltaY = line.deltaY(this)
            val tpY = deltaY + zeroHeight + offsetY
//            if (tpY + line.h(songVersion)*2 < 0) {
            if ((tpY + frameHeightPx) < 0) {
                break
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

    var _countChordPictureTracks: Int? = null
    var countChordPictureTracks: Int
        get() {
            return _countChordPictureTracks ?: 0
        }
        set(value) {
            _countChordPictureTracks = value
            val chords = getLines()
                .flatMap { line -> line.getElements(SongVersion.CHORDS) }
                .flatMap { element -> element.getSyllables() }
                .filter { syllable -> syllable.chord.isNotEmpty() }
            chords.forEach { it.countChordPictureTracks = value }
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
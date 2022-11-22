package model

import Karaoke
import NOTES_SYMBOLS
import containOnlyThisSymbols
import containThisSymbols
import convertFramesToMilliseconds
import convertFramesToTimecode
import convertMillisecondsToFrames
import convertMillisecondsToTimecode
import convertTimecodeToFrames
import convertTimecodeToMilliseconds
import deleteThisSymbols
import getDurationInMilliseconds
import getFirstVowelIndex
import getListFiles
import getNewTone
import getTextWidthHeightPx
import hashtag
import mlt.MltText
import java.io.File
import kotlin.math.absoluteValue

data class Song(val settings: Settings, val songVersion: SongVersion) {
    fun getOutputFilename(songOutputFile: SongOutputFile, idBluetoothDelay: Boolean): String {
        return "${settings.rootFolder}/done_${if (songOutputFile == SongOutputFile.PROJECT || songOutputFile == SongOutputFile.SUBTITLE) "projects" else "files"}/${settings.year} ${settings.fileName}${songVersion.suffix}${if (idBluetoothDelay) " bluetooth" else ""}.${songOutputFile.extension}"
    }

    fun getDescription(isBluetoothDelay: Boolean): String {

        return "${settings.songName} ★♫★ ${settings.author} ★♫★ ${songVersion.text} ★♫★ ${songVersion.textForDescription}${if (isBluetoothDelay) " ★♫★ video delay ${Karaoke.timeOffsetBluetoothSpeakerMs}ms for bluetooth speakers" else ""}" + "\n" +
                "Версия: ${songVersion.text} (${songVersion.textForDescription})${if (isBluetoothDelay) " с задержкой видео на ${Karaoke.timeOffsetBluetoothSpeakerMs}ms для bluetooth-колонок" else ""}\n" +
                "Композиция: ${settings.songName}\n" +
                "Исполнитель: ${settings.author}\n" +
                "Альбом: ${settings.album}\n" +
                "Год: ${settings.year}\n" +
                (if (getChordDescription() !="") "${getChordDescription()}\n" else "") +
                "https://github.com/svoemesto/Karaoke\n" +
                "${settings.songName.hashtag()} ${settings.author.hashtag()} ${"karaoke".hashtag()} ${"караоке".hashtag()}${if (songVersion == SongVersion.CHORDS) " ${"chords".hashtag()} ${"аккорды".hashtag()}" else ""}\n"

    }

    fun getChordDescription(): String {

        if (songVersion == SongVersion.CHORDS) {
            if (capo == 0) {
                return  "Темп: ${settings.bpm} bpm\n" +
                        "Тональность: ${settings.key}"
            } else {
                return  "Темп: ${settings.bpm} bpm\n" +
                        "Оригинальная тональность: ${settings.key}\n" +
                        "Аккорды и аппликатуры: ${getNewTone(settings.key, capo)}\n" +
                        "Каподастр на ${capo}-м ладу"
            }
        } else {
            return ""
        }

    }

    var endTimecode: String = ""
    var capo: Int = 0
    var voices: MutableList<SongVoice> = mutableListOf()
    val hasChords: Boolean get() = voices.sumOf { it.lines.filter { it.type == SongVoiceLineType.CHORDS }.size } > 0


    init {

        val beatMs = if (settings.ms == 0L) (60000.0 / settings.bpm).toLong() else settings.ms

        val mapFiles = mutableListOf<String>()
        val listFile = getListFiles(settings.rootFolder, ".srt", "${settings.fileName}.kdenlive")
        listFile.sorted().forEach { mapFiles.add(it) }

        val listVoices = mutableListOf<SongVoice>()
        for (voideId in listFile.indices) {

            val chords: MutableList<Chord> = mutableListOf()
            val listLines = mutableListOf<SongVoiceLine>()
            val listChordLines = mutableListOf<SongVoiceLine>()
            var maxLengthLine = 0
            var maxWidthLinePx = 0.0
            var lengthLine = 0
            var widthLinePx = 0.0
            var lineText = ""
            var maxWidthLineText = ""
            val body = File(mapFiles[voideId]).readText(Charsets.UTF_8)

            var group = 0

            var songSubtitles: MutableList<Subtitle> = mutableListOf()

            val blocks = body.split("\\n[\\n]+".toRegex())

            blocks.forEach() { block ->
                val blocklines = block.split("\n")
                val id = if (blocklines.isNotEmpty() && blocklines[0]!= "" ) blocklines[0].toLong() else 0
                val startEnd = if (blocklines.size > 1) blocklines[1] else ""
                var text = if (blocklines.size > 2) blocklines[2] else ""

                if (startEnd != "" && id != 0L) {
                    if (text.uppercase().startsWith("[SETTING]|")) {
                        // Разделяем sub по | в список
                        val settingList = text.split("|")
                        when (settingList[1].uppercase()) {
                            "GROUP" -> group = if (settingList.size > 2) settingList[2].toInt() else 0
                            "CAPO" -> capo = if (settingList.size > 2) settingList[2].toInt() else 0
                            "CHORD" -> {
                                val chordTimecode = startEnd.split(" --> ")[0].replace(",",".")
                                var chordText = "${if (settingList.size > 2) settingList[2] else ""}${if (settingList.size > 3) "|" + settingList[3] else ""}"
                                if (chordText != "") {
                                    chords.add(Chord(chordTimecode, chordText))
                                }
                            }
                            "COMMENT" -> {
                                val commentTimecode = startEnd.split(" --> ")[0].replace(",",".")
                                val commentText = if (settingList.size > 2) settingList[2] else ""
                                if (commentText != "") {
                                    val subtitle = Subtitle(
                                        startTimecode = commentTimecode,
                                        endTimecode = commentTimecode,
                                        mltText = Karaoke.voices[voideId].groups[group].mltText.copy(commentText),
                                        mltTextBefore = Karaoke.voices[voideId].groups[group].mltText.copy(""),
                                        isLineStart = true,
                                        isLineEnd = true,
                                        indexFirstSymbolInLine = 0
                                    )
                                    val symbol = SongVoiceLineSymbol(
                                        start = commentTimecode,
                                        mltText = Karaoke.voices[voideId].groups[group].mltText.copy(commentText),
                                        mltTextBefore = Karaoke.voices[voideId].groups[group].mltText.copy("")
                                    )
                                    listLines.add(
                                        SongVoiceLine(
                                            type = SongVoiceLineType.COMMENTS,
                                            subtitles = mutableListOf(subtitle),
                                            symbols = mutableListOf(symbol),
                                            text = commentText,
                                            group = group,
                                            start = subtitle.startTimecode,
                                            end = subtitle.endTimecode,
                                            durationMs = getDurationInMilliseconds(subtitle.startTimecode, subtitle.endTimecode),
                                            mltText = Karaoke.voices[voideId].groups[group].mltText.copy(commentText)
                                        )
                                    )
                                }
                            }
                            else -> {}
                        }
                    } else {
                        val se = startEnd.split(" --> ")
                        val start = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(se[0]))
                        val end = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(se[1]))
                        val isLineStart = text.startsWith("//")   // Вычисляем признак начала строки
                        val isLineEnd = text.endsWith("\\\\")     // Вычисляем признак конца строки
                        // Удаляем служебные символы из строки и заменяем подчёркивание пробелом
                        text = text
                            .replace("//", "")
                            .replace("\\\\", "")
                            .replace("_", " ")
                        // Создаем объект classes.Subtitle и инициализируем его переменными

                        if (songVersion != SongVersion.CHORDS &&
                            text.containThisSymbols(NOTES_SYMBOLS)
                        ) {
                            text = text.deleteThisSymbols(NOTES_SYMBOLS)
                        }

                        val subtitle = Subtitle(
                            startTimecode = start,
                            endTimecode = end,
                            mltText = Karaoke.voices[voideId].groups[group].mltText.copy(text),
                            mltTextBefore = Karaoke.voices[voideId].groups[group].mltText.copy(lineText),
                            isLineStart = isLineStart,
                            isLineEnd = isLineEnd,
                            indexFirstSymbolInLine = lengthLine
                        )
                        lengthLine += text.length
                        lineText += text

                        // Добавляем этот объект к списку объектов
                        songSubtitles.add(subtitle)

                        if (isLineEnd) {
                            maxLengthLine = Integer.max(maxLengthLine, lengthLine)
                            lengthLine = 0
                            widthLinePx = getTextWidthHeightPx(lineText, Karaoke.voices[voideId].groups[group].mltText.font).first
                            if (maxWidthLinePx < widthLinePx) {
                                maxWidthLinePx = java.lang.Double.max(maxWidthLinePx, widthLinePx)
                                maxWidthLineText = lineText
                            }

                            if (songVersion != SongVersion.CHORDS && lineText.containOnlyThisSymbols(NOTES_SYMBOLS)) {
                                listLines.add(
                                    SongVoiceLine(
                                        type = SongVoiceLineType.EMPTY,
                                        subtitles = mutableListOf(),
                                        text = "",
                                        group = group,
                                        start = songSubtitles.first().startTimecode,
                                        end = songSubtitles.last().endTimecode,
                                        durationMs = getDurationInMilliseconds(songSubtitles.first().startTimecode, songSubtitles.last().endTimecode),
                                        mltText = Karaoke.voices[voideId].groups[group].mltText
                                    )
                                )
                            } else {
                                if (songVersion != SongVersion.CHORDS &&
                                    lineText.containThisSymbols(NOTES_SYMBOLS) &&
                                    !lineText.containOnlyThisSymbols(NOTES_SYMBOLS)
                                ) {
                                    lineText = lineText.deleteThisSymbols(NOTES_SYMBOLS)
                                }
                                listLines.add(
                                    SongVoiceLine(
                                        type = if (lineText.trim() == "") SongVoiceLineType.EMPTY else SongVoiceLineType.TEXT,
                                        subtitles = songSubtitles,
                                        text = lineText,
                                        group = group,
                                        start = songSubtitles.first().startTimecode,
                                        end = songSubtitles.last().endTimecode,
                                        durationMs = getDurationInMilliseconds(songSubtitles.first().startTimecode, songSubtitles.last().endTimecode),
                                        mltText = Karaoke.voices[voideId].groups[group].mltText.copy(lineText)
                                    )
                                )
                            }
                            lineText = ""
                            songSubtitles = mutableListOf()
                        }
                    }
                }

            }

            listLines.forEach { voiceLine ->
                val symbolsChords = mutableListOf<SongVoiceLineSymbol>()
                val subtitlesChords = mutableListOf<Subtitle>()
                chords.forEach { chord ->
                    // Находим субтитр текущей строки, на который попадает аккорд
                    val subtitle = voiceLine.subtitles.firstOrNull {convertTimecodeToMilliseconds(chord.timecode) in convertTimecodeToMilliseconds(it.startTimecode) until convertTimecodeToMilliseconds(it.endTimecode)}
                    // Если такой субтитр найден
                    if (subtitle != null) {
                        val firstVowelIndex = subtitle.mltText.text.getFirstVowelIndex()
                        // Получаем текст строки до этого символа
                        var textBefore = ""
                        for (sub in voiceLine.subtitles) {
                            if (sub == subtitle) {
                                textBefore += subtitle.mltText.text.substring(0,firstVowelIndex)
                                break
                            } else {
                                textBefore += sub.mltText.text
                            }
                        }
                        val mltFontChord = Karaoke.chordsFont.copy(chord.text)
                        subtitlesChords.add(
                            Subtitle(
                                startTimecode = subtitle.startTimecode,
                                endTimecode = subtitle.endTimecode,
                                mltText = subtitle.mltText.copy(chord.text),
                                mltTextBefore = subtitle.mltText.copy(textBefore)
                            )
                        )
                        symbolsChords.add(
                            SongVoiceLineSymbol(
                                start = subtitle.startTimecode,
                                mltText = mltFontChord.copy(chord.text),
                                mltTextBefore = subtitle.mltText.copy(textBefore))
                        )
                    }
                }
                if (symbolsChords.isNotEmpty()) {
                    subtitlesChords.first().isLineStart = true
                    subtitlesChords.last().isLineEnd = true
                    listChordLines.add(
                        SongVoiceLine(
                            type = SongVoiceLineType.CHORDS,
                            subtitles = subtitlesChords,
                            symbols = symbolsChords,
                            text = "",
                            group = 0,
                            start = convertFramesToTimecode(convertTimecodeToFrames(voiceLine.start)-4),
                            end = convertFramesToTimecode(convertTimecodeToFrames(voiceLine.start)-2),
                            durationMs = getDurationInMilliseconds(voiceLine.start, voiceLine.end),
                            mltText = Karaoke.voices[voideId].groups[group].mltText.copy("0")
                        )
                    )
                }
            }

            if (listChordLines.isNotEmpty()) listLines.addAll(listChordLines)

            listVoices.add(
                SongVoice(
                    srtFileBody = body,
                    lines = listLines.sortedBy { convertTimecodeToMilliseconds(it.start)}.toMutableList()
                ))

        }

        voices = listVoices
//        endTimecode = voices.first().lines.last().subtitles.last().endTimecode
        endTimecode = voices.first().lines.last().end
        setMaxLines()

    }

    fun setMaxLines() {

        voices.forEachIndexed { indexVoice, currVoice ->
            val currVoiceTextLines = currVoice.lines.filter { it.type == SongVoiceLineType.TEXT }
            var currVoiceMaxLine = currVoiceTextLines[0]
            var maxLineWidthPx = 0
            if (currVoice != voices.last() ) {
                val nextVoice = voices[indexVoice+1]
                currVoiceTextLines.forEach { currVoiceTextLine ->
                    var currLineWidthPx = currVoiceTextLine.widthLinePx
                    val nextVoiceLines = mutableListOf<SongVoiceLine>()
                    val currVoiceLineStartMs = convertTimecodeToMilliseconds(currVoiceTextLine.start)
                    val currVoiceLineEndMs = convertTimecodeToMilliseconds(currVoiceTextLine.end)
                    val nextVoiceTextLines = nextVoice.lines.filter { it.type == SongVoiceLineType.TEXT }
                    nextVoiceTextLines.forEach { nextVoiceLine ->
                        val nextVoiceLineStartMs = convertTimecodeToMilliseconds(nextVoiceLine.start)
                        val nextVoiceLineEndMs = convertTimecodeToMilliseconds(nextVoiceLine.end)
                        if ((currVoiceLineStartMs in nextVoiceLineStartMs..nextVoiceLineEndMs) ||
                            (nextVoiceLineStartMs in currVoiceLineStartMs..currVoiceLineEndMs)) {
                            nextVoiceLines.add(nextVoiceLine)
                        }
                    }
                    if (nextVoiceLines.size > 0) {
                        val maxLine = nextVoiceLines.maxBy { it.widthLinePx }
                        currLineWidthPx += maxLine.widthLinePx
                        if (currLineWidthPx > maxLineWidthPx) {
                            currVoiceMaxLine = currVoiceTextLine
                            maxLineWidthPx = currLineWidthPx.toInt()
                        }
                    }
                }
            } else {
                currVoiceMaxLine = currVoiceTextLines.maxBy { it.widthLinePx }
            }
            currVoiceMaxLine.isMaxLine = true
        }
    }

}


data class SongVoice(
    val srtFileBody: String = "",
    var lines: MutableList<SongVoiceLine> = mutableListOf()
) {
    val maxDurationMs: Long
    get() {
        return lines.maxOf { it.durationMs }
    }
    val maxCountSymbolsInLine: Int
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT}.first { it.isMaxLine }.text.length
    }
    val maxWidthLinePx: Long
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT}.first { it.isMaxLine }.widthLinePx
    }
    val maxWidthLineText: String
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT}.first { it.isMaxLine }.text
    }

    fun getScreenY(line: SongVoiceLine, time: String, horizonPositionPx: Int): Int {
        var result = 0
        if (line in lines) {
            val indexLine = lines.indexOf(line)
            val timeMs = convertTimecodeToMilliseconds(time)
            val lineContainTime = lines
                .filter { it.startTp != null && it.endTp != null && it.type == SongVoiceLineType.TEXT }.firstOrNull {
                    convertTimecodeToMilliseconds(it.startTp!!.time) <= timeMs && convertTimecodeToMilliseconds(it.endTp!!.time) >= timeMs
                }
            if (lineContainTime != null) {
                // Найдена строка, содержащая время
                // Это значит что в это время эта строка находится в центре экрана horizonPositionPx - voiceLine.hPx
                // Координата текущей строки - разница между текущей и найденной строкой по Y
                result = horizonPositionPx - line.hPx - (line.yPx - lineContainTime.yPx).absoluteValue
            } else {
                // Время находится между строками (или до первой, или после последней)
                val lineBeforeTime = lines
                    .filter { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT}
                    .lastOrNull { convertTimecodeToMilliseconds(it.endTp!!.time) < timeMs }
                val lineAfterTime = lines
                    .filter { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT}
                    .firstOrNull { convertTimecodeToMilliseconds(it.startTp!!.time) > timeMs }
                if (lineBeforeTime == null) {
                    // Если нет "линии до" - значит нужная линия - первая
                    val lineInTime = lines.first { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT}
                    result = horizonPositionPx - line.hPx - (line.yPx - lineInTime.yPx).absoluteValue
                } else if (lineAfterTime == null) {
                    // Если нет "линии после" - значит нужная линия - последняя
                    val lineInTime = lines.last { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT}
                    result = horizonPositionPx - line.hPx - (line.yPx - lineInTime.yPx).absoluteValue
                } else {
                    // Если найдены линии "до" и "после" - надо найти координату, на которую попадает нужное время
                    val timeBefore = convertTimecodeToMilliseconds(lineBeforeTime.endTp!!.time)
                    val timeAfter = convertTimecodeToMilliseconds(lineAfterTime.startTp!!.time)
                    val yBefore = lineBeforeTime.yPx
                    val yAfter = lineAfterTime.yPx
                    val coeff = (timeMs.toDouble() - timeBefore.toDouble()) / (timeAfter.toDouble() - timeBefore.toDouble())
                    val yInTime = yBefore + ((yAfter - yBefore) * coeff).toInt()
                    result = horizonPositionPx - line.hPx - (line.yPx - yInTime).absoluteValue
                }
            }

        }
        return result
    }
}

enum class SongVoiceLineType {
    EMPTY,
    TEXT,
    CHORDS,
    COMMENTS
}
data class SongVoiceLine(
    var type:SongVoiceLineType = SongVoiceLineType.TEXT,
    var subtitles: MutableList<Subtitle> = mutableListOf(),
    var symbols: MutableList<SongVoiceLineSymbol> = mutableListOf(),
    var group: Int = 0,
    var yPx: Int = 0,
    var text: String = "",
    var start: String = "",
    var end: String = "",
    var startTp: TransformProperty? = null,
    var endTp: TransformProperty? = null,
    var isNeedCounter: Boolean = false,
    var isFadeLine: Boolean = false,
    var isMaxLine: Boolean = false,
    var durationMs: Long = 0,
    var mltText: MltText,
) {
    fun getFillTps(voice: SongVoice, horizonPositionPx: Int, opacityFillValue: Double): String {
        val result: MutableList<TransformProperty> = mutableListOf()

        val endTpCurrLineMs = convertTimecodeToMilliseconds(endTp!!.time) // Конец текущей линии. В это время начинается движение линии
        // Следующая линия - текстовая, с не пустым startTp, начало линии позже конца текущей линии
        val nextLine = voice.lines.firstOrNull {
            it.type == SongVoiceLineType.TEXT && it.startTp != null && convertTimecodeToMilliseconds(it.startTp!!.time) > endTpCurrLineMs
        }
        // Начало следующией линии
        val startTpNextLineMs = if (nextLine != null) convertTimecodeToMilliseconds(nextLine.startTp!!.time) else -1
        val endTpNextLineMs = if (nextLine != null) convertTimecodeToMilliseconds(nextLine.endTp!!.time) else -1

        // Тек же надо найти линию (если есть) которая идёт за следующей
        val nextNextLine : SongVoiceLine? = if (nextLine == null) {
            null
        } else {
            voice.lines.firstOrNull {
                it.type == SongVoiceLineType.TEXT && it.startTp != null && convertTimecodeToMilliseconds(it.startTp!!.time) > convertTimecodeToMilliseconds(nextLine.endTp!!.time)
            }
        }

        if (subtitles.isNotEmpty()) {

            // Если в линии есть субтитры, то начало анимации заливки будет за 2 фрейма от начала первого субтитра.
            result.add(
                TransformProperty(
                    time = convertFramesToTimecode(convertTimecodeToFrames(subtitles.first().startTimecode) - 2),
                    x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                    y = voice.getScreenY(this, subtitles.first().startTimecode, horizonPositionPx) + subtitles.first().deltaStartY,
                    w = 1,
                    h = subtitles.first().hPx - subtitles.first().deltaEndH,
                    opacity = 0.0
                )
            )

            // Так же прописываем время начала анимации первого субтитра. Далее будем прописывать для субтитров только время конца анимации
            val subtitle = subtitles.first()
            val timeFromMs = convertTimecodeToMilliseconds(subtitle.startTimecode)
            val timeFromTimecode = convertMillisecondsToTimecode(timeFromMs)
            result.add(
                TransformProperty(
                    time = timeFromTimecode,
                    x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                    y = voice.getScreenY(this, subtitle.startTimecode, horizonPositionPx) + subtitle.deltaStartY,
                    w = Integer.max(subtitle.xStartPx,1),
                    h = subtitle.hPx - subtitle.deltaStartH,
                    opacity = opacityFillValue
                )
            )

        }

        val timeToMsNextNext = if(nextNextLine != null) convertTimecodeToMilliseconds(nextNextLine.endTp!!.time) else 0
        val timeToTimecodeNextNext = convertMillisecondsToTimecode(timeToMsNextNext)

        var yEnd = 0
        // Проходимся по каждому субтиру строки
        subtitles.forEachIndexed { indexSubtitle, subtitle ->

            // Время начала анимации текущего субтитра - время начала субтитра
            val timeFromMs = convertTimecodeToMilliseconds(subtitle.startTimecode)
            // Время конца анимации текущего субтитра - время конца субтитра
            val timeToMs = convertFramesToMilliseconds(convertTimecodeToFrames(subtitle.endTimecode))

            val timeFromTimecode = convertMillisecondsToTimecode(timeFromMs)
            val timeToTimecode = convertMillisecondsToTimecode(timeToMs)

            if (timeToMs <= endTpCurrLineMs) { // Начало и конец анимации титра полностью укладывается во время линии
                // Находим положение текущей линии на момент окончания субтитра
                yEnd = voice.getScreenY(this, subtitle.endTimecode, horizonPositionPx) + subtitle.deltaEndY
                result.add(
                    TransformProperty(
                        time = timeToTimecode,
                        x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                        y = yEnd,
                        w = Integer.max(subtitle.xEndPx,1),
                        h = subtitle.hPx - subtitle.deltaEndH,
                        opacity = opacityFillValue
                    )
                )

            } else { // Анимация титра перекрывает конец времени линии
                // От начала анимации титра до конца времени линии - анимация на уровне линии, пропорционально по времени
                // От конца времени линии до конца анимации титра - на новый уровень, который надо рассчитать

                val coeff = (endTpCurrLineMs.toDouble() - timeFromMs.toDouble()) / (timeToMs.toDouble() - timeFromMs.toDouble())

                // Находим положение текущей линии на момент начала субтитра
                val yStart = voice.getScreenY(this, subtitle.startTimecode, horizonPositionPx) + subtitle.deltaEndY
                // Добавляем заливку до момента начала анимации строки на том же уровне, что пока находится строка пропорционально по ширине
                result.add(
                    TransformProperty(
                        time = convertMillisecondsToTimecode(endTpCurrLineMs),
                        x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                        y = yStart,
                        w = Integer.max(subtitle.xStartPx,1) + (subtitle.wPx * coeff).toInt(),
                        h = subtitle.hPx - subtitle.deltaStartH,
                        opacity = opacityFillValue
                    )
                )

                // Находим положение текущей линии на момент конца субтитра
                yEnd = voice.getScreenY(this, subtitle.endTimecode, horizonPositionPx) + subtitle.deltaEndY
                result.add(
                    TransformProperty(
                        time = timeToTimecode,
                        x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                        y = yEnd,
                        w = Integer.max(subtitle.xEndPx,1),
                        h = subtitle.hPx - subtitle.deltaEndH,
                        opacity = opacityFillValue
                    )
                )
            }
        }

        // Мы закрасили все субтитры, и в переменной yEnd находится позиция строки на момент окончания заливки. Теперь надо фэйдить

        if (subtitles.isNotEmpty()) {
            // По-умолчания время фейда - 1 секунда после окончания последнего субтитра строки
            var endTimeFadeMs = convertTimecodeToMilliseconds(subtitles.last().endTimecode) + 1000
            // Если следующая строка закончится раньше, чем это время, то время фейда - за 2 фрейма от конца следующей строки
            endTimeFadeMs = if (endTpNextLineMs > 0 && endTpNextLineMs < endTimeFadeMs) {
                convertFramesToMilliseconds(convertMillisecondsToFrames(endTpNextLineMs)-2)
            } else {
                endTimeFadeMs
            }

            // Если время окончания фейда закончится до начала следующей строки
            if (startTpNextLineMs >= endTimeFadeMs) {
                // Находим положение текущей линии на момент окончания фэйда
                yEnd = voice.getScreenY(this, convertMillisecondsToTimecode(endTimeFadeMs), horizonPositionPx) + subtitles.last().deltaEndY
                result.add(
                    TransformProperty(
                        time = convertMillisecondsToTimecode(endTimeFadeMs),
                        x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                        y = yEnd,
                        w = Integer.max(subtitles.last().xEndPx,1),
                        h = subtitles.last().hPx - subtitles.last().deltaEndH,
                        opacity = 0.0
                    )
                )
            } else {
                // Если время окончания фейда закончится после начала следующей строки
                val coeff = (endTimeFadeMs.toDouble() - startTpNextLineMs.toDouble()) / (endTimeFadeMs.toDouble() - endTpCurrLineMs.toDouble())
                // До начала анимации следующей линии фейдим до коэффициэнта
                if (nextLine != null) {
                    // Находим положение текущей линии на момент начала следующей линии
                    yEnd = voice.getScreenY(this, convertMillisecondsToTimecode(startTpNextLineMs), horizonPositionPx) + subtitles.last().deltaEndY
                    result.add(
                        TransformProperty(
                            time = convertMillisecondsToTimecode(startTpNextLineMs),
                            x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                            y = yEnd,
                            w = Integer.max(subtitles.last().xEndPx,1),
                            h = subtitles.last().hPx - subtitles.last().deltaEndH,
                            opacity = opacityFillValue * coeff
                        )
                    )
                }

                // Находим положение текущей линии на момент окончания фэйда
                yEnd = voice.getScreenY(this, convertMillisecondsToTimecode(endTimeFadeMs), horizonPositionPx) + subtitles.last().deltaEndY
                result.add(
                    TransformProperty(
                        time = convertMillisecondsToTimecode(endTimeFadeMs),
                        x = Karaoke.songtextStartPositionXpx + (startTp?.x ?: 0),
                        y = yEnd,
                        w = Integer.max(subtitles.last().xEndPx,1),
                        h = subtitles.last().hPx - subtitles.last().deltaEndH,
                        opacity = 0.0
                    )
                )
            }

        }
        // Надо проверить, куда попадает endTp и startTp следующей строки

        return result.joinToString(";")
    }
    val hPx: Int get() = getTextWidthHeightPx("0",mltText.font).second.toInt()
    val widthLinePx: Long
        get() = getWidthLinePx().toLong()

    fun getSymbolXpx(symbolPositionInSymbols: Int): Double {
        var result = 0.0
        for (indexSymbol in symbols.indices) {
            if (indexSymbol >= symbolPositionInSymbols) break
            result += symbols[indexSymbol].widthPx
        }
        return result
    }

    fun getSubtitleXpx(subtitle: Subtitle): Double {
        var textSubs = ""
        for (indexSubtitle in subtitles.indices) {
            val sub = subtitles[indexSubtitle]
            if (sub == subtitle) break
            textSubs += sub.wPx
        }
        return getTextWidthHeightPx(textSubs,mltText.font).first
    }

    fun getWidthLinePx(): Double {
        var textSubs = ""
        for (indexSubtitle in subtitles.indices) {
            val sub = subtitles[indexSubtitle]
            textSubs += sub.mltText.text
        }

        val result = getTextWidthHeightPx(textSubs,mltText.font).first

        return result

    }

    fun getSubtitleWpx(subtitle: Subtitle): Double {
        return getTextWidthHeightPx(subtitle.mltText.text,mltText.font).first
    }


    fun getCharXpx(charPositionText: Int): Double {

        val textBeforeChar = text.substring(0..charPositionText)
        return if (textBeforeChar != "") {
            getTextWidthHeightPx(textBeforeChar,mltText.font).first
        } else {
            0.0
        }
    }

}

data class SongVoiceLineSymbol(
    var start: String,
    var mltText: MltText = Karaoke.voices[0].groups[0].mltText.copy(""),
    var mltTextBefore: MltText = Karaoke.voices[0].groups[0].mltText.copy("")
) {
    val hPx: Int get() = getTextWidthHeightPx("0", mltText.font).second.toInt()
    val xStartPx: Int get() = getTextWidthHeightPx(mltTextBefore.text, mltTextBefore.font).first.toInt()
    val xEndPx: Int get() = getTextWidthHeightPx(mltTextBefore.text+mltText.text, mltTextBefore.font).first.toInt()
    val wPx: Int get() = xEndPx - xStartPx
    private val widthHeightPx: Pair<Double, Double> get() {
        val result = getTextWidthHeightPx(mltText.text, mltText.font)
        return result
    }
    val widthPx: Double get() {
        return widthHeightPx.first
    }
    val heightPx: Double get() {
        return widthHeightPx.second
    }
}

data class Subtitle(
    val startTimecode: String = "",
    var endTimecode: String = "",
    var isLineStart: Boolean = false,
    var isLineEnd: Boolean = false,
    var indexFirstSymbolInLine: Int = 0,
    var mltText: MltText = Karaoke.voices[0].groups[0].mltText.copy(""),
    var mltTextBefore: MltText = Karaoke.voices[0].groups[0].mltText.copy("")
) {
    val hPx: Int get() = getTextWidthHeightPx("0", mltText.font).second.toInt()
    val xStartPx: Int get() = getTextWidthHeightPx(mltTextBefore.text, mltTextBefore.font).first.toInt()
    val xEndPx: Int get() = getTextWidthHeightPx(mltTextBefore.text+mltText.text, mltTextBefore.font).first.toInt()
    val wPx: Int get() = xEndPx - xStartPx
    val durationMs: Long get() = convertTimecodeToMilliseconds(endTimecode) - convertTimecodeToMilliseconds(startTimecode)
    val isShortSubtitleInt: Int get() = if (durationMs <= Karaoke.shortSubtitleMs) 1 else 0
    val deltaStartY: Int get() = hPx / 7
    val deltaStartH: Int get() = 2 * deltaStartY
    val deltaEndY: Int get() = isShortSubtitleInt * (hPx / 7)
    val deltaEndH: Int get() = 2 * deltaEndY

}

data class Chord(
    val timecode: String = "",
    val text: String = ""
)
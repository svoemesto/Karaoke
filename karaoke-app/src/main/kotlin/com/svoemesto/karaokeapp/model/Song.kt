package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltText
import java.io.File
import java.io.Serializable
import kotlin.math.absoluteValue

data class Song(val settings: Settings, val songVersion: SongVersion, val woInit: Boolean = false) : Serializable {

    val propAudioVolumeOn: String get() {
        val prop = mutableListOf<String>()
        prop.add("00:00:00.000=0")
        prop.add("${endTimecode}=0")
        return prop.joinToString(";")
    }

    val propAudioVolumeOff: String get() {
        val prop = mutableListOf<String>()
        prop.add("00:00:00.000=-100")
        prop.add("${endTimecode}=-100")
        return prop.joinToString(";")
    }

    val propAudioVolumeCustom: String get() {
        val prop = mutableListOf<String>()
        prop.add("00:00:00.000=-100")
        settings.sourceUnmute.forEach { (unMuteStart, unMuteEnd) ->
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteStart*1000).toLong())- 2)}=-100")
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteStart*1000).toLong()))}=0")
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteEnd*1000).toLong()))}=0")
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteEnd*1000).toLong())+ 2)}=-100")
        }
        prop.add("${endTimecode}=-100")
        return prop.joinToString(";")
    }


    private val REPLACE_STRING = "[R]"
    private val END_STRING = "[E]"
    private val COMMENT_STRING = "[C]"
    private val REPEAT1_START_STRING = "[RS]"
    private val REPEAT1_END_STRING = "[RE]"
    private val REPEAT1_PAST_STRING = "[RR]"
    private val REPEAT2_START_STRING = "[TS]"
    private val REPEAT2_END_STRING = "[TE]"
    private val REPEAT2_PAST_STRING = "[TR]"
    fun getOutputFilename2(songOutputFile: SongOutputFile): String {
        return "${settings.rootFolder}/done_${if (songOutputFile in listOf(
                SongOutputFile.PROJECT,
                SongOutputFile.SUBTITLE,
                SongOutputFile.MLT,
                SongOutputFile.RUN,
                SongOutputFile.RUNALL,
                SongOutputFile.TEXT
            )) "projects" else if (songOutputFile == SongOutputFile.PICTURECHORDS) "chords" else "files"}/${if (songOutputFile == SongOutputFile.VK) "[{REPLACE_DATE}_{REPLACE_TIME}] " else ""}${settings.rightSettingFileName}${songVersion.suffix}${if (songOutputFile == SongOutputFile.PICTURECHORDS) " chords" else ""}${if (songOutputFile == SongOutputFile.PICTUREBOOSTY) " boosty" else ""}${if (songOutputFile == SongOutputFile.PICTUREVK) " VK" else ""}${if (songOutputFile == SongOutputFile.VK) " [VK]" else ""}.${songOutputFile.extension}"
    }

    var endTimecode: String = ""
    var capo: Int = 0
    var voices: MutableList<SongVoice> = mutableListOf()
    val hasChords: Boolean get() = voices.sumOf { it.lines.filter { it.type == SongVoiceLineType.CHORDS }.size } > 0

    data class SubtitleFileElement(
        var startFrame: Long,
        var endFrame: Long,
        var text: String,
        var isStartOfLine: Boolean,
        var isEndOfLine: Boolean,
        val isSetting: Boolean
    ) : Serializable

    init {
        if (!woInit) {
            val beatMs = if (settings.ms == 0L) (60000.0 / settings.bpm).toLong() else settings.ms

            val mapFiles = mutableListOf<String>()
            // Считываем сабы
            var listFile = getListFiles(settings.rootFolder, ".srt", "${settings.fileName}.voice")
            var bodyText = settings.sourceText
            if (listFile.size == 0) {
                listFile = getListFiles(settings.rootFolder, ".srt", "${settings.fileName}.srt")
                bodyText = settings.sourceText
                if (listFile.size == 0) {
                    listFile = getListFiles(settings.rootFolder, ".srt", "${settings.fileName}.kdenlive")
                    val listFileTxt = getListFiles(settings.rootFolder, ".txt", settings.fileName)
                    if (listFileTxt.size == 1) {
                        bodyText = File(listFileTxt[0]).readText(Charsets.UTF_8)
                    }
                }

            }
            listFile.sorted().forEach { mapFiles.add(it) }
            // Если текстовый файл один
            if (bodyText != "") {

                // Считываем тест из первого файла сабов и заменяем в нём тильды
                if (mapFiles.isNotEmpty()) {
                    var bodySubs = File(mapFiles[0]).readText(Charsets.UTF_8)
                        .replace("~","")
                        .replace("Введите текст","")
                        .replace("[G0]","[SETTING]|GROUP|0")
                        .replace("[G1]","[SETTING]|GROUP|1")
                        .replace("[G2]","[SETTING]|GROUP|2")
                        .replace("[G3]","[SETTING]|GROUP|3")
                        .replace("[C]","[SETTING]|COMMENT| ")
                    // Если в тексте сабов присутствует строка замены
                    if (bodySubs.contains(END_STRING)) {
                        // Бэкапим файл (если еще нет бэкапа)
                        val fileText = File("${mapFiles[0]}.backup")
                        if (fileText.exists()) {
                            bodySubs = fileText.readText(Charsets.UTF_8)
                                .replace("~","")
                                .replace("Введите текст","")
                                .replace("[G0]","[SETTING]|GROUP|0")
                                .replace("[G1]","[SETTING]|GROUP|1")
                                .replace("[G2]","[SETTING]|GROUP|2")
                                .replace("[G3]","[SETTING]|GROUP|3")
                                .replace("[C]","[SETTING]|COMMENT| ")
                        } else {
                            fileText.writeText(bodySubs)
                        }

                        // Заполняем список слогов
                        val slogs = getSyllables(bodyText)

                        println(bodySubs)
                        println(slogs)

                        val body = File(mapFiles[0]).readText(Charsets.UTF_8)
                            .replace("~","")
                            .replace("Введите текст","")
                            .replace("[G0]","[SETTING]|GROUP|0")
                            .replace("[G1]","[SETTING]|GROUP|1")
                            .replace("[G2]","[SETTING]|GROUP|2")
                            .replace("[G3]","[SETTING]|GROUP|3")
                            .replace("[C]","[SETTING]|COMMENT| ")

                        // Считываем сабы в блоки
                        val blocks = body.split("\\n[\\n]+".toRegex())
                        val listSubtitleFileElements: MutableList<SubtitleFileElement> = mutableListOf()
                        val listSubtitleFileElementsRepeated1: MutableList<SubtitleFileElement> = mutableListOf()
                        val listSubtitleFileElementsRepeated2: MutableList<SubtitleFileElement> = mutableListOf()

                        var isRepeated1 = false
                        var nextSubIsFirstRepeated1 = false
                        var repeatOffset1 = 0L
                        var isRepeated2 = false
                        var nextSubIsFirstRepeated2 = false
                        var repeatOffset2 = 0L

                        // Проходимся по блокам
                        blocks.forEach() { block ->
                            val blocklines = block.split("\n")
                            val id = if (blocklines.isNotEmpty() && blocklines[0]!= "" ) blocklines[0].toLong() else 0
                            val startEnd = if (blocklines.size > 1) blocklines[1] else ""
                            if (startEnd != "" && id != 0L) {
                                var text = if (blocklines.size > 2) blocklines[2] else ""
                                val se = startEnd.split(" --> ")
                                val startFrame = convertTimecodeToFrames(se[0].replace(",","."))
                                val endFrame = convertTimecodeToFrames(se[1].replace(",","."))

                                // Если текущий саб - отметка начала повтора
                                if (text.contains(REPEAT1_START_STRING) || text.contains(REPEAT2_START_STRING)) {
                                    // Устанавливаем флаги и пропускаем этот саб
                                    if (text.contains(REPEAT1_START_STRING)) {
                                        isRepeated1 = true
                                        nextSubIsFirstRepeated1 = true
                                    }
                                    if (text.contains(REPEAT2_START_STRING)) {
                                        isRepeated2 = true
                                        nextSubIsFirstRepeated2 = true
                                    }
                                } else {
                                    // Если текущий саб - отметка конца повтора
                                    if (text.contains(REPEAT1_END_STRING) || text.contains(REPEAT2_END_STRING)) {
                                        // Снимаем флаг и пропускаем этот саб
                                        if (text.contains(REPEAT1_END_STRING)) {
                                            isRepeated1 = false
                                        }
                                        if (text.contains(REPEAT2_END_STRING)) {
                                            isRepeated2 = false
                                        }
                                    } else {

                                        // Если текущий саб - отметка начала вставки повтора
                                        if (text.contains(REPEAT1_PAST_STRING)) {
                                            // Надо добавить в общий лист все элементы из повторного листа со сдвигом на оффсет
                                            val repeatPastOffset = startFrame
                                            listSubtitleFileElementsRepeated1.forEach {
                                                listSubtitleFileElements.add(
                                                    SubtitleFileElement(
                                                        startFrame = it.startFrame - repeatOffset1 + repeatPastOffset,
                                                        endFrame = it.endFrame - repeatOffset1 + repeatPastOffset,
                                                        text = it.text,
                                                        isStartOfLine = it.isStartOfLine,
                                                        isEndOfLine = it.isEndOfLine,
                                                        isSetting = it.isSetting
                                                    )
                                                )
                                            }
                                        } else if (text.contains(REPEAT2_PAST_STRING)) {
                                            // Надо добавить в общий лист все элементы из повторного листа со сдвигом на оффсет
                                            val repeatPastOffset = startFrame
                                            listSubtitleFileElementsRepeated2.forEach {
                                                listSubtitleFileElements.add(
                                                    SubtitleFileElement(
                                                        startFrame = it.startFrame - repeatOffset2 + repeatPastOffset,
                                                        endFrame = it.endFrame - repeatOffset2 + repeatPastOffset,
                                                        text = it.text,
                                                        isStartOfLine = it.isStartOfLine,
                                                        isEndOfLine = it.isEndOfLine,
                                                        isSetting = it.isSetting
                                                    )
                                                )
                                            }
                                        } else {
                                            // Если текущий саб - первый в повторе - запоминаем оффсет
                                            if (nextSubIsFirstRepeated1) {
                                                nextSubIsFirstRepeated1 = false
                                                repeatOffset1 = startFrame
                                            }
                                            if (nextSubIsFirstRepeated2) {
                                                nextSubIsFirstRepeated2 = false
                                                repeatOffset2 = startFrame
                                            }
                                            // Заносим саб в общий лист
                                            listSubtitleFileElements.add(SubtitleFileElement(startFrame, endFrame, text, false, false, (text.uppercase().startsWith("[SETTING]|") || text == "//\\\\")))
                                            // Если саб в повторе - заносим его еще и в лист повторов
                                            if (isRepeated1) {
                                                listSubtitleFileElementsRepeated1.add(SubtitleFileElement(startFrame, endFrame, text, false, false, (text.uppercase().startsWith("[SETTING]|") || text == "//\\\\")))
                                            }
                                            if (isRepeated2) {
                                                listSubtitleFileElementsRepeated2.add(SubtitleFileElement(startFrame, endFrame, text, false, false, (text.uppercase().startsWith("[SETTING]|") || text == "//\\\\")))
                                            }
                                        }


                                    }
                                }
                            }
                        }

                        listSubtitleFileElements.sortBy { it.startFrame }
                        var isStartLine = true
                        var indexSlog = 0
                        for (i in 0 until listSubtitleFileElements.size - 1) {
                            val currSubElement = listSubtitleFileElements[i]
                            val nextSubElement = listSubtitleFileElements[i+1]
                            if (!currSubElement.isSetting && !currSubElement.text.contains(END_STRING)) {
                                currSubElement.endFrame = nextSubElement.startFrame
                                currSubElement.isStartOfLine = isStartLine
                                currSubElement.isEndOfLine = nextSubElement.text.contains(END_STRING)
                                isStartLine = currSubElement.isEndOfLine
                                if (indexSlog < slogs.size) {
                                    val txt = if (currSubElement.isEndOfLine) slogs[indexSlog].substring(0,slogs[indexSlog].length-1) else slogs[indexSlog]
                                    currSubElement.text = "${if (currSubElement.isStartOfLine) "//" else ""}${if (currSubElement.isStartOfLine) txt.uppercaseFirstLetter() else txt}${if (currSubElement.isEndOfLine) "\\\\" else ""}"
                                    indexSlog++
                                    if (indexSlog == slogs.size) {
                                        val lastSymbol = currSubElement.text.substring(currSubElement.text.length - 3)
                                        currSubElement.text = currSubElement.text.substring(0,currSubElement.text.length - 3)
                                        currSubElement.isEndOfLine = false
                                        listSubtitleFileElements.add(
                                            SubtitleFileElement(
                                                startFrame = currSubElement.endFrame,
                                                endFrame = currSubElement.endFrame+5,
                                                text = lastSymbol,
                                                isStartOfLine = false,
                                                isEndOfLine = true,
                                                isSetting = false
                                            )
                                        )
                                    }
                                }

                            }
                        }

                        listSubtitleFileElements.sortBy { it.startFrame }

                        var newBody = ""
                        listSubtitleFileElements.filter { !it.text.contains(END_STRING) } .forEachIndexed { index, element ->
                            newBody += "${index+1}\n${convertFramesToTimecode(element.startFrame).replace(".",",")} --> ${convertFramesToTimecode(element.endFrame).replace(".",",")}\n${element.text}\n\n"
                        }
                        File(mapFiles[0]).writeText(newBody)

                    }
                }

            }

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
                                    var commentText = if (settingList.size > 2) settingList[2] else ""
                                    if (commentText == "") commentText = " "
                                    if (commentText != "") {
                                        val subtitle = Subtitle(
                                            startTimecode = commentTimecode,
                                            endTimecode = commentTimecode,
                                            mltText = Karaoke.voices[0].groups[group].mltText.copy(commentText),
                                            mltTextBefore = Karaoke.voices[0].groups[group].mltText.copy(""),
                                            isLineStart = true,
                                            isLineEnd = true,
                                            indexFirstSymbolInLine = 0
                                        )
                                        val symbol = SongVoiceLineSymbol(
                                            start = commentTimecode,
                                            mltText = Karaoke.voices[0].groups[group].mltText.copy(commentText),
                                            mltTextBefore = Karaoke.voices[0].groups[group].mltText.copy("")
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
                                                mltText = Karaoke.voices[0].groups[group].mltText.copy(commentText)
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
//                            mltText = Karaoke.voices[0].groups[group].mltText.copy(text),
                                mltText = Karaoke.voices[0].groups[group].mltText.copy(text),
//                            mltTextBefore = Karaoke.voices[0].groups[group].mltText.copy(lineText),
                                mltTextBefore = Karaoke.voices[0].groups[group].mltText.copy(lineText),
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
                                widthLinePx = getTextWidthHeightPx(lineText, Karaoke.voices[0].groups[group].mltText.font).first
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
                                            mltText = Karaoke.voices[0].groups[group].mltText
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
                                            mltText = Karaoke.voices[0].groups[group].mltText.copy(lineText)
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
                        val subtitle = voiceLine.subtitles.firstOrNull { convertTimecodeToMilliseconds(chord.timecode) in convertTimecodeToMilliseconds(it.startTimecode) until convertTimecodeToMilliseconds(it.endTimecode) }
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
                                mltText = Karaoke.voices[0].groups[group].mltText.copy("0")
                            )
                        )
                    }
                }

                if (listChordLines.isNotEmpty()) listLines.addAll(listChordLines)

                listVoices.add(
                    SongVoice(
                        srtFileBody = body,
                        lines = listLines.sortedBy { convertTimecodeToMilliseconds(it.start)}.toMutableList()
                    )
                )

            }

            voices = listVoices
//        endTimecode = voices.first().lines.last().subtitles.last().endTimecode
            if (listVoices.isNotEmpty() && listVoices.first().lines.isNotEmpty()) endTimecode = voices.first().lines.last().end
            setMaxLines()
        }

    }

    fun setMaxLines() {

        voices.forEachIndexed { indexVoice, currVoice ->
            val currVoiceTextLines = currVoice.lines.filter { it.type == SongVoiceLineType.TEXT }
            if (currVoiceTextLines.isEmpty()) return
            var currVoiceMaxLine = currVoiceTextLines[0]
            var currVoiceMaxSingleLine = currVoiceTextLines[0]
            var maxLineWidthPx = 0
            var maxSingleLineWidthPx = 0
            if (indexVoice == 0) {
                currVoiceMaxLine = currVoiceTextLines.maxBy { it.widthLinePx }
                currVoiceMaxLine.isMaxSingleLine = true
            }
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
) : Serializable {
    val maxDurationMs: Long
    get() {
        return lines.maxOf { it.durationMs }
    }
    val maxCountSymbolsInLine: Int
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT }.firstOrNull { it.isMaxLine }?.text?.length ?: 0
    }
    val maxWidthLinePx: Long
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT }.firstOrNull { it.isMaxLine }?.widthLinePx ?: 0
    }

    val maxWidthSingleLinePx: Long
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT }.firstOrNull() { it.isMaxSingleLine }?.widthLinePx ?: 0
    }

    val maxWidthLineText: String
    get() {
        return lines.filter {it.type == SongVoiceLineType.TEXT }.firstOrNull { it.isMaxLine }?.text ?: ""
    }

    val maxWidthSingleLineText: String
        get() {
            return lines.filter {it.type == SongVoiceLineType.TEXT }.firstOrNull { it.isMaxSingleLine }?.text ?: ""
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
                    .filter { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT }
                    .lastOrNull { convertTimecodeToMilliseconds(it.endTp!!.time) < timeMs }
                val lineAfterTime = lines
                    .filter { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT }
                    .firstOrNull { convertTimecodeToMilliseconds(it.startTp!!.time) > timeMs }
                if (lineBeforeTime == null) {
                    // Если нет "линии до" - значит нужная линия - первая
                    val lineInTime = lines.first { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT }
                    result = horizonPositionPx - line.hPx - (line.yPx - lineInTime.yPx).absoluteValue
                } else if (lineAfterTime == null) {
                    // Если нет "линии после" - значит нужная линия - последняя
                    val lineInTime = lines.last { it.startTp != null && it.endTp != null  && it.type == SongVoiceLineType.TEXT }
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

enum class SongVoiceLineType : Serializable {
    EMPTY,
    TEXT,
    CHORDS,
    COMMENTS
}
data class SongVoiceLine(
    var type: SongVoiceLineType = SongVoiceLineType.TEXT,
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
    var isMaxSingleLine: Boolean = false,
    var durationMs: Long = 0,
    var mltText: MltText
) : Serializable {
    val startYT: String get() {
        return if (start == "") {
            ""
        } else {
            convertMillisecondsToDzenTimecode(convertTimecodeToMilliseconds(start) + 8000);
        }
    }

    fun getFillTps(voice: SongVoice, horizonPositionPx: Int, opacityFillValue: Double): String {
        val result: MutableList<TransformProperty> = mutableListOf()

        val endTpCurrLineMs = convertTimecodeToMilliseconds(endTp!!.time) // Конец текущей линии. В это время начинается движение линии
        // Следующая линия - текстовая, с не пустым startTp, начало линии позже конца текущей линии
        val nextLine = voice.lines.firstOrNull {
            it.type == SongVoiceLineType.TEXT && it.startTp != null && convertTimecodeToMilliseconds(it.startTp!!.time) > endTpCurrLineMs
        }
        // Начало следующей линии
        val startTpNextLineMs = if (nextLine != null) convertTimecodeToMilliseconds(nextLine.startTp!!.time) else -1
        val endTpNextLineMs = if (nextLine != null) convertTimecodeToMilliseconds(nextLine.endTp!!.time) else -1

        // Так же надо найти линию (если есть) которая идёт за следующей
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

//                println("${subtitle.mltText.text} - ${timeToMs} - ${endTpCurrLineMs} - ${yEnd}")

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
                // В том случае, если конец предыдущего субтитра не совпадает с концом линии - в этом случае анимация уже была добавлена
                if (subtitle != subtitles.first() &&
                    convertTimecodeToMilliseconds(subtitles[indexSubtitle-1].endTimecode) != endTpCurrLineMs) {
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
                }


                // Находим положение текущей линии на момент конца субтитра
                yEnd = voice.getScreenY(this, subtitle.endTimecode, horizonPositionPx) + subtitle.deltaEndY
//                println("${subtitle.mltText.text} - ${timeToMs} - ${endTpCurrLineMs} - ${yEnd}")
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
                convertFramesToMilliseconds(convertMillisecondsToFrames(endTpNextLineMs) -2)
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
) : Serializable {
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
    var startTimecode: String = "",
    var endTimecode: String = "",
    var isLineStart: Boolean = false,
    var isLineEnd: Boolean = false,
    var indexFirstSymbolInLine: Int = 0,
    var mltText: MltText = Karaoke.voices[0].groups[0].mltText.copy(""),
    var mltTextBefore: MltText = Karaoke.voices[0].groups[0].mltText.copy("")
) : Serializable {
    val hPx: Int get() = getTextWidthHeightPx("0", mltText.font).second.toInt()
    val xStartPx: Int get() = getTextWidthHeightPx(mltTextBefore.text, mltTextBefore.font).first.toInt()
    val xEndPx: Int get() = getTextWidthHeightPx(mltTextBefore.text+mltText.text, mltTextBefore.font).first.toInt()
    val wPx: Int get() = xEndPx - xStartPx
    val startMs: Long get() = convertTimecodeToMilliseconds(startTimecode)
    val endMs: Long get() = convertTimecodeToMilliseconds(endTimecode)
    val durationMs: Long get() = endMs - startMs
    val isShortSubtitleInt: Int get() = if (durationMs <= Karaoke.shortSubtitleMs) 1 else 0
    val deltaStartY: Int get() = hPx / 7
    val deltaStartH: Int get() = 2 * deltaStartY
    val deltaEndY: Int get() = isShortSubtitleInt * (hPx / 7)
    val deltaEndH: Int get() = 2 * deltaEndY

}

data class Chord(
    val timecode: String = "",
    val text: String = ""
): Serializable

@kotlinx.serialization.Serializable
data class WaveSurferMarker(
    var time: Double = 0.0,
    var label: String = "Marker",
    var color: String = "#ff990a",
    var position: String = "bottom"
): Serializable
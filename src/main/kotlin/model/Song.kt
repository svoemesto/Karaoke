package model

import Karaoke
import convertMillisecondsToTimecode
import convertTimecodeToMilliseconds
import getBeatNumberByTimecode
import getDurationInMilliseconds
import getListFiles
import getTextWidthHeightPx
import hashtag
import java.awt.Font
import java.io.File
import java.util.DoubleSummaryStatistics

data class Song(val settings: Settings) {
        var endTimecode: String = ""
        var beatTimecode: String = "00:00:00.000"
        var voices: MutableList<SongVoice> = mutableListOf()

        val descriptionLyricText: String
            get() {
                val text = "${settings.songName} ★♫★ ${settings.author} ★♫★ lyric" + "\n" +
                        "Композиция: ${settings.songName}\n" +
                        "Исполнитель: ${settings.author}\n" +
                        "Альбом: ${settings.album}\n" +
                        "Тональность: ${settings.key}\n" +
                        "Темп: ${settings.bpm} bpm\n" +
                        "Karaoke-версия: \n" +
                        "Плейлист «${settings.author} (karaoke)»: \n" +
                        "Плейлист «${settings.author} (lyrics)»: \n" +
                        "Минусовка, определение тональности и темпа композиции сделаны с помощью сервиса https://vocalremover.org\n" +
                        "Видео создано с помощью написанной мной программы для создания караоке: https://github.com/svoemesto/Karaoke\n" +
                        "${settings.songName.hashtag()} ${settings.author.hashtag()} ${"karaoke".hashtag()} ${"караоке".hashtag()}\n"
                return text
            }

    val descriptionKaraokeText: String
        get() {
            val text = "${settings.songName} ★♫★ ${settings.author} ★♫★ karaoke" + "\n" +
                    "Композиция: ${settings.songName}\n" +
                    "Исполнитель: ${settings.author}\n" +
                    "Альбом: ${settings.album}\n" +
                    "Тональность: ${settings.key}\n" +
                    "Темп: ${settings.bpm} bpm\n" +
                    "Lyric-версия: \n" +
                    "Плейлист «${settings.author} (karaoke)»: \n" +
                    "Плейлист «${settings.author} (lyrics)»: \n" +
                    "Минусовка, определение тональности и темпа композиции сделаны с помощью сервиса https://vocalremover.org\n" +
                    "Видео создано с помощью написанной мной программы для создания караоке: https://github.com/svoemesto/Karaoke\n" +
                    "${settings.songName.hashtag()} ${settings.author.hashtag()} ${"karaoke".hashtag()} ${"караоке".hashtag()}\n"
            return text
        }
        init {

            val beatMs = if (settings.ms == 0L) (60000.0 / settings.bpm).toLong() else settings.ms

            val mapFiles = mutableListOf<String>()
            val listFile = getListFiles(settings.rootFolder, ".srt", "${settings.fileName}.kdenlive")
            listFile.sorted().forEach { mapFiles.add(it) }

            val listVoices = mutableListOf<SongVoice>()
            for (voideId in listFile.indices) {

                val listLines = mutableListOf<SongVoiceLine>()
                var maxLengthLine = 0
                var maxWidthLinePx = 0.0
                var lengthLine = 0
                var widthLinePx = 0.0
                var lineText = ""
                var maxWidthLineText = ""
                val body = File(mapFiles[voideId]).readText(Charsets.UTF_8)

                var voiceBeatTimecode = "00:00:00.000"
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
                                "BEAT" -> voiceBeatTimecode = startEnd.split(" --> ")[0].replace(",",".")
                                "GROUP" -> group = if (settingList.size > 2) settingList[2].toInt() else 0
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
                            val subtitle = Subtitle(
                                startTimecode = start,
                                endTimecode = end,
                                text = text,
                                isLineStart = isLineStart,
                                isLineEnd = isLineEnd,
                                group = group,
                                indexFirstSymbolInLine = lengthLine
                            )
                            lengthLine += text.length
                            lineText += text

                            // Добавляем этот объект к списку объектов
                            songSubtitles.add(subtitle)

                            if (isLineEnd) {
                                maxLengthLine = Integer.max(maxLengthLine, lengthLine)
                                lengthLine = 0
                                widthLinePx = getTextWidthHeightPx(lineText, Karaoke.voices[voideId].groups[group].songtextTextMltFont.font).first
                                if (maxWidthLinePx < widthLinePx) {
                                    maxWidthLinePx = java.lang.Double.max(maxWidthLinePx, widthLinePx)
                                    maxWidthLineText = lineText
                                }
                                listLines.add(
                                    SongVoiceLine(
                                        subtitles = songSubtitles,
                                        text = lineText,
                                        start = songSubtitles.first().startTimecode,
                                        end = songSubtitles.last().endTimecode,
                                        durationMs = getDurationInMilliseconds(songSubtitles.first().startTimecode, songSubtitles.last().endTimecode),
                                        isEmptyLine = (lineText == ""),
                                        fontText = Karaoke.voices[voideId].groups[group].songtextTextMltFont.font,
                                        fontBeat = Karaoke.voices[voideId].groups[group].songtextBeatMltFont.font
                                    )
                                )
                                lineText = ""
                                songSubtitles = mutableListOf()
                            }
                        }
                    }

                }

                listLines.forEach {
//                    println("${it.widthLinePx} - ${maxWidthLinePx.toLong()}")
                    it.isMaxLine = it.widthLinePx >= maxWidthLinePx.toLong()
                }

                listVoices.add(
                    SongVoice(
                        srtFileBody = body,
                        lines = listLines
                    ))

                if (voideId == 0) beatTimecode = voiceBeatTimecode

            }

            listVoices.forEach { songVoice ->
                songVoice.lines.forEach { voiceLine ->
                    voiceLine.subtitles.forEach { subtitle ->
                        subtitle.isBeat = if (subtitle.text != "") {
                            val startBeatNumber = getBeatNumberByTimecode(subtitle.startTimecode, beatMs, beatTimecode)
                            val endBeatNumber = getBeatNumberByTimecode(subtitle.endTimecode, beatMs, beatTimecode)
                            startBeatNumber > endBeatNumber
                        } else false
                    }
                }
            }

            voices = listVoices
            endTimecode = voices.first().lines.last().subtitles.last().endTimecode

        }
    }


data class SongVoice(
    val srtFileBody: String = "",
    val lines: MutableList<SongVoiceLine> = mutableListOf()
) {
    val maxDurationMs: Long
    get() {
        return lines.maxOf { it.durationMs }
    }
    val maxCountSymbolsInLine: Int
    get() {
        return lines.first { it.isMaxLine }.text.length
    }
    val maxWidthLinePx: Long
    get() {
        return lines.first { it.isMaxLine }.widthLinePx
    }
    val maxWidthLineText: String
    get() {
        return lines.first { it.isMaxLine }.text
    }
}

data class SongVoiceLine(
    var subtitles: MutableList<Subtitle> = mutableListOf(),
    var symbols: MutableList<SongVoiceLineSymbol> = mutableListOf(),
    var text: String = "",
    var start: String = "",
    var end: String = "",
    var startTp: TransformProperty? = null,
    var endTp: TransformProperty? = null,
    var isEmptyLine: Boolean = false,
    var isNeedCounter: Boolean = false,
    var isFadeLine: Boolean = false,
    var isMaxLine: Boolean = false,
    var durationMs: Long = 0,
    var fontText: Font,
    var fontBeat: Font,
) {
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
            textSubs += sub.text
        }
        return getTextWidthHeightPx(textSubs,fontText).first
    }

    fun getWidthLinePx(): Double {
        var textSubs = ""
        for (indexSubtitle in subtitles.indices) {
            val sub = subtitles[indexSubtitle]
            textSubs += sub.text
        }

        val result = getTextWidthHeightPx(textSubs,fontText).first

        return result

    }

    fun getSubtitleWpx(subtitle: Subtitle): Double {
        return getTextWidthHeightPx(subtitle.text,fontText).first
    }


    fun getCharXpx(charPositionText: Int): Double {

        val textBeforeChar = text.substring(0..charPositionText)
        return if (textBeforeChar != "") {
            getTextWidthHeightPx(textBeforeChar,fontText).first
        } else {
            0.0
        }

    }
}

data class SongVoiceLineSymbol(
    var text: String = "",
    var font: Font,
    var group: Int = 0,
    var isBeat: Boolean = false
) {
    private val widthHeightPx: Pair<Double, Double> get() {
        val result = getTextWidthHeightPx(text, font)
        return result
    }
    val widthPx: Double get() {
        return widthHeightPx.first
    }
    val heightPx: Double get() {
        return widthHeightPx.second
    }
}
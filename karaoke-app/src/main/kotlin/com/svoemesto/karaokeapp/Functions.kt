package com.svoemesto.karaokeapp//import model.Lyric
import getTemplateBackChords
import getTemplateBoosty
import getTemplateCounter
import getTemplateFaderChords
import getTemplateFaderText
import getTemplateFingerboard
import getTemplateFlash
import getTemplateHeader
import getTemplateHorizon
import getTemplateProgress
import getTemplateSongText
import getTemplateSplashstart
import getTemplateWatermark
import com.svoemesto.karaokeapp.mlt.getMlt
import com.svoemesto.karaokeapp.model.*
import java.io.File
import java.lang.Integer.min
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
//import java.nio.file.Path
import kotlin.io.path.Path
fun createKaraokeAll(pathToSettingsFile: String) {
    val settings = Settings.loadFromFile(pathToSettingsFile)
    createKaraoke(Song(settings, SongVersion.LYRICS), false)
    createKaraoke(Song(settings, SongVersion.KARAOKE), false)
    createKaraoke(Song(settings, SongVersion.CHORDS), false)
    createKaraoke(Song(settings, SongVersion.LYRICS), true)
    createKaraoke(Song(settings, SongVersion.KARAOKE), true)
    createKaraoke(Song(settings, SongVersion.CHORDS), true)

}
fun createKaraoke(song: Song, isBluetoothDelay: Boolean) {

    if (song.songVersion == SongVersion.CHORDS && !song.hasChords) return
    println("Создаём ${song.songVersion.name}${if (isBluetoothDelay) " for Bluetooth" else ""}: ${song.settings.author} / ${song.settings.songName}")

    val param = mutableMapOf<String, Any?>()

    param["SONG_VERSION"] = song.songVersion
    param["SONG_CAPO"] = song.capo
    param["SONG_CHORD_DESCRIPTION"] = song.getChordDescription()
    param["SONG_NAME"] = song.settings.songName
    param["ID_BLUETOOTH_DELAY"] = isBluetoothDelay

    param["COUNT_VOICES"] = song.voices.size
    param["LINE_SPACING"] = LINE_SPACING
    param["SHADOW"] = SHADOW
    param["TYPEWRITER"] = TYPEWRITER
    param["ALIGNMENT"] = ALIGNMENT

    val maxTextWidthPx =
        Karaoke.frameWidthPx.toDouble() - Karaoke.songtextStartPositionXpx * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа

    // Ширина в пикселах суммарной самой длинной строки
    var maxTextWidthPxByFontSize =
        (Integer.max(song.voices.sumOf { it.maxWidthLinePx }.toInt(), song.voices.sumOf { it.maxWidthSingleLinePx }.toInt()) + Karaoke.songtextStartPositionXpx * (song.voices.size - 1)).toLong()

    // Максимальный размер шрифта берем из дефолтного значения
    var fontSongtextSizePt = Karaoke.voices[0].groups[0].mltText.font.size
    val step = if (maxTextWidthPxByFontSize > maxTextWidthPx) -1 else 1
    while (true) {
        if ((maxTextWidthPxByFontSize > maxTextWidthPx && step < 0) || (maxTextWidthPxByFontSize < maxTextWidthPx && step > 0)) {
            fontSongtextSizePt += step
            val a1 = (song.voices.sumOf {
                getTextWidthHeightPx(
                    it.maxWidthLineText,
                    Karaoke.voices[0].groups[0].mltText.font.name,
                    Karaoke.voices[0].groups[0].mltText.font.style,
                    fontSongtextSizePt
                ).first
            } + Karaoke.songtextStartPositionXpx * (song.voices.size).toLong()).toInt()
            val a2 = (song.voices.sumOf {
                getTextWidthHeightPx(
                    it.maxWidthSingleLineText,
                    Karaoke.voices[0].groups[0].mltText.font.name,
                    Karaoke.voices[0].groups[0].mltText.font.style,
                    fontSongtextSizePt
                ).first
            } + Karaoke.songtextStartPositionXpx * (song.voices.size - 1).toLong()).toInt()

            maxTextWidthPxByFontSize = Integer.max(a1, a2).toLong()
        } else {
            break
        }
    }

    val fontChordsSizePt = (fontSongtextSizePt * Karaoke.chordsHeightCoefficient).toInt()

    val mltTextSongtext = Karaoke.voices[0].groups[0].mltText.copy(
        "0",
        fontSongtextSizePt
    ) //Font(Karaoke.voices[0].groups[0].songtextTextMltText.font.name, Karaoke.voices[0].groups[0].songtextTextMltText.font.style, fontSongtextSizePt)
    val mltTextChords = Karaoke.chordsFont.copy(
        "0",
        fontChordsSizePt
    ) //Font(Karaoke.chordsFont.font.name, Karaoke.chordsFont.font.style, fontChordsSizePt)

    val symbolSongtextHeightPx = mltTextSongtext.h
    val symbolSongtextWidthPx = mltTextSongtext.w

    val quarterNoteLengthMs =
        if (song.settings.ms == 0L) {
            if (song.settings.bpm == 0L) {
                (60000.0 / 120.0).toLong()
            } else {
                (60000.0 / song.settings.bpm).toLong()
            }

        } else {
            song.settings.ms
        } // Находим длительность звучания 1/4 ноты в миллисекундах
    val halfNoteLengthMs = quarterNoteLengthMs * 2

    var currentVoiceOffset = 0

    // Цикл по голосам
    for (voiceId in 0 until song.voices.size) {

        val songVoice = song.voices[voiceId]

        if (voiceId > 0) {
            currentVoiceOffset += (getTextWidthHeightPx(
                song.voices[voiceId - 1].maxWidthLineText,
                Karaoke.voices[0].groups[0].mltText.font.name,
                Karaoke.voices[0].groups[0].mltText.font.style,
                fontSongtextSizePt
            ).first + Karaoke.songtextStartPositionXpx).toInt()
        }

        val counters = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        counters[0].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[1].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[2].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[3].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[4].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

        val beats = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf()
        )

        val propRectSongtextLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propRectFaderChordsLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propRectBackChordsLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propProgressLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propHeaderLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propFlashLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propGuidesValue = mutableListOf<String>()

        val propRectSongtextValueLineOddEven = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf()
        )

        val propRectChordsValueLineOddEven = listOf<MutableList<String>>(
            mutableListOf(),
            mutableListOf()
        )

        val voiceSetting = Karaoke.voices[min(voiceId, Karaoke.voices.size - 1)]
        voiceSetting.replaceFontSize(fontSongtextSizePt)

        var currentGroup = 0 // Получаем номер текущей группы
        var groupSetting = voiceSetting.groups[min(
            currentGroup,
            voiceSetting.groups.size - 1
        )] // Получаем настройки для текущей группы
        // В строках есть и текстовые, и аккордовые, и комментарии

        // Пройдем по текстовым строкам и вставим пустые строки, если надо
        var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль
        var voiceLines = mutableListOf<SongVoiceLine>()
        val emptyLines = mutableListOf<SongVoiceLine>()
        var endTimeHidingHeaderMs: Long? = null
        songVoice.lines.filter { it.type == SongVoiceLineType.TEXT || it.type == SongVoiceLineType.EMPTY }
            .forEachIndexed { indexVoiceLineText, voiceLineText ->
                val silentDuration = convertTimecodeToMilliseconds(voiceLineText.start) - currentPositionEnd
                var linesToInsert: Long = silentDuration / songVoice.maxDurationMs
                if (indexVoiceLineText == 0 && linesToInsert == 0L) linesToInsert =
                    1L // Если строка первая - то перед ней обязательно вставляем хотя бы одну пусту строку
                if (linesToInsert > 0) {
                    val silentLineDuration: Long = silentDuration / linesToInsert
                    for (i in 1..linesToInsert) {
                        val startDuration =
                            convertMillisecondsToTimecode(currentPositionEnd) //  + silentLineDuration / 2
                        val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration)
                        val emptyVoiceLine = SongVoiceLine(
                            type = SongVoiceLineType.EMPTY,
                            subtitles = mutableListOf(),
                            symbols = mutableListOf(),
                            text = "",
                            group = voiceLineText.group,
                            start = startDuration,
                            end = endDuration,
                            startTp = null,
                            endTp = null,
                            isNeedCounter = false,
                            isFadeLine = false,
                            isMaxLine = false,
                            durationMs = silentLineDuration,
                            mltText = voiceLineText.mltText.copy("0")
                        )
                        emptyLines.add(emptyVoiceLine)
                        currentPositionEnd =
                            currentPositionEnd + silentLineDuration //convertTimecodeToMilliseconds(endDuration)
                    }
                }
                currentPositionEnd = convertTimecodeToMilliseconds(voiceLineText.end)
                if (voiceLineText.type != SongVoiceLineType.EMPTY) endTimeHidingHeaderMs = currentPositionEnd
            }
        if (convertTimecodeToMilliseconds(song.endTimecode) - endTimeHidingHeaderMs!! < 10000) endTimeHidingHeaderMs =
            convertTimecodeToMilliseconds(song.endTimecode) - 10000

        // В списке emptyLines сформирован список пустых линий, которые надо добавить к общему списку линий
        // Добавим в список voiceLines все линии из songVoice.lines (текстовые, комментные и если надо - аккордные)
        // и все пустые линии из emptyLines. Отсортируем по времени начала
        if (song.songVersion == SongVersion.CHORDS) {
            voiceLines.addAll(songVoice.lines)
        } else {
            voiceLines.addAll(songVoice.lines.filter { it.type != SongVoiceLineType.CHORDS })
        }
        voiceLines.addAll(emptyLines)
        voiceLines = voiceLines.sortedBy { convertTimecodeToMilliseconds(it.start) }.toMutableList()

        // Теперь для каждой строки нужно прописать шрифт
        // Для каждого титра строки - шрифт и текст

        voiceLines.forEach { voiceLine ->
            val fontSizeCoeff = when (voiceLine.type) {
                SongVoiceLineType.COMMENTS -> Karaoke.shortLineFontScaleCoeff
                SongVoiceLineType.CHORDS -> Karaoke.chordsHeightCoefficient
                else -> 1.0
            }
            currentGroup = voiceLine.group // Получаем номер текущей группы
            groupSetting = voiceSetting.groups[min(
                currentGroup,
                voiceSetting.groups.size - 1
            )] // Получаем настройки для текущей группы
            var currentGroupText = ""

            // Устанавливаем шрифты в зависимости от типа линии
            when (voiceLine.type) {
                SongVoiceLineType.CHORDS -> {
                    voiceLine.mltText =
                        Karaoke.chordsFont.copy(currentGroupText, (fontSongtextSizePt * fontSizeCoeff).toInt())
                    voiceLine.subtitles.forEach { subtitle ->
                        subtitle.mltText = voiceLine.mltText.copy(subtitle.mltText.text)
                        subtitle.mltTextBefore =
                            subtitle.mltTextBefore.copy(subtitle.mltTextBefore.text, fontSongtextSizePt)
                    }
                    voiceLine.symbols.forEach { symbol ->
                        symbol.mltText = voiceLine.mltText.copy(symbol.mltText.text)
                        symbol.mltTextBefore = symbol.mltTextBefore.copy(symbol.mltTextBefore.text, fontSongtextSizePt)
                    }
                }

                else -> {
                    voiceLine.mltText =
                        groupSetting.mltText.copy(currentGroupText, (fontSongtextSizePt * fontSizeCoeff).toInt())
                    voiceLine.subtitles.forEach { subtitle ->
                        subtitle.mltText = groupSetting.mltText.copy(
                            subtitle.mltText.text,
                            (fontSongtextSizePt * fontSizeCoeff).toInt()
                        )
                        subtitle.mltTextBefore = groupSetting.mltText.copy(
                            subtitle.mltTextBefore.text,
                            (fontSongtextSizePt * fontSizeCoeff).toInt()
                        )
                    }
                    voiceLine.symbols.forEach { symbol ->
                        symbol.mltText =
                            groupSetting.mltText.copy(symbol.mltText.text, (fontSongtextSizePt * fontSizeCoeff).toInt())
                        symbol.mltTextBefore = groupSetting.mltText.copy(
                            symbol.mltTextBefore.text,
                            (fontSongtextSizePt * fontSizeCoeff).toInt()
                        )
                    }
                }
            }

            // Если линия - текстовая - проходим по субтитрам и формирум лайнсимволы имея в виду биты если надо
            if (voiceLine.type == SongVoiceLineType.TEXT) {
                var textBefore = ""
                for (subtitle in voiceLine.subtitles) {
                    currentGroupText += subtitle.mltText.text
                }
                if (currentGroupText != "") {
                    voiceLine.symbols.add(
                        SongVoiceLineSymbol(
                            start = "",
                            mltText = voiceLine.mltText.copy(currentGroupText)
                        )
                    )
                }
            }

        }

        // Отметим те линии, для которых нужны счётчики
        var prevLineInEmptyOrComment = true
        voiceLines.forEach { voiceLine ->
            if (voiceLine.type !in listOf(SongVoiceLineType.EMPTY)) {
                voiceLine.isNeedCounter = prevLineInEmptyOrComment
            }
            prevLineInEmptyOrComment = (voiceLine.type in listOf(SongVoiceLineType.EMPTY))
        }

        // Пройдёмся по линиям и пропишем координаты Y для строк
        var currLinePositionY = 0
        voiceLines.forEach { voiceLine ->
            voiceLine.yPx = currLinePositionY
            currLinePositionY += voiceLine.hPx
        }
        val workAreaSongtextHeightPx = currLinePositionY

        val horizonPositionPx =
            ((Karaoke.frameHeightPx / 2 + symbolSongtextHeightPx.toLong() / 2) - Karaoke.horizonOffsetPx).toInt()    // horizonPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет

        // Пройдёмся по линиям и пропишем TransformProperty
        voiceLines.filter { it.type in listOf(SongVoiceLineType.TEXT, SongVoiceLineType.EMPTY) }
            .forEachIndexed { indexVoiceLine, voiceLine ->

                val startTp = TransformProperty(
                    time = voiceLine.start,
                    x = currentVoiceOffset,
                    y = horizonPositionPx - voiceLine.yPx - voiceLine.hPx,
                    w = Karaoke.frameWidthPx,
                    h = workAreaSongtextHeightPx,
                    opacity = if (indexVoiceLine in 1 until voiceLines.filter {
                            it.type in listOf(
                                SongVoiceLineType.TEXT,
                                SongVoiceLineType.EMPTY
                            )
                        }.size - 1) 1.0 else 0.0
                )

                var time = voiceLine.end
                // Если текущий элемент не последний - надо проверить начало следующего элемента
                if (indexVoiceLine != voiceLines.filter {
                        it.type in listOf(
                            SongVoiceLineType.TEXT,
                            SongVoiceLineType.EMPTY
                        )
                    }.size - 1) {
                    val nextVoiceLine = voiceLines.filter {
                        it.type in listOf(
                            SongVoiceLineType.TEXT,
                            SongVoiceLineType.EMPTY
                        )
                    }[indexVoiceLine + 1] // Находим следующую строку
                    val diffInMills = getDiffInMilliseconds(
                        nextVoiceLine.start,
                        voiceLine.end
                    ) // Находим разницу во времени между текущей строкой и следующей
                    if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {                            // Если эта разница меньше 200 мс
                        voiceLine.end =
                            nextVoiceLine.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                        if (voiceLine.subtitles.isNotEmpty()) {
                            voiceLine.subtitles.last().endTimecode = voiceLine.end
                            time =
                                voiceLine.subtitles.last().startTimecode       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
                        }
                    }
                }

                val endTp = TransformProperty(
                    time = time,
                    x = currentVoiceOffset,
                    y = horizonPositionPx - voiceLine.yPx - voiceLine.hPx,
                    w = Karaoke.frameWidthPx,
                    h = workAreaSongtextHeightPx,
                    opacity = if (indexVoiceLine in 1 until voiceLines.filter {
                            it.type in listOf(
                                SongVoiceLineType.TEXT,
                                SongVoiceLineType.EMPTY
                            )
                        }.size - 1) 1.0 else 0.0
                )

                voiceLine.startTp = startTp
                voiceLine.endTp = endTp

            }

        songVoice.lines = voiceLines

        val songLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val progressSymbolHalfWidth =
            0 //(getTextWidthHeightPx(Karaoke.progressSymbol, Karaoke.progressFont.font).first/2).toLong()
        val kdeHeaderAuthor = song.settings.author
        val kdeHeaderTone = song.settings.key
        val kdeHeaderBpm = song.settings.bpm
        val kdeHeaderAlbum = song.settings.album
        val kdeHeaderYear = song.settings.year
        val kdeHeaderTrack = song.settings.track
        val kdeHeaderSongName = song.settings.songName
        val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / song.settings.songName.length), 80)
        val yOffset = 0 //-5

        propProgressLineValue.add("00:00:00.000=-${progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propProgressLineValue.add("${song.endTimecode}=${Karaoke.frameWidthPx - progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propFlashLineValue.add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

        param["VOICE${voiceId}_SETTING"] = voiceSetting
        param["VOICE${voiceId}_OFFSET"] = currentVoiceOffset
        param["VOICE${voiceId}_WORK_AREA_SONGTEXT_HEIGHT_PX"] = workAreaSongtextHeightPx
        param["VOICE${voiceId}_VOICELINES_SONGTEXT"] = voiceLines

        param["SYMBOL_SONGTEXT_HEIGHT_PX"] = symbolSongtextHeightPx
        param["HORIZON_POSITION_PX"] = horizonPositionPx
        param["COUNTER_POSITION_Y_PX"] = (horizonPositionPx - symbolSongtextHeightPx).toLong()
        param["VOICE${voiceId}_COUNTER_POSITION_X_PX"] =
            currentVoiceOffset + Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx - symbolSongtextWidthPx
        param["SONG_LENGTH_MS"] = songLengthMs
        param["FONT_SONGTEXT_SIZE_PT"] = fontSongtextSizePt
        param["FONT_CHORDS_SIZE_PT"] = fontChordsSizePt

        param["HEADER_AUTHOR"] = kdeHeaderAuthor
        param["HEADER_TONE"] = kdeHeaderTone
        param["HEADER_BPM"] = kdeHeaderBpm
        param["HEADER_ALBUM"] = kdeHeaderAlbum
        param["HEADER_YEAR"] = kdeHeaderYear
        param["HEADER_TRACK"] = kdeHeaderTrack
        param["HEADER_SONG_NAME"] = kdeHeaderSongName
        param["HEADER_SONG_NAME_FONT_SIZE"] = fontNameSizePt
        param["LOGOAUTHOR_PATH"] = "${song.settings.rootFolder}/LogoAuthor.png"
        param["LOGOALBUM_PATH"] = "${song.settings.rootFolder}/LogoAlbum.png"

        // Настало время прописать classes.TransformProperty для заливок

        val opacityFillValue = voiceSetting.fill.evenOpacity
        voiceLines.filter { it.type == SongVoiceLineType.TEXT }.forEachIndexed { indexLine, voiceLineText ->
            propFlashLineValue.add("${convertFramesToTimecode(convertTimecodeToFrames(voiceLineText.startTp!!.time) - 2)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
            propFlashLineValue.add("${voiceLineText.startTp?.time}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
            propFlashLineValue.add("${convertMillisecondsToTimecode(convertTimecodeToMilliseconds(voiceLineText.startTp!!.time) + 1000)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

            propRectSongtextValueLineOddEven[indexLine % 2].add(voiceLineText.getFillTps(songVoice, horizonPositionPx, opacityFillValue))
        }

        // Настало время заняться счётчиками вступления.

        var startTimeFirstCounterMs: Long? = null
        voiceLines.filter { it.isNeedCounter }
            .forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
                if (startTimeFirstCounterMs == null) startTimeFirstCounterMs =
                    convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * 4
                for (counterNumber in 0..4) {
                    val startTimeMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * counterNumber
                    val initTimeMs = convertFramesToMilliseconds(convertMillisecondsToFrames(startTimeMs) - 2)
                    val endTimeMs =
                        convertFramesToMilliseconds(convertMillisecondsToFrames(startTimeMs + halfNoteLengthMs) - 2)
                    if (startTimeMs > 0) {
                        counters[counterNumber].add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(endTimeMs)}=0 ${-symbolSongtextHeightPx} ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

                        propFlashLineValue.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                        propFlashLineValue.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
                        propFlashLineValue.add("${convertMillisecondsToTimecode(endTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

                    }
                }
            }
        if (startTimeFirstCounterMs == null || startTimeFirstCounterMs!! < 0L) startTimeFirstCounterMs = 3000

        val kdeIn = "00:00:00.000"
        val kdeFadeIn = "00:00:01.000"
        val kdeOut = song.endTimecode.replace(",", ".")
        val kdeFadeOut =
            convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.endTimecode) - 1000).replace(",", ".")

        val chords = songVoice.lines.filter { it.type == SongVoiceLineType.CHORDS }
            .flatMap { it.symbols }.toList()
        val countFingerboards = (chords.size / Karaoke.maxCountChordsInFingerboard) + 1
        val propRectFingerboardLineValue: MutableMap<Int, MutableList<String>> = mutableMapOf()
        val propRectFingerboardValues: MutableMap<Int, String> = mutableMapOf()
        val chordW = (Karaoke.frameHeightPx / 4)
        val fingerboardW: MutableMap<Int, Int> = mutableMapOf()
        val fingerboardH = chordW

        param["VOICE${voiceId}_FINGERBOARD_H"] = fingerboardH
        param["VOICE${voiceId}_CHORD_W"] = chordW
        param["VOICE${voiceId}_CHORD_H"] = chordW
        param["VOICE${voiceId}_COUNT_FINGERBOARDS"] = 0
        if (song.songVersion == SongVersion.CHORDS && voiceId == 0) {

            propRectFaderChordsLineValue.add("${kdeIn}=0 -${fingerboardH + 50} ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")
            propRectFaderChordsLineValue.add("${kdeFadeIn}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")

            propRectBackChordsLineValue.add("${kdeIn}=0 -${fingerboardH} ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")
            propRectBackChordsLineValue.add("${kdeFadeIn}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")

            val chordXoffsetPx = Karaoke.frameWidthPx / 2 - chordW / 2 + chordW
            val chordsInFingerboards: MutableMap<Int, MutableList<SongVoiceLineSymbol>> = mutableMapOf()

            for (i in 0 until countFingerboards) {
                fingerboardW[i] = if (chords.size >= (i + 1) * Karaoke.maxCountChordsInFingerboard) {
                    Karaoke.maxCountChordsInFingerboard * chordW
                } else {
                    (chords.size % Karaoke.maxCountChordsInFingerboard) * chordW
                }
                chordsInFingerboards[i] = mutableListOf()
                propRectFingerboardLineValue[i] = mutableListOf()
                propRectFingerboardLineValue[i]?.add("${kdeIn}=${chordXoffsetPx} -${fingerboardH + 50} ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                propRectFingerboardLineValue[i]?.add("${kdeFadeIn}=${chordXoffsetPx} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
            }

            var prevChordX = 0
            var prevChordTimeCode = kdeFadeIn
            var currChordX = 0

            var endMoveTimecode = ""
            var prevFingerboardIndex = 0
            chords.forEachIndexed { indexChords, chord ->
                val currFingerboardIndex = indexChords / Karaoke.maxCountChordsInFingerboard
                chordsInFingerboards[currFingerboardIndex]?.add(chord)
                val chordTimecode = chord.start
                val diffChordsMs =
                    convertTimecodeToMilliseconds(chordTimecode) - convertTimecodeToMilliseconds(prevChordTimeCode)
                val movingMs = if (diffChordsMs > 500) 500 else (diffChordsMs / 2).toInt()
                val startMoveTimecode =
                    convertMillisecondsToTimecode(convertTimecodeToMilliseconds(chordTimecode) - movingMs)
                endMoveTimecode = chordTimecode
                currChordX = prevChordX - chordW
                for (i in 0 until countFingerboards) {
                    propRectFingerboardLineValue[i]?.add("${startMoveTimecode}=${prevChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                    propRectFingerboardLineValue[i]?.add("${endMoveTimecode}=${currChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                }
                prevChordX = currChordX
            }

            if (convertTimecodeToMilliseconds(endMoveTimecode) > endTimeHidingHeaderMs!!) {
                endTimeHidingHeaderMs = convertTimecodeToMilliseconds(endMoveTimecode)
//                endMoveTimecode = convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)
            }

            for (i in 0 until countFingerboards) {
                propRectFingerboardLineValue[i]?.add("${endMoveTimecode}=${currChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} 0 ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
                propRectFingerboardLineValue[i]?.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=${currChordX + chordXoffsetPx + (i * Karaoke.maxCountChordsInFingerboard * chordW)} -${fingerboardH + 50} ${fingerboardW[i]} ${fingerboardH + 50} 1.0")
            }

            for (i in 0 until countFingerboards) {
                propRectFingerboardValues[i] = propRectFingerboardLineValue[i]?.joinToString(";") ?: ""
                param["VOICE${voiceId}${i}_CHORDS"] = chordsInFingerboards[i]
                param["VOICE${voiceId}${i}_FINGERBOARD_W"] = fingerboardW[i]
            }

            param["VOICE${voiceId}_COUNT_FINGERBOARDS"] = countFingerboards

            propRectFaderChordsLineValue.add("${endMoveTimecode}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")
            propRectFaderChordsLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=0 -${fingerboardH + 50} ${Karaoke.frameWidthPx} ${fingerboardH + 50} 1.0")

            propRectBackChordsLineValue.add("${endMoveTimecode}=0 0 ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")
            propRectBackChordsLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=0 -${fingerboardH} ${Karaoke.frameWidthPx} ${fingerboardH} 1.0")


        }



        if (song.songVersion != SongVersion.CHORDS) {
            propHeaderLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!!)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        }
        propHeaderLineValue.add("${convertMillisecondsToTimecode(startTimeFirstCounterMs!! + halfNoteLengthMs * 4)}=0 -492 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!!)}=0 -492 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propHeaderLineValue.add("${convertMillisecondsToTimecode(endTimeHidingHeaderMs!! + halfNoteLengthMs * 4)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")

        val propRectSongtextValue = propRectSongtextLineValue.joinToString(";")
        val propRectFaderChordsValue = propRectFaderChordsLineValue.joinToString(";")
        val propRectBackChordsValue = propRectBackChordsLineValue.joinToString(";")
        val propProgressValue = propProgressLineValue.joinToString(";")
        val propHeaderValue = propHeaderLineValue.joinToString(";")
        val propFlashValue = propFlashLineValue.joinToString(";")
        val propSongtextFillOddValue = propRectSongtextValueLineOddEven[0].joinToString(";")
        val propSongtextFillEvenValue = propRectSongtextValueLineOddEven[1].joinToString(";")
        val propFillCounter0Value = counters[0].joinToString(";")
        val propFillCounter1Value = counters[1].joinToString(";")
        val propFillCounter2Value = counters[2].joinToString(";")
        val propFillCounter3Value = counters[3].joinToString(";")
        val propFillCounter4Value = counters[4].joinToString(";")
        val propGuides = propGuidesValue.joinToString(",")

        val kdeInOffsetAudio =
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs - if (isBluetoothDelay) Karaoke.timeOffsetBluetoothSpeakerMs else 0) // convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + max(Karaoke.timeOffsetStartFillingLineMs.toInt(),0).absoluteValue)
        val kdeInOffsetVideo =
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs) // convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + max(-Karaoke.timeOffsetStartFillingLineMs.toInt(),0).absoluteValue)
        val kdeLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val kdeLengthFrames = convertTimecodeToFrames(song.endTimecode, Karaoke.frameFps)

        param["SONG_ROOT_FOLDER"] = song.settings.rootFolder
        param["SONG_START_TIMECODE"] = kdeIn
        param["SONG_END_TIMECODE"] = kdeOut
        param["SPLASHSTART_END_TIMECODE"] = convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs)
        param["BOOSTY_END_TIMECODE"] = convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs)
        param["SONG_FADEIN_TIMECODE"] = kdeFadeIn
        param["SONG_FADEOUT_TIMECODE"] = kdeFadeOut
        param["SPLASHSTART_FADEOUT_TIMECODE"] =
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs - 1000).replace(",", ".")
        param["BOOSTY_FADEIN_TIMECODE"] =
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + 1000).replace(",", ".")
        param["BOOSTY_FADEOUT_TIMECODE"] =
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs - 1000).replace(",", ".")
        param["SONG_LENGTH_MS"] = kdeLengthMs
        param["SONG_LENGTH_FR"] = kdeLengthFrames
        param["TOTAL_LENGTH_MS"] = kdeLengthMs + Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs
        param["TOTAL_LENGTH_FR"] = convertMillisecondsToFrames(kdeLengthMs + Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs, Karaoke.frameFps)
        param["GUIDES_PROPERTY"] = "[${propGuides}]"
        param["IN_OFFSET_AUDIO"] = kdeInOffsetAudio
        param["IN_OFFSET_VIDEO"] = kdeInOffsetVideo


        val templateSongText = getTemplateSongText(param, voiceId)
        val templateSongTextIgnoreCapo = getTemplateSongText(param, voiceId, true)
        val templateHorizon = getTemplateHorizon(param)
        val templateFlash = getTemplateFlash(param)
        val templateProgress = getTemplateProgress(param)
        val templateWatermark = getTemplateWatermark(param)
        val templateFaderText = getTemplateFaderText(param)
        val templateFaderChords = getTemplateFaderChords(param)
        val templateBackChords = getTemplateBackChords(param)
        val templateHeader = getTemplateHeader(param)
        val templateSplashstart = getTemplateSplashstart(param)
        val templateBoosty = getTemplateBoosty(param)
        val templateCounter0 = getTemplateCounter(param, 0, voiceId)
        val templateCounter1 = getTemplateCounter(param, 1, voiceId)
        val templateCounter2 = getTemplateCounter(param, 2, voiceId)
        val templateCounter3 = getTemplateCounter(param, 3, voiceId)
        val templateCounter4 = getTemplateCounter(param, 4, voiceId)

        val templateFingerboards: MutableMap<Int, MltNode> = mutableMapOf()
        if (song.songVersion == SongVersion.CHORDS) {
            for (i in 0 until countFingerboards) {
                templateFingerboards[i] = getTemplateFingerboard(param, i)
            }
        }

//        val templateFingerboard = getTemplateFingerboard(param)

        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_ID"] = idProducerAudioSong
        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_PATH"] = song.settings.audioSongFileName

        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_ID"] = idProducerAudioMusic
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_PATH"] = song.settings.audioMusicFileName

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_ID"] = idProducerAudioVocal
        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_PATH"] = song.settings.audioVocalFileName
        param["HIDE_TRACTOR_${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}"] = "both"

        param["${ProducerType.AUDIOBASS.text.uppercase()}${voiceId}_ID"] = idProducerAudioBass
        param["${ProducerType.AUDIOBASS.text.uppercase()}${voiceId}_PATH"] = song.settings.audioBassFileName

        param["${ProducerType.AUDIODRUMS.text.uppercase()}${voiceId}_ID"] = idProducerAudioDrums
        param["${ProducerType.AUDIODRUMS.text.uppercase()}${voiceId}_PATH"] = song.settings.audioDrumsFileName

        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_ID"] = idProducerSongText
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_WORK_AREA_SONGTEXT_HEIGHT_PX"] =
            workAreaSongtextHeightPx.toLong()
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_XML_DATA"] = templateSongText
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_XML_DATA_IGNORE_CAPO"] = templateSongTextIgnoreCapo
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectSongtextValue
        param["HIDE_TRACTOR_${ProducerType.SONGTEXT.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_ID"] = idProducerHorizon
        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_XML_DATA"] = templateHorizon
        param["HIDE_TRACTOR_${ProducerType.HORIZON.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FLASH.text.uppercase()}${voiceId}_ID"] = idProducerFlash
        param["${ProducerType.FLASH.text.uppercase()}${voiceId}_XML_DATA"] = templateFlash
        param["${ProducerType.FLASH.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propFlashValue
        param["HIDE_TRACTOR_${ProducerType.FLASH.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_ID"] = idProducerWatermark
        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_XML_DATA"] = templateWatermark
        param["HIDE_TRACTOR_${ProducerType.WATERMARK.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FADERTEXT.text.uppercase()}${voiceId}_ID"] = idProducerFaderText
        param["${ProducerType.FADERTEXT.text.uppercase()}${voiceId}_XML_DATA"] = templateFaderText
        param["HIDE_TRACTOR_${ProducerType.FADERTEXT.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_ID"] = idProducerFaderChords
        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_XML_DATA"] = templateFaderChords
        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectFaderChordsValue
        param["HIDE_TRACTOR_${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.BACKCHORDS.text.uppercase()}${voiceId}_ID"] = idProducerBackChords
        param["${ProducerType.BACKCHORDS.text.uppercase()}${voiceId}_XML_DATA"] = templateBackChords
        param["${ProducerType.BACKCHORDS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectBackChordsValue
        param["HIDE_TRACTOR_${ProducerType.BACKCHORDS.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}_ID"] = idProducerFingerboard

        for (i in 0 until countFingerboards) {
            param["${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}${i}_XML_DATA"] = templateFingerboards[i]
            param["${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}${i}_PROPERTY_RECT"] =
                propRectFingerboardValues[i]
        }
        param["HIDE_TRACTOR_${ProducerType.FINGERBOARD.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_ID"] = idProducerProgress
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_XML_DATA"] = templateProgress
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propProgressValue
        param["HIDE_TRACTOR_${ProducerType.PROGRESS.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_EVEN_ID"] = idProducerFillColorSongtextEven
        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_EVEN_PROPERTY_RECT"] =
            propSongtextFillEvenValue

        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_ODD_ID"] = idProducerFillColorSongtextOdd
        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_ODD_PROPERTY_RECT"] =
            propSongtextFillOddValue

        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_ID"] = idProducerHeader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_XML_DATA"] = templateHeader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propHeaderValue
        param["HIDE_TRACTOR_${ProducerType.HEADER.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.SPLASHSTART.text.uppercase()}${voiceId}_ID"] = idProducerSplashstart
        param["${ProducerType.SPLASHSTART.text.uppercase()}${voiceId}_XML_DATA"] = templateSplashstart
        param["HIDE_TRACTOR_${ProducerType.SPLASHSTART.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.BOOSTY.text.uppercase()}${voiceId}_ID"] = idProducerBoosty
        param["${ProducerType.BOOSTY.text.uppercase()}${voiceId}_XML_DATA"] = templateBoosty
        param["HIDE_TRACTOR_${ProducerType.BOOSTY.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_ID"] = idProducerBackground
        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_PATH"] =
            getRandomFile(Karaoke.backgroundFolderPath, ".png")
        param["HIDE_TRACTOR_${ProducerType.BACKGROUND.text.uppercase()}${voiceId}"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}0_ID"] = idProducerCounter0
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}0_XML_DATA"] = templateCounter0
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}0_PROPERTY_RECT"] = propFillCounter0Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}0"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}1_ID"] = idProducerCounter1
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}1_XML_DATA"] = templateCounter1
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}1_PROPERTY_RECT"] = propFillCounter1Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}1"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}2_ID"] = idProducerCounter2
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}2_XML_DATA"] = templateCounter2
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}2_PROPERTY_RECT"] = propFillCounter2Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}2"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}3_ID"] = idProducerCounter3
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}3_XML_DATA"] = templateCounter3
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}3_PROPERTY_RECT"] = propFillCounter3Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}3"] = "audio"

        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}4_ID"] = idProducerCounter4
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}4_XML_DATA"] = templateCounter4
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}4_PROPERTY_RECT"] = propFillCounter4Value
        param["HIDE_TRACTOR_${ProducerType.COUNTER.text.uppercase()}${voiceId}4"] = "audio"

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioVocal
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioMusic
        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioSong
        param["${ProducerType.AUDIOBASS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioBass
        param["${ProducerType.AUDIODRUMS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioDrums
        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createBackground
        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createHorizon
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createProgress
        param["${ProducerType.FILLCOLORSONGTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFillsSongtext
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createSongtext
        param["${ProducerType.FADERTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFader
        param["${ProducerType.FADERCHORDS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createHeader
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createCounters
        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createWatermark

    }

    param["SONG_PROJECT_RUNALL_FILENAME"] = song.getOutputFilename(SongOutputFile.RUNALL, isBluetoothDelay)
    param["SONG_PROJECT_RUN_FILENAME"] = song.getOutputFilename(SongOutputFile.RUN, isBluetoothDelay)
    param["SONG_PROJECT_MELT_FILENAME"] = song.getOutputFilename(SongOutputFile.MLT, isBluetoothDelay)
    param["SONG_PROJECT_FILENAME"] = song.getOutputFilename(SongOutputFile.PROJECT, isBluetoothDelay)
    param["SONG_VIDEO_FILENAME"] = song.getOutputFilename(SongOutputFile.VIDEO, isBluetoothDelay)
    param["SONG_PICTURE_FILENAME"] = song.getOutputFilename(SongOutputFile.PICTURE, isBluetoothDelay)
    param["SONG_PICTURECHORDS_FILENAME"] = song.getOutputFilename(SongOutputFile.PICTURECHORDS, isBluetoothDelay)
    param["SONG_SUBTITLE_FILENAME"] = song.getOutputFilename(SongOutputFile.SUBTITLE, isBluetoothDelay)
    param["SONG_DESCRIPTION_FILENAME"] = song.getOutputFilename(SongOutputFile.DESCRIPTION, isBluetoothDelay)
    param["SONG_TEXT_FILENAME"] = song.getOutputFilename(SongOutputFile.TEXT, isBluetoothDelay)

    val permissions = PosixFilePermissions.fromString("rwxr-x---")

    val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(param)}"
    val fileProject = File(param["SONG_PROJECT_FILENAME"].toString())
    val fileMlt = File(param["SONG_PROJECT_MELT_FILENAME"].toString())
    Files.createDirectories(Path(fileProject.parent))
    fileProject.writeText(templateProject)
    fileMlt.writeText(templateProject)
    val fileRun = File(param["SONG_PROJECT_RUN_FILENAME"].toString())
    fileRun.writeText("echo \"${param["SONG_PROJECT_MELT_FILENAME"].toString()}\"\n" +
            "melt -progress \"${param["SONG_PROJECT_MELT_FILENAME"].toString()}\"\n")
    Files.setPosixFilePermissions(fileRun.toPath(), permissions)

    val fileDescription = File(param["SONG_DESCRIPTION_FILENAME"].toString())
    Files.createDirectories(Path(fileDescription.parent))
    fileDescription.writeText(song.getDescription(isBluetoothDelay))

    if (song.songVersion == SongVersion.LYRICS) createBoostyTeaserPicture(song, song.getOutputFilename(SongOutputFile.PICTUREBOOSTY, false))
    if (song.songVersion == SongVersion.LYRICS) createVKPicture(song, song.getOutputFilename(SongOutputFile.PICTUREVK, false))

    val fileText = File(param["SONG_TEXT_FILENAME"].toString())
    Files.createDirectories(Path(fileText.parent))
    fileText.writeText(song.getText())

    val filePictures = File(param["SONG_DESCRIPTION_FILENAME"].toString())
    Files.createDirectories(Path(filePictures.parent))
    createSongPicture(song, param["SONG_PICTURE_FILENAME"].toString(), song.songVersion, isBluetoothDelay)

    val filePictureChords = File(param["SONG_PICTURECHORDS_FILENAME"].toString())
    Files.createDirectories(Path(filePictureChords.parent))
    createSongChordsPicture(song, param["SONG_PICTURECHORDS_FILENAME"].toString(), song.songVersion, isBluetoothDelay, param["${ProducerType.SONGTEXT.text.uppercase()}${0}_XML_DATA_IGNORE_CAPO"] as MltNode)

}

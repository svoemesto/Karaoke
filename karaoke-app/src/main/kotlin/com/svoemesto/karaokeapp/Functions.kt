package com.svoemesto.karaokeapp//import model.Lyric
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.getMlt
import com.svoemesto.karaokeapp.mlt.mko.*
import com.svoemesto.karaokeapp.model.*
import java.io.File
import java.lang.Integer.min
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
//import java.nio.file.Path
import kotlin.io.path.Path

fun createKaraoke(song: Song) {

    val mltProp = MltProp()

    if (song.songVersion == SongVersion.CHORDS && !song.hasChords) return
    println("Создаём ${song.songVersion.name}: ${song.settings.author} / ${song.settings.songName}")

//    val param = mutableMapOf<String, Any?>()

    mltProp.setSongVersion(song.songVersion)
    mltProp.setSongCapo(song.capo)
    mltProp.setSongChordDescription(song.getChordDescription())
    mltProp.setSongName(song.settings.songName.replace("&", "&amp;"))
    mltProp.setCountVoices(song.voices.size)
    mltProp.setLineSpacing(LINE_SPACING)
    mltProp.setShadow(SHADOW)
    mltProp.setTypeWriter(TYPEWRITER)
    mltProp.setAlignment(ALIGNMENT)

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

    val propAudioVolumeOnValue = song.propAudioVolumeOn
    val propAudioVolumeOffValue = song.propAudioVolumeOff
    val propAudioVolumeCustomValue = song.propAudioVolumeCustom

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

        mltProp.setVoiceSetting(voiceSetting, voiceId)
        mltProp.setOffset(currentVoiceOffset, voiceId)
        mltProp.setWorkAreaHeightPx(workAreaSongtextHeightPx.toLong(), listOf(ProducerType.SONGTEXT,voiceId))
        mltProp.setVoicelines(voiceLines, listOf(ProducerType.SONGTEXT,voiceId))
        mltProp.setSymbolHeightPx(symbolSongtextHeightPx, ProducerType.SONGTEXT)
        mltProp.setPositionYPx(horizonPositionPx.toLong(), ProducerType.HORIZON)
        mltProp.setPositionYPx((horizonPositionPx - symbolSongtextHeightPx).toLong(), ProducerType.COUNTER)
        mltProp.setPositionXPx(
            currentVoiceOffset + Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx - symbolSongtextWidthPx,
            listOf(ProducerType.COUNTER, voiceId))
        mltProp.setLengthMs(songLengthMs, "Song")
        mltProp.setFontSizePt(fontSongtextSizePt, ProducerType.SONGTEXT)
        mltProp.setFontSizePt(fontChordsSizePt, ProducerType.FADERCHORDS)
        mltProp.setAuthor(kdeHeaderAuthor.replace("&", "&amp;amp;"), ProducerType.HEADER)
        mltProp.setTone(kdeHeaderTone, ProducerType.HEADER)
        mltProp.setBpm(kdeHeaderBpm, ProducerType.HEADER)
        mltProp.setAlbum(kdeHeaderAlbum.replace("&", "&amp;amp;"), ProducerType.HEADER)
        mltProp.setYear(kdeHeaderYear, ProducerType.HEADER)
        mltProp.setTrack(kdeHeaderTrack, ProducerType.HEADER)
        mltProp.setSongName(kdeHeaderSongName.replace("&", "&amp;amp;"), ProducerType.HEADER)
        mltProp.setFontSizePt(fontNameSizePt, listOf(ProducerType.HEADER, "SongName"))
        mltProp.setPath("${song.settings.pathToFileLogoAuthor.replace("&", "&amp;amp;")}", "LogoAuthor")
        mltProp.setPath("${song.settings.pathToFileLogoAlbum.replace("&", "&amp;amp;")}", "LogoAlbum")
        mltProp.setBase64(song.settings.pathToFileLogoAuthor.base64ifFileExists(), "LogoAuthor")
        mltProp.setBase64(song.settings.pathToFileLogoAlbum.base64ifFileExists(), "LogoAlbum")
        mltProp.setPath("/home/nsa/Documents/Караоке/SPLASH.png", ProducerType.BOOSTY)
        mltProp.setBase64("/home/nsa/Documents/Караоке/SPLASH.png".base64ifFileExists(), ProducerType.BOOSTY)

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

        mltProp.setFingerboardH(fingerboardH, voiceId)
        mltProp.setChordW(chordW, voiceId)
        mltProp.setChordH(chordW, voiceId)
        mltProp.setCountFingerboards(0, voiceId)

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
                chordsInFingerboards[i]?.let { mltProp.setChords(it, listOf(voiceId, i)) }
                fingerboardW[i]?.let { mltProp.setFingerboardW(it, listOf(voiceId, i)) }
            }

            mltProp.setCountFingerboards(countFingerboards, voiceId)

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
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs) // convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + max(Karaoke.timeOffsetStartFillingLineMs.toInt(),0).absoluteValue)
        val kdeInOffsetVideo =
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs) // convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + max(-Karaoke.timeOffsetStartFillingLineMs.toInt(),0).absoluteValue)
        val kdeLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val kdeLengthFrames = convertTimecodeToFrames(song.endTimecode, Karaoke.frameFps)

        mltProp.setRootFolder(song.settings.rootFolder.replace("&", "&amp;"), "Song")
        mltProp.setStartTimecode(kdeIn, "Song")
        mltProp.setEndTimecode(kdeOut, "Song")
        mltProp.setEndTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs), ProducerType.SPLASHSTART)
        mltProp.setEndTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs), ProducerType.BOOSTY)
        mltProp.setFadeInTimecode(kdeFadeIn, "Song")
        mltProp.setFadeOutTimecode(kdeFadeOut, "Song")
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs - 1000).replace(",", ".")
        mltProp.setFadeOutTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs - 1000).replace(",", "."), ProducerType.SPLASHSTART)
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + 1000).replace(",", ".")
        mltProp.setFadeInTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + 1000).replace(",", "."), ProducerType.BOOSTY)
            convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs - 1000).replace(",", ".")
        mltProp.setFadeOutTimecode(convertMillisecondsToTimecode(Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs - 1000).replace(",", "."), ProducerType.BOOSTY)
        mltProp.setLengthMs(kdeLengthMs, "Song")
        mltProp.setLengthFr(kdeLengthFrames, "Song")
        mltProp.setLengthMs(kdeLengthMs + Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs, "Total")
        mltProp.setLengthFr(convertMillisecondsToFrames(kdeLengthMs + Karaoke.timeSplashScreenStartMs + Karaoke.timeBoostyStartMs, Karaoke.frameFps), "Total")
        mltProp.setGuidesProperty("[${propGuides}]")
        mltProp.setInOffsetAudio(kdeInOffsetAudio)
        mltProp.setInOffsetVideo(kdeInOffsetVideo)

        val templateSongText = MkoSongText(mltProp, voiceId, ignoreCapo = false).template()
        val templateSongTextIgnoreCapo = MkoSongText(mltProp, voiceId, ignoreCapo = true).template()
        val templateSongTextLine = MkoSongTextLine(mltProp).template()
        val templateHorizon = MkoHorizon(mltProp).template()
        val templateFlash = MkoFlash(mltProp).template()
        val templateProgress = MkoProgress(mltProp).template()
        val templateWatermark = MkoWatermark(mltProp).template()
        val templateFaderText = MkoFaderText(mltProp).template()
        val templateFaderChords = MkoFaderChords(mltProp).template()
        val templateBackChords = MkoBackChords(mltProp).template()
        val templateHeader = MkoHeader(mltProp).template()
        val templateSplashstart = MkoSplashStart(mltProp).template()
        val templateBoosty = MkoBoosty(mltProp).template()
        val templateCounter0 = MkoCounter(mltProp, 0, voiceId).template()
        val templateCounter1 = MkoCounter(mltProp, 1, voiceId).template()
        val templateCounter2 = MkoCounter(mltProp, 2, voiceId).template()
        val templateCounter3 = MkoCounter(mltProp, 3, voiceId).template()
        val templateCounter4 = MkoCounter(mltProp, 4, voiceId).template()

        val templateFingerboards: MutableMap<Int, MltNode> = mutableMapOf()
        if (song.songVersion == SongVersion.CHORDS) {
            for (i in 0 until countFingerboards) {
                templateFingerboards[i] = MkoFingerboard(mltProp, i).template()
            }
        }

//        val templateFingerboard = getTemplateFingerboard(param)

        mltProp.setId(idProducerAudioSong, listOf(ProducerType.AUDIOSONG, voiceId))
        mltProp.setPath(song.settings.audioSongFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOSONG, voiceId))

        mltProp.setId(idProducerAudioMusic, listOf(ProducerType.AUDIOMUSIC, voiceId))
        mltProp.setPath(song.settings.audioMusicFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOMUSIC, voiceId))

        mltProp.setId(idProducerAudioVocal, listOf(ProducerType.AUDIOVOCAL, voiceId))
        mltProp.setPath(song.settings.audioVocalFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOVOCAL, voiceId))

        mltProp.setId(idProducerAudioBass, listOf(ProducerType.AUDIOBASS, voiceId))
        mltProp.setPath(song.settings.audioBassFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOBASS, voiceId))

        mltProp.setId(idProducerAudioDrums, listOf(ProducerType.AUDIODRUMS, voiceId))
        mltProp.setPath(song.settings.audioDrumsFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIODRUMS, voiceId))

        mltProp.setId(idProducerSongText, listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setWorkAreaHeightPx(workAreaSongtextHeightPx.toLong(), listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setXmlData(templateSongText, listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setXmlData(templateSongTextIgnoreCapo, listOf(ProducerType.SONGTEXT, voiceId, "IgnoreCapo"))
        mltProp.setRect(propRectSongtextValue, listOf(ProducerType.SONGTEXT, voiceId))

        mltProp.setId(idProducerSongTextLine, listOf(ProducerType.SONGTEXTLINE, voiceId))
        mltProp.setXmlData(templateSongTextLine, listOf(ProducerType.SONGTEXTLINE, voiceId))

        mltProp.setId(idProducerHorizon, listOf(ProducerType.HORIZON, voiceId))
        mltProp.setXmlData(templateHorizon, listOf(ProducerType.HORIZON, voiceId))

        mltProp.setId(idProducerFlash, listOf(ProducerType.FLASH, voiceId))
        mltProp.setXmlData(templateFlash, listOf(ProducerType.FLASH, voiceId))
        mltProp.setRect(propFlashValue, listOf(ProducerType.FLASH, voiceId))

        mltProp.setId(idProducerWatermark, listOf(ProducerType.WATERMARK, voiceId))
        mltProp.setXmlData(templateWatermark, listOf(ProducerType.WATERMARK, voiceId))

        mltProp.setId(idProducerFaderText, listOf(ProducerType.FADERTEXT, voiceId))
        mltProp.setXmlData(templateFaderText, listOf(ProducerType.FADERTEXT, voiceId))

        mltProp.setId(idProducerFaderChords, listOf(ProducerType.FADERCHORDS, voiceId))
        mltProp.setXmlData(templateFaderChords, listOf(ProducerType.FADERCHORDS, voiceId))
        mltProp.setRect(propRectFaderChordsValue, listOf(ProducerType.FADERCHORDS, voiceId))

        mltProp.setId(idProducerBackChords, listOf(ProducerType.BACKCHORDS, voiceId))
        mltProp.setXmlData(templateBackChords, listOf(ProducerType.BACKCHORDS, voiceId))
        mltProp.setRect(propRectBackChordsValue, listOf(ProducerType.BACKCHORDS, voiceId))

        mltProp.setId(idProducerFingerboard, listOf(ProducerType.FADERTEXT, voiceId))

        for (i in 0 until countFingerboards) {
            templateFingerboards[i]?.let { mltProp.setXmlData(it, listOf(ProducerType.FINGERBOARD, voiceId, i)) }
            propRectFingerboardValues[i]?.let { mltProp.setRect(it, listOf(ProducerType.FINGERBOARD, voiceId, i)) }
        }

        mltProp.setId(idProducerProgress, listOf(ProducerType.PROGRESS, voiceId))
        mltProp.setXmlData(templateProgress, listOf(ProducerType.PROGRESS, voiceId))
        mltProp.setRect(propProgressValue, listOf(ProducerType.PROGRESS, voiceId))

        mltProp.setId(idProducerFillColorSongtextEven, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId, 0))
        mltProp.setRect(propSongtextFillEvenValue, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId, 0))
        mltProp.setId(idProducerFillColorSongtextOdd, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId, 1))
        mltProp.setRect(propSongtextFillOddValue, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId, 1))

        mltProp.setId(idProducerHeader, listOf(ProducerType.HEADER, voiceId))
        mltProp.setXmlData(templateHeader, listOf(ProducerType.HEADER, voiceId))
        mltProp.setRect(propHeaderValue, listOf(ProducerType.HEADER, voiceId))

        mltProp.setId(idProducerSplashstart, listOf(ProducerType.SPLASHSTART, voiceId))
        mltProp.setXmlData(templateSplashstart, listOf(ProducerType.SPLASHSTART, voiceId))

        mltProp.setId(idProducerBoosty, listOf(ProducerType.BOOSTY, voiceId))
        mltProp.setXmlData(templateBoosty, listOf(ProducerType.BOOSTY, voiceId))

        mltProp.setId(idProducerBackground, listOf(ProducerType.BACKGROUND, voiceId))
        mltProp.setPath(getRandomFile(Karaoke.backgroundFolderPath, ".png"), listOf(ProducerType.BACKGROUND, voiceId))


        mltProp.setId(idProducerCounter0, listOf(ProducerType.COUNTER, voiceId, 0))
        mltProp.setXmlData(templateCounter0, listOf(ProducerType.COUNTER, voiceId, 0))
        mltProp.setRect(propFillCounter0Value, listOf(ProducerType.COUNTER, voiceId, 0))
        mltProp.setId(idProducerCounter1, listOf(ProducerType.COUNTER, voiceId, 1))
        mltProp.setXmlData(templateCounter1, listOf(ProducerType.COUNTER, voiceId, 1))
        mltProp.setRect(propFillCounter1Value, listOf(ProducerType.COUNTER, voiceId, 1))
        mltProp.setId(idProducerCounter2, listOf(ProducerType.COUNTER, voiceId, 2))
        mltProp.setXmlData(templateCounter2, listOf(ProducerType.COUNTER, voiceId, 2))
        mltProp.setRect(propFillCounter2Value, listOf(ProducerType.COUNTER, voiceId, 2))
        mltProp.setId(idProducerCounter3, listOf(ProducerType.COUNTER, voiceId, 3))
        mltProp.setXmlData(templateCounter3, listOf(ProducerType.COUNTER, voiceId, 3))
        mltProp.setRect(propFillCounter3Value, listOf(ProducerType.COUNTER, voiceId, 3))
        mltProp.setId(idProducerCounter4, listOf(ProducerType.COUNTER, voiceId, 4))
        mltProp.setXmlData(templateCounter4, listOf(ProducerType.COUNTER, voiceId, 4))
        mltProp.setRect(propFillCounter4Value, listOf(ProducerType.COUNTER, voiceId, 4))

        mltProp.setEnabled(Karaoke.createAudioVocal, listOf(ProducerType.AUDIOVOCAL, voiceId))
        mltProp.setEnabled(Karaoke.createAudioMusic, listOf(ProducerType.AUDIOMUSIC, voiceId))
        mltProp.setEnabled(Karaoke.createAudioSong, listOf(ProducerType.AUDIOSONG, voiceId))
        mltProp.setEnabled(Karaoke.createAudioBass, listOf(ProducerType.AUDIOBASS, voiceId))
        mltProp.setEnabled(Karaoke.createAudioDrums, listOf(ProducerType.AUDIODRUMS, voiceId))
        mltProp.setEnabled(Karaoke.createBackground, listOf(ProducerType.BACKGROUND, voiceId))
        mltProp.setEnabled(Karaoke.createHorizon, listOf(ProducerType.HORIZON, voiceId))
        mltProp.setEnabled(Karaoke.createProgress, listOf(ProducerType.PROGRESS, voiceId))
        mltProp.setEnabled(Karaoke.createFillsSongtext, listOf(ProducerType.FILLCOLORSONGTEXT, voiceId))
        mltProp.setEnabled(Karaoke.createSongtext, listOf(ProducerType.SONGTEXT, voiceId))
        mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERTEXT, voiceId))
        mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERCHORDS, voiceId))
        mltProp.setEnabled(Karaoke.createHeader, listOf(ProducerType.HEADER, voiceId))
        mltProp.setEnabled(Karaoke.createCounters, listOf(ProducerType.COUNTER, voiceId))
        mltProp.setEnabled(Karaoke.createWatermark, listOf(ProducerType.WATERMARK, voiceId))

    }

    when(song.songVersion) {
        SongVersion.LYRICS -> {
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOVOCAL)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOMUSIC)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIOSONG)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOBASS)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIODRUMS)
        }
        SongVersion.KARAOKE -> {
            mltProp.setVolume(propAudioVolumeCustomValue, ProducerType.AUDIOVOCAL)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIOMUSIC)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOSONG)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOBASS)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIODRUMS)
        }
        SongVersion.CHORDS -> {
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOVOCAL)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOMUSIC)
            mltProp.setVolume(propAudioVolumeOffValue, ProducerType.AUDIOSONG)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIOBASS)
            mltProp.setVolume(propAudioVolumeOnValue, ProducerType.AUDIODRUMS)
        }
    }


    mltProp.setFileName(song.getOutputFilename(SongOutputFile.RUNALL).replace("&", "&amp;"), SongOutputFile.RUNALL)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.RUN,).replace("&", "&amp;"), SongOutputFile.RUN)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.MLT).replace("&", "&amp;"), SongOutputFile.MLT)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.PROJECT).replace("&", "&amp;"), SongOutputFile.PROJECT)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.VIDEO).replace("&", "&amp;"), SongOutputFile.VIDEO)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.PICTURE).replace("&", "&amp;"), SongOutputFile.PICTURE)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.PICTURECHORDS).replace("&", "&amp;"), SongOutputFile.PICTURECHORDS)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.SUBTITLE).replace("&", "&amp;"), SongOutputFile.SUBTITLE)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.DESCRIPTION).replace("&", "&amp;"), SongOutputFile.DESCRIPTION)
    mltProp.setFileName(song.getOutputFilename(SongOutputFile.TEXT).replace("&", "&amp;"), SongOutputFile.TEXT)

    val permissions = PosixFilePermissions.fromString("rwxr-x---")

    val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(mltProp)}"
    val fileProject = File(song.getOutputFilename(SongOutputFile.PROJECT))
    val fileMlt = File(song.getOutputFilename(SongOutputFile.MLT))
    Files.createDirectories(Path(fileProject.parent))
    fileProject.writeText(templateProject)
    fileMlt.writeText(templateProject)
    val fileRun = File(song.getOutputFilename(SongOutputFile.RUN))
    fileRun.writeText("echo \"${song.getOutputFilename(SongOutputFile.MLT)}\"\n" +
            "melt -progress \"${song.getOutputFilename(SongOutputFile.MLT).toString()}\"\n")
    Files.setPosixFilePermissions(fileRun.toPath(), permissions)

    val fileDescription = File(song.getOutputFilename(SongOutputFile.DESCRIPTION))
    Files.createDirectories(Path(fileDescription.parent))
    fileDescription.writeText(song.getDescription())

    if (song.songVersion == SongVersion.LYRICS) createBoostyTeaserPicture(song, song.getOutputFilename(SongOutputFile.PICTUREBOOSTY))
    if (song.songVersion == SongVersion.LYRICS) createVKPicture(song, song.getOutputFilename(SongOutputFile.PICTUREVK))

    val fileText = File(song.getOutputFilename(SongOutputFile.TEXT))
    Files.createDirectories(Path(fileText.parent))
    fileText.writeText(song.getText())

    val filePictures = File(song.getOutputFilename(SongOutputFile.DESCRIPTION))
    Files.createDirectories(Path(filePictures.parent))
    createSongPicture(song, song.getOutputFilename(SongOutputFile.PICTURE), song.songVersion)

    val filePictureChords = File(song.getOutputFilename(SongOutputFile.PICTURECHORDS))
    Files.createDirectories(Path(filePictureChords.parent))
    createSongChordsPicture(song, song.getOutputFilename(SongOutputFile.PICTURECHORDS), song.songVersion, mltProp.getXmlData(listOf(ProducerType.SONGTEXT, 0, "IgnoreCapo")))

}

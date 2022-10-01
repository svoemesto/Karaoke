//import model.Lyric
import mlt.getMlt
import model.SongVoiceLine
import model.ProducerType
import model.Song
import model.SongVoiceLineSymbol
import model.TransformProperty
import java.awt.Font
import java.io.File
import java.lang.Integer.min

fun createKaraoke(song: Song) {

    val param = mutableMapOf<String, Any?>()
    param["COUNT_VOICES"] = song.voices.size
    param["LINE_SPACING"] = LINE_SPACING
    param["SHADOW"] = SHADOW
    param["TYPEWRITER"] = TYPEWRITER
    param["ALIGNMENT"] = ALIGNMENT

    val maxTextWidthPx = Karaoke.frameWidthPx.toDouble() - Karaoke.songtextStartPositionXpx * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа

    // Ширина в пикселах суммарной самой длинной строки
    var maxTextWidthPxByFontSize = song.voices.sumOf { it.maxWidthLinePx } + Karaoke.songtextStartPositionXpx*(song.voices.size-1)

    // Максимальный размер шрифта берем из дефолтного значения
    var fontSizePt = Karaoke.voices[0].groups[0].songtextTextMltFont.font.size
    val step = if (maxTextWidthPxByFontSize > maxTextWidthPx) -1 else 1
    while (true) {
        if ((maxTextWidthPxByFontSize > maxTextWidthPx && step < 0) || (maxTextWidthPxByFontSize < maxTextWidthPx && step > 0)) {
            fontSizePt += step
            maxTextWidthPxByFontSize = (song.voices.sumOf{getTextWidthHeightPx(it.maxWidthLineText, Karaoke.voices[0].groups[0].songtextTextMltFont.font.name, Karaoke.voices[0].groups[0].songtextTextMltFont.font.style, fontSizePt).first} + Karaoke.songtextStartPositionXpx*(song.voices.size-1).toLong()).toLong()
        } else {
            break
        }
    }

    val font = Font(Karaoke.voices[0].groups[0].songtextTextMltFont.font.name, Karaoke.voices[0].groups[0].songtextTextMltFont.font.style, fontSizePt)

    val symbolHeightPx = getTextWidthHeightPx("W", font).second
    val symbolWidthPx = getTextWidthHeightPx("0", font).first

    val quarterNoteLengthMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms // Находим длительность звучания 1/4 ноты в миллисекундах
    val halfNoteLengthMs = quarterNoteLengthMs * 2

    var currentVoiceOffset = 0L

    for (voiceId in 0 until song.voices.size) {

        val songVoice = song.voices[voiceId]

        if (voiceId > 0) {
            currentVoiceOffset += (getTextWidthHeightPx(song.voices[voiceId-1].maxWidthLineText, Karaoke.voices[0].groups[0].songtextTextMltFont.font.name, Karaoke.voices[0].groups[0].songtextTextMltFont.font.style, fontSizePt).first + Karaoke.songtextStartPositionXpx).toLong()
        }

        val counters = listOf(
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList()
        )

        counters[0].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[1].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[2].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[3].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
        counters[4].add("00:00:00.000=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")

        val idProducerSongText = 2
        val idProducerHorizon = 3
        val idProducerFillColorEven = 4
        val idProducerFillColorOdd = 5
        val idProducerLogotype = 6
        val idProducerHeader = 7
        val idProducerBackground = 8
        val idProducerMicrophone = 9
        val idProducerCounter4 = 10
        val idProducerCounter3 = 11
        val idProducerCounter2 = 12
        val idProducerCounter1 = 13
        val idProducerCounter0 = 14
        val idProducerAudioSong = 15
        val idProducerAudioMusic = 16
        val idProducerAudioVocal = 17
        val idProducerBeat1 = 18
        val idProducerBeat2 = 19
        val idProducerBeat3 = 20
        val idProducerBeat4 = 21
        val idProducerProgress = 22
        val idProducerWatermark = 23

        val beats = listOf(
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList()
        )

        val propRectLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propProgressLineValue = mutableListOf<String>() // Список свойств трансформации текста
        val propGuidesValue = mutableListOf<String>()
        val guidesTypes = listOf<Long>(8, 7, 4, 3)

        val propRectTitleValueLineOddEven = listOf(
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList()
        )

        val voiceSetting = Karaoke.voices[min(voiceId, Karaoke.voices.size-1)]
        voiceSetting.replaceFontSize(fontSizePt)

        var currentGroup = 0 // Получаем номер текущей группы
        var groupSetting = voiceSetting.groups[min(currentGroup, voiceSetting.groups.size-1)] // Получаем настройки для текущей группы
        songVoice.lines.forEach { voiceLine ->
            val fontText = Font(groupSetting.songtextTextMltFont.font.name, groupSetting.songtextTextMltFont.font.style, fontSizePt)
            val fontBeat = Font(groupSetting.songtextBeatMltFont.font.name, groupSetting.songtextBeatMltFont.font.style, fontSizePt)
            voiceLine.symbols.forEach {lineSymbol ->
                lineSymbol.font = if (lineSymbol.isBeat) fontBeat else fontText
            }
            val lineText = voiceLine.subtitles.map { it.text }.toList().joinToString("")
            currentGroup = voiceLine.subtitles.firstOrNull()?.group ?: 0 // Получаем номер текущей группы
            groupSetting = voiceSetting.groups[min(currentGroup, voiceSetting.groups.size-1)] // Получаем настройки для текущей группы
            var currentGroupText = ""

            voiceLine.fontText = fontText
            voiceLine.fontBeat = fontBeat

            for (subtitle in voiceLine.subtitles) {
                currentGroup = subtitle.group // Получаем номер текущей группы
                groupSetting = voiceSetting.groups[min(currentGroup, voiceSetting.groups.size-1)] // Получаем настройки для текущей группы
                val fontTextForSubtitle = Font(groupSetting.songtextTextMltFont.font.name, groupSetting.songtextTextMltFont.font.style, fontSizePt)
                val fontBeatForSubtitle = Font(groupSetting.songtextBeatMltFont.font.name, groupSetting.songtextBeatMltFont.font.style, fontSizePt)
                if (subtitle.isBeat) {
                    var textBeforeVowel = ""
                    var textVowel = ""
                    var textAfterVowel = ""
                    var isFoundVowel = false
                    for (i in subtitle.text.indices) {
                        val symbol = subtitle.text[i]
                        if (symbol in LETTERS_VOWEL) {
                            isFoundVowel = true
                            if (i>0) textBeforeVowel = subtitle.text.substring(0 until i)
                            textVowel = symbol.toString()
                            if (i<subtitle.text.length-1) textAfterVowel = subtitle.text.substring(i+1)
                            break
                        }
                    }
                    if (!isFoundVowel) {
                        currentGroupText += subtitle.text
                    } else {
                        currentGroupText += textBeforeVowel
                        if (currentGroupText != "") {
                            voiceLine.symbols.add(
                                SongVoiceLineSymbol(
                                    text = currentGroupText,
                                    font = fontTextForSubtitle,
                                    group = currentGroup,
                                    isBeat = false
                                )
                            )
                            currentGroupText = ""
                        }

                        currentGroupText += textVowel
                        if (currentGroupText != "") {
                            voiceLine.symbols.add(
                                SongVoiceLineSymbol(
                                    text = currentGroupText,
                                    font = fontBeatForSubtitle,
                                    group = currentGroup,
                                    isBeat = true
                                )
                            )
                            currentGroupText = ""
                        }
                        currentGroupText += textAfterVowel
                    }
                } else {
                    currentGroupText += subtitle.text
                }

            }

            if (currentGroupText != "") {
                voiceLine.symbols.add(
                    SongVoiceLineSymbol(
                        text = currentGroupText,
                        font = fontText,
                        group = currentGroup,
                        isBeat = false
                    )
                )
            }
        }

        var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль

        val voiceLines = mutableListOf<SongVoiceLine>()
        songVoice.lines.forEach { voiceLine ->
            val silentDuration = convertTimecodeToMilliseconds(voiceLine.start) - currentPositionEnd
            val linesToInsert: Long = silentDuration / songVoice.maxDurationMs
            if (linesToInsert > 0) {
                val silentLineDuration: Long = silentDuration / linesToInsert
                for (i in 1..linesToInsert) {
                    val startDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration / 5)
                    val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration)
                    voiceLines.add(
                        SongVoiceLine(
                            subtitles = mutableListOf(),
                            symbols = mutableListOf(),
                            text = "",
                            start = startDuration,
                            end = endDuration,
                            startTp = null,
                            endTp = null,
                            isEmptyLine = true,
                            isNeedCounter = false,
                            isFadeLine = false,
                            isMaxLine = false,
                            durationMs = silentLineDuration,
                            fontText = voiceLine.fontText,
                            fontBeat = voiceLine.fontBeat
                        )
                    )
                    currentPositionEnd = convertTimecodeToMilliseconds(endDuration)
                }
            }
            voiceLine.isNeedCounter = ((linesToInsert > 0 && !voiceLine.isEmptyLine) || voiceLine == songVoice.lines.first())
            voiceLines.add(voiceLine)
            currentPositionEnd = convertTimecodeToMilliseconds(voiceLine.end)
        }

        val workAreaHeightPx = symbolHeightPx * voiceLines.size // Высота рабочей области
        val horizonPositionPx = (Karaoke.frameHeightPx / 2 + symbolHeightPx.toLong() / 2) - Karaoke.horizonOffsetPx    // horizonPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет
        val songLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val progressSymbolHalfWidth = (getTextWidthHeightPx(Karaoke.progressSymbol, Karaoke.progressFont.font).first/2).toLong()
        val kdeHeaderAuthor = song.settings.author
        val kdeHeaderTone = song.settings.key
        val kdeHeaderBpm = song.settings.bpm
        val kdeHeaderAlbum = song.settings.album
        val kdeHeaderSongName = song.settings.songName
        val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / song.settings.songName.length), 80)
        val yOffset = -5

        propProgressLineValue.add("00:00:00.000=-${progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
        propProgressLineValue.add("${song.endTimecode}=${Karaoke.frameWidthPx - progressSymbolHalfWidth} $yOffset ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")

        param["VOICE${voiceId}_SETTING"] = voiceSetting
        param["VOICE${voiceId}_OFFSET"] = currentVoiceOffset
        param["VOICE${voiceId}_WORK_AREA_HEIGHT_PX"] = workAreaHeightPx.toLong()
        param["VOICE${voiceId}_VOICELINES"] = voiceLines

        param["SYMBOL_HEIGHT_PX"] = symbolHeightPx
        param["HORIZON_POSITION_PX"] = horizonPositionPx
        param["COUNTER_POSITION_Y_PX"] = (horizonPositionPx - symbolHeightPx).toLong()
        param["VOICE${voiceId}_COUNTER_POSITION_X_PX"] = currentVoiceOffset + Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx - symbolWidthPx
        param["SONG_LENGTH_MS"] = songLengthMs
        param["FONT_SIZE_PT"] = fontSizePt

        param["HEADER_AUTHOR"] = kdeHeaderAuthor
        param["HEADER_TONE"] = kdeHeaderTone
        param["HEADER_BPM"] = kdeHeaderBpm
        param["HEADER_ALBUM"] = kdeHeaderAlbum
        param["HEADER_SONG_NAME"] = kdeHeaderSongName
        param["HEADER_SONG_NAME_FONT_SIZE"] = fontNameSizePt

        val templateSongText = getTemplateSongText(param,voiceId)
        val templateHorizon = getTemplateHorizon(param)
        val templateProgress = getTemplateProgress(param)
        val templateWatermark = getTemplateWatermark(param)
        val templateHeader = getTemplateHeader(param)
        val templateCounter0 = getTemplateCounter(param,0, voiceId)
        val templateCounter1 = getTemplateCounter(param,1, voiceId)
        val templateCounter2 = getTemplateCounter(param,2, voiceId)
        val templateCounter3 = getTemplateCounter(param,3, voiceId)
        val templateCounter4 = getTemplateCounter(param,4, voiceId)
        val templateBeat1 = getTemplateBeat1(param)
        val templateBeat2 = getTemplateBeat2(param)
        val templateBeat3 = getTemplateBeat3(param)
        val templateBeat4 = getTemplateBeat4(param)

        voiceLines.forEachIndexed { indexLine, voiceLine ->

            val startTp = TransformProperty(
                time = voiceLine.start,
                x = currentVoiceOffset,
                y = horizonPositionPx - ((indexLine + 1) * symbolHeightPx).toLong(),
                w = Karaoke.frameWidthPx,
                h = workAreaHeightPx.toLong(),
                opacity = if (indexLine in 1 until voiceLines.size-1) 1.0 else 0.0
            )

            var time = voiceLine.end
            // Если текущий элемент не последний - надо проверить начало следующего элемента
            if (indexLine != voiceLines.size - 1) {
                val nextVoiceLine = voiceLines[indexLine + 1] // Находим следующую строку
                val diffInMills = getDiffInMilliseconds(nextVoiceLine.start, voiceLine.end) // Находим разницу во времени между текущей строкой и следующей
                if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {                            // Если эта разница меньше 200 мс
                    voiceLine.end = nextVoiceLine.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                    if (voiceLine.subtitles.isNotEmpty()) {
                        voiceLine.subtitles.last().endTimecode = voiceLine.end
                        time = voiceLine.subtitles.last().startTimecode       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
                    }
                }
            }

            val endTp = TransformProperty(
                time = time,
                x = currentVoiceOffset,
                y = horizonPositionPx - ((indexLine + 1) * symbolHeightPx).toLong(),
                w = Karaoke.frameWidthPx,
                h = workAreaHeightPx.toLong(),
                opacity = if (indexLine in 1 until voiceLines.size-1) 1.0 else 0.0
            )
            voiceLine.startTp = startTp
            voiceLine.endTp = endTp

        }

//        if (!resultSongVoiceLinesFullText[0].isEmptyLine) {
//            val currentLyricLine = resultSongVoiceLinesFullText[0]
//            propRectLineValue.add("00:00:00.000=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} 0.0")
//            propRectLineValue.add("00:00:01.000=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} 1.0")
//        }

        // Настало время прописать classes.TransformProperty для заливок

        voiceLines.forEachIndexed { indexLine, voiceLine ->
//            voiceLine._widthLinePx = null
            val nextVoiceLine = if (indexLine < voiceLines.size - 1) voiceLines[indexLine + 1] else null //Следующая строка
            propRectLineValue.add("${voiceLine.startTp?.time}=${voiceLine.startTp?.x} ${voiceLine.startTp?.y} ${voiceLine.startTp?.w} ${voiceLine.startTp?.h} ${voiceLine.startTp?.opacity}")

            if (!voiceLine.isEmptyLine) { // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
                propRectLineValue.add("${voiceLine.endTp?.time}=${voiceLine.endTp?.x} ${voiceLine.endTp?.y} ${voiceLine.endTp?.w} ${voiceLine.endTp?.h} ${voiceLine.endTp?.opacity}")
                val currSubtitle = voiceLine.subtitles[0] // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
                val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSubtitle.startTimecode) - (1000 / Karaoke.frameFps + 1).toLong()) // Время начала анимации = времени начала этого титра минус 1 фрейм
                val x = Karaoke.songtextStartPositionXpx - Karaoke.songtextStartOffsetXpx + currentVoiceOffset
                var y: Double = horizonPositionPx - symbolHeightPx // Координата y = позиция горизонта - высота символа
                val h = symbolHeightPx // Высота = высоте символа
                val propRectTitleValueFade = "$startTime=$x ${y.toLong()} 1 ${h.toLong()} 0.0" // Свойство трансформации заливки с полной прозрачностью
                propRectTitleValueLineOddEven[indexLine % 2].add(propRectTitleValueFade)

                val startW = Karaoke.songtextStartOffsetXpx //.toDouble() // Смещаем стартовую позицию w на величину TITLE_OFFSET_START_X_PX
                val timeFirstIn = currSubtitle.startTimecode // Время - начало текущего титра
                val wFirstIn = startW + voiceLine.getSubtitleXpx(currSubtitle).toLong()
                val propRectTitleValueFirstIn = "$timeFirstIn=$x ${y.toLong()} $wFirstIn ${h.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                propRectTitleValueLineOddEven[indexLine % 2].add(propRectTitleValueFirstIn)

                for (indexSub in 0..(voiceLine.subtitles.size) - 2) { // Проходимся по титрам текущей линии от первого до предпоследнего
                    val currSub = voiceLine.subtitles[indexSub] // Текущий титр
                    val nextSub = voiceLine.subtitles[indexSub + 1] // Следующий титр

                    val timeOut = nextSub.startTimecode // Время - начало следующего титра
                    val wOut = startW + voiceLine.getSubtitleXpx(nextSub).toLong()
                    val propRectTitleValueOut = "$timeOut=$x ${y.toLong()} $wOut ${h.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                    propRectTitleValueLineOddEven[indexLine % 2].add(propRectTitleValueOut)
                }

                // На этом этапе мы закрасили все титры линии, кроме последнего

                val currSub = voiceLine.subtitles[(voiceLine.subtitles.size) - 1]   // Текущий титр - последний титр текущей строки
                val nextSub = if (nextVoiceLine?.subtitles != null && nextVoiceLine.subtitles.isNotEmpty()) nextVoiceLine.subtitles[0] else null // Следующий титр - первый титр следующей строки (может не быть)
                val diffInMills = if (nextVoiceLine != null) getDiffInMilliseconds(nextVoiceLine.start, voiceLine.end) else Karaoke.transferMinimumMsBetweenLinesToScroll

                y -= if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) symbolHeightPx else 0.0
                val timeOut = currSub.endTimecode // Время - конец текущего титра
                val wOut = startW + voiceLine.widthLinePx + Karaoke.songtextStartOffsetXpx
                val propRectTitleValueOut = "$timeOut=$x ${y.toLong()} $wOut ${h.toLong()} ${if (indexLine % 2 == 0) Karaoke.voices[voiceId].fill.oddOpacity else Karaoke.voices[voiceId].fill.evenOpacity}"
                propRectTitleValueLineOddEven[indexLine % 2].add(propRectTitleValueOut)

                y -= if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) 0.0 else symbolHeightPx

                val timeFadeOut = if (diffInMills < Karaoke.transferMinimumMsBetweenLinesToScroll) {
                    nextSub?.endTimecode  ?: nextVoiceLine?.start ?: convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSub.endTimecode) + Karaoke.transferMinimumMsBetweenLinesToScroll)
                } else {
                    nextSub?.startTimecode  ?: nextVoiceLine?.start ?: convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currSub.endTimecode) + Karaoke.transferMinimumMsBetweenLinesToScroll)
                }
                val propRectTitleValueFadeOut = "$timeFadeOut=$x ${y.toLong()} $wOut ${h.toLong()} 0.0"
                propRectTitleValueLineOddEven[indexLine % 2].add(propRectTitleValueFadeOut)
            }
        }

        // Настало время заняться счётчиками вступления.

        voiceLines.filter { it.isNeedCounter }
            .forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
                for (counterNumber in 0..4) {
                    val startTimeMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * counterNumber
                    val initTimeMs = startTimeMs - (1000 / Karaoke.frameFps + 1).toLong()
                    val endTimeMs = startTimeMs + halfNoteLengthMs
                    if (startTimeMs > 0) {
                        counters[counterNumber].add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(endTimeMs)}=0 ${-symbolHeightPx} ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0")
                    }
                }
            }


        // Такты
        var delayMs = convertTimecodeToMilliseconds(song.beatTimecode) // + TIME_OFFSET_MS

        // Такт в мс
        val beatMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms// song.delay
        val different = ((delayMs / (beatMs * 4)) - 1) * (beatMs * 4)
        delayMs -= different

        var currentPositionStartMs = 0L
        var beatCounter = 1L
        propGuidesValue.add("""{"comment": "Offset", "pos": ${convertMillisecondsToFrames(Karaoke.timeOffsetMs)}, "type": 0}""")
        while ((delayMs + currentPositionStartMs + beatMs) < convertTimecodeToMilliseconds(song.endTimecode)) {

            val tick = (beatCounter - 1) % 4
            currentPositionStartMs = (delayMs + beatMs * (beatCounter - 1))// + TIME_OFFSET_MS
            val currentPositionStartFrame = convertMillisecondsToFrames(currentPositionStartMs)

            val currentPositionStartMs2fb =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs) - 2, Karaoke.frameFps)
            val currentPositionStartMs1fb =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs) - 1, Karaoke.frameFps)
            val currentPositionEndMs = convertFramesToMilliseconds(
                convertMillisecondsToFrames(currentPositionStartMs + (beatMs * (4 - tick))) - 3,
                Karaoke.frameFps
            )
            val currentPositionEndMs1fa =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs) + 1, Karaoke.frameFps)
            val currentPositionEndMs2fa =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs) + 2, Karaoke.frameFps)

            val point0 = "${convertMillisecondsToTimecode(currentPositionStartMs2fb)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
            val point1 = "${convertMillisecondsToTimecode(currentPositionStartMs1fb)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
            val point2 = "${convertMillisecondsToTimecode(currentPositionStartMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 1.0"
            val point3 = "${convertMillisecondsToTimecode(currentPositionEndMs)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
            val point4 = "${convertMillisecondsToTimecode(currentPositionEndMs1fa)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"
            val point5 = "${convertMillisecondsToTimecode(currentPositionEndMs2fa)}=0 0 ${Karaoke.frameWidthPx} ${Karaoke.frameHeightPx} 0.0"

            beats[tick.toInt()].add(point0)
            beats[tick.toInt()].add(point1)
            beats[tick.toInt()].add(point2)
            beats[tick.toInt()].add(point3)
            beats[tick.toInt()].add(point4)
            beats[tick.toInt()].add(point5)

            if (tick == 0L) {
                propGuidesValue.add("""{"comment": "|", "pos": $currentPositionStartFrame, "type": ${guidesTypes[tick.toInt()]}}""")
            }

            beatCounter += 1
        }

        // Формируем тексты для файлов и сохраняем файлы

        val propRectValue = propRectLineValue.joinToString(";")
        val propProgressValue = propProgressLineValue.joinToString(";")
        val propFillOddValue = propRectTitleValueLineOddEven[0].joinToString(";")
        val propFillEvenValue = propRectTitleValueLineOddEven[1].joinToString(";")
        val propFillCounter0Value = counters[0].joinToString(";")
        val propFillCounter1Value = counters[1].joinToString(";")
        val propFillCounter2Value = counters[2].joinToString(";")
        val propFillCounter3Value = counters[3].joinToString(";")
        val propFillCounter4Value = counters[4].joinToString(";")
        val propBeat1Value = beats[0].joinToString(";")
        val propBeat2Value = beats[1].joinToString(";")
        val propBeat3Value = beats[2].joinToString(";")
        val propBeat4Value = beats[3].joinToString(";")
        val propGuides = propGuidesValue.joinToString(",")

        val kdeIn = "00:00:00.000"
        val kdeInOffset = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + Karaoke.timeOffsetMs)
        val kdeFadeIn = "00:00:01.000"
        val kdeOut = song.endTimecode.replace(",", ".")
        val kdeFadeOut = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.endTimecode) - 1000).replace(",", ".")
        val kdeLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val kdeLengthFrames = convertTimecodeToFrames(song.endTimecode, Karaoke.frameFps)

        kdeLogoPath = "${song.settings.rootFolder}/Logo.png"
        kdeMicrophonePath = "${song.settings.rootFolder}/Microphone.png"

        param["SONG_ROOT_FOLDER"] = song.settings.rootFolder
        param["SONG_START_TIMECODE"] = kdeIn
        param["SONG_END_TIMECODE"] = kdeOut
        param["SONG_FADEIN_TIMECODE"] = kdeFadeIn
        param["SONG_FADEOUT_TIMECODE"] = kdeFadeOut
        param["SONG_LENGTH_MS"] = kdeLengthMs
        param["SONG_LENGTH_FR"] = kdeLengthFrames
        param["GUIDES_PROPERTY"] = "[${propGuides}]"
        param["IN_OFFSET"] = kdeInOffset


        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_ID"] = idProducerAudioSong
        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_PATH"] = song.settings.audioSongFileName

        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_ID"] = idProducerAudioMusic
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_PATH"] = song.settings.audioMusicFileName

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_ID"] = idProducerAudioVocal
        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_PATH"] = song.settings.audioVocalFileName
        param["HIDE_TRACTOR_${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}"] = "both"

        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_ID"] = idProducerSongText
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_WORK_AREA_HEIGHT_PX"] = workAreaHeightPx.toLong()
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_XML_DATA"] = templateSongText
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propRectValue
        param["HIDE_TRACTOR_${ProducerType.SONGTEXT.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_ID"] = idProducerHorizon
        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_XML_DATA"] = templateHorizon
        param["HIDE_TRACTOR_${ProducerType.HORIZON.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_ID"] = idProducerWatermark
        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_XML_DATA"] = templateWatermark
        param["HIDE_TRACTOR_${ProducerType.WATERMARK.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_ID"] = idProducerProgress
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_XML_DATA"] = templateProgress
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_PROPERTY_RECT"] = propProgressValue
        param["HIDE_TRACTOR_${ProducerType.PROGRESS.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_EVEN_ID"] = idProducerFillColorEven
        param["${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_EVEN_PROPERTY_RECT"] = propFillEvenValue
        param["HIDE_TRACTOR_${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_EVEN"] = "audio"

        param["${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_ODD_ID"] = idProducerFillColorOdd
        param["${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_ODD_PROPERTY_RECT"] = propFillOddValue
        param["HIDE_TRACTOR_${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_ODD"] = "audio"

        param["${ProducerType.LOGOTYPE.text.uppercase()}${voiceId}_ID"] = idProducerLogotype
        param["${ProducerType.LOGOTYPE.text.uppercase()}${voiceId}_PATH"] = kdeLogoPath
        param["HIDE_TRACTOR_${ProducerType.LOGOTYPE.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_ID"] = idProducerHeader
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_XML_DATA"] = templateHeader
        param["HIDE_TRACTOR_${ProducerType.HEADER.text.uppercase()}${voiceId}"] = "audio"


        param["${ProducerType.MICROPHONE.text.uppercase()}${voiceId}_ID"] = idProducerMicrophone
        param["${ProducerType.MICROPHONE.text.uppercase()}${voiceId}_PATH"] = kdeMicrophonePath


        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_ID"] = idProducerBackground
        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_PATH"] = getRandomFile(Karaoke.backgroundFolderPath, ".png")
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


        param["${ProducerType.BEAT.text.uppercase()}${voiceId}1_ID"] = idProducerBeat1
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}1_XML_DATA"] = templateBeat1
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}1_PROPERTY_RECT"] = propBeat1Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}1"] = "audio"

        param["${ProducerType.BEAT.text.uppercase()}${voiceId}2_ID"] = idProducerBeat2
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}2_XML_DATA"] = templateBeat2
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}2_PROPERTY_RECT"] = propBeat2Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}2"] = "audio"

        param["${ProducerType.BEAT.text.uppercase()}${voiceId}3_ID"] = idProducerBeat3
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}3_XML_DATA"] = templateBeat3
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}3_PROPERTY_RECT"] = propBeat3Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}3"] = "audio"

        param["${ProducerType.BEAT.text.uppercase()}${voiceId}4_ID"] = idProducerBeat4
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}4_XML_DATA"] = templateBeat4
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}4_PROPERTY_RECT"] = propBeat4Value
        param["HIDE_TRACTOR_${ProducerType.BEAT.text.uppercase()}${voiceId}4"] = "audio"

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioVocal
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioMusic
        param["${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createAudioSong
        param["${ProducerType.BACKGROUND.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createBackground
        param["${ProducerType.MICROPHONE.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createMicrophone
        param["${ProducerType.HORIZON.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createHorizon
        param["${ProducerType.PROGRESS.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createProgress
        param["${ProducerType.FILLCOLOR.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createFills
        param["${ProducerType.SONGTEXT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createSongtext
        param["${ProducerType.HEADER.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createHeader
        param["${ProducerType.LOGOTYPE.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createLogotype
        param["${ProducerType.BEAT.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createBeats
        param["${ProducerType.COUNTER.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createCounters
        param["${ProducerType.WATERMARK.text.uppercase()}${voiceId}_ENABLED"] = Karaoke.createWatermark

    }


    val fileIsKaraoke = listOf(false, true)

//    param.forEach{println("${it.key} : ${it.value}")}

    fileIsKaraoke.forEach { isKaraoke ->
        param["SONG_PROJECT_FILENAME"] = if (isKaraoke) song.settings.projectKaraokeFileName else song.settings.projectLyricsFileName
        param["SONG_VIDEO_FILENAME"] = if (isKaraoke) song.settings.videoKaraokeFileName else song.settings.videoLyricsFileName
        for (voiceId in 0 until song.voices.size) {
            param["HIDE_TRACTOR_${ProducerType.AUDIOMUSIC.text.uppercase()}${voiceId}"] = if (isKaraoke) "video" else "both"
            param["HIDE_TRACTOR_${ProducerType.AUDIOSONG.text.uppercase()}${voiceId}"] = if (isKaraoke) "both" else "video"
            param["HIDE_TRACTOR_${ProducerType.MICROPHONE.text.uppercase()}${voiceId}"] = if (isKaraoke) "audio" else "both"
        }

        val fileProjectName = "${song.settings.rootFolder}/${if (isKaraoke) song.settings.projectKaraokeFileName else song.settings.projectLyricsFileName}"
        val fileSubtitleName = "$fileProjectName.srt"

        val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(param)}"

        File(fileProjectName).writeText(templateProject)
        File(fileSubtitleName).writeText(song.voices[0].srtFileBody)
    }

}

//import model.Lyric
import mlt.getMlt
import model.LyricLine
import model.Producer
import model.ProducerType
import model.Settings
import model.Song
import model.Subtitle
import model.TransformProperty
import java.io.File
import kotlin.io.path.Path

fun createKaraoke(song: Song) {

    val param = mutableMapOf<String, Any?>()
    param["COUNT_GROUPS"] = song.subtitles.size
    param["MAX_GROUPS"] = MAX_GROUPS
    param["FRAME_WIDTH_PX"] = FRAME_WIDTH_PX
    param["FRAME_HEIGHT_PX"] = FRAME_HEIGHT_PX
    param["TITLE_POSITION_START_X_PX"] = TITLE_POSITION_START_X_PX
    param["TITLE_POSITION_START_Y_PX"] = TITLE_POSITION_START_Y_PX
    param["LINE_SPACING"] = LINE_SPACING
    param["SHADOW"] = SHADOW
    param["FONT_UNDERLINE"] = FONT_UNDERLINE
    param["FONT_NAME"] = FONT_NAME
    param["FONT_ITALIC"] = FONT_ITALIC
    param["TYPEWRITER"] = TYPEWRITER
    param["ALIGNMENT"] = ALIGNMENT
    param["FONT_WEIGHT"] = FONT_WEIGHT
    param["GROUPS_FONT_COLORS_TEXT"] = GROUPS_FONT_COLORS_TEXT
    param["GROUPS_FONT_COLORS_BEAT"] = GROUPS_FONT_COLORS_BEAT
    param["GROUPS_TIMELINE_COLORS"] = GROUPS_TIMELINE_COLORS


    for (groupId in 0 until song.subtitles.size) {

        var startLine = ""
        var endLine: String

        var subs: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()
        val lyricLinesFullText: MutableList<LyricLine> = mutableListOf()
        val lyricLinesFullTextGroups: MutableMap<Long, MutableList<LyricLine>> = mutableMapOf()
        val lyricLinesBeatTextGroups: MutableMap<Long, MutableList<LyricLine>> = mutableMapOf()

        val resultLyricLinesFullText: MutableList<LyricLine> = mutableListOf()
        val resultLyricLinesFullTextGroups: MutableMap<Long, MutableList<LyricLine>> = mutableMapOf()
        val resultLyricLinesBeatTextGroups: MutableMap<Long, MutableList<LyricLine>> = mutableMapOf()

        val counters = listOf(
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList(),
            emptyList<String>().toMutableList()
        )

        counters[0].add("00:00:00.000=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
        counters[1].add("00:00:00.000=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
        counters[2].add("00:00:00.000=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
        counters[3].add("00:00:00.000=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
        counters[4].add("00:00:00.000=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")

        val idProducerSongText = 2
        val idProducerHorizon = 3
        val idProducerFillColor = 4
        val idProducerLogotype = 5
        val idProducerHeader = 6
        val idProducerBackground = 7
        val idProducerMicrophone = 8
        val idProducerCounter4 = 9
        val idProducerCounter3 = 10
        val idProducerCounter2 = 11
        val idProducerCounter1 = 12
        val idProducerCounter0 = 13
        val idProducerAudioSong = 14
        val idProducerAudioMusic = 15
        val idProducerAudioVocal = 16
        val idProducerBeat1 = 17
        val idProducerBeat2 = 18
        val idProducerBeat3 = 19
        val idProducerBeat4 = 20
        val idProducerProgress = 21
        val idProducerWatermark = 22

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

        var text = ""
        var lineText = ""
        var lineFullText = ""
        var lineBeatText = ""
        var maxLineDuration = 0L
        var group = 0L

        // Проходимся по всем сабам
        song.subtitles[groupId.toLong()]!!.forEach { subtitle ->
            // Если саб - начало строки - начинаем новую строку (пока она пустая) и инициализируем пустой список subs
            if (subtitle.isLineStart) {
                startLine = subtitle.startTimecode
                lineText = ""
                lineFullText = ""
                lineBeatText = ""
                subs = mutableListOf()
                group = subtitle.group
            }

            lineText += subtitle.text
            lineFullText += if (!subtitle.isBeat) subtitle.text else replaceVowelOrConsonantLetters(
                subtitle.text,
                true
            )    // Дописываем в текст текущей строки текст из саба
            lineBeatText += if (subtitle.isBeat) replaceVowelOrConsonantLetters(subtitle.text, false) else " ".repeat(
                subtitle.text.length
            )
            subs.add(subtitle)      // Добавляем текущий саб к списку subs

            // Если саб - конец строки
            if (subtitle.isLineEnd) {

                endLine = subtitle.endTimecode  // Устанавливаем конец строки позицией конца из саба

                lyricLinesFullText.add(
                    LyricLine(
                        text = lineText,
                        start = startLine,
                        end = endLine,
                        subtitles = subs,
                        isEmptyLine = lineFullText.isEmpty()
                    )
                )

                for (indexGroup in 0L until MAX_GROUPS) {
                    val lyricLineFull = if (indexGroup == group) LyricLine(
                        text = lineFullText,
                        start = startLine,
                        end = endLine,
                        subtitles = subs,
                        isEmptyLine = lineFullText.isEmpty()
                    ) else LyricLine(
                        text = "",
                        start = startLine,
                        end = endLine,
                        subtitles = subs,
                        isEmptyLine = lineFullText.isEmpty()
                    )
                    val lyricLineBeat = if (indexGroup == group) LyricLine(
                        text = lineBeatText,
                        start = startLine,
                        end = endLine,
                        subtitles = subs,
                        isEmptyLine = lineFullText.isEmpty()
                    ) else LyricLine(
                        text = "",
                        start = startLine,
                        end = endLine,
                        subtitles = subs,
                        isEmptyLine = lineFullText.isEmpty()
                    )
                    val lstLineFull = lyricLinesFullTextGroups[indexGroup] ?: mutableListOf()
                    val lstLineBeat = lyricLinesBeatTextGroups[indexGroup] ?: mutableListOf()
                    lstLineFull.add(lyricLineFull)
                    lstLineBeat.add(lyricLineBeat)
                    lyricLinesFullTextGroups[indexGroup] =
                        lstLineFull                                                       // Добавляем строку lyric в список строк lyrics
                    lyricLinesBeatTextGroups[indexGroup] = lstLineBeat
                }

                // Создаем объект classes.LyricLine и инициализируем его переменными. На данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми

                val lineDuration =
                    getDurationInMilliseconds(startLine, endLine)     // Находим время "звучания" строки в миллисекундах
                maxLineDuration = java.lang.Long.max(
                    maxLineDuration,
                    lineDuration
                )                    // Находим максимальное время "звучания" среди всех строк
                // Добавляем строку lyric в список строк lyrics

            }
        }

        var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль

        var index = 0
        lyricLinesFullText.forEach { lyricFullText -> // Проходимся по массиву строк
            index++
            val silentDuration =
                convertTimecodeToMilliseconds(lyricFullText.start) - currentPositionEnd  // Вычисляем время "тишины"
            val linesToInsert: Long =
                silentDuration / maxLineDuration // Вычисляем кол-во "пустых" строк, которые надо вставить перед текущей строкой

            if (linesToInsert > 0) { // Если количество вставляемых пустых строк больше нуля - начинаем их вставлять

                val silentLineDuration: Long =
                    silentDuration / linesToInsert // Вычисляем "длительность" вставляемой пустой строки

                for (i in 1..linesToInsert) { // Цикл от 1 до количества вставляемых строк включительно

                    val startDuration =
                        convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration / 5) // Время начала строки
                    val endDuration =
                        convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration) // Время конца строки

                    // Создаем объект classes.Subtitle с пустым текстом, с вычисленными выше началом и концом и помечаем его и как начало и конец строки
                    val subtitleEmpty = Subtitle(
                        text = "",
                        startTimecode = startDuration,
                        endTimecode = endDuration,
                        isLineStart = true,
                        isLineEnd = true
                    )

                    // Создаем объект classes.LyricLine, в его subtitles помещаем единственный "пустой" classes.Subtitle, созданный шагом раньше
                    // помечаем его как пустую строку
                    val lyricEmpty = LyricLine(
                        text = "",
                        start = startDuration,
                        end = endDuration,
                        subtitles = listOf(subtitleEmpty),
                        isEmptyLine = true,
                        isFadeLine = resultLyricLinesFullText.isEmpty() || lyricFullText == lyricLinesFullText.last()
                    )

                    currentPositionEnd += silentLineDuration    // Устанавливаем текущую позицию конца равной позиции конца созданной пустой строки

                    resultLyricLinesFullText.add(lyricEmpty)
                    for (indexGroup in 0L until MAX_GROUPS) {
                        val lstLineFull = resultLyricLinesFullTextGroups[indexGroup] ?: mutableListOf()
                        val lstLineBeat = resultLyricLinesBeatTextGroups[indexGroup] ?: mutableListOf()
                        lstLineFull.add(lyricEmpty)
                        lstLineBeat.add(lyricEmpty)
                        resultLyricLinesFullTextGroups[indexGroup] =
                            lstLineFull                                                       // Добавляем строку lyric в список строк lyrics
                        resultLyricLinesBeatTextGroups[indexGroup] = lstLineBeat
                    }

                    text += "\n"
                }
            }
            // Добавляем строку lyric в список строк result

            resultLyricLinesFullText.add(lyricFullText)
            for (indexGroup in 0L until MAX_GROUPS) {
                val lstLineFull = resultLyricLinesFullTextGroups[indexGroup] ?: mutableListOf()
                val lstLineBeat = resultLyricLinesBeatTextGroups[indexGroup] ?: mutableListOf()
                lstLineFull.add(lyricLinesFullTextGroups[indexGroup]!![index - 1])
                lstLineBeat.add(lyricLinesBeatTextGroups[indexGroup]!![index - 1])
                resultLyricLinesFullTextGroups[indexGroup] =
                    lstLineFull                                                       // Добавляем строку lyric в список строк lyrics
                resultLyricLinesBeatTextGroups[indexGroup] = lstLineBeat
            }

            currentPositionEnd =
                convertTimecodeToMilliseconds(lyricFullText.end) // Устанавливаем текущую позицию конца равной позиции конца текущей строки
            text += "${lyricFullText.text}\n"

        }
        // Теперь в списке result у нас нужное количество строк - как полных, так и пустых.


        val maxTextWidthPx =
            FRAME_WIDTH_PX.toDouble() - TITLE_POSITION_START_X_PX * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
        val maxTextLengthSym =
            resultLyricLinesFullText.maxBy { it.text.length }.text.length   // maxTextLength - максимальная длина текста (в символах) = длине символов самой длинной строки
        val maxSymbolWidthPx =
            maxTextWidthPx / maxTextLengthSym                            // maxSymbolWidth - максимальная ширина символа = максимальная ширина текста делённая на максимальную длину
        val fontSizePt = Integer.min(
            getFontSizeBySymbolWidth(maxSymbolWidthPx),
            KLT_ITEM_CONTENT_FONT_SIZE_PT
        ) // Размер шрифта для найденной максимальной ширины символа
        val symbolHeightPx = getSymbolHeight(fontSizePt)
        val symbolWidthPx = getSymbolWidth(fontSizePt)
        val boxHeightPx =
            ((resultLyricLinesFullText.size + 1) * symbolHeightPx.toLong())  // boxHeight - высота "бокса" текста = количество строк текста * высоту символа
        val boxWidthPx =
            (maxTextLengthSym * symbolWidthPx)                         // boxHeight - ширина "бокса" текста = ширина текста * ширину символа
        val workAreaHeightPx = boxHeightPx + symbolHeightPx // Высота рабочей области
        val horizonPositionPx =
            (FRAME_HEIGHT_PX / 2 + symbolHeightPx / 2) - HORIZON_OFFSET_PX    // horizonPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет
        val songLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val fontSizeProgress = 30
        val progressSymbolHalfWidth = (getSymbolWidth(fontSizeProgress) / 2).toLong()
        val kdeHeaderAuthor = "Исполнитель: ${song.settings.author}"
        val kdeHeaderTone = "Тональность: ${song.settings.key}"
        val kdeHeaderBpm = "Темп: ${song.settings.bpm} bpm"
        val kdeHeaderAlbum = "Альбом: ${song.settings.album}"
        val kdeHeaderSongName = song.settings.songName
        val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / song.settings.songName.length), 80)
        val yOffset = -5

        propProgressLineValue.add("00:00:00.000=-${progressSymbolHalfWidth} $yOffset $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
        propProgressLineValue.add("${song.endTimecode}=${FRAME_WIDTH_PX - progressSymbolHalfWidth} $yOffset $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")

        param["WORK_AREA_HEIGHT_PX"] = workAreaHeightPx
        param["HORIZON_POSITION_PX"] = horizonPositionPx
        param["SONG_LENGTH_MS"] = songLengthMs
        param["BOX_HEIGHT_PX"] = boxHeightPx
        param["BOX_WIDTH_PX"] = boxWidthPx
        param["FONT_SIZE_PT"] = fontSizePt
        param["LYRIC_LINES_FULL_TEXT_GROUPS"] = resultLyricLinesFullTextGroups
        param["LYRIC_LINES_BEAT_TEXT_GROUPS"] = resultLyricLinesBeatTextGroups
        param["LYRIC_LINES_FULL_TEXT"] = resultLyricLinesFullText
        param["FONT_SIZE_PROGRESS"] = fontSizeProgress
        param["HEADER_AUTHOR"] = kdeHeaderAuthor
        param["HEADER_TONE"] = kdeHeaderTone
        param["HEADER_BPM"] = kdeHeaderBpm
        param["HEADER_ALBUM"] = kdeHeaderAlbum
        param["HEADER_SONG_NAME"] = kdeHeaderSongName
        param["HEADER_SONG_NAME_FONT_SIZE"] = fontNameSizePt

        val templateSongText = getTemplateSongText(param)
        val templateHorizon = getTemplateHorizon(param)
        val templateProgress = getTemplateProgress(param)
        val templateWatermark = getTemplateWatermark(param)
        val templateHeader = getTemplateHeader(param)
        val templateCounter0 = getTemplateCounter0(param)
        val templateCounter1 = getTemplateCounter1(param)
        val templateCounter2 = getTemplateCounter2(param)
        val templateCounter3 = getTemplateCounter3(param)
        val templateCounter4 = getTemplateCounter4(param)
        val templateBeat1 = getTemplateBeat1(param)
        val templateBeat2 = getTemplateBeat2(param)
        val templateBeat3 = getTemplateBeat3(param)
        val templateBeat4 = getTemplateBeat4(param)

        resultLyricLinesFullText.forEachIndexed { itemIndex, lyricLine -> // Проходимся по всем строкам

            val startTp = TransformProperty(
                time = lyricLine.start,
                x = 0,
                y = horizonPositionPx - ((itemIndex + 1) * (symbolHeightPx + HEIGHT_CORRECTION)).toLong(),
                w = FRAME_WIDTH_PX,
                h = workAreaHeightPx,
                opacity = if (lyricLine.isFadeLine) 0.0 else if (itemIndex < (resultLyricLinesFullText.size) - 1) 1.0 else 0.0
            )

            var time = lyricLine.end
            // Если текущий элемент не последний - надо проверить начало следующего элемента
            if (itemIndex != resultLyricLinesFullText.size - 1) {
                val nextLyricLine = resultLyricLinesFullText[itemIndex + 1] // Находим следующую строку
                val diffInMills = getDiffInMilliseconds(
                    nextLyricLine.start,
                    lyricLine.end
                ) // Находим разницу во времени между текущей строкой и следующей
                if (diffInMills < 200) {                            // Если эта разница меньше 200 мс
                    lyricLine.end =
                        nextLyricLine.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                    lyricLine.subtitles.last().endTimecode = lyricLine.end
                    time =
                        lyricLine.subtitles.last().startTimecode       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
                }
            }

            val endTp = TransformProperty(
                time = time,
                x = 0,
                y = horizonPositionPx - ((itemIndex + 1) * (symbolHeightPx + HEIGHT_CORRECTION)).toLong(),
                w = FRAME_WIDTH_PX,
                h = workAreaHeightPx,
                opacity = if (lyricLine.isFadeLine) 0.0 else if (itemIndex < (resultLyricLinesFullText.size) - 1) 1.0 else 0.0
            )
            lyricLine.startTp = startTp
            lyricLine.endTp = endTp
        }

        if (!resultLyricLinesFullText[0].isEmptyLine) {
            val currentLyricLine = resultLyricLinesFullText[0]
            propRectLineValue.add("00:00:00.000=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} 0.0")
            propRectLineValue.add("00:00:01.000=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} 1.0")
        }

        // Настало время прописать classes.TransformProperty для заливок

        for (i in 0 until resultLyricLinesFullText.size) { // Проходимся по строкам - от первой до последней

            val currentLyricLine = resultLyricLinesFullText[i] // Текущая строка
            val nextLyricLine =
                if (i < resultLyricLinesFullText.size - 1) resultLyricLinesFullText[i + 1] else null //Следующая строка
            val diffInMills =
                if (nextLyricLine != null) convertTimecodeToMilliseconds(nextLyricLine.start) - convertTimecodeToMilliseconds(
                    currentLyricLine.end
                ) else 0 // Разница во времени между текущей строкой и следующей
            propRectLineValue.add("${currentLyricLine.startTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

            // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
            if (currentLyricLine.text != "") { // Если текущая строка не пустая
                propRectLineValue.add("${currentLyricLine.endTp?.time}=${currentLyricLine.endTp?.x} ${currentLyricLine.endTp?.y} ${currentLyricLine.endTp?.w} ${currentLyricLine.endTp?.h} ${currentLyricLine.endTp?.opacity}")
                var ww = 1.0 // Начальная позиция w для заливки = 1
                var currentSubtitle =
                    currentLyricLine.subtitles[0] // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
                val startTime =
                    convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currentSubtitle.startTimecode) - (1000 / FRAME_FPS + 1)) // Время начала анимации = времени начала этого титра минус 1 фрейм
                val x =
                    TITLE_POSITION_START_X_PX + TITLE_OFFSET_START_X_PX // Координата x всегда одна и та же = TITLE_POSITION_START_X_PX + TITLE_OFFSET_START_X_PX
                var y = horizonPositionPx - symbolHeightPx // Координата y = позиция горизонта - высота символа
                val h = symbolHeightPx // Высота = высоте символа
                val propRectTitleValueFade =
                    "$startTime=$x $y ${ww.toLong()} $h 0.0" // Свойство трансформации заливки с полной прозрачностью
                propRectTitleValueLineOddEven[i % 2].add(propRectTitleValueFade)
                ww = -TITLE_OFFSET_START_X_PX.toDouble() // Смещаем стартовую позицию w на величину TITLE_OFFSET_START_X_PX

                for (j in 0..(currentLyricLine.subtitles.size) - 2) { // Проходимся по титрам текущей линии от первого до предпоследнего
                    val currentSub = currentLyricLine.subtitles[j] // Текущий титр
                    val nextSub = currentLyricLine.subtitles[j + 1] // Следующий титр
                    var time = currentSub.startTimecode // Время - начало текущего титра
                    val w = currentSub.text.length * symbolWidthPx // Ширина = ширина текста тира * ширину символа
                    val propRectTitleValueStart =
                        "$time=$x $y ${ww.toLong()} $h 0.6" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                    time = nextSub.startTimecode // Время - начало следующего титра
                    ww += w // Ширина = предыдущее значение ширины + ширина
                    val propRectTitleValueEnd =
                        "$time=$x $y ${ww.toLong()} $h 0.6" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                    if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueStart) propRectTitleValueLineOddEven[i % 2].add(
                        propRectTitleValueStart
                    )
                    if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueEnd) propRectTitleValueLineOddEven[i % 2].add(
                        propRectTitleValueEnd
                    )
                }

                // На этом этапе мы закрасили все титры линии, кроме последнего
                currentSubtitle =
                    currentLyricLine.subtitles[(currentLyricLine.subtitles.size) - 1]   // Текущий титр - последний титр текущей строки
                val nextSubtitle = nextLyricLine!!.subtitles[0]  // Следующий титр - первый титр следующей строки
                var time = currentSubtitle.startTimecode  // Время - начало текущего титра
                val w = currentSubtitle.text.length * symbolWidthPx  // Ширина = ширина текста титра * ширину символа
                val propRectTitleValueStart =
                    "$time=$x $y ${ww.toLong()} $h 0.6" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueStart) propRectTitleValueLineOddEven[i % 2].add(
                    propRectTitleValueStart
                )
                time = currentSubtitle.endTimecode  // Время - конец текущего титра
                ww += w  // Ширина = предыдущее значение ширины + ширина
                if (diffInMills < 200) y -= symbolHeightPx
                val propRectTitleValueEnd = "$time=$x $y ${ww.toLong()} $h 0.6"
                if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueEnd) propRectTitleValueLineOddEven[i % 2].add(
                    propRectTitleValueEnd
                )

                if (diffInMills < 200) { // Если между текущей и следующей строкой меньше 200 мс
                    // На данном этапе залили последний титр строки - пора сделать фэйд
                    time = nextSubtitle.startTimecode // Время - начало следующего титра
                    var propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.6"
                    if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i % 2].add(
                        propRectTitleValueFadeOut
                    )
                    time = nextSubtitle.endTimecode // Время - конец следующего титра
                    propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.0"
                    if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i % 2].add(
                        propRectTitleValueFadeOut
                    )
                } else { // Если между текущей и следующей строкой больше или равно 200 мс
                    // На данный момент мы закрасили всю строку, и теперь надо её сфэйдить с переходом на новую строку
                    y -= symbolHeightPx        // Поднимаем y на высоту символа
                    time = nextSubtitle.startTimecode  // Время - начало следующего титра
                    val propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.0"
                    if (propRectTitleValueLineOddEven[i % 2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i % 2].add(
                        propRectTitleValueFadeOut
                    )
                }
            }
        }

        // Настало время заняться счётчиками вступления. В том случае, если для песни известно BPM

        val quarterNoteLengthMs =
            if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms // Находим длительность звучания 1/4 ноты в миллисекундах
        val halfNoteLengthMs = quarterNoteLengthMs * 2

        // Счетчики надо вставлять тогда, когда перед не пустой строкой шла пустая. Найдём и пометим такие строки
        var currentTime = 0L
        var previousLineIsEmpty = true
        resultLyricLinesFullText.forEach { lyricLine -> // Проходимся по строкам
            if (!lyricLine.isEmptyLine) { // Если строка не пустая
                if (previousLineIsEmpty || currentTime == 0L) { // Если предыдущая строка была пустой или это первая не пустая строка
                    lyricLine.isNeedCounter = true // Помечаем строку для счётчика
                }
                currentTime = convertTimecodeToMilliseconds(lyricLine.end) // Запоминаем текущую позицию
            }
            previousLineIsEmpty = lyricLine.isEmptyLine
        }
        // На данный момент мы пометили всё нужные строки, для которых нужен счётчик
        lyricLinesFullText.filter { it.isNeedCounter }
            .forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
                for (counterNumber in 0..4) {
                    val startTimeMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * counterNumber
                    val initTimeMs = startTimeMs - (1000 / FRAME_FPS + 1)
                    val endTimeMs = startTimeMs + halfNoteLengthMs
                    if (startTimeMs > 0) {
                        counters[counterNumber].add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                        counters[counterNumber].add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")
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
        propGuidesValue.add("""{"comment": "Offset", "pos": ${convertMillisecondsToFrames(TIME_OFFSET_MS)}, "type": 0}""")
        while ((delayMs + currentPositionStartMs + beatMs) < convertTimecodeToMilliseconds(song.endTimecode)) {

            val tick = (beatCounter - 1) % 4
            currentPositionStartMs = (delayMs + beatMs * (beatCounter - 1))// + TIME_OFFSET_MS
            val currentPositionStartFrame = convertMillisecondsToFrames(currentPositionStartMs)

            val currentPositionStartMs2fb =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs) - 2, FRAME_FPS)
            val currentPositionStartMs1fb =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs) - 1, FRAME_FPS)
            val currentPositionEndMs = convertFramesToMilliseconds(
                convertMillisecondsToFrames(currentPositionStartMs + (beatMs * (4 - tick))) - 3,
                FRAME_FPS
            )
            val currentPositionEndMs1fa =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs) + 1, FRAME_FPS)
            val currentPositionEndMs2fa =
                convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs) + 2, FRAME_FPS)

            val point0 =
                "${convertMillisecondsToTimecode(currentPositionStartMs2fb)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
            val point1 =
                "${convertMillisecondsToTimecode(currentPositionStartMs1fb)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
            val point2 = "${convertMillisecondsToTimecode(currentPositionStartMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0"
            val point3 = "${convertMillisecondsToTimecode(currentPositionEndMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
            val point4 =
                "${convertMillisecondsToTimecode(currentPositionEndMs1fa)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
            val point5 =
                "${convertMillisecondsToTimecode(currentPositionEndMs2fa)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"

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
        val kdeInOffset = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + TIME_OFFSET_MS)
        val kdeFadeIn = "00:00:01.000"
        val kdeOut = song.endTimecode.replace(",", ".")
        val kdeFadeOut = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.endTimecode) - 1000).replace(",", ".")
        val kdeLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
        val kdeLengthFrames = convertTimecodeToFrames(song.endTimecode, FRAME_FPS)

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


        param["AUDIOSONG_ID"] = idProducerAudioSong
        param["AUDIOSONG_PATH"] = song.settings.audioSongFileName

        param["AUDIOMUSIC_ID"] = idProducerAudioMusic
        param["AUDIOMUSIC_PATH"] = song.settings.audioMusicFileName

        param["AUDIOVOCAL_ID"] = idProducerAudioVocal
        param["AUDIOVOCAL_PATH"] = song.settings.audioVocalFileName
        param["HIDE_TRACTOR_AUDIOVOCAL"] = "both"

        param["SONGTEXT_ID"] = idProducerSongText
        param["SONGTEXT_WORK_AREA_HEIGHT_PX"] = workAreaHeightPx
        param["SONGTEXT_XML_DATA"] = templateSongText
        param["SONGTEXT_PROPERTY_RECT"] = propRectValue
        param["HIDE_TRACTOR_SONGTEXT"] = "audio"


        param["HORIZON_ID"] = idProducerHorizon
        param["HORIZON_XML_DATA"] = templateHorizon
        param["HIDE_TRACTOR_HORIZON"] = "audio"


        param["WATERMARK_ID"] = idProducerWatermark
        param["WATERMARK_XML_DATA"] = templateWatermark
        param["HIDE_TRACTOR_WATERMARK"] = "audio"


        param["PROGRESS_ID"] = idProducerProgress
        param["PROGRESS_XML_DATA"] = templateProgress
        param["PROGRESS_PROPERTY_RECT"] = propProgressValue
        param["HIDE_TRACTOR_PROGRESS"] = "audio"


        param["FILLCOLOR_ID"] = idProducerFillColor
        param["FILLCOLOR_EVEN_PROPERTY_RECT"] = propFillEvenValue
        param["FILLCOLOR_ODD_PROPERTY_RECT"] = propFillOddValue
        param["HIDE_TRACTOR_FILLCOLOR_EVEN"] = "audio"
        param["HIDE_TRACTOR_FILLCOLOR_ODD"] = "audio"


        param["LOGOTYPE_ID"] = idProducerLogotype
        param["LOGOTYPE_PATH"] = kdeLogoPath
        param["HIDE_TRACTOR_LOGOTYPE"] = "audio"


        param["HEADER_ID"] = idProducerHeader
        param["HEADER_XML_DATA"] = templateHeader
        param["HIDE_TRACTOR_HEADER"] = "audio"


        param["MICROPHONE_ID"] = idProducerMicrophone
        param["MICROPHONE_PATH"] = kdeMicrophonePath


        param["BACKGROUND_ID"] = idProducerBackground
        param["BACKGROUND_PATH"] = getRandomFile(kdeBackgroundFolderPath, ".png")
        param["HIDE_TRACTOR_BACKGROUND"] = "audio"


        param["COUNTER0_ID"] = idProducerCounter0
        param["COUNTER0_XML_DATA"] = templateCounter0
        param["COUNTER0_PROPERTY_RECT"] = propFillCounter0Value
        param["HIDE_TRACTOR_COUNTER0"] = "audio"

        param["COUNTER1_ID"] = idProducerCounter1
        param["COUNTER1_XML_DATA"] = templateCounter1
        param["COUNTER1_PROPERTY_RECT"] = propFillCounter1Value
        param["HIDE_TRACTOR_COUNTER1"] = "audio"

        param["COUNTER2_ID"] = idProducerCounter2
        param["COUNTER2_XML_DATA"] = templateCounter2
        param["COUNTER2_PROPERTY_RECT"] = propFillCounter2Value
        param["HIDE_TRACTOR_COUNTER2"] = "audio"

        param["COUNTER3_ID"] = idProducerCounter3
        param["COUNTER3_XML_DATA"] = templateCounter3
        param["COUNTER3_PROPERTY_RECT"] = propFillCounter3Value
        param["HIDE_TRACTOR_COUNTER3"] = "audio"

        param["COUNTER4_ID"] = idProducerCounter4
        param["COUNTER4_XML_DATA"] = templateCounter4
        param["COUNTER4_PROPERTY_RECT"] = propFillCounter4Value
        param["HIDE_TRACTOR_COUNTER4"] = "audio"


        param["BEAT1_ID"] = idProducerBeat1
        param["BEAT1_XML_DATA"] = templateBeat1
        param["BEAT1_PROPERTY_RECT"] = propBeat1Value
        param["HIDE_TRACTOR_BEAT1"] = "audio"

        param["BEAT2_ID"] = idProducerBeat2
        param["BEAT2_XML_DATA"] = templateBeat2
        param["BEAT2_PROPERTY_RECT"] = propBeat2Value
        param["HIDE_TRACTOR_BEAT2"] = "audio"

        param["BEAT3_ID"] = idProducerBeat3
        param["BEAT3_XML_DATA"] = templateBeat3
        param["BEAT3_PROPERTY_RECT"] = propBeat3Value
        param["HIDE_TRACTOR_BEAT3"] = "audio"

        param["BEAT4_ID"] = idProducerBeat4
        param["BEAT4_XML_DATA"] = templateBeat4
        param["BEAT4_PROPERTY_RECT"] = propBeat4Value
        param["HIDE_TRACTOR_BEAT4"] = "audio"

        param["${ProducerType.AUDIOVOCAL.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.AUDIOMUSIC.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.AUDIOSONG.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.BACKGROUND.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.MICROPHONE.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.HORIZON.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.PROGRESS.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.FILLCOLOR.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.SONGTEXT.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.HEADER.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.LOGOTYPE.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.BEAT.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.COUNTER.text.uppercase()}}_ENABLED"] = true
        param["${ProducerType.WATERMARK.text.uppercase()}}_ENABLED"] = true

    }


    val fileIsKaraoke = listOf(false, true)

//    param.forEach{println("${it.key} : ${it.value}")}

    fileIsKaraoke.forEach { isKaraoke ->
        param["SONG_PROJECT_FILENAME"] = if (isKaraoke) song.settings.projectKaraokeFileName else song.settings.projectLyricsFileName
        param["SONG_VIDEO_FILENAME"] = if (isKaraoke) song.settings.videoKaraokeFileName else song.settings.videoLyricsFileName
        param["HIDE_TRACTOR_AUDIOMUSIC"] = if (isKaraoke) "video" else "both"
        param["HIDE_TRACTOR_AUDIOSONG"] = if (isKaraoke) "both" else "video"
        param["HIDE_TRACTOR_MICROPHONE"] = if (isKaraoke) "audio" else "both"

        val fileProjectName = "${song.settings.rootFolder}/${if (isKaraoke) song.settings.projectKaraokeFileName else song.settings.projectLyricsFileName}"
        val fileSubtitleName = "$fileProjectName.srt"

        val templateProject = "<?xml version='1.0' encoding='utf-8'?>\n${getMlt(param)}"

        File(fileProjectName).writeText(templateProject)
        File(fileSubtitleName).writeText(song.srtFileBody[0]?:"")
    }

}


fun getSong(settings: Settings): Song {

    val mapFiles = mutableMapOf<Int, String>()
    val listFile = getListFiles(settings.rootFolder, ".srt", "${settings.fileName}.kdenlive")
    listFile.sorted().forEachIndexed() { index, fileName ->
        mapFiles[index] = fileName
    }

    val result = Song()

    for (i in listFile.indices) {
        // Считываем содержимое файла субтитров
        val body = File("${settings.rootFolder}/${settings.subtitleFileName}").readText(Charsets.UTF_8)

        var beatTimecode = "00:00:00.000"
        var group = 0L

        val subtitles: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()

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
                        "BEAT" -> beatTimecode = startEnd.split(" --> ")[0].replace(",",".")
                        "GROUP" -> group = if (settingList.size > 2) settingList[2].toLong() else 0
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
                        group = group
                    )
                    // Добавляем этот объект к списку объектов
                    subtitles.add(subtitle)
                }
            }

        }

        val beatMs = if (settings.ms == 0L) (60000.0 / settings.bpm).toLong() else settings.ms
        subtitles.forEach { subtitle ->
            subtitle.isBeat = if (subtitle.text != "") {
                val startBeatNumber = getBeatNumberByTimecode(subtitle.startTimecode, beatMs, beatTimecode)
                val endBeatNumber = getBeatNumberByTimecode(subtitle.endTimecode, beatMs, beatTimecode)
                startBeatNumber > endBeatNumber
            } else false
        }

        if (i == 0) {
            result.settings = settings
            result.endTimecode = subtitles.last().endTimecode
            result.beatTimecode = beatTimecode
        }
        result.srtFileBody[i.toLong()] = body
        result.subtitles[i.toLong()] = subtitles
    }

    return result
}

fun getSettings(pathToSettingsFile: String): Settings {
    val settingFilePath = Path(pathToSettingsFile)
    val settingRoot = settingFilePath.parent.toString()
    val settingFileNameList = settingFilePath.fileName.toString()
        .split(".")
        .toMutableList()
    settingFileNameList.removeLast()
    val settingFileName = settingFileNameList.joinToString(".")

    val settings = Settings()
    settings.rootFolder = settingRoot
    settings.fileName = settingFileName
    settings.subtitleFileName = "${settingFileName}.kdenlive.srt"
    settings.audioSongFileName = "${settingFileName}.flac"
    settings.audioMusicFileName = "$settingFileName [music].wav"
    settings.audioVocalFileName = "$settingFileName [vocals].wav"
    settings.projectLyricsFileName = "$settingFileName [lyrics].kdenlive"
    settings.videoLyricsFileName = "$settingFileName [lyrics].mp4"
    settings.projectKaraokeFileName = "$settingFileName [karaoke].kdenlive"
    settings.videoKaraokeFileName = "$settingFileName [karaoke].mp4"

    val body = File(pathToSettingsFile).readText(Charsets.UTF_8)
    body.split("\n").forEach { line ->
        println(line)
        val settingList = line.split("=")
        if (settingList.size == 2) {
            val settingName = settingList[0].uppercase()
            val settingValue = settingList[1]
            when (settingName) {
                "NAME" -> settings.songName = settingValue
                "AUTHOR" -> settings.author = settingValue
                "ALBUM" -> settings.album = settingValue
                "KEY" -> settings.key = settingValue
                "BPM" -> settings.bpm = settingValue.toLong()
                "MS" -> settings.ms = settingValue.toLong()
                "FORMAT" -> settings.audioSongFileName = "${settingFileName}.${settingValue}"
            }
        }
    }

    return settings
}
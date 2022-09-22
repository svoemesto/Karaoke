import model.Lyric
import model.LyricLine
import model.Settings
import model.Song
import model.Subtitle
import model.TransformProperty
import java.io.File

fun main(args: Array<String>) {

    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум"
    val file = "(11) [Агата Кристи] Опиум для никого"
    getLyric(getSong(getSettings("${folder}/${file}.settings")))

}

fun getLyric(song: Song): Lyric {

    var startLine: String? = null
    var endLine: String?

    var subs: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()
    val lyricsFullText: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()
    val lyricsBeatText: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()
    val resultFullText: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()
    val resultBeatText: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()

    val counters = listOf(
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList()
    )

    val beats = listOf(
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList()
    )

    val propRectLineValue = emptyList<String>().toMutableList() // Список свойств трансформации текста
    val propGuidesValue = emptyList<String>().toMutableList()
    val guidesTypes = listOf<Long>(8,7,4,3)

    val propRectTitleValueLineOddEven = listOf(
        emptyList<String>().toMutableList(),
        emptyList<String>().toMutableList()
    )

    var text = ""
    var lineFullText = ""
    var lineBeatText = ""
    var maxLineDuration = 0L

    // Проходимся по всем сабам
    song.subtitles.forEach { subtitle ->
        // Если саб - начало строки - начинаем новую строку (пока она пустая) и инициализируем пустой список subs
        if (subtitle.isLineStart == true) {
            startLine = subtitle.start
            lineFullText = ""
            lineBeatText = ""
            subs = emptyList<Subtitle>().toMutableList()
        }

        lineFullText += if (!subtitle.isBeat) subtitle.text else subtitle.text?.let { replaceVowelOrConsonantLetters(it, true) }    // Дописываем в текст текущей строки текст из саба
        lineBeatText += if (subtitle.isBeat) subtitle.text?.let { replaceVowelOrConsonantLetters(it, false) } else subtitle.text?.let {" ".repeat(it.length)}
        subs.add(subtitle)      // Добавляем текущий саб к списку subs

        // Если саб - конец строки
        if (subtitle.isLineEnd == true) {

            endLine = subtitle.end  // Устанавливаем конец строки позицией конца из саба

            // Создаем объект classes.LyricLine и инициализируем его переменными. На данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми
            val lyricLineFull = LyricLine(text = lineFullText, start = startLine, end = endLine, subtitles = subs)
            val lyricLineBeat = LyricLine(text = lineBeatText, start = startLine, end = endLine, subtitles = subs)

            val lineDuration = getDurationInMilliseconds(startLine!!, endLine!!)     // Находим время "звучания" строки в миллисекундах
            maxLineDuration = java.lang.Long.max(
                maxLineDuration,
                lineDuration
            )                    // Находим максимальное время "звучания" среди всех строк
            lyricsFullText.add(lyricLineFull)                                                       // Добавляем строку lyric в список строк lyrics
            lyricsBeatText.add(lyricLineBeat)                                                       // Добавляем строку lyric в список строк lyrics

        }
    }

    var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль

    var index = 0
    lyricsFullText.forEach { lyricFullText -> // Проходимся по массиву строк
        val lyricBeatText = lyricsBeatText[index]
        index ++
        val silentDuration = convertTimecodeToMilliseconds(lyricFullText.start!!) - currentPositionEnd  // Вычисляем время "тишины"
        val linesToInsert: Long = silentDuration / maxLineDuration // Вычисляем кол-во "пустых" строк, которые надо вставить перед текущей строкой

        if (linesToInsert > 0) { // Если количество вставляемых пустых строк больше нуля - начинаем их вставлять

            val silentLineDuration: Long =  silentDuration / linesToInsert // Вычисляем "длительность" вставляемой пустой строки

            for (i in 1..linesToInsert) { // Цикл от 1 до количества вставляемых строк включительно

                val startDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration/5) // Время начала строки
                val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration) // Время конца строки

                // Создаем объект classes.Subtitle с пустым текстом, с вычисленными выше началом и концом и помечаем его и как начало и конец строки
                val subtitleEmpty = Subtitle(
                    text = "",
                    start = startDuration,
                    end = endDuration,
                    isLineStart = true,
                    isLineEnd = true)

                // Создаем объект classes.LyricLine, в его subtitles помещаем единственный "пустой" classes.Subtitle, созданный шагом раньше
                // помечаем его как пустую строку
                val lyricEmpty = LyricLine(
                    text = "",
                    start = startDuration,
                    end = endDuration,
                    subtitles = listOf(subtitleEmpty),
                    isEmptyLine = true,
                    isFadeLine = resultFullText.isEmpty() || lyricFullText == lyricsFullText.last())

                currentPositionEnd += silentLineDuration    // Устанавливаем текущую позицию конца равной позиции конца созданной пустой строки
                resultFullText.add(lyricEmpty)                      // Добавляем lyricEmpty в список строк result
                resultBeatText.add(lyricEmpty)                      // Добавляем lyricEmpty в список строк result
                text += "\n"
            }
        }

        if (lyricFullText != lyricsFullText.last()) {
            resultFullText.add(lyricFullText)
            resultBeatText.add(lyricBeatText)
        } // Добавляем строку lyric в список строк result если строка не последняя
        currentPositionEnd = convertTimecodeToMilliseconds(lyricFullText.end!!) // Устанавливаем текущую позицию конца равной позиции конца текущей строки
        text += "${lyricFullText.text}\n"

    }

    // Теперь в списке result у нас нужное количество строк - как полных, так и пустых.

    // Создаём объект classes.Lyric и в его items помещаем этот список
    val resultLyricFullText = Lyric()
    val resultLyricBeatText = Lyric()
    resultLyricFullText.items = resultFullText
    resultLyricBeatText.items = resultBeatText

    val maxTextWidthPx = FRAME_WIDTH_PX.toDouble() - TITLE_POSITION_START_X_PX * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
    val maxTextLengthSym = resultLyricFullText.items.maxBy { it.text!!.length }.text!!.length   // maxTextLength - максимальная длина текста (в символах) = длине символов самой длинной строки
    val maxSymbolWidthPx = maxTextWidthPx / maxTextLengthSym                            // maxSymbolWidth - максимальная ширина символа = максимальная ширина текста делённая на максимальную длину
    val fontSizePt = Integer.min(
        getFontSizeBySymbolWidth(maxSymbolWidthPx),
        KLT_ITEM_CONTENT_FONT_SIZE_PT
    ) // Размер шрифта для найденной максимальной ширины символа
    val symbolHeightPx = getSymbolHeight(fontSizePt)
    val symbolWidthPx = getSymbolWidth(fontSizePt)
    val boxHeightPx = ((resultLyricFullText.items.size + 1) * symbolHeightPx.toLong())  // boxHeight - высота "бокса" текста = количество строк текста * высоту символа
    val boxWidthPx = (maxTextLengthSym * symbolWidthPx)                         // boxHeight - ширина "бокса" текста = ширина текста * ширину символа
    val workAreaHeightPx = boxHeightPx + symbolHeightPx // Высота рабочей области

    // Шаблон для файла субтитра текста
    val templateTitle = """<kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$workAreaHeightPx" out="0">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="$LINE_SPACING" shadow="$SHADOW" font-underline="$FONT_UNDERLINE" box-height="$boxHeightPx" font="$FONT_NAME" letter-spacing="0" font-pixel-size="$fontSizePt" font-italic="$FONT_ITALIC" typewriter="$TYPEWRITER" alignment="$ALIGNMENT" font-weight="$FONT_WEIGHT" box-width="$boxWidthPx" font-color="$FONT_COLOR_TEXT">${resultFullText.map { it.text }.joinToString("\n")}</content>
 </item>
 <item type="QGraphicsTextItem" z-index="1">
  <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="$LINE_SPACING" shadow="$SHADOW" font-underline="$FONT_UNDERLINE" box-height="$boxHeightPx" font="$FONT_NAME" letter-spacing="0" font-pixel-size="$fontSizePt" font-italic="$FONT_ITALIC" typewriter="$TYPEWRITER" alignment="$ALIGNMENT" font-weight="$FONT_WEIGHT" box-width="$boxWidthPx" font-color="${if (SHOW_BEAT_SUBS) FONT_COLOR_BEAT else FONT_COLOR_TEXT}">${resultBeatText.map { it.text }.joinToString("\n")}</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,${workAreaHeightPx}"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,${workAreaHeightPx}"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>"""

    val horizontPositionPx = (FRAME_HEIGHT_PX / 2 + symbolHeightPx / 2) - HORIZONT_OFFSET_PX    // horizontPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет

    // Шаблон для файла субтитра "горизонта"
    val templateHorizont = """<kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="0">
 <item type="QGraphicsRectItem" z-index="0">
  <position x="0" y="${horizontPositionPx.toLong()}">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="255,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,$FRAME_WIDTH_PX,3"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>"""

    // Настало время прописать classes.TransformProperty для строк

    resultLyricFullText.items.forEachIndexed { index, lyricLine -> // Проходимся по всем строкам

        val startTp = TransformProperty(
            time = lyricLine.start,
            x = 0,
            y = horizontPositionPx - ((index + 1)*(symbolHeightPx + HEIGHT_CORRECTION)).toLong(),
            w = FRAME_WIDTH_PX,
            h = workAreaHeightPx,
            if(lyricLine.isFadeLine) 0.0 else 1.0
        )

        var time = lyricLine.end!!
        // Если текущий элемент не последний - надо проверить начало следующего элемента
        if (index != resultLyricFullText.items.size-1) {
            val nextLyricLine = resultLyricFullText.items[index+1] // Находим следующую строку
            val diffInMills = getDiffInMilliseconds(nextLyricLine.start!!, lyricLine.end!!) // Находим разницу во времени между текущей строкой и следующей
            if (diffInMills < 200) {                            // Если эта разница меньше 200 мс
                lyricLine.end = nextLyricLine.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                lyricLine.subtitles.last().end = lyricLine.end
                time = lyricLine.subtitles.last().start!!       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
            }
        }

        val endTp = TransformProperty(
            time = time,
            x = 0,
            y = horizontPositionPx - ((index + 1)*(symbolHeightPx + HEIGHT_CORRECTION)).toLong(),
            w = FRAME_WIDTH_PX,
            h = workAreaHeightPx,
            opacity = if(lyricLine.isFadeLine) 0.0 else 1.0
        )
        lyricLine.startTp = startTp
        lyricLine.endTp = endTp
    }

    // Настало время прописать classes.TransformProperty для заливок

    for (i in 0 until resultLyricFullText.items.size) { // Проходимся по строкам - от первой до последней

        val currentLyricLine = resultLyricFullText.items[i] // Текущая строка
        val nextLyricLine = if (i < resultLyricFullText.items.size -1 ) resultLyricFullText.items[i+1] else null //Следующая строка
        val diffInMills = if (nextLyricLine != null) convertTimecodeToMilliseconds(nextLyricLine.start!!) - convertTimecodeToMilliseconds(currentLyricLine.end!!) else 0 // Разница во времени между текущей строкой и следующей
        propRectLineValue.add("${currentLyricLine.startTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

        // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
        if (currentLyricLine.text != "") { // Если текущая строка не пустая
            propRectLineValue.add("${currentLyricLine.endTp?.time}=${currentLyricLine.endTp?.x} ${currentLyricLine.endTp?.y} ${currentLyricLine.endTp?.w} ${currentLyricLine.endTp?.h} ${currentLyricLine.endTp?.opacity}")
            var ww = 1.0 // Начальная позиция w для заливки = 1
            var currentSubtitle = currentLyricLine.subtitles[0] // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
            val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currentSubtitle.start!!)-(1000/FRAME_FPS+1)) // Время начала анимации = времени начала этого титра минус 1 фрейм
            val x = TITLE_POSITION_START_X_PX+TITLE_OFFSET_START_X_PX // Координата x всегда одна и та же = TITLE_POSITION_START_X_PX + TITLE_OFFSET_START_X_PX
            var y = horizontPositionPx - symbolHeightPx // Координата y = позиция горизонта - высота символа
            val h = symbolHeightPx // Высота = высоте символа
            val propRectTitleValueFade = "${startTime}=${x} ${y} ${ww.toLong()} ${h} 0.0" // Свойство трансформации заливки с полной прозрачностью
            propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFade)
            ww = -TITLE_OFFSET_START_X_PX.toDouble() // Смещаем стартовую позицию w на величину TITLE_OFFSET_START_X_PX

            for (j in 0..(currentLyricLine.subtitles.size) - 2) { // Проходимся по титрам текущей линии от первого до предпоследнего
                val currentSub = currentLyricLine.subtitles[j] // Текущий титр
                var time = currentSub.start // Время - начало текущего титра
                val w = currentSub.text!!.length * symbolWidthPx // Ширина = ширина текста тира * ширину символа
                val propRectTitleValueStart = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                time = currentSub.end // Время - конец текущего титра
                ww += w // Ширина = предыдущее значение ширины + ширина
                val propRectTitleValueEnd = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueStart) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueStart)
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueEnd) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueEnd)
            }

            // На этом этапе мы закрасили все титры линии, кроме последнего
            currentSubtitle = currentLyricLine.subtitles[(currentLyricLine.subtitles.size)-1]   // Текущий титр - последний титр текущей строки
            val nextSubtitle = nextLyricLine!!.subtitles[0]  // Следующий титр - первый титр следующей строки
            var time = currentSubtitle.start  // Время - начало текущего титра
            val w = currentSubtitle.text!!.length * symbolWidthPx  // Ширина = ширина текста титра * ширину символа
            val propRectTitleValueStart = "$time=$x $y ${ww.toLong()} $h 0.6" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
            if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueStart) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueStart)
            time = currentSubtitle.end  // Время - конец текущего титра
            ww += w  // Ширина = предыдущее значение ширины + ширина
            if (diffInMills < 200) y -= symbolHeightPx
            val propRectTitleValueEnd = "$time=$x $y ${ww.toLong()} $h 0.6"
            if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueEnd) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueEnd)

            if (diffInMills < 200) { // Если между текущей и следующей строкой меньше 200 мс
                // На данном этапе залили последний титр строки - пора сделать фэйд
                time = nextSubtitle.start // Время - начало следующего титра
                var propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.6"
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFadeOut)
                time = nextSubtitle.end // Время - конец следующего титра
                propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.0"
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFadeOut)
            } else { // Если между текущей и следующей строкой больше или равно 200 мс
                // На данный момент мы закрасили всю строку, и теперь надо её сфэйдить с переходом на новую строку
                y -= symbolHeightPx        // Поднимаем y на высоту символа
                time = nextSubtitle.start  // Время - начало следующего титра
                var propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.0"
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFadeOut)
            }
        }
    }

    // Настало время заняться счётчиками вступления. В том случае, если для песни известно BPM

    val quarterNoteLengthMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms // Находим длительность звучания 1/4 ноты в миллисекундах

    // Счетчики надо вставлять тогда, когда перед не пустой строкой шла пустая. Найдём и пометим такие строки
    var currentTime = 0L
    var previousLineIsEmpty = true
    resultLyricFullText.items.forEach { lyricLine -> // Проходимся по строкам
        if (!lyricLine.isEmptyLine) { // Если строка не пустая
            if (previousLineIsEmpty || currentTime == 0L) { // Если предыдущая строка была пустой или это первая не пустая строка
                lyricLine.isNeedCounter = true // Помечаем строку для счётчика
            }
            currentTime = convertTimecodeToMilliseconds(lyricLine.end!!) // Запоминаем текущую позицию
        }
        previousLineIsEmpty = lyricLine.isEmptyLine
    }
    // На данный момент мы пометили всё нужные строки, для которых нужен счётчик
    lyricsFullText.filter { it.isNeedCounter }.forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
        for (counterNumber in 0 .. 4) {
            val startTimeMs = convertTimecodeToMilliseconds(lyric.start!!) - quarterNoteLengthMs * counterNumber
            val initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
            val endTimeMs = startTimeMs + quarterNoteLengthMs
            if (startTimeMs > 0) {
                counters[counterNumber].add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                counters[counterNumber].add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                counters[counterNumber].add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")
            }
        }
    }

    // Такты
    var delayMs = convertTimecodeToMilliseconds(song.beat!!) // + TIME_OFFSET_MS

    // Такт в мс
    val beatMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms// song.delay
    val different = ((delayMs / (beatMs * 4))-1) * (beatMs * 4)
    delayMs -= different

    var currentPositionStartMs = 0L
    var beatCounter = 1L
    propGuidesValue.add("""{"comment": "Offset", "pos": ${convertMillisecondsToFrames(TIME_OFFSET_MS)}, "type": 0}""")
    while ((delayMs + currentPositionStartMs + beatMs) < convertTimecodeToMilliseconds(song.end!!)) {

        val tick = (beatCounter-1)%4
        currentPositionStartMs = (delayMs + beatMs * (beatCounter-1)).toLong()// + TIME_OFFSET_MS
        val currentPositionStartFrame = convertMillisecondsToFrames(currentPositionStartMs)

        val currentPositionStartMs2fb = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs)-2, FRAME_FPS)
        val currentPositionStartMs1fb = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs)-1, FRAME_FPS)
        val currentPositionEndMs = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs + (beatMs * (4-tick)).toLong())-3, FRAME_FPS)
        val currentPositionEndMs1fa = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs)+1, FRAME_FPS)
        val currentPositionEndMs2fa = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionEndMs)+2, FRAME_FPS)

        val point0 = "${convertMillisecondsToTimecode(currentPositionStartMs2fb)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
        val point1 = "${convertMillisecondsToTimecode(currentPositionStartMs1fb)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
        val point2 = "${convertMillisecondsToTimecode(currentPositionStartMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0"
        val point3 = "${convertMillisecondsToTimecode(currentPositionEndMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
        val point4 = "${convertMillisecondsToTimecode(currentPositionEndMs1fa)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"
        val point5 = "${convertMillisecondsToTimecode(currentPositionEndMs2fa)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0"


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

    resultLyricFullText.fontSize = fontSizePt
    resultLyricFullText.horizontPosition = horizontPositionPx.toLong()
    resultLyricFullText.symbolHeight = symbolHeightPx
    resultLyricFullText.symbolWidth = symbolWidthPx

    val kdeHeaderTone = "Тональность: ${song.settings.key}"
    val kdeHeaderBpm = "Темп: ${song.settings.bpm} bpm"
    val kdeHeaderAlbum = "Альбом: ${song.settings.album}"
    val kdeHeaderSongName = song.settings.songName

    val kdeIn = "00:00:00.000"
    val kdeInOffset = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + TIME_OFFSET_MS)
    val kdeFadeIn = "00:00:01.000"
    val kdeOut = song.end!!.replace(",",".")
    val kdeFadeOut = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.end!!) - 1000).replace(",",".")
    val kdeLength = convertTimecodeToMilliseconds(song.end!!)
    val kdeSongTextXmlData = templateTitle
    val kdeHorizontXmlData = templateHorizont

    val fileIsKaraoke = listOf(false, true)

    fileIsKaraoke.forEach { isKaraoke ->

        val templateProject = """<?xml version='1.0' encoding='utf-8'?>
<mlt LC_NUMERIC="C" producer="main_bin" version="7.9.0" root="${song.settings.rootFolder}">
 <profile frame_rate_num="60" sample_aspect_num="1" display_aspect_den="9" colorspace="709" progressive="1" description="HD 1080p 60 fps" display_aspect_num="16" frame_rate_den="1" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" sample_aspect_den="1"/>
 <producer id="producer_song_text" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">$kdeOut</property>
  <property name="kdenlive:clipname">Текст песни</property>
  <property name="xmldata">$kdeSongTextXmlData</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">2</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$workAreaHeightPx</property>
 </producer>
 <producer id="producer_horizont" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">$kdeOut</property>
  <property name="kdenlive:clipname">Горизонт</property>
  <property name="xmldata">$kdeHorizontXmlData</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">3</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_orange" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">0xff8000ff</property>
  <property name="aspect_ratio">1</property>
  <property name="mlt_service">color</property>
  <property name="kdenlive:clipname">Оранжевый</property>
  <property name="kdenlive:duration">00:00:05.000</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">4</property>
  <property name="mlt_image_format">rgb</property>
 </producer>
 <producer id="producer_logotype" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">$kdeLogoPath</property>
  <property name="ttl">25</property>
  <property name="aspect_ratio">1</property>
  <property name="progressive">1</property>
  <property name="seekable">1</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
  <property name="mlt_service">qimage</property>
  <property name="kdenlive:clipname">Логотип</property>
  <property name="kdenlive:duration">00:00:05.000</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">5</property>
  <property name="kdenlive:file_size">30611</property>
 </producer>
 <producer id="producer_header" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">Заголовок</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsTextItem" z-index="6">
  <position x="223" y="169">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="233.797" font-color="85,255,255,255">$kdeHeaderBpm</content>
 </item>
 <item type="QGraphicsTextItem" z-index="5">
  <position x="96" y="132">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="359.688" font-color="85,255,255,255">$kdeHeaderTone</content>
 </item>
 <item type="QGraphicsRectItem" z-index="4">
  <position x="0" y="210">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,$FRAME_WIDTH_PX,50" gradient="#ff000000;#00bf4040;0;100;90"/>
 </item>
 <item type="QGraphicsTextItem" z-index="2">
  <position x="185" y="96">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="395.656" font-color="85,255,255,255">$kdeHeaderAlbum</content>
 </item>
 <item type="QGraphicsTextItem" z-index="1">
  <position x="96" y="0">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="106" font="JetBrains Mono" letter-spacing="0" font-pixel-size="80" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="818.734" font-color="255,255,127,255">$kdeHeaderSongName</content>
 </item>
 <item type="QGraphicsRectItem" z-index="-1">
  <position x="0" y="0">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,$FRAME_WIDTH_PX,210"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">6</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_background" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">$kdeBackgroundPath</property>
  <property name="ttl">25</property>
  <property name="aspect_ratio">1</property>
  <property name="progressive">1</property>
  <property name="seekable">1</property>
  <property name="meta.media.width">4096</property>
  <property name="meta.media.height">4096</property>
  <property name="mlt_service">qimage</property>
  <property name="kdenlive:clipname">Фон</property>
  <property name="kdenlive:duration">00:00:05.000</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">7</property>
  <property name="kdenlive:file_size">9425672</property>
 </producer>
 <producer id="producer_microphone" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">$kdeMicrophonePath</property>
  <property name="ttl">25</property>
  <property name="aspect_ratio">1</property>
  <property name="progressive">1</property>
  <property name="seekable">1</property>
  <property name="meta.media.width">249</property>
  <property name="meta.media.height">412</property>
  <property name="mlt_service">qimage</property>
  <property name="kdenlive:clipname">Караоке</property>
  <property name="kdenlive:duration">00:00:05.000</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">8</property>
  <property name="kdenlive:file_size">62987</property>
 </producer>
 <producer id="producer_counter4" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">4</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="897" y="300">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="264" font="JetBrains Mono" letter-spacing="0" font-pixel-size="200" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="120" font-color="255,0,0,255">4</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">9</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_counter3" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">3</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="897" y="300">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="264" font="JetBrains Mono" letter-spacing="0" font-pixel-size="200" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="120" font-color="255,0,0,255">3</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">10</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_counter2" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">2</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="897" y="300">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="264" font="JetBrains Mono" letter-spacing="0" font-pixel-size="200" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="120" font-color="255,255,0,255">2</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">11</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_counter1" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">1</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="897" y="300">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="264" font="JetBrains Mono" letter-spacing="0" font-pixel-size="200" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="120" font-color="255,255,0,255">1</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">12</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_counter0" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">0</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="777" y="300">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="264" font="JetBrains Mono" letter-spacing="0" font-pixel-size="200" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="360" font-color="85,255,0,255">GO!</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">13</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_audio_song" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.settings.audioSongFileName}</property>
  <property name="meta.media.nb_streams">1</property>
  <property name="meta.media.0.stream.type">audio</property>
  <property name="meta.media.0.codec.sample_fmt">s16</property>
  <property name="meta.media.0.codec.sample_rate">44100</property>
  <property name="meta.media.0.codec.channels">2</property>
  <property name="meta.media.0.codec.name">flac</property>
  <property name="meta.media.0.codec.long_name">FLAC (Free Lossless Audio Codec)</property>
  <property name="meta.media.0.codec.bit_rate">0</property>
  <property name="meta.attr.track.markup">06</property>
  <property name="meta.attr.TRACKTOTAL.markup">13</property>
  <property name="meta.attr.ALBUM.markup">Опиум</property>
  <property name="meta.attr.album_artist.markup">Агата Кристи</property>
  <property name="meta.attr.TITLE.markup">Сказочная тайга</property>
  <property name="meta.attr.ARTIST.markup">Агата Кристи</property>
  <property name="meta.attr.COMMENT.markup">by moogle_</property>
  <property name="meta.attr.GENRE.markup">Rock</property>
  <property name="meta.attr.DATE.markup">1994</property>
  <property name="seekable">1</property>
  <property name="audio_index">0</property>
  <property name="video_index">-1</property>
  <property name="mute_on_pause">1</property>
  <property name="mlt_service">avformat</property>
  <property name="kdenlive:clipname"/>
  <property name="kdenlive:clip_type">1</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:id">14</property>
  <property name="kdenlive:file_size">22762064</property>
  <property name="kdenlive:audio_max0">250</property>
 </producer>
 <producer id="producer_audio_music" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.settings.audioMusicFileName}</property>
  <property name="meta.media.nb_streams">1</property>
  <property name="meta.media.0.stream.type">audio</property>
  <property name="meta.media.0.codec.sample_fmt">s16</property>
  <property name="meta.media.0.codec.sample_rate">44100</property>
  <property name="meta.media.0.codec.channels">2</property>
  <property name="meta.media.0.codec.name">pcm_s16le</property>
  <property name="meta.media.0.codec.long_name">PCM signed 16-bit little-endian</property>
  <property name="meta.media.0.codec.bit_rate">1411200</property>
  <property name="seekable">1</property>
  <property name="audio_index">0</property>
  <property name="video_index">-1</property>
  <property name="mute_on_pause">1</property>
  <property name="mlt_service">avformat</property>
  <property name="kdenlive:clipname"/>
  <property name="kdenlive:clip_type">1</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:id">15</property>
  <property name="kdenlive:file_size">30326828</property>
  <property name="kdenlive:audio_max0">248</property>
 </producer>
 <producer id="producer_audio_vocal" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.settings.audioVocalFileName}</property>
  <property name="meta.media.nb_streams">1</property>
  <property name="meta.media.0.stream.type">audio</property>
  <property name="meta.media.0.codec.sample_fmt">s16</property>
  <property name="meta.media.0.codec.sample_rate">44100</property>
  <property name="meta.media.0.codec.channels">2</property>
  <property name="meta.media.0.codec.name">pcm_s16le</property>
  <property name="meta.media.0.codec.long_name">PCM signed 16-bit little-endian</property>
  <property name="meta.media.0.codec.bit_rate">1411200</property>
  <property name="seekable">1</property>
  <property name="audio_index">0</property>
  <property name="video_index">-1</property>
  <property name="mute_on_pause">1</property>
  <property name="mlt_service">avformat</property>
  <property name="kdenlive:clipname"/>
  <property name="kdenlive:clip_type">1</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:id">16</property>
  <property name="kdenlive:file_size">30326828</property>
  <property name="kdenlive:audio_max0">204</property>
 </producer>
 <producer id="producer_beat1" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">Такт1</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsRectItem" z-index="1">
  <position x="860" y="150">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,255,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,200,50"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">17</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_beat2" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">Такт2</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsRectItem" z-index="2">
  <position x="910" y="150">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,255,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,150,50"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">18</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_beat3" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">Такт3</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsRectItem" z-index="4">
  <position x="1010" y="150">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,255,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,50,50"/>
 </item>
 <item type="QGraphicsRectItem" z-index="3">
  <position x="960" y="150">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,255,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,100,50"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">19</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_beat4" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource"/>
  <property name="progressive">1</property>
  <property name="aspect_ratio">1</property>
  <property name="seekable">1</property>
  <property name="mlt_service">kdenlivetitle</property>
  <property name="kdenlive:duration">300</property>
  <property name="kdenlive:clipname">Такт4</property>
  <property name="xmldata"><kdenlivetitle duration="300" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="299">
 <item type="QGraphicsRectItem" z-index="4">
  <position x="1010" y="150">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="0,255,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,50,50"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">20</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <playlist id="main_bin">
  <property name="kdenlive:docproperties.activeTrack">3</property>
  <property name="kdenlive:docproperties.audioChannels">2</property>
  <property name="kdenlive:docproperties.audioTarget">-1</property>
  <property name="kdenlive:docproperties.compositing">1</property>
  <property name="kdenlive:docproperties.disablepreview">0</property>
  <property name="kdenlive:docproperties.documentid">1663318034767</property>
  <property name="kdenlive:docproperties.enableTimelineZone">0</property>
  <property name="kdenlive:docproperties.enableexternalproxy">0</property>
  <property name="kdenlive:docproperties.enableproxy">0</property>
  <property name="kdenlive:docproperties.externalproxyparams">./;GL;.LRV;./;GX;.MP4;./;GP;.LRV;./;GP;.MP4</property>
  <property name="kdenlive:docproperties.generateimageproxy">0</property>
  <property name="kdenlive:docproperties.generateproxy">0</property>
  <property name="kdenlive:docproperties.groups">[
]
</property>
  <property name="kdenlive:docproperties.kdenliveversion">22.08.0</property>
  <property name="kdenlive:docproperties.position">1340</property>
  <property name="kdenlive:docproperties.previewextension"/>
  <property name="kdenlive:docproperties.previewparameters"/>
  <property name="kdenlive:docproperties.profile">atsc_1080p_60</property>
  <property name="kdenlive:docproperties.proxyextension"/>
  <property name="kdenlive:docproperties.proxyimageminsize">2000</property>
  <property name="kdenlive:docproperties.proxyimagesize">800</property>
  <property name="kdenlive:docproperties.proxyminsize">1000</property>
  <property name="kdenlive:docproperties.proxyparams"/>
  <property name="kdenlive:docproperties.proxyresize">640</property>
  <property name="kdenlive:docproperties.rendercategory">Ultra-High Definition (4K)</property>
  <property name="kdenlive:docproperties.rendercustomquality">100</property>
  <property name="kdenlive:docproperties.renderendguide">-1</property>
  <property name="kdenlive:docproperties.renderexportaudio">0</property>
  <property name="kdenlive:docproperties.rendermode">0</property>
  <property name="kdenlive:docproperties.renderplay">0</property>
  <property name="kdenlive:docproperties.renderpreview">0</property>
  <property name="kdenlive:docproperties.renderprofile">MP4-H265 (HEVC)</property>
  <property name="kdenlive:docproperties.renderratio">1</property>
  <property name="kdenlive:docproperties.renderrescale">0</property>
  <property name="kdenlive:docproperties.renderrescaleheight">405</property>
  <property name="kdenlive:docproperties.renderrescalewidth">720</property>
  <property name="kdenlive:docproperties.renderspeed">8</property>
  <property name="kdenlive:docproperties.renderstartguide">-1</property>
  <property name="kdenlive:docproperties.rendertcoverlay">0</property>
  <property name="kdenlive:docproperties.rendertctype">-1</property>
  <property name="kdenlive:docproperties.rendertwopass">0</property>
  <property name="kdenlive:docproperties.scrollPos">0</property>
  <property name="kdenlive:docproperties.seekOffset">30000</property>
  <property name="kdenlive:docproperties.storagefolder">cachefiles/1663318034767</property>
  <property name="kdenlive:docproperties.version">1.04</property>
  <property name="kdenlive:docproperties.verticalzoom">1</property>
  <property name="kdenlive:docproperties.videoTarget">3</property>
  <property name="kdenlive:docproperties.zonein">4704</property>
  <property name="kdenlive:docproperties.zoneout">5678</property>
  <property name="kdenlive:docproperties.zoom">12</property>
  <property name="kdenlive:expandedFolders"/>
  <property name="kdenlive:documentnotes"/>
  <property name="kdenlive:docproperties.guides">[$propGuides]</property>
  <property name="kdenlive:docproperties.renderurl">${if (isKaraoke) song.settings.videoKaraokeFileName else song.settings.videoLyricsFileName}</property>
  <property name="xml_retain">1</property>
  <entry producer="producer_song_text" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_horizont" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_orange" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_logotype" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_header" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_background" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_microphone" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_counter4" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_counter3" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_counter2" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_counter1" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_counter0" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_audio_song" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_audio_music" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_audio_vocal" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_beat1" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_beat2" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_beat3" in="$kdeIn" out="$kdeOut"/>
  <entry producer="producer_beat4" in="$kdeIn" out="$kdeOut"/>
 </playlist>
 <producer id="black_track" in="$kdeIn" out="00:11:11.917">
  <property name="length">2147483647</property>
  <property name="eof">continue</property>
  <property name="resource">black</property>
  <property name="aspect_ratio">1</property>
  <property name="mlt_service">color</property>
  <property name="mlt_image_format">rgba</property>
  <property name="set.test_audio">0</property>
 </producer>
 <producer id="producer_audio_vocal_file" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.settings.audioVocalFileName}</property>
  <property name="audio_index">0</property>
  <property name="video_index">-1</property>
  <property name="mute_on_pause">0</property>
  <property name="mlt_service">avformat-novalidate</property>
  <property name="seekable">1</property>
  <property name="kdenlive:clipname"/>
  <property name="kdenlive:clip_type">1</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:id">16</property>
  <property name="kdenlive:file_size">30326828</property>
  <property name="kdenlive:audio_max0">204</property>
  <property name="xml">was here</property>
  <property name="set.test_audio">0</property>
  <property name="set.test_image">1</property>
 </producer>
 <playlist id="playlist_audio_vocal_file">
  <property name="kdenlive:audio_track">1</property>
  <blank length="$kdeInOffset"/>
  <entry producer="producer_audio_vocal_file" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">16</property>
  </entry>
 </playlist>
 <playlist id="playlist_audio_vocal_track">
  <property name="kdenlive:audio_track">1</property>
 </playlist>
 <tractor id="tractor_vocal" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:audio_track">1</property>
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">0</property>
  <property name="kdenlive:track_name">Vocal</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="both" producer="playlist_audio_vocal_file"/>
  <track hide="both" producer="playlist_audio_vocal_track"/>
  <filter id="filter0">
   <property name="window">75</property>
   <property name="max_gain">20dB</property>
   <property name="mlt_service">volume</property>
   <property name="internal_added">237</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter1">
   <property name="channel">-1</property>
   <property name="mlt_service">panner</property>
   <property name="internal_added">237</property>
   <property name="start">0.5</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter2">
   <property name="iec_scale">0</property>
   <property name="mlt_service">audiolevel</property>
   <property name="peak">1</property>
   <property name="disable">1</property>
  </filter>
 </tractor>
 <producer id="producer_audio_music_file" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.settings.audioMusicFileName}</property>
  <property name="audio_index">0</property>
  <property name="video_index">-1</property>
  <property name="mute_on_pause">0</property>
  <property name="mlt_service">avformat-novalidate</property>
  <property name="seekable">1</property>
  <property name="kdenlive:clipname"/>
  <property name="kdenlive:clip_type">1</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:id">15</property>
  <property name="kdenlive:file_size">30326828</property>
  <property name="kdenlive:audio_max0">248</property>
  <property name="xml">was here</property>
  <property name="set.test_audio">0</property>
  <property name="set.test_image">1</property>
 </producer>
 <playlist id="playlist_audio_music_file">
  <property name="kdenlive:audio_track">1</property>
  <blank length="$kdeInOffset"/>
  <entry producer="producer_audio_music_file" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">15</property>
  </entry>
 </playlist>
 <playlist id="playlist_audio_music_track">
  <property name="kdenlive:audio_track">1</property>
 </playlist>
 <tractor id="tractor_music" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:audio_track">1</property>
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">0</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Music</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="${if (isKaraoke) "video" else "both"}" producer="playlist_audio_music_file"/>
  <track hide="${if (isKaraoke) "video" else "both"}" producer="playlist_audio_music_track"/>
  <filter id="filter3">
   <property name="window">75</property>
   <property name="max_gain">20dB</property>
   <property name="mlt_service">volume</property>
   <property name="internal_added">237</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter4">
   <property name="channel">-1</property>
   <property name="mlt_service">panner</property>
   <property name="internal_added">237</property>
   <property name="start">0.5</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter5">
   <property name="iec_scale">0</property>
   <property name="mlt_service">audiolevel</property>
   <property name="peak">1</property>
   <property name="disable">1</property>
  </filter>
 </tractor>
 <producer id="producer_audio_song_file" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.settings.audioSongFileName}</property>
  <property name="audio_index">0</property>
  <property name="video_index">-1</property>
  <property name="mute_on_pause">0</property>
  <property name="mlt_service">avformat-novalidate</property>
  <property name="seekable">1</property>
  <property name="kdenlive:clipname"/>
  <property name="kdenlive:clip_type">1</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:id">14</property>
  <property name="kdenlive:file_size">22762064</property>
  <property name="kdenlive:audio_max0">250</property>
  <property name="xml">was here</property>
  <property name="set.test_audio">0</property>
  <property name="set.test_image">1</property>
 </producer>
 <playlist id="playlist_audio_song_file">
  <property name="kdenlive:audio_track">1</property>
  <blank length="$kdeInOffset"/>
  <entry producer="producer_audio_song_file" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">14</property>
  </entry>
 </playlist>
 <playlist id="playlist_audio_song_track">
  <property name="kdenlive:audio_track">1</property>
 </playlist>
 <tractor id="tractor_song" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:audio_track">1</property>
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">0</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">classes.Song</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="${if (isKaraoke) "both" else "video"}" producer="playlist_audio_song_file"/>
  <track hide="${if (isKaraoke) "both" else "video"}" producer="playlist_audio_song_track"/>
  <filter id="filter6">
   <property name="window">75</property>
   <property name="max_gain">20dB</property>
   <property name="mlt_service">volume</property>
   <property name="internal_added">237</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter7">
   <property name="channel">-1</property>
   <property name="mlt_service">panner</property>
   <property name="internal_added">237</property>
   <property name="start">0.5</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter8">
   <property name="iec_scale">0</property>
   <property name="mlt_service">audiolevel</property>
   <property name="peak">1</property>
   <property name="disable">1</property>
  </filter>
 </tractor>
 <playlist id="playlist_background_file">
  <entry producer="producer_background" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">7</property>
   <property name="kdenlive:activeeffect">1</property>
   <filter id="filter9">
    <property name="lift_r">$kdeIn=-0.199985</property>
    <property name="lift_g">$kdeIn=-0.199985</property>
    <property name="lift_b">$kdeIn=-0.199985</property>
    <property name="gamma_r">$kdeIn=0.724987</property>
    <property name="gamma_g">$kdeIn=0.724987</property>
    <property name="gamma_b">$kdeIn=0.724987</property>
    <property name="gain_r">$kdeIn=1</property>
    <property name="gain_g">$kdeIn=1</property>
    <property name="gain_b">$kdeIn=1</property>
    <property name="mlt_service">lift_gamma_gain</property>
    <property name="kdenlive_id">lift_gamma_gain</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
   <filter id="filter_background">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$kdeIn=0 0 4096 4096 0.000000;$kdeFadeIn=-13 -18 4096 4096 1.000000;$kdeFadeOut=-2163 -2998 4096 4096 1.000000;$kdeOut=-2176 -3016 4096 4096 0.000000</property>
    <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_background_track"/>
 <tractor id="tractor_background" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_background_file"/>
  <track hide="audio" producer="playlist_background_track"/>
 </tractor>
 <playlist id="playlist_microphone_file">
  <entry producer="producer_microphone" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">8</property>
   <filter id="karaoke_microfon">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$kdeIn=-90 477 230 129 0.000000;$kdeFadeIn=-90 477 230 129 1.000000;$kdeFadeOut=-90 477 230 129 1.000000;$kdeOut=-90 477 230 129 0.000000</property>
    <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_microphone_track"/>
 <tractor id="tractor_microphone" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Караоке</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="${if (isKaraoke) "audio" else "both"}" producer="playlist_microphone_file"/>
  <track hide="${if (isKaraoke) "audio" else "both"}" producer="playlist_microphone_track"/>
 </tractor>
 <playlist id="playlist_horizont_file">
  <entry producer="producer_horizont" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">3</property>
   <filter id="karaoke_horizont">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$kdeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000;$kdeFadeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.000000;$kdeFadeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.000000;$kdeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000</property>
    <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_horizont_track"/>
 <tractor id="tractor_horizont" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Горизонт</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_horizont_file"/>
  <track hide="audio" producer="playlist_horizont_track"/>
 </tractor>
 <playlist id="playlist_fill_odd_file">
  <entry producer="producer_orange" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">4</property>
   <filter id="filter_fill_even">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillEvenValue</property>
    <property name="compositing">0</property>
    <property name="distort">1</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_fill_odd_track"/>
 <tractor id="tractor_fill_odd" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">ODD</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_fill_odd_file"/>
  <track hide="audio" producer="playlist_fill_odd_track"/>
 </tractor>
 <playlist id="playlist_fill_even_file">
  <entry producer="producer_orange" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">4</property>
   <filter id="filter_fill_odd">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillOddValue</property>
    <property name="rotation">$kdeIn=0</property>
    <property name="compositing">0</property>
    <property name="distort">1</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_fill_even_track"/>
 <tractor id="tractor_fill_even" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">EVEN</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_fill_even_file"/>
  <track hide="audio" producer="playlist_fill_even_track"/>
 </tractor>
 <playlist id="playlist_songtext_file">
  <entry producer="producer_song_text" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">2</property>
   <filter id="karaoke_text">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propRectValue</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_songtext_track"/>
 <tractor id="tractor_textsong" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Текст песни</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_songtext_file"/>
  <track hide="audio" producer="playlist_songtext_track"/>
 </tractor>
 <playlist id="playlist_header_file">
  <entry producer="producer_header" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">6</property>
   <filter id="filter_header">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$kdeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000;$kdeFadeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.000000;$kdeFadeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.000000;$kdeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000</property>
    <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_header_track"/>
 <tractor id="tractor_header" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Заголовок</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_header_file"/>
  <track hide="audio" producer="playlist_header_track"/>
 </tractor>
 <playlist id="playlist_logo_file">
  <entry producer="producer_logotype" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">5</property>
   <filter id="karaoke_logotype">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$kdeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000;$kdeFadeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.000000;$kdeFadeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.000000;$kdeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000</property>
    <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_logo_track"/>
 <tractor id="tractor_logo" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Логотип</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_logo_file"/>
  <track hide="audio" producer="playlist_logo_track"/>
 </tractor>
 <playlist id="playlist_counter4_file">
  <entry producer="producer_counter4" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">9</property>
   <filter id="filter_counter_4">
    <property name="rotate_center">0</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillCounter4Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_counter4_track"/>
 <tractor id="tractor_counter4" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">4</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_counter4_file"/>
  <track hide="audio" producer="playlist_counter4_track"/>
 </tractor>
 <playlist id="playlist_counter3_file">
  <entry producer="producer_counter3" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">10</property>
   <filter id="filter_counter_3">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillCounter3Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_counter3_track"/>
 <tractor id="tractor_counter3" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">3</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_counter3_file"/>
  <track hide="audio" producer="playlist_counter3_track"/>
 </tractor>
 <playlist id="playlist_counter2_file">
  <entry producer="producer_counter2" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">11</property>
   <filter id="filter_counter_2">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillCounter2Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_counter2_track"/>
 <tractor id="tractor_counter2" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">2</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_counter2_file"/>
  <track hide="audio" producer="playlist_counter2_track"/>
 </tractor>
 <playlist id="playlist_counter1_file">
  <entry producer="producer_counter1" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">12</property>
   <filter id="filter_counter_1">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillCounter1Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_counter1_track"/>
 <tractor id="tractor_counter1" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">1</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_counter1_file"/>
  <track hide="audio" producer="playlist_counter1_track"/>
 </tractor>
 <playlist id="playlist_counter0_file">
  <entry producer="producer_counter0" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">13</property>
   <filter id="filter_counter_0">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propFillCounter0Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_counter0_track"/>
 <tractor id="tractor_counter0" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">0</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_counter0_file"/>
  <track hide="audio" producer="playlist_counter0_track"/>
 </tractor>
<playlist id="playlist_beat4_file">
  <entry producer="producer_beat4" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">20</property>
   <property name="kdenlive:activeeffect">0</property>
   <filter id="filter_beat4">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propBeat4Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_beat4_track"/>
 <tractor id="tractor_beat4" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">ТАКТ4</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist_beat4_file"/>
  <track hide="audio" producer="playlist_beat4_track"/>
 </tractor>
 <playlist id="playlist_beat3_file">
  <entry producer="producer_beat3" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">19</property>
   <property name="kdenlive:activeeffect">0</property>
   <filter id="filter_beat3">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propBeat3Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_beat3_track"/>
 <tractor id="tractor_beat3" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:track_name">ТАКТ3</property>
  <property name="kdenlive:collapsed">28</property>
  <track producer="playlist_beat3_file"/>
  <track producer="playlist_beat3_track"/>
 </tractor>
 <playlist id="playlist_beat2_file">
  <entry producer="producer_beat2" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">18</property>
   <property name="kdenlive:activeeffect">0</property>
   <filter id="filter_beat2">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propBeat2Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_beat2_track"/>
 <tractor id="tractor_beat2" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:track_name">ТАКТ2</property>
  <property name="kdenlive:collapsed">28</property>
  <track producer="playlist_beat2_file"/>
  <track producer="playlist_beat2_track"/>
 </tractor>
 <playlist id="playlist_beat1_file">
  <entry producer="producer_beat1" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">17</property>
   <property name="kdenlive:activeeffect">0</property>
   <filter id="filter_beat1">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$propBeat1Value</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist_beat1_track"/>
 <tractor id="tractor_beat1" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:track_name">ТАКТ1</property>
  <property name="kdenlive:collapsed">28</property>
  <track producer="playlist_beat1_file"/>
  <track producer="playlist_beat1_track"/>
 </tractor>
 <tractor id="tractor_timeline" in="$kdeIn" out="00:11:11.917">
  <track producer="black_track"/>
  <track producer="tractor_vocal"/>
  <track producer="tractor_music"/>
  <track producer="tractor_song"/>
  <track producer="tractor_background"/>
  <track producer="tractor_microphone"/>
  <track producer="tractor_horizont"/>
  <track producer="tractor_fill_odd"/>
  <track producer="tractor_fill_even"/>
  <track producer="tractor_textsong"/>
  <track producer="tractor_header"/>
  <track producer="tractor_logo"/>
  <track producer="tractor_counter4"/>
  <track producer="tractor_counter3"/>
  <track producer="tractor_counter2"/>
  <track producer="tractor_counter1"/>
  <track producer="tractor_counter0"/>
  <track producer="tractor_beat4"/>
  <track producer="tractor_beat3"/>
  <track producer="tractor_beat2"/>
  <track producer="tractor_beat1"/>
  <transition id="transition0">
   <property name="a_track">0</property>
   <property name="b_track">1</property>
   <property name="mlt_service">mix</property>
   <property name="kdenlive_id">mix</property>
   <property name="internal_added">237</property>
   <property name="always_active">1</property>
   <property name="accepts_blanks">1</property>
   <property name="sum">1</property>
  </transition>
  <transition id="transition1">
   <property name="a_track">0</property>
   <property name="b_track">2</property>
   <property name="mlt_service">mix</property>
   <property name="kdenlive_id">mix</property>
   <property name="internal_added">237</property>
   <property name="always_active">1</property>
   <property name="accepts_blanks">1</property>
   <property name="sum">1</property>
  </transition>
  <transition id="transition2">
   <property name="a_track">0</property>
   <property name="b_track">3</property>
   <property name="mlt_service">mix</property>
   <property name="kdenlive_id">mix</property>
   <property name="internal_added">237</property>
   <property name="always_active">1</property>
   <property name="accepts_blanks">1</property>
   <property name="sum">1</property>
  </transition>
  <transition id="transition3">
   <property name="a_track">0</property>
   <property name="b_track">4</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition4">
   <property name="a_track">0</property>
   <property name="b_track">5</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition5">
   <property name="a_track">0</property>
   <property name="b_track">6</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition6">
   <property name="a_track">0</property>
   <property name="b_track">7</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition7">
   <property name="a_track">0</property>
   <property name="b_track">8</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition8">
   <property name="a_track">0</property>
   <property name="b_track">9</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition9">
   <property name="a_track">0</property>
   <property name="b_track">10</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition10">
   <property name="a_track">0</property>
   <property name="b_track">11</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition11">
   <property name="a_track">0</property>
   <property name="b_track">12</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition12">
   <property name="a_track">0</property>
   <property name="b_track">13</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition13">
   <property name="a_track">0</property>
   <property name="b_track">14</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition14">
   <property name="a_track">0</property>
   <property name="b_track">15</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <transition id="transition15">
   <property name="a_track">0</property>
   <property name="b_track">16</property>
   <property name="version">0.1</property>
   <property name="mlt_service">frei0r.cairoblend</property>
   <property name="always_active">1</property>
   <property name="internal_added">237</property>
  </transition>
  <filter id="filter24">
   <property name="window">75</property>
   <property name="max_gain">20dB</property>
   <property name="mlt_service">volume</property>
   <property name="internal_added">237</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter25">
   <property name="channel">-1</property>
   <property name="mlt_service">panner</property>
   <property name="internal_added">237</property>
   <property name="start">0.5</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter10">
   <property name="mlt_service">avfilter.subtitles</property>
   <property name="internal_added">237</property>
   <property name="av.filename">/tmp/1663318034767.srt</property>
   <property name="disable">1</property>
  </filter>
  <filter id="filter11">
   <property name="iec_scale">0</property>
   <property name="mlt_service">audiolevel</property>
   <property name="peak">1</property>
   <property name="disable">1</property>
  </filter>
 </tractor>
</mlt>
"""
        val fileProjectName = "${song.settings.rootFolder}/${if (isKaraoke) song.settings.projectKaraokeFileName else song.settings.projectLyricsFileName}"
        val fileSubtitleName = "$fileProjectName.srt"
        File(fileProjectName).writeText(templateProject)
        File(fileSubtitleName).writeText(song.body)
    }

    return resultLyricFullText
}


fun getSong(settings: Settings): Song {
    // Считываем содержимое файла субтитров
    val body = File("${settings.rootFolder}/${settings.subtitleFileName}").readText(Charsets.UTF_8)

    var id: Long? = null
    var startEnd: String? = null
    var start: String? = null
    var end: String? = null
    var text: String? = null
    var beat: String? = null

    val subtitles: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()

    // Если массив строк не пустой - работаем с ним
    body.split("\n").forEach { sub ->
        if (id == null && sub != "") {                  // Если еще нет id и строка не пустая - значит текущая строка - это id
            id = sub.toLongOrNull()
        } else if (startEnd == null && sub != "") {     // Если еще нет startEnd и строка не пустая - значит текущая строка - это startEnd
            startEnd = sub
        } else if (text == null && sub != "") {          // Если еще нет text и строка не пустая - значит текущая строка - это text или настройка
            // Если sub начитается с "[SETTING]|" - это настройка
            if (sub.uppercase().startsWith("[SETTING]|")) {
                // Разделяем sub по | в список
                val settings = sub.split("|")
                when (settings[1].uppercase()) {
                    "BEAT" -> beat = startEnd!!.split(" --> ")[0]
                }
                // Обнуляем переменные
                id = null
                startEnd = null
                start = null
                end = null
                text = null
            } else {
                text = sub
            }
        } else if (text != null && sub != "") {          // Если уже есть text и строка всё еще не пустая - значит текущая строка - это тоже text
            text += sub
        } else if (sub == "") {                                        // Если строка пустая
            // Если уже есть все переменные
            if (id != null && startEnd != null && text != null) {
                // Разделяем startEnd на start и end в список, вынимаем из списка соответствующие значения
                // прибавляя к значению времени оффсет, указанный в TIME_OFFSET
                val se = startEnd!!.split(" --> ")
                start = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(se[0]))
                end = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(se[1]))
                val isLineStart = text!!.startsWith("//")   // Вычисляем признак начала строки
                val isLineEnd = text!!.endsWith("\\\\")     // Вычисляем признак конца строки
                // Удаляем служебные символы из строки и заменяем подчёркивание пробелом
                text = text!!
                    .replace("//", "")
                    .replace("\\\\", "")
                    .replace("_", " ")
                // Создаем объект classes.Subtitle и инициализируем его переменными
                val isBeat = if (text != "" && beat != null) {
                    val beatMs = if (settings.ms == 0L) (60000.0 / settings.bpm).toLong() else settings.ms
                    val startBeatNumber = getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(se[0]), beatMs, beat!!)
                    val endBeatNumber = getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(se[1]), beatMs, beat!!)
                    startBeatNumber > endBeatNumber
                } else false
                val subtitle = Subtitle(
                    id = id,
                    start = start,
                    end = end,
                    text = text,
                    isLineStart = isLineStart,
                    isLineEnd = isLineEnd,
                    isBeat = isBeat
                )
                // Добавляем этот объект к списку объектов
                subtitles.add(subtitle)
                // Обнуляем переменные
                id = null
                startEnd = null
                start = null
                end = null
                text = null
            }
        }
    }

    // Создаем объект classes.Song
    val result = Song()
    result.settings = settings
    // Устанавливаем end равный end последнего объекта из списка и найденные выше настройки (если они были)
    result.end = subtitles.last().end
    result.beat = beat
    result.body = body

    // В его объект Subtitles кладём список объектов classes.Subtitle
    result.subtitles = subtitles

    // Возвращаем объект classes.Song
    return result
}

fun getSettings(pathToSettingsFile: String): Settings {
    val settings = Settings()
    val body = File(pathToSettingsFile).readText(Charsets.UTF_8)
    body.split("\n").forEach { line ->
        println(line)
        val settingList = line.split("=")
        if (settingList.size == 2) {
            val settingName = settingList[0].uppercase()
            val settingValue = settingList[1]
            when (settingName) {
                "NAME" -> settings.songName = settingValue
                "ALBUM" -> settings.album = settingValue
                "KEY" -> settings.key = settingValue
                "BPM" -> settings.bpm = settingValue.toLong()
                "MS" -> settings.ms = settingValue.toLong()
                "ROOT" -> settings.rootFolder = settingValue
                "SUBTITLES" -> settings.subtitleFileName = settingValue
                "SONGFILE" -> settings.audioSongFileName = settingValue
                "MUSICFILE" -> settings.audioMusicFileName = settingValue
                "VOCALFILE" -> settings.audioVocalFileName = settingValue
                "PROJECTLYRICSFILE" -> settings.projectLyricsFileName = settingValue
                "PROJECTKARAOKEFILE" -> settings.projectKaraokeFileName = settingValue
                "VIDEOLYRICSFILE" -> settings.videoLyricsFileName = settingValue
                "VIDEOKARAOKEFILE" -> settings.videoKaraokeFileName = settingValue
            }
        }
    }
    return settings
}
//import model.Lyric
import model.LyricLine
import model.Settings
import model.Song
import model.Subtitle
import model.TransformProperty
import java.io.File
import kotlin.io.path.Path

fun createKaraoke(song: Song) {

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
    val idProducerOrange = 4
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
    val guidesTypes = listOf<Long>(8,7,4,3)

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
    song.subtitles.forEach { subtitle ->
        // Если саб - начало строки - начинаем новую строку (пока она пустая) и инициализируем пустой список subs
        if (subtitle.isLineStart) {
            startLine = subtitle.startTimecode
            lineText = ""
            lineFullText = ""
            lineBeatText = ""
            subs = mutableListOf<Subtitle>()
            group = subtitle.group
        }

        lineText += subtitle.text
        lineFullText += if (!subtitle.isBeat) subtitle.text else replaceVowelOrConsonantLetters(subtitle.text, true)    // Дописываем в текст текущей строки текст из саба
        lineBeatText += if (subtitle.isBeat) replaceVowelOrConsonantLetters(subtitle.text, false) else " ".repeat(subtitle.text.length)
        subs.add(subtitle)      // Добавляем текущий саб к списку subs

        // Если саб - конец строки
        if (subtitle.isLineEnd) {

            endLine = subtitle.endTimecode  // Устанавливаем конец строки позицией конца из саба

            lyricLinesFullText.add(LyricLine(text = lineText, start = startLine, end = endLine, subtitles = subs, isEmptyLine = lineFullText.isEmpty()))

            for (indexGroup in 0L until MAX_GROUPS) {
                val lyricLineFull = if (indexGroup == group) LyricLine(text = lineFullText, start = startLine, end = endLine, subtitles = subs, isEmptyLine = lineFullText.isEmpty()) else LyricLine(text = "", start = startLine, end = endLine, subtitles = subs, isEmptyLine = lineFullText.isEmpty())
                val lyricLineBeat = if (indexGroup == group) LyricLine(text = lineBeatText, start = startLine, end = endLine, subtitles = subs, isEmptyLine = lineFullText.isEmpty()) else LyricLine(text = "", start = startLine, end = endLine, subtitles = subs, isEmptyLine = lineFullText.isEmpty())
                val lstLineFull = lyricLinesFullTextGroups[indexGroup] ?: mutableListOf()
                val lstLineBeat = lyricLinesBeatTextGroups[indexGroup] ?: mutableListOf()
                lstLineFull.add(lyricLineFull)
                lstLineBeat.add(lyricLineBeat)
                lyricLinesFullTextGroups[indexGroup] = lstLineFull                                                       // Добавляем строку lyric в список строк lyrics
                lyricLinesBeatTextGroups[indexGroup] = lstLineBeat
            }

            // Создаем объект classes.LyricLine и инициализируем его переменными. На данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми

            val lineDuration = getDurationInMilliseconds(startLine, endLine)     // Находим время "звучания" строки в миллисекундах
            maxLineDuration = java.lang.Long.max(maxLineDuration, lineDuration)                    // Находим максимальное время "звучания" среди всех строк
                                                               // Добавляем строку lyric в список строк lyrics

        }
    }

    var currentPositionEnd = 0L // Устанавливаем текущую позицию конца в ноль

    var index = 0
    lyricLinesFullText.forEach { lyricFullText -> // Проходимся по массиву строк
        index ++
        val silentDuration = convertTimecodeToMilliseconds(lyricFullText.start) - currentPositionEnd  // Вычисляем время "тишины"
        val linesToInsert: Long = silentDuration / maxLineDuration // Вычисляем кол-во "пустых" строк, которые надо вставить перед текущей строкой

        if (linesToInsert > 0) { // Если количество вставляемых пустых строк больше нуля - начинаем их вставлять

            val silentLineDuration: Long =  silentDuration / linesToInsert // Вычисляем "длительность" вставляемой пустой строки

            for (i in 1..linesToInsert) { // Цикл от 1 до количества вставляемых строк включительно

                val startDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration/5) // Время начала строки
                val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration) // Время конца строки

                // Создаем объект classes.Subtitle с пустым текстом, с вычисленными выше началом и концом и помечаем его и как начало и конец строки
                val subtitleEmpty = Subtitle(
                    text = "",
                    startTimecode = startDuration,
                    endTimecode = endDuration,
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
                    isFadeLine = resultLyricLinesFullText.isEmpty() || lyricFullText == lyricLinesFullText.last())

                currentPositionEnd += silentLineDuration    // Устанавливаем текущую позицию конца равной позиции конца созданной пустой строки

                resultLyricLinesFullText.add(lyricEmpty)
                for (indexGroup in 0L until MAX_GROUPS) {
                    val lstLineFull = resultLyricLinesFullTextGroups[indexGroup] ?: mutableListOf()
                    val lstLineBeat = resultLyricLinesBeatTextGroups[indexGroup] ?: mutableListOf()
                    lstLineFull.add(lyricEmpty)
                    lstLineBeat.add(lyricEmpty)
                    resultLyricLinesFullTextGroups[indexGroup] = lstLineFull                                                       // Добавляем строку lyric в список строк lyrics
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
            lstLineFull.add(lyricLinesFullTextGroups[indexGroup]!![index-1])
            lstLineBeat.add(lyricLinesBeatTextGroups[indexGroup]!![index-1])
            resultLyricLinesFullTextGroups[indexGroup] = lstLineFull                                                       // Добавляем строку lyric в список строк lyrics
            resultLyricLinesBeatTextGroups[indexGroup] = lstLineBeat
        }

        currentPositionEnd = convertTimecodeToMilliseconds(lyricFullText.end) // Устанавливаем текущую позицию конца равной позиции конца текущей строки
        text += "${lyricFullText.text}\n"

    }
    // Теперь в списке result у нас нужное количество строк - как полных, так и пустых.


    val maxTextWidthPx = FRAME_WIDTH_PX.toDouble() - TITLE_POSITION_START_X_PX * 2      // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
    val maxTextLengthSym = resultLyricLinesFullText.maxBy { it.text.length }.text.length   // maxTextLength - максимальная длина текста (в символах) = длине символов самой длинной строки
    val maxSymbolWidthPx = maxTextWidthPx / maxTextLengthSym                            // maxSymbolWidth - максимальная ширина символа = максимальная ширина текста делённая на максимальную длину
    val fontSizePt = Integer.min(
        getFontSizeBySymbolWidth(maxSymbolWidthPx),
        KLT_ITEM_CONTENT_FONT_SIZE_PT
    ) // Размер шрифта для найденной максимальной ширины символа
    val symbolHeightPx = getSymbolHeight(fontSizePt)
    val symbolWidthPx = getSymbolWidth(fontSizePt)
    val boxHeightPx = ((resultLyricLinesFullText.size + 1) * symbolHeightPx.toLong())  // boxHeight - высота "бокса" текста = количество строк текста * высоту символа
    val boxWidthPx = (maxTextLengthSym * symbolWidthPx)                         // boxHeight - ширина "бокса" текста = ширина текста * ширину символа
    val workAreaHeightPx = boxHeightPx + symbolHeightPx // Высота рабочей области


    val templateTitleGroup = mutableListOf<String>()
    for (indexGroup in 0L until MAX_GROUPS) {
        templateTitleGroup.add("""
            <item type="QGraphicsTextItem" z-index="0">
              <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
               <transform>1,0,0,0,1,0,0,0,1</transform>
              </position>
              <content line-spacing="$LINE_SPACING" shadow="$SHADOW" font-underline="$FONT_UNDERLINE" box-height="$boxHeightPx" font="$FONT_NAME" letter-spacing="0" font-pixel-size="$fontSizePt" font-italic="$FONT_ITALIC" typewriter="$TYPEWRITER" alignment="$ALIGNMENT" font-weight="$FONT_WEIGHT" box-width="$boxWidthPx" font-color="${GROUPS_FONT_COLORS_TEXT[indexGroup]}">${resultLyricLinesFullTextGroups[indexGroup]?.map { it.text }?.joinToString("\n")}</content>
             </item>
             <item type="QGraphicsTextItem" z-index="0">
              <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
               <transform>1,0,0,0,1,0,0,0,1</transform>
              </position>
              <content line-spacing="$LINE_SPACING" shadow="$SHADOW" font-underline="$FONT_UNDERLINE" box-height="$boxHeightPx" font="$FONT_NAME" letter-spacing="0" font-pixel-size="$fontSizePt" font-italic="$FONT_ITALIC" typewriter="$TYPEWRITER" alignment="$ALIGNMENT" font-weight="$FONT_WEIGHT" box-width="$boxWidthPx" font-color="${GROUPS_FONT_COLORS_BEAT[indexGroup]}">${resultLyricLinesBeatTextGroups[indexGroup]?.map { it.text }?.joinToString("\n")}</content>
             </item>""")
    }


    // Шаблон для файла субтитра текста
    val templateTitle = """
        <kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$workAreaHeightPx" out="0">
         ${templateTitleGroup.joinToString("\n")}
         <startviewport rect="0,0,$FRAME_WIDTH_PX,${workAreaHeightPx}"/>
         <endviewport rect="0,0,$FRAME_WIDTH_PX,${workAreaHeightPx}"/>
         <background color="0,0,0,0"/>
        </kdenlivetitle>"""

    val templateHorizonGroup = mutableListOf<String>()
    val horizonPositionPx = (FRAME_HEIGHT_PX / 2 + symbolHeightPx / 2) - HORIZON_OFFSET_PX    // horizonPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет

    resultLyricLinesFullText.forEach { lyricLine ->
        if (!lyricLine.isEmptyLine) {
            val lineStartMs = convertTimecodeToMilliseconds(lyricLine.start)
            val lineEndMs = convertTimecodeToMilliseconds(lyricLine.end)
            val songLengthMs = convertTimecodeToMilliseconds(song.endTimecode)
            val lineX = ((lineStartMs.toDouble() / songLengthMs) * FRAME_WIDTH_PX).toLong()
            val lineW = ((lineEndMs.toDouble() / songLengthMs) * FRAME_WIDTH_PX).toLong() - lineX
            val templateHorizonGroupText = """
                <item type="QGraphicsRectItem" z-index="0">
                  <position x="0" y="${horizonPositionPx}">
                   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
                  </position>
                  <content brushcolor="${GROUPS_TIMELINE_COLORS[lyricLine.subtitles.first().group]}" pencolor="0,0,0,255" penwidth="0" rect="$lineX,0,$lineW,3"/>
                 </item>
                 """
            templateHorizonGroup.add(templateHorizonGroupText)
        }

    }

    // Шаблон для файла субтитра "горизонта"
    val templateHorizon = """
        <kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="0">
         <item type="QGraphicsRectItem" z-index="0">
          <position x="0" y="$horizonPositionPx">
           <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content brushcolor="${GROUPS_TIMELINE_COLORS[-1]}" pencolor="0,0,0,255" penwidth="0" rect="0,0,$FRAME_WIDTH_PX,3"/>
         </item>
         ${templateHorizonGroup.joinToString("\n")}
         <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <background color="0,0,0,0"/>
        </kdenlivetitle>"""

    // Настало время прописать classes.TransformProperty для строк

    val fontSizeProgress = 30
    val progressSymbolHalfWidth = (getSymbolWidth(fontSizeProgress)/2).toLong()
    val templateProgress = """
        <kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="0">
         <item type="QGraphicsTextItem" z-index="0">
          <position x="0" y="$horizonPositionPx">
           <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="$LINE_SPACING" shadow="0" font-underline="0" box-height="100" font="$FONT_NAME" letter-spacing="0" font-pixel-size="$fontSizeProgress" font-italic="0" typewriter="$TYPEWRITER" alignment="$ALIGNMENT" font-weight="$FONT_WEIGHT" box-width="10" font-color="$PROGRESS_COLOR">▲</content>
         </item>
         <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <background color="0,0,0,0"/>
        </kdenlivetitle>"""

    val yOffset = -5
    propProgressLineValue.add("00:00:00.000=-${progressSymbolHalfWidth} $yOffset $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
    propProgressLineValue.add("00:00:01.000=${FRAME_WIDTH_PX*(1000.0/convertTimecodeToMilliseconds(song.endTimecode)).toLong()-progressSymbolHalfWidth} $yOffset $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
    propProgressLineValue.add("${convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.endTimecode)-1000L)}=${FRAME_WIDTH_PX-FRAME_WIDTH_PX*(1000.0/convertTimecodeToMilliseconds(song.endTimecode)).toLong()-progressSymbolHalfWidth} $yOffset $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
    propProgressLineValue.add("${song.endTimecode}=${FRAME_WIDTH_PX-progressSymbolHalfWidth} $yOffset $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")

    val templateWatermark = """
        <kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="0">
         <item type="QGraphicsTextItem" z-index="0">
          <position x="1700" y="1063">
           <transform>1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="0" shadow="0;#64000000;3;3;3" font-underline="0" box-height="14" font-outline-color="0,0,0,255" font="JetBrains Mono" letter-spacing="0" font-pixel-size="10" font-italic="0" typewriter="0;2;1;0;0" alignment="2" font-weight="50" font-outline="0" box-width="216" font-color="255,255,255,255">https://github.com/svoemesto/Karaoke</content>
         </item>
         <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <background color="0,0,0,0"/>
        </kdenlivetitle>"""

    resultLyricLinesFullText.forEachIndexed { itemIndex, lyricLine -> // Проходимся по всем строкам

        val startTp = TransformProperty(
            time = lyricLine.start,
            x = 0,
            y = horizonPositionPx - ((itemIndex + 1)*(symbolHeightPx + HEIGHT_CORRECTION)).toLong(),
            w = FRAME_WIDTH_PX,
            h = workAreaHeightPx,
            opacity = if(lyricLine.isFadeLine) 0.0 else if (itemIndex < (resultLyricLinesFullText.size)-1) 1.0 else 0.0
        )

        var time = lyricLine.end
        // Если текущий элемент не последний - надо проверить начало следующего элемента
        if (itemIndex != resultLyricLinesFullText.size-1) {
            val nextLyricLine = resultLyricLinesFullText[itemIndex+1] // Находим следующую строку
            val diffInMills = getDiffInMilliseconds(nextLyricLine.start, lyricLine.end) // Находим разницу во времени между текущей строкой и следующей
            if (diffInMills < 200) {                            // Если эта разница меньше 200 мс
                lyricLine.end = nextLyricLine.start             // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                lyricLine.subtitles.last().endTimecode = lyricLine.end
                time = lyricLine.subtitles.last().startTimecode       // сдвигаем время classes.TransformProperty к началу последнего титра текущей строки
            }
        }

        val endTp = TransformProperty(
            time = time,
            x = 0,
            y = horizonPositionPx - ((itemIndex + 1)*(symbolHeightPx + HEIGHT_CORRECTION)).toLong(),
            w = FRAME_WIDTH_PX,
            h = workAreaHeightPx,
            opacity = if(lyricLine.isFadeLine) 0.0 else if (itemIndex < (resultLyricLinesFullText.size)-1) 1.0 else 0.0
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
        val nextLyricLine = if (i < resultLyricLinesFullText.size -1 ) resultLyricLinesFullText[i+1] else null //Следующая строка
        val diffInMills = if (nextLyricLine != null) convertTimecodeToMilliseconds(nextLyricLine.start) - convertTimecodeToMilliseconds(currentLyricLine.end) else 0 // Разница во времени между текущей строкой и следующей
        propRectLineValue.add("${currentLyricLine.startTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

        // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
        if (currentLyricLine.text != "") { // Если текущая строка не пустая
            propRectLineValue.add("${currentLyricLine.endTp?.time}=${currentLyricLine.endTp?.x} ${currentLyricLine.endTp?.y} ${currentLyricLine.endTp?.w} ${currentLyricLine.endTp?.h} ${currentLyricLine.endTp?.opacity}")
            var ww = 1.0 // Начальная позиция w для заливки = 1
            var currentSubtitle = currentLyricLine.subtitles[0] // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
            val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currentSubtitle.startTimecode)-(1000/FRAME_FPS+1)) // Время начала анимации = времени начала этого титра минус 1 фрейм
            val x = TITLE_POSITION_START_X_PX+TITLE_OFFSET_START_X_PX // Координата x всегда одна и та же = TITLE_POSITION_START_X_PX + TITLE_OFFSET_START_X_PX
            var y = horizonPositionPx - symbolHeightPx // Координата y = позиция горизонта - высота символа
            val h = symbolHeightPx // Высота = высоте символа
            val propRectTitleValueFade = "$startTime=$x $y ${ww.toLong()} $h 0.0" // Свойство трансформации заливки с полной прозрачностью
            propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFade)
            ww = -TITLE_OFFSET_START_X_PX.toDouble() // Смещаем стартовую позицию w на величину TITLE_OFFSET_START_X_PX

            for (j in 0..(currentLyricLine.subtitles.size) - 2) { // Проходимся по титрам текущей линии от первого до предпоследнего
                val currentSub = currentLyricLine.subtitles[j] // Текущий титр
                val nextSub = currentLyricLine.subtitles[j+1] // Следующий титр
                var time = currentSub.startTimecode // Время - начало текущего титра
                val w = currentSub.text.length * symbolWidthPx // Ширина = ширина текста тира * ширину символа
                val propRectTitleValueStart = "$time=$x $y ${ww.toLong()} $h 0.6" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                time = nextSub.startTimecode // Время - начало следующего титра
                ww += w // Ширина = предыдущее значение ширины + ширина
                val propRectTitleValueEnd = "$time=$x $y ${ww.toLong()} $h 0.6" // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueStart) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueStart)
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueEnd) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueEnd)
            }

            // На этом этапе мы закрасили все титры линии, кроме последнего
            currentSubtitle = currentLyricLine.subtitles[(currentLyricLine.subtitles.size)-1]   // Текущий титр - последний титр текущей строки
            val nextSubtitle = nextLyricLine!!.subtitles[0]  // Следующий титр - первый титр следующей строки
            var time = currentSubtitle.startTimecode  // Время - начало текущего титра
            val w = currentSubtitle.text.length * symbolWidthPx  // Ширина = ширина текста титра * ширину символа
            val propRectTitleValueStart = "$time=$x $y ${ww.toLong()} $h 0.6" // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
            if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueStart) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueStart)
            time = currentSubtitle.endTimecode  // Время - конец текущего титра
            ww += w  // Ширина = предыдущее значение ширины + ширина
            if (diffInMills < 200) y -= symbolHeightPx
            val propRectTitleValueEnd = "$time=$x $y ${ww.toLong()} $h 0.6"
            if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueEnd) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueEnd)

            if (diffInMills < 200) { // Если между текущей и следующей строкой меньше 200 мс
                // На данном этапе залили последний титр строки - пора сделать фэйд
                time = nextSubtitle.startTimecode // Время - начало следующего титра
                var propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.6"
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFadeOut)
                time = nextSubtitle.endTimecode // Время - конец следующего титра
                propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.0"
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFadeOut)
            } else { // Если между текущей и следующей строкой больше или равно 200 мс
                // На данный момент мы закрасили всю строку, и теперь надо её сфэйдить с переходом на новую строку
                y -= symbolHeightPx        // Поднимаем y на высоту символа
                time = nextSubtitle.startTimecode  // Время - начало следующего титра
                val propRectTitleValueFadeOut = "$time=$x $y ${ww.toLong()} $h 0.0"
                if (propRectTitleValueLineOddEven[i%2].last() != propRectTitleValueFadeOut) propRectTitleValueLineOddEven[i%2].add(propRectTitleValueFadeOut)
            }
        }
    }

    // Настало время заняться счётчиками вступления. В том случае, если для песни известно BPM

    val quarterNoteLengthMs = if (song.settings.ms == 0L) (60000.0 / song.settings.bpm).toLong() else song.settings.ms // Находим длительность звучания 1/4 ноты в миллисекундах
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
    lyricLinesFullText.filter { it.isNeedCounter }.forEach { lyric -> // Проходимся по всем строкам, для которых нужен счётчик
        for (counterNumber in 0 .. 4) {
            val startTimeMs = convertTimecodeToMilliseconds(lyric.start) - halfNoteLengthMs * counterNumber
            val initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
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
    val different = ((delayMs / (beatMs * 4))-1) * (beatMs * 4)
    delayMs -= different

    var currentPositionStartMs = 0L
    var beatCounter = 1L
    propGuidesValue.add("""{"comment": "Offset", "pos": ${convertMillisecondsToFrames(TIME_OFFSET_MS)}, "type": 0}""")
    while ((delayMs + currentPositionStartMs + beatMs) < convertTimecodeToMilliseconds(song.endTimecode)) {

        val tick = (beatCounter-1)%4
        currentPositionStartMs = (delayMs + beatMs * (beatCounter-1))// + TIME_OFFSET_MS
        val currentPositionStartFrame = convertMillisecondsToFrames(currentPositionStartMs)

        val currentPositionStartMs2fb = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs)-2, FRAME_FPS)
        val currentPositionStartMs1fb = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs)-1, FRAME_FPS)
        val currentPositionEndMs = convertFramesToMilliseconds(convertMillisecondsToFrames(currentPositionStartMs + (beatMs * (4-tick)))-3, FRAME_FPS)
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

    val kdeHeaderAuthor = "Исполнитель: ${song.settings.author}"
    val kdeHeaderTone = "Тональность: ${song.settings.key}"
    val kdeHeaderBpm = "Темп: ${song.settings.bpm} bpm"
    val kdeHeaderAlbum = "Альбом: ${song.settings.album}"
    val kdeHeaderSongName = song.settings.songName

    val kdeIn = "00:00:00.000"
    val kdeInOffset = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(kdeIn) + TIME_OFFSET_MS)
    val kdeFadeIn = "00:00:01.000"
    val kdeOut = song.endTimecode.replace(",",".")
    val kdeFadeOut = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.endTimecode) - 1000).replace(",",".")
    val kdeLength = convertTimecodeToMilliseconds(song.endTimecode)
    val kdeSongTextXmlData = templateTitle
    val kdeHorizonXmlData = templateHorizon
    val kdeProgressXmlData = templateProgress
    val kdeWatermarkXmlData = templateWatermark

    val fileIsKaraoke = listOf(false, true)

    kdeLogoPath = "${song.settings.rootFolder}/Logo.png"
    kdeMicrophonePath = "${song.settings.rootFolder}/Microphone.png"

    val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / song.settings.songName.length),80)

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
          <property name="kdenlive:id">$idProducerSongText</property>
          <property name="force_reload">0</property>
          <property name="meta.media.width">$FRAME_WIDTH_PX</property>
          <property name="meta.media.height">$workAreaHeightPx</property>
         </producer>
         <producer id="producer_horizon" in="$kdeIn" out="$kdeOut">
          <property name="length">$kdeLength</property>
          <property name="eof">pause</property>
          <property name="resource"/>
          <property name="progressive">1</property>
          <property name="aspect_ratio">1</property>
          <property name="seekable">1</property>
          <property name="mlt_service">kdenlivetitle</property>
          <property name="kdenlive:duration">$kdeOut</property>
          <property name="kdenlive:clipname">Горизонт</property>
          <property name="xmldata">$kdeHorizonXmlData</property>
          <property name="kdenlive:folderid">-1</property>
          <property name="kdenlive:clip_type">2</property>
          <property name="kdenlive:id">$idProducerHorizon</property>
          <property name="force_reload">0</property>
          <property name="meta.media.width">$FRAME_WIDTH_PX</property>
          <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
         </producer>
         <producer id="producer_watermark" in="$kdeIn" out="$kdeOut">
          <property name="length">$kdeLength</property>
          <property name="eof">pause</property>
          <property name="resource"/>
          <property name="progressive">1</property>
          <property name="aspect_ratio">1</property>
          <property name="seekable">1</property>
          <property name="mlt_service">kdenlivetitle</property>
          <property name="kdenlive:duration">$kdeOut</property>
          <property name="kdenlive:clipname">Watermark</property>
          <property name="xmldata">$kdeWatermarkXmlData</property>
          <property name="kdenlive:folderid">-1</property>
          <property name="kdenlive:clip_type">2</property>
          <property name="kdenlive:id">$idProducerWatermark</property>
          <property name="force_reload">0</property>
          <property name="meta.media.width">$FRAME_WIDTH_PX</property>
          <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
         </producer>
         <producer id="producer_progress" in="$kdeIn" out="$kdeOut">
         <filter id="karaoke_text">
            <property name="rotate_center">1</property>
            <property name="mlt_service">qtblend</property>
            <property name="kdenlive_id">qtblend</property>
            <property name="rect">$propProgressValue</property>
            <property name="compositing">0</property>
            <property name="distort">0</property>
            <property name="kdenlive:collapsed">0</property>
          </filter>
          <property name="length">$kdeLength</property>
          <property name="eof">pause</property>
          <property name="resource"/>
          <property name="progressive">1</property>
          <property name="aspect_ratio">1</property>
          <property name="seekable">1</property>
          <property name="mlt_service">kdenlivetitle</property>
          <property name="kdenlive:duration">$kdeOut</property>
          <property name="kdenlive:clipname">Прогресс</property>
          <property name="xmldata">$kdeProgressXmlData</property>
          <property name="kdenlive:folderid">-1</property>
          <property name="kdenlive:clip_type">2</property>
          <property name="kdenlive:id">$idProducerProgress</property>
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
          <property name="kdenlive:id">$idProducerOrange</property>
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
          <property name="kdenlive:id">$idProducerLogotype</property>
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
          <position x="96" y="96">
           <transform>1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="233.797" font-color="85,255,255,255">$kdeHeaderAuthor</content>
         </item>
         <item type="QGraphicsTextItem" z-index="6">
          <position x="223" y="201">
           <transform>1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="233.797" font-color="85,255,255,255">$kdeHeaderBpm</content>
         </item>
         <item type="QGraphicsTextItem" z-index="5">
          <position x="96" y="169">
           <transform>1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="359.688" font-color="85,255,255,255">$kdeHeaderTone</content>
         </item>
         <item type="QGraphicsRectItem" z-index="4">
          <position x="0" y="246">
           <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content brushcolor="0,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,$FRAME_WIDTH_PX,50" gradient="#ff000000;#00bf4040;0;100;90"/>
         </item>
         <item type="QGraphicsTextItem" z-index="2">
          <position x="185" y="132">
           <transform>1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="40" font="JetBrains Mono" letter-spacing="0" font-pixel-size="30" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="395.656" font-color="85,255,255,255">$kdeHeaderAlbum</content>
         </item>
         <item type="QGraphicsTextItem" z-index="1">
          <position x="96" y="0">
           <transform>1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content line-spacing="0" shadow="1;#64000000;3;3;3" font-underline="0" box-height="106" font="JetBrains Mono" letter-spacing="0" font-pixel-size="${fontNameSizePt}" font-italic="0" typewriter="0;2;1;0;0" alignment="1" font-weight="50" box-width="818.734" font-color="255,255,127,255">$kdeHeaderSongName</content>
         </item>
         <item type="QGraphicsRectItem" z-index="-1">
          <position x="0" y="0">
           <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content brushcolor="0,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,$FRAME_WIDTH_PX,246"/>
         </item>
         <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
         <background color="0,0,0,0"/>
        </kdenlivetitle>
        </property>
          <property name="kdenlive:folderid">-1</property>
          <property name="kdenlive:clip_type">2</property>
          <property name="kdenlive:id">$idProducerHeader</property>
          <property name="force_reload">0</property>
          <property name="meta.media.width">$FRAME_WIDTH_PX</property>
          <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
         </producer>
         <producer id="producer_background" in="$kdeIn" out="$kdeOut">
          <property name="length">$kdeLength</property>
          <property name="eof">pause</property>
          <property name="resource">${getRandomFile(kdeBackgroundFolderPath, ".png")}</property>
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
          <property name="kdenlive:id">$idProducerBackground</property>
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
          <property name="kdenlive:id">$idProducerMicrophone</property>
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
          <property name="kdenlive:id">$idProducerCounter4</property>
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
          <property name="kdenlive:id">$idProducerCounter3</property>
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
          <property name="kdenlive:id">$idProducerCounter2</property>
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
          <property name="kdenlive:id">$idProducerCounter1</property>
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
          <property name="kdenlive:id">$idProducerCounter0</property>
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
          <property name="kdenlive:id">$idProducerAudioSong</property>
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
          <property name="kdenlive:id">$idProducerAudioMusic</property>
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
          <property name="kdenlive:id">$idProducerAudioVocal</property>
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
          <position x="860" y="180">
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
          <property name="kdenlive:id">$idProducerBeat1</property>
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
          <position x="910" y="180">
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
          <property name="kdenlive:id">$idProducerBeat2</property>
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
          <position x="1010" y="180">
           <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
          </position>
          <content brushcolor="0,255,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,50,50"/>
         </item>
         <item type="QGraphicsRectItem" z-index="3">
          <position x="960" y="180">
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
          <property name="kdenlive:id">$idProducerBeat3</property>
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
          <position x="1010" y="180">
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
          <property name="kdenlive:id">$idProducerBeat4</property>
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
          <entry producer="producer_horizon" in="$kdeIn" out="$kdeOut"/>
          <entry producer="producer_watermark" in="$kdeIn" out="$kdeOut"/>
          <entry producer="producer_progress" in="$kdeIn" out="$kdeOut"/>
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
          <property name="kdenlive:id">$idProducerAudioVocal</property>
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
           <property name="kdenlive:id">$idProducerAudioVocal</property>
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
          <property name="kdenlive:id">$idProducerAudioMusic</property>
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
           <property name="kdenlive:id">$idProducerAudioMusic</property>
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
          <property name="kdenlive:id">$idProducerAudioSong</property>
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
           <property name="kdenlive:id">$idProducerAudioSong</property>
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
           <property name="kdenlive:id">$idProducerBackground</property>
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
           <property name="kdenlive:id">$idProducerMicrophone</property>
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
         <playlist id="playlist_horizon_file">
          <entry producer="producer_horizon" in="$kdeIn" out="$kdeOut">
           <property name="kdenlive:id">$idProducerHorizon</property>
           <filter id="karaoke_horizon">
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
         <playlist id="playlist_horizon_track"/>
         <tractor id="tractor_horizon" in="$kdeIn" out="$kdeOut">
          <property name="kdenlive:trackheight">69</property>
          <property name="kdenlive:timeline_active">1</property>
          <property name="kdenlive:collapsed">28</property>
          <property name="kdenlive:track_name">Горизонт</property>
          <property name="kdenlive:thumbs_format"/>
          <property name="kdenlive:audio_rec"/>
          <track hide="audio" producer="playlist_horizon_file"/>
          <track hide="audio" producer="playlist_horizon_track"/>
         </tractor>
         <playlist id="playlist_watermark_file">
          <entry producer="producer_watermark" in="$kdeIn" out="$kdeOut">
           <property name="kdenlive:id">$idProducerWatermark</property>
           <filter id="karaoke_watermark">
            <property name="rotate_center">1</property>
            <property name="mlt_service">qtblend</property>
            <property name="kdenlive_id">qtblend</property>
            <property name="rect">$kdeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000;$kdeFadeIn=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.500000;$kdeFadeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.500000;$kdeOut=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.000000</property>
            <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
            <property name="compositing">0</property>
            <property name="distort">0</property>
            <property name="kdenlive:collapsed">0</property>
           </filter>
          </entry>
         </playlist>
         <playlist id="playlist_watermark_track"/>
         <tractor id="tractor_watermark" in="$kdeIn" out="$kdeOut">
          <property name="kdenlive:trackheight">69</property>
          <property name="kdenlive:timeline_active">1</property>
          <property name="kdenlive:collapsed">28</property>
          <property name="kdenlive:track_name">Watermark</property>
          <property name="kdenlive:thumbs_format"/>
          <property name="kdenlive:audio_rec"/>
          <track hide="audio" producer="playlist_watermark_file"/>
          <track hide="audio" producer="playlist_watermark_track"/>
         </tractor>
         <playlist id="playlist_progress_file">
          <entry producer="producer_progress" in="$kdeIn" out="$kdeOut">
           <property name="kdenlive:id">$idProducerProgress</property>
           <filter id="karaoke_progress">
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
         <playlist id="playlist_progress_track"/>
         <tractor id="tractor_progress" in="$kdeIn" out="$kdeOut">
          <property name="kdenlive:trackheight">69</property>
          <property name="kdenlive:timeline_active">1</property>
          <property name="kdenlive:collapsed">28</property>
          <property name="kdenlive:track_name">Прогресс</property>
          <property name="kdenlive:thumbs_format"/>
          <property name="kdenlive:audio_rec"/>
          <track hide="audio" producer="playlist_progress_file"/>
          <track hide="audio" producer="playlist_progress_track"/>
         </tractor>
         <playlist id="playlist_fill_odd_file">
          <entry producer="producer_orange" in="$kdeIn" out="$kdeOut">
           <property name="kdenlive:id">$idProducerOrange</property>
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
           <property name="kdenlive:id">$idProducerOrange</property>
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
           <property name="kdenlive:id">$idProducerSongText</property>
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
           <property name="kdenlive:id">$idProducerHeader</property>
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
           <property name="kdenlive:id">$idProducerLogotype</property>
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
           <property name="kdenlive:id">$idProducerCounter4</property>
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
           <property name="kdenlive:id">$idProducerCounter3</property>
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
           <property name="kdenlive:id">$idProducerCounter2</property>
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
           <property name="kdenlive:id">$idProducerCounter1</property>
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
           <property name="kdenlive:id">$idProducerCounter0</property>
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
           <property name="kdenlive:id">$idProducerBeat4</property>
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
           <property name="kdenlive:id">$idProducerBeat3</property>
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
           <property name="kdenlive:id">$idProducerBeat2</property>
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
           <property name="kdenlive:id">$idProducerBeat1</property>
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
          <track producer="tractor_horizon"/>
          <track producer="tractor_progress"/>
          <track producer="tractor_fill_odd"/>
          <track producer="tractor_fill_even"/>
          <track producer="tractor_textsong"/>
          <track producer="tractor_header"/>
          <track producer="tractor_logo"/>
          <track producer="tractor_beat4"/>
          <track producer="tractor_beat3"/>
          <track producer="tractor_beat2"/>
          <track producer="tractor_beat1"/>          
          <track producer="tractor_counter4"/>
          <track producer="tractor_counter3"/>
          <track producer="tractor_counter2"/>
          <track producer="tractor_counter1"/>
          <track producer="tractor_counter0"/>
          <track producer="tractor_watermark"/>
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
        File(fileSubtitleName).writeText(song.srtFileBody)
    }

}


fun getSong(settings: Settings): Song {
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
                    "BEAT" -> beatTimecode = startEnd!!.split(" --> ")[0].replace(",",".")
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

    // Создаем объект classes.Song
    val result = Song()
    result.settings = settings
    // Устанавливаем end равный end последнего объекта из списка и найденные выше настройки (если они были)
    result.endTimecode = subtitles.last().endTimecode
    result.beatTimecode = beatTimecode
    result.srtFileBody = body

    // В его объект Subtitles кладём список объектов classes.Subtitle
    result.subtitles = subtitles

    // Возвращаем объект classes.Song
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
import java.io.File
import java.lang.Long.max
import java.lang.Long.min

class Lyric {
    var items: List<LyricLine> = emptyList()
    var fontSize: Long = KLT_ITEM_CONTENT_FONT_PIXEL_SIZE
    var horizontPosition: Long = KLT_ITEM_CONTENT_FONT_PIXEL_SIZE / 2
    var symbolWidth: Long = KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH
    var symbolHeight: Long = KLT_ITEM_CONTENT_FONT_SYMBOL_HEIGHT
    companion object {
        fun getLiric(subtitles: Subtitles): Lyric {

            val fileName = "src/main/resources/lyrics.txt"
            val fileNameKdeTitile = "src/main/resources/lyrics.kdenlivetitle"
            val fileNameKdeHorizont = "src/main/resources/horyzont.kdenlivetitle"
            val fileNameKdeTransformPropertyTitle = "src/main/resources/transform_property_title.txt"
            val fileNameKdeTransformPropertyFillOdd = "src/main/resources/transform_property_fill_odd.txt"
            val fileNameKdeTransformPropertyFillEven = "src/main/resources/transform_property_fill_even.txt"
            var startLine: String? = null
            var endLine: String? = null

            var subs: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()
            val lyrics: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()
            val result: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()

            var text = ""
            var line = ""
            var maxLineDuration = 0L

            // Проходимся по всем сабам
            subtitles.items.forEach { subtitle ->
                // Если саб - начало строки - начинаем новую строку (пока она пустая) и инициализируем пустой список subs
                if (subtitle.isLineStart == true) {
                    startLine = subtitle.start
                    line = ""
                    subs = emptyList<Subtitle>().toMutableList()
                }
                // Дописываем в текст текущей строки текст из саба
                line += subtitle.text

                // Добавляем текущий саб к списку subs
                subs.add(subtitle)

                // Если саб - конец строки
                if (subtitle.isLineEnd == true) {

                    // Устанавливаем конец строки позицией конца из саба
                    endLine = subtitle.end

                    // Создаем объект Subtitles
                    val s = Subtitles()
                    // В его items прописываем список subs - это все сабы текущей строки
                    s.items = subs

                    // Создаем объект LyricLine и инициализируем его переменными
                    // на данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми
                    val liric = LyricLine(text = line, start = startLine, end = endLine, subtitles = s, startTp = null, endTp = null)

                    // Находим время "звучания" строки в миллисекундах
                    val lineDuration = getDurationInMilliseconds(startLine!!, endLine!!)

                    // Находим максимальное время "звучания" среди всех строк
                    maxLineDuration = max(maxLineDuration, lineDuration)

                    // Добавляем строку liric в список строк lyrics
                    lyrics.add(liric)

                }
            }

            // Устанавливаем текущую позицию конца в ноль
            var currentPositionEnd = 0L

            // Походимся по массиву строк
            lyrics.forEach { lyric ->

                // Вычисляем время "тишины" - от конца текущей позиции до начала текущей строки
                val silentDuration = convertTimecodeToMilliseconds(lyric.start!!) - currentPositionEnd

                // Вычисляем кол-во "пустых" строк, которые надо вставить перед текущей строкой
                // Оно равно времени "тишины" деленному на время "звучания" самой длинной строки, которое мы нашли ранее
                val linesToInsert: Long = silentDuration / maxLineDuration

                // Если количество вставляемых пустых строк больше нуля - начинаем их вставлять
                if (linesToInsert > 0) {

                    // Вычисляем "длительность" вставляемой пустой строки
                    // Она равна времени тишины деленное на количество вставляемых строк
                    val silentLineDuration: Long =  silentDuration / linesToInsert

                    // Цикл от 1 до количества вставляемых строк включительно
                    for (i in 1..linesToInsert) {

                        // Время начала строки = текущая позиция конца + 1/10 от длины строки
                        val startDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration/10)

                        // Время конца строки = текущая позиция конца + длина строки
                        val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration)

                        // Создаем объект Subtitle с пустым текстом, с вычисленными выше началом и концом
                        // И помечаем его и как начало и как конец строки
                        val subtitleEmpty = Subtitle(text = "", start = startDuration, end = endDuration, isLineStart = true, isLineEnd = true)

                        // Создаем объект Subtitles, в его items помещаем единственный "пустой" Subtitle, созданный шагом раньше
                        // на данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми
                        val s = Subtitles()
                        s.items = listOf(subtitleEmpty)
                        val lyricEmpty = LyricLine(text = "", start = startDuration, end = endDuration, subtitles = s, startTp = null, endTp = null)

                        // Устанавливаем текущую позицию конца равной позиции конца созданной пустой строки
                        currentPositionEnd += silentLineDuration

                        // Добавляем пустую строку lyricEmpty в список строк result
                        result.add(lyricEmpty)

                        text += "\n"
                    }
                }

                // Добавляем строку lyric в список строк result
                // К этому моменту в список уже добавлено нужное количество пустых строк перед текущей
                result.add(lyric)

                // Устанавливаем текущую позицию конца равной позиции конца текущей строки
                currentPositionEnd = convertTimecodeToMilliseconds(lyric.end!!)

                text += "${lyric.text}\n"
            }

            // Мы добавили в result все строки, но у нас еще остался "хвост тишины", для которого тоже надо добавить пустые строки

            // Вычисляем время "тишины" - от конца текущей позиции до конца титров
            val silentDuration = convertTimecodeToMilliseconds(subtitles.end!!) - currentPositionEnd

            // Вычисляем кол-во "пустых" строк, которые надо вставить после последней строки
            // Оно равно времени "тишины" деленному на время "звучания" самой длинной строки, которое мы нашли ранее
            val linesToInsert: Long = silentDuration / maxLineDuration

            // Если количество вставляемых пустых строк больше нуля - начинаем их вставлять
            if (linesToInsert > 0) {

                // Вычисляем "длительность" вставляемой пустой строки
                // Она равна времени тишины деленное на количество вставляемых строк
                val silentLineDuration: Long =  silentDuration / linesToInsert

                // Цикл от 1 до количества вставляемых строк включительно
                for (i in 1..linesToInsert) {

                    // Время начала строки = текущая позиция конца + 1/10 от длины строки
                    val startDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration/10)

                    // Время конца строки = текущая позиция конца + длина строки
                    val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration)

                    // Создаем объект Subtitle с пустым текстом, с вычисленными выше началом и концом
                    // И помечаем его и как начало и как конец строки
                    val subtitleEmpty = Subtitle(text = "", start = startDuration, end = endDuration, isLineStart = true, isLineEnd = true)

                    // Создаем объект Subtitles, в его items помещаем единственный "пустой" Subtitle, созданный шагом раньше
                    // на данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми
                    val s = Subtitles()
                    s.items = listOf(subtitleEmpty)
                    val lyricEmpty = LyricLine(text = "", start = startDuration, end = endDuration, subtitles = s, startTp = null, endTp = null)

                    // Устанавливаем текущую позицию конца равной позиции конца созданной пустой строки
                    currentPositionEnd += silentLineDuration

                    // Добавляем пустую строку lyricEmpty в список строк result
                    result.add(lyricEmpty)

                    text += "\n"
                }
            }

            // Теперь в списке result у нас нужное количество строк - как полных, так и пустых. И в начале, и в середине, и в конце

            println(text)

            var resultLyric = Lyric()
            resultLyric.items = result

            val maxTextWidth = FRAME_WIDTH - KLT_ITEM_CONTENT_TITLE_POSITION_START_X * 2
            val maxTextLength = resultLyric.items.maxBy { it.text!!.length }.text!!.length
            val maxSymbolWidth = maxTextWidth / maxTextLength
            val maxFontSize = min((maxSymbolWidth * KLT_ITEM_CONTENT_FONT_PIXEL_SIZE) / KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH, KLT_ITEM_CONTENT_FONT_PIXEL_SIZE)
            val currentFontSymbolHeightDouble = (maxFontSize.toDouble() * KLT_ITEM_CONTENT_FONT_SYMBOL_HEIGHT / KLT_ITEM_CONTENT_FONT_PIXEL_SIZE)
            val currentFontSymbolWidthDouble = (maxFontSize.toDouble() * KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH / KLT_ITEM_CONTENT_FONT_PIXEL_SIZE)
            val currentFontSymbolHeight: Long = Math.round(currentFontSymbolHeightDouble)
            val currentFontSymbolWidth: Long = Math.round(currentFontSymbolWidthDouble)

            val boxHeight: Long = ((resultLyric.items.size-1) * currentFontSymbolHeight)
            val boxWidth: Long = (maxTextLength * currentFontSymbolWidth)

            val templateTitle = """
<kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH" height="$boxHeight" out="0">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="$KLT_ITEM_CONTENT_TITLE_POSITION_START_X" y="$KLT_ITEM_CONTENT_TITLE_POSITION_START_Y">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content 
        line-spacing="$KLT_ITEM_CONTENT_LINE_SPACING" 
        shadow="$KLT_ITEM_CONTENT_SHADOW" 
        font-underline="$KLT_ITEM_CONTENT_FONT_UNDERLINE" 
        box-height="$boxHeight" 
        font="$KLT_ITEM_CONTENT_FONT_NAME" 
        letter-spacing="0" 
        font-pixel-size="$maxFontSize" 
        font-italic="$KLT_ITEM_CONTENT_FONT_ITALIC" 
        typewriter="$KLT_ITEM_CONTENT_TYPEWRITER" 
        alignment="$KLT_ITEM_CONTENT_ALIGNMENT" 
        font-weight="$KLT_ITEM_CONTENT_FONT_WEIGHT" 
        box-width="$boxWidth" 
        font-color="$KLT_ITEM_CONTENT_FONT_COLOR"
        >$text<
        /content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH,$boxHeight"/>
 <endviewport rect="0,0,$FRAME_WIDTH,$boxHeight"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>"""

            val horizontPosition = (FRAME_HEIGHT / 2 + currentFontSymbolHeight / 2) - HORIZONT_OFFSET


            val templateHorizont = """
<kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH" height="$FRAME_HEIGHT" out="0">
 <item type="QGraphicsRectItem" z-index="0">
  <position x="0" y="$horizontPosition">
   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content brushcolor="255,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,1920,3"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH,$FRAME_HEIGHT"/>
 <endviewport rect="0,0,$FRAME_WIDTH,$FRAME_HEIGHT"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>"""




            resultLyric.items.forEachIndexed { index, lyricLine ->
                val startTp = TransformProperty(
                    time = lyricLine.start,
                    x = 0,
                    y = horizontPosition - ((index + 1)*(currentFontSymbolHeightDouble+HEIGHT_CORRECTION)).toLong(),
                    w = FRAME_WIDTH,
                    h = boxHeight,
                    opacity = 1.0
                )

                val endTp = TransformProperty(
                    time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(lyricLine.end!!)-(1000/FRAME_FPS+1)),
                    x = 0,
                    y = horizontPosition - ((index + 1)*(currentFontSymbolHeightDouble+HEIGHT_CORRECTION)).toLong(),
                    w = FRAME_WIDTH,
                    h = boxHeight,
                    opacity = 1.0
                )
                lyricLine.startTp = startTp
                lyricLine.endTp = endTp
            }

            var propRectLineValue = emptyList<String>().toMutableList()
            var propRectTitleValueLineOdd = emptyList<String>().toMutableList()
            var propRectTitleValueLineEven = emptyList<String>().toMutableList()
            for (i in 0..resultLyric.items.size-2) {
                val currentLyricLine = resultLyric.items[i]
                val nextLyricLine = resultLyric.items[i+1]
                val diffInMills = convertTimecodeToMilliseconds(nextLyricLine.start!!) - convertTimecodeToMilliseconds(currentLyricLine.end!!)
                if (diffInMills < 200) {
                    currentLyricLine.end = nextLyricLine.start
                    currentLyricLine.subtitles?.items?.last()?.end = currentLyricLine.end
                }

                propRectLineValue.add("${currentLyricLine.startTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")
                if (currentLyricLine.text != "") {

                    var ww = 1L

                    val currentSubtitle = currentLyricLine.subtitles!!.items[0]
                    val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currentSubtitle.start!!)-(1000/FRAME_FPS+1))
                    val y = horizontPosition - currentFontSymbolHeight + HORIZONT_OFFSET
                    var propRectTitleValueFade = "${startTime}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.0"
                    if (i%2 == 0) {
                        propRectTitleValueLineOdd.add(propRectTitleValueFade)
                    } else {
                        propRectTitleValueLineEven.add(propRectTitleValueFade)
                    }
                    ww = -KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                    for (j in 0..(currentLyricLine.subtitles!!.items.size) - 2) {
                        val currentSubtitle = currentLyricLine.subtitles.items[j]
                        val nextSubtitle = currentLyricLine.subtitles.items[j+1]
                        val y = horizontPosition - currentFontSymbolHeight + HORIZONT_OFFSET
                        val w = currentSubtitle.text!!.length * currentFontSymbolWidth
                        var propRectTitleValueStart = "${currentSubtitle.start}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"
                        ww += w
                        var propRectTitleValueEnd = "${currentSubtitle.end}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueStart) propRectTitleValueLineOdd.add(propRectTitleValueStart)
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueEnd) propRectTitleValueLineOdd.add(propRectTitleValueEnd)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueStart) propRectTitleValueLineEven.add(propRectTitleValueStart)
                            if (propRectTitleValueLineEven.last() != propRectTitleValueEnd) propRectTitleValueLineEven.add(propRectTitleValueEnd)
                        }
                    }

                    if (diffInMills < 200) {

                        propRectLineValue.add("${currentLyricLine.subtitles?.items?.last()?.start}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

                        val currentSubtitle = currentLyricLine.subtitles.items[(currentLyricLine.subtitles!!.items.size)-1]
                        var nextSubtitle = nextLyricLine.subtitles!!.items[0]

                        var y = horizontPosition - currentFontSymbolHeight + HORIZONT_OFFSET

                        val w = currentSubtitle.text!!.length * currentFontSymbolWidth
                        var propRectTitleValueStart = "${currentSubtitle.start}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"
                        ww += w
                        y -= currentFontSymbolHeight
                        var propRectTitleValueEnd = "${currentSubtitle.end}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"

                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueStart) propRectTitleValueLineOdd.add(propRectTitleValueStart)
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueEnd) propRectTitleValueLineOdd.add(propRectTitleValueEnd)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueStart) propRectTitleValueLineEven.add(propRectTitleValueStart)
                            if (propRectTitleValueLineEven.last() != propRectTitleValueEnd) propRectTitleValueLineEven.add(propRectTitleValueEnd)
                        }

                        var propRectTitleValueFade = "${nextSubtitle.start}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueFade) propRectTitleValueLineOdd.add(propRectTitleValueFade)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueFade) propRectTitleValueLineEven.add(propRectTitleValueFade)
                        }

                        propRectTitleValueFade = "${nextSubtitle.end}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.0"
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueFade) propRectTitleValueLineOdd.add(propRectTitleValueFade)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueFade) propRectTitleValueLineEven.add(propRectTitleValueFade)
                        }

                    } else {
                        propRectLineValue.add("${currentLyricLine.endTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

                        val currentSubtitle = currentLyricLine.subtitles.items[(currentLyricLine.subtitles!!.items.size)-1]
                        val nextSubtitle = nextLyricLine.subtitles!!.items[0]

                        var y = horizontPosition - currentFontSymbolHeight + HORIZONT_OFFSET
                        val w = currentSubtitle.text!!.length * currentFontSymbolWidth
                        var propRectTitleValueStart = "${currentSubtitle.start}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"
                        ww += w
                        var propRectTitleValueEnd = "${currentSubtitle.end}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.6"

                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueStart) propRectTitleValueLineOdd.add(propRectTitleValueStart)
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueEnd) propRectTitleValueLineOdd.add(propRectTitleValueEnd)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueStart) propRectTitleValueLineEven.add(propRectTitleValueStart)
                            if (propRectTitleValueLineEven.last() != propRectTitleValueEnd) propRectTitleValueLineEven.add(propRectTitleValueEnd)
                        }

                        y -= currentFontSymbolHeight
                        var propRectTitleValueFade = "${nextSubtitle.start}=${KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X} ${y} ${ww} ${currentFontSymbolHeight} 0.0"
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueFade) propRectTitleValueLineOdd.add(propRectTitleValueFade)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueFade) propRectTitleValueLineEven.add(propRectTitleValueFade)
                        }
                    }
                }
            }



//            val propRectValue = resultLyric.items.map { "${it.startTp?.time}=${it.startTp?.x} ${it.startTp?.y} ${it.startTp?.w} ${it.startTp?.h} ${it.startTp?.opacity}${if (it.text == "") "" else ";${it.endTp?.time}=${it.startTp?.x} ${it.startTp?.y} ${it.startTp?.w} ${it.startTp?.h} ${it.startTp?.opacity}"}"}.joinToString(";")
            val propRectValue = propRectLineValue.joinToString(";")
            val propFillOddValue = propRectTitleValueLineOdd.joinToString(";")
            val propFillEvenValue = propRectTitleValueLineEven.joinToString(";")
            val propText = """<property name="rect">$propRectValue</property>""".replace(",",".")
            val propTextFillOdd = """<property name="rect">$propFillOddValue</property>""".replace(",",".")
            val propTextFillEven = """<property name="rect">$propFillEvenValue</property>""".replace(",",".")

            File(fileName).writeText(text)
            File(fileNameKdeTitile).writeText(templateTitle)
            File(fileNameKdeHorizont).writeText(templateHorizont)
            File(fileNameKdeTransformPropertyTitle).writeText(propText)
            File(fileNameKdeTransformPropertyFillOdd).writeText(propTextFillOdd)
            File(fileNameKdeTransformPropertyFillEven).writeText(propTextFillEven)

            resultLyric.fontSize = maxFontSize
            resultLyric.horizontPosition = horizontPosition
            resultLyric.symbolHeight = currentFontSymbolHeight
            resultLyric.symbolWidth = currentFontSymbolWidth

            println(resultLyric.fontSize)
            println(resultLyric.horizontPosition)
            println(resultLyric.symbolWidth)
            println(resultLyric.symbolHeight)

            return resultLyric
        }
    }
}

data class TransformProperty(
    val time: String? = null,
    val x: Long? = null,
    val y: Long? = null,
    val w: Long? = null,
    val h: Long? = null,
    val opacity: Double? = null
)

data class LyricLine(
    val text: String? = null,
    var start: String? = null,
    var end: String? = null,
    val subtitles: Subtitles? = null,
    var startTp: TransformProperty? = null,
    var endTp: TransformProperty? = null
)
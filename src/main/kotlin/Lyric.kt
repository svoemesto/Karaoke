import java.io.File
import java.lang.Double.min
import java.lang.Long.max

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
    val subtitles: List<Subtitle> = emptyList(),
    var startTp: TransformProperty? = null,
    var endTp: TransformProperty? = null,
    var isEmptyLine: Boolean = false,
    var isNeedCounter: Boolean = false,
    var isFadeLine: Boolean = false
)

class Lyric {
    var items: List<LyricLine> = emptyList()
    var fontSize: Long = KLT_ITEM_CONTENT_FONT_SIZE_PT
    var horizontPosition = FRAME_HEIGHT_PX / 2
    var symbolWidth = fontSize * PT_TO_PX
    var symbolHeight: Long = (fontSize * FONT_SYMBOL_WIDTH_ASPECT).toLong()
    companion object {
        fun getLiric(song: Song): Lyric {

            val fileName = "src/main/resources/lyrics.txt"
            val fileNameKdeTitile = "src/main/resources/lyrics.kdenlivetitle"
            val fileNameKdeHorizont = "src/main/resources/horyzont.kdenlivetitle"
            val fileNameKdeTransformPropertyTitle = "src/main/resources/transform_property_title.txt"
            val fileNameKdeTransformPropertyFillOdd = "src/main/resources/transform_property_fill_odd.txt"
            val fileNameKdeTransformPropertyFillEven = "src/main/resources/transform_property_fill_even.txt"
            val fileNameKdeTransformPropertyCounter0 = "src/main/resources/transform_property_counter_0.txt"
            val fileNameKdeTransformPropertyCounter1 = "src/main/resources/transform_property_counter_1.txt"
            val fileNameKdeTransformPropertyCounter2 = "src/main/resources/transform_property_counter_2.txt"
            val fileNameKdeTransformPropertyCounter3 = "src/main/resources/transform_property_counter_3.txt"
            val fileNameKdeTransformPropertyCounter4 = "src/main/resources/transform_property_counter_4.txt"
            var startLine: String? = null
            var endLine: String? = null

            var subs: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()
            val lyrics: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()
            val result: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()

            val counter0list: MutableList<String> = emptyList<String>().toMutableList()
            val counter1list: MutableList<String> = emptyList<String>().toMutableList()
            val counter2list: MutableList<String> = emptyList<String>().toMutableList()
            val counter3list: MutableList<String> = emptyList<String>().toMutableList()
            val counter4list: MutableList<String> = emptyList<String>().toMutableList()

            var text = ""
            var line = ""
            var maxLineDuration = 0L

            // Проходимся по всем сабам
            song.subtitles.forEach { subtitle ->
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

                    // Создаем объект LyricLine и инициализируем его переменными
                    // на данный момент нам пока неизвестны поля startTp и endTp - оставляем их пустыми
                    val liric = LyricLine(text = line, start = startLine, end = endLine, subtitles = subs, startTp = null, endTp = null)

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
                        val lyricEmpty = LyricLine(text = "", start = startDuration, end = endDuration, subtitles = listOf(subtitleEmpty), startTp = null, endTp = null, isEmptyLine = true, isFadeLine = result.isEmpty())

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
            val silentDuration = convertTimecodeToMilliseconds(song.end!!) - currentPositionEnd

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
                    val lyricEmpty = LyricLine(text = "", start = startDuration, end = endDuration, subtitles = listOf(subtitleEmpty), startTp = null, endTp = null, isEmptyLine = true, isFadeLine = true)

                    // Устанавливаем текущую позицию конца равной позиции конца созданной пустой строки
                    currentPositionEnd += silentLineDuration

                    // Добавляем пустую строку lyricEmpty в список строк result
                    result.add(lyricEmpty)

                    text += "\n"
                }
            }
//            println(text)

            // Теперь в списке result у нас нужное количество строк - как полных, так и пустых. И в начале, и в середине, и в конце
            // Создаём объект Lyric и в его items помещаем этот список
            val resultLyric = Lyric()
            resultLyric.items = result

            // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
            val maxTextWidthPx = FRAME_WIDTH_PX.toDouble() - TITLE_POSITION_START_X_PX * 2
            println("Максимальная ширина текста: $maxTextWidthPx")
            // maxTextLength - максимальная длина текста (в символах) = длине символов самой длинной строки
            val maxTextLengthSym = resultLyric.items.maxBy { it.text!!.length }.text!!.length
            println("Максимальная длина текста (в символах): $maxTextLengthSym")

            // maxSymbolWidth - максимальная ширина символа = максимальная ширина текста делённая на максимальную длину
            val maxSymbolWidthPx = maxTextWidthPx / maxTextLengthSym
            println("Максимальная ширина символа: $maxSymbolWidthPx")

            val maxSymbolHeightPx = maxSymbolWidthPx / FONT_SYMBOL_WIDTH_ASPECT
            println("Максимальная высота символа: $maxSymbolHeightPx")

            // maxFontSize - максимальный размер шрифта = максимальная ширина символа * KLT_ITEM_CONTENT_FONT_PIXEL_SIZE / KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH
            // но не больше KLT_ITEM_CONTENT_FONT_PIXEL_SIZE

            // Высота символа в пикселах
            val currentSymbolHeightPx = KLT_ITEM_CONTENT_FONT_SIZE_PT * PT_TO_PX
            println("Высота символа в пикселах по умлочанию: $currentSymbolHeightPx")

            // Ширина символа в пикселах
            val currentSymbolWidthPx = currentSymbolHeightPx * FONT_SYMBOL_WIDTH_ASPECT
            println("Ширина символа в пикселах по умлочанию: $currentSymbolWidthPx")

            var maxFontSizePx = min(maxSymbolHeightPx, currentSymbolHeightPx).toLong()
            println("Расчётная высота символа в пикселах: $maxFontSizePx")

            val maxFontSizePt = (maxFontSizePx / PT_TO_PX).toLong()
            println("Расчётный размер шрифта в пунктах: $maxFontSizePt")

            maxFontSizePx = POINT_TO_PIXEL[maxFontSizePt.toInt()].toLong()
            println("Расчётная высота символа в пикселах после расчета размера шрифта: $maxFontSizePx")

            // currentFontSymbolHeightDouble - текущая высота символа (дробное)
            val currentFontSymbolHeightPx = maxFontSizePx
            println("Текущая высота символа в пикселах: $currentFontSymbolHeightPx")

            // currentFontSymbolHeightDouble - текущая ширина символа (дробное)
            val currentFontSymbolWidthPx = maxFontSizePx * FONT_SYMBOL_WIDTH_ASPECT
            println("Текущая ширина символа в пикселах: $currentFontSymbolWidthPx")

            // boxHeight - высота "бокса" текста = количество строк текста * высоту символа
            val boxHeightPx = ((resultLyric.items.size + 1) * currentFontSymbolHeightPx.toLong())
            println("Высота бокса в пикселах: $boxHeightPx")
            println("Количество строк: ${resultLyric.items.size}")

            // boxHeight - ширина "бокса" текста = ширина текста * ширину символа
            val boxWidthPx = (maxTextLengthSym * currentFontSymbolWidthPx)
            println("Ширина бокса в пикселах: $boxWidthPx")

            // Высота рабочей области
            val workAreaHeightPx = ((boxHeightPx / 1080) + 1) * 1080

            // Шаблон для файла субтитра текста
            val templateTitle = """<kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="${workAreaHeightPx}" out="0">
 <item type="QGraphicsTextItem" z-index="0">
  <position x="$TITLE_POSITION_START_X_PX" y="$TITLE_POSITION_START_Y_PX">
   <transform>1,0,0,0,1,0,0,0,1</transform>
  </position>
  <content line-spacing="$LINE_SPACING" shadow="$SHADOW" font-underline="$FONT_UNDERLINE" box-height="${boxHeightPx.toLong()}" font="$FONT_NAME" letter-spacing="0" font-pixel-size="${maxFontSizePt.toLong()}" font-italic="$FONT_ITALIC" typewriter="$TYPEWRITER" alignment="$ALIGNMENT" font-weight="$FONT_WEIGHT" box-width="$boxWidthPx" font-color="$FONT_COLOR">$text</content>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,${workAreaHeightPx}"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,${workAreaHeightPx}"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>"""

            // horizontPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет
            val horizontPositionPx = (FRAME_HEIGHT_PX / 2 + currentFontSymbolHeightPx / 2) - HORIZONT_OFFSET_PX
            println("Позиция горизонта в пикселах: $horizontPositionPx")

            // Шаблон для файла субтитра "горизонта"
            val templateHorizont = """
                <kdenlivetitle duration="0" LC_NUMERIC="C" width="$FRAME_WIDTH_PX" height="$FRAME_HEIGHT_PX" out="0">
                 <item type="QGraphicsRectItem" z-index="0">
                  <position x="0" y="${horizontPositionPx.toLong()}">
                   <transform zoom="100">1,0,0,0,1,0,0,0,1</transform>
                  </position>
                  <content brushcolor="255,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,1920,3"/>
                 </item>
                 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
                 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
                 <background color="0,0,0,0"/>
                </kdenlivetitle>"""

            // Настало время прописать TransformProperty для строк
            // Проходимся по всем строкам
            resultLyric.items.forEachIndexed { index, lyricLine ->

                // TransformProperty для точки начала линии
                // time = время начала строки
                // x = 0
                // y = позиция горизонта - текущий номер строки * (высота символа + коррекция)
                // w = ширина экрана
                // h = высота бокса текста
                // непрозрачность полная
                val startTp = TransformProperty(
                    time = lyricLine.start,
                    x = 0,
                    y = horizontPositionPx - ((index + 1)*(currentFontSymbolHeightPx +HEIGHT_CORRECTION)).toLong(),
                    w = FRAME_WIDTH_PX,
                    h = workAreaHeightPx,
                    if(lyricLine.isFadeLine) 0.0 else 1.0
                )

                // TransformProperty для точки конца линии
                // time = время конца строки (с коррекцией на следующую строку)
                // x = 0
                // y = позиция горизонта - текущий номер строки * (высота символа + коррекция)
                // w = ширина экрана
                // h = высота бокса текста
                // непрозрачность полная

                var time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(lyricLine.end!!))
                // Если текущий элемент не последний - надо проверить начало следующего элемента
                // и если оно отличается меньше чем на 200 мс - скорректировать время конца текущей линии
                if (index != resultLyric.items.size-1) {
                    // Находим следующую строку
                    val nextLyricLine = resultLyric.items[index+1]
                    // Находим разницу во времени между текущей строкой и следующей
                    val diffInMills = convertTimecodeToMilliseconds(nextLyricLine.start!!) - convertTimecodeToMilliseconds(lyricLine.end!!)
                    // Если эта разница меньше 200 мс
                    if (diffInMills < 200) {
                        // Сдвигаем конец текущей линии и конец последнего титра в ней до начала следующей
                        lyricLine.end = nextLyricLine.start
                        lyricLine.subtitles.last().end = lyricLine.end
                        // сдвигаем время TransformProperty к начало последнего титра текущей строки
                        time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(lyricLine.subtitles.last().start!!))
                    }
                }

                val endTp = TransformProperty(
                    time = time,
                    x = 0,
                    y = horizontPositionPx - ((index + 1)*(currentFontSymbolHeightPx +HEIGHT_CORRECTION)).toLong(),
                    w = FRAME_WIDTH_PX,
                    h = workAreaHeightPx,
                    opacity = if(lyricLine.isFadeLine) 0.0 else 1.0
                )
                lyricLine.startTp = startTp
                lyricLine.endTp = endTp
            }

            // Список свойств трансформации текста
            val propRectLineValue = emptyList<String>().toMutableList()
            // Список свойств трансформации чётных заливок
            val propRectTitleValueLineOdd = emptyList<String>().toMutableList()
            // Список свойств трансформации нечётных заливок
            val propRectTitleValueLineEven = emptyList<String>().toMutableList()

            // Настало время прописать TransformProperty для заливок
            // Проходимся по строкам - от первой до предпоследней
            for (i in 0 until resultLyric.items.size) {

                // Текущая строка
                val currentLyricLine = resultLyric.items[i]

                //Следующая строка
                val nextLyricLine = if (i < resultLyric.items.size -1 ) resultLyric.items[i+1] else null

                // Разница во времени между текущей строкой и следующей
                val diffInMills = if (nextLyricLine != null) convertTimecodeToMilliseconds(nextLyricLine.start!!) - convertTimecodeToMilliseconds(currentLyricLine.end!!) else 0

                // Свойство трансформации текста текущей строки - из startTp
                propRectLineValue.add("${currentLyricLine.startTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

                // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
                // Если текущая строка не пустая
                if (currentLyricLine.text != "") {

                    // Свойство трансформации текста текущей строки - из endTp
                    propRectLineValue.add("${currentLyricLine.endTp?.time}=${currentLyricLine.endTp?.x} ${currentLyricLine.endTp?.y} ${currentLyricLine.endTp?.w} ${currentLyricLine.endTp?.h} ${currentLyricLine.endTp?.opacity}")

                    // Начальная позиция w для заливки = 1
                    var ww = 1.0

                    // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
                    val currentSubtitle = currentLyricLine.subtitles[0]

                    // Время начала анимации = времени начала этого титра минус 1 фрейм
                    val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currentSubtitle.start!!)-(1000/FRAME_FPS+1))

                    // Координата x всегда одна и та же = KLT_ITEM_CONTENT_TITLE_POSITION_START_X + KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                    val x = TITLE_POSITION_START_X_PX+TITLE_OFFSET_START_X_PX

                    // Координата y = позиция горизонта - высота символа + оффсет
                    val y = horizontPositionPx - currentFontSymbolHeightPx// + HORIZONT_OFFSET_PX

                    // Ширина = начальной позиции (1)
                    val w = ww

                    // Высота = высоте символа
                    val h = currentFontSymbolHeightPx

                    // Свойство трансформации заливки с полной прозрачностью
                    val propRectTitleValueFade = "${startTime}=${x} ${y} ${w.toLong()} ${h} 0.0"

                    // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                    if (i%2 == 0) {
                        propRectTitleValueLineOdd.add(propRectTitleValueFade)
                    } else {
                        propRectTitleValueLineEven.add(propRectTitleValueFade)
                    }

                    // Смещаем стартовую позицию w на величину KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                    ww = -TITLE_OFFSET_START_X_PX.toDouble()

                    // Проходимся по титрам текущей линии от первого до предпоследнего
                    for (j in 0..(currentLyricLine.subtitles.size) - 2) {

                        // Текущий титр
                        val currentSubtitle = currentLyricLine.subtitles[j]

                        // Время - начало текущего титра
                        var time = currentSubtitle.start

                        // Координата x всегда одна и та же = KLT_ITEM_CONTENT_TITLE_POSITION_START_X + KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                        val x = TITLE_POSITION_START_X_PX+TITLE_OFFSET_START_X_PX

                        // Координата y = позиция горизонта - высота символа + оффсет
                        val y = horizontPositionPx - currentFontSymbolHeightPx// + HORIZONT_OFFSET_PX

                        // Ширина = ширина текста тира * ширину символа
                        val w = currentSubtitle.text!!.length * currentFontSymbolWidthPx

                        // Высота = высоте символа
                        val h = currentFontSymbolHeightPx

                        // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                        var propRectTitleValueStart = "${time}=${x.toLong()} ${y.toLong()} ${ww.toLong()} ${h.toLong()} 0.6"

                        // Время - конец текущего титра
                        time = currentSubtitle.end

                        // Ширина = предыдущее значение ширины + ширина
                        ww += w
                        // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                        var propRectTitleValueEnd = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6"

                        // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueStart) propRectTitleValueLineOdd.add(propRectTitleValueStart)
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueEnd) propRectTitleValueLineOdd.add(propRectTitleValueEnd)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueStart) propRectTitleValueLineEven.add(propRectTitleValueStart)
                            if (propRectTitleValueLineEven.last() != propRectTitleValueEnd) propRectTitleValueLineEven.add(propRectTitleValueEnd)
                        }
                    }

                    // На этом этапе мы закрасили все титры линии, кроме последнего

                    // Если между текущей и следующей строкой меньше 200 мс
                    if (diffInMills < 200) {


                        // Текущий титр - последний титр текущей строки
                        val currentSubtitle = currentLyricLine.subtitles[(currentLyricLine.subtitles.size)-1]

                        // Следующий титр - первый титр следующей строки
                        val nextSubtitle = nextLyricLine!!.subtitles[0]

                        // Время - начало текущего титра
                        var time = currentSubtitle.start

                        // Координата x всегда одна и та же = KLT_ITEM_CONTENT_TITLE_POSITION_START_X + KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                        val x = TITLE_POSITION_START_X_PX+TITLE_OFFSET_START_X_PX

                        // Координата y = позиция горизонта - высота символа + оффсет
                        var y = horizontPositionPx - currentFontSymbolHeightPx// + HORIZONT_OFFSET_PX

                        // Ширина = ширина текста тира * ширину символа
                        val w = currentSubtitle.text!!.length * currentFontSymbolWidthPx

                        // Высота = высоте символа
                        val h = currentFontSymbolHeightPx

                        // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                        var propRectTitleValueStart = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6"

                        // Время - конец текущего титра
                        time = currentSubtitle.end

                        // Ширина = предыдущее значение ширины + ширина
                        ww += w

                        // Поднимаем y на высоту символа
                        y -= currentFontSymbolHeightPx

                        // Конец анимации титра - в конечной позиции титра на новом уровне с непрозрачностью 60%
                        var propRectTitleValueEnd = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6"

                        // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueStart) propRectTitleValueLineOdd.add(propRectTitleValueStart)
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueEnd) propRectTitleValueLineOdd.add(propRectTitleValueEnd)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueStart) propRectTitleValueLineEven.add(propRectTitleValueStart)
                            if (propRectTitleValueLineEven.last() != propRectTitleValueEnd) propRectTitleValueLineEven.add(propRectTitleValueEnd)
                        }

                        // На данном этапе залили последний титр строки - пора сделать фэйд

                        // Начало анимации титра - в начальной позиции следующиго титра с теми же координатами и с непрозрачностью 60%
                        var propRectTitleValueFade = "${nextSubtitle.start}=${x} ${y} ${ww.toLong()} ${h} 0.6"

                        // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueFade) propRectTitleValueLineOdd.add(propRectTitleValueFade)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueFade) propRectTitleValueLineEven.add(propRectTitleValueFade)
                        }

                        // Конец анимации титра - в конечной позиции следующиго титра с теми же координатами и с непрозрачностью 0%
                        propRectTitleValueFade = "${nextSubtitle.end}=${x} ${y} ${ww.toLong()} ${h} 0.0"

                        // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueFade) propRectTitleValueLineOdd.add(propRectTitleValueFade)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueFade) propRectTitleValueLineEven.add(propRectTitleValueFade)
                        }

                        // На данный момент мы залили и зафейдили текущую строку

                    // Если между текущей и следующей строкой больше или равно 200 мс
                    } else {

                        // Текущий титр - последний титр текущей строки
                        val currentSubtitle = currentLyricLine.subtitles[(currentLyricLine.subtitles.size)-1]

                        // Следующий титр - первый титр следующей строки
                        val nextSubtitle = nextLyricLine!!.subtitles[0]

                        // Время - начало текущего титра
                        var time = currentSubtitle.start

                        // Координата x всегда одна и та же = KLT_ITEM_CONTENT_TITLE_POSITION_START_X + KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                        val x = TITLE_POSITION_START_X_PX+TITLE_OFFSET_START_X_PX

                        // Координата y = позиция горизонта - высота символа + оффсет
                        var y = horizontPositionPx - currentFontSymbolHeightPx// + HORIZONT_OFFSET_PX

                        // Ширина = ширина текста тира * ширину символа
                        val w = currentSubtitle.text!!.length * currentFontSymbolWidthPx

                        // Высота = высоте символа
                        val h = currentFontSymbolHeightPx

                        // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                        var propRectTitleValueStart = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6"

                        // Время - конец текущего титра
                        time = currentSubtitle.end

                        // Ширина = предыдущее значение ширины + ширина
                        ww += w

                        // Конец анимации титра - в конечной позиции титра на том же уровне с непрозрачностью 60%
                        var propRectTitleValueEnd = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.6"

                        // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueStart) propRectTitleValueLineOdd.add(propRectTitleValueStart)
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueEnd) propRectTitleValueLineOdd.add(propRectTitleValueEnd)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueStart) propRectTitleValueLineEven.add(propRectTitleValueStart)
                            if (propRectTitleValueLineEven.last() != propRectTitleValueEnd) propRectTitleValueLineEven.add(propRectTitleValueEnd)
                        }

                        // На данный момент мы закрасили всю строку, и теперь надо её сфэйдить с переходом на новую строку

                        // Поднимаем y на высоту символа
                        y -= currentFontSymbolHeightPx

                        // Время - начало следующего титра
                        time = nextSubtitle.start

                        // Конец анимации титра - в начальной позиции следующиго титра с новыми координатами и с непрозрачностью 0%
                        var propRectTitleValueFade = "${time}=${x} ${y} ${ww.toLong()} ${h} 0.0"

                        // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                        if (i%2 == 0) {
                            if (propRectTitleValueLineOdd.last() != propRectTitleValueFade) propRectTitleValueLineOdd.add(propRectTitleValueFade)
                        } else {
                            if (propRectTitleValueLineEven.last() != propRectTitleValueFade) propRectTitleValueLineEven.add(propRectTitleValueFade)
                        }
                    }
                }
            }

            // Настало время заняться счётчиками вступления. В том случае, если для песни известно BPM
            if (song.bpm != null) {
                // Находим длительность показа одного счётчика в миллисекундах: BPM / 4 такта / 60 секунд * 1000 миллисекунд
                val counterLengthMs = (((song.bpm!!.toDouble() / 4) / 60) * 1000).toLong()

                // Счетчики надо вставлять тогда, когда перед не пустой строкой шла пустая. Найдём и пометим такие строки
                var currentTime = 0L
                var previousLineIsEmpty = true
                // Проходимся по строкам
                resultLyric.items.forEach { lyricLine ->
                    // Если строка не пустая
                    if (!lyricLine.isEmptyLine) {
                        // Если предыдущая строка была пустой или это первая не пустая строка
                        if (previousLineIsEmpty || currentTime == 0L) {
                            // Помечаем строку для счётчика
                            lyricLine.isNeedCounter = true
                        }
                        // Запоминаем текущую позицию
                        currentTime = convertTimecodeToMilliseconds(lyricLine.end!!)
                    }
                    previousLineIsEmpty = lyricLine.isEmptyLine
                }
                // На данный момент мы пометили всё нужные строки, для которых нужен счётчик

                // Проходимся по всем строкам, для которых нужен счётчик
                lyrics.filter { it.isNeedCounter }.forEach { lyric ->

                    // Счётчик "0" - с позиции начала строки
                    var startTimeMs = convertTimecodeToMilliseconds(lyric.start!!) - counterLengthMs * 0
                    var initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
                    var endTimeMs = startTimeMs + counterLengthMs

                    counter0list.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                    counter0list.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                    counter0list.add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")

                    // Счётчик "1" - с позиции начала строки - 1 такт
                    startTimeMs = convertTimecodeToMilliseconds(lyric.start!!) - counterLengthMs * 1
                    initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
                    endTimeMs = startTimeMs + counterLengthMs

                    if (startTimeMs > 0) {
                        counter1list.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                        counter1list.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                        counter1list.add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")
                    }

                    // Счётчик "2" - с позиции начала строки - 2 такта
                    startTimeMs = convertTimecodeToMilliseconds(lyric.start!!) - counterLengthMs * 2
                    initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
                    endTimeMs = startTimeMs + counterLengthMs

                    if (startTimeMs > 0) {
                        counter2list.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                        counter2list.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                        counter2list.add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")
                    }

                    // Счётчик "3" - с позиции начала строки - 3 такта
                    startTimeMs = convertTimecodeToMilliseconds(lyric.start!!) - counterLengthMs * 3
                    initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
                    endTimeMs = startTimeMs + counterLengthMs

                    if (startTimeMs > 0) {
                        counter3list.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                        counter3list.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                        counter3list.add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")
                    }

                    // Счётчик "4" - с позиции начала строки - 4 такта
                    startTimeMs = convertTimecodeToMilliseconds(lyric.start!!) - counterLengthMs * 4
                    initTimeMs = startTimeMs -(1000/FRAME_FPS+1)
                    endTimeMs = startTimeMs + counterLengthMs

                    if (startTimeMs > 0) {
                        counter4list.add("${convertMillisecondsToTimecode(initTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 0.0")
                        counter4list.add("${convertMillisecondsToTimecode(startTimeMs)}=0 0 $FRAME_WIDTH_PX $FRAME_HEIGHT_PX 1.0")
                        counter4list.add("${convertMillisecondsToTimecode(endTimeMs)}=-1440 -810 4800 2700 0.0")
                    }

                }

            }

            // Формируем тексты для файлов и сохраняем файлы

            val propRectValue = propRectLineValue.joinToString(";")
            val propFillOddValue = propRectTitleValueLineOdd.joinToString(";")
            val propFillEvenValue = propRectTitleValueLineEven.joinToString(";")
            val propFillCounter0Value = counter0list.joinToString(";")
            val propFillCounter1Value = counter1list.joinToString(";")
            val propFillCounter2Value = counter2list.joinToString(";")
            val propFillCounter3Value = counter3list.joinToString(";")
            val propFillCounter4Value = counter4list.joinToString(";")
            val propText = """<property name="rect">$propRectValue</property>""".replace(",",".")
            val propTextFillOdd = """<property name="rect">$propFillOddValue</property>""".replace(",",".")
            val propTextFillEven = """<property name="rect">$propFillEvenValue</property>""".replace(",",".")
            val propTextCounter0 = """<property name="rect">$propFillCounter0Value</property>""".replace(",",".")
            val propTextCounter1 = """<property name="rect">$propFillCounter1Value</property>""".replace(",",".")
            val propTextCounter2 = """<property name="rect">$propFillCounter2Value</property>""".replace(",",".")
            val propTextCounter3 = """<property name="rect">$propFillCounter3Value</property>""".replace(",",".")
            val propTextCounter4 = """<property name="rect">$propFillCounter4Value</property>""".replace(",",".")

            File(fileName).writeText(text)
            File(fileNameKdeTitile).writeText(templateTitle)
            File(fileNameKdeHorizont).writeText(templateHorizont)
            File(fileNameKdeTransformPropertyTitle).writeText(propText)
            File(fileNameKdeTransformPropertyFillOdd).writeText(propTextFillOdd)
            File(fileNameKdeTransformPropertyFillEven).writeText(propTextFillEven)
            File(fileNameKdeTransformPropertyCounter0).writeText(propTextCounter0)
            File(fileNameKdeTransformPropertyCounter1).writeText(propTextCounter1)
            File(fileNameKdeTransformPropertyCounter2).writeText(propTextCounter2)
            File(fileNameKdeTransformPropertyCounter3).writeText(propTextCounter3)
            File(fileNameKdeTransformPropertyCounter4).writeText(propTextCounter4)


            resultLyric.fontSize = maxFontSizePt
            resultLyric.horizontPosition = horizontPositionPx.toLong()
            resultLyric.symbolHeight = currentFontSymbolHeightPx
            resultLyric.symbolWidth = currentFontSymbolWidthPx

//            println(resultLyric.fontSize)
//            println(resultLyric.horizontPosition)
//            println(resultLyric.symbolWidth)
//            println(resultLyric.symbolHeight)

            val kdeHeaderTone = "Тональность: ${song.key}"
            val kdeHeaderBpm = "Темп: ${song.bpm} bpm"
            val kdeHeaderAlbum = "Альбом: ${song.album}"
            val kdeHeaderSongName = song.songName

            val kdeIn = "00:00:00.000"
            val kdeFadeIn = "00:00:01.000"
            val kdeOut = song.end!!.replace(",",".")
            val kdeFadeOut = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(song.end!!) - 1000).replace(",",".")
            val kdeLength = convertTimecodeToMilliseconds(song.end!!)
            val kdeSongTextXmlData = templateTitle
            val kdeHorizontXmlData = templateHorizont
            val kdeTransformFillEvenData = propTextFillEven
            val kdeTransformFillOddData = propTextFillOdd
            val kdeTransformTextData = propText
            val kdeTransformCounter0Data = propTextCounter0
            val kdeTransformCounter1Data = propTextCounter1
            val kdeTransformCounter2Data = propTextCounter2
            val kdeTransformCounter3Data = propTextCounter3
            val kdeTransformCounter4Data = propTextCounter4

            val templateProject = """<?xml version='1.0' encoding='utf-8'?>
<mlt LC_NUMERIC="C" producer="main_bin" version="7.9.0" root="${song.rootFolder}">
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
  <property name="kdenlive:file_hash">c59779cad231c7b8f562e8ef29443a01</property>
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
  <property name="kdenlive:file_hash">16ef3f54e386fdf436ac658069cd3452</property>
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
  <property name="kdenlive:file_hash">13e1c0b0876a622164d511aa95adea94</property>
 </producer>
 <producer id="producer_logotype" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">/home/nsa/Documents/Караоке/Агата Кристи/Agata_Logo.png</property>
  <property name="ttl">25</property>
  <property name="aspect_ratio">1</property>
  <property name="progressive">1</property>
  <property name="seekable">1</property>
  <property name="meta.media.width">630</property>
  <property name="meta.media.height">630</property>
  <property name="mlt_service">qimage</property>
  <property name="kdenlive:clipname">Логотип</property>
  <property name="kdenlive:duration">00:00:05.000</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">5</property>
  <property name="kdenlive:file_size">30611</property>
  <property name="kdenlive:file_hash">c4e099091af6d3375ff857acb31ce4eb</property>
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
  <content brushcolor="0,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,1920,50" gradient="#ff000000;#00bf4040;0;100;90"/>
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
  <content brushcolor="0,0,0,255" pencolor="0,0,0,255" penwidth="0" rect="0,0,1920,210"/>
 </item>
 <startviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <endviewport rect="0,0,$FRAME_WIDTH_PX,$FRAME_HEIGHT_PX"/>
 <background color="0,0,0,0"/>
</kdenlivetitle>
</property>
  <property name="kdenlive:folderid">-1</property>
  <property name="kdenlive:clip_type">2</property>
  <property name="kdenlive:id">6</property>
  <property name="kdenlive:file_hash">95d29724ff9aa14f6293d585a6f15819</property>
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
  <property name="kdenlive:file_hash">6c362c6f19d4488ea3cfdbb02545e00a</property>
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
  <property name="kdenlive:file_hash">35887e30f1b8aaa2015de9b97a459ef7</property>
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
  <property name="kdenlive:file_hash">fd04285313b869ae7b121d36d93c6282</property>
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
  <property name="kdenlive:file_hash">086e6c6bbca124747fe2086e4d9a6d8d</property>
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
  <property name="kdenlive:file_hash">b840cdc4c2d960b77793a5df3268b902</property>
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
  <property name="kdenlive:file_hash">343afff97616b0574c44cfd53b2d5765</property>
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
  <property name="kdenlive:file_hash">fe787c4acf6ed08b75ec319697c6204d</property>
  <property name="force_reload">0</property>
  <property name="meta.media.width">$FRAME_WIDTH_PX</property>
  <property name="meta.media.height">$FRAME_HEIGHT_PX</property>
 </producer>
 <producer id="producer_audio_song" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.audioSongPath}</property>
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
  <property name="kdenlive:file_hash">10050cff723571891d2a83e0b83808bf</property>
  <property name="kdenlive:audio_max0">250</property>
 </producer>
 <producer id="producer_audio_music" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.audioMusicPath}</property>
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
  <property name="kdenlive:file_hash">1c3feccff8d2e997c1c49b25c4e85935</property>
  <property name="kdenlive:audio_max0">248</property>
 </producer>
 <producer id="producer_audio_vocal" in="$kdeIn" out="$kdeOut">
  <property name="length">$kdeLength</property>
  <property name="eof">pause</property>
  <property name="resource">${song.audioVocalPath}</property>
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
  <property name="kdenlive:file_hash">b6676a29a1127b0244f33cc610f2f491</property>
  <property name="kdenlive:audio_max0">204</property>
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
  <property name="kdenlive:docproperties.renderurl">(06) [Агата Кристи] Сказочная тайга [lyrics].mp4</property>
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
  <property name="resource">${song.audioVocalPath}</property>
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
  <property name="kdenlive:file_hash">b6676a29a1127b0244f33cc610f2f491</property>
  <property name="kdenlive:audio_max0">204</property>
  <property name="xml">was here</property>
  <property name="set.test_audio">0</property>
  <property name="set.test_image">1</property>
 </producer>
 <playlist id="playlist0">
  <property name="kdenlive:audio_track">1</property>
  <entry producer="producer_audio_vocal_file" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">16</property>
  </entry>
 </playlist>
 <playlist id="playlist1">
  <property name="kdenlive:audio_track">1</property>
 </playlist>
 <tractor id="tractor0" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:audio_track">1</property>
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">0</property>
  <property name="kdenlive:track_name">Vocal</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="both" producer="playlist0"/>
  <track hide="both" producer="playlist1"/>
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
  <property name="resource">${song.audioMusicPath}</property>
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
  <property name="kdenlive:file_hash">1c3feccff8d2e997c1c49b25c4e85935</property>
  <property name="kdenlive:audio_max0">248</property>
  <property name="xml">was here</property>
  <property name="set.test_audio">0</property>
  <property name="set.test_image">1</property>
 </producer>
 <playlist id="playlist2">
  <property name="kdenlive:audio_track">1</property>
  <entry producer="producer_audio_music_file" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">15</property>
  </entry>
 </playlist>
 <playlist id="playlist3">
  <property name="kdenlive:audio_track">1</property>
 </playlist>
 <tractor id="tractor1" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:audio_track">1</property>
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">0</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Music</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="both" producer="playlist2"/>
  <track hide="both" producer="playlist3"/>
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
  <property name="resource">${song.audioSongPath}</property>
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
  <property name="kdenlive:file_hash">10050cff723571891d2a83e0b83808bf</property>
  <property name="kdenlive:audio_max0">250</property>
  <property name="xml">was here</property>
  <property name="set.test_audio">0</property>
  <property name="set.test_image">1</property>
 </producer>
 <playlist id="playlist4">
  <property name="kdenlive:audio_track">1</property>
  <entry producer="producer_audio_song_file" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">14</property>
  </entry>
 </playlist>
 <playlist id="playlist5">
  <property name="kdenlive:audio_track">1</property>
 </playlist>
 <tractor id="tractor2" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:audio_track">1</property>
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">0</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Song</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="video" producer="playlist4"/>
  <track hide="video" producer="playlist5"/>
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
 <playlist id="playlist6">
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
 <playlist id="playlist7"/>
 <tractor id="tractor3" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist6"/>
  <track hide="audio" producer="playlist7"/>
 </tractor>
 <playlist id="playlist8">
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
 <playlist id="playlist9"/>
 <tractor id="tractor4" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Караоке</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="both" producer="playlist8"/>
  <track hide="both" producer="playlist9"/>
 </tractor>
 <playlist id="playlist10">
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
 <playlist id="playlist11"/>
 <tractor id="tractor5" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Горизонт</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist10"/>
  <track hide="audio" producer="playlist11"/>
 </tractor>
 <playlist id="playlist12">
  <entry producer="producer_orange" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">4</property>
   <filter id="filter_fill_even">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformFillEvenData
    <property name="compositing">0</property>
    <property name="distort">1</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist13"/>
 <tractor id="tractor6" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">ODD</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist12"/>
  <track hide="audio" producer="playlist13"/>
 </tractor>
 <playlist id="playlist14">
  <entry producer="producer_orange" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">4</property>
   <filter id="filter_fill_odd">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformFillOddData
    <property name="rotation">$kdeIn=0</property>
    <property name="compositing">0</property>
    <property name="distort">1</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist15"/>
 <tractor id="tractor7" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">EVEN</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist14"/>
  <track hide="audio" producer="playlist15"/>
 </tractor>
 <playlist id="playlist16">
  <entry producer="producer_song_text" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">2</property>
   <filter id="karaoke_text">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformTextData
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist17"/>
 <tractor id="tractor8" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Текст песни</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist16"/>
  <track hide="audio" producer="playlist17"/>
 </tractor>
 <playlist id="playlist18">
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
 <playlist id="playlist19"/>
 <tractor id="tractor9" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Заголовок</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist18"/>
  <track hide="audio" producer="playlist19"/>
 </tractor>
 <playlist id="playlist20">
  <entry producer="producer_logotype" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">5</property>
   <property name="kdenlive:activeeffect">1</property>
   <filter id="filter17">
    <property name="lift_r">$kdeIn=0.862501</property>
    <property name="lift_g">$kdeIn=0.862501</property>
    <property name="lift_b">$kdeIn=0.862501</property>
    <property name="gamma_r">$kdeIn=0.977523</property>
    <property name="gamma_g">$kdeIn=0.977523</property>
    <property name="gamma_b">$kdeIn=0.977523</property>
    <property name="gain_r">$kdeIn=2.00003</property>
    <property name="gain_g">$kdeIn=0.941176</property>
    <property name="gain_b">$kdeIn=0.900893</property>
    <property name="mlt_service">lift_gamma_gain</property>
    <property name="kdenlive_id">lift_gamma_gain</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
   <filter id="karaoke_logotype">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    <property name="rect">$kdeIn=1096 -202 1048 589 0.000000;$kdeFadeIn=1096 -202 1048 589 1.000000;$kdeFadeOut=1096 -202 1048 589 1.000000;$kdeOut=1096 -202 1048 589 0.000000</property>
    <property name="rotation">$kdeIn=0;$kdeFadeIn=0;$kdeFadeOut=0;$kdeOut=0</property>
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist21"/>
 <tractor id="tractor10" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">Логотип</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist20"/>
  <track hide="audio" producer="playlist21"/>
 </tractor>
 <playlist id="playlist22">
  <entry producer="producer_counter4" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">9</property>
   <filter id="filter_counter_4">
    <property name="rotate_center">0</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformCounter4Data
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist23"/>
 <tractor id="tractor11" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">4</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist22"/>
  <track hide="audio" producer="playlist23"/>
 </tractor>
 <playlist id="playlist24">
  <entry producer="producer_counter3" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">10</property>
   <filter id="filter_counter_3">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformCounter3Data
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist25"/>
 <tractor id="tractor12" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">3</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist24"/>
  <track hide="audio" producer="playlist25"/>
 </tractor>
 <playlist id="playlist26">
  <entry producer="producer_counter2" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">11</property>
   <filter id="filter_counter_2">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformCounter2Data
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist27"/>
 <tractor id="tractor13" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">2</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist26"/>
  <track hide="audio" producer="playlist27"/>
 </tractor>
 <playlist id="playlist28">
  <entry producer="producer_counter1" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">12</property>
   <filter id="filter_counter_1">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformCounter1Data
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist29"/>
 <tractor id="tractor14" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">1</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist28"/>
  <track hide="audio" producer="playlist29"/>
 </tractor>
 <playlist id="playlist30">
  <entry producer="producer_counter0" in="$kdeIn" out="$kdeOut">
   <property name="kdenlive:id">13</property>
   <filter id="filter_counter_0">
    <property name="rotate_center">1</property>
    <property name="mlt_service">qtblend</property>
    <property name="kdenlive_id">qtblend</property>
    $kdeTransformCounter0Data
    <property name="compositing">0</property>
    <property name="distort">0</property>
    <property name="kdenlive:collapsed">0</property>
   </filter>
  </entry>
 </playlist>
 <playlist id="playlist31"/>
 <tractor id="tractor15" in="$kdeIn" out="$kdeOut">
  <property name="kdenlive:trackheight">69</property>
  <property name="kdenlive:timeline_active">1</property>
  <property name="kdenlive:collapsed">28</property>
  <property name="kdenlive:track_name">0</property>
  <property name="kdenlive:thumbs_format"/>
  <property name="kdenlive:audio_rec"/>
  <track hide="audio" producer="playlist30"/>
  <track hide="audio" producer="playlist31"/>
 </tractor>
 <tractor id="tractor16" in="$kdeIn" out="00:11:11.917">
  <track producer="black_track"/>
  <track producer="tractor0"/>
  <track producer="tractor1"/>
  <track producer="tractor2"/>
  <track producer="tractor3"/>
  <track producer="tractor4"/>
  <track producer="tractor5"/>
  <track producer="tractor6"/>
  <track producer="tractor7"/>
  <track producer="tractor8"/>
  <track producer="tractor9"/>
  <track producer="tractor10"/>
  <track producer="tractor11"/>
  <track producer="tractor12"/>
  <track producer="tractor13"/>
  <track producer="tractor14"/>
  <track producer="tractor15"/>
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

            File("${song.rootFolder}/${song.projectPath}").writeText(templateProject)

            return resultLyric
        }
    }
}


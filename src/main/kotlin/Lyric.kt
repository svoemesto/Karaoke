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
            println(text)

            // Теперь в списке result у нас нужное количество строк - как полных, так и пустых. И в начале, и в середине, и в конце
            // Создаём объект Lyric и в его items помещаем этот список
            var resultLyric = Lyric()
            resultLyric.items = result

            // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
            val maxTextWidth = FRAME_WIDTH - KLT_ITEM_CONTENT_TITLE_POSITION_START_X * 2

            // maxTextLength - максимальная длина текста (в символах) = длине символов самой длинной строки
            val maxTextLength = resultLyric.items.maxBy { it.text!!.length }.text!!.length

            // maxSymbolWidth - максимальная ширина символа = максимальная ширина текста делённая на максимальную длину
            val maxSymbolWidth = maxTextWidth / maxTextLength

            // maxFontSize - максимальный размер шрифта = максимальная ширина символа * KLT_ITEM_CONTENT_FONT_PIXEL_SIZE / KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH
            // но не больше KLT_ITEM_CONTENT_FONT_PIXEL_SIZE
            val maxFontSize = min(
                (maxSymbolWidth * KLT_ITEM_CONTENT_FONT_PIXEL_SIZE) / KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH,
                KLT_ITEM_CONTENT_FONT_PIXEL_SIZE
            )

            // currentFontSymbolHeightDouble - текущая высота символа (дробное)
            val currentFontSymbolHeightDouble = (maxFontSize.toDouble() * KLT_ITEM_CONTENT_FONT_SYMBOL_HEIGHT / KLT_ITEM_CONTENT_FONT_PIXEL_SIZE)

            // currentFontSymbolHeightDouble - текущая ширина символа (дробное)
            val currentFontSymbolWidthDouble = (maxFontSize.toDouble() * KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH / KLT_ITEM_CONTENT_FONT_PIXEL_SIZE)

            // currentFontSymbolHeightDouble - текущая высота символа (целое)
            val currentFontSymbolHeight: Long = Math.round(currentFontSymbolHeightDouble)

            // currentFontSymbolHeightDouble - текущая ширина символа (целое)
            val currentFontSymbolWidth: Long = Math.round(currentFontSymbolWidthDouble)

            // boxHeight - высота "бокса" текста = количество строк текста * высоту символа
            val boxHeight: Long = ((resultLyric.items.size) * currentFontSymbolHeight)

            // boxHeight - ширина "бокса" текста = ширина текста * ширину символа
            val boxWidth: Long = (maxTextLength * currentFontSymbolWidth)

            // Шаблон для файла субтитра текста
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

            // horizontPosition - позиция горизонта = половина экрана + половина высоты символа - оффсет
            val horizontPosition = (FRAME_HEIGHT / 2 + currentFontSymbolHeight / 2) - HORIZONT_OFFSET

            // Шаблон для файла субтитра "горизонта"
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
                    y = horizontPosition - ((index + 1)*(currentFontSymbolHeightDouble+HEIGHT_CORRECTION)).toLong(),
                    w = FRAME_WIDTH,
                    h = boxHeight,
                    opacity = 1.0
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
                        lyricLine.subtitles?.items?.last()?.end = lyricLine.end
                        // сдвигаем время TransformProperty к начало последнего титра текущей строки
                        time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(lyricLine.subtitles!!.items.last().start!!))
                    }
                }

                val endTp = TransformProperty(
                    time = time,
                    x = 0,
                    y = horizontPosition - ((index + 1)*(currentFontSymbolHeightDouble+HEIGHT_CORRECTION)).toLong(),
                    w = FRAME_WIDTH,
                    h = boxHeight,
                    opacity = 1.0
                )
                lyricLine.startTp = startTp
                lyricLine.endTp = endTp
            }

            // Список свойств трансформации текста
            var propRectLineValue = emptyList<String>().toMutableList()
            // Список свойств трансформации чётных заливок
            var propRectTitleValueLineOdd = emptyList<String>().toMutableList()
            // Список свойств трансформации нечётных заливок
            var propRectTitleValueLineEven = emptyList<String>().toMutableList()

            // Настало время прописать TransformProperty для заливок
            // Проходимся по строкам - от первой до предпоследней
            for (i in 0..resultLyric.items.size-2) {

                // Текущая строка
                val currentLyricLine = resultLyric.items[i]

                //Следующая строка
                val nextLyricLine = resultLyric.items[i+1]

                // Разница во времени между текущей строкой и следующей
                val diffInMills = convertTimecodeToMilliseconds(nextLyricLine.start!!) - convertTimecodeToMilliseconds(currentLyricLine.end!!)

//
//                if (diffInMills < 200) {
//                    currentLyricLine.end = nextLyricLine.start
//                    currentLyricLine.subtitles?.items?.last()?.end = currentLyricLine.end
//                }

                // Свойство трансформации текста текущей строки - из startTp
                propRectLineValue.add("${currentLyricLine.startTp?.time}=${currentLyricLine.startTp?.x} ${currentLyricLine.startTp?.y} ${currentLyricLine.startTp?.w} ${currentLyricLine.startTp?.h} ${currentLyricLine.startTp?.opacity}")

                // Если текущая строка пустая - ничего больше не делаем. Переход между строками будет плавны
                // Если текущая строка не пустая
                if (currentLyricLine.text != "") {

                    // Начальная позиция w для заливки = 1
                    var ww = 1L

                    // Получаем первый титр текущей строки (он точно есть, т.к. строка не пустая)
                    val currentSubtitle = currentLyricLine.subtitles!!.items[0]

                    // Время начала анимации = времени начала этого титра //-(1000/FRAME_FPS+1)
                    val startTime = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(currentSubtitle.start!!))

                    // Координата x всегда одна и та же = KLT_ITEM_CONTENT_TITLE_POSITION_START_X + KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                    val x = KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X

                    // Координата y = позиция горизонта - высота символа + оффсет
                    val y = horizontPosition - currentFontSymbolHeight + HORIZONT_OFFSET

                    // Ширина = начальной позиции (1)
                    val w = ww

                    // Высота = высоте символа
                    val h = currentFontSymbolHeight

                    // Свойство трансформации заливки с полной прозрачностью
                    var propRectTitleValueFade = "${startTime}=${x} ${y} ${w} ${h} 0.0"

                    // В зависимости от чётности номера линии добавляем свойство трансформации заливки в нужный список
                    if (i%2 == 0) {
                        propRectTitleValueLineOdd.add(propRectTitleValueFade)
                    } else {
                        propRectTitleValueLineEven.add(propRectTitleValueFade)
                    }

                    // Смещаем стартовую позицию w на величину KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                    ww = -KLT_ITEM_CONTENT_TITLE_OFFSET_START_X

                    // Проходимся по титрам текущей линии от первого до предпоследнего
                    for (j in 0..(currentLyricLine.subtitles!!.items.size) - 2) {

                        // Текущий титр
                        val currentSubtitle = currentLyricLine.subtitles.items[j]

                        // Время - начало текущего титра
                        var time = currentSubtitle.start

                        // Координата x всегда одна и та же = KLT_ITEM_CONTENT_TITLE_POSITION_START_X + KLT_ITEM_CONTENT_TITLE_OFFSET_START_X
                        val x = KLT_ITEM_CONTENT_TITLE_POSITION_START_X+KLT_ITEM_CONTENT_TITLE_OFFSET_START_X

                        // Координата y = позиция горизонта - высота символа + оффсет
                        val y = horizontPosition - currentFontSymbolHeight + HORIZONT_OFFSET

                        // Ширина = ширина текста тира * ширину символа
                        val w = currentSubtitle.text!!.length * currentFontSymbolWidth

                        // Высота = высоте символа
                        val h = currentFontSymbolHeight

                        // Начало анимации титра - в начальной позиции титра с непрозрачностью 60%
                        var propRectTitleValueStart = "${time}=${x} ${y} ${ww} ${h} 0.6"

                        // Время - конец текущего титра
                        time = currentSubtitle.end

                        // Ширина = предыдущее значение ширины + ширина
                        ww += w
                        // Конец анимации титра - в конечной позиции титра с непрозрачностью 60%
                        var propRectTitleValueEnd = "${time}=${x} ${y} ${ww} ${h} 0.6"

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

                    // Если между текущей и следующей строкой больше или равно 200 мс
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
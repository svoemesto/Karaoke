import java.io.File
import java.lang.Long.max
import java.lang.Long.min
import kotlin.math.roundToLong

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
            var startLine: String? = null
            var endLine: String? = null

            var subs: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()
            val lyrics: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()
            val result: MutableList<LyricLine> = emptyList<LyricLine>().toMutableList()

            var text = ""
            var line = ""
            var maxLineDuration = 0L

            subtitles.items.forEach { subtitle ->
                if (subtitle.isLineStart == true) {
                    startLine = subtitle.start
                    line = ""
                    subs = emptyList<Subtitle>().toMutableList()
                }
                line += subtitle.text
                subs.add(subtitle)
                if (subtitle.isLineEnd == true) {
                    endLine = subtitle.end
                    val s = Subtitles()
                    s.items = subs

                    val liric = LyricLine(
                        text = line,
                        start = startLine,
                        end = endLine,
                        subtitles = s,
                        startTp = null,
                        endTp = null
                    )
                    val lineDuration = getDurationInMilliseconds(startLine!!, endLine!!)
                    maxLineDuration = max(maxLineDuration, lineDuration)
                    lyrics.add(liric)
//                    text += "[$startLine --> $endLine]: $line\n"
                }
            }

            var currentPositionStart = 0L
            var currentPositionEnd = 0L
            lyrics.forEach { liric ->
                val silentDuration = convertTimecodeToMilliseconds(liric.start!!) - currentPositionEnd
                val linesToInsert: Long = silentDuration / maxLineDuration
                if (linesToInsert > 0) {
                    val silentLineDuration: Long =  silentDuration / linesToInsert
                    for (i in 1..linesToInsert) {
                        val startDuration = convertMillisecondsToTimecode(currentPositionEnd)
                        val endDuration = convertMillisecondsToTimecode(currentPositionEnd + silentLineDuration)
                        val subtitleEmpty = Subtitle(
                            text = "",
                            start = startDuration,
                            end = endDuration,
                            isLineStart = true,
                            isLineEnd = true
                        )
                        val s = Subtitles()
                        s.items = listOf(subtitleEmpty)
                        val liricEmpty = LyricLine(
                            text = "",
                            start = startDuration,
                            end = endDuration,
                            subtitles = s
                        )
                        currentPositionEnd += silentLineDuration
                        result.add(liricEmpty)
//                        text += "[$startDuration --> $endDuration]: \n"
                        text += "\n"
                    }
                }
                result.add(liric)
                currentPositionEnd = convertTimecodeToMilliseconds(liric.end!!)
//                text += "[${liric.start} --> ${liric.end}]: ${liric.text}\n"
                text += "${liric.text}\n"
            }

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

            val horizontPosition = (FRAME_HEIGHT / 2 + currentFontSymbolHeight / 2) + 7


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




            resultLyric.items.forEachIndexed { index, liricLine ->
                val startTp = TransformProperty(
                    time = liricLine.start,
                    x = 0,
                    y = horizontPosition - ((index + 1)*(currentFontSymbolHeightDouble-1.1)).toLong(),
                    w = FRAME_WIDTH,
                    h = boxHeight,
                    opacity = 1.0
                )

                val endTp = TransformProperty(
                    time = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(liricLine.end!!)-(1000/FRAME_FPS+1)),
                    x = 0,
                    y = horizontPosition - ((index + 1)*(currentFontSymbolHeightDouble-1.1)).toLong(),
                    w = FRAME_WIDTH,
                    h = boxHeight,
                    opacity = 1.0
                )
                liricLine.startTp = startTp
                liricLine.endTp = endTp
            }
            val propRectValue = resultLyric.items.map { "${it.startTp?.time}=${it.startTp?.x} ${it.startTp?.y} ${it.startTp?.w} ${it.startTp?.h} ${it.startTp?.opacity};${it.endTp?.time}=${it.startTp?.x} ${it.startTp?.y} ${it.startTp?.w} ${it.startTp?.h} ${it.startTp?.opacity}" }.joinToString(";")
            val propText = """<property name="rect">$propRectValue</property>""".replace(",",".")

            File(fileName).writeText(text)
            File(fileNameKdeTitile).writeText(templateTitle)
            File(fileNameKdeHorizont).writeText(templateHorizont)
            File(fileNameKdeTransformPropertyTitle).writeText(propText)

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
    val start: String? = null,
    val end: String? = null,
    val subtitles: Subtitles? = null,
    var startTp: TransformProperty? = null,
    var endTp: TransformProperty? = null
)
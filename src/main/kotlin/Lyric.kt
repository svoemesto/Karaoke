import java.io.File
import java.lang.Long.max
import java.lang.Long.min

class Lyric {
    var items: List<LyricLine> = emptyList()
    companion object {
        fun getLiric(subtitles: Subtitles): Lyric {

            val fileName = "src/main/resources/lyrics.txt"
            val fileNameKdeTitile = "src/main/resources/lyrics.kdenlivetitle"
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
                        subtitles = s
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
            val currentFontSymbolHeight = maxFontSize * KLT_ITEM_CONTENT_FONT_SYMBOL_HEIGHT / KLT_ITEM_CONTENT_FONT_PIXEL_SIZE
            val currentFontSymbolWidth = maxFontSize * KLT_ITEM_CONTENT_FONT_SYMBOL_WIDTH / KLT_ITEM_CONTENT_FONT_PIXEL_SIZE

            val boxHeight = resultLyric.items.size * currentFontSymbolHeight
            val boxWidth = maxTextLength * currentFontSymbolWidth

            val template = """
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


            File(fileName).writeText(text)
            File(fileNameKdeTitile).writeText(template)

            return resultLyric
        }
    }
}

data class LyricLine(
    val text: String? = null,
    val start: String? = null,
    val end: String? = null,
    val subtitles: Subtitles? = null
)
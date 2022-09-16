import java.io.File
import java.lang.Long.max

class Lyric {
    var items: List<LyricLine> = emptyList()
    companion object {
        fun getLiric(subtitles: Subtitles): Lyric {

            val fileName = "src/main/resources/lyrics.txt"
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
                        text += "[$startDuration --> $endDuration]: \n"
                    }
                }
                result.add(liric)
                currentPositionEnd = convertTimecodeToMilliseconds(liric.end!!)
                text += "[${liric.start} --> ${liric.end}]: ${liric.text}\n"
            }

            println(text)

            File(fileName).writeText(text)

            var resultLyric = Lyric()
            resultLyric.items = result

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
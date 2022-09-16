import java.io.File

class Subtitles() {
    var items: List<Subtitle> = emptyList()
    var end: String? = null
    companion object {
        fun getSubtitles(): Subtitles {
            // Задаём имя и путь к файлу с субтитрами
            val fileName = "subtitles.srt"
            // Считываем содержимое файла субтитров
            val body = Subtitles::class.java.getResource(fileName)?.readText()
            // Разбиваем содерфимое файла на массив строк
            val subs = body?.split("\n")

            var id: Long? = null
            var startEnd: String? = null
            var start: String? = null
            var end: String? = null
            var text: String? = null

            val subtitles: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()

            // Если массив строк не пустой - работаем с ним
            if (subs != null) {
                // Проходимся последовательно по всем элементам массива строк
                subs.forEach { sub ->
                    if (id == null && sub != "") {
                        id = sub.toLongOrNull()
                    } else if (startEnd == null && sub != "") {
                        startEnd = sub
                    } else if(text == null && sub != "") {
                        text = sub
                    } else if(text != null && sub != "") {
                        text += sub
                    } else if(sub == "") {
                        if (id != null && startEnd != null && text != null) {
                            val se = startEnd!!.split(" --> ")
                            start = se[0]
                            end = se[1]
                            val isLineStart = text!!.startsWith("//")
                            val isLineEnd = text!!.endsWith("\\\\")
                            text = text!!
                                .replace("//","")
                                .replace("\\\\","")
                                .replace("_", " ")
                            val subtitle = Subtitle(id = id,
                            start = start,
                            end = end,
                            text = text,
                            isLineStart = isLineStart,
                            isLineEnd = isLineEnd)
                            subtitles.add(subtitle)
                            id = null
                            startEnd = null
                            start = null
                            end = null
                            text = null
                        }
                    }
                }
            }

            val result = Subtitles()
            result.items = subtitles
            result.end = subtitles.last().end
            if (subtitles.last().text == "[END]") {
                subtitles.removeLast()
            }
            result.items.forEach {
                println(it)
            }

            return result
        }

        fun createTextFromSubtitles() {

            val fileName = "src/main/resources/lyrics.txt"

            val subtitles = getSubtitles()
            var text = ""
            subtitles.items.forEach { subtitle ->
                text += subtitle.text
                if (subtitle.isLineEnd == true) text += "\n"
            }

            println(text)

            File(fileName).writeText(text)

        }

    }
}

data class Subtitle(
    val id: Long? = null,
    val start: String? = null,
    val end: String? = null,
    val text: String? = null,
    val isLineStart: Boolean? = null,
    val isLineEnd: Boolean? = null
)
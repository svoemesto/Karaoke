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

                    if (id == null && sub != "") {                  // Если еще нет id и строка не пустая - значит текущая строка - это id
                        id = sub.toLongOrNull()
                    } else if (startEnd == null && sub != "") {     // Если еще нет startEnd и строка не пустая - значит текущая строка - это startEnd
                        startEnd = sub
                    } else if(text == null && sub != "") {          // Если еще нет text и строка не пустая - значит текущая строка - это text
                        text = sub
                    } else if(text != null && sub != "") {          // Если уже есть text и строка всё еще не пустая - значит текущая строка - это тоже text
                        text += sub
                    } else if(sub == "") {                          // Если строка пустая
                        // Если уже есть все переменные
                        if (id != null && startEnd != null && text != null) {
                            // Разделяем startEnd на start и end в список, вынимаем из списка соответствующие значения
                            // прибавляя к значению времени оффсет, указанный в TIME_OFFSET
                            val se = startEnd!!.split(" --> ")
                            start = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(se[0])+TIME_OFFSET)
                            end = convertMillisecondsToTimecode(convertTimecodeToMilliseconds(se[1])+TIME_OFFSET)
                            val isLineStart = text!!.startsWith("//")   // Вычисляем признак начала строки
                            val isLineEnd = text!!.endsWith("\\\\")     // Вычисляем признак конца строки
                            // Удаляем служебные символы из строки и заменяем подчёркивание пробелом
                            text = text!!
                                .replace("//","")
                                .replace("\\\\","")
                                .replace("_", " ")
                            // Создаем объект Subtitle и инициализируем его переменными
                            val subtitle = Subtitle(id = id, start = start, end = end, text = text, isLineStart = isLineStart, isLineEnd = isLineEnd)
                            // Добавляем этот объект к списку объектов
                            subtitles.add(subtitle)
                            // Обнулям переменные
                            id = null
                            startEnd = null
                            start = null
                            end = null
                            text = null
                        }
                    }
                }
            }

            // Создаем объект Subtitles
            val result = Subtitles()
            // Устанавливаем end равный end последнего объекта из списка
            result.end = subtitles.last().end
            // Удаляем последний объект из списка - он у нас "служебный" и был нужен только для обозначения финальной позиции
            subtitles.removeLast()
            // В его объект Subtitles кладём список объектов Subtitle
            result.items = subtitles
            // Выводим на экран список объектов Subtitle
            result.items.forEach {
                println(it)
            }

            // Возвращаем объект Subtitles
            return result
        }

    }
}

data class Subtitle(
    val id: Long? = null,
    val start: String? = null,
    var end: String? = null,
    val text: String? = null,
    val isLineStart: Boolean? = null,
    val isLineEnd: Boolean? = null
)
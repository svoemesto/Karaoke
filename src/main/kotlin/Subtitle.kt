class Song() {
    var subtitles: List<Subtitle> = emptyList()
    var end: String? = null
    var bpm: Long = 90
    var ms: Long? = null
    var key: String? = null
    var rootFolder: String? = null
    var projectLyricsPath: String? = null
    var projectKaraokePath: String? = null
    var audioSongPath: String? = null
    var audioMusicPath: String? = null
    var audioVocalPath: String? = null
    var album: String? = null
    var songName: String? = null
    var videoLyricsPath: String? = null
    var videoKaraokePath: String? = null
    var beat: String? = null
    var body: String = ""
    companion object {
        fun getSubtitles(): Song {
            // Задаём имя и путь к файлу с субтитрами
            val fileName = "subtitles.srt"
            // Считываем содержимое файла субтитров
            val body = Song::class.java.getResource(fileName)?.readText()
            // Разбиваем содержимое файла на массив строк
            val subs = body?.split("\n")

            var id: Long? = null
            var startEnd: String? = null
            var start: String? = null
            var end: String? = null
            var text: String? = null
            var bpm: Long? = null
            var ms: Long? = null
            var key: String? = null
            var rootFolder: String? = null
            var projectLyricsPath: String? = null
            var projectKaraokePath: String? = null
            var audioSongPath: String? = null
            var audioMusicPath: String? = null
            var audioVocalPath: String? = null
            var album: String? = null
            var songName: String? = null
            var videoLyricsPath: String? = null
            var videoKaraokePath: String? = null
            var beat: String? = null

            val subtitles: MutableList<Subtitle> = emptyList<Subtitle>().toMutableList()

            // Если массив строк не пустой - работаем с ним
            if (subs != null) {
                // Проходимся последовательно по всем элементам массива строк
                subs.forEach { sub ->

                    if (id == null && sub != "") {                  // Если еще нет id и строка не пустая - значит текущая строка - это id
                        id = sub.toLongOrNull()
                    } else if (startEnd == null && sub != "") {     // Если еще нет startEnd и строка не пустая - значит текущая строка - это startEnd
                        startEnd = sub
                    } else if(text == null && sub != "") {          // Если еще нет text и строка не пустая - значит текущая строка - это text или настройка
                        // Если sub начитается с "[SETTING]|" - это настройка
                        if (sub.uppercase().startsWith("[SETTING]|")) {
                            // Разделяем sub по | в список
                            val settings = sub.split("|")
                            when (settings[1].uppercase()) {
                                "BPM" -> bpm = settings[2].toLongOrNull()
                                "KEY" -> key = settings[2]
                                "MS" -> ms = settings[2].toLongOrNull()
                                "NAME" -> songName = settings[2]
                                "ALBUM" -> album = settings[2]
                                "ROOT" -> rootFolder = settings[2]
                                "MUSICFILE" -> audioMusicPath = settings[2]
                                "VOCALFILE" -> audioVocalPath = settings[2]
                                "SONGFILE" -> audioSongPath = settings[2]
                                "PROJECTLYRICSFILE" -> projectLyricsPath = settings[2]
                                "PROJECTKARAOKEFILE" -> projectKaraokePath = settings[2]
                                "VIDEOLYRICSFILE" -> videoLyricsPath = settings[2]
                                "VIDEOKARAOKEFILE" -> videoKaraokePath = settings[2]
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
                    } else if(text != null && sub != "") {          // Если уже есть text и строка всё еще не пустая - значит текущая строка - это тоже text
                        text += sub
                    } else if (sub == ""){                                        // Если строка пустая
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
                                .replace("//","")
                                .replace("\\\\","")
                                .replace("_", " ")
                            // Создаем объект Subtitle и инициализируем его переменными
                            val isBeat = if (text != "" && beat != null && bpm != null) {
                                val beatMs = if (ms == null) (60000.0 / bpm!!).toLong() else ms!!
                                val startBeatNumber = getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(se[0]), beatMs, beat!!)
                                val endBeatNumber = getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(se[1]), beatMs, beat!!)
                                (startBeatNumber == 1L && endBeatNumber == 1L)|| startBeatNumber > endBeatNumber
                            } else false
                            val subtitle = Subtitle(id = id, start = start, end = end, text = text, isLineStart = isLineStart, isLineEnd = isLineEnd, isBeat = isBeat)
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
            }

            // Создаем объект Subtitles
            val result = Song()
            // Устанавливаем end равный end последнего объекта из списка и найденные выше настройки (если они были)
            result.end = subtitles.last().end
            result.body = body?:""
            result.bpm = bpm?:90L
            result.key = key
            result.ms = ms
            result.rootFolder = rootFolder
            result.projectLyricsPath = projectLyricsPath
            result.projectKaraokePath = projectKaraokePath
            result.audioSongPath = audioSongPath
            result.audioMusicPath = audioMusicPath
            result.audioVocalPath = audioVocalPath
            result.album = album
            result.songName = songName
            result.videoLyricsPath = videoLyricsPath
            result.videoKaraokePath = videoKaraokePath
            result.beat = beat

            // В его объект Subtitles кладём список объектов Subtitle
            result.subtitles = subtitles
            // Выводим на экран список объектов Subtitle
            result.subtitles.forEach {
                println(it)
            }
            println("BPM = $bpm")
            println("KEY = $key")
            println("end = ${result.end}")
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
    val isLineEnd: Boolean? = null,
    val isBeat: Boolean = false
)
package com.svoemesto.karaokeapp

import com.google.gson.GsonBuilder
import com.svoemesto.karaokeapp.mlt.*
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
import org.apache.commons.io.FileUtils
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.odftoolkit.simple.SpreadsheetDocument
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.awt.*
import java.awt.image.BufferedImage
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import javax.sound.sampled.AudioSystem
import kotlin.io.path.Path
import kotlin.math.roundToInt
import kotlin.random.Random


fun main() {

//    updateBpmAndKey()

//    Settings.createFromPath("/home/nsa/Documents/Караоке/По заявкам/Дом Кукол")
//    markDublicates("Несчастный Случай")
//    delDublicates()
//    markDublicates("Блуждающие Огни")
//    markDublicates("Глеб Самойлоff & The MatriXX")
//    markDublicates("Несчастный Случай")
//    markDublicates("Белая Гвардия")
//    markDublicates("Спектакль Джо")
//    markDublicates("Ленинград")
//    markDublicates("Эпидемия")
//    markDublicates("Тимур Шаов")
//    markDublicates("Гражданская Оборона")
//    markDublicates("Хитобои")
//    markDublicates("Дореволюціонный совѣтчикъ")
//    markDublicates("Бригадный подряд")

//    createDigestForAllAuthors()
//    createDigestForAllAuthorsForOper()


//    collectDoneFilesToStoreFolderAndCreate720pForAllUncreated()

//    testUpdateSongFromCloud()

//    collectYo()

//    createDzenPicture("/home/nsa/Documents/Караоке/Несчастный Случай")
//    createDzenPicture("/home/nsa/Documents/Караоке/Блуждающие Огни")
//    createDzenPicture("/home/nsa/Documents/Караоке/Глеб Самойлоff & The MatriXX")
//    createDzenPicture("/home/nsa/Documents/Караоке/Белая Гвардия")
//    createDzenPicture("/home/nsa/Documents/Караоке/Спектакль Джо")
//    createDzenPicture("/home/nsa/Documents/Караоке/Ленинград")
//    createDzenPicture("/home/nsa/Documents/Караоке/Эпидемия")
//    createDzenPicture("/home/nsa/Documents/Караоке/Тимур Шаов")
//    createDzenPicture("/home/nsa/Documents/Караоке/Гражданская Оборона")
//    createDzenPicture("/home/nsa/Documents/Караоке/Хитобои")
//    createDzenPicture("/home/nsa/Documents/Караоке/Дореволюціонный совѣтчикъ")
//    createDzenPicture("/home/nsa/Documents/Караоке/Бригадный подряд")
//    createDzenPicture("/home/nsa/Documents/Караоке/Звери")
//    createDzenPicture("/home/nsa/Documents/Караоке/КИНО")
//    createDzenPicture("/home/nsa/Documents/Караоке/КняZz")
//    createDzenPicture("/home/nsa/Documents/Караоке/Король и Шут")
//    createDzenPicture("/home/nsa/Documents/Караоке/Кукрыниксы")
//    createDzenPicture("/home/nsa/Documents/Караоке/Мельница")
//    createDzenPicture("/home/nsa/Documents/Караоке/Неприкасаемые")
//    createDzenPicture("/home/nsa/Documents/Караоке/Павел Кашин")
//    createDzenPicture("/home/nsa/Documents/Караоке/Пикник")
//    createDzenPicture("/home/nsa/Documents/Караоке/Сектор Газа")
//    createDzenPicture("/home/nsa/Documents/Караоке/СерьГа")
//    createDzenPicture("/home/nsa/Documents/Караоке/Сплин")
//    createDzenPicture("/home/nsa/Documents/Караоке/Ундервуд")
//    createDzenPicture("/home/nsa/Documents/Караоке/Чайф")
//    createDzenPicture("/home/nsa/Documents/Караоке/ЭЛЕКТРОСЛАБОСТЬ")
//    createDzenPicture("/home/nsa/Documents/Караоке/Юнона и Авось")

}

fun customFunction(): String {
    var result = ""

//    Settings.loadListFromDb(mapOf("publish_date" to ">06.12.23"))
//        .filter { it.statusProcessKaraoke == "WAITING" && it.statusProcessLyrics == "WAITING" && it.date != ""}
//        .forEach { settings ->
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 100)
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 100)
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 100)
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_KAR, true, 100)
//        }
//
//    Settings.loadListFromDb(mapOf("publish_date" to "21.11.23<"))
//        .filter { it.statusProcessKaraoke == "WAITING" && it.statusProcessLyrics == "WAITING" && it.date != ""}
//        .forEach { settings ->
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 100)
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 100)
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 100)
//            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_KAR, true, 100)
//        }

    return result
}

fun <T : java.io.Serializable> deepCopy(obj: T?): T? {
    if (obj == null) return null
    val baos = ByteArrayOutputStream()
    val oos  = ObjectOutputStream(baos)
    oos.writeObject(obj)
    oos.close()
    val bais = ByteArrayInputStream(baos.toByteArray())
    val ois  = ObjectInputStream(bais)
    @Suppress("unchecked_cast")
    return ois.readObject() as T
}
fun organizeUnpublished() {

    val listUnpublished =
        Settings.loadListFromDb(mapOf("publish_date" to "-", "publish_time" to "-"))
            .groupBy { it.author }
            .map { Pair(it.key, it.value.size) }

    var slotes: MutableList<Pair<String, Long>> = mutableListOf()
    slotes.add("11:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "11:00")).last().date).time)
    slotes.add("12:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "12:00")).last().date).time)
    slotes.add("13:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "13:00")).last().date).time)
    slotes.add("14:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "14:00")).last().date).time)
    slotes.add("15:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "15:00")).last().date).time)
    slotes.add("16:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "16:00")).last().date).time)
    slotes.add("17:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "17:00")).last().date).time)
    slotes.add("18:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "18:00")).last().date).time)
    slotes.add("19:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "19:00")).last().date).time)
    slotes.add("20:00" to SimpleDateFormat("dd.MM.yy").parse(Settings.loadListFromDb(mapOf("publish_time" to "20:00")).last().date).time)
    val minDate = slotes.minOfOrNull { it.second } ?: 0L
    slotes = slotes.map {Pair(it.first, it.second - minDate) }.toMutableList()

}

fun updateBpmAndKey(): Int {
    val listSettings = Settings.loadListFromDb(mapOf("song_tone" to "''", "song_bpm" to "0"))
    var counter = 0
    listSettings.forEach { settings ->
        val (bpm, key) = getBpmAndKeyFromCsv(settings)
        if (bpm != 0L && key != "") {
            println("${settings.fileName} : bpm = ${bpm}, tone = ${key}")
            settings.fields[SettingField.BPM] = bpm.toString()
            settings.fields[SettingField.KEY] = key
            settings.saveToDb()
            counter++
        }
    }
    return counter
}

fun getBpmAndKeyFromCsv(settings: Settings): Pair<Long, String> {
    var csvFilePath = settings.rootFolder + "/key_bpm.csv"
    var file = File(csvFilePath)
    if (!file.exists()) {
        csvFilePath = Path(settings.rootFolder).parent.toString() + "/key_bpm.csv"
        file = File(csvFilePath)
        if (!file.exists()) {
            return Pair(0, "")
        }
    }

    try {
        FileReader(csvFilePath).use { fileReader ->
            val csvParser = CSVParser(fileReader, CSVFormat.DEFAULT)

            // Проходимся по записям CSV и читаем данные
            for (csvRecord in csvParser) {
                val fileName = csvRecord.get(0)
                val bpm = csvRecord.get(3)
                val key = csvRecord.get(4)
                if (fileName == settings.fileName + ".flac") {
                    return Pair(bpm.toLong(), key)
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }


    return Pair(0, "")

}



fun delDublicates(): Int {
    var counter = 0
    val listSettings = Settings.loadListFromDb(
        mapOf(Pair("tags", "DD"))
    )
    listSettings.forEach { settings ->
        if (settings.tags == "DD") {
            settings.deleteFromDb()
            counter++
        }
    }
    return counter
}

fun clearPreDublicates(): Int {
    var counter = 0
    val listSettings = Settings.loadListFromDb(
        mapOf(Pair("tags", "D"))
    )
    listSettings.forEach { settings ->
        if (settings.tags == "D") {
            settings.tags = ""
            settings.saveToDb()
            counter++
        }
    }
    return counter
}

fun markDublicates(autor: String): Int {
    var counter = 0
    val listSettings = Settings.loadListFromDb(
        mapOf(Pair("song_author", autor))
    )
    listSettings.forEach { settings ->
        if (settings.tags == "") {
            val listDoubles = listSettings.filter {
                it.songName == settings.songName && it.id > settings.id
            }
            if (listDoubles.isNotEmpty()) {
                settings.tags = "D"
                settings.saveToDb()
                listDoubles.forEach{
                    it.tags = "DD"
                    it.saveToDb()
                    counter++
                }
            }
        }
    }
    return counter
}

fun create720pForAllUncreated() {

    val settingsList = Settings.loadListFromDb()
    settingsList.forEach { settings ->
        if (File(settings.pathToFileLyrics).exists() && !File(settings.pathToFile720Lyrics).exists()) {
            if (!File(settings.pathToFolder720Lyrics).exists()) Files.createDirectories(Path(settings.pathToFolder720Lyrics))
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileLyrics}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 1)
        }
        if (File(settings.pathToFileKaraoke).exists() && !File(settings.pathToFile720Karaoke).exists()) {
            if (!File(settings.pathToFolder720Karaoke).exists()) Files.createDirectories(Path(settings.pathToFolder720Karaoke))
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileKaraoke}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_KAR, true, 1)
        }
    }

}


fun copyIfNeed(pathFrom: String, pathTo: String, folderTo: String, log: String = ""): Boolean {
    val fileFrom = File(pathFrom)
    val fileTo = File(pathTo)
    if (fileFrom.exists()) {
        if (!fileTo.exists() || (fileFrom.length() != fileTo.length())) {
            if (!File(folderTo).exists()) Files.createDirectories(Path(folderTo))
            if (log != "") println(log)
            Files.copy(Path(pathFrom), Path(pathTo), StandardCopyOption.REPLACE_EXISTING)
            return true
        }
    }
    return false
}

fun collectDoneFilesToStoreFolderAndCreate720pForAllUncreated() {
    println("Копирование в хранилище и создание заданий на кодирование в 720р")
    val settingsList = Settings.loadListFromDb()
    settingsList.forEach { settings ->
        if (copyIfNeed(settings.pathToFileLyrics, settings.pathToStoreFileLyrics, settings.pathToStoreFolderLyrics, "Копируем в хранилище файл: ${settings.nameFileLyrics}")) {
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileLyrics}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 1)
        }
        if (copyIfNeed(settings.pathToFileKaraoke, settings.pathToStoreFileKaraoke, settings.pathToStoreFolderKaraoke, "Копируем в хранилище файл: ${settings.nameFileKaraoke}")) {
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileKaraoke}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_KAR, true, 1)
        }
        copyIfNeed(settings.pathToFileChords, settings.pathToStoreFileChords, settings.pathToStoreFolderChords, "Копируем в хранилище файл: ${settings.nameFileChords}")


//        if (File(settings.pathToFileLyricsBt).exists() && !File(settings.pathToStoreFileLyricsBt).exists()) {
//            if (!File(settings.pathToStoreFolderLyricsBt).exists()) Files.createDirectories(Path(settings.pathToStoreFolderLyricsBt))
//            println("Копируем в хранилище файл: ${settings.nameFileLyricsBt}")
//            Files.copy(Path(settings.pathToFileLyricsBt), Path(settings.pathToStoreFileLyricsBt))
//        }
//        if (File(settings.pathToFileKaraokeBt).exists() && !File(settings.pathToStoreFileKaraokeBt).exists()) {
//            if (!File(settings.pathToStoreFolderKaraokeBt).exists()) Files.createDirectories(Path(settings.pathToStoreFolderKaraokeBt))
//            println("Копируем в хранилище файл: ${settings.nameFileKaraokeBt}")
//            Files.copy(Path(settings.pathToFileKaraokeBt), Path(settings.pathToStoreFileKaraokeBt))
//        }
//        if (File(settings.pathToFileChordsBt).exists() && !File(settings.pathToStoreFileChordsBt).exists()) {
//            if (!File(settings.pathToStoreFolderChordsBt).exists()) Files.createDirectories(Path(settings.pathToStoreFolderChordsBt))
//            println("Копируем в хранилище файл: ${settings.nameFileChordsBt}")
//            Files.copy(Path(settings.pathToFileChordsBt), Path(settings.pathToStoreFileChordsBt))
//        }
    }
}

class ResourceReader {
    fun readTextResource(filename: String): String {
        val uri = this.javaClass.getResource("/$filename").toURI()
        return Files.readString(Paths.get(uri))
    }
}

fun replaceSymbolsInSong(sourceText: String): String {
    var result = sourceText.addNewLinesByUpperCase()

    val yo = YoWordsDictionary().dict

    yo.forEach { wordWithYO ->
        val replacedWord = wordWithYO.replace("ё", "е")
        val patt1 = "\\b$replacedWord\\b".toRegex()
        result = result.replace(patt1, wordWithYO)
        val capWordWithYO = wordWithYO.uppercaseFirstLetter()
        val capReplacedWord = capWordWithYO.replace("ё", "е")
        val patt2 = "\\b$capReplacedWord\\b".toRegex()
        result = result.replace(patt2, capWordWithYO)
    }

    result = result.replaceQuotes()

    result = result.replace(",",", ")
    result = result.replace(",  ",", ")
    result = result.replace("--","-")
    result = result.replace("—","-")
    result = result.replace("–","-")
    result = result.replace("−","-")
    result = result.replace(" - ","_- ")
    result = result.replace(" -\n","_-\n")



    return result
}

fun createFilesByTags(listOfTags: List<String> = emptyList()) {
    val listTags = (if (listOfTags.isEmpty()) Settings.getSetOfTags() else listOfTags.map { it.uppercase() }.toSet()).toList()
    listTags.forEach { tag ->

        val pathToTagFolder = "$PATH_TO_STORE_FOLDER/TAGS/${tag}"
        if (!File(pathToTagFolder).exists()) Files.createDirectories(Path(pathToTagFolder))

        val pathToTagFolder720Karaoke = "$PATH_TO_STORE_FOLDER/720p_Karaoke/TAGS/${tag}"
        if (!File(pathToTagFolder720Karaoke).exists()) Files.createDirectories(Path(pathToTagFolder720Karaoke))

        val listOfSettings = Settings.loadListFromDb(mapOf(Pair("tags", tag)))
        listOfSettings.forEach { settings ->
            val sourceFileKaraoke = settings.pathToFileKaraoke
            if (File(sourceFileKaraoke).exists()) {
                val destinationFile = pathToTagFolder + "/" + sourceFileKaraoke.split("/").last().replace(" [karaoke].mp4", " [karaoke] {${tag}}.mp4")
                if (!File(destinationFile).exists()) {
                    Files.copy(Path(sourceFileKaraoke), Path(destinationFile))
                }
            }

            val sourceFile720Karaoke = settings.pathToFile720Karaoke
            if (File(sourceFile720Karaoke).exists()) {
                val destinationFile = pathToTagFolder720Karaoke + "/" + sourceFile720Karaoke.split("/").last().replace(" [karaoke] 720p.mp4", " [karaoke] {${tag}} 720p.mp4")
                if (!File(destinationFile).exists()) {
                    Files.copy(Path(sourceFile720Karaoke), Path(destinationFile))
                }
            }
        }

    }
}

fun createDigestForAllAuthors(vararg authors: String) {

    val listAuthors = getAuthorsForDigest()
    listAuthors.forEach { author ->
        if (authors.isEmpty() || author in authors) {
            var txt = "ЗАКРОМА - «$author»\n\n${getAuthorDigest(author, false).first}"
            val fileName = "/home/nsa/Documents/Караоке/Digest/${author} (digest).txt"
            File(fileName).writeText(txt, Charsets.UTF_8)
        }
    }

}

fun createDigestForAllAuthorsForOper(vararg authors: String) {

    val listAuthors = getAuthorsForDigest()
    var txt = ""
    var total = 0
    listAuthors.forEach { author ->
        if (authors.isEmpty() || author in authors) {
            val (digest, count) = getAuthorDigest(author, false)
            if (digest.isNotEmpty()) {
                txt += "«$author»\nПесен: $count шт.\n[spoiler]\n${digest}[/spoiler]\n\n"
                total += count
            }
        }
    }
    txt = "----------ЗАКРОМА----------\nВсего песен: $total шт.\n\n" + txt
    val fileName = "/home/nsa/Documents/Караоке/Digest/OPER_digest.txt"
    File(fileName).writeText(txt, Charsets.UTF_8)
}

fun getAuthorsForDigest(): List<String> {

    Class.forName("org.postgresql.Driver")
    val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
    var statement: Statement? = null
    var rs: ResultSet? = null
    var sql: String

    try {
        statement = connection.createStatement()
        sql = "select song_author, count(DISTINCT song_album) as albums, count(DISTINCT id) as songs " +
                "from tbl_settings " +
//                "where id_boosty != '' AND id_boosty IS NOT NULL AND root_folder NOT LIKE '%/Разное/%' " +
                "where id_boosty != '' AND id_boosty IS NOT NULL " +
                "group by song_author"

        rs = statement.executeQuery(sql)
        val result: MutableList<String> = mutableListOf()
        while (rs.next()) {
            val author = rs.getString("song_author")
            result.add(author)
        }
        result.sort()
        return result
    } catch (e: SQLException) {
        e.printStackTrace()
    } finally {
        try {
            rs?.close() // close result set
            statement?.close() // close statement
            connection?.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }
    return emptyList()

}

fun getAuthorDigest(author: String, withRazor: Boolean = true): Pair<String, Int> {

    val MAX_SYMBOLS = 16300

    val listDigest = Settings.loadListFromDb(mapOf(Pair("song_author", author)))
        .filter { it.digestIsFull }
        .map { it.digest }

    var result = ""
    var counter = 0

    listDigest.forEach { digets ->
        if (withRazor && (counter + digets.length > MAX_SYMBOLS)) {
            result += "\n(ПРОДОЛЖЕНИЕ - В КОММЕНТАРИЯХ)\n\n----------------------------------------------------------------------------------------\n\n\n"
            counter = 0
        }
        result += digets + "\n"
        counter += digets.length
    }

    return result to listDigest.size
}

fun searchSongText2(settings: Settings) {

    val searchQuery = "${settings.author} ${settings.songName}"
    val searchUrl = "https://www.google.com/search?q=${searchQuery.replace(" ", "+")}+текст+песни"

    // Загрузка страницы результатов поиска
    val document = Jsoup.connect(searchUrl).get()

    val links: List<Element> = document.select("a")

    // Пройтись по найденным ссылкам и вывести их href (URL)
    for (link in links) {
        val href = link.attr("href")
        println(href)
    }

}

fun searchSongText(settings: Settings): String {

    val searchQuery = "${settings.author} ${settings.songName}".replace("&", "")
    val searchUrl = "https://www.google.com/search?q=${searchQuery.replace(" ", "+")}+текст+песни"

    // Загрузка страницы результатов поиска
    var document = Jsoup.connect(searchUrl).get()


    // Поиск текста песни на странице результатов
    var lyricsElement = document.selectFirst("div[data-lyricid]")

    println(lyricsElement?.text())

    lyricsElement?.let { le ->
        val spanElements = le.select("span")
        val spanTexts = spanElements?.map { it.ownText() }
        spanTexts?.let { st ->
            return spanTexts.joinToString("\n")
        }
    }

    val links: List<Element> = document.select("a")

    println("Ссылок найдено: ${links.size}")

    for (link in links) {
        if (link.attr("href").startsWith("http")) {
            println(link.attr("href"))
        }

    }
    // Пройтись по найденным ссылкам и вывести их href (URL)
    for (link in links) {
        val href = link.attr("href")
        if (href.startsWith("https://learnsongs.ru/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.getElementById("tab01")
//            println(lyricsElement?.ownText())
//            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> &nbsp;", "")
                text = text.replace("<br> ", "")
                text = text.replace("<br>", "")
                return text
            }

        } else if (href.startsWith("https://textypesen.com/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.selectFirst("div.col-sm-100.text-center")
            println(lyricsElement?.ownText())
            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")
                text = text.replace("&nbsp;", " ")
                text = text.replace("<p class=\"font-size-20\">", "")
                text = text.replace("</p>", "")
                println(text)
                return text
            }

        } else if (href.startsWith("https://musictxt.ru/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h1Elements = document.select("h1")
            for (h1Element in h1Elements) {
                h1Element.remove()
            }

            val aElements = document.select("a")
            for (aElements in aElements) {
                aElements.remove()
            }

            lyricsElement = document.getElementById("layer2")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")
                text = text.replace("<!-- Yandex.RTB R-A-587487-5 -->", "")
                text = text.replace("""<div id="yandex_rtb_R-A-587487-5"></div><script>window.yaContextCb.push(()=>{Ya.Context.AdvManager.render({"blockId": "R-A-587487-5","renderTo": "yandex_rtb_R-A-587487-5"})})</script></pre>""", "")
                text = text.replace("<pre>", "")
                println(text)
                return text
            }

        } else if (href.startsWith("https://textocat.ru/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h1Elements = document.select("h1")
            for (h1Element in h1Elements) {
                h1Element.remove()
            }

            val aElements = document.select("a")
            for (aElements in aElements) {
                aElements.remove()
            }

            lyricsElement = document.selectFirst("div.entry-content")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.text()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")

                println(text)
                return text
            }


        } else if (href.startsWith("https://txtsong.ru/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.selectFirst("div.the_content")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.text()

            if (text != null) {
                println(text)
                return text
            }

        } else if (href.startsWith("https://pesni.guru/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.selectFirst("div.songtext")
            println(lyricsElement?.text())
            println(lyricsElement?.ownText())
            println(lyricsElement?.html())

            var text = lyricsElement?.ownText()

            if (text != null) {
                return text
            }

        } else if (href.startsWith("https://teksti-pesenok.pro/")) {

            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.getElementById("text")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {

                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")
                text = text.replace("&nbsp;", " ")
                text = text.replace("""<span class="status_select" itemprop="lyrics">""", "")
                text = text.replace("</span>", "")

                return text
            }
        } else if (href.startsWith("https://text-lyrics.ru/")) {

            println(href)

            try {
                document = Jsoup.connect(href).get()
                lyricsElement = document.selectFirst("div.entry_content")
                println("text()")
                println(lyricsElement?.text())
                println("ownText()")
                println(lyricsElement?.ownText())
                println("html()")
                println(lyricsElement?.html())

                var text = lyricsElement?.text()
                if (text != null) {
                    println(text)
                    return text
                }
            } catch (e: Exception) {
                return ""
            }



        }

    }

    return ""
}


fun updateSettingsFromDb(startFolder: String) {
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        println("updateSettingsFromDb: $pathToSettingsFile")
        val settings = Settings.loadFromFile(pathToSettingsFile)
        settings.saveToDb()
    }
}


fun testSoundLib() {
    val audioFile = File("/home/nsa/Documents/Караоке/Nautilus Pompilius/1997 - Атлантида/1997 (02) [Nautilus Pompilius] - Умершие во сне.flac")
//    val audioFile = File("/home/nsa/Documents/Караоке/Nautilus Pompilius/2001 - Яблокитай/2001 (03) [Nautilus Pompilius] - Странники в ночи.flac")
    val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
    val format = audioInputStream.format
    val durationInSeconds = audioInputStream.frameLength / format.sampleRate
    println("audioInputStream.frameLength: ${audioInputStream.frameLength}")
    println("format.sampleRate: ${format.sampleRate}")
    println("Duration of audio file: $durationInSeconds seconds")
}

fun createRunMlt(startFolder: String) {
    val listFiles = getListFiles(startFolder,"settings")
    val albums = listFiles.groupBy { File(it).parentFile.absolutePath }
    var authorTxtAll = ""
    var authorTxtLyrics = ""
    var authorTxtWOLyrics = ""
    val permissions = PosixFilePermissions.fromString("rwxr-x---")

    albums.forEach{(albumPath, songs) ->
        var albumTxtAll = ""
        var albumTxtLyrics = ""
        var albumTxtWOLyrics = ""
        Files.createDirectories(Path("$albumPath/done_projects"))
        songs.forEach { pathToSettingsFile ->

            println(pathToSettingsFile)

            val settings = Settings.loadFromFile(pathToSettingsFile)
            val songLyrics = Song(settings, SongVersion.LYRICS, woInit = true)
            val songKaraoke = Song(settings, SongVersion.KARAOKE, woInit = true)
            val songChords = Song(settings, SongVersion.CHORDS, woInit = false)
            val hasChords = songChords.hasChords
            val pathToMltLyrics = songLyrics.getOutputFilename(SongOutputFile.MLT)
            val pathToMltKaraoke = songKaraoke.getOutputFilename(SongOutputFile.MLT)
            val pathToMltChords = songChords.getOutputFilename(SongOutputFile.MLT)

            val txtLyric = "echo \"$pathToMltLyrics\"\nmelt -progress \"$pathToMltLyrics\"\n\n"
            val txtKaraoke = "echo \"$pathToMltKaraoke\"\nmelt -progress \"$pathToMltKaraoke\"\n\n"
            val txtChords = "echo \"$pathToMltChords\"\nmelt -progress \"$pathToMltChords\"\n\n"

            val songTxtAll = "$txtLyric$txtKaraoke${if (hasChords) txtChords else ""}"
            val songTxtWOLyrics = "$txtKaraoke${if (hasChords) txtChords else ""}"

            albumTxtAll += "echo \"---------------------------------------------------------------------------------------\"\n\n"
            albumTxtLyrics += "echo \"---------------------------------------------------------------------------------------\"\n\n"
            albumTxtWOLyrics += "echo \"---------------------------------------------------------------------------------------\"\n\n"
            albumTxtAll += songTxtAll
            albumTxtLyrics += txtLyric
            albumTxtWOLyrics += songTxtWOLyrics


            var file = File(songLyrics.getOutputFilename(SongOutputFile.RUN))
            file.writeText(txtLyric)
            Files.setPosixFilePermissions(file.toPath(), permissions)


            file = File(songKaraoke.getOutputFilename(SongOutputFile.RUN))
            file.writeText(txtKaraoke)
            Files.setPosixFilePermissions(file.toPath(), permissions)

            if (hasChords) {
                file = File(songChords.getOutputFilename(SongOutputFile.RUN))
                file.writeText(txtChords)
                Files.setPosixFilePermissions(file.toPath(), permissions)
            }

            file = File(songLyrics.getOutputFilename(SongOutputFile.RUNALL).replace("[lyrics]","[ALL]"))
            file.writeText(songTxtAll)
            Files.setPosixFilePermissions(file.toPath(), permissions)

            file = File(songLyrics.getOutputFilename(SongOutputFile.RUNALL).replace("[lyrics]","[ALLwoLYRICS]"))
            file.writeText(songTxtWOLyrics)
            Files.setPosixFilePermissions(file.toPath(), permissions)

        }

        authorTxtAll += "echo \"===========================================================================================\"\n\n"
        authorTxtWOLyrics += "echo \"===========================================================================================\"\n\n"

        authorTxtAll += albumTxtAll
        authorTxtLyrics += albumTxtLyrics
        authorTxtWOLyrics += albumTxtWOLyrics

        var file = File("$albumPath/[ALL].run")
        file.writeText(albumTxtAll)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File("$albumPath/[ALLwoLYRICS].run")
        file.writeText(albumTxtWOLyrics)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File("$albumPath/[LYRICS].run")
        file.writeText(albumTxtLyrics)
        Files.setPosixFilePermissions(file.toPath(), permissions)

    }

    var file = File("$startFolder/[ALL].run")
    file.writeText(authorTxtAll)
    Files.setPosixFilePermissions(file.toPath(), permissions)

    file = File("$startFolder/[ALLwoLYRICS].run")
    file.writeText(authorTxtWOLyrics)
    Files.setPosixFilePermissions(file.toPath(), permissions)

    file = File("$startFolder/[LYRICS].run")
    file.writeText(authorTxtLyrics)
    Files.setPosixFilePermissions(file.toPath(), permissions)

}

fun createVKtext(startFolder: String, fromDb: Boolean = false) {
    val spreadsheetDocument = if (fromDb) SpreadsheetDocument.loadDocument(File(PATH_TO_ODS)) else null
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        val settings = Settings.loadFromFile(pathToSettingsFile)
        val song = Song(settings, SongVersion.LYRICS)
        val fileName = song.getOutputFilename(SongOutputFile.VK)
        val decsAndName = Ods.getSongVKDescription(song, fileName, spreadsheetDocument)
        decsAndName?.let { (text, name) ->
            if (text != "") {
                println(name)
                File(name).writeText(text)
                val vkPictNameOld = (song.getOutputFilename(SongOutputFile.PICTUREVK)).replace(" [lyrics] VK"," [VK]")
                val vkPictNameNew = name.replace(" [VK].txt", " [VK].png")
                FileUtils.copyFile(File(vkPictNameOld), File(vkPictNameNew))
            } else {
                return@forEach
            }
        }
    }
    if (fromDb) spreadsheetDocument!!.close()
}
fun createBoostyTeserPictures(startFolder: String) {
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        val settings = Settings.loadFromFile(pathToSettingsFile)
        val song = Song(settings, SongVersion.LYRICS)
        println(pathToSettingsFile)
        createBoostyTeaserPicture(song, song.getOutputFilename(SongOutputFile.PICTUREBOOSTY))
    }
}

fun createRunToDecodeKaraokeTo720p(runFileName: String, sourceFolder: String, destinationFolder: String) {
    val listFiles = getListFiles(sourceFolder, " [karaoke].mp4")
    var txt = ""
    val permissions = PosixFilePermissions.fromString("rwxr-x---")
    Files.createDirectories(Path(destinationFolder))
    listFiles.forEach { sourceFile ->
        val destinationFile = destinationFolder + "/" + sourceFile.split("/").last().replace(" [karaoke].mp4", " [karaoke] 720p.mp4")
        txt += "ffmpeg -i \"${sourceFile}\" -c:v hevc_nvenc -preset fast -b:v 1000k -vf \"scale=1280:720,fps=30\" -c:a aac \"${destinationFile}\" -y\n"
    }
    val file = File(runFileName)
    file.writeText(txt)
    Files.setPosixFilePermissions(file.toPath(), permissions)
}

fun createVKPictures(startFolder: String) {
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        val settings = Settings.loadFromFile(pathToSettingsFile)
        val song = Song(settings, SongVersion.LYRICS)
        println(pathToSettingsFile)
        createVKPicture(song, song.getOutputFilename(SongOutputFile.PICTUREVK))
    }
}

fun createDescriptionFilesForAll(startFolder: String) {

    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->

        println(pathToSettingsFile)

        try {
            val settings = Settings.loadFromFile(pathToSettingsFile)
            val songLyric = Song(settings, SongVersion.LYRICS)
            val songKaraoke = Song(settings, SongVersion.KARAOKE)
            val songChords = Song(settings, SongVersion.CHORDS)


            File(songLyric.getOutputFilename(SongOutputFile.DESCRIPTION)).writeText(songLyric.getDescription())

            File(songKaraoke.getOutputFilename(SongOutputFile.DESCRIPTION)).writeText(songKaraoke.getDescription())

            if (songChords.hasChords) {
                File(songChords.getOutputFilename(SongOutputFile.DESCRIPTION)).writeText(songChords.getDescription())
            }
        } catch (e: Exception) {
            println("Ошибка, продолжаем...")
        }
    }
}



fun createSettingsFilesForAll(startFolder: String) {
    val patternFileName = "(\\d{4}).*\\s\\((\\d{2})\\)\\s\\[(.*)\\]\\s-\\s(.*)\\.(.*)"
    val regexpFileName = Regex(patternFileName)
    val patternFolderName = "(\\d{4}).*\\s-\\s(.*)"
    val regexpFolderName = Regex(patternFolderName)
    val permissions = PosixFilePermissions.fromString("rwxr-x---")

    val fileDemucs2tracks = File("$startFolder/demusc2track.run")
    val fileDemucs4tracks = File("$startFolder/demusc4track.run")
    val fileDemucs5tracks = File("$startFolder/demusc5track.run")
    val fileMainPairs = File("$startFolder/mainPairs.txt")
    val fileMelt = File("$startFolder/melt.txt")

    var textFileDemucs2tracks = ""
    var textFileDemucs4tracks = ""
    var textFileDemucs5tracks = ""
    var textFileMainPairs = ""
    var textFileMelt = ""

    val listFiles = getListFiles(
        pathToFolder =  startFolder,
        extentions = listOf("flac"),
        excludes = listOf("-accompaniment", "-bass", "-drums", "-guitars", "-metronome", "[music", "[drums", "[bass", "[vocals]", "-vocals", "-other")
    )
    println("Всего файлов = ${listFiles.size}")
    listFiles.map{File(it)}.forEach { file ->
        val fileName = file.name

        val fileAbsolutePath = file.absolutePath
        val fileFolder = file.parent
        val folderName = file.parentFile.name
        val fileNamesMatchResult = regexpFileName.findAll(fileName).toList().firstOrNull()
        val folderNamesMatchResult = regexpFolderName.findAll(folderName).toList().firstOrNull()

        val songYear = fileNamesMatchResult?.let {fileNamesMatchResult.groups[1]?.value}
        val songTrack = fileNamesMatchResult?.let {fileNamesMatchResult.groups[2]?.value}
        val songAuthor = fileNamesMatchResult?.let {fileNamesMatchResult.groups[3]?.value}
        var songName = fileNamesMatchResult?.let {fileNamesMatchResult.groups[4]?.value}
        val songFormat = file.extension //    extention //fileNamesMatchResult?.let {fileNamesMatchResult.groups[5]?.value}
        val songAlbum = folderNamesMatchResult?.let {folderNamesMatchResult.groups[2]?.value}
        val settingFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".settings"
        val textFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".txt"
        val kdenliveFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".kdenlive"
        val kdenliveSubsFileName = fileAbsolutePath.substring(0,fileAbsolutePath.length-songFormat!!.length-1)+".kdenlive.srt"
        val fileNameWOExt = fileName.substring(0, fileName.length-songFormat!!.length-1)

        if (songName != null) {
            songName = songName.uppercaseFirstLetter()
        }

        println("Year = $songYear")
        println("Track = $songTrack")
        println("Author = $songAuthor")
        println("Name = $songName")
        println("Format = $songFormat")
        println("Album = $songAlbum")
        println("settingFileName = $settingFileName")
        println()

        val pathToResultedModel = "$fileFolder/$DEMUCS_MODEL_NAME"
        val separatedStem = "vocals"
        val oldNoStemNameWav = "$pathToResultedModel/${fileNameWOExt}-no_$separatedStem.wav"
        val newNoStemNameWav = "$pathToResultedModel/${fileNameWOExt}-accompaniment.wav"
        val newNoStemNameFlac = "$pathToResultedModel/${fileNameWOExt}-accompaniment.flac"
        val vocalsNameWav = "$pathToResultedModel/${fileNameWOExt}-vocals.wav"
        val vocalsNameFlac = "$pathToResultedModel/${fileNameWOExt}-vocals.flac"
        val drumsNameWav = "$pathToResultedModel/${fileNameWOExt}-drums.wav"
        val drumsNameFlac = "$pathToResultedModel/${fileNameWOExt}-drums.flac"
        val bassNameWav = "$pathToResultedModel/${fileNameWOExt}-bass.wav"
        val bassNameFlac = "$pathToResultedModel/${fileNameWOExt}-bass.flac"
        val guitarsNameWav = "$pathToResultedModel/${fileNameWOExt}-guitars.wav"
        val guitarsNameFlac = "$pathToResultedModel/${fileNameWOExt}-guitars.flac"
        val otherNameWav = "$pathToResultedModel/${fileNameWOExt}-other.wav"
        val otherNameFlac = "$pathToResultedModel/${fileNameWOExt}-other.flac"

        val textDemucs2track = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" --two-stems=$separatedStem -o \"$fileFolder\" \"$fileAbsolutePath\"\n" +
                "mv \"$oldNoStemNameWav\" \"$newNoStemNameWav\"" + "\n" +
                "ffmpeg -i \"$newNoStemNameWav\" -compression_level 8 \"$newNoStemNameFlac\" -y" + "\n" +
                "rm \"$newNoStemNameWav\"" + "\n" +
                "ffmpeg -i \"$vocalsNameWav\" -compression_level 8 \"$vocalsNameFlac\" -y" + "\n" +
                "rm \"$vocalsNameWav\"" + "\n"

        val textDemucs4track = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" -o \"$fileFolder\" \"$fileAbsolutePath\"\n" +
                "ffmpeg -i \"$drumsNameWav\" -compression_level 8 \"$drumsNameFlac\" -y" + "\n" +
                "rm \"$drumsNameWav\"" + "\n" +
                "ffmpeg -i \"$bassNameWav\" -compression_level 8 \"$bassNameFlac\" -y" + "\n" +
                "rm \"$bassNameWav\"" + "\n" +
                "ffmpeg -i \"$guitarsNameWav\" -compression_level 8 \"$guitarsNameFlac\" -y" + "\n" +
                "rm \"$guitarsNameWav\"" + "\n" +
                "ffmpeg -i \"$otherNameWav\" -compression_level 8 \"$otherNameFlac\" -y" + "\n" +
                "rm \"$otherNameWav\"" + "\n" +
                "ffmpeg -i \"$vocalsNameWav\" -compression_level 8 \"$vocalsNameFlac\" -y" + "\n" +
                "rm \"$vocalsNameWav\"" + "\n"

        textFileDemucs2tracks += textDemucs2track
        textFileDemucs4tracks += textDemucs4track

        textFileDemucs5tracks += textDemucs2track
        textFileDemucs5tracks += textDemucs4track

        textFileMainPairs += "//        Pair(\"$fileFolder\",\"$fileNameWOExt\"),\n"

        textFileMelt += "echo \"$fileFolder/done_projects/$fileNameWOExt [lyrics].mlt\"\n"
        textFileMelt += "melt -progress \"$fileFolder/done_projects/$fileNameWOExt [lyrics].mlt\"\n\n"
        textFileMelt += "echo \"$fileFolder/done_projects/$fileNameWOExt [karaoke].mlt\"\n"
        textFileMelt += "melt -progress \"$fileFolder/done_projects/$fileNameWOExt [karaoke].mlt\"\n\n"
        textFileMelt += "echo \"$fileFolder/done_projects/$fileNameWOExt [chords].mlt\"\n"
        textFileMelt += "melt -progress \"$fileFolder/done_projects/$fileNameWOExt [chords].mlt\"\n\n"
        textFileMelt += "echo \"$fileFolder/done_projects/$fileNameWOExt [lyrics] bluetooth.mlt\"\n"
        textFileMelt += "melt -progress \"$fileFolder/done_projects/$fileNameWOExt [lyrics] bluetooth.mlt\"\n\n"
        textFileMelt += "echo \"$fileFolder/done_projects/$fileNameWOExt [karaoke] bluetooth.mlt\"\n"
        textFileMelt += "melt -progress \"$fileFolder/done_projects/$fileNameWOExt [karaoke] bluetooth.mlt\"\n\n"
        textFileMelt += "echo \"$fileFolder/done_projects/$fileNameWOExt [chords] bluetooth.mlt\"\n"
        textFileMelt += "melt -progress \"$fileFolder/done_projects/$fileNameWOExt [chords] bluetooth.mlt\"\n\n"
        textFileMelt += "echo \"========================================================================================\"\n"

        val settingFile = File(settingFileName)
        if (!settingFile.exists()) {

            val text =
                "NAME=$songName"+"\n"+
                        "AUTHOR=$songAuthor" + "\n" +
                        "ALBUM=$songAlbum" + "\n" +
                        "YEAR=$songYear" + "\n" +
                        "FORMAT=$songFormat" + "\n" +
                        "TRACK=$songTrack" + "\n" +
                        "KEY=" + "\n" +
                        "BPM=" + "\n"
            settingFile.writeText(text)
        }

        val textFile = File(textFileName)
        if (!textFile.exists()) {
            val text = "\n"
            textFile.writeText(text)
        }

        val fileNameVocals = "${fileNameWOExt}-vocals.flac"
        val fileNameAccompaniment = "${fileNameWOExt}-accompaniment.flac"
        val fullPathToVocals = "${fileFolder}/${DEMUCS_MODEL_NAME}/$fileNameVocals"
        val fullPathToAccompaniment = "${fileFolder}/${DEMUCS_MODEL_NAME}/$fileNameAccompaniment"

//        val audioFile = File("${fileFolder}/$fileName")
//        val audioFile = File(fullPathToAccompaniment)
//        val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
//        val format = audioInputStream.format
//        val durationInMilliseconds = (audioInputStream.frameLength / format.sampleRate * 1000).toLong()
        val durationInMilliseconds = ((MediaInfo.getInfoBySectionAndParameter(
            "${fileFolder}/$fileName",
            "Audio",
            "Duration"
        ) ?: "0.0").toDouble() * 1000).toLong()
        val durationTimecode = convertMillisecondsToTimecode(durationInMilliseconds)
        val durationFrames = convertMillisecondsToFrames(durationInMilliseconds)

        val kdenliveTemplate = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<mlt LC_NUMERIC=\"C\" producer=\"main_bin\" version=\"7.15.0\" root=\"${fileFolder}\">\n" +
                " <profile frame_rate_num=\"60\" sample_aspect_num=\"1\" display_aspect_den=\"9\" colorspace=\"709\" progressive=\"1\" description=\"HD 1080p 60 fps\" display_aspect_num=\"16\" frame_rate_den=\"1\" width=\"1920\" height=\"1080\" sample_aspect_den=\"1\"/>\n" +
                " <producer id=\"producer0\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "  <property name=\"length\">${durationFrames}</property>\n" +
                "  <property name=\"eof\">pause</property>\n" +
                "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameVocals}</property>\n" +
                "  <property name=\"seekable\">1</property>\n" +
                "  <property name=\"audio_index\">0</property>\n" +
                "  <property name=\"video_index\">-1</property>\n" +
                "  <property name=\"mute_on_pause\">1</property>\n" +
                "  <property name=\"mlt_service\">avformat</property>\n" +
                "  <property name=\"kdenlive:clipname\">VOICE</property>\n" +
                "  <property name=\"kdenlive:clip_type\">1</property>\n" +
                "  <property name=\"kdenlive:folderid\">-1</property>\n" +
                "  <property name=\"kdenlive:id\">3</property>\n" +
                " </producer>\n" +
                " <producer id=\"producer1\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "  <property name=\"length\">${durationFrames}</property>\n" +
                "  <property name=\"eof\">pause</property>\n" +
                "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameAccompaniment}</property>\n" +
                "  <property name=\"seekable\">1</property>\n" +
                "  <property name=\"audio_index\">0</property>\n" +
                "  <property name=\"video_index\">-1</property>\n" +
                "  <property name=\"mute_on_pause\">1</property>\n" +
                "  <property name=\"mlt_service\">avformat</property>\n" +
                "  <property name=\"kdenlive:clipname\">MUSIC</property>\n" +
                "  <property name=\"kdenlive:clip_type\">1</property>\n" +
                "  <property name=\"kdenlive:folderid\">-1</property>\n" +
                "  <property name=\"kdenlive:id\">2</property>\n" +
                " </producer>\n" +
                " <playlist id=\"main_bin\">\n" +
                "  <property name=\"kdenlive:docproperties.activeTrack\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.audioChannels\">2</property>\n" +
                "  <property name=\"kdenlive:docproperties.audioTarget\">1</property>\n" +
                "  <property name=\"kdenlive:docproperties.compositing\">1</property>\n" +
                "  <property name=\"kdenlive:docproperties.disablepreview\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.documentid\">1671265183813</property>\n" +
                "  <property name=\"kdenlive:docproperties.enableTimelineZone\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.enableexternalproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.enableproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.externalproxyparams\">./;GL;.LRV;./;GX;.MP4;./;GP;.LRV;./;GP;.MP4</property>\n" +
                "  <property name=\"kdenlive:docproperties.generateimageproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.generateproxy\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.groups\">[\n" +
                "]\n" +
                "</property>\n" +
                "  <property name=\"kdenlive:docproperties.kdenliveversion\">22.12.3</property>\n" +
                "  <property name=\"kdenlive:docproperties.position\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.previewextension\"/>\n" +
                "  <property name=\"kdenlive:docproperties.previewparameters\"/>\n" +
                "  <property name=\"kdenlive:docproperties.profile\">atsc_1080p_60</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyextension\"/>\n" +
                "  <property name=\"kdenlive:docproperties.proxyimageminsize\">2000</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyimagesize\">800</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyminsize\">1000</property>\n" +
                "  <property name=\"kdenlive:docproperties.proxyparams\"/>\n" +
                "  <property name=\"kdenlive:docproperties.proxyresize\">640</property>\n" +
                "  <property name=\"kdenlive:docproperties.scrollPos\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.seekOffset\">30000</property>\n" +
                "  <property name=\"kdenlive:docproperties.version\">1.04</property>\n" +
                "  <property name=\"kdenlive:docproperties.verticalzoom\">1</property>\n" +
                "  <property name=\"kdenlive:docproperties.videoTarget\">-1</property>\n" +
                "  <property name=\"kdenlive:docproperties.zonein\">0</property>\n" +
                "  <property name=\"kdenlive:docproperties.zoneout\">75</property>\n" +
                "  <property name=\"kdenlive:docproperties.zoom\">8</property>\n" +
                "  <property name=\"kdenlive:expandedFolders\"/>\n" +
                "  <property name=\"kdenlive:documentnotes\"/>\n" +
                "  <property name=\"xml_retain\">1</property>\n" +
                "  <entry producer=\"producer0\" in=\"00:00:00.000\" out=\"${durationTimecode}\"/>\n" +
                "  <entry producer=\"producer1\" in=\"00:00:00.000\" out=\"${durationTimecode}\"/>\n" +
                " </playlist>\n" +
                " <producer id=\"black_track\" in=\"00:00:00.000\" out=\"00:10:59.333\">\n" +
                "  <property name=\"eof\">continue</property>\n" +
                "  <property name=\"resource\">black</property>\n" +
                "  <property name=\"aspect_ratio\">1</property>\n" +
                "  <property name=\"mlt_service\">color</property>\n" +
                "  <property name=\"mlt_image_format\">rgba</property>\n" +
                "  <property name=\"set.test_audio\">0</property>\n" +
                " </producer>\n" +
                " <producer id=\"producer2\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "  <property name=\"length\">${durationFrames}</property>\n" +
                "  <property name=\"eof\">pause</property>\n" +
                "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameAccompaniment}</property>\n" +
                "  <property name=\"seekable\">1</property>\n" +
                "  <property name=\"audio_index\">0</property>\n" +
                "  <property name=\"video_index\">-1</property>\n" +
                "  <property name=\"mute_on_pause\">0</property>\n" +
                "  <property name=\"mlt_service\">avformat-novalidate</property>\n" +
                "  <property name=\"kdenlive:clipname\">MUSIC</property>\n" +
                "  <property name=\"kdenlive:clip_type\">1</property>\n" +
                "  <property name=\"kdenlive:folderid\">-1</property>\n" +
                "  <property name=\"kdenlive:id\">2</property>\n" +
                "  <property name=\"kdenlive:audio_max0\">236</property>\n" +
                "  <property name=\"xml\">was here</property>\n" +
                "  <property name=\"set.test_audio\">0</property>\n" +
                "  <property name=\"set.test_image\">1</property>\n" +
                " </producer>\n" +
                " <playlist id=\"playlist0\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                "  <entry producer=\"producer2\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "   <property name=\"kdenlive:id\">2</property>\n" +
                "  </entry>\n" +
                " </playlist>\n" +
                " <playlist id=\"playlist1\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                " </playlist>\n" +
                " <tractor id=\"tractor0\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                "  <property name=\"kdenlive:trackheight\">69</property>\n" +
                "  <property name=\"kdenlive:timeline_active\">1</property>\n" +
                "  <property name=\"kdenlive:collapsed\">28</property>\n" +
                "  <property name=\"kdenlive:thumbs_format\"/>\n" +
                "  <property name=\"kdenlive:audio_rec\"/>\n" +
                "  <track hide=\"both\" producer=\"playlist0\"/>\n" +
                "  <track hide=\"both\" producer=\"playlist1\"/>\n" +
                "  <filter id=\"filter0\">\n" +
                "   <property name=\"window\">75</property>\n" +
                "   <property name=\"max_gain\">20dB</property>\n" +
                "   <property name=\"mlt_service\">volume</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter1\">\n" +
                "   <property name=\"channel\">-1</property>\n" +
                "   <property name=\"mlt_service\">panner</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"start\">0.5</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter2\">\n" +
                "   <property name=\"iec_scale\">0</property>\n" +
                "   <property name=\"mlt_service\">audiolevel</property>\n" +
                "   <property name=\"dbpeak\">1</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                " </tractor>\n" +
                " <producer id=\"producer3\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "  <property name=\"length\">${durationFrames}</property>\n" +
                "  <property name=\"eof\">pause</property>\n" +
                "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameVocals}</property>\n" +
                "  <property name=\"seekable\">1</property>\n" +
                "  <property name=\"audio_index\">0</property>\n" +
                "  <property name=\"video_index\">-1</property>\n" +
                "  <property name=\"mute_on_pause\">0</property>\n" +
                "  <property name=\"mlt_service\">avformat-novalidate</property>\n" +
                "  <property name=\"kdenlive:clipname\">VOICE</property>\n" +
                "  <property name=\"kdenlive:clip_type\">1</property>\n" +
                "  <property name=\"kdenlive:folderid\">-1</property>\n" +
                "  <property name=\"kdenlive:id\">3</property>\n" +
                "  <property name=\"kdenlive:audio_max0\">197</property>\n" +
                "  <property name=\"xml\">was here</property>\n" +
                "  <property name=\"set.test_audio\">0</property>\n" +
                "  <property name=\"set.test_image\">1</property>\n" +
                " </producer>\n" +
                " <playlist id=\"playlist2\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                "  <entry producer=\"producer3\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "   <property name=\"kdenlive:id\">3</property>\n" +
                "  </entry>\n" +
                " </playlist>\n" +
                " <playlist id=\"playlist3\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                " </playlist>\n" +
                " <tractor id=\"tractor1\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
                "  <property name=\"kdenlive:audio_track\">1</property>\n" +
                "  <property name=\"kdenlive:trackheight\">246</property>\n" +
                "  <property name=\"kdenlive:timeline_active\">1</property>\n" +
                "  <property name=\"kdenlive:collapsed\">0</property>\n" +
                "  <property name=\"kdenlive:thumbs_format\"/>\n" +
                "  <property name=\"kdenlive:audio_rec\"/>\n" +
                "  <track hide=\"video\" producer=\"playlist2\"/>\n" +
                "  <track hide=\"video\" producer=\"playlist3\"/>\n" +
                "  <filter id=\"filter3\">\n" +
                "   <property name=\"window\">75</property>\n" +
                "   <property name=\"max_gain\">20dB</property>\n" +
                "   <property name=\"mlt_service\">volume</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter4\">\n" +
                "   <property name=\"channel\">-1</property>\n" +
                "   <property name=\"mlt_service\">panner</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"start\">0.5</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter5\">\n" +
                "   <property name=\"iec_scale\">0</property>\n" +
                "   <property name=\"mlt_service\">audiolevel</property>\n" +
                "   <property name=\"dbpeak\">1</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                " </tractor>\n" +
                " <tractor id=\"tractor2\" in=\"00:00:00.000\">\n" +
                "  <track producer=\"black_track\"/>\n" +
                "  <track producer=\"tractor0\"/>\n" +
                "  <track producer=\"tractor1\"/>\n" +
                "  <transition id=\"transition0\">\n" +
                "   <property name=\"a_track\">0</property>\n" +
                "   <property name=\"b_track\">1</property>\n" +
                "   <property name=\"mlt_service\">mix</property>\n" +
                "   <property name=\"kdenlive_id\">mix</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"always_active\">1</property>\n" +
                "   <property name=\"accepts_blanks\">1</property>\n" +
                "   <property name=\"sum\">1</property>\n" +
                "  </transition>\n" +
                "  <transition id=\"transition1\">\n" +
                "   <property name=\"a_track\">0</property>\n" +
                "   <property name=\"b_track\">2</property>\n" +
                "   <property name=\"mlt_service\">mix</property>\n" +
                "   <property name=\"kdenlive_id\">mix</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"always_active\">1</property>\n" +
                "   <property name=\"accepts_blanks\">1</property>\n" +
                "   <property name=\"sum\">1</property>\n" +
                "  </transition>\n" +
                "  <filter id=\"filter6\">\n" +
                "   <property name=\"window\">75</property>\n" +
                "   <property name=\"max_gain\">20dB</property>\n" +
                "   <property name=\"mlt_service\">volume</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter7\">\n" +
                "   <property name=\"channel\">-1</property>\n" +
                "   <property name=\"mlt_service\">panner</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"start\">0.5</property>\n" +
                "   <property name=\"disable\">1</property>\n" +
                "  </filter>\n" +
                "  <filter id=\"filter8\">\n" +
                "   <property name=\"mlt_service\">avfilter.subtitles</property>\n" +
                "   <property name=\"internal_added\">237</property>\n" +
                "   <property name=\"av.filename\">/var/tmp/1671265183813.srt</property>\n" +
                "  </filter>\n" +
                " </tractor>\n" +
                "</mlt>\n"

        val kdenliveFile = File(kdenliveFileName)
        if (!kdenliveFile.exists()) {
            kdenliveFile.writeText(kdenliveTemplate)
        }

        val durationSubsTimecode1 = convertMillisecondsToTimecode(durationInMilliseconds).replace(".",",")
        val durationSubsTimecode2 = convertFramesToTimecode(convertMillisecondsToFrames(durationInMilliseconds) +1).replace(".",",")

        val subs = "1\n" +
                "00:00:00,000 --> 00:00:00,083\n" +
                "~[G0]\n" +
                "\n" +
                "2\n" +
                "${durationSubsTimecode1} --> ${durationSubsTimecode2}\n" +
                "//\\\\\n"

        val kdenliveSubsFile = File(kdenliveSubsFileName)
        if (!kdenliveSubsFile.exists()) {
            kdenliveSubsFile.writeText(subs)
        }

    }

    fileDemucs2tracks.writeText(textFileDemucs2tracks)
    Files.setPosixFilePermissions(fileDemucs2tracks.toPath(), permissions)

    fileDemucs4tracks.writeText(textFileDemucs4tracks)
    Files.setPosixFilePermissions(fileDemucs4tracks.toPath(), permissions)

    fileDemucs5tracks.writeText(textFileDemucs5tracks)
    Files.setPosixFilePermissions(fileDemucs5tracks.toPath(), permissions)

    fileMainPairs.writeText(textFileMainPairs)
    fileMelt.writeText(textFileMelt)

}

fun getNewTone(tone: String, capo: Int): String {
    val noteAndTone = tone.split(" ")
    val nameChord = noteAndTone[0]
    val (chord, note) = MusicChord.getChordNote(nameChord)
    var newIndexNote = MusicNote.values().indexOf(note!!) - capo
    if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
    val newNote = MusicNote.values()[newIndexNote]
    return "${newNote.names.first()} ${noteAndTone[1]}"
}
fun generateChordLayout(chordName: String, capo: Int): List<MltObject> {
    val chordNameAndFret = chordName.split("|")
    val nameChord = chordNameAndFret[0]
    val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
    val (chord, note) = MusicChord.getChordNote(nameChord)
    return if (chord!=null && note != null) generateChordLayout(chord, note, fretChord, capo) else emptyList()
}
fun generateChordLayout(chord: MusicChord, startRootNote: MusicNote, startInitFret: Int, capo: Int): List<MltObject> {

    var newIndexNote = MusicNote.values().indexOf(startRootNote) - capo
    if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
    val note = MusicNote.values()[newIndexNote]
    var fret = startInitFret - capo
    if (fret < 0) fret = 0

    var fingerboards: List<Fingerboard> = chord.getFingerboard(note, if (fret == 0) note.defaultRootFret else fret, capo)

    var nextFret = fret
    while (fingerboards.isEmpty()) {
        nextFret += 1
        fingerboards = chord.getFingerboard(note, if (nextFret == 0) note.defaultRootFret else nextFret)
    }

    val initFret = fingerboards[0].rootFret
    val result:MutableList<MltObject> = mutableListOf()
    var chordLayoutW = (Karaoke.frameHeightPx / 4).toInt()
    var chordLayoutH = chordLayoutW

    val chordName = "${note.names.first()}${chord.names.first()}"
    val chordNameMltText = Karaoke.chordLayoutChordNameMltText.copy(chordName)
//    chordNameMltText.text = chordName

    val fretW = (chordLayoutW / 6.0).toInt()
    var fretNumberTextH = 0
    val mltShapeFingerCircleDiameter = fretW/2
    val fretRectangleMltShape = Karaoke.chordLayoutFretsRectangleMltShape.copy()

    // Бэкграунд
    result.add(
        MltObject(
        layoutW = chordLayoutW,
        layoutH = chordLayoutH,
        _shape = Karaoke.chordLayoutBackgroundRectangleMltShape,
        alignmentX = MltObjectAlignmentX.LEFT,
        alignmentY = MltObjectAlignmentY.TOP,
        _x = 0,
        _y = 0,
        _w = chordLayoutW,
        _h = chordLayoutH
    )
    )

    // Название аккорда
    val mltTextChordName = MltObject(
        layoutW = chordLayoutW,
        layoutH = chordLayoutH,
        _shape = chordNameMltText,
        alignmentX = MltObjectAlignmentX.CENTER,
        alignmentY = MltObjectAlignmentY.TOP,
        _x = chordLayoutW/2,
        _y = 0,
        _h = (chordLayoutH * 0.2).toInt()
    )
    result.add(mltTextChordName)

    // Номера ладов
    val firstFret = if (initFret == 0) 1 else initFret
    for (fret in firstFret+capo..(firstFret+capo+3)) {
        val fretNumberMltText = Karaoke.chordLayoutFretsNumbersMltText.copy(fret.toString())
//        fretNumberMltText.text = fret.toString()

        val mltTextFretNumber = MltObject(
            layoutW = chordLayoutW,
            layoutH = chordLayoutH,
            _shape = fretNumberMltText,
            alignmentX = MltObjectAlignmentX.CENTER,
            alignmentY = MltObjectAlignmentY.TOP,
            _x = fretW * (fret - firstFret + 1 - capo) + fretW/2,
            _y = mltTextChordName.h,
            _h = (chordLayoutH * 0.1).toInt()
        )
        fretNumberTextH = mltTextFretNumber.h
        result.add(mltTextFretNumber)
    }

    val mltShapeFretRectangleH = (chordLayoutH - (mltTextChordName.h + 2*fretNumberTextH)) / 5

    // Прямоугольники ладов

    for (string in 0..4) {
        // Порожек или каподастр
        if (initFret == 0) {
            val nutRectangleMltShape = if (capo == 0) Karaoke.chordLayoutNutsRectangleMltShape.copy() else Karaoke.chordLayoutСapoRectangleMltShape.copy()
            val mltShapeNutRectangle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = nutRectangleMltShape,
                alignmentX = MltObjectAlignmentX.RIGHT,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(string) + mltShapeFingerCircleDiameter/2,
                _w = fretW/5,
                _h = mltShapeFretRectangleH
            )
            result.add(mltShapeNutRectangle)
        }
        for (fret in 1..4) {
            val mltShapeFretRectangle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = fretRectangleMltShape,
                alignmentX = MltObjectAlignmentX.CENTER,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW * fret + fretW/2,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(string) + mltShapeFingerCircleDiameter/2,
                _w = fretW,
                _h = mltShapeFretRectangleH
            )
            result.add(mltShapeFretRectangle)
        }
    }

    // Распальцовка
    fingerboards.forEach { fingerboard ->

        // Приглушение струны
        if (fingerboard.muted) {
            val mutedRectangleMltShape = Karaoke.chordLayoutMutedRectangleMltShape.copy()
            val mltShapeMutedRectangle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = mutedRectangleMltShape,
                alignmentX = MltObjectAlignmentX.LEFT,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(fingerboard.guitarString.number-1) + mltShapeFingerCircleDiameter/2 - fretRectangleMltShape.shapeOutline/2,
                _w = fretW*4,
                _h = fretRectangleMltShape.shapeOutline
            )
            result.add(mltShapeMutedRectangle)
        }

        if (!((initFret == 0 && fingerboard.fret == 0) || fingerboard.muted)) {
            val fingerCircleMltShape = Karaoke.chordLayoutFingerCircleMltShape.copy()
            val mltShapeFingerCircle = MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                _shape = fingerCircleMltShape,
                alignmentX = MltObjectAlignmentX.LEFT,
                alignmentY = MltObjectAlignmentY.TOP,
                _x = fretW * (fingerboard.fret - initFret + (if (initFret != 0) 1 else 0)) + fretW/2 - (mltShapeFingerCircleDiameter)/2,
                _y = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH*(fingerboard.guitarString.number-1) + mltShapeFingerCircleDiameter/2 - mltShapeFingerCircleDiameter/2,
                _w = mltShapeFingerCircleDiameter,
                _h = mltShapeFingerCircleDiameter
            )
            result.add(mltShapeFingerCircle)
        }


    }

    // Барре (если первый лад не нулевой)
    if (initFret != 0) {
        val fingerCircleMltShape = Karaoke.chordLayoutFingerCircleMltShape.copy()
        fingerCircleMltShape.type = MltObjectType.ROUNDEDRECTANGLE
        val mltShapeFingerCircle = MltObject(
            layoutW = chordLayoutW,
            layoutH = chordLayoutH,
            _shape = fingerCircleMltShape,
            alignmentX = MltObjectAlignmentX.LEFT,
            alignmentY = MltObjectAlignmentY.TOP,
            _x = fretW + fretW/2 - (mltShapeFingerCircleDiameter)/2,
            _y = mltTextChordName.h + fretNumberTextH + mltShapeFingerCircleDiameter/2 - mltShapeFingerCircleDiameter/2,
            _w = mltShapeFingerCircleDiameter,
            _h = mltShapeFretRectangleH*5 +  mltShapeFingerCircleDiameter
        )
        result.add(mltShapeFingerCircle)
    }

    return result
}

fun getChordLayoutPicture(mltObjects:List<MltObject>): BufferedImage {

    val imageType = BufferedImage.TYPE_INT_ARGB

    if (mltObjects.isEmpty()) {
        val resultImage = BufferedImage((Karaoke.frameHeightPx /4).toInt(), (Karaoke.frameHeightPx /4).toInt(), imageType)
        val graphics2D = resultImage.graphics as Graphics2D
        val opaque = 1f
        val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
        graphics2D.composite = alphaChannel
        graphics2D.color = Color.BLACK
        graphics2D.fillRect(0,0,(Karaoke.frameHeightPx /4).toInt(), (Karaoke.frameHeightPx /4).toInt())
        graphics2D.dispose()
        return resultImage
    }
    val resultImage = BufferedImage(mltObjects[0].layoutW, mltObjects[0].layoutH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D


    mltObjects.forEach { obj ->

        when (obj.shape.type) {
            MltObjectType.TEXT -> {
                val opaque = obj.shape.shapeColor.alpha / 255f
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.color = obj.shape.shapeColor
                graphics2D.stroke = BasicStroke(obj.shape.shapeOutline.toFloat())

                val textToOverlay = (obj.shape as MltText).text
                graphics2D.font = (obj.shape as MltText).font
                graphics2D.drawString(textToOverlay, obj.x, obj.y + obj.h - obj.h/4)

            }
            else -> {

                var opaque = obj.shape.shapeColor.alpha / 255f
                var alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.color = obj.shape.shapeColor

                when (obj.shape.type) {
                    MltObjectType.RECTANGLE -> graphics2D.fillRect(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.CIRCLE -> graphics2D.fillOval(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.ROUNDEDRECTANGLE -> graphics2D.fillRoundRect(obj.x,obj.y,obj.w, obj.h, Integer.min(obj.w, obj.h), Integer.min(obj.w, obj.h))
                    else -> {}
                }

                opaque = obj.shape.shapeOutlineColor.alpha / 255f
                alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.stroke = BasicStroke(obj.shape.shapeOutline.toFloat())

                graphics2D.color = obj.shape.shapeOutlineColor
                when (obj.shape.type) {
                    MltObjectType.RECTANGLE -> graphics2D.drawRect(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.CIRCLE -> graphics2D.drawOval(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.ROUNDEDRECTANGLE -> graphics2D.drawRoundRect(obj.x,obj.y,obj.w, obj.h, Integer.min(obj.w, obj.h), Integer.min(obj.w, obj.h))
                    else -> {}
                }

            }
        }
    }
    graphics2D.dispose()
    return resultImage
}

fun getFontSizeByHeight(heightPx: Int, font: Font): Int {
    var fontSize = 1
    while (getTextWidthHeightPx("0", Font(font.fontName, font.style, fontSize)).second < heightPx) {
        fontSize += 1
    }
    return fontSize-1
}

fun getFileNameByMasks(pathToFolder: String, startWith: String, suffixes: List<String>,extension: String): String {

    try {
        val files = Files.walk(Path(pathToFolder))
            .filter(Files::isRegularFile)
            .map { it.toString() }
            .filter{ it.endsWith(extension) && it.startsWith("${pathToFolder}/$startWith")}
            .map { Path(it).toFile().name }
            .toList()
        suffixes.forEach { suffix ->
            val filename = files.firstOrNull{it.startsWith("${startWith}${suffix}")}
            if (filename != null) return filename
        }
    } catch (e: Exception) {
        return ""
    }
    return ""

}

fun getSongChordsPicture(song: Song, mltNode: MltNode): BufferedImage {

    val songTextSymbolsGroup = mltNode.body as MutableList<MltNode>
    val startViewport = songTextSymbolsGroup.first { it.name == "startviewport" }
    val startViewportFields = startViewport.fields["rect"]!!.split(",")
    val frameW = startViewportFields[2].toInt()
    val frameH = startViewportFields[3].toInt()
    // Находим количество страниц, на которые надо разделить текст, чтобы ширина была больше высоты
    var countPages = 0
    do {
        countPages++
    } while ((frameW.toDouble() * countPages) / (frameH.toDouble() / countPages) < 1.2 )

    // Находим минимальную высоту страницы
    var minPageH = frameH / (countPages)

    val opaque: Float = 1f
    val colorBack = Color(255,255,255,255)
    val colorText = Color(0,0,0,255)
    val colorChord = Color(255,0,0,255)
    var fontText = Font(MAIN_FONT_NAME, 0, 10)
    val imageType = BufferedImage.TYPE_INT_ARGB
    val resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    graphics2D.composite = alphaChannel
    graphics2D.background = colorBack
    graphics2D.color = colorBack
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorBack
    graphics2D.font = fontText

    var currPage = 1

    data class PicturePage(
        val number: Int,
        var h: Int,
        var lines: MutableList<MltNode>
    )
    val pages: MutableList<PicturePage> = mutableListOf()
    var currentH = 0
    var listLines: MutableList<MltNode> = mutableListOf()
    songTextSymbolsGroup.filter {it.name == "item"} .forEach { songTextSymbols ->
        val nodePosition = (songTextSymbols.body as MutableList<*>)[0] as MltNode
        val nodeText = (songTextSymbols.body as MutableList<*>)[1] as MltNode
        val x = nodePosition.fields["x"]!!.toInt()
        val y = nodePosition.fields["y"]!!.toInt()
        val textToOverlay = nodeText.body as String
        val isChord = (nodeText.fields["font"]!!.split(" ")[0] == Karaoke.chordsFont.font.fontName.split(" ")[0])
        val fontTextTmp = Font(nodeText.fields["font"], 0, nodeText.fields["font-pixel-size"]!!.toInt())
        graphics2D.font = fontTextTmp
        val fontMetrics = graphics2D.fontMetrics
        val rectH = fontMetrics.getStringBounds(textToOverlay, graphics2D).height.toInt()
        currentH = y + rectH
        val totalH = minPageH + pages.sumOf { it.h } // Полная высота - минимальная высота плюс высоты уже найденных страниц
        // Если текущая высота больше полной высоты и строчка не аккорд - переход на следующую страницу
        if (currentH > totalH && isChord) {
            val picturePage = PicturePage(number = currPage, h = currentH - pages.sumOf { it.h } - rectH, lines = listLines)
            pages.add(picturePage)
            currPage++
            listLines = mutableListOf()
            listLines.add(songTextSymbols)
//            println("${picturePage.number} - ${picturePage.h} - ${picturePage.lines.last().body}")
        } else {
            listLines.add(songTextSymbols)
        }
    }
    val picturePage = PicturePage(number = currPage, h = currentH - pages.sumOf { it.h }, lines = listLines)
    pages.add(picturePage)

    var totalH = 0
    val bis: MutableList<BufferedImage> = mutableListOf()
    pages.forEach { picturePage ->

        val resultImagePage = BufferedImage(frameW, picturePage.h+20, imageType)
        val graphics2Dpage = resultImagePage.graphics as Graphics2D
        val alphaChannelPage = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

        graphics2Dpage.composite = alphaChannelPage
        graphics2Dpage.background = colorBack
        graphics2Dpage.color = colorBack
        graphics2Dpage.fillRect(0,0,frameW, picturePage.h+20)
        graphics2Dpage.color = colorBack
        graphics2Dpage.font = fontText

        picturePage.lines.forEach { songTextSymbols ->
            val nodePosition = (songTextSymbols.body as MutableList<*>)[0] as MltNode
            val nodeText = (songTextSymbols.body as MutableList<*>)[1] as MltNode
            val x = nodePosition.fields["x"]!!.toInt()
            val y = nodePosition.fields["y"]!!.toInt() - totalH
            val textToOverlay = (nodeText.body as String).replace("&amp;amp;", "&")
            val isChord = (nodeText.fields["font"]!!.split(" ")[0] == Karaoke.chordsFont.font.fontName.split(" ")[0])
            graphics2Dpage.color = if (isChord) colorChord else colorText
            fontText = Font(nodeText.fields["font"], 0, (nodeText.fields["font-pixel-size"]!!.toInt() / if (isChord) Karaoke.chordsHeightCoefficient else 1.0).toInt())
            graphics2Dpage.font = fontText
            val fontMetrics = graphics2Dpage.fontMetrics
            val rectH = fontMetrics.getStringBounds(textToOverlay, graphics2Dpage).height.toInt()
            val newY = y + rectH
            graphics2Dpage.drawString(textToOverlay, x, newY)
        }
        graphics2Dpage.dispose()
        bis.add(resultImagePage)
        totalH += picturePage.h
    }

    val fullW = frameW * pages.size
    var fullH = (210 * fullW / 297.0).toInt()

    val resultImages = BufferedImage(fullW, pages.maxOf { it.h } + 20, imageType)
    val graphics2Dresult = resultImages.graphics as Graphics2D
    graphics2Dresult.composite = alphaChannel
    graphics2Dresult.background = colorBack
    graphics2Dresult.color = colorBack
    graphics2Dresult.fillRect(0,0,fullW, pages.maxOf { it.h } + 20)
    bis.forEachIndexed{ index, bi ->
        graphics2Dresult.drawImage(bi, frameW * index, 0, null)
    }
    graphics2Dresult.dispose()

    val nameW = fullW
    val nameH = Integer.max(fullH - (pages.maxOf { it.h } + 20),200)

    val resultImageName = BufferedImage(nameW, nameH, imageType)
    val graphics2name = resultImageName.graphics as Graphics2D
    graphics2name.composite = alphaChannel
    graphics2name.background = colorBack
    graphics2name.color = colorBack
    graphics2name.fillRect(0,0,nameW, nameH)
    graphics2name.color = colorText
    val textToOverlay = "${song.settings.author} - ${song.settings.year} - «${song.settings.songName}» (${song.settings.key}, ${song.settings.bpm} bpm)"
    var rectW = 0
    var rectH = 0
    fontText = Font(MAIN_FONT_NAME, 0, 10)
    do {
        fontText = Font(fontText.name, fontText.style, fontText.size+1)
        graphics2name.font = fontText
        val fontMetrics = graphics2name.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2name)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > (nameH * 0.7) || rectW > (nameW * 0.7)))

    var centerX = (nameW - rectW) / 2
    var centerY = (nameH - rectH) / 2 + rectH
    graphics2name.drawString(textToOverlay, centerX, centerY)
    graphics2name.dispose()
    fullH = resultImageName.height + resultImages.height
    val resultImagesAndName = BufferedImage(fullW, fullH, imageType)
    val graphics2DresultAndName = resultImagesAndName.graphics as Graphics2D
    graphics2DresultAndName.composite = alphaChannel
    graphics2DresultAndName.background = colorBack
    graphics2DresultAndName.color = colorBack
    graphics2DresultAndName.fillRect(0,0,fullW, fullH)
    graphics2DresultAndName.drawImage(resultImageName, 0, 0, null)
    graphics2DresultAndName.drawImage(resultImages, 0, nameH, null)

    graphics2DresultAndName.dispose()

    return resultImagesAndName
}

fun createSongChordsPicture(song: Song, fileName: String, songVersion: SongVersion, mltNode: MltNode) {
    if (songVersion == SongVersion.CHORDS) {
        val resultImage = getSongChordsPicture(song, mltNode)
        val file = File(fileName)
        ImageIO.write(resultImage, "png", file)
    }
}
fun createSongPicture(song: Song, fileName: String, songVersion: SongVersion) {
    val caption = songVersion.text
    val comment: String = "${songVersion.textForDescription}"
    val pathToLogoAlbum = "${song.settings.pathToFileLogoAlbum}"
    val pathToLogoAuthor = "${song.settings.pathToFileLogoAuthor}"

    val frameW = 1920
    val frameH = 1080
    val opaque: Float = 1f
    var fontSongname = Font(MAIN_FONT_NAME, 0, 10)
    var fontCaption = Font(MAIN_FONT_NAME, 0, 200)
    var fontComment = Font(MAIN_FONT_NAME, 0, 60)
    val colorSongname = Color(255,255,127,255)
    val colorCaption = Color(85,255,255,255)
    val colorComment = Color(85,255,255,255)
    var textToOverlay = song.settings.songName.censored()
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    val biLogoAlbum = ImageIO.read(File(pathToLogoAlbum))
    val biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorSongname
    graphics2D.font = fontSongname

    var rectW = 0
    var rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size+1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 430 || rectW > (frameW * 0.95)))

    var centerX = (frameW - rectW) / 2
    var centerY = (frameH - rectH) / 2 + rectH
    graphics2D.drawString(textToOverlay, centerX, centerY)

    textToOverlay = caption.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    graphics2D.color = colorCaption
    graphics2D.font = fontCaption
    var fontMetrics = graphics2D.fontMetrics
    var rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
    rectW = rect.width.toInt()
    rectH = rect.height.toInt()

    centerX = (frameW - rectW) / 2
    centerY = frameH - 100
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(biLogoAlbum, 260, 50, null)
    graphics2D.drawImage(biLogoAuthor, 710, 50, null)

    if (comment != "") {
        textToOverlay = comment
        graphics2D.color = colorComment
        graphics2D.font = fontComment
        var fontMetrics = graphics2D.fontMetrics
        var rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()

        centerX = (frameW - rectW) / 2
        centerY = frameH - 20
        graphics2D.drawString(textToOverlay, centerX, centerY)


    }

//    graphics2D.drawImage(biLogoAlbum, 50, 50, null)
//    graphics2D.drawImage(biLogoAuthor, 710, 50, null)

    graphics2D.dispose()

    val file = File(fileName)

    ImageIO.write(resultImage, "png", file)

}

fun resizeBufferedImage(img: BufferedImage, newW: Int, newH: Int): BufferedImage? {
    val tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH)
    val dimg = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
    val g2d = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    return dimg
}

fun createBoostyTeaserPicture(song: Song, fileName: String) {
    val pathToLogoAlbum = "${song.settings.pathToFileLogoAlbum}"
    val pathToLogoAuthor = "${song.settings.pathToFileLogoAuthor}"

    val frameW = 575
    val frameH = 625
    val opaque: Float = 1f
    var fontSongname = Font(MAIN_FONT_NAME, 0, 10)
    val colorSongname = Color(255,255,127,255)
    var textToOverlay = song.settings.songName.censored()
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    val biLogoAlbum = ImageIO.read(File(pathToLogoAlbum))
    val biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorSongname
    graphics2D.font = fontSongname

    var rectW = 0
    var rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size+1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 130 || rectW > (frameW * 0.95)))

    var centerX = (frameW - rectW) / 2
    var centerY = (frameH - rectH) / 2 + rectH + 50
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(resizeBufferedImage(biLogoAlbum,148, 148), 20, 100, null)
    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 370, 148), 188, 100, null)

    graphics2D.dispose()

    val file = File(fileName.replace(" [lyrics] boosty"," [boosty]"))

    ImageIO.write(resultImage, "png", file)

}

fun createVKPicture(song: Song, fileName: String) {
    val pathToLogoAlbum = "${song.settings.pathToFileLogoAlbum}"
    val pathToLogoAuthor = "${song.settings.pathToFileLogoAuthor}"

    val frameW = 575
    val frameH = 300
    val opaque: Float = 1f
    var fontSongname = Font(MAIN_FONT_NAME, 0, 10)
    val colorSongname = Color(255,255,127,255)
    var textToOverlay = song.settings.songName.censored()
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    val biLogoAlbum = ImageIO.read(File(pathToLogoAlbum))
    val biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorSongname
    graphics2D.font = fontSongname

    var rectW = 0
    var rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size+1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 130 || rectW > (frameW * 0.95)))

    var centerX = (frameW - rectW) / 2
    var centerY = (frameH - rectH) / 2 + rectH + 60
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(resizeBufferedImage(biLogoAlbum,148, 148), 20, 20, null)
    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 370, 148), 188, 20, null)

    graphics2D.dispose()

    val file = File(fileName.replace(" [lyrics] VK"," [VK]"))

    ImageIO.write(resultImage, "png", file)

}

fun createDzenPicture(pathToAuthor: String) {
    val pathToLogoAuthor = "$pathToAuthor/LogoAuthor.png"

    val frameW = 575
    val frameH = 575
    val opaque: Float = 1f
    var fontSongname = Font(MAIN_FONT_NAME, 0, 10)
    val colorCaption = Color(85,255,255,255)
    var textToOverlay = "Karaoke"
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    var graphics2D = resultImage.graphics as Graphics2D
    var alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    var biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorCaption
    graphics2D.font = fontSongname

    var rectW = 0
    var rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size+1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 130 || rectW > (frameW * 0.95)))

    var centerX = (frameW - rectW) / 2
    var centerY = (frameH - rectH) / 2 + rectH + 140
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 575, 230), 0, 120, null)

    graphics2D.dispose()

    var file = File("$pathToAuthor/DZEN_$textToOverlay.png")

    ImageIO.write(resultImage, "png", file)





    textToOverlay = "Lyrics"
    resultImage = BufferedImage(frameW, frameH, imageType)
    graphics2D = resultImage.graphics as Graphics2D
    alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorCaption
    graphics2D.font = fontSongname

    rectW = 0
    rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size+1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 130 || rectW > (frameW * 0.95)))

    centerX = (frameW - rectW) / 2
    centerY = (frameH - rectH) / 2 + rectH + 140
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 575, 230), 0, 120, null)

    graphics2D.dispose()

    file = File("$pathToAuthor/DZEN_$textToOverlay.png")

    ImageIO.write(resultImage, "png", file)


}

fun test() {


    val fileNameXml = "src/main/resources/settings.xml"
    val props = Properties()
//    val frameW = Integer.valueOf(props.getProperty("FRAME_WIDTH_PX", "1"));
    var kdeBackgroundFolderPath = props.getProperty("kdeBackgroundFolderPath", "&&&")

    props.setProperty("FRAME_FPS", Karaoke.frameFps.toString())
    props.setProperty("VOICES_SETTINGS", """
        voice=0;group=0;fontNameText=Tahoma;colorText=255,255,255,255;fontNameBeat=Tahoma;colorBeat=155,255,255,255
        voice=0;group=1;fontNameText=Lobster;colorBeat=105,255,105,255;fontNameBeat=Lobster;colorText=255,255,155,255
        """
        .trimIndent())
    props.storeToXML(File(fileNameXml).outputStream(), "Какой-то комментарий")

    props.loadFromXML(File(fileNameXml).inputStream())

    val videoSettings = props.getProperty("VOICES_SETTINGS").split("\n")

    videoSettings.forEach { vs ->
        if (vs.isNotEmpty()) {
            val vars = vs.split(";")
            vars.forEach { variable ->
                val nameAndValue = variable.split("=")
                when(nameAndValue[0]) {
                    "voice" -> println("${nameAndValue[0]} = ${(nameAndValue[1].toLong())}")
                    "group" -> println("${nameAndValue[0]} = ${(nameAndValue[1].toLong())}")
                    "fontNameText" -> println("${nameAndValue[0]} = ${(nameAndValue[1] as String)}")
                    "fontNameBeat" -> println("${nameAndValue[0]} = ${(nameAndValue[1] as String)}")
                    "colorText" -> {
                        val rgba = nameAndValue[1].split(",")
                        println("colorText r = ${(rgba[0].toLong())}")
                        println("colorText g = ${(rgba[1].toLong())}")
                        println("colorText b = ${(rgba[2].toLong())}")
                        println("colorText a = ${(rgba[3].toLong())}")
                    }
                    "colorBeat" -> {
                        val rgba = nameAndValue[1].split(",")
                        println("colorBeat r = ${(rgba[0].toLong())}")
                        println("colorBeat g = ${(rgba[1].toLong())}")
                        println("colorBeat b = ${(rgba[2].toLong())}")
                        println("colorBeat a = ${(rgba[3].toLong())}")
                    }
                }
            }
        }
    }



}

fun getTextWidthHeightPx(text: String, fontName: String, fontStyle: Int, fontSize: Int): Pair<Double, Double> {
    return getTextWidthHeightPx(text, Font(fontName, fontStyle, fontSize))
}

fun getTextWidthHeightPx(text: String, font: Font): Pair<Double, Double> {
    val graphics2D = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
    graphics2D.font = font
    val rect = graphics2D.fontMetrics.getStringBounds(text, graphics2D)
    return Pair(rect.width, rect.height)
}

fun convertMarkersToSubtitles(pathToSourceFile: String, pathToResultFile: String = "") {

    val gson = GsonBuilder()
        .setLenient()
        .create()

    val sourceFileBody = File(pathToSourceFile).readText(Charsets.UTF_8)
    val regexpLines = Regex("""<property name=\"kdenlive:markers\"[^<]([\s\S]+?)</property>""")
    val linesMatchResults = regexpLines.findAll(sourceFileBody)
    var countSubsFile = 0L
    val subsFiles: MutableList<MutableList<Marker>> = emptyList<MutableList<Marker>>().toMutableList()
    linesMatchResults.forEach { lineMatchResult ->
        val textToAnalize = lineMatchResult.groups.get(1)?.value?.replace("\n", "")?.replace("[", "")?.replace("]", "")
        val regexpMarkers = Regex("""\{[^\}]([\s\S]+?)\}""")
        val markersMatchResults = regexpMarkers.findAll(textToAnalize!!)
        if (markersMatchResults.iterator().hasNext()) {
            countSubsFile++
            val markers = mutableListOf<Marker>()
            markersMatchResults.forEach { markerMatchResult ->
                val marker = gson.fromJson(markerMatchResult.value, Marker::class.java)
                markers.add(marker)
            }
            subsFiles.add(markers)
        }
    }


    var countCreatedFiles = 0L
    for (indexSubFiles in 0 until subsFiles.size) {
        val subFile = subsFiles[indexSubFiles]
        var prevMarkerIsEndLine = true
        val subtitles = mutableListOf<Subtitle>()
        for (indexMarker in 0 until subFile.size) {

            val currMarker = subFile[indexMarker]

            if (currMarker.comment in ".\\/*" || indexMarker == subFile.size-1) {
                prevMarkerIsEndLine = true
                continue
            }

            val nextMarker = subFile[indexMarker+1]
            val isLineStart = prevMarkerIsEndLine
            val isLineEnd = (nextMarker.comment in ".\\/*" || indexMarker == subFile.size-1)
            prevMarkerIsEndLine = isLineEnd

            var subText = currMarker.comment.replace(" ", "_").replace("-", "")
            if (isLineStart) subText = subText[0].uppercase()+subText.subSequence(1,subText.length)
            if (isLineStart) subText = "//${subText}"
            if (isLineEnd) subText = "${subText}\\\\"

            val startTimecode = convertFramesToTimecode(currMarker.pos, 60.0)
            val endTimecode = convertFramesToTimecode(nextMarker.pos, 60.0)

            val subtitle = Subtitle(
                startTimecode = startTimecode,
                endTimecode = endTimecode,
                mltText = Karaoke.voices[0].groups[0].mltText.copy(subText),
                isLineStart = isLineStart,
                isLineEnd = isLineEnd
            )
            subtitles.add(subtitle)
        }

        var textSubtitleFile = ""
        for (index in 0 until subtitles.size) {
            val subtitle = subtitles[index]
            textSubtitleFile += "${index+1}\n${subtitle.startTimecode} --> ${subtitle.endTimecode}\n${subtitle.mltText.text}\n\n"
        }

        if (textSubtitleFile != "") {
            countCreatedFiles++
            val fileNameNewSubs = "${pathToSourceFile}${if (countCreatedFiles == 1L) "" else "_${countCreatedFiles-1}"}.srt"
            File(fileNameNewSubs).writeText(textSubtitleFile)
        }

    }

}

fun getRandomFile(pathToFolder: String, extention: String = ""): String {
    val listFiles = getListFiles(pathToFolder, extention)
    return if (listFiles.isEmpty()) "" else listFiles[Random.nextInt(listFiles.size)]
}

fun getListFiles(pathToFolder: String, extention: String = "", startWith: String = ""): List<String> {
    return Files.walk(Path(pathToFolder)).filter(Files::isRegularFile).map { it.toString() }.filter{ it.endsWith(extention) && it.startsWith("${pathToFolder}/$startWith")}.toList().sorted()
}
fun getListFiles(pathToFolder: String, extentions: List<String> = listOf(), startsWith: List<String> = listOf(), excludes: List<String> = listOf()): List<String> {
    val result = mutableListOf<String>()
    val preRes = getListFiles(pathToFolder)
    val filteredEndRes = mutableListOf<String>()
    val filteredStartRes = mutableListOf<String>()
    val filteredExcludeRes = mutableListOf<String>()
    if (extentions.isNotEmpty()) {
        extentions.forEach { extention ->
            filteredEndRes.addAll(preRes.filter { it.endsWith(extention) })
        }
    } else {
        filteredEndRes.addAll(preRes)
    }
    if (startsWith.isNotEmpty()) {
        startsWith.forEach { startWith ->
            filteredStartRes.addAll(filteredEndRes.filter { it.startsWith("${pathToFolder}/$startWith") })
        }
    } else {
        filteredStartRes.addAll(filteredEndRes)
    }
    if (excludes.isNotEmpty()) {
        val excludeRes = mutableListOf<String>()
        excludes.forEach { exclude ->
            excludeRes.addAll(filteredStartRes.filter { it.contains(exclude) })
        }
        result.addAll(filteredStartRes.filter { it !in excludeRes })
    } else {
        result.addAll(filteredStartRes)
    }

    return result.sorted().toList()
}


fun extractSubtitlesFromAutorecognizedFile(pathToFileFrom: String, pathToFileTo: String): String {
    val text = File(pathToFileFrom).readText(Charsets.UTF_8)
    val regexpLines = Regex("""href=\"\d+?#[^\/a](.+?)\/a""")
    val linesMatchResults = regexpLines.findAll(text)
    var counter = 0L
    var subs = ""
    linesMatchResults.forEach { lineMatchResult->
        val line = lineMatchResult.value
        val startEnd = Regex("""href=\"\d+?[^\"&gt](.+?)\"&gt""").find(line)?.groups?.get(1)?.value?.split(":")
        val start = convertMillisecondsToTimecode(((startEnd?.get(0)?:"0").toDouble()*1000).toLong())
        val end = convertMillisecondsToTimecode(((startEnd?.get(1)?:"0").toDouble()*1000).toLong())
        val word = Regex("""&gt[^&lt](.+?)&lt""").find(line)?.groups?.get(1)?.value
        if (word != "Речь отсутствует") {
            counter++
            subs += "${counter}\n${start} --> ${end}\n${word}\n\n"
        }
    }
    File(pathToFileTo).writeText(subs)
    return subs
}

fun convertMillisecondsToFrames(milliseconds: Long, fps:Double = Karaoke.frameFps): Long {
    val frameLength = 1000.0 / fps
    return Math.round(milliseconds / frameLength)
}

fun convertMillisecondsToFramesDouble(milliseconds: Long, fps:Double = Karaoke.frameFps): Double {
    val frameLength = 1000.0 / fps
    return milliseconds / frameLength
}

fun convertFramesToMilliseconds(frames: Long, fps:Double = Karaoke.frameFps): Long {
    val frameLength = 1000.0 / fps
    return (frames * frameLength).roundToInt().toLong()
}

fun convertMillisecondsToTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%02d:%02d:%02d.%03d".format(hours,minutes,seconds,ms)
}

fun convertMillisecondsToYoutubeTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%01d:%02d:%02d".format(hours,minutes,seconds)
}

fun convertFramesToTimecode(frames: Long, fps:Double = Karaoke.frameFps): String {
    return convertMillisecondsToTimecode(milliseconds = convertFramesToMilliseconds(frames,fps))
}

fun convertTimecodeToMilliseconds(timecode: String): Long {
    val hhmmssmm = timecode.split(":")
    val hours = hhmmssmm[0].toLong()
    val minutes = hhmmssmm[1].toLong()
    val ssmm = hhmmssmm[2].replace(",", ".").split(".")
    val seconds = ssmm[0].toLong()
    val milliseconds = ssmm[1].toLong()
    return milliseconds + seconds * 1000 + minutes * 1000 * 60 + hours * 1000 * 60 * 60
}

fun convertTimecodeToFrames(timecode: String, fps:Double = Karaoke.frameFps): Long {
    return convertMillisecondsToFrames(convertTimecodeToMilliseconds(timecode = timecode), fps)
}

fun getBeatNumberByMilliseconds(timeInMilliseconds: Long, beatMs: Long, firstBeatTimecode: String): Long {

    var delayMs = convertTimecodeToMilliseconds(firstBeatTimecode)
    val diff = ((delayMs / (beatMs * 4))-1) * (beatMs * 4)
    delayMs -= diff

    val firstBeatMs = delayMs
    // println("Время звучания 1 бита = $beatMs ms")
//    val firstBeatMs = convertTimecodeToMilliseconds(firstBeatTimecode)
    // println("Первый отмеченый бит находится от начала в $firstBeatMs ms")
    // println("Время = $timeInMilliseconds ms")
    var timeInMillsCorrected = timeInMilliseconds - firstBeatMs
    // println("Время после сдвигания = $timeInMillsCorrected ms")
    val count4beatsBefore = (timeInMillsCorrected / (beatMs * 4))
    // println("Перед первым временем находится как минимум $count4beatsBeafore тактов по 4 бита")
    val different = count4beatsBefore * (beatMs * 4)
    // println("Надо сдвинуть время на $different ms")
    timeInMillsCorrected -= different
    // println("После сдвига время находится от начала в $timeInMillsCorrected ms и это должно быть меньше, чем ${(beatMs * 4).toLong()} ms")
    // println("Результат = $result")
    return ((timeInMillsCorrected / (beatMs)) % 4) + 1
}

fun getBeatNumberByTimecode(timeInTimecode: String, beatMs: Long, firstBeatTimecode: String): Long {
    return getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(timeInTimecode), beatMs, firstBeatTimecode)
}
fun getDurationInMilliseconds(start: String, end: String): Long {
    return convertTimecodeToMilliseconds(end) - convertTimecodeToMilliseconds(start)
}

fun getDiffInMilliseconds(firstTimecode: String, secondTimecode: String): Long {
    return convertTimecodeToMilliseconds(firstTimecode) - convertTimecodeToMilliseconds(secondTimecode)
}

fun getSymbolWidth(fontSizePt: Int): Double {
    // Получение ширины символа (в пикселях) для размера шрифта (в пунктах)
    return fontSizePt*0.6
}

fun getFontSizeBySymbolWidth(symbolWidthPx: Double): Int {
    // Получение размера шрифта (в пунктах) для ширины символа (в пикселах)
    return (symbolWidthPx/0.6).toInt()
}

fun replaceVowelOrConsonantLetters(str: String, isVowel: Boolean = true, replSymbol: String = " "): String {
    var result = ""
    str.forEach { symbol ->
        if ((symbol in LETTERS_VOWEL) == isVowel) result += replSymbol else result += symbol
    }
    return result
}

class Ribbon(private val input: String) {
    private var position = -1
    private val length: Int
    private var flag = -1
    private var startSyllableIndex = 0
    private var endSyllableIndex = 0

    init {
        length = input.length
    }

    fun setEndSyllableIndex() {
        endSyllableIndex = position
    }

    fun extractSyllable(): String {
        val result = input.substring(startSyllableIndex, endSyllableIndex + 1)
        startSyllableIndex = endSyllableIndex + 1
        flag = position
        endSyllableIndex = 0
        return result
    }

    fun readCurrentPosition(): Char {
        check(!(position < 0 || position > length - 1))
        return input[position]
    }

    fun setFlag() {
        flag = position
    }

    fun rewindToFlag() {
        if (flag >= 0) {
            position = flag
        }
    }

    fun moveHeadForward(): Boolean {
        return if (position + 1 < length) {
            position++
            true
        } else {
            false
        }
    }
}

class MainRibbon {
    val vowels = "аеёиоуыюяэАЕЁИОУЫЮЯЭeuioayYEUIOAїієѣ"
    val nonPairConsonant = "лйрнмЛЙРНМ.,:-"
    fun syllables(inputString: String?, delimiter: String = "|"): List<String> {
        if (inputString == null) return emptyList()
        val result: MutableList<String> = ArrayList()
        val inputList = inputString.split(delimiter)
//        val inputList = listOf(inputString)
        inputList.forEach { input ->
//            if (!input.containThisSymbols(vowels)) {
//                result.add(input)
//            } else {
                val ribbon = Ribbon(input)
                while (ribbon.moveHeadForward()) {
                    ribbon.setFlag()
                    if (checkVowel(ribbon.readCurrentPosition())) {
                        if (ribbon.moveHeadForward() && ribbon.moveHeadForward()) {
                            if (checkVowel(ribbon.readCurrentPosition())) {
                                ribbon.rewindToFlag()
                                ribbon.setEndSyllableIndex()
                                result.add(ribbon.extractSyllable())
                                continue
                            }
                        }
                        ribbon.rewindToFlag()
                        if (ribbon.moveHeadForward() && checkSpecialConsonant(ribbon.readCurrentPosition())) {
                            ribbon.setEndSyllableIndex()
                            result.add(ribbon.extractSyllable())
                            continue
                        }
                        ribbon.rewindToFlag()
                        if (hasMoreVowels(ribbon)) {
                            ribbon.rewindToFlag()
                            ribbon.setEndSyllableIndex()
                            result.add(ribbon.extractSyllable())
                            continue
                        } else {
                            while (ribbon.moveHeadForward());
                            ribbon.setEndSyllableIndex()
                            result.add(ribbon.extractSyllable())
                        }
                    }
                }
//            }

        }

        return result
    }

    fun checkVowel(ch: Char): Boolean {
        return vowels.contains(ch.toString())
    }

    fun hasMoreVowels(ribbon: Ribbon): Boolean {
        while (ribbon.moveHeadForward()) {
            if (checkVowel(ribbon.readCurrentPosition())) {
                return true
            }
        }
        return false
    }

    fun checkSpecialConsonant(ch: Char): Boolean {
        return nonPairConsonant.contains(ch.toString())
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val mainRibbon = MainRibbon()
            println(mainRibbon.syllables("Я однажды проснусь оттого, что пойму: в эту ночь"))
        }
    }
}
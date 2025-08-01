package com.svoemesto.karaokeapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.svoemesto.karaokeapp.mlt.*
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.io.FileUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.odftoolkit.simple.SpreadsheetDocument
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.awt.*
import java.awt.image.BufferedImage
import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import javax.imageio.ImageIO
import javax.sound.sampled.AudioSystem
import kotlin.io.path.Path
import kotlin.math.roundToInt
import kotlin.random.Random
//import io.ktor.client.*
//import io.ktor.client.engine.cio.*
//import io.ktor.client.request.*
//import io.ktor.client.statement.*
//import org.jsoup.nodes.Document

fun mainUtils() {

}

fun customFunction(): String {

    var total = 0L
    Settings.loadListFromDb(database = WORKING_DATABASE).forEach { sett ->
        val ms = sett.ms
        total += ms
        println("${convertMillisecondsToDtoTimecode(ms)} - (${sett.datePublish}) - ${sett.rightSettingFileName}")
    }
    println("ИТОГО: ${convertMillisecondsToDtoTimecode(total)}")
    return convertMillisecondsToDtoTimecode(total)
}


fun recodePictures() {
    var totalOld: Long = 0
    var totalNew: Long = 0

    Pictures.loadList(database = WORKING_DATABASE)
        .forEach { picture ->
            val picBase64 = picture.full
            val picBytes = Base64.getDecoder().decode(picBase64)
            val picSize = picBytes.size
            val picBi = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(picBase64)))
            val picW = picBi.width
            val picH = picBi.height

            val ios = ByteArrayOutputStream()
            ImageIO.write(picBi, "png", ios)
            val picBytesNew = ios.toByteArray()
            val picSizeNew = picBytesNew.size
            val picBase64New = Base64.getEncoder().encodeToString(picBytesNew)
            totalOld += picSize
            totalNew += picSizeNew
            println("${picture.name} : ${picW} x ${picH} экономия ${picSize - picSizeNew} байт.")
//            picture.full = picBase64New
//            picture.save()
        }
    println("Общая экономия: ${totalOld - totalNew} байт.")
}


fun updateRemoteDatabaseFromLocalDatabase(updateSettings: Boolean = true, updatePictures: Boolean = true): Triple<Int, Int, Int> {
    return updateDatabases(Connection.local(), Connection.remote(), updateSettings, updatePictures)
}

fun updateLocalDatabaseFromRemoteDatabase(updateSettings: Boolean = true, updatePictures: Boolean = true): Triple<Int, Int, Int> {
    return updateDatabases(Connection.remote(), Connection.local(), updateSettings, updatePictures)
}
fun updateDatabases(fromDatabase: KaraokeConnection, toDatabase: KaraokeConnection, updateSettings: Boolean = true, updatePictures: Boolean = true): Triple<Int, Int, Int> {
    if (fromDatabase == toDatabase) return Triple(0,0,0)

    var countCreate = 0
    var countUpdate = 0
    var countDelete = 0

    val listToCreate: MutableList<Map<String, Any>> = mutableListOf()
    val listToUpdate: MutableList<Map<String, Any>> = mutableListOf()
    val listToDelete: MutableList<Map<String, Any>> = mutableListOf()

    if (updateSettings) {

        // Список айдишников базы ИЗ
        val listSettingsFrom = Settings.loadListFromDb(database = fromDatabase)
        println("Таблица tbl_settings, записей в базе ${fromDatabase.name}: ${listSettingsFrom.size}")

        // Список айдишников базы В
        val listSettingsTo = Settings.loadListFromDb(database = toDatabase).toMutableList()
        println("Таблица tbl_settings, записей в базе ${toDatabase.name}: ${listSettingsTo.size}")

        val setToDel = listSettingsTo.map { it.id }.toMutableSet()

        var prc = 0
        var prcPrev = 0
        // Проходимся по айдишникам ИЗ
        listSettingsFrom.forEachIndexed { indexFrom, settingsFrom ->

            prc = indexFrom * 100 / listSettingsFrom.size
            if (prc % 10 == 0 && prc != prcPrev) {
                println("Таблица tbl_settings, $prc%...")
                prcPrev = prc
            }

            // Считываем записи с текущим айди из ИЗ и В
            val settingsTo = listSettingsTo.firstOrNull { it.id == settingsFrom.id }

            // Если в В записи нету - создаём её на основе записи из ИЗ и изменяем айдишник
            if (settingsTo == null) {
                println("Добавляем запись: id=${settingsFrom.id}, author=${settingsFrom.author}, album=${settingsFrom.album}, name=${settingsFrom.songName}")
                val sqlToInsert = settingsFrom.getSqlToInsert()
                if (toDatabase.name == "SERVER") {

                    val setStrEncrypted = Crypto.encrypt(sqlToInsert)
                    val values: Map<String, Any> = mapOf(
                        "sqlToInsert" to (setStrEncrypted ?: "")
                    )
                    listToCreate.add(values)
                } else {
                    val connection = toDatabase.getConnection()
                    val ps = connection.prepareStatement(sqlToInsert)
                    ps.executeUpdate()
                    ps.close()
                }
                countCreate++

            } else {

                setToDel.remove(settingsFrom.id)

                // Если записи есть в обоих базах - получаем их дифы
                val diff = Settings.getDiff(settingsFrom, settingsTo)

                // Если диффы есть - вносим изменения в базу В
                if (diff.isNotEmpty()) {
                    println("Изменяем запись: id=${settingsFrom.id}, author=${settingsFrom.author}, album=${settingsFrom.album}, name=${settingsFrom.songName}")
                    val messageRecordChange = RecordChangeMessage(tableName = "tbl_settings",  recordId = settingsTo.id, diffs = diff, databaseName = toDatabase.name, record = settingsFrom.toDTO())

                    if (toDatabase.name == "SERVER") {

                        val setStr = messageRecordChange.getSetString()
                        if (setStr != "") {
                            println(setStr)

                            val setStrEncrypted = Crypto.encrypt(setStr)
                            val values: Map<String, Any> = mapOf(
                                "tableName" to messageRecordChange.tableName,
                                "idRecord" to messageRecordChange.recordId,
                                "setText" to (setStrEncrypted ?: "")
                                )
                            listToUpdate.add(values)
                        }

                    } else {
                        val setStr = diff.filter{ it.recordDiffRealField }.map { "${it.recordDiffName} = ?" }.joinToString(", ")
                        if (setStr != "") {
                            val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

                            val connection = toDatabase.getConnection()
                            val ps = connection.prepareStatement(sql)

                            var index = 1
                            diff.filter{ it.recordDiffRealField }.forEach {
                                if (it.recordDiffValueNew is Long) {
                                    ps.setLong(index, it.recordDiffValueNew.toLong())
                                } else {
                                    ps.setString(index, it.recordDiffValueNew.toString())
                                }
                                index++
                            }
                            ps.setLong(index, settingsTo.id)
                            ps.executeUpdate()
                            ps.close()

//                            println(messageRecordChange.toString())


                        }
                    }
                    countUpdate++


                }

            }
        }

        val listSettingsToDel: MutableList<Settings> = mutableListOf()
        setToDel.toList().forEach { idToDel ->
            val settingsFrom = listSettingsFrom.firstOrNull { it.id == idToDel }
            if (settingsFrom == null) {
                val settingsTo = listSettingsTo.firstOrNull { it.id == idToDel }
                if (settingsTo != null) {
                    println("Проверка на необходимость удаления записи: id=${settingsTo.id}, author=${settingsTo.author}, album=${settingsTo.album}, name=${settingsTo.songName}")
                    listSettingsToDel.add(settingsTo)
                }
            }
        }

        listSettingsToDel.forEach { toDel ->
            if (toDatabase.name == "SERVER") {
                val sqlToDelete = "DELETE FROM tbl_settings WHERE id = ${toDel.id}"
                val setStrEncrypted = Crypto.encrypt(sqlToDelete)
                val values: Map<String, Any> = mapOf(
                    "sqlToDelete" to (setStrEncrypted ?: "")
                )
                listToDelete.add(values)
            } else {
                Settings.deleteFromDb(id = toDel.id, database = toDatabase)
            }

            countDelete++
        }

    }


    if (updatePictures) {

        // Список айдишников базы ИЗ
        val listPicturesFrom = Pictures.loadList(database = fromDatabase)
        println("Таблица tbl_pictures, записей в базе ${fromDatabase.name}: ${listPicturesFrom.size}")

        // Список айдишников базы В
        val listPicturesTo = Pictures.loadList(database = toDatabase).toMutableList()
        println("Таблица tbl_pictures, записей в базе ${toDatabase.name}: ${listPicturesTo.size}")

        val setToDel = listPicturesTo.map { it.id }.toMutableSet()

        var prc = 0
        var prcPrev = 0
        // Проходимся по айдишникам ИЗ
        listPicturesFrom.forEachIndexed { indexFrom, pictureFrom ->

            prc = indexFrom * 100 / listPicturesFrom.size
            if (prc % 10 == 0 && prc != prcPrev) {
                println("Таблица tbl_pictures, $prc%...")
                prcPrev = prc
            }

            // Считываем записи с текущим айди из ИЗ и В
            val pictureTo = listPicturesTo.firstOrNull { it.id == pictureFrom.id }

            // Если в В записи нету - создаём её на основе записи из ИЗ и изменяем айдишник
            if (pictureTo == null) {
                println("Добавляем запись: id=${pictureFrom.id}, picture_name=${pictureFrom.name}")
                val sqlToInsert = pictureFrom.getSqlToInsert()
                if (toDatabase.name == "SERVER") {

                    val setStrEncrypted = Crypto.encrypt(sqlToInsert)
                    val values: Map<String, Any> = mapOf(
                        "sqlToInsert" to (setStrEncrypted ?: "")
                    )
                    listToCreate.add(values)
                } else {
                    val connection = toDatabase.getConnection()
                    val ps = connection.prepareStatement(sqlToInsert)
                    ps.executeUpdate()
                    ps.close()
                }
                countCreate++

            } else {

                setToDel.remove(pictureFrom.id)

                // Если записи есть в обоих базах - получаем их дифы
                val diff = Pictures.getDiff(pictureFrom, pictureTo)

                // Если диффы есть - вносим изменения в базу В
                if (diff.isNotEmpty()) {
                    println("Изменяем запись: id=${pictureFrom.id}, picture_name=${pictureFrom.name}")
                    val messageRecordChange = RecordChangeMessage(tableName = "tbl_pictures",  recordId = pictureTo.id.toLong(), diffs = diff, databaseName = toDatabase.name, record = pictureFrom)

                    if (toDatabase.name == "SERVER") {

                        val setStr = messageRecordChange.getSetString()
                        if (setStr != "") {
                            println(setStr)

                            val setStrEncrypted = Crypto.encrypt(setStr)
                            val values: Map<String, Any> = mapOf(
                                "tableName" to messageRecordChange.tableName,
                                "idRecord" to messageRecordChange.recordId,
                                "setText" to (setStrEncrypted ?: "")
                            )
                            listToUpdate.add(values)
                        }

                    } else {
                        val setStr = diff.filter{ it.recordDiffRealField }.map { "${it.recordDiffName} = ?" }.joinToString(", ")
                        if (setStr != "") {
                            val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

                            val connection = toDatabase.getConnection()
                            val ps = connection.prepareStatement(sql)

                            var index = 1
                            diff.filter{ it.recordDiffRealField }.forEach {
                                if (it.recordDiffValueNew is Long) {
                                    ps.setLong(index, it.recordDiffValueNew.toLong())
                                } else {
                                    ps.setString(index, it.recordDiffValueNew.toString())
                                }
                                index++
                            }
                            ps.setLong(index, pictureTo.id.toLong())
                            ps.executeUpdate()
                            ps.close()

//                            println(messageRecordChange.toString())


                        }
                    }
                    countUpdate++


                }

            }
        }

        val listPicturesToDel: MutableList<Pictures> = mutableListOf()
        setToDel.toList().forEach { idToDel ->
            val settingsFrom = listPicturesFrom.firstOrNull { it.id == idToDel }
            if (settingsFrom == null) {
                val pictureTo = listPicturesTo.firstOrNull { it.id == idToDel }
                if (pictureTo != null) {
                    println("Проверка на необходимость удаления записи: id=${pictureTo.id}, picture_name=${pictureTo.name}")
                    listPicturesToDel.add(pictureTo)
                }
            }
        }

        listPicturesToDel.forEach { toDel ->
            if (toDatabase.name == "SERVER") {
                val sqlToDelete = "DELETE FROM tbl_settings WHERE id = ${toDel.id}"
                val setStrEncrypted = Crypto.encrypt(sqlToDelete)
                val values: Map<String, Any> = mapOf(
                    "sqlToDelete" to (setStrEncrypted ?: "")
                )
                listToDelete.add(values)
            } else {
                Pictures.delete(id = toDel.id, database = toDatabase)
            }

            countDelete++
        }

    }

    if (toDatabase.name == "SERVER") {

        val chunkedSize = if (updatePictures) 1 else 10

        println("Запрос на сервер на изменение/добавление/удаление.")

        if (listToCreate.isNotEmpty()) {
            println("Запрос на сервер на добавление.")
            val chunked = listToCreate.chunked(chunkedSize)
            chunked.forEach { lstToCreate ->
                val values: Map<String, Any> = mapOf(
                    "dataCreate" to lstToCreate,
                    "dataUpdate" to emptyList<Map<String, Any>>(),
                    "dataDelete" to emptyList<Map<String, Any>>(),
                    "word" to (Crypto.encrypt(Crypto.wordsToChesk) ?: "")
                )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build();
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString());
                println(response.body())
            }
        }

        if (listToDelete.isNotEmpty()) {
            println("Запрос на сервер на удаление.")

            val chunked = listToDelete.chunked(chunkedSize)
            chunked.forEach { lstToDelete ->
                val values: Map<String, Any> = mapOf(
                    "dataCreate" to emptyList<Map<String, Any>>(),
                    "dataUpdate" to emptyList<Map<String, Any>>(),
                    "dataDelete" to lstToDelete,
                    "word" to (Crypto.encrypt(Crypto.wordsToChesk) ?: "")
                )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build();
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString());
                println(response.body())
            }

        }

        if (listToUpdate.isNotEmpty()) {
            println("Запрос на сервер на изменение.")

            val chunked = listToUpdate.chunked(chunkedSize)
            chunked.forEach { lstToUpdate ->
                val values: Map<String, Any> = mapOf(
                    "dataCreate" to emptyList<Map<String, Any>>(),
                    "dataUpdate" to lstToUpdate,
                    "dataDelete" to emptyList<Map<String, Any>>(),
                    "word" to (Crypto.encrypt(Crypto.wordsToChesk) ?: "")
                )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build();
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString());
                println(response.body())
            }

        }

    }

    return Triple(countCreate, countUpdate, countDelete)

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

@Throws(IOException::class)
fun getMd5HashForFile(filename: String?): String? {
    return try {
        val md: MessageDigest? = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        Files.newInputStream(Paths.get(filename)).use { `is` ->
            var read: Int
            while (`is`.read(buffer).also { read = it } > 0) {
                if (md != null) {
                    md.update(buffer, 0, read)
                }
            }
        }
        val digest: ByteArray = md?.digest() ?: byteArrayOf()
        bytesToHex(digest)
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException(e)
    }
}

fun getMd5Hash(source: String): String? {
    return try {
        val md = MessageDigest.getInstance("MD5")
        md.update(source.toByteArray())
        val digest = md.digest()
        bytesToHex(digest)
    } catch (e: NoSuchAlgorithmException) {
        throw java.lang.RuntimeException(e)
    }
}
fun bytesToHex(bytes: ByteArray): String? {
    val builder = StringBuilder()
    for (b in bytes) {
        builder.append(String.format("%02x", b.toInt() and 0xff))
    }
    return builder.toString()
}

fun updateBpmAndKey(database: KaraokeConnection): Int {
    val listSettings = Settings.loadListFromDb(mapOf("song_tone" to "''", "song_bpm" to "0"), database)
    var counter = 0
    listSettings.forEach { settings ->
        val (bpm, key) = getBpmAndKeyFromCsv(settings)
        if (bpm != 0L && key != "") {
            println("${settings.rightSettingFileName} : bpm = ${bpm}, tone = ${key}")
            settings.fields[SettingField.BPM] = bpm.toString()
            settings.fields[SettingField.KEY] = key
            settings.saveToDb()
            counter++
        }
    }
    return counter
}

fun updateBpmAndKeyLV(database: KaraokeConnection): Pair<Int, Int> {
    val listSettings = Settings.loadListFromDb(mapOf("song_tone" to "''", "song_bpm" to "0"), database)
    var counterSuccess = 0
    var counterFailed = 0
    listSettings.forEach { settings ->
        val sheetstageInfo = settings.sheetstageInfo
        if (sheetstageInfo.isNotEmpty()) {
            val bpm = sheetstageInfo["tempo"] as String
            val key = sheetstageInfo["key"] as String
            if (bpm != "" && key != "") {
                println("${settings.rightSettingFileName} : bpm = ${bpm}, tone = ${key}")
                settings.fields[SettingField.BPM] = bpm
                settings.fields[SettingField.KEY] = key
                settings.saveToDb()
                counterSuccess++
            } else {
                counterFailed++
            }
        }
    }
    return Pair(counterSuccess, counterFailed)
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
        println(csvFilePath)
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



fun delDublicates(database: KaraokeConnection): Int {
    var counter = 0
    val listSettings = Settings.loadListFromDb(
        mapOf(Pair("tags", "DD")), database
    )
    listSettings.forEach { settings ->
        if (settings.tags == "DD") {
            settings.deleteFromDb()
            counter++
        }
    }
    return counter
}

fun clearPreDublicates(database: KaraokeConnection): Int {
    var counter = 0
    val listSettings = Settings.loadListFromDb(
        mapOf(Pair("tags", "D")), database
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

fun markDublicates(autor: String, database: KaraokeConnection): Int {
    var counter = 0
    val listSettings = Settings.loadListFromDb(
        mapOf(Pair("song_author", autor)), database
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

fun create720pForAllUncreated(database: KaraokeConnection) {

    val settingsList = Settings.loadListFromDb(database = database)
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


fun copyIfNeed(pathFrom: String, pathTo: String, folderTo: String, log: String = ""): Int {
    val fileFrom = File(pathFrom)
    val fileTo = File(pathTo)
    if (fileFrom.exists()) {
        if (!fileTo.exists() || (fileFrom.length() != fileTo.length())) {
            if (!File(folderTo).exists()) Files.createDirectories(Path(folderTo))
            if (log != "") println(log)
            Files.copy(Path(pathFrom), Path(pathTo), StandardCopyOption.REPLACE_EXISTING)
            return 1
        }
    }
    return 0
}

fun collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(settingsList: List<Settings>, priorLyrics: Int = 10, priorKaraoke: Int = 10): Pair<Int, Int> {
    println("Копирование в хранилище и создание заданий на кодирование в 720р")
//    val settingsList = Settings.loadListFromDb(database = database)
    var countCopy = 0;
    var countCode = 0;
    settingsList.forEach { settings ->

        countCopy += copyIfNeed(settings.pathToFileLyrics, settings.pathToStoreFileLyrics, settings.pathToStoreFolderLyrics, "Копируем в хранилище файл: ${settings.nameFileLyrics}")
        countCopy += copyIfNeed(settings.pathToFileKaraoke, settings.pathToStoreFileKaraoke, settings.pathToStoreFolderKaraoke, "Копируем в хранилище файл: ${settings.nameFileKaraoke}")
        countCopy += copyIfNeed(settings.pathToFileChords, settings.pathToStoreFileChords, settings.pathToStoreFolderChords, "Копируем в хранилище файл: ${settings.nameFileChords}")

        val sourceFileLyrics = File(settings.pathToFileLyrics)
        val destinationFileLyrics720 = File(settings.pathToFile720Lyrics)
        val needCreateLyrics720 = if (!sourceFileLyrics.exists()) {
            false
        } else {
            if (!destinationFileLyrics720.exists()) {
                true
            } else {
                if (sourceFileLyrics.lastModified() > destinationFileLyrics720.lastModified()) {
                    destinationFileLyrics720.delete()
                    true
                } else {
                    false
                }
            }
        }
        if (needCreateLyrics720) {
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileLyrics}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, priorLyrics)
            countCode++
        }


        val sourceFileKaraoke = File(settings.pathToFileKaraoke)
        val destinationFileKaraoke720 = File(settings.pathToFile720Karaoke)
        val needCreateKaraoke720 = if (!sourceFileKaraoke.exists()) {
            false
        } else {
            if (!destinationFileKaraoke720.exists()) {
                true
            } else {
                if (sourceFileKaraoke.lastModified() > destinationFileKaraoke720.lastModified()) {
                    destinationFileKaraoke720.delete()
                    true
                } else {
                    false
                }
            }
        }
        if (needCreateKaraoke720) {
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileKaraoke}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_KAR, true, priorKaraoke)
            countCode++
        }

    }
    return Pair(countCopy, countCode)
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
    val sourceTextContainsRussianLetters = sourceText.containThisSymbols(RUSSIN_LETTERS)
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

    result = result.replace("_"," ")
    result = result.replace(",",", ")
    result = result.replace(",  ",", ")
    result = result.replace("--","-")
    result = result.replace("—","-")
    result = result.replace("–","-")
    result = result.replace("−","-")
    result = result.replace(" : ",": ")
    result = result.replace(" :\n",":\n")

    if (sourceTextContainsRussianLetters) {
        val lines = result.split("\n")
        val linesWithoutChords: MutableList<String> = mutableListOf()
        lines.forEach { line ->
            if (!(line.containThisSymbols(ENGLISH_LETTERS) && !line.containThisSymbols(RUSSIN_LETTERS))) {
                linesWithoutChords.add(line)
            }
        }
        result = linesWithoutChords.joinToString("\n")

        result = result.replace("p","р")
        result = result.replace("y","у")
        result = result.replace("e","е")
        result = result.replace("o","о")
        result = result.replace("a","а")
        result = result.replace("x","х")
        result = result.replace("c","с")
        result = result.replace("A","А")
        result = result.replace("T","Т")
        result = result.replace("O","О")
        result = result.replace("P","Р")
        result = result.replace("H","Н")
        result = result.replace("K","К")
        result = result.replace("X","Х")
        result = result.replace("C","С")
        result = result.replace("B","В")
        result = result.replace("M","М")
    }


//    result = result.replace(" -\n","_-\n")

    return result
}

fun createFilesByTags(listOfTags: List<String> = emptyList(), database: KaraokeConnection) {
    val listTags = (if (listOfTags.isEmpty()) Settings.getSetOfTags(database = database) else listOfTags.map { it.uppercase() }.toSet()).toList()
    listTags.forEach { tag ->

        val pathToTagFolder = "$PATH_TO_STORE_FOLDER/TAGS/${tag}"
        if (!File(pathToTagFolder).exists()) Files.createDirectories(Path(pathToTagFolder))

        val pathToTagFolder720Karaoke = "$PATH_TO_STORE_FOLDER/720p_Karaoke/TAGS/${tag}"
        if (!File(pathToTagFolder720Karaoke).exists()) Files.createDirectories(Path(pathToTagFolder720Karaoke))

        val listOfSettings = Settings.loadListFromDb(mapOf(Pair("tags", tag)), database = database)
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

fun createDigestForAllAuthors(vararg authors: String, database: KaraokeConnection) {

    val listAuthors = getAuthorsForDigest(database = database)
    listAuthors.forEach { author ->
        if (authors.isEmpty() || author in authors) {
            var txt = "ЗАКРОМА - «$author»\n\n${getAuthorDigest(author, false, database).first}"
            val fileName = "/home/nsa/Documents/Караоке/Digest/${author} (digest).txt"
            File(fileName).writeText(txt, Charsets.UTF_8)
        }
    }

}

fun createDigestForAllAuthorsForOper(vararg authors: String, database: KaraokeConnection) {

    val listAuthors = getAuthorsForDigest(database = database)
    var txt = ""
    var total = 0
    listAuthors.forEach { author ->
        if (authors.isEmpty() || author in authors) {
            val (digest, count) = getAuthorDigest(author, false, database)
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

fun getAuthorsForDigest(database: KaraokeConnection): List<String> {

    val connection = database.getConnection()
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
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }
    return emptyList()

}

fun getAuthorDigest(author: String, withRazor: Boolean = true, database: KaraokeConnection): Pair<String, Int> {

    val MAX_SYMBOLS = 16300

    val listDigest = Settings.loadListFromDb(mapOf(Pair("song_author", author)), database)
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


fun updateSettingsFromDb(startFolder: String, database: KaraokeConnection) {
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        println("updateSettingsFromDb: $pathToSettingsFile")
        val settings = Settings.loadFromFile(pathToSettingsFile, database = database)
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

fun createRunMlt(startFolder: String, database: KaraokeConnection) {
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

            val settings = Settings.loadFromFile(pathToSettingsFile, database = database)
            val hasChords = true
            val pathToMltLyrics = settings.getOutputFilename(SongOutputFile.MLT, SongVersion.LYRICS)
            val pathToMltKaraoke = settings.getOutputFilename(SongOutputFile.MLT, SongVersion.KARAOKE)
            val pathToMltChords = settings.getOutputFilename(SongOutputFile.MLT, SongVersion.CHORDS)

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


            var file = File(settings.getOutputFilename(SongOutputFile.RUN, SongVersion.LYRICS))
            file.writeText(txtLyric)
            Files.setPosixFilePermissions(file.toPath(), permissions)


            file = File(settings.getOutputFilename(SongOutputFile.RUN, SongVersion.KARAOKE))
            file.writeText(txtKaraoke)
            Files.setPosixFilePermissions(file.toPath(), permissions)

            if (hasChords) {
                file = File(settings.getOutputFilename(SongOutputFile.RUN, SongVersion.CHORDS))
                file.writeText(txtChords)
                Files.setPosixFilePermissions(file.toPath(), permissions)
            }

            file = File(settings.getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALL]"))
            file.writeText(songTxtAll)
            Files.setPosixFilePermissions(file.toPath(), permissions)

            file = File(settings.getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALLwoLYRICS]"))
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

fun createVKtext(startFolder: String, fromDb: Boolean = false, database: KaraokeConnection) {
    val spreadsheetDocument = if (fromDb) SpreadsheetDocument.loadDocument(File(PATH_TO_ODS)) else null
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        val settings = Settings.loadFromFile(pathToSettingsFile, database = database)
        val song = Song(settings, SongVersion.LYRICS)
        val fileName = settings.getOutputFilename(SongOutputFile.VK, SongVersion.LYRICS)
        val decsAndName = Ods.getSongVKDescription(song, fileName, spreadsheetDocument)
        decsAndName?.let { (text, name) ->
            if (text != "") {
                println(name)
                File(name).writeText(text)
                val vkPictNameOld = (settings.getOutputFilename(SongOutputFile.PICTUREVK, SongVersion.LYRICS)).replace(" [lyrics] VK"," [VK]")
                val vkPictNameNew = name.replace(" [VK].txt", " [VK].png")
                FileUtils.copyFile(File(vkPictNameOld), File(vkPictNameNew))
            } else {
                return@forEach
            }
        }
    }
    if (fromDb) spreadsheetDocument!!.close()
}
fun createBoostyTeserPictures(startFolder: String, database: KaraokeConnection) {
    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->
        val settings = Settings.loadFromFile(pathToSettingsFile, database = database)
        println(pathToSettingsFile)
        createBoostyTeaserPicture(settings)
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

fun createVKLinkPictures(database: KaraokeConnection) {
    val listSettings = Settings.loadListFromDb(database = database)
    listSettings.forEach { settings ->
        println("${settings.datePublish} - ${settings.author} - ${settings.year} - ${settings.album} - ${settings.songName}")
        try {
            createVKLinkPicture(settings)
        } catch (e: Exception) {
            println("Пропускаем.")
        }
    }
}

fun createDescriptionFilesForAll(startFolder: String, database: KaraokeConnection) {

    val listFiles = getListFiles(startFolder,"settings")
    listFiles.forEach { pathToSettingsFile ->

        println(pathToSettingsFile)

        try {
            val settings = Settings.loadFromFile(pathToSettingsFile, database = database)

            File(settings.getOutputFilename(SongOutputFile.DESCRIPTION, SongVersion.LYRICS)).writeText(settings.getDescription(SongVersion.LYRICS))

            File(settings.getOutputFilename(SongOutputFile.DESCRIPTION, SongVersion.KARAOKE)).writeText(settings.getDescription(SongVersion.KARAOKE))

            File(settings.getOutputFilename(SongOutputFile.DESCRIPTION, SongVersion.CHORDS)).writeText(settings.getDescription(SongVersion.CHORDS))

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
                "<mlt LC_NUMERIC=\"C\" producer=\"main_bin\" version=\"7.28.0\" root=\"${fileFolder}\">\n" +
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
            val nutRectangleMltShape = if (capo == 0) Karaoke.chordLayoutNutsRectangleMltShape.copy() else Karaoke.chordLayoutCapoRectangleMltShape.copy()
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

fun createSongTextFile(settings: Settings, songVersion: SongVersion) {

    val fileText = File(settings.getOutputFilename(SongOutputFile.TEXT, songVersion))
    Files.createDirectories(Path(fileText.parent))
    val text = settings.getTextBody()
    fileText.writeText(text)

}

fun createSongDescriptionFile(settings: Settings, songVersion: SongVersion) {

    val fileText = File(settings.getOutputFilename(SongOutputFile.DESCRIPTION, songVersion))
    Files.createDirectories(Path(fileText.parent))
    val text = settings.getDescriptionWithHeaderWOTimecodes(songVersion)
    fileText.writeText(text)

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
    return try {
        Files.walk(Path(pathToFolder)).filter(Files::isRegularFile).map { it.toString() }.filter{ it.endsWith(extention) && it.startsWith("${pathToFolder}/$startWith")}.toList().sorted()
    } catch (e: Exception) {
        emptyList()
    }
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

fun millisecondsToTimeFormatted(milliseconds: Long): String {
    val date = Date(milliseconds)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return formatter.format(date)
}

fun convertMillisecondsToTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%02d:%02d:%02d.%03d".format(hours,minutes,seconds,ms)
}

fun convertMillisecondsToDzenTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%01d:%02d:%02d".format(hours,minutes,seconds)
}

fun convertMillisecondsToDtoTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000*60*60)
    val minutes = (milliseconds - hours*1000*60*60) / (1000*60)
    val seconds = (milliseconds - hours*1000*60*60 - minutes*1000*60) / 1000
    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return (if (hours > 0) "$hours:" else "") + "%02d:%02d".format(minutes,seconds)
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

fun getSyllables(text: String): List<String> {
    val result: MutableList<String> = mutableListOf();
    val regexWords = """\S+""".toRegex(setOf(RegexOption.IGNORE_CASE))
    val words = regexWords.find(text)?.groupValues ?: emptyList()

    val regexSyllables = """[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*[ЁУЕЫАОЭЯИЮEUIOAїієѣ][ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*?(?=[ЦКНГШЩЗХФВПРЛДЖЧСМТБQWRTYPSDFGHJKLZXCVBNM-]?[ЁУЕЫАОЭЯИЮEUIOAїієѣ]|[Й|Y][АИУЕОEUIOAїієѣ])""".toRegex(setOf(RegexOption.IGNORE_CASE))

    words.forEach { word ->
        val syllables = regexSyllables.replace(word) { m -> "${m.value} " }.split(" ")
        if (syllables.isEmpty()) {
            result.add("${word}_")
        } else {
            syllables.forEachIndexed { j, syllable ->
                result.add("${syllable}${if (j == syllables.size -1) "_" else ""}")
            }
        }
    }

    var i = 0
    while (i < result.size) {
        val word = result[i]
        if (!word.haveVowel()) {
            if (i == result.size-1 && (word == "-_" && i != 0)) {
                result[i-1] = "${result[i-1]}${word}"
                result.removeAt(i)
                i--
            } else if (i < result.size-2) {
                result[i+1] = "${word}${result[i+1]}"
                result.removeAt(i)
                i--
            }
        }
        i++
    }
    return result

}

//class Ribbon(private val input: String) {
//    private var position = -1
//    private val length: Int
//    private var flag = -1
//    private var startSyllableIndex = 0
//    private var endSyllableIndex = 0
//
//    init {
//        length = input.length
//    }
//
//    fun setEndSyllableIndex() {
//        endSyllableIndex = position
//    }
//
//    fun extractSyllable(): String {
//        val result = input.substring(startSyllableIndex, endSyllableIndex + 1)
//        startSyllableIndex = endSyllableIndex + 1
//        flag = position
//        endSyllableIndex = 0
//        return result
//    }
//
//    fun readCurrentPosition(): Char {
//        check(!(position < 0 || position > length - 1))
//        return input[position]
//    }
//
//    fun setFlag() {
//        flag = position
//    }
//
//    fun rewindToFlag() {
//        if (flag >= 0) {
//            position = flag
//        }
//    }
//
//    fun moveHeadForward(): Boolean {
//        return if (position + 1 < length) {
//            position++
//            true
//        } else {
//            false
//        }
//    }
//}
//
//class MainRibbon {
//    val vowels = "аеёиоуыюяэАЕЁИОУЫЮЯЭeuioayYEUIOAїієѣ"
//    val nonPairConsonant = "лйрнмЛЙРНМ.,:-"
//    fun syllables(inputString: String?, delimiter: String = "|"): List<String> {
//        if (inputString == null) return emptyList()
//        val result: MutableList<String> = ArrayList()
//        val inputList = inputString.split(delimiter)
////        val inputList = listOf(inputString)
//        inputList.forEach { input ->
////            if (!input.containThisSymbols(vowels)) {
////                result.add(input)
////            } else {
//                val ribbon = Ribbon(input)
//                while (ribbon.moveHeadForward()) {
//                    ribbon.setFlag()
//                    if (checkVowel(ribbon.readCurrentPosition())) {
//                        if (ribbon.moveHeadForward() && ribbon.moveHeadForward()) {
//                            if (checkVowel(ribbon.readCurrentPosition())) {
//                                ribbon.rewindToFlag()
//                                ribbon.setEndSyllableIndex()
//                                result.add(ribbon.extractSyllable())
//                                continue
//                            }
//                        }
//                        ribbon.rewindToFlag()
//                        if (ribbon.moveHeadForward() && checkSpecialConsonant(ribbon.readCurrentPosition())) {
//                            ribbon.setEndSyllableIndex()
//                            result.add(ribbon.extractSyllable())
//                            continue
//                        }
//                        ribbon.rewindToFlag()
//                        if (hasMoreVowels(ribbon)) {
//                            ribbon.rewindToFlag()
//                            ribbon.setEndSyllableIndex()
//                            result.add(ribbon.extractSyllable())
//                            continue
//                        } else {
//                            while (ribbon.moveHeadForward());
//                            ribbon.setEndSyllableIndex()
//                            result.add(ribbon.extractSyllable())
//                        }
//                    }
//                }
////            }
//
//        }
//
//        return result
//    }
//
//    fun checkVowel(ch: Char): Boolean {
//        return vowels.contains(ch.toString())
//    }
//
//    fun hasMoreVowels(ribbon: Ribbon): Boolean {
//        while (ribbon.moveHeadForward()) {
//            if (checkVowel(ribbon.readCurrentPosition())) {
//                return true
//            }
//        }
//        return false
//    }
//
//    fun checkSpecialConsonant(ch: Char): Boolean {
//        return nonPairConsonant.contains(ch.toString())
//    }
//
//    companion object {
//        @JvmStatic
//        fun mainn(args: Array<String>) {
//            val mainRibbon = MainRibbon()
//            println(mainRibbon.syllables("Я однажды проснусь оттого, что пойму: в эту ночь"))
//        }
//    }
//}

class Solution {
    fun merge(nums1: IntArray, m: Int, nums2: IntArray, n: Int): Unit {
        val result: MutableList<Int> = mutableListOf()
        result.addAll(nums1.filterIndexed { index, _ -> index < m })
        result.addAll(nums2.filterIndexed { index, _ -> index < n })
        result.sort()
        println(result)
    }
}

// Возвращает "самый длинный элемент", состоящий из слогов самой длинной комбинированной строки всех голосов
fun getLongerElement(songVersion: SongVersion, listOfVoices: List<SettingVoice>): SettingVoiceLineElement? {
    if (listOfVoices.isEmpty()) return null

    val longerElementLastVoice = listOfVoices.last().longerTextElement(songVersion) ?: return null
    val listLongerElementPreviousVoices = listOfVoices.filterIndexed { index, _ -> index < listOfVoices.size }.mapNotNull { it.longerElementPreviousVoice }
    if (listLongerElementPreviousVoices.isEmpty()) {
        return longerElementLastVoice
    } else {
        val syls: MutableList<SettingVoiceLineElementSyllable> = mutableListOf()
        var prevSyl: SettingVoiceLineElementSyllable? = null
        listLongerElementPreviousVoices.forEach { el ->
            val elGetSylls = el.getSyllables()
            elGetSylls.first().previous = prevSyl
            syls.addAll(elGetSylls)
            prevSyl = elGetSylls.last()
        }
        val elGetSylls = longerElementLastVoice.getSyllables()
        elGetSylls.first().previous = prevSyl
        syls.addAll(elGetSylls)
        val result = SettingVoiceLineElement(
            rootId = listOfVoices[0].rootId,
            type = longerElementLastVoice.type,
        )
        result.addSyllables(syls)

        return result
    }
}

// Вычисляет максимальный размер шрифта, чтобы все голоса поместились на экране по ширине
fun getFontSize(songVersion: SongVersion, listOfVoices: List<SettingVoice>): Int {

    var fontSize = 10
    if (listOfVoices.isEmpty()) return fontSize
    val cntVoices = listOfVoices.size
    // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
    val maxTextWidthPx = Karaoke.frameWidthPx.toDouble() - Karaoke.songtextStartPositionXpx * 2
    val longerElement = getLongerElement(songVersion, listOfVoices) ?: return fontSize
    // Ширина в пикселах суммарной самой длинной строки
    var maxTextWidthPxByFontSize = longerElement.w() + Karaoke.songtextStartPositionXpx * (cntVoices - 1)
    val stepIncrease = if (maxTextWidthPxByFontSize > maxTextWidthPx) -1 else 1
    while (true) {
        if ((maxTextWidthPxByFontSize > maxTextWidthPx && stepIncrease < 0) || (maxTextWidthPxByFontSize < maxTextWidthPx && stepIncrease > 0)) {
            fontSize += stepIncrease
            longerElement.fontSize = fontSize
            val longerElementW = longerElement.w()
            maxTextWidthPxByFontSize = longerElementW + Karaoke.songtextStartPositionXpx * (cntVoices - 1)
        } else {
            break
        }
    }

//        voices().forEach { voice ->
//            voice.lines.forEach { line ->
//                line.elements.forEach { element ->
//                    element.fontSize = fontSize
//                }
//            }
//        }
    val longerElementLastVoice = listOfVoices.last().longerTextElement(songVersion) ?: return fontSize
    val elGetSylls = longerElementLastVoice.getSyllables()
    elGetSylls.first().previous = null
    return fontSize
}


fun getAlbumCardTitle(authorYmId: String): String = runBlocking {
    val searchUrl = "https://music.yandex.ru/artist/$authorYmId/albums"
    var result = ""

    try {
        // Создание HttpClient
        val client = HttpClient.newBuilder().build();

        val request = HttpRequest.newBuilder()
            .uri(URI.create(searchUrl))
            .GET()
            .build()
        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        println(response.body())

        // Получение HTML-контента страницы
        val htmlContent = response.body() // EntityUtils.toString(response.entity)

        // Парсинг HTML с помощью Jsoup
        val doc: Document = Jsoup.parse(htmlContent)

        // Находим первый элемент <a>, у которого один из классов начинается с "AlbumCard_titleLink"
        val element = doc.selectFirst("a[class*=AlbumCard_titleLink]")

        if (element !== null) {
            result = element.text().trim()
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }

    result
}

fun String.extractBalancedBracesFromString(startWord: String): String {
    var result = ""
    var currentContent = StringBuilder()
    val firstIndexOfStartWord = this.indexOf(startWord)
    if (firstIndexOfStartWord < 0) return result
    val indexToStartSearch = firstIndexOfStartWord + startWord.length
    val firstChar = this[indexToStartSearch]
    if (firstChar !== '{') return result
    var counter = 0
    for (i in indexToStartSearch until this.length) {
        val currentSymbol = this[i]
        when (currentSymbol) {
            '{' -> {
                currentContent.append(currentSymbol)
                counter++
            }
            '}' -> {
                currentContent.append(currentSymbol)
                counter--
            }
            else -> {
                currentContent.append(currentSymbol)
            }
        }
        if (counter <= 0) return currentContent.toString()
    }
    return result
}

fun String.textBetween(startString: String, endString: String): String {
    var result = ""
    val firstIndexOfStartString = this.indexOf(startString)
    if (firstIndexOfStartString < 0) return result
    val stringToSearch = this.substring(firstIndexOfStartString + startString.length)
    val lastIndexOfStartString = stringToSearch.indexOf(endString)
    if (lastIndexOfStartString < 0) return result
    return stringToSearch.substring(0, lastIndexOfStartString)
}
fun searchLastAlbumYm2(authorYmId: String): String {
    val searchUrl = "https://music.yandex.ru/artist/$authorYmId/albums"
    // Выбор случайного User-Agent
    val randomUserAgent = USER_AGENTS.random()

//    val document = Jsoup.connect(searchUrl).get()
    val document = Jsoup.connect(searchUrl)
        .header("User-Agent", randomUserAgent)
        .header("Referer", "https://music.yandex.ru/ ")
        .get()

    val html = document.html()
    val preloadedAlbums = html.extractBalancedBracesFromString("""\"preloadedAlbums\":""")
    val album = preloadedAlbums.extractBalancedBracesFromString("""\"albums\":[""")
    val result = album.textBetween("""\"title\":\"""", """\",\"""")
    if (result == "") {
        if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
            println("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
            throw Exception("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
        }
        println("preloadedAlbum = $preloadedAlbums")
        println("album = $album")
    }
    return result
}

fun searchLastAlbumYm(authorYmId: String): String {
    val searchUrl = "https://music.yandex.ru/artist/$authorYmId/albums"
//    val document = Jsoup.connect(searchUrl).get()
//    val html = document.html()
//    println(html)
//    val selector = "a[class~=\\bAlbumCard_titleLink\\S*]"
//    val element = document.select(selector).first()
//    println(element)
//    val result = element?.text() ?: ""
////    val result = (document.getElementsByClass("album__caption").first()?.text() ?: "").trim()
//    if (result == "") {
//        if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
//            println("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
//            throw Exception("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
//        }
//    }

    var result = ""

    System.setProperty(WEBDRIVER_CHROMEDRIVER, PATH_TO_CHROMEDRIVER)
    // Настройка опций Chrome
    val options = ChromeOptions()
//    options.addArguments("--headless") // Запуск в безголовом режиме (опционально)
//    options.addArguments("--remote-allow-origins=*")

    // Создание экземпляра WebDriver
    val driver: WebDriver = try {
        ChromeDriver(options)
    } catch (e: AbstractMethodError) {
        println("Не удалось инициализировать WebDriver")
        return result
    }

    try {

        // Открываем веб-страницу
        driver.get(searchUrl)

        // Ждём полной загрузки страницы
        val wait = WebDriverWait(driver, Duration.ofSeconds(10)) // Ожидание до 10 секунд

        // Находим первый элемент <a>, у которого один из классов начинается с "AlbumCard_titleLink"
        val element: WebElement? = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.cssSelector("a[class*=AlbumCard_titleLink]")
        ))

        // Проверяем, что элемент найден и имеет нужный класс
        result = if (element != null && element.getAttribute("class").split("\\s+".toRegex()).any { it.startsWith("AlbumCard_titleLink") }) {
            println("Текст найденного элемента: ${element.text}")
            element.text
        } else {
            println("Элемент не найден")
            ""
        }
    } finally {
        // Закрываем браузер
        driver.quit()
    }

    return result

}

fun getAuthorForRequest(lastAuthor: String = ""): Author? {
    val listSongAuthors = Settings.loadListAuthors(WORKING_DATABASE)
    if (listSongAuthors.isEmpty()) return null
    var requestNewSongLastSuccessAuthor = if (lastAuthor != "") lastAuthor else Karaoke.requestNewSongLastSuccessAuthor

    val authorForRequest = if (requestNewSongLastSuccessAuthor == "") {
        listSongAuthors.first()
    } else {
        var result = ""
        listSongAuthors.forEachIndexed { indexAuthor, author ->
            if (author == requestNewSongLastSuccessAuthor) {
                result = if (indexAuthor < listSongAuthors.size - 1) {
                    listSongAuthors[indexAuthor + 1]
                } else {
                    listSongAuthors[0]
                }
                return@forEachIndexed
            }
        }
        if (result == "") result = listSongAuthors.first()
        result
    }
    var author = Author.load(author = authorForRequest, database = WORKING_DATABASE)


    if (author == null) {
        val newAuthor = Author()
        newAuthor.author = authorForRequest
        Author.createDbInstance(author = newAuthor, database = WORKING_DATABASE)
        author = newAuthor
        println("Автор «$authorForRequest» отсутствует в таблице tbl_authors. Создаём запись.")
    }

    if (author.watched && author.ymId !== "" && author.lastAlbumYm == author.lastAlbumProcessed) {
        return author
    } else {
        println("Поиск для автора «$authorForRequest» не нужен, ищем другого автора...")
        return getAuthorForRequest(authorForRequest)
    }

}
fun checkLastAlbumYm(): Triple<String, String, Int> {
    /*
    -2 - нету автора!
    -1 - ошибка поиска
     0 - поиск успешен, но новых альбомов нет
     1 - поиск успешен, найден новый альбом
     */
    val author = getAuthorForRequest() ?: return Triple("", "", -2)
    val authorForRequest = author.author

    val lastAlbumYm = try {
//        getAlbumCardTitle(author.ymId)
//        searchLastAlbumYm(author.ymId)
        searchLastAlbumYm2(author.ymId)
    } catch (e: Exception) {
        println("Поиск для автора «$authorForRequest» завершился ошибкой.")
        return Triple(authorForRequest, "", -1)
    }

    if (lastAlbumYm == "") {
        println("Поиск для автора «$authorForRequest» выдал пустой результат. Возможно Yandex.Музыка изменила код страницы.")
        return Triple(authorForRequest, "", -1)
    }

    author.lastAlbumYm = lastAlbumYm
    author.save()

    return if (lastAlbumYm == author.lastAlbumProcessed) {
        println("Поиск для автора «$authorForRequest» завершился успешно, но новых альбомов не найдено. (Альбом «$lastAlbumYm» уже был ранее найден.)")
        Triple(authorForRequest, lastAlbumYm, 0)
    } else {
        println("Поиск для автора «$authorForRequest» завершился успешно, найден новый альбом «$lastAlbumYm». (Ранее последним альбомом был «${author.lastAlbumProcessed}».)")
        Triple(authorForRequest, lastAlbumYm, 1)
    }

}

fun setProcessPriority(pid: Long, priority: Int): Boolean {
    try {
        // Используем команду renice для изменения приоритета процесса
        val reniceCommand = listOf("renice", "-n", priority.toString(), "-p", pid.toString())
        val processBuilder = ProcessBuilder(reniceCommand)
        val process = processBuilder.start()

        // Проверяем результат выполнения команды
        val exitCode = process.waitFor()
        return exitCode == 0
//        if (exitCode == 0) {
//            println("Приоритет процесса успешно изменен на $priority")
//        } else {
//            println("Не удалось изменить приоритет процесса")
//        }
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

fun runCommand(args: List<String>): String {

    // Создаем ProcessBuilder сформированным списком аргументов
    val processBuilder = ProcessBuilder(args)

    // Направляем стандартный поток ошибок в стандартный поток вывода для удобства
    processBuilder.redirectErrorStream(true)

    try {
        // Запускаем процесс
        val process = processBuilder.start()

        // Читаем вывод процесса
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val result = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            result.append(line).append("\n")
        }

        // Ждем завершения процесса
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Process exited with error code $exitCode")
        }

        // Возвращаем результат, удаляя последний символ новой строки
        return result.toString().trim()
    } catch (e: Exception) {
        throw RuntimeException("Error running runCommand", e)
    }
}
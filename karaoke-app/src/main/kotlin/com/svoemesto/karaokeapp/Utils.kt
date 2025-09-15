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
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
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

    Settings.loadListFromDb(database = WORKING_DATABASE).map { it.id }.forEach { id ->
//        updateRemoteSettingFromLocalDatabase(id)
    }

    return ""
}


fun recodePictures() {
    var totalOld: Long = 0
    var totalNew: Long = 0

    Pictures.loadListFromDb(database = WORKING_DATABASE)
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

fun setSettingsToSyncRemoteTable(id: Long) {

    val sqlToInsert = Settings.loadFromDbById(id = id, database = Connection.local())?.getSqlToInsert(sync = true)
    if (sqlToInsert != null) {
        Settings.deleteFromDb(id = id, database = Connection.remote(), sync = true)
        val connection = Connection.remote().getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
            return
        }
        val ps = connection.prepareStatement(sqlToInsert)
        ps.executeUpdate()
        ps.close()
    }

}
fun updateRemotePictureFromLocalDatabase(id: Long): Triple<Int, Int, Int> {
    return updateDatabases(Connection.local(), Connection.remote(), updateSettings = false, updatePictures = true, argsPictures = mapOf("id" to id.toString()))
}
fun updateRemoteSettingFromLocalDatabase(id: Long): Triple<Int, Int, Int> {
    return updateDatabases(Connection.local(), Connection.remote(), updateSettings = true, updatePictures = false, argsSettings = mapOf("id" to id.toString()))
}
fun updateRemoteDatabaseFromLocalDatabase(updateSettings: Boolean = true, updatePictures: Boolean = true): Triple<Int, Int, Int> {
    return updateDatabases(Connection.local(), Connection.remote(), updateSettings, updatePictures)
}

fun updateLocalDatabaseFromRemoteDatabase(updateSettings: Boolean = true, updatePictures: Boolean = true): Triple<Int, Int, Int> {
    return updateDatabases(Connection.remote(), Connection.local(), updateSettings, updatePictures)
}
fun updateDatabases(
    fromDatabase: KaraokeConnection,
    toDatabase: KaraokeConnection,
    updateSettings: Boolean = true,
    updatePictures: Boolean = true,
    argsSettings: Map<String, String> = emptyMap(),
    argsPictures: Map<String, String> = emptyMap()
): Triple<Int, Int, Int> {
    if (fromDatabase == toDatabase) return Triple(0,0,0)

    var countCreate = 0
    var countUpdate = 0
    var countDelete = 0

    val listToCreate: MutableList<Map<String, Any>> = mutableListOf()
    val listToUpdate: MutableList<Map<String, Any>> = mutableListOf()
    val listToDelete: MutableList<Map<String, Any>> = mutableListOf()

    println("[${Timestamp.from(Instant.now())}] Устанавливаем связь с базой данный ${fromDatabase.name}...")
    val connFrom = fromDatabase.getConnection()
    if (connFrom == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${fromDatabase.name}")
        return Triple(-1,-1,-1)
    }
    println("[${Timestamp.from(Instant.now())}] Связь с базой данный ${fromDatabase.name} успешно установлена")

    println("[${Timestamp.from(Instant.now())}] Устанавливаем связь с базой данный ${toDatabase.name}...")
    val connTo = toDatabase.getConnection()
    if (connTo == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${toDatabase.name}")
        return Triple(-1,-1,-1)
    }
    println("[${Timestamp.from(Instant.now())}] Связь с базой данный ${toDatabase.name} успешно установлена")

    if (updateSettings) {

        val whereText = if (argsSettings.containsKey("id")) "WHERE id = ${argsSettings["id"]}" else ""
        val tableName = "tbl_settings"
        println("[${Timestamp.from(Instant.now())}] Запрашиваем таблицу хэшей из базы данных ${fromDatabase.name}...")
        val listFromIdsHashes = Settings.listHashes(database = fromDatabase, whereText = whereText)
        if (listFromIdsHashes == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${fromDatabase.name}")
            return Triple(-1,-1,-1)
        }
        println("[${Timestamp.from(Instant.now())}] Таблица хэшей из базы данных ${fromDatabase.name} успешно получена, записей: ${listFromIdsHashes.size}")

        println("[${Timestamp.from(Instant.now())}] Запрашиваем таблицу хэшей из базы данных ${toDatabase.name}...")
        val listToIdsHashes = Settings.listHashes(database = toDatabase, whereText = whereText)
        if (listToIdsHashes == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${toDatabase.name}")
            return Triple(-1,-1,-1)
        }
        println("[${Timestamp.from(Instant.now())}] Таблица хэшей из базы данных ${toDatabase.name} успешно получена, записей: ${listToIdsHashes.size}")

        val totalCountFrom = listFromIdsHashes.size

        if (totalCountFrom == 0) {
            return Triple(0,0,0)
        }

        val idsToInsert = listFromIdsHashes.filter { fromIdHash ->
            listToIdsHashes.none { toIdHash -> toIdHash.id == fromIdHash.id }
        }.map { it.id }

        val idsToUpdate = listFromIdsHashes.filter { fromIdHash ->
            listToIdsHashes.any { toIdHash -> toIdHash.id == fromIdHash.id && toIdHash.recordhash != fromIdHash.recordhash }
        }.map { it.id }

        val idsToDelete = listToIdsHashes.filter { toIdHash ->
            listFromIdsHashes.none { fromIdHash -> toIdHash.id == fromIdHash.id }
        }.map { it.id }

        idsToDelete.forEach { id ->
            if (toDatabase.name == "SERVER") {
                val sqlToDelete = "DELETE FROM $tableName WHERE id = $id"
                val setStrEncrypted = Crypto.encrypt(sqlToDelete)
                val values: Map<String, Any> = mapOf(
                    "sqlToDelete" to (setStrEncrypted ?: "")
                )
                listToDelete.add(values)
            } else {
                Settings.deleteFromDb(id = id, database = toDatabase)
            }
        }

        idsToInsert.forEach { id ->
            val itemFrom = Settings.loadFromDbById(id = id, database = fromDatabase)
            if (itemFrom != null) {
                println("Добавляем запись в $tableName: id=${itemFrom.id}, ${itemFrom.rightSettingFileName}")
                val sqlToInsert = itemFrom.getSqlToInsert()
                if (toDatabase.name == "SERVER") {
                    val setStrEncrypted = Crypto.encrypt(sqlToInsert)
                    val values: Map<String, Any> = mapOf(
                        "sqlToInsert" to (setStrEncrypted ?: "")
                    )
                    listToCreate.add(values)
                } else {
                    val connection = toDatabase.getConnection()
                    if (connection == null) {
                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                        return Triple(-1,-1,-1)
                    }
                    val ps = connection.prepareStatement(sqlToInsert)
                    ps.executeUpdate()
                    ps.close()
                }
                countCreate++
            }
        }

        idsToUpdate.forEach { id ->
            val itemFrom = Settings.loadFromDbById(id = id, database = fromDatabase)
            val itemTo = Settings.loadFromDbById(id = id, database = toDatabase)
            if (itemFrom != null && itemTo != null) {
                val diff = Settings.getDiff(itemFrom, itemTo)
                if (diff.isNotEmpty()) {
                    println("[${Timestamp.from(Instant.now())}] Изменяем запись в $tableName: id=${itemFrom.id}, ${itemFrom.rightSettingFileName}, поля: ${diff.joinToString(", ") { it.recordDiffName }}")
                    val messageRecordChange = RecordChangeMessage(tableName = tableName,  recordId = itemTo.id.toLong(), diffs = diff, databaseName = toDatabase.name, record = itemFrom)
                    if (toDatabase.name == "SERVER") {
                        val setStr = messageRecordChange.getSetString()
                        if (setStr != "") {
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
                            val sql = "UPDATE $tableName SET $setStr WHERE id = ?"
                            val connection = toDatabase.getConnection()
                            if (connection == null) {
                                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                                return Triple(-1,-1,-1)
                            }
                            val ps = connection.prepareStatement(sql)
                            var index = 1
                            diff.filter{ it.recordDiffRealField }.forEach {
                                if (it.recordDiffValueNew is Long) {
                                    ps.setLong(index, it.recordDiffValueNew.toLong())
                                } else if (it.recordDiffValueNew is Int) {
                                    ps.setInt(index, it.recordDiffValueNew.toInt())
                                } else {
                                    ps.setString(index, it.recordDiffValueNew.toString())
                                }
                                index++
                            }
                            ps.setLong(index, itemTo.id.toLong())
                            ps.executeUpdate()
                            ps.close()
                        }
                    }
                    countUpdate++
                }
            }
        }
    }
    if (updatePictures) {

        val whereText = if (argsPictures.containsKey("id")) "WHERE id = ${argsPictures["id"]}" else ""
        val tableName = "tbl_pictures"
        println("[${Timestamp.from(Instant.now())}] Запрашиваем таблицу хэшей из базы данных ${fromDatabase.name}...")
        val listFromIdsHashes = Pictures.listHashes(database = fromDatabase, whereText = whereText)
        if (listFromIdsHashes == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${fromDatabase.name}")
            return Triple(-1,-1,-1)
        }
        println("[${Timestamp.from(Instant.now())}] Таблица хэшей из базы данных ${fromDatabase.name} успешно получена, записей: ${listFromIdsHashes.size}")

        println("[${Timestamp.from(Instant.now())}] Запрашиваем таблицу хэшей из базы данных ${toDatabase.name}...")
        val listToIdsHashes = Pictures.listHashes(database = toDatabase, whereText = whereText)
        if (listToIdsHashes == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${toDatabase.name}")
            return Triple(-1,-1,-1)
        }
        println("[${Timestamp.from(Instant.now())}] Таблица хэшей из базы данных ${fromDatabase.name} успешно получена, записей: ${listToIdsHashes.size}")

        val totalCountFrom = listFromIdsHashes.size

        if (totalCountFrom == 0) {
            return Triple(0,0,0)
        }

        val idsToInsert = listFromIdsHashes.filter { fromIdHash ->
            listToIdsHashes.none { toIdHash -> toIdHash.id == fromIdHash.id }
        }.map { it.id }

        val idsToUpdate = listFromIdsHashes.filter { fromIdHash ->
            listToIdsHashes.any { toIdHash -> toIdHash.id == fromIdHash.id && toIdHash.recordhash != fromIdHash.recordhash }
        }.map { it.id }

        val idsToDelete = listToIdsHashes.filter { toIdHash ->
            listFromIdsHashes.none { fromIdHash -> toIdHash.id == fromIdHash.id }
        }.map { it.id }

        idsToDelete.forEach { id ->
            if (toDatabase.name == "SERVER") {
                val sqlToDelete = "DELETE FROM $tableName WHERE id = $id"
                val setStrEncrypted = Crypto.encrypt(sqlToDelete)
                val values: Map<String, Any> = mapOf(
                    "sqlToDelete" to (setStrEncrypted ?: "")
                )
                listToDelete.add(values)
            } else {
                Pictures.deleteFromDb(id = id.toInt(), database = toDatabase)
            }
        }

        idsToInsert.forEach { id ->
            val itemFrom = Pictures.loadFromDbById(id = id, database = fromDatabase)
            if (itemFrom != null) {
                println("[${Timestamp.from(Instant.now())}] Добавляем запись в $tableName: id=${itemFrom.id}, ${itemFrom.name}")
                val sqlToInsert = itemFrom.getSqlToInsert()
                if (toDatabase.name == "SERVER") {
                    val setStrEncrypted = Crypto.encrypt(sqlToInsert)
                    val values: Map<String, Any> = mapOf(
                        "sqlToInsert" to (setStrEncrypted ?: "")
                    )
                    listToCreate.add(values)
                } else {
                    val connection = toDatabase.getConnection()
                    if (connection == null) {
                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                        return Triple(-1,-1,-1)
                    }
                    val ps = connection.prepareStatement(sqlToInsert)
                    ps.executeUpdate()
                    ps.close()
                }
                countCreate++
            }
        }

        idsToUpdate.forEach { id ->
            val itemFrom = Pictures.loadFromDbById(id = id, database = fromDatabase)
            val itemTo = Pictures.loadFromDbById(id = id, database = toDatabase)
            if (itemFrom != null && itemTo != null) {
                val diff = Pictures.getDiff(itemFrom, itemTo)
                if (diff.isNotEmpty()) {
                    println("[${Timestamp.from(Instant.now())}] Изменяем запись в $tableName: id=${itemFrom.id}, ${itemFrom.name}, поля: ${diff.joinToString(", ") { it.recordDiffName }}")
                    val messageRecordChange = RecordChangeMessage(tableName = tableName,  recordId = itemTo.id.toLong(), diffs = diff, databaseName = toDatabase.name, record = itemFrom)
                    if (toDatabase.name == "SERVER") {
                        val setStr = messageRecordChange.getSetString()
                        if (setStr != "") {
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
                            val sql = "UPDATE $tableName SET $setStr WHERE id = ?"
                            val connection = toDatabase.getConnection()
                            if (connection == null) {
                                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                                return Triple(-1,-1,-1)
                            }
                            val ps = connection.prepareStatement(sql)
                            var index = 1
                            diff.filter{ it.recordDiffRealField }.forEach {
                                if (it.recordDiffValueNew is Long) {
                                    ps.setLong(index, it.recordDiffValueNew.toLong())
                                } else if (it.recordDiffValueNew is Int) {
                                    ps.setInt(index, it.recordDiffValueNew.toInt())
                                } else {
                                    ps.setString(index, it.recordDiffValueNew.toString())
                                }
                                index++
                            }
                            ps.setLong(index, itemTo.id.toLong())
                            ps.executeUpdate()
                            ps.close()
                        }
                    }
                    countUpdate++
                }
            }
        }
    }


    if (toDatabase.name == "SERVER") {

        val chunkedSize = if (updatePictures) 1 else 10

        if (listToCreate.isNotEmpty()) {
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер на добавление.")
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
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер на удаление.")

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
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер на изменение.")

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
            if (!File(settings.pathToFolder720Lyrics).exists()) {
                Files.createDirectories(Path(settings.pathToFolder720Lyrics))
                runCommand(listOf("chmod", "777", settings.pathToFolder720Lyrics))
            }
            println("Создаём задание на кодирование в 720р для файла: ${settings.nameFileLyrics}")
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 1)
        }
        if (File(settings.pathToFileKaraoke).exists() && !File(settings.pathToFile720Karaoke).exists()) {
            if (!File(settings.pathToFolder720Karaoke).exists()) {
                Files.createDirectories(Path(settings.pathToFolder720Karaoke))
                runCommand(listOf("chmod", "777", settings.pathToFolder720Karaoke))
            }
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
            if (!File(folderTo).exists()) {
                Files.createDirectories(Path(folderTo))
                runCommand(listOf("chmod", "777", folderTo))
            }
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
        if (!File(pathToTagFolder).exists()) {
            Files.createDirectories(Path(pathToTagFolder))
            runCommand(listOf("chmod", "777", pathToTagFolder))
        }

        val pathToTagFolder720Karaoke = "$PATH_TO_STORE_FOLDER/720p_Karaoke/TAGS/${tag}"
        if (!File(pathToTagFolder720Karaoke).exists()) {
            Files.createDirectories(Path(pathToTagFolder720Karaoke))
            runCommand(listOf("chmod", "777", pathToTagFolder720Karaoke))
        }

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
            val fileName = "/sm-karaoke/system/Digest/${author} (digest).txt"
            File(fileName).writeText(txt, Charsets.UTF_8)
            runCommand(listOf("chmod", "666", fileName))
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
    val fileName = "/sm-karaoke/system/Digest/OPER_digest.txt"
    File(fileName).writeText(txt, Charsets.UTF_8)
    runCommand(listOf("chmod", "666", fileName))
}

fun getAuthorsForDigest(database: KaraokeConnection): List<String> {

    val connection = database.getConnection()
    if (connection == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
        return emptyList()
    }
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
    runCommand(listOf("chmod", "666", fileText.parent))

}

fun createSongDescriptionFile(settings: Settings, songVersion: SongVersion) {

    val fileText = File(settings.getOutputFilename(SongOutputFile.DESCRIPTION, songVersion))
    Files.createDirectories(Path(fileText.parent))
    val text = settings.getDescriptionWithHeaderWOTimecodes(songVersion)
    fileText.writeText(text)
    runCommand(listOf("chmod", "666", fileText.parent))
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

    val notesSymbols = "●∙◉♪"
    val notesFont = Font("Arial Unicode MS",font.style, font.size)
    var notesString = ""
    var notNotesString = ""
    text.forEach { symbol ->
        if (notesSymbols.contains(symbol)) {
            notesString += symbol
        } else {
            notNotesString += symbol
        }
    }

    val graphics2D1 = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
    graphics2D1.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2D1.font = font
    val rect1 = graphics2D1.fontMetrics.getStringBounds(notNotesString, graphics2D1)

    val graphics2D2 = BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
    graphics2D2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2D2.font = notesFont
    val rect2 = graphics2D2.fontMetrics.getStringBounds(notesString, graphics2D2)

    return Pair(rect1.width + rect2.width, Math.max(rect1.height, rect2.height))
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
            runCommand(listOf("chmod", "666", fileNameNewSubs))
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
    runCommand(listOf("chmod", "666", pathToFileTo))
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

fun createScriptForHost(args: List<String>) {
    val txt = args.joinToString(" ")
    val fileName = "/sm-karaoke/system/scriptsFromDocker/${UUID.randomUUID()}.sh"
    val file = File(fileName)
    file.writeText(txt)
    runCommand(listOf("chmod", "777", fileName))
}

fun runCommand(args: List<String>, ignoreErrors: Boolean = false): String {

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
        if (exitCode != 0 && !ignoreErrors) {
            throw RuntimeException("Process exited with error code $exitCode")
        }

        // Возвращаем результат, удаляя последний символ новой строки
        return result.toString().trim()
    } catch (e: Exception) {
        throw RuntimeException("Error running runCommand", e)
    }
}

fun getTransposingChord(originalChord: String, capo: Int = 0): String {
    if (capo == 0) return originalChord
    val chordNameAndFret = originalChord.split("|")
    val nameChord = chordNameAndFret[0]
    val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
    val (chord, note) = MusicChord.getChordNote(nameChord)
    var newIndexNote = MusicNote.values().indexOf(note!!) - capo
    if (newIndexNote < 0) newIndexNote += MusicNote.values().size
    val newNote = MusicNote.values()[newIndexNote]
    return newNote.names.first() + chord!!.names.first()
}
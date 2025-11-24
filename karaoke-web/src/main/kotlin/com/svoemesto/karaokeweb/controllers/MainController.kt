package com.svoemesto.karaokeweb.controllers


import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.WEB_WORK_IN_CONTAINER
import com.svoemesto.karaokeweb.WORKING_DATABASE
//import com.svoemesto.karaokeweb.services.KSS_WEB
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import java.time.Instant

@Controller
class MainController(
    @Suppress("unused") private val webSocket: SimpMessagingTemplate,
    @Value($$"${work-in-container}") val wic: Long,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient
    ) {

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
        println("WEB_WORK_IN_CONTAINER = $WEB_WORK_IN_CONTAINER")
        println("storageService = $storageService")
        println("storageApiClient = $storageApiClient")
    }

    @GetMapping("/")
    fun main(
        model: Model,
        request: HttpServletRequest
    ): String {
        model.addAttribute("onBoosty", StatBySong.getCountSongsInCollection(database = WORKING_DATABASE))
        model.addAttribute("onAir", StatBySong.getCountSongsOnAir(database = WORKING_DATABASE))
        doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "main", "parameters" to emptyMap<String, Any>(), "referer" to request.remoteHost))
        return "main"
    }

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        model: Model,
        request: HttpServletRequest
    ): String {
        val data: MutableMap<String, Any> = mutableMapOf()
        author?.let {
            data["author"] = it
        }
        model.addAttribute("author_init", author ?: "")
        model.addAttribute("authors", Settings.loadListAuthors(database = WORKING_DATABASE))
        model.addAttribute("zakroma", Zakroma.getZakroma(
            author = author ?: "",
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        ))
        doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "zakroma", "parameters" to data, "referer" to request.remoteHost))
        return "zakroma"
    }

//    @PostMapping("/registerevent")

    @PostMapping("/registerevent")
    @ResponseBody
    fun doRegisterEvent(
        @RequestParam(required = true) data: Map<String, Any>
    ): Boolean {
        println("Вызов registerevent $data")
        if (!data.containsKey("eventType")) return false
        val eventType = data["eventType"] as String
        when (eventType) {
            "clickToLink" -> {
                if (!data.containsKey("linkType")) return false
                val linkType = data["linkType"] as String
                when (linkType) {
                    "linkToSocialNetwork" -> {
                        if (!data.containsKey("linkName")) return false
                        val linkName = data["linkName"] as String
                        println("Переход в соцсеть: $linkName")
                        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()
                        fieldsValues.add(Pair("event_type", "clickToLink"))
                        fieldsValues.add(Pair("link_type", "linkToSocialNetwork"))
                        fieldsValues.add(Pair("link_name", linkName))
                        val connection = WORKING_DATABASE.getConnection()
                        if (connection == null) {
                            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
                            return false
                        }
                        val sqlToInsert = "INSERT INTO tbl_events (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
                            fieldsValues.joinToString(
                                ", "
                            ) { if (it.second is Long) "${it.second}" else "'${it.second.toString().rightFileName()}'" }
                        })"
                        val ps = connection.prepareStatement(sqlToInsert)
                        ps.executeUpdate()
                        ps.close()
                    }
                    "linkToSong" -> {
                        if (!data.containsKey("linkName")) return false
                        val linkName = data["linkName"] as String
                        if (!data.containsKey("songId")) return false
                        val songId = (data["songId"] as String).toLong()
                        if (!data.containsKey("songVersion")) return false
                        val songVersion = data["songVersion"] as String
                        println("Переход на просмотр: сайт $linkName, id=$songId, Версия: $songVersion")
                        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()
                        fieldsValues.add(Pair("event_type", "clickToLink"))
                        fieldsValues.add(Pair("link_type", "linkToSong"))
                        fieldsValues.add(Pair("link_name", linkName))
                        fieldsValues.add(Pair("song_id", songId))
                        fieldsValues.add(Pair("song_version", songVersion))
                        val connection = WORKING_DATABASE.getConnection()
                        if (connection == null) {
                            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
                            return false
                        }
                        val sqlToInsert = "INSERT INTO tbl_events (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
                            fieldsValues.joinToString(
                                ", "
                            ) { if (it.second is Long) "${it.second}" else "'${it.second.toString().rightFileName()}'" }
                        })"
                        val ps = connection.prepareStatement(sqlToInsert)
                        ps.executeUpdate()
                        ps.close()
                    }

                    else -> {}
                }


            }
            "play" -> {
                if (!data.containsKey("songId")) return false
                val songId = (data["songId"] as String).toLong()
                if (!data.containsKey("songVersion")) return false
                val songVersion = data["songVersion"] as String
                println("Просмотр на странице: id=$songId, Версия: $songVersion")
                val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()
                fieldsValues.add(Pair("event_type", "play"))
                fieldsValues.add(Pair("song_id", songId))
                fieldsValues.add(Pair("song_version", songVersion))
                val connection = WORKING_DATABASE.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
                    return false
                }
                val sqlToInsert = "INSERT INTO tbl_events (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
                    fieldsValues.joinToString(
                        ", "
                    ) { if (it.second is Long) "${it.second}" else "'${it.second.toString().rightFileName()}'" }
                })"
                val ps = connection.prepareStatement(sqlToInsert)
                ps.executeUpdate()
                ps.close()
            }
            "callRest" -> {
                val restName = data["restName"] as String
                val parameters = data["parameters"] as Map<*, *>
                println("Вызван рест $restName с параметрами $parameters")
                val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()
                fieldsValues.add(Pair("event_type", "callRest"))
                fieldsValues.add(Pair("rest_name", restName))
                fieldsValues.add(Pair("rest_parameters", parameters.toString()))
                fieldsValues.add(Pair("referer", data["referer"]?:""))
                val connection = WORKING_DATABASE.getConnection()
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
                    return false
                }
                if (parameters.containsKey("id")) fieldsValues.add(Pair("song_id", parameters["id"]!!.toString().toLong()))
                val sqlToInsert = "INSERT INTO tbl_events (${fieldsValues.joinToString(", ") { it.first }}) OVERRIDING SYSTEM VALUE VALUES(${
                    fieldsValues.joinToString(
                        ", "
                    ) { if (it.second is Long) "${it.second}" else "'${it.second.toString().rightFileName()}'" }
                })"
                val ps = connection.prepareStatement(sqlToInsert)
                ps.executeUpdate()
                ps.close()
            }
            else -> {}
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    @PostMapping("/changerecords")
    @ResponseBody
    fun doChangeRecords(
        @RequestBody(required = true) data: Map<String, Any>
    ): String {
        try {
            val word = data["word"] as String
            if (Crypto.decrypt(word) != Crypto.WORDS_TO_CHECK) return "Не удалось расшифровать кодовое слово"

            val dataCreate = data["dataCreate"] as List<Map<String, Any>>
            val dataUpdate = data["dataUpdate"] as List<Map<String, Any>>
            val dataDelete = data["dataDelete"] as List<Map<String, Any>>

            val connection = WORKING_DATABASE.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
                return "ERROR: Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}"
            }
            dataCreate.forEach { action ->
                val sqlToInsert = action["sqlToInsert"] as String
                val sqlToInsertDecrypted = Crypto.decrypt(sqlToInsert)
                val ps = connection.prepareStatement(sqlToInsertDecrypted)
                ps.executeUpdate()
                ps.close()
            }

            dataUpdate.forEach { action ->
                val tableName = action["tableName"] as String
                val idRecord = action["idRecord"] as Int
                val setText = action["setText"] as String
                val setTextDecrypted = Crypto.decrypt(setText)
                val sql = "UPDATE $tableName SET $setTextDecrypted WHERE id = $idRecord"
                val ps = connection.prepareStatement(sql)
                ps.executeUpdate()
                ps.close()
            }

            dataDelete.forEach { action ->
                val sqlToDelete = action["sqlToDelete"] as String
                val sqlToDeleteDecrypted = Crypto.decrypt(sqlToDelete)
                val ps = connection.prepareStatement(sqlToDeleteDecrypted)
                ps.executeUpdate()
                ps.close()
            }

        } catch (e: Exception) {
            return e.message!!
        }
        return "OK"
    }

    @GetMapping("/filter")
    fun filter(
        @RequestParam(required = false) songName: String?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) album: String?,
        model: Model,
        request: HttpServletRequest
    ): String {
        val attr: MutableMap<String, String> = mutableMapOf()
        if (songName != null && songName != "") attr["song_name"] = songName
        if (author != null && author != "") attr["author"] = author
        if (text != null && text != "") attr["text"] = text
        if (album != null && album != "") attr["song_album"] = album

        val settings: List<Settings> = if ("${songName ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length < 3) emptyList() else Settings.loadListFromDb(attr, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true)

        model.addAttribute("authors", Settings.loadListAuthors(WORKING_DATABASE))
        model.addAttribute("settings", settings)

        val data: MutableMap<String, Any> = mutableMapOf()
        if (songName != null && songName != "") data["song_name"] = songName
        if (author != null && author != "") data["author"] = author
        if (text != null && text != "") data["text"] = text
        if (album != null && album != "") data["album"] = album
        doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "filter", "parameters" to data, "referer" to request.remoteHost))

        return "filter"
    }

    @GetMapping("/song")
    fun song(
        @RequestParam(required = true) id: Long,
        model: Model,
        request: HttpServletRequest
    ): String {
        val sett = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//        sett?.let {
//            if (!sett.haveVkGroupLink) {
//                val pathToPictureVkGroupLink = "/home/Karaoke/webpictures/${sett.id}.png"
//                val filePictureVkGroupLink = File(pathToPictureVkGroupLink)
//                if (!filePictureVkGroupLink.exists()) {
//                    createVKLinkPicture(sett, pathToPictureVkGroupLink)
//                }
//            }
//        }
        model.addAttribute("sett", sett)
        doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "song", "parameters" to mapOf("id" to id), "referer" to request.remoteHost))
        return "song"
    }

    @GetMapping("/statbysong")
    fun doStatBySong(
        model: Model
    ): String {
        model.addAttribute("stats", StatBySong.getStatBySong(WORKING_DATABASE))
        return "statbysong"
    }

    @GetMapping("/webevents")
    fun doWebEvents(
        model: Model
    ): String {
        model.addAttribute("webevents", StatBySong.getWebEvents(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient))
        return "webevents"
    }

    @GetMapping("/testpage/{id}")
    fun doTestPage(@PathVariable id: Long,
        model: Model
    ): String {
        val sett = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        model.addAttribute("sett", sett)
        return "testpage"
    }

//
//    @PostMapping("/storage/upload")
//    fun uploadFile(
//        @RequestParam("file") file: MultipartFile,
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName", required = false) fileName: String? = null
//    ): ResponseEntity<String> {
//        logger.info("Received upload request for bucket: $bucketName, file: ${file.originalFilename}")
//
//        if (file.isEmpty) {
//            logger.warn("Upload failed: file is empty")
//            return ResponseEntity.badRequest().body("File is empty")
//        }
//
//        val actualFileName = fileName ?: file.originalFilename ?: throw IllegalArgumentException("File name is required")
//
//        if (!isValidFileName(actualFileName)) {
//            logger.warn("Invalid file name: $actualFileName")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            logger.warn("Bucket does not exist: $bucketName")
//            return ResponseEntity.badRequest().body("Bucket does not exist: $bucketName")
//        }
//
//        val inputStream = file.inputStream
//        val size = file.size
//
//        try {
//            karaokeStorageService.uploadFile(bucketName, actualFileName, inputStream, size)
//            logger.info("File uploaded successfully: $actualFileName to bucket: $bucketName")
//            return ResponseEntity.ok("File uploaded successfully: $actualFileName")
//        } catch (e: Exception) {
//            logger.error("Upload failed for file: $actualFileName in bucket: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Upload failed: ${e.message}")
//        }
//    }
//
//    @GetMapping("/storage/url")
//    fun getFileUrl(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        val url = karaokeStorageService.getFileUrl(bucketName, fileName)
//        logger.info("URL requested for file: $fileName in bucket: $bucketName")
//        return ResponseEntity.ok(url)
//    }
//
//    @GetMapping("/storage/presigned-url")
//    fun getPresignedUrl(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        @RequestParam("expiry", required = false, defaultValue = "604800") expiry: Int,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        val url = karaokeStorageService.getPresignedUrl(bucketName, fileName, expiry)
//        logger.info("Presigned URL generated for file: $fileName in bucket: $bucketName")
//        return ResponseEntity.ok(url)
//    }
//
//    @GetMapping("/storage/download")
//    fun downloadFile(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<ByteArray> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().build()
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            val inputStream = karaokeStorageService.downloadFile(bucketName, fileName)
//            val bytes = inputStream.readAllBytes()
//
//            logger.info("File downloaded: $fileName from bucket: $bucketName")
//
//            return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
//                .body(bytes)
//        } catch (e: Exception) {
//            logger.error("Download failed for file: $fileName in bucket: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
//        }
//    }
//
//    @DeleteMapping("/storage/delete")
//    fun deleteFile(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            karaokeStorageService.deleteFile(bucketName, fileName)
//            logger.info("File deleted: $fileName from bucket: $bucketName")
//            return ResponseEntity.ok("File deleted successfully: $fileName")
//        } catch (e: Exception) {
//            logger.error("Deletion failed for file: $fileName in bucket: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Deletion failed: ${e.message}")
//        }
//    }
//
//    @GetMapping("/storage/list")
//    fun listFiles(
//        @RequestParam("bucketName") bucketName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<List<String>> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            logger.info("Bucket not found: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        logger.info("Listing files in bucket: $bucketName from IP: ${request.remoteAddr}")
//
//        val files = karaokeStorageService.listFiles(bucketName)
//        return ResponseEntity.ok(files)
//    }
//
//    @GetMapping("/storage/exists")
//    fun checkIfExists(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<Map<String, Boolean>> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().build()
//        }
//
//        val exists = karaokeStorageService.fileExists(bucketName, fileName)
//        logger.info("Check exists: file=$fileName, bucket=$bucketName, exists=$exists")
//        return ResponseEntity.ok(mapOf("exists" to exists))
//    }
//
//    @PutMapping("/storage/bucket/public")
//    fun setBucketPublic(
//        @RequestParam("bucketName") bucketName: String
//    ): ResponseEntity<String> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            karaokeStorageService.setBucketPublic(bucketName)
//            logger.info("Bucket set to public: $bucketName")
//            return ResponseEntity.ok("Bucket '$bucketName' is now public")
//        } catch (e: Exception) {
//            logger.error("Failed to set bucket as public: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Failed to set bucket as public: ${e.message}")
//        }
//    }
//
//    @PutMapping("/storage/bucket/private")
//    fun setBucketPrivate(
//        @RequestParam("bucketName") bucketName: String
//    ): ResponseEntity<String> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            karaokeStorageService.setBucketPrivate(bucketName)
//            logger.info("Bucket set to private: $bucketName")
//            return ResponseEntity.ok("Bucket '$bucketName' is now private")
//        } catch (e: Exception) {
//            logger.error("Failed to set bucket as private: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Failed to set bucket as private: ${e.message}")
//        }
//    }
//
//    @GetMapping("/storage/bucket/public-status")
//    fun isBucketPublic(
//        @RequestParam("bucketName") bucketName: String
//    ): ResponseEntity<Map<String, Boolean>> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            return ResponseEntity.notFound().build()
//        }
//
//        val isPublic = karaokeStorageService.isBucketPublic(bucketName)
//        logger.info("Bucket public status checked: $bucketName -> $isPublic")
//        return ResponseEntity.ok(mapOf("isPublic" to isPublic))
//    }
//
//    @PostMapping("/storage/fileStat")
//    @ResponseBody
//    fun getFileStat(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): StatObjectResponse? {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return null
//        }
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return null
//        }
//        return karaokeStorageService.getFileStat(bucketName, fileName)
//    }
//
//    @PostMapping("/storage/fileInfo")
//    @ResponseBody
//    fun getFileInfo(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): StorageFileInfo? {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return null
//        }
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return null
//        }
//        return karaokeStorageService.getFileInfo(bucketName, fileName)
//    }
//
//
//    @PostMapping("/storage/listInfo")
//    @ResponseBody
//    fun listFilesInfo(
//        @RequestParam("bucketName") bucketName: String,
//        request: HttpServletRequest
//    ): List<StorageFileInfo>? {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            logger.info("Bucket not found: $bucketName")
//            return null
//        }
//        return karaokeStorageService.listFilesInfo(bucketName)
//    }
}
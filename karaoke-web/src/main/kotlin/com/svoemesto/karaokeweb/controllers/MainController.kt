package com.svoemesto.karaokeweb.controllers


import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeweb.services.WEB_WORK_IN_CONTAINER
import com.svoemesto.karaokeweb.WORKING_DATABASE
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
    @Value($$"${work-in-container}") val wic: Long) {

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
        println("WEB_WORK_IN_CONTAINER = $WEB_WORK_IN_CONTAINER")
    }

    @GetMapping("/")
    fun main(
        model: Model,
        request: HttpServletRequest
    ): String {
        model.addAttribute("onBoosty", StatBySong.getCountSongsInCollection(WORKING_DATABASE))
        model.addAttribute("onAir", StatBySong.getCountSongsOnAir(WORKING_DATABASE))
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
        model.addAttribute("authors", Settings.loadListAuthors(WORKING_DATABASE))
        model.addAttribute("zakroma", Zakroma.getZakroma(author ?: "", WORKING_DATABASE))
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

        val settings: List<Settings> = if ("${songName ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length < 3) emptyList() else Settings.loadListFromDb(attr, WORKING_DATABASE)

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
        val sett = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        model.addAttribute("webevents", StatBySong.getWebEvents(WORKING_DATABASE))
        return "webevents"
    }

    @GetMapping("/testpage/{id}")
    fun doTestPage(@PathVariable id: Long,
        model: Model
    ): String {
        val sett = Settings.loadFromDbById(id, WORKING_DATABASE)
        model.addAttribute("sett", sett)
        return "testpage"
    }

}
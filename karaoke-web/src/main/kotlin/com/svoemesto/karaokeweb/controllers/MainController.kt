package com.svoemesto.karaokeweb.controllers


import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.services.WEB_WORK_IN_CONTAINER
import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
class MainController(private val webSocket: SimpMessagingTemplate, @Value("\${work-in-container}") val wic: Long) {

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
    }

    @GetMapping("/")
    fun main(
        model: Model
    ): String {
        return "main"
    }

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        model: Model
    ): String {
        model.addAttribute("authors", Settings.loadListAuthors(WORKING_DATABASE))
        model.addAttribute("zakroma", Zakroma.getZakroma(author ?: "", WORKING_DATABASE))
        return "zakroma"
    }

    @PostMapping("/updaterecord")
    @ResponseBody
    fun doUpdateRecord(
        @RequestBody(required = true) data: Map<String, Any>
    ): String {
        try {
            val tableName = data["tableName"] as String
            val idRecord = data["idRecord"] as Integer
            val setText = data["setText"] as String
            val word = data["word"] as String
            if (Crypto.decrypt(word) != Crypto.wordsToChesk) return "Не удалось расшифровать кодовое слово"
            val setTextDecrypted = Crypto.decrypt(setText)
            val sql = "UPDATE $tableName SET $setTextDecrypted WHERE id = $idRecord"
            val connection = WORKING_DATABASE.getConnection()
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate()
            ps.close()
        } catch (e: Exception) {
            return e.message!!
        }
        return "OK"
    }

    @PostMapping("/insertrecord")
    @ResponseBody
    fun doInsertRecord(
        @RequestBody(required = true) data: Map<String, Any>
    ): String {
        try {
            val sqlToInsert = data["sqlToInsert"] as String
            val word = data["word"] as String
            if (Crypto.decrypt(word) != Crypto.wordsToChesk) return "Не удалось расшифровать кодовое слово"
            val setTextDecrypted = Crypto.decrypt(sqlToInsert)
            val connection = WORKING_DATABASE.getConnection()
            val ps = connection.prepareStatement(setTextDecrypted)
            ps.executeUpdate()
            ps.close()
        } catch (e: Exception) {
            return e.message!!
        }
        return "OK"
    }

    @PostMapping("/changerecords")
    @ResponseBody
    fun doChangeRecords(
        @RequestBody(required = true) data: Map<String, Any>
    ): String {
        try {
            val word = data["word"] as String
            if (Crypto.decrypt(word) != Crypto.wordsToChesk) return "Не удалось расшифровать кодовое слово"

            val dataCreate = data["dataCreate"] as List<Map<String, Any>>
            val dataUpdate = data["dataUpdate"] as List<Map<String, Any>>
            val dataDelete = data["dataDelete"] as List<Map<String, Any>>

            val connection = WORKING_DATABASE.getConnection()

            dataCreate.forEach { action ->
                val sqlToInsert = action["sqlToInsert"] as String
                val sqlToInsertDecrypted = Crypto.decrypt(sqlToInsert)
                val ps = connection.prepareStatement(sqlToInsertDecrypted)
                ps.executeUpdate()
                ps.close()
            }

            dataUpdate.forEach { action ->
                val tableName = action["tableName"] as String
                val idRecord = action["idRecord"] as Integer
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
        @RequestParam(required = false) song_name: String?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) album: String?,
        model: Model
    ): String {
        val attr: MutableMap<String, String> = mutableMapOf()
        if (song_name != null && song_name != "") attr["song_name"] = song_name
        if (author != null && author != "") attr["author"] = author
        if (text != null && text != "") attr["text"] = text
        if (album != null && album != "") attr["song_album"] = album

        val settings: List<Settings> = if ("${song_name ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length < 3) emptyList() else Settings.loadListFromDb(attr, WORKING_DATABASE)

        model.addAttribute("settings", settings)
        return "filter"
    }

    @GetMapping("/song")
    fun song(
        @RequestParam(required = true) id: Long,
        model: Model
    ): String {
        model.addAttribute("sett", Settings.loadFromDbById(id, WORKING_DATABASE))
        return "song"
    }

}
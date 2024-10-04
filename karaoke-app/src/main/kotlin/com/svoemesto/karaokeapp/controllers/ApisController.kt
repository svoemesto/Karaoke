package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.textfiledictionary.TextFileDictionary
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.File
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*

@Controller
@RequestMapping("/apis")
class ApisController(private val sseNotificationService: SseNotificationService) {

    @GetMapping("/song/{id}/filedrums")
    fun getSongFileDrums(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.drumsNameFlac)
        println("filedrums: ${settings?.drumsNameFlac}");
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            println("filedrums notFound");
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/filebass")
    fun getSongFileBass(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.bassNameFlac)
        println("filebass: ${settings?.bassNameFlac}");
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            println("filebass notFound");
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/filevoice")
    fun getSongFileVocal(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.vocalsNameFlac)
        println("filevoice: ${settings?.vocalsNameFlac}");
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            println("filevoice notFound");
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/fileminus")
    fun getSongFileMusic(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.newNoStemNameFlac)
        println("fileminus: ${settings?.newNoStemNameFlac}");
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            println("fileminus notFound");
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/filesong")
    fun getSongFileSong(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.fileAbsolutePath)
        println("filesong: ${settings?.fileAbsolutePath}");
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            println("filesong notFound");
            return ResponseEntity.notFound().build()
        }
    }

    // Получение списка id песен, изменившихся с указанного момента
    @PostMapping("/songs/changed")
    @ResponseBody
    fun getChangedSongsIds(@RequestParam time: Long): List<Long> {
        val result: MutableList<Long> = mutableListOf()

        val connection = WORKING_DATABASE.getConnection()
        var statement: Statement? = null
        var rs: ResultSet? = null
        var sql = "select id from tbl_settings where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > $time;"
        statement = connection.createStatement()
        try {
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(rs.getLong("id"))
            }
//            if (result.isNotEmpty()) {
//                println("time = $time, ids = $result");
//            }

            return result
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    // Получение списка id процессов, изменившихся с указанного момента
    @PostMapping("/processes/changed")
    @ResponseBody
    fun getChangedProcessesIds(@RequestParam time: Long): List<Long> {
        val result: MutableList<Long> = mutableListOf()

        val connection = WORKING_DATABASE.getConnection()
        var statement: Statement? = null
        var rs: ResultSet? = null
        var sql = "select id from tbl_processes where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > $time;"
        statement = connection.createStatement()
        try {
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(rs.getLong("id"))
            }
//            if (result.isNotEmpty()) {
//                println("time = $time, ids = $result");
//            }

            return result
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    // Получение исходного текста для голоса
    @PostMapping("/song/voicesourcetext")
    @ResponseBody
    fun getSongSourceText(@RequestParam id: Long, @RequestParam voiceId: Int): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            settings.getSourceText(voiceId)
        } ?: ""
        println("voicesourcetext: ${text}");
        return text
    }

    // diffbeats + 1
    @PostMapping("/song/diffbeatsinc")
    @ResponseBody
    fun diffBeatsIncrement(@RequestParam id: Long): Long {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let {
            settings.fields[SettingField.DIFFBEATS] = (settings.diffBeats+1).toString()
            settings.saveToDb()
            settings.diffBeats
        }?: -1
    }

    // diffbeats -+ 1
    @PostMapping("/song/diffbeatsdec")
    @ResponseBody
    fun diffBeatsDecrement(@RequestParam id: Long): Long {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let {
            if (settings.diffBeats > 0) {
                settings.fields[SettingField.DIFFBEATS] = (settings.diffBeats-1).toString()
                settings.saveToDb()
            }
            settings.diffBeats
        }?: -1
    }

    // Получение sheetsageinfo
    @PostMapping("/song/sheetsageinfo")
    @ResponseBody
    fun getSheetsageinfo(@RequestParam id: Long): Map<String, Any> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfo = settings?.let {
            settings.sheetstageInfo
        } ?: emptyMap()
        println("sheetsageinfo: ${sheetsageinfo}");
        return sheetsageinfo
    }

    // Получение sheetsageinfo - tempo
    @PostMapping("/song/sheetsageinfobpm")
    @ResponseBody
    fun getSheetsageinfoBpm(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfotempo = settings?.let {
            settings.sheetstageInfo["tempo"] as String
        } ?: ""
        println("sheetsageinfobpm: ${sheetsageinfotempo}");
        return sheetsageinfotempo
    }

    // Получение sheetsageinfo - key
    @PostMapping("/song/sheetsageinfokey")
    @ResponseBody
    fun getSheetsageinfoKey(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfokey = settings?.let {
            settings.sheetstageInfo["key"] as String
        } ?: ""
        println("sheetsageinfokey: ${sheetsageinfokey}");
        return sheetsageinfokey
    }

    // Получение sheetsageinfo - chords
    @PostMapping("/song/sheetsageinfochords")
    @ResponseBody
    fun getSheetsageinfoChords(@RequestParam id: Long): List<String> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfochords = settings?.let {
            settings.sheetstageInfo["chords"] as List<String>
        } ?: emptyList()
        println("sheetsageinfochords: ${sheetsageinfochords}");
        return sheetsageinfochords
    }

    // Получение sheetsageinfo - beattimes
    @PostMapping("/song/sheetsageinfobeattimes")
    @ResponseBody
    fun getSheetsageinfoBeattimes(@RequestParam id: Long): List<Double> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfobeattimes = settings?.let {
            settings.sheetstageInfo["beattimes"] as List<Double>
        } ?: emptyList()
        println("sheetsageinfobeattimes: ${sheetsageinfobeattimes}");
        return sheetsageinfobeattimes
    }


    // Получение слогов для голоса
    @PostMapping("/song/voicesourcesyllables")
    @ResponseBody
    fun getSongSourceSyllables(@RequestParam id: Long, @RequestParam voiceId: Int): List<String> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val syllables = settings?.let {
            settings.getSourceSyllables(voiceId)
        } ?: emptyList()
        println("voicesourcesyllables: ${syllables}");
        return syllables
    }

    // Получение маркеров для голоса
    @PostMapping("/song/voicesourcemarkers")
    @ResponseBody
    fun getSongSourceMarkers(@RequestParam id: Long, @RequestParam voiceId: Int): List<SourceMarker> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val markers = settings?.let {
            settings.getSourceMarkers(voiceId)
        } ?: emptyList()
        println("voicesourcemarkers: ${markers}");
        return markers
    }

    // Получение форматированного текста
    @PostMapping("/song/textformatted")
    @ResponseBody
    fun getSongTextFormatted(@RequestParam id: Long): String {
        println("getSongTextFormatted called")
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            settings.getTextFormatted()
        } ?: ""
        return text
    }

    // Получение текста заголовка для boosty
    @PostMapping("/song/textboostyhead")
    @ResponseBody
    fun getSongTextBoostyHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getTextBoostyHead()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для boosty
    @PostMapping("/song/textboostybody")
    @ResponseBody
    fun getSongTextBoostyBody(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getTextBoostyBody()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для boostyFiles
    @PostMapping("/song/textboostyfileshead")
    @ResponseBody
    fun getSongTextBoostyFilesHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getTextBoostyFilesHead()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела VkGroup
    @PostMapping("/song/textvkbody")
    @ResponseBody
    fun getSongTextVkBody(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getVKGroupDescription()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Dzen Karaoke
    @PostMapping("/song/textyoutubekaraokeheader")
    @ResponseBody
    fun getSongTextYoutubeKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Dzen Karaoke
    @PostMapping("/song/textyoutubekaraokewoheader")
    @ResponseBody
    fun getSongTextYoutubeKaraokeWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionWOHeaderWithTimecodes(5000)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Dzen Lyrics
    @PostMapping("/song/textyoutubelyricsheader")
    @ResponseBody
    fun getSongTextYoutubeLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Dzen Lyrics
    @PostMapping("/song/textyoutubelyricswoheader")
    @ResponseBody
    fun getSongTextYoutubeLyricsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionWOHeaderWithTimecodes(5000)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Dzen Chords
    @PostMapping("/song/textyoutubechordsheader")
    @ResponseBody
    fun getSongTextYoutubeChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Dzen Lyrics
    @PostMapping("/song/textyoutubechordswoheader")
    @ResponseBody
    fun getSongTextYoutubeChordsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionWOHeaderWithTimecodes(5000)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Platforma Karaoke
    @PostMapping("/song/textplkaraokeheader")
    @ResponseBody
    fun getSongTextPlKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Platforma Karaoke
    @PostMapping("/song/textplkaraokewoheader")
    @ResponseBody
    fun getSongTextPlKaraokeWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionWOHeaderWithTimecodes(5000, 100)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Platforma Lyrics
    @PostMapping("/song/textpllyricsheader")
    @ResponseBody
    fun getSongTextPlLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Platforma Lyrics
    @PostMapping("/song/textpllyricswoheader")
    @ResponseBody
    fun getSongTextPlLyricsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionWOHeaderWithTimecodes(5000, 100)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Platforma Chords
    @PostMapping("/song/textplchordsheader")
    @ResponseBody
    fun getSongTextPlChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Platforma Chords
    @PostMapping("/song/textplchordswoheader")
    @ResponseBody
    fun getSongTextPlChordsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionWOHeaderWithTimecodes(5000, 100)
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }


    // Получение текста заголовка для Vk Karaoke
    @PostMapping("/song/textvkkaraokeheader")
    @ResponseBody
    fun getSongTextVkKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Vk Karaoke
    @PostMapping("/song/textvkkaraoke")
    @ResponseBody
    fun getSongTextVkKaraoke(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Vk Lyrics
    @PostMapping("/song/textvklyricsheader")
    @ResponseBody
    fun getSongTextVkLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Vk Lyrics
    @PostMapping("/song/textvklyrics")
    @ResponseBody
    fun getSongTextVkLyrics(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Vk Chords
    @PostMapping("/song/textvkchordsheader")
    @ResponseBody
    fun getSongTextVkChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста тела для Vk Chords
    @PostMapping("/song/textvkchords")
    @ResponseBody
    fun getSongTextVkChords(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }


    // Получение текста заголовка для Telegram Karaoke
    @PostMapping("/song/texttelegramkaraokeheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Telegram Lyrics
    @PostMapping("/song/texttelegramlyricsheader")
    @ResponseBody
    fun getSongTextTelegramLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение текста заголовка для Telegram Chords
    @PostMapping("/song/texttelegramchordsheader")
    @ResponseBody
    fun getSongTextTelegramChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        println("id = ${id}, text = ${text}")
        return text
    }

    // Получение списка авторов
    @PostMapping("/songs/authors")
    @ResponseBody
    fun authors(): Map<String, Any> {
        return mapOf(
            "authors" to Settings.loadListAuthors(WORKING_DATABASE)
        )
    }

    // Получение списка словарей
    @PostMapping("/songs/dicts")
    @ResponseBody
    fun dicts(): Map<String, Any> {
        return mapOf(
            "dicts" to TEXT_FILE_DICTS.keys.toMutableList().sorted().toList()
        )
    }

    // Получение списка статусов процессов
    @PostMapping("/processes/statuses")
    @ResponseBody
    fun processesStatuses(): Map<String, Any> {
        return mapOf(
            "statuses" to KaraokeProcessStatuses.values()
        )
    }

    // Получение списка типов процессов
    @PostMapping("/processes/types")
    @ResponseBody
    fun processesTypes(): Map<String, Any> {
        return mapOf(
            "authors" to KaraokeProcessStatuses.values()
        )
    }

    // Получение списка песен по списку id
    @PostMapping("/songs/ids")
    @ResponseBody
    fun apisSongsByIds(@RequestParam ids: List<Long>): List<SettingsDTO> {
        return Settings.loadListFromDb(mapOf(Pair("ids", ids.joinToString(","))), WORKING_DATABASE).map { it.toDTO() }
    }

    // Получение списка процессов по списку id
    @PostMapping("/processes/ids")
    @ResponseBody
    fun apisProcessesByIds(@RequestParam ids: List<Long>): List<ProcessesDTO> {
        return KaraokeProcess.loadList(mapOf(Pair("ids", ids.joinToString(","))), WORKING_DATABASE).map { it.toDTO() }
    }

    // список publications
    @PostMapping("/publications")
    @ResponseBody
    fun publications(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["publish_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getPublicationList(args, WORKING_DATABASE).map { it.toDTO() }
        )
    }

    // список unpublications
    @PostMapping("/unpublications")
    @ResponseBody
    fun unpublications(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getUnPublicationList(WORKING_DATABASE).map { it.map { it.toDTO() } }
        )
    }

    // список publications
    @PostMapping("/publications2")
    @ResponseBody
    fun publications2(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["publish_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to CrossSettings.publications(Publication.getSettingsListForPublications(args, WORKING_DATABASE))
        )
    }

    // список unpublications
    @PostMapping("/unpublications2")
    @ResponseBody
    fun unpublications2(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to CrossSettings.unpublications(Publication.getSettingsListForUnpublications(WORKING_DATABASE))
        )
    }

    @PostMapping("/publicationsdigest")
    @ResponseBody
    fun publicationsDigest(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["filter_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }
        val listOfSettings = Publication.getSettingsListForPublications(args, WORKING_DATABASE)
        val publications = if (filterCond == "unpublish") {
            CrossSettings.unpublications(listOfSettings)
        } else {
            CrossSettings.publications(listOfSettings)
        }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publicationsDigest" to publications
        )
    }

    @PostMapping("/processesdigests")
    @ResponseBody
    fun apisProcessesDigest(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_name: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) filter_order: String?,
        @RequestParam(required = false) filter_priority: String?,
        @RequestParam(required = false) filter_description: String?,
        @RequestParam(required = false) filter_settings_id: String?,
        @RequestParam(required = false) filter_type: String?,
        @RequestParam(required = false) filter_limit: String?,
        @RequestParam(required = false) filter_notail: String?

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_name?.let { if (filter_name != "") args["process_name"] = filter_name }
        filter_status?.let { if (filter_status != "") args["process_status"] = filter_status }
        filter_order?.let { if (filter_order != "") args["process_order"] = filter_order }
        filter_priority?.let { if (filter_priority != "") args["process_priority"] = filter_priority }
        filter_description?.let { if (filter_description != "") args["process_description"] = filter_description }
        filter_settings_id?.let { if (filter_settings_id != "") args["settings_id"] = filter_settings_id }
        filter_type?.let { if (filter_type != "") args["process_type"] = filter_type }
        filter_limit?.let { if (filter_limit != "") args["filter_limit"] = filter_limit }
        filter_notail?.let { if (filter_notail != "") args["filter_notail"] = filter_notail }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "processesDigests" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() },
            "statuses" to KaraokeProcessStatuses.values(),
            "types" to KaraokeProcessTypes.values()
        )
    }

    // Получение списка песен
    @PostMapping("/songsdigests")
    @ResponseBody
    fun apisSongsDigests(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_songName: String?,
        @RequestParam(required = false) filter_author: String?,
        @RequestParam(required = false) filter_year: String?,
        @RequestParam(required = false) filter_album: String?,
        @RequestParam(required = false) filter_track: String?,
        @RequestParam(required = false) filter_tags: String?,
        @RequestParam(required = false) filter_date: String?,
        @RequestParam(required = false) filter_time: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) flag_boosty: String?,
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_youtube_lyrics: String?,
        @RequestParam(required = false) flag_youtube_karaoke: String?,
        @RequestParam(required = false) flag_youtube_chords: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        @RequestParam(required = false) flag_pl_lyrics: String?,
        @RequestParam(required = false) flag_pl_karaoke: String?,
        @RequestParam(required = false) filter_result_version: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_songName?.let { if (filter_songName != "") args["song_name"] = filter_songName }
        filter_author?.let { if (filter_author != "") args["song_author"] = filter_author }
        filter_album?.let { if (filter_album != "") args["song_album"] = filter_album }
        filter_date?.let { if (filter_date != "") args["publish_date"] = filter_date }
        filter_time?.let { if (filter_time != "") args["publish_time"] = filter_time }
        filter_year?.let { if (filter_year != "") args["song_year"] = filter_year }
        filter_track?.let { if (filter_track != "") args["song_track"] = filter_track }
        filter_tags?.let { if (filter_tags != "") args["tags"] = filter_tags }
        filter_status?.let { if (filter_status != "") args["id_status"] = filter_status }
        flag_boosty?.let { if (flag_boosty != "") args["flag_boosty"] = flag_boosty }
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_youtube_lyrics?.let { if (flag_youtube_lyrics != "") args["flag_youtube_lyrics"] = flag_youtube_lyrics }
        flag_youtube_karaoke?.let { if (flag_youtube_karaoke != "") args["flag_youtube_karaoke"] = flag_youtube_karaoke }
        flag_youtube_chords?.let { if (flag_youtube_chords != "") args["flag_youtube_chords"] = flag_youtube_chords }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        flag_pl_lyrics?.let { if (flag_pl_lyrics != "") args["flag_pl_lyrics"] = flag_pl_lyrics }
        flag_pl_karaoke?.let { if (flag_pl_karaoke != "") args["flag_pl_karaoke"] = flag_pl_karaoke }
        filter_result_version?.let { if (filter_result_version != "") args["filter_result_version"] = filter_result_version }

        println("args: $args")

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "songsDigests" to Settings.loadListFromDb(args, WORKING_DATABASE).map { it.toDTO().toDtoDigest() },
            "authors" to Settings.loadListAuthors(WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE)
        )
    }

    // Получение списка песен
    @PostMapping("/songs")
    @ResponseBody
    fun apisSongs(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_songName: String?,
        @RequestParam(required = false) filter_author: String?,
        @RequestParam(required = false) filter_year: String?,
        @RequestParam(required = false) filter_album: String?,
        @RequestParam(required = false) filter_track: String?,
        @RequestParam(required = false) filter_tags: String?,
        @RequestParam(required = false) filter_date: String?,
        @RequestParam(required = false) filter_time: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) flag_boosty: String?,
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_youtube_lyrics: String?,
        @RequestParam(required = false) flag_youtube_karaoke: String?,
        @RequestParam(required = false) flag_youtube_chords: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        @RequestParam(required = false) flag_pl_lyrics: String?,
        @RequestParam(required = false) flag_pl_karaoke: String?,
        @RequestParam(required = false) filter_result_version: String?,
        @RequestParam(required = false) pageSize: Int = 30
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_songName?.let { if (filter_songName != "") args["song_name"] = filter_songName }
        filter_author?.let { if (filter_author != "") args["song_author"] = filter_author }
        filter_album?.let { if (filter_album != "") args["song_album"] = filter_album }
        filter_date?.let { if (filter_date != "") args["publish_date"] = filter_date }
        filter_time?.let { if (filter_time != "") args["publish_time"] = filter_time }
        filter_year?.let { if (filter_year != "") args["song_year"] = filter_year }
        filter_track?.let { if (filter_track != "") args["song_track"] = filter_track }
        filter_tags?.let { if (filter_tags != "") args["tags"] = filter_tags }
        filter_status?.let { if (filter_status != "") args["id_status"] = filter_status }
        flag_boosty?.let { if (flag_boosty != "") args["flag_boosty"] = flag_boosty }
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_youtube_lyrics?.let { if (flag_youtube_lyrics != "") args["flag_youtube_lyrics"] = flag_youtube_lyrics }
        flag_youtube_karaoke?.let { if (flag_youtube_karaoke != "") args["flag_youtube_karaoke"] = flag_youtube_karaoke }
        flag_youtube_chords?.let { if (flag_youtube_chords != "") args["flag_youtube_chords"] = flag_youtube_chords }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        flag_pl_lyrics?.let { if (flag_pl_lyrics != "") args["flag_pl_lyrics"] = flag_pl_lyrics }
        flag_pl_karaoke?.let { if (flag_pl_karaoke != "") args["flag_pl_karaoke"] = flag_pl_karaoke }
        filter_result_version?.let { if (filter_result_version != "") args["filter_result_version"] = filter_result_version }

        println("args: $args")

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to Settings.loadListFromDb(args, WORKING_DATABASE).map { it.toDTO() }.chunked(pageSize),
            "authors" to Settings.loadListAuthors(WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE)
        )
    }

    // Получение списка процессов
    @PostMapping("/processes")
    @ResponseBody
    fun apisProcesses(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_name: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) filter_order: String?,
        @RequestParam(required = false) filter_priority: String?,
        @RequestParam(required = false) filter_description: String?,
        @RequestParam(required = false) filter_settings_id: String?,
        @RequestParam(required = false) filter_type: String?,
        @RequestParam(required = false) filter_limit: String?,
        @RequestParam(required = false) pageSize: Int = 30

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_name?.let { if (filter_name != "") args["process_name"] = filter_name }
        filter_status?.let { if (filter_status != "") args["process_status"] = filter_status }
        filter_order?.let { if (filter_order != "") args["process_order"] = filter_order }
        filter_priority?.let { if (filter_priority != "") args["process_priority"] = filter_priority }
        filter_description?.let { if (filter_description != "") args["process_description"] = filter_description }
        filter_settings_id?.let { if (filter_settings_id != "") args["settings_id"] = filter_settings_id }
        filter_type?.let { if (filter_type != "") args["process_type"] = filter_type }
        filter_limit?.let { if (filter_limit != "") args["filter_limit"] = filter_limit }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() }.chunked(pageSize),
            "statuses" to KaraokeProcessStatuses.values(),
            "types" to KaraokeProcessTypes.values()
        )
    }

    // Видеопроигрыватель: Lyrics
    @PostMapping("/song/playlyrics")
    @ResponseBody
    fun doPlayLyrics(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            println("doPlayLyrics")
            settings.playLyrics()
        }
        return true
    }

    // Видеопроигрыватель: Karaoke
    @PostMapping("/song/playkaraoke")
    @ResponseBody
    fun doPlayKaraoke(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            println("doPlayKaraoke")
            settings.playKaraoke()
        }
        return true
    }


    // Видеопроигрыватель: Chords
    @PostMapping("/song/playchords")
    @ResponseBody
    fun doPlayChords(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            println("doPlayChords")
            settings.playChords()
        }
        return true
    }

    // Получение песени
    @PostMapping("/song")
    @ResponseBody
    fun apisSong(@RequestParam id: String): Any? {
        return Settings.loadFromDbById(id.toLong(), WORKING_DATABASE)?.toDTO()
    }

    // Обновление песни
    @PostMapping("/song/update")
    @ResponseBody
    fun songs2Update(
        @RequestParam(required = false) id: String,
        @RequestParam(required = false) rootFolder: String?,
        @RequestParam(required = false) fileName: String?,
        @RequestParam(required = false) idStatus: String?,
        @RequestParam(required = false) songName: String?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) album: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) time: String?,
        @RequestParam(required = false) year: String?,
        @RequestParam(required = false) track: String?,
        @RequestParam(required = false) key: String?,
        @RequestParam(required = false) bpm: String?,
        @RequestParam(required = false) ms: String?,
        @RequestParam(required = false) tags: String?,
        @RequestParam(required = false) idBoosty: String?,
        @RequestParam(required = false) idBoostyFiles: String?,
        @RequestParam(required = false) idVk: String?,
        @RequestParam(required = false) idYoutubeLyrics: String?,
        @RequestParam(required = false) idYoutubeKaraoke: String?,
        @RequestParam(required = false) idYoutubeChords: String?,
        @RequestParam(required = false) idVkLyrics: String?,
        @RequestParam(required = false) idVkKaraoke: String?,
        @RequestParam(required = false) idVkChords: String?,
        @RequestParam(required = false) idTelegramLyrics: String?,
        @RequestParam(required = false) idTelegramKaraoke: String?,
        @RequestParam(required = false) idTelegramChords: String?,
        @RequestParam(required = false) idPlLyrics: String?,
        @RequestParam(required = false) idPlKaraoke: String?,
        @RequestParam(required = false) idPlChords: String?,
        @RequestParam(required = false) resultVersion: String?,
        @RequestParam(required = false) diffBeats: String?
    ): Boolean {
        val settingsId: Long = id.toLong()
        val settings = Settings.loadFromDbById(settingsId, WORKING_DATABASE)
        settings?.let { sett ->
            fileName?.let { sett.fileName = it }
            rootFolder?.let { sett.rootFolder = it }
            tags?.let { sett.tags = it }
            id.let { sett.fields[SettingField.ID] = it }
            songName?.let { sett.fields[SettingField.NAME] = it }
            author?.let { sett.fields[SettingField.AUTHOR] = it }
            year?.let { sett.fields[SettingField.YEAR] = it }
            album?.let { sett.fields[SettingField.ALBUM] = it }
            track?.let { sett.fields[SettingField.TRACK] = it }
            date?.let { sett.fields[SettingField.DATE] = it }
            time?.let { sett.fields[SettingField.TIME] = it }
            key?.let { sett.fields[SettingField.KEY] = it }
            bpm?.let { sett.fields[SettingField.BPM] = it }
            ms?.let { sett.fields[SettingField.MS] = it }
            idBoosty?.let { sett.fields[SettingField.ID_BOOSTY] = it }
            idBoostyFiles?.let { sett.fields[SettingField.ID_BOOSTY_FILES] = it }
            idVk?.let { sett.fields[SettingField.ID_VK] = it }
            idYoutubeLyrics?.let { sett.fields[SettingField.ID_YOUTUBE_LYRICS] = it }
            idYoutubeKaraoke?.let { sett.fields[SettingField.ID_YOUTUBE_KARAOKE] = it }
            idYoutubeChords?.let { sett.fields[SettingField.ID_YOUTUBE_CHORDS] = it }
            idVkLyrics?.let { sett.fields[SettingField.ID_VK_LYRICS] = it }
            idVkKaraoke?.let { sett.fields[SettingField.ID_VK_KARAOKE] = it }
            idVkChords?.let { sett.fields[SettingField.ID_VK_CHORDS] = it }
            idTelegramLyrics?.let { sett.fields[SettingField.ID_TELEGRAM_LYRICS] = it }
            idTelegramKaraoke?.let { sett.fields[SettingField.ID_TELEGRAM_KARAOKE] = it }
            idTelegramChords?.let { sett.fields[SettingField.ID_TELEGRAM_CHORDS] = it }
            idPlLyrics?.let { sett.fields[SettingField.ID_PL_LYRICS] = it }
            idPlKaraoke?.let { sett.fields[SettingField.ID_PL_KARAOKE] = it }
            idPlChords?.let { sett.fields[SettingField.ID_PL_CHORDS] = it }
            resultVersion?.let { sett.fields[SettingField.RESULT_VERSION] = it }
            diffBeats?.let { sett.fields[SettingField.DIFFBEATS] = it }
            idStatus?.let { sett.fields[SettingField.ID_STATUS] =  it }
            sett.saveToDb()
            sett.saveToFile()
        }

        return true
    }

    // Получение процесса
    @PostMapping("/process")
    @ResponseBody
    fun apisProcess(@RequestParam id: String): Any? {
        return KaraokeProcess.load(id.toLong(), WORKING_DATABASE)?.toDTO()
    }

    // Обновление процесса
    @PostMapping("/process/update")
    @ResponseBody
    fun processes2Update(
        @RequestParam(required = false) id: Int,
        @RequestParam(required = false) name: String,
        @RequestParam(required = false) status: String,
        @RequestParam(required = false) order: Int,
        @RequestParam(required = false) priority: Int,
        @RequestParam(required = false) command: String,
        @RequestParam(required = false) description: String,
        @RequestParam(required = false) type: String

    ): Boolean {
        val processId: Long = id.toLong()
        val processes = KaraokeProcess.load(processId, WORKING_DATABASE)
        processes?.let { process ->
            name.let { process.name = it }
            status.let { process.status = it }
            order.let { process.order = it }
            priority.let { process.priority = it }
            command.let { process.command = it }
            description.let { process.description = it }
            type.let { process.type = it }
            process.save()
        }

        return true
    }

    // Получение данных для редактирования сабов
    @PostMapping("/song/voices")
    @ResponseBody
    fun getSongVoices(@RequestParam id: Long): Map<String, Any> {
        println("getSongVoices for song id = ${id}")
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)

        settings?.let {
            val result: MutableList<Map<String, Any>> = mutableListOf()
            for (voice in 0 until settings.countVoices) {
                result.add(mapOf(
                    "text" to settings.getSourceText(voice),
                    "markers" to settings.getSourceMarkers(voice),
                    "syllables" to settings.getSourceSyllables(voice),
                    "voice" to voice
                ))
            }
            return mapOf("voices" to result)
        }

        return emptyMap()
    }

    // Получаем картинку в BASE64 для альбома
    @PostMapping("/song/picturealbum")
    @ResponseBody
    fun getPictureAlbum(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            return "data:image/gif;base64,${it.pictureAlbum?.full}" ?: ""
        }
        return ""
    }

    // Получаем картинку в BASE64 для автора
    @PostMapping("/song/pictureauthor")
    @ResponseBody
    fun getPictureAuthor(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            return "data:image/gif;base64,${it.pictureAuthor?.full}" ?: ""
        }
        return ""
    }

    // Получаем дату начала для публикаций
    @PostMapping("/publications/date")
    @ResponseBody
    fun getPublicationsDateFrom(@RequestParam param: String): String {

        val currentCalendar = Calendar.getInstance()
        val currentDateTime = currentCalendar.time

        val formatter = SimpleDateFormat("dd/MM/yyyy")
        val currentDate = formatter.parse(formatter.format(currentDateTime))

        val settings = Settings.loadListFromDb(emptyMap(), WORKING_DATABASE)
        val sett = when (param) {
            "STATE_ALL_DONE" -> settings.firstOrNull { it.state == SettingState.ALL_DONE } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_OVERDUE" -> settings.firstOrNull { it.state == SettingState.OVERDUE } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_TODAY" -> settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_ALL_UPLOADED" -> settings.firstOrNull { it.state == SettingState.ALL_UPLOADED } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_TG" -> settings.firstOrNull { it.state == SettingState.WO_TG } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_VK" -> settings.firstOrNull { it.state == SettingState.WO_VK } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_DZEN" -> settings.firstOrNull { it.state == SettingState.WO_DZEN } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_VKG" -> settings.firstOrNull { it.state == SettingState.WO_VKG } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_0" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 0L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_1" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 1L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_2" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 2L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_3" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 3L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_4" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 4L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_6" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 6L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            else -> null
        } ?: return ""
        return sett.date
    }

    // Сохраняем маркеры для войса
    @PostMapping("/song/savesourcemarkers")
    @ResponseBody
    fun saveSourceMarkers(
        @RequestParam id: Long,
        @RequestParam voice: Int,
        @RequestParam(required = false) sourceMarkers: String = ""): Boolean {
        var result = false
        if (sourceMarkers.trim() != "") {
            val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
            result = settings?.let {
                settings.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
                val strText = settings.convertMarkersToSrt(voice)
                File("${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt").writeText(strText)
                true
            } ?: false
        }
        return result
    }

    // Сохраняем исходный текст для войса
    @PostMapping("/song/savesourcetext")
    @ResponseBody
    fun saveSourceText(
        @RequestParam id: Long,
        @RequestParam voice: Int,
        @RequestParam(required = false) sourceText: String = ""): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let {
            settings.setSourceText(voice, sourceText)
            settings.updateMarkersFromSourceText(voice)
            true
        } ?: false
    }

    // Сохраняем исходный текст и маркеры для войса
    @PostMapping("/song/savesourcetextmarkers")
    @ResponseBody
    fun saveSourceTextAndMarkers(
        @RequestParam id: Long,
        @RequestParam voice: Int,
        @RequestParam sourceText: String,
        @RequestParam sourceMarkers: String
        ): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let {
            val markers = try {
                Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers)
            } catch (e: Exception) {
                println("Ошибка при парсинге маркеров.")
                emptyList()
            }
            settings.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
            val strText = settings.convertMarkersToSrt(voice)
            try {
                File("${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt").writeText(strText)
            } catch (e: Exception) {
                println("Ошибка при создании файла субтитров.")
            }
            settings.setSourceText(voice, sourceText)
            true
        } ?: false
    }

    // Создаём караоке
    @PostMapping("/song/createkaraoke")
    @ResponseBody
    fun getSongCreateKaraoke(@RequestParam id: Long,
                             @RequestParam(required = false) priorLyrics: String = "0",
                             @RequestParam(required = false) priorKaraoke: String = "1",
                             @RequestParam(required = false) priorChords: String = "",
    ): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)


        var type = "danger"
        val head = "Создание караоке"
        var body = "Что-то пошло не так"
        var result = false
        settings?.let {
            val createLyrics = priorLyrics != ""
            val createKaraoke = priorKaraoke != ""
            val createChords = priorChords != ""
            settings.createKaraoke(createLyrics = createLyrics, createKaraoke = createKaraoke, createChords = createChords)
            if (createLyrics) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, priorLyrics.toInt())
            if (createKaraoke) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, priorKaraoke.toInt())
            if (createChords) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, priorChords.toInt())
            type = "info"
            body = "Создание караоке для песни «${it.songName}» прошло успешно."
            result = true
        }
        SNS.send(SseNotification.message(Message(type = type, head = head, body = body)))
        return result
    }

    // Создаём караоке для всех
    @PostMapping("/songs/createkaraokeall")
    @ResponseBody
    fun getSongsCreateKaraokeAll(@RequestParam songsIds: String,
                                 @RequestParam(required = false) priorLyrics: String = "10",
                                 @RequestParam(required = false) priorKaraoke: String = "10",
                                 @RequestParam(required = false) priorChords: String = ""
    ) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    val createLyrics = priorLyrics != ""
                    val createKaraoke = priorKaraoke != ""
                    val createChords = priorChords != ""
                    settings.createKaraoke(createLyrics = createLyrics, createKaraoke = createKaraoke, createChords = createChords)
                    if (createLyrics) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, priorLyrics.toInt())
                    if (createKaraoke) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, priorKaraoke.toInt())
                    if (createChords) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, priorChords.toInt())
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание караоке для всех",
                body = "Создание караоке для всех прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание караоке для всех",
                body = "Что-то пошло не так"
            )))
        }
    }

    // DEMUCS2 для песни
    @PostMapping("/song/demucs2")
    @ResponseBody
    fun doProcessDemucs2(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, prior)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS2",
                body = "Создание DEMUCS2 прошло успешно"
            )))
            return
        }
        SNS.send(SseNotification.message(Message(
            type = "warning",
            head = "Создание DEMUCS2",
            body = "Что-то пошло не так"
        )))
    }

    // DEMUCS2 для всех
    @PostMapping("/songs/createdemucs2all")
    @ResponseBody
    fun getSongsCreateDemucs2All(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS2",
                body = "Создание DEMUCS2 прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание DEMUCS2",
                body = "Что-то пошло не так"
            )))
        }
    }

    // DEMUCS5 для песни
    @PostMapping("/song/demucs5")
    @ResponseBody
    fun doProcessDemucs5(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, prior)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS5",
                body = "Создание DEMUCS5 прошло успешно"
            )))
            return
        }
        SNS.send(SseNotification.message(Message(
            type = "warning",
            head = "Создание DEMUCS5",
            body = "Что-то пошло не так"
        )))
    }

    // DEMUCS5 для всех
    @PostMapping("/songs/createdemucs5all")
    @ResponseBody
    fun getSongsCreateDemucs5All(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS5",
                body = "Создание DEMUCS5 прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание DEMUCS5",
                body = "Что-то пошло не так"
            )))
        }
    }

    // SHEETSAGE для песни
    @PostMapping("/song/sheetsage")
    @ResponseBody
    fun doProcessSheetsage(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE, true, prior)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание SHEETSAGE",
                body = "Создание SHEETSAGE прошло успешно"
            )))
            return
        }
        SNS.send(SseNotification.message(Message(
            type = "warning",
            head = "Создание SHEETSAGE",
            body = "Что-то пошло не так"
        )))
    }

    // SHEETSAGE для всех
    @PostMapping("/songs/sheetsageall")
    @ResponseBody
    fun getSongsCreateSheetsageAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE, true, prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание SHEETSAGE",
                body = "Создание SHEETSAGE прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание SHEETSAGE",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Удаляем песню
    @PostMapping("/song/delete")
    @ResponseBody
    fun doDeleteSong(@RequestParam id: Long) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            it.deleteFromDb()
        }
    }

    // Создаём MP3 KARAOKE для песни
    @PostMapping("/song/mp3karaoke")
    @ResponseBody
    fun doMP3Karaoke(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.doMP3Karaoke(prior)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание MP3 KARAOKE",
            body = "Создание MP3 KARAOKE прошло успешно"
        )))
    }

    // Создаём MP3 KARAOKE для всех
    @PostMapping("/songs/createmp3karaokeall")
    @ResponseBody
    fun getSongsCreateMP3KaraokeAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doMP3Karaoke(prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание MP3 KARAOKE",
                body = "Создание MP3 KARAOKE прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание MP3 KARAOKE",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Создаём MP3 LYRICS для песни
    @PostMapping("/song/mp3lyrics")
    @ResponseBody
    fun doMP3Lyrics(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.doMP3Lyrics(prior)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание MP3 LYRICS",
            body = "Создание MP3 LYRICS прошло успешно"
        )))
    }

    // Создаём MP3 LYRICS для всех
    @PostMapping("/songs/createmp3lyricsall")
    @ResponseBody
    fun getSongsCreateMP3LyricsAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doMP3Lyrics(prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание MP3 LYRICS",
                body = "Создание MP3 LYRICS прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание MP3 LYRICS",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Создаём SYMLINKs для песни
    @PostMapping("/song/symlink")
    @ResponseBody
    fun doSymlink(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.doSymlink(prior)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание SYMLINK",
            body = "Создание SYMLINK прошло успешно"
        )))
    }

    // Создаём SYMLINKs для всех
    @PostMapping("/songs/createsymlinksall")
    @ResponseBody
    fun getSongsCreateSymlinksAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doSymlink(prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание SYMLINKs",
                body = "Создание SYMLINKs прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание SYMLINKs",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Ищем и возвращаем текст
    @PostMapping("/song/searchsongtext")
    @ResponseBody
    fun getSearchSongText(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return  settings?.let {
            searchSongText(settings)
        } ?: ""
    }

    // Ищем тексты для всех
    @PostMapping("/songs/searchsongtextall")
    @ResponseBody
    fun getSearchSongTextAll(@RequestParam songsIds: String): Boolean {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    if (settings.sourceText.isBlank()) {
                        val text = searchSongText(settings)

                        Thread.sleep(2000)

                        if (text.isNotBlank()) {
                            settings.sourceText = text
                            settings.fields[SettingField.ID_STATUS] = "1"
                            settings.saveToDb()
                        }
                    }
                }
                result = true
            }
        }
        return result
    }

    @PostMapping("/song/setpublishdatetimetoauthor")
    @ResponseBody
    fun doSetPublishDateTimeToAuthor(@RequestParam id: Long) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            Settings.setPublishDateTimeToAuthor(settings)
        }
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Даты публикации",
            body = "Изменение дат публикаций для автора прошло успешно"
        )))
    }

    // Заменяем символы в тексте
    @PostMapping("/replacesymbolsinsong")
    @ResponseBody
    fun getReplaceSymbolsInSong(@RequestParam(required = true) txt: String): String {
        return replaceSymbolsInSong(txt)
    }

    // Действия со словарями
    @PostMapping("/utils/tfd")
    @ResponseBody
    fun doTextFileDictionary(
        @RequestParam(required = true) dictName: String,
        @RequestParam(required = true) dictValue: String,
        @RequestParam(required = true) dictAction: String
    ) {
        TextFileDictionary.doAction(dictName, dictAction, listOf(dictValue))
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Действия со словарями",
            body = "Действие «$dictAction», словарь «$dictName», слово «$dictValue» прошло успешно"
        )))
    }

    // Обновляем RemoteDatabase
    @PostMapping("/utils/updateremotedatabasefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteDatabaseFromLocalDatabase(
        @RequestParam(required = true) updateSettings: Boolean = true,
        @RequestParam(required = true) updatePictures: Boolean = true
    ): List<Int> {
        val (countCreate, countUpdate, countDelete) = updateRemoteDatabaseFromLocalDatabase(updateSettings,updatePictures)
        SNS.send(SseNotification.message(
            Message(
            type = "info",
            head = "Обновление БД",
            body = "Создано записей: $countCreate, обновлено записей: $countUpdate, удалено записей: $countDelete"
        )
        ))
        return listOf(countCreate, countUpdate, countDelete)
    }

    // Обновляем LocalDatabase
    @PostMapping("/utils/updatelocaldatabasefromremotedatabase")
    @ResponseBody
    fun doUpdateLocalDatabaseFromRemoteDatabase(
        @RequestParam(required = true) updateSettings: Boolean = true,
        @RequestParam(required = true) updatePictures: Boolean = true
    ): List<Int> {
        val (countCreate, countUpdate, countDelete) = updateLocalDatabaseFromRemoteDatabase(updateSettings,updatePictures)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление БД",
            body = "Создано записей: $countCreate, обновлено записей: $countUpdate, удалено записей: $countDelete"
        )))
        return listOf(countCreate, countUpdate, countDelete)
    }

    // Добавление файлов из папки
    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String) {
        val result =  Settings.createFromPath(folder, WORKING_DATABASE).size
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Добавление файлов из папки",
            body = "Добавлено файлов из папки «$folder»: $result"
        )))
    }

    // Создание картинок Dzen для папки
    @PostMapping("/utils/createdzenpicturesforfolder")
    @ResponseBody
    fun doCreateDzenPicturesForFolder(
        @RequestParam(required = true) folder: String) {
        createDzenPicture(folder)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание картинок Dzen для папки",
            body = "Создание картинок Dzen для папки «$folder» прошло успешно"
        )))
    }

    @PostMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(@RequestParam(required = false) songsIds: String = "",
                       @RequestParam(required = false) priorLyrics: Int = 10,
                       @RequestParam(required = false) priorKaraoke: Int = 10): Any {
        val settingsList = if (songsIds == "") {
            Settings.loadListFromDb(database = WORKING_DATABASE)
        } else {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            val result: MutableList<Settings> = mutableListOf()
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let { result.add(it) }
            }
            result.toList()
        }

        val (countCopy, countCode) = collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(
            settingsList = settingsList, priorLyrics = priorLyrics, priorKaraoke = priorKaraoke)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление хранилища",
            body = "Скопировано песен в хранилище: $countCopy, создано заданий на кодирование: $countCode"
        )))
        return listOf(countCopy, countCode)
    }


    // Обновить пустые BPM и KEY из фалов CSV
    @PostMapping("/utils/updatebpmandkey")
    @ResponseBody
    fun doUpdateBpmAndKey() {
        val result =  updateBpmAndKey(WORKING_DATABASE)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление BPM и KEY из фалов CSV",
            body = "Обновлено пустых BPM и KEY из фалов CSV: $result"
        )))
    }

    // Обновить пустые BPM и KEY из фалов LV
    @PostMapping("/utils/updatebpmandkeylv")
    @ResponseBody
    fun doUpdateBpmAndKeyLV() {
        val (resultSuccess, resultFailed) =  updateBpmAndKeyLV(WORKING_DATABASE)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление BPM и KEY из фалов LV",
            body = "Обновлено пустых BPM и KEY из фалов LV: $resultSuccess" + if(resultFailed == 0) "" else ", Не удалось обновить файлов: $resultFailed"
        )))
    }

    // Найти и пометить дубликаты песен автора
    @PostMapping("/utils/markdublicates")
    @ResponseBody
    fun doMarkDublicates(
        @RequestParam(required = true) author: String) {
        val result = markDublicates(author, WORKING_DATABASE)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Нахождение дубликатов",
            body = "Найдено и отмечено дубликатов: $result"
        )))
    }

    // Удалить дубликаты
    @PostMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates() {
        val result = delDublicates(WORKING_DATABASE)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Удаление дубликатов",
            body = "Удалено дубликатов: $result"
        )))
    }

    // Очистить информацию о пре-дубликатах
    @PostMapping("/utils/clearpredublicates")
    @ResponseBody
    fun doClearPreDublicates() {
        val result = clearPreDublicates(WORKING_DATABASE)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Очистка пре-дубликатов",
            body = "Очищено пре-дубликатов: $result"
        )))
    }

    // Выполнить Custom Function
    @PostMapping("/utils/customfunction")
    @ResponseBody
    fun doCustomFunction() {
        val result = customFunction()
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Custom Function",
            body = "CustomFunction выполнена с результатом: «$result»"
        )))
    }

    @PostMapping("/processes/deletedone")
    @ResponseBody
    fun doProcessDeleteDone() {
        KaraokeProcessWorker.deleteDone(WORKING_DATABASE)
    }

    @PostMapping("/processes/workerstatus")
    @ResponseBody
    fun getProcessWorkerStatus(): Map<String, Any> {
        return mapOf("isWork" to KaraokeProcessWorker.isWork, "stopAfterThreadIsDone" to KaraokeProcessWorker.stopAfterThreadIsDone)
    }

    @PostMapping("/processes/workerstartstop")
    @ResponseBody
    fun getProcessWorkerStartStop() {
        if (KaraokeProcessWorker.isWork) {
            KaraokeProcessWorker.stop()
        } else {
            KaraokeProcessWorker.start(WORKING_DATABASE)
        }
    }

    @GetMapping("/subscribe")
    fun subscribeSse(
        response: HttpServletResponse
    ): SseEmitter {

        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
        response.setHeader("X-Accel-Buffering", "no")

//        val tabId: String = "tabId"
//        val userId: Long = 1L

        return sseNotificationService.subscribe()
    }

//    @PostMapping("/send/{targetUserId}")
//    suspend fun sendSse(@PathVariable targetUserId: Long, @RequestBody payload: RecordChangeMessage) {
//        sseNotificationService.send(targetUserId, payload)
//    }

}
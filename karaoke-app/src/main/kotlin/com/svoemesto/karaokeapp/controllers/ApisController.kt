package com.svoemesto.karaokeapp.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.textfiledictionary.TextFileDictionary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.io.File
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

@Controller
@RequestMapping("/apis")
class ApisController(private val webSocket: SimpMessagingTemplate,
                     private val objectMapper: ObjectMapper
) {

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
            if (result.isNotEmpty()) {
                println("time = $time, ids = $result");
            }

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

    // Получение списка песен по списку id
    @PostMapping("/songs/ids")
    @ResponseBody
    fun apisSongsByIds(@RequestParam ids: List<Long>): List<SettingsDTO> {
        return Settings.loadListFromDb(mapOf(Pair("ids", ids.joinToString(","))), WORKING_DATABASE).map { it.toDTO() }
    }

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

    @PostMapping("/unpublications")
    @ResponseBody
    fun unpublications(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getUnPublicationList(WORKING_DATABASE).map { it.map { it.toDTO() } }
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

        println("args: $args")

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to Settings.loadListFromDb(args, WORKING_DATABASE).map { it.toDTO() }.chunked(pageSize),
            "authors" to Settings.loadListAuthors(WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE)
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

    // Получение песени
    @PostMapping("/song")
    @ResponseBody
    fun apisSong(@RequestParam id: String): Any? {
        return Settings.loadFromDbById(id.toLong(), WORKING_DATABASE)?.toDTO()
    }

    @PostMapping("/update")
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
        @RequestParam(required = false) idTelegramChords: String?
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
            idStatus?.let { sett.fields[SettingField.ID_STATUS] =  it }
            sett.saveToDb()
            sett.saveToFile()
//            if (idBoosty != "" && idBoosty != null) {
//                sett.createVKDescription()
//            }
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
    fun getSongCreateKaraoke(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let {
            settings.createKaraoke()
            if (settings.idStatus < 3) {
                settings.fields[SettingField.ID_STATUS] = "3"
                settings.saveToDb()
            }
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 0)
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
            true
        } ?: false
    }

    // Создаём караоке для всех
    @PostMapping("/songs/createkaraokeall")
    @ResponseBody
    fun getSongsCreateKaraokeAll(@RequestParam songsIds: String): Boolean {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    settings.createKaraoke()
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 10)
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 10)
                }
                result = true
            }
        }
        return result
    }

    // DEMUCS2 для песни
    @PostMapping("/song/demucs2")
    @ResponseBody
    fun doProcessDemucs2(@RequestParam id: Long): Int {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            return  KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, -1)
        }
        return 0
    }

    // DEMUCS2 для всех
    @PostMapping("/songs/createdemucs2all")
    @ResponseBody
    fun getSongsCreateDemucs2All(@RequestParam songsIds: String): Boolean {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, -1)
                }
                result = true
            }
        }
        return result
    }

    // Удаляем песню
    @PostMapping("/song/delete")
    @ResponseBody
    fun doDeleteSong(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            it.deleteFromDb()
        }
        return true
    }

    // Создаём SYMLINKs для песни
    @PostMapping("/song/symlink")
    @ResponseBody
    fun doSymlink(@RequestParam id: Long): Int {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            it.doSymlink()
        }
        return 0
    }

    // Создаём SYMLINKs для всех
    @PostMapping("/songs/createsymlinksall")
    @ResponseBody
    fun getSongsCreateSymlinksAll(@RequestParam songsIds: String): Boolean {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doSymlink()
                }
                result = true
            }
        }
        return result
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
    fun doSetPublishDateTimeToAuthor(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            Settings.setPublishDateTimeToAuthor(settings)
        }
        return true
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
    ): Boolean {
        return TextFileDictionary.doAction(dictName, dictAction, listOf(dictValue))
    }

    // Обновляем RemoteDatabase
    @PostMapping("/utils/updateremotedatabasefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteDatabaseFromLocalDatabase(
        @RequestParam(required = true) updateSettings: Boolean = true,
        @RequestParam(required = true) updatePictures: Boolean = true
    ): List<Int> {
        val result = updateRemoteDatabaseFromLocalDatabase(updateSettings,updatePictures)

        return listOf(result.first, result.second, result.third)
    }

    // Обновляем LocalDatabase
    @PostMapping("/utils/updatelocaldatabasefromremotedatabase")
    @ResponseBody
    fun doUpdateLocalDatabaseFromRemoteDatabase(
        @RequestParam(required = true) updateSettings: Boolean = true,
        @RequestParam(required = true) updatePictures: Boolean = true
    ): List<Int> {
        val result = updateLocalDatabaseFromRemoteDatabase(updateSettings,updatePictures)

        return listOf(result.first, result.second, result.third)
    }

    // Добавление файлов из папки
    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String): Int {
        return Settings.createFromPath(folder, WORKING_DATABASE).size
    }

    // Создание картинок Dzen для папки
    @PostMapping("/utils/createdzenpicturesforfolder")
    @ResponseBody
    fun doCreateDzenPicturesForFolder(
        @RequestParam(required = true) folder: String): Boolean {
        createDzenPicture(folder)
        return true
    }

    @PostMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(): Any {
        val result = collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(WORKING_DATABASE)
        return listOf(result.first, result.second)
    }


    // Обновить пустые BPM и KEY из фалов CSV
    @PostMapping("/utils/updatebpmandkey")
    @ResponseBody
    fun doUpdateBpmAndKey(): Int {
        return updateBpmAndKey(WORKING_DATABASE)
    }

    // Найти и пометить дубликаты песен автора
    @PostMapping("/utils/markdublicates")
    @ResponseBody
    fun doMarkDublicates(
        @RequestParam(required = true) author: String): Int {
        return markDublicates(author, WORKING_DATABASE)
    }

    // Удалить дубликаты
    @PostMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates(): Int {
        return delDublicates(WORKING_DATABASE)
    }

    // Очистить информацию о пре-дубликатах
    @PostMapping("/utils/clearpredublicates")
    @ResponseBody
    fun doClearPreDublicates(): Int {
        return clearPreDublicates(WORKING_DATABASE)
    }

    // Выполнить Custom Function
    @PostMapping("/utils/customfunction")
    @ResponseBody
    fun doCustomFunction(): String {
        return customFunction()
    }

}
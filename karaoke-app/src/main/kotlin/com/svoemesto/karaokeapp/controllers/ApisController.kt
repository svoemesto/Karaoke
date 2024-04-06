package com.svoemesto.karaokeapp.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
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

    // Получение списка песен по списку id
    @PostMapping("/songs/ids")
    @ResponseBody
    fun apisSongsByIds(@RequestParam ids: List<Long>): List<SettingsDTO> {
        return Settings.loadListFromDb(mapOf(Pair("ids", ids.joinToString(","))), WORKING_DATABASE).map { it.getDTO() }
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
            "pages" to Settings.loadListFromDb(args, WORKING_DATABASE).map { it.getDTO() }.chunked(pageSize),
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
        return Settings.loadFromDbById(id.toLong(), WORKING_DATABASE)?.getDTO()
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

}
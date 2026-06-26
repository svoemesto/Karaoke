package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeweb.dto.SettingsPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaPublicDto
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

/**
 * JSON API для нового публичного SPA (karaoke-public). Чисто аддитивный контроллер:
 * MainController и его Thymeleaf-роуты не меняются и продолжают обслуживать старый сайт.
 */
@RestController
@RequestMapping("/api/public")
class PublicApiController(
    private val mainController: MainController,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient
) {

    @GetMapping("/stats")
    fun stats(request: HttpServletRequest): Map<String, Int> {
        mainController.doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "main", "parameters" to emptyMap<String, Any>(), "referer" to request.remoteHost))
        return mapOf(
            "onSponsr" to StatBySong.getCountSongsInCollection(database = WORKING_DATABASE),
            "onAir" to StatBySong.getCountSongsOnAir(database = WORKING_DATABASE),
            "exclusive" to StatBySong.getCountSongsExclusive(database = WORKING_DATABASE),
        )
    }

    @GetMapping("/authors")
    fun authors(): List<String> = Settings.loadListAuthors(withSkiped = false, database = WORKING_DATABASE)

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        request: HttpServletRequest
    ): List<ZakromaPublicDto> {
        val data: MutableMap<String, Any> = mutableMapOf()
        author?.let { data["author"] = it }
        mainController.doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "zakroma", "parameters" to data, "referer" to request.remoteHost))
        val zakroma = Zakroma.getZakroma(
            author = author ?: "",
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        )
        return ZakromaPublicDto.fromZakroma(zakroma)
    }

    @GetMapping("/songs")
    fun songs(
        @RequestParam(required = false) songName: String?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) album: String?,
        request: HttpServletRequest
    ): List<SettingsPublicDto> {
        val attr: MutableMap<String, String> = mutableMapOf()
        if (!songName.isNullOrEmpty()) attr["song_name"] = songName
        if (!author.isNullOrEmpty()) attr["author"] = author
        if (!text.isNullOrEmpty()) attr["text"] = text
        if (!album.isNullOrEmpty()) attr["song_album"] = album

        val settings: List<Settings> = if ("${songName ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length < 3) {
            emptyList()
        } else {
            Settings.loadListFromDb(attr, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true)
        }

        val data: MutableMap<String, Any> = mutableMapOf()
        if (!songName.isNullOrEmpty()) data["song_name"] = songName
        if (!author.isNullOrEmpty()) data["author"] = author
        if (!text.isNullOrEmpty()) data["text"] = text
        if (!album.isNullOrEmpty()) data["album"] = album
        mainController.doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "filter", "parameters" to data, "referer" to request.remoteHost))

        return settings.map { SettingsPublicDto.fromSettings(it, includeDetails = false) }
    }

    @GetMapping("/song/{id}")
    fun song(
        @PathVariable id: Long,
        request: HttpServletRequest
    ): SettingsPublicDto? {
        val sett = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        mainController.doRegisterEvent(mapOf("eventType" to "callRest", "restName" to "song", "parameters" to mapOf("id" to id), "referer" to request.remoteHost))
        return sett?.let { SettingsPublicDto.fromSettings(it) }
    }

    @PostMapping("/events")
    fun events(@RequestParam(required = true) data: Map<String, Any>): Boolean = mainController.doRegisterEvent(data)
}

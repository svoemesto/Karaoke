package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.Pictures
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.resizeBufferedImage
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeweb.dto.SettingsPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaPublicDto
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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

    @GetMapping("/song-picture/{id}")
    fun songPicture(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val bucket = "karaoke"
        val cacheKey = "song_banner_$id.png"

        if (storageService.fileExists(bucket, cacheKey)) {
            val bytes = storageService.downloadFile(bucket, cacheKey).use { it.readBytes() }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        }

        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE,
            storageService = storageService, storageApiClient = storageApiClient)
            ?: return ResponseEntity.notFound().build()

        val albumPicName = "${settings.author} - ${settings.year} - ${settings.album}"
        val albumPic = Pictures.getPictureByName(albumPicName, WORKING_DATABASE,
            storageService, storageApiClient, ignoreUseInList = false)
        val authorPic = Pictures.getPictureByName(settings.author, WORKING_DATABASE,
            storageService, storageApiClient, ignoreUseInList = false)

        val frameW = 800; val frameH = 194
        val resultImage = BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB)
        val g = resultImage.graphics as Graphics2D
        g.color = Color.BLACK
        g.fillRect(0, 0, frameW, frameH)

        fun loadFromMinIO(fileName: String): BufferedImage? {
            if (fileName.isEmpty() || !storageService.fileExists(bucket, fileName)) return null
            return storageService.downloadFile(bucket, fileName).use { ImageIO.read(it) }
        }

        loadFromMinIO(albumPic?.storageFileName ?: "")?.let {
            g.drawImage(resizeBufferedImage(it, 154, 154), 20, 20, null)
        }
        loadFromMinIO(authorPic?.storageFileName ?: "")?.let {
            g.drawImage(resizeBufferedImage(it, 385, 154), 294, 20, null)
        }
        g.dispose()

        val out = ByteArrayOutputStream()
        ImageIO.write(resultImage, "png", out)
        val bytes = out.toByteArray()
        storageService.uploadFile(bucket, cacheKey, ByteArrayInputStream(bytes), bytes.size.toLong())

        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
    }

    @GetMapping("/picture")
    fun picture(@RequestParam file: String): ResponseEntity<ByteArray> {
        val bucket = "karaoke"
        if (storageService.fileExists(bucket, file)) {
            val bytes = storageService.downloadFile(bucket, file).use { it.readBytes() }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        }
        val isAuthor = file.endsWith(".preview.author.png")
        val fullFile = if (isAuthor)
            file.replace(".preview.author.png", ".author.png")
        else
            file.replace(".preview.album.png", ".album.png")
        if (!storageService.fileExists(bucket, fullFile)) return ResponseEntity.notFound().build()
        val fullBytes = storageService.downloadFile(bucket, fullFile).use { it.readBytes() }
        val bi = ImageIO.read(ByteArrayInputStream(fullBytes))
        val (newW, newH) = if (isAuthor) 125 to 50 else 50 to 50
        val previewBi = resizeBufferedImage(bi, newW = newW, newH = newH)
        val out = ByteArrayOutputStream()
        ImageIO.write(previewBi, "png", out)
        val previewBytes = out.toByteArray()
        storageService.uploadFile(bucket, file, ByteArrayInputStream(previewBytes), previewBytes.size.toLong())
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(previewBytes)
    }
}

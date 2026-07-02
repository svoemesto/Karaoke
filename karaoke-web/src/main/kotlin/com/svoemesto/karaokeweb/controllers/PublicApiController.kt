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
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
    private val storageApiClient: StorageApiClient,
    private val gestureUnlockService: PlayerGestureUnlockService
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
    fun events(@RequestParam(required = true) data: Map<String, Any>, request: HttpServletRequest): Map<String, Any?> {
        val ok = mainController.doRegisterEvent(data)

        // Piggy-backs the hidden player-unlock gesture on this same ordinary-looking click-tracking
        // call. Nothing about which field/click-count/timing matters is decided here or in any
        // frontend code — that logic lives entirely in PlayerGestureUnlockService on the server.
        var meta: String? = null
        if (data["eventType"] == "clickToLink" && data["linkType"] == "songMeta") {
            val songId = (data["songId"] as? String)?.toLongOrNull()
            val field = data["linkName"] as? String
            val shiftKey = (data["shiftKey"] as? String)?.toBoolean() ?: false
            val clientId = (data["clientId"] as? String)?.takeIf { it.isNotBlank() } ?: request.remoteHost
            if (songId != null && field != null) {
                meta = gestureUnlockService.registerClick(clientId, songId, field, shiftKey)
            }
        }

        return mapOf("ok" to ok, "meta" to meta)
    }

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

    @GetMapping("/song-vk-image/{id}")
    fun songVkImage(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE,
            storageService = storageService, storageApiClient = storageApiClient)
            ?: return ResponseEntity.notFound().build()

        val cacheFile = File("/tmp/vk_$id.png")
        val bucket = "karaoke"
        val albumPicName = "${settings.author} - ${settings.year} - ${settings.album}"
        val albumPic = Pictures.getPictureByName(albumPicName, WORKING_DATABASE,
            storageService, storageApiClient, ignoreUseInList = false)
        val authorPic = Pictures.getPictureByName(settings.author, WORKING_DATABASE,
            storageService, storageApiClient, ignoreUseInList = false)

        val albumFile = albumPic?.let {
            "${settings.author}/${settings.year} - ${settings.album}/${albumPicName}.album.png"
                .takeIf { storageService.fileExists(bucket, it) }
        }
        val authorFile = authorPic?.let {
            "${settings.author}/${settings.author}.author.png"
                .takeIf { storageService.fileExists(bucket, it) }
        }

        if (albumFile == null || authorFile == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/KARAOKE_LOGO.png")
                .build()
        }

        if (cacheFile.exists()) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(cacheFile.readBytes())
        }

        val frameW = 537; val frameH = 240; val padding = 20
        val picAreaH = 176
        val albumW = ((frameW - 3 * padding) / 3.5).toInt()
        val albumH = albumW
        val authorW = (albumW * 2.5).toInt()
        val authorH = albumH

        val resultImage = BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB)
        val g = resultImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fillRect(0, 0, frameW, frameH)

        fun loadFromMinIO(fileName: String): BufferedImage =
            storageService.downloadFile(bucket, fileName).use { ImageIO.read(it) }

        g.drawImage(resizeBufferedImage(loadFromMinIO(albumFile), albumW, albumH), padding, padding, null)
        g.drawImage(resizeBufferedImage(loadFromMinIO(authorFile), authorW, authorH), albumW + 2 * padding, padding, null)

        val textAreaW = frameW - 2 * padding
        val textAreaH = frameH - picAreaH
        val songText = settings.songName
        val baseFont = PublicApiController::class.java.getResourceAsStream("/Roboto-Black.ttf")
            ?.let { Font.createFont(Font.TRUETYPE_FONT, it) }
            ?: Font("SansSerif", Font.PLAIN, 10)
        var fontSize = textAreaH
        var font = baseFont.deriveFont(fontSize.toFloat())
        g.font = font
        while (g.fontMetrics.stringWidth(songText) > textAreaW && fontSize > 8) {
            fontSize--
            font = baseFont.deriveFont(fontSize.toFloat())
            g.font = font
        }
        g.color = Color(255, 255, 127)
        val fm = g.fontMetrics
        val textX = padding + maxOf(0, (textAreaW - fm.stringWidth(songText)) / 2)
        val textY = picAreaH + (textAreaH + fm.ascent - fm.descent) / 2
        g.drawString(songText, textX, textY)

        g.dispose()

        val out = ByteArrayOutputStream()
        ImageIO.write(resultImage, "png", out)
        val bytes = out.toByteArray()
        cacheFile.writeBytes(bytes)

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

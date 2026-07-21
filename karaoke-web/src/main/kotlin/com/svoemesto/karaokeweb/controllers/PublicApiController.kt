package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.Author
import com.svoemesto.karaokeapp.model.EventType
import com.svoemesto.karaokeapp.model.Pictures
import com.svoemesto.karaokeapp.model.RestName
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.resizeBufferedImage
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeweb.dto.AuthorTilePublicDto
import com.svoemesto.karaokeweb.dto.SettingsPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaPublicDto
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import com.svoemesto.karaokeweb.services.SiteUserResolver
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
import java.net.URI
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
    private val gestureUnlockService: PlayerGestureUnlockService,
    private val siteUserResolver: SiteUserResolver,
    @org.springframework.beans.factory.annotation.Value("\${storage.proxy-url}") private val minioProxyUrl: String,
) {
    // Fetches a PNG from MinIO via the nginx /minio/ proxy on the host.
    // The proxy runs on the host (MTU=1450), avoiding the Docker MTU=1500 mismatch
    // that causes silent packet drops when Java contacts the remote MinIO directly.
    private fun fetchFromMinIO(fileName: String): ByteArray? {
        if (fileName.isEmpty()) return null
        val encodedPath =
            fileName.split("/").joinToString("/") { segment ->
                java.net.URLEncoder
                    .encode(segment, Charsets.UTF_8)
                    .replace("+", "%20")
            }
        return try {
            val conn =
                java.net
                    .URL("$minioProxyUrl/minio/karaoke/$encodedPath")
                    .openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) {
            println("fetchFromMinIO error for $fileName: ${e.message}")
            null
        }
    }

    @GetMapping("/stats")
    fun stats(
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): Map<String, Int> {
        mainController.doRegisterEvent(
            mapOf(
                "eventType" to EventType.CALL_REST.dbValue,
                "restName" to RestName.MAIN.dbValue,
                "parameters" to emptyMap<String, Any>(),
                "anonId" to (anonId ?: ""),
                "referrer" to (referrer ?: ""),
            ),
            request,
            siteUserResolver.resolve(request)?.id ?: 0,
        )
        return mapOf(
            "onSponsr" to StatBySong.getCountSongsInCollection(database = WORKING_DATABASE),
            "onAir" to StatBySong.getCountSongsOnAir(database = WORKING_DATABASE),
            "exclusive" to StatBySong.getCountSongsExclusive(database = WORKING_DATABASE),
            "inWork" to StatBySong.getCountSongsInWork(database = WORKING_DATABASE),
            "total" to StatBySong.getCountSongsTotal(database = WORKING_DATABASE),
        )
    }

    @GetMapping("/authors")
    fun authors(): List<String> = Settings.loadListAuthors(withSkiped = false, database = WORKING_DATABASE)

    @GetMapping("/authors-tiles")
    fun authorsTiles(): List<AuthorTilePublicDto> {
        val counts = Settings.loadAuthorSongCounts(WORKING_DATABASE)
        return Settings
            .loadListAuthors(withSkiped = false, database = WORKING_DATABASE)
            .map { AuthorTilePublicDto.fromAuthorName(it, counts[it] ?: 0L) }
    }

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): List<ZakromaPublicDto> {
        val data: MutableMap<String, Any> = mutableMapOf()
        author?.let { data["author"] = it }
        mainController.doRegisterEvent(
            mapOf(
                "eventType" to EventType.CALL_REST.dbValue,
                "restName" to RestName.ZAKROMA.dbValue,
                "parameters" to data,
                "anonId" to (anonId ?: ""),
                "referrer" to (referrer ?: ""),
            ),
            request,
            siteUserResolver.resolve(request)?.id ?: 0,
        )
        val zakroma =
            Zakroma.getZakroma(
                author = author ?: "",
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return ZakromaPublicDto.fromZakroma(zakroma)
    }

    @GetMapping("/songs")
    fun songs(
        @RequestParam(required = false) songName: String?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) album: String?,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): List<SettingsPublicDto> {
        val attr: MutableMap<String, String> = mutableMapOf()
        if (!songName.isNullOrEmpty()) attr["song_name"] = songName
        // Поиск по автору: сначала резолвим term (может быть и реальным именем, и алиасом —
        // солист/участник группы) через tbl_authors, затем ищем песни по НАБОРУ реальных имён.
        // Если совпадений в tbl_authors нет (автор не заведён как отслеживаемый) — фолбэк на
        // прежнее строгое равенство, чтобы не потерять существующее поведение поиска.
        var aliasByAuthor: Map<String, String> = emptyMap()
        if (!author.isNullOrEmpty()) {
            val matches = Author.resolveByTerm(author, WORKING_DATABASE)
            if (matches.isNotEmpty()) {
                attr["author_in"] = matches.joinToString(Settings.AUTHOR_IN_DELIMITER) { it.author }
                aliasByAuthor =
                    matches
                        .filter { it.matchedAliases.isNotEmpty() }
                        .associate { it.author.lowercase() to it.matchedAliases.joinToString(", ") }
            } else {
                attr["author"] = author
            }
        }
        if (!text.isNullOrEmpty()) attr["text"] = text
        if (!album.isNullOrEmpty()) attr["song_album"] = album

        val settings: List<Settings> =
            if ("${songName ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length < 3) {
                emptyList()
            } else {
                Settings.loadListFromDb(
                    attr,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            }

        val data: MutableMap<String, Any> = mutableMapOf()
        if (!songName.isNullOrEmpty()) data["song_name"] = songName
        if (!author.isNullOrEmpty()) data["author"] = author
        if (!text.isNullOrEmpty()) data["text"] = text
        if (!album.isNullOrEmpty()) data["album"] = album
        mainController.doRegisterEvent(
            mapOf(
                "eventType" to EventType.CALL_REST.dbValue,
                "restName" to RestName.FILTER.dbValue,
                "parameters" to data,
                "anonId" to (anonId ?: ""),
                "referrer" to (referrer ?: ""),
            ),
            request,
            siteUserResolver.resolve(request)?.id ?: 0,
        )

        return settings.map {
            val dto = SettingsPublicDto.fromSettings(it, includeDetails = false)
            dto.copy(authorAlias = aliasByAuthor[dto.author.lowercase()] ?: "")
        }
    }

    @GetMapping("/song/{id}")
    fun song(
        @PathVariable id: Long,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): SettingsPublicDto? {
        val sett =
            Settings.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        mainController.doRegisterEvent(
            mapOf(
                "eventType" to EventType.CALL_REST.dbValue,
                "restName" to RestName.SONG.dbValue,
                "parameters" to mapOf("id" to id),
                "anonId" to (anonId ?: ""),
                "referrer" to (referrer ?: ""),
            ),
            request,
            siteUserResolver.resolve(request)?.id ?: 0,
        )
        return sett?.let { SettingsPublicDto.fromSettings(it) }
    }

    @PostMapping("/events")
    fun events(
        @RequestParam(required = true) data: Map<String, Any>,
        request: HttpServletRequest,
    ): Map<String, Any?> {
        val ok = mainController.doRegisterEvent(data, request, siteUserResolver.resolve(request)?.id ?: 0)

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
    fun songPicture(
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val bucket = "karaoke"
//        val cacheKey = "song_banner_$id.png"

//        if (storageService.fileExists(bucket, cacheKey)) {
//            val bytes = storageService.downloadFile(bucket, cacheKey).use { it.readBytes() }
//            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
//        }

        val settings =
            Settings.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: return ResponseEntity.notFound().build()

        val albumPicName = "${settings.author} - ${settings.year} - ${settings.album}"
        val albumPic =
            Pictures.getPictureByName(
                albumPicName,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
                ignoreUseInList = false,
            )
        val authorPic =
            Pictures.getPictureByName(
                settings.author,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
                ignoreUseInList = false,
            )

        val frameW = 800
        val frameH = 194
        val resultImage = BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB)
        val g = resultImage.graphics as Graphics2D
        g.color = Color.BLACK
        g.fillRect(0, 0, frameW, frameH)

        fun loadFromMinIO(fileName: String): BufferedImage? = fetchFromMinIO(fileName)?.let { ImageIO.read(ByteArrayInputStream(it)) }

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
//        storageService.uploadFile(bucket, cacheKey, ByteArrayInputStream(bytes), bytes.size.toLong())
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
    }

    @GetMapping("/song-vk-image/{id}")
    fun songVkImage(
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val settings =
            Settings.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: return ResponseEntity.notFound().build()

        val cacheFile = File("/tmp/vk_$id.png")
        val bucket = "karaoke"
        val albumPicName = "${settings.author} - ${settings.year} - ${settings.album}"
        val albumPic =
            Pictures.getPictureByName(
                albumPicName,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
                ignoreUseInList = false,
            )
        val authorPic =
            Pictures.getPictureByName(
                settings.author,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
                ignoreUseInList = false,
            )

        val albumFilePath = "${settings.author}/${settings.year} - ${settings.album}/$albumPicName.album.png"
        val authorFilePath = "${settings.author}/${settings.author}.author.png"

        if (cacheFile.exists()) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(cacheFile.readBytes())
        }

        val albumBytes = fetchFromMinIO(albumFilePath)
        val authorBytes = fetchFromMinIO(authorFilePath)

        if (albumBytes == null || authorBytes == null) {
            return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/KARAOKE_LOGO.png")
                .build()
        }

        val frameW = 537
        val frameH = 240
        val padding = 20
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

        g.drawImage(resizeBufferedImage(ImageIO.read(ByteArrayInputStream(albumBytes)), albumW, albumH), padding, padding, null)
        g.drawImage(
            resizeBufferedImage(ImageIO.read(ByteArrayInputStream(authorBytes)), authorW, authorH),
            albumW + 2 * padding,
            padding,
            null,
        )

        val textAreaW = frameW - 2 * padding
        val textAreaH = frameH - picAreaH
        val songText = settings.songName
        val baseFont =
            PublicApiController::class.java
                .getResourceAsStream("/Roboto-Black.ttf")
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
    fun picture(
        @RequestParam file: String,
    ): ResponseEntity<Void> {
        // Redirect to nginx MinIO proxy — nginx runs on the host (MTU=1450) so large TCP packets
        // are not silently dropped the way they are when Java in Docker (MTU=1500) talks to
        // the remote MinIO server across an ens3 interface with MTU=1450.
        val encodedPath =
            file.split("/").joinToString("/") { segment ->
                java.net.URLEncoder
                    .encode(segment, Charsets.UTF_8)
                    .replace("+", "%20")
            }
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create("/minio/karaoke/$encodedPath"))
            .build()
    }
}

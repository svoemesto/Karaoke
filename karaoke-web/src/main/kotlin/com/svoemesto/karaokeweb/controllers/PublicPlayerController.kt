package com.svoemesto.karaokeweb.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Онлайн-плеер для публичного сайта (karaoke-public) — намеренно скрыт: доступен только тем,
 * у кого есть валидный токен, выданный [PlayerGestureUnlockService] после секретного жеста на
 * странице песни. Без токена (или с чужим/просроченным) все эндпоинты ведут себя как
 * несуществующий ресурс (404), а не как "тут есть защита, но у вас нет доступа" — чтобы сам факт
 * существования этого механизма не был очевиден по ответам API.
 *
 * ВАЖНО: karaoke-web выполняется на другом хосте, чем karaoke-app (тот, где физически лежат FLAC
 * и работает Demucs/ffmpeg) — доступа к локальным путям вроде Settings.accompanimentNameFlac у
 * этого процесса нет и быть не может (кроме того, обращение к ним из процесса karaoke-web роняет
 * его целиком, см. IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before
 * get — Settings.rootFolder тянет за собой инициализацию karaoke-app, которая тут никогда не
 * происходит; тот же класс проблем уже отмечен комментарием в SettingsPublicDto.kt).
 * Поэтому стемы читаются ИСКЛЮЧИТЕЛЬНО из MinIO (storageService, как и картинки в
 * PublicApiController) — их туда лениво заливает karaoke-app (см. ApiController.pushMp3ToStorage),
 * при каждом обращении к admin-эндпоинтам fileminus.mp3/filevoice.mp3/filebass.mp3/filedrums.mp3.
 * Если админ ни разу не открывал плеер для песни — стемов в MinIO ещё нет, и публичный плеер
 * корректно покажет "песня пока не может быть проиграна" вместо падения.
 */
@RestController
@RequestMapping("/api/public/player")
class PublicPlayerController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val gestureUnlockService: PlayerGestureUnlockService
) {
    private val bucket = "karaoke"

    private fun authorized(id: Long, token: String?): Boolean =
        token != null && gestureUnlockService.validateToken(token, id)

    // Same template HealthReport.kt/ApiController.pushMp3ToStorage use for every KaraokeFileType
    // with a REMOTE_STORAGE location: "${settings.storageFileName}${suffix}.${extention}" — suffix
    // already carries its own leading dot (e.g. ".accompaniment"), NOT a dash.
    private fun stemStorageKey(settings: Settings, fileType: KaraokeFileType) =
        "${settings.storageFileName}${fileType.suffix}.${fileType.extention}"

    private fun stemResponse(settings: Settings, fileType: KaraokeFileType): ResponseEntity<Resource> {
        val storageKey = stemStorageKey(settings, fileType)
        if (!storageService.fileExists(bucket, storageKey)) return ResponseEntity.notFound().build()
        val bytes = storageService.downloadFile(bucket, storageKey).use { it.readBytes() }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .body(ByteArrayResource(bytes))
    }

    private fun loadSettings(id: Long) =
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

    @GetMapping("/{id}/fileminus.mp3")
    fun fileAccompaniment(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<Resource> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_ACCOMPANIMENT)
    }

    @GetMapping("/{id}/filevoice.mp3")
    fun fileVocals(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<Resource> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_VOCAL)
    }

    @GetMapping("/{id}/filebass.mp3")
    fun fileBass(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<Resource> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_BASS)
    }

    @GetMapping("/{id}/filedrums.mp3")
    fun fileDrums(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<Resource> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_DRUMS)
    }

    @GetMapping("/{id}/playerdata")
    fun playerData(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<Map<String, Any?>> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()

        val tokenSuffix = "?token=${token}"
        val hasAccompaniment = storageService.fileExists(bucket, stemStorageKey(settings, KaraokeFileType.MP3_ACCOMPANIMENT))
        val hasVocals = storageService.fileExists(bucket, stemStorageKey(settings, KaraokeFileType.MP3_VOCAL))
        val hasBass = storageService.fileExists(bucket, stemStorageKey(settings, KaraokeFileType.MP3_BASS))
        val hasDrums = storageService.fileExists(bucket, stemStorageKey(settings, KaraokeFileType.MP3_DRUMS))

        val data = mapOf(
            "id" to id,
            "songName" to settings.songName,
            "author" to settings.author,
            "album" to settings.album,
            "year" to settings.year.takeIf { it > 0 },
            "track" to settings.track.takeIf { it > 0 },
            "key" to settings.key.takeIf { it.isNotBlank() },
            "bpm" to settings.bpm,
            "markers" to settings.sourceMarkersList,
            "audioAccompanimentUrl" to if (hasAccompaniment) "/api/public/player/$id/fileminus.mp3$tokenSuffix" else null,
            "audioVocalsUrl" to if (hasVocals) "/api/public/player/$id/filevoice.mp3$tokenSuffix" else null,
            "audioBassUrl" to if (hasBass) "/api/public/player/$id/filebass.mp3$tokenSuffix" else null,
            "audioDrumsUrl" to if (hasDrums) "/api/public/player/$id/filedrums.mp3$tokenSuffix" else null,
            "albumImageUrl" to settings.pictureAlbum?.storageFileName?.let { "/api/public/picture?file=$it" },
            "artistImageUrl" to settings.pictureAuthor?.storageFileName?.let { "/api/public/picture?file=$it" },
            "exportBaseName" to "${settings.fileName} [id-$id]".rightFileName()
        )
        return ResponseEntity.ok(data)
    }

    // "Сохранить файл" в скрытом плеере — тот же .smkaraoke-контейнер, что и admin-эндпоинт
    // /song/{id}/playerfile в karaoke-app, но собранный из MinIO (stemResponse-стемы + картинки),
    // без единого обращения к локальным дисковым путям.
    @GetMapping("/{id}/playerfile")
    fun playerFile(@PathVariable id: Long, @RequestParam token: String?, response: HttpServletResponse) {
        if (!authorized(id, token)) { response.status = 404; return }
        val settings = loadSettings(id) ?: run { response.status = 404; return }

        val tracks = mutableMapOf<String, String>()
        val images = mutableMapOf<String, String>()
        val bos = ByteArrayOutputStream()
        val zip = ZipOutputStream(bos)

        fun addStemIfPresent(fileType: KaraokeFileType, entryName: String, trackKey: String) {
            val storageKey = stemStorageKey(settings, fileType)
            if (storageService.fileExists(bucket, storageKey)) {
                val bytes = storageService.downloadFile(bucket, storageKey).use { it.readBytes() }
                addStored(zip, entryName, bytes)
                tracks[trackKey] = entryName
            }
        }
        addStemIfPresent(KaraokeFileType.MP3_ACCOMPANIMENT, "audio/accompaniment.mp3", "accompaniment")
        addStemIfPresent(KaraokeFileType.MP3_VOCAL, "audio/vocals.mp3", "vocals")
        addStemIfPresent(KaraokeFileType.MP3_BASS, "audio/bass.mp3", "bass")
        addStemIfPresent(KaraokeFileType.MP3_DRUMS, "audio/drums.mp3", "drums")

        settings.pictureAlbum?.let { pic ->
            if (storageService.fileExists(bucket, pic.storageFileName)) {
                val bytes = storageService.downloadFile(bucket, pic.storageFileName).use { it.readBytes() }
                addStored(zip, "images/album.png", bytes)
                images["album"] = "images/album.png"
            }
        }
        settings.pictureAuthor?.let { pic ->
            if (storageService.fileExists(bucket, pic.storageFileName)) {
                val bytes = storageService.downloadFile(bucket, pic.storageFileName).use { it.readBytes() }
                addStored(zip, "images/artist.png", bytes)
                images["artist"] = "images/artist.png"
            }
        }

        val manifest = mapOf(
            "version" to 1,
            "format" to "smkaraoke",
            "id" to id,
            "songName" to settings.songName,
            "author" to settings.author,
            "album" to settings.album,
            "year" to settings.year.takeIf { it > 0 },
            "track" to settings.track.takeIf { it > 0 },
            "key" to settings.key.takeIf { it.isNotBlank() },
            "bpm" to settings.bpm,
            "markers" to settings.sourceMarkersList,
            "tracks" to tracks,
            "images" to images,
            "exportBaseName" to "${settings.fileName} [id-$id]".rightFileName()
        )
        val manifestBytes = ObjectMapper().writeValueAsBytes(manifest)
        val manifestEntry = ZipEntry("manifest.json").apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(manifestEntry)
        zip.write(manifestBytes)
        zip.closeEntry()
        zip.close()

        val downloadName = "${settings.fileName} [id-$id].smkaraoke".rightFileName()
        val encodedName = URLEncoder.encode(downloadName, "UTF-8").replace("+", "%20")
        response.contentType = "application/x-smkaraoke"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"song-$id.smkaraoke\"; filename*=UTF-8''$encodedName")
        response.outputStream.write(bos.toByteArray())
    }

    private fun addStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32().also { it.update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }
}

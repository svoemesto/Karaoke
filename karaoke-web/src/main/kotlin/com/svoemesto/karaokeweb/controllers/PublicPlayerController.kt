package com.svoemesto.karaokeweb.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.model.EventType
import com.svoemesto.karaokeapp.model.PlayerAction
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import com.svoemesto.karaokeweb.services.SiteUserResolver
import com.svoemesto.karaokeweb.util.Mp3Trimmer
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Онлайн-плеер для публичного сайта (karaoke-public). Токен доступа к плееру выдаётся двумя
 * путями: (1) открыто, через [access] — страница песни сама решает по статусу "в эфире"/премиум,
 * можно ли встроить плеер вместо видео ВК / вместо сообщения об ожидании; (2) исторически —
 * секретным жестом на любой странице песни, см. [PlayerGestureUnlockService]. Оба пути выдают
 * структурно одинаковый токен из одного и того же хранилища. Без токена (или с чужим/просроченным)
 * файловые эндпоинты (fileminus.mp3 и т.п.) ведут себя как несуществующий ресурс (404).
 *
 * ДЕМО-РЕЖИМ: если контент готов ([stemsReady]), но пользователь не может смотреть целиком
 * (не премиум/не подписан/не "в эфире"), [access] всё равно выдаёт токен — но демо-токен,
 * ограниченный ДИАПАЗОНОМ (см. Settings.demoFragmentStartSeconds/demoFragmentEndSeconds — фрагмент
 * "первый куплет (текст группы 0) минус отступ под фейд-ин" по маркерам разметки; куплет закрывает
 * смена группы или принудительная пустая строка). Ограничение применяется на СЕРВЕРЕ в двух местах:
 * [stemResponse] обрезает байты стема через [Mp3Trimmer] (полный файл физически не отдаётся —
 * тот же принцип, что и у постоянно-публичного URL, см. комментарий у stemResponse ниже), а
 * [playerData] обрезает список маркеров ДО диапазона и ПЕРЕБАЗИРУЕТ их время (вычитает начало
 * диапазона), чтобы совпадать с обрезанным аудио, которое для плеера снова "начинается с нуля".
 * Различить обычный и демо-токен на фронте позволяют поля isDemo/demoFadeInSeconds в ответах
 * [access]/[playerData] — demoFadeInSeconds задаёт длину фейд-ина в начале фрагмента.
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
    private val gestureUnlockService: PlayerGestureUnlockService,
    private val siteUserResolver: SiteUserResolver,
    private val mainController: MainController,
    @Value("\${storage.proxy-url}") private val minioProxyUrl: String,
) {
    private val bucket = "karaoke"
    private val lenientJson = Json { ignoreUnknownKeys = true }

    private fun authorized(id: Long, token: String?): Boolean =
        token != null && gestureUnlockService.validateToken(token, id)

    // Живая проверка премиум-статуса — намеренно не кэшируется в токене плеера, чтобы бан/снятие
    // премиума посреди 30-минутного TTL токена плеера подействовали немедленно.
    private fun isPremiumUser(request: HttpServletRequest): Boolean =
        siteUserResolver.resolve(request)?.isEffectivePremium == true

    // Оформлена ли у текущего пользователя бессрочная подписка именно на эту песню (scope=SONG).
    // Отдельная ветка доступа от isPremiumUser — подписка на одну песню не даёт isEffectivePremium.
    private fun isSubscribedToSong(request: HttpServletRequest, id: Long): Boolean {
        val userId = siteUserResolver.resolve(request)?.id ?: return false
        return Subscription.isSubscribedToSong(userId, id, WORKING_DATABASE, storageService, storageApiClient)
    }

    private fun stemsReady(settings: Settings): Boolean =
        settings.idStatus >= 3 &&
            existsInMinIO(stemStorageKey(settings, KaraokeFileType.MP3_ACCOMPANIMENT)) &&
            existsInMinIO(stemStorageKey(settings, KaraokeFileType.MP3_VOCAL)) &&
            settings.sourceMarkersList.isNotEmpty()

    /**
     * Решает, можно ли прямо сейчас показать на странице песни встроенный онлайн-плеер вместо
     * видео ВК (для "в эфире") или сообщения об ожидании (для остального). Не требует секретного
     * жеста — это открытая, документированная точка входа для обычного показа плеера.
     */
    @GetMapping("/{id}/access")
    fun access(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        val ready = stemsReady(settings)
        val premium = isPremiumUser(request)
        val subscribed = !premium && isSubscribedToSong(request, id)
        val canWatch = ready && (settings.onAir || premium || subscribed)
        val canExport = canWatch && premium
        // Демо-режим: контент готов, но полного доступа нет — вместо отказа выдаём токен,
        // ограниченный диапазоном (фрагмент "куплет минус отступ под фейд-ин"), чтобы не-премиум
        // мог послушать и оценить разметку/качество перед подпиской.
        val isDemo = ready && !canWatch
        val demoFadeInSeconds = if (isDemo) settings.demoFragmentFadeInSeconds else null
        val token = when {
            canWatch -> gestureUnlockService.issueDirectAccessToken(id)
            isDemo -> gestureUnlockService.issueDemoAccessToken(id, settings.demoFragmentStartSeconds, settings.demoFragmentEndSeconds)
            else -> null
        }
        if (canWatch || isDemo) {
            // source=list — клик по иконке плеера в таблице «Закрома»/«Поиск» (осознанное открытие
            // новой вкладкой) логируется как OPENED; заход на страницу песни (пассивный показ
            // встроенного плеера) — как SHOWN.
            val linkType = if (request.getParameter("source") == "list")
                PlayerAction.OPENED.dbValue else PlayerAction.SHOWN.dbValue
            mainController.doRegisterEvent(
                mapOf(
                    "eventType" to EventType.PLAYER.dbValue,
                    "linkType" to linkType,
                    "songId" to id.toString(),
                    "anonId" to (request.getParameter("anonId") ?: ""),
                ),
                request, siteUserResolver.resolve(request)?.id ?: 0
            )
        }
        return ResponseEntity.ok(mapOf(
            "ready" to ready,
            "isPremiumUser" to premium,
            "canWatch" to canWatch,
            "canExport" to canExport,
            "isDemo" to isDemo,
            "demoFadeInSeconds" to demoFadeInSeconds,
            "token" to token,
        ))
    }

    /**
     * Пакетная проверка готовности плеера для списка песен (иконка плеера в таблицах «Закрома»/
     * «Поиск»). НИЧЕГО не логирует и НЕ выдаёт токенов — только сообщает, можно ли прямо сейчас
     * открыть плеер для каждой песни, чтобы фронт отрисовал активную/недоступную иконку (сам показ
     * плеера и его логирование происходят позже, по клику, через [access] с source=list).
     *
     * Премиум резолвится один раз на весь запрос. Возвращаемые поля на песню:
     *  - contentReady — премиум-независимая готовность контента ([stemsReady]: idStatus>=3 + оба стема
     *    в MinIO + непустые маркеры). Нужна фронту, чтобы отличить «золотую» монетку (контент готов,
     *    премиум смог бы открыть плеер прямо сейчас) от «серебряной» (ещё не готов).
     *  - watchable (= ready, для обратной совместимости) — может ли ПРЯМО СЕЙЧАС открыть плеер сам
     *    запрашивающий: contentReady && (onAir || premium). Управляет активностью иконки плеера.
     * Короткого замыкания по (onAir||premium) больше нет — [stemsReady] нужен и для не-onAir песен,
     * чтобы вычислить contentReady для монетки (тяжёлые 2 HEAD в MinIO смягчаются чанками/параллелизмом
     * на фронте, см. usePlayerReadiness.js).
     */
    @PostMapping("/readiness")
    fun readiness(@RequestParam ids: String, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val premium = isPremiumUser(request)
        val songIds = ids.split(",").mapNotNull { it.trim().toLongOrNull() }.distinct()
        // Батч-проверка подписок на песню одним запросом (не премиуму — премиум уже видит всё).
        val userId = if (premium) 0L else (siteUserResolver.resolve(request)?.id ?: 0L)
        val subscribedIds = if (premium) emptySet()
            else Subscription.subscribedSongIds(userId, songIds, WORKING_DATABASE, storageService, storageApiClient)
        val items = songIds.associate { id ->
            val settings = loadSettings(id)
            val contentReady = settings != null && stemsReady(settings)
            val watchable = contentReady && (settings!!.onAir || premium || id in subscribedIds)
            id.toString() to mapOf("ready" to watchable, "watchable" to watchable, "contentReady" to contentReady)
        }
        return ResponseEntity.ok(mapOf("items" to items))
    }

    // Same template HealthReport.kt/ApiController.pushMp3ToStorage use for every KaraokeFileType
    // with a REMOTE_STORAGE location: "${settings.storageFileName}${suffix}.${extention}" — suffix
    // already carries its own leading dot (e.g. ".accompaniment"), NOT a dash.
    private fun stemStorageKey(settings: Settings, fileType: KaraokeFileType) =
        "${settings.storageFileName}${fileType.suffix}.${fileType.extention}"

    private fun encodedProxyPath(storageKey: String): String =
        storageKey.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")
        }

    // HEAD-запрос через nginx-прокси — минует MinIO SDK и MTU-проблему Docker bridge
    private fun existsInMinIO(storageKey: String): Boolean = try {
        val conn = java.net.URL("$minioProxyUrl/minio/karaoke/${encodedProxyPath(storageKey)}")
            .openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.responseCode == 200
    } catch (e: Exception) { false }

    // GET-запрос через nginx-прокси — для ZIP-экспорта (.smkaraoke)
    private fun fetchFromMinIO(storageKey: String): ByteArray? = try {
        val conn = java.net.URL("$minioProxyUrl/minio/karaoke/${encodedProxyPath(storageKey)}")
            .openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 120_000
        if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) { null }

    // Байты стема проксируются напрямую через этот же токен-защищённый эндпоинт — НЕ 302-редиректом
    // на статический /minio/-путь. Редирект на постоянный публичный URL раньше был единственной
    // защитой (только на входе): токен проверялся один раз, а полученный браузером конечный адрес
    // MinIO дальше был доступен бессрочно и без проверки токена вообще (закладка/шаринг ссылки/кэш
    // download-менеджера обходили токен и его 30-минутный TTL). Проксирование байтов устраняет этот
    // постоянный публичный URL — каждый запрос снова проверяет token.
    private fun stemResponse(settings: Settings, fileType: KaraokeFileType, demoRange: PlayerGestureUnlockService.DemoRange?): ResponseEntity<ByteArray> {
        val storageKey = stemStorageKey(settings, fileType)
        val bytes = fetchFromMinIO(storageKey) ?: return ResponseEntity.notFound().build()
        // Демо-токен — обрезаем байты стема на границе mp3-фрейма (Mp3Trimmer), чтобы полный
        // файл физически не покидал сервер. Обычный токен получает bytes как есть.
        val payload = if (demoRange != null) Mp3Trimmer.trimToRange(bytes, demoRange.startSeconds, demoRange.endSeconds) else bytes
        return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg")).body(payload)
    }

    private fun loadSettings(id: Long) =
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

    @GetMapping("/{id}/fileminus.mp3")
    fun fileAccompaniment(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<ByteArray> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_ACCOMPANIMENT, gestureUnlockService.demoRangeForToken(token, id))
    }

    @GetMapping("/{id}/filevoice.mp3")
    fun fileVocals(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<ByteArray> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_VOCAL, gestureUnlockService.demoRangeForToken(token, id))
    }

    @GetMapping("/{id}/filebass.mp3")
    fun fileBass(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<ByteArray> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_BASS, gestureUnlockService.demoRangeForToken(token, id))
    }

    @GetMapping("/{id}/filedrums.mp3")
    fun fileDrums(@PathVariable id: Long, @RequestParam token: String?): ResponseEntity<ByteArray> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()
        return stemResponse(settings, KaraokeFileType.MP3_DRUMS, gestureUnlockService.demoRangeForToken(token, id))
    }

    @GetMapping("/{id}/playerdata")
    fun playerData(@PathVariable id: Long, @RequestParam token: String?, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        if (!authorized(id, token)) return ResponseEntity.notFound().build()
        val settings = loadSettings(id) ?: return ResponseEntity.notFound().build()

        // Если токен был выдан онлайн-редактором для конкретного задания (issueDirectAccessTokenForAssignment),
        // подставляем НЕОДОБРЕННЫЙ черновик этого задания целиком вместо опубликованных маркеров —
        // превью "проиграть, что я уже сделал" в самом редакторе (см. PublicSongEditorController.task).
        // Задание покрывает всю песню — черновик несёт ВСЕ голоса разом, отдельный voice не нужен.
        var markersList = settings.sourceMarkersList
        gestureUnlockService.assignmentIdForToken(token, id)?.let { assignmentId ->
            val assignment = SongAssignment.getById(assignmentId, WORKING_DATABASE, storageService, storageApiClient)
                ?.takeIf { it.songId == id }
            val draft = assignment?.let { SongAssignmentDraft.getByAssignment(it.id, WORKING_DATABASE, storageService, storageApiClient) }
            if (draft != null) {
                val draftMarkersPerVoice = draft.editedMarkersPerVoice(lenientJson)
                if (draftMarkersPerVoice.any { it.isNotEmpty() }) markersList = draftMarkersPerVoice
            }
        }

        // Демо-токен — обрезаем маркеры до диапазона фрагмента и ПЕРЕБАЗИРУЕМ их время (вычитаем
        // начало диапазона), чтобы совпасть с обрезанным аудио — stemResponse обрезает те же байты
        // стема тем же диапазоном через Mp3Trimmer.trimToRange, и для плеера итоговый файл снова
        // "начинается с нуля".
        val demoRange = gestureUnlockService.demoRangeForToken(token, id)
        var demoFadeInSeconds: Double? = null
        if (demoRange != null) {
            markersList = markersList.map { voiceMarkers ->
                voiceMarkers
                    .filter { it.time in demoRange.startSeconds..demoRange.endSeconds }
                    .map { it.copy(time = it.time - demoRange.startSeconds) }
            }
            demoFadeInSeconds = settings.demoFragmentFadeInSeconds
        }

        val tokenSuffix = "?token=${token}"
        val hasAccompaniment = existsInMinIO(stemStorageKey(settings, KaraokeFileType.MP3_ACCOMPANIMENT))
        val hasVocals = existsInMinIO(stemStorageKey(settings, KaraokeFileType.MP3_VOCAL))
        val hasBass = existsInMinIO(stemStorageKey(settings, KaraokeFileType.MP3_BASS))
        val hasDrums = existsInMinIO(stemStorageKey(settings, KaraokeFileType.MP3_DRUMS))

        val data = mapOf(
            "id" to id,
            "songName" to settings.songName,
            "author" to settings.author,
            "album" to settings.album,
            "year" to settings.year.takeIf { it > 0 },
            "track" to settings.track.takeIf { it > 0 },
            "key" to settings.key.takeIf { it.isNotBlank() },
            "bpm" to settings.bpm,
            "songType" to settings.songType.dbValue,
            "markers" to markersList,
            "audioAccompanimentUrl" to if (hasAccompaniment) "/api/public/player/$id/fileminus.mp3$tokenSuffix" else null,
            "audioVocalsUrl" to if (hasVocals) "/api/public/player/$id/filevoice.mp3$tokenSuffix" else null,
            "audioBassUrl" to if (hasBass) "/api/public/player/$id/filebass.mp3$tokenSuffix" else null,
            "audioDrumsUrl" to if (hasDrums) "/api/public/player/$id/filedrums.mp3$tokenSuffix" else null,
            "albumImageUrl" to settings.pictureAlbum?.storageFileName?.let { "/api/public/picture?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}" },
            "artistImageUrl" to settings.pictureAuthor?.storageFileName?.let { "/api/public/picture?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}" },
            "exportBaseName" to "${settings.fileName} [id-$id]".rightFileName(),
            // Живая проверка, не завязанная на TTL токена плеера — открывает/закрывает пункт меню
            // "Экспорт аудио..." на фронте. Сама выдача байт стемов (fileminus.mp3 и т.п.) этим
            // флагом не ограничена — эти же URL нужны и для обычного воспроизведения всем, у кого
            // есть валидный token; canExport — только про предложение сохранить файл себе.
            "canExport" to isPremiumUser(request),
            "isDemo" to (demoRange != null),
            "demoFadeInSeconds" to demoFadeInSeconds,
        )
        return ResponseEntity.ok(data)
    }

    // Полный .smkaraoke-контейнер — тот же формат, что и admin-эндпоинт /song/{id}/playerfile
    // в karaoke-app, но собранный из MinIO (стемы + картинки), без обращения к локальным дискам.
    // Пункт меню "Сохранить файл" убран из публичного плеера (KaraokePlayer.js, karaoke-public) —
    // этот эндпоинт больше не вызывается обычным UI, но оставлен для будущей платной выдачи файла
    // покупателям. Защищён отдельно от токена плеера: требует живого премиум-статуса — токен
    // плеера сам по себе доступ к паковке не даёт (иначе любой посмотревший видео "в эфире" мог бы
    // напрямую дёрнуть URL и получить весь платный файл).
    @GetMapping("/{id}/playerfile")
    fun playerFile(@PathVariable id: Long, @RequestParam token: String?, request: HttpServletRequest, response: HttpServletResponse) {
        if (!authorized(id, token)) { response.status = 404; return }
        if (!isPremiumUser(request)) { response.status = 403; response.contentType = "application/json"; response.writer.write("{\"error\":\"premium_required\"}"); return }
        val settings = loadSettings(id) ?: run { response.status = 404; return }

        val tracks = mutableMapOf<String, String>()
        val images = mutableMapOf<String, String>()
        val bos = ByteArrayOutputStream()
        val zip = ZipOutputStream(bos)

        fun addStemIfPresent(fileType: KaraokeFileType, entryName: String, trackKey: String) {
            fetchFromMinIO(stemStorageKey(settings, fileType))?.let { bytes ->
                addStored(zip, entryName, bytes)
                tracks[trackKey] = entryName
            }
        }
        addStemIfPresent(KaraokeFileType.MP3_ACCOMPANIMENT, "audio/accompaniment.mp3", "accompaniment")
        addStemIfPresent(KaraokeFileType.MP3_VOCAL, "audio/vocals.mp3", "vocals")
        addStemIfPresent(KaraokeFileType.MP3_BASS, "audio/bass.mp3", "bass")
        addStemIfPresent(KaraokeFileType.MP3_DRUMS, "audio/drums.mp3", "drums")

        settings.pictureAlbum?.let { pic ->
            fetchFromMinIO(pic.storageFileName)?.let { bytes ->
                addStored(zip, "images/album.png", bytes)
                images["album"] = "images/album.png"
            }
        }
        settings.pictureAuthor?.let { pic ->
            fetchFromMinIO(pic.storageFileName)?.let { bytes ->
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

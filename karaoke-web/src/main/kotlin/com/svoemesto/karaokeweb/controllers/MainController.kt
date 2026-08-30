package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.Crypto
import com.svoemesto.karaokeapp.model.EventType
import com.svoemesto.karaokeapp.model.LinkType
import com.svoemesto.karaokeapp.model.ListeningHistory
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.PlayerAction
import com.svoemesto.karaokeapp.model.RestName
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SongReleaseAnnouncementService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.SamplingFilter
import com.svoemesto.karaokeweb.services.WEB_WORK_IN_CONTAINER
import com.svoemesto.karaokeweb.util.ClientIpResolver
// import com.svoemesto.karaokeweb.services.KSS_WEB
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant

/**
 * Контроллер (HTTP/WebSocket endpoints) для main .
 *
 * @see AGENTS.md
 */
@Controller
class MainController(
    @Suppress("unused") private val webSocket: SimpMessagingTemplate,
    @Value($$"${work-in-container}") val wic: Long,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val samplingFilter: SamplingFilter,
    // FR-109 (спека 274-events-batch-insert): EventsBuffer для batch INSERT в tbl_events.
    // Kill-switch `karaoke.web.events.batch-enabled` (дефолт false — sync INSERT как раньше).
    // При включении — снижает RPS INSERT на ≥80% (50 INSERT/5 сек → 1 batch).
    private val eventsBuffer: com.svoemesto.karaokeweb.services.EventsBuffer,
) {
    private val log = LoggerFactory.getLogger(MainController::class.java)

    init {
        WEB_WORK_IN_CONTAINER = (wic != 0L)
        println("WEB_WORK_IN_CONTAINER = $WEB_WORK_IN_CONTAINER")
        println("storageService = $storageService")
        println("storageApiClient = $storageApiClient")
    }

    @GetMapping("/")
    fun main(
        model: Model,
        request: HttpServletRequest,
    ): String {
        // Атрибуты "onAir"/"exclusive" — исторические имена Thymeleaf-переменных (main.html),
        // содержимое теперь считается по новому правилу окна бесплатного доступа
        // (specs/143-song-free-access-window) — переименовывать сами имена атрибутов не стали,
        // это внутренние идентификаторы шаблона, не публичный контракт.
        model.addAttribute("onSponsr", StatBySong.getCountSongsInCollection(database = WORKING_DATABASE))
        model.addAttribute("onAir", StatBySong.getCountSongsFreeNow(database = WORKING_DATABASE))
        model.addAttribute("exclusive", StatBySong.getCountSongsSubscriptionOnly(database = WORKING_DATABASE))
        model.addAttribute("inWork", StatBySong.getCountSongsInWork(database = WORKING_DATABASE))
        model.addAttribute("total", StatBySong.getCountSongsTotal(database = WORKING_DATABASE))
        // Блок «Последние 5 новостей» на главной (specs/144-homepage-latest-news). Тот же источник,
        // что и SPA + публичная лента /news (см. PublicNewsController.list). Намеренно используем
        // News.loadPublished напрямую (а не отдельный эндпоинт /latest) — единый код-путь,
        // исключает дрейф «опубликованности» между фичами. На сбое БД список остаётся пустым,
        // шаблон рендерится без блока (или с пустой таблицей) — HTTP 200 OK, без падения.
        model.addAttribute(
            "latestNews",
            try {
                News.loadPublished(database = WORKING_DATABASE, limit = 5, offset = 0)
            } catch (e: Exception) {
                println("MainController.main loadPublished error: ${e.message}")
                emptyList()
            },
        )
        doRegisterEvent(
            mapOf(
                "eventType" to EventType.CALL_REST.dbValue,
                "restName" to RestName.MAIN.dbValue,
                "parameters" to emptyMap<String, Any>(),
            ),
            request,
        )
        return "main"
    }

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val data: MutableMap<String, Any> = mutableMapOf()
        author?.let {
            data["author"] = it
        }
        model.addAttribute("author_init", author ?: "")
        model.addAttribute("authors", Song.loadListAuthors(withSkiped = false, database = WORKING_DATABASE))
        // Публичная поверхность прода — показываем только готовые песни (specs/013-song-status-filter).
        model.addAttribute(
            "zakroma",
            Zakroma.getZakroma(
                author = author ?: "",
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                onlyPublished = true,
            ),
        )
        doRegisterEvent(
            mapOf("eventType" to EventType.CALL_REST.dbValue, "restName" to RestName.ZAKROMA.dbValue, "parameters" to data),
            request,
        )
        return "zakroma"
    }

//    @PostMapping("/registerevent")

    @PostMapping("/registerevent")
    @ResponseBody
    fun doRegisterEvent(
        @RequestParam(required = true) data: Map<String, Any>,
        request: HttpServletRequest,
        siteUserId: Long = 0,
    ): Boolean {
        println("Вызов registerevent $data")
        if (!data.containsKey("eventType")) return false
        val eventType = data["eventType"] as String
        val clientIp = ClientIpResolver.resolve(request)
        val userAgent = request.getHeader("User-Agent")
        val anonId = (data["anonId"] as? String)?.takeIf { it.isNotBlank() }

        fun insertEvent(fieldsValues: MutableList<Pair<String, Any>>): Boolean {
            // FR-006 (спека 274-events-batch-insert): формируем EventRecord и передаём
            // в EventsBuffer для batch INSERT. Kill-switch через
            // `karaoke.web.events.batch-enabled` (дефолт false — sync INSERT как раньше).
            // SQL-формирование идентично прежнему (escape через rightFileName/referer)
            // и инкапсулировано в EventsBuffer.buildInsertSql для единой точки контроля.
            val record = com.svoemesto.karaokeweb.services.EventsBuffer.EventRecord(
                fieldsValues = fieldsValues.toList(),
                eventType = eventType,
                clientIp = clientIp,
                userAgent = userAgent,
                anonId = anonId,
                siteUserId = siteUserId,
            )
            return eventsBuffer.add(record)
        }

        when (eventType) {
            EventType.CLICK_TO_LINK.dbValue -> {
                if (!data.containsKey("linkType")) return false
                val linkType = data["linkType"] as String
                when (linkType) {
                    LinkType.LINK_TO_SOCIAL_NETWORK.dbValue -> {
                        if (!data.containsKey("linkName")) return false
                        val linkName = data["linkName"] as String
                        println("Переход в соцсеть: $linkName")
                        return insertEvent(
                            mutableListOf(
                                Pair("event_type", EventType.CLICK_TO_LINK.dbValue),
                                Pair("link_type", LinkType.LINK_TO_SOCIAL_NETWORK.dbValue),
                                Pair("link_name", linkName),
                            ),
                        )
                    }
                    LinkType.LINK_TO_SONG.dbValue -> {
                        if (!data.containsKey("linkName")) return false
                        val linkName = data["linkName"] as String
                        if (!data.containsKey("songId")) return false
                        val songId = (data["songId"] as String).toLong()
                        if (!data.containsKey("songVersion")) return false
                        val songVersion = data["songVersion"] as String
                        println("Переход на просмотр: сайт $linkName, id=$songId, Версия: $songVersion")
                        return insertEvent(
                            mutableListOf(
                                Pair("event_type", EventType.CLICK_TO_LINK.dbValue),
                                Pair("link_type", LinkType.LINK_TO_SONG.dbValue),
                                Pair("link_name", linkName),
                                Pair("song_id", songId),
                                Pair("song_version", songVersion),
                            ),
                        )
                    }
                    else -> {}
                }
            }
            EventType.PLAY.dbValue -> {
                if (!data.containsKey("songId")) return false
                val songId = (data["songId"] as String).toLong()
                if (!data.containsKey("songVersion")) return false
                val songVersion = data["songVersion"] as String
                println("Просмотр на странице: id=$songId, Версия: $songVersion")
                // Апсерт в персональную «Историю прослушиваний» (QW-13) — дополнительно к обычной
                return insertEvent(
                    mutableListOf(
                        Pair("event_type", EventType.PLAY.dbValue),
                        Pair("song_id", songId),
                        Pair("song_version", songVersion),
                    ),
                )
            }
            EventType.CALL_REST.dbValue -> {
                val restName = data["restName"] as String
                val parameters = data["parameters"] as Map<*, *>
                println("Вызван рест $restName с параметрами $parameters")
                // US3: sampling/dedup (FR-006, FR-007) — пропустить INSERT, если фильтр решил.
                // Endpoint всё равно возвращает 200 OK — клиент не замечает sampling.
                if (samplingFilter.shouldSkip(restName, parameters, siteUserId, anonId)) {
                    return true
                }
                val fieldsValues: MutableList<Pair<String, Any>> =
                    mutableListOf(
                        Pair("event_type", EventType.CALL_REST.dbValue),
                        Pair("rest_name", restName),
                        Pair("rest_parameters", parameters.toString()),
                    )
                // referer теперь несёт настоящий внешний источник перехода (document.referrer
                // заход-лендинга, кросс-домен — см. karaoke-public/services/entryReferrer.js). Пишем
                // его, только если пришёл непустым (внутренние SPA-переходы его не несут). Больше НЕ
                // дублируем сюда clientIp — реальный IP и так лежит в client_ip.
                (data["referrer"] as? String)?.takeIf { it.isNotBlank() }?.let { fieldsValues.add(Pair("referer", it)) }
                if (parameters.containsKey("id")) fieldsValues.add(Pair("song_id", parameters["id"]!!.toString().toLong()))
                return insertEvent(fieldsValues)
            }
            EventType.PLAYER.dbValue -> {
                if (!data.containsKey("linkType") || !data.containsKey("songId")) return false
                val linkType = data["linkType"] as String // PlayerAction.dbValue: open|play|pause|seek|export
                val songId = (data["songId"] as String).toLong()
                val fieldsValues: MutableList<Pair<String, Any>> =
                    mutableListOf(
                        Pair("event_type", EventType.PLAYER.dbValue),
                        Pair("link_type", linkType),
                        Pair("song_id", songId),
                    )
                // link_name — деталь действия: ключ стема при export, позиция в секундах при seek,
                // процент-веха при progress
                (data["linkName"] as? String)?.let { fieldsValues.add(Pair("link_name", it)) }
                // Реальный запуск онлайн-плеера (не legacy EventType.PLAY — тот только для внешних
                // VK-ссылок на странице песни). Запись в tbl_listening_history — доп. к tbl_events,
                // не вместо неё (tbl_events регулярно опустошается на PROD через sync, непригодна
                // как персистентный источник личной истории — см.
                // specs/009-listening-history/research.md Decision 1/4). Только для залогиненных.
                if (siteUserId > 0 && linkType == PlayerAction.PLAY.dbValue) {
                    ListeningHistory.upsert(siteUserId, songId, WORKING_DATABASE)
                }
                return insertEvent(fieldsValues)
            }
            EventType.ENGAGEMENT.dbValue -> {
                // Время на странице: rest_name = идентификатор страницы, link_name = секунды видимости
                val fieldsValues: MutableList<Pair<String, Any>> =
                    mutableListOf(
                        Pair("event_type", EventType.ENGAGEMENT.dbValue),
                    )
                (data["page"] as? String)?.let { fieldsValues.add(Pair("rest_name", it)) }
                (data["linkName"] as? String)?.let { fieldsValues.add(Pair("link_name", it)) }
                (data["songId"] as? String)?.toLongOrNull()?.let { fieldsValues.add(Pair("song_id", it)) }
                return insertEvent(fieldsValues)
            }
            EventType.UI.dbValue -> {
                // UI-действие: link_type = navigate|theme|scroll, link_name = деталь (маршрут/тема/процент)
                val fieldsValues: MutableList<Pair<String, Any>> =
                    mutableListOf(
                        Pair("event_type", EventType.UI.dbValue),
                    )
                (data["linkType"] as? String)?.let { fieldsValues.add(Pair("link_type", it)) }
                (data["linkName"] as? String)?.let { fieldsValues.add(Pair("link_name", it)) }
                (data["songId"] as? String)?.toLongOrNull()?.let { fieldsValues.add(Pair("song_id", it)) }
                return insertEvent(fieldsValues)
            }
            else -> {}
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    @PostMapping("/changerecords")
    @ResponseBody
    fun doChangeRecords(
        @RequestBody(required = true) data: Map<String, Any>,
    ): String {
        var result = "OK"
        try {
            val word = data["word"] as String
            if (Crypto.decrypt(word) != Crypto.WORDS_TO_CHECK) return "Не удалось расшифровать кодовое слово"

            val dataCreate = data["dataCreate"] as List<Map<String, Any>>
            val dataUpdate = data["dataUpdate"] as List<Map<String, Any>>
            val dataDelete = data["dataDelete"] as List<Map<String, Any>>

            val connection = WORKING_DATABASE.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
                return "ERROR: Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}"
            }
            // Флаг «доступна для новости» (specs/101-song-news-flag, FR-004 spec.md) — для каждой
            // затронутой строки tbl_songs запоминаем значение ДО применения изменения (для dataUpdate
            // — точечный SELECT перед UPDATE; для dataCreate — «до» считается false по определению
            // новой строки, см. contracts/news-lifecycle.md п.2). Детекция перехода выполняется после
            // применения всего батча.
            val songAvailabilityBefore = mutableListOf<Pair<Long, Boolean>>()

            dataCreate.forEach { action ->
                val sqlToInsert = action["sqlToInsert"] as String
                val sqlToInsertDecrypted = Crypto.decrypt(sqlToInsert)
                val ps = connection.prepareStatement(sqlToInsertDecrypted)
                ps.executeUpdate()
                ps.close()
                // Редкий путь (обычный пайплайн доводит песню до готовности через несколько
                // отдельных UPDATE, не одним INSERT) — но должен быть покрыт: id всегда идёт первым
                // значением после VALUES( при явной вставке (см. KaraokeDbTable.getSqlToInsert,
                // OVERRIDING SYSTEM VALUE).
                if (sqlToInsertDecrypted != null && sqlToInsertDecrypted.trimStart().startsWith("INSERT INTO ${Song.TABLE_NAME} ")) {
                    Regex("""VALUES\((\d+)""").find(sqlToInsertDecrypted)?.groupValues?.get(1)?.toLongOrNull()?.let { newSongId ->
                        songAvailabilityBefore.add(newSongId to false)
                    }
                }
            }

            dataUpdate.forEach { action ->
                val tableName = action["tableName"] as String
                val idRecord = action["idRecord"] as Int
                val setText = action["setText"] as String
                val setTextDecrypted = Crypto.decrypt(setText)
                if (tableName == Song.TABLE_NAME) {
                    songAvailabilityBefore.add(idRecord.toLong() to Song.readNewsAvailableFlag(idRecord.toLong(), WORKING_DATABASE))
                }
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

            result =
                "[${Timestamp.from(Instant.now())}] Created: ${dataCreate.size}, Updated: ${dataUpdate.size}, Deleted: ${dataDelete.size}"

            // Новость «песня появилась в коллекции» (specs/101-song-news-flag) — единственная точка
            // кода, создающая этот вид новости: детекция перехода false→true для каждой затронутой в
            // этом батче песни (FR-004 spec.md). Новость «песня вышла в эфир» синхронизацией больше
            // НЕ создаётся (FR-007 spec.md) — только SongReleaseAnnouncementScheduler или вручную.
            // Обёрнуто отдельно: сбой детекции не должен ронять уже успешно применённую синхронизацию
            // и не должен менять формат ответа выше.
            try {
                songAvailabilityBefore.forEach { (songId, wasAvailableBefore) ->
                    SongReleaseAnnouncementService.detectAndAnnounceAvailability(
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                        songId = songId,
                        wasAvailableBefore = wasAvailableBefore,
                    )
                }
            } catch (e: Exception) {
                println("[${Timestamp.from(Instant.now())}] SongReleaseAnnouncementService.detectAndAnnounceAvailability error: ${e.message}")
            }
        } catch (e: Exception) {
            return e.message!!
        }
        return result
    }

    @GetMapping("/filter")
    fun filter(
        @RequestParam(required = false) songName: String?,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) text: String?,
        @RequestParam(required = false) album: String?,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val attr: MutableMap<String, String> = mutableMapOf()
        if (songName != null && songName != "") attr["song_name"] = songName
        if (author != null && author != "") attr["author"] = author
        if (text != null && text != "") attr["text"] = text
        if (album != null && album != "") attr["song_album"] = album
        // Публичная поверхность прода — показываем только готовые песни (specs/013-song-status-filter,
        // specs/022-song-status-lifecycle).
        attr["id_status"] = ">=6"

        val song: List<Song> =
            if ("${songName ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length <
                3
            ) {
                emptyList()
            } else {
                Song.loadListFromDb(
                    attr,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            }

        model.addAttribute("authors", Song.loadListAuthors(withSkiped = false, database = WORKING_DATABASE))
        model.addAttribute("song", song)

        val data: MutableMap<String, Any> = mutableMapOf()
        if (songName != null && songName != "") data["song_name"] = songName
        if (author != null && author != "") data["author"] = author
        if (text != null && text != "") data["text"] = text
        if (album != null && album != "") data["album"] = album
        doRegisterEvent(
            mapOf("eventType" to EventType.CALL_REST.dbValue, "restName" to RestName.FILTER.dbValue, "parameters" to data),
            request,
        )

        return "filter"
    }

    @GetMapping("/song")
    fun song(
        @RequestParam(required = true) id: Long,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
//        song?.let {
//            if (!song.haveVkGroupLink) {
//                val pathToPictureVkGroupLink = "/home/Karaoke/webpictures/${song.id}.png"
//                val filePictureVkGroupLink = File(pathToPictureVkGroupLink)
//                if (!filePictureVkGroupLink.exists()) {
//                    createVKLinkPicture(song, pathToPictureVkGroupLink)
//                }
//            }
//        }
        model.addAttribute("song", song)
        doRegisterEvent(
            mapOf(
                "eventType" to EventType.CALL_REST.dbValue,
                "restName" to RestName.SONG.dbValue,
                "parameters" to mapOf("id" to id),
            ),
            request,
        )
        if (song
                ?.tags
                ?.split(" ")
                ?.map { it.uppercase() }
                ?.contains("SKIP") == true
        ) {
            return "song-removed"
        }
        return "song"
    }

    /**
     * Thymeleaf-страница `/statbysong` — топ песен по числу событий в `tbl_events`.
     *
     * До FR-007 (Pass 241) использовался `limit = 100_000` — минутный запрос на полную таблицу
     * с 17 условными `count(*) filter (...)`. После FR-007 — `limit = 1000` (FR-001 спеки
     * [specs/272-statbysong-pagination](../../specs/272-statbysong-pagination/spec.md)),
     * SQL сам clamp'ит через safety-guard `coerceIn(MIN_STAT_BY_SONG_LIMIT=1,
     * MAX_STAT_BY_SONG_LIMIT=1000)` в `StatsByEvents.getStatBySong` (FR-002).
     *
     * Полная выгрузка (>1000 песен) — через REST API `/api/stats/by-song?page=N&pageSize=50`
     * с пагинацией. В UI под заголовком — баннер с totalCount и ссылкой на REST API
     * (FR-003, FR-004).
     *
     * @see specs/272-statbysong-pagination FR-001..FR-005
     * @see specs/241-db-storage-perf-audit FR-007 (Tier-3 / H-10)
     */
    @GetMapping("/statbysong")
    fun doStatBySong(model: Model): String {
        // FR-001: limit 1000 вместо 100_000 — UI top-1000, полные данные через /api/stats/by-song.
        model.addAttribute(
            "stats",
            com.svoemesto.karaokeapp.model.StatsByEvents
                .getStatBySong(database = WORKING_DATABASE, limit = 1000),
        )
        // FR-004: totalCount для баннера «Показано топ-1000 из ~N доступных».
        model.addAttribute(
            "totalCount",
            com.svoemesto.karaokeapp.model.StatsByEvents
                .getStatBySongCount(database = WORKING_DATABASE),
        )
        return "statbysong"
    }

    @GetMapping("/webevents")
    fun doWebEvents(model: Model): String {
        model.addAttribute(
            "webevents",
            com.svoemesto.karaokeapp.model.StatsByEvents.getWebEvents(
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ),
        )
        return "webevents"
    }

    @GetMapping("/testpage/{id}")
    fun doTestPage(
        @PathVariable id: Long,
        model: Model,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        model.addAttribute("song", song)
        return "testpage"
    }

//
//    @PostMapping("/storage/upload")
//    fun uploadFile(
//        @RequestParam("file") file: MultipartFile,
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName", required = false) fileName: String? = null
//    ): ResponseEntity<String> {
//        logger.info("Received upload request for bucket: $bucketName, file: ${file.originalFilename}")
//
//        if (file.isEmpty) {
//            logger.warn("Upload failed: file is empty")
//            return ResponseEntity.badRequest().body("File is empty")
//        }
//
//        val actualFileName = fileName ?: file.originalFilename ?: throw IllegalArgumentException("File name is required")
//
//        if (!isValidFileName(actualFileName)) {
//            logger.warn("Invalid file name: $actualFileName")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            logger.warn("Bucket does not exist: $bucketName")
//            return ResponseEntity.badRequest().body("Bucket does not exist: $bucketName")
//        }
//
//        val inputStream = file.inputStream
//        val size = file.size
//
//        try {
//            karaokeStorageService.uploadFile(bucketName, actualFileName, inputStream, size)
//            logger.info("File uploaded successfully: $actualFileName to bucket: $bucketName")
//            return ResponseEntity.ok("File uploaded successfully: $actualFileName")
//        } catch (e: Exception) {
//            logger.error("Upload failed for file: $actualFileName in bucket: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Upload failed: ${e.message}")
//        }
//    }
//
//    @GetMapping("/storage/url")
//    fun getFileUrl(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        val url = karaokeStorageService.getFileUrl(bucketName, fileName)
//        logger.info("URL requested for file: $fileName in bucket: $bucketName")
//        return ResponseEntity.ok(url)
//    }
//
//    @GetMapping("/storage/presigned-url")
//    fun getPresignedUrl(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        @RequestParam("expiry", required = false, defaultValue = "604800") expiry: Int,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        val url = karaokeStorageService.getPresignedUrl(bucketName, fileName, expiry)
//        logger.info("Presigned URL generated for file: $fileName in bucket: $bucketName")
//        return ResponseEntity.ok(url)
//    }
//
//    @GetMapping("/storage/download")
//    fun downloadFile(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<ByteArray> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().build()
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            val inputStream = karaokeStorageService.downloadFile(bucketName, fileName)
//            val bytes = inputStream.readAllBytes()
//
//            logger.info("File downloaded: $fileName from bucket: $bucketName")
//
//            return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
//                .body(bytes)
//        } catch (e: Exception) {
//            logger.error("Download failed for file: $fileName in bucket: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
//        }
//    }
//
//    @DeleteMapping("/storage/delete")
//    fun deleteFile(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<String> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().body("Invalid file name")
//        }
//
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            karaokeStorageService.deleteFile(bucketName, fileName)
//            logger.info("File deleted: $fileName from bucket: $bucketName")
//            return ResponseEntity.ok("File deleted successfully: $fileName")
//        } catch (e: Exception) {
//            logger.error("Deletion failed for file: $fileName in bucket: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Deletion failed: ${e.message}")
//        }
//    }
//
//    @GetMapping("/storage/list")
//    fun listFiles(
//        @RequestParam("bucketName") bucketName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<List<String>> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            logger.info("Bucket not found: $bucketName")
//            return ResponseEntity.notFound().build()
//        }
//
//        logger.info("Listing files in bucket: $bucketName from IP: ${request.remoteAddr}")
//
//        val files = karaokeStorageService.listFiles(bucketName)
//        return ResponseEntity.ok(files)
//    }
//
//    @GetMapping("/storage/exists")
//    fun checkIfExists(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): ResponseEntity<Map<String, Boolean>> {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return ResponseEntity.badRequest().build()
//        }
//
//        val exists = karaokeStorageService.fileExists(bucketName, fileName)
//        logger.info("Check exists: file=$fileName, bucket=$bucketName, exists=$exists")
//        return ResponseEntity.ok(mapOf("exists" to exists))
//    }
//
//    @PutMapping("/storage/bucket/public")
//    fun setBucketPublic(
//        @RequestParam("bucketName") bucketName: String
//    ): ResponseEntity<String> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            karaokeStorageService.setBucketPublic(bucketName)
//            logger.info("Bucket set to public: $bucketName")
//            return ResponseEntity.ok("Bucket '$bucketName' is now public")
//        } catch (e: Exception) {
//            logger.error("Failed to set bucket as public: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Failed to set bucket as public: ${e.message}")
//        }
//    }
//
//    @PutMapping("/storage/bucket/private")
//    fun setBucketPrivate(
//        @RequestParam("bucketName") bucketName: String
//    ): ResponseEntity<String> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            return ResponseEntity.notFound().build()
//        }
//
//        try {
//            karaokeStorageService.setBucketPrivate(bucketName)
//            logger.info("Bucket set to private: $bucketName")
//            return ResponseEntity.ok("Bucket '$bucketName' is now private")
//        } catch (e: Exception) {
//            logger.error("Failed to set bucket as private: $bucketName", e)
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body("Failed to set bucket as private: ${e.message}")
//        }
//    }
//
//    @GetMapping("/storage/bucket/public-status")
//    fun isBucketPublic(
//        @RequestParam("bucketName") bucketName: String
//    ): ResponseEntity<Map<String, Boolean>> {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            return ResponseEntity.notFound().build()
//        }
//
//        val isPublic = karaokeStorageService.isBucketPublic(bucketName)
//        logger.info("Bucket public status checked: $bucketName -> $isPublic")
//        return ResponseEntity.ok(mapOf("isPublic" to isPublic))
//    }
//
//    @PostMapping("/storage/fileStat")
//    @ResponseBody
//    fun getFileStat(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): StatObjectResponse? {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return null
//        }
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return null
//        }
//        return karaokeStorageService.getFileStat(bucketName, fileName)
//    }
//
//    @PostMapping("/storage/fileInfo")
//    @ResponseBody
//    fun getFileInfo(
//        @RequestParam("bucketName") bucketName: String,
//        @RequestParam("fileName") fileName: String,
//        request: HttpServletRequest
//    ): StorageFileInfo? {
//        if (!isValidFileName(fileName)) {
//            logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
//            return null
//        }
//        if (!karaokeStorageService.fileExists(bucketName, fileName)) {
//            logger.info("File not found: $fileName in bucket: $bucketName")
//            return null
//        }
//        return karaokeStorageService.getFileInfo(bucketName, fileName)
//    }
//
//
//    @PostMapping("/storage/listInfo")
//    @ResponseBody
//    fun listFilesInfo(
//        @RequestParam("bucketName") bucketName: String,
//        request: HttpServletRequest
//    ): List<StorageFileInfo>? {
//        if (!karaokeStorageService.bucketExists(bucketName)) {
//            logger.info("Bucket not found: $bucketName")
//            return null
//        }
//        return karaokeStorageService.listFilesInfo(bucketName)
//    }
}

package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.Author
import com.svoemesto.karaokeapp.model.EventType
import com.svoemesto.karaokeapp.model.Pictures
import com.svoemesto.karaokeapp.model.RestName
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.resizeBufferedImage
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.StatBySong
import com.svoemesto.karaokeweb.dto.AuthorTilePublicDto
import com.svoemesto.karaokeweb.dto.SongPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaAlbumMetaPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaAlbumSongPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaPublicDto
import com.svoemesto.karaokeweb.dto.ZakromaStreamMessageDto
import com.svoemesto.karaokeweb.dto.ZakromaStreamMetricDto
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

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

    // specs/017-editor-status-bypass: анонимные/обычные посетители продолжают видеть только
    // готовые песни (id_status >= 6, specs/013-song-status-filter, specs/022-song-status-lifecycle);
    // для "редактора" (SiteUser
    // .isEditor) фильтр по статусу снимается целиком — поэтому здесь и в местах вызова
    // проверяем именно "!= true", а не "== false": невалидный/отсутствующий токен, как и явный
    // isEditor=false, должны попадать в ветку "фильтр действует", а не в исключение.
    private fun onlyPublishedFor(request: HttpServletRequest): Boolean = siteUserResolver.resolve(request)?.isEditor != true

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
            "freeNow" to StatBySong.getCountSongsFreeNow(database = WORKING_DATABASE),
            "subscriptionOnly" to StatBySong.getCountSongsSubscriptionOnly(database = WORKING_DATABASE),
            "inWork" to StatBySong.getCountSongsInWork(database = WORKING_DATABASE),
            "total" to StatBySong.getCountSongsTotal(database = WORKING_DATABASE),
        )
    }

    @GetMapping("/authors")
    fun authors(
        @RequestParam(required = false, defaultValue = "main") scope: String?,
    ): List<String> {
        val isSpecialOrderFilter: Boolean? =
            when (scope) {
                "special" -> true
                "main" -> false
                "all" -> null
                else -> false
            }
        return Song.loadListAuthors(
            withSkiped = false,
            isSpecialOrder = isSpecialOrderFilter,
            database = WORKING_DATABASE,
        )
    }

    @GetMapping("/authors-tiles")
    fun authorsTiles(
        @RequestParam(required = false, defaultValue = "main") scope: String?,
        request: HttpServletRequest,
    ): List<AuthorTilePublicDto> {
        val isSpecialOrderFilter: Boolean? =
            when (scope) {
                "special" -> true
                "main" -> false
                "all" -> null
                else -> false
            }
        // Публичная поверхность прода — считаем и показываем только готовые песни
        // (specs/013-song-status-filter): плашка автора без готовых песен не отображается,
        // подпись плашки считает только их. Кроме "редактора" — для него фильтр по статусу снят,
        // подпись отражает полное количество песен автора (specs/017-editor-status-bypass).
        val counts =
            Song.loadAuthorSongCounts(
                isSpecialOrder = isSpecialOrderFilter,
                onlyPublished = onlyPublishedFor(request),
                database = WORKING_DATABASE,
            )
        val loadedAuthors: List<String> =
            Song
                .loadListAuthors(
                    withSkiped = false,
                    isSpecialOrder = isSpecialOrderFilter,
                    database = WORKING_DATABASE,
                ).filter { (counts[it] ?: 0L) > 0L }
        val specialFlags: Map<String, Boolean> =
            loadedAuthors.associateWith {
                it in counts.keys && (isSpecialOrderFilter ?: false)
            }
        return loadedAuthors.map {
            AuthorTilePublicDto.fromAuthorName(
                author = it,
                songCount = counts[it] ?: 0L,
                isSpecialOrder = it in specialFlags && specialFlags[it] == true,
            )
        }
    }

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false, defaultValue = "false") specialBucket: Boolean,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): List<ZakromaPublicDto> {
        val data: MutableMap<String, Any> = mutableMapOf()
        author?.let { data["author"] = it }
        if (specialBucket) data["specialBucket"] = true
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
        // specialBucket=true — виртуальная плашка «Отдельные песни разных авторов»: все
        // is_special_order=true авторы одним запросом, вместо N+1 по каждому автору отдельно.
        // @see docs/features/special-orders.md
        // Публичная поверхность прода — показываем только готовые песни (specs/013-song-status-filter),
        // кроме "редактора" — для него фильтр по статусу снят (specs/017-editor-status-bypass).
        val onlyPublished = onlyPublishedFor(request)
        val zakroma =
            if (specialBucket) {
                Zakroma.getZakromaBySpecialOrder(
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    onlyPublished = onlyPublished,
                )
            } else {
                Zakroma.getZakroma(
                    author = author ?: "",
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    onlyPublished = onlyPublished,
                )
            }
        return ZakromaPublicDto.fromZakroma(zakroma)
    }

    /**
     * NDJSON chunked-stream версия /zakroma для real-time прогресса на фронте.
     *
     * Отдаёт 5 типов сообщений (FR-BE-003): `meta` (1 шт.) → `album` (N шт.) →
     * `song` (M шт.) → `done` (1 шт.). Альбомы и песни итерируются в
     * порядке их получения из [Zakroma.getZakroma], что совпадает с порядком
     * отображения на странице (TOCTOU-консистентность).
     *
     * **Прогресс-контракт**: `expectedCount` в `meta` MUST совпадать с числом
     * на тайле автора (`Song.loadAuthorSongCounts(author, onlyPublished)`) —
     * иначе фронт покажет «дрейф» счётчика (получили X из 230, а на тайле 234).
     *
     * **Ошибки**: при SQLException шлём `{"type":"error",...}` + close,
     * HTTP 200 (НЕ 500 — иначе fetch не сможет парсить тело, см. FR-BE-006).
     *
     * **Prod-конфиг**: файлу `/etc/nginx/sites-enabled/80to8897` MUST быть
     * добавлен location-блок с `proxy_buffering off; gzip off; proxy_read_timeout 300s;`
     * (см. `deploy/80to8897.stream-addition.frag` + `tools/deploy-nginx-stream.sh`).
     * Без этой правки nginx буферизует chunked-ответ и фронт получит весь
     * массив одним блоком — никакого «real-time» не будет.
     *
     * @see docs/features/zakroma-stream-progress.md
     */
    @GetMapping("/zakroma/stream", produces = ["application/x-ndjson"])
    fun zakromaStream(
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) expectedCount: Long?,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): ResponseEntity<StreamingResponseBody> {
        val data: MutableMap<String, Any> = mutableMapOf()
        author?.let { data["author"] = it }
        data["stream"] = true
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
        val onlyPublished = onlyPublishedFor(request)
        val auth = author ?: ""

        val body =
            StreamingResponseBody { out ->
                val writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
                val mapper = ObjectMapper()
                try {
                    // 1. meta — отправляем ДО загрузки данных, чтобы фронт сразу
                    //    узнал expectedCount.
                    //
                    // Стратегия выбора источника:
                    // - Если фронт прислал `expectedCount > 0` (т.е. счётчик
                    //   был на тайле `AuthorTilePublicDto.songCount` к моменту
                    //   клика) — TRUST его (та же формула `Song.loadAuthorSongCounts`).
                    //   Saves 100-500мс DB-запроса.
                    // - Иначе (null/0/missing) — FALLBACK на `Song.loadAuthorSongCounts()`.
                    //   Это MUST для deep-link URL `/zakroma?author=...` — тайлы
                    //   могут быть НЕ загружены к моменту `mounted()`, фронт
                    //   ещё в процессе fetching `authors-tiles`. Без fallback
                    //   метрика «0 из 0» (user видит после моего предыдущего
                    //   fix'а 181/243).
                    //
                    // FR-BE-008 (sanity check): backend всё равно отдаёт
                    // `done.actualCount` — frontend может сверить с
                    // мета.expectedCount (для drift detection).
                    val metaExpectedCount: Long =
                        if (expectedCount != null && expectedCount > 0) {
                            expectedCount
                        } else {
                            Song.loadAuthorSongCounts(
                                isSpecialOrder = null,
                                onlyPublished = onlyPublished,
                                database = WORKING_DATABASE,
                            )[auth] ?: 0L
                        }
                    writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.meta(auth, metaExpectedCount)))
                    writer.newLine()
                    writer.flush()
                    // Явный out.flush() — гарантирует, что servlet-буфер
                    // (Tomcat 8KB default) отправит байты клиенту СРАЗУ, а не
                    // будет держать их до заполнения буфера. writer.flush() уже
                    // должен вызвать out.flush() через OutputStreamWriter, но
                    // Tomcat-уровневая буферизация делает явный вызов
                    // безопаснее.
                    out.flush()

                    // 2. Загрузка данных (тот же код, что в обычном /zakroma).
                    val zakroma =
                        Zakroma.getZakroma(
                            author = auth,
                            database = WORKING_DATABASE,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                            onlyPublished = onlyPublished,
                        )

                    // 3. Streaming loop по альбомам и песням (FR-BE-004).
                    //    Полная реализация добавляется в T012 — здесь
                    //    передаём по одному album + song с flush после каждого.
                    var actualCount = 0L
                    for (zak in zakroma) {
                        for (album in zak.albums.sorted()) {
                            // album message — метаданные (без albumSettings, FR-BE-003).
                            writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.album(ZakromaAlbumMetaPublicDto.fromAlbum(album))))
                            writer.newLine()
                            writer.flush()
                            out.flush()
                            for (song in album.albumSongs) {
                                writer.write(
                                    mapper.writeValueAsString(
                                        ZakromaStreamMessageDto.song(
                                            ZakromaAlbumSongPublicDto(
                                                id = song.id,
                                                track = song.track,
                                                songName = song.songName,
                                                onAir = song.onAir,
                                                datePublish = song.datePublish,
                                                airTimestamp = song.airTimestamp,
                                                songSubscriptionAvailable = song.songSubscriptionAvailable,
                                                alwaysFree = song.alwaysFree,
                                                freelyAvailableNow = song.freelyAvailableNow,
                                                freeAccessWindowEndText = song.freeAccessWindowEndText,
                                                linkBoosty = song.linkBoosty,
                                                linkSponsrPlay = song.linkSponsrPlay,
                                                linkDzenKaraoke = song.linkDzenKaraoke,
                                                linkDzenLyrics = song.linkDzenLyrics,
                                                linkDzenTabs = song.linkDzenTabs,
                                                linkDzenChords = song.linkDzenChords,
                                                linkVkKaraoke = song.linkVkKaraoke,
                                                linkVkLyrics = song.linkVkLyrics,
                                                linkVkTabs = song.linkVkTabs,
                                                linkVkChords = song.linkVkChords,
                                                linkTgKaraoke = song.linkTgKaraoke,
                                                linkTgLyrics = song.linkTgLyrics,
                                                linkTgTabs = song.linkTgTabs,
                                                linkTgChords = song.linkTgChords,
                                                linkPlKaraoke = song.linkPlKaraoke,
                                                linkPlLyrics = song.linkPlLyrics,
                                                linkPlTabs = song.linkPlTabs,
                                                linkPlChords = song.linkPlChords,
                                                linkMaxKaraoke = song.linkMaxKaraoke,
                                                linkMaxLyrics = song.linkMaxLyrics,
                                                linkMaxTabs = song.linkMaxTabs,
                                                linkMaxChords = song.linkMaxChords,
                                            ),
                                        ),
                                    ),
                                )
                                writer.newLine()
                                writer.flush()
                                out.flush()
                                actualCount++
                            }
                        }
                    }

                    // 4. done — финал (FR-BE-003: actualCount = реально отправленных песен,
                    //    FR-BE-008: должен совпадать с expectedCount, если фильтр не удалил).
                    writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.done(actualCount)))
                    writer.newLine()
                    writer.flush()
                    out.flush()
                } catch (e: Exception) {
                    // FR-BE-006: 200 + {"type":"error",...} при любой ошибке SQL/IO.
                    // НЕ отдаём 500 — иначе fetch не сможет прочитать тело.
                    try {
                        writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.error("Не удалось загрузить песни автора")))
                        writer.newLine()
                        writer.flush()
                        out.flush()
                    } catch (_: Exception) {
                        // Если даже error-сообщение не удалось записать — стрим уже
                        // сломан, ничего не поделать. Tomcat закроет соединение.
                    }
                } finally {
                    try {
                        writer.flush()
                        out.flush()
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }

        return ResponseEntity
            .ok()
            .contentType(MediaType("application", "x-ndjson"))
            .body(body)
    }

    /**
     * Принимает батч метрик NDJSON-стрима (FR-FE-010).
     *
     * Фронт при `pagehide` (через `navigator.sendBeacon`, fallback `fetch keepalive`)
     * шлёт массив [ZakromaStreamMetricDto] со всеми событиями стрима
     * (`zakroma_stream_start` / `_done` / `_error` / `_abort`). Каждое
     * регистрируется в `tbl_events` для последующего анализа (SC-004).
     *
     * **Возврат 200 даже при ошибках БД** — метрики не должны ломать UX
     * посетителя (страница уже закрывается через `pagehide`). Если БД
     * недоступна — событие просто теряется, это нормально (sampling loss).
     *
     * **Совместимо с `sendBeacon`**: `Content-Type`=`application/json`,
     * `Keep-Alive` через `keepalive: true` fallback.
     *
     * @see docs/features/zakroma-stream-progress.md
     */
    @PostMapping("/zakroma/stream/metrics")
    fun zakromaStreamMetrics(
        @RequestBody metrics: List<ZakromaStreamMetricDto>,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Unit> {
        if (metrics.isEmpty()) return ResponseEntity.ok().build()
        try {
            for (m in metrics) {
                // eventType берём из самого DTO (специализированный, НЕ CALL_REST),
                // чтобы админ-фильтры в tbl_events могли выделять stream-события
                // в отдельную категорию.
                val parameters: MutableMap<String, Any?> =
                    mutableMapOf(
                        "author" to m.author,
                        "firstChunkMs" to m.firstChunkMs,
                        "durationMs" to m.durationMs,
                        "expectedCount" to m.expectedCount,
                        "receivedCount" to m.receivedCount,
                        "streamAborted" to m.streamAborted,
                        "errorCategory" to m.errorCategory,
                    )
                mainController.doRegisterEvent(
                    mapOf(
                        "eventType" to m.eventType,
                        "restName" to RestName.ZAKROMA.dbValue,
                        "parameters" to parameters,
                        "anonId" to (anonId ?: ""),
                        "referrer" to (referrer ?: ""),
                    ),
                    request,
                    siteUserResolver.resolve(request)?.id ?: 0,
                )
            }
        } catch (e: Exception) {
            // Не падаем — метрики best-effort.
            println("zakromaStreamMetrics error: ${e.message}")
        }
        return ResponseEntity.ok().build()
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
    ): List<SongPublicDto> {
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
                attr["author_in"] = matches.joinToString(Song.AUTHOR_IN_DELIMITER) { it.author }
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
        // Публичная поверхность прода — показываем только готовые песни (specs/013-song-status-filter),
        // кроме "редактора" — для него фильтр по статусу снят (specs/017-editor-status-bypass).
        if (onlyPublishedFor(request)) attr["id_status"] = ">=6"

        val song: List<Song> =
            if ("${songName ?: ""}${author ?: ""}${album ?: ""}${text ?: ""}".length < 3) {
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

        return song.map {
            val dto = SongPublicDto.fromSong(it, includeDetails = false)
            dto.copy(authorAlias = aliasByAuthor[dto.author.lowercase()] ?: "")
        }
    }

    @GetMapping("/song/{id}")
    fun song(
        @PathVariable id: Long,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false) referrer: String?,
        request: HttpServletRequest,
    ): SongPublicDto? {
        val sett =
            Song.loadFromDbById(
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
        // FR-008 (self-assign): для self-assign-редакторов — батч-перетяжка активного задания
        // ровно по ЭТОЙ песне. Если песни нет или user не self-assign-editor — assignment:null.
        val me = siteUserResolver.resolve(request)
        val isSelfAssignEditor = me?.isEditor == true && me.canSelfAssignTasks
        val assignmentDto =
            if (isSelfAssignEditor && sett != null) {
                SongAssignment
                    .loadBySongIds(
                        listOf(sett.id),
                        WORKING_DATABASE,
                        storageService,
                        storageApiClient,
                    )[sett.id]
                    ?.let { a ->
                        com.svoemesto.karaokeapp.model.SongAssignmentBriefDto(
                            id = a.id,
                            assigneeId = a.assigneeId,
                            assignedAt = a.assignedAt,
                            adminStatus = a.adminStatus,
                        )
                    }
            } else {
                null
            }
        return sett?.let { SongPublicDto.fromSong(it).copy(assignment = assignmentDto) }
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

        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: return ResponseEntity.notFound().build()

        val albumPicName = "${song.author} - ${song.year} - ${song.album}"
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
                song.author,
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
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: return ResponseEntity.notFound().build()

        val cacheFile = File("/tmp/vk_$id.png")
        val bucket = "karaoke"
        val albumPicName = "${song.author} - ${song.year} - ${song.album}"
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
                song.author,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
                ignoreUseInList = false,
            )

        val albumFilePath = "${song.author}/${song.year} - ${song.album}/$albumPicName.album.png"
        val authorFilePath = "${song.author}/${song.author}.author.png"

        if (cacheFile.exists()) {
            val cached = cacheFile.readBytes()
            // specs/130-vk-preview-generation: защита от чтения частично записанного/повреждённого
            // кэша. Проверяем PNG magic-signature (8 байт) — дешёвая и достаточная защита,
            // поскольку генерация теперь атомарна (см. ниже) и валидный файл не может иметь
            // мусор в первых 8 байтах.
            if (cached.isNotEmpty() && isPngSignature(cached)) {
                return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(cached)
            }
            // Повреждённый или пустой кэш — удаляем и регенерируем.
            cacheFile.delete()
        }

        val albumBytes = fetchFromMinIO(albumFilePath)
        val authorBytes = fetchFromMinIO(authorFilePath)

        if (albumBytes == null || authorBytes == null) {
            return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/KARAOKE_LOGO.png")
                .build()
        }

        val frameW = 1200
        val frameH = 630
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
        val songText = song.songName
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
        writeAtomically(cacheFile, bytes)

        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
    }

    /**
     * Атомарная запись PNG в [target]: пишем во временный файл в той же директории и затем
     * [Files.move] с [StandardCopyOption.ATOMIC_MOVE]. На файловых системах без поддержки
     * `ATOMIC_MOVE` (например, некоторые сетевые FS) — fallback на обычную замену
     * `REPLACE_EXISTING`. Параллельный читатель увидит либо старый файл, либо новый,
     * но не частично записанный (specs/130-vk-preview-generation).
     */
    private fun writeAtomically(
        target: File,
        bytes: ByteArray,
    ) {
        val tempFile = File(target.parentFile, "${target.name}.tmp.${System.nanoTime()}")
        try {
            tempFile.writeBytes(bytes)
            try {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /** Проверяет PNG magic signature (8 байт: 89 50 4E 47 0D 0A 1A 0A). */
    private fun isPngSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
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

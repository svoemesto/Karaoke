package com.svoemesto.karaokeapp.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.llm.LyricsFinderService
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.SongReleaseAnnouncementService
import com.svoemesto.karaokeapp.services.SseNotificationService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.WVP
import com.svoemesto.karaokeapp.sync.SyncDirection
import com.svoemesto.karaokeapp.sync.SyncOperation
import com.svoemesto.karaokeapp.sync.SyncRegistry
import com.svoemesto.karaokeapp.sync.SyncTarget
import com.svoemesto.karaokeapp.sync.isAllowed
import com.svoemesto.karaokeapp.sync.isOperationAllowed
import com.svoemesto.karaokeapp.sync.operationPropertyKey
import com.svoemesto.karaokeapp.textfiledictionary.SyncIdsDictionary
import com.svoemesto.karaokeapp.textfiledictionary.TextFileDictionary
import com.svoemesto.karaokeapp.textfilehistory.SongsHistory
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/**
 * DTO для family song: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class FamilySongDto(
    val id: Long,
    val songName: String,
    val author: String,
    val album: String,
    val year: Long,
    val diffSeconds: Long,
    val original: Boolean,
    val current: Boolean,
    val idStatus: Long,
)

/**
 * DTO для select family song result: сериализуемое представление для API/UI.
 *
 * Содержит фактически сохранённые значения после применения выбора похожей версии:
 * rootId и idStatus — существующие поля результата; audioParentId, audioSimilarityPercent
 * и audioDeltaMs — три аудиополя текущей песни, синхронизированные с выбранным кандидатом.
 * Поля аудио возвращаются как Long/Int после нормализации и перечитывания записи,
 * а не как эхо неподтверждённого запроса.
 *
 * @see docs/features/songs-table.md
 */
data class SelectFamilySongResultDto(
    val rootId: Long,
    val idStatus: Long,
    val audioParentId: Long,
    val audioSimilarityPercent: Int,
    val audioDeltaMs: Long,
)

/**
 * DTO кандидата картинки альбома (результат поиска обложки): сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class AlbumCoverCandidateDto(
    val url: String,
    val source: String,
)

/**
 * DTO результата поиска обложки альбома: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class AlbumCoverSearchResponseDto(
    val ok: Boolean,
    val message: String,
    val candidates: List<AlbumCoverCandidateDto>,
    val defaultQuery: String = "",
)

/**
 * DTO для find audio parent result: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class FindAudioParentResultDto(
    val audioParentId: Long,
    val audioSimilarityPercent: Int,
    val audioDeltaMs: Long,
    val matched: Boolean,
    val reason: String,
)

/**
 * DTO для sync entity info: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class SyncEntityInfoDto(
    val key: String,
    val displayName: String,
    val allowPush: Boolean,
    val allowPull: Boolean,
    val oneClickDirection: String,
    // Флаги операций per-direction (push = Local→Server, pull = Server→Local).
    val pushInsert: Boolean,
    val pushUpdate: Boolean,
    val pushDelete: Boolean,
    val pushMove: Boolean,
    val pullInsert: Boolean,
    val pullUpdate: Boolean,
    val pullDelete: Boolean,
    val pullMove: Boolean,
)

/**
 * DTO для sync run result: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class SyncRunResultDto(
    val created: List<String>,
    val updated: List<String>,
    val deleted: List<String>,
    val moved: List<String>,
)

/**
 * DTO для sync one click result: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class SyncOneClickResultDto(
    val key: String,
    val displayName: String,
    val direction: String,
    val skipped: Boolean,
    val created: List<String>,
    val updated: List<String>,
    val deleted: List<String>,
    val moved: List<String>,
)

/**
 * Контроллер (HTTP/WebSocket endpoints) для api .
 *
 * @see AGENTS.md
 */
@SuppressWarnings("SpellCheckingInspection")
@Controller
@RequestMapping("/api")
class ApiController(
    private val sseNotificationService: SseNotificationService,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val lyricsFinderService: LyricsFinderService,
    private val albumCoverService: AlbumCoverService,
) {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    // specs/082-fix-import-folder-oom: ограничивает конкурентность фонового поиска текста песни
    // (SearXNG) при массовом импорте из папки — без лимита doCreateFromFolder создавал бы
    // отдельный OS-поток на каждую новую песню без найденного текста одновременно.
    private val lyricsSearchExecutor: ExecutorService = Executors.newFixedThreadPool(4)

    @GetMapping("/diagnostics") // GET запрос на /api/diagnostics
    @ResponseBody
    fun getDiagnosticsInfo(): Map<String, Any> {
        // Ваши "вшитые" или ожидаемые пути
        val expectedPaths =
            listOf(
                "/sm-karaoke/work",
                "/sm-karaoke/done1",
                "/sm-karaoke/done2",
                "/sm-karaoke/system/demucs/input",
                "/sm-karaoke/system/demucs/output",
                // Добавьте сюда другие важные пути, которые использует ваше приложение
            )

        val info = mutableMapOf<String, Any>()

        // --- НОВОЕ: Проверка SSL/Сертификатов ---
        val sslInfo = mutableMapOf<String, Any>()
        try {
            // 1. Где Java ищет cacerts?
            val javaHome = System.getProperty("java.home")
            sslInfo["java.home"] = javaHome
            val cacertsPathStr = "$javaHome/lib/security/cacerts"
            sslInfo["expected.cacerts.path"] = cacertsPathStr

            val cacertsPath = Paths.get(cacertsPathStr)
            sslInfo["cacerts.path.exists"] = Files.exists(cacertsPath)
            sslInfo["cacerts.path.isReadable"] = Files.isReadable(cacertsPath)

            if (Files.exists(cacertsPath)) {
                // 2. Попробуем загрузить keystore и проверить наличие нашего сертификата
                try {
                    val keystore = KeyStore.getInstance(KeyStore.getDefaultType()) // Обычно "JKS"
                    Files.newInputStream(cacertsPath).use { fis ->
                        // "changeit" - стандартный пароль для cacerts
                        keystore.load(fis, "changeit".toCharArray())
                    }
                    sslInfo["keystore.load.success"] = true
                    sslInfo["keystore.type"] = keystore.type
                    sslInfo["keystore.size"] = keystore.size()

                    // 3. Проверим наличие нашего сертификата (замените на ваш alias)
                    val certAliasToCheck = "www.sm-karaoke.ru" // <-- ВАЖНО: Укажите правильный alias
                    val certFound = keystore.containsAlias(certAliasToCheck)
                    sslInfo["cert.$certAliasToCheck.found"] = certFound

                    if (certFound) {
                        val cert: Certificate = keystore.getCertificate(certAliasToCheck)
                        sslInfo["cert.$certAliasToCheck.type"] = cert.type ?: "Unknown"
                        // Можно добавить отпечаток, но это сложнее
                    }

                    // 4. Проверим пару стандартных сертификатов, чтобы убедиться, что keystore не пуст
                    sslInfo["cert.digicert.found"] = keystore.containsAlias("digicertglobalrootca") // Пример
                    sslInfo["cert.letsencrypt.found"] = keystore.containsAlias("letsencryptauthorityx3") // Пример
                } catch (ke: KeyStoreException) {
                    sslInfo["keystore.error"] = "KeyStoreException: ${ke.message}"
                } catch (ioe: IOException) {
                    sslInfo["keystore.error"] = "IOException (e.g., wrong password): ${ioe.message}"
                } catch (ce: CertificateException) {
                    sslInfo["keystore.error"] = "CertificateException: ${ce.message}"
                } catch (e: Exception) {
                    sslInfo["keystore.error"] = "Unexpected error loading keystore: ${e.message}"
                    sslInfo["keystore.error.type"] = e.javaClass.simpleName
                }
            } else {
                sslInfo["cacerts.path.error"] = "File does not exist"
            }
        } catch (e: Exception) {
            sslInfo["general.error"] = e.message.toString()
            sslInfo["general.error.type"] = e.javaClass.simpleName
        }
        info["ssl.keystore.check"] = sslInfo
        // --- КОНЕЦ НОВОГО ---

        // 1. Информация о системе и JVM
        info["java.version"] = System.getProperty("java.version")
        info["java.home"] = System.getProperty("java.home")
        info["user.dir (working directory)"] = System.getProperty("user.dir")
        info["user.name"] = System.getProperty("user.name")
        info["user.home"] = System.getProperty("user.home")
        info["os.name"] = System.getProperty("os.name")
        info["os.version"] = System.getProperty("os.version")

        // --- НОВОЕ: Информация о локалях и кодировке ---
        info["default.charset"] = Charset.defaultCharset().toString()
        info["file.encoding"] = Charset.defaultCharset().displayName()
        info["sun.jnu.encoding"] = System.getProperty("sun.jnu.encoding")
        info["user.language"] = System.getProperty("user.language")
        info["user.country"] = System.getProperty("user.country") ?: "Not Set"
        info["locale.default"] = Locale.getDefault().toString()
        // --- КОНЕЦ НОВОГО ---

        // 2. Переменные окружения
        val envVars = System.getenv()
        info["env.WORK_IN_CONTAINER"] = envVars["WORK_IN_CONTAINER"] ?: "Not Set"
        info["env.JAVA_HOME"] = envVars["JAVA_HOME"] ?: "Not Set"
        info["env.LANG"] = envVars["LANG"] ?: "Not Set" // НОВОЕ: Проверка LANG
        info["env.LC_ALL"] = envVars["LC_ALL"] ?: "Not Set" // НОВОЕ: Проверка LC_ALL

        // 3. Информация о файлах и путях (с обработкой ошибок)
        val pathsInfo = mutableMapOf<String, Map<String, Any>>()
        for (pathStr in expectedPaths) {
            val pathInfo = mutableMapOf<String, Any>()
            try {
                // --- НОВОЕ: Анализ самой строки пути ---
                pathInfo["string.length"] = pathStr.length
                pathInfo["string.bytes_utf8"] = pathStr.toByteArray(Charsets.UTF_8).contentToString()
                pathInfo["string.bytes_default"] = pathStr.toByteArray().contentToString()
                // --- КОНЕЦ НОВОГО ---

                val path: Path = Paths.get(pathStr) // Эта строка вызвала ошибку
                pathInfo["path_created_successfully"] = true

                // Остальная логика проверки пути...
                pathInfo["exists"] = Files.exists(path)
                pathInfo["isReadable"] = Files.isReadable(path)
                pathInfo["isWritable"] = Files.isWritable(path)
                pathInfo["isDirectory"] = Files.isDirectory(path)

                if (Files.exists(path) && Files.isDirectory(path)) {
                    val files =
                        try {
                            Files
                                .list(path)
                                .limit(10)
                                .map { it.fileName.toString() }
                                .toList()
                        } catch (e: Exception) {
                            listOf("Error listing files: ${e.message}")
                        }
                    pathInfo["first_10_files"] = files
                    pathInfo["total_files_approx"] =
                        try {
                            File(pathStr).list()?.size ?: "Unknown"
                        } catch (e: Exception) {
                            "Error counting: ${e.message}"
                        }
                } else if (Files.exists(path)) {
                    pathInfo["size_bytes"] =
                        try {
                            Files.size(path)
                        } catch (e: Exception) {
                            "Error getting size: ${e.message}"
                        }
                }
            } catch (ipe: InvalidPathException) {
                // --- НОВОЕ: Специальная обработка InvalidPathException ---
                pathInfo["path_created_successfully"] = false
                pathInfo["error.type"] = "InvalidPathException"
                pathInfo["error.message"] = ipe.message.toString()
                pathInfo["error.input"] = ipe.input
                pathInfo["error.index"] = ipe.index
                // --- КОНЕЦ НОВОГО ---
            } catch (e: Exception) {
                pathInfo["path_created_successfully"] = false
                pathInfo["error.type"] = e.javaClass.simpleName
                pathInfo["error.message"] = e.message ?: "No message"
            }
            pathsInfo[pathStr] = pathInfo
        }
        info["paths_check"] = pathsInfo

        // 4. Информация о Classpath
        info["classloader"] = this.javaClass.classLoader.toString()
        try {
            val protectionDomain = this.javaClass.protectionDomain
            val codeSource = protectionDomain.codeSource
            info["jar_location"] = codeSource?.location?.toString() ?: "Unknown or not from JAR"
        } catch (e: Exception) {
            info["jar_location_error"] = e.message.toString()
        }

        return info
    }

    @GetMapping("/cnt")
    @ResponseBody
    fun getCnt(): String {
        val song =
            Song.loadListFromDb(
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            )
        println("Вызван getCnt. Количество записей в в базе данных: ${song.size}")
        return "Количество записей в в базе данных: ${song.size}"
    }

    @GetMapping("/fls")
    @ResponseBody
    fun getFls(): String {
        val files = getListFiles("/sm-karaoke/work").joinToString(", ")
        println("Вызван getFls. Файлы в папке /sm-karaoke/work: $files")
        return "Вызван getFls. Файлы в папке /sm-karaoke/work: $files"
    }

    @GetMapping("/song/{id}/shortinfo")
    fun getSongShortInfo(
        @PathVariable id: Long,
    ): ResponseEntity<SongShortInfoDto> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            SongShortInfoDto(
                id = song.id,
                author = song.author,
                year = song.year,
                album = song.album,
                songName = song.songName,
            ),
        )
    }

    @GetMapping("/song/{id}/filedrums")
    fun getSongFileDrums(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            val filename = File(song.drumsNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filebass")
    fun getSongFileBass(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            val filename = File(song.bassNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filevoice")
    fun getSongFileVocal(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            val filename = File(song.vocalsNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/fileminus")
    fun getSongFileMusic(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            val filename = File(song.accompanimentNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filesong")
    fun getSongFileSong(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            val filename = File(song.fileAbsolutePath)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    // Получение списка id песен, изменившихся с указанного момента
    @PostMapping("/songs/changed")
    @ResponseBody
    fun getChangedSongsIds(
        @RequestParam time: Long,
    ): List<Long> {
        val result: MutableList<Long> = mutableListOf()

        val connection = WORKING_DATABASE.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
            return emptyList()
        }
        var rs: ResultSet? = null
        val sql = "select id from tbl_songs where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > ?;"
        val statement = connection.prepareStatement(sql)
        statement.setLong(1, time)
        try {
            rs = statement.executeQuery()
            while (rs.next()) {
                result.add(rs.getLong("id"))
            }
//            if (result.isNotEmpty()) {
//                println("time = $time, ids = $result");
//            }

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

    // Получение списка id процессов, изменившихся с указанного момента
    @PostMapping("/processes/changed")
    @ResponseBody
    fun getChangedProcessesIds(
        @RequestParam time: Long,
    ): List<Long> {
        val result: MutableList<Long> = mutableListOf()

        val connection = WORKING_DATABASE.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
            return emptyList()
        }
        var rs: ResultSet? = null
        val sql = "select id from tbl_processes where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > ?;"
        val statement = connection.prepareStatement(sql)
        statement.setLong(1, time)
        try {
            rs = statement.executeQuery()
            while (rs.next()) {
                result.add(rs.getLong("id"))
            }
//            if (result.isNotEmpty()) {
//                println("time = $time, ids = $result");
//            }

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

    // Копирование полей
    @PostMapping("/song/copyfieldsfromanother")
    @ResponseBody
    fun copyFieldsFromAnother(
        @RequestParam id: Long,
        @RequestParam idAnother: Long,
        @RequestParam fields: String,
    ): String {
        Song
            .loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                song.copyFieldsFromAnother(idAnother, fields.split(";").map { SongField.valueOf(it) })
            }
        return "OK"
    }

    // Поиск "оригинала" текущей песни (по кнопке на форме, для песен в статусе NONE) - при успехе копирует
    // текст/маркеры и возвращает true, иначе запускает поиск текста в Интернете и возвращает false
    @PostMapping("/song/findoriginal")
    @ResponseBody
    fun doFindOriginalForSong(
        @RequestParam id: Long,
    ): Boolean {
        val song =
            Song.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return false
        val original =
            findDuplicateOriginal(
                song,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return if (original != null) {
            applyDuplicateOriginal(song, original)
            true
        } else {
            getLyricsSearch(song = song, lyricsFinderService = lyricsFinderService, engine = resolveLyricsSearchEngine())
            false
        }
    }

    // Список песен из той же "семьи" (совпадение id/root_id с текущей песней), с разницей длительности
    @PostMapping("/song/familysongs")
    @ResponseBody
    fun getFamilySongs(
        @RequestParam id: Long,
    ): List<FamilySongDto> {
        val song =
            Song.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return emptyList()
        val familyIds = findFamilySongIds(song, database = WORKING_DATABASE)
        val familySettings =
            Song.loadListFromDbByIds(
                familyIds,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val currentMs = song.ms
        val originalId = if (song.rootId != 0L) song.rootId else song.id
        return (familySettings.values + song)
            .map { s ->
                val diffMs = s.ms - currentMs
                val diffSeconds = Math.round(diffMs / 1000.0)
                FamilySongDto(
                    id = s.id,
                    songName = s.songName,
                    author = s.author,
                    album = s.album,
                    year = s.year,
                    diffSeconds = diffSeconds,
                    original = s.id == originalId,
                    current = s.id == song.id,
                    idStatus = s.idStatus,
                )
            }.sortedBy { it.year }
    }

    // Ручной поиск "оригинала" по (части) названия - без учёта пунктуации и с "ё"="е" (модалка "Похожие версии песни")
    @PostMapping("/song/searchoriginal")
    @ResponseBody
    fun searchOriginalCandidates(
        @RequestParam id: Long,
        @RequestParam search: String,
    ): List<FamilySongDto> {
        val song =
            Song.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return emptyList()
        val ids = searchSongsByNormalizedName(song, search, database = WORKING_DATABASE)
        if (ids.isEmpty()) return emptyList()
        val candidates =
            Song.loadListFromDbByIds(
                ids,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val currentMs = song.ms
        val originalId = if (song.rootId != 0L) song.rootId else song.id
        return candidates.values
            .map { s ->
                val diffMs = s.ms - currentMs
                val diffSeconds = Math.round(diffMs / 1000.0)
                FamilySongDto(
                    id = s.id,
                    songName = s.songName,
                    author = s.author,
                    album = s.album,
                    year = s.year,
                    diffSeconds = diffSeconds,
                    original = s.id == originalId,
                    current = s.id == song.id,
                    idStatus = s.idStatus,
                )
            }.sortedBy { it.year }
    }

    // Выбор песни из модалки "Похожие версии песни" - копирует текст/маркеры, безусловно проставляет
    // root_id (осознанный выбор пользователя) и условно статус (только если он ещё NONE/0 -> TEXT_CREATE/1).
    // Дополнительно сохраняет три аудиополя (audioParentId, audioSimilarityPercent, audioDeltaMs),
    // связанные с выбранным кандидатом. Параметр audioSimilarityPercent nullable: отсутствие обеих
    // метрик (audioSimilarityPercent и deltaMs) означает выбор без сверки и приводит к записи 0/0;
    // частичная пара (только одна из двух метрик) отклоняется 400 Bad Request до изменения записи.
    // Подробнее — docs/features/songs-table.md (FR-009).
    @PostMapping("/song/selectfamilysong")
    @ResponseBody
    fun selectFamilySong(
        @RequestParam id: Long,
        @RequestParam idAnother: Long,
        @RequestParam(required = false) deltaMs: Long?,
        @RequestParam(required = false) audioSimilarityPercent: Int?,
    ): SelectFamilySongResultDto? {
        if (id == idAnother) {
            throw IllegalArgumentException("Выбор текущей песни недопустим: id == idAnother ($id)")
        }
        // Валидация парности nullable-метрик: либо обе, либо ни одной. Дельта=0 разрешена как
        // успешный результат сверки, но без процента считается частичной парой.
        val hasPercent = audioSimilarityPercent != null
        val hasDelta = deltaMs != null
        if (hasPercent != hasDelta) {
            throw IllegalArgumentException(
                "Метрики сверки должны передаваться парой: либо обе, либо ни одной.",
            )
        }
        if (hasPercent && (audioSimilarityPercent < 0 || audioSimilarityPercent > 100)) {
            throw IllegalArgumentException(
                "audioSimilarityPercent должен быть в диапазоне 0..100, получено $audioSimilarityPercent",
            )
        }
        val song =
            Song.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return null
        val another =
            Song.loadFromDbById(
                idAnother,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: return null
        // Ручной режим: явно передаём audioParentId = another.id и согласованные метрики (или 0/0).
        val resolvedPercent = audioSimilarityPercent ?: 0
        val resolvedDelta = deltaMs ?: 0L
        applyFamilySongSelection(
            song = song,
            another = another,
            deltaMs = deltaMs,
            audioParentId = another.id,
            audioSimilarityPercent = resolvedPercent,
            audioDeltaMs = resolvedDelta,
        )
        // Пост-сохранительная проверка: helper уже выполнил saveToDb(); перечитываем и убеждаемся,
        // что три аудиополя действительно записаны. Иначе возвращаем ошибку вместо ложного успеха.
        val reloaded =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: throw IllegalStateException("Не удалось перечитать запись id=$id после сохранения")
        if (reloaded.audioParentId != another.id ||
            reloaded.audioSimilarityPercent != resolvedPercent ||
            reloaded.audioDeltaMs != resolvedDelta
        ) {
            throw IllegalStateException(
                "Аудиополя не подтверждены после сохранения: " +
                    "parent=${reloaded.audioParentId} (ожидалось ${another.id}), " +
                    "percent=${reloaded.audioSimilarityPercent} (ожидалось $resolvedPercent), " +
                    "deltaMs=${reloaded.audioDeltaMs} (ожидалось $resolvedDelta)",
            )
        }
        return SelectFamilySongResultDto(
            rootId = song.rootId,
            idStatus = song.idStatus,
            audioParentId = reloaded.audioParentId,
            audioSimilarityPercent = reloaded.audioSimilarityPercent,
            audioDeltaMs = reloaded.audioDeltaMs,
        )
    }

    // Акустическая сверка текущей песни с кандидатом в оригинал (модалка "Похожие версии песни",
    // кнопки "Сверить"/"Сверить все"). Кросс-корреляция огибающих вокальных стемов - см. WaveformCompare.
    @PostMapping("/song/comparewaveform")
    @ResponseBody
    fun compareWaveform(
        @RequestParam id: Long,
        @RequestParam idAnother: Long,
    ): WaveformCompareResultDto {
        val song =
            Song.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return WaveformCompareResultDto(idAnother, 0, 0, "", false, "Текущая песня не найдена")
        val another =
            Song.loadFromDbById(
                idAnother,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
                ?: return WaveformCompareResultDto(idAnother, 0, 0, "", false, "Кандидат не найден")
        return WaveformCompare.compareWaveforms(song, another)
    }

    // Поиск и сохранение "аудио-родителя" - независимо от кураторского root_id: акустически
    // сравнивает песню со всеми кандидатами (семья + текстовый поиск по названию, как в модалке
    // "Похожие версии песни" / "Сверить все") и запоминает id наиболее похожей, % схожести и
    // сдвиг в мс (порог 95%, см. AUDIO_PARENT_THRESHOLD). НЕ трогает root_id/текст/маркеры/статус -
    // задел на будущую автоматизацию добавления новых песен.
    @PostMapping("/song/findaudioparent")
    @ResponseBody
    fun findAudioParent(
        @RequestParam id: Long,
    ): FindAudioParentResultDto? {
        val song =
            Song.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return null
        val result = findAudioParentByWaveform(song, WORKING_DATABASE, storageService, storageApiClient)
        return FindAudioParentResultDto(
            audioParentId = song.audioParentId,
            audioSimilarityPercent = song.audioSimilarityPercent,
            audioDeltaMs = song.audioDeltaMs,
            matched = result.matched,
            reason = result.reason,
        )
    }

    // Получение исходного текста для голоса
    @PostMapping("/song/voicesourcetext")
    @ResponseBody
    fun getSongSourceText(
        @RequestParam id: Long,
        @RequestParam voiceId: Int,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                song.getSourceText(voiceId)
            } ?: ""
        return text
    }

    // diffbeats + 1
    @PostMapping("/song/diffbeatsinc")
    @ResponseBody
    fun diffBeatsIncrement(
        @RequestParam id: Long,
    ): Long {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            song.fields[SongField.DIFFBEATS] = (song.diffBeats + 1).toString()
            song.saveToDb()
            song.diffBeats
        } ?: -1
    }

    // diffbeats -+ 1
    @PostMapping("/song/diffbeatsdec")
    @ResponseBody
    fun diffBeatsDecrement(
        @RequestParam id: Long,
    ): Long {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            if (song.diffBeats > 0) {
                song.fields[SongField.DIFFBEATS] = (song.diffBeats - 1).toString()
                song.saveToDb()
            }
            song.diffBeats
        } ?: -1
    }

    // Получение sheetsageinfo
    @PostMapping("/song/sheetsageinfo")
    @ResponseBody
    fun getSheetsageinfo(
        @RequestParam id: Long,
    ): Map<String, Any> {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val sheetsageinfo =
            song?.let {
                song.sheetsageInfo
            } ?: emptyMap()
        return sheetsageinfo
    }

    // Получение sheetsageinfo - tempo
    @PostMapping("/song/sheetsageinfobpm")
    @ResponseBody
    fun getSheetsageinfoBpm(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val sheetsageinfotempo =
            song?.let {
                song.sheetsageInfo["tempo"] as String
            } ?: ""
        return sheetsageinfotempo
    }

    // Получение sheetsageinfo - key
    @PostMapping("/song/sheetsageinfokey")
    @ResponseBody
    fun getSheetsageinfoKey(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val sheetsageinfokey =
            song?.let {
                song.sheetsageInfo["key"] as String
            } ?: ""
        return sheetsageinfokey
    }

    // Получение sheetsageinfo - chords
    @PostMapping("/song/sheetsageinfochords")
    @ResponseBody
    fun getSheetsageinfoChords(
        @RequestParam id: Long,
    ): List<String> {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val sheetsageinfochords =
            song?.let {
                @Suppress("UNCHECKED_CAST")
                song.sheetsageInfo["chords"] as List<String>
            } ?: emptyList()
        return sheetsageinfochords
    }

    // Получение sheetsageinfo - beattimes
    @PostMapping("/song/sheetsageinfobeattimes")
    @ResponseBody
    fun getSheetsageinfoBeattimes(
        @RequestParam id: Long,
    ): List<Double> {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val sheetsageinfobeattimes =
            song?.let {
                @Suppress("UNCHECKED_CAST")
                song.sheetsageInfo["beattimes"] as List<Double>
            } ?: emptyList()
        return sheetsageinfobeattimes
    }

    // Получение слогов для голоса
    @PostMapping("/song/voicesourcesyllables")
    @ResponseBody
    fun getSongSourceSyllables(
        @RequestParam id: Long,
        @RequestParam voiceId: Int,
    ): List<String> {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val syllables =
            song?.let {
                song.getSourceSyllables(voiceId)
            } ?: emptyList()
        return syllables
    }

    // Получение маркеров для голоса
    @PostMapping("/song/voicesourcemarkers")
    @ResponseBody
    fun getSongSourceMarkers(
        @RequestParam id: Long,
        @RequestParam voiceId: Int,
    ): List<SourceMarker> {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val markers =
            song?.let {
                song.getSourceMarkers(voiceId)
            } ?: emptyList()
        return markers
    }

    // Получение форматированного текста
    @PostMapping("/song/textformatted")
    @ResponseBody
    fun getSongTextFormatted(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
//            song.getTextFormatted()
                song.formattedTextSong
            } ?: ""
        return text
    }

    // Получение форматированного текста с нотами
    @PostMapping("/song/notesformatted")
    @ResponseBody
    fun getSongFormattedNotes(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
//            song.getFormattedNotes()
                song.formattedTextTabs
            } ?: ""
        return text
    }

    // Получение форматированного текста с аккордами
    @PostMapping("/song/chordsformatted")
    @ResponseBody
    fun getSongFormattedChords(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
//            song.getFormattedChords()
                song.formattedTextChords
            } ?: ""
        return text
    }

    // Получение текста заголовка для boosty
    @PostMapping("/song/textboostyhead")
    @ResponseBody
    fun getSongTextBoostyHead(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getTextBoostyHead()
                text
            } ?: ""
        return text
    }

    // Получение текста тела для boosty
    @PostMapping("/song/textboostybody")
    @ResponseBody
    fun getSongTextBoostyBody(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getTextBoostyBody()
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для sponsr
    @PostMapping("/song/textsponsrhead")
    @ResponseBody
    fun getSongTextSponsrHead(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getTextBoostyHead()
                text
            } ?: ""
        return text
    }

    // Получение текста тела для sponsr
    @PostMapping("/song/textsponsrbody")
    @ResponseBody
    fun getSongTextSponsrBody(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getTextSponsrBody()
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для boostyFiles
    @PostMapping("/song/textboostyfileshead")
    @ResponseBody
    fun getSongTextBoostyFilesHead(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getTextBoostyFilesHead()
                text
            } ?: ""
        return text
    }

    // Получение текста тела VkGroup
    @PostMapping("/song/textvkbody")
    @ResponseBody
    fun getSongTextVkBody(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getVKGroupDescription()
                text
            } ?: ""
        return text
    }

    // Получение текста тела VkGroup
    @PostMapping("/song/textvkbodysponsr")
    @ResponseBody
    fun getSongTextVkBodySponsr(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getVKGroupDescriptionSponsr()
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Karaoke
    @PostMapping("/song/textdzenkaraokeheader")
    @ResponseBody
    fun getSongTextDzenKaraokeHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.KARAOKE, 140)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Dzen Karaoke
    @PostMapping("/song/textdzenkaraokewoheader")
    @ResponseBody
    fun getSongTextDzenKaraokeWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE, 5000)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Lyrics
    @PostMapping("/song/textdzenlyricsheader")
    @ResponseBody
    fun getSongTextDzenLyricsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Dzen Lyrics
    @PostMapping("/song/textdzenlyricswoheader")
    @ResponseBody
    fun getSongTextDzenLyricsWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Chords
    @PostMapping("/song/textdzenchordsheader")
    @ResponseBody
    fun getSongTextDzenChordsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Tabs
    @PostMapping("/song/textdzentabsheader")
    @ResponseBody
    fun getSongTextDzenTabsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.TABS, 140)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Dzen Chords
    @PostMapping("/song/textdzenchordswoheader")
    @ResponseBody
    fun getSongTextDzenChordsWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Dzen Tabs
    @PostMapping("/song/textdzentabswoheader")
    @ResponseBody
    fun getSongTextDzenTabsWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.TABS, 5000)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Demo
    @PostMapping("/song/textdzendemoheader")
    @ResponseBody
    fun getSongTextDzenDemoHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionDemoHeader(140)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Dzen Demo
    @PostMapping("/song/textdzendemowoheader")
    @ResponseBody
    fun getSongTextDzenDemoWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodesDemo(5000)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Karaoke
    @PostMapping("/song/textplkaraokeheader")
    @ResponseBody
    fun getSongTextPlKaraokeHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.KARAOKE, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Platforma Karaoke
    @PostMapping("/song/textplkaraokewoheader")
    @ResponseBody
    fun getSongTextPlKaraokeWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE, 5000, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Lyrics
    @PostMapping("/song/textpllyricsheader")
    @ResponseBody
    fun getSongTextPlLyricsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.LYRICS, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Platforma Lyrics
    @PostMapping("/song/textpllyricswoheader")
    @ResponseBody
    fun getSongTextPlLyricsWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Chords
    @PostMapping("/song/textplchordsheader")
    @ResponseBody
    fun getSongTextPlChordsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Tabs
    @PostMapping("/song/textpltabsheader")
    @ResponseBody
    fun getSongTextPlTabsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionHeader(SongVersion.TABS, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Platforma Chords
    @PostMapping("/song/textplchordswoheader")
    @ResponseBody
    fun getSongTextPlChordsWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Platforma Tabs
    @PostMapping("/song/textpltabswoheader")
    @ResponseBody
    fun getSongTextPlTabsWOHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.TABS, 5000, 100)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Karaoke
    @PostMapping("/song/textvkkaraokeheader")
    @ResponseBody
    fun getSongTextVkKaraokeHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                SongRenderContext(song, SongVersion.KARAOKE)
                val text = it.getDescriptionVkHeader(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Vk Karaoke
    @PostMapping("/song/textvkkaraoke")
    @ResponseBody
    fun getSongTextVkKaraoke(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVk(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Lyrics
    @PostMapping("/song/textvklyricsheader")
    @ResponseBody
    fun getSongTextVkLyricsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Vk Lyrics
    @PostMapping("/song/textvklyrics")
    @ResponseBody
    fun getSongTextVkLyrics(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVk(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Chords
    @PostMapping("/song/textvkchordsheader")
    @ResponseBody
    fun getSongTextVkChordsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Tabs
    @PostMapping("/song/textvktabsheader")
    @ResponseBody
    fun getSongTextVkTabsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVkHeader(SongVersion.TABS)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Vk Chords
    @PostMapping("/song/textvkchords")
    @ResponseBody
    fun getSongTextVkChords(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVk(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Vk Tabs
    @PostMapping("/song/textvktabs")
    @ResponseBody
    fun getSongTextVkTabs(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVk(SongVersion.TABS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Demo
    @PostMapping("/song/textvkdemoheader")
    @ResponseBody
    fun getSongTextVkDemoHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVkDemoHeader()
                text
            } ?: ""
        return text
    }

    // Получение текста тела для Vk Demo
    @PostMapping("/song/textvkdemo")
    @ResponseBody
    fun getSongTextVkDemo(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionVkDemo()
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Karaoke
    @PostMapping("/song/texttelegramkaraokeheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Lyrics
    @PostMapping("/song/texttelegramlyricsheader")
    @ResponseBody
    fun getSongTextTelegramLyricsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Chords
    @PostMapping("/song/texttelegramchordsheader")
    @ResponseBody
    fun getSongTextTelegramChordsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Tabs
    @PostMapping("/song/texttelegramtabsheader")
    @ResponseBody
    fun getSongTextTelegramTabsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.TABS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Demo
    @PostMapping("/song/texttelegramdemoheader")
    @ResponseBody
    fun getSongTextTelegramDemoHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionTelegramDemoHeader()
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Max Karaoke
    @PostMapping("/song/textmaxkaraokeheader")
    @ResponseBody
    fun getSongTextMaxKaraokeHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionMaxHeader(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Max Lyrics
    @PostMapping("/song/textmaxlyricsheader")
    @ResponseBody
    fun getSongTextMaxLyricsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionMaxHeader(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Max Chords
    @PostMapping("/song/textmaxchordsheader")
    @ResponseBody
    fun getSongTextMaxChordsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionMaxHeader(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Max Tabs
    @PostMapping("/song/textmaxtabsheader")
    @ResponseBody
    fun getSongTextMaxTabsHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionMaxHeader(SongVersion.TABS)
                text
            } ?: ""
        return text
    }

    // Получение текста заголовка для Max Demo
    @PostMapping("/song/textmaxdemoheader")
    @ResponseBody
    fun getSongTextMaxDemoHeader(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val text =
            song?.let {
                val text = it.getDescriptionMaxDemoHeader()
                text
            } ?: ""
        return text
    }

    // Получение indexTabsVariant
    @PostMapping("/song/indextabsvariant")
    @ResponseBody
    fun getSongIndexTabsVariant(
        @RequestParam id: Long,
    ): Int {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.indexTabsVariant ?: 0
    }

    // Получение списка авторов
    @PostMapping("/songs/authors")
    @ResponseBody
    fun authors(): Map<String, Any> =
        mapOf(
            "authors" to Song.loadListAuthors(database = WORKING_DATABASE),
        )

    // Получение списка словарей
    @PostMapping("/songs/dicts")
    @ResponseBody
    fun dicts(): Map<String, Any> =
        mapOf(
            "dicts" to
                TEXT_FILE_DICTS.keys
                    .toMutableList()
                    .sorted()
                    .toList(),
        )

    // Получение списка статусов процессов
    @PostMapping("/processes/countwaiting")
    @ResponseBody
    fun getCountWaiting(): Long = KaraokeProcess.getCountWaiting(database = WORKING_DATABASE)

    // Получение списка статусов процессов
    @PostMapping("/processes/statuses")
    @ResponseBody
    fun processesStatuses(): Map<String, Any> =
        mapOf(
            "statuses" to KaraokeProcessStatuses.entries.toTypedArray(),
        )

    // Получение списка типов процессов
    @PostMapping("/processes/types")
    @ResponseBody
    fun processesTypes(): Map<String, Any> =
        mapOf(
            "authors" to KaraokeProcessStatuses.entries.toTypedArray(),
        )

    // Получение списка песен по списку id
    @PostMapping("/songs/ids")
    @ResponseBody
    fun apisSongsByIds(
        @RequestParam ids: List<Long>,
    ): List<SongDTO> =
        Song
            .loadListFromDb(
                mapOf(Pair("ids", ids.joinToString(","))),
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            ).map {
                it.toDTO()
            }

    // Получение списка процессов по списку id
    @PostMapping("/processes/ids")
    @ResponseBody
    fun apisProcessesByIds(
        @RequestParam ids: List<Long>,
    ): List<KaraokeProcessDTO> =
        KaraokeProcess.loadList(mapOf(Pair("ids", ids.joinToString(","))), database = WORKING_DATABASE).map {
            it.toDTO()
        }

    // список publications
    @PostMapping("/publications")
    @ResponseBody
    fun publications(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["publish_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to
                Publication
                    .getPublicationList(
                        args,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    ).map {
                        it.toDTO()
                    },
        )
    }

    // список unpublications
    @PostMapping("/unpublications")
    @ResponseBody
    fun unpublications(): Map<String, Any> =
        mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to
                Publication
                    .getUnPublicationList(
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    ).map { publication ->
                        publication.map { it.toDTO() }
                    },
        )

    // список skipedpublications
    @PostMapping("/skipedpublications")
    @ResponseBody
    fun skipedPublications(): Map<String, Any> =
        mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to
                Publication
                    .getSkipedPublicationList(
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    ).map { publish ->
                        publish.map { it.toDTO() }
                    },
        )

    // список publications
    @PostMapping("/publications2")
    @ResponseBody
    fun publications2(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["publish_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to
                CrossSong.publications(
                    Publication.getSongListForPublications(
                        args,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    ),
                ),
        )
    }

    // список unpublications
    @PostMapping("/unpublications2")
    @ResponseBody
    fun unpublications2(): Map<String, Any> =
        mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to
                CrossSong.unpublications(
                    Publication.getSongListForUnpublications(
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    ),
                ),
        )

    @PostMapping("/publicationsdigest")
    @ResponseBody
    fun publicationsDigest(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["filter_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }
        val listOfSongs =
            Publication.getSongListForPublications(
                args,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val publications =
            when (filterCond) {
                "unpublish" -> {
                    CrossSong.unpublications(listOfSongs)
                }
                "skiped" -> {
                    CrossSong.skiped(listOfSongs)
                }
                else -> {
                    CrossSong.publications(listOfSongs)
                }
            }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publicationsDigest" to publications,
        )
    }

    @PostMapping("/processesdigests")
    @ResponseBody
    fun apisProcessesDigest(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterThreadId: String?,
        @RequestParam(required = false) filterName: String?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) filterOrder: String?,
        @RequestParam(required = false) filterPriority: String?,
        @RequestParam(required = false) filterDescription: String?,
        @RequestParam(required = false) filterSongId: String?,
        @RequestParam(required = false) filterType: String?,
        @RequestParam(required = false) filterLimit: String?,
        @RequestParam(required = false) filterNotail: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterThreadId?.let { if (filterThreadId != "") args["thread_id"] = filterThreadId }
        filterName?.let { if (filterName != "") args["process_name"] = filterName }
        filterStatus?.let { if (filterStatus != "") args["process_status"] = filterStatus }
        filterOrder?.let { if (filterOrder != "") args["process_order"] = filterOrder }
        filterPriority?.let { if (filterPriority != "") args["process_priority"] = filterPriority }
        filterDescription?.let { if (filterDescription != "") args["process_description"] = filterDescription }
        filterSongId?.let { if (filterSongId != "") args["song_id"] = filterSongId }
        filterType?.let { if (filterType != "") args["process_type"] = filterType }
        filterLimit?.let { if (filterLimit != "") args["filter_limit"] = filterLimit }
        filterNotail?.let { if (filterNotail != "") args["filter_notail"] = filterNotail }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "processesDigests" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() },
            "statuses" to KaraokeProcessStatuses.entries.toTypedArray(),
            "types" to KaraokeProcessTypes.entries.toTypedArray(),
        )
    }

    @PostMapping("/songshistory")
    @ResponseBody
    fun apisSongsHistory(): Map<String, Any> {
        val history = SongsHistory().toDTO()
        return mapOf(
            "history" to history,
        )
    }

    // Получение списка песен
    @PostMapping("/songsdigests")
    @ResponseBody
    fun apisSongsDigests(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterSongName: String?,
        @RequestParam(required = false) filterAuthor: String?,
        @RequestParam(required = false) filterYear: String?,
        @RequestParam(required = false) filterAlbum: String?,
        @RequestParam(required = false) filterTrack: String?,
        @RequestParam(required = false) filterTags: String?,
        @RequestParam(required = false) filterDate: String?,
        @RequestParam(required = false) filterTime: String?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) flagBoosty: String?,
        @RequestParam(required = false) flagSponsr: String?,
        @RequestParam(required = false) flagVk: String?,
        @RequestParam(required = false) flagDzenLyrics: String?,
        @RequestParam(required = false) flagDzenKaraoke: String?,
        @RequestParam(required = false) flagDzenChords: String?,
        @RequestParam(required = false) flagDzenMelody: String?,
        @RequestParam(required = false) flagVkLyrics: String?,
        @RequestParam(required = false) flagVkKaraoke: String?,
        @RequestParam(required = false) flagVkChords: String?,
        @RequestParam(required = false) flagVkMelody: String?,
        @RequestParam(required = false) flagTelegramLyrics: String?,
        @RequestParam(required = false) flagTelegramKaraoke: String?,
        @RequestParam(required = false) flagTelegramChords: String?,
        @RequestParam(required = false) flagTelegramMelody: String?,
        @RequestParam(required = false) flagPlLyrics: String?,
        @RequestParam(required = false) flagPlKaraoke: String?,
        @RequestParam(required = false) flagPlChords: String?,
        @RequestParam(required = false) flagPlMelody: String?,
        @RequestParam(required = false) flagMaxLyrics: String?,
        @RequestParam(required = false) flagMaxKaraoke: String?,
        @RequestParam(required = false) flagMaxChords: String?,
        @RequestParam(required = false) flagMaxMelody: String?,
        @RequestParam(required = false) flagFree: String?,
        @RequestParam(required = false) filterResultVersion: String?,
        @RequestParam(required = false) filterCountVoices: String?,
        @RequestParam(required = false) filterVersionBoosty: String?,
        @RequestParam(required = false) filterVersionBoostyFiles: String?,
        @RequestParam(required = false) filterVersionSponsr: String?,
        @RequestParam(required = false) filterVersionDzenKaraoke: String?,
        @RequestParam(required = false) filterVersionVkKaraoke: String?,
        @RequestParam(required = false) filterVersionTelegramKaraoke: String?,
        @RequestParam(required = false) filterVersionPlKaraoke: String?,
        @RequestParam(required = false) filterVersionMaxKaraoke: String?,
        @RequestParam(required = false) filterRate: String?,
        @RequestParam(required = false) filterStatusProcessLyrics: String?,
        @RequestParam(required = false) filterStatusProcessKaraoke: String?,
        @RequestParam(required = false) filterStatusProcessDemo: String?,
        @RequestParam(required = false) filterIsSync: String?,
        @RequestParam(required = false) filterRootId: String?,
        @RequestParam(required = false) filterAudioParentId: String?,
        @RequestParam(required = false) filterSongType: String?,
        // filterAssignmentStatus/target — фильтр по назначенному заданию онлайн-редактора ("unassigned"
        // или dbValue из SongAssignmentStatus). Song по-прежнему всегда грузятся из WORKING_DATABASE
        // (как раньше) — target относится ТОЛЬКО к тому, где искать назначения (local/remote).
        @RequestParam(required = false) filterAssignmentStatus: String?,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterSongName?.let { if (filterSongName != "") args["song_name"] = filterSongName }
        filterAuthor?.let { if (filterAuthor != "") args["song_author"] = filterAuthor }
        filterAlbum?.let { if (filterAlbum != "") args["song_album"] = filterAlbum }
        filterDate?.let { if (filterDate != "") args["publish_date"] = filterDate }
        filterTime?.let { if (filterTime != "") args["publish_time"] = filterTime }
        filterYear?.let { if (filterYear != "") args["song_year"] = filterYear }
        filterTrack?.let { if (filterTrack != "") args["song_track"] = filterTrack }
        filterTags?.let { if (filterTags != "") args["tags"] = filterTags }
        filterStatus?.let { if (filterStatus != "") args["id_status"] = filterStatus }
        flagBoosty?.let { if (flagBoosty != "") args["flag_boosty"] = flagBoosty }
        flagSponsr?.let { if (flagSponsr != "") args["flag_sponsr"] = flagSponsr }
        flagVk?.let { if (flagVk != "") args["flag_vk"] = flagVk }
        flagDzenLyrics?.let { if (flagDzenLyrics != "") args["flag_dzen_lyrics"] = flagDzenLyrics }
        flagDzenKaraoke?.let { if (flagDzenKaraoke != "") args["flag_dzen_karaoke"] = flagDzenKaraoke }
        flagDzenChords?.let { if (flagDzenChords != "") args["flag_dzen_chords"] = flagDzenChords }
        flagDzenMelody?.let { if (flagDzenMelody != "") args["flag_dzen_melody"] = flagDzenMelody }
        flagVkLyrics?.let { if (flagVkLyrics != "") args["flag_vk_lyrics"] = flagVkLyrics }
        flagVkKaraoke?.let { if (flagVkKaraoke != "") args["flag_vk_karaoke"] = flagVkKaraoke }
        flagVkChords?.let { if (flagVkChords != "") args["flag_vk_chords"] = flagVkChords }
        flagVkMelody?.let { if (flagVkMelody != "") args["flag_vk_melody"] = flagVkMelody }
        flagTelegramLyrics?.let { if (flagTelegramLyrics != "") args["flag_telegram_lyrics"] = flagTelegramLyrics }
        flagTelegramKaraoke?.let { if (flagTelegramKaraoke != "") args["flag_telegram_karaoke"] = flagTelegramKaraoke }
        flagTelegramChords?.let { if (flagTelegramChords != "") args["flag_telegram_chords"] = flagTelegramChords }
        flagTelegramMelody?.let { if (flagTelegramMelody != "") args["flag_telegram_melody"] = flagTelegramMelody }
        flagPlLyrics?.let { if (flagPlLyrics != "") args["flag_pl_lyrics"] = flagPlLyrics }
        flagPlKaraoke?.let { if (flagPlKaraoke != "") args["flag_pl_karaoke"] = flagPlKaraoke }
        flagPlChords?.let { if (flagPlChords != "") args["flag_pl_chords"] = flagPlChords }
        flagPlMelody?.let { if (flagPlMelody != "") args["flag_pl_melody"] = flagPlMelody }
        flagMaxLyrics?.let { if (flagMaxLyrics != "") args["flag_max_lyrics"] = flagMaxLyrics }
        flagMaxKaraoke?.let { if (flagMaxKaraoke != "") args["flag_max_karaoke"] = flagMaxKaraoke }
        flagMaxChords?.let { if (flagMaxChords != "") args["flag_max_chords"] = flagMaxChords }
        flagMaxMelody?.let { if (flagMaxMelody != "") args["flag_max_melody"] = flagMaxMelody }
        flagFree?.let { if (flagFree != "") args["flag_free"] = flagFree }
        filterResultVersion?.let { if (filterResultVersion != "") args["filter_result_version"] = filterResultVersion }
        filterCountVoices?.let { if (filterCountVoices != "") args["filter_count_voices"] = filterCountVoices }
        filterVersionBoosty?.let { if (filterVersionBoosty != "") args["filter_version_boosty"] = filterVersionBoosty }
        filterVersionBoostyFiles?.let { if (filterVersionBoostyFiles != "") args["filter_version_boosty_files"] = filterVersionBoostyFiles }
        filterVersionSponsr?.let { if (filterVersionSponsr != "") args["filter_version_sponsr"] = filterVersionSponsr }
        filterVersionDzenKaraoke?.let { if (filterVersionDzenKaraoke != "") args["filter_version_dzen_karaoke"] = filterVersionDzenKaraoke }
        filterVersionVkKaraoke?.let { if (filterVersionVkKaraoke != "") args["filter_version_vk_karaoke"] = filterVersionVkKaraoke }
        filterVersionTelegramKaraoke?.let {
            if (filterVersionTelegramKaraoke !=
                ""
            ) {
                args["filter_version_telegram_karaoke"] = filterVersionTelegramKaraoke
            }
        }
        filterVersionPlKaraoke?.let { if (filterVersionPlKaraoke != "") args["filter_version_pl_karaoke"] = filterVersionPlKaraoke }
        filterVersionMaxKaraoke?.let { if (filterVersionMaxKaraoke != "") args["filter_version_max_karaoke"] = filterVersionMaxKaraoke }
        filterRate?.let { if (filterRate != "") args["filter_rate"] = filterRate }
        filterStatusProcessLyrics?.let {
            if (filterStatusProcessLyrics !=
                ""
            ) {
                args["filter_status_process_lyrics"] = filterStatusProcessLyrics
            }
        }
        filterStatusProcessKaraoke?.let {
            if (filterStatusProcessKaraoke !=
                ""
            ) {
                args["filter_status_process_karaoke"] = filterStatusProcessKaraoke
            }
        }
        filterStatusProcessDemo?.let { if (filterStatusProcessDemo != "") args["filter_status_process_demo"] = filterStatusProcessDemo }
        filterIsSync?.let { if (filterIsSync != "") args["is_sync"] = filterIsSync }
        filterRootId?.let { if (filterRootId != "") args["filter_root_id"] = filterRootId }
        filterAudioParentId?.let { if (filterAudioParentId != "") args["filter_audio_parent_id"] = filterAudioParentId }
        filterSongType?.let { if (filterSongType != "") args["song_type"] = filterSongType }

        SongsHistory().add(args)

        var songList =
            Song.loadListFromDb(
                args,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            )
        if (!filterAssignmentStatus.isNullOrBlank()) {
            val remoteAssignmentDb = if (target == "remote") Connection.remote() else null
            try {
                val assignmentDb = remoteAssignmentDb ?: WORKING_DATABASE
                val statuses =
                    SongAssignment.composeStatusesForSongIds(
                        songList.map { it.id },
                        assignmentDb,
                        storageService,
                        storageApiClient,
                    )
                songList =
                    if (filterAssignmentStatus == "unassigned") {
                        songList.filter { statuses[it.id] == null }
                    } else {
                        songList.filter { statuses[it.id]?.second?.dbValue == filterAssignmentStatus }
                    }
            } finally {
                try {
                    remoteAssignmentDb?.getConnection()?.close()
                } catch (_: Exception) {
                }
            }
        }
        val lst = songList.map { it.toDTO().toDtoDigest() }
        var totalMs = 0L
        for (i in lst.indices) {
            if (i > 0) lst[i].idPrevious = lst[i - 1].id
            if (i < lst.size - 1) lst[i].idNext = lst[i + 1].id
            totalMs += lst[i].ms
        }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "songsDigests" to lst,
            "authors" to Song.loadListAuthors(database = WORKING_DATABASE),
            "albums" to Song.loadListAlbums(WORKING_DATABASE),
            "totalDuration" to convertMillisecondsToDtoTimecode(totalMs),
        )
    }

    // Получение списка песен
    @PostMapping("/songs")
    @ResponseBody
    fun apisSongs(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterSongname: String?,
        @RequestParam(required = false) filterAuthor: String?,
        @RequestParam(required = false) filterYear: String?,
        @RequestParam(required = false) filterAlbum: String?,
        @RequestParam(required = false) filterTrack: String?,
        @RequestParam(required = false) filterTags: String?,
        @RequestParam(required = false) filterDate: String?,
        @RequestParam(required = false) filterTime: String?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) flagBoosty: String?,
        @RequestParam(required = false) flagSponsr: String?,
        @RequestParam(required = false) flagVk: String?,
        @RequestParam(required = false) flagDzenLyrics: String?,
        @RequestParam(required = false) flagDzenKaraoke: String?,
        @RequestParam(required = false) flagDzenChords: String?,
        @RequestParam(required = false) flagDzenMelody: String?,
        @RequestParam(required = false) flagVkLyrics: String?,
        @RequestParam(required = false) flagVkKaraoke: String?,
        @RequestParam(required = false) flagVkChords: String?,
        @RequestParam(required = false) flagVkMelody: String?,
        @RequestParam(required = false) flagTelegramLyrics: String?,
        @RequestParam(required = false) flagTelegramKaraoke: String?,
        @RequestParam(required = false) flagTelegramChords: String?,
        @RequestParam(required = false) flagTelegramMelody: String?,
        @RequestParam(required = false) flagPlLyrics: String?,
        @RequestParam(required = false) flagPlKaraoke: String?,
        @RequestParam(required = false) flagPlChords: String?,
        @RequestParam(required = false) flagPlMelody: String?,
        @RequestParam(required = false) flagMaxLyrics: String?,
        @RequestParam(required = false) flagMaxKaraoke: String?,
        @RequestParam(required = false) flagMaxChords: String?,
        @RequestParam(required = false) flagMaxMelody: String?,
        @RequestParam(required = false) flagFree: String?,
        @RequestParam(required = false) filterResultVersion: String?,
        @RequestParam(required = false) filterVersionBoosty: String?,
        @RequestParam(required = false) filterVersionBoostyFiles: String?,
        @RequestParam(required = false) filterVersionSponsr: String?,
        @RequestParam(required = false) filterVersionDzenKaraoke: String?,
        @RequestParam(required = false) filterVersionVkKaraoke: String?,
        @RequestParam(required = false) filterVersionTelegramKaraoke: String?,
        @RequestParam(required = false) filterVersionPlKaraoke: String?,
        @RequestParam(required = false) filterVersionMaxKaraoke: String?,
        @RequestParam(required = false) filterRate: String?,
        @RequestParam(required = false) filterStatusProcessLyrics: String?,
        @RequestParam(required = false) filterStatusProcessKaraoke: String?,
        @RequestParam(required = false) filterStatusProcessDemo: String?,
        @RequestParam(required = false) filterRootId: String?,
        @RequestParam(required = false) filterAudioParentId: String?,
        @RequestParam(required = false) pageSize: Int = 30,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterSongname?.let { if (filterSongname != "") args["song_name"] = filterSongname }
        filterAuthor?.let { if (filterAuthor != "") args["song_author"] = filterAuthor }
        filterAlbum?.let { if (filterAlbum != "") args["song_album"] = filterAlbum }
        filterDate?.let { if (filterDate != "") args["publish_date"] = filterDate }
        filterTime?.let { if (filterTime != "") args["publish_time"] = filterTime }
        filterYear?.let { if (filterYear != "") args["song_year"] = filterYear }
        filterTrack?.let { if (filterTrack != "") args["song_track"] = filterTrack }
        filterTags?.let { if (filterTags != "") args["tags"] = filterTags }
        filterStatus?.let { if (filterStatus != "") args["id_status"] = filterStatus }
        flagBoosty?.let { if (flagBoosty != "") args["flag_boosty"] = flagBoosty }
        flagSponsr?.let { if (flagSponsr != "") args["flag_sponsr"] = flagSponsr }
        flagVk?.let { if (flagVk != "") args["flag_vk"] = flagVk }
        flagDzenLyrics?.let { if (flagDzenLyrics != "") args["flag_dzen_lyrics"] = flagDzenLyrics }
        flagDzenKaraoke?.let { if (flagDzenKaraoke != "") args["flag_dzen_karaoke"] = flagDzenKaraoke }
        flagDzenChords?.let { if (flagDzenChords != "") args["flag_dzen_chords"] = flagDzenChords }
        flagDzenMelody?.let { if (flagDzenMelody != "") args["flag_dzen_melody"] = flagDzenMelody }
        flagVkLyrics?.let { if (flagVkLyrics != "") args["flag_vk_lyrics"] = flagVkLyrics }
        flagVkKaraoke?.let { if (flagVkKaraoke != "") args["flag_vk_karaoke"] = flagVkKaraoke }
        flagVkChords?.let { if (flagVkChords != "") args["flag_vk_chords"] = flagVkChords }
        flagVkMelody?.let { if (flagVkMelody != "") args["flag_vk_melody"] = flagVkMelody }
        flagTelegramLyrics?.let { if (flagTelegramLyrics != "") args["flag_telegram_lyrics"] = flagTelegramLyrics }
        flagTelegramKaraoke?.let { if (flagTelegramKaraoke != "") args["flag_telegram_karaoke"] = flagTelegramKaraoke }
        flagTelegramChords?.let { if (flagTelegramChords != "") args["flag_telegram_chords"] = flagTelegramChords }
        flagTelegramMelody?.let { if (flagTelegramMelody != "") args["flag_telegram_melody"] = flagTelegramMelody }
        flagPlLyrics?.let { if (flagPlLyrics != "") args["flag_pl_lyrics"] = flagPlLyrics }
        flagPlKaraoke?.let { if (flagPlKaraoke != "") args["flag_pl_karaoke"] = flagPlKaraoke }
        flagPlChords?.let { if (flagPlChords != "") args["flag_pl_chords"] = flagPlChords }
        flagPlMelody?.let { if (flagPlMelody != "") args["flag_pl_melody"] = flagPlMelody }
        flagMaxLyrics?.let { if (flagMaxLyrics != "") args["flag_max_lyrics"] = flagMaxLyrics }
        flagMaxKaraoke?.let { if (flagMaxKaraoke != "") args["flag_max_karaoke"] = flagMaxKaraoke }
        flagMaxChords?.let { if (flagMaxChords != "") args["flag_max_chords"] = flagMaxChords }
        flagMaxMelody?.let { if (flagMaxMelody != "") args["flag_max_melody"] = flagMaxMelody }
        flagFree?.let { if (flagFree != "") args["flag_free"] = flagFree }
        filterResultVersion?.let { if (filterResultVersion != "") args["filter_result_version"] = filterResultVersion }
        filterVersionBoosty?.let { if (filterVersionBoosty != "") args["filter_version_boosty"] = filterVersionBoosty }
        filterVersionBoostyFiles?.let { if (filterVersionBoostyFiles != "") args["filter_version_boosty_files"] = filterVersionBoostyFiles }
        filterVersionSponsr?.let { if (filterVersionSponsr != "") args["filter_version_sponsr"] = filterVersionSponsr }
        filterVersionDzenKaraoke?.let { if (filterVersionDzenKaraoke != "") args["filter_version_dzen_karaoke"] = filterVersionDzenKaraoke }
        filterVersionVkKaraoke?.let { if (filterVersionVkKaraoke != "") args["filter_version_vk_karaoke"] = filterVersionVkKaraoke }
        filterVersionTelegramKaraoke?.let {
            if (filterVersionTelegramKaraoke !=
                ""
            ) {
                args["filter_version_telegram_karaoke"] = filterVersionTelegramKaraoke
            }
        }
        filterVersionPlKaraoke?.let { if (filterVersionPlKaraoke != "") args["filter_version_pl_karaoke"] = filterVersionPlKaraoke }
        filterVersionMaxKaraoke?.let { if (filterVersionMaxKaraoke != "") args["filter_version_max_karaoke"] = filterVersionMaxKaraoke }
        filterRate?.let { if (filterRate != "") args["filter_rate"] = filterRate }
        filterStatusProcessLyrics?.let {
            if (filterStatusProcessLyrics !=
                ""
            ) {
                args["filter_status_process_lyrics"] = filterStatusProcessLyrics
            }
        }
        filterStatusProcessKaraoke?.let {
            if (filterStatusProcessKaraoke !=
                ""
            ) {
                args["filter_status_process_karaoke"] = filterStatusProcessKaraoke
            }
        }
        filterStatusProcessDemo?.let { if (filterStatusProcessDemo != "") args["filter_status_process_demo"] = filterStatusProcessDemo }
        filterRootId?.let { if (filterRootId != "") args["filter_root_id"] = filterRootId }
        filterAudioParentId?.let { if (filterAudioParentId != "") args["filter_audio_parent_id"] = filterAudioParentId }

        SongsHistory().add(args)

        val lst =
            Song
                .loadListFromDb(
                    args,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                ).map {
                    it.toDTO()
                }
        for (i in lst.indices) {
            if (i > 0) lst[i].idPrevious = lst[i - 1].id
            if (i < lst.size - 1) lst[i].idNext = lst[i + 1].id
        }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to lst.chunked(pageSize),
            "authors" to Song.loadListAuthors(database = WORKING_DATABASE),
            "albums" to Song.loadListAlbums(WORKING_DATABASE),
        )
    }

    // Получение списка процессов
    @PostMapping("/processes")
    @ResponseBody
    fun apisProcesses(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterThreadId: String?,
        @RequestParam(required = false) filterName: String?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) filterOrder: String?,
        @RequestParam(required = false) filterPriority: String?,
        @RequestParam(required = false) filterDescription: String?,
        @RequestParam(required = false) filterSongId: String?,
        @RequestParam(required = false) filterType: String?,
        @RequestParam(required = false) filterLimit: String?,
        @RequestParam(required = false) pageSize: Int = 30,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterThreadId?.let { if (filterThreadId != "") args["thread_id"] = filterThreadId }
        filterName?.let { if (filterName != "") args["process_name"] = filterName }
        filterStatus?.let { if (filterStatus != "") args["process_status"] = filterStatus }
        filterOrder?.let { if (filterOrder != "") args["process_order"] = filterOrder }
        filterPriority?.let { if (filterPriority != "") args["process_priority"] = filterPriority }
        filterDescription?.let { if (filterDescription != "") args["process_description"] = filterDescription }
        filterSongId?.let { if (filterSongId != "") args["song_id"] = filterSongId }
        filterType?.let { if (filterType != "") args["process_type"] = filterType }
        filterLimit?.let { if (filterLimit != "") args["filter_limit"] = filterLimit }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() }.chunked(pageSize),
            "statuses" to KaraokeProcessStatuses.entries.toTypedArray(),
            "types" to KaraokeProcessTypes.entries.toTypedArray(),
        )
    }

    // Видео проигрыватель: Lyrics
    @PostMapping("/song/playlyrics")
    @ResponseBody
    fun doPlayLyrics(
        @RequestParam id: Long,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            song.playLyrics()
        }
        return true
    }

    // Видео проигрыватель: Karaoke
    @PostMapping("/song/playkaraoke")
    @ResponseBody
    fun doPlayKaraoke(
        @RequestParam id: Long,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            song.playKaraoke()
        }
        return true
    }

    // Видео проигрыватель: Render MP4 (из онлайн-плеера)
    @PostMapping("/song/playrendermp4")
    @ResponseBody
    fun doPlayRenderMp4(
        @RequestParam id: Long,
        @RequestParam(required = false) version: String?,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            val renderVersion =
                try {
                    com.svoemesto.karaokeapp.services.RenderVersion
                        .valueOf(version ?: "KARAOKE")
                } catch (_: Exception) {
                    com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE
                }
            song.playRenderMp4ForVersion(renderVersion)
        }
        return true
    }

    // Видео проигрыватель: Chords
    @PostMapping("/song/playchords")
    @ResponseBody
    fun doPlayChords(
        @RequestParam id: Long,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            song.playChords()
        }
        return true
    }

    // Видео проигрыватель: Tabs
    @PostMapping("/song/playtabs")
    @ResponseBody
    fun doPlayTabs(
        @RequestParam id: Long,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            song.playTabs()
        }
        return true
    }

    // Получение песни
    @PostMapping("/song")
    @ResponseBody
    fun apisSong(
        @RequestParam id: String,
    ): Any? {
        val settCurrId = id.toLong()
        val currSett =
            Song
                .loadFromDbById(
                    id.toLong(),
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )?.toDTO()
        if (currSett != null) {
            val lst =
                Song.loadListFromDb(
                    args = mapOf("song_author" to currSett.author),
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            for (i in lst.indices) {
                if (lst[i].id == settCurrId) {
                    if (i > 0) currSett.idPrevious = lst[i - 1].id
                    if (i < lst.size - 1) currSett.idNext = lst[i + 1].id
                    break
                }
            }
            currSett.dateTimePublish?.let {
                val leftTime = "%02d".format(currSett.time.split(":")[0].toLong() - 1) + ":00"
                val rightTime = "%02d".format(currSett.time.split(":")[0].toLong() + 1) + ":00"
                val leftSett =
                    Song.loadListFromDb(
                        args = mapOf("publish_date" to currSett.date, "publish_time" to leftTime),
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                        withoutMarkersAndText = true,
                    )
                val rightSett =
                    Song.loadListFromDb(
                        args = mapOf("publish_date" to currSett.date, "publish_time" to rightTime),
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                        withoutMarkersAndText = true,
                    )
                if (leftSett.isNotEmpty()) currSett.idLeft = leftSett[0].id
                if (rightSett.isNotEmpty()) currSett.idRight = rightSett[0].id
            }

            return currSett
        }

        return Song
            .loadFromDbById(
                id.toLong(),
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.toDTO()
    }

    /**
     * Результат [songs2Update] — помимо старого `albumLinkValid` (см. FR-008 specs/011-album-song-rename)
     * несёт [fileNameRenameError], если переименование файла (специфично для параметра `fileName`,
     * specs/124-filename-sanitization-rename) было отклонено (коллизия/пустое имя после санитайзинга/
     * активная фоновая обработка песни). Остальные поля из запроса при этом всё равно применяются и
     * сохраняются как обычно — отклоняется только сама смена имени файла.
     *
     * @see docs/features/premium-stems.md
     */
    data class SongUpdateResultDto(
        val albumLinkValid: Boolean,
        val fileNameRenameError: String? = null,
    )

    // Сообщает karaoke-web, что free-статус хотя бы одной песни мог измениться — счётчик "В открытом
    // доступе"/"По подписке" (StatBySong, specs/143-song-free-access-window) кешируется на час,
    // без этого сигнала админ увидел бы обновление только в начале следующего часа. НЕ пересчитывает
    // ничего сама — только взводит dirty-флаг (InternalStatsController.markDirty), сам пересчёт —
    // на стороне karaoke-web в течение минуты (StatsCacheScheduler.refreshIfDirty). Best-effort по
    // образцу ackStemJobRawFileConsumed (StemJobProcessing.kt): если karaoke-web недоступен —
    // счётчик просто обновится по обычному часовому крону, как раньше.
    private fun notifyStatsDirty() {
        val baseUrl = Karaoke.stemJobsWebInternalUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return
        try {
            val connection = URL("$baseUrl/api/internal/stats/mark-dirty").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-Internal-Secret", Karaoke.stemJobsInternalSecret)
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = false
            connection.responseCode // инициирует запрос
            connection.disconnect()
        } catch (e: Exception) {
            println("[notifyStatsDirty] ${e.message}")
        }
    }

    // Синхронизация не сообщает, какие именно поля изменились у затронутых записей (Principle II —
    // recordhash-диф, не field-диф) — точное обнаружение "именно free изменился" при синхронизации
    // потребовало бы отдельного построчного сравнения. Упрощение (по решению пользователя): любой
    // push (LOCAL→SERVER — только это направление доставляет изменение туда, где его видит
    // karaoke-web) с непустым списком created/updated по таблице "songs" считается потенциально
    // затрагивающим free-статус — лишний пересчёт раз в синхронизацию дешевле точного диффа.
    private fun notifyStatsDirtyIfSongsPushed(
        targetKey: String,
        direction: SyncDirection,
        affectedCount: Int,
    ) {
        if (targetKey == "songs" && direction == SyncDirection.LOCAL_TO_SERVER && affectedCount > 0) {
            notifyStatsDirty()
        }
    }

    // Обновление песни
    @PostMapping("/song/update")
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
        @RequestParam(required = false) versionBoosty: String?,
        @RequestParam(required = false) idBoostyFiles: String?,
        @RequestParam(required = false) versionBoostyFiles: String?,
        @RequestParam(required = false) idSponsr: String?,
        @RequestParam(required = false) versionSponsr: String?,
        @RequestParam(required = false) indexTabsVariant: String?,
        @RequestParam(required = false) idVk: String?,
        @RequestParam(required = false) idDzenLyrics: String?,
        @RequestParam(required = false) idDzenKaraoke: String?,
        @RequestParam(required = false) idDzenChords: String?,
        @RequestParam(required = false) idDzenMelody: String?,
        @RequestParam(required = false) idVkLyrics: String?,
        @RequestParam(required = false) idVkKaraoke: String?,
        @RequestParam(required = false) idVkChords: String?,
        @RequestParam(required = false) idVkMelody: String?,
        @RequestParam(required = false) idTelegramLyrics: String?,
        @RequestParam(required = false) idTelegramKaraoke: String?,
        @RequestParam(required = false) idTelegramChords: String?,
        @RequestParam(required = false) idTelegramMelody: String?,
        @RequestParam(required = false) idPlLyrics: String?,
        @RequestParam(required = false) idPlKaraoke: String?,
        @RequestParam(required = false) idPlChords: String?,
        @RequestParam(required = false) idPlMelody: String?,
        @RequestParam(required = false) idMaxLyrics: String?,
        @RequestParam(required = false) idMaxKaraoke: String?,
        @RequestParam(required = false) idMaxChords: String?,
        @RequestParam(required = false) idMaxMelody: String?,
        @RequestParam(required = false) idDzenDemo: String?,
        @RequestParam(required = false) idVkDemo: String?,
        @RequestParam(required = false) idTelegramDemo: String?,
        @RequestParam(required = false) idMaxDemo: String?,
        @RequestParam(required = false) versionDzenLyrics: String?,
        @RequestParam(required = false) versionDzenKaraoke: String?,
        @RequestParam(required = false) versionDzenChords: String?,
        @RequestParam(required = false) versionDzenMelody: String?,
        @RequestParam(required = false) versionVkLyrics: String?,
        @RequestParam(required = false) versionVkKaraoke: String?,
        @RequestParam(required = false) versionVkChords: String?,
        @RequestParam(required = false) versionVkMelody: String?,
        @RequestParam(required = false) versionTelegramLyrics: String?,
        @RequestParam(required = false) versionTelegramKaraoke: String?,
        @RequestParam(required = false) versionTelegramChords: String?,
        @RequestParam(required = false) versionTelegramMelody: String?,
        @RequestParam(required = false) versionPlLyrics: String?,
        @RequestParam(required = false) versionPlKaraoke: String?,
        @RequestParam(required = false) versionPlChords: String?,
        @RequestParam(required = false) versionPlMelody: String?,
        @RequestParam(required = false) versionMaxLyrics: String?,
        @RequestParam(required = false) versionMaxKaraoke: String?,
        @RequestParam(required = false) versionMaxChords: String?,
        @RequestParam(required = false) versionMaxMelody: String?,
        @RequestParam(required = false) versionDzenDemo: String?,
        @RequestParam(required = false) versionVkDemo: String?,
        @RequestParam(required = false) versionTelegramDemo: String?,
        @RequestParam(required = false) versionMaxDemo: String?,
        @RequestParam(required = false) resultVersion: String?,
        @RequestParam(required = false) diffBeats: String?,
        @RequestParam(required = false) rate: String?,
        @RequestParam(required = false) rootId: String?,
        @RequestParam(required = false) audioParentId: String?,
        @RequestParam(required = false) audioSimilarityPercent: String?,
        @RequestParam(required = false) audioDeltaMs: String?,
        @RequestParam(required = false) free: String?,
        @RequestParam(required = false) idTariff: String?,
        @RequestParam(required = false) songType: String?,
        @RequestParam(required = false) albumId: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) shortDescription: String?,
        @RequestParam(required = false) warning: String?,
    ): SongUpdateResultDto {
        val songId: Long = id.toLong()
        val song =
            Song.loadFromDbById(
                songId,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        var albumLinkValid = true
        var fileNameRenameError: String? = null
        song?.let { sett ->
            // specs/143-song-free-access-window: снимок ДО применения правок — сравнивается после
            // saveToDb(), чтобы уведомить karaoke-web (StatBySong dirty-флаг) только если free
            // реально изменился, а не на каждое сохранение песни.
            val freeBefore = sett.free
            // specs/124-filename-sanitization-rename FR-006/FR-007/FR-008/FR-011/FR-013: смена
            // "Имя файла" санитайзируется теми же правилами, что и импорт, отклоняется при коллизии/
            // пустом имени/активной фоновой обработке песни, и (если применяется) каскадно
            // переименовывает связанные артефакты на диске и в обоих хранилищах. Остальные поля этого
            // запроса (ниже) применяются независимо от результата этой проверки.
            fileName?.let { requestedFileName ->
                val sanitized = requestedFileName.sanitizeSongFileName()
                val effectiveRootFolder = rootFolder ?: sett.rootFolder
                if (sanitized.isEmpty()) {
                    fileNameRenameError = "Имя файла после удаления недопустимых символов оказалось пустым — введите другое значение."
                } else if (sanitized != sett.fileName) {
                    val collision =
                        Song
                            .loadListFromDb(
                                args = mapOf(Pair("file_name", sanitized), Pair("root_folder", effectiveRootFolder)),
                                database = WORKING_DATABASE,
                                storageService = storageService,
                                storageApiClient = storageApiClient,
                                withoutMarkersAndText = true,
                            ).any { it.id != sett.id }
                    if (collision) {
                        fileNameRenameError = "Песня с именем файла «$sanitized» уже существует в этой папке."
                    } else if (KaraokeProcess.hasActiveProcess(songId = sett.id, database = WORKING_DATABASE)) {
                        fileNameRenameError =
                            "Над песней сейчас выполняется фоновая обработка — дождитесь её завершения и повторите переименование."
                    } else {
                        val oldFileName = sett.fileName
                        sett.fileName = sanitized
                        sett.renameCascadeExtraArtifacts(oldFileName)
                    }
                }
            }
            rootFolder?.let { sett.rootFolder = it }
            tags?.let { sett.tags = it }
            id.let { sett.fields[SongField.ID] = it }
            songName?.let { sett.fields[SongField.NAME] = it }
            author?.let { sett.fields[SongField.AUTHOR] = it }
            year?.let { sett.fields[SongField.YEAR] = it }
            album?.let { sett.fields[SongField.ALBUM] = it }
            track?.let { sett.fields[SongField.TRACK] = it }
            date?.let { sett.fields[SongField.DATE] = it }
            time?.let { sett.fields[SongField.TIME] = it }
            key?.let { sett.fields[SongField.KEY] = it }
            bpm?.let { sett.fields[SongField.BPM] = it }
            ms?.let { sett.fields[SongField.MS] = it }
            idBoosty?.let { sett.fields[SongField.ID_BOOSTY] = it }
            idBoostyFiles?.let { sett.fields[SongField.ID_BOOSTY_FILES] = it }
            idSponsr?.let { sett.fields[SongField.ID_SPONSR] = it }
            versionBoosty?.let { sett.fields[SongField.VERSION_BOOSTY] = it }
            versionBoostyFiles?.let { sett.fields[SongField.VERSION_BOOSTY_FILES] = it }
            versionSponsr?.let { sett.fields[SongField.VERSION_SPONSR] = it }
            indexTabsVariant?.let { sett.fields[SongField.INDEX_TABS_VARIANT] = it }
            idVk?.let { sett.fields[SongField.ID_VK] = it }
            idDzenLyrics?.let { sett.fields[SongField.ID_DZEN_LYRICS] = it }
            idDzenKaraoke?.let { sett.fields[SongField.ID_DZEN_KARAOKE] = it }
            idDzenChords?.let { sett.fields[SongField.ID_DZEN_CHORDS] = it }
            idDzenMelody?.let { sett.fields[SongField.ID_DZEN_MELODY] = it }
            idVkLyrics?.let { sett.fields[SongField.ID_VK_LYRICS] = it }
            idVkKaraoke?.let { sett.fields[SongField.ID_VK_KARAOKE] = it }
            idVkChords?.let { sett.fields[SongField.ID_VK_CHORDS] = it }
            idVkMelody?.let { sett.fields[SongField.ID_VK_MELODY] = it }
            idTelegramLyrics?.let { sett.fields[SongField.ID_TELEGRAM_LYRICS] = it }
            idTelegramKaraoke?.let { sett.fields[SongField.ID_TELEGRAM_KARAOKE] = it }
            idTelegramChords?.let { sett.fields[SongField.ID_TELEGRAM_CHORDS] = it }
            idTelegramMelody?.let { sett.fields[SongField.ID_TELEGRAM_MELODY] = it }
            idPlLyrics?.let { sett.fields[SongField.ID_PL_LYRICS] = it }
            idPlKaraoke?.let { sett.fields[SongField.ID_PL_KARAOKE] = it }
            idPlChords?.let { sett.fields[SongField.ID_PL_CHORDS] = it }
            idPlMelody?.let { sett.fields[SongField.ID_PL_MELODY] = it }
            idMaxLyrics?.let { sett.fields[SongField.ID_MAX_LYRICS] = it }
            idMaxKaraoke?.let { sett.fields[SongField.ID_MAX_KARAOKE] = it }
            idMaxChords?.let { sett.fields[SongField.ID_MAX_CHORDS] = it }
            idMaxMelody?.let { sett.fields[SongField.ID_MAX_MELODY] = it }
            idDzenDemo?.let { sett.fields[SongField.ID_DZEN_DEMO] = it }
            idVkDemo?.let { sett.fields[SongField.ID_VK_DEMO] = it }
            idTelegramDemo?.let { sett.fields[SongField.ID_TELEGRAM_DEMO] = it }
            idMaxDemo?.let { sett.fields[SongField.ID_MAX_DEMO] = it }
            versionDzenLyrics?.let { sett.fields[SongField.VERSION_DZEN_LYRICS] = it }
            versionDzenKaraoke?.let { sett.fields[SongField.VERSION_DZEN_KARAOKE] = it }
            versionDzenChords?.let { sett.fields[SongField.VERSION_DZEN_CHORDS] = it }
            versionDzenMelody?.let { sett.fields[SongField.VERSION_DZEN_MELODY] = it }
            versionVkLyrics?.let { sett.fields[SongField.VERSION_VK_LYRICS] = it }
            versionVkKaraoke?.let { sett.fields[SongField.VERSION_VK_KARAOKE] = it }
            versionVkChords?.let { sett.fields[SongField.VERSION_VK_CHORDS] = it }
            versionVkMelody?.let { sett.fields[SongField.VERSION_VK_MELODY] = it }
            versionTelegramLyrics?.let { sett.fields[SongField.VERSION_TELEGRAM_LYRICS] = it }
            versionTelegramKaraoke?.let { sett.fields[SongField.VERSION_TELEGRAM_KARAOKE] = it }
            versionTelegramChords?.let { sett.fields[SongField.VERSION_TELEGRAM_CHORDS] = it }
            versionTelegramMelody?.let { sett.fields[SongField.VERSION_TELEGRAM_MELODY] = it }
            versionPlLyrics?.let { sett.fields[SongField.VERSION_PL_LYRICS] = it }
            versionPlKaraoke?.let { sett.fields[SongField.VERSION_PL_KARAOKE] = it }
            versionPlChords?.let { sett.fields[SongField.VERSION_PL_CHORDS] = it }
            versionPlMelody?.let { sett.fields[SongField.VERSION_PL_MELODY] = it }
            versionMaxLyrics?.let { sett.fields[SongField.VERSION_MAX_LYRICS] = it }
            versionMaxKaraoke?.let { sett.fields[SongField.VERSION_MAX_KARAOKE] = it }
            versionMaxChords?.let { sett.fields[SongField.VERSION_MAX_CHORDS] = it }
            versionMaxMelody?.let { sett.fields[SongField.VERSION_MAX_MELODY] = it }
            versionDzenDemo?.let { sett.fields[SongField.VERSION_DZEN_DEMO] = it }
            versionVkDemo?.let { sett.fields[SongField.VERSION_VK_DEMO] = it }
            versionTelegramDemo?.let { sett.fields[SongField.VERSION_TELEGRAM_DEMO] = it }
            versionMaxDemo?.let { sett.fields[SongField.VERSION_MAX_DEMO] = it }
            resultVersion?.let { sett.fields[SongField.RESULT_VERSION] = it }
            diffBeats?.let { sett.fields[SongField.DIFFBEATS] = it }
            idStatus?.let { sett.fields[SongField.ID_STATUS] = it }
            rate?.let { sett.fields[SongField.RATE] = it }
            rootId?.let { sett.fields[SongField.ROOT_ID] = it }
            audioParentId?.let { sett.fields[SongField.AUDIO_PARENT_ID] = it }
            audioSimilarityPercent?.let { sett.fields[SongField.AUDIO_SIMILARITY_PERCENT] = it }
            audioDeltaMs?.let { sett.fields[SongField.AUDIO_DELTA_MS] = it }
            free?.let { sett.fields[SongField.FREE] = it }
            idTariff?.let { sett.fields[SongField.ID_TARIFF] = it }
            songType?.let { sett.songType = SongType.entries.firstOrNull { st -> st.dbValue == it.lowercase() } ?: SongType.SONG }
            description?.let { sett.description = it }
            shortDescription?.let { sett.shortDescription = it }
            warning?.let { sett.warning = it }
            // FR-008 (specs/011-album-song-rename): альбом песни обязан принадлежать тому же автору,
            // что и главный автор песни — иначе противоречивое состояние "автор песни" != "автор альбома".
            albumId?.let { rawAlbumId ->
                val newAlbumId = rawAlbumId.toLongOrNull()
                if (newAlbumId == null || newAlbumId <= 0L) {
                    sett.albumId = null
                } else {
                    val album = Album.getAlbumById(newAlbumId, WORKING_DATABASE, storageService, storageApiClient)
                    val albumAuthor =
                        album?.let { Author.getAuthorById(it.authorId, WORKING_DATABASE, storageService, storageApiClient) }
                    if (album != null && albumAuthor != null && albumAuthor.author.equals(sett.author, ignoreCase = true)) {
                        sett.albumId = newAlbumId
                    } else {
                        albumLinkValid = false
                    }
                }
            }
            sett.saveToDb()
            sett.saveToFile()
            if (sett.free != freeBefore) notifyStatsDirty()
        }

        return SongUpdateResultDto(albumLinkValid = albumLinkValid, fileNameRenameError = fileNameRenameError)
    }

    // Получение процесса
    @PostMapping("/process")
    @ResponseBody
    fun apisProcess(
        @RequestParam id: String,
    ): Any? = KaraokeProcess.load(id.toLong(), WORKING_DATABASE)?.toDTO()

    // Обновление процесса
    @PostMapping("/process/update")
    @ResponseBody
    fun processes2Update(
        @RequestParam(required = false) id: Int,
        @RequestParam(required = false) name: String,
        @RequestParam(required = false) status: String,
        @RequestParam(required = false) order: Int,
        @RequestParam(required = false) priority: Int,
        @RequestParam(required = false) command: String,
        @RequestParam(required = false) description: String,
        @RequestParam(required = false) type: String,
    ): Boolean {
        val processId: Long = id.toLong()
        val processes = KaraokeProcess.load(processId, WORKING_DATABASE)
        processes?.let { process ->
            name.let { process.name = it }
            status.let { process.status = it }
            order.let { process.order = it }
            priority.let { process.priority = it }
            command.let { process.command = it }
            description.let { process.description = it }
            type.let { process.type = it }
            process.save()
        }

        return true
    }

    // Получение данных для редактирования сабов
    @PostMapping("/song/voices")
    @ResponseBody
    fun getSongVoices(
        @RequestParam id: Long,
    ): Map<String, Any> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )

        song?.let {
            val result: MutableList<Map<String, Any>> = mutableListOf()
            for (voice in 0 until song.countVoices) {
                result.add(
                    mapOf(
                        "text" to song.getSourceText(voice),
                        "markers" to song.getSourceMarkers(voice),
                        "syllables" to song.getSourceSyllables(voice),
                        "voice" to voice,
                    ),
                )
            }
            return mapOf("voices" to result)
        }

        return emptyMap()
    }

    @PostMapping("/song/picturealbum")
    @ResponseBody
    fun getPictureAlbum(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            val pic = it.pictureAlbum ?: return ""
            return "/api/picture/file?file=${java.net.URLEncoder.encode(pic.storageFileName, java.nio.charset.StandardCharsets.UTF_8)}"
        }
        return ""
    }

    @PostMapping("/song/pictureauthor")
    @ResponseBody
    fun getPictureAuthor(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            val pic = it.pictureAuthor ?: return ""
            return "/api/picture/file?file=${java.net.URLEncoder.encode(pic.storageFileName, java.nio.charset.StandardCharsets.UTF_8)}"
        }
        return ""
    }

    // Поиск обложки альбома (Яндекс.Музыка → SearXNG-фолбэк), см. AlbumCoverFinder.kt
    @PostMapping("/song/searchalbumcover")
    @ResponseBody
    fun searchAlbumCover(
        @RequestParam id: Long,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) skipYandex: Boolean?,
        @RequestParam(required = false) engine: String?,
    ): AlbumCoverSearchResponseDto {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return AlbumCoverSearchResponseDto(ok = false, message = "Песня не найдена", candidates = emptyList())

        val authorYmId =
            Author
                .getAuthorByName(
                    author = song.author,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )?.ymId

        val defaultQuery = albumCoverService.defaultSearchQuery(song.author, song.album)
        val resolvedEngine = resolveAlbumCoverSearchEngine(engine)

        return when (
            val outcome =
                albumCoverService.search(
                    authorYmId = authorYmId,
                    author = song.author,
                    album = song.album,
                    skipYandex = skipYandex ?: false,
                    customQuery = query,
                    engine = resolvedEngine,
                )
        ) {
            is AlbumCoverSearchOutcome.Found -> {
                val candidates =
                    outcome.candidates.map { candidate ->
                        AlbumCoverCandidateDto(
                            url =
                                "/api/song/albumcoverproxy?url=${java.net.URLEncoder.encode(
                                    candidate.sourceUrl,
                                    java.nio.charset.StandardCharsets.UTF_8,
                                )}",
                            source = candidate.source.name,
                        )
                    }
                AlbumCoverSearchResponseDto(ok = true, message = outcome.note, candidates = candidates, defaultQuery = defaultQuery)
            }
            is AlbumCoverSearchOutcome.NotFound ->
                AlbumCoverSearchResponseDto(ok = false, message = outcome.reason, candidates = emptyList(), defaultQuery = defaultQuery)
        }
    }

    // Same-origin прокси для внешних картинок-кандидатов — нужен, чтобы фронтовый кроппер мог
    // читать пиксели через <canvas> без "tainted canvas" (внешние домены не шлют CORS-заголовки).
    // Используется только из admin-контекста (karaoke-app), не выносить в karaoke-web/публичные модули.
    @GetMapping("/song/albumcoverproxy")
    fun getAlbumCoverProxy(
        @RequestParam url: String,
    ): ResponseEntity<ByteArray> {
        val bytes = downloadImageBytes(url) ?: return ResponseEntity.notFound().build()
        val contentType =
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                MediaType.IMAGE_JPEG
            } else {
                MediaType.IMAGE_PNG
            }
        return ResponseEntity.ok().contentType(contentType).body(bytes)
    }

    // Сохранение выбранной/скадрированной картинки альбома как LogoAlbum.png в папке альбома +
    // инвалидация кэша Pictures/MinIO. Фронт присылает уже скадрированный к 1:1 и смасштабированный
    // до 400x400 PNG (см. AlbumCoverModal.vue); бэкенд defensively досаживает размер на всякий случай.
    @PostMapping("/song/savealbumcover")
    @ResponseBody
    fun saveAlbumCover(
        @RequestParam id: Long,
        @RequestParam imageBase64: String,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return ""

        val decodedBytes =
            try {
                Base64.getDecoder().decode(imageBase64)
            } catch (e: Exception) {
                println("saveAlbumCover: не удалось декодировать base64: ${e.message}")
                return ""
            }

        val finalImage = cropCenterSquareAndResize(decodedBytes, targetSize = 400) ?: return ""

        val targetPath = "${song.rootFolder}/LogoAlbum.png"
        try {
            val file = File(targetPath)
            ImageIO.write(finalImage, "png", file)
            runCommand(listOf("chmod", "666", targetPath))
        } catch (e: Exception) {
            println("saveAlbumCover: не удалось сохранить файл '$targetPath': ${e.message}")
            return ""
        }

        val iosFull = ByteArrayOutputStream()
        ImageIO.write(finalImage, "png", iosFull)
        val finalBase64 = Base64.getEncoder().encodeToString(iosFull.toByteArray())

        // Инвалидация кэша: если запись Pictures с таким именем уже существует — обновляем её
        // (а не создаём новую, чтобы не плодить дубли, см. docs/architecture-notes-archive.md).
        // Если записи ещё нет — song.pictureAlbum сам создаст её из только что записанного файла.
        val existingPicture =
            Pictures.getPictureByName(
                name = song.pictureNameAlbum,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val picture =
            if (existingPicture != null) {
                existingPicture.full = finalBase64
                existingPicture.save()
                existingPicture
            } else {
                song.pictureAlbum
            }

        return picture?.let {
            "/api/picture/file?file=${java.net.URLEncoder.encode(it.storageFileName, java.nio.charset.StandardCharsets.UTF_8)}"
        } ?: ""
    }

    // Получаем дату начала для публикаций
    @PostMapping("/publications/date")
    @ResponseBody
    fun getPublicationsDateFrom(
        @RequestParam param: String,
    ): String {
        val currentCalendar = Calendar.getInstance()
        val currentDateTime = currentCalendar.time

        val formatter = SimpleDateFormat("dd/MM/yyyy")
        val currentDate = formatter.parse(formatter.format(currentDateTime))

        val song =
            Song.loadListFromDb(
                emptyMap(),
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            )
        val sett =
            when (param) {
                "STATE_ALL_DONE" ->
                    song.firstOrNull { it.state == SongState.ALL_DONE }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATE_OVERDUE" ->
                    song.firstOrNull { it.state == SongState.OVERDUE }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATE_TODAY" ->
                    song.firstOrNull {
                        it.dateTimePublish != null &&
                            formatter.parse(formatter.format(it.dateTimePublish)) == currentDate
                    }
                "STATE_ALL_UPLOADED" ->
                    song.firstOrNull { it.state == SongState.ALL_UPLOADED }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATE_WO_TG" ->
                    song.firstOrNull { it.state == SongState.WO_TG }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATE_WO_VK" ->
                    song.firstOrNull { it.state == SongState.WO_VK }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATE_WO_DZEN" ->
                    song.firstOrNull { it.state == SongState.WO_DZEN }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATE_WO_VKG" ->
                    song.firstOrNull { it.state == SongState.WO_VKG }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATUS_0" ->
                    song.firstOrNull { it.state == SongState.IN_WORK && it.idStatus == 0L }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATUS_1" ->
                    song.firstOrNull { it.state == SongState.IN_WORK && it.idStatus == 1L }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATUS_2" ->
                    song.firstOrNull { it.state == SongState.IN_WORK && it.idStatus == 2L }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATUS_3" ->
                    song.firstOrNull { it.state == SongState.IN_WORK && it.idStatus == 3L }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATUS_4" ->
                    song.firstOrNull { it.state == SongState.IN_WORK && it.idStatus == 4L }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                "STATUS_6" ->
                    song.firstOrNull { it.state == SongState.IN_WORK && it.idStatus == 6L }
                        ?: song.firstOrNull {
                            it.dateTimePublish != null &&
                                formatter.parse(
                                    formatter.format(it.dateTimePublish),
                                ) == currentDate
                        }
                else -> null
            } ?: return ""
        return sett.date
    }

    // Сохраняем маркеры для войса
    @PostMapping("/song/savesourcemarkers")
    @ResponseBody
    fun saveSourceMarkers(
        @RequestParam id: Long,
        @RequestParam voice: Int,
        @RequestParam(required = false) sourceMarkers: String = "",
    ): Boolean {
        var result = false
        if (sourceMarkers.trim() != "") {
            val song =
                Song.loadFromDbById(
                    id = id,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            result = song?.let {
                song.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
                val strText = song.convertMarkersToSrt(voice)
                val pathToFile = "${song.rootFolder}/${song.fileName}.voice${voice + 1}.srt"
                File(pathToFile).writeText(strText)
                runCommand(listOf("chmod", "666", pathToFile))
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
        @RequestParam(required = false) sourceText: String = "",
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            song.setSourceText(voice, sourceText)
            song.updateMarkersFromSourceText(voice)
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
        @RequestParam sourceMarkers: String,
        @RequestParam indexTabsVariant: Int,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            try {
                Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers)
            } catch (_: Exception) {
                println("Ошибка при парсинге маркеров.")
                emptyList()
            }
            song.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
            val strText = song.convertMarkersToSrt(voice)
            try {
                val pathToFile = "${song.rootFolder}/${song.fileName}.voice${voice + 1}.srt"
                File(pathToFile).writeText(strText)
                runCommand(listOf("chmod", "666", pathToFile))
            } catch (_: Exception) {
                println("Ошибка при создании файла субтитров.")
            }
            song.setSourceText(voice, sourceText)
            song.setIndexTabsVariant(indexTabsVariant)
            true
        } ?: false
    }

    // Создаём караоке
    @PostMapping("/song/createkaraoke")
    @ResponseBody
    fun getSongCreateKaraoke(
        @RequestParam id: Long,
        @RequestParam(required = false) priorLyrics: String? = "0",
        @RequestParam(required = false) priorKaraoke: String? = "1",
        @RequestParam(required = false) priorChords: String? = "",
        @RequestParam(required = false) priorMelody: String? = "",
        @RequestParam(required = false) priorDemo: String? = "",
        @RequestParam(required = false) threadId: String? = "0",
    ): Boolean {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )

        var type = "danger"
        val head = "Создание караоке"
        var body = "Что-то пошло не так"
        var result = false
        song?.let {
            val createLyrics = priorLyrics != "" && priorLyrics != null
            val createKaraoke = priorKaraoke != "" && priorKaraoke != null
            val createChords = priorChords != "" && priorChords != null
            val createMelody = priorMelody != "" && priorMelody != null
            val createDemo = priorDemo != "" && priorDemo != null

            if (createLyrics) {
                KaraokeProcess.createProcess(
                    song,
                    KaraokeProcessTypes.RENDER_MP4_LYRICS,
                    true,
                    priorLyrics.toInt(),
                    threadId =
                        threadId?.toInt() ?: 0,
                    context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.LYRICS.name),
                )
            }
            if (createKaraoke) {
                KaraokeProcess.createProcess(
                    song,
                    KaraokeProcessTypes.RENDER_MP4_KARAOKE,
                    true,
                    priorKaraoke.toInt(),
                    threadId =
                        threadId?.toInt() ?: 0,
                    context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE.name),
                )
            }
            if (createChords) {
                KaraokeProcess.createProcess(
                    song,
                    KaraokeProcessTypes.RENDER_MP4_CHORDS,
                    true,
                    priorChords.toInt(),
                    threadId =
                        threadId?.toInt() ?: 0,
                    context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.CHORDS.name),
                )
            }
            if (createMelody) {
                KaraokeProcess.createProcess(
                    song,
                    KaraokeProcessTypes.RENDER_MP4_TABS,
                    true,
                    priorMelody.toInt(),
                    threadId =
                        threadId?.toInt() ?: 0,
                    context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.TABS.name),
                )
            }
            if (createDemo) {
                KaraokeProcess.createProcess(
                    song,
                    KaraokeProcessTypes.RENDER_MP4_DEMO,
                    true,
                    priorDemo.toInt(),
                    threadId =
                        threadId?.toInt() ?: 0,
                    context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.DEMO.name),
                )
            }

            type = "info"
            body = "Создание караоке для песни «${it.songName}» прошло успешно."
            result = true
        }
        SNS.send(SseNotification.message(Message(type = type, head = head, body = body)))
        return result
    }

    // Создаём караоке для всех
    @PostMapping("/songs/createkaraokeall")
    @ResponseBody
    fun getSongsCreateKaraokeAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) priorLyrics: String? = "10",
        @RequestParam(required = false) priorKaraoke: String? = "10",
        @RequestParam(required = false) priorChords: String? = "",
        @RequestParam(required = false) priorMelody: String? = "",
        @RequestParam(required = false) priorDemo: String? = "",
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.let {
                    val createLyrics = priorLyrics != "" && priorLyrics != null
                    val createKaraoke = priorKaraoke != "" && priorKaraoke != null
                    val createChords = priorChords != "" && priorChords != null
                    val createMelody = priorMelody != "" && priorMelody != null
                    val createDemo = priorDemo != "" && priorDemo != null

                    if (createLyrics) {
                        KaraokeProcess.createProcess(
                            song,
                            KaraokeProcessTypes.RENDER_MP4_LYRICS,
                            true,
                            priorLyrics.toInt(),
                            threadId =
                                threadId?.toInt() ?: 0,
                            context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.LYRICS.name),
                        )
                    }
                    if (createKaraoke) {
                        KaraokeProcess.createProcess(
                            song,
                            KaraokeProcessTypes.RENDER_MP4_KARAOKE,
                            true,
                            priorKaraoke.toInt(),
                            threadId =
                                threadId?.toInt() ?: 0,
                            context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE.name),
                        )
                    }
                    if (createChords) {
                        KaraokeProcess.createProcess(
                            song,
                            KaraokeProcessTypes.RENDER_MP4_CHORDS,
                            true,
                            priorChords.toInt(),
                            threadId =
                                threadId?.toInt() ?: 0,
                            context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.CHORDS.name),
                        )
                    }
                    if (createMelody) {
                        KaraokeProcess.createProcess(
                            song,
                            KaraokeProcessTypes.RENDER_MP4_TABS,
                            true,
                            priorMelody.toInt(),
                            threadId =
                                threadId?.toInt() ?: 0,
                            context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.TABS.name),
                        )
                    }
                    if (createDemo) {
                        KaraokeProcess.createProcess(
                            song,
                            KaraokeProcessTypes.RENDER_MP4_DEMO,
                            true,
                            priorDemo.toInt(),
                            threadId =
                                threadId?.toInt() ?: 0,
                            context = mapOf("version" to com.svoemesto.karaokeapp.services.RenderVersion.DEMO.name),
                        )
                    }
                }
                result = true
            }
        }
        if (result) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание караоке для всех",
                        body = "Создание караоке для всех прошло успешно",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Создание караоке для всех",
                        body = "Что-то пошло не так",
                    ),
                ),
            )
        }
    }

    // DEMUCS2 для песни
    @PostMapping("/song/demucs2")
    @ResponseBody
    fun doProcessDemucs2(
        @RequestParam id: Long,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(song, KaraokeProcessTypes.RECODE_48000, true, prior)
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.DEMUCS2, true, prior, threadId = threadId?.toInt() ?: 0)
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание DEMUCS2",
                        body = "Создание DEMUCS2 прошло успешно",
                    ),
                ),
            )
            return
        }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "warning",
                    head = "Создание DEMUCS2",
                    body = "Что-то пошло не так",
                ),
            ),
        )
    }

    // DEMUCS2 для всех
    @PostMapping("/songs/createdemucs2all")
    @ResponseBody
    fun getSongsCreateDemucs2All(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.let {
                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.DEMUCS2, true, prior, threadId = threadId?.toInt() ?: 0)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание DEMUCS2",
                        body = "Создание DEMUCS2 прошло успешно",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Создание DEMUCS2",
                        body = "Что-то пошло не так",
                    ),
                ),
            )
        }
    }

    // Точные маркеры (forced-alignment) для песни - фоновый аналог кнопки «Точные маркеры» в
    // SubsEdit (см. Utils.executeForcedAlignMarkers), обрабатывает все голоса песни разом. Нельзя
    // ставить в очередь для песен со статусом idStatus>=4 (маркеры уже расставлены, см.
    // alignment-ml/README.md, specs/022-song-status-lifecycle) - иначе фоновый процесс молча
    // затёр бы уже подтверждённую разметку.
    @PostMapping("/song/forcedalignmarkers")
    @ResponseBody
    fun doProcessForcedAlignMarkers(
        @RequestParam id: Long,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
        @RequestParam(required = false) useFinetunedModel: Boolean?,
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        if (song != null && song.idStatus < 4) {
            val effectiveUseFinetuned = useFinetunedModel ?: KaraokeProperties.getBoolean("alignmentUseFinetunedModel")
            KaraokeProcess.createProcess(
                song,
                KaraokeProcessTypes.FORCED_ALIGN_MARKERS,
                true,
                prior,
                threadId = threadId?.toInt() ?: 0,
                context = mapOf("useFinetunedModel" to effectiveUseFinetuned.toString()),
            )
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Точные маркеры",
                        body = "Создание процесса «Точные маркеры» прошло успешно",
                    ),
                ),
            )
            return
        }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "warning",
                    head = "Точные маркеры",
                    body =
                        if (song == null) {
                            "Что-то пошло не так"
                        } else {
                            "Песня уже имеет статус ${song.idStatus} (маркеры расставлены) - процесс не создан"
                        },
                ),
            ),
        )
    }

    // Точные маркеры (forced-alignment) для всех песен (из текущей выборки) - песни со статусом
    // idStatus>=4 молча пропускаются (см. комментарий у doProcessForcedAlignMarkers выше).
    @PostMapping("/songs/createforcedalignmarkersall")
    @ResponseBody
    fun getSongsCreateForcedAlignMarkersAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
        @RequestParam(required = false) useFinetunedModel: Boolean?,
    ) {
        val effectiveUseFinetuned = useFinetunedModel ?: KaraokeProperties.getBoolean("alignmentUseFinetunedModel")
        var queued = 0
        var skipped = 0
        val ids =
            songsIds
                .split(";")
                .map { it }
                .filter { it != "" }
                .map { it.toLong() }
        ids.forEach { id ->
            val song =
                Song.loadFromDbById(
                    id = id,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            if (song != null && song.idStatus < 4) {
                KaraokeProcess.createProcess(
                    song,
                    KaraokeProcessTypes.FORCED_ALIGN_MARKERS,
                    true,
                    prior,
                    threadId = threadId?.toInt() ?: 0,
                    context = mapOf("useFinetunedModel" to effectiveUseFinetuned.toString()),
                )
                queued++
            } else {
                skipped++
            }
        }
        if (queued > 0) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Точные маркеры",
                        body = "Поставлено в очередь: $queued. Пропущено (статус >= 3): $skipped",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Точные маркеры",
                        body = "Ни одна песня не подошла (статус >= 3 у всех выбранных)",
                    ),
                ),
            )
        }
    }

    // DEMUCS5 для песни
    @PostMapping("/song/demucs5")
    @ResponseBody
    fun doProcessDemucs5(
        @RequestParam id: Long,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(song, KaraokeProcessTypes.RECODE_48000, true, prior)
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.DEMUCS5, true, prior, threadId = threadId?.toInt() ?: 0)
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание DEMUCS5",
                        body = "Создание DEMUCS5 прошло успешно",
                    ),
                ),
            )
            return
        }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "warning",
                    head = "Создание DEMUCS5",
                    body = "Что-то пошло не так",
                ),
            ),
        )
    }

    // DEMUCS5 для всех
    @PostMapping("/songs/createdemucs5all")
    @ResponseBody
    fun getSongsCreateDemucs5All(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.let {
//                    if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(song, KaraokeProcessTypes.RECODE_48000, true, prior)
                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.DEMUCS5, true, prior, threadId = threadId?.toInt() ?: 0)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание DEMUCS5",
                        body = "Создание DEMUCS5 прошло успешно",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Создание DEMUCS5",
                        body = "Что-то пошло не так",
                    ),
                ),
            )
        }
    }

    // SHEETSAGE для песни
    @PostMapping("/song/sheetsage")
    @ResponseBody
    fun doProcessSheetsage(
        @RequestParam id: Long,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            if (File(it.pathToFileSheetsageMIDI).exists()) return
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.SHEETSAGE, true, prior, threadId = threadId?.toInt() ?: 0)
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание SHEETSAGE",
                        body = "Создание SHEETSAGE прошло успешно",
                    ),
                ),
            )
            return
        }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "warning",
                    head = "Создание SHEETSAGE",
                    body = "Что-то пошло не так",
                ),
            ),
        )
    }

    // SHEETSAGE для всех
    @PostMapping("/songs/sheetsageall")
    @ResponseBody
    fun getSongsCreateSheetsageAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.let {
                    if (!File(it.pathToFileSheetsageMIDI).exists()) {
                        KaraokeProcess.createProcess(
                            song,
                            KaraokeProcessTypes.SHEETSAGE,
                            true,
                            prior,
                            threadId = threadId?.toInt() ?: 0,
                        )
                    }
                }
                result = true
            }
        }
        if (result) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание SHEETSAGE",
                        body = "Создание SHEETSAGE прошло успешно",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Создание SHEETSAGE",
                        body = "Что-то пошло не так",
                    ),
                ),
            )
        }
    }

    // Удаляем песню
    @PostMapping("/song/delete")
    @ResponseBody
    fun doDeleteSong(
        @RequestParam id: Long,
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.deleteFromDb()
    }

//    // Создаём MP3 KARAOKE для песни
//    @PostMapping("/song/mp3karaoke")
//    @ResponseBody
//    fun doMP3Karaoke(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
//        val song = Song.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//        song?.doMP3Karaoke(prior, threadId = threadId?.toInt() ?: 0)
//        SNS.send(SseNotification.message(Message(
//            type = "info",
//            head = "Создание MP3 KARAOKE",
//            body = "Создание MP3 KARAOKE прошло успешно"
//        )))
//    }
//
//    // Создаём MP3 KARAOKE для всех
//    @PostMapping("/songs/createmp3karaokeall")
//    @ResponseBody
//    fun getSongsCreateMP3KaraokeAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
//        var result = false
//        songsIds.let {
//            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
//            ids.forEach { id ->
//                val song = Song.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//                song?.doMP3Karaoke(prior, threadId = threadId?.toInt() ?: 0)
//                result = true
//            }
//        }
//        if (result) {
//            SNS.send(SseNotification.message(Message(
//                type = "info",
//                head = "Создание MP3 KARAOKE",
//                body = "Создание MP3 KARAOKE прошло успешно"
//            )))
//        } else {
//            SNS.send(SseNotification.message(Message(
//                type = "warning",
//                head = "Создание MP3 KARAOKE",
//                body = "Что-то пошло не так"
//            )))
//        }
//    }
//
//    // Создаём MP3 LYRICS для песни
//    @PostMapping("/song/mp3lyrics")
//    @ResponseBody
//    fun doMP3Lyrics(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
//        val song = Song.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//        song?.doMP3Lyrics(prior, threadId = threadId?.toInt() ?: 0)
//        SNS.send(SseNotification.message(Message(
//            type = "info",
//            head = "Создание MP3 LYRICS",
//            body = "Создание MP3 LYRICS прошло успешно"
//        )))
//    }
//
//    // Создаём MP3 LYRICS для всех
//    @PostMapping("/songs/createmp3lyricsall")
//    @ResponseBody
//    fun getSongsCreateMP3LyricsAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
//        var result = false
//        songsIds.let {
//            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
//            ids.forEach { id ->
//                val song = Song.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//                song?.doMP3Lyrics(prior, threadId = threadId?.toInt() ?: 0)
//                result = true
//            }
//        }
//        if (result) {
//            SNS.send(SseNotification.message(Message(
//                type = "info",
//                head = "Создание MP3 LYRICS",
//                body = "Создание MP3 LYRICS прошло успешно"
//            )))
//        } else {
//            SNS.send(SseNotification.message(Message(
//                type = "warning",
//                head = "Создание MP3 LYRICS",
//                body = "Что-то пошло не так"
//            )))
//        }
//    }

    // Создаём SYMLINKs для песни
    @PostMapping("/song/symlink")
    @ResponseBody
    fun doSymlink(
        @RequestParam id: Long,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.doSymlink(prior, threadId = threadId?.toInt() ?: 0)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Создание SYMLINK",
                    body = "Создание SYMLINK прошло успешно",
                ),
            ),
        )
    }

    // Создаём картинку BoostyTeaser для песни
    @PostMapping("/song/createpictureboostyteaser")
    @ResponseBody
    fun doCreatePictureBoostyTeaser(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createBoostyTeaserPicture(song)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки BoostyTeaser",
                            body = "Создание картинки BoostyTeaser прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку SponsrTeaser для песни
    @PostMapping("/song/createpicturesponsrteaser")
    @ResponseBody
    fun doCreatePictureSponsrTeaser(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSponsrTeaserPicture(song)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки SponsrTeaser",
                            body = "Создание картинки SponsrTeaser прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку BoostyFiles для песни
    @PostMapping("/song/createpictureboostyfiles")
    @ResponseBody
    fun doCreatePictureBoostyFiles(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createBoostyFilesPicture(song)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки BoostyFiles",
                            body = "Создание картинки BoostyFiles прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку VK для песни
    @PostMapping("/song/createpicturevk")
    @ResponseBody
    fun doCreatePictureVK(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createVKPicture(song)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки VK",
                            body = "Создание картинки VK прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку VKlink для песни
    @PostMapping("/song/createpicturevklink")
    @ResponseBody
    fun doCreatePictureVKlink(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createVKLinkPicture(song)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки VKlink",
                            body = "Создание картинки VKlink прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку LYRICS для песни
    @PostMapping("/song/createpicturelyrics")
    @ResponseBody
    fun doCreatePictureLyrics(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongPicture(song, SongVersion.LYRICS)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки LYRICS",
                            body = "Создание картинки LYRICS прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку KARAOKE для песни
    @PostMapping("/song/createpicturekaraoke")
    @ResponseBody
    fun doCreatePictureKaraoke(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongPicture(song, SongVersion.KARAOKE)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки KARAOKE",
                            body = "Создание картинки KARAOKE прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку CHORDS для песни
    @PostMapping("/song/createpicturechords")
    @ResponseBody
    fun doCreatePictureChords(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongPicture(song, SongVersion.CHORDS)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки CHORDS",
                            body = "Создание картинки CHORDS прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём картинку TABS для песни
    @PostMapping("/song/createpicturetabs")
    @ResponseBody
    fun doCreatePictureTabs(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongPicture(song, SongVersion.TABS)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание картинки TABS",
                            body = "Создание картинки TABS прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём текстовый файл LYRICS для песни
    @PostMapping("/song/createdescriptionfilelyrics")
    @ResponseBody
    fun doCreateDescriptionFileLyrics(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongDescriptionFile(song, SongVersion.LYRICS)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание текстового файла LYRICS",
                            body = "Создание текстового файла LYRICS прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём текстовый файл KARAOKE для песни
    @PostMapping("/song/createdescriptionfilekaraoke")
    @ResponseBody
    fun doCreateDescriptionFileKaraoke(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongDescriptionFile(song, SongVersion.KARAOKE)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание текстового файла KARAOKE",
                            body = "Создание текстового файла KARAOKE прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём текстовый файл CHORDS для песни
    @PostMapping("/song/createdescriptionfilechords")
    @ResponseBody
    fun doCreateDescriptionFileChords(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongDescriptionFile(song, SongVersion.CHORDS)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание текстового файла CHORDS",
                            body = "Создание текстового файла CHORDS прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём текстовый файл TABS для песни
    @PostMapping("/song/createdescriptionfiletabs")
    @ResponseBody
    fun doCreateDescriptionFileTabs(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                createSongDescriptionFile(song, SongVersion.TABS)
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Создание текстового файла TABS",
                            body = "Создание текстового файла TABS прошло успешно",
                        ),
                    ),
                )
            }
    }

    // Создаём SYMLINKs для всех
    @PostMapping("/songs/createsymlinksall")
    @ResponseBody
    fun getSongsCreateSymlinksAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.doSymlink(prior, threadId = threadId?.toInt() ?: 0)
                result = true
            }
        }
        if (result) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание SYMLINKs",
                        body = "Создание SYMLINKs прошло успешно",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Создание SYMLINKs",
                        body = "Что-то пошло не так",
                    ),
                ),
            )
        }
    }

    // SmartCopyAll
    @PostMapping("/songs/smartcopyall")
    @ResponseBody
    fun getSmartCopyAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam smartCopySongVersion: String,
        @RequestParam smartCopySongResolution: String,
        @RequestParam(required = false) smartCopyCreateSubfoldersAuthors: Boolean?,
        @RequestParam(required = false) smartCopyRenameTemplate: String?,
        @RequestParam smartCopyPath: String,
        @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        val versions =
            if (smartCopySongVersion == "ALL") {
                SongVersion.entries
            } else {
                listOf(
                    if (SongVersion.entries
                            .map {
                                it.name
                            }.contains(smartCopySongVersion)
                    ) {
                        SongVersion.valueOf(smartCopySongVersion)
                    } else {
                        SongVersion.KARAOKE
                    },
                )
            }

        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.let {
                    doSmartCopyForVersions(
                        song = it,
                        versions = versions,
                        prior = prior,
                        scResolution = smartCopySongResolution,
                        scCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors ?: false,
                        scRenameTemplate = smartCopyRenameTemplate ?: "",
                        scPath = smartCopyPath,
                        threadId = threadId?.toInt() ?: 0,
                    )
                }
                result = true
            }
        }
        sendSmartCopyResultNotification(result)
    }

    // Общая часть getSmartCopyAll/getSmartCopyPeriodByDay: копирование одного набора Song во всех версиях
    private fun doSmartCopyForVersions(
        song: Song,
        versions: List<SongVersion>,
        prior: Int,
        scResolution: String,
        scCreateSubfoldersAuthors: Boolean,
        scRenameTemplate: String,
        scPath: String,
        threadId: Int,
    ) {
        versions.forEach { scVersion ->
            song.doSmartCopy(
                prior = prior,
                scVersion = scVersion,
                scResolution = scResolution,
                scCreateSubfoldersAuthors = scCreateSubfoldersAuthors,
                scRenameTemplate = scRenameTemplate,
                scPath = scPath,
                threadId = threadId,
            )
        }
    }

    private fun sendSmartCopyResultNotification(success: Boolean) {
        if (success) {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Создание Smart Copy",
                        body = "Создание Smart Copy прошло успешно",
                    ),
                ),
            )
        } else {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "warning",
                        head = "Создание Smart Copy",
                        body = "Что-то пошло не так",
                    ),
                ),
            )
        }
    }

    // SmartCopyAll
    @PostMapping("/songs/smartcopyperodbyday")
    @ResponseBody
    fun getSmartCopyPeriodByDay(
        @RequestParam periodStart: String,
        @RequestParam periodEnd: String,
        @RequestParam smartCopyPathPrefix: String,
    ) {
        val prior: Int = -1
        val versions = SongVersion.entries
        val smartCopySongResolution = "1080p"
        val smartCopyCreateSubfoldersAuthors = false
        val smartCopyRenameTemplate = ""
        val threadId = KaraokeProcess.THREAD_LANE_LIGHT_BACKGROUND

        var result = false

        val formatterDDMMYY = DateTimeFormatter.ofPattern("dd.MM.yy")
        val formatterYYYYDDMM = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val startDate = LocalDate.parse(periodStart, formatterDDMMYY)
        val endDate = LocalDate.parse(periodEnd, formatterDDMMYY)

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val filterString = currentDate.format(formatterDDMMYY)
            val dayFolder = currentDate.format(formatterYYYYDDMM)
            val smartCopyPath = "$smartCopyPathPrefix/$dayFolder"

            val songList =
                Song.loadListFromDb(
                    args = mapOf("publish_date" to filterString),
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            songList.forEach { song ->
                doSmartCopyForVersions(
                    song = song,
                    versions = versions,
                    prior = prior,
                    scResolution = smartCopySongResolution,
                    scCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors,
                    scRenameTemplate = smartCopyRenameTemplate,
                    scPath = smartCopyPath,
                    threadId = threadId,
                )
                result = true
            }

            // Переходим к следующему дню
            currentDate = currentDate.plusDays(1)
        }

        sendSmartCopyResultNotification(result)
    }

    @PostMapping("/song/findsongtext")
    @ResponseBody
    fun getFindSongText(
        @RequestParam id: Long,
    ): List<FindSongResult> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            findSongText(song = song)
        } ?: emptyList()
    }

    // Ищем и возвращаем текст
    @PostMapping("/song/searchsongtext")
    @ResponseBody
    fun getSearchSongText(
        @RequestParam id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            findSongText(song = song, countInResult = 1).firstOrNull()?.findedText ?: ""
        } ?: ""
    }

    // Ищем тексты для всех
    @PostMapping("/songs/searchsongtextall")
    @ResponseBody
    fun getSearchSongTextAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) engine: String?,
        @RequestParam(required = false) forceResearch: Boolean = false,
    ): Boolean {
        val resolvedEngine = resolveLyricsSearchEngine(engine)

        var result = false
        songsIds.let {
            val ids =
                songsIds
                    .split(";")
                    .map { it }
                    .filter { it != "" }
                    .map { it.toLong() }
            ids.forEach { id ->
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                song?.let {
                    println("song.haveSourceText = ${song.haveSourceText}")
                    if (!song.haveSourceText || ids.size == 1) {
                        getLyricsSearch(
                            song = song,
                            lyricsFinderService = lyricsFinderService,
                            engine = resolvedEngine,
                            forceResearch = forceResearch,
                        )
                    }
                }
                result = true
            }
        }
        return result
    }

    // Пакетная автопривязка оригинала по аудио-сверке для всех песен со статусом 1 (TEXT_CREATE) и
    // ненулевым root_id: для каждой находим наиболее похожий по аудио вариант из "семьи" (порог threshold%,
    // по умолчанию 95%), применяем его (текст/маркеры со сдвигом) как выбор в модалке "Похожие версии
    // песни", сохраняем (пересчёт производных полей + .srt) и переводим песню в статус 2 (TEXT_CHECK).
    // Тяжёлая операция (ffmpeg-декод на каждого кандидата) — уходит в фоновый поток, прогресс/итог
    // печатается в консоль и присылается тостом по SSE. Параметр threshold остаётся параметризованным:
    // куратор MAY явно передать ?threshold=<N> для иного значения.
    @PostMapping("/songs/autoassignoriginalall")
    @ResponseBody
    fun autoAssignOriginalAll(
        @RequestParam(required = false) author: String? = null,
        @RequestParam(required = false) threshold: Int = 95,
    ): Boolean {
        val authorFilter = author?.trim()?.takeIf { it.isNotEmpty() }
        thread {
            val ids = mutableListOf<Long>()
            try {
                val connection = WORKING_DATABASE.getConnection()
                if (connection != null) {
                    // Колонка автора в tbl_songs — song_author (не author); сравнение регистронезависимо.
                    val sql =
                        "SELECT id FROM tbl_songs WHERE id_status = 1 AND root_id <> 0" +
                            (if (authorFilter != null) " AND LOWER(song_author) = LOWER(?)" else "") +
                            " ORDER BY id"
                    val ps = connection.prepareStatement(sql)
                    if (authorFilter != null) ps.setString(1, authorFilter)
                    val rs = ps.executeQuery()
                    while (rs.next()) ids.add(rs.getLong("id"))
                    rs.close()
                    ps.close()
                }
            } catch (e: Exception) {
                println("Автопривязка оригинала: ошибка выборки песен — ${e.message}")
            }

            val scope = if (authorFilter != null) "автор «$authorFilter»" else "все авторы"
            println("Автопривязка оригинала ($scope): найдено песен со статусом 1 и root_id<>0: ${ids.size} (порог $threshold%)")
            var matched = 0
            var skipped = 0
            ids.forEachIndexed { index, id ->
                try {
                    val song =
                        Song.loadFromDbById(
                            id = id,
                            database = WORKING_DATABASE,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )
                    if (song == null) {
                        skipped++
                        println("  [${index + 1}/${ids.size}] id=$id — пропущено (не удалось загрузить)")
                        return@forEachIndexed
                    }
                    val result = autoAssignOriginalByWaveform(song, WORKING_DATABASE, storageService, storageApiClient, threshold)
                    if (result.matched) matched++ else skipped++
                    println("  [${index + 1}/${ids.size}] ${songLogLabel(song)} — ${result.reason}")
                } catch (e: Exception) {
                    skipped++
                    println("  [${index + 1}/${ids.size}] id=$id — ошибка: ${e.message}")
                }
            }
            println("Автопривязка оригинала: завершено. Обработано ${ids.size}, привязано $matched, пропущено $skipped.")

            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Автопривязка оригинала ($scope)",
                        body = "Обработано ${ids.size}, привязано $matched, пропущено $skipped (порог $threshold%)",
                    ),
                ),
            )
        }
        return true
    }

    @PostMapping("/song/setpublishdatetimetoauthor")
    @ResponseBody
    fun doSetPublishDateTimeToAuthor(
        @RequestParam id: Long,
        @RequestParam(required = false) skipPublished: Boolean = false,
    ) {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            Song.setPublishDateTimeToAuthor(song, skipPublished = skipPublished)
        }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Даты публикации",
                    body = "Изменение дат публикаций для автора прошло успешно",
                ),
            ),
        )
    }

    // Заменяем символы в тексте
    @PostMapping("/replacesymbolsinsong")
    @ResponseBody
    fun getReplaceSymbolsInSong(
        @RequestParam(required = true) txt: String,
    ): String = replaceSymbolsInSong(txt)

    // Действия со словарями
    @PostMapping("/utils/tfd")
    @ResponseBody
    fun doTextFileDictionary(
        @RequestParam(required = true) dictName: String,
        @RequestParam(required = true) dictValue: String,
        @RequestParam(required = true) dictAction: String,
    ) {
        TextFileDictionary.doAction(dictName, dictAction, listOf(dictValue))
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Действия со словарями",
                    body = "Действие «$dictAction», словарь «$dictName», слово «$dictValue» прошло успешно",
                ),
            ),
        )
    }

    // Разовый импорт значений словарей из старых текстовых файлов (/sm-karaoke/system/*.txt) в
    // tbl_dictionaries. Идемпотентно — повторный вызов не создаёт дублей (UNIQUE-индекс).
    @PostMapping("/dictionaries/importfromfiles")
    @ResponseBody
    fun doImportDictionariesFromFiles(): Map<String, Int> {
        val filesByDictName =
            mapOf(
                "Слова с Ё" to YO_FILE_PATH,
                "Censored" to CENSORED_FILE_PATH,
                "Sync Ids" to SYNCIDS_FILE_PATH,
            )
        val result =
            filesByDictName.mapValues { (dictName, filePath) ->
                Dictionary.importFromFile(dictName = dictName, filePath = filePath, database = WORKING_DATABASE)
            }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Импорт словарей",
                    body = "Импортировано новых значений: " + result.entries.joinToString(", ") { "${it.key} — ${it.value}" },
                ),
            ),
        )
        return result
    }

    // Обновляем одну картинку в RemoteDatabase
    @PostMapping("/utils/updateremotepicturefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemotePictureFromLocalDatabase(
        @RequestParam(required = true) id: Long,
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) = updateRemotePictureFromLocalDatabase(id)
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Обновляем одну песню в RemoteDatabase
    @PostMapping("/utils/updateremotesongfromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteSongFromLocalDatabase(
        @RequestParam(required = true) id: Long,
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) = updateRemoteSongFromLocalDatabase(id)
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Добавляем одну песню в SYNC-таблицу
    @PostMapping("/utils/tosync")
    @ResponseBody
    fun doSetSongToSyncRemoteTable(
        @RequestParam(required = true) id: Long,
    ) {
        setSongToSyncRemoteTable(id)
        val body = "Запись ${Song.loadFromDbById(
            id = id,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )?.fileName} добавлена в SYNC-таблицу"
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "SYNC",
                    body = body,
                ),
            ),
        )
        println(body)
    }

    // Обновляем RemoteDatabase
    @PostMapping("/utils/updateremotedatabasefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteDatabaseFromLocalDatabase(
        @RequestParam(required = false) updateSongs: Boolean = true,
        @RequestParam(required = false) updatePictures: Boolean = true,
        @RequestParam(required = false) updateAuthors: Boolean = true,
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) =
            updateRemoteDatabaseFromLocalDatabase(
                updateSongs = updateSongs,
                updatePictures = updatePictures,
                updateAuthors = updateAuthors,
            )
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Обновляем LocalDatabase
    @PostMapping("/utils/updatelocaldatabasefromremotedatabase")
    @ResponseBody
    fun doUpdateLocalDatabaseFromRemoteDatabase(
        @RequestParam(required = false) updateSongs: Boolean = true,
        @RequestParam(required = false) updatePictures: Boolean = true,
        @RequestParam(required = false) updateAuthors: Boolean = true,
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) =
            updateLocalDatabaseFromRemoteDatabase(
                updateSongs = updateSongs,
                updatePictures = updatePictures,
                updateAuthors = updateAuthors,
            )
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Универсальная синхронизация LOCAL<->SERVER (webvue3, раздел "Синхронизация") — по любой
    // сущности SyncRegistry (Song/Pictures/Authors/SiteUsers/Events), в любую сторону, с проверкой
    // разрешения через sync_<key>_push/pull_allowed (см. KaraokeProperties.kt).
    private fun syncEntityInfo(target: SyncTarget<*>): SyncEntityInfoDto =
        SyncEntityInfoDto(
            key = target.key,
            displayName = target.displayName,
            allowPush = target.isAllowed(SyncDirection.LOCAL_TO_SERVER),
            allowPull = target.isAllowed(SyncDirection.SERVER_TO_LOCAL),
            oneClickDirection = target.oneClickDirection.name,
            pushInsert = target.isOperationAllowed(SyncDirection.LOCAL_TO_SERVER, SyncOperation.INSERT),
            pushUpdate = target.isOperationAllowed(SyncDirection.LOCAL_TO_SERVER, SyncOperation.UPDATE),
            pushDelete = target.isOperationAllowed(SyncDirection.LOCAL_TO_SERVER, SyncOperation.DELETE),
            pushMove = target.isOperationAllowed(SyncDirection.LOCAL_TO_SERVER, SyncOperation.MOVE),
            pullInsert = target.isOperationAllowed(SyncDirection.SERVER_TO_LOCAL, SyncOperation.INSERT),
            pullUpdate = target.isOperationAllowed(SyncDirection.SERVER_TO_LOCAL, SyncOperation.UPDATE),
            pullDelete = target.isOperationAllowed(SyncDirection.SERVER_TO_LOCAL, SyncOperation.DELETE),
            pullMove = target.isOperationAllowed(SyncDirection.SERVER_TO_LOCAL, SyncOperation.MOVE),
        )

    @GetMapping("/sync/entities")
    @ResponseBody
    fun getSyncEntities(): List<SyncEntityInfoDto> = SyncRegistry.all.map { syncEntityInfo(it) }

    // Переключение одного флага операции (сущность × направление × операция). Наименование ключа
    // KaraokeProperty инкапсулировано в бэкенде (operationPropertyKey) — фронт шлёт только семантику.
    @PostMapping("/sync/setflag")
    @ResponseBody
    fun postSyncSetFlag(
        @RequestParam(required = true) key: String,
        @RequestParam(required = true) direction: String,
        @RequestParam(required = true) operation: String,
        @RequestParam(required = true) value: Boolean,
    ): ResponseEntity<Any> {
        val target =
            SyncRegistry.byKey(key)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "unknown_key"))
        val syncDirection =
            when (direction) {
                "PUSH" -> SyncDirection.LOCAL_TO_SERVER
                "PULL" -> SyncDirection.SERVER_TO_LOCAL
                else -> return ResponseEntity.badRequest().body(mapOf("error" to "unknown_direction"))
            }
        val op =
            when (operation) {
                "INSERT" -> SyncOperation.INSERT
                "UPDATE" -> SyncOperation.UPDATE
                "DELETE" -> SyncOperation.DELETE
                "MOVE" -> SyncOperation.MOVE
                else -> return ResponseEntity.badRequest().body(mapOf("error" to "unknown_operation"))
            }
        KaraokeProperties.set(target.operationPropertyKey(syncDirection, op), value)
        return ResponseEntity.ok(syncEntityInfo(target))
    }

    @PostMapping("/sync/run")
    @ResponseBody
    fun postSyncRun(
        @RequestParam(required = true) key: String,
        @RequestParam(required = true) direction: String,
        @RequestParam(required = false) id: Long? = null,
    ): ResponseEntity<Any> {
        val target =
            SyncRegistry.byKey(key)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "unknown_key"))
        val syncDirection =
            when (direction) {
                "PUSH" -> SyncDirection.LOCAL_TO_SERVER
                "PULL" -> SyncDirection.SERVER_TO_LOCAL
                else -> return ResponseEntity.badRequest().body(mapOf("error" to "unknown_direction"))
            }
        if (!target.isAllowed(syncDirection)) {
            return ResponseEntity.status(403).body(
                mapOf(
                    "error" to "sync_not_allowed",
                    "message" to "Синхронизация «${target.displayName}» в этом направлении запрещена настройками",
                ),
            )
        }
        val result = runEntitySync(key = target.key, direction = syncDirection, id = id)
        val (created, updated, deleted, moved) = result
        if (created.size + updated.size + deleted.size + moved.size != 0) {
            SNS.send(SseNotification.crud(listOf(created, updated, deleted)))
        }
        notifyStatsDirtyIfSongsPushed(target.key, syncDirection, created.size + updated.size)
        return ResponseEntity.ok(SyncRunResultDto(created, updated, deleted, moved))
    }

    @PostMapping("/sync/oneclick")
    @ResponseBody
    fun postSyncOneClick(): List<SyncOneClickResultDto> =
        SyncRegistry.all.map { target ->
            val direction = target.oneClickDirection
            if (!target.isAllowed(direction)) {
                SyncOneClickResultDto(
                    key = target.key,
                    displayName = target.displayName,
                    direction = direction.name,
                    skipped = true,
                    created = emptyList(),
                    updated = emptyList(),
                    deleted = emptyList(),
                    moved = emptyList(),
                )
            } else {
                val (created, updated, deleted, moved) = runEntitySync(key = target.key, direction = direction)
                if (created.size + updated.size + deleted.size + moved.size != 0) {
                    SNS.send(SseNotification.crud(listOf(created, updated, deleted)))
                }
                notifyStatsDirtyIfSongsPushed(target.key, direction, created.size + updated.size)
                SyncOneClickResultDto(
                    key = target.key,
                    displayName = target.displayName,
                    direction = direction.name,
                    skipped = false,
                    created = created,
                    updated = updated,
                    deleted = deleted,
                    moved = moved,
                )
            }
        }

    // Добавление файлов из папки
    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String,
    ) {
        val importResult =
            Song.createFromPath(
                folder,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val createdList = importResult.addedSongs
        createdList.forEach { newSong ->
            try {
                var textResolved = false

                val original =
                    findDuplicateOriginal(
                        newSong,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                if (original != null) {
                    applyDuplicateOriginal(newSong, original)
                    textResolved = true
                }

                // Поиск аудио-родителя (по звучанию) - независимо от результата обычного поиска по названию.
                // Если найден (сходство >= AUDIO_PARENT_THRESHOLD, т.е. >= 95%) и уже полностью "готов"
                // (idStatus >= 6) - его маркеры точнее/полнее, применяем их со сдвигом и переводим песню
                // в статус 5 (MARKERS_CHECK, предфинальная вычитка куратором) — перекрывая более слабый
                // статус 1 обычного родителя. Статус 6 (READY) НЕ ставится автоматически: акустическая
                // сверка не гарантирует идеального совпадения таймлайнов (FR-003 spec.md).
                val audioParentResult =
                    findAudioParentByWaveform(
                        newSong,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                if (audioParentResult.matched && audioParentResult.bestId != null && audioParentResult.deltaMs != null) {
                    val audioParent =
                        Song.loadFromDbById(
                            id = audioParentResult.bestId,
                            database = WORKING_DATABASE,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )
                    if (audioParent != null && audioParent.idStatus >= 6) {
                        applyAudioParentMarkers(newSong, audioParent, audioParentResult.deltaMs)
                        textResolved = true
                    }
                }

                // Поиск текста на Яндекс.Музыке (см. YandexLyricsFinder.kt) - только если текст ещё не
                // получен ни родителем, ни аудио-родителем. Синхронно (не в фоне): один Playwright-прогон,
                // не гонка нескольких потоков над одним newSong, как и предыдущие два шага.
                if (!textResolved) {
                    val yandexLyricsResult =
                        try {
                            findYandexSongLyrics(newSong.author, newSong.songName)
                        } catch (e: Exception) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] doCreateFromFolder - ошибка поиска текста на Яндекс.Музыке для песни id=${newSong.id}: ${e.message}",
                            )
                            null
                        }
                    if (yandexLyricsResult is YandexLyricsSearchOutcome.Found && yandexLyricsResult.text.isNotBlank()) {
                        newSong.sourceText = yandexLyricsResult.text
                        if (newSong.idStatus == 0L) newSong.fields[SongField.ID_STATUS] = "1"
                        newSong.saveToDb()
                        textResolved = true
                    }
                }

                // Фоновый интернет-поиск текста (SearXNG) - только если текст так и не был получен ни одним
                // из предыдущих способов (заодно исключает гонку нескольких фоновых потоков над одним newSong).
                // Через ограниченный по конкурентности lyricsSearchExecutor (не kotlin.concurrent.thread) -
                // на массовом импорте не должно запускаться по одному потоку на каждую песню без текста.
                if (!textResolved) {
                    lyricsSearchExecutor.submit {
                        try {
                            getLyricsSearch(
                                song = newSong,
                                lyricsFinderService = lyricsFinderService,
                                engine = resolveLyricsSearchEngine(),
                            )
                        } catch (e: Exception) {
                            println(
                                "[${Timestamp.from(
                                    Instant.now(),
                                )}] doCreateFromFolder - ошибка фонового поиска текста для песни id=${newSong.id}: ${e.message}",
                            )
                        }
                    }
                }

                // Repair-All-эквивалент для только что созданной песни: записи tbl_pictures для автора/альбома
                // (если их ещё нет, + загрузка full/preview в хранилище) и самопродолжающийся каскад
                // HealthReport (стемы mp3, превью автора/альбома, загрузка всех файлов в локальное/удалённое
                // хранилище) - тот же вызов, что делает кнопка Repair All на карточке песни.
                newSong.pictureAuthor
                newSong.pictureAlbum
                HealthReport.startRepairAll(newSong, WORKING_DATABASE, storageService, storageApiClient)
            } catch (e: Exception) {
                println(
                    "[${Timestamp.from(Instant.now())}] doCreateFromFolder - ошибка постобработки песни id=${newSong.id}: ${e.message}",
                )
            }
        }
        val result = createdList.size
        val skipped = importResult.skippedFilesCount
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Добавление файлов из папки",
                    body = "Добавлено файлов из папки «$folder»: $result (пропущено: $skipped)",
                ),
            ),
        )
    }

    // Создание картинок Dzen для папки
    @PostMapping("/utils/createdzenpicturesforfolder")
    @ResponseBody
    fun doCreateDzenPicturesForFolder(
        @RequestParam(required = true) folder: String,
    ) {
        createDzenPicture(folder)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Создание картинок Dzen для папки",
                    body = "Создание картинок Dzen для папки «$folder» прошло успешно",
                ),
            ),
        )
    }

    @PostMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(
        @RequestParam(required = false) songsIds: String = "",
        @RequestParam(required = false) priorLyrics: Int = 10,
        @RequestParam(required = false) priorKaraoke: Int = 10,
        @RequestParam(required = false) threadId: String? = "0",
    ): Any {
        val songList =
            if (songsIds == "") {
                Song.loadListFromDb(
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            } else {
                val ids =
                    songsIds
                        .split(";")
                        .map { it }
                        .filter { it != "" }
                        .map { it.toLong() }
                val result: MutableList<Song> = mutableListOf()
                ids.forEach { id ->
                    val song =
                        Song.loadFromDbById(
                            id = id,
                            database = WORKING_DATABASE,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )
                    song?.let { result.add(it) }
                }
                result.toList()
            }

        val (countCopy, countCode) =
            collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(
                songList = songList,
                priorLyrics = priorLyrics,
                priorKaraoke = priorKaraoke,
                threadId = threadId?.toInt() ?: 0,
            )
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Обновление хранилища",
                    body = "Скопировано песен в хранилище: $countCopy, создано заданий на кодирование: $countCode",
                ),
            ),
        )
        return listOf(countCopy, countCode)
    }

    // Обновить пустые BPM и KEY из фалов CSV
    @PostMapping("/utils/updatebpmandkey")
    @ResponseBody
    fun doUpdateBpmAndKey() {
        val result = updateBpmAndKey(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Обновление BPM и KEY из фалов CSV",
                    body = "Обновлено пустых BPM и KEY из фалов CSV: $result",
                ),
            ),
        )
    }

    // Обновить пустые BPM и KEY из фалов LV
    @PostMapping("/utils/updatebpmandkeylv")
    @ResponseBody
    fun doUpdateBpmAndKeyLV() {
        val (resultSuccess, resultFailed) =
            updateBpmAndKeyLV(
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Обновление BPM и KEY из файлов LV",
                    body =
                        "Обновлено пустых BPM и KEY из файлов LV: $resultSuccess" +
                            if (resultFailed == 0) "" else ", Не удалось обновить файлов: $resultFailed",
                ),
            ),
        )
    }

    // Найти и пометить дубликаты песен автора
    @PostMapping("/utils/markdublicates")
    @ResponseBody
    fun doMarkDublicates(
        @RequestParam(required = true) author: String,
    ) {
        val result =
            findAndFillDublicates(author, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Нахождение дубликатов",
                    body = "Найдено и обработано дубликатов: $result",
                ),
            ),
        )
    }

    // Разовый backfill персистентных флагов готовности плеера (deploy/karaoke-db/26_player_readiness_flags.sql)
    // для уже существующих песен — новые колонки создаются с DEFAULT false. author не передаётся
    // (или пустой) → обработка всех авторов (по образцу autoAssignOriginalAll ниже). Тяжёлая операция
    // (полный HealthReport-скан на каждую песню) — уходит в фоновый поток, итог приходит тостом по SSE.
    @PostMapping("/utils/recalcplayerreadiness")
    @ResponseBody
    fun doRecalcPlayerReadiness(
        @RequestParam(required = false) author: String? = null,
    ): Boolean {
        val authorFilter = author?.trim()?.takeIf { it.isNotEmpty() }
        thread {
            val scope = if (authorFilter != null) "автор «$authorFilter»" else "все авторы"
            println("Пересчёт готовности плеера ($scope): начало")
            // specs/100-fix-recalc-readiness-progress-resilience: без этого try/catch необработанное
            // исключение в этом потоке (например, из SNS.send) тихо убивает фоновый поток — ни строки
            // "завершено", ни тоста, ни ошибки в логе; администратор не может отличить "ещё считает"
            // от "давно упало".
            try {
                val result =
                    HealthReport.recalculatePlayerReadiness(
                        authorFilter,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                println("Пересчёт готовности плеера ($scope): завершено, проверено песен: $result")
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Пересчёт готовности плеера ($scope)",
                            body = "Проверено песен: $result",
                        ),
                    ),
                )
            } catch (e: Exception) {
                println("Пересчёт готовности плеера ($scope): аварийно прерван: ${e.message}")
                try {
                    SNS.send(
                        SseNotification.error(
                            Message(
                                type = "error",
                                head = "Пересчёт готовности плеера ($scope)",
                                body = "Прервано ошибкой: ${e.message}",
                            ),
                        ),
                    )
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    // Разовый backfill флага «доступна для новости» (specs/101-song-news-flag, FR-012 spec.md) —
    // выставляет Song.newsAvailableAnnounced=true (через обычный saveToDb(), не raw SQL) для песен,
    // уже удовлетворяющих Song.isContentReady на момент включения фичи, БЕЗ создания новости — иначе
    // первая же обычная синхронизация такой песни создала бы новость «доступна» из ничего. Нужно
    // выполнить ОТДЕЛЬНО на LOCAL и на REMOTE (target) ДО очистки tbl_news/удаления
    // tbl_song_news_announced (см. quickstart.md, Шаг 0-1) — иначе серверное «до»-значение флага
    // останется false и следующая обычная синхронизация уже готовой песни создаст новость из ничего
    // (см. research.md фичи 101, п.3). Тяжёлая операция — уходит в фоновый поток, по образцу
    // doRecalcPlayerReadiness выше, итог приходит тостом по SSE.
    @PostMapping("/utils/backfillnewsavailable")
    @ResponseBody
    fun doBackfillNewsAvailable(
        @RequestParam(required = false) target: String? = null,
    ): Boolean {
        val database = if (target == "remote") Connection.remote() else Connection.local()
        thread {
            println("Backfill флага «доступна для новости» (${database.name}): начало")
            try {
                val result =
                    SongReleaseAnnouncementService.backfillNewsAvailableFlag(
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                println("Backfill флага «доступна для новости» (${database.name}): завершено, затронуто песен: $result")
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Backfill флага «доступна» (${database.name})",
                            body = "Затронуто песен: $result",
                        ),
                    ),
                )
            } catch (e: Exception) {
                println("Backfill флага «доступна для новости» (${database.name}): аварийно прерван: ${e.message}")
                try {
                    SNS.send(
                        SseNotification.error(
                            Message(
                                type = "error",
                                head = "Backfill флага «доступна» (${database.name})",
                                body = "Прервано ошибкой: ${e.message}",
                            ),
                        ),
                    )
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    // Разовый backfill ПОЛНОГО complete-набора флагов публикации (specs/124-news-flags-backfill) —
    // проставляет готовым песням на LOCAL: newsAvailableAnnounced=true, newsPremiumPublishPending=false,
    // newsPremiumTelegramSent=true, newsPremiumVkSent=true, premiumAutoPublishState="COMPLETE",
    // premiumAutoPublishLastError="", premiumAttemptCount=0. Без этого первая же правка oldSong.saveToDb()
    // после развёртывания feature 122 триггерила бы в markNewsAvailableIfReady Block 2 переход
    // newsPremiumPublishPending false→true + state=RUNNING — а за ним PremiumAutoPublishScheduler
    // запустил бы автопубликацию в TG+VK для 15000 песен разом (лавина). Backfill явно помечает
    // «уже-опубликовано и завершено», минуя state=RUNNING. Действует только на LOCAL (target=remote
    // принимается для симметрии с соседним /backfillnewsavailable, но НЕ рекомендуется — флаги должны
    // приехать на PROD через обычный sync LOCAL→PROD с активным kill-switch'ом newsAutoPublishKillSwitch,
    // иначе синхронизация создаст лавину auto-новостей). `dryRun=true` → без записи, только отчёт.
    // Тяжёлая операция — уходит в фоновый поток, итог приходит тостом по SSE (с многострочным body).
    @PostMapping("/utils/backfillpublishflags")
    @ResponseBody
    fun doBackfillPublishFlags(
        @RequestParam(required = false) target: String? = null,
        @RequestParam(required = false, defaultValue = "false") dryRun: Boolean = false,
    ): Boolean {
        val database = if (target == "remote") Connection.remote() else Connection.local()
        thread {
            println("Backfill флагов публикации (${database.name}, dryRun=$dryRun): начало")
            try {
                val report =
                    SongReleaseAnnouncementService.backfillPublishFlags(
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                        dryRun = dryRun,
                    )
                println("Backfill флагов публикации (${database.name}, dryRun=$dryRun): ${report.toBody().replace("\n", " | ")}")
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "info",
                            head = "Backfill флагов публикации (${database.name})",
                            body = report.toBody(),
                        ),
                    ),
                )
            } catch (e: Exception) {
                println("Backfill флагов публикации (${database.name}, dryRun=$dryRun): аварийно прерван: ${e.message}")
                try {
                    SNS.send(
                        SseNotification.error(
                            Message(
                                type = "error",
                                head = "Backfill флагов публикации (${database.name})",
                                body = "Прервано ошибкой: ${e.message}",
                            ),
                        ),
                    )
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    // Массовая очистка результатов поиска текста для уже готовых песен (статус ≥3) — backfill
    // для песен, ставших готовыми ДО появления автоочистки в Song.saveToDb() (см. HealthReport.kt,
    // specs/015-search-engine-selection). Тяжёлая операция — уходит в фоновый поток, по образцу
    // doRecalcPlayerReadiness выше, итог приходит тостом по SSE.
    @PostMapping("/utils/deletesearchresultsforreadysongs")
    @ResponseBody
    fun doDeleteSearchResultsForReadySongs(): Boolean {
        thread {
            println("Удаление результатов поиска готовых песен: начало")
            val result =
                HealthReport.deleteSearchResultsForReadySongs(
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            println("Удаление результатов поиска готовых песен: завершено, обработано песен: $result")
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Удаление результатов поиска готовых песен",
                        body = "Обработано песен: $result",
                    ),
                ),
            )
        }
        return true
    }

    // Удалить дубликаты
    @PostMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates() {
        val result = delDublicates(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Удаление дубликатов",
                    body = "Удалено дубликатов: $result",
                ),
            ),
        )
    }

    // Очистить информацию о пре-дубликатах
    @PostMapping("/utils/clearpredublicates")
    @ResponseBody
    fun doClearPreDublicates() {
        val result = clearPreDublicates(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Очистка пре-дубликатов",
                    body = "Очищено пре-дубликатов: $result",
                ),
            ),
        )
    }

    // Выполнить Custom Function
    @PostMapping("/utils/customfunction")
    @ResponseBody
    fun doCustomFunction() {
        val result =
            customFunction(storageService = storageService, storageApiClient = storageApiClient, lyricsFinderService = lyricsFinderService)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Custom Function",
                    body = "CustomFunction выполнена с результатом: «$result»",
                ),
            ),
        )
    }

    // Экспорт манифеста для дообучения forced-alignment модели (см. alignment-ml/, ExportAlignmentDataset.kt)
    @PostMapping("/utils/exportalignmentdataset")
    @ResponseBody
    fun doExportAlignmentDataset() {
        val result = exportAlignmentDataset(storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Экспорт датасета для forced-alignment",
                    body = result,
                ),
            ),
        )
    }

    // Актуализация VKLinkPictureWeb
//    @PostMapping("/utils/actualizevklinkpictureweb")
//    @ResponseBody
//    fun doActualizeVKLinkPictureWeb() {
//        var cntSkip = 0
//        var cntDelete = 0
//        var cntCreate = 0
//
//        Song.loadListFromDb(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true).forEach { song ->
//            when (createVKLinkPictureWeb(song, false)) {
//                "delete" -> cntDelete++
//                "skip" -> cntSkip++
//                else -> cntCreate++
//            }
//        }
//
//        SNS.send(SseNotification.message(Message(
//            type = "info",
//            head = "Актуализация VKLinkPictureWeb",
//            body = "Актуализация VKLinkPictureWeb выполнена с результатом: создано картинок - $cntCreate, удалено картинок - $cntDelete, пропущено картинок - $cntSkip"
//        )))
//    }

//    @PostMapping("/utils/checklastalbumym")
//    @ResponseBody
//    fun doCheckLastAlbumYm() {
//        val result = checkLastAlbumYm()
//        SNS.send(SseNotification.message(Message(
//            type = "info",
//            head = "Поиск новых альбомов",
//            body = result
//        )))
//
//    }

    @PostMapping("/processes/deletedone")
    @ResponseBody
    fun doProcessDeleteDone() {
        KaraokeProcess.deleteDone(WORKING_DATABASE)
    }

    @PostMapping("/processes/workerstatus")
    @ResponseBody
    fun getProcessWorkerStatus(): Map<String, Any> =
        mapOf("isWork" to KaraokeProcessWorker.isWork, "stopAfterThreadIsDone" to KaraokeProcessWorker.stopAfterThreadIsDone)

    @PostMapping("/processes/workerstartstop")
    @ResponseBody
    fun getProcessWorkerStartStop() {
        if (KaraokeProcessWorker.isWork) {
            KaraokeProcessWorker.stop()
        } else {
            KaraokeProcessWorker.start(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        }
    }

    // Принудительная (жёсткая) остановка очереди — по двойному клику на задизейбленную кнопку старт/стоп
    // во время мягкого ожидания: убивает docker-контейнеры выполняющихся заданий, возвращает их в WAITING.
    @PostMapping("/processes/workerforcestop")
    @ResponseBody
    fun getProcessWorkerForceStop() {
        KaraokeProcessWorker.forceStop()
    }

    @GetMapping("/subscribe")
    fun subscribeSse(
        @RequestParam(required = false) tabId: String?,
        response: HttpServletResponse,
    ): SseEmitter {
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
        response.setHeader("X-Accel-Buffering", "no")

        // Каждая вкладка браузера присылает свой tabId (см. getTabId() в webvue3/src/lib/utils.js) -
        // так у каждой вкладки своё независимое SSE-соединение и она получает все broadcast-события.
        // Fallback на случайный UUID - для старых клиентов без tabId в запросе.
        val realTabId = if (tabId.isNullOrBlank()) UUID.randomUUID().toString() else tabId

        return sseNotificationService.subscribe(1L, realTabId)
    }

    // Получаем properties
    @PostMapping("/properties/getproperties")
    @ResponseBody
    fun getProperties(): Map<String, Any> = mapOf("properties" to KaraokeProperties.getDTOs())

    // Получаем property
    @PostMapping("/properties/getproperty")
    @ResponseBody
    fun getProperty(
        @RequestParam key: String,
    ): Map<String, Any> = mapOf("property" to KaraokeProperties.getDTO(key))

    // Изменяем property
    @PostMapping("/properties/setproperty")
    @ResponseBody
    fun setProperty(
        @RequestParam key: String,
        @RequestParam stringValue: String,
    ): Map<String, Any> {
        KaraokeProperties.setFromString(key, stringValue)
        if (key == "resourceLimitsEnabled" || key.startsWith("cpuLimitPercent")) {
            applyLiveCpuLimitToRunningProcesses()
        }
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "SET PROPERTY",
                    body = "Свойство «$key» установлено в значение «$stringValue»",
                ),
            ),
        )
        return getProperty(key)
    }

    // Изменяем property к значению по умолчанию
    @PostMapping("/properties/setpropertydefault")
    @ResponseBody
    fun setPropertyDefault(
        @RequestParam key: String,
    ) {
        KaraokeProperties.setDefault(key)
        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "SET PROPERTY",
                    body = "Свойство «$key» установлено в значение по умолчанию",
                ),
            ),
        )
    }

    @PostMapping("/propertiesdigests")
    @ResponseBody
    fun apisPropertiesDigest(
        @RequestParam(required = false) filterKey: String?,
        @RequestParam(required = false) filterValue: String?,
        @RequestParam(required = false) filterDefaultValue: String?,
        @RequestParam(required = false) filterDescription: String?,
        @RequestParam(required = false) filterType: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterKey?.let { if (filterKey != "") args["key"] = filterKey }
        filterValue?.let { if (filterValue != "") args["value"] = filterValue }
        filterDefaultValue?.let { if (filterDefaultValue != "") args["default_value"] = filterDefaultValue }
        filterDescription?.let { if (filterDescription != "") args["description"] = filterDescription }
        filterType?.let { if (filterType != "") args["type"] = filterType }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "propertiesDigests" to KaraokeProperties.loadList(args),
            "types" to KaraokeProperties.types(),
        )
    }

    @PostMapping("/authors/updateauthor")
    @ResponseBody
    fun apisUpdateAuthor(
        @RequestParam(required = true) id: Long,
        @RequestParam(required = true) author: String,
        @RequestParam(required = true) ymId: String,
        @RequestParam(required = true) vkId: String,
        @RequestParam(required = true) lastAlbumYm: String,
        @RequestParam(required = true) lastAlbumVk: String?,
        @RequestParam(required = true) lastAlbumProcessed: String,
        @RequestParam(required = true) watched: Boolean,
        @RequestParam(required = true) skip: Boolean,
        @RequestParam(required = false) aliases: String?,
        @RequestParam(required = false) isSpecialOrder: Boolean?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) shortDescription: String?,
        @RequestParam(required = false) warning: String?,
    ): Long {
        Author
            .getAuthorById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let {
                it.author = author
                it.ymId = ymId
                it.vkId = vkId
                it.lastAlbumYm = lastAlbumYm
                it.lastAlbumVk = lastAlbumVk ?: ""
                it.lastAlbumProcessed = lastAlbumProcessed
                it.watched = watched
                it.skip = skip
                aliases?.let { a -> it.aliases = a }
                isSpecialOrder?.let { v -> it.isSpecialOrder = v }
                description?.let { v -> it.description = v }
                shortDescription?.let { v -> it.shortDescription = v }
                warning?.let { v -> it.warning = v }
                it.save()
                return id
            }
        return 0L
    }

    @PostMapping("/authors/authorsdigests")
    @ResponseBody
    fun apisAuthorsDigest(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterAuthor: String?,
        @RequestParam(required = false) filterYmId: String?,
        @RequestParam(required = false) filterVkId: String?,
        @RequestParam(required = false) filterLastAlbumYm: String?,
        @RequestParam(required = false) filterLastAlbumVk: String?,
        @RequestParam(required = false) filterLastAlbumProcessed: String?,
        @RequestParam(required = false) filterWatched: String?,
        @RequestParam(required = false) filterHaveNewAlbum: String?,
        @RequestParam(required = false) filterSkip: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterAuthor?.let { if (filterAuthor != "") args["author"] = filterAuthor }
        filterYmId?.let { if (filterYmId != "") args["ym_id"] = filterYmId }
        filterVkId?.let { if (filterVkId != "") args["vk_id"] = filterVkId }
        filterLastAlbumYm?.let { if (filterLastAlbumYm != "") args["last_album_ym"] = filterLastAlbumYm }
        filterLastAlbumVk?.let { if (filterLastAlbumVk != "") args["last_album_vk"] = filterLastAlbumVk }
        filterLastAlbumProcessed?.let { if (filterLastAlbumProcessed != "") args["last_album_processed"] = filterLastAlbumProcessed }
        filterWatched?.let { if (filterWatched != "") args["watched"] = filterWatched }
        filterHaveNewAlbum?.let { if (filterHaveNewAlbum != "") args["haveNewAlbum"] = filterHaveNewAlbum }
        filterSkip?.let { if (filterSkip != "") args["skip"] = filterSkip }
        val authorsList =
            Author
                .loadList(
                    whereArgs = args,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = true,
                ).map { it.toDTO() }
                .sorted()

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "authorsDigests" to authorsList,
        )
    }

    @PostMapping("/albums/albumsdigests")
    @ResponseBody
    fun apisAlbumsDigest(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterAuthorId: String?,
        @RequestParam(required = false) filterAuthorName: String?,
        @RequestParam(required = false) filterYear: String?,
        @RequestParam(required = false) filterName: String?,
        @RequestParam(required = false) filterAlbumType: String?,
        @RequestParam(required = false) filterSongsCountMin: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterAuthorId?.let { if (filterAuthorId != "") args["author_id"] = filterAuthorId }
        // Автор фильтруется по точному имени (как и в Author.getWhereList) — при отсутствии
        // совпадения форсируем заведомо пустой результат, а не игнорируем фильтр молча.
        filterAuthorName?.let {
            if (it != "") {
                val author = Author.getAuthorByName(it, WORKING_DATABASE, storageService, storageApiClient)
                args["author_id"] = author?.id?.toString() ?: "-1"
            }
        }
        filterYear?.let { if (filterYear != "") args["year"] = filterYear }
        // Частичный поиск (LOWER(name) LIKE) — не точное совпадение, см. Album.getWhereList.
        filterName?.let { if (filterName != "") args["name_search"] = filterName }
        filterAlbumType?.let { if (filterAlbumType != "") args["album_type"] = filterAlbumType }
        // Минимальное число песен в альбоме. Считаем в Kotlin ПОСЛЕ batch-подсчёта
        // (см. Album.countSongsByAlbumIds) — subquery HAVING в WHERE тут не нужен, фильтр
        // работает в связке с другими и резко сужает выборку уже на клиенте.
        val minSongsCount = filterSongsCountMin?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        val albums =
            Album
                .loadList(
                    whereArgs = args,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = true,
                )
        // Батч-подсчёт песен по альбомам одним SQL-запросом (см. Album.countSongsByAlbumIds).
        // Без N+1 на 5000+ альбомах. Альбомы без песен в map не попадут — дефолтим на 0.
        val songsCountByAlbumId =
            Album.countSongsByAlbumIds(albums.map { it.id }, WORKING_DATABASE)
        // Album.toDigestDTOs — пакетная версия toDTO() (батчит автора/картинки), не .map{it.toDTO()}
        // по одному альбому — иначе N+1 на 2000+ альбомах (см. KDoc Album.toDigestDTOs).
        val albumsList =
            Album
                .toDigestDTOs(albums, WORKING_DATABASE, storageService, storageApiClient)
                .map { it.copy(songsCount = songsCountByAlbumId[it.id] ?: 0) }
                .filter { minSongsCount <= 0 || it.songsCount >= minSongsCount }
                .sorted()

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "albumsDigests" to albumsList,
        )
    }

    /**
     * Облегчённая версия [apisAlbumsDigest] — без автора/картинок/счётчика песен (без единого
     * дополнительного запроса сверх основного списка альбомов, см. [Album.toLiteDTOs]).
     * Используется пикером «Альбом (ссылка)» в `SongEdit.vue` — там нужны только id/authorId/
     * year/name, чтобы отфильтровать альбомы по автору песни на клиенте и подписать `<option>`;
     * загрузка полного [apisAlbumsDigest] (нужен таблице "Альбомы" — там реально нужны 2 картинки
     * на строку) для этого избыточна.
     *
     * @see docs/features/dual-db-sync.md
     */
    @PostMapping("/albums/albumsdigestslite")
    @ResponseBody
    fun apisAlbumsDigestLite(): Map<String, Any> {
        val albums =
            Album.loadList(
                whereArgs = emptyMap(),
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true,
            )
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "albumsDigests" to Album.toLiteDTOs(albums).sorted(),
        )
    }

    /**
     * Возвращает id «репрезентативной» песни альбома для контекста [AlbumCoverModal] (модалка
     * обложки альбома, вызываемая из `webvue3/src/components/Albums/AlbumsTable.vue` по клику
     * на preview `(альбом)` или на название альбома). Модалка привязана к конкретной песне
     * через `currentSongId` — ей нужен `Song.rootFolder` для чтения/записи `LogoAlbum.png`
     * и `Song.album`/`Song.author` для дефолтного поискового запроса.
     *
     * Поиск: `MIN(id)` среди песен альбома (единственный стабильный критерий — в `tbl_songs`
     * нет колонки `first_song_in_album`, см. KDoc [Album.getFirstSongId]). Если у альбома нет
     * ни одной песни — возвращает `0L` (UI должен блокировать клик в этом случае — см.
     * `AlbumsTable.vue::canEditCover`).
     *
     * Не участвует в LOCAL↔SERVER sync (read-only lookup, не меняет данные).
     *
     * @return id песни альбома или `0L`, если у альбома нет песен
     * @see specs/014-album-cell-album-cover-modal/contracts/api.md
     */
    @PostMapping("/albums/firstsongid")
    @ResponseBody
    fun apisGetFirstSongIdByAlbumId(
        @RequestParam(required = true) albumId: Long,
    ): Long = Album.getFirstSongId(albumId, WORKING_DATABASE) ?: 0L

    @PostMapping("/albums/createalbum")
    @ResponseBody
    fun apisCreateAlbum(
        @RequestParam(required = true) authorId: Long,
        @RequestParam(required = true) year: Int,
        @RequestParam(required = true) name: String,
        @RequestParam(required = false) albumType: String?,
        @RequestParam(required = false) sortOrder: Int?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) shortDescription: String?,
        @RequestParam(required = false) warning: String?,
    ): Long {
        val newAlbum = Album(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        newAlbum.authorId = authorId
        newAlbum.year = year
        newAlbum.name = name
        albumType?.let { newAlbum.albumType = it }
        sortOrder?.let { newAlbum.sortOrder = it }
        description?.let { newAlbum.description = it }
        shortDescription?.let { newAlbum.shortDescription = it }
        warning?.let { newAlbum.warning = it }
        return Album.createNewAlbum(newAlbum, WORKING_DATABASE)?.id ?: 0L
    }

    @PostMapping("/albums/updatealbum")
    @ResponseBody
    fun apisUpdateAlbum(
        @RequestParam(required = true) id: Long,
        @RequestParam(required = true) authorId: Long,
        @RequestParam(required = true) year: Int,
        @RequestParam(required = true) name: String,
        @RequestParam(required = true) albumType: String,
        @RequestParam(required = true) sortOrder: Int,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) shortDescription: String?,
        @RequestParam(required = false) warning: String?,
    ): Long {
        Album
            .getAlbumById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let {
                it.authorId = authorId
                it.year = year
                it.name = name
                it.albumType = albumType
                it.sortOrder = sortOrder
                description?.let { v -> it.description = v }
                shortDescription?.let { v -> it.shortDescription = v }
                warning?.let { v -> it.warning = v }
                it.save()
                return id
            }
        return 0L
    }

    @PostMapping("/albums/deletealbum")
    @ResponseBody
    fun apisDeleteAlbum(
        @RequestParam(required = true) id: Long,
    ): Boolean = Album.delete(id = id, database = WORKING_DATABASE)

    // Переупорядочивание альбомов автора (drag-and-drop в webvue3, модалка "Альбомы автора").
    // ids — все альбомы автора в желаемом порядке отображения, sortOrder = их индекс в списке.
    @PostMapping("/albums/reorderalbums")
    @ResponseBody
    fun apisReorderAlbums(
        @RequestParam ids: List<Long>,
    ): Boolean {
        Album.reorderAlbums(
            orderedIds = ids,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
        return true
    }

    @PostMapping("/songs/coauthors/list")
    @ResponseBody
    fun apisSongCoAuthorsList(
        @RequestParam(required = true) songId: Long,
    ): Map<String, Any> {
        val coAuthorAuthorIds =
            SongCoAuthor
                .getCoAuthorsBySongId(
                    songId = songId,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it.authorId }
        val coAuthors =
            Author
                .getAuthorsByIds(
                    ids = coAuthorAuthorIds,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).values
                .map { it.toDTO() }
        return mapOf("coAuthors" to coAuthors)
    }

    // FR-009/FR-011 (specs/011-album-song-rename): произвольное число соавторов песни, отдельно
    // от главного автора (Song.author, свободный текст) — не влияет на группировку/URL/альбом.
    @PostMapping("/songs/coauthors/add")
    @ResponseBody
    fun apisSongCoAuthorsAdd(
        @RequestParam(required = true) songId: Long,
        @RequestParam(required = true) authorId: Long,
    ): Boolean {
        val song =
            Song.loadFromDbById(
                songId,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return false
        val author =
            Author.getAuthorById(authorId, WORKING_DATABASE, storageService, storageApiClient) ?: return false
        // Edge case (spec.md): соавтор не должен дублировать уже указанного главного автора песни.
        if (author.author.equals(song.author, ignoreCase = true)) return false
        val alreadyExists =
            SongCoAuthor
                .getCoAuthorsBySongId(songId, WORKING_DATABASE, storageService, storageApiClient)
                .any { it.authorId == authorId }
        if (alreadyExists) return true
        val newCoAuthor =
            SongCoAuthor(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        newCoAuthor.songId = songId
        newCoAuthor.authorId = authorId
        return SongCoAuthor.createNew(newCoAuthor, WORKING_DATABASE) != null
    }

    @PostMapping("/songs/coauthors/remove")
    @ResponseBody
    fun apisSongCoAuthorsRemove(
        @RequestParam(required = true) songId: Long,
        @RequestParam(required = true) authorId: Long,
    ): Boolean {
        val toDelete =
            SongCoAuthor
                .getCoAuthorsBySongId(songId, WORKING_DATABASE, storageService, storageApiClient)
                .firstOrNull { it.authorId == authorId } ?: return false
        return SongCoAuthor.delete(id = toDelete.id, database = WORKING_DATABASE)
    }

    // Одноразовый бэкфилл Album из song_author/song_year/song_album существующих песен
    // (specs/011-album-song-rename, FR-005/SC-003). Безопасно перезапускать — идемпотентно
    // (обрабатывает только album_id IS NULL).
    @PostMapping("/utils/backfillalbumsfromsongs")
    @ResponseBody
    fun doBackfillAlbumsFromSongs(): Boolean {
        thread {
            println("Бэкфилл альбомов из песен: начало")
            val result = AlbumBackfill.run(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            println("Бэкфилл альбомов из песен: завершено — $result")
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Бэкфилл альбомов из песен",
                        body =
                            "Групп обработано: ${result.groupsProcessed}, альбомов создано: ${result.albumsCreated}, " +
                                "переиспользовано: ${result.albumsReused}, песен привязано: ${result.songsLinked}",
                    ),
                ),
            )
        }
        return true
    }

    // Одноразовая миграция sortOrder альбомов с "внутри (автор,год)" на сквозной по автору
    // (см. Album.normalizeSortOrderAcrossYears). Безопасно перезапускать — идемпотентно.
    @PostMapping("/utils/normalizealbumsortorder")
    @ResponseBody
    fun doNormalizeAlbumSortOrder(): Boolean {
        thread {
            println("Нормализация sortOrder альбомов: начало")
            val updated = Album.normalizeSortOrderAcrossYears(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            println("Нормализация sortOrder альбомов: завершено — обновлено $updated")
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Нормализация порядка альбомов",
                        body = "Обновлено записей: $updated",
                    ),
                ),
            )
        }
        return true
    }

    data class FileDTO(
        val name: String,
        val path: String,
        val extension: String,
        val nameWithoutExtension: String,
        val parent: String,
        val length: Long,
        val isDirectory: Boolean,
    ) : Comparable<FileDTO> {
        override fun compareTo(other: FileDTO): Int {
            var result = other.isDirectory.compareTo(isDirectory)
            if (result != 0) return result
            result = name.compareTo(other.name)
            if (result != 0) return result
            return path.compareTo(other.path)
        }
    }

    @PostMapping("/files")
    @ResponseBody
    fun getFiles(
        @RequestParam path: String,
        @RequestParam(required = false) extensions: String?,
    ): List<FileDTO> {
        var directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            directory = File("/")
            if (!directory.exists() || !directory.isDirectory) {
                throw IllegalArgumentException("Invalid directory path")
            }
        }

        return directory
            .listFiles()
            ?.mapNotNull { file ->
                val needToAdd =
                    file.isDirectory ||
                        (extensions.isNullOrBlank() || file.extension.lowercase() in extensions.split(";").map { it.lowercase() })
                if (needToAdd) {
                    FileDTO(
                        name = file.name,
                        path = file.absolutePath,
                        extension = file.extension,
                        nameWithoutExtension = file.nameWithoutExtension,
                        parent = file.parent,
                        length = file.length(),
                        isDirectory = file.isDirectory,
                    )
                } else {
                    null
                }
            }?.sorted() ?: emptyList()
    }

    @PostMapping("/pictures/updatepicture")
    @ResponseBody
    fun apisUpdatePicture(
        @RequestParam(required = true) id: Long,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) full: String?,
        @RequestParam(required = false) @Suppress("unused") preview: String?,
    ): Long {
        Pictures
            .getPictureById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { pic ->
                name?.let { pic.name = it }
                full?.let { pic.full = it }
//            preview?.let { pic.preview = it }
                pic.save()
                return id
            }
        return 0L
    }

    @PostMapping("/pictures/picturesdigests")
    @ResponseBody
    fun apisPicturesDigest(
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterName: String?,
    ): Map<String, Any> {
        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterName?.let { if (filterName != "") args["picture_name"] = filterName }
        val picturesDigests =
            Pictures
                .loadList(
                    whereArgs = args,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = false,
                ).map {
                    it.toDTO()
                }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "picturesDigests" to picturesDigests,
        )
    }

    @PostMapping("/picture")
    @ResponseBody
    fun apisPicture(
        @RequestParam id: String,
    ): Any? =
        Pictures
            .getPictureById(
                id = id.toLong(),
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.toDTO()

    @PostMapping("/picture/delete")
    @ResponseBody
    fun doDeletePicture(
        @RequestParam id: Long,
    ) {
        Pictures.delete(id = id, database = WORKING_DATABASE)
    }

    @PostMapping("/picture/savetodisk")
    @ResponseBody
    fun doSavePictureToDisk(
        @RequestParam id: Long,
    ) {
        Pictures
            .getPictureById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.saveToDisk()
    }

    @PostMapping("/picture/loadfromdisk")
    @ResponseBody
    fun doLoadPictureFromDisk(
        @RequestParam pathToFile: String,
    ): String {
        if (!File(pathToFile).exists()) return ""
        try {
            val pictureBites = File(pathToFile).inputStream().readAllBytes()
            val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
            val iosFull = ByteArrayOutputStream()
            ImageIO.write(bi, "png", iosFull)
            return Base64.getEncoder().encodeToString(iosFull.toByteArray())
        } catch (e: Exception) {
            println(e)
        }
        return ""
    }

    @GetMapping("/picture/file")
    fun getPictureFile(
        @RequestParam file: String,
    ): ResponseEntity<ByteArray> {
        val bucket = "karaoke"
        if (storageService.fileExists(bucket, file)) {
            val bytes = storageService.downloadFile(bucket, file).use { it.readBytes() }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        }
        val isAuthor = file.endsWith(".preview.author.png")
        val isAlbum = file.endsWith(".preview.album.png")
        if (!isAuthor && !isAlbum) return ResponseEntity.notFound().build()
        val fullFile =
            if (isAuthor) {
                file.replace(".preview.author.png", ".author.png")
            } else {
                file.replace(".preview.album.png", ".album.png")
            }
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

    @PostMapping("/getwebvueprop")
    @ResponseBody
    fun getWebvueProperty(
        @RequestParam(required = true) key: String,
        @RequestParam(required = false) default: String?,
    ): String {
        val result = WVP.get(key = key, default = (default ?: ""))
        return result
    }

    @PostMapping("/setwebvueprop")
    @ResponseBody
    fun setWebvueProperty(
        @RequestParam(required = true) key: String,
        @RequestParam(required = true) value: String,
    ) {
        WVP.set(key = key, value = value)
    }

    @PostMapping("/getdict")
    @ResponseBody
    fun getDict(
        @RequestParam(required = true) dict: String,
    ): List<String> = TextFileDictionary.loadList(dict)

    @PostMapping("/getfreetimeslots")
    @ResponseBody
    fun getFreeTS(): List<String> = getFreeTimeSlots()

    @PostMapping("/songs/addsyncforall")
    @ResponseBody
    fun addSyncForAll(
        @RequestParam songsIds: String,
    ): List<String> {
        val ids =
            songsIds
                .split(";")
                .map { it }
                .filter { it != "" }
                .map { it.toLong() }
        val listSync = setSongToSyncRemoteTable(ids)

        if (listSync.isNotEmpty()) {
            SNS.send(SseNotification.sync(listOf(listSync)))
        }
        SyncIdsDictionary().clear()
        return listSync
    }

    @PostMapping("/song/keyBpmFinder")
    @ResponseBody
    fun createKeyBpmFinderProcess(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                KaraokeProcess.createProcess(
                    song = song,
                    action = KaraokeProcessTypes.KEY_BPM_FROM_FILE,
                    doWait = true,
                    prior = -1,
                    threadId = 1,
                )
            }
    }

    // Рендер видео mp4 из онлайн-плеера — интеграция в очередь KaraokeProcess.
    // Строго админская функция. Прогресс — через SSE ( tbl_processes ).
    @PostMapping("/song/renderMp4Preview")
    @ResponseBody
    fun createRenderMp4PreviewProcess(
        @RequestParam id: Long,
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) fps: Int?,
        @RequestParam(required = false) version: String?,
    ): Map<String, Any> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf("ok" to false, "message" to "Песня не найдена: id=$id")

        val renderVersion =
            try {
                com.svoemesto.karaokeapp.services.RenderVersion
                    .valueOf(version ?: "KARAOKE")
            } catch (_: Exception) {
                com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE
            }

        val processId =
            KaraokeProcess.createProcess(
                song = song,
                action =
                    when (renderVersion) {
                        com.svoemesto.karaokeapp.services.RenderVersion.LYRICS -> KaraokeProcessTypes.RENDER_MP4_LYRICS
                        com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE -> KaraokeProcessTypes.RENDER_MP4_KARAOKE
                        com.svoemesto.karaokeapp.services.RenderVersion.CHORDS -> KaraokeProcessTypes.RENDER_MP4_CHORDS
                        com.svoemesto.karaokeapp.services.RenderVersion.TABS -> KaraokeProcessTypes.RENDER_MP4_TABS
                        com.svoemesto.karaokeapp.services.RenderVersion.DEMO -> KaraokeProcessTypes.RENDER_MP4_DEMO
                    },
                doWait = true,
                prior = 1,
                threadId = 0,
                context =
                    mapOf(
                        "width" to (width ?: if (renderVersion == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) 1280 else 1920),
                        "height" to (height ?: if (renderVersion == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) 720 else 1080),
                        "fps" to (fps ?: if (renderVersion == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) 30 else 60),
                        "version" to renderVersion.name,
                    ),
            )
        return if (processId > 0) {
            mapOf(
                "ok" to true,
                "processId" to processId,
                "message" to "Рендер MP4 (${renderVersion.name}) поставлен в очередь (processId=$processId)",
            )
        } else {
            mapOf("ok" to false, "message" to "Не удалось поставить в очередь (возможно, уже выполняется)")
        }
    }

    // Фаза 2 автопубликации в Telegram (specs/113-telegram-demo-publish): ручной триггер того же
    // пути, что scheduler (FR-015). Кнопка «Опубликовать сейчас» в SongEdit.vue вызывает этот
    // endpoint для немедленной публикации или «опоздавшей» песни (прошедшая date/time).
    // В отличие от scheduler'а, endpoint игнорирует прошедшую дату (allowPastDate=true) —
    // админ явно инициировал публикацию. Возвращает JSON {success, state, messageId, error}
    // (контракт specs/113-telegram-demo-publish/contracts/telegram-auto-publish.md §1).
    // Endpoint доступен всегда (даже при telegramAutoPublishEnabled=false) — это ручной
    // триггер, не плановый бот. Паттерн /api/** = permitAll (SecurityConfig), как у
    // renderMp4Preview — отдельный adminKey в проекте не используется.
    @PostMapping("/song/publishToTelegramNow")
    @ResponseBody
    fun publishToTelegramNow(
        @RequestParam id: Long,
    ): Map<String, Any> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "state" to "scheduled" as Any,
                "messageId" to null as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )

        // FR-016: если уже опубликовано — отказ (кнопка должна быть скрыта во фронте,
        // сервер всё равно проверяет idTelegramDemo для защиты от гонок).
        if (song.idTelegramDemo.isNotEmpty()) {
            return mapOf(
                "success" to false as Any,
                "state" to "published" as Any,
                "messageId" to song.idTelegramDemo as Any,
                "error" to "Song $id is already published (idTelegramDemo=${song.idTelegramDemo}); clear idTelegramDemo first to re-publish" as Any,
            )
        }

        val result =
            com.svoemesto.karaokeapp.services.TelegramAutoPublishService.publishToTelegram(
                song,
                allowPastDate = true,
            )
        val response: MutableMap<String, Any> = mutableMapOf()
        response["success"] = result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.PUBLISHED ||
            result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.RENDERING ||
            result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.PUBLISHING
        response["state"] = result.state.code
        response["messageId"] = result.messageId ?: ""
        response["error"] = result.error ?: ""
        return response
    }

    // specs/121-vk-news-auto-publish: принудительная публикация песни в группу ВКонтакте
    // (кнопки «Опубликовать во ВК (air)» / «Опубликовать во ВК (premium)» в webvue3, FR-016/FR-026).
    // По образцу publishToTelegramNow выше. Endpoint доступен всегда (даже при
    // vkAutoPublishEnabled=false) — ручной триггер. Паттерн /api/** = permitAll (SecurityConfig).
    // Контракт: specs/121-vk-news-auto-publish/contracts/vk-api-contract.md §6. FR-027 — расширяемо.
    @PostMapping("/song/publishToVkNow")
    @ResponseBody
    fun publishToVkNow(
        @RequestParam id: Long,
        @RequestParam(required = false, defaultValue = "air") type: String,
    ): Map<String, Any> {
        val settings =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "state" to "scheduled" as Any,
                "postId" to null as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )

        // FR-008/FR-016: общая идемпотентность по idVk (один пост на песню, независимо от типа).
        if (settings.idVk.isNotEmpty()) {
            return mapOf(
                "success" to false as Any,
                "state" to "published" as Any,
                "postId" to settings.idVk as Any,
                "error" to "Song $id is already published (idVk=${settings.idVk}); clear idVk first to re-publish" as Any,
            )
        }

        val pubType =
            com.svoemesto.karaokeapp.model.PublicationType
                .fromCode(type) ?: com.svoemesto.karaokeapp.model.PublicationType.AIR
        val result =
            com.svoemesto.karaokeapp.services.VkAutoPublishService
                .publishToVk(settings, pubType)
        val response: MutableMap<String, Any> = mutableMapOf()
        response["success"] = result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.PUBLISHED ||
            result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.RENDERING ||
            result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.PUBLISHING
        response["state"] = result.state.code
        response["postId"] = result.postId ?: ""
        response["error"] = result.error ?: ""
        return response
    }

    // specs/122-premium-auto-publish: ручная премиум-публикация в Telegram. Endpoint доступен
    // всегда (даже при premiumAutoPublishEnabled=false) — позволяет админу форсировать публикацию
    // повторно после сброса флага или для тестов. В отличие от /api/song/publishToTelegramNow,
    // здесь persistMessageId=false (не записывает idTelegramDemo, чтобы тот же слот заполнила
    // будущая AIR-публикация).
    @PostMapping("/song/publishPremiumTelegram")
    @ResponseBody
    fun publishPremiumTelegram(
        @RequestParam id: Long,
    ): Map<String, Any> {
        val settings =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "state" to "scheduled" as Any,
                "messageId" to null as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )

        // Идемпотентность: если air-публикация уже произошла — skip (премиум-период заведомо позади).
        if (settings.idTelegramDemo.isNotEmpty()) {
            return mapOf(
                "success" to false as Any,
                "state" to "published" as Any,
                "messageId" to settings.idTelegramDemo as Any,
                "error" to "Song $id already has air-publication (idTelegramDemo=${settings.idTelegramDemo}); premium is no-op" as Any,
            )
        }

        // specs/122 fix: ВАЖНО — выставить newsPremiumPublishPending=true и related flags ПЕРЕД
        // публикацией (а не после). Это сигнал для TelegramAutoPublishScheduler.resumeRenderingSongs,
        // что этот рендер был запущен для PREMIUM-публикации. Без этого scheduler-резюм после
        // завершения render'а вызвал бы publishFile с дефолтным publicationType=AIR+persistMessageId=true
        // и СЛОМАЛ идею — записал бы id в idTelegramDemo, не оставив слот для будущей AIR-публикации
        // при выходе песни в эфир. PremiumAutoPublishScheduler тоже смотрит на эти флаги для skip'а
        // и для закрытия задачи после успеха обоих каналов.
        if (!settings.newsPremiumPublishPending &&
            (settings.premiumAutoPublishState.isBlank() || settings.premiumAutoPublishState == "RUNNING")
        ) {
            settings.newsPremiumPublishPending = true
            settings.premiumAutoPublishState = "RUNNING"
            settings.premiumAttemptCount = 0
            settings.premiumAutoPublishLastError = ""
            settings.saveToDb()
        }

        val result =
            com.svoemesto.karaokeapp.services.TelegramAutoPublishService.publishToTelegram(
                song = settings,
                allowPastDate = true,
                publicationType = com.svoemesto.karaokeapp.model.PublicationType.PREMIUM,
                persistMessageId = false,
            )
        // Помечаем флаг задачи как снятый, если публикация завершилась (успех/рендер/фейл), чтобы
        // при ручном вызове scheduler на следующем тике не начинал повторно. PREMIUM-опубликованная
        // песня — задача завершена.
        if (result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.PUBLISHED ||
            result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.RENDERING ||
            result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.SEND_FAILED
        ) {
            settings.newsPremiumTelegramSent = settings.newsPremiumTelegramSent ||
                result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.PUBLISHED
            if (settings.idTelegramDemo.isEmpty() &&
                settings.idVk.isEmpty() &&
                settings.newsPremiumTelegramSent &&
                settings.newsPremiumVkSent
            ) {
                settings.newsPremiumPublishPending = false
                if (settings.premiumAutoPublishState != "FAILED") {
                    settings.premiumAutoPublishState = "COMPLETE"
                }
            }
            settings.saveToDb()
        }

        val response: MutableMap<String, Any> = mutableMapOf()
        response["success"] = result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.PUBLISHED ||
            result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.RENDERING ||
            result.state == com.svoemesto.karaokeapp.services.TelegramAutoPublishState.PUBLISHING
        response["state"] = result.state.code
        response["messageId"] = result.messageId ?: ""
        response["error"] = result.error ?: ""
        response["newsPremiumPublishPending"] = settings.newsPremiumPublishPending as Any
        response["newsPremiumTelegramSent"] = settings.newsPremiumTelegramSent as Any
        response["newsPremiumVkSent"] = settings.newsPremiumVkSent as Any
        response["premiumAutoPublishState"] = settings.premiumAutoPublishState as Any
        return response
    }

    // specs/122-premium-auto-publish: ручная премиум-публикация в VK.
    // persistPostId=false, persistMessageId-equivalent для VK.
    @PostMapping("/song/publishPremiumVk")
    @ResponseBody
    fun publishPremiumVk(
        @RequestParam id: Long,
    ): Map<String, Any> {
        val settings =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "state" to "scheduled" as Any,
                "postId" to null as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )

        if (settings.idVk.isNotEmpty()) {
            return mapOf(
                "success" to false as Any,
                "state" to "published" as Any,
                "postId" to settings.idVk as Any,
                "error" to "Song $id already has air-publication (idVk=${settings.idVk}); premium is no-op" as Any,
            )
        }

        // specs/122 fix: выставить newsPremiumPublishPending=true ДО публикации — чтобы
        // VkAutoPublishScheduler.resumeRenderingSongs знал, что этот рендер для PREMIUM
        // и не вызвал onRenderCompleted с дефолтным AIR+persistPostId=true.
        if (!settings.newsPremiumPublishPending &&
            (settings.premiumAutoPublishState.isBlank() || settings.premiumAutoPublishState == "RUNNING")
        ) {
            settings.newsPremiumPublishPending = true
            settings.premiumAutoPublishState = "RUNNING"
            settings.premiumAttemptCount = 0
            settings.premiumAutoPublishLastError = ""
            settings.saveToDb()
        }

        val result =
            com.svoemesto.karaokeapp.services.VkAutoPublishService.publishToVk(
                song = settings,
                type = com.svoemesto.karaokeapp.model.PublicationType.PREMIUM,
                persistPostId = false,
            )

        if (result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.PUBLISHED ||
            result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.RENDERING ||
            result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.SEND_FAILED
        ) {
            settings.newsPremiumVkSent = settings.newsPremiumVkSent ||
                result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.PUBLISHED
            if (settings.idTelegramDemo.isEmpty() &&
                settings.idVk.isEmpty() &&
                settings.newsPremiumTelegramSent &&
                settings.newsPremiumVkSent
            ) {
                settings.newsPremiumPublishPending = false
                if (settings.premiumAutoPublishState != "FAILED") {
                    settings.premiumAutoPublishState = "COMPLETE"
                }
            }
            settings.saveToDb()
        }

        val response: MutableMap<String, Any> = mutableMapOf()
        response["success"] = result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.PUBLISHED ||
            result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.RENDERING ||
            result.state == com.svoemesto.karaokeapp.services.VkAutoPublishState.PUBLISHING
        response["state"] = result.state.code
        response["postId"] = result.postId ?: ""
        response["error"] = result.error ?: ""
        response["newsPremiumPublishPending"] = settings.newsPremiumPublishPending as Any
        response["newsPremiumTelegramSent"] = settings.newsPremiumTelegramSent as Any
        response["newsPremiumVkSent"] = settings.newsPremiumVkSent as Any
        response["premiumAutoPublishState"] = settings.premiumAutoPublishState as Any
        return response
    }

    // specs/121 fix 02.08.2026: User-token через Implicit Flow.
    //
    // VkApiClient.video.save требует user-token с правом `video` (community-token НЕ подходит,
    // error_code=5 "invalid token type"). Получить такой токен можно через Implicit Flow
    // Standalone-приложения VK:
    //
    //   1) Владелец группы создаёт Standalone-приложение:
    //      https://vk.com/apps?act=manage → Создать приложение → Standalone → Категория "Другое".
    //      Запоминает App ID (число). В настройках приложения задаёт Redirect URI —
    //      например, "https://sm-karaoke.ru/api/utils/vkOAuthCallback".
    //
    //   2) Сохраняет App ID в Karaoke.properties:
    //      - vkAppId = <число>
    //      - vkRedirectUri = "https://sm-karaoke.ru/api/utils/vkOAuthCallback"
    //
    //   3) Запрашивает у эндпоинта /api/utils/vkOAuthUrl готовую ссылку для авторизации
    //      (scope=video,photos,wall,offline) и открывает её в браузере.
    //
    //   4) VK редиректит на Redirect URI с фрагментом #access_token=...&user_id=...
    //      Владелец копирует фрагмент access_token и POST-ит на /api/utils/vkSaveUserToken.
    //
    //   5) Эндпоинт /api/utils/vkSaveUserToken сохраняет токен в Karaoke.properties
    //      (ключ `vkUserAccessToken`). После этого VkApiClient.video.save использует его.
    //
    // Этот двухшаговый flow безопасен (Implicit Flow с клиентским JS не хранит секрет
    // на сервере; Standalone-приложение использует client_id, без client_secret). Срок жизни
    // токена — бесконечный (благодаря scope=offline), но VK может отозвать при смене пароля.

    /**
     * DEPRECATED (specs/151-vk-id-personal-token). Используйте `/api/public/utils/vkIdOAuthUrl`
     * на проде — он автоматически вызовет `/api/utils/vkIdSaveTokens` на admin-машине.
     *
     * Старый `oauth.vk.ru` Implicit Flow заблокирован VK (05.08.2026). Возвращаем HTTP 410 Gone.
     */
    @GetMapping("/utils/vkOAuthUrl")
    @ResponseBody
    fun getVkOAuthUrl(): Map<String, Any> =
        mapOf(
            "deprecated" to true as Any,
            "use" to "/api/public/utils/vkIdOAuthUrl" as Any,
            "message" to "Этот endpoint устарел (oauth.vk.ru заблокирован). Используйте /api/public/utils/vkIdOAuthUrl (VK ID)." as Any,
        )

    /**
     * Сохраняет user-token (полученный после Implicit Flow) в Karaoke.properties
     * (ключ `vkUserAccessToken`). Сразу проверяет валидность токена через users.get —
     * если VK вернёт массив response с user_id, токен подходит и записывается.
     */
    @PostMapping("/utils/vkSaveUserToken")
    @ResponseBody
    fun saveVkUserToken(
        @RequestParam token: String,
    ): Map<String, Any> {
        if (token.isBlank()) {
            return mapOf(
                "success" to false as Any,
                "error" to "token is empty" as Any,
            )
        }
        // Проверяем валидность через users.get (если токен битый — VK вернёт ошибку).
        val apiVersion = KaraokeProperties.getString("vkApiVersion").ifBlank { "5.199" }
        val checkUrl =
            "https://api.vk.ru/method/users.get?access_token=" +
                java.net.URLEncoder.encode(token, "UTF-8") +
                "&v=$apiVersion"
        return try {
            val conn = java.net.URL(checkUrl).openConnection()
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            val response =
                conn
                    .getInputStream()
                    .bufferedReader()
                    .use { it.readText() }
            val parsed =
                com.svoemesto.karaokeapp.services.VkApiClient
                    .decodeUserCheck(response)
            if (parsed.error != null) {
                return mapOf(
                    "success" to false as Any,
                    "error" to "VK rejected token: ${parsed.error.errorCode} ${parsed.error.errorMsg}" as Any,
                )
            }
            val firstUser = parsed.response?.firstOrNull()
            if (firstUser == null) {
                return mapOf(
                    "success" to false as Any,
                    "error" to "VK вернул пустой массив пользователей (token битый?)" as Any,
                )
            }
            // Токен валиден — сохраняем в Karaoke.properties.
            KaraokeProperties.set("vkUserAccessToken", token)
            return mapOf(
                "success" to true as Any,
                "userId" to firstUser.id as Any,
                "userFirstName" to (firstUser.firstName ?: "") as Any,
                "userLastName" to (firstUser.lastName ?: "") as Any,
                "message" to
                    "Токен сохранён в vkUserAccessToken. Теперь VkApiClient.video.save и photos.* будут использовать его." as Any,
            )
        } catch (e: Exception) {
            return mapOf(
                "success" to false as Any,
                "error" to "Ошибка проверки токена: ${e.message}" as Any,
            )
        }
    }

    /**
     * DEPRECATED (specs/151-vk-id-personal-token). Используйте `/api/public/utils/vkIdOAuthCallback`.
     *
     * Старый `oauth.vk.ru` callback заблокирован VK (05.08.2026). Возвращаем HTTP 410 Gone.
     */
    @GetMapping("/utils/vkOAuthCallback", produces = ["text/html; charset=UTF-8"])
    @ResponseBody
    fun vkOAuthCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): String =
        "<html><body style=\"font-family:sans-serif;padding:40px;max-width:900px\">" +
            "<h2>DEPRECATED</h2>" +
            "<p>Этот endpoint устарел (oauth.vk.ru заблокирован).</p>" +
            "<p>Используйте <code>/api/public/utils/vkIdOAuthUrl</code> для получения нового токена через VK ID.</p>" +
            "<p>Подробнее — <code>specs/151-vk-id-personal-token</code>.</p>" +
            "</body></html>"

    /**
     * DEPRECATED (specs/151-vk-id-personal-token). Используйте `/api/public/utils/vkIdOAuthUrl`
     * на проде.
     *
     * Старый `oauth.vk.ru` Auth Code Flow заблокирован VK (05.08.2026). Возвращаем HTTP 410 Gone.
     */
    @GetMapping("/utils/vkOAuthCodeUrl")
    @ResponseBody
    fun getVkOAuthCodeUrl(): Map<String, Any> =
        mapOf(
            "deprecated" to true as Any,
            "use" to "/api/public/utils/vkIdOAuthUrl" as Any,
            "message" to "Этот endpoint устарел (oauth.vk.ru заблокирован). Используйте /api/public/utils/vkIdOAuthUrl (VK ID)." as Any,
        )

    // specs/151-vk-id-personal-token: миграция с oauth.vk.ru (заблокирован Security Error
    // 05.08.2026) на id.vk.ru. Ниже — 3 endpoint'а для работы с VK ID flow:
    // 1) /api/utils/vkIdSaveTokens — сохранение токенов (вызывается автоматически из
    //    PublicVkIdAuthController на проде после обмена code → tokens).
    // 2) /api/utils/vkIdTokenStatus — состояние токена (для мониторинга).
    // 3) /api/utils/vkIdRefreshNow — принудительный refresh (для ручного управления).
    // Scheduled refresh — в VkIdTokenRefreshScheduler (каждый час).
    // @see docs/features/vk-id-auth.md

    /**
     * Сохраняет VK ID токены в Karaoke.properties (FR-003).
     *
     * Вызывается автоматически из `PublicVkIdAuthController.vkIdOAuthCallback` на проде
     * после успешного обмена `code → tokens`. Проверяет валидность `accessToken` через
     * `users.get` перед сохранением (как в `vkSaveUserToken`).
     *
     * Сохраняет:
     * - `vkIdAccessToken`
     * - `vkIdRefreshToken`
     * - `vkIdAccessTokenExpiresAt = now + expiresIn`
     * - `vkIdIdToken` (если есть)
     * - `vkIdRefreshNeeded = false`
     * - `vkIdRefreshLastError = ""`
     *
     * @param accessToken access_token от VK ID.
     * @param refreshToken refresh_token от VK ID.
     * @param expiresIn срок жизни access_token в секундах.
     * @param idToken id_token (JWT) — опционально.
     * @return JSON `{success, userId, firstName, lastName, expiresAt, message}` или
     *   `{success: false, error}`.
     */
    @PostMapping("/utils/vkIdSaveTokens")
    @ResponseBody
    fun saveVkIdTokens(
        @RequestParam accessToken: String,
        @RequestParam(required = false) refreshToken: String = "",
        @RequestParam(required = false) expiresIn: Long = 3600L,
        @RequestParam(required = false) idToken: String = "",
    ): Map<String, Any> {
        if (accessToken.isBlank()) {
            return mapOf("success" to false as Any, "error" to "accessToken is empty" as Any)
        }
        // Проверяем валидность через users.get (по образцу saveVkUserToken).
        val apiVersion = KaraokeProperties.getString("vkApiVersion").ifBlank { "5.199" }
        val checkUrl =
            "https://api.vk.ru/method/users.get?access_token=" +
                java.net.URLEncoder.encode(accessToken, "UTF-8") +
                "&v=$apiVersion"
        return try {
            val conn = java.net.URL(checkUrl).openConnection()
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            val response =
                conn
                    .getInputStream()
                    .bufferedReader()
                    .use { it.readText() }
            val parsed =
                com.svoemesto.karaokeapp.services.VkApiClient
                    .decodeUserCheck(response)
            if (parsed.error != null) {
                return mapOf(
                    "success" to false as Any,
                    "error" to "VK rejected VK ID token: ${parsed.error.errorCode} ${parsed.error.errorMsg}" as Any,
                )
            }
            val firstUser = parsed.response?.firstOrNull()
            if (firstUser == null) {
                return mapOf(
                    "success" to false as Any,
                    "error" to "VK вернул пустой массив пользователей (token битый?)" as Any,
                )
            }
            // Сохраняем токены.
            KaraokeProperties.set("vkIdAccessToken", accessToken)
            if (refreshToken.isNotBlank()) {
                KaraokeProperties.set("vkIdRefreshToken", refreshToken)
            }
            val expiresAt =
                java.time.Instant
                    .now()
                    .plusSeconds(expiresIn)
                    .toString()
            KaraokeProperties.set("vkIdAccessTokenExpiresAt", expiresAt)
            if (idToken.isNotBlank()) {
                KaraokeProperties.set("vkIdIdToken", idToken)
            }
            KaraokeProperties.set("vkIdRefreshNeeded", false)
            KaraokeProperties.set("vkIdRefreshLastError", "")
            mapOf(
                "success" to true as Any,
                "userId" to firstUser.id as Any,
                "userFirstName" to (firstUser.firstName ?: "") as Any,
                "userLastName" to (firstUser.lastName ?: "") as Any,
                "expiresAt" to expiresAt as Any,
                "message" to
                    "Токены VK ID сохранены в Karaoke.properties (vkIdAccessToken / vkIdRefreshToken). " +
                    "VkApiClient начнёт использовать их для photos.* и video.save." as Any,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false as Any,
                "error" to "Ошибка проверки/сохранения токена: ${e.message ?: e.javaClass.simpleName}" as Any,
            )
        }
    }

    /**
     * Возвращает состояние VK ID токена (FR-008). Для мониторинга и UI-индикаторов.
     *
     * @return JSON `{hasClientId, hasClientSecret, hasAccessToken, hasRefreshToken,
     *   expiresAt, refreshNeeded, lastError}`.
     */
    @GetMapping("/utils/vkIdTokenStatus")
    @ResponseBody
    fun getVkIdTokenStatus(): Map<String, Any> =
        mapOf(
            "hasClientId" to (KaraokeProperties.getLong("vkIdClientId") > 0) as Any,
            "hasClientSecret" to (KaraokeProperties.getString("vkIdClientSecret").isNotBlank()) as Any,
            "hasAccessToken" to (KaraokeProperties.getString("vkIdAccessToken").isNotBlank()) as Any,
            "hasRefreshToken" to (KaraokeProperties.getString("vkIdRefreshToken").isNotBlank()) as Any,
            "expiresAt" to (KaraokeProperties.getString("vkIdAccessTokenExpiresAt")) as Any,
            "refreshNeeded" to (KaraokeProperties.getBoolean("vkIdRefreshNeeded")) as Any,
            "lastError" to (KaraokeProperties.getString("vkIdRefreshLastError")) as Any,
        )

    /**
     * Принудительно вызывает refresh access_token через refresh_token (FR-009).
     *
     * Полезно для отладки и ручного управления. Scheduled refresh работает в фоне
     * (см. VkIdTokenRefreshScheduler), но иногда нужен ручной refresh.
     *
     * При успехе — обновляет токены в Karaoke.properties.
     * При ошибке — устанавливает `vkIdRefreshNeeded=true`, `vkIdRefreshLastError`.
     *
     * @return JSON `{success, expiresAt?, error?, refreshNeeded?}`.
     */
    @PostMapping("/utils/vkIdRefreshNow")
    @ResponseBody
    fun vkIdRefreshNow(): Map<String, Any> =
        try {
            val result =
                com.svoemesto.karaokeapp.services
                    .VkApiClient()
                    .refreshVkIdAccessToken()
            val expiresAt =
                java.time.Instant
                    .now()
                    .plusSeconds(result.expiresIn)
                    .toString()
            KaraokeProperties.set("vkIdAccessToken", result.accessToken)
            KaraokeProperties.set("vkIdRefreshToken", result.refreshToken)
            KaraokeProperties.set("vkIdAccessTokenExpiresAt", expiresAt)
            KaraokeProperties.set("vkIdRefreshNeeded", false)
            KaraokeProperties.set("vkIdRefreshLastError", "")
            if (result.idToken != null) {
                KaraokeProperties.set("vkIdIdToken", result.idToken)
            }
            mapOf(
                "success" to true as Any,
                "expiresAt" to expiresAt as Any,
                "message" to "VK ID access_token обновлён" as Any,
            )
        } catch (e: com.svoemesto.karaokeapp.services.VkIdRefreshFailedException) {
            KaraokeProperties.set("vkIdRefreshNeeded", true)
            KaraokeProperties.set("vkIdRefreshLastError", "${e.errorCode}: ${e.errorMsg}")
            mapOf(
                "success" to false as Any,
                "error" to "${e.errorCode}: ${e.errorMsg}" as Any,
                "refreshNeeded" to true as Any,
            )
        } catch (e: IllegalStateException) {
            mapOf(
                "success" to false as Any,
                "error" to (e.message ?: e.javaClass.simpleName) as Any,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false as Any,
                "error" to "Неизвестная ошибка: ${e.message ?: e.javaClass.simpleName}" as Any,
            )
        }

    // specs/121-vk-news-auto-publish: редактор шаблонов постов ВК (FR-025). Возвращает все шаблоны
    // и список плейсхолдеров для UI редактора в webvue3 (VkTemplatesEditor.vue).
    // Контракт: specs/121-vk-news-auto-publish/contracts/vk-api-contract.md §6.
    @GetMapping("/vk/templates")
    @ResponseBody
    fun getVkTemplates(): Map<String, Any> {
        val templates =
            com.svoemesto.karaokeapp.model.PublicationType.entries.map { pt ->
                val key = "vkTemplate${pt.name.lowercase().replaceFirstChar { it.uppercase() }}"
                mapOf(
                    "type" to pt.code,
                    "key" to key,
                    "value" to KaraokeProperties.getString(key),
                    "description" to (KaraokeProperties.getDTO(key).description),
                )
            }
        return mapOf(
            "templates" to templates,
            "placeholders" to
                com.svoemesto.karaokeapp.services.VkTemplateService
                    .placeholders(),
        )
    }

    // specs/121-vk-news-auto-publish: сохранение шаблона поста ВК (FR-025, FR-015 — без перезапуска).
    @PostMapping("/vk/templates")
    @ResponseBody
    fun saveVkTemplate(
        @RequestParam key: String,
        @RequestParam value: String,
    ): Map<String, Any> {
        val allowedKeys =
            com.svoemesto.karaokeapp.model.PublicationType.entries
                .map { pt -> "vkTemplate${pt.name.lowercase().replaceFirstChar { it.uppercase() }}" }
                .toSet()
        if (key !in allowedKeys) {
            return mapOf(
                "success" to false as Any,
                "error" to "unknown key: $key (allowed: ${allowedKeys.joinToString(", ")})" as Any,
            )
        }
        KaraokeProperties.set(key, value)
        return mapOf("success" to true as Any, "key" to key as Any)
    }

    // specs/121-vk-news-auto-publish: preview шаблона на тестовой песне (FR-025, для редактора).
    // Принимает value шаблона (не сохранённого — для live-preview) и songId, возвращает отрендеренный
    // текст через VkTemplateService.render.
    @PostMapping("/vk/templates/preview")
    @ResponseBody
    fun previewVkTemplate(
        @RequestParam value: String,
        @RequestParam id: Long,
    ): Map<String, Any> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )
        val rendered =
            com.svoemesto.karaokeapp.services.VkTemplateService
                .render(value, song, null)
        return mapOf(
            "success" to true as Any,
            "preview" to rendered as Any,
            "truncated" to (rendered.length >= com.svoemesto.karaokeapp.services.VkTemplateService.VK_POST_MAX_LENGTH) as Any,
            "length" to rendered.length as Any,
            "maxLength" to com.svoemesto.karaokeapp.services.VkTemplateService.VK_POST_MAX_LENGTH as Any,
        )
    }

    // specs/121-vk-news-auto-publish: дефолтные шаблоны (FR-025, для кнопки «Сбросить к дефолту»).
    @GetMapping("/vk/templates/defaults")
    @ResponseBody
    fun getVkTemplateDefaults(): Map<String, Any> {
        val defaults =
            com.svoemesto.karaokeapp.model.PublicationType.entries.associate { pt ->
                val key = "vkTemplate${pt.name.lowercase().replaceFirstChar { it.uppercase() }}"
                val default =
                    when (pt) {
                        com.svoemesto.karaokeapp.model.PublicationType.AIR ->
                            com.svoemesto.karaokeapp.services.VkTemplateService.DEFAULT_AIR_TEMPLATE
                        com.svoemesto.karaokeapp.model.PublicationType.PREMIUM ->
                            com.svoemesto.karaokeapp.services.VkTemplateService.DEFAULT_PREMIUM_TEMPLATE
                    }
                key to default
            }
        return mapOf("defaults" to defaults)
    }

    // specs/121-vk-news-auto-publish: Telegram-шаблоны (caption) — управляются через общий
    // редактор шаблонов публикаций (frontend) на странице «Шаблоны публикаций».
    // Эндпойнты симметричны VK-аналогам (/vk/templates).
    @GetMapping("/telegram/templates")
    @ResponseBody
    fun getTelegramTemplates(): Map<String, Any> {
        val templates =
            com.svoemesto.karaokeapp.model.PublicationType.entries.map { pt ->
                val key = "telegramTemplate${pt.name.lowercase().replaceFirstChar { it.uppercase() }}"
                mapOf(
                    "type" to pt.code,
                    "key" to key,
                    "value" to KaraokeProperties.getString(key),
                    "description" to (KaraokeProperties.getDTO(key).description),
                )
            }
        return mapOf(
            "templates" to templates,
            "placeholders" to
                com.svoemesto.karaokeapp.services.TelegramTemplateService
                    .placeholders(),
        )
    }

    @PostMapping("/telegram/templates")
    @ResponseBody
    fun saveTelegramTemplate(
        @RequestParam key: String,
        @RequestParam value: String,
    ): Map<String, Any> {
        val allowedKeys =
            com.svoemesto.karaokeapp.model.PublicationType.entries
                .map { pt -> "telegramTemplate${pt.name.lowercase().replaceFirstChar { it.uppercase() }}" }
                .toSet()
        if (key !in allowedKeys) {
            return mapOf(
                "success" to false as Any,
                "error" to "unknown key: $key (allowed: ${allowedKeys.joinToString(", ")})" as Any,
            )
        }
        KaraokeProperties.set(key, value)
        return mapOf("success" to true as Any, "key" to key as Any)
    }

    @PostMapping("/telegram/templates/preview")
    @ResponseBody
    fun previewTelegramTemplate(
        @RequestParam value: String,
        @RequestParam id: Long,
    ): Map<String, Any> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )
        val rendered =
            com.svoemesto.karaokeapp.services.TelegramTemplateService
                .render(value, song)
        return mapOf(
            "success" to true as Any,
            "preview" to rendered as Any,
            "truncated" to (rendered.length >= com.svoemesto.karaokeapp.services.TelegramTemplateService.TELEGRAM_CAPTION_MAX_LENGTH) as Any,
            "length" to rendered.length as Any,
            "maxLength" to com.svoemesto.karaokeapp.services.TelegramTemplateService.TELEGRAM_CAPTION_MAX_LENGTH as Any,
        )
    }

    @GetMapping("/telegram/templates/defaults")
    @ResponseBody
    fun getTelegramTemplateDefaults(): Map<String, Any> {
        val defaults =
            com.svoemesto.karaokeapp.model.PublicationType.entries.associate { pt ->
                val key = "telegramTemplate${pt.name.lowercase().replaceFirstChar { it.uppercase() }}"
                val default =
                    when (pt) {
                        com.svoemesto.karaokeapp.model.PublicationType.AIR ->
                            com.svoemesto.karaokeapp.services.TelegramTemplateService.DEFAULT_AIR_TEMPLATE
                        com.svoemesto.karaokeapp.model.PublicationType.PREMIUM ->
                            com.svoemesto.karaokeapp.services.TelegramTemplateService.DEFAULT_PREMIUM_TEMPLATE
                    }
                key to default
            }
        return mapOf("defaults" to defaults)
    }

    // Статус рендера MP4
    @PostMapping("/song/renderMp4Status")
    @ResponseBody
    fun getRenderMp4Status(
        @RequestParam id: Long,
        @RequestParam(required = false) version: String?,
    ): Map<String, Any> {
        val processType =
            try {
                when (
                    com.svoemesto.karaokeapp.services.RenderVersion
                        .valueOf(version ?: "KARAOKE")
                ) {
                    com.svoemesto.karaokeapp.services.RenderVersion.LYRICS -> KaraokeProcessTypes.RENDER_MP4_LYRICS
                    com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE -> KaraokeProcessTypes.RENDER_MP4_KARAOKE
                    com.svoemesto.karaokeapp.services.RenderVersion.CHORDS -> KaraokeProcessTypes.RENDER_MP4_CHORDS
                    com.svoemesto.karaokeapp.services.RenderVersion.TABS -> KaraokeProcessTypes.RENDER_MP4_TABS
                    com.svoemesto.karaokeapp.services.RenderVersion.DEMO -> KaraokeProcessTypes.RENDER_MP4_DEMO
                }
            } catch (_: Exception) {
                KaraokeProcessTypes.RENDER_MP4_KARAOKE
            }
        val processes =
            KaraokeProcess.loadList(
                mapOf("song_id" to id.toString(), "process_type" to processType.name),
                WORKING_DATABASE,
            )
        val latest = processes.maxByOrNull { it.id }
        return if (latest != null) {
            mapOf(
                "ok" to true,
                "processId" to latest.id,
                "status" to latest.status,
                "percentage" to latest.percentage,
                "description" to latest.description,
                "start" to (latest.startStr ?: ""),
                "end" to (latest.endStr ?: ""),
            )
        } else {
            mapOf("ok" to true, "status" to "NONE")
        }
    }

    // Скачивание отрендеренного MP4
    @GetMapping("/song/renderMp4Download")
    fun downloadRenderedMp4(
        @RequestParam id: Long,
    ): ResponseEntity<Resource> {
        val file = File("$PATH_TO_TEMP_RENDERMP4_FOLDER/$id/output.mp4")
        if (!file.exists()) return ResponseEntity.notFound().build()
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"song_$id.mp4\"")
            .body(FileSystemResource(file))
    }

    // Получение healthReportList
    @PostMapping("/song/healthReportList")
    @ResponseBody
    fun getHealthReportList(
        @RequestParam id: Long,
    ): List<HealthReportDTO> =
        HealthReport
            .recomputeAndBroadcast(
                songId = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).errorsOnly()
            .map { it.toDTO() }

    // Каскадное «Исправить всё»: помечает песню как «в авто-ремонте» и выполняет всё решаемое сейчас.
    // Дальнейшие шаги цепочки (upload в локальное/удалённое хранилище после создания файла на диске)
    // ставятся автоматически из пост-хука воркера по мере завершения предыдущих задач.
    @PostMapping("/song/repairAll")
    @ResponseBody
    fun repairAll(
        @RequestParam id: Long,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                HealthReport.startRepairAll(
                    song = song,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            }
    }

    // Выполнение customActions у конкретного HealthReport-а
    @PostMapping("/song/executeHealthReportActions")
    @ResponseBody
    fun executeHealthReportActions(
        @RequestParam id: Long,
        @RequestParam healthReportStatusName: String,
        @RequestParam healthReportTypeName: String,
        @RequestParam description: String,
    ) {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                val healthReportDTO =
                    HealthReportDTO(
                        songId = id,
                        healthReportTypeName = healthReportTypeName,
                        healthReportStatusName = healthReportStatusName,
                        description = description,
                    )
                HealthReport.getHealthReport(song = song, dto = healthReportDTO)?.executeSolutionActions()
            }
    }

    // Получение SearchAsyncListBySongId
    @PostMapping("/song/searchasync")
    @ResponseBody
    fun getSearchAsyncList(
        @RequestParam songId: Long,
    ): List<SearchAsyncDTO> {
        val result =
            SearchAsync
                .getSearchAsyncListBySongId(
                    songId = songId,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it.toDTO() }
        return result
    }

    // Получение SearchResultListBySearchAsyncId
    @PostMapping("/song/searchresult")
    @ResponseBody
    fun getSearchResultList(
        @RequestParam searchAsyncId: Long,
    ): List<SearchResultDTO> {
        val result =
            SearchResult
                .getSearchResultListBySearchAsyncId(
                    searchAsyncId = searchAsyncId,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it.toDTO() }
        return result
    }

    // Удаление результатов поиска текста песни без запуска нового поиска
    // (specs/015-search-engine-selection, кнопка «Удалить результаты поиска» в SearchText.vue)
    @PostMapping("/song/deletesearchresults")
    @ResponseBody
    fun deleteSearchResults(
        @RequestParam songId: Long,
    ): Boolean {
        SearchResult.deleteBySongId(songId, WORKING_DATABASE, storageService, storageApiClient)
        SearchAsync.deleteBySongId(songId, WORKING_DATABASE, storageService, storageApiClient)
        return true
    }

    @PostMapping("/authymstart")
    @ResponseBody
    fun authYMstart() {
        createNewAuthContext()
    }

    @PostMapping("/authymstart2")
    @ResponseBody
    fun authYMstart2() {
        createNewAuthContext2()
    }

    @PostMapping("/authymstop")
    @ResponseBody
    fun authYMstop() {
        completeAuth()
    }

    private fun convertFlacToMp3(flacPath: String): File? {
        val flacFile = File(flacPath)
        if (!flacFile.exists()) return null
        val mp3File = File(flacPath.removeSuffix(".flac") + ".mp3")
        if (!mp3File.exists()) {
            val process =
                ProcessBuilder("ffmpeg", "-i", flacPath, "-codec:a", "libmp3lame", "-qscale:a", "2", "-y", mp3File.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            process.waitFor()
            if (!mp3File.exists()) return null
        }
        return mp3File
    }

    // Lazily seeds MinIO with a copy of the stem mp3 (idempotent). karaoke-web (public site,
    // runs on a different host with no access to local disk / the Demucs pipeline) reads stems
    // for the hidden public player exclusively from here — this is the only path that keeps them
    // in sync, since visiting the admin player is what triggers convertFlacToMp3() in the first place.
    // Storage key follows the same template HealthReport.kt uses for every KaraokeFileType with a
    // REMOTE_STORAGE location: "${song.storageFileName}${suffix}.${extention}" — suffix already
    // carries its own leading dot (e.g. ".accompaniment"), NOT a dash.
    private fun pushMp3ToStorage(
        mp3File: File,
        song: Song,
        fileType: KaraokeFileType,
    ) {
        val bucket = "karaoke"
        val storageKey = "${song.storageFileName}${fileType.suffix}.${fileType.extention}"
        if (!storageService.fileExists(bucket, storageKey)) {
            storageService.uploadFile(bucket, storageKey, mp3File.absolutePath)
        }
        // Персистентный флаг готовности плеера (см. deploy/karaoke-db/26_player_readiness_flags.sql) —
        // проставляем только для файлов, входящих в гейт готовности (accompaniment/vocal); bass/drums
        // не влияют на готовность и не отслеживаются этим флагом.
        when (fileType) {
            KaraokeFileType.MP3_ACCOMPANIMENT ->
                if (!song.stemAccompanimentReady) {
                    song.stemAccompanimentReady = true
                    song.saveToDb()
                }
            KaraokeFileType.MP3_VOCAL ->
                if (!song.stemVocalReady) {
                    song.stemVocalReady = true
                    song.saveToDb()
                }
            else -> {}
        }
    }

    @GetMapping("/song/{id}/fileminus.mp3")
    fun getSongFileMusicMp3(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            convertFlacToMp3(song.accompanimentNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, song, KaraokeFileType.MP3_ACCOMPANIMENT)
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filevoice.mp3")
    fun getSongFileVocalMp3(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            convertFlacToMp3(song.vocalsNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, song, KaraokeFileType.MP3_VOCAL)
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filebass.mp3")
    fun getSongFileBassMp3(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            convertFlacToMp3(song.bassNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, song, KaraokeFileType.MP3_BASS)
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filedrums.mp3")
    fun getSongFileDrumsMp3(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { song ->
            convertFlacToMp3(song.drumsNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, song, KaraokeFileType.MP3_DRUMS)
                return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    // assignmentId (опционально) — превью НЕОДОБРЕННОГО черновика онлайн-редактора (tbl_song_assignment_drafts):
    // до approve() правки живут только в drafts, tbl_songs их не видит. Задание покрывает ВСЮ песню —
    // черновик несёт ВСЕ голоса разом, подменяем весь sourceMarkersList целиком. assignment должен
    // принадлежать именно этой песне (id) — иначе подмена игнорируется (fail-safe, без утечки чужого черновика).
    // target (опционально, только вместе с assignmentId) — где реально читать задание/черновик: реальный
    // рабочий цикл онлайн-редактора часто идёт целиком на REMOTE (см. SongEditorController), а local ещё
    // не синкнут — без этого параметра подстановка молча не находила черновик и превью показывало пустой
    // текст. Song (метаданные, аудио с локального диска) всегда из WORKING_DATABASE — id совпадает.
    @GetMapping("/song/{id}/playerdata")
    @ResponseBody
    fun getSongPlayerData(
        @PathVariable id: Long,
        @RequestParam(required = false) assignmentId: Long?,
        @RequestParam(required = false) target: String?,
    ): ResponseEntity<Map<String, Any?>> {
        val song =
            Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return ResponseEntity.notFound().build()

        var markersList = song.sourceMarkersList
        if (assignmentId != null) {
            val remoteDb = if (target == "remote") Connection.remote() else null
            try {
                val assignmentDb = remoteDb ?: WORKING_DATABASE
                val assignment = SongAssignment.getById(assignmentId, assignmentDb, storageService, storageApiClient)
                val draft =
                    assignment
                        ?.takeIf { it.songId == id }
                        ?.let { SongAssignmentDraft.getByAssignment(it.id, assignmentDb, storageService, storageApiClient) }
                if (draft != null) {
                    val draftMarkersPerVoice = draft.editedMarkersPerVoice(lenientJson)
                    if (draftMarkersPerVoice.any { it.isNotEmpty() }) markersList = draftMarkersPerVoice
                }
            } finally {
                try {
                    remoteDb?.getConnection()?.close()
                } catch (_: Exception) {
                }
            }
        }

        val data =
            mapOf(
                "id" to id,
                "songName" to song.songName,
                "author" to song.author,
                "album" to song.album,
                "year" to song.year.takeIf { it > 0 },
                "track" to song.track.takeIf { it > 0 },
                "key" to song.key.takeIf { it.isNotBlank() },
                "bpm" to song.bpm,
                "songType" to song.songType.dbValue,
                "markers" to markersList,
                "audioAccompanimentUrl" to "/api/song/$id/fileminus.mp3",
                "audioVocalsUrl" to "/api/song/$id/filevoice.mp3",
                "audioBassUrl" to if (File(song.bassNameFlac).exists()) "/api/song/$id/filebass.mp3" else null,
                "audioDrumsUrl" to if (File(song.drumsNameFlac).exists()) "/api/song/$id/filedrums.mp3" else null,
                "albumImageUrl" to
                    song.pictureAlbum?.storageFileName?.let {
                        "/api/picture/file?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}"
                    },
                "artistImageUrl" to
                    song.pictureAuthor?.storageFileName?.let {
                        "/api/picture/file?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}"
                    },
                "exportBaseName" to "${song.fileName} [id-$id]".rightFileName(),
            )
        return ResponseEntity.ok(data)
    }

    // Demo fragment bounds for a song (admin: opens player in DEMO mode like public non-premium user).
    @GetMapping("/song/{id}/demobounds")
    @ResponseBody
    fun getDemoBounds(
        @PathVariable id: Long,
    ): ResponseEntity<Map<String, Double?>> {
        val song =
            Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "start" to song.demoFragmentStartSeconds,
                "end" to song.demoFragmentEndSeconds,
                "fadeIn" to song.demoFragmentFadeInSeconds,
            ),
        )
    }

    // Generates a .smkaraoke container (ZIP): manifest.json + audio MP3s + images from MinIO.
    // Media files are STORED (no recompression); manifest is DEFLATED.
    // Optional fields (tracks/images) are present only if the source files actually exist.
    @GetMapping("/song/{id}/playerfile")
    fun getSongPlayerFile(
        @PathVariable id: Long,
        response: HttpServletResponse,
    ) {
        val song =
            Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: run {
                    response.status = 404
                    return
                }

        val bucket = "karaoke"
        val tracks = mutableMapOf<String, String>()
        val images = mutableMapOf<String, String>()

        val bos = ByteArrayOutputStream()
        val zip = ZipOutputStream(bos)

        convertFlacToMp3(song.accompanimentNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/accompaniment.mp3", mp3.readBytes())
            tracks["accompaniment"] = "audio/accompaniment.mp3"
        }
        convertFlacToMp3(song.vocalsNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/vocals.mp3", mp3.readBytes())
            tracks["vocals"] = "audio/vocals.mp3"
        }
        convertFlacToMp3(song.bassNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/bass.mp3", mp3.readBytes())
            tracks["bass"] = "audio/bass.mp3"
        }
        convertFlacToMp3(song.drumsNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/drums.mp3", mp3.readBytes())
            tracks["drums"] = "audio/drums.mp3"
        }
        song.pictureAlbum?.let { pic ->
            if (storageService.fileExists(bucket, pic.storageFileName)) {
                val bytes = storageService.downloadFile(bucket, pic.storageFileName).use { it.readBytes() }
                smkaraokeAddStored(zip, "images/album.png", bytes)
                images["album"] = "images/album.png"
            }
        }
        song.pictureAuthor?.let { pic ->
            if (storageService.fileExists(bucket, pic.storageFileName)) {
                val bytes = storageService.downloadFile(bucket, pic.storageFileName).use { it.readBytes() }
                smkaraokeAddStored(zip, "images/artist.png", bytes)
                images["artist"] = "images/artist.png"
            }
        }

        // Embed app icon so OS file managers can associate a custom icon after type registration
        val iconBytes = javaClass.classLoader?.getResourceAsStream("smkaraoke-icon.ico")?.readBytes()
        if (iconBytes != null) smkaraokeAddStored(zip, "icon.ico", iconBytes)

        val manifest =
            mapOf(
                "version" to 1,
                "format" to "smkaraoke",
                "id" to id,
                "songName" to song.songName,
                "author" to song.author,
                "album" to song.album,
                "year" to song.year.takeIf { it > 0 },
                "track" to song.track.takeIf { it > 0 },
                "key" to song.key.takeIf { it.isNotBlank() },
                "bpm" to song.bpm,
                "markers" to song.sourceMarkersList,
                "tracks" to tracks,
                "images" to images,
                "icon" to if (iconBytes != null) "icon.ico" else null,
                "exportBaseName" to "${song.fileName} [id-$id]".rightFileName(),
            )
        val manifestBytes = ObjectMapper().writeValueAsBytes(manifest)
        val manifestEntry = ZipEntry("manifest.json").apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(manifestEntry)
        zip.write(manifestBytes)
        zip.closeEntry()

        zip.close()

        val downloadName = "${song.fileName} [id-$id].smkaraoke".rightFileName()
        // RFC 5987 encoding so browsers use the Cyrillic filename instead of the URL path ("playerfile")
        val encodedName =
            java.net.URLEncoder
                .encode(downloadName, "UTF-8")
                .replace("+", "%20")
        response.contentType = "application/x-smkaraoke"
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"song-$id.smkaraoke\"; filename*=UTF-8''$encodedName",
        )
        response.outputStream.write(bos.toByteArray())
    }

    private fun smkaraokeAddStored(
        zip: ZipOutputStream,
        name: String,
        bytes: ByteArray,
    ) {
        val crc = CRC32().also { it.update(bytes) }
        val entry =
            ZipEntry(name).apply {
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

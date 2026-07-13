package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.llm.LyricsFinderService
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.PlayerMp4MuxService
import com.svoemesto.karaokeapp.services.PlayerMp4RenderService
import com.svoemesto.karaokeapp.services.RenderMp4Params
import com.svoemesto.karaokeapp.services.SNS
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
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import java.io.IOException
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
import javax.imageio.ImageIO

data class FamilySongDto(val id: Long, val songName: String, val author: String, val album: String, val year: Long, val diffSeconds: Long, val original: Boolean, val current: Boolean, val idStatus: Long)
data class SelectFamilySongResultDto(val rootId: Long, val idStatus: Long)

data class SyncEntityInfoDto(
    val key: String,
    val displayName: String,
    val allowPush: Boolean,
    val allowPull: Boolean,
    val oneClickDirection: String,
    // Флаги операций per-direction (push = Local→Server, pull = Server→Local).
    val pushInsert: Boolean, val pushUpdate: Boolean, val pushDelete: Boolean, val pushMove: Boolean,
    val pullInsert: Boolean, val pullUpdate: Boolean, val pullDelete: Boolean, val pullMove: Boolean,
)
data class SyncRunResultDto(val created: List<String>, val updated: List<String>, val deleted: List<String>, val moved: List<String>)
data class SyncOneClickResultDto(val key: String, val displayName: String, val direction: String, val skipped: Boolean, val created: List<String>, val updated: List<String>, val deleted: List<String>, val moved: List<String>)

@SuppressWarnings("SpellCheckingInspection")
@Controller
@RequestMapping("/api")
class ApiController(
    private val sseNotificationService: SseNotificationService,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val lyricsFinderService: LyricsFinderService
) {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @GetMapping("/diagnostics") // GET запрос на /api/diagnostics
    @ResponseBody
    fun getDiagnosticsInfo(): Map<String, Any> {

        // Ваши "вшитые" или ожидаемые пути
        val expectedPaths = listOf(
            "/sm-karaoke/work",
            "/sm-karaoke/done1",
            "/sm-karaoke/done2",
            "/sm-karaoke/system/demucs/input",
            "/sm-karaoke/system/demucs/output"
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
                    val files = try {
                        Files.list(path).limit(10).map { it.fileName.toString() }.toList()
                    } catch (e: Exception) {
                        listOf("Error listing files: ${e.message}")
                    }
                    pathInfo["first_10_files"] = files
                    pathInfo["total_files_approx"] = try {
                        File(pathStr).list()?.size ?: "Unknown"
                    } catch (e: Exception) {
                        "Error counting: ${e.message}"
                    }
                } else if (Files.exists(path)) {
                    pathInfo["size_bytes"] = try {
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
        val settings = Settings.loadListFromDb(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true)
        println("Вызван getCnt. Количество записей в в базе данных: ${settings.size}")
        return "Количество записей в в базе данных: ${settings.size}"
    }

    @GetMapping("/fls")
    @ResponseBody
    fun getFls(): String {
        val files = getListFiles("/sm-karaoke/work").joinToString(", ")
        println("Вызван getFls. Файлы в папке /sm-karaoke/work: $files")
        return "Вызван getFls. Файлы в папке /sm-karaoke/work: $files"
    }

    @GetMapping("/song/{id}/filedrums")
    fun getSongFileDrums(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            val filename = File(settings.drumsNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filebass")
    fun getSongFileBass(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            val filename = File(settings.bassNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filevoice")
    fun getSongFileVocal(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            val filename = File(settings.vocalsNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/fileminus")
    fun getSongFileMusic(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            val filename = File(settings.accompanimentNameFlac)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filesong")
    fun getSongFileSong(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            val filename = File(settings.fileAbsolutePath)
            val resource = FileSystemResource(filename)
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(resource)
            }
        }
        return ResponseEntity.notFound().build()
    }

    // Получение списка id песен, изменившихся с указанного момента
    @PostMapping("/songs/changed")
    @ResponseBody
    fun getChangedSongsIds(@RequestParam time: Long): List<Long> {
        val result: MutableList<Long> = mutableListOf()

        val connection = WORKING_DATABASE.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${WORKING_DATABASE.name}")
            return emptyList()
        }
        var rs: ResultSet? = null
        val sql = "select id from tbl_settings where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > ?;"
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
    fun getChangedProcessesIds(@RequestParam time: Long): List<Long> {
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
        @RequestParam fields: String
    ): String {
        Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            settings.copyFieldsFromAnother(idAnother, fields.split(";").map { SettingField.valueOf(it) })
        }
        return "OK"
    }

    // Поиск "оригинала" текущей песни (по кнопке на форме, для песен в статусе NONE) - при успехе копирует
    // текст/маркеры и возвращает true, иначе запускает поиск текста в Интернете и возвращает false
    @PostMapping("/song/findoriginal")
    @ResponseBody
    fun doFindOriginalForSong(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient) ?: return false
        val original = findDuplicateOriginal(settings, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return if (original != null) {
            applyDuplicateOriginal(settings, original)
            true
        } else {
            getSearXNGSearch(settings = settings, lyricsFinderService = lyricsFinderService)
            false
        }
    }

    // Список песен из той же "семьи" (совпадение id/root_id с текущей песней), с разницей длительности
    @PostMapping("/song/familysongs")
    @ResponseBody
    fun getFamilySongs(@RequestParam id: Long): List<FamilySongDto> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient) ?: return emptyList()
        val familyIds = findFamilySongIds(settings, database = WORKING_DATABASE)
        val familySettings = Settings.loadListFromDbByIds(familyIds, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val currentMs = settings.ms
        val originalId = if (settings.rootId != 0L) settings.rootId else settings.id
        return (familySettings.values + settings).map { s ->
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
                current = s.id == settings.id,
                idStatus = s.idStatus
            )
        }.sortedBy { it.year }
    }

    // Ручной поиск "оригинала" по (части) названия - без учёта пунктуации и с "ё"="е" (модалка "Похожие версии песни")
    @PostMapping("/song/searchoriginal")
    @ResponseBody
    fun searchOriginalCandidates(@RequestParam id: Long, @RequestParam search: String): List<FamilySongDto> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient) ?: return emptyList()
        val ids = searchSongsByNormalizedName(settings, search, database = WORKING_DATABASE)
        if (ids.isEmpty()) return emptyList()
        val candidates = Settings.loadListFromDbByIds(ids, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val currentMs = settings.ms
        val originalId = if (settings.rootId != 0L) settings.rootId else settings.id
        return candidates.values.map { s ->
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
                current = s.id == settings.id,
                idStatus = s.idStatus
            )
        }.sortedBy { it.year }
    }

    // Выбор песни из модалки "Похожие версии песни" - копирует текст/маркеры, безусловно проставляет
    // root_id (осознанный выбор пользователя) и условно статус (только если он ещё NONE/0 -> TEXT_CREATE/1)
    @PostMapping("/song/selectfamilysong")
    @ResponseBody
    fun selectFamilySong(@RequestParam id: Long, @RequestParam idAnother: Long, @RequestParam(required = false) deltaMs: Long?): SelectFamilySongResultDto? {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient) ?: return null
        val another = Settings.loadFromDbById(idAnother, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient) ?: return null
        applyFamilySongSelection(settings, another, deltaMs)
        return SelectFamilySongResultDto(rootId = settings.rootId, idStatus = settings.idStatus)
    }

    // Акустическая сверка текущей песни с кандидатом в оригинал (модалка "Похожие версии песни",
    // кнопки "Сверить"/"Сверить все"). Кросс-корреляция огибающих вокальных стемов - см. WaveformCompare.
    @PostMapping("/song/comparewaveform")
    @ResponseBody
    fun compareWaveform(@RequestParam id: Long, @RequestParam idAnother: Long): WaveformCompareResultDto {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            ?: return WaveformCompareResultDto(idAnother, 0, 0, "", false, "Текущая песня не найдена")
        val another = Settings.loadFromDbById(idAnother, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            ?: return WaveformCompareResultDto(idAnother, 0, 0, "", false, "Кандидат не найден")
        return WaveformCompare.compareWaveforms(settings, another)
    }

    // Получение исходного текста для голоса
    @PostMapping("/song/voicesourcetext")
    @ResponseBody
    fun getSongSourceText(@RequestParam id: Long, @RequestParam voiceId: Int): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            settings.getSourceText(voiceId)
        } ?: ""
        return text
    }

    // diffbeats + 1
    @PostMapping("/song/diffbeatsinc")
    @ResponseBody
    fun diffBeatsIncrement(@RequestParam id: Long): Long {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return settings?.let {
            settings.fields[SettingField.DIFFBEATS] = (settings.diffBeats+1).toString()
            settings.saveToDb()
            settings.diffBeats
        }?: -1
    }

    // diffbeats -+ 1
    @PostMapping("/song/diffbeatsdec")
    @ResponseBody
    fun diffBeatsDecrement(@RequestParam id: Long): Long {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return settings?.let {
            if (settings.diffBeats > 0) {
                settings.fields[SettingField.DIFFBEATS] = (settings.diffBeats-1).toString()
                settings.saveToDb()
            }
            settings.diffBeats
        }?: -1
    }

    // Получение sheetsageinfo
    @PostMapping("/song/sheetsageinfo")
    @ResponseBody
    fun getSheetsageinfo(@RequestParam id: Long): Map<String, Any> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val sheetsageinfo = settings?.let {
            settings.sheetsageInfo
        } ?: emptyMap()
        return sheetsageinfo
    }

    // Получение sheetsageinfo - tempo
    @PostMapping("/song/sheetsageinfobpm")
    @ResponseBody
    fun getSheetsageinfoBpm(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val sheetsageinfotempo = settings?.let {
            settings.sheetsageInfo["tempo"] as String
        } ?: ""
        return sheetsageinfotempo
    }

    // Получение sheetsageinfo - key
    @PostMapping("/song/sheetsageinfokey")
    @ResponseBody
    fun getSheetsageinfoKey(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val sheetsageinfokey = settings?.let {
            settings.sheetsageInfo["key"] as String
        } ?: ""
        return sheetsageinfokey
    }

    // Получение sheetsageinfo - chords
    @PostMapping("/song/sheetsageinfochords")
    @ResponseBody
    fun getSheetsageinfoChords(@RequestParam id: Long): List<String> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val sheetsageinfochords = settings?.let {
            @Suppress("UNCHECKED_CAST")
            settings.sheetsageInfo["chords"] as List<String>
        } ?: emptyList()
        return sheetsageinfochords
    }

    // Получение sheetsageinfo - beattimes
    @PostMapping("/song/sheetsageinfobeattimes")
    @ResponseBody
    fun getSheetsageinfoBeattimes(@RequestParam id: Long): List<Double> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val sheetsageinfobeattimes = settings?.let {
            @Suppress("UNCHECKED_CAST")
            settings.sheetsageInfo["beattimes"] as List<Double>
        } ?: emptyList()
        return sheetsageinfobeattimes
    }


    // Получение слогов для голоса
    @PostMapping("/song/voicesourcesyllables")
    @ResponseBody
    fun getSongSourceSyllables(@RequestParam id: Long, @RequestParam voiceId: Int): List<String> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val syllables = settings?.let {
            settings.getSourceSyllables(voiceId)
        } ?: emptyList()
        return syllables
    }

    // Получение маркеров для голоса
    @PostMapping("/song/voicesourcemarkers")
    @ResponseBody
    fun getSongSourceMarkers(@RequestParam id: Long, @RequestParam voiceId: Int): List<SourceMarker> {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val markers = settings?.let {
            settings.getSourceMarkers(voiceId)
        } ?: emptyList()
        return markers
    }

    // Получение форматированного текста
    @PostMapping("/song/textformatted")
    @ResponseBody
    fun getSongTextFormatted(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
//            settings.getTextFormatted()
            settings.formattedTextSong
        } ?: ""
        return text
    }


    // Получение форматированного текста с нотами
    @PostMapping("/song/notesformatted")
    @ResponseBody
    fun getSongFormattedNotes(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
//            settings.getFormattedNotes()
            settings.formattedTextTabs
        } ?: ""
        return text
    }

    // Получение форматированного текста с аккордами
    @PostMapping("/song/chordsformatted")
    @ResponseBody
    fun getSongFormattedChords(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
//            settings.getFormattedChords()
            settings.formattedTextChords
        } ?: ""
        return text
    }

    // Получение текста заголовка для boosty
    @PostMapping("/song/textboostyhead")
    @ResponseBody
    fun getSongTextBoostyHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getTextBoostyHead()
            text
        } ?: ""
        return text
    }

    // Получение текста тела для boosty
    @PostMapping("/song/textboostybody")
    @ResponseBody
    fun getSongTextBoostyBody(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getTextBoostyBody()
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для sponsr
    @PostMapping("/song/textsponsrhead")
    @ResponseBody
    fun getSongTextSponsrHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getTextBoostyHead()
            text
        } ?: ""
        return text
    }

    // Получение текста тела для sponsr
    @PostMapping("/song/textsponsrbody")
    @ResponseBody
    fun getSongTextSponsrBody(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getTextSponsrBody()
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для boostyFiles
    @PostMapping("/song/textboostyfileshead")
    @ResponseBody
    fun getSongTextBoostyFilesHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getTextBoostyFilesHead()
            text
        } ?: ""
        return text
    }

    // Получение текста тела VkGroup
    @PostMapping("/song/textvkbody")
    @ResponseBody
    fun getSongTextVkBody(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getVKGroupDescription()
            text
        } ?: ""
        return text
    }

    // Получение текста тела VkGroup
    @PostMapping("/song/textvkbodysponsr")
    @ResponseBody
    fun getSongTextVkBodySponsr(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getVKGroupDescriptionSponsr()
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Karaoke
    @PostMapping("/song/textdzenkaraokeheader")
    @ResponseBody
    fun getSongTextDzenKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.KARAOKE, 140)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Dzen Karaoke
    @PostMapping("/song/textdzenkaraokewoheader")
    @ResponseBody
    fun getSongTextDzenKaraokeWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE, 5000)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Lyrics
    @PostMapping("/song/textdzenlyricsheader")
    @ResponseBody
    fun getSongTextDzenLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Dzen Lyrics
    @PostMapping("/song/textdzenlyricswoheader")
    @ResponseBody
    fun getSongTextDzenLyricsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Chords
    @PostMapping("/song/textdzenchordsheader")
    @ResponseBody
    fun getSongTextDzenChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Tabs
    @PostMapping("/song/textdzentabsheader")
    @ResponseBody
    fun getSongTextDzenTabsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.TABS, 140)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Dzen Chords
    @PostMapping("/song/textdzenchordswoheader")
    @ResponseBody
    fun getSongTextDzenChordsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000)
            text
        } ?: ""
        return text
    }


    // Получение текста тела для Dzen Tabs
    @PostMapping("/song/textdzentabswoheader")
    @ResponseBody
    fun getSongTextDzenTabsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.TABS, 5000)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Karaoke
    @PostMapping("/song/textplkaraokeheader")
    @ResponseBody
    fun getSongTextPlKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.KARAOKE, 100)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Platforma Karaoke
    @PostMapping("/song/textplkaraokewoheader")
    @ResponseBody
    fun getSongTextPlKaraokeWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE, 5000, 100)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Lyrics
    @PostMapping("/song/textpllyricsheader")
    @ResponseBody
    fun getSongTextPlLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.LYRICS, 100)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Platforma Lyrics
    @PostMapping("/song/textpllyricswoheader")
    @ResponseBody
    fun getSongTextPlLyricsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000, 100)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Platforma Chords
    @PostMapping("/song/textplchordsheader")
    @ResponseBody
    fun getSongTextPlChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
            text
        } ?: ""
        return text
    }
    // Получение текста заголовка для Platforma Tabs
    @PostMapping("/song/textpltabsheader")
    @ResponseBody
    fun getSongTextPlTabsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionHeader(SongVersion.TABS, 100)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Platforma Chords
    @PostMapping("/song/textplchordswoheader")
    @ResponseBody
    fun getSongTextPlChordsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000, 100)
            text
        } ?: ""
        return text
    }
    // Получение текста тела для Platforma Tabs
    @PostMapping("/song/textpltabswoheader")
    @ResponseBody
    fun getSongTextPlTabsWOHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.TABS, 5000, 100)
            text
        } ?: ""
        return text
    }


    // Получение текста заголовка для Vk Karaoke
    @PostMapping("/song/textvkkaraokeheader")
    @ResponseBody
    fun getSongTextVkKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            Song(settings, SongVersion.KARAOKE)
            val text = it.getDescriptionVkHeader(SongVersion.KARAOKE)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Vk Karaoke
    @PostMapping("/song/textvkkaraoke")
    @ResponseBody
    fun getSongTextVkKaraoke(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVk(SongVersion.KARAOKE)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Lyrics
    @PostMapping("/song/textvklyricsheader")
    @ResponseBody
    fun getSongTextVkLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Vk Lyrics
    @PostMapping("/song/textvklyrics")
    @ResponseBody
    fun getSongTextVkLyrics(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVk(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Vk Chords
    @PostMapping("/song/textvkchordsheader")
    @ResponseBody
    fun getSongTextVkChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }
    // Получение текста заголовка для Vk Tabs
    @PostMapping("/song/textvktabsheader")
    @ResponseBody
    fun getSongTextVkTabsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.TABS)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Vk Chords
    @PostMapping("/song/textvkchords")
    @ResponseBody
    fun getSongTextVkChords(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVk(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }
    // Получение текста тела для Vk Tabs
    @PostMapping("/song/textvktabs")
    @ResponseBody
    fun getSongTextVkTabs(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionVk(SongVersion.TABS)
            text
        } ?: ""
        return text
    }


    // Получение текста заголовка для Telegram Karaoke
    @PostMapping("/song/texttelegramkaraokeheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionTelegramHeader(SongVersion.KARAOKE)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Lyrics
    @PostMapping("/song/texttelegramlyricsheader")
    @ResponseBody
    fun getSongTextTelegramLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionTelegramHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Chords
    @PostMapping("/song/texttelegramchordsheader")
    @ResponseBody
    fun getSongTextTelegramChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionTelegramHeader(SongVersion.CHORDS)
            text
        } ?: ""
        return text
    }
    // Получение текста заголовка для Telegram Tabs
    @PostMapping("/song/texttelegramtabsheader")
    @ResponseBody
    fun getSongTextTelegramTabsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionTelegramHeader(SongVersion.TABS)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Max Karaoke
    @PostMapping("/song/textmaxkaraokeheader")
    @ResponseBody
    fun getSongTextMaxKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionMaxHeader(SongVersion.KARAOKE)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Max Lyrics
    @PostMapping("/song/textmaxlyricsheader")
    @ResponseBody
    fun getSongTextMaxLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionMaxHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Max Chords
    @PostMapping("/song/textmaxchordsheader")
    @ResponseBody
    fun getSongTextMaxChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionMaxHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }
    // Получение текста заголовка для Max Tabs
    @PostMapping("/song/textmaxtabsheader")
    @ResponseBody
    fun getSongTextMaxTabsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val text = settings?.let {
            val text = it.getDescriptionMaxHeader(SongVersion.TABS)
            text
        } ?: ""
        return text
    }
    
    // Получение indexTabsVariant
    @PostMapping("/song/indextabsvariant")
    @ResponseBody
    fun getSongIndexTabsVariant(@RequestParam id: Long): Int {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return settings?.indexTabsVariant ?: 0
    }

    // Получение списка авторов
    @PostMapping("/songs/authors")
    @ResponseBody
    fun authors(): Map<String, Any> {
        return mapOf(
            "authors" to Settings.loadListAuthors(database = WORKING_DATABASE)
        )
    }

    // Получение списка словарей
    @PostMapping("/songs/dicts")
    @ResponseBody
    fun dicts(): Map<String, Any> {
        return mapOf(
            "dicts" to TEXT_FILE_DICTS.keys.toMutableList().sorted().toList()
        )
    }

    // Получение списка статусов процессов
    @PostMapping("/processes/countwaiting")
    @ResponseBody
    fun getCountWaiting(): Long {
        return KaraokeProcess.getCountWaiting(database = WORKING_DATABASE)
    }

    // Получение списка статусов процессов
    @PostMapping("/processes/statuses")
    @ResponseBody
    fun processesStatuses(): Map<String, Any> {
        return mapOf(
            "statuses" to KaraokeProcessStatuses.entries.toTypedArray()
        )
    }

    // Получение списка типов процессов
    @PostMapping("/processes/types")
    @ResponseBody
    fun processesTypes(): Map<String, Any> {
        return mapOf(
            "authors" to KaraokeProcessStatuses.entries.toTypedArray()
        )
    }

    // Получение списка песен по списку id
    @PostMapping("/songs/ids")
    @ResponseBody
    fun apisSongsByIds(@RequestParam ids: List<Long>): List<SettingsDTO> {
        return Settings.loadListFromDb(mapOf(Pair("ids", ids.joinToString(","))), database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true).map { it.toDTO() }
    }

    // Получение списка процессов по списку id
    @PostMapping("/processes/ids")
    @ResponseBody
    fun apisProcessesByIds(@RequestParam ids: List<Long>): List<KaraokeProcessDTO> {
        return KaraokeProcess.loadList(mapOf(Pair("ids", ids.joinToString(","))), database = WORKING_DATABASE).map { it.toDTO() }
    }

    // список publications
    @PostMapping("/publications")
    @ResponseBody
    fun publications(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["publish_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getPublicationList(args, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient).map { it.toDTO() }
        )
    }

    // список unpublications
    @PostMapping("/unpublications")
    @ResponseBody
    fun unpublications(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getUnPublicationList(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient).map { publication -> publication.map { it.toDTO() } }
        )
    }

    // список skipedpublications
    @PostMapping("/skipedpublications")
    @ResponseBody
    fun skipedPublications(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getSkipedPublicationList(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient).map { publish -> publish.map { it.toDTO() } }
        )
    }

    // список publications
    @PostMapping("/publications2")
    @ResponseBody
    fun publications2(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["publish_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to CrossSettings.publications(Publication.getSettingsListForPublications(args, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient))
        )
    }

    // список unpublications
    @PostMapping("/unpublications2")
    @ResponseBody
    fun unpublications2(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to CrossSettings.unpublications(Publication.getSettingsListForUnpublications(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient))
        )
    }

    @PostMapping("/publicationsdigest")
    @ResponseBody
    fun publicationsDigest(
        @RequestParam(required = false) filterDateFrom: String?,
        @RequestParam(required = false) filterDateTo: String?,
        @RequestParam(required = false) filterCond: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterDateFrom?.let { if (filterDateFrom != "") args["filter_date_from"] = filterDateFrom }
        filterDateTo?.let { if (filterDateTo != "") args["filter_date_to"] = filterDateTo }
        filterCond?.let { if (filterCond != "") args["filter_cond"] = filterCond }
        val listOfSettings = Publication.getSettingsListForPublications(args, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        val publications = when (filterCond) {
            "unpublish" -> {
                CrossSettings.unpublications(listOfSettings)
            }
            "skiped" -> {
                CrossSettings.skiped(listOfSettings)
            }
            else -> {
                CrossSettings.publications(listOfSettings)
            }
        }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publicationsDigest" to publications
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
        @RequestParam(required = false) filterSettingsId: String?,
        @RequestParam(required = false) filterType: String?,
        @RequestParam(required = false) filterLimit: String?,
        @RequestParam(required = false) filterNotail: String?

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterThreadId?.let { if (filterThreadId != "") args["thread_id"] = filterThreadId }
        filterName?.let { if (filterName != "") args["process_name"] = filterName }
        filterStatus?.let { if (filterStatus != "") args["process_status"] = filterStatus }
        filterOrder?.let { if (filterOrder != "") args["process_order"] = filterOrder }
        filterPriority?.let { if (filterPriority != "") args["process_priority"] = filterPriority }
        filterDescription?.let { if (filterDescription != "") args["process_description"] = filterDescription }
        filterSettingsId?.let { if (filterSettingsId != "") args["settings_id"] = filterSettingsId }
        filterType?.let { if (filterType != "") args["process_type"] = filterType }
        filterLimit?.let { if (filterLimit != "") args["filter_limit"] = filterLimit }
        filterNotail?.let { if (filterNotail != "") args["filter_notail"] = filterNotail }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "processesDigests" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() },
            "statuses" to KaraokeProcessStatuses.entries.toTypedArray(),
            "types" to KaraokeProcessTypes.entries.toTypedArray()
        )
    }

    @PostMapping("/songshistory")
    @ResponseBody
    fun apisSongsHistory(): Map<String, Any> {
        val history = SongsHistory().toDTO()
        return mapOf(
            "history" to history
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
        @RequestParam(required = false) flagExclusive: String?,
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
        @RequestParam(required = false) filterIsSync: String?,
        @RequestParam(required = false) filterRootId: String?,
        // filterAssignmentStatus/target — фильтр по назначенному заданию онлайн-редактора ("unassigned"
        // или dbValue из SongAssignmentStatus). Settings по-прежнему всегда грузятся из WORKING_DATABASE
        // (как раньше) — target относится ТОЛЬКО к тому, где искать назначения (local/remote).
        @RequestParam(required = false) filterAssignmentStatus: String?,
        @RequestParam(required = false) target: String?

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
        flagExclusive?.let { if (flagExclusive != "") args["flag_exclusive"] = flagExclusive }
        flagFree?.let { if (flagFree != "") args["flag_free"] = flagFree }
        filterResultVersion?.let { if (filterResultVersion != "") args["filter_result_version"] = filterResultVersion }
        filterCountVoices?.let { if (filterCountVoices != "") args["filter_count_voices"] = filterCountVoices }
        filterVersionBoosty?.let { if (filterVersionBoosty != "") args["filter_version_boosty"] = filterVersionBoosty }
        filterVersionBoostyFiles?.let { if (filterVersionBoostyFiles != "") args["filter_version_boosty_files"] = filterVersionBoostyFiles }
        filterVersionSponsr?.let { if (filterVersionSponsr != "") args["filter_version_sponsr"] = filterVersionSponsr }
        filterVersionDzenKaraoke?.let { if (filterVersionDzenKaraoke != "") args["filter_version_dzen_karaoke"] = filterVersionDzenKaraoke }
        filterVersionVkKaraoke?.let { if (filterVersionVkKaraoke != "") args["filter_version_vk_karaoke"] = filterVersionVkKaraoke }
        filterVersionTelegramKaraoke?.let { if (filterVersionTelegramKaraoke != "") args["filter_version_telegram_karaoke"] = filterVersionTelegramKaraoke }
        filterVersionPlKaraoke?.let { if (filterVersionPlKaraoke != "") args["filter_version_pl_karaoke"] = filterVersionPlKaraoke }
        filterVersionMaxKaraoke?.let { if (filterVersionMaxKaraoke != "") args["filter_version_max_karaoke"] = filterVersionMaxKaraoke }
        filterRate?.let { if (filterRate != "") args["filter_rate"] = filterRate }
        filterStatusProcessLyrics?.let { if (filterStatusProcessLyrics != "") args["filter_status_process_lyrics"] = filterStatusProcessLyrics }
        filterStatusProcessKaraoke?.let { if (filterStatusProcessKaraoke != "") args["filter_status_process_karaoke"] = filterStatusProcessKaraoke }
        filterIsSync?.let { if (filterIsSync != "") args["is_sync"] = filterIsSync }
        filterRootId?.let { if (filterRootId != "") args["filter_root_id"] = filterRootId }

        SongsHistory().add(args)

        var settingsList = Settings.loadListFromDb(args, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true)
        if (!filterAssignmentStatus.isNullOrBlank()) {
            val remoteAssignmentDb = if (target == "remote") Connection.remote() else null
            try {
                val assignmentDb = remoteAssignmentDb ?: WORKING_DATABASE
                val statuses = SongAssignment.composeStatusesForSongIds(settingsList.map { it.id }, assignmentDb, storageService, storageApiClient)
                settingsList = if (filterAssignmentStatus == "unassigned") {
                    settingsList.filter { statuses[it.id] == null }
                } else {
                    settingsList.filter { statuses[it.id]?.second?.dbValue == filterAssignmentStatus }
                }
            } finally {
                try { remoteAssignmentDb?.getConnection()?.close() } catch (_: Exception) {}
            }
        }
        val lst = settingsList.map { it.toDTO().toDtoDigest() }
        var totalMs = 0L
        for (i in lst.indices) {
            if (i > 0) lst[i].idPrevious = lst[i-1].id
            if (i < lst.size-1) lst[i].idNext = lst[i+1].id
            totalMs += lst[i].ms
        }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "songsDigests" to lst,
            "authors" to Settings.loadListAuthors(database = WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE),
            "totalDuration" to convertMillisecondsToDtoTimecode(totalMs)
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
        @RequestParam(required = false) flagExclusive: String?,
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
        @RequestParam(required = false) filterRootId: String?,
        @RequestParam(required = false) pageSize: Int = 30
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
        flagExclusive?.let { if (flagExclusive != "") args["flag_exclusive"] = flagExclusive }
        flagFree?.let { if (flagFree != "") args["flag_free"] = flagFree }
        filterResultVersion?.let { if (filterResultVersion != "") args["filter_result_version"] = filterResultVersion }
        filterVersionBoosty?.let { if (filterVersionBoosty != "") args["filter_version_boosty"] = filterVersionBoosty }
        filterVersionBoostyFiles?.let { if (filterVersionBoostyFiles != "") args["filter_version_boosty_files"] = filterVersionBoostyFiles }
        filterVersionSponsr?.let { if (filterVersionSponsr != "") args["filter_version_sponsr"] = filterVersionSponsr }
        filterVersionDzenKaraoke?.let { if (filterVersionDzenKaraoke != "") args["filter_version_dzen_karaoke"] = filterVersionDzenKaraoke }
        filterVersionVkKaraoke?.let { if (filterVersionVkKaraoke != "") args["filter_version_vk_karaoke"] = filterVersionVkKaraoke }
        filterVersionTelegramKaraoke?.let { if (filterVersionTelegramKaraoke != "") args["filter_version_telegram_karaoke"] = filterVersionTelegramKaraoke }
        filterVersionPlKaraoke?.let { if (filterVersionPlKaraoke != "") args["filter_version_pl_karaoke"] = filterVersionPlKaraoke }
        filterVersionMaxKaraoke?.let { if (filterVersionMaxKaraoke != "") args["filter_version_max_karaoke"] = filterVersionMaxKaraoke }
        filterRate?.let { if (filterRate != "") args["filter_rate"] = filterRate }
        filterStatusProcessLyrics?.let { if (filterStatusProcessLyrics != "") args["filter_status_process_lyrics"] = filterStatusProcessLyrics }
        filterStatusProcessKaraoke?.let { if (filterStatusProcessKaraoke != "") args["filter_status_process_karaoke"] = filterStatusProcessKaraoke }
        filterRootId?.let { if (filterRootId != "") args["filter_root_id"] = filterRootId }

        SongsHistory().add(args)

        val lst = Settings.loadListFromDb(args, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true).map { it.toDTO() }
        for (i in lst.indices) {
            if (i > 0) lst[i].idPrevious = lst[i-1].id
            if (i < lst.size-1) lst[i].idNext = lst[i+1].id
        }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to lst.chunked(pageSize),
            "authors" to Settings.loadListAuthors(database = WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE)
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
        @RequestParam(required = false) filterSettingsId: String?,
        @RequestParam(required = false) filterType: String?,
        @RequestParam(required = false) filterLimit: String?,
        @RequestParam(required = false) pageSize: Int = 30

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterThreadId?.let { if (filterThreadId != "") args["thread_id"] = filterThreadId }
        filterName?.let { if (filterName != "") args["process_name"] = filterName }
        filterStatus?.let { if (filterStatus != "") args["process_status"] = filterStatus }
        filterOrder?.let { if (filterOrder != "") args["process_order"] = filterOrder }
        filterPriority?.let { if (filterPriority != "") args["process_priority"] = filterPriority }
        filterDescription?.let { if (filterDescription != "") args["process_description"] = filterDescription }
        filterSettingsId?.let { if (filterSettingsId != "") args["settings_id"] = filterSettingsId }
        filterType?.let { if (filterType != "") args["process_type"] = filterType }
        filterLimit?.let { if (filterLimit != "") args["filter_limit"] = filterLimit }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() }.chunked(pageSize),
            "statuses" to KaraokeProcessStatuses.entries.toTypedArray(),
            "types" to KaraokeProcessTypes.entries.toTypedArray()
        )
    }

    // Видео проигрыватель: Lyrics
    @PostMapping("/song/playlyrics")
    @ResponseBody
    fun doPlayLyrics(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            settings.playLyrics()
        }
        return true
    }

    // Видео проигрыватель: Karaoke
    @PostMapping("/song/playkaraoke")
    @ResponseBody
    fun doPlayKaraoke(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            settings.playKaraoke()
        }
        return true
    }

    // Видео проигрыватель: Render MP4 (из онлайн-плеера)
    @PostMapping("/song/playrendermp4")
    @ResponseBody
    fun doPlayRenderMp4(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            settings.playRenderMp4()
        }
        return true
    }


    // Видео проигрыватель: Chords
    @PostMapping("/song/playchords")
    @ResponseBody
    fun doPlayChords(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            settings.playChords()
        }
        return true
    }
    // Видео проигрыватель: Tabs
    @PostMapping("/song/playtabs")
    @ResponseBody
    fun doPlayTabs(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            settings.playTabs()
        }
        return true
    }

    // Получение песни
    @PostMapping("/song")
    @ResponseBody
    fun apisSong(@RequestParam id: String): Any? {
        val settCurrId = id.toLong()
        val currSett = Settings.loadFromDbById(id.toLong(), database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.toDTO()
        if (currSett != null) {
            val lst = Settings.loadListFromDb(
                args = mapOf("song_author" to currSett.author),
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true
            )
            for (i in lst.indices) {
                if (lst[i].id == settCurrId) {
                    if (i > 0) currSett.idPrevious = lst[i-1].id
                    if (i < lst.size-1) currSett.idNext = lst[i+1].id
                    break
                }
            }
            currSett.dateTimePublish?.let {
                val leftTime = "%02d".format(currSett.time.split(":")[0].toLong()-1) + ":00"
                val rightTime = "%02d".format(currSett.time.split(":")[0].toLong()+1) + ":00"
                val leftSett = Settings.loadListFromDb(
                    args = mapOf("publish_date" to currSett.date, "publish_time" to leftTime),
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true
                )
                val rightSett = Settings.loadListFromDb(
                    args = mapOf("publish_date" to currSett.date, "publish_time" to rightTime),
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true
                )
                if (leftSett.isNotEmpty()) currSett.idLeft = leftSett[0].id
                if (rightSett.isNotEmpty()) currSett.idRight = rightSett[0].id
            }


            return currSett
        }

        return Settings.loadFromDbById(id.toLong(), database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.toDTO()
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
        @RequestParam(required = false) resultVersion: String?,
        @RequestParam(required = false) diffBeats: String?,
        @RequestParam(required = false) rate: String?,
        @RequestParam(required = false) rootId: String?,
        @RequestParam(required = false) exclusive: String?,
        @RequestParam(required = false) free: String?,
        @RequestParam(required = false) idTariff: String?,
    ): Boolean {
        val settingsId: Long = id.toLong()
        val settings = Settings.loadFromDbById(settingsId, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
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
            idSponsr?.let { sett.fields[SettingField.ID_SPONSR] = it }
            versionBoosty?.let { sett.fields[SettingField.VERSION_BOOSTY] = it }
            versionBoostyFiles?.let { sett.fields[SettingField.VERSION_BOOSTY_FILES] = it }
            versionSponsr?.let { sett.fields[SettingField.VERSION_SPONSR] = it }
            indexTabsVariant?.let { sett.fields[SettingField.INDEX_TABS_VARIANT] = it }
            idVk?.let { sett.fields[SettingField.ID_VK] = it }
            idDzenLyrics?.let { sett.fields[SettingField.ID_DZEN_LYRICS] = it }
            idDzenKaraoke?.let { sett.fields[SettingField.ID_DZEN_KARAOKE] = it }
            idDzenChords?.let { sett.fields[SettingField.ID_DZEN_CHORDS] = it }
            idDzenMelody?.let { sett.fields[SettingField.ID_DZEN_MELODY] = it }
            idVkLyrics?.let { sett.fields[SettingField.ID_VK_LYRICS] = it }
            idVkKaraoke?.let { sett.fields[SettingField.ID_VK_KARAOKE] = it }
            idVkChords?.let { sett.fields[SettingField.ID_VK_CHORDS] = it }
            idVkMelody?.let { sett.fields[SettingField.ID_VK_MELODY] = it }
            idTelegramLyrics?.let { sett.fields[SettingField.ID_TELEGRAM_LYRICS] = it }
            idTelegramKaraoke?.let { sett.fields[SettingField.ID_TELEGRAM_KARAOKE] = it }
            idTelegramChords?.let { sett.fields[SettingField.ID_TELEGRAM_CHORDS] = it }
            idTelegramMelody?.let { sett.fields[SettingField.ID_TELEGRAM_MELODY] = it }
            idPlLyrics?.let { sett.fields[SettingField.ID_PL_LYRICS] = it }
            idPlKaraoke?.let { sett.fields[SettingField.ID_PL_KARAOKE] = it }
            idPlChords?.let { sett.fields[SettingField.ID_PL_CHORDS] = it }
            idPlMelody?.let { sett.fields[SettingField.ID_PL_MELODY] = it }
            idMaxLyrics?.let { sett.fields[SettingField.ID_MAX_LYRICS] = it }
            idMaxKaraoke?.let { sett.fields[SettingField.ID_MAX_KARAOKE] = it }
            idMaxChords?.let { sett.fields[SettingField.ID_MAX_CHORDS] = it }
            idMaxMelody?.let { sett.fields[SettingField.ID_MAX_MELODY] = it }
            versionDzenLyrics?.let { sett.fields[SettingField.VERSION_DZEN_LYRICS] = it }
            versionDzenKaraoke?.let { sett.fields[SettingField.VERSION_DZEN_KARAOKE] = it }
            versionDzenChords?.let { sett.fields[SettingField.VERSION_DZEN_CHORDS] = it }
            versionDzenMelody?.let { sett.fields[SettingField.VERSION_DZEN_MELODY] = it }
            versionVkLyrics?.let { sett.fields[SettingField.VERSION_VK_LYRICS] = it }
            versionVkKaraoke?.let { sett.fields[SettingField.VERSION_VK_KARAOKE] = it }
            versionVkChords?.let { sett.fields[SettingField.VERSION_VK_CHORDS] = it }
            versionVkMelody?.let { sett.fields[SettingField.VERSION_VK_MELODY] = it }
            versionTelegramLyrics?.let { sett.fields[SettingField.VERSION_TELEGRAM_LYRICS] = it }
            versionTelegramKaraoke?.let { sett.fields[SettingField.VERSION_TELEGRAM_KARAOKE] = it }
            versionTelegramChords?.let { sett.fields[SettingField.VERSION_TELEGRAM_CHORDS] = it }
            versionTelegramMelody?.let { sett.fields[SettingField.VERSION_TELEGRAM_MELODY] = it }
            versionPlLyrics?.let { sett.fields[SettingField.VERSION_PL_LYRICS] = it }
            versionPlKaraoke?.let { sett.fields[SettingField.VERSION_PL_KARAOKE] = it }
            versionPlChords?.let { sett.fields[SettingField.VERSION_PL_CHORDS] = it }
            versionPlMelody?.let { sett.fields[SettingField.VERSION_PL_MELODY] = it }
            versionMaxLyrics?.let { sett.fields[SettingField.VERSION_MAX_LYRICS] = it }
            versionMaxKaraoke?.let { sett.fields[SettingField.VERSION_MAX_KARAOKE] = it }
            versionMaxChords?.let { sett.fields[SettingField.VERSION_MAX_CHORDS] = it }
            versionMaxMelody?.let { sett.fields[SettingField.VERSION_MAX_MELODY] = it }
            resultVersion?.let { sett.fields[SettingField.RESULT_VERSION] = it }
            diffBeats?.let { sett.fields[SettingField.DIFFBEATS] = it }
            idStatus?.let { sett.fields[SettingField.ID_STATUS] =  it }
            rate?.let { sett.fields[SettingField.RATE] =  it }
            rootId?.let { sett.fields[SettingField.ROOT_ID] =  it }
            exclusive?.let { sett.fields[SettingField.EXCLUSIVE] =  it }
            free?.let { sett.fields[SettingField.FREE] =  it }
            idTariff?.let { sett.fields[SettingField.ID_TARIFF] =  it }
            sett.saveToDb()
            sett.saveToFile()
        }

        return true
    }

    // Получение процесса
    @PostMapping("/process")
    @ResponseBody
    fun apisProcess(@RequestParam id: String): Any? {
        return KaraokeProcess.load(id.toLong(), WORKING_DATABASE)?.toDTO()
    }

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
        @RequestParam(required = false) type: String

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
    fun getSongVoices(@RequestParam id: Long): Map<String, Any> {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

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

    @PostMapping("/song/picturealbum")
    @ResponseBody
    fun getPictureAlbum(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            val pic = it.pictureAlbum ?: return ""
            return "/api/picture/file?file=${java.net.URLEncoder.encode(pic.storageFileName, java.nio.charset.StandardCharsets.UTF_8)}"
        }
        return ""
    }

    @PostMapping("/song/pictureauthor")
    @ResponseBody
    fun getPictureAuthor(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            val pic = it.pictureAuthor ?: return ""
            return "/api/picture/file?file=${java.net.URLEncoder.encode(pic.storageFileName, java.nio.charset.StandardCharsets.UTF_8)}"
        }
        return ""
    }

    // Получаем дату начала для публикаций
    @PostMapping("/publications/date")
    @ResponseBody
    fun getPublicationsDateFrom(@RequestParam param: String): String {

        val currentCalendar = Calendar.getInstance()
        val currentDateTime = currentCalendar.time

        val formatter = SimpleDateFormat("dd/MM/yyyy")
        val currentDate = formatter.parse(formatter.format(currentDateTime))

        val settings = Settings.loadListFromDb(emptyMap(), database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true)
        val sett = when (param) {
            "STATE_ALL_DONE" -> settings.firstOrNull { it.state == SettingState.ALL_DONE } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_OVERDUE" -> settings.firstOrNull { it.state == SettingState.OVERDUE } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_TODAY" -> settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_ALL_UPLOADED" -> settings.firstOrNull { it.state == SettingState.ALL_UPLOADED } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_TG" -> settings.firstOrNull { it.state == SettingState.WO_TG } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_VK" -> settings.firstOrNull { it.state == SettingState.WO_VK } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_DZEN" -> settings.firstOrNull { it.state == SettingState.WO_DZEN } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATE_WO_VKG" -> settings.firstOrNull { it.state == SettingState.WO_VKG } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_0" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 0L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_1" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 1L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_2" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 2L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_3" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 3L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_4" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 4L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
            "STATUS_6" -> settings.firstOrNull { it.state == SettingState.IN_WORK && it.idStatus == 6L } ?: settings.firstOrNull { it.dateTimePublish != null && formatter.parse(formatter.format(it.dateTimePublish)) == currentDate }
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
        @RequestParam(required = false) sourceMarkers: String = ""): Boolean {
        var result = false
        if (sourceMarkers.trim() != "") {
            val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            result = settings?.let {
                settings.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
                val strText = settings.convertMarkersToSrt(voice)
                val pathToFile = "${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt"
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
        @RequestParam(required = false) sourceText: String = ""): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return settings?.let {
            settings.setSourceText(voice, sourceText)
            settings.updateMarkersFromSourceText(voice)
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
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return settings?.let {
            try {
                Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers)
            } catch (_: Exception) {
                println("Ошибка при парсинге маркеров.")
                emptyList()
            }
            settings.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
            val strText = settings.convertMarkersToSrt(voice)
            try {
                val pathToFile = "${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt"
                File(pathToFile).writeText(strText)
                runCommand(listOf("chmod", "666", pathToFile))
            } catch (_: Exception) {
                println("Ошибка при создании файла субтитров.")
            }
            settings.setSourceText(voice, sourceText)
            settings.setIndexTabsVariant(indexTabsVariant)
            true
        } ?: false
    }

    // Создаём караоке
    @PostMapping("/song/createkaraoke")
    @ResponseBody
    fun getSongCreateKaraoke(@RequestParam id: Long,
                             @RequestParam(required = false) priorLyrics: String? = "0",
                             @RequestParam(required = false) priorKaraoke: String? = "1",
                             @RequestParam(required = false) priorChords: String? = "",
                             @RequestParam(required = false) priorMelody: String? = "",
                             @RequestParam(required = false) threadId: String? = "0",
    ): Boolean {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)


        var type = "danger"
        val head = "Создание караоке"
        var body = "Что-то пошло не так"
        var result = false
        settings?.let {

            val createLyrics = priorLyrics != "" && priorLyrics != null
            val createKaraoke = priorKaraoke != "" && priorKaraoke != null
            val createChords = priorChords != "" && priorChords != null
            val createMelody = priorMelody != "" && priorMelody != null

            settings.createKaraoke(
                createLyrics = createLyrics,
                createKaraoke = createKaraoke,
                createChords = createChords,
                createMelody = createMelody,
            )
            if (createLyrics) {
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, priorLyrics.toInt(), threadId = threadId?.toInt() ?: 0)
            }
            if (createKaraoke) {
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, priorKaraoke.toInt(), threadId = threadId?.toInt() ?: 0)
            }
            if (createChords) {

                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, priorChords.toInt(), threadId = threadId?.toInt() ?: 0)
            }
            if (createMelody) {
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABS, true, priorMelody.toInt(), threadId = threadId?.toInt() ?: 0)
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
    fun getSongsCreateKaraokeAll(@RequestParam songsIds: String,
                                 @RequestParam(required = false) priorLyrics: String? = "10",
                                 @RequestParam(required = false) priorKaraoke: String? = "10",
                                 @RequestParam(required = false) priorChords: String? = "",
                                 @RequestParam(required = false) priorMelody: String? = "",
                                 @RequestParam(required = false) threadId: String? = "0",
    ) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let {
                    val createLyrics = priorLyrics != "" && priorLyrics != null
                    val createKaraoke = priorKaraoke != "" && priorKaraoke != null
                    val createChords = priorChords != "" && priorChords != null
                    val createMelody = priorMelody != "" && priorMelody != null

                    settings.createKaraoke(
                        createLyrics = createLyrics,
                        createKaraoke = createKaraoke,
                        createChords = createChords,
                        createMelody = createMelody,
                    )

                    if (createLyrics) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, priorLyrics.toInt(), threadId = threadId?.toInt() ?: 0)
                    }
                    if (createKaraoke) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, priorKaraoke.toInt(), threadId = threadId?.toInt() ?: 0)
                    }
                    if (createChords) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, priorChords.toInt(), threadId = threadId?.toInt() ?: 0)
                    }
                    if (createMelody) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABS, true, priorMelody.toInt(), threadId = threadId?.toInt() ?: 0)
                    }
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание караоке для всех",
                body = "Создание караоке для всех прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание караоке для всех",
                body = "Что-то пошло не так"
            )))
        }
    }

    // DEMUCS2 для песни
    @PostMapping("/song/demucs2")
    @ResponseBody
    fun doProcessDemucs2(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, prior, threadId = threadId?.toInt() ?: 0)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS2",
                body = "Создание DEMUCS2 прошло успешно"
            )))
            return
        }
        SNS.send(SseNotification.message(Message(
            type = "warning",
            head = "Создание DEMUCS2",
            body = "Что-то пошло не так"
        )))
    }

    // DEMUCS2 для всех
    @PostMapping("/songs/createdemucs2all")
    @ResponseBody
    fun getSongsCreateDemucs2All(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, prior, threadId = threadId?.toInt() ?: 0)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS2",
                body = "Создание DEMUCS2 прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание DEMUCS2",
                body = "Что-то пошло не так"
            )))
        }
    }

    // DEMUCS5 для песни
    @PostMapping("/song/demucs5")
    @ResponseBody
    fun doProcessDemucs5(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, prior, threadId = threadId?.toInt() ?: 0)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS5",
                body = "Создание DEMUCS5 прошло успешно"
            )))
            return
        }
        SNS.send(SseNotification.message(Message(
            type = "warning",
            head = "Создание DEMUCS5",
            body = "Что-то пошло не так"
        )))
    }

    // DEMUCS5 для всех
    @PostMapping("/songs/createdemucs5all")
    @ResponseBody
    fun getSongsCreateDemucs5All(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let {
//                    if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, prior, threadId = threadId?.toInt() ?: 0)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание DEMUCS5",
                body = "Создание DEMUCS5 прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание DEMUCS5",
                body = "Что-то пошло не так"
            )))
        }
    }

    // SHEETSAGE для песни
    @PostMapping("/song/sheetsage")
    @ResponseBody
    fun doProcessSheetsage(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            if (File(it.pathToFileSheetsageMIDI).exists()) return
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE, true, prior, threadId = threadId?.toInt() ?: 0)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание SHEETSAGE",
                body = "Создание SHEETSAGE прошло успешно"
            )))
            return
        }
        SNS.send(SseNotification.message(Message(
            type = "warning",
            head = "Создание SHEETSAGE",
            body = "Что-то пошло не так"
        )))
    }

    // SHEETSAGE для всех
    @PostMapping("/songs/sheetsageall")
    @ResponseBody
    fun getSongsCreateSheetsageAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let {
                    if (!File(it.pathToFileSheetsageMIDI).exists()) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE, true, prior, threadId = threadId?.toInt() ?: 0)
                    }
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание SHEETSAGE",
                body = "Создание SHEETSAGE прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание SHEETSAGE",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Удаляем песню
    @PostMapping("/song/delete")
    @ResponseBody
    fun doDeleteSong(@RequestParam id: Long) {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.deleteFromDb()
    }

//    // Создаём MP3 KARAOKE для песни
//    @PostMapping("/song/mp3karaoke")
//    @ResponseBody
//    fun doMP3Karaoke(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
//        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//        settings?.doMP3Karaoke(prior, threadId = threadId?.toInt() ?: 0)
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
//                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//                settings?.doMP3Karaoke(prior, threadId = threadId?.toInt() ?: 0)
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
//        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//        settings?.doMP3Lyrics(prior, threadId = threadId?.toInt() ?: 0)
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
//                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
//                settings?.doMP3Lyrics(prior, threadId = threadId?.toInt() ?: 0)
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
    fun doSymlink(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.doSymlink(prior, threadId = threadId?.toInt() ?: 0)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание SYMLINK",
            body = "Создание SYMLINK прошло успешно"
        )))
    }

    // Создаём картинку BoostyTeaser для песни
    @PostMapping("/song/createpictureboostyteaser")
    @ResponseBody
    fun doCreatePictureBoostyTeaser(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createBoostyTeaserPicture(settings)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки BoostyTeaser",
                body = "Создание картинки BoostyTeaser прошло успешно"
            )))
        }
    }

    // Создаём картинку SponsrTeaser для песни
    @PostMapping("/song/createpicturesponsrteaser")
    @ResponseBody
    fun doCreatePictureSponsrTeaser(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSponsrTeaserPicture(settings)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки SponsrTeaser",
                body = "Создание картинки SponsrTeaser прошло успешно"
            )))
        }
    }

    // Создаём картинку BoostyFiles для песни
    @PostMapping("/song/createpictureboostyfiles")
    @ResponseBody
    fun doCreatePictureBoostyFiles(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createBoostyFilesPicture(settings)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки BoostyFiles",
                body = "Создание картинки BoostyFiles прошло успешно"
            )))
        }
    }

    // Создаём картинку VK для песни
    @PostMapping("/song/createpicturevk")
    @ResponseBody
    fun doCreatePictureVK(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createVKPicture(settings)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки VK",
                body = "Создание картинки VK прошло успешно"
            )))
        }
    }

    // Создаём картинку VKlink для песни
    @PostMapping("/song/createpicturevklink")
    @ResponseBody
    fun doCreatePictureVKlink(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createVKLinkPicture(settings)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки VKlink",
                body = "Создание картинки VKlink прошло успешно"
            )))
        }
    }

    // Создаём картинку LYRICS для песни
    @PostMapping("/song/createpicturelyrics")
    @ResponseBody
    fun doCreatePictureLyrics(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongPicture(settings, SongVersion.LYRICS)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки LYRICS",
                body = "Создание картинки LYRICS прошло успешно"
            )))
        }
    }

    // Создаём картинку KARAOKE для песни
    @PostMapping("/song/createpicturekaraoke")
    @ResponseBody
    fun doCreatePictureKaraoke(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongPicture(settings, SongVersion.KARAOKE)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки KARAOKE",
                body = "Создание картинки KARAOKE прошло успешно"
            )))
        }
    }

    // Создаём картинку CHORDS для песни
    @PostMapping("/song/createpicturechords")
    @ResponseBody
    fun doCreatePictureChords(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongPicture(settings, SongVersion.CHORDS)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки CHORDS",
                body = "Создание картинки CHORDS прошло успешно"
            )))
        }
    }
    // Создаём картинку TABS для песни
    @PostMapping("/song/createpicturetabs")
    @ResponseBody
    fun doCreatePictureTabs(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongPicture(settings, SongVersion.TABS)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание картинки TABS",
                body = "Создание картинки TABS прошло успешно"
            )))
        }
    }

    // Создаём текстовый файл LYRICS для песни
    @PostMapping("/song/createdescriptionfilelyrics")
    @ResponseBody
    fun doCreateDescriptionFileLyrics(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongDescriptionFile(settings, SongVersion.LYRICS)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание текстового файла LYRICS",
                body = "Создание текстового файла LYRICS прошло успешно"
            )))
        }
    }

    // Создаём текстовый файл KARAOKE для песни
    @PostMapping("/song/createdescriptionfilekaraoke")
    @ResponseBody
    fun doCreateDescriptionFileKaraoke(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongDescriptionFile(settings, SongVersion.KARAOKE)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание текстового файла KARAOKE",
                body = "Создание текстового файла KARAOKE прошло успешно"
            )))
        }
    }

    // Создаём текстовый файл CHORDS для песни
    @PostMapping("/song/createdescriptionfilechords")
    @ResponseBody
    fun doCreateDescriptionFileChords(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongDescriptionFile(settings, SongVersion.CHORDS)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание текстового файла CHORDS",
                body = "Создание текстового файла CHORDS прошло успешно"
            )))
        }
    }
    // Создаём текстовый файл TABS для песни
    @PostMapping("/song/createdescriptionfiletabs")
    @ResponseBody
    fun doCreateDescriptionFileTabs(@RequestParam id: Long) {
        Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            createSongDescriptionFile(settings, SongVersion.TABS)
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание текстового файла TABS",
                body = "Создание текстового файла TABS прошло успешно"
            )))
        }
    }

    // Создаём SYMLINKs для всех
    @PostMapping("/songs/createsymlinksall")
    @ResponseBody
    fun getSongsCreateSymlinksAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1, @RequestParam(required = false) threadId: String? = "0") {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.doSymlink(prior, threadId = threadId?.toInt() ?: 0)
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание SYMLINKs",
                body = "Создание SYMLINKs прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание SYMLINKs",
                body = "Что-то пошло не так"
            )))
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
        @RequestParam(required = false) threadId: String? = "0"
    ) {
        var result = false
        val versions = if (smartCopySongVersion == "ALL") {
            SongVersion.entries
        } else {
            listOf(if (SongVersion.entries.map {it.name}.contains(smartCopySongVersion)) SongVersion.valueOf(smartCopySongVersion) else SongVersion.KARAOKE)
        }

        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let {
                    doSmartCopyForVersions(
                        settings = it,
                        versions = versions,
                        prior = prior,
                        scResolution = smartCopySongResolution,
                        scCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors ?: false,
                        scRenameTemplate = smartCopyRenameTemplate ?: "",
                        scPath = smartCopyPath,
                        threadId = threadId?.toInt() ?: 0
                    )
                }
                result = true
            }
        }
        sendSmartCopyResultNotification(result)
    }

    // Общая часть getSmartCopyAll/getSmartCopyPeriodByDay: копирование одного набора Settings во всех версиях
    private fun doSmartCopyForVersions(
        settings: Settings,
        versions: List<SongVersion>,
        prior: Int,
        scResolution: String,
        scCreateSubfoldersAuthors: Boolean,
        scRenameTemplate: String,
        scPath: String,
        threadId: Int
    ) {
        versions.forEach { scVersion ->
            settings.doSmartCopy(
                prior = prior,
                scVersion = scVersion,
                scResolution = scResolution,
                scCreateSubfoldersAuthors = scCreateSubfoldersAuthors,
                scRenameTemplate = scRenameTemplate,
                scPath = scPath,
                threadId = threadId
            )
        }
    }

    private fun sendSmartCopyResultNotification(success: Boolean) {
        if (success) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание Smart Copy",
                body = "Создание Smart Copy прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание Smart Copy",
                body = "Что-то пошло не так"
            )))
        }
    }

    // SmartCopyAll
    @PostMapping("/songs/smartcopyperodbyday")
    @ResponseBody
    fun getSmartCopyPeriodByDay(
        @RequestParam periodStart: String,
        @RequestParam periodEnd: String,
        @RequestParam smartCopyPathPrefix: String
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

            val filterString =  currentDate.format(formatterDDMMYY)
            val dayFolder =  currentDate.format(formatterYYYYDDMM)
            val smartCopyPath = "$smartCopyPathPrefix/$dayFolder"

            val settingsList = Settings.loadListFromDb(args = mapOf("publish_date" to filterString), database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            settingsList.forEach { settings ->
                doSmartCopyForVersions(
                    settings = settings,
                    versions = versions,
                    prior = prior,
                    scResolution = smartCopySongResolution,
                    scCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors,
                    scRenameTemplate = smartCopyRenameTemplate,
                    scPath = smartCopyPath,
                    threadId = threadId
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
    fun getFindSongText(@RequestParam id: Long): List<FindSongResult> {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return  settings?.let {
            findSongText(settings = settings)
        } ?: emptyList()
    }

    // Ищем и возвращаем текст
    @PostMapping("/song/searchsongtext")
    @ResponseBody
    fun getSearchSongText(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return  settings?.let {
            findSongText(settings = settings, countInResult = 1).firstOrNull()?.findedText?: ""
        } ?: ""
    }

    // Ищем тексты для всех
    @PostMapping("/songs/searchsongtextall")
    @ResponseBody
    fun getSearchSongTextAll(@RequestParam songsIds: String): Boolean {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let {
                    println("settings.haveSourceText = ${settings.haveSourceText}")
                    if (!settings.haveSourceText || ids.size == 1) {
//                        getYandexSearch(settings = settings, async = true)
                        getSearXNGSearch(settings = settings, lyricsFinderService = lyricsFinderService)
                    }
                }
                result = true
            }
        }
        return result
    }

    // Пакетная автопривязка оригинала по аудио-сверке для всех песен со статусом 1 (TEXT_CREATE) и
    // ненулевым root_id: для каждой находим наиболее похожий по аудио вариант из "семьи" (порог threshold%),
    // применяем его (текст/маркеры со сдвигом) как выбор в модалке "Похожие версии песни", сохраняем
    // (пересчёт производных полей + .srt) и переводим песню в статус 2 (TEXT_CHECK). Тяжёлая операция
    // (ffmpeg-декод на каждого кандидата) — уходит в фоновый поток, прогресс/итог печатается в консоль
    // и присылается тостом по SSE.
    @PostMapping("/songs/autoassignoriginalall")
    @ResponseBody
    fun autoAssignOriginalAll(
        @RequestParam(required = false) author: String? = null,
        @RequestParam(required = false) threshold: Int = 85
    ): Boolean {
        val authorFilter = author?.trim()?.takeIf { it.isNotEmpty() }
        thread {
            val ids = mutableListOf<Long>()
            try {
                val connection = WORKING_DATABASE.getConnection()
                if (connection != null) {
                    // Колонка автора в tbl_settings — song_author (не author); сравнение регистронезависимо.
                    val sql = "SELECT id FROM tbl_settings WHERE id_status = 1 AND root_id <> 0" +
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
                    val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                    if (settings == null) {
                        skipped++
                        println("  [${index + 1}/${ids.size}] id=$id — пропущено (не удалось загрузить)")
                        return@forEachIndexed
                    }
                    val result = autoAssignOriginalByWaveform(settings, WORKING_DATABASE, storageService, storageApiClient, threshold)
                    if (result.matched) matched++ else skipped++
                    println("  [${index + 1}/${ids.size}] ${songLogLabel(settings)} — ${result.reason}")
                } catch (e: Exception) {
                    skipped++
                    println("  [${index + 1}/${ids.size}] id=$id — ошибка: ${e.message}")
                }
            }
            println("Автопривязка оригинала: завершено. Обработано ${ids.size}, привязано $matched, пропущено $skipped.")

            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Автопривязка оригинала ($scope)",
                body = "Обработано ${ids.size}, привязано $matched, пропущено $skipped (порог $threshold%)"
            )))
        }
        return true
    }

    @PostMapping("/song/setpublishdatetimetoauthor")
    @ResponseBody
    fun doSetPublishDateTimeToAuthor(@RequestParam id: Long, @RequestParam(required = false) skipPublished: Boolean = false) {
        val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        settings?.let {
            Settings.setPublishDateTimeToAuthor(settings, skipPublished = skipPublished)
        }
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Даты публикации",
            body = "Изменение дат публикаций для автора прошло успешно"
        )))
    }

    // Заменяем символы в тексте
    @PostMapping("/replacesymbolsinsong")
    @ResponseBody
    fun getReplaceSymbolsInSong(@RequestParam(required = true) txt: String): String {
        return replaceSymbolsInSong(txt)
    }

    // Действия со словарями
    @PostMapping("/utils/tfd")
    @ResponseBody
    fun doTextFileDictionary(
        @RequestParam(required = true) dictName: String,
        @RequestParam(required = true) dictValue: String,
        @RequestParam(required = true) dictAction: String
    ) {
        TextFileDictionary.doAction(dictName, dictAction, listOf(dictValue))
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Действия со словарями",
            body = "Действие «$dictAction», словарь «$dictName», слово «$dictValue» прошло успешно"
        )))
    }

    // Разовый импорт значений словарей из старых текстовых файлов (/sm-karaoke/system/*.txt) в
    // tbl_dictionaries. Идемпотентно — повторный вызов не создаёт дублей (UNIQUE-индекс).
    @PostMapping("/dictionaries/importfromfiles")
    @ResponseBody
    fun doImportDictionariesFromFiles(): Map<String, Int> {
        val filesByDictName = mapOf(
            "Слова с Ё" to YO_FILE_PATH,
            "Censored" to CENSORED_FILE_PATH,
            "Sync Ids" to SYNCIDS_FILE_PATH
        )
        val result = filesByDictName.mapValues { (dictName, filePath) ->
            Dictionary.importFromFile(dictName = dictName, filePath = filePath, database = WORKING_DATABASE)
        }
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Импорт словарей",
            body = "Импортировано новых значений: " + result.entries.joinToString(", ") { "${it.key} — ${it.value}" }
        )))
        return result
    }

    // Обновляем одну картинку в RemoteDatabase
    @PostMapping("/utils/updateremotepicturefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemotePictureFromLocalDatabase(
        @RequestParam(required = true) id: Long
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) = updateRemotePictureFromLocalDatabase(id)
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Обновляем одну песню в RemoteDatabase
    @PostMapping("/utils/updateremotesettingsfromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteSettingFromLocalDatabase(
        @RequestParam(required = true) id: Long
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) = updateRemoteSettingFromLocalDatabase(id)
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Добавляем одну песню в SYNC-таблицу
    @PostMapping("/utils/tosync")
    @ResponseBody
    fun doSetSettingsToSyncRemoteTable(
        @RequestParam(required = true) id: Long
    ) {
        setSettingsToSyncRemoteTable(id)
        val body = "Запись ${Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.fileName} добавлена в SYNC-таблицу"
        SNS.send(SseNotification.message(
            Message(
                type = "info",
                head = "SYNC",
                body = body
            )
        ))
        println(body)
    }

    // Обновляем RemoteDatabase
    @PostMapping("/utils/updateremotedatabasefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteDatabaseFromLocalDatabase(
        @RequestParam(required = false) updateSettings: Boolean = true,
        @RequestParam(required = false) updatePictures: Boolean = true,
        @RequestParam(required = false) updateAuthors: Boolean = true
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) = updateRemoteDatabaseFromLocalDatabase(
            updateSettings = updateSettings,
            updatePictures = updatePictures,
            updateAuthors = updateAuthors)
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Обновляем LocalDatabase
    @PostMapping("/utils/updatelocaldatabasefromremotedatabase")
    @ResponseBody
    fun doUpdateLocalDatabaseFromRemoteDatabase(
        @RequestParam(required = false) updateSettings: Boolean = true,
        @RequestParam(required = false) updatePictures: Boolean = true,
        @RequestParam(required = false) updateAuthors: Boolean = true
    ): List<List<String>> {
        val (listCreate, listUpdate, listDelete) = updateLocalDatabaseFromRemoteDatabase(
            updateSettings = updateSettings,
            updatePictures = updatePictures,
            updateAuthors = updateAuthors)
        if (listCreate.size + listUpdate.size + listDelete.size != 0) {
            SNS.send(SseNotification.crud(listOf(listCreate, listUpdate, listDelete)))
        }
        return listOf(listCreate, listUpdate, listDelete)
    }

    // Универсальная синхронизация LOCAL<->SERVER (webvue3, раздел "Синхронизация") — по любой
    // сущности SyncRegistry (Settings/Pictures/Authors/SiteUsers/Events), в любую сторону, с проверкой
    // разрешения через sync_<key>_push/pull_allowed (см. KaraokeProperties.kt).
    private fun syncEntityInfo(target: SyncTarget<*>): SyncEntityInfoDto = SyncEntityInfoDto(
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
    fun getSyncEntities(): List<SyncEntityInfoDto> {
        return SyncRegistry.all.map { syncEntityInfo(it) }
    }

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
        val target = SyncRegistry.byKey(key)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "unknown_key"))
        val syncDirection = when (direction) {
            "PUSH" -> SyncDirection.LOCAL_TO_SERVER
            "PULL" -> SyncDirection.SERVER_TO_LOCAL
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "unknown_direction"))
        }
        val op = when (operation) {
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
        val target = SyncRegistry.byKey(key)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "unknown_key"))
        val syncDirection = when (direction) {
            "PUSH" -> SyncDirection.LOCAL_TO_SERVER
            "PULL" -> SyncDirection.SERVER_TO_LOCAL
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "unknown_direction"))
        }
        if (!target.isAllowed(syncDirection)) {
            return ResponseEntity.status(403).body(mapOf(
                "error" to "sync_not_allowed",
                "message" to "Синхронизация «${target.displayName}» в этом направлении запрещена настройками"
            ))
        }
        val result = runEntitySync(key = target.key, direction = syncDirection, id = id)
        val (created, updated, deleted, moved) = result
        if (created.size + updated.size + deleted.size + moved.size != 0) {
            SNS.send(SseNotification.crud(listOf(created, updated, deleted)))
        }
        return ResponseEntity.ok(SyncRunResultDto(created, updated, deleted, moved))
    }

    @PostMapping("/sync/oneclick")
    @ResponseBody
    fun postSyncOneClick(): List<SyncOneClickResultDto> {
        return SyncRegistry.all.map { target ->
            val direction = target.oneClickDirection
            if (!target.isAllowed(direction)) {
                SyncOneClickResultDto(
                    key = target.key, displayName = target.displayName, direction = direction.name,
                    skipped = true, created = emptyList(), updated = emptyList(), deleted = emptyList(), moved = emptyList()
                )
            } else {
                val (created, updated, deleted, moved) = runEntitySync(key = target.key, direction = direction)
                if (created.size + updated.size + deleted.size + moved.size != 0) {
                    SNS.send(SseNotification.crud(listOf(created, updated, deleted)))
                }
                SyncOneClickResultDto(
                    key = target.key, displayName = target.displayName, direction = direction.name,
                    skipped = false, created = created, updated = updated, deleted = deleted, moved = moved
                )
            }
        }
    }

    // Добавление файлов из папки
    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String) {
        val createdList = Settings.createFromPath(folder, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        createdList.forEach { newSettings ->
            val original = findDuplicateOriginal(newSettings, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            if (original != null) {
                applyDuplicateOriginal(newSettings, original)
            } else {
                thread(start = true) {
                    try {
                        getSearXNGSearch(settings = newSettings, lyricsFinderService = lyricsFinderService)
                    } catch (e: Exception) {
                        println("[${Timestamp.from(Instant.now())}] doCreateFromFolder - ошибка фонового поиска текста для песни id=${newSettings.id}: ${e.message}")
                    }
                }
            }
        }
        val result = createdList.size
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Добавление файлов из папки",
            body = "Добавлено файлов из папки «$folder»: $result"
        )))
    }

    // Создание картинок Dzen для папки
    @PostMapping("/utils/createdzenpicturesforfolder")
    @ResponseBody
    fun doCreateDzenPicturesForFolder(
        @RequestParam(required = true) folder: String) {
        createDzenPicture(folder)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание картинок Dzen для папки",
            body = "Создание картинок Dzen для папки «$folder» прошло успешно"
        )))
    }

    @PostMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(@RequestParam(required = false) songsIds: String = "",
                       @RequestParam(required = false) priorLyrics: Int = 10,
                       @RequestParam(required = false) priorKaraoke: Int = 10,
                       @RequestParam(required = false) threadId: String? = "0"): Any {
        val settingsList = if (songsIds == "") {
            Settings.loadListFromDb(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true)
        } else {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            val result: MutableList<Settings> = mutableListOf()
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                settings?.let { result.add(it) }
            }
            result.toList()
        }

        val (countCopy, countCode) = collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(
            settingsList = settingsList, priorLyrics = priorLyrics, priorKaraoke = priorKaraoke, threadId = threadId?.toInt() ?: 0)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление хранилища",
            body = "Скопировано песен в хранилище: $countCopy, создано заданий на кодирование: $countCode"
        )))
        return listOf(countCopy, countCode)
    }


    // Обновить пустые BPM и KEY из фалов CSV
    @PostMapping("/utils/updatebpmandkey")
    @ResponseBody
    fun doUpdateBpmAndKey() {
        val result =  updateBpmAndKey(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление BPM и KEY из фалов CSV",
            body = "Обновлено пустых BPM и KEY из фалов CSV: $result"
        )))
    }

    // Обновить пустые BPM и KEY из фалов LV
    @PostMapping("/utils/updatebpmandkeylv")
    @ResponseBody
    fun doUpdateBpmAndKeyLV() {
        val (resultSuccess, resultFailed) =  updateBpmAndKeyLV(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление BPM и KEY из файлов LV",
            body = "Обновлено пустых BPM и KEY из файлов LV: $resultSuccess" + if(resultFailed == 0) "" else ", Не удалось обновить файлов: $resultFailed"
        )))
    }

    // Найти и пометить дубликаты песен автора
    @PostMapping("/utils/markdublicates")
    @ResponseBody
    fun doMarkDublicates(
        @RequestParam(required = true) author: String) {
        val result = findAndFillDublicates(author, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Нахождение дубликатов",
            body = "Найдено и обработано дубликатов: $result"
        )))
    }

    // Удалить дубликаты
    @PostMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates() {
        val result = delDublicates(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Удаление дубликатов",
            body = "Удалено дубликатов: $result"
        )))
    }

    // Очистить информацию о пре-дубликатах
    @PostMapping("/utils/clearpredublicates")
    @ResponseBody
    fun doClearPreDublicates() {
        val result = clearPreDublicates(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Очистка пре-дубликатов",
            body = "Очищено пре-дубликатов: $result"
        )))
    }

    // Выполнить Custom Function
    @PostMapping("/utils/customfunction")
    @ResponseBody
    fun doCustomFunction() {
        val result = customFunction(storageService = storageService, storageApiClient = storageApiClient, lyricsFinderService = lyricsFinderService)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Custom Function",
            body = "CustomFunction выполнена с результатом: «$result»"
        )))
    }

    // Актуализация VKLinkPictureWeb
//    @PostMapping("/utils/actualizevklinkpictureweb")
//    @ResponseBody
//    fun doActualizeVKLinkPictureWeb() {
//        var cntSkip = 0
//        var cntDelete = 0
//        var cntCreate = 0
//
//        Settings.loadListFromDb(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, withoutMarkersAndText = true).forEach { settings ->
//            when (createVKLinkPictureWeb(settings, false)) {
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
    fun getProcessWorkerStatus(): Map<String, Any> {
        return mapOf("isWork" to KaraokeProcessWorker.isWork, "stopAfterThreadIsDone" to KaraokeProcessWorker.stopAfterThreadIsDone)
    }

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
        response: HttpServletResponse
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
    fun getProperties(): Map<String, Any> {
        return mapOf("properties" to KaraokeProperties.getDTOs())
    }

    // Получаем property
    @PostMapping("/properties/getproperty")
    @ResponseBody
    fun getProperty(@RequestParam key: String): Map<String, Any> {
        return mapOf("property" to KaraokeProperties.getDTO(key))
    }

    // Изменяем property
    @PostMapping("/properties/setproperty")
    @ResponseBody
    fun setProperty(@RequestParam key: String, @RequestParam stringValue: String): Map<String, Any> {
        KaraokeProperties.setFromString(key, stringValue)
        if (key == "resourceLimitsEnabled" || key.startsWith("cpuLimitPercent")) {
            applyLiveCpuLimitToRunningProcesses()
        }
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "SET PROPERTY",
            body = "Свойство «$key» установлено в значение «$stringValue»"
        )))
        return getProperty(key)
    }

    // Изменяем property к значению по умолчанию
    @PostMapping("/properties/setpropertydefault")
    @ResponseBody
    fun setPropertyDefault(@RequestParam key: String) {
        KaraokeProperties.setDefault(key)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "SET PROPERTY",
            body = "Свойство «$key» установлено в значение по умолчанию"
        )))
    }

    @PostMapping("/propertiesdigests")
    @ResponseBody
    fun apisPropertiesDigest(
        @RequestParam(required = false) filterKey: String?,
        @RequestParam(required = false) filterValue: String?,
        @RequestParam(required = false) filterDefaultValue: String?,
        @RequestParam(required = false) filterDescription: String?,
        @RequestParam(required = false) filterType: String?
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
            "types" to KaraokeProperties.types()
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
        @RequestParam(required = false) aliases: String?
    ): Long {

        Author.getAuthorById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let {
            it.author = author
            it.ymId = ymId
            it.vkId = vkId
            it.lastAlbumYm = lastAlbumYm
            it.lastAlbumVk = lastAlbumVk?:""
            it.lastAlbumProcessed = lastAlbumProcessed
            it.watched = watched
            it.skip = skip
            aliases?.let { a -> it.aliases = a }
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
        @RequestParam(required = false) filterSkip: String?
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
        val authorsList = Author.loadList(
            whereArgs = args,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
            ignoreUseInList = true
        ).map { it.toDTO() }.sorted()

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "authorsDigests" to authorsList
        )
    }

    data class FileDTO(
        val name: String,
        val path: String,
        val extension: String,
        val nameWithoutExtension: String,
        val parent: String,
        val length: Long,
        val isDirectory: Boolean
    ): Comparable<FileDTO> {
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
        @RequestParam(required = false) extensions: String?
    ): List<FileDTO> {
        var directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            directory = File("/")
            if (!directory.exists() || !directory.isDirectory) {
                throw IllegalArgumentException("Invalid directory path")
            }
        }

        return directory.listFiles()?.mapNotNull { file ->
            val needToAdd = file.isDirectory || (extensions.isNullOrBlank() || file.extension.lowercase() in extensions.split(";").map { it.lowercase() })
            if (needToAdd) {
                FileDTO(
                    name = file.name,
                    path = file.absolutePath,
                    extension = file.extension,
                    nameWithoutExtension = file.nameWithoutExtension,
                    parent = file.parent,
                    length = file.length(),
                    isDirectory = file.isDirectory
                )
            } else null
        }?.sorted() ?: emptyList()
    }

    @PostMapping("/pictures/updatepicture")
    @ResponseBody
    fun apisUpdatePicture(
        @RequestParam(required = true) id: Long,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) full: String?,
        @RequestParam(required = false) @Suppress("unused") preview: String?
    ): Long {

        Pictures.getPictureById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { pic ->
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
        @RequestParam(required = false) filterName: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filterId?.let { if (filterId != "") args["id"] = filterId }
        filterName?.let { if (filterName != "") args["picture_name"] = filterName }
        val picturesDigests = Pictures.loadList(whereArgs = args, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient, ignoreUseInList = false).map { it.toDTO() }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "picturesDigests" to picturesDigests
        )
    }

    @PostMapping("/picture")
    @ResponseBody
    fun apisPicture(@RequestParam id: String): Any? = Pictures.getPictureById(id = id.toLong(), database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.toDTO()

    @PostMapping("/picture/delete")
    @ResponseBody
    fun doDeletePicture(@RequestParam id: Long) {
        Pictures.delete(id = id, database = WORKING_DATABASE)
    }
    @PostMapping("/picture/savetodisk")
    @ResponseBody
    fun doSavePictureToDisk(@RequestParam id: Long) {
        Pictures.getPictureById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.saveToDisk()
    }

    @PostMapping("/picture/loadfromdisk")
    @ResponseBody
    fun doLoadPictureFromDisk(@RequestParam pathToFile: String): String {
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
    fun getPictureFile(@RequestParam file: String): ResponseEntity<ByteArray> {
        val bucket = "karaoke"
        if (storageService.fileExists(bucket, file)) {
            val bytes = storageService.downloadFile(bucket, file).use { it.readBytes() }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        }
        val isAuthor = file.endsWith(".preview.author.png")
        val isAlbum = file.endsWith(".preview.album.png")
        if (!isAuthor && !isAlbum) return ResponseEntity.notFound().build()
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

    @PostMapping("/getwebvueprop")
    @ResponseBody
    fun getWebvueProperty(
        @RequestParam(required = true) key: String,
        @RequestParam(required = false) default: String?
    ): String {
        val result = WVP.get(key = key, default = (default ?: ""))
        return result
    }


    @PostMapping("/setwebvueprop")
    @ResponseBody
    fun setWebvueProperty(
        @RequestParam(required = true) key: String,
        @RequestParam(required = true) value: String
    ) {
        WVP.set(key = key, value = value)
    }

    @PostMapping("/getdict")
    @ResponseBody
    fun getDict(
        @RequestParam(required = true) dict: String
    ): List<String> {
        return TextFileDictionary.loadList(dict)
    }

    @PostMapping("/getfreetimeslots")
    @ResponseBody
    fun getFreeTS(): List<String> {
        return getFreeTimeSlots()
    }

    @PostMapping("/songs/addsyncforall")
    @ResponseBody
    fun addSyncForAll(@RequestParam songsIds: String): List<String> {
        val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
        val listSync = setSettingsToSyncRemoteTable(ids)

        if (listSync.isNotEmpty()) {
            SNS.send(SseNotification.sync(listOf(listSync)))
        }
        SyncIdsDictionary().clear()
        return listSync

    }

    @PostMapping("/song/keyBpmFinder")
    @ResponseBody
    fun createKeyBpmFinderProcess(@RequestParam id: Long) {
        Settings.loadFromDbById(
            id = id,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        )?.let { settings ->
            KaraokeProcess.createProcess(
                settings = settings,
                action = KaraokeProcessTypes.KEY_BPM_FROM_FILE,
                doWait = true,
                prior = -1,
                threadId = 1
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
    ): Map<String, Any> {
        val settings = Settings.loadFromDbById(
            id = id,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        ) ?: return mapOf("ok" to false, "message" to "Песня не найдена: id=$id")

        val processId = KaraokeProcess.createProcess(
            settings = settings,
            action = KaraokeProcessTypes.RENDER_MP4,
            doWait = true,
            prior = 1,
            threadId = 0,
            context = mapOf(
                "width" to (width ?: 1920),
                "height" to (height ?: 1080),
                "fps" to (fps ?: 60)
            )
        )
        return if (processId > 0) {
            mapOf("ok" to true, "processId" to processId, "message" to "Рендер MP4 поставлен в очередь (processId=$processId)")
        } else {
            mapOf("ok" to false, "message" to "Не удалось поставить в очередь (возможно, уже выполняется)")
        }
    }

    // Статус рендера MP4
    @PostMapping("/song/renderMp4Status")
    @ResponseBody
    fun getRenderMp4Status(@RequestParam id: Long): Map<String, Any> {
        val processes = KaraokeProcess.loadList(
            mapOf("settings_id" to id.toString(), "process_type" to KaraokeProcessTypes.RENDER_MP4.name),
            WORKING_DATABASE
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
                "end" to (latest.endStr ?: "")
            )
        } else {
            mapOf("ok" to true, "status" to "NONE")
        }
    }

    // Скачивание отрендеренного MP4
    @GetMapping("/song/renderMp4Download")
    fun downloadRenderedMp4(@RequestParam id: Long): ResponseEntity<Resource> {
        val file = File("$PATH_TO_TEMP_RENDERMP4_FOLDER/$id/output.mp4")
        if (!file.exists()) return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"song_$id.mp4\"")
            .body(FileSystemResource(file))
    }

    // Получение healthReportList
    @PostMapping("/song/healthReportList")
    @ResponseBody
    fun getHealthReportList(@RequestParam id: Long): List<HealthReportDTO> {
        return HealthReport.recomputeAndBroadcast(
            settingsId = id,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        ).errorsOnly().map { it.toDTO() }
    }

    // Каскадное «Исправить всё»: помечает песню как «в авто-ремонте» и выполняет всё решаемое сейчас.
    // Дальнейшие шаги цепочки (upload в локальное/удалённое хранилище после создания файла на диске)
    // ставятся автоматически из пост-хука воркера по мере завершения предыдущих задач.
    @PostMapping("/song/repairAll")
    @ResponseBody
    fun repairAll(@RequestParam id: Long) {
        Settings.loadFromDbById(
            id = id,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        )?.let { settings ->
            HealthReport.startRepairAll(
                settings = settings,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient
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
        @RequestParam description: String
    ) {
        Settings.loadFromDbById(
            id = id,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        )?.let { settings ->
            val healthReportDTO = HealthReportDTO(
                settingsId = id,
                healthReportTypeName = healthReportTypeName,
                healthReportStatusName = healthReportStatusName,
                description = description
            )
            HealthReport.getHealthReport(settings = settings, dto = healthReportDTO)?.executeSolutionActions()
        }
    }

    // Получение SearchAsyncListBySongId
    @PostMapping("/song/searchasync")
    @ResponseBody
    fun getSearchAsyncList(@RequestParam songId: Long): List<SearchAsyncDTO> {
        val result = SearchAsync.getSearchAsyncListBySongId(
            songId = songId,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        ).map { it.toDTO() }
        return result
    }

    // Получение SearchResultListBySearchAsyncId
    @PostMapping("/song/searchresult")
    @ResponseBody
    fun getSearchResultList(@RequestParam searchAsyncId: Long): List<SearchResultDTO> {
        val result = SearchResult.getSearchResultListBySearchAsyncId(
            searchAsyncId = searchAsyncId,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient
        ).map { it.toDTO() }
        return result
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
            val process = ProcessBuilder("ffmpeg", "-i", flacPath, "-codec:a", "libmp3lame", "-qscale:a", "2", "-y", mp3File.absolutePath)
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
    // REMOTE_STORAGE location: "${settings.storageFileName}${suffix}.${extention}" — suffix already
    // carries its own leading dot (e.g. ".accompaniment"), NOT a dash.
    private fun pushMp3ToStorage(mp3File: File, settings: Settings, fileType: KaraokeFileType) {
        val bucket = "karaoke"
        val storageKey = "${settings.storageFileName}${fileType.suffix}.${fileType.extention}"
        if (!storageService.fileExists(bucket, storageKey)) {
            storageService.uploadFile(bucket, storageKey, mp3File.absolutePath)
        }
    }

    @GetMapping("/song/{id}/fileminus.mp3")
    fun getSongFileMusicMp3(@PathVariable id: Long): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            convertFlacToMp3(settings.accompanimentNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, settings, KaraokeFileType.MP3_ACCOMPANIMENT)
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filevoice.mp3")
    fun getSongFileVocalMp3(@PathVariable id: Long): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            convertFlacToMp3(settings.vocalsNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, settings, KaraokeFileType.MP3_VOCAL)
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filebass.mp3")
    fun getSongFileBassMp3(@PathVariable id: Long): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            convertFlacToMp3(settings.bassNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, settings, KaraokeFileType.MP3_BASS)
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    @GetMapping("/song/{id}/filedrums.mp3")
    fun getSongFileDrumsMp3(@PathVariable id: Long): ResponseEntity<Resource> {
        Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)?.let { settings ->
            convertFlacToMp3(settings.drumsNameFlac)?.let { mp3File ->
                pushMp3ToStorage(mp3File, settings, KaraokeFileType.MP3_DRUMS)
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(FileSystemResource(mp3File))
            }
        }
        return ResponseEntity.notFound().build()
    }

    // assignmentId (опционально) — превью НЕОДОБРЕННОГО черновика онлайн-редактора (tbl_song_assignment_drafts):
    // до approve() правки живут только в drafts, tbl_settings их не видит. Задание покрывает ВСЮ песню —
    // черновик несёт ВСЕ голоса разом, подменяем весь sourceMarkersList целиком. assignment должен
    // принадлежать именно этой песне (id) — иначе подмена игнорируется (fail-safe, без утечки чужого черновика).
    // target (опционально, только вместе с assignmentId) — где реально читать задание/черновик: реальный
    // рабочий цикл онлайн-редактора часто идёт целиком на REMOTE (см. SongEditorController), а local ещё
    // не синкнут — без этого параметра подстановка молча не находила черновик и превью показывало пустой
    // текст. Settings (метаданные, аудио с локального диска) всегда из WORKING_DATABASE — id совпадает.
    @GetMapping("/song/{id}/playerdata")
    @ResponseBody
    fun getSongPlayerData(@PathVariable id: Long, @RequestParam(required = false) assignmentId: Long?, @RequestParam(required = false) target: String?): ResponseEntity<Map<String, Any?>> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            ?: return ResponseEntity.notFound().build()

        var markersList = settings.sourceMarkersList
        if (assignmentId != null) {
            val remoteDb = if (target == "remote") Connection.remote() else null
            try {
                val assignmentDb = remoteDb ?: WORKING_DATABASE
                val assignment = SongAssignment.getById(assignmentId, assignmentDb, storageService, storageApiClient)
                val draft = assignment?.takeIf { it.songId == id }
                    ?.let { SongAssignmentDraft.getByAssignment(it.id, assignmentDb, storageService, storageApiClient) }
                if (draft != null) {
                    val draftMarkersPerVoice = draft.editedMarkersPerVoice(lenientJson)
                    if (draftMarkersPerVoice.any { it.isNotEmpty() }) markersList = draftMarkersPerVoice
                }
            } finally {
                try { remoteDb?.getConnection()?.close() } catch (_: Exception) {}
            }
        }

        val data = mapOf(
            "id" to id,
            "songName" to settings.songName,
            "author" to settings.author,
            "album" to settings.album,
            "year" to settings.year.takeIf { it > 0 },
            "track" to settings.track.takeIf { it > 0 },
            "key" to settings.key.takeIf { it.isNotBlank() },
            "bpm" to settings.bpm,
            "markers" to markersList,
            "audioAccompanimentUrl" to "/api/song/$id/fileminus.mp3",
            "audioVocalsUrl" to "/api/song/$id/filevoice.mp3",
            "audioBassUrl" to if (File(settings.bassNameFlac).exists()) "/api/song/$id/filebass.mp3" else null,
            "audioDrumsUrl" to if (File(settings.drumsNameFlac).exists()) "/api/song/$id/filedrums.mp3" else null,
            "albumImageUrl" to settings.pictureAlbum?.storageFileName?.let { "/api/picture/file?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}" },
            "artistImageUrl" to settings.pictureAuthor?.storageFileName?.let { "/api/picture/file?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}" },
            "exportBaseName" to "${settings.fileName} [id-$id]".rightFileName()
        )
        return ResponseEntity.ok(data)
    }

    // Generates a .smkaraoke container (ZIP): manifest.json + audio MP3s + images from MinIO.
    // Media files are STORED (no recompression); manifest is DEFLATED.
    // Optional fields (tracks/images) are present only if the source files actually exist.
    @GetMapping("/song/{id}/playerfile")
    fun getSongPlayerFile(@PathVariable id: Long, response: HttpServletResponse) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            ?: run { response.status = 404; return }

        val bucket = "karaoke"
        val tracks = mutableMapOf<String, String>()
        val images = mutableMapOf<String, String>()

        val bos = ByteArrayOutputStream()
        val zip = ZipOutputStream(bos)

        convertFlacToMp3(settings.accompanimentNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/accompaniment.mp3", mp3.readBytes())
            tracks["accompaniment"] = "audio/accompaniment.mp3"
        }
        convertFlacToMp3(settings.vocalsNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/vocals.mp3", mp3.readBytes())
            tracks["vocals"] = "audio/vocals.mp3"
        }
        convertFlacToMp3(settings.bassNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/bass.mp3", mp3.readBytes())
            tracks["bass"] = "audio/bass.mp3"
        }
        convertFlacToMp3(settings.drumsNameFlac)?.let { mp3 ->
            smkaraokeAddStored(zip, "audio/drums.mp3", mp3.readBytes())
            tracks["drums"] = "audio/drums.mp3"
        }
        settings.pictureAlbum?.let { pic ->
            if (storageService.fileExists(bucket, pic.storageFileName)) {
                val bytes = storageService.downloadFile(bucket, pic.storageFileName).use { it.readBytes() }
                smkaraokeAddStored(zip, "images/album.png", bytes)
                images["album"] = "images/album.png"
            }
        }
        settings.pictureAuthor?.let { pic ->
            if (storageService.fileExists(bucket, pic.storageFileName)) {
                val bytes = storageService.downloadFile(bucket, pic.storageFileName).use { it.readBytes() }
                smkaraokeAddStored(zip, "images/artist.png", bytes)
                images["artist"] = "images/artist.png"
            }
        }

        // Embed app icon so OS file managers can associate a custom icon after type registration
        val iconBytes = javaClass.classLoader?.getResourceAsStream("smkaraoke-icon.ico")?.readBytes()
        if (iconBytes != null) smkaraokeAddStored(zip, "icon.ico", iconBytes)

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
            "icon" to if (iconBytes != null) "icon.ico" else null,
            "exportBaseName" to "${settings.fileName} [id-$id]".rightFileName()
        )
        val manifestBytes = ObjectMapper().writeValueAsBytes(manifest)
        val manifestEntry = ZipEntry("manifest.json").apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(manifestEntry)
        zip.write(manifestBytes)
        zip.closeEntry()

        zip.close()

        val downloadName = "${settings.fileName} [id-$id].smkaraoke".rightFileName()
        // RFC 5987 encoding so browsers use the Cyrillic filename instead of the URL path ("playerfile")
        val encodedName = java.net.URLEncoder.encode(downloadName, "UTF-8").replace("+", "%20")
        response.contentType = "application/x-smkaraoke"
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"song-$id.smkaraoke\"; filename*=UTF-8''$encodedName")
        response.outputStream.write(bos.toByteArray())
    }

    private fun smkaraokeAddStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
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

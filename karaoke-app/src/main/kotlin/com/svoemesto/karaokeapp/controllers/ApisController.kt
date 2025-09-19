package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.SNS
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
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.*

import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.KeyStoreException
import java.io.IOException
import java.sql.Timestamp
import java.time.Instant

@Controller
@RequestMapping("/apis")
class ApisController(private val sseNotificationService: SseNotificationService) {

    @GetMapping("/diagnostics") // GET запрос на /apis/diagnostics
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
                        sslInfo["cert.$certAliasToCheck.type"] = cert?.type ?: "Unknown"
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
        info["file.encoding"] = System.getProperty("file.encoding")
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
        val settings = Settings.loadListFromDb(database = WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.drumsNameFlac)
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/filebass")
    fun getSongFileBass(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.bassNameFlac)
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/filevoice")
    fun getSongFileVocal(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.vocalsNameFlac)
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/fileminus")
    fun getSongFileMusic(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.newNoStemNameFlac)
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            return ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/song/{id}/filesong")
    fun getSongFileSong(
        @PathVariable id: Long
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val filename = File(settings?.fileAbsolutePath)
        val resource = FileSystemResource(filename)
        if (resource.exists()) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment") //; filename=\"${filename.name}\"")
                .body(resource)
        } else {
            return ResponseEntity.notFound().build()
        }
    }

    // Получение списка id песен, изменившихся с указанного момента
    @PostMapping("/songs/changed")
    @ResponseBody
    fun getChangedSongsIds(@RequestParam time: Long): List<Long> {
        val result: MutableList<Long> = mutableListOf()

        val connection = WORKING_DATABASE.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
            return emptyList()
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        var sql = "select id from tbl_settings where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > $time;"
        statement = connection.createStatement()
        try {
            rs = statement.executeQuery(sql)
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
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
            return emptyList()
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        var sql = "select id from tbl_processes where EXTRACT(EPOCH FROM last_update at time zone 'UTC-3')*1000 > $time;"
        statement = connection.createStatement()
        try {
            rs = statement.executeQuery(sql)
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

    // Получение исходного текста для голоса
    @PostMapping("/song/voicesourcetext")
    @ResponseBody
    fun getSongSourceText(@RequestParam id: Long, @RequestParam voiceId: Int): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            settings.getSourceText(voiceId)
        } ?: ""
        return text
    }

    // diffbeats + 1
    @PostMapping("/song/diffbeatsinc")
    @ResponseBody
    fun diffBeatsIncrement(@RequestParam id: Long): Long {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfo = settings?.let {
            settings.sheetstageInfo
        } ?: emptyMap()
        return sheetsageinfo
    }

    // Получение sheetsageinfo - tempo
    @PostMapping("/song/sheetsageinfobpm")
    @ResponseBody
    fun getSheetsageinfoBpm(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfotempo = settings?.let {
            settings.sheetstageInfo["tempo"] as String
        } ?: ""
        return sheetsageinfotempo
    }

    // Получение sheetsageinfo - key
    @PostMapping("/song/sheetsageinfokey")
    @ResponseBody
    fun getSheetsageinfoKey(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfokey = settings?.let {
            settings.sheetstageInfo["key"] as String
        } ?: ""
        return sheetsageinfokey
    }

    // Получение sheetsageinfo - chords
    @PostMapping("/song/sheetsageinfochords")
    @ResponseBody
    fun getSheetsageinfoChords(@RequestParam id: Long): List<String> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfochords = settings?.let {
            settings.sheetstageInfo["chords"] as List<String>
        } ?: emptyList()
        return sheetsageinfochords
    }

    // Получение sheetsageinfo - beattimes
    @PostMapping("/song/sheetsageinfobeattimes")
    @ResponseBody
    fun getSheetsageinfoBeattimes(@RequestParam id: Long): List<Double> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val sheetsageinfobeattimes = settings?.let {
            settings.sheetstageInfo["beattimes"] as List<Double>
        } ?: emptyList()
        return sheetsageinfobeattimes
    }


    // Получение слогов для голоса
    @PostMapping("/song/voicesourcesyllables")
    @ResponseBody
    fun getSongSourceSyllables(@RequestParam id: Long, @RequestParam voiceId: Int): List<String> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val syllables = settings?.let {
            settings.getSourceSyllables(voiceId)
        } ?: emptyList()
        return syllables
    }

    // Получение маркеров для голоса
    @PostMapping("/song/voicesourcemarkers")
    @ResponseBody
    fun getSongSourceMarkers(@RequestParam id: Long, @RequestParam voiceId: Int): List<SourceMarker> {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val markers = settings?.let {
            settings.getSourceMarkers(voiceId)
        } ?: emptyList()
        return markers
    }

    // Получение форматированного текста
    @PostMapping("/song/textformatted")
    @ResponseBody
    fun getSongTextFormatted(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            settings.getTextFormatted()
        } ?: ""
        return text
    }


    // Получение форматированного текста с нотами
    @PostMapping("/song/notesformatted")
    @ResponseBody
    fun getSongFormattedNotes(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            settings.getFormattedNotes()
        } ?: ""
        return text
    }

        // Получение форматированного текста с аккордами
    @PostMapping("/song/chordsformatted")
    @ResponseBody
    fun getSongFormattedChords(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            settings.getFormattedChords()
        } ?: ""
        return text
    }

    // Получение текста заголовка для boosty
    @PostMapping("/song/textboostyhead")
    @ResponseBody
    fun getSongTextBoostyHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val text = it.getTextBoostyBody()
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для sponsr
    @PostMapping("/song/textsponsrhead")
    @ResponseBody
    fun getSongTextSpobsrHead(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val text = it.getVKGroupDescription()
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Dzen Karaoke
    @PostMapping("/song/textdzenkaraokeheader")
    @ResponseBody
    fun getSongTextDzenKaraokeHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = it.getDescriptionVkHeader(SongVersion.KARAOKE)
            text
        } ?: ""
        return text
    }

    // Получение текста тела для Vk Karaoke
    @PostMapping("/song/textvkkaraoke")
    @ResponseBody
    fun getSongTextVkKaraoke(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.KARAOKE)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Lyrics
    @PostMapping("/song/texttelegramlyricsheader")
    @ResponseBody
    fun getSongTextTelegramLyricsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }

    // Получение текста заголовка для Telegram Chords
    @PostMapping("/song/texttelegramchordsheader")
    @ResponseBody
    fun getSongTextTelegramChordsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
            text
        } ?: ""
        return text
    }
    // Получение текста заголовка для Telegram Tabs
    @PostMapping("/song/texttelegramtabsheader")
    @ResponseBody
    fun getSongTextTelegramTabsHeader(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        val text = settings?.let {
            val text = it.getDescriptionVkHeader(SongVersion.TABS)
            text
        } ?: ""
        return text
    }

    // Получение indexTabsVariant
    @PostMapping("/song/indextabsvariant")
    @ResponseBody
    fun getSongIndexTabsVariant(@RequestParam id: Long): Int {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let { it.indexTabsVariant }?: 0
    }

    // Получение списка авторов
    @PostMapping("/songs/authors")
    @ResponseBody
    fun authors(): Map<String, Any> {
        return mapOf(
            "authors" to Settings.loadListAuthors(WORKING_DATABASE)
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
    @PostMapping("/processes/statuses")
    @ResponseBody
    fun processesStatuses(): Map<String, Any> {
        return mapOf(
            "statuses" to KaraokeProcessStatuses.values()
        )
    }

    // Получение списка типов процессов
    @PostMapping("/processes/types")
    @ResponseBody
    fun processesTypes(): Map<String, Any> {
        return mapOf(
            "authors" to KaraokeProcessStatuses.values()
        )
    }

    // Получение списка песен по списку id
    @PostMapping("/songs/ids")
    @ResponseBody
    fun apisSongsByIds(@RequestParam ids: List<Long>): List<SettingsDTO> {
        return Settings.loadListFromDb(mapOf(Pair("ids", ids.joinToString(","))), WORKING_DATABASE).map { it.toDTO() }
    }

    // Получение списка процессов по списку id
    @PostMapping("/processes/ids")
    @ResponseBody
    fun apisProcessesByIds(@RequestParam ids: List<Long>): List<ProcessesDTO> {
        return KaraokeProcess.loadList(mapOf(Pair("ids", ids.joinToString(","))), WORKING_DATABASE).map { it.toDTO() }
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
            "publications" to Publication.getPublicationList(args, WORKING_DATABASE).map { it.toDTO() }
        )
    }

    // список unpublications
    @PostMapping("/unpublications")
    @ResponseBody
    fun unpublications(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to Publication.getUnPublicationList(WORKING_DATABASE).map { it.map { it.toDTO() } }
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
            "publications" to CrossSettings.publications(Publication.getSettingsListForPublications(args, WORKING_DATABASE))
        )
    }

    // список unpublications
    @PostMapping("/unpublications2")
    @ResponseBody
    fun unpublications2(): Map<String, Any> {
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publications" to CrossSettings.unpublications(Publication.getSettingsListForUnpublications(WORKING_DATABASE))
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
        val listOfSettings = Publication.getSettingsListForPublications(args, WORKING_DATABASE)
        val publications = if (filterCond == "unpublish") {
            CrossSettings.unpublications(listOfSettings)
        } else {
            CrossSettings.publications(listOfSettings)
        }
        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "publicationsDigest" to publications
        )
    }

    @PostMapping("/processesdigests")
    @ResponseBody
    fun apisProcessesDigest(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_name: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) filter_order: String?,
        @RequestParam(required = false) filter_priority: String?,
        @RequestParam(required = false) filter_description: String?,
        @RequestParam(required = false) filter_settings_id: String?,
        @RequestParam(required = false) filter_type: String?,
        @RequestParam(required = false) filter_limit: String?,
        @RequestParam(required = false) filter_notail: String?

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_name?.let { if (filter_name != "") args["process_name"] = filter_name }
        filter_status?.let { if (filter_status != "") args["process_status"] = filter_status }
        filter_order?.let { if (filter_order != "") args["process_order"] = filter_order }
        filter_priority?.let { if (filter_priority != "") args["process_priority"] = filter_priority }
        filter_description?.let { if (filter_description != "") args["process_description"] = filter_description }
        filter_settings_id?.let { if (filter_settings_id != "") args["settings_id"] = filter_settings_id }
        filter_type?.let { if (filter_type != "") args["process_type"] = filter_type }
        filter_limit?.let { if (filter_limit != "") args["filter_limit"] = filter_limit }
        filter_notail?.let { if (filter_notail != "") args["filter_notail"] = filter_notail }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "processesDigests" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() },
            "statuses" to KaraokeProcessStatuses.values(),
            "types" to KaraokeProcessTypes.values()
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
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_songName: String?,
        @RequestParam(required = false) filter_author: String?,
        @RequestParam(required = false) filter_year: String?,
        @RequestParam(required = false) filter_album: String?,
        @RequestParam(required = false) filter_track: String?,
        @RequestParam(required = false) filter_tags: String?,
        @RequestParam(required = false) filter_date: String?,
        @RequestParam(required = false) filter_time: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) flag_boosty: String?,
        @RequestParam(required = false) flag_sponsr: String?,
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_dzen_lyrics: String?,
        @RequestParam(required = false) flag_dzen_karaoke: String?,
        @RequestParam(required = false) flag_dzen_chords: String?,
        @RequestParam(required = false) flag_dzen_melody: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_vk_melody: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        @RequestParam(required = false) flag_telegram_melody: String?,
        @RequestParam(required = false) flag_pl_lyrics: String?,
        @RequestParam(required = false) flag_pl_karaoke: String?,
        @RequestParam(required = false) flag_pl_chords: String?,
        @RequestParam(required = false) flag_pl_melody: String?,
        @RequestParam(required = false) filter_result_version: String?,
        @RequestParam(required = false) filter_count_voices: String?,
        @RequestParam(required = false) filter_version_boosty: String?,
        @RequestParam(required = false) filter_version_boosty_files: String?,
        @RequestParam(required = false) filter_version_sponsr: String?,
        @RequestParam(required = false) filter_version_dzen_karaoke: String?,
        @RequestParam(required = false) filter_version_vk_karaoke: String?,
        @RequestParam(required = false) filter_version_telegram_karaoke: String?,
        @RequestParam(required = false) filter_version_pl_karaoke: String?,
        @RequestParam(required = false) filter_rate: String?,
        @RequestParam(required = false) filter_status_process_lyrics: String?,
        @RequestParam(required = false) filter_status_process_karaoke: String?

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_songName?.let { if (filter_songName != "") args["song_name"] = filter_songName }
        filter_author?.let { if (filter_author != "") args["song_author"] = filter_author }
        filter_album?.let { if (filter_album != "") args["song_album"] = filter_album }
        filter_date?.let { if (filter_date != "") args["publish_date"] = filter_date }
        filter_time?.let { if (filter_time != "") args["publish_time"] = filter_time }
        filter_year?.let { if (filter_year != "") args["song_year"] = filter_year }
        filter_track?.let { if (filter_track != "") args["song_track"] = filter_track }
        filter_tags?.let { if (filter_tags != "") args["tags"] = filter_tags }
        filter_status?.let { if (filter_status != "") args["id_status"] = filter_status }
        flag_boosty?.let { if (flag_boosty != "") args["flag_boosty"] = flag_boosty }
        flag_sponsr?.let { if (flag_sponsr != "") args["flag_sponsr"] = flag_sponsr }
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_dzen_lyrics?.let { if (flag_dzen_lyrics != "") args["flag_dzen_lyrics"] = flag_dzen_lyrics }
        flag_dzen_karaoke?.let { if (flag_dzen_karaoke != "") args["flag_dzen_karaoke"] = flag_dzen_karaoke }
        flag_dzen_chords?.let { if (flag_dzen_chords != "") args["flag_dzen_chords"] = flag_dzen_chords }
        flag_dzen_melody?.let { if (flag_dzen_melody != "") args["flag_dzen_melody"] = flag_dzen_melody }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_vk_melody?.let { if (flag_vk_melody != "") args["flag_vk_melody"] = flag_vk_melody }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        flag_telegram_melody?.let { if (flag_telegram_melody != "") args["flag_telegram_melody"] = flag_telegram_melody }
        flag_pl_lyrics?.let { if (flag_pl_lyrics != "") args["flag_pl_lyrics"] = flag_pl_lyrics }
        flag_pl_karaoke?.let { if (flag_pl_karaoke != "") args["flag_pl_karaoke"] = flag_pl_karaoke }
        flag_pl_chords?.let { if (flag_pl_chords != "") args["flag_pl_chords"] = flag_pl_chords }
        flag_pl_melody?.let { if (flag_pl_melody != "") args["flag_pl_melody"] = flag_pl_melody }
        filter_result_version?.let { if (filter_result_version != "") args["filter_result_version"] = filter_result_version }
        filter_count_voices?.let { if (filter_count_voices != "") args["filter_count_voices"] = filter_count_voices }
        filter_version_boosty?.let { if (filter_version_boosty != "") args["filter_version_boosty"] = filter_version_boosty }
        filter_version_boosty_files?.let { if (filter_version_boosty_files != "") args["filter_version_boosty_files"] = filter_version_boosty_files }
        filter_version_sponsr?.let { if (filter_version_sponsr != "") args["filter_version_sponsr"] = filter_version_sponsr }
        filter_version_dzen_karaoke?.let { if (filter_version_dzen_karaoke != "") args["filter_version_dzen_karaoke"] = filter_version_dzen_karaoke }
        filter_version_vk_karaoke?.let { if (filter_version_vk_karaoke != "") args["filter_version_vk_karaoke"] = filter_version_vk_karaoke }
        filter_version_telegram_karaoke?.let { if (filter_version_telegram_karaoke != "") args["filter_version_telegram_karaoke"] = filter_version_telegram_karaoke }
        filter_version_pl_karaoke?.let { if (filter_version_pl_karaoke != "") args["filter_version_pl_karaoke"] = filter_version_pl_karaoke }
        filter_rate?.let { if (filter_rate != "") args["filter_rate"] = filter_rate }
        filter_status_process_lyrics?.let { if (filter_status_process_lyrics != "") args["filter_status_process_lyrics"] = filter_status_process_lyrics }
        filter_status_process_karaoke?.let { if (filter_status_process_karaoke != "") args["filter_status_process_karaoke"] = filter_status_process_karaoke }

        SongsHistory().add(args)

        val lst = Settings.loadListFromDb(args, WORKING_DATABASE).map { it.toDTO().toDtoDigest() }
        var totalMs = 0L
        for (i in lst.indices) {
            if (i > 0) lst[i].idPrevious = lst[i-1].id
            if (i < lst.size-1) lst[i].idNext = lst[i+1].id
            totalMs += lst[i].ms
        }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "songsDigests" to lst,
            "authors" to Settings.loadListAuthors(WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE),
            "totalDuration" to convertMillisecondsToDtoTimecode(totalMs)
        )
    }

    // Получение списка песен
    @PostMapping("/songs")
    @ResponseBody
    fun apisSongs(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_songName: String?,
        @RequestParam(required = false) filter_author: String?,
        @RequestParam(required = false) filter_year: String?,
        @RequestParam(required = false) filter_album: String?,
        @RequestParam(required = false) filter_track: String?,
        @RequestParam(required = false) filter_tags: String?,
        @RequestParam(required = false) filter_date: String?,
        @RequestParam(required = false) filter_time: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) flag_boosty: String?,
        @RequestParam(required = false) flag_sponsr: String?,
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_dzen_lyrics: String?,
        @RequestParam(required = false) flag_dzen_karaoke: String?,
        @RequestParam(required = false) flag_dzen_chords: String?,
        @RequestParam(required = false) flag_dzen_melody: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_vk_melody: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        @RequestParam(required = false) flag_telegram_melody: String?,
        @RequestParam(required = false) flag_pl_lyrics: String?,
        @RequestParam(required = false) flag_pl_karaoke: String?,
        @RequestParam(required = false) flag_pl_chords: String?,
        @RequestParam(required = false) flag_pl_melody: String?,
        @RequestParam(required = false) filter_result_version: String?,
        @RequestParam(required = false) filter_version_boosty: String?,
        @RequestParam(required = false) filter_version_boosty_files: String?,
        @RequestParam(required = false) filter_version_sponsr: String?,
        @RequestParam(required = false) filter_version_dzen_karaoke: String?,
        @RequestParam(required = false) filter_version_vk_karaoke: String?,
        @RequestParam(required = false) filter_version_telegram_karaoke: String?,
        @RequestParam(required = false) filter_version_pl_karaoke: String?,
        @RequestParam(required = false) filter_rate: String?,
        @RequestParam(required = false) filter_status_process_lyrics: String?,
        @RequestParam(required = false) filter_status_process_karaoke: String?,
        @RequestParam(required = false) pageSize: Int = 30
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_songName?.let { if (filter_songName != "") args["song_name"] = filter_songName }
        filter_author?.let { if (filter_author != "") args["song_author"] = filter_author }
        filter_album?.let { if (filter_album != "") args["song_album"] = filter_album }
        filter_date?.let { if (filter_date != "") args["publish_date"] = filter_date }
        filter_time?.let { if (filter_time != "") args["publish_time"] = filter_time }
        filter_year?.let { if (filter_year != "") args["song_year"] = filter_year }
        filter_track?.let { if (filter_track != "") args["song_track"] = filter_track }
        filter_tags?.let { if (filter_tags != "") args["tags"] = filter_tags }
        filter_status?.let { if (filter_status != "") args["id_status"] = filter_status }
        flag_boosty?.let { if (flag_boosty != "") args["flag_boosty"] = flag_boosty }
        flag_sponsr?.let { if (flag_sponsr != "") args["flag_sponsr"] = flag_sponsr }
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_dzen_lyrics?.let { if (flag_dzen_lyrics != "") args["flag_dzen_lyrics"] = flag_dzen_lyrics }
        flag_dzen_karaoke?.let { if (flag_dzen_karaoke != "") args["flag_dzen_karaoke"] = flag_dzen_karaoke }
        flag_dzen_chords?.let { if (flag_dzen_chords != "") args["flag_dzen_chords"] = flag_dzen_chords }
        flag_dzen_melody?.let { if (flag_dzen_melody != "") args["flag_dzen_melody"] = flag_dzen_melody }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_vk_melody?.let { if (flag_vk_melody != "") args["flag_vk_melody"] = flag_vk_melody }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        flag_telegram_melody?.let { if (flag_telegram_melody != "") args["flag_telegram_melody"] = flag_telegram_melody }
        flag_pl_lyrics?.let { if (flag_pl_lyrics != "") args["flag_pl_lyrics"] = flag_pl_lyrics }
        flag_pl_karaoke?.let { if (flag_pl_karaoke != "") args["flag_pl_karaoke"] = flag_pl_karaoke }
        flag_pl_chords?.let { if (flag_pl_chords != "") args["flag_pl_chords"] = flag_pl_chords }
        flag_pl_melody?.let { if (flag_pl_melody != "") args["flag_pl_melody"] = flag_pl_melody }
        filter_result_version?.let { if (filter_result_version != "") args["filter_result_version"] = filter_result_version }
        filter_version_boosty?.let { if (filter_version_boosty != "") args["filter_version_boosty"] = filter_version_boosty }
        filter_version_boosty_files?.let { if (filter_version_boosty_files != "") args["filter_version_boosty_files"] = filter_version_boosty_files }
        filter_version_sponsr?.let { if (filter_version_sponsr != "") args["filter_version_sponsr"] = filter_version_sponsr }
        filter_version_dzen_karaoke?.let { if (filter_version_dzen_karaoke != "") args["filter_version_dzen_karaoke"] = filter_version_dzen_karaoke }
        filter_version_vk_karaoke?.let { if (filter_version_vk_karaoke != "") args["filter_version_vk_karaoke"] = filter_version_vk_karaoke }
        filter_version_telegram_karaoke?.let { if (filter_version_telegram_karaoke != "") args["filter_version_telegram_karaoke"] = filter_version_telegram_karaoke }
        filter_version_pl_karaoke?.let { if (filter_version_pl_karaoke != "") args["filter_version_pl_karaoke"] = filter_version_pl_karaoke }
        filter_rate?.let { if (filter_rate != "") args["filter_rate"] = filter_rate }
        filter_status_process_lyrics?.let { if (filter_status_process_lyrics != "") args["filter_status_process_lyrics"] = filter_status_process_lyrics }
        filter_status_process_karaoke?.let { if (filter_status_process_karaoke != "") args["filter_status_process_karaoke"] = filter_status_process_karaoke }

        SongsHistory().add(args)

        val lst = Settings.loadListFromDb(args, WORKING_DATABASE).map { it.toDTO() }
        for (i in lst.indices) {
            if (i > 0) lst[i].idPrevious = lst[i-1].id
            if (i < lst.size-1) lst[i].idNext = lst[i+1].id
        }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to lst.chunked(pageSize),
            "authors" to Settings.loadListAuthors(WORKING_DATABASE),
            "albums" to Settings.loadListAlbums(WORKING_DATABASE)
        )
    }

    // Получение списка процессов
    @PostMapping("/processes")
    @ResponseBody
    fun apisProcesses(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_name: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) filter_order: String?,
        @RequestParam(required = false) filter_priority: String?,
        @RequestParam(required = false) filter_description: String?,
        @RequestParam(required = false) filter_settings_id: String?,
        @RequestParam(required = false) filter_type: String?,
        @RequestParam(required = false) filter_limit: String?,
        @RequestParam(required = false) pageSize: Int = 30

    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_name?.let { if (filter_name != "") args["process_name"] = filter_name }
        filter_status?.let { if (filter_status != "") args["process_status"] = filter_status }
        filter_order?.let { if (filter_order != "") args["process_order"] = filter_order }
        filter_priority?.let { if (filter_priority != "") args["process_priority"] = filter_priority }
        filter_description?.let { if (filter_description != "") args["process_description"] = filter_description }
        filter_settings_id?.let { if (filter_settings_id != "") args["settings_id"] = filter_settings_id }
        filter_type?.let { if (filter_type != "") args["process_type"] = filter_type }
        filter_limit?.let { if (filter_limit != "") args["filter_limit"] = filter_limit }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "pages" to KaraokeProcess.loadList(args, WORKING_DATABASE).map { it.toDTO() }.chunked(pageSize),
            "statuses" to KaraokeProcessStatuses.values(),
            "types" to KaraokeProcessTypes.values()
        )
    }

    // Видеопроигрыватель: Lyrics
    @PostMapping("/song/playlyrics")
    @ResponseBody
    fun doPlayLyrics(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            settings.playLyrics()
        }
        return true
    }

    // Видеопроигрыватель: Karaoke
    @PostMapping("/song/playkaraoke")
    @ResponseBody
    fun doPlayKaraoke(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            settings.playKaraoke()
        }
        return true
    }


    // Видеопроигрыватель: Chords
    @PostMapping("/song/playchords")
    @ResponseBody
    fun doPlayChords(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            settings.playChords()
        }
        return true
    }
    // Видеопроигрыватель: Tabs
    @PostMapping("/song/playtabs")
    @ResponseBody
    fun doPlayTabs(@RequestParam id: Long): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val currSett = Settings.loadFromDbById(id.toLong(), WORKING_DATABASE)?.toDTO()
        if (currSett != null) {
            val lst = Settings.loadListFromDb(args = mapOf("song_author" to currSett.author), WORKING_DATABASE)
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
                val leftSett = Settings.loadListFromDb(args = mapOf("publish_date" to currSett.date, "publish_time" to leftTime), WORKING_DATABASE)
                val rightSett = Settings.loadListFromDb(args = mapOf("publish_date" to currSett.date, "publish_time" to rightTime), WORKING_DATABASE)
                if (leftSett.isNotEmpty()) currSett.idLeft = leftSett[0].id
                if (rightSett.isNotEmpty()) currSett.idRight = rightSett[0].id
            }


            return currSett
        }

        return Settings.loadFromDbById(id.toLong(), WORKING_DATABASE)?.toDTO()
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
        @RequestParam(required = false) resultVersion: String?,
        @RequestParam(required = false) diffBeats: String?,
        @RequestParam(required = false) rate: String?
    ): Boolean {
        val settingsId: Long = id.toLong()
        val settings = Settings.loadFromDbById(settingsId, WORKING_DATABASE)
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
            resultVersion?.let { sett.fields[SettingField.RESULT_VERSION] = it }
            diffBeats?.let { sett.fields[SettingField.DIFFBEATS] = it }
            idStatus?.let { sett.fields[SettingField.ID_STATUS] =  it }
            rate?.let { sett.fields[SettingField.RATE] =  it }
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)

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

    // Получаем картинку в BASE64 для альбома
    @PostMapping("/song/picturealbum")
    @ResponseBody
    fun getPictureAlbum(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            return "data:image/gif;base64,${it.pictureAlbum?.full}" ?: ""
        }
        return ""
    }

    // Получаем картинку в BASE64 для автора
    @PostMapping("/song/pictureauthor")
    @ResponseBody
    fun getPictureAuthor(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            return "data:image/gif;base64,${it.pictureAuthor?.full}" ?: ""
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

        val settings = Settings.loadListFromDb(emptyMap(), WORKING_DATABASE)
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
            val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return settings?.let {
            val markers = try {
                Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers)
            } catch (e: Exception) {
                println("Ошибка при парсинге маркеров.")
                emptyList()
            }
            settings.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
            val strText = settings.convertMarkersToSrt(voice)
            try {
                val pathToFile = "${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt"
                File(pathToFile).writeText(strText)
                runCommand(listOf("chmod", "666", pathToFile))
            } catch (e: Exception) {
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
                             @RequestParam(required = false) priorLyricsVk: String? = "",
                             @RequestParam(required = false) priorKaraokeVk: String? = "",
                             @RequestParam(required = false) priorChordsVk: String? = "",
                             @RequestParam(required = false) priorMelodyVk: String? = "",
    ): Boolean {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)


        var type = "danger"
        val head = "Создание караоке"
        var body = "Что-то пошло не так"
        var result = false
        settings?.let {

            val createLyrics = priorLyrics != "" && priorLyrics != null
            val createKaraoke = priorKaraoke != "" && priorKaraoke != null
            val createChords = priorChords != "" && priorChords != null
            val createMelody = priorMelody != "" && priorMelody != null
            val createLyricsVk = priorLyricsVk != "" && priorLyricsVk != null
            val createKaraokeVk = priorKaraokeVk != "" && priorKaraokeVk != null
            val createChordsVk = priorChordsVk != "" && priorChordsVk != null
            val createMelodyVk = priorMelodyVk != "" && priorMelodyVk != null

            settings.createKaraoke(
                createLyrics = createLyrics,
                createKaraoke = createKaraoke,
                createChords = createChords,
                createMelody = createMelody,
                createLyricsVk = createLyricsVk,
                createKaraokeVk = createKaraokeVk,
                createChordsVk = createChordsVk,
                createMelodyVk = createMelodyVk,
            )
            if (createLyrics) {
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, priorLyrics!!.toInt())
                if (!createLyricsVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICSVK, true, priorLyrics.toInt())
            }
            if (createKaraoke) {
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, priorKaraoke!!.toInt())
                if (!createKaraokeVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKEVK, true, priorKaraoke.toInt())
            }
            if (createChords) {
//                if (!File(settings.drumsNameFlac).exists() || !File(settings.bassNameFlac).exists()) {
//                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, priorChords!!.toInt())
//                }
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, priorChords!!.toInt())
                if (!createChordsVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDSVK, true, priorChords.toInt())
            }
            if (createMelody) {
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABS, true, priorMelody!!.toInt())
                if (!createMelodyVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABSVK, true, priorMelody.toInt())
            }
            if (createLyricsVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICSVK, true, priorLyricsVk!!.toInt())
            if (createKaraokeVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKEVK, true, priorKaraokeVk!!.toInt())
            if (createChordsVk) {
//                if (!File(settings.drumsNameFlac).exists() || !File(settings.bassNameFlac).exists()) {
//                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, priorChords!!.toInt())
//                }
                KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDSVK, true, priorChordsVk!!.toInt())
            }
            if (createMelodyVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABSVK, true, priorMelodyVk!!.toInt())
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
                                 @RequestParam(required = false) priorLyricsVk: String? = "",
                                 @RequestParam(required = false) priorKaraokeVk: String? = "",
                                 @RequestParam(required = false) priorChordsVk: String? = "",
                                 @RequestParam(required = false) priorMelodyVk: String? = "",
    ) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    val createLyrics = priorLyrics != "" && priorLyrics != null
                    val createKaraoke = priorKaraoke != "" && priorKaraoke != null
                    val createChords = priorChords != "" && priorChords != null
                    val createMelody = priorMelody != "" && priorMelody != null
                    val createLyricsVk = priorLyricsVk != "" && priorLyricsVk != null
                    val createKaraokeVk = priorKaraokeVk != "" && priorKaraokeVk != null
                    val createChordsVk = priorChordsVk != "" && priorChordsVk != null
                    val createMelodyVk = priorMelodyVk != "" && priorMelodyVk != null

                    settings.createKaraoke(
                        createLyrics = createLyrics,
                        createKaraoke = createKaraoke,
                        createChords = createChords,
                        createMelody = createMelody,
                        createLyricsVk = createLyricsVk,
                        createKaraokeVk = createKaraokeVk,
                        createMelodyVk = createMelodyVk
                    )

                    if (createLyrics) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, priorLyrics!!.toInt())
                        if (!createLyricsVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICSVK, true, priorLyrics.toInt())
                    }
                    if (createKaraoke) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, priorKaraoke!!.toInt())
                        if (!createKaraokeVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKEVK, true, priorKaraoke.toInt())
                    }
                    if (createChords) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, priorChords!!.toInt())
                        if (!createChordsVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDSVK, true, priorChords.toInt())
                    }
                    if (createMelody) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABS, true, priorMelody!!.toInt())
                        if (!createMelodyVk && settings.getSongDurationVideoMs() < 61_100) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABSVK, true, priorMelody.toInt())
                    }
                    if (createLyricsVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICSVK, true, priorLyricsVk!!.toInt())
                    if (createKaraokeVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKEVK, true, priorKaraokeVk!!.toInt())
                    if (createChordsVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDSVK, true, priorChordsVk!!.toInt())
                    if (createMelodyVk) KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_TABSVK, true, priorMelodyVk!!.toInt())
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
    fun doProcessDemucs2(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, prior)
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
    fun getSongsCreateDemucs2All(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
//                    if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, prior)
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
    fun doProcessDemucs5(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, prior)
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
    fun getSongsCreateDemucs5All(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
//                    if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(settings, KaraokeProcessTypes.RECODE_48000, true, prior)
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS5, true, prior)
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
    fun doProcessSheetsage(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            if (File(it.pathToFileSheetsageMIDI).exists()) return
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE, true, prior)
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
    fun getSongsCreateSheetsageAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    if (!File(it.pathToFileSheetsageMIDI).exists()) {
                        KaraokeProcess.createProcess(settings, KaraokeProcessTypes.SHEETSAGE, true, prior)
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
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            it.deleteFromDb()
        }
    }

    // Создаём MP3 KARAOKE для песни
    @PostMapping("/song/mp3karaoke")
    @ResponseBody
    fun doMP3Karaoke(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.doMP3Karaoke(prior)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание MP3 KARAOKE",
            body = "Создание MP3 KARAOKE прошло успешно"
        )))
    }

    // Создаём MP3 KARAOKE для всех
    @PostMapping("/songs/createmp3karaokeall")
    @ResponseBody
    fun getSongsCreateMP3KaraokeAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doMP3Karaoke(prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание MP3 KARAOKE",
                body = "Создание MP3 KARAOKE прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание MP3 KARAOKE",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Создаём MP3 LYRICS для песни
    @PostMapping("/song/mp3lyrics")
    @ResponseBody
    fun doMP3Lyrics(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.doMP3Lyrics(prior)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Создание MP3 LYRICS",
            body = "Создание MP3 LYRICS прошло успешно"
        )))
    }

    // Создаём MP3 LYRICS для всех
    @PostMapping("/songs/createmp3lyricsall")
    @ResponseBody
    fun getSongsCreateMP3LyricsAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doMP3Lyrics(prior)
                }
                result = true
            }
        }
        if (result) {
            SNS.send(SseNotification.message(Message(
                type = "info",
                head = "Создание MP3 LYRICS",
                body = "Создание MP3 LYRICS прошло успешно"
            )))
        } else {
            SNS.send(SseNotification.message(Message(
                type = "warning",
                head = "Создание MP3 LYRICS",
                body = "Что-то пошло не так"
            )))
        }
    }

    // Создаём SYMLINKs для песни
    @PostMapping("/song/symlink")
    @ResponseBody
    fun doSymlink(@RequestParam id: Long, @RequestParam(required = false) prior: Int = -1) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.doSymlink(prior)
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
        Settings.loadFromDbById(id, WORKING_DATABASE)?.let { settings ->
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
    fun getSongsCreateSymlinksAll(@RequestParam songsIds: String, @RequestParam(required = false) prior: Int = -1) {
        var result = false
        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doSymlink(prior)
                }
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

    // Создаём SYMLINKs для всех
    @PostMapping("/songs/smartcopyall")
    @ResponseBody
    fun getSmartCopyAll(
        @RequestParam songsIds: String,
        @RequestParam(required = false) prior: Int = -1,
        @RequestParam smartCopySongVersion: String,
        @RequestParam smartCopySongResolution: String,
        @RequestParam(required = false) smartCopyCreateSubfoldersAuthors: Boolean?,
        @RequestParam(required = false) smartCopyRenameTemplate: String?,
        @RequestParam smartCopyPath: String
    ) {
        var result = false
        val scVersion = if (SongVersion.values().map {it.name}.contains(smartCopySongVersion)) SongVersion.valueOf(smartCopySongVersion) else SongVersion.KARAOKE

        songsIds.let {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    it.doSmartCopy(
                        prior = prior,
                        scVersion = scVersion,
                        scResolution = smartCopySongResolution,
                        scCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors ?: false,
                        scRenameTemplate = smartCopyRenameTemplate ?: "",
                        scPath = smartCopyPath,
                        )
                }
                result = true
            }
        }
        if (result) {
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

    // Ищем и возвращаем текст
    @PostMapping("/song/searchsongtext")
    @ResponseBody
    fun getSearchSongText(@RequestParam id: Long): String {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        return  settings?.let {
            searchSongText(settings)
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
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let {
                    if (settings.sourceText.isBlank()) {
                        val text = searchSongText(settings)

                        Thread.sleep(2000)

                        if (text.isNotBlank()) {
                            settings.sourceText = text
                            settings.fields[SettingField.ID_STATUS] = "1"
                            settings.saveToDb()
                        }
                    }
                }
                result = true
            }
        }
        return result
    }

    @PostMapping("/song/setpublishdatetimetoauthor")
    @ResponseBody
    fun doSetPublishDateTimeToAuthor(@RequestParam id: Long) {
        val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
        settings?.let {
            Settings.setPublishDateTimeToAuthor(settings)
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

    // Обновляем одну картинку в RemoteDatabase
    @PostMapping("/utils/updateremotepicturefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemotePictureFromLocalDatabase(
        @RequestParam(required = true) id: Long
    ): List<Int> {
        val (countCreate, countUpdate, countDelete) = updateRemotePictureFromLocalDatabase(id)
        val body = if (countCreate + countUpdate + countDelete == 0) "Изменения не требуются" else listOf(Pair(countCreate > 0, "создано записей: $countCreate"), Pair(countUpdate > 0, "обновлено записей: $countUpdate"), Pair(countDelete > 0, "удалено записей: $countDelete")).filter { it.first }.map{ it.second }.joinToString(", ").uppercaseFirstLetter()
        SNS.send(SseNotification.message(
            Message(
                type = "info",
                head = "Обновление БД",
                body = body
            )
        ))
        println("Обновление записей серверной БД - ${body}")
        return listOf(countCreate, countUpdate, countDelete)
    }

    // Обновляем одну песню в RemoteDatabase
    @PostMapping("/utils/updateremotesettingsfromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteSettingFromLocalDatabase(
        @RequestParam(required = true) id: Long
    ): List<Int> {
        val (countCreate, countUpdate, countDelete) = updateRemoteSettingFromLocalDatabase(id)
        val body = if (countCreate + countUpdate + countDelete == 0) "Изменения не требуются" else listOf(Pair(countCreate > 0, "создано записей: $countCreate"), Pair(countUpdate > 0, "обновлено записей: $countUpdate"), Pair(countDelete > 0, "удалено записей: $countDelete")).filter { it.first }.map{ it.second }.joinToString(", ").uppercaseFirstLetter()
        SNS.send(SseNotification.message(
            Message(
                type = "info",
                head = "Обновление БД",
                body = body
            )
        ))
        println("Обновление записей серверной БД - ${body}")
        return listOf(countCreate, countUpdate, countDelete)
    }

    // Добавляем одну песню в SYNC-таблицу
    @PostMapping("/utils/tosync")
    @ResponseBody
    fun doSetSettingsToSyncRemoteTable(
        @RequestParam(required = true) id: Long
    ) {
        setSettingsToSyncRemoteTable(id)
        val body = "Запись добавлена в SYNC-таблицу"
        SNS.send(SseNotification.message(
            Message(
                type = "info",
                head = "SYNC",
                body = body
            )
        ))
        println("Запись добавлена в SYNC-таблицу")
    }

    // Обновляем RemoteDatabase
    @PostMapping("/utils/updateremotedatabasefromlocaldatabase")
    @ResponseBody
    fun doUpdateRemoteDatabaseFromLocalDatabase(
        @RequestParam(required = true) updateSettings: Boolean = true,
        @RequestParam(required = true) updatePictures: Boolean = true
    ): List<Int> {
        val (countCreate, countUpdate, countDelete) = updateRemoteDatabaseFromLocalDatabase(updateSettings,updatePictures)
        val body = if (countCreate + countUpdate + countDelete == 0) "Изменения не требуются" else listOf(Pair(countCreate > 0, "создано записей: $countCreate"), Pair(countUpdate > 0, "обновлено записей: $countUpdate"), Pair(countDelete > 0, "удалено записей: $countDelete")).filter { it.first }.map{ it.second }.joinToString(", ").uppercaseFirstLetter()
        SNS.send(SseNotification.message(
            Message(
            type = "info",
            head = "Обновление БД",
            body = body
        )
        ))
        println("Обновление записей серверной БД - ${body}")
        return listOf(countCreate, countUpdate, countDelete)
    }

    // Обновляем LocalDatabase
    @PostMapping("/utils/updatelocaldatabasefromremotedatabase")
    @ResponseBody
    fun doUpdateLocalDatabaseFromRemoteDatabase(
        @RequestParam(required = true) updateSettings: Boolean = true,
        @RequestParam(required = true) updatePictures: Boolean = true
    ): List<Int> {
        val (countCreate, countUpdate, countDelete) = updateLocalDatabaseFromRemoteDatabase(updateSettings,updatePictures)
        val body = if (countCreate + countUpdate + countDelete == 0) "Изменения не требуются" else listOf(Pair(countCreate > 0, "создано записей: $countCreate"), Pair(countUpdate > 0, "обновлено записей: $countUpdate"), Pair(countDelete > 0, "удалено записей: $countDelete")).filter { it.first }.map{ it.second }.joinToString(", ").uppercaseFirstLetter()
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Обновление БД",
            body = body
        )))
        println("Обновление записей локальной БД - ${body}")
        return listOf(countCreate, countUpdate, countDelete)
    }

    // Добавление файлов из папки
    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String) {
        val result =  Settings.createFromPath(folder, WORKING_DATABASE).size
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
                       @RequestParam(required = false) priorKaraoke: Int = 10): Any {
        val settingsList = if (songsIds == "") {
            Settings.loadListFromDb(database = WORKING_DATABASE)
        } else {
            val ids = songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }
            val result: MutableList<Settings> = mutableListOf()
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id, WORKING_DATABASE)
                settings?.let { result.add(it) }
            }
            result.toList()
        }

        val (countCopy, countCode) = collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(
            settingsList = settingsList, priorLyrics = priorLyrics, priorKaraoke = priorKaraoke)
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
        val result =  updateBpmAndKey(WORKING_DATABASE)
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
        val (resultSuccess, resultFailed) =  updateBpmAndKeyLV(WORKING_DATABASE)
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
        val result = markDublicates(author, WORKING_DATABASE)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Нахождение дубликатов",
            body = "Найдено и отмечено дубликатов: $result"
        )))
    }

    // Удалить дубликаты
    @PostMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates() {
        val result = delDublicates(WORKING_DATABASE)
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
        val result = clearPreDublicates(WORKING_DATABASE)
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
        val result = customFunction()
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Custom Function",
            body = "CustomFunction выполнена с результатом: «$result»"
        )))
    }

    // Актуализация VKLinkPictureWeb
    @PostMapping("/utils/actualizevklinkpictureweb")
    @ResponseBody
    fun doActualizeVKLinkPictureWeb() {
        var cntSkip = 0
        var cntDelete = 0
        var cntCreate = 0

        Settings.loadListFromDb(database = WORKING_DATABASE).forEach { settings ->
            when (createVKLinkPictureWeb(settings, false)) {
                "delete" -> cntDelete++
                "skip" -> cntSkip++
                else -> cntCreate++
            }
        }

        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "Актуализация VKLinkPictureWeb",
            body = "Актуализация VKLinkPictureWeb выполнена с результатом: создано картинок - $cntCreate, удалено картинок - $cntDelete, пропущено картинок - $cntSkip"
        )))
    }

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
        KaraokeProcessWorker.deleteDone(WORKING_DATABASE)
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
            KaraokeProcessWorker.start(WORKING_DATABASE)
        }
    }

    @GetMapping("/subscribe")
    fun subscribeSse(
        response: HttpServletResponse
    ): SseEmitter {

        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
        response.setHeader("X-Accel-Buffering", "no")

//        val tabId: String = "tabId"
//        val userId: Long = 1L

        return sseNotificationService.subscribe()
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
    fun setProperty(@RequestParam key: String, @RequestParam stringValue: String) {
        KaraokeProperties.setFromString(key, stringValue)
        SNS.send(SseNotification.message(Message(
            type = "info",
            head = "SET PROPERTY",
            body = "Свойство «$key» установлено в значение «$stringValue»"
        )))
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
        @RequestParam(required = false) filter_key: String?,
        @RequestParam(required = false) filter_value: String?,
        @RequestParam(required = false) filter_default_value: String?,
        @RequestParam(required = false) filter_description: String?,
        @RequestParam(required = false) filter_type: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_key?.let { if (filter_key != "") args["key"] = filter_key }
        filter_value?.let { if (filter_value != "") args["value"] = filter_value }
        filter_default_value?.let { if (filter_default_value != "") args["default_value"] = filter_default_value }
        filter_description?.let { if (filter_description != "") args["description"] = filter_description }
        filter_type?.let { if (filter_type != "") args["type"] = filter_type }

        return mapOf(
            "workInContainer" to APP_WORK_IN_CONTAINER,
            "propertiesDigests" to KaraokeProperties.loadList(args),
            "types" to KaraokeProperties.types()
        )
    }

    @PostMapping("/authorsdigests")
    @ResponseBody
    fun apisAuthorsDigest(
            @RequestParam(required = false) filter_id: String?,
            @RequestParam(required = false) filter_author: String?,
            @RequestParam(required = false) filter_ym_id: String?,
            @RequestParam(required = false) filter_last_album_ym: String?,
            @RequestParam(required = false) filter_last_album_processed: String?,
            @RequestParam(required = false) filter_watched: String?
    ): Map<String, Any> {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_author?.let { if (filter_author != "") args["author"] = filter_author }
        filter_ym_id?.let { if (filter_ym_id != "") args["ym_id"] = filter_ym_id }
        filter_last_album_ym?.let { if (filter_last_album_ym != "") args["last_album_ym"] = filter_last_album_ym }
        filter_last_album_processed?.let { if (filter_last_album_processed != "") args["last_album_processed"] = filter_last_album_processed }
        filter_watched?.let { if (filter_watched != "") args["watched"] = filter_watched }
        val authorsList = Author.loadList(args, WORKING_DATABASE)
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
    fun getFiles(@RequestParam path: String): List<FileDTO> {
        var directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            directory = File("/")
            if (!directory.exists() || !directory.isDirectory) {
                throw IllegalArgumentException("Invalid directory path")
            }
        }

        return directory.listFiles()?.map { file ->
            FileDTO(
                name = file.name,
                path = file.absolutePath,
                extension = file.extension,
                nameWithoutExtension = file.nameWithoutExtension,
                parent = file.parent,
                length = file.length(),
                isDirectory = file.isDirectory
            )
        }?.sorted() ?: emptyList()
    }
}
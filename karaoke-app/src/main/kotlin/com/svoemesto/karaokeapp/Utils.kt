package com.svoemesto.karaokeapp

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.svoemesto.karaokeapp.llm.LyricsFinderService
import com.svoemesto.karaokeapp.mlt.MltObject
import com.svoemesto.karaokeapp.mlt.MltObjectAlignmentX
import com.svoemesto.karaokeapp.mlt.MltObjectAlignmentY
import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.AlignmentServiceClient
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.WhisperAsrService
import com.svoemesto.karaokeapp.sync.SyncDirection
import com.svoemesto.karaokeapp.sync.SyncOperation
import com.svoemesto.karaokeapp.sync.SyncRegistry
import com.svoemesto.karaokeapp.sync.SyncTarget
import com.svoemesto.karaokeapp.sync.isOperationAllowed
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.inputStream
import kotlin.io.outputStream
import kotlin.io.path.Path
import kotlin.io.println
import kotlin.io.readText
import kotlin.io.writeText
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.use

// Повторный поиск родителей и аудио-родителей: для КАЖДОЙ песни с root_id = 0 И id_status < 6
// (ещё не привязана ни к куратору-родителю, ни к автоматическому, и ещё не полностью готова —
// прескан намеренно широкий, реальная защита от перезаписи уже проверенного текста - отдельная
// проверка id_status >= 2 чуть ниже, specs/022-song-status-lifecycle)
// прогоняет ДВА независимых механизма подряд, в два прохода:
//   1) "родитель" (findParentCandidateId + applyDuplicateOriginal) - по ВСЕЙ выборке; точное
//      совпадение нормализованного названия среди ВСЕХ песен; root_id/текст/статус переписываются
//      всегда, кроме случая, когда у текущей песни уже есть свой текст И он уже прошёл проверку
//      (id_status >= 2 = TEXT_CHECK) - такой текст не затираем;
//   2) "аудио-родитель" (findAudioParentByWaveform) - ТОЛЬКО по песням, которым в проходе 1
//      реально назначен родитель (root_id проставлен) - акустическая сверка, независимое поле
//      audio_parent_id, текст/статус не трогает.
// Проход 1 выполняется целиком по всем песням ДО начала прохода 2: к этому моменту у части песен
// уже проставлен root_id, и findFamilySongIds (использует findAudioParentByWaveform) видит более
// полную "семью" - точность подбора аудио-родителя от этого выше, чем при чередовании обоих
// механизмов по одной песне за раз. Проход 2 сужен именно до "свежепривязанных" песен - остальные
// (без нового root_id) не стали ближе к семье относительно предыдущего запуска, так что повторно
// гонять по ним тяжёлую акустическую сверку бессмысленно.
// Разовая/повторяемая тяжёлая операция для админа (кнопка "Custom Function" на главной странице
// админки) - т.к. акустическая сверка (ffmpeg-декод + кросс-корреляция) на тысячах песен может
// идти долго, вся работа уходит в фоновый поток; функция возвращает управление сразу же, итоговая
// сводка приходит отдельным SSE-тостом по завершении (тот же паттерн, что и у autoAssignOriginalAll).
fun customFunction(
    storageService: KaraokeStorageService,
    lyricsFinderService: LyricsFinderService,
    storageApiClient: StorageApiClient,
): String {
    thread {
        val ids = mutableListOf<Long>()
        try {
            val connection = WORKING_DATABASE.getConnection()
            if (connection != null) {
                val ps = connection.prepareStatement("SELECT id FROM tbl_songs WHERE root_id = 0 AND id_status < 6 ORDER BY id")
                val rs = ps.executeQuery()
                while (rs.next()) ids.add(rs.getLong("id"))
                rs.close()
                ps.close()
            }
        } catch (e: Exception) {
            println("Поиск родителей и аудио-родителей: ошибка выборки песен — ${e.message}")
        }

        println("Поиск родителей и аудио-родителей: найдено песен: ${ids.size}")

        // --- Фаза 1: родители (по всей выборке, ДО фазы 2) -----------------------------------
        var parentMatched = 0
        var parentSkippedHasText = 0
        val matchedParentIds = mutableListOf<Long>()
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
                    println("  [родитель ${index + 1}/${ids.size}] id=$id — пропущено (не найдено)")
                    return@forEachIndexed
                }
                val candidateId = findParentCandidateId(song, WORKING_DATABASE)
                when {
                    candidateId == null -> {
                        println("  [родитель ${index + 1}/${ids.size}] ${songLogLabel(song)} — родитель не найден")
                    }
                    song.sourceText.isNotBlank() && song.idStatus >= 2 -> {
                        parentSkippedHasText++
                        println(
                            "  [родитель ${index + 1}/${ids.size}] ${songLogLabel(song)} — " +
                                "родитель найден (id=$candidateId), но текст уже проверен (id_status=${song.idStatus}) — пропущено",
                        )
                    }
                    else -> {
                        val original =
                            Song.loadFromDbById(
                                id = candidateId,
                                database = WORKING_DATABASE,
                                storageService = storageService,
                                storageApiClient = storageApiClient,
                            )
                        if (original == null) {
                            println("  [родитель ${index + 1}/${ids.size}] ${songLogLabel(song)} — кандидат id=$candidateId не найден при загрузке")
                        } else {
                            if (original.sourceText.isNotBlank()) {
                                applyDuplicateOriginal(song, original)
                            } else {
                                // specs/281-find-lyrics-overwrites-key-bpm (FR-010): reload-from-db-before-save.
                                // Между loadFromDbById(song) выше и этим saveToDb проходит несколько секунд
                                // (findParentCandidateId + loadFromDbById(original)). Параллельный KEY_BPM_FROM_FILE
                                // мог успеть записать key/bpm — без reload они попадут в diff и перезатрутся.
                                val songToSave =
                                    Song.loadFromDbById(
                                        id = song.id,
                                        database = WORKING_DATABASE,
                                        storageService = storageService,
                                        storageApiClient = storageApiClient,
                                    ) ?: song
                                songToSave.rootId = original.id
                                songToSave.saveToDb()
                                song.rootId = original.id
                            }
                            parentMatched++
                            matchedParentIds.add(id)
                            println("  [родитель ${index + 1}/${ids.size}] ${songLogLabel(song)} — привязан родитель [${songLogLabel(original)}]")
                        }
                    }
                }
            } catch (e: Exception) {
                println("  [родитель ${index + 1}/${ids.size}] id=$id — ошибка: ${e.message}")
            }
        }
        println("Поиск родителей: завершено. Обработано ${ids.size}, назначено $parentMatched, найдено но пропущено (текст уже есть) $parentSkippedHasText")

        // --- Фаза 2: аудио-родители (только по песням, которым в фазе 1 назначен родитель) ---
        var audioMatched = 0
        matchedParentIds.forEachIndexed { index, id ->
            try {
                val song =
                    Song.loadFromDbById(
                        id = id,
                        database = WORKING_DATABASE,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                if (song == null) {
                    println("  [аудио-родитель ${index + 1}/${matchedParentIds.size}] id=$id — пропущено (не найдено)")
                    return@forEachIndexed
                }
                val result = findAudioParentByWaveform(song, WORKING_DATABASE, storageService, storageApiClient)
                if (result.matched) audioMatched++
                println("  [аудио-родитель ${index + 1}/${matchedParentIds.size}] ${songLogLabel(song)} — ${result.reason}")
            } catch (e: Exception) {
                println("  [аудио-родитель ${index + 1}/${matchedParentIds.size}] id=$id — ошибка: ${e.message}")
            }
        }
        println("Поиск аудио-родителей: завершено. Обработано ${matchedParentIds.size}, назначено $audioMatched")

        val summary =
            "Обработано ${ids.size}, родитель назначен $parentMatched " +
                "(найдено, но пропущено из-за текста: $parentSkippedHasText), " +
                "аудио-родитель назначен $audioMatched из ${matchedParentIds.size} с родителем"
        println("Поиск родителей и аудио-родителей: завершено. $summary")

        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Поиск родителей и аудио-родителей",
                    body = summary,
                ),
            ),
        )
    }
    return "Поиск родителей и аудио-родителей запущен в фоне"
}

/**
 * Флаг защиты от гонок для [rescanAllCensoredNames] (см. research.md §4). JVM-single-instance,
 * in-memory достаточно — `karaoke-app` запускается в одном контейнере на admin-машине.
 */
@Volatile
private var isCensoredRescanInProgress: Boolean = false

/**
 specs/277-song-name-censored: фоновая функция — реckan всех `tbl_songs.song_name_censored`
 * по актуальному словарю «Censored». По образцу [customFunction] (см. выше, строки 96-217).
 *
 * Алгоритм (research.md §5):
 *  1. Сначала собрать ВСЕ `id` через `SELECT id FROM tbl_songs ORDER BY id` (один запрос);
 *  2. Для каждого `id` — лёгкий `SELECT id, song_name, song_name_censored FROM tbl_songs
 *     WHERE id = ?` (НЕ `loadFromDbById` — без FK-джойнов ради скорости);
 *  3. Сравнить `songName.censored(database)` с текущим `song_name_censored`;
 *  4. Если отличается — `UPDATE tbl_songs SET song_name_censored = ? WHERE id = ?`;
 *  5. По завершении — SSE-тост с числом обработанных/обновлённых строк и длительностью.
 *
 * Защита от гонок — in-memory флаг [isCensoredRescanInProgress] (single-instance JVM),
 * сбрасывается в `finally`.
 *
 * Идемпотентный повторный запуск после завершения.
 *
 * @return `"OK"` если запущено в фоне; `"ALREADY_RUNNING"` если уже идёт.
 * @see specs/277-song-name-censored/spec.md
 */
fun rescanAllCensoredNames(
    storageService: KaraokeStorageService,
    lyricsFinderService: LyricsFinderService,
    storageApiClient: StorageApiClient,
): String {
    if (isCensoredRescanInProgress) return "ALREADY_RUNNING"
    isCensoredRescanInProgress = true

    thread {
        val startMs = System.currentTimeMillis()
        var processed = 0
        var updated = 0
        var errors = 0
        try {
            // Шаг 1: собрать все id (одним запросом, чтобы не тянуть JOIN'ы и result_text).
            val ids = mutableListOf<Long>()
            try {
                val connection = WORKING_DATABASE.getConnection() ?: return@thread
                val ps = connection.prepareStatement("SELECT id FROM tbl_songs ORDER BY id")
                val rs = ps.executeQuery()
                while (rs.next()) ids.add(rs.getLong("id"))
                rs.close()
                ps.close()
            } catch (e: Exception) {
                println("rescanAllCensoredNames: ошибка выборки id — ${e.message}")
                SNS.send(
                    SseNotification.message(
                        Message(
                            type = "error",
                            head = "Пересканирование цензурированных названий",
                            body = "Ошибка выборки id из tbl_songs: ${e.message}",
                        ),
                    ),
                )
                return@thread
            }

            println("rescanAllCensoredNames: найдено песен: ${ids.size}")

            // Шаги 2-4: для каждой песни — лёгкий SELECT, сравнить, UPDATE при отличии.
            val connection = WORKING_DATABASE.getConnection() ?: return@thread
            ids.forEachIndexed { index, id ->
                try {
                    val psSel =
                        connection.prepareStatement(
                            "SELECT song_name, song_name_censored FROM tbl_songs WHERE id = ?",
                        )
                    psSel.setLong(1, id)
                    val rs = psSel.executeQuery()
                    if (!rs.next()) {
                        rs.close()
                        psSel.close()
                        return@forEachIndexed
                    }
                    val songName = rs.getString("song_name") ?: ""
                    val current = rs.getString("song_name_censored") ?: ""
                    rs.close()
                    psSel.close()

                    processed++
                    val censoredValue = songName.censored(WORKING_DATABASE)
                    if (censoredValue != current) {
                        val psUpd =
                            connection.prepareStatement(
                                "UPDATE tbl_songs SET song_name_censored = ? WHERE id = ?",
                            )
                        psUpd.setString(1, censoredValue)
                        psUpd.setLong(2, id)
                        psUpd.executeUpdate()
                        psUpd.close()
                        updated++
                    }

                    if ((index + 1) % 1000 == 0) {
                        println(
                            "rescanAllCensoredNames: прогресс ${index + 1}/${ids.size} " +
                                "(обновлено $updated)",
                        )
                    }
                } catch (e: Exception) {
                    errors++
                    println("rescanAllCensoredNames: ошибка на id=$id — ${e.message}")
                }
            }

            val durationSec = (System.currentTimeMillis() - startMs) / 1000
            val summary =
                "Обработано $processed песен за $durationSec сек, обновлено $updated" +
                    if (errors > 0) ", ошибок: $errors" else ""
            println("rescanAllCensoredNames: завершено. $summary")

            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Пересканирование цензурированных названий",
                        body = summary,
                    ),
                ),
            )
        } catch (e: Exception) {
            println("rescanAllCensoredNames: непредвиденная ошибка — ${e.message}")
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "error",
                        head = "Пересканирование цензурированных названий",
                        body = "Ошибка: ${e.message}",
                    ),
                ),
            )
        } finally {
            isCensoredRescanInProgress = false
        }
    }

    return "OK"
}

fun fillFormattedFields(
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
) {
    val songList =
        Song.loadListFromDb(
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
            withoutMarkersAndText = false,
        )

    var lastPrintedPercent = -1
    songList.forEachIndexed { index, song ->
        val percent = (((index / songList.size.toDouble()) * 100).toInt() / 10) * 10
        if (percent != lastPrintedPercent) {
            lastPrintedPercent = percent
            println("fillFormattedFields $percent%")
        }

        song.formattedTextSong = song.getTextFormatted()
        song.formattedTextTabs = song.getFormattedNotes()
        song.formattedTextChords = song.getFormattedChords()
        song.saveToDb()
    }
    println("fillFormattedFields 100% - DONE")
}

fun checkHealth(
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    executeActions: Boolean = false,
) {
    val songList =
        Song.loadListFromDb(
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
            withoutMarkersAndText = false,
        )
    var lastPrintedPercent = -1
    songList.forEachIndexed { index, song ->
        val percent = (((index / songList.size.toDouble()) * 100).toInt() / 10) * 10
        if (percent != lastPrintedPercent) {
            lastPrintedPercent = percent
            println("checkHealth $percent%")
        }
        val healthReport = song.healthReportList().errorsOnly()
        if (healthReport.isNotEmpty()) {
            println("${song.fileName} содержит ошибки:")
            healthReport.forEach { healthReport ->
                println("    Тип отчёта: ${healthReport.healthReportType.name}")
                println("    Тип файла: ${healthReport.description}")
                println("    Статус: ${healthReport.healthReportStatus.name}")
                println("    Проблема: ${healthReport.problemText}")
                println("    Решение: ${healthReport.solutionText}")
                println("--------------------------------------------")
                if (executeActions) {
                    healthReport.solutionActions.forEach { action ->
                        action()
                    }
                }
            }
            println()
        }
    }
    println("checkHealth 100% - DONE")
}

@Suppress("unused")
fun syncRemotePicturesInStorage(
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
) {
    val bucketName = "karaoke"

    val listFilesInLocalStorage = storageService.listFilesInfo(bucketName = bucketName)

    println("В локальном хранилище в бакете '$bucketName' найдено файлов: ${listFilesInLocalStorage.size}")
    println()

    listFilesInLocalStorage.forEach { fileInLocal ->

//        println(fileInLocal)

        println("Ищем файл '${fileInLocal.fileName}' в удалённом хранилище...")
        val monoCheckIfExists =
            storageApiClient.checkIfExists(
                bucketName = fileInLocal.bucketName,
                fileName = fileInLocal.fileName,
            )
        val checkIfExists =
            try {
                monoCheckIfExists.block()
            } catch (e: Exception) {
                println("Ошибка при проверке наличия файла в удаленном хранилище: ${e.message}")
                null
            }
        println("Результат проверки наличия файла в удаленном хранилище: $checkIfExists")
        val fileExists = checkIfExists?.get("exists") ?: false

        var needToDelete = false
        var needToAdd = false
        if (fileExists) {
            println("Файл '${fileInLocal.fileName}' найден в удалённом хранилище.")

            val monoGetFileInfo =
                storageApiClient.getFileInfo(
                    bucketName = fileInLocal.bucketName,
                    fileName = fileInLocal.fileName,
                )
            val fileInfo =
                try {
                    monoGetFileInfo.block()
                } catch (e: Exception) {
                    println("Ошибка при получении файла из удаленного хранилища: ${e.message}")
                    null
                }
            println("Результат получения файла из удаленного хранилища: $fileInfo")

            if (fileInfo?.size == fileInLocal.size) {
                println("Размер файла в удалённом хранилище совпадает с размером файла в локальном хранилище. Пропускаем.")
            } else {
                println(
                    "Размер файла в удалённом хранилище '${fileInfo?.size}', в локальном хранилище '${fileInLocal.size}'. Удаляем и заново загружаем.",
                )
                needToDelete = true
                needToAdd = true
            }
        } else {
            println("Файл '${fileInLocal.fileName}' не найден в удалённом хранилище. Загружаем.")
            needToAdd = true
        }

        if (needToDelete) {
            val monoDelete =
                storageApiClient.deleteFile(
                    bucketName = fileInLocal.bucketName,
                    fileName = fileInLocal.fileName,
                )
            val delete =
                try {
                    monoDelete.block()
                } catch (_: Exception) {
                    null
                }
            println("Результат удаления файла: $delete")
        }

        if (needToAdd) {
            val fileInputStream =
                storageService.downloadFile(
                    bucketName = fileInLocal.bucketName,
                    fileName = fileInLocal.fileName,
                )

            val monoUpload =
                storageApiClient.uploadFile(
                    bucketName = fileInLocal.bucketName,
                    fileName = fileInLocal.fileName,
                    fileContent = fileInputStream.readAllBytes(),
                )

            val upload =
                try {
                    monoUpload.block()
                } catch (e: Exception) {
                    println(e.message)
                    null
                }
            println("Результат загрузки файла: $upload")
        }
        println()
    }
}

@Suppress("unused")
fun uploadPicturesToStorage() {
    Pictures
        .loadList(
            whereArgs = emptyMap(),
            database = WORKING_DATABASE,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
            ignoreUseInList = false,
        ).forEach { picture ->
            if (picture.storageFileExists()) {
                println("Картинка '${picture.name}' уже есть в хранилище, пропускаем.")
            } else {
                val pathToFileOnDisk = "${picture.pathToFolder}/${picture.fileName}"
                if (File(pathToFileOnDisk).exists()) {
                    picture.storageUploadFile(pathToFileOnDisk)
                    println("Картинка '${picture.name}': загружаем в хранилище с диска")
                } else {
                    Pictures
                        .getPictureById(
                            id = picture.id,
                            database = WORKING_DATABASE,
                            storageService = KSS_APP,
                            storageApiClient = SAC_APP,
                        )?.let { picWithFull ->
                            val pictureBites = Base64.getDecoder().decode(picWithFull.full)
                            val bais = ByteArrayInputStream(pictureBites)
                            picWithFull.storageUploadFile(file = bais, size = bais.available().toLong())
                            println("Картинка '${picWithFull.name}': загружаем в хранилище из БД")
                        }
                }
            }
        }
}

fun setSongToSyncRemoteTable(id: Long) {
    val sqlToInsert =
        Song
            .loadFromDbById(
                id = id,
                database = Connection.local(),
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            )?.getSqlToInsert(sync = true)
    if (sqlToInsert != null) {
        Song.deleteFromDb(id = id, database = Connection.remote(), sync = true)
        val connection = Connection.remote().getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных REMOTE")
            return
        }
        val ps = connection.prepareStatement(sqlToInsert)
        ps.executeUpdate()
        ps.close()
    }
}

fun setSongToSyncRemoteTable(ids: List<Long>): List<String> {
    val listToCreate: MutableList<Map<String, Any>> = mutableListOf()
    val listToDelete: MutableList<Map<String, Any>> = mutableListOf()
    val listToCreateNames: MutableList<String> = mutableListOf()

    val fromDatabase = Connection.local()
    val tableName = "tbl_songs_sync"

    ids.forEach { id ->
        val sqlToDelete = "DELETE FROM $tableName WHERE id = $id"
        val setStrEncrypted = Crypto.encrypt(sqlToDelete)
        val values: Map<String, Any> =
            mapOf(
                "sqlToDelete" to (setStrEncrypted ?: ""),
            )
        listToDelete.add(values)
    }

    ids.forEach { id ->
        val itemFrom = Song.loadFromDbById(id = id, database = fromDatabase, storageService = KSS_APP, storageApiClient = SAC_APP)
        if (itemFrom != null) {
            listToCreateNames.add(itemFrom.fileName)
            println("Добавляем запись в $tableName: id=${itemFrom.id}, ${itemFrom.fileName}")
            val sqlToInsert = itemFrom.getSqlToInsert(sync = true)
            val setStrEncrypted = Crypto.encrypt(sqlToInsert)
            val values: Map<String, Any> =
                mapOf(
                    "sqlToInsert" to (setStrEncrypted ?: ""),
                )
            listToCreate.add(values)
        }
    }

    val chunkedSize = 10

    if (listToDelete.isNotEmpty()) {
        println("[${Timestamp.from(Instant.now())}] Запрос на сервер на удаление.")

        val chunked = listToDelete.chunked(chunkedSize)
        chunked.forEach { lstToDelete ->
            val values: Map<String, Any> =
                mapOf(
                    "dataCreate" to emptyList<Map<String, Any>>(),
                    "dataUpdate" to emptyList<Map<String, Any>>(),
                    "dataDelete" to lstToDelete,
                    "word" to (Crypto.encrypt(Crypto.WORDS_TO_CHECK) ?: ""),
                )

            val objectMapper = ObjectMapper()
            val requestBody: String = objectMapper.writeValueAsString(values)
            val client = HttpClient.newBuilder().build()
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            println(response.body())
        }
    }

    if (listToCreate.isNotEmpty()) {
        println("[${Timestamp.from(Instant.now())}] Запрос на сервер на добавление.")
        val chunked = listToCreate.chunked(chunkedSize)
        chunked.forEach { lstToCreate ->
            val values: Map<String, Any> =
                mapOf(
                    "dataCreate" to lstToCreate,
                    "dataUpdate" to emptyList<Map<String, Any>>(),
                    "dataDelete" to emptyList<Map<String, Any>>(),
                    "word" to (Crypto.encrypt(Crypto.WORDS_TO_CHECK) ?: ""),
                )

            val objectMapper = ObjectMapper()
            val requestBody: String = objectMapper.writeValueAsString(values)
            val client = HttpClient.newBuilder().build()
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            println(response.body())
        }
    }

    return listToCreateNames
}

/**
 * Результат синхронизации: имена записей по операциям над ЦЕЛЬЮ (created/updated/deleted) плюс
 * moved — имена записей, удалённых из ИСТОЧНИКА в режиме «перемещение». Data class даёт component1..4,
 * поэтому legacy-деструктуризация `val (c, u, d) = ...` остаётся рабочей без правок.
 */
data class SyncResult(
    val created: List<String>,
    val updated: List<String>,
    val deleted: List<String>,
    val moved: List<String>,
)

fun updateRemotePictureFromLocalDatabase(id: Long): SyncResult =
    updateDatabases(
        fromDatabase = Connection.local(),
        toDatabase = Connection.remote(),
        keys = setOf("pictures"),
        idFilter =
            mapOf(
                "pictures" to id,
            ),
    )

// toDatabase — по умолчанию новое подключение Connection.remote(); вызывающий код (например,
// SongEditorController.approve(), specs/094-fix-approve-news-failure) может передать уже открытое
// remote-соединение, чтобы не плодить лишние физические JDBC-подключения к прод-серверу в рамках
// одного запроса (см. research.md фичи 094, п.1-2).
fun updateRemoteSongFromLocalDatabase(
    id: Long,
    toDatabase: KaraokeConnection = Connection.remote(),
): SyncResult =
    updateDatabases(
        fromDatabase = Connection.local(),
        toDatabase = toDatabase,
        // Фича 137: после миграции Settings→Songs (28_rename_settings_to_songs.sql) SyncTarget.key
        // для песен переименован с "song" на "songs". Здесь "song" (singular) → "songs" (plural):
        // иначе цикл `for (target in SyncRegistry.all) { if (target.key !in keys) continue }`
        // пропускает песни и sync возвращает created=0 updated=0, хотя diff действительно есть.
        // Тот же баг был в legacySyncKeys(updateSongs=true) ниже — add("song") вместо add("songs").
        // Симптом, который ловится: после approve `[approve/timing] push на SERVER: ... ms,
        // created=0 updated=0` несмотря на то, что LOCAL менялся (новые source_markers/id_status).
        keys = setOf("songs"),
        idFilter =
            mapOf(
                "songs" to id,
            ),
    )

fun updateRemoteDatabaseFromLocalDatabase(
    updateSongs: Boolean = true,
    updatePictures: Boolean = true,
    updateAuthors: Boolean = true,
): SyncResult =
    updateDatabases(
        fromDatabase = Connection.local(),
        toDatabase = Connection.remote(),
        keys = legacySyncKeys(updateSongs, updatePictures, updateAuthors),
    )

fun updateLocalDatabaseFromRemoteDatabase(
    updateSongs: Boolean = true,
    updatePictures: Boolean = true,
    updateAuthors: Boolean = true,
): SyncResult =
    updateDatabases(
        fromDatabase = Connection.remote(),
        toDatabase = Connection.local(),
        keys = legacySyncKeys(updateSongs, updatePictures, updateAuthors),
    )

private fun legacySyncKeys(
    updateSongs: Boolean,
    updatePictures: Boolean,
    updateAuthors: Boolean,
): Set<String> =
    buildSet {
        // Фича 137: см. updateRemoteSongFromLocalDatabase — SyncTarget.key для песен теперь "songs".
        if (updateSongs) add("songs")
        if (updatePictures) add("pictures")
        if (updateAuthors) add("authors")
    }

// Точка входа для нового generic UI синхронизации (webvue3, /api/sync/*) — один ключ SyncRegistry,
// направление явно задаётся вызывающим кодом (ручная синхронизация конкретной таблицы или "1 клик").
fun runEntitySync(
    key: String,
    direction: SyncDirection,
    id: Long? = null,
): SyncResult {
    val (fromDatabase, toDatabase) =
        if (direction == SyncDirection.LOCAL_TO_SERVER) {
            Connection.local() to Connection.remote()
        } else {
            Connection.remote() to Connection.local()
        }
    return updateDatabases(
        fromDatabase = fromDatabase,
        toDatabase = toDatabase,
        keys = setOf(key),
        idFilter =
            id?.let { mapOf(key to it) } ?: emptyMap(),
    )
}

fun updateDatabases(
    fromDatabase: KaraokeConnection,
    toDatabase: KaraokeConnection,
    keys: Set<String>,
    idFilter: Map<String, Long> = emptyMap(),
): SyncResult {
    if (fromDatabase == toDatabase) return SyncResult(emptyList(), emptyList(), emptyList(), emptyList())

    val listToCreate: MutableList<Map<String, Any>> = mutableListOf()
    val listToUpdate: MutableList<Map<String, Any>> = mutableListOf()
    val listToDelete: MutableList<Map<String, Any>> = mutableListOf()
    // Режим «перемещение»: удаление перенесённых строк из ИСТОЧНИКА (fromDatabase). Отдельный список,
    // т.к. адресуется другой БД, чем listToDelete (то — цель), и флашится ПОСЛЕ записи в цель.
    val listToDeleteFromSource: MutableList<Map<String, Any>> = mutableListOf()

    val listToCreateNames: MutableList<String> = mutableListOf()
    val listToUpdateNames: MutableList<String> = mutableListOf()
    val listToDeleteNames: MutableList<String> = mutableListOf()
    val listToMoveNames: MutableList<String> = mutableListOf()

    println("[${Timestamp.from(Instant.now())}] Устанавливаем связь с базой данный ${fromDatabase.name}...")
    val connFrom = fromDatabase.getConnection()
    if (connFrom == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${fromDatabase.name}")
        return SyncResult(emptyList(), emptyList(), emptyList(), emptyList())
    }
    println("[${Timestamp.from(Instant.now())}] Связь с базой данный ${fromDatabase.name} успешно установлена")

    println("[${Timestamp.from(Instant.now())}] Устанавливаем связь с базой данный ${toDatabase.name}...")
    val connTo = toDatabase.getConnection()
    if (connTo == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${toDatabase.name}")
        return SyncResult(emptyList(), emptyList(), emptyList(), emptyList())
    }
    println("[${Timestamp.from(Instant.now())}] Связь с базой данный ${toDatabase.name} успешно установлена")

    for (target in SyncRegistry.all) {
        if (target.key !in keys) continue
        val whereText = idFilter[target.key]?.let { "WHERE id = $it" } ?: ""
        val ok =
            collectSyncOps(
                target = target,
                fromDatabase = fromDatabase,
                toDatabase = toDatabase,
                whereText = whereText,
                listToCreate = listToCreate,
                listToUpdate = listToUpdate,
                listToDelete = listToDelete,
                listToDeleteFromSource = listToDeleteFromSource,
                listToCreateNames = listToCreateNames,
                listToUpdateNames = listToUpdateNames,
                listToDeleteNames = listToDeleteNames,
                listToMoveNames = listToMoveNames,
            )
        if (!ok) return SyncResult(emptyList(), emptyList(), emptyList(), emptyList())
    }

    if (toDatabase.name == "SERVER") {
        // Полнострочные операции (INSERT/UPDATE) — по весу самой тяжёлой из синхронизируемых таблиц
        // (минимум per-table rowChunkSize), удаления — по общему большому DELETE_CHUNK_SIZE (payload
        // "DELETE ... WHERE id=X" крошечный). См. SyncTarget.rowChunkSize / SyncRegistry.DELETE_CHUNK_SIZE.
        val rowChunk = SyncRegistry.all.filter { it.key in keys }.minOfOrNull { it.rowChunkSize } ?: 100

        if (listToCreate.isNotEmpty()) {
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер на добавление.")
            val chunked = listToCreate.chunked(rowChunk)
            chunked.forEach { lstToCreate ->
                val values: Map<String, Any> =
                    mapOf(
                        "dataCreate" to lstToCreate,
                        "dataUpdate" to emptyList<Map<String, Any>>(),
                        "dataDelete" to emptyList<Map<String, Any>>(),
                        "word" to (Crypto.encrypt(Crypto.WORDS_TO_CHECK) ?: ""),
                    )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build()
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("Content-Type", "application/json")
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                println(response.body())
            }
        }

        if (listToDelete.isNotEmpty()) {
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер на удаление.")

            val chunked = listToDelete.chunked(SyncRegistry.DELETE_CHUNK_SIZE)
            chunked.forEach { lstToDelete ->
                val values: Map<String, Any> =
                    mapOf(
                        "dataCreate" to emptyList<Map<String, Any>>(),
                        "dataUpdate" to emptyList<Map<String, Any>>(),
                        "dataDelete" to lstToDelete,
                        "word" to (Crypto.encrypt(Crypto.WORDS_TO_CHECK) ?: ""),
                    )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build()
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("Content-Type", "application/json")
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                println(response.body())
            }
        }

        if (listToUpdate.isNotEmpty()) {
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер на изменение.")

            val chunked = listToUpdate.chunked(rowChunk)
            chunked.forEach { lstToUpdate ->
                val values: Map<String, Any> =
                    mapOf(
                        "dataCreate" to emptyList<Map<String, Any>>(),
                        "dataUpdate" to lstToUpdate,
                        "dataDelete" to emptyList<Map<String, Any>>(),
                        "word" to (Crypto.encrypt(Crypto.WORDS_TO_CHECK) ?: ""),
                    )

                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build()
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("Content-Type", "application/json")
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                println(response.body())
            }
        }
    }

    // Режим «перемещение»: удаляем перенесённые строки из ИСТОЧНИКА. Делаем это ПОСЛЕ всех записей в
    // цель (блок выше для SERVER-цели; для LOCAL-цели записи уже применены внутри collectSyncOps) —
    // источник чистим только по подтверждённой цели, чтобы исключить потерю данных при сбое переноса.
    if (listToDeleteFromSource.isNotEmpty()) {
        if (fromDatabase.name == "SERVER") {
            println("[${Timestamp.from(Instant.now())}] Запрос на сервер (источник) на удаление перемещённых записей.")
            val payloads =
                listToDeleteFromSource.mapNotNull { item ->
                    val tableName = item["tableName"] as? String ?: return@mapNotNull null
                    val id = item["id"]
                    val sqlToDelete = "DELETE FROM $tableName WHERE id = $id"
                    Crypto.encrypt(sqlToDelete)?.let { mapOf("sqlToDelete" to it) }
                }
            payloads.chunked(SyncRegistry.DELETE_CHUNK_SIZE).forEach { lstToDelete ->
                val values: Map<String, Any> =
                    mapOf(
                        "dataCreate" to emptyList<Map<String, Any>>(),
                        "dataUpdate" to emptyList<Map<String, Any>>(),
                        "dataDelete" to lstToDelete,
                        "word" to (Crypto.encrypt(Crypto.WORDS_TO_CHECK) ?: ""),
                    )
                val objectMapper = ObjectMapper()
                val requestBody: String = objectMapper.writeValueAsString(values)
                val client = HttpClient.newBuilder().build()
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create("https://sm-karaoke.ru/changerecords"))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("Content-Type", "application/json")
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                println(response.body())
            }
        } else {
            println("[${Timestamp.from(Instant.now())}] Удаление перемещённых записей из источника ${fromDatabase.name} (JDBC).")
            listToDeleteFromSource.forEach { item ->
                val tableName = item["tableName"] as? String ?: return@forEach
                val id = item["id"]
                val ps = connFrom.prepareStatement("DELETE FROM $tableName WHERE id = ?")
                ps.setLong(1, (id as? Number)?.toLong() ?: return@forEach)
                ps.executeUpdate()
                ps.close()
            }
        }
    }

    return SyncResult(listToCreateNames, listToUpdateNames, listToDeleteNames, listToMoveNames)
}

// Универсальный hash-diff sync одной сущности реестра (SyncRegistry) — заменяет то, что раньше было
// тремя copy-paste блоками (Song/Pictures/Authors) внутри updateDatabases(). Возвращает false при
// сбое соединения ИЛИ когда fromDatabase вернула пустой список хэшей (защита от случая, когда сбойный
// коннект молча даёт пустой, но не null, результат — без этой проверки idsToDelete включил бы ВСЕ
// записи toDatabase, что стало бы массовым удалением). false — сигнал вызывающему коду прервать всю
// операцию целиком (как и раньше — return Triple(empty...) на уровне updateDatabases), а не просто
// пропустить эту сущность.
private fun <T : Any> collectSyncOps(
    target: SyncTarget<T>,
    fromDatabase: KaraokeConnection,
    toDatabase: KaraokeConnection,
    whereText: String,
    listToCreate: MutableList<Map<String, Any>>,
    listToUpdate: MutableList<Map<String, Any>>,
    listToDelete: MutableList<Map<String, Any>>,
    listToDeleteFromSource: MutableList<Map<String, Any>>,
    listToCreateNames: MutableList<String>,
    listToUpdateNames: MutableList<String>,
    listToDeleteNames: MutableList<String>,
    listToMoveNames: MutableList<String>,
): Boolean {
    val tableName = target.tableName

    // Направление выводим из БД-цели (LOCAL_TO_SERVER == запись в SERVER, иначе SERVER_TO_LOCAL) —
    // сигнатуру менять не нужно, legacy-вызовы не затрагиваются. По нему гейтим операции per-direction.
    val direction = if (toDatabase.name == "SERVER") SyncDirection.LOCAL_TO_SERVER else SyncDirection.SERVER_TO_LOCAL
    val insertAllowed = target.isOperationAllowed(direction, SyncOperation.INSERT)
    val updateAllowed = target.isOperationAllowed(direction, SyncOperation.UPDATE)
    val deleteAllowed = target.isOperationAllowed(direction, SyncOperation.DELETE)
    val moveAllowed = target.isOperationAllowed(direction, SyncOperation.MOVE)

    println("[${Timestamp.from(Instant.now())}] Запрашиваем таблицу хэшей из базы данных ${fromDatabase.name} (${target.displayName})...")
    val listFromIdsHashes = target.listHashes(fromDatabase, whereText)
    if (listFromIdsHashes == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${fromDatabase.name}")
        return false
    }
    println(
        "[${Timestamp.from(
            Instant.now(),
        )}] Таблица хэшей из базы данных ${fromDatabase.name} успешно получена, записей: ${listFromIdsHashes.size}",
    )

    println("[${Timestamp.from(Instant.now())}] Запрашиваем таблицу хэшей из базы данных ${toDatabase.name} (${target.displayName})...")
    val listToIdsHashes = target.listHashes(toDatabase, whereText)
    if (listToIdsHashes == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить связь с базой данный ${toDatabase.name}")
        return false
    }
    println(
        "[${Timestamp.from(
            Instant.now(),
        )}] Таблица хэшей из базы данных ${toDatabase.name} успешно получена, записей: ${listToIdsHashes.size}",
    )

    if (listFromIdsHashes.isEmpty()) return false

    val toHashMap = listToIdsHashes.associateBy { it.id }
    val fromHashMap = listFromIdsHashes.associateBy { it.id }

    val idsToInsert = listFromIdsHashes.filter { it.id !in toHashMap }.map { it.id }
    val idsToUpdate =
        listFromIdsHashes
            .filter { from ->
                val to = toHashMap[from.id]
                to != null && to.recordhash != from.recordhash
            }.map { it.id }
    val idsToDelete = listToIdsHashes.filter { it.id !in fromHashMap }.map { it.id }

    // Зеркальное удаление в ЦЕЛИ (строки, которых нет в источнике) — только если операция разрешена
    // для этого направления. Для режима «перемещение» обычно выключено (см. SyncOperation.DELETE/MOVE).
    // Чанкуем по target.rowChunkSize — при большом числе удаляемых id (например, десятки тысяч
    // событий) loadByIds иначе вернул бы одну огромную Map со всеми строками разом (сами данные нужны
    // только ради label() для лога) — риск OutOfMemoryError на «синхронизации в 1 клик».
    if (deleteAllowed) {
        idsToDelete.chunked(target.rowChunkSize).forEach { chunkIds ->
            val toDeleteMap = target.loadByIds(chunkIds, toDatabase)
            chunkIds.forEach { id ->
                toDeleteMap[id]?.let { listToDeleteNames.add(target.label(it)) }
                if (toDatabase.name == "SERVER") {
                    val sqlToDelete = "DELETE FROM $tableName WHERE id = $id"
                    val setStrEncrypted = Crypto.encrypt(sqlToDelete)
                    listToDelete.add(mapOf("sqlToDelete" to (setStrEncrypted ?: "")))
                } else {
                    target.deleteLocal(id, toDatabase)
                }
            }
        }
    }

    // id, реально перенесённые в цель (подтверждённые) — основа безопасного удаления из источника
    // в режиме move: никогда не удаляем из источника строку, не подтверждённую в цели.
    val actuallyInserted = mutableListOf<Long>()
    val actuallyUpdated = mutableListOf<Long>()

    // Чанкуем по target.rowChunkSize (уже подобран под вес строки данной сущности — см. SyncTarget.kt)
    // — при большом числе id (реалистично при первом прогоне или после большой правки схемы, пример —
    // 19000+ изменённых записей) loadByIds иначе вернул бы одну Map со всеми полными объектами разом,
    // держа их все одновременно в памяти — риск OutOfMemoryError на «синхронизации в 1 клик».
    if (insertAllowed) {
        idsToInsert.chunked(target.rowChunkSize).forEach { chunkIds ->
            val toInsertMap = target.loadByIds(chunkIds, fromDatabase)
            chunkIds.forEach { id ->
                val itemFrom = toInsertMap[id] ?: return@forEach
                listToCreateNames.add(target.label(itemFrom))
                println("[${Timestamp.from(Instant.now())}] Добавляем запись в $tableName: id=$id, ${target.label(itemFrom)}")
                val sqlToInsert = target.getSqlToInsert(itemFrom)
                if (toDatabase.name == "SERVER") {
                    val setStrEncrypted = Crypto.encrypt(sqlToInsert)
                    listToCreate.add(mapOf("sqlToInsert" to (setStrEncrypted ?: "")))
                } else {
                    val connection = toDatabase.getConnection()
                    if (connection == null) {
                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${toDatabase.name}")
                        return false
                    }
                    val ps = connection.prepareStatement(sqlToInsert)
                    ps.executeUpdate()
                    ps.close()
                }
                actuallyInserted.add(id)
            }
        }
        // Дрейф сиквенса после sync-вставки с явным id (specs/099-fix-sync-sequence-drift):
        // sqlToInsert (target.getSqlToInsert) сохраняет ИСХОДНЫЙ id записи с другой БД через
        // OVERRIDING SYSTEM VALUE — GENERATED ALWAYS AS IDENTITY-сиквенс ЦЕЛИ при этом НЕ продвигается
        // (Postgres не трогает сиквенс при явно заданном значении identity-колонки). Если в ЦЕЛЕВУЮ
        // таблицу параллельно пишут и локально-сгенерированные строки (например, tbl_events —
        // посетители локального сайта, не только синк статистики с прод), следующая такая запись берёт
        // из сиквенса номер, который синк уже занял явно, и падает с duplicate key (см. инцидент:
        // "tbl_events_id_key" на локальном karaoke-web). Тот же класс самолечения, что уже есть в
        // KaraokeDbTable.createDbInstance() (сверка сиквенса с MAX(id)), но применительно ко ВСЕЙ
        // пачке вставленных id разом (один setval на весь батч, а не на каждую строку — insertAllowed
        // может вставлять тысячи строк за один синк).
        if (toDatabase.name != "SERVER" && actuallyInserted.isNotEmpty()) {
            val connection = toDatabase.getConnection()
            if (connection != null) {
                try {
                    connection.createStatement().use { st ->
                        st.execute(
                            "SELECT setval(pg_get_serial_sequence('$tableName', 'id'), " +
                                "(SELECT COALESCE(MAX(id), 0) FROM $tableName));",
                        )
                    }
                } catch (e: Exception) {
                    println(
                        "[${Timestamp.from(Instant.now())}] Не удалось выровнять сиквенс $tableName после синка: ${e.message}",
                    )
                }
            }
        }
    }

    if (updateAllowed) {
        idsToUpdate.chunked(target.rowChunkSize).forEach { chunkIds ->
            val fromMap = target.loadByIds(chunkIds, fromDatabase)
            val toMap = target.loadByIds(chunkIds, toDatabase)
            chunkIds.forEach { id ->
                val itemFrom = fromMap[id]
                val itemTo = toMap[id]
                if (itemFrom != null && itemTo != null) {
                    val diff = target.getDiff(itemFrom, itemTo)
                    if (target.shouldPush(diff)) {
                        listToUpdateNames.add(target.label(itemFrom))
                        println(
                            "[${Timestamp.from(
                                Instant.now(),
                            )}] Изменяем запись в $tableName: id=$id, ${target.label(
                                itemFrom,
                            )}, поля: ${diff.joinToString(", ") { it.recordDiffName }}",
                        )
                        val messageRecordChange =
                            RecordChangeMessage(
                                tableName = tableName,
                                recordId = id,
                                diffs = diff,
                                databaseName = toDatabase.name,
                                record = itemFrom,
                            )
                        if (toDatabase.name == "SERVER") {
                            val setStr = messageRecordChange.getSetString()
                            if (setStr != "") {
                                val setStrEncrypted = Crypto.encrypt(setStr)
                                listToUpdate.add(
                                    mapOf(
                                        "tableName" to messageRecordChange.tableName,
                                        "idRecord" to messageRecordChange.recordId,
                                        "setText" to (setStrEncrypted ?: ""),
                                    ),
                                )
                            }
                        } else {
                            val setStr = diff.filter { it.recordDiffRealField }.joinToString(", ") { "${it.recordDiffName} = ?" }
                            if (setStr != "") {
                                val sql = "UPDATE $tableName SET $setStr WHERE id = ?"
                                val connection = toDatabase.getConnection()
                                if (connection == null) {
                                    println(
                                        "[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${toDatabase.name}",
                                    )
                                    return false
                                }
                                val ps = connection.prepareStatement(sql)
                                var index = 1
                                diff.filter { it.recordDiffRealField }.forEach {
                                    when (val v = it.recordDiffValueNew) {
                                        null -> ps.setObject(index, null)
                                        is String -> ps.setString(index, v)
                                        is Long -> ps.setLong(index, v)
                                        is Int -> ps.setInt(index, v)
                                        is Double -> ps.setDouble(index, v)
                                        is Float -> ps.setFloat(index, v)
                                        is Timestamp -> ps.setTimestamp(index, v)
                                        is Boolean -> ps.setBoolean(index, v)
                                        else -> ps.setString(index, v.toString())
                                    }
                                    index++
                                }
                                ps.setLong(index, id)
                                ps.executeUpdate()
                                ps.close()
                            }
                        }
                        actuallyUpdated.add(id)
                    }
                }
            }
        }
    } // if (updateAllowed)

    // Режим «перемещение»: удаляем из ИСТОЧНИКА строки, гарантированно оказавшиеся в цели.
    // Безопасный набор = реально вставленные + реально изменённые + уже идентичные (равный recordhash).
    // Строки, которые различаются, но не были перенесены (insert/update выключены) — НЕ удаляем.
    if (moveAllowed) {
        val unchangedIds =
            listFromIdsHashes
                .filter { toHashMap[it.id]?.recordhash == it.recordhash }
                .map { it.id }
        val movedIds = (actuallyInserted + actuallyUpdated + unchangedIds).distinct()
        movedIds.chunked(target.rowChunkSize).forEach { chunkIds ->
            val movedItems = target.loadByIds(chunkIds, fromDatabase)
            chunkIds.forEach { id ->
                movedItems[id]?.let { listToMoveNames.add(target.label(it)) }
                listToDeleteFromSource.add(mapOf("tableName" to tableName, "id" to id))
                println(
                    "[${Timestamp.from(Instant.now())}] Перемещение: помечаем на удаление из источника ${fromDatabase.name}.$tableName: id=$id",
                )
            }
        }
    }

    return true
}

@Suppress("unused")
fun <T : Serializable> deepCopy(obj: T?): T? {
    if (obj == null) return null
    val baos = ByteArrayOutputStream()
    val oos = ObjectOutputStream(baos)
    oos.writeObject(obj)
    oos.close()
    val bais = ByteArrayInputStream(baos.toByteArray())
    val ois = ObjectInputStream(bais)
    @Suppress("unchecked_cast")
    return ois.readObject() as T
}

// @Throws(IOException::class)
// fun getMd5HashForFile(filename: String?): String? {
//    return try {
//        val md: MessageDigest? = MessageDigest.getInstance("MD5")
//        val buffer = ByteArray(8192)
//        Files.newInputStream(Paths.get(filename)).use { `is` ->
//            var read: Int
//            while (`is`.read(buffer).also { read = it } > 0) {
//                if (md != null) {
//                    md.update(buffer, 0, read)
//                }
//            }
//        }
//        val digest: ByteArray = md?.digest() ?: byteArrayOf()
//        bytesToHex(digest)
//    } catch (e: NoSuchAlgorithmException) {
//        throw RuntimeException(e)
//    }
// }

fun getMd5Hash(source: String): String? =
    try {
        val md = MessageDigest.getInstance("MD5")
        md.update(source.toByteArray())
        val digest = md.digest()
        bytesToHex(digest)
    } catch (e: NoSuchAlgorithmException) {
        throw java.lang.RuntimeException(e)
    }

fun bytesToHex(bytes: ByteArray): String? {
    val builder = StringBuilder()
    for (b in bytes) {
        builder.append(String.format("%02x", b.toInt() and 0xff))
    }
    return builder.toString()
}

fun updateBpmAndKey(
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Int {
    val songList =
        Song.loadListFromDb(
            mapOf("song_tone" to "''", "song_bpm" to "0"),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
    var counter = 0
    songList.forEach { song ->
        val (bpm, key) = getBpmAndKeyFromCsv(song)
        if (bpm != 0L && key != "") {
            println("${song.fileName} : bpm = $bpm, tone = $key")
            song.fields[SongField.BPM] = bpm.toString()
            song.fields[SongField.KEY] = key
            song.saveToDb()
            counter++
        }
    }
    return counter
}

fun updateBpmAndKeyLV(
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Pair<Int, Int> {
    val songList =
        Song.loadListFromDb(
            mapOf("song_tone" to "''", "song_bpm" to "0"),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
    var counterSuccess = 0
    var counterFailed = 0
    songList.forEach { song ->
        val sheetsageInfo = song.sheetsageInfo
        if (sheetsageInfo.isNotEmpty()) {
            val bpm = sheetsageInfo["tempo"] as String
            val key = sheetsageInfo["key"] as String
            if (bpm != "" && key != "") {
                println("${song.fileName} : bpm = $bpm, tone = $key")
                song.fields[SongField.BPM] = bpm
                song.fields[SongField.KEY] = key
                song.saveToDb()
                counterSuccess++
            } else {
                counterFailed++
            }
        }
    }
    return Pair(counterSuccess, counterFailed)
}

fun getBpmAndKeyFromCsv(song: Song): Pair<Long, String> {
    var csvFilePath = song.rootFolder + "/key_bpm.csv"
    var file = File(csvFilePath)
    if (!file.exists()) {
        csvFilePath = Path(song.rootFolder).parent.toString() + "/key_bpm.csv"
        file = File(csvFilePath)
        if (!file.exists()) {
            return Pair(0, "")
        }
    }

    try {
        println(csvFilePath)
        FileReader(csvFilePath).use { fileReader ->
            val csvParser = CSVParser(fileReader, CSVFormat.DEFAULT)

            // Проходимся по записям CSV и читаем данные
            for (csvRecord in csvParser) {
                val fileName = csvRecord.get(0)
                val bpm = csvRecord.get(3)
                val key = csvRecord.get(4)
                if (fileName == song.fileName + ".flac") {
                    return Pair(bpm.toLong(), key)
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return Pair(0, "")
}

fun delDublicates(
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Int {
    var counter = 0
    val songList =
        Song.loadListFromDb(
            mapOf(Pair("tags", "DD")),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
    songList.forEach { song ->
        if (song.tags == "DD") {
            song.deleteFromDb()
            counter++
        }
    }
    return counter
}

fun clearPreDublicates(
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Int {
    var counter = 0
    val songList =
        Song.loadListFromDb(
            mapOf(Pair("tags", "D")),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
    songList.forEach { song ->
        if (song.tags == "D") {
            song.tags = ""
            song.saveToDb()
            counter++
        }
    }
    return counter
}

fun markDublicates(
    author: String,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Int {
    var counter = 0
    val songList =
        Song.loadListFromDb(
            mapOf(Pair("song_author", author)),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
    songList.forEach { song ->
        if (song.tags == "") {
            val listDoubles =
                songList.filter {
                    it.songName == song.songName && it.id > song.id
                }
            if (listDoubles.isNotEmpty()) {
                song.tags = "D"
                song.saveToDb()
                listDoubles.forEach {
                    it.tags = "DD"
                    it.saveToDb()
                    counter++
                }
            }
        }
    }
    return counter
}

@Suppress("unused")
fun create720pForAllUncreated(
    database: KaraokeConnection,
    threadId: Int,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
) {
    val songList = Song.loadListFromDb(database = database, storageService = storageService, storageApiClient = storageApiClient)
    songList.forEach { song ->
        if (File(song.pathToFileLyrics).exists() && !File(song.pathToFile720Lyrics).exists()) {
            if (!File(song.pathToFolder720Lyrics).exists()) {
                Files.createDirectories(Path(song.pathToFolder720Lyrics))
                runCommand(listOf("chmod", "777", song.pathToFolder720Lyrics))
            }
            println("Создаём задание на кодирование в 720р для файла: ${song.nameFileLyrics}")
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.FF_720_LYR, true, 1, threadId)
        }
        if (File(song.pathToFileKaraoke).exists() && !File(song.pathToFile720Karaoke).exists()) {
            if (!File(song.pathToFolder720Karaoke).exists()) {
                Files.createDirectories(Path(song.pathToFolder720Karaoke))
                runCommand(listOf("chmod", "777", song.pathToFolder720Karaoke))
            }
            println("Создаём задание на кодирование в 720р для файла: ${song.nameFileKaraoke}")
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.FF_720_KAR, true, 1, threadId)
        }
    }
}

fun copyIfNeed(
    pathFrom: String,
    pathTo: String,
    folderTo: String,
    log: String = "",
): Int {
    val fileFrom = File(pathFrom)
    val fileTo = File(pathTo)
    if (fileFrom.exists()) {
        if (!fileTo.exists() || (fileFrom.length() != fileTo.length())) {
            if (!File(folderTo).exists()) {
                Files.createDirectories(Path(folderTo))
                runCommand(listOf("chmod", "777", folderTo))
            }
            if (log != "") println(log)
            Files.copy(Path(pathFrom), Path(pathTo), StandardCopyOption.REPLACE_EXISTING)
            return 1
        }
    }
    return 0
}

fun collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(
    songList: List<Song>,
    priorLyrics: Int = 10,
    priorKaraoke: Int = 10,
    threadId: Int,
): Pair<Int, Int> {
    println("Копирование в хранилище и создание заданий на кодирование в 720р")
//    val songList = Song.loadListFromDb(database = database)
    var countCopy = 0
    var countCode = 0
    songList.forEach { song ->

        countCopy +=
            copyIfNeed(
                song.pathToFileLyrics,
                song.pathToStoreFileLyrics,
                song.pathToStoreFolderLyrics,
                "Копируем в хранилище файл: ${song.nameFileLyrics}",
            )
        countCopy +=
            copyIfNeed(
                song.pathToFileKaraoke,
                song.pathToStoreFileKaraoke,
                song.pathToStoreFolderKaraoke,
                "Копируем в хранилище файл: ${song.nameFileKaraoke}",
            )
        countCopy +=
            copyIfNeed(
                song.pathToFileChords,
                song.pathToStoreFileChords,
                song.pathToStoreFolderChords,
                "Копируем в хранилище файл: ${song.nameFileChords}",
            )

        val sourceFileLyrics = File(song.pathToFileLyrics)
        val destinationFileLyrics720 = File(song.pathToFile720Lyrics)
        val needCreateLyrics720 =
            if (!sourceFileLyrics.exists()) {
                false
            } else {
                if (!destinationFileLyrics720.exists()) {
                    true
                } else {
                    if (sourceFileLyrics.lastModified() > destinationFileLyrics720.lastModified()) {
                        destinationFileLyrics720.delete()
                        true
                    } else {
                        false
                    }
                }
            }
        if (needCreateLyrics720) {
            println("Создаём задание на кодирование в 720р для файла: ${song.nameFileLyrics}")
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.FF_720_LYR, true, priorLyrics, threadId)
            countCode++
        }

        val sourceFileKaraoke = File(song.pathToFileKaraoke)
        val destinationFileKaraoke720 = File(song.pathToFile720Karaoke)
        val needCreateKaraoke720 =
            if (!sourceFileKaraoke.exists()) {
                false
            } else {
                if (!destinationFileKaraoke720.exists()) {
                    true
                } else {
                    if (sourceFileKaraoke.lastModified() > destinationFileKaraoke720.lastModified()) {
                        destinationFileKaraoke720.delete()
                        true
                    } else {
                        false
                    }
                }
            }
        if (needCreateKaraoke720) {
            println("Создаём задание на кодирование в 720р для файла: ${song.nameFileKaraoke}")
            KaraokeProcess.createProcess(song, KaraokeProcessTypes.FF_720_KAR, true, priorKaraoke, threadId)
            countCode++
        }
    }
    return Pair(countCopy, countCode)
}

// class ResourceReader {
//    fun readTextResource(filename: String): String {
//        val uri = this.javaClass.getResource("/$filename").toURI()
//        return Files.readString(Paths.get(uri))
//    }
// }

fun replaceSymbolsInSong(sourceText: String): String {
    var result = sourceText.addNewLinesByUpperCase()

    val yo = YoWordsDictionary().dict
    val sourceTextContainsRussianLetters = sourceText.containThisSymbols(RUSSIAN_LETTERS)
    yo.forEach { wordWithYO ->
        val replacedWord = wordWithYO.replace("ё", "е")
        val patt1 = "\\b$replacedWord\\b".toRegex()
        result = result.replace(patt1, wordWithYO)
        val capWordWithYO = wordWithYO.uppercaseFirstLetter()
        val capReplacedWord = capWordWithYO.replace("ё", "е")
        val patt2 = "\\b$capReplacedWord\\b".toRegex()
        result = result.replace(patt2, capWordWithYO)
    }

    result = result.replaceQuotes()

    result = result.replace("_", " ")
    result = result.replace(",", ", ")
    result = result.replace(",  ", ", ")
    result = result.replace("--", "-")
    result = result.replace("—", "-")
    result = result.replace("–", "-")
    result = result.replace("−", "-")
    result = result.replace(" : ", ": ")
    result = result.replace(" :\n", ":\n")

    if (sourceTextContainsRussianLetters) {
        val lines = result.split("\n")
        val linesWithoutChords: MutableList<String> = mutableListOf()
        lines.forEach { line ->
            val lineIsEmpty = line.trim() == ""
            val lineHaveOnlyChordsLetters = line.containOnlyThisSymbols(CHORDS_LETTERS) && !lineIsEmpty
            if (!lineHaveOnlyChordsLetters) {
                linesWithoutChords.add(line.trimEnd())
            }
        }
        result = linesWithoutChords.joinToString("\n")

        result = result.replace("p", "р")
        result = result.replace("y", "у")
        result = result.replace("e", "е")
        result = result.replace("o", "о")
        result = result.replace("a", "а")
        result = result.replace("x", "х")
        result = result.replace("c", "с")
        result = result.replace("A", "А")
        result = result.replace("T", "Т")
        result = result.replace("O", "О")
        result = result.replace("P", "Р")
        result = result.replace("H", "Н")
        result = result.replace("K", "К")
        result = result.replace("X", "Х")
        result = result.replace("C", "С")
        result = result.replace("B", "В")
        result = result.replace("M", "М")
    }

//    result = result.replace(" -\n","_-\n")

    return result
}

fun createFilesByTags(
    listOfTags: List<String> = emptyList(),
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
) {
    val listTags =
        (
            if (listOfTags.isEmpty()) {
                Song.getSetOfTags(database = database)
            } else {
                listOfTags
                    .map {
                        it.uppercase()
                    }.toSet()
            }
        ).toList()
    listTags.forEach { tag ->

        val pathToTagFolder = "$PATH_TO_STORE_FOLDER/TAGS/$tag"
        if (!File(pathToTagFolder).exists()) {
            Files.createDirectories(Path(pathToTagFolder))
            runCommand(listOf("chmod", "777", pathToTagFolder))
        }

        val pathToTagFolder720Karaoke = "$PATH_TO_STORE_FOLDER/720p_Karaoke/TAGS/$tag"
        if (!File(pathToTagFolder720Karaoke).exists()) {
            Files.createDirectories(Path(pathToTagFolder720Karaoke))
            runCommand(listOf("chmod", "777", pathToTagFolder720Karaoke))
        }

        val listOfSongs =
            Song.loadListFromDb(
                mapOf(Pair("tags", tag)),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        listOfSongs.forEach { song ->
            val sourceFileKaraoke = song.pathToFileKaraoke
            if (File(sourceFileKaraoke).exists()) {
                val destinationFile =
                    pathToTagFolder + "/" + sourceFileKaraoke.split("/").last().replace(" [karaoke].mp4", " [karaoke] {$tag}.mp4")
                if (!File(destinationFile).exists()) {
                    Files.copy(Path(sourceFileKaraoke), Path(destinationFile))
                }
            }

            val sourceFile720Karaoke = song.pathToFile720Karaoke
            if (File(sourceFile720Karaoke).exists()) {
                val destinationFile =
                    pathToTagFolder720Karaoke + "/" +
                        sourceFile720Karaoke.split("/").last().replace(" [karaoke] 720p.mp4", " [karaoke] {$tag} 720p.mp4")
                if (!File(destinationFile).exists()) {
                    Files.copy(Path(sourceFile720Karaoke), Path(destinationFile))
                }
            }
        }
    }
}

fun createDigestForAllAuthors(
    vararg authors: String,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
) {
    val listAuthors = getAuthorsForDigest(database = database)
    listAuthors.forEach { author ->
        if (authors.isEmpty() || author in authors) {
            val txt = "ЗАКРОМА - «$author»\n\n${getAuthorDigest(
                author,
                false,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).first}"
            val fileName = "/sm-karaoke/system/Digest/$author (digest).txt"
            File(fileName).writeText(txt, Charsets.UTF_8)
            runCommand(listOf("chmod", "666", fileName))
        }
    }
}

fun createDigestForAllAuthorsForOper(
    vararg authors: String,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
) {
    val listAuthors = getAuthorsForDigest(database = database)
    var txt = ""
    var total = 0
    listAuthors.forEach { author ->
        if (authors.isEmpty() || author in authors) {
            val (digest, count) =
                getAuthorDigest(
                    author,
                    false,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            if (digest.isNotEmpty()) {
                txt += "«$author»\nПесен: $count шт.\n[spoiler]\n$digest[/spoiler]\n\n"
                total += count
            }
        }
    }
    txt = "----------ЗАКРОМА----------\nВсего песен: $total шт.\n\n$txt"
    val fileName = "/sm-karaoke/system/Digest/OPER_digest.txt"
    File(fileName).writeText(txt, Charsets.UTF_8)
    runCommand(listOf("chmod", "666", fileName))
}

fun getAuthorsForDigest(database: KaraokeConnection): List<String> {
    val connection = database.getConnection()
    if (connection == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
        return emptyList()
    }
    var statement: Statement? = null
    var rs: ResultSet? = null
    var sql: String

    try {
        statement = connection.createStatement()

        sql = "select song_author, count(DISTINCT song_album) as albums, count(DISTINCT id) as songs " +
            "from tbl_songs " +
//                "where id_boosty != '' AND id_boosty IS NOT NULL AND root_folder NOT LIKE '%/Разное/%' " +
            "where id_boosty != '' AND id_boosty IS NOT NULL " +
            "group by song_author"

        rs = statement.executeQuery(sql)
        val result: MutableList<String> = mutableListOf()
        while (rs.next()) {
            val author = rs.getString("song_author")
            result.add(author)
        }
        result.sort()
        return result
    } catch (e: SQLException) {
        e.printStackTrace()
    } finally {
        try {
            rs?.close() // close result set
            statement?.close() // close statement
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }
    return emptyList()
}

fun getAuthorDigest(
    author: String,
    withRazor: Boolean = true,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Pair<String, Int> {
    val maxSymbols = 16300

    val listDigest =
        Song
            .loadListFromDb(
                mapOf(Pair("song_author", author)),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).filter { it.digestIsFull }
            .map { it.digest }

    var result = ""
    var counter = 0

    listDigest.forEach { digets ->
        if (withRazor && (counter + digets.length > maxSymbols)) {
            result +=
                "\n(ПРОДОЛЖЕНИЕ - В КОММЕНТАРИЯХ)\n\n----------------------------------------------------------------------------------------\n\n\n"
            counter = 0
        }
        result += digets + "\n"
        counter += digets.length
    }

    return result to listDigest.size
}

@Suppress("unused")
fun searchSongText2(song: Song) {
    val searchQuery = "${song.author} ${song.songName}"
    val searchUrl = "https://www.google.com/search?q=${searchQuery.replace(" ", "+")}+текст+песни"

    // Загрузка страницы результатов поиска
    val document = Jsoup.connect(searchUrl).get()

    val links: List<Element> = document.select("a")

    // Пройтись по найденным ссылкам и вывести их href (URL)
    for (link in links) {
        val href = link.attr("href")
        println(href)
    }
}

fun searchSongText(song: Song): String {
    val searchQuery = "${song.author} ${song.songName}".replace("&", "")
    val searchUrl = "https://www.google.com/search?q=${searchQuery.replace(" ", "+")}+текст+песни"

    // Загрузка страницы результатов поиска
    var document = Jsoup.connect(searchUrl).get()

    // Поиск текста песни на странице результатов
    var lyricsElement = document.selectFirst("div[data-lyricid]")

    println(lyricsElement?.text())

    lyricsElement?.let { le ->
        val spanElements = le.select("span")
        val spanTexts = spanElements.map { it.ownText() }
        spanTexts.let { st ->
            return spanTexts.joinToString("\n")
        }
    }

    val links: List<Element> = document.select("a")

    println("Ссылок найдено: ${links.size}")

    for (link in links) {
        if (link.attr("href").startsWith("http")) {
            println(link.attr("href"))
        }
    }
    // Пройтись по найденным ссылкам и вывести их href (URL)
    for (link in links) {
        val href = link.attr("href")
        if (href.startsWith("https://learnsongs.ru/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.getElementById("tab01")
//            println(lyricsElement?.ownText())
//            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> &nbsp;", "")
                text = text.replace("<br> ", "")
                text = text.replace("<br>", "")
                return text
            }
        } else if (href.startsWith("https://textypesen.com/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.selectFirst("div.col-sm-100.text-center")
            println(lyricsElement?.ownText())
            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")
                text = text.replace("&nbsp;", " ")
                text = text.replace("<p class=\"font-size-20\">", "")
                text = text.replace("</p>", "")
                println(text)
                return text
            }
        } else if (href.startsWith("https://musictxt.ru/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h1Elements = document.select("h1")
            for (h1Element in h1Elements) {
                h1Element.remove()
            }

            val aElements = document.select("a")
            for (aElements in aElements) {
                aElements.remove()
            }

            lyricsElement = document.getElementById("layer2")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")
                text = text.replace("<!-- Yandex.RTB R-A-587487-5 -->", "")
                text =
                    text.replace(
                        """<div id="yandex_rtb_R-A-587487-5"></div><script>window.yaContextCb.push(()=>{Ya.Context.AdvManager.render({"blockId": "R-A-587487-5","renderTo": "yandex_rtb_R-A-587487-5"})})</script></pre>""",
                        "",
                    )
                text = text.replace("<pre>", "")
                println(text)
                return text
            }
        } else if (href.startsWith("https://textocat.ru/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h1Elements = document.select("h1")
            for (h1Element in h1Elements) {
                h1Element.remove()
            }

            val aElements = document.select("a")
            for (aElements in aElements) {
                aElements.remove()
            }

            lyricsElement = document.selectFirst("div.entry-content")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.text()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")

                println(text)
                return text
            }
        } else if (href.startsWith("https://txtsong.ru/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.selectFirst("div.the_content")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            val text = lyricsElement?.text()

            if (text != null) {
                println(text)
                return text
            }
        } else if (href.startsWith("https://pesni.guru/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.selectFirst("div.songtext")
            println(lyricsElement?.text())
            println(lyricsElement?.ownText())
            println(lyricsElement?.html())

            val text = lyricsElement?.ownText()

            if (text != null) {
                return text
            }
        } else if (href.startsWith("https://teksti-pesenok.pro/")) {
            println(href)

            document = Jsoup.connect(href).get()

            val h2Elements = document.select("h2")
            for (h2Element in h2Elements) {
                h2Element.remove()
            }

            lyricsElement = document.getElementById("text")
            println("text()")
            println(lyricsElement?.text())
            println("ownText()")
            println(lyricsElement?.ownText())
            println("html()")
            println(lyricsElement?.html())

            var text = lyricsElement?.html()

            if (text != null) {
                text = text.replace("<br> ", "\n")
                text = text.replace("<br>", "\n")
                text = text.replace("&nbsp;", " ")
                text = text.replace("""<span class="status_select" itemprop="lyrics">""", "")
                text = text.replace("</span>", "")

                return text
            }
        } else if (href.startsWith("https://text-lyrics.ru/")) {
            println(href)

            try {
                document = Jsoup.connect(href).get()
                lyricsElement = document.selectFirst("div.entry_content")
                println("text()")
                println(lyricsElement?.text())
                println("ownText()")
                println(lyricsElement?.ownText())
                println("html()")
                println(lyricsElement?.html())

                val text = lyricsElement?.text()
                if (text != null) {
                    println(text)
                    return text
                }
            } catch (_: Exception) {
                return ""
            }
        }
    }

    return ""
}

@Suppress("unused")
fun getNewTone(
    tone: String,
    capo: Int,
): String {
    val noteAndTone = tone.split(" ")
    val nameChord = noteAndTone[0]
    val (_, note) = MusicChord.getChordNote(nameChord)
    var newIndexNote = MusicNote.entries.indexOf(note!!) - capo
    if (newIndexNote < 0) newIndexNote = MusicNote.entries.size + newIndexNote
    val newNote = MusicNote.entries[newIndexNote]
    return "${newNote.names.first()} ${noteAndTone[1]}"
}

fun generateChordLayout(
    chordName: String,
    capo: Int,
): List<MltObject> {
    val chordNameAndFret = chordName.split("|")
    val nameChord = chordNameAndFret[0]
    val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
    val (chord, note) = MusicChord.getChordNote(nameChord)
    return if (chord != null && note != null) generateChordLayout(chord, note, fretChord, capo) else emptyList()
}

fun generateChordLayout(
    chord: MusicChord,
    startRootNote: MusicNote,
    startInitFret: Int,
    capo: Int,
): List<MltObject> {
    var newIndexNote = MusicNote.entries.indexOf(startRootNote) - capo
    if (newIndexNote < 0) newIndexNote = MusicNote.entries.size + newIndexNote
    val note = MusicNote.entries[newIndexNote]
    var fret = startInitFret - capo
    if (fret < 0) fret = 0

    var fingerboards: List<Fingerboard> = chord.getFingerboard(note, if (fret == 0) note.defaultRootFret else fret, capo)

    var nextFret = fret
    while (fingerboards.isEmpty()) {
        nextFret += 1
        fingerboards = chord.getFingerboard(note, if (nextFret == 0) note.defaultRootFret else nextFret)
    }

    val initFret = fingerboards[0].rootFret
    val result: MutableList<MltObject> = mutableListOf()
    val chordLayoutW = (Karaoke.frameHeightPx / 4)
    val chordLayoutH = chordLayoutW

    val chordName = "${note.names.first()}${chord.names.first()}"
    val chordNameMltText = Karaoke.chordLayoutChordNameMltText.copy(chordName)
//    chordNameMltText.text = chordName

    val fretW = (chordLayoutW / 6.0).toInt()
    var fretNumberTextH = 0
    val mltShapeFingerCircleDiameter = fretW / 2
    val fretRectangleMltShape = Karaoke.chordLayoutFretsRectangleMltShape.copy()

    // Бэкграунд
    result.add(
        MltObject(
            layoutW = chordLayoutW,
            layoutH = chordLayoutH,
            privateShape = Karaoke.chordLayoutBackgroundRectangleMltShape,
            alignmentX = MltObjectAlignmentX.LEFT,
            alignmentY = MltObjectAlignmentY.TOP,
            privateX = 0,
            privateY = 0,
            privateW = chordLayoutW,
            privateH = chordLayoutH,
        ),
    )

    // Название аккорда
    val mltTextChordName =
        MltObject(
            layoutW = chordLayoutW,
            layoutH = chordLayoutH,
            privateShape = chordNameMltText,
            alignmentX = MltObjectAlignmentX.CENTER,
            alignmentY = MltObjectAlignmentY.TOP,
            privateX = chordLayoutW / 2,
            privateY = 0,
            privateH = (chordLayoutH * 0.2).toInt(),
        )
    result.add(mltTextChordName)

    // Номера ладов
    val firstFret = if (initFret == 0) 1 else initFret
    for (fret in firstFret + capo..(firstFret + capo + 3)) {
        val fretNumberMltText = Karaoke.chordLayoutFretsNumbersMltText.copy(fret.toString())
//        fretNumberMltText.text = fret.toString()

        val mltTextFretNumber =
            MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                privateShape = fretNumberMltText,
                alignmentX = MltObjectAlignmentX.CENTER,
                alignmentY = MltObjectAlignmentY.TOP,
                privateX = fretW * (fret - firstFret + 1 - capo) + fretW / 2,
                privateY = mltTextChordName.h,
                privateH = (chordLayoutH * 0.1).toInt(),
            )
        fretNumberTextH = mltTextFretNumber.h
        result.add(mltTextFretNumber)
    }

    val mltShapeFretRectangleH = (chordLayoutH - (mltTextChordName.h + 2 * fretNumberTextH)) / 5

    // Прямоугольники ладов

    for (string in 0..4) {
        // Порожек или каподастр
        if (initFret == 0) {
            val nutRectangleMltShape =
                if (capo ==
                    0
                ) {
                    Karaoke.chordLayoutNutsRectangleMltShape.copy()
                } else {
                    Karaoke.chordLayoutCapoRectangleMltShape.copy()
                }
            val mltShapeNutRectangle =
                MltObject(
                    layoutW = chordLayoutW,
                    layoutH = chordLayoutH,
                    privateShape = nutRectangleMltShape,
                    alignmentX = MltObjectAlignmentX.RIGHT,
                    alignmentY = MltObjectAlignmentY.TOP,
                    privateX = fretW,
                    privateY = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH * (string) + mltShapeFingerCircleDiameter / 2,
                    privateW = fretW / 5,
                    privateH = mltShapeFretRectangleH,
                )
            result.add(mltShapeNutRectangle)
        }
        for (fret in 1..4) {
            val mltShapeFretRectangle =
                MltObject(
                    layoutW = chordLayoutW,
                    layoutH = chordLayoutH,
                    privateShape = fretRectangleMltShape,
                    alignmentX = MltObjectAlignmentX.CENTER,
                    alignmentY = MltObjectAlignmentY.TOP,
                    privateX = fretW * fret + fretW / 2,
                    privateY = mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH * (string) + mltShapeFingerCircleDiameter / 2,
                    privateW = fretW,
                    privateH = mltShapeFretRectangleH,
                )
            result.add(mltShapeFretRectangle)
        }
    }

    // Распальцовка
    fingerboards.forEach { fingerboard ->

        // Приглушение струны
        if (fingerboard.muted) {
            val mutedRectangleMltShape = Karaoke.chordLayoutMutedRectangleMltShape.copy()
            val mltShapeMutedRectangle =
                MltObject(
                    layoutW = chordLayoutW,
                    layoutH = chordLayoutH,
                    privateShape = mutedRectangleMltShape,
                    alignmentX = MltObjectAlignmentX.LEFT,
                    alignmentY = MltObjectAlignmentY.TOP,
                    privateX = fretW,
                    privateY =
                        mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH * (fingerboard.guitarString.number - 1) +
                            mltShapeFingerCircleDiameter / 2 -
                            fretRectangleMltShape.shapeOutline / 2,
                    privateW = fretW * 4,
                    privateH = fretRectangleMltShape.shapeOutline,
                )
            result.add(mltShapeMutedRectangle)
        }

        if (!((initFret == 0 && fingerboard.fret == 0) || fingerboard.muted)) {
            val fingerCircleMltShape = Karaoke.chordLayoutFingerCircleMltShape.copy()
            val mltShapeFingerCircle =
                MltObject(
                    layoutW = chordLayoutW,
                    layoutH = chordLayoutH,
                    privateShape = fingerCircleMltShape,
                    alignmentX = MltObjectAlignmentX.LEFT,
                    alignmentY = MltObjectAlignmentY.TOP,
                    privateX =
                        fretW * (fingerboard.fret - initFret + (if (initFret != 0) 1 else 0)) + fretW / 2 -
                            (mltShapeFingerCircleDiameter) / 2,
                    privateY =
                        mltTextChordName.h + fretNumberTextH + mltShapeFretRectangleH * (fingerboard.guitarString.number - 1) +
                            mltShapeFingerCircleDiameter / 2 -
                            mltShapeFingerCircleDiameter / 2,
                    privateW = mltShapeFingerCircleDiameter,
                    privateH = mltShapeFingerCircleDiameter,
                )
            result.add(mltShapeFingerCircle)
        }
    }

    // Барре (если первый лад не нулевой)
    if (initFret != 0) {
        val fingerCircleMltShape = Karaoke.chordLayoutFingerCircleMltShape.copy()
        fingerCircleMltShape.type = MltObjectType.ROUNDEDRECTANGLE
        val mltShapeFingerCircle =
            MltObject(
                layoutW = chordLayoutW,
                layoutH = chordLayoutH,
                privateShape = fingerCircleMltShape,
                alignmentX = MltObjectAlignmentX.LEFT,
                alignmentY = MltObjectAlignmentY.TOP,
                privateX = fretW + fretW / 2 - (mltShapeFingerCircleDiameter) / 2,
                privateY = mltTextChordName.h + fretNumberTextH + mltShapeFingerCircleDiameter / 2 - mltShapeFingerCircleDiameter / 2,
                privateW = mltShapeFingerCircleDiameter,
                privateH = mltShapeFretRectangleH * 5 + mltShapeFingerCircleDiameter,
            )
        result.add(mltShapeFingerCircle)
    }

    return result
}

fun getFontSizeByHeight(
    heightPx: Int,
    font: Font,
): Int {
    var fontSize = 1
    while (getTextWidthHeightPx("0", Font(font.fontName, font.style, fontSize)).second < heightPx) {
        fontSize += 1
    }
    return fontSize - 1
}

fun getFileNameByMasks(
    pathToFolder: String,
    startWith: String,
    suffixes: List<String>,
    extension: String,
): String {
    try {
        val files =
            Files
                .walk(Path(pathToFolder))
                .filter(Files::isRegularFile)
                .map { it.toString() }
                .filter { it.endsWith(extension) && it.startsWith("$pathToFolder/$startWith") }
                .map { Path(it).toFile().name }
                .toList()
        suffixes.forEach { suffix ->
            val filename = files.firstOrNull { it.startsWith("${startWith}$suffix") }
            if (filename != null) return filename
        }
    } catch (_: Exception) {
        return ""
    }
    return ""
}

fun createSongTextFile(
    song: Song,
    songVersion: SongVersion,
) {
    val filePath = song.getOutputFilename(SongOutputFile.TEXT, songVersion)
    val fileText = File(filePath)
    Files.createDirectories(Path(fileText.parent))
    runCommand(listOf("chmod", "777", fileText.parent))
    val text = song.getTextBody()
    fileText.writeText(text)
    runCommand(listOf("chmod", "666", filePath))
}

fun createSongDescriptionFile(
    song: Song,
    songVersion: SongVersion,
) {
    val filePath = song.getOutputFilename(SongOutputFile.DESCRIPTION, songVersion)
    val fileText = File(filePath)
    Files.createDirectories(Path(fileText.parent))
    runCommand(listOf("chmod", "777", fileText.parent))
    val text = song.getDescriptionWithHeaderWOTimecodes(songVersion)
    fileText.writeText(text)
    runCommand(listOf("chmod", "666", filePath))
}

@Suppress("unused")
fun test() {
    val fileNameXml = "src/main/resources/song.xml"
    val props = Properties()
//    val frameW = Integer.valueOf(props.getProperty("FRAME_WIDTH_PX", "1"));
//    val kdeBackgroundFolderPath = props.getProperty("kdeBackgroundFolderPath", "&&&")

    props.setProperty("FRAME_FPS", Karaoke.frameFps.toString())
    props.setProperty(
        "VOICES_SETTINGS",
        """
        voice=0;group=0;fontNameText=Tahoma;colorText=255,255,255,255;fontNameBeat=Tahoma;colorBeat=155,255,255,255
        voice=0;group=1;fontNameText=Lobster;colorBeat=105,255,105,255;fontNameBeat=Lobster;colorText=255,255,155,255
        """.trimIndent(),
    )
    props.storeToXML(File(fileNameXml).outputStream(), "Какой-то комментарий")
    props.loadFromXML(File(fileNameXml).inputStream())

    val videoSettings = props.getProperty("VOICES_SETTINGS").split("\n")

    videoSettings.forEach { vs ->
        if (vs.isNotEmpty()) {
            val vars = vs.split(";")
            vars.forEach { variable ->
                val nameAndValue = variable.split("=")
                when (nameAndValue[0]) {
                    "voice" -> println("${nameAndValue[0]} = ${(nameAndValue[1].toLong())}")
                    "group" -> println("${nameAndValue[0]} = ${(nameAndValue[1].toLong())}")
                    "fontNameText" -> println("${nameAndValue[0]} = ${nameAndValue[1]}")
                    "fontNameBeat" -> println("${nameAndValue[0]} = ${nameAndValue[1]}")
                    "colorText" -> {
                        val rgba = nameAndValue[1].split(",")
                        println("colorText r = ${(rgba[0].toLong())}")
                        println("colorText g = ${(rgba[1].toLong())}")
                        println("colorText b = ${(rgba[2].toLong())}")
                        println("colorText a = ${(rgba[3].toLong())}")
                    }
                    "colorBeat" -> {
                        val rgba = nameAndValue[1].split(",")
                        println("colorBeat r = ${(rgba[0].toLong())}")
                        println("colorBeat g = ${(rgba[1].toLong())}")
                        println("colorBeat b = ${(rgba[2].toLong())}")
                        println("colorBeat a = ${(rgba[3].toLong())}")
                    }
                }
            }
        }
    }
}

@Suppress("unused")
fun getTextWidthHeightPx(
    text: String,
    fontName: String,
    fontStyle: Int,
    fontSize: Int,
): Pair<Double, Double> = getTextWidthHeightPx(text, Font(fontName, fontStyle, fontSize))

fun getTextWidthHeightPx(
    text: String,
    font: Font,
): Pair<Double, Double> {
    val notesSymbols = "●∙◉♪"
    val notesFont = Font("Arial Unicode MS", font.style, font.size)
    var notesString = ""
    var notNotesString = ""
    text.forEach { symbol ->
        if (notesSymbols.contains(symbol)) {
            notesString += symbol
        } else {
            notNotesString += symbol
        }
    }

    val graphics2D1 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
    graphics2D1.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2D1.font = font
    val rect1 = graphics2D1.fontMetrics.getStringBounds(notNotesString, graphics2D1)

    val graphics2D2 = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).graphics as Graphics2D
    graphics2D2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2D2.font = notesFont
    val rect2 = graphics2D2.fontMetrics.getStringBounds(notesString, graphics2D2)

    return Pair(rect1.width + rect2.width, rect1.height.coerceAtLeast(rect2.height))
}

@Suppress("unused")
fun convertMarkersToSubtitles(
    pathToSourceFile: String,
    pathToResultFile: String = "",
) {
    val gson =
        GsonBuilder()
            .setLenient()
            .create()

    val sourceFileBody = File(pathToSourceFile).readText(Charsets.UTF_8)
    val regexpLines = Regex("""<property name="kdenlive:markers"[^<]([\s\S]+?)</property>""")
    val linesMatchResults = regexpLines.findAll(sourceFileBody)
    var countSubsFile = 0L
    val subsFiles: MutableList<MutableList<Marker>> = emptyList<MutableList<Marker>>().toMutableList()
    linesMatchResults.forEach { lineMatchResult ->
        val textToAnalize =
            lineMatchResult.groups[1]
                ?.value
                ?.replace("\n", "")
                ?.replace("[", "")
                ?.replace("]", "")
        val regexpMarkers = Regex("""\{[^}]([\s\S]+?)}""")
        val markersMatchResults = regexpMarkers.findAll(textToAnalize!!)
        if (markersMatchResults.iterator().hasNext()) {
            countSubsFile++
            val markers = mutableListOf<Marker>()
            markersMatchResults.forEach { markerMatchResult ->
                val marker = gson.fromJson(markerMatchResult.value, Marker::class.java)
                markers.add(marker)
            }
            subsFiles.add(markers)
        }
    }

    var countCreatedFiles = 0L
    for (indexSubFiles in 0 until subsFiles.size) {
        val subFile = subsFiles[indexSubFiles]
        var prevMarkerIsEndLine = true
        val subtitles = mutableListOf<Subtitle>()
        for (indexMarker in 0 until subFile.size) {
            val currMarker = subFile[indexMarker]

            if (currMarker.comment in ".\\/*" || indexMarker == subFile.size - 1) {
                prevMarkerIsEndLine = true
                continue
            }

            val nextMarker = subFile[indexMarker + 1]
            val isLineStart = prevMarkerIsEndLine
            val isLineEnd = (nextMarker.comment in ".\\/*" || indexMarker == subFile.size - 1)
            prevMarkerIsEndLine = isLineEnd

            var subText = currMarker.comment.replace(" ", "_").replace("-", "")
            if (isLineStart) subText = subText[0].uppercase() + subText.subSequence(1, subText.length)
            if (isLineStart) subText = "//$subText"
            if (isLineEnd) subText = "${subText}\\\\"

            val startTimecode = convertFramesToTimecode(currMarker.pos, 60.0)
            val endTimecode = convertFramesToTimecode(nextMarker.pos, 60.0)

            val subtitle =
                Subtitle(
                    startTimecode = startTimecode,
                    endTimecode = endTimecode,
                    mltText =
                        Karaoke.voices[0]
                            .groups[0]
                            .mltText
                            .copy(subText),
                    isLineStart = isLineStart,
                    isLineEnd = isLineEnd,
                )
            subtitles.add(subtitle)
        }

        var textSubtitleFile = ""
        for (index in 0 until subtitles.size) {
            val subtitle = subtitles[index]
            textSubtitleFile += "${index + 1}\n${subtitle.startTimecode} --> ${subtitle.endTimecode}\n${subtitle.mltText.text}\n\n"
        }

        if (textSubtitleFile != "") {
            countCreatedFiles++
            val fileNameNewSubs = "${pathToSourceFile}${if (countCreatedFiles == 1L) "" else "_${countCreatedFiles - 1}"}.srt"
            File(fileNameNewSubs).writeText(textSubtitleFile)
            runCommand(listOf("chmod", "666", fileNameNewSubs))
        }
    }
}

fun getRandomFile(
    pathToFolder: String,
    extension: String = "",
): String {
    val listFiles = getListFiles(pathToFolder, extension)
    return if (listFiles.isEmpty()) "" else listFiles[Random.nextInt(listFiles.size)]
}

/**
 * Рекурсивно обходит [pathToFolder] и возвращает пути файлов, отфильтрованные по [extension]/[startWith].
 *
 * Фильтрация происходит внутри цепочки [Files.walk], до материализации в список — на произвольно
 * большом/глубоко вложенном дереве в памяти не накапливается ничего сверх уже отфильтрованного
 * результата. [Files.walk] закрывается через [use] независимо от исхода обхода.
 *
 * @see archive/docs/features/async-process-queue.md
 */
fun getListFiles(
    pathToFolder: String,
    extension: String = "",
    startWith: String = "",
): List<String> =
    try {
        Files
            .walk(Path(pathToFolder))
            .use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .map { it.toString() }
                    .filter {
                        it.endsWith(extension) &&
                            it.startsWith("$pathToFolder/$startWith")
                    }.toList()
            }.sorted()
    } catch (_: Exception) {
        emptyList()
    }

/**
 * Рекурсивно обходит [pathToFolder] и возвращает пути файлов, подходящие под любое из [extensions]
 * (если список не пуст), любое из [startsWith] (если список не пуст) и ни одно из [excludes].
 *
 * Один проход по дереву — не переиспользует однорасширенческую перегрузку с пустым `extension`
 * (что буферизовало бы в памяти все файлы дерева независимо от расширения, см. `research.md`,
 * Находка A этой фичи).
 *
 * @see archive/docs/features/async-process-queue.md
 */
fun getListFiles(
    pathToFolder: String,
    extensions: List<String> = listOf(),
    startsWith: List<String> = listOf(),
    excludes: List<String> = listOf(),
): List<String> =
    try {
        Files
            .walk(Path(pathToFolder))
            .use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .map { it.toString() }
                    .filter { path ->
                        (extensions.isEmpty() || extensions.any { path.endsWith(it) }) &&
                            (startsWith.isEmpty() || startsWith.any { path.startsWith("$pathToFolder/$it") }) &&
                            (excludes.isEmpty() || excludes.none { path.contains(it) })
                    }.toList()
            }.sorted()
    } catch (_: Exception) {
        emptyList()
    }

@Suppress("unused")
fun extractSubtitlesFromAutorecognizedFile(
    pathToFileFrom: String,
    pathToFileTo: String,
): String {
    val text = File(pathToFileFrom).readText(Charsets.UTF_8)
    val regexpLines = Regex("""href="\d+?#[^/a](.+?)/a""")
    val linesMatchResults = regexpLines.findAll(text)
    var counter = 0L
    var subs = ""
    linesMatchResults.forEach { lineMatchResult ->
        val line = lineMatchResult.value
        val startEnd =
            Regex("""href="\d+?[^"&gt](.+?)"&gt""")
                .find(line)
                ?.groups
                ?.get(1)
                ?.value
                ?.split(":")
        val start = convertMillisecondsToTimecode(((startEnd?.get(0) ?: "0").toDouble() * 1000).toLong())
        val end = convertMillisecondsToTimecode(((startEnd?.get(1) ?: "0").toDouble() * 1000).toLong())
        val word =
            Regex("""&gt[^&lt](.+?)&lt""")
                .find(line)
                ?.groups
                ?.get(1)
                ?.value
        if (word != "Речь отсутствует") {
            counter++
            subs += "${counter}\n$start --> ${end}\n${word}\n\n"
        }
    }
    File(pathToFileTo).writeText(subs)
    runCommand(listOf("chmod", "666", pathToFileTo))
    return subs
}

fun convertMillisecondsToFrames(
    milliseconds: Long,
    fps: Double = Karaoke.frameFps,
): Long {
    val frameLength = 1000.0 / fps
    return (milliseconds / frameLength).roundToInt().toLong()
}

@Suppress("unused")
fun convertMillisecondsToFramesDouble(
    milliseconds: Long,
    fps: Double = Karaoke.frameFps,
): Double {
    val frameLength = 1000.0 / fps
    return milliseconds / frameLength
}

fun convertFramesToMilliseconds(
    frames: Long,
    fps: Double = Karaoke.frameFps,
): Long {
    val frameLength = 1000.0 / fps
    return (frames * frameLength).roundToInt().toLong()
}

fun millisecondsToTimeFormatted(milliseconds: Long): String {
    val date = Date(milliseconds)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return formatter.format(date)
}

fun convertMillisecondsToTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds - hours * 1000 * 60 * 60) / (1000 * 60)
    val seconds = (milliseconds - hours * 1000 * 60 * 60 - minutes * 1000 * 60) / 1000
    val ms = milliseconds - hours * 1000 * 60 * 60 - minutes * 1000 * 60 - seconds * 1000
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, ms)
}

fun convertMillisecondsToDzenTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds - hours * 1000 * 60 * 60) / (1000 * 60)
    val seconds = (milliseconds - hours * 1000 * 60 * 60 - minutes * 1000 * 60) / 1000
//    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return "%01d:%02d:%02d".format(hours, minutes, seconds)
}

fun convertMillisecondsToDtoTimecode(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds - hours * 1000 * 60 * 60) / (1000 * 60)
    val seconds = (milliseconds - hours * 1000 * 60 * 60 - minutes * 1000 * 60) / 1000
//    val ms = milliseconds - hours*1000*60*60 - minutes*1000*60 - seconds*1000
    return (if (hours > 0) "$hours:" else "") + "%02d:%02d".format(minutes, seconds)
}

fun convertFramesToTimecode(
    frames: Long,
    fps: Double = Karaoke.frameFps,
): String = convertMillisecondsToTimecode(milliseconds = convertFramesToMilliseconds(frames, fps))

fun convertTimecodeToMilliseconds(timecode: String): Long {
    val hhmmssmm = timecode.split(":")
    val hours = hhmmssmm[0].toLong()
    val minutes = hhmmssmm[1].toLong()
    val ssmm = hhmmssmm[2].replace(",", ".").split(".")
    val seconds = ssmm[0].toLong()
    val milliseconds = ssmm[1].toLong()
    return milliseconds + seconds * 1000 + minutes * 1000 * 60 + hours * 1000 * 60 * 60
}

fun convertTimecodeToFrames(
    timecode: String,
    fps: Double = Karaoke.frameFps,
): Long = convertMillisecondsToFrames(convertTimecodeToMilliseconds(timecode = timecode), fps)

fun getBeatNumberByMilliseconds(
    timeInMilliseconds: Long,
    beatMs: Long,
    firstBeatTimecode: String,
): Long {
    var delayMs = convertTimecodeToMilliseconds(firstBeatTimecode)
    val diff = ((delayMs / (beatMs * 4)) - 1) * (beatMs * 4)
    delayMs -= diff

    val firstBeatMs = delayMs
    // println("Время звучания 1 бита = $beatMs ms")
//    val firstBeatMs = convertTimecodeToMilliseconds(firstBeatTimecode)
    // println("Первый отмеченный бит находится от начала в $firstBeatMs ms")
    // println("Время = $timeInMilliseconds ms")
    var timeInMillsCorrected = timeInMilliseconds - firstBeatMs
    // println("Время после сдвигания = $timeInMillsCorrected ms")
    val count4beatsBefore = (timeInMillsCorrected / (beatMs * 4))
    // println("Перед первым временем находится как минимум $count4beatsBefore тактов по 4 бита")
    val different = count4beatsBefore * (beatMs * 4)
    // println("Надо сдвинуть время на $different ms")
    timeInMillsCorrected -= different
    // println("После сдвига время находится от начала в $timeInMillsCorrected ms и это должно быть меньше, чем ${(beatMs * 4).toLong()} ms")
    // println("Результат = $result")
    return ((timeInMillsCorrected / (beatMs)) % 4) + 1
}

@Suppress("unused")
fun getBeatNumberByTimecode(
    timeInTimecode: String,
    beatMs: Long,
    firstBeatTimecode: String,
): Long = getBeatNumberByMilliseconds(convertTimecodeToMilliseconds(timeInTimecode), beatMs, firstBeatTimecode)

fun getDurationInMilliseconds(
    start: String,
    end: String,
): Long = convertTimecodeToMilliseconds(end) - convertTimecodeToMilliseconds(start)

@Suppress("unused")
fun getDiffInMilliseconds(
    firstTimecode: String,
    secondTimecode: String,
): Long = convertTimecodeToMilliseconds(firstTimecode) - convertTimecodeToMilliseconds(secondTimecode)

@Suppress("unused")
fun getSymbolWidth(fontSizePt: Int): Double {
    // Получение ширины символа (в пикселях) для размера шрифта (в пунктах)
    return fontSizePt * 0.6
}

@Suppress("unused")
fun getFontSizeBySymbolWidth(symbolWidthPx: Double): Int {
    // Получение размера шрифта (в пунктах) для ширины символа (в пикселах)
    return (symbolWidthPx / 0.6).toInt()
}

@Suppress("unused")
fun replaceVowelOrConsonantLetters(
    str: String,
    isVowel: Boolean = true,
    replSymbol: String = " ",
): String {
    var result = ""
    str.forEach { symbol ->
        result += if ((symbol in LETTERS_VOWEL) == isVowel) replSymbol else symbol
    }
    return result
}

fun getSyllables(text: String): List<String> {
    val result: MutableList<String> = mutableListOf()
    val regexWords = """\S+""".toRegex(setOf(RegexOption.IGNORE_CASE))
    val words = regexWords.find(text)?.groupValues ?: emptyList()

    val regexSyllables =
        """[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*[ЁУЕЫАОЭЯИЮEUIOAїієѣ][ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*?(?=[ЦКНГШЩЗХФВПРЛДЖЧСМТБQWRTYPSDFGHJKLZXCVBNM-]?[ЁУЕЫАОЭЯИЮEUIOAїієѣ]|[Й|Y][АИУЕОEUIOAїієѣ])"""
            .toRegex(
                setOf(RegexOption.IGNORE_CASE),
            )

    words.forEach { word ->
        val syllables = regexSyllables.replace(word) { m -> "${m.value} " }.split(" ")
        if (syllables.isEmpty()) {
            result.add("${word}_")
        } else {
            syllables.forEachIndexed { j, syllable ->
                result.add("${syllable}${if (j == syllables.size - 1) "_" else ""}")
            }
        }
    }

    var i = 0
    while (i < result.size) {
        val word = result[i]
        if (!word.haveVowel()) {
            if (i == result.size - 1 && (word == "-_" && i != 0)) {
                result[i - 1] = "${result[i - 1]}$word"
                result.removeAt(i)
                i--
            } else if (i < result.size - 2) {
                result[i + 1] = "${word}${result[i + 1]}"
                result.removeAt(i)
                i--
            }
        }
        i++
    }
    return result
}

/**
 * Класс Solution.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
@Suppress("unused")
class Solution {
    fun merge(
        nums1: IntArray,
        m: Int,
        nums2: IntArray,
        n: Int,
    ) {
        val result: MutableList<Int> = mutableListOf()
        result.addAll(nums1.filterIndexed { index, _ -> index < m })
        result.addAll(nums2.filterIndexed { index, _ -> index < n })
        result.sort()
        println(result)
    }
}

// Возвращает "самый длинный элемент", состоящий из слогов самой длинной комбинированной строки всех голосов
fun getLongerElement(
    songVersion: SongVersion,
    listOfVoices: List<SongVoice>,
): SongVoiceLineElement? {
    if (listOfVoices.isEmpty()) return null

    val longerElementLastVoice = listOfVoices.last().longerTextElement(songVersion) ?: return null
    val listLongerElementPreviousVoices =
        listOfVoices
            .filterIndexed {
                index,
                _,
                ->
                index < listOfVoices.size
            }.mapNotNull { it.longerElementPreviousVoice }
    if (listLongerElementPreviousVoices.isEmpty()) {
        return longerElementLastVoice
    } else {
        val syls: MutableList<SongVoiceLineElementSyllable> = mutableListOf()
        var prevSyl: SongVoiceLineElementSyllable? = null
        listLongerElementPreviousVoices.forEach { el ->
            val elGetSylls = el.getSyllables()
            elGetSylls.first().previous = prevSyl
            syls.addAll(elGetSylls)
            prevSyl = elGetSylls.last()
        }
        val elGetSylls = longerElementLastVoice.getSyllables()
        elGetSylls.first().previous = prevSyl
        syls.addAll(elGetSylls)
        val result =
            SongVoiceLineElement(
                rootId = listOfVoices[0].rootId,
                type = longerElementLastVoice.type,
            )
        result.addSyllables(syls)

        return result
    }
}

// Вычисляет максимальный размер шрифта, чтобы все голоса поместились на экране по ширине
fun getFontSize(
    songVersion: SongVersion,
    listOfVoices: List<SongVoice>,
): Int {
    var fontSize = 10
    if (listOfVoices.isEmpty()) return fontSize
    val cntVoices = listOfVoices.size
    // maxTextWidth - максимальная ширина текста = ширина экрана минус 2 отступа
    val maxTextWidthPx = Karaoke.frameWidthPx.toDouble() - Karaoke.songtextStartPositionXpx * 2
    val longerElement = getLongerElement(songVersion, listOfVoices) ?: return fontSize
    // Ширина в пикселах суммарной самой длинной строки
    var maxTextWidthPxByFontSize = longerElement.w() + Karaoke.songtextStartPositionXpx * (cntVoices - 1)
    val stepIncrease = if (maxTextWidthPxByFontSize > maxTextWidthPx) -1 else 1
    while (true) {
        if ((maxTextWidthPxByFontSize > maxTextWidthPx && stepIncrease < 0) ||
            (maxTextWidthPxByFontSize < maxTextWidthPx && stepIncrease > 0)
        ) {
            fontSize += stepIncrease
            longerElement.fontSize = fontSize
            val longerElementW = longerElement.w()
            maxTextWidthPxByFontSize = longerElementW + Karaoke.songtextStartPositionXpx * (cntVoices - 1)
        } else {
            break
        }
    }

//        voices().forEach { voice ->
//            voice.lines.forEach { line ->
//                line.elements.forEach { element ->
//                    element.fontSize = fontSize
//                }
//            }
//        }
    val longerElementLastVoice = listOfVoices.last().longerTextElement(songVersion) ?: return fontSize
    val elGetSylls = longerElementLastVoice.getSyllables()
    elGetSylls.first().previous = null
    return fontSize
}

@Suppress("unused")
fun getAlbumCardTitle(authorYmId: String): String =
    runBlocking {
        val searchUrl = "https://music.yandex.ru/artist/$authorYmId/albums"
        var result = ""

        try {
            // Создание HttpClient
            val client = HttpClient.newBuilder().build()

            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(searchUrl))
                    .GET()
                    .build()
            val response =
                withContext(Dispatchers.IO) {
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                }

            println(response.body())

            // Получение HTML-контента страницы
            val htmlContent = response.body() // EntityUtils.toString(response.entity)

            // Парсинг HTML с помощью Jsoup
            val doc: Document = Jsoup.parse(htmlContent)

            // Находим первый элемент <a>, у которого один из классов начинается с "AlbumCard_titleLink"
            val element = doc.selectFirst("a[class*=AlbumCard_titleLink]")

            if (element !== null) {
                result = element.text().trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        result
    }

fun String.extractBalancedBracesFromString(startWord: String): String {
    val result = "" // Строка для возврата в случае ошибки
    val currentContent = StringBuilder()
    val firstIndexOfStartWord = this.indexOf(startWord)
    if (firstIndexOfStartWord < 0) return result // startWord не найден

    val indexToStartSearch = firstIndexOfStartWord + startWord.length
    if (indexToStartSearch >= this.length) return result // Проверяем, не выходим ли за границы

    val firstChar = this[indexToStartSearch]
    // Проверяем, начинается ли сразу после startWord с '{'
    if (firstChar != '{') return result // Если нет, возвращаем пустую строку

    var counter = 0
    for (i in indexToStartSearch until this.length) {
        val currentSymbol = this[i]
        when (currentSymbol) {
            '{' -> {
                currentContent.append(currentSymbol)
                counter++
            }
            '}' -> {
                currentContent.append(currentSymbol)
                counter--
            }
            else -> {
                currentContent.append(currentSymbol)
            }
        }
        // Завершаем, только когда счётчик достигает 0 (сбалансированная пара)
        if (counter == 0) return currentContent.toString()
        // Не нужно проверять counter < 0 здесь, если логика выше верна,
        // но можно добавить для отладки или если строка может быть заведомо некорректной.
        // if (counter < 0) break // Прервать, если закрывающих скобок больше
    }
    // Если цикл завершился, и counter != 0, значит, скобки несбалансированы
    return result
}

fun String.textBetween(
    startString: String,
    endString: String,
): String {
    val result = ""
    val firstIndexOfStartString = this.indexOf(startString)
    if (firstIndexOfStartString < 0) return result
    val stringToSearch = this.substring(firstIndexOfStartString + startString.length)
    val lastIndexOfStartString = stringToSearch.indexOf(endString)
    if (lastIndexOfStartString < 0) return result
    return stringToSearch.substring(0, lastIndexOfStartString)
}

/**
 * Аналог [extractBalancedBracesFromString], но для массива `[...]` объектов `{...}`:
 * находит `[` сразу после [startWord], затем последовательно вырезает каждый сбалансированный
 * по фигурным скобкам объект внутри массива (а не только первый, как делает
 * [extractBalancedBracesFromString] будучи применён к массиву). Используется для перебора ВСЕХ
 * альбомов автора со страницы Яндекс.Музыки (не только последнего/первого).
 */
fun String.extractAllBalancedBraceObjects(startWord: String): List<String> {
    val result: MutableList<String> = mutableListOf()
    val firstIndexOfStartWord = this.indexOf(startWord)
    if (firstIndexOfStartWord < 0) return result

    val indexToStartSearch = firstIndexOfStartWord + startWord.length
    if (indexToStartSearch >= this.length) return result
    if (this[indexToStartSearch] != '[') return result

    var i = indexToStartSearch + 1
    while (i < this.length) {
        while (i < this.length && (this[i] == ',' || this[i].isWhitespace())) i++
        if (i >= this.length || this[i] == ']') break
        if (this[i] != '{') break // неожиданный токен — прекращаем, не пытаемся угадать дальше

        val currentObject = StringBuilder()
        var counter = 0
        var j = i
        while (j < this.length) {
            val currentSymbol = this[j]
            currentObject.append(currentSymbol)
            when (currentSymbol) {
                '{' -> counter++
                '}' -> counter--
            }
            j++
            if (counter == 0) break
        }
        if (counter != 0) break // скобки не сбалансировались до конца строки

        result.add(currentObject.toString())
        i = j
    }
    return result
}

fun searchLastAlbumVk(vkId: String): String {
    var result = ""
    val authorUrl = "https://vk.ru/artist/$vkId"
    val searchUrl = "https://vk.ru/artist/$vkId/albums"
    Playwright.create().use { playwright ->

        val browser =
            playwright.chromium().launch(
                BrowserType
                    .LaunchOptions()
                    .setHeadless(false),
            )

        // Создаем контекст с дополнительными заголовками и сохраненным состоянием
        val context =
            browser.newContext(
                Browser
                    .NewContextOptions()
//                .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH))
                    .setExtraHTTPHeaders(
                        mapOf(
                            "Referer" to authorUrl,
                            "User-Agent" to
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                            "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                            "Accept-Encoding" to "gzip, deflate, br",
                            "Connection" to "keep-alive",
                            "Upgrade-Insecure-Requests" to "1",
                            "Sec-Fetch-Dest" to "document",
                            "Sec-Fetch-Mode" to "navigate",
                            "Sec-Fetch-Site" to "same-origin",
                        ),
                    ),
            )

        val page = context.newPage()
        page.navigate(searchUrl)

        Thread.sleep(50000)

        page.waitForLoadState()

        val html = page.content()

        println(html)

        val preloadedAlbums = html.extractBalancedBracesFromString("""\"preloadedAlbums\":""")
        val album = preloadedAlbums.extractBalancedBracesFromString("""\"albums\":[""")
        result = album.textBetween("""\"title\":\"""", """\",\"""")

        if (result == "") {
            if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
                println("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
                throw Exception("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
            }
            println("preloadedAlbum = $preloadedAlbums")
            println("album = $album")
            println("searchLastAlbumYm2 html: '${html.substring(0, minOf(html.length, 1000))}...'") // ограничиваем вывод
        }

        // Сохраняем состояние (cookies, localStorage и т.д.) после успешного поиска
//        context.storageState(BrowserContext.StorageStateOptions().setPath(Path.of(YANDEX_AUTH_STATE_PATH)))
//        browser.close()
        Thread.sleep(10000)
    }
    return result
}

fun searchLastAlbumYm2(authorYmId: String): String {
    var result = ""
    val authorUrl = "https://music.yandex.ru/artist/$authorYmId"
    val searchUrl = "$authorUrl/albums"
    Playwright.create().use { playwright ->

        val browser =
            playwright.chromium().launch(
                BrowserType
                    .LaunchOptions()
                    .setHeadless(true),
            )

        // Создаем контекст с дополнительными заголовками и сохраненным состоянием
        val context =
            browser.newContext(
                Browser
                    .NewContextOptions()
                    .setStorageStatePath(Path.of(YANDEX_AUTH_STATE_PATH))
                    .setExtraHTTPHeaders(
                        mapOf(
                            "Referer" to authorUrl,
                            "User-Agent" to
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                            "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                            "Accept-Encoding" to "gzip, deflate, br",
                            "Connection" to "keep-alive",
                            "Upgrade-Insecure-Requests" to "1",
                            "Sec-Fetch-Dest" to "document",
                            "Sec-Fetch-Mode" to "navigate",
                            "Sec-Fetch-Site" to "same-origin",
                        ),
                    ),
            )

        val page = context.newPage()
        page.navigate(searchUrl)

        val html = page.content()

        val preloadedAlbums = html.extractBalancedBracesFromString("""\"preloadedAlbums\":""")
        val album = preloadedAlbums.extractBalancedBracesFromString("""\"albums\":[""")
        result = album.textBetween("""\"title\":\"""", """\",\"""")

        if (result == "") {
            if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
                println("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
                throw Exception("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
            }
            println("preloadedAlbum = $preloadedAlbums")
            println("album = $album")
            println("searchLastAlbumYm2 html: '${html.substring(0, minOf(html.length, 1000))}...'") // ограничиваем вывод
        }

        // Сохраняем состояние (cookies, localStorage и т.д.) после успешного поиска
        context.storageState(BrowserContext.StorageStateOptions().setPath(Path.of(YANDEX_AUTH_STATE_PATH)))
        browser.close()
    }
    return result
}

/**
 * Класс Album Search Result.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
sealed class AlbumSearchResult {
    data class Success(
        val albumTitle: String,
    ) : AlbumSearchResult()

    object VpnBlocked : AlbumSearchResult()

    object AuthExpired : AlbumSearchResult()

    object BotDetected : AlbumSearchResult()

    data class Unknown(
        val pageTitle: String,
        val pageUrl: String,
    ) : AlbumSearchResult()
}

fun searchLastAlbumYm3(authorYmId: String): AlbumSearchResult {
    val authorUrl = "https://music.yandex.ru/artist/$authorYmId"
    val searchUrl = "$authorUrl/albums"

    Playwright.create().use { playwright ->
        val context =
            playwright.chromium().launchPersistentContext(
                USER_DATA_DIR,
                BrowserType
                    .LaunchPersistentContextOptions()
                    .setHeadless(true)
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow"),
            )

        val page = context.pages().firstOrNull() ?: context.newPage()
        try {
            page.navigate("https://music.yandex.ru/")
        } finally {
            context.close()
        }
    }

    Playwright.create().use { playwright ->

        val context =
            playwright.chromium().launchPersistentContext(
                USER_DATA_DIR,
                BrowserType
                    .LaunchPersistentContextOptions()
                    .setHeadless(true)
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow"),
            )

        val page = context.newPage()
        try {
            page.navigate(searchUrl)
            val currentUrl = page.url()
            val html = page.content()

            if (currentUrl.contains("passport.yandex") || currentUrl.contains("id.yandex")) {
                return AlbumSearchResult.AuthExpired
            }

            if (html.contains("недоступна в вашем регионе", ignoreCase = true)) {
                return AlbumSearchResult.VpnBlocked
            }

            if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
                return AlbumSearchResult.BotDetected
            }

            val preloadedAlbums = html.extractBalancedBracesFromString("""\"preloadedAlbums\":""")
            val album = preloadedAlbums.extractBalancedBracesFromString("""\"albums\":[""")
            val result = album.textBetween("""\"title\":\"""", """\",\"""")

            return if (result.isNotEmpty()) {
                AlbumSearchResult.Success(result)
            } else {
                AlbumSearchResult.Unknown(page.title(), currentUrl)
            }
        } finally {
            context.close()
        }
    }
}

// fun searchLastAlbumYm2(authorYmId: String): String {
//    val searchUrl = "https://music.yandex.ru/artist/$authorYmId/albums"
//    // Выбор случайного User-Agent
//    val randomUserAgent = USER_AGENTS.random()
//
// //    val document = Jsoup.connect(searchUrl).get()
//    val document = Jsoup.connect(searchUrl)
//        .header("User-Agent", randomUserAgent)
//        .header("Referer", "https://music.yandex.ru/ ")
//        .get()
//
//    val html = document.html()
//
//    val preloadedAlbums = html.extractBalancedBracesFromString("""\"preloadedAlbums\":""")
//    val album = preloadedAlbums.extractBalancedBracesFromString("""\"albums\":[""")
//    val result = album.textBetween("""\"title\":\"""", """\",\"""")
//    if (result == "") {
//        if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
//            println("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
//            throw Exception("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")
//        }
//        println("preloadedAlbum = $preloadedAlbums")
//        println("album = $album")
//        println("searchLastAlbumYm2 html: '$html'")
//    }
//    return result
// }

fun getAuthorForRequest(lastAuthor: String = ""): Author? {
    val listSongAuthors = Song.loadListAuthors(database = WORKING_DATABASE)
    if (listSongAuthors.isEmpty()) return null
    val requestNewSongLastSuccessAuthor = if (lastAuthor != "") lastAuthor else Karaoke.requestNewSongLastSuccessAuthor

    val authorForRequest =
        if (requestNewSongLastSuccessAuthor == "") {
            listSongAuthors.first()
        } else {
            var result = ""
            listSongAuthors.forEachIndexed { indexAuthor, author ->
                if (author == requestNewSongLastSuccessAuthor) {
                    result =
                        if (indexAuthor < listSongAuthors.size - 1) {
                            listSongAuthors[indexAuthor + 1]
                        } else {
                            listSongAuthors[0]
                        }
                    return@forEachIndexed
                }
            }
            if (result == "") result = listSongAuthors.first()
            result
        }
    var author =
        Author.getAuthorByName(
            author = authorForRequest,
            database = WORKING_DATABASE,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        )

    if (author == null) {
        val newAuthor = Author()
        newAuthor.author = authorForRequest
        Author.createNewAuthor(newAuthor = newAuthor, database = WORKING_DATABASE)
        author = newAuthor
        println("Автор «$authorForRequest» отсутствует в таблице tbl_authors. Создаём запись.")
    }

    if (author.watched && author.ymId !== "" && author.lastAlbumYm == author.lastAlbumProcessed) {
        return author
    } else {
        println("Поиск для автора «$authorForRequest» не нужен, ищем другого автора...")
        return getAuthorForRequest(authorForRequest)
    }
}

fun isVpnActive(): Boolean {
    // Сравниваем текущую страну с настройкой vpnHomeCountry (по умолчанию "RU").
    // Для сервера в Германии установить vpnHomeCountry = "DE" через интерфейс настроек.
    // api.country.is работает из Docker-контейнеров без ограничений.
    val homeCountry = Karaoke.vpnHomeCountry.trim().uppercase()
    val services =
        listOf(
            "https://api.country.is/" to Regex(""""country"\s*:\s*"([A-Z]{2})""""),
            "https://ipapi.co/country/" to Regex("""^([A-Z]{2})$"""),
        )
    for ((url, regex) in services) {
        val body =
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            } catch (e: Exception) {
                println("isVpnActive: исключение при запросе $url: ${e.message}")
                continue
            }
        val country = regex.find(body)?.groupValues?.getOrElse(1) { "" } ?: ""
        if (country.isNotEmpty()) {
            val isVpn = country != homeCountry
            println("isVpnActive: countryCode=$country (homeCountry=$homeCountry) → ВПН ${if (isVpn) "включён" else "выключен"} (via $url)")
            return isVpn
        }
    }
    println("isVpnActive: не удалось определить страну, пропускаем проверку ВПН")
    return false
}

fun checkLastAlbumYm(): Triple<String, String, Int> {
    /*
    -3 - ошибка конфигурации (ВПН или авторизация) — не увеличивать таймаут, не менять автора
    -2 - Нет автора!
    -1 - ошибка поиска (бот-детект и т.п.) — увеличивать таймаут
     0 - поиск успешен, но новых альбомов нет
     1 - поиск успешен, найден новый альбом
     */
    val author = getAuthorForRequest() ?: return Triple("", "", -2)
    val authorForRequest = author.author

    return when (val searchResult = searchLastAlbumYm3(author.ymId)) {
        is AlbumSearchResult.VpnBlocked -> {
            println("Поиск нового альбома автора «$authorForRequest» завершился неудачей из-за включенного ВПН. Отключите ВПН.")
            Triple(authorForRequest, "", -3)
        }
        is AlbumSearchResult.AuthExpired -> {
            println(
                "Поиск нового альбома автора «$authorForRequest» завершился неудачей из-за просроченной авторизации. Переавторизуйтесь.",
            )
            Triple(authorForRequest, "", -3)
        }
        is AlbumSearchResult.BotDetected -> {
            println("Поиск нового альбома автора «$authorForRequest» завершился неудачей: Яндекс заблокировал автоматические запросы.")
            Triple(authorForRequest, "", -1)
        }
        is AlbumSearchResult.Unknown -> {
            val country =
                try {
                    java.net
                        .URL("https://ip-api.com/line/?fields=countryCode")
                        .readText(Charsets.UTF_8)
                        .trim()
                } catch (_: Exception) {
                    ""
                }
            if (country.isNotEmpty() && country != "RU") {
                println(
                    "Поиск нового альбома автора «$authorForRequest» завершился неудачей из-за включенного ВПН (IP-регион: $country). Отключите ВПН.",
                )
                Triple(authorForRequest, "", -1)
            } else {
                println(
                    "Поиск нового альбома автора «$authorForRequest» выдал пустой результат. Возможно Yandex.Музыка изменила код страницы. [Заголовок: '${searchResult.pageTitle}', URL: '${searchResult.pageUrl}']",
                )
                Triple(authorForRequest, "", 0)
            }
        }
        is AlbumSearchResult.Success -> {
            author.lastAlbumYm = searchResult.albumTitle
            author.save()
            if (searchResult.albumTitle == author.lastAlbumProcessed) {
                println(
                    "Поиск для автора «$authorForRequest» завершился успешно, но новых альбомов не найдено. (Альбом «${searchResult.albumTitle}» уже был ранее найден.)",
                )
                Triple(authorForRequest, searchResult.albumTitle, 0)
            } else {
                println(
                    "Поиск для автора «$authorForRequest» завершился успешно, найден новый альбом «${searchResult.albumTitle}». (Ранее последним альбомом был «${author.lastAlbumProcessed}».)",
                )
                Triple(authorForRequest, searchResult.albumTitle, 1)
            }
        }
    }
}

fun setProcessPriority(
    pid: Long,
    priority: Int,
): Boolean {
    try {
        // Используем команду renice для изменения приоритета процесса
        val reniceCommand = listOf("renice", "-n", priority.toString(), "-p", pid.toString())
        val processBuilder = ProcessBuilder(reniceCommand)
        val process = processBuilder.start()

        // Проверяем результат выполнения команды
        val exitCode = process.waitFor()
        return exitCode == 0
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}

fun hostCpuCoreCount(): Int = Runtime.getRuntime().availableProcessors()

// Флаг --cpus для docker run/docker compose run: percent — доля от суммарной мощности хоста (0-100).
// docker трактует --cpus 0 как "без ограничения" (не "ноль"), поэтому значение никогда не форматируется в 0.
fun dockerCpusFlag(percent: Long): List<String> {
    if (!Karaoke.resourceLimitsEnabled || percent <= 0) return emptyList()
    val cpus = (hostCpuCoreCount() * percent / 100.0).coerceAtLeast(0.05)
    return listOf("--cpus", String.format(Locale.ROOT, "%.2f", cpus))
}

// Значение для деплой-лимита CPU в docker-compose.yaml (deploy.resources.limits.cpus: "${VAR:-0}"),
// а не argv-флаг: "docker compose run" не поддерживает --cpus вообще (падает "unknown flag: --cpus"),
// но применяет deploy.resources.limits.cpus даже без Swarm. "0" — значение docker'а для "без ограничения".
fun dockerCpusEnvValue(percent: Long): String {
    if (!Karaoke.resourceLimitsEnabled || percent <= 0) return "0"
    val cpus = (hostCpuCoreCount() * percent / 100.0).coerceAtLeast(0.05)
    return String.format(Locale.ROOT, "%.2f", cpus)
}

// Префикс cpulimit для голых (не докеризованных) процессов (ffmpeg, sheetsage.sh). cpulimit -l — это
// процент ОДНОГО ядра, поэтому для той же семантики "percent от всего хоста", что и dockerCpusFlag,
// значение умножается на число ядер.
// percent <= 0 означает "тип не лимитируется" (cpuLimitPercentForType.else) — обёртку не накладываем,
// иначе layer 2 (refreshArgvCpuLimit) заворачивал бы в cpulimit даже те ffmpeg-шаги, которые layer 1
// (createProcess) намеренно оставляет без лимита (FF_MP3_ACCOMPANIMENT/VOCAL/BASS/DRUMS и т.п.).
// Флаги под установленный cpulimit 2.4 (пакет 2.7-2):
// - -m/--monitor-forks — следить за дочерними процессами (аналог прежнего -i/--include-children, которого
//   в этой версии НЕТ; передача несуществующего -i заставляла cpulimit печатать help и НЕ запускать
//   программу вовсе — задание завершалось за доли секунды без результата).
// - -f/--foreground — запустить программу в foreground и ДОЖДАТЬСЯ её завершения. Без -f cpulimit в
//   режиме "-- PROGRAM" возвращает управление сразу, ffmpeg остаётся осиротевшим/убитым, и на выходе
//   получается обрезанный/пустой файл. stdout программы при -f прокидывается наружу — парсинг прогресса
//   (time=/percentage) в KaraokeProcessWorker продолжает работать.
fun cpulimitPrefix(percent: Long): List<String> {
    if (!Karaoke.resourceLimitsEnabled || percent <= 0) return emptyList()
    val value = (hostCpuCoreCount() * percent).coerceAtLeast(1)
    return listOf("cpulimit", "-l", value.toString(), "-m", "-f", "--")
}

fun cpuLimitPercentForType(type: KaraokeProcessTypes): Long =
    when (type) {
        KaraokeProcessTypes.MELT_LYRICS -> Karaoke.cpuLimitPercentMeltLyrics
        KaraokeProcessTypes.MELT_KARAOKE -> Karaoke.cpuLimitPercentMeltKaraoke
        KaraokeProcessTypes.MELT_CHORDS -> Karaoke.cpuLimitPercentMeltChords
        KaraokeProcessTypes.MELT_TABS -> Karaoke.cpuLimitPercentMeltTabs
        KaraokeProcessTypes.DEMUCS2 -> Karaoke.cpuLimitPercentDemucs2
        KaraokeProcessTypes.DEMUCS5 -> Karaoke.cpuLimitPercentDemucs5
        // Тот же демукс, что и DEMUCS2/DEMUCS5 (свой thread-лейн, но тот же docker-образ/GPU) —
        // переиспользуем существующие лимиты, не заводим отдельные свойства.
        KaraokeProcessTypes.STEM_JOB_DEMUCS2 -> Karaoke.cpuLimitPercentDemucs2
        KaraokeProcessTypes.STEM_JOB_DEMUCS5 -> Karaoke.cpuLimitPercentDemucs5
        KaraokeProcessTypes.KEY_BPM_FROM_FILE -> Karaoke.cpuLimitPercentKeyBpmFinder
        KaraokeProcessTypes.SHEETSAGE -> Karaoke.cpuLimitPercentSheetsage
        KaraokeProcessTypes.SHEETSAGE2 -> Karaoke.cpuLimitPercentSheetsage2
        KaraokeProcessTypes.FF_720_KAR -> Karaoke.cpuLimitPercentFf720Kar
        KaraokeProcessTypes.FF_720_LYR -> Karaoke.cpuLimitPercentFf720Lyr
        KaraokeProcessTypes.RENDER_MP4_LYRICS,
        KaraokeProcessTypes.RENDER_MP4_KARAOKE,
        KaraokeProcessTypes.RENDER_MP4_DEMO,
        -> Karaoke.cpuLimitPercentRenderMp4
        // 0 = тип НЕ лимитируется по CPU. Раньше здесь было 100L, из-за чего layer 2
        // (refreshArgvCpuLimit) заворачивал в cpulimit любой ffmpeg-шаг нелимитируемого типа
        // (в частности FF_MP3_ACCOMPANIMENT/VOCAL/BASS/DRUMS) — layer 1 (createProcess) их
        // намеренно НЕ оборачивает, поэтому и layer 2 не должен. cpulimitPrefix/dockerCpusFlag/
        // dockerCpusEnvValue трактуют percent<=0 как "без обёртки/без лимита".
        else -> 0L
    }

// Пересобирает CPU-лимит в argv шага задания заново, ПРЯМО ПЕРЕД СТАРТОМ процесса (вызывается из
// KaraokeProcessThread.run()) - а не полагается на значение, запечённое в process_args при постановке в
// очередь createProcess(). Ловушка, из-за которой это понадобилось: задание может простоять в очереди
// WAITING долго, настройки (resourceLimitsEnabled/cpuLimitPercent*) за это время успевают измениться -
// без пересборки на старте задание использует лимит, актуальный на момент СОЗДАНИЯ, а не на момент
// реального запуска. Сначала снимает уже имевшуюся обёртку (если она была - неважно, какая именно версия
// настроек её породила), затем накладывает актуальную:
// - "docker run ... --cpus N ..." (DEMUCS2/DEMUCS5/KEY_BPM_FROM_FILE) - argv-флаг, ищется/заменяется по
//   позиции относительно "--rm" и "--cpus".
// - "cpulimit -l N -i -- ffmpeg/sheetsage.sh ..." (голые шаги SHEETSAGE/SHEETSAGE2/FF_720_KAR/FF_720_LYR
//   и встроенный ffmpeg 720p-транскод внутри MELT_LYRICS/MELT_KARAOKE) - argv-обёртка, распознаётся по
//   первому токену исходной (развёрнутой) команды: "ffmpeg" или путь, оканчивающийся на "sheetsage.sh".
// docker-compose шаг MLT (первый токен "docker","compose") и голые filesystem-команды (chmod/mkdir/cp/
// rm/ln/mv) не подходят ни под один из паттернов - возвращаются как есть, не трогаются.
fun refreshArgvCpuLimit(
    type: KaraokeProcessTypes,
    args: List<String>,
): List<String> {
    val stripped: List<String> =
        when {
            args.getOrNull(0) == "cpulimit" -> {
                val ddIdx = args.indexOf("--")
                if (ddIdx != -1) args.subList(ddIdx + 1, args.size).toList() else args
            }
            args.getOrNull(0) == "docker" && args.getOrNull(1) == "run" -> {
                val mutable = args.toMutableList()
                val idx = mutable.indexOf("--cpus")
                if (idx != -1 && idx + 1 < mutable.size) {
                    mutable.removeAt(idx)
                    mutable.removeAt(idx)
                }
                mutable
            }
            else -> args
        }

    val percent = cpuLimitPercentForType(type)
    return when {
        stripped.getOrNull(0) == "docker" && stripped.getOrNull(1) == "run" -> {
            val mutable = stripped.toMutableList()
            val insertAt = mutable.indexOf("--rm").let { if (it == -1) mutable.size else it + 1 }
            mutable.addAll(insertAt, dockerCpusFlag(percent))
            mutable
        }
        stripped.getOrNull(0) == "ffmpeg" || stripped.getOrNull(0)?.endsWith("sheetsage.sh") == true ->
            cpulimitPrefix(percent) + stripped
        else -> stripped
    }
}

// Пара к refreshArgvCpuLimit для env-варианта (MELT_* docker-compose шаг, лимит через переменную
// MLT_CPU_LIMIT - см. dockerCpusEnvValue). Обновляет значение, только если ключ уже присутствовал -
// он есть исключительно у docker-compose шага (остальные split-шаги того же job получают пустые envs при
// createProcess()), поэтому проверка ключа однозначно отличает нужный шаг от прочих без обращения к args.
fun refreshEnvCpuLimit(
    type: KaraokeProcessTypes,
    envs: Map<String, String>,
): Map<String, String> {
    if (!envs.containsKey("MLT_CPU_LIMIT")) return envs
    return envs + ("MLT_CPU_LIMIT" to dockerCpusEnvValue(cpuLimitPercentForType(type)))
}

// Применяет актуальный CPU-лимит к уже ВЫПОЛНЯЮЩИМСЯ докеризованным задачам немедленно, не дожидаясь
// следующего запуска очереди - вызывается из ApiController.setProperty при смене resourceLimitsEnabled
// или любого cpuLimitPercent*. Docker поддерживает live-обновление лимита у уже запущенного контейнера -
// лимит меняется за секунды, без потери прогресса рендера/распознавания.
// Голых (не докеризованных) ffmpeg/sheetsage-задач под cpulimit это не касается - там -l "запечён" в argv
// самого процесса, для смены значения нужен перезапуск, поэтому они по-прежнему подхватывают новый
// процент только со следующего запуска задания.
// Ловушка: "docker update --cpus 0" - это no-op (проверено на Docker 29.6.1: NanoCpus остаётся прежним,
// команда завершается успешно, но лимит не снимается), хотя "0" - обычная семантика docker'а для
// "без ограничения" в НОВЫХ контейнерах (--cpus при docker run/compose). Для снятия лимита с УЖЕ
// запущенного контейнера нужен отдельный флаг "--cpu-quota -1" (сбрасывает cgroup quota/period в
// unlimited) - подтверждено docker stats: нагрузка сразу возвращается к полной.
fun applyLiveCpuLimitToRunningProcesses() {
    val dockerTypes =
        setOf(
            KaraokeProcessTypes.MELT_LYRICS,
            KaraokeProcessTypes.MELT_KARAOKE,
            KaraokeProcessTypes.MELT_CHORDS,
            KaraokeProcessTypes.MELT_TABS,
            KaraokeProcessTypes.DEMUCS2,
            KaraokeProcessTypes.DEMUCS5,
            KaraokeProcessTypes.KEY_BPM_FROM_FILE,
            KaraokeProcessTypes.STEM_JOB_DEMUCS2,
            KaraokeProcessTypes.STEM_JOB_DEMUCS5,
        )
    val workingTypes =
        KaraokeProcess
            .loadList(mapOf("process_status" to KaraokeProcessStatuses.WORKING.name), WORKING_DATABASE)
            .mapNotNull { p -> dockerTypes.find { it.name == p.type } }
            .toSet()

    for (type in workingTypes) {
        val containerRef =
            when (type) {
                KaraokeProcessTypes.MELT_LYRICS, KaraokeProcessTypes.MELT_KARAOKE,
                KaraokeProcessTypes.MELT_CHORDS, KaraokeProcessTypes.MELT_TABS,
                ->
                    runCommand(
                        listOf("docker", "ps", "--filter", "ancestor=svoemestodev/melt:latest", "--format", "{{.ID}}"),
                        ignoreErrors = true,
                    ).lineSequence()
                        .firstOrNull { it.isNotBlank() }
                KaraokeProcessTypes.DEMUCS2, KaraokeProcessTypes.DEMUCS5 -> "demucs"
                KaraokeProcessTypes.KEY_BPM_FROM_FILE ->
                    runCommand(
                        listOf("docker", "ps", "--filter", "ancestor=svoemestodev/keybpmfinder:latest", "--format", "{{.ID}}"),
                        ignoreErrors = true,
                    ).lineSequence()
                        .firstOrNull { it.isNotBlank() }
                KaraokeProcessTypes.STEM_JOB_DEMUCS2, KaraokeProcessTypes.STEM_JOB_DEMUCS5 ->
                    // Per-job имя (stemjob-{id}), а не фиксированное, как у DEMUCS2/DEMUCS5 — ищем по префиксу.
                    runCommand(
                        listOf("docker", "ps", "--filter", "name=^stemjob-", "--format", "{{.ID}}"),
                        ignoreErrors = true,
                    ).lineSequence()
                        .firstOrNull { it.isNotBlank() }
                else -> null
            } ?: continue

        val cpusValue = dockerCpusEnvValue(cpuLimitPercentForType(type))
        val updateArgs =
            if (cpusValue == "0") {
                listOf("docker", "update", "--cpu-quota", "-1", containerRef)
            } else {
                listOf("docker", "update", "--cpus", cpusValue, containerRef)
            }
        try {
            runCommand(updateArgs, ignoreErrors = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Убивает docker-контейнеры выполняющихся сейчас докеризованных заданий — для принудительной остановки
// очереди (KaraokeProcessWorker.forceStop). Зеркало applyLiveCpuLimitToRunningProcesses() по способу
// нахождения контейнера (по образу/фиксированному имени), но вместо "docker update" делает "docker kill".
// MELT-контейнеры запускаются как "docker compose run --rm" → фиксированного имени нет, ищутся по ancestor;
// одновременно может быть несколько (edit-рендеры на разных threadId), поэтому убиваем ВСЕ найденные.
// Убийство контейнера закрывает stdout родительского CLI (docker/docker compose) → поток чтения
// разблокируется; контейнеры "--rm" удаляются сами.
fun killRunningDockerContainers() {
    val dockerTypes =
        setOf(
            KaraokeProcessTypes.MELT_LYRICS,
            KaraokeProcessTypes.MELT_KARAOKE,
            KaraokeProcessTypes.MELT_CHORDS,
            KaraokeProcessTypes.MELT_TABS,
            KaraokeProcessTypes.DEMUCS2,
            KaraokeProcessTypes.DEMUCS5,
            KaraokeProcessTypes.KEY_BPM_FROM_FILE,
            KaraokeProcessTypes.STEM_JOB_DEMUCS2,
            KaraokeProcessTypes.STEM_JOB_DEMUCS5,
        )
    val workingTypes =
        KaraokeProcess
            .loadList(mapOf("process_status" to KaraokeProcessStatuses.WORKING.name), WORKING_DATABASE)
            .mapNotNull { p -> dockerTypes.find { it.name == p.type } }
            .toSet()

    for (type in workingTypes) {
        val containerRefs: List<String> =
            when (type) {
                KaraokeProcessTypes.MELT_LYRICS, KaraokeProcessTypes.MELT_KARAOKE,
                KaraokeProcessTypes.MELT_CHORDS, KaraokeProcessTypes.MELT_TABS,
                ->
                    runCommand(
                        listOf("docker", "ps", "--filter", "ancestor=svoemestodev/melt:latest", "--format", "{{.ID}}"),
                        ignoreErrors = true,
                    ).lineSequence()
                        .filter { it.isNotBlank() }
                        .toList()
                KaraokeProcessTypes.DEMUCS2, KaraokeProcessTypes.DEMUCS5 -> listOf("demucs")
                KaraokeProcessTypes.KEY_BPM_FROM_FILE ->
                    runCommand(
                        listOf("docker", "ps", "--filter", "ancestor=svoemestodev/keybpmfinder:latest", "--format", "{{.ID}}"),
                        ignoreErrors = true,
                    ).lineSequence()
                        .filter { it.isNotBlank() }
                        .toList()
                KaraokeProcessTypes.STEM_JOB_DEMUCS2, KaraokeProcessTypes.STEM_JOB_DEMUCS5 ->
                    // Per-job имя (stemjob-{id}), а не фиксированное, как у DEMUCS2/DEMUCS5 — ищем по префиксу,
                    // может быть >1 (лейн один, но защититься от гонки не помешает).
                    runCommand(
                        listOf("docker", "ps", "--filter", "name=^stemjob-", "--format", "{{.ID}}"),
                        ignoreErrors = true,
                    ).lineSequence()
                        .filter { it.isNotBlank() }
                        .toList()
                else -> emptyList()
            }
        containerRefs.forEach { ref ->
            try {
                println("[${Timestamp.from(Instant.now())}] ProcessWorker: docker kill $ref (тип $type)")
                runCommand(listOf("docker", "kill", ref), ignoreErrors = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun createScriptForHost(
    args: List<String>,
    waitToDone: Boolean = false,
) {
    val txt = args.joinToString(" ")
    val fileName = "/sm-karaoke/system/scriptsFromDocker/${UUID.randomUUID()}.sh"
    val file = File(fileName)
    file.writeText(txt)
    runCommand(listOf("chmod", "777", fileName))
    if (waitToDone) {
        // Ожидание удаления файла с таймаутом
        val timeoutMillis = 120_000 // 120 секунд таймаут
        val startTime = System.currentTimeMillis()
        while (file.exists()) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                throw RuntimeException("Файл $fileName не был удален в течение $timeoutMillis мс, проверьте запущен ли watcher")
            }
            Thread.sleep(100) // Пауза 100 мс между проверками
        }
    }
}

/**
 * Ниже — переиспользуемые реализации для "функциональных" заданий очереди (KaraokeProcessTypes,
 * выполняемых напрямую как Kotlin-функция вместо OS-подпроцесса, args[0] = "runFunctionWithArgs").
 * Параметры (args[2..]) кодируются как "key=value" и парсятся parseRunFunctionWithArgsParams() в
 * Map<String, String> — именованный доступ вместо позиционных индексов, общий для типобезопасной
 * диспетчеризации по KaraokeProcessTypes (KaraokeProcessThread.run()) и строкового диспетчера
 * runFunctionWithArgs() ниже (единственное место без доступа к KaraokeProcess.type — runCommand()).
 * Каждая execute*-функция возвращает false, если запись Song не найдена (вместо прежнего
 * молчаливого "успеха"), чтобы вызывающая сторона могла корректно проставить ERROR.
 */
fun parseRunFunctionWithArgsParams(args: List<String>): Map<String, String> =
    args.drop(2).associate { entry ->
        val idx = entry.indexOf('=')
        if (idx == -1) entry to "" else entry.substring(0, idx) to entry.substring(idx + 1)
    }

fun executeGetKeyBpmFromFile(params: Map<String, String>): Boolean {
    val songId = params["songId"]?.toLongOrNull() ?: return false
    val song =
        Song.loadFromDbById(
            id = songId,
            database = WORKING_DATABASE,
            sync = false,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        )
            ?: return false
    val (key, bpm) = song.getKeyBpmFromFile(reFind = false)
    song.fields[SongField.KEY] = key
    song.fields[SongField.BPM] = bpm.toString()
    song.saveToDb()
    return true
}

/**
 * Фоновый аналог кнопки «Точные маркеры» в SubsEdit (см. SongEditorController.editReconcileText/
 * editForcedAlignMarkers) - но для ВСЕХ голосов песни сразу и с автосохранением результата (не
 * черновик на подтверждение фронта). Whisper-транскрипция вокала не зависит от голоса - одна на
 * всю песню, переиспользуется для согласования текста (вставки) каждого голоса.
 *
 * idStatus проверяется дважды - на постановку в очередь (ApiController.doProcessForcedAlignMarkers/
 * getSongsCreateForcedAlignMarkersAll) и здесь: очередь может ждать своего хода долго, за это время
 * идстатус песни мог измениться руками через UI.
 *
 * Реализует «автоматическую расстановку маркеров» из жизненного цикла статуса готовности
 * (specs/022-song-status-lifecycle) - продвигает idStatus строго на 1 шаг (3 -> 4, FR-011), не
 * трогая его, если текущий статус не 3 (например, орфография/сверка слов ещё не пройдены вручную).
 */
fun executeForcedAlignMarkers(params: Map<String, String>): Boolean {
    val songId = params["songId"]?.toLongOrNull() ?: return false
    val useFinetunedModel = params["useFinetunedModel"]?.toBoolean() ?: false
    val song =
        Song.loadFromDbById(
            id = songId,
            database = WORKING_DATABASE,
            sync = false,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        )
            ?: return false
    if (song.idStatus >= 4) return false

    val vocalsFile = File(song.vocalsNameFlac)
    if (!vocalsFile.exists()) return false

    val transcription = WhisperAsrService.transcribe(vocalsFile) ?: return false
    val words = WhisperAsrService.flatWords(transcription)
    if (words.isEmpty()) return false

    var anyVoiceProcessed = false
    for (voice in song.sourceTextList.indices) {
        val sourceText = song.getSourceText(voice)
        if (sourceText.isBlank()) continue

        val reconciledText = WhisperMarkerAligner.reconcileText(sourceText, words)

        val response = AlignmentServiceClient.align(vocalsFile, reconciledText, useFinetunedModel) ?: continue
        if (!response.ok || response.syllables.isEmpty()) continue

        val syllableTimes = response.syllables.map { (it.startMs / 1000.0) to (it.endMs / 1000.0) }
        val markers = WhisperMarkerAligner.buildMarkersFromSyllableTimes(reconciledText, syllableTimes) ?: continue

        song.setSourceMarkers(voice, markers)
        if (reconciledText != sourceText) song.setSourceText(voice, reconciledText)
        anyVoiceProcessed = true
    }
    if (!anyVoiceProcessed) return false

    if (song.idStatus == 3L) {
        song.fields[SongField.ID_STATUS] = "4"
        song.saveToDb()
    }
    return true
}

/**
 * Финальный шаг обычного пайплайна demucs (см. Song.argsDemucs2/5) — по образцу
 * executeFinalizeStemJob (StemJobProcessing.kt) для премиум-фичи «Создать минусовку»: очередь
 * заданий НЕ прерывает цепочку шагов при ошибке одного из них (см. KaraokeProcess.getProcessesToStart/
 * KaraokeProcessThread.run()), поэтому только финальный шаг может достоверно проверить, что demucs
 * реально создал ожидаемые flac-стемы, а не считать обработку успешной по факту запуска docker.
 *
 * Если стемов не хватает, но исходный file.flac (вход demucs) на месте и это ещё не CPU-повтор (см.
 * retriedOnCpu/argsDemucs2RetryCpu/argsDemucs5RetryCpu) — считаем, что упал именно demucs (обычно
 * нехватка видеопамяти GPU — на админской машине GPU общий с локальной LLM-моделью) и тут же ставим
 * в очередь повтор без GPU вместо немедленного ERROR.
 */
fun executeFinalizeDemucs(params: Map<String, String>): Boolean {
    val songId = params["songId"]?.toLongOrNull() ?: return false
    val demucsType = params["demucsType"] ?: return false
    val threadId = params["threadId"]?.toIntOrNull() ?: KaraokeProcess.THREAD_LANE_HEAVY_RENDER
    val retriedOnCpu = params["retriedOnCpu"]?.toBoolean() ?: false
    val song =
        Song.loadFromDbById(
            id = songId,
            database = WORKING_DATABASE,
            sync = false,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        )
            ?: return false

    val stemFiles =
        if (demucsType == KaraokeProcessTypes.DEMUCS5.name) {
            listOf(song.accompanimentNameFlac, song.vocalsNameFlac, song.drumsNameFlac, song.bassNameFlac, song.otherNameFlac)
        } else {
            listOf(song.accompanimentNameFlac, song.vocalsNameFlac)
        }
    val missingStems = stemFiles.any { !File(it).exists() || File(it).length() == 0L }

    if (missingStems) {
        val tempFlac = File("$PATH_TO_TEMP_DEMUCS_FOLDER/file.flac")
        if (!retriedOnCpu && tempFlac.exists() && tempFlac.length() > 0L) {
            val (retryArgs, retryEnvs) =
                if (demucsType == KaraokeProcessTypes.DEMUCS5.name) {
                    song.argsDemucs5RetryCpu(threadId)
                } else {
                    song.argsDemucs2RetryCpu(threadId)
                }
            val retryProcess = KaraokeProcess(song.database)
            retryProcess.name = "[${song.author}] - [${song.album}] - «${song.songName}»"
            retryProcess.status = KaraokeProcessStatuses.WAITING.name
            retryProcess.priority = 1
            retryProcess.command = ""
            retryProcess.type = demucsType
            retryProcess.songId = song.id.toInt()
            retryProcess.threadId = threadId
            retryProcess.description = "Демукс ${if (demucsType == KaraokeProcessTypes.DEMUCS5.name) "5" else "2"} (повтор без GPU)"
            retryProcess.args = retryArgs
            retryProcess.envs = retryEnvs
            KaraokeProcess.createDbInstance(KaraokeProcess.separate(retryProcess))
            return false
        }

        File(PATH_TO_TEMP_DEMUCS_FOLDER).deleteRecursively()
        return false
    }

    File(PATH_TO_TEMP_DEMUCS_FOLDER).deleteRecursively()
    return true
}

fun executeUploadToLocalStore(
    params: Map<String, String>,
    onProgress: ((Int) -> Unit)? = null,
): Boolean {
    val songId = params["songId"]?.toLongOrNull() ?: return false
    val pathToFile = params["pathToFile"] ?: return false
    val karaokeFileType = params["karaokeFileType"] ?: return false
    val deleteAfterUpload = params["deleteAfterUpload"]?.toBoolean() ?: false
    val fileType = KaraokeFileType.valueOf(karaokeFileType)
    val storageService = KSS_APP
    val song =
        Song.loadFromDbById(
            id = songId,
            database = WORKING_DATABASE,
            sync = false,
            storageService = storageService,
            storageApiClient = SAC_APP,
        )
            ?: return false

    val existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
    // storageFileName/bucketName приходят из HealthReport (точный ключ для типа файла - у картинок
    // альбома/автора он отличается от song.storageFileName). Фолбэк - старая формула для уже
    // стоящих в очереди задач без этих параметров (аудио-стемы MP3_*).
    val storageFileName = params["storageFileName"] ?: "${song.storageFileName}${fileType.suffix}.${fileType.extention}"
    val bucketName = params["bucketName"] ?: song.storageBucketName
    val existsInLocalStorage = storageService.fileExists(bucketName = bucketName, fileName = storageFileName)
    if (existsInLocalFileSystem && !existsInLocalStorage) {
        val file = File(pathToFile)
        val totalSize = file.length()
        val stream =
            if (onProgress != null && totalSize > 0) {
                CountingInputStream(file.inputStream()) { bytesRead -> onProgress(((bytesRead * 100) / totalSize).toInt()) }
            } else {
                file.inputStream()
            }
        storageService.uploadFile(
            bucketName = bucketName,
            fileName = storageFileName,
            file = stream,
            size = totalSize,
        )
        if (deleteAfterUpload) Files.deleteIfExists(file.toPath())
    }
    return true
}

fun executeUploadToRemoteStore(
    params: Map<String, String>,
    onProgress: ((Int) -> Unit)? = null,
): Boolean {
    val songId = params["songId"]?.toLongOrNull() ?: return false
    val pathToFile = params["pathToFile"] ?: return false
    val karaokeFileType = params["karaokeFileType"] ?: return false
    val deleteAfterUpload = params["deleteAfterUpload"]?.toBoolean() ?: false
    val fileType = KaraokeFileType.valueOf(karaokeFileType)
    val storageApiClient = SAC_APP
    val song =
        Song.loadFromDbById(
            id = songId,
            database = WORKING_DATABASE,
            sync = false,
            storageService = KSS_APP,
            storageApiClient = storageApiClient,
        )
            ?: return false

    val existsInLocalFileSystem = if (pathToFile != "") File(pathToFile).exists() else false
    // storageFileName/bucketName приходят из HealthReport (точный ключ для типа файла - у картинок
    // альбома/автора он отличается от song.storageFileName). Фолбэк - старая формула для уже
    // стоящих в очереди задач без этих параметров (аудио-стемы MP3_*).
    val storageFileName = params["storageFileName"] ?: "${song.storageFileName}${fileType.suffix}.${fileType.extention}"
    val bucketName = params["bucketName"] ?: song.storageBucketName
    val existsInRemoteStorage = storageApiClient.fileExists(bucketName = bucketName, fileName = storageFileName)
    if (existsInLocalFileSystem && !existsInRemoteStorage) {
        storageApiClient.uploadFile(
            bucketName = bucketName,
            fileName = storageFileName,
            pathToFileOnDisk = pathToFile,
            onProgress = onProgress,
        )
        if (deleteAfterUpload) Files.deleteIfExists(File(pathToFile).toPath())
    }
    return true
}

fun executeRenderMp4(
    params: Map<String, String>,
    onProgress: ((Int) -> Unit)? = null,
): Boolean {
    val songId = params["songId"]?.toLongOrNull() ?: return false
    val width = params["width"]?.toIntOrNull() ?: 1920
    val height = params["height"]?.toIntOrNull() ?: 1080
    val fps = params["fps"]?.toIntOrNull() ?: 60
    val version =
        try {
            com.svoemesto.karaokeapp.services.RenderVersion
                .valueOf(params["version"] ?: "KARAOKE")
        } catch (_: Exception) {
            com.svoemesto.karaokeapp.services.RenderVersion.KARAOKE
        }
    val song =
        Song.loadFromDbById(
            id = songId,
            database = WORKING_DATABASE,
            sync = false,
            storageService = KSS_APP,
            storageApiClient = SAC_APP,
        )
            ?: return false

    // Для DEMO — границы фрагмента из Song (первый куплет)
    val demoStart = if (version == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) song.demoFragmentStartSeconds else null
    val demoEnd = if (version == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) song.demoFragmentEndSeconds else null
    val demoFadeIn = if (version == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) song.demoFragmentFadeInSeconds else null

    println(
        "[${java.sql.Timestamp.from(
            java.time.Instant.now(),
        )}] executeRenderMp4: старт для id=$songId (${width}x$height@$fps) version=${version.name}" +
            if (demoStart != null && demoEnd != null) " demo=$demoStart..$demoEnd" else "",
    )

    val renderParams =
        com.svoemesto.karaokeapp.services.RenderMp4Params(
            songId = songId,
            width = width,
            height = height,
            fps = fps,
            version = version,
            demoFragmentStart = demoStart,
            demoFragmentEnd = demoEnd,
        )
    val framesResult =
        com.svoemesto.karaokeapp.services.PlayerMp4RenderService.renderFrames(renderParams) { framePercent ->
            onProgress?.invoke((framePercent * 80) / 100)
        }

    val tempOutputPath = "${com.svoemesto.karaokeapp.PATH_TO_TEMP_RENDERMP4_FOLDER}/${songId}_${version.name}/output.mp4"
    val tailSeconds = if (version == com.svoemesto.karaokeapp.services.RenderVersion.DEMO) 10.0 else 1.0
    val totalDurationSec = framesResult.preroll + framesResult.duration + tailSeconds
    com.svoemesto.karaokeapp.services.PlayerMp4MuxService.mixAndMux(
        framesDir = framesResult.framesDir,
        fps = fps,
        preroll = framesResult.preroll,
        audioTracks =
            com.svoemesto.karaokeapp.services.PlayerMp4MuxService
                .tracksForVersion(song, version),
        outputPath = tempOutputPath,
        totalDurationSeconds = totalDurationSec,
        demoFragmentStart = demoStart,
        demoFragmentEnd = demoEnd,
        demoFadeInSeconds = demoFadeIn,
        onProgress = { muxPercent -> onProgress?.invoke(80 + (muxPercent * 20) / 100) },
    )

    // Копируем результат в done_files с именем [version].mp4
    val doneFilesDir = File("${song.rootFolder}/done_files")
    if (!doneFilesDir.exists()) {
        doneFilesDir.mkdirs()
    }
    val destFile = File(song.pathToFileRenderMp4ForVersion(version))
    File(tempOutputPath).copyTo(destFile, overwrite = true)
    runCommand(listOf("chmod", "666", destFile.absolutePath))
    println("[${java.sql.Timestamp.from(java.time.Instant.now())}] executeRenderMp4: скопировано в ${destFile.absolutePath}")

    // Удаляем временную папку (секвенция кадров + промежуточный mp4)
    val tempDir = File("${com.svoemesto.karaokeapp.PATH_TO_TEMP_RENDERMP4_FOLDER}/${songId}_${version.name}")
    tempDir.deleteRecursively()
    println("[${java.sql.Timestamp.from(java.time.Instant.now())}] executeRenderMp4: удалена временная папка ${tempDir.absolutePath}")

    onProgress?.invoke(100)
    println("[${java.sql.Timestamp.from(java.time.Instant.now())}] executeRenderMp4: готово -> ${destFile.absolutePath}")
    return true
}

fun runFunctionWithArgs(args: List<String>): String {
    if (args.size <= 1) return ""
    val func = args[1]
    val params = parseRunFunctionWithArgsParams(args)
    return when (func) {
        "getKeyBpmFromFile" -> if (executeGetKeyBpmFromFile(params)) "Success for '$func'" else ""
        "uploadToLocalStore" -> if (executeUploadToLocalStore(params)) "Success for '$func'" else ""
        "uploadToRemoteStore" -> if (executeUploadToRemoteStore(params)) "Success for '$func'" else ""
        "renderMp4" -> if (executeRenderMp4(params)) "Success for '$func'" else ""
        else -> ""
    }
}

fun runCommand(
    args: List<String>,
    ignoreErrors: Boolean = false,
    skipRunFunctionWithArgs: Boolean = false,
    envs: Map<String, String> = emptyMap(),
): String {
    if (args.isNotEmpty() && args[0] == "runFunctionWithArgs" && !skipRunFunctionWithArgs) {
        return runFunctionWithArgs(args)
    }

    // Создаем ProcessBuilder сформированным списком аргументов
    val processBuilder = ProcessBuilder(args)
    val processBuilderEnvironment = processBuilder.environment()
    processBuilderEnvironment.putAll(envs)

    // Направляем стандартный поток ошибок в стандартный поток вывода для удобства
    processBuilder.redirectErrorStream(true)

    try {
        // Запускаем процесс
        val process = processBuilder.start()

        // Читаем вывод процесса
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val result = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            result.append(line).append("\n")
        }

        // Ждем завершения процесса
        val exitCode = process.waitFor()
        if (exitCode != 0 && !ignoreErrors) {
            throw RuntimeException("Process exited with error code $exitCode")
        }

        // Возвращаем результат, удаляя последний символ новой строки
        return result.toString().trim()
    } catch (e: Exception) {
        throw RuntimeException("Error running runCommand", e)
    }
}

fun getTransposingChord(
    originalChord: String,
    capo: Int = 0,
): String {
    if (capo == 0) return originalChord
    val chordNameAndFret = originalChord.split("|")
    val nameChord = chordNameAndFret[0]
//    val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
    val (chord, note) = MusicChord.getChordNote(nameChord)
    var newIndexNote = MusicNote.entries.indexOf(note!!) - capo
    if (newIndexNote < 0) newIndexNote += MusicNote.entries.size
    val newNote = MusicNote.entries[newIndexNote]
    return newNote.names.first() + chord!!.names.first()
}

/**
 * Проверяет, безопасно ли имя файла (защита от path traversal).
 */
fun isValidFileName(fileName: String): Boolean = !fileName.startsWith("../") && !fileName.startsWith("/") && !fileName.contains("/../")

/**
 * Проверяет, разрешён ли тип файла (опционально).
 */
@Suppress("unused")
fun isAllowedFileType(
    fileName: String,
    allowedTypes: Set<String> = setOf("jpg", "png", "mp3", "wav", "txt", "pdf"),
): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return allowedTypes.contains(extension)
}

fun calculateRelativePathForSymlink(
    targetAbsolutePath: String,
    symlinkAbsolutePath: String,
): String {
    val targetPath: Path = Paths.get(targetAbsolutePath).normalize()
    val symlinkPath: Path = Paths.get(symlinkAbsolutePath).normalize()

    // Убедимся, что оба пути абсолютные
    require(targetPath.isAbsolute) { "targetAbsolutePath must be an absolute path: $targetAbsolutePath" }
    require(symlinkPath.isAbsolute) { "symlinkAbsolutePath must be an absolute path: $symlinkAbsolutePath" }

    val symlinkParentDir: Path = symlinkPath.parent
    // Проверим, что оба пути находятся на одном диске/корне (например, оба на / в Unix или на C:\ в Windows)
    // resolveSibling может не работать корректно, если корни разные
    require(targetPath.root == symlinkParentDir.root) {
        "Target and symlink paths have different roots: ${targetPath.root} vs ${symlinkParentDir.root}"
    }

    // Вычисляем относительный путь от родительской директории ссылки к целевому файлу
    val relativePath: Path = symlinkParentDir.relativize(targetPath)

    return relativePath.toString()
}

fun calculateAbsolutePathFromSymlink(
    relativePath: String,
    symlinkAbsolutePath: String,
): String {
    val relativePathObj: Path = Paths.get(relativePath).normalize()
    val symlinkPath: Path = Paths.get(symlinkAbsolutePath).normalize()

    // Убедимся, что путь к ссылке абсолютный
    require(symlinkPath.isAbsolute) { "symlinkAbsolutePath must be an absolute path: $symlinkAbsolutePath" }

    // Если относительный путь уже абсолютный, возвращаем его как есть (хотя это странно для "относительного" пути)
    if (relativePathObj.isAbsolute) {
        // Или можно бросить исключение, если это недопустимый случай
        // throw IllegalArgumentException("relativePath is already absolute: $relativePath")
        println("Предупреждение: relativePath уже является абсолютным: $relativePath")
        return relativePathObj.toString()
    }

    val symlinkParentDir: Path = symlinkPath.parent // Получаем родительский каталог ссылки
    // Объединяем родительский каталог ссылки с относительным путем цели
    val resolvedTargetAbsolutePath: Path = symlinkParentDir.resolve(relativePathObj).normalize()

    return resolvedTargetAbsolutePath.toString()
}

fun actionToDeleteFileAndFolderIfFolderEmpty(pathToFile: String): () -> Unit =
    {
        println("actionToDeleteFileAndFolderIfFolderEmpty - Удаление файла '$pathToFile' >>>")
        val fileExists = File(pathToFile).exists()
        if (fileExists) {
            val folder = File(pathToFile).parent
            runCommand(args = listOf("rm", "-f", pathToFile))
            val folderExists = File(folder).exists()
            if (folderExists) {
                val folderIsEmpty = Files.list(Path(folder)).findFirst().isEmpty
                if (folderIsEmpty) {
                    Files.deleteIfExists(Path(folder))
                    // Проверка, что пустая папка удалена
                    if (File(folder).exists()) {
                        println("actionToDeleteFileAndFolderIfFolderEmpty - не удалось удалить пустую папку '$folder'")
                    }
                }
            }
            if (File(pathToFile).exists()) {
                println("actionToDeleteFileAndFolderIfFolderEmpty - не удалось удалить файл '$pathToFile'")
            }
        } else {
            println("actionToDeleteFileAndFolderIfFolderEmpty - попытка удалить несуществующий файл '$pathToFile'")
        }
        println("actionToDeleteFileAndFolderIfFolderEmpty - Удаление файла '$pathToFile' <<<")
    }

fun getTempFilePath(
    prefix: String = "temp",
    suffix: String = ".tmp",
): Path {
    // Создаёт файл в стандартной директории для временных файлов
    // с рандомным именем, начинающимся с 'prefix' и заканчивающимся на 'suffix'
    return Files.createTempFile(prefix, suffix)
}

fun findAndFillDublicates(
    author: String,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Int {
    /*
    На вход подаётся список песен. Предполагается, что это песни одного автора. На всякий случай будет это учитывать при фильтрации
    Дла каждой песни со статусом 0 нужно получить её имя без скобок "Имя песни (1992)" - "Имя песни"
    Найти это имя среди готовых песен (idStatus >= 6 — готова к онлайн-плееру, specs/022-song-status-lifecycle)
    и скопировать root_id, поля текста, установить статус в 1
     */
    var result = 0
    val songList =
        Song.loadListFromDb(
            args = mapOf("author" to author),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
            withoutMarkersAndText = false,
        )
    songList.filter { it.idStatus == 0L }.forEach { newSong ->
        val nameToFind = newSong.songName.replace(Regex("""\([^)]*\)"""), "").trim()
        songList.firstOrNull { it.idStatus >= 6L && it.songName.replace(Regex("""\([^)]*\)"""), "").trim() == nameToFind }?.let { findedSong ->
            newSong.rootId = findedSong.id
            newSong.sourceText = findedSong.sourceText
            newSong.resultText = findedSong.resultText
            newSong.sourceMarkers = findedSong.sourceMarkers
            newSong.formattedTextSong = findedSong.formattedTextSong
            newSong.formattedTextTabs = findedSong.formattedTextTabs
            newSong.formattedTextChords = findedSong.formattedTextChords
            newSong.fields[SongField.ID_STATUS] = "1"
            newSong.saveToDb()
            result++
        }
    }
    return result
}

fun normalizeSongNameForSearch(name: String): String {
    /*
    Нормализация названия песни для сравнения/поиска "оригинала":
    - убирается содержимое в скобках (уже была правилом раньше)
    - буквы "ё"/"Ё" приравниваются к "е"
    - знаки препинания убираются полностью (остаются только буквы/цифры/пробелы)
    Punctuation-класс намеренно реализован в Kotlin (\p{L}/\p{Nd}, Unicode-aware), а не через Postgres
    REGEXP_REPLACE с \w - в дефолтной C-локали Postgres \w распознаёт только ASCII-буквы и вырезал бы
    кириллицу вместе со знаками препинания.
     */
    return name
        .replace(Regex("""\([^)]*\)"""), "")
        .lowercase()
        .replace('ё', 'е')
        .replace(Regex("""[^\p{L}\p{Nd}\s]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

fun findDuplicateOriginal(
    newSong: Song,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Song? {
    /**
     * Для новой песни (обычно только что импортированной из папки) ищет "оригинал" - уже существующую в базе
     * песню с тем же названием без учёта содержимого в скобках, знаков препинания и различия "е"/"ё"
     * (регистронезависимо), у которой уже есть текст. Ищет **только** у того же автора; если не найдено —
     * возвращает `null` (никакого fallback'а на других авторов). При нескольких совпадениях берёт запись
     * с наименьшим id.
     *
     * @see specs/238-import-folder-author-album-cover/spec.md (FR-001..FR-003, US1)
     */
    val cleanedName = normalizeSongNameForSearch(newSong.songName)
    if (cleanedName.isBlank()) return null

    fun findId(sameAuthorOnly: Boolean): Long? {
        val connection = database.getConnection() ?: return null
        val sql =
            "SELECT id, song_name FROM tbl_songs" +
                " WHERE id <> ?" +
                (if (sameAuthorOnly) " AND LOWER(song_author) = LOWER(?)" else "") +
                " AND TRIM(source_text) <> ''" +
                " ORDER BY id ASC"
        val ps = connection.prepareStatement(sql)
        var idx = 1
        ps.setLong(idx++, newSong.id)
        if (sameAuthorOnly) ps.setString(idx, newSong.author)
        val rs = ps.executeQuery()
        var foundId: Long? = null
        while (rs.next()) {
            if (normalizeSongNameForSearch(rs.getString("song_name")) == cleanedName) {
                foundId = rs.getLong("id")
                break
            }
        }
        rs.close()
        ps.close()
        return foundId
    }

    val id = findId(sameAuthorOnly = true) ?: return null
    return Song.loadFromDbById(id = id, database = database, storageService = storageService, storageApiClient = storageApiClient)
}

private data class ParentCandidate(
    val id: Long,
    val author: String,
    val hasText: Boolean
)

/**
 * Подбор кандидата в "родители" для пакетного повторного поиска (см. customFunction), в отличие
 * от findDuplicateOriginal ищет среди ВСЕХ песен с точным совпадением нормализованного названия
 * (normalizeSongNameForSearch), не только среди тех, у кого уже есть текст. При нескольких
 * совпадениях выбор идёт по цепочке приоритетов: сначала кандидаты с непустым source_text (если
 * такие есть), затем внутри этого пула - того же автора (регистронезависимо), затем - с
 * наименьшим id.
 */
fun findParentCandidateId(
    song: Song,
    database: KaraokeConnection,
): Long? {
    val cleanedName = normalizeSongNameForSearch(song.songName)
    if (cleanedName.isBlank()) return null
    val connection = database.getConnection() ?: return null
    val ps =
        connection.prepareStatement(
            "SELECT id, song_name, song_author, source_text FROM tbl_songs WHERE id <> ?",
        )
    ps.setLong(1, song.id)
    val rs = ps.executeQuery()
    val candidates = mutableListOf<ParentCandidate>()
    while (rs.next()) {
        if (normalizeSongNameForSearch(rs.getString("song_name")) == cleanedName) {
            candidates.add(
                ParentCandidate(
                    id = rs.getLong("id"),
                    author = rs.getString("song_author") ?: "",
                    hasText = !rs.getString("source_text").isNullOrBlank(),
                ),
            )
        }
    }
    rs.close()
    ps.close()
    if (candidates.isEmpty()) return null

    val withText = candidates.filter { it.hasText }
    val pool = withText.ifEmpty { candidates }
    val sameAuthor = pool.filter { it.author.equals(song.author, ignoreCase = true) }
    val finalPool = sameAuthor.ifEmpty { pool }
    return finalPool.minByOrNull { it.id }?.id
}

fun searchSongsByNormalizedName(
    currentSong: Song,
    searchQuery: String,
    database: KaraokeConnection,
): List<Long> {
    /*
    Ручной поиск кандидатов в "оригинал" по (части) названия - подстрока нормализованного имени песни
    (см. normalizeSongNameForSearch) содержит нормализованный поисковый запрос. В отличие от
    findDuplicateOriginal (точное совпадение), здесь - вхождение подстроки, чтобы можно было найти песню
    по неполному названию. Ищет среди всех авторов, только среди песен с непустым исходным текстом.
     */
    val normalizedQuery = normalizeSongNameForSearch(searchQuery)
    if (normalizedQuery.isBlank() || normalizedQuery.length < 4) return emptyList()
    val connection = database.getConnection() ?: return emptyList()
    val sql = "SELECT id, song_name FROM tbl_songs WHERE id <> ? AND TRIM(source_text) <> ''"
    val ps = connection.prepareStatement(sql)
    ps.setLong(1, currentSong.id)
    val rs = ps.executeQuery()
    val ids = mutableListOf<Long>()
    while (rs.next()) {
        if (normalizeSongNameForSearch(rs.getString("song_name")).contains(normalizedQuery)) {
            ids.add(rs.getLong("id"))
        }
    }
    rs.close()
    ps.close()
    return ids
}

/**
 * Применяет данные «родителя» ([original], найденного через [findDuplicateOriginal]) к новой песне
 * ([newSong]): копирует [Song.rootId], [Song.sourceText], [Song.resultText], [Song.sourceMarkers],
 * [Song.formattedTextSong], [Song.formattedTextTabs], [Song.formattedTextChords] и выставляет
 * `id_status = 1` (TEXT_CREATE). Сохранение — через [Song.saveToDb].
 *
 * **Защита от race condition (спека 278)**: между [Song.createFromPath] и этим `saveToDb()` может
 * пройти время (поиск дубликата через сравнение имён), за которое параллельный процесс
 * (`KEY_BPM_FROM_FILE`) успевает обновить `song_tone`/`song_bpm` через свой экземпляр
 * `Song.saveToDb()`. Перезагружаем объект из БД в [songToSave], чтобы `getDiff()` НЕ включил
 * эти поля в UPDATE (иначе — перезатирание пустыми значениями из stale in-memory объекта).
 *
 * **Защита от расхождения память↔БД (спека 279)**: после `songToSave.saveToDb()` в БД записываются
 * новые значения (включая `root_id = original.id`), но `newSong` в памяти остался бы со старым
 * `rootId = 0`. Следующий шаг `doCreateFromFolder` ([findAudioParentByWaveform]) вызывает
 * `newSong.saveToDb()` (см. `Utils.kt:4879/4898/4919/4933`), внутри которого `savedSong = loadFromDbById(id)`
 * возвращает объект с актуальным `root_id = original.id` из БД. `getDiff(this, savedSong)` видит
 * `this.rootId = 0` (в памяти) ≠ `savedSong.rootId = original.id` (из БД) → diff включает
 * `root_id = 0` → UPDATE перезатирает только что записанный `root_id` обратно в 0. Регресс после
 * спеки 278: до неё присваивание шло напрямую в `newSong` (newSong.rootId = original.id), и
 * память с БД были согласованы сразу. Без явной синхронизации ниже `findAudioParentByWaveform`
 * перезаписывал `root_id` обратно в 0, и `findYandexSongLyrics` НЕ запускался (т.к. `textResolved`
 * уже `true` от этой функции).
 *
 * @see specs/279-fix-parent-search-folder-add/spec.md
 * @see specs/278-fix-key-loss-on-lyrics-search
 */
fun applyDuplicateOriginal(
    newSong: Song,
    original: Song,
) {
    // specs/278-fix-key-loss-on-lyrics-search: reload-from-db-before-save (см. KDoc).
    val songToSave =
        Song.loadFromDbById(
            id = newSong.id,
            database = newSong.database,
            storageService = newSong.storageService,
            storageApiClient = newSong.storageApiClient,
        ) ?: newSong
    songToSave.rootId = original.id
    songToSave.sourceText = original.sourceText
    songToSave.resultText = original.resultText
    songToSave.sourceMarkers = original.sourceMarkers
    songToSave.formattedTextSong = original.formattedTextSong
    songToSave.formattedTextTabs = original.formattedTextTabs
    songToSave.formattedTextChords = original.formattedTextChords
    songToSave.fields[SongField.ID_STATUS] = "1"
    songToSave.saveToDb()

    // specs/279-fix-parent-search-folder-add: синхронизировать newSong в памяти с только что
    // записанным состоянием. Без этого следующий шаг doCreateFromFolder (findAudioParentByWaveform
    // → song.saveToDb в Utils.kt:4879/4898/4919/4933) увидит this.rootId=0 (в памяти) ≠
    // savedSong.rootId=original.id (из БД) → diff включит root_id=0 → UPDATE перезатрёт только что
    // записанный root_id обратно в 0.
    newSong.rootId = original.id
    newSong.sourceText = original.sourceText
    newSong.resultText = original.resultText
    newSong.sourceMarkers = original.sourceMarkers
    newSong.formattedTextSong = original.formattedTextSong
    newSong.formattedTextTabs = original.formattedTextTabs
    newSong.formattedTextChords = original.formattedTextChords
    newSong.fields[SongField.ID_STATUS] = "1"
}

/**
 * Применяет текст/маркеры аудио-родителя (см. findAudioParentByWaveform) к только что импортированной
 * песне - вызывается, только если аудио-родитель найден и уже полностью "готов" (idStatus >= 6, т.е.
 * прошёл весь жизненный цикл проверки текста/маркеров - specs/022-song-status-lifecycle). Маркеры
 * сдвигаются под таймлайн текущей песни тем же способом, что и в applyFamilySongSelection
 * (shiftMarkersAndFixEnd), но, в отличие от неё, root_id не трогается - audio_parent_id уже отдельно
 * связывает пару (см. findAudioParentByWaveform), а статус выставляется в 5 (MARKERS_CHECK) безусловно,
 * независимо от текущего статуса песни: копирование уже полностью проверенного контента от
 * аудио-подтверждённого родителя (сходство >= AUDIO_PARENT_THRESHOLD, т.е. >= 95%) - осознанная
 * остановка на предфинальной вычитке куратором перед публикацией, а не автоматический переход в READY:
 * акустическая сверка не гарантирует идеального совпадения таймлайнов, поэтому статус 6 (READY)
 * проставляется только после ручного подтверждения куратора (FR-003 spec.md).
 */
fun applyAudioParentMarkers(
    song: Song,
    audioParent: Song,
    deltaMs: Long,
) {
    // specs/278-fix-key-loss-on-lyrics-search: applyAudioParentMarkers — самый долгий шаг в цепочке
    // doCreateFromFolder (поиск по waveform через акустическое сходство). KEY_BPM_FROM_FILE и
    // DEMUCS2, поставленные в очередь из Song.createFromPath(), почти наверняка уже отработали к
    // этому моменту и обновили song_tone/song_bpm/url'ы стемов в БД через свой экземпляр
    // Song.saveToDb(). Перезагружаем объект из БД, чтобы getDiff() не включил эти поля в UPDATE.
    // deltaMs рассчитывается от song.ms — после reload используем reloaded.ms (длительность
    // песни уже не изменится за время reload'а, но это единообразно с applyDuplicateOriginal).
    val songToSave =
        Song.loadFromDbById(
            id = song.id,
            database = song.database,
            storageService = song.storageService,
            storageApiClient = song.storageApiClient,
        ) ?: song
    songToSave.sourceText = audioParent.sourceText
    songToSave.resultText = audioParent.resultText
    songToSave.sourceMarkers = shiftMarkersAndFixEnd(audioParent.sourceMarkers, deltaMs, songToSave.ms)
    songToSave.formattedTextSong = audioParent.formattedTextSong
    songToSave.formattedTextTabs = audioParent.formattedTextTabs
    songToSave.formattedTextChords = audioParent.formattedTextChords
    songToSave.fields[SongField.ID_STATUS] = "5"
    songToSave.saveToDb()

    // specs/279-fix-parent-search-folder-add: синхронизировать song в памяти с только что записанным
    // состоянием. Тот же паттерн, что и в applyDuplicateOriginal — без явной синхронизации любой
    // последующий song.saveToDb() (например, если другой код-путь сразу после этой функции сохраняет
    // песню через тот же объект) увидит расхождение между памятью и БД и перезапишет только что
    // записанные поля (audio_* через diff).
    song.sourceText = songToSave.sourceText
    song.resultText = songToSave.resultText
    song.sourceMarkers = songToSave.sourceMarkers
    song.formattedTextSong = songToSave.formattedTextSong
    song.formattedTextTabs = songToSave.formattedTextTabs
    song.formattedTextChords = songToSave.formattedTextChords
    song.fields[SongField.ID_STATUS] = songToSave.fields[SongField.ID_STATUS] ?: "5"
}

fun applyFamilySongSelection(
    song: Song,
    another: Song,
    deltaMs: Long? = null,
    audioParentId: Long? = null,
    audioSimilarityPercent: Int? = null,
    audioDeltaMs: Long? = null,
) {
    /*
    Выбор песни из модалки "Похожие версии песни" (как из общего списка "семьи", так и из ручного
    поиска по названию). В отличие от applyDuplicateOriginal (автопоиск оригинала при импорте) -
    статус трогается только условно, не перетирая уже существующее осознанное значение, а вот
    root_id - осознанный выбор самого пользователя (явный клик по конкретной строке), поэтому
    переписывается безусловно, даже если у текущей песни уже был какой-то root_id:
    - значение - root_id кандидата, если он у него уже есть (кандидат сам часть семьи - указываем
      на её настоящий корень), иначе id самого кандидата (кандидат и есть корень)
    - статус NONE (0) переводится в TEXT_CREATE (1) только если он ещё NONE

    deltaMs - результат акустической сверки (кнопка "Сверить"): если задан, маркеры кандидата
    сдвигаются на дельту в таймлайн текущей песни, а END-маркер пересчитывается под её реальную
    длительность. Если null (строку не сверяли) - маркеры копируются как есть (прежнее поведение).

    Аудиопараметры (audioParentId, audioSimilarityPercent, audioDeltaMs) — opt-in: если все три
    null, поведение helper'а не меняется (используется автоматическим autoAssignOriginalByWaveform,
    который трогать нельзя — см. specs/129-copy-family-audio/research.md Decision 3). Если заданы,
    helper устанавливает их до единственного saveToDb(), чтобы текст/маркеры/root/status и три
    аудиополя попали в один SQL UPDATE и один recordhash-diff.

    Race condition защита (specs/281-find-lyrics-overwrites-key-bpm, FR-011): паттерн
    reload-from-db-before-save, как в applyDuplicateOriginal и applyAudioParentMarkers. Объект
    song мог жить в памяти долго (ручной клик из модалки — несколько секунд между load и save,
    autoAssignOriginalByWaveform — десятки секунд ffmpeg-сверки). Параллельные процессы могли
    обновить key/bpm/URL'ы стемов. Без reload Song.getDiff() увидел бы пустые поля в stale
    song против заполненных в БД и включил бы их в UPDATE → перезатирание.
     */
    val songToSave =
        Song.loadFromDbById(
            id = song.id,
            database = song.database,
            storageService = song.storageService,
            storageApiClient = song.storageApiClient,
        ) ?: song
    songToSave.sourceText = another.sourceText
    songToSave.resultText = another.resultText
    songToSave.sourceMarkers =
        if (deltaMs != null) {
            shiftMarkersAndFixEnd(another.sourceMarkers, deltaMs, songToSave.ms)
        } else {
            another.sourceMarkers
        }
    songToSave.formattedTextSong = another.formattedTextSong
    songToSave.formattedTextTabs = another.formattedTextTabs
    songToSave.formattedTextChords = another.formattedTextChords
    songToSave.rootId = if (another.rootId != 0L) another.rootId else another.id
    if (songToSave.idStatus == 0L) songToSave.fields[SongField.ID_STATUS] = "1"
    // Аудиополя — opt-in. Передаются только из ручного endpoint'а; autoAssignOriginalByWaveform
    // вызывает helper с дефолтами (без аудиопараметров) и не трогает audioParentId/percent/delta.
    if (audioParentId != null) songToSave.audioParentId = audioParentId
    if (audioSimilarityPercent != null) songToSave.audioSimilarityPercent = audioSimilarityPercent
    if (audioDeltaMs != null) songToSave.audioDeltaMs = audioDeltaMs
    songToSave.saveToDb()

    // specs/281-find-lyrics-overwrites-key-bpm (FR-011): синхронизировать `song` в памяти с только что
    // записанным состоянием, чтобы любой последующий `song.saveToDb()` в caller-е (например,
    // autoAssignOriginalByWaveform финальный saveToDb на строке 4850) видел актуальные поля и НЕ
    // включил их в diff. Тот же паттерн, что и в applyDuplicateOriginal/applyAudioParentMarkers.
    song.sourceText = songToSave.sourceText
    song.resultText = songToSave.resultText
    song.sourceMarkers = songToSave.sourceMarkers
    song.formattedTextSong = songToSave.formattedTextSong
    song.formattedTextTabs = songToSave.formattedTextTabs
    song.formattedTextChords = songToSave.formattedTextChords
    song.rootId = songToSave.rootId
    song.fields[SongField.ID_STATUS] = songToSave.fields[SongField.ID_STATUS] ?: "1"
    if (audioParentId != null) song.audioParentId = audioParentId
    if (audioSimilarityPercent != null) song.audioSimilarityPercent = audioSimilarityPercent
    if (audioDeltaMs != null) song.audioDeltaMs = audioDeltaMs
}

/**
 * Сдвигает все маркеры на deltaMs (мс) в таймлайн текущей песни и выставляет END-маркер на реальную
 * длительность текущей песни (currentMs). Разбор JSON - тем же способом, что геттер
 * Song.sourceMarkersList (список голосов; фолбэк на одиночный список маркеров).
 */
fun shiftMarkersAndFixEnd(
    sourceMarkersJson: String,
    deltaMs: Long,
    currentMs: Long,
): String {
    val voices: List<List<SourceMarker>> =
        try {
            Json.decodeFromString(ListSerializer(ListSerializer(SourceMarker.serializer())), sourceMarkersJson)
        } catch (_: Exception) {
            try {
                listOf(Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkersJson))
            } catch (_: Exception) {
                return sourceMarkersJson // не смогли распарсить - возвращаем как есть, без сдвига
            }
        }
    val deltaSec = deltaMs / 1000.0
    val endSec = currentMs / 1000.0
    val shifted =
        voices.map { voice ->
            voice.map { m ->
                val isEnd = m.markertype == Markertype.SETTING.value && m.label == "END"
                m.copy(time = if (isEnd) endSec else maxOf(0.0, m.time + deltaSec))
            }
        }
    return Json.encodeToString(ListSerializer(ListSerializer(SourceMarker.serializer())), shifted)
}

fun findFamilySongIds(
    currentSong: Song,
    database: KaraokeConnection,
): List<Long> {
    /*
    Ищет "семью" песни - все песни, у которых id или root_id совпадает с id или root_id текущей песни
    (сама текущая песня в результат не включается). Покрывает случаи: сама "корневая" песня (id == текущий root_id),
    песни-братья (root_id == текущий root_id) и песни-дети текущей (root_id == текущий id, если текущая - корень).
     */
    val keys = mutableSetOf(currentSong.id)
    if (currentSong.rootId != 0L) keys.add(currentSong.rootId)
    val connection = database.getConnection() ?: return emptyList()
    val placeholders = keys.joinToString(",") { "?" }
    val sql = "SELECT id FROM tbl_songs WHERE id <> ? AND (id IN ($placeholders) OR root_id IN ($placeholders))"
    val ps = connection.prepareStatement(sql)
    var idx = 1
    ps.setLong(idx++, currentSong.id)
    keys.forEach { ps.setLong(idx++, it) }
    keys.forEach { ps.setLong(idx++, it) }
    val rs = ps.executeQuery()
    val ids = mutableListOf<Long>()
    while (rs.next()) ids.add(rs.getLong("id"))
    rs.close()
    ps.close()
    return ids
}

/**
 * Результат автопривязки оригинала по аудио-сверке для одной песни. Используется для агрегированного лога
 * пакетной операции (эндпоинт /songs/autoassignoriginalall).
 */
data class AutoOriginalResult(
    val songId: Long,
    val matched: Boolean,
    val bestId: Long?,
    val bestPercent: Int?,
    val deltaMs: Long?,
    val reason: String,
)

/** Человекочитаемое описание песни для логов автопривязки: автор / год / альбом / название (+ id). */
fun songLogLabel(s: Song): String = "${s.author} / ${s.year} / ${s.album} / «${s.songName}» (id=${s.id})"

/**
 * Автоматический аналог ручного сценария из модалки "Похожие версии песни" на карточке песни:
 * найти "семью" песни, акустически сверить текущую песню с каждым размеченным кандидатом
 * (WaveformCompare — кросс-корреляция огибающих вокала), выбрать кандидата с максимальным процентом
 * схожести и, если он не ниже порога, применить его так же, как клик по строке в модалке
 * (applyFamilySongSelection со сдвигом маркеров), доиграть серверный эквивалент кнопки "Сохранить"
 * (пересчёт производных полей + запись .srt по каждому голосу) и перевести песню в статус 2 (TEXT_CHECK).
 *
 * Порог по умолчанию — 95 %. Кандидат обязан иметь непустые маркеры (иначе копировать нечего). Если ни
 * один кандидат не прошёл сверку (нет аудио) или не набрал порога — статус остаётся прежним.
 */
fun autoAssignOriginalByWaveform(
    song: Song,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    threshold: Int = 95,
): AutoOriginalResult {
    val familyIds = findFamilySongIds(song, database)
    if (familyIds.isEmpty()) {
        return AutoOriginalResult(song.id, false, null, null, null, "Нет песен в семье")
    }
    val family = Song.loadListFromDbByIds(familyIds, database, storageService, storageApiClient)
    // Кандидат годится, только если у него есть непустые маркеры — из пустого копировать нечего,
    // заодно экономим тяжёлый ffmpeg-декод в сверке.
    val candidates = family.values.filter { c -> c.sourceMarkersList.any { it.isNotEmpty() } }
    if (candidates.isEmpty()) {
        return AutoOriginalResult(song.id, false, null, null, null, "Нет размеченных кандидатов в семье")
    }

    // Сверяем со всеми кандидатами, оставляем только удачные (есть аудио), берём максимальный процент.
    val best =
        candidates
            .map { candidate -> candidate to WaveformCompare.compareWaveforms(song, candidate) }
            .filter { it.second.ok }
            .maxByOrNull { it.second.similarityPercent }

    if (best == null) {
        return AutoOriginalResult(song.id, false, null, null, null, "Не удалось сверить ни одного кандидата (нет аудио)")
    }

    val (bestSong, cmp) = best
    if (cmp.similarityPercent < threshold) {
        return AutoOriginalResult(
            song.id,
            false,
            bestSong.id,
            cmp.similarityPercent,
            cmp.deltaMs,
            "Лучшее совпадение ${cmp.similarityPercent}% [${songLogLabel(bestSong)}] ниже порога $threshold%",
        )
    }

    // 1) Применяем выбор кандидата так же, как клик по строке в модалке (со сдвигом маркеров под таймлайн
    //    текущей песни; END-маркер внутри shiftMarkersAndFixEnd уже сажается на реальную длительность текущей).
    applyFamilySongSelection(song, bestSong, deltaMs = cmp.deltaMs)

    // 2) Серверный эквивалент кнопки "Сохранить" в SubsEdit.vue: applyFamilySongSelection пересчитывает
    //    только resultText, а setSourceMarkers при сохранении пересчитывает ещё 3 форматированных поля —
    //    воспроизводим их из уже установленных (сдвинутых) маркеров.
    song.resultText = song.getText()
    song.formattedTextSong = song.getTextFormatted()
    song.formattedTextTabs = song.getFormattedNotes()
    song.formattedTextChords = song.getFormattedChords()

    // Запись .srt по каждому голосу — как в /song/savesourcetextmarkers.
    song.sourceMarkersList.indices.forEach { voice ->
        try {
            val srt = song.convertMarkersToSrt(voice)
            val pathToFile = "${song.rootFolder}/${song.fileName}.voice${voice + 1}.srt"
            File(pathToFile).writeText(srt)
            runCommand(listOf("chmod", "666", pathToFile))
        } catch (_: Exception) {
            println("Ошибка при создании файла субтитров для песни ${song.id}, голос ${voice + 1}.")
        }
    }

    // 3) Переводим песню в статус 2 (TEXT_CHECK) и сохраняем всё одним saveToDb().
    // specs/281-find-lyrics-overwrites-key-bpm (FR-012): reload-from-db-before-save — между
    // applyFamilySongSelection (где уже есть reload+sync по FR-011) и этим saveToDb проходит
    // ~несколько секунд на запись N файлов .srt. Параллельный процесс мог обновить поля —
    // без reload diff перезатрёт их. Делаем reload на финал, чтобы гарантировать атомарность
    // обновления (status + resultText + formatted* без потери key/bpm/url'ов стемов).
    val finalSongToSave =
        Song.loadFromDbById(
            id = song.id,
            database = song.database,
            storageService = song.storageService,
            storageApiClient = song.storageApiClient,
        ) ?: song
    finalSongToSave.resultText = song.resultText
    finalSongToSave.formattedTextSong = song.formattedTextSong
    finalSongToSave.formattedTextTabs = song.formattedTextTabs
    finalSongToSave.formattedTextChords = song.formattedTextChords
    finalSongToSave.fields[SongField.ID_STATUS] = "2"
    finalSongToSave.saveToDb()
    // Синхронизировать song в памяти, чтобы внешний код (если он ещё держит ссылку) видел актуальное состояние.
    song.fields[SongField.ID_STATUS] = "2"

    return AutoOriginalResult(
        song.id,
        true,
        bestSong.id,
        cmp.similarityPercent,
        cmp.deltaMs,
        "Привязано к [${songLogLabel(bestSong)}] (${cmp.similarityPercent}%, сдвиг ${cmp.deltaMs} мс)",
    )
}

/** Порог схожести (%), начиная с которого кандидат может быть записан как "аудио-родитель". См. findAudioParentByWaveform. */
const val AUDIO_PARENT_THRESHOLD = 95

/** Результат поиска "аудио-родителя" песни (см. findAudioParentByWaveform). */
data class AudioParentResult(
    val songId: Long,
    val matched: Boolean,
    val bestId: Long?,
    val bestPercent: Int?,
    val deltaMs: Long?,
    val reason: String,
)

/**
 * Ищет и запоминает "аудио-родителя" песни - песню, максимально похожую по звучанию (WaveformCompare),
 * НЕЗАВИСИМО от кураторского root_id/"семьи". В отличие от autoAssignOriginalByWaveform, здесь ничего
 * не применяется к тексту/маркерам/статусу - только записываются 3 поля (audio_parent_id,
 * audio_similarity_percent, audio_delta_ms) плюс служебная история сравнений (audio_compare_history),
 * которая не даёт повторно гонять WaveformCompare для уже сравненных пар при последующих запусках.
 *
 * Кандидаты - объединение findFamilySongIds (курируемая семья, если уже есть) и
 * searchSongsByNormalizedName по названию текущей песни (across all authors) - тот же набор
 * источников, что уже видит пользователь в модалке "Похожие версии песни".
 *
 * Порог отбора - AUDIO_PARENT_THRESHOLD (95%). Дерево audio_parent_id, как и root_id, должно
 * оставаться плоским (глубина 1) и без петель: если у лучшего кандидата уже есть свой
 * audio_parent_id, текущая песня получает ЕГО (а не id самого кандидата) - флэттенинг в один хоп.
 * Если у кандидата ещё нет аудио-родителя ("первичный анализ"), корнем считается песня с меньшим id;
 * если корнем оказывается сама текущая песня (в т.ч. когда кандидат уже указывает на неё) -
 * audio_parent_id не трогаем, песня остаётся корнем.
 */
fun findAudioParentByWaveform(
    song: Song,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): AudioParentResult {
    val candidateIds =
        (findFamilySongIds(song, database) + searchSongsByNormalizedName(song, song.songName, database))
            .toSet() - song.id
    if (candidateIds.isEmpty()) {
        return AudioParentResult(song.id, false, null, null, null, "Нет кандидатов (ни в семье, ни по названию)")
    }

    val historyById = song.audioCompareHistoryList.associateBy { it.id }.toMutableMap()
    val newIds = candidateIds.filter { it !in historyById }
    var loadedCandidates: Map<Long, Song> = emptyMap()

    if (newIds.isNotEmpty()) {
        loadedCandidates = Song.loadListFromDbByIds(newIds, database, storageService, storageApiClient)
        val now = Instant.now().toString()
        newIds.forEach { id ->
            val candidate = loadedCandidates[id] ?: return@forEach
            val cmp = WaveformCompare.compareWaveforms(song, candidate)
            historyById[id] = AudioCompareHistoryEntry(id, cmp.similarityPercent, cmp.deltaMs, cmp.ok, now)
        }
    }

    // specs/281-find-lyrics-overwrites-key-bpm (FR-013): reload-from-db-before-save — между
    // loadFromDbById в caller-е и saveToDb ниже проходят десятки секунд (ffmpeg-декод через
    // WaveformCompare.compareWaveforms). Параллельные процессы могли обновить поля песни —
    // применяем историю сверок к reloaded объекту, чтобы diff в saveToDb не перезатёр ничего лишнего.
    val songToSave =
        Song.loadFromDbById(
            id = song.id,
            database = song.database,
            storageService = song.storageService,
            storageApiClient = song.storageApiClient,
        ) ?: song
    songToSave.audioCompareHistory =
        Json.encodeToString(ListSerializer(AudioCompareHistoryEntry.serializer()), historyById.values.toList())

    val best =
        candidateIds
            .mapNotNull { historyById[it] }
            .filter { it.ok }
            .maxByOrNull { it.similarityPercent }

    if (best == null || best.similarityPercent < AUDIO_PARENT_THRESHOLD) {
        songToSave.saveToDb()
        song.audioCompareHistory = songToSave.audioCompareHistory
        return AudioParentResult(
            song.id,
            false,
            best?.id,
            best?.similarityPercent,
            best?.deltaMs,
            if (best == null) {
                "Не удалось сверить ни одного кандидата (нет аудио)"
            } else {
                "Лучшее совпадение ${best.similarityPercent}% (id=${best.id}) ниже порога $AUDIO_PARENT_THRESHOLD%"
            },
        )
    }

    val bestSong =
        loadedCandidates[best.id]
            ?: Song.loadFromDbById(best.id, database = database, storageService = storageService, storageApiClient = storageApiClient)
            ?: run {
                songToSave.saveToDb()
                song.audioCompareHistory = songToSave.audioCompareHistory
                return AudioParentResult(
                    song.id,
                    false,
                    best.id,
                    best.similarityPercent,
                    best.deltaMs,
                    "Кандидат id=${best.id} не найден при резолве корня",
                )
            }

    val resolvedRoot =
        if (bestSong.audioParentId != 0L) {
            bestSong.audioParentId
        } else if (bestSong.id < song.id) {
            bestSong.id
        } else {
            song.id
        }

    if (resolvedRoot == song.id) {
        songToSave.saveToDb()
        song.audioCompareHistory = songToSave.audioCompareHistory
        return AudioParentResult(
            song.id,
            false,
            best.id,
            best.similarityPercent,
            best.deltaMs,
            "Текущая песня определена как корень пары с [${songLogLabel(bestSong)}] (меньший id)",
        )
    }

    songToSave.audioParentId = resolvedRoot
    songToSave.audioSimilarityPercent = best.similarityPercent
    songToSave.audioDeltaMs = best.deltaMs
    songToSave.saveToDb()
    // Синхронизировать song в памяти — caller (ApiController.findaudioparent, Utils.findParentAndAudioParentForAll)
    // читает song.audioParentId после возврата.
    song.audioParentId = songToSave.audioParentId
    song.audioSimilarityPercent = songToSave.audioSimilarityPercent
    song.audioDeltaMs = songToSave.audioDeltaMs
    song.audioCompareHistory = songToSave.audioCompareHistory

    return AudioParentResult(
        song.id,
        true,
        resolvedRoot,
        best.similarityPercent,
        best.deltaMs,
        "Похож на [${songLogLabel(bestSong)}] (${best.similarityPercent}%, сдвиг ${best.deltaMs} мс)" +
            if (resolvedRoot != bestSong.id) ", корень id=$resolvedRoot" else "",
    )
}

/**
 * Возвращает список свободных слотов публикации для подсказок в поле «Дата
 * публикации» (`SongEdit.vue`): по одному варианту на каждый целый час с
 * 10:00 до 22:00 включительно (13 слотов). Для каждого часа кандидат-дата —
 * день, следующий за самой поздней уже занятой датой публикации в этот час
 * (среди всех песен независимо от площадки публикации); если для часа ещё не
 * было ни одной публикации, кандидат стартует от сегодняшней даты. Кандидат
 * затем сдвигается вперёд по одному дню, пока не станет строго позже текущего
 * момента на сервере — это гарантирует, что ни один предложенный слот никогда
 * не оказывается в прошлом или уже наступившим сегодня часом.
 * @see specs/156-publish-slots-range/spec.md
 * @return список строк формата "dd.MM.yy HH:mm", по одной на каждый час 10:00-22:00
 */
fun getFreeTimeSlots(): List<String> {
    val hours = (10..22).toList()
    val hourLabels = hours.map { "%02d:00".format(it) }
    val inClause = hourLabels.joinToString(",") { "'$it'" }
    val sql =
        """
        SELECT ts.publish_time AS PT, MAX(TO_DATE(ts.publish_date, 'DD.MM.YY')) AS LAST_DATE
        FROM tbl_songs ts
        WHERE ts.publish_time IN ($inClause)
        GROUP BY ts.publish_time
        """.trimIndent()

    val database = WORKING_DATABASE
    val connection = database.getConnection()
    if (connection == null) {
        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
        return emptyList()
    }
    var statement: Statement? = null
    var rs: ResultSet? = null

    val lastUsedDateByHour = mutableMapOf<Int, LocalDate>()
    try {
        statement = connection.createStatement()
        rs = statement.executeQuery(sql)
        while (rs.next()) {
            val hour = rs.getString("PT").substringBefore(":").toInt()
            lastUsedDateByHour[hour] = rs.getDate("LAST_DATE").toLocalDate()
        }
    } catch (e: SQLException) {
        e.printStackTrace()
        return emptyList()
    } finally {
        try {
            rs?.close() // close result set
            statement?.close() // close statement
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    val now = LocalDateTime.now()
    val result = mutableListOf<String>()
    for (hour in hours) {
        var candidate = (lastUsedDateByHour[hour]?.plusDays(1) ?: LocalDate.now()).atTime(hour, 0)
        while (candidate <= now) {
            candidate = candidate.plusDays(1)
        }
        result.add(candidate.format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")))
    }
    return result
}

package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.llm.LyricsFinderService
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.textfiledictionary.TextFileDictionary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import jakarta.servlet.http.HttpServletRequest
import java.io.File

/**
 * Контроллер (HTTP/WebSocket endpoints) для main .
 *
 * @see AGENTS.md
 */
@Controller
class MainController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val lyricsFinderService: LyricsFinderService,
) {
    /**
     * BR-236: журнал admin-контроллера. Категория зарегистрирована в
     * `knowledge/domains/monitoring/components/log-categories.md`
     * (`infra.admin.processes`) — по правилу компоненты новая категория обязана
     * быть зафиксирована в таблице.
     */
    private val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger("infra.admin.processes")

    @GetMapping("/")
    fun main(model: Model): String {
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute("authors", Song.loadListAuthors(withSkiped = false, database = WORKING_DATABASE))
        model.addAttribute(
            "dicts",
            TEXT_FILE_DICTS.keys
                .toMutableList()
                .sorted()
                .toList(),
        )
        return "main"
    }

    @GetMapping("/zakroma")
    fun zakroma(
        @RequestParam(required = false) author: String?,
        model: Model,
    ): String {
        val args: MutableMap<String, String> = mutableMapOf()
        author?.let { if (author != "") args["author"] = author }
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute("authors", Song.loadListAuthors(database = WORKING_DATABASE))
        model.addAttribute(
            "zakroma",
            Zakroma.getZakroma(
                author = author ?: "",
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ),
        )
        return "zakroma"
    }

    @GetMapping("/utils/createdigest")
    @ResponseBody
    fun doCreateDigest(): Boolean {
        createDigestForAllAuthors(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        createDigestForAllAuthorsForOper(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return true
    }

    // Обновить хранилище.
    // BR-234/P-10-04: раньше вызывалось с `emptyList()` и `threadId = 1`, поэтому не копировалось
    // ни одной песни и не создавалось ни одного задания 720p, а UI получал «успех». Теперь список
    // грузится так же, как в API-зеркале, лейн — THREAD_LANE_HEAVY_RENDER (0), а наружу уходят
    // реальные счётчики, чтобы «0 сделано» отличалось от успеха.
    @GetMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(): List<Int> {
        val songList =
            Song.loadListFromDb(
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            )
        val (countCopy, countCode) =
            collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(
                songList = songList,
                threadId = KaraokeProcess.THREAD_LANE_HEAVY_RENDER,
            )
        return listOf(countCopy, countCode)
    }

    @PostMapping("/utils/censored")
    @ResponseBody
    fun doCensored(
        @RequestParam(required = true) source: String,
    ): String = source.censored(WORKING_DATABASE)

    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String,
    ): Int =
        Song
            .createFromPath(
                folder,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).addedSongs.size

    @PostMapping("/utils/createdzenpicturesforfolder")
    @ResponseBody
    fun doCreateDzenPicturesForFolder(
        @RequestParam(required = true) folder: String,
    ): Boolean {
        createDzenPicture(folder)
        return true
    }

    @GetMapping("/utils/updatebpmandkey")
    @ResponseBody
    fun doUpdateBpmAndKey(): Int =
        updateBpmAndKey(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

//    @PostMapping("/utils/updateremotedatabasefromlocaldatabase")
//    @ResponseBody
//    fun doUpdateRemoteDatabaseFromLocalDatabase(
//        @RequestParam(required = true) updateSongs: Boolean = true,
//        @RequestParam(required = true) updatePictures: Boolean = true
//    ): List<Int> {
//        val result = updateRemoteDatabaseFromLocalDatabase(updateSongs,updatePictures)
//
//        return listOf(result.first, result.second, result.third)
//    }
//
//    @PostMapping("/utils/updatelocaldatabasefromremotedatabase")
//    @ResponseBody
//    fun doUpdateLocalDatabaseFromRemoteDatabase(
//        @RequestParam(required = true) updateSongs: Boolean = true,
//        @RequestParam(required = true) updatePictures: Boolean = true
//    ): List<Int> {
//        val result = updateLocalDatabaseFromRemoteDatabase(updateSongs,updatePictures)
//
//        return listOf(result.first, result.second, result.third)
//    }

    @PostMapping("/utils/markdublicates")
    @ResponseBody
    fun doMarkDublicates(
        @RequestParam(required = true) author: String,
    ): Int = markDublicates(author, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

    @GetMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates(): Int =
        delDublicates(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

    @GetMapping("/utils/clearpredublicates")
    @ResponseBody
    fun doClearPreDublicates(): Int =
        clearPreDublicates(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

    @GetMapping("/utils/customfunction")
    @ResponseBody
    fun doCustomFunction(): String =
        customFunction(storageService = storageService, storageApiClient = storageApiClient, lyricsFinderService = lyricsFinderService)

    // Pass 435 (#159): зеркало POST /api/utils/backfillcacheetagsize для Thymeleaf-страниц.
    @GetMapping("/utils/backfillcacheetagsize")
    @ResponseBody
    fun doBackfillCacheEtagSize(): String =
        backfillCacheEtagSize(
            storageService = storageService,
            storageApiClient = storageApiClient,
            remoteBreaker = HealthReport.remoteStorageCircuitBreaker,
        )

    // Спека #449 (#182): зеркало POST /api/utils/warmstoragecache для Thymeleaf-страниц.
    @GetMapping("/utils/warmstoragecache")
    @ResponseBody
    fun doWarmStorageCache(): String =
        warmStorageCache(
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
            remoteBreaker = HealthReport.remoteStorageCircuitBreaker,
        )

    // specs/277-song-name-censored: зеркало POST /api/utils/rescanallcensorednames для Thymeleaf-страниц.
    @GetMapping("/utils/rescanallcensorednames")
    @ResponseBody
    fun doRescanAllCensoredNames(): String =
        rescanAllCensoredNames(
            storageService = storageService,
            storageApiClient = storageApiClient,
            lyricsFinderService = lyricsFinderService,
        )

    @PostMapping("/changesettingsstatus")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) songId: Long,
        @RequestParam(required = true) statusId: Long,
    ) {
        Song
            .loadFromDbById(
                songId,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let {
                it.fields[SongField.ID_STATUS] = statusId.toString()
                it.saveToDb()
            }
    }

    /**
     * BR-231: разбор числовых параметров без 500.
     *
     * Раньше `id.toLong()` / `process_order.toInt()` / `process_priority.toInt()` бросали
     * `NumberFormatException` (или NPE на non-null Kotlin-параметре с `required = false`),
     * а наружу уходил 500. Штатный сценарий — кнопка «New prior» с пустым полем: она
     * проставляет пустую строку в priority ВСЕМ строкам и вызывает `updateProcess` для
     * каждой — каждый вызов получал 500, приоритеты в БД не менялись.
     *
     * Теперь: параметры nullable, пустое/пропущенное поле означает «не менять», а
     * нечисловое значение — 400 с причиной. Статус проверяется по
     * [KaraokeProcessStatuses], чтобы в БД не попал мусор.
     *
     * BR-232: для XHR возвращается короткий JSON, а не `redirect:/processes`. Раньше XHR
     * прозрачно шёл по 302 и СКАЧИВАЛ ВСЮ страницу `/processes` (полный SELECT всех
     * процессов + полный Thymeleaf-рендер) на КАЖДОЕ изменение поля, а «New prior» —
     * N раз подряд (O(N²)). Теперь XHR получает `{ok:true}` и обновляет строку сама.
     */
    @PostMapping("/process/update")
    fun progressUpdate(
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) process_name: String?,
        @RequestParam(required = false) process_status: String?,
        @RequestParam(required = false) process_order: String?,
        @RequestParam(required = false) process_priority: String?,
        @RequestParam(required = false) process_description: String?,
        @RequestParam(required = false) process_type: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val processId =
            id?.trim()?.toLongOrNull()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "bad_process_id")
        val order = parseOptionalInt(process_order, "bad_process_order")
        val priority = parseOptionalInt(process_priority, "bad_process_priority")
        val status =
            process_status?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                if (KaraokeProcessStatuses.entries.none { it.name == value }) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "bad_process_status")
                }
                value
            }

        val process = KaraokeProcess.load(processId, WORKING_DATABASE)
        if (process == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "process_not_found")
        }
        process.name = process_name ?: process.name
        status?.let { process.status = it }
        order?.let { process.order = it }
        priority?.let { process.priority = it }
        process.description = process_description ?: process.description
        process.type = process_type ?: process.type

        process.save()
        process.updateStatusProcessSong(
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )

        return if (isXhr(request)) {
            ResponseEntity.ok(mapOf("ok" to true))
        } else {
            ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/processes").build()
        }
    }

    @GetMapping("/songs/lastupdated")
    @ResponseBody
    fun getLastUpdatedSongs(
        @RequestParam(required = false) lastTime: Long? = null,
    ): List<Int> = Song.getLastUpdated(lastTime, WORKING_DATABASE)

    @GetMapping("/process/lastupdated")
    @ResponseBody
    fun getLastUpdatedProcesses(
        @RequestParam(required = false) lastTime: Long? = null,
    ): List<Int> = KaraokeProcess.getLastUpdated(lastTime, WORKING_DATABASE)

    @GetMapping("/songs/createtags")
    @ResponseBody
    fun doCreateTags(): Boolean {
        createFilesByTags(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return true
    }

    @GetMapping("/process/start")
    @ResponseBody
    fun doProcessWorkerStart(): Boolean {
        KaraokeProcessWorker.start(database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
        return KaraokeProcessWorker.isWork
    }

    @GetMapping("/process/stop")
    @ResponseBody
    fun doProcessWorkerStop(): Boolean {
        KaraokeProcessWorker.stop()
        return KaraokeProcessWorker.isWork
    }

    @GetMapping("/process/deletedone")
    @ResponseBody
    fun doProcessDeleteDone(): Boolean {
        KaraokeProcess.deleteDone(WORKING_DATABASE)
        return true
    }

    @GetMapping("/process/isworking")
    @ResponseBody
    fun doProcessWorkerIsWorking(): Boolean = KaraokeProcessWorker.isWork

    @GetMapping("/process/isstopafterthreadssdone")
    @ResponseBody
    fun doProcessWorkerIsStopAfterThreadIsDone(): Boolean = KaraokeProcessWorker.stopAfterThreadIsDone

    @GetMapping("/song/{id}/pictureauthor")
    @ResponseBody
    fun getPictureAuthor(
        @PathVariable id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            return it.pictureAuthor?.full ?: ""
        }
        return ""
    }

    @GetMapping("/song/{id}/picturealbum")
    @ResponseBody
    fun getPictureAlbum(
        @PathVariable id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            return it.pictureAlbum?.full ?: ""
        }
        return ""
    }

    @GetMapping("/song/{id}/symlink")
    @ResponseBody
    fun doSymlink(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): Int {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.doSymlink(threadId = threadId?.toInt() ?: 0)
        return 0
    }

    /**
     * Удаление песни (legacy GET-эндпоинт страницы `/songs`).
     *
     * BR-208 (аудит #336, пакет `PKG-S3-24`): возвращается ЧИСЛО удалённых строк
     * (`1` — удалено, `0` — песня не найдена). Раньше эндпоинт всегда отдавал `0`, и
     * клиент (удалявший строку из DOM на любой `200`) показывал «Удалено» даже когда
     * песня не существовала — таблица расходилась с БД.
     */
    @GetMapping("/song/{id}/delete")
    @ResponseBody
    fun doDeleteSong(
        @PathVariable id: Long,
    ): Int {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        if (song == null) return 0
        song.deleteFromDb()
        return 1
    }

    @GetMapping("/song/{id}/setpublishdatetimetoauthor")
    @ResponseBody
    fun doSetPublishDateTimeToAuthor(
        @PathVariable id: Long,
    ): Int {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            Song.setPublishDateTimeToAuthor(song)
        }
        return 0
    }

    @GetMapping("/song/{id}/playlyrics")
    @ResponseBody
    fun doPlayLyrics(
        @PathVariable id: Long,
    ): Int {
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
        return 0
    }

    @GetMapping("/song/{id}/playkaraoke")
    @ResponseBody
    fun doPlayKaraoke(
        @PathVariable id: Long,
    ): Int {
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
        return 0
    }

    @GetMapping("/song/{id}/playchords")
    @ResponseBody
    fun doPlayChords(
        @PathVariable id: Long,
    ): Int {
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
        return 0
    }

    @PostMapping("/song/{id}/{voice}/savesourcetext")
    fun saveSourceText(
        @PathVariable id: Long,
        @PathVariable voice: Int,
        @RequestParam(required = false) sourceText: String = "",
        @RequestParam(required = false) threadId: String? = "0",
        model: Model,
    ): String {
        var text: String
//        if (sourceText.trim() != "") {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        text =
            song?.let {
                // BR-235: неограниченный `voice` из URL приводил к паддингу списков до 10^8
                // элементов и OOM всего JVM (вместе с очередью KaraokeProcess) — отклоняем до записи.
                if (!isValidVoiceIndex(voice = voice, countVoices = song.countVoices)) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "bad_voice")
                }
                // specs/547: текст и маркеры одного голоса — одним сохранением (research R4).
                song.setSourceTextAndMarkers(voice = voice, text = sourceText, markers = song.getSourceMarkers(voice))
                song.updateMarkersFromSourceText(voice)
                "OK"
            } ?: "Error"
//        }
        model.addAttribute("text", text)
        return "text"
    }

    @GetMapping("/song/{id}/doprocesslyrics")
    @ResponseBody
    fun doProcessLyrics(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): Long {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            return KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_LYRICS, true, 0, threadId = threadId?.toInt() ?: 0)
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocesskaraoke")
    @ResponseBody
    fun doProcessKaraoke(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): Long {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            return KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_KARAOKE, true, 1, threadId = threadId?.toInt() ?: 0)
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocesschords")
    @ResponseBody
    fun doProcessChords(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): Long {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
            // specs/544-song-artifact-cleanup (FR-007): признак аккордов считается по
            // маркерам разметки из БД (Song.hasChords), без чтения файлов субтитров.
            if (song.hasChords) {
                return KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_CHORDS, true, 1, threadId = threadId?.toInt() ?: 0)
            }
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocessall")
    @ResponseBody
    fun doProcessAll(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): List<Long> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val result: MutableList<Long> = mutableListOf()
        song?.let {
            // specs/544 (FR-007): по маркерам из БД, без чтения файлов субтитров
            val hasChords = song.hasChords
            result.add(KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_LYRICS, true, 4, threadId = threadId?.toInt() ?: 0))
            result.add(KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_KARAOKE, true, 4, threadId = threadId?.toInt() ?: 0))
            if (hasChords) {
                result.add(
                    KaraokeProcess.createProcess(
                        song,
                        KaraokeProcessTypes.MELT_CHORDS,
                        true,
                        4,
                        threadId =
                            threadId?.toInt() ?: 0,
                    ),
                )
            }
        }
        return result
    }

    @GetMapping("/song/{id}/doprocessallwolyrics")
    @ResponseBody
    fun doProcessAllWOLyrics(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): List<Long> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val result: MutableList<Long> = mutableListOf()
        song?.let {
            // specs/544 (FR-007): по маркерам из БД, без чтения файлов субтитров
            val hasChords = song.hasChords
            result.add(KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_KARAOKE, true, 2, threadId = threadId?.toInt() ?: 0))
            if (hasChords) {
                result.add(
                    KaraokeProcess.createProcess(
                        song,
                        KaraokeProcessTypes.MELT_CHORDS,
                        true,
                        3,
                        threadId =
                            threadId?.toInt() ?: 0,
                    ),
                )
            }
        }
        return result
    }

    @GetMapping("/song/{id}/dodemucs2")
    @ResponseBody
    fun doProcessDemucs2(
        @PathVariable id: Long,
        @RequestParam(required = false) threadId: String? = "0",
    ): Long {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        song?.let {
//            if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(song, KaraokeProcessTypes.RECODE_48000, true, -1)
            return KaraokeProcess.createProcess(song, KaraokeProcessTypes.DEMUCS2, true, -1, threadId = threadId?.toInt() ?: 0)
        }
        return 0
    }

    @GetMapping("/process/{id}")
    @ResponseBody
    fun getProcess(
        @PathVariable id: Long,
    ): KaraokeProcess? = KaraokeProcess.load(id, WORKING_DATABASE)

    @GetMapping("/process/working")
    @ResponseBody
    fun getWorkingProcess(): KaraokeProcess? =
        KaraokeProcess.loadList(mapOf(Pair("process_status", KaraokeProcessStatuses.WORKING.name)), WORKING_DATABASE).firstOrNull()

    @GetMapping("/song/{id}")
    @ResponseBody
    fun getSong(
        @PathVariable id: Long,
    ): Song? =
        Song.loadFromDbById(id = id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)

    @GetMapping("/song/{id}/{voice}/sourcetext")
    @ResponseBody
    fun getSourceText(
        @PathVariable id: Long,
        @PathVariable voice: Int,
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
                song.getSourceText(voice)
            } ?: ""
//        model.addAttribute("text", text)
        return text
    }

    @GetMapping("/song/{id}/{voice}/sourcemarkers")
    @ResponseBody
    fun getSourceMarkers(
        @PathVariable id: Long,
        @PathVariable voice: Int,
    ): List<SourceMarker> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            song.getSourceMarkers(voice)
        } ?: emptyList()
    }

    @GetMapping("/song/{id}/{voice}/sourcesyllables")
    @ResponseBody
    fun getSourceSyllables(
        @PathVariable id: Long,
        @PathVariable voice: Int,
    ): List<String> {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            song.getSourceSyllables(voice)
        } ?: emptyList()
    }

    @PostMapping("/song/{id}/{voice}/savesourcemarkers")
    fun saveSourceMarkers(
        @PathVariable id: Long,
        @PathVariable voice: Int,
        @RequestParam(required = false) sourceMarkers: String = "",
        model: Model,
    ): String {
        var text = "Error"
        if (sourceMarkers.trim() != "") {
            val song =
                Song.loadFromDbById(
                    id = id,
                    database = WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            // BR-236: раньше «нет песни» и «битый JSON» отдавались одинаково — строкой
            // "Error" с HTTP 200, а стек терялся (только println без стека). Теперь
            // различаем: 404 — песни нет, 400 — маркеры не разобрались. Логируем через
            // SLF4J со стеком и songId/voice.
            if (song == null) {
                log.warn("Маркеры для песни $id (голос $voice): песня не найдена в БД")
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "song_not_found")
            }
            text =
                song.let {
                    // BR-235: см. saveSourceText — проверка диапазона до паддинга списков.
                    if (!isValidVoiceIndex(voice = voice, countVoices = song.countVoices)) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "bad_voice")
                    }
                    val markers =
                        try {
                            Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers)
                        } catch (e: Exception) {
                            log.warn("Ошибка при парсинге маркеров для песни $id (голос $voice)", e)
                            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "bad_source_markers")
                        }
                    // specs/547: текст и маркеры одного голоса — одним сохранением (research R4).
                    song.setSourceTextAndMarkers(voice = voice, text = song.getSourceText(voice), markers = markers)
                    // specs/544-song-artifact-cleanup (FR-008): файл субтитров не создаётся.
                    "OK"
                }
        }
        model.addAttribute("text", text)
        return "text"
    }

    @GetMapping("/song/{id}/fileVocal")
    fun getSongFileVocal(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
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

    @GetMapping("/song/{id}/fileMusic")
    fun getSongFileMusic(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
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

    @GetMapping("/song/{id}/fileSong")
    fun getSongFileSong(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
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

    @GetMapping("/song/{id}/{voice}/editsubs")
    fun getSongEditSubs(
        @PathVariable id: Long,
        @PathVariable voice: Int,
        model: Model,
    ): String {
        Song
            .loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )?.let { song ->
                val markersValue = Json.encodeToString(song.getSourceMarkers(voice))
                val syllablesValue = Json.encodeToString(song.getSourceSyllables(voice))

                model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
                model.addAttribute("song", song)
                model.addAttribute("text", song.getSourceText(voice))
                model.addAttribute("markers", markersValue)
                model.addAttribute("syllables", syllablesValue)
                model.addAttribute("voice", voice)
            }

        return "editsubs"
    }

    @GetMapping("/song/{id}/color")
    @ResponseBody
    fun getSongColor(
        @PathVariable id: Long,
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
                song.color
            } ?: "#FFFFFF"
        return text
    }

    @GetMapping("/song/{id}/textdzenlyrics")
    @ResponseBody
    fun getSongTextDzenLyrics(
        @PathVariable id: Long,
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
                val text = it.getDescription(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenkaraoke")
    @ResponseBody
    fun getSongTextDzenKaraoke(
        @PathVariable id: Long,
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
                val text = it.getDescription(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenchords")
    @ResponseBody
    fun getSongTextDzenChords(
        @PathVariable id: Long,
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
                val text = it.getDescription(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenlyricsheader")
    @ResponseBody
    fun getSongTextDzenLyricsHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionHeader(SongVersion.LYRICS, 140)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenkaraokeheader")
    @ResponseBody
    fun getSongTextDzenKaraokeHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionHeader(SongVersion.KARAOKE, 140)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenchordsheader")
    @ResponseBody
    fun getSongTextDzenChordsHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionHeader(SongVersion.CHORDS, 140)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenlyricswoheader")
    @ResponseBody
    fun getSongTextDzenLyricsWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 5000)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenkaraokewoheader")
    @ResponseBody
    fun getSongTextDzenKaraokeWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE, 5000)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textdzenchordswoheader")
    @ResponseBody
    fun getSongTextDzenChordsWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.CHORDS, 5000)
                text
            } ?: ""
        return text
    }

    @PostMapping("/replacesymbolsinsong")
    @ResponseBody
    fun getReplaceSymbolsInSong(
        @RequestParam(required = true) txt: String,
    ): String {
//        println(txt)
        val result = replaceSymbolsInSong(txt)
        return result
    }

    @GetMapping("/song/{id}/textvklyrics")
    @ResponseBody
    fun getSongTextVkLyrics(
        @PathVariable id: Long,
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

    @GetMapping("/song/{id}/textvkkaraoke")
    @ResponseBody
    fun getSongTextVkKaraoke(
        @PathVariable id: Long,
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

    @GetMapping("/song/{id}/textvkchords")
    @ResponseBody
    fun getSongTextVkChords(
        @PathVariable id: Long,
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
                val text = it.getDescriptionVk(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvklyricsheader")
    @ResponseBody
    fun getSongTextVkLyricsHeader(
        @PathVariable id: Long,
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

    @GetMapping("/song/{id}/textvkkaraokeheader")
    @ResponseBody
    fun getSongTextVkKaraokeHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionVkHeader(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkchordsheader")
    @ResponseBody
    fun getSongTextVkChordsHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionVkHeader(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvklyricswoheader")
    @ResponseBody
    fun getSongTextVkLyricsWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS, 4785)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkkaraokewoheader")
    @ResponseBody
    fun getSongTextVkKaraokeWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE, 4785)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkchordswoheader")
    @ResponseBody
    fun getSongTextVkChordsWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.CHORDS, 4893)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramlyrics")
    @ResponseBody
    fun getSongTextTelegramLyrics(
        @PathVariable id: Long,
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

    @GetMapping("/song/{id}/texttelegramkaraoke")
    @ResponseBody
    fun getSongTextTelegramKaraoke(
        @PathVariable id: Long,
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

    @GetMapping("/song/{id}/texttelegramchords")
    @ResponseBody
    fun getSongTextTelegramChords(
        @PathVariable id: Long,
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
                val text = it.getDescriptionVk(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramlyricsheader")
    @ResponseBody
    fun getSongTextTelegramLyricsHeader(
        @PathVariable id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        // BR-233: для Telegram-заголовка нужен `getDescriptionTelegramHeader` (он добавляет
        // ссылку на страницу песни), а не VK-вариант. Раньше три «Telegram header»-метода
        // клали в буфер обмена VK-текст без ссылки, и администратор публиковал пост без неё.
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramkaraokeheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeHeader(
        @PathVariable id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        // BR-233: см. getSongTextTelegramLyricsHeader.
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramchordsheader")
    @ResponseBody
    fun getSongTextTelegramChordsHeader(
        @PathVariable id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        // BR-233: см. getSongTextTelegramLyricsHeader.
        val text =
            song?.let {
                val text = it.getDescriptionTelegramHeader(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramlyricswoheader")
    @ResponseBody
    fun getSongTextTelegramLyricsWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.LYRICS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramkaraokewoheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.KARAOKE)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramchordswoheader")
    @ResponseBody
    fun getSongTextTelegramChordsWOHeader(
        @PathVariable id: Long,
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
                val text = it.getDescriptionWOHeaderWithTimecodes(SongVersion.CHORDS)
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textboostyhead")
    @ResponseBody
    fun getSongTextBoostyHead(
        @PathVariable id: Long,
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
                val text = it.getTextBoostyHead()
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textboostybody")
    @ResponseBody
    fun getSongTextBoostyBody(
        @PathVariable id: Long,
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
                val text = it.getTextBoostyBody()
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkbody")
    @ResponseBody
    fun getSongTextVkBody(
        @PathVariable id: Long,
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
                val text = it.getVKGroupDescription()
                text
            } ?: ""
        return text
    }

    @GetMapping("/song/{id}/searchsongtext")
    @ResponseBody
    fun getSearchSongText(
        @PathVariable id: Long,
    ): String {
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        return song?.let {
            searchSongText(song)
        } ?: ""
    }

    @GetMapping("/song/{id}/createkaraoke")
    fun getSongCreateKaraoke(
        @PathVariable id: Long,
        model: Model,
        @RequestParam(required = false) threadId: String? = "0",
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
                song.createKaraoke()
                KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_LYRICS, true, 0, threadId = threadId?.toInt() ?: 0)
                KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_KARAOKE, true, 1, threadId = threadId?.toInt() ?: 0)
                "OK"
            } ?: "Error"
        model.addAttribute("text", text)
        return "text"
    }

    // specs/544-song-artifact-cleanup (FR-009): эндпоинт /song/{id}/createkdenlivefiles удалён
    // вместе с писателем .kdenlive-файлов; вызовов во фронтендах не было.

    @PostMapping("/songs/createkaraokeall")
    fun getSongsCreateKaraokeAll(
        @RequestParam(required = false) txt: String?,
        model: Model,
        @RequestParam(required = false) threadId: String? = "0",
    ): String {
        var result = "Error"
        txt?.let {
            val ids =
                txt
                    .split(";")
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
                    song.createKaraoke()

                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_LYRICS, true, 10, threadId = threadId?.toInt() ?: 0)
                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.MELT_KARAOKE, true, 10, threadId = threadId?.toInt() ?: 0)
                }
                result = "OK"
            }
        }
        model.addAttribute("text", result)
        return "text"
    }

    @PostMapping("/songs/createdemucs2all")
    fun getSongsCreateDemucs2All(
        @RequestParam(required = false) txt: String?,
        model: Model,
        @RequestParam(required = false) threadId: String? = "0",
    ): String {
        var result = "Error"
        txt?.let {
            val ids =
                txt
                    .split(";")
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
//                    if (it.getAudioAspectRate() != "48000") KaraokeProcess.createProcess(song, KaraokeProcessTypes.RECODE_48000, true, -1)
                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.DEMUCS2, true, -1, threadId = threadId?.toInt() ?: 0)
                }
                result = "OK"
            }
        }
        model.addAttribute("text", result)
        return "text"
    }

    @PostMapping("/songs/create720pkaraokeall")
    fun getSongsCreate720pKaraokeAll(
        @RequestParam(required = false) txt: String?,
        model: Model,
        @RequestParam(required = false) threadId: String? = "0",
    ): String {
        var result = "Error"
        txt?.let {
            val ids =
                txt
                    .split(";")
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
                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.FF_720_KAR, true, 1, threadId = threadId?.toInt() ?: 0)
                }
                result = "OK"
            }
        }
        model.addAttribute("text", result)
        return "text"
    }

    @PostMapping("/songs/create720plyricsall")
    fun getSongsCreate720pLyricsAll(
        @RequestParam(required = false) txt: String?,
        model: Model,
        @RequestParam(required = false) threadId: String? = "0",
    ): String {
        var result = "Error"
        txt?.let {
            val ids =
                txt
                    .split(";")
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
                    KaraokeProcess.createProcess(song, KaraokeProcessTypes.FF_720_LYR, true, 1, threadId = threadId?.toInt() ?: 0)
                }
                result = "OK"
            }
        }
        model.addAttribute("text", result)
        return "text"
    }

    // specs/316-search-timeout-configurable (R-002, FR-007): deprecated endpoint, симметрично
    // ApiController.getSearchSongTextAll. timeout — query-param (секунды), иначе сохранённое
    // значение из KaraokeProperties.lyricsSearchTimeoutSeconds (default 10).
    @PostMapping("/songs/searchsongtextall")
    fun getSearchSongTextAll(
        @RequestParam(required = false) txt: String?,
        @RequestParam(required = false) timeout: Int? = null,
        model: Model,
    ): String {
        var result = "Error"
        // specs/316-search-timeout-configurable (R-002): effective timeout =
        // query-param, иначе сохранённое значение из KaraokeProperties (default 10).
        val effectiveTimeout =
            timeout?.coerceAtLeast(1)
                ?: Karaoke.lyricsSearchTimeoutSeconds
        txt?.let {
            val ids =
                txt
                    .split(";")
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
                    if (song.sourceText.isBlank()) {
                        val text = searchSongText(song)

                        Thread.sleep(effectiveTimeout * 1000L)

                        if (text.isNotBlank()) {
                            song.sourceText = text
                            song.fields[SongField.ID_STATUS] = "1"
                            // specs/357-folder-import-overwrite (FR-140): HTTP whisper — Thread.sleep +
                            // ML между load и save, секунды/десятки секунд. Параллельный SongEdit мог
                            // обновить поля. saveToDbLocked() защищает.
                            song.saveToDbLocked()
                        }
                    }
                }
                result = "OK"
            }
        }
        model.addAttribute("text", result)
        return "text"
    }

    @GetMapping("/processes")
    fun processes(
        @RequestParam(required = false) filter_id: String?,
        @RequestParam(required = false) filter_name: String?,
        @RequestParam(required = false) filter_status: String?,
        @RequestParam(required = false) filter_order: String?,
        @RequestParam(required = false) filter_priority: String?,
        @RequestParam(required = false) filter_description: String?,
        @RequestParam(required = false) filter_song_id: String?,
        @RequestParam(required = false) filter_type: String?,
        @RequestParam(required = false) filter_limit: String?,
        model: Model,
    ): String {
        val args: MutableMap<String, String> = mutableMapOf()
        filter_id?.let { if (filter_id != "") args["id"] = filter_id }
        filter_name?.let { if (filter_name != "") args["process_name"] = filter_name }
        filter_status?.let { if (filter_status != "") args["process_status"] = filter_status }
        filter_order?.let { if (filter_order != "") args["process_order"] = filter_order }
        filter_priority?.let { if (filter_priority != "") args["process_priority"] = filter_priority }
        filter_description?.let { if (filter_description != "") args["process_description"] = filter_description }
        filter_song_id?.let { if (filter_song_id != "") args["song_id"] = filter_song_id }
        filter_type?.let { if (filter_type != "") args["process_type"] = filter_type }
        // BR-034 (аудит #336, пакет PKG-S3-22): нечисловой/отрицательный `filter_limit`
        // раньше валил страницу в 500 (`toInt()`/`result[i]` в KaraokeProcess.loadList).
        // Для HTML-страницы админки честнее проигнорировать некорректный параметр, чем
        // отдать 400: лимит — необязательный фильтр, а не часть контракта страницы.
        filter_limit
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }
            ?.let { args["filter_limit"] = it.toString() }
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute("processes", KaraokeProcess.loadList(args, WORKING_DATABASE))

        return "processes"
    }

    @PostMapping("/songs_update")
    fun songsUpdate(
        @RequestParam(required = false) song_id: String,
        @RequestParam(required = false) song_songName: String,
        @RequestParam(required = false) song_author: String,
        @RequestParam(required = false) song_year: String,
        @RequestParam(required = false) song_album: String,
        @RequestParam(required = false) song_track: String,
        @RequestParam(required = false) song_tags: String,
        @RequestParam(required = false) song_date: String,
        @RequestParam(required = false) song_time: String,
        @RequestParam(required = false) song_key: String,
        @RequestParam(required = false) song_bpm: String,
        @RequestParam(required = false) song_ms: String,
        @RequestParam(required = false) song_fileName: String,
        @RequestParam(required = false) song_rootFolder: String,
        @RequestParam(required = false) song_idBoosty: String,
        // BR-205/BR-213: этих полей нет в форме редактора (webvue3) — и не было в
        // легаси-страницах песен (`songs.html`, `songs2.html` удалены 2026-10-26),
        // поэтому параметр обязан быть nullable: иначе Spring передаёт null, а
        // Kotlin-проверка non-null бросает NPE → 500 на каждом «Сохранить» (и правка
        // теряется молча).
        @RequestParam(required = false) song_idBoostyFiles: String?,
        @RequestParam(required = false) song_idVk: String,
        @RequestParam(required = false) song_idDzenLyrics: String,
        @RequestParam(required = false) song_idDzenKaraoke: String,
        @RequestParam(required = false) song_idDzenChords: String,
        @RequestParam(required = false) song_idVkLyrics: String,
        @RequestParam(required = false) song_idVkKaraoke: String,
        @RequestParam(required = false) song_idVkChords: String,
        @RequestParam(required = false) song_idTelegramLyrics: String,
        @RequestParam(required = false) song_idTelegramKaraoke: String,
        @RequestParam(required = false) song_idTelegramChords: String,
        @RequestParam(required = false) song_resultVersion: String?,
        @RequestParam(required = false) select_status: String,
    ): String {
        val songId: Long = song_id.toLong()
        val song =
            Song.loadFromDbById(
                songId,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        // BR-191 (аудит #336, пакет `PKG-S3-24`): дата/время публикации проверяются
        // ДО сохранения — неверный формат больше не попадает в БД (и не роняет потом
        // страницу публикаций целиком).
        try {
            Publication.validatePublishDateTime(song_date, song_time)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message ?: "неверный формат даты/времени публикации")
        }
        song?.let { songValue ->
            songValue.fileName = song_fileName
            songValue.rootFolder = song_rootFolder
            songValue.tags = song_tags
            songValue.fields[SongField.ID] = song_id
            songValue.fields[SongField.NAME] = song_songName
            songValue.fields[SongField.AUTHOR] = song_author
            songValue.fields[SongField.YEAR] = song_year
            songValue.fields[SongField.ALBUM] = song_album
            songValue.fields[SongField.TRACK] = song_track
            songValue.fields[SongField.DATE] = song_date
            songValue.fields[SongField.TIME] = song_time
            songValue.fields[SongField.KEY] = song_key
            songValue.fields[SongField.BPM] = song_bpm
            songValue.fields[SongField.MS] = song_ms
            songValue.fields[SongField.ID_BOOSTY] = song_idBoosty
            song_idBoostyFiles?.let { songValue.fields[SongField.ID_BOOSTY_FILES] = it }
            songValue.fields[SongField.ID_VK] = song_idVk
            songValue.fields[SongField.ID_DZEN_LYRICS] = song_idDzenLyrics
            songValue.fields[SongField.ID_DZEN_KARAOKE] = song_idDzenKaraoke
            songValue.fields[SongField.ID_DZEN_CHORDS] = song_idDzenChords
            songValue.fields[SongField.ID_VK_LYRICS] = song_idVkLyrics
            songValue.fields[SongField.ID_VK_KARAOKE] = song_idVkKaraoke
            songValue.fields[SongField.ID_VK_CHORDS] = song_idVkChords
            songValue.fields[SongField.ID_TELEGRAM_LYRICS] = song_idTelegramLyrics
            songValue.fields[SongField.ID_TELEGRAM_KARAOKE] = song_idTelegramKaraoke
            songValue.fields[SongField.ID_TELEGRAM_CHORDS] = song_idTelegramChords
            // BR-205/BR-213: не передан — не затираем сохранённое значение (fail-closed).
            song_resultVersion?.let { songValue.fields[SongField.RESULT_VERSION] = it }
            songValue.fields[SongField.ID_STATUS] = select_status
            songValue.saveToDb()
            // specs/544 (FR-006): sidecar-файл состояния не пишется.
//            if (song_idBoosty != "") {
//                songValue.createVKDescription()
//            }
        }
        return "redirect:/songs"
    }

    @GetMapping("/publications")
    fun publications(
        @RequestParam(required = false) filter_date_from: String?,
        @RequestParam(required = false) filter_date_to: String?,
        @RequestParam(required = false) filter_cond: String?,
        // BR-200: строка расписания — день; 31 день на страницу вместо всего каталога.
        @RequestParam(required = false) filter_page: Int?,
        model: Model,
    ): String {
        val args: MutableMap<String, String> = mutableMapOf()
        filter_date_from?.let { if (filter_date_from != "") args["publish_date_from"] = filter_date_from }
        filter_date_to?.let { if (filter_date_to != "") args["filter_date_to"] = filter_date_to }
        filter_cond?.let { if (filter_cond != "") args["filter_cond"] = filter_cond }

        val allPublications =
            Publication.getPublicationList(
                args,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        val page = normalizePageNumber(filter_page)
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute("publications", pageSlice(allPublications, page, PUBLICATION_PAGE_SIZE))
        model.addAttribute("page", page)
        model.addAttribute("hasNext", hasNextPage(allPublications, page, PUBLICATION_PAGE_SIZE))
        model.addAttribute("hasPrev", page > 1)
        return "publications"
    }

    @GetMapping("/unpublications")
    fun unpublications(model: Model): String {
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute(
            "publications",
            Publication.getUnPublicationList(
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ),
        )
        return "unpublications"
    }

    @PostMapping("/utils/tfd")
    @ResponseBody
    fun doTextFileDictionary(
        @RequestParam(required = true) dictName: String,
        @RequestParam(required = true) dictValue: String,
        @RequestParam(required = true) dictAction: String,
    ): Boolean = TextFileDictionary.doAction(dictName, dictAction, listOf(dictValue))
}

/**
 * BR-231: разбор НЕОБЯЗАТЕЛЬНОГО целого параметра.
 *
 * `null`/пустая строка → `null` («поле не передано — оставить как было»).
 * Нечисловое значение → 400 с причиной (раньше был 500 из `NumberFormatException`).
 */
internal fun parseOptionalInt(
    raw: String?,
    reason: String,
): Int? {
    val trimmed = raw?.trim()
    if (trimmed.isNullOrEmpty()) return null
    return trimmed.toIntOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, reason)
}

/**
 * BR-232: признак XHR-запроса.
 *
 * jQuery проставляет `X-Requested-With: XMLHttpRequest`; такие запросы не должны
 * получать HTML-страницу целиком (см. `/process/update`).
 */
internal fun isXhr(request: HttpServletRequest): Boolean =
    "XMLHttpRequest".equals(request.getHeader("X-Requested-With"), ignoreCase = true) ||
        request.getHeader("X-Requested-With")?.contains("XMLHttpRequest", ignoreCase = true) == true

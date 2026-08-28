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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
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

    // Обновить хранилище
    @GetMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(): Boolean {
        collectDoneFilesToStoreFolderAndCreate720pForAllUncreated(emptyList(), threadId = 1)
        return true
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

    @PostMapping("/process/update")
    fun progressUpdate(
        @RequestParam(required = false) id: String,
        @RequestParam(required = false) process_name: String,
        @RequestParam(required = false) process_status: String,
        @RequestParam(required = false) process_order: String,
        @RequestParam(required = false) process_priority: String,
        @RequestParam(required = false) process_description: String,
        @RequestParam(required = false) process_type: String,
    ): String {
        val processId: Long = id.toLong()
        val process = KaraokeProcess.load(processId, WORKING_DATABASE)
        process?.let {
            process.name = process_name
            process.status = process_status
            process.order = process_order.toInt()
            process.priority = process_priority.toInt()
            process.description = process_description
            process.type = process_type

            process.save()
            process.updateStatusProcessSong(
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        }

        return "redirect:/processes"
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
        song?.deleteFromDb()
        return 0
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
        text = song?.let {
            song.setSourceText(voice, sourceText)
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
            if (SongRenderContext(song, SongVersion.LYRICS).hasChords) {
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
            val hasChords = SongRenderContext(song, SongVersion.LYRICS).hasChords
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
            val hasChords = SongRenderContext(song, SongVersion.LYRICS).hasChords
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
            text = song?.let {
                song.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
                val strText = song.convertMarkersToSrt(voice)
                val pathToFile = "${song.rootFolder}/${song.fileName}.voice${voice + 1}.srt"
                File(pathToFile).writeText(strText)
                runCommand(listOf("chmod", "666", pathToFile))
                "OK"
            } ?: "Error"
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
        val text =
            song?.let {
                val text = it.getDescriptionVkHeader(SongVersion.LYRICS)
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
        val text =
            song?.let {
                val text = it.getDescriptionVkHeader(SongVersion.KARAOKE)
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
        val text =
            song?.let {
                val text = it.getDescriptionVkHeader(SongVersion.CHORDS)
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

    @GetMapping("/song/{id}/createkdenlivefiles")
    fun createKdenliveFiles(
        @PathVariable id: Long,
        @RequestParam(required = false) overrideKdenliveFile: Boolean = true,
        @RequestParam(required = false) overrideKdenliveSubsFile: Boolean = false,
        model: Model,
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
                song.createKdenliveFiles(overrideKdenliveFile, overrideKdenliveSubsFile)
                "OK"
            } ?: "Error"
        model.addAttribute("text", text)
        return "text"
    }

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

    @PostMapping("/songs/searchsongtextall")
    fun getSearchSongTextAll(
        @RequestParam(required = false) txt: String?,
        model: Model,
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
                    if (song.sourceText.isBlank()) {
                        val text = searchSongText(song)

                        Thread.sleep(2000)

                        if (text.isNotBlank()) {
                            song.sourceText = text
                            song.fields[SongField.ID_STATUS] = "1"
                            song.saveToDb()
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
        filter_limit?.let { if (filter_limit != "") args["filter_limit"] = filter_limit }
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute("processes", KaraokeProcess.loadList(args, WORKING_DATABASE))

        return "processes"
    }

    @GetMapping("/songs")
    fun songs(
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
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_dzen_lyrics: String?,
        @RequestParam(required = false) flag_dzen_karaoke: String?,
        @RequestParam(required = false) flag_dzen_chords: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        @RequestParam(required = false) filter_result_version: String?,
        model: Model,
    ): String {
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
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_dzen_lyrics?.let { if (flag_dzen_lyrics != "") args["flag_dzen_lyrics"] = flag_dzen_lyrics }
        flag_dzen_karaoke?.let { if (flag_dzen_karaoke != "") args["flag_dzen_karaoke"] = flag_dzen_karaoke }
        flag_dzen_chords?.let { if (flag_dzen_chords != "") args["flag_dzen_chords"] = flag_dzen_chords }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        filter_result_version?.let { if (filter_result_version != "") args["filter_result_version"] = filter_result_version }
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute(
            "song",
            Song.loadListFromDb(
                args,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            ),
        )
        model.addAttribute("authors", Song.loadListAuthors(withSkiped = false, database = WORKING_DATABASE))
        model.addAttribute("albums", Song.loadListAlbums(WORKING_DATABASE))
        return "songs"
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
        @RequestParam(required = false) song_idBoostyFiles: String,
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
        @RequestParam(required = false) song_resultVersion: String,
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
            songValue.fields[SongField.ID_BOOSTY_FILES] = song_idBoostyFiles
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
            songValue.fields[SongField.RESULT_VERSION] = song_resultVersion
            songValue.fields[SongField.ID_STATUS] = select_status
            songValue.saveToDb()
            songValue.saveToFile()
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
        model: Model,
    ): String {
        val args: MutableMap<String, String> = mutableMapOf()
        filter_date_from?.let { if (filter_date_from != "") args["publish_date_from"] = filter_date_from }
        filter_date_to?.let { if (filter_date_to != "") args["filter_date_to"] = filter_date_to }
        filter_cond?.let { if (filter_cond != "") args["filter_cond"] = filter_cond }

        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute(
            "publications",
            Publication.getPublicationList(
                args,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ),
        )
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

    @GetMapping("/songs2")
    fun songs2(
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
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_dzen_lyrics: String?,
        @RequestParam(required = false) flag_dzen_karaoke: String?,
        @RequestParam(required = false) flag_dzen_chords: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        @RequestParam(required = false) filter_result_version: String?,
        model: Model,
    ): String {
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
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_dzen_lyrics?.let { if (flag_dzen_lyrics != "") args["flag_dzen_lyrics"] = flag_dzen_lyrics }
        flag_dzen_karaoke?.let { if (flag_dzen_karaoke != "") args["flag_dzen_karaoke"] = flag_dzen_karaoke }
        flag_dzen_chords?.let { if (flag_dzen_chords != "") args["flag_dzen_chords"] = flag_dzen_chords }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        filter_result_version?.let { if (filter_result_version != "") args["filter_result_version"] = filter_result_version }
        model.addAttribute("workInContainer", APP_WORK_IN_CONTAINER)
        model.addAttribute(
            "song",
            Song.loadListFromDb(
                args,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
                withoutMarkersAndText = true,
            ),
        )
        model.addAttribute("authors", Song.loadListAuthors(withSkiped = false, database = WORKING_DATABASE))
        model.addAttribute("albums", Song.loadListAlbums(WORKING_DATABASE))
        return "songs2"
    }

    @PostMapping("/songs2_update")
    fun songs2Update(
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
        @RequestParam(required = false) song_idBoostyFiles: String,
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
        @RequestParam(required = false) song_resultVersion: String,
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
            songValue.fields[SongField.ID_BOOSTY_FILES] = song_idBoostyFiles
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
            songValue.fields[SongField.RESULT_VERSION] = song_resultVersion
            songValue.fields[SongField.ID_STATUS] = select_status
            songValue.saveToDb()
            songValue.saveToFile()
//            if (song_idBoosty != "") {
//                songValue.createVKDescription()
//            }
        }
        return "redirect:/songs2"
    }
}

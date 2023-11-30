package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.textfiledictionary.CensoredWordsDictionary
import com.svoemesto.karaokeapp.textfiledictionary.TestDictionary
import com.svoemesto.karaokeapp.textfiledictionary.TextFileDictionary
import com.svoemesto.karaokeapp.textfiledictionary.YoWordsDictionary
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.io.File

@Controller
class MainController(private val webSocket: SimpMessagingTemplate) {
//
//    private val emittersSettings = CopyOnWriteArrayList<SseEmitter>()
//    private val emittersProcesses = CopyOnWriteArrayList<SseEmitter>()

    @GetMapping("/")
    fun main(model: Model): String {
        model.addAttribute("authors", Settings.loadListAuthors())
        model.addAttribute("dicts", TEXT_FILE_DICTS.keys.toMutableList().sorted().toList())
        return "main"
    }

    @GetMapping("/utils/createdigest")
    @ResponseBody
    fun doCreateDigest(): Boolean {
        createDigestForAllAuthors()
        createDigestForAllAuthorsForOper()
        return true
    }

    @GetMapping("/utils/collectstore")
    @ResponseBody
    fun doCollectStore(): Boolean {
        collectDoneFilesToStoreFolderAndCreate720pForAllUncreated()
        return true
    }

    @PostMapping("/utils/censored")
    @ResponseBody
    fun doCensored(
        @RequestParam(required = true) source: String,
        model: Model): String {
        return source.censored()
    }

    @PostMapping("/utils/createfromfolder")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) folder: String,
        model: Model): Int {
        return Settings.createFromPath(folder).size
    }

    @PostMapping("/utils/createdzenpicturesforfolder")
    @ResponseBody
    fun doCreateDzenPicturesForFolder(
        @RequestParam(required = true) folder: String,
        model: Model): Boolean {
        createDzenPicture(folder)
        return true
    }

    @GetMapping("/utils/updatebpmandkey")
    @ResponseBody
    fun doUpdateBpmAndKey(): Int {
        return updateBpmAndKey()
    }

    @PostMapping("/utils/markdublicates")
    @ResponseBody
    fun doMarkDublicates(
        @RequestParam(required = true) author: String,
        model: Model): Int {
        return markDublicates(author)
    }

    @GetMapping("/utils/deldublicates")
    @ResponseBody
    fun doDelDublicates(): Int {
        return delDublicates()
    }

    @GetMapping("/utils/clearpredublicates")
    @ResponseBody
    fun doClearPreDublicates(): Int {
        return clearPreDublicates()
    }

    @GetMapping("/utils/customfunction")
    @ResponseBody
    fun doCustomFunction(): String {
        return customFunction()
    }

    @PostMapping("/changesettingsstatus")
    @ResponseBody
    fun doCreateFromFolder(
        @RequestParam(required = true) settingsId: Long,
        @RequestParam(required = true) statusId: Long,
        model: Model) {
        Settings.loadFromDbById(settingsId)?.let {
            it.fields[SettingField.ID_STATUS] = statusId.toString()
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
        model: Model): String {

        val processId: Long = id.toLong()
        val process = KaraokeProcess.load(processId)
        process?.let {

            process.name = process_name
            process.status = process_status
            process.order = process_order.toInt()
            process.priority = process_priority.toInt()
            process.description = process_description
            process.type = process_type

            process.save()
            process.updateStatusProcessSettings()

        }

        return "redirect:/processes"
    }

    @GetMapping("/songs/lastupdated")
    @ResponseBody
    fun getLastUpdatedSettings(@RequestParam(required = false) lastTime: Long? = null): List<Int> {
        return Settings.getLastUpdated(lastTime)
    }

    @GetMapping("/process/lastupdated")
    @ResponseBody
    fun getLastUpdatedProcesses(@RequestParam(required = false) lastTime: Long? = null): List<Int> {
        return KaraokeProcess.getLastUpdated(lastTime)
    }

    @GetMapping("/songs/createtags")
    @ResponseBody
    fun doCreateTags(): Boolean {
        createFilesByTags()
        return true
    }

    @GetMapping("/process/start")
    @ResponseBody
    fun doProcessWorkerStart(): Boolean {
        KaraokeProcessWorker.start()
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
        KaraokeProcessWorker.deleteDone()
        return true
    }

    @GetMapping("/process/isworking")
    @ResponseBody
    fun doProcessWorkerIsWorking(): Boolean {
        return KaraokeProcessWorker.isWork
    }

    @GetMapping("/process/isstopafterthreadssdone")
    @ResponseBody
    fun doProcessWorkerIsStopAfterThreadIsDone(): Boolean {
        return KaraokeProcessWorker.stopAfterThreadIsDone
    }

    @GetMapping("/song/{id}/pictureauthor")
    @ResponseBody
    fun getPictureAuthor(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return it.pictureAuthor?.full ?: ""
        }
        return ""
    }

    @GetMapping("/song/{id}/picturealbum")
    @ResponseBody
    fun getPictureAlbum(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return it.pictureAlbum?.full ?: ""
        }
        return ""
    }

    @GetMapping("/song/{id}/delete")
    @ResponseBody
    fun doDeleteSong(@PathVariable id: Long): Int {
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            it.deleteFromDb()
        }
        return 0
    }

    @GetMapping("/song/{id}/setpublishdatetimetoauthor")
    @ResponseBody
    fun doSetPublishDateTimeToAuthor(@PathVariable id: Long): Int {
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            Settings.setPublishDateTimeToAuthor(settings)
        }
        return 0
    }

    @GetMapping("/song/{id}/playlyrics")
    @ResponseBody
    fun doPlayLyrics(@PathVariable id: Long): Int {
        println("doPlayLyrics")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            settings.playLyrics()
        }
        return 0
    }

    @GetMapping("/song/{id}/playkaraoke")
    @ResponseBody
    fun doPlayKaraoke(@PathVariable id: Long): Int {
        println("doPlayKaraoke")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            settings.playKaraoke()
        }
        return 0
    }

    @GetMapping("/song/{id}/playchords")
    @ResponseBody
    fun doPlayChords(@PathVariable id: Long): Int {
        println("doPlayChords")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            settings.playChords()
        }
        return 0
    }

    @GetMapping("/song/{id}/playlyricsbt")
    @ResponseBody
    fun doPlayLyricsBt(@PathVariable id: Long): Int {
        println("doPlayLyricsBt")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            settings.playLyricsBt()
        }
        return 0
    }

    @GetMapping("/song/{id}/playkaraokebt")
    @ResponseBody
    fun doPlayKaraokeBt(@PathVariable id: Long): Int {
        println("doPlayKaraokeBt")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            settings.playKaraokeBt()
        }
        return 0
    }

    @GetMapping("/song/{id}/playchordsbt")
    @ResponseBody
    fun doPlayChordsBt(@PathVariable id: Long): Int {
        println("doPlayChordsBt")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            settings.playChordsBt()
        }
        return 0
    }

    @PostMapping("/song/{id}/{voice}/savesourcetext")
    fun saveSourceText(
        @PathVariable id: Long,
        @PathVariable voice: Int,
        @RequestParam(required = false) sourceText: String = "",
        model: Model): String {
        var text = "Error"
//        if (sourceText.trim() != "") {
            val settings = Settings.loadFromDbById(id)
            text = settings?.let {
                settings.setSourceText(voice, sourceText)
                settings.updateMarkersFromSourceText(voice)
                "OK"
            } ?: "Error"
//        }
        model.addAttribute("text", text)
        return "text"
    }

    @GetMapping("/song/{id}/doprocesslyrics")
    @ResponseBody
    fun doProcessLyrics(@PathVariable id: Long): Int {
        println("doProcessLyrics")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 0)
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocesskaraoke")
    @ResponseBody
    fun doProcessKaraoke(@PathVariable id: Long): Int {
        println("doProcessKaraoke")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocesschords")
    @ResponseBody
    fun doProcessChords(@PathVariable id: Long): Int {
        println("doProcessChords")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            if (Song(settings, SongVersion.LYRICS).hasChords) {
                return KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, 1)
            }
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocessall")
    @ResponseBody
    fun doProcessAll(@PathVariable id: Long): List<Int> {
        println("doProcessAll")
        val settings = Settings.loadFromDbById(id)
        val result: MutableList<Int> = mutableListOf()
        settings?.let {
            val hasChords = Song(settings, SongVersion.LYRICS).hasChords
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 4))
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 4))
            if (hasChords) {
                result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, 4))
            }
        }
        return result
    }

    @GetMapping("/song/{id}/doprocessallwolyrics")
    @ResponseBody
    fun doProcessAllWOLyrics(@PathVariable id: Long): List<Int> {
        println("doProcessAllWOLyrics")
        val settings = Settings.loadFromDbById(id)
        val result: MutableList<Int> = mutableListOf()
        settings?.let {
            val hasChords = Song(settings, SongVersion.LYRICS).hasChords
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 2))
            if (hasChords) {
                result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, 3))
            }
        }
        return result
    }

    @GetMapping("/song/{id}/dodemucs2")
    @ResponseBody
    fun doProcessDemucs2(@PathVariable id: Long): Int {
        println("doProcessDemucs2")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return  KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, -1)
        }
        return 0
    }

    @GetMapping("/process/{id}")
    @ResponseBody
    fun getProcess(@PathVariable id: Long): KaraokeProcess? {
        return KaraokeProcess.load(id)
    }

    @GetMapping("/process/working")
    @ResponseBody
    fun getWorkingProcess(): KaraokeProcess? {
        return KaraokeProcess.loadList(mapOf(Pair("process_status",KaraokeProcessStatuses.WORKING.name))).firstOrNull()
    }

    @GetMapping("/song/{id}")
    @ResponseBody
    fun getSong(@PathVariable id: Long): Settings? {
        return Settings.loadFromDbById(id)
    }

    @GetMapping("/song/{id}/{voice}/sourcetext")
    @ResponseBody
    fun getSourceText(@PathVariable id: Long, @PathVariable voice: Int): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            settings.getSourceText(voice)
        } ?: ""
//        model.addAttribute("text", text)
        return text
    }

    @GetMapping("/song/{id}/{voice}/sourcemarkers")
    @ResponseBody
    fun getSourceMarkers(@PathVariable id: Long, @PathVariable voice: Int): List<SourceMarker> {
        val settings = Settings.loadFromDbById(id)
        return settings?.let {
            settings.getSourceMarkers(voice)
        } ?: emptyList()
    }

    @GetMapping("/song/{id}/{voice}/sourcesyllables")
    @ResponseBody
    fun getSourceSyllables(@PathVariable id: Long, @PathVariable voice: Int): List<String> {
        val settings = Settings.loadFromDbById(id)
        return settings?.let {
            settings.getSourceSyllables(voice)
        } ?: emptyList()
    }

    @PostMapping("/song/{id}/{voice}/savesourcemarkers")
    fun saveSourceMarkers(
        @PathVariable id: Long,
        @PathVariable voice: Int,
        @RequestParam(required = false) sourceMarkers: String = "",
        model: Model): String {
        var text = "Error"
        if (sourceMarkers.trim() != "") {
            val settings = Settings.loadFromDbById(id)
            text = settings?.let {
                settings.setSourceMarkers(voice, Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
                val strText = settings.convertMarkersToSrt(voice)
                File("${settings.rootFolder}/${settings.fileName}.voice${voice+1}.srt").writeText(strText)
                "OK"
            } ?: "Error"
        }
        model.addAttribute("text", text)
        return "text"
    }


    @GetMapping("/song/{id}/fileVocal")
    fun getSongFileVocal(
        @PathVariable id: Long,
        model: Model
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id)
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

    @GetMapping("/song/{id}/fileMusic")
    fun getSongFileMusic(
        @PathVariable id: Long,
        model: Model
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id)
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

    @GetMapping("/song/{id}/fileSong")
    fun getSongFileSong(
        @PathVariable id: Long,
        model: Model
    ): ResponseEntity<Resource> {
        val settings = Settings.loadFromDbById(id)
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

    @GetMapping("/song/{id}/{voice}/editsubs")
    fun getSongEditSubs(@PathVariable id: Long, @PathVariable voice: Int, model: Model): String {
        val settings = Settings.loadFromDbById(id)

        val textValue = Json.encodeToString(settings!!.getSourceText(voice))
        val markersValue = Json.encodeToString(settings!!.getSourceMarkers(voice))
        val syllablesValue = Json.encodeToString(settings!!.getSourceSyllables(voice))

        println(settings!!.getSourceText(voice))
        println(settings!!.getSourceMarkers(voice))
        println(settings!!.getSourceSyllables(voice))

        println(textValue)
        println(markersValue)
        println(syllablesValue)

        model.addAttribute("settings", settings)
        model.addAttribute("text", settings!!.getSourceText(voice))
        model.addAttribute("markers", markersValue)
        model.addAttribute("syllables", syllablesValue)
        model.addAttribute("voice", voice)
        return "editsubs"
    }

    @GetMapping("/song/{id}/color")
    @ResponseBody
    fun getSongColor(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            settings.color
        } ?: "#FFFFFF"
        return text
    }

    @GetMapping("/song/{id}/textyoutubelyrics")
    @ResponseBody
    fun getSongTextYoutubeLyrics(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescription()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubekaraoke")
    @ResponseBody
    fun getSongTextYoutubeKaraoke(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescription()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubechords")
    @ResponseBody
    fun getSongTextYoutubeChords(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescription()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textyoutubelyricsheader")
    @ResponseBody
    fun getSongTextYoutubeLyricsHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textyoutubekaraokeheader")
    @ResponseBody
    fun getSongTextYoutubeKaraokeHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textyoutubechordsheader")
    @ResponseBody
    fun getSongTextYoutubeChordsHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionHeader(140)
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textyoutubelyricswoheader")
    @ResponseBody
    fun getSongTextYoutubeLyricsWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionDzenWOHeader(5000)
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textyoutubekaraokewoheader")
    @ResponseBody
    fun getSongTextYoutubeKaraokeWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionDzenWOHeader(5000)
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textyoutubechordswoheader")
    @ResponseBody
    fun getSongTextYoutubeChordsWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionDzenWOHeader(5000)
            text
        } ?: ""
        return text
    }


    @PostMapping("/replacesymbolsinsong")
    @ResponseBody
    fun getReplaceSymbolsInSong(@RequestParam(required = true) txt: String): String {
//        println(txt)
        val result = replaceSymbolsInSong(txt)
        println(result)
        return result
    }




    @GetMapping("/song/{id}/textvklyrics")
    @ResponseBody
    fun getSongTextVkLyrics(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkkaraoke")
    @ResponseBody
    fun getSongTextVkKaraoke(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvkchords")
    @ResponseBody
    fun getSongTextVkChords(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvklyricsheader")
    @ResponseBody
    fun getSongTextVkLyricsHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkkaraokeheader")
    @ResponseBody
    fun getSongTextVkKaraokeHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvkchordsheader")
    @ResponseBody
    fun getSongTextVkChordsHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvklyricswoheader")
    @ResponseBody
    fun getSongTextVkLyricsWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkWOHeader()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvkkaraokewoheader")
    @ResponseBody
    fun getSongTextVkKaraokeWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkWOHeader()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvkchordswoheader")
    @ResponseBody
    fun getSongTextVkChordsWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVkWOHeader()
            text
        } ?: ""
        return text
    }




    @GetMapping("/song/{id}/texttelegramlyrics")
    @ResponseBody
    fun getSongTextTelegramLyrics(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramkaraoke")
    @ResponseBody
    fun getSongTextTelegramKaraoke(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/texttelegramchords")
    @ResponseBody
    fun getSongTextTelegramChords(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVk()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/texttelegramlyricsheader")
    @ResponseBody
    fun getSongTextTelegramLyricsHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramkaraokeheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/texttelegramchordsheader")
    @ResponseBody
    fun getSongTextTelegramChordsHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVkHeader()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/texttelegramlyricswoheader")
    @ResponseBody
    fun getSongTextTelegramLyricsWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkWOHeader()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramkaraokewoheader")
    @ResponseBody
    fun getSongTextTelegramKaraokeWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkWOHeader()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/texttelegramchordswoheader")
    @ResponseBody
    fun getSongTextTelegramChordsWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVkWOHeader()
            text
        } ?: ""
        return text
    }



    @GetMapping("/song/{id}/textboostyhead")
    @ResponseBody
    fun getSongTextBoostyHead(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getTextBoostyHead()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textboostybody")
    @ResponseBody
    fun getSongTextBoostyBody(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getTextBoostyBody()
            text
        } ?: ""
        return text
    }


    @GetMapping("/song/{id}/textvkbody")
    @ResponseBody
    fun getSongTextVkBody(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getVKGroupDescription()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/searchsongtext")
    @ResponseBody
    fun getSearchSongText(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        return  settings?.let {
            searchSongText(settings)
        } ?: ""
    }

    @GetMapping("/song/{id}/createkaraoke")
    fun getSongCreateKaraoke(@PathVariable id: Long, model: Model): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            settings.createKaraoke()
            if (settings.idStatus < 3) {
                settings.fields[SettingField.ID_STATUS] = "3"
                settings.saveToDb()
            }
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 0)
            KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 1)
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
        model: Model): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            settings.createKdenliveFiles(overrideKdenliveFile, overrideKdenliveSubsFile)
            "OK"
        } ?: "Error"
        model.addAttribute("text", text)
        return "text"
    }

    @PostMapping("/songs/createkaraokeall")
    fun getSongsCreateKaraokeAll(
        @RequestParam(required = false) txt: String?,
        model: Model): String {
        var result = "Error"
        txt?.let {
            val ids = txt.split(";").mapNotNull { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id)
                settings?.let {
                    settings.createKaraoke()

                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS, true, 10)
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 10)

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
        model: Model): String {
        var result = "Error"
        txt?.let {
            val ids = txt.split(";").mapNotNull { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.DEMUCS2, true, -1)
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
        model: Model): String {
        var result = "Error"
        txt?.let {
            val ids = txt.split(";").mapNotNull { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_KAR, true, 1)
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
        model: Model): String {
        var result = "Error"
        txt?.let {
            val ids = txt.split(";").mapNotNull { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id)
                settings?.let {
                    KaraokeProcess.createProcess(settings, KaraokeProcessTypes.FF_720_LYR, true, 1)
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
        model: Model): String {
        var result = "Error"
        txt?.let {
            val ids = txt.split(";").mapNotNull { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id)
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
                result = "OK"
            }
        }
        model.addAttribute("text", result)
        return "text"
    }

    @PostMapping("/songs/createtextandmarkersfromoldversion")
    fun createTextAndMarkersFromOldVersion(
        @RequestParam(required = false) txt: String?,
        model: Model): String {
        var result = "Error"
        txt?.let {
            val ids = txt.split(";").mapNotNull { it }.filter { it != "" }.map { it.toLong() }
            ids.forEach { id ->
                val settings = Settings.loadFromDbById(id)
                settings?.let {
                    settings.createTextAndMarkersFromOldVersion()
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
        @RequestParam(required = false) filter_settings_id: String?,
        @RequestParam(required = false) filter_type: String?,
        @RequestParam(required = false) filter_limit: String?,
        model: Model

    ): String {

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
        model.addAttribute("processes", KaraokeProcess.loadList(args))

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
        @RequestParam(required = false) flag_youtube_lyrics: String?,
        @RequestParam(required = false) flag_youtube_karaoke: String?,
        @RequestParam(required = false) flag_youtube_chords: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        model: Model): String {

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
        flag_youtube_lyrics?.let { if (flag_youtube_lyrics != "") args["flag_youtube_lyrics"] = flag_youtube_lyrics }
        flag_youtube_karaoke?.let { if (flag_youtube_karaoke != "") args["flag_youtube_karaoke"] = flag_youtube_karaoke }
        flag_youtube_chords?.let { if (flag_youtube_chords != "") args["flag_youtube_chords"] = flag_youtube_chords }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        model.addAttribute("sett", Settings.loadListFromDb(args))
        model.addAttribute("authors", Settings.loadListAuthors())
        model.addAttribute("albums", Settings.loadListAlbums())
        return "songs"
    }
    @PostMapping("/songs_update")
    fun songsUpdate(
        @RequestParam(required = false) settings_id: String,
        @RequestParam(required = false) settings_songName: String,
        @RequestParam(required = false) settings_author: String,
        @RequestParam(required = false) settings_year: String,
        @RequestParam(required = false) settings_album: String,
        @RequestParam(required = false) settings_track: String,
        @RequestParam(required = false) settings_tags: String,
        @RequestParam(required = false) settings_date: String,
        @RequestParam(required = false) settings_time: String,
        @RequestParam(required = false) settings_key: String,
        @RequestParam(required = false) settings_bpm: String,
        @RequestParam(required = false) settings_ms: String,
        @RequestParam(required = false) settings_fileName: String,
        @RequestParam(required = false) settings_rootFolder: String,
        @RequestParam(required = false) settings_idBoosty: String,
        @RequestParam(required = false) settings_idVk: String,
        @RequestParam(required = false) settings_idYoutubeLyrics: String,
        @RequestParam(required = false) settings_idYoutubeKaraoke: String,
        @RequestParam(required = false) settings_idYoutubeChords: String,
        @RequestParam(required = false) settings_idVkLyrics: String,
        @RequestParam(required = false) settings_idVkKaraoke: String,
        @RequestParam(required = false) settings_idVkChords: String,
        @RequestParam(required = false) settings_idTelegramLyrics: String,
        @RequestParam(required = false) settings_idTelegramKaraoke: String,
        @RequestParam(required = false) settings_idTelegramChords: String,
        @RequestParam(required = false) select_status: String,
        model: Model): String {
        val settingsId: Long = settings_id.toLong()
        val settings = Settings.loadFromDbById(settingsId)
        settings?.let { sett ->
            sett.fileName = settings_fileName
            sett.rootFolder = settings_rootFolder
            sett.tags = settings_tags
            sett.fields[SettingField.ID] = settings_id
            sett.fields[SettingField.NAME] = settings_songName
            sett.fields[SettingField.AUTHOR] = settings_author
            sett.fields[SettingField.YEAR] = settings_year
            sett.fields[SettingField.ALBUM] = settings_album
            sett.fields[SettingField.TRACK] = settings_track
            sett.fields[SettingField.DATE] = settings_date
            sett.fields[SettingField.TIME] = settings_time
            sett.fields[SettingField.KEY] = settings_key
            sett.fields[SettingField.BPM] = settings_bpm
            sett.fields[SettingField.MS] = settings_ms
            sett.fields[SettingField.ID_BOOSTY] = settings_idBoosty
            sett.fields[SettingField.ID_VK] = settings_idVk
            sett.fields[SettingField.ID_YOUTUBE_LYRICS] = settings_idYoutubeLyrics
            sett.fields[SettingField.ID_YOUTUBE_KARAOKE] = settings_idYoutubeKaraoke
            sett.fields[SettingField.ID_YOUTUBE_CHORDS] = settings_idYoutubeChords
            sett.fields[SettingField.ID_VK_LYRICS] = settings_idVkLyrics
            sett.fields[SettingField.ID_VK_KARAOKE] = settings_idVkKaraoke
            sett.fields[SettingField.ID_VK_CHORDS] = settings_idVkChords
            sett.fields[SettingField.ID_TELEGRAM_LYRICS] = settings_idTelegramLyrics
            sett.fields[SettingField.ID_TELEGRAM_KARAOKE] = settings_idTelegramKaraoke
            sett.fields[SettingField.ID_TELEGRAM_CHORDS] = settings_idTelegramChords
            sett.fields[SettingField.ID_STATUS] = select_status
            sett.saveToDb()
            sett.saveToFile()
            if (settings_idBoosty != "") {
                sett.createVKDescription()
            }

        }
        return "redirect:/songs"
    }



    @GetMapping("/publications")
    fun publications(
        @RequestParam(required = false) filter_date_from: String?,
        @RequestParam(required = false) filter_date_to: String?,
        @RequestParam(required = false) filter_cond: String?,
        model: Model
    ): String {

        val args: MutableMap<String, String> = mutableMapOf()
        filter_date_from?.let { if (filter_date_from != "") args["publish_date_from"] = filter_date_from }
        filter_date_to?.let { if (filter_date_to != "") args["filter_date_to"] = filter_date_to }
        filter_cond?.let { if (filter_cond != "") args["filter_cond"] = filter_cond }

        model.addAttribute("publications", Publication.getPublicationList(args))
        return "publications"
    }

    @GetMapping("/unpublications")
    fun unpublications(
        model: Model
    ): String {
        model.addAttribute("publications", Publication.getUnPublicationList())
        return "unpublications"
    }


    @PostMapping("/utils/tfd")
    @ResponseBody
    fun doTextFileDictionary(
        @RequestParam(required = true) dictName: String,
        @RequestParam(required = true) dictValue: String,
        @RequestParam(required = true) dictAction: String,
        model: Model): Boolean {

        return TextFileDictionary.doAction(dictName, dictAction, listOf(dictValue))

    }





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
        @RequestParam(required = false) flag_youtube_lyrics: String?,
        @RequestParam(required = false) flag_youtube_karaoke: String?,
        @RequestParam(required = false) flag_youtube_chords: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_telegram_lyrics: String?,
        @RequestParam(required = false) flag_telegram_karaoke: String?,
        @RequestParam(required = false) flag_telegram_chords: String?,
        model: Model): String {

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
        flag_youtube_lyrics?.let { if (flag_youtube_lyrics != "") args["flag_youtube_lyrics"] = flag_youtube_lyrics }
        flag_youtube_karaoke?.let { if (flag_youtube_karaoke != "") args["flag_youtube_karaoke"] = flag_youtube_karaoke }
        flag_youtube_chords?.let { if (flag_youtube_chords != "") args["flag_youtube_chords"] = flag_youtube_chords }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_telegram_lyrics?.let { if (flag_telegram_lyrics != "") args["flag_telegram_lyrics"] = flag_telegram_lyrics }
        flag_telegram_karaoke?.let { if (flag_telegram_karaoke != "") args["flag_telegram_karaoke"] = flag_telegram_karaoke }
        flag_telegram_chords?.let { if (flag_telegram_chords != "") args["flag_telegram_chords"] = flag_telegram_chords }
        model.addAttribute("sett", Settings.loadListFromDb(args))
        model.addAttribute("authors", Settings.loadListAuthors())
        model.addAttribute("albums", Settings.loadListAlbums())
        return "songs2"
    }


    @PostMapping("/songs2_update")
    fun songs2Update(
        @RequestParam(required = false) settings_id: String,
        @RequestParam(required = false) settings_songName: String,
        @RequestParam(required = false) settings_author: String,
        @RequestParam(required = false) settings_year: String,
        @RequestParam(required = false) settings_album: String,
        @RequestParam(required = false) settings_track: String,
        @RequestParam(required = false) settings_tags: String,
        @RequestParam(required = false) settings_date: String,
        @RequestParam(required = false) settings_time: String,
        @RequestParam(required = false) settings_key: String,
        @RequestParam(required = false) settings_bpm: String,
        @RequestParam(required = false) settings_ms: String,
        @RequestParam(required = false) settings_fileName: String,
        @RequestParam(required = false) settings_rootFolder: String,
        @RequestParam(required = false) settings_idBoosty: String,
        @RequestParam(required = false) settings_idVk: String,
        @RequestParam(required = false) settings_idYoutubeLyrics: String,
        @RequestParam(required = false) settings_idYoutubeKaraoke: String,
        @RequestParam(required = false) settings_idYoutubeChords: String,
        @RequestParam(required = false) settings_idVkLyrics: String,
        @RequestParam(required = false) settings_idVkKaraoke: String,
        @RequestParam(required = false) settings_idVkChords: String,
        @RequestParam(required = false) settings_idTelegramLyrics: String,
        @RequestParam(required = false) settings_idTelegramKaraoke: String,
        @RequestParam(required = false) settings_idTelegramChords: String,
        @RequestParam(required = false) select_status: String,
        model: Model): String {
        val settingsId: Long = settings_id.toLong()
        val settings = Settings.loadFromDbById(settingsId)
        settings?.let { sett ->
            sett.fileName = settings_fileName
            sett.rootFolder = settings_rootFolder
            sett.tags = settings_tags
            sett.fields[SettingField.ID] = settings_id
            sett.fields[SettingField.NAME] = settings_songName
            sett.fields[SettingField.AUTHOR] = settings_author
            sett.fields[SettingField.YEAR] = settings_year
            sett.fields[SettingField.ALBUM] = settings_album
            sett.fields[SettingField.TRACK] = settings_track
            sett.fields[SettingField.DATE] = settings_date
            sett.fields[SettingField.TIME] = settings_time
            sett.fields[SettingField.KEY] = settings_key
            sett.fields[SettingField.BPM] = settings_bpm
            sett.fields[SettingField.MS] = settings_ms
            sett.fields[SettingField.ID_BOOSTY] = settings_idBoosty
            sett.fields[SettingField.ID_VK] = settings_idVk
            sett.fields[SettingField.ID_YOUTUBE_LYRICS] = settings_idYoutubeLyrics
            sett.fields[SettingField.ID_YOUTUBE_KARAOKE] = settings_idYoutubeKaraoke
            sett.fields[SettingField.ID_YOUTUBE_CHORDS] = settings_idYoutubeChords
            sett.fields[SettingField.ID_VK_LYRICS] = settings_idVkLyrics
            sett.fields[SettingField.ID_VK_KARAOKE] = settings_idVkKaraoke
            sett.fields[SettingField.ID_VK_CHORDS] = settings_idVkChords
            sett.fields[SettingField.ID_TELEGRAM_LYRICS] = settings_idTelegramLyrics
            sett.fields[SettingField.ID_TELEGRAM_KARAOKE] = settings_idTelegramKaraoke
            sett.fields[SettingField.ID_TELEGRAM_CHORDS] = settings_idTelegramChords
            sett.fields[SettingField.ID_STATUS] = select_status
            sett.saveToDb()
            sett.saveToFile()
            if (settings_idBoosty != "") {
                sett.createVKDescription()
            }

        }
        return "redirect:/songs2"
    }
}
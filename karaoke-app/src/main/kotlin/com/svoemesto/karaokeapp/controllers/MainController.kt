package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.model.*
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
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

@Controller
class MainController {
    @GetMapping("/")
    fun main(): String {
        return "main"
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
        if (sourceText.trim() != "") {
            val settings = Settings.loadFromDbById(id)
            text = settings?.let {
                settings.setSourceText(voice, sourceText)
                settings.updateMarkersFromSourceText(voice)
                "OK"
            } ?: "Error"
        }
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

    @GetMapping("/song/{id}/doprocesslyricsbt")
    @ResponseBody
    fun doProcessLyricsBt(@PathVariable id: Long): Int {
        println("doProcessLyricsBt")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS_BT, true, 1)
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocesskaraokebt")
    @ResponseBody
    fun doProcessKaraokeBt(@PathVariable id: Long): Int {
        println("doProcessKaraokeBt")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            return KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE_BT, true, 1)
        }
        return 0
    }

    @GetMapping("/song/{id}/doprocesschordsbt")
    @ResponseBody
    fun doProcessChordsBt(@PathVariable id: Long): Int {
        println("doProcessChordsBt")
        val settings = Settings.loadFromDbById(id)
        settings?.let {
            if (Song(settings, SongVersion.LYRICS).hasChords) {
                return  KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS_BT, true, 1)
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
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS_BT, true, 10))
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE_BT, true, 10))
            if (hasChords) {
                result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS_BT, true, 10))
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
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE, true, 3))
            if (hasChords) {
                result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS, true, 3))
            }
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_LYRICS_BT, true, 3))
            result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_KARAOKE_BT, true, 3))
            if (hasChords) {
                result.add(KaraokeProcess.createProcess(settings, KaraokeProcessTypes.MELT_CHORDS_BT, true, 3))
            }
        }
        return result
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


    @GetMapping("/song/{id}/file")
    fun getSongFile(
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

    @GetMapping("/song/{id}/{voice}/editsubs")
    fun getSongEditSubs(@PathVariable id: Long, @PathVariable voice: Int, model: Model): String {
        val settings = Settings.loadFromDbById(id)
//        val markers = settings?.let {
//            Song(settings,SongVersion.LYRICS).getMarkers()
//        } ?: emptyList()

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
            val text = song.getDescription(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubelyricsbt")
    @ResponseBody
    fun getSongTextYoutubeLyricsBt(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescription(isBluetoothDelay = true)
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
            val text = song.getDescription(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubekaraokebt")
    @ResponseBody
    fun getSongTextYoutubeKaraokeBt(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescription(isBluetoothDelay = true)
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
            val text = song.getDescription(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubechordsbt")
    @ResponseBody
    fun getSongTextYoutubeChordsBt(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescription(isBluetoothDelay = true)
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
            val text = song.getDescriptionHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubelyricsbtheader")
    @ResponseBody
    fun getSongTextYoutubeLyricsBtHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubekaraokebtheader")
    @ResponseBody
    fun getSongTextYoutubeKaraokeBtHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubechordsbtheader")
    @ResponseBody
    fun getSongTextYoutubeChordsBtHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionYoutubeWOHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubelyricsbtwoheader")
    @ResponseBody
    fun getSongTextYoutubeLyricsBtWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionYoutubeWOHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionYoutubeWOHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubekaraokebtwoheader")
    @ResponseBody
    fun getSongTextYoutubeKaraokeBtWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionYoutubeWOHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionYoutubeWOHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textyoutubechordsbtwoheader")
    @ResponseBody
    fun getSongTextYoutubeChordsBtWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionYoutubeWOHeader(isBluetoothDelay = true)
            text
        } ?: ""
        return text
    }





    @GetMapping("/song/{id}/textvklyrics")
    @ResponseBody
    fun getSongTextVkLyrics(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVk(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvklyricsbt")
    @ResponseBody
    fun getSongTextVkLyricsBt(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVk(isBluetoothDelay = true)
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
            val text = song.getDescriptionVk(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkkaraokebt")
    @ResponseBody
    fun getSongTextVkKaraokeBt(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVk(isBluetoothDelay = true)
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
            val text = song.getDescriptionVk(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkchordsbt")
    @ResponseBody
    fun getSongTextVkChordsBt(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVk(isBluetoothDelay = true)
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
            val text = song.getDescriptionVkHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvklyricsbtheader")
    @ResponseBody
    fun getSongTextVkLyricsBtHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionVkHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkkaraokebtheader")
    @ResponseBody
    fun getSongTextVkKaraokeBtHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionVkHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkchordsbtheader")
    @ResponseBody
    fun getSongTextVkChordsBtHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVkHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionVkWOHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvklyricsbtwoheader")
    @ResponseBody
    fun getSongTextVkLyricsBtWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.LYRICS)
            val text = song.getDescriptionVkWOHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionVkWOHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkkaraokebtwoheader")
    @ResponseBody
    fun getSongTextVkKaraokeBtWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.KARAOKE)
            val text = song.getDescriptionVkWOHeader(isBluetoothDelay = true)
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
            val text = song.getDescriptionVkWOHeader(isBluetoothDelay = false)
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/textvkchordsbtwoheader")
    @ResponseBody
    fun getSongTextVkChordsBtWOHeader(@PathVariable id: Long): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            val song = Song(settings, SongVersion.CHORDS)
            val text = song.getDescriptionVkWOHeader(isBluetoothDelay = true)
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
            val text = song.getVKDescription()
            text
        } ?: ""
        return text
    }

    @GetMapping("/song/{id}/createkaraoke")
    fun getSongCreateKaraoke(@PathVariable id: Long, model: Model): String {
        val settings = Settings.loadFromDbById(id)
        val text = settings?.let {
            settings.createKaraoke()
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

    @GetMapping("/publications")
    fun publications(model: Model): String {
        model.addAttribute("publications", Publication.getPublicationList())
        return "publications"
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
        @RequestParam(required = false) filter_date: String?,
        @RequestParam(required = false) filter_time: String?,
        @RequestParam(required = false) flag_boosty: String?,
        @RequestParam(required = false) flag_vk: String?,
        @RequestParam(required = false) flag_youtube_lyrics: String?,
        @RequestParam(required = false) flag_youtube_lyrics_bt: String?,
        @RequestParam(required = false) flag_youtube_karaoke: String?,
        @RequestParam(required = false) flag_youtube_karaoke_bt: String?,
        @RequestParam(required = false) flag_youtube_chords: String?,
        @RequestParam(required = false) flag_youtube_chords_bt: String?,
        @RequestParam(required = false) flag_vk_lyrics: String?,
        @RequestParam(required = false) flag_vk_lyrics_bt: String?,
        @RequestParam(required = false) flag_vk_karaoke: String?,
        @RequestParam(required = false) flag_vk_karaoke_bt: String?,
        @RequestParam(required = false) flag_vk_chords: String?,
        @RequestParam(required = false) flag_vk_chords_bt: String?,
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
        flag_boosty?.let { if (flag_boosty != "") args["flag_boosty"] = flag_boosty }
        flag_vk?.let { if (flag_vk != "") args["flag_vk"] = flag_vk }
        flag_youtube_lyrics?.let { if (flag_youtube_lyrics != "") args["flag_youtube_lyrics"] = flag_youtube_lyrics }
        flag_youtube_lyrics_bt?.let { if (flag_youtube_lyrics_bt != "") args["flag_youtube_lyrics_bt"] = flag_youtube_lyrics_bt }
        flag_youtube_karaoke?.let { if (flag_youtube_karaoke != "") args["flag_youtube_karaoke"] = flag_youtube_karaoke }
        flag_youtube_karaoke_bt?.let { if (flag_youtube_karaoke_bt != "") args["flag_youtube_karaoke_bt"] = flag_youtube_karaoke_bt }
        flag_youtube_chords?.let { if (flag_youtube_chords != "") args["flag_youtube_chords"] = flag_youtube_chords }
        flag_youtube_chords_bt?.let { if (flag_youtube_chords_bt != "") args["flag_youtube_chords_bt"] = flag_youtube_chords_bt }
        flag_vk_lyrics?.let { if (flag_vk_lyrics != "") args["flag_vk_lyrics"] = flag_vk_lyrics }
        flag_vk_lyrics_bt?.let { if (flag_vk_lyrics_bt != "") args["flag_vk_lyrics_bt"] = flag_vk_lyrics_bt }
        flag_vk_karaoke?.let { if (flag_vk_karaoke != "") args["flag_vk_karaoke"] = flag_vk_karaoke }
        flag_vk_karaoke_bt?.let { if (flag_vk_karaoke_bt != "") args["flag_vk_karaoke_bt"] = flag_vk_karaoke_bt }
        flag_vk_chords?.let { if (flag_vk_chords != "") args["flag_vk_chords"] = flag_vk_chords }
        flag_vk_chords_bt?.let { if (flag_vk_chords_bt != "") args["flag_vk_chords_bt"] = flag_vk_chords_bt }
        model.addAttribute("sett", Settings.loadListFromDb(args))
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
        @RequestParam(required = false) settings_idYoutubeLyricsBt: String,
        @RequestParam(required = false) settings_idYoutubeKaraoke: String,
        @RequestParam(required = false) settings_idYoutubeKaraokeBt: String,
        @RequestParam(required = false) settings_idYoutubeChords: String,
        @RequestParam(required = false) settings_idYoutubeChordsBt: String,
        @RequestParam(required = false) settings_idVkLyrics: String,
        @RequestParam(required = false) settings_idVkLyricsBt: String,
        @RequestParam(required = false) settings_idVkKaraoke: String,
        @RequestParam(required = false) settings_idVkKaraokeBt: String,
        @RequestParam(required = false) settings_idVkChords: String,
        @RequestParam(required = false) settings_idVkChordsBt: String,
        @RequestParam(required = false) select_status: String,
        model: Model): String {
        val settingsId: Long = settings_id.toLong()
        val settings = Settings.loadFromDbById(settingsId)
        settings?.let { sett ->
            sett.fileName = settings_fileName
            sett.rootFolder = settings_rootFolder
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
            sett.fields[SettingField.ID_YOUTUBE_LYRICS_BT] = settings_idYoutubeLyricsBt
            sett.fields[SettingField.ID_YOUTUBE_KARAOKE] = settings_idYoutubeKaraoke
            sett.fields[SettingField.ID_YOUTUBE_KARAOKE_BT] = settings_idYoutubeKaraokeBt
            sett.fields[SettingField.ID_YOUTUBE_CHORDS] = settings_idYoutubeChords
            sett.fields[SettingField.ID_YOUTUBE_CHORDS_BT] = settings_idYoutubeChordsBt
            sett.fields[SettingField.ID_VK_LYRICS] = settings_idVkLyrics
            sett.fields[SettingField.ID_VK_LYRICS_BT] = settings_idVkLyricsBt
            sett.fields[SettingField.ID_VK_KARAOKE] = settings_idVkKaraoke
            sett.fields[SettingField.ID_VK_KARAOKE_BT] = settings_idVkKaraokeBt
            sett.fields[SettingField.ID_VK_CHORDS] = settings_idVkChords
            sett.fields[SettingField.ID_VK_CHORDS_BT] = settings_idVkChordsBt
            sett.fields[SettingField.ID_STATUS] = select_status
            sett.saveToDb()
            sett.saveToFile()
            if (settings_idBoosty != "" && settings_idYoutubeLyrics != "" && settings_idYoutubeLyricsBt != "" && settings_idYoutubeKaraoke != "" && settings_idYoutubeKaraokeBt !="") {
                sett.createVKDescription()
            }
        }
        return "redirect:/songs"
    }



}
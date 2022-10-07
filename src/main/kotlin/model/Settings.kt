package model

import getFileNameByMasks
import java.io.File
import kotlin.io.path.Path

data class Settings(val _pathToSettingsFile: String) {
    var songName: String = ""
    var author: String = ""
    var album: String = ""
    var year: Long = 0
    var track: Long = 0
    var key: String = ""
    var bpm: Long = 0
    var ms: Long = 0
    var rootFolder: String = ""
    var fileName: String = ""
    var subtitleFileName: String = ""
    var audioSongFileName: String = ""
    var audioMusicFileName: String = ""
    var audioVocalFileName: String = ""
    var audioBassFileName: String = ""
    var audioDrumsFileName: String = ""
    var projectLyricsFileName: String = ""
    var videoLyricsFileName: String = ""
    var projectKaraokeFileName: String = ""
    var videoKaraokeFileName: String = ""
    var projectChordsFileName: String = ""
    var videoChordsFileName: String = ""
    init {
        val settingFilePath = Path(_pathToSettingsFile)
        val settingRoot = settingFilePath.parent.toString()
        val settingFileNameList = settingFilePath.fileName.toString()
            .split(".")
            .toMutableList()
        settingFileNameList.removeLast()
        val settingFileName = settingFileNameList.joinToString(".")

        rootFolder = settingRoot
        fileName = settingFileName
        subtitleFileName = "${settingFileName}.kdenlive.srt"
        audioSongFileName = "${settingFileName}.flac"
        projectLyricsFileName = "$settingFileName [lyrics].kdenlive"
        videoLyricsFileName = "done/$settingFileName [lyrics].mp4"
        projectKaraokeFileName = "$settingFileName [karaoke].kdenlive"
        videoKaraokeFileName = "done/$settingFileName [karaoke].mp4"
        projectChordsFileName = "$settingFileName [chords].kdenlive"
        videoChordsFileName = "done/$settingFileName [chords].mp4"

        val body = File(_pathToSettingsFile).readText(Charsets.UTF_8)
        body.split("\n").forEach { line ->
            val settingList = line.split("=")
            if (settingList.size > 1 ) {
                val settingName = settingList[0].uppercase()
                val settingValue = settingList[1]
                val settingValue2 = if (settingList.size == 3) "="+settingList[2] else ""
                when (settingName) {
                    "NAME" -> songName = settingValue+settingValue2
                    "AUTHOR" -> author = settingValue
                    "ALBUM" -> album = settingValue
                    "YEAR" -> year = settingValue.toLong()
                    "TRACK" -> track = settingValue.toLong()
                    "KEY" -> key = settingValue
                    "BPM" -> bpm = settingValue.toLong()
                    "MS" -> ms = settingValue.toLong()
                    "FORMAT" -> audioSongFileName = "${settingFileName}.${settingValue}"
                    "AUDIOSONG" -> audioSongFileName = settingValue
                    "AUDIOMUSIC" -> audioMusicFileName = settingValue
                    "AUDIOVOCALS" -> audioVocalFileName = settingValue
                    "AUDIODRUMS" -> audioDrumsFileName = settingValue
                    "AUDIOBASS" -> audioBassFileName = settingValue
                }
            }
        }

        if (audioMusicFileName == "") audioMusicFileName = getFileNameByMasks(rootFolder,fileName, listOf("-accompaniment-"," [music]"),".wav")
        if (audioVocalFileName == "") audioVocalFileName = getFileNameByMasks(rootFolder,fileName, listOf("-vocals-"," [vocals]"),".wav")
        if (audioBassFileName == "") audioBassFileName = getFileNameByMasks(rootFolder,fileName, listOf("-bass-"," [bass]"),".wav")
        if (audioDrumsFileName == "") audioDrumsFileName = getFileNameByMasks(rootFolder,fileName, listOf("-drums-"," [drums]"),".wav")

    }
}
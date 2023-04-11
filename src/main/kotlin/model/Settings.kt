package model

import DEMUCS_MODEL_NAME
import PATH_TO_ODS
import URL_PREFIX_BOOSTY
import URL_PREFIX_YOUTUBE_EDIT
import URL_PREFIX_YOUTUBE_PLAY
import getFileNameByMasks
import org.odftoolkit.simple.SpreadsheetDocument
import java.io.File
import java.io.Serializable
import kotlin.io.path.Path

enum class SettingField : Serializable {
    NAME,
    AUTHOR,
    ALBUM,
    YEAR,
    TRACK,
    KEY,
    BPM,
    MS,
    FORMAT,
    AUDIOSONG,
    AUDIOMUSIC,
    AUDIOVOCALS,
    AUDIODRUMS,
    AUDIOBASS,
    DATE,
    TIME,
    BOOSTY_ONLY,
    ID_BOOSTY,
    ID_YOUTUBE_LYRICS,
    ID_YOUTUBE_LYRICS_BT,
    ID_YOUTUBE_KARAOKE,
    ID_YOUTUBE_KARAOKE_BT,
    ID_YOUTUBE_CHORDS,
    ID_YOUTUBE_CHORDS_BT,
}
data class Settings(val _pathToSettingsFile: String, val spreadsheetDocument: SpreadsheetDocument? = null) : Serializable {
    var rootFolder: String
    var fileName: String
    val fields: MutableMap<SettingField, String> = mutableMapOf()

    val songName: String get() = fields[SettingField.NAME] ?: ""
    val author: String get() = fields[SettingField.AUTHOR] ?: ""
    val album: String get() = fields[SettingField.ALBUM] ?: ""
    val date: String get() = fields[SettingField.DATE] ?: ""
    val time: String get() = fields[SettingField.TIME] ?: ""
    val year: Long get() = fields[SettingField.YEAR]?.toLongOrNull() ?: 0L
    val track: Long get() = fields[SettingField.TRACK]?.toLongOrNull() ?: 0L
    val key: String get() = fields[SettingField.KEY] ?: ""
    val bpm: Long get() = fields[SettingField.BPM]?.toLongOrNull() ?: 0L
    val ms: Long get() = fields[SettingField.MS]?.toLongOrNull() ?: 0L
    val subtitleFileName: String get() = "${fileName}.kdenlive.srt"
    val audioSongFileName: String get() =
        if (fields.contains(SettingField.FORMAT)) {
            "${fileName}.${fields[SettingField.FORMAT]}"
        } else {
            if (fields.contains(SettingField.AUDIOSONG)) {
                fields[SettingField.AUDIOSONG] ?: ""
            } else {
                "${fileName}.flac"
            }
        }
    val projectLyricsFileName: String get() = "$fileName [lyrics].kdenlive"
    val videoLyricsFileName: String get() = "done/$year $fileName [lyrics].mp4"
    val projectKaraokeFileName: String get() = "$fileName [karaoke].kdenlive"
    val videoKaraokeFileName: String get() = "done/$year $fileName [karaoke].mp4"
    val projectChordsFileName: String get() = "$fileName [chords].kdenlive"
    val videoChordsFileName: String get() = "done/$year $fileName [chords].mp4"
    val audioMusicFileName: String get() =
        if (fields.contains(SettingField.AUDIOMUSIC)) {
            fields[SettingField.AUDIOMUSIC] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-accompaniment"," [music]"),".wav")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-accompaniment"," [music]"),".wav")
            } else {
                tmp
            }
        }
    val audioVocalFileName: String get() =
        if (fields.contains(SettingField.AUDIOVOCALS)) {
            fields[SettingField.AUDIOVOCALS] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-vocals"," [vocals]"),".wav")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-vocals"," [vocals]"),".wav")
            } else {
                tmp
            }
        }
    val audioBassFileName: String get() =
        if (fields.contains(SettingField.AUDIOBASS)) {
            fields[SettingField.AUDIOBASS] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-bass"," [bass]"),".wav")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-bass"," [bass]"),".wav")
            } else {
                tmp
            }
        }
    val audioDrumsFileName: String get() =
        if (fields.contains(SettingField.AUDIODRUMS)) {
            fields[SettingField.AUDIODRUMS] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-drums"," [drums]"),".wav")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-drums"," [drums]"),".wav")
            } else {
                tmp
            }
        }

    val idBoosty: String? get() = fields[SettingField.ID_BOOSTY]
    val idYoutubeLyrics: String? get() = fields[SettingField.ID_YOUTUBE_LYRICS]
    val idYoutubeLyricsBt: String? get() = fields[SettingField.ID_YOUTUBE_LYRICS_BT]
    val idYoutubeKaraoke: String? get() = fields[SettingField.ID_YOUTUBE_KARAOKE]
    val idYoutubeKaraokeBt: String? get() = fields[SettingField.ID_YOUTUBE_KARAOKE_BT]
    val idYoutubeChords: String? get() = fields[SettingField.ID_YOUTUBE_CHORDS]
    val idYoutubeChordsBt: String? get() = fields[SettingField.ID_YOUTUBE_CHORDS_BT]

    val linkBoosty: String? get() = idBoosty?.let {URL_PREFIX_BOOSTY.replace("{REPLACE}", idBoosty!!)}
    val linkYoutubeLyricsPlay: String? get() = idYoutubeLyrics?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeLyrics!!)}
    val linkYoutubeLyricsEdit: String? get() = idYoutubeLyrics?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeLyrics!!)}
    val linkYoutubeLyricsBtPlay: String? get() = idYoutubeLyricsBt?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeLyricsBt!!)}
    val linkYoutubeLyricsBtEdit: String? get() = idYoutubeLyricsBt?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeLyricsBt!!)}

    val linkYoutubeKaraokePlay: String? get() = idYoutubeKaraoke?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeKaraoke!!)}
    val linkYoutubeKaraokeEdit: String? get() = idYoutubeKaraoke?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeKaraoke!!)}
    val linkYoutubeKaraokeBtPlay: String? get() = idYoutubeKaraokeBt?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeKaraokeBt!!)}
    val linkYoutubeKaraokeBtEdit: String? get() = idYoutubeKaraokeBt?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeKaraokeBt!!)}

    val linkYoutubeChordsPlay: String? get() = idYoutubeChords?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeChords!!)}
    val linkYoutubeChordsEdit: String? get() = idYoutubeChords?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeChords!!)}
    val linkYoutubeChordsBtPlay: String? get() = idYoutubeChordsBt?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeChordsBt!!)}
    val linkYoutubeChordsBtEdit: String? get() = idYoutubeChordsBt?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeChordsBt!!)}

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

        val body = File(_pathToSettingsFile).readText(Charsets.UTF_8)
        body.split("\n").forEach { line ->
            val settingList = line.split("=")
            if (settingList.size > 1 ) {
                val settingName = settingList[0].uppercase()
                val settingValue = settingList[1] + (if (settingList.size == 3) "="+settingList[2] else "")
                val settingField = if (SettingField.values().map { it.name }.contains(settingName)) SettingField.valueOf(settingName) else null
                settingField?.let { fields[settingField] = settingValue.trim() }
            }
        }

        spreadsheetDocument?.let {
            val mapFromDb = Ods.getSettingFields(author, songName, spreadsheetDocument)
            mapFromDb?.let {
                mapFromDb.forEach{(mapKey, mapValue) ->
                    fields[mapKey] = mapValue
                }
            }
            save()
        }

    }

    companion object {
        fun updateFromDb(pathToSettingsFile: String, spreadsheetDocument: SpreadsheetDocument) {
            Settings(pathToSettingsFile, spreadsheetDocument)
        }
        fun updateFromDb(settings: Settings, spreadsheetDocument: SpreadsheetDocument) {
            Settings(settings._pathToSettingsFile, spreadsheetDocument)
        }
        fun updateFromDb(pathToSettingsFile: String) {
            val spreadsheetDocument = SpreadsheetDocument.loadDocument(File(PATH_TO_ODS))
            Settings(pathToSettingsFile, spreadsheetDocument)
            spreadsheetDocument.close()
        }
        fun updateFromDb(settings: Settings) {
            val spreadsheetDocument = SpreadsheetDocument.loadDocument(File(PATH_TO_ODS))
            Settings(settings._pathToSettingsFile, spreadsheetDocument)
            spreadsheetDocument.close()
        }

    }

    fun save() {
        var txt = ""
        SettingField.values().forEach { settingField ->
            if (fields.contains(settingField)) txt += "${settingField.name}=${fields[settingField]}\n"
        }
        File(_pathToSettingsFile).writeText(txt)
    }

}
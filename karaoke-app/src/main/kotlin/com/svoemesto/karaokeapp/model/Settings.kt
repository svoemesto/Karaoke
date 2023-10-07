package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.odftoolkit.simple.SpreadsheetDocument
import org.springframework.beans.BeansException
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.Path
import org.springframework.stereotype.Component
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import java.sql.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date

enum class SettingField : Serializable {
    ID,
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
    ID_VK,
    ID_YOUTUBE_LYRICS,
//    ID_YOUTUBE_LYRICS_BT,
    ID_YOUTUBE_KARAOKE,
//    ID_YOUTUBE_KARAOKE_BT,
    ID_YOUTUBE_CHORDS,
//    ID_YOUTUBE_CHORDS_BT,
    ID_VK_LYRICS,
//    ID_VK_LYRICS_BT,
    ID_VK_KARAOKE,
//    ID_VK_KARAOKE_BT,
    ID_VK_CHORDS,
//    ID_VK_CHORDS_BT,
    ID_STATUS,
    COLOR,
    SOURCE_TEXT,
    SOURCE_MARKERS,
    ID_TELEGRAM_LYRICS,
//    ID_TELEGRAM_LYRICS_BT,
    ID_TELEGRAM_KARAOKE,
//    ID_TELEGRAM_KARAOKE_BT,
    ID_TELEGRAM_CHORDS,
//    ID_TELEGRAM_CHORDS_BT
}


@kotlinx.serialization.Serializable
data class SourceMarker(
    var time: Double,
    var label: String,
    var color: String,
    var position: String,
    var markertype: String
) {

}

@Component
class ApplicationContextProvider : ApplicationContextAware {
    companion object {
        private lateinit var context: ApplicationContext

        fun getCurrentApplicationContext(): ApplicationContext {
            return context
        }
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(context: ApplicationContext) {
        Companion.context = context
    }
}

@Component
class Settings : Serializable, Comparable<Settings> {


    private var _rootFolder: String = ""
    var readonly = false


    var rootFolder: String
        get() {
            if (_rootFolder == "") return _rootFolder
            if (File(_rootFolder).exists()) return _rootFolder
            PROJECT_ROOT_FOLDERS.firstOrNull() { _rootFolder.startsWith(it) }?.let {
                val subRootFolder = _rootFolder.substring(it.length)
                PROJECT_ROOT_FOLDERS.forEach { rsf ->
                    if (File("$rsf$subRootFolder").exists()) {
                        _rootFolder = "$rsf$subRootFolder"
                        return@let
                    }
                }
            }
            return _rootFolder
        }
        set(value) {_rootFolder = value}


    var fileName: String = ""
    var tags: String = ""
    val fields: MutableMap<SettingField, String> = mutableMapOf()

    var firstSongInAlbum: Boolean = false

    val id: Long get() = fields[SettingField.ID]?.toLongOrNull() ?: 0L
    val idStatus: Long get() = fields[SettingField.ID_STATUS]?.toLongOrNull() ?: 0L
    val status: String get() {
          return when (idStatus) {
              0L -> "NONE"
              1L -> "TEXT_CREATE"
              2L -> "TEXT_CHECK"
              3L -> "PROJECT_CREATE"
              4L -> "PROJECT_CHECK"
              5L -> "RENDERING"
              6L -> "DONE"
              else -> "N/A"
          }
    }

    var sourceText: String
        get() {
            val txt = fields[SettingField.SOURCE_TEXT] ?: ""
            return txt
//            return if (txt == "") {
//                val fileName = "$rootFolder/$fileName.txt"
//                val file = File(fileName)
//                if (file.exists()) {
//                    val fileBody = file.readText(Charsets.UTF_8)
//                    if (fileBody.trim() != "") {
//                        fields[SettingField.SOURCE_TEXT] = Json.encodeToString(listOf(fileBody))
//                        saveToDb()
//                        fields[SettingField.SOURCE_TEXT]!!
//                    } else {
//                       txt
//                    }
//                } else {
//                    txt
//                }
//            } else {
//                txt
//            }
        }
        set(value) {fields[SettingField.SOURCE_TEXT] = value}

    var statusProcessLyrics: String = ""
    var statusProcessLyricsBt: String = ""
    var statusProcessKaraoke: String = ""
    var statusProcessKaraokeBt: String = ""
    var statusProcessChords: String = ""
    var statusProcessChordsBt: String = ""

    val sourceTextList: List<String>
        get() {
            return try {
                Json.decodeFromString(ListSerializer(String.serializer()), sourceText)
            } catch (e: Exception) {
                val data = listOf(sourceText)
//                fields[SettingField.SOURCE_TEXT] = Json.encodeToString(data)
//                saveToDb()
                data
            }
        }

    val sourceSyllables: String get() {
        return Json.encodeToString(sourceSyllablesList)
    }
    val sourceSyllablesList: List<List<String>> get() {
        val result: MutableList<MutableList<String>> = mutableListOf()
        sourceTextList.forEachIndexed{ index, _ ->
            val voiceText = getSourceText(index)
            val words = voiceText.replace("\n"," ").split(" ").filter { it != "" }
            val slogs: MutableList<String> = mutableListOf()
            val mainRibbon = MainRibbon()
            var addBefore = ""
            words.forEach { word ->
                val wordSlogs: MutableList<String> = mainRibbon.syllables(word).toMutableList()
                if (wordSlogs.isEmpty()) {
                    addBefore += "${word}_"
                } else {
                    if (wordSlogs.joinToString("") != word) wordSlogs[wordSlogs.size-1] = wordSlogs[wordSlogs.size-1] + word.substring(wordSlogs.joinToString("").length)
                    wordSlogs[0] = addBefore + wordSlogs[0]
                    addBefore = ""
                    wordSlogs[wordSlogs.size-1] = wordSlogs[wordSlogs.size-1] + "_"
                    slogs.addAll(wordSlogs)
                }
            }
            result.add(slogs)
        }
        return result
    }

    var sourceMarkers: String
        get() = if (fields[SettingField.SOURCE_MARKERS] == null || fields[SettingField.SOURCE_MARKERS] == "") "[[]]" else fields[SettingField.SOURCE_MARKERS]!!
        set(value) {fields[SettingField.SOURCE_MARKERS] = value}
    val sourceMarkersList: List<List<SourceMarker>> get() {
        return try {
            Json.decodeFromString(ListSerializer(ListSerializer(SourceMarker.serializer())), sourceMarkers)
        } catch (e: Exception) {
            val data = listOf(Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers))
            sourceMarkers = Json.encodeToString(data)
            saveToDb()
            data
        }
    }

//    fun getSourceMarkersList(): List<SourceMarker> {
//        return if (sourceMarkers != "") {
//            Json.decodeFromString(ListSerializer(SourceMarker.serializer()), sourceMarkers)
//        } else {
//            emptyList()
//        }
//    }

//    var sourceMarkers: List<String>
//        get() {
//            return if (fields[SettingField.SOURCE_MARKERS] == null || fields[SettingField.SOURCE_MARKERS] == "") {
//                emptyList()
//            } else {
//                fields[SettingField.SOURCE_MARKERS] ?: "[]"
//            }
//
//        }
//        set(value) {fields[SettingField.SOURCE_MARKERS] = value}


    val pathToFileLyrics: String  get() = "${rootFolder.replace("'","''")}/done_files/$nameFileLyrics"
    val pathToFileKaraoke: String  get() = "${rootFolder.replace("'","''")}/done_files/$nameFileKaraoke"
    val pathToFileChords: String  get() = "${rootFolder.replace("'","''")}/done_files/$nameFileChords"
    val pathToFileLyricsBt: String  get() = "${rootFolder.replace("'","''")}/done_files/$nameFileLyricsBt"
    val pathToFileKaraokeBt: String  get() = "${rootFolder.replace("'","''")}/done_files/$nameFileKaraokeBt"
    val pathToFileChordsBt: String  get() = "${rootFolder.replace("'","''")}/done_files/$nameFileChordsBt"

    val pathToFile720Lyrics: String  get() = "$pathToFolder720Lyrics/${nameFileLyrics.replace(" [lyrics].mp4", " [lyrics] 720p.mp4")}"
    val pathToFile720Karaoke: String  get() = "$pathToFolder720Karaoke/${nameFileKaraoke.replace(" [karaoke].mp4", " [karaoke] 720p.mp4")}"
    val pathToFile720Chords: String  get() = "$pathToFolder720Chords/${nameFileChords.replace(" [chords].mp4", " [chords] 720p.mp4")}"
    val pathToFile720LyricsBt: String  get() = "$pathToFolder720LyricsBt/${nameFileLyricsBt.replace(" [lyrics] bluetooth.mp4", " [lyrics] bluetooth 720p.mp4")}"
    val pathToFile720KaraokeBt: String  get() = "$pathToFolder720KaraokeBt/${nameFileKaraokeBt.replace(" [karaoke] bluetooth.mp4", " [karaoke] bluetooth 720p.mp4")}"
    val pathToFile720ChordsBt: String  get() = "$pathToFolder720ChordsBt/${nameFileChordsBt.replace(" [chords] bluetooth.mp4", " [chords] bluetooth 720p.mp4")}"

    val pathToFolder720Lyrics: String  get() = "$PATH_TO_STORE_FOLDER/720p_Lyrics/${author} 720p"
    val pathToFolder720Karaoke: String  get() = "$PATH_TO_STORE_FOLDER/720p_Karaoke/${author} 720p"
    val pathToFolder720Chords: String  get() = "$PATH_TO_STORE_FOLDER/720p_Chords/${author} 720p"
    val pathToFolder720LyricsBt: String  get() = "$PATH_TO_STORE_FOLDER/720p_LyricsBt/${author} 720p"
    val pathToFolder720KaraokeBt: String  get() = "$PATH_TO_STORE_FOLDER/720p_KaraokeBt/${author} 720p"
    val pathToFolder720ChordsBt: String  get() = "$PATH_TO_STORE_FOLDER/720p_ChordsBt/${author} 720p"

    val pathToStoreFileLyrics: String  get() = "$pathToStoreFolderLyrics/$nameFileLyrics"
    val pathToStoreFileKaraoke: String  get() = "$pathToStoreFolderKaraoke/$nameFileKaraoke"
    val pathToStoreFileChords: String  get() = "$pathToStoreFolderChords/$nameFileChords"
    val pathToStoreFileLyricsBt: String  get() = "$pathToStoreFolderLyricsBt/$nameFileLyricsBt"
    val pathToStoreFileKaraokeBt: String  get() = "$pathToStoreFolderKaraokeBt/$nameFileKaraokeBt"
    val pathToStoreFileChordsBt: String  get() = "$pathToStoreFolderChordsBt/$nameFileChordsBt"

    val pathToStoreFolderLyrics: String  get() = "$PATH_TO_STORE_FOLDER/Lyrics/${author} - Lyrics"
    val pathToStoreFolderKaraoke: String  get() = "$PATH_TO_STORE_FOLDER/Karaoke/${author} - Karaoke"
    val pathToStoreFolderChords: String  get() = "$PATH_TO_STORE_FOLDER/Chords/${author} - Chords"
    val pathToStoreFolderLyricsBt: String  get() = "$PATH_TO_STORE_FOLDER/LyricsBt/${author} - LyricsBt"
    val pathToStoreFolderKaraokeBt: String  get() = "$PATH_TO_STORE_FOLDER/KaraokeBt/${author} - KaraokeBt"
    val pathToStoreFolderChordsBt: String  get() = "$PATH_TO_STORE_FOLDER/ChordsBt/${author} - ChordsBt"


    val nameFileLyrics: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.replace("'","''")} [lyrics].mp4"
    val nameFileKaraoke: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.replace("'","''")} [karaoke].mp4"
    val nameFileChords: String  get() = "$${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.replace("'","''")} [chords].mp4"
    val nameFileLyricsBt: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.replace("'","''")} [lyrics] bluetooth.mp4"
    val nameFileKaraokeBt: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.replace("'","''")} [karaoke] bluetooth.mp4"
    val nameFileChordsBt: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.replace("'","''")} [chords] bluetooth.mp4"


    val color: String get() = fields[SettingField.COLOR] ?: ""
    val songName: String get() = fields[SettingField.NAME] ?: ""
    val author: String get() = fields[SettingField.AUTHOR] ?: ""
    val album: String get() = fields[SettingField.ALBUM] ?: ""
    val date: String get() = fields[SettingField.DATE] ?: ""
    val time: String get() = fields[SettingField.TIME] ?: ""
    val dateTimePublish: Date? get() {
        return if (date == "" || time == "") {
            null
        } else {
            SimpleDateFormat("dd.MM.yy HH:mm").parse("$date $time")
        }
    }
    val year: Long get() = fields[SettingField.YEAR]?.toLongOrNull() ?: 0L
    val track: Long get() = fields[SettingField.TRACK]?.toLongOrNull() ?: 0L
    val key: String get() = fields[SettingField.KEY] ?: ""
    val bpm: Long get() = fields[SettingField.BPM]?.toLongOrNull() ?: 0L
    val ms: Long get() = fields[SettingField.MS]?.toLongOrNull() ?: 0L
    val subtitleFileName: String get() = "${fileName}.kdenlive.srt"
    val audioSongFileName: String get() = "${fileName}.flac"
//        if (fields.contains(SettingField.FORMAT)) {
//            "${fileName}.${fields[SettingField.FORMAT]}"
//        } else {
//            if (fields.contains(SettingField.AUDIOSONG)) {
//                fields[SettingField.AUDIOSONG] ?: ""
//            } else {
//                "${fileName}.flac"
//            }
//        }
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
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-accompaniment"," [music]"),".flac")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-accompaniment"," [music]"),".flac")
            } else {
                tmp
            }
        }
    val audioVocalFileName: String get() =
        if (fields.contains(SettingField.AUDIOVOCALS)) {
            fields[SettingField.AUDIOVOCALS] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-vocals"," [vocals]"),".flac")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-vocals"," [vocals]"),".flac")
            } else {
                tmp
            }
        }
    val audioBassFileName: String get() =
        if (fields.contains(SettingField.AUDIOBASS)) {
            fields[SettingField.AUDIOBASS] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-bass"," [bass]"),".flac")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-bass"," [bass]"),".flac")
            } else {
                tmp
            }
        }
    val audioDrumsFileName: String get() =
        if (fields.contains(SettingField.AUDIODRUMS)) {
            fields[SettingField.AUDIODRUMS] ?: ""
        } else {
            val tmp = getFileNameByMasks(rootFolder,fileName, listOf("-drums"," [drums]"),".flac")
            if (tmp == "") {
                DEMUCS_MODEL_NAME + "/" + getFileNameByMasks("$rootFolder/$DEMUCS_MODEL_NAME",fileName, listOf("-drums"," [drums]"),".flac")
            } else {
                tmp
            }
        }

    val idBoosty: String get() = fields[SettingField.ID_BOOSTY]?.nullIfEmpty() ?: ""
    val idVk: String get() = fields[SettingField.ID_VK]?.nullIfEmpty() ?: ""
    val idYoutubeLyrics: String get() = fields[SettingField.ID_YOUTUBE_LYRICS]?.nullIfEmpty() ?: ""
//    val idYoutubeLyricsBt: String get() = fields[SettingField.ID_YOUTUBE_LYRICS_BT]?.nullIfEmpty() ?: ""
    val idYoutubeKaraoke: String get() = fields[SettingField.ID_YOUTUBE_KARAOKE]?.nullIfEmpty() ?: ""
//    val idYoutubeKaraokeBt: String get() = fields[SettingField.ID_YOUTUBE_KARAOKE_BT]?.nullIfEmpty() ?: ""
    val idYoutubeChords: String get() = fields[SettingField.ID_YOUTUBE_CHORDS]?.nullIfEmpty() ?: ""
//    val idYoutubeChordsBt: String get() = fields[SettingField.ID_YOUTUBE_CHORDS_BT]?.nullIfEmpty() ?: ""

    val idVkLyrics: String get() = fields[SettingField.ID_VK_LYRICS]?.nullIfEmpty() ?: ""
//    val idVkLyricsBt: String get() = fields[SettingField.ID_VK_LYRICS_BT]?.nullIfEmpty() ?: ""
    val idVkKaraoke: String get() = fields[SettingField.ID_VK_KARAOKE]?.nullIfEmpty() ?: ""
//    val idVkKaraokeBt: String get() = fields[SettingField.ID_VK_KARAOKE_BT]?.nullIfEmpty() ?: ""
    val idVkChords: String get() = fields[SettingField.ID_VK_CHORDS]?.nullIfEmpty() ?: ""
//    val idVkChordsBt: String get() = fields[SettingField.ID_VK_CHORDS_BT]?.nullIfEmpty() ?: ""

    val idTelegramLyrics: String get() = fields[SettingField.ID_TELEGRAM_LYRICS]?.nullIfEmpty() ?: ""
//    val idTelegramLyricsBt: String get() = fields[SettingField.ID_TELEGRAM_LYRICS_BT]?.nullIfEmpty() ?: ""
    val idTelegramKaraoke: String get() = fields[SettingField.ID_TELEGRAM_KARAOKE]?.nullIfEmpty() ?: ""
//    val idTelegramKaraokeBt: String get() = fields[SettingField.ID_TELEGRAM_KARAOKE_BT]?.nullIfEmpty() ?: ""
    val idTelegramChords: String get() = fields[SettingField.ID_TELEGRAM_CHORDS]?.nullIfEmpty() ?: ""
//    val idTelegramChordsBt: String get() = fields[SettingField.ID_TELEGRAM_CHORDS_BT]?.nullIfEmpty() ?: ""
    
    val linkBoosty: String? get() = idBoosty?.let {URL_PREFIX_BOOSTY.replace("{REPLACE}", idBoosty!!)}
    val linkVk: String? get() = idBoosty?.let {URL_PREFIX_VK.replace("{REPLACE}", idBoosty!!)}
    val linkYoutubeLyricsPlay: String? get() = idYoutubeLyrics?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeLyrics!!)}
    val linkYoutubeLyricsEdit: String? get() = idYoutubeLyrics?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeLyrics!!)}
//    val linkYoutubeLyricsBtPlay: String? get() = idYoutubeLyricsBt?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeLyricsBt!!)}
//    val linkYoutubeLyricsBtEdit: String? get() = idYoutubeLyricsBt?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeLyricsBt!!)}

    val linkYoutubeKaraokePlay: String? get() = idYoutubeKaraoke?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeKaraoke!!)}
    val linkYoutubeKaraokeEdit: String? get() = idYoutubeKaraoke?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeKaraoke!!)}
//    val linkYoutubeKaraokeBtPlay: String? get() = idYoutubeKaraokeBt?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeKaraokeBt!!)}
//    val linkYoutubeKaraokeBtEdit: String? get() = idYoutubeKaraokeBt?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeKaraokeBt!!)}

    val linkYoutubeChordsPlay: String? get() = idYoutubeChords?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeChords!!)}
    val linkYoutubeChordsEdit: String? get() = idYoutubeChords?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeChords!!)}
//    val linkYoutubeChordsBtPlay: String? get() = idYoutubeChordsBt?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeChordsBt!!)}
//    val linkYoutubeChordsBtEdit: String? get() = idYoutubeChordsBt?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeChordsBt!!)}

    val linkVkLyricsPlay: String? get() = idVkLyrics?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkLyrics!!)}
    val linkVkLyricsEdit: String? get() = idVkLyrics?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkLyrics!!)}
//    val linkVkLyricsBtPlay: String? get() = idVkLyricsBt?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkLyricsBt!!)}
//    val linkVkLyricsBtEdit: String? get() = idVkLyricsBt?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkLyricsBt!!)}

    val linkVkKaraokePlay: String? get() = idVkKaraoke?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkKaraoke!!)}
    val linkVkKaraokeEdit: String? get() = idVkKaraoke?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkKaraoke!!)}
//    val linkVkKaraokeBtPlay: String? get() = idVkKaraokeBt?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkKaraokeBt!!)}
//    val linkVkKaraokeBtEdit: String? get() = idVkKaraokeBt?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkKaraokeBt!!)}
    
    val linkVkChordsPlay: String? get() = idVkChords?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkChords!!)}
    val linkVkChordsEdit: String? get() = idVkChords?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkChords!!)}
//    val linkVkChordsBtPlay: String? get() = idVkChordsBt?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkChordsBt!!)}
//    val linkVkChordsBtEdit: String? get() = idVkChordsBt?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkChordsBt!!)}

    val linkTelegramLyricsPlay: String? get() = idTelegramLyrics?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramLyrics!!)}
    val linkTelegramLyricsEdit: String? get() = idTelegramLyrics?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramLyrics!!)}
//    val linkTelegramLyricsBtPlay: String? get() = idTelegramLyricsBt?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramLyricsBt!!)}
//    val linkTelegramLyricsBtEdit: String? get() = idTelegramLyricsBt?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramLyricsBt!!)}

    val linkTelegramKaraokePlay: String? get() = idTelegramKaraoke?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramKaraoke!!)}
    val linkTelegramKaraokeEdit: String? get() = idTelegramKaraoke?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramKaraoke!!)}
//    val linkTelegramKaraokeBtPlay: String? get() = idTelegramKaraokeBt?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramKaraokeBt!!)}
//    val linkTelegramKaraokeBtEdit: String? get() = idTelegramKaraokeBt?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramKaraokeBt!!)}

    val linkTelegramChordsPlay: String? get() = idTelegramChords?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramChords!!)}
    val linkTelegramChordsEdit: String? get() = idTelegramChords?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramChords!!)}
//    val linkTelegramChordsBtPlay: String? get() = idTelegramChordsBt?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramChordsBt!!)}
//    val linkTelegramChordsBtEdit: String? get() = idTelegramChordsBt?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramChordsBt!!)}

    val flagBoosty: String get() = if (idBoosty == null || idBoosty == "null" || idBoosty == "") "" else "✓"
    val flagVk: String get() = if (idVk == null || idVk == "null" || idVk == "") "" else "✓"
    val flagYoutubeLyrics: String get() = if (idYoutubeLyrics == null || idYoutubeLyrics == "null" || idYoutubeLyrics == "") "" else "✓"
//    val flagYoutubeLyricsBt: String get() = if (idYoutubeLyricsBt == null || idYoutubeLyricsBt == "null" || idYoutubeLyricsBt == "") "" else "✓"
    val flagYoutubeKaraoke: String get() = if (idYoutubeKaraoke == null || idYoutubeKaraoke == "null" || idYoutubeKaraoke == "") "" else "✓"
//    val flagYoutubeKaraokeBt: String get() = if (idYoutubeKaraokeBt == null || idYoutubeKaraokeBt == "null" || idYoutubeKaraokeBt == "") "" else "✓"
    val flagYoutubeChords: String get() = if (idYoutubeChords == null || idYoutubeChords == "null" || idYoutubeChords == "") "" else "✓"
//    val flagYoutubeChordsBt: String get() = if (idYoutubeChordsBt == null || idYoutubeChordsBt == "null" || idYoutubeChordsBt == "") "" else "✓"

    val flagVkLyrics: String get() = if (idVkLyrics == null || idVkLyrics == "null" || idVkLyrics == "") "" else "✓"
//    val flagVkLyricsBt: String get() = if (idVkLyricsBt == null || idVkLyricsBt == "null" || idVkLyricsBt == "") "" else "✓"
    val flagVkKaraoke: String get() = if (idVkKaraoke == null || idVkKaraoke == "null" || idVkKaraoke == "") "" else "✓"
//    val flagVkKaraokeBt: String get() = if (idVkKaraokeBt == null || idVkKaraokeBt == "null" || idVkKaraokeBt == "") "" else "✓"
    val flagVkChords: String get() = if (idVkChords == null || idVkChords == "null" || idVkChords == "") "" else "✓"
//    val flagVkChordsBt: String get() = if (idVkChordsBt == null || idVkChordsBt == "null" || idVkChordsBt == "") "" else "✓"


    val flagTelegramLyrics: String get() = if (idTelegramLyrics == null || idTelegramLyrics == "null" || idTelegramLyrics == "") "" else "✓"
//    val flagTelegramLyricsBt: String get() = if (idTelegramLyricsBt == null || idTelegramLyricsBt == "null" || idTelegramLyricsBt == "") "" else "✓"
    val flagTelegramKaraoke: String get() = if (idTelegramKaraoke == null || idTelegramKaraoke == "null" || idTelegramKaraoke == "") "" else "✓"
//    val flagTelegramKaraokeBt: String get() = if (idTelegramKaraokeBt == null || idTelegramKaraokeBt == "null" || idTelegramKaraokeBt == "") "" else "✓"
    val flagTelegramChords: String get() = if (idTelegramChords == null || idTelegramChords == "null" || idTelegramChords == "") "" else "✓"
//    val flagTelegramChordsBt: String get() = if (idTelegramChordsBt == null || idTelegramChordsBt == "null" || idTelegramChordsBt == "") "" else "✓"

    
    val pathToResultedModel: String get() = "$rootFolder/$DEMUCS_MODEL_NAME"
    val separatedStem: String get() = "vocals"
    val oldNoStemNameWav: String get() = "$pathToResultedModel/$fileName-no_$separatedStem.wav"
    val newNoStemNameWav: String get() = "$pathToResultedModel/$fileName-accompaniment.wav"
    val newNoStemNameFlac: String get() = "$pathToResultedModel/$fileName-accompaniment.flac"
    val vocalsNameWav: String get() = "$pathToResultedModel/$fileName-vocals.wav"
    val vocalsNameFlac: String get() = "$pathToResultedModel/$fileName-vocals.flac"
    val drumsNameWav: String get() = "$pathToResultedModel/$fileName-drums.wav"
    val drumsNameFlac: String get() = "$pathToResultedModel/$fileName-drums.flac"
    val bassNameWav: String get() = "$pathToResultedModel/$fileName-bass.wav"
    val bassNameFlac: String get() = "$pathToResultedModel/$fileName-bass.flac"
    val guitarsNameWav: String get() = "$pathToResultedModel/$fileName-guitars.wav"
    val guitarsNameFlac: String get() = "$pathToResultedModel/$fileName-guitars.flac"
    val otherNameWav: String get() = "$pathToResultedModel/$fileName-other.wav"
    val otherNameFlac: String get() = "$pathToResultedModel/$fileName}-other.flac"
    val fileAbsolutePath: String get() = "$rootFolder/$fileName.flac"
    val fileSettingsAbsolutePath: String get() = "$rootFolder/$fileName.settings"

    val fileNameVocals: String get() = "${fileName}-vocals.flac"
    val fileNameAccompaniment: String get() = "${fileName}-accompaniment.flac"

    val kdenliveFileName: String get() = "$rootFolder/$fileName.kdenlive"
    val kdenliveSubsFileName: String get() = "$rootFolder/$fileName.kdenlive.srt"

    val durationInMilliseconds: Long get() = ((MediaInfo.getInfoBySectionAndParameter(
        "$fileAbsolutePath",
        "Audio",
        "Duration"
    ) ?: "0.0").toDouble() * 1000).toLong()
    val durationTimecode: String get() = convertMillisecondsToTimecode(durationInMilliseconds)
    val durationFrames: Long get() = convertMillisecondsToFrames(durationInMilliseconds)

    val kdenliveTemplate: String get() = "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<mlt LC_NUMERIC=\"C\" producer=\"main_bin\" version=\"7.15.0\" root=\"${rootFolder.replace("&", "&amp;")}\">\n" +
            " <profile frame_rate_num=\"60\" sample_aspect_num=\"1\" display_aspect_den=\"9\" colorspace=\"709\" progressive=\"1\" description=\"HD 1080p 60 fps\" display_aspect_num=\"16\" frame_rate_den=\"1\" width=\"1920\" height=\"1080\" sample_aspect_den=\"1\"/>\n" +
            " <producer id=\"producer0\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "  <property name=\"length\">${durationFrames}</property>\n" +
            "  <property name=\"eof\">pause</property>\n" +
            "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameVocals.replace("&", "&amp;")}</property>\n" +
            "  <property name=\"seekable\">1</property>\n" +
            "  <property name=\"audio_index\">0</property>\n" +
            "  <property name=\"video_index\">-1</property>\n" +
            "  <property name=\"mute_on_pause\">1</property>\n" +
            "  <property name=\"mlt_service\">avformat</property>\n" +
            "  <property name=\"kdenlive:clipname\">VOICE</property>\n" +
            "  <property name=\"kdenlive:clip_type\">1</property>\n" +
            "  <property name=\"kdenlive:folderid\">-1</property>\n" +
            "  <property name=\"kdenlive:id\">3</property>\n" +
            " </producer>\n" +
            " <producer id=\"producer1\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "  <property name=\"length\">${durationFrames}</property>\n" +
            "  <property name=\"eof\">pause</property>\n" +
            "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameAccompaniment.replace("&", "&amp;")}</property>\n" +
            "  <property name=\"seekable\">1</property>\n" +
            "  <property name=\"audio_index\">0</property>\n" +
            "  <property name=\"video_index\">-1</property>\n" +
            "  <property name=\"mute_on_pause\">1</property>\n" +
            "  <property name=\"mlt_service\">avformat</property>\n" +
            "  <property name=\"kdenlive:clipname\">MUSIC</property>\n" +
            "  <property name=\"kdenlive:clip_type\">1</property>\n" +
            "  <property name=\"kdenlive:folderid\">-1</property>\n" +
            "  <property name=\"kdenlive:id\">2</property>\n" +
            " </producer>\n" +
            " <playlist id=\"main_bin\">\n" +
            "  <property name=\"kdenlive:docproperties.activeTrack\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.audioChannels\">2</property>\n" +
            "  <property name=\"kdenlive:docproperties.audioTarget\">1</property>\n" +
            "  <property name=\"kdenlive:docproperties.compositing\">1</property>\n" +
            "  <property name=\"kdenlive:docproperties.disablepreview\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.documentid\">1671265183813</property>\n" +
            "  <property name=\"kdenlive:docproperties.enableTimelineZone\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.enableexternalproxy\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.enableproxy\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.externalproxyparams\">./;GL;.LRV;./;GX;.MP4;./;GP;.LRV;./;GP;.MP4</property>\n" +
            "  <property name=\"kdenlive:docproperties.generateimageproxy\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.generateproxy\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.groups\">[\n" +
            "]\n" +
            "</property>\n" +
            "  <property name=\"kdenlive:docproperties.kdenliveversion\">22.12.3</property>\n" +
            "  <property name=\"kdenlive:docproperties.position\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.previewextension\"/>\n" +
            "  <property name=\"kdenlive:docproperties.previewparameters\"/>\n" +
            "  <property name=\"kdenlive:docproperties.profile\">atsc_1080p_60</property>\n" +
            "  <property name=\"kdenlive:docproperties.proxyextension\"/>\n" +
            "  <property name=\"kdenlive:docproperties.proxyimageminsize\">2000</property>\n" +
            "  <property name=\"kdenlive:docproperties.proxyimagesize\">800</property>\n" +
            "  <property name=\"kdenlive:docproperties.proxyminsize\">1000</property>\n" +
            "  <property name=\"kdenlive:docproperties.proxyparams\"/>\n" +
            "  <property name=\"kdenlive:docproperties.proxyresize\">640</property>\n" +
            "  <property name=\"kdenlive:docproperties.scrollPos\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.seekOffset\">30000</property>\n" +
            "  <property name=\"kdenlive:docproperties.version\">1.04</property>\n" +
            "  <property name=\"kdenlive:docproperties.verticalzoom\">1</property>\n" +
            "  <property name=\"kdenlive:docproperties.videoTarget\">-1</property>\n" +
            "  <property name=\"kdenlive:docproperties.zonein\">0</property>\n" +
            "  <property name=\"kdenlive:docproperties.zoneout\">75</property>\n" +
            "  <property name=\"kdenlive:docproperties.zoom\">8</property>\n" +
            "  <property name=\"kdenlive:expandedFolders\"/>\n" +
            "  <property name=\"kdenlive:documentnotes\"/>\n" +
            "  <property name=\"xml_retain\">1</property>\n" +
            "  <entry producer=\"producer0\" in=\"00:00:00.000\" out=\"${durationTimecode}\"/>\n" +
            "  <entry producer=\"producer1\" in=\"00:00:00.000\" out=\"${durationTimecode}\"/>\n" +
            " </playlist>\n" +
            " <producer id=\"black_track\" in=\"00:00:00.000\" out=\"00:10:59.333\">\n" +
            "  <property name=\"eof\">continue</property>\n" +
            "  <property name=\"resource\">black</property>\n" +
            "  <property name=\"aspect_ratio\">1</property>\n" +
            "  <property name=\"mlt_service\">color</property>\n" +
            "  <property name=\"mlt_image_format\">rgba</property>\n" +
            "  <property name=\"set.test_audio\">0</property>\n" +
            " </producer>\n" +
            " <producer id=\"producer2\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "  <property name=\"length\">${durationFrames}</property>\n" +
            "  <property name=\"eof\">pause</property>\n" +
            "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameAccompaniment.replace("&", "&amp;")}</property>\n" +
            "  <property name=\"seekable\">1</property>\n" +
            "  <property name=\"audio_index\">0</property>\n" +
            "  <property name=\"video_index\">-1</property>\n" +
            "  <property name=\"mute_on_pause\">0</property>\n" +
            "  <property name=\"mlt_service\">avformat-novalidate</property>\n" +
            "  <property name=\"kdenlive:clipname\">MUSIC</property>\n" +
            "  <property name=\"kdenlive:clip_type\">1</property>\n" +
            "  <property name=\"kdenlive:folderid\">-1</property>\n" +
            "  <property name=\"kdenlive:id\">2</property>\n" +
            "  <property name=\"kdenlive:audio_max0\">236</property>\n" +
            "  <property name=\"xml\">was here</property>\n" +
            "  <property name=\"set.test_audio\">0</property>\n" +
            "  <property name=\"set.test_image\">1</property>\n" +
            " </producer>\n" +
            " <playlist id=\"playlist0\">\n" +
            "  <property name=\"kdenlive:audio_track\">1</property>\n" +
            "  <entry producer=\"producer2\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "   <property name=\"kdenlive:id\">2</property>\n" +
            "  </entry>\n" +
            " </playlist>\n" +
            " <playlist id=\"playlist1\">\n" +
            "  <property name=\"kdenlive:audio_track\">1</property>\n" +
            " </playlist>\n" +
            " <tractor id=\"tractor0\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "  <property name=\"kdenlive:audio_track\">1</property>\n" +
            "  <property name=\"kdenlive:trackheight\">69</property>\n" +
            "  <property name=\"kdenlive:timeline_active\">1</property>\n" +
            "  <property name=\"kdenlive:collapsed\">28</property>\n" +
            "  <property name=\"kdenlive:thumbs_format\"/>\n" +
            "  <property name=\"kdenlive:audio_rec\"/>\n" +
            "  <track hide=\"both\" producer=\"playlist0\"/>\n" +
            "  <track hide=\"both\" producer=\"playlist1\"/>\n" +
            "  <filter id=\"filter0\">\n" +
            "   <property name=\"window\">75</property>\n" +
            "   <property name=\"max_gain\">20dB</property>\n" +
            "   <property name=\"mlt_service\">volume</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            "  <filter id=\"filter1\">\n" +
            "   <property name=\"channel\">-1</property>\n" +
            "   <property name=\"mlt_service\">panner</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"start\">0.5</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            "  <filter id=\"filter2\">\n" +
            "   <property name=\"iec_scale\">0</property>\n" +
            "   <property name=\"mlt_service\">audiolevel</property>\n" +
            "   <property name=\"dbpeak\">1</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            " </tractor>\n" +
            " <producer id=\"producer3\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "  <property name=\"length\">${durationFrames}</property>\n" +
            "  <property name=\"eof\">pause</property>\n" +
            "  <property name=\"resource\">${DEMUCS_MODEL_NAME}/${fileNameVocals.replace("&", "&amp;")}</property>\n" +
            "  <property name=\"seekable\">1</property>\n" +
            "  <property name=\"audio_index\">0</property>\n" +
            "  <property name=\"video_index\">-1</property>\n" +
            "  <property name=\"mute_on_pause\">0</property>\n" +
            "  <property name=\"mlt_service\">avformat-novalidate</property>\n" +
            "  <property name=\"kdenlive:clipname\">VOICE</property>\n" +
            "  <property name=\"kdenlive:clip_type\">1</property>\n" +
            "  <property name=\"kdenlive:folderid\">-1</property>\n" +
            "  <property name=\"kdenlive:id\">3</property>\n" +
            "  <property name=\"kdenlive:audio_max0\">197</property>\n" +
            "  <property name=\"xml\">was here</property>\n" +
            "  <property name=\"set.test_audio\">0</property>\n" +
            "  <property name=\"set.test_image\">1</property>\n" +
            " </producer>\n" +
            " <playlist id=\"playlist2\">\n" +
            "  <property name=\"kdenlive:audio_track\">1</property>\n" +
            "  <entry producer=\"producer3\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "   <property name=\"kdenlive:id\">3</property>\n" +
            "  </entry>\n" +
            " </playlist>\n" +
            " <playlist id=\"playlist3\">\n" +
            "  <property name=\"kdenlive:audio_track\">1</property>\n" +
            " </playlist>\n" +
            " <tractor id=\"tractor1\" in=\"00:00:00.000\" out=\"${durationTimecode}\">\n" +
            "  <property name=\"kdenlive:audio_track\">1</property>\n" +
            "  <property name=\"kdenlive:trackheight\">246</property>\n" +
            "  <property name=\"kdenlive:timeline_active\">1</property>\n" +
            "  <property name=\"kdenlive:collapsed\">0</property>\n" +
            "  <property name=\"kdenlive:thumbs_format\"/>\n" +
            "  <property name=\"kdenlive:audio_rec\"/>\n" +
            "  <track hide=\"video\" producer=\"playlist2\"/>\n" +
            "  <track hide=\"video\" producer=\"playlist3\"/>\n" +
            "  <filter id=\"filter3\">\n" +
            "   <property name=\"window\">75</property>\n" +
            "   <property name=\"max_gain\">20dB</property>\n" +
            "   <property name=\"mlt_service\">volume</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            "  <filter id=\"filter4\">\n" +
            "   <property name=\"channel\">-1</property>\n" +
            "   <property name=\"mlt_service\">panner</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"start\">0.5</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            "  <filter id=\"filter5\">\n" +
            "   <property name=\"iec_scale\">0</property>\n" +
            "   <property name=\"mlt_service\">audiolevel</property>\n" +
            "   <property name=\"dbpeak\">1</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            " </tractor>\n" +
            " <tractor id=\"tractor2\" in=\"00:00:00.000\">\n" +
            "  <track producer=\"black_track\"/>\n" +
            "  <track producer=\"tractor0\"/>\n" +
            "  <track producer=\"tractor1\"/>\n" +
            "  <transition id=\"transition0\">\n" +
            "   <property name=\"a_track\">0</property>\n" +
            "   <property name=\"b_track\">1</property>\n" +
            "   <property name=\"mlt_service\">mix</property>\n" +
            "   <property name=\"kdenlive_id\">mix</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"always_active\">1</property>\n" +
            "   <property name=\"accepts_blanks\">1</property>\n" +
            "   <property name=\"sum\">1</property>\n" +
            "  </transition>\n" +
            "  <transition id=\"transition1\">\n" +
            "   <property name=\"a_track\">0</property>\n" +
            "   <property name=\"b_track\">2</property>\n" +
            "   <property name=\"mlt_service\">mix</property>\n" +
            "   <property name=\"kdenlive_id\">mix</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"always_active\">1</property>\n" +
            "   <property name=\"accepts_blanks\">1</property>\n" +
            "   <property name=\"sum\">1</property>\n" +
            "  </transition>\n" +
            "  <filter id=\"filter6\">\n" +
            "   <property name=\"window\">75</property>\n" +
            "   <property name=\"max_gain\">20dB</property>\n" +
            "   <property name=\"mlt_service\">volume</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            "  <filter id=\"filter7\">\n" +
            "   <property name=\"channel\">-1</property>\n" +
            "   <property name=\"mlt_service\">panner</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"start\">0.5</property>\n" +
            "   <property name=\"disable\">1</property>\n" +
            "  </filter>\n" +
            "  <filter id=\"filter8\">\n" +
            "   <property name=\"mlt_service\">avfilter.subtitles</property>\n" +
            "   <property name=\"internal_added\">237</property>\n" +
            "   <property name=\"av.filename\">/var/tmp/1671265183813.srt</property>\n" +
            "  </filter>\n" +
            " </tractor>\n" +
            "</mlt>\n"

    val textDemucs2track: String get() = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" --two-stems=$separatedStem -o \"$rootFolder\" \"$fileAbsolutePath\"\n" +
            "mv \"$oldNoStemNameWav\" \"$newNoStemNameWav\"" + "\n" +
            "ffmpeg -i \"$newNoStemNameWav\" -compression_level 8 \"$newNoStemNameFlac\" -y" + "\n" +
            "rm \"$newNoStemNameWav\"" + "\n" +
            "ffmpeg -i \"$vocalsNameWav\" -compression_level 8 \"$vocalsNameFlac\" -y" + "\n" +
            "rm \"$vocalsNameWav\"" + "\n"

    val textDemucs4track: String get() = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" -o \"$rootFolder\" \"$fileAbsolutePath\"\n" +
            "ffmpeg -i \"$drumsNameWav\" -compression_level 8 \"$drumsNameFlac\" -y" + "\n" +
            "rm \"$drumsNameWav\"" + "\n" +
            "ffmpeg -i \"$bassNameWav\" -compression_level 8 \"$bassNameFlac\" -y" + "\n" +
            "rm \"$bassNameWav\"" + "\n" +
            "ffmpeg -i \"$guitarsNameWav\" -compression_level 8 \"$guitarsNameFlac\" -y" + "\n" +
            "rm \"$guitarsNameWav\"" + "\n" +
            "ffmpeg -i \"$otherNameWav\" -compression_level 8 \"$otherNameFlac\" -y" + "\n" +
            "rm \"$otherNameWav\"" + "\n" +
            "ffmpeg -i \"$vocalsNameWav\" -compression_level 8 \"$vocalsNameFlac\" -y" + "\n" +
            "rm \"$vocalsNameWav\"" + "\n"
    val textDemucs5track: String get() = "$textDemucs2track$textDemucs4track"

    val subsTemplate : String get() {
        val durationSubsTimecode1 = convertMillisecondsToTimecode(durationInMilliseconds).replace(".",",")
        val durationSubsTimecode2 = convertFramesToTimecode(convertMillisecondsToFrames(durationInMilliseconds) +1).replace(".",",")
        return  "1\n" +
                "00:00:00,000 --> 00:00:00,083\n" +
                "~[G0]\n" +
                "\n" +
                "2\n" +
                "${durationSubsTimecode1} --> ${durationSubsTimecode2}\n" +
                "//\\\\\n"
    }

    val processColorMeltLyrics: String get() = getColorToProcessTypeName(statusProcessLyrics)
    val processColorMeltKaraoke: String get() = getColorToProcessTypeName(statusProcessKaraoke)
    val processColorMeltChords: String get() = getColorToProcessTypeName(statusProcessChords)
    val processColorMeltLyricsBt: String get() = getColorToProcessTypeName(statusProcessLyricsBt)
    val processColorMeltKaraokeBt: String get() = getColorToProcessTypeName(statusProcessKaraokeBt)
    val processColorMeltChordsBt: String get() = getColorToProcessTypeName(statusProcessChordsBt)

    val processColorVk: String get() = if (idVk.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorBoosty: String get() = if (idBoosty.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorVkLyrics: String get() = if (idVkLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorVkKaraoke: String get() = if (idVkKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorYoutubeLyrics: String get() = if (idYoutubeLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorYoutubeKaraoke: String get() = if (idYoutubeKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorTelegramLyrics: String get() =
        if (idTelegramLyrics == "-" || idTelegramKaraoke == "-" ) {
            "#F08080"
        } else if (idTelegramLyrics.isNotBlank()) {
            "#00FF00"
        } else {
            "#A9A9A9"
        }

    val processColorTelegramKaraoke: String get() =
        if (idTelegramLyrics == "-" || idTelegramKaraoke == "-" ) {
            "#F08080"
        } else if (idTelegramKaraoke.isNotBlank()) {
            "#00FF00"
        } else {
            "#A9A9A9"
        }


    val haveBoostyLink: Boolean get() = idBoosty.isNotBlank()
    val haveVkGroupLink: Boolean get() = idVk.isNotBlank()
    val haveYoutubeLinks: Boolean get() = idYoutubeLyrics.isNotBlank() ||
            idYoutubeKaraoke.isNotBlank() ||
            idYoutubeChords.isNotBlank()
//            idYoutubeLyricsBt.isNotBlank() ||
//            idYoutubeKaraokeBt.isNotBlank() ||
//            idYoutubeChordsBt.isNotBlank()

    val haveVkLinks: Boolean get() = idVkLyrics.isNotBlank() ||
            idVkKaraoke.isNotBlank() ||
            idVkChords.isNotBlank()
//            idVkLyricsBt.isNotBlank() ||
//            idVkKaraokeBt.isNotBlank() ||
//            idVkChordsBt.isNotBlank()

    val haveTelegramLinks: Boolean get() = idTelegramLyrics.isNotBlank() && idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-"

    val flags: String get() = if (haveBoostyLink && haveVkGroupLink && haveVkLinks && haveTelegramLinks && haveYoutubeLinks) "" else "(${if (haveBoostyLink) "b" else "-"}${if (haveVkGroupLink) "g" else "-"}${if (haveVkLinks) "v" else "-"}${if (haveTelegramLinks) "t" else "-"}${if (haveYoutubeLinks) "z" else "-"}) "

    val digest: String get() =
       (if (firstSongInAlbum) "Альбом: «${album}» (${year})\n\n" else "") +
       "$songName\n$linkBoosty\n" +
                "${if (idVkKaraoke.isNotBlank()) "Karaoke VK $linkVkKaraokePlay\n" else ""}${if (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") "Karaoke TG $linkTelegramKaraokePlay\n" else ""}${if (idYoutubeKaraoke.isNotBlank()) "Karaoke DZ $linkYoutubeKaraokePlay\n" else ""}" +
                "${if (idVkLyrics.isNotBlank()) "Lyrics VK $linkVkLyricsPlay\n" else ""}${if (idTelegramLyrics.isNotBlank()) "Lyrics TG $linkTelegramLyricsPlay\n" else ""}${if (idYoutubeLyrics.isNotBlank()) "Lyrics DZ $linkYoutubeLyricsPlay\n" else ""}\n"

    val digestIsFull: Boolean get() = idVkKaraoke.isNotBlank() && (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") && idYoutubeKaraoke.isNotBlank() && idVkLyrics.isNotBlank() && idTelegramLyrics.isNotBlank() && idYoutubeLyrics.isNotBlank()

    fun getDescriptionLinks(): String {

        var result = if (idBoosty.isNotBlank()) "На Boosty (все версии): ${linkBoosty}\n\n" else ""

        if (haveYoutubeLinks) {
            result += "На Dzen:\n"
            result += if (idYoutubeLyrics.isNotBlank()) "Версия Lyrics: ${linkYoutubeLyricsPlay}\n" else ""
            result += if (idYoutubeKaraoke.isNotBlank()) "Версия Karaoke: ${linkYoutubeKaraokePlay}\n" else ""
            result += if (idYoutubeChords.isNotBlank()) "Версия Chords: ${linkYoutubeChordsPlay}\n" else ""
//            result += if (idYoutubeLyricsBt.isNotBlank()) "Версия Lyrics with delay: ${linkYoutubeLyricsBtPlay}\n" else ""
//            result += if (idYoutubeKaraokeBt.isNotBlank()) "Версия Karaoke with delay: ${linkYoutubeKaraokeBtPlay}\n" else ""
//            result += if (idYoutubeChordsBt.isNotBlank()) "Версия Chords with delay: ${linkYoutubeChordsBtPlay}\n" else ""
            result += "\n"
        }

        if (haveVkLinks) {
            result += "В VK:\n"
            result += if (idVkLyrics.isNotBlank()) "Версия Lyrics: ${linkVkLyricsPlay}\n" else ""
            result += if (idVkKaraoke.isNotBlank()) "Версия Karaoke: ${linkVkKaraokePlay}\n" else ""
            result += if (idVkChords.isNotBlank()) "Версия Chords: ${linkVkChordsPlay}\n" else ""
//            result += if (idVkLyricsBt.isNotBlank()) "Версия Lyrics with delay: ${linkVkLyricsBtPlay}\n" else ""
//            result += if (idVkKaraokeBt.isNotBlank()) "Версия Karaoke with delay: ${linkVkKaraokeBtPlay}\n" else ""
//            result += if (idVkChordsBt.isNotBlank()) "Версия Chords with delay: ${linkVkChordsBtPlay}\n" else ""
            result += "\n"
        }

        if (haveTelegramLinks) {
            result += "В Telegram:\n"
            result += if (idTelegramLyrics.isNotBlank()) "Версия Lyrics: ${linkTelegramLyricsPlay}\n" else ""
            result += if (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") "Версия Karaoke: ${linkTelegramKaraokePlay}\n" else ""
            result += if (idTelegramChords.isNotBlank()) "Версия Chords: ${linkTelegramChordsPlay}\n" else ""
//            result += if (idTelegramLyricsBt.isNotBlank()) "Версия Lyrics with delay: ${linkTelegramLyricsBtPlay}\n" else ""
//            result += if (idTelegramKaraokeBt.isNotBlank()) "Версия Karaoke with delay: ${linkTelegramKaraokeBtPlay}\n" else ""
//            result += if (idTelegramChordsBt.isNotBlank()) "Версия Chords with delay: ${linkTelegramChordsBtPlay}\n" else ""
            result += "\n"
        }

        return result
    }
    private fun getColorToProcessTypeName(statusName: String): String {

        return when (statusName) {
            KaraokeProcessStatuses.CREATING.name -> "#FFDEAD"
            KaraokeProcessStatuses.WAITING.name -> "#FFFF00"
            KaraokeProcessStatuses.WORKING.name -> "#FF1493"
            KaraokeProcessStatuses.DONE.name -> "#00FFFF"
            KaraokeProcessStatuses.ERROR.name -> "#8B0000"
            else -> "#FFFFFF"
        }

    }

    fun playLyrics() {
        val fileName = "$rootFolder/done_files/$fileName [lyrics].mp4"
        if (File(fileName).exists()) {
            val processBuilder = ProcessBuilder("smplayer", fileName)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        }
    }

    fun playKaraoke() {
        val fileName = "$rootFolder/done_files/$fileName [karaoke].mp4"
        if (File(fileName).exists()) {
            val processBuilder = ProcessBuilder("smplayer", fileName)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        }
    }

    fun playChords() {
        val fileName = "$rootFolder/done_files/$fileName [chords].mp4"
        if (File(fileName).exists()) {
            val processBuilder = ProcessBuilder("smplayer", fileName)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        }
    }

    fun playLyricsBt() {
        val fileName = "$rootFolder/done_files/$fileName [lyrics] bluetooth.mp4"
        if (File(fileName).exists()) {
            val processBuilder = ProcessBuilder("smplayer", fileName)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        }
    }

    fun playKaraokeBt() {
        val fileName = "$rootFolder/done_files/$fileName [karaoke] bluetooth.mp4"
        if (File(fileName).exists()) {
            val processBuilder = ProcessBuilder("smplayer", fileName)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        }
    }

    fun playChordsBt() {
        val fileName = "$rootFolder/done_files/$fileName [chords] bluetooth.mp4"
        if (File(fileName).exists()) {
            val processBuilder = ProcessBuilder("smplayer", fileName)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        }
    }

    fun getSourceMarkers(voice: Int): List<SourceMarker> {
        return if (sourceMarkersList.size > voice) {
            sourceMarkersList[voice]
        } else {
            emptyList()
        }
    }

    fun setSourceMarkers(voice: Int, markers: List<SourceMarker>) {
        if (sourceMarkersList.size < voice) return
        if (sourceMarkersList.size == voice) {
            val lst = sourceMarkersList.toMutableList()
            lst.add(markers)
            sourceMarkers = Json.encodeToString(lst)
            saveToDb()
            return
        }
        if (voice >= 0 && voice < sourceMarkersList.size) {
            val lst = sourceMarkersList.toMutableList()
            lst[voice] = markers
            sourceMarkers = Json.encodeToString(lst)
            saveToDb()
            return
        }
    }

    fun getSourceText(voice: Int): String {
        return if (sourceTextList.size > voice) {
            sourceTextList[voice].replace("\\n", "\n").replace("\"", "")
        } else {
            ""
        }
    }

    fun setSourceText(voice: Int, text: String) {
        if (sourceTextList.size < voice) return
        if (sourceTextList.size == voice) {
            val lst = sourceTextList.toMutableList()
            lst.add(text)
            sourceText = Json.encodeToString(lst)
            saveToDb()
            return
        }
        if (voice >= 0 && voice < sourceTextList.size) {
            val lst = sourceTextList.toMutableList()
            lst[voice] = text
            sourceText = Json.encodeToString(lst)
            saveToDb()
            return
        }
    }

    fun getSourceSyllables(voice: Int): List<String> {
        return if (sourceSyllablesList.size > voice) {
            sourceSyllablesList[voice]
        } else {
            emptyList()
        }
    }

    fun updateMarkersFromSourceText(voice: Int) {

        val listMarkers = getSourceMarkers(voice)
        val listSyllables = getSourceSyllables(voice)
        var indexSyllable = 0
        listMarkers.forEach { marker ->
            if (marker.markertype == "syllables") {
                if (indexSyllable < listSyllables.size) {
                    marker.label = listSyllables[indexSyllable]
                    indexSyllable++
                } else {
                    marker.label = ""
                }
            }
        }
        setSourceMarkers(voice, listMarkers)
    }

    fun convertSrtToMarkers(srt: String): List<SourceMarker> {
        val result: MutableList<SourceMarker> = mutableListOf()
        val listSubtitleFileElements: MutableList<Song.SubtitleFileElement> = mutableListOf()
        val blocks = srt.split("\\n[\\n]+".toRegex())
        blocks.forEach() { block ->
            val blocklines = block.split("\n")
            val id = if (blocklines.isNotEmpty() && blocklines[0]!= "" ) blocklines[0].toLong() else 0
            val startEnd = if (blocklines.size > 1) blocklines[1] else ""
            if (startEnd != "" && id != 0L) {
                var text = if (blocklines.size > 2) blocklines[2] else ""
                val se = startEnd.split(" --> ")
                val startFrame = convertTimecodeToFrames(se[0].replace(",","."))
                val endFrame = convertTimecodeToFrames(se[1].replace(",","."))
                val isStartOfLine = (text.uppercase().startsWith("[SETTING]|") || text.startsWith("//"))
                val isEndOfLine =  (text.uppercase().startsWith("[SETTING]|") || text.endsWith("\\\\"))
                listSubtitleFileElements.add(
                    Song.SubtitleFileElement(
                        startFrame,
                        endFrame,
                        text,
                        false,
                        false,
                        (text.uppercase().startsWith("[SETTING]|") || text == "//\\\\")
                    )
                )
            }
        }
        listSubtitleFileElements.sortBy { it.startFrame }
        val markers: MutableList<SourceMarker> = mutableListOf()
        listSubtitleFileElements.forEachIndexed{index, _ ->
            val currSfe = listSubtitleFileElements[index]
            if (currSfe.text.uppercase().startsWith("[SETTING]|")) {
                markers.add(
                    SourceMarker(
                        time = convertFramesToMilliseconds(currSfe.startFrame).toDouble() / 1000,
                        label = currSfe.text.replace("[SETTING]|", ""),
                        color = "#000080",
                        position = "top",
                        markertype = "setting"
                    )
                )
            } else {
                if (currSfe.text == "//\\\\") {
                    markers.add(
                        SourceMarker(
                            time = convertFramesToMilliseconds(currSfe.startFrame).toDouble() / 1000,
                            label = "END",
                            color = "#000000",
                            position = "top",
                            markertype = "setting"
                        )
                    )
                } else {
                    if (currSfe.text.uppercase().startsWith("//")) {
                        markers.add(
                            SourceMarker(
                                time = convertFramesToMilliseconds(currSfe.startFrame).toDouble() / 1000,
                                label = currSfe.text.replace("//", "").replace("\\", ""),
                                color = "#008000",
                                position = "bottom",
                                markertype = "syllables"
                            )
                        )
                    } else {
                        markers.add(
                            SourceMarker(
                                time = convertFramesToMilliseconds(currSfe.startFrame).toDouble() / 1000,
                                label = currSfe.text.replace("//", "").replace("\\", ""),
                                color = "#D2691E",
                                position = "bottom",
                                markertype = "syllables"
                            )
                        )
                    }
                    if (currSfe.text.uppercase().endsWith("\\\\")) {
                        markers.add(
                            SourceMarker(
                                time = convertFramesToMilliseconds(currSfe.endFrame).toDouble() / 1000,
                                label = "",
                                color = "#FF0000",
                                position = "bottom",
                                markertype = "endofline"
                            )
                        )
                    }
                }

            }

        }

        return markers

    }

    fun convertMarkersToText(markers: List<SourceMarker>): String {
        var result = ""
        markers.forEach { marker ->
            if (marker.markertype == "endofline") result += "\n"
            if (marker.markertype == "syllables") result += marker.label
        }
        return result
    }


    fun createTextAndMarkersFromOldVersion() {
        /***
         * Проверяем наличие маркеров - если они есть - пропускаем
         * Проверяем наличие файла *.voice1.srt - если он есть - пропускаем
         * Проверяем наличие файла *.srt - если он есть - переименовываем в *.voice1.srt и пропускаем
         * Проверяем наличие файла *.kdenlive.srt. Если он есть проверяем его содержимое. Если оно шаблонное - пропускаем
         * Если нет - обрабатываем все файлы *.kdenlive.srt, формируем из них тексты и маркеры, записываем в базу, формируем файлы *.versionX.srt
         */

        if (sourceMarkersList.isNotEmpty() && sourceMarkersList[0].isNotEmpty()) {
            println("createTextAndMarkersFromOldVersion: ${fileName}: Список маркеров не пустой, выходим.")
            return
        }

        if(getListFiles(rootFolder, ".srt", "${fileName}.voice").size > 0) {
            println("createTextAndMarkersFromOldVersion: ${fileName}: Список файлов *.voiceX.srt не пустой, выходим.")
            return
        }

        if (File("$rootFolder/$fileName.srt").exists()) {
            println("createTextAndMarkersFromOldVersion: ${fileName}: Найден файл *.srt. Переименовываем в *.voice1.srt и выходим.")
            File("$rootFolder/$fileName.srt").renameTo(File("$rootFolder/$fileName.voice1.srt"))
            return
        }

        if (File("$rootFolder/$fileName.kdenlive.srt").exists()) {
            val body = File("$rootFolder/$fileName.kdenlive.srt").readText(Charsets.UTF_8)
            if (body.contains("~[G0]")) {
                println("createTextAndMarkersFromOldVersion: ${fileName}: Найден шаблонный файл *.kdenlive.srt, выходим.")
                return
            }
        } else {
            println("createTextAndMarkersFromOldVersion: ${fileName}: Не найден файл *.kdenlive.srt, выходим.")
            return
        }

        println("createTextAndMarkersFromOldVersion: ${fileName}: Найден файл *.kdenlive.srt, работаем.")

        val listSrtFiles = getListFiles(rootFolder, ".srt", "${fileName}.kdenlive").sorted()
        listSrtFiles.forEachIndexed { voice, srtFileName ->
            val body = File(srtFileName).readText(Charsets.UTF_8)
            val markers = convertSrtToMarkers(body)
            val text = convertMarkersToText(markers)
            setSourceText(voice, text)
            setSourceMarkers(voice, markers)
            File("$rootFolder/$fileName.voice${voice+1}.srt").writeText(convertMarkersToSrt(voice))
        }
    }

    fun convertMarkersToSrt(voice: Int): String {
        val listMarkers = getSourceMarkers(voice)
        var perviousMarkerIsEndOfLine = true
        var numberSrt = 0
        var result = ""

        listMarkers.forEachIndexed { index, sourceMarker ->
            if (sourceMarker.markertype != "endofline") numberSrt++
            val nextMarker = if (index == listMarkers.size - 1) null else listMarkers[index + 1]
            val srtNumber =  numberSrt.toString()
            val srtTimeStart = convertMillisecondsToTimecode((sourceMarker.time * 1000).toLong()).replace(".", ",")
            val srtTimeEnd = if (nextMarker == null) {
                convertMillisecondsToTimecode(((sourceMarker.time + 1.0) * 1000).toLong()).replace(".", ",")
            } else {
                convertMillisecondsToTimecode((nextMarker.time * 1000).toLong()).replace(".", ",")
            }

            val srtText = if (sourceMarker.markertype == "syllables") {
                 "${if (perviousMarkerIsEndOfLine) "//${sourceMarker.label.uppercaseFirstLetter()}" else sourceMarker.label}${if (nextMarker != null && nextMarker.markertype == "endofline") "\\\\" else ""}\n\n"
            } else if (sourceMarker.markertype == "setting") {
                if (sourceMarker.label == "END") {
                    "//\\\\\n\n"
                } else {
                    "[SETTING]|${sourceMarker.label}\n\n"
                }

            } else {
                ""
            }

            val srt = "$srtNumber\n$srtTimeStart --> $srtTimeEnd\n$srtText"
            if (sourceMarker.markertype != "endofline") result += srt
            if (sourceMarker.markertype == "endofline") {
                perviousMarkerIsEndOfLine = true
            } else if (sourceMarker.markertype != "setting") {
                perviousMarkerIsEndOfLine = false
            }

        }
        return result
    }

    fun saveToDb() {

        if (readonly) return
        if  (id == 0L) {
            val newSett = createDbInstance(this)
            newSett?.let {
                newSett.saveToFile()
            }
        } else {

            val diff = getDiff(this, loadFromDbById(id))
            if (diff.isEmpty()) return
            val setStr = diff.map { "${it.first} = ?" }.joinToString(", ")
            val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            val ps = connection.prepareStatement(sql)

            println("Saved To Database:")
            println("Setting id = $id, diffs:")

            var index = 1
            diff.forEach {
                val (diffName, diffNewValue, diffOldValue) = it
                println("Field: $diffName")
                println("Old value = $diffOldValue")
                println("New value = $diffNewValue")
                println("-----------------------------")
                if (diffNewValue is Long) {
                    ps.setLong(index, diffNewValue.toLong())
                } else {
                    ps.setString(index, diffNewValue.toString())
                }
                index++
            }
            ps.setLong(index, id)
            ps.executeUpdate()
            ps.close()
            connection.close()

        }

    }

    fun deleteFromDb(withFiles: Boolean = true) {
        if (withFiles) {
            if (File(fileAbsolutePath).exists()) File(fileAbsolutePath).delete()
            if (File(fileSettingsAbsolutePath).exists()) File(fileSettingsAbsolutePath).deleteOnExit()
            if (File(vocalsNameFlac).exists()) File(vocalsNameFlac).deleteOnExit()
            if (File(newNoStemNameFlac).exists()) File(newNoStemNameFlac).deleteOnExit()
        }

        Class.forName("org.postgresql.Driver")
        val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
        val sql = "DELETE FROM tbl_settings WHERE id = ?"
        val ps = connection.prepareStatement(sql)
        ps.setLong(1, id)
        ps.executeUpdate()
        ps.close()
        connection.close()

    }

    fun saveToFile() {
        var txt = ""
        SettingField.values().forEach { settingField ->
            if (fields.contains(settingField)) txt += "${settingField.name}=${fields[settingField]}\n"
        }
        File("$rootFolder/$fileName.settings").writeText(txt)
    }

    fun createVKDescription() {
        val song = Song(this, SongVersion.LYRICS)
        val fileName = song.getOutputFilename(SongOutputFile.VK, false)
        val date = date
        val time = time.replace(":",".")


        val trueDate = "${date.substring(6)}.${date.substring(3,5)}.${date.substring(0,2)}"
        val name = fileName.replace("{REPLACE_DATE}",trueDate).replace("{REPLACE_TIME}",time).replace(" [lyrics]","")
        val text = song.getVKGroupDescription()

        if (text != "") {
            File(name).writeText(text)
            val vkPictNameOld = (song.getOutputFilename(SongOutputFile.PICTUREVK, false)).replace(" [lyrics] VK"," [VK]")
            val vkPictNameNew = name.replace(" [VK].txt", " [VK].png")
            FileUtils.copyFile(File(vkPictNameOld), File(vkPictNameNew))
        }

    }

    fun createKdenliveFiles(overrideKdenliveFile: Boolean = true, overrideKdenliveSubsFile: Boolean = false) {
        val kdenliveFile = File(kdenliveFileName)
        if (!kdenliveFile.exists() || overrideKdenliveFile) {
            kdenliveFile.writeText(kdenliveTemplate)
        }
        val kdenliveSubsFile = File(kdenliveSubsFileName)
        if (!kdenliveSubsFile.exists() || overrideKdenliveSubsFile) {
            kdenliveSubsFile.writeText(subsTemplate)
        }
    }

    fun createKaraoke() {

        val permissions = PosixFilePermissions.fromString("rwxr-x---")

        val songLyrics = Song(this, SongVersion.LYRICS)
        val songKaraoke = Song(this, SongVersion.KARAOKE)
        val songChords = Song(this, SongVersion.CHORDS)
        val hasChords = songChords.hasChords

        val pathToMltLyrics = songLyrics.getOutputFilename(SongOutputFile.MLT,false)
        val pathToMltLyricsBt = songLyrics.getOutputFilename(SongOutputFile.MLT,true)
        val pathToMltKaraoke = songKaraoke.getOutputFilename(SongOutputFile.MLT,false)
        val pathToMltKaraokeBt = songKaraoke.getOutputFilename(SongOutputFile.MLT,true)
        val pathToMltChords = songChords.getOutputFilename(SongOutputFile.MLT,false)
        val pathToMltChordsBt = songChords.getOutputFilename(SongOutputFile.MLT,true)

        val txtLyric = "echo \"$pathToMltLyrics\"\nmelt -progress \"$pathToMltLyrics\"\n\n"
        val txtKaraoke = "echo \"$pathToMltKaraoke\"\nmelt -progress \"$pathToMltKaraoke\"\n\n"
        val txtChords = "echo \"$pathToMltChords\"\nmelt -progress \"$pathToMltChords\"\n\n"
        val txtLyricBt = "echo \"$pathToMltLyricsBt\"\nmelt -progress \"$pathToMltLyricsBt\"\n\n"
        val txtKaraokeBt = "echo \"$pathToMltKaraokeBt\"\nmelt -progress \"$pathToMltKaraokeBt\"\n\n"
        val txtChordsBt = "echo \"$pathToMltChordsBt\"\nmelt -progress \"$pathToMltChordsBt\"\n\n"

        val songTxtAll = "$txtLyric$txtKaraoke${if (hasChords) txtChords else ""}$txtLyricBt$txtKaraokeBt${if (hasChords) txtChordsBt else ""}"
        val songTxtWOLyrics = "$txtKaraoke${if (hasChords) txtChords else ""}$txtLyricBt$txtKaraokeBt${if (hasChords) txtChordsBt else ""}"

        var file = File(songLyrics.getOutputFilename(SongOutputFile.RUN,false))
        Files.createDirectories(Path(file.parent))
        file.writeText(txtLyric)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File(songLyrics.getOutputFilename(SongOutputFile.RUN,true))
        file.writeText(txtLyricBt)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File(songKaraoke.getOutputFilename(SongOutputFile.RUN,false))
        file.writeText(txtKaraoke)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File(songKaraoke.getOutputFilename(SongOutputFile.RUN,true))
        file.writeText(txtKaraokeBt)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        if (hasChords) {
            file = File(songChords.getOutputFilename(SongOutputFile.RUN,false))
            file.writeText(txtChords)
            Files.setPosixFilePermissions(file.toPath(), permissions)

            file = File(songChords.getOutputFilename(SongOutputFile.RUN,true))
            file.writeText(txtChordsBt)
            Files.setPosixFilePermissions(file.toPath(), permissions)
        }

        file = File(songLyrics.getOutputFilename(SongOutputFile.RUNALL,false).replace("[lyrics]","[ALL]"))
        file.writeText(songTxtAll)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File(songLyrics.getOutputFilename(SongOutputFile.RUNALL,false).replace("[lyrics]","[ALLwoLYRICS]"))
        file.writeText(songTxtWOLyrics)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        createKaraoke(songLyrics, false)
        createKaraoke(songKaraoke, false)
        createKaraoke(songChords, false)
//        createKaraoke(songLyrics, true)
//        createKaraoke(songKaraoke, true)
//        createKaraoke(songChords, true)

    }

    companion object {

        fun getDiff(settA: Settings, settB: Settings?): List<Triple<String, Any, Any>> {
            val result: MutableList<Triple<String, Any, Any>> = mutableListOf()
            if (settB != null) {
                if (settA.songName != settB.songName) result.add(Triple("song_name", settA.songName, settB.songName))
                if (settA.author != settB.author) result.add(Triple("song_author", settA.author, settB.author))
                if (settA.album != settB.album) result.add(Triple("song_album", settA.album, settB.album))
                if (settA.date != settB.date) result.add(Triple("publish_date", settA.date, settB.date))
                if (settA.time != settB.time) result.add(Triple("publish_time", settA.time, settB.time))
                if (settA.year != settB.year) result.add(Triple("song_year", settA.year, settB.year))
                if (settA.track != settB.track) result.add(Triple("song_track", settA.track, settB.track))
                if (settA.key != settB.key) result.add(Triple("song_tone", settA.key, settB.key))
                if (settA.bpm != settB.bpm) result.add(Triple("song_bpm", settA.bpm, settB.bpm))
                if (settA.ms != settB.ms) result.add(Triple("song_ms", settA.ms, settB.ms))
                if (settA.fileName != settB.fileName) result.add(Triple("file_name", settA.fileName, settB.fileName))
                if (settA.rootFolder != settB.rootFolder) result.add(Triple("root_folder", settA.rootFolder, settB.rootFolder))
                if (settA.idBoosty != settB.idBoosty) result.add(Triple("id_boosty", settA.idBoosty, settB.idBoosty))
                if (settA.idVk != settB.idVk) result.add(Triple("id_vk", settA.idVk, settB.idVk))
                if (settA.idYoutubeLyrics != settB.idYoutubeLyrics) result.add(Triple("id_youtube_lyrics", settA.idYoutubeLyrics, settB.idYoutubeLyrics))
//                if (settA.idYoutubeLyricsBt != settB.idYoutubeLyricsBt) result.add(Triple("id_youtube_lyrics_bt", settA.idYoutubeLyricsBt, settB.idYoutubeLyricsBt))
                if (settA.idYoutubeKaraoke != settB.idYoutubeKaraoke) result.add(Triple("id_youtube_karaoke", settA.idYoutubeKaraoke, settB.idYoutubeKaraoke))
//                if (settA.idYoutubeKaraokeBt != settB.idYoutubeKaraokeBt) result.add(Triple("id_youtube_karaoke_bt", settA.idYoutubeKaraokeBt, settB.idYoutubeKaraokeBt))
                if (settA.idYoutubeChords != settB.idYoutubeChords) result.add(Triple("id_youtube_chords", settA.idYoutubeChords, settB.idYoutubeChords))
//                if (settA.idYoutubeChordsBt != settB.idYoutubeChordsBt) result.add(Triple("id_youtube_chords_bt", settA.idYoutubeChordsBt, settB.idYoutubeChordsBt))
                if (settA.idVkLyrics != settB.idVkLyrics) result.add(Triple("id_vk_lyrics", settA.idVkLyrics, settB.idVkLyrics))
//                if (settA.idVkLyricsBt != settB.idVkLyricsBt) result.add(Triple("id_vk_lyrics_bt", settA.idVkLyricsBt, settB.idVkLyricsBt))
                if (settA.idVkKaraoke != settB.idVkKaraoke) result.add(Triple("id_vk_karaoke", settA.idVkKaraoke, settB.idVkKaraoke))
//                if (settA.idVkKaraokeBt != settB.idVkKaraokeBt) result.add(Triple("id_vk_karaoke_bt", settA.idVkKaraokeBt, settB.idVkKaraokeBt))
                if (settA.idVkChords != settB.idVkChords) result.add(Triple("id_vk_chords", settA.idVkChords, settB.idVkChords))
//                if (settA.idVkChordsBt != settB.idVkChordsBt) result.add(Triple("id_vk_chords_bt", settA.idVkChordsBt, settB.idVkChordsBt))
                if (settA.idTelegramLyrics != settB.idTelegramLyrics) result.add(Triple("id_telegram_lyrics", settA.idTelegramLyrics, settB.idTelegramLyrics))
//                if (settA.idTelegramLyricsBt != settB.idTelegramLyricsBt) result.add(Triple("id_telegram_lyrics_bt", settA.idTelegramLyricsBt, settB.idTelegramLyricsBt))
                if (settA.idTelegramKaraoke != settB.idTelegramKaraoke) result.add(Triple("id_telegram_karaoke", settA.idTelegramKaraoke, settB.idTelegramKaraoke))
//                if (settA.idTelegramKaraokeBt != settB.idTelegramKaraokeBt) result.add(Triple("id_telegram_karaoke_bt", settA.idTelegramKaraokeBt, settB.idTelegramKaraokeBt))
                if (settA.idTelegramChords != settB.idTelegramChords) result.add(Triple("id_telegram_chords", settA.idTelegramChords, settB.idTelegramChords))
//                if (settA.idTelegramChordsBt != settB.idTelegramChordsBt) result.add(Triple("id_telegram_chords_bt", settA.idTelegramChordsBt, settB.idTelegramChordsBt))
                if (settA.idStatus != settB.idStatus) result.add(Triple("id_status", settA.idStatus, settB.idStatus))
                if (settA.sourceText != settB.sourceText) result.add(Triple("source_text", settA.sourceText, settB.sourceText))
                if (settA.sourceMarkers != settB.sourceMarkers) result.add(Triple("source_markers", settA.sourceMarkers, settB.sourceMarkers))
                if (settA.statusProcessLyrics != settB.statusProcessLyrics) result.add(Triple("status_process_lyrics", settA.statusProcessLyrics, settB.statusProcessLyrics))
                if (settA.statusProcessLyricsBt != settB.statusProcessLyricsBt) result.add(Triple("status_process_lyrics_bt", settA.statusProcessLyricsBt, settB.statusProcessLyricsBt))
                if (settA.statusProcessKaraoke != settB.statusProcessKaraoke) result.add(Triple("status_process_karaoke", settA.statusProcessKaraoke, settB.statusProcessKaraoke))
                if (settA.statusProcessKaraokeBt != settB.statusProcessKaraokeBt) result.add(Triple("status_process_karaoke_bt", settA.statusProcessKaraokeBt, settB.statusProcessKaraokeBt))
                if (settA.statusProcessChords != settB.statusProcessChords) result.add(Triple("status_process_chords", settA.statusProcessChords, settB.statusProcessChords))
                if (settA.statusProcessChordsBt != settB.statusProcessChordsBt) result.add(Triple("status_process_chords_bt", settA.statusProcessChordsBt, settB.statusProcessChordsBt))
                if (settA.tags != settB.tags) result.add(Triple("tags", settA.tags, settB.tags))
            }
            return result
        }

        fun getLastUpdated(lastTime: Long? = null): List<Int> {
            if (lastTime == null) return emptyList()

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            var statement: Statement? = null
            var rs: ResultSet? = null
            val sql: String

            val result: MutableList<Int> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT id FROM tbl_settings WHERE last_update > '${Timestamp(lastTime)}'::timestamp"
                rs = statement.executeQuery(sql)
                while (rs.next()) {
                    result.add(rs.getInt("id"))
                }
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun createDbInstance(settings: Settings? = null) : Settings? {
            val sql = if (settings != null) {
                "INSERT INTO tbl_settings (" +
                        "song_name, " +
                        "song_author, " +
                        "song_album, " +
                        "publish_date, " +
                        "publish_time, " +
                        "song_year, " +
                        "song_track, " +
                        "song_tone, " +
                        "song_bpm, " +
                        "song_ms, " +
                        "file_name, " +
                        "root_folder, " +
                        "id_boosty, " +
                        "id_vk, " +
                        "id_youtube_lyrics, " +
//                        "id_youtube_lyrics_bt, " +
                        "id_youtube_karaoke, " +
//                        "id_youtube_karaoke_bt, " +
                        "id_youtube_chords, " +
//                        "id_youtube_chords_bt, " +
                        "id_vk_lyrics, " +
//                        "id_vk_lyrics_bt, " +
                        "id_vk_karaoke, " +
//                        "id_vk_karaoke_bt, " +
                        "id_vk_chords, " +
//                        "id_vk_chords_bt, " +
                        "id_telegram_lyrics, " +
//                        "id_telegram_lyrics_bt, " +
                        "id_telegram_karaoke, " +
//                        "id_telegram_karaoke_bt, " +
                        "id_telegram_chords, " +
//                        "id_telegram_chords_bt, " +
                        "id_status, " +
                        "source_text, " +
                        "source_markers, " +
                        "status_process_lyrics, " +
//                        "status_process_lyrics_bt, " +
                        "status_process_karaoke, " +
//                        "status_process_karaoke_bt, " +
                        "status_process_chords, " +
//                        "status_process_chords_bt, " +
                        "tags" +
                        ") VALUES(" +
                        "'${settings.songName.replace("'","''")}', " +
                        "'${settings.author}', " +
                        "'${settings.album}', " +
                        "'${settings.date}', " +
                        "'${settings.time}', " +
                        "${settings.year}, " +
                        "${settings.track}, " +
                        "'${settings.key}', " +
                        "${settings.bpm}, " +
                        "${settings.ms}, " +
                        "'${settings.fileName.replace("'","''")}', " +
                        "'${settings.rootFolder}', " +
                        "'${settings.idBoosty}', " +
                        "'${settings.idVk}', " +
                        "'${settings.idYoutubeLyrics}', " +
//                        "'${settings.idYoutubeLyricsBt}', " +
                        "'${settings.idYoutubeKaraoke}', " +
//                        "'${settings.idYoutubeKaraokeBt}', " +
                        "'${settings.idYoutubeChords}', " +
//                        "'${settings.idYoutubeChordsBt}', " +
                        "'${settings.idVkLyrics}', " +
//                        "'${settings.idVkLyricsBt}', " +
                        "'${settings.idVkKaraoke}', " +
//                        "'${settings.idVkKaraokeBt}', " +
                        "'${settings.idVkChords}', " +
//                        "'${settings.idVkChordsBt}', " +
                        "'${settings.idTelegramLyrics}', " +
//                        "'${settings.idTelegramLyricsBt}', " +
                        "'${settings.idTelegramKaraoke}', " +
//                        "'${settings.idTelegramKaraokeBt}', " +
                        "'${settings.idTelegramChords}', " +
//                        "'${settings.idTelegramChordsBt}', " +
                        "'${settings.idStatus}', " +
                        "'${settings.sourceText}', " +
                        "'${settings.sourceMarkers}', " +
                        "'${settings.statusProcessLyrics}', " +
//                        "'${settings.statusProcessLyricsBt}', " +
                        "'${settings.statusProcessKaraoke}', " +
//                        "'${settings.statusProcessKaraokeBt}', " +
                        "'${settings.statusProcessChords}', " +
//                        "'${settings.statusProcessChordsBt}', " +
                        "'${settings.tags}'" +
                        ")"
            } else {
                "INSERT INTO tbl_settings (song_name, song_author, id_status) VALUES('NEW SONG', 'NEW AUTHOR', 0)"
            }

            println(sql)

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                if (settings != null) {
                    settings.fields[SettingField.ID] = rs.getInt(1).toString()
                    settings
                } else {
                    val newSet = Settings()
                    newSet.fields[SettingField.ID] = rs.getInt(1).toString()
                    newSet.fields[SettingField.NAME] = "NEW SONG"
                    newSet.fields[SettingField.AUTHOR] = "NEW AUTHOR"
                    newSet.fields[SettingField.ID_STATUS] = "0"
                    newSet
                }
            } else null

            ps.close()
            connection.close()

            return result

        }

        fun loadListAuthors(): List<String> {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String

            try {
                statement = connection.createStatement()
                sql = "select DISTINCT song_author from tbl_settings order by song_author"

                rs = statement.executeQuery(sql)
                val result: MutableList<String> = mutableListOf()
                while (rs.next()) {
                    result.add(rs.getString("song_author"))
                }
                result.sort()
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun loadListAlbums(): List<String> {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String

            try {
                statement = connection.createStatement()
                sql = "select DISTINCT song_album from tbl_settings order by song_album"

                rs = statement.executeQuery(sql)
                val result: MutableList<String> = mutableListOf()
                while (rs.next()) {
                    result.add(rs.getString("song_album"))
                }
                result.sort()
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun loadListFromDb(args: Map<String, String> = emptyMap()): List<Settings> {

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT tbl_settings.*, tbl_status.status, tbl_status.color1, tbl_status.color2, tbl_status.color3, " +
                        "CASE WHEN id_boosty IS NOT NULL AND id_boosty <> 'null' AND id_boosty <> '' THEN '✓' ELSE '' END AS flag_boosty," +
                        "CASE WHEN id_vk IS NOT NULL AND id_vk <> 'null' AND id_vk <> '' THEN '✓' ELSE '' END AS flag_vk," +
                        "CASE WHEN id_youtube_lyrics IS NOT NULL AND id_youtube_lyrics <> 'null' AND id_youtube_lyrics <> '' THEN '✓' ELSE '' END AS flag_youtube_lyrics," +
//                        "CASE WHEN id_youtube_lyrics_bt IS NOT NULL AND id_youtube_lyrics_bt <> 'null' AND id_youtube_lyrics_bt <> '' THEN '✓' ELSE '' END AS flag_youtube_lyrics_bt," +
                        "CASE WHEN id_youtube_karaoke IS NOT NULL AND id_youtube_karaoke <> 'null' AND id_youtube_karaoke <> '' THEN '✓' ELSE '' END AS flag_youtube_karaoke," +
//                        "CASE WHEN id_youtube_karaoke_bt IS NOT NULL AND id_youtube_karaoke_bt <> 'null' AND id_youtube_karaoke_bt <> '' THEN '✓' ELSE '' END AS flag_youtube_karaoke_bt," +
                        "CASE WHEN id_youtube_chords IS NOT NULL AND id_youtube_chords <> 'null' AND id_youtube_chords <> '' THEN '✓' ELSE '' END AS flag_youtube_chords," +
//                        "CASE WHEN id_youtube_chords_bt IS NOT NULL AND id_youtube_chords_bt <> 'null' AND id_youtube_chords_bt <> '' THEN '✓' ELSE '' END AS flag_youtube_chords_bt, " +
                        "CASE WHEN id_vk_lyrics IS NOT NULL AND id_vk_lyrics <> 'null' AND id_vk_lyrics <> '' THEN '✓' ELSE '' END AS flag_vk_lyrics," +
//                        "CASE WHEN id_vk_lyrics_bt IS NOT NULL AND id_vk_lyrics_bt <> 'null' AND id_vk_lyrics_bt <> '' THEN '✓' ELSE '' END AS flag_vk_lyrics_bt," +
                        "CASE WHEN id_vk_karaoke IS NOT NULL AND id_vk_karaoke <> 'null' AND id_vk_karaoke <> '' THEN '✓' ELSE '' END AS flag_vk_karaoke," +
//                        "CASE WHEN id_vk_karaoke_bt IS NOT NULL AND id_vk_karaoke_bt <> 'null' AND id_vk_karaoke_bt <> '' THEN '✓' ELSE '' END AS flag_vk_karaoke_bt," +
                        "CASE WHEN id_vk_chords IS NOT NULL AND id_vk_chords <> 'null' AND id_vk_chords <> '' THEN '✓' ELSE '' END AS flag_vk_chords," +
//                        "CASE WHEN id_vk_chords_bt IS NOT NULL AND id_vk_chords_bt <> 'null' AND id_vk_chords_bt <> '' THEN '✓' ELSE '' END AS flag_vk_chords_bt, " +
                        "CASE WHEN id_telegram_lyrics IS NOT NULL AND id_telegram_lyrics <> 'null' AND id_telegram_lyrics <> '' THEN '✓' ELSE '' END AS flag_telegram_lyrics," +
//                        "CASE WHEN id_telegram_lyrics_bt IS NOT NULL AND id_telegram_lyrics_bt <> 'null' AND id_telegram_lyrics_bt <> '' THEN '✓' ELSE '' END AS flag_telegram_lyrics_bt," +
                        "CASE WHEN id_telegram_karaoke IS NOT NULL AND id_telegram_karaoke <> 'null' AND id_telegram_karaoke <> '' THEN '✓' ELSE '' END AS flag_telegram_karaoke," +
//                        "CASE WHEN id_telegram_karaoke_bt IS NOT NULL AND id_telegram_karaoke_bt <> 'null' AND id_telegram_karaoke_bt <> '' THEN '✓' ELSE '' END AS flag_telegram_karaoke_bt," +
                        "CASE WHEN id_telegram_chords IS NOT NULL AND id_telegram_chords <> 'null' AND id_telegram_chords <> '' THEN '✓' ELSE '' END AS flag_telegram_chords," +
//                        "CASE WHEN id_telegram_chords_bt IS NOT NULL AND id_telegram_chords_bt <> 'null' AND id_telegram_chords_bt <> '' THEN '✓' ELSE '' END AS flag_telegram_chords_bt, " +
                        "    CASE WHEN id_status < 6 THEN color1\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') = CURRENT_DATE AND\n" +
                        "              id_telegram_lyrics = '' AND\n" +
                        "              id_telegram_karaoke = '-' AND\n" +
                        "              id_vk_lyrics != '' AND\n" +
                        "              id_vk_karaoke != '' THEN '#FFA500'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') < CURRENT_DATE AND\n" +
                        "              id_telegram_lyrics = '' AND\n" +
                        "              id_telegram_karaoke = '-' AND\n" +
                        "              id_vk_lyrics != '' AND\n" +
                        "              id_vk_karaoke != '' THEN '#BDB76B'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') > CURRENT_DATE AND\n" +
                        "              id_telegram_lyrics = '' AND\n" +
                        "              id_telegram_karaoke = '-' AND\n" +
                        "              id_vk_lyrics != '' AND\n" +
                        "              id_vk_karaoke != '' THEN '#DCDCDC'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') > CURRENT_DATE AND\n" +
                        "              id_telegram_lyrics = '' AND\n" +
                        "              id_telegram_karaoke = '' AND\n" +
                        "              id_vk_lyrics != '' AND\n" +
                        "              id_vk_karaoke != '' THEN '#87CEFA'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') < CURRENT_DATE AND\n" +
                        "              id_telegram_lyrics != '' AND\n" +
                        "              id_telegram_lyrics IS NOT NULL AND\n" +
                        "              id_telegram_karaoke != '' AND\n" +
                        "              id_telegram_karaoke IS NOT NULL AND\n" +
                        "              id_vk_lyrics != '' AND\n" +
                        "              id_vk_lyrics IS NOT NULL AND\n" +
                        "              id_vk_karaoke != '' AND\n" +
                        "              id_vk_karaoke IS NOT NULL THEN '#7FFFD4'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') < CURRENT_DATE AND\n" +
                        "              id_telegram_lyrics != '' AND\n" +
                        "              id_telegram_lyrics IS NOT NULL AND\n" +
                        "              id_telegram_karaoke != '' AND\n" +
                        "              id_telegram_karaoke IS NOT NULL THEN '#1E90FF'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') < CURRENT_DATE AND\n" +
                        "              id_vk_lyrics != '' AND\n" +
                        "              id_vk_lyrics IS NOT NULL AND\n" +
                        "              id_vk_karaoke != '' AND\n" +
                        "              id_vk_karaoke IS NOT NULL THEN '#BDB76B'\n" +
                        "        WHEN to_date(publish_date, 'DD.MM.YY') < CURRENT_DATE AND\n" +
                        "              id_boosty != '' AND\n" +
                        "              id_boosty IS NOT NULL THEN '#98FB98'\n" +
                        "         WHEN to_date(publish_date, 'DD.MM.YY') = CURRENT_DATE THEN '#FFFF00'\n" +
                        "         WHEN to_date(publish_date, 'DD.MM.YY') > CURRENT_DATE AND\n" +
                        "              id_vk != '' AND\n" +
                        "              id_vk IS NOT NULL THEN '#FFDAB9'\n" +
                        "         WHEN to_date(publish_date, 'DD.MM.YY') > CURRENT_DATE AND\n" +
                        "              id_boosty != '' AND\n" +
                        "              id_boosty IS NOT NULL THEN '#FFEFD5'\n" +
                        "        ELSE color1\n" +
                        "    END AS color" +
                        " FROM tbl_settings JOIN tbl_status ON id_status = tbl_status.id"
                if (args.containsKey("id")) where += "tbl_settings.id=${args["id"]}"
                if (args.containsKey("file_name")) where += "LOWER(file_name)='${args["file_name"]?.replace("'","''")?.lowercase()}'"
                if (args.containsKey("root_folder")) where += "LOWER(root_folder)='${args["root_folder"]?.replace("'","''")?.lowercase()}'"
                if (args.containsKey("song_name")) where += "LOWER(song_name) LIKE '%${args["song_name"]?.replace("'","''")?.lowercase()}%'"
                if (args.containsKey("song_author")) where += "LOWER(song_author) LIKE '%${args["song_author"]?.replace("'","''")?.lowercase()}%'"
                if (args.containsKey("song_album")) where += "LOWER(song_album) LIKE '%${args["song_album"]?.replace("'","''")?.lowercase()}%'"
                if (args.containsKey("publish_date")) where += "publish_date LIKE '%${args["publish_date"]}%'"
                if (args.containsKey("publish_time")) where += "publish_time LIKE '%${args["publish_time"]}%'"
                if (args.containsKey("status")) where += "status LIKE '%${args["status"]}%'"
                if (args.containsKey("song_bpm")) where += "song_bpm=${args["song_bpm"]}"
                if (args.containsKey("song_tone")) where += "song_tone=${args["song_tone"]}"
                if (args.containsKey("song_year")) where += "song_year=${args["song_year"]}"
                if (args.containsKey("song_track")) where += "song_track=${args["song_track"]}"
                if (args.containsKey("id_status")) where += "id_status=${args["id_status"]}"
                if (args.containsKey("flag_boosty")) where += "CASE WHEN id_boosty IS NOT NULL AND id_boosty <> 'null' AND id_boosty <> '' THEN '+' ELSE '-' END='${args["flag_boosty"]}'"
                if (args.containsKey("flag_vk")) where += "CASE WHEN id_vk IS NOT NULL AND id_vk <> 'null' AND id_vk <> '' THEN '+' ELSE '-' END='${args["flag_vk"]}'"
                if (args.containsKey("flag_youtube_lyrics")) where += "CASE WHEN id_youtube_lyrics IS NOT NULL AND id_youtube_lyrics <> 'null' AND id_youtube_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_youtube_lyrics"]}'"
//                if (args.containsKey("flag_youtube_lyrics_bt")) where += "CASE WHEN id_youtube_lyrics_bt IS NOT NULL AND id_youtube_lyrics_bt <> 'null' AND id_youtube_lyrics_bt <> '' THEN '+' ELSE '-' END='${args["flag_youtube_lyrics_bt"]}'"
                if (args.containsKey("flag_youtube_karaoke")) where += "CASE WHEN id_youtube_karaoke IS NOT NULL AND id_youtube_karaoke <> 'null' AND id_youtube_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_youtube_karaoke"]}'"
//                if (args.containsKey("flag_youtube_karaoke_bt")) where += "CASE WHEN id_youtube_karaoke_bt IS NOT NULL AND id_youtube_karaoke_bt <> 'null' AND id_youtube_karaoke_bt <> '' THEN '+' ELSE '-' END='${args["flag_youtube_karaoke_bt"]}'"
                if (args.containsKey("flag_youtube_chords")) where += "CASE WHEN id_youtube_chords IS NOT NULL AND id_youtube_chords <> 'null' AND id_youtube_chords <> '' THEN '+' ELSE '-' END='${args["flag_youtube_chords"]}'"
//                if (args.containsKey("flag_youtube_chords_bt")) where += "CASE WHEN id_youtube_chords_bt IS NOT NULL AND id_youtube_chords_bt <> 'null' AND id_youtube_chords_bt <> '' THEN '+' ELSE '-' END='${args["flag_youtube_chords_bt"]}'"
                if (args.containsKey("flag_vk_lyrics")) where += "CASE WHEN id_vk_lyrics IS NOT NULL AND id_vk_lyrics <> 'null' AND id_vk_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_vk_lyrics"]}'"
//                if (args.containsKey("flag_vk_lyrics_bt")) where += "CASE WHEN id_vk_lyrics_bt IS NOT NULL AND id_vk_lyrics_bt <> 'null' AND id_vk_lyrics_bt <> '' THEN '+' ELSE '-' END='${args["flag_vk_lyrics_bt"]}'"
                if (args.containsKey("flag_vk_karaoke")) where += "CASE WHEN id_vk_karaoke IS NOT NULL AND id_vk_karaoke <> 'null' AND id_vk_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_vk_karaoke"]}'"
//                if (args.containsKey("flag_vk_karaoke_bt")) where += "CASE WHEN id_vk_karaoke_bt IS NOT NULL AND id_vk_karaoke_bt <> 'null' AND id_vk_karaoke_bt <> '' THEN '+' ELSE '-' END='${args["flag_vk_karaoke_bt"]}'"
                if (args.containsKey("flag_vk_chords")) where += "CASE WHEN id_vk_chords IS NOT NULL AND id_vk_chords <> 'null' AND id_vk_chords <> '' THEN '+' ELSE '-' END='${args["flag_vk_chords"]}'"
//                if (args.containsKey("flag_vk_chords_bt")) where += "CASE WHEN id_vk_chords_bt IS NOT NULL AND id_vk_chords_bt <> 'null' AND id_vk_chords_bt <> '' THEN '+' ELSE '-' END='${args["flag_vk_chords_bt"]}'"
                if (args.containsKey("flag_telegram_lyrics")) where += "CASE WHEN id_telegram_lyrics IS NOT NULL AND id_telegram_lyrics <> 'null' AND id_telegram_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_telegram_lyrics"]}'"
//                if (args.containsKey("flag_telegram_lyrics_bt")) where += "CASE WHEN id_telegram_lyrics_bt IS NOT NULL AND id_telegram_lyrics_bt <> 'null' AND id_telegram_lyrics_bt <> '' THEN '+' ELSE '-' END='${args["flag_telegram_lyrics_bt"]}'"
                if (args.containsKey("flag_telegram_karaoke")) where += "CASE WHEN id_telegram_karaoke IS NOT NULL AND id_telegram_karaoke <> 'null' AND id_telegram_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_telegram_karaoke"]}'"
//                if (args.containsKey("flag_telegram_karaoke_bt")) where += "CASE WHEN id_telegram_karaoke_bt IS NOT NULL AND id_telegram_karaoke_bt <> 'null' AND id_telegram_karaoke_bt <> '' THEN '+' ELSE '-' END='${args["flag_telegram_karaoke_bt"]}'"
                if (args.containsKey("flag_telegram_chords")) where += "CASE WHEN id_telegram_chords IS NOT NULL AND id_telegram_chords <> 'null' AND id_telegram_chords <> '' THEN '+' ELSE '-' END='${args["flag_telegram_chords"]}'"
//                if (args.containsKey("flag_telegram_chords_bt")) where += "CASE WHEN id_telegram_chords_bt IS NOT NULL AND id_telegram_chords_bt <> 'null' AND id_telegram_chords_bt <> '' THEN '+' ELSE '-' END='${args["flag_telegram_chords_bt"]}'"
                if (args.containsKey("tags")) where += "LOWER(tags) LIKE '%${args["tags"]?.replace("'","''")?.lowercase()}%'"

                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"

                sql += " ORDER BY tbl_settings.id"

//                println(where)
//                println(sql)

                rs = statement.executeQuery(sql)
                val result: MutableList<Settings> = mutableListOf()
                var prevAlbum = ""
                while (rs.next()) {
                    val settings = Settings()
                    settings.fileName = rs.getString("file_name")
                    settings.rootFolder = rs.getString("root_folder")
                    rs.getInt("id")?.let { value -> settings.fields[SettingField.ID] = value.toString() }
                    rs.getInt("id_status")?.let { value -> settings.fields[SettingField.ID_STATUS] = value.toString() }
                    rs.getString("song_name")?.let { value -> settings.fields[SettingField.NAME] = value }
                    rs.getString("song_author")?.let { value -> settings.fields[SettingField.AUTHOR] = value }
                    rs.getString("song_album")?.let { value ->
                        settings.fields[SettingField.ALBUM] = value
                        if (value != prevAlbum) {
                            prevAlbum = value
                            settings.firstSongInAlbum = true
                        }
                    }
                    rs.getString("publish_date")?.let { value -> settings.fields[SettingField.DATE] = value }
                    rs.getString("publish_time")?.let { value -> settings.fields[SettingField.TIME] = value }
                    rs.getInt("song_year")?.let { value -> settings.fields[SettingField.YEAR] = value.toString() }
                    rs.getInt("song_track")?.let { value -> settings.fields[SettingField.TRACK] = value.toString() }
                    rs.getString("song_tone")?.let { value -> settings.fields[SettingField.KEY] = value }
                    rs.getInt("song_bpm")?.let { value -> settings.fields[SettingField.BPM] = value.toString() }
                    rs.getInt("song_ms")?.let { value -> settings.fields[SettingField.MS] = value.toString() }
                    rs.getString("id_boosty")?.let { value -> settings.fields[SettingField.ID_BOOSTY] = value }
                    rs.getString("id_vk")?.let { value -> settings.fields[SettingField.ID_VK] = value }
                    rs.getString("id_youtube_lyrics")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_LYRICS] = value }
//                    rs.getString("id_youtube_lyrics_bt")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_LYRICS_BT] = value }
                    rs.getString("id_youtube_karaoke")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_KARAOKE] = value }
//                    rs.getString("id_youtube_karaoke_bt")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_KARAOKE_BT] = value }
                    rs.getString("id_youtube_chords")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_CHORDS] = value }
//                    rs.getString("id_youtube_chords_bt")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_CHORDS_BT] = value }
                    rs.getString("id_vk_lyrics")?.let { value -> settings.fields[SettingField.ID_VK_LYRICS] = value }
//                    rs.getString("id_vk_lyrics_bt")?.let { value -> settings.fields[SettingField.ID_VK_LYRICS_BT] = value }
                    rs.getString("id_vk_karaoke")?.let { value -> settings.fields[SettingField.ID_VK_KARAOKE] = value }
//                    rs.getString("id_vk_karaoke_bt")?.let { value -> settings.fields[SettingField.ID_VK_KARAOKE_BT] = value }
                    rs.getString("id_vk_chords")?.let { value -> settings.fields[SettingField.ID_VK_CHORDS] = value }
//                    rs.getString("id_vk_chords_bt")?.let { value -> settings.fields[SettingField.ID_VK_CHORDS_BT] = value }
                    rs.getString("id_telegram_lyrics")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_LYRICS] = value }
//                    rs.getString("id_telegram_lyrics_bt")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_LYRICS_BT] = value }
                    rs.getString("id_telegram_karaoke")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_KARAOKE] = value }
//                    rs.getString("id_telegram_karaoke_bt")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_KARAOKE_BT] = value }
                    rs.getString("id_telegram_chords")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_CHORDS] = value }
//                    rs.getString("id_telegram_chords_bt")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_CHORDS_BT] = value }
                    rs.getString("color")?.let { value -> settings.fields[SettingField.COLOR] = value }
                    rs.getString("source_text")?.let { value -> settings.sourceText = value }
                    rs.getString("source_markers")?.let { value -> settings.sourceMarkers = value }
                    settings.statusProcessLyrics = rs.getString("status_process_lyrics") ?: ""
//                    settings.statusProcessLyricsBt = rs.getString("status_process_lyrics_bt") ?: ""
                    settings.statusProcessKaraoke = rs.getString("status_process_karaoke") ?: ""
//                    settings.statusProcessKaraokeBt = rs.getString("status_process_karaoke_bt") ?: ""
                    settings.statusProcessChords = rs.getString("status_process_chords") ?: ""
//                    settings.statusProcessChordsBt = rs.getString("status_process_chords_bt") ?: ""
                    settings.tags = rs.getString("tags") ?: ""

                    result.add(settings)

                }
                result.sort()
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun loadFromDbById(id: Long): Settings? {

            return loadListFromDb(mapOf(Pair("id", id.toString()))).firstOrNull()

        }


        fun loadFromOds(author: String, songName: String, spreadsheetDocument: SpreadsheetDocument): Settings {
            val settings = Settings()
            val mapFromOds = Ods.getSettingFields(author, songName, spreadsheetDocument)
            mapFromOds?.let {
                mapFromOds.forEach{ (mapKey, mapValue) ->
                    settings.fields[mapKey] = mapValue
                }
            }
            return settings
        }
        fun loadFromFile(pathToSettingsFile: String, readonly: Boolean = false): Settings {
            val settings = Settings()
            settings.readonly = readonly
            val settingFilePath = Path(pathToSettingsFile)
            val settingRoot = settingFilePath.parent.toString()
            val settingFileNameList = settingFilePath.fileName.toString()
                .split(".")
                .toMutableList()
            settingFileNameList.removeLast()
            val settingFileName = settingFileNameList.joinToString(".")

            settings.rootFolder = settingRoot
            settings.fileName = settingFileName

            val body = File(pathToSettingsFile).readText(Charsets.UTF_8)
            body.split("\n").forEach { line ->
                val settingList = line.split("=")
                if (settingList.size > 1 ) {
                    val settingName = settingList[0].uppercase()
                    val settingValue = settingList[1] + (if (settingList.size == 3) "="+settingList[2] else "")
                    val settingField = if (SettingField.values().map { it.name }.contains(settingName)) SettingField.valueOf(settingName) else null
                    settingField?.let { settings.fields[settingField] = settingValue.trim() }
                }
            }
            return settings
        }

        fun createFromPath(startFolder: String): MutableList<Settings> {
            val result: MutableList<Settings> = mutableListOf()
            val listFiles = getListFiles(startFolder,"flac")
            listFiles.forEach { pathToFile ->

                val file = File(pathToFile)
                val fileName = file.nameWithoutExtension
                val rootFolder = file.parent
                val albumFolder = file.parentFile.name

                val regexFile = Regex("(\\d+)\\s+\\((\\d+)\\)\\s+\\[([\\S|\\s]+)\\]\\s+-\\s+([\\S|\\s]+)")
                val regexAlbum = Regex("(\\d+)\\s+-\\s+([\\S|\\s]+)")
                val matchResultAlbum = regexAlbum.find(albumFolder)
                val matchResultFile = regexFile.find(fileName)
                if (matchResultAlbum != null) {
                    val yearStr = matchResultAlbum.groupValues[1]
                    val albumStr = matchResultAlbum.groupValues[2]
                    if (matchResultFile != null) {
                        val numberStr = matchResultFile.groupValues[2]
                        val authorStr = matchResultFile.groupValues[3]
                        val songNameStr = matchResultFile.groupValues[4]

                        if (loadListFromDb(
                                mapOf(
                                    Pair("file_name", fileName),
                                    Pair("root_folder", rootFolder)
                                )
                            ).isEmpty()
                        ) {
                            val settings = Settings()
                            settings.fileName = fileName
                            settings.rootFolder = rootFolder
                            settings.fields[SettingField.NAME] = songNameStr
                            settings.fields[SettingField.YEAR] = yearStr
                            settings.fields[SettingField.ALBUM] = albumStr
                            settings.fields[SettingField.TRACK] = numberStr
                            settings.fields[SettingField.AUTHOR] = authorStr
                            settings.saveToDb()
                            result.add(settings)

                        }

                    }
                }

            }
            return result
        }

        fun getSetOfTags(): Set<String> {

            val result: MutableSet<String> = mutableSetOf()

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(CONNECTION_URL, CONNECTION_USER, CONNECTION_PASSWORD)
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String

            try {
                statement = connection.createStatement()
                sql = "select DISTINCT tbl_settings.tags as tags " +
                        "from tbl_settings " +
                        "where tags != '' AND tags IS NOT NULL"

                rs = statement.executeQuery(sql)
                while (rs.next()) {
                    result.addAll(rs.getString("tags").split(" ").map { it.uppercase() })
                }
                return result
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptySet()
        }

        fun setPublishDateTimeToAuthor(startSettings: Settings) {
            var publishDate = SimpleDateFormat("dd.MM.yy").parse(startSettings.date)
            val publishTime = startSettings.time
            val listOfSettings = loadListFromDb(mapOf(Pair("song_author", startSettings.author))).filter { it.id > startSettings.id }
            listOfSettings.forEach { settings ->
                val calendar = Calendar.getInstance()
                calendar.time = publishDate
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                publishDate = calendar.time

                settings.fields[SettingField.DATE] = SimpleDateFormat("dd.MM.yy").format(publishDate)
                settings.fields[SettingField.TIME] = publishTime

                settings.saveToDb()

            }
        }

    }

    override fun compareTo(other: Settings): Int {
        try {
            if (dateTimePublish != null && other.dateTimePublish != null) {
                return dateTimePublish!!.compareTo(other.dateTimePublish)
            }
        } catch (e: Exception) {
            return id.compareTo(other.id)
        }
        return id.compareTo(other.id)
    }

}
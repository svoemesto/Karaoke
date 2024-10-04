package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.services.SNS
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.sql.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date
import kotlin.io.path.Path


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
    ID_BOOSTY_FILES,
    ID_VK,
    ID_YOUTUBE_LYRICS,
    ID_YOUTUBE_KARAOKE,
    ID_YOUTUBE_CHORDS,
    ID_VK_LYRICS,
    ID_VK_KARAOKE,
    ID_VK_CHORDS,
    ID_STATUS,
    COLOR,
    SOURCE_TEXT,
    RESULT_TEXT,
    SOURCE_MARKERS,
    ID_TELEGRAM_LYRICS,
    ID_TELEGRAM_KARAOKE,
    ID_TELEGRAM_CHORDS,
    ID_PL_LYRICS,
    ID_PL_KARAOKE,
    ID_PL_CHORDS,
    RESULT_VERSION,
    DIFFBEATS
}


@kotlinx.serialization.Serializable
data class SourceMarker(
    var time: Double,
    var label: String = "",
    var note: String = "",
    var chord: String = "",
    var tag: String = "",
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

//@Component
@JsonIgnoreProperties(value = ["database", "pictureAuthor", "pictureAlbum"])
class Settings(val database: KaraokeConnection = WORKING_DATABASE): Serializable, Comparable<Settings> {

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

    val sortString: String get() {
        return if (dateTimePublish == null) {
            listOf(
                author, year.toString(), album, "%3d".format(track)
            ).joinToString(" - ")
        } else
        {
            "%15d".format(dateTimePublish!!.time)
        }
    }

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
        }
        set(value) {fields[SettingField.SOURCE_TEXT] = value}

    var resultText: String
        get() {
            val txt = fields[SettingField.RESULT_TEXT] ?: ""
            return txt
        }
        set(value) {fields[SettingField.RESULT_TEXT] = value}

    var statusProcessLyrics: String = ""
    var statusProcessLyricsBt: String = ""
    var statusProcessKaraoke: String = ""
    var statusProcessKaraokeBt: String = ""
    var statusProcessChords: String = ""
    var statusProcessChordsBt: String = ""

    @get:JsonIgnore
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

    @get:JsonIgnore
    val sourceSyllables: String get() {
        return Json.encodeToString(sourceSyllablesList)
    }
    @get:JsonIgnore
    val sourceSyllablesList: List<List<String>> get() {
        val result: MutableList<List<String>> = mutableListOf()
        sourceTextList.forEachIndexed{ index, _ ->
            val voiceText = getSourceText(index)
            result.add(getSyllables(voiceText))
        }
        return result
    }

    @get:JsonIgnore
    var sourceMarkers: String
        get() = if (fields[SettingField.SOURCE_MARKERS] == null || fields[SettingField.SOURCE_MARKERS] == "") "[[]]" else fields[SettingField.SOURCE_MARKERS]!!
        set(value) {fields[SettingField.SOURCE_MARKERS] = value}
    @get:JsonIgnore
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

    @get:JsonIgnore
    val countVoices: Int get() = sourceMarkersList.size

    @get:JsonIgnore
    val sourceUnmute: List<Pair<Double, Double>> get() {
        if (sourceMarkersList.isEmpty()) return emptyList()
        val unMuteTimeList = sourceMarkersList[0].filter { it.markertype == "unmute" }.map { it.time }
        if (unMuteTimeList.isEmpty()) return emptyList()
        if (unMuteTimeList.size % 2 != 0) return emptyList()
        val result: MutableList<Pair<Double, Double>> = mutableListOf()
        for (i in unMuteTimeList.indices step 2) {
            result.add(Pair(unMuteTimeList[i], unMuteTimeList[i+1]))
        }
        return result
    }


    val pathToFileLyrics: String  get() = "${rootFolder.rightFileName()}/done_files/$nameFileLyrics"
    val pathToFileKaraoke: String  get() = "${rootFolder.rightFileName()}/done_files/$nameFileKaraoke"
    val pathToFileChords: String  get() = "${rootFolder.rightFileName()}/done_files/$nameFileChords"

    val pathToFile720Lyrics: String  get() = "$pathToFolder720Lyrics/${nameFileLyrics.replace(" [lyrics].mp4", " [lyrics] 720p.mp4")}"
    val pathToFileMP3Lyrics: String  get() = "$pathToFolderMP3Lyrics/${nameFileLyrics.replace(" [lyrics].mp4", " [lyrics].mp3")}"
    val pathToFile720Karaoke: String  get() = "$pathToFolder720Karaoke/${nameFileKaraoke.replace(" [karaoke].mp4", " [karaoke] 720p.mp4")}"
    val pathToFileMP3Karaoke: String  get() = "$pathToFolderMP3Karaoke/${nameFileKaraoke.replace(" [karaoke].mp4", " [karaoke].mp3")}"
    val pathToFile720Chords: String  get() = "$pathToFolder720Chords/${nameFileChords.replace(" [chords].mp4", " [chords] 720p.mp4")}"

    val pathToFolder720Lyrics: String  get() = "$PATH_TO_STORE_FOLDER/720p_Lyrics/${author} 720p"
    val pathToFolderMP3Lyrics: String  get() = "$PATH_TO_STORE_FOLDER/MP3_Lyrics/${author} MP3"
    val pathToFolder720Karaoke: String  get() = "$PATH_TO_STORE_FOLDER/720p_Karaoke/${author} 720p"
    val pathToFolderMP3Karaoke: String  get() = "$PATH_TO_STORE_FOLDER/MP3_Karaoke/${author} MP3"
    val pathToFolder720Chords: String  get() = "$PATH_TO_STORE_FOLDER/720p_Chords/${author} 720p"

    val pathToStoreFileLyrics: String  get() = "$pathToStoreFolderLyrics/$nameFileLyrics"
    val pathToStoreFileKaraoke: String  get() = "$pathToStoreFolderKaraoke/$nameFileKaraoke"
    val pathToStoreFileChords: String  get() = "$pathToStoreFolderChords/$nameFileChords"

    val pathToStoreFolderLyrics: String  get() = "$PATH_TO_STORE_FOLDER/Lyrics/${author} - Lyrics"
    val pathToStoreFolderKaraoke: String  get() = "$PATH_TO_STORE_FOLDER/Karaoke/${author} - Karaoke"
    val pathToStoreFolderChords: String  get() = "$PATH_TO_STORE_FOLDER/Chords/${author} - Chords"

    val nameFileLogoAlbum: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [album].png"
    val nameFileLogoAuthor: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [author].png"
    val nameFileLyrics: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [lyrics].mp4"
    val nameFileKaraoke: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [karaoke].mp4"
    val nameFileChords: String  get() = "$${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [chords].mp4"

    val pathToFolderSheetsage: String  get() = "${rootFolder.rightFileName()}/sheetsage"
    val nameFileSheetsagePDF: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [sheetsage].pdf"
    val nameFileSheetsageMIDI: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [sheetsage].midi"
    val nameFileSheetsageLY: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [sheetsage].ly"
    val nameFileSheetsageBeattimes: String  get() = "${if (!fileName.startsWith (year.toString())) "${year} " else ""}${fileName.rightFileName()} [beattimes].txt"
    val pathToFileSheetsagePDF: String  get() = "$pathToFolderSheetsage/$nameFileSheetsagePDF"
    val pathToFileSheetsageMIDI: String  get() = "$pathToFolderSheetsage/$nameFileSheetsageMIDI"
    val pathToFileSheetsageLY: String  get() = "$pathToFolderSheetsage/$nameFileSheetsageLY"
    val pathToFileSheetsageBeattimes: String  get() = "$pathToFolderSheetsage/$nameFileSheetsageBeattimes"

    @get:JsonIgnore
    val pathToFileLogoAlbum: String  get() {
        var path = "$rootFolder/$nameFileLogoAlbum"
        if (File(path).exists()) return path
        path = "$rootFolder/LogoAlbum.png"
        if (File(path).exists()) return path
        path = "${File(rootFolder).parentFile.absolutePath}/LogoAlbum.png"
        if (File(path).exists()) return path
        return ""
    }

    @get:JsonIgnore
    val pathToFileLogoAuthor: String  get() {
        var path = "$rootFolder/$nameFileLogoAuthor"
        if (File(path).exists()) return path
        path = "$rootFolder/LogoAuthor.png"
        if (File(path).exists()) return path
        path = "${File(rootFolder).parentFile.absolutePath}/LogoAuthor.png"
        if (File(path).exists()) return path
        return ""
    }

    val color: String get() = fields[SettingField.COLOR] ?: ""
    val songName: String get() = fields[SettingField.NAME] ?: ""
    val songNameCensored: String get() = songName.censored()
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
    val onAir: Boolean get() = (dateTimePublish != null && dateTimePublish!! <= Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time)
    val year: Long get() = fields[SettingField.YEAR]?.toLongOrNull() ?: 0L
    val track: Long get() = fields[SettingField.TRACK]?.toLongOrNull() ?: 0L
    val key: String get() = fields[SettingField.KEY] ?: ""
    val bpm: Long get() = fields[SettingField.BPM]?.toLongOrNull() ?: 0L
    val ms: Long get() = fields[SettingField.MS]?.toLongOrNull() ?: 0L
    val resultVersion: Long get() = fields[SettingField.RESULT_VERSION]?.toLongOrNull() ?: 0L
    val diffBeats: Long get() = fields[SettingField.DIFFBEATS]?.toLongOrNull() ?: 0L
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


    val pictureNameAuthor: String get() = author
    val pictureNameAlbum: String get() = "$author - $year - $album"

    @get:JsonIgnore
    val pictureAuthor: Pictures? get() {
        var pic = Pictures.load(pictureNameAuthor, database)
        if (pic == null) {
            val pathToFile = pathToFileLogoAuthor
            if (pathToFile != "") {
                val fullPicture = java.util.Base64.getEncoder().encodeToString(File(pathToFile).inputStream().readAllBytes())
                val pict = Pictures(database)
                pict.name = pictureNameAuthor
                pict.full = fullPicture
                pic = Pictures.createDbInstance(pict, database)
            }
        }
        return pic
    }

    @get:JsonIgnore
    val pictureAlbum: Pictures? get() {
        var pic = Pictures.load(pictureNameAlbum, database)
        if (pic == null) {
            val pathToFile = pathToFileLogoAlbum
            if (pathToFile != "") {
                val fullPicture = java.util.Base64.getEncoder().encodeToString(File(pathToFile).inputStream().readAllBytes())
                val pict = Pictures(database)
                pict.name = pictureNameAlbum
                pict.full = fullPicture
                pic = Pictures.createDbInstance(pict, database)
            }
        }
        return pic
    }

    val projectLyricsFileName: String get() = "$fileName [lyrics].kdenlive"
    val videoLyricsFileName: String get() = "done/$year $fileName [lyrics].mp4"
    val projectKaraokeFileName: String get() = "$fileName [karaoke].kdenlive"
    val videoKaraokeFileName: String get() = "done/$year $fileName [karaoke].mp4"
    val projectChordsFileName: String get() = "$fileName [chords].kdenlive"
    val videoChordsFileName: String get() = "done/$year $fileName [chords].mp4"
    @get:JsonIgnore
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
    @get:JsonIgnore
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
    @get:JsonIgnore
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
    @get:JsonIgnore
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
    val idBoostyFiles: String get() = fields[SettingField.ID_BOOSTY_FILES]?.nullIfEmpty() ?: ""
    val idVk: String get() = fields[SettingField.ID_VK]?.nullIfEmpty() ?: ""
    val idYoutubeLyrics: String get() = fields[SettingField.ID_YOUTUBE_LYRICS]?.nullIfEmpty() ?: ""
    val idYoutubeKaraoke: String get() = fields[SettingField.ID_YOUTUBE_KARAOKE]?.nullIfEmpty() ?: ""
    val idYoutubeChords: String get() = fields[SettingField.ID_YOUTUBE_CHORDS]?.nullIfEmpty() ?: ""

    val idVkLyrics: String get() = fields[SettingField.ID_VK_LYRICS]?.nullIfEmpty() ?: ""
    val idVkLyricsOID: String get() = if (idVkLyrics!="" && idVkLyrics.contains("_")) idVkLyrics.split("_")[0] else ""
    val idVkLyricsID: String get() = if (idVkLyrics!="" && idVkLyrics.contains("_")) idVkLyrics.split("_")[1] else ""
    val idVkKaraoke: String get() = fields[SettingField.ID_VK_KARAOKE]?.nullIfEmpty() ?: ""
    val idVkKaraokeOID: String get() = if (idVkKaraoke!="" && idVkKaraoke.contains("_")) idVkKaraoke.split("_")[0] else ""
    val idVkKaraokeID: String get() = if (idVkKaraoke!="" && idVkKaraoke.contains("_")) idVkKaraoke.split("_")[1] else ""
    val idVkChords: String get() = fields[SettingField.ID_VK_CHORDS]?.nullIfEmpty() ?: ""
    val idVkChordsOID: String get() = if (idVkChords!="" && idVkChords.contains("_")) idVkChords.split("_")[0] else ""
    val idVkChordsID: String get() = if (idVkChords!="" && idVkChords.contains("_")) idVkChords.split("_")[1] else ""
    val idTelegramLyrics: String get() = fields[SettingField.ID_TELEGRAM_LYRICS]?.nullIfEmpty() ?: ""
    val idTelegramKaraoke: String get() = fields[SettingField.ID_TELEGRAM_KARAOKE]?.nullIfEmpty() ?: ""
    val idTelegramChords: String get() = fields[SettingField.ID_TELEGRAM_CHORDS]?.nullIfEmpty() ?: ""
    val idPlLyrics: String get() = fields[SettingField.ID_PL_LYRICS]?.nullIfEmpty() ?: ""
    val idPlKaraoke: String get() = fields[SettingField.ID_PL_KARAOKE]?.nullIfEmpty() ?: ""
    val idPlChords: String get() = fields[SettingField.ID_PL_CHORDS]?.nullIfEmpty() ?: ""

    val linkSM: String get() = URL_PREFIX_SM.replace("{REPLACE}", id.toString())
    val linkBoosty: String? get() = idBoosty?.let {URL_PREFIX_BOOSTY.replace("{REPLACE}", idBoosty!!)}
    val linkBoostyFiles: String? get() = idBoostyFiles?.let {URL_PREFIX_BOOSTY.replace("{REPLACE}", idBoostyFiles!!)}
    val linkVk: String? get() = idVk?.let {URL_PREFIX_VK.replace("{REPLACE}", idVk!!)}
    val linkYoutubeLyricsPlay: String? get() = idYoutubeLyrics?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeLyrics!!)}
    val linkYoutubeLyricsEdit: String? get() = idYoutubeLyrics?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeLyrics!!)}

    val linkYoutubeKaraokePlay: String? get() = idYoutubeKaraoke?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeKaraoke!!)}
    val linkYoutubeKaraokeEdit: String? get() = idYoutubeKaraoke?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeKaraoke!!)}

    val linkYoutubeChordsPlay: String? get() = idYoutubeChords?.let {URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", idYoutubeChords!!)}
    val linkYoutubeChordsEdit: String? get() = idYoutubeChords?.let {URL_PREFIX_YOUTUBE_EDIT.replace("{REPLACE}", idYoutubeChords!!)}

    val linkVkLyricsPlay: String? get() = idVkLyrics?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkLyrics!!)}
    val linkVkLyricsEdit: String? get() = idVkLyrics?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkLyrics!!)}

    val linkVkKaraokePlay: String? get() = idVkKaraoke?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkKaraoke!!)}
    val linkVkKaraokeEdit: String? get() = idVkKaraoke?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkKaraoke!!)}

    val linkVkChordsPlay: String? get() = idVkChords?.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkChords!!)}
    val linkVkChordsEdit: String? get() = idVkChords?.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkChords!!)}

    val linkTelegramLyricsPlay: String? get() = idTelegramLyrics?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramLyrics!!)}
    val linkTelegramLyricsEdit: String? get() = idTelegramLyrics?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramLyrics!!)}

    val linkTelegramKaraokePlay: String? get() = idTelegramKaraoke?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramKaraoke!!)}
    val linkTelegramKaraokeEdit: String? get() = idTelegramKaraoke?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramKaraoke!!)}

    val linkTelegramChordsPlay: String? get() = idTelegramChords?.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramChords!!)}
    val linkTelegramChordsEdit: String? get() = idTelegramChords?.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramChords!!)}

    val linkPlLyricsPlay: String? get() = idPlLyrics?.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlLyrics!!)}
    val linkPlLyricsEdit: String? get() = idPlLyrics?.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlLyrics!!)}

    val linkPlKaraokePlay: String? get() = idPlKaraoke?.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlKaraoke!!)}
    val linkPlKaraokeEdit: String? get() = idPlKaraoke?.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlKaraoke!!)}

    val linkPlChordsPlay: String? get() = idPlChords?.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlChords!!)}
    val linkPlChordsEdit: String? get() = idPlChords?.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlChords!!)}

    val flagBoosty: String get() =
        if (idBoosty == null || idBoosty == "null" || idBoosty == "") {
            "-"
        } else if (idBoostyFiles == null || idBoostyFiles == "null" || idBoostyFiles == "") {
            "✅"
        } else {
            "✔"
        }

    val flagBoostyFiles: String get() = if (idBoostyFiles == null || idBoostyFiles == "null" || idBoostyFiles == "") "-" else "✓"
    val flagVk: String get() = if (idVk == null || idVk == "null" || idVk == "") "-" else "✓"
    val flagYoutubeLyrics: String get() = if (idYoutubeLyrics == null || idYoutubeLyrics == "null" || idYoutubeLyrics == "") "-" else "✓"
    val flagYoutubeKaraoke: String get() = if (idYoutubeKaraoke == null || idYoutubeKaraoke == "null" || idYoutubeKaraoke == "") "-" else "✓"
    val flagYoutubeChords: String get() = if (idYoutubeChords == null || idYoutubeChords == "null" || idYoutubeChords == "") "-" else "✓"

    val flagVkLyrics: String get() = if (idVkLyrics == null || idVkLyrics == "null" || idVkLyrics == "") "-" else "✓"
    val flagVkKaraoke: String get() = if (idVkKaraoke == null || idVkKaraoke == "null" || idVkKaraoke == "") "-" else "✓"
    val flagVkChords: String get() = if (idVkChords == null || idVkChords == "null" || idVkChords == "") "-" else "✓"


    val flagTelegramLyrics: String get() = if (idTelegramLyrics == null || idTelegramLyrics == "null" || idTelegramLyrics == "") "-" else "✓"
    val flagTelegramKaraoke: String get() = if (idTelegramKaraoke == null || idTelegramKaraoke == "null" || idTelegramKaraoke == "") "-" else "✓"
    val flagTelegramChords: String get() = if (idTelegramChords == null || idTelegramChords == "null" || idTelegramChords == "") "-" else "✓"

    val flagPlLyrics: String get() = if (idPlLyrics == null || idPlLyrics == "null" || idPlLyrics == "") "-" else "✓"
    val flagPlKaraoke: String get() = if (idPlKaraoke == null || idPlKaraoke == "null" || idPlKaraoke == "") "-" else "✓"
    val flagPlChords: String get() = if (idPlChords == null || idPlChords == "null" || idPlChords == "") "-" else "✓"

    val pathToResultedModel: String get() = "$rootFolder/$DEMUCS_MODEL_NAME"
    val pathToSymlinkFolder: String get() = "$rootFolder/symlink"
    val separatedStem: String get() = "vocals"
    val oldNoStemNameWav: String get() = "$pathToResultedModel/$fileName-no_$separatedStem.wav"
    val newNoStemNameWav: String get() = "$pathToResultedModel/$fileName-accompaniment.wav"
    val newNoStemNameFlac: String get() = "$pathToResultedModel/$fileName-accompaniment.flac"
    val newNoStemNameFlacSymlink: String get() = "$pathToSymlinkFolder/$fileName-accompaniment.flac"
    val vocalsNameWav: String get() = "$pathToResultedModel/$fileName-vocals.wav"
    val vocalsNameFlac: String get() = "$pathToResultedModel/$fileName-vocals.flac"
    val vocalsNameFlacSymlink: String get() = "$pathToSymlinkFolder/$fileName-vocals.flac"
    val drumsNameWav: String get() = "$pathToResultedModel/$fileName-drums.wav"
    val drumsNameFlac: String get() = "$pathToResultedModel/$fileName-drums.flac"
    val bassNameWav: String get() = "$pathToResultedModel/$fileName-bass.wav"
    val bassNameFlac: String get() = "$pathToResultedModel/$fileName-bass.flac"
    val guitarsNameWav: String get() = "$pathToResultedModel/$fileName-guitars.wav"
    val guitarsNameFlac: String get() = "$pathToResultedModel/$fileName-guitars.flac"
    val otherNameWav: String get() = "$pathToResultedModel/$fileName-other.wav"
    val otherNameFlac: String get() = "$pathToResultedModel/$fileName-other.flac"
    val fileAbsolutePath: String get() = "$rootFolder/$fileName.flac"
    val fileAbsolutePathSymlink: String get() = "$pathToSymlinkFolder/$fileName.flac"
    val fileSettingsAbsolutePath: String get() = "$rootFolder/$fileName.settings"

    val fileNameVocals: String get() = "${fileName}-vocals.flac"
    val fileNameAccompaniment: String get() = "${fileName}-accompaniment.flac"

    val kdenliveFileName: String get() = "$rootFolder/$fileName.kdenlive"
    val kdenliveSubsFileName: String get() = "$rootFolder/$fileName.kdenlive.srt"
    @get:JsonIgnore
    val durationInMilliseconds: Long get() = ((MediaInfo.getInfoBySectionAndParameter(
        "$fileAbsolutePath",
        "Audio",
        "Duration"
    ) ?: "0.0").toDouble() * 1000).toLong()
    @get:JsonIgnore
    val durationTimecode: String get() = convertMillisecondsToTimecode(durationInMilliseconds)
    @get:JsonIgnore
    val durationFrames: Long get() = convertMillisecondsToFrames(durationInMilliseconds)

    val linkBoostyTxt: String get() = if (idBoosty == "") "" else linkBoosty!!
    val linkBoostyFilesTxt: String get() = if (idBoostyFiles == "") "" else linkBoostyFiles!!
    val linkDzenKaraoke: String get() = if (idYoutubeKaraoke == "") "" else linkYoutubeKaraokePlay!!
    val linkDzenLyrics: String get() = if (idYoutubeLyrics == "") "" else linkYoutubeLyricsPlay!!

    val linkVkKaraoke: String get() = if (idVkKaraoke == "") "" else linkVkKaraokePlay!!
    val linkVkLyrics: String get() = if (idVkLyrics == "") "" else linkVkLyricsPlay!!

    val linkTgKaraoke: String get() = if (idTelegramKaraoke == "" || idTelegramKaraoke == "-") "" else linkTelegramKaraokePlay!!
    val linkTgLyrics: String get() = if (idTelegramLyrics == "" || idTelegramLyrics == "-") "" else linkTelegramLyricsPlay!!
    val linkPlKaraoke: String get() = if (idPlKaraoke == "") "" else linkPlKaraokePlay!!
    val linkPlLyrics: String get() = if (idPlLyrics == "") "" else linkPlLyricsPlay!!
    val linkPlChords: String get() = if (idPlChords == "") "" else linkPlChordsPlay!!
    val datePublish: String get() = if (date == "" || time == "") "Дата пока не определена" else "${date} ${time}"

    val sheetstageInfo: Map<String, Any> get() {
        // key - тональность
        // tempo - bpm
        // chords - аккорды в формате "аккорд время длительность_в_битах бит_по_счёту"
        // beattimes - времена каждой 1/16 такта
        val result: MutableMap<String, Any> = mutableMapOf()

        val file = File(pathToFileSheetsageLY)
        val fileBeattime = File(pathToFileSheetsageBeattimes)
        if (!file.exists() || !fileBeattime.exists()) return result
        val bodyLY = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return result
        }
        val bodyBeattimes = try {
            fileBeattime.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return result
        }

        println(pathToFileSheetsageLY)
        println(bodyLY)

        println(pathToFileSheetsageBeattimes)
        println(bodyBeattimes)

        val beattimesSource = bodyBeattimes.substring(1,bodyBeattimes.length-2).split(", ").map { it.toDouble() }
        val beattimes: MutableList<Double> = mutableListOf()
        for (i in 0 until beattimesSource.size-1) {
            val currentBeatTime = beattimesSource[i]
            val nextBeatTime = beattimesSource[i+1]
            val lenOneBeat = (nextBeatTime - currentBeatTime) / 4
            beattimes.add(currentBeatTime)
            beattimes.add(currentBeatTime + lenOneBeat)
            beattimes.add(currentBeatTime + lenOneBeat*2)
            beattimes.add(currentBeatTime + lenOneBeat*3)
        }
        result["beattimes"] = beattimes

        val regexChords = Regex("\\\\chordmode \\{([\\n?|\\s?|\\S?|.?]*?)}")
        val regexKey = Regex("\\\\key\\s([\\S|\\s]*?)\\n")
        val regexTempo = Regex("\\\\tempo\\s([\\S|\\s]*?)\\n")
        val regexChord = Regex("(\\D)(\\D{0,2})(\\d{1,2})\\*(\\d{1,3})(:|)(\\S*|)")

        val matchResultChords = regexChords.find(bodyLY)
        val matchResultKey = regexKey.find(bodyLY)
        val matchResultTempo = regexTempo.find(bodyLY)

        if (matchResultKey != null) {
            val (v1, v2) = matchResultKey.groupValues[1].split(" \\")
            val key = v1[0].toString().uppercase() +
                    (if (v1.length == 3 && v1.endsWith("is"))
                        "#"
                    else
                        if (v1.length == 3 && v1.endsWith("es"))
                            "♭"
                        else "") + " " + v2
            result["key"] = key
        }

        if (matchResultTempo != null) {
            val (_, tempo) = matchResultTempo.groupValues[1].split("=")
            result["tempo"] = tempo.trim()
        }

        if (matchResultChords != null) {
            val chords = matchResultChords.groupValues[1].trim().split(" ")
            val chordsList: MutableList<String> = mutableListOf()
            var indexBeat = diffBeats.toInt()
            println("DiffBeats = $indexBeat")
            var chordTime = beattimes[indexBeat]

            chords.forEach { chordText ->
                val matchResultChord = regexChord.find(chordText)
                if (matchResultChord != null) {
                    println(chordText)
                    var chord = matchResultChord.groupValues[1].uppercase() +
                            if (matchResultChord.groupValues[2] == "is") "#" else if (matchResultChord.groupValues[2] == "es") "♭" else ""

                    chord = chord + try {
                        matchResultChord.groupValues[6]
                    } catch (e: Exception) {
                        ""
                    }

                    val beats = (matchResultChord.groupValues[3].toLong()) * (matchResultChord.groupValues[4].toLong()) / 16
                    chord = chord + " " + chordTime.toString() + " " + beats + " " + indexBeat
                    for (i in 0 until beats) {
                        indexBeat++
                        if (indexBeat < beattimes.size) {
                            chordTime = beattimes[indexBeat]
                        }
                    }

                    println(chord)
                    chordsList.add(chord)
                }

            }
            result["chords"] = chordsList
        }

        return result
    }

    fun getVKPictureBase64(): String = getVKPictureBase64(this)
    @get:JsonIgnore
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

    @get:JsonIgnore
    val textDemucs2track: String get() = "python3 -m demucs -n $DEMUCS_MODEL_NAME -d cuda --filename \"{track}-{stem}.{ext}\" --two-stems=$separatedStem -o \"$rootFolder\" \"$fileAbsolutePath\"\n" +
            "mv \"$oldNoStemNameWav\" \"$newNoStemNameWav\"" + "\n" +
            "ffmpeg -i \"$newNoStemNameWav\" -compression_level 8 \"$newNoStemNameFlac\" -y" + "\n" +
            "rm \"$newNoStemNameWav\"" + "\n" +
            "ffmpeg -i \"$vocalsNameWav\" -compression_level 8 \"$vocalsNameFlac\" -y" + "\n" +
            "rm \"$vocalsNameWav\"" + "\n"

    @get:JsonIgnore
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
    @get:JsonIgnore
    val textDemucs5track: String get() = "$textDemucs2track$textDemucs4track"
    @get:JsonIgnore
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

    val processColorVk: String get() = if (idVk.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorBoosty: String get() =
        if (idBoosty.isNotBlank() && idBoostyFiles.isNotBlank()) {
            "#00FF00"
        } else if (idBoosty.isNotBlank()) {
            "#00B500"
        } else {
            "#A9A9A9"
        }
    val processColorBoostyFiles: String get() = if (idBoostyFiles.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorVkLyrics: String get() = if (idVkLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorVkKaraoke: String get() = if (idVkKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorVkChords: String get() = if (idVkChords.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorDzenLyrics: String get() = if (idYoutubeLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorDzenKaraoke: String get() = if (idYoutubeKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorDzenChords: String get() = if (idYoutubeChords.isNotBlank()) "#00FF00" else "#A9A9A9"

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

    val processColorTelegramChords: String get() =
        if (idTelegramChords == "-" || idTelegramChords == "-" ) {
            "#F08080"
        } else if (idTelegramChords.isNotBlank()) {
            "#00FF00"
        } else {
            "#A9A9A9"
        }

    val processColorPlLyrics: String get() = if (idPlLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorPlKaraoke: String get() = if (idPlKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorPlChords: String get() = if (idPlChords.isNotBlank()) "#00FF00" else "#A9A9A9"

    @get:JsonIgnore
    val haveBoostyLink: Boolean get() = idBoosty.isNotBlank()
    @get:JsonIgnore
    val haveBoostyFilesLink: Boolean get() = idBoostyFiles.isNotBlank()
    @get:JsonIgnore
    val haveVkGroupLink: Boolean get() = idVk.isNotBlank()
    @get:JsonIgnore
    val haveYoutubeLinks: Boolean get() = idYoutubeLyrics.isNotBlank() ||
            idYoutubeKaraoke.isNotBlank() ||
            idYoutubeChords.isNotBlank()
    @get:JsonIgnore
    val haveVkLinks: Boolean get() = idVkLyrics.isNotBlank() ||
            idVkKaraoke.isNotBlank() ||
            idVkChords.isNotBlank()
    @get:JsonIgnore
    val haveTelegramLinks: Boolean get() = idTelegramLyrics.isNotBlank() && idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-"
    @get:JsonIgnore
    val flags: String get() = if (haveBoostyLink && haveVkGroupLink && haveVkLinks && haveTelegramLinks && haveYoutubeLinks) "" else "(${if (haveBoostyLink) "b" else "-"}${if (haveVkGroupLink) "g" else "-"}${if (haveVkLinks) "v" else "-"}${if (haveTelegramLinks) "t" else "-"}${if (haveYoutubeLinks) "z" else "-"}) "

    @get:JsonIgnore
    val havePlLinks: Boolean get() = idPlLyrics.isNotBlank() ||
            idPlKaraoke.isNotBlank() || idPlChords.isNotBlank()

    @get:JsonIgnore
    val digest: String get() =
       (if (firstSongInAlbum) "Альбом: «${album}» (${year})\n\n" else "") +
       "$songName\n$linkBoosty\n" +
                "${if (idVkKaraoke.isNotBlank()) "Karaoke VK $linkVkKaraokePlay\n" else ""}${if (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") "Karaoke TG $linkTelegramKaraokePlay\n" else ""}${if (idYoutubeKaraoke.isNotBlank()) "Karaoke DZ $linkYoutubeKaraokePlay\n" else ""}" +
                "${if (idVkLyrics.isNotBlank()) "Lyrics VK $linkVkLyricsPlay\n" else ""}${if (idTelegramLyrics.isNotBlank()) "Lyrics TG $linkTelegramLyricsPlay\n" else ""}${if (idYoutubeLyrics.isNotBlank()) "Lyrics DZ $linkYoutubeLyricsPlay\n" else ""}\n"
    @get:JsonIgnore
    val digestIsFull: Boolean get() = idVkKaraoke.isNotBlank() && (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") && idYoutubeKaraoke.isNotBlank() && idVkLyrics.isNotBlank() && idTelegramLyrics.isNotBlank() && idYoutubeLyrics.isNotBlank()



    fun getDescriptionLinks(): String {

        var result = "Все версии этой песни: ${linkSM}\n\n"

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
        if (File(pathToFileLyrics).exists()) {
            val processBuilder = ProcessBuilder("smplayer", pathToFileLyrics)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        } else {
            println("Не найден ${pathToFileLyrics}")
        }
    }

    fun playKaraoke() {
        if (File(pathToFileKaraoke).exists()) {
            val processBuilder = ProcessBuilder("smplayer", pathToFileKaraoke)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        } else {
            println("Не найден ${pathToFileKaraoke}")
        }
    }

    fun playChords() {
        if (File(pathToFileChords).exists()) {
            val processBuilder = ProcessBuilder("smplayer", pathToFileChords)
            processBuilder.redirectErrorStream(true)
            processBuilder.start()
        } else {
            println("Не найден ${pathToFileChords}")
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
            resultText = getText()
            saveToDb()
            return
        }
        if (voice >= 0 && voice < sourceMarkersList.size) {
            val lst = sourceMarkersList.toMutableList()
            lst[voice] = markers
            sourceMarkers = Json.encodeToString(lst)
            resultText = getText()
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

    fun getTextFormatted(): String {
        val result = StringBuilder()
        for (voice in 0 until countVoices) {

            val SPAN_STYLE_GROUP0 = """<span style="color: #FFFFFF; font-size: smaller; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_GROUP1 = """<span style="color: #FFFF00; font-size: smaller; font-style: italic; font-weight: bolder;">"""
            val SPAN_STYLE_GROUP2 = """<span style="color: #00BFFF; font-size: smaller; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_GROUP3 = """<span style="color: #00FF00; font-size: smaller; font-style: italic; font-weight: bolder;">"""
            val SPAN_STYLE_COMMENT = """<span style="color: #D2691E; font-size: small; font-style: italic; font-weight: bolder;">"""


            val markers = getSourceMarkers(voice)
            var spanStyle = SPAN_STYLE_GROUP0
            var spanStylePrev = spanStyle
            var wasBr = true
            markers.forEach { marker ->

                when (marker.markertype) {
                    "setting" -> {
                        when (marker.label) {
                            "GROUP|0" -> spanStyle = SPAN_STYLE_GROUP0
                            "GROUP|1" -> spanStyle = SPAN_STYLE_GROUP1
                            "GROUP|2" -> spanStyle = SPAN_STYLE_GROUP2
                            "GROUP|3" -> spanStyle = SPAN_STYLE_GROUP3
                            "COMMENT| " -> result.append("<br>")
                            else -> {
                                if (marker.label.startsWith("COMMENT|")) {
                                    val txt = marker.label.split("|")[1]
                                    result.append(SPAN_STYLE_COMMENT)
                                    result.append(txt.replace("_", " ").uppercaseFirstLetter())
                                    result.append("</span>")
                                    result.append("<br>")
                                }
                            }
                        }
                    }
                    "endofline", "newline" -> {
                        result.append("<br>")
                        wasBr = true
//                    result.append("""<span style="font-size: 0">Источник: sm-karaoke.ru</span>""")
                    }
                    "syllables" -> {
                        if (marker.label.length > 0) {
                            result.append(spanStyle)
                            var txt = marker.label.replace("_", " ")
                            if (wasBr) {
                                txt = txt.uppercaseFirstLetter()
                                wasBr = false
                            }
                            result.append(txt)
                            result.append("</span>")
                        }
                    }
                    else -> {} // unmute, note, chord
                }

                if (spanStyle != spanStylePrev) result.append("<br>")
                spanStylePrev = spanStyle
            }

            if (countVoices > 1 && voice != countVoices-1) {
                result.append("""<br><hr style="border: 2px solid blue;"><br>""")
            }

        }

        return result.toString().deleteThisSymbols(NOTES_SYMBOLS)
    }

    fun getTextBody(): String {
        val result = StringBuilder()
        for (voice in 0 until countVoices) {

            val SPAN_STYLE_GROUP0 = """<span style="color: #FFFFFF; font-size: smaller; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_GROUP1 = """<span style="color: #FFFF00; font-size: smaller; font-style: italic; font-weight: bolder;">"""
            val SPAN_STYLE_GROUP2 = """<span style="color: #00BFFF; font-size: smaller; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_GROUP3 = """<span style="color: #00FF00; font-size: smaller; font-style: italic; font-weight: bolder;">"""
            val SPAN_STYLE_COMMENT = """<span style="color: #D2691E; font-size: small; font-style: italic; font-weight: bolder;">"""


            val markers = getSourceMarkers(voice)
            var spanStyle = SPAN_STYLE_GROUP0
            var spanStylePrev = spanStyle
            var wasBr = true
            markers.forEach { marker ->

                when (marker.markertype) {
                    "setting" -> {
                        when (marker.label) {
                            "GROUP|0" -> spanStyle = SPAN_STYLE_GROUP0
                            "GROUP|1" -> spanStyle = SPAN_STYLE_GROUP1
                            "GROUP|2" -> spanStyle = SPAN_STYLE_GROUP2
                            "GROUP|3" -> spanStyle = SPAN_STYLE_GROUP3
                            "COMMENT| " -> result.append("\n")
                            else -> {
                                if (marker.label.startsWith("COMMENT|")) {
                                    val txt = marker.label.split("|")[1]
                                    result.append(txt.replace("_", " ").uppercaseFirstLetter())
                                    result.append("\n")
                                }
                            }
                        }
                    }
                    "endofline", "newline" -> {
                        result.append("\n")
                        wasBr = true
                    }
                    "syllables" -> {
                        var txt = marker.label.replace("_", " ")
                        if (wasBr) {
                            txt = txt.uppercaseFirstLetter()
                            wasBr = false
                        }
                        result.append(txt)
                    }
                    else -> {} // unmute, note, chord
                }

                if (spanStyle != spanStylePrev) result.append("\n")
                spanStylePrev = spanStyle
            }

            if (countVoices > 1 && voice != countVoices-1) {
                result.append("\n-----------------------------\n")
            }

        }

        return result.toString().deleteThisSymbols(NOTES_SYMBOLS)
    }

    fun getTextBodyWithTimecodes(maxTimeCodes: Int? = null): String {
    val result = StringBuilder()
    for (voice in 0 until countVoices) {

        val SPAN_STYLE_GROUP0 = """<span style="color: #FFFFFF; font-size: smaller; font-style: normal; font-weight: bolder;">"""
        val SPAN_STYLE_GROUP1 = """<span style="color: #FFFF00; font-size: smaller; font-style: italic; font-weight: bolder;">"""
        val SPAN_STYLE_GROUP2 = """<span style="color: #00BFFF; font-size: smaller; font-style: normal; font-weight: bolder;">"""
        val SPAN_STYLE_GROUP3 = """<span style="color: #00FF00; font-size: smaller; font-style: italic; font-weight: bolder;">"""
        val SPAN_STYLE_COMMENT = """<span style="color: #D2691E; font-size: small; font-style: italic; font-weight: bolder;">"""


        val markers = getSourceMarkers(voice)
        var spanStyle = SPAN_STYLE_GROUP0
        var spanStylePrev = spanStyle
        var wasBr = true
        var timecodeCounter = 0
        markers.forEach { marker ->
            val timecode = convertMillisecondsToYoutubeTimecode((marker.time * 1000 + 8000).toLong())
            when (marker.markertype) {
                "setting" -> {
                    when (marker.label) {
                        "GROUP|0" -> spanStyle = SPAN_STYLE_GROUP0
                        "GROUP|1" -> spanStyle = SPAN_STYLE_GROUP1
                        "GROUP|2" -> spanStyle = SPAN_STYLE_GROUP2
                        "GROUP|3" -> spanStyle = SPAN_STYLE_GROUP3
                        "COMMENT| " -> result.append("\n")
                        else -> {
                            if (marker.label.startsWith("COMMENT|")) {
                                val txt = marker.label.split("|")[1]
                                result.append(txt.replace("_", " ").uppercaseFirstLetter())
                                result.append("\n")
                            }
                        }
                    }
                }
                "endofline", "newline" -> {
                    result.append("\n")
                    wasBr = true
                }
                "syllables" -> {
                    var txt = marker.label.replace("_", " ")
                    if (wasBr) {
                        timecodeCounter++
                        if (maxTimeCodes != null && timecodeCounter >= maxTimeCodes) {
                            txt = txt.uppercaseFirstLetter()
                        } else {
                            txt = timecode + " " + txt.uppercaseFirstLetter()
                        }
                        wasBr = false
                    }
                    result.append(txt)
                }
                else -> {} // unmute, note, chord
            }

            if (spanStyle != spanStylePrev) result.append("\n")
            spanStylePrev = spanStyle
        }

        if (countVoices > 1 && voice != countVoices-1) {
            result.append("\n-----------------------------\n")
        }

    }

    return result.toString().deleteThisSymbols(NOTES_SYMBOLS)
}

    fun getText(): String {
        val result = StringBuilder()
        for (voice in 0 until countVoices) {
            val markers = getSourceMarkers(voice)
            var wasBr = true
            markers.forEach { marker ->
                when (marker.markertype) {
                    "syllables" -> {
                        var txt = marker.label.replace("_", " ")
                        if (wasBr) {
                            txt = txt.uppercaseFirstLetter()
                            wasBr = false
                        }
                        result.append(txt)
                    }
                    "endofline", "newline" -> {
                        result.append("\n")
                        wasBr = true
                    }
                    else -> {}
                }
            }
            if (countVoices > 1 && voice != countVoices-1) {
                result.append("\n")
            }
        }
        return result.toString().deleteThisSymbols(NOTES_SYMBOLS)
    }

    fun getWords(): List<String> {
        return getText().getWords()
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
        val listMarkers = getSourceMarkers(voice).filter { it.markertype != "unmute" && it.markertype != "newline" && it.markertype != "chord" && it.markertype != "beat" && it.markertype != "note"}
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
            } else ""

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
            val newSett = createDbInstance(this, database)
            newSett?.let {
                newSett.saveToFile()
            }
        } else {

            val diff = getDiff(this, loadFromDbById(id,database))
            if (diff.isEmpty()) return
            val messageRecordChange = SseNotification.recordChange(
                RecordChangeMessage(
                    tableName = "tbl_settings",
                    recordId = id,
                    diffs = diff,
                    databaseName = database.name,
                    record = this.toDTO()
                )
            )

            val setStr = diff.filter{ it.recordDiffRealField }.map { "${it.recordDiffName} = ?" }.joinToString(", ")
            if (setStr != "") {

                val sql = "UPDATE tbl_settings SET $setStr WHERE id = ?"

                val connection = database.getConnection()
                val ps = connection.prepareStatement(sql)

                var index = 1
                diff.filter{ it.recordDiffRealField }.forEach {
                    if (it.recordDiffValueNew is Long) {
                        ps.setLong(index, it.recordDiffValueNew.toLong())
                    } else {
                        ps.setString(index, it.recordDiffValueNew.toString())
                    }
                    index++
                }
                ps.setLong(index, id)
                ps.executeUpdate()
                ps.close()

                println(messageRecordChange.toString())

                try {
                    SNS.send(messageRecordChange)
                } catch (e: Exception) {
                    println(e.message)
                }
                val saved = loadFromDbById(id, database)
                val diffNew = getDiff(saved,this)
                if (diffNew.isNotEmpty()) {
                    val messageRecordChangeNew = SseNotification.recordChange(
                        RecordChangeMessage(
                            tableName = "tbl_settings",
                            recordId = id,
                            diffs = diffNew,
                            databaseName = database.name,
                            record = saved?.toDTO()
                        )
                    )

                    println(messageRecordChangeNew.toString())
                    try {
                        SNS.send(messageRecordChangeNew)
                    } catch (e: Exception) {
                        println(e.message)
                    }
                }

            }

        }

    }

    fun doSymlink(prior: Int = -1) {
        KaraokeProcess.createProcess(this, KaraokeProcessTypes.SYMLINK, true, prior)
    }
    fun doMP3Karaoke(prior: Int = -1) {
        KaraokeProcess.createProcess(this, KaraokeProcessTypes.FF_MP3_KAR, true, prior)
    }
    fun doMP3Lyrics(prior: Int = -1) {
        KaraokeProcess.createProcess(this, KaraokeProcessTypes.FF_MP3_LYR, true, prior)
    }
    fun deleteFromDb(withFiles: Boolean = true) {
        if (withFiles) {
            if (File(fileAbsolutePath).exists()) File(fileAbsolutePath).delete()
            if (File(fileSettingsAbsolutePath).exists()) File(fileSettingsAbsolutePath).deleteOnExit()
            if (File(vocalsNameFlac).exists()) File(vocalsNameFlac).deleteOnExit()
            if (File(newNoStemNameFlac).exists()) File(newNoStemNameFlac).deleteOnExit()
        }

        val connection = database.getConnection()
        val sql = "DELETE FROM tbl_settings WHERE id = ?"
        val ps = connection.prepareStatement(sql)
        ps.setLong(1, id)
        ps.executeUpdate()
        ps.close()

        val messageRecordDelete = SseNotification.recordDelete(
            RecordDeleteMessage(
                recordId = id,
                tableName = "tbl_settings",
                databaseName = database.name
            )
        )
        SNS.send(messageRecordDelete)

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
        val fileName = song.getOutputFilename(SongOutputFile.VK)
        val date = date
        val time = time.replace(":",".")


        val trueDate = "${date.substring(6)}.${date.substring(3,5)}.${date.substring(0,2)}"
        val name = fileName.replace("{REPLACE_DATE}",trueDate).replace("{REPLACE_TIME}",time).replace(" [lyrics]","")
        val text = song.getVKGroupDescription()

        if (text != "") {
            File(name).writeText(text)
            val vkPictNameOld = (song.getOutputFilename(SongOutputFile.PICTUREVK)).replace(" [lyrics] VK"," [VK]")
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

    fun createKaraoke(createLyrics: Boolean = true, createKaraoke: Boolean = true, createChords: Boolean = false) {

        val permissions = PosixFilePermissions.fromString("rwxr-x---")

        val songLyrics = Song(this, SongVersion.LYRICS)
        val songKaraoke = Song(this, SongVersion.KARAOKE)
        val songChords = Song(this, SongVersion.CHORDS)
        val hasChords = songChords.hasChords

        val pathToMltLyrics = songLyrics.getOutputFilename(SongOutputFile.MLT)
        val pathToMltKaraoke = songKaraoke.getOutputFilename(SongOutputFile.MLT)
        val pathToMltChords = songChords.getOutputFilename(SongOutputFile.MLT)

        val txtLyric = "echo \"$pathToMltLyrics\"\nmelt -progress \"$pathToMltLyrics\"\n\n"
        val txtKaraoke = "echo \"$pathToMltKaraoke\"\nmelt -progress \"$pathToMltKaraoke\"\n\n"
        val txtChords = "echo \"$pathToMltChords\"\nmelt -progress \"$pathToMltChords\"\n\n"

        val songTxtAll = "$txtLyric$txtKaraoke${if (hasChords) txtChords else ""}"
        val songTxtWOLyrics = "$txtKaraoke${if (hasChords) txtChords else ""}"

        var file = File(songLyrics.getOutputFilename(SongOutputFile.RUN))
        Files.createDirectories(Path(file.parent))
        file.writeText(txtLyric)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File(songKaraoke.getOutputFilename(SongOutputFile.RUN))
        file.writeText(txtKaraoke)
        Files.setPosixFilePermissions(file.toPath(), permissions)


        if (createChords && hasChords) {
            file = File(songChords.getOutputFilename(SongOutputFile.RUN))
            file.writeText(txtChords)
            Files.setPosixFilePermissions(file.toPath(), permissions)
        }

        file = File(songLyrics.getOutputFilename(SongOutputFile.RUNALL).replace("[lyrics]","[ALL]"))
        file.writeText(songTxtAll)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        file = File(songLyrics.getOutputFilename(SongOutputFile.RUNALL).replace("[lyrics]","[ALLwoLYRICS]"))
        file.writeText(songTxtWOLyrics)
        Files.setPosixFilePermissions(file.toPath(), permissions)

        if (createLyrics) createKaraoke(songLyrics)
        if (createKaraoke) createKaraoke(songKaraoke)
        if (createChords) createKaraoke(songChords)

        if (idStatus < 3) {
            fields[SettingField.ID_STATUS] = "3"
        }
        fields[SettingField.RESULT_VERSION] = CURRENT_RESULT_VERSION.toString()
        saveToDb()

    }

    fun getSqlToInsert(): String {
        val settings = this
        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()

        if (settings.id > 0) fieldsValues.add(Pair("id", settings.id))
        fieldsValues.add(Pair("song_name", settings.songName))
        fieldsValues.add(Pair("song_author", settings.author))
        fieldsValues.add(Pair("song_album", settings.album))
        fieldsValues.add(Pair("publish_date", settings.date))
        fieldsValues.add(Pair("publish_time", settings.time))
        fieldsValues.add(Pair("song_year", settings.year))
        fieldsValues.add(Pair("song_track", settings.track))
        fieldsValues.add(Pair("song_tone", settings.key))
        fieldsValues.add(Pair("song_bpm", settings.bpm))
        fieldsValues.add(Pair("result_version", settings.resultVersion))
        fieldsValues.add(Pair("diff_beats", settings.diffBeats))
        fieldsValues.add(Pair("song_ms", settings.ms))
        fieldsValues.add(Pair("file_name", settings.fileName))
        fieldsValues.add(Pair("root_folder", settings.rootFolder))
        fieldsValues.add(Pair("id_boosty", settings.idBoosty))
        fieldsValues.add(Pair("id_boosty_files", settings.idBoostyFiles))
        fieldsValues.add(Pair("id_vk", settings.idVk))
        fieldsValues.add(Pair("id_youtube_lyrics", settings.idYoutubeLyrics))
        fieldsValues.add(Pair("id_youtube_karaoke", settings.idYoutubeKaraoke))
        fieldsValues.add(Pair("id_youtube_chords", settings.idYoutubeChords))
        fieldsValues.add(Pair("id_vk_lyrics", settings.idVkLyrics))
        fieldsValues.add(Pair("id_vk_karaoke", settings.idVkKaraoke))
        fieldsValues.add(Pair("id_vk_chords", settings.idVkChords))
        fieldsValues.add(Pair("id_telegram_lyrics", settings.idTelegramLyrics))
        fieldsValues.add(Pair("id_telegram_karaoke", settings.idTelegramKaraoke))
        fieldsValues.add(Pair("id_telegram_chords", settings.idTelegramChords))
        fieldsValues.add(Pair("id_status", settings.idStatus))
        fieldsValues.add(Pair("source_text", settings.sourceText))
        fieldsValues.add(Pair("result_text", settings.resultText))
        fieldsValues.add(Pair("source_markers", settings.sourceMarkers))
        fieldsValues.add(Pair("status_process_lyrics", settings.statusProcessLyrics))
        fieldsValues.add(Pair("status_process_karaoke", settings.statusProcessKaraoke))
        fieldsValues.add(Pair("status_process_chords", settings.statusProcessChords))
        fieldsValues.add(Pair("tags", settings.tags))

       return "INSERT INTO tbl_settings (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second.toString().rightFileName()}'"}.joinToString(", ")})"

    }

    val state: SettingState get() {
        val currentCalendar = Calendar.getInstance()
        val currentDateTime = currentCalendar.time

        val formatter = SimpleDateFormat("dd/MM/yyyy")
        val currentDate = formatter.parse(formatter.format(currentDateTime))
        val datePublish = if (dateTimePublish == null) null else formatter.parse(formatter.format(dateTimePublish))
        if (datePublish == null || idStatus < 6) return SettingState.IN_WORK

        if (datePublish == currentDate) {
            if (idTelegramKaraoke != "-" &&
                idTelegramKaraoke != "" &&
                idTelegramLyrics != "" &&
                idVkKaraoke != "" &&
                idVkLyrics != "" &&
                idYoutubeKaraoke != "" &&
                idYoutubeLyrics != "" &&
                idPlKaraoke != "" &&
                idPlLyrics != "" &&
                idVk != "" &&
                idBoosty != ""
            ) {
                return SettingState.ALL_DONE
            } else if (idTelegramKaraoke != "-" &&
                idTelegramKaraoke != "" &&
                idTelegramLyrics != "" &&
                idVkKaraoke != "" &&
                idVkLyrics != "" &&
                idYoutubeKaraoke != "" &&
                idYoutubeLyrics != "" &&
                idVk != "" &&
                idBoosty != ""
            ) {
                return SettingState.ALL_DONE_WO_PL
            } else {
                return SettingState.TODAY
            }
        }

        if (datePublish < currentDate) {
            if (idTelegramKaraoke != "-" &&
                idTelegramKaraoke != "" &&
                idTelegramLyrics != "" &&
                idVkKaraoke != "" &&
                idVkLyrics != "" &&
                idYoutubeKaraoke != "" &&
                idYoutubeLyrics != "" &&
                idPlKaraoke != "" &&
                idPlLyrics != "" &&
                idVk != "" &&
                idBoosty != ""
            ) {
                return SettingState.ALL_DONE
            } else if (idTelegramKaraoke != "-" &&
                idTelegramKaraoke != "" &&
                idTelegramLyrics != "" &&
                idVkKaraoke != "" &&
                idVkLyrics != "" &&
                idYoutubeKaraoke != "" &&
                idYoutubeLyrics != "" &&
                idVk != "" &&
                idBoosty != ""
            ) {
                return SettingState.ALL_DONE_WO_PL
            } else {
                return SettingState.OVERDUE
            }
        }

        if (idTelegramKaraoke == "-" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idYoutubeKaraoke != "" &&
            idYoutubeLyrics != "" &&
            idVk != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.ALL_UPLOADED

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idYoutubeKaraoke != "" &&
            idYoutubeLyrics != "" &&
            idPlKaraoke != "" &&
            idPlLyrics != "" &&
            idVk != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_TG

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idYoutubeKaraoke != "" &&
            idYoutubeLyrics != "" &&
            (idPlKaraoke == "" ||
            idPlLyrics == "") &&
            idVk != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_PL

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            (idVkKaraoke == "" ||
            idVkLyrics == "") &&
            idYoutubeKaraoke != "" &&
            idYoutubeLyrics != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_VK

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idPlKaraoke != "" &&
            idPlLyrics != "" &&
            (idYoutubeKaraoke == "" ||
                    idYoutubeLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_DZEN_WITH_VK_WITH_PL
        
        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            (idYoutubeKaraoke == "" ||
            idYoutubeLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_DZEN_WITH_VK



        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            (idVkKaraoke == "" ||
            idVkLyrics == "") &&
            (idYoutubeKaraoke == "" ||
            idYoutubeLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_DZEN

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
//            idVkKaraoke == "" &&
//            idVkLyrics == "" &&
//            idYoutubeKaraoke == "" &&
//            idYoutubeLyrics == "" &&
            idVk == "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_VKG

        return SettingState.IN_WORK
    }

    companion object {

        fun getDiff(settA: Settings?, settB: Settings?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (settA != null && settB != null) {
                if (settA.songName != settB.songName) result.add(RecordDiff("song_name", settA.songName, settB.songName))
                if (settA.author != settB.author) result.add(RecordDiff("song_author", settA.author, settB.author))
                if (settA.album != settB.album) result.add(RecordDiff("song_album", settA.album, settB.album))
                if (settA.date != settB.date) result.add(RecordDiff("publish_date", settA.date, settB.date))
                if (settA.time != settB.time) result.add(RecordDiff("publish_time", settA.time, settB.time))
                if (settA.year != settB.year) result.add(RecordDiff("song_year", settA.year, settB.year))
                if (settA.track != settB.track) result.add(RecordDiff("song_track", settA.track, settB.track))
                if (settA.key != settB.key) result.add(RecordDiff("song_tone", settA.key, settB.key))
                if (settA.bpm != settB.bpm) result.add(RecordDiff("song_bpm", settA.bpm, settB.bpm))
                if (settA.resultVersion != settB.resultVersion) result.add(RecordDiff("result_version", settA.resultVersion, settB.resultVersion))
                if (settA.diffBeats != settB.diffBeats) result.add(RecordDiff("diff_beats", settA.diffBeats, settB.diffBeats))
                if (settA.ms != settB.ms) result.add(RecordDiff("song_ms", settA.ms, settB.ms))
                if (settA.fileName != settB.fileName) result.add(RecordDiff("file_name", settA.fileName, settB.fileName))
                if (settA.rootFolder != settB.rootFolder) result.add(RecordDiff("root_folder", settA.rootFolder, settB.rootFolder))
                if (settA.idBoosty != settB.idBoosty) result.add(RecordDiff("id_boosty", settA.idBoosty, settB.idBoosty))
                if (settA.idBoostyFiles != settB.idBoostyFiles) result.add(RecordDiff("id_boosty_files", settA.idBoostyFiles, settB.idBoostyFiles))
                if (settA.idVk != settB.idVk) result.add(RecordDiff("id_vk", settA.idVk, settB.idVk))
                if (settA.idYoutubeLyrics != settB.idYoutubeLyrics) result.add(RecordDiff("id_youtube_lyrics", settA.idYoutubeLyrics, settB.idYoutubeLyrics))
                if (settA.idYoutubeKaraoke != settB.idYoutubeKaraoke) result.add(RecordDiff("id_youtube_karaoke", settA.idYoutubeKaraoke, settB.idYoutubeKaraoke))
                if (settA.idYoutubeChords != settB.idYoutubeChords) result.add(RecordDiff("id_youtube_chords", settA.idYoutubeChords, settB.idYoutubeChords))
                if (settA.idVkLyrics != settB.idVkLyrics) result.add(RecordDiff("id_vk_lyrics", settA.idVkLyrics, settB.idVkLyrics))
                if (settA.idVkKaraoke != settB.idVkKaraoke) result.add(RecordDiff("id_vk_karaoke", settA.idVkKaraoke, settB.idVkKaraoke))
                if (settA.idVkChords != settB.idVkChords) result.add(RecordDiff("id_vk_chords", settA.idVkChords, settB.idVkChords))
                if (settA.idTelegramLyrics != settB.idTelegramLyrics) result.add(RecordDiff("id_telegram_lyrics", settA.idTelegramLyrics, settB.idTelegramLyrics))
                if (settA.idTelegramKaraoke != settB.idTelegramKaraoke) result.add(RecordDiff("id_telegram_karaoke", settA.idTelegramKaraoke, settB.idTelegramKaraoke))
                if (settA.idTelegramChords != settB.idTelegramChords) result.add(RecordDiff("id_telegram_chords", settA.idTelegramChords, settB.idTelegramChords))
                if (settA.idPlLyrics != settB.idPlLyrics) result.add(RecordDiff("id_pl_lyrics", settA.idPlLyrics, settB.idPlLyrics))
                if (settA.idPlKaraoke != settB.idPlKaraoke) result.add(RecordDiff("id_pl_karaoke", settA.idPlKaraoke, settB.idPlKaraoke))
                if (settA.idPlChords != settB.idPlChords) result.add(RecordDiff("id_pl_chords", settA.idPlChords, settB.idPlChords))
                if (settA.idStatus != settB.idStatus) result.add(RecordDiff("id_status", settA.idStatus, settB.idStatus))
                if (settA.sourceText != settB.sourceText) result.add(RecordDiff("source_text", settA.sourceText, settB.sourceText))
                if (settA.resultText != settB.resultText) result.add(RecordDiff("result_text", settA.resultText, settB.resultText))
                if (settA.sourceMarkers != settB.sourceMarkers) result.add(RecordDiff("source_markers", settA.sourceMarkers, settB.sourceMarkers))
                if (settA.statusProcessLyrics != settB.statusProcessLyrics) result.add(RecordDiff("status_process_lyrics", settA.statusProcessLyrics, settB.statusProcessLyrics))
                if (settA.statusProcessLyricsBt != settB.statusProcessLyricsBt) result.add(RecordDiff("status_process_lyrics_bt", settA.statusProcessLyricsBt, settB.statusProcessLyricsBt))
                if (settA.statusProcessKaraoke != settB.statusProcessKaraoke) result.add(RecordDiff("status_process_karaoke", settA.statusProcessKaraoke, settB.statusProcessKaraoke))
                if (settA.statusProcessKaraokeBt != settB.statusProcessKaraokeBt) result.add(RecordDiff("status_process_karaoke_bt", settA.statusProcessKaraokeBt, settB.statusProcessKaraokeBt))
                if (settA.statusProcessChords != settB.statusProcessChords) result.add(RecordDiff("status_process_chords", settA.statusProcessChords, settB.statusProcessChords))
                if (settA.statusProcessChordsBt != settB.statusProcessChordsBt) result.add(RecordDiff("status_process_chords_bt", settA.statusProcessChordsBt, settB.statusProcessChordsBt))
                if (settA.tags != settB.tags) result.add(RecordDiff("tags", settA.tags, settB.tags))
                if (settA.status != settB.status) result.add(RecordDiff("status", settA.status, settB.status, false))

                if (settA.color != settB.color) result.add(RecordDiff("color", settA.color, settB.color, false))
                if (settA.processColorMeltLyrics != settB.processColorMeltLyrics) result.add(RecordDiff("processColorMeltLyrics", settA.processColorMeltLyrics, settB.processColorMeltLyrics, false))
                if (settA.processColorMeltKaraoke != settB.processColorMeltKaraoke) result.add(RecordDiff("processColorMeltKaraoke", settA.processColorMeltKaraoke, settB.processColorMeltKaraoke, false))
                if (settA.processColorMeltChords != settB.processColorMeltChords) result.add(RecordDiff("processColorMeltChords", settA.processColorMeltChords, settB.processColorMeltChords, false))
                if (settA.processColorDzenLyrics != settB.processColorDzenLyrics) result.add(RecordDiff("processColorYoutubeLyrics", settA.processColorDzenLyrics, settB.processColorDzenLyrics, false))
                if (settA.processColorDzenKaraoke != settB.processColorDzenKaraoke) result.add(RecordDiff("processColorYoutubeKaraoke", settA.processColorDzenKaraoke, settB.processColorDzenKaraoke, false))
                if (settA.processColorDzenChords != settB.processColorDzenChords) result.add(RecordDiff("processColorYoutubeChords", settA.processColorDzenChords, settB.processColorDzenChords, false))
                if (settA.processColorVkLyrics != settB.processColorVkLyrics) result.add(RecordDiff("processColorVkLyrics", settA.processColorVkLyrics, settB.processColorVkLyrics, false))
                if (settA.processColorVkKaraoke != settB.processColorVkKaraoke) result.add(RecordDiff("processColorVkKaraoke", settA.processColorVkKaraoke, settB.processColorVkKaraoke, false))
                if (settA.processColorVkChords != settB.processColorVkChords) result.add(RecordDiff("processColorVkChords", settA.processColorVkChords, settB.processColorVkChords, false))
                if (settA.processColorTelegramLyrics != settB.processColorTelegramLyrics) result.add(RecordDiff("processColorTelegramLyrics", settA.processColorTelegramLyrics, settB.processColorTelegramLyrics, false))
                if (settA.processColorTelegramKaraoke != settB.processColorTelegramKaraoke) result.add(RecordDiff("processColorTelegramKaraoke", settA.processColorTelegramKaraoke, settB.processColorTelegramKaraoke, false))
                if (settA.processColorTelegramChords != settB.processColorTelegramChords) result.add(RecordDiff("processColorTelegramChords", settA.processColorTelegramChords, settB.processColorTelegramChords, false))
                if (settA.processColorVk != settB.processColorVk) result.add(RecordDiff("processColorVk", settA.processColorVk, settB.processColorVk, false))
                if (settA.processColorBoosty != settB.processColorBoosty) result.add(RecordDiff("processColorBoosty", settA.processColorBoosty, settB.processColorBoosty, false))
                if (settA.processColorPlLyrics != settB.processColorPlLyrics) result.add(RecordDiff("processColorPlLyrics", settA.processColorPlLyrics, settB.processColorPlLyrics, false))
                if (settA.processColorPlKaraoke != settB.processColorPlKaraoke) result.add(RecordDiff("processColorPlKaraoke", settA.processColorPlKaraoke, settB.processColorPlKaraoke, false))
                if (settA.processColorPlChords != settB.processColorPlChords) result.add(RecordDiff("processColorPlChords", settA.processColorPlChords, settB.processColorPlChords, false))
            }
            return result
        }

        fun getLastUpdated(lastTime: Long? = null, database: KaraokeConnection): List<Int> {
            if (lastTime == null) return emptyList()

            val connection = database.getConnection()
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
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun createDbInstance(settings: Settings, database: KaraokeConnection) : Settings? {

            val sql = settings.getSqlToInsert()
//            println(sql)

            val connection = database.getConnection()
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next() && settings.id <= 0) {
                settings.fields[SettingField.ID] = rs.getInt(1).toString()
                settings
            } else null

            ps.close()
            result?.let {
                val messageRecordAdd = SseNotification.recordAdd(
                    RecordAddMessage(
                        tableName = "tbl_settings",
                        recordId = result.id,
                        databaseName = database.name,
                        record = result.toDTO()
                    )
                )
                SNS.send(messageRecordAdd)
            }


            return result

        }

        fun loadListAuthors(database: KaraokeConnection): List<String> {
            val connection = database.getConnection()
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
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun loadListAlbums(database: KaraokeConnection): List<String> {
            val connection = database.getConnection()
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
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun loadListIds(database: KaraokeConnection): List<Long> {
            val connection = database.getConnection()
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String

            try {
                statement = connection.createStatement()
                sql = "select id from tbl_settings"

                rs = statement.executeQuery(sql)
                val result: MutableList<Long> = mutableListOf()
                while (rs.next()) {
                    result.add(rs.getLong("id"))
                }
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

        fun loadListFromDb(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Settings> {

            val connection = database.getConnection()
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT * FROM tbl_settings"
                if (args.containsKey("id")) where += "tbl_settings.id=${args["id"]}"
                if (args.containsKey("ids")) where += "tbl_settings.id in (${args["ids"]})"
                if (args.containsKey("file_name")) where += "LOWER(file_name)='${args["file_name"]?.rightFileName()?.lowercase()}'"
                if (args.containsKey("root_folder")) where += "LOWER(root_folder)='${args["root_folder"]?.rightFileName()?.lowercase()}'"
                if (args.containsKey("song_name")) where += "LOWER(song_name) LIKE '%${args["song_name"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("song_author")) where += "LOWER(song_author) LIKE '%${args["song_author"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("author")) where += "LOWER(song_author) = '${args["author"]?.rightFileName()?.lowercase()}'"
                if (args.containsKey("song_album")) where += "LOWER(song_album) LIKE '%${args["song_album"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("publish_date")) {
                    var pd = args["publish_date"]!!
                    if (pd[0] == '>') {
                        pd = pd.substring(1)
                        where += "to_date(publish_date, 'DD.MM.YY') >= to_date('$pd', 'DD.MM.YY')"
                    } else if (pd.last() == '<') {
                        pd = pd.dropLast(1)
                        where += "to_date(publish_date, 'DD.MM.YY') <= to_date('$pd', 'DD.MM.YY')"
                    } else {
                        if (pd == "-") {
                            where += "publish_date = ''"
                        } else if (pd == "+") {
                            where += "publish_date <> ''"
                        } else {
                            where += "publish_date LIKE '%$pd%'"
                        }
                    }

                }
                if (args.containsKey("publish_time")) {
                    if (args["publish_time"] == "-") {
                        where += "publish_time = ''"
                    } else if (args["publish_time"] == "+") {
                        where += "publish_date <> ''"
                    } else {
                        where += "publish_time LIKE '%${args["publish_time"]}%'"
                    }
                }
                if (args.containsKey("status")) where += "status LIKE '%${args["status"]}%'"
                if (args.containsKey("song_bpm")) where += "song_bpm=${args["song_bpm"]}"
                if (args.containsKey("song_tone")) where += "song_tone=${args["song_tone"]}"
                if (args.containsKey("song_year")) where += "song_year=${args["song_year"]}"
                if (args.containsKey("song_track")) where += "song_track=${args["song_track"]}"
                if (args.containsKey("id_status")) where += "id_status=${args["id_status"]}"
                if (args.containsKey("flag_boosty")) where += "CASE WHEN id_boosty IS NOT NULL AND id_boosty <> 'null' AND id_boosty <> '' THEN '+' ELSE '-' END='${args["flag_boosty"]}'"
                if (args.containsKey("flag_vk")) where += "CASE WHEN id_vk IS NOT NULL AND id_vk <> 'null' AND id_vk <> '' THEN '+' ELSE '-' END='${args["flag_vk"]}'"
                if (args.containsKey("flag_youtube_lyrics")) where += "CASE WHEN id_youtube_lyrics IS NOT NULL AND id_youtube_lyrics <> 'null' AND id_youtube_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_youtube_lyrics"]}'"
                if (args.containsKey("flag_youtube_karaoke")) where += "CASE WHEN id_youtube_karaoke IS NOT NULL AND id_youtube_karaoke <> 'null' AND id_youtube_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_youtube_karaoke"]}'"
                if (args.containsKey("flag_youtube_chords")) where += "CASE WHEN id_youtube_chords IS NOT NULL AND id_youtube_chords <> 'null' AND id_youtube_chords <> '' THEN '+' ELSE '-' END='${args["flag_youtube_chords"]}'"
                if (args.containsKey("flag_vk_lyrics")) where += "CASE WHEN id_vk_lyrics IS NOT NULL AND id_vk_lyrics <> 'null' AND id_vk_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_vk_lyrics"]}'"
                if (args.containsKey("flag_vk_karaoke")) where += "CASE WHEN id_vk_karaoke IS NOT NULL AND id_vk_karaoke <> 'null' AND id_vk_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_vk_karaoke"]}'"
                if (args.containsKey("flag_vk_chords")) where += "CASE WHEN id_vk_chords IS NOT NULL AND id_vk_chords <> 'null' AND id_vk_chords <> '' THEN '+' ELSE '-' END='${args["flag_vk_chords"]}'"
                if (args.containsKey("flag_telegram_lyrics")) where += "CASE WHEN id_telegram_lyrics IS NOT NULL AND id_telegram_lyrics <> 'null' AND id_telegram_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_telegram_lyrics"]}'"
                if (args.containsKey("flag_telegram_karaoke")) where += "CASE WHEN id_telegram_karaoke IS NOT NULL AND id_telegram_karaoke <> 'null' AND id_telegram_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_telegram_karaoke"]}'"
                if (args.containsKey("flag_telegram_chords")) where += "CASE WHEN id_telegram_chords IS NOT NULL AND id_telegram_chords <> 'null' AND id_telegram_chords <> '' THEN '+' ELSE '-' END='${args["flag_telegram_chords"]}'"
                if (args.containsKey("flag_pl_lyrics")) where += "CASE WHEN id_pl_lyrics IS NOT NULL AND id_pl_lyrics <> 'null' AND id_pl_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_pl_lyrics"]}'"
                if (args.containsKey("flag_pl_karaoke")) where += "CASE WHEN id_pl_karaoke IS NOT NULL AND id_pl_karaoke <> 'null' AND id_pl_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_pl_karaoke"]}'"
                if (args.containsKey("filter_result_version")) where += "result_version=${args["filter_result_version"]}"
                if (args.containsKey("tags")) {
                    var tg = args["tags"]!!
                    if (tg[0] == '~') {
                        tg = tg.substring(1)
                        where += "tags='${tg.rightFileName()}'"
                    } else if (tg.length > 2 && tg.startsWith("!~")) {
                        tg = tg.substring(2)
                        where += "tags NOT LIKE '%${tg.rightFileName()}%'"
                    } else {
                        where += "LOWER(tags) LIKE '%${tg.rightFileName().lowercase()}%'"
                    }

                }
                if (args.containsKey("text")) where += "to_tsvector('russian', result_text) @@ plainto_tsquery('russian', '${args["text"]?.rightFileName()?.lowercase()}')"

                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"

                sql += " ORDER BY tbl_settings.id"

//                println(where)
//                println(sql)

                rs = statement.executeQuery(sql)
                val result: MutableList<Settings> = mutableListOf()
                var prevAlbum = ""
                while (rs.next()) {

                    val settings = Settings(database)
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
                    rs.getString("id_boosty_files")?.let { value -> settings.fields[SettingField.ID_BOOSTY_FILES] = value }
                    rs.getString("id_vk")?.let { value -> settings.fields[SettingField.ID_VK] = value }
                    rs.getString("id_youtube_lyrics")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_LYRICS] = value }
                    rs.getString("id_youtube_karaoke")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_KARAOKE] = value }
                    rs.getString("id_youtube_chords")?.let { value -> settings.fields[SettingField.ID_YOUTUBE_CHORDS] = value }
                    rs.getString("id_vk_lyrics")?.let { value -> settings.fields[SettingField.ID_VK_LYRICS] = value }
                    rs.getString("id_vk_karaoke")?.let { value -> settings.fields[SettingField.ID_VK_KARAOKE] = value }
                    rs.getString("id_vk_chords")?.let { value -> settings.fields[SettingField.ID_VK_CHORDS] = value }
                    rs.getString("id_telegram_lyrics")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_LYRICS] = value }
                    rs.getString("id_telegram_karaoke")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_KARAOKE] = value }
                    rs.getString("id_pl_lyrics")?.let { value -> settings.fields[SettingField.ID_PL_LYRICS] = value }
                    rs.getString("id_pl_karaoke")?.let { value -> settings.fields[SettingField.ID_PL_KARAOKE] = value }
                    rs.getString("id_pl_chords")?.let { value -> settings.fields[SettingField.ID_PL_CHORDS] = value }
                    rs.getString("id_telegram_chords")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_CHORDS] = value }
                    rs.getInt("result_version")?.let { value -> settings.fields[SettingField.RESULT_VERSION] = value.toString() }
                    rs.getInt("diff_beats")?.let { value -> settings.fields[SettingField.DIFFBEATS] = value.toString() }
                    rs.getString("source_text")?.let { value -> settings.sourceText = value }
                    rs.getString("result_text")?.let { value -> settings.resultText = value }
                    rs.getString("source_markers")?.let { value -> settings.sourceMarkers = value }
                    settings.statusProcessLyrics = rs.getString("status_process_lyrics") ?: ""
                    settings.statusProcessKaraoke = rs.getString("status_process_karaoke") ?: ""
                    settings.statusProcessChords = rs.getString("status_process_chords") ?: ""
                    settings.tags = rs.getString("tags") ?: ""

                    val currentCalendar = Calendar.getInstance()
                    val currentDateTime = currentCalendar.time

                    val formatter = SimpleDateFormat("dd/MM/yyyy")
                    val currentDate = formatter.parse(formatter.format(currentDateTime))
                    val datePublish = if (settings.dateTimePublish == null) null else  formatter.parse(formatter.format(settings.dateTimePublish))

                    val color1 = when(settings.idStatus) {
                        0L -> "#FFFFFF"
                        1L -> "#DDA0DD"
                        2L -> "#EE82EE"
                        3L -> "#98FB98"
                        4L -> "#00FF7F"
                        5L -> "#D2691E"
                        6L -> "#00FF00"
                        else -> "#FFFFFF"
                    }

                    settings.fields[SettingField.COLOR] = if (settings.state.color == "") color1 else settings.state.color

//                    if (args.containsKey("text")) {
//                        if (settings.getWords().containsAll((args["text"]?:"").getWords())) result.add(settings)
//                    } else {
//                        result.add(settings)
//                    }

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
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }



        fun loadFromDbById(id: Long, database: KaraokeConnection): Settings? {

            return loadListFromDb(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }


        fun deleteFromDb(id: Long, database: KaraokeConnection) {

            val connection = database.getConnection()
            val sql = "DELETE FROM tbl_settings WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            ps.setLong(1, id)
            ps.executeUpdate()
            ps.close()

        }

        fun loadFromFile(pathToSettingsFile: String, readonly: Boolean = false, database: KaraokeConnection): Settings {
            val settings = Settings(database)
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

        fun createFromPath(startFolder: String, database: KaraokeConnection): MutableList<Settings> {
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
                                ), database
                            ).isEmpty()
                        ) {
                            val settings = Settings(database)
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

        fun getSetOfTags(database: KaraokeConnection): Set<String> {

            val result: MutableSet<String> = mutableSetOf()

            val connection = database.getConnection()
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
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptySet()
        }

        fun setPublishDateTimeToAuthor(startSettings: Settings) {

            val listOfSettings = loadListFromDb(mapOf(Pair("song_author", startSettings.author)), startSettings.database).filter { it.id > startSettings.id }

            if (startSettings.date == "") {
                listOfSettings.forEach { settings ->
                    settings.fields[SettingField.DATE] = ""
                    settings.fields[SettingField.TIME] = ""
                    settings.saveToDb()
                }
            } else {
                var publishDate = SimpleDateFormat("dd.MM.yy").parse(startSettings.date)
                val publishTime = startSettings.time
                listOfSettings.forEach { settings ->
                    val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
                    calendar.time = publishDate
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                    publishDate = calendar.time

                    settings.fields[SettingField.DATE] = SimpleDateFormat("dd.MM.yy").format(publishDate)
                    settings.fields[SettingField.TIME] = publishTime

                    settings.saveToDb()

                }
            }


        }

    }

    override fun compareTo(other: Settings): Int {
//        val a = dateTimePublish?.time ?: id
//        val b = other.dateTimePublish?.time ?: other.id
//        return a.compareTo(b)
        return sortString.compareTo(other.sortString)
    }

    fun toDTO(): SettingsDTO {
        return SettingsDTO(
            id = id,
            rootFolder = rootFolder,
            fileName = fileName,
            idStatus = idStatus,
            status = status,
            tags = tags,
            color = color,
            songName = songName,
            songNameCensored = songNameCensored,
            author = author,
            album = album,
            date = date,
            time = time,
            dateTimePublish = dateTimePublish,
            onAir = onAir,
            year = year,
            track = track,
            key = key,
            bpm = bpm,
            resultVersion = resultVersion,
            ms = ms,
            countVoices = countVoices,
            firstSongInAlbum = firstSongInAlbum,
            flagBoosty = flagBoosty,
            flagVk = flagVk,
            flagYoutubeLyrics = flagYoutubeLyrics,
            flagYoutubeKaraoke = flagYoutubeKaraoke,
            flagYoutubeChords = flagYoutubeChords,
            flagVkLyrics = flagVkLyrics,
            flagVkKaraoke = flagVkKaraoke,
            flagVkChords = flagVkChords,
            flagTelegramLyrics = flagTelegramLyrics,
            flagTelegramKaraoke = flagTelegramKaraoke,
            flagPlLyrics = flagPlLyrics,
            flagPlKaraoke = flagPlKaraoke,
            flagPlChords = flagPlChords,
            flagTelegramChords = flagTelegramChords,
            processColorBoosty = processColorBoosty,
            processColorVk = processColorVk,
            processColorMeltLyrics = processColorMeltLyrics,
            processColorMeltKaraoke = processColorMeltKaraoke,
            processColorMeltChords = processColorMeltChords,
            processColorDzenLyrics = processColorDzenLyrics,
            processColorDzenKaraoke = processColorDzenKaraoke,
            processColorDzenChords = processColorDzenChords,
            processColorVkLyrics = processColorVkLyrics,
            processColorVkKaraoke = processColorVkKaraoke,
            processColorVkChords = processColorVkChords,
            processColorTelegramLyrics = processColorTelegramLyrics,
            processColorTelegramKaraoke = processColorTelegramKaraoke,
            processColorTelegramChords = processColorTelegramChords,
            processColorPlLyrics = processColorPlLyrics,
            processColorPlKaraoke = processColorPlKaraoke,
            processColorPlChords = processColorPlChords,
            idBoosty = idBoosty,
            idBoostyFiles = idBoostyFiles,
            idVk = idVk,
            idYoutubeLyrics = idYoutubeLyrics,
            idYoutubeKaraoke = idYoutubeKaraoke,
            idYoutubeChords = idYoutubeChords,
            idVkLyrics = idVkLyrics,
            idVkLyricsOID = idVkLyricsOID,
            idVkLyricsID = idVkLyricsID,
            idVkKaraoke = idVkKaraoke,
            idVkKaraokeOID = idVkKaraokeOID,
            idVkKaraokeID = idVkKaraokeID,
            idVkChords = idVkChords,
            idVkChordsOID = idVkChordsOID,
            idVkChordsID = idVkChordsID,
            idTelegramLyrics = idTelegramLyrics,
            idTelegramKaraoke = idTelegramKaraoke,
            idTelegramChords = idTelegramChords,
            idPlLyrics = idPlLyrics,
            idPlKaraoke = idPlKaraoke,
            idPlChords = idPlChords
        )
    }

}


data class SettingsDTO(
    val id: Long,
    val rootFolder: String,
    val fileName: String,
    val idStatus: Long,
    val status: String,
    val tags: String,
    val color: String,
    val songName: String,
    val songNameCensored: String,
    val author: String,
    val album: String,
    val date: String,
    val time: String,
    val dateTimePublish: Date?,
    val onAir: Boolean,
    val year: Long,
    val track: Long,
    val key: String,
    val bpm: Long,
    val ms: Long,
    val countVoices: Int,
    val firstSongInAlbum: Boolean,
    val flagBoosty: String,
    val flagVk: String,
    val flagYoutubeLyrics: String,
    val flagYoutubeKaraoke: String,
    val flagYoutubeChords: String,
    val flagVkLyrics: String,
    val flagVkKaraoke: String,
    val flagVkChords: String,
    val flagTelegramLyrics: String,
    val flagTelegramKaraoke: String,
    val flagTelegramChords: String,
    val flagPlLyrics: String,
    val flagPlKaraoke: String,
    val flagPlChords: String,
    val processColorBoosty: String,
    val processColorVk: String,
    val processColorMeltLyrics: String,
    val processColorMeltKaraoke: String,
    val processColorMeltChords: String,
    val processColorDzenLyrics: String,
    val processColorDzenKaraoke: String,
    val processColorDzenChords: String,
    val processColorVkLyrics: String,
    val processColorVkKaraoke: String,
    val processColorVkChords: String,
    val processColorTelegramLyrics: String,
    val processColorTelegramKaraoke: String,
    val processColorTelegramChords: String,
    val processColorPlLyrics: String,
    val processColorPlKaraoke: String,
    val processColorPlChords: String,
    val idBoosty: String,
    val idBoostyFiles: String,
    val idVk: String,
    val idYoutubeLyrics: String,
    val idYoutubeKaraoke: String,
    val idYoutubeChords: String,
    val idVkLyrics: String,
    val idVkLyricsOID: String,
    val idVkLyricsID: String,
    val idVkKaraoke: String,
    val idVkKaraokeOID: String,
    val idVkKaraokeID: String,
    val idVkChords: String,
    val idVkChordsOID: String,
    val idVkChordsID: String,
    val idTelegramLyrics: String,
    val idTelegramKaraoke: String,
    val idTelegramChords: String,
    val idPlLyrics: String,
    val idPlKaraoke: String,
    val idPlChords: String,
    val resultVersion: Long
): Serializable, Comparable<SettingsDTO> {

    private val sortString: String get() {
        return if (dateTimePublish == null) {
            listOf(
                author, year.toString(), album, "%3d".format(track)
            ).joinToString(" - ")
        } else
        {
            "%15d".format(dateTimePublish.time)
        }
    }

    override fun compareTo(other: SettingsDTO): Int {
        return sortString.compareTo(other.sortString)
    }

    fun toDtoDigest(): SettingsDTOdigest {
        return SettingsDTOdigest(
            id = id,
            status = status,
            tags = tags,
            color = color,
            songName = songName,
            songNameCensored = songNameCensored,
            author = author,
            album = album,
            date = date,
            time = time,
            dateTimePublish = dateTimePublish,
            year = year,
            track = track,
            countVoices = countVoices,
            firstSongInAlbum = firstSongInAlbum,
            flagBoosty = flagBoosty,
            flagVk = flagVk,
            flagYoutubeLyrics = flagYoutubeLyrics,
            flagYoutubeKaraoke = flagYoutubeKaraoke,
            flagYoutubeChords = flagYoutubeChords,
            flagVkLyrics = flagVkLyrics,
            flagVkKaraoke = flagVkKaraoke,
            flagVkChords = flagVkChords,
            flagTelegramLyrics = flagTelegramLyrics,
            flagTelegramKaraoke = flagTelegramKaraoke,
            flagTelegramChords = flagTelegramChords,
            flagPlLyrics = flagPlLyrics,
            flagPlKaraoke = flagPlKaraoke,
            flagPlChords = flagPlChords,
            processColorBoosty = processColorBoosty,
            processColorVk = processColorVk,
            processColorMeltLyrics = processColorMeltLyrics,
            processColorMeltKaraoke = processColorMeltKaraoke,
            processColorMeltChords = processColorMeltChords,
            processColorDzenLyrics = processColorDzenLyrics,
            processColorDzenKaraoke = processColorDzenKaraoke,
            processColorDzenChords = processColorDzenChords,
            processColorVkLyrics = processColorVkLyrics,
            processColorVkKaraoke = processColorVkKaraoke,
            processColorVkChords = processColorVkChords,
            processColorTelegramLyrics = processColorTelegramLyrics,
            processColorTelegramKaraoke = processColorTelegramKaraoke,
            processColorTelegramChords = processColorTelegramChords,
            processColorPlLyrics = processColorPlLyrics,
            processColorPlKaraoke = processColorPlKaraoke,
            processColorPlChords = processColorPlChords,
            resultVersion = resultVersion,
        )
    }
}

data class SettingsDTOdigest(
    val id: Long,
    val status: String,
    val tags: String,
    val color: String,
    val songName: String,
    val songNameCensored: String,
    val author: String,
    val album: String,
    val date: String,
    val time: String,
    val dateTimePublish: Date?,
    val year: Long,
    val track: Long,
    val countVoices: Int,
    val firstSongInAlbum: Boolean,
    val flagBoosty: String,
    val flagVk: String,
    val flagYoutubeLyrics: String,
    val flagYoutubeKaraoke: String,
    val flagYoutubeChords: String,
    val flagVkLyrics: String,
    val flagVkKaraoke: String,
    val flagVkChords: String,
    val flagTelegramLyrics: String,
    val flagTelegramKaraoke: String,
    val flagTelegramChords: String,
    val flagPlLyrics: String,
    val flagPlKaraoke: String,
    val flagPlChords: String,
    val processColorBoosty: String,
    val processColorVk: String,
    val processColorMeltLyrics: String,
    val processColorMeltKaraoke: String,
    val processColorMeltChords: String,
    val processColorDzenLyrics: String,
    val processColorDzenKaraoke: String,
    val processColorDzenChords: String,
    val processColorVkLyrics: String,
    val processColorVkKaraoke: String,
    val processColorVkChords: String,
    val processColorTelegramLyrics: String,
    val processColorTelegramKaraoke: String,
    val processColorTelegramChords: String,
    val processColorPlLyrics: String,
    val processColorPlKaraoke: String,
    val processColorPlChords: String,
    val resultVersion: Long
): Serializable, Comparable<SettingsDTOdigest> {

    private val sortString: String get() {
        return if (dateTimePublish == null) {
            listOf(
                author, year.toString(), album, "%3d".format(track)
            ).joinToString(" - ")
        } else
        {
            "%15d".format(dateTimePublish.time)
        }
    }

    override fun compareTo(other: SettingsDTOdigest): Int {
        return sortString.compareTo(other.sortString)
    }
}
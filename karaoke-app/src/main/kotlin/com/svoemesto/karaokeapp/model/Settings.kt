package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.SNS
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.sql.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.Date
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.math.abs


//@Component
@JsonIgnoreProperties(value = ["database", "pictureAuthor", "pictureAlbum"])
class Settings(val database: KaraokeConnection = WORKING_DATABASE): Serializable, Comparable<Settings> {

    private var _rootFolder: String = ""
    var readonly = false


    var rootFolder: String
        get() {
            if (_rootFolder == "") return _rootFolder
            if (File(_rootFolder).exists()) return _rootFolder
            if (database.name == "LOCAL") {
                val folders = _rootFolder.split("/").reversed()
                PROJECT_ROOT_FOLDERS.forEach { rsf ->
                    var fld = ""
                    folders.forEach { folder ->
                        fld = "/$folder$fld"
                        val candidate = "$rsf$fld"
                        if (File(candidate).exists()) {
                            _rootFolder = candidate

                            val sql = "UPDATE tbl_settings SET root_folder = ? WHERE id = ?"
                            val connection = database.getConnection()
                            if (connection == null) {
                                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                            } else {
                                val ps = connection.prepareStatement(sql)
                                ps.setString(1, candidate)
                                ps.setLong(2, id)
                                try {
                                    ps.executeUpdate()
                                    println("[${Timestamp.from(Instant.now())}] Новое значение root_folder сохранено в базе данных: $candidate")
                                } catch (e: Exception) {
                                    val errorMessage = "Не удалось сохранить запись в БД. Оригинальный текст ошибки: «${e.message}»"
                                    println(errorMessage)
                                }
                                ps.close()
                            }
                            return _rootFolder
                        }
                    }
                }
            }
            return _rootFolder
        }
        set(value) {_rootFolder = value}

    var ms: Long
        get() {
            val value = fields[SettingField.MS]?.toLongOrNull() ?: 0L
            return if (value == 0L) {
                val argForDurationFromAudioFileMs = listOf(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    fileAbsolutePath
                )
                val durationFromAudioFileMs = try {
                    val tmp = ((runCommand(argForDurationFromAudioFileMs).toDoubleOrNull() ?: 0.0) * 1000L).toLong()
                    fields[SettingField.MS] = tmp.toString()
                    val sql = "UPDATE tbl_settings SET song_ms = ? WHERE id = ?"
                    val connection = database.getConnection()
                    if (connection == null) {
                        println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                        return 0L
                    }
                    val ps = connection.prepareStatement(sql)
                    ps.setLong(1, tmp)
                    ps.setLong(2, id)
                    try {
                        ps.executeUpdate()
                    } catch (e: Exception) {
                        val errorMessage = "Не удалось сохранить запись в БД. Оригинальный текст ошибки: «${e.message}»"
                        println(errorMessage)
                    }
                    ps.close()
                    tmp
                } catch (e: Exception) {
                    value
                }
                durationFromAudioFileMs
            } else {
                value
            }
        }
        set(value) {fields[SettingField.MS] = value.toString()}

    var fileName: String = ""
    val rightSettingFileName: String get() {
        val resultList: MutableList<String> = mutableListOf()
        if (year != 0L ) resultList.add("$year")
        if (track != 0L ) resultList.add("(${"%02d".format(track)})")
        resultList.add("[$author] -")
        resultList.add(songName)
        return resultList.joinToString(" ").replace("?","").replace(":","-").replace("!","") //.rightFileName()
    }

    var tags: String = ""
    var fields: MutableMap<SettingField, String> = mutableMapOf()

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
    var statusProcessKaraoke: String = ""
    var statusProcessChords: String = ""
    var statusProcessMelody: String = ""

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
    val hasMelody: Boolean get() = sourceMarkersList.flatten().any {it.markertype in listOf(Markertype.SYLLABLES.value, Markertype.NOTE.value) && it.note.isNotBlank()}
    @get:JsonIgnore
    val hasChords: Boolean get() = sourceMarkersList.flatten().any {it.markertype in listOf(Markertype.SYLLABLES.value, Markertype.CHORD.value) && it.chord.isNotBlank()}

    @get:JsonIgnore
    val countVoices: Int get() = sourceMarkersList.size
    val countNotEmptyVoices: Int get() = sourceMarkersList.filter { it.isNotEmpty() }.size

    @get:JsonIgnore
    val sourceUnmute: List<Pair<Double, Double>> get() {
        if (sourceMarkersList.isEmpty()) return emptyList()
        val unMuteTimeList = sourceMarkersList[0].filter { it.markertype == Markertype.UNMUTE.value }.map { it.time }
        if (unMuteTimeList.isEmpty()) return emptyList()
        if (unMuteTimeList.size % 2 != 0) return emptyList()
        val result: MutableList<Pair<Double, Double>> = mutableListOf()
        for (i in unMuteTimeList.indices step 2) {
            result.add(Pair(unMuteTimeList[i], unMuteTimeList[i+1]))
        }
        return result
    }


    val pathToFileLyrics: String  get() = "${rootFolder}/done_files/$nameFileLyrics".rightFileName()
//    val pathToFileLyricsVk: String  get() = "${rootFolder}/done_files/$nameFileLyricsVk".rightFileName()
    val pathToFileKaraoke: String  get() = "${rootFolder}/done_files/$nameFileKaraoke".rightFileName()
//    val pathToFileKaraokeVk: String  get() = "${rootFolder}/done_files/$nameFileKaraokeVk".rightFileName()
    val pathToFileChords: String  get() = "${rootFolder}/done_files/$nameFileChords".rightFileName()
//    val pathToFileChordsVk: String  get() = "${rootFolder}/done_files/$nameFileChordsVk".rightFileName()
    val pathToFileMelody: String  get() = "${rootFolder}/done_files/$nameFileMelody".rightFileName()
//    val pathToFileMelodyVk: String  get() = "${rootFolder}/done_files/$nameFileMelodyVk".rightFileName()

    val pathToFile720Lyrics: String  get() = "$pathToFolder720Lyrics/${nameFileLyrics.replace(" [lyrics].mp4", " [lyrics] 720p.mp4")}".rightFileName()
    val pathToFileMP3Lyrics: String  get() = "$pathToFolderMP3Lyrics/${nameFileLyrics.replace(" [lyrics].mp4", " [lyrics].mp3")}".rightFileName()
    val pathToFile720Karaoke: String  get() = "$pathToFolder720Karaoke/${nameFileKaraoke.replace(" [karaoke].mp4", " [karaoke] 720p.mp4")}".rightFileName()
    val pathToFileMP3Karaoke: String  get() = "$pathToFolderMP3Karaoke/${nameFileKaraoke.replace(" [karaoke].mp4", " [karaoke].mp3")}".rightFileName()
    val pathToFile720Chords: String  get() = "$pathToFolder720Chords/${nameFileChords.replace(" [chords].mp4", " [chords] 720p.mp4")}".rightFileName()
    val pathToFile720Melody: String  get() = "$pathToFolder720Melody/${nameFileMelody.replace(" [tabs].mp4", " [tabs] 720p.mp4")}".rightFileName()

    val pathToFolder720Lyrics: String  get() = "$PATH_TO_STORE_FOLDER/720p_Lyrics/${author} 720p".rightFileName()
    val pathToFolderMP3Lyrics: String  get() = "$PATH_TO_STORE_FOLDER/MP3_Lyrics/${author} MP3".rightFileName()
    val pathToFolder720Karaoke: String  get() = "$PATH_TO_STORE_FOLDER/720p_Karaoke/${author} 720p".rightFileName()
    val pathToFolderMP3Karaoke: String  get() = "$PATH_TO_STORE_FOLDER/MP3_Karaoke/${author} MP3".rightFileName()
    val pathToFolder720Chords: String  get() = "$PATH_TO_STORE_FOLDER/720p_Chords/${author} 720p".rightFileName()
    val pathToFolder720Melody: String  get() = "$PATH_TO_STORE_FOLDER/720p_TABS/${author} 720p".rightFileName()

    val pathToStoreFileLyrics: String  get() = "$pathToStoreFolderLyrics/$nameFileLyrics".rightFileName()
    val pathToStoreFileKaraoke: String  get() = "$pathToStoreFolderKaraoke/$nameFileKaraoke".rightFileName()
    val pathToStoreFileChords: String  get() = "$pathToStoreFolderChords/$nameFileChords".rightFileName()
    val pathToStoreFileMelody: String  get() = "$pathToStoreFolderMelody/$nameFileMelody".rightFileName()

    val pathToStoreFolderLyrics: String  get() = "$PATH_TO_STORE_FOLDER/Lyrics/${author} - Lyrics".rightFileName()
    val pathToStoreFolderKaraoke: String  get() = "$PATH_TO_STORE_FOLDER/Karaoke/${author} - Karaoke".rightFileName()
    val pathToStoreFolderChords: String  get() = "$PATH_TO_STORE_FOLDER/Chords/${author} - Chords".rightFileName()
    val pathToStoreFolderMelody: String  get() = "$PATH_TO_STORE_FOLDER/TABS/${author} - TABS".rightFileName()

    val nameFileLogoAlbum: String  get() = "${rightSettingFileName} [album].png".rightFileName()
    val nameFileLogoAuthor: String  get() = "${rightSettingFileName} [author].png".rightFileName()
    val nameFileLyrics: String  get() = "${rightSettingFileName} [lyrics].mp4".rightFileName()
//    val nameFileLyricsVk: String  get() = "${rightSettingFileName} [lyricsVk].mp4".rightFileName()
    val nameFileKaraoke: String  get() = "${rightSettingFileName} [karaoke].mp4".rightFileName()
//    val nameFileKaraokeVk: String  get() = "${rightSettingFileName} [karaokeVk].mp4".rightFileName()
    val nameFileChords: String  get() = "${rightSettingFileName} [chords].mp4".rightFileName()
//    val nameFileChordsVk: String  get() = "${rightSettingFileName} [chordsVk].mp4".rightFileName()
    val nameFileMelody: String  get() = "${rightSettingFileName} [tabs].mp4".rightFileName()
//    val nameFileMelodyVk: String  get() = "${rightSettingFileName} [tabsVk].mp4".rightFileName()

    val pathToFolderSheetsage: String  get() = "${rootFolder}/sheetsage".rightFileName()
    val nameFileSheetsagePDF: String  get() = "${rightSettingFileName} [sheetsage].pdf".rightFileName()
    val nameFileSheetsageMIDI: String  get() = "${rightSettingFileName} [sheetsage].midi".rightFileName()
    val nameFileSheetsageLY: String  get() = "${rightSettingFileName} [sheetsage].ly".rightFileName()
    val nameFileSheetsageBeattimes: String  get() = "${rightSettingFileName} [beattimes].txt".rightFileName()
    val pathToFileSheetsagePDF: String  get() = "$pathToFolderSheetsage/$nameFileSheetsagePDF".rightFileName()
    val pathToFileSheetsageMIDI: String  get() = "$pathToFolderSheetsage/$nameFileSheetsageMIDI".rightFileName()
    val pathToFileSheetsageLY: String  get() = "$pathToFolderSheetsage/$nameFileSheetsageLY".rightFileName()
    val pathToFileSheetsageBeattimes: String  get() = "$pathToFolderSheetsage/$nameFileSheetsageBeattimes".rightFileName()

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
            try {
                SimpleDateFormat("dd.MM.yy HH:mm").parse("$date $time")
            } catch (e: Exception) {
                null
            }
        }
    }
    val onAir: Boolean get() = (dateTimePublish != null && dateTimePublish!! <= Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time)
    val year: Long get() = fields[SettingField.YEAR]?.toLongOrNull() ?: 0L
    val track: Long get() = fields[SettingField.TRACK]?.toLongOrNull() ?: 0L
    val key: String get() = fields[SettingField.KEY] ?: ""
    val bpm: Long get() = fields[SettingField.BPM]?.toLongOrNull() ?: 0L
    val resultVersion: Long get() = fields[SettingField.RESULT_VERSION]?.toLongOrNull() ?: 0L
    val diffBeats: Long get() = fields[SettingField.DIFFBEATS]?.toLongOrNull() ?: 0L
    val subtitleFileName: String get() = "${rightSettingFileName}.kdenlive.srt"
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
        var pic = Pictures.loadFromDbByName(pictureNameAuthor, database)
        if (pic == null) {
            val pathToFile = pathToFileLogoAuthor
            if (pathToFile != "") {
                val pictureBites = File(pathToFile).inputStream().readAllBytes()
                val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
                val resizedBi = if (bi.width == 1000 && bi.height == 400) bi else resizeBufferedImage(bi, newW = 1000, newH = 400)
                val previewBi = resizeBufferedImage(bi, newW = 125, newH = 50)
                val iosFull = ByteArrayOutputStream()
                ImageIO.write(resizedBi, "png", iosFull)
                val full = Base64.getEncoder().encodeToString(iosFull.toByteArray())
                val iosPreview = ByteArrayOutputStream()
                ImageIO.write(previewBi, "png", iosPreview)
                val preview = Base64.getEncoder().encodeToString(iosPreview.toByteArray())
//                val fullPicture = java.util.Base64.getEncoder().encodeToString(File(pathToFile).inputStream().readAllBytes())
                val pict = Pictures(database)
                pict.name = pictureNameAuthor
                pict.full = full
                pict.preview = preview
                pic = Pictures.createDbInstance(pict, database)
            }
        }
        return pic
    }

    @get:JsonIgnore
    val pictureAlbum: Pictures? get() {
        var pic = Pictures.loadFromDbByName(pictureNameAlbum, database)
        if (pic == null) {
            val pathToFile = pathToFileLogoAlbum
            if (pathToFile != "") {
                val pictureBites = File(pathToFile).inputStream().readAllBytes()
                val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
                val resizedBi = if (bi.width == 400 && bi.height == 400) bi else resizeBufferedImage(bi, newW = 400, newH = 400)
                val previewBi = resizeBufferedImage(bi, newW = 50, newH = 50)
                val iosFull = ByteArrayOutputStream()
                ImageIO.write(resizedBi, "png", iosFull)
                val full = Base64.getEncoder().encodeToString(iosFull.toByteArray())
                val iosPreview = ByteArrayOutputStream()
                ImageIO.write(previewBi, "png", iosPreview)
                val preview = Base64.getEncoder().encodeToString(iosPreview.toByteArray())
//                val fullPicture = java.util.Base64.getEncoder().encodeToString(File(pathToFile).inputStream().readAllBytes())
                val pict = Pictures(database)
                pict.name = pictureNameAlbum
                pict.full = full
                pict.preview = preview
                pic = Pictures.createDbInstance(pict, database)
            }
        }
        return pic
    }

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
    val versionBoosty: Int get() = (fields[SettingField.VERSION_BOOSTY]?.nullIfEmpty() ?: "0").toInt()
    val idBoostyFiles: String get() = fields[SettingField.ID_BOOSTY_FILES]?.nullIfEmpty() ?: ""
    val versionBoostyFiles: Int get() = (fields[SettingField.VERSION_BOOSTY_FILES]?.nullIfEmpty() ?: "0").toInt()
    val idVk: String get() = fields[SettingField.ID_VK]?.nullIfEmpty() ?: ""
    val idDzenLyrics: String get() = fields[SettingField.ID_DZEN_LYRICS]?.nullIfEmpty() ?: ""
    val idDzenKaraoke: String get() = fields[SettingField.ID_DZEN_KARAOKE]?.nullIfEmpty() ?: ""
    val idDzenChords: String get() = fields[SettingField.ID_DZEN_CHORDS]?.nullIfEmpty() ?: ""
    val idDzenMelody: String get() = fields[SettingField.ID_DZEN_MELODY]?.nullIfEmpty() ?: ""
    val versionDzenLyrics: Int get() = (fields[SettingField.VERSION_DZEN_LYRICS]?.nullIfEmpty() ?: "0").toInt()
    val versionDzenKaraoke: Int get() = (fields[SettingField.VERSION_DZEN_KARAOKE]?.nullIfEmpty() ?: "0").toInt()
    val versionDzenChords: Int get() = (fields[SettingField.VERSION_DZEN_CHORDS]?.nullIfEmpty() ?: "0").toInt()
    val versionDzenMelody: Int get() = (fields[SettingField.VERSION_DZEN_MELODY]?.nullIfEmpty() ?: "0").toInt()

    val idVkLyrics: String get() = fields[SettingField.ID_VK_LYRICS]?.nullIfEmpty() ?: ""
    val idVkLyricsOID: String get() = if (idVkLyrics!="" && idVkLyrics.contains("_")) idVkLyrics.split("_")[0] else ""
    val idVkLyricsID: String get() = if (idVkLyrics!="" && idVkLyrics.contains("_")) idVkLyrics.split("_")[1] else ""
    val idVkKaraoke: String get() = fields[SettingField.ID_VK_KARAOKE]?.nullIfEmpty() ?: ""
    val idVkKaraokeOID: String get() = if (idVkKaraoke!="" && idVkKaraoke.contains("_")) idVkKaraoke.split("_")[0] else ""
    val idVkKaraokeID: String get() = if (idVkKaraoke!="" && idVkKaraoke.contains("_")) idVkKaraoke.split("_")[1] else ""
    val idVkChords: String get() = fields[SettingField.ID_VK_CHORDS]?.nullIfEmpty() ?: ""
    val idVkMelody: String get() = fields[SettingField.ID_VK_MELODY]?.nullIfEmpty() ?: ""
    val idVkChordsOID: String get() = if (idVkChords!="" && idVkChords.contains("_")) idVkChords.split("_")[0] else ""
    val idVkMelodyOID: String get() = if (idVkMelody!="" && idVkMelody.contains("_")) idVkMelody.split("_")[0] else ""
    val idVkChordsID: String get() = if (idVkChords!="" && idVkChords.contains("_")) idVkChords.split("_")[1] else ""
    val idVkMelodyID: String get() = if (idVkMelody!="" && idVkMelody.contains("_")) idVkMelody.split("_")[1] else ""

    val versionVkLyrics: Int get() = (fields[SettingField.VERSION_VK_LYRICS]?.nullIfEmpty() ?: "0").toInt()
    val versionVkKaraoke: Int get() = (fields[SettingField.VERSION_VK_KARAOKE]?.nullIfEmpty() ?: "0").toInt()
    val versionVkChords: Int get() = (fields[SettingField.VERSION_VK_CHORDS]?.nullIfEmpty() ?: "0").toInt()
    val versionVkMelody: Int get() = (fields[SettingField.VERSION_VK_MELODY]?.nullIfEmpty() ?: "0").toInt()

    val idTelegramLyrics: String get() = fields[SettingField.ID_TELEGRAM_LYRICS]?.nullIfEmpty() ?: ""
    val idTelegramKaraoke: String get() = fields[SettingField.ID_TELEGRAM_KARAOKE]?.nullIfEmpty() ?: ""
    val idTelegramChords: String get() = fields[SettingField.ID_TELEGRAM_CHORDS]?.nullIfEmpty() ?: ""
    val idTelegramMelody: String get() = fields[SettingField.ID_TELEGRAM_MELODY]?.nullIfEmpty() ?: ""

    val versionTelegramLyrics: Int get() = (fields[SettingField.VERSION_TELEGRAM_LYRICS]?.nullIfEmpty() ?: "0").toInt()
    val versionTelegramKaraoke: Int get() = (fields[SettingField.VERSION_TELEGRAM_KARAOKE]?.nullIfEmpty() ?: "0").toInt()
    val versionTelegramChords: Int get() = (fields[SettingField.VERSION_TELEGRAM_CHORDS]?.nullIfEmpty() ?: "0").toInt()
    val versionTelegramMelody: Int get() = (fields[SettingField.VERSION_TELEGRAM_MELODY]?.nullIfEmpty() ?: "0").toInt()


    val idPlLyrics: String get() = fields[SettingField.ID_PL_LYRICS]?.nullIfEmpty() ?: ""
    val idPlKaraoke: String get() = fields[SettingField.ID_PL_KARAOKE]?.nullIfEmpty() ?: ""
    val idPlChords: String get() = fields[SettingField.ID_PL_CHORDS]?.nullIfEmpty() ?: ""
    val idPlMelody: String get() = fields[SettingField.ID_PL_MELODY]?.nullIfEmpty() ?: ""

    val versionPlLyrics: Int get() = (fields[SettingField.VERSION_PL_LYRICS]?.nullIfEmpty() ?: "0").toInt()
    val versionPlKaraoke: Int get() = (fields[SettingField.VERSION_PL_KARAOKE]?.nullIfEmpty() ?: "0").toInt()
    val versionPlChords: Int get() = (fields[SettingField.VERSION_PL_CHORDS]?.nullIfEmpty() ?: "0").toInt()
    val versionPlMelody: Int get() = (fields[SettingField.VERSION_PL_MELODY]?.nullIfEmpty() ?: "0").toInt()


    val idSponsr: String get() = fields[SettingField.ID_SPONSR]?.nullIfEmpty() ?: ""
    val versionSponsr: Int get() = (fields[SettingField.VERSION_SPONSR]?.nullIfEmpty() ?: "0").toInt()
    val indexTabsVariant: Int get() = (fields[SettingField.INDEX_TABS_VARIANT]?.nullIfEmpty() ?: "0").toInt()

    val rate: Int get() = (fields[SettingField.RATE]?.nullIfEmpty() ?: "0").toInt()

    val linkSM: String get() = URL_PREFIX_SM.replace("{REPLACE}", id.toString())
    val linkBoosty: String? get() = idBoosty.let {URL_PREFIX_BOOSTY.replace("{REPLACE}", idBoosty)}
    val linkBoostyFiles: String? get() = idBoostyFiles.let {URL_PREFIX_BOOSTY.replace("{REPLACE}", idBoostyFiles)}
    val linkVk: String? get() = idVk?.let {URL_PREFIX_VK.replace("{REPLACE}", idVk)}
    val linkDzenLyricsPlay: String? get() = idDzenLyrics.let {URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", idDzenLyrics)}
    val linkDzenLyricsEdit: String? get() = idDzenLyrics.let {URL_PREFIX_DZEN_EDIT.replace("{REPLACE}", idDzenLyrics)}

    val linkDzenKaraokePlay: String? get() = idDzenKaraoke.let {URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", idDzenKaraoke)}
    val linkDzenKaraokeEdit: String? get() = idDzenKaraoke.let {URL_PREFIX_DZEN_EDIT.replace("{REPLACE}", idDzenKaraoke)}

    val linkDzenChordsPlay: String? get() = idDzenChords.let {URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", idDzenChords)}
    val linkDzenChordsEdit: String? get() = idDzenChords.let {URL_PREFIX_DZEN_EDIT.replace("{REPLACE}", idDzenChords)}
    val linkDzenMelodyPlay: String? get() = idDzenMelody.let {URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", idDzenMelody)}
    val linkDzenMelodyEdit: String? get() = idDzenMelody.let {URL_PREFIX_DZEN_EDIT.replace("{REPLACE}", idDzenMelody)}

    val linkVkLyricsPlay: String? get() = idVkLyrics.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkLyrics)}
    val linkVkLyricsEdit: String? get() = idVkLyrics.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkLyrics)}

    val linkVkKaraokePlay: String? get() = idVkKaraoke.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkKaraoke)}
    val linkVkKaraokeEdit: String? get() = idVkKaraoke.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkKaraoke)}

    val linkVkChordsPlay: String? get() = idVkChords.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkChords)}
    val linkVkChordsEdit: String? get() = idVkChords.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkChords)}

    val linkVkMelodyPlay: String? get() = idVkMelody.let {URL_PREFIX_VK_PLAY.replace("{REPLACE}", idVkMelody)}
    val linkVkMelodyEdit: String? get() = idVkMelody.let {URL_PREFIX_VK_EDIT.replace("{REPLACE}", idVkMelody)}

    val linkTelegramLyricsPlay: String? get() = idTelegramLyrics.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramLyrics)}
    val linkTelegramLyricsEdit: String? get() = idTelegramLyrics.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramLyrics)}

    val linkTelegramKaraokePlay: String? get() = idTelegramKaraoke.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramKaraoke)}
    val linkTelegramKaraokeEdit: String? get() = idTelegramKaraoke.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramKaraoke)}

    val linkTelegramChordsPlay: String? get() = idTelegramChords.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramChords)}
    val linkTelegramChordsEdit: String? get() = idTelegramChords.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramChords)}

    val linkTelegramMelodyPlay: String? get() = idTelegramMelody.let {URL_PREFIX_TELEGRAM_PLAY.replace("{REPLACE}", idTelegramMelody)}
    val linkTelegramMelodyEdit: String? get() = idTelegramMelody.let {URL_PREFIX_TELEGRAM_EDIT.replace("{REPLACE}", idTelegramMelody)}

    val linkPlLyricsPlay: String? get() = idPlLyrics.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlLyrics)}
    val linkPlLyricsEdit: String? get() = idPlLyrics.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlLyrics)}

    val linkPlKaraokePlay: String? get() = idPlKaraoke.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlKaraoke)}
    val linkPlKaraokeEdit: String? get() = idPlKaraoke.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlKaraoke)}

    val linkPlChordsPlay: String? get() = idPlChords.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlChords)}
    val linkPlChordsEdit: String? get() = idPlChords.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlChords)}

    val linkPlMelodyPlay: String? get() = idPlMelody.let {URL_PREFIX_PL_PLAY.replace("{REPLACE}", idPlMelody)}
    val linkPlMelodyEdit: String? get() = idPlMelody.let {URL_PREFIX_PL_EDIT.replace("{REPLACE}", idPlMelody)}

    val linkSponsrPlay: String get() = if (idSponsr == "") "" else URL_PREFIX_SPONSR_PLAY.replace("{REPLACE}", idSponsr)
    val linkSponsrEdit: String get() = if (idSponsr == "") "" else URL_PREFIX_SPONSR_EDIT.replace("{REPLACE}", idSponsr)

    val flagBoosty: String get() =
        if (idBoosty == "null" || idBoosty == "") {
            "-"
        } else if (idBoostyFiles == "null" || idBoostyFiles == "") {
            "✅"
        } else {
            if (versionBoosty != resultVersion.toInt()) versionBoosty.toString() else "✔"
        }

    val flagSponsr: String get() = if (idSponsr == "null" || idSponsr == "") "-" else if (versionSponsr != resultVersion.toInt()) versionSponsr.toString() else "✓"
    val flagBoostyFiles: String get() = if (idBoostyFiles == "null" || idBoostyFiles == "") "-" else if (versionBoostyFiles != resultVersion.toInt()) versionBoostyFiles.toString() else "✓"
    val flagVk: String get() = if (idVk == "null" || idVk == "") "-" else "✓"
    val flagDzenLyrics: String get() = if (idDzenLyrics == "null" || idDzenLyrics == "") "-" else if (versionDzenLyrics != resultVersion.toInt()) versionDzenLyrics.toString() else "✓"
    val flagDzenKaraoke: String get() = if (idDzenKaraoke == "null" || idDzenKaraoke == "") "-" else if (versionDzenKaraoke != resultVersion.toInt()) versionDzenKaraoke.toString() else "✓"
    val flagDzenChords: String get() = if (idDzenChords == "null" || idDzenChords == "") "-" else if (versionDzenChords != resultVersion.toInt()) versionDzenChords.toString() else "✓"
    val flagDzenMelody: String get() = if (idDzenMelody == "null" || idDzenMelody == "") "-" else if (versionDzenMelody != resultVersion.toInt()) versionDzenMelody.toString() else "✓"

    val flagVkLyrics: String get() = if (idVkLyrics == "null" || idVkLyrics == "") "-" else if (versionVkLyrics != resultVersion.toInt()) versionVkLyrics.toString() else "✓"
    val flagVkKaraoke: String get() = if (idVkKaraoke == "null" || idVkKaraoke == "") "-" else if (versionVkKaraoke != resultVersion.toInt()) versionVkKaraoke.toString() else "✓"
    val flagVkChords: String get() = if (idVkChords == "null" || idVkChords == "") "-" else if (versionVkChords != resultVersion.toInt()) versionVkChords.toString() else "✓"
    val flagVkMelody: String get() = if (idVkMelody == "null" || idVkMelody == "") "-" else if (versionVkMelody != resultVersion.toInt()) versionVkMelody.toString() else "✓"


    val flagTelegramLyrics: String get() = if (idTelegramLyrics == "null" || idTelegramLyrics == "") "-" else if (versionTelegramLyrics != resultVersion.toInt()) versionTelegramLyrics.toString() else "✓"
    val flagTelegramKaraoke: String get() = if (idTelegramKaraoke == "null" || idTelegramKaraoke == "") "-" else if (versionTelegramKaraoke != resultVersion.toInt()) versionTelegramKaraoke.toString() else "✓"
    val flagTelegramChords: String get() = if (idTelegramChords == "null" || idTelegramChords == "") "-" else if (versionTelegramChords != resultVersion.toInt()) versionTelegramChords.toString() else "✓"
    val flagTelegramMelody: String get() = if (idTelegramMelody == "null" || idTelegramMelody == "") "-" else if (versionTelegramMelody != resultVersion.toInt()) versionTelegramMelody.toString() else "✓"

    val flagPlLyrics: String get() = if (idPlLyrics == "null" || idPlLyrics == "") "-" else if (versionPlLyrics != resultVersion.toInt()) versionPlLyrics.toString() else "✓"
    val flagPlKaraoke: String get() = if (idPlKaraoke == "null" || idPlKaraoke == "") "-" else if (versionPlKaraoke != resultVersion.toInt()) versionPlKaraoke.toString() else "✓"
    val flagPlChords: String get() = if (idPlChords == "null" || idPlChords == "") "-" else if (versionPlChords != resultVersion.toInt()) versionPlChords.toString() else "✓"
    val flagPlMelody: String get() = if (idPlMelody == "null" || idPlMelody == "") "-" else if (versionPlMelody != resultVersion.toInt()) versionPlMelody.toString() else "✓"

    val pathToResultedModel: String get() = "$rootFolder/$DEMUCS_MODEL_NAME"
//    val pathToSymlinkFolder: String get() = "$rootFolder/symlink"
    val pathToSymlinkFolderMP4: String get() = "$rootFolder/symlink_mp4"
    val pathToSymlinkFolderPNG: String get() = "$rootFolder/symlink_png"
    val pathToSymlinkFolderBoostyPNG: String get() = "$rootFolder/symlink_boosty_png"
    val pathToSymlinkFolderBoostyFiles: String get() = "$rootFolder/symlink_boosty_files"
    val separatedStem: String get() = "vocals"
    val oldNoStemNameWav: String get() = "$pathToResultedModel/$fileName-no_$separatedStem.wav"
    val newNoStemNameWav: String get() = "$pathToResultedModel/$fileName-accompaniment.wav"
    val newNoStemNameFlac: String get() = "$pathToResultedModel/$fileName-accompaniment.flac"
    val newNoStemNameFlacSymlink: String get() = "$pathToSymlinkFolderBoostyFiles/$fileName-accompaniment.flac"
    val vocalsNameWav: String get() = "$pathToResultedModel/$fileName-vocals.wav"
    val vocalsNameFlac: String get() = "$pathToResultedModel/$fileName-vocals.flac"
    val vocalsNameFlacSymlink: String get() = "$pathToSymlinkFolderBoostyFiles/$fileName-vocals.flac"
    val drumsNameWav: String get() = "$pathToResultedModel/$fileName-drums.wav"
    val drumsNameFlac: String get() = "$pathToResultedModel/$fileName-drums.flac"
    val bassNameWav: String get() = "$pathToResultedModel/$fileName-bass.wav"
    val bassNameFlac: String get() = "$pathToResultedModel/$fileName-bass.flac"
    val guitarsNameWav: String get() = "$pathToResultedModel/$fileName-guitars.wav"
    val guitarsNameFlac: String get() = "$pathToResultedModel/$fileName-guitars.flac"
    val otherNameWav: String get() = "$pathToResultedModel/$fileName-other.wav"
    val otherNameFlac: String get() = "$pathToResultedModel/$fileName-other.flac"
    val fileAbsolutePath: String get() = "$rootFolder/$fileName.flac"
    val fileAbsolutePathTmp: String get() = "$rootFolder/$fileName-tmp.flac"
    val fileAbsolutePathSymlink: String get() = "$pathToSymlinkFolderBoostyFiles/$fileName.flac"
    val fileSettingsAbsolutePath: String get() = "$rootFolder/$rightSettingFileName.settings"

    val relativePathToFile: String get() = "../$fileName.flac"
    val relativePathToNoStemNameFlac: String get() = "../$DEMUCS_MODEL_NAME/$fileName-accompaniment.flac"
    val relativePathToVocalsNameFlac: String get() = "../$DEMUCS_MODEL_NAME/$fileName-vocals.flac"

    val fileNameVocals: String get() = "${fileName}-vocals.flac"
    val fileNameAccompaniment: String get() = "${fileName}-accompaniment.flac"

    val kdenliveFileName: String get() = "$rootFolder/$rightSettingFileName.kdenlive"
    val kdenliveSubsFileName: String get() = "$rootFolder/$rightSettingFileName.kdenlive.srt"
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
    val linkDzenKaraoke: String get() = if (idDzenKaraoke == "") "" else linkDzenKaraokePlay!!
    val linkDzenLyrics: String get() = if (idDzenLyrics == "") "" else linkDzenLyricsPlay!!
    val linkDzenTabs: String get() = if (idDzenMelody == "") "" else linkDzenMelodyPlay!!
    val linkDzenChords: String get() = if (idDzenChords == "") "" else linkDzenChordsPlay!!

    val linkVkKaraoke: String get() = if (idVkKaraoke == "") "" else linkVkKaraokePlay!!
    val linkVkLyrics: String get() = if (idVkLyrics == "") "" else linkVkLyricsPlay!!
    val linkVkTabs: String get() = if (idVkMelody == "") "" else linkVkMelodyPlay!!
    val linkVkChords: String get() = if (idVkChords == "") "" else linkVkChordsPlay!!

    val linkTgKaraoke: String get() = if (idTelegramKaraoke == "" || idTelegramKaraoke == "-") "" else linkTelegramKaraokePlay!!
    val linkTgLyrics: String get() = if (idTelegramLyrics == "" || idTelegramLyrics == "-") "" else linkTelegramLyricsPlay!!
    val linkTgTabs: String get() = if (idTelegramMelody == "" || idTelegramMelody == "-") "" else linkTelegramMelodyPlay!!
    val linkTgChords: String get() = if (idTelegramChords == "" || idTelegramChords == "-") "" else linkTelegramChordsPlay!!

    val linkPlKaraoke: String get() = if (idPlKaraoke == "") "" else linkPlKaraokePlay!!
    val linkPlLyrics: String get() = if (idPlLyrics == "") "" else linkPlLyricsPlay!!
    val linkPlTabs: String get() = if (idPlMelody == "") "" else linkPlMelodyPlay!!
    val linkPlChords: String get() = if (idPlChords == "") "" else linkPlChordsPlay!!
    val datePublish: String get() = if (date == "" || time == "") "Дата пока не определена" else "${date} ${time}"

    fun argsDemucs2(): List<List<String>> {

        // Сначала копируем файл аудио в папку PATH_TO_TEMP_DEMUCS_FOLDER с именем file.flac, потом вызываем докер,
        // потом копируем оттуда результат и удаляем папку PATH_TO_TEMP_DEMUCS_FOLDER
        val tmpFileName = "file"
        return listOf(
            listOf("mkdir", "-p", pathToResultedModel),
            listOf("chmod", "777", pathToResultedModel),
            listOf("mkdir", "-p", PATH_TO_TEMP_DEMUCS_FOLDER),
            listOf("chmod", "777", PATH_TO_TEMP_DEMUCS_FOLDER),
            listOf("cp", fileAbsolutePath.rightFileName(), "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName.flac"),
            listOf(
                "docker", "run", "--rm", "-i", "--name=demucs",
                "-v", "$PATH_TO_TEMP_DEMUCS_FOLDER:/data/input",
                "-v", "$PATH_TO_TEMP_DEMUCS_FOLDER:/data/output",
                "svoemestodev/demucs:latest",
                "''./demucs2 -file $PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName.flac -recode flac''"
            ),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-accompaniment.flac", "${newNoStemNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${newNoStemNameFlac.rightFileName()}"),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-vocals.flac", "${vocalsNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${vocalsNameFlac.rightFileName()}"),
            listOf("rm", "-rf", PATH_TO_TEMP_DEMUCS_FOLDER)
        )
//        val settings = this
//        return listOf(
//        listOf("python3", "-m", "demucs", "-n", DEMUCS_MODEL_NAME, "-d", "cpu", "--filename", "{track}-{stem}.{ext}",
//            "--two-stems=${settings.separatedStem}",
//            "-o",
//            settings.rootFolder.rightFileName(),
//            settings.fileAbsolutePath.rightFileName()
//        ),
//        ),
//        listOf("mv", settings.oldNoStemNameWav.rightFileName(), settings.newNoStemNameWav.rightFileName()),
//        listOf("ffmpeg", "-i", settings.newNoStemNameWav.rightFileName(), "-compression_level", "8", settings.newNoStemNameFlac.rightFileName(), "-y"),
//        listOf("rm", settings.newNoStemNameWav.rightFileName()),
//        listOf("ffmpeg", "-i", settings.vocalsNameWav.rightFileName(), "-compression_level", "8", settings.vocalsNameFlac.rightFileName(), "-y"),
//        listOf("rm", settings.vocalsNameWav.rightFileName())
//        )
    }

    fun argsDemucs5(): List<List<String>> {

        // Сначала копируем файл аудио в папку PATH_TO_TEMP_DEMUCS_FOLDER с именем file.flac, потом вызываем докер,
        // потом копируем оттуда результат и удаляем папку PATH_TO_TEMP_DEMUCS_FOLDER
        val tmpFileName = "file"
        return listOf(
            listOf("mkdir", "-p", pathToResultedModel),
            listOf("chmod", "777", pathToResultedModel),
            listOf("mkdir", "-p", PATH_TO_TEMP_DEMUCS_FOLDER),
            listOf("chmod", "777", PATH_TO_TEMP_DEMUCS_FOLDER),
            listOf("cp", fileAbsolutePath.rightFileName(), "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName.flac"),
            listOf(
                "docker", "run", "--rm", "-i", "--name=demucs",
                "-v", "$PATH_TO_TEMP_DEMUCS_FOLDER:/data/input",
                "-v", "$PATH_TO_TEMP_DEMUCS_FOLDER:/data/output",
                "svoemestodev/demucs:latest",
                "''./demucs5 -file $PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName.flac -recode flac''"
            ),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-accompaniment.flac", "${newNoStemNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${newNoStemNameFlac.rightFileName()}"),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-vocals.flac", "${vocalsNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${vocalsNameFlac.rightFileName()}"),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-drums.flac", "${drumsNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${drumsNameFlac.rightFileName()}"),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-bass.flac", "${bassNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${bassNameFlac.rightFileName()}"),
            listOf("mv", "$PATH_TO_TEMP_DEMUCS_FOLDER/$tmpFileName-other.flac", "${otherNameFlac.rightFileName()}"),
            listOf("chmod", "666", "${otherNameFlac.rightFileName()}"),
            listOf("rm", "-rf", PATH_TO_TEMP_DEMUCS_FOLDER)
        )
//        val settings = this
//        return listOf(
//            listOf("python3", "-m", "demucs", "-n", DEMUCS_MODEL_NAME, "-d", "cpu", "--filename", "{track}-{stem}.{ext}",
//                "--two-stems=${settings.separatedStem}",
//                "-o",
//                settings.rootFolder.rightFileName(),
//                settings.fileAbsolutePath.rightFileName()
//            ),
//            listOf("mv", settings.oldNoStemNameWav.rightFileName(), settings.newNoStemNameWav.rightFileName()),
//            listOf("ffmpeg", "-i", settings.newNoStemNameWav.rightFileName(), "-compression_level", "8", settings.newNoStemNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.newNoStemNameWav.rightFileName()),
//            listOf("ffmpeg", "-i", settings.vocalsNameWav.rightFileName(), "-compression_level", "8", settings.vocalsNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.vocalsNameWav.rightFileName()),
//            listOf("python3", "-m", "demucs", "-n", DEMUCS_MODEL_NAME, "-d", "cpu", "--filename", "{track}-{stem}.{ext}",
//                "-o",
//                settings.rootFolder.rightFileName(),
//                settings.fileAbsolutePath.rightFileName()
//            ),
//            listOf("ffmpeg", "-i", settings.drumsNameWav.rightFileName(), "-compression_level", "8", settings.drumsNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.drumsNameWav.rightFileName()),
//            listOf("ffmpeg", "-i", settings.bassNameWav.rightFileName(), "-compression_level", "8", settings.bassNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.bassNameWav.rightFileName()),
//            listOf("ffmpeg", "-i", settings.guitarsNameWav.rightFileName(), "-compression_level", "8", settings.guitarsNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.guitarsNameWav.rightFileName()),
//            listOf("ffmpeg", "-i", settings.otherNameWav.rightFileName(), "-compression_level", "8", settings.otherNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.otherNameWav.rightFileName()),
//            listOf("ffmpeg", "-i", settings.vocalsNameWav.rightFileName(), "-compression_level", "8", settings.vocalsNameFlac.rightFileName(), "-y"),
//            listOf("rm", settings.vocalsNameWav.rightFileName())
//        )
    }

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

        val beattimesSource = bodyBeattimes.substring(1,bodyBeattimes.length-2).split(", ").map { it.toDouble() }
        val beattimes: MutableList<Double> = mutableListOf()
        for (i in 0 until beattimesSource.size-1) {
            val currentBeatTime = beattimesSource[i]
            val nextBeatTime = beattimesSource[i+1]
            val lenOneBeat = (nextBeatTime - currentBeatTime) / 4
            beattimes.add(currentBeatTime)
            beattimes.add(currentBeatTime + lenOneBeat)
            beattimes.add(currentBeatTime + lenOneBeat*2)
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
            var chordTime = beattimes[indexBeat]

            chords.forEach { chordText ->
                val matchResultChord = regexChord.find(chordText)
                if (matchResultChord != null) {
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

                    chordsList.add(chord)
                }

            }
            result["chords"] = chordsList
        }

        return result
    }

    fun getAudioAspectRate(): String {

        val args = listOf(
            "ffprobe",
            "-v", "error",
            "-select_streams", "a:0",
            "-show_entries", "stream=sample_rate",
            "-of", "default=noprint_wrappers=1:nokey=1",
            fileAbsolutePath
        )
        return runCommand(args)

    }

    fun getVKPictureBase64(): String = getVKPictureBase64(this)
    @get:JsonIgnore
    val kdenliveTemplate: String get() = "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<mlt LC_NUMERIC=\"C\" producer=\"main_bin\" version=\"7.28.0\" root=\"${rootFolder.replace("&", "&amp;")}\">\n" +
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
    val processColorMeltMelody: String get() = getColorToProcessTypeName(statusProcessMelody)

    val processColorVk: String get() = if (idVk.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorBoosty: String get() =
        if (idBoosty.isNotBlank() && idBoostyFiles.isNotBlank()) {
            "#00FF00"
        } else if (idBoosty.isNotBlank()) {
            "#00B500"
        } else {
            "#A9A9A9"
        }
    val processColorSponsr: String get() = if (idSponsr.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorBoostyFiles: String get() = if (idBoostyFiles.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorVkLyrics: String get() = if (idVkLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorVkKaraoke: String get() = if (idVkKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorVkChords: String get() = if (idVkChords.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorVkMelody: String get() = if (idVkMelody.isNotBlank()) "#00FF00" else "#A9A9A9"

    val processColorDzenLyrics: String get() = if (idDzenLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorDzenKaraoke: String get() = if (idDzenKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorDzenChords: String get() = if (idDzenChords.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorDzenMelody: String get() = if (idDzenMelody.isNotBlank()) "#00FF00" else "#A9A9A9"

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
    val processColorTelegramMelody: String get() =
        if (idTelegramMelody == "-" || idTelegramMelody == "-" ) {
            "#F08080"
        } else if (idTelegramMelody.isNotBlank()) {
            "#00FF00"
        } else {
            "#A9A9A9"
        }

    val processColorPlLyrics: String get() = if (idPlLyrics.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorPlKaraoke: String get() = if (idPlKaraoke.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorPlChords: String get() = if (idPlChords.isNotBlank()) "#00FF00" else "#A9A9A9"
    val processColorPlMelody: String get() = if (idPlMelody.isNotBlank()) "#00FF00" else "#A9A9A9"

    @get:JsonIgnore
    val haveBoostyLink: Boolean get() = idBoosty.isNotBlank()
    @get:JsonIgnore
    val haveSponsrLink: Boolean get() = idSponsr.isNotBlank()
    @get:JsonIgnore
    val haveBoostyFilesLink: Boolean get() = idBoostyFiles.isNotBlank()
    @get:JsonIgnore
    val haveVkGroupLink: Boolean get() = idVk.isNotBlank()
    @get:JsonIgnore
    val haveDzenLinks: Boolean get() = idDzenLyrics.isNotBlank() ||
            idDzenKaraoke.isNotBlank() ||
            idDzenChords.isNotBlank()
    @get:JsonIgnore
    val haveVkLinks: Boolean get() = idVkLyrics.isNotBlank() ||
            idVkKaraoke.isNotBlank() ||
            idVkChords.isNotBlank()
    @get:JsonIgnore
    val haveTelegramLinks: Boolean get() = idTelegramLyrics.isNotBlank() && idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-"
    @get:JsonIgnore
    val flags: String get() = if (haveBoostyLink && haveVkGroupLink && haveVkLinks && haveTelegramLinks && haveDzenLinks) "" else "(${if (haveBoostyLink) "b" else "-"}${if (haveVkGroupLink) "g" else "-"}${if (haveVkLinks) "v" else "-"}${if (haveTelegramLinks) "t" else "-"}${if (haveDzenLinks) "z" else "-"}) "

    @get:JsonIgnore
    val havePlLinks: Boolean get() = idPlLyrics.isNotBlank() ||
            idPlKaraoke.isNotBlank() || idPlChords.isNotBlank()

    @get:JsonIgnore
    val digest: String get() =
       (if (firstSongInAlbum) "Альбом: «${album}» (${year})\n\n" else "") +
       "$songName\n$linkBoosty\n" +
                "${if (idVkKaraoke.isNotBlank()) "Karaoke VK $linkVkKaraokePlay\n" else ""}${if (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") "Karaoke TG $linkTelegramKaraokePlay\n" else ""}${if (idDzenKaraoke.isNotBlank()) "Karaoke DZ $linkDzenKaraokePlay\n" else ""}" +
                "${if (idVkLyrics.isNotBlank()) "Lyrics VK $linkVkLyricsPlay\n" else ""}${if (idTelegramLyrics.isNotBlank()) "Lyrics TG $linkTelegramLyricsPlay\n" else ""}${if (idDzenLyrics.isNotBlank()) "Lyrics DZ $linkDzenLyricsPlay\n" else ""}\n"
    @get:JsonIgnore
    val digestIsFull: Boolean get() = idVkKaraoke.isNotBlank() && (idTelegramKaraoke.isNotBlank() && idTelegramKaraoke != "-") && idDzenKaraoke.isNotBlank() && idVkLyrics.isNotBlank() && idTelegramLyrics.isNotBlank() && idDzenLyrics.isNotBlank()

    @get:JsonIgnore
    var voicesForMlt: List<SettingVoice> = emptyList()


    val songLengthMs: Long get()  = (sourceMarkersList.maxOf { it.firstOrNull { it.markertype == Markertype.SETTING.value && it.label == "END" }?.time ?: 0.0 } * 1000).toLong()

    val endTimecode: String get() = convertMillisecondsToTimecode(songLengthMs)

    val propAudioVolumeOn: String get() {
        val prop = mutableListOf<String>()
        prop.add("00:00:00.000=0")
        prop.add("${endTimecode}=0")
        return prop.joinToString(";")
    }

    val propAudioVolumeOff: String get() {
        val prop = mutableListOf<String>()
        prop.add("00:00:00.000=-100")
        prop.add("${endTimecode}=-100")
        return prop.joinToString(";")
    }

    val propAudioVolumeCustom: String get() {
        val prop = mutableListOf<String>()
        prop.add("00:00:00.000=-100")
        sourceUnmute.forEach { (unMuteStart, unMuteEnd) ->
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteStart*1000).toLong())- 2)}=-100")
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteStart*1000).toLong()))}=0")
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteEnd*1000).toLong()))}=0")
            prop.add("${convertFramesToTimecode(convertMillisecondsToFrames((unMuteEnd*1000).toLong())+ 2)}=-100")
        }
        prop.add("${endTimecode}=-100")
        return prop.joinToString(";")
    }

    fun getOutputFilename(songOutputFile: SongOutputFile, songVersion: SongVersion? = null, relative: Boolean = false): String {
        val folderName = when (songOutputFile) {
            SongOutputFile.PROJECT,
            SongOutputFile.SUBTITLE,
            SongOutputFile.MLT,
            SongOutputFile.RUN,
            SongOutputFile.RUNALL,
            SongOutputFile.TEXT -> "done_projects"
            SongOutputFile.PICTURECHORDS -> "done_chords"
            else -> "done_files"
        }

        val fileName = "${rightSettingFileName}${if (songVersion == null) "" else songVersion.suffix}"
        val fileNameSuffix = when (songOutputFile) {
            SongOutputFile.PICTURECHORDS -> " chords"
            SongOutputFile.PICTUREBOOSTY -> " boosty"
            SongOutputFile.PICTUREVK -> " VK"
            SongOutputFile.VK -> " [VK]"
            SongOutputFile.PICTUREBOOSTYTEASER -> " [boosty]"
            SongOutputFile.PICTUREBOOSTYFILES -> " [files]"
            SongOutputFile.PICTURESPONSRTEASER -> " [sponsr]"
            else -> ""
        }

        return "${if (relative) ".." else rootFolder}/$folderName/$fileName$fileNameSuffix.${songOutputFile.extension}".rightFileName()
    }

    fun clone(): Settings {
        val result = Settings()
        result._rootFolder = this._rootFolder
        result.readonly = this.readonly
        result.fileName = this.fileName
        result.tags = this.tags
        result.firstSongInAlbum = this.firstSongInAlbum
        result.sourceText = this.sourceText
        result.resultText = this.resultText
        result.statusProcessLyrics = this.statusProcessLyrics
        result.statusProcessKaraoke = this.statusProcessKaraoke
        result.statusProcessChords = this.statusProcessChords
        result.statusProcessMelody = this.statusProcessMelody
        result.sourceMarkers = this.sourceMarkers
        result.voicesForMlt = this.voicesForMlt
        result.fields = this.fields
        return result
    }

    fun getMltProp(songVersion: SongVersion): MltProp {
        val mltProp = MltProp()

        // setSettings - текущий settings
        mltProp.setSettings(this)

        this.voicesForMlt = getVoices(this, songVersion)

        val startSilentOffsetMs = getStartSilentOffsetMs()

        // setFrameWidthPx - ширина экрана в пикселях
        mltProp.setFrameWidthPx(Karaoke.frameWidthPx)

        // setFrameHeightPx - высота экрана в пикселях
        mltProp.setFrameHeightPx(Karaoke.frameHeightPx)

        // setSongVersion - версия (Lyrics, Karaoke и т.п.)
        mltProp.setSongVersion(songVersion)

        // setCountAudioTracks - кол-во аудиодорожек. Зависит от songVersion
        val countAudioTracks = songVersion.producers.count { it.isAudio && it != ProducerType.MAINBIN }
        mltProp.setCountAudioTracks(countAudioTracks)

        /** setCountAllTracks - кол-во треков. треки аудио + 10 видео треков.
         V1 - BACKGROUND
         V2 - SPLASHSTART
         V3 - BOOSTY
         V4 - HORIZON
         V5 - FLASH
         V6 - PROGRESS
         V7 - VOICES
         V8 - FADERTEXT
         V9 - HEADER
         V10 - WATERMARK
        **/
        val countAllTracks = countAudioTracks + 10
        mltProp.setCountAllTracks(countAllTracks)

        // setCountVoices - кол-во голосов. Минимум 1
        val countVoices = voicesForMlt.size
        mltProp.setCountVoices(countVoices)

        // setSongCapo - лад каподастра
        val markers = this.sourceMarkersList[0]
        val capo = markers
            .firstOrNull { marker -> marker.markertype == Markertype.SETTING.value && marker.label.startsWith("CAPO|") }
            ?.label?.split("|")?.get(1)?.toInt() ?: 0
        mltProp.setSongCapo(capo)

        // setSongLengthFr - длительность песни в кадрах
        val songLengthFr = convertMillisecondsToFrames(songLengthMs + startSilentOffsetMs)
        mltProp.setSongLengthFr(songLengthFr)

        // setSplashLengthMs - длительность заставки (5 секунд) в миллисекундах
        val splashLengthMs = Karaoke.timeSplashScreenLengthMs
        mltProp.setSplashLengthMs(splashLengthMs)

        // setBoostyLengthMs - длительность бусти (3 секунды) в миллисекундах
        val boostyLengthMs = Karaoke.timeBoostyLengthMs
        mltProp.setBoostyLengthMs(boostyLengthMs)

        // НЕ ИСПОЛЬЗУЕТСЯ
        // setTimelineLengthMs - длительность таймлайна в миллисекундах
        val timelineLengthMs = songLengthMs + splashLengthMs + boostyLengthMs

        // setTimelineStartTimecode - таймкод начала таймлайна
        val timelineStartTimecode = convertMillisecondsToTimecode(0L)
        mltProp.setTimelineStartTimecode(timelineStartTimecode)

        // setTimelineEndTimecode - таймкод конца таймлайна
        val timelineEndTimecode = convertMillisecondsToTimecode(timelineLengthMs)
        mltProp.setTimelineEndTimecode(timelineEndTimecode)


        // setTotalLengthFr - общая длительность в кадрах
        val totalLengthFr = convertMillisecondsToFrames(timelineLengthMs + startSilentOffsetMs)
        mltProp.setTotalLengthFr(totalLengthFr)

        // setBackgroundLengthFr - общая длительность фона в кадрах
        val backgroundLengthFr = totalLengthFr
        mltProp.setBackgroundLengthFr(backgroundLengthFr)

        // setFadeLengthMs - время затухания в миллисекундах (1 сек)
        val fadeMs = 1000L
//        mltProp.setFadeLengthMs(fadeMs)

        // setSongStartTimecode - таймкод начала песни (0)
        val songStartTimecode = convertMillisecondsToTimecode(0L)
        mltProp.setSongStartTimecode(songStartTimecode)

        // setSongEndTimecode - таймкод конца песни
        val songEndTimecode = convertMillisecondsToTimecode(songLengthMs + startSilentOffsetMs)
        mltProp.setSongEndTimecode(songEndTimecode)

        // setSongFadeInTimecode - таймкод начала фейда (fadeMs)
        val songFadeInTimecode = convertMillisecondsToTimecode(fadeMs)
        mltProp.setSongFadeInTimecode(songFadeInTimecode)

        // songFadeOutTimecode - таймкод конца фейда (за fadeMs до конца песни)
        val songFadeOutTimecode = convertMillisecondsToTimecode(songLengthMs-fadeMs + startSilentOffsetMs)
        mltProp.setSongFadeOutTimecode(songFadeOutTimecode)


        // setTotalStartTimecode - таймкод начала всего (0)
        val totalStartTimecode = convertMillisecondsToTimecode(0L)
        mltProp.setTotalStartTimecode(totalStartTimecode)

        // setTotalEndTimecode - таймкод конца всего (общая длительность)
        val totalEndTimecode = convertMillisecondsToTimecode(timelineLengthMs + startSilentOffsetMs)
        mltProp.setTotalEndTimecode(totalEndTimecode)

        // setBackgroundEndTimecode - таймкод конца aфона (общая длительность)
        val backgroundEndTimecode = totalEndTimecode
        mltProp.setBackgroundEndTimecode(backgroundEndTimecode)

        // setTotalFadeInTimecode - таймкод начала фейда всего (fadeMs)
        val totalFadeInTimecode = convertMillisecondsToTimecode(fadeMs)
        mltProp.setTotalFadeInTimecode(totalFadeInTimecode)

        // setTotalFadeOutTimecode - таймкод конча фейда всего (за fadeMs до конца всего)
        val totalFadeOutTimecode = convertMillisecondsToTimecode(timelineLengthMs-fadeMs + startSilentOffsetMs)
        mltProp.setTotalFadeOutTimecode(totalFadeOutTimecode)


        // setBoostyStartTimecode - таймкод начала бусти (длина заставки)
        val boostyBlankTimecode = convertMillisecondsToTimecode(splashLengthMs)
        mltProp.setBoostyStartTimecode(boostyBlankTimecode)



        // setBoostyEndTimecode - таймкод конца бусти (длина заставки + длина бусти)
        val boostyEndTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs)
        val voiceBlankTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs + startSilentOffsetMs)
        mltProp.setBoostyEndTimecode(boostyEndTimecode)

        // setBoostyFadeInTimecode - начало фейда бусти (начало бусти + fadeMs)
        val boostyFadeInTimecode = convertMillisecondsToTimecode(splashLengthMs+fadeMs)
        mltProp.setBoostyFadeInTimecode(boostyFadeInTimecode)

        // setBoostyFadeOutTimecode - конец фейда бусти (за fadeMs до конца бусти)
        val boostyFadeOutTimecode = convertMillisecondsToTimecode(splashLengthMs + boostyLengthMs-fadeMs)
        mltProp.setBoostyFadeOutTimecode(boostyFadeOutTimecode)

        // setBoostyBlankTimecode - то же, что и setBoostyStartTimecode
        mltProp.setBoostyBlankTimecode(boostyBlankTimecode)

        // setVoiceBlankTimecode - то же, что и setBoostyEndTimecode
        mltProp.setVoiceBlankTimecode(voiceBlankTimecode)


        // setSplashStartTimecode - таймкод начала заставки (0)
        mltProp.setSplashStartTimecode(songStartTimecode)

        // setSplashEndTimecode - таймкод конца заставки (длина заставки)
        mltProp.setSplashEndTimecode(boostyBlankTimecode)

        // setSplashFadeInTimecode - таймкод начала фейда заставки (fadeMs)
        val splashFadeInTimecode = songFadeInTimecode
        mltProp.setSplashFadeInTimecode(splashFadeInTimecode)

        // setSplashFadeOutTimecode - таймкод конца фейда заставки (за fadeMs до конца заставки)
        val splashFadeOutTimecode = convertMillisecondsToTimecode(splashLengthMs-fadeMs)
        mltProp.setSplashFadeOutTimecode(splashFadeOutTimecode)

        val offsetInAudioMs = splashLengthMs + boostyLengthMs
        val offsetInVideoMs = splashLengthMs + boostyLengthMs
        val offsetInAudioTimecode = convertMillisecondsToTimecode(offsetInAudioMs)
        val offsetInVideoTimecode = convertMillisecondsToTimecode(offsetInVideoMs)

        mltProp.setStartSilentOffsetMs(startSilentOffsetMs)

//        val audioLengthFr = convertMillisecondsToFrames(songLengthMs)
        val argForDurationFromAudioFileMs = listOf(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            fileAbsolutePath
        )
        val durationFromAudioFileMs = ((runCommand(argForDurationFromAudioFileMs).toDoubleOrNull() ?: 0.0) * 1000L).toLong()
        val audioLengthFr = convertMillisecondsToFrames(durationFromAudioFileMs)
        mltProp.setAudioLengthFr(audioLengthFr)
//        val audioEndTimecode = convertMillisecondsToTimecode(songLengthMs - startSilentOffsetMs)
        val audioEndTimecode = convertMillisecondsToTimecode(songLengthMs)
        mltProp.setAudioEndTimecode(audioEndTimecode)
//        val progressSymbolHalfWidth = 0 //(getTextWidthHeightPx(Karaoke.progressSymbol, Karaoke.progressFont.font).first/2).toLong()
//        val fontNameSizePt = Integer.min(getFontSizeBySymbolWidth(1100.0 / songName.length), 80)
//        val yOffset = 0 //-5

        // setSongName - название песни
        mltProp.setSongName(songName.replace("&", "&amp;"))

        // setSongTone - тональность песни
        mltProp.setSongTone(this.key.replace(" minor", "m").replace(" major", ""))

        // setSongBpm - темп песни
        mltProp.setSongBpm(this.bpm.toString())

        // setVolume - громкость определенного аудиотрека в зависимости от songVersion
        when(songVersion) {
            SongVersion.LYRICS -> {
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOVOCAL)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOMUSIC)
                mltProp.setVolume(propAudioVolumeOn, ProducerType.AUDIOSONG)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOBASS)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIODRUMS)
            }
            SongVersion.KARAOKE -> {
                mltProp.setVolume(propAudioVolumeCustom, ProducerType.AUDIOVOCAL)
                mltProp.setVolume(propAudioVolumeOn, ProducerType.AUDIOMUSIC)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOSONG)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOBASS)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIODRUMS)
            }
            SongVersion.CHORDS -> {
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOVOCAL)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOMUSIC)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOSONG)
                mltProp.setVolume(propAudioVolumeOn, ProducerType.AUDIOBASS)
                mltProp.setVolume(propAudioVolumeOn, ProducerType.AUDIODRUMS)
            }
            SongVersion.TABS -> {
                mltProp.setVolume(propAudioVolumeCustom, ProducerType.AUDIOVOCAL)
                mltProp.setVolume(propAudioVolumeOn, ProducerType.AUDIOMUSIC)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOSONG)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIOBASS)
                mltProp.setVolume(propAudioVolumeOff, ProducerType.AUDIODRUMS)
            }
        }

        // setFileName - имя файла в зависимости от SongOutputFile
        mltProp.setFileName(getOutputFilename(SongOutputFile.VIDEO, songVersion).replace("&", "&amp;"), SongOutputFile.VIDEO)

        // setTone - тональность песни (для HEADER)
        mltProp.setTone(key, ProducerType.HEADER)

        // setBpm - Bpm песни (для HEADER)
        mltProp.setBpm(bpm, ProducerType.HEADER)

        // setYear - Year песни (для HEADER)
        mltProp.setYear(year, ProducerType.HEADER)

        // setAuthor - Author песни (для HEADER)
        mltProp.setAuthor(author.replace("&", "&amp;amp;"), ProducerType.HEADER)

        // setAlbum - Album песни (для HEADER)
        mltProp.setAlbum(album.replace("&", "&amp;amp;"), ProducerType.HEADER)

        // setSongName - Название песни (для HEADER)
        mltProp.setSongName(songName.replace("&", "&amp;amp;"), ProducerType.HEADER)

        // setPath - путь к файлу логотипа автора для песни
        mltProp.setPath(pathToFileLogoAuthor.replace("&", "&amp;amp;"), "LogoAuthor")

        // setPath - путь к файлу логотипа альбома для песни
        mltProp.setPath(pathToFileLogoAlbum.replace("&", "&amp;amp;"), "LogoAlbum")

        // setBase64 - base64 файла логотипа автора для песни
        mltProp.setBase64(pathToFileLogoAuthor.base64ifFileExists(), "LogoAuthor")

        // setBase64 - base64 файла логотипа альбома для песни
        mltProp.setBase64(pathToFileLogoAlbum.base64ifFileExists(), "LogoAlbum")

        // setPath - путь к файлу бусти-заставки для песни
        mltProp.setPath("/sm-karaoke/system/SPLASH.png", ProducerType.BOOSTY)

        // setBase64 - base64 файла бусти-заставки для песни
        mltProp.setBase64("/sm-karaoke/system/SPLASH.png".base64ifFileExists(), ProducerType.BOOSTY)

        // setChords
        val chords = voicesForMlt[0].linesForMlt()
            .flatMap { line -> line.getElements(SongVersion.CHORDS) }
            .flatMap { element -> element.getSyllables() }
            .filter { syllable -> syllable.chord.isNotEmpty() }
            .toMutableList()
        mltProp.setChords(chords)

        mltProp.setRootFolder(rootFolder.replace("&", "&amp;"), "Song")
        mltProp.setLengthMs(songLengthMs, "Song")
//        mltProp.setLengthFr(songLengthFr, "Song")
//        mltProp.setLengthFr(convertMillisecondsToFrames(timelineLengthMs, Karaoke.frameFps), "Total")
        mltProp.setInOffsetVideo(offsetInVideoTimecode)

        val listOfVoices = voicesForMlt

        listOfVoices.forEachIndexed { indexVoice, voice ->
            mltProp.setCountChilds(voice.linesForMlt().size, listOf(ProducerType.LINES, indexVoice))
            mltProp.setId(ProducerType.LINES.ordinal*1000 + indexVoice*100, listOf(ProducerType.LINES, indexVoice))
            val key = listOf(ProducerType.LINES, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

            voice.linesForMlt().forEachIndexed { indexLine, line ->
                mltProp.setCountChilds(line.getElements(songVersion).size, listOf(ProducerType.ELEMENT, indexVoice, indexLine))
                mltProp.setId(ProducerType.ELEMENT.ordinal*1000 + indexVoice*100 + indexLine*10, listOf(ProducerType.ELEMENT, indexVoice, indexLine))
                var key = listOf(ProducerType.ELEMENT, indexVoice, indexLine)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

                ProducerType.ELEMENT.childs().asReversed().forEach {
                    if (it in songVersion.producers) {
                        mltProp.setCountChilds(line.getElements(songVersion).size, listOf(it, indexVoice, indexLine))
                        mltProp.setId(it.ordinal*1000 + indexVoice*100 + indexLine*10, listOf(it, indexVoice, indexLine))
                        key = listOf(it, indexVoice, indexLine)
                        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                    }
                }
            }

            chords.forEachIndexed{ indexChord, chord ->
                val indexLine = indexChord

                mltProp.setId(ProducerType.CHORDPICTURELINES.ordinal*1000 + indexVoice*100, listOf(ProducerType.CHORDPICTURELINES, indexVoice))
                val key = listOf(ProducerType.CHORDPICTURELINES, indexVoice)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

                mltProp.setId(ProducerType.CHORDPICTURELINE.ordinal*1000 + indexVoice*100 + indexLine*10, listOf(ProducerType.CHORDPICTURELINE, indexVoice, indexLine))
                val key1 = listOf(ProducerType.CHORDPICTURELINE, indexVoice, indexLine)
                if (mltProp.getUUID(key1) == "") mltProp.setUUID(getStoredUuid(key1), key1)

                mltProp.setId(ProducerType.CHORDPICTUREELEMENT.ordinal*1000 + indexVoice*100 + indexLine*10, listOf(ProducerType.CHORDPICTUREELEMENT, indexVoice, indexLine))
                val key2 = listOf(ProducerType.CHORDPICTUREELEMENT, indexVoice, indexLine)
                if (mltProp.getUUID(key2) == "") mltProp.setUUID(getStoredUuid(key2), key2)

                ProducerType.CHORDPICTUREELEMENT.childs().asReversed().forEach {
                    if (it in songVersion.producers) {
                        mltProp.setId(it.ordinal*1000 + indexVoice*100 + indexLine*10, listOf(it, indexVoice, indexLine))
                        val key3 = listOf(it, indexVoice, indexLine)
                        if (mltProp.getUUID(key3) == "") mltProp.setUUID(getStoredUuid(key3), key3)
                    }
                }

            }
        }



        // Цикл по голосам для предварительной инициализации параметров
        for (voiceId in 0 until countVoices) {
            ProducerType.values().forEach { type ->
                if (type.ids.isEmpty()) {
                    mltProp.setId(type.ordinal*100 + voiceId*10, listOf(type, voiceId))
                    val key = listOf(type, voiceId)
                    if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                } else {
                    for (childId in 0 until type.ids.size) {
                        mltProp.setId(type.ordinal*100 + voiceId*10 + childId*1, listOf(type, voiceId, childId))
                        val key = listOf(type, voiceId, childId)
                        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                    }
                }

            }

        }

        mltProp.setPath(audioSongFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOSONG))
        mltProp.setPath(audioMusicFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOMUSIC))
        mltProp.setPath(audioVocalFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOVOCAL))
        mltProp.setPath(audioBassFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIOBASS))
        mltProp.setPath(audioDrumsFileName.replace("&", "&amp;"), listOf(ProducerType.AUDIODRUMS))

        mltProp.setEnabled(Karaoke.createAudioVocal, listOf(ProducerType.AUDIOVOCAL))
        mltProp.setEnabled(Karaoke.createAudioMusic, listOf(ProducerType.AUDIOMUSIC))
        mltProp.setEnabled(Karaoke.createAudioSong, listOf(ProducerType.AUDIOSONG))
        mltProp.setEnabled(Karaoke.createAudioBass, listOf(ProducerType.AUDIOBASS))
        mltProp.setEnabled(Karaoke.createAudioDrums, listOf(ProducerType.AUDIODRUMS))
        mltProp.setEnabled(Karaoke.createBackground, listOf(ProducerType.BACKGROUND))
        mltProp.setEnabled(Karaoke.createHorizon, listOf(ProducerType.HORIZON))
        mltProp.setEnabled(Karaoke.createProgress, listOf(ProducerType.PROGRESS))
        mltProp.setEnabled(Karaoke.createFillsSongtext, listOf(ProducerType.FILLCOLORSONGTEXT))
        mltProp.setEnabled(Karaoke.createSongtext, listOf(ProducerType.SONGTEXT))
        mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.FADERTEXT))
        mltProp.setEnabled(Karaoke.createFader, listOf(ProducerType.CHORDPICTUREFADER))
        mltProp.setEnabled(Karaoke.createHeader, listOf(ProducerType.HEADER))
        mltProp.setEnabled(Karaoke.createCounters, listOf(ProducerType.COUNTER))
        mltProp.setEnabled(Karaoke.createWatermark, listOf(ProducerType.WATERMARK))

        val quarterNoteLengthMs =
            if (bpm == 0L) {
                (60000.0 / 120.0).toLong()
            } else {
                (60000.0 / bpm).toLong()
            }
        // Находим длительность звучания 1/4 ноты в миллисекундах
        val halfNoteLengthMs = quarterNoteLengthMs * 2

        // Времена для "вспыхивания" Тут времена всех каунтеров и времена начала всех текстовых линий
        val timesForFlashSet: MutableSet<Long> = mutableSetOf()

        // Находим первый текстовый элемент, из него получаем размер шрифта
        val textElement = this.voicesForMlt.firstOrNull()?.getLinesForCounters(songVersion)?.firstOrNull()?.textElement(songVersion)
        val fontSize = textElement?.fontSize ?: 10
        mltProp.setFontSize(fontSize)

        // Вычисляем ширину и высоту одного символа с найденным размером шрифта
        val symbolHeightPx = Karaoke.voices[0].groups[0].mltText.copy("0", fontSize).h()
        mltProp.setSymbolHeightPx(symbolHeightPx)
        val symbolWidthPx = Karaoke.voices[0].groups[0].mltText.copy("0", fontSize).w()

        // Позиция горизонта в пикселах по Y = половина экрана + половина высоты символа минус оффтсет (-7)
        val horizonPositionPx = ((Karaoke.frameHeightPx / 2 + symbolHeightPx.toLong() / 2) - Karaoke.horizonOffsetPx).toInt()

        mltProp.setPositionYPx(horizonPositionPx.toLong(), ProducerType.HORIZON)

        // Позиция каунтеров по Y = позиция горизонта минут высота символа
        mltProp.setPositionYPx((horizonPositionPx - symbolHeightPx).toLong(), ProducerType.COUNTER)

        val frameWPx = Karaoke.frameWidthPx
        val frameHPx = Karaoke.frameHeightPx

        for (voiceId in 0 until countVoices) {
            val voice = this.voicesForMlt[voiceId]
            // Расстояние по X в пикселях, на которое сдвинут голос относительно левого края экрана
            val deltaX = voice.longerElementPreviousVoice?.w() ?: 0
            val offsetX = Karaoke.songtextStartPositionXpx
            val currentVoiceOffset = deltaX + offsetX * (voiceId + 1)

            // Позиция каунтеров для голоса по X - посередине левого отступа
            mltProp.setPositionXPx((currentVoiceOffset - offsetX + Math.max((offsetX - symbolWidthPx) / 2, 0)).toLong(), listOf(ProducerType.COUNTER, voiceId))

            // Добавляем в сет флешеров времена начала текстовых линий и каунтеров
            timesForFlashSet.addAll(voice.getLines().filter { it.haveTextElement(songVersion) }.map { it.lineStartMs })
            val timesForCounters = voice.getLinesForCounters(songVersion).map { it.lineStartMs }
            // Для каждого каунтера заполняем TransformProperty
            for (counter in 0..4) {
                timesForFlashSet.addAll(timesForCounters.map { it - counter * halfNoteLengthMs }.filter { it > 0 })
                val counterTimesMs = timesForCounters.map { it - counter * halfNoteLengthMs }.filter { it > 0 }
                val counterTps: MutableList<TransformProperty> = mutableListOf()
                counterTimesMs.forEach { timeMs ->
                    counterTps.add(TransformProperty(time = convertFramesToTimecode(convertMillisecondsToFrames(timeMs) - 2),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 0.0))
                    counterTps.add(TransformProperty(time = convertMillisecondsToTimecode(timeMs),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 1.0))
                    counterTps.add(TransformProperty(time = convertFramesToTimecode(convertMillisecondsToFrames(timeMs + halfNoteLengthMs) - 2),x=0,y=-symbolHeightPx,w=frameWPx,h=frameHPx,opacity = 0.0))
                }
                mltProp.setRect(counterTps.joinToString(";") { it.toString() }, listOf(ProducerType.COUNTER, voiceId, counter))
            }
        }

        // В timesForFlashSet находятся времена начала всех текстовых линий всех голосов и времена всех каунтеров
        // Нужно преобразовать сет в лист, отсортировать, сформировать TransformProperty
        val timesForFlashList = timesForFlashSet.toList().sorted()
        val flashTps: MutableList<TransformProperty> = mutableListOf()
        timesForFlashList.forEachIndexed { index, timeMs ->
            val nextTime = if (index != timesForFlashList.size -1) timesForFlashList[index+1] else songLengthMs + startSilentOffsetMs
            val durationMs = convertFramesToMilliseconds(convertMillisecondsToFrames(Math.min(nextTime - timeMs, 1000)) - 2)
            flashTps.add(TransformProperty(time = convertFramesToTimecode(convertMillisecondsToFrames(timeMs) - 2),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 0.0))
            flashTps.add(TransformProperty(time = convertMillisecondsToTimecode(timeMs),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 1.0))
            flashTps.add(TransformProperty(time = convertMillisecondsToTimecode(timeMs + durationMs),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 0.0))
        }
        mltProp.setRect(flashTps.joinToString(";") { it.toString() }, listOf(ProducerType.FLASH))

        // Заполняем TransformProperty для HEADER
        // Изначально HEADER на экране. Начинать убираться вверх он должен в момент появления первого каунтера и делать это в течение 2 тактов (4 полуноты)
        // Начинать показываться сверху в конце песни HEADER должен в момен окончания последней текстовой строки, но не позже чем за 4 таката до конца песни
        val startTimeFirstCounterMs = timesForFlashList[0]
        val endTimeHidingHeaderMs = startSilentOffsetMs +
                Math.min((this.voicesForMlt.maxOfOrNull { voice -> (voice.getLastTextLine(songVersion)?.lineEndMs ?: songLengthMs) }
                    ?: (songLengthMs - halfNoteLengthMs * 8)), (songLengthMs - halfNoteLengthMs * 8))
        val propHeaderLineTps: MutableList<TransformProperty> = mutableListOf()
        propHeaderLineTps.add(TransformProperty(time = convertMillisecondsToTimecode(startTimeFirstCounterMs),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 1.0))
        propHeaderLineTps.add(TransformProperty(time = convertMillisecondsToTimecode(startTimeFirstCounterMs + halfNoteLengthMs * 4),x=0,y=-592,w=frameWPx,h=frameHPx,opacity = 1.0))
        propHeaderLineTps.add(TransformProperty(time = convertMillisecondsToTimecode(endTimeHidingHeaderMs),x=0,y=-592,w=frameWPx,h=frameHPx,opacity = 1.0))
        propHeaderLineTps.add(TransformProperty(time = convertMillisecondsToTimecode(endTimeHidingHeaderMs + halfNoteLengthMs * 4),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 1.0))
        mltProp.setRect(propHeaderLineTps.joinToString(";") { it.toString() }, listOf(ProducerType.HEADER))

        mltProp.setPath(getRandomFile(Karaoke.backgroundFolderPath, ".png"), listOf(ProducerType.BACKGROUND))

        val propProgressLineTps: MutableList<TransformProperty> = mutableListOf()
        propProgressLineTps.add(TransformProperty(time = convertMillisecondsToTimecode(0),x=0,y=0,w=frameWPx,h=frameHPx,opacity = 1.0))
        propProgressLineTps.add(TransformProperty(time = convertMillisecondsToTimecode(songLengthMs + startSilentOffsetMs),x=frameWPx,y=0,w=frameWPx,h=frameHPx,opacity = 1.0))
        mltProp.setRect(propProgressLineTps.joinToString(";") { it.toString() }, listOf(ProducerType.PROGRESS))

        return mltProp
    }


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
            if (APP_WORK_IN_CONTAINER) {
                val args = listOf("smplayer", pathToFileLyrics.wrapInApostraf())
                createScriptForHost(args = args)
            } else {
                val args = listOf("smplayer", pathToFileLyrics)
                runCommand(args = args, ignoreErrors = true)
            }
        } else {
            println("Не найден ${pathToFileLyrics}")
        }
    }

    fun playKaraoke() {
        if (File(pathToFileKaraoke).exists()) {
            if (APP_WORK_IN_CONTAINER) {
                val args = listOf("smplayer", pathToFileKaraoke.wrapInApostraf())
                createScriptForHost(args = args)
            } else {
                val args = listOf("smplayer", pathToFileKaraoke)
                runCommand(args = args, ignoreErrors = true)
            }
        } else {
            println("Не найден ${pathToFileKaraoke}")
        }
    }

    fun playChords() {
        if (File(pathToFileChords).exists()) {
            if (APP_WORK_IN_CONTAINER) {
                val args = listOf("smplayer", pathToFileChords.wrapInApostraf())
                createScriptForHost(args = args)
            } else {
                val args = listOf("smplayer", pathToFileChords)
                runCommand(args = args, ignoreErrors = true)
            }
        } else {
            println("Не найден ${pathToFileChords}")
        }
    }
    fun playTabs() {
        if (File(pathToFileMelody).exists()) {
            if (APP_WORK_IN_CONTAINER) {
                val args = listOf("smplayer", pathToFileMelody.wrapInApostraf())
                createScriptForHost(args = args)
            } else {
                val args = listOf("smplayer", pathToFileMelody)
                runCommand(args = args, ignoreErrors = true)
            }
        } else {
            println("Не найден ${pathToFileMelody}")
        }
    }

    fun getSongDurationAudioMs(): Long {
        return songLengthMs + getStartSilentOffsetMs()
    }
    fun getSongDurationAudioTimecode(): String {
        return convertMillisecondsToTimecode(getSongDurationAudioMs())
    }
    fun getSongDurationVideoMs(): Long {
        return getSongDurationAudioMs() + Karaoke.timeSplashScreenLengthMs + Karaoke.timeBoostyLengthMs
    }
    fun getSongDurationVideoMsTimecode(): String {
        return convertMillisecondsToTimecode(getSongDurationVideoMs())
    }
    fun getStartSilentOffsetMs(): Long {
        val timeFirstSyllable = try {
            (sourceMarkersList.minOf { lstMarkers -> lstMarkers.filter { marker -> marker.markertype in listOf(Markertype.SYLLABLES.value, Markertype.NOTE.value)}.map { it.time }.minOf { it } } * 1000).toLong()
        } catch (e: Exception) {
            0L
        }
        return  if (timeFirstSyllable > 5000L) 0L else 5000L - timeFirstSyllable
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

    fun setIndexTabsVariant(ind: Int) {
        fields[SettingField.INDEX_TABS_VARIANT] = ind.toString()
        saveToDb()
    }

    fun getSourceSyllables(voice: Int): List<String> {
        return if (sourceSyllablesList.size > voice) {
            sourceSyllablesList[voice]
        } else {
            emptyList()
        }
    }
    
    // По текстовой ноте возвращает массив "номер струны - номер лада"
    fun getStrings(note: String): List<GuitarStringLad> {
        val result: MutableList<GuitarStringLad> = mutableListOf()
        val guitar = listOf(
            listOf("E|4","F|4","F#|4","G|4","G#|4","A|4","A#|4","B|4","C|5","C#|5","D|5","D#|5","E|5","F|5","F#|5","G|5","G#|5","A|5","A#|5","B|5","C|6"),
            listOf("B|3","C|4","C#|4","D|4","D#|4","E|4","F|4","F#|4","G|4","G#|4","A|4","A#|4","B|4","C|5","C#|5","D|5","D#|5","E|5","F|5","F#|5","G|5"),
            listOf("G|3","G#|3","A|3","A#|3","B|3","C|4","C#|4","D|4","D#|4","E|4","F|4","F#|4","G|4","G#|4","A|4","A#|4","B|4","C|5","C#|5","D|5","D#|5"),
            listOf("D|3","D#|3","E|3","F|3","F#|3","G|3","G#|3","A|3","A#|3","B|3","C|4","C#|4","D|4","D#|4","E|4","F|4","F#|4","G|4","G#|4","A|4","A#|4"),
            listOf("A|2","A#|2","B|2","C|3","C#|3","D|3","D#|3","E|3","F|3","F#|3","G|3","G#|3","A|3","A#|3","B|3","C|4","C#|4","D|4","D#|4","E|4","F|4"),
            listOf("E|2","F|2","F#|2","G|2","G#|2","A|2","A#|2","B|2","C|3","C#|3","D|3","D#|3","E|3","F|3","F#|3","G|3","G#|3","A|3","A#|3","B|3","C|4"),
        )
        guitar.forEachIndexed { indexString, string ->
            string.forEachIndexed stringLoop@ { lad, noteInLad ->
                if (noteInLad == note) {
                    result.add(GuitarStringLad(string = indexString, lad = lad))
                    return@stringLoop
                }
            }
        }
        return result
    }

    data class GuitarStringLad(val string: Int, val lad: Int)
    data class DiffGuitarStringLad(val diff: Int, val guitarStringLad: GuitarStringLad)
    data class ListDiffGuitarStringLadDiff(val listDiffGuitarStringLad: List<DiffGuitarStringLad>, val diff: Int)

    fun getStringsForAllNotesInSong(voice: Int): List<List<DiffGuitarStringLad>> {
        val markersNotes = this.sourceMarkersList[voice].filter { it.note != "" }.map { it.note }
        if (markersNotes.size == 0) return emptyList()
        val notes = markersNotes.toSet().toList() // Уникальный список нот в песне
        val notesAndStringsArray: MutableMap<String, List<GuitarStringLad>> = mutableMapOf()
        notes.forEach { note ->
            notesAndStringsArray[note] = getStrings(note)
        }
        val variants: MutableList<ListDiffGuitarStringLadDiff> = mutableListOf()

        for (lad in 0 until 20) {
            var minLad = 20
            var maxLad = 0
            val variant: MutableList<DiffGuitarStringLad> = mutableListOf()
            var isBadLad = false
            markersNotes.forEach markersLoop@ { note ->

                val stringsForNote = notesAndStringsArray[note]!!
                val tmpArray = stringsForNote.filter {it.lad >= lad}
                    .map {DiffGuitarStringLad(diff = abs(it.lad - lad), guitarStringLad = it)}
                if (tmpArray.isNotEmpty()) {
                    var minDiffIndex = 0
                    var minDiff = tmpArray[minDiffIndex].diff
                    tmpArray.forEachIndexed { index, diffGuitarStringLad ->
                        if (minDiff > diffGuitarStringLad.diff ) {
                            minDiffIndex = index
                            minDiff = diffGuitarStringLad.diff
                        }
                    }
                    if (minLad > tmpArray[minDiffIndex].guitarStringLad.lad) {
                        minLad = tmpArray[minDiffIndex].guitarStringLad.lad
                    }
                    if (maxLad < tmpArray[minDiffIndex].guitarStringLad.lad) {
                        maxLad = tmpArray[minDiffIndex].guitarStringLad.lad
                    }
                    variant.add(tmpArray[minDiffIndex])
                } else {
                    isBadLad = true
                    return@markersLoop
                }

            }
            if (variant.isNotEmpty() && !isBadLad) {
                variants.add(ListDiffGuitarStringLadDiff(listDiffGuitarStringLad = variant, diff = abs(maxLad - minLad)))
            }

        }

        val minDiffLad = variants.minOf{it.diff}
        return variants.filter { it.diff == minDiffLad }.map {it.listDiffGuitarStringLad}

    }

    fun getFormattedChords(): String {
        val resultArray: MutableList<String> = mutableListOf()
        for (voice in 0 until countVoices) {

            val markers = this.sourceMarkersList[voice]
            val hasChords = markers.any { it.chord.isNotEmpty() }

            if (hasChords) {

                val BR = """<br>"""

                val SPAN_STYLE_CAPO = """<span style="color: #FFFF00; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
                val SPAN_STYLE_CHORD = """<span style="color: #00BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
                val SPAN_STYLE_TEXT = """<span style="color: #FFFFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
                val SPAN_END = """</span>"""

                var result = ""
                var wasBr = true
                var lineChords = ""
                var lineChordsIsEmpty = true
                var lineText = ""
                var slide = 0
                val capo = markers
                    .firstOrNull { marker -> marker.markertype == Markertype.SETTING.value && marker.label.startsWith("CAPO|") }
                    ?.label?.split("|")?.get(1)?.toInt() ?: 0

                if (capo > 0) {
                    val originalNote = key.replace(" minor", "").replace(" major", "")
                    val originalMinor = if (key.endsWith(" minor")) "m" else ""
                    val originalKey = originalNote + originalMinor
                    val newKey = getTransposingChord(originalKey, capo)

                    var lineCapo = SPAN_STYLE_CAPO + "Каподастр на $capo-м ладу" + SPAN_END + BR
                    lineCapo += SPAN_STYLE_CAPO + "Оригинальная тональность: $originalKey" + SPAN_END + BR
                    lineCapo += SPAN_STYLE_CAPO + "Аккорды для тональности: $newKey" + SPAN_END + BR
                    result += lineCapo + BR
                }
                markers.forEach { marker ->
                    when (marker.markertype) {
                        Markertype.SETTING.value -> {
                            if (marker.label.startsWith("GROUP|")) {
                                if (!lineChordsIsEmpty) result += lineChords + BR
                                result += lineText + BR
                                lineChordsIsEmpty = true
                                lineChords = ""
                                lineText = ""
                                wasBr = true
                            }
                        }
                        Markertype.ENDOFLINE.value,
                        Markertype.EOL_NOTE.value,
                        Markertype.EOL_CHORD.value,
                        Markertype.NEWLINE_NOTE.value,
                        Markertype.NEWLINE.value -> {
                            if (!lineChordsIsEmpty) result += lineChords + BR
                            result += lineText + BR
                            lineChordsIsEmpty = true
                            lineChords = ""
                            lineText = ""
                            wasBr = true
                        }

                        Markertype.SYLLABLES.value, Markertype.CHORD.value -> {
                            var txt= ""
                            var txtHtml = ""

                            if (marker.markertype == Markertype.CHORD.value) {
                                if (marker.label.isBlank() || marker.label == marker.chord) {
                                    txt = "♪  "
                                    txtHtml = "♪&nbsp;&nbsp;"
                                } else {
                                    txt = marker.label
                                    txtHtml = txt.replace(" ", "&nbsp;")
                                }
                            } else if (marker.markertype == Markertype.SYLLABLES.value) {
                                // Если в маркере не пустой лейбл (т.е. есть слог)
                                if (marker.label.isNotEmpty()) {

                                    txt = marker.label.replace("_", " ") // Заменяем подчеркивания на пробелы
                                    txtHtml = marker.label.replace("_", "&nbsp;")
                                    // Если был перенос строки - инициализируем новые переменны (3 пробела + слог)
                                    if (wasBr) {
                                        txt = "   " + txt.uppercaseFirstLetter()
                                        txtHtml = "&nbsp;&nbsp;&nbsp;" + marker.label.uppercaseFirstLetter().replace("_", "&nbsp;")
                                    }

                                } else {
                                    if (marker.markertype == Markertype.CHORD.value) {
                                        // Если в маркере пустой лейбл - пробел в тексте
                                        txt = " "
                                        txtHtml = "&nbsp;"
                                    }
                                }
                            }

                            var chord = ""
                            var chordHtml = ""

                            // Если в маркере есть аккорд
                            if (marker.chord.isNotEmpty()) {
                                // Находим аккорд
                                lineChordsIsEmpty = false

                                val (musicChord, musicNote) = MusicChord.getChordNote(marker.chord)
                                var newIndexNote = MusicNote.values().indexOf(musicNote!!) - capo
                                if (newIndexNote < 0) newIndexNote += MusicNote.values().size
                                val newNote = MusicNote.values()[newIndexNote]

                                chord = newNote.names.first() + musicChord!!.names.first()
                                chordHtml = chord

                                // Находим позицию гласной буквы в слоге (0 - если гласная первая или если её нет)
                                fun String.firstVowelIndex(): Int {
                                    val vovels = "♪ёуеыаоэяиюeuioaїієѣ" + "ёуеыаоэяиюeuioaїієѣ".uppercase()
                                    for (i in this.indices) {
                                        if (this[i] in vovels) {
                                            return i
                                        }
                                    }
                                    return 0
                                }
                                val vowelPosition = txt.firstVowelIndex()
                                // Добавляем пробелы перед названием аккорда (по позиции гласной)
                                chord = (0 until vowelPosition).joinToString("") { " " } + chord
                                chordHtml = (0 until vowelPosition).joinToString("") { "&nbsp;" } + chordHtml

                                // Если длина аккорда больше длины текста, то к слайдеру надо добавить кол-во символов разницы длины
                                // А если меньше - добавить пробелы после аккорда
                                val diff = txt.length - chord.length
                                if (diff < 0) {
                                    slide -= diff
                                } else if (diff > 0) {
                                    chord += " ".repeat(diff)
                                    chordHtml += "&nbsp;".repeat(diff)
                                }

                            } else {
                                // Если в маркере нет аккорда - пробелы по длине текста
                                // Если слайдер больше нуля
                                if (slide > 0) {
                                    // Если слайдер меньше длинны текущего текста
                                    if (slide < txt.length ) {
                                        // Текст аккорда должен состоять из пробелов по кол-ву "длина текста минус слайд", слайдер в ноль
                                        chord = " ".repeat(txt.length - slide)
                                        chordHtml = "&nbsp;".repeat(txt.length - slide)
                                        slide = 0
                                    } else if (slide == txt.length) {
                                        // Текст аккорда пустой, слайдер в ноль
                                        // Текст аккорда должен состоять из пробелов по кол-ву длины текста
                                        chord = " ".repeat(txt.length)
                                        chordHtml = "&nbsp;".repeat(txt.length)
                                        slide = 0
                                    } else {
                                        // Текст аккорда пустой, уменьшаем слайдер на длину текста
                                        chord = ""
                                        chordHtml = ""
                                        slide -= txt.length
                                    }
                                } else {
                                    // Текст аккорда должен состоять из пробелов по кол-ву длины текста
                                    chord = " ".repeat(txt.length)
                                    chordHtml = "&nbsp;".repeat(txt.length)
                                }
                            }

                            lineChords += SPAN_STYLE_CHORD + chordHtml + SPAN_END
                            lineText += SPAN_STYLE_TEXT + txtHtml + SPAN_END

                            wasBr = false
                        }
                        else -> {}
                    }

                }
                resultArray.add(result)
            }

        }
        return resultArray.joinToString("""<br><hr style="border: 2px solid blue;"><br>""")
    }
    fun getFormattedNotes(): String {
        val resultArray: MutableList<String> = mutableListOf()
        for (voice in 0 until countVoices) {

            val stringsForAllNotesArray = getStringsForAllNotesInSong(voice)
            if (stringsForAllNotesArray.isEmpty()) break
            val indexStringsForAllNotes = stringsForAllNotesArray.size.coerceAtMost(indexTabsVariant)

            val SPAN_STYLE_NOTE = """<span style="color: #00BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_TABLINE = """<span style="color: #66BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_TEXT = """<span style="color: #FFFFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_OCTAVE = """<span style="color: #444444; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
            val SPAN_STYLE_DEFIS = """<span style="color: #666666; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">"""
            val SPAN_END = """</span>"""
            val BR = """<br>"""
            val stringsForAllNotes = stringsForAllNotesArray[indexStringsForAllNotes]
            var stringsForAllNotesIndex = 0
            val markers = this.sourceMarkersList[voice]
            var wasBr = true
            var result = ""
            var lineNotes = ""
            var lineText = ""
            var strings = mutableListOf("E‖⎼","B‖⎼","G‖⎼","D‖⎼","A‖⎼","e‖⎼")
            markers.forEach { marker ->
                when (marker.markertype) {
                    Markertype.ENDOFLINE.value,
                    Markertype.EOL_NOTE.value,
                    Markertype.NEWLINE_NOTE.value,
                    Markertype.NEWLINE.value -> {
                        if (!wasBr) {
                            strings.forEach { sn ->
                                result += SPAN_STYLE_TABLINE + sn + "⎼‖" + SPAN_END + BR
                            }
                            result += lineNotes + BR + lineText + BR + BR
                            lineNotes = ""
                            lineText = ""
                            wasBr = true
                        }
                    }
                    Markertype.SYLLABLES.value, Markertype.NOTE.value -> {
                        var txt= ""
                        var txtHtml = ""
                        var endOfWord = true
                        // Если в маркере не пустой лейбл (т.е. есть слог)
                        if (marker.label.isNotEmpty()) {
                            endOfWord = marker.label.endsWith("_") // Это конец слова если слог заканчивается подчёркиванием
                            txt = marker.label.replace("_", " "); // Заменяем подчеркивания на пробелы
                            txtHtml = marker.label.replace("_", "&nbsp;");
                            // Если был перенос строки - инициализируем новые переменны (3 пробела + слог и начало струн)
                            if (wasBr) {
                                txt = "   " + txt.uppercaseFirstLetter()
                                txtHtml = "&nbsp;&nbsp;&nbsp;" + marker.label.uppercaseFirstLetter().replace("_", "&nbsp;")
                                strings = mutableListOf("E‖⎼","B‖⎼","G‖⎼","D‖⎼","A‖⎼","e‖⎼")
                            }
                        } else {
                            // Если в маркере пустой лейбл - пробел в тексте
                            txt = " "
                            txtHtml = "&nbsp;"
                        }
                        var note = "";
                        var noteHtml = "";
                        var noteOctave = "";
                        var stringNote = mutableListOf("⎼⎼","⎼⎼","⎼⎼","⎼⎼","⎼⎼","⎼⎼") // Выделяем по 2 черты на ноту на струне
                        
                        // Если в маркере есть нота
                        if (marker.note.isNotEmpty()) {
                            // Находим ноту и октаву ноты
                            val noteParts = marker.note.split("|")
                            note = noteParts[0]
                            noteHtml = noteParts[0]
                            noteOctave = if (noteParts.size > 1) noteParts[1] else ""
                            val noteLength = note.length
                            // Находим номер струны и номер лада, подставляем их в массив stringNote
                            val sn = if (marker.locklad.toBoolean()) {
                                val (stringTxt, ladTxt) = marker.stringlad.split("|")
                                DiffGuitarStringLad(diff = 0, guitarStringLad = GuitarStringLad(string = stringTxt.toInt(), lad = ladTxt.toInt()))
                            } else {
                                stringsForAllNotes[stringsForAllNotesIndex]
                            }
                            val stringIndex = sn.guitarStringLad.string
                            val lad = "${sn.guitarStringLad.lad}${if (sn.guitarStringLad.lad < 10) "⎼" else ""}"
                            stringNote[stringIndex] = lad
                            stringsForAllNotesIndex++
                            // Находим позицию гласной буквы в слоге (0 - если гласная первая или если её нет)
                            fun String.firstVowelIndex(): Int {
                                val vovels = "♪ёуеыаоэяиюeuioaїієѣ" + "ёуеыаоэяиюeuioaїієѣ".uppercase()
                                for (i in this.indices) {
                                    if (this[i] in vovels) {
                                        return i
                                    }
                                }
                                return 0
                            }
                            val vowelPosition = txt.firstVowelIndex()
                            // Добавляем пробелы перед названием ноты (по позиции гласной)
                            note = (0 until vowelPosition).joinToString("") { " " } + note
                            noteHtml = (0 until vowelPosition).joinToString("") { "&nbsp;" } + noteHtml
                            var diff = 0
                            if (wasBr) {
                                diff = 3
                                wasBr = false
                            }
                            for (i in 0 until vowelPosition - diff + (noteLength - 1)) {
                                for (j in 0 until stringNote.size) {
                                    val newLad = "⎼" + stringNote[j]
                                    stringNote[j] = newLad
                                }
                            }
                        } else {
                            // Если в маркере нет ноты - пробел в названии ноты и одна черта на ноту в каждой струне
                            note = " "
                            noteHtml = "&nbsp;"
                            stringNote = mutableListOf("⎼","⎼","⎼","⎼","⎼","⎼")
                        }

                        lineNotes += SPAN_STYLE_NOTE + noteHtml + SPAN_END + SPAN_STYLE_OCTAVE + noteOctave + SPAN_END
                        lineText += SPAN_STYLE_TEXT + txtHtml + SPAN_END
                        if (!endOfWord) {
                            lineText += SPAN_STYLE_DEFIS + "-" + SPAN_END
                        }
                        val lengthNote = note.length + noteOctave.length;
                        val lengthText = txt.length + (if (endOfWord) 0 else 1)
                        if (lengthNote > lengthText) {
                            lineText += SPAN_STYLE_TEXT
                            for (i in 0 until lengthNote-lengthText) {
                                lineText += "&nbsp;"
                            }
                            lineText += SPAN_END
                        } else if (lengthText > lengthNote) {
                            lineNotes += SPAN_STYLE_NOTE
                            for (i in 0 until lengthText-lengthNote) {
                                lineNotes += "&nbsp;"
                                for (j in 0 until stringNote.size) {
                                val newLad = stringNote[j] + '⎼'
                                stringNote[j] = newLad
                            }
                            }
                            lineNotes += SPAN_END
                        }
                        for (j in 0 until strings.size) {
                            val sn = strings[j] + stringNote[j]
                            strings[j] = sn
                        }

                    }
                    else -> {}
                }
            }
            resultArray.add(result)
        }
        return resultArray.joinToString("""<br><hr style="border: 2px solid blue;"><br>""")
    }

    fun getNotesBody(): String {
        val resultArray: MutableList<String> = mutableListOf()
        for (voice in 0 until countVoices) {

            val stringsForAllNotesArray = getStringsForAllNotesInSong(voice)
            if (stringsForAllNotesArray.isEmpty()) break
            val indexStringsForAllNotes = stringsForAllNotesArray.size.coerceAtMost(indexTabsVariant)

            val BR = "\n"
            val stringsForAllNotes = stringsForAllNotesArray[indexStringsForAllNotes]
            var stringsForAllNotesIndex = 0
            val markers = this.sourceMarkersList[voice]
            var wasBr = true
            var result = ""
            var lineNotes = ""
            var lineText = ""
            var strings = mutableListOf("E‖⎼","B‖⎼","G‖⎼","D‖⎼","A‖⎼","e‖⎼")
            markers.forEach { marker ->
                when (marker.markertype) {
                    Markertype.ENDOFLINE.value,
                    Markertype.EOL_NOTE.value,
                    Markertype.NEWLINE_NOTE.value,
                    Markertype.NEWLINE.value -> {
                        if (!wasBr) {
                            strings.forEach { sn ->
                                result += sn + "⎼‖" + BR
                            }
                            result += lineNotes + BR + lineText + BR + BR
                            lineNotes = ""
                            lineText = ""
                            wasBr = true
                        }
                    }
                    Markertype.SYLLABLES.value, Markertype.NOTE.value -> {
                        var txt= ""
                        var txtHtml = ""
                        var endOfWord = true
                        // Если в маркере не пустой лейбл (т.е. есть слог)
                        if (marker.label.isNotEmpty()) {
                            endOfWord = marker.label.endsWith("_") // Это конец слова если слог заканчивается подчёркиванием
                            txt = marker.label.replace("_", " "); // Заменяем подчеркивания на пробелы
                            txtHtml = marker.label.replace("_", " ");
                            // Если был перенос строки - инициализируем новые переменны (3 пробела + слог и начало струн)
                            if (wasBr) {
                                txt = "   " + txt.uppercaseFirstLetter()
                                txtHtml = "   " + marker.label.uppercaseFirstLetter().replace("_", " ")
                                strings = mutableListOf("E‖⎼","B‖⎼","G‖⎼","D‖⎼","A‖⎼","e‖⎼")
                            }
                        } else {
                            // Если в маркере пустой лейбл - пробел в тексте
                            txt = " "
                            txtHtml = " "
                        }
                        var note = "";
                        var noteHtml = "";
                        var noteOctave = "";
                        var stringNote = mutableListOf("⎼⎼","⎼⎼","⎼⎼","⎼⎼","⎼⎼","⎼⎼") // Выделяем по 2 черты на ноту на струне

                        // Если в маркере есть нота
                        if (marker.note.isNotEmpty()) {
                            // Находим ноту и октаву ноты
                            val noteParts = marker.note.split("|")
                            note = noteParts[0]
                            noteHtml = noteParts[0]
                            noteOctave = if (noteParts.size > 1) noteParts[1] else ""
                            val noteLength = note.length
                            // Находим номер струны и номер лада, подставляем их в массив stringNote
                            val sn = if (marker.locklad.toBoolean()) {
                                val (stringTxt, ladTxt) = marker.stringlad.split("|")
                                DiffGuitarStringLad(diff = 0, guitarStringLad = GuitarStringLad(string = stringTxt.toInt(), lad = ladTxt.toInt()))
                            } else {
                                stringsForAllNotes[stringsForAllNotesIndex]
                            }
                            val stringIndex = sn.guitarStringLad.string
                            val lad = "${sn.guitarStringLad.lad}${if (sn.guitarStringLad.lad < 10) "⎼" else ""}"
                            stringNote[stringIndex] = lad
                            stringsForAllNotesIndex++
                            // Находим позицию гласной буквы в слоге (0 - если гласная первая или если её нет)
                            val vowelPosition = txt.getFirstVowelIndex()
                            // Добавляем пробелы перед названием ноты (по позиции гласной)
                            note = (0 until vowelPosition).joinToString("") { " " } + note
                            noteHtml = (0 until vowelPosition).joinToString("") { " " } + noteHtml
                            var diff = 0
                            if (wasBr) {
                                diff = 3
                                wasBr = false
                            }
                            for (i in 0 until vowelPosition - diff + (noteLength - 1)) {
                                for (j in 0 until stringNote.size) {
                                    val newLad = "⎼" + stringNote[j]
                                    stringNote[j] = newLad
                                }
                            }
                        } else {
                            // Если в маркере нет ноты - пробел в названии ноты и одна черта на ноту в каждой струне
                            note = " "
                            noteHtml = " "
                            stringNote = mutableListOf("⎼","⎼","⎼","⎼","⎼","⎼")
                        }

                        lineNotes += noteHtml + noteOctave
                        lineText += txtHtml
                        if (!endOfWord) {
                            lineText += "-"
                        }
                        val lengthNote = note.length + noteOctave.length;
                        val lengthText = txt.length + (if (endOfWord) 0 else 1)
                        if (lengthNote > lengthText) {
                            for (i in 0 until lengthNote-lengthText) {
                                lineText += " "
                            }
                        } else if (lengthText > lengthNote) {
                            for (i in 0 until lengthText-lengthNote) {
                                lineNotes += " "
                                for (j in 0 until stringNote.size) {
                                    val newLad = stringNote[j] + '⎼'
                                    stringNote[j] = newLad
                                }
                            }
                        }
                        for (j in 0 until strings.size) {
                            val sn = strings[j] + stringNote[j]
                            strings[j] = sn
                        }

                    }
                    else -> {}
                }
            }
            resultArray.add(result)
        }
        return resultArray.joinToString("\n------------------------------------------------------\n\n")
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
                    Markertype.SETTING.value -> {
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
                    Markertype.ENDOFLINE.value, Markertype.NEWLINE.value -> {
                        result.append("<br>")
                        wasBr = true
//                    result.append("""<span style="font-size: 0">Источник: sm-karaoke.ru</span>""")
                    }
                    Markertype.SYLLABLES.value -> {
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
                    Markertype.SETTING.value -> {
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
                    Markertype.ENDOFLINE.value, Markertype.NEWLINE.value -> {
                        result.append("\n")
                        wasBr = true
                    }
                    Markertype.SYLLABLES.value -> {
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
    var timecodeCounter = 0
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
            val timecode = convertMillisecondsToDzenTimecode((marker.time * 1000 + 8000).toLong())
            when (marker.markertype) {
                Markertype.SETTING.value -> {
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
                Markertype.ENDOFLINE.value, Markertype.NEWLINE.value -> {
                    result.append("\n")
                    wasBr = true
                }
                Markertype.SYLLABLES.value -> {
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
                    Markertype.SYLLABLES.value -> {
                        var txt = marker.label.replace("_", " ")
                        if (wasBr) {
                            txt = txt.uppercaseFirstLetter()
                            wasBr = false
                        }
                        result.append(txt)
                    }
                    Markertype.ENDOFLINE.value, Markertype.NEWLINE.value -> {
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

    fun getTextForSponsrPictureDescription(): String {
        val resultList: MutableList<String> = mutableListOf()
        resultList.add(SongVersion.KARAOKE.name)
        resultList.add(SongVersion.LYRICS.name)
        if (hasMelody) resultList.add(SongVersion.TABS.name)
        if (hasChords) resultList.add(SongVersion.CHORDS.name)
        return resultList.joinToString(" • ")
    }

    fun getTextForSponsrTeaser(): String {

        val txtVersions = if (hasMelody && hasChords) {
            "Видео эксклюзивных версий с табулаторой нот и аккордами для гитары. "
        } else if (hasMelody && !hasChords) {
            "Видео эксклюзивной версии с табулаторой нот. "
        } else if (!hasMelody && hasChords) {
            "Видео эксклюзивной версий с аккордами для гитары. "
        } else {
            ""
        }
        return "Петь караоке композицию «${songName}» группы «${author}» онлайн со словами. Текст песни с альбома «${album}» ${year} года под оригинальный аудиотрек, под минус, под плюс. ${txtVersions}" +
                "Любимые русские хиты группы «${author}» с идеально синхронизированным текстом, минусовкой и плюсовкой в видео формате караоке у вас дома!"
    }

    fun getDescriptionHeader(songVersion: SongVersion, maxSymbols: Int = 0): String {

        return "${songName.censored()} ★♫★ ${author} ★♫★ ${songVersion.text} ★♫★ ${songVersion.textForDescription}".cutByWords(maxLength = maxSymbols)

    }

    fun getDescription(songVersion: SongVersion): String {

        return getDescriptionHeader(songVersion = songVersion) + "\n" +
                getDescriptionWOHeaderWOTimecodes(songVersion = songVersion)

    }

    fun getDescriptionVk(songVersion: SongVersion): String {

        return getDescriptionVkHeader(songVersion = songVersion) + "\n" +
                getDescriptionWOHeaderWithTimecodes(songVersion = songVersion)

    }

    fun getTextBoostyHead(): String {
        return "${songName.censored()} ★♫★ ${author}"
    }

    fun getTextBoostyFilesHead(): String {
        return "[ФАЙЛЫ] ${songName.censored()} ★♫★ ${author}"
    }

    fun getTextForDescriptionHeader(songVersion: SongVersion? = null): String {
        return "${linkSM} ⇐ Страница песни на официальном сайте проекта\n\n" +
                (if (songVersion == null) "" else "Версия: ${songVersion.text} (${songVersion.textForDescription})\n") +
                "Композиция: ${songName}\n" +
                "Исполнитель: ${author}\n" +
                "Альбом: ${album}\n" +
                "Год: ${year}\n" +
                "Темп: ${bpm} bpm\n" +
                "Тональность: ${key}" +
                "\n\n"
    }

    fun getTextForDescriptionFooter(): String {
        return "\n\n"+
                "Официальный сайт проекта: https://sm-karaoke.ru\n" +
                "Поддержать проект на Sponsr: https://sponsr.ru/smkaraoke\n" +
                "Поддержать проект на Boosty: https://boosty.to/svoemesto\n" +
                "Группа ВКонтакте: https://vk.com/svoemestokaraoke\n" +
                "Канал Telegram: https://t.me/svoemestokaraoke\n" +
                "Канал Дзен: https://dzen.ru/svoemesto\n" +
                "Канал Платформа: https://plvideo.ru/channel/@sm-karaoke\n" +
                "${songName.hashtag()} ${author.hashtag()} ${"karaoke".hashtag()} ${"караоке".hashtag()}\n"
    }

    fun getTextBoostyBody(): String {
        return  getTextForDescriptionHeader() +
                "\n\n"+
                getTextForDescription() +
                "\n\n"
    }

    fun getTextSponsrBody(): String {
        return  getTextForSponsrTeaser() +
                "\n\n"+
                getTextForDescriptionHeader() +
                "\n\n"+
                getTextForDescription() +
                "\n\n"+
                author
    }

    fun getDescriptionVkHeader(songVersion: SongVersion, maxSymbols: Int = 0): String {

        return "${songName.censored()} ★♫★ ${author} ★♫★ ${songVersion.text} ★♫★ ${songVersion.textForDescription}".cutByWords(maxLength = maxSymbols)

    }

    fun getDescriptionWOHeaderWOTimecodes(songVersion: SongVersion, maxSymbols: Int = 0): String {

        val txtStart = getTextForDescriptionHeader(songVersion = songVersion)
        val txtEnd = getTextForDescriptionFooter()
        val txtDescription = getTextForDescription(maxSymbols - txtStart.length - txtEnd.length)

        return txtStart + txtDescription + txtEnd

    }

    fun getDescriptionWithHeaderWOTimecodes(songVersion: SongVersion, maxSymbols: Int = 0): String {

        val txtStart = getDescriptionHeader(songVersion = songVersion) + "\n\n" + getTextForDescriptionHeader(songVersion = songVersion)
        val txtEnd = getTextForDescriptionFooter()
        val txtDescription = when(songVersion) {
            SongVersion.TABS -> {
                getNotesForDescription(maxSymbols - txtStart.length - txtEnd.length)
            }
            else -> {
                getTextForDescription(maxSymbols - txtStart.length - txtEnd.length)
            }
        }

        return txtStart + txtDescription + txtEnd

    }

    fun getDescriptionWOHeaderWithTimecodes(songVersion: SongVersion, maxSymbols: Int = 0, maxTimeCodes: Int? = null): String {

        val txtStart = getTextForDescriptionHeader(songVersion = songVersion)
        val txtEnd = getTextForDescriptionFooter()
        val txtDescription = getTextForDescriptionWithTimecodes(maxSymbols - txtStart.length - txtEnd.length, maxTimeCodes)

        return txtStart + txtDescription + txtEnd

    }

    fun getVKGroupDescription(maxSymbols: Int = 0): String {

        return  "${songName.censored()} ★♫★ ${author}" + "\n\n" +
                getTextForDescriptionHeader() +
                getTextForDescription()
    }

    fun getChordDescription(songVersion: SongVersion): String {

        if (songVersion == SongVersion.CHORDS) {
            val capo = 0
            if (capo == 0) {
                return  "Темп: ${bpm} bpm\n" +
                        "Тональность: ${key}"
            } else {
                return  "Темп: ${bpm} bpm\n" +
                        "Оригинальная тональность: ${key}\n" +
                        "Аккорды и аппликатуры: ${getNewTone(key, capo)}\n" +
                        "Каподастр на ${capo}-м ладу"
            }
        } else {
            return ""
        }

    }


    fun getTextForDescription(maxSymbols: Int = 0): String {
        var result = getTextBody()

        while (maxSymbols > 0 && result.length > maxSymbols) {
            val lst = result.split("\n").toMutableList()
            lst.removeLast()
            result = lst.joinToString("\n")
        }

        return result
    }

    fun getNotesForDescription(maxSymbols: Int = 0): String {
        var result = getNotesBody()

        while (maxSymbols > 0 && result.length > maxSymbols) {
            val lst = result.split("\n").toMutableList()
            lst.removeLast()
            result = lst.joinToString("\n")
        }

        return result
    }

    fun getTextForDescriptionWithTimecodes(maxSymbols: Int = 0, maxTimeCodes: Int? = null): String {
        var result = getTextBodyWithTimecodes(maxTimeCodes)

        while (maxSymbols > 0 && result.length > maxSymbols) {
            val lst = result.split("\n").toMutableList()
            lst.removeLast()
            result = lst.joinToString("\n")
        }

        return result

    }

    fun getWords(): List<String> {
        return getText().getWords()
    }

    fun updateMarkersFromSourceText(voice: Int) {

        val listMarkers = getSourceMarkers(voice)
        val listSyllables = getSourceSyllables(voice)
        var indexSyllable = 0
        listMarkers.forEach { marker ->
            if (marker.markertype == Markertype.SYLLABLES.value) {
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
                        markertype = Markertype.SETTING.value
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
                            markertype = Markertype.SETTING.value
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
                                markertype = Markertype.SYLLABLES.value
                            )
                        )
                    } else {
                        markers.add(
                            SourceMarker(
                                time = convertFramesToMilliseconds(currSfe.startFrame).toDouble() / 1000,
                                label = currSfe.text.replace("//", "").replace("\\", ""),
                                color = "#D2691E",
                                position = "bottom",
                                markertype = Markertype.SYLLABLES.value
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
                                markertype = Markertype.ENDOFLINE.value
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
            if (marker.markertype == Markertype.ENDOFLINE.value) result += "\n"
            if (marker.markertype == Markertype.SYLLABLES.value) result += marker.label
        }
        return result
    }




    fun convertMarkersToSrt(voice: Int): String {
        val notSkippedTypes = listOf(Markertype.ENDOFLINE.value, Markertype.SYLLABLES.value, Markertype.SETTING.value)
        val listMarkers = getSourceMarkers(voice).filter { it.markertype in notSkippedTypes}
        var perviousMarkerIsEndOfLine = true
        var numberSrt = 0
        var result = ""

        listMarkers.forEachIndexed { index, sourceMarker ->

            if (sourceMarker.markertype != Markertype.ENDOFLINE.value) numberSrt++
            val nextMarker = if (index == listMarkers.size - 1) null else listMarkers[index + 1]
            val srtNumber =  numberSrt.toString()
            val srtTimeStart = convertMillisecondsToTimecode((sourceMarker.time * 1000).toLong()).replace(".", ",")
            val srtTimeEnd = if (nextMarker == null) {
                convertMillisecondsToTimecode(((sourceMarker.time + 1.0) * 1000).toLong()).replace(".", ",")
            } else {
                convertMillisecondsToTimecode((nextMarker.time * 1000).toLong()).replace(".", ",")
            }

            val srtText = if (sourceMarker.markertype == Markertype.SYLLABLES.value) {
                 "${if (perviousMarkerIsEndOfLine) "//${sourceMarker.label.uppercaseFirstLetter()}" else sourceMarker.label}${if (nextMarker != null && nextMarker.markertype == Markertype.ENDOFLINE.value) "\\\\" else ""}\n\n"
            } else if (sourceMarker.markertype == Markertype.SETTING.value) {
                if (sourceMarker.label == "END") {
                    "//\\\\\n\n"
                } else {
                    "[SETTING]|${sourceMarker.label}\n\n"
                }
            } else ""

            val srt = "$srtNumber\n$srtTimeStart --> $srtTimeEnd\n$srtText"
            if (sourceMarker.markertype != Markertype.ENDOFLINE.value) result += srt
            if (sourceMarker.markertype == Markertype.ENDOFLINE.value) {
                perviousMarkerIsEndOfLine = true
            } else if (sourceMarker.markertype != Markertype.SETTING.value) {
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

            val savedSettings = loadFromDbById(id,database)
            
            // При сохранении проверяем и меняем если надо номера версий на площадках. Если на площадке 0 и было изменение id - обновляем
            if (savedSettings !== null) {
                if (
                    savedSettings.idBoosty.isBlank() &&
                    this.idBoosty.isNotBlank() &&
                    savedSettings.versionBoosty == 0
                    ) {
                    fields[SettingField.VERSION_BOOSTY] = resultVersion.toString()
                }
                if (savedSettings.idBoostyFiles.isBlank() && this.idBoostyFiles.isNotBlank() && savedSettings.versionBoostyFiles == 0) fields[SettingField.VERSION_BOOSTY_FILES] = resultVersion.toString()
                if (savedSettings.idSponsr.isBlank() && this.idSponsr.isNotBlank() && savedSettings.versionSponsr == 0) fields[SettingField.VERSION_SPONSR] = resultVersion.toString()
                if (savedSettings.idDzenLyrics.isBlank() && this.idDzenLyrics.isNotBlank() && savedSettings.versionDzenLyrics == 0) fields[SettingField.VERSION_DZEN_LYRICS] = resultVersion.toString()
                if (savedSettings.idDzenKaraoke.isBlank() && this.idDzenKaraoke.isNotBlank() && savedSettings.versionDzenKaraoke == 0) fields[SettingField.VERSION_DZEN_KARAOKE] = resultVersion.toString()
                if (savedSettings.idDzenMelody.isBlank() && this.idDzenMelody.isNotBlank() && savedSettings.versionDzenMelody == 0) fields[SettingField.VERSION_DZEN_MELODY] = resultVersion.toString()
                if (savedSettings.idDzenChords.isBlank() && this.idDzenChords.isNotBlank() && savedSettings.versionDzenChords == 0) fields[SettingField.VERSION_DZEN_CHORDS] = resultVersion.toString()
                if (savedSettings.idVkLyrics.isBlank() && this.idVkLyrics.isNotBlank() && savedSettings.versionVkLyrics == 0) fields[SettingField.VERSION_VK_LYRICS] = resultVersion.toString()
                if (savedSettings.idVkKaraoke.isBlank() && this.idVkKaraoke.isNotBlank() && savedSettings.versionVkKaraoke == 0) fields[SettingField.VERSION_VK_KARAOKE] = resultVersion.toString()
                if (savedSettings.idVkMelody.isBlank() && this.idVkMelody.isNotBlank() && savedSettings.versionVkMelody == 0) fields[SettingField.VERSION_VK_MELODY] = resultVersion.toString()
                if (savedSettings.idVkChords.isBlank() && this.idVkChords.isNotBlank() && savedSettings.versionVkChords == 0) fields[SettingField.VERSION_VK_CHORDS] = resultVersion.toString()
                if ((savedSettings.idTelegramLyrics.isBlank() || savedSettings.idTelegramLyrics == "-") && this.idTelegramLyrics.isNotBlank() && this.idTelegramLyrics != "-" && savedSettings.versionTelegramLyrics == 0) fields[SettingField.VERSION_TELEGRAM_LYRICS] = resultVersion.toString()
                if (
                    (savedSettings.idTelegramKaraoke.isBlank() || savedSettings.idTelegramKaraoke == "-") &&
                    this.idTelegramKaraoke.isNotBlank() &&
                    this.idTelegramKaraoke != "-" &&
                    savedSettings.versionTelegramKaraoke == 0
                    ) {
                    fields[SettingField.VERSION_TELEGRAM_KARAOKE] = resultVersion.toString()
                }
                if ((savedSettings.idTelegramMelody.isBlank() || savedSettings.idTelegramMelody == "-") && this.idTelegramMelody.isNotBlank() && this.idTelegramMelody != "-" && savedSettings.versionTelegramMelody == 0) fields[SettingField.VERSION_TELEGRAM_MELODY] = resultVersion.toString()
                if ((savedSettings.idTelegramChords.isBlank() || savedSettings.idTelegramChords == "-") && this.idTelegramChords.isNotBlank() && this.idTelegramChords != "-" && savedSettings.versionTelegramChords == 0) fields[SettingField.VERSION_TELEGRAM_CHORDS] = resultVersion.toString()
                if (savedSettings.idPlLyrics.isBlank() && this.idPlLyrics.isNotBlank() && savedSettings.versionPlLyrics == 0) fields[SettingField.VERSION_PL_LYRICS] = resultVersion.toString()
                if (savedSettings.idPlKaraoke.isBlank() && this.idPlKaraoke.isNotBlank() && savedSettings.versionPlKaraoke == 0) fields[SettingField.VERSION_PL_KARAOKE] = resultVersion.toString()
                if (savedSettings.idPlMelody.isBlank() && this.idPlMelody.isNotBlank() && savedSettings.versionPlMelody == 0) fields[SettingField.VERSION_PL_MELODY] = resultVersion.toString()
                if (savedSettings.idPlChords.isBlank() && this.idPlChords.isNotBlank() && savedSettings.versionPlChords == 0) fields[SettingField.VERSION_PL_CHORDS] = resultVersion.toString()
            }

            val diff = getDiff(this, savedSettings)
//            println("diff = $diff")
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
                if (connection == null) {
                    println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                    return
                }
                val ps = connection.prepareStatement(sql)

                var index = 1
                diff.filter{ it.recordDiffRealField }.forEach {
                    try {
                        if (it.recordDiffValueNew is Long) {
                            ps.setLong(index, it.recordDiffValueNew.toLong())
                        } else if (it.recordDiffValueNew is Int) {
                            ps.setInt(index, it.recordDiffValueNew.toInt())
                        } else {
                            ps.setString(index, it.recordDiffValueNew.toString())
                        }
                    } catch (e: Exception) {
                        val errorMessage = "Не удалось сохранить запись в БД. Поле «${it.recordDiffName}» имеет значение «${it.recordDiffValueNew}», несовместимое с форматом данных этого поля в БД. Оригинальный текст ошибки: «${e.message}»"
                        println(errorMessage)
                    }
                    index++
                }
                ps.setLong(index, id)
                try {
                    ps.executeUpdate()
                } catch (e: Exception) {
                    val errorMessage = "Не удалось сохранить запись в БД. Оригинальный текст ошибки: «${e.message}»"
                    println(errorMessage)
                }
                ps.close()

//                println(messageRecordChange.toString())

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

//                    println(messageRecordChangeNew.toString())
                    try {
                        SNS.send(messageRecordChangeNew)
                    } catch (e: Exception) {
                        println(e.message)
                    }
                }

            }

            if (savedSettings != null) renameFilesIfDiff(this, savedSettings)

            if (Karaoke.autoUpdateRemoteSettings) {
                val (countCreate, countUpdate, countDelete) = updateRemoteSettingFromLocalDatabase(id)
                val body = if (countCreate + countUpdate + countDelete == 0) "Изменения не требуются" else listOf(Pair(countCreate > 0, "создано записей: $countCreate"), Pair(countUpdate > 0, "обновлено записей: $countUpdate"), Pair(countDelete > 0, "удалено записей: $countDelete")).filter { it.first }.map{ it.second }.joinToString(", ").uppercaseFirstLetter()

                if (!(countUpdate == 1 && diff.any { it.recordDiffName.startsWith("status_process_")  })) {
                    SNS.send(SseNotification.message(
                        Message(
                            type = "info",
                            head = "Автоматическое обновление БД",
                            body = body
                        )
                    ))
                    println("Автоматическое обновление записей серверной БД - ${body}")
                }

            }

        }

    }

    fun doSymlink(prior: Int = -1) {
        KaraokeProcess.createProcess(this, KaraokeProcessTypes.SYMLINK, true, prior)
    }
    fun doSmartCopy(
        prior: Int = -1,
        scVersion: SongVersion,
        scResolution: String,
        scCreateSubfoldersAuthors: Boolean,
        scRenameTemplate: String,
        scPath: String
    ) {
        val context: MutableMap<String, Any> = mutableMapOf()


        var doSkip = false
        var deleteOldBeforeCopy = false
        var sourceFilePathAndName = ""

        if (scResolution == "1080p") {

            when (scVersion) {
                SongVersion.KARAOKE -> {
                    sourceFilePathAndName = this.pathToFileKaraoke
                }
                SongVersion.LYRICS -> {
                    sourceFilePathAndName = this.pathToFileLyrics
                }
                SongVersion.CHORDS -> {
                    sourceFilePathAndName = this.pathToFileChords
                }
                SongVersion.TABS -> {
                    sourceFilePathAndName = this.pathToFileMelody
                }
                else -> {
                    doSkip = true
                }
            }
            if (sourceFilePathAndName != "" && !File(sourceFilePathAndName).exists()) doSkip = true
        } else {

            when (scVersion) {
                SongVersion.KARAOKE -> {
                    sourceFilePathAndName = this.pathToFile720Karaoke
                }
                SongVersion.LYRICS -> {
                    sourceFilePathAndName = this.pathToFile720Lyrics
                }
                SongVersion.CHORDS -> {
                    sourceFilePathAndName = this.pathToFile720Chords
                }
                SongVersion.TABS -> {
                    sourceFilePathAndName = this.pathToFile720Melody
                }
                else -> {
                    doSkip = true
                }
            }
            if (sourceFilePathAndName != "" && !File(sourceFilePathAndName).exists()) {
                println("Пропускаем копирование, т.к. отсутствует файл $sourceFilePathAndName")
                doSkip = true
            }

        }

        // Если исходный файл существует - ничего не помешает копированию
        if (!doSkip) {

            // Задаём папку назначения в зависимости он необходимости создавать сабфолдеры для авторов
            val destinationFileFolder = if (scCreateSubfoldersAuthors) {
                "$scPath/${this.author.rightFileNameSymbols()}"
            } else {
                scPath
            }

            // Проверим наличие папки назначения и если её нет - создадим
            if (!File(destinationFileFolder).exists()) {
                Files.createDirectories(Path(destinationFileFolder))
                runCommand(listOf("chmod", "777", destinationFileFolder))
            }

            // Найдём имя и путь конечного файла, принимая во внимание шаблон переименования

            val sourceFileFolder = Path(sourceFilePathAndName).parent.toString()
            val sourceFileName = Path(sourceFilePathAndName).fileName.toString()

            val destinationFileName = if (scRenameTemplate != "") {
                var fileName = scRenameTemplate
                    .replace("{author}", this.author)
                    .replace("{name}", this.songName)
                    .replace("{year}", this.year.toString())
                    .replace("{track}", this.track.toString())
                    .replace("{album}", this.album)
                    .replace("{key}", this.key.replace(" major", "").replace(" minor","m"))
                fileName += scVersion.suffix
                if (scResolution == "720p") fileName += " 720p"
                fileName += ".mp4"

                fileName
            } else {
                sourceFileName
            }

            val destinationFilePathAndName = "$destinationFileFolder/$destinationFileName"

            // Проверим наличие файла назначения. Если есть - сверим размеры файлов.
            // Если размеры не совпадают - пометим старый файл назначения на удаления перед копированием

            if (File(destinationFilePathAndName).exists()) {
                if (File(destinationFilePathAndName).length() != File(sourceFilePathAndName).length()) {
                    println("Исходный файл $destinationFilePathAndName имеет размер ${File(destinationFilePathAndName).length()}, результирующий файл $sourceFilePathAndName имеет размер ${File(sourceFilePathAndName).length()}, поэтому удаляем старый файл перед копированием.")
                    deleteOldBeforeCopy = true
                } else {
                    println("Проускаем копирование, т.к. исходный файл $destinationFilePathAndName и результирующий файл $sourceFilePathAndName имеют одинаковый размер ${File(destinationFilePathAndName).length()}")
                    doSkip = true
                }
            }

            if (!doSkip) {
                val args: MutableList<List<String>> = mutableListOf()
                val argsDescription: MutableList<String> = mutableListOf()

                if (deleteOldBeforeCopy) {
                    args.add(listOf("rm", destinationFilePathAndName))
                    argsDescription.add("Delete old file")
                }

                args.add(listOf("cp", sourceFilePathAndName, destinationFilePathAndName))
                argsDescription.add("Copy new file")
                args.add(listOf("chmod", "666", destinationFilePathAndName))
                argsDescription.add("chmod new file")

                context["args"] = args
                context["argsDescription"] = argsDescription
                context["typesText"] = "${KaraokeProcessTypes.SMARTCOPY.name}_${scVersion.name}_$scResolution"

                KaraokeProcess.createProcess(
                    settings = this,
                    action = KaraokeProcessTypes.SMARTCOPY,
                    doWait = true,
                    prior = prior,
                    context = context
                )
            }

        }


    }

    fun doMP3Karaoke(prior: Int = -1) {
        KaraokeProcess.createProcess(this, KaraokeProcessTypes.FF_MP3_KAR, true, prior)
    }
    fun doMP3Lyrics(prior: Int = -1) {
        KaraokeProcess.createProcess(this, KaraokeProcessTypes.FF_MP3_LYR, true, prior)
    }
    fun deleteFromDb(withFiles: Boolean = true, sync: Boolean = false) {
        if (withFiles) {
            if (File(fileAbsolutePath).exists()) File(fileAbsolutePath).delete()
            if (File(fileSettingsAbsolutePath).exists()) File(fileSettingsAbsolutePath).deleteOnExit()
            if (File(vocalsNameFlac).exists()) File(vocalsNameFlac).deleteOnExit()
            if (File(newNoStemNameFlac).exists()) File(newNoStemNameFlac).deleteOnExit()
        }

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
            return
        }
        val sql = "DELETE FROM tbl_settings${if (sync) "_sync" else ""} WHERE id = ?"
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
        if (File(rootFolder).exists()) {
            var txt = ""
            SettingField.values().forEach { settingField ->
                if (fields.contains(settingField)) txt += "${settingField.name}=${fields[settingField]}\n"
            }
            val pathToFile = "$rootFolder/$rightSettingFileName.settings".rightFileName()
            File(pathToFile).writeText(txt)
            runCommand(listOf("chmod", "666", pathToFile))
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

    fun createKaraoke(
        createLyrics: Boolean = true,
        createKaraoke: Boolean = true,
        createChords: Boolean = false,
        createMelody: Boolean = false,
//        createLyricsVk: Boolean = false,
//        createKaraokeVk: Boolean = false,
//        createChordsVk: Boolean = false,
//        createMelodyVk: Boolean = false,
        ) {

        val permissions = PosixFilePermissions.fromString("rwxr-x---")

        val pathToMltLyrics = getOutputFilename(SongOutputFile.MLT, SongVersion.LYRICS)
        val pathToMltKaraoke = getOutputFilename(SongOutputFile.MLT, SongVersion.KARAOKE)
        val pathToMltChords = getOutputFilename(SongOutputFile.MLT, SongVersion.CHORDS)
        val pathToMltTABS = getOutputFilename(SongOutputFile.MLT, SongVersion.TABS)

        val txtLyric = "echo \"$pathToMltLyrics\"\nmelt -progress \"$pathToMltLyrics\"\n\n"
        val txtKaraoke = "echo \"$pathToMltKaraoke\"\nmelt -progress \"$pathToMltKaraoke\"\n\n"
        val txtChords = "echo \"$pathToMltChords\"\nmelt -progress \"$pathToMltChords\"\n\n"
        val txtMelody = "echo \"$pathToMltTABS\"\nmelt -progress \"$pathToMltTABS\"\n\n"

        val songTxtAll = "$txtLyric$txtKaraoke${if (hasChords) txtChords else ""}"
        val songTxtWOLyrics = "$txtKaraoke${if (hasChords) txtChords else ""}"

        val fileNameRunLyrics = getOutputFilename(SongOutputFile.RUN, SongVersion.LYRICS)
        val fileRunLyrics = File(fileNameRunLyrics)
        Files.createDirectories(Path(fileRunLyrics.parent))
        runCommand(listOf("chmod", "777", fileRunLyrics.parent))

        fileRunLyrics.writeText(txtLyric)
        runCommand(listOf("chmod", "777", fileNameRunLyrics))

        val fileNameRunKaraoke = getOutputFilename(SongOutputFile.RUN, SongVersion.KARAOKE)
        val fileRunKaraoke = File(fileNameRunKaraoke)
        fileRunKaraoke.writeText(txtKaraoke)
        runCommand(listOf("chmod", "777", fileNameRunKaraoke))

        if (createChords && hasChords) {
            val fileNameRunChords = getOutputFilename(SongOutputFile.RUN, SongVersion.CHORDS)
            val fileRunChords = File(fileNameRunChords)
            fileRunChords.writeText(txtChords)
            runCommand(listOf("chmod", "777", fileNameRunChords))
        }

        if (createMelody && hasMelody) {
            val fileNameRunTabs = getOutputFilename(SongOutputFile.RUN, SongVersion.TABS)
            val fileRunTabs = File(fileNameRunTabs)
            fileRunTabs.writeText(txtMelody)
            runCommand(listOf("chmod", "777", fileNameRunTabs))
        }

        val fileNameRunAllLyrics = getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALL]")
        val fileRunAllLyrics = File(fileNameRunAllLyrics)
        fileRunAllLyrics.writeText(songTxtAll)
        runCommand(listOf("chmod", "777", fileNameRunAllLyrics))

        val fileNameRunAllwoLyrics = getOutputFilename(SongOutputFile.RUNALL, SongVersion.LYRICS).replace("[lyrics]","[ALLwoLYRICS]")
        val fileRunAllwoLyrics = File(fileNameRunAllwoLyrics)
        fileRunAllwoLyrics.writeText(songTxtWOLyrics)
        runCommand(listOf("chmod", "777", fileNameRunAllwoLyrics))

        if (createLyrics) {
            createKaraoke(this, SongVersion.LYRICS)
//            if (!createLyricsVk && getSongDurationVideoMs() < 61_100) createKaraoke(this, SongVersion.LYRICSVK)
        }
        if (createKaraoke) {
            createKaraoke(this, SongVersion.KARAOKE)
//            if (!createKaraokeVk && getSongDurationVideoMs() < 61_100) createKaraoke(this, SongVersion.KARAOKEVK)
        }
        if (createChords) {
            createKaraoke(this, SongVersion.CHORDS)
//            if (!createChordsVk && getSongDurationVideoMs() < 61_100) createKaraoke(this, SongVersion.CHORDSVK)
        }
        if (createMelody) {
            createKaraoke(this, SongVersion.TABS)
//            if (!createMelodyVk && getSongDurationVideoMs() < 61_100) createKaraoke(this, SongVersion.TABSVK)
        }
//        if (createLyricsVk) createKaraoke(this, SongVersion.LYRICSVK)
//        if (createKaraokeVk) createKaraoke(this, SongVersion.KARAOKEVK)
//        if (createChordsVk) createKaraoke(this, SongVersion.CHORDSVK)
//        if (createMelodyVk) createKaraoke(this, SongVersion.TABSVK)

        if (idStatus < 3) {
            fields[SettingField.ID_STATUS] = "3"
        }
        fields[SettingField.RESULT_VERSION] = CURRENT_RESULT_VERSION.toString()
        saveToDb()

    }

    fun getSqlToInsert(sync: Boolean = false): String {
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
        fieldsValues.add(Pair("version_boosty", settings.versionBoosty))
        fieldsValues.add(Pair("id_boosty_files", settings.idBoostyFiles))
        fieldsValues.add(Pair("version_boosty_files", settings.versionBoostyFiles))
        fieldsValues.add(Pair("id_sponsr", settings.idSponsr))
        fieldsValues.add(Pair("version_sponsr", settings.versionSponsr))
        fieldsValues.add(Pair("index_tabs_variant", settings.indexTabsVariant))
        fieldsValues.add(Pair("id_vk", settings.idVk))
        fieldsValues.add(Pair("id_dzen_lyrics", settings.idDzenLyrics))
        fieldsValues.add(Pair("id_dzen_karaoke", settings.idDzenKaraoke))
        fieldsValues.add(Pair("id_dzen_chords", settings.idDzenChords))
        fieldsValues.add(Pair("id_dzen_melody", settings.idDzenMelody))
        fieldsValues.add(Pair("version_dzen_lyrics", settings.versionDzenLyrics))
        fieldsValues.add(Pair("version_dzen_karaoke", settings.versionDzenKaraoke))
        fieldsValues.add(Pair("version_dzen_chords", settings.versionDzenChords))
        fieldsValues.add(Pair("version_dzen_melody", settings.versionDzenMelody))
        fieldsValues.add(Pair("id_vk_lyrics", settings.idVkLyrics))
        fieldsValues.add(Pair("id_vk_karaoke", settings.idVkKaraoke))
        fieldsValues.add(Pair("id_vk_chords", settings.idVkChords))
        fieldsValues.add(Pair("id_vk_melody", settings.idVkMelody))
        fieldsValues.add(Pair("version_vk_lyrics", settings.versionVkLyrics))
        fieldsValues.add(Pair("version_vk_karaoke", settings.versionVkKaraoke))
        fieldsValues.add(Pair("version_vk_chords", settings.versionVkChords))
        fieldsValues.add(Pair("version_vk_melody", settings.versionVkMelody))
        fieldsValues.add(Pair("id_telegram_lyrics", settings.idTelegramLyrics))
        fieldsValues.add(Pair("id_telegram_karaoke", settings.idTelegramKaraoke))
        fieldsValues.add(Pair("id_telegram_chords", settings.idTelegramChords))
        fieldsValues.add(Pair("id_telegram_melody", settings.idTelegramMelody))
        fieldsValues.add(Pair("version_telegram_lyrics", settings.versionTelegramLyrics))
        fieldsValues.add(Pair("version_telegram_karaoke", settings.versionTelegramKaraoke))
        fieldsValues.add(Pair("version_telegram_chords", settings.versionTelegramChords))
        fieldsValues.add(Pair("version_telegram_melody", settings.versionTelegramMelody))
        fieldsValues.add(Pair("id_pl_lyrics", settings.idPlLyrics))
        fieldsValues.add(Pair("id_pl_karaoke", settings.idPlKaraoke))
        fieldsValues.add(Pair("id_pl_chords", settings.idPlChords))
        fieldsValues.add(Pair("id_pl_melody", settings.idPlMelody))
        fieldsValues.add(Pair("version_pl_lyrics", settings.versionPlLyrics))
        fieldsValues.add(Pair("version_pl_karaoke", settings.versionPlKaraoke))
        fieldsValues.add(Pair("version_pl_chords", settings.versionPlChords))
        fieldsValues.add(Pair("version_pl_melody", settings.versionPlMelody))
        fieldsValues.add(Pair("id_status", settings.idStatus))
        fieldsValues.add(Pair("source_text", settings.sourceText))
        fieldsValues.add(Pair("result_text", settings.resultText))
        fieldsValues.add(Pair("source_markers", settings.sourceMarkers))
        fieldsValues.add(Pair("status_process_lyrics", settings.statusProcessLyrics))
        fieldsValues.add(Pair("status_process_karaoke", settings.statusProcessKaraoke))
        fieldsValues.add(Pair("status_process_chords", settings.statusProcessChords))
        fieldsValues.add(Pair("status_process_melody", settings.statusProcessMelody))
        fieldsValues.add(Pair("tags", settings.tags))
        fieldsValues.add(Pair("rate", settings.rate))

       return "INSERT INTO tbl_settings${if (sync) "_sync" else ""} (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second.toString().replace("'","''")}'"}.joinToString(", ")})"

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
                idDzenKaraoke != "" &&
                idDzenLyrics != "" &&
                idPlKaraoke != "" &&
                idPlLyrics != "" &&
                idVk != "" &&
                idBoosty != "" &&
                idSponsr != ""
            ) {
                return SettingState.ALL_DONE
            } else if (idTelegramKaraoke != "-" &&
                idTelegramKaraoke != "" &&
                idTelegramLyrics != "" &&
                idVkKaraoke != "" &&
                idVkLyrics != "" &&
                idDzenKaraoke != "" &&
                idDzenLyrics != "" &&
                idPlKaraoke != "" &&
                idPlLyrics != "" &&
                idVk != "" &&
                idBoosty != "" &&
                idSponsr == ""
            ) {
                return SettingState.ALL_DONE_WO_SPONSR
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
                idDzenKaraoke != "" &&
                idDzenLyrics != "" &&
                idPlKaraoke != "" &&
                idPlLyrics != "" &&
                idVk != "" &&
                idBoosty != "" &&
                idSponsr != ""
            ) {
                return SettingState.ALL_DONE
            } else if (idTelegramKaraoke != "-" &&
                idTelegramKaraoke != "" &&
                idTelegramLyrics != "" &&
                idVkKaraoke != "" &&
                idVkLyrics != "" &&
                idDzenKaraoke != "" &&
                idDzenLyrics != "" &&
                idPlKaraoke != "" &&
                idPlLyrics != "" &&
                idVk != "" &&
                idBoosty != "" &&
                idSponsr == ""
            ) {
                return SettingState.ALL_DONE_WO_SPONSR
            } else {
                return SettingState.OVERDUE
            }
        }

        if (idTelegramKaraoke == "-" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idDzenKaraoke != "" &&
            idDzenLyrics != "" &&
            idVk != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.ALL_UPLOADED

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idDzenKaraoke != "" &&
            idDzenLyrics != "" &&
            idPlKaraoke != "" &&
            idPlLyrics != "" &&
            idVk != "" &&
            idBoosty != "" &&
            idBoostyFiles != "" &&
            idSponsr == ""
        ) return SettingState.WO_TG

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idDzenKaraoke != "" &&
            idDzenLyrics != "" &&
            idPlKaraoke != "" &&
            idPlLyrics != "" &&
            idVk != "" &&
            idBoosty != "" &&
            idBoostyFiles != "" &&
            idSponsr != ""
        ) return SettingState.WO_TG_WITH_SPONSR

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idDzenKaraoke != "" &&
            idDzenLyrics != "" &&
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
            (idPlKaraoke == "" ||
            idPlLyrics == "") &&
            idDzenKaraoke != "" &&
            idDzenLyrics != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_VK_WO_PL

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            (idVkKaraoke == "" ||
                    idVkLyrics == "") &&
            idDzenKaraoke != "" &&
            idDzenLyrics != "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_VK

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            idPlKaraoke != "" &&
            idPlLyrics != "" &&
            (idDzenKaraoke == "" ||
                    idDzenLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_DZEN_WITH_VK_WITH_PL
        
        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            idVkKaraoke != "" &&
            idVkLyrics != "" &&
            (idDzenKaraoke == "" ||
            idDzenLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_DZEN_WITH_VK



        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            (idVkKaraoke == "" ||
            idVkLyrics == "") &&
            (idDzenKaraoke == "" ||
            idDzenLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != "" &&
            idSponsr == ""
        ) return SettingState.WO_DZEN

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
            (idVkKaraoke == "" ||
                    idVkLyrics == "") &&
            (idDzenKaraoke == "" ||
                    idDzenLyrics == "") &&
            idBoosty != "" &&
            idBoostyFiles != "" &&
            idSponsr != ""
        ) return SettingState.BOOSTY_SPONSR

        if (idTelegramKaraoke == "" &&
            idTelegramLyrics == "" &&
//            idVkKaraoke == "" &&
//            idVkLyrics == "" &&
//            idDzenKaraoke == "" &&
//            idDzenLyrics == "" &&
            idVk == "" &&
            idBoosty != "" &&
            idBoostyFiles != ""
        ) return SettingState.WO_VKG

        return SettingState.IN_WORK
    }

    companion object {

        fun renameFilesIfDiff(settNewVersion: Settings, settOldVersion: Settings): Pair<Boolean, Boolean> {
            val rsfnNew = settNewVersion.rightSettingFileName
            val rsfnOld = settOldVersion.rightSettingFileName
            var was1 = false
            var was2 = false
            if (rsfnNew != rsfnOld) {
                if (Paths.get(settOldVersion.pathToFileLyrics).toFile().exists()) Paths.get(settOldVersion.pathToFileLyrics).toFile().renameTo(Paths.get(settNewVersion.pathToFileLyrics).toFile())
                if (Paths.get(settOldVersion.pathToFileKaraoke).toFile().exists()) Paths.get(settOldVersion.pathToFileKaraoke).toFile().renameTo(Paths.get(settNewVersion.pathToFileKaraoke).toFile())
                if (Paths.get(settOldVersion.pathToFileChords).toFile().exists()) Paths.get(settOldVersion.pathToFileChords).toFile().renameTo(Paths.get(settNewVersion.pathToFileChords).toFile())
                if (Paths.get(settOldVersion.pathToFileMelody).toFile().exists()) Paths.get(settOldVersion.pathToFileMelody).toFile().renameTo(Paths.get(settNewVersion.pathToFileMelody).toFile())
                if (Paths.get(settOldVersion.pathToFile720Lyrics).toFile().exists()) Paths.get(settOldVersion.pathToFile720Lyrics).toFile().renameTo(Paths.get(settNewVersion.pathToFile720Lyrics).toFile())
                if (Paths.get(settOldVersion.pathToFile720Karaoke).toFile().exists()) Paths.get(settOldVersion.pathToFile720Karaoke).toFile().renameTo(Paths.get(settNewVersion.pathToFile720Karaoke).toFile())
                if (Paths.get(settOldVersion.pathToFile720Chords).toFile().exists()) Paths.get(settOldVersion.pathToFile720Chords).toFile().renameTo(Paths.get(settNewVersion.pathToFile720Chords).toFile())
                if (Paths.get(settOldVersion.pathToFile720Melody).toFile().exists()) Paths.get(settOldVersion.pathToFile720Melody).toFile().renameTo(Paths.get(settNewVersion.pathToFile720Melody).toFile())
                if (Paths.get(settOldVersion.pathToFileMP3Lyrics).toFile().exists()) Paths.get(settOldVersion.pathToFileMP3Lyrics).toFile().renameTo(Paths.get(settNewVersion.pathToFileMP3Lyrics).toFile())
                if (Paths.get(settOldVersion.pathToFileMP3Karaoke).toFile().exists()) Paths.get(settOldVersion.pathToFileMP3Karaoke).toFile().renameTo(Paths.get(settNewVersion.pathToFileMP3Karaoke).toFile())
                if (Paths.get(settOldVersion.pathToStoreFileLyrics).toFile().exists()) Paths.get(settOldVersion.pathToStoreFileLyrics).toFile().renameTo(Paths.get(settNewVersion.pathToStoreFileLyrics).toFile())
                if (Paths.get(settOldVersion.pathToStoreFileKaraoke).toFile().exists()) Paths.get(settOldVersion.pathToStoreFileKaraoke).toFile().renameTo(Paths.get(settNewVersion.pathToStoreFileKaraoke).toFile())
                if (Paths.get(settOldVersion.pathToStoreFileChords).toFile().exists()) Paths.get(settOldVersion.pathToStoreFileChords).toFile().renameTo(Paths.get(settNewVersion.pathToStoreFileChords).toFile())
                if (Paths.get(settOldVersion.pathToStoreFileMelody).toFile().exists()) Paths.get(settOldVersion.pathToStoreFileMelody).toFile().renameTo(Paths.get(settNewVersion.pathToStoreFileMelody).toFile())
                if (Paths.get(settOldVersion.pathToFileSheetsagePDF).toFile().exists()) Paths.get(settOldVersion.pathToFileSheetsagePDF).toFile().renameTo(Paths.get(settNewVersion.pathToFileSheetsagePDF).toFile())
                if (Paths.get(settOldVersion.pathToFileSheetsageLY).toFile().exists()) Paths.get(settOldVersion.pathToFileSheetsageLY).toFile().renameTo(Paths.get(settNewVersion.pathToFileSheetsageLY).toFile())
                if (Paths.get(settOldVersion.pathToFileSheetsageMIDI).toFile().exists()) Paths.get(settOldVersion.pathToFileSheetsageMIDI).toFile().renameTo(Paths.get(settNewVersion.pathToFileSheetsageMIDI).toFile())
                if (Paths.get(settOldVersion.pathToFileSheetsageBeattimes).toFile().exists()) Paths.get(settOldVersion.pathToFileSheetsageBeattimes).toFile().renameTo(Paths.get(settNewVersion.pathToFileSheetsageBeattimes).toFile())
                if (Paths.get(settOldVersion.fileSettingsAbsolutePath).toFile().exists()) Paths.get(settOldVersion.fileSettingsAbsolutePath).toFile().renameTo(Paths.get(settNewVersion.fileSettingsAbsolutePath).toFile())
                if (Paths.get("/home/nsa/Karaoke/karaoke-web/src/main/resources/static/tmp/${settNewVersion.id}.png").toFile().exists()) createVKLinkPictureWeb(settNewVersion)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTUREBOOSTYFILES)).toFile().exists()) createBoostyFilesPicture(settNewVersion)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTUREBOOSTYTEASER)).toFile().exists()) createBoostyTeaserPicture(settNewVersion)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER)).toFile().exists()) createSponsrTeaserPicture(settNewVersion)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTURE, SongVersion.LYRICS)).toFile().exists()) createSongPicture(settNewVersion, SongVersion.LYRICS)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTURE, SongVersion.KARAOKE)).toFile().exists()) createSongPicture(settNewVersion, SongVersion.KARAOKE)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTURE, SongVersion.CHORDS)).toFile().exists()) createSongPicture(settNewVersion, SongVersion.CHORDS)
                if (Paths.get(settNewVersion.getOutputFilename(SongOutputFile.PICTURE, SongVersion.TABS)).toFile().exists()) createSongPicture(settNewVersion, SongVersion.TABS)
                was1 = true
            }

            if (rsfnNew != settNewVersion.fileName) {
                settNewVersion.fileName = rsfnNew
                if (Paths.get(settOldVersion.fileAbsolutePath).toFile().exists()) Paths.get(settOldVersion.fileAbsolutePath).toFile().renameTo(Paths.get(settNewVersion.fileAbsolutePath).toFile())
                if (Paths.get(settOldVersion.oldNoStemNameWav).toFile().exists()) Paths.get(settOldVersion.oldNoStemNameWav).toFile().renameTo(Paths.get(settNewVersion.oldNoStemNameWav).toFile())
                if (Paths.get(settOldVersion.newNoStemNameWav).toFile().exists()) Paths.get(settOldVersion.newNoStemNameWav).toFile().renameTo(Paths.get(settNewVersion.newNoStemNameWav).toFile())
                if (Paths.get(settOldVersion.newNoStemNameFlac).toFile().exists()) Paths.get(settOldVersion.newNoStemNameFlac).toFile().renameTo(Paths.get(settNewVersion.newNoStemNameFlac).toFile())
                if (Paths.get(settOldVersion.vocalsNameWav).toFile().exists()) Paths.get(settOldVersion.vocalsNameWav).toFile().renameTo(Paths.get(settNewVersion.vocalsNameWav).toFile())
                if (Paths.get(settOldVersion.vocalsNameFlac).toFile().exists()) Paths.get(settOldVersion.vocalsNameFlac).toFile().renameTo(Paths.get(settNewVersion.vocalsNameFlac).toFile())
                if (Paths.get(settOldVersion.drumsNameWav).toFile().exists()) Paths.get(settOldVersion.drumsNameWav).toFile().renameTo(Paths.get(settNewVersion.drumsNameWav).toFile())
                if (Paths.get(settOldVersion.drumsNameFlac).toFile().exists()) Paths.get(settOldVersion.drumsNameFlac).toFile().renameTo(Paths.get(settNewVersion.drumsNameFlac).toFile())
                if (Paths.get(settOldVersion.bassNameWav).toFile().exists()) Paths.get(settOldVersion.bassNameWav).toFile().renameTo(Paths.get(settNewVersion.bassNameWav).toFile())
                if (Paths.get(settOldVersion.bassNameFlac).toFile().exists()) Paths.get(settOldVersion.bassNameFlac).toFile().renameTo(Paths.get(settNewVersion.bassNameFlac).toFile())
                if (Paths.get(settOldVersion.guitarsNameWav).toFile().exists()) Paths.get(settOldVersion.guitarsNameWav).toFile().renameTo(Paths.get(settNewVersion.guitarsNameWav).toFile())
                if (Paths.get(settOldVersion.guitarsNameFlac).toFile().exists()) Paths.get(settOldVersion.guitarsNameFlac).toFile().renameTo(Paths.get(settNewVersion.guitarsNameFlac).toFile())
                if (Paths.get(settOldVersion.otherNameWav).toFile().exists()) Paths.get(settOldVersion.otherNameWav).toFile().renameTo(Paths.get(settNewVersion.otherNameWav).toFile())
                if (Paths.get(settOldVersion.otherNameFlac).toFile().exists()) Paths.get(settOldVersion.otherNameFlac).toFile().renameTo(Paths.get(settNewVersion.otherNameFlac).toFile())
                settNewVersion.saveToDb()
                was2 = true
            }
            return Pair(was1, was2)
        }

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
                if (settA.versionBoosty != settB.versionBoosty) result.add(RecordDiff("version_boosty", settA.versionBoosty, settB.versionBoosty))
                if (settA.idBoostyFiles != settB.idBoostyFiles) result.add(RecordDiff("id_boosty_files", settA.idBoostyFiles, settB.idBoostyFiles))
                if (settA.versionBoostyFiles != settB.versionBoostyFiles) result.add(RecordDiff("version_boosty_files", settA.versionBoostyFiles, settB.versionBoostyFiles))
                if (settA.idSponsr != settB.idSponsr) result.add(RecordDiff("id_sponsr", settA.idSponsr, settB.idSponsr))
                if (settA.versionSponsr != settB.versionSponsr) result.add(RecordDiff("version_sponsr", settA.versionSponsr, settB.versionSponsr))
                if (settA.indexTabsVariant != settB.indexTabsVariant) result.add(RecordDiff("index_tabs_variant", settA.indexTabsVariant, settB.indexTabsVariant))
                if (settA.idVk != settB.idVk) result.add(RecordDiff("id_vk", settA.idVk, settB.idVk))
                if (settA.idDzenLyrics != settB.idDzenLyrics) result.add(RecordDiff("id_dzen_lyrics", settA.idDzenLyrics, settB.idDzenLyrics))
                if (settA.idDzenKaraoke != settB.idDzenKaraoke) result.add(RecordDiff("id_dzen_karaoke", settA.idDzenKaraoke, settB.idDzenKaraoke))
                if (settA.idDzenChords != settB.idDzenChords) result.add(RecordDiff("id_dzen_chords", settA.idDzenChords, settB.idDzenChords))
                if (settA.idDzenMelody != settB.idDzenMelody) result.add(RecordDiff("id_dzen_melody", settA.idDzenMelody, settB.idDzenMelody))
                if (settA.versionDzenLyrics != settB.versionDzenLyrics) result.add(RecordDiff("version_dzen_lyrics", settA.versionDzenLyrics, settB.versionDzenLyrics))
                if (settA.versionDzenKaraoke != settB.versionDzenKaraoke) result.add(RecordDiff("version_dzen_karaoke", settA.versionDzenKaraoke, settB.versionDzenKaraoke))
                if (settA.versionDzenChords != settB.versionDzenChords) result.add(RecordDiff("version_dzen_chords", settA.versionDzenChords, settB.versionDzenChords))
                if (settA.versionDzenMelody != settB.versionDzenMelody) result.add(RecordDiff("version_dzen_melody", settA.versionDzenMelody, settB.versionDzenMelody))
                if (settA.idVkLyrics != settB.idVkLyrics) result.add(RecordDiff("id_vk_lyrics", settA.idVkLyrics, settB.idVkLyrics))
                if (settA.idVkKaraoke != settB.idVkKaraoke) result.add(RecordDiff("id_vk_karaoke", settA.idVkKaraoke, settB.idVkKaraoke))
                if (settA.idVkChords != settB.idVkChords) result.add(RecordDiff("id_vk_chords", settA.idVkChords, settB.idVkChords))
                if (settA.idVkMelody != settB.idVkMelody) result.add(RecordDiff("id_vk_melody", settA.idVkMelody, settB.idVkMelody))
                if (settA.versionVkLyrics != settB.versionVkLyrics) result.add(RecordDiff("version_vk_lyrics", settA.versionVkLyrics, settB.versionVkLyrics))
                if (settA.versionVkKaraoke != settB.versionVkKaraoke) result.add(RecordDiff("version_vk_karaoke", settA.versionVkKaraoke, settB.versionVkKaraoke))
                if (settA.versionVkChords != settB.versionVkChords) result.add(RecordDiff("version_vk_chords", settA.versionVkChords, settB.versionVkChords))
                if (settA.versionVkMelody != settB.versionVkMelody) result.add(RecordDiff("version_vk_melody", settA.versionVkMelody, settB.versionVkMelody))
                if (settA.idTelegramLyrics != settB.idTelegramLyrics) result.add(RecordDiff("id_telegram_lyrics", settA.idTelegramLyrics, settB.idTelegramLyrics))
                if (settA.idTelegramKaraoke != settB.idTelegramKaraoke) result.add(RecordDiff("id_telegram_karaoke", settA.idTelegramKaraoke, settB.idTelegramKaraoke))
                if (settA.idTelegramChords != settB.idTelegramChords) result.add(RecordDiff("id_telegram_chords", settA.idTelegramChords, settB.idTelegramChords))
                if (settA.idTelegramMelody != settB.idTelegramMelody) result.add(RecordDiff("id_telegram_melody", settA.idTelegramMelody, settB.idTelegramMelody))
                if (settA.versionTelegramLyrics != settB.versionTelegramLyrics) result.add(RecordDiff("version_telegram_lyrics", settA.versionTelegramLyrics, settB.versionTelegramLyrics))
                if (settA.versionTelegramKaraoke != settB.versionTelegramKaraoke) result.add(RecordDiff("version_telegram_karaoke", settA.versionTelegramKaraoke, settB.versionTelegramKaraoke))
                if (settA.versionTelegramChords != settB.versionTelegramChords) result.add(RecordDiff("version_telegram_chords", settA.versionTelegramChords, settB.versionTelegramChords))
                if (settA.versionTelegramMelody != settB.versionTelegramMelody) result.add(RecordDiff("version_telegram_melody", settA.versionTelegramMelody, settB.versionTelegramMelody))                
                if (settA.idPlLyrics != settB.idPlLyrics) result.add(RecordDiff("id_pl_lyrics", settA.idPlLyrics, settB.idPlLyrics))
                if (settA.idPlKaraoke != settB.idPlKaraoke) result.add(RecordDiff("id_pl_karaoke", settA.idPlKaraoke, settB.idPlKaraoke))
                if (settA.idPlChords != settB.idPlChords) result.add(RecordDiff("id_pl_chords", settA.idPlChords, settB.idPlChords))
                if (settA.idPlMelody != settB.idPlMelody) result.add(RecordDiff("id_pl_melody", settA.idPlMelody, settB.idPlMelody))
                if (settA.versionPlLyrics != settB.versionPlLyrics) result.add(RecordDiff("version_pl_lyrics", settA.versionPlLyrics, settB.versionPlLyrics))
                if (settA.versionPlKaraoke != settB.versionPlKaraoke) result.add(RecordDiff("version_pl_karaoke", settA.versionPlKaraoke, settB.versionPlKaraoke))
                if (settA.versionPlChords != settB.versionPlChords) result.add(RecordDiff("version_pl_chords", settA.versionPlChords, settB.versionPlChords))
                if (settA.versionPlMelody != settB.versionPlMelody) result.add(RecordDiff("version_pl_melody", settA.versionPlMelody, settB.versionPlMelody))        
                if (settA.idStatus != settB.idStatus) result.add(RecordDiff("id_status", settA.idStatus, settB.idStatus))
                if (settA.sourceText != settB.sourceText) result.add(RecordDiff("source_text", settA.sourceText, settB.sourceText))
                if (settA.resultText != settB.resultText) result.add(RecordDiff("result_text", settA.resultText, settB.resultText))
                if (settA.sourceMarkers != settB.sourceMarkers) result.add(RecordDiff("source_markers", settA.sourceMarkers, settB.sourceMarkers))
                if (settA.statusProcessLyrics != settB.statusProcessLyrics) result.add(RecordDiff("status_process_lyrics", settA.statusProcessLyrics, settB.statusProcessLyrics))
                if (settA.statusProcessKaraoke != settB.statusProcessKaraoke) result.add(RecordDiff("status_process_karaoke", settA.statusProcessKaraoke, settB.statusProcessKaraoke))
                if (settA.statusProcessChords != settB.statusProcessChords) result.add(RecordDiff("status_process_chords", settA.statusProcessChords, settB.statusProcessChords))
                if (settA.statusProcessMelody != settB.statusProcessMelody) result.add(RecordDiff("status_process_melody", settA.statusProcessMelody, settB.statusProcessMelody))
                if (settA.tags != settB.tags) result.add(RecordDiff("tags", settA.tags, settB.tags))
                if (settA.rate != settB.rate) result.add(RecordDiff("rate", settA.rate, settB.rate))

                if (settA.status != settB.status) result.add(RecordDiff("status", settA.status, settB.status, false))
                if (settA.color != settB.color) result.add(RecordDiff("color", settA.color, settB.color, false))
                if (settA.processColorMeltLyrics != settB.processColorMeltLyrics) result.add(RecordDiff("processColorMeltLyrics", settA.processColorMeltLyrics, settB.processColorMeltLyrics, false))
                if (settA.processColorMeltKaraoke != settB.processColorMeltKaraoke) result.add(RecordDiff("processColorMeltKaraoke", settA.processColorMeltKaraoke, settB.processColorMeltKaraoke, false))
                if (settA.processColorMeltChords != settB.processColorMeltChords) result.add(RecordDiff("processColorMeltChords", settA.processColorMeltChords, settB.processColorMeltChords, false))
                if (settA.processColorMeltMelody != settB.processColorMeltMelody) result.add(RecordDiff("processColorMeltMelody", settA.processColorMeltMelody, settB.processColorMeltMelody, false))
                if (settA.processColorDzenLyrics != settB.processColorDzenLyrics) result.add(RecordDiff("processColorDzenLyrics", settA.processColorDzenLyrics, settB.processColorDzenLyrics, false))
                if (settA.processColorDzenKaraoke != settB.processColorDzenKaraoke) result.add(RecordDiff("processColorDzenKaraoke", settA.processColorDzenKaraoke, settB.processColorDzenKaraoke, false))
                if (settA.processColorDzenChords != settB.processColorDzenChords) result.add(RecordDiff("processColorDzenChords", settA.processColorDzenChords, settB.processColorDzenChords, false))
                if (settA.processColorDzenMelody != settB.processColorDzenMelody) result.add(RecordDiff("processColorDzenMelody", settA.processColorDzenMelody, settB.processColorDzenMelody, false))
                if (settA.processColorVkLyrics != settB.processColorVkLyrics) result.add(RecordDiff("processColorVkLyrics", settA.processColorVkLyrics, settB.processColorVkLyrics, false))
                if (settA.processColorVkKaraoke != settB.processColorVkKaraoke) result.add(RecordDiff("processColorVkKaraoke", settA.processColorVkKaraoke, settB.processColorVkKaraoke, false))
                if (settA.processColorVkChords != settB.processColorVkChords) result.add(RecordDiff("processColorVkChords", settA.processColorVkChords, settB.processColorVkChords, false))
                if (settA.processColorVkMelody != settB.processColorVkMelody) result.add(RecordDiff("processColorVkMelody", settA.processColorVkMelody, settB.processColorVkMelody, false))
                if (settA.processColorTelegramLyrics != settB.processColorTelegramLyrics) result.add(RecordDiff("processColorTelegramLyrics", settA.processColorTelegramLyrics, settB.processColorTelegramLyrics, false))
                if (settA.processColorTelegramKaraoke != settB.processColorTelegramKaraoke) result.add(RecordDiff("processColorTelegramKaraoke", settA.processColorTelegramKaraoke, settB.processColorTelegramKaraoke, false))
                if (settA.processColorTelegramChords != settB.processColorTelegramChords) result.add(RecordDiff("processColorTelegramChords", settA.processColorTelegramChords, settB.processColorTelegramChords, false))
                if (settA.processColorTelegramMelody != settB.processColorTelegramMelody) result.add(RecordDiff("processColorTelegramMelody", settA.processColorTelegramMelody, settB.processColorTelegramMelody, false))
                if (settA.processColorVk != settB.processColorVk) result.add(RecordDiff("processColorVk", settA.processColorVk, settB.processColorVk, false))
                if (settA.processColorBoosty != settB.processColorBoosty) result.add(RecordDiff("processColorBoosty", settA.processColorBoosty, settB.processColorBoosty, false))
                if (settA.processColorSponsr != settB.processColorSponsr) result.add(RecordDiff("processColorSponsr", settA.processColorSponsr, settB.processColorSponsr, false))
                if (settA.processColorPlLyrics != settB.processColorPlLyrics) result.add(RecordDiff("processColorPlLyrics", settA.processColorPlLyrics, settB.processColorPlLyrics, false))
                if (settA.processColorPlKaraoke != settB.processColorPlKaraoke) result.add(RecordDiff("processColorPlKaraoke", settA.processColorPlKaraoke, settB.processColorPlKaraoke, false))
                if (settA.processColorPlChords != settB.processColorPlChords) result.add(RecordDiff("processColorPlChords", settA.processColorPlChords, settB.processColorPlChords, false))
                if (settA.processColorPlMelody != settB.processColorPlMelody) result.add(RecordDiff("processColorPlMelody", settA.processColorPlMelody, settB.processColorPlMelody, false))
            }
            return result
        }

        fun totalCount(database: KaraokeConnection): Int {
            val sql = "SELECT COUNT(*) AS total_count FROM tbl_settings;"
            var result = -1
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return -1
            }
            var statement: Statement? = null
            var rs: ResultSet? = null

            try {
                statement = connection.createStatement()
                rs = statement.executeQuery(sql)
                while (rs.next()) {
                    return rs.getInt("total_count")
                }
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
            return result
        }

        fun getLastUpdated(lastTime: Long? = null, database: KaraokeConnection): List<Int> {
            if (lastTime == null) return emptyList()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return emptyList()
            }
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

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return null
            }
            val ps = connection.prepareStatement(sql)
            try {
                ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            } catch (e: Exception) {
                // Проверяем последнее значение сиквенса и айдишника таблицы
                val statement = connection.createStatement()
                val rsLastId = statement.executeQuery("select max(id) as last_value from tbl_settings;")
                val rsLastSeq = statement.executeQuery("select last_value from tbl_settings_id_seq;")
                rsLastId.next()
                val lastId = rsLastId.getLong("last_value")
                rsLastSeq.next()
                val lastSeq = rsLastSeq.getLong("last_value")
                if (lastSeq < lastId) {
                    statement.execute("alter sequence tbl_settings_id_seq restart with ${lastId+1};")
                    ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
                }
            }
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
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return emptyList()
            }
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
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return emptyList()
            }
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
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return emptyList()
            }
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

        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? {
            var result: MutableList<RecordHash>? = mutableListOf()
            val sql = "SELECT id, recordhash FROM tbl_settings $whereText"

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return null
            }
            var statement: Statement? = null
            var rs: ResultSet? = null

            try {
                statement = connection.createStatement()

                println("[${Timestamp.from(Instant.now())}] Запрос хешей...")
                rs = statement.executeQuery(sql)
                var cnt = 0
                while (rs.next()) {
                    cnt++
                    result!!.add(RecordHash(id = rs.getLong("id"), recordhash = rs.getString("recordhash")))
                }
                println("[${Timestamp.from(Instant.now())}] Получено хешей: $cnt")

            } catch (e: SQLException) {
                e.printStackTrace()
                result = null
            } finally {
                try {
                    rs?.close() // close result set
                    statement?.close() // close statement
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return result
        }

        fun loadListFromDb(args: Map<String, String> = emptyMap(), database: KaraokeConnection, sync: Boolean = false): List<Settings> {

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return emptyList()
            }
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT * FROM tbl_settings${if (sync) "_sync" else ""}"
                val limit = args["limit"]?.toInt() ?: 0
                val offset = args["offset"]?.toInt() ?: 0

                if (args.containsKey("ids")) where += "tbl_settings${if (sync) "_sync" else ""}.id in (${args["ids"]})"
                if (args.containsKey("file_name")) where += "LOWER(file_name)='${args["file_name"]?.rightFileName()?.lowercase()}'"
                if (args.containsKey("root_folder")) where += "LOWER(root_folder)='${args["root_folder"]?.rightFileName()?.lowercase()}'"
                if (args.containsKey("song_name")) where += "LOWER(song_name) LIKE '%${args["song_name"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("song_author")) where += "LOWER(song_author) LIKE '%${args["song_author"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("author")) where += "LOWER(song_author) = '${args["author"]?.rightFileName()?.lowercase()}'"
                if (args.containsKey("song_album")) where += "LOWER(song_album) LIKE '%${args["song_album"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("album")) where += "LOWER(song_album) = '${args["album"]?.rightFileName()?.lowercase()}'"

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

                if (args.containsKey("flag_boosty")) where += "CASE WHEN id_boosty IS NOT NULL AND id_boosty <> 'null' AND id_boosty <> '' THEN '+' ELSE '-' END='${args["flag_boosty"]}'"
                if (args.containsKey("flag_sponsr")) where += "CASE WHEN id_sponsr IS NOT NULL AND id_sponsr <> 'null' AND id_sponsr <> '' THEN '+' ELSE '-' END='${args["flag_sponsr"]}'"
                if (args.containsKey("flag_vk")) where += "CASE WHEN id_vk IS NOT NULL AND id_vk <> 'null' AND id_vk <> '' THEN '+' ELSE '-' END='${args["flag_vk"]}'"
                if (args.containsKey("flag_dzen_lyrics")) where += "CASE WHEN id_dzen_lyrics IS NOT NULL AND id_dzen_lyrics <> 'null' AND id_dzen_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_dzen_lyrics"]}'"
                if (args.containsKey("flag_dzen_karaoke")) where += "CASE WHEN id_dzen_karaoke IS NOT NULL AND id_dzen_karaoke <> 'null' AND id_dzen_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_dzen_karaoke"]}'"
                if (args.containsKey("flag_dzen_chords")) where += "CASE WHEN id_dzen_chords IS NOT NULL AND id_dzen_chords <> 'null' AND id_dzen_chords <> '' THEN '+' ELSE '-' END='${args["flag_dzen_chords"]}'"
                if (args.containsKey("flag_dzen_melody")) where += "CASE WHEN id_dzen_melody IS NOT NULL AND id_dzen_melody <> 'null' AND id_dzen_melody <> '' THEN '+' ELSE '-' END='${args["flag_dzen_melody"]}'"
                if (args.containsKey("flag_vk_lyrics")) where += "CASE WHEN id_vk_lyrics IS NOT NULL AND id_vk_lyrics <> 'null' AND id_vk_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_vk_lyrics"]}'"
                if (args.containsKey("flag_vk_karaoke")) where += "CASE WHEN id_vk_karaoke IS NOT NULL AND id_vk_karaoke <> 'null' AND id_vk_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_vk_karaoke"]}'"
                if (args.containsKey("flag_vk_chords")) where += "CASE WHEN id_vk_chords IS NOT NULL AND id_vk_chords <> 'null' AND id_vk_chords <> '' THEN '+' ELSE '-' END='${args["flag_vk_chords"]}'"
                if (args.containsKey("flag_vk_melody")) where += "CASE WHEN id_vk_melody IS NOT NULL AND id_vk_melody <> 'null' AND id_vk_melody <> '' THEN '+' ELSE '-' END='${args["flag_vk_melody"]}'"
                if (args.containsKey("flag_telegram_lyrics")) where += "CASE WHEN id_telegram_lyrics IS NOT NULL AND id_telegram_lyrics <> 'null' AND id_telegram_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_telegram_lyrics"]}'"
                if (args.containsKey("flag_telegram_karaoke")) where += "CASE WHEN id_telegram_karaoke IS NOT NULL AND id_telegram_karaoke <> 'null' AND id_telegram_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_telegram_karaoke"]}'"
                if (args.containsKey("flag_telegram_chords")) where += "CASE WHEN id_telegram_chords IS NOT NULL AND id_telegram_chords <> 'null' AND id_telegram_chords <> '' THEN '+' ELSE '-' END='${args["flag_telegram_chords"]}'"
                if (args.containsKey("flag_telegram_melody")) where += "CASE WHEN id_telegram_melody IS NOT NULL AND id_telegram_melody <> 'null' AND id_telegram_melody <> '' THEN '+' ELSE '-' END='${args["flag_telegram_melody"]}'"
                if (args.containsKey("flag_pl_lyrics")) where += "CASE WHEN id_pl_lyrics IS NOT NULL AND id_pl_lyrics <> 'null' AND id_pl_lyrics <> '' THEN '+' ELSE '-' END='${args["flag_pl_lyrics"]}'"
                if (args.containsKey("flag_pl_karaoke")) where += "CASE WHEN id_pl_karaoke IS NOT NULL AND id_pl_karaoke <> 'null' AND id_pl_karaoke <> '' THEN '+' ELSE '-' END='${args["flag_pl_karaoke"]}'"
                if (args.containsKey("flag_pl_chords")) where += "CASE WHEN id_pl_chords IS NOT NULL AND id_pl_chords <> 'null' AND id_pl_chords <> '' THEN '+' ELSE '-' END='${args["flag_pl_chords"]}'"
                if (args.containsKey("flag_pl_melody")) where += "CASE WHEN id_pl_melody IS NOT NULL AND id_pl_melody <> 'null' AND id_pl_melody <> '' THEN '+' ELSE '-' END='${args["flag_pl_melody"]}'"

                if (args.containsKey("filter_status_process_lyrics")) where += "LOWER(status_process_lyrics) LIKE '%${args["filter_status_process_lyrics"]?.rightFileName()?.lowercase()}%'"
                if (args.containsKey("filter_status_process_karaoke")) where += "LOWER(status_process_karaoke) LIKE '%${args["filter_status_process_karaoke"]?.rightFileName()?.lowercase()}%'"

                val listFields = listOf(
                    Pair("id", "id"),
                    Pair("id_status", "id_status"),
                    Pair("filter_result_version", "result_version"),
                    Pair("filter_version_boosty", "version_boosty"),
                    Pair("filter_version_boosty_files", "version_boosty_files"),
                    Pair("filter_version_sponsr", "version_sponsr"),
                    Pair("filter_version_dzen_karaoke", "version_dzen_karaoke"),
                    Pair("filter_version_vk_karaoke", "version_vk_karaoke"),
                    Pair("filter_version_telegram_karaoke", "version_telegram_karaoke"),
                    Pair("filter_version_pl_karaoke", "version_pl_karaoke"),
                    Pair("filter_rate", "rate")
                )

                listFields.forEach { (filterFldName, fldName) ->
                    if (args.containsKey(filterFldName)) {
                        args[filterFldName]!!.split("&&").forEach {
                            val value = it.trim()
                            if (value.startsWith(">=")) {
                                where += "tbl_settings${if (sync) "_sync" else ""}.$fldName>=${value.substring(2)}"
                            } else if (value.startsWith(">")) {
                                where += "tbl_settings${if (sync) "_sync" else ""}.$fldName>${value.substring(1)}"
                            } else if (value.startsWith("<=")) {
                                where += "tbl_settings${if (sync) "_sync" else ""}.$fldName<=${value.substring(2)}"
                            } else if (value.startsWith("<")) {
                                where += "tbl_settings${if (sync) "_sync" else ""}.$fldName<${value.substring(1)}"
                            } else if (value.startsWith("!=")) {
                                where += "tbl_settings${if (sync) "_sync" else ""}.$fldName<>${value.substring(2)}"
                            } else {
                                where += "tbl_settings${if (sync) "_sync" else ""}.$fldName=${value}"
                            }
                        }
                    }
                }

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

                sql += " ORDER BY tbl_settings${if (sync) "_sync" else ""}.id"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"

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
                    rs.getInt("song_year").let { value -> settings.fields[SettingField.YEAR] = value.toString() }
                    rs.getInt("song_track").let { value -> settings.fields[SettingField.TRACK] = value.toString() }
                    rs.getString("song_tone")?.let { value -> settings.fields[SettingField.KEY] = value }
                    rs.getInt("song_bpm").let { value -> settings.fields[SettingField.BPM] = value.toString() }
                    rs.getInt("song_ms").let { value -> settings.fields[SettingField.MS] = value.toString() }
                    rs.getString("id_boosty")?.let { value -> settings.fields[SettingField.ID_BOOSTY] = value }
                    rs.getString("id_boosty_files")?.let { value -> settings.fields[SettingField.ID_BOOSTY_FILES] = value }
                    rs.getString("id_sponsr")?.let { value -> settings.fields[SettingField.ID_SPONSR] = value }
                    rs.getInt("version_boosty").let { value -> settings.fields[SettingField.VERSION_BOOSTY] = value.toString() }
                    rs.getInt("version_boosty_files").let { value -> settings.fields[SettingField.VERSION_BOOSTY_FILES] = value.toString() }
                    rs.getInt("version_sponsr").let { value -> settings.fields[SettingField.VERSION_SPONSR] = value.toString() }
                    rs.getInt("index_tabs_variant").let { value -> settings.fields[SettingField.INDEX_TABS_VARIANT] = value.toString() }
                    rs.getString("id_vk")?.let { value -> settings.fields[SettingField.ID_VK] = value }
                    rs.getString("id_dzen_lyrics")?.let { value -> settings.fields[SettingField.ID_DZEN_LYRICS] = value }
                    rs.getString("id_dzen_karaoke")?.let { value -> settings.fields[SettingField.ID_DZEN_KARAOKE] = value }
                    rs.getString("id_dzen_chords")?.let { value -> settings.fields[SettingField.ID_DZEN_CHORDS] = value }
                    rs.getString("id_dzen_melody")?.let { value -> settings.fields[SettingField.ID_DZEN_MELODY] = value }
                    rs.getString("id_vk_lyrics")?.let { value -> settings.fields[SettingField.ID_VK_LYRICS] = value }
                    rs.getString("id_vk_karaoke")?.let { value -> settings.fields[SettingField.ID_VK_KARAOKE] = value }
                    rs.getString("id_vk_chords")?.let { value -> settings.fields[SettingField.ID_VK_CHORDS] = value }
                    rs.getString("id_vk_melody")?.let { value -> settings.fields[SettingField.ID_VK_MELODY] = value }
                    rs.getString("id_telegram_lyrics")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_LYRICS] = value }
                    rs.getString("id_telegram_karaoke")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_KARAOKE] = value }
                    rs.getString("id_telegram_chords")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_CHORDS] = value }
                    rs.getString("id_telegram_melody")?.let { value -> settings.fields[SettingField.ID_TELEGRAM_MELODY] = value }
                    rs.getString("id_pl_lyrics")?.let { value -> settings.fields[SettingField.ID_PL_LYRICS] = value }
                    rs.getString("id_pl_karaoke")?.let { value -> settings.fields[SettingField.ID_PL_KARAOKE] = value }
                    rs.getString("id_pl_chords")?.let { value -> settings.fields[SettingField.ID_PL_CHORDS] = value }
                    rs.getString("id_pl_melody")?.let { value -> settings.fields[SettingField.ID_PL_MELODY] = value }
                    rs.getInt("version_dzen_lyrics").let { value -> settings.fields[SettingField.VERSION_DZEN_LYRICS] = value.toString() }
                    rs.getInt("version_dzen_karaoke").let { value -> settings.fields[SettingField.VERSION_DZEN_KARAOKE] = value.toString() }
                    rs.getInt("version_dzen_chords").let { value -> settings.fields[SettingField.VERSION_DZEN_CHORDS] = value.toString() }
                    rs.getInt("version_dzen_melody").let { value -> settings.fields[SettingField.VERSION_DZEN_MELODY] = value.toString() }
                    rs.getInt("version_vk_lyrics").let { value -> settings.fields[SettingField.VERSION_VK_LYRICS] = value.toString() }
                    rs.getInt("version_vk_karaoke").let { value -> settings.fields[SettingField.VERSION_VK_KARAOKE] = value.toString() }
                    rs.getInt("version_vk_chords").let { value -> settings.fields[SettingField.VERSION_VK_CHORDS] = value.toString() }
                    rs.getInt("version_vk_melody").let { value -> settings.fields[SettingField.VERSION_VK_MELODY] = value.toString() }
                    rs.getInt("version_telegram_lyrics").let { value -> settings.fields[SettingField.VERSION_TELEGRAM_LYRICS] = value.toString() }
                    rs.getInt("version_telegram_karaoke").let { value -> settings.fields[SettingField.VERSION_TELEGRAM_KARAOKE] = value.toString() }
                    rs.getInt("version_telegram_chords").let { value -> settings.fields[SettingField.VERSION_TELEGRAM_CHORDS] = value.toString() }
                    rs.getInt("version_telegram_melody").let { value -> settings.fields[SettingField.VERSION_TELEGRAM_MELODY] = value.toString() }
                    rs.getInt("version_pl_lyrics").let { value -> settings.fields[SettingField.VERSION_PL_LYRICS] = value.toString() }
                    rs.getInt("version_pl_karaoke").let { value -> settings.fields[SettingField.VERSION_PL_KARAOKE] = value.toString() }
                    rs.getInt("version_pl_chords").let { value -> settings.fields[SettingField.VERSION_PL_CHORDS] = value.toString() }
                    rs.getInt("version_pl_melody").let { value -> settings.fields[SettingField.VERSION_PL_MELODY] = value.toString() }
                    rs.getInt("result_version").let { value -> settings.fields[SettingField.RESULT_VERSION] = value.toString() }
                    rs.getInt("diff_beats").let { value -> settings.fields[SettingField.DIFFBEATS] = value.toString() }
                    rs.getString("source_text")?.let { value -> settings.sourceText = value }
                    rs.getString("result_text")?.let { value -> settings.resultText = value }
                    rs.getString("source_markers")?.let { value -> settings.sourceMarkers = value }
                    rs.getInt("rate").let { value -> settings.fields[SettingField.RATE] = value.toString() }
                    settings.statusProcessLyrics = rs.getString("status_process_lyrics") ?: ""
                    settings.statusProcessKaraoke = rs.getString("status_process_karaoke") ?: ""
                    settings.statusProcessChords = rs.getString("status_process_chords") ?: ""
                    settings.statusProcessMelody = rs.getString("status_process_melody") ?: ""
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

                    if (!args.containsKey("filter_count_voices") || (args.containsKey("filter_count_voices") && args["filter_count_voices"] == settings.countVoices.toString())) result.add(settings)

                }
                result.sort()

                if (args.containsKey("sort")) {
                    when (args["sort"]) {
                        "id" -> return result.sortedWith(compareBy { it.id })
                        "author" -> return result.sortedWith(compareBy<Settings> { it.author }.thenBy { it.dateTimePublish }.thenBy { it.id }
                        )
                    }
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



        fun loadFromDbById(id: Long, database: KaraokeConnection, sync: Boolean = false): Settings? {
            val setting = loadListFromDb(mapOf(Pair("id", id.toString())), database, sync = sync).firstOrNull()
//            setting?.let {
//                if (setting.countNotEmptyVoices > 0) {
//                    println(it.getTextFromVoices(maxTimeCodes = -1))
//                    println("getLongerElement = ${it.getLongerElement()?.syllables?.map { it.text }?.joinToString("")}")
//                    println("getFontSize = ${it.getFontSize()}")
//                }
//            }
            return setting

        }


        fun deleteFromDb(id: Long, database: KaraokeConnection, sync: Boolean = false) {

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return
            }
            val sql = "DELETE FROM tbl_settings${if (sync) "_sync" else ""} WHERE id = ?"
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
            val listFiles = getListFiles(startFolder,listOf("flac", "mp3"))
            listFiles.forEach { pathToFile ->

                val file = File(pathToFile)
                val fileName = file.nameWithoutExtension
                val fileExtension = file.extension
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
                            // если файл не флак - преобразуем во флак и удаляем исходник
                            if (fileExtension != "flac") {
                                runCommand(listOf("ffmpeg", "-i", pathToFile, "-compression_level", "8", pathToFile.substring(0, pathToFile.length - fileExtension.length)+"flac", "-y"),)
                                runCommand(listOf("rm", pathToFile))
                            }
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
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return emptySet()
            }
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
        return sortString.compareTo(other.sortString)
    }

    fun toDTO(): SettingsDTO {
        return SettingsDTO(
            id = id,
            idPrevious = id,
            idNext = id,
            idLeft = id,
            idRight = id,
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
            timecode = convertMillisecondsToDtoTimecode(ms),
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
            flagSponsr = flagSponsr,
            flagVk = flagVk,
            flagDzenLyrics = flagDzenLyrics,
            flagDzenKaraoke = flagDzenKaraoke,
            flagDzenChords = flagDzenChords,
            flagDzenMelody = flagDzenMelody,
            flagVkLyrics = flagVkLyrics,
            flagVkKaraoke = flagVkKaraoke,
            flagVkChords = flagVkChords,
            flagVkMelody = flagVkMelody,
            flagTelegramLyrics = flagTelegramLyrics,
            flagTelegramKaraoke = flagTelegramKaraoke,
            flagTelegramChords = flagTelegramChords,
            flagTelegramMelody = flagTelegramMelody,
            flagPlLyrics = flagPlLyrics,
            flagPlKaraoke = flagPlKaraoke,
            flagPlChords = flagPlChords,
            flagPlMelody = flagPlMelody,
            processColorBoosty = processColorBoosty,
            processColorSponsr = processColorSponsr,
            processColorVk = processColorVk,
            processColorMeltLyrics = processColorMeltLyrics,
            processColorMeltKaraoke = processColorMeltKaraoke,
            processColorMeltChords = processColorMeltChords,
            processColorMeltMelody = processColorMeltMelody,
            processColorDzenLyrics = processColorDzenLyrics,
            processColorDzenKaraoke = processColorDzenKaraoke,
            processColorDzenChords = processColorDzenChords,
            processColorDzenMelody = processColorDzenMelody,
            processColorVkLyrics = processColorVkLyrics,
            processColorVkKaraoke = processColorVkKaraoke,
            processColorVkChords = processColorVkChords,
            processColorVkMelody = processColorVkMelody,
            processColorTelegramLyrics = processColorTelegramLyrics,
            processColorTelegramKaraoke = processColorTelegramKaraoke,
            processColorTelegramChords = processColorTelegramChords,
            processColorTelegramMelody = processColorTelegramMelody,
            processColorPlLyrics = processColorPlLyrics,
            processColorPlKaraoke = processColorPlKaraoke,
            processColorPlChords = processColorPlChords,
            processColorPlMelody = processColorPlMelody,
            idBoosty = idBoosty,
            idBoostyFiles = idBoostyFiles,
            idSponsr = idSponsr,
            versionBoosty = versionBoosty,
            versionBoostyFiles = versionBoostyFiles,
            versionSponsr = versionSponsr,
            indexTabsVariant = indexTabsVariant,
            idVk = idVk,
            idDzenLyrics = idDzenLyrics,
            idDzenKaraoke = idDzenKaraoke,
            idDzenChords = idDzenChords,
            idDzenMelody = idDzenMelody,
            versionDzenLyrics = versionDzenLyrics,
            versionDzenKaraoke = versionDzenKaraoke,
            versionDzenChords = versionDzenChords,
            versionDzenMelody = versionDzenMelody,
            idVkLyrics = idVkLyrics,
            idVkLyricsOID = idVkLyricsOID,
            idVkLyricsID = idVkLyricsID,
            idVkKaraoke = idVkKaraoke,
            idVkKaraokeOID = idVkKaraokeOID,
            idVkKaraokeID = idVkKaraokeID,
            idVkChords = idVkChords,
            idVkChordsOID = idVkChordsOID,
            idVkChordsID = idVkChordsID,
            idVkMelody = idVkMelody,
            idVkMelodyOID = idVkMelodyOID,
            idVkMelodyID = idVkMelodyID,
            versionVkLyrics = versionVkLyrics,
            versionVkKaraoke = versionVkKaraoke,
            versionVkChords = versionVkChords,
            versionVkMelody = versionVkMelody,
            idTelegramLyrics = idTelegramLyrics,
            idTelegramKaraoke = idTelegramKaraoke,
            idTelegramChords = idTelegramChords,
            idTelegramMelody = idTelegramMelody,
            versionTelegramLyrics = versionTelegramLyrics,
            versionTelegramKaraoke = versionTelegramKaraoke,
            versionTelegramChords = versionTelegramChords,
            versionTelegramMelody = versionTelegramMelody,
            idPlLyrics = idPlLyrics,
            idPlKaraoke = idPlKaraoke,
            idPlChords = idPlChords,
            idPlMelody = idPlMelody,
            versionPlLyrics = versionPlLyrics,
            versionPlKaraoke = versionPlKaraoke,
            versionPlChords = versionPlChords,
            versionPlMelody = versionPlMelody,
            rate = rate
        )
    }

    fun copyFieldsFromAnother(idAnother: Long, listFields: List<SettingField>) {
        loadFromDbById(id = idAnother, database = this.database)?. let { anotherSettings ->
            var wasChange = false
            listFields.forEach { settingField ->
                anotherSettings.fields[settingField]?.let { anotherValue ->
                    this.fields[settingField] = anotherValue
                    wasChange = true
                }
            }
            if (wasChange) this.saveToDb()
        }

    }

}



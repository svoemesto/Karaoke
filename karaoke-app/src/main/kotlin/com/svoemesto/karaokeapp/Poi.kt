package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SettingField
import com.svoemesto.karaokeapp.model.Song
import java.io.File
import org.odftoolkit.simple.SpreadsheetDocument
import org.odftoolkit.simple.table.Row

fun mainPoi() {
    val artist = "Павел Кашин"
    val songName = "Барышня"

    val (firstRow, row) = Ods.findRow(artist, songName)

    if (row == null) {
        println("Не найдена композиция $songName исполнителя $artist")
    } else {
        println("Композиция $songName исполнителя $artist найдена в строке с индексом ${row.rowIndex}")
        println("Публикация на Youtube: ${row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue}")
        println("Lyrics: ${URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", row.getCellByIndex(
            Ods.getColumnIndex(
                firstRow!!,
                ODS_COLUMN_YOUTUBE_LIRIC
            )
        ).stringValue)}")

    }
}

class Ods {
    companion object {

        fun getColumnIndex(row: Row, value: String): Int {
            for (i in 0 until row.cellCount) {
                val cell = row.getCellByIndex(i)
                if (cell.stringValue == value) return cell.columnIndex
            }
            return -1
        }
        fun findRow(artist: String, songName: String): Pair<Row?, Row?> {
            val spreadsheetDocument = SpreadsheetDocument.loadDocument(File(PATH_TO_ODS))

            val table = spreadsheetDocument.tableList.firstOrNull() { it.tableName == artist }
            table?.let {
                val firstRow = it.rowList[0]
                    it.rowList.forEach { row ->
                    val cell = row.getCellByIndex(8)
                    if (cell.stringValue == songName) {
                        spreadsheetDocument.close()
                        return Pair(firstRow, row)
                    }
                }
            }
            spreadsheetDocument.close()
            return Pair(null, null)
        }
        fun findRow(artist: String, songName: String, spreadsheetDocument: SpreadsheetDocument): Pair<Row?, Row?> {

            val table = spreadsheetDocument.tableList.firstOrNull() { it.tableName == artist } ?: spreadsheetDocument.tableList.firstOrNull() { it.tableName == "РАЗНОЕ" }
            table?.let {
                val firstRow = it.rowList[0]
                    it.rowList.forEach { row ->
                    val cell = row.getCellByIndex(8)
                    if (cell.stringValue == songName) {
                        return Pair(firstRow, row)
                    }
                }
            }
            return Pair(null, null)
        }

        fun getSettingFields(author: String, songName: String, spreadsheetDocument: SpreadsheetDocument): MutableMap<SettingField, String>? {
            val (firstRow, row) = findRow(author, songName, spreadsheetDocument)
            if (row == null) {
                return null
            } else {

                val result: MutableMap<SettingField, String> = mutableMapOf()

                val date = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue.trim()
                val time = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_TIME)).stringValue.trim()
                val boostyNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_BOOSTY)).stringValue.trim()
                val lyricsNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC)).stringValue.trim()
//                val lyricsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC_BT)).stringValue.trim()
                val karaokeNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_KARAOKE)).stringValue.trim()
//                val karaokeDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_KARAOKE_BT)).stringValue.trim()
                val chordsNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_CHORDS)).stringValue.trim()
//                val chordsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_CHORDS_BT)).stringValue.trim()
                val year = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YEAR)).stringValue.trim()
                val album = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_ALBUM)).stringValue.trim()
                val track = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_TRACK)).stringValue.trim()
                val tone = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_TONE)).stringValue.trim()
                val bpm = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_BPM)).stringValue.trim()
                val format = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_FORMAT)).stringValue.trim()

                result[SettingField.AUTHOR] = author
                result[SettingField.NAME] = songName
                if (date != "") result[SettingField.DATE] = date
                if (time != "") result[SettingField.TIME] = time
                if (year != "") result[SettingField.YEAR] = year
                if (album != "") result[SettingField.ALBUM] = album
                if (track != "") result[SettingField.TRACK] = track
                if (tone != "") result[SettingField.KEY] = tone
                if (bpm != "") result[SettingField.BPM] = bpm
                if (format != "") result[SettingField.FORMAT] = format
                if (boostyNormal != "") result[SettingField.ID_BOOSTY] = boostyNormal
                if (lyricsNormal != "") result[SettingField.ID_YOUTUBE_LYRICS] = lyricsNormal
//                if (lyricsDelay != "") result[SettingField.ID_YOUTUBE_LYRICS_BT] = lyricsDelay
                if (karaokeNormal != "") result[SettingField.ID_YOUTUBE_KARAOKE] = karaokeNormal
//                if (karaokeDelay != "") result[SettingField.ID_YOUTUBE_KARAOKE_BT] = karaokeDelay
                if (chordsNormal != "") result[SettingField.ID_YOUTUBE_CHORDS] = chordsNormal
//                if (chordsDelay != "") result[SettingField.ID_YOUTUBE_CHORDS_BT] = chordsDelay

                return result
            }
        }

        fun getSongVKDescription(song: Song, fileName: String, spreadsheetDocument: SpreadsheetDocument?): Pair<String, String>? {

            val template = song.settings.getVKGroupDescription()
            val author = song.settings.author
            val songName = song.settings.songName

            var date = ""
            var time = ""
            var boostyNormal = ""
            var lyricsNormal = ""
//            var lyricsDelay = ""
            var karaokeNormal = ""
//            var karaokeDelay = ""
            var chordsNormal = ""
//            var chordsDelay = ""

            if (spreadsheetDocument != null) {
                val (firstRow, row) = findRow(author, songName, spreadsheetDocument)
                if (row == null) {
                    return null
                } else {
                    date = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue.trim()
                    time = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_TIME)).stringValue.trim().replace(":",".")
                    boostyNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_BOOSTY)).stringValue.trim()
                    lyricsNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC)).stringValue.trim()
//                    lyricsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC_BT)).stringValue.trim()
                    karaokeNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_KARAOKE)).stringValue.trim()
//                    karaokeDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_KARAOKE_BT)).stringValue.trim()
                    chordsNormal = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_CHORDS)).stringValue.trim()
//                    chordsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_CHORDS_BT)).stringValue.trim()
                }
            } else {
                date = song.settings.date
                time = song.settings.time.replace(":",".")
                boostyNormal = song.settings.idBoosty ?: ""
                lyricsNormal = song.settings.idYoutubeLyrics ?: ""
//                lyricsDelay = song.settings.idYoutubeLyricsBt ?: ""
                karaokeNormal = song.settings.idYoutubeKaraoke ?: ""
//                karaokeDelay = song.settings.idYoutubeKaraokeBt ?: ""
                chordsNormal = song.settings.idYoutubeChords ?: ""
//                chordsDelay = song.settings.idYoutubeChordsBt ?: ""
            }

            if ("$lyricsNormal$karaokeNormal" != "") {
                val trueDate = "${date.substring(6)}.${date.substring(3,5)}.${date.substring(0,2)}"
                val name = fileName.replace("{REPLACE_DATE}",trueDate).replace("{REPLACE_TIME}",time).replace(" [lyrics]","")
                val boostyNormalLink = if (boostyNormal == "") "" else URL_PREFIX_BOOSTY.replace("{REPLACE}", boostyNormal) +"\n"
                val lyricsNormalLink = if (lyricsNormal == "") "" else "Lyrics: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", lyricsNormal) +"\n"
//                val lyricsDelayLink = if (lyricsDelay == "") "" else "Lyrics with delay: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", lyricsDelay) +"\n"
                val karaokeNormalLink = if (karaokeNormal == "") "" else "Karaoke: " +  URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", karaokeNormal) +"\n"
//                val karaokeDelayLink = if (karaokeDelay == "") "" else "Karaoke with delay: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", karaokeDelay) +"\n"
                val chordsNormalLink = if (chordsNormal == "") "" else "Chords: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", chordsNormal) +"\n"
//                val chordsDelayLink = if (chordsDelay == "") "" else "Chords with delay: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", chordsDelay) +"\n"
                val text = template
                    .replace("{REPLACE_BOOSTY_NORMAL}\n", boostyNormalLink)
                    .replace("{REPLACE_LYRICS_NORMAL}\n", lyricsNormalLink)
                    .replace("{REPLACE_KARAOKE_NORMAL}\n", karaokeNormalLink)
                    .replace("{REPLACE_CHORDS_NORMAL}\n", chordsNormalLink)
//                    .replace("{REPLACE_LYRICS_DELAY}\n", lyricsDelayLink)
//                    .replace("{REPLACE_KARAOKE_DELAY}\n", karaokeDelayLink)
//                    .replace("{REPLACE_CHORDS_DELAY}\n", chordsDelayLink)

                return Pair(text, name)
            }
            return null
        }
    }
}
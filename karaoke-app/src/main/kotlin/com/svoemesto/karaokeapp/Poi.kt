package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.SongRenderContext
import org.odftoolkit.simple.SpreadsheetDocument
import org.odftoolkit.simple.table.Row
import java.io.File

@Suppress("unused")
fun mainPoi() {
    val artist = "Павел Кашин"
    val songName = "Барышня"

    val (firstRow, row) = Ods.findRow(artist, songName)

    if (row == null) {
        println("Не найдена композиция $songName исполнителя $artist")
    } else {
        println("Композиция $songName исполнителя $artist найдена в строке с индексом ${row.rowIndex}")
        println("Публикация на Dzen: ${row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue}")
        println(
            "Lyrics: ${URL_PREFIX_DZEN_PLAY.replace(
                "{REPLACE}",
                row
                    .getCellByIndex(
                        Ods.getColumnIndex(
                            firstRow,
                            ODS_COLUMN_DZEN_LYRIC,
                        ),
                    ).stringValue,
            )}",
        )
    }
}

/**
 * Класс Ods.
 *
 * @see archive/archive/docs/features/dual-db-sync.md
 */
class Ods {
    companion object {
        fun getColumnIndex(
            row: Row,
            value: String,
        ): Int {
            for (i in 0 until row.cellCount) {
                val cell = row.getCellByIndex(i)
                if (cell.stringValue == value) return cell.columnIndex
            }
            return -1
        }

        fun findRow(
            artist: String,
            songName: String,
        ): Pair<Row?, Row?> {
            val spreadsheetDocument = SpreadsheetDocument.loadDocument(File(PATH_TO_ODS))

            val table = spreadsheetDocument.tableList.firstOrNull { it.tableName == artist }
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

        fun findRow(
            artist: String,
            songName: String,
            spreadsheetDocument: SpreadsheetDocument,
        ): Pair<Row?, Row?> {
            val table =
                spreadsheetDocument.tableList.firstOrNull { it.tableName == artist }
                    ?: spreadsheetDocument.tableList.firstOrNull { it.tableName == "РАЗНОЕ" }
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

        @Suppress("unused")
        fun getSettingFields(
            author: String,
            songName: String,
            spreadsheetDocument: SpreadsheetDocument,
        ): MutableMap<SongField, String>? {
            val (firstRow, row) = findRow(author, songName, spreadsheetDocument)
            if (row == null) {
                return null
            } else {
                val result: MutableMap<SongField, String> = mutableMapOf()

                val date = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue.trim()
                val time = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_TIME)).stringValue.trim()
                val boostyNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_BOOSTY)).stringValue.trim()
                val lyricsNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_DZEN_LYRIC)).stringValue.trim()
//                val lyricsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DZEN_LYRIC_BT)).stringValue.trim()
                val karaokeNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_DZEN_KARAOKE)).stringValue.trim()
//                val karaokeDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DZEN_KARAOKE_BT)).stringValue.trim()
                val chordsNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_DZEN_CHORDS)).stringValue.trim()
//                val chordsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DZEN_CHORDS_BT)).stringValue.trim()
                val year = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_YEAR)).stringValue.trim()
                val album = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_ALBUM)).stringValue.trim()
                val track = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_TRACK)).stringValue.trim()
                val tone = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_TONE)).stringValue.trim()
                val bpm = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_BPM)).stringValue.trim()
                val format = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_FORMAT)).stringValue.trim()

                result[SongField.AUTHOR] = author
                result[SongField.NAME] = songName
                if (date != "") result[SongField.DATE] = date
                if (time != "") result[SongField.TIME] = time
                if (year != "") result[SongField.YEAR] = year
                if (album != "") result[SongField.ALBUM] = album
                if (track != "") result[SongField.TRACK] = track
                if (tone != "") result[SongField.KEY] = tone
                if (bpm != "") result[SongField.BPM] = bpm
                if (format != "") result[SongField.FORMAT] = format
                if (boostyNormal != "") result[SongField.ID_BOOSTY] = boostyNormal
                if (lyricsNormal != "") result[SongField.ID_DZEN_LYRICS] = lyricsNormal
//                if (lyricsDelay != "") result[SongField.ID_DZEN_LYRICS_BT] = lyricsDelay
                if (karaokeNormal != "") result[SongField.ID_DZEN_KARAOKE] = karaokeNormal
//                if (karaokeDelay != "") result[SongField.ID_DZEN_KARAOKE_BT] = karaokeDelay
                if (chordsNormal != "") result[SongField.ID_DZEN_CHORDS] = chordsNormal
//                if (chordsDelay != "") result[SongField.ID_DZEN_CHORDS_BT] = chordsDelay

                return result
            }
        }

        @Suppress("unused")
        fun getSongVKDescription(
            song: SongRenderContext,
            fileName: String,
            spreadsheetDocument: SpreadsheetDocument?,
        ): Pair<String, String>? {
            val template = song.song.getVKGroupDescription()
            val author = song.song.author
            val songName = song.song.songName

            var date: String
            var time: String
            var boostyNormal: String
            var lyricsNormal: String
//            var lyricsDelay = ""
            var karaokeNormal: String
//            var karaokeDelay = ""
            var chordsNormal: String
//            var chordsDelay = ""

            if (spreadsheetDocument != null) {
                val (firstRow, row) = findRow(author, songName, spreadsheetDocument)
                if (row == null) {
                    return null
                } else {
                    date = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue.trim()
                    time =
                        row
                            .getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_TIME))
                            .stringValue
                            .trim()
                            .replace(":", ".")
                    boostyNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_BOOSTY)).stringValue.trim()
                    lyricsNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_DZEN_LYRIC)).stringValue.trim()
//                    lyricsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DZEN_LYRIC_BT)).stringValue.trim()
                    karaokeNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_DZEN_KARAOKE)).stringValue.trim()
//                    karaokeDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DZEN_KARAOKE_BT)).stringValue.trim()
                    chordsNormal = row.getCellByIndex(getColumnIndex(firstRow, ODS_COLUMN_DZEN_CHORDS)).stringValue.trim()
//                    chordsDelay = row.getCellByIndex(getColumnIndex(firstRow!!, ODS_COLUMN_DZEN_CHORDS_BT)).stringValue.trim()
                }
            } else {
                date = song.song.date
                time = song.song.time.replace(":", ".")
                boostyNormal = song.song.idBoosty
                lyricsNormal = song.song.idDzenLyrics
//                lyricsDelay = song.song.idDzenLyricsBt ?: ""
                karaokeNormal = song.song.idDzenKaraoke
//                karaokeDelay = song.song.idDzenKaraokeBt ?: ""
                chordsNormal = song.song.idDzenChords
//                chordsDelay = song.song.idDzenChordsBt ?: ""
            }

            if ("$lyricsNormal$karaokeNormal" != "") {
                val trueDate = "${date.substring(6)}.${date.substring(3,5)}.${date.substring(0,2)}"
                val name = fileName.replace("{REPLACE_DATE}", trueDate).replace("{REPLACE_TIME}", time).replace(" [lyrics]", "")
                val boostyNormalLink = if (boostyNormal == "") "" else URL_PREFIX_BOOSTY.replace("{REPLACE}", boostyNormal) + "\n"
                val lyricsNormalLink =
                    if (lyricsNormal ==
                        ""
                    ) {
                        ""
                    } else {
                        "Lyrics: " + URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", lyricsNormal) + "\n"
                    }
//                val lyricsDelayLink = if (lyricsDelay == "") "" else "Lyrics with delay: " + URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", lyricsDelay) +"\n"
                val karaokeNormalLink =
                    if (karaokeNormal ==
                        ""
                    ) {
                        ""
                    } else {
                        "Karaoke: " + URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", karaokeNormal) + "\n"
                    }
//                val karaokeDelayLink = if (karaokeDelay == "") "" else "Karaoke with delay: " + URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", karaokeDelay) +"\n"
                val chordsNormalLink =
                    if (chordsNormal ==
                        ""
                    ) {
                        ""
                    } else {
                        "Chords: " + URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", chordsNormal) + "\n"
                    }
//                val chordsDelayLink = if (chordsDelay == "") "" else "Chords with delay: " + URL_PREFIX_DZEN_PLAY.replace("{REPLACE}", chordsDelay) +"\n"
                val text =
                    template
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

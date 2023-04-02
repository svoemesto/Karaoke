import model.Song
import java.io.File
import org.odftoolkit.simple.SpreadsheetDocument
import org.odftoolkit.simple.table.Row

fun main() {
    val artist = "Павел Кашин"
    val songName = "Барышня"

    val (firstRow, row) = Ods.findRow(artist, songName)

    if (row == null) {
        println("Не найдена композиция $songName исполнителя $artist")
    } else {
        println("Композиция $songName исполнителя $artist найдена в строке с индексом ${row.rowIndex}")
        println("Публикация на Youtube: ${row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue}")
        println("Lyrics: ${URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC)).stringValue)}")

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

        fun getSongVKDescription(song: Song, fileName: String, spreadsheetDocument: SpreadsheetDocument): Pair<String, String>? {

            val template = song.getVKDescription()
            val author = song.settings.author
            val songName = song.settings.songName

            val (firstRow, row) = Ods.findRow(author, songName, spreadsheetDocument)
            if (row == null) {
                return null
            } else {
                val date = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_DATE)).stringValue.trim()
                val time = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_TIME)).stringValue.trim().replace(":",".")
                val boostyNormal = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_BOOSTY)).stringValue.trim()
                val lyricsNormal = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC)).stringValue.trim()
                val lyricsDelay = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_LIRIC_BT)).stringValue.trim()
                val karaokeNormal = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_KARAOKE)).stringValue.trim()
                val karaokeDelay = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_KARAOKE_BT)).stringValue.trim()
                val chordsNormal = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_CHORDS)).stringValue.trim()
                val chordsDelay = row.getCellByIndex(Ods.getColumnIndex(firstRow!!, ODS_COLUMN_YOUTUBE_CHORDS_BT)).stringValue.trim()

                if ("$lyricsNormal$lyricsDelay$karaokeNormal$karaokeDelay" != "") {
                    val trueDate = "${date.substring(6)}.${date.substring(3,5)}.${date.substring(0,2)}"
                    val name = fileName.replace("{REPLACE_DATE}",trueDate).replace("{REPLACE_TIME}",time).replace(" [lyrics]","")
                    val boostyNormalLink = if (boostyNormal == "") "" else URL_PREFIX_BOOSTY.replace("{REPLACE}", boostyNormal) +"\n"
                    val lyricsNormalLink = if (lyricsNormal == "") "" else "Lyrics: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", lyricsNormal) +"\n"
                    val lyricsDelayLink = if (lyricsDelay == "") "" else "Lyrics with delay: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", lyricsDelay) +"\n"
                    val karaokeNormalLink = if (karaokeNormal == "") "" else "Karaoke: " +  URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", karaokeNormal) +"\n"
                    val karaokeDelayLink = if (karaokeDelay == "") "" else "Karaoke with delay: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", karaokeDelay) +"\n"
                    val chordsNormalLink = if (chordsNormal == "") "" else "Chords: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", chordsNormal) +"\n"
                    val chordsDelayLink = if (chordsDelay == "") "" else "Chords with delay: " + URL_PREFIX_YOUTUBE_PLAY.replace("{REPLACE}", chordsDelay) +"\n"
                    val text = template
                        .replace("{REPLACE_BOOSTY_NORMAL}\n", boostyNormalLink)
                        .replace("{REPLACE_LYRICS_NORMAL}\n", lyricsNormalLink)
                        .replace("{REPLACE_KARAOKE_NORMAL}\n", karaokeNormalLink)
                        .replace("{REPLACE_CHORDS_NORMAL}\n", chordsNormalLink)
                        .replace("{REPLACE_LYRICS_DELAY}\n", lyricsDelayLink)
                        .replace("{REPLACE_KARAOKE_DELAY}\n", karaokeDelayLink)
                        .replace("{REPLACE_CHORDS_DELAY}\n", chordsDelayLink)

                    return Pair(text, name)
                }
                return null
            }
        }
    }
}
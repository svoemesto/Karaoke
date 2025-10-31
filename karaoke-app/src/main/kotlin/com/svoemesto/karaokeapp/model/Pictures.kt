package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getListHashes
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getTotalCount
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.imageio.ImageIO
import kotlin.text.lowercase

class Pictures(override val database: KaraokeConnection = WORKING_DATABASE) : Serializable, Comparable<Pictures>, KaraokeDbTable {

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "picture_name")
    var name: String = "Picture name"

    @KaraokeDbTableField(name = "picture_full")
    var full: String = ""
        set(value) {
            field = value
            try {
                val pictureBites = Base64.getDecoder().decode(value)
                val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
                val previewBi = if (bi.width > 400) resizeBufferedImage(bi, newW = 125, newH = 50) else resizeBufferedImage(bi, newW = 50, newH = 50)
                val iosPreview = ByteArrayOutputStream()
                ImageIO.write(previewBi, "png", iosPreview)
                val preview = Base64.getEncoder().encodeToString(iosPreview.toByteArray())
                if (this.preview != preview) this.preview = preview
            } catch (_: Exception) {

            }
        }

    @KaraokeDbTableField(name = "picture_preview")
    var preview: String = ""

    val author: String get() {
        val arr = name.split(" - ")
        return if (arr.size >= 3) arr[0] else name
    }

    val year: String get() {
        val arr = name.split(" - ")
        return if (arr.size >= 3) arr[1] else ""
    }

    val album: String get() {
        val arr = name.split(" - ")
        return if (arr.size >= 3) arr.filterIndexed { index, _ -> index >= 2 }.joinToString(" - ") else ""
    }

    val isAuthorPicture: Boolean get() = author.isNotBlank() && year.isBlank() && album.isBlank()
    val isAlbumPicture: Boolean get() = author.isNotBlank() && year.isNotBlank() && album.isNotBlank()

    val pathToFolder: String get() {
        return if (isAlbumPicture) {
            // Ищем первую песню автора, года и альбома
            val args = mapOf("author" to author, "song_year" to year, "album" to album, "limit" to "1")
            Settings.loadListFromDb(args = args, database = WORKING_DATABASE).firstOrNull()?.rootFolder ?: ""
        } else if (isAuthorPicture) {
            val args = mapOf("author" to author, "limit" to "1")
            Settings.loadListFromDb(args = args, database = WORKING_DATABASE).firstOrNull()?.let { sett ->
                File(sett.rootFolder).parent
            }?: ""
        } else ""
    }

    val fileName: String get() = if (isAuthorPicture) "LogoAuthor.png" else if (isAlbumPicture) "LogoAlbum.png" else ""

    override fun compareTo(other: Pictures): Int {
        return id.compareTo(other.id)
    }

    override fun getTableName(): String = TABLE_NAME

    override fun toDTO(): PicturesDTO {
        return PicturesDTO(
                id = id,
                name = name,
                preview = preview,
                full = full,
                author = author,
                year = year,
                album = album,
                isAuthorPicture = isAuthorPicture,
                isAlbumPicture = isAlbumPicture,
                pathToFolder = pathToFolder,
                fileName = fileName
        )
    }

    fun saveToDisk() {
        try {
            val pictureBites = Base64.getDecoder().decode(full)
            val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
            val fName = "/sm-karaoke/system/pictures/$name.png"
            val file = File(fName)
            ImageIO.write(bi, "png", file)
            runCommand(listOf("chmod", "666", fName))
        } catch (e: Exception) {
            println(e.message)
        }
    }

    companion object {

        const val TABLE_NAME = "tbl_pictures"

        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)
        @Suppress("unused")
        fun totalCount(database: KaraokeConnection): Int = getTotalCount(tableName = TABLE_NAME, database = database)
        @Suppress("unused")
        fun loadListIds(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Long> {
            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return emptyList()
            }
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                val limit = args["limit"]?.toInt() ?: 0
                val offset = args["offset"]?.toInt() ?: 0
                sql = "SELECT tbl_pictures.*" +
                        " FROM tbl_pictures"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("picture_name")) where += "LOWER(picture_name) LIKE '%${args["picture_name"]?.rightFileName()?.lowercase()}%'"
                if (where.isNotEmpty()) sql += " WHERE ${where.joinToString(" AND ")}"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"
//                sql = "select id from tbl_pictures"

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

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("picture_name")) where += "LOWER(picture_name) LIKE '%${whereArgs["picture_name"]?.lowercase()}%'"
            if (whereArgs.containsKey("name")) where += "LOWER(picture_name) = '${whereArgs["name"]?.lowercase()}'"
            return where
        }

        fun loadList(whereArgs: Map<String, String>,
                     limit: Int = 0,
                     offset: Int = 0,
                     database: KaraokeConnection): List<Pictures> {
            return KaraokeDbTable.loadList(
                clazz = Pictures::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database
            ).map { it as Pictures }
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database
            )
        }

        fun createNewPicture(newPicture: Pictures, database: KaraokeConnection): Pictures? {
            val newPictureInDb = KaraokeDbTable.createDbInstance(
                entity = newPicture,
                database = database
            ) as? Pictures?
            newPictureInDb?.let {
                return it
            }
            return null
        }

        fun getPictureById(id: Long, database: KaraokeConnection): Pictures? {
            return KaraokeDbTable.loadById(
                clazz = Pictures::class,
                tableName = TABLE_NAME,
                id = id,
                database = database
            ) as? Pictures?
        }

        fun getPictureByName(name: String, database: KaraokeConnection): Pictures? {

            return loadList(whereArgs = mapOf(Pair("name", name)), database = database).firstOrNull()

        }

    }
}
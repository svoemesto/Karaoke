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
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

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
            Settings.loadListFromDb(args = args, database = WORKING_DATABASE).firstOrNull()?.let { sett ->
                sett.rootFolder
            }?: ""
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

    override fun save() {

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return
        }
        val sql = "UPDATE ${getTableName()} SET " +
                "picture_name = ?, " +
                "picture_full = ?, " +
                "picture_preview = ? " +
                "WHERE id = ?"
        val ps = connection.prepareStatement(sql)
        var index = 1
        ps.setString(index, name)
        index++
        ps.setString(index, full)
        index++
        ps.setString(index, preview)
        index++
        ps.setLong(index, id)
        ps.executeUpdate()
        ps.close()

    }

//    override fun getSqlToInsert(): String {
//        val picture = this
//        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()
//
//        if (picture.id > 0) fieldsValues.add(Pair("id", picture.id))
//        fieldsValues.add(Pair("picture_name", picture.name))
//        fieldsValues.add(Pair("picture_full", picture.full))
//        fieldsValues.add(Pair("picture_preview", picture.preview))
//
//        return "INSERT INTO tbl_pictures (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second.toString().replace("'","''")}'"}.joinToString(", ")})"
//    }

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

        val TABLE_NAME = "tbl_pictures"

        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)
        fun totalCount(database: KaraokeConnection): Int = getTotalCount(tableName = TABLE_NAME, database = database)

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
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"
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



//        fun getDiff(entityA: KaraokeDbTable?, entityB: KaraokeDbTable?): List<RecordDiff> {
//            val result: MutableList<RecordDiff> = mutableListOf()
//            if (entityA != null && entityB != null) {
//                val kClassEntityA: KClass<out KaraokeDbTable> = entityA::class
//                val kClassEntityB: KClass<out KaraokeDbTable> = entityB::class
//                for (member in kClassEntityA.members) {
//                    if (member is kotlin.reflect.KProperty<*>) {
//                        val property = member
//                        val karaokeDbTableFieldAnnotation = property.findAnnotation<KaraokeDbTableField>()
//                        if (karaokeDbTableFieldAnnotation != null) {
//                            if (karaokeDbTableFieldAnnotation.useInDiff) {
//                                val fieldName = property.name
//                                val fieldValueA = property.getter.call(entityA)
//                                val fieldValueB = property.getter.call(entityB)
//                                if (fieldValueA != fieldValueB) {
//                                    result.add(RecordDiff(karaokeDbTableFieldAnnotation.name, fieldValueA, fieldValueB))
//                                }
//                            }
//
//                        }
//                    }
//                }
////                if (entityA.name != entityB.name) result.add(RecordDiff("picture_name", entityA.name, entityB.name))
////                if (entityA.full != entityB.full) result.add(RecordDiff("picture_full", entityA.full, entityB.full))
////                if (entityA.preview != entityB.preview) result.add(RecordDiff("picture_preview", entityA.preview, entityB.preview))
//            }
//            return result
//        }

        fun createDbInstance(picture: Pictures, database: KaraokeConnection) : Pictures? {
            val sql = picture.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return null
            }
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            try {
                ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            } catch (e: Exception) {
                // Проверяем последнее значение сиквенса и айдишника таблицы
                val statement = connection.createStatement()
                val rsLastId = statement.executeQuery("select max(id) as last_value from tbl_pictures;")
                val rsLastSeq = statement.executeQuery("select last_value from tbl_pictures_id_seq;")
                rsLastId.next()
                val lastId = rsLastId.getLong("last_value")
                rsLastSeq.next()
                val lastSeq = rsLastSeq.getLong("last_value")
                if (lastSeq < lastId) {
                    statement.execute("alter sequence tbl_pictures_id_seq restart with ${lastId+1};")
                    ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
                }
            }
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                picture.id = rs.getLong(1)
                picture
            } else null

            ps.close()

            if (result != null) updateRemotePictureFromLocalDatabase(result.id.toLong())

            return result

        }

        fun loadListFromDb(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Pictures> {

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
                if (args.containsKey("name")) where += "LOWER(picture_name) = '${args["name"]?.rightFileName()?.lowercase()}'"
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"

                rs = statement.executeQuery(sql)
                val result: MutableList<Pictures> = mutableListOf()
                while (rs.next()) {
                    val picture = Pictures(database)
                    picture.id = rs.getLong("id")
                    picture.name = rs.getString("picture_name")
                    picture.full = rs.getString("picture_full")
                    picture.preview = rs.getString("picture_preview")
                    result.add(picture)

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

        fun loadListDTOFromDb(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<PicturesDTO> {

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
                sql = "SELECT id, picture_name, picture_preview FROM tbl_pictures"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("picture_name")) where += "LOWER(picture_name) LIKE '%${args["picture_name"]?.rightFileName()?.lowercase()}%'"
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"

                rs = statement.executeQuery(sql)
                val result: MutableList<PicturesDTO> = mutableListOf()
                while (rs.next()) {
                    val pictureDTO = PicturesDTO(
                        id = rs.getLong("id"),
                        name = rs.getString("picture_name"),
                        preview = rs.getString("picture_preview")
                    )
                    result.add(pictureDTO)
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
        fun deleteFromDb(id: Int, database: KaraokeConnection) {

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return
            }
            val sql = "DELETE FROM tbl_pictures WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()

        }

        fun loadFromDbById(id: Long, database: KaraokeConnection): Pictures? {

            return Pictures.loadListFromDb(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }

        fun loadFromDbByName(name: String, database: KaraokeConnection): Pictures? {

            return Pictures.loadListFromDb(mapOf(Pair("picture_name", name)), database).firstOrNull()

        }

    }
}
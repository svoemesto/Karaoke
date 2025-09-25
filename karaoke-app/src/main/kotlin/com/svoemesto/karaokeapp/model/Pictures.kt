package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.imageio.ImageIO

class Pictures(val database: KaraokeConnection = WORKING_DATABASE) : Serializable, Comparable<Pictures> {

    var id: Int = 0
    var name: String = "Picture name"
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
    var preview: String = ""

    override fun compareTo(other: Pictures): Int {
        return id.compareTo(other.id)
    }

    fun save() {

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
            return
        }
        val sql = "UPDATE tbl_pictures SET " +
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
        ps.setInt(index, id)
        ps.executeUpdate()
        ps.close()

    }

    fun getSqlToInsert(): String {
        val picture = this
        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()

        if (picture.id > 0) fieldsValues.add(Pair("id", picture.id))
        fieldsValues.add(Pair("picture_name", picture.name))
        fieldsValues.add(Pair("picture_full", picture.full))
        fieldsValues.add(Pair("picture_preview", picture.preview))

        return "INSERT INTO tbl_pictures (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second.toString().replace("'","''")}'"}.joinToString(", ")})"

    }

    fun toDTO(): PicturesDTO {
        return PicturesDTO(
                id = id,
                name = name,
                preview = preview
        )
    }
    companion object {

        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? {
            var result: MutableList<RecordHash>? = mutableListOf()
            val sql = "SELECT id, recordhash FROM tbl_pictures $whereText"

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

        fun loadListIds(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Long> {
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

        fun totalCount(database: KaraokeConnection): Int {
            val sql = "SELECT COUNT(*) AS total_count FROM tbl_pictures;"
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

        fun getDiff(picA: Pictures?, picB: Pictures?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (picA != null && picB != null) {
                if (picA.name != picB.name) result.add(RecordDiff("picture_name", picA.name, picB.name))
                if (picA.full != picB.full) result.add(RecordDiff("picture_full", picA.full, picB.full))
                if (picA.preview != picB.preview) result.add(RecordDiff("picture_preview", picA.preview, picB.preview))
            }
            return result
        }

        fun createDbInstance(picture: Pictures, database: KaraokeConnection) : Pictures? {
            val sql = picture.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
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
                picture.id = rs.getInt(1)
                picture
            } else null

            ps.close()

            if (result != null) updateRemotePictureFromLocalDatabase(result.id.toLong())

            return result

        }

        fun loadListFromDb(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Pictures> {

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

                val limit = args["limit"]?.toInt() ?: 0
                val offset = args["offset"]?.toInt() ?: 0
                sql = "SELECT tbl_pictures.*" +
                        " FROM tbl_pictures"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("picture_name")) where += "LOWER(picture_name) LIKE '%${args["picture_name"]?.rightFileName()?.lowercase()}%'"
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"
                if (limit > 0) sql += " LIMIT $limit"
                if (offset > 0) sql += " OFFSET $offset"

                rs = statement.executeQuery(sql)
                val result: MutableList<Pictures> = mutableListOf()
                while (rs.next()) {
                    val picture = Pictures(database)
                    picture.id = rs.getInt("id")
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
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
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
                        id = rs.getInt("id"),
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
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
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
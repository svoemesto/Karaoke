package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import java.io.Serializable
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

class Pictures(val database: Connection = WORKING_DATABASE) : Serializable, Comparable<Pictures> {

    var id: Int = 0
    var name: String = "Picture name"
    var full: String = ""
    var preview: String = ""

    override fun compareTo(other: Pictures): Int {
        return id.compareTo(other.id)
    }

    fun save() {

        Class.forName("org.postgresql.Driver")
        val connection = DriverManager.getConnection(database.url, database.username, database.password)
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
        connection.close()

    }

    companion object {

        fun getDiff(picA: Pictures?, picB: Pictures?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (picA != null && picB != null) {
                if (picA.name != picB.name) result.add(RecordDiff("picture_name", picA.name, picB.name))
                if (picA.full != picB.full) result.add(RecordDiff("picture_full", picA.full, picB.full))
                if (picA.preview != picB.preview) result.add(RecordDiff("picture_preview", picA.preview, picB.preview))
            }
            return result
        }

        fun createDbInstance(picture: Pictures, database: Connection) : Pictures? {
            val sql =
                "INSERT INTO tbl_pictures (" +
                        "picture_name, " +
                        "picture_full, " +
                        "picture_preview " +
                        ") VALUES(" +
                        "'${picture.name.replace("'","''")}', " +
                        "'${picture.full}', " +
                        "'${picture.preview}'" +
                        ")"

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(database.url, database.username, database.password)
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                picture.id = rs.getInt(1)
                picture
            } else null

            ps.close()
            connection.close()

            return result

        }

        fun loadList(args: Map<String, String> = emptyMap(), database: Connection): List<Pictures> {

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(database.url, database.username, database.password)
            var statement: Statement? = null
            var rs: ResultSet? = null
            var sql: String
            val where: MutableList<String> = mutableListOf()

            try {
                statement = connection.createStatement()
                sql = "SELECT tbl_pictures.*" +
                        " FROM tbl_pictures"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("picture_name")) where += "picture_name = '${args["picture_name"]}'"
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"

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
                    connection?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun delete(id: Int, database: Connection) {

            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(database.url, database.username, database.password)
            val sql = "DELETE FROM tbl_pictures WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()
            connection.close()

        }

        fun load(id: Long, database: Connection): Pictures? {

            return Pictures.loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }

        fun load(name: String, database: Connection): Pictures? {

            return Pictures.loadList(mapOf(Pair("picture_name", name)), database).firstOrNull()

        }

    }
}
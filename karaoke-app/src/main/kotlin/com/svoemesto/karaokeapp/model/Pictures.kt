package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.rightFileName
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

class Pictures(val database: KaraokeConnection = WORKING_DATABASE) : Serializable, Comparable<Pictures> {

    var id: Int = 0
    var name: String = "Picture name"
    var full: String = ""
    var preview: String = ""

    override fun compareTo(other: Pictures): Int {
        return id.compareTo(other.id)
    }

    fun save() {

        val connection = database.getConnection()
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

        return "INSERT INTO tbl_pictures (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second.toString().rightFileName()}'"}.joinToString(", ")})"

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

        fun createDbInstance(picture: Pictures, database: KaraokeConnection) : Pictures? {
            val sql = picture.getSqlToInsert()

            val connection = database.getConnection()
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                picture.id = rs.getInt(1)
                picture
            } else null

            ps.close()

            return result

        }

        fun loadList(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Pictures> {

            val connection = database.getConnection()
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
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return emptyList()
        }

        fun delete(id: Int, database: KaraokeConnection) {

            val connection = database.getConnection()
            val sql = "DELETE FROM tbl_pictures WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()

        }

        fun load(id: Long, database: KaraokeConnection): Pictures? {

            return Pictures.loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }

        fun load(name: String, database: KaraokeConnection): Pictures? {

            return Pictures.loadList(mapOf(Pair("picture_name", name)), database).firstOrNull()

        }

    }
}
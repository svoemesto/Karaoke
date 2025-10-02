package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

class Uuids(var id: Int, var uuid: String, val database: KaraokeConnection = WORKING_DATABASE) : Serializable {
//    var id: Int = 0
//    var uuid: String = ""
    fun save() {

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return
        }
        val sql = "UPDATE tbl_uuids SET " +
                "uuid = ?, " +
                "WHERE id = ?"
        val ps = connection.prepareStatement(sql)
        var index = 1
        ps.setString(index, uuid)
        index++
        ps.setInt(index, id)
        ps.executeUpdate()
        ps.close()

    }

    fun getSqlToInsert(): String {
        val uuids = this
        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()

        fieldsValues.add(Pair("id", uuids.id))
        fieldsValues.add(Pair("uuid", uuids.uuid))

        return "INSERT INTO tbl_uuids (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second}'"}.joinToString(", ")})"

    }

    companion object {

        fun getDiff(uuidA: Uuids?, uuidB: Uuids?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (uuidA != null && uuidB != null) {
                if (uuidA.uuid != uuidB.uuid) result.add(RecordDiff("uuid", uuidA.uuid, uuidB.uuid))
            }
            return result
        }

        fun createDbInstance(uuid: Uuids, database: KaraokeConnection) : Uuids? {
            val sql = uuid.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return null
            }
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS ) //, Statement.RETURN_GENERATED_KEYS)
//            val rs = ps.generatedKeys
//
//            val result = if (rs.next()) {
//                picture.id = rs.getInt(1)
//                picture
//            } else null

            ps.close()

            return uuid

        }

        fun loadList(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Uuids> {

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
                sql = "SELECT tbl_uuids.*" +
                        " FROM tbl_uuids"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("uuid")) where += "uuid = '${args["uuid"]}'"
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"

                rs = statement.executeQuery(sql)
                val result: MutableList<Uuids> = mutableListOf()
                while (rs.next()) {
                    val picture = Uuids(rs.getInt("id"), rs.getString("uuid"), database)
                    result.add(picture)
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

        fun delete(id: Int, database: KaraokeConnection) {

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
                return
            }
            val sql = "DELETE FROM tbl_uuids WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()

        }

        fun load(id: Int, database: KaraokeConnection): Uuids? {

            return Uuids.loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }

    }

}
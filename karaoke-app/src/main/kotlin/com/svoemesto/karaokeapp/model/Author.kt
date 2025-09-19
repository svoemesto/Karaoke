package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.rightFileName
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class Author(val database: KaraokeConnection = WORKING_DATABASE) : Serializable, Comparable<Author> {

    var id: Int = 0
    var author: String = "Author name"
    var ymId: String = ""
    var lastAlbumYm: String = ""
    var lastAlbumProcessed: String = ""
    var watched: Boolean = false

    override fun compareTo(other: Author): Int {
        return author.compareTo(other.author)
    }

    fun save() {

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
            return
        }
        val sql = "UPDATE tbl_authors SET " +
                "author = ?, " +
                "ym_id = ?, " +
                "last_album_ym = ?, " +
                "last_album_processed = ?, " +
                "watched = ? " +
                "WHERE id = ?"
        val ps = connection.prepareStatement(sql)
        var index = 1
        ps.setString(index, author)
        index++
        ps.setString(index, ymId)
        index++
        ps.setString(index, lastAlbumYm)
        index++
        ps.setString(index, lastAlbumProcessed)
        index++
        ps.setBoolean(index, watched)
        index++
        ps.setInt(index, id)
        ps.executeUpdate()
        ps.close()

    }

    fun getSqlToInsert(): String {
        val author = this
        val fieldsValues: MutableList<Pair<String, Any>> = mutableListOf()

        if (author.id > 0) fieldsValues.add(Pair("id", author.id))
        fieldsValues.add(Pair("author", author.author))
        fieldsValues.add(Pair("ym_id", author.ymId))
        fieldsValues.add(Pair("last_album_ym", author.lastAlbumYm))
        fieldsValues.add(Pair("last_album_processed", author.lastAlbumProcessed))
        fieldsValues.add(Pair("watched", author.watched))

        return "INSERT INTO tbl_authors (${fieldsValues.map {it.first}.joinToString(", ")}) OVERRIDING SYSTEM VALUE VALUES(${fieldsValues.map {if (it.second is Long) "${it.second}" else "'${it.second.toString().replace("'","''")}'"}.joinToString(", ")})"

    }

    companion object {

        fun getDiff(authorA: Author?, authorB: Author?): List<RecordDiff> {
            val result: MutableList<RecordDiff> = mutableListOf()
            if (authorA != null && authorB != null) {
                if (authorA.author != authorB.author) result.add(RecordDiff("author", authorA.author, authorB.author))
                if (authorA.ymId != authorB.ymId) result.add(RecordDiff("ym_id", authorA.ymId, authorB.ymId))
                if (authorA.lastAlbumYm != authorB.lastAlbumYm) result.add(RecordDiff("last_album_ym", authorA.lastAlbumYm, authorB.lastAlbumYm))
                if (authorA.lastAlbumProcessed != authorB.lastAlbumProcessed) result.add(RecordDiff("last_album_processed", authorA.lastAlbumProcessed, authorB.lastAlbumProcessed))
                if (authorA.watched != authorB.watched) result.add(RecordDiff("watched", authorA.watched, authorB.watched))
            }
            return result
        }

        fun createDbInstance(author: Author, database: KaraokeConnection) : Author? {
            val sql = author.getSqlToInsert()

            val connection = database.getConnection()
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return null
            }
            val ps = connection.prepareStatement(sql)
            ps.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS)
            val rs = ps.generatedKeys

            val result = if (rs.next()) {
                author.id = rs.getInt(1)
                author
            } else null

            ps.close()

            return result

        }

        fun loadList(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Author> {

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
                sql = "SELECT tbl_authors.*" +
                        " FROM tbl_authors"
                if (args.containsKey("id")) where += "id=${args["id"]}"
                if (args.containsKey("author")) where += "author = '${args["author"]}'"
                if (args.containsKey("ym_id")) where += "ym_id = '${args["ym_id"]}'"
                if (args.containsKey("last_album_ym")) where += "last_album_ym = '${args["last_album_ym"]}'"
                if (args.containsKey("last_album_processed")) where += "last_album_processed = '${args["last_album_processed"]}'"
                if (args.containsKey("watched")) where += "watched = ${args["watched"]}"
                if (where.size > 0) sql += " WHERE ${where.joinToString(" AND ")}"

                rs = statement.executeQuery(sql)
                val result: MutableList<Author> = mutableListOf()
                while (rs.next()) {
                    val author = Author(database)
                    author.id = rs.getInt("id")
                    author.author = rs.getString("author")?:""
                    author.ymId = rs.getString("ym_id")?:""
                    author.lastAlbumYm = rs.getString("last_album_ym")?:""
                    author.lastAlbumProcessed = rs.getString("last_album_processed")?:""
                    author.watched = rs.getBoolean("watched")
                    result.add(author)

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
            if (connection == null) {
                println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных")
                return
            }
            val sql = "DELETE FROM tbl_authors WHERE id = ?"
            val ps = connection.prepareStatement(sql)
            var index = 1
            ps.setInt(index, id)
            ps.executeUpdate()
            ps.close()

        }

        fun load(id: Long, database: KaraokeConnection): Author? {

            return Author.loadList(mapOf(Pair("id", id.toString())), database).firstOrNull()

        }

        fun load(author: String, database: KaraokeConnection): Author? {

            return Author.loadList(mapOf(Pair("author", author)), database).firstOrNull()

        }

    }

}
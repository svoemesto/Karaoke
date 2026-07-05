package com.svoemesto.karaokeweb

import com.svoemesto.karaokeapp.KaraokeConnection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

// getWebEvents()/getStatBySong() перенесены в com.svoemesto.karaokeapp.model.StatsByEvents (нужны
// и для webvue3-админки, и для этой Thymeleaf-страницы) — не дублировать здесь снова, см.
// MainController.doStatBySong()/doWebEvents(). Здесь остались только счётчики для главной/закромов,
// у которых нет аналога в karaoke-app.
object StatBySong {

    fun getCountSongsExclusive(database: KaraokeConnection = WORKING_DATABASE): Int {
        val sql = """
            select count(DISTINCT id) as cnt
            from tbl_settings
            where exclusive = true AND id_sponsr != '' AND id_sponsr IS NOT NULL;
        """.trimIndent()
        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return 0
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                return rs.getInt("cnt")
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
        return 0
    }

    fun getCountSongsOnAir(database: KaraokeConnection = WORKING_DATABASE): Int {
        val sql = """
            select count(DISTINCT id) as cnt
            from tbl_settings
            where publish_date != ''
              and publish_date is not null
              and publish_time != ''
              and publish_time is not null
              and to_timestamp(CONCAT(publish_date, ' ', publish_time), 'DD.MM.YY HH24:MI') <= current_timestamp;
        """.trimIndent()
        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return 0
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                return rs.getInt("cnt")
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
        return 0
    }

    fun getCountSongsInCollection(database: KaraokeConnection = WORKING_DATABASE): Int {
        val sql = """
            select count(DISTINCT id) as cnt
            from tbl_settings
            where (publish_date != ''
              and publish_date is not null
              and publish_time != ''
              and publish_time is not null
              and to_timestamp(CONCAT(publish_date, ' ', publish_time), 'DD.MM.YY HH24:MI') <= current_timestamp)
              or (id_sponsr != '' AND id_sponsr IS NOT NULL);
        """.trimIndent()
        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return 0
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                return rs.getInt("cnt")
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
        return 0
    }
}

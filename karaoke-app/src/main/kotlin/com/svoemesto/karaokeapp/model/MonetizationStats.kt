package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

data class MonetizationSummaryDto(
    val revenueTotal: Double,
    val revenueSong: Double,
    val revenueSite: Double,
    val paidSongSubscriptions: Int,
    val paidSiteSubscriptions: Int,
    val pendingSubscriptions: Int,
    val failedSubscriptions: Int,
    // Активные СЕЙЧАС премиум-пользователи по источнику (пересекаются — юзер может подпадать под
    // несколько источников одновременно, поэтому сумма может не совпадать с общим числом премиумов).
    val activeManualPremium: Int,
    val activeSponsrPremium: Int,
    val activeSitePremium: Int,
    val totalSiteUsers: Int,
)

data class TopSubscribedSongDto(
    val songId: Long,
    val songName: String,
    val author: String,
    val subscriptionsCount: Int,
    val revenue: Double,
)

// Статистика монетизации (подписки — см. план монетизации) для дашборда «Статистика» в webvue3.
// Тот же ручной-JDBC стиль, что и StatBySong (scalarInt/scalarDouble — маленькие независимые
// хелперы, не переиспользуют private scalarInt оттуда, чтобы не тащить зависимость между файлами).
object MonetizationStats {

    private fun scalarInt(database: KaraokeConnection, sql: String): Int {
        val connection = database.getConnection() ?: return 0
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            return if (rs.next()) rs.getInt(1) else 0
        } catch (e: SQLException) {
            e.printStackTrace()
            return 0
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
    }

    private fun scalarDouble(database: KaraokeConnection, sql: String): Double {
        val connection = database.getConnection() ?: return 0.0
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            return if (rs.next()) rs.getDouble(1) else 0.0
        } catch (e: SQLException) {
            e.printStackTrace()
            return 0.0
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
    }

    fun getSummary(database: KaraokeConnection = WORKING_DATABASE): MonetizationSummaryDto {
        val paidWhere = "status = 'PAID'"
        return MonetizationSummaryDto(
            revenueTotal = scalarDouble(database, "select coalesce(sum(final_price), 0) from tbl_subscriptions where $paidWhere"),
            revenueSong = scalarDouble(database, "select coalesce(sum(final_price), 0) from tbl_subscriptions where $paidWhere and scope = 'SONG'"),
            revenueSite = scalarDouble(database, "select coalesce(sum(final_price), 0) from tbl_subscriptions where $paidWhere and scope = 'SITE'"),
            paidSongSubscriptions = scalarInt(database, "select count(*) from tbl_subscriptions where $paidWhere and scope = 'SONG'"),
            paidSiteSubscriptions = scalarInt(database, "select count(*) from tbl_subscriptions where $paidWhere and scope = 'SITE'"),
            pendingSubscriptions = scalarInt(database, "select count(*) from tbl_subscriptions where status in ('CREATED', 'PENDING')"),
            failedSubscriptions = scalarInt(database, "select count(*) from tbl_subscriptions where status = 'FAILED'"),
            activeManualPremium = scalarInt(database, "select count(*) from tbl_site_users where is_premium = true or is_permanent_premium = true"),
            activeSponsrPremium = scalarInt(database, "select count(*) from tbl_site_users where sponsr_premium_until is not null and sponsr_premium_until > now()"),
            activeSitePremium = scalarInt(database, "select count(*) from tbl_site_users where site_premium_until is not null and site_premium_until > now()"),
            totalSiteUsers = scalarInt(database, "select count(*) from tbl_site_users"),
        )
    }

    fun getTopSubscribedSongs(database: KaraokeConnection = WORKING_DATABASE, limit: Int = 20): List<TopSubscribedSongDto> {
        val result = mutableListOf<TopSubscribedSongDto>()
        val connection = database.getConnection() ?: return emptyList()
        val sql = """
            select s.id_song, st.song_name, st.song_author, count(*) as cnt, coalesce(sum(s.final_price), 0) as revenue
            from tbl_subscriptions s
            join tbl_settings st on st.id = s.id_song
            where s.status = 'PAID' and s.scope = 'SONG'
            group by s.id_song, st.song_name, st.song_author
            order by cnt desc
            limit $limit
        """.trimIndent()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(TopSubscribedSongDto(
                    songId = rs.getLong("id_song"),
                    songName = rs.getString("song_name") ?: "",
                    author = rs.getString("song_author") ?: "",
                    subscriptionsCount = rs.getInt("cnt"),
                    revenue = rs.getDouble("revenue"),
                ))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }
}

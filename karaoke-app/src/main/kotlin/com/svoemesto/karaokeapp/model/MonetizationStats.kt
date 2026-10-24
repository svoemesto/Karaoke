package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 * DTO для monetization summary: сериализуемое представление для API/UI.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
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

/**
 * DTO для top subscribed song: сериализуемое представление для API/UI.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class TopSubscribedSongDto(
    val songId: Long,
    val songName: String,
    val author: String,
    val subscriptionsCount: Int,
    val revenue: Double,
)

// Статистика монетизации (подписки — см. план монетизации) для дашборда «Статистика» в webvue3.
// Ручной-JDBC стиль, но БЕЗ локального гашения ошибок: сбой БД здесь обязан дойти до
// StatsController.respondCached, иначе дашборд покажет «нулевую выручку» вместо
// 503 stats.unavailable (BR-185, аудит #336).

/**
 * Singleton-объект Monetization Stats.
 *
 * Контракт ошибок (BR-185, аудит #336): и «нет соединения» ([KaraokeConnection.getConnection]
 * вернул null), и [SQLException] запроса пробрасываются наружу. Раньше оба случая превращались
 * в `0`/`0.0`/пустой список — неотличимо от настоящего нуля, причём нули попадали в
 * [com.svoemesto.karaokeapp.services.StatsCache] на 60 секунд. Владелец принимает решения
 * (выручка, число подписок) по этим цифрам, поэтому «не знаю» должно быть видно как ошибка.
 * Вызывающий слой ([com.svoemesto.karaokeapp.controllers.StatsController]) превращает
 * исключение в `503 stats.unavailable` по контракту FR-003.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
object MonetizationStats {
    /**
     * Все 11 скаляров одной строкой: один round-trip к БД и согласованный снимок
     * (раньше 11 последовательных запросов могли попасть в разные моменты времени).
     * Текст подзапросов повторяет прежние отдельные запросы один в один.
     */
    private val SUMMARY_SQL =
        """
        select
            coalesce((select sum(final_price) from tbl_subscriptions where status = 'PAID'), 0) as revenue_total,
            coalesce((select sum(final_price) from tbl_subscriptions where status = 'PAID' and scope = 'SONG'), 0) as revenue_song,
            coalesce((select sum(final_price) from tbl_subscriptions where status = 'PAID' and scope = 'SITE'), 0) as revenue_site,
            (select count(*) from tbl_subscriptions where status = 'PAID' and scope = 'SONG') as paid_song_subscriptions,
            (select count(*) from tbl_subscriptions where status = 'PAID' and scope = 'SITE') as paid_site_subscriptions,
            (select count(*) from tbl_subscriptions where status in ('CREATED', 'PENDING')) as pending_subscriptions,
            (select count(*) from tbl_subscriptions where status = 'FAILED') as failed_subscriptions,
            (select count(*) from tbl_site_users where is_premium = true or is_permanent_premium = true) as active_manual_premium,
            (select count(*) from tbl_site_users where sponsr_premium_until is not null and sponsr_premium_until > now()) as active_sponsr_premium,
            (select count(*) from tbl_site_users where site_premium_until is not null and site_premium_until > now()) as active_site_premium,
            (select count(*) from tbl_site_users) as total_site_users
        """.trimIndent()

    /**
     * Соединение или исключение: молчаливый `null` на этом уровне и есть корень BR-185 —
     * вызывающий не может отличить «нет данных» от «нет БД».
     */
    private fun connectionOrFail(
        database: KaraokeConnection,
        endpoint: String,
    ): Connection =
        database.getConnection()
            ?: throw SQLException("stats.unavailable: нет соединения с БД (endpoint=$endpoint, database=${database.name})")

    /** Закрывает ресурсы, не подменяя уже возникшую ошибку запроса ошибкой закрытия. */
    private fun closeQuietly(
        rs: ResultSet?,
        statement: Statement?,
    ) {
        try {
            rs?.close()
        } catch (_: SQLException) {
        }
        try {
            statement?.close()
        } catch (_: SQLException) {
        }
    }

    /**
     * Сводка монетизации для `/api/stats/monetization`.
     *
     * @throws SQLException нет соединения с БД или запрос не выполнен — вызывающий отдаёт 503
     *   `stats.unavailable` (FR-003), а не `200` с нулями (BR-185).
     */
    @Throws(SQLException::class)
    fun getSummary(database: KaraokeConnection = WORKING_DATABASE): MonetizationSummaryDto {
        val connection = connectionOrFail(database, "monetization")
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(SUMMARY_SQL)
            if (!rs.next()) {
                // Агрегат без GROUP BY всегда возвращает строку; её отсутствие — аномалия,
                // а не «все нули».
                throw SQLException("stats.unavailable: агрегат монетизации не вернул строку")
            }
            return MonetizationSummaryDto(
                revenueTotal = rs.getDouble("revenue_total"),
                revenueSong = rs.getDouble("revenue_song"),
                revenueSite = rs.getDouble("revenue_site"),
                paidSongSubscriptions = rs.getInt("paid_song_subscriptions"),
                paidSiteSubscriptions = rs.getInt("paid_site_subscriptions"),
                pendingSubscriptions = rs.getInt("pending_subscriptions"),
                failedSubscriptions = rs.getInt("failed_subscriptions"),
                activeManualPremium = rs.getInt("active_manual_premium"),
                activeSponsrPremium = rs.getInt("active_sponsr_premium"),
                activeSitePremium = rs.getInt("active_site_premium"),
                totalSiteUsers = rs.getInt("total_site_users"),
            )
        } finally {
            closeQuietly(rs, statement)
        }
    }

    /**
     * Топ песен по оплаченным подпискам для `/api/stats/monetization/top-songs`.
     *
     * @throws SQLException нет соединения с БД или запрос не выполнен — вызывающий отдаёт 503
     *   `stats.unavailable` (FR-003), а не `200` с пустым списком (BR-185).
     */
    @Throws(SQLException::class)
    fun getTopSubscribedSongs(
        database: KaraokeConnection = WORKING_DATABASE,
        limit: Int = 20,
    ): List<TopSubscribedSongDto> {
        val connection = connectionOrFail(database, "monetization/top-songs")
        val result = mutableListOf<TopSubscribedSongDto>()
        val sql =
            """
            select s.id_song, st.song_name, st.song_author, count(*) as cnt, coalesce(sum(s.final_price), 0) as revenue
            from tbl_subscriptions s
            join tbl_songs st on st.id = s.id_song
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
                result.add(
                    TopSubscribedSongDto(
                        songId = rs.getLong("id_song"),
                        songName = rs.getString("song_name") ?: "",
                        author = rs.getString("song_author") ?: "",
                        subscriptionsCount = rs.getInt("cnt"),
                        revenue = rs.getDouble("revenue"),
                    ),
                )
            }
            return result
        } finally {
            closeQuietly(rs, statement)
        }
    }
}

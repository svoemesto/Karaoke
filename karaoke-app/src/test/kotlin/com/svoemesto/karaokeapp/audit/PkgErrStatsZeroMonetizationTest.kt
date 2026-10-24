package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.MonetizationStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 * Регрессия BR-185 (аудит #336, пакет `PKG-ERR-STATS-ZERO`, OpenProject #385):
 * сбой БД в статистике монетизации больше не превращается в `0`/`0.0`/пустой список.
 *
 * Живая БД не нужна: [KaraokeConnection] подменяется [FakeKaraokeConnection], JDBC-объекты —
 * Mockito-моками. На коде «до» правки первый, второй и третий тесты падают на
 * «Expected SQLException to be thrown, but nothing was thrown» (модель молча возвращала нули).
 *
 * @see specs/385-pkg-err-stats-zero/report.md
 */
class PkgErrStatsZeroMonetizationTest {
    private class Jdbc {
        val connection: Connection = Mockito.mock(Connection::class.java)
        val statement: Statement = Mockito.mock(Statement::class.java)
        val resultSet: ResultSet = Mockito.mock(ResultSet::class.java)

        init {
            Mockito.`when`(connection.createStatement()).thenReturn(statement)
            Mockito.`when`(statement.executeQuery(Mockito.anyString())).thenReturn(resultSet)
        }
    }

    @Test
    fun `BR-185 нет соединения — сводка бросает SQLException, а не отдаёт нули`() {
        val db = FakeKaraokeConnection(jdbcConnection = null)

        // ДО: MonetizationStats.getSummary(db) возвращал MonetizationSummaryDto со всеми нулями,
        // StatsController отвечал 200 и клал нули в StatsCache на 60 секунд.
        val error = assertThrows<SQLException> { MonetizationStats.getSummary(db) }

        assertTrue(
            error.message.orEmpty().contains("stats.unavailable"),
            "причина должна нести контракт 503, а не выглядеть как обычный ноль: ${error.message}",
        )
    }

    @Test
    fun `BR-185 SQLException запроса не превращается в нулевую сводку`() {
        val jdbc = Jdbc()
        Mockito
            .`when`(jdbc.statement.executeQuery(Mockito.anyString()))
            .thenThrow(SQLException("too many clients already"))
        val db = FakeKaraokeConnection(jdbc.connection)

        // ДО: catch (e: SQLException) { e.printStackTrace(); return 0 } — исключение не доходило
        // до respondCached, и дашборд показывал нулевую выручку.
        val error = assertThrows<SQLException> { MonetizationStats.getSummary(db) }

        assertEquals("too many clients already", error.message)
    }

    @Test
    fun `BR-185 топ-песен при недоступной БД — исключение, а не пустой список`() {
        val db = FakeKaraokeConnection(jdbcConnection = null)

        // ДО: `database.getConnection() ?: return emptyList()` — «нет БД» выглядело как
        // «оплаченных подписок на песни нет».
        assertThrows<SQLException> { MonetizationStats.getTopSubscribedSongs(db) }
    }

    @Test
    fun `BR-185 сводка читается одним запросом и не подменяет значения нулями`() {
        val jdbc = Jdbc()
        Mockito.`when`(jdbc.resultSet.next()).thenReturn(true)
        Mockito.`when`(jdbc.resultSet.getDouble("revenue_total")).thenReturn(1234.5)
        Mockito.`when`(jdbc.resultSet.getDouble("revenue_song")).thenReturn(1000.0)
        Mockito.`when`(jdbc.resultSet.getDouble("revenue_site")).thenReturn(234.5)
        Mockito.`when`(jdbc.resultSet.getInt("paid_song_subscriptions")).thenReturn(7)
        Mockito.`when`(jdbc.resultSet.getInt("paid_site_subscriptions")).thenReturn(3)
        Mockito.`when`(jdbc.resultSet.getInt("pending_subscriptions")).thenReturn(2)
        Mockito.`when`(jdbc.resultSet.getInt("failed_subscriptions")).thenReturn(1)
        Mockito.`when`(jdbc.resultSet.getInt("active_manual_premium")).thenReturn(5)
        Mockito.`when`(jdbc.resultSet.getInt("active_sponsr_premium")).thenReturn(4)
        Mockito.`when`(jdbc.resultSet.getInt("active_site_premium")).thenReturn(6)
        Mockito.`when`(jdbc.resultSet.getInt("total_site_users")).thenReturn(100)

        val summary = MonetizationStats.getSummary(FakeKaraokeConnection(jdbc.connection))

        assertEquals(1234.5, summary.revenueTotal)
        assertEquals(1000.0, summary.revenueSong)
        assertEquals(234.5, summary.revenueSite)
        assertEquals(7, summary.paidSongSubscriptions)
        assertEquals(3, summary.paidSiteSubscriptions)
        assertEquals(2, summary.pendingSubscriptions)
        assertEquals(1, summary.failedSubscriptions)
        assertEquals(5, summary.activeManualPremium)
        assertEquals(4, summary.activeSponsrPremium)
        assertEquals(6, summary.activeSitePremium)
        assertEquals(100, summary.totalSiteUsers)
        // Один round-trip вместо 11: цифры относятся к одному моменту времени, и ошибка
        // любого из них не может дать «частично нулевую» сводку.
        Mockito.verify(jdbc.connection, Mockito.times(1)).createStatement()
        Mockito.verify(jdbc.statement, Mockito.times(1)).executeQuery(Mockito.anyString())
    }
}

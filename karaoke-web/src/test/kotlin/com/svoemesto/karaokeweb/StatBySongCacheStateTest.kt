package com.svoemesto.karaokeweb

import com.svoemesto.karaokeapp.KaraokeConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

/**
 * Регрессия BR-016 (аудит #336, пакет `PKG-ERR-STATS-ZERO`, OpenProject #385): кеш счётчиков
 * главной различает «нет данных» (`warming_up`), «ошибку» (`error`) и настоящий ноль (`ready`).
 *
 * Тесты работают с изолированным `StatBySongCache` через шов `refreshCacheInto`, поэтому не
 * трогают глобальное состояние `object StatBySong` и не зависят от порядка тестов. На коде «до»
 * правки файл не компилируется (типов состояния и шва ещё нет) — «до/после» по поведению
 * зафиксировано в [StatBySongCacheRegressionTest].
 *
 * @see specs/385-pkg-err-stats-zero/report.md
 */
class StatBySongCacheStateTest {
    private class FakeDb(
        private val jdbc: Connection?,
    ) : KaraokeConnection("jdbc:fake:test", "test", "test", "FAKE") {
        override fun getConnection(): Connection? = jdbc
    }

    private fun dbWithCounts(
        total: Int,
        collection: Int,
        freeNow: Int,
    ): KaraokeConnection {
        val resultSet = Mockito.mock(ResultSet::class.java)
        Mockito.`when`(resultSet.next()).thenReturn(true)
        Mockito.`when`(resultSet.getInt("cnt")).thenReturn(total, collection, freeNow)
        val statement = Mockito.mock(Statement::class.java)
        Mockito.`when`(statement.executeQuery(Mockito.anyString())).thenReturn(resultSet)
        val connection = Mockito.mock(Connection::class.java)
        Mockito.`when`(connection.createStatement()).thenReturn(statement)
        return FakeDb(connection)
    }

    @Test
    fun `BR-016 холодный кеш — нет данных, а не ноль`() {
        val cache = StatBySongCache()

        assertEquals(StatsCacheState.WARMING_UP, cache.current().state)
        assertFalse(cache.current().hasData, "до первого успешного пересчёта данных нет")
        assertEquals(0, cache.current().total, "fallback для шаблона остаётся нулевым")
    }

    @Test
    fun `BR-016 неудачный первый пересчёт не выдаёт себя за настоящий ноль и не залипает`() {
        val cache = StatBySongCache()

        StatBySong.refreshCacheInto(cache, FakeDb(null))

        assertEquals(StatsCacheState.ERROR, cache.current().state)
        assertFalse(cache.current().hasData, "после сбоя следующий запрос должен повторить пересчёт")
    }

    @Test
    fun `BR-016 настоящий ноль отличим от отсутствия данных`() {
        val cache = StatBySongCache()

        StatBySong.refreshCacheInto(cache, dbWithCounts(total = 0, collection = 0, freeNow = 0))

        assertEquals(StatsCacheState.READY, cache.current().state)
        assertTrue(cache.current().hasData)
        assertEquals(0, cache.current().total, "при ready ноль — это действительно ноль")
    }

    @Test
    fun `BR-016 сбой после успеха сохраняет прежний снимок и помечает состояние error`() {
        val cache = StatBySongCache()
        StatBySong.refreshCacheInto(cache, dbWithCounts(total = 42, collection = 10, freeNow = 4))

        StatBySong.refreshCacheInto(cache, FakeDb(null))

        val counters = cache.current()
        assertEquals(StatsCacheState.ERROR, counters.state)
        assertTrue(counters.hasData, "последние успешные цифры остаются доступными")
        assertEquals(42, counters.total)
        assertEquals(10, counters.collection)
        assertEquals(4, counters.freeNow)
        assertEquals(6, counters.subscriptionOnly)
        assertEquals(32, counters.inWork)
    }

    @Test
    fun `BR-016 восстановление БД возвращает состояние ready и свежие цифры`() {
        val cache = StatBySongCache()
        StatBySong.refreshCacheInto(cache, dbWithCounts(total = 42, collection = 10, freeNow = 4))
        StatBySong.refreshCacheInto(cache, FakeDb(null))

        StatBySong.refreshCacheInto(cache, dbWithCounts(total = 43, collection = 11, freeNow = 5))

        assertEquals(StatsCacheState.READY, cache.current().state)
        assertEquals(43, cache.current().total)
        assertEquals(11, cache.current().collection)
        assertEquals(5, cache.current().freeNow)
        assertEquals(6, cache.current().subscriptionOnly)
        assertEquals(32, cache.current().inWork)
    }
}

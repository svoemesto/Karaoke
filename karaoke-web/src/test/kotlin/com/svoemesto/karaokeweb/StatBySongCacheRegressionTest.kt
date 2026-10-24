package com.svoemesto.karaokeweb

import com.svoemesto.karaokeapp.KaraokeConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

/**
 * Регрессия BR-016 (аудит #336, пакет `PKG-ERR-STATS-ZERO`, OpenProject #385) на публичном API
 * `StatBySong`: неудачный пересчёт не подменяет ранее посчитанные счётчики нулями.
 *
 * Файл намеренно не использует новые типы (`StatsCacheState`, `StatBySongCache`) — он
 * компилируется и на коде «до» правки, поэтому служит доказательством «до/после»:
 * на старом `runCountQuery` возвращал `0` при сбое, `refreshCache` публиковал эти нули,
 * и вторая группа assertEquals падала (`expected: 42, actual: 0`).
 *
 * @see specs/385-pkg-err-stats-zero/report.md
 */
class StatBySongCacheRegressionTest {
    private class FakeDb(
        private val jdbc: Connection?,
    ) : KaraokeConnection("jdbc:fake:test", "test", "test", "FAKE") {
        override fun getConnection(): Connection? = jdbc
    }

    /** Три запроса refreshCache подряд получают заданные значения `cnt`. */
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
    fun `BR-016 сбой БД не обнуляет уже посчитанные счётчики главной`() {
        StatBySong.refreshCache(dbWithCounts(total = 42, collection = 10, freeNow = 4))

        // Явная «мёртвая» БД: дефолтный параметр — глобальный WORKING_DATABASE, который в
        // unit-тесте не инициализирован (WEB_WORK_ON_SERVER); на чтение это не влияет.
        assertEquals(42, StatBySong.getCountSongsTotal(FakeDb(null)))
        assertEquals(10, StatBySong.getCountSongsInCollection(FakeDb(null)))
        assertEquals(4, StatBySong.getCountSongsFreeNow(FakeDb(null)))
        assertEquals(6, StatBySong.getCountSongsSubscriptionOnly(FakeDb(null)))
        assertEquals(32, StatBySong.getCountSongsInWork(FakeDb(null)))

        // Нет соединения: все три runCountQuery вернут null.
        StatBySong.refreshCache(FakeDb(null))

        // ДО правки: 0/0/0/0/0 — тот же экран, что и «в каталоге правда ноль песен».
        assertEquals(42, StatBySong.getCountSongsTotal(FakeDb(null)), "сбой БД не должен подменять total нулём")
        assertEquals(10, StatBySong.getCountSongsInCollection(FakeDb(null)), "сбой БД не должен подменять collection нулём")
        assertEquals(4, StatBySong.getCountSongsFreeNow(FakeDb(null)), "сбой БД не должен подменять freeNow нулём")
        assertEquals(6, StatBySong.getCountSongsSubscriptionOnly(FakeDb(null)))
        assertEquals(32, StatBySong.getCountSongsInWork(FakeDb(null)))
    }
}

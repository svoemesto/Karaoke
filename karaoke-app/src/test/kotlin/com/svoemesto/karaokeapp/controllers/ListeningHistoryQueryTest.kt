package com.svoemesto.karaokeapp.controllers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-112 (аудит #336, PKG-S2-11): пагинация истории прослушиваний
 * выполняется в SQL, а не после полной загрузки таблицы в память.
 *
 * До правки контроллер вызывал `KaraokeDbTable.loadList` без LIMIT, грузил все
 * песни по всем `songId` и резал страницу в Kotlin. Тест фиксирует контракт
 * SQL-построителя: LIMIT/OFFSET присутствуют, фильтры биндятся как `?` и не
 * склеиваются со значениями.
 */
class ListeningHistoryQueryTest {
    @Test
    fun `page query limits rows and binds filters`() {
        val q =
            buildListeningHistoryQuery(
                filterUserId = 7L,
                filterSongId = 42L,
                filterLastPlayedFrom = "2026-01-01",
                filterLastPlayedTo = null,
                sortColumn = "h.play_count",
                sortDirection = "ASC",
            )

        assertTrue(q.pageSql.contains("LIMIT ? OFFSET ?"), "нет LIMIT/OFFSET: ${q.pageSql}")
        assertTrue(q.pageSql.contains("EXISTS (SELECT 1 FROM tbl_songs"), "нет EXISTS-фильтра")
        assertTrue(q.pageSql.contains("ORDER BY h.play_count ASC"))
        assertTrue(q.countSql.startsWith("SELECT count(*)"))
        assertEquals(3, q.params.size)
        assertFalse(q.pageSql.contains("7"), "значение фильтра не должно склеиваться в SQL")
        assertTrue(q.pageSql.contains("h.site_user_id = ?"))
    }

    @Test
    fun `no filters yields count and page over all existing songs`() {
        val q =
            buildListeningHistoryQuery(
                filterUserId = null,
                filterSongId = null,
                filterLastPlayedFrom = " ",
                filterLastPlayedTo = null,
                sortColumn = "h.last_played_at",
                sortDirection = "DESC",
            )
        assertTrue(q.params.isEmpty())
        assertTrue(q.pageSql.contains("LIMIT ? OFFSET ?"))
    }
}

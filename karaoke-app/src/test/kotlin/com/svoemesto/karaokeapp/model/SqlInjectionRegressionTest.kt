package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Детали параметризации пакета `PKG-SEC-SQLI` (аудит #336, OpenProject #383):
 * белые списки, разбор `ids` в `Long` и отказ (`1=0`) на невалидном значении.
 *
 * Базовый сценарий «payload в фильтре» — в [SqlInjectionBeforeAfterTest];
 * здесь проверяются ветки, которые нельзя выразить одним payload:
 * числовые семейства, `IN (?, …)` и `album_type` по белому списку.
 *
 * @see SqlWhere
 * @see SqlWhereBuilder
 */
class SqlInjectionRegressionTest {
    @Test
    fun `BR-028 — ids процесса разбирается в Long и биндится по одному`() {
        val jdbc = MockJdbc()
        KaraokeProcess.loadList(
            args = mapOf("ids" to "1,2,3"),
            database = FakeKaraokeConnection(jdbc.connection),
        )
        // BR-037 (аудит #336, пакет PKG-S3-22): добавлен постоянный предикат
        // `process_deleted_at IS NULL` — soft-deleted задания больше не возвращаются.
        assertEquals(
            "SELECT tbl_processes.* FROM tbl_processes WHERE tbl_processes.id IN (?, ?, ?) " +
                "AND process_deleted_at IS NULL",
            jdbc.preparedSql(),
        )
        Mockito.verify(jdbc.prepared).setLong(1, 1L)
        Mockito.verify(jdbc.prepared).setLong(2, 2L)
        Mockito.verify(jdbc.prepared).setLong(3, 3L)

        val injected = MockJdbc()
        KaraokeProcess.loadList(
            args = mapOf("ids" to "1) OR 1=1"),
            database = FakeKaraokeConnection(injected.connection),
        )
        assertTrue(injected.preparedSql().contains("1=0"), "невалидный ids даёт заведомо пустую выборку")
    }

    @Test
    fun `BR-100 — getWhereList отдаёт только константы и параметры`() {
        val payload = SQLI_TAUTOLOGY
        val args =
            mapOf(
                "song_name" to payload,
                "status" to payload,
                "flag_free" to payload,
                "filter_rate" to "1 OR 1=1",
                "publish_date" to ">01.01.24' OR '1'='1",
            )
        val where = Song.getWhereList(tableName = "tbl_songs", args = args, sync = false)
        val sql = "SELECT * FROM tbl_songs WHERE ${where.joinToString(" AND ")}"
        assertFalse(sql.contains(payload))
        assertFalse(sql.contains("1 OR 1=1"))
        assertFalse(hasOperatorOutsideLiterals(sql), "пользовательские значения не попадают в SQL-структуру")
        // строковое семейство — параметр; числовое/флаговое с мусором — константа 1=0.
        assertTrue(where.params.contains("%$payload%"))
        assertTrue(where.fragments.contains("1=0"))
        assertTrue(where.fragments.any { it.contains("to_date(publish_date") }, "дата сравнения — параметр, формат — константа")
    }

    @Test
    fun `BR-100 — числовые фильтры Song принимают только Long`() {
        val jdbc = MockJdbc()
        Song.countMatchingAttr(
            args = mapOf("ids" to "1,2", "filter_rate" to ">=5"),
            database = FakeKaraokeConnection(jdbc.connection),
        )
        assertEquals(
            "SELECT COUNT(*) AS cnt FROM tbl_songs WHERE tbl_songs.id IN (?, ?) AND tbl_songs.rate >= ?",
            jdbc.preparedSql(),
        )
        Mockito.verify(jdbc.prepared).setLong(1, 1L)
        Mockito.verify(jdbc.prepared).setLong(2, 2L)
        Mockito.verify(jdbc.prepared).setLong(3, 5L)

        val injected = MockJdbc()
        Song.countMatchingAttr(
            args = mapOf("ids" to "1) OR 1=1"),
            database = FakeKaraokeConnection(injected.connection),
        )
        assertTrue(injected.preparedSql().endsWith("1=0"))
    }

    @Test
    fun `BR-175 — невалидный id картинки даёт пустую выборку`() {
        val jdbc = MockJdbc()
        Pictures.loadList(
            whereArgs = mapOf("id" to "1 OR 1=1"),
            database = FakeKaraokeConnection(jdbc.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
            ignoreUseInList = true,
        )
        assertTrue(jdbc.statementSql().endsWith("1=0"))
    }

    @Test
    fun `BR-182 — невалидный id автора даёт пустую выборку`() {
        val jdbc = MockJdbc()
        Author.loadList(
            whereArgs = mapOf("id" to "1 OR 1=1"),
            database = FakeKaraokeConnection(jdbc.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
            ignoreUseInList = true,
        )
        assertTrue(jdbc.statementSql().endsWith("1=0"))
    }

    @Test
    fun `BR-237 — album_type только из AlbumType, иначе пустая выборка`() {
        val valid = MockJdbc()
        Album.loadList(
            whereArgs = mapOf("album_type" to "live"),
            database = FakeKaraokeConnection(valid.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
        )
        assertEquals("SELECT tbl_albums.* FROM tbl_albums WHERE album_type = ?", valid.preparedSql())
        Mockito.verify(valid.prepared).setString(1, "live")

        val injected = MockJdbc()
        Album.loadList(
            whereArgs = mapOf("album_type" to SQLI_TAUTOLOGY),
            database = FakeKaraokeConnection(injected.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
        )
        assertTrue(injected.statementSql().endsWith("1=0"))
    }
}

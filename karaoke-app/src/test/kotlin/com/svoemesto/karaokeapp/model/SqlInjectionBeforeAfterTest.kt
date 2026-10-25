package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Тесты «до/после» пакета `PKG-SEC-SQLI` (аудит #336, OpenProject #383):
 * BR-028, BR-100, BR-175, BR-182, BR-237.
 *
 * **До фикса** (ветка `master` @ 07126462) эти тесты падают:
 * - BR-028/175/182/237: старый код исполнял запрос через `Statement`, поэтому
 *   `PreparedStatement` не вызывался вовсе (`WantedButNotInvoked`), а в
 *   `statement.executeQuery` уходил SQL с payload;
 * - BR-100: `countMatchingAttr` уже использовал `prepareStatement`, но склеивал
 *   `WHERE` конкатенацией — `assertFalse(sql.contains(payload))` падал.
 *
 * **После фикса** — проходят: значения уходят в `?`, SQL содержит только
 * константы. В каждом тесте рядом лежит `legacy*Sql(...)` — дословная
 * репродукция старого построителя, доказывающая, что payload выходил за
 * пределы строкового литерала ([hasOperatorOutsideLiterals]). Живая БД не
 * нужна: JDBC подменён [MockJdbc], `ResultSet.next()` = `false`.
 *
 * @see SqlWhere
 */
class SqlInjectionBeforeAfterTest {
    private val payload = SQLI_TAUTOLOGY

    @Test
    fun `BR-028 — фильтры процессов не попадают в текст SQL`() {
        val legacy = legacyProcessSql(mapOf("process_name" to payload, "process_status" to payload))
        assertTrue(hasOperatorOutsideLiterals(legacy), "старый SQL: payload выходит за пределы литерала")

        val jdbc = MockJdbc()
        KaraokeProcess.loadList(
            args = mapOf("process_name" to payload, "process_status" to payload),
            database = FakeKaraokeConnection(jdbc.connection),
        )

        val sql = jdbc.preparedSql()
        assertFalse(sql.contains(payload), "в SQL не должно быть пользовательских значений")
        // BR-037 (аудит #336, пакет PKG-S3-22): к фильтрам добавлен постоянный предикат
        // `process_deleted_at IS NULL` — soft-deleted задания больше не возвращаются.
        assertEquals(
            "SELECT tbl_processes.* FROM tbl_processes WHERE process_name LIKE ? AND process_status = ? " +
                "AND process_deleted_at IS NULL",
            sql,
        )
        assertFalse(hasOperatorOutsideLiterals(sql))
        Mockito.verify(jdbc.prepared).setString(1, "%$payload%")
        Mockito.verify(jdbc.prepared).setString(2, payload)
    }

    @Test
    fun `BR-100 — фильтр status песен биндится, а не склеивается`() {
        val legacy = legacySongSql(mapOf("status" to payload))
        assertTrue(hasOperatorOutsideLiterals(legacy), "старый SQL: payload выходит за пределы литерала")

        val jdbc = MockJdbc()
        Song.countMatchingAttr(args = mapOf("status" to payload), database = FakeKaraokeConnection(jdbc.connection))

        val sql = jdbc.preparedSql()
        assertFalse(sql.contains(payload), "в SQL не должно быть пользовательских значений")
        assertEquals("SELECT COUNT(*) AS cnt FROM tbl_songs WHERE status LIKE ?", sql)
        Mockito.verify(jdbc.prepared).setString(1, "%$payload%")
    }

    @Test
    fun `BR-175 — фильтр picture_name картинок биндится`() {
        val legacy = legacyPicturesSql(mapOf("picture_name" to payload))
        assertTrue(hasOperatorOutsideLiterals(legacy), "старый SQL: payload выходит за пределы литерала")

        val jdbc = MockJdbc()
        Pictures.loadList(
            whereArgs = mapOf("picture_name" to payload),
            database = FakeKaraokeConnection(jdbc.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
            ignoreUseInList = true,
        )

        val sql = jdbc.preparedSql()
        assertFalse(sql.contains(payload))
        assertEquals("SELECT tbl_pictures.* FROM tbl_pictures WHERE LOWER(picture_name) LIKE ?", sql)
        Mockito.verify(jdbc.prepared).setString(1, "%${payload.lowercase()}%")
    }

    @Test
    fun `BR-182 — фильтры авторов биндятся`() {
        val legacy = legacyAuthorSql(mapOf("author" to payload, "ym_id" to payload))
        assertTrue(hasOperatorOutsideLiterals(legacy), "старый SQL: payload выходит за пределы литерала")

        val jdbc = MockJdbc()
        Author.loadList(
            whereArgs = mapOf("author" to payload, "ym_id" to payload),
            database = FakeKaraokeConnection(jdbc.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
            ignoreUseInList = true,
        )

        val sql = jdbc.preparedSql()
        assertFalse(sql.contains(payload))
        assertEquals("SELECT tbl_authors.* FROM tbl_authors WHERE author = ? AND ym_id = ?", sql)
        Mockito.verify(jdbc.prepared).setString(1, payload)
        Mockito.verify(jdbc.prepared).setString(2, payload)
    }

    @Test
    fun `BR-237 — фильтр названия альбома биндится`() {
        val legacy = legacyAlbumSql(mapOf("name_search" to payload))
        assertTrue(hasOperatorOutsideLiterals(legacy), "старый SQL: payload выходит за пределы литерала")

        val jdbc = MockJdbc()
        Album.loadList(
            whereArgs = mapOf("name_search" to payload),
            database = FakeKaraokeConnection(jdbc.connection),
            storageService = MockJdbc.storageService(),
            storageApiClient = MockJdbc.storageApiClient(),
        )

        val sql = jdbc.preparedSql()
        assertFalse(sql.contains(payload))
        assertEquals("SELECT tbl_albums.* FROM tbl_albums WHERE LOWER(name) LIKE ?", sql)
        Mockito.verify(jdbc.prepared).setString(1, "%${payload.lowercase()}%")
    }

    // --- Репродукция старого кода (ветка master @ 07126462) ---

    private fun legacyProcessSql(args: Map<String, String>): String {
        val where = mutableListOf<String>()
        args["process_name"]?.let { where += "process_name LIKE '%$it%'" }
        args["process_status"]?.let { where += "process_status = '$it'" }
        return "SELECT tbl_processes.* FROM tbl_processes WHERE ${where.joinToString(" AND ")}"
    }

    private fun legacySongSql(args: Map<String, String>): String {
        val where = mutableListOf<String>()
        args["status"]?.let { where += "status LIKE '%$it%'" }
        return "SELECT COUNT(*) AS cnt FROM tbl_songs WHERE ${where.joinToString(" AND ")}"
    }

    private fun legacyPicturesSql(args: Map<String, String>): String {
        val where = mutableListOf<String>()
        args["picture_name"]?.let { where += "LOWER(picture_name) LIKE '%${it.lowercase()}%'" }
        return "SELECT tbl_pictures.* FROM tbl_pictures WHERE ${where.joinToString(" AND ")}"
    }

    private fun legacyAuthorSql(args: Map<String, String>): String {
        val where = mutableListOf<String>()
        args["author"]?.let { where += "author = '$it'" }
        args["ym_id"]?.let { where += "ym_id = '$it'" }
        return "SELECT tbl_authors.* FROM tbl_authors WHERE ${where.joinToString(" AND ")}"
    }

    private fun legacyAlbumSql(args: Map<String, String>): String {
        val where = mutableListOf<String>()
        args["name_search"]?.let { where += "LOWER(name) LIKE '%${it.lowercase()}%'" }
        return "SELECT tbl_albums.* FROM tbl_albums WHERE ${where.joinToString(" AND ")}"
    }
}

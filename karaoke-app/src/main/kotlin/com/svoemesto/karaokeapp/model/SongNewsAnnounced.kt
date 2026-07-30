package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types

/**
 * Бухгалтерия «по какой песне уже принято решение об анонсе» (`tbl_song_news_announced`,
 * specs/083-auto-news-song-release). Строка означает либо «новость реально создана» (`newsId`
 * заполнен), либо «песня попала в разовый backfill при включении фичи и анонс намеренно не
 * создавался» (`newsId = null`, см. [SongReleaseAnnouncementService.backfillExistingReadySongs]).
 *
 * Намеренно НЕ реализует [KaraokeDbTable] и НЕ регистрируется в `SyncRegistry` — таблица не
 * участвует в LOCAL↔SERVER синхронизации вообще (данные значимы только там, где реально выполняется
 * `doChangeRecords`, т.е. на PROD). Сырой JDBC напрямую, как и остальной доступ к БД в проекте.
 *
 * @see docs/features/dual-db-sync.md
 */
object SongNewsAnnounced {
    private const val TABLE_NAME = "tbl_song_news_announced"

    /** Есть ли уже решение по этой песне (реальная новость либо backfill-отметка без новости). */
    fun isAnnounced(
        songId: Long,
        database: KaraokeConnection,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            connection.prepareStatement("SELECT 1 FROM $TABLE_NAME WHERE song_id = ?").use { ps ->
                ps.setLong(1, songId)
                ps.executeQuery().use { rs -> rs.next() }
            }
        } catch (e: SQLException) {
            println("SongNewsAnnounced.isAnnounced SQLException: ${e.message}")
            false
        }
    }

    /**
     * Идемпотентно фиксирует решение по песне. `ON CONFLICT (song_id) DO NOTHING` — повторный вызов
     * (например, из нескольких `/changerecords`-вызовов в рамках одного admin-триггерного «1 клик»)
     * не падает и не создаёт дубль.
     */
    fun markAnnounced(
        songId: Long,
        newsId: Long?,
        database: KaraokeConnection,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            connection
                .prepareStatement(
                    "INSERT INTO $TABLE_NAME (song_id, news_id, created_at) VALUES (?, ?, ?) ON CONFLICT (song_id) DO NOTHING",
                ).use { ps ->
                    ps.setLong(1, songId)
                    if (newsId != null) ps.setLong(2, newsId) else ps.setNull(2, Types.BIGINT)
                    ps.setTimestamp(3, Timestamp(System.currentTimeMillis()))
                    ps.executeUpdate() > 0
                }
        } catch (e: SQLException) {
            println("SongNewsAnnounced.markAnnounced SQLException: ${e.message}")
            false
        }
    }

    /** Пакетная загрузка id уже отмеченных песен — O(1) lookup через Set при фильтрации кандидатов. */
    fun loadAnnouncedSongIds(database: KaraokeConnection): Set<Long> {
        val connection = database.getConnection() ?: return emptySet()
        val result = mutableSetOf<Long>()
        return try {
            connection.prepareStatement("SELECT song_id FROM $TABLE_NAME").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) result.add(rs.getLong("song_id"))
                }
            }
            result
        } catch (e: SQLException) {
            println("SongNewsAnnounced.loadAnnouncedSongIds SQLException: ${e.message}")
            emptySet()
        }
    }
}

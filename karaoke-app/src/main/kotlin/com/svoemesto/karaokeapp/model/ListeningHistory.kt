package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp

// «История прослушиваний» пользователя публичного сайта (karaoke-public), QW-13. Одна строка на
// пару (site_user_id, song_id) — апсертится при каждом прослушивании (play_count++,
// last_played_at=now()), НЕ растёт на каждое событие (в отличие от tbl_events, которая
// используется для общей аналитики и регулярно опустошается на PROD через
// sync_events_pull_move_allowed — непригодна как персистентный источник, см.
// specs/009-listening-history/research.md Decision 1). Участвует в LOCAL<->SERVER синхронизации
// (SyncRegistry: listeninghistory), БД несёт recordhash-триггер (27_listening_history.sql).

/**
 * Запись истории прослушиваний: одна пара (пользователь, песня) с накопленным счётчиком.
 *
 * @see specs/009-listening-history/data-model.md
 */
class ListeningHistory(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "site_user_id")
    var siteUserId: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "play_count")
    var playCount: Long = 1

    @KaraokeDbTableField(name = "last_played_at")
    var lastPlayedAt: Timestamp? = null

    // last_update авто-управляется триггером update_last_updated_listening_history_trigger и НЕ
    // входит в recordhash — тот же паттерн, что SitePlaylist.lastUpdate.
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun toDTO(): ListeningHistoryDto =
        ListeningHistoryDto(
            id = id,
            siteUserId = siteUserId,
            songId = songId,
            playCount = playCount,
            lastPlayedAt = lastPlayedAt?.toString() ?: "",
        )

    companion object {
        const val TABLE_NAME = "tbl_listening_history"

        /**
         * Записывает прослушивание песни пользователем: создаёт строку при первом прослушивании
         * или увеличивает `play_count`/обновляет `last_played_at` при повторном. Race-safe на
         * уровне БД (`ON CONFLICT`), не read-then-write из приложения.
         *
         * @see specs/009-listening-history/research.md Decision 2
         */
        fun upsert(
            siteUserId: Long,
            songId: Long,
            database: KaraokeConnection = WORKING_DATABASE,
        ): Boolean {
            if (siteUserId <= 0 || songId <= 0) return false
            val connection = database.getConnection() ?: return false
            var statement: Statement? = null
            try {
                statement = connection.createStatement()
                statement.executeUpdate(
                    """
                    INSERT INTO $TABLE_NAME (site_user_id, song_id, play_count, last_played_at)
                    VALUES ($siteUserId, $songId, 1, now())
                    ON CONFLICT (site_user_id, song_id) DO UPDATE
                    SET play_count = $TABLE_NAME.play_count + 1, last_played_at = now()
                    """.trimIndent(),
                )
                return true
            } catch (e: SQLException) {
                e.printStackTrace()
                return false
            } finally {
                try {
                    statement?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * История прослушиваний конкретного пользователя, обогащённая данными песни, отсортированная
         * от последней прослушанной к более старой. `SKIP`-помеченные песни исключены (запись в
         * `tbl_listening_history` при этом не удаляется — фильтруется только на чтении).
         *
         * @see specs/009-listening-history/contracts/history-api.md
         */
        fun getForUser(
            siteUserId: Long,
            database: KaraokeConnection = WORKING_DATABASE,
            limit: Int = 100,
        ): List<ListeningHistoryEntry> {
            val result: MutableList<ListeningHistoryEntry> = mutableListOf()
            if (siteUserId <= 0) return result
            val connection = database.getConnection() ?: return result
            var statement: Statement? = null
            var rs: ResultSet? = null
            try {
                statement = connection.createStatement()
                rs =
                    statement.executeQuery(
                        """
                        SELECT h.song_id, s.song_name, s.song_author, s.song_album,
                               h.last_played_at, h.play_count
                        FROM $TABLE_NAME h
                        JOIN tbl_settings s ON s.id = h.song_id
                        WHERE h.site_user_id = $siteUserId
                          AND (s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))
                        ORDER BY h.last_played_at DESC
                        LIMIT $limit
                        """.trimIndent(),
                    )
                while (rs.next()) {
                    result.add(
                        ListeningHistoryEntry(
                            songId = rs.getLong("song_id"),
                            songName = rs.getString("song_name") ?: "",
                            songAuthor = rs.getString("song_author") ?: "",
                            songAlbum = rs.getString("song_album") ?: "",
                            lastPlayedAt = rs.getTimestamp("last_played_at"),
                            playCount = rs.getInt("play_count"),
                        ),
                    )
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            } finally {
                try {
                    rs?.close()
                    statement?.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
            return result
        }
    }
}

/**
 * Одна строка истории прослушиваний, обогащённая данными песни (результат join, не БД-сущность).
 *
 * @see specs/009-listening-history/data-model.md
 */
data class ListeningHistoryEntry(
    val songId: Long,
    val songName: String,
    val songAuthor: String,
    val songAlbum: String,
    val lastPlayedAt: Timestamp?,
    val playCount: Int,
)

package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.Timestamp

// Элемент плейлиста (одна песня в плейлисте). song_id -> tbl_settings.id, БЕЗ FK (песни живут своей
// жизнью и синхронизируются отдельно). Участвует в LOCAL<->SERVER синхронизации (SyncRegistry:
// siteplaylistitems). Порядок в плейлисте — колонка position (drag-drop), muted — пропуск при
// проигрывании (остаётся в списке).
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SitePlaylistItem(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<SitePlaylistItem>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "playlist_id")
    var playlistId: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "position")
    var position: Long = 0

    @KaraokeDbTableField(name = "muted")
    var muted: Boolean = false

    // Вне recordhash ⇒ вне diff синхронизации — см. SitePlaylist.lastUpdate / WebEvent.kt.
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: SitePlaylistItem): Int = compareValuesBy(this, other, { it.position }, { it.id })

    override fun toDTO(): SitePlaylistItemDto =
        SitePlaylistItemDto(
            id = id,
            playlistId = playlistId,
            songId = songId,
            position = position,
            muted = muted,
        )

    companion object {
        const val TABLE_NAME = "tbl_site_playlist_items"

        // Элементы плейлиста в порядке position (loadList не поддерживает ORDER BY — сортируем в Kotlin).
        fun loadItems(
            playlistId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SitePlaylistItem> =
            KaraokeDbTable
                .loadList(
                    clazz = SitePlaylistItem::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("playlist_id=$playlistId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SitePlaylistItem }
                .sorted()

        fun findItem(
            playlistId: Long,
            songId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SitePlaylistItem? =
            KaraokeDbTable
                .loadList(
                    clazz = SitePlaylistItem::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("playlist_id=$playlistId", "song_id=$songId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).firstOrNull() as? SitePlaylistItem?

        fun countItems(
            playlistId: Long,
            database: KaraokeConnection,
        ): Int {
            val connection = database.getConnection() ?: return 0
            val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME WHERE playlist_id = $playlistId"
            connection.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> if (rs.next()) return rs.getInt("cnt") }
            }
            return 0
        }

        // Множество song_id, входящих в набор плейлистов (для батч-эндпоинта membership в таблицах).
        fun songIdsInPlaylists(
            playlistIds: List<Long>,
            database: KaraokeConnection,
        ): Map<Long, List<Long>> {
            if (playlistIds.isEmpty()) return emptyMap()
            val connection = database.getConnection() ?: return emptyMap()
            val sql = "SELECT playlist_id, song_id FROM $TABLE_NAME WHERE playlist_id IN (${playlistIds.joinToString(",")})"
            val result = HashMap<Long, MutableList<Long>>() // songId -> [playlistId...]
            connection.createStatement().use { st ->
                st.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        val songId = rs.getLong("song_id")
                        val playlistId = rs.getLong("playlist_id")
                        result.getOrPut(songId) { mutableListOf() }.add(playlistId)
                    }
                }
            }
            return result
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}

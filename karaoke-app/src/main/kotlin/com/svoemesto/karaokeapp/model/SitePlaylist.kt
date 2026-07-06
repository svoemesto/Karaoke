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

// Плейлист пользователя публичного сайта (karaoke-public). «Избранное» — это плейлист с
// isFavorites = true (ровно один на пользователя, гарантируется частичным UNIQUE-индексом в БД).
// Участвует в LOCAL<->SERVER синхронизации (SyncRegistry: siteplaylists) — колонки аннотированы
// @KaraokeDbTableField, БД несёт recordhash-триггер (09_playlists.sql).
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SitePlaylist(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<SitePlaylist>, KaraokeDbTable {

    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "owner_id")
    var ownerId: Long = 0

    @KaraokeDbTableField(name = "name")
    var name: String = ""

    @KaraokeDbTableField(name = "is_favorites")
    var isFavorites: Boolean = false

    @KaraokeDbTableField(name = "sort_order")
    var sortOrder: Long = 0

    @KaraokeDbTableField(name = "continuous")
    var continuous: Boolean = true

    @KaraokeDbTableField(name = "repeat_mode")
    var repeatMode: String = "none"

    @KaraokeDbTableField(name = "shuffle")
    var shuffle: Boolean = false

    // last_update авто-управляется триггером update_last_updated_site_playlists_trigger и НЕ входит
    // в recordhash — поэтому вне diff синхронизации (иначе строки «вечно отличаются», см. WebEvent.kt).
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    // Избранное — сверху, затем по sort_order, затем по id.
    override fun compareTo(other: SitePlaylist): Int =
        compareValuesBy(this, other, { !it.isFavorites }, { it.sortOrder }, { it.id })

    override fun toDTO(): SitePlaylistDto = SitePlaylistDto(
        id = id,
        ownerId = ownerId,
        name = name,
        favorites = isFavorites,
        sortOrder = sortOrder,
        continuous = continuous,
        repeatMode = repeatMode,
        shuffle = shuffle,
    )

    companion object {

        const val TABLE_NAME = "tbl_site_playlists"

        fun loadByUser(
            ownerId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SitePlaylist> {
            return KaraokeDbTable.loadList(
                clazz = SitePlaylist::class,
                tableName = TABLE_NAME,
                whereList = listOf("owner_id=$ownerId"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).map { it as SitePlaylist }.sorted()
        }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SitePlaylist? {
            return KaraokeDbTable.loadById(
                clazz = SitePlaylist::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? SitePlaylist?
        }

        // Возвращает (создавая при необходимости) «Избранное» пользователя. Частичный UNIQUE-индекс
        // в БД дополнительно защищает от гонки двух параллельных первых добавлений.
        fun getOrCreateFavorites(
            ownerId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SitePlaylist? {
            loadByUser(ownerId, database, storageService, storageApiClient)
                .firstOrNull { it.isFavorites }?.let { return it }
            val fav = SitePlaylist(database = database, storageService = storageService, storageApiClient = storageApiClient)
            fav.ownerId = ownerId
            fav.name = "Избранное"
            fav.isFavorites = true
            fav.sortOrder = 0
            return KaraokeDbTable.createDbInstance(entity = fav, database = database) as? SitePlaylist?
        }

        // Имя по умолчанию для нового плейлиста: «Плейлист N» (N = число обычных плейлистов + 1).
        fun nextDefaultName(
            ownerId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): String {
            val count = loadByUser(ownerId, database, storageService, storageApiClient).count { !it.isFavorites }
            return "Плейлист ${count + 1}"
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean =
            KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}

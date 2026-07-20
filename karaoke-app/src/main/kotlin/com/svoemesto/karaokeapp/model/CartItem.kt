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

// Позиция «Корзины» — песня, которую пользователь копит для пакетной оплаты одним заказом
// (см. PublicCartController в karaoke-web). Прод-only данные, вне LOCAL<->SERVER SyncRegistry —
// как tbl_subscriptions.
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class CartItem(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<CartItem>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "site_user_id")
    var siteUserId: Long = 0

    @KaraokeDbTableField(name = "id_song")
    var idSong: Long = 0

    @KaraokeDbTableField(name = "added_at")
    var addedAt: Timestamp = Timestamp(0)

    override fun compareTo(other: CartItem): Int = compareValuesBy(this, other, { it.addedAt.time }, { it.id })

    override fun toDTO(): CartItemDto =
        CartItemDto(
            id = id,
            siteUserId = siteUserId,
            idSong = idSong,
            addedAt = addedAt.toString(),
        )

    companion object {
        const val TABLE_NAME = "tbl_cart_items"

        fun loadByUser(
            siteUserId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<CartItem> =
            KaraokeDbTable
                .loadList(
                    clazz = CartItem::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_user_id=$siteUserId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as CartItem }
                .sorted()

        fun getByUserAndSong(
            siteUserId: Long,
            idSong: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): CartItem? =
            KaraokeDbTable
                .loadList(
                    clazz = CartItem::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_user_id=$siteUserId", "id_song=$idSong"),
                    limit = 1,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).firstOrNull() as? CartItem?

        fun createNew(
            siteUserId: Long,
            idSong: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): CartItem? {
            val entity = CartItem(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.siteUserId = siteUserId
            entity.idSong = idSong
            entity.addedAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? CartItem?
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)

        // Очистка позиций, оформленных в заказ (после checkout) — по конкретным id_song пользователя.
        fun deleteByUserAndSongs(
            siteUserId: Long,
            songIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ) {
            if (songIds.isEmpty()) return
            loadByUser(siteUserId, database, storageService, storageApiClient)
                .filter { it.idSong in songIds }
                .forEach { delete(it.id, database) }
        }
    }
}

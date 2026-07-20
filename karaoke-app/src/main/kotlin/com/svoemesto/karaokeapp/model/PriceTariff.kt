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

// Тариф подписки (монетизация). SONG — бессрочная подписка на одну песню (periodDays игнорируется,
// доступ = сам факт наличия PAID-записи Subscription). SITE — периодическая подписка на сайт
// (periodDays продлевает SiteUser.sitePremiumUntil). См. также PromoRule (акции поверх тарифа) и
// Subscription (запись оформленной подписки). Не участвует в LOCAL<->SERVER content-sync
// (SyncRegistry) — тарифы правятся напрямую через target=local|remote, как SiteUser.
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class PriceTariff(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<PriceTariff>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "scope")
    var scope: String = SCOPE_SONG

    @KaraokeDbTableField(name = "name")
    var name: String = ""

    @KaraokeDbTableField(name = "price_rub")
    var priceRub: Double = 0.0

    @KaraokeDbTableField(name = "period_days")
    var periodDays: Int = 0

    @KaraokeDbTableField(name = "is_active")
    var isActive: Boolean = true

    @KaraokeDbTableField(name = "is_default")
    var isDefault: Boolean = false

    @KaraokeDbTableField(name = "sort_order")
    var sortOrder: Int = 0

    @KaraokeDbTableField(name = "created_at")
    var createdAt: Timestamp = Timestamp(0)

    // Авто-управляется триггером, вне recordhash/diff (см. SitePlaylist.lastUpdate).
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: PriceTariff): Int = compareValuesBy(this, other, { it.scope }, { it.sortOrder }, { it.id })

    override fun toDTO(): PriceTariffDto =
        PriceTariffDto(
            id = id,
            scope = scope,
            name = name,
            priceRub = priceRub,
            periodDays = periodDays,
            isActive = isActive,
            isDefault = isDefault,
            sortOrder = sortOrder,
        )

    companion object {
        const val TABLE_NAME = "tbl_price_tariffs"
        const val SCOPE_SONG = "SONG"
        const val SCOPE_SITE = "SITE"

        fun loadAll(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<PriceTariff> =
            KaraokeDbTable
                .loadList(
                    clazz = PriceTariff::class,
                    tableName = TABLE_NAME,
                    whereList = emptyList(),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as PriceTariff }
                .sorted()

        // Активные тарифы нужного охвата, для показа пользователю на /premium и странице песни.
        fun loadActiveByScope(
            scope: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<PriceTariff> =
            KaraokeDbTable
                .loadList(
                    clazz = PriceTariff::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("scope='${scope.replace("'", "''")}'", "is_active=true"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as PriceTariff }
                .sorted()

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): PriceTariff? =
            KaraokeDbTable.loadById(
                clazz = PriceTariff::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? PriceTariff?

        fun getDefault(
            scope: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): PriceTariff? =
            loadActiveByScope(scope, database, storageService, storageApiClient).firstOrNull { it.isDefault }
                ?: loadActiveByScope(scope, database, storageService, storageApiClient).firstOrNull()

        fun createNew(
            scope: String,
            name: String,
            priceRub: Double,
            periodDays: Int,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): PriceTariff? {
            val entity = PriceTariff(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.scope = scope
            entity.name = name
            entity.priceRub = priceRub
            entity.periodDays = periodDays
            entity.createdAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? PriceTariff?
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}

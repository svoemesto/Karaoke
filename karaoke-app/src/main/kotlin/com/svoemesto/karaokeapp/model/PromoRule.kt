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

// Акция поверх тарифа. paramsJson — параметры конкретного типа правила (см. TYPE_* ниже),
// расширяемо без миграций схемы. Применяется PriceService (karaoke-web) при расчёте итоговой цены,
// по приоритету (больше priority — раньше проверяется/побеждает при конфликте — решает PriceService).
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class PromoRule(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<PromoRule>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "name")
    var name: String = ""

    @KaraokeDbTableField(name = "type")
    var type: String = TYPE_FLAT_PERCENT

    @KaraokeDbTableField(name = "params_json")
    var paramsJson: String = "{}"

    @KaraokeDbTableField(name = "applies_to")
    var appliesTo: String = APPLIES_BOTH

    @KaraokeDbTableField(name = "is_active")
    var isActive: Boolean = true

    @KaraokeDbTableField(name = "valid_from")
    var validFrom: Timestamp? = null

    @KaraokeDbTableField(name = "valid_to")
    var validTo: Timestamp? = null

    @KaraokeDbTableField(name = "priority")
    var priority: Int = 0

    @KaraokeDbTableField(name = "created_at")
    var createdAt: Timestamp = Timestamp(0)

    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: PromoRule): Int = compareValuesBy(this, other, { -it.priority }, { it.id })

    override fun toDTO(): PromoRuleDto =
        PromoRuleDto(
            id = id,
            name = name,
            type = type,
            paramsJson = paramsJson,
            appliesTo = appliesTo,
            isActive = isActive,
            validFrom = validFrom?.toString(),
            validTo = validTo?.toString(),
            priority = priority,
        )

    // Активна ли акция прямо сейчас (независимо от применимости к конкретному scope/юзеру — это
    // решает PriceService на основе type/paramsJson).
    fun isCurrentlyActive(): Boolean {
        if (!isActive) return false
        val now = System.currentTimeMillis()
        validFrom?.let { if (now < it.time) return false }
        validTo?.let { if (now > it.time) return false }
        return true
    }

    fun appliesToScope(scope: String): Boolean = appliesTo == APPLIES_BOTH || appliesTo == scope

    companion object {
        const val TABLE_NAME = "tbl_promo_rules"

        const val APPLIES_SONG = "SONG"
        const val APPLIES_SITE = "SITE"
        const val APPLIES_BOTH = "BOTH"

        // Скидка % пользователям младше N часов с момента регистрации.
        const val TYPE_NEW_USER_PERCENT = "NEW_USER_PERCENT"

        // Каждая N-я ОПЛАЧЕННАЯ подписка пользователя — бесплатно.
        const val TYPE_NTH_FREE = "NTH_FREE"

        // Скидка % в окне часов/дней недели ("счастливый час").
        const val TYPE_HAPPY_HOUR = "HAPPY_HOUR"

        // Простая фиксированная скидка % без условий.
        const val TYPE_FLAT_PERCENT = "FLAT_PERCENT"

        // Скидка % на ВЕСЬ заказ «Корзины», если позиций в нём >= minQty (params: {"minQty":10,"percent":30}).
        // В отличие от NTH_FREE (считает по всем покупкам пользователя за всё время), это количество
        // СТРОГО в рамках одного заказа корзины.
        const val TYPE_CART_BULK_PERCENT = "CART_BULK_PERCENT"

        fun loadAll(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<PromoRule> =
            KaraokeDbTable
                .loadList(
                    clazz = PromoRule::class,
                    tableName = TABLE_NAME,
                    whereList = emptyList(),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as PromoRule }
                .sorted()

        fun loadActive(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<PromoRule> = loadAll(database, storageService, storageApiClient).filter { it.isCurrentlyActive() }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): PromoRule? =
            KaraokeDbTable.loadById(
                clazz = PromoRule::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? PromoRule?

        fun createNew(
            name: String,
            type: String,
            paramsJson: String,
            appliesTo: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): PromoRule? {
            val entity = PromoRule(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.name = name
            entity.type = type
            entity.paramsJson = paramsJson
            entity.appliesTo = appliesTo
            entity.createdAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? PromoRule?
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}

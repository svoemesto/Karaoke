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

// Обобщённая запись подписки — единый платёжный конвейер для двух видов доступа (термин «покупка»
// сознательно не используется нигде в проекте, см. план монетизации):
//   scope=SONG — бессрочная подписка на одну песню. PAID-запись сама по себе есть владение
//                (см. PublicPlayerController.subscribedToSong в karaoke-web).
//   scope=SITE — периодическая подписка на сайт. PAID продлевает SiteUser.sitePremiumUntil
//                (fulfillment в PublicPaymentController.webhook), с возможным автопродлением
//                (autoRenew + yookassaPaymentMethodId).
// Модель живёт в karaoke-app, т.к. karaoke-web зависит от него (implementation(project(":karaoke-app"))),
// но реально создаётся/читается почти всегда из karaoke-web (там платёжный конвейер на проде).
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class Subscription(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<Subscription>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "site_user_id")
    var siteUserId: Long = 0

    @KaraokeDbTableField(name = "scope")
    var scope: String = SCOPE_SONG

    // NULL для scope=SITE. Nullable-в-БД → Long? (инвариант reflection-loader).
    @KaraokeDbTableField(name = "id_song")
    var idSong: Long? = null

    @KaraokeDbTableField(name = "tariff_id")
    var tariffId: Long? = null

    @KaraokeDbTableField(name = "period_days")
    var periodDays: Int = 0

    @KaraokeDbTableField(name = "base_price")
    var basePrice: Double = 0.0

    @KaraokeDbTableField(name = "discount")
    var discount: Double = 0.0

    @KaraokeDbTableField(name = "final_price")
    var finalPrice: Double = 0.0

    @KaraokeDbTableField(name = "promo_applied")
    var promoApplied: String = ""

    @KaraokeDbTableField(name = "status")
    var status: String = STATUS_CREATED

    @KaraokeDbTableField(name = "yookassa_payment_id")
    var yookassaPaymentId: String = ""

    // Заказ «Корзины»: несколько записей одного оформления делят один order_id и один
    // yookassa_payment_id (один платёж на несколько песен). NULL — одиночная мгновенная покупка
    // (SongSubscriptionModal), как раньше.
    @KaraokeDbTableField(name = "order_id")
    var orderId: String? = null

    // Имеет смысл только для scope=SITE. По умолчанию true — подписка на сайт автопродлевается,
    // если явно не отключена пользователем (POST /purchase.../cancel в PublicSubscriptionController).
    @KaraokeDbTableField(name = "auto_renew")
    var autoRenew: Boolean = true

    @KaraokeDbTableField(name = "yookassa_payment_method_id")
    var yookassaPaymentMethodId: String = ""

    @KaraokeDbTableField(name = "created_at")
    var createdAt: Timestamp = Timestamp(0)

    @KaraokeDbTableField(name = "paid_at")
    var paidAt: Timestamp? = null

    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: Subscription): Int = compareValuesBy(this, other, { -it.createdAt.time }, { it.id })

    override fun toDTO(): SubscriptionDto =
        SubscriptionDto(
            id = id,
            siteUserId = siteUserId,
            scope = scope,
            idSong = idSong,
            tariffId = tariffId,
            periodDays = periodDays,
            basePrice = basePrice,
            discount = discount,
            finalPrice = finalPrice,
            promoApplied = promoApplied,
            status = status,
            autoRenew = autoRenew,
            createdAt = createdAt.toString(),
            paidAt = paidAt?.toString(),
            orderId = orderId,
        )

    companion object {
        const val TABLE_NAME = "tbl_subscriptions"

        const val SCOPE_SONG = "SONG"
        const val SCOPE_SITE = "SITE"

        const val STATUS_CREATED = "CREATED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PAID = "PAID"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_REFUNDED = "REFUNDED"
        const val STATUS_CANCELED = "CANCELED"

        fun loadByUser(
            siteUserId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Subscription> =
            KaraokeDbTable
                .loadList(
                    clazz = Subscription::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_user_id=$siteUserId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as Subscription }
                .sorted()

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Subscription? =
            KaraokeDbTable.loadById(
                clazz = Subscription::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? Subscription?

        // Для заказа «Корзины» несколько записей делят один yookassa_payment_id — возвращаем ВСЕ
        // (для одиночной покупки список будет из одного элемента, вызывающий код это не ломает).
        fun getAllByYookassaPaymentId(
            paymentId: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Subscription> =
            KaraokeDbTable
                .loadList(
                    clazz = Subscription::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("yookassa_payment_id='${paymentId.replace("'", "''")}'"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as Subscription }

        // Активна ли подписка пользователя на конкретную песню (scope=SONG, PAID). Бессрочная —
        // без проверки срока, само наличие PAID-записи = владение.
        fun isSubscribedToSong(
            siteUserId: Long,
            idSong: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Boolean =
            KaraokeDbTable
                .loadList(
                    clazz = Subscription::class,
                    tableName = TABLE_NAME,
                    whereList =
                        listOf(
                            "site_user_id=$siteUserId",
                            "scope='$SCOPE_SONG'",
                            "id_song=$idSong",
                            "status='$STATUS_PAID'",
                        ),
                    limit = 1,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).isNotEmpty()

        // Батч-версия isSubscribedToSong — для readiness() (иконки в таблицах Закрома/Поиск), чтобы
        // не делать по запросу на каждую песню (см. PublicPlaylistController.membership — тот же
        // паттерн батч-членства одним SQL-запросом).
        fun subscribedSongIds(
            siteUserId: Long,
            songIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Set<Long> {
            if (songIds.isEmpty() || siteUserId <= 0) return emptySet()
            return KaraokeDbTable
                .loadList(
                    clazz = Subscription::class,
                    tableName = TABLE_NAME,
                    whereList =
                        listOf(
                            "site_user_id=$siteUserId",
                            "scope='$SCOPE_SONG'",
                            "id_song IN (${songIds.joinToString(",")})",
                            "status='$STATUS_PAID'",
                        ),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).mapNotNull { (it as Subscription).idSong }
                .toSet()
        }

        // Незавершённый (PENDING) заказ пользователя на конкретный тариф сайта — используется, чтобы
        // повторный клик "Оформить" до перехода на страницу оплаты не плодил дублирующие подписки и
        // платежи в ЮKassa (см. PublicSubscriptionController.create): вместо новой записи переиспользуем
        // confirmationUrl уже созданного платежа.
        fun findPendingSite(
            siteUserId: Long,
            tariffId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Subscription? =
            KaraokeDbTable
                .loadList(
                    clazz = Subscription::class,
                    tableName = TABLE_NAME,
                    whereList =
                        listOf(
                            "site_user_id=$siteUserId",
                            "scope='$SCOPE_SITE'",
                            "tariff_id=$tariffId",
                            "status='$STATUS_PENDING'",
                        ),
                    limit = 1,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).firstOrNull() as? Subscription

        // Счётчик оплаченных подписок юзера в рамках scope — нужен для акции NTH_FREE.
        fun countPaid(
            siteUserId: Long,
            scope: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Int =
            KaraokeDbTable
                .loadList(
                    clazz = Subscription::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_user_id=$siteUserId", "scope='${scope.replace("'", "''")}'", "status='$STATUS_PAID'"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).size

        fun createNew(
            siteUserId: Long,
            scope: String,
            idSong: Long?,
            tariffId: Long?,
            periodDays: Int,
            basePrice: Double,
            discount: Double,
            finalPrice: Double,
            promoApplied: String,
            autoRenew: Boolean,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            orderId: String? = null,
        ): Subscription? {
            val entity = Subscription(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.siteUserId = siteUserId
            entity.scope = scope
            entity.idSong = idSong
            entity.tariffId = tariffId
            entity.periodDays = periodDays
            entity.basePrice = basePrice
            entity.discount = discount
            entity.finalPrice = finalPrice
            entity.promoApplied = promoApplied
            entity.autoRenew = autoRenew
            entity.orderId = orderId
            entity.createdAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? Subscription?
        }
    }
}

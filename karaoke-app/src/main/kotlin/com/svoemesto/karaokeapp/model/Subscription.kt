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

/**
 * Подписка пользователя (см. `PriceTariff.kt` для тарифов).
 *
 * Содержит:
 * - `id`, `idSiteUser` — пользователь.
 * - `idTariff` — ID тарифа.
 * - `startDate`, `endDate` — период действия.
 * - `isActive` — автопродление/ручное.
 * - `paymentId` — ID платежа (внешняя система).
 *
 * `SubscriptionRenewalScheduler` раз в сутки проверяет истёкшие подписки
 * и отправляет уведомления.
 *
 * @see archive/docs/features/telegram-auto-publish.md (доступ по подписке)
 */
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

        // Незавершённый заказ пользователя на конкретный тариф сайта — используется, чтобы повторный
        // клик "Оформить" до перехода на страницу оплаты не плодил дублирующие подписки и платежи в
        // ЮKassa (см. PublicSubscriptionController.create): вместо новой записи переиспользуем
        // confirmationUrl уже созданного платежа.
        //
        // Учитывается и CREATED: строка создаётся ДО платежа, и если создание платежа в прошлый раз
        // не удалось (503), остаётся CREATED без yookassa_payment_id. Раньше такой «хвост» не находился,
        // и следующий клик создавал ВТОРУЮ строку и второй платёж (аудит #336, BR-021).
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
                            "status IN ('$STATUS_CREATED','$STATUS_PENDING')",
                        ),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as Subscription }
                .let { pickUnfinished(it) }

        // Незавершённый заказ КОРЗИНЫ пользователя — все подписки одного оформления (общий
        // order_id) в статусе CREATED или PENDING (заказ создан, но платёж ещё не подтверждён).
        // Нужен для идемпотентности checkout (аудит #336, BR-014): повторный клик «Оплатить всё»
        // переиспользует тот же order_id → тот же Idempotence-Key ЮKassa → второй платёж не
        // создаётся. Возвращает подписки НАИБОЛЕЕ НОВОГО незавершённого заказа (пусто — заказа нет).
        fun loadUnfinishedCartOrder(
            siteUserId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Subscription> {
            val rows =
                KaraokeDbTable
                    .loadList(
                        clazz = Subscription::class,
                        tableName = TABLE_NAME,
                        whereList =
                            listOf(
                                "site_user_id=$siteUserId",
                                "scope='$SCOPE_SONG'",
                                "order_id IS NOT NULL",
                                "status IN ('$STATUS_CREATED','$STATUS_PENDING')",
                            ),
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    ).map { it as Subscription }
            if (rows.isEmpty()) return emptyList()
            val newestOrderId = rows.maxByOrNull { it.createdAt.time }?.orderId ?: return emptyList()
            return rows.filter { it.orderId == newestOrderId }
        }

        // Незавершённая подписка пользователя на конкретную песню — защита от двойного клика
        // «Оформить» (аудит #336, BR-021): второй запрос переиспользует существующий платёж вместо
        // создания нового. Учитываются и позиции заказа корзины (order_id задан): один платёж на
        // песню — значит, второй платёж по той же песне был бы двойным списанием.
        //
        // CREATED включается по той же причине, что и в findPendingSite: строка без платежа после
        // сбойного создания платежа должна переиспользоваться, а не дублироваться.
        fun findPendingSong(
            siteUserId: Long,
            idSong: Long,
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
                            "scope='$SCOPE_SONG'",
                            "id_song=$idSong",
                            "status IN ('$STATUS_CREATED','$STATUS_PENDING')",
                        ),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as Subscription }
                .let { pickUnfinished(it) }

        // Из нескольких незавершённых строк предпочитаем ту, у которой УЖЕ есть платёж
        // (её и надо переиспользовать), иначе — самую новую. Без этого `limit = 1` мог вернуть
        // старую CREATED-строку без платежа, и рядом с живым PENDING родился бы второй платёж.
        private fun pickUnfinished(rows: List<Subscription>): Subscription? =
            rows.filter { it.yookassaPaymentId.isNotBlank() }.maxByOrNull { it.createdAt.time }
                ?: rows.maxByOrNull { it.createdAt.time }

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

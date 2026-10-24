package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.CartItem
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.PaymentOrderReuse
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.PriceService
import com.svoemesto.karaokeweb.services.ReuseOutcome
import com.svoemesto.karaokeweb.services.SubscriptionFulfillmentService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * «Корзина» — накопление нескольких подписок на песни для пакетной оплаты одним заказом (термин
 * «покупка» не используется — см. план монетизации). Второй, параллельный путь к мгновенной покупке
 * одной песни (SongSubscriptionModal/PublicSubscriptionController) — оба сосуществуют.
 *
 * Весь класс под /api/public/account — проходит через SiteAuthInterceptor (WebMvcConfig), доступно
 * только вошедшим. Цена всегда пересчитывается на сервере при checkout — клиент не может её подменить.
 */

/**
 * Контроллер (HTTP/WebSocket endpoints) для public cart .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/public/account/cart")
class PublicCartController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val priceService: PriceService,
    private val paymentService: PaymentService,
    /**
     * Начисление оплаченной подписки (транзакция «статус + продление»). Шов для тестов.
     */
    private val fulfillmentService: SubscriptionFulfillmentService = SubscriptionFulfillmentService(),
    @Value("\${app.public-site-url}") private val publicSiteUrl: String,
) {
    private val db get() = WORKING_DATABASE

    private fun currentUser(request: HttpServletRequest): SiteUser = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    private fun songInfo(
        idSong: Long,
        database: KaraokeConnection = db,
    ): Song? = Song.loadFromDbById(idSong, database, storageService = storageService, storageApiClient = storageApiClient)

    // Песню могли купить напрямую (SongSubscriptionModal), минуя корзину — она навсегда остаётся
    // "непокупаемой" через корзину. Убираем такие позиции при каждом обращении к корзине (не только
    // при checkout), иначе зомби-позиция висела бы в списке до следующей оплаты.
    private fun loadCartCleaned(
        user: SiteUser,
        database: KaraokeConnection = db,
    ): List<CartItem> {
        val items = CartItem.loadByUser(user.id, database, storageService, storageApiClient)
        val alreadyOwned = items.filter { Subscription.isSubscribedToSong(user.id, it.idSong, database, storageService, storageApiClient) }
        if (alreadyOwned.isNotEmpty()) {
            CartItem.deleteByUserAndSongs(user.id, alreadyOwned.map { it.idSong }, database, storageService, storageApiClient)
        }
        return items.filter { it !in alreadyOwned }
    }

    // ---- Список товаров в корзине ------------------------------------------------------------------

    @GetMapping("/list")
    fun list(request: HttpServletRequest): List<Map<String, Any?>> {
        val user = currentUser(request)
        return loadCartCleaned(user).mapNotNull { item ->
            val song = songInfo(item.idSong) ?: return@mapNotNull null
            mapOf(
                "songId" to item.idSong,
                "songName" to song.songName,
                "author" to song.author,
            )
        }
    }

    // ---- Очистить корзину целиком ------------------------------------------------------------------

    @PostMapping("/clear")
    fun clear(request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        CartItem.loadByUser(user.id, db, storageService, storageApiClient).forEach { CartItem.delete(it.id, db) }
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    // ---- Добавить/убрать (тумблер, как избранное) --------------------------------------------------

    @PostMapping("/toggle")
    fun toggle(
        @RequestParam songId: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        val existing = CartItem.getByUserAndSong(user.id, songId, db, storageService, storageApiClient)
        if (existing != null) {
            CartItem.delete(existing.id, db)
            return ResponseEntity.ok(mapOf("inCart" to false))
        }
        val song = songInfo(songId) ?: return ResponseEntity.notFound().build()
        if (song.idTariff < 0) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "song_not_for_subscription"))
        if (Subscription.isSubscribedToSong(user.id, songId, db, storageService, storageApiClient)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "already_subscribed"))
        }
        CartItem.createNew(user.id, songId, db, storageService, storageApiClient)
        return ResponseEntity.ok(mapOf("inCart" to true))
    }

    // ---- Цена (превью до оплаты, с учётом акций) ----------------------------------------------------

    @GetMapping("/price")
    fun price(request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val items = loadCartCleaned(user)
        val paymentsEnabled = paymentService.hasCredentials()
        if (items.isEmpty()) {
            return ResponseEntity.ok(mapOf("items" to emptyList<Any>(), "total" to 0.0, "paymentsEnabled" to paymentsEnabled))
        }
        val tariff =
            PriceTariff.getDefault(Subscription.SCOPE_SONG, db, storageService, storageApiClient)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))
        val prices = priceService.computeCartPrice(tariff, items.size, user, db, storageService, storageApiClient)
        val rows =
            items.zip(prices).map { (item, p) ->
                val song = songInfo(item.idSong)
                mapOf(
                    "songId" to item.idSong,
                    "songName" to song?.songName,
                    "author" to song?.author,
                    "base" to p.base,
                    "discount" to p.discount,
                    "final" to p.final,
                    "promoApplied" to p.promoApplied,
                    "personalDiscountPercent" to p.personalDiscountPercent,
                )
            }
        return ResponseEntity.ok(
            mapOf(
                "items" to rows,
                "total" to rows.sumOf { it["final"] as Double },
                "paymentsEnabled" to paymentsEnabled,
            ),
        )
    }

    // ---- Оформление заказа целиком ------------------------------------------------------------------

    @PostMapping("/checkout")
    fun checkout(
        @RequestParam disclaimerAccepted: Boolean,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = handleCheckout(disclaimerAccepted, request, WORKING_DATABASE)

    /**
     * Оформление заказа. Шов для тестов: [database] подменяется поддельным соединением.
     *
     * Порядок операций (аудит #336, BR-014 — «деньги: потеря корзины и второй платёж»):
     *
     * 1. Незавершённый заказ пользователя **переиспользуется** (стабильный `order_id` → тот же
     *    `Idempotence-Key` ЮKassa `order-<orderId>` → второй платёж не создаётся).
     * 2. Новый заказ: сначала строки `Subscription` (нужен `orderId` для ключа), затем платёж,
     *    и только ПОСЛЕ успешного создания платежа — очистка корзины и перевод строк в PENDING.
     * 3. Платёж не создался — строки заказа откатываются, корзина остаётся нетронутой, клиент может
     *    повторить checkout (раньше корзина уже была пуста, а заказ висел в CREATED).
     * 4. Два одновременных checkout сериализуются по пользователю: второй увидит заказ первого и
     *    переиспользует его, а не создаст второй платёж.
     */
    internal fun handleCheckout(
        disclaimerAccepted: Boolean,
        request: HttpServletRequest,
        database: KaraokeConnection,
    ): ResponseEntity<Any> {
        if (!disclaimerAccepted) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "disclaimer_required"))
        val user = currentUser(request)
        // loadCartCleaned уже убрала из корзины то, что успели купить напрямую, минуя корзину, —
        // защитный пересчёт прямо перед оплатой (двойная оплата невозможна). Дополнительно здесь
        // отбрасываем то, для чего автор с момента добавления в корзину запретил подписку.
        val cartItems = loadCartCleaned(user, database)
        if (cartItems.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "cart_empty"))
        val eligible = cartItems.filter { item -> songInfo(item.idSong, database)?.idTariff?.let { it >= 0 } == true }
        if (eligible.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "cart_empty"))

        return synchronized(checkoutLock(user.id)) {
            val unfinished = Subscription.loadUnfinishedCartOrder(user.id, database, storageService, storageApiClient)
            if (unfinished.isNotEmpty()) {
                val reused = reuseCartOrder(unfinished, user, database)
                if (reused != null) return@synchronized reused
                // Заказ оказался мёртвым (платёж отменён) — помечен FAILED, создаём новый ниже.
            }
            createNewCartOrder(user, eligible, database)
        }
    }

    /**
     * Переиспользование незавершённого заказа корзины (аудит #336, BR-014).
     *
     * @return ответ клиенту либо `null` — «заказ мёртвый, создавай новый».
     */
    private fun reuseCartOrder(
        existing: List<Subscription>,
        user: SiteUser,
        database: KaraokeConnection,
    ): ResponseEntity<Any>? {
        val orderId = existing.firstNotNullOfOrNull { it.orderId } ?: return null
        val knownPaymentId = existing.firstOrNull { it.yookassaPaymentId.isNotBlank() }?.yookassaPaymentId ?: ""
        if (knownPaymentId.isBlank()) {
            // Строки заказа созданы, а платёж — нет (прошлый запрос упал на createCartPayment).
            // Создаём платёж для ТОГО ЖЕ orderId: ключ идемпотентности стабилен, дубля платежа не будет.
            val total = existing.sumOf { it.finalPrice }
            if (total <= 0.0) return fulfillFreeCartOrder(existing, orderId, user, database)
            val payment =
                paymentService.createCartPayment(
                    orderId = orderId,
                    totalAmount = total,
                    items = orderItems(existing, database),
                    email = user.email,
                    returnUrl = returnUrlFor(orderId),
                )
            if (payment == null || payment.id.isBlank()) {
                // Платёж не создан — НИ корзину, НИ заказ не трогаем: повтор вернётся сюда же.
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    mapOf("error" to "payment_unavailable", "orderId" to orderId),
                )
            }
            markPending(existing, payment.id)
            deleteCartItems(user, existing.mapNotNull { it.idSong }, database)
            return ResponseEntity.ok(
                mapOf(
                    "orderId" to orderId,
                    "status" to Subscription.STATUS_PENDING,
                    "confirmationUrl" to payment.confirmation?.confirmation_url,
                ),
            )
        }

        val verified = paymentService.verifyAndFetch(knownPaymentId)
        return when (PaymentOrderReuse.decide(verified?.status)) {
            ReuseOutcome.REUSE_PAYMENT -> {
                adoptPaymentId(existing, knownPaymentId)
                deleteCartItems(user, existing.mapNotNull { it.idSong }, database)
                ResponseEntity.ok(
                    mapOf(
                        "orderId" to orderId,
                        "status" to Subscription.STATUS_PENDING,
                        "confirmationUrl" to verified?.confirmation?.confirmation_url,
                    ),
                )
            }
            ReuseOutcome.ALREADY_PAID -> {
                // Платёж уже прошёл (вебхук мог не дойти) — начисляем сами, второй счёт не выставляем.
                adoptPaymentId(existing, knownPaymentId)
                existing.filter { it.status != Subscription.STATUS_PAID }.forEach { sub ->
                    fulfillmentService.fulfillPaid(sub, knownPaymentId, verified?.payment_method?.id ?: "")
                }
                deleteCartItems(user, existing.mapNotNull { it.idSong }, database)
                ResponseEntity.ok(
                    mapOf(
                        "orderId" to orderId,
                        "status" to Subscription.STATUS_PAID,
                        "confirmationUrl" to null,
                    ),
                )
            }
            ReuseOutcome.CREATE_NEW -> {
                // Мёртвый заказ — помечаем FAILED, чтобы он больше не находился как незавершённый.
                existing.forEach { sub ->
                    if (sub.status != Subscription.STATUS_PAID) {
                        sub.status = Subscription.STATUS_FAILED
                        sub.save()
                    }
                }
                null
            }
            ReuseOutcome.UNAVAILABLE ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    mapOf("error" to "payment_unavailable", "orderId" to orderId),
                )
        }
    }

    /** Новый заказ: строки → платёж → (только при успехе) PENDING и очистка корзины. */
    private fun createNewCartOrder(
        user: SiteUser,
        eligible: List<CartItem>,
        database: KaraokeConnection,
    ): ResponseEntity<Any> {
        val tariff =
            PriceTariff.getDefault(Subscription.SCOPE_SONG, database, storageService, storageApiClient)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))
        // Аудит BR-005 (OpenProject #378): порядковые номера позиций для акции «каждая N-я
        // бесплатно» выделяются под блокировкой (пользователь, scope). Без неё два параллельных
        // checkout'а читали один и тот же countPaid, и бесплатной становилась одна и та же
        // позиция в обоих заказах (двойная скидка на границе акции).
        val orderId = UUID.randomUUID().toString()
        var freeOrder = false
        val subs =
            priceService.withOrdinalLock(user.id, Subscription.SCOPE_SONG) {
                // Цена считается ВНУТРИ блокировки: countPaid читается и «занимается» атомарно
                // с созданием строк заказа (BR-005, #378).
                val prices = priceService.computeCartPrice(tariff, eligible.size, user, database, storageService, storageApiClient)
                val created =
                    eligible.zip(prices).mapNotNull { (item, p) ->
                        Subscription.createNew(
                            siteUserId = user.id,
                            scope = Subscription.SCOPE_SONG,
                            idSong = item.idSong,
                            tariffId = tariff.id,
                            periodDays = tariff.periodDays,
                            basePrice = p.base,
                            discount = p.discount,
                            finalPrice = p.final,
                            promoApplied = p.promoApplied ?: "",
                            autoRenew = false,
                            database = database,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                            orderId = orderId,
                        )
                    }
                if (created.size == eligible.size && created.sumOf { it.finalPrice } <= 0.0) {
                    // Акция довела весь заказ до нуля — начисляем сразу, без ЮKassa. Запись PAID —
                    // ВНУТРИ блокировки: она «занимает» порядковые номера (BR-005). Начисление идёт
                    // единой точкой [SubscriptionFulfillmentService] (#365, BR-020).
                    try {
                        created.filter { it.status != Subscription.STATUS_PAID }.forEach { fulfillmentService.fulfillPaid(it) }
                        freeOrder = true
                    } catch (e: Exception) {
                        // Начисление не удалось — блокировку отпускаем, корзину НЕ трогаем (BR-014).
                        // Ответ и повтор начисления отдаёт [fulfillFreeCartOrder] ниже.
                        log.error("checkout: начисление бесплатного заказа {} не выполнено в блокировке: {}", orderId, e.message, e)
                    }
                }
                created
            }
        if (subs.size != eligible.size) {
            rollbackOrder(subs, database)
            return ResponseEntity.internalServerError().body(mapOf("error" to "create_failed"))
        }

        val total = subs.sumOf { it.finalPrice }
        if (freeOrder || total <= 0.0) {
            // Бесплатный (акционный) заказ: строки уже PAID (запись была в блокировке). Здесь —
            // очистка корзины и ответ; если начисление в блокировке сорвалось, [fulfillFreeCartOrder]
            // повторит его и при сбое вернёт ошибку, оставив корзину нетронутой (BR-014).
            return fulfillFreeCartOrder(subs, orderId, user, database)
        }

        val payment =
            paymentService.createCartPayment(
                orderId = orderId,
                totalAmount = total,
                items = orderItems(subs, database),
                email = user.email,
                returnUrl = returnUrlFor(orderId),
            )
        return commitCartOrder(
            orderId = orderId,
            payment = payment,
            onPaymentCreated = { created ->
                markPending(subs, created.id)
                deleteCartItems(user, eligible.map { it.idSong }, database)
            },
            onRollback = {
                rollbackOrder(subs, database)
                log.warn("checkout: платёж не создан, заказ {} откатан, корзина сохранена (site_user_id={})", orderId, user.id)
            },
        )
    }

    /**
     * Инвариант порядка (аудит #336, BR-014): корзина очищается и заказ переводится в PENDING
     * **только после** успешного создания платежа. Платёж не создан — заказ откатывается, корзина
     * остаётся нетронутой, клиент повторяет checkout.
     *
     * Вынесено отдельным методом, чтобы регрессия «сначала очистили корзину, потом создали платёж»
     * ловилась тестом, а не только чтением кода.
     */
    internal fun commitCartOrder(
        orderId: String,
        payment: PaymentService.PaymentResponse?,
        onPaymentCreated: (PaymentService.PaymentResponse) -> Unit,
        onRollback: () -> Unit,
    ): ResponseEntity<Any> {
        if (payment == null || payment.id.isBlank()) {
            onRollback()
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to "payment_unavailable"))
        }
        onPaymentCreated(payment)
        return ResponseEntity.ok(
            mapOf(
                "orderId" to orderId,
                "status" to Subscription.STATUS_PENDING,
                "confirmationUrl" to payment.confirmation?.confirmation_url,
            ),
        )
    }

    /** Бесплатный (акционный) заказ: начисляем каждой позиции, затем убираем её из корзины. */
    private fun fulfillFreeCartOrder(
        subs: List<Subscription>,
        orderId: String,
        user: SiteUser,
        database: KaraokeConnection,
    ): ResponseEntity<Any> =
        try {
            subs.filter { it.status != Subscription.STATUS_PAID }.forEach { fulfillmentService.fulfillPaid(it) }
            deleteCartItems(user, subs.mapNotNull { it.idSong }, database)
            ResponseEntity.ok(mapOf("orderId" to orderId, "status" to Subscription.STATUS_PAID, "confirmationUrl" to null))
        } catch (e: Exception) {
            // Начисление не удалось — корзину не трогаем (заказ останется незавершённым и будет
            // доведён следующим checkout'ом; повторное начисление отсекает атомарный захват в PAID).
            log.error("checkout: начисление бесплатного заказа {} не выполнено: {}", orderId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "fulfillment_failed", "orderId" to orderId))
        }

    /** Откат только что созданных строк заказа (платёж не создан / создан частично). */
    private fun rollbackOrder(
        subs: List<Subscription>,
        database: KaraokeConnection,
    ) {
        val ids = subs.map { it.id }
        if (ids.isEmpty()) return
        KaraokeDbTable.deleteIn(Subscription.TABLE_NAME, ids, database)
    }

    private fun markPending(
        subs: List<Subscription>,
        paymentId: String,
    ) {
        subs.forEach { sub ->
            sub.yookassaPaymentId = paymentId
            sub.status = Subscription.STATUS_PENDING
            sub.save()
        }
    }

    /** Проставляет id платежа строкам заказа, оставшимся без него (частичный сбой прошлого запроса). */
    private fun adoptPaymentId(
        subs: List<Subscription>,
        paymentId: String,
    ) {
        subs.filter { it.yookassaPaymentId.isBlank() }.forEach { sub ->
            sub.yookassaPaymentId = paymentId
            if (sub.status == Subscription.STATUS_CREATED) sub.status = Subscription.STATUS_PENDING
            sub.save()
        }
    }

    private fun orderItems(
        subs: List<Subscription>,
        database: KaraokeConnection,
    ): List<Pair<String, Double>> =
        subs.map { sub -> "Подписка на песню: ${songInfo(sub.idSong!!, database)?.songName ?: sub.idSong}" to sub.finalPrice }

    private fun returnUrlFor(orderId: String): String = "$publicSiteUrl/subscription/return?orderId=$orderId"

    private fun deleteCartItems(
        user: SiteUser,
        songIds: List<Long>,
        database: KaraokeConnection,
    ) {
        if (songIds.isEmpty()) return
        CartItem.deleteByUserAndSongs(user.id, songIds, database, storageService, storageApiClient)
    }

    /** Полоса блокировок по пользователю: два одновременных checkout не создадут два заказа. */
    private fun checkoutLock(userId: Long): Any = checkoutLocks[Math.floorMod(userId, CHECKOUT_LOCK_STRIPES)]

    private companion object {
        const val CHECKOUT_LOCK_STRIPES = 64
        private val log = LoggerFactory.getLogger(PublicCartController::class.java)
    }

    private val checkoutLocks = Array(CHECKOUT_LOCK_STRIPES) { Any() }
}

package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.CartItem
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.PriceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
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
    @Value("\${app.public-site-url}") private val publicSiteUrl: String,
) {
    private val db get() = WORKING_DATABASE

    private fun currentUser(request: HttpServletRequest): SiteUser = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    private fun songInfo(idSong: Long): Settings? =
        Settings.loadFromDbById(idSong, db, storageService = storageService, storageApiClient = storageApiClient)

    // Песню могли купить напрямую (SongSubscriptionModal), минуя корзину — она навсегда остаётся
    // "непокупаемой" через корзину. Убираем такие позиции при каждом обращении к корзине (не только
    // при checkout), иначе зомби-позиция висела бы в списке до следующей оплаты.
    private fun loadCartCleaned(user: SiteUser): List<CartItem> {
        val items = CartItem.loadByUser(user.id, db, storageService, storageApiClient)
        val alreadyOwned = items.filter { Subscription.isSubscribedToSong(user.id, it.idSong, db, storageService, storageApiClient) }
        if (alreadyOwned.isNotEmpty()) {
            CartItem.deleteByUserAndSongs(user.id, alreadyOwned.map { it.idSong }, db, storageService, storageApiClient)
        }
        return items.filter { it !in alreadyOwned }
    }

    // ---- Список товаров в корзине ------------------------------------------------------------------

    @GetMapping("/list")
    fun list(request: HttpServletRequest): List<Map<String, Any?>> {
        val user = currentUser(request)
        return loadCartCleaned(user).mapNotNull { item ->
            val settings = songInfo(item.idSong) ?: return@mapNotNull null
            mapOf(
                "songId" to item.idSong,
                "songName" to settings.songName,
                "author" to settings.author,
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
        val settings = songInfo(songId) ?: return ResponseEntity.notFound().build()
        if (settings.idTariff < 0) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "song_not_for_subscription"))
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
                val settings = songInfo(item.idSong)
                mapOf(
                    "songId" to item.idSong,
                    "songName" to settings?.songName,
                    "author" to settings?.author,
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
    ): ResponseEntity<Any> {
        if (!disclaimerAccepted) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "disclaimer_required"))
        val user = currentUser(request)
        // loadCartCleaned уже убрала из корзины то, что успели купить напрямую, минуя корзину, —
        // защитный пересчёт прямо перед оплатой (двойная оплата невозможна). Дополнительно здесь
        // отбрасываем то, для чего автор с момента добавления в корзину запретил подписку.
        val cartItems = loadCartCleaned(user)
        if (cartItems.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "cart_empty"))
        val eligible = cartItems.filter { item -> songInfo(item.idSong)?.idTariff?.let { it >= 0 } == true }
        if (eligible.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "cart_empty"))

        val tariff =
            PriceTariff.getDefault(Subscription.SCOPE_SONG, db, storageService, storageApiClient)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))
        val prices = priceService.computeCartPrice(tariff, eligible.size, user, db, storageService, storageApiClient)

        val orderId = UUID.randomUUID().toString()
        val subs =
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
                    database = db,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    orderId = orderId,
                )
            }
        if (subs.size != eligible.size) return ResponseEntity.internalServerError().body(mapOf("error" to "create_failed"))

        // Заказ теперь живёт как записи Subscription — очищаем оформленные позиции из корзины сразу.
        CartItem.deleteByUserAndSongs(user.id, eligible.map { it.idSong }, db, storageService, storageApiClient)

        val total = subs.sumOf { it.finalPrice }
        if (total <= 0.0) {
            // Акция довела весь заказ до нуля — фиксируем как оплаченный сразу, без похода в ЮKassa.
            subs.forEach { sub ->
                sub.status = Subscription.STATUS_PAID
                sub.paidAt = Timestamp(System.currentTimeMillis())
                sub.save()
            }
            return ResponseEntity.ok(mapOf("orderId" to orderId, "status" to "PAID", "confirmationUrl" to null))
        }

        val items = subs.map { sub -> "Подписка на песню: ${songInfo(sub.idSong!!)?.songName ?: sub.idSong}" to sub.finalPrice }
        val returnUrl = "$publicSiteUrl/subscription/return?orderId=$orderId"
        val payment = paymentService.createCartPayment(orderId, total, items, user.email, returnUrl)
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to "payment_unavailable", "orderId" to orderId))
        }
        subs.forEach { sub ->
            sub.yookassaPaymentId = payment.id
            sub.status = Subscription.STATUS_PENDING
            sub.save()
        }
        return ResponseEntity.ok(
            mapOf(
                "orderId" to orderId,
                "status" to "PENDING",
                "confirmationUrl" to payment.confirmation?.confirmation_url,
            ),
        )
    }
}

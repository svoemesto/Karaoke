package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.PriceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Оформление подписки — единая точка для обоих видов (термин «покупка» нигде не используется,
 * см. план монетизации): scope=SONG (бессрочная подписка на песню) и scope=SITE (периодическая
 * подписка на сайт, по умолчанию с автопродлением). Весь класс под /api/public/account — проходит
 * через SiteAuthInterceptor (WebMvcConfig), доступно только вошедшим.
 *
 * Цена всегда пересчитывается на сервере (PriceService) — клиент не может её подменить, см. план,
 * Verification. Fulfillment (реальная разблокировка/продление) происходит в PublicPaymentController
 * по вебхуку ЮKassa, а не здесь — до оплаты запись остаётся в CREATED/PENDING.
 */
@RestController
@RequestMapping("/api/public/account/subscription")
class PublicSubscriptionController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val priceService: PriceService,
    private val paymentService: PaymentService,
    @Value("\${app.public-site-url}") private val publicSiteUrl: String,
) {
    private val db get() = WORKING_DATABASE

    private fun currentUser(request: HttpServletRequest): SiteUser =
        request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    private fun tariffFor(scope: String, songId: Long?, tariffId: Long?): PriceTariff? {
        if (tariffId != null) {
            PriceTariff.getById(tariffId, db, storageService, storageApiClient)
                ?.takeIf { it.scope == scope && it.isActive }
                ?.let { return it }
        }
        return PriceTariff.getDefault(scope, db, storageService, storageApiClient)
    }

    // ---- Список активных тарифов (для выбора на /premium) --------------------------------------

    // paymentsEnabled — заданы ли ключи ЮKassa (PaymentService.hasCredentials()). Пока их нет, фронт
    // должен отключать кнопку "Оплатить" вместо того, чтобы пользователь ловил payment_unavailable
    // после клика. Бесплатных (акция 100%) подписок это не касается — они не ходят в ЮKassa вовсе.
    @GetMapping("/tariffs")
    fun tariffs(@RequestParam scope: String): List<Map<String, Any?>> {
        val paymentsEnabled = paymentService.hasCredentials()
        return PriceTariff.loadAll(db, storageService, storageApiClient)
            .filter { it.scope == scope && it.isActive }
            .map { mapOf("id" to it.id, "name" to it.name, "priceRub" to it.priceRub, "periodDays" to it.periodDays, "isDefault" to it.isDefault, "paymentsEnabled" to paymentsEnabled) }
    }

    // ---- Цена (для показа в CheckoutModal ДО оплаты) -------------------------------------------

    @GetMapping("/price")
    fun price(
        @RequestParam scope: String,
        @RequestParam(required = false) songId: Long?,
        @RequestParam(required = false) tariffId: Long?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        val tariff = tariffFor(scope, songId, tariffId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))
        val result = priceService.computePrice(tariff, user, db, storageService, storageApiClient)
        return ResponseEntity.ok(mapOf(
            "tariffId" to tariff.id,
            "tariffName" to tariff.name,
            "periodDays" to tariff.periodDays,
            "base" to result.base,
            "discount" to result.discount,
            "final" to result.final,
            "promoApplied" to result.promoApplied,
            // Отключаем "Оплатить" на фронте, пока не заданы ключи ЮKassa — бесплатных (final=0)
            // подписок это не касается, они не ходят в ЮKassa вовсе.
            "paymentsEnabled" to paymentService.hasCredentials(),
        ))
    }

    // ---- Оформление ------------------------------------------------------------------------------

    @PostMapping("/create")
    fun create(
        @RequestParam scope: String,
        @RequestParam(required = false) songId: Long?,
        @RequestParam(required = false) tariffId: Long?,
        @RequestParam disclaimerAccepted: Boolean,
        @RequestParam(required = false) autoRenew: Boolean?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!disclaimerAccepted) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "disclaimer_required"))
        val user = currentUser(request)

        if (scope == Subscription.SCOPE_SONG) {
            if (songId == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "song_id_required"))
            val settings = Settings.loadFromDbById(songId, db, storageService = storageService, storageApiClient = storageApiClient)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "song_not_found"))
            if (settings.idTariff <= 0) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "song_not_for_subscription"))
            if (Subscription.isSubscribedToSong(user.id, songId, db, storageService, storageApiClient))
                return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "already_subscribed"))
        } else if (scope != Subscription.SCOPE_SITE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "invalid_scope"))
        }

        val tariff = tariffFor(scope, songId, tariffId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))
        val price = priceService.computePrice(tariff, user, db, storageService, storageApiClient)

        val sub = Subscription.createNew(
            siteUserId = user.id,
            scope = scope,
            idSong = if (scope == Subscription.SCOPE_SONG) songId else null,
            tariffId = tariff.id,
            periodDays = tariff.periodDays,
            basePrice = price.base,
            discount = price.discount,
            finalPrice = price.final,
            promoApplied = price.promoApplied ?: "",
            // По умолчанию включено для подписки на сайт (см. план: "автопродление по умолчанию"),
            // для подписки на песню значение не имеет смысла (бессрочная, продлевать нечего).
            autoRenew = if (scope == Subscription.SCOPE_SITE) (autoRenew ?: true) else false,
            database = db, storageService = storageService, storageApiClient = storageApiClient,
        ) ?: return ResponseEntity.internalServerError().body(mapOf("error" to "create_failed"))

        // Акция довела цену до нуля — фиксируем как оплаченную сразу, без похода в ЮKassa
        // (там ненулевой минимум суммы платежа).
        if (price.final <= 0.0) {
            sub.status = Subscription.STATUS_PAID
            sub.paidAt = java.sql.Timestamp(System.currentTimeMillis())
            sub.save()
            applyFulfillment(sub)
            return ResponseEntity.ok(mapOf("subscriptionId" to sub.id, "status" to sub.status, "confirmationUrl" to null))
        }

        val description = if (scope == Subscription.SCOPE_SONG) "Подписка на песню (id $songId)" else "Подписка на сайт (${tariff.name})"
        val returnUrl = "$publicSiteUrl/subscription/return?subId=${sub.id}"
        val payment = paymentService.createPayment(
            sub = sub,
            description = description,
            email = user.email,
            returnUrl = returnUrl,
            saveMethod = scope == Subscription.SCOPE_SITE && sub.autoRenew,
        )
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf(
                "error" to "payment_unavailable",
                "subscriptionId" to sub.id,
            ))
        }
        sub.yookassaPaymentId = payment.id
        sub.status = Subscription.STATUS_PENDING
        sub.save()
        return ResponseEntity.ok(mapOf(
            "subscriptionId" to sub.id,
            "status" to sub.status,
            "confirmationUrl" to payment.confirmation?.confirmation_url,
        ))
    }

    // Fulfillment для бесплатных (акционных) подписок — та же логика, что в PublicPaymentController
    // на успешный вебхук, только без реального платежа. Держим в одном месте не получилось бы без
    // циклической зависимости контроллер<->контроллер, поэтому дублируем короткую SITE-ветку —
    // см. идентичный блок в PublicPaymentController.webhook (SONG ничего дополнительно не требует:
    // сам факт PAID-записи уже есть владение).
    private fun applyFulfillment(sub: Subscription) {
        if (sub.scope != Subscription.SCOPE_SITE) return
        val user = SiteUser.getSiteUserById(sub.siteUserId, db, storageService, storageApiClient) ?: return
        val now = System.currentTimeMillis()
        val currentUntil = user.sitePremiumUntil?.time?.takeIf { it > now } ?: now
        user.sitePremiumUntil = java.sql.Timestamp(currentUntil + sub.periodDays * 24L * 3600_000L)
        user.save()
    }

    // ---- Список (личный кабинет) -----------------------------------------------------------------

    @GetMapping("/list")
    fun list(request: HttpServletRequest): List<Map<String, Any?>> {
        val user = currentUser(request)
        return Subscription.loadByUser(user.id, db, storageService, storageApiClient).map { sub ->
            val songName = sub.idSong?.let { Settings.loadFromDbById(it, db, storageService = storageService, storageApiClient = storageApiClient)?.songName }
            mapOf(
                "id" to sub.id,
                "scope" to sub.scope,
                "idSong" to sub.idSong,
                "songName" to songName,
                "finalPrice" to sub.finalPrice,
                "status" to sub.status,
                "autoRenew" to sub.autoRenew,
                "createdAt" to sub.createdAt.toString(),
                "paidAt" to sub.paidAt?.toString(),
            )
        }
    }

    // Отключает автопродление подписки на сайт — доступ доживает до текущего sitePremiumUntil,
    // повторного списания больше не будет (см. план: POST /cancel).
    @PostMapping("/cancel")
    fun cancel(@RequestParam id: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val sub = Subscription.getById(id, db, storageService, storageApiClient)
            ?.takeIf { it.siteUserId == user.id && it.scope == Subscription.SCOPE_SITE }
            ?: return ResponseEntity.notFound().build()
        sub.autoRenew = false
        sub.save()
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}

package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp

/**
 * Вебхук ЮKassa. Намеренно ВНЕ SiteAuthInterceptor (путь не начинается с /api/public/account) —
 * вызывающая сторона это сама ЮKassa, а не залогиненный пользователь сайта.
 *
 * Ключевой инвариант (см. план, Verification): телу вебхука НЕ доверяем напрямую — событие лишь
 * триггер, статус платежа ВСЕГДА перезапрашивается у ЮKassa (verifyAndFetch) перед fulfillment.
 * Идемпотентно: повторный вызов на уже PAID-подписке no-op (проверка status до обработки).
 */
@RestController
@RequestMapping("/api/public/payment")
class PublicPaymentController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val paymentService: PaymentService,
) {
    private val db get() = WORKING_DATABASE

    @PostMapping("/webhook")
    fun webhook(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Any> {
        // Структура события ЮKassa: {"event":"payment.succeeded","object":{"id":"...","status":"..."}}
        @Suppress("UNCHECKED_CAST")
        val paymentObject = body["object"] as? Map<String, Any?>
        val paymentId =
            paymentObject?.get("id") as? String
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "no_payment_id"))

        // Заказ «Корзины» — несколько подписок делят один yookassa_payment_id (один платёж на
        // несколько песен); одиночная покупка — список из одного элемента, поведение не меняется.
        val subs = Subscription.getAllByYookassaPaymentId(paymentId, db, storageService, storageApiClient)
        if (subs.isEmpty()) return ResponseEntity.ok(mapOf("ok" to true, "note" to "unknown_subscription")) // не наш платёж/уже удалён — не 500

        // Идемпотентность: если все позиции заказа уже обработаны — no-op.
        if (subs.all { it.status == Subscription.STATUS_PAID }) return ResponseEntity.ok(mapOf("ok" to true))

        // Не доверяем телу вебхука — перезапрашиваем статус напрямую у ЮKassa (один раз на весь заказ).
        val verified =
            paymentService.verifyAndFetch(paymentId)
                ?: return ResponseEntity.status(502).body(mapOf("error" to "verify_failed"))

        when (verified.status) {
            "succeeded" -> {
                subs.filter { it.status != Subscription.STATUS_PAID }.forEach { sub ->
                    sub.status = Subscription.STATUS_PAID
                    sub.paidAt = Timestamp(System.currentTimeMillis())
                    verified.payment_method?.id?.let { if (it.isNotBlank()) sub.yookassaPaymentMethodId = it }
                    sub.save()
                    applyFulfillment(sub)
                }
            }
            "canceled" -> {
                subs.filter { it.status != Subscription.STATUS_PAID }.forEach { sub ->
                    sub.status = Subscription.STATUS_FAILED
                    sub.save()
                }
            }
            else -> { /* pending/waiting_for_capture — ничего не делаем, дождёмся следующего события */ }
        }
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    // SONG: ничего дополнительно не требуется — сам факт PAID-записи уже есть владение
    // (см. Subscription.isSubscribedToSong в PublicPlayerController).
    // SITE: продлевает SiteUser.sitePremiumUntil от текущего максимума (now либо уже проставленной
    // даты) — повторная оплата НЕ теряет ранее оплаченный хвост срока.
    private fun applyFulfillment(sub: Subscription) {
        if (sub.scope != Subscription.SCOPE_SITE) return
        val user = SiteUser.getSiteUserById(sub.siteUserId, db, storageService, storageApiClient) ?: return
        val now = System.currentTimeMillis()
        val currentUntil = user.sitePremiumUntil?.time?.takeIf { it > now } ?: now
        user.sitePremiumUntil = Timestamp(currentUntil + sub.periodDays * 24L * 3600_000L)
        user.save()
        user.sendWelcomePremiumMessageIfNeeded()
    }
}

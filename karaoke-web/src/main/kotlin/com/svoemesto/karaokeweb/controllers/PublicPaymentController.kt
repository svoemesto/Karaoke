package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.SubscriptionFulfillmentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Вебхук ЮKassa. Намеренно ВНЕ SiteAuthInterceptor (путь не начинается с /api/public/account) —
 * вызывающая сторона это сама ЮKassa, а не залогиненный пользователь сайта.
 *
 * Ключевой инвариант (см. план, Verification): телу вебхука НЕ доверяем напрямую — событие лишь
 * триггер, статус платежа ВСЕГДА перезапрашивается у ЮKassa (verifyAndFetch) перед fulfillment.
 *
 * Идемпотентность (аудит #336, BR-020): перевод подписки в PAID и начисление (продление
 * `sitePremiumUntil`) выполняются ОДНОЙ транзакцией через [SubscriptionFulfillmentService],
 * а захват подписки — условным `UPDATE ... WHERE status <> 'PAID'`. Поэтому:
 *
 * - повторная доставка вебхука (ЮKassa повторяет при таймауте) не продлевает срок дважды;
 * - падение между «статус = PAID» и начислением откатывает оба изменения, обработчик
 *   возвращает ошибку, и ЮKassa повторяет доставку — оплативший пользователь не остаётся без
 *   доступа (раньше: статус PAID сохранялся, начисление терялось, а повторный вебхук видел
 *   «все PAID» и отвечал 200 no-op).
 */

/**
 * Контроллер (HTTP/WebSocket endpoints) для public payment .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/public/payment")
class PublicPaymentController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val paymentService: PaymentService,
    /**
     * Начисление оплаченной подписки (транзакция «статус + продление»). Шов для тестов:
     * подставляется заглушка, которая считает вызовы начисления.
     */
    private val fulfillmentService: SubscriptionFulfillmentService = SubscriptionFulfillmentService(),
) {
    @PostMapping("/webhook")
    fun webhook(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Any> = handleWebhook(body, WORKING_DATABASE)

    /**
     * Обработка вебхука. Шов для тестов: [database] подменяется поддельным соединением.
     */
    internal fun handleWebhook(
        body: Map<String, Any?>,
        database: KaraokeConnection,
    ): ResponseEntity<Any> {
        // Структура события ЮKassa: {"event":"payment.succeeded","object":{"id":"...","status":"..."}}
        @Suppress("UNCHECKED_CAST")
        val paymentObject = body["object"] as? Map<String, Any?>
        val paymentId =
            paymentObject?.get("id") as? String
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "no_payment_id"))

        // Заказ «Корзины» — несколько подписок делят один yookassa_payment_id (один платёж на
        // несколько песен); одиночная покупка — список из одного элемента, поведение не меняется.
        val subs = Subscription.getAllByYookassaPaymentId(paymentId, database, storageService, storageApiClient)
        if (subs.isEmpty()) return ResponseEntity.ok(mapOf("ok" to true, "note" to "unknown_subscription")) // не наш платёж/уже удалён — не 500

        // Идемпотентность: если все позиции заказа уже обработаны — no-op. Начисление НЕ может
        // «потеряться» после перевода в PAID: статус и продление коммитятся одной транзакцией
        // (SubscriptionFulfillmentService), поэтому PAID ⇒ начисление уже было.
        if (subs.all { it.status == Subscription.STATUS_PAID }) return ResponseEntity.ok(mapOf("ok" to true))

        // Не доверяем телу вебхука — перезапрашиваем статус напрямую у ЮKassa (один раз на весь заказ).
        val verified =
            paymentService.verifyAndFetch(paymentId)
                ?: return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf("error" to "verify_failed"))

        when (verified.status) {
            "succeeded" -> {
                subs.filter { it.status != Subscription.STATUS_PAID }.forEach { sub ->
                    // Атомарный захват + начисление в одной транзакции. `false` = подписку уже
                    // обработал параллельный вызов (повторный вебхук) — это не ошибка.
                    fulfillmentService.fulfillPaid(
                        sub = sub,
                        yookassaPaymentId = paymentId,
                        paymentMethodId = verified.payment_method?.id ?: "",
                    )
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
}

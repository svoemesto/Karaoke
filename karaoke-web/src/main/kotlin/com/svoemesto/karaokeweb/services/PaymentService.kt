package com.svoemesto.karaokeweb.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.Subscription
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.UUID

/**
 * Интеграция с ЮKassa (приём платежей для самозанятого — авто-чеки в "Мой налог" по 422-ФЗ, см.
 * план монетизации). Обслуживает единый платёжный конвейер для scope=SONG (бессрочно) и scope=SITE
 * (период + опционально автопродление через сохранённый payment_method).
 *
 * ВАЖНО: без реальных YOOKASSA_SHOP_ID/YOOKASSA_SECRET_KEY (магазин ещё не зарегистрирован — см.
 * Prerequisites плана) этот сервис не может быть end-to-end проверен. hasCredentials() — явная
 * защита, чтобы не пытаться дёргать API без ключей (упало бы Basic Auth с пустой строкой).
 *
 * Все внешние вызовы идут через host-nginx прокси (yookassa.proxy-url), см. WebClientConfig —
 * тот же обход MTU black-hole, что и CAPTCHA_PROXY_URL/STORAGE_PROXY_URL.
 */

/**
 * Сервис для payment .
 *
 * @see docs/features/async-process-queue.md
 */
@Service
class PaymentService(
    @Qualifier("yookassaWebClient") private val webClient: WebClient,
) {
    private val objectMapper = ObjectMapper()

    fun hasCredentials(): Boolean =
        System.getenv("YOOKASSA_SHOP_ID")?.isNotBlank() == true ||
            System.getProperty("yookassa.shop-id")?.isNotBlank() == true

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Amount(
        val value: String,
        val currency: String = "RUB",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Confirmation(
        val type: String = "redirect",
        val confirmation_url: String? = null,
        val return_url: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ReceiptCustomer(
        val email: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ReceiptItem(
        val description: String,
        val quantity: String = "1",
        val amount: Amount,
        // Самозанятый (НПД): услуга не облагается НДС отдельно (в чек "Мой налог" вносится единая
        // ставка по 422-ФЗ) — vat_code=1 ("без НДС"), payment_subject=service. Проверить перед боевым
        // запуском по реальным настройкам магазина ЮKassa (см. Prerequisites).
        val vat_code: Int = 1,
        val payment_mode: String = "full_payment",
        val payment_subject: String = "service",
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Receipt(
        val customer: ReceiptCustomer,
        val items: List<ReceiptItem>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PaymentMethodRef(
        val id: String? = null,
        val saved: Boolean? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PaymentResponse(
        val id: String = "",
        val status: String = "",
        val paid: Boolean = false,
        val confirmation: Confirmation? = null,
        val payment_method: PaymentMethodRef? = null,
    )

    private fun postPayment(
        idempotenceKey: String,
        body: Map<String, Any>,
    ): PaymentResponse? =
        webClient
            .post()
            .uri("/payments")
            .header("Idempotence-Key", idempotenceKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono<PaymentResponse>()
            .block()

    /**
     * Создаёт платёж на новую подписку (redirect-подтверждение — пользователь уходит на страницу
     * ЮKassa). Для scope=SITE с автопродлением передаём save_payment_method=true, чтобы получить
     * payment_method.id для будущих chargeRecurring(). Idempotence-Key = "sub-<id>" — повторный вызов
     * с тем же id (например, повторный клик "Оплатить" до редиректа) не создаёт дублирующий платёж.
     *
     * save_payment_method=true требует, чтобы менеджер ЮKassa включил рекуррентные платежи для
     * магазина — без этого ЮKassa отвечает 4xx (403). Если это ещё не включено, не проваливаем всё
     * оформление подписки: повторяем тот же платёж без save_payment_method (отдельный
     * Idempotence-Key) — подписка оформится как обычная, разовая, без автопродления. Как только
     * рекуррентные платежи будут включены в кабинете ЮKassa, автопродление заработает само, без
     * повторного деплоя.
     */
    fun createPayment(
        sub: Subscription,
        description: String,
        email: String,
        returnUrl: String,
        saveMethod: Boolean,
    ): PaymentResponse? {
        if (!hasCredentials()) {
            println("PaymentService: YOOKASSA_SHOP_ID/SECRET_KEY не заданы — платёж не создан (sub=${sub.id})")
            return null
        }
        val body =
            mutableMapOf<String, Any>(
                "amount" to Amount(value = "%.2f".format(sub.finalPrice)),
                "capture" to true,
                "confirmation" to Confirmation(type = "redirect", return_url = returnUrl),
                "description" to description,
                "receipt" to
                    Receipt(
                        customer = ReceiptCustomer(email = email),
                        items = listOf(ReceiptItem(description = description, amount = Amount(value = "%.2f".format(sub.finalPrice)))),
                    ),
            )
        if (saveMethod) body["save_payment_method"] = true
        val idempotenceKey = "sub-${sub.id}-${sub.createdAt.time}"
        return try {
            postPayment(idempotenceKey, body)
        } catch (e: WebClientResponseException) {
            if (saveMethod && e.statusCode.is4xxClientError) {
                println(
                    "PaymentService.createPayment: save_payment_method отклонён ЮKassa (${e.statusCode}) для sub=${sub.id} " +
                        "— повтор без сохранения способа оплаты (проверьте, включены ли рекуррентные платежи в кабинете ЮKassa)",
                )
                body.remove("save_payment_method")
                try {
                    postPayment("$idempotenceKey-nosave", body)
                } catch (e2: Exception) {
                    println("PaymentService.createPayment: повтор без save_payment_method тоже не удался для sub=${sub.id}: ${e2.message}")
                    null
                }
            } else {
                println("PaymentService.createPayment: ошибка создания платежа для sub=${sub.id}: ${e.message}")
                null
            }
        } catch (e: Exception) {
            println("PaymentService.createPayment: ошибка создания платежа для sub=${sub.id}: ${e.message}")
            null
        }
    }

    /**
     * Создаёт ОДИН платёж на НЕСКОЛЬКО позиций (заказ «Корзины» — несколько подписок scope=SONG,
     * объединённых общим orderId/yookassa_payment_id). `items` — (описание, цена) на каждую песню;
     * сумма позиций чека обязана точно совпасть с `totalAmount` (54-ФЗ). Idempotence-Key завязан на
     * orderId — повторный клик "Оплатить всё" до редиректа не создаёт дублирующий платёж.
     */
    fun createCartPayment(
        orderId: String,
        totalAmount: Double,
        items: List<Pair<String, Double>>,
        email: String,
        returnUrl: String,
    ): PaymentResponse? {
        if (!hasCredentials()) {
            println("PaymentService: YOOKASSA_SHOP_ID/SECRET_KEY не заданы — платёж для заказа $orderId не создан")
            return null
        }
        val description = "Подписка на ${items.size} " + pluralSongsRu(items.size) + " (заказ корзины)"
        val body =
            mapOf(
                "amount" to Amount(value = "%.2f".format(totalAmount)),
                "capture" to true,
                "confirmation" to Confirmation(type = "redirect", return_url = returnUrl),
                "description" to description,
                "receipt" to
                    Receipt(
                        customer = ReceiptCustomer(email = email),
                        items =
                            items.map { (desc, price) ->
                                ReceiptItem(description = desc.take(128), amount = Amount(value = "%.2f".format(price)))
                            },
                    ),
            )
        return try {
            webClient
                .post()
                .uri("/payments")
                .header("Idempotence-Key", "order-$orderId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono<PaymentResponse>()
                .block()
        } catch (e: Exception) {
            println("PaymentService.createCartPayment: ошибка создания платежа для заказа $orderId: ${e.message}")
            null
        }
    }

    private fun pluralSongsRu(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> "песен"
            mod10 == 1 -> "песню"
            mod10 in 2..4 -> "песни"
            else -> "песен"
        }
    }

    /**
     * Повторное списание по сохранённому payment_method_id (автопродление подписки на сайт).
     * Idempotence-Key завязан на конкретный цикл продления (передаётся снаружи), чтобы ретрай
     * шедулера в тот же день не задвоил списание.
     */
    fun chargeRecurring(
        sub: Subscription,
        description: String,
        email: String,
        idempotenceKey: String,
    ): PaymentResponse? {
        if (!hasCredentials() || sub.yookassaPaymentMethodId.isBlank()) return null
        val body =
            mapOf(
                "amount" to Amount(value = "%.2f".format(sub.finalPrice)),
                "capture" to true,
                "payment_method_id" to sub.yookassaPaymentMethodId,
                "description" to description,
                "receipt" to
                    Receipt(
                        customer = ReceiptCustomer(email = email),
                        items = listOf(ReceiptItem(description = description, amount = Amount(value = "%.2f".format(sub.finalPrice)))),
                    ),
            )
        return try {
            webClient
                .post()
                .uri("/payments")
                .header("Idempotence-Key", idempotenceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono<PaymentResponse>()
                .block()
        } catch (e: Exception) {
            println("PaymentService.chargeRecurring: ошибка автосписания для sub=${sub.id}: ${e.message}")
            null
        }
    }

    /**
     * Перезапрашивает статус платежа У ЮKASSA (не доверяем телу вебхука напрямую — см. план,
     * раздел Verification: "статус берётся перезапросом, не из тела").
     */
    fun verifyAndFetch(paymentId: String): PaymentResponse? {
        if (!hasCredentials()) return null
        return try {
            webClient
                .get()
                .uri("/payments/$paymentId")
                .retrieve()
                .bodyToMono<PaymentResponse>()
                .block()
        } catch (e: Exception) {
            println("PaymentService.verifyAndFetch: ошибка проверки статуса платежа $paymentId: ${e.message}")
            null
        }
    }

    fun newIdempotenceKey(): String = UUID.randomUUID().toString()
}

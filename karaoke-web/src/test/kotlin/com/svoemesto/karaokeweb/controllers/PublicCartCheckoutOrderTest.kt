package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.PriceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Регрессионные тесты «до/после» порядка операций checkout корзины
 * (аудит #336, BR-014; OpenProject #365).
 *
 * **Что было не так.** `PublicCartController.checkout` создавал строки `Subscription`, сразу
 * удалял позиции корзины и только ПОТОМ создавал платёж. Если `createCartPayment` возвращал
 * `null` (нет ключей ЮKassa / сбой сети), клиент получал 503, но корзина была уже пуста, а
 * `CREATED`-подписки висели без платежа; повторить checkout было нельзя — следующий вызов
 * отвечал `cart_empty`. Идемпотентности не было вовсе: `orderId` — новый `UUID` на каждый запрос,
 * поэтому двойной submit создавал второй заказ и второй платёж.
 *
 * **После правки** корзина очищается и заказ переводится в `PENDING` только ПОСЛЕ успешного
 * создания платежа; при отказе строки заказа откатываются, корзина остаётся нетронутой, а
 * незавершённый заказ переиспользуется по стабильному `order_id` (см. [PaymentOrderReuse]).
 *
 * **До правки** тест «платёж не создан — откат заказа, корзина цела» падал: шаг удаления корзины
 * выполнялся до проверки результата платежа.
 *
 * @see specs/365-pkg-money-idempotency/report.md
 */
class PublicCartCheckoutOrderTest {
    private fun controller(): PublicCartController =
        PublicCartController(
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            priceService = Mockito.mock(PriceService::class.java),
            paymentService = Mockito.mock(PaymentService::class.java),
            publicSiteUrl = "https://karaoke.test",
        )

    private fun payment(
        id: String = "pay-1",
        status: String = "pending",
    ): PaymentService.PaymentResponse =
        PaymentService.PaymentResponse(
            id = id,
            status = status,
            confirmation = PaymentService.Confirmation(confirmation_url = "https://yookassa.test/checkout"),
        )

    @Test
    fun `платёж создан - заказ фиксируется, корзина очищается`() {
        val steps = mutableListOf<String>()

        val response =
            controller().commitCartOrder(
                orderId = "order-1",
                payment = payment(),
                onPaymentCreated = { steps += "mark-pending+clear-cart" },
                onRollback = { steps += "rollback" },
            )

        assertEquals(200, response.statusCode.value())
        assertEquals(listOf("mark-pending+clear-cart"), steps, "шаги: $steps")
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any?>
        assertEquals("order-1", body["orderId"])
        assertEquals("PENDING", body["status"])
    }

    @Test
    fun `платёж не создан - заказ откатывается, корзина не трогается`() {
        val steps = mutableListOf<String>()

        val response =
            controller().commitCartOrder(
                orderId = "order-1",
                payment = null,
                onPaymentCreated = { steps += "mark-pending+clear-cart" },
                onRollback = { steps += "rollback" },
            )

        assertEquals(503, response.statusCode.value())
        assertEquals(listOf("rollback"), steps, "до фиксации заказа дело доходить не должно: $steps")
        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any?>
        assertEquals("payment_unavailable", body["error"])
    }

    @Test
    fun `пустой id платежа трактуется как отказ`() {
        var rolledBack = false
        var committed = false

        val response =
            controller().commitCartOrder(
                orderId = "order-1",
                payment = payment(id = ""),
                onPaymentCreated = { committed = true },
                onRollback = { rolledBack = true },
            )

        assertEquals(503, response.statusCode.value())
        assertTrue(rolledBack, "заказ должен быть откатан")
        assertFalse(committed, "без id платежа фиксировать заказ нельзя")
    }
}

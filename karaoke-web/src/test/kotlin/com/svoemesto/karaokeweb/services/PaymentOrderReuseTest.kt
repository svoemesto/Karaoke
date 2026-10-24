package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Регрессионные тесты «до/после» решения о переиспользовании незавершённого заказа
 * (аудит #336: BR-021 — оформление подписки, BR-014 — checkout корзины; OpenProject #365).
 *
 * **Что было не так.** Оба пути создания платежа переиспользовали существующий заказ только при
 * статусе платежа ровно `pending`:
 *
 * ```
 * if (existingPayment?.status == "pending") { ... }
 * ```
 *
 * Любой другой исход проваливался в создание ВТОРОГО платежа:
 *
 * - `succeeded` — платёж уже прошёл, а вебхук задержался (типичная задержка после возврата с
 *   оплаты) → пользователь получал второй `confirmationUrl` и мог заплатить дважды;
 * - `null` (ЮKassa/сеть недоступны) → новый платёж создавался «вслепую»;
 * - `canceled` — заказ мёртв, но и его нельзя молча игнорировать (строка висела бы PENDING вечно).
 *
 * **После правки** решение вынесено в [PaymentOrderReuse]: переиспользуется любой не-`canceled`
 * статус, `succeeded` обрабатывается как «уже оплачено», неизвестный статус — fail-closed (503,
 * новый платёж не создаём).
 *
 * **До правки** тесты про `succeeded`, `canceled` и `UNAVAILABLE` падают: все статусы, кроме
 * `pending`, вели к созданию нового платежа.
 *
 * @see specs/365-pkg-money-idempotency/report.md
 */
class PaymentOrderReuseTest {
    @Test
    fun `pending - переиспользуем тот же платёж`() {
        assertEquals(ReuseOutcome.REUSE_PAYMENT, PaymentOrderReuse.decide("pending"))
    }

    @Test
    fun `waiting_for_capture - переиспользуем тот же платёж`() {
        assertEquals(ReuseOutcome.REUSE_PAYMENT, PaymentOrderReuse.decide("waiting_for_capture"))
    }

    @Test
    fun `succeeded - подписка уже оплачена, второй платёж не создаётся`() {
        assertEquals(ReuseOutcome.ALREADY_PAID, PaymentOrderReuse.decide("succeeded"))
    }

    @Test
    fun `canceled - заказ мёртв, создаём новый`() {
        assertEquals(ReuseOutcome.CREATE_NEW, PaymentOrderReuse.decide("canceled"))
    }

    @Test
    fun `ЮKassa недоступна - fail-closed, новый платёж не создаётся`() {
        assertEquals(ReuseOutcome.UNAVAILABLE, PaymentOrderReuse.decide(null))
    }

    @Test
    fun `неизвестный статус - fail-closed, а не новый счёт`() {
        assertEquals(ReuseOutcome.UNAVAILABLE, PaymentOrderReuse.decide("refunded"))
        assertEquals(ReuseOutcome.UNAVAILABLE, PaymentOrderReuse.decide(""))
    }
}

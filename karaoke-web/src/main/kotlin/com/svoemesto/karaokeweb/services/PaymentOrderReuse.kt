package com.svoemesto.karaokeweb.services

/**
 * Решение «что делать с уже существующим незавершённым заказом» — единое для обоих путей
 * оформления: подписка на сайт/песню (`PublicSubscriptionController`, аудит #336 BR-021) и
 * корзина (`PublicCartController`, аудит #336 BR-014).
 *
 * **Почему отдельный объект.** Раньше решение было зашито в `if (existingPayment?.status ==
 * "pending")`, и любого другого статуса хватало, чтобы провалиться в создание ВТОРОГО платежа.
 * Для денег это недопустимо: `succeeded` (вебхук задержался) означает «уже оплачено», а `null`
 * (ЮKassa недоступна) — «не знаем, поэтому не создаём». Оба не-`canceled`-статуса обязаны
 * переиспользовать существующий заказ.
 *
 * Fail-closed: неизвестный или пустой статус трактуется как [UNAVAILABLE] — новый платёж не
 * создаётся, клиент получает 503 и повторяет позже. Худший исход — «повторите», а не второе
 * списание.
 *
 * @see specs/365-pkg-money-idempotency/report.md
 */
internal enum class ReuseOutcome {
    /** Платёж ещё жив (`pending` / `waiting_for_capture`) — отдать тот же `confirmationUrl`. */
    REUSE_PAYMENT,

    /** Платёж уже прошёл (`succeeded`), вебхук мог не дойти — начислить подписку, второй платёж не создавать. */
    ALREADY_PAID,

    /** Платёж отменён (`canceled`) — заказ мёртв, помечаем FAILED и создаём новый. */
    CREATE_NEW,

    /** Статус неизвестен или ЮKassa недоступна — новый платёж НЕ создаём (503, повтор позже). */
    UNAVAILABLE,
}

internal object PaymentOrderReuse {
    fun decide(paymentStatus: String?): ReuseOutcome =
        when (paymentStatus) {
            "pending", "waiting_for_capture" -> ReuseOutcome.REUSE_PAYMENT
            "succeeded" -> ReuseOutcome.ALREADY_PAID
            "canceled" -> ReuseOutcome.CREATE_NEW
            else -> ReuseOutcome.UNAVAILABLE
        }
}

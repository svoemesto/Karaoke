package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессионные тесты «до/после» идемпотентности автопродления
 * (аудит #336, BR-514; OpenProject #365).
 *
 * **Что было не так.** `SubscriptionRenewalScheduler.tryRenew`:
 *
 * 1. отбрасывал ответ ЮKassa — `result.id` не сохранялся в `renewal.yookassaPaymentId`, поэтому
 *    вебхук о списании не мог найти подписку (`unknown_subscription`) и оплаченный период терялся;
 * 2. списывал деньги до фиксации результата, без транзакции;
 * 3. строил `Idempotence-Key` от ДАТЫ ЗАПУСКА (`renew-<userId>-<yyyy-MM-dd>`). Сбой между
 *    списанием и `save()` приводил к тому, что на следующем тике создавалась новая строка с новой
 *    датой → новый ключ → **повторное списание за тот же период**.
 *
 * **После правки** ключ привязан к СТРОКЕ продления (`renew-<userId>-sub<id>`): ключ коммитится
 * вместе со строкой ДО списания, поэтому повторный тик по той же строке возвращает тот же платёж
 * ЮKassa (дедупликация), а новая попытка (новая строка) получает новый ключ. Статус PAID и
 * продление `sitePremiumUntil` фиксируются одной транзакцией через
 * [SubscriptionFulfillmentService] (общая точка с вебхуком, BR-020).
 *
 * **До правки** тест «ключ привязан к строке продления, а не к дате запуска» падал: ключ от
 * (userId, сегодняшняя дата) одинаков для разных строк и менялся на следующий день, то есть
 * повторный тик после сбоя списывал деньги второй раз.
 *
 * @see specs/365-pkg-money-idempotency/report.md
 */
class SubscriptionRenewalIdempotenceTest {
    @Test
    fun `ключ идемпотентности стабилен для одной строки продления`() {
        assertEquals(
            SubscriptionRenewalScheduler.idempotenceKeyFor(5, 77),
            SubscriptionRenewalScheduler.idempotenceKeyFor(5, 77),
            "повторный тик по той же строке обязан вернуть тот же ключ (иначе — второе списание)",
        )
    }

    @Test
    fun `ключ привязан к строке продления, а не к дате запуска`() {
        val first = SubscriptionRenewalScheduler.idempotenceKeyFor(5, 77)
        val second = SubscriptionRenewalScheduler.idempotenceKeyFor(5, 78)

        assertNotEquals(
            first,
            second,
            "новая строка продления = новая попытка списания, ключи обязаны различаться",
        )
        assertTrue(first.contains("77"), "ключ содержит id строки продления: $first")
        assertTrue(first.startsWith("renew-5-"), "ключ принадлежит пользователю 5: $first")
    }

    @Test
    fun `ключ не зависит от порядка вызовов и других пользователей`() {
        val key = SubscriptionRenewalScheduler.idempotenceKeyFor(5, 77)

        assertNotEquals(key, SubscriptionRenewalScheduler.idempotenceKeyFor(6, 77))
        assertEquals(key, SubscriptionRenewalScheduler.idempotenceKeyFor(5, 77))
    }
}

package com.svoemesto.karaokeweb.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.PromoRule
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Расчёт итоговой цены тарифа с учётом активных акций (tbl_promo_rules). Единственный source of
 * truth для цены — вызывается и на GET /price (для показа пользователю), и заново внутри
 * POST /create (сервер никогда не доверяет цене, присланной клиентом, см. план, Verification).
 *
 * Побеждает акция с наибольшим priority среди применимых (первая подходящая в отсортированном
 * списке) — не суммируем несколько акций одновременно, чтобы не пришлось разбираться в порядке
 * начисления процентов.
 */

/**
 * Сервис для price .
 *
 * @see docs/features/async-process-queue.md
 */
@Service
class PriceService {
    data class PriceResult(
        val base: Double,
        val discount: Double,
        val final: Double,
        val promoApplied: String?,
        // >0, если сверху была применена ещё и постоянная персональная скидка пользователя
        // (SiteUser.personalDiscountPercent) — суммируется поверх любой акции, не конкурирует с ней.
        val personalDiscountPercent: Double = 0.0,
    )

    private val objectMapper = ObjectMapper()

    fun computePrice(
        tariff: PriceTariff,
        user: SiteUser,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        // Порядковый номер ЭТОЙ покупки для TYPE_NTH_FREE. null (по умолчанию, одиночная покупка) —
        // считаем как раньше, countPaid+1. Корзина передаёт явный номер позиции в заказе (см.
        // computeCartPrice), чтобы не пересчитывать countPaid на каждую позицию отдельно.
        ordinalOverride: Int? = null,
    ): PriceResult {
        val base = tariff.priceRub
        val rules =
            PromoRule
                .loadActive(database, storageService, storageApiClient)
                .filter { it.appliesToScope(tariff.scope) }
                .sortedByDescending { it.priority }

        for (rule in rules) {
            val percent = discountPercentFor(rule, tariff, user, database, storageService, storageApiClient, ordinalOverride)
            if (percent > 0.0) {
                val discount = (base * percent / 100.0).coerceIn(0.0, base)
                val final = (base - discount).coerceAtLeast(0.0)
                return applyPersonalDiscount(base, discount, final, rule.name, user)
            }
        }
        return applyPersonalDiscount(base, 0.0, base, null, user)
    }

    // Постоянная персональная скидка пользователя (SiteUser.personalDiscountPercent, выставляется
    // вручную админом) — суммируется поверх РЕЗУЛЬТАТА любой акции (или базовой цены, если акций не
    // было), а не конкурирует с ними как ещё одно правило. Применяется к любому заказу без исключений.
    private fun applyPersonalDiscount(
        base: Double,
        discountSoFar: Double,
        finalSoFar: Double,
        promoName: String?,
        user: SiteUser,
    ): PriceResult {
        val personalPercent = user.personalDiscountPercent.coerceIn(0.0, 100.0)
        if (personalPercent <= 0.0) return PriceResult(base, discountSoFar, finalSoFar, promoName, 0.0)
        val finalAfterPersonal = (finalSoFar * (1.0 - personalPercent / 100.0)).coerceAtLeast(0.0)
        return PriceResult(base, base - finalAfterPersonal, finalAfterPersonal, promoName, personalPercent)
    }

    // Цена корзины «Корзина»: список позиций (по одной на песню, в порядке добавления). Один результат
    // на каждую позицию, а не общая скидка на сумму, — нужно для fiscal-чека ЮKassa (54-ФЗ: у каждой
    // позиции чека своя цена, сумма позиций обязана совпасть с суммой платежа).
    //
    // Приоритет: если найдена активная CART_BULK_PERCENT-акция, применимая по количеству позиций в этом
    // заказе — она побеждает целиком (как и одиночные акции, не суммируются) и скидывает процент с КАЖДОЙ
    // позиции. Иначе — каждая позиция считается независимо через computePrice, включая генерализованный
    // NTH_FREE (порядковый номер растёт по всем покупкам пользователя, начиная с текущего countPaid+1).
    fun computeCartPrice(
        tariff: PriceTariff,
        cartSize: Int,
        user: SiteUser,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): List<PriceResult> {
        if (cartSize <= 0) return emptyList()
        val base = tariff.priceRub
        val rules =
            PromoRule
                .loadActive(database, storageService, storageApiClient)
                .filter { it.appliesToScope(tariff.scope) }
                .sortedByDescending { it.priority }

        val bulkRule =
            rules.firstOrNull {
                it.type == PromoRule.TYPE_CART_BULK_PERCENT && cartSize >= asInt(params(it)["minQty"], Int.MAX_VALUE)
            }
        if (bulkRule != null) {
            val percent = asDouble(params(bulkRule)["percent"])
            val discount = (base * percent / 100.0).coerceIn(0.0, base)
            val final = (base - discount).coerceAtLeast(0.0)
            return List(cartSize) { applyPersonalDiscount(base, discount, final, bulkRule.name, user) }
        }

        val startOrdinal = Subscription.countPaid(user.id, tariff.scope, database, storageService, storageApiClient)
        return (1..cartSize).map { i ->
            computePrice(tariff, user, database, storageService, storageApiClient, ordinalOverride = startOrdinal + i)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun params(rule: PromoRule): Map<String, Any?> =
        try {
            objectMapper.readValue(rule.paramsJson, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            emptyMap()
        }

    private fun asDouble(
        v: Any?,
        default: Double = 0.0,
    ): Double =
        when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }

    private fun asInt(
        v: Any?,
        default: Int = 0,
    ): Int =
        when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }

    @Suppress("UNCHECKED_CAST")
    private fun asIntList(v: Any?): List<Int> = (v as? List<Any?>)?.mapNotNull { asInt(it, -1).takeIf { n -> n >= 0 } } ?: emptyList()

    private fun discountPercentFor(
        rule: PromoRule,
        tariff: PriceTariff,
        user: SiteUser,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        ordinalOverride: Int? = null,
    ): Double {
        val p = params(rule)
        return when (rule.type) {
            PromoRule.TYPE_FLAT_PERCENT -> asDouble(p["percent"])

            // Скидка % пользователям младше N часов с момента регистрации.
            PromoRule.TYPE_NEW_USER_PERCENT -> {
                val hours = asDouble(p["hoursAfterRegistration"], 24.0)
                val ageMs = System.currentTimeMillis() - user.createdAt.time
                if (ageMs in 0..(hours * 3600_000).toLong()) asDouble(p["percent"]) else 0.0
            }

            // Каждая N-я ОПЛАЧЕННАЯ подписка (в рамках scope) — бесплатно (100% скидка).
            PromoRule.TYPE_NTH_FREE -> {
                val n = asInt(p["n"], 0)
                if (n <= 0) {
                    0.0
                } else {
                    val ordinalOfThisOne =
                        ordinalOverride
                            ?: (Subscription.countPaid(user.id, tariff.scope, database, storageService, storageApiClient) + 1)
                    if (ordinalOfThisOne % n == 0) 100.0 else 0.0
                }
            }

            // "Счастливый час" — скидка % в указанных часах суток (0-23) и/или днях недели
            // (ISO-8601: 1=Пн..7=Вс). Пустой список часов/дней = ограничение не действует по этой оси.
            PromoRule.TYPE_HAPPY_HOUR -> {
                val hours = asIntList(p["hours"])
                val daysOfWeek = asIntList(p["daysOfWeek"])
                val now = LocalDateTime.now()
                val hourOk = hours.isEmpty() || now.hour in hours
                val dayOk = daysOfWeek.isEmpty() || now.dayOfWeek.value in daysOfWeek
                if (hourOk && dayOk) asDouble(p["percent"]) else 0.0
            }

            else -> 0.0
        }
    }
}

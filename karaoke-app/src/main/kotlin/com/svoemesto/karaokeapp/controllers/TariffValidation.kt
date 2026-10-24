package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.model.PriceTariff

/**
 * Инварианты тарифа, проверяемые ДО записи в `tbl_price_tariffs` (BR-219/P-11-01).
 *
 * Раньше админский API сохранял значения, ломающие денежный путь: отрицательная цена
 * (на публичной `/price` даёт `coerceIn(0.0, base)` → 500), `periodDays = 0` у SITE
 * (оплата не продлевает премиум), несколько `is_default = true` на один scope.
 *
 * @return код ошибки для ответа 400 (`bad_price`, `bad_period_days`) или null, если значения допустимы.
 */
internal fun validateTariffValues(
    scope: String,
    priceRub: Double,
    periodDays: Int,
): String? =
    when {
        priceRub < 0.0 -> "bad_price"
        scope == PriceTariff.SCOPE_SITE && periodDays <= 0 -> "bad_period_days"
        else -> null
    }

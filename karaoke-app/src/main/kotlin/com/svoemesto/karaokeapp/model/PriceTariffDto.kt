package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO для price tariff: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class PriceTariffDto(
    val id: Long = 0,
    val scope: String = PriceTariff.SCOPE_SONG,
    val name: String = "",
    val priceRub: Double = 0.0,
    val periodDays: Int = 0,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
) : Serializable,
    Comparable<PriceTariffDto>,
    KaraokeDbTableDto {
    override fun compareTo(other: PriceTariffDto): Int = compareValuesBy(this, other, { it.scope }, { it.sortOrder }, { it.id })

    override fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (scope != PriceTariff.SCOPE_SONG && scope != PriceTariff.SCOPE_SITE) errors.add("Некорректный scope тарифа")
        if (name.isBlank()) errors.add("Не указано имя тарифа")
        if (priceRub < 0) errors.add("Цена не может быть отрицательной")
        if (scope == PriceTariff.SCOPE_SITE && periodDays <= 0) errors.add("Для подписки на сайт нужен положительный период (дней)")
        return errors
    }

    override fun isValid(): Boolean = validationErrors().isEmpty()

    override fun fromDto(database: KaraokeConnection): PriceTariff {
        val entity = PriceTariff(database = database)
        entity.id = id
        entity.scope = scope
        entity.name = name
        entity.priceRub = priceRub
        entity.periodDays = periodDays
        entity.isActive = isActive
        entity.isDefault = isDefault
        entity.sortOrder = sortOrder
        return entity
    }
}

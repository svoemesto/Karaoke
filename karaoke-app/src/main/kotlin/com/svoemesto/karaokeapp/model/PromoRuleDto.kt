package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

data class PromoRuleDto(
    val id: Long = 0,
    val name: String = "",
    val type: String = PromoRule.TYPE_FLAT_PERCENT,
    val paramsJson: String = "{}",
    val appliesTo: String = PromoRule.APPLIES_BOTH,
    val isActive: Boolean = true,
    val validFrom: String? = null,
    val validTo: String? = null,
    val priority: Int = 0,
) : Serializable,
    Comparable<PromoRuleDto>,
    KaraokeDbTableDto {
    override fun compareTo(other: PromoRuleDto): Int = compareValuesBy(this, other, { -it.priority }, { it.id })

    override fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        val knownTypes =
            setOf(PromoRule.TYPE_NEW_USER_PERCENT, PromoRule.TYPE_NTH_FREE, PromoRule.TYPE_HAPPY_HOUR, PromoRule.TYPE_FLAT_PERCENT)
        if (type !in knownTypes) errors.add("Неизвестный тип акции: $type")
        val knownScopes = setOf(PromoRule.APPLIES_SONG, PromoRule.APPLIES_SITE, PromoRule.APPLIES_BOTH)
        if (appliesTo !in knownScopes) errors.add("Некорректный appliesTo акции")
        if (name.isBlank()) errors.add("Не указано имя акции")
        return errors
    }

    override fun isValid(): Boolean = validationErrors().isEmpty()

    override fun fromDto(database: KaraokeConnection): PromoRule {
        val entity = PromoRule(database = database)
        entity.id = id
        entity.name = name
        entity.type = type
        entity.paramsJson = paramsJson
        entity.appliesTo = appliesTo
        entity.isActive = isActive
        entity.priority = priority
        return entity
    }
}

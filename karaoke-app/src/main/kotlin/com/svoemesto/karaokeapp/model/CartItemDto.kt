package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO позиции в корзине для API/UI (без ссылок на `Settings`/`PriceTariff`).
 *
 * Содержит только сериализуемые поля (id, idSiteUser, idSettings,
 * idPriceTariff, idPromoRule, created).
 *
 * @see docs/features/telegram-auto-publish.md
 */
data class CartItemDto(
    val id: Long = 0,
    val siteUserId: Long = 0,
    val idSong: Long = 0,
    val addedAt: String = "",
) : Serializable,
    Comparable<CartItemDto>,
    KaraokeDbTableDto {
    override fun compareTo(other: CartItemDto): Int = addedAt.compareTo(other.addedAt)

    override fun validationErrors(): List<String> = emptyList()

    override fun isValid(): Boolean = true

    // Не используется для создания/обновления (см. CartItem.createNew) — реализован для соответствия
    // интерфейсу KaraokeDbTableDto.
    override fun fromDto(database: KaraokeConnection): CartItem {
        val entity = CartItem(database = database)
        entity.id = id
        entity.siteUserId = siteUserId
        entity.idSong = idSong
        return entity
    }
}

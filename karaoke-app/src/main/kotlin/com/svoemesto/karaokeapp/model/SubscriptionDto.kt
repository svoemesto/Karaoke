package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

data class SubscriptionDto(
    val id: Long = 0,
    val siteUserId: Long = 0,
    val scope: String = Subscription.SCOPE_SONG,
    val idSong: Long? = null,
    val tariffId: Long? = null,
    val periodDays: Int = 0,
    val basePrice: Double = 0.0,
    val discount: Double = 0.0,
    val finalPrice: Double = 0.0,
    val promoApplied: String = "",
    val status: String = Subscription.STATUS_CREATED,
    val autoRenew: Boolean = true,
    val createdAt: String = "",
    val paidAt: String? = null,
    val orderId: String? = null,
) : Serializable,
    Comparable<SubscriptionDto>,
    KaraokeDbTableDto {
    override fun compareTo(other: SubscriptionDto): Int = createdAt.compareTo(other.createdAt)

    override fun validationErrors(): List<String> = emptyList()

    override fun isValid(): Boolean = true

    // fromDto здесь не используется для создания/обновления (см. Subscription.createNew и
    // PublicPaymentController — там прямое изменение полей + save()); реализован для соответствия
    // интерфейсу KaraokeDbTableDto.
    override fun fromDto(database: KaraokeConnection): Subscription {
        val entity = Subscription(database = database)
        entity.id = id
        entity.siteUserId = siteUserId
        entity.scope = scope
        entity.idSong = idSong
        entity.tariffId = tariffId
        entity.periodDays = periodDays
        entity.status = status
        entity.autoRenew = autoRenew
        return entity
    }
}

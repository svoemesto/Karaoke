package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

data class SiteUserDto(
    val id: Long = 0,
    val email: String = "",
    val displayName: String = "",
    val sponsrUid: String = "",
    val isPremium: Boolean = false,
    val isPermanentPremium: Boolean = false,
    val isEffectivePremium: Boolean = false,
    // В норме проставляются Sponsr-синхронизацией / оплатой подписки на сайт, но админ может
    // подправить/очистить вручную через update() (например выдать/отозвать временный премиум).
    val sponsrPremiumUntil: String? = null,
    val sitePremiumUntil: String? = null,
    // Постоянная скидка (%), выставляется вручную админом — суммируется поверх любой акции при
    // расчёте цены (PriceService). Виден пользователю в личном кабинете, если > 0.
    val personalDiscountPercent: Double = 0.0,
    val isEditor: Boolean = false,
    val isBanned: Boolean = false,
    val banReason: String = "",
    // Персональные лимиты (0 = дефолт). Без is-префикса — JSON-ключи maxFavorites/... как есть.
    val maxFavorites: Int = 0,
    val maxPlaylists: Int = 0,
    val maxPlaylistItems: Int = 0,
    val createdAt: String = "",
    val lastLoginAt: String = "",
    // Флаг однократной отправки приветственного сообщения при первом премиуме (см. SiteUser.kt) —
    // редактируется вручную, чтобы админ мог принудительно вызвать повторную отправку (сбросить в false).
    val welcomeMessageSent: Boolean = false,
) : Serializable, Comparable<SiteUserDto>, KaraokeDbTableDto {

    override fun compareTo(other: SiteUserDto): Int = email.compareTo(other.email)

    override fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (!email.contains("@") || email.length < 5) errors.add("Некорректный email")
        return errors
    }

    override fun isValid(): Boolean = validationErrors().isEmpty()

    // Используется только админкой (webvue3) для обновления профильных полей — passwordHash сюда никогда не попадает.
    override fun fromDto(database: KaraokeConnection): SiteUser {
        val entity = SiteUser(database = database)
        entity.id = id
        entity.email = email
        entity.displayName = displayName
        entity.sponsrUid = sponsrUid
        entity.isEditor = isEditor
        entity.isBanned = isBanned
        entity.banReason = banReason
        entity.maxFavorites = maxFavorites
        entity.maxPlaylists = maxPlaylists
        entity.maxPlaylistItems = maxPlaylistItems
        return entity
    }
}

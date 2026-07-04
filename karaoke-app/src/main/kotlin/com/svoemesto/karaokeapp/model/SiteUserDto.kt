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
    val isBanned: Boolean = false,
    val banReason: String = "",
    val createdAt: String = "",
    val lastLoginAt: String = "",
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
        entity.isBanned = isBanned
        entity.banReason = banReason
        return entity
    }
}

package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO для site user: сериализуемое представление для API/UI.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
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
    // Self-assign tasks: редактор с флагом может брать себе свободные песни в karaoke-public/ZakromaView.
    // Явный @JsonProperty, потому что Kotlin data class Jackson отбрасывает is-префикс (AGENTS.md Q&A).
    // Хотя имя поля и так без is — всё равно ставим аннотацию для единообразия с соседними
    // boolean-полями и устойчивости при будущих рефакторингах имени.
    @get:JsonProperty("canSelfAssignTasks")
    val canSelfAssignTasks: Boolean = false,
    // specs/293-skip-author-toggle: разрешение на работу с SKIP-авторами/песнями.
    // Залогиненный пользователь с флагом видит SKIP-контент в публичных списках (Закрома,
    // история прослушиваний), бейдж «SKIP» в UI. Share-link для SKIP-песен ВСЕ РАВНО
    // запрещён (FR-012). Выставляется ТОЛЬКО админом в webvue3 (не в karaoke-public/AccountView,
    // см. /speckit.specify 2026-09-02).
    @get:JsonProperty("canWorkWithSkipped")
    val canWorkWithSkipped: Boolean = false,
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
) : Serializable,
    Comparable<SiteUserDto>,
    KaraokeDbTableDto {
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
        entity.canSelfAssignTasks = canSelfAssignTasks
        entity.canWorkWithSkipped = canWorkWithSkipped
        entity.isBanned = isBanned
        entity.banReason = banReason
        entity.maxFavorites = maxFavorites
        entity.maxPlaylists = maxPlaylists
        entity.maxPlaylistItems = maxPlaylistItems
        return entity
    }
}

package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

/**
 * DTO для author: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class AuthorDTO(
    val id: Long,
    val author: String,
    val ymId: String,
    val vkId: String,
    val lastAlbumYm: String,
    val lastAlbumVk: String,
    val lastAlbumProcessed: String,
    val watched: Boolean,
    val skip: Boolean,
    val aliases: String = "",
    /**
     * Флаг автора "по спецзаказу" — отдельно от основного каталога.
     *
     * @JsonProperty нужен, потому что Jackson по умолчанию отбрасывает префикс `is` для boolean-полей
     * (data class `isSpecialOrder: Boolean` → JSON-поле `specialOrder`, а не `isSpecialOrder`).
     *
     * @see specs/008-special-orders/spec.md
     * @see docs/strategy/growth.md (H1.20, M-23)
     */
    @get:JsonProperty("isSpecialOrder")
    val isSpecialOrder: Boolean = false,
    val haveNewAlbum: Boolean,
    val pictureId: Long = 0,
    val picturePreview: String = "",
    val picturePreviewUrl: String = "",
) : Serializable,
    Comparable<AuthorDTO>,
    KaraokeDbTableDto {
    override fun compareTo(other: AuthorDTO): Int = author.compareTo(other.author)

    override fun fromDto(database: KaraokeConnection): Author {
        val entity = Author(database = database)
        entity.id = id
        entity.author = author
        entity.ymId = ymId
        entity.vkId = vkId
        entity.lastAlbumYm = lastAlbumYm
        entity.lastAlbumVk = lastAlbumVk
        entity.lastAlbumProcessed = lastAlbumProcessed
        entity.watched = watched
        entity.skip = skip
        entity.aliases = aliases
        entity.isSpecialOrder = isSpecialOrder
        return entity
    }
}

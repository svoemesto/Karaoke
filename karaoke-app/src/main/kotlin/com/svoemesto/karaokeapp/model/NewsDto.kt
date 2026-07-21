package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

// Булево поле БЕЗ префикса `is` — Jackson сериализует геттер isPublished() в JSON-ключ "published"
// (см. DEVELOPMENT.md про Jackson bean convention, тот же приём, что fromAuthor/read в SiteChatMessageDto).

/**
 * DTO для new: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class NewsDto(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val category: String = "general",
    val link: String = "",
    val publishAt: String = "",
    val createdAt: String = "",
    val published: Boolean = false,
) : Serializable,
    KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): News {
        val entity = News(database = database)
        entity.id = id
        entity.title = title
        entity.body = body
        entity.category = category
        entity.link = link.ifBlank { null }
        return entity
    }
}

package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

// Булевы поля БЕЗ префикса `is` — Jackson сериализует геттер isX() в JSON-ключ "x" (без "is"), фронт
// читает fromAuthor/read буквально (см. CLAUDE.md про Jackson bean convention).
data class SiteChatMessageDto(
    val id: Long = 0,
    val siteUserId: Long = 0,
    val fromAuthor: Boolean = false,
    val body: String = "",
    val read: Boolean = false,
    val createdAt: String = "",
) : Serializable, KaraokeDbTableDto {

    override fun fromDto(database: KaraokeConnection): SiteChatMessage {
        val entity = SiteChatMessage(database = database)
        entity.id = id
        entity.siteUserId = siteUserId
        entity.isFromAuthor = fromAuthor
        entity.body = body
        entity.isRead = read
        return entity
    }
}

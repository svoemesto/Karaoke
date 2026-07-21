package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable

// DTO черновика. Отдельно наружу почти не ходит (данные черновика попадают в UI через
// SongAssignmentDto.draftSourceText/draftMarkers) — существует ради контракта KaraokeDbTable.toDTO()
// и sync (fromDto). Булевых is-полей нет (Jackson bean convention).

/**
 * DTO для song assignment draft: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class SongAssignmentDraftDto(
    val id: Long = 0,
    val assignmentId: Long = 0,
    val assigneeId: Long = 0,
    val editedSourceText: String = "",
    val editedMarkers: String = "[]",
    val userStatus: String = SongAssignmentStatus.USER_IN_PROGRESS,
) : Serializable,
    KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): SongAssignmentDraft {
        val entity = SongAssignmentDraft(database = database)
        entity.id = id
        entity.assignmentId = assignmentId
        entity.assigneeId = assigneeId
        entity.editedSourceText = editedSourceText
        entity.editedMarkers = editedMarkers
        entity.userStatus = userStatus
        return entity
    }
}

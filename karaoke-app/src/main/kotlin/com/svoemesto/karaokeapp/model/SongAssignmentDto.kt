package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable
import java.sql.Timestamp

// DTO задания онлайн-редактора. Поля status/assigneeEmail/assigneeName/songName/author/album/year —
// НЕ БД-поля, заполняются контроллером для UI (композитный статус + метаданные песни и пользователя).
// draftSourceText/draftMarkers — тоже заполняются контроллером из связанного черновика (для просмотра
// админом в webvue3 и для загрузки в редактор). Булевых is-полей нет (Jackson bean convention).
data class SongAssignmentDto(
    val id: Long = 0,
    val assigneeId: Long = 0,
    val songId: Long = 0,
    val voice: Long = 0,
    val adminStatus: String = SongAssignmentStatus.ADMIN_OPEN,
    val reviewComment: String = "",
    val assignedBy: Long = 0,
    // Вычисляемые/связанные поля для UI:
    val status: String = SongAssignmentStatus.ASSIGNED.dbValue,
    val assigneeEmail: String = "",
    val assigneeName: String = "",
    val songName: String = "",
    val author: String = "",
    val album: String = "",
    val year: Long = 0,
    val assignedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val submittedAt: Timestamp? = null,
    val draftSourceText: String = "",
    val draftMarkers: String = "[]",
) : Serializable, KaraokeDbTableDto {

    override fun fromDto(database: KaraokeConnection): SongAssignment {
        val entity = SongAssignment(database = database)
        entity.id = id
        entity.assigneeId = assigneeId
        entity.songId = songId
        entity.voice = voice
        entity.adminStatus = adminStatus
        entity.reviewComment = reviewComment
        entity.assignedBy = assignedBy
        return entity
    }
}

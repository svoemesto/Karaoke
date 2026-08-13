package com.svoemesto.karaokeapp.model

import java.io.Serializable
import java.sql.Timestamp

// Краткое представление SongAssignment для встраивания в ответ /api/public/zakroma (стрим «Закром»):
// фронт хочет знать, свободна ли песня (null), или у неё есть назначение (id + assigneeId + статус).
// НЕ наследует KaraokeDbTableDto — не сериализуется из БД, собирается контроллером (см.
// PublicApiController.zakromaStream и SongAssignmentBriefMapper — батч по списку song_id).
//
// JsonProperty явный для консистентности и устойчивости к Jackson-дефолту по is-префиксу (AGENTS.md).

/**
 * Brief DTO задания для встраивания в публичные ответы (Закрома, статус-чек и т.п.).
 *
 * @see docs/features/editor-tasks.md#self-assign
 */
data class SongAssignmentBriefDto(
    @get:com.fasterxml.jackson.annotation.JsonProperty("id")
    val id: Long = 0,
    @get:com.fasterxml.jackson.annotation.JsonProperty("assigneeId")
    val assigneeId: Long = 0,
    @get:com.fasterxml.jackson.annotation.JsonProperty("assignedAt")
    val assignedAt: Timestamp? = null,
    @get:com.fasterxml.jackson.annotation.JsonProperty("adminStatus")
    val adminStatus: String = SongAssignmentStatus.ADMIN_OPEN,
) : Serializable

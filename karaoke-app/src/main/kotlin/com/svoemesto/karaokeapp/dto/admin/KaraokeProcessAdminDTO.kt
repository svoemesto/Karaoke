package com.svoemesto.karaokeapp.dto.admin

import java.sql.Timestamp

/**
 * DTO для админ-панели процессов (specs/315-admin-ui-karaoke-process-v5).
 *
 * 20 основных полей из 28 реальных колонок `tbl_processes`. camelCase-алиасы
 * `updatedAt`/`startedAt`/`endedAt` маппятся на реальные колонки
 * `last_update`/`process_start`/`process_end` через SQL-алиасы `AS updated_at` и т.д.
 * (в схеме НЕТ колонок `created_at`/`updated_at`/`started_at`/`ended_at` — урок #11 iter #3).
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
data class KaraokeProcessAdminDTO(
    val id: Int,
    val name: String,
    val status: String,
    val type: String,
    val command: String,
    val args: String,
    val envs: String,
    val description: String,
    val songId: Int?,
    val order: Int,
    val priority: Int,
    val prioritet: Int,
    val withoutControl: Boolean,
    val threadId: Int,
    val processChainId: Long?,
    val processDeletedAt: Timestamp?,
    val updatedAt: Timestamp?,
    val startedAt: Timestamp?,
    val endedAt: Timestamp?,
    val recordhash: String?,
)

/**
 * Запись audit-лога процесса (tbl_processes_audit, миграция 47).
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
data class ProcessAuditDTO(
    val id: Int,
    val processId: Int,
    val actor: String,
    val action: String,
    val oldValue: Map<String, Any?>?,
    val newValue: Map<String, Any?>?,
    val createdAt: Timestamp,
)

/**
 * Результат списка процессов (total + items) для пагинации.
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
data class ProcessListResult(
    val total: Int,
    val items: List<KaraokeProcessAdminDTO>,
)

package com.svoemesto.karaokeapp.dto.admin

import java.util.UUID

/**
 * Отчёт о выполнении bulk-операции над процессами (specs/319-process-bulk-actions-v2).
 *
 * @property batchId UUID, общий для всех audit-записей этой операции
 * @property action тип операции (`bulk_update_field` | `bulk_delete`)
 * @property requested сколько id прислали в запросе
 * @property succeeded сколько обработано успешно
 * @property failed сколько ошибок (равно `errors.size`)
 * @property errors список ошибок per-process
 * @property durationMs время выполнения на backend (без учёта сетевой задержки)
 *
 * @see specs/319-process-bulk-actions-v2/spec.md (FR-006)
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md
 */
data class BulkOperationReport(
    val batchId: UUID,
    val action: String,
    val requested: Int,
    val succeeded: Int,
    val failed: Int,
    val errors: List<BulkError> = emptyList(),
    val durationMs: Long = 0,
)

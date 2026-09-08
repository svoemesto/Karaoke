package com.svoemesto.karaokeapp.dto.admin

/**
 * Описание ошибки в рамках bulk-операции (specs/319-process-bulk-actions-v2).
 *
 * Один элемент списка `BulkOperationReport.errors`.
 *
 * @property processId id процесса, на котором произошла ошибка
 * @property reason человекочитаемое сообщение (показывается в UI)
 * @property errorCode машиночитаемый код (для логирования / диагностики),
 *   опционально — null для непредвиденных ошибок
 *
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md
 */
data class BulkError(
    val processId: Long,
    val reason: String,
    val errorCode: String? = null,
)

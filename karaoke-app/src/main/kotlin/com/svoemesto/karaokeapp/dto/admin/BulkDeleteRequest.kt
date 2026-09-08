package com.svoemesto.karaokeapp.dto.admin

import java.util.UUID

/**
 * Запрос на bulk-delete набора процессов (specs/319-process-bulk-actions-v2).
 *
 * Plain class с сеттерами для совместимости с дефолтным Jackson (см. `BulkUpdateRequest.kt`).
 *
 * @property ids список process.id для удаления
 * @property batchId опционально — UUID batch'а
 *
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-2)
 */
class BulkDeleteRequest {
    var ids: List<Int>? = null
    var batchId: UUID? = null
}

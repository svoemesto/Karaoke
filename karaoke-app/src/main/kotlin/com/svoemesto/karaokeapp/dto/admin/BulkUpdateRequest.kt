package com.svoemesto.karaokeapp.dto.admin

import java.util.UUID

/**
 * Запрос на bulk-update одного поля у набора процессов (specs/319-process-bulk-actions-v2).
 *
 * Plain class с сеттерами (не data class) для совместимости с дефолтным Jackson
 * без `jackson-module-kotlin`. Стиль совпадает с существующим `edit` endpoint
 * (`Map<String, Any?>`), который тоже работает без Kotlin module.
 *
 * @property ids список process.id для изменения
 * @property field имя поля из `editableColumns` whitelist (см. `KaraokeProcessAdminService`)
 * @property value новое значение (тип зависит от `field` — coerce в сервисе)
 * @property batchId опционально — UUID batch'а. Null → генерируется на backend.
 *
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-1)
 */
class BulkUpdateRequest {
    var ids: List<Int>? = null
    var field: String? = null
    var value: Any? = null
    var batchId: UUID? = null
}

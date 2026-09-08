# Contract: Admin Process Bulk REST API

**Created**: 2026-09-08 | **Spec**: `specs/319-process-bulk-actions-v2/spec.md`
**Format**: REST over JSON (existing pattern, см. `KaraokeProcessAdminController`)

---

## Base URL

`/api/admin/processes` (consistent with `KaraokeProcessAdminController:30`).

## Auth

`permitAll()` (Constitution V). Optional `X-Admin-Username` header для actor в audit.

---

## Endpoints

### EP-1: POST `/api/admin/processes/bulk-update`

Bulk-edit одного поля у набора процессов. Sync endpoint, target ≤ 1000 процессов.

**Request body**:
```json
{
  "ids": [101, 102, 103, 950],
  "field": "priority",            // Whitelist: editableColumns keys
  "value": -1,                    // Тип соответствует field (см. V-1)
  "batchId": "uuid-optional"       // Если не задан, генерируется на backend
}
```

**Response 200 OK**:
```json
{
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "action": "bulk_update_field",
  "requested": 4,
  "succeeded": 3,
  "failed": 1,
  "errors": [
    { "processId": 950, "reason": "Invalid transition: WORKING → DONE", "errorCode": "INVALID_STATUS_TRANSITION" }
  ],
  "durationMs": 245
}
```

**Errors**:
- `400 Bad Request` — `{ error: "INVALID_FIELD", message: "Field 'foo' is not editable" }`
- `400 Bad Request` — `{ error: "INVALID_VALUE", message: "priority must be ≥ 0" }`
- `400 Bad Request` — `{ error: "EMPTY_IDS", message: "ids must be non-empty" }`
- `400 Bad Request` — `{ error: "TOO_MANY_IDS", message: "Use /bulk-update-async for > 1000 ids" }` (если > 1000)

### EP-2: POST `/api/admin/processes/bulk-delete`

Hard-delete набора процессов. Sync endpoint, target ≤ 1000 процессов.

**Request body**:
```json
{
  "ids": [101, 102, 103],
  "batchId": "uuid-optional"
}
```

**Response 200 OK**:
```json
{
  "batchId": "550e8400-e29b-41d4-a716-446655440001",
  "action": "bulk_delete",
  "requested": 3,
  "succeeded": 3,
  "failed": 0,
  "errors": [],
  "durationMs": 187
}
```

**Side effects**:
- WORKING/WAITING/CREATING — interrupt + 5 sec grace (WORKING) + destroyForcibly.
- `DELETE FROM tbl_processes WHERE id = ?` — каскадно удаляет audit (FK CASCADE).
- `tbl_processes_audit` — НЕ пишется для bulk_delete (cascade стирает всё).
  Если в будущем потребуется сохранить audit — см. data-model.md E-2.

**Errors**:
- `400 Bad Request` — `{ error: "EMPTY_IDS", message: "ids must be non-empty" }`
- `400 Bad Request` — `{ error: "TOO_MANY_IDS", message: "Use /bulk-delete-async for > 1000 ids" }`

### EP-3: POST `/api/admin/processes/bulk-update-async`

Async вариант для > 1000 процессов. Стартует `ProcessBulkTask`, возвращает taskId
для polling через `/api/admin/tasks/{taskId}`.

**Request body**: тот же, что EP-1.

**Response 202 Accepted**:
```json
{
  "taskId": "770e8400-e29b-41d4-a716-446655440002",
  "statusUrl": "/api/admin/tasks/770e8400-e29b-41d4-a716-446655440002",
  "estimatedDurationSec": 120
}
```

**Errors**: те же, что EP-1 + `503 Service Unavailable` если admin-task subsystem недоступен.

### EP-4: POST `/api/admin/processes/bulk-delete-async`

Async вариант bulk-delete. То же поведение, что EP-2, но через admin-task.

### EP-5: GET `/api/admin/processes/bulk/snapshot?{filters}`

> **Note (2026-09-08)**: endpoint изначально планировался как `/ids-by-filter`,
> но Spring Boot 3.x маршрутизировал его на `GET /{id}` (id="ids-by-filter" → Int
> conversion fail). Перенесён под `/bulk/snapshot` — дополнительный сегмент
> гарантирует, что `{id}`-matcher не сработает (WP #68 comment 320).

Получить snapshot id, удовлетворяющих фильтру (для UI bulk-кнопок).
**Новый endpoint** (см. R-9).

**Query params**: те же, что `GET /api/admin/processes` (status, type, threadId, chainId, includeDeleted, name, topLevelOnly, parentId).

**Response 200 OK**:
```json
{
  "total": 5432,
  "ids": [101, 102, 103, ...],   // Все id, удовлетворяющие фильтру
  "limitApplied": 10000          // Cap для защиты (если фильтр даёт > 10000 — обрезаем)
}
```

Если `total > limitApplied` — UI должен предупредить админа и либо передать фильтр
в bulk-endpoint (server-side execution), либо разбить на чанки.

### EP-6: GET `/api/admin/tasks/{taskId}` (existing, may need creation)

Polling прогресса async-задачи. Возвращает:
```json
{
  "taskId": "770e8400-...",
  "status": "RUNNING",           // INITIATED | VALIDATING | RUNNING | COMPLETED | PARTIAL | FAILED
  "totalCount": 5432,
  "processedCount": 2100,
  "succeededCount": 2098,
  "failedCount": 2,
  "errors": [ { "processId": ..., "reason": ... } ]
}
```

Если endpoint ещё не существует — создать в `AdminTaskController`.

---

## Audit-trail actions

Расширение CHECK constraint в `tbl_processes_audit.action` (см. data-model.md):
- `'EDIT'` (existing)
- `'RETRY'` (existing)
- `'DELETE'` (existing)
- `'bulk_update_field'` (NEW)
- `'bulk_delete'` (NEW — для admin-task endpoint'ов; для sync EP-2 записи
  в audit не пишутся, так как FK CASCADE удалит их)

**Согласованность**: для async варианта bulk_delete можно писать audit ДО удаления
(`INSERT INTO audit ... action='bulk_delete' process_id=... batch_id=...`)
→ он будет удалён CASCADE вместе с процессом. Это даёт zero-trace поведение
(consistent с Q1) но усложняет логирование. **v1: НЕ пишем audit для bulk_delete
(полная CASCADE), см. EP-2 side effects**.

---

## Status Codes Summary

| Code | When | Body |
|------|------|------|
| 200 | Bulk complete (sync) | `BulkOperationReport` |
| 202 | Bulk accepted (async) | `{ taskId, statusUrl, estimatedDurationSec }` |
| 400 | Validation error | `{ error, message, details? }` |
| 404 | (no new 404 — bulk operates on sets) | — |
| 500 | DB error / unexpected | `{ error: "INTERNAL_ERROR", message }` |
| 503 | Admin-task subsystem unavailable (async only) | `{ error: "TASK_SYSTEM_UNAVAILABLE" }` |

---

## Backwards compatibility

- Existing endpoints (`/list`, `/{id}/edit`, `/{id}/delete`, `/{id}/retry`, `/{id}/audit`) — **не меняются**.
- Новые endpoints — additive, ничего не ломают.
- Миграция CHECK constraint — backward-compatible (расширяет whitelist).
- Vuex-store additions — additive (новые поля в state, новые actions).

---

## Versioning

Это часть `KaraokeProcessAdminController` v1.6+ (предыдущая — v1.5 в спеку #315).
Никаких version-префиксов в URL (`/v2/...`) — добавление новых endpoint'ов не требует
версионирования.

---

## Reference

- Existing endpoints: `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`
- Error format: `contracts/admin-process-rest-api.md` (existing, спека #315)
- Data model: `specs/319-process-bulk-actions-v2/data-model.md`
- Spec: `specs/319-process-bulk-actions-v2/spec.md`

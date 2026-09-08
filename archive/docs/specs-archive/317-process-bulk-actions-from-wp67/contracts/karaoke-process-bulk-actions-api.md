# Contracts: Karaoke Process Bulk Actions API (iter #317)

**Branch**: `317-process-bulk-actions` | **Date**: 2026-09-08 | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md)

**Base URL**: `http://localhost:8899` (admin machine) — `karaoke-app` container.

**Auth**: `permitAll()` (прецедент spec #315, `KaraokeProcessAdminController.kt:22/:30/:37`).

**Actor**: из request header `X-Admin-Username` (FR-007 spec #315 audit-trail).

**Endpoints**: 2 новых, в **существующем** `KaraokeProcessAdminController.kt`:

---

## POST /api/admin/processes/bulk-update

Изменение одного поля у группы процессов.

### Request

**Headers**:
- `Content-Type: application/json`
- `X-Admin-Username: <username>` (для audit actor)

**Body** (`ProcessBulkUpdateRequest`):
```json
{
  "ids": [12345, 12346, 12347],
  "field": "priority",
  "value": -1
}
```

**Поля**:
- `ids` — `array<long>`, non-empty, ≤ 10000.
- `field` — `string`, whitelist: `"priority"` | `"status"` | `"threadId"`.
- `value` — типизированный: `int` (priority), `string` (status enum name), `long` (threadId).

### Response

**200 OK** (`ProcessBulkUpdateResponse`):
```json
{
  "updated": 3,
  "skipped": 0
}
```

С skipped процессами (для status transition error или already-deleted):
```json
{
  "updated": 7,
  "skipped": 3,
  "reasons": {
    "12346": "недопустимый переход CREATING→ERROR",
    "12350": "process_deleted_at IS NOT NULL",
    "12360": "недопустимый переход DONE→WAITING"
  }
}
```

**400 Bad Request** (validation error):
```json
{
  "error": "INVALID_FIELD",
  "message": "field must be one of: priority, status, threadId",
  "allowedFields": ["priority", "status", "threadId"]
}
```

**400 Bad Request** (ids validation):
```json
{
  "error": "INVALID_IDS",
  "message": "ids must be non-empty array, max 10000 elements"
}
```

**400 Bad Request** (value validation):
```json
{
  "error": "INVALID_VALUE",
  "message": "value for field='priority' must be integer, got: abc"
}
```

### Backend logic (AdminService.processesBulkUpdate)

```
1. Whitelist field (priority|status|threadId) → 400 INVALID_FIELD if not.
2. ids validation (non-empty, ≤ 10000) → 400 INVALID_IDS if not.
3. value type coercion per field:
   - priority → Int (any positive or negative value, no MIN/MAX in v1, Р-1)
   - status → enum value (CREATING/WAITING/WORKING/DONE/ERROR, Р-2)
   - threadId → Long ≥ 0
4. For each id (batch via WHERE id IN (..)):
   a. SELECT process_deleted_at FROM tbl_processes WHERE id = ?
   b. If process_deleted_at IS NOT NULL → add to skipped with reason "process_deleted_at IS NOT NULL"
   c. If field="status" → validate transition:
      - If existing transition validation in AdminService:66 fails → add to skipped with reason "недопустимый переход X→Y"
5. UPDATE tbl_processes SET field = :value WHERE id IN (skipped-ids excluded) AND process_deleted_at IS NULL
6. For each updated id → INSERT INTO tbl_processes_audit (process_id, actor, action, old_value, new_value) VALUES (?, 'admin:username', 'bulk-update', '{"field": "old_value"}'::jsonb, '{"field": "new_value"}'::jsonb) -- created_at: DEFAULT NOW() (DB-side, адаптация к AdminService.kt:643 который шлёт 5 колонок)
7. Return {updated: N, skipped: M, reasons: {...}}
```

### Examples (curl)

```bash
# Update priority on 3 processes
curl -X POST http://localhost:8899/api/admin/processes/bulk-update \
  -H "Content-Type: application/json" \
  -H "X-Admin-Username: admin" \
  -d '{"ids": [12345, 12346, 12347], "field": "priority", "value": -1}'
# → {"updated": 3, "skipped": 0}

# Update status with transition error
curl -X POST http://localhost:8899/api/admin/processes/bulk-update \
  -H "Content-Type: application/json" \
  -H "X-Admin-Username: admin" \
  -d '{"ids": [12346], "field": "status", "value": "ERROR"}'
# → {"updated": 0, "skipped": 1, "reasons": {"12346": "недопустимый переход CREATING→ERROR"}}
```

---

## POST /api/admin/processes/bulk-delete

Soft-delete группы процессов (НЕ hard-delete, out of scope v1).

### Request

**Headers**:
- `Content-Type: application/json`
- `X-Admin-Username: <username>` (для audit actor)

**Body** (`ProcessBulkDeleteRequest`):
```json
{
  "ids": [12345, 12346, 12347]
}
```

**Поля**:
- `ids` — `array<long>`, non-empty, ≤ 10000.

### Response

**200 OK** (`ProcessBulkDeleteResponse`):
```json
{
  "deleted": 3,
  "skipped": 0
}
```

С skipped процессами (для already-deleted или has-children):
```json
{
  "deleted": 7,
  "skipped": 3,
  "reasons": {
    "12346": "process_deleted_at IS NOT NULL",
    "12350": "has_children (child_count: 2)",
    "12360": "has_children (child_count: 1)"
  }
}
```

**400 Bad Request** (ids validation):
```json
{
  "error": "INVALID_IDS",
  "message": "ids must be non-empty array, max 10000 elements"
}
```

### Backend logic (AdminService.processesBulkDelete)

```
1. ids validation (non-empty, ≤ 10000) → 400 INVALID_IDS if not.
2. For each id (batch via WHERE id IN (..)):
   a. SELECT process_deleted_at FROM tbl_processes WHERE id = ?
   b. If process_deleted_at IS NOT NULL → add to skipped with reason "process_deleted_at IS NOT NULL"
   c. children-check (FR-007):
      - If process_chain_id IS NULL (head process):
        SELECT COUNT(*) FROM tbl_processes WHERE process_chain_id = :id AND process_deleted_at IS NULL
      - If COUNT > 0 → add to skipped with reason "has_children (child_count: N)"
3. UPDATE tbl_processes SET process_deleted_at = NOW()
   WHERE id IN (skipped-ids excluded)
4. For each deleted id → INSERT INTO tbl_processes_audit
   VALUES (?, 'admin:username', 'bulk-delete', ?::jsonb, NULL)
   (old_value = JSON со старыми значениями полей — прецедент AdminService:374 single-delete; new_value = NULL — soft-delete не имеет post-значений)
5. Return {deleted: N, skipped: M, reasons: {...}}
```

### Examples (curl)

```bash
# Delete 3 processes
curl -X POST http://localhost:8899/api/admin/processes/bulk-delete \
  -H "Content-Type: application/json" \
  -H "X-Admin-Username: admin" \
  -d '{"ids": [12345, 12346, 12347]}'
# → {"deleted": 3, "skipped": 0}

# Delete head process with children → skipped
curl -X POST http://localhost:8899/api/admin/processes/bulk-delete \
  -H "Content-Type: application/json" \
  -H "X-Admin-Username: admin" \
  -d '{"ids": [12350]}'
# → {"deleted": 0, "skipped": 1, "reasons": {"12350": "has_children (child_count: 2)"}}
```

---

## Error Format (canonical)

Все 4xx/5xx ответы — JSON:
```json
{
  "error": "<ERROR_CODE>",
  "message": "<human-readable>",
  "<optional fields>": "<optional values>"
}
```

**HTTP codes**:
- **200** — успех (включая partial success с `skipped > 0`).
- **400** — validation error (whitelist / ids / value).
- **500** — internal error (B-1 fix pattern: логировать с stack trace, отдавать generic message).

---

## Аутентификация (governance)

**`permitAll()`** (прецедент spec #315). Аутентификация НЕ используется — single-user инсталляция (Constitution V). Audit-trail через `X-Admin-Username` header — единственная идентификация.

Если в будущем понадобится auth — отдельная спека (out of scope v1).

---

## Backwards compatibility

**Новые endpoint'ы**, не ломают существующие. 6 endpoint'ов spec #315 (`/api/admin/processes/{id}`, `/edit`, `/retry`, `/delete`, `/audit`) — без изменений.

---

## Versioning

`v317-bulk-actions` — добавлены 2 endpoint'а в `KaraokeProcessAdminController.kt`.

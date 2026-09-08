# Data Model: Массовые действия с процессами (iter #317)

**Branch**: `317-process-bulk-actions` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Принцип**: никаких **новых таблиц** или колонок в БД. Переиспользуем существующие сущности spec #315.

## Существующие сущности (reuse)

### `KaraokeProcess` (`karaoke-app/.../KaraokeProcess.kt`)

Уже содержит все нужные поля для bulk-update:

| Поле | Тип | Применимость |
|---|---|---|
| `priority: Int` | Int | ✅ bulk-update target (FR-002, FR-005) |
| `status: KaraokeProcessStatuses` | enum | ✅ bulk-update target (FR-002, FR-005), применять transition-правила |
| `threadId: Long?` | Long? | ✅ bulk-update target (FR-002, FR-005) |
| `processChainId: Long?` | Long? | children-check (FR-007) |
| `processDeletedAt: Timestamp?` | Timestamp? | soft-delete (FR-004, FR-006) |

`loadList()` (KaraokeProcess.kt:867) **уже** читает `process_chain_id` (line :925) + `process_deleted_at` из ResultSet (iter #5 spec #315 v3 fix).

### `KaraokeProcessStatuses` enum (5 значений)

```kotlin
enum class KaraokeProcessStatuses {
    CREATING, WAITING, WORKING, DONE, ERROR
}
```

Прецедент: `KaraokeProcessStatuses.kt:8-15`. **Нет CANCELED** (Р-2: прямые enum-значения в UI).

### `tbl_processes_audit` (existing schema, миграция 47 spec #315)

**Фактические колонки** (verified by Кирилл в Stage 2 — Р-4):

| Колонка | Тип | Описание |
|---|---|---|
| `process_id` | BIGINT | FK на `tbl_processes.id` |
| `actor` | VARCHAR(64) | Из request header `X-Admin-Username` (spec 315 pattern) |
| `action` | VARCHAR | `'EDIT'` (spec 315) / `'bulk-update'` / `'bulk-delete'` (новые, Р-4) |
| `old_value` | JSONB | `{"status": "DONE", "priority": 0, "threadId": null}` |
| `new_value` | JSONB | `{"status": "ERROR", "priority": -1, "threadId": 42}` |
| `created_at` | TIMESTAMP | Server time |

**НЕ существуют** (Р-4): `operation`, `field`, `timestamp`, `user_agent`. Старые/новые значения упаковываются в jsonb.

**INSERT pattern** (Кирилл сверено по коду, `AdminService.kt:643`):
```kotlin
val sql = "INSERT INTO tbl_processes_audit (process_id, actor, action, old_value, new_value) VALUES (?, ?, ?, ?::jsonb, ?::jsonb)"
// created_at — DEFAULT NOW() в схеме (DB-side).
```

## DTO (Request/Response)

**Все DTO — в `karaoke-app/.../dto/admin/`** (existing dir, прецедент `KaraokeProcessAdminDTO.kt`).

### `ProcessBulkUpdateRequest`

```kotlin
data class ProcessBulkUpdateRequest(
    val ids: List<Long>,         // non-empty, ≤ 10000
    val field: String,           // whitelist: "priority" | "status" | "threadId"
    val value: Any               // типизированный: Int (priority), enum (status), Long (threadId)
)
```

### `ProcessBulkUpdateResponse`

```kotlin
data class ProcessBulkUpdateResponse(
    val updated: Int,             // успешно обновлено
    val skipped: Int,             // пропущено (transition error / already deleted)
    val reasons: Map<Long, String>? = null  // processId → reason (для skipped)
)
```

### `ProcessBulkDeleteRequest`

```kotlin
data class ProcessBulkDeleteRequest(
    val ids: List<Long>           // non-empty, ≤ 10000
)
```

### `ProcessBulkDeleteResponse`

```kotlin
data class ProcessBulkDeleteResponse(
    val deleted: Int,             // успешно soft-deleted
    val skipped: Int,             // пропущено (already deleted / has children)
    val reasons: Map<Long, String>? = null
)
```

## State Transitions (FR-017 spec #315 + Р-3)

Применяется в `bulkUpdate` для `field="status"`:

```
CREATING → WAITING   ✅ (allowed)
CREATING → WORKING   ❌ (skipped, "CREATING→WORKING не разрешён")
WAITING → WORKING    ✅
WORKING → DONE       ✅
WORKING → ERROR      ✅
DONE → ERROR         ❌ (skipped, "DONE→ERROR не разрешён")
DONE → WAITING       ❌ (skipped, "DONE→WAITING не разрешён")
ERROR → WAITING      ✅
ERROR → CREATING     ❌
ERROR → WORKING      ❌
```

**Правило**: переходы, не разрешённые существующей `validateTransition` логикой в `AdminService.kt:495-520`, → `skipped`.

**Фактическая матрица** (AdminService:495-520):
- CREATING → {WAITING, WORKING, ERROR} ✅
- WAITING → {WORKING} ✅
- WORKING → {DONE, ERROR} ✅
- DONE → ∅ (no transitions)
- ERROR → ∅ (no transitions — будет разрешено в v1 как retry-семантика для bulkUpdate, см. Р-1 вопрос владельцу)

## Validation Rules

| Поле | Правило | Где |
|---|---|---|
| `ids` | non-empty, ≤ 10000, all > 0 | FR-009 + AdminService bulkUpdate/bulkDelete |
| `field` | whitelist: `"priority"`, `"status"`, `"threadId"` | FR-008 + AdminService |
| `value` (priority) | Int, диапазон не ограничивается в v1 (Р-1) | AdminService.coerceValue |
| `value` (status) | enum CREATING/WAITING/WORKING/DONE/ERROR | AdminService.coerceValue |
| `value` (threadId) | Long ≥ 0 | AdminService.coerceValue |

## Bulk-delete children-check (FR-007)

Для каждого `id` где `process_chain_id IS NULL` (head процесса):

```sql
SELECT COUNT(*) FROM tbl_processes
WHERE process_chain_id = :id
  AND process_deleted_at IS NULL
```

Если > 0 → id попадает в `skipped` с reason `{"reason": "has_children", "child_count": N}`.

## Migration

**Нет миграций**. Используем существующую `tbl_processes` (с колонками из миграции 47 spec #315) и `tbl_processes_audit`.

## Indexes (verify существующие)

`tbl_processes.id` — PRIMARY KEY (BIGSERIAL).
`tbl_processes.process_chain_id` — индекс есть (iter #5 spec #315).
`tbl_processes.process_deleted_at` — индекс есть (iter #5 spec #315, soft-delete фильтрация).
`tbl_processes_audit.process_id` — индекс есть.
`tbl_processes_audit.action` + `created_at` — индекс для SC-004 (COUNT запросы).

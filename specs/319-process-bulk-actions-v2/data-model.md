# Data Model: Массовые действия с процессами (v2)

**Created**: 2026-09-08 | **Spec**: `specs/319-process-bulk-actions-v2/spec.md`

Phase 1 — описание сущностей, изменений схемы, валидаций.

---

## Entities

### E-1: `tbl_processes` (existing, no schema changes)

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | BIGSERIAL | NO | PK |
| process_name | VARCHAR | YES | Имя процесса |
| process_status | VARCHAR | YES | Enum: CREATING/WAITING/WORKING/DONE/ERROR |
| process_type | VARCHAR | YES | Тип процесса |
| process_command | VARCHAR | YES | OS-команда |
| process_args | VARCHAR | YES | Аргументы |
| process_envs | VARCHAR | YES | ENV-переменные |
| process_description | VARCHAR | YES | Описание |
| song_id | BIGINT | YES | FK на `tbl_songs` (nullable) |
| process_order | INT | YES | Порядок |
| process_priority | INT | YES | Приоритет (≥ 0, A-003) |
| process_prioritet | INT | YES | Legacy-поле |
| without_control | BOOLEAN | YES | Флаг ручного контроля |
| thread_id | INT | YES | Lane/threadId |
| process_chain_id | BIGINT | YES | Цепочка процессов |
| process_deleted_at | TIMESTAMP | YES | Soft-delete timestamp (NULL = живой) |
| ... + служебные | | | |

**Indexes (existing, relevant)**:
- `idx_tbl_processes_active` ON (id) WHERE process_deleted_at IS NULL
- `idx_tbl_processes_chain_id` ON (process_chain_id) WHERE process_chain_id IS NOT NULL

**Изменения в этой спеке**: **нет**. Используем существующие колонки.

### E-2: `tbl_processes_audit` (existing, schema change: CHECK constraint)

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | BIGSERIAL | NO | PK |
| process_id | BIGINT | NO | FK → `tbl_processes(id)` **ON DELETE CASCADE** |
| actor | VARCHAR(64) | NO | Имя админа |
| action | VARCHAR(32) | NO | **CHECK** action IN ('EDIT', 'RETRY', 'DELETE') ← расширяем |
| old_value | JSONB | YES | { field: oldValue, ... } |
| new_value | JSONB | YES | { field: newValue, ... } |
| created_at | TIMESTAMP | NO | DEFAULT NOW() |
| batch_id | UUID | (NEW) | **NEW**: NULL для single-record, UUID для bulk |

**Изменения в этой спеке** (миграция `48_admin_process_bulk_actions.sql`):
1. ALTER CHECK constraint: добавить `'bulk_update_field'` и `'bulk_delete'` в whitelist.
2. ALTER TABLE ADD COLUMN batch_id UUID NULL (опционально — для группировки в report).

**Зачем batch_id как колонка**: для `tbl_processes_audit` это даёт возможность
индексированного поиска по batch:
```sql
SELECT * FROM tbl_processes_audit WHERE batch_id = ? ORDER BY created_at;
```
Без колонки — нужно фильтровать через jsonb (`new_value->>'batch_id' = ?`),
что медленнее на больших аудитах.

### E-3: `BulkOperationReport` (DTO, no persistence)

Не новая таблица, а response-объект от bulk endpoint'а:

```kotlin
data class BulkOperationReport(
    val batchId: UUID,
    val action: String,           // "bulk_update_field" | "bulk_delete"
    val requested: Int,           // Сколько id прислали
    val succeeded: Int,           // Сколько обработано успешно
    val failed: Int,              // Сколько ошибок
    val errors: List<BulkError> = emptyList(),
    val durationMs: Long = 0,
)

data class BulkError(
    val processId: Long,
    val reason: String,           // Человекочитаемое сообщение
    val errorCode: String? = null, // Машиночитаемый код (для логирования)
)
```

### E-4: `ProcessBulkTask` (async variant, no persistence)

Если выбран async endpoint (≥ 1000 процессов), используется существующий
admin-task механизм. Task хранится в существующей admin-tasks таблице
(используется для search и других длительных операций).

```kotlin
data class ProcessBulkTask(
    val taskId: UUID,
    val action: String,
    val totalCount: Int,
    val processedCount: Int = 0,
    val succeededCount: Int = 0,
    val failedCount: Int = 0,
    val status: TaskStatus = TaskStatus.RUNNING,
    val startedAt: Instant = Instant.now(),
    val finishedAt: Instant? = null,
    val errors: List<BulkError> = emptyList(),
)
```

### E-5: Vuex state additions

```javascript
// webvue3/src/components/Processes/store.js — additions
state: {
  // ... existing
  bulkSelectionSnapshot: [],       // Array<Number> — snapshot of process.id при apply filter
  bulkSelectionTotal: 0,           // Total в snapshot (не зависит от пагинации)
  bulkSelectionTimestamp: null,    // ISO timestamp snapshot'а
  bulkOperationInProgress: false,  // Флаг «идёт операция»
  bulkOperationReport: null,       // BulkOperationReport | null
}

mutations: {
  SET_BULK_SELECTION(state, { ids, total }) { ... },
  SET_BULK_OPERATION_IN_PROGRESS(state, val) { ... },
  SET_BULK_OPERATION_REPORT(state, report) { ... },
}
```

---

## Validations

### V-1: Bulk-edit field validation

На backend:
```kotlin
// Reject anything not in editableColumns
val column = editableColumns[field]
    ?: throw ValidationException("Field $field is not editable")
```

На frontend (UI select):
- `field ∈ { "priority", "status", "threadId" }` ← v1 ограничение (FR-009 + Clarifications Q3)
- `value`:
  - `priority`: integer ≥ 0 (A-003)
  - `status`: enum ∈ { CREATING, WAITING, WORKING, DONE, ERROR }
  - `threadId`: integer (любой)

### V-2: Bulk-update transition validation

Переходы статуса — те же, что в single-record edit
(`KaraokeProcessAdminService.validateTransition`):
- CREATING → WAITING, WORKING, ERROR
- WAITING → WORKING
- WORKING → DONE, ERROR
- DONE/ERROR → none

При bulk-edit если хотя бы один процесс имеет невалидный переход:
- Вариант A: fail-fast — откатить все изменения в транзакции.
- Вариант B: best-effort — пропустить ошибочные, обработать остальные, отчёт.

**Decision**: Вариант B (best-effort), см. spec SC-005.

### V-3: Bulk-delete validation

- `ids` не пустой.
- `ids.size ≤ 10000` для sync endpoint (async для > 1000, см. D-5).
- Каждый id существует в `tbl_processes` на момент старта (race condition handled
  best-effort — `DELETE WHERE id = ?` возвращает 0 affected rows).

### V-4: Batch UUID uniqueness

`batch_id` генерируется через `UUID.randomUUID()` на старте операции
(backend, перед первой мутацией). Передаётся в response и в каждую audit-запись.

---

## State transitions

### X-1: Process lifecycle (unchanged)

Single-record:
```
CREATING → WAITING (auto)
CREATING → WORKING (rare)
CREATING → ERROR
WAITING → WORKING
WORKING → DONE
WORKING → ERROR
```

Bulk-edit может инициировать любой валидный переход в рамках V-2.

### X-2: Bulk operation lifecycle (new)

```
INITIATED ──(snapshot ids, batch_id, validate)──> VALIDATING
VALIDATING ──(all ids valid)──> RUNNING
VALIDATING ──(any id missing)──> FAILED (early return)
RUNNING ──(all processed, no errors)──> COMPLETED
RUNNING ──(some errors)──> PARTIAL (report с errors[])
RUNNING ──(fatal error)──> FAILED
COMPLETED | PARTIAL | FAILED ──(after retention)──> ARCHIVED
```

`PARTIAL` — нормальное завершение, операция не откатывается (SC-005).

---

## Relationships

```
tbl_processes (id) ──FK──> tbl_processes_audit.process_id (ON DELETE CASCADE)
                    ──in-memory──> KaraokeProcessWorker.threadsMap (runtime)
tbl_processes (process_chain_id) ──self-ref──> tbl_processes.id (цепочки)
tbl_processes (song_id) ──FK──> tbl_songs.id
```

В этой спеке затрагиваются:
- `tbl_processes ↔ tbl_processes_audit` через CASCADE (A-001, D-6).
- `tbl_processes ↔ KaraokeProcessWorker.threadsMap` через interrupt (D-4).
- `tbl_processes ↔ tbl_processes` (chains) — НЕ затрагивается (FR-009 — chainId вне v1).

---

## Naming conventions (DB ↔ Kotlin ↔ UI)

| Концепция | DB (`action` в audit) | Kotlin (service/method) | Endpoint | UI label |
|-----------|------------------------|--------------------------|----------|----------|
| Bulk-edit field | `bulk_update_field` | `bulkUpdateProcesses` | `POST /api/admin/processes/bulk-update` | «Массовые действия → Изменить поле» |
| Bulk-delete | `bulk_delete` | `bulkDeleteProcesses` | `POST /api/admin/processes/bulk-delete` | «Массовые действия → Удалить» |
| (legacy) Single edit | `EDIT` | `editProcess` | `POST /api/admin/processes/{id}/edit` | — |
| (legacy) Single delete | `DELETE` (soft) | `deleteProcess` | `POST /api/admin/processes/{id}/delete` | — |
| (legacy) Single retry | `RETRY` | `retryProcess` | `POST /api/admin/processes/{id}/retry` | — |

Этот маппинг фиксированный. Новые bulk-операции добавляются по тому же шаблону:
`bulk_<verb>` в DB → `bulk<Verb>Processes` в Kotlin → `bulk-<verb>` в URL → label в UI.

## Migration: `48_admin_process_bulk_actions.sql` (NEW)

```sql
-- Спека 319 — Bulk actions for processes (v2).
-- Расширяет CHECK constraint в tbl_processes_audit для bulk-операций
-- и добавляет batch_id для группировки audit-записей по bulk-операции.

-- 1. Расширить CHECK constraint на action.
ALTER TABLE tbl_processes_audit
    DROP CONSTRAINT IF EXISTS tbl_processes_audit_action_check;

ALTER TABLE tbl_processes_audit
    ADD CONSTRAINT tbl_processes_audit_action_check
    CHECK (action IN ('EDIT', 'RETRY', 'DELETE', 'bulk_update_field', 'bulk_delete'));

-- 2. Добавить колонку batch_id для группировки bulk-операций.
ALTER TABLE tbl_processes_audit
    ADD COLUMN IF NOT EXISTS batch_id UUID NULL;

-- 3. Индекс по batch_id для быстрого report-эндпоинта.
CREATE INDEX IF NOT EXISTS idx_tbl_processes_audit_batch_id
    ON tbl_processes_audit(batch_id) WHERE batch_id IS NOT NULL;

-- DOWN-секция:
--   DROP INDEX IF EXISTS idx_tbl_processes_audit_batch_id;
--   ALTER TABLE tbl_processes_audit DROP COLUMN IF EXISTS batch_id;
--   ALTER TABLE tbl_processes_audit DROP CONSTRAINT IF EXISTS tbl_processes_audit_action_check;
--   ALTER TABLE tbl_processes_audit ADD CONSTRAINT tbl_processes_audit_action_check
--       CHECK (action IN ('EDIT', 'RETRY', 'DELETE'));
```

**Применяется**: один раз на LOCAL и PROD отдельно (прецедент миграции 47).

---

## Volume expectations

- `tbl_processes`: ~13 169 живых + ~N архивных (после soft-delete через single-record).
  После bulk-cleanup: возможно уменьшение до ~5 000 живых.
- `tbl_processes_audit`: рост ~`batch_count × avg_batch_size` записей в день.
  Retention 30 дней (см. `cleanupOldAudit`).
- Index `idx_tbl_processes_audit_batch_id` — overhead минимальный (nullable UUID).

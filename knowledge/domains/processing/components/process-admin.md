# Component: process-admin

> **Домен**: [processing](../domain.md)
> **Компонента**: `KaraokeProcessAdminService` + `KaraokeProcessAdminController` —
> UI для управления async-очередью.

## Ответственность | Responsibility

**Admin REST API** для управления задачами в `tbl_processes`.
Используется webvue3 (admin) для ручного управления очередью:
просмотр, фильтрация, редактирование, удаление, retry, bulk-operations.

**Граница**: контекст НЕ отвечает за:

- Саму обработку subprocess'ов — это [async-process-queue.md](async-process-queue.md).
- Добавление новых задач — `KaraokeProcess.createProcess(...)`.

## Ubiquitous Language | Единый язык

| Термин | Определение |
|---|---|
| **`ProcessFilters`** | Параметры фильтрации для `loadProcesses`. |
| **`ProcessNotFoundException`** | 404 — id не найден. |
| **`InvalidStatusTransitionException`** | 400 — невалидный переход статуса. |
| **`editableColumns`** | Map JSON-key → SQL-column для editable полей. |
| **`bulkUpdateProcessesAsync`** | Bulk update в фоне (ProcessListResultBody). |

## Интерфейсы и Контракты

### Контроллер: `KaraokeProcessAdminController`

**Файл**: `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`.

**Endpoints** (по grep `@*Mapping`):

- `GET /api/admin/processes` — список процессов с фильтрами.
- `GET /api/admin/processes/{id}` — один процесс.
- `POST /api/admin/processes/{id}/edit` — редактировать.
- `POST /api/admin/processes/{id}/delete` — удалить.
- `POST /api/admin/processes/{id}/retry` — retry.
- `GET /api/admin/processes/{id}/audit` — audit log.
- `POST /api/admin/processes/bulkUpdate` — bulk update.
- `POST /api/admin/processes/bulkDelete` — bulk delete.
- `POST /api/admin/processes/bulkUpdateAsync` — async bulk update.
- `POST /api/admin/processes/bulkDeleteAsync` — async bulk delete.
- `POST /api/admin/processes/idsByFilter` — получить ids по фильтрам.

**Response wrapper**: `ProcessListResultBody` (содержит results + total).

## Логика и Алгоритмы

### `loadProcesses(filters)`

```kotlin
fun loadProcesses(filters: ProcessFilters): ProcessListResultBody {
    val (where, params) = buildWhere(filters)
    val total = countProcesses(where, params)
    val processes = loadProcessIds(filters, where, params)
    return ProcessListResultBody(processes, total)
}
```

**Фильтры**: `name`, `status`, `type`, `topLevelOnly`, `parentId`,
`limit` (default 100), `offset`.

### `editProcess(id, edits)`

Редактирует **editable columns**:
`name`, `status`, `order`, `priority`, `command`, `args`, `envs`,
`description`, `songId`, `type`, `startedAt`, `endedAt`, `prioritet`,
`withoutControl`, `threadId`.

**НЕ редактируются**: `process_deleted_at` (soft delete),
`process_chain_id` (защита от race, FR-019).

**Transition validation** через `validateTransition`:
- `WAITING → WORKING` — OK.
- `WORKING → DONE/ERROR` — OK.
- `DONE/ERROR → WAITING` (retry) — OK.
- `WORKING → WAITING` — может быть force-stop.
- Прочие → `InvalidStatusTransitionException`.

### `deleteProcess(id)`

Soft delete (FR-019): устанавливает `process_deleted_at = NOW()`.

### `retryProcess(id)`

Переводит задание из `DONE/ERROR` → `WAITING` (FR-012). При наличии
`process_chain_id` — обкает всю цепочку.

### `bulkUpdateProcesses(updates)`

Массовое обновление. Каждое `update` — `id + map of edits`.

### `bulkDeleteProcesses(ids)`

Массовое удаление. Soft delete для каждого id.

### `bulkUpdateProcessesAsync` / `bulkDeleteProcessesAsync`

Async версии: создают `KaraokeProcess` с типом `BULK_UPDATE` или
`BULK_DELETE` (см. async-process-queue.md), и возвращают управление
немедленно. Прогресс через SSE.

**Преимущество**: не блокируют UI при больших объёмах (1000+ записей).

### `loadAudit(id)`

Audit log — история изменений процесса (FR-009).

### `cleanupOldAudit()`

Удаляет старые записи audit (TODO: retention period).

## Архитектурные решения

### Решение 1: editableColumns как JSON-key → SQL-column map

Это позволяет безопасное редактирование через JSON без раскрытия
прямого SQL.

### Решение 2: Soft delete (FR-019)

`process_deleted_at` вместо hard delete — защита от race (admin
удаляет во время того, как worker обрабатывает).

### Решение 3: Bulk + Async версии

Sync для маленьких объов (до 100), async для больших. Прогресс
через SSE.

## Ловушки

1. **`process_chain_id`** — НЕ редактируется. Защита от race
   между admin и worker.
2. **`validateTransition`** — обязателен. Невалидный переход →
   400 Bad Request.
3. **Soft delete** — задание остаётся в БД, только помечается
   удалённым. Worker должен проверять `process_deleted_at` перед
   обработкой (Pass 343+ проверка).
4. **Async bulk operations** — прогресс только через SSE. Не
   все клиенты могут получать SSE (Pass 343+ проверка).

## Связь с другими компонентами

- **Async Process Queue** ([async-process-queue.md](async-process-queue.md)) —
  `bulkUpdateProcessesAsync` создает задание типа `BULK_*`.
- **KaraokeDbTable** ([persistence domain](../../../domains/persistence/domain.md)) —
  стандартное чтение/запись через `KaraokeDbTable`.

## Известные TODO

- [ ] **`cleanupOldAudit`** — retention period, периодичность.
- [ ] **Worker проверяет `process_deleted_at`** — нужно подтвердить.
- [ ] **Async bulk error handling** — что если `KaraokeProcess` для
      async bulk не создался?
- [ ] **`validateTransition`** — полный список переходов.

## Код (физическая реализация)

- `karaoke-app/.../services/KaraokeProcessAdminService.kt` (1000+ строк).
- `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`.
- `karaoke-app/.../controllers/dto/AutoOneClickSyncDtos.kt` —
  `ProcessListResultBody`.

## Changelog

- **Pass 347** (2026-09-09): Initial. Автор: agent (Karaoke).
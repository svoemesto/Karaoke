---
status: Active
slug: 319-process-bulk-actions
related:
  - ../architecture/webvue3-patterns.md
  - ../architecture/dsh-sandbox-conventions.md
  - ../domain/rendering.md
  - ../../specs/319-process-bulk-actions-v2/spec.md
  - ../../docs/features/process-bulk-actions.md
---

# Mass bulk actions on Processes (LiveDoc)

> Сводка. Drill-down: [specs/319-process-bulk-actions-v2/spec.md](../../specs/319-process-bulk-actions-v2/spec.md).

## Что делает

В admin SPA (`webvue3/src/components/Processes/ProcessesTable.vue`) добавляет две
кнопки массовых действий в footer таблицы рядом с фильтром (как в `SongsTable`):
- **Массовое изменение поля** — выбор поля (priority/status/threadId) + нового значения → apply ко всей выборке.
- **Массовое удаление** — hard-delete процессов из выборки (Clarifications Q1: hard-delete вместо soft-delete).

Поддержка до 1000 процессов sync, выше — async через `AdminTaskService` + polling.

## User Stories

- **US1** Mass edit field — выборка по фильтру → изменение одного поля (P1, MVP).
- **US2** Mass hard-delete — hard-delete пачкой с прогрессом и отчётом (P1).
- **US3** Audit report + CSV — после операции модалка с per-id результатами и CSV-выгрузкой (P2).
- **US4** Async для > 1000 — отдельный async endpoint + polling (P2).

## Functional Requirements (указатель)

- **FR-002**: счётчик «отобрано N» в bulk-actions bar.
- **FR-003–FR-006**: whitelist полей, snapshot-id, валидация, audit.
- **FR-007**: async endpoint при > 1000.
- **FR-008**: confirm-диалог для bulk-delete (без ввода слова).
- **FR-009**: UI whitelist `{priority, status, threadId}` в v1; backend принимает все 15 editableColumns.
- **FR-010**: transition-правила для status (те же что в single-edit, FR-017 спеки #315).

## Endpoints

| Path | Method | Лимит | Назначение |
|------|--------|-------|-----------|
| `/api/admin/processes/bulk/snapshot` | GET | 10 000 | Snapshot id по фильтру (FR-002, FR-004) |
| `/api/admin/processes/bulk-update` | POST | 1 000 | Sync bulk-edit (FR-006) |
| `/api/admin/processes/bulk-delete` | POST | 1 000 | Sync hard-delete (FR-008) |
| `/api/admin/processes/bulk-update-async` | POST | — | Async bulk-edit (FR-007) |
| `/api/admin/processes/bulk-delete-async` | POST | — | Async bulk-delete (FR-007) |
| `/api/admin/tasks/{taskId}` | GET | — | Polling (US4) |

## Lessons learned (зафиксировано 2026-09-08, spec 319 review)

### 1. Priority допускает отрицательные значения

Спека WP #68 явно просила «приоритета с 0 на -1» как пример. Не выдумывать
constraint `priority ≥ 0` — он не подтверждён бизнесом. Удалён и из UI (нет `:min="0"`
на input), и из canApply-логики.

### 2. Контракт `<CustomConfirm>` — `header`/`body`/`callback`, НЕ `title`/`message`/`onConfirm`

См. [architecture/webvue3-patterns.md](../architecture/webvue3-patterns.md#контракт-customconfirm--обязательно-проверять-исходник). При использовании
обязательно сверить контракт с исходником компонента или эталонным
`SmartCopyModal.vue`.

### 3. Spring Boot 3.x routing: literal-path не всегда побеждает `/{id}`

`@GetMapping("/ids-by-filter")` ошибочно маршрутизировался на `GET /{id}` (id="ids-by-filter"
→ Int conversion fail → 500). Фикс: literal-path с дополнительным сегментом —
`@GetMapping("/bulk/snapshot")`. Конфликт снят, и `/{id}` не матчит.

### 4. Sync vs async threshold — 1000

`ids.size > 1000` → async через `AdminTaskService` + polling. ≤ 1000 → sync.
Threshold настраивается отдельно (пока хардкод).

### 5. Hard-delete + FK CASCADE = «без следов»

Bulk-delete физически `DELETE FROM tbl_processes WHERE id IN (..)`. FK
`tbl_processes_audit.process_id ... ON DELETE CASCADE` стирает audit-записи.
Это согласованное поведение для Clarifications Q1 «без следов в БД».

### 6. Single vs Bulk delete — known asymmetry

| Действие | Метод | Удаление | Audit |
|----------|-------|----------|-------|
| Single | `deleteProcess` | soft-delete | 1 INSERT |
| Bulk | `bulkDeleteProcesses` | hard-delete | (CASCADE) |

Single жертвует размером таблицы ради audit-истории; bulk — наоборот.

## Связанные LiveDocs

- Domain: [rendering.md](../domain/rendering.md) — процессы рендеринга караоке
- Architecture:
  - [webvue3-patterns.md](../architecture/webvue3-patterns.md) — конвенции модалок и CustomConfirm
  - [ci-cd-pipeline.md](../architecture/ci-cd-pipeline.md) — сборка webvue3

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
  - `controllers/KaraokeProcessAdminController.kt` (extends spec 315)
  - `services/KaraokeProcessAdminService.kt` (+bulkUpdateProcesses, +bulkDeleteProcesses)
  - `services/AdminTaskService.kt` (NEW, in-memory task tracker)
  - `controllers/AdminTaskController.kt` (NEW)
  - `dto/admin/BulkOperationReport.kt`, `BulkError.kt`, `BulkUpdateRequest.kt`, `BulkDeleteRequest.kt`, `IdsByFilterResponse.kt`
- Frontend: `webvue3/src/components/Processes/`
  - `ProcessesTable.vue` (footer c bulk-кнопками)
  - `ProcessesBulkUpdateModal.vue` (single-file inline-форма по конвенции SmartCopyModal)
  - `ProcessBulkReportModal.vue` (отчёт + CSV export)
  - `store.js` (+bulk state + actions с `Content-type: application/json`)
- DB migration: `deploy/karaoke-db/48_admin_process_bulk_actions.sql` (audit CHECK + `batch_id UUID`)
- Per-feature doc: [docs/features/process-bulk-actions.md](../../docs/features/process-bulk-actions.md)

## История

- Создан: 2026-09-08 (Pass 295+)
- Архивный дубль `317-process-bulk-actions-from-wp67/` сохранён в `archive/docs/specs-archive/`
- Последнее обновление: 2026-09-08

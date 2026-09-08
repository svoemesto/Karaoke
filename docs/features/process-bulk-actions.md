# Process Bulk Actions (v2)

**Source**: OpenProject WP #68 (`tracker.sh get-issue 68`)
**Spec**: `specs/319-process-bulk-actions-v2/spec.md`
**Plan**: `specs/319-process-bulk-actions-v2/plan.md`
**Migration**: `deploy/karaoke-db/48_admin_process_bulk_actions.sql`

## Что это

Массовые действия над выборкой процессов из текущего фильтра в `webvue3/.../ProcessesTable.vue`,
по симметрии с паттерном песен (`SongsTable.vue`). Два действия в первой итерации:

1. **Bulk-edit field** — изменить одно поле (`priority` / `status` / `threadId`)
   у всех процессов в выборке.
2. **Bulk-delete** — физическое `DELETE FROM tbl_processes WHERE id IN (..)`.

Дополнительно: детальный отчёт с CSV-выгрузкой (US3), async-вариант для > 1000
процессов (US4, через in-memory `AdminTaskService`).

## Архитектурные решения

| # | Решение | Обоснование |
|---|---------|-------------|
| D-1 | `tbl_processes` НЕ в `SyncRegistry.all` | Процессы — локальная admin-БД, не синхронизируется (см. Constitution III). Future sync — добавить `KaraokeProcessSyncTarget` + 8 флагов (out of scope v1). |
| D-2a | `tbl_processes`: миграция НЕ нужна | Существующая схема покрывает |
| D-2b | `tbl_processes_audit`: миграция ТРЕБУЕТСЯ | Расширить CHECK constraint `action` + добавить `batch_id UUID` |
| D-3 | UI whitelist = 3 поля, backend = все 15 editableColumns | Расширяемость без breaking changes |
| D-4 | Прерывание runtime-потоков | Повторяет single-record паттерн (WORKING → interrupt + grace + destroyForcibly) |
| D-5 | Async threshold = 1000 | SC-001/002 (60s/1000) — выполнимо на sync endpoint; > 1000 — async через AdminTaskService |
| D-6 | FK CASCADE на audit остаётся | Соответствует решению владельца «без следов в БД». Миграция → SET NULL — out of scope v1 |
| D-7 | Single-record `ProcessDeleteModal` остаётся на soft-delete | Known asymmetry: bulk жертвует audit ради скорости, single — наоборот |
| D-8 | Каркас расширяемый | Шаблон: backend `bulkXxxProcesses` + endpoint, frontend `bulkXxxModal` + Vuex action |

## Endpoints

| Path | Method | Sync/Async | Лимит | Описание |
|------|--------|-----------|-------|----------|
| `/api/admin/processes/bulk/snapshot` | GET | sync | 10 000 | Snapshot id по фильтру (FR-002, FR-004) |
| `/api/admin/processes/bulk-update` | POST | sync | 1 000 | Bulk-edit одного поля (FR-006) |
| `/api/admin/processes/bulk-delete` | POST | sync | 1 000 | Bulk hard-delete (FR-008, A-001) |
| `/api/admin/processes/bulk-update-async` | POST | async | — | Стартует task, возвращает `taskId` |
| `/api/admin/processes/bulk-delete-async` | POST | async | — | Стартует task, возвращает `taskId` |
| `/api/admin/tasks/{taskId}` | GET | polling | — | Статус async-задачи |

## Frontend компоненты

| Файл | Назначение |
|------|------------|
| `webvue3/.../Processes/ProcessesTable.vue` | +bulk-actions bar с счётчиком + 2 кнопки (Изменить поле / Удалить) |
| `webvue3/.../Processes/ProcessesBulkUpdateModal.vue` | NEW — модалка выбора field + value + preview |
| `webvue3/.../Processes/ProcessBulkReportModal.vue` | NEW — отчёт + CSV-выгрузка (US3) |
| `webvue3/.../Processes/store.js` | +bulk state, +4 actions (fetchBulkSelectionIds, bulkUpdate, bulkDelete, async варианты) |
| `webvue3/.../Processes/filter/ProcessesFilterModal.vue` | +dispatch `fetchBulkSelectionIds` при apply filter |

## Операционные заметки

### Sync vs Async

- **< 1000** — sync endpoint, UI ждёт ответ (≤ 60 секунд).
- **> 1000** — async endpoint, UI показывает spinner + polling каждые 2 сек.

### Hard-delete

`bulkDeleteProcesses` делает физический `DELETE FROM tbl_processes WHERE id IN (..)`.
Audit-trail удаляется каскадно (FK ON DELETE CASCADE на `tbl_processes_audit.process_id`).
Это согласовано с решением владельца «без следов в БД» (Clarifications Q1).

**Предупреждение**: bulk-delete **нельзя откатить** (даже через audit-trail —
он стёрт вместе с процессом). UI просит подтверждение через `window.confirm`.

### Migration apply

Файл `deploy/karaoke-db/48_admin_process_bulk_actions.sql` НЕ применён автоматически
(нет psql credentials в CI-сессии). Владелец применяет вручную:

```bash
psql -h <host> -U karaoke -d karaoke_<env> < deploy/karaoke-db/48_admin_process_bulk_actions.sql
```

### `AdminTaskService` — in-memory

Async-задачи хранятся в памяти процесса `karaoke-app`. После рестарта — теряются.
Это нормально для v1 (владелец не оставляет процессы на полпути); для
персистентности в будущем — отдельный `tbl_admin_tasks` + cron cleanup.

### Single vs Bulk delete (asymmetry)

| Действие | Метод | Удаление | Audit |
|----------|-------|----------|-------|
| Single (UI кнопка) | `deleteProcess` | soft-delete (`process_deleted_at`) | 1 INSERT в audit |
| Bulk удаление | `bulkDeleteProcesses` | hard-delete (DELETE row) | ничего (CASCADE стирает) |

Это **намеренная** асимметрия, не баг. Single жертвует размером таблицы ради
audit-истории; bulk — наоборот.

## Roadmap (out of scope v1)

- **Bulk-retry** — повторный запуск упавших ERROR-процессов пачкой.
- **Bulk-restore** — снятие `process_deleted_at` пачкой (для single-record).
- **Undo bulk** — откат последней bulk-операции (требует snapshot до изменений).
- **Persistent admin-tasks** — выживают после рестарта `karaoke-app`.
- **chainId bulk-edit** — перепривязка процессов к другой цепочке (UI v1 запрещает,
  Q3 Clarifications).
- **FK SET NULL на audit** — если потребуется сохранять audit-trail после hard-delete.

## История решений

- **2026-09-08**: архивация `specs/317-process-bulk-actions-from-wp67/` (дубль WP #67),
  старт v2 на WP #68. Владелец выбрал «Свежий spec на #68, откатить 317».
- **2026-09-08 (Stage 2 Clarifications)**: Q1 hard-delete, Q2 confirm-dialog (без ввода
  слова), Q3 chainId не в v1.
- **2026-09-08 (Stage 3 Plan)**: открытие ветки `319-process-bulk-actions-v2`; тщательное
  исследование выявило, что `tbl_processes` не в SyncRegistry (упростило hard-delete).
- **2026-09-08 (Stage 5 Analyze)**: 9 находок (2 HIGH, 4 MEDIUM, 3 LOW) — все исправлены
  до начала implementation.
- **2026-09-08 (Stage 6 Implement)**: 22/39 задач выполнены в первой итерации
  (Phase 1-4: foundation + US1 + US2); оставшиеся 17 — US3 + US4 + Polish — во второй
  итерации (та же сессия).

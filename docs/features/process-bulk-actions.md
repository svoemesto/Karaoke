# Process Bulk Actions (v2)

> **Status**: active
> **Feature Key**: process-bulk-actions
> **Last Updated**: 2026-09-27
> **Source**: OpenProject WP #68 (`tracker.sh get-issue 68`)
> **Spec**: [specs/319-process-bulk-actions-v2/spec.md](../../specs/319-process-bulk-actions-v2/spec.md)
> **Plan**: [specs/319-process-bulk-actions-v2/plan.md](../../specs/319-process-bulk-actions-v2/plan.md)
> **Migration**: [deploy/karaoke-db/48_admin_process_bulk_actions.sql](../../deploy/karaoke-db/48_admin_process_bulk_actions.sql)

## Что делает

Массовые действия над выборкой процессов из текущего фильтра в
`webvue3/src/components/Processes/ProcessesTable.vue`, по симметрии с паттерном песен
(`SongsTable.vue`). Два действия в первой итерации:

1. **Bulk-edit field** — изменить одно поле (`priority` / `status` / `threadId`)
   у всех процессов в выборке.
2. **Bulk-delete** — физическое `DELETE FROM tbl_processes WHERE id IN (..)`.

Дополнительно: детальный отчёт с CSV-выгрузкой (US3), async-вариант для > 1000
процессов (US4, через in-memory `AdminTaskService`).

## Зачем

Админ, отфильтровав список процессов, раньше мог менять их только по одному — правка
`priority`/`status`/`threadId` или удаление сотен записей требовали сотен кликов и
запросов. Bulk-действия над текущей выборкой повторяют уже привычный паттерн
`SongsTable.vue`, экономя время на массовых операциях. Отдельный async-путь нужен,
чтобы операции над тысячами процессов не держали HTTP-запрос открытым дольше 60 секунд
(SC-001/002).

## Как работает (кратко)

UI делает snapshot id по текущему фильтру (`GET /bulk/snapshot`), затем отправляет
id пачкой в sync endpoint (≤ 1000 id) или стартует async-задачу (> 1000 id) и
опрашивает её статус. Backend выполняет bulk-edit через `KaraokeProcessAdminService`,
bulk-delete — физическим `DELETE` с каскадным стиранием audit-trail. Async-задачи
хранятся в памяти `AdminTaskService` и после рестарта теряются.

### Архитектурные решения

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

### Endpoints

| Path | Method | Sync/Async | Лимит | Описание |
|------|--------|-----------|-------|----------|
| `/api/admin/processes/bulk/snapshot` | GET | sync | 10 000 | Snapshot id по фильтру (FR-002, FR-004) |
| `/api/admin/processes/bulk-update` | POST | sync | 1 000 | Bulk-edit одного поля (FR-006) |
| `/api/admin/processes/bulk-delete` | POST | sync | 1 000 | Bulk hard-delete (FR-008, A-001) |
| `/api/admin/processes/bulk-update-async` | POST | async | — | Стартует task, возвращает `taskId` |
| `/api/admin/processes/bulk-delete-async` | POST | async | — | Стартует task, возвращает `taskId` |
| `/api/admin/tasks/{taskId}` | GET | polling | — | Статус async-задачи |

### Frontend компоненты

| Файл | Назначение |
|------|------------|
| `webvue3/src/components/Processes/ProcessesTable.vue` | +bulk-actions bar с счётчиком + 2 кнопки (Изменить поле / Удалить) |
| `webvue3/src/components/Processes/ProcessesBulkUpdateModal.vue` | NEW — модалка выбора field + value + preview |
| `webvue3/src/components/Processes/ProcessBulkReportModal.vue` | NEW — отчёт + CSV-выгрузка (US3) |
| `webvue3/src/components/Processes/store.js` | +bulk state, +4 actions (fetchBulkSelectionIds, bulkUpdate, bulkDelete, async варианты) |
| `webvue3/src/components/Processes/filter/ProcessesFilterModal.vue` | +dispatch `fetchBulkSelectionIds` при apply filter |

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

## Инварианты

- **MUST**: sync bulk-endpoints принимают не более `bulkSyncLimit = 1000` id; при
  превышении — отказ с подсказкой использовать async-вариант
  (`KaraokeProcessAdminController.kt`). См. [constitution.md](../../.specify/memory/constitution.md).
- **MUST**: `GET /bulk/snapshot` ограничен `idsByFilterLimit = 10000` id (защита от
  over-fetch).
- **MUST**: `tbl_processes` НЕ входит в `SyncRegistry.all` — это локальная admin-БД,
  не синхронизируется (D-1, Constitution III).
- **MUST**: UI whitelist bulk-edit — ровно 3 поля (`priority` / `status` / `threadId`);
  backend поддерживает все 15 editableColumns (D-3). `chainId` в UI v1 запрещён
  (Q3 Clarifications).
- **MUST**: bulk-delete — физический `DELETE`; audit-trail стирается каскадом
  (FK ON DELETE CASCADE) — это осознанное решение владельца «без следов в БД» (D-6).
- **MUST**: миграция `48_admin_process_bulk_actions.sql` применяется вручную
  владельцем — CI её не применяет.
- **MUST**: async-задачи живут только в памяти `AdminTaskService`; персистентности
  нет (v1).
- **SHOULD**: изменения по коду фичи сопровождать обновлением этого документа
  ([AGENTS.md](../../AGENTS.md), [constitution.md](../../.specify/memory/constitution.md)).

## Известные ловушки

- **Bulk-delete необратим** — audit-trail стирается каскадом вместе с процессами,
  откатить операцию нельзя даже через audit (UI просит `window.confirm`).
- **Миграция не применена автоматически** — `deploy/karaoke-db/48_admin_process_bulk_actions.sql`
  нужно применить вручную (`psql ...`), иначе bulk-операции не получат `batch_id` и
  расширенный CHECK `action`.
- **Async-задачи теряются при рестарте** `karaoke-app` — `AdminTaskService` держит их
  в памяти; polling `GET /api/admin/tasks/{taskId}` после рестарта вернёт «нет задачи».
- **Намеренная асимметрия single vs bulk delete**: single — soft-delete + audit,
  bulk — hard-delete без audit. Это не баг (D-7), но легко принять за него при
  сравнении поведения.
- **> 1000 id в sync endpoint отклоняется** — не «тихо усекается»; фронт обязан
  переключиться на async-вариант, иначе операция не выполнится.
- **`tbl_processes` вне `SyncRegistry.all`** — при добавлении синхронизации в будущем
  потребуется `KaraokeProcessSyncTarget` + 8 флагов (out of scope v1).
- **`chainId` bulk-edit в v1 запрещён в UI** — попытка перепривязки цепочки через
  bulk-edit не поддерживается (Q3 Clarifications).

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

## Ссылки

- [`specs/319-process-bulk-actions-v2/spec.md`](../../specs/319-process-bulk-actions-v2/spec.md)
  — спецификация (FR-001…FR-008, US1…US4, Clarifications).
- [`specs/319-process-bulk-actions-v2/plan.md`](../../specs/319-process-bulk-actions-v2/plan.md)
  — implementation plan.
- [`deploy/karaoke-db/48_admin_process_bulk_actions.sql`](../../deploy/karaoke-db/48_admin_process_bulk_actions.sql)
  — миграция `tbl_processes_audit` (CHECK `action` + `batch_id`, partial index).
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt)
  — bulk/snapshot/sync/async endpoints, `bulkSyncLimit`, `idsByFilterLimit`.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/AdminTaskController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/AdminTaskController.kt)
  — `GET /api/admin/tasks/{taskId}` (polling статуса async-задачи).
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt)
  — `bulkUpdateProcesses`, `bulkDeleteProcesses`, async-варианты.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AdminTaskService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AdminTaskService.kt)
  — in-memory реестр async-задач.
- [`webvue3/src/components/Processes/ProcessesTable.vue`](../../webvue3/src/components/Processes/ProcessesTable.vue)
  — bulk-actions bar, кнопки «Изменить поле» / «Удалить».
- [`webvue3/src/components/Processes/ProcessesBulkUpdateModal.vue`](../../webvue3/src/components/Processes/ProcessesBulkUpdateModal.vue)
  — модалка выбора поля и значения.
- [`webvue3/src/components/Processes/ProcessBulkReportModal.vue`](../../webvue3/src/components/Processes/ProcessBulkReportModal.vue)
  — отчёт и CSV-выгрузка (US3).
- [`webvue3/src/components/Processes/store.js`](../../webvue3/src/components/Processes/store.js)
  — bulk state, getters, mutations и actions.

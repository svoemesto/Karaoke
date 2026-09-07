---
status: Active
slug: admin-ui-karaoke-process-v5
related:
  - specs/315-admin-ui-karaoke-process-v5/spec.md
  - specs/315-admin-ui-karaoke-process-v5/plan.md
  - specs/315-admin-ui-karaoke-process-v5/tasks.md
---

# Admin UI для KaraokeProcess (v5)

Админ-панель управления процессами (`/admin/processes`): просмотр с фильтрацией и
parent-child визуализацией, редактирование, удаление (soft-delete), retry ERROR-процессов,
просмотр audit-лога. OpenProject WP #64.

## Что делает

- **US1 (Просмотр)**: top-level head-процессы (`process_chain_id IS NULL`), фильтры по
  статусу/типу/threadId/chainId/name/includeDeleted, разворачивание head → lazy load
  tail-детей. Soft-deleted процессы — зачёркнутый текст + 🗑️.
- **US2 (Edit)**: кнопка «Редактировать» в Actions-колонке → модалка со всеми
  редактируемыми полями (FR-008). Валидация переходов статуса (FR-017).
- **US3 (Delete)**: soft-delete по статусу (DONE/ERROR — только soft-delete; WAITING/CREATING —
  cancel; WORKING — interrupt + 5 сек grace + destroyForcibly). БЕЗ cascade (FR-013).
- **US4 (Retry)**: ERROR → WAITING (только для не удалённых, `process_deleted_at IS NULL`).
- **US5 (Audit)**: история EDIT/RETRY/DELETE за 30 дней (retention cron).

## Где в коде

**Backend** (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`):
- `KaraokeProcess.kt` — поля `processChainId`/`processDeletedAt` + R-019 pickup-фильтр
  `AND process_deleted_at IS NULL` в `getProcessesToStart()`.
- `dto/admin/KaraokeProcessAdminDTO.kt` — DTO (20 полей) + ProcessAuditDTO + ProcessListResult.
- `services/KaraokeProcessAdminService.kt` — `@DependsOn("karaokeAppService")`, 6 методов,
  `make_interval(days => ?)`, cron `INTERVAL '30 days'`.
- `controllers/KaraokeProcessAdminController.kt` — `@DependsOn("karaokeAppService")`, 6 endpoints.

**Frontend** (`webvue3/src/components/Processes/`):
- `ProcessesTable.vue` — таблица + parent-child viz + Actions-колонка.
- `filter/ProcessesFilterModal.vue` + `filter/store.js` — фильтры.
- `store.js` — actions loadProcesses/loadChildren/editProcess/deleteProcess/retryProcess/loadAudit.
- `edit/ProcessEditModal.vue` + `edit/ProcessEdit.vue` — форма редактирования.
- `delete/ProcessDeleteModal.vue` — подтверждение удаления.
- `audit/ProcessAuditModal.vue` — audit-лог.

## Ключевые решения и уроки

- **`@DependsOn("karaokeAppService")` на service И controller** (Lesson #4) — сервис читает
  `WORKING_DATABASE`, инициализируемый в KaraokeAppService.
- **Race protection (FR-019)**: `KaraokeProcess.save()` НЕ пишет `process_deleted_at`/
  `process_chain_id`; edit/delete/retry — отдельные targeted UPDATE.
- **R-019**: pickup-логика Worker фильтрует `process_deleted_at IS NULL`, чтобы не подхватывать
  soft-deleted процессы.
- **INTERVAL**: `make_interval(days => ?)` + `ps.setInt` (НЕ `INTERVAL '? days'` — урок RC-2).
- **Реальные колонки**: `last_update`/`process_start`/`process_end` (НЕ `created_at`/
  `updated_at`/`started_at`/`ended_at` — урок #11 iter #3).
- **KaraokeProcessQueue НЕ существует** — реальный механизм `KaraokeProcessWorker.threadsMap`.

## Как тестировать

Владелец прогоняет `specs/315-admin-ui-karaoke-process-v5/quickstart.md` (10 scenarios):
1. Открыть `/admin/processes` → top-level загружается, expand head → дети.
2. Edit процесса → сохранить → таблица обновляется.
3. Delete WAITING/WORKING → soft-delete.
4. Retry ERROR → WAITING.
5. Audit-модалка показывает записи.

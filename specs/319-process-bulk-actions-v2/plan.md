# Implementation Plan: Массовые действия с процессами (v2)

**Branch**: `319-process-bulk-actions-v2` | **Date**: 2026-09-08 | **Spec**: `specs/319-process-bulk-actions-v2/spec.md`
**Source**: OpenProject WP #68 (свежая попытка после архивации `specs/317-process-bulk-actions-from-wp67/`)

---

## Summary

Добавить в admin SPA (`webvue3`) два массовых действия над выборкой процессов из
текущего фильтра, по симметрии с `SongsTable`:

1. **Bulk-edit field** — изменить одно поле (`priority`, `status`, `threadId`)
   у всех процессов в выборке. Переходы статуса — по правилам single-edit
   (FR-017 спеки #315).
2. **Bulk-delete** — физически `DELETE FROM tbl_processes WHERE id IN (...)`
   (по решению владельца, Clarifications Q1 2026-09-08). Через `WHERE id IN`
   батчем; рабочие процессы прерываются по паттерну single-record
   `KaraokeProcessAdminService.deleteProcess` (interrupt + 5 сек grace + forcibly).

Каркас расширяемый: новые массовые действия (reassign-chain, retry, restore)
добавляются без переделки архитектуры.

Объём работ: backend (~5 новых методов в `KaraokeProcessAdminService`,
2 endpoint'а в `KaraokeProcessAdminController`, миграция схемы при необходимости),
frontend (`ProcessesBulkActions` modal, кнопки в `ProcessesTable`,
новые actions в Vuex-store, обновление `ProcessesFilter` для подсчёта выборки).

## Technical Context

**Language/Version**: Kotlin 1.x (JVM 17), Spring Boot 3.x
**Primary Dependencies**:
- Backend: Spring Web (`@RestController` + JSON), JDBC (raw, через `KaraokeConnection`),
  PostgreSQL driver, kotlinx.serialization/json (через `Json.encodeToString` для jsonb).
- Frontend: Vue 3 + Vite + Vuex + Bootstrap-vue-next, существующие
  `custom-confirm` / `SmartCopyModal` для подтверждений.

**Storage**: PostgreSQL (через сырой JDBC, без JPA/Hibernate — Constitution II).
- `tbl_processes` — основная таблица (см. `KaraokeProcess.kt:56`).
- `tbl_processes_audit` — audit-trail (миграция `47_admin_process_audit.sql`).
- `KaraokeProcessWorker.threadsMap` — runtime registry живых процессов
  (in-memory, требует interrupt перед delete для WORKING/WAITING/CREATING).

**Testing**: в CI нет; `karaoke-app/src/test` — `@Disabled` интеграционные.
Проверка — владельцем вручную. Локальные smoke-проверки через `karaoke-app:bootJar`
+ ручные curl/UI-клики (Pass 282: на `nsa-i9` агенту разрешён перезапуск
`karaoke-app` без согласия).

**Target Platform**: Linux (admin-машина `nsa-i9`), Docker (`eclipse-temurin:22-jre-jammy`),
Nginx (`nginx:stable`).

**Project Type**: web (Spring Boot admin API + Vue 3 SPA).

**Performance Goals**:
- SC-001: bulk-edit на 1000+ процессов ≤ 60 секунд (sync endpoint).
- SC-002: bulk-delete на 1000+ процессов ≤ 60 секунд.
- На 1000+ — async через `KaraokeTask` + polling progress в UI (A-006).

**Constraints**:
- Constitution II: raw JDBC, `WHERE id IN (..)` батчами, `associateBy { it.id }`.
- Constitution III: `tbl_processes` НЕ в `SyncRegistry.all` (проверено, см. D-1);
  sync-миграция НЕ требуется.
- Constitution V: `webvue3` admin — `permitAll()`, без авторизации;
  `X-Admin-Username` header опционально (default `admin:unknown`).

**Scale/Scope**:
- 13 169+ legacy процессов на проде (прецедент iter #4 спеки #315).
- Реальный сценарий — очистка 1 000–10 000 процессов за один запуск.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Статус | Комментарий |
|---|---------|--------|-------------|
| I | Self-contained pipeline | ✅ Pass | UI/DB фича, без внешних SaaS |
| II | Raw JDBC + diff по хэшам | ✅ Pass | Все DB-операции — raw JDBC через `KaraokeConnection`; bulk операции идут `WHERE id IN (..)` батчами |
| III | Двух-БД синхронизация | ✅ Pass (N/A) | `tbl_processes` НЕ в `SyncRegistry.all` — sync-миграция не требуется (см. D-1) |
| IV | Async + парсинг stdout | ✅ Pass (N/A) | `KaraokeProcess` (Constitution IV) — для OS-подпроцессов; bulk-операции — это bulk DB-операции, не OS-subprocess, поэтому `KaraokeProcess` неприменимо. Bulk async использует существующий `AdminTaskService` (или новый `ProcessBulkTaskService`) с polling через `/api/admin/tasks/{taskId}` (см. R-5/R-4, US4). Для sync — endpoint ≤ 60s (FR-007, SC-001/002). |
| V | Двух-фронтенд | ✅ Pass | Фича только в `webvue3` (admin), не трогает `karaoke-public` |
| VI | Code Standards (KDoc/JSDoc/lint) | ✅ Pass | Все новые публичные API сопровождаются KDoc с `@see` на `specs/319-process-bulk-actions-v2/spec.md`; pre-commit хуки покрывают |
| VII | Cross-Machine Setup | ✅ Pass (N/A) | Локальные AI-конфиги не меняются |
| VIII | Secrets & git-гигиена | ✅ Pass | Никаких секретов; pre-commit проверит |

**Re-check после Phase 1**: см. секцию «Constitution Re-Check» ниже.

## Project Structure

### Documentation (this feature)

```text
specs/319-process-bulk-actions-v2/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── admin-process-bulk-rest-api.md   # REST контракт
│   └── webvue3-bulk-store-actions.md    # Vuex actions контракт
└── tasks.md             # Phase 2 output (создаётся /speckit.tasks)
```

### Source Code (repository root)

Проект — Spring Boot + Vue 3 (web backend + admin SPA). Существующая структура:

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── controllers/
    │   └── KaraokeProcessAdminController.kt     # расширяется на +2 endpoint'а
    └── services/
        └── KaraokeProcessAdminService.kt        # расширяется на +2 метода

webvue3/
└── src/components/Processes/
    ├── ProcessesTable.vue                       # +группа bulk-кнопок
    ├── ProcessesBulkUpdateModal.vue             # NEW
    ├── ProcessesBulkDeleteModal.vue             # NEW (опционально, можно inline confirm)
    ├── store.js                                 # +2 actions (bulkUpdateProcesses, bulkDeleteProcesses)
    └── filter/ProcessesFilterModal.vue          # без изменений
```

**Structure Decision**: Расширяем существующие файлы. Новые компоненты — `ProcessesBulkUpdateModal.vue`,
`ProcessesBulkDeleteModal.vue` (если нужно). Миграция SQL — новая
`deploy/karaoke-db/48_admin_process_bulk_actions.sql` (см. D-2b, требуется для
расширения CHECK constraint и добавления `batch_id` колонки).

## Implementation Plan (high-level)

### Phase A — Backend (Spring + JDBC)

1. **A-1**: Расширить `KaraokeProcessAdminService`:
   - `bulkUpdateProcesses(ids: List<Int>, field: String, value: Any?, actor, db)` — батч `UPDATE` по `WHERE id IN (..)`,
     `validateTransition` для status, audit-batch `bulk_update_field` на каждую затронутую строку,
     `batchId` через `UUID.randomUUID()`.
   - `bulkDeleteProcesses(ids: List<Int>, actor, db)` — для каждого id: interrupt thread
     (если WORKING/WAITING/CREATING), затем `DELETE FROM tbl_processes WHERE id = ?` (FK CASCADE уберёт audit),
     `batchId` через `UUID.randomUUID()`.
   - Валидация `field` whitelist — только поля из `editableColumns` (`priority`, `status`, `threadId` и др.).
   - `ids.size > 1000` → sync (≤60s target); `ids.size > 1000` → отдать через admin-task endpoint (см. A-3).

2. **A-2**: Расширить `KaraokeProcessAdminController`:
   - `POST /api/admin/processes/bulk-update` — принимает `{ ids: List<Int>, field: String, value: Any?, batchId?: UUID }`.
   - `POST /api/admin/processes/bulk-delete` — принимает `{ ids: List<Int>, batchId?: UUID }`.
   - Тело ответа: `{ batchId, requested, succeeded, failed, errors: [{id, reason}] }`.

3. **A-3**: Для ≥ 1000 — admin-task endpoint:
   - `POST /api/admin/processes/bulk-update-async` / `bulk-delete-async` — стартует KaraokeTask.
   - `GET /api/admin/tasks/{taskId}` — polling прогресса.
   - Реализация — через существующий `AdminTaskService` (если есть) или
     добавить новый `ProcessBulkTaskService`.

4. **A-4**: Миграция SQL — ТРЕБУЕТСЯ для `tbl_processes_audit` (см. D-2b); НЕ требуется для `tbl_processes`. Файл `deploy/karaoke-db/48_admin_process_bulk_actions.sql` создаётся в Phase 1 (T001), применяется в Phase 2 (T002).

### Phase B — Frontend (Vue 3 + Vuex)

5. **B-1**: Расширить `Processes/store.js`:
   - `bulkUpdateProcesses(ctx, { ids, field, value })` — `POST /api/admin/processes/bulk-update`,
     обработка отчёта.
   - `bulkDeleteProcesses(ctx, { ids })` — `POST /api/admin/processes/bulk-delete`.
   - Helpers для async-варианта (если реализован A-3).

6. **B-2**: Расширить `ProcessesTable.vue`:
   - Computed `selectedIds` (по текущему фильтру) — массив всех `id` ВНЕ текущей
     пагинации (snapshot на момент применения фильтра, не динамический).
   - Computed `selectedCount` — длина `selectedIds`.
   - Computed `canBulk` — `selectedCount >= 1`.
   - UI: над таблицей группа кнопок «Массовые действия → Изменить поле» / «Удалить».
     Disabled если `!canBulk`. Tooltip: «нет процессов в выборке».

7. **B-3**: `ProcessesBulkUpdateModal.vue` — поле + значение + превью.
   - `<custom-confirm>` для подтверждения (симметрия с `SongsTable`).
   - Валидация на клиенте: `field` — select из `{priority, status, threadId}`,
     `value` — соответствующего типа.
   - Превью: «Будет изменено: N процессов, поле X, новое значение Y».
   - По завершении — отчёт (таблица успехов/ошибок).

8. **B-4**: `ProcessesBulkDeleteModal.vue` (опционально) или inline `<custom-confirm>`:
   - Текст: «Будет удалено N процессов. Действие необратимо».
   - `dispatch('bulkDeleteProcesses', { ids })`.

9. **B-5**: After-action report (US3):
   - По завершении bulk-операции — уведомление «Готово: K из N. Открыть отчёт».
   - Отчёт в `ProcessAuditModal` (расширить) или новый `ProcessBulkReportModal.vue`.
   - CSV-выгрузка через `Blob` + `URL.createObjectURL`.

### Phase C — Polish

10. **C-1**: KDoc/JSDoc (FR-006):
    - Все новые `class`/`fun`/`Vuex action` — с `@see` на `specs/319-process-bulk-actions-v2/spec.md`.
11. **C-2**: Per-feature документ:
    - `docs/features/process-bulk-actions.md` (Pass FR-009).
12. **C-3**: Smoke-проверка на dev-стенде (Pass 282, nsa-i9):
    - Создать 5 ERROR-процессов, bulk-edit status→WAITING, проверить БД.
    - Bulk-delete 10 ERROR-процессов, проверить отсутствие в БД и audit CASCADE.
    - 7/7 pre-commit (ktlint, ESLint, Prettier, KDoc, JSDoc, pre-commit, docs).

## Key Decisions

### D-1: `tbl_processes` НЕ в `SyncRegistry.all` → sync-миграция не нужна

**Finding**: `karaoke-app/.../sync/SyncTarget.kt:503-523` содержит 18 sync-сущностей
(Song, Pictures, Authors, …), но НЕ `KaraokeProcess`/`ProcessSyncTarget`.
**Implication**: `tbl_processes` — локальная таблица admin-БД, **не синхронизируется**
на сервер. Constitution III в части sync-регистра к этой фиче неприменима.
Hard-delete (Clarifications Q1) не требует удаления из SyncRegistry.

**Future sync (out of scope этой спеки)**: при добавлении процессов в sync — добавить
`KaraokeProcessSyncTarget : GenericKaraokeDbTableSyncTarget<KaraokeProcess>` в
`SyncRegistry.all` + 8 флагов `sync_processes_<push|pull>_<insert|update|delete|move>_allowed`
в `KaraokeProperties.kt`. См. Constitution III и прецедент `SongSyncTarget` (line 221-265).

### D-2: Migrations

**D-2a `tbl_processes`**: миграция НЕ требуется. Существующая схема (миграция
`47_admin_process_audit.sql`) покрывает bulk-edit/bulk-delete полностью.

**D-2b `tbl_processes_audit`**: миграция ТРЕБУЕТСЯ — новый файл
`deploy/karaoke-db/48_admin_process_bulk_actions.sql`. Изменения:
1. Расширить CHECK constraint `action` whitelist с
   `('EDIT', 'RETRY', 'DELETE')` до
   `('EDIT', 'RETRY', 'DELETE', 'bulk_update_field', 'bulk_delete')`.
2. Добавить колонку `batch_id UUID NULL` для группировки audit-записей по bulk-операции.
3. Индекс `idx_tbl_processes_audit_batch_id` для быстрого report-эндпоинта.

DDL — см. `data-model.md` → «Migration: `48_admin_process_bulk_actions.sql`».
FK CASCADE на audit (line 35 существующей миграции) уже согласован с
hard-delete (line 30-32 явно упоминает это).

### D-3: Bulk-edit поддерживает поля из `editableColumns`

**Finding**: `KaraokeProcessAdminService.kt:74-91` определяет whitelist редактируемых полей:
`name`, `status`, `order`, `priority`, `command`, `args`, `envs`, `description`,
`songId`, `type`, `startedAt`, `endedAt`, `prioritet`, `withoutControl`, `threadId`.
**Decision**: В v1 UI ограничивает field select до `{priority, status, threadId}`
(см. FR-009 + Clarifications Q3). Backend принимает любое поле из whitelist
(расширяемость для будущих UI-полей).

### D-4: Hard-delete с прерыванием runtime-потоков

**Finding**: Существующий `deleteProcess` (single-record) прерывает WORKING/WAITING/CREATING
потоки через `KaraokeProcessWorker.threadsMap`, потом soft-delete.
**Decision**: Bulk-delete повторяет эту логику для каждого id батча:
- `WORKING` → interrupt + 5 sec grace + `destroyForcibly`, потом `DELETE`.
- `WAITING`/`CREATING` → interrupt (no grace), потом `DELETE`.
- `DONE`/`ERROR` → `DELETE` сразу.

**Risk**: 1000+ WORKING-процессов одновременно — потенциальная нагрузка на `destroyForcibly`.
A-006: для > 1000 — async endpoint.

### D-5: Async threshold 1000

**Decision**: На ≥ 1000 процессов — async endpoint с polling прогресса (A-3).
На < 1000 — sync endpoint, ожидание в UI ≤ 60 секунд (SC-001/002).
Threshold настраивается через `KaraokeProperties` (прецедент — `search-timeout-configurable`,
spec 316).

### D-6: Audit-trail cascade — known & accepted

**Finding**: FK `tbl_processes_audit.process_id ... ON DELETE CASCADE` →
audit-строки удаляются вместе с процессом.
**Decision**: Оставляем CASCADE. Соответствует решению владельца «без следов в БД»
(Clarifications Q1). Документируем в spec (FR-005). Если в будущем потребуется
сохранять audit после hard-delete — отдельная миграция (FK → SET NULL).

### D-7: Single-record `ProcessDeleteModal` остаётся на soft-delete

**Finding**: Существующий `deleteProcess` — soft-delete (UPDATE `process_deleted_at`).
**Decision**: Оставляем как есть. Bulk-delete и single-delete — **разные режимы**.
Это known asymmetry: bulk жертвует audit ради скорости и размера таблицы,
single жертвует размером ради audit. Документируем в spec (A-001).

### D-8: Каркас расширяемый (FR-009)

**Pattern**:
- Backend: новый метод `bulkXxxProcesses(ids, params)` в сервисе +
  endpoint в контроллере с тем же шаблоном response.
- Frontend: новый `<Xxx>Modal.vue` + Vuex action `bulkXxxProcesses` +
  кнопка в группе «Массовые действия».
- Audit: action string в whitelist `('EDIT', 'RETRY', 'DELETE', 'bulk_update_field', 'bulk_delete')`.

Чеклист расширения (для будущих bulk-операций): docs в `quickstart.md`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Hard-delete bulk при том, что single — soft-delete (D-7) | Владелец явно выбрал «Hard-delete» (Clarifications Q1) — 13 169 legacy процессов, размер таблицы растёт, sync отсутствует | Soft-delete для bulk = та же проблема, что и single; не решает основную боль (Legacy-хвосты) |
| Async endpoint для ≥ 1000 (D-5) | UI timeout, SC-001/002 (60s/1000), удержание HTTP-соединения нестабильно | Sync для всех = UI виснет на больших выборках; polling существующий admin-task механизм уже есть |
| FK CASCADE на audit (D-6) | Hard-delete = «без следов» (владелец); FK уже настроен в миграции 47 | SET NULL на audit = храним мусорные `process_id` без `tbl_processes` строки, усложняет audit-логику |

## Constitution Re-Check (post-Phase 1)

| # | Принцип | Статус | Изменения |
|---|---------|--------|-----------|
| I | Self-contained | ✅ Pass | без изменений |
| II | Raw JDBC + diff | ✅ Pass | bulk идёт через `WHERE id IN (..)` батчами (см. A-1); никаких N+1 |
| III | Двух-БД sync | ✅ Pass | подтверждено N/A после исследования (D-1) |
| IV | Async + парсинг stdout | ✅ Pass | для > 1000 — admin-task (см. A-3); для ≤ 1000 — sync endpoint |
| V | Двух-фронтенд | ✅ Pass | только admin SPA |
| VI | Code Standards | ✅ Pass | KDoc/JSDoc требования зафиксированы в C-1 |
| VII | Cross-Machine | ✅ Pass | без изменений |
| VIII | Secrets | ✅ Pass | без изменений |

## Governance

- **Source**: WP #68 (`tracker.sh get-issue 68`), spec v2 от 2026-09-08.
- **Supersedes**: `archive/docs/specs-archive/317-process-bulk-actions-from-wp67/`
  (archived per Clarifications Q1 prep — duplicate of WP #67, abandoned by owner).
- **Per-feature документ**: `docs/features/process-bulk-actions.md` создаётся в C-2.
- **Constitution amendment**: не требуется.
- **Версионирование**: semver не меняется (фича админ-UI, не ломающая).

## Verification (runtime — владелец)

После реализации, **владелец** запускает на dev-стенде (`nsa-i9`):

1. **Smoke (sync, малый объём)**:
   - Создать 5 ERROR-процессов через `KaraokeProcess`.
   - В `ProcessesTable` отобрать фильтром по status=ERROR.
   - Bulk-edit → status=WAITING. Проверить: 5 строк в БД имеют status=WAITING;
     5 audit-записей с `action=bulk_update_field`, `batch_id` одинаковый.
   - Bulk-delete. Проверить: 5 строк удалены из БД; audit каскадно удалён.

2. **Smoke (async, большой объём)**:
   - Создать 1500 ERROR-процессов (массовая вставка через SQL).
   - Bulk-delete. Проверить: UI показывает прогресс, финальный отчёт
     `succeeded=1500, failed=0`.

3. **Edge cases**:
   - Параллельная правка: открыть 2 вкладки, в одной bulk-edit, в другой
     single-edit на один и тот же id. Проверить отчёт «конфликт версии».
   - Изменение фильтра во время операции — операция идёт по snapshot.

4. **CI**: 7/7 pre-commit на ветке `319-process-bulk-actions-v2`.

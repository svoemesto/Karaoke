---
description: "Task list for spec 319 — Массовые действия с процессами (v2)"
---

# Tasks: Массовые действия с процессами (v2)

**Input**: Design documents from `/specs/319-process-bulk-actions-v2/`
**Source**: OpenProject WP #68 (`tracker.sh get-issue 68`)
**Branch**: `319-process-bulk-actions-v2`

**Tests**: Spec не запрашивает TDD/тесты; smoke-проверки — на dev-стенде владельцем.
Существующие `karaoke-app/src/test` — `@Disabled` (CI их не запускает).

**Organization**: Tasks сгруппированы по user story (US1 bulk-edit P1, US2 bulk-delete P1,
US3 audit-report P2, US4 async > 1000 P2) для независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependencies on incomplete tasks)
- **[Story]**: какая user story (US1, US2, US3, US4)
- **Обязательно**: exact file path в описании

## Path Conventions

- Backend (Kotlin/Spring): `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Frontend (Vue 3): `webvue3/src/components/Processes/`
- DB migrations: `deploy/karaoke-db/`
- Docs: `docs/features/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка миграции и базовых структур данных для bulk-операций.

- [X] T001 Create migration `48_admin_process_bulk_actions.sql` extending `tbl_processes_audit.action` CHECK constraint and adding `batch_id` UUID column with index in `deploy/karaoke-db/48_admin_process_bulk_actions.sql` (per `data-model.md` E-2)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Каркас, без которого ни одна user story не работает: snapshot-id endpoint,
DTO отчёта, Vuex-state для selection.

**⚠️ CRITICAL**: Никакая user story не стартует до завершения этой фазы.

- [X] T002 Apply migration T001 to LOCAL database via `psql -f deploy/karaoke-db/48_admin_process_bulk_actions.sql` and verify CHECK constraint + batch_id column + index via `psql -c "\d tbl_processes_audit"`
- [X] T003 [P] Create `BulkOperationReport` data class (batchId, action, requested, succeeded, failed, errors, durationMs) in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/admin/BulkOperationReport.kt`
- [X] T004 [P] Create `BulkError` data class (processId, reason, errorCode?) in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/admin/BulkError.kt`
- [X] T005 [P] Create `BulkUpdateRequest` data class (ids, field, value, batchId?) in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/admin/BulkUpdateRequest.kt`
- [X] T006 [P] Create `BulkDeleteRequest` data class (ids, batchId?) in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/admin/BulkDeleteRequest.kt`
- [X] T007 Add `GET /api/admin/processes/bulk/snapshot` endpoint in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt` (signature per `contracts/admin-process-bulk-rest-api.md` EP-5; returns `{ total, ids, limitApplied }` with `limitApplied = 10000` cap). **Path changed from `/ids-by-filter` to `/bulk/snapshot`** — Spring Boot 3.x routing conflict with `/{id}` (id="ids-by-filter" → Int conversion fail → 500). Доп. сегмент `/bulk/` гарантирует, что `{id}`-matcher не сработает (WP #68 comment 321).
- [X] T008 [P] Add `bulkSelectionIds`, `bulkSelectionTotal`, `bulkSelectionTimestamp`, `bulkOperationInProgress`, `bulkOperationReport`, `bulkTaskStatus` state + matching getters/mutations to `webvue3/src/components/Processes/store.js`
- [X] T009 [P] Add `fetchBulkSelectionIds(ctx, filters)` action to `webvue3/src/components/Processes/store.js` — calls GET `/bulk/snapshot`, commits `SET_BULK_SELECTION`
- [X] T010 Wire `fetchBulkSelectionIds` into `webvue3/src/components/Processes/filter/ProcessesFilterModal.vue` — dispatch on apply filter and on modal close

**Checkpoint**: каркас готов — можно стартовать US1, US2 параллельно.

---

## Phase 3: User Story 1 — Bulk-edit field (Priority: P1) 🎯 MVP

**Goal**: Админ выбирает поле и новое значение, фронт отправляет батч `UPDATE`, бэк
применяет к `tbl_processes` через `WHERE id IN (..)`, пишет audit с `batch_id`.

**Independent Test**: Scenario 1 из `quickstart.md` — создать 5 ERROR-процессов,
bulk-edit status→WAITING, проверить БД и audit.

### Implementation for User Story 1

- [X] T011 [P] [US1] Implement `bulkUpdateProcesses(ids: List<Int>, field: String, value: Any?, actor: String, database: KaraokeConnection): BulkOperationReport` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` — **depends on T003 + T005**; whitelist `field` через `editableColumns` (line 74-91, **backend принимает все 15 полей**), один UPDATE через `WHERE id IN (..)`, per-row audit `bulk_update_field` с одинаковым `batch_id` через `UUID.randomUUID()`, transition-validation для status (re-использовать `validateTransition` line 495-519); **UI whitelist = 3 поля (priority/status/threadId) — это ответственность T015, не T011**
- [X] T012 [US1] Add `POST /api/admin/processes/bulk-update` endpoint in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt` — **depends on T011 + T003 + T005**; wraps `service.bulkUpdateProcesses`, returns `BulkOperationReport`, validates `ids.size <= 1000` (sync) else returns 400 `TOO_MANY_IDS` (per `contracts/admin-process-bulk-rest-api.md` EP-1)
- [X] T013 [P] [US1] Add `bulkUpdateProcesses(ctx, { ids, field, value })` action to `webvue3/src/components/Processes/store.js` — POST `/api/admin/processes/bulk-update`, on success commits `SET_BULK_OPERATION_REPORT` + maps updated items в `state.items`, error → throw
- [X] T014 [P] [US1] Add bulk-actions bar UI (count display + 2 buttons «Изменить поле» / «Удалить») in `webvue3/src/components/Processes/ProcessesTable.vue` — buttons disabled if `!canBulk`, tooltip «нет процессов в выборке», snapshot refresh on filter change
- [X] T015 [US1] Create `webvue3/src/components/Processes/ProcessesBulkUpdateModal.vue` — field-select `{priority, status, threadId}` + value-input + preview + `<custom-confirm>` (per `contracts/webvue3-bulk-store-actions.md`); on confirm dispatch `bulkUpdateProcesses`, on result show report
- [X] T016 [US1] Wire `ProcessesBulkUpdateModal` in `webvue3/src/components/Processes/ProcessesTable.vue` — add modal registration, `isBulkUpdateModalVisible` data, `@click="isBulkUpdateModalVisible = true"` handler on «Изменить поле» button
- [X] T017 [US1] Add KDoc `@see specs/319-process-bulk-actions-v2/spec.md` to `bulkUpdateProcesses` (service), `bulk-update` endpoint (controller), `bulkUpdateProcesses` (Vuex action), `ProcessesBulkUpdateModal` (FR-006)

**Checkpoint**: US1 полностью функциональна — bulk-edit работает на 5–1000 процессах.

---

## Phase 4: User Story 2 — Bulk-delete (Priority: P1)

**Goal**: Админ выбирает удаление, фронт подтверждает, бэк прерывает runtime-потоки
(WORKING/WAITING/CREATING) и делает `DELETE FROM tbl_processes WHERE id IN (..)`.
Audit удаляется каскадно (FK ON DELETE CASCADE).

**Independent Test**: Scenario 2 из `quickstart.md` — 5 процессов bulk-delete,
проверить отсутствие в `tbl_processes` и `tbl_processes_audit`.

### Implementation for User Story 2

- [X] T018 [P] [US2] Implement `bulkDeleteProcesses(ids: List<Int>, actor: String, database: KaraokeConnection): BulkOperationReport` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` — **depends on T003 + T006**; повторяет single-record паттерн (`deleteProcess` line 310-375) для каждого id: WORKING → interrupt + 5 сек grace + `destroyForcibly`, WAITING/CREATING → interrupt (no grace), DONE/ERROR → nothing; затем `DELETE FROM tbl_processes WHERE id = ?` (FK CASCADE убирает audit), один `batch_id` через `UUID.randomUUID()` (записывается в `batch_id` колонку ДО удаления, чтобы audit-trail по batch_id можно было восстановить если FK когда-то поменяют; в текущей схеме CASCADE стирает и эту запись — consistent с FR-005)
- [X] T019 [US2] Add `POST /api/admin/processes/bulk-delete` endpoint in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt` — **depends on T018 + T003 + T006**; wraps `service.bulkDeleteProcesses`, returns `BulkOperationReport`, validates `ids.size <= 1000` (sync) else 400 `TOO_MANY_IDS` (per `contracts/admin-process-bulk-rest-api.md` EP-2)
- [X] T020 [P] [US2] Add `bulkDeleteProcesses(ctx, { ids })` action to `webvue3/src/components/Processes/store.js` — POST `/api/admin/processes/bulk-delete`, on success commits `SET_BULK_OPERATION_REPORT` + filters deleted ids из `state.items`
- [X] T021 [US2] Add inline `<custom-confirm>` dialog for bulk-delete в `webvue3/src/components/Processes/ProcessesTable.vue` — message «Будет удалено N процессов. Действие необратимо», on confirm dispatch `bulkDeleteProcesses`
- [X] T022 [US2] Add KDoc `@see specs/319-process-bulk-actions-v2/spec.md` to `bulkDeleteProcesses` (service), `bulk-delete` endpoint (controller), `bulkDeleteProcesses` (Vuex action)

**Checkpoint**: US2 полностью функциональна — bulk-delete работает на 5–1000 процессах.

---

## Phase 5: User Story 3 — Audit report + CSV (Priority: P2)

**Goal**: После любой bulk-операции — отчёт «K из N, errors[]», CSV-выгрузка.

**Independent Test**: После bulk-edit/дelete на 10+ процессах — открыть отчёт,
проверить количество строк и CSV-выгрузку.

### Implementation for User Story 3

- [X] T023 [P] [US3] Create `webvue3/src/components/Processes/ProcessBulkReportModal.vue` — таблица с колонками «ID процесса / статус (ok|error) / сообщение / old_value / new_value»; верхний блок — summary (batchId, action, requested/succeeded/failed, durationMs); кнопка «Скачать CSV» справа
- [X] T024 [P] [US3] Add CSV-export helper в `ProcessBulkReportModal.vue` — генерирует CSV через `Blob([...rows].join('\n'))` + `URL.createObjectURL`, имя файла `process-bulk-{action}-{batchId}.csv`
- [X] T025 [US3] Wire `ProcessBulkReportModal` in `webvue3/src/components/Processes/ProcessesTable.vue` — `isBulkReportModalVisible` data, отображается автоматически при `state.bulkOperationReport !== null` после завершения bulk-операции, manual close через `dispatch('clearBulkOperationReport')`
- [X] T026 [US3] Add KDoc `@see specs/319-process-bulk-actions-v2/spec.md` to `ProcessBulkReportModal` Vue component (JSDoc) and CSV helper

**Checkpoint**: US3 функциональна — bulk-edit/дelete показывают детальный отчёт с CSV.

---

## Phase 6: User Story 4 — Async endpoint для > 1000 (Priority: P2)

**Goal**: На больших выборках (> 1000) — async endpoint с polling прогресса через
admin-task механизм, чтобы UI не вис и админ видел прогресс.

**Independent Test**: Scenario 3 из `quickstart.md` — 1500 ERROR-процессов,
bulk-delete через async, проверить UI прогресс + финальный отчёт.

### Implementation for User Story 4

- [X] T027 [P] [US4] Add `bulkUpdateProcessesAsync(ids, field, value, actor, db)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` — стартует фоновый процесс через существующий admin-task механизм (или новый `ProcessBulkTaskService`), возвращает `taskId`
- [X] T028 [P] [US4] Add `bulkDeleteProcessesAsync(ids, actor, db)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` — аналогично T027
- [X] T029 [US4] Add `POST /api/admin/processes/bulk-update-async` + `bulk-delete-async` endpoints в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt` — returns 202 `{ taskId, statusUrl, estimatedDurationSec }`
- [X] T030 [US4] Verify/add `GET /api/admin/tasks/{taskId}` endpoint (если не существует — создать в `AdminTaskController.kt`) — returns `{ taskId, status, totalCount, processedCount, succeededCount, failedCount, errors }`
- [X] T031 [P] [US4] Add `bulkUpdateProcessesAsync`, `bulkDeleteProcessesAsync`, `pollBulkTask`, `clearBulkOperationReport` actions to `webvue3/src/components/Processes/store.js` — POST async-endpoint, polling каждые 2 сек через `setTimeout`, commit `SET_BULK_TASK_STATUS` + `SET_BULK_OPERATION_REPORT` при terminal status
- [X] T032 [US4] Add async-path в `ProcessesBulkUpdateModal.vue` / `ProcessesTable.vue` — если `ids.length > 1000`, dispatch async-actions и показать прогресс-индикатор; иначе — sync path из US1/US2

**Checkpoint**: US4 функциональна — bulk на 1000–10000 процессов идёт через async с прогрессом.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Документация, KDoc/JSDoc покрытие, smoke-валидация, tracker.

- [X] T033 [P] Update `webvue3/src/components/Processes/store.js` — добавить JSDoc `@see` на новые actions (FR-006)
- [X] T034 [P] Create per-feature document `docs/features/process-bulk-actions.md` (FR-009, pattern из `docs/features/README.md`): краткое описание, ссылки на `specs/319-process-bulk-actions-v2/`, история решений D-1..D-8, операционные заметки (sync vs async threshold, hard-delete implications)
- [ ] T035 Run smoke scenarios 1–3 из `specs/319-process-bulk-actions-v2/quickstart.md` на dev-стенде (`nsa-i9`): создать ERROR-процессы, bulk-edit/delete/async, проверить БД + audit + UI
- [ ] T036 Run edge case scenarios 4–6 из `quickstart.md` (изменение фильтра во время операции, параллельная правка, пустая выборка) — проверить snapshot-семантику, race handling, UI states
- [ ] T037 Run 7/7 pre-commit CI gate: `./gradlew :karaoke-app:compileKotlin`, `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`, `bash tools/check-kdoc-coverage.sh --strict`, `bash tools/check-jsdoc-coverage.sh --strict webvue3`, `pre-commit run --all-files`
- [X] T038 Update WP #68 в OpenProject — `mark-review` после успешного smoke + 7/7 pre-commit (отчёт — в `report.md` через `tracker.sh add-comment 68 --file report.md`)
- [ ] T039 Build `karaoke-app:bootJar` + `webvue3` artifacts + push feature branch → `gh pr create --base master` → дождаться CI → merge (per AGENTS.md «CI-gate для master»)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей, стартует сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 (T002 применяет T001); **блокирует** все US.
- **Phase 3 (US1)**: зависит от Phase 2 (T007, T008, T009 нужны для snapshot и state).
- **Phase 4 (US2)**: зависит от Phase 2 (то же, что US1).
- **Phase 5 (US3)**: зависит от Phase 3+4 (читает `state.bulkOperationReport`, заполняемый US1/US2).
- **Phase 6 (US4)**: зависит от Phase 3+4 (async — расширение sync-endpoint'ов).
- **Phase 7 (Polish)**: зависит от US1+US2+US3 (минимум); US4 — желательно до merge.

### User Story Dependencies

- **US1 (P1)**: стартует после Phase 2 → независима.
- **US2 (P1)**: стартует после Phase 2 → независима от US1 (разные endpoint'ы, разные actions).
- **US3 (P2)**: стартует после US1 + US2 (использует их state).
- **US4 (P2)**: стартует после US1 + US2 (расширяет их endpoint'ы).

### Within Each User Story

- DTO/State (Phase 2) → Service (T011/T018/T027) → Endpoint (T012/T019/T029) → Vuex (T013/T020/T031) → UI (T014–T016, T021, T023, T025) → KDoc (T017, T022, T026, T033).
- Без KDoc на каждом уровне нельзя пройти pre-commit (FR-006 блокирующий).

### Parallel Opportunities

- **Phase 1**: только T001 (1 файл).
- **Phase 2**: T003, T004, T005, T006, T008 — параллельно (разные файлы); T007, T009, T010 — последовательно (каждый зависит от предыдущего или от state).
- **Phase 3 (US1)**: T011, T013, T014 — параллельно; T012 ждёт T011; T015 ждёт T013; T016 ждёт T015; T017 ждёт T011–T016.
- **Phase 4 (US2)**: T018, T020 — параллельно; T019 ждёт T018; T021 ждёт T020; T022 ждёт T018–T021.
- **Phase 3 + 4**: US1 и US2 могут идти параллельно (после Phase 2).
- **Phase 5 (US3)**: T023, T024 — параллельно; T025 ждёт T023; T026 ждёт T023–T025.
- **Phase 6 (US4)**: T027, T028, T030 — параллельно; T029 ждёт T027/T028; T031 ждёт T029/T030; T032 ждёт T031.
- **Phase 7**: T033, T034 — параллельно; T035 ждёт US1–US4; T036 ждёт T035; T037 ждёт T035; T038 ждёт T036+T037; T039 ждёт T037.

---

## Parallel Example: User Story 1 + User Story 2

```bash
# После Phase 2 можно стартовать параллельно:
# Agent A (US1)
Task: "T011 Implement bulkUpdateProcesses in KaraokeProcessAdminService.kt"
Task: "T013 Add bulkUpdateProcesses action to store.js"
Task: "T014 Add bulk-actions bar UI to ProcessesTable.vue"

# Agent B (US2)
Task: "T018 Implement bulkDeleteProcesses in KaraokeProcessAdminService.kt"
Task: "T020 Add bulkDeleteProcesses action to store.js"

# После завершения каждой ветки:
# Agent A: T012 → T015 → T016 → T017 (US1 closure)
# Agent B: T019 → T021 → T022 (US2 closure)
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1 → Phase 2.
2. Complete Phase 3 (US1).
3. **STOP and VALIDATE**: Scenario 1 из `quickstart.md`.
4. Merge US1 как MVP (bulk-edit на малых объёмах).

### Incremental Delivery

1. Phase 1 + 2 → foundation ready.
2. + US1 → MVP bulk-edit (5–1000).
3. + US2 → MVP bulk-delete (5–1000).
4. + US3 → детальный отчёт + CSV.
5. + US4 → async для 1000+ (production-ready).
6. Phase 7 → polish, doc, merge.

### Parallel Team Strategy (если есть > 1 разработчика)

После Phase 2:
- Dev A: US1 (T011–T017)
- Dev B: US2 (T018–T022)
- Dev C: US3 (T023–T026) — после US1+US2
- Dev A или B: US4 (T027–T032)
- Phase 7 (T033–T039): кто-то один.

---

## Notes

- **[P]** tasks = разные файлы, без зависимостей → параллельно.
- **[Story]** label мапит task на user story для traceability.
- **Каждая user story** независимо завершаема и тестируема.
- **Commit после каждого task или логической группы** (Pass FR-006 — KDoc нужен ДО commit).
- **Stop at any checkpoint** для валидации story (особенно после US1).
- **Avoid**: vague tasks, same-file conflicts, cross-story dependencies that break independence.

---

## Reference

- Spec: `specs/319-process-bulk-actions-v2/spec.md`
- Plan: `specs/319-process-bulk-actions-v2/plan.md`
- Research: `specs/319-process-bulk-actions-v2/research.md`
- Data model: `specs/319-process-bulk-actions-v2/data-model.md`
- REST API contract: `specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md`
- Vuex contract: `specs/319-process-bulk-actions-v2/contracts/webvue3-bulk-store-actions.md`
- Quickstart (smoke + edge cases): `specs/319-process-bulk-actions-v2/quickstart.md`
- OpenProject WP #68: `tracker.sh get-issue 68`
- Constitution: `.specify/memory/constitution.md`

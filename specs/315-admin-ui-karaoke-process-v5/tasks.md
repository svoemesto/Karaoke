# Tasks: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Input**: Design documents from `/specs/315-admin-ui-karaoke-process-v5/`
- plan.md (APPROVE Кириллом #155)
- spec.md (APPROVE Кириллом #153, 28 FR, 5 US, 9 SC)
- research.md (20 решений R-001..R-020)
- data-model.md (3 entities + race protection + R-019)
- contracts/admin-process-rest-api.md (6 endpoints)
- quickstart.md (10 validation scenarios + governance + MVP-checkpoint)

**Tests**: НЕ включены (CI тестов нет, все `@Disabled` per Karaoke/AGENTS.md). Verify через quickstart.md manual scenarios владельцем.

**🚦 MVP-CHECKPOINT (R-016, SC-007, Lesson #15 iter #3 — NON-NEGOTIABLE для iter #5)**: после Phase 3 US1 (задачи T011..T017) → STOP → владелец rebuild + перезапуск + smoke-test 6 endpoints + visual verify Scenario 1, 9 → только после ОК продолжение US2..US5 + Polish. Iter #4 был исключением (batch mode по решению владельца) — iter #5 возвращается к MVP-checkpoint.

**Governance (Lessons #11-#14 iter #3, NON-NEGOTIABLE)**:
- Агенты НЕ перезапускают контейнер `karaoke-app` (governance Karaoke/AGENTS.md).
- Smoke-test реальной БД только владелец.
- Boss self-verify через `psql -c information_schema.columns` ДО отправки задач Алине (Lesson #16 iter #4, R-018).

**Реальная схема `tbl_processes`** (зафиксирована ДО старта, 28 колонок, в `notes/real-schema-tbl_processes.txt`):
- **НЕТ** `created_at`/`updated_at`/`started_at`/`ended_at` (урок #11 iter #3 — SQL bug).
- **ЕСТЬ** `last_update`/`process_start`/`process_end`/`recordhash`/`process_chain_id`/`process_deleted_at` (миграция 47).
- `id`/`songId`/`threadId` = Int (НЕ Long).

**17 уроков iter #1-#4 применены** (8 + 2 + 5 + 2 = 17 lessons; Lesson #11 iter #3 + Lesson #16 iter #4 process lesson = 2 дополнительных). Всего 17 уроков покрыты в спеке/плане. Ни одна ошибка из 4 прогонов не повторится.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies).
- **[Story]**: US1, US2, US3, US4, US5.
- File paths exact.

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/` (в корне пакета, **НЕ** `beans/`/`workers/`).
- Frontend: `webvue3/src/components/Processes/`.
- Migrations: `deploy/karaoke-db/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify миграции 47+48, ветка, reference components, реальная схема (Lesson #16).

- [ ] T001 [P] Verify migration 47 applied: `docker exec karaoke-db psql -U postgres -d karaoke -c "\d tbl_processes" | grep -E 'process_chain_id|process_deleted_at'` (должны быть 2 строки).
- [ ] T002 [P] Verify `git checkout 315-admin-ui-karaoke-process-v5` (уже на ветке per `tools/specify-bootstrap.sh`).
- [ ] T003 [P] Read reference components: `webvue3/src/components/Songs/edit/SongEdit.vue` (58 вхождений `label-and-input`), `webvue3/src/components/Songs/edit/SongEditModal.vue`, `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue`, `webvue3/src/components/SiteUsers/edit/SiteUserEditModal.vue`, `webvue3/src/components/Pictures/edit/PictureEdit.vue`, `webvue3/src/components/Pictures/edit/PictureEditModal.vue` (Lesson #1).
- [ ] T004 [P] Read real mechanism reference: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (`threadsMap: MutableMap<Int, KaraokeProcessThread?>` стр. ~607, class `KaraokeProcessThread` стр. ~70, WORKING стр. ~89, `runCatching { thread.interrupt() }` стр. ~1340); `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` (`override fun save()` стр. ~280, `ps.executeUpdate()` стр. ~361).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend foundation (KaraokeProcess fields + DTO + admin service + controller) + **R-019 pickup-фильтр Worker** (новое iter #5, Кирилл iter #5 Р-3). БЕЗ ЭТОГО US1..US5 не работают.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T005 [P] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` (в корне пакета, НЕ `beans/`): добавить `@KaraokeDbTableField(name = "process_chain_id") var processChainId: Long? = null` и `@KaraokeDbTableField(name = "process_deleted_at") var processDeletedAt: Timestamp? = null` (для load и DTO). **save() их НЕ пишет** (FR-019 race protection, Lesson #5 iter #1). Реальные имена колонок: `process_chain_id`, `process_deleted_at`, `last_update`, `process_start`, `process_end` (НЕ `updated_at`/`started_at`/`ended_at` — урок #11 iter #3).
- [ ] T006 Create `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/admin/KaraokeProcessAdminDTO.kt`: DTO с **20 основными полями** из 28 реальных (id, name, status, type, command, args, envs, description, songId, order, priority, prioritet, withoutControl, threadId, processChainId, processDeletedAt, updatedAt=last_update, startedAt=process_start, endedAt=process_end, recordhash) + `ProcessAuditDTO` (id, processId, actor, action, oldValue, newValue, createdAt) + `ProcessListResult` (total, items). actor VARCHAR(64) (миграция 47, НЕ 255). **НЕ использовать** имена колонок `created_at`/`updated_at`/`started_at`/`ended_at` — их в схеме НЕТ; camelCase-алиасы DTO (`updatedAt`/`startedAt`/`endedAt`) маппятся на реальные `last_update`/`process_start`/`process_end` через `AS updated_at`/`AS started_at`/`AS ended_at` в SQL.
- [ ] T007 Create `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` с `@DependsOn("karaokeAppService")` (Lesson #4 iter #1, plan.md стр. 99 — впервые введено iter #4, 0 вхождений `@DependsOn` в master). Методы:
  - `loadProcesses(database, filters: ProcessFilters): ProcessListResult` — FR-001/002/004/005/006 (topLevelOnly, parentId для lazy load, name `ILIKE '%' || ? || '%'` per R-007).
  - `loadProcess(id, database): KaraokeProcessAdminDTO?`
  - `editProcess(id, changes, actor, database): KaraokeProcessAdminDTO` — FR-008/016/017 (status transitions validation, **НЕ** записывает `process_deleted_at`/`process_chain_id` per FR-019 race protection, **НЕ** через `KaraokeProcess.save()`).
  - `deleteProcess(id, actor, database): Unit` — FR-010/011/012/013 (WAITING cancel + soft-delete, WORKING interrupt + 5 сек grace (R-008) + destroyForcibly, cascade OFF).
  - `retryProcess(id, actor, database): KaraokeProcessAdminDTO` — FR-014 (только ERROR + `process_deleted_at IS NULL`, T048 iter #3) + FR-015 (audit RETRY, **отдельный targeted UPDATE** — НЕ helper `setWorkingToWaiting`).
  - `loadAudit(processId, days, database): List<ProcessAuditDTO>` — FR-016/018 (filter: `created_at > NOW() - make_interval(days => ?)`, **⚠️ НЕ** `INTERVAL '? days'` — урок RC-2 iter #3, используем `make_interval(days => ?)` + `ps.setInt(idx, days)`).
  - **`@Scheduled(fixedDelay = 24*60*60*1000, initialDelay = 60*1000) cleanupOldAudit()`** — FR-018 retention cron: `DELETE FROM tbl_processes_audit WHERE created_at < NOW() - INTERVAL '30 days'` (хардкод `'30 days'`, политика фиксирована).
- [ ] T008 Create `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt` с `@DependsOn("karaokeAppService")` (Lesson #4, **на service И controller**). Endpoints (6) per `contracts/admin-process-rest-api.md`:
  - `GET /api/admin/processes` → service.loadProcesses
  - `GET /api/admin/processes/{id}` → service.loadProcess
  - `POST /api/admin/processes/{id}/edit` → service.editProcess
  - `POST /api/admin/processes/{id}/delete` → service.deleteProcess
  - `POST /api/admin/processes/{id}/retry` → service.retryProcess
  - `GET /api/admin/processes/{id}/audit?days=30` → service.loadAudit
  - Pattern: `StemJobsAdminController` (Lesson #3 iter #1). `permitAll()` автоматически по `SecurityConfig.kt:43-45` (verified iter #3/#4/#5 Кириллом).
- [ ] T009 [P] **R-019 (новое iter #5)**: Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (в корне пакета, НЕ `workers/`): pickup-логика добавить `WHERE process_deleted_at IS NULL` (Кирилл iter #5 Р-3, 0 вхождений grep на момент iter #5). Verify: `grep -nE "process_deleted_at" KaraokeProcessWorker.kt` — должно быть ≥1 вхождение в pickup-query.
- [ ] T010 [P] Verify Constitution Check Principle III: `git grep -n "tbl_processes" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` → должно быть **ПУСТО** (tbl_processes вне sync, plan.md стр. 50). Если НЕ пусто — остановиться и доложить владельцу.

**Checkpoint**: foundation ready — backend компилируется, endpoints доступны (после deploy владельцем). User story implementation can begin.

---

## Phase 3: User Story 1 - Просмотр процессов с фильтрацией и parent-child визуализацией (Priority: P1) 🎯 MVP

**Goal**: Администратор открывает `/admin/processes`, видит top-level список (только head, `process_chain_id IS NULL`), фильтрует по статусу/типу/thread_id/process_chain_id/process_deleted_at/name, разворачивает head → видит tail-дети (lazy load).

**Independent Test**: Открыть `/admin/processes`, убедиться top-level загружается (≤2 сек, FR-004); кликнуть expand на head → дети подгружаются (≤1 сек); применить фильтры → работают. Quickstart Scenarios 1, 9.

**🚦 MVP-CHECKPOINT после T017**: STOP → владелец rebuild + перезапуск + smoke-test 6 endpoints + visual verify Scenario 1, 9 → только после ОК продолжение US2..US5 + Polish.

### Implementation for User Story 1

- [ ] T011 [P] [US1] Modify `webvue3/src/components/Processes/filter/ProcessesFilterModal.vue`: header «Фильтр процессов» (FR-003, НЕ «Фильтр для песен»); добавить новые поля фильтра (chainId, includeDeleted, name substring matcher `ILIKE '%x%'` per R-007, FR-002).
- [ ] T012 [P] [US1] Modify `webvue3/src/components/Processes/filter/store.js`: добавить state для новых полей фильтра (threadId, chainId, includeDeleted, name). Persistence через `setWebvueProp`/`getWebvueProp`.
- [ ] T013 [P] [US1] Modify `webvue3/src/components/Processes/store.js`: добавить action `loadProcesses(filters)` который вызывает `GET /api/admin/processes` с фильтрами из `filter/store.js`. Возвращает `{ total, items }`. **⚠️ R-006 (Lesson #10 iter #2 / RC iter #3)**: `queryParams.topLevelOnly = !params.filterChainId` (не безусловно `true`).
- [ ] T014 [US1] Modify `webvue3/src/components/Processes/store.js`: добавить action `loadChildren(parentId)` который вызывает `GET /api/admin/processes?topLevelOnly=false&parentId={parentId}` для lazy load (FR-004 + R-006, ≤1 сек).
- [ ] T015 [US1] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: parent-child визуализация (FR-004 + R-006 — top-level + lazy load):
  - По умолчанию отображает только head-процессы (`topLevelOnly=true`).
  - Head с детьми показывает expand-кнопку (▶/▼).
  - При expand → вызов `loadChildren(parentId)` → вложенные строки под head.
  - Legacy `process_chain_id=null` отображаются как самостоятельные head-процессы без дочерних (FR-005 + R-005).
  - Broken references (`process_chain_id` указывает на несуществующий parent) → UI badge «⚠️ parent не найден» (FR-006, reachable через filter chainId + parent удалён).
- [ ] T016 [US1] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: визуальная индикация soft-deleted процессов (FR-002 `includeDeleted=true`): зачёркнутый текст + иконка 🗑️.
- [ ] T017 [US1] Boss diff (agent only — агенты НЕ запускают smoke-test, governance): `git add -N . && git diff > /home/nsa/Agents/Boss/reviews/315-admin-ui-karaoke-process-v5-implement.diff` + sha256. **🚦 STOP — MVP-CHECKPOINT**. Владелец делает rebuild + перезапуск + smoke-test 6 endpoints через curl + visual verify Scenario 1, 9. Если ОК → продолжить US2..US5 + Polish. Если REJECT → откат к baseline Phase 2, фикс, повтор.

**MVP-CHECKPOINT (R-016, SC-007, Lesson #15 — NON-NEGOTIABLE для iter #5)**:
1. ✅ Агент завершил T011..T016 + 5-step verification зелёная.
2. ✅ Агент сделал diff (T017).
3. ✅ Агент НЕ запускает smoke-test (governance, Lessons #11-#14 iter #3).
4. **Владелец делает**: rebuild + перезапуск контейнера + smoke-test 6 endpoints через curl + visual verify Scenario 1, 9.
5. **Если ОК** → Алина продолжает US2..US5 + Polish. **Если REJECT** → Алина откатывает (working tree clean к baseline Phase 2), фикс, повтор.

Без MVP-checkpoint → batch mode хрупкий (iter #2+#3 накопили 6 regressions подряд).

---

## Phase 4: User Story 2 - Редактирование процесса (Priority: P2)

**Goal**: Администратор нажимает кнопку «Редактировать» в Actions-колонке → открывается `ProcessEditModal` → меняет поля → сохраняет → таблица обновляется. **ТОЛЬКО через кнопку** (Lesson #9 iter #2).

**Independent Test**: Выбрать процесс → нажать «Редактировать» → модалка открывается → изменить name → сохранить → таблица обновляется. Клик на ячейку `name` НЕ открывает модалку. Quickstart Scenarios 2, 3.

### Implementation for User Story 2

- [ ] T018 [P] [US2] Create `webvue3/src/components/Processes/edit/ProcessEditModal.vue`: тонкая обёртка (backdrop + header «Редактирование процесса» + `<ProcessEdit/>` + close button), **ровно как `SongEditModal.vue`** (convention match, FR-009, Lesson #1).
- [ ] T019 [P] [US2] Create `webvue3/src/components/Processes/edit/ProcessEdit.vue`: форма с **ВСЕМИ** редактируемыми полями (FR-008: name, status, order, priority, command, args, envs, description, songId, type, **process_start** (datetime Local), **process_end** (datetime Local), prioritet, withoutControl, threadId). Использовать `label-and-input`/`custom-confirm`/`notChanged`/`save` как в `SongEdit.vue` (FR-009, Lesson #1). **⚠️ Урок RC-3 iter #3**: datetime сравниваются/сохраняются через Local-поля (`startedAtLocal`/`endedAtLocal`), не raw.
- [ ] T020 [P] [US2] Modify `webvue3/src/components/Processes/store.js`: добавить action `editProcess(id, changes)` который вызывает `POST /api/admin/processes/{id}/edit`. После успеха — обновить item в `state.items`.
- [ ] T021 [US2] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: добавить кнопку «Редактировать» в Actions-колонку → вызывает `openEditModal(id)`. **УДАЛИТЬ** `@click.left="editProcess(data.item.id)"` из `<template #cell(name)>` (Lesson #9 iter #2 + Кирилл iter #3 grep-контроль строки 65-73; после отката iter #4 присутствует в дереве).
- [ ] T022 [US2] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: добавить колонку Actions с кнопкой «Редактировать» если её нет.
- [ ] T023 [US2] Backend: в `KaraokeProcessAdminService.editProcess()` добавить status transitions validation (FR-017): CREATING→WAITING/WORKING/ERROR, WAITING→WORKING, WORKING→DONE/ERROR, DONE→(terminal), ERROR→WAITING (только через retry FR-014). При невалидном transition → throw `IllegalArgumentException` с кодом `INVALID_STATUS_TRANSITION` (Error Format секция `contracts/admin-process-rest-api.md`, стр. 167-175), frontend показывает ошибку UI (Quickstart Scenario 3).
- [ ] T024 [US2] Backend: в `KaraokeProcessAdminService.editProcess()` **НЕ** записывать `process_deleted_at` и `process_chain_id` (FR-019 race protection, Lesson #5). Использовать raw JDBC UPDATE только для редактируемых полей + `UPDATE tbl_processes SET ... WHERE id = ?` (без `deleted_at`/`chain_id`). Реальный паттерн: `db.getConnection()?.use { conn → conn.prepareStatement(...).use { ps → ps.setLong(...); ps.executeUpdate() }}` (KaraokeProcess.kt:361 reference). **НЕ** `connection.executeUpdate(...)` (нет такого API).
- [ ] T025 [US2] Backend: после успешного edit → записать в `tbl_processes_audit` (action='EDIT', oldValue + newValue JSONB diff, FR-016). **⚠️ Урок RC-4 iter #3**: `oldMap.filter { (k, v) -> newMap[k] != v }` и `newMap.filter { (k, v) -> oldMap[k] != v }` — **раздельно** (старые значения сохраняются). `audit.oldValue.status` (НЕ `.action`).

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 5: User Story 3 - Удаление процесса (Priority: P2)

**Goal**: Администратор удаляет процесс. Логика зависит от статуса. **БЕЗ cascade** (FR-013 + owner clarification iter #1).

**Independent Test**: Создать процесс WAITING → удалить → запись пропадает из механизма исполнения, soft-deleted. Cascade OFF. Quickstart Scenarios 4, 5, 10.

### Implementation for User Story 3

- [ ] T026 [P] [US3] Create `webvue3/src/components/Processes/delete/ProcessDeleteModal.vue`: confirm-модалка «Удалить процесс X? (статус: WAITING)». Действие → вызов `store.deleteProcess(id)`. Реализация: confirm через `custom-confirm` (как в `SongEdit.vue`/`SiteUserEdit.vue`) или простой BModal. **НЕ** ссылаться на `PictureDeleteModal.vue` (НЕ существует в коде).
- [ ] T027 [P] [US3] Modify `webvue3/src/components/Processes/store.js`: добавить action `deleteProcess(id)` который вызывает `POST /api/admin/processes/{id}/delete`. После успеха — удалить item из `state.items` (если не includeDeleted).
- [ ] T028 [US3] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: добавить кнопку «Удалить» в Actions-колонку → `openDeleteModal(id)`.
- [ ] T029 [US3] Backend: `KaraokeProcessAdminService.deleteProcess()` логика по статусу (FR-010..FR-013):
  - DONE/ERROR → ТОЛЬКО targeted UPDATE `process_deleted_at = NOW() WHERE id = ? AND process_deleted_at IS NULL` (FR-010).
  - WAITING/CREATING → cancel из механизма исполнения (`KaraokeProcessWorker.threadsMap`; pickup-логика фильтрует `process_deleted_at IS NULL` — R-019) + targeted UPDATE `process_deleted_at` (FR-011).
  - WORKING → `Thread.interrupt()` + grace 5 сек (FR-012 + R-008) + `destroyForcibly()` при timeout + targeted UPDATE `process_deleted_at`.
  - CREATING → cancel + targeted UPDATE.
  - **NO cascade** — дети остаются (FR-013).
  - **⚠️ Проверка**: перед удалением — `if (current.processDeletedAt != null) throw ResponseStatusException(HttpStatus.CONFLICT, "Process is already deleted: id=$id")` (Spring-стандарт для 409; класс `StateConflictException` НЕ существует в кодовой базе, grep 0).
- [ ] T030 [US3] Backend: после успешного delete → записать в `tbl_processes_audit` (action='DELETE', oldValue.status + флаги, newValue.process_deleted_at, FR-016). При WORKING timeout → `oldValue` содержит флаг timeout. **⚠️** Использовать `old_value.status` (НЕ `old_value.action` — это audit-action, не process-status).
- [ ] T031 [US3] 5-step verification + проверка cascade: агент только 5-step + grep (что deleteProcess не имеет cascade-логики для детей). **Функциональную проверку cascade делает владелец** (governance, Quickstart Scenario 10).

**Checkpoint**: User Stories 1, 2, 3 work independently.

---

## Phase 6: User Story 4 - Retry ERROR-процесса (Priority: P3)

**Goal**: Администратор нажимает «Retry» на ERROR-процессе → процесс переходит в WAITING (FR-014). **НЕ** для удалённых (FR-014 условие `process_deleted_at IS NULL`, T048 iter #3).

**Independent Test**: Создать процесс с ошибкой → ERROR → Retry → WAITING. Не retry удалённого. Quickstart Scenarios 6, 7.

### Implementation for User Story 4

- [ ] T032 [P] [US4] Modify `webvue3/src/components/Processes/store.js`: добавить action `retryProcess(id)` который вызывает `POST /api/admin/processes/{id}/retry`. После успеха — обновить status в `state.items`.
- [ ] T033 [US4] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: добавить кнопку «Retry» в Actions-колонку (видна только для status=ERROR + processDeletedAt==null, иначе скрыта/disabled, FR-014 + T048 iter #3).
- [ ] T034 [US4] Backend: `KaraokeProcessAdminService.retryProcess()` — **отдельный targeted UPDATE** (НЕ helper `setWorkingToWaiting(database)` — это WORKING→WAITING-сброс, урок Т-3 Кирилла iter #4): `UPDATE tbl_processes SET process_status = ? WHERE id = ? AND process_status = ? AND process_deleted_at IS NULL` (FR-014, T048 iter #3). Использовать `ps.setString(1, WAITING.name)`, `ps.setInt(2, id)` (id: Int — колонка integer), `ps.setString(3, ERROR.name)`. Проверка перед retry: `if (current.status != ERROR) throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot retry process in status ${current.status}")`; `if (current.processDeletedAt != null) throw ResponseStatusException(HttpStatus.CONFLICT, "Process is deleted, cannot retry: id=$id")`. Записать audit (action='RETRY', oldValue.status='ERROR', newValue.status='WAITING', FR-015).
- [ ] T035 [US4] 5-step verification + Retry ERROR → WAITING + audit запись. **Функциональную проверку Retry делает владелец** (governance, Quickstart Scenarios 6-7). Агент — только 5-step + grep (что retryProcess имеет `process_deleted_at IS NULL` условие).

**Checkpoint**: User Stories 1, 2, 3, 4 work independently.

---

## Phase 7: User Story 5 - Просмотр audit log (Priority: P3)

**Goal**: Администратор открывает audit-модалку → видит историю изменений (EDIT/RETRY/DELETE за 30 дней, retention cron).

**Independent Test**: Открыть процесс → нажать «Audit» → видит список. Quickstart Scenario 8.

### Implementation for User Story 5

- [ ] T036 [P] [US5] Create `webvue3/src/components/Processes/audit/ProcessAuditModal.vue`: read-only модалка с таблицей audit-записей (timestamp, actor, action, JSONB diff). Bootstrap-vue-next стили.
- [ ] T037 [P] [US5] Modify `webvue3/src/components/Processes/store.js`: добавить action `loadAudit(id, days=30)` который вызывает `GET /api/admin/processes/{id}/audit?days=30`.
- [ ] T038 [US5] Modify `webvue3/src/components/Processes/ProcessesTable.vue`: добавить кнопку «Audit» в Actions-колонку → `openAuditModal(id)`.
- [ ] T039 [US5] Backend: `KaraokeProcessAdminService.loadAudit()` — `SELECT id, process_id, actor, action, old_value, new_value, created_at FROM tbl_processes_audit WHERE process_id = ? AND created_at > NOW() - make_interval(days => ?)` (FR-016 + FR-018, **⚠️ НЕ** `INTERVAL ?` — урок RC-2 iter #3, используем `make_interval(days => ?)` + `ps.setInt(idx, days)`).
- [ ] T040 [US5] 5-step verification + audit-модалка показывает записи EDIT/RETRY/DELETE. **Функциональную проверку audit-модалки делает владелец** (governance, Quickstart Scenario 8). Агент — только 5-step + статические проверки (grep что loadAudit вызывается из ProcessAuditModal).

**Checkpoint**: All 5 user stories independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements affecting multiple stories + verifications + governance.

- [ ] T041 [P] Update `livedocs/INDEX.md`: добавить ссылку на feature livedoc (FR-023).
- [ ] T042 [P] Create `livedocs/features/315-admin-ui-karaoke-process-v5.md`: feature livedoc (FR-023, формат NNN-name).
- [ ] T043 [P] Backend KDoc: добавить `@see` на `specs/315-admin-ui-karaoke-process-v5/spec.md` для public API (KaraokeProcessAdminService, KaraokeProcessAdminController, KaraokeProcessAdminDTO) — Constitution Principle VI.
- [ ] T044 [P] Verify `@DependsOn("karaokeAppService")` на service И controller (Lesson #4). `git grep -n "@DependsOn" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt` — обе строки.
- [ ] T045 [P] Verify race protection FR-019: в теле UPDATE-запроса метода `save()` (`KaraokeProcess.kt`, строки ~280-361, «process_name = ?, ...») НЕ должно быть `process_deleted_at` / `process_chain_id` (проверка глазами); декларации `@KaraokeDbTableField` — допустимы (T005).
- [ ] T046 [P] Verify INTERVAL SQL fix (урок RC-2 iter #3): `grep -nE "INTERVAL '\?|INTERVAL \?" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` — должно быть **0 вхождений** обеих сломанных форм (как quoted `INTERVAL '? days'`, так и bare `INTERVAL ?`; корректные `INTERVAL '30 days'` и `make_interval(days => ?)` под паттерн не попадают).
- [ ] T047 [P] Verify cron cleanup работает: `grep -nE "@Scheduled|cleanupOldAudit" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt` — должна быть `@Scheduled` аннотация + `fun cleanupOldAudit()`.
- [ ] T048 [P] **R-019 verify** (новое iter #5, Кирилл Р-3): `grep -nE "process_deleted_at IS NULL" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` — должно быть ≥1 вхождение в pickup-query. Если 0 — pickup-логика подхватит soft-deleted процессы (регрессия FR-011).
- [ ] T049 Boss self-verify (урок #11 iter #3 + Lesson #16 iter #4): `grep -nE 'created_at|updated_at|started_at|ended_at' specs/315-admin-ui-karaoke-process-v5/spec.md`. Критерий: `created_at` — допустим ТОЛЬКО в контекстах tbl_processes_audit (FR-018 cron, Edge Cases, ProcessAuditDTO); `updated_at`/`started_at`/`ended_at` — допустимы ТОЛЬКО как запрещённые имена с пометкой «НЕ» (FR-001, Key Entities). 0 вхождений этих имён как РЕАЛЬНЫХ колонок tbl_processes в SQL. Иначе — регрессия iter #3 SQL bug.
- [ ] T050 Владелец прогоняет `quickstart.md` все 10 scenarios (runtime-прогон; governance: агент НЕ делает HTTP/visual).
- [ ] T051 **Final 5-step verification канон `brief.md:59-63` после ВСЕХ изменений Phase 8** (FR-024 «после ЛЮБОГО изменения кода»: T041-T043 меняют код/KDoc). Все 5 шагов; nsa-i9 3-step subset НЕ применим (менялись karaoke-app и webvue3).
- [ ] T052 [P] [OPT] R-020 (Кирилл iter #5 Р-5): если владелец решит — реализовать `GET /api/admin/processes?orphans=true` endpoint + UI toggle «Orphans». **Backlog по умолчанию** (владелец решает iter #5 или iter #6+).

**Checkpoint**: все gaps закрыты. Готово к финальной visual verify владельцем + speckit.analyze + speckit.converge.

---

## Phase 9: Convergence (gap-filling от speckit.analyze)

**Purpose**: Закрыть gaps между code и спекой, найденные в analyze ПОСЛЕ implement.

**Source**: speckit.analyze (1 MEDIUM gap F1: T052 orphans определение).

- [ ] T053 [P] [MEDIUM F1] T052 [OPT] orphans определение (Кирилл iter #5 Р-5): явно задокументировать что такое `orphans`. В spec.md Edge case «Broken parent reference» уже частично покрыт (FR-006 badge reachable через filter chainId + parent удалён). T052 orphans endpoint должен возвращать процессы с `process_chain_id NOT NULL AND NOT IN (SELECT id FROM tbl_processes WHERE process_deleted_at IS NULL)` (hard-missing parent vs soft-deleted parent). Решает Lesson #10 dead code в полном объёме. Владелец решает iter #5 или iter #6+.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories.
- **Phase 3 US1 (MVP)**: depends on Foundational — NO deps on other stories.
- **Phase 4 US2**: depends on Foundational — integrates with US1 (таблица + Actions) but independently testable.
- **Phase 5 US3**: depends on Foundational — integrates with US1 but independently testable.
- **Phase 6 US4**: depends on Foundational — reuses US2/US3 actions column, independently testable.
- **Phase 7 US5**: depends on Foundational — reuses US2/US3 actions column, independently testable.
- **Phase 8 Polish**: depends on all 5 user stories complete.

### MVP-CHECKPOINT after Phase 3 (R-016, SC-007, Lesson #15)

- После T017 (US1 5-step verification зелёная) → **STOP** → владелец rebuild + перезапуск + smoke-test 6 endpoints + visual verify Scenario 1, 9 → только после ОК продолжение US2..US5 + Polish.
- **Без MVP-checkpoint** → batch mode хрупкий (Lesson #15 iter #3, iter #2+#3 накопили 6 regressions подряд).

### Within Each User Story

- Backend (T006, T007, T008, T023-T025, T029-T030, T034, T039) → Frontend (T011-T014, T018-T022, T026-T028, T032-T033, T036-T038).
- Frontend components → store actions → table integration.
- Story complete before moving to next priority.

### Parallel Opportunities

- Phase 1: T002 [P], T003 [P], T004 [P].
- Phase 2: T005 [P], T006 (depends T005), T007 (depends T005+T006), T008 (depends T007), T009 [P] (independent verify), T010 [P] (independent verify).
- US1: T011 [P], T012 [P], T013 [P]; T014 (depends T013), T015 (depends T014), T016.
- US2: T018 [P], T019 [P], T020 [P]; T021 (depends T018+T019+T020), T022 (depends T021), T023 → T024 → T025 (последовательно, один файл сервиса).
- US3: T026 [P], T027 [P]; T028 (depends T026+T027), T029 → T030 (последовательно, один файл сервиса), T031.
- US4: T032 [P], T033 (depends T032), T034 (depends T032), T035.
- US5: T036 [P], T037 [P]; T038 (depends T036+T037), T039 (depends T036), T040.
- Phase 8: T041 [P], T042 [P], T043 [P], T044 [P], T045 [P], T046 [P], T047 [P], T048 [P], T049, T050, T051, T052 [P].

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories).
3. Complete Phase 3: User Story 1.
4. **STOP и VALIDATE**: **MVP-CHECKPOINT** — владелец rebuild + перезапуск + smoke-test 6 endpoints + visual verify Scenario 1, 9.
5. Deploy/demo if ready.

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. **US1 (P1) → MVP-CHECKPOINT** → владелец verify → Deploy/Demo (MVP!).
3. US2 (P2) → +Edit. Verify Scenario 2, 3.
4. US3 (P2) → +Delete. Verify Scenario 4, 5, 10.
5. US4 (P3) → +Retry. Verify Scenario 6, 7.
6. US5 (P3) → +Audit. Verify Scenario 8.
7. Phase 8 (Polish). Livedocs, verifications, R-019 verify, boss self-verify, final 5-step.
8. speckit.analyze + speckit.converge (фаза 10 цикла).

### Parallel Team Strategy

С одной командой (Алина implement, Илья оркестратор, Марк+Кирилл ревью):

1. Алина: Setup + Foundational → backend компилируется.
2. Алина: US1 → Илья boss self-verify → **MVP-CHECKPOINT** → владелец verify → Марк ревью → Алина правит.
3. Алина: US2 + US3 параллельно (разные файлы) → Марк ревью.
4. Алина: US4 + US5 параллельно → Марк ревью.
5. Илья: Phase 8 (Polish, verifications, R-019).
6. Илья: speckit.analyze + speckit.converge (фаза 10 цикла).

---

## Governance (Lessons #11-#14 iter #3, NON-NEGOTIABLE)

| Действие | Кто делает |
|---|---|
| Статическая проверка (grep, schema lookup через `psql -c information_schema.columns`) | **Агент** |
| Compile / lint / build (5-step verification канон `brief.md:59-63`) | **Агент** |
| **Перезапуск контейнера** `karaoke-app` | **Только владелец** |
| **Smoke-test через HTTP / curl** | **Только владелец** |
| **Visual verify** (открыть UI в браузере) | **Только владелец** |
| **Deploy / docker compose** | **Только владелец** |

## Notes

- **[P]** tasks = different files, no dependencies → can run in parallel.
- **[Story]** label maps task to specific user story for traceability.
- Each user story independently completable and testable.
- Verify quickstart.md scenarios between phases (владелец).
- **MVP-CHECKPOINT после US1** (R-016, SC-007, Lesson #15) — STOP → владелец visual verify → продолжение.
- **NO commits/pushes** — вся работа в working tree до явного указания владельца (FR-028).
- 5-step verification канон `brief.md:59-63` после ЛЮБОГО изменения кода (NON-NEGOTIABLE, FR-024).
- OpenProject WP #64 add-comment после каждой фазы (NON-NEGOTIABLE, FR-026).
- **Все 17 уроков iter #1-#4 применены** (Lesson #1-#8 iter #1, Lesson #9-#10 iter #2, Lesson #11-#15 iter #3, Lesson #16 iter #4) — ни одна ошибка из 4 прогонов не повторится.
- **Реальная схема** `tbl_processes` зафиксирована в `notes/real-schema-tbl_processes.txt` ДО старта (Lesson #16) — **НЕТ** колонок `created_at`/`updated_at`/`started_at`/`ended_at` (урок #11 iter #3 — SQL bug).
- **T049** (boss self-verify #11): grep `created_at|updated_at|started_at|ended_at` в spec.md — только в Context/Key Entities (указывая «НЕ существует»), НЕ в SQL.
- **T048** (R-019 verify): grep `process_deleted_at IS NULL` в Worker — должно быть ≥1 вхождение в pickup-query.
- **T046** (INTERVAL RC-2 verify): grep `INTERVAL '\?|INTERVAL \?` в service — 0 вхождений.
- ⚠️ INTERVAL `?` runtime SQL — урок RC-2 iter #3 (та же ошибка что вызывала SQL bug `created_at`). Только `make_interval(days => ?)` + `ps.setInt(idx, days)` корректен.
- KaraokeProcessQueue НЕ существует в коде (Lesson П-5 iter #4) — реальный механизм `KaraokeProcessWorker.threadsMap`. Cancel = soft-delete + R-019 pickup-фильтр.

— Илья (boss, MiniMax-M3 High)

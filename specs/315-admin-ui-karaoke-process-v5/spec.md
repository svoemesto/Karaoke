# Feature Specification: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Feature Branch**: `315-admin-ui-karaoke-process-v5`

**Created**: 2026-09-07

**Status**: Draft

**Input**: User description: "Проанализировать все 4 реализации и подготовить пятую, которая будет в себе соединять всё лучшее и учтёт все ошибки из четырёх попыток."

## Context

Это **iteration #5** evaluation-цикла по задаче «Admin UI для KaraokeProcess» — **«эталонная» реализация** на основе мета-анализа 4 предыдущих прогонов.

| Iter | Модели | Verdict | Ключевой урок |
|---|---|---|---|
| **#1** | MiniMax-M3 High все | REJECT | 8 уроков (convention match, terminology, placement, DI, race protection, owner clarifications, 5-step, subset) |
| **#2** | deepseek-v4-pro:0813 Default все | REJECT | 2 regressions: дублирование UI-входа, parent-child viz dead code |
| **#3** | MiniMax-M3 High + Qwen 3.8 Max High → Kimi K2.7 Code + GLM 5.3 Flash | CATASTROPHE (откат) | Batch mode + governance violation + SQL bug `created_at` + JSON serialization + NPE |
| **#4** | MiniMax-M3 Default + deepseek-v4-flash + GLM 5.3 Flash | SUCCESS | 3 цикла ревью + 51 задача + migration 48 + владелец visual verify |

**Миграции в master:**
- `47_admin_process_audit.sql` (commit `7ad6313c`) — `process_chain_id BIGINT NULL`, `process_deleted_at TIMESTAMP NULL`, `tbl_processes_audit`, индексы, recordhash-триггер.
- `48_chain_id_backfill.sql` (NEW iter #4) — backfill эвристика по `thread_id` + `process_command` для legacy tail'ов (13 169 tails привязаны, sample 10 — все правильно).

## Что взяли из iter #1-#4 (мета-анализ)

### Из iter #1 (MiniMax-M3 High все) — 8 уроков ПРИМЕНЕНЫ
1. **Convention match (NON-NEGOTIABLE)**: edit-modal = `XxxEditModal.vue` (тонкая обёртка) + `XxxEdit.vue` (форма с `label-and-input`/`custom-confirm`/`notChanged`/`save`), как в `SongEdit`/`SiteUserEdit`/`PictureEdit`. **Применено во всех реализациях #2-#4 успешно**.
2. **Terminology**: `thread_id` (НЕ `threadAdi`) vs `process_chain_id` (НОВОЕ из миграции 47).
3. **Placement**: backend в `karaoke-app/`, `WORKING_DATABASE = com.svoemesto.karaokeapp.WORKING_DATABASE`, pattern `StemJobsAdminController`.
4. **DI protection**: `@DependsOn("karaokeAppService")` на service И controller (Lesson #4).
5. **Race protection**: `save()` НЕ пишет `process_deleted_at`/`process_chain_id`; targeted UPDATE через raw JDBC (`getConnection() + prepareStatement + executeUpdate`).
6. **Owner clarifications**: cascade=NO, status=EDITABLE, retention=30d, start/end=EDITABLE.
7. **5-step verification** (канон `brief.md:59-63`): compileKotlin, ktlintCheck, **npm run lint**, bootJar, Vite build, Docker build.
8. **5-step subset** на nsa-i9: compileKotlin + ktlintCheck + bootJar.

### Из iter #2 (deepseek-v4-pro Default) — 2 урока ПРИМЕНЕНЫ (но **не помогли в iter #3** — batch mode съел преимущество)
9. **No duplicated UI entry points** (Lesson #9): edit-modal ТОЛЬКО через кнопку «Редактировать» в Actions-колонке. **УДАЛИТЬ** `@click.left="editProcess(data.item.id)"` из `<template #cell(name)>` (после отката iter #4 присутствует в дереве ~строки 65-73).
10. **Parent-child viz needs populated link** (Lesson #10): legacy data невозможно восстановить эвристикой по времени (`process_start = NULL`), но **можно по `thread_id`** (миграция 48). Legacy `chainId=null` отображаются как самостоятельные head-процессы без дочерних (FR-005).

### Из iter #3 (Mixed + batch mode + governance violation) — 5 уроков ПРИМЕНЕНЫ
11. **Runtime smoke-test только владелец** (Lesson #11): 5-step verification ловит синтаксис, НЕ runtime SQL. Агенты делают: grep, schema lookup через `psql -c information_schema.columns`. Владелец делает: HTTP smoke-test, browser visual verify.
12. **Governance проверяется перед runtime-проверкой** (Lesson #12): явно разделять «agent делает» / «owner делает» в каждом требовании.
13. **Frontend regressions = владелец** (Lesson #13): нужен Vite dev + browser F12 inspection — владелец делает.
14. **Governance правила приоритет над «полезной работой»** (Lesson #14): если требование подразумевает нарушение governance — стоп, доложи.
15. **MVP-checkpoint обязателен** (Lesson #15): для сложных задач с БД/frontend. Batch mode (iter #2/#3) хрупкий.

### Из iter #4 (deepseek-v4-flash + GLM Flash + batch по решению владельца) — 1 процессный урок
16. **Boss self-verify через реальную схему БД ДО отправки требований** (Process Lesson): `docker exec karaoke-db psql -c "SELECT column_name FROM information_schema.columns WHERE table_name='tbl_processes'"` и сохранение в `notes/real-schema-tbl_processes.txt`. Урок усвоен **до** старта iter #5 (см. `notes/real-schema-tbl_processes.txt`).

### Дополнительные уроки из опыта iter #4

17. **Владелец сказал «не дёргать пока сами всё не сделаете»** — это был случай iter #4. **MVP-checkpoint НЕ отменён как принцип** — он остаётся обязательным для iter #5+.

## Workflow iter #5 (NON-NEGOTIABLE)

- **MVP-checkpoint после Phase 8 US1** (R-016, SC-007, Lesson #15): implement US1 → STOP → владелец rebuild + перезапуск + smoke-test 6 endpoints + visual verify Scenario 1, 9 → только после ОК продолжение US2..US5 + Polish.
- **Boss self-verify** через реальную схему БД ДО отправки требований (Lesson #16, `notes/real-schema-tbl_processes.txt`).
- **Агенты НЕ перезапускают контейнер** `karaoke-app` (governance Karaoke/AGENTS.md, Lesson #12-#14).
- **Smoke-test реальной БД только владелец** (Lesson #11).
- **0 коммитов** до явного указания владельца (FR-028).
- **5-step verification канон `brief.md:59-63`** перед передачей владельцу (Lesson #7).

## Модели iter #5 (по решению владельца 2026-09-07)

| Агент | Модель | План | Effort |
|---|---|---|---|
| **Илья (boss)** | MiniMax-M3 | Token Plan · Monthly Max | **High** (владелец: «токены можно не экономить») |
| **Алина (programmer)** | deepseek-v4-flash | Ollama Cloud Pro | (default) |
| **Марк (reviewer)** | GLM 5.3 Flash | Ollama Cloud Pro | (default) |
| **Кирилл (critic)** | GLM 5.3 Flash | Ollama Cloud Pro | (default) |

## Что «эталон» iter #5 берёт из iter #4

- **Все 51 задача** T001..T051 из iter #4 (28 FR, 5 US, 9 SC, 51 задача включая T051 FR-006 dead code).
- **Миграция 48_chain_id_backfill.sql** — остаётся в репо, владелец решает применять или нет (опционально, для эстетики parent-child viz на legacy данных).

## Что «эталон» iter #5 добавляет (НЕ в iter #4)

**Дополнительные улучшения от iter #1-#4 (опциональные):**
- **T052 [OPT]**: **Orphans endpoint** (FR-006 dead code fix) — `GET /api/admin/processes?orphans=true` возвращает процессы с `process_chain_id NOT NULL AND NOT IN (SELECT id FROM tbl_processes WHERE process_deleted_at IS NULL)`. UI toggle «Orphans» в фильтр-модалке. Решает Lesson #10 dead code.

Собственно, **T052 опциональный** — владелец решает включать или нет в iter #5 (если не хочет — оставляем backlog для iter #6).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Просмотр процессов с фильтрацией и parent-child визуализацией (Priority: P1) 🎯 MVP

Администратор открывает `/admin/processes`, видит top-level список (только head, `process_chain_id IS NULL`), фильтрует по статусу/типу/thread_id/process_chain_id/process_deleted_at/name, разворачивает head → видит tail-дети (lazy load).

**Why this priority**: P1 — базовая функциональность + parent-child viz (Lesson #10). Без визуализации parent-child владелец не сможет оценить fix.

**Independent Test**: Открыть `/admin/processes`, убедиться top-level загружается (≤2 сек); кликнуть expand на head → дети подгружаются (≤1 сек); применить фильтры → работают. Quickstart Scenarios 1, 9.

**Acceptance Scenarios**:
1. **Given** существуют процессы A (head), B (tail), C (tail), все с `process_chain_id` правильно заполненным, **When** администратор открывает `/admin/processes`, **Then** A отображается как head с кнопкой expand, B и C — как children A.
2. **Given** legacy tail-процессы с `process_chain_id=null`, **When** администратор открывает `/admin/processes`, **Then** они отображаются как самостоятельные head-процессы без дочерних (FR-005, R-005 iter #3).
3. **Given** администратор применяет фильтр «включая удалённые», **When** он нажимает Apply, **Then** таблица показывает и активные, и soft-deleted процессы с разной визуальной индикацией.

---

### User Story 2 - Редактирование процесса (Priority: P2)

Администратор нажимает кнопку «Редактировать» в Actions-колонке → открывается `ProcessEditModal` → меняет поля → сохраняет → таблица обновляется. **ТОЛЬКО через кнопку** (Lesson #9).

**Why this priority**: P2 — основная операция администратора. Зависит от US1.

**Independent Test**: Выбрать процесс → нажать «Редактировать» → модалка открывается → изменить name → сохранить → таблица обновляется. Клик на ячейку `name` НЕ открывает модалку. Quickstart Scenarios 2, 3.

**Acceptance Scenarios**:
1. **Given** администратор на `/admin/processes`, **When** он нажимает кнопку «Редактировать» в строке, **Then** открывается `ProcessEditModal` со ВСЕМИ редактируемыми полями.
2. **Given** открытая `ProcessEditModal`, **When** администратор меняет status с WAITING на DONE (минуя WORKING), **Then** UI показывает ошибку валидации (FR-017).
3. **Given** администратор меняет `process_start`/`process_end`, **When** он нажимает «Сохранить», **Then** запись в БД обновляется через Local-поля (Lesson RC-3 iter #3).

**⚠️ Lesson #9 iter #2**: edit-modal ТОЛЬКО через кнопку «Редактировать» в Actions-колонке. **УДАЛИТЬ** `@click.left="editProcess(data.item.id)"` из `<template #cell(name)>` (после отката iter #4 присутствует в дереве ~строки 65-73).

---

### User Story 3 - Удаление процесса (Priority: P2)

Администратор удаляет процесс. Логика зависит от статуса. **БЕЗ cascade** (FR-013 + owner clarification iter #1).

**Independent Test**: Создать процесс WAITING → удалить → запись пропадает из механизма исполнения, soft-deleted. Cascade OFF. Quickstart Scenarios 4, 5, 10.

**Acceptance Scenarios**:
1. **Given** процесс WAITING, **When** нажимает «Удалить», **Then** процесс отзывается из механизма (`KaraokeProcessWorker.threadsMap`; pickup-логика фильтрует `process_deleted_at IS NULL`), `process_deleted_at = NOW()`, audit DELETE.
2. **Given** процесс WORKING, **When** нажимает «Удалить», **Then** worker thread `Thread.interrupt()` + 5 сек grace (R-008 iter #3, FR-012) + `destroyForcibly()` при timeout → soft-delete.
3. **Given** процесс DONE, **When** нажимает «Удалить», **Then** ТОЛЬКО soft-delete.
4. **Given** parent-процесс с tail-детьми, **When** удаляет parent, **Then** дети НЕ удаляются (cascade OFF).

---

### User Story 4 - Retry ERROR-процесса (Priority: P3)

Администратор нажимает «Retry» на ERROR-процессе → процесс переходит в WAITING (FR-014). **НЕ** для удалённых (FR-014 условие `process_deleted_at IS NULL`, T048 iter #3).

**Independent Test**: Создать процесс с ошибкой → ERROR → Retry → WAITING. Не retry удалённого. Quickstart Scenarios 6, 7.

**Acceptance Scenarios**:
1. **Given** процесс ERROR с `process_deleted_at IS NULL`, **When** нажимает «Retry», **Then** процесс → WAITING, audit RETRY (FR-015).
2. **Given** процесс ERROR с `process_deleted_at IS NOT NULL`, **When** отображается таблица, **Then** кнопка «Retry» disabled/скрыта.

---

### User Story 5 - Просмотр audit log (Priority: P3)

Администратор открывает audit-модалку → видит историю изменений (EDIT/RETRY/DELETE за 30 дней, retention cron T047 iter #3).

**Independent Test**: Открыть процесс → нажать «Audit» → видит список. Quickstart Scenario 8.

**Acceptance Scenarios**:
1. **Given** процесс с 5 изменениями за неделю, **When** открывает audit-модалку, **Then** 5 записей в обратном хронологическом порядке с timestamp, actor, action, JSONB diff.
2. **Given** процесс без изменений, **Then** «Нет изменений».

---

### Edge Cases

- **Два администратора одновременно**: last-write-wins (один администратор).
- **WORKING delete timeout**: 5 сек grace → `destroyForcibly()` → soft-delete с пометкой в audit JSONB.
- **Миграция 47 не применена**: UI graceful warning.
- **Audit retention**: cron `DELETE FROM tbl_processes_audit WHERE created_at < NOW() - INTERVAL '30 days'`.
- **Broken parent reference** (FR-006): UI badge «⚠️ parent не найден» (см. T052 опциональный — orphans endpoint).
- **Edit soft-deleted процесса**: модалка readonly или warning.

## Requirements *(mandatory)*

### Functional Requirements

#### Просмотр и фильтрация
- **FR-001**: System MUST отображать таблицу `tbl_processes` со всеми **20 основными колонками** из 28 реальных (id, name, status, type, command, args, envs, description, songId, order, priority, prioritet, withoutControl, threadId, processChainId, processDeletedAt, updatedAt, startedAt, endedAt, recordhash). **8 derived progress-колонок НЕ отображаются**: `process_start_str`, `process_end_str`, `process_percentage`, `process_percentage_str`, `process_time_passed_ms`, `process_time_passed_str`, `process_time_left_ms`, `process_time_left_str`. **Имена колонок** в реальной схеме (зафиксированы в `notes/real-schema-tbl_processes.txt` 2026-09-07): `last_update` (НЕ `updated_at`), `process_start`/`process_end` (НЕ `started_at`/`ended_at`).

- **FR-002**: System MUST поддерживать фильтрацию по: status (multi-select), type (multi-select), threadId (input), process_chain_id (input), process_deleted_at (boolean «включая удалённые»), name (substring matcher `ILIKE '%' || ? || '%'`).

- **FR-003**: Filter modal MUST иметь заголовок «Фильтр процессов» (НЕ «Фильтр для песен»).

#### Parent-child визуализация (CRITICAL — Lesson #10)
- **FR-004**: System MUST визуализировать parent-child связи через `process_chain_id` — top-level + lazy load.
- **FR-005**: System MUST корректно отображать legacy-процессы с `process_chain_id=null` как самостоятельные head-процессы без дочерних.
- **FR-006**: System MUST handle broken references (`process_chain_id` указывает на несуществующий parent) — UI badge «⚠️ parent не найден». *(T052 опциональный — orphans endpoint для full FR-006 reachable scope.)*

#### Edit (single entry point — Lesson #9)
- **FR-007**: System MUST открывать `ProcessEditModal` ТОЛЬКО через кнопку «Редактировать» в Actions-колонке. **НЕ** через `@click` на ячейке `name`. **УДАЛИТЬ** `@click.left="editProcess(data.item.id)"` из `<template #cell(name)>` (после отката iter #4 присутствует в дереве ~строки 65-73).
- **FR-008**: `ProcessEditModal` MUST позволять редактировать: name, status, order, priority, command, args, envs, description, songId, type, **process_start** (datetime Local), **process_end** (datetime Local), prioritet, withoutControl, threadId. Datetime через Local-поля (Lesson RC-3 iter #3).
- **FR-009**: Edit-modal MUST следовать convention pattern: `ProcessEditModal.vue` (тонкая обёртка, 1:1 с `SongEditModal.vue`) + `ProcessEdit.vue` (форма, 1:1 с `SongEdit.vue`).

#### Delete
- **FR-010**: System MUST soft-delete (`process_deleted_at = NOW()`) для DONE и ERROR.
- **FR-011**: System MUST cancel для WAITING + soft-delete (механизм исполнения — `KaraokeProcessWorker.threadsMap`; pickup-логика фильтрует `process_deleted_at IS NULL`).
- **FR-012**: System MUST stop worker thread (`Thread.interrupt()` + grace **5 секунд** + `destroyForcibly()`) для WORKING + soft-delete.
- **FR-013**: System MUST soft-delete БЕЗ cascade — дети остаются.

#### Retry
- **FR-014**: System MUST позволять retry только для ERROR с `process_deleted_at IS NULL` — перевод в WAITING.
- **FR-015**: Retry MUST создавать запись в `tbl_processes_audit` с типом RETRY.

#### Audit
- **FR-016**: System MUST записывать в `tbl_processes_audit` все EDIT/RETRY/DELETE с timestamp, actor, action, **полным diff старых→новых значений в `old_value`/`new_value` JSONB раздельно** (Lesson RC-4 iter #3). actor VARCHAR(64) (миграция 47).
- **FR-017**: System MUST валидировать status transitions при EDIT: CREATING→WAITING/WORKING/ERROR, WAITING→WORKING, WORKING→DONE/ERROR, DONE→(terminal), ERROR→WAITING (только через retry FR-014). **Отмена** WAITING/WORKING — через US3-delete, не сменой статуса. Недопустимые transitions → ошибка UI с кодом `INVALID_STATUS_TRANSITION`.

#### Retention
- **FR-018**: System MUST хранить audit 30 дней. Cron `@Scheduled cleanupOldAudit()` (R-009 cron-стратегия, не query filter): `DELETE FROM tbl_processes_audit WHERE created_at < NOW() - INTERVAL '30 days'` (хардкод `'30 days'`, политика фиксирована).

#### Race protection (Lesson #5)
- **FR-019**: `KaraokeProcess.save()` MUST NOT записывать `process_deleted_at` / `process_chain_id`. Targeted UPDATE через raw JDBC: `db.getConnection()?.use { conn → conn.prepareStatement(...).use { ps → ps.setLong(...); ps.executeUpdate() }}` (KaraokeProcess.kt:361 reference). **НЕ** `connection.executeUpdate(...)` (нет такого API).

#### Architecture
- **FR-020**: Backend MUST размещаться в `karaoke-app/`, использовать `com.svoemesto.karaokeapp.WORKING_DATABASE`.
- **FR-021**: Admin controller MUST следовать `StemJobsAdminController` pattern.
- **FR-022**: Admin service И controller MUST иметь `@DependsOn("karaokeAppService")` (впервые введено в iter #4 — 0 вхождений `@DependsOn` в master; Lesson #4).

#### Livedocs
- **FR-023**: После имплементации MUST обновить `livedocs/INDEX.md` + добавить `livedocs/features/315-admin-ui-karaoke-process-v5.md`.

#### 5-step verification (Lesson #7)
- **FR-024**: После ЛЮБОГО изменения кода MUST быть 5-step verification канон `brief.md:59-63`:
  1. Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
  2. Линтеры: `./gradlew :karaoke-web:ktlintCheck` + `npm run lint` в `webvue3/` (+ `npm run lint` в `karaoke-public/` если менялся)
  3. Backend bootJar: `./gradlew :karaoke-web:bootJar --parallel` (на nsa-i9: также `:karaoke-app:bootJar`)
  4. Frontend Vite: `cd webvue3 && npm run build && npm run format:check` (+ `cd karaoke-public && npm run build && npm run format:check` если менялся)
  5. Docker: `cd deploy && bash do.sh build_webvue3`; если менялся `karaoke-public` — `bash do.sh build_public`
  - На nsa-i9 под nsa допустим 3-step subset (compile + lint + bootJar) для правок только в `karaoke-app`/`karaoke-web`.
  - **karaoke-public шаги 2/4/5 — no-op**, пока `karaoke-public` не менялся (FR-005 в спеке: scope только admin bundle).

#### Speckit workflow
- **FR-025**: 10 фаз через named skills.
- **FR-026**: После КАЖДОЙ фазы add-comment в OpenProject WP #64.
- **FR-027**: mark-review ТОЛЬКО после runtime-boot + visual-verify владельца.

#### Git
- **FR-028**: Финальный commit + push + merge — ТОЛЬКО по прямому указанию владельца.

### Key Entities

- **KaraokeProcess**: 20 отображаемых полей из 28 реальных. Реальные колонки: `last_update`, `process_start`, `process_end` (НЕ `updated_at`/`started_at`/`ended_at`). Enum `KaraokeProcessStatuses`: CREATING/WAITING/WORKING/DONE/ERROR (5 значений, **НЕТ** CANCELED). Enum `KaraokeProcessTypes`: 31 активная константа (ещё 3 закомментированы).
- **ProcessAuditEntry** (миграция 47): id, process_id, actor (VARCHAR(64)), action CHECK(EDIT/RETRY/DELETE), old_value JSONB, new_value JSONB, created_at.
- **Механизм исполнения** (НЕ `KaraokeProcessQueue` — класс НЕ существует в коде, grep 0): `KaraokeProcessWorker.threadsMap: MutableMap<Int, KaraokeProcessThread?>` + `KaraokeProcessThread` class (KaraokeProcessWorker.kt:70, WORKING ~89, interrupt ~1340). Cancel WAITING = soft-delete + pickup-логика фильтрует `process_deleted_at IS NULL`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Top-level список 18k+ процессов ≤2 сек. Lazy load детей ≤1 сек.
- **SC-002**: Edit ≤30 сек end-to-end.
- **SC-003**: 100% EDIT/RETRY/DELETE → audit-запись ≤1 сек.
- **SC-004**: 0 случаев cascade delete.
- **SC-005**: 0 случаев «заблокированной» миграции 47.
- **SC-006**: 10 фаз speckit-цикла завершаются с verdict.
- **SC-007**: **MVP-checkpoint пройден** (Phase 8 US1 visual verify владельцем ОК перед US2..US5).
- **SC-008**: Владелец делает smoke-test 6 endpoints через curl перед mark-review.
- **SC-009**: Финальный commit + push + merge по прямому указанию владельца.

## Assumptions

- **Target users**: единственный администратор (owner).
- **Миграция 47 применена** (в master, commit `7ad6313c`); **миграция 48 НЕ применена** (БД откачена, 0 chains — владелец решает). Спека НЕ зависит от 48: FR-005 обрабатывает legacy `process_chain_id=null`; применение 48 включает parent-child viz на legacy данных.
- **Concurrency**: last-write-wins.
- **Audit retention 30 дней**: cron (R-009).
- **MVP-checkpoint обязателен** (Lesson #15) — владелец iter #4 сказал «не дёргать» — это было исключение, не норма.
- **Boss self-verify** через `psql information_schema.columns` ДО отправки требований (Lesson #16).
- **Агенты НЕ перезапускают контейнер** (governance Karaoke/AGENTS.md, Lesson #12).
- **Iter #1-#4 артефакты**: бэкап в Boss (`/home/nsa/Agents/Boss/iter3-artifacts-backup/` + `ITER-*-REPORT-*.md`).

— Илья (boss, MiniMax-M3 High)

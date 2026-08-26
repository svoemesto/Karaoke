---
description: "Task list для FR-101: батч-синхронизация sync-записей в KaraokeProcessWorker"
---

# Tasks: Батч-синхронизация sync-записей в KaraokeProcessWorker

**Input**: Design documents from `/specs/242-db-sync-batch-worker/`
- plan.md (required)
- spec.md (required for user stories)

**Tests**: Конституция § Тесты — автоматические тесты в karaoke-app `@Disabled`. Тестирование — ручное на admin-машине + `pg_log`-замеры. Tests-фазы НЕ включены.

**Organization**: Tasks сгруппированы по user story (US1 — основная, US2 — параллельная отзывчивость UI).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, без зависимостей)
- **[Story]**: к какой user story относится (US1, US2)
- В описаниях — точные file:line

## Path Conventions

- **Single project**: `karaoke-app/src/main/kotlin/...`, тесты отсутствуют (см. Constitution)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка ветки и preconditions проверка.

- [ ] T001 [P] Переключиться на ветку `242-db-sync-batch-worker`, убедиться что `git status` чистый.
- [ ] T002 [P] Проверить наличие `Song.loadListFromDbByIds(ids: List<Long>, database, storageService, storageApiClient): List<Song>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`. Ожидаемая сигнатура: `chunked(rowChunkSize)` + `WHERE id IN (...)`.
- [ ] T003 [P] Проверить наличие `KaraokeDbTable.deleteBatch(tableName, ids, database, sync)` или эквивалента. Если отсутствует — пометить как T004 в Phase 2.
- [ ] T004 [P] Проверить `SyncRegistry.DELETE_CHUNK_SIZE` — значение для батч-Delete. Записать в Phase 2 как константу `DELETE_CHUNK_SIZE`.
- [ ] T005 [P] Проверить `SongSyncTarget.rowChunkSize = 25` — использовать как `SELECT_CHUNK_SIZE` (тяжёлые строки Song).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Вспомогательный код, который ДОЛЖЕН быть готов до user story.

**⚠️ CRITICAL**: User story не может начаться, пока Phase 2 не завершена.

- [ ] T006 [P] Если `KaraokeDbTable.deleteBatch` отсутствует — добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt` companion-метод:
  ```kotlin
  fun deleteBatch(
      tableName: String,
      ids: List<Long>,
      database: KaraokeConnection,
      sync: Boolean = false,
  ): Int
  ```
  Реализация через PostgreSQL `Array` (`connection.unwrap(PGConnection::class.java).createArrayOf("BIGINT", ids.toTypedArray())`) с fallback на текстовую подстановку `id IN (1,2,3,...)`.
- [ ] T007 Добавить KDoc для `deleteBatch` с ссылкой на `archive/docs/features/dual-db-sync.md` (Constitution § VI Code Standards, FR-006 — KDoc обязателен).
- [ ] T008 Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти без ошибок. Если есть ошибки в KDoc — починить.
- [ ] T009 Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений.

**Checkpoint**: Foundation готова — можно начинать US1.

---

## Phase 3: User Story 1 — Низкая нагрузка на БД от sync-цикла (Priority: P1) 🎯 MVP

**Goal**: При 100 sync-записях sync-цикл делает ≤ 11 SQL вместо 201.

**Independent Test**: Создать тестовый сценарий — 100 sync-записей в `tbl_songs_sync`, дождаться 24 сек (`intervalCheckFiles`), проверить docker logs `KaraokeProcessWorker` — должно быть ≤ 11 SQL.

### Implementation for User Story 1

- [ ] T010 [US1] Извлечь в `KaraokeProcessWorker` новый приватный метод `processRemoteSongsSyncBatch(database, storageService, storageApiClient): SyncBatchResult` со вспомогательным `data class SyncBatchResult(localUpdates: Int, localInserts: Int, renderSideEffects: Int, sqlQueryCount: Int)`. Логика:
  - 1 SELECT: `Song.loadListFromDb(database = Connection.remote(), sync = true, ...)` — все sync-записи.
  - Если empty — return с `sqlQueryCount=1`.
  - 1 SELECT chunked: `ids.chunked(SELECT_CHUNK_SIZE /* 25 */).flatMap { Song.loadListFromDbByIds(it, ...) }.associateBy { it.id }`.
  - Цикл `listSongsSync.forEach { songSync -> ... }` — обновляет счётчики `localUpdates`, `localInserts` (per-record, сохраняется текущая логика diff + INSERT).
  - Side-effect `if (songSync.tags == "RENDER")` — per-record, вынести в helper `renderSideEffect(songLocal, ...)` (если ≥ 5 строк — вынести).
  - 1 DELETE chunked: `ids.chunked(DELETE_CHUNK_SIZE).forEach { KaraokeDbTable.deleteBatch(Song.TABLE_NAME, it, Connection.remote(), sync = true) }`.
  - Return `SyncBatchResult`.

- [ ] T011 [US1] Заменить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:doStart()` блок строк ~994-1106 на вызов `val syncResult = processRemoteSongsSyncBatch(database, storageService, storageApiClient)` + println с `updates`, `inserts`, `renderSideEffects`, `sqlQueryCount` (FR-006 spec.md — логирование).

- [ ] T012 [P] [US1] Добавить константы в companion-объект `KaraokeProcessWorker`:
  ```kotlin
  private const val SELECT_CHUNK_SIZE = 25  // Same as SongSyncTarget.rowChunkSize
  private const val DELETE_CHUNK_SIZE = 500  // Same as SyncRegistry.DELETE_CHUNK_SIZE
  ```

- [ ] T013 [US1] Добавить KDoc для `processRemoteSongsSyncBatch` с `@see` на `archive/docs/features/dual-db-sync.md` (Constitution § VI FR-006 — обязательно).

- [ ] T014 [US1] Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти. Если есть ошибки типов (Long/Int mismatch, см. AGENTS.md «обязательная проверка после правок») — починить.

- [ ] T015 [US1] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений baseline. Если есть — починить (или добавить в baseline-файл с обоснованием, см. Constitution § VI FR-007).

- [ ] T016 [US1] Локальная проверка: запустить karaoke-app на admin-машине с включённым `Karaoke.monitoringRemoteSettingsSync = true` и вставить 100 записей в `tbl_songs_sync` REMOTE-БД. Дождаться 24 сек, проверить docker logs — должно быть ≤ 11 SQL от sync-цикла (SC-001).

**Checkpoint**: US1 функциональна и независимо тестируема. 201 SQL → ≤ 11 SQL на 100 записях (SC-001).

---

## Phase 4: User Story 2 — Отзывчивость UI при больших sync-окнах (Priority: P2)

**Goal**: Параллельная работа админ-UI во время sync-цикла без contention на JDBC-соединении.

**Independent Test**: Открыть Song Editor параллельно с sync-окном 100 записей. UI не виснет > 200 мс.

**Замечание**: Это требование удовлетворяется автоматически после US1, потому что batch-сокращение SQL → меньше времени держания ThreadLocal-соединения. Отдельной реализации не требуется — только verify.

### Verification for User Story 2

- [ ] T017 [US2] Запустить на admin-машине одновременно: (1) sync-цикл с 100 записями; (2) открытие Song Editor через webvue3. Замерить latency UI (browser devtools network tab).
- [ ] T018 [US2] Подтвердить, что latency UI < 200 мс (SC spec.md US2 AC1). Если нет — это значит, что batch-логика недостаточно сократила блокировку, нужно дополнительно исследовать.

**Checkpoint**: US2 автоматически удовлетворена через US1. Verification step.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Финализация, regression-тесты, документация.

- [ ] T019 [P] Regression-тест для `tags = "RENDER"` (SC-005): сделать большой push с RENDER-тегом, убедиться что все песни получают karaoke-процессы (через webvue3-таблицу Processes).
- [ ] T020 [P] Замер `pg_log` за 24 ч после деплоя (SC-003): должно быть ≥ 30% снижение SQL от `KaraokeProcessWorker`. Сравнить с baseline.
- [ ] T021 [P] Обновить per-feature документ `archive/docs/features/dual-db-sync.md` (если есть — ссылка на sync-цикл в `KaraokeProcessWorker`). Constitution § VI FR-009: правка одной из 9 ключевых подсистем → обновить per-feature документ в том же PR.
- [ ] T022 Code-review checklist (Constitution § VI FR-006, FR-009): KDoc на новых публичных API, per-feature документ обновлён, baseline линтера не вырос.
- [ ] T023 Создать PR через `gh pr create --base master` (AGENTS.md «CI-gate для master»). Title: `db-sync-batch-worker: батч-синхронизация sync-записей в KaraokeProcessWorker`.
- [ ] T024 Дождаться CI 8/8 PASS (`gh pr checks`), merge через `gh pr merge --merge` (БЕЗ `--delete-branch` — AGENTS.md «Lifecycle: ветка живёт после мёрджа»).
- [ ] T025 Deploy на admin-машину (НЕ на прод — это admin-only). Запустить `deploy/do.sh build_start_app` или эквивалент.
- [ ] T026 Post-deploy: снять `pg_log` через 24 ч, сравнить с T020, подтвердить SC-003.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — можно начать сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 — БЛОКИРУЕТ все user stories.
- **Phase 3 (US1)**: зависит от Phase 2 — основная реализация.
- **Phase 4 (US2)**: зависит от Phase 3 — только verify (нет нового кода).
- **Phase 5 (Polish)**: зависит от всех user stories.

### Within Each User Story

- T001-T005 → параллельно (Setup preconditions).
- T006-T009 → последовательно (Foundational: deleteBatch).
- T010-T016 → последовательно (US1: refactor + integrate).
- T017-T018 → параллельно (US2 verify).
- T019-T026 → параллельно где возможно (Polish).

### Parallel Opportunities

- Phase 1 полностью параллельна (T001-T005).
- T006 и T007 можно делать параллельно (deleteBatch + KDoc).
- T019, T020, T021, T022 можно параллельно.

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup (T001-T005).
2. ✅ Phase 2: Foundational (T006-T009) — добавить deleteBatch.
3. ✅ Phase 3: US1 (T010-T016) — основной рефакторинг.
4. **STOP and VALIDATE**: запустить на admin-машине, проверить docker logs + `pg_log`.
5. Deploy на admin-машину (НЕ на прод).

### Incremental Delivery

Эта фича — US1 = MVP. US2 — только verify, нет нового кода. Не требует отдельного деплоя.

### Parallel Team Strategy

Фича маленькая (~1-2 часа кодинга), один разработчик. Параллельная работа с другими фичами (FR-102, FR-103, FR-104) возможна, потому что они в разных файлах.

---

## Notes

- Это **admin-only** фича — на проде (`karaoke-web`) НЕ влияет. Deploy только на admin-машину.
- Side-effect `tags = "RENDER"` остаётся per-record (важно для regression SC-005).
- `DELETE_CHUNK_SIZE = 500` (большой, потому что DELETE — лёгкий запрос) vs `SELECT_CHUNK_SIZE = 25` (тяжёлые строки Song). Значения валидированы в `SongSyncTarget.rowChunkSize` (parent спека, A.4).
- **Не блокирует** другие Tier-1 фичи (FR-102, FR-103, FR-104) — разные файлы, разные ветки.
- `pg_stat_statements` НЕ включается (parent спека, Clarifications Session 2026-08-26).
- После успешного merge в master — feature-ветка `242-db-sync-batch-worker` НЕ удаляется (AGENTS.md «Lifecycle: ветка живёт после мёрджа»).

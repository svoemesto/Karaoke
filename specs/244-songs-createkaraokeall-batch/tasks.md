---
description: "Task list для FR-103: батч-загрузка песен в getSongsCreateKaraokeAll"
---

# Tasks: Батч-загрузка песен в getSongsCreateKaraokeAll

**Input**: Design documents from `/specs/244-songs-createkaraokeall-batch/`
- plan.md (required)
- spec.md (required for user stories)

**Tests**: Конституция § Тесты — автоматические тесты `@Disabled`. Тестирование — ручное через webvue3-таблицу Songs. Tests-фазы НЕ включены.

**Organization**: Tasks сгруппированы по user story (US1 — основная, US2 — читаемость кода).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно
- **[Story]**: US1, US2
- В описаниях — точные file:line

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preconditions проверка.

- [ ] T001 [P] Переключиться на ветку `244-songs-createkaraokeall-batch`, убедиться что `git status` чистый.
- [ ] T002 [P] Прочитать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:getSongsCreateKaraokeAll` (строки 3664-3778) — текущая реализация с N+1.
- [ ] T003 [P] Найти `Song.loadListFromDbByIds(ids, database, storageService, storageApiClient)` — проверить сигнатуру. Должна быть в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.
- [ ] T004 [P] Найти `SongSyncTarget.rowChunkSize = 25` — использовать как `SELECT_CHUNK_SIZE`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Helper `loadSongsBatch`.

**⚠️ CRITICAL**: US1 не может начаться, пока helper не готов.

- [ ] T005 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` добавить приватный helper:
  ```kotlin
  /**
   * Батч-загрузка песен по списку ID. Возвращает Map<id, Song> для O(1) lookup.
   * @see specs/244-songs-createkaraokeall-batch/spec.md FR-001
   */
  private fun loadSongsBatch(
      ids: List<Long>,
      database: KaraokeConnection,
      storageService: KaraokeStorageService,
      storageApiClient: StorageApiClient,
  ): Map<Long, Song> {
      if (ids.isEmpty()) return emptyMap()
      return ids
          .chunked(SELECT_CHUNK_SIZE)
          .flatMap { chunk ->
              Song.loadListFromDbByIds(
                  ids = chunk,
                  database = database,
                  storageService = storageService,
                  storageApiClient = storageApiClient,
              )
          }.associateBy { it.id }
  }

  private companion object {
      private const val SELECT_CHUNK_SIZE = 25  // Same as SongSyncTarget.rowChunkSize
  }
  ```
  Замечание: если в `ApiController` уже есть `companion object` — добавить `SELECT_CHUNK_SIZE` туда, иначе создать private.

- [ ] T006 Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти.

**Checkpoint**: Helper готов — можно начинать US1.

---

## Phase 3: User Story 1 — Быстрый запуск массового создания караоке (Priority: P1) 🎯 MVP

**Goal**: 100 ID → ≤ 5 SQL вместо 100; latency ≤ 3 сек (cold path).

**Independent Test**: Через webvue3-таблицу Songs выбрать 100 песен → нажать «Создать караоке для всех» → замерить latency и `pg_log`.

### Implementation for User Story 1

- [ ] T007 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:getSongsCreateKaraokeAll` (строки 3664-3778) заменить цикл `ids.forEach { id -> Song.loadFromDbById(id, ...) }` (строка ~3683) на:
  ```kotlin
  val ids = songsIds
      .split(";")
      .map { it }
      .filter { it != "" }
      .map { it.toLong() }
      .distinct()  // FR-005 spec.md (опционально, см. plan.md Phase 3)
  val songsById = loadSongsBatch(
      ids = ids,
      database = WORKING_DATABASE,
      storageService = storageService,
      storageApiClient = storageApiClient,
  )
  ```

- [ ] T008 [US1] Заменить дальнейший цикл `ids.forEach { id -> ... song?.let { ... } }` (строки ~3691-3753) на:
  ```kotlin
  ids.forEach { id ->
      val song = songsById[id]  // O(1) lookup
      song?.let {
          if (createLyrics) KaraokeProcess.createProcess(...)
          if (createKaraoke) KaraokeProcess.createProcess(...)
          if (createChords) KaraokeProcess.createProcess(...)
          if (createMelody) KaraokeProcess.createProcess(...)
          if (createDemo) KaraokeProcess.createProcess(...)
      }
  }
  ```

- [ ] T009 [US1] Изменить `result = true` (строка ~3754) на `result = ids.isNotEmpty()` (текущее поведение ошибочно — возвращает success даже при пустом `songsIds`).

- [ ] T010 [US1] Убедиться, что SSE-уведомление (`if (result) { SNS.send(...) } else { ... }`, строки ~3757-3777) остаётся без изменений.

- [ ] T011 [P] [US1] Добавить KDoc-комментарий наверху метода `getSongsCreateKaraokeAll` с ссылкой на `specs/244-songs-createkaraokeall-batch/spec.md` (Constitution § VI FR-006).

- [ ] T012 [US1] Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти. Если есть ошибки типов (Long vs Int — см. AGENTS.md Pass 239 инцидент) — починить.

- [ ] T013 [US1] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений.

- [ ] T014 [US1] Compile + ktlint пройдены.

- [ ] T015 [US1] Локальная проверка: запустить karaoke-app, через webvue3 выбрать 100 песен → нажать «Создать караоке для всех» → замерить latency (≤ 3 сек, SC-003) и проверить `pg_log` (≤ 5 SQL, SC-001).

**Checkpoint**: US1 функциональна. 100 SQL → ≤ 5 SQL (SC-001), latency улучшен (SC-003).

---

## Phase 4: User Story 2 — Читаемость admin-кода (Priority: P2)

**Goal**: Метод имеет ≤ 80 строк, видимая батч-логика (не N+1).

**Independent Test**: Code-review метода `getSongsCreateKaraokeAll` — ≤ 80 строк.

**Замечание**: Это требование удовлетворяется автоматически после US1, потому что извлечение helper'а + батч-логика сокращают размер метода.

### Verification for User Story 2

- [ ] T016 [US2] Подсчитать количество строк в `getSongsCreateKaraokeAll` после US1 (должно быть ≤ 80). Если больше — вынести `prior*` валидацию в отдельный helper `buildProcessActions(priorLyrics, priorKaraoke, priorChords, priorMelody, priorDemo): List<Pair<KaraokeProcessTypes, String>>`.

- [ ] T017 [US2] Код-ревью: убедиться, что helper `loadSongsBatch` корректно инкапсулирует батч-логику и может быть переиспользован в других admin-контроллерах (parent спека, H-14).

**Checkpoint**: US2 автоматически удовлетворена через US1. Verify step.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T018 [P] Regression: убедиться что для 100 песен с `priorLyrics="10"` создаётся 100 процессов `RENDER_MP4_LYRICS` (через webvue3-таблицу Processes). SC-005 spec.md.
- [ ] T019 [P] Если FR-005 (`.distinct()`) принят — проверить, что дублирующиеся ID в `songsIds` НЕ плодят дублирующие INSERT в `tbl_processes` (SC-007). Если не принят — задача не нужна.
- [ ] T020 [P] Замер `pg_log` за 24 ч после деплоя (SC-006): должно быть ≥ 50% снижение SQL к `tbl_songs` от admin-операций.
- [ ] T021 [P] Обновить per-feature документ `archive/docs/features/song-editor.md` (если есть; иначе создать). Constitution § VI FR-009.
- [ ] T022 Code-review checklist (Constitution § VI FR-006, FR-009): KDoc обновлён, per-feature документ обновлён, baseline линтера не вырос.
- [ ] T023 Создать PR через `gh pr create --base master`. Title: `songs-createkaraokeall-batch: батч-загрузка песен через loadListFromDbByIds`.
- [ ] T024 Дождаться CI 8/8 PASS, merge через `gh pr merge --merge` (БЕЗ `--delete-branch`).
- [ ] T025 Deploy на admin-машину (НЕ на prod — это admin-only endpoint).
- [ ] T026 Post-deploy: ручное тестирование через webvue3 + `pg_log`-замер через 24 ч.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: нет зависимостей.
- **Phase 2**: зависит от Phase 1 — БЛОКИРУЕТ US1.
- **Phase 3 (US1)**: зависит от Phase 2.
- **Phase 4 (US2)**: зависит от Phase 3.
- **Phase 5**: зависит от всех user stories.

### Within Each User Story

- Phase 1 параллельна (T001-T004).
- Phase 2 последовательна (T005-T006).
- Phase 3 последовательна (T007-T015).
- Phase 4 последовательна (T016-T017).
- Phase 5 параллельна где возможно (T018-T026).

### Parallel Opportunities

- Все Setup preconditions параллельны.
- Regression + pg_log + per-feature doc можно параллельно.

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup preconditions.
2. ✅ Phase 2: Foundational (helper).
3. ✅ Phase 3: US1 (батч-логика).
4. **STOP and VALIDATE**: локальная проверка через webvue3 + `pg_log`.
5. Deploy на admin-машину.

### Incremental Delivery

- US1 = основной эффект (снижение SQL + latency).
- US2 = дополнительный (читаемость). Без US2 фича всё равно работает.

### Parallel Team Strategy

Фича маленькая (~50 мин кодинга). Параллельно с FR-101, FR-102, FR-104 — разные файлы, разные ветки.

---

## Notes

- Это **admin-only** фича — на проде (`karaoke-web`) этот endpoint НЕ вызывается. Deploy только на admin-машину.
- `.distinct()` (FR-005) — добавляется по умолчанию (strict improvement). Если пользователь против — вернуть код без `.distinct()`.
- Side-effect `KaraokeProcess.createProcess` остаётся per-record (это атомарная операция, не батчится).
- `result = ids.isNotEmpty()` (T009) — корректировка текущего бага (`result = true` без проверки).
- SSE-уведомление — без изменений.
- **Не блокирует** другие Tier-1 фичи — разные файлы, разные ветки.
- После успешного merge — feature-ветка НЕ удаляется.

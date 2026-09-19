---
description: "Tasks: 413 — Синхронизация аудио-потомков (#141)"
---

# Tasks: Синхронизация аудио-потомков (#141)

**Input**: Design documents from `/specs/413-sync-audio-descendants/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: unit-тесты для гейта/очереди включены (проект использует JUnit в
`karaoke-app/src/test`; остальные — manual quickstart).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллельно (разные файлы, нет зависимостей)
- **[Story]**: US1 / US2 / US3 (из spec.md)

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Tests: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/`
- Frontend: `webvue3/src/`
- Knowledge: `knowledge/domains/catalog/`
- Feature doc: `docs/features/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: каркас ветки и новые файлы

- [X] T001 Убедиться, что активна ветка `413-sync-audio-descendants` (создана bootstrap), `git status` чистый от чужих правок
- [X] T002 [P] Создать файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt` со скелетом `object SyncAudioDescendants` (пустые `onSongSaved`/`syncAll`, поля очереди и флагов из data-model.md) с KDoc `@see specs/413-sync-audio-descendants/spec.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: инфраструктура, нужная всем историям — гейт, очередь, воркер, helper переноса

**⚠️ CRITICAL**: без этой фазы ни одна story не работает

- [X] T003 [P] Реализовать в `SyncAudioDescendants.kt` константу `CONTENT_FIELDS` = `{source_text, result_text, source_markers, formatted_text_song, formatted_text_tabs, formatted_text_chords}` + функцию `shouldEnqueue(song, previous, changedFields): Boolean` по таблице условий research R1 (статус≥5, content-diff ИЛИ переход <5→≥5, непустые маркеры для `SongType.SONG`, не `inSync`)
- [X] T004 [P] Реализовать очередь: `pendingParents: LinkedHashSet<Long>` под synchronized, `workerRunning: @Volatile Boolean`, `adminPending: @Volatile Boolean`, `inSync: ThreadLocal<Boolean>`, постановка + запуск воркера (single-flight) в `SyncAudioDescendants.kt`
- [X] T005 Реализовать воркер-цикл `drainQueue()` в `SyncAudioDescendants.kt`: последовательный разбор очереди, вызов `syncOneParent(parentId)`, `try/catch` на каждой песне с логом `[sync-audio]`, сброс `workerRunning`; при `adminPending` — SSE-сводка (`SNS.send(SseNotification.message(Message(...)))`) и сброс флага
- [X] T006 Реализовать `syncOneParent(parentId): SyncAudioResult` в `SyncAudioDescendants.kt`: загрузка родителя, гейт статуса ≥5, `SELECT id FROM tbl_songs WHERE audio_parent_id = ? AND id_status < 6`, цикл по потомкам с `KaraokeProcess.hasActiveProcess` → skip `active_process`, `WaveformCompare.compareWaveforms` → skip `no_audio`/`below_threshold`, иначе `syncAudioDescendantFromParent`
- [X] T007 Реализовать helper `syncAudioDescendantFromParent(child, parent, similarityPercent, deltaMs)` в `SyncAudioDescendants.kt`: перенос `sourceText`/`resultText`/`sourceMarkers` (`shiftMarkersAndFixEnd(parent.sourceMarkers, deltaMs, child.ms)`)/`formattedText*`, обновление `audioSimilarityPercent`/`audioDeltaMs`/`audioCompareHistory`, `ID_STATUS = "5"`, один `saveToDbLocked()` (research R4)
- [X] T008 Реализовать в `SyncAudioDescendants.kt` запись `.srt`: для каждого голоса `child.convertMarkersToSrt(voice)` → `"${child.rootFolder}/${child.fileName}.voice${voice + 1}.srt"` + `runCommand(listOf("chmod","666", path))`, ошибки — лог без отката (research R8)
- [X] T009 Добавить data class `SyncAudioResult(parentId, processed, synced, skipped, reason)` и счётчики прохода (admin-сводка) в `SyncAudioDescendants.kt`

**Checkpoint**: механика готова (гейт/очередь/воркер/перенос), но ещё не подключена к сохранению.

---

## Phase 3: User Story 1 — Авто-синхронизация при правке родителя (Priority: P1) 🎯 MVP

**Goal**: правка родителя (статус ≥5) автоматически подтягивается в аудио-потомков.

**Independent Test**: сценарии 1-6 из [quickstart.md](quickstart.md).

### Implementation for User Story 1

- [X] T010 [US1] Добавить вызов хука в `Song.saveToDb()` (UPDATE-ветка, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`) после успешного `ps.executeUpdate()`: сформировать `changedContentFields` из `diff`, вызвать `SyncAudioDescendants.onSongSaved(this, savedSong, changedFields)`
- [X] T011 [US1] Добавить вызов хука в `Song.saveToDbLocked()` после `connection.commit()` (там же), с подавлением внутреннего pre-commit вызова через `inSync` (research R1)
- [X] T012 [US1] Реализовать `onSongSaved` в `SyncAudioDescendants.kt`: `shouldEnqueue` → `inSync`-подавление собственной записи → постановка parent в очередь (research R5)
- [X] T013 [US1] Логирование `[sync-audio]` по каждой паре и родителю (формат из contracts/internal-mechanism.md) в `SyncAudioDescendants.kt`

### Tests for User Story 1

- [X] T014 [P] [US1] Unit-тест `shouldEnqueue` (все ветви гейта: статус<5, content-diff, переход <5→≥5, 5→6, пустые маркеры для SONG, inSync) в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendantsTest.kt`
- [X] T015 [P] [US1] Unit-тест дедупликации очереди (`LinkedHashSet`: повторная постановка одного id не дублируется) в том же файле
- [ ] T016 [US1] Ручная валидация quickstart-сценариев 1-6 (нужна admin-машина с данными)

**Checkpoint**: MVP — автоматическая синхронизация работает end-to-end.

---

## Phase 4: User Story 2 — Массовая синхронизация из админки (Priority: P2)

**Goal**: кнопка на главной админки запускает проход по всем потомкам.

**Independent Test**: quickstart-сценарий 7.

### Implementation for User Story 2

- [X] T017 [P] [US2] Реализовать `syncAll(): String` в `SyncAudioDescendants.kt`: `SELECT DISTINCT audio_parent_id FROM tbl_songs WHERE audio_parent_id <> 0 AND id_status < 6`, постановка в общую очередь, `adminPending = true`, возврат `"ALREADY_RUNNING"` если проход идёт, иначе `"OK"`
- [X] T018 [P] [US2] Добавить эндпоинт `POST /utils/syncaudioparents` → `SyncAudioDescendants.syncAll()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (по образцу `doFindAudioParentForAuthor`, `ApiController.kt:5887`)
- [X] T019 [P] [US2] Добавить Vuex action `syncAudioParentsPromise()` в `webvue3/src/components/Songs/store.js` (POST `/api/utils/syncaudioparents`, по образцу `findAudioParentForAuthorPromise`, `store.js:2489`)
- [X] T020 [US2] Добавить кнопку «Синхронизировать аудио-потомков (Custom Function)» + метод `syncAudioParents()` + модалку `CustomConfirm` в `webvue3/src/views/HomeView.vue` (по образцу `customFunction`, `HomeView.vue:140-146,865-888`); учесть `"ALREADY_RUNNING"` (warning-тост)
- [X] T021 [US2] Обновить `webvue3` JSDoc/`@see docs/features/sync-audio-descendants.md` (FR-006) в `store.js`/`HomeView.vue`
- [ ] T022 [US2] Ручная валидация quickstart-сценария 7

**Checkpoint**: US1 и US2 работают; админ-функция идёт через ту же очередь/лок.

---

## Phase 5: User Story 3 — Безопасность (без каскадов, гонок, порчи процессов) (Priority: P3)

**Goal**: механизм не зацикливается, не срабатывает на фоновые сохранения, не ломает активные процессы.

**Independent Test**: quickstart-сценарий 8.

### Implementation for User Story 3

- [X] T023 [US3] Проверить и закрыть подавление каскада: `inSync.set(true)` вокруг `syncAudioDescendantFromParent` (в `SyncAudioDescendants.kt`), гарантировать `finally { inSync.remove() }`
- [X] T024 [US3] Unit-тест реентерабельности: вызов `onSongSaved` при `inSync=true` не ставит в очередь (добавить в `SyncAudioDescendantsTest.kt`)
- [X] T025 [US3] Unit-тест: `shouldEnqueue` возвращает false при `changedFields` без контента и статусе ≥5 без перехода (анти-ложный запуск фоновых saveToDb)
- [X] T026 [US3] Unit-тест skip активного процесса (мок `hasActiveProcess` = true → `skipped: active_process`), если достижимо без БД; иначе — покрыть manual quickstart-сценарием 6
- [ ] T027 [US3] Ручная валидация quickstart-сценария 8

**Checkpoint**: все три истории функциональны и изолированы.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 [P] Создать `knowledge/domains/catalog/components/audio-descendant-sync.md` (компонент: гейт, очередь, перенос, статус 5, admin-функция) и добавить ссылку в `knowledge/domains/catalog/domain.md`
- [X] T029 [P] Обновить `knowledge/domains/catalog/components/song-lifecycle.md` (статус 5 как цель синхронизации потомков)
- [X] T030 [P] Обновить `knowledge/domains/catalog/components/song-entity.md` (хук saveToDb/saveToDbLocked → SyncAudioDescendants)
- [X] T031 [P] Обновить `knowledge/domains/catalog/components/dictionaries.md` (заметка о статусных воротах ≥5 и целевом 5 — требование `.ssot-map.yml`)
- [X] T032 [P] Создать per-feature документ `docs/features/sync-audio-descendants.md` по контракту `contracts/per-feature-doc.md` (6 секций) — FR-009
- [X] T033 Проверить, что `tools/check-feature-doc.sh docs/features/sync-audio-descendants.md` зелёный
- [X] T034 Прогнать compile: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
- [X] T035 Прогнать ktlint: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` (+ при необходимости `:karaoke-app:ktlintCheck`)
- [X] T036 Прогнать unit-тесты `karaoke-app`: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*SyncAudioDescendantsTest*'`
- [X] T037 Frontend lint/build: `cd webvue3 && npm run lint && npm run build` (workdir `webvue3`)
- [X] T038 `python3 tools/lint-knowledge.py` + `python3 tools/check-ssot-impact.py`
- [X] T039 Собрать `karaoke-app` jar: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar`
- [ ] T040 Открыть PR из ветки `413-sync-audio-descendants` в `master`, дождаться CI 7/7 (без `--delete-branch`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → без зависимостей.
- **Foundational (Phase 2)** → после Setup; **блокирует все истории**.
- **US1 (Phase 3)** → после Phase 2. MVP.
- **US2 (Phase 4)** → после Phase 2; использует `syncAll`/очередь из Phase 2. Может
  делаться параллельно с US1 (разные файлы: backend vs frontend), но SSE-сводка
  зависит от воркера (T005).
- **US3 (Phase 5)** → в основном верификация Phase 2 — выполняется после US1/US2.
- **Polish (Phase 6)** → после всех историй.

### Within Each User Story

- Setup/Foundational → US1 hook → US1 tests.
- Модели/механика (Phase 2) → endpoints (US2) → frontend (US2).

### Parallel Opportunities

- T003/T004 параллельны (разные функции одного файла — осторожно, один файл:
  фактически последовательно; [P] относится к независимым файлам).
- T014/T015 (тесты) параллельны.
- T017/T018/T019 параллельны (разные файлы).
- T028-T032 (knowledge/docs) параллельны.

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 + Phase 2 (механика).
2. Phase 3 (хук + тесты) → ручная валидация quickstart 1-6.
3. STOP: это уже даёт ценность #141.

### Incremental Delivery

1. MVP (US1) → проверка.
2. US2 (админ-кнопка) → проверка.
3. US3 (безопасность) → проверка.
4. Polish (knowledge/docs/CI/PR).

---

## Notes

- Тесты — unit-уровня (гейт/очередь); интеграционные в проекте `@Disabled`,
  поэтому e2e — через quickstart на admin-машине.
- Все gradle-команды — с `GRADLE_USER_HOME`; frontend — из `webvue3/`.
- `karaoke-app` на nsa-i9 можно пересобирать (bootJar), контейнер перезапускает
  владелец.
- Не коммитить напрямую в `master`; merge — через PR/CI.

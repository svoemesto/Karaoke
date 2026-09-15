# Tasks: 126 — Поиск тональности из существующего `[key].json`

**Branch**: `126-key-from-file` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Input**: Design documents from `/specs/126-key-from-file/`

**Format**: `- [ ] [TaskID] [P?] [Story?] Description with file path`

## Phase 1: Setup

- [X] T001 Verify feature branch is current (`git branch --show-current` → `126-key-from-file`).
- [X] T002 Read `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` строки 499-505, 561-565, 686-689, 1879-1925 (понимание `pathToFileKeyBpmFinder`, `getKeyBpmFromFile`, `argsKeyBpmFinder`).

## Phase 2: Foundational

- [X] T003 Add helper `applyKeyBpmFromFileIfExists(database: KaraokeConnection): Boolean` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (после метода `getKeyBpmFromFile`, около строки 1895). Реализация:
  - Если `File(pathToFileKeyBpmFinder).exists()` → читаем файл, парсим через `Json.decodeFromString(AudioAnalysisResult.serializer(), text)`.
  - Если `data.key != null` И `data.bpm != null` → применяем к `fields[SongField.KEY]` и `fields[SongField.BPM]`, сохраняем через `saveToDbLocked()`, логируем через `infra.cache.keybpm` (использовать `companionLog` или локальный logger по аналогии с `HealthReportWaitingCount`), возвращаем `true`.
  - Иначе → возвращаем `false`.
  - Обработка исключений (`IOException`, `SerializationException`) → логируем WARN, возвращаем `false`.

## Phase 3: User Story 1 — HealthReport применяет key/bpm из файла (точку 1)

**Story goal**: В HealthReport.solutionActions для CONSISTENCY_VIOLATION «У песни отсутствует тональность» — перед созданием KaraokeProcess проверять файл [key].json и применять key/bpm напрямую.

**Independent test**: 
1. Создать тестовую песню с пустым `song.key` и валидным `[key].json`.
2. Запустить HealthReport.solutionActions.
3. Убедиться, что `song.key` обновился, **никакой** KaraokeProcess не создан.

### Tests for User Story 1

- [X] T004 [P] [US1] Create `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KeyBpmFromFileCacheTest.kt` с 4 unit-тестами:
  - `applyKeyBpmFromFileIfExists returns true when file exists and valid` — создать временный файл с `{"key":"Am","bpm":120}`, вызвать helper, проверить `song.key == "Am"`, `song.bpm == 120`, возврат `true`.
  - `applyKeyBpmFromFileIfExists returns false when file missing` — нет файла, возврат `false`, ничего не меняется.
  - `applyKeyBpmFromFileIfExists returns false when file has null fields` — файл `{"key":null,"bpm":null}`, возврат `false`.
  - `applyKeyBpmFromFileIfExists returns false when file is invalid JSON` — файл `not-json{{`, возврат `false`.

### Implementation for User Story 1

- [X] T005 [US1] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` около строки 1407 (в `solutionActions` для CONSISTENCY_VIOLATION). **Перед** `KaraokeProcess.createProcess(...)` добавить:
  ```kotlin
  // specs/126-key-from-file (#126, OpenProject): проверка [key].json — если
  // файл уже есть, применить key/bpm без docker-прогона.
  if (song.applyKeyBpmFromFileIfExists(database)) {
      result.add(
          HealthReport(
              healthReportType = CONSISTENCY_VIOLATION,
              song = song,
              healthReportStatus = OK,
              canResolve = false,
              problemText = "Тональность была применена из существующего файла",
              solutionText = "Файл <songFile> [key].json уже содержал результат keybpmfinder",
          ),
      )
  } else {
      // старое поведение: создать KaraokeProcess
      result.add(HealthReport(...))  // существующий код
  }
  ```

- [X] T006 [US1] Запустить `./gradlew :karaoke-app:test --tests "KeyBpmFromFileCacheTest" --no-daemon`. Ожидаемый результат: 4/4 PASS.

## Phase 4: User Story 2 — KaraokeProcess.createProcess применяет key/bpm (точка 2)

**Story goal**: В `KaraokeProcess.createProcess` для типа `KEY_BPM_FROM_FILE` — **перед** записью процесса в БД проверить наличие валидного `[key].json`. Если файл есть — НЕ создавать процесс (вернуть `0`), `song.key`/`song.bpm` уже применены helper'ом.

**Архитектурное решение** (см. research.md D-005): проверка в `createProcess` **лучше**, чем в `prepareContext`, потому что:
- `prepareContext` вызывается worker'ом **после** создания процесса, а worker ожидает `args[0][0] == "runFunctionWithArgs"` — пустой `args` упадёт с IndexOutOfBounds.
- В `createProcess` один атомарный `check-then-create` (single-flight + saveToDbLocked).
- Покрывает все call-sites одним куском.

**Independent test**:
1. Создать KaraokeProcess с типом KEY_BPM_FROM_FILE для песни с валидным [key].json.
2. Проверить, что `song.key`/`song.bpm` обновлены, никакого `KaraokeProcess` в БД не создано.

### Implementation for User Story 2

- [X] T007 [US2] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` в `createProcess(...)` (около строки 1037, **сразу после** `if (wasWorking) return 0`):
  ```kotlin
  // specs/126-key-from-file (#126, OpenProject): если файл уже есть, не создаём процесс.
  if (action == KaraokeProcessTypes.KEY_BPM_FROM_FILE && song.applyKeyBpmFromFileIfExists()) {
      return 0
  }
  ```

- [X] T008 [US2] Verify `WORKING_DATABASE` доступен из `Song.applyKeyBpmFromFileIfExists()` (он вызывает `saveToDbLocked()` без параметров — должно работать). Покрыто T003.

## Phase 5: Knowledge SSoT

- [X] T009 [P] Create `knowledge/domains/processing/components/key-bpm-from-file.md` — Living Docs:
  - **Назначение**: алгоритм «проверка файла → если есть, применить; иначе docker».
  - **Файл**: `<songFile> [key].json` (см. `Song.pathToFileKeyBpmFinder`).
  - **Формат**: `AudioAnalysisResult { key: String?, bpm: Int? }`.
  - **Helper**: `Song.applyKeyBpmFromFileIfExists(): Boolean` — единая точка проверки.
  - **Pure-helper**: `Song.parseKeyBpmFileOrNull(filePath): AudioAnalysisResult?` — для unit-тестов без БД.
  - **Call-sites**:
    - `HealthReport.solutionActions` (точка 1).
    - `KaraokeProcess.createProcess` (точка 2).
  - **Race-safety**: `saveToDbLocked()`.
  - **Связанные ADR**: нет.
  - **Тесты**: `KeyBpmFromFileCacheTest` (4 теста).
  - **История**: Pass 401.

## Phase 6: Polish & Cross-Cutting

- [X] T010 Запустить `./gradlew :karaoke-app:ktlintCheck` — PASS.
- [X] T011 Запустить `./gradlew :karaoke-app:test --tests "KeyBpmFromFileCacheTest"` — 4/4 PASS.
- [X] T012 Запустить `./gradlew :karaoke-app:compileKotlin` — PASS.
- [X] T013 Запустить `tools/check-no-jpa-imports.sh` — PASS (без новых violations).
- [X] T014 Запустить `tools/check-no-mp4-mentions.sh` — PASS (без новых violations).
- [ ] T015 Git commit + push + PR (см. AGENTS.md § "CI-gate для master").
- [ ] T016 OpenProject: add-comment 126 → mark-review 126.

## Dependencies & Execution Order

```
Phase 1 (Setup)        → T001, T002
Phase 2 (Foundational) → T003 (нужен для US1 и US2)
Phase 3 (US1)          → T004 (tests, parallel) || T005 (impl, sequential after T003)
                       → T006 (verify tests)
Phase 4 (US2)          → T007, T008 (parallel после T003, но sequential между собой)
Phase 5 (Knowledge)    → T009 (parallel с US2)
Phase 6 (Polish)       → T010..T016 (sequential)
```

## Parallel Opportunities

- T004 [P] (тесты) можно делать параллельно с T005 (импл), но T006 зависит от обоих.
- T009 [P] (Knowledge) можно делать параллельно с US2.
- T010, T011, T012, T013, T014 — все независимы (можно параллельно).

## Independent Test Criteria (per Story)

- **US1**: тесты T004 + T006 PASS; ручная проверка через HealthReport UI.
- **US2**: ручная проверка через прямой вызов `KaraokeProcess.createProcess(action = KEY_BPM_FROM_FILE, ...)` для песни с валидным `[key].json]`.

## MVP Scope

**MVP = Phase 2 + Phase 3** (helper + US1 HealthReport). Точка 2 (US2) — nice-to-have, может быть отложена.

## File Paths Summary

**Modify**:
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (T003)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` (T005)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` (T007, T008)

**Create**:
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KeyBpmFromFileCacheTest.kt` (T004)
- `knowledge/domains/processing/components/key-bpm-from-file.md` (T009)
- `specs/126-key-from-file/report.md` (T016)

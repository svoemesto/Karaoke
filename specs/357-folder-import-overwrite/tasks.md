---
description: "Task list for spec 357 — Folder Import Overwrite (Audit #73)"
---

# Tasks: 357 — Folder Import Overwrite (Audit #73)

**Input**: Design documents from `/specs/357-folder-import-overwrite/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md (required — аудит 49 мест), data-model.md, contracts/

**Tests**: Manual tests из `contracts/manual-test-checklist.md` + 6 сценариев из `quickstart.md`. Unit-тесты НЕ добавляются (Q2 — `saveToDbLocked()` уже покрыт тестами спеки 299).

**Organization**: Задачи сгруппированы по user story. Все фиксы — в одном PR (Q1 Resolved).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, нет зависимостей).
- **[Story]**: User story из spec.md (US1, US2, US3, US4).
- Включать точные file paths в описания.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка инструментов и проверка baseline.

- [ ] T001 [P] Проверить baseline — backend compile: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel` из `/home/nsa/Karaoke`
- [ ] T002 [P] Проверить baseline линтеров: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` из `/home/nsa/Karaoke`
- [ ] T003 Подтвердить что спека 299 смержена в master (PR #395): `git log --oneline | grep "299\|FOR NO KEY UPDATE"` — иначе `saveToDbLocked()` не существует

**Checkpoint**: baseline зелёный, `saveToDbLocked()` доступен.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Базовые изменения, которые блокируют все user stories — WARN-логирование в `saveToDbLocked()`.

**⚠️ CRITICAL**: Без этой фазы US4 не может быть реализована (FR-160).

- [ ] T004 Добавить WARN-лог `song.locked_save_diff_overlap` в `Song.saveToDbLocked()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — после reload из БД, перед `getDiff()`: для каждого поля с `value != savedValue && value != originalValue` пишется WARN с `songId`, `field`, `oldInMemory`, `newInDb`. Формат — см. `karaoke-app/specs/357-folder-import-overwrite/contracts/log-format.md`
- [ ] T005 [P] Добавить KDoc на новую секцию WARN-логирования в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` с `@see specs/357` и ссылкой на FR-160, FR-170

**Checkpoint**: WARN-лог готов, можно компилировать.

---

## Phase 3: User Story 1 — Правка `author` через SongEdit во время импорта сохраняется (Priority: P1) 🎯 MVP

**Goal**: Гарантировать, что ручная правка `author` (или любого поля) через `SongEdit.vue` во время импорта файлов из папки НЕ перезатирается фоновыми процессами.

**Independent Test**: Импортировать папку из 3-5 песен → через 30 секунд через `SongEdit.vue` поменять `author` у 2 песен → дождаться завершения всех процессов → проверить SQL: `SELECT author FROM tbl_songs WHERE id IN (...)` → ручное значение сохранено.

### Implementation for User Story 1

#### `model/Song.kt` — 4 места B1

- [ ] T006 [US1] Перевести `model/Song.kt:3710` (truncateVoicesTo) на `saveToDbLocked()` + KDoc со ссылкой на `specs/357`
- [ ] T007 [US1] Перевести `model/Song.kt:6681` (setNewVersion loop) на `saveToDbLocked()` + KDoc
- [ ] T008 [US1] Перевести `model/Song.kt:8551` (loadListFromDb filter save) на `saveToDbLocked()` + KDoc
- [ ] T009 [US1] Перевести `model/Song.kt:8568` (loadListFromDb filter save fallback) на `saveToDbLocked()` + KDoc
- [ ] T010 [US1] Перевести `model/Song.kt:8738` (renameFilesIfDiff) на `saveToDbLocked()` + KDoc

#### `Utils.kt` — 11 мест B1

- [ ] T011 [P] [US1] Перевести `Utils.kt:173` (findDuplicateOriginal loop) на `saveToDbLocked()` + KDoc
- [ ] T012 [P] [US1] Перевести `Utils.kt:341` (autoAssignOriginalByWaveform) на `saveToDbLocked()` + KDoc
- [ ] T013 [P] [US1] Перевести `Utils.kt:668` (applyFoundLyricsIfMissing) на `saveToDbLocked()` + KDoc
- [ ] T014 [P] [US1] Перевести `Utils.kt:1596` (applyFamilySongSelection auto) на `saveToDbLocked()` + KDoc
- [ ] T015 [P] [US1] Перевести `Utils.kt:1626` (applyFamilySongSelection fallback) на `saveToDbLocked()` + KDoc
- [ ] T016 [P] [US1] Перевести `Utils.kt:1707` (applyAudioParentMarkers) на `saveToDbLocked()` + KDoc
- [ ] T017 [P] [US1] Перевести `Utils.kt:1736` (applyAudioParentMarkers fallback) на `saveToDbLocked()` + KDoc
- [ ] T018 [P] [US1] Перевести `Utils.kt:1739` (applyAudioParentMarkers loop) на `saveToDbLocked()` + KDoc
- [ ] T019 [P] [US1] Перевести `Utils.kt:4126` (setNewVersion) на `saveToDbLocked()` + KDoc
- [ ] T020 [P] [US1] Перевести `Utils.kt:4186` (setNewVersion fallback) на `saveToDbLocked()` + KDoc
- [ ] T021 [P] [US1] Перевести `Utils.kt:4639` (autoAssignOriginalByWaveform searchAllByAuthor) на `saveToDbLocked()` + KDoc

#### `controllers/ApiController.kt` — 5 мест B1

- [ ] T022 [P] [US1] Перевести `controllers/ApiController.kt:889` (applyFoundLyricsIfMissing HTTP) на `saveToDbLocked()` + KDoc
- [ ] T023 [P] [US1] Перевести `controllers/ApiController.kt:910` (applyFoundLyricsIfMissing HTTP fallback) на `saveToDbLocked()` + KDoc
- [ ] T024 [P] [US1] Перевести `controllers/ApiController.kt:5370` (findAudioParentByWaveform HTTP) на `saveToDbLocked()` + KDoc
- [ ] T025 [P] [US1] Перевести `controllers/ApiController.kt:7856` (applyFamilySongSelection HTTP) на `saveToDbLocked()` + KDoc
- [ ] T026 [P] [US1] Перевести `controllers/ApiController.kt:7861` (applyFamilySongSelection HTTP fallback) на `saveToDbLocked()` + KDoc

#### `controllers/MainController.kt` — 2 места B1

- [ ] T027 [P] [US1] Перевести `controllers/MainController.kt:184` (setNewVersion-loop) на `saveToDbLocked()` + KDoc
- [ ] T028 [P] [US1] Перевести `controllers/MainController.kt:1640` (ML/ffmpeg в той же функции) на `saveToDbLocked()` + KDoc

**Checkpoint**: US1 полностью функциональна — все 30 мест категории B1 защищены.

---

## Phase 4: User Story 2 — Правка любых полей песни во время импорта сохраняется (Priority: P1)

**Goal**: То же, что US1, но для любого из ~150 полей песни (не только `author`).

**Independent Test**: Повторить US1 для каждого из 7+ горячих путей с разными полями (`song_name`, `album`, `year`, `genre`, `id_status`). Финальное состояние: ручные правки сохранены, фоновые обновления применены.

### Implementation for User Story 2

> US2 **реализуется автоматически** через US1 — все 30 мест B1 покрывают ВСЕ поля через `getDiff()`. Никаких дополнительных задач не требуется. Independent test — повторить US1 с другими полями.

**Checkpoint**: US2 покрыта US1.

---

## Phase 5: User Story 3 — Аудит всех оставшихся мест `saveToDb()` в коде (Priority: P2)

**Goal**: Полный аудит с классификацией A/B/C и PR со всеми B1-местами.

**Independent Test**: `grep -rn '\.saveToDb()' karaoke-app/src/main/kotlin/ --include='*.kt' | grep -v 'saveToDbLocked'` → получить список. Каждое место классифицировано в `research.md`. Все B1 переведены на `saveToDbLocked()` (выполнено в Phase 3).

### Implementation for User Story 3

> US3 **уже реализована** через Phase 3 — все 30 мест B1 переведены, `research.md` содержит таблицу аудита. Никаких дополнительных задач не требуется. Independent test — перечитать `research.md` и убедиться, что список покрыт.

**Checkpoint**: US3 покрыта Phase 3 + `research.md`.

---

## Phase 6: User Story 4 — Диагностика при попытке перезатереть правку (Priority: P3)

**Goal**: WARN-лог `song.locked_save_diff_overlap` для операционной видимости.

**Independent Test**: Параллельное обновление (SongEdit + `saveToDbLocked`) → проверить, что в `infra.prod.ping` логе появилось WARN с `songId`, `field`, `oldInMemory`, `newInDb`.

### Implementation for User Story 4

> US4 **уже реализована** через Phase 2 (T004, T005) — WARN-лог добавлен в `saveToDbLocked()`. Independent test — запустить Сценарий 3 из `quickstart.md`.

**Checkpoint**: US4 покрыта Phase 2.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Финальные шаги — компиляция, линтеры, документация, governance.

- [ ] T029 [P] Backend compile после всех изменений: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
- [ ] T030 [P] Линтеры: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck`
- [ ] T031 [P] KDoc coverage: `bash tools/check-kdoc-coverage.sh --strict` — должен быть ≥ 50%
- [ ] T032 Обновить `knowledge/domains/catalog/components/song-entity.md` — раздел «Методы» дополнить описанием `saveToDbLocked()` как рекомендуемого пути для долгих процессов (race protection). Добавить ссылку на спеку 357.
- [ ] T033 [P] Добавить запись в `docs/architecture-notes.md` — «Pass 357: спека 357 — Folder Import Overwrite (Audit #73), 30 мест B1 переведены на `saveToDbLocked()`, добавлен WARN-лог `song.locked_save_diff_overlap`»
- [ ] T034 Создать `specs/357-folder-import-overwrite/report.md` — REQUIRED артефакт governance (см. AGENTS.md § Issue-tracker OpenProject). Содержание: сводка по 30 местам, manual test результаты, ссылка на PR.
- [ ] T035 [P] Создать commit с маркером `[tracker-claim-73]` — например: `git commit -m "[tracker-claim-73] Pass 357: 30 мест saveToDb() → saveToDbLocked() + WARN-лог diff_overlap (Audit #73)"`
- [ ] T036 [P] Push в feature-ветку: `git push -u origin 357-folder-import-overwrite`
- [ ] T037 [P] Создать PR через `gh pr create --base master` с заголовком «Pass 357: Folder Import Overwrite (Audit #73)» и описанием (резюме спеки + manual test checklist)
- [ ] T038 Дождаться CI 7/7 PASS (ktlint, ESLint+Prettier, KDoc, JSDoc, docs structure, baseline stats)
- [ ] T039 Выполнить manual test checklist (`contracts/manual-test-checklist.md` — 10 шагов). Результат — в Sign-off таблице.
- [ ] T040 Merge PR через `gh pr merge --merge` (БЕЗ `--delete-branch` — lifecycle ветки сохраняется, см. AGENTS.md § Git workflow)
- [ ] T041 [P] Опубликовать отчёт: `bash tools/tracker.sh add-comment 73 --file specs/357-folder-import-overwrite/report.md`
- [ ] T042 [P] Перевести в review: `bash tools/tracker.sh mark-review 73`
- [ ] T043 [P] После ревью владельцем: `bash tools/tracker.sh close-issue 73`

**Checkpoint**: PR смержен, OpenProject #73 в `In review` (или `Closed`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей, начинается сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 (baseline зелёный). **Блокирует** Phase 3 (US1).
- **Phase 3 (US1)**: зависит от Phase 2 (WARN-лог готов). **Блокирует** Phase 7 (Polish).
- **Phase 4-6 (US2, US3, US4)**: нет собственных задач — покрыты Phase 2 и Phase 3.
- **Phase 7 (Polish)**: зависит от Phase 3 (все 30 мест B1 переведены). Завершает pipeline.

### User Story Dependencies

- **US1 (P1)**: после Phase 2. Нет зависимостей от других stories. **MVP**.
- **US2 (P1)**: покрыта US1.
- **US3 (P2)**: покрыта Phase 3 (аудит + 30 мест).
- **US4 (P3)**: покрыта Phase 2 (WARN-лог).

### Within Each Phase

- **Phase 3**: 30 переводов — все в разных строках разных файлов, можно делать параллельно (но реально один PR = последовательные коммиты).
- **Phase 7**: линтеры и компиляция ПОСЛЕ всех переводов; governance шаги ПОСЛЕ merge.

### Parallel Opportunities

- **Phase 3**: T011..T028 — все `[P]` (разные файлы или разные места в одном файле, нет runtime-зависимостей). Реально один PR = один коммит (или группа коммитов по файлам).
- **Phase 7**: T029..T032, T041..T043 — все `[P]` (линтеры параллельно, документация параллельно, governance параллельно).

---

## Parallel Example: User Story 1

```bash
# Все 30 переводов — в одном PR. Можно делать параллельно по файлам:
# Группа 1 (model/Song.kt):
Task: "Перевести model/Song.kt:3710 на saveToDbLocked() + KDoc"
Task: "Перевести model/Song.kt:6681 на saveToDbLocked() + KDoc"
Task: "Перевести model/Song.kt:8551 на saveToDbLocked() + KDoc"
Task: "Перевести model/Song.kt:8568 на saveToDbLocked() + KDoc"
Task: "Перевести model/Song.kt:8738 на saveToDbLocked() + KDoc"

# Группа 2 (Utils.kt):
Task: "Перевести Utils.kt:173..4639 (11 мест) на saveToDbLocked() + KDoc"

# Группа 3 (controllers + services + HealthReport):
Task: "Перевести controllers/ApiController.kt:5 мест"
Task: "Перевести controllers/MainController.kt:2 места"
Task: "Перевести services/VkAutoPublishService.kt:2 места"
# ... и т.д.
```

---

## Implementation Strategy

### MVP First (Phase 1 → 3)

1. ✅ Phase 1 (Setup) — baseline зелёный.
2. ✅ Phase 2 (Foundational) — WARN-лог готов.
3. ✅ Phase 3 (US1) — все 30 мест B1 переведены.
4. **STOP и VALIDATE**: Manual test из `quickstart.md` Сценарий 1 (US1) → SQL проверить `author`.
5. Phase 7 (Polish) — компиляция, линтеры, governance, merge.

### Incremental Delivery

Один большой PR (Q1 Resolved). Все 30 мест B1 + WARN-лог = один момент времени, баг полностью закрыт.

### Parallel Team Strategy

С одним разработчиком: Phase 3 — последовательно по файлам (Song.kt → Utils.kt → controllers → services → HealthReport).

---

## Notes

- [P] задачи = разные файлы или разные места, нет runtime-зависимостей.
- Все задачи US1..US4 имеют чёткие file paths и описания.
- Manual test обязателен перед merge (T039) — quickstart.md Сценарии 1, 2, 6.
- Governance: claim → add-comment → mark-review → close (T034, T041, T042, T043) — NON-NEGOTIABLE.
- Всего задач: **43**. Из них `[P]`: **~30**. Реально один PR.

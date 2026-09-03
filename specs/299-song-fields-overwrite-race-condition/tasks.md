---
description: "Task list for spec 299 — перезатирание полей песни при фоновой обработке"
---

# Tasks: 299 — Перезатирание полей песни при фоновой обработке

**Input**: Design documents from `/specs/299-song-fields-overwrite-race-condition/`
- [`spec.md`](./spec.md) — US1-US4, FR-001..FR-060, SC-001..SC-008, Clarifications Session 2026-09-03
- [`plan.md`](./plan.md) — Tech stack, Constitution Check ✅, Project Structure
- [`research.md`](./research.md) — Phase 0 research R1-R7
- [`data-model.md`](./data-model.md) — Phase 1 data-model
- [`contracts/manual-test-checklist.md`](./contracts/manual-test-checklist.md) — manual test (5 шагов)
- [`quickstart.md`](./quickstart.md) — dev-машина + smoke test

**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), contracts/ (✅)

**Tests**: В проекте нет автотестов для `Song.saveToDb()` (`karaoke-app/src/test` — `@Disabled`, см. Constitution §II). Tests OPTIONAL — пропущены. Проверка — manual через `contracts/manual-test-checklist.md`.

**Organization**: Tasks grouped by user story (P1 → P2 → P3) для независимой имплементации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно параллельно (разные файлы, нет зависимостей)
- **[Story]**: User story, к которой относится задача (US1, US2, US3, US4)
- Указывать точные пути файлов

## Path Conventions

- Backend Kotlin: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- LiveDocs: `livedocs/features/`, `livedocs/architecture/`
- Логи прода: `docs/ops/log-correlation.md`
- KaraokeProperties: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`

---

## Phase 1: Setup (Базовая инфраструктура)

**Цель**: Создать базовые методы и настройки, необходимые всем user stories.

- [ ] T001 Добавить поле `songSaveLockedTimeoutMs` (Long, default 5000) в `KaraokeProperties.kt:listKaraokeProperties` с KDoc (см. `data-model.md` «Новое поле: songSaveLockedTimeoutMs»)
- [ ] T002 Реализовать `Song.Companion.loadFromDbByIdForUpdate(id, database, storageService, storageApiClient, connection): Song?` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — выполняет `SELECT * FROM tbl_songs WHERE id = ? FOR NO KEY UPDATE` на уже открытой транзакции (см. `data-model.md` сигнатура, `research.md` R1, R3)
- [ ] T003 Реализовать `Song.saveToDbLocked(): Boolean` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — обёртка с autoCommit flip + `SET LOCAL lock_timeout` + loadFromDbByIdForUpdate + getDiff + ps.executeUpdate + commit, fallback `?: song` + WARN при null (см. `data-model.md` сигнатура, `research.md` R2, Clarifications Q2, Q4)

**Checkpoint**: Phase 1 готова — можно начинать user stories.

---

## Phase 2: Foundational (Логирование и документация)

**Цель**: Общая инфраструктура (логирование для мониторинга прода), MUST be complete before user stories.

- [ ] T004 Добавить новые WARN-маркеры (`song.locked_save_fallback`, `song.locked_save_failed`, `song.lock_timeout`) в `docs/ops/log-correlation.md` — формат, grep-команды, описание (см. `data-model.md` «Лог-маркеры»)

**Checkpoint**: Phase 2 готова — user stories могут использовать WARN-маркеры.

---

## Phase 3: User Story 1 — Поиск текстов не перезатирает название (Priority: P1) 🎯 MVP

**Goal**: Импорт папки + параллельная правка `songName` через SongEdit + завершение поиска текстов = `songName` сохранён, `source_text` обновлён.

**Independent Test**: 
- Создать тестовую песню со `songName='ПММЛ'`, `source_text=''`, `id_status=0`.
- Запустить `applyFoundLyricsIfMissing(song, listOf("Найденный текст"))`.
- Параллельно из другого потока сделать `UPDATE tbl_songs SET song_name = 'П.М.М.Л.' WHERE id = songId` через 100мс после старта.
- Через 1 сек проверить: `song_name='П.М.М.Л.'`, `source_text='Найденный текст'`, `id_status=1`.

### Implementation for User Story 1

- [ ] T005 [US1] Изменить `applyFoundLyricsIfMissing` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:144` — заменить `loadFromDbById` + `songToSave.saveToDb()` на `song.saveToDbLocked()` (см. `data-model.md` «Изменения в горячих путях», FR-010)
- [ ] T006 [US1] [P] Smoke test на dev-машине: `quickstart.md` Шаг 5 (см. также `contracts/manual-test-checklist.md` Шаг 3)

**Checkpoint**: US1 fully functional. Smoke test прошёл — `song.saveToDbLocked()` корректно блокирует строку через `FOR NO KEY UPDATE`.

---

## Phase 4: User Story 2 — Все фоновые процессы не перезатирают (Priority: P1)

**Goal**: Pass 281 hot paths (5 мест) + FR-020 (25+ мест) переведены на `saveToDbLocked()` или имеют явное KDoc-обоснование «не горячий путь».

**Independent Test**: 
- Повторить US1 для каждого из hot paths (5 Pass 281 + 25+ FR-020).
- Smoke test: импорт папки + одновременный запуск `KEY_BPM_FROM_FILE`/`DEMUCS2`/`Sheetsage` + ручная правка через SongEdit.
- Финальное состояние: ручные правки сохранены, фоновые обновления тоже применены.

### Implementation for User Story 2 — Pass 281 hot paths

- [ ] T007 [US2] Изменить `applyDuplicateOriginal` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4847` — заменить `loadFromDbById` + `songToSave.saveToDb()` на `songToSave.saveToDbLocked()` (Pass 278, FR-011 спеки 299). Sync-блок (newSong.X = ...) сохраняется.
- [ ] T008 [US2] Изменить `applyAudioParentMarkers` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4897` — заменить `loadFromDbById` + `songToSave.saveToDb()` на `songToSave.saveToDbLocked()` (Pass 278, FR-012 спеки 299). Sync-блок сохраняется.
- [ ] T009 [US2] Изменить `applyFamilySongSelection` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4939` — заменить `loadFromDbById` + `songToSave.saveToDb()` на `songToSave.saveToDbLocked()` (Pass 281, FR-013 спеки 299). Sync-блок сохраняется.
- [ ] T010 [US2] Изменить `autoAssignOriginalByWaveform` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:5104` — оба reload'а (`finalSongToSave` + внутри `applyFamilySongSelection`) использовать `saveToDbLocked()` (FR-014 спеки 299)
- [ ] T011 [US2] Изменить `findAudioParentByWaveform` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:5248` — все 4 reload'а (`songToSave` перед каждым из 4 `saveToDb`) использовать `saveToDbLocked()` (FR-015 спеки 299)
- [ ] T012 [US2] Изменить `Song.setSourceMarkers` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:3626` — оба `loadFromDbById` + `reloaded.saveToDb()` заменить на `reloaded.saveToDbLocked()` (FR-016 спеки 299)
- [ ] T013 [US2] Изменить `Song.setSourceText` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:3690` — оба `loadFromDbById` + `reloaded.saveToDb()` заменить на `reloaded.saveToDbLocked()` (FR-016 спеки 299)

### Implementation for User Story 2 — FR-020 hot paths (Telegram/VK публикация, рендер MP4)

- [ ] T014 [US2] [P] Изменить `TelegramAutoPublishService.kt:257` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc с race-сценарием
- [ ] T015 [US2] [P] Изменить `TelegramAutoPublishService.kt:293` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T016 [US2] [P] Изменить `TelegramAutoPublishService.kt:310` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T017 [US2] [P] Изменить `TelegramAutoPublishService.kt:327` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T018 [US2] [P] Изменить `VkAutoPublishService.kt:252` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T019 [US2] [P] Изменить `VkAutoPublishService.kt:358` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T020 [US2] [P] Изменить `VkAutoPublishService.kt:372` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T021 [US2] [P] Изменить `VkAutoPublishService.kt:468` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T022 [US2] [P] Изменить `VkAutoPublishService.kt:480` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T023 [US2] [P] Изменить `VkAutoPublishService.kt:535` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T024 [US2] [P] Изменить `KaraokeProcess.kt:408` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc с race-сценарием (рендер MP4, минуты)
- [ ] T025 [US2] [P] Изменить `KaraokeProcess.kt:415` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T026 [US2] [P] Изменить `KaraokeProcess.kt:422` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T027 [US2] [P] Изменить `KaraokeProcess.kt:429` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T028 [US2] [P] Изменить `KaraokeProcess.kt:436` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T029 [US2] [P] Изменить `SongReleaseAnnouncementService.kt:290` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T030 [US2] [P] Изменить `SongReleaseAnnouncementService.kt:437` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T031 [US2] [P] Изменить `PremiumAutoPublishScheduler.kt:289` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc
- [ ] T032 [US2] [P] Изменить `PremiumAutoPublishScheduler.kt:311` — `song.saveToDb()` → `song.saveToDbLocked()`, добавить KDoc

### Implementation for User Story 2 — FR-020 review for remaining places

- [ ] T033 [US2] Проверить и добавить KDoc-обоснования для НЕ горячих мест из FR-020 — каждое место в `Utils.kt:666, 4141, 4201, 4654`, `Song.kt:455, 738, 776, 5951, 6577, 8186, 8357, 8374, 8544`, `ApiController.kt:883, 904, 7014, 7043, 7099, 7125, 7907, 7912`, `MainController.kt:1631, 1804, 1993` — либо `saveToDbLocked()`, либо явный KDoc «объект живёт < 100мс, race не воспроизводится» (FR-021 спеки 299)
- [ ] T034 [US2] Smoke test на dev-машине для каждого изменённого hot path: запустить фон + параллельная правка, проверить что оба изменения применены

**Checkpoint**: US2 fully functional. Все hot paths из Pass 281 + FR-020 защищены. Не горячие места имеют KDoc-обоснование.

---

## Phase 5: User Story 3 — Pass 281 acceptance scenarios не сломаны (Priority: P2)

**Goal**: Регрессионная гарантия — Pass 281 (PR #395, в проде) продолжает работать после `FOR NO KEY UPDATE`.

**Independent Test**: 
- Повторить Pass 281 acceptance scenarios (см. `specs/281-find-lyrics-overwrites-key-bpm/spec.md` разделы «User Stories»):
  - Импорт папки из 3 файлов + параллельный `KEY_BPM_FROM_FILE` + поиск текстов → `key`/`bpm`/`source_text` все заполнены.
  - Модалка «Похожие версии» → клик по строке + параллельный `KEY_BPM_FROM_FILE` → выбор пользователя применён, `song_tone` не перезатёрт.
  - Апрув задания редактора (цикл `setSourceMarkers`/`setSourceText`) + параллельный `KEY_BPM_FROM_FILE` → маркеры сохранены, `key`/`bpm` не перезатёрты.

### Implementation for User Story 3

- [ ] T035 [US3] [P] Pass 281 acceptance scenario 1 (импорт папки + `KEY_BPM_FROM_FILE` + поиск текстов): проверить на dev-машине
- [ ] T036 [US3] [P] Pass 281 acceptance scenario 2 (модалка «Похожие версии» + `KEY_BPM_FROM_FILE`): проверить на dev-машине
- [ ] T037 [US3] [P] Pass 281 acceptance scenario 3 (апрув задания редактора + `KEY_BPM_FROM_FILE`): проверить на dev-машине
- [ ] T038 [US3] KDoc-обновление: убедиться, что FR-040/FR-041 выполнены — все затронутые функции имеют `@see specs/299-song-fields-overwrite-race-condition/spec.md` (см. `plan.md` Project Structure)

**Checkpoint**: US3 verified — Pass 281 acceptance scenarios проходят, регрессий нет.

---

## Phase 6: User Story 4 — Диагностика при попытке потерять правку (Priority: P3)

**Goal**: При попытке фонового сохранения перезатереть параллельно обновлённое поле система записывает WARN, а не молча перезатирает.

**Independent Test**: 
- Запустить saveToDbLocked с объектом `song`, у которого одно из полей уже отличается от `savedSong` (эмулировать параллельный UPDATE).
- Проверить WARN `song.overwrite_recovered` (или аналогичный) в логе с `songId`, именем поля, старым/новым значениями.

### Implementation for User Story 4

- [ ] T039 [US4] Реализовать пост-дифф-чек в `Song.saveToDbLocked()` (FR-050 спеки 299): если `getDiff(this, savedSong)` содержит поле, которое в `savedSong` отличается от `this` (т.е. параллельная транзакция успела обновить в БД ПОСЛЕ нашего reload) — записать WARN `song.overwrite_recovered` в `infra.prod.ping` лог с `songId`, именем поля, oldInMemory, newInDb
- [ ] T040 [US4] Smoke test: эмулировать параллельный UPDATE + проверить WARN в логе

**Checkpoint**: US4 verified — WARN-маркер появляется при эмуляции race, в обычном режиме — нет WARN.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Цель**: Документация, final checks, PR.

- [ ] T041 [P] Обновить `livedocs/features/299-song-fields-overwrite-race-condition.md` — создать новый per-feature документ (FR-009 спеки, если правлю код этой фичи). Использовать шаблон `livedocs/templates/feature-summary.md`. Содержимое: что делает фича, где в коде, как тестировать.
- [ ] T042 [P] Обновить `livedocs/INDEX.md` — добавить строку для `299-song-fields-overwrite-race-condition` в таблицу SDD-слоя (`livedocs/features/`).
- [ ] T043 [P] Обновить `livedocs/CHANGELOG.md` (Pass 295+) — запись о создании спеки 299 и реализации.
- [ ] T044 Запустить pre-commit 7/7 проверок (см. AGENTS.md «Перед каждым git commit»):
  ```bash
  ./gradlew ktlintCheck
  bash tools/check-kdoc-coverage.sh --strict
  cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
  cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
  bash tools/check-livedocs-structure.sh
  bash tools/check-livedocs-cross-links.sh
  bash tools/check-livedocs-external-links.sh
  pre-commit run --all-files
  ```
- [ ] T045 Запустить полный manual test checklist (`contracts/manual-test-checklist.md`) — 5 шагов на dev-машине, заполнить Sign-off таблицу. Все шаги должны быть `pass`.
- [ ] T046 Сборка бэка: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` (см. AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»)
- [ ] T047 Push ветки и создание PR: `git push -u origin 299-song-fields-overwrite-race-condition && gh pr create --base master` (см. AGENTS.md «CI-gate для master»)
- [ ] T048 Дождаться CI 7/7: `gh pr checks` — все проверки должны быть зелёными
- [ ] T049 Merge PR: `gh pr merge --merge` (БЕЗ `--delete-branch`, см. AGENTS.md)
- [ ] T050 OpenProject WP #49: `tools/tracker.sh mark-review 49` + `close-issue 49` — после успешного деплоя

**Checkpoint**: PR смержен в master, OpenProject WP #49 закрыт.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Нет зависимостей — можно начать сразу.
- **Phase 2 (Foundational)**: Зависит от Phase 1 — BLOCKS все user stories.
- **Phase 3 (US1)**: Зависит от Phase 1, Phase 2.
- **Phase 4 (US2)**: Зависит от Phase 1, Phase 2, Phase 3.
- **Phase 5 (US3)**: Зависит от Phase 1, Phase 2, Phase 3, Phase 4.
- **Phase 6 (US4)**: Зависит от Phase 1, Phase 2, Phase 3.
- **Phase 7 (Polish)**: Зависит от всех предыдущих фаз.

### User Story Dependencies

- **US1 (P1)**: Может начаться после Phase 1 (Setup). Нет зависимостей от других stories.
- **US2 (P1)**: Зависит от US1 (та же инфраструктура, тот же smoke test). Можно начать параллельно, если staffing позволяет.
- **US3 (P2)**: Зависит от US1+US2 — это регрессионные проверки.
- **US4 (P3)**: Независима от US2, US3 — только от Phase 1.

### Within Each User Story

- Implementation → Smoke test (никаких автотестов в проекте).
- KDoc-обоснование → после lock-механизма (Phase 1).
- Pass 281 hot paths (T007..T013) — последовательно в `Utils.kt` и `Song.kt`, [P] не применяется (тот же файл).
- FR-020 hot paths (T014..T032) — `[P]` параллельно (разные файлы).
- US3 (T035..T037) — `[P]` параллельно (разные acceptance scenarios).

### Parallel Opportunities

- T014..T032 (FR-020 hot paths, разные файлы) — могут выполняться параллельно разными людьми.
- T035..T037 (Pass 281 acceptance scenarios, разные сценарии) — могут выполняться параллельно.
- T041..T043 (livedocs обновления) — `[P]` параллельно.
- T045..T046 (manual test + bootJar) — последовательно (test → build).

---

## Parallel Example: User Story 2 — FR-020 hot paths

```bash
# Все 19 задач FR-020 (T014..T032) могут выполняться параллельно — разные файлы:
Task T014: "TelegramAutoPublishService.kt:257"
Task T015: "TelegramAutoPublishService.kt:293"
Task T016: "TelegramAutoPublishService.kt:310"
Task T017: "TelegramAutoPublishService.kt:327"
Task T018: "VkAutoPublishService.kt:252"
# ... и т.д. — каждый разработчик берёт свой файл.
```

---

## Parallel Example: User Story 3 — Pass 281 regression

```bash
# 3 acceptance scenarios (T035..T037) параллельно:
Task T035: "Сценарий 1: импорт папки + KEY_BPM_FROM_FILE + поиск текстов"
Task T036: "Сценарий 2: модалка «Похожие версии» + KEY_BPM_FROM_FILE"
Task T037: "Сценарий 3: апрув задания редактора + KEY_BPM_FROM_FILE"
```

---

## Implementation Strategy

### MVP First (только User Story 1)

1. ✅ Phase 1: Setup (T001-T003) — 3 задачи, ~1 час.
2. ✅ Phase 2: Foundational (T004) — 1 задача, ~15 мин.
3. ✅ Phase 3: US1 (T005-T006) — 2 задачи, ~30 мин.
4. ⏸️ **STOP and VALIDATE**: Smoke test на dev-машине (quickstart.md Шаг 5).
5. ⏸️ Deploy/demo (опционально).

Минимальный жизнеспособный продукт = `applyFoundLyricsIfMissing` под `FOR NO KEY UPDATE`. Закрывает сценарий из задачи #49 буквально.

### Incremental Delivery

1. ✅ Phase 1: Setup (базовые методы).
2. ✅ Phase 2: Foundational (логирование).
3. ✅ Phase 3: US1 (`applyFoundLyricsIfMissing`) → smoke test → MVP deploy.
4. ✅ Phase 4: US2 (Pass 281 + FR-020 hot paths) → smoke test каждого → релиз.
5. ✅ Phase 5: US3 (Pass 281 regression checks) → smoke test → финальный релиз.
6. ✅ Phase 6: US4 (диагностика) → smoke test → финальный релиз.
7. ✅ Phase 7: Polish (livedocs + pre-commit + PR) → merge.

Каждая фаза добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С 3 разработчиками:
1. Все вместе: Phase 1 + Phase 2 (~1.5 часа).
2. После Phase 2:
   - Разработчик A: Phase 3 (US1) + Phase 5 (US3 — регрессия).
   - Разработчик B: Phase 4 (US2 — Pass 281 hot paths: T007-T013).
   - Разработчик C: Phase 4 (US2 — FR-020 hot paths: T014-T032) + Phase 6 (US4 — диагностика).
3. Все вместе: Phase 7 (Polish + PR).

---

## Notes

- [P] tasks = разные файлы, нет зависимостей.
- [Story] label привязывает задачу к user story для traceability.
- Каждая user story должна быть independently completable и testable.
- Тесты пропущены (проект не имеет автотестов, см. Constitution §II).
- Commit после каждой задачи или логической группы.
- Stop at any checkpoint to validate story independently.
- Избегать: vague tasks, same file conflicts, cross-story dependencies.
- **ВАЖНО:** `Song.saveToDb()` НЕ изменяется — обратная совместимость с 70+ мест вызова (FR-003 спеки 299).
- **ВАЖНО:** Pass 281 паттерн reload-from-db-before-save сохраняется как страховка поверх `FOR NO KEY UPDATE` (FR-040 спеки 299).
- **ВАЖНО:** Все новые методы должны иметь KDoc ≥ 50% coverage (CI gate, FR-006 спеки 299).
- **ВАЖНО:** `KaraokeProperties.songSaveLockedTimeoutMs` читается внутри `Song.saveToDbLocked()` через `KaraokeProperties.getLong(key).coerceAtLeast(1000L)`.
- На машине `nsa-i9` под `nsa` разрешено пересобирать `karaoke-app` без явного согласия (AGENTS.md Pass 282).

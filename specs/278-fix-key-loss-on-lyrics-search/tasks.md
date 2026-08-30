---
description: "Task list for feature 278-fix-key-loss-on-lyrics-search"
---

# Tasks: Fix Key/Tone Loss During Lyrics Search in Add-Files-From-Folder

**Input**: Design documents from `/specs/278-fix-key-loss-on-lyrics-search/`
- spec.md (required for user stories)
- plan.md (required)

**Tests**: Не запрошены — интеграционные тесты в `karaoke-app/src/test` помечены `@Disabled`, проверка делается пользователем вручную (Constitution §"Рабочий процесс").

**Organization**: Задачи сгруппированы по user story (US1 — P1 основная, US2 — P2 дополнительная для Demucs). Между US1 и US2 нет блокирующих зависимостей — реализуются последовательно одним PR.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, нет зависимостей)
- **[Story]**: К какой user story относится задача (US1, US2)
- Точные пути к файлам в описании

---

## Phase 1: Setup

**Purpose**: Подготовка инфраструктуры и проверка baseline

- [ ] T001 [P] Запустить `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` для baseline-проверки (текущее состояние должно собираться без ошибок)
- [ ] T002 [P] Запустить `./gradlew :karaoke-web:ktlintCheck` для baseline-проверки ktlint (фикс не должен вводить НОВЫХ нарушений baseline — Constitution FR-007)
- [ ] T003 [P] Прочитать текущий `docs/features/` и `archive/docs/features/` для поиска per-feature документа по импорту файлов / async-процессам (для Phase 4 T014)

**Checkpoint**: baseline собирается, ktlint OK, целевой документ найден.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Никаких блокирующих prerequisites — фикс затрагивает существующий код, новых абстракций/моделей не требуется.

**⚠️ CRITICAL**: Phase 2 пуста по сути — пропускается. Все user stories могут начинаться сразу после Phase 1.

---

## Phase 3: User Story 1 — Тональность не теряется при добавлении файлов из папки (Priority: P1) 🎯 MVP

**Goal**: После добавления файлов из папки через UI «Добавить файлы из папки» тональность (song_tone/song_bpm), найденная параллельным процессом KEY_BPM_FROM_FILE, НЕ теряется при синхронном поиске текста.

**Independent Test**: Добавить 3+ файлов из папки, дождаться KEY_BPM_FROM_FILE. После завершения всех операций проверить `tbl_songs`: для всех песен `song_tone`/`song_bpm` заполнены (если процесс нашёл) + `source_text` заполнен (если Яндекс нашёл). Повторный запуск KEY_BPM_FROM_FILE для этих песен НЕ требуется.

### Implementation for User Story 1

- [ ] T004 [US1] В файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` в методе `doCreateFromFolder` (строки 5390-5515) изменить блок после `findYandexSongLyrics` (строки 5461-5466): перед `newSong.saveToDb()` добавить перезагрузку объекта `Song` из БД через `Song.loadFromDbById(id = newSong.id, database = WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)` с fallback на `newSong` при null. Переприсвоить `sourceText` и `ID_STATUS` уже на перезагруженный объект.

- [ ] T005 [US1] В файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` в функции `applyDuplicateOriginal` (строки 4528-4541): перед `newSong.saveToDb()` (строка 4540) добавить перезагрузку объекта через `Song.loadFromDbById(id = newSong.id, database = newSong.database, storageService = newSong.storageService, storageApiClient = newSong.storageApiClient)` с fallback на `newSong`. Все мутации (`rootId`, `sourceText`, ...) выполнять на перезагруженном объекте.

- [ ] T006 [US1] В файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` в функции `applyAudioParentMarkers` (строки 4556-4569): перед `song.saveToDb()` (строка 4568) добавить перезагрузку объекта через `Song.loadFromDbById(id = song.id, database = song.database, storageService = song.storageService, storageApiClient = song.storageApiClient)` с fallback на `song`. Все мутации (`sourceText`, `resultText`, `sourceMarkers`, ...) выполнять на перезагруженном объекте. **ВАЖНО**: `deltaMs` рассчитывается от `song.ms`, заменить на `reloaded.ms` после reload.

**Checkpoint**: После T004-T006 компиляция должна проходить, ktlint baseline не должен расти. US1 покрыта — основная защита от race condition с KEY_BPM_FROM_FILE для 3 точек saveToDb.

---

## Phase 4: User Story 2 — Другие параллельные процессы (Demucs) не теряют свои результаты (Priority: P2)

**Goal**: Помимо KEY_BPM_FROM_FILE, Demucs (DEMUCS2) тоже ставится в очередь из `Song.createFromPath`. Архитектурно тот же подход уже покрывает и его (один и тот же reload-from-db перед saveToDb). US2 — это в первую очередь документация и per-feature обновление.

**Independent Test**: Запустить импорт папки, дождаться DEMUCS2 для одного из треков. После сохранения текста проверить `tbl_songs.audio_song`/`audio_music`/`audio_vocals` не сброшены.

### Implementation for User Story 2

- [ ] T007 [US2] (только документация) — добавление примечание в KDoc функций `applyDuplicateOriginal`, `applyAudioParentMarkers` и в комментарий к блоку `findYandexSongLyrics` в `ApiController.doCreateFromFolder`: явно описать race condition с KEY_BPM_FROM_FILE/DEMUCS2 и подход "reload-from-db-before-save" как защиту. Это документирует, что фикс покрывает оба процесса (FR-006 Constitution: KDoc обязателен).

**Checkpoint**: US2 покрыта — Demucs гонка защищена тем же фиксом, документация обновлена.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки и документация

- [ ] T008 Запустить `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — компиляция должна проходить без новых ошибок
- [ ] T009 Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких НОВЫХ нарушений baseline
- [ ] T010 Запустить `./gradlew :karaoke-web:bootJar --parallel` — bootJar собирается (AGENTS.md обязательная проверка)
- [ ] T011 [P] Обновить per-feature документ (найденный в T003) — добавить секцию о race condition с параллельными процессами и фиксе "reload-from-db-before-save". Если подходящего документа нет — создать новый в `archive/docs/features/` (т.к. фикс узкий и локальный, может быть достаточно короткого раздела в существующем документе про async-process-queue или admin-import).
- [ ] T012 [P] Проверить, что ни один секрет-файл не попадает в git (Constitution VIII.3): `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` должно вернуть пусто
- [ ] T013 Проверить `git diff --stat` — изменены только 2 ожидаемых файла (`ApiController.kt`, `Utils.kt`) и документация. Никаких случайных изменений

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Без зависимостей — стартует сразу. T001, T002, T003 могут выполняться параллельно.
- **Phase 2 (Foundational)**: Пустая — пропускается.
- **Phase 3 (US1)**: Зависит от Phase 1 (T001-T003 завершены для baseline).
- **Phase 4 (US2)**: Зависит от Phase 3 (комментарии ссылаются на тот же паттерн фикса).
- **Phase 5 (Polish)**: Зависит от Phase 3 и Phase 4.

### User Story Dependencies

- **US1 (P1)**: Самостоятельна после Phase 1.
- **US2 (P2)**: Зависит от US1 в части документации (комментарии в T007 ссылаются на тот же фикс).

### Within Each User Story

- T004 → T005 → T006 — последовательно (все правят связанный код, могут конфликтовать при неосторожном edit).
- T008 → T009 → T010 — последовательно (compile → lint → bootJar — стандартный порядок AGENTS.md).
- T011, T012, T013 — параллельно (разные файлы/команды).

### Parallel Opportunities

- T001, T002, T003 — параллельно (Phase 1).
- T011, T012, T013 — параллельно (Phase 5 polish).

---

## Implementation Strategy

### MVP First (US1 Only)

1. Завершить Phase 1: Setup (baseline проверки)
2. Завершить Phase 3: User Story 1 (T004, T005, T006)
3. **STOP and VALIDATE**: Запустить `./gradlew :karaoke-app:compileKotlin` — компиляция должна проходить
4. Запустить `./gradlew :karaoke-web:ktlintCheck` — без новых нарушений
5. Готово к ручной проверке пользователем на dev-pc

### Incremental Delivery

Один PR покрывает все 3 user story tasks (T004-T013) — фикс локальный, единая тема.

### Parallel Team Strategy

Не применимо — задача локальная, выполняется одним агентом/разработчиком.

---

## Notes

- [P] tasks = разные файлы или разные команды, нет зависимостей.
- [Story] label мапит задачу к user story для трассируемости.
- Никаких тестов не пишется — Constitution §"Рабочий процесс" говорит, что тесты `@Disabled`, проверка делается пользователем.
- Коммит после T006 (Phase 3 завершён) или после T013 (Phase 5 завершён).
- Commit-сообщение на русском, в стиле проекта: `karaoke-app: fix race condition в doCreateFromFolder — тональность не теряется при синхронном поиске текста`.

# Tasks: Заполнение аудиоданных при выборе похожей версии песни

**Input**: Design documents from `/specs/129-copy-family-audio/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/select-family-song.md, quickstart.md

**Tests**: Не запрашиваются в спецификации (FR-009 — per-feature документация; ручные сценарии из quickstart.md как independent test). Frontend unit-test runner в проекте отсутствует; backend-тесты требуют окружения и `@Disabled`.

**Organization**: Задачи сгруппированы по user stories для независимой реализации и проверки. MVP = US1 (ручной выбор с результатом сверки); US2 добавляет обработку отсутствия сверки и edge-cases.

## Format: `- [x] [ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей)
- **[Story]**: связь с user story (US1, US2); Setup/Foundational/Polish — без метки
- В описании — точные пути файлов

## Path Conventions

- **Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- **Frontend admin**: `webvue3/src/components/Songs/...`
- **Docs**: `docs/features/songs-table.md`

## Phase 1: Setup (Shared Infrastructure)

**Цель**: Зарезервировать номер ветки по NNN-slug конвенции, создать feature-ветку и подготовить baseline-проверки.

- [x] T001 Зарезервировать номер ветки через `./tools/reserve-branch-number.sh copy-family-audio` и получить переменную `N`
- [x] T002 Создать и переключиться на ветку `git checkout -b "${N}-copy-family-audio"` в `/home/nsa/Karaoke`
- [x] T003 [P] Проверить baseline: `./gradlew :karaoke-app:compileKotlin` в `/home/nsa/Karaoke` должен завершаться BUILD SUCCESSFUL
- [x] T004 [P] Проверить baseline: `./gradlew ktlintCheck` в `/home/nsa/Karaoke` должен завершаться без ошибок (baseline 0 допустим)
- [x] T005 [P] Проверить baseline: `npm run lint:check` в `/home/nsa/Karaoke/webvue3` должен проходить без ошибок

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: Загрузить контекст ключевых файлов, чтобы последующие US-фазы оперировали согласованными якорями кода.

**⚠️ CRITICAL**: Без завершения этой фазы работа над user stories невозможна.

- [x] T006 Прочитать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (блоки `audioParentId`/`audioSimilarityPercent`/`audioDeltaMs`, `getDiff()`, `saveToDb()`) для точных якорных строк
- [x] T007 [P] Прочитать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (блок `SelectFamilySongResultDto` строки 707-727, endpoint `/api/song/selectfamilysong`, валидация строки 86, 119-121)
- [x] T008 [P] Прочитать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (функция `applyFamilySongSelection` строки 4412-...)
- [x] T009 [P] Прочитать `webvue3/src/components/Songs/edit/FamilySongsModal.vue` (метод `select()`, `compareResults`, payload строки 124-179, 202-207)
- [x] T010 [P] Прочитать `webvue3/src/components/Songs/store.js` (action `selectFamilySongPromise` строки 2711-2716) и `webvue3/src/components/Songs/edit/SongEdit.vue` (handler `selectFamilySong`, поля `audioParentId`/`audioSimilarityPercent`/`audioDeltaMs`)
- [x] T011 [P] Прочитать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/WaveformCompare.kt` (`WaveformCompareResultDto` строки 30-37) для подтверждения источника процента и deltaMs

**Checkpoint**: Контекстная база готова — user stories могут стартовать параллельно (если хватает ресурсов).

---

## Phase 3: User Story 1 — Сохранение аудиосвязи при выборе похожей версии (Priority: P1) 🎯 MVP

**Goal**: При успешном выборе строки в окне «Похожие версии песни» с завершённой акустической сверкой система сохраняет три аудиополя текущей песни (ID выбранного кандидата, процент схожести, signed аудиосдвиг в мс) и делает их видимыми в редакторе без повторного открытия.

**Independent Test**: Выполнить сценарий `quickstart.md` §UI-1 (положительный сдвиг), §UI-2 (отрицательный сдвиг), §UI-3 (повторный выбор) и §API-1 из quickstart.md; убедиться, что после возврата из endpoint все три поля записаны согласованно и совпадают с перечитанной из БД строкой.

### Implementation for User Story 1

- [x] T012 [US1] Расширить `SelectFamilySongResultDto` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (строки 707-727): добавить поля `audioParentId: Long?`, `audioSimilarityPercent: Int?`, `audioDeltaMs: Int?` с KDoc/`@see docs/features/songs-table.md` (FR-001..FR-004, FR-008)
- [x] T013 [US1] Расширить endpoint `selectFamilySong` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (строки 86, 119-121): принять nullable `@RequestParam(required=false) audioSimilarityPercent: Int?` с диапазоном 0..100; использовать существующий `deltaMs` для `audioDeltaMs`; при отсутствии обеих метрик передавать `0/0` в helper (FR-006, FR-010)
- [x] T014 [US1] Расширить helper `applyFamilySongSelection` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (строки 4412-...): добавить opt-in параметры `audioParentId: Long?`, `audioSimilarityPercent: Int?`, `audioDeltaMs: Int?`; устанавливать `song.audioParentId`, `song.audioSimilarityPercent`, `song.audioDeltaMs` до существующего `song.saveToDb()` (FR-007)
- [x] T015 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` после `saveToDb()` добавить проверку: перечитать запись из БД и сравнить три аудиополя; при несоответствии вернуть ошибку без ложного `success=true` (FR-009)
- [x] T016 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` после успешного вызова helper собрать `SelectFamilySongResultDto` с тремя возвращёнными аудиополями из перечитанной записи
- [x] T017 [P] [US1] Расширить action `selectFamilySongPromise` в `webvue3/src/components/Songs/store.js` (строки 2711-2716): принимать `audioSimilarityPercent` (nullable Int) и `audioDeltaMs` (signed Int); не отправлять `audioSimilarityPercent` если null (FR-001, FR-002, FR-006)
- [x] T018 [P] [US1] В `webvue3/src/components/Songs/edit/FamilySongsModal.vue` (метод `select()`, строки 124-179, 202-207): брать `compareResults[song.id]`; при `status === 'done'` передавать `audioSimilarityPercent = percent`, иначе null; payload должен включать `deltaMs` со знаком (FR-002, FR-003, FR-006)
- [x] T019 [US1] В `webvue3/src/components/Songs/edit/SongEdit.vue` (handler `selectFamilySong`): передавать в action новые поля; после успешного ответа применить к `currentSong` и `snapshotSong` три возвращённых аудиополя + `rootId`/`idStatus` (FR-004, FR-008)
- [x] T020 [US1] В `webvue3/src/components/Songs/store.js` добавить узкую Vuex mutation `setSongAudioFieldsFromFamily({audioParentId, audioSimilarityPercent, audioDeltaMs, rootId, idStatus})`: обновлять только эти 5 полей в `currentSong` и `snapshotSong`, чтобы debounce autosave не отправлял значения повторно (FR-008)
- [x] T021 [US1] В `webvue3/src/components/Songs/edit/SongEdit.vue` закрывать модалку только после успешного ответа; добавить in-flight guard (disabled state кнопок выбора) и toast на HTTP/JSON-ошибке с сохранением возможности повторить выбор (FR-009)

**Checkpoint**: User Story 1 полностью функциональна и тестируема независимо (положительный/отрицательный сдвиг, повторный выбор, повторное открытие редактора).

---

## Phase 4: User Story 2 — Предсказуемый выбор без результата сверки (Priority: P2)

**Goal**: При выборе строки без предварительной акустической сверки система сохраняет выбранный ID аудио-родителя, но сбрасывает процент/сдвиг в нейтральный `0/0`, не оставляя значений от прежнего кандидата, и не ломает существующее копирование текста/маркеров.

**Independent Test**: Сценарий `quickstart.md` §UI-4 (выбор без сверки поверх старого A), §UI-5 (self-selection), §API-2 (отсутствие результата сверки), §API-3 (несогласованные параметры); убедиться, что `audioParentId` обновлён, процент/сдвиг = 0, текст и маркеры скопированы как раньше.

### Implementation for User Story 2

- [x] T022 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: при отсутствии `audioSimilarityPercent` в запросе валидировать, что `deltaMs` тоже отсутствует или равен 0; при нарушении возвращать 400 Bad Request (FR-006)
- [x] T023 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: добавить серверную защиту self-selection — если `idAnother == idCurrent`, возвращать 400 с сообщением «выбор текущей песни недопустим», не вызывая helper (FR-010)
- [x] T024 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`: в `applyFamilySongSelection` при отсутствии метрик устанавливать `song.audioSimilarityPercent = 0`, `song.audioDeltaMs = 0` до `saveToDb()` (FR-006)
- [x] T025 [P] [US2] В `webvue3/src/components/Songs/edit/FamilySongsModal.vue`: заблокировать клик по строке текущей песни (`song.id === currentSongId`) — disabled state + tooltip (FR-010)
- [x] T026 [US2] В `webvue3/src/components/Songs/store.js` action `selectFamilySongPromise`: не отправлять `audioSimilarityPercent` если `null`; `deltaMs` всегда передавать как signed integer (включая `0`) (FR-006)
- [x] T027 [US2] В `webvue3/src/components/Songs/edit/SongEdit.vue` (handler `selectFamilySong`): на ответ сервера со `status !== 'ok'` или HTTP-ошибке не закрывать модалку, показывать toast и сохранять предыдущие значения `currentSong` нетронутыми (FR-009, регрессия)
- [x] T028 [US2] В `webvue3/src/components/Songs/edit/SongEdit.vue`: убедиться, что после выбора без сверки три аудиополя в `currentSong` равны `(anotherId, 0, 0)` независимо от предыдущего состояния; добавить ручной лог в dev-режиме для регрессионной проверки (FR-005, FR-006)

**Checkpoint**: User Stories 1 И 2 обе работают независимо; выбор с результатом сверки и без сверки дают согласованные данные; self-selection и несогласованные параметры отклоняются.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Цель**: Документация и финальные проверки.

- [x] T029 [P] Обновить `docs/features/songs-table.md`: добавить секцию «Ручной выбор похожей версии и аудиополя» с описанием трёх аудиополей, правила `0/0`, единого `saveToDb()`, in-flight guard, ограничения `autoAssignOriginalByWaveform`, ссылку на FR-009 (constitution requirement)
- [x] T030 [P] Обновить `docs/architecture-notes.md` (Pass-секция в конце файла): запись о фиче 129 с перечислением изменённых файлов и PR-ссылкой после merge
- [x] T031 [P] Проверить formatting: `npm run format:check` в `/home/nsa/Karaoke/webvue3` (baseline файлы `src/lib/sockjs-client/*` исключены — это легаси)
- [x] T032 [P] Проверить compile: `./gradlew :karaoke-app:compileKotlin && ./gradlew ktlintCheck` в `/home/nsa/Karaoke` (должны быть зелёными)
- [x] T033 [P] Проверить frontend build: `npm run build` в `/home/nsa/Karaoke/webvue3` (должен собираться без ошибок; chunk-size warnings допустимы)
- [x] T034 [P] Проверить lint: `npm run lint:check` в `/home/nsa/Karaoke/webvue3` (без warnings, `--max-warnings 0`)
- [x] T035 [P] Проверить KDoc/JSDoc coverage: `bash tools/check-kdoc-coverage.sh` и `bash tools/check-jsdoc-coverage.sh webvue3` (должны быть 100% или baseline 0)
- [x] T036 [P] Создать commit и PR: `git add` только изменённых файлов (НЕ коммитить `node_modules/`, `dist/`, `deploy/do.env`); `git commit`; создать PR через `gh pr create --base master`; дождаться CI 7/7 PASS; `gh pr merge --merge --delete-branch`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Нет зависимостей — стартует сразу.
- **Foundational (Phase 2)**: Зависит от Setup (T001-T005). BLOCKS все user stories.
- **User Stories (Phase 3+)**: Зависят от Foundational. Можно параллелить по ресурсам (T012-T016 backend → T017-T021 frontend).
- **Polish (Phase 5)**: Зависит от завершения US1 + US2.

### User Story Dependencies

- **US1 (P1)**: Стартует после Foundational. Независима от US2 (минимальный MVP путь).
- **US2 (P2)**: Расширяет US1 — серверная валидация, frontend UI блокировка, edge-case handling. Зависит от US1 инфраструктурно, но тестируется независимо по quickstart.

### Within Each Story

- Backend DTO (T012) → backend endpoint (T013) → helper расширение (T014) → post-save проверка (T015) → response builder (T016) — последовательно.
- Frontend store action (T017) и modal payload (T018) могут идти параллельно после backend baseline.
- SongEdit handler (T019) зависит от T017, T020.
- Vuex mutation (T020) — независимая, может идти параллельно с T019.
- US2 валидация (T022, T023) может идти параллельно с US1 backend, если согласованы имена параметров.

### Parallel Opportunities

- T003, T004, T005 (baseline проверки) — параллельно.
- T007, T008, T009, T010, T011 (чтение контекстных файлов) — параллельно.
- T017, T018 — параллельно (разные файлы).
- T020 — параллельно с T019 (тот же файл, но разные строки; рекомендуется последовательно после T019 для атомарности).
- T029, T030, T031, T032, T033, T034, T035 — параллельно (только проверки/документация).

---

## Parallel Example: User Story 1

```bash
# Backend последовательно (один файл, T012-T016 — последовательно):
Task: "T012 [US1] Расширить SelectFamilySongResultDto в ApiController.kt"
Task: "T013 [US1] Расширить endpoint selectFamilySong в ApiController.kt"
Task: "T014 [US1] Расширить applyFamilySongSelection в Utils.kt"
Task: "T015 [US1] Добавить post-save проверку в Utils.kt"
Task: "T016 [US1] Собрать DTO с аудиополями в ApiController.kt"

# Frontend параллельно после backend baseline:
Task: "T017 [P] [US1] Расширить selectFamilySongPromise в store.js"
Task: "T018 [P] [US1] Обновить payload в FamilySongsModal.vue"

# Затем последовательно:
Task: "T019 [US1] Обработать ответ в SongEdit.vue"
Task: "T020 [US1] Добавить mutation в store.js"
Task: "T021 [US1] Закрыть модалку + in-flight guard в SongEdit.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T005)
2. Complete Phase 2: Foundational (T006-T011)
3. Complete Phase 3: User Story 1 (T012-T021)
4. **STOP and VALIDATE**: запустить quickstart §UI-1..§UI-3, §API-1; убедиться, что три аудиополя сохранены и видны после повторного открытия.
5. Deploy/demo if ready.

### Incremental Delivery

1. Setup + Foundational → готовность инфраструктуры
2. + US1 → тест независимо → MVP (выбор с завершённой сверкой)
3. + US2 → тест независимо (выбор без сверки, self-selection, валидация)
4. + Polish → документация, финальные проверки, PR

### Parallel Team Strategy

- Developer A: US1 backend (T012-T016)
- Developer B: US1 frontend (T017-T021) после завершения T012-T013
- Developer C: US2 (T022-T028) после US1 baseline
- Все вместе на Polish (T029-T036)

---

## Notes

- [P] tasks = разные файлы, нет зависимостей.
- [Story] label обеспечивает трассировку до spec.md.
- Каждая user story независимо завершаема и тестируема через quickstart.md.
- Тесты не пишутся (не запрошены); верификация — ручные сценарии + статические команды.
- Коммит после каждой задачи или логической группы.
- При отказе checkpoint — стоп, проверить, исправить.
- Избегать: расплывчатых формулировок, конфликтов в одном файле, кросс-story зависимостей.

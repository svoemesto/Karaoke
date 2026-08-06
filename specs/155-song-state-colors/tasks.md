---
description: "Task list for feature 155-song-state-colors"
---

# Tasks: Актуализация статусов и цветов песен

**Input**: Design documents from `/specs/155-song-state-colors/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Запрошены в спецификации (FR-007, FR-008, SC-001, SC-005) и `quickstart.md` — создаются офлайн-тесты в `karaoke-app/src/test` и один Vue-компонентный ручной сценарий.

**Organization**: Задачи сгруппированы по user stories (US1 — расчёт состояния, US2 — распространение цвета, US3 — фильтр и UI публикаций), затем Phase Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей).
- **[Story]**: метка user story (US1, US2, US3).
- В описаниях указаны абсолютные/относительные пути относительно корня репозитория.

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`, `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/...`.
- Admin frontend: `webvue3/src/components/Publish/...`, `webvue3/src/store/modules/...`.
- Документация: `docs/features/...`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка репозитория и проверка опорных файлов перед правками.

- [x] T001 Verify feature branch `155-song-state-colors` is checked out via `git rev-parse --abbrev-ref HEAD` and clean working tree via `git status`
- [x] T002 [P] Read existing `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongState.kt` and `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (Song.state, loadListFromDb color fallback) to confirm trim targets
- [x] T003 [P] Read existing `webvue3/src/components/Publish/components/PublishTableFooter.vue`, `webvue3/src/store/modules/publish/filter/store.js`, `webvue3/src/components/Publish/store.js` to map old STATE_* button layout
- [x] T004 [P] Read existing `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` for `getPublicationsDateFrom` and `publicationsDigest`, plus `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt` for `getSongListForPublications`

**Checkpoint**: ветка готова, точки правки идентифицированы, можно приступать к Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Минимальный фундамент, без которого ни одна user story не работает: чистый enum, чистый расчёт, чистая передача цвета.

**⚠️ CRITICAL**: Все user stories зависят от завершения этой фазы.

- [x] T005 Trim `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongState.kt` to exactly five values `DONE`, `TODAY`, `ON_AIR`, `EXCLUSIVE`, `IN_WORK` with colors `#CCFFCC`, `#FFFF00`, `#33FF33`, `#99CCFF`, `""` and KDoc link to `docs/features/song-state-colors.md`
- [x] T006 Add internal testable resolution function `Song.resolveStateFor(now: LocalDateTime): SongState` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`. Keep `Song.state` as a public property delegating to `resolveStateFor(MoscowNow())` so existing consumers (`loadListFromDb`, DTO wiring) keep the same interface.
- [x] T007 Implement priority order in resolver: `IN_WORK` (`idStatus < 6`) → `ON_AIR` (`free=true` or active free window) → `EXCLUSIVE` (no valid date+time) → `TODAY` (today, future moment) → `DONE` (rest) using `Europe/Moscow` zone in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
- [x] T008 Replace legacy `idStatus`-palette fallback in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` `loadListFromDb` (~line 7928) so that empty color for `IN_WORK` is preserved and not substituted by an old status color
- [x] T009 Confirm `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` and `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt` propagate `color` unchanged and add KDoc cross-reference to `contracts/song-state-color.md`

**Checkpoint**: backend рассчитывает пять состояний и передаёт канонические цвета; UI получает их «как есть».

---

## Phase 3: User Story 1 - Понятная цветовая классификация песен (Priority: P1) 🎯 MVP

**Goal**: Каждая песня получает ровно одно из пяти состояний, цвета соответствуют таблице из `data-model.md` и видны в таблице песен.

**Independent Test**: Прогнать `SongStateTest.kt` (см. T010) на матрице из `quickstart.md`; открыть таблицу песен `webvue3/src/components/Songs/...` и сверить цвет имени с ожидаемым состоянием.

### Tests for User Story 1 ⚠️

> **NOTE**: тесты должны запускаться ДО реализации и падать на старом коде.

- [x] T010 [P] [US1] Create offline JUnit 5 matrix `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SongStateTest.kt` covering all 9 cases from `specs/155-song-state-colors/quickstart.md` (IN_WORK, free ON_AIR, TODAY, in-window ON_AIR, post-window DONE, future non-today DONE, EXCLUSIVE без дат, EXCLUSIVE с битой парой, границы `now`)

### Implementation for User Story 1

- [x] T011 [P] [US1] Document invariants for `Song.state` (priority order, timezone, IN_WORK пустой цвет) in KDoc above `Song.resolveStateFor` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
- [x] T012 [US1] Wire `Song.state.color` into `SongDTO.color` directly in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (remove any remaining Telegram/VK/Dzen/Sponsr/PL palette branches) — depends on T005, T006, T007, T008
- [x] T013 [US1] Run `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.model.SongStateTest"` and `./gradlew :karaoke-app:compileKotlin` to confirm resolver passes matrix

**Checkpoint**: US1 готов; каждая песня имеет ровно одно из пяти состояний; матрица зелёная.

---

## Phase 4: User Story 2 - Единые статусы во всех представлениях (Priority: P1)

**Goal**: Таблица песен, сетка публикаций, обновление строки и любые потребители цвета показывают одинаковый цвет одной и той же песни.

**Independent Test**: Найти по одной песне каждой категории в таблице песен и сетке публикаций, сравнить цвета; изменить `free` или расписание и убедиться, что оба представления обновляются согласованно.

### Tests for User Story 2 ⚠️

- [x] T014 [P] [US2] Extend `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SongStateTest.kt` with assertions that `Song.state.color` equals canonical values from `contracts/song-state-color.md` for each branch

### Implementation for User Story 2

- [x] T015 [P] [US2] Verify `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` and `webvue3/src/components/Publish/components/PublishTableBody.vue` only consume `color` field (no hard-coded old tokens); report diff if cleanup needed
- [x] T016 [P] [US2] Verify `webvue3/src/components/Songs/components/SongsTable.vue` and any sister component render only via `color` and delete references to old STATE_* labels
- [x] T017 [US2] Ensure SSE / row-update payload in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (update endpoints) propagates new `color` value without caching the previous state — depends on T012

**Checkpoint**: цвета согласованы между модулями; ручная проверка матрицы в UI проходит.

---

## Phase 5: User Story 3 - Фильтрация по новой классификации (Priority: P2)

**Goal**: Endpoint `POST /api/publications/date` и `PublishTableFooter.vue` поддерживают только пять токенов из `contracts/publications-date-filter.md`; старые токены и кнопки удалены.

**Independent Test**: В UI нажать каждую из пяти кнопок легенды и проверить, что отправляется соответствующий `STATE_*` токен, ответ используется как дата диапазона, а сетка публикаций загружается без ошибки; убедиться, что старых кнопок нет.

### Tests for User Story 3 ⚠️

- [x] T018 [P] [US3] Add unit tests in `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/controllers/PublicationsDateFilterTest.kt` for `STATE_DONE|STATE_TODAY|STATE_ON_AIR|STATE_EXCLUSIVE|STATE_IN_WORK` returning expected date and unknown token returning empty string

### Implementation for User Story 3

- [x] T019 [US3] Trim `POST /api/publications/date` handler in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` to accept only five `STATE_*` tokens; remove Telegram/VK/Dzen branches and unknown aliases in `getPublicationsDateFrom` — depends on T006, T007 (resolver must produce the five canonical states)
- [x] T020 [P] [US3] Replace 13 legacy color buttons in `webvue3/src/components/Publish/components/PublishTableFooter.vue` with five buttons (DONE, TODAY, ON_AIR, EXCLUSIVE, IN_WORK) and corresponding colors from `contracts/song-state-color.md`. Add a JSDoc block above `<script setup>` with `@see docs/features/song-state-colors.md` per Constitution Principle VI.FR-006.
- [x] T021 [P] [US3] Handle empty date response from `STATE_EXCLUSIVE`/`STATE_IN_WORK` in `PublishTableFooter.vue` so that no empty date propagates into range arithmetic and the publications grid still reloads
- [x] T022 [P] [US3] Clean `webvue3/src/store/modules/publish/filter/store.js` and `webvue3/src/components/Publish/store.js` from legacy `STATE_ALL_DONE`, `STATE_OVERDUE`, `STATE_ALL_UPLOADED`, `STATE_WO_TG`, `STATE_WO_VK`, `STATE_WO_DZEN`, `STATE_WO_VKG`, `STATUS_0..6` literals (no silent aliases)

**Checkpoint**: фильтр публикаций работает по пяти состояниям; старые токены и кнопки отсутствуют; ручной quickstart проходит.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, lint/coverage, end-to-end проверка.

- [x] T023 [P] Create per-feature document `docs/features/song-state-colors.md` describing classification rules, color table, contract link, edge cases, известные ловушки
- [x] T024 [P] Register the new feature in `docs/features/README.md` and add cross-link from `docs/features/songs-table.md`
- [x] T025 Run automated checks from `specs/155-song-state-colors/quickstart.md`: `./gradlew :karaoke-app:compileKotlin`, `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check`, `cd karaoke-public && npm run lint:check`
- [x] T026 Run coverage gates: `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`, `bash tools/check-jsdoc-coverage.sh karaoke-public`, `bash tools/check-feature-doc.sh docs/features/song-state-colors.md`
- [x] T027 Run pre-commit `pre-commit run --all-files` and ensure all hooks pass
- [x] T028 [P] Verify FR-013 invariant: confirm `processColor*` references in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Constants.kt` (lines 32–39 commented palette) and channel-specific statuses in `Publication.kt` / `ApiController.kt` were not renamed or removed by this feature (grep `processColor|STATE_WO_TG|STATE_WO_VK|STATE_WO_DZEN|STATE_WO_VKG|STATE_OVERDUE|STATE_ALL_DONE|STATE_ALL_UPLOADED` returns only the deleted-from-SongState references).
- [ ] T029 Execute manual UI walkthrough from `quickstart.md` (7 steps) on `155-song-state-colors` branch against local admin frontend

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно начинать немедленно.
- **Foundational (Phase 2)**: зависит от Setup — блокирует все user stories.
- **User Stories (Phase 3–5)**: зависят от Foundational; US1 и US2 приоритета P1, US3 — P2.
- **Polish (Phase 6)**: зависит от завершения всех user stories.

### User Story Dependencies

- **US1 (P1)**: можно начинать после Foundational; не зависит от других стори.
- **US2 (P1)**: расширяет US1 (тот же резолвер и тот же DTO), но тестируется независимо по одному и тому же цвету в разных представлениях.
- **US3 (P2)**: зависит от Foundational (новый расчёт) и от US1 (контракт цвета); UI-правки независимы от US2.

### Within Each User Story

- Tests пишутся первыми и должны падать до реализации.
- Модели и расчёт — перед сервисами/эндпоинтами.
- Backend-эндпоинт — перед UI.
- Каждая стори завершается независимой проверкой перед переходом к следующей.

### Parallel Opportunities

- Phase 1: T002, T003, T004 — параллельно (разные файлы).
- Phase 2: T009 — параллельно с T005–T008; внутри T005–T008 последовательно из-за общих правок в `Song.kt`/`SongState.kt`.
- Phase 3: T010 параллельно с T011 (тест + KDoc); T012 — после T005–T008; T013 — после T012.
- Phase 4: T015 и T016 — параллельно (разные Vue-файлы); T014 — параллельно с T015/T016.
- Phase 5: T020 и T021 — после T019; T018 и T022 — параллельно с backend-правкой.
- Phase 6: T023 и T024 — параллельно.

---

## Parallel Example: User Story 1

```bash
# Запустить подготовку теста и KDoc параллельно:
Task: "Create offline JUnit 5 matrix SongStateTest.kt"
Task: "Document invariants for Song.state in KDoc"

# После прохождения теста — пересборка backend одним шагом:
Task: "Wire Song.state.color into SongDTO.color and remove legacy palette branches"
Task: "Run SongStateTest and compileKotlin"
```

---

## Parallel Example: User Story 3

```bash
# Backend trim и UI replacement независимы до момента ручной проверки:
Task: "Trim /api/publications/date to five STATE_* tokens in ApiController.kt"
Task: "Replace 13 legacy color buttons with five in PublishTableFooter.vue"

# Параллельно можно чистить стор и добавлять unit-тест endpoint:
Task: "Add PublicationsDateFilterTest in karaoke-app/src/test"
Task: "Clean legacy STATE_* literals from publish filter/store.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1 (state resolution + canonical colors).
4. **STOP and VALIDATE**: запустить `SongStateTest`, открыть таблицу песен, сверить цвета.
5. Если MVP устраивает — продолжить US2/US3.

### Incremental Delivery

1. Setup + Foundational → backend рассчитывает пять состояний.
2. US1 → матрица зелёная, таблица песен окрашена правильно.
3. US2 → цвета согласованы между таблицей песен, сеткой публикаций и SSE-обновлениями.
4. US3 → фильтр публикаций работает по пяти токенам, старые кнопки удалены.
5. Polish → документация, lint, coverage, ручной quickstart.

### Parallel Team Strategy

С одним разработчиком — последовательно по приоритету. С двумя — после Foundational:
- Developer A: US1 (resolver + tests + DTO).
- Developer B: US3 (endpoint + UI buttons + store cleanup).
- US2 объединяет результат и проверяет кросс-представление.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей; внутри `Song.kt`/`SongState.kt` правки последовательные.
- [Story] метка нужна для traceability фаз US1/US2/US3; Setup/Foundational/Polish — без метки.
- Каждая user story самодостаточна и тестируется отдельно (resolver/распространение/фильтр).
- Тесты падают на старом коде → зелёные после US1.
- Коммиты — после каждой задачи или логической группы.
- Остановка на любом checkpoint для независимой проверки стори.
- Не делать: размытые задачи, конфликты в одном файле, кросс-стори зависимости, нарушающие независимость.

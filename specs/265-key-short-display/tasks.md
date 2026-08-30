# Tasks: Краткое отображение тональности в онлайн-плеере

**Input**: Design documents from `/specs/265-key-short-display/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/key-display-contract.md, quickstart.md

**Tests**: В спеке тесты не запрашивались (acceptance scenarios — ручные, см. quickstart.md). Тестовые задачи не генерируются.

**Organization**: Tasks grouped by user story (4 stories: US1, US2, US3 — P1; US4 — P2). Каждая фаза = одна user story, инкрементально поставляемая и независимо тестируемая. Foundational phase = добавление static helper `_shortKey` в обе копии плеера (блокирует все user stories).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preflight проверки — активная ветка, baseline-файлы на месте. Минимальная, т.к. проект уже инициализирован.

- [x] T001 Verify active branch is `265-key-short-display` and ESLint baseline files exist: `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Добавление static helper `KaraokePlayer._shortKey(key)` в обе копии плеера. Без этого helper-а ни одна user story не может быть реализована.

**⚠️ CRITICAL**: Никакая user story не может начаться, пока эта фаза не завершена.

- [x] T002 [P] Add `static _shortKey(key)` method with JSDoc and `@see archive/docs/features/player-transpose.md` in `karaoke-public/src/player/KaraokePlayer.js` (place next to existing `_parseKey` ~line 993)
- [x] T003 [P] Add `static _shortKey(key)` method with JSDoc and `@see archive/docs/features/player-transpose.md` in `webvue3/src/player/KaraokePlayer.js` (place next to existing `_parseKey` ~line 1119)

**Checkpoint**: Helper доступен в обеих копиях — можно начинать user stories.

---

## Phase 3: User Story 1 — Краткая тональность на сплэш-экране публичного плеера (Priority: P1) 🎯 MVP

**Goal**: На сплэш-экране публичного плеера (`karaoke-public`) тональность отображается кратко (`Gm`, `A`, `F#m`) вместо длинных форм (`G minor`, `A major`).

**Independent Test**: Открыть `/player/<id>` для песни с `data.key = "G minor"`, `data.bpm = 120` → на сплэше строка `Key: «Gm», bpm: 120` (SC-001).

### Implementation for User Story 1

- [x] T004 [US1] Replace direct `this.data.key` usage with `KaraokePlayer._shortKey(this.data.key)` in splash `_renderSplash` at `karaoke-public/src/player/KaraokePlayer.js:2989` (shortKey variable + keyStr ternary, см. research.md D2)

**Checkpoint**: SC-001 пройден для публичного плеера. Сплэш показывает короткую тональность.

---

## Phase 4: User Story 2 — Краткая тональность в блоке метаданных (header) публичного плеера (Priority: P1)

**Goal**: В блоке метаданных хедера публичного плеера строка «Тональность:» показывает короткое значение.

**Independent Test**: Открыть `/player/<id>` для песни с `data.key = "G minor"`, запустить воспроизведение → в header.metadata строка `Тональность: Gm` (SC-002).

### Implementation for User Story 2

- [x] T005 [US2] Replace direct `this.data.key` usage with `KaraokePlayer._shortKey(this.data.key)` in header.metadata at `karaoke-public/src/player/KaraokePlayer.js:3597` (shortKey variable + if-guard, см. research.md D2)

**Checkpoint**: SC-002 пройден для публичного плеера. Header.metadata показывает короткую тональность.

---

## Phase 5: User Story 3 — Краткая тональность на сплэш-экране админского плеера (Priority: P1)

**Goal**: На сплэш-экране админского плеера (`webvue3`) тональность отображается кратко в обоих режимах: online и MP4 render. Финальный mp4 получит короткую форму.

**Independent Test**: В админке открыть `/player/<id>` для песни с `data.key = "A major"` → на сплэше обоих режимов `Key: «A», bpm: 90` (SC-003). MP4-рендер: готовое видео содержит `Key: «A», bpm: 90` (SC-003a).

### Implementation for User Story 3

- [x] T006 [US3] Replace direct `this.data.key` usage with `KaraokePlayer._shortKey(this.data.key)` in splash online mode at `webvue3/src/player/KaraokePlayer.js:3131` (shortKey variable + keyStr ternary, см. research.md D3)
- [x] T007 [US3] Replace direct `this.data.key` usage with `KaraokePlayer._shortKey(this.data.key)` in splash MP4 render mode at `webvue3/src/player/KaraokePlayer.js:3255` (shortKey2 variable + keyStr2 ternary, см. research.md D3 — суффикс `2` для отличия от online-ветки в той же функции)

**Checkpoint**: SC-003, SC-003a пройдены для админского плеера. Сплэш в обоих режимах показывает короткую тональность.

---

## Phase 6: User Story 4 — Краткая тональность в блоке метаданных (header) админского плеера (Priority: P2)

**Goal**: В блоке метаданных хедера админского плеера строка «Тональность:» показывает короткое значение. Зеркалирует US2 для админки.

**Independent Test**: В админке открыть `/player/<id>` для песни с `data.key = "F# minor"`, запустить воспроизведение → в header.metadata `Тональность: F#m` (SC-004). Бемоль `Bb minor` → `Тональность: A#m` (flat→sharp нормализация).

### Implementation for User Story 4

- [x] T008 [US4] Replace direct `this.data.key` usage with `KaraokePlayer._shortKey(this.data.key)` in header.metadata at `webvue3/src/player/KaraokePlayer.js:3864` (shortKey variable + if-guard, см. research.md D3)

**Checkpoint**: SC-004 пройден для админского плеера. Header.metadata показывает короткую тональность с flat→sharp нормализацией.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Governance, документация, линт/baseline проверка, сборка Docker-образов, ручная валидация по quickstart, PR. Зависит от всех US.

- [x] T009 [P] Update per-feature document `archive/docs/features/player-transpose.md`: добавить раздел «Короткое отображение тональности (added 2026-08-30, FR-265)» с описанием `_shortKey` и 5 точек отрисовки (FR-012, Constitution §VI FR-009, см. research.md D4)
- [x] T010 [P] Run `npm run lint` and `bash tools/check-eslint-baseline.sh webvue3`; verify 0 new baseline entries in `webvue3/.eslint-baseline.json` (FR-013, §VI FR-007)
- [x] T011 [P] Run `npm run lint` and `bash tools/check-eslint-baseline.sh karaoke-public`; verify 0 new baseline entries in `karaoke-public/.eslint-baseline.json` (FR-013, §VI FR-007)
- [x] T012 Build Docker images per AGENTS.md Pass 245 (Vite-build ≠ Docker-image): `cd deploy && bash do.sh build_webvue3`; if `karaoke-public` was modified — `bash do.sh build_public` (контекст multi-stage Dockerfile копирует только свой каталог; кросс-импорты `../../<other>/...` падают внутри контейнера)
- [ ] T013 Run quickstart.md validation scenarios (SC-001..SC-008, FR-012, FR-013) per `specs/265-key-short-display/quickstart.md` — ручная проверка пользователем в браузере
- [ ] T014 Reserve branch number and create PR per AGENTS.md "CI-gate для master" (NON-NEGOTIABLE): `N=$(./tools/reserve-branch-number.sh my-slug)` — note: `265-key-short-display` already has reserved `265`, but для merge в master нужен свежий PR через `gh pr create --base master && gh pr checks && gh pr merge --merge` (БЕЗ `--delete-branch`)
- [x] T015 Final regression check: убедиться, что меню «Тональность» и бейдж transpose НЕ изменились (FR-009, SC-005); `_transposeLabel` остался без правок; `_parseKey` остался без правок; `CHROMATIC` остался без правок; `SongEdit.vue:235-243` остался без правок (out-of-scope)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Setup — BLOCKS все user stories.
- **User Stories (Phase 3-6)**: зависят от Foundational.
  - US1 (Phase 3) и US3 (Phase 5) — параллельны (разные файлы: `karaoke-public` vs `webvue3`).
  - US2 (Phase 4) — последовательно после US1 (тот же файл `karaoke-public/src/player/KaraokePlayer.js`).
  - US4 (Phase 6) — последовательно после US3 (тот же файл `webvue3/src/player/KaraokePlayer.js`).
- **Polish (Phase 7)**: зависит от всех US.

### User Story Dependencies

- **US1 (P1, сплэш публичного)**: нет зависимостей на другие US, может стартовать сразу после Foundational.
- **US2 (P1, header публичного)**: зависит от US1 (тот же файл — последовательная правка в одном файле).
- **US3 (P1, сплэш админского)**: нет зависимостей на другие US, может стартовать параллельно с US1.
- **US4 (P2, header админского)**: зависит от US3 (тот же файл — последовательная правка).

### Within Each Phase

- Внутри Phase 2: T002 и T003 параллельны (разные файлы, [P]).
- Внутри Phase 3: одна задача T004 (US1).
- Внутри Phase 4: одна задача T005 (US2).
- Внутри Phase 5: T006 и T007 последовательны в одном файле (НЕ [P]).
- Внутри Phase 6: одна задача T008 (US4).
- Внутри Phase 7: T009, T010, T011 параллельны (разные файлы/команды, [P]); T012, T013, T014, T015 последовательны (полагаются на предыдущие).

### Parallel Opportunities

- **Phase 2**: T002 ∥ T003 (helper в обеих копиях плеера — разные файлы).
- **Phase 3 ∥ Phase 5**: US1 (karaoke-public) ∥ US3 (webvue3) — после Foundational.
- **Phase 7**: T009 ∥ T010 ∥ T011 (per-feature документ ∥ ESLint webvue3 ∥ ESLint karaoke-public).

---

## Parallel Example: Foundational Phase

```bash
# T002 и T003 параллельно (разные файлы):
Task: "T002 [P] Add static _shortKey(key) in karaoke-public/src/player/KaraokePlayer.js"
Task: "T003 [P] Add static _shortKey(key) in webvue3/src/player/KaraokePlayer.js"
```

## Parallel Example: US1 + US3 (P1 stories в разных плеерах)

```bash
# После Foundational — две независимые US-фазы в параллель:
Task: "T004 [US1] Use _shortKey in karaoke-public splash"
Task: "T006 [US3] Use _shortKey in webvue3 splash online"
Task: "T007 [US3] Use _shortKey in webvue3 splash MP4"
```

---

## Implementation Strategy

### MVP First (US1 Only — публичный плеер, сплэш)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002 helper в karaoke-public)
3. Phase 3: US1 (T004 — сплэш публичного плеера)
4. **STOP and VALIDATE**: SC-001 пройден для публичного плеера.
5. Можно задеплоить на прод как MVP, если срочно.

### Полная поставка (все 4 US)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002, T003 параллельно)
3. Phase 3: US1 (T004) ∥ Phase 5: US3 (T006, T007 последовательно в одном файле) — две независимые работы в параллель
4. Phase 4: US2 (T005, после US1 — тот же файл)
5. Phase 6: US4 (T008, после US3 — тот же файл)
6. Phase 7: Polish (T009..T015)
7. Готово к merge в master.

### Incremental Delivery

1. После Foundational — foundation готова.
2. После US1 — публичный плеер (сплэш) показывает короткую тональность → deploy/demo (MVP!).
3. После US2 — публичный плеер полностью готов (сплэш + header) → deploy/demo.
4. После US3 — админский плеер (сплэш online + MP4) готов → deploy/demo.
5. После US4 — админский плеер полностью готов (сплэш + header) → deploy/demo.
6. Каждая US добавляет ценность, не ломая предыдущие.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] label мапит задачу на user story для traceability.
- Каждая user story независимо завершаема и тестируема (см. quickstart.md).
- Внутри одного файла (`karaoke-public/...KaraokePlayer.js` или `webvue3/...KaraokePlayer.js`) — последовательная правка, чтобы избежать merge-конфликтов.
- Коммит после каждой задачи или логической группы.
- Остановка на любом checkpoint для валидации story независимо.
- Избегать: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей.
- Не забыть: pre-commit проверка `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` пуста (Constitution §VIII.3).
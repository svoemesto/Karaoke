# Tasks: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Input**: Design documents from `/specs/363-album-type-filter-reset-on-album-open/`
- spec.md (US1: авто-сброс; US2: сохранение UI-выбора; US3: контр-кейс)
- plan.md (Vue 2 + Vuex + Vue Router + localStorage, frontend-only)
- research.md (5 решений)
- data-model.md (existing `hiddenAlbumTypes` + new computed `effectiveHiddenAlbumTypes`)
- quickstart.md (5 ручных сценариев)

**Tests**: НЕ генерируются — проект `karaoke-public` не покрыт unit-тестами (см. AGENTS.md § Тесты; `research.md` → Decision 5). Проверка — DevTools reproducer per `quickstart.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллелить (разные файлы, нет зависимостей)
- **[Story]**: к какой user story привязана задача

## Path Conventions

- **Frontend-only** правка: `karaoke-public/src/views/ZakromaView.vue` (единственный файл)
- Документы: `specs/363-album-type-filter-reset-on-album-open/`

---

## Phase 1: Setup (контекст для фичи)

**Purpose**: подготовить ветку, проверить tooling, убедиться что baseline-тесты зелёные.

- [x] T001 Подтвердить активную ветку `363-album-type-filter-reset-on-album-open` через `git branch --show-current`
- [x] T002 [P] Прочитать целиком `karaoke-public/src/views/ZakromaView.vue` (строки 426–800) — зафиксировать текущий контракт `hiddenAlbumTypes`/`visibleAlbums`/`selectedAlbumId`/`zakroma` перед правкой
- [x] T003 [P] Убедиться что локальные контейнеры `karaoke-public` и `nginx` запущены (`docker ps`) — нужно для ручной проверки в Phase 5

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: добавить **общий** computed `effectiveHiddenAlbumTypes`, который используется **всеми** US (US1 — основная правка; US2 — без регрессии; US3 — контр-кейс).

**⚠️ CRITICAL**: все user story tasks зависят от T004.

- [x] T004 [US1] Добавить computed `effectiveHiddenAlbumTypes(zak)` в `karaoke-public/src/views/ZakromaView.vue` рядом с `visibleAlbums(zak)` (~строка 730) — возвращает `Set<string>`, эквивалентный `hiddenAlbumTypes` минус `albumType` открытого альбома (если он в `hiddenAlbumTypes`). Зависимости: `hiddenAlbumTypes` (data), `selectedAlbumId` (computed), `zakroma` (Vuex getter). Добавить JSDoc-комментарий с описанием и ссылкой на issue #78.

**Checkpoint**: Foundation ready — можно приступать к US1/US2/US3.

---

## Phase 3: User Story 1 — Открытие песен альбома со скрытой категорией (Priority: P1) 🎯 MVP

**Goal**: при `?albumId=Y` фильтр `hiddenAlbumTypes` НЕ блокирует отображение песен открытого альбома.

**Independent Test**: открыть `/zakroma/{id}?albumId=Y` (тип Y скрыт в `localStorage`) → видны песни альбома; `localStorage` байт-в-байт идентичен до и после; после возврата на `/zakroma/{id}` (без `?albumId=`) фильтр восстановлен.

### Implementation for User Story 1

- [x] T005 [US1] Заменить в `karaoke-public/src/views/ZakromaView.vue` тело `visibleAlbums(zak)` (строка 733) на использование `effectiveHiddenAlbumTypes(zak)` вместо `hiddenAlbumTypes` (FR-001, FR-002, FR-003, FR-005, FR-006). Зависит от T004.
- [x] T006 [US1] Обернуть блок `<div class="km-album-type-filters">` в `karaoke-public/src/views/ZakromaView.vue` (строка 59) в `v-if="!selectedAlbumId"` (FR-004, из Clarifications Q1). Зависит от T005.

**Checkpoint**: US1 функционально полный. Ручная проверка quickstart сценариев 1, 2, 4, 5.

---

## Phase 4: User Story 2 — Сохранение пользовательского выбора фильтра (Priority: P2)

**Goal**: убедиться что существующая механика `toggleAlbumType` + `localStorage` persistence не сломана.

**Independent Test**: открыть `/zakroma/{id}` без `?albumId=`, кликнуть кнопку фильтра → состояние + `localStorage` обновляются; перезагрузка сохраняет выбор.

### Implementation for User Story 2

- [x] T007 [US2] Проверить что `toggleAlbumType(dbValue)` (строка 719) и привязка кнопок-фильтров в шапке не затронуты правкой T006 (защита от регрессии). Чисто верификационная задача: `git diff` показывает, что правка в строке 59 не задела `toggleAlbumType` и `@click` обработчики кнопок.

**Checkpoint**: US2 подтверждён — нет регрессии.

---

## Phase 5: User Story 3 — Открытие альбома без скрытого типа (Priority: P3)

**Goal**: подтвердить что auto-reset не срабатывает «превентивно».

**Independent Test**: скрыть `single`, открыть студийный альбом через `?albumId=Z` → песни видны, `hiddenAlbumTypes` НЕ изменился.

### Implementation for User Story 3

- [x] T008 [US3] Верифицировать computed `effectiveHiddenAlbumTypes` (Phase 2, T004): когда `albumType` открытого альбома **НЕ** в `hiddenAlbumTypes` — возвращается идентичный Set (FR-005). Чисто код-ревью: убедиться что `if (!opened || !hiddenAlbumTypes.has(opened.albumType)) return hiddenAlbumTypes` покрывает этот кейс (см. `data-model.md` → Алгоритм).

**Checkpoint**: все 3 US функционально валидны.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: проверить CI 7/7, прогнать quickstart, подготовить tracker-report, обновить Knowledge если требуется.

- [x] T009 [P] Запустить `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` — должно быть чисто
- [x] T010 [P] Запустить `bash tools/check-jsdoc-coverage.sh karaoke-public` — JSDoc coverage ≥50% (для нового computed нужен JSDoc — T004 его добавляет)
- [ ] T011 [P] Собрать Docker-образ: `cd deploy && bash do.sh build_public` — для локальной проверки quickstart (заблокировано read-only `/home/nsa/.docker/buildx`; отложено владельцу)
- [ ] T012 Прогнать 5 сценариев из `specs/363-album-type-filter-reset-on-album-open/quickstart.md` в DevTools — заполнить чек-лист в quickstart (требует браузера; отложено владельцу)
- [x] T013 [P] Проверить что `AGENTS.md § FR-009` (per-feature документ) НЕ требует нового `docs/features/<slug>.md` для этой фичи (scope = single-file fix, не новая C4 L3-компонента). Зафиксировать вывод в `specs/363-album-type-filter-reset-on-album-open/report.md`.
- [x] T014 [P] Проверить `.ssot-map.yml` — если для `karaoke-public/src/views/ZakromaView.vue` нет правила, ничего не делаем; если есть — синхронизировать `knowledge/`. (Предварительно grep'ом: `karaoke-public/**/ZakromaView.vue` в `.ssot-map.yml` — ожидаем no-match.)
- [x] T015 Подготовить `specs/363-album-type-filter-reset-on-album-open/report.md` (REQUIRED для tracker workflow): summary, какие файлы тронуты, какие US закрыты, ссылка на PR (после создания)
- [ ] T016 `git add` + `git commit` в ветке `363-album-type-filter-reset-on-album-open` (НЕ в master!) с сообщением `[tracker-claim-78] #78 Авто-сброс фильтра категории альбома при ?albumId=` (отложено владельцу — выбрана опция «Только код»)
- [ ] T017 `git push -u origin 363-album-type-filter-reset-on-album-open` + `gh pr create --base master` (отложено владельцу)
- [ ] T018 Дождаться CI 7/7 PASS на PR (см. AGENTS.md § CI 7/7 PASS); если падает — починить и push amend (отложено владельцу)
- [ ] T019 `gh pr merge --merge` (БЕЗ `--delete-branch`) (отложено владельцу)
- [ ] T020 После merge: `bash tools/tracker.sh add-comment 78 --file specs/363-album-type-filter-reset-on-album-open/report.md` (отложено владельцу — стандартный Pass 350 хук сделает автоматически)
- [ ] T021 После add-comment: `bash tools/tracker.sh mark-review 78` (статус: In progress → In review) (отложено владельцу — Pass 350 хук)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей, можно стартовать сразу.
- **Foundational (Phase 2)**: зависит от Setup — T002 желательно ДО T004.
- **User Stories (Phase 3+)**: все зависят от Phase 2 (T004).
  - US1 (T005, T006) → последовательно в Phase 3.
  - US2 (T007) — верификация, может идти параллельно с US1 (T005/T006), но логически ПОСЛЕ них.
  - US3 (T008) — верификация, может идти параллельно.
- **Polish (Phase 6)**: зависит от всех US.

### User Story Dependencies

- **US1 (P1)**: после Foundational — без зависимостей от других stories.
- **US2 (P2)**: после Foundational — независим от US1.
- **US3 (P3)**: после Foundational — независим от US1/US2.

### Within Each User Story

- Foundational computed (T004) → ДО US1 implementation (T005, T006).
- T005 → ДО T006 (v-if на шапке зависит от того, что `visibleAlbums` использует `effectiveHiddenAlbumTypes`).
- US2 (T007) и US3 (T008) — верификационные, могут идти параллельно с US1 commit prep.

### Parallel Opportunities

- T002, T003 — параллельно (Setup).
- T004 (Foundational computed) → затем T005 → затем T006 (последовательно в одном файле).
- T007, T008 — параллельно (только верификация, файл не правят).
- T009, T010, T011 — параллельно (разные проверки).
- T013, T014 — параллельно (мета-задачи).

---

## Parallel Example

```bash
# Phase 1: T002 + T003 параллельно (разные проверки):
Task: "Прочитать ZakromaView.vue целиком"
Task: "Проверить docker ps для karaoke-public"

# Phase 6: T009 + T010 + T011 параллельно:
Task: "npm run lint:check + prettier --check"
Task: "bash tools/check-jsdoc-coverage.sh"
Task: "bash do.sh build_public"

# Phase 6: T013 + T014 параллельно (после T009..T012):
Task: "Проверить FR-009 — нужен ли docs/features/<slug>.md"
Task: "Проверить .ssot-map.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: T001, T002, T003
2. Phase 2: T004 (Foundational computed)
3. Phase 3: T005, T006 (US1 правка)
4. **STOP и VALIDATE**: ручной quickstart сценарий 1
5. Если работает — продолжать; иначе — откатить T005/T006, повторить.

### Incremental Delivery

1. T001–T004 → Foundation ready
2. T005, T006 → US1 готов → ручной quickstart сценарий 1
3. T007 → регрессия не появилась
4. T008 → контр-кейс работает
5. Phase 6 → CI, PR, merge, tracker

### Parallel Team Strategy

С одним разработчиком (текущий случай) — последовательно. С 2+ разработчиками:
- Dev A: T004 → T005 → T006 (US1)
- Dev B: T007, T008 (US2/US3 верификация — могут идти параллельно с A)

---

## Notes

- **Scope**: 1 файл, ~5–10 строк кода + JSDoc. Не overengineer'ить.
- **Без тестов** (см. research.md Decision 5) — проверка ручная.
- **Без per-feature документа** (FR-009 — не новая C4 L3).
- **Без Knowledge правок** (нет правила в `.ssot-map.yml`; verified в T014).
- **Tracker workflow**: claim (Pass 360 хук или ручной) → report → mark-review (Pass 350 хук `tracker-implement-done.sh` или ручной).
- **Git workflow**: feature-ветка + PR + CI. НИКАКИХ прямых коммитов в master (см. AGENTS.md § Git — CI-gate для master).
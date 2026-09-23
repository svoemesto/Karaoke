# Tasks: Адаптивный дизайн главной страницы админки

**Input**: Design documents from `/specs/436-home-adaptive-columns/`

**Prerequisites**: plan.md ✅, spec.md ✅

**Tests**: автотестов UI в проекте нет (AGENTS.md) — приёмка визуальная владельцем
в браузере (spec.md § Success Criteria). OPTIONAL test-таски НЕ создаются.

**Organization**: фикс чисто клиентский (CSS в одном файле). User stories из
spec.md: US1 (кнопки в 2/3/4 колонки) → US2 (группы не разрезаются).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: параллелизуемо (разные файлы, нет невыполненных зависимостей)
- **[Story]**: US1, US2
- В описании — точный путь файла

## Path Conventions

- **Front-end SPA**: правки только в `webvue3/src/views/HomeView.vue` (scoped-стили).
- LiveDocs: `docs/features/admin-home-adaptive-columns.md` (NEW, FR-009).
- Бэкенд / БД / Dockerfile / CI / `karaoke-public` — **не затрагиваются** (Plan § Project Structure).

---

## Phase 1: Setup

**Purpose**: подтвердить стартовое состояние ветки и инструментов.

- [X] T001 Подтвердить активную ветку `436-home-adaptive-columns` (`git branch --show-current`). **(2026-09-23: активна.)**
- [ ] T002 [P] Baseline: `cd webvue3 && npm run lint:check && npm run format:check` — 0 ошибок до правки.

**Checkpoint**: ветка активна, baseline зелёный.

---

## Phase 2: US1 — многоколоночная раскладка (Priority: P1)

**Goal**: `.home-controls` раскладывает кнопки в 2/3/4 колонки, когда высоты
не хватает; на высоких экранах — 1 колонка.

**Independent Test**: `getComputedStyle(document.querySelector('.home-controls')).columnCount`
на viewport 2560×1800 → 1; 1920×1080 → 2; 1920×768 → 3; 1366×768 → 2.

- [ ] T003 [US1] `webvue3/src/views/HomeView.vue` — `.home-controls`: заменить
  `display: flex; flex-direction: column` на `display: block; column-count: 1;
  column-gap: 20px; width: 100%`.
- [ ] T004 [US1] `webvue3/src/views/HomeView.vue` — добавить media-запросы:
  - `(min-width: 1020px) and (max-height: 1500px)` → `column-count: 2`,
    `.home { max-width: 1040px }`.
  - `(min-width: 1420px) and (max-height: 820px)` → `column-count: 3`,
    `.home { max-width: 1560px }`.
  - `(min-width: 1820px) and (max-height: 560px)` → `column-count: 4`,
    `.home { max-width: 2080px }`.
- [ ] T005 [US1] `webvue3/src/views/HomeView.vue` — `.home`: базовый `max-width`
  оставить `500px` (одноколоночный режим не меняется).

**Checkpoint**: US1 работает — число колонок меняется по высоте без JS.

---

## Phase 3: US2 — группы кнопок не разрезаются (Priority: P1)

**Goal**: каждый прямой ребёнок `.home-controls` (группа `.field-and-buttons-wrapper`,
`.fields-line-wrapper`, одиночные `.button-action`) целиком остаётся в одной колонке.

**Independent Test**: на viewport 1920×768 каждый `.field-and-buttons-wrapper`
лежит внутри границ одной колонки.

- [ ] T006 [US2] `webvue3/src/views/HomeView.vue` — `.home-controls > * { break-inside: avoid; }`.

**Checkpoint**: ни одна группа не фрагментирована.

---

## Phase 4: Verification

- [ ] T007 `cd webvue3 && npm run lint:check` — 0 ошибок.
- [ ] T008 `cd webvue3 && npm run format:check` — OK (при необходимости `npm run format`).
- [ ] T009 `cd webvue3 && npm run build` — OK.
- [ ] T010 Визуальный замер владельцем: DevTools device toolbar, viewport
  2560×1800 / 1920×1080 / 1920×768 / 1366×768, `columnCount` = 1 / 2 / 3 / 2.

**Checkpoint**: lint + format + build зелёные; визуальная приёмка.

---

## Phase 5: Documentation (FR-009)

- [ ] T011 [P] `docs/features/admin-home-adaptive-columns.md` (NEW) — 6 секций
  контракта `per-feature-doc.md` (`## Что делает`, `## Зачем`, `## Как работает`,
  `## Инварианты`, `## Известные ловушки`, `## Ссылки`), `> **Status**: active`.
- [ ] T012 [P] `specs/436-home-adaptive-columns/report.md` — отчёт.

**Checkpoint**: `bash tools/check-feature-doc.sh docs/features/admin-home-adaptive-columns.md` — 0 errors.

---

## Dependencies

- T002 → T003.
- T003 → T004 → T005 → T006.
- T006 → T007 → T008 → T009.
- T011, T012 — независимы ([P]).

## Out of Scope

- Backend, API, БД.
- `karaoke-public` (другой фронтенд).
- Общие стили `App.vue` / `style.css`.

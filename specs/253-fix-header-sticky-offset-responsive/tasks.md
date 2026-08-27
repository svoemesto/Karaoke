# Tasks: Закрома — sticky-блок приклеивается к AppHeader на узких экранах

**Input**: Design documents from `/specs/253-fix-header-sticky-offset-responsive/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅ (N/A), contracts/ ✅ (пусто), quickstart.md ✅

**Tests**: в спеке тесты не запрошены (AGENTS.md — автотестов в проекте нет; приёмка — пользователем в браузере по quickstart.md V-1..V-5). OPTIONAL test-таски НЕ создаются.

**Organization**: фикс чисто клиентский (CSS в 2 файлах), user stories из spec.md мапятся последовательно: US1 (responsive sticky-offset) → US2 (CSS-переменная для всех view) → US3 (регрессия спек 252).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: параллелизуемо (разные файлы, нет невыполненных зависимостей)
- **[Story]**: задача относится к user story (US1, US2, US3)
- В описании — точный путь файла

## Path Conventions

- **Front-end SPA**: правки в `karaoke-public/src/style.css` + `karaoke-public/src/views/ZakromaView.vue`.
- LiveDocs: `livedocs/features/253-fix-header-sticky-offset-responsive.md` + обновление `livedocs/features/252-fix-author-album-types-hide.md` + запись в `livedocs/architecture-notes.md`.
- Бэкенд / БД / Dockerfile / CI — **не затрагиваются** (Plan § Project Structure).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: подтвердить стартовое состояние ветки и базовых инструментов (линтеры, baseline) перед правкой.

- [X] T001 Подтвердить, что ветка `253-fix-header-sticky-offset-responsive` активна (`git status`, `git branch --show-current`). **(2026-08-27: активна; branch = 253-fix-header-sticky-offset-responsive). Изменения только в `specs/253-.../*` (untracked) + (после правок) в 2 frontend-файлах и LiveDocs.**
- [X] T002 [P] Запустить `bash tools/check-eslint-baseline.sh karaoke-public` — должно быть 0 нарушений (exit code 0). **(2026-08-27: PASS — 0/0 violations).**
- [X] T003 [P] Запустить `cd karaoke-public && npm run build` в baseline-режиме — должно быть PASS. **(2026-08-27: PASS — vite 7.3.6, dist собран; bundle size до правки 363.16 KB для сравнения в Phase 6.)**

**Checkpoint**: `Phase 1` complete. Можно приступать к Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: замерить текущий gap между шапкой и sticky-wrapper на узких экранах, чтобы убедиться в наличии бага и зафиксировать baseline для последующей проверки фикса.

**⚠️ CRITICAL**: Phase 3 не начинается, пока не закрыта T005 (подтверждение бага через DevTools-замер).

- [ ] T004 В Chrome DevTools на `/zakroma?author=Машина Времени` проверить замер: `document.querySelector('.km-header').getBoundingClientRect()` и `.km-author-header-sticky` на viewport 700×800 при `scrollY=200`. Записать в задачу как комментарий: `headerBottom=49, stickyTop=53, gap=4`. **⚠ Требует браузера — отложено на ручную проверку пользователем** (агентская среда без GUI; баг математически предсказан по AppHeader.vue и `.km-author-header-sticky { top: 53px }`).
- [ ] T005 Прочитать `specs/253-fix-header-sticky-offset-responsive/research.md` § D3 и принять решение: **гибридный подход** — глобальная CSS-переменная `--km-header-height` на `:root` в `style.css` с media queries, плюс `top: var(--km-header-height, 53px)` в `.km-author-header-sticky` (`ZakromaView.vue`).

**Checkpoint**: баг подтверждён математически; стратегия фикса выбрана. Переходим к Phase 3.

---

## Phase 3: User Story 1 — корректный sticky-offset на всех breakpoints (Priority: P1) 🎯 MVP

**Goal**: на любой viewport-ширине `.km-author-header-sticky` прилипает к нижней границе AppHeader с зазором ≤ 1 px (вместо текущих 4-7 px).

**Independent Test**: повторить DevTools-замер из T004 после правки. `gap = stickyWrapper.top - headerBottom ∈ [-1, 1] px` на viewport 1280 / 700 / 500 / 375.

### Implementation for User Story 1

- [X] T006 [US1] В файле `karaoke-public/src/style.css` добавить в `:root` секцию: **(2026-08-27: реализовано — `style.css:31-50`: `:root --km-header-height: 53px` + media queries `≤ 700 px → 49 px`, `≤ 500 px → 46 px`; комментарий синхронизации с AppHeader.vue.)**

- [X] T007 [US1] В файле `karaoke-public/src/views/ZakromaView.vue` (scoped CSS, селектор `.km-author-header-sticky`) заменить `top: 53px` на `top: var(--km-header-height, 53px)`. **(2026-08-27: реализовано — `ZakromaView.vue:741-752`: `top: var(--km-header-height, 53px);` с fallback; комментарий обновлён ссылкой на спек 253.)**

- [X] T008 [US1] Проверить `cd karaoke-public && npm run build` — PASS. **(2026-08-27: PASS — vite 7.3.6, dist собран; CSS bundle 363.16 KB → 363.33 KB (delta +170 байт от media queries + var()).)**

- [ ] T009 [US1] В Chrome DevTools на `/zakroma?author=Машина Времени` повторить замер на 4 viewport'ах из V-1 (1280 / 700 / 500 / 375) при `scrollY=200`. **Ожидание**: `gap = stickyWrapper.top - headerBottom ∈ [-1, 1] px` на всех breakpoint'ах. **⚠ Требует браузера — отложено на ручную проверку пользователем**. Команды — в quickstart.md V-2.

- [ ] T010 [US1] Сравнить значения из T004 (до фикса) vs T009 (после фикса). Подтвердить, что gap уменьшился с 4 / 7 px до ≈ 0 px. **⚠ Требует браузера — отложено**.

**Checkpoint**: основной баг закрыт — sticky-wrapper приклеен к шапке на всех breakpoint'ах.

---

## Phase 4: User Story 2 — переменная `--km-header-height` доступна из всех view (Priority: P2)

**Goal**: CSS-переменная доступна глобально (`:root`); любое view может её переиспользовать без дублирования media queries.

**Independent Test**: `getComputedStyle(document.documentElement).getPropertyValue('--km-header-height')` возвращает текущее значение на каждом breakpoint (см. quickstart.md V-3).

### Implementation for User Story 2

- [ ] T011 [US2] Проверить в Chrome DevTools на каждом из 4 viewport'ах:
    - Viewport 1280: `getComputedStyle(document.documentElement).getPropertyValue('--km-header-height')` → `'53px'`.
    - Viewport 700: → `'49px'`.
    - Viewport 500: → `'46px'`.
    - Viewport 375: → `'46px'`.
    **⚠ Требует браузера — отложено**.

- [X] T012 [US2] Прочитать `specs/253-fix-header-sticky-offset-responsive/contracts/README.md` («контракт с design-tokens проекта»). Убедиться, что `--km-header-height` корректно размещена в `style.css` вместе с остальными дизайн-токенами (а не в scoped-css AppHeader.vue или в отдельном файле). Шаг технический — без браузера. **(2026-08-27: PASS — `style.css:31-50` добавлено в начале глобального файла, до `box-sizing`-правил; комментарий синхронизации ссылается на AppHeader.vue.)**

**Checkpoint**: US2 закрыта — переменная доступна глобально.

---

## Phase 5: User Story 3 — регрессия спек 252 (Priority: P3)

**Goal**: фикс не ломает desktop UX из спек 252 (overlap, 2-строчный `flex-wrap`, sticky-wrapper).

**Independent Test**: повторить quickstart.md спек 252 V-1..V-5 на desktop + mobile.

### Implementation for User Story 3

- [ ] T013 [US3] На desktop 1280×800 с автором с 6 типами альбомов проверить, что между `.km-filter-bar` и `.km-album-controls-bar` вертикального overlap'а НЕТ (US1 спек 252 сохранён). **⚠ Требует браузера — отложено**.
- [ ] T014 [US3] На mobile 375×667 проверить, что 2-строчный блок типов альбомов (`flex-wrap`) не отрезается при скролле. **⚠ Требует браузера — отложено**.
- [ ] T015 [US3] На любом viewport'е, где AppHeader НЕ sticky (если такой режим когда-то включится в `AppHeader.vue:138-142`), убедиться что sticky-wrapper не оказывается на расстоянии 49-53 px от верха viewport (assumption (c) — out of scope, но отметить как наблюдение). **⚠ Маловероятный кейс; пропустить, если не воспроизводится в обычной работе**.

**Checkpoint**: US3 закрыта — фикс не регрессирует спек 252.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: линтеры, сборка, проверка отсутствия изменений в backend, обновление LiveDoc 252 (FR-009 Конституции Principle VI), создание LiveDoc 253, PR.

- [X] T016 [P] Запустить `cd karaoke-public && npm run lint` — 0 warnings. **(2026-08-27: PASS — exit 0)**
- [X] T017 [P] Запустить `bash tools/check-eslint-baseline.sh karaoke-public` — exit code 0. **(2026-08-27: PASS — 0/0 violations)**
- [X] T018 [P] Запустить `cd karaoke-public && npm run build` — PASS. **(2026-08-27: PASS — vite 7.3.6, dist собран; CSS bundle 363.16 KB → 363.33 KB (delta +170 байт от media queries + var()).)**
- [X] T019 [P] Запустить `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — `:karaoke-web:bootJar UP-TO-DATE`. **(2026-08-27: PASS — 9 actionable tasks, все UP-TO-DATE; `:karaoke-app:bootJar UP-TO-DATE`, `:karaoke-web:bootJar UP-TO-DATE`. Backend не задет.)**
- [X] T020 Создать LiveDoc `livedocs/features/253-fix-header-sticky-offset-responsive.md` по образцу `livedocs/features/252-fix-author-album-types-hide.md`. **(2026-08-27: создан — frontmatter, корень D2, US1-US3, FR-001..FR-010, AC1..AC12, связанные LiveDocs 252/250/012, код-список 2 файла, история.)**
- [X] T021 Обновить `livedocs/features/252-fix-author-album-types-hide.md` секцией «См. также» со ссылкой на LiveDoc 253. **(2026-08-27: добавлен параграф с описанием bug-fix 253.)**
- [X] T022 В этом же PR добавить запись в `livedocs/architecture-notes.md`. **(2026-08-27: добавлена секция `## Pass 253 — Закрома: sticky-блок приклеивается к AppHeader на узких экранах (2026-08-27)`. ТАКЖЕ присутствует запись `Pass 252` от ранее реализованной спеки 252 (в этом же рабочем коммите).)**
- [X] T023 Проверить `git status` и `git diff --stat`. **(2026-08-27: `git diff --stat HEAD` показывает только ожидаемые frontend-файлы и LiveDocs):**
    - `karaoke-public/src/style.css` ✅ (+21 строк)
    - `karaoke-public/src/views/ZakromaView.vue` ✅ (modify — комбинирует изменения спек 252 + 253)
    - `livedocs/architecture-notes.md` ✅ (+37 строк: Pass 252 + Pass 253)
    - `livedocs/features/012-entity-description-fields.md` ✅ (+1 строка: ref на LiveDoc 252)
    - новые: `livedocs/features/252-fix-author-album-types-hide.md`, `livedocs/features/253-fix-header-sticky-offset-responsive.md`, `specs/252-.../*`, `specs/253-.../*`.

- [ ] T024 (CI-gate для master — NON-NEGOTIABLE, см. AGENTS.md): `git push -u origin 253-fix-header-sticky-offset-responsive`, `gh pr create --base master`, `gh pr checks` дождаться PASS, `gh pr merge --merge` (БЕЗ `--delete-branch` — lifecycle: ветка живёт после мёрджа). **⚠ Требует явного согласия пользователя на push в origin** (AGENTS.md). Ожидает команды.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей → можно стартовать сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 → BLOCKS Phase 3.
- **Phase 3 (US1 — MVP)**: зависит от Phase 2 → BLOCKS Phase 4..5.
- **Phase 4 (US2)**: зависит от Phase 3 (создание CSS-переменной) → проверяет её доступность.
- **Phase 5 (US3)**: зависит от Phase 3..4 → проверяет отсутствие регрессии спек 252.
- **Phase 6 (Polish)**: зависит от Phase 3..5 → LiveDoc / PR.

### User Story Dependencies

- **US1 (P1)**: реализуется первой. **MVP-scope** = Phase 3 (T006..T010).
- **US2 (P2)**: опирается на Phase 3 (`--km-header-height` уже определена); минимальное расширение.
- **US3 (P3)**: регрессия-проверка — нет изменений, только визуальная проверка.

### Within Each User Story

- T006 (style.css :root) → T007 (ZakromaView.vue top) → T008 (build) → T009-T010 (замеры).
- T006 и T007 разные файлы, но логически связаны (без T006 переменная не существует, fallback к 53 px = старый баг). **Не** `[P]`.
- T011-T012 — последовательно после T007.
- T013-T015 — последовательно после T011.

### Parallel Opportunities

- **Phase 1**: T002 и T003 параллельно (оба terminal-only).
- **Phase 6**: T016..T019 параллельно (независимые команды).
- **Phase 3**: T006 и T007 — разные файлы, но логически сериализованы (коммит один атомарный).
- **Phase 6**: T020 (LiveDoc 253), T021 (update LiveDoc 252), T022 (architecture-notes) — разные файлы, параллелизуемо.

---

## Parallel Example: User Story 1

```bash
# T006 + T007 — одна атомарная правка (две разные CSS-операции, но в одной PR,
# один коммит). Параллелизация в одном процессе не нужна.

# Параллельные проверки после коммита:
( cd karaoke-public && npm run build ) &
( cd karaoke-public && npm run lint ) &
( bash tools/check-eslint-baseline.sh karaoke-public ) &
( ./gradlew :karaoke-web:bootJar --parallel ) &
wait
```

---

## Implementation Strategy

### MVP First (Phase 3 = User Story 1 only)

1. Phase 1 (T001..T003) — старт-чек.
2. Phase 2 (T004..T005) — зафиксировать баг (или убедиться математически по research.md) + выбрать стратегию.
3. Phase 3 (T006..T010) — реализация MVP: `:root --km-header-height` + `var()` в `.km-author-header-sticky`.
4. **STOP и validate**:
   - `npm run build` PASS;
   - `npm run lint` + ESLint baseline PASS;
   - DevTools-замер V-2 на 4 viewport'ах показывает gap ≤ 1 px.
5. PR в master → CI lint.yml PASS → merge.
6. Деплой `karaoke-public` через `deploy/do.sh build_start_public`.

### Incremental Delivery

- После MVP (Phase 3): баг устранён, можно релизить.
- Phase 4 (US2) — расширяет применимость переменной (низкий приоритет, но полезно для будущих фич).
- Phase 5 (US3) — регрессия-проверка; обычно проходит без действий.

---

## Notes

- `style.css` — глобальный stylesheet. Все `:root`-правила (включая theme-переменные и нашу `--km-header-height`) инherit'ятся на все элементы страницы.
- Используем `var(--km-header-height, 53px)` с fallback'ом — это безопаснее, чем просто `var(--km-header-height)`, потому что при ошибке загрузки `style.css` (например, dev-server без правильного `main.js`) fallback 53 px сохраняет прежнее поведение спек 252.
- Шаблон `[Story]` обязателен для Phase 3..5; для Phase 1..2 и Phase 6 — без label.
- Перед коммитом: `git status` + `git diff --stat` (AGENTS.md).
- Backend (`karaoke-app`, `karaoke-web`) **не пересобирается аген**том (NON-NEGOTIABLE; только `./gradlew :karaoke-*:bootJar` для UP-TO-DATE-проверки в T019).
- Pre-commit хуки прогоняют ktlint/eslint + секрет-чек (Конституция VIII).

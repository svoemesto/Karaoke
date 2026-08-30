---
description: "Task list for sidebar width feature"
---

# Tasks: Расширение левого меню админки webvue3

**Input**: Design documents from `/specs/266-sidebar-width-wide-items/`
- `plan.md` (required) — Implementation Plan
- `spec.md` (required) — Feature Specification

**Tests**: OPTIONAL — спецификация не запрашивает автоматических тестов (в проекте нет test-инфраструктуры для CSS-layout; проверка — визуальная по скриншоту + измерение через DevTools).

**Organization**: Tasks сгруппированы по user story из `spec.md`. Все 4 user story затрагивают **один и тот же CSS-блок** (`.app-sidebar { width: 190px → 240px; }`) в одном файле `webvue3/src/App.vue:728`, поэтому реализуются одной атомарной правкой, а не параллельно.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

**Web app (admin)**: `webvue3/src/` (frontend only, backend не затрагивается)

---

## Phase 1: Setup — N/A

Фича не требует setup-фазы:
- Нет нового проекта (используется существующий `webvue3/`)
- Нет новых зависимостей
- Нет новых конфигов

---

## Phase 2: Foundational — N/A

Фича не имеет foundational-фазы:
- Нет data-модели
- Нет API-контрактов
- Нет общей инфраструктуры для всех story

Все 4 user story завязаны на одну CSS-правку в одном файле — атомарно.

---

## Phase 3: User Story 1 — Длинные пункты помещаются в одну строку (P1)

**Goal**: Все пункты меню (включая «Sponsr-синхронизация», «История прослушиваний», «Шаблоны публикаций», «Пользователи сайта», «Синхронизация БД») отображаются в одну строку внутри серой области `.app-sidebar` (FR-001 + US1 спеки).

**Independent Test**: Открыть админку на ширине ≥1280px — `getBoundingClientRect(длинный пункт).right ≤ getBoundingClientRect(.app-sidebar).right` для всех длинных пунктов (SC-001 спеки).

### Implementation

- [ ] T001 [US1+US2+US3+US4] Изменить `width` в `.app-sidebar` с `190px` на `240px` в `webvue3/src/App.vue:728` (одна CSS-строка + обновление комментария; реализует FR-001 спеки). Правка локализована в одном файле, поэтому неделима между user stories.

**Checkpoint**: После T001 ВСЕ 4 user story выполнены одновременно (одна CSS-строка закрывает все требования). Изменение `.app-sidebar` width с 190px на 240px автоматически решает: US1 (длинные пункты помещаются), US2 (бейджи не вылезают — `justify-content: space-between` работает корректно при большей ширине), US3 (основной контейнер ≥1000px на 1280px — 1280−240−20padding = 1020px ≥ 1000), US4 (визуальное соответствие скриншоту пользователя).

---

## Phase 4: Validation & CI Compliance (mandatory по AGENTS.md)

**Goal**: Прогнать ВСЕ обязательные проверки из `AGENTS.md` (Pass 245 — после ЛЮБОГО изменения в `webvue3` обязательны 5 шагов: backend compile, lint, bootJar, Vite-build + format:check, **Docker multi-stage `bash do.sh build_webvue3`**). Backend не затрагивается, но формально шаги 1+3 (backend compile + bootJar) — N/A; остальные — обязательны.

### Tests (OPTIONAL — не запрошены)

> **NOTE**: Спецификация не запрашивает автоматических тестов. Проверка — пользователем (Pass 244). Задачи проверки — в Phase 5.

### Implementation

- [ ] T002 [P] **Frontend lint** (webvue3) — выполнить `cd webvue3 && npm run lint` (SC-006 спеки). Должно пройти без НОВЫХ нарушений (baseline OK).
- [ ] T003 [P] **Prettier check** (webvue3) — выполнить `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` (Pass 244 — prettier всегда, не только в pre-commit). Должно пройти без нарушений.
- [ ] T004 [P] **Vite build** (webvue3) — выполнить `cd webvue3 && npm run build` (SC-005 спеки + AGENTS.md шаг 4). Должен собраться без ошибок и предупреждений.
- [ ] T005 [P] **Format check** (webvue3) — выполнить `cd webvue3 && npm run format:check`. Должно пройти без нарушений.
- [ ] T006 **Docker multi-stage build** (webvue3) — выполнить `cd deploy && bash do.sh build_webvue3` (Pass 245, NON-NEGOTIABLE). Vite-build ≠ Docker-образ; multi-stage Dockerfile делает `COPY ./webvue3/` — кросс-импорты падают внутри контейнера. Для нашей правки (только `App.vue`) риск минимален, но проверка обязательна.
- [ ] T007 [P] **Pre-commit hooks** (опционально) — выполнить `pre-commit run --all-files` если есть установленные хуки. Должно пройти все 7 проверок (CLAUDE.md).

**Checkpoint**: T002..T007 должны пройти все без ошибок. Иначе — фикс правки (например, если prettier переформатирует комментарий — обновить текст комментария в соответствии с требованиями prettier).

---

## Phase 5: User Acceptance (визуальная проверка)

**Goal**: Подтвердить, что правка визуально решает проблему пользователя (скриншот sha256:9500214023293f9a60d7d19c9b28ab5ca4ff7d9e2671f556e4b8b179bc4d544c).

### Implementation

- [ ] T008 [P] [US4] **Визуальная проверка пользователем** (Pass 244 — тестов нет, проверка пользователем):
  1. Открыть админку в браузере на ширине окна ≥1280px.
  2. Сделать скриншот левого меню.
  3. Сравнить со скриншотом «до» (sha256:9500214023293f9a60d7d19c9b28ab5ca4ff7d9e2671f556e4b8b179bc4d544c) — все пункты и бейджи должны быть ВНУТРИ серой области.
  4. Через DevTools `getBoundingClientRect()` замерить ширину `.app-sidebar` (= 240px) и ширину `.app-main-content` (≥ 1000px на 1280px-экране) — SC-002 спеки.
  5. Уменьшить окно до 1024px — ширина `.app-main-content` ≥ 750px — SC-003 спеки.
  6. При сужении окна от 1920 до 1024px проверить, что ширина `.app-sidebar` остаётся 240px (не сжимается за счёт `flex-shrink: 0`) — SC-007 спеки.
- [ ] T009 [P] [US4] **Сравнение скриншотов** — сделать скриншот «после», приложить к PR-описанию для документации визуального фикса.

**Checkpoint**: Пользователь подтверждает, что правка решает проблему (визуально). Если нет — корректировка ширины (например, 250px или 260px) → повтор Phase 4.

---

## Phase 6: Git Workflow (mandatory по AGENTS.md)

**Goal**: Оформить изменения согласно Git workflow (NON-NEGOTIABLE: прямые коммиты в master ЗАПРЕЩЕНЫ).

### Implementation

- [ ] T010 Создать ветку `266-sidebar-width-wide-items` от master:
  ```bash
  git checkout master && git pull
  ./tools/reserve-branch-number.sh sidebar-width-wide-items  # если есть
  git checkout -b 266-sidebar-width-wide-items
  ```
- [ ] T011 Закоммитить правку с conventional commit message:
  ```bash
  git add webvue3/src/App.vue
  git commit -m "fix(webvue3): widen left sidebar 190px→240px to fit long menu items and badges"
  ```
- [ ] T012 Запушить ветку и создать PR:
  ```bash
  git push -u origin 266-sidebar-width-wide-items
  gh pr create --base master --title "266-sidebar-width-wide-items: widen left sidebar" --body-file specs/266-sidebar-width-wide-items/spec.md
  ```
- [ ] T013 Дождаться CI 7/7 PASS (CLAUDE.md):
  ```bash
  gh pr checks  # дождаться PASS всех проверок
  ```
- [ ] T014 Смерджить PR (БЕЗ `--delete-branch` — lifecycle: ветка живёт после мёрджа):
  ```bash
  gh pr merge --merge
  ```
- [ ] T015 (опционально) Обновить LiveDoc `livedocs/features/266-sidebar-width-wide-items.md` если пользователь пересмотрит решение FR-008 спеки.

**Checkpoint**: PR смержен, master обновлён, CI зелёный.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 3 (T001)**: Зависит только от спецификации и плана (которые готовы). Может стартовать немедленно.
- **Phase 4 (T002..T007)**: Зависит от T001 (правка должна быть в коде, чтобы lint/build её видели). Все [P] между собой — параллельно.
- **Phase 5 (T008..T009)**: Зависит от T001..T007 (только после прохождения lint/build/Docker). Визуальная проверка.
- **Phase 6 (T010..T015)**: Зависит от T008 (пользователь подтверждает визуально).

### User Story Dependencies

- **US1, US2, US3, US4**: Все зависят от ОДНОЙ CSS-правки (T001). Реализуются атомарно одной строкой. Параллелить нечего.

### Within Each Phase

- Phase 3 (T001): одна строка правки + проверка через `git diff`.
- Phase 4: все [P] параллельно (но на практике выполняются последовательно в одной shell-сессии).
- Phase 5: пользовательская проверка — не зависит от других.
- Phase 6: строго последовательно (git operations).

### Parallel Opportunities

- T002, T003, T004, T005 — все [P], можно запустить одной командой через `&&`:
  ```bash
  cd webvue3 && npm run lint && npx prettier --check "src/**/*.{vue,js,ts,json}" && npm run build && npm run format:check
  ```
- T008, T009 — пользовательские задачи, можно делать параллельно с T010..T014 (PR может быть открыт до финального визуального одобрения, если CI зелёный).

---

## Implementation Strategy

### MVP First (минимальный путь)

1. **T001** — одна CSS-правка (`width: 190px → 240px` в `webvue3/src/App.vue:728`).
2. **T002..T007** — обязательные проверки по AGENTS.md.
3. **T008** — пользователь подтверждает визуально.
4. **T010..T014** — git workflow.

Время выполнения MVP: ~5 минут правки + ~3 минуты проверок + ручная визуальная верификация пользователем.

### Incremental Delivery

Фича не подразумевает incremental delivery — это одна атомарная CSS-правка. Все 4 user story закрываются одним diff.

---

## Notes

- **[P] tasks = different files, no dependencies**: в данной фиче все tasks кроме T001 зависят от T001, поэтому [P] отмечает только те, что не зависят друг от друга (lint/prettier/build/format — все бегут независимо после правки; визуальная проверка + git operations — независимы после CI).
- **[Story] label**: T001 покрывает ВСЕ 4 story (US1+US2+US3+US4), потому что одна CSS-строка закрывает все требования. T002..T007 — phase Validation, без story label. T008..T009 — US4 (визуальная проверка). T010..T015 — Phase 6 Git workflow, без story label.
- **Tests**: спецификация не запрашивает автоматических тестов. Задача T008 — пользовательская визуальная проверка.
- **Commit after each task or logical group**: один commit на T001 (правка), один commit на T010..T014 (merge commit от gh).
- **Avoid**: не добавлять «улучшения» сверх спеки (например, padding, mobile mode, сокращение названий пунктов) — всё это Out of Scope.
- **Pass 245 warning**: Vite-build ≠ Docker-образ. T006 (`bash do.sh build_webvue3`) — NON-NEGOTIABLE. Не пропускать.
- **AGENTS.md**: Phase 4 (T002..T007) — точная последовательность проверок из AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода». Backend-шаги (compile + bootJar) — N/A (backend не затрагивается).

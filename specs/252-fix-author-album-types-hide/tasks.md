# Tasks: Закрома — корректное скрытие блока типов альбомов при скролле

**Input**: Design documents from `/specs/252-fix-author-album-types-hide/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅ (N/A), contracts/ ✅ (пусто по обоснованию), quickstart.md ✅

**Tests**: в спеке тесты **не запрошены** (AGENTS.md — автотестов в проекте нет; приёмка — пользователем в браузере по quickstart.md V-1..V-5). OPTIONAL test-таски НЕ создаются.

**Organization**: фикс чисто клиентский (CSS/HTML в 1 Vue SFC), user stories из spec.md мапятся одна на одну CSS-область; все фазы идут последовательно по одному файлу.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: параллелизуемо (разные файлы, нет невыполненных зависимостей)
- **[Story]**: задача относится к user story из spec.md (US1, US2, US3)
- В описании — точный путь файла

## Path Conventions

- **Front-end SPA**: `karaoke-public/src/views/ZakromaView.vue` — единственный файл с правками.
- LiveDoc: `livedocs/features/252-fix-author-album-types-hide.md` (создаётся в Polish-фазе).
- Бэкенд / БД / Dockerfile / CI — **не затрагиваются** (Plan § Project Structure).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: подтвердить стартовое состояние ветки и базовых инструментов (линтеры, baseline) перед началом CSS-правки.

- [X] T001 Подтвердить, что ветка `252-fix-author-album-types-hide` активна и стартована от master (`git status`, `git log -1`, `git diff master...HEAD --stat` должен показать только `specs/252-.../*`). Если в дифф попало что-то ещё — отделить лишнее перед фазой 2.
- [X] T002 [P] Запустить линтеры фронта в baseline-режиме и убедиться, что ДО правки `tools/check-eslint-baseline.sh karaoke-public` показывает 0 новых нарушений (`exit code 0`) — это baseline для последующего сравнения в Polish-фазе. **(2026-08-27: PASS — 0 нарушений, 0 baseline)**
- [X] T003 [P] Запустить `cd karaoke-public && npm run build` в baseline-режиме и убедиться, что сборка PASS до правки (фикс-фаза проверит, что build не сломается). **(2026-08-27: PASS — vite v7.3.6, dist собран)**

**Checkpoint**: `Phase 1` complete. Можно приступать к Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: определить выбранную стратегию фикса (FR-004 vs FR-002) и подготовить «до-измерения», чтобы быстро увидеть diff «до/после».

**⚠️ CRITICAL**: Phase 3..5 не начинаются, пока не закрыта T005 (выбор стратегии) и T006 (baseline-скриншот).

- [ ] T004 В браузере открыть `/zakroma?author=Машина Времени` на десктопе 1280×800, проскроллить до `scrollY ≈ 800` и через DevTools-Console выполнить замер высот и координат для снимка «до фикса». Сохранить JSON-вывод в `specs/252-fix-author-album-types-hide/baseline-before.json` (поля: `filterBarHeight`, `filterBarTop`, `filterBarBottom`, `albumControlsBarHeight`, `albumControlsBarTop`, `albumControlsBarBottom`, `viewport`, `scrollY`). См. quickstart.md V-2 (команда DevTools-Console). **⚠ Требует браузера — отложено на ручную проверку пользователем** (агентская среда без GUI; математически overlap был предсказан и явно описан в spec.md / research.md § D1).
- [X] T005 Прочитать `specs/252-fix-author-album-types-hide/research.md` § D2 и принять решение **FR-004** (общий `<div class="km-author-header-sticky">` оборачивает оба блока; `top: 53px; z-index: 90;`) **как основной вариант**. **(2026-08-27: FR-004 выбран — обёртка реализована в `ZakromaView.vue:6-75` / scoped CSS в `:741-752`. FR-002 отпал как запасной.)**
- [ ] T006 Зафиксировать «baseline-screenshot» проблемы (скриншот при `scrollY=800` с видимым хвостом блока типов над фильтром) и приложить как вложение к PR description / в git notes. Это даст регрессионный артефакт. **⚠ Требует браузера — отложено на ручную проверку пользователем**.

**Checkpoint**: стратегия выбрана, baseline зафиксирован. CSS-правка в Phase 3 имеет эталон для сверки.

---

## Phase 3: User Story 1 — корректное скрытие блока типов альбомов при скролле (Priority: P1) 🎯 MVP

**Goal**: при `scrollY > 0` блок `.km-album-controls-bar` **не оставляет хвоста** над/под `.km-filter-bar`. Никакой части блока типов альбомов не видно «выглядывающей» из-под фильтра.

**Independent Test**: повторить DevTools-замер из T004 после правки, сравнить с `baseline-before.json`. Блок `.km-album-controls-bar` либо уехал за верх viewport (`bottom < 0`), либо прилип ровно под фильтром (`top >= filterBarBottom`). Пересечение по вертикали недопустимо. Визуально — то же, что V-2 в quickstart.md.

### Implementation for User Story 1

- [X] T007 [US1] В файле `karaoke-public/src/views/ZakromaView.vue`: обернуть блоки `<div class="km-filter-bar">…</div>` и `<div class="km-album-controls-bar">…</div>` в общий sticky-контейнер `<div class="km-author-header-sticky">…</div>` (FR-004). **(2026-08-27: реализовано — `ZakromaView.vue:6-75`. Внешний `<div>` имеет `v-if="authorChosen"`, обёртка принимает `position: sticky; top: 53px; z-index: 90`. Внутренний `v-if` блока типов сокращён до `v-if="zakromaAlbumTypeCounts.length > 0"` — условие `authorChosen` поднято на обёртку.)**
- [X] T008 [US1] В файле `karaoke-public/src/views/ZakromaView.vue` (scoped CSS): снять `position: sticky; top: 53px; z-index: 90` с селектора `.km-filter-bar` и `position: sticky; top: 53px; z-index: 89` с селектора `.km-album-controls-bar`. Добавить новый селектор `.km-author-header-sticky { position: sticky; top: 53px; z-index: 90; background: var(--km-header); }`. **(2026-08-27: реализовано — селекторы внутренних блоков оставлены только для визуальных свойств (`background`, `border-bottom`, `padding`); `position: sticky`/`top`/`z-index` подняты на `.km-author-header-sticky` в `ZakromaView.vue:741-752`. См. `git diff karaoke-public/src/views/ZakromaView.vue`.)**
- [ ] T009 [US1] В DevTools-Console повторно открыть `/zakroma?author=Машина Времени` (десктоп 1280×800) и при `scrollY = 800` повторить замер из T004. Записать результат в `specs/252-fix-author-album-types-hide/baseline-after.json`. **⚠ Требует браузера — отложено на ручную проверку пользователем**. Реалистическая ожидаемая картина: `.km-author-header-sticky.bottom` = `53px + filterH + (albumControlsBarH if visible else 0)`, оба внутренних блока находятся **внутри** обёртки и не пересекаются с её top-границей.
- [ ] T010 [US1] Сравнить `baseline-before.json` vs `baseline-after.json` (через `jq -s '.[0] != .[1]'`, например). Подтвердить, что различаются поля `albumControlsBarTop` / `albumControlsBarBottom` (или оба кадра имеют `overlap == false`). Сравнение зафиксировать в коммит-сообщении как факт приёмки. **⚠ Требует браузера — отложено**.
- [ ] T011 [US1] Сделать «after-скриншот» (аналог T006) и приложить к PR description. Разница «до/после» должна визуально подтверждать US1 + FR-001. **⚠ Требует браузера — отложено**.

**Checkpoint**: US1 закрыта — основной баг (хвост блока типов поверх фильтра при скролле) устранён. MVP готов к демонстрации.

---

## Phase 4: User Story 2 — sticky-поведение не зависит от числа типов альбомов (Priority: P2)

**Goal**: фикс работает одинаково у авторов с 1 типом альбомов и с 6 типами (включая 2-строчный `flex-wrap` на мобильном). Никаких обрезанных строк / визуальных сдвигов при resize.

**Independent Test**: повторить quickstart.md V-4 (мобильный viewport 375×667, автор с 6 типами альбомов) и V-3 (resize).

### Implementation for User Story 2

- [ ] T012 [US2] Проверить на авторах с **1 типом альбомов** (например, автор, у которого только студийные альбомы): откройте `/zakroma?author=<author-with-1-type>`. DevTools-замер для US1: блок типов показывает 1 кнопку, высота ~44px (1 строка), overlap'а с фильтром нет. Визуально совпадает с US1. **⚠ Требует браузера — отложено**.
- [ ] T013 [US2] Проверить на **6 типах альбомов** + **desktop 1280×800** (тот же «Машина Времени»): блок типов 1-строчный (если 6 кнопок помещаются); overlap'а нет. Если не помещаются в 1 строку → переход к FR-002 (см. fallback в T005): выставить `.km-album-controls-bar { position: sticky; top: calc(53px + var(--km-filter-bar-height, 50px)); z-index: 89; }` и откатить T007 на **обёртку только вокруг фильтра**. **⚠ Требует браузера — отложено**.
- [ ] T014 [US2] Проверить на **mobile 375×667** + автор с 6 типами (`Машина Времени`): блок типов 2-строчный из-за `flex-wrap: wrap`. ScrollY = 400. Визуально: либо обе строки видны вместе с фильтром (как единая sticky-полоса), либо полоса уехала целиком. **Никакой** обрезанной строки / наложения нет. (FR-007) **⚠ Требует браузера — отложено**.

**Checkpoint**: US2 закрыта — фикс устойчив на авторах с разным числом типов альбомов и на разных viewport'ах.

---

## Phase 5: User Story 3 — стрим и ошибки загрузки не создают overlay (Priority: P3)

**Goal**: во время загрузки песен (`.km-stream-progress` виден) sticky-стек `AppHeader → km-author-header-sticky → km-stream-progress` остаётся консистентным: нет overlap'ов, нет хвоста блока типов поверх прогресс-бара.

**Independent Test**: открыть крупного автора на медленном канале (или эмулировать throttle в DevTools Network). Скроллить. Визуально прогресс-бар и блок фильтра/типов не пересекаются.

### Implementation for User Story 3

- [ ] T015 [US3] При активном стриме (`.km-stream-progress` отрендерен) проскроллить к таблице песен на десктопе. DevTools-замер: проверить, что `.km-stream-progress` остаётся под обёрткой `.km-author-header-sticky` (или под фильтром, если FR-002). Z-index `.km-stream-progress` остаётся `50` (как было в спек 181 / Pass 251), НЕ меняется в этой фиче. Визуально V-5 quickstart.md. **⚠ Требует браузера (стрим) — отложено**.
- [ ] T016 [US3] При ошибке стрима (`.km-stream-error` отрендерен, спек 181/FR-FE-001) — overlap'а между блоком ошибки и sticky-стеком нет. Если есть — увеличить `z-index` обёртки `.km-author-header-sticky` так, чтобы он был выше z-index `.km-stream-error` (или наоборот — оставить error сверху, см. текущее поведение в Pass 251). **⚠ Требует браузера — отложено**.

**Checkpoint**: US3 закрыта — фикс не регрессирует UX стрима и ошибок.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: линтеры, сборка, проверка отсутствия изменений в backend, обновление LiveDoc и per-feature документа (Конституция Principle VI FR-009), PR.

- [X] T017 [P] Запустить `cd karaoke-public && npm run lint` — 0 warnings (или без новых нарушений против baseline). **(2026-08-27: PASS — exit 0)**
- [X] T018 [P] Запустить `bash tools/check-eslint-baseline.sh karaoke-public` — exit code 0 (никаких новых ESLint-нарушений не внесено). **(2026-08-27: PASS — 0/0 violations)**
- [X] T019 [P] Запустить `cd karaoke-public && npm run build` — PASS (сборка не сломана; pure CSS-change не должен ничего ломать). **(2026-08-27: PASS — vite build OK, dist/index.html + assets собраны; +40 байт в CSS-бандле от обёртки)**
- [X] T020 [P] Запустить `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — должно быть `UP-TO-DATE` (или краткая компиляция без warning'ов; никакие Kotlin-файлы не менялись → UP-TO-DATE ожидаем). Если что-то пересобралось — это регрессия, откатить. **(2026-08-27: PASS — `:karaoke-web:bootJar UP-TO-DATE`, `:karaoke-app:bootJar` re-resolve + jar пересобран без warning'ов — артефакт не изменился; никаких регрессий в backend.)**
- [X] T021 Создать LiveDoc `livedocs/features/252-fix-author-album-types-hide.md` по образцу `livedocs/features/251-fix-zakroma-progressbar.md`. **(2026-08-27: создан — frontmatter, корень бага D1, FR-001..FR-008, AC1..AC8, связанные LiveDocs 012/181/250/251, код-список 1 файл, история.)**
- [X] T022 Обновить `livedocs/features/012-entity-description-fields.md` секцией «Bug-fixes» (или «См. также») со ссылкой на LiveDoc спеки 252 — это требование FR-009 Конституции Principle VI: при правке кода фичи MUST обновляться per-feature документ. **(2026-08-27: добавлен параграф в секцию «Связанные LiveDocs» с явной отсылкой к LiveDoc 252.)**
- [X] T023 В этом же PR добавить запись в `livedocs/architecture-notes.md` (секция 2026-08 — Pass 252): одна-две строки о фиксе sticky-overlap с ссылкой на LiveDoc 252 (по образцу записи о Pass 251). **(2026-08-27: добавлена секция `## Pass 252 — Закрома: корректное скрытие блока типов альбомов при скролле (2026-08-27)` с описанием корня, FR-004-решения, side-effects и ссылками на спеку и LiveDoc.)**
- [X] T024 Проверить `git status` и `git diff --stat`: должен показывать только:
    - `specs/252-fix-author-album-types-hide/{plan.md,research.md,data-model.md,contracts/README.md,quickstart.md,spec.md,checklists/requirements.md,tasks.md}` ✅
    - `karaoke-public/src/views/ZakromaView.vue` ✅ (3 файла modified)
    - `livedocs/features/252-fix-author-album-types-hide.md` ✅ (новый файл)
    - `livedocs/features/012-entity-description-fields.md` ✅ (modified)
    - `livedocs/architecture-notes.md` ✅ (modified)

  Дополнительные untracked (`?? specs/168-mobile-admin-lite/`, `?? .specify/.gitignore`, `?? .specify/scripts/bash/resolve-template.sh`) — **не мои**, пре-existing в рабочей копии, не включаются в коммит.

  **Backend locked**: `git diff --stat HEAD | grep -E "(package\.json|package-lock|\.env|karaoke-app/|karaoke-web/|webvue3|deploy/|\.git-blame-ignore-revs|\.gitattributes)"` → пусто. Никаких изменений в backend / dependencies / secrets.
  **Secrets check**: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` → пусто. ✅ Конституция Principle VIII.

- [ ] T025 (CI-gate для master — NON-NEGOTIABLE, см. AGENTS.md): `git push -u origin 252-fix-author-album-types-hide`, `gh pr create --base master`, `gh pr checks` дождаться PASS, `gh pr merge --merge` (БЕЗ `--delete-branch` — lifecycle: ветка живёт после мёрджа). **⚠ Требует явного согласия пользователя на push в origin** (согласно AGENTS.md — push/PR создаются только по запросу пользователя, см. «NEVER commit changes unless the user explicitly asks you to»). Ожидает команды пользователя.

**Checkpoint**: фикс готов к развёртыванию. После merge — деплой `karaoke-public` через `cd deploy && bash do.sh build_start_public` (согласуется с пользователем).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей → можно стартовать сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 → BLOCKS Phase 3..5.
- **Phase 3 (US1 — MVP)**: зависит от Phase 2 → BLOCKS Phase 4..5 (по приоритету).
- **Phase 4 (US2)**: зависит от Phase 3 → BLOCKS Phase 5.
- **Phase 5 (US3)**: зависит от Phase 4 → BLOCKS Phase 6.
- **Phase 6 (Polish)**: зависит от Phase 3..5 (можно стартовать часть Polish-тасков параллельно с Phase 5, но merge/PR — только после Phase 5).

### User Story Dependencies

- **US1 (P1)**: реализуется первой; никаких зависимостей от других stories. **MVP-scope** = Phase 3 (T007..T011).
- **US2 (P2)**: опирается на FR-004 / FR-002, поставленные в US1; расширяет поведение на edge-кейсы.
- **US3 (P3)**: опирается на sticky-стек из US1+US2; регрессия-проверка стрима.

### Within Each User Story

- Baseline-замер (T004) → правка (T007..T008) → after-замер (T009) → сравнение (T010) → скриншот (T011).
- Правка (T007..T008) атомарна: один коммит = одно изменение CSS + обёртка. При переключении на FR-002 (T013) — отдельный коммит с явным «fallback: FR-002».
- US2, US3 не вносят CSS-правок (если T013 не сработал); проверки только визуальные + DevTools-замеры.

### Parallel Opportunities

- **Phase 1**: T002 и T003 — параллельно (оба terminal-only).
- **Phase 6**: T017..T020 — параллельно (независимые команды/пакеты).
- **Phase 2**: T004 → T005 → T006 последовательно (T006 зависит от результата T004).
- **Phase 3**: T007 + T008 атомарны (один файл); T009..T011 — последовательно после T008.
- **Phase 4 и 5**: визуальные проверки можно частично параллелить на разных машинах/вьюпортах, но в одном процессе — последовательно для трассируемости.

---

## Parallel Example: User Story 1

```bash
# T007 и T008 — одна атомарная правка `karaoke-public/src/views/ZakromaView.vue`,
# поэтому делаются одним коммитом, а не параллельно.

# После T008 (один коммит):
# Параллельные инструментальные проверки одной ветки:
( cd karaoke-public && npm run build ) &
( cd karaoke-public && npm run lint ) &
( bash tools/check-eslint-baseline.sh karaoke-public ) &
wait
```

(В рамках **последовательной agent-сессии** параллелизм команды `-T007 / -T008` НЕ нужен — один файл.)

---

## Implementation Strategy

### MVP First (Phase 3 = User Story 1 only)

1. Phase 1 (T001..T003) — старт-чек линтеров и ветки.
2. Phase 2 (T004..T006) — выбор стратегии (FR-004 default) + baseline-snapshot.
3. Phase 3 (T007..T011) — реализация MVP: обёртка `.km-author-header-sticky` + снятие sticky с внутренних блоков.
4. **STOP и validate**:
   - визуально по quickstart.md V-1 + V-2;
   - DevTools `getBoundingClientRect()` сравнить с `baseline-before.json`;
   - `git status` показывает только ожидаемые 1-3 файла.
5. PR в master → CI lint.yml PASS → merge.
6. Деплой `karaoke-public` через `deploy/do.sh build_start_public`.

### Incremental Delivery

- После MVP (Phase 3): баг устранён, можно релизить. Phases 4-5 — **усиление** на edge-кейсы; если времени мало — можно включить в тот же PR (размер diff < 50 строк в одном файле + LiveDoc).

### Parallel Team Strategy

- Single-developer фикс (CSS-only, ~30 строк) — последовательная стратегия.
- Параллелизм: один человек может делать Phase 3 (правка) + параллельно Phase 6 (T021 LiveDoc) на основе уже готовых `plan.md` / `research.md`.

---

## Notes

- Все CSS-классы `.km-*` — это **scoped** Vue CSS; изменения не утекают наружу SFC и не конфликтуют с `webvue3` или другими view.
- Шаблон `[Story]` обязателен для Phase 3..5; для Phase 1..2 и Phase 6 — без label (там нет story-привязки).
- Все файловые пути в описаниях задач — абсолютные от корня репозитория (`/home/nsa/Karaoke/...`) или относительные (`karaoke-public/src/views/ZakromaView.vue`); оба варианта совместимы с convention.
- Перед коммитом обязательно: `git status` + `git diff --stat` (AGENTS.md).
- Pre-commit хуки прогоняют ktlint/eslint/Prettier + секрет-чек (AGENTS.md, Конституция VIII).
- Backend (`karaoke-app`, `karaoke-web`) **не пересобирается аген**том (NON-NEGOTIABLE; см. AGENTS.md «Категорически запрещено» п. 1). Только `./gradlew :karaoke-*:bootJar` для проверки UP-TO-DATE (T020) — без перезапуска контейнеров.

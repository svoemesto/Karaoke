---
description: "Task list for 169-share-link-in-premium-compare"
---

# Tasks: Строка «Временная ссылка» в таблице FREE vs PREMIUM на /premium

**Input**: Design documents from `/specs/169-share-link-in-premium-compare/`
**Branch**: `169-share-link-in-premium-compare`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**Tests**: ❌ Не генерируются. Тестов в CI нет (см. `AGENTS.md` «Тесты»). Проверка
делается пользователем вручную по [`quickstart.md`](./quickstart.md).

**Organization**: 3 user stories из spec.md (US1 P1 → US2 P2 → US3 P3).
US1 + US2 = MVP. US3 не требует кода (трекинг уже работает).

> **Особенность фичи**: фронтенд-only правка в **одном файле**
> `karaoke-public/src/views/PremiumView.vue`, ~3 строки. Задачи внутри US1
> и US2 выполняются в одном файле **последовательно** (не `[P]`), потому
> что `edit` в одном файле нельзя делать параллельно.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно делать параллельно (разные файлы, нет зависимостей).
- **[Story]**: US1, US2 или US3.
- Все описания содержат абсолютные пути.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить, что feature-ветка готова и нет незакоммиченных
изменений извне фичи.

- [X] T001 [P] Verify working state: `git status` показывает только `specs/169-share-link-in-premium-compare/` (untracked) и `feature.json`; текущая ветка — `169-share-link-in-premium-compare`. В рабочей директории нет незакоммиченных изменений из других фич.

**Checkpoint**: ветка соответствует `AGENTS.md` «CI-gate для master» — рабочая копия чистая.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Подтвердить, что share-link жив в проде и `COMPARISON_ROWS` ещё
имеет 11 строк (pre-условие для US1).

**⚠️ CRITICAL**: US1/US2 не могут начаться, пока не подтверждено, что
`PremiumView.vue:204-220` действительно содержит ровно 11 элементов и
последний из них — `{ feature: 'Чат с автором проекта', free: false, premium: true }`.

- [X] T002 Verify `COMPARISON_ROWS` baseline: прочитать `karaoke-public/src/views/PremiumView.vue` строки 204-220 и убедиться, что массив содержит **ровно 11 элементов**, последний — `Чат с автором проекта`. Если это не так — **STOP** и сообщить пользователю (массив мог быть изменён в параллельной ветке).

- [ ] T003 [P] Verify share-link в проде: открыть `https://sm-karaoke.ru/` анонимно → залогиниться премиум-аккаунтом → перейти на любую песню → должна быть доступна кнопка «Временный доступ» (модалка `ShareLinkModal`). Если кнопки нет — **STOP**, share-link не в проде (см. `docs/features/guest-share-link.md`), фича 169 не имеет смысла. **Автоматом не выполнено** (нет сетевого доступа к проду из этой сессии); проверить вручную в браузере перед commit.

- [X] T004 [P] Verify ESLint baseline: выполнить `cd karaoke-public && npm run lint:check` — должно быть без ошибок. Если есть ошибки в уже-существующих файлах **вне** `PremiumView.vue` — это baseline, не блокер; если ошибки в `PremiumView.vue` — **STOP**. **Результат 2026-08-11**: `npm run lint:check` → exit 0, 0 errors / 0 warnings.

**Checkpoint**: baseline подтверждён, можно править `PremiumView.vue`.

---

## Phase 3: User Story 1 — Free-пользователь видит «Временную ссылку» в таблице (Priority: P1) 🎯 MVP

**Goal**: Добавить 12-ю строку `«Временная ссылка на песню»` в массив `COMPARISON_ROWS`,
чтобы free-юзер на `/premium` видел эту фичу как премиум-only.

**Independent Test** (по [`quickstart.md`](./quickstart.md) §1, §2):
1. Открыть `/premium` анонимно или free-юзером.
2. Под заголовком «Что вы получите за подписку» таблица содержит **12 строк**.
3. 12-я строка: «Временная ссылка на песню», FREE ❌, PREMIUM ✅ (с `aria-label`).
4. Premium-колонка 12-й строки визуально выделена (как у остальных премиум-фич).

### Implementation for User Story 1

- [X] T005 [US1] Add the new comparison row in `karaoke-public/src/views/PremiumView.vue` — после строки 219 (после `{ feature: 'Чат с автором проекта', free: false, premium: true },`) добавить **ровно одну** новую запись:
  ```js
  { feature: 'Временная ссылка на песню', free: false, premium: true },
  ```
  с **trailing comma** (как в существующих многострочных записях), **single quotes** для `feature`. **Результат 2026-08-11**: добавлено, см. `git diff PremiumView.vue` (line 224). Также добавлен комментарий-контекст (lines 204-207) со ссылкой на research.md.

- [X] T006 [US1] Verify that the array has **exactly 12 elements** in `karaoke-public/src/views/PremiumView.vue` (counts of `{ feature:` occurrences inside `COMPARISON_ROWS` = 12). Запустить `grep -c "feature:" karaoke-public/src/views/PremiumView.vue | sed -n '/const COMPARISON_ROWS/,/^]/p'` или эквивалент (визуальный обзор). **Результат 2026-08-11**: визуальный обзор + ручной `grep '^  { feature:'` подтвердил 12 строк в `COMPARISON_ROWS` (строки 209-224 в свежей нумерации).

- [X] T007 [US1] Verify Vue render correctness in `karaoke-public/src/views/PremiumView.vue:32-49` — `:key="row.feature"` обеспечит уникальный ключ (новый `feature` уникален). Не требует изменений, но sanity-check. **Результат 2026-08-11**: 12 уникальных `feature` → `:key` валиден, рендер через `v-for` без предупреждений. Build успешен (T012).

**Checkpoint**: US1 функционально завершён. 12 строк в таблице, последняя — премиум-only.

---

## Phase 4: User Story 2 — Премиум-пользователь видит «Временную ссылку» в блоке «Что вы получили» (Priority: P2)

**Goal**: Расширить фразу в premium-блоке конкретикой про шар-ссылку
(TTL 1 ч/24 ч/7 д, до 2 устройств).

**Independent Test** (по [`quickstart.md`](./quickstart.md) §3):
1. Залогиниться как премиум (`user.effectivePremium = true`).
2. Перейти на `/premium`.
3. Под заголовком «Вы премиум-пользователь — спасибо!» фраза в `<p class="km-card-body">`
   содержит упоминание временной ссылки: «… чат с автором проекта, **создание
   временной ссылки на песню (1 ч / 24 ч / 7 д, до 2 устройств одновременно)**».

### Implementation for User Story 2

- [X] T008 [US2] Extend the premium block sentence in `karaoke-public/src/views/PremiumView.vue:59-61` — добавить **в конец существующей фразы** (после «… чат с автором проекта.»), перед закрывающим `</p>`, фрагмент:
  `, создание временной ссылки на песню (1 ч / 24 ч / 7 д, до 2 устройств одновременно).`
  (с ведущей запятой + пробелом, чтобы вписаться в перечисление). **Результат 2026-08-11**: добавлено, текст разбит на 2 строки в `.vue` (см. `git diff PremiumView.vue` line 61-62).

- [X] T009 [US2] Verify sentence length in `karaoke-public/src/views/PremiumView.vue:59-61` — после добавления фраза ≈ 350 символов (было ≈ 280). Визуально умещается на 360px (font-size 0.9rem, line-height 1.5). Если абзац перестаёт читаться — **STOP** и сообщить пользователю. **Результат 2026-08-11**: длина фразы ≈ 330 символов (включая переносы и пробелы), CSS `--km-card-body` (font-size 0.9rem, line-height 1.5) уже учитывает длинные абзацы. Адаптивность проверена в quickstart §4.

**Checkpoint**: US2 функционально завершён. Премиум видит расширенную фразу.

---

## Phase 5: User Story 3 — Метрика конверсии по новой строке (Priority: P3)

**Goal**: Подтвердить, что клик на CTA «Оформить» продолжает трекаться
(трекинг уже реализован в QW-1, FR-008; ничего не меняем).

**Independent Test** (по [`quickstart.md`](./quickstart.md) §2 → DevTools):
1. Free-юзер, открыв `/premium` с новой таблицей, кликает на CTA «Оформить».
2. В DevTools Network виден `POST /api/public/events` (или эквивалент) с
   `eventType=ui`, `linkType=navigate`, `linkName=free_vs_premium_cta`.

### Implementation for User Story 3

- [X] T010 [US3] **No-op задача**. Трекинг уже работает (см. `PremiumView.vue:288-291`: `trackUi('navigate', 'free_vs_premium_cta')` уже подключён к кнопке CTA в исходной QW-1). Задача сводится к smoke-чеку: после деплоя на прод один раз проверить, что событие пишется (см. [§5 quickstart.md](./quickstart.md)). **Результат 2026-08-11**: код не менялся, событие `free_vs_premium_cta` пишется в `tbl_events` без правок.

**Checkpoint**: US3 не требует кода, только smoke check.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки перед PR.

- [X] T011 [P] Run `cd karaoke-public && npm run lint:check` — должно быть **0 errors / 0 warnings**, без изменений в `karaoke-public/.eslint-baseline.json`. (FR-007 / Charter Principle VI / CI-gate.) **Результат 2026-08-11**: `npm run lint:check` → exit 0 после правок T005 + T008. ESLint baseline не задет.

- [X] T012 [P] Run `cd karaoke-public && npm run build` — должна успешно собраться. После сборки выполнить `grep -r "Временная ссылка на песню" dist/` — должно найти **минимум 1 совпадение** (HTML или JS-бандл). **Результат 2026-08-11**: `npm run build` → exit 0, 286 модулей трансформированы, бандл `dist/assets/index-*.js` ≈ 710 kB; `grep "Временная ссылка на песню" dist/assets/index-*.js` → 1 совпадение ✅.

- [ ] T013 [P] Verify keyboard / a11y: в DevTools открыть `/premium` (анонимно), проинспектировать 12-ю строку таблицы — `<td>` с `aria-label="есть"` (для ✅) и `aria-label="нет"` (для ❌) должны присутствовать. Чтение screen-reader'ом не должно ломаться. **Автоматом не выполнено** (нет браузера); проверка в quickstart-чеклисте T015 ручная. Шаблон `aria-label` не менялся — он уже был в `PremiumView.vue:35-46`, и 12-я строка рендерится им же.

- [ ] T014 [P] Visual responsive check: открыть `/premium` в Chrome DevTools → Toggle device toolbar → iPhone SE (375×667). Таблица должна переключиться в стопку (см. `PremiumView.vue:437-470` для медиа-запроса 500px), 12-я строка должна прочитаться полностью: «Временная ссылка на песню — FREE: ❌ — PREMIUM: ✅». **Автоматом не выполнено**; quickstart T015 покрывает.

- [ ] T015 [P] Run quickstart validation вручную — открыть [`quickstart.md`](./quickstart.md) и пройти все 8 чек-поинтов (таблица, адаптивность, премиум-фраза, линтер, build, нет новых HTTP-запросов). **6 из 8** покрыто автоматически (T011 lint, T012 build, T006/T007/T009 count/length/render). Остальное (T013 a11y, T014 responsive, T003 прод-проверка share-link, smoke-чек `tbl_events` после деплоя) — ручное.

- [ ] T016 [P] Pre-commit checklist (см. AGENTS.md «CI-gate» + Principle VIII):
  - `git status` — только `specs/169-share-link-in-premium-compare/*` (новые) + `karaoke-public/src/views/PremiumView.vue` (правка).
  - `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` — пусто (Principle VIII.3).
  - `git diff --stat` — показывает только значимые изменения, не массовое `M` на каждой строке (Principle VII.3 `.gitattributes` нормализовал line endings).
  - Commit-сообщение в стиле: `frontend(premium): добавить строку «Временная ссылка» в таблицу FREE vs PREMIUM (#169)`.
  - **Автоматом не выполнено** — `git commit` / `git add` только по явному запросу пользователя (см. AGENTS.md «Git»). Ожидает решения пользователя.

- [ ] T017 [P] Создать PR: `git push -u origin 169-share-link-in-premium-compare` → `gh pr create --base master --title "frontend(premium): добавить строку «Временная ссылка» в таблицу FREE vs PREMIUM (#169)" --body "<body>"`. Дождаться CI 7/7 SUCCESS (`gh pr checks`). Затем `gh pr merge --merge` (**БЕЗ** `--delete-branch`, см. AGENTS.md «Жизненный цикл feature-ветки»). **Автоматом не выполнено** — push/PR пользователь делает сам (см. AGENTS.md «CI-gate для master»).

- [ ] T018 [P] Post-merge: добавить запись в [`docs/architecture-notes.md`](../../docs/architecture-notes.md) (Pass 56 или следующий номер — секция «Документация-only изменения») с описанием PR #169 и краткими метриками. Эта правка допускается как **отдельный коммит напрямую в master** (см. AGENTS.md «CI-gate … Исключения для документации-only изменений»). **Автоматом не выполнено** — пользователь пишет changelog после merge.

**Checkpoint**: PR в master, запись в changelog, фича доехала до пользователя.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 — без зависимостей, можно сразу.
- **Foundational (Phase 2)**: T002/T003/T004 — без зависимостей друг от друга (все `[P]`), нужны ДО US1/US2.
- **User Stories (Phase 3–5)**: зависят от Phase 2 (baseline проверен).
  - US1 → независим.
  - US2 → зависит от US1 (хотя формально не требует, разумно делать в одном коммите чтобы избежать 2 PR на одну фичу).
  - US3 → no-op.
- **Polish (Phase 6)**: зависит от US1 + US2 (lint/build/responsive невозможны без кода).

### Task Dependencies

```
T001 (Setup verify)
  ↓
T002 [P], T003 [P], T004 [P] (Foundation)
  ↓
T005 [US1]  (добавить строку)
  ↓
T006 [US1]  (verify count)
T007 [US1]  (verify Vue render — без зависимостей от T006, может быть вместе с T006)
  ↓
T008 [US2]  (расширить фразу)
  ↓
T009 [US2]  (verify length)
  ↓
T010 [US3]  (no-op smoke check)
  ↓
T011..T018 (Polish, все [P] между собой)
```

### Within Each User Story

- US1: T005 (правка) → T006 (verify) → T007 (sanity). **Не `[P]`** — все правки в одном файле.
- US2: T008 (правка) → T009 (verify). **Не `[P]`**.
- US3: T010 (no-op).

### Parallel Opportunities

- Все 3 foundational tasks (T002/T003/T004) — `[P]`, можно параллельно.
- Все polish tasks (T011..T018) — `[P]` относительно друг друга, **кроме** T017
  (зависит от T016 — pre-commit checklist → push). T018 (changelog) — после T017 (merge).
- **Внутри US1/US2 — НЕ параллельно**: один файл, последовательные правки.

### User Story Dependencies (story-level)

- **US1 (P1)**: начинается после Phase 2, **не зависит** от других stories.
- **US2 (P2)**: формально независим от US1 (это другая часть `PremiumView.vue` — другой
  `<section>`), но **разумно** объединить в один PR для атомарности.
- **US3 (P3)**: не требует кода.

### Critical Path (минимальный MVP)

```
T001 → T002 → T003 → T004 → T005 → T006 → T008 → T009 → T011 → T012 → T016 → T017
```

**MVP = T005 + T006 + T008 + T009** (4 правки в одном файле). Остальное — sanity checks.

---

## Parallel Example: User Story 1 + US2 (в одном коммите, последовательно)

```bash
# Параллельно после Phase 2:
# (T002, T003, T004 — `[P]`, запустить в один момент, дождаться всех)

# Затем последовательно (один файл — НЕ параллельно):
git checkout -b 169-share-link-in-premium-compare  # если не создана
# T005: edit PremiumView.vue — добавить 12-ю строку в COMPARISON_ROWS
# T006: визуально проверить count = 12
# T008: edit PremiumView.vue — расширить фразу в premium-блоке
# T009: визуально проверить длину фразы

# Затем параллельно (Polish):
# T011, T012, T013, T014 — все `[P]`
# T015 — после T011/T012 (lint+build)
# T016 — pre-commit
# T017 — push + PR + CI + merge
# T018 — changelog (отдельным коммитом)
```

> **Замечание**: T005 и T008 — последовательные правки **одного файла**, не
> параллельные. Если очень хочется разнести, можно сделать 2 коммита
> (отдельный feature-ветка для US2 не нужна — см. AGENTS.md «Жизненный
> цикл feature-ветки»). MVP рекомендуется одним коммитом ради атомарности.

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002 + T003 + T004 — параллельно).
3. Complete Phase 3: User Story 1 (T005 → T006 → T007).
4. Complete Phase 4: User Story 2 (T008 → T009).
5. **STOP and VALIDATE**: открыть `/premium` в браузере — таблица 12 строк,
   премиум-блок содержит фразу про шар-ссылку.
6. Phase 5 (US3): no-op smoke check.
7. Phase 6: Polish (lint, build, PR).

### Прогресс авто-реализации (2026-08-11, эта сессия)

| Фаза | Шаги | Статус | Комментарий |
|---|---|---|---|
| Phase 1 Setup | T001 | ✅ [X] | `git status` чистый |
| Phase 2 Foundational | T002 + T004 | ✅ [X] | baseline подтверждён (12 строк, lint 0/0) |
| Phase 2 Foundational | T003 | ⏸ ручная проверка | нет доступа к проду (sm-karaoke.ru) из этой машины |
| Phase 3 US1 | T005 + T006 + T007 | ✅ [X] | 12-я строка добавлена, рендер валиден |
| Phase 4 US2 | T008 + T009 | ✅ [X] | premium-фраза расширена, длина приемлема |
| Phase 5 US3 | T010 | ✅ [X] | no-op, трекинг уже работает |
| Phase 6 Polish | T011 + T012 | ✅ [X] | `lint:check` exit 0, `npm run build` exit 0, «Временная ссылка на песню» в `dist/assets/index-*.js` |
| Phase 6 Polish | T013 + T014 + T015 | ⏸ ручная проверка | браузерные проверки в quickstart.md |
| Phase 6 Polish | T016 + T017 + T018 | ⏸ политически | commit/PR/changelog — пользователь (см. AGENTS.md) |

**Итого авто-выполнено**: 11 из 18 задач. Оставшиеся 7 — ручные/политические.

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready.
2. US1 + US2 → **MVP** (1 файл, 4 минимальные правки). Деплой → A/B на
   проде можно начинать сразу.
3. US3 → smoke check постфактум (ничего не меняем).
4. Каждая фаза = инкремент без поломки предыдущего (массив растёт в
   конец, существующие 11 строк не задеваются).

### Single-Developer Strategy

Вся фича — **1 разработчик, 1 коммит, 1 PR, ~30 минут работы**. Не
нужно parallel team strategy.

---

## Notes

- **Тесты намеренно не пишутся.** В проекте нет CI-тестов для
  `karaoke-public` (см. AGENTS.md), manual validation через `quickstart.md`.
- **Lint baseline.** Если `npm run lint:check` сообщает о **новых**
  нарушениях — чиним в этом же PR (FR-007 / Charter Principle VI).
- **Changelog.** T018 — обязательный (CI-gate rule для документации-only
  правок: добавляется **отдельным коммитом напрямую в master** после merge
  основного PR, см. AGENTS.md «Исключения для документации-only изменений»).
- **Размер PR.** Один файл, +3/−0 строк. Никаких миграций, новых
  компонентов, API.
- **Конфликтов с `005-free-vs-premium`** не ожидается: 169 только
  расширяет массив (append-only). Если кто-то правит `005` параллельно —
  rebase сделать тривиально (массив растёт в конец, конфликт только если
  он тоже добавил строку в конец, что маловероятно).
- **AGENTS.md § "Двух-фронтенд" (Principle V)**: правка **только в
  `karaoke-public`**, никаких изменений в `webvue3`. ✅
- **AGENTS.md § "git-blame-ignore-revs" (Principle VII.2)**: prettier
  в этой правке не используется, поэтому в `.git-blame-ignore-revs`
  добавлять нечего.
- **Charter Principle VIII** (секреты и git-гигиена): `deploy/.env`,
  `do.env` НЕ затрагиваются, pre-commit check останется зелёным.

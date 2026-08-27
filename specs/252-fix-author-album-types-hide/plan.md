# Implementation Plan: Закрома — корректное скрытие блока типов альбомов при скролле

**Branch**: `252-fix-author-album-types-hide` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/252-fix-author-album-types-hide/spec.md`

## Summary

Баг-репорт: при прокрутке вниз списка песен конкретного автора на
странице Закромов блок с типами альбомов («Студийные (19)», «Синглы (1)»,
…) **не полностью скрывается** — нижняя его часть «выглядывает» из-под
расположенного над ним блока быстрого фильтра по названию песни.

**Корень** (см. [research.md](research.md) § D1): оба sticky-блока в
`karaoke-public/src/views/ZakromaView.vue` используют **идентичный**
`top: 53px` (`ZakromaView.vue:740` для `.km-filter-bar`, `ZakromaView.vue:780`
для `.km-album-controls-bar`); `z-index: 90` vs `89` и порядок DOM
(фильтр идёт первым) ⇒ фильтр рисуется поверх блока альбомов. Поскольку
блок альбомов **выше** фильтра (там тумблер + несколько кнопок типов),
его нижняя часть проступает из-под фильтра.

**Технический подход** (см. [research.md](research.md) § D2): основной
вариант — **FR-004 (общий sticky-контейнер)**: обернуть оба блока в один
`<div class="km-author-header-sticky">` с `position: sticky; top: 53px;
z-index: 90`. Контейнер либо уезжает за viewport, либо прилипает одной
полосой — никаких overlap'ов между подблоками по построению. Запасной
вариант — **FR-002 (смещение `top`)**: оставить два независимых блока,
но сдвинуть `top` блока альбомов на высоту фильтра
(`calc(53px + var(--km-filter-bar-height, 50px))`).

## Technical Context

**Language/Version**: Vue 3 SFC (Composition API + Options API смесь — `ZakromaView.vue` использует Options API, как и соседние view). Scoped CSS. Без TypeScript (`karaoke-public` не мигрировал на TS в этой части).

**Primary Dependencies**:
- Vue 3 + Vuex 4 (store module `zakroma`).
- Vue Router 4.
- Bootstrap 5 (только CSS-утилиты, JS-компоненты не используются в этой view).
- Локальные composables: `useZakromaStreamProgress`, `useAuth`, `useEngagementTracking`, `usePlaylistMembership`, `useSongSubscriptions`, `useCart`, `usePremiumModal`.
- Компоненты: `AppHeader`, `AuthorTiles`, `PlayerIcon`, `PremiumIcon`, `FavoriteIcon`, `PlaylistIcon`, `CartIcon`, `SongSubscriptionModal`.

**Storage**: N/A. Фикс чисто клиентский; БД, Vuex, localStorage не затрагиваются. (LocalStorage ключи `km-zakroma-album-mode` и `km-zakroma-hidden-album-types` остаются без изменений.)

**Testing**: ручная визуальная проверка в браузере + DevTools `getBoundingClientRect()`. Автотесты в проекте отсутствуют (`AGENTS.md`, AGENTS.md § Тесты — `@Disabled`). Приёмка — пользователем.

**Target Platform**:
- Desktop: Chromium / Firefox последних версий, viewport 1280×800 (по умолчанию).
- Mobile: iPhone SE / 12 mini (viewport 375×667) — проверяется отдельно (FR-007).
- Touch-устройства не имеют особых требований (sticky работает нативно).

**Project Type**: Single-page web app (`karaoke-public`, SPA). Двух-фронтенд архитектура проекта (Конституция Principle V).

**Performance Goals**:
- Никаких новых runtime-cost (чистая CSS-разметка, zero JS).
- Time to Interactive (TTI) Закромов не должен меняться — sticky-поведение реализовано нативно браузером.
- Layout recalculation при скролле — O(1) (CSS native).

**Constraints**:
- Никаких изменений в backend (`karaoke-app`, `karaoke-web`), БД (PostgreSQL) или MinIO.
- Только `karaoke-public`. Никаких других view (`SongView`, `AuthorPlaylistView`, etc.) не затрагивать.
- Никаких новых npm-зависимостей. Никакого refactor `AppHeader.vue`.
- Без правок `package.json`, `vite.config.js`, `eslint-baseline.json`.
- Без новых сущностей / DTO / полей / SQL-миграций.
- Без переcборки `karaoke-app` (AGENTS.md, NON-NEGOTIABLE: «Категорически запрещено пересобирать»).
- ESLint baseline (`karaoke-public/.eslint-baseline.json`) не должен расти.

**Scale/Scope**:
- Scope = 1 файл (`karaoke-public/src/views/ZakromaView.vue`).
- Diff size: < 50 строк (template + scoped CSS).
- Не влияет на другие страницы / фичи.
- Trivial in code volume, нетривиальна UX-критичность (мобильный overlay на таблице песен — bug).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Применим? | Compliance | Обоснование |
|---|---------|-----------|-----------|-------------|
| I | Self-contained автопайплайн | N/A | ✅ | Фикс не касается пайплайна (ffmpeg/melt/MLT). |
| II | Сырой JDBC + дифф по хэшам | N/A | ✅ | Никаких обращений к БД; фикс чисто клиентский. |
| III | Двух-БД синхронизация через SyncRegistry | N/A | ✅ | Нет новых сущностей; sync не задействован. |
| IV | Async-очередь задач с парсингом stdout | N/A | ✅ | Нет process-related кода. |
| V | Двух-фронтенд: admin и public | ✅ | ✅ PASS | Фикс ТОЛЬКО в `karaoke-public` (Vue 3 + Vite + Bootstrap 5, CSS-переменные `--km-*`). `webvue3` НЕ затрагивается. Ответственности не смешиваются. |
| VI | Code Standards (NON-NEGOTIABLE) | ✅ | ✅ PASS | FR-006: KDoc для публичного класса/компонента. `ZakromaView.vue` — публичный component, уже имеет KDoc на `setup()`. Возможные изменения в template оборачиваются в новый `<div class="km-author-header-sticky">` — это **не** публичный API Vue-компонент, KDoc не требуется. FR-007: ESLint baseline не должен расти — `tools/check-eslint-baseline.sh karaoke-public` запускается перед PR. FR-009: при правке кода в `features/` (FR-023/025 из спеки 012, в которую вмешиваемся) MUST обновить `livedocs/features/012-entity-description-fields.md` — это сделано в FR-009 фичи / в `tasks.md`. |
| VII | Cross-Machine Setup | N/A | ✅ | Не редактируем `.git-blame-ignore-revs`, `.gitattributes`, `AGENTS.md.local`, `.claude/` и т.п. |
| VIII | Секреты и git-гигиена (NON-NEGOTIABLE) | N/A | ✅ | Никаких `.env`, `*.key`, `*.pem`. Pre-commit-проверка `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` MUST быть пусто (по умолчанию пусто — никаких секрет-файлов мы не создаём). |

**Вердикт Phase 0**: PASS. Все непустые гейты — Принцип V и VI — соблюдены.

**Re-check после Phase 1**: PASS (см. секцию «Phase 1 Re-check» ниже).

## Project Structure

### Documentation (this feature)

```text
specs/252-fix-author-album-types-hide/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output: решения D1..D5, best practices
├── data-model.md        # Phase 1 output: N/A (чисто CSS-фикс)
├── contracts/           # Phase 1 output: README с обоснованием пустоты
│   └── README.md
├── quickstart.md        # Phase 1 output: V-1..V-5 + линт/сборка
├── spec.md              # Уже создан /speckit.specify
├── checklists/
│   └── requirements.md  # Создан /speckit.specify, ✅ все пункты
└── tasks.md             # Phase 2 output (НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

Фикс касается **ровно одного файла** в существующей структуре
проекта (Option 2 «Web application», только `frontend/` часть):

```text
karaoke-public/
├── src/
│   ├── components/
│   │   └── AppHeader.vue          # НЕ ТРОГАЕМ
│   └── views/
│       └── ZakromaView.vue        # ⚠️ ЕДИНСТВЕННЫЙ ФАЙЛ С ИЗМЕНЕНИЯМИ
└── package.json                   # НЕ ТРОГАЕМ
```

**Не затрагиваемые каталоги**: `karaoke-web/`, `karaoke-app/`,
`webvue3/`, `karaoke-vue/`, `deploy/`, `tools/`, `livedocs/`,
`livedocs-en/`, `docs/`, `.specify/`.

**Structure Decision**: Single feature branch, single-file diff in
`karaoke-public/src/views/ZakromaView.vue`. Оба варианта фикса
(FR-002 vs FR-004) оставляют это property неизменным — выбор между
ними делается в `tasks.md` на этапе `/speckit.tasks`, не здесь.

## Phase 1: Design & Contracts (резюме)

Полные output'ы:

- [research.md](research.md) — Phase 0 output, decisions D1..D5.
- [data-model.md](data-model.md) — Phase 1: N/A (front-end CSS fix).
- [contracts/README.md](contracts/README.md) — Phase 1: пусто по
  обоснованию (фикс не вводит / не меняет внешних контрактов).
- [quickstart.md](quickstart.md) — Phase 1: V-1..V-5 acceptance.

### Phase 1 Re-check Constitution

| Gate | Проверка | Итог |
|------|----------|------|
| V (двух-фронтенд) | Дифф ограничен `karaoke-public/src/views/ZakromaView.vue`; `webvue3/src/`, `karaoke-web/src/`, `karaoke-app/src/` — нетронуты. | ✅ PASS |
| VI FR-006 (KDoc) | `ZakromaView.vue` уже имеет KDoc на компонент (см. `ZakromaView.vue:159` и `:483`/`setup()`); новый `<div class="km-author-header-sticky">` — это template-обёртка, не публичный API Vue-компонент. | ✅ PASS |
| VI FR-007 (линтеры, baseline) | `tools/check-eslint-baseline.sh karaoke-public` будет прогоняться перед PR — pure CSS change не должен вводить новых warnings (CSS не линтуется ESLint'ом). | ✅ PASS |
| VI FR-009 (per-feature doc sync) | `livedocs/features/012-entity-description-fields.md` (фича 012 — sticky-controls-bar) будет обновлён в этом же PR (по живому содержанию добавлена секция «Bug-fix 252» со ссылкой на этот LiveDoc). | ⏳ TODO в tasks.md |
| VIII (секреты) | Никаких секрет-файлов. | ✅ PASS |

## Phase 1 Artifacts

| Артефакт | Путь | Статус |
|----------|------|--------|
| Spec | `specs/252-fix-author-album-types-hide/spec.md` | ✅ создан /speckit.specify |
| Checklist | `specs/252-fix-author-album-types-hide/checklists/requirements.md` | ✅ создан /speckit.specify |
| Research | `specs/252-fix-author-album-types-hide/research.md` | ✅ Phase 0 |
| Data-model | `specs/252-fix-author-album-types-hide/data-model.md` | ✅ Phase 1 (N/A-обоснование) |
| Contracts | `specs/252-fix-author-album-types-hide/contracts/README.md` | ✅ Phase 1 (пусто по обоснованию) |
| Quickstart | `specs/252-fix-author-album-types-hide/quickstart.md` | ✅ Phase 1 |
| Plan | `specs/252-fix-author-album-types-hide/plan.md` | ✅ это Phase 1 output |
| Tasks | `specs/252-fix-author-album-types-hide/tasks.md` | ⏳ Phase 2 (/speckit.tasks) |

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Никаких нарушений Constitution Check. Все 8 принципов (I-VIII) либо
отмечены N/A, либо PASS.

## Открытые вопросы (для `/speckit.tasks`)

1. Выбор между **FR-002** (`top: calc(...)` для `.km-album-controls-bar`)
   и **FR-004** (общий `<div class="km-author-header-sticky">`). По
   дефолту — FR-004 (см. research.md D2). Если при реализации окажется,
   что FR-004 ломает `flex-wrap`-2-строчный режим или требует переноса
   `border-bottom`/фона — fallback на FR-002.
2. Точная высота `.km-filter-bar` (если пойдём по FR-002). Измерить
   в браузере при `font-size: 1rem` десктопной темы; ожидаемая ≈ 44-50 px
   (1rem × line-height + 0.5rem × 2 padding).
3. Перенос `border-bottom` блока `.km-filter-bar` и `.km-album-controls-bar`
   на обёртку (если FR-004): UX-решение, не блокер. По дефолту — оставить
   `border-bottom` там же.

## Подтверждение готовности к `/speckit.tasks`

- [x] Все NEEDS CLARIFICATION разрешены (research.md U1..U5).
- [x] Все артефакты Phase 0 + Phase 1 созданы.
- [x] Constitution Check PASS (Phase 0 + Phase 1).
- [x] Complexity Tracking пуст (нет нарушений).
- [x] Backend / БД / sync / secrets — не задеты.
- [x] Только `karaoke-public/` затронут (1 файл).

Готово к `/speckit.tasks` для генерации конкретного `tasks.md` со
step-by-step list правок.

# Implementation Plan: Закрома — header-back-link «К списку авторов» + удаление in-page дубля

**Branch**: `254-fix-zakroma-header-back-link` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/254-fix-zakroma-header-back-link/spec.md`

## Summary

UI-фикс в одном файле `karaoke-public/src/views/ZakromaView.vue`:
1. Статический `:back="{ to: '/', label: '← Главная' }"` → динамический через computed `zakromaHeaderBack`, который возвращает `null` (когда нет выбранного автора / спец-режима) или `{ to: '/zakroma', label: '← К списку авторов' }`.
2. Удаление двух in-page `<button class="km-back-btn">`, дублирующих ту же навигацию.

AppHeader API (`back: { type: Object, default: null }` + `v-if="back"` в рендере) уже поддерживает передачу `null` для скрытия back-link — никаких изменений в `AppHeader.vue` не требуется.

## Technical Context

**Language/Version**: Vue 3 SFC (Options API) — `ZakromaView.vue` уже использует Options API.

**Primary Dependencies**:
- Vue 3 + Vue Router 4 (для `RouterLink`).
- `AppHeader.vue` (спека 250 — единый header) — API не меняется.
- Vuex 4 (store module `zakroma`) — не затрагивается.
- Без новых npm-пакетов.

**Storage**: N/A. LocalStorage не используется для этого state (Vue-router query).

**Testing**: ручная визуальная проверка в браузере + DevTools-Console для DOM-инспекции (`document.querySelectorAll('.km-back-btn').length`, `document.location.pathname`). Никаких автотестов в проекте нет.

**Target Platform**:
- Desktop (Chromium / Firefox).
- Mobile viewport (≤ 700 / 500 px) — sticky-поведение AppHeader уже адаптивное; новая back-link работает на любых viewport'ах одинаково.
- Touch-устройства — без особенностей.

**Project Type**: SPA (`karaoke-public`). Конституция Principle V (двух-фронтенд).

**Performance Goals**:
- Никаких изменений runtime-cost: 1 computed + 1 RouterLink. Без новых watchers, без новых reactive dependencies.

**Constraints**:
- **Только** `karaoke-public/src/views/ZakromaView.vue`. AppHeader.vue, AppHeader API, router-config, store — НЕ затрагиваются.
- Никаких новых зависимостей.
- Никаких изменений в backend.
- ESLint baseline (`karaoke-public/.eslint-baseline.json`) не должен расти.

**Scale/Scope**: 1 файл, ~20 строк правок (template -20 / +5, computed +6, scoped CSS -22 для `.km-back-btn`).

## Constitution Check

| # | Принцип | Применим? | Compliance | Обоснование |
|---|---------|-----------|-----------|-------------|
| I | Self-contained автопайплайн | N/A | ✅ | Фикс не касается пайплайна. |
| II | Сырой JDBC | N/A | ✅ | Никаких обращений к БД. |
| III | Двух-БД синхронизация | N/A | ✅ | Нет новых сущностей. |
| IV | Async-очередь задач | N/A | ✅ | Нет process-related кода. |
| V | Двух-фронтенд | ✅ | ✅ PASS | Правки ТОЛЬКО в `karaoke-public/src/views/ZakromaView.vue`. `webvue3`, `karaoke-web`, `karaoke-app` — без изменений. `AppHeader.vue` API не меняется. |
| VI | Code Standards | ✅ | ✅ PASS | FR-006 (KDoc): `ZakromaView.vue` уже имеет KDoc на компонент (`:159, :483`); новые computed-property следуют существующему стилю (см. `albumRenderItems`, `visibleAlbums`). FR-007 (линтеры): pure template change, ESLint baseline не должен расти. FR-009 (per-feature doc): LiveDoc 254 создаётся (T011 Phase 6); LiveDoc 250 (`unify-site-header`) может получить cross-ref на эту фичу, т.к. AppHeader-back теперь имеет явное поведение «null = hidden» (T012). |
| VII | Cross-Machine Setup | N/A | ✅ | Нет правок `.git-blame-ignore-revs`, `.gitattributes`. |
| VIII | Секреты и git-гигиена | N/A | ✅ | Никаких секрет-файлов. |

**Вердикт Phase 0**: PASS.

**Re-check после Phase 1**: PASS (см. ниже).

## Project Structure

### Documentation (this feature)

```text
specs/254-fix-zakroma-header-back-link/
├── plan.md              # Этот файл
├── research.md          # Phase 0 (пропущена — trivial fix, см. ниже)
├── data-model.md        # Phase 1: N/A (pure UI fix)
├── contracts/           # Phase 1: README с обоснованием пустоты
│   └── README.md
├── quickstart.md        # Phase 1: V-1..V-4 + линт/сборка
├── spec.md              # ✅ создан /speckit.specify
├── checklists/
│   └── requirements.md  # ✅ создан /speckit.specify, PASS
└── tasks.md             # Phase 2 output (НЕ создаётся /speckit.plan)
```

**research.md сознательно не создаётся**: фикс настолько мал (один computed + удаление 2 кнопок + удаление CSS), что отдельные «decisions D1..D2» не нужны. Альтернативы рассмотрены в спеке § Assumptions + FR-001..FR-003.

### Source Code (repository root)

```text
karaoke-public/
└── src/
    ├── components/
    │   └── AppHeader.vue           # НЕ ТРОГАЕМ (API уже поддерживает `null`)
    └── views/
        └── ZakromaView.vue         # ⚠ ЕДИНСТВЕННЫЙ ФАЙЛ С ИЗМЕНЕНИЯМИ
```

**Изменения**:
1. Template, ~строка 4: `<AppHeader :back="{ to: '/', label: '← Главная' }" />` → `<AppHeader :back="zakromaHeaderBack" />`.
2. Template, ~строка 99-115: удалить два `<button class="km-back-btn">`.
3. Computed (в секции `computed: {}`, рядом с `zakromaHeaderBack`): добавить `zakromaHeaderBack()` (см. FR-002 в спеке).
4. Scoped CSS, ~строка 959-980: удалить стили `.km-back-btn` (4 правила).

**Не затрагиваемые каталоги**: `karaoke-web/`, `karaoke-app/`, `webvue3/`, `livedocs-en/`, `deploy/`, `tools/`.

**Structure Decision**: single feature branch (`254-…`), one-file diff в `karaoke-public`.

## Phase 1: Design & Contracts (резюме)

Полные output'ы:

- [data-model.md](data-model.md) — Phase 1: N/A (front-end UI fix без новых сущностей).
- [contracts/README.md](contracts/README.md) — Phase 1: пусто по обоснованию (фикс не вводит / не меняет внешних контрактов; AppHeader API уже поддерживает передачу `null`).
- [quickstart.md](quickstart.md) — Phase 1: V-1..V-4 acceptance (DOM-проверки, URL-проверки).

### Phase 1 Re-check Constitution

| Gate | Проверка | Итог |
|------|----------|------|
| V | Diff ограничен `karaoke-public/src/views/ZakromaView.vue`. | ✅ PASS |
| VI FR-006 | Новый `computed` — типичный Vue-2/3 Options API; стиль соответствует существующим `albumRenderItems()`, `visibleAlbums()`. | ✅ PASS |
| VI FR-007 | Pure template + computed + scoped CSS; ESLint baseline не должен расти. | ✅ PASS |
| VI FR-009 | LiveDoc 254 + опционально cross-ref в LiveDoc 250 (`unify-site-header`) — T011..T012 Polish. | ⏳ TODO в tasks.md |

## Phase 1 Artifacts

| Артефакт | Путь | Статус |
|----------|------|--------|
| Spec | `specs/254-fix-zakroma-header-back-link/spec.md` | ✅ /speckit.specify |
| Checklist | `specs/254-fix-zakroma-header-back-link/checklists/requirements.md` | ✅ PASS |
| Data-model | `specs/254-fix-zakroma-header-back-link/data-model.md` | ✅ N/A |
| Contracts | `specs/254-fix-zakroma-header-back-link/contracts/README.md` | ✅ пусто по обоснованию |
| Quickstart | `specs/254-fix-zakroma-header-back-link/quickstart.md` | ✅ Phase 1 |
| Plan | `specs/254-fix-zakroma-header-back-link/plan.md` | ✅ Phase 1 |
| Tasks | `specs/254-fix-zakroma-header-back-link/tasks.md` | ⏳ Phase 2 |

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Никаких нарушений Constitution Check.

## Открытые вопросы

Нет. Фикс очевиден:
- AppHeader API поддерживает `null` для скрытия back-link.
- `ZakromaView.vue` имеет `authorChosen` и `specialBucketShown` уже как `data`.
- `backToAuthors()` метод переиспользуется автоматически через Vue-router.
- CSS `.km-back-btn` удаляется за отсутствием использования.

## Подтверждение готовности к `/speckit.tasks`

- [x] Все `[NEEDS CLARIFICATION]` разрешены.
- [x] Все артефакты Phase 0 + Phase 1 созданы.
- [x] Constitution Check PASS (Phase 0 + Phase 1).
- [x] Complexity Tracking пуст.
- [x] Backend / БД / sync / secrets — не задеты.
- [x] Только `karaoke-public/src/views/ZakromaView.vue` затронут (1 файл).

Готово к `/speckit.tasks`.

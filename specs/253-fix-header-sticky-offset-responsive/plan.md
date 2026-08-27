# Implementation Plan: Закрома — sticky-блок приклеивается к AppHeader с учётом её реальной высоты на узких экранах

**Branch**: `253-fix-header-sticky-offset-responsive` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/253-fix-header-sticky-offset-responsive/spec.md`

## Summary

Bug-fix: на узких экранах (≤ 700 px / ≤ 500 px) между AppHeader (высота которой
адаптивно уменьшается до 49 px / 46 px) и `.km-author-header-sticky`-обёрткой
(введённой в спек 252 с захардкоженным `top: 53px`) появляется видимый промежуток
4 px / 7 px при скролле.

**Технический подход** (см. [research.md](research.md) § D3): глобальная
CSS-переменная `--km-header-height` на `:root` в `karaoke-public/src/style.css`
с media queries (53 / 49 / 46 px), плюс `top: var(--km-header-height, 53px)`
в `.km-author-header-sticky` (`ZakromaView.vue` scoped CSS).

Pure CSS fix: 2 файла, ~10 строк правок. Без backend. Без изменения
контрактов соседних спек (252 / 250 / 012).

## Technical Context

**Language/Version**: Vue 3 SFC (Options API) + scoped CSS + глобальный
`style.css` (vanilla CSS, postcss или vite-CSS).

**Primary Dependencies**:
- Vue 3 + Vuex 4 (как в спек 252, без изменений).
- Vite 7.3.6 (сборка `karaoke-public`).
- Без новых npm-пакетов.

**Storage**: N/A. БД, Vuex, localStorage не затрагиваются.

**Testing**: ручная визуальная проверка в Chrome DevTools на разных viewport'ах (см. quickstart.md V-1). DevTools `getBoundingClientRect()` для проверки gap'а между `header.bottom` и `wrapper.top`.

**Target Platform**:
- Desktop (Chromium / Firefox, viewport 1280×800).
- Mobile viewport emulation: 700 px breakpoint (logo 32 px), 500 px breakpoint (logo 28 px), 375 px (iPhone SE).
- Touch-устройства не требуют особых проверок (CSS media queries работают нативно).

**Project Type**: SPA (`karaoke-public`). Двух-фронтенд архитектура (Принцип V).

**Performance Goals**:
- Никаких новых runtime-cost (CSS-only fix).
- Browser reflow на resize — минимальный (одна CSS-переменная на `:root`, изменяется только при breakpoint).

**Constraints**:
- **Только** `karaoke-public`. AppHeader.vue НЕ модифицируется (scoped не достигает других view).
- Никаких новых зависимостей.
- Никаких изменений в backend (`karaoke-app`, `karaoke-web`), БД, Docker-образах.
- ESLint baseline (`karaoke-public/.eslint-baseline.json`) не должен расти.

**Scale/Scope**:
- Scope = **2 файла**: `karaoke-public/src/style.css` (+6 строк на `--km-header-height`) и `karaoke-public/src/views/ZakromaView.vue` (1 строка).
- Diff < 10 строк.
- Не влияет на другие страницы / фичи (AppHeader.vue остаётся источником истины для своей высоты).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Применим? | Compliance | Обоснование |
|---|---------|-----------|-----------|-------------|
| I | Self-contained автопайплайн | N/A | ✅ | Фикс не касается пайплайна. |
| II | Сырой JDBC + diff по хэшам | N/A | ✅ | Никаких обращений к БД. |
| III | Двух-БД синхронизация | N/A | ✅ | Нет новых сущностей. |
| IV | Async-очередь задач | N/A | ✅ | Нет process-related кода. |
| V | Двух-фронтенд | ✅ | ✅ PASS | Правки ТОЛЬКО в `karaoke-public/src/style.css` + `Zakroma-public/src/views/ZakromaView.vue`. `webvue3` не затрагивается. AppHeader.vue тоже не правится (scoped CSS не достигнет ZakromaView —sync через `:root`-переменную). |
| VI | Code Standards | ✅ | ✅ PASS | FR-006 (KDoc): изменения в `--km-header-height` — это CSS-переменная, не Vue-компонент; KDoc не требуется. Изменения в `ZakromaView.vue` — одна строка в существующем CSS-блоке с комментарием (T006). FR-007: ESLint baseline не должен расти — pure CSS change не должен вводить новых warnings. FR-009: при правке CSS-фрагмента, входящего в контракт спек 252 (`.km-author-header-sticky` top), MUST обновить `livedocs/features/252-fix-author-album-types-hide.md` или добавить новую LiveDoc для спек 253 — T022 в Polish-фазе. |
| VII | Cross-Machine Setup | N/A | ✅ | Не редактируем `.git-blame-ignore-revs`, `.gitattributes`. |
| VIII | Секреты и git-гигиена | N/A | ✅ | Никаких секрет-файлов. |

**Вердикт Phase 0**: PASS.

**Re-check после Phase 1**: PASS (см. ниже).

## Project Structure

### Documentation (this feature)

```text
specs/253-fix-header-sticky-offset-responsive/
├── plan.md              # Этот файл
├── research.md          # Phase 0: decisions D1..D5
├── data-model.md        # Phase 1: N/A (чисто CSS-фикс)
├── contracts/           # Phase 1: README с обоснованием пустоты
│   └── README.md
├── quickstart.md        # Phase 1: V-1..V-3 + линт/сборка
├── spec.md              # ✅ создан /speckit.specify
├── checklists/
│   └── requirements.md  # ✅ создан /speckit.specify, PASS
└── tasks.md             # Phase 2 output (НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-public/
├── src/
│   ├── style.css                        # ⚠ +6 строк (--km-header-height + media queries)
│   └── views/
│       └── ZakromaView.vue              # ⚠ 1 строка (top: var(--km-header-height, 53px))
└── package.json                         # НЕ ТРОГАЕМ
```

**Не затрагиваемые файлы**:
- `karaoke-public/src/components/AppHeader.vue` — НЕ модифицируется (research.md § D3-D4). Синхронизация через явный комментарий в `style.css`.
- `karaoke-web/`, `karaoke-app/`, `webvue3/` — без изменений.
- `livedocs/features/252-fix-author-album-types-hide.md` — обновляется (T022 Polish-фазы: добавить bug-fix-отсылку).

**Structure Decision**: single feature branch (`253-…`), 2 файла. Совместимо с Pass 252 (тот же компонент).

## Phase 1: Design & Contracts (резюме)

Полные output'ы:

- [research.md](research.md) — Phase 0: decisions D1..D5.
- [data-model.md](data-model.md) — Phase 1: N/A (front-end CSS fix).
- [contracts/README.md](contracts/README.md) — Phase 1: пусто по обоснованию (фикс не вводит / не меняет внешних контрактов; `:root`-переменная — observable, но не API).
- [quickstart.md](quickstart.md) — Phase 1: V-1..V-3 acceptance + DevTools-команды для проверки gap'а.

### Phase 1 Re-check Constitution

| Gate | Проверка | Итог |
|------|----------|------|
| V | Diff ограничен `karaoke-public/src/style.css` + `ZakromaView.vue`. `webvue3`, `karaoke-web/`, `karaoke-app/` — нетронуты. | ✅ PASS |
| VI FR-006 | `--km-header-height` — CSS-переменная, не Vue-компонент, KDoc не требуется. | ✅ PASS |
| VI FR-007 | Pure CSS change; `tools/check-eslint-baseline.sh karaoke-public` не должен расти. | ✅ PASS |
| VI FR-009 | LiveDoc 252 обновляется (FR-009) + новая LiveDoc 253 создаётся (T021). | ⏳ TODO в tasks.md |

## Phase 1 Artifacts

| Артефакт | Путь | Статус |
|----------|------|--------|
| Spec | `specs/253-fix-header-sticky-offset-responsive/spec.md` | ✅ создан /speckit.specify |
| Checklist | `specs/253-fix-header-sticky-offset-responsive/checklists/requirements.md` | ✅ PASS |
| Research | `specs/253-fix-header-sticky-offset-responsive/research.md` | ✅ Phase 0 |
| Data-model | `specs/253-fix-header-sticky-offset-responsive/data-model.md` | ✅ N/A-обоснование |
| Contracts | `specs/253-fix-header-sticky-offset-responsive/contracts/README.md` | ✅ пусто по обоснованию |
| Quickstart | `specs/253-fix-header-sticky-offset-responsive/quickstart.md` | ✅ Phase 1 |
| Plan | `specs/253-fix-header-sticky-offset-responsive/plan.md` | ✅ это Phase 1 output |

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Никаких нарушений Constitution Check.

## Открытые вопросы

1. **FR-006 sync-комментарий**: добавить ли аналогичный комментарий в AppHeader.vue (`Если меняешь высоту — обнови --km-header-height`)? — решено: НЕ добавляем в AppHeader.vue (out of scope — CSS scoped не достигает). Комментарий ставится только в `style.css`.
2. **FR-007** (AppHeader.vue НЕ правится): подтверждено в research.md § D3 (scoped CSS — лишние грабли).
3. **Перенос шапки на 2 строки** (assumption (b)): явно out of scope; требует JS ResizeObserver — отдельная фича.

## Подтверждение готовности к `/speckit.tasks`

- [x] Все NEEDS CLARIFICATION разрешены (research.md U1..U5).
- [x] Все артефакты Phase 0 + Phase 1 созданы.
- [x] Constitution Check PASS (Phase 0 + Phase 1).
- [x] Complexity Tracking пуст.
- [x] Backend / БД / sync / secrets — не задеты.
- [x] Только `karaoke-public/` затронут (2 файла).

Готово к `/speckit.tasks`.

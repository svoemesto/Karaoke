---
status: Active
slug: 253-fix-header-sticky-offset-responsive
related:
  - ../architecture/L3-components.md
  - ../../specs/253-fix-header-sticky-offset-responsive/spec.md
  - ../../specs/253-fix-header-sticky-offset-responsive/plan.md
  - ../../specs/253-fix-header-sticky-offset-responsive/research.md
  - 252-fix-author-album-types-hide
  - 250-unify-site-header
  - 012-entity-description-fields
---

# 253 — Sticky-блок Закромов приклеивается к AppHeader на узких экранах (LiveDoc)

> Drill-down — [specs/253-fix-header-sticky-offset-responsive/spec.md](../../specs/253-fix-header-sticky-offset-responsive/spec.md),
> [plan.md](../../specs/253-fix-header-sticky-offset-responsive/plan.md),
> [research.md](../../specs/253-fix-header-sticky-offset-responsive/research.md).

## Что делает

Чинит responsive-зазор между AppHeader и `.km-author-header-sticky`-обёрткой
на узких экранах. На десктопе (> 700 px) шапка высотой 53 px совпадала с
захардкоженным `top: 53px` обёртки (введённым в спек 252) — зазора не было.
На узких экранах (≤ 700 px / ≤ 500 px) AppHeader становится короче (49 px / 46 px),
а sticky-wrapper всё ещё «зацеплен» за `53 px` → появляется видимый промежуток
**4 px / 7 px** между шапкой и обёрткой при скролле.

## Решение

Глобальная CSS-переменная `--km-header-height` на `:root` в `karaoke-public/src/style.css`
с media queries (53 / 49 / 46 px), плюс `top: var(--km-header-height, 53px)` в
`.km-author-header-sticky` (`ZakromaView.vue` scoped CSS).

**Эффект**: sticky-wrapper автоматически отслеживает реальную высоту шапки
на всех breakpoints. Зазор `< 1 px` на всех viewport'ах от 1280×800 до 375×667.

`AppHeader.vue` **не модифицируется** — синхронизация через `:root`-переменную
в `style.css` + явный комментарий `/* Синхронизировано с AppHeader.vue ... */`.

## User Stories (краткий список)

- **US1** (P1, MVP): sticky-wrapper приклеен к AppHeader на любой ширине
  экрана с зазором ≤ 1 px.
- **US2** (P2): CSS-переменная `--km-header-height` доступна из любых view —
  единый источник истины для высоты AppHeader.
- **US3** (P3): регрессия спек 252 не происходит (overlap удалён,
  2-строчный `flex-wrap` блока типов альбомов не отрезается).

## Functional Requirements (указатель)

- **FR-001..FR-003**: responsive sticky-offset (53 / 49 / 46 px через media queries).
- **FR-004..FR-005**: глобальная CSS-переменная `:root --km-header-height` + fallback 53 px.
- **FR-006..FR-007**: синхронизация значений с AppHeader.vue; AppHeader.vue НЕ правится.
- **FR-008..FR-010**: регрессии (backend не задет, спек 252 поведение сохранено).

Полный список — [spec.md](../../specs/253-fix-header-sticky-offset-responsive/spec.md#requirements-mandatory).

## Acceptance Criteria

- [ ] **AC1 (US1)**: на viewport 1280×800 (`scrollY > 0`) `stickyWrapper.top - headerBottom` ∈ `[-1, 1] px`.
- [ ] **AC2 (US1)**: на viewport 700×800 — то же, gap ≤ 1 px.
- [ ] **AC3 (US1)**: на viewport 500×800 — то же, gap ≤ 1 px.
- [ ] **AC4 (US1)**: на viewport 375×667 — то же + 2-строчный `flex-wrap` блока типов альбомов не отрезается.
- [ ] **AC5 (US1)**: при resize 1280 → 400 px на каждом breakpoint gap ≤ 1 px.
- [ ] **AC6 (US2)**: `getComputedStyle(document.documentElement).getPropertyValue('--km-header-height')`
      возвращает `53 / 49 / 46 px` на соответствующих breakpoint'ах.
- [ ] **AC7 (US2)**: fallback `53px` срабатывает при отсутствии переменной
      (поведение спек 252 не ломается).
- [ ] **AC8 (US3)**: спек 252 поведение сохранено (overlap не появляется,
      2-строчный `flex-wrap` блока типов работает).
- [ ] **AC9**: `cd karaoke-public && npm run build` PASS.
- [ ] **AC10**: `npm run lint` (karaoke-public) — 0 warnings.
- [ ] **AC11**: `tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- [ ] **AC12**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` —
      `:karaoke-web:bootJar UP-TO-DATE` (backend не задет).

## Связанные LiveDocs

- [252-fix-author-album-types-hide](252-fix-author-album-types-hide.md) —
  ввела `.km-author-header-sticky` с хардкоженным `top: 53px`. **Без изменений** —
  LiveDoc 253 только правит одно CSS-значение (`top: var(...)` вместо `top: 53px`).
- [250-unify-site-header](../features/250-unify-site-header.md) — единый AppHeader,
  высоту которого LiveDoc 253 отслеживает. **Без изменений** (AppHeader.vue
  не правится — синхронизация через `:root`-переменную в `style.css`).
- [012-entity-description-fields](012-entity-description-fields.md) —
  ввела быстрые фильтры по типу альбома (sticky-controls-bar). Без изменений.
- Architecture: [L3-components.md](../architecture/L3-components.md).

## Код

- `karaoke-public/src/style.css` — добавить `:root { --km-header-height: 53px; }` +
  `@media (max-width: 700px) { :root { --km-header-height: 49px; } }` +
  `@media (max-width: 500px) { :root { --km-header-height: 46px; } }` +
  блок комментариев синхронизации.
- `karaoke-public/src/views/ZakromaView.vue` — заменить `top: 53px` на
  `top: var(--km-header-height, 53px)` в `.km-author-header-sticky`.

Бэкенд (`karaoke-app`, `karaoke-web`) и DTO **не затронуты**. Зависимости
(`package.json`, `vite.config.js`, ESLint baseline) **не затронуты**. CSS bundle
вырос на ≈ 170 байт (новые media queries + `var()`).

## Будущая работа (out of scope)

- Если AppHeader.vue сильно перерастёт (например, ≥ 2 строки на ≤ 700 px viewport
  при переполнении виджетами), `--km-header-height = 49px` (или 46 px) станет
  недостаточным → большой зазор. Решение требует JS `ResizeObserver` или
  CSS `@container` queries — отдельная фича. См. spec.md § Edge Cases /
  assumptions (b).
- Поиск и применение `:root --km-header-height` в **других** view (SearchView,
  AccountView, AuthorPlaylistView) — отдельная фича при наличии у них
  собственных sticky-панелей.

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27

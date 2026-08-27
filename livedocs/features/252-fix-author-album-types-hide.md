---
status: Active
slug: 252-fix-author-album-types-hide
related:
  - ../architecture/L3-components.md
  - ../../specs/252-fix-author-album-types-hide/spec.md
  - ../../specs/252-fix-author-album-types-hide/plan.md
  - ../../specs/252-fix-author-album-types-hide/research.md
  - 012-entity-description-fields
  - 181-zakroma-author-load-progress
  - 250-unify-site-header
---

# 252 — Закрома: корректное скрытие блока типов альбомов при скролле (LiveDoc)

> Drill-down — [specs/252-fix-author-album-types-hide/spec.md](../../specs/252-fix-author-album-types-hide/spec.md),
> [plan.md](../../specs/252-fix-author-album-types-hide/plan.md),
> [research.md](../../specs/252-fix-author-album-types-hide/research.md).

## Что делает

Чинит баг: при скролле вниз по списку песен конкретного автора блок с типами альбомов
(«Студийные (19)», «Синглы (1)», «Концертные (11)», «Сборники (52)», «Бутлеги (1)»,
«Архивные (73)») **не полностью скрывался** — нижняя его часть «выглядывала» из-под
поля быстрого фильтра по названию песни.

## Корень бага (research.md § D1)

В `karaoke-public/src/views/ZakromaView.vue` оба блока (`.km-filter-bar` и
`.km-album-controls-bar`) использовали **идентичный** `position: sticky; top: 53px`,
с `z-index` 90 vs 89 соответственно. DOM-порядок: фильтр первый ⇒ фильтр
рисовался поверх блока альбомов. Поскольку блок альбомов выше фильтра (там
тумблер + кнопки типов), его нижняя часть не была перекрыта полностью — хвост
оставался виден. Усугублялось на мобильном (2-строчный `flex-wrap` блока типов
альбомов на 375×667) и при 6 типах.

## Решение (FR-004)

Обёртка `<div class="km-author-header-sticky">` с `v-if="authorChosen"` оборачивает
оба блока. Sticky (`position: sticky; top: 53px; z-index: 90`) теперь висит
**на обёртке**, а не на её детях. Внутренние блоки — обычные flex-children,
каждый отвечает только за собственные визуальные свойства (`background`,
`border-bottom`, `padding`).

**Эффект**: обёртка либо уезжает за верх viewport одним куском, либо прилипает
к AppHeader одним блоком. Пересечения между фильтром и блоком типов
невозможны по построению.

## User Stories (краткий список)

- **US1** (P1): корректное скрытие блока типов альбомов при скролле.
- **US2** (P2): sticky-поведение не зависит от числа типов альбомов (1 / 6 типов,
  2-строчный `flex-wrap` на мобильном).
- **US3** (P3): стрим и ошибки загрузки остаются консистентными (sticky-стек
  `AppHeader → km-author-header-sticky → km-stream-progress` не регрессирует).

## Functional Requirements (указатель)

- **FR-001..FR-004**: CSS-layout sticky-блоков (FR-004 — общий sticky-контейнер,
  FR-001..FR-003 — fallback FR-002 «смещение top», см. spec.md § A).
- **FR-005..FR-006**: регрессии на смежных sticky-элементах (AppHeader z=100,
  StreamProgress z=50 — без изменений).
- **FR-007..FR-008**: мобильный + resize.

Полный список — [spec.md](../../specs/252-fix-author-album-types-hide/spec.md#requirements-mandatory).

## Acceptance Criteria

- [ ] **AC1**: При `scrollY > 0` на десктопе 1280×800 (автор с ≥3 типами альбомов)
      `.km-author-header-sticky` либо уехал за верх viewport, либо прилип к шапке
      единой полосой. Хвоста блока типов поверх фильтра НЕТ.
      Проверка: `getBoundingClientRect()` обёртки и обоих внутренних блоков
      не пересекаются по вертикали (или обёртка целиком скрыта, `bottom < 0`).
- [ ] **AC2**: При scrollY = 0 оба блока видны полностью в нормальных in-flow позициях.
- [ ] **AC3**: На mobile 375×667 у автора с 6 типами (`Машина Времени`) блок типов
      2-строчный из-за `flex-wrap`. Sticky-поведение согласовано с десктопом:
      либо обе строки видны обе, либо полоса уехала целиком (никаких обрезанных
      строк).
- [ ] **AC4**: При активном стриме (`.km-stream-progress` виден) sticky-стек
      `AppHeader → km-author-header-sticky → km-stream-progress` остаётся
      визуально согласованным: хвоста блока типов поверх прогресс-бара НЕТ.
- [ ] **AC5**: `cd karaoke-public && npm run build` PASS.
- [ ] **AC6**: `npm run lint` (karaoke-public) — 0 warnings.
- [ ] **AC7**: `tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- [ ] **AC8**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` —
      `:karaoke-web:bootJar UP-TO-DATE`, бэкенд не задет.

## Связанные LiveDocs

- [012-entity-description-fields](012-entity-description-fields.md) — вводит
  sticky-controls-bar и быстрые фильтры по типу альбома. **Без изменений** —
  LiveDoc 252 только правит CSS-разметку (overlap фикс), не меняет контракт
  FR-025/027.
- [181-zakroma-author-load-progress](../features/181-zakroma-author-load-progress.md) —
  стрим песен с прогресс-баром `.km-stream-progress` (z-index 50). Без изменений.
- [250-unify-site-header](../features/250-unify-site-header.md) — единый
  `AppHeader` (z-index 100, высота ~53 px). Без изменений.
- [251-fix-zakroma-progressbar](../features/251-fix-zakroma-progressbar.md) —
  соседняя фича того же `ZakromaView.vue`: корректное визуальное заполнение
  `.km-stream-bar`. Без изменений.
- [253-fix-header-sticky-offset-responsive](253-fix-header-sticky-offset-responsive.md) —
  follow-up bug-fix на этом же файле: устраняет responsive-зазор между
  AppHeader и обёрткой на узких экранах (≤ 700 / 500 px). Контракт спек 252
  (sticky обёртка) сохранён; LiveDoc 253 вводит только CSS-переменную
  `--km-header-height` (на `:root` в `style.css`) и заменяет `top: 53px` на
  `top: var(--km-header-height, 53px)` в `.km-author-header-sticky`.
- Architecture: [L3-components.md](../architecture/L3-components.md) —
  структура Vue-компонентов `karaoke-public`, в т.ч. `ZakromaView.vue`.

## Код

- `karaoke-public/src/views/ZakromaView.vue` — **template** (T007):
  - Обернуть `<div class="km-filter-bar">…</div>` И `<div class="km-album-controls-bar">…</div>`
    в общий `<div v-if="authorChosen" class="km-author-header-sticky">…</div>`
    (строки 6-75 — внешний блок; внутренние условия сохранены).
  - `v-if` фильтра упрощён до безусловного (фильтр показан когда `authorChosen`
    через обёртку); `v-if` блока типов сохранён `zakromaAlbumTypeCounts.length > 0`.
- `karaoke-public/src/views/ZakromaView.vue` — **scoped CSS** (T008):
  - `.km-author-header-sticky { position: sticky; top: 53px; z-index: 90; background: var(--km-header); }`
    — новое правило.
  - `.km-filter-bar`, `.km-album-controls-bar` — убраны `position: sticky`,
    `top`, `z-index`; оставлены только визуальные свойства (background,
    border-bottom, padding).

Бэкенд (`karaoke-app`, `karaoke-web`) и DTO **не затронуты**. Зависимости
(`package.json`, `vite.config.js`, ESLint baseline) **не затронуты**.

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27

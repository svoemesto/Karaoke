# Research: 253 — sticky-блок приклеивается к AppHeader с учётом её высоты

**Branch**: `253-fix-header-sticky-offset-responsive` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

## Неизвестные из Technical Context

| # | Unknown | Где появилось | Статус после Research |
|---|---------|---------------|------------------------|
| U1 | Какая реальная высота AppHeader на каждом breakpoint (default / 700 / 500)? | AppHeader.vue `:136, 181, 211-242`; задано padding + логотип + 1 px border | RESOLVED — см. D1 |
| U2 | Почему текущее `top: 53px` в `.km-author-header-sticky` даёт gap на узких экранах? | math: `wrapper.top = 53`, `header.bottom = 49` (на 700 px) → gap = 4 px | RESOLVED — см. D2 |
| U3 | Какая стратегия — (a) media queries прямо в `.km-author-header-sticky`, или (b) CSS-переменная `--km-header-height` на `:root` + `var()`? | spec.md FR-001..FR-005 | RESOLVED — см. D3 |
| U4 | Где объявить переменную (`:root` в `style.css`, или на `.km-page`, или scoped в AppHeader)? | Нужно глобально, чтобы все view могли использовать | RESOLVED — см. D4 |
| U5 | Что делать с JS ResizeObserver (для 2-строчной шапки — assumption (b))? | Out of scope этой спеки | RESOLVED — см. D5 |

## Решения

### D1 — Высоты AppHeader на каждом breakpoint

- **Decision**:
  - **> 700 px (default):** `padding 0.5rem × 2 + логотип 36 px + border 1 px` = **53 px**.
  - **≤ 700 px:** `padding 0.5rem × 2 + логотип 32 px + border 1 px` = **49 px**.
    (`AppHeader.vue:213`: `padding: 0.5rem 0.75rem;` — но padding по вертикали остаётся 0.5rem, уменьшается только горизонтальный).
  - **≤ 500 px:** `padding 0.4rem × 2 + логотип 28 px + border 1 px` = **46 px**.
    (`AppHeader.vue:234`: `padding: 0.4rem 0.5rem;` — оба padding'а уменьшены).

- **Rationale**: проверено построчно в `AppHeader.vue:133-247`. Логотип — единственный content element (`AppHeader.vue:181, 229, 240`). Padding — на `.km-header` (`:136, 213, 234`). Border-bottom — `1px solid var(--km-border)` (`:135`).
- **Alternatives considered**:
  - **(α) Замерить реальную высоту через `getBoundingClientRect()` в JS.** Точно, но добавляет JS-логику и ломает принцип «CSS-only fix». Отвергнуто.
  - **(β) Использовать `top: 0` на обёртке и накладывать через `padding-top` на `.km-content`.** Альтернативный layout, требует большого рефакторинга ZakromaView. Отвергнуто как overkill для фикса.

### D2 — Причина текущего gap'а

- **Decision**: спек 252 ввела `.km-author-header-sticky { top: 53px; z-index: 90; }` (см. `ZakromaView.vue:741-752` после правки в Pass 252). Координата `top: 53px` корректна для default высоты шапки (53 px). На узких экранах шапка короче, а sticky-wrapper по-прежнему «зацеплен» за `53` — образуется gap (4 px / 7 px) между `header.bottom` (49 / 46) и `wrapper.top` (53).
- **Rationale**: статический CSS-`top` vs динамическая высота header — конфликт. Без responsive-tracker'а `top` остаётся 53 на всех breakpoints.
- **Alternatives considered**:
  - **Убрать sticky-обёртку на узких экранах.** Сломал бы UX контракт спек 252 (FR-025/027 — sticky сохраняется). Отвергнуто.
  - **Сделать `.km-author-header-sticky` НЕ sticky, а in-flow.** То же самое. Отвергнуто.

### D3 — Стратегия фикса: media queries vs CSS-переменная

- **Decision**: **гибрид**:
  - **(a)** На `:root` в `karaoke-public/src/style.css` объявляем CSS-переменную `--km-header-height` с media queries, зеркально повторяющими breakpoints AppHeader.
  - **(b)** В `ZakromaView.vue` (scoped CSS) `.km-author-header-sticky` использует `top: var(--km-header-height, 53px)` (с fallback).

- **Rationale**:
  - **Переменная в `:root`** даёт единый источник истины для всей SPA (SearchView, AccountView, AuthorPlaylistView и т.п. в будущем могут использовать ту же переменную для своих sticky-панелей).
  - **`var(--km-header-height, 53px)` с fallback'ом** устойчиво: если по какой-то причине переменная не определена (например, при импорте компонента вне `style.css`), sticky-wrapper работает как раньше (53 px).
  - **Media queries задаются ОДИН раз** в `:root` — на любом breakpoint все элементы автоматически подхватывают правильное значение.
  - Альтернативный подход (только media queries в `.km-author-header-sticky`) тоже работает, но **только для одного правила**. Если в будущем ещё какая-то CSS-правило должно знать высоту AppHeader (padding-top для `.km-content`?), придётся снова копировать media queries.
- **Alternatives considered**:
  - **(α) Только media queries в `.km-author-header-sticky`.** Просто, но изолировано — переменная не переиспользуется. **Принято как резерв**, если переменная не сработает (например, в SSR-режиме).
  - **(β) JS-обёртка (`<HeaderSpacer>` Vue-компонент с CSS-переменной).** Сложно. Отвергнуто как overkill.
  - **(γ) Изменить AppHeader.vue scoped CSS, чтобы он сам выставлял `--km-header-height` на `.km-header`.** Scoped CSS в AppHeader не достигнет ZakromaView (Vue scoped). Пришлось бы использовать `:deep()` или unscoped — лишние грабли. Отвергнуто.

### D4 — Где объявить `--km-header-height`

- **Decision**: на `:root` в `karaoke-public/src/style.css` (глобально). Файл импортируется в `main.js` (см. `main.js:1-10`, если есть — проверить), определяется первым среди стилей, доступен всем view.
- **Rationale**:
  - `style.css` уже содержит существующие theme-переменные (`--km-bg`, `--km-text` и т.д.). Это **каноническое место** для глобальных CSS-переменных в проекте.
  - `:root` гарантирует максимальную специфичность (выше, чем любая локальная) → переменная доступна во всех дочерних элементах.
  - **Not** на `.km-page` или `.km-header` — тогда пришлось бы объявлять в каждом view, и AppHeader бы не покрывал сам себя.
- **Alternatives considered**:
  - **(α) Отдельный файл `header.css` с одним `:root` block.** Over-инженеринг. Отвергнуто.
  - **(β) Объявить в AppHeader.vue scoped + cross-vue-imperative через provide/inject.** Сложно. Отвергнуто.

### D5 — JS ResizeObserver (assumption (b)) — out of scope

- **Decision**: NOT в этой спеке. Assumptions (b) фиксирует, что при переносе шапки на 2 строки `top: 49px` → gap 49 px (большой). Решение требует JS ResizeObserver или `ResizeObserver` API, что выходит за scope «CSS-only fix» и требует изменения AppHeader.vue для измерения своей высоты. **Это отдельная задача**, может быть оформлена как LiveDoc follow-up при обнаружении.
- **Rationale**: scope-минимализм. Текущий баг (gap на узких экранах) решается **только** CSS. Перенос на 2 строки — крайне edge-case (только при viewport ≤ 700 px + переполненная шапка + все 3 виджета видны), не блокирует основную функциональность.
- **Alternatives considered**:
  - **Реализовать ResizeObserver в этой спеке.** Сделает спеку больше и нарушит «CSS-only fix». Отвергнуто — отдельная фича.

## Зависимости и границы

- **Frontend** (`karaoke-public/`): 2 файла с правками:
  - `src/style.css` — добавить `:root { --km-header-height: 53px; }` + media queries (≤ 700 px → 49 px, ≤ 500 px → 46 px).
  - `src/views/ZakromaView.vue` (scoped CSS) — заменить `top: 53px` на `top: var(--km-header-height, 53px)` в `.km-author-header-sticky` (одна строка).
- **Не затрагивается**:
  - **AppHeader.vue** — НЕ модифицируется (CSS scoped не достигнет ZakromaView; sync-комментарий добавляется в `style.css`).
  - Backend (`karaoke-app`, `karaoke-web`), БД (PostgreSQL), MinIO.
  - Vuex store, composables, роутер.
  - Другие view (`SearchView`, `AccountView`, `AuthorPlaylistView`, `SongView`).
- **Сборка**: только `npm run lint` + `npm run build` для `karaoke-public`. Бэкенд `karaoke-app`/`karaoke-web` — UP-TO-DATE.

## Синхронизация AppHeader.vue ↔ style.css

В этой спеке AppHeader.vue **не трогается**, но добавляется **комментарий в style.css** с обратной ссылкой:

```css
:root {
  /* Синхронизировано с AppHeader.vue :logo-height + :padding + :border-bottom.
     При изменении высоты AppHeader обязательно обновить эти значения +
     проверить спеку 253-fix-header-sticky-offset-responsive. */
  --km-header-height: 53px;
}
@media (max-width: 700px) { --km-header-height: 49px; }
@media (max-width: 500px) { --km-header-height: 46px; }
```

Аналогичный комментарий должен быть добавлен в AppHeader.vue (но это out of scope — см. Future Work).

## Open Questions для plan/tasks

- **Минор**: добавить ли аналогичный комментарий в AppHeader.vue (`Если меняешь высоту — обнови --km-header-height в style.css`)? — решено: в AppHeader.vue НЕ добавляем (out of scope), но в `style.css` ставим явный комментарий.
- **Без open questions, блокирующих план**.

## Acceptance для research-фазы

- ✅ Все `NEEDS CLARIFICATION` разрешены (U1..U5).
- ✅ Выбрана стратегия фикса (CSS-переменная `:root --km-header-height` + `var()` в ZakromaView).
- ✅ Подтверждено, что backend не затрагивается (Constitution Principle II, V).
- ✅ AppHeader.vue не модифицируется в этой спеке (только синхронизирующий комментарий в `style.css`).

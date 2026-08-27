# Feature Specification: Закрома — sticky-блок приклеивается к AppHeader на узких экранах с учётом реальной высоты шапки

**Feature Branch**: `253-fix-header-sticky-offset-responsive`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: «при узком экране (когда шапка имеет меньшую чем обычно высоту) между шапкой и блоком "Быстрый фильтр по названию" есть промежуток. Нужно чтобы блок приклеивался к шапке с учётом её высоты».

## User Scenarios & Testing *(mandatory)*

### User Story 1 — sticky-блок приклеен к нижней границе шапки на любой ширине экрана (Priority: P1)

Посетитель открывает страницу `/zakroma?author=Машина Времени` на узком viewport'е (≤ 700 px, например iPhone 12 mini / Pixel 6 шириной 412 px, или просто узкое окно десктопа 600 px). Шапка AppHeader визуально короче (logo 32 px вместо 36 px, padding уменьшен до `0.5rem 0.75rem` → высота ≈ 49 px), sticky-блок «Быстрый фильтр по названию…» + блок типов альбомов при прокрутке должен прижиматься **к нижней границе шапки** без видимого промежутка между ними. На ещё более узком viewport'е (≤ 500 px, logo 28 px, padding `0.4rem 0.5rem` → высота ≈ 46 px) — то же поведение: ноль видимого зазора.

**Why this priority**: Pass 252 (спека `252-fix-author-album-types-hide`) ввёл sticky-обёртку `.km-author-header-sticky { top: 53px; z-index: 90; }` с **захардкоженным** `top`. На desktop это корректно совпадает с высотой AppHeader (16 + 36 + 1 = 53 px), но на узких экранах AppHeader становится короче (49 px / 46 px), а sticky-wrapper по-прежнему прижимается к координате 53 px. В результате при `scrollY > 0` между нижней границей шапки (49 px) и верхней границей sticky-wrapper (53 px) появляется **зазор 4 px** (на ≤ 700 px) или **7 px** (на ≤ 500 px). Зазор нарушает визуальную связность шапки и контента, делает интерфейс «рваным».

**Independent Test**: открыть `/zakroma?author=Машина Времени` в Chrome DevTools, переключить в режим device emulation: viewport 1280×800 (desktop), затем 700×800, затем 500×800, затем 375×667. При `scrollY = 0` и при `scrollY > 200` проверить через DevTools-Console:
```js
const h = document.querySelector('.km-header').getBoundingClientRect()
const s = document.querySelector('.km-author-header-sticky').getBoundingClientRect()
// При scrollY > 0: s.top === h.bottom (perfectly attached, gap === 0)
// Допуск: ± 1 px (sub-pixel rounding)
console.log('gap', s.top - h.bottom)
```
**Критерий PASS**: `s.top - h.bottom` ∈ `[-1, 1]` px на всех viewport-широтах (default / 700 / 500 / 375). Промежуток НЕ появляется ни на одном breakpoint.

**Acceptance Scenarios**:

1. **Given** viewport 1280×800 (desktop), **When** посетитель скроллит вниз, **Then** `.km-author-header-sticky` прилипает к нижней границе AppHeader с зазором ≤ 1 px (поведение сохранено из спек 252).
2. **Given** viewport 700×800 (узкий десктоп), **When** посетитель скроллит вниз, **Then** НЕТ видимого промежутка между шапкой (высота ≈ 49 px) и sticky-wrapper; `gap = s.top - h.bottom ∈ [-1, 1] px`.
3. **Given** viewport 500×800 (мобильный), **When** посетитель скроллит вниз, **Then** НЕТ промежутка между шапкой (высота ≈ 46 px) и sticky-wrapper; зазор ≤ 1 px.
4. **Given** viewport 375×667 (iPhone SE), **When** посетитель скроллит таблицу песен большого автора (2500+ песен, 6 типов альбомов), **Then** sticky-wrapper приклеен к шапке без зазора; блок типов альбомов 2-строчный (`flex-wrap`) не отрезается — обёртка едет/прилипает единой полосой.
5. **Given** пользователь поворачивает мобильный (меняет ориентацию 375×667 ↔ 667×375), **When** происходит resize, **Then** sticky-offset пересчитывается автоматически через CSS media queries (без перезагрузки страницы); промежутка нет ни в portrait, ни в landscape.
6. **Given** окно браузера ресайзится с 1280 до 400 (десктоп drag-to-resize), **When** viewport пересекает breakpoint 700 и 500, **Then** на каждом из них `gap = s.top - h.bottom ∈ [-1, 1] px`.

---

### User Story 2 — переменная `--km-header-height` доступна из всех view (Priority: P2)

Если в будущем другие view (`SearchView`, `AccountView`, `AuthorPlaylistView` и т.п.) тоже захотят использовать sticky-панели под AppHeader, они смогут опираться на ту же CSS-переменную `--km-header-height`, не дублируя media queries и не рискуя рассинхронизации с реальной высотой шапки.

**Why this priority**: даёт переиспользуемость и единый источник истины. Без неё каждый view будет дублировать свои media queries с риском отстать от изменений AppHeader. P2 — потому что сейчас ничего другого не ломается; фикс нужен на будущее.

**Independent Test**: открыть любую страницу, где AppHeader присутствует. В DevTools изменить значение `--km-header-height` через `document.documentElement.style.setProperty('--km-header-height', '99px')` или через `:root`. Замерить `getBoundingClientRect().top` у `.km-author-header-sticky` (после скролла). Ожидаемо: `stickyWrapper.top === 99 + headerActualTop` (т.е. sticky-wrapper прилипает к новой offset-координате).

**Acceptance Scenarios**:

1. **Given** глобальная CSS-переменная `--km-header-height` определена на `:root` (через `style.css`) с media queries, **When** viewport пересекает breakpoint, **Then** значение переменной меняется (`53px → 49px → 46px`) и `.km-author-header-sticky { top: var(--km-header-height) }` пересчитывает sticky-offset.
2. **Given** переменная `--km-header-height` НЕ определена (fallback), **When** любой view использует `top: var(--km-header-height, 53px)`, **Then** fallback на `53px` срабатывает (поведение из спек 252 не ломается).
3. **Given** AppHeader.vue меняет свою высоту (например, высота logo увеличена до 40 px), **When** разработчик обновляет `:root` в `style.css` (или разработчик добавляет media query) синхронно с AppHeader, **Then** зазор исчезает без правки ZakromaView.vue.

---

### User Story 3 — фикс не ломает desktop UX из спек 252 (Priority: P3)

Сохранены устранения дефектов из спек 252:
- На desktop (≥ 700 px) блок типов альбомов НЕ выглядывает хвостом из-под фильтра (US1 спек 252).
- Sticky-wrapper прилипает к шапке единой полосой, никакого overlap'а между подблоками.
- 2-строчный `flex-wrap` блока типов альбомов на мобильном работает корректно (тоже едет/прилипает целиком с обёрткой).

**Why this priority**: P3 — регрессия-проверка. Без неё фикс узких экранов мог бы случайно сломать то, что починили в спек 252.

**Independent Test**: повторить quickstart.md V-1..V-5 спек 252 на desktop + mobile.

**Acceptance Scenarios**:

1. **Given** пользователь на desktop 1280×800 с автором с 6 типами альбомов, **When** он скроллит таблицу песен, **Then** между `.km-filter-bar` и `.km-album-controls-bar` вертикального overlap'а НЕТ (US1 спек 252 сохранён).
2. **Given** mobile 375×667 с автором с 6 типами, **When** блок типов 2-строчный, **Then** обёртка либо едет целиком, либо прилипает целиком — обрезки строк нет.

---

## Requirements *(mandatory)*

### Functional Requirements

#### A. Responsive sticky-offset

- **FR-001**: `.km-author-header-sticky` MUST иметь `top`, равный реальной высоте AppHeader на текущем viewport. На десктопе (> 700 px) — `53 px`; на узком (≤ 700 px) — `49 px`; на мобильном (≤ 500 px) — `46 px`. Допуск ± 1 px на sub-pixel rounding (см. AC1-US1).
- **FR-002**: Изменение `top` должно происходить через media queries (CSS нативно), без JS-измерений в рантайме. Никакого `getBoundingClientRect` в `mounted()` — иначе нарушается принцип «CSS-only fix» и теряется реактивность на resize.
- **FR-003**: При resize (между breakpoints) sticky-offset MUST пересчитываться автоматически (CSS нативно). Никакой перезагрузки страницы не требуется.

#### B. CSS custom property `--km-header-height`

- **FR-004**: Глобальная CSS-переменная `--km-header-height` MUST быть определена на `:root` в `karaoke-public/src/style.css` со значениями:
  - `--km-header-height: 53px;` (default > 700 px)
  - `@media (max-width: 700px) { --km-header-height: 49px; }`
  - `@media (max-width: 500px) { --km-header-height: 46px; }`
- **FR-005**: `top: var(--km-header-height)` для `.km-author-header-sticky` MUST использовать переменную с fallback'ом на `53px` для устойчивости (`top: var(--km-header-height, 53px)`).

#### C. Синхронизация с AppHeader

- **FR-006**: Значения `--km-header-height` в каждом breakpoint MUST соответствовать **фактической** высоте AppHeader (логотип + padding×2 + 1 px border). Если AppHeader.vue меняет высоту logo или padding — `:root`-объявление в `style.css` MUST быть синхронно обновлено (комментарий в обоих файлах ссылается друг на друга, см. AC2-US2).
- **FR-007**: Никаких изменений в `AppHeader.vue`'s scoped CSS (это НЕ его контракт — он остаётся «владельцем» своей высоты; всякие CSS-переменные `--km-header-height` живут глобально в `style.css`).

#### D. Регрессии

- **FR-008**: Никаких изменений в backend (`karaoke-app`, `karaoke-web`), БД, Vuex-стейте, других view.
- **FR-009**: Существующее поведение sticky-обёртки (из спек 252) на desktop сохраняется:
  - z-index: 90;
  - background: var(--km-header);
  - bottom граница не выглядывает из-под фильтра;
  - 2-строчный `flex-wrap` блока типов альбомов на мобильном работает.
- **FR-010**: Никаких новых npm-зависимостей, изменений в `package.json` / `package-lock.json`.

### Key Entities

Не применимо — это чисто CSS-фикс (`ZakromaView.vue` scoped CSS + `style.css` глобально). Бэкенд / DTO / БД / миграции / state-машины не затрагиваются.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: На viewport 1280×800 (desktop) при `scrollY > 0`: `stickyWrapper.top - headerBottom` ∈ `[-1, 1] px` (сохранено из спек 252).
- **SC-002**: На viewport 700×800: то же, что SC-001, но с учётом высоты шапки 49 px. Зазор ≤ 1 px.
- **SC-003**: На viewport 500×800: то же, что SC-001, но с учётом высоты шапки 46 px. Зазор ≤ 1 px.
- **SC-004**: На viewport 375×667 (iPhone SE): зазор ≤ 1 px; обёртка не отрезает 2-строчный блок типов альбомов.
- **SC-005**: При resize 1280 → 400 px: на каждом шаге зазор ≤ 1 px; sticky пересчитывается без перезагрузки.
- **SC-006**: `cd karaoke-public && npm run build` PASS.
- **SC-007**: `npm run lint` (`karaoke-public`) PASS, `tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- **SC-008**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — `:karaoke-web:bootJar UP-TO-DATE` (бэкенд не задет).

## Edge Cases *(include if feature involves data)*

- **Что если AppHeader.vue изменит padding/logo?** → значения `--km-header-height` в `style.css` станут рассинхронизированы с реальной высотой → зазор 1-7 px вернётся. **Решение**: короткий комментарий в AppHeader.vue («При изменении высоты — синхронно обновить `--km-header-height` в `style.css`») и в `:root` (`style.css`) — ссылка на AppHeader.
- **Что если header на ≤ 700 px переносится на 2 строки (`flex-wrap`)?** → высота AppHeader становится ≈ 98 px (двойной ряд); sticky-wrapper прилипает к координате `49 px`, оставляя **зазор** 49 px между нижней границей шапки и верхней границей обёртки. **Это ухудшение UX** при переносе шапки на 2 строки — НЕ часть этой спеки (требует JS-измерения или более сложного решения типа CSS `@container`). Зафиксировано как Assumptions (b).
- **Что если viewport очень узкий (≤ 320 px)?** → стили AppHeader для ≤ 500 px сработают; высота ≈ 46 px (логотип 28 px + padding `0.4rem × 2` + border). На таких ширинах шапка может ещё не перенестись на 2 строки; зазор ≤ 1 px.
- **Что если шапка не sticky (passing другой layout)?** → `top: 53px` обёртки всё равно «зацепится» за координату 53 px от верха viewport, **но** если AppHeader не стики, шапка уйдёт за верх — и появится зазор 53 px между верхним краем и обёрткой. Это **тоже ухудшение UX**, но **не часть этой спеки**: AppHeader.vue практически всегда `km-header-sticky` (см. AppHeader.vue:138-142 и спеку 250). Зафиксировано как Assumptions (c).

## Assumptions

- (a) Существующая высота AppHeader на desktop = `padding 0.5rem × 2 + логотип 36 px + border 1 px = 53 px` (вычислена из `AppHeader.vue:136, 181`); на ≤ 700 px = 49 px (логотип 32 + те же padding'и); на ≤ 500 px = 46 px (логотип 28 + меньшие padding'и). Это **не** внешнее API, а наблюдаемые свойства AppHeader.vue. Если AppHeader.vue меняется — переменная должна быть обновлена.
- (b) На очень узких экранах при переносе AppHeader на 2 строки (`flex-wrap` в `@media (max-width: 700px)`) высота шапки удваивается, а `top: 49px` недостаточен → появится **большой** зазор между шапкой и обёрткой. **Решение не входит в эту спеку** — требует JS `ResizeObserver` или более сложного решения. Это будет видно только при специфическом сочетании: viewport ≤ 700 px + длинный `back` label в AppHeader (текст ссылки «← Главная» длинный) + все 3 виджета шапки присутствуют одновременно. Типичный 412×915 (Pixel 6) — шапка остаётся 1-строчной → этой проблемы нет.
- (c) AppHeader в production всегда `sticky: true` (спека 250-unify-site-header, `AppHeader.vue:2`: `class="['km-header', { 'km-header-sticky': sticky }]"`). НЕ-sticky режим не рассматривается.
- (d) Багфикс чисто клиентский (CSS + маленькая правка `style.css`). Бэкенд / БД / DTO / Vuex не затрагиваются. Бэкенд-NOT-UP-TO-DATE → не ожидается (только frontend CSS).
- (e) Связанная спека: `252-fix-author-album-types-hide` (ввела `.km-author-header-sticky` с хардкоженным `top: 53px`). Фичи комплементарны: спек 252 устранила overlap, спек 253 устраняет responsive-зазор. Никаких изменений в контрактах спек 252 / 250 / 012 не делается — только `top`-значение.

## Что **не** входит в эту спеку

- Перевод AppHeader.vue на CSS-переменную `--km-header-height` для собственных padding/height — в этой спеке оставляем AppHeader в покое, чтобы не выходить за scope.
- Альтернатива `ResizeObserver`-JS для динамической высоты при wrap — отдельная фича (см. assumptions (b)).
- Изменения в любых других view (SearchView / AccountView / AuthorPlaylistView). Если они тоже используют `top: 53px` для своих sticky-панелей — фикс регрессии у них НЕ часть этой спеки. См. Future Work (live-doc 253 при необходимости).

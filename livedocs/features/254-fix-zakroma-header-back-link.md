---
status: Active
slug: 254-fix-zakroma-header-back-link
related:
  - ../architecture/L3-components.md
  - ../../specs/254-fix-zakroma-header-back-link/spec.md
  - ../../specs/254-fix-zakroma-header-back-link/plan.md
  - 250-unify-site-header
  - 008-special-orders
  - 012-entity-description-fields
---

# 254 — Закрома: header-back-link «К списку авторов» + удаление in-page дубля (LiveDoc)

> Drill-down — [specs/254-fix-zakroma-header-back-link/spec.md](../../specs/254-fix-zakroma-header-back-link/spec.md),
> [plan.md](../../specs/254-fix-zakroma-header-back-link/plan.md).

## Что делает

Заменяет статическую header-back-link **«← Главная»** (всегда ведёт на `/`) в `ZakromaView.vue`
на **динамическую**, контекстно-зависимую:
- на `/zakroma` (без выбранного автора) → header-back-link **скрыт**;
- на `/zakroma?author=X` или `/zakrama?specialBucket=true` → header-back-link **«← К списку авторов»** → `/zakroma` (без query).

Дополнительно: удалена in-page `<button class="km-back-btn">` (две кнопки — для обычного автора
и спец-корзины), дублирующая ту же навигацию. CSS `.km-back-btn` также удалён.

## Корень проблемы

До фикса:
- `<AppHeader :back="{ to: '/', label: '← Главная' }" />` — статически, без учёта состояния view.
- Под `<AppHeader>` две `<button class="km-back-btn">` — ссылка «К списку авторов» дублировалась в шапке и в теле страницы. UX нарушение: одна и та же навигация в двух местах.

## Решение (FR-001..FR-006)

1. **Новый computed `zakromaHeaderBack`** в `ZakromaView.vue:computed:`:
   ```js
   zakromaHeaderBack() {
     if (this.authorChosen || this.specialBucketShown) {
       return { to: '/zakroma', label: '← К списку авторов' }
     }
     return null
   }
   ```
2. **`<AppHeader :back="zakromaHeaderBack" />`** в template.
3. **Удалены 2 in-page `<button class="km-back-btn">`** (строки 99-115 в исходном файле).
4. **Удалён `.km-back-btn` CSS** (4 правила, строки 959-980 в исходном файле).

## User Stories (краткий список)

- **US1** (P1): header-back-link «← К списку авторов» на `/zakroma?author=X`.
- **US2** (P1): header-back-link скрыт на `/zakroma` без автора.
- **US3** (P2): in-page `.km-back-btn` удалён.
- **US4** (P3): full-flow регрессия-проверка (без изменения других view).

## Functional Requirements (указатель)

- **FR-001..FR-003**: динамический `:back` prop через `zakromaHeaderBack`.
- **FR-004..FR-006**: удаление in-page кнопок и CSS.
- **FR-007..FR-009**: регрессии (другие view не задеты, sticky/sticky-wrapper сохранены, backend не задет).

Полный список — [spec.md](../../specs/254-fix-zakroma-header-back-link/spec.md#requirements-mandatory).

## Acceptance Criteria

- [ ] **AC1**: На `/zakroma?author=X` в DOM присутствует ровно один `<a class="km-back">` слева в `.km-header-left`, текст «К списку авторов», `href="/zakroma"` (без `?author=`).
- [ ] **AC2**: На `/zakroma` (без query) `.km-header-left .km-back` count = 0.
- [ ] **AC3**: На `/zakroma?author=X` `.km-back-btn` count = 0 (ни в DOM, ни в bundle CSS).
- [ ] **AC4**: Клик на header-back-link «← К списку авторов» → URL `/zakroma` без query, без reload.
- [ ] **AC5**: На `/zakroma?specialBucket=true` header-back-link виден, target `/zakroma`.
- [ ] **AC6**: `cd karaoke-public && npm run build` PASS.
- [ ] **AC7**: `npm run lint` (`karaoke-public`) — 0 warnings.
- [ ] **AC8**: `tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- [ ] **AC9**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` —
      `:karaoke-web:bootJar UP-TO-DATE`, бэкенд не задет.

## Связанные LiveDocs

- [250-unify-site-header](../features/250-unify-site-header.md) — единый
  AppHeader с prop `back: Object, default: null`. **Без изменений** — фикс 254
  переиспользует существующий API (`back: null` для скрытия + `back: { to, label }`
  с минимальными данными). Это демонстрирует явный паттерн «back: null = hidden»
  для будущих view.
- [008-special-orders](../features/008-special-orders.md) — спец-корзина
  `?specialBucket=true`. Header-back-link теперь работает и для неё (возврат к
  тайлам авторов).
- [012-entity-description-fields](../features/012-entity-description-fields.md) —
  feature, в которой появилась `zakromaAlbumTypeCounts` и обёртка
  `.km-author-header-sticky` — без изменений для спеки 254.
- [255-fix-zakroma-state-reset-on-back-nav](255-fix-zakroma-state-reset-on-back-nav.md) —
  **bug-fix спек 254**: в спеке 254 предполагалось, что vue-router сам сбросит
  `data`-properties при переходе `/zakroma?author=X` → `/zakroma`, но это не так
  (Options API `data()` вычисляется один раз). LiveDoc 255 добавляет watcher на
  `$route.query.author`, который сбрасывает `authorChosen`, `selectedAuthor`,
  `specialBucketShown`, `songFilter` при смене query. Без LiveDoc 254 header-back-link
  технически навигирует, но `v-if="authorChosen"` остаётся `true` → пользователь видит
  scroll-to-top вместо тайлов.
- Architecture: [L3-components.md](../architecture/L3-components.md) — структура
  Vue-компонентов `karaoke-public`, в т.ч. `ZakromaView.vue`.

## Код

- `karaoke-public/src/views/ZakromaView.vue` — **единственный файл с правками**:
  - Template `AppHeader :back` → динамический (`:back="zakromaHeaderBack"`, ~строка 6).
  - Computed `zakromaHeaderBack()` в секции `computed:` (~строка 503).
  - Удалены 2 in-page `<button class="km-back-btn">` (~старые строки 99-115).
  - Удалены стили `.km-back-btn` (~старые строки 959-980).

Бэкенд (`karaoke-app`, `karaoke-web`) и DTO **не затронуты**. `AppHeader.vue`
**не правится** — API уже поддерживает `null` для скрытия back-link и `to`
без `query` для перехода на конкретный path.

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27

---
status: Active
slug: 255-fix-zakroma-state-reset-on-back-nav
related:
  - ../architecture/L3-components.md
  - ../../specs/255-fix-zakroma-state-reset-on-back-nav/spec.md
  - ../../specs/255-fix-zakroma-state-reset-on-back-nav/plan.md
  - 254-fix-zakroma-header-back-link
  - 250-unify-site-header
  - 008-special-orders
  - 012-entity-description-fields
---

# 255 — Закрома: сброс state при навигации от автора к тайлам (LiveDoc)

> Drill-down — [specs/255-fix-zakroma-state-reset-on-back-nav/spec.md](../../specs/255-fix-zakroma-state-reset-on-back-nav/spec.md),
> [plan.md](../../specs/255-fix-zakroma-state-reset-on-back-nav/plan.md).

## Что делает

Bug-fix после спеки 254. Когда пользователь на `/zakroma?author=X` кликает на
header-back-link «← К списку авторов», **vue-router обновляет URL**, но:
- `data.authorChosen` остаётся `true` (было вычислено один раз при создании компонента),
- `data.selectedAuthor`, `data.songFilter`, `data.specialBucketShown` — тоже,
- `v-if="authorChosen"` остаётся `true` → фильтр-блок и контент автора продолжают
  рендериться, сетка тайлов (`v-if="!authorChosen"`) НЕ отрисовывается.

Пользователь видит scroll-to-top вместо переключения на тайлы — содержимое страницы
остаётся прежним.

## Корень

Options API `data()`-properties вычисляются **один раз** при создании инстанса компонента
и не реагируют на изменение `$route`. Vue-router 4 при навигации на тот же path
(`/zakroma` → `/zakroma`) с изменением только query НЕ пересоздаёт компонент;
срабатывают watcher'ы (если есть) на `$route`-properties.

## Решение

Один watcher на `$route.query.author` в секции `watch:` `ZakromaView.vue`:

```js
'$route.query.author'(newAuthor) {
  if (!newAuthor && this.authorChosen) {
    // Снятие выбора: header-back-link / browser back / programmatic $router.push без query.
    this.selectedAuthor = ''
    this.authorChosen = false
    this.specialBucketShown = false
    this.songFilter = ''
  } else if (newAuthor && newAuthor !== this.selectedAuthor) {
    // Смена автора через URL (deep-link / browser forward).
    this.selectedAuthor = newAuthor
    this.authorChosen = true
    this.songFilter = ''
    this.loadZakromaStream({ author: newAuthor, expectedCount: undefined })
  }
}
```

Двухветвевая логика:
1. **`!newAuthor && this.authorChosen`** — query.author стал пуст → сбросить все 4
   data-properties (аналог исходного метода `backToAuthors()`).
2. **`newAuthor && newAuthor !== this.selectedAuthor`** — query.author изменился на
   другой (deep-link) → перезагрузить стрим для нового автора.

При первичном монтировании `data.authorChosen` уже соответствует query.author
(через `data() { return { authorChosen: !!this.$route.query.author } }`).
Условие `&& this.authorChosen` гарантирует идемпотентность на первичной загрузке.

## User Stories (краткий список)

- **US1** (P1): клик на header-back-link реально переключает UI на тайлы.
- **US2** (P1, регрессия): browser BACK ведёт к тайлам (а не к scroll-to-top).
- **US3** (P3, edge): deep-link на другой автор перезагружает стрим.

## Functional Requirements (указатель)

- **FR-001..FR-004**: добавление watcher'а на `$route.query.author`.
- **FR-005..FR-007**: регрессии на спеки 254 / 252 / 253 / 250.

Полный список — [spec.md](../../specs/255-fix-zakroma-state-reset-on-back-nav/spec.md#requirements-mandatory).

## Acceptance Criteria

- [ ] **AC1**: клик на header-back-link «← К списку авторов» → URL `/zakroma` + сетка тайлов видна + `.km-author-header-sticky` скрыт.
- [ ] **AC2**: browser BACK с `/zakroma?author=X` → `/zakroma` + тайлы.
- [ ] **AC3**: deep-link `/zakroma?author=B` поверх `/zakroma?author=A` → контент перерисован на B, новый стрим.
- [ ] **AC4**: `npm run build` + `npm run lint` (karaoke-public) PASS; `eslint-baseline` 0/0.
- [ ] **AC5**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — `:karaoke-web:bootJar UP-TO-DATE`.

## Связанные LiveDocs

- [254-fix-zakroma-header-back-link](../features/254-fix-zakroma-header-back-link.md) — ввела
  header-back-link через `<RouterLink :to="/zakroma">`. **Без изменений** — LiveDoc 255
  добавляет watcher, который ловит смену `$route.query.author` от любого триггера
  (header-back, browser back, programmatic). Это и есть «bug-fix 255» по тексту
  LiveDoc 254.
- [250-unify-site-header](../features/250-unify-site-header.md) — AppHeader API
  остаётся неизменным (поддерживает `null` для скрытия back-link и RouterLink для
  навигации). Без изменений.
- [008-special-orders](../features/008-special-orders.md) — спец-корзина
  `?specialBucket=true`. Покрыта частично (см. assumption (d) спеки 255): если
  пользователь пришёл через `?specialBucket=true` (без `?author`), header-back-link
  ведёт на `/zakroma`, watcher сбрасывает `authorChosen = false`, но
  `specialBucketShown` остаётся `true` → рендерится specialBucket-таблица, не тайлы.
  Полное покрытие спец-корзины — отдельная фича (дополнительный watcher на
  `$route.query.specialBucket`).
- [012-entity-description-fields](../features/012-entity-description-fields.md) — sticky
  фильтр/типы альбомов. Без изменений.
- Architecture: [L3-components.md](../architecture/L3-components.md).

## Код

- `karaoke-public/src/views/ZakromaView.vue` — **единственный файл с правками**:
  новый watch-блок `'$route.query.author'(newAuthor) { ... }` в секции `watch:`
  (после `zakroma` и `specialBucket` watcher'ов).

Бэкенд (`karaoke-app`, `karaoke-web`), AppHeader.vue, router-config, store-модули —
**не затронуты**. JS bundle +~250 байт (новый watcher-handler).

## Известные ограничения (out of scope)

- **`?specialBucket=true`** без `?author=X` — watcher `query.author` срабатывает, но не сбрасывает `specialBucketShown`. См. assumption (d) спеки + «Что НЕ входит в эту спеку».
- Компонент **не пересоздаётся** при навигации (это **фича** Vue, не баг) — поэтому watcher'ы остаются каноничным способом реагировать.

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27

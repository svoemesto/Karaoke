# Feature Specification: Закрома — корректный сброс state при навигации от автора обратно к тайлам (через header-back-link или browser back)

**Feature Branch**: `255-fix-zakroma-state-reset-on-back-nav`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description (баг-репорт): «клик на "к списку авторов" в шапке не приводит к показу страницы с тайтлами авторов, а только переходит в начало уже открытой страницы со списком песен автора».

## User Scenarios & Testing *(Priority: P1; flows — bug-fix после спеки 254)*

### User Story 1 — клик на header-back-link реально переключает UI на сетку тайлов авторов

Посетитель на `/zakroma?author=Машина Времени` кликает на header-back-link «← К списку авторов». После клика:
- URL становится `localhost/zakroma` (без `?author=…`),
- **визуально** отображается сетка тайлов авторов (`.km-page` → сетка `.at-grid`),
- блок `.km-author-header-sticky` (фильтр + типы альбомов) **скрыт**,
- если была активна спец-корзина (`?specialBucket=true`) — её view тоже сбрасывается на тайлы,
- любые in-page ошибки / прогресс-бары (`.km-stream-progress`, `.km-stream-error`, `.km-stream-cancel`) — сбрасываются.

**Why this priority**: прямой баг-репорт. Спека 254 ввела `<RouterLink :to="/zakroma">` через AppHeader, но **не учла**, что vue-router при переходе на тот же path с другим query НЕ пересоздаёт инстанс Vue-компонента — `data`-properties (`authorChosen`, `selectedAuthor`, `songFilter`, `specialBucketShown`) сохраняют значения от предыдущего состояния. Например:
- `data.authorChosen = !!this.$route.query.author` — вычисляется **один раз** при создании компонента (в `data()`), при изменении query не пересчитывается.
- Поэтому после клика URL = `/zakroma`, query.author = `undefined`, но `data.authorChosen === true` (старый). Результат: `v-if="authorChosen"` в template остаётся `true`, рендерится `.km-author-header-sticky` + `.km-author-block`, а тайлы (`.km-page` → `v-if="!authorChosen"`) **не рендерятся**. Только scroll-to-top — потому что `<RouterLink>` всё-таки обновляет hash/history.

**Independent Test**: открыть `/zakroma?author=Машина Времени` → кликнуть на «← К списку авторов» в шапке:
```js
// После клика:
location.pathname  === '/zakroma'
location.search    === ''
document.querySelector('.km-author-header-sticky') === null  // фильтр-блок скрыт
document.querySelector('.at-grid, .km-author-tiles, [data-author-tiles]') !== null  // тайлы видны
```
Также через DevTools — Elements → tab visibility: `.km-author-block` НЕ в DOM, `.km-page > div` показывает сетку тайлов.

**Acceptance Scenarios**:

1. **Given** URL `/zakroma?author=Машина Времени`, **When** пользователь кликает на header-back-link «← К списку авторов», **Then** URL `/zakroma`, сетка тайлов отрисовывается, фильтр-блок скрыт, header-back-link сам скрывается (т.к. `!authorChosen`).
2. **Given** URL `/zakroma?specialBucket=true`, **When** пользователь кликает на «← К списку авторов», **Then** URL `/zakroma`, сетка тайлов отрисовывается, спец-таблица скрыта.
3. **Given** URL `/zakroma?author=X` + активный стрим (`.km-stream-progress` виден), **When** пользователь кликает на header-back-link, **Then** URL `/zakroma`, прогресс-бар скрыт (т.к. `v-if="authorChosen"` блок не рендерится), тайлы показаны.

---

### User Story 2 — browser BACK ведёт себя так же (reгрессионная проверка)

Пользователь на `/zakroma?author=X` нажимает кнопку «назад» браузера. Аналогично US1 — тайлы показаны, фильтр-блок скрыт, заголовок без back-link.

**Why this priority**: US1 фикс делается через `$route.query.author` watcher, который реагирует на любую смену query — в т.ч. browser back/forward. Это автоматически покрывает US2, но проверять надо явно.

**Independent Test**: same as US1, но триггер — кнопка «назад» браузера.

**Acceptance Scenarios**:

1. **Given** URL `/zakroma?author=X`, **When** пользователь нажимает browser «←», **Then** URL = `/zakroma`, сетка тайлов отрисовывается.
2. **Given** URL `/zakroma?author=X` → клик-тапл → `/zakroma?author=Y`, **When** пользователь нажимает browser «←», **Then** URL = `/zakroma?author=X`, контент снова показывает автора X (требует watcher и reload стрима — частичное покрытие, см. FR-003).

---

### User Story 3 — смена автора через URL работает (deep-link / browser back-forward)

Пользователь на `/zakroma?author=Машина Времени` правит URL на `/zakrama?author=Кино` (или browser history navigation). Контент перерисовывается на нового автора, стрим перезагружается.

**Why this priority**: edge case, но не покрыть его — значит допустить регрессию при навигации browser back/forward между двумя авторами. Минимальное покрытие — обновить `selectedAuthor` и перезагрузить стрим.

**Independent Test**: в DevTools → console выполнить `history.pushState({}, '', '/zakroma?author=Кино')` (или напрямую поправить URL). Контент перерисовывается; в DOM — заголовок «Кино» вместо «Машина Времени»; `.km-stream-progress` появляется заново (если стрим медленный).

**Acceptance Scenarios**:

1. **Given** URL `/zakroma?author=A`, **When** пользователь правит URL на `/zakroma?author=B`, **Then** `data.selectedAuthor = 'B'`, контент показывает B, новый стрим стартует.
2. **Given** URL `/zakroma` (без автора) → клик на тайле автора B, **When** URL становится `/zakroma?author=B`, **Then** контент показывает B (это уже работало через `onAuthorSelect` + watcher-логика не требуется).

---

## Requirements *(mandatory)*

### Functional Requirements

#### A. Watcher `$route.query.author` в `ZakromaView.vue`

- **FR-001**: В секции `watch:` `ZakromaView.vue` добавить watcher на `$route.query.author` (строковый ключ с точкой для path `'route'` → `query.author`; используем строковый путь для совместимости с Options API):
  ```js
  '$route.query.author'(newAuthor) {
    if (!newAuthor && this.authorChosen) {
      // Навигация ОТ автора обратно к тайлам: header-back-link, browser back,
      // programmatic router.replace/push без query.
      this.selectedAuthor = ''
      this.authorChosen = false
      this.specialBucketShown = false
      this.songFilter = ''
    } else if (newAuthor && newAuthor !== this.selectedAuthor) {
      // Смена автора через URL: deep-link, browser forward, programmatic.
      // Обновить state и перезагрузить стрим для нового автора.
      this.selectedAuthor = newAuthor
      this.authorChosen = true
      this.songFilter = ''
      this.loadZakromaStream({ author: newAuthor, expectedCount: undefined })
    }
  }
  ```

- **FR-002**: Условие `!newAuthor && this.authorChosen` — гарантирует, что watcher срабатывает только при **реальной** смене query (когда есть, что сбрасывать). На первоначальной загрузке `data.authorChosen = false` уже, так что идемпотентен.

- **FR-003**: Условие `newAuthor && newAuthor !== this.selectedAuthor` — обрабатывает deep-link / browser back-forward между авторами. Сравнение строковое; если `selectedAuthor` пустой (`''`), а `newAuthor` не пустой — это первый заход на нового автора (deep-link), watcher правильно отрабатывает.

- **FR-004**: Watcher НЕ трогает `this.zakroma` / Vuex store напрямую — store recompute'ится из getters автоматически при изменении `data`-properties. Если нужен явный reset (например, для освобождения памяти), это отдельный refactor.

#### B. Регрессии на спеку 254 и 252/253/250

- **FR-005**: Header-back-link из спеки 254 остаётся как `<RouterLink :to="/zakroma">` — НЕ модифицируется. Watcher из FR-001 реагирует на смену query автоматически.

- **FR-006**: Sticky-stэк (AppHeader + `.km-author-header-sticky`, спеки 250/252/253) сохраняется. Скрытие через `v-if="authorChosen"` — никаких изменений.

- **FR-007**: Никаких изменений в backend, БД, Vuex-actions, composables. Только дополнительный watcher в одном файле.

### Key Entities

Не применимо — фикс чисто UI-state-management.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: На `/zakroma?author=Машина Времени` клик на header-back-link «← К списку авторов» приводит к **видимому** переключению UI на сетку тайлов (`.km-author-block` НЕТ в DOM, `.at-grid` есть).
- **SC-002**: После клика URL = `/zakroma` без query, фильтр-блок `.km-author-header-sticky` НЕ в DOM, header-back-link скрыт.
- **SC-003**: Browser BACK с `/zakroma?author=X` ведёт к `/zakroma` с тайлами (аналогично SC-001).
- **SC-004**: Browser BACK с `/zakroma?author=Y` на `/zakroma?author=X` (forward history после двух кликов тайлов) — контент показывает X без полного reload страницы (`location.reload` НЕ вызывается).
- **SC-005**: Программное `history.pushState` (или `this.$router.push({ path: '/zakroma', query: { author: 'Кино' } })` из devtools) перерисовывает контент на нового автора, новый стрим стартует (через ~1 сек видна мета `expectedCount` Кино).
- **SC-006**: `cd karaoke-public && npm run build` PASS, `npm run lint` 0 warnings, `tools/check-eslint-baseline.sh karaoke-public` 0/0.
- **SC-007**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — `:karaoke-web:bootJar UP-TO-DATE`.

## Edge Cases *(include if feature involves data)*

- **Снятие выбора при переходе туда-обратно по history** — watcher срабатывает на любую смену query, включая browser-history. Корректно.
- **`newAuthor === ''`** (пустая строка как query) — Vue-router кодирует как `?author=` (без значения); `newAuthor` будет `''`, falsy. Watcher войдёт в ветку «снятие выбора». Корректно (UI не должен считать пустую строку валидным автором).
- **`?specialBucket=true`** — независимо от `?author=`, watcher смотрит только на `query.author`. Если `specialBucket=true` без author, watcher не сработает; для спец-режима нужно отдельное поведение. **Спец-режим переходит на `/zakroma` через тот же header-back-link** → Vue-router видит смену query (нет author) + нет specialBucket → по идее нужно сбросить specialBucketShown. **FIX**: проверять `query.specialBucket` тоже или добавить более общий watcher на `route` целиком.
- **`?author=A&specialBucket=true`** (комбинация) — frontend вряд ли это генерирует, но если придёт по deep-link: `query.author = 'A'` → `authorChosen = true`, watcher войдёт в ветку «смена/установка автора». specialBucket НЕ обработан watcher'ом — `data.specialBucketShown` останется стейл. Edge case, наблюдательный.

## Assumptions

- (a) Vue-router 4 при навигации на тот же path с разным query НЕ пересоздаёт компонент, а только обновляет `$route`. Это стандартное поведение; watcher на `$route.query.author` — каноничный способ реагировать. Подтверждается тем, что в коде уже есть `watch: { zakroma: { handler } }` pattern.
- (b) `data.authorChosen = !!this.$route.query.author` — set-once pattern, и в этом вся суть бага. Vue не ре-eval'уирует `data()` функцию при изменении проп/route.
- (c) Watcher сбрасывает именно **локальный** state (4 поля в `data`), без касания Vuex `state.zakroma` / `state.specialBucket`. Store остаётся закэшированным — это безопасно, потому что:
  - `state.zakroma` отображается только при `authorChosen = true`, после сброса не используется.
  - При выборе нового автора `onAuthorSelect` инициирует новый stream → state перезаписывается.
- (d) Дополнительное покрытие спец-корзины (`?specialBucket=true`): для полноценного решения watcher должен реагировать и на `query.specialBucket`. Текущая спека **ограничивается** watcher'ом на `query.author`. Спец-кейс покрывается через существующий поведенческий контракт: при `?specialBucket=true` без `?author=` data.specialBucketShown = true. При клике header-back-link → URL `/zakroma` → query.author пуст и query.specialBucket пуст. Watcher сбрасывает `authorChosen = false`, но `data.specialBucketShown` остаётся true. Сетка тайлов **не** отрисуется (т.к. `displayedZakroma` вернёт specialBucket, не тайлы). **Это частичный фикс — см. FR-009 в разделе "Что НЕ входит в эту спеку"**.
- (e) Бэкенд не затрагивается (Конституция Principles II, V). Чисто Options API watcher в `ZakromaView.vue`.

## Что НЕ входит в эту спеку

- Полная нормализация watcher'ов для `?specialBucket=true` (отдельная задача — нужен дополнительный watcher на `$route.query.specialBucket`). Текущая спека фокусируется на б основном баге «назад к тайлам от автора».
- Освобождение памяти Vuex state при сбросе `authorChosen` (например, `commit('zakroma/clearZakroma')`).
- Защита от двойных срабатываний watcher'а (race conditions при быстром browser back-forward). Не критично для UX.
- Рефакторинг спеки 254 (Header-back-link) — текущая спека **только добавляет** watcher; AppHeader.vue и `<RouterLink :to="..."` остаются без изменений.
- Мультиязычность / i18n.
- Тесты / автотесты (отсутствуют в проекте — AGENTS.md).

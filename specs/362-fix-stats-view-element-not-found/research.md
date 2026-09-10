# Phase 0 Research: ApexCharts «Element not found» в StatsView

**Spec**: [`spec.md`](./spec.md)
**Branch**: `362-fix-stats-view-element-not-found`
**Date**: 2026-09-10

## Неизвестные из Technical Context

В Technical Context нет `NEEDS CLARIFICATION` маркеров — все вопросы
были разрешены в спеке (Clarifications секция). Однако для архитектурной
уверенности исследованы 3 вопроса:

### R1. ApexCharts `Element not found` — корень проблемы

**Decision**: Race в DOM между `vue3-apexcharts` (рендер SVG) и параллельными
обновлениями store (от 11 HTTP-запросов).

**Rationale**: Из стектрейса в Issue #79:
```
Uncaught (in promise) Error: Element not found
  at index--dqHOftT.js:1442:2730
  at new Promise (<anonymous>)
  at Proxy.render (index--dqHOftT.js:1442:1293)
```
Стек указывает на `Proxy.render` — это apexcharts `render()` метод.
При `render()` apexcharts проверяет `D.elementExists(this.el)` (строка 833
исходного кода `apexcharts.js`), и если `this.el` (DOM-элемент, в который
рендерится SVG) не существует — reject с `Element not found`.

Когда это происходит в нашем коде:
1. `StatsView.vue:mounted() → reloadAll()` запускает 11 параллельных HTTP
2. Vue получает 11 новых mutation'ов в store (sets of summary/timeseries/...)
3. Vue re-render'ит `<apexchart>` компоненты (TimeSeriesChart,
   TypeChannelBreakdown, GeoReferrers, DetailBreakdown) с новыми series
4. **Apexcharts внутри `<apexchart>` получает props update → пытается
   перерендерить SVG в существующий DOM-элемент**
5. **Но:** Vue уже начал remove + insert в DOM (из-за v-if/v-else гардов),
   apexcharts пытается render в DOM-элемент, который в этот момент
   удалён (или ещё не закрепился после insert)
6. → `elementExists(this.el) → false → reject → "Element not found"`

**Alternatives considered**:
1. **AbortController** для отмены in-flight HTTP на unmount —
   НЕ подходит (нет в lib/utils, добавлять вне scope FR-011).
2. **await всех HTTP последовательно** — слишком медленно (11 запросов
   по 100ms = 1.1s только на mounted).
3. **Загружать данные ДО mounted()** — невозможно (нет preloader'а
   во Vue 3 без route guards).
4. **Lazy load активной вкладки + watcher на activeTab + TTL** ✅
   — это и есть FR-001..FR-008. Race исчезает потому, что
   при `mounted()` отправляется только **1-2** запроса (для
   активной вкладки KPI: `summary` + `monetization summary`),
   store меняется **синхронно с одним render pass**, и apexcharts
   успевает отрисовать SVG до следующего watcher trigger.

### R2. Vuex singleton vs data() для `lastLoadedAt`

**Decision**: Vuex store (singleton).

**Rationale**: Из Clarifications спеки (Pass 362):
- US3 acceptance scenario 2: при возврате на страницу «Статистика»
  в течение 60 сек — **0 новых HTTP**.
- `data()` компонента `StatsView.vue` сбрасывается на unmount.
  При возврате на страницу компонент пересоздаётся → `data()`
  инициализируется заново → `lastLoadedAt = {}` → TTL не
  работает → HTTP шлются заново (race возвращается).
- Vuex store — **singleton** в пределах `webvue3` app (создаётся
  при инициализации Vuex, живёт до закрытия вкладки браузера).
  Timestamps сохраняются между mount/unmount.

**Alternatives considered**:
1. **`data()` компонента** — сбрасывается на unmount → TTL
   не работает в US3 acceptance 2. Отклонено.
2. **`localStorage`** — overkill для 60s TTL (не нужен
   persistence между сессиями). Также требует JSON.stringify
   при каждом update. Отклонено.
3. **`sessionStorage`** — живёт только в рамках одной browser
   tab, теряется при reload. Не подходит для «вернулся на
   страницу Статистика в течение 60 сек» (предполагается,
   что F5 не делалось). Альтернативно, но `Vuex store` проще.
4. **Vuex store (singleton)** ✅ — минимальный код (один
   `state.lastLoadedAt = {}` + один `getLastLoadedAt(tab)`),
   нет персистентности между сессиями (TTL — in-memory).

### R3. Удалять ли `reloadAll()` сразу или сначала ввести `loadDataForActiveTab()` параллельно?

**Decision**: Удалять сразу (FR-003).

**Rationale**: Из Clarifications спеки:
- Сохранение двух методов = footgun: разработчик может случайно
  вызвать `reloadAll()` (он будет помечен deprecated, но не
  удалён → следующий PR может его вызвать).
- Спека 174 (`specs/174-fix-stats-connection-leak/spec.md`)
  обещала удаление, но не сделала. Spec 362 выполняет это обещание.
- Кнопка «Обновить всё» тоже удаляется (её единственный смысл —
  вызов `reloadAll()`). Заменяется на «Обновить» с lazy-семантикой.

**Alternatives considered**:
1. **Сначала ввести новый, оставить старый deprecated** —
   отложенный техдолг, риск regression. Отклонено.
2. **Удалить сразу + обновить UI (кнопка)** ✅ — чисто, нет
   footgun. Требует одновременного изменения view + store +
   тестов.

## Итог: 3 вопроса решены, 0 NEEDS CLARIFICATION осталось.

Все архитектурные решения зафиксированы в:
- **FR-001..FR-008** (spec.md) — lazy load + watcher + TTL
- **FR-009..FR-011** (spec.md) — гарантии от race
- **Key Entities** (spec.md) — `lastLoadedAt` в Vuex store
- **Clarifications** (spec.md) — 5 вопросов, все с обоснованием

Можно переходить к Phase 1 (data-model.md, quickstart.md).

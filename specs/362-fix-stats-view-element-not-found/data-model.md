# Phase 1 Data Model: StatsView lazy load + TTL

**Spec**: [`spec.md`](./spec.md)
**Plan**: [`plan.md`](./plan.md)
**Date**: 2026-09-10

## Overview

Фича чисто фронтовая (webvue3 admin SPA). Data model описывает
**UI-state** компонента `StatsView.vue` и **store-state** Vuex-модуля
`webvue3/src/components/Stats/store.js`. Бизнес-сущности (Song, Event,
User) уже описаны в `knowledge/domains/stats/domain.md` и НЕ затрагиваются.

## Entities

### 1. `activeTab` (UI-state)

**Owner**: `StatsView.vue` (data())
**Type**: `Number` (0..7)
**Default**: `0` (KPI)

**Values** (маппинг из FR-004):

| Index | Вкладка | Endpoint'ы (FR-004) |
|---|---|---|
| 0 | KPI | `/api/stats/summary` |
| 1 | Монетизация | `/api/stats/monetization` |
| 2 | Динамика | `/api/stats/timeseries` (mode=current) |
| 3 | Разбивки | `/api/stats/by-type`, `/api/stats/channels`, `/api/stats/by-detail` |
| 4 | География | `/api/stats/countries`, `/api/stats/referrers` |
| 5 | Пользователи | `/api/stats/top-users` (page, pageSize) |
| 6 | Слушают | `/api/stats/top-listened` (page, pageSize) |
| 7 | События | `/api/stats/by-song`, `/api/webevents` (page, pageSize, type) |

**Lifecycle**:
- Created: при `mounted()` (в `data()` инициализируется через `this.$store.getters.getStatsBySongPage` для persistence между mount/unmount).
- Updated: при клике по табу (v-model на `<BTabs>`).
- Destroyed: при unmount компонента.

**Validation rules**: `0 <= activeTab <= 7` (Vuex `state.activeTabIndex`
не валидируется — это UI-state, не критично).

### 2. `lastLoadedAt` (Vuex store singleton)

**Owner**: `webvue3/src/components/Stats/store.js` (state)
**Type**: `Object<Number, Number>` (Map от tabIndex → timestamp в ms)
**Default**: `{}`

**Example**:
```javascript
state.lastLoadedAt = {
  0: 1725968400000,  // KPI loaded at 2026-09-10T08:00:00Z
  2: 1725968460000,  // Динамика loaded 1 minute later
  // tabs 1, 3, 4, 5, 6, 7 not loaded yet
}
```

**Lifecycle**:
- Created: при инициализации Vuex store (default: `{}`).
- Updated: внутри action `loadXxxForActiveTab(activeTabIndex)` —
  `ctx.commit('setLastLoadedAt', { tab: activeTabIndex, ts: Date.now() })`
  после успешного HTTP.
- Read: внутри `loadDataForActiveTab(activeTabIndex)` —
  `if (Date.now() - lastLoadedAt[activeTab] < STATS_FRONT_TTL_MS) return`.
- Destroyed: при закрытии вкладки браузера (singleton).

**Validation rules**:
- Timestamps — `Date.now()` (ms since epoch).
- TTL: 60_000 ms (см. `STATS_FRONT_TTL_MS`).

### 3. `STATS_FRONT_TTL_MS` (константа)

**Owner**: `StatsView.vue` (module-level const)
**Type**: `Number` (миллисекунды)
**Default**: `60_000` (60 сек)

**Why**: см. спеку 174 (`specs/174-fix-stats-connection-leak/spec.md`,
FR-005). Соответствует TTL backend'а (`StatsCache` — `60s`).

**Не выносится в Vuex store** — это UI-константа, не state.

### 4. `tabEndpoints` (константа)

**Owner**: `StatsView.vue` (module-level const)
**Type**: `Object<Number, Array<String>>` — маппинг
**Default**:

```javascript
const tabEndpoints = {
  0: ['summary'],          // KPI
  1: ['monetization'],     // Монетизация
  2: ['timeseries'],       // Динамика
  3: ['by-type', 'channels', 'by-detail'],  // Разбивки
  4: ['countries', 'referrers'],           // География
  5: ['top-users'],        // Пользователи
  6: ['top-listened'],     // Слушают
  7: ['by-song', 'webevents'],             // События
}
```

**Why const, не Vuex**: маппинг статический, никогда не меняется в runtime.
Хранение в `data()` или computed не нужно — это compile-time constant.

### 5. `dayDependentEndpoints` (константа)

**Owner**: `StatsView.vue` (module-level const)
**Type**: `Set<String>`
**Default**:

```javascript
const dayDependentEndpoints = new Set([
  'summary', 'timeseries', 'by-type', 'by-detail'
])
```

**Why**: при `onDaysChange()` нужно обновить только эти endpoint'ы.
Остальные (`top-users`, `top-listened`, `by-song`, `webevents`) НЕ
зависят от `days` (фильтруют по дате на backend'е через `eventDate`,
но `days` в URL не передают).

### 6. `targetDependentEndpoints` (константа)

**Owner**: `StatsView.vue` (module-level const)
**Type**: `Set<String>`
**Default**:

```javascript
const targetDependentEndpoints = new Set([
  // Все — `target` присутствует в URL.
  'summary', 'monetization', 'timeseries', 'by-type', 'channels',
  'by-detail', 'countries', 'referrers', 'top-users', 'top-listened',
  'by-song', 'webevents', 'monetization-top-songs'
])
```

**Why**: при `onTargetChange()` нужно **очистить** данные активной
вкладки в store + **обновить** только эту вкладку (не все 10-12).
Очистка нужна чтобы не показывать stale данные, пока грузятся новые.

## Relationships

```
StatsView.vue
├── activeTab (data) ──────────────────────────┐
│                                               │
├── $store (Vuex)                              │
│   ├── state.lastLoadedAt { ──────────────────┤── TTL check
│   │   0: 1725968400000,                       │
│   │   2: 1725968460000,                       │
│   │   ...                                     │
│   │ }                                         │
│   ├── state.summary (existing) ───────────────┼── sets via setStatsSummary
│   ├── state.timeSeries (existing) ────────────┼── sets via setStatsTimeSeries
│   └── ... (10 more existing)                  │
│                                               │
├── STATS_FRONT_TTL_MS = 60_000 (const)         │
├── tabEndpoints = { ... } (const) ─────────────┘
├── dayDependentEndpoints = Set([...]) (const)
└── targetDependentEndpoints = Set([...]) (const)
```

## State Transitions

### Trigger 1: `mounted()`

```
activeTab = 0 (default)
↓
loadDataForActiveTab(0)
↓
проверка TTL: lastLoadedAt[0] ?
  ├── есть + age < 60s → skip
  └── нет или age >= 60s → load endpoint(s) for tab 0
↓
mutation setStatsSummary, setStatsMonetization
↓
mutation setLastLoadedAt { tab: 0, ts: Date.now() }
```

### Trigger 2: `activeTab change (user clicks tab)`

```
user clicks "Динамика" → activeTab = 2
↓
watcher on activeTab
↓
loadDataForActiveTab(2)
↓
проверка TTL: lastLoadedAt[2] ?
  ├── есть + age < 60s → skip (US3 scenario 1)
  └── нет или age >= 60s → loadStatsTimeSeries
↓
mutation setStatsTimeSeries
↓
mutation setLastLoadedAt { tab: 2, ts: Date.now() }
```

### Trigger 3: `onTargetChange()` (user switches DB local/remote)

```
user clicks <select> → target = 'remote'
↓
mutation setStatsTarget (existing)
↓
clearActiveTabData(activeTab)
  ├── mutation setStatsSummary(null)  // если activeTab=0 (KPI)
  └── mutation setStatsMonetization(null)
↓
loadDataForActiveTab(activeTab)  // загрузка с target=remote
```

### Trigger 4: `onDaysChange()` (user changes period)

```
user clicks <select> → days = 90
↓
mutation setStatsDays (existing)
↓
loadDataForActiveTab(activeTab)  // загрузка с days=90
  // Эта вкладка имеет endpoint с фильтром days? см. dayDependentEndpoints
↓
НЕ очищаем данные для вкладок БЕЗ фильтра days (top-users, top-listened, и т.д.)
```

## Validation Rules (FR)

Из spec.md:

- **FR-006**: `lastLoadedAt[tab]` MUST быть Number (Date.now() в ms).
  Проверяется через `typeof lastLoadedAt[tab] === 'number'`.
- **FR-004**: `tabEndpoints` MUST содержать все 8 вкладок (0-7).
  Проверяется unit-тестом `Object.keys(tabEndpoints).length === 8`.
- **FR-007**: `onTargetChange()` MUST очистить данные активной
  вкладки ДО загрузки новых. Реализуется через
  `clearActiveTabData(this.activeTab)` перед `loadDataForActiveTab`.

## Migration / Compatibility

**Migration from 174**:
- Спека 174 обещала `loadDataForActiveTab` (FR-001) — НЕ реализовано.
- Spec 362 реализует это обещание + добавляет TTL в store (174 обещал
  TTL на фронте через `lastLoadedAt` — тоже не реализовано).

**Backward-compat**:
- `reloadAll()` удаляется → если есть сторонние вызовы
  (кроме StatsView), они сломаются. **Поиск**: `grep -rn 'reloadAll'
  webvue3/src/` — если найдены, обновить.
- `state.lastLoadedAt = {}` — новое поле, нет migration.
- Vuex store getters/mutations: имена сохраняем, добавляем
  `getLastLoadedAt` и `setLastLoadedAt`.

## Open Questions

**Нет открытых вопросов.** Все 5 вопросов из Clarifications спеки
разрешены. Phase 0/1 complete.

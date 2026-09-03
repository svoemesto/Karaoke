# Data Model: Корректная пагинация таблиц после применения фильтра

**Дата**: 2026-09-03
**Связанная спека**: [spec.md](spec.md)
**Связанное research**: [research.md](research.md)

Этот документ описывает **client-side state** и **backend response shapes**, относящиеся к пагинации и фильтрации в admin SPA `webvue3`. Изменений в БД нет; фикс целиком на клиенте.

## Entities

### 1. PaginationState (client-side, per-table)

Состояние пагинации, хранимое в Vuex-модуле каждой таблицы (`webvue3/src/components/<Entity>/store.js`).

| Поле | Тип | Источник | Описание |
|------|-----|----------|----------|
| `<entity>TableCurrentPage` | `number` (>=1) | UI / watcher | Текущая страница (1-based). Источник: `<b-pagination v-model="currentPage">` или computed из store getter. |
| `<entity>TablePerPage` | `number` | hardcoded | Размер страницы (hardcoded в `<Entity>Table.vue`, обычно 30). |
| `digests` | `Array` | backend response | Записи текущей страницы (длина ≤ perPage). |
| `digestsIsLoading` | `boolean` | store mutation | Флаг загрузки (для `<b-table :busy>`). |

**Пример для Authors** (`webvue3/src/components/Authors/store.js:9-20`):

```javascript
state: {
  authorsDigest: [],          // digests
  authorsDigestIsLoading: false,
  authorsTableCurrentPage: 1, // currentPage
  authorsWithNewAlbumCount: 0,
}
```

### 2. FilterParams (request payload)

Объект параметров фильтра, передаваемый в `load<Entities>Digests(ctx, params)`.

| Поле | Тип | Описание |
|------|-----|----------|
| `filterAuthor` | `string` | Поиск по имени автора (Authors) |
| `filterSkip` | `'true'/'false'` | Чекбокс «пропущенные» (Authors) |
| `filterHaveNewAlbum` | `'true'/'false'` | Чекбокс «есть новые альбомы» (Authors — пример из бага) |
| `target` | `'local'/'remote'` | Серверный target (только для News, SiteUsers) |
| `*` | `string` | Любые другие `filter*` параметры, специфичные для таблицы |

**Важно**: фильтр-параметры определяют **новую выборку** на backend; результат может содержать меньше записей, чем предыдущая выборка.

### 3. DigestResponse (backend response)

Объект, возвращаемый backend-эндпоинтом `/api/<entity>/<digests>`.

| Поле | Тип | Наличие | Описание |
|------|-----|---------|----------|
| `<entity>Digests` | `Array` | ✅ во всех | Массив записей текущей страницы |
| `workInContainer` | `boolean` | ✅ Authors | Флаг режима контейнера |
| `news` | `Array` | ✅ News | (News использует другое имя поля) |
| `total` | `number` | ✅ только News | Общее число записей выборки (нет в Authors/Albums/Pictures/SiteUsers) |

**Импликация**: поскольку `total` отсутствует в большинстве ответов, `<b-pagination :total-rows="countRows">` использует `digests.length` как surrogate. Это и есть причина визуального артефакта «Page 1 of 1» после фильтрации.

### 4. ViewComputed (computed в `<Entity>Table.vue`)

| Computed | Формула | Где используется |
|----------|---------|------------------|
| `currentPage` | `this.$store.getters.get<Entity>TableCurrentPage \|\| 1` (data) | `<b-pagination v-model>` |
| `countRows` | `this.<digests> ? this.<digests>.length : 0` | `<b-pagination :total-rows>` |
| `perPage` | hardcoded (30 или 50) | `<b-pagination :per-page>` |

**Ключевое наблюдение**: `countRows = digests.length` — это **длина ТЕКУЩЕЙ страницы**, не общее число. После фильтрации массив уменьшается до ≤ perPage, и `total-rows` для `<b-pagination>` = этому уменьшенному массиву.

## Relationships

```
┌─────────────────────┐                ┌─────────────────────┐
│  <Entity>FilterModal│                │  <Entity>Table.vue  │
│  (ввод параметров)  │                │  (UI: пагинация +   │
└──────────┬──────────┘                │   таблица)          │
           │                           └─────────┬───────────┘
           │ params                              │ v-model / :items
           ▼                                     ▼
┌─────────────────────────────────────────────────────────┐
│     Vuex store: <Entity>/store.js                       │
│     - state: <entity>Digest[], CurrentPage              │
│     - action: load<Entity>Digests(ctx, params)          │
│     - mutation: set<Entity>TableCurrentPage             │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼ HTTP POST
┌─────────────────────────────────────────────────────────┐
│  Backend: POST /api/<entity>/<digests>                  │
│  Response: { <entity>Digests: [...], workInContainer }  │
│  (no total field — см. спеку News для контр-примера)    │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼ (response → state mutation)
┌─────────────────────────────────────────────────────────┐
│  Watcher на countRows (НОВЫЙ, в <Entity>Table.vue):     │
│  if (currentPage > totalPages) currentPage = 1          │
└─────────────────────────────────────────────────────────┘
```

## State transitions

### Пре-фикс (текущее, с багом):

```
state = { digests: [...30 записей...], currentPage: 3 }
        ──► user applies filter ──►
loadDigests(params) ──► backend returns {digests: [...5 записей...]}
state = { digests: [...5 записей...], currentPage: 3 }   ← BUG: currentPage не сброшен!
UI: <b-pagination :total-rows="5"> → "Page 1 of 1", но currentPage=3, b-table рендерит пустоту
```

### Пост-фикс (с watcher):

```
state = { digests: [...30 записей...], currentPage: 3 }
        ──► user applies filter ──►
loadDigests(params) ──► backend returns {digests: [...5 записей...]}
commit('setDigests', result)  → digests.length = 5
                            ▼ watcher на countRows срабатывает
totalPages = Math.ceil(5 / 30) = 1
if (3 > 1) currentPage = 1   ← FIX
UI: <b-pagination :total-rows="5" :current-page="1"> → "Page 1 of 1", b-table рендерит 5 записей ✅
```

## Validation rules

- `currentPage >= 1` всегда (UI не позволяет <1).
- `currentPage <= ceil(digests.length / perPage)` после каждой загрузки (enforced новым watcher).
- `digests.length <= perPage` (enforced backend'ом через `LIMIT perPage OFFSET (currentPage-1)*perPage`).

## Storage

- **Client state**: Vuex (in-memory + localStorage через Vuex-persistedstate, если настроено в проекте). См. `webvue3/src/store/index.js`.
- **Backend response**: не персистируется; приходит по HTTP.
- **БД**: не задействована в этом фиксе.

## Edge cases (data-model specific)

- `digests = []` (пустой массив после фильтра): `countRows = 0`, `totalPages = 1`, watcher сбрасывает currentPage в 1, UI показывает empty-state (b-table с `:items=[]`).
- `digests.length === perPage` (страница заполнена): `totalPages = 1`, currentPage сбрасывается в 1, если был >1. Это может приводить к «сбросу» при фильтрации, который сужает выборку до ровно одного экрана — это **желаемое поведение** (см. FR-006).
- `digests.length > perPage` (невозможно, т.к. backend ограничивает LIMIT) — но если произойдёт: `totalPages = ceil(N/perPage) > 1`, currentPage не сбрасывается (если ≤ totalPages).
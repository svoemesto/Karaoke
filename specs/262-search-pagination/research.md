# Phase 0 — Research: 262-search-pagination

## 1. Поддерживает ли существующий `Song.loadListFromDb` параметры `limit` / `offset`?

**Decision**: ДА — параметры уже поддерживаются через `args["limit"]` и `args["offset"]`. Никаких изменений в `Song.loadListFromDb` не требуется.

**Rationale**: В файле
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7650-7657`:

```kotlin
val limit = args["limit"]?.toInt() ?: 0
val offset = args["offset"]?.toInt() ?: 0
...
sql += " ORDER BY $TABLE_NAME.id"
if (limit > 0) sql += " LIMIT $limit"
if (offset > 0) sql += " OFFSET $offset"
```

Сортировка по `id ASC` — стабильна (primary key), удовлетворяет FR-004 спеки
(«стабильная сортировка»).

**Alternatives considered**:
- (A) Keyset-пагинация по `Song.id > lastSeenId`: быстрее на больших
  OFFSET, но требует cursor-state на фронте (несовместимо с текущим
  паттерном URL `?page=N`). Отклонено — лишний дифф, не требуется
  при разумном pageSize (35) и типичных запросах (≤5000 результатов).
- (B) Cursor-based без URL-state: пользователь не сможет поделиться
  ссылкой на конкретную позицию. Отклонено — противоречит FR-012 спеки.

## 2. Есть ли pattern для `COUNT(*)` в проекте?

**Decision**: ДА — pattern `Author.countWithNewAlbum`
(`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt:384-401`):

```kotlin
fun countWithNewAlbum(database: KaraokeConnection): Int {
    val connection = database.getConnection() ?: return 0
    val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME WHERE watched = true ..."
    return try {
        connection.prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
        }
    } catch (e: SQLException) {
        println("[...] SQLException: ${e.message}")
        0
    }
}
```

**Применяем тот же pattern** для нового companion-метода
`Song.countMatchingAttr(attr, database, ...)`: тот же `KaraokeConnection.getConnection()`,
тот же `try/catch`, та же защита `?: return 0`. Использует существующий
helper `getWhereList(tableName, args, sync = false)` для построения WHERE —
**никакого дублирования SQL-фильтров**.

**Alternatives considered**:
- (A) `SELECT count(*) OVER ()` как window в основном запросе — экономит
  один round-trip, но PostgreSQL `count(*) OVER ()` всё равно сканирует
  весь результат до OFFSET, и `connection.prepareStatement` уже
  отрабатывает за <500ms на 18k записях с фильтром. Отклонено —
  лишний дифф, выигрыш минимален.
- (B) Hibernate `CriteriaBuilder.count()` — ЗАПРЕЩЁН Constitution II
  (сырой JDBC без ORM).

## 3. Поддерживает ли Vuex-store паттерн `requestId` для race-conditions?

**Decision**: ДА — pattern уже есть в `search` action
(`karaoke-public/src/store/modules/songs.js:50-55`):

```javascript
let latestSearchId = 0
async search({ commit }, params) {
  const requestId = ++latestSearchId
  this.$api.apiGet('/api/public/songs', params).then(results => {
    if (requestId === latestSearchId) commit('setSearchResults', results)
  })
}
```

**Применяем тот же pattern** для нового action `loadMoreSearchResults`:
- Свой счётчик `latestLoadMoreId` (не пересекается с `search`).
- При успехе — проверка `requestId === latestLoadMoreId` перед `appendSearchResults`.
- Параллельные `loadMore` НЕ отменяются (race только при смене фильтров).
- При смене фильтров (FR-017 спеки) `latestSearchId` инкрементируется,
  что само по себе инвалидирует предыдущие результаты.

**Alternatives considered**:
- (A) AbortController на axios — чище, но в проекте используется
  обёртка `this.$api.apiGet(...)` без явной поддержки AbortController.
  Введение AbortController потребовало бы менять обёртку → лишний
  дифф в shared-коде. Отклонено для этого PR; можно добавить позже.
- (B) RxJS / Observable-store — ЗАПРЕЩЁН, проект на Vuex (Composition
  API не введён), новые зависимости не добавляются (FR-015 спеки).

## 4. Есть ли в проекте готовый компонент «Load more» / пагинация?

**Decision**: НЕТ — реализуется inline в `SearchView.vue` (минимальный дифф).

**Существующие примеры для референса**:
- `webvue3/src/components/News/NewsTable.vue` — классическая пагинация
  (см. `livedocs/features/093-news-pagination-top-35.md`): используется
  в **админке**, не переносится в публичный SPA без веской причины.
- Никаких готовых компонентов в `karaoke-public/src/components/`
  для infinite scroll не обнаружено.

**Реализация**:
- Кнопка `<button class="km-load-more-btn" @click="onLoadMore">Загрузить ещё</button>`
  в нижней части `<div class="km-song-list">` в `SearchView.vue`.
- Состояние `isLoadingMore` блокирует повторные клики через `disabled`.
- Опционально `@scroll` listener на `<div class="km-search-results">`
  для auto-load при scroll-near-bottom (FR-014 → Implementation Notes).

**Alternatives considered**:
- (A) npm-пакет `vue-infinite-loading` — новая зависимость, ЗАПРЕЩЕНА
  (FR-015 спеки). Отклонено.
- (B) IntersectionObserver API (vanilla) — нативный, без зависимостей.
  Применяется для auto-load на scroll-near-bottom; не для основной
  кнопки (она должна быть явным триггером).

## 5. Подход к синхронизации URL ↔ Vuex-state

**Decision**: Прямой binding через Vue Router 4 `$route.query`
(стандарт для проекта; никаких новых утилит).

**Паттерн** (в `SearchView.vue`):

```javascript
computed: {
  pageFromUrl() { return Number(this.$route.query.page) || 1 },
  pageSizeFromUrl() { return Number(this.$route.query.pageSize) || 35 },
},
watch: {
  pageFromUrl(newVal) {
    if (newVal > 1) this.loadPage(newVal)  // восстановление F5
  },
},
methods: {
  updateUrl({ page, pageSize }) {
    this.$router.replace({ query: { ...this.$route.query, page, pageSize } })
  }
}
```

**Альтернативы**:
- (A) `vue-router-sync` / отдельный helper — проект не использует;
  введение = новый шаблон для одного use-case. Отклонено.
- (B) Hash-based routing (`#/search?page=2`) — несовместимо с текущей
  конфигурацией Vue Router (HTML5 history mode в проде). Отклонено.

## 6. Sort order — гарантированно ли стабилен `Song.id ASC`?

**Decision**: ДА — `id` — это SERIAL PRIMARY KEY в `tbl_songs`, добавление
в БД монотонно; `ORDER BY id ASC` стабилен по определению.

**Подтверждение в коде**:
- `Song.loadListFromDb` всегда добавляет `ORDER BY tbl_songs.id` (см. `Song.kt:7655`).
- Существующее поведение `/api/public/songs` уже возвращает данные
  в порядке `id ASC` — поведение не меняется для пользователя,
  кроме факта чанкования.

**Edge case**: при миграциях / restore из бэкапа `id` может быть переназначен,
но это **не** меняет порядок в пределах одного запроса
(SELECT по одному DB-инстансу даёт согласованный результат). Безопасно.

## 7. Сводка решений

| # | Решение | Обоснование |
|---|---|---|
| 1 | `args["limit"]`/`args["offset"]` в `Song.loadListFromDb` | уже есть в коде |
| 2 | Companion `Song.countMatchingAttr(...)` по pattern `Author.countWithNewAlbum` | Constitution II (сырой JDBC), паттерн проекта |
| 3 | Race-condition через `latestLoadMoreId` (расширение `latestSearchId`) | уже есть в проекте |
| 4 | Inline-кнопка «Загрузить ещё» в `SearchView.vue` | минимальный дифф, нет готовых компонентов |
| 5 | URL-sync через `$route.query` | стандарт Vue Router 4 |
| 6 | Сортировка `Song.id ASC` (без изменений) | уже в `Song.loadListFromDb`, стабильна по PK |

Никаких новых зависимостей, никаких миграций, никаких изменений в
других контроллерах/DTO. Готовность к Phase 1.
# Phase 1 — Data Model: 262-search-pagination

## Что меняется

1. **Новый DTO** `PagedSongsDto` (бэкенд) — обёртка ответа `/api/public/songs`
   при пагинированном запросе. Никаких изменений в существующих DTO
   (`SongPublicDto`, `ZakromaPublicDto` и пр.) — этот DTO **надстраивается**
   поверх них.
3. **Расширение** Vuex-модуля `songs` — новый slice `searchPagination`
   с состоянием пагинации + новая mutation `appendSearchResults` + новый
   action `loadMoreSearchResults`.
2. **Новый helper** `Song.countMatchingAttr(...)` (companion object в
   `karaoke-app/.../Song.kt`) — подсчёт `totalCount` с тем же фильтром,
   что и `items`.

Никаких новых сущностей, никаких миграций БД, никаких изменений в
существующих таблицах.

## 1. Сущности (UI/фронт)

### SearchResult (UI-элемент массива) — без изменений

Источник: `state.songs.searchResults` в Vuex-сторе
`karaoke-public/src/store/modules/songs.js:16`. Заполняется ответом
`GET /api/public/songs?...`.

**Контракт каждого элемента** не меняется — все поля из спеки 261
(включая `contentReady`, `albumPictureUrl`, `authorPictureUrl`) остаются.
Изменение только в **механизме загрузки массива** (чанки) и в
**состоянии пагинации** (см. ниже).

### searchPagination (новый slice в Vuex-store)

Источник: `state.songs.searchPagination` в
`karaoke-public/src/store/modules/songs.js`. Инициализируется через
`setSearchPagination(...)` mutation (новая) или обновляется в `search` /
`loadMoreSearchResults` actions.

| Поле | Тип | Семантика | Источник / поведение |
|---|---|---|---|
| `page` | `number` | Текущая (последняя загруженная) страница, 1-based, default 1 | Echo из ответа бэка после нормализации |
| `pageSize` | `number` | Размер порции, default 35, допустимые 10/25/35/50/100 | Echo из ответа бэка |
| `totalCount` | `number` | Общее число песен по текущему фильтру | Echo из ответа бэка (`PagedSongsDto.totalCount`) |
| `hasMore` | `boolean` | Есть ли ещё страницы | Echo из ответа бэка |
| `isLoadingMore` | `boolean` | Защита от rapid-click на «Загрузить ещё»; true во время in-flight запроса | Локальное состояние, сбрасывается в `finally` блоке |

**Mutations** (новые в `store/modules/songs.js`):
- `setSearchPagination(state, pagination)` — устанавливает
  `state.searchPagination` целиком (для простоты).
- `appendSearchResults(state, newItems)` — дописывает `newItems` к
  `state.searchResults` (для `page > 1`); для `page === 1` —
  перезаписывает.
- `setLoadingMore(state, value)` — `state.searchPagination.isLoadingMore = value`.

**Action** (новый):
- `loadMoreSearchResults({ commit, state, rootState })`:
  1. Если `!state.searchPagination.hasMore` — no-op.
  2. Если `state.searchPagination.isLoadingMore` — no-op (rapid-click).
  3. `commit('setLoadingMore', true)`.
  4. `requestId = ++latestLoadMoreId`.
  6. Вызов `this.$api.apiGet('/api/public/songs', { ...currentFilters, page: state.searchPagination.page + 1, pageSize: state.searchPagination.pageSize })`.
  7. По успеху: если `requestId === latestLoadMoreId` —
     `commit('appendSearchResults', results.items)` +
     `commit('setSearchPagination', { ...state.searchPagination, ...results })` +
     `commit('updateUrlPage', results.page)` (новая mutation для URL).
  8. В `finally`: `commit('setLoadingMore', false)`.

### URL search params — расширение

Источник: `$route.query` в `SearchView.vue`. Существующие параметры
`songName`, `author`, `text`, `album` остаются (спека 261). Добавляются:

| Параметр | Тип | Default | Семантика |
|---|---|---|---|
| `page` | `number` | `1` | Последняя загруженная порция. При F5 — автоподгрузка всех страниц от 1 до `page` (см. FR-012 спеки) |
| `pageSize` | `number` | `35` | Размер порции (10/25/35/50/100) |

## 2. Сущности (бэк)

### PagedSongsDto (новый)

Расположение: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/PagedSongsDto.kt`.

| Поле | Тип | Default | JSON-ключ | Источник |
|---|---|---|---|---|
| `items` | `List<SongPublicDto>` | `emptyList()` | `items` | `Song.loadListFromDb(attr + limit + offset, ...)` (см. `Song.kt:7650-7657`) |
| `totalCount` | `Long` | `0` | `totalCount` | `Song.countMatchingAttr(attr, ...)` (новый companion method) |
| `page` | `Int` | `1` | `page` | Echo из query-параметра после нормализации |
| `pageSize` | `Int` | `35` | `pageSize` | Echo из query-параметра после нормализации |
| `hasMore` | `Boolean` | `false` | `hasMore` | `page * pageSize < totalCount` |

**Nullability / Defaults**:
- `items` — никогда `null`; пустой массив `[]` если нет совпадений.
- `totalCount` — `0` если нет соединения или SQL вернул NULL (защита
  как в `Author.countWithNewAlbum`).
- `page` / `pageSize` / `hasMore` — вычисляемые echo, всегда non-null.

**Правила валидации** на стороне контроллера (нормализация):
- `page < 1` → `1`.
- `pageSize` не из списка `[10, 25, 35, 50, 100]` → `35` (или 400 — Implementation Notes).
- `pageSize <= 0` → `35`.

**State transitions**: нет (поисковый ответ — snapshot).

**Связи**:
- `items[i] ← SongPublicDto` — `1:N` без изменений.
- `items` ↔ `totalCount` — оба из одного фильтра; `hasMore` —
  вычисляемое от них.

### SongPublicDto (без изменений)

Расположение: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`.

Никаких изменений в полях / Jackson-аннотациях / `fromSong(...)`.
Спека 261 зафиксировала этот DTO; 262 не трогает его.

### Song (без изменений на уровне сущности; новый companion helper)

Расположение: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.

**Новый companion method** (добавляется в `object Song`):

```kotlin
/**
 * Подсчёт числа песен, удовлетворяющих фильтру attr.
 * Используется в PublicApiController.songs(...) для totalCount в PagedSongsDto.
 *
 * @see specs/262-search-pagination/contracts/api-songs.md
 */
fun countMatchingAttr(
    args: Map<String, String> = emptyMap(),
    database: KaraokeConnection,
    sync: Boolean = false,
): Int {
    val connection = database.getConnection() ?: return 0
    val where = getWhereList(tableName = TABLE_NAME, args = args, sync = sync)
    val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME${if (sync) "_sync" else ""}" +
              if (where.isNotEmpty()) " WHERE ${where.joinToString(" AND ")}" else ""
    return try {
        connection.prepareStatement(sql).use { ps ->
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
        }
    } catch (e: SQLException) {
        println("[${Timestamp.from(Instant.now())}] Song.countMatchingAttr SQLException: ${e.message}")
        0
    }
}
```

Паттерн взят из `Author.countWithNewAlbum`
(`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt:384`):
тот же `KaraokeConnection.getConnection()`, тот же `try/catch`,
та же защита `?: return 0`. Использует существующий helper `getWhereList(...)` —
**никакого дублирования SQL-фильтров**.

## 3. End-to-end data flow

```text
1. User opens /search?songName=X&page=2
2. SearchView.vue computed pageFromUrl = 2 → loadPage(2)
3. SearchView.vue → store.dispatch('search', { songName: 'X' })  // page=1 first
4. store action search: apiGet('/api/public/songs?songName=X&page=1&pageSize=35')
5. PublicApiController.songs(...):
   - attr = { song_name: 'X', ... }
   - LIMIT 35 OFFSET 0 → items (Song.loadListFromDb)
   - COUNT(*) → totalCount (Song.countMatchingAttr)
   - page=1, pageSize=35, hasMore=true → PagedSongsDto
6. Vuex: setSearchResults(items) + setSearchPagination({page:1, hasMore:true, totalCount:N})
7. SearchView.vue render list + "Загрузить ещё" button (visible since hasMore=true)
8. User clicks "Загрузить ещё" → store.dispatch('loadMoreSearchResults')
9. apiGet('/api/public/songs?songName=X&page=2&pageSize=35')
10. ... loop ...
11. URL updated via $router.replace({ query: { page: N }})
```

## 4. Edge cases (data-model perspective)

- **`totalCount == 0`**: `items=[]`, `hasMore=false`, UI показывает
  «Ничего не найдено». Кнопка «Загрузить ещё» не отображается.
- **`totalCount == pageSize`** (ровно одна страница): `hasMore=false`.
- **`page > ceil(totalCount/pageSize)`** (например, `page=999` для 50
  результатов): бэк возвращает `items=[]`, `totalCount=50`, `hasMore=false`.
  Фронт показывает «Страница не найдена» (см. Story 4 спеки).
- **Невалидный `pageSize`**: нормализуется к `35` на бэке (FR-001 спеки).
- **Невалидный `page`** (`page=0` / `page=-1`): нормализуется к `1`.

## 5. Связь с другими спеками

- **261-search-results-ui**: `SongPublicDto` остаётся как есть (поля
  `contentReady`, `albumPictureUrl`, `authorPictureUrl` — без изменений).
  Эта спека только **надстраивает** обёртку `PagedSongsDto` поверх.
- **013-song-status-filter**: фильтр `id_status >= 6` для публичной
  поверхности уже в `PublicApiController.songs` (см. `PublicApiController.kt:683`)
  — `Song.countMatchingAttr` использует тот же фильтр через `attr["id_status"]`.
- **015-search-engine-selection**: резолв автора через `Author.resolveByTerm`
  + `attr["author_in"]` — уже в `PublicApiController.songs`; `Song.countMatchingAttr`
  получает тот же `attr` и применяет тот же WHERE (через `getWhereList`).
- **090-news-pagination**, **093-news-pagination-top-35**: единый стандарт
  pageSize=35 — настоящая спека следует ему.
- **259-playlist-clickable-links**: URL-routing для ссылок на `/song` и
  `/zakroma` — настоящая спека НЕ меняет эти ссылки, только добавляет
  query-параметры `page`/`pageSize`.
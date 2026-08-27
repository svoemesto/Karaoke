# Data Model: Закрома — header-back-link из SongView + рефакторинг URL-routing

**Feature**: 258 — `specs/258-zakroma-routing-refactor`
**Date**: 2026-08-27

Phase 1 — модель данных для новых URL-маршрутов и изменений в DTO.

## Затронутые сущности

### 1. `AuthorTilePublicDto` (изменяется — добавляется поле)

**Где**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt`

**Текущая структура**:
```kotlin
data class AuthorTilePublicDto(
    val author: String,              // имя автора
    val authorPictureUrl: String,    // URL превью картинки
    val songCount: Long,             // кол-во песен
    @get:JsonProperty("isSpecialOrder")
    val isSpecialOrder: Boolean = false,
)
```

**Изменение**: добавляется `val id: Long` (BigInt из `tbl_authors.id`).

**Новая структура**:
```kotlin
data class AuthorTilePublicDto(
    val id: Long,                    // PK из tbl_authors.id (NEW)
    val author: String,              // имя автора
    val authorPictureUrl: String,    // URL превью картинки
    val songCount: Long,             // кол-во песен
    @get:JsonProperty("isSpecialOrder")
    val isSpecialOrder: Boolean = false,
)
```

**Обновление `fromAuthorName`**:
```kotlin
fun fromAuthorName(
    id: Long,                        // NEW
    author: String,
    songCount: Long,
    isSpecialOrder: Boolean = false,
): AuthorTilePublicDto {
    // ... existing logic ...
    return AuthorTilePublicDto(
        id = id,                     // NEW
        author = author,
        authorPictureUrl = "/minio/$BUCKET/$encoded",
        songCount = songCount,
        isSpecialOrder = isSpecialOrder,
    )
}
```

**JSON до изменения**:
```json
{
  "author": "Машина Времени",
  "authorPictureUrl": "/minio/karaoke/...",
  "songCount": 234,
  "isSpecialOrder": false
}
```

**JSON после изменения** (добавляется поле `id` в начало):
```json
{
  "id": 42,
  "author": "Машина Времени",
  "authorPictureUrl": "/minio/karaoke/...",
  "songCount": 234,
  "isSpecialOrder": false
}
```

**Обратная совместимость**: фронт, который не использует `id`, продолжает работать (новое поле игнорируется). Никаких breaking changes.

---

### 2. `PublicApiController.authorsTiles` (изменяется — добавляется запрос ID)

**Где**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:250-301`

**Текущая логика**:
```kotlin
@GetMapping("/authors-tiles")
fun authorsTiles(scope: String?, ...): List<AuthorTilePublicDto> {
    val counts = Song.loadAuthorSongCounts(...)
    val loadedAuthors: List<String> = Song.loadListAuthors(...).filter { counts[it] ?: 0L > 0L }
    // ...
    return loadedAuthors.map {
        AuthorTilePublicDto.fromAuthorName(
            author = it,
            songCount = counts[it] ?: 0L,
            isSpecialOrder = ...,
        )
    }
}
```

**Изменение**: добавить загрузку `Map<authorName, id>` через новый helper `Author.loadIdsByNames(names)`.

**Новая логика**:
```kotlin
@GetMapping("/authors-tiles")
fun authorsTiles(scope: String?, ...): List<AuthorTilePublicDto> {
    val counts = Song.loadAuthorSongCounts(...)
    val loadedAuthors: List<String> = Song.loadListAuthors(...).filter { counts[it] ?: 0L > 0L }

    val authorIdsByName: Map<String, Long> = Author.loadIdsByNames(loadedAuthors)  // NEW

    // ...
    return loadedAuthors.map { authorName ->
        AuthorTilePublicDto.fromAuthorName(
            id = authorIdsByName[authorName] ?: 0L,  // NEW; fallback 0 нежелателен, см. ниже
            author = authorName,
            songCount = counts[authorName] ?: 0L,
            isSpecialOrder = ...,
        )
    }
}
```

**Validation rule** (NFR из спеки): если для какого-то автора `id == 0` (не нашли в БД), нужно либо log warning, либо пропустить автора. Решение — логировать warning и пропускать, чтобы не отдавать `id=0` во фронт (мог бы вызвать `404` на `/zakroma/0`).

---

### 3. `Author.loadIdsByNames` (новый helper)

**Где**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`

**Сигнатура**:
```kotlin
companion object {
    /**
     * Загружает `Map<authorName, id>` для списка имён одним raw-SELECT'ом.
     * Используется для резолвинга ID в `/api/public/authors-tiles`.
     *
     * @param names список имён авторов
     * @param database подключение к БД
     * @return `Map<author, id>` — только для найденных; авторы без записи в БД пропускаются
     */
    fun loadIdsByNames(
        names: List<String>,
        database: KaraokeConnection,
    ): Map<String, Long>
}
```

**Реализация (raw SQL, без `loadList` overhead)**:
```kotlin
fun loadIdsByNames(
    names: List<String>,
    database: KaraokeConnection,
): Map<String, Long> {
    if (names.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, Long>()
    val connection = database.getConnection() ?: return result
    // Chunking по 100 имён — иначе SQL слишком длинный при 1000+ авторов
    names.chunked(100).forEach { chunk ->
        val placeholders = chunk.joinToString(",") { "?" }
        val sql = "SELECT id, author FROM $TABLE_NAME WHERE author IN ($placeholders)"
        try {
            connection.prepareStatement(sql).use { ps ->
                chunk.forEachIndexed { i, name -> ps.setString(i + 1, name) }
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong("id")
                        val author = rs.getString("author") ?: continue
                        result[author] = id
                    }
                }
            }
        } catch (e: SQLException) {
            println("[${Timestamp.from(Instant.now())}] Author.loadIdsByNames SQLException: ${e.message}")
        }
    }
    return result
}
```

**Performance**:
- При 1000+ авторов — 10 chunked запросов вместо одного гигантского.
- Использует существующий индекс `tbl_authors(author)` (если есть) или PK scan.
- Без `loadList` overhead — нет создания объектов `Author`, нет storage вызовов.

---

### 4. URL-маршруты (frontend, `karaoke-public/src/router/index.js`)

**Текущая структура**:
```js
const routes = [
  // ...
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },
  // ...
]
```

**Новая структура**:
```js
const routes = [
  // ...
  // Тайтлы авторов (только)
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },
  // Песни конкретного автора (ID — число)
  { path: '/zakroma/:authorId(\\d+)', name: 'zakroma-author', component: ZakromaView },
  // Спец-корзина (отдельный route)
  { path: '/zakroma/special-bucket', name: 'zakroma-special-bucket', component: ZakromaView },
  // ...
]
```

**Validation rules**:
- `:authorId` — regex `\\d+`, любая другая строка → 404 (Vue-router No match).
- `/zakroma/special-bucket` — точное совпадение. `/zakroma/special-bucket/anything` → 404.

**Legacy redirect** (через global guard, см. research.md RT-3):
- `/zakroma?author=X` → `/zakroma/:authorId` (резолвится через `state.zakroma.authorTiles`)
- `/zakroma?specialBucket=true` → `/zakroma/special-bucket`

---

### 5. `ZakromaView.vue` data (изменяется)

**Где**: `karaoke-public/src/views/ZakromaView.vue:402-428`

**Текущая data**:
```js
data() {
  return {
    selectedAuthor: this.$route.query.author || '',
    authorChosen: !!this.$route.query.author,
    specialBucketShown: this.$route.query.specialBucket === 'true',
    // ...
    songFilter: '',
    // ...
  }
}
```

**Новая data**:
```js
data() {
  // Определяем режим по $route.path (однозначно после рефакторинга)
  const isSpecialBucketRoute = this.$route.path === '/zakroma/special-bucket'
  const authorIdParam = this.$route.params.authorId
  const hasAuthor = !!authorIdParam && /^\d+$/.test(authorIdParam)

  return {
    // authorId из path (или '' если тайты/спец)
    selectedAuthorId: hasAuthor ? authorIdParam : '',
    // Имя автора — резолвится из authorTiles в mounted()
    selectedAuthor: '',
    authorChosen: hasAuthor,
    specialBucketShown: isSpecialBucketRoute,
    // ...
    songFilter: '',
    // ...
  }
}
```

**Изменения в `mounted()`**:
```js
mounted() {
  // FR-A4: watcher из спеки 255 УДАЛЁН — vue-router пересоздаёт компонент
  //         при смене path, state инициализируется заново через data()
  
  // Тайлы загружаются всегда (для сетки И для резолвинга имя→ID в beforeEach guard)
  this.loadAuthorTiles('main')
  this.loadSpecialBucket()

  // Резолвим имя автора по ID (если мы на /zakroma/:authorId)
  if (this.authorChosen && this.selectedAuthorId) {
    const tile = this.authorTiles.find(t => String(t.id) === String(this.selectedAuthorId))
    if (tile) {
      this.selectedAuthor = tile.author
    }
    // Запуск стрима для песен автора
    this.loadZakromaStream({
      author: this.selectedAuthor || '',
      expectedCount: tile?.songCount || undefined,
    })
  }
}
```

**Изменения в `<AppHeader :back="...">`**:
```js
computed: {
  // ... существующие ...
  zakromaHeaderBack() {
    // На странице тайтлов ИЛИ спец-корзины — back-link в шапке скрыт
    if (!this.authorChosen && !this.specialBucketShown) {
      return null
    }
    // На странице песен автора — back ведёт на тайты
    if (this.authorChosen) {
      return { to: '/zakroma', label: '← К списку авторов' }
    }
    // На странице спец-корзины — back ведёт на тайты
    if (this.specialBucketShown) {
      return { to: '/zakroma', label: '← К списку авторов' }
    }
    return null
  },
}
```

---

### 6. `SongView.vue` (изменяется — динамический back-link)

**Где**: `karaoke-public/src/views/SongView.vue:4`

**Текущий код**:
```vue
<AppHeader :back="{ to: '/zakroma', label: '← Назад' }" />
```

**Новый код**:
```js
// В computed добавляется:
songHeaderBack() {
  const authorId = this.$route.query.authorId
  if (authorId && /^\d+$/.test(authorId)) {
    return {
      name: 'zakroma-author',
      params: { authorId },
      label: '← К песням автора',
    }
  }
  return { to: '/zakroma', label: '← В Закрома' }
}
```

```vue
<!-- В template -->
<AppHeader :back="songHeaderBack" />
```

---

## State transitions

### URL → `ZakromaView` mode

| URL                              | `data.authorChosen` | `data.specialBucketShown` | `data.selectedAuthorId` | Рендер |
|----------------------------------|---------------------|----------------------------|-------------------------|--------|
| `/zakroma`                       | `false`             | `false`                    | `''`                    | тайтлы |
| `/zakroma/123`                   | `true`              | `false`                    | `'123'`                 | песни автора |
| `/zakroma/special-bucket`        | `false`             | `true`                     | `''`                    | спец-корзина |

### Routing flows

```
/zakroma → клик тайла → /zakroma/123 (vue-router создаёт новый инстанс ZakromaView)
                           ↓
                        data.authorChosen = true, selectedAuthorId = '123'
                        selectedAuthor резолвится в mounted() через authorTiles

/zakroma/123 → клик песни → /song?id=Y&authorId=123
                                 ↓
                              SongView, header-back-link использует authorId=123

/song?id=Y&authorId=123 → клик «← Назад» → /zakroma/123 (Vue-router name + params)
                                                       ↓
                                                    ZakromaView пересоздаётся, state OK
```

### Legacy redirects (через router.beforeEach)

```
/zakroma?author=Машина%20Времени
  ↓ router.beforeEach
  ↓ резолвинг Машина Времени → id=42 (через authorTiles)
  ↓ redirect replace
/zakroma/42

/zakroma?specialBucket=true
  ↓ router.beforeEach
  ↓ redirect replace
/zakroma/special-bucket

/legacy-url с невалидным author (нет в authorTiles)
  ↓ router.beforeEach
  ↓ fallback
/zakroma (тайтлы + notify "Автор не найден")
```

## Validation rules

| Правило | Где | Как проверяется |
|---------|-----|-----------------|
| `:authorId` — только цифры | router `(\\d+)` | Vue-router сам отклоняет → 404 |
| `?authorId` в SongView — только цифры | `songHeaderBack` computed | regex `/^\d+$/` → fallback на `/zakroma` |
| `selectedAuthorId` существует в БД | `mounted()` через `authorTiles.find` | если не найдено → `authorChosen = false` + notify |
| `authorTiles` не пустой при заходе на `/zakroma/:authorId` | `beforeEach` guard | если пустой → `await loadAuthorTiles` (dedup 30s) |
| `id=0` в `AuthorTilePublicDto` | backend `authorsTiles` | log warning + пропуск автора |

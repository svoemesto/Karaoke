# Contracts: 262-search-pagination

## Внешний контракт — публичный HTTP endpoint

### `GET /api/public/songs` (расширен)

**Назначение**: публичный эндпоинт поиска песен. Возвращает **либо**
массив `SongPublicDto[]` (старый формат, для обратной совместимости),
**либо** `PagedSongsDto` (новый формат, при пагинированном запросе).
Используется `SearchView.vue` через `state.songs.searchResults`.

**Контроллер**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`,
метод `songs(...)` (см. `PublicApiController.kt:650-732`).

### Запрос

`@RequestParam`, все опциональны:

| Параметр | Тип | Default | Семантика |
|---|---|---|---|
| `songName` | `String?` | `null` | Подстрока имени песни (`LOWER(song_name) LIKE`) — без изменений |
| `author` | `String?` | `null` | Автор; резолвится через `Author.resolveByTerm` — без изменений |
| `text` | `String?` | `null` | Подстрока текста (`LOWER(text) LIKE`) — без изменений |
| `album` | `String?` | `null` | Подстрока альбома (`LOWER(song_album) LIKE`) — без изменений |
| `anonId` | `String?` | `null` | Анонимный ID (event-tracking) — без изменений |
| `referrer` | `String?` | `null` | Реферер страницы (event-tracking) — без изменений |
| **`page`** | `Int?` | `1` | Номер страницы, 1-based. `<1` нормализуется к `1` |
| **`pageSize`** | `Int?` | `35` | Размер порции. Допустимые: `10`, `25`, `35`, `50`, `100`. Не из списка → `35` |

### Ответ

**Два варианта** в зависимости от наличия параметров пагинации:

#### Вариант A: Без `page`/`pageSize` (обратная совместимость)

Применяется, если **оба** параметра `page` и `pageSize` отсутствуют в запросе
(legacy-вызовы, если такие есть).

```
HTTP/1.1 200 OK
Content-Type: application/json

[
  { "id": 12345, "songName": "...", ... },
  ...
]
```

Это старый контракт, оставлен **as-is** (FR-003 спеки). Никаких изменений
в JSON-структуре элементов.

#### Вариант B: С `page` или `pageSize`

Применяется, если **хотя бы один** из параметров `page`/`pageSize`
присутствует в запросе (даже если равен дефолту).

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    { "id": 12345, "songName": "...", ... },
    { "id": 12346, "songName": "...", ... },
    ...
  ],
  "totalCount": 1234,
  "page": 1,
  "pageSize": 35,
  "hasMore": true
}
```

### Структура `PagedSongsDto` (новый DTO)

```kotlin
data class PagedSongsDto(
    val items: List<SongPublicDto> = emptyList(),
    val totalCount: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 35,
    val hasMore: Boolean = false,
)
```

| Поле | Тип | JSON-ключ | Default | Источник |
|---|---|---|---|---|
| `items` | `List<SongPublicDto>` | `items` | `[]` | `Song.loadListFromDb(attr + limit=pageSize + offset=(page-1)*pageSize, ...)` |
| `totalCount` | `Long` | `totalCount` | `0` | `Song.countMatchingAttr(attr, ...)` |
| `page` | `Int` | `page` | `1` | Echo после нормализации |
| `pageSize` | `Int` | `pageSize` | `35` | Echo после нормализации |
| `hasMore` | `Boolean` | `hasMore` | `false` | `(page * pageSize) < totalCount` |

**Nullability**: ни одно поле не бывает `null` (Jackson + Kotlin-default
даёт `0` / `[]` / `false` / `1` / `35` для null-входа). См.
`data-model.md#2-pagedsongsdto-новый`.

### Коды ответов

- `200 OK` — массив (Вариант A) или обёртка (Вариант B).
- (Никаких других кодов, изменений контракта ошибок нет.)

### Примеры

**Запрос с пагинацией (первая страница)**:
```
GET /api/public/songs?songName=Солнце&page=1&pageSize=35
→ 200 OK
{
  "items": [
    { "id": 12345, "songName": "Звезда по имени Солнце", "author": "Кино", ... },
    ...
    { "id": 12379, "songName": "Солнечный день", "author": "Кино", ... }
  ],
  "totalCount": 12,
  "page": 1,
  "pageSize": 35,
  "hasMore": false
}
```

**Запрос без пагинации (старый формат)**:
```
GET /api/public/songs?songName=Солнце
→ 200 OK
[
  { "id": 12345, ... },
  ...
]
```

**Запрос с пустым результатом**:
```
GET /api/public/songs?songName=абвгдеж
→ 200 OK
{
  "items": [],
  "totalCount": 0,
  "page": 1,
  "pageSize": 35,
  "hasMore": false
}
```

**Запрос с невалидным pageSize** (например, `pageSize=99`):
```
GET /api/public/songs?songName=Кино&pageSize=99
→ 200 OK
{
  "items": [...35 песен...],
  "totalCount": 250,
  "page": 1,
  "pageSize": 35,        ← нормализован к 35
  "hasMore": true
}
```

### Сортировка и стабильность

- Сортировка: `ORDER BY tbl_songs.id ASC` (см. `Song.loadListFromDb`,
  `Song.kt:7655`). Стабильна между страницами (PK).
- Гарантия SC-005: повторный вызов того же запроса с тем же `page`
  возвращает те же элементы в том же порядке.
- Гарантия SC-003: страницы `page=1` и `page=2` возвращают
  **непересекающиеся** элементы (по `id`).

## Потребители контракта

- **`karaoke-public/src/views/SearchView.vue`** (основной потребитель).
  Использует `result.items` для рендера строк, `result.totalCount`
  для счётчика «Показано X из Y», `result.hasMore` для видимости
  кнопки «Загрузить ещё».
- **Другие потребители**: на момент спеки — нет. Старый формат
  (Вариант A) сохранён для гипотетических legacy-вызовов.

## Внутренний контракт — `Song.countMatchingAttr`

### `Song.countMatchingAttr(args, database, sync)` (новый)

Companion-метод в `object Song` (`karaoke-app/.../Song.kt`).

**Сигнатура**:
```kotlin
fun countMatchingAttr(
    args: Map<String, String> = emptyMap(),
    database: KaraokeConnection,
    sync: Boolean = false,
): Int
```

**Семантика**: `SELECT COUNT(*) FROM tbl_songs WHERE <getWhereList(args)>`
(или `tbl_songs_sync` если `sync=true`). Использует **тот же** `attr`-фильтр,
что и `Song.loadListFromDb`, через общий helper `getWhereList(...)` — никакого
дублирования WHERE-логики.

**Возврат**: `Int` (число строк) или `0` при:
- `database.getConnection() == null` (нет соединения);
- `SQLException` (catch + log, как в `Author.countWithNewAlbum`).

**Применение в контроллере**:
```kotlin
val attr: Map<String, String> = ... // формируется один раз
val items = Song.loadListFromDb(
    attr + ("limit" to pageSize.toString()) + ("offset" to ((page - 1) * pageSize).toString()),
    database = WORKING_DATABASE,
    ...
)
val totalCount = Song.countMatchingAttr(attr, database = WORKING_DATABASE)
```

## UI-контракт (DOM)

### Кнопка «Загрузить ещё»

| Класс | Назначение | Состояние |
|---|---|---|
| `.km-load-more` | Контейнер (центрирование + margin) | всегда |
| `.km-load-more-btn` | Кнопка `<button>` | `disabled` когда `isLoadingMore` или `!hasMore` |
| `.km-load-more-spinner` | Inline-спиннер во время загрузки | visible когда `isLoadingMore` |
| `.km-load-more-error` | Сообщение «Не удалось загрузить ещё» + retry | visible при ошибке |
| `.km-counter` | Счётчик «Показано X из Y» | visible когда `searchResults.length > 0` |

### Тексты и aria

| Элемент | Текст | ARIA |
|---|---|---|
| `.km-counter` | `«Показано ${shown} из ${total}»` | `aria-live="polite"` |
| `.km-load-more-btn` | `«Загрузить ещё»` | `aria-label="Загрузить следующие 35 результатов"` |
| `.km-load-more-error` | `«Не удалось загрузить ещё. Повторить?»` + retry-кнопка | `role="alert"` |

### Адаптивность

- Кнопка «Загрузить ещё» — полная ширина на мобильных вьюпортах
  (`width: 100%` при `@media (max-width: 480px)`).
- Счётчик «X из Y» — одна строк, обрезается overflow-ellipsis на узких экранах.

## Контракт событий/аналитики

- Клик по «Загрузить ещё» → `loadMoreSearchResults` action → `apiGet(...)`.
  Никаких **новых** событий в `EventTypes.kt` / `tbl_events` НЕ добавляется.
- Существующая аналитика поиска (`CALL_REST / FILTER` — см.
  `PublicApiController.kt:703-712`) пишется и для пагинированных запросов
  **с теми же параметрами** (`song_name`, `author`, `text`, `album`);
  параметры `page`/`pageSize` **НЕ** логируются в `tbl_events` (не нужны
  для аналитики, избыточный шум).
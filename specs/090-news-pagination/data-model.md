# Data Model: Пагинация ленты новостей

Структурных изменений в БД нет — `tbl_news` не меняется (ни новых колонок,
ни новых индексов; `publish_at`/`id` уже существуют и уже используются для
сортировки). Эта фича добавляет только **новые параметры выборки** поверх
существующей сущности `News`/`NewsDto`.

## Существующая сущность (без изменений структуры)

### News (karaoke-app/model/News.kt)

Без изменений полей. Меняются/добавляются только функции доступа (см. ниже).

### NewsDto (karaoke-app/model/NewsDto.kt)

Без изменений полей (`id`, `title`, `body`, `category`, `link`, `publishAt`,
`createdAt`, `published`, `source`).

## Новые/изменённые функции доступа к данным

### `News.loadPublished` (публичная сторона)

Было:
```kotlin
fun loadPublished(database: KaraokeConnection): List<NewsDto>
```

Станет:
```kotlin
fun loadPublished(database: KaraokeConnection, limit: Int, offset: Int): List<NewsDto>
fun countPublished(database: KaraokeConnection): Long
```

SQL получает `ORDER BY publish_at DESC, id DESC LIMIT ? OFFSET ?` (второй
ключ сортировки — см. research.md п.5). `countPublished` — отдельный лёгкий
`SELECT COUNT(*) FROM tbl_news WHERE publish_at IS NOT NULL AND publish_at <= now()`.

### `News.loadAll` (админка)

Было:
```kotlin
fun loadAll(database, storageService, storageApiClient): List<News>
// грузит ВСЕ строки через generic loadList, сортирует sortedByDescending{it.id} в памяти
```

Станет:
```kotlin
fun loadAll(database, storageService, storageApiClient, limit: Int, offset: Int): List<News>
fun countAll(database: KaraokeConnection): Long
```

`loadAll` переходит на прямой SQL (`ORDER BY id DESC LIMIT ? OFFSET ?`)
вместо generic `KaraokeDbTable.loadList` + сортировки в памяти — иначе
`LIMIT/OFFSET` пришлось бы применять уже после полной загрузки, что не решает
задачу. `countAll` — `SELECT COUNT(*) FROM tbl_news`.

`loadPublishedSince` (бейдж) — без изменений (не участвует в пагинации, см.
research.md п.6 и FR-009 spec.md).

## Контракт запрос/ответ (детали — см. contracts/news-api.md)

- Публичная сторона: `page`/`size` → `{ items, total, hasMore }`.
- Админка: `page`/`pageSize` (+ существующий `target`) → `{ news, total }`.

## State (только frontend, не БД)

### karaoke-public (NewsView.vue), локальный `data()`, без Vuex

- `news: NewsDto[]` — уже загруженные карточки (накопительно, паттерн
  «Показать ещё»).
- `page: number` — номер следующей порции для запроса (0-based).
- `total: number` — общее число новостей (для решения «есть ли ещё»).
- `loadingMore: boolean` — состояние кнопки «Показать ещё» во время запроса.

### webvue3 (News/store.js), Vuex state

- `newsList: NewsDto[]` — только текущая страница (не накопительно, в
  отличие от публичной стороны).
- `newsTotalCount: number` — для `<b-pagination :total-rows>`.
- `newsCurrentPage: number` — 1-based, как в остальных таблицах проекта
  (`songsTableCurrentPage`, `dictionariesTableCurrentPage`).
- `newsPerPage: number` — константа 50 (не персистится, не настраивается
  пользователем — как и в большинстве существующих таблиц проекта).

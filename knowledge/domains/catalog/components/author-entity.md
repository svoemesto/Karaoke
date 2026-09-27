# Component: Author (детальный)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Author` entity.


## Ответственность | Responsibility


детальное описание `Author` entity.

## Файл

`karaoke-app/.../model/Author.kt`

## Назначение

**Сущность «Автор» (исполнитель)** — отдельная таблица `tbl_authors`.
**Один автор — много песен** (для переиспользования).

## Интерфейсы и Контракты | Interfaces and Contracts

### Контракт класса

`class Author(database, storageService, storageApiClient) : Serializable,
Comparable<Author>, KaraokeDbTable` — `getTableName() = "tbl_authors"`,
`toDTO(): AuthorDTO`, `compareTo()` по `author`.
Аннотация `@JsonIgnoreProperties(value = ["database", "sqlToInsert"])`.

### Поля (Kotlin-тип по `Author.kt`, колонка по `@KaraokeDbTableField`)

| Kotlin-поле | Колонка | Тип (Kotlin) | Описание |
|---|---|---|---|
| `id` | `id` | Long | PK, `isId = true` |
| `author` | `author` | String | Имя автора |
| `ymId` | `ym_id` | String | **ID на Яндекс.Музыке** (для парсинга) |
| `vkId` | `vk_id` | String | **ID в VK** (для парсинга) |
| `lastAlbumYm` | `last_album_ym` | String | ID последнего альбома (YM) |
| `lastAlbumVk` | `last_album_vk` | String | ID последнего альбома (VK) |
| `lastAlbumProcessed` | `last_album_processed` | String | Время последней обработки альбома |
| `watched` | `watched` | Boolean | Наблюдаемый ли (отслеживаются ли новые альбомы) |
| `skip` | `skip` | Boolean | Пропускать ли при обработке |
| `aliases` | `aliases` | String | Псевдонимы (для LLM-поиска) |
| `description` | `description` | String | Полное описание |
| `shortDescription` | `short_description` | String | Короткое описание |
| `warning` | `warning` | String | Предупреждение (например, "автор отозвал согласие") |
| `isSpecialOrder` | `is_special_order` | Boolean | Спецзаказ (см. [editorial domain](../../editorial/domain.md)) |
| `sortOrder` | `sort_order` | Int | Порядок отображения (миграция `deploy/karaoke-db/46_author_sort_order.sql`) |

**Вычисляемый признак**: `val haveNewAlbum: Boolean` (см. «Логика»).

### Статические методы (`companion object`)

- `TABLE_NAME = "tbl_authors"`.
- `loadList(whereArgs, limit, offset, database, storageService, storageApiClient, ignoreUseInList): List<Author>` — фильтры `getWhereList`: `id`, `author`, `ym_id`, `vk_id`, `last_album_ym`, `last_album_vk`, `last_album_processed`, `watched`, `haveNewAlbum` (`+`/`true` и `-`/`false`), `skip`, `is_special_order`.
- `getAuthorById(id, ...): Author?` — `KaraokeDbTable.loadById`.
- `getAuthorsByIds(ids, ...): Map<Long, Author>` — пакетный `loadByIds`.
- `getAuthorByName(author, ...)`, `loadIdsByNames(...)` (chunked `WHERE author IN (...)`), `loadAuthorTilesWithCounts(...)`.
- `createNewAuthor(newAuthor, database): Author?`, `delete(id, database): Boolean`.
- `listHashes(database, whereText): List<RecordHash>?` (помечен `@Suppress("unused")`).
- `resolveByTerm(term, database): List<AuthorAliasMatch>`, `countWithNewAlbum(database): Int`.

### DTO и строки-плитки

- `AuthorDTO`: поля `id`, `author`, `ymId`, `vkId`, `lastAlbumYm`, `lastAlbumVk`, `lastAlbumProcessed`, `watched`, `skip`, `aliases`, `isSpecialOrder`, `sortOrder`, `description`, `shortDescription`, `warning`, `haveNewAlbum`, `pictureId`, `picturePreview`, `picturePreviewUrl`.
- `data class AuthorAliasMatch(author: String, matchedAliases: List<String>)`.
- `data class AuthorTileRow(id, author, readySongsCount, totalSongsCount, isSpecialOrder, sortOrder)` — готовая строка для `AuthorTilePublicDto` (см. `/zakroma`).

### API-контракты (karaoke-app `ApiController`)

| Endpoint | Контракт |
|---|---|
| `POST /api/authors/authorsdigests` | Списочный дайджест авторов (webvue3 `Authors/store.js`) |
| `POST /api/authors/updateauthor` | Обновление автора |
| `POST /api/authors/withnewalbumcount` | Бейдж «новые альбомы» (spec 176) |

## Логика и Алгоритмы | Logic and Algorithms

### `haveNewAlbum` — вычисляемый признак

```kotlin
val haveNewAlbum: Boolean get() =
    watched &&
        (ymId != "" || vkId != "") &&
        (lastAlbumYm != lastAlbumProcessed || lastAlbumVk != lastAlbumProcessed)
```

Семантика «нового альбома» продублирована в **трёх местах**, которые нужно
менять синхронно: getter `haveNewAlbum`, `getWhereList["haveNewAlbum=+"]`
(серверный фильтр) и `countWithNewAlbum` (SQL `SELECT COUNT(*)`).

### `countWithNewAlbum` — счётчик без инстанцирования

Прямой `SELECT COUNT(*) FROM tbl_authors WHERE watched = true AND
(ym_id <> '' OR vk_id <> '') AND (last_album_ym <> last_album_processed OR
last_album_vk <> last_album_processed)` — тот же предикат, что у getter,
без создания сущностей `Author` (шаблон `SiteChatMessage.countUnreadFromUsers`).
При ошибке JDBC возвращает `0`, при отсутствии соединения — `0`.

### `toDTO()` — резолв аватарки

`toDTO()` ищет картинку автора через
`Pictures.loadList(whereArgs = mapOf("name" to author), limit = 1,
ignoreUseInList = true)`. При наличии — `pictureId` и
`picturePreviewUrl = "/api/picture/file?file=<URLEncoder(storageFileNamePreview)>"`,
иначе `Pair(0L, "")`. `Author.getAuthorById` используется в `Album.toDTO()`
для денормализованного `authorName`/картинки автора.

### `resolveByTerm` — поиск по имени и алиасам

Лёгкий raw-`SELECT author, aliases FROM tbl_authors WHERE LOWER(author) = ?
OR LOWER(aliases) LIKE ?`. `matchedAliases` — только те алиасы, по которым
`term` реально совпал (`aliases` разбивается по `;`); пусто, если совпало
само имя. Сущности `Author` не создаются и storage не трогается — метод
безопасно вызывать из karaoke-web.

### Sync

`AuthorsSyncTarget` (`SyncTarget.kt`): `key = "authors"`,
`tableName = tbl_authors`, `oneClickDirection = LOCAL_TO_SERVER`,
`labelFn = { it.author }`, `rowChunkSize = 500` (вся таблица ~125 записей —
фактически один запрос).
Разрешения — 40 флагов `sync_authors_<push|pull>_<insert|update|delete|move>_allowed`
в `KaraokeProperties`.

### LLM-поиск (specs/llm-lyrics-search)

`aliases` — для LLM-поиска текстов песен. Автор может иметь
несколько имён/псевдонимов; LLM учитывает все; машинный резолв термина в
имя автора — через `Author.resolveByTerm`.

## Hot paths

- **`POST /api/authors/authorsdigests`** — список авторов.
- **`POST /api/authors/withnewalbumcount`** — бейдж (см.
  [store-authors.md](../../../system/frontend/store-authors.md)).
- **`/zakroma` плитки авторов** — `loadAuthorTilesWithCounts` +
  `AuthorTileRow`, счётчики поддерживаются DB-триггером
  `trg_tbl_songs_update_author_counts` (миграция `44_author_song_counts.sql`).
- **Парсинг YM/VK** — по `ymId` / `vkId`; `lastAlbumYm` / `lastAlbumVk`
  против `lastAlbumProcessed`.

## Зависимости | Dependencies

- [Song entity](song-entity.md) — `Song.author` (строковое имя, не FK).
- [Album entity](album-entity.md) — `Album.authorId` (FK).
- [Pictures](pictures.md) — аватарка автора (`name` = имя автора).
- [store-authors.md](../../../system/frontend/store-authors.md) — UI.

## Известные TODO

- [ ] `last_album_processed`: Kotlin-тип `String`, в KDoc/старых текстах —
      TIMESTAMP; уточнить фактический тип колонки (Pass 343+).
- [ ] `Author.aliases` — все call-site'ы LLM-поиска.

## Changelog

- **Pass 483** (2026-09-27, spec `483-knowledge-domain-catalog`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 427** (2026-09-09): Initial. Автор: agent (Karaoke).

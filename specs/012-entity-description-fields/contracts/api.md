# API Contracts: Доп. поля Author/Album/Song + новый UI Закромов

Ниже — только ИЗМЕНЕНИЯ относительно текущих контрактов (полные схемы см. в
исходных DTO-файлах). Все существующие поля и пути остаются без изменений.

## Публичный сайт (`karaoke-web`, без авторизации, `karaoke-public` — клиент)

### `GET /api/public/zakroma`

`PublicApiController.kt:155` → `ZakromaPublicDto` (`ZakromaPublicDto.kt:64`).

Новые поля в `ZakromaPublicDto`:

| Поле | Тип | Описание |
|---|---|---|
| `authorDescription` | `string` | Описание автора, `""` если не заполнено |
| `authorShortDescription` | `string` | Короткое описание автора |
| `authorWarning` | `string` | Предупреждение автора |
| `albumTypeCounts` | `AlbumTypeSummaryDto[]` | Только типы с count>0, в порядке studio→single→live→compilation→bootleg |

Новые поля в каждом элементе `albums[]` (`ZakromaAlbumPublicDto`):

| Поле | Тип | Описание |
|---|---|---|
| `description` | `string` | Описание альбома |
| `shortDescription` | `string` | Короткое описание альбома |
| `warning` | `string` | Предупреждение альбома |

Новая структура `AlbumTypeSummaryDto`:

```json
{ "dbValue": "studio", "groupLabel": "Студийные альбомы", "filterLabel": "Студийные", "count": 10 }
```

Элементы `albums[].albumSettings[]` (`ZakromaAlbumSongPublicDto`) — **без
изменений** (вне объёма фичи).

### `GET /api/public/song/{id}`

`PublicApiController.kt:266` → `SongPublicDto` (`SongPublicDto.kt:10`).

Новые поля (за флагом `includeDetails`, как и `formattedTextSong`):

| Поле | Тип | Описание |
|---|---|---|
| `description` | `string` | Описание песни |
| `shortDescription` | `string` | Короткое описание песни |
| `warning` | `string` | Предупреждение песни |

## Админка (`webvue3`, `permitAll()`, без авторизации)

Общий паттерн: существующие update-эндпоинты принимают частичный/полный
объект сущности и уже сохраняют произвольные присланные поля через
`KaraokeDbTable.save()`-diff (Author/Album) или через явный маппинг
изменённых ключей (Song, `saveSong`) — контракт расширяется декларативно, без
изменения формы запроса.

### `POST /api/authors/updateauthor`

Тело запроса (частичный `AuthorDTO`) — добавляются необязательные ключи
`description`, `shortDescription`, `warning: string`.

### `POST /api/albums/updatealbum` (и `createalbum`)

Тело запроса (частичный/полный `AlbumDTO`) — добавляются необязательные ключи
`description`, `shortDescription`, `warning: string`.

### `POST /api/song/update`

Тело запроса (diff-объект песни, `webvue3/.../Songs/store.js` `saveSong`) —
добавляются необязательные ключи `description`, `shortDescription`,
`warning: string`.

### Списочные/digest-эндпоинты админки

`AuthorDTO`, `AlbumDTO`, `SongDTO` (полные, не digest) — получают 3 новых
поля 1:1 с сущностью, для отображения текущих значений в таблицах
редактирования. `SongDTOdigest` — без изменений (не показывает эти поля в
списке, только в форме редактирования одной песни).

# Contract: Authorization header для `/api/public/authors/{authorId}/albums`

> **Delta-контракт** к [specs/356-zakroma-albums-by-author/contracts/albums-tiles-api.md](../../356-zakroma-albums-by-author/contracts/albums-tiles-api.md). Описывает **только** изменение поведения, вводимое спекой #360 (issue #76).
>
> **Никаких изменений** в URL, query-параметрах, форме ответа или DTO — меняется **только набор данных**, отдаваемых эндпоинтом, в зависимости от того, передал ли клиент валидный `Authorization`-заголовок.

## Контекст

Бэкенд уже реализует bypass `isEditor` для фильтра `ready_song_count > 0` (см. `PublicApiController.onlyPublishedFor` → `SiteUserResolver.resolve`). Но `ZakromaAlbumsView.vue` (единственный клиент этого эндпоинта) не передаёт `Authorization`-заголовок, поэтому сервер всегда видит анонимного пользователя → bypass не срабатывает → редактор видит то же, что гость (issue #76).

## Изменение поведения

### `GET /api/public/authors/{authorId}/albums?scope=main`

- **Без изменений**: query-параметр `scope=main`, форма ответа (`List<AlbumTilePublicDto>`).
- **Изменение поведения** (зависит от авторизации клиента):
  - **Запрос с валидным `Authorization: Bearer <token>` И `SiteUser.isEditor == true`** → фильтр `ready_song_count > 0` НЕ применяется. Возвращаются все не-skip альбомы автора с `total_song_count > 0`. Подпись у фронта — «N песен» (`totalSongCount`).
  - **Запрос без `Authorization`, или с невалидным/просроченным токеном, или с токеном пользователя без `isEditor`** → фильтр `ready_song_count > 0` применяется. Возвращаются только альбомы с готовыми песнями. Подпись у фронта — «N готовых» (`readySongCount`).

### Поведение клиента (требуемое исправление)

`karaoke-public/src/views/ZakromaAlbumsView.vue` ДОЛЖЕН передавать `Authorization: Bearer <token>` в fetch, где `<token>` — значение `localStorage.getItem('km_auth_token')` (если оно есть). Для анонимного пользователя заголовок не передаётся (поведение как для гостя).

#### Пример (исправленный fetch, вариант A — inline)

```js
const headers = {}
const token = localStorage.getItem('km_auth_token')
if (token) headers.Authorization = `Bearer ${token}`

const response = await fetch(
  `/api/public/authors/${this.authorId}/albums?scope=main`,
  { credentials: 'include', headers },
)
```

#### Пример (вариант B — через `apiGet()`)

Если `apiGet(url, params)` из `karaoke-public/src/services/api.js` подходит по сигнатуре (возвращает распарсенный JSON, поддерживает query-параметры), использовать его вместо `fetch`. Текущая реализация `apiGet` уже добавляет `Authorization: Bearer <token>` автоматически (см. `services/api.js:14-22`).

Конкретный выбор (A или B) — на стадии `/speckit.implement` после чтения `services/api.js` целиком.

## Не входит в контракт этой фичи

- **Никаких изменений в `AlbumTilePublicDto`** — поля остаются как в спеке 356 (FR-005 спеки).
- **Никаких изменений в `Album.loadAlbumTilesWithCounts`** — сигнатура уже принимает `onlyPublished: Boolean` (KDoc ссылается на FR-011 спеки 356).
- **Никаких изменений в `SiteUserResolver`** — контракт `Authorization: Bearer <token>` остаётся стандартным (паттерн спеки 017).
- **Никаких изменений в кеше `albumsTilesCache`** — TTL ≤60с, инвалидация через `consumeDirty()` (спек 356 FR-007/FR-014).
- **Skip-фильтр (`tbl_albums.skip = true`)** — НЕ реализуется в этой фиче. В `tbl_albums` пока нет колонки `skip` (Pass 357), фильтрация не выполняется (см. `Album.kt:517-518`).

## Контрактные точки (для будущих фич)

- Если в будущем добавится skip-фильтр (Pass 357+), `Album.loadAlbumTilesWithCounts` уже принимает `includeSkipped: Boolean` — фронт сможет учитывать `canWorkWithSkipped` через дополнительный API-параметр.
- Если в будущем введут дополнительные роли (admin, premium-расширенный) — паттерн «`SiteUser.X == true` → флаг в контроллер» готов к расширению через `onlyPublishedFor` → `isEditorFor` / `isPremiumFor` / etc.
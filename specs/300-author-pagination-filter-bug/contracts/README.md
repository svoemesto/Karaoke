# Contracts: Корректная пагинация таблиц после применения фильтра

**Дата**: 2026-09-03

## Сводка

В рамках этой задачи **backend-контракты НЕ изменяются** (см. AGENTS.md «Фикс не должен менять API/контракты бэкенда»).

Фикс целиком на стороне клиента (`webvue3/src/components/<Entity>/<Entity>Table.vue`): добавляется `watcher` на computed `countRows`, по образцу `Songs/SongsTable.vue:998-1009`.

## Существующие backend-эндпоинты (для справки)

### `POST /api/authors/authorsdigests`

**Request params** (все опциональны):

| Параметр | Тип | Описание |
|----------|-----|----------|
| `filterAuthor` | string | Поиск по имени автора |
| `filterId` | string | Поиск по ID |
| `filterYmId` | string | Поиск по Yandex Music ID |
| `filterVkId` | string | Поиск по VK ID |
| `filterLastAlbumYm` | string | Последний альбом на YM |
| `filterLastAlbumVk` | string | Последний альбом на VK |
| `filterLastAlbumProcessed` | string | Дата последней обработки |
| `filterWatched` | `'true'/'false'` | Флаг «просмотрено» |
| `filterHaveNewAlbum` | `'true'/'false'` | **Баг из #50** — флаг «есть новые альбомы» |
| `filterSkip` | `'true'/'false'` | Флаг «пропущено» |

**Response**: см. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:6188-6227`.

```json
{
  "workInContainer": true,
  "authorsDigests": [ /* Array<AuthorDTO> */ ]
}
```

**Известное ограничение**: поле `total` (общее число записей выборки) **отсутствует**. Клиент использует `authorsDigests.length` как surrogate для `<b-pagination :total-rows>`. Это документировано в `data-model.md` и `research.md`. Добавление `total` в ответ — отдельная задача (см. FR-005 в спеке как future-work).

### Аналогично для других таблиц

- `POST /api/songsdigests` (Songs) — без `total`, имеет `songsDigest`.
- `POST /api/albums/albumsdigests` (Albums) — без `total`.
- `POST /api/pictures/picturesdigests` (Pictures) — без `total`.
- `POST /api/siteusers/digest` (SiteUsers) — без `total`.

### Контраст: `POST /api/news/list` (News — эталон)

Возвращает `total`:

```json
{
  "news": [ /* Array<NewsDTO> */ ],
  "total": 123
}
```

Источник: `webvue3/src/components/News/store.js:62-79`. Именно наличие `total` позволяет News корректно отображать «Page X of Y» при пагинации и сбрасывать `currentPage` при смене target.

## Будущие контракты (не в этой задаче)

Если в будущем потребуется добавить `total` в ответы Authors/Albums/Pictures/SiteUsers — это потребует:

1. Изменения `*Controller.kt` в `karaoke-app` (добавить поле в `mapOf(...)`).
2. Изменения соответствующего `store.js` (добавить `set<Entity>TotalCount` mutation).
3. Изменения `<Entity>Table.vue` (использовать `get<Entity>TotalCount` в `countRows` computed).
4. Sync-контракт: не затрагивается (только клиент +1 поле в API).

Это **отдельная задача**, выходит за рамки текущего баг-фикса.
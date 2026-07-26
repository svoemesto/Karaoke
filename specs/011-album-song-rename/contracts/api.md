# API Contracts: Альбом как сущность + переименование Settings→Song

Проект не публикует внешний API/SDK — эти эндпоинты обслуживают исключительно собственные
фронтенды (`webvue3` admin, `karaoke-public`), как и весь остальной REST-слой `karaoke-app`/
`karaoke-web` (нет формального OpenAPI-контракта в репозитории — берём тот же неформальный,
prose-стиль документирования, что уже используют существующие эндпоинты Authors/Sync).

## Новые эндпоинты: Album (karaoke-app `ApiController.kt`, по образцу Authors)

- `POST /api/albums/albumsdigests` — список/фильтр альбомов.
  Request: `{ filterId?, filterAuthorId?, filterYear?, filterName?, filterAlbumType? }`.
  Response: `{ workInContainer: Boolean, albumsDigests: AlbumDTO[] }`.
- `POST /api/albums/createalbum` — создание нового альбома (в отличие от Author, который
  создаётся только автоматически, Album создаётся администратором вручную для новых песен,
  не покрытых бэкфиллом).
  Request: `AlbumDTO` (без `id`, либо `id=0`).
  Response: `{ id: Long }` (0 при ошибке, по конвенции `apisUpdateAuthor`).
- `POST /api/albums/updatealbum` — редактирование полей, включая `sortOrder`/`albumType`.
  Request: `{ id, authorId, year, name, albumType, sortOrder }`.
  Response: `{ id: Long }`.
- `POST /api/albums/deletealbum` — удаление (песни, ссылавшиеся на альбом, переходят в
  `albumId = null` через `ON DELETE SET NULL` — не требует отдельной логики в контроллере).
  Request: `{ id }`. Response: `{ success: Boolean }`.

Синхронизация (push/pull/one-click) — без нового кода в контроллере: `Album` регистрируется
в `SyncRegistry.all`, и уже существующие generic-эндпоинты (`/api/sync/entities`,
`/api/sync/setflag`, `/api/sync/run`, `/api/sync/oneclick`) автоматически подхватывают новую
сущность (см. research.md §7, Principle III).

## Новые эндпоинты: Song co-authors (karaoke-app `ApiController.kt` или `SongEditorController.kt`)

- `POST /api/songs/coauthors/list` — список соавторов песни.
  Request: `{ songId }`. Response: `{ coAuthors: AuthorDTO[] }`.
- `POST /api/songs/coauthors/add` — добавить одного соавтора.
  Request: `{ songId, authorId }`. Response: `{ success: Boolean }` (ошибка, если
  `authorId` совпадает с главным автором песни — см. data-model.md, edge case).
- `POST /api/songs/coauthors/remove` — удалить одного соавтора.
  Request: `{ songId, authorId }`. Response: `{ success: Boolean }`.

## Изменённые эндпоинты

- Существующий digest-эндпоинт песен (`ApiController` — сейчас `songs2Update`/аналог для
  списка `SettingsDTOdigest`, после переименования `SongDTOdigest`) — DTO дополняется полями
  `albumId: Long`, `albumName: String` (denormalized-удобство для таблицы в админке, без
  дополнительного запроса) и `coAuthorNames: List<String>` (только для отображения; изменение
  состава — через отдельные `coauthors/add|remove`, не через этот эндпоинт).
- `GET /zakroma` (karaoke-web `PublicApiController.kt`) и `ZakromaAlbumPublicDto` — дополняются
  полями `albumType` и используют `sortOrder` альбома вместо `(year, albumName)` для порядка
  внутри года (реализует FR-007/SC-002). Формат ответа расширяется, обратная совместимость
  сохраняется (новые поля опциональны для существующих потребителей).
- `GET /authors-tiles`, `GET /songs` — без изменений контракта (co-authors и albumId не влияют
  на существующую фильтрацию по строковому `author`).

## Валидация на границе API

- `createalbum`/`updatealbum`: `authorId` должен существовать в `tbl_authors`; `albumType`
  должен быть одним из значений `AlbumType.dbValue` (иначе 400).
- `coauthors/add`: `authorId` не должен совпадать с главным автором указанной песни (см.
  data-model.md edge case) — иначе 400 с понятным сообщением.
- Привязка песни к альбому (`updatealbum`-эндпоинт песни, где выставляется `albumId`) должна
  отклонять комбинацию, где автор альбома не совпадает с главным автором песни (FR-008).

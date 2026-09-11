# Zakroma Albums By Author (Альбомы авторов на публичном сайте)

> **Slug**: `zakroma-albums-by-author`
> **Feature branch**: `356-zakroma-albums-by-author`
> **Spec**: [specs/356-zakroma-albums-by-author/spec.md](../../specs/356-zakroma-albums-by-author/spec.md)
> **Status**: Implemented (Pass 360)

## Что делает фича

Добавляет на публичный сайт (`karaoke-public`) промежуточный этап «Альбомы автора» в навигации `/zakroma`:

```
Главная → /zakroma (плашки авторов)
       → /zakroma/{author_id} (страница песен автора)
       → /zakroma/{author_id}/albums (плашки альбомов автора) ← NEW
       → /zakroma/{author_id}?albumId={album_id} (песни выбранного альбома) ← NEW (query-параметр)
```

### Основные элементы

1. **Страница `/zakroma/{author_id}/albums`** — сетка плашек альбомов конкретного автора. Визуальный стиль — как у плашек авторов на `/zakroma` (см. `AuthorTiles.vue`). Плашка содержит:
   - Бейдж типа альбома (Pass 360) — над обложкой, цветной: `studio` (синий), `single` (фиолетовый), `live` (оранжевый), `compilation` (зелёный), `bootleg` (красный), `archive` (серый), `tribute` (жёлтый).
   - Обложку альбома 200×200 (полноразмерный файл, не preview — Pass 358 fix).
   - Год выпуска + название альбома.
   - Счётчик песен (N готовых для гостя / N песен для редактора).
2. **Псевдо-плашка «Все песни автора с группировкой по альбомам»** — первый элемент сетки через `<slot name="leading" />` в `AuthorTiles.vue` (паттерн спеки 307).
3. **Сортировка плашек** — `year ASC NULLS LAST, name ASC` (хронологический порядок, см. Clarification Q1).
4. **Сортировка внутри `?albumId=`** — клик по плашке альбома ведёт на `/zakroma/{author_id}?albumId={album_id}` — фильтр по `tbl_songs.album_id` на бэке, бэк отдаёт ТОЛЬКО песни этого альбома (Pass 360).
5. **Хлебные крошки**: `/zakroma/{author_id}/albums` → «К списку авторов» (на `/zakroma`); страница песен с `?albumId=` → «К альбомам автора» (на `/zakroma/{author_id}/albums`).
6. **Видимость альбомов на `/zakroma/{author_id}/albums`**:
   - Гость: только альбомы с `ready_song_count > 0` (есть готовые песни), подпись «N готовых».
   - Редактор: все альбомы автора, подпись «N песен» (`total_song_count`).
7. **Навигация** (Pass 359): клик по плашке автора на `/zakroma` ведёт **сразу** на `/zakroma/{author_id}/albums` (новая страница альбомов), а не на старую страницу песен. Цепочка навигации: `/zakroma` → `/zakroma/{id}/albums` → `/zakroma/{id}?albumId={id}`.

## Контракт

### SQL: `tbl_albums.total_song_count` / `ready_song_count`

Миграция: `deploy/karaoke-db/49_albums_song_counts.sql`.

- Тип: `BIGINT NOT NULL DEFAULT 0`.
- Колонки входят в `recordhash` `tbl_albums` (обновлённый триггер `update_tbl_albums_recordhash`).
- Поддерживаются актуальными триггером `trg_tbl_songs_update_album_counts` (AFTER INSERT/UPDATE/DELETE на `tbl_songs`).
- Миграция идемпотентна (`ADD COLUMN IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION/TRIGGER`, backfill через `UPDATE`).

### Триггер: `trg_tbl_songs_update_album_counts`

Полная логика — в миграции `49_albums_song_counts.sql`. Краткая сводка:

- **INSERT**: +1 в `total_song_count`, +1 в `ready_song_count` если `NEW.id_status >= 6`.
- **DELETE**: -1 в `total_song_count`, -1 в `ready_song_count` если `OLD.id_status >= 6`.
- **UPDATE album_id** (перенос песни): декремент у `OLD.album_id`, инкремент у `NEW.album_id`.
- **UPDATE id_status** (без смены album_id): меняется только `ready_song_count`.
- **Граничные случаи**: `album_id = NULL` → no-op, без `RAISE EXCEPTION`. Skip-альбомы — счётчики обновляются (UI фильтрует отдельно).

### SQL ORDER BY (для публичного endpoint)

```sql
SELECT id, author_id, year, name, album_type, sort_order, description, short_description, warning,
       picture_full, total_song_count, ready_song_count
FROM tbl_albums a
JOIN tbl_authors au ON au.id = a.author_id
WHERE a.skip = false
  AND (
    CASE WHEN :onlyPublished THEN a.ready_song_count > 0 ELSE a.total_song_count > 0 END
  )
ORDER BY a.year ASC NULLS LAST, a.name ASC
```

(Реализация — `Album.loadAlbumTilesWithCounts` в `karaoke-app/.../model/Album.kt`.)

### Публичный API: `GET /api/public/authors/{authorId}/albums?scope=main`

- **Endpoint**: `GET /api/public/authors/{authorId}/albums`
- **Параметры**: `authorId` (path, Long), `scope=main` (query, default).
- **Response**: `List<AlbumTilePublicDto>` (см. `karaoke-web/.../dto/AlbumTilePublicDto.kt`).
- **Поля** в каждом элементе: `id`, `name`, `year`, `pictureUrl` (полноразмерный — Pass 358 fix), `albumType` (`studio`/`single`/`live`/`compilation`/`bootleg`/`archive`/`tribute`), `totalSongCount`, `readySongCount`.
- **Backward compatible**: добавление полей не ломает существующих клиентов.
- **Кеш**: `albumsTilesCache` (TTL ≤60с), инвалидация через `consumeDirty()` при sync.

### Бэкенд-фильтр по `albumId` (Pass 360)

Чтобы не гонять с бэка ВСЕ песни автора (например 388), когда нужны только 10 из альбома:

- **`/api/public/zakroma?author=...&albumId=N`** — добавляет `AND album_id = N` в WHERE.
- **`/api/public/zakroma/stream?author=...&albumId=N`** — NDJSON-стрим отдаёт только песни этого альбома.

Реализация:
- `Zakroma.getZakroma(..., albumId: Long? = null)` — добавляет `args["album_id"]` если задан.
- `Song.kt getWhereList()` — `if (args.containsKey("album_id")) where += "album_id = $aid"`. БЕЗ префикса `AND` — `where` это `List<String>`, элементы joinятся через `" AND "`.
- PublicApiController — `@RequestParam albumId: Long?` на обоих эндпоинтах.

### Стрим-протокол: `albumId` обязателен

`ZakromaAlbumMetaPublicDto` (DTO для стрим-чанков) включает `albumId: Long` — иначе фильтр на фронте по `?albumId=N` не сработает (прецедент Pass 359: `ZakromaAlbumMetaPublicDto` не имел `albumId`, фильтр возвращал 0 песен).

## Контракты API

| Endpoint | Описание |
|---|---|
| `GET /api/public/authors/{authorId}/albums?scope=main` | Список альбомов автора (с обложками и счётчиками) |
| `GET /api/public/zakroma?author={name}` | Все песни автора (для страницы `/zakroma/{id}` без фильтра) |
| `GET /api/public/zakroma?author={name}&albumId=N` | Только песни альбома N (Pass 360 — не гоняем 388 песен когда нужны 10) |
| `GET /api/public/zakroma/stream?author={name}` | NDJSON-стрим всех песен (с прогрессом) |
| `GET /api/public/zakroma/stream?author={name}&albumId=N` | NDJSON-стрим песен одного альбома |

## UI: `AlbumTiles.vue` (вместо `AuthorTiles.vue`)

`AlbumTiles.vue` — отдельный компонент, НЕ `AuthorTiles.vue` (Pass 358 fix, в отличие от первоначального плана). Содержит:
- Бейдж типа альбома (Pass 360) — над обложкой, цветной, см. таблицу цветов выше.
- Полноразмерную обложку (`pictureUrl` — `*.album.png`, не `*.preview.album.png`).
- Год + название альбома.
- Счётчик песен (N готовых / N песен).

Псевдо-плашка «Все песни автора» реализована через `<slot name="leading" />` (паттерн спеки 307).

## Сценарии

- **Гость открывает `/zakroma/{author_id}/albums`** — видит плашки альбомов (только с готовыми песнями) с бейджем типа. Клик по плашке открывает `/zakroma/{author_id}?albumId={album_id}` со списком песен альбома.
- **Редактор открывает `/zakroma/{author_id}/albums`** — видит ВСЕ альбомы (включая без готовых песен); подпись плашки «N песен».
- **Клик по плашке автора на `/zakroma`** — теперь сразу ведёт на `/zakroma/{author_id}/albums` (новая цепочка навигации, Pass 70).
- **Переход по хлебной крошке** — со страницы песен с фильтром по альбому возвращает на список альбомов автора (а не на список авторов).
- **Производительность (Pass 360)** — при загрузке `/zakroma/35?albumId=541` бэк отдаёт только 45 песен альбома, а не все 388 песен автора. Подтверждено curl-тестом.

## Связанные документы

- [specs/356-zakroma-albums-by-author/](../../specs/356-zakroma-albums-by-author/) — полная спецификация (User Stories, FR, SC, Clarifications).
- [specs/356-zakroma-albums-by-author/research.md](../../specs/356-zakroma-albums-by-author/research.md) — технические решения (R1-R11).
- [specs/356-zakroma-albums-by-author/data-model.md](../../specs/356-zakroma-albums-by-author/data-model.md) — модель данных.
- [specs/356-zakroma-albums-by-author/contracts/albums-tiles-api.md](../../specs/356-zakroma-albums-by-author/contracts/albums-tiles-api.md) — контракт API.
- [specs/356-zakroma-albums-by-author/quickstart.md](../../specs/356-zakroma-albums-by-author/quickstart.md) — пошаговая валидация.
- [specs/286-author-song-counts-cache/spec.md](../../specs/286-author-song-counts-cache/spec.md) — прецедент (кэш счётчиков авторов через DB-триггер).
- [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md) — прецедент (zakroma-tiles + `<slot name="leading" />`).
- [docs/features/zakroma-tiles-sort-order.md](zakroma-tiles-sort-order.md) — историческая фича zakroma-tiles.
- [deploy/karaoke-db/49_albums_song_counts.sql](../../deploy/karaoke-db/49_albums_song_counts.sql) — миграция.
- [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md) — спека фиксирует сортировку year+name.
- [knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md](../../knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md) — ADR про обязательность `albumId` в стрим-DTO.

## Контрактные точки (для будущих фич)

- Кеш `albumsTilesCache` в `PublicApiController` — TTL ≤60с; инвалидация через `consumeDirty()` при sync.
- Sync LOCAL↔SERVER — `total_song_count`/`ready_song_count` входят в `recordhash`; sync автоматически работает.
- API `/api/public/authors/{authorId}/albums` — backward compatible (новый endpoint).
- Бэкенд принимает `?albumId=N` на `/api/public/zakroma` и `/api/public/zakroma/stream` — фронт передаёт `albumId` из `selectedAlbumId` (Pass 360).
- `ZakromaSettings.vue` (Pass 359) — компонент и `useZakromaSettings.js` композабл сохранены в репо (НЕ удалены), но сейчас НЕ используются на странице альбомов. Можно переиспользовать в будущем, если понадобится панель настроек размера/режима.

## Bugfix #76 (spec 360 — Pass 361, 2026-09-10)

Спека `360-editor-sees-all-albums` исправляет баг: редактор (`SiteUser.isEditor == true`) на публичном сайте видел только альбомы с готовыми песнями, как обычный пользователь, хотя контракт FR-011 спеки 356 явно требует, чтобы редактор видел **все** альбомы автора.

**Root cause**: `karaoke-public/src/views/ZakromaAlbumsView.vue:111` (Pass 360) — единственный клиент `/api/public/authors/{authorId}/albums`, который делал наивный `fetch` без `Authorization: Bearer <token>`-заголовка. `SiteUserResolver.resolve(request)` (`SiteUserResolver.kt:25`) берёт токен **только** из `Authorization`-заголовка (контракт спеки 017), поэтому бэкенд всегда видел анонимного пользователя → `onlyPublishedFor(request) == true` → `Album.loadAlbumTilesWithCounts(authorId, onlyPublished=true)` отдавал выборку гостя.

**Фикс** (Pass 361): добавить передачу `Authorization: Bearer ${localStorage.getItem('km_auth_token')}` в `fetch`. Паттерн — точно как в `useZakromaStreamProgress.js:144-146` и `services/api.js:15-18` (`authHeader()`). Бэкенд уже умеет bypass — никаких изменений на бэке.

**Влияние**: только `ZakromaAlbumsView.vue` (1 файл). Другие клиенты (`apiGet`, `useZakromaStreamProgress`) уже передавали заголовок — bypass для них работал с Pass 360.

**Валидация**:
- Автор #17 (АнимациЯ): гость → 22 альбома, 0 с `readySongCount=0`; редактор → 29 альбомов, 7 с `readySongCount=0` (id=244, 252, 254, 257, 261, 265, 270). Все 7 ожидаемых альбомов (по БД) видны.
- Без токена / с невалидным токеном: поведение как у гостя (как до фикса).

**См. также**: [specs/360-editor-sees-all-albums/spec.md](../../specs/360-editor-sees-all-albums/spec.md), [specs/360-editor-sees-all-albums/contracts/albums-tiles-authorization.md](../../specs/360-editor-sees-all-albums/contracts/albums-tiles-authorization.md).
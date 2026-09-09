# Zakroma Albums By Author (Альбомы авторов на публичном сайте)

> **Slug**: `zakroma-albums-by-author`
> **Feature branch**: `356-zakroma-albums-by-author`
> **Spec**: [specs/356-zakroma-albums-by-author/spec.md](../specs/356-zakroma-albums-by-author/spec.md)
> **Status**: Draft (заполняется в Phase 8 Polish)

## Что делает фича

Добавляет на публичный сайт (`karaoke-public`) промежуточный этап «Альбомы автора» в навигации `/zakroma`:

```
Главная → /zakroma (плашки авторов)
       → /zakroma/{author_id} (страница песен автора)
       → /zakroma/{author_id}/albums (плашки альбомов автора) ← NEW
       → /zakroma/{author_id}?album={album_id} (песни выбранного альбома) ← NEW (query-параметр)
```

### Основные элементы

1. **Страница `/zakroma/{author_id}/albums`** — сетка плашек альбомов конкретного автора. Визуальный стиль — как у плашек авторов на `/zakroma` (см. `AuthorTiles.vue`). Плашка содержит обложку альбома 200×200, год выпуска, название, количество песен.
2. **Псевдо-плашка «Все песни автора с группировкой по альбомам»** — первый элемент сетки через `<slot name="leading" />` в `AuthorTiles.vue` (паттерн спеки 307).
3. **Сортировка плашек** — `year ASC NULLS LAST, name ASC` (хронологический порядок, см. Clarification Q1).
4. **Секция «Альбомы автора» на `/zakroma/{author_id}`** — те же плашки над списком песен автора.
5. **Фильтр по альбому** через query-параметр `?album={album_id}` на странице песен автора.
6. **Хлебные крошки**: `/zakroma/{author_id}/albums` → «К списку авторов» (на `/zakroma`); страница песен с `?album=` → «К альбомам автора» (на `/zakroma/{author_id}/albums`).
7. **Видимость**:
   - Гость: только альбомы с `ready_song_count > 0` (есть готовые песни), подпись «N готовых».
   - Редактор: все альбомы автора, подпись «N песен» (`total_song_count`).
8. **Слайдер размера плашек** (200..400px, шаг 50, дефолт 200) — общий для `/zakroma` и `/zakroma/{author_id}/albums`, в `localStorage["zakroma_tile_size"]`.
9. **Переключатель «Плашки / Таблица»** — общий режим, в `localStorage["zakroma_view_mode"]`.

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
- **Новое поле** в каждом элементе: `albumType: String`, `totalSongCount: Long`, `readySongCount: Long`.
- **Backward compatible**: добавление полей не ломает существующих клиентов (новый endpoint).
- **Кеш**: `albumsTilesCache` (TTL ≤60с), инвалидация через `consumeDirty()` при sync.

### UI: `AuthorTiles.vue` (переиспользование)

Спека 307 уже добавила `<slot name="leading" />` в `AuthorTiles.vue` для спец-плашки «Отдельные песни разных авторов» на `/zakroma`. Тот же компонент переиспользуется для плашек альбомов на `/zakroma/{author_id}/albums` и в секции «Альбомы автора» на `/zakroma/{author_id}`.

### Per-user UI-настройки (localStorage)

| Key | Default | Allowed | Применяется |
|---|---|---|---|
| `zakroma_tile_size` | `200` | `200..400` (шаг 50) | Размер плашек на `/zakroma` + `/zakroma/{author_id}/albums` |
| `zakroma_view_mode` | `"tiles"` | `"tiles"`, `"table"` | Режим отображения на тех же страницах |

Реализация — `useZakromaSettings.js` composable + `ZakromaSettings.vue` component.

## Сценарии

- **Гость открывает `/zakroma/{author_id}/albums`** — видит плашки альбомов (только с готовыми песнями); клик по плашке открывает `/zakroma/{author_id}?album={album_id}` со списком песен альбома.
- **Редактор открывает `/zakroma/{author_id}/albums`** — видит ВСЕ альбомы (включая без готовых песен); подпись плашки «N песен».
- **Переход по хлебной крошке** — со страницы песен с фильтром по альбому возвращает на список альбомов автора (а не на список авторов).
- **Слайдер размера** — сдвигаешь, плашки меняются; F5 — размер сохранился.
- **Переключатель «Плашки / Таблица»** — переключаешь, таблица отображается; F5 — режим сохранился; переход между `/zakroma` и `/zakroma/{id}/albums` — общий режим.

## Связанные документы

- [specs/356-zakroma-albums-by-author/](../specs/356-zakroma-albums-by-author/) — полная спецификация (User Stories, FR, SC, Clarifications).
- [specs/356-zakroma-albums-by-author/research.md](../specs/356-zakroma-albums-by-author/research.md) — технические решения (R1-R11).
- [specs/356-zakroma-albums-by-author/data-model.md](../specs/356-zakroma-albums-by-author/data-model.md) — модель данных.
- [specs/356-zakroma-albums-by-author/contracts/albums-tiles-api.md](../specs/356-zakroma-albums-by-author/contracts/albums-tiles-api.md) — контракт API.
- [specs/356-zakroma-albums-by-author/quickstart.md](../specs/356-zakroma-albums-by-author/quickstart.md) — пошаговая валидация.
- [specs/286-author-song-counts-cache/spec.md](../specs/286-author-song-counts-cache/spec.md) — прецедент (кэш счётчиков авторов через DB-триггер).
- [specs/307-special-authors-zakroma-order/spec.md](../specs/307-special-authors-zakroma-order/spec.md) — прецедент (zakroma-tiles + `<slot name="leading" />`).
- [docs/features/zakroma-tiles-sort-order.md](zakroma-tiles-sort-order.md) — историческая фича zakroma-tiles.
- [deploy/karaoke-db/49_albums_song_counts.sql](../deploy/karaoke-db/49_albums_song_counts.sql) — миграция.
- [knowledge/adr/local-0007-album-tile-sort-order.md](../knowledge/adr/local-0007-album-tile-sort-order.md) — ADR фиксирует выбор year+name сортировки.

## Контрактные точки (для будущих фич)

- Кеш `albumsTilesCache` в `PublicApiController` — TTL ≤60с; инвалидация через `consumeDirty()` при sync.
- Sync LOCAL↔SERVER — `total_song_count`/`ready_song_count` входят в `recordhash`; sync автоматически работает.
- API `/api/public/authors/{authorId}/albums` — backward compatible (новый endpoint).
- localStorage-настройки (`zakroma_tile_size`, `zakroma_view_mode`) — общие для `/zakroma` + `/zakroma/{author_id}/albums`. Можно переиспользовать для других разделов сайта (та же логика composable).
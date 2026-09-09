# Data Model: Закрома — Альбомы авторов

> **Контекст**: денормализация счётчиков песен в `tbl_albums` (аналог спеки 286 для `tbl_authors`) + новый DTO `AlbumTilePublicDto` для публичного API.

## Изменения в существующих таблицах

### `tbl_albums` — новые колонки

```sql
ALTER TABLE public.tbl_albums
    ADD COLUMN IF NOT EXISTS total_song_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.tbl_albums
    ADD COLUMN IF NOT EXISTS ready_song_count BIGINT NOT NULL DEFAULT 0;
```

| Поле | Тип | Default | NOT NULL | Описание |
|---|---|---|---|---|
| `total_song_count` | `BIGINT` | `0` | ✅ | Общее кол-во песен альбома (`COUNT(*) FROM tbl_songs WHERE album_id = tbl_albums.id`) |
| `ready_song_count` | `BIGINT` | `0` | ✅ | Кол-во песен с `id_status >= 6` (`COUNT(*) ... WHERE id_status >= 6`) |

**Обоснование `BIGINT`**: у автора может быть тысячи песен, альбом редко превышает 100, но единообразие с `tbl_authors.ready_songs_count`/`total_songs_count` (BIGINT в спеке 286) + будущее-совместимость.

### Обновление `update_tbl_albums_recordhash()`

Включает новые колонки в md5:

```sql
CREATE OR REPLACE FUNCTION public.update_tbl_albums_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.author_id::TEXT, '') ||
        COALESCE(NEW.year::TEXT, '') ||
        COALESCE(NEW.name, '') ||
        COALESCE(NEW.album_type, '') ||
        COALESCE(NEW.sort_order::TEXT, '') ||
        COALESCE(NEW.description, '') ||
        COALESCE(NEW.short_description, '') ||
        COALESCE(NEW.warning, '') ||
        COALESCE(NEW.total_song_count::TEXT, '') ||
        COALESCE(NEW.ready_song_count::TEXT, '')
    );
    RETURN NEW;
END;
$$;
```

**Важно**: старая функция `update_tbl_albums_recordhash()` уже существует (миграция 29), но **не включала** `total_song_count`/`ready_song_count`. После `CREATE OR REPLACE FUNCTION` + backfill (см. ниже) sync корректно работает.

### Триггер: `trg_tbl_songs_update_album_counts`

AFTER INSERT/UPDATE/DELETE на `tbl_songs`. Поддерживает `total_song_count`/`ready_song_count` в `tbl_albums` атомарно.

```sql
CREATE OR REPLACE FUNCTION public.trg_tbl_songs_update_album_counts()
RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Новая песня: +1 в total, +1 в ready если готова.
        UPDATE public.tbl_albums
        SET
            total_song_count = total_song_count + 1,
            ready_song_count = ready_song_count + CASE WHEN NEW.id_status >= 6 THEN 1 ELSE 0 END
        WHERE id = NEW.album_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        -- Удаление песни: -1 в total, -1 в ready если была готова.
        UPDATE public.tbl_albums
        SET
            total_song_count = total_song_count - 1,
            ready_song_count = ready_song_count - CASE WHEN OLD.id_status >= 6 THEN 1 ELSE 0 END
        WHERE id = OLD.album_id;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        -- UPDATE album_id (перенос между альбомами)
        IF (OLD.album_id IS DISTINCT FROM NEW.album_id) THEN
            -- Декремент у OLD
            IF OLD.album_id IS NOT NULL THEN
                UPDATE public.tbl_albums
                SET
                    total_song_count = total_song_count - 1,
                    ready_song_count = ready_song_count - CASE WHEN OLD.id_status >= 6 THEN 1 ELSE 0 END
                WHERE id = OLD.album_id;
            END IF;
            -- Инкремент у NEW
            IF NEW.album_id IS NOT NULL THEN
                UPDATE public.tbl_albums
                SET
                    total_song_count = total_song_count + 1,
                    ready_song_count = ready_song_count + CASE WHEN NEW.id_status >= 6 THEN 1 ELSE 0 END
                WHERE id = NEW.album_id;
            END IF;
        -- UPDATE id_status без смены album_id
        ELSIF (OLD.id_status IS DISTINCT FROM NEW.id_status) THEN
            UPDATE public.tbl_albums
            SET
                ready_song_count = ready_song_count
                    - CASE WHEN OLD.id_status >= 6 THEN 1 ELSE 0 END
                    + CASE WHEN NEW.id_status >= 6 THEN 1 ELSE 0 END
            WHERE id = NEW.album_id;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_tbl_songs_update_album_counts
AFTER INSERT OR UPDATE OR DELETE ON public.tbl_songs
FOR EACH ROW
EXECUTE FUNCTION public.trg_tbl_songs_update_album_counts();
```

**Граничные случаи**:
- `album_id = NULL` → UPDATE затрагивает 0 строк (no-op), без `RAISE EXCEPTION`.
- Skip-альбом (`tbl_albums.skip = true`) → счётчики обновляются нормально (UI скрывает отдельно).
- Висящая песня (`album_id` без соответствия) → аналогично.

### Backfill существующих альбомов

```sql
-- Один UPDATE с подзапросом COUNT(*) GROUP BY album_id. Альбомы без песен
-- остаются с (0, 0) благодаря LEFT JOIN + COALESCE.
UPDATE public.tbl_albums a
SET
    total_song_count = COALESCE(s.total_cnt, 0),
    ready_song_count = COALESCE(s.ready_cnt, 0)
FROM (
    SELECT
        album_id,
        COUNT(*) AS total_cnt,
        COUNT(*) FILTER (WHERE id_status >= 6) AS ready_cnt
    FROM public.tbl_songs
    WHERE album_id IS NOT NULL
    GROUP BY album_id
) s
WHERE a.id = s.album_id;

-- Backfill recordhash для существующих строк (триггер сработает только на новые UPDATE).
UPDATE public.tbl_albums
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(author_id::TEXT, '') ||
    COALESCE(year::TEXT, '') ||
    COALESCE(name, '') ||
    COALESCE(album_type, '') ||
    COALESCE(sort_order::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '') ||
    COALESCE(total_song_count::TEXT, '') ||
    COALESCE(ready_song_count::TEXT, '')
) WHERE id > 0;
```

## Kotlin модель: `Album.kt`

Добавить два поля с аннотациями `@KaraokeDbTableField`:

```kotlin
@KaraokeDbTableField(name = "total_song_count")
var totalSongCount: Long = 0

@KaraokeDbTableField(name = "ready_song_count")
var readySongCount: Long = 0
```

**KDoc** (обязательно — FR-006):
```kotlin
/**
 * Общее кол-во песен альбома. Поддерживается SQL-триггером
 * `trg_tbl_songs_update_album_counts` атомарно при INSERT/UPDATE/DELETE
 * в `tbl_songs`. Не редактировать вручную.
 * @see specs/286-author-song-counts-cache (прецедент для авторов)
 */
@KaraokeDbTableField(name = "total_song_count")
var totalSongCount: Long = 0

/**
 * Кол-во песен альбома с `id_status >= 6` (APPROVED, см. [dictionaries.md](
 *   ../../knowledge/domains/catalog/components/dictionaries.md)). Поддерживается
 * SQL-триггером `trg_tbl_songs_update_album_counts`.
 * @see specs/286-author-song-counts-cache
 */
@KaraokeDbTableField(name = "ready_song_count")
var readySongCount: Long = 0
```

## DTO: `AlbumTilePublicDto`

Расположение: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AlbumTilePublicDto.kt`.

```kotlin
package com.svoemesto.karaokeweb.dto

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Публичный DTO плашки альбома для эндпоинта
 * `GET /api/public/authors/{authorId}/albums?scope=main`.
 *
 * Backward compatible — добавление полей не ломает существующих клиентов.
 *
 * @property id Идентификатор альбома (`tbl_albums.id`).
 * @property name Название альбома.
 * @property year Год выпуска (`0` или `null` → не отображается в подписи).
 * @property pictureUrl URL обложки альбома 200×200 в MinIO; пустая строка если нет.
 * @property totalSongCount Общее кол-во песен альбома (для редакторов).
 * @property readySongCount Кол-во готовых песен (`id_status >= 6`, для гостей).
 * @property albumType Тип альбома (`"studio"` / `"live"` / `"compilation"` /
 *   `"bootleg"`, см. AlbumType.dbValue).
 *
 * @see docs/features/zakroma-albums-by-author.md
 * @see specs/356-zakroma-albums-by-author/spec.md FR-014
 */
data class AlbumTilePublicDto(
    val id: Long,
    val name: String,
    val year: Int,
    val pictureUrl: String,
    val totalSongCount: Long,
    val readySongCount: Long,
    val albumType: String,
) {
    companion object {
        private const val BUCKET = "karaoke"

        /**
         * Строит URL обложки альбома по имени автора/году/названию альбома.
         * Шаблон: `<author>/<year> - <name>/<author> - <year> - <name>.preview.album.png`.
         * Пустая строка если поля пустые.
         */
        fun albumPictureUrl(
            authorName: String,
            year: Int,
            albumName: String,
            storageUrl: String,
        ): String {
            if (authorName.isEmpty() || year <= 0 || albumName.isEmpty()) return ""
            val previewFileName = "$authorName/$year - $albumName/$authorName - $year - $albumName.preview.album.png"
            val encoded = URLEncoder.encode(previewFileName, StandardCharsets.UTF_8).replace("+", "%20")
            return "$storageUrl/$BUCKET/$encoded"
        }
    }
}
```

## DTO: `AlbumMetaPublicDto` (опционально, для страницы песен альбома)

Если страница `/zakroma/{authorId}?album={albumId}` нуждается в показе плашки/имени текущего альбома в шапке — отдельный DTO. На implement уточняется (см. A-005, отложить если сложно).

## UI State (localStorage)

```typescript
// karaoke-public/src/composables/useZakromaSettings.js
export const ZAKROMA_TILE_SIZE_KEY = 'zakroma_tile_size';
export const ZAKROMA_VIEW_MODE_KEY = 'zakroma_view_mode';

export const DEFAULT_TILE_SIZE = 200;
export const MIN_TILE_SIZE = 200;
export const MAX_TILE_SIZE = 400;
export const TILE_SIZE_STEP = 50;

export const VIEW_MODE_TILES = 'tiles';
export const VIEW_MODE_TABLE = 'table';
export const DEFAULT_VIEW_MODE = VIEW_MODE_TILES;
```

## Сводка изменений по файлам

| Файл | Изменения |
|---|---|
| `deploy/karaoke-db/49_albums_song_counts.sql` | NEW — миграция: +columns, +trigger, +backfill, +recordhash |
| `karaoke-app/.../model/Album.kt` | +2 поля (`totalSongCount`, `readySongCount`) + KDoc |
| `karaoke-web/.../dto/AlbumTilePublicDto.kt` | NEW — DTO для публичного API |
| `karaoke-web/.../controllers/PublicApiController.kt` | +endpoint `/authors/{authorId}/albums` + кеш `albumsTilesCache` |
| `karaoke-app/.../model/Album.kt` | +`loadAlbumTilesWithCounts(authorId, scope, onlyPublished, includeSkipped)` |
| `karaoke-public/src/router/` | +route `/zakroma/:authorId(\d+)/albums` |
| `karaoke-public/src/views/ZakromaAlbumsView.vue` | NEW (или доработка `ZakromaView.vue`) |
| `karaoke-public/src/components/ZakromaSettings.vue` | NEW — слайдер + переключатель |
| `karaoke-public/src/composables/useZakromaSettings.js` | NEW — реактивный state из localStorage |
| `docs/features/zakroma-albums-by-author.md` | NEW — per-feature документ (FR-009) |
| `knowledge/adr/local-0007-album-tile-sort-order.md` | NEW (опционально) — local ADR |
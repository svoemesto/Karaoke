# Кэш счётчиков песен автора в `tbl_authors`

> **Status**: active
> **Feature Key**: author-song-counts-cache
> **Last Updated**: 2026-08-31
> **Spec**: [specs/286-author-song-counts-cache/spec.md](../../specs/286-author-song-counts-cache/spec.md)
> **Migration**: `deploy/karaoke-db/44_author_song_counts.sql`

## Что делает

Денормализация: хранит количество готовых (`ready_songs_count`) и общее (`total_songs_count`) песен автора прямо в `tbl_authors`. Эндпоинт `/api/public/authors-tiles` читает эти числа одной точечной выборкой вместо GROUP BY по `tbl_songs` (снижение нагрузки на БД при заходе в `/zakroma`).

Счётчики поддерживаются актуальными **DB-триггером `trg_tbl_songs_update_author_counts`** на `tbl_songs` (INSERT/UPDATE/DELETE) — атомарно в той же транзакции, что и изменение песни.

## Зачем

При заходе на `/zakroma` (`/api/public/authors-tiles?scope=main`) фронт показывает сетку плашек авторов с подписью «N готовых песен» (для редактора — «N песен всего»). Текущая реализация (см. `Song.loadAuthorSongCounts` в `karaoke-app/.../model/Song.kt:7234`) делает `SELECT song_author, COUNT(*) FROM tbl_songs WHERE ... GROUP BY song_author` — это GROUP BY по таблице с ~18k+ строк на проде.

GROUP BY дороже точечной выборки; даже при L2-кеше `authorsTilesCache` (TTL=30 мин, спека 248) холодные старты и инвалидация триггерят тяжёлую агрегацию. Денормализация переносит работу с пути чтения на путь записи (триггер обновляет счётчик при каждом изменении — это дёшево) и убирает GROUP BY с горячего пути.

## Где живёт

| Артефакт | Расположение |
|----------|--------------|
| Миграция | `deploy/karaoke-db/44_author_song_counts.sql` |
| Триггер | `trg_tbl_songs_update_author_counts` на `tbl_songs` (только на LOCAL-БД активен; на SERVER-БД — no-op, потому что `tbl_songs` пуста) |
| `recordhash`-функция | `update_tbl_authors_recordhash()` — пересоздана с включением `ready_songs_count`/`total_songs_count` в md5 |
| Новый SQL-метод | `Author.loadAuthorTilesWithCounts(onlyPublished, isSpecialOrder, database)` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` |
| DTO-результат | `data class AuthorTileRow(id, author, readySongsCount, totalSongsCount, isSpecialOrder)` — там же |
| Потребитель | `PublicApiController.authorsTiles()` — `karaoke-web/.../controllers/PublicApiController.kt:258` |
| Cache invalidation | `notifyStatsDirty()` при изменении `id_status` песни — `karaoke-app/.../controllers/ApiController.kt:3034` (snapshot `idStatusBefore`) и `:3170` (проверка после save) |

## Как работает

### На стороне БД

1. **`tbl_authors`** получает 2 новые колонки: `ready_songs_count BIGINT NOT NULL DEFAULT 0`, `total_songs_count BIGINT NOT NULL DEFAULT 0`.
2. **`trg_tbl_songs_update_author_counts`** на `tbl_songs` (AFTER INSERT/UPDATE/DELETE, FOR EACH ROW):
   - INSERT: `+1` в `total_songs_count` для `NEW.song_author`; `+1` в `ready_songs_count`, если `NEW.id_status >= 6`.
   - DELETE: `-1` в `total_songs_count` для `OLD.song_author`; `-1` в `ready_songs_count`, если `OLD.id_status >= 6`.
   - UPDATE `song_author` (перенос песни между авторами): декремент у `OLD.song_author`, инкремент у `NEW.song_author` с учётом `OLD/NEW.id_status`.
   - UPDATE `id_status` (1 → 6 или 6 → 5): меняется только `ready_songs_count`.
   - **Skip-авторы** (`tbl_authors.skip = true`) — счётчики обновляются нормально; UI скрывает их отдельно.
   - **«Висящие» песни** (`song_author` без соответствия в `tbl_authors`) — UPDATE затрагивает 0 строк (no-op), без `RAISE EXCEPTION`.
3. **`recordhash`-функция** для `tbl_authors` пересоздаётся с включением новых колонок в md5 (Constitution Principle III).

### На стороне синхронизации

- `tbl_authors` участвует в sync через `AuthorsSyncTarget` (`SyncTarget.kt:281`) — без изменений.
- `sync_authors_push_update_allowed = true` (по умолчанию) — счётчики пушатся на SERVER.
- На SERVER-БД миграция добавляет колонки и делает backfill (на SERVER `tbl_songs` пуста, backfill даст `0,0` для всех; реальные значения приедут через sync).

### На стороне API

`PublicApiController.authorsTiles()` теперь делает **один SQL-запрос** вместо трёх:

```kotlin
val rows = Author.loadAuthorTilesWithCounts(
    onlyPublished = onlyPublished,
    isSpecialOrder = isSpecialOrderFilter,
    database = WORKING_DATABASE,
)
rows.map { row ->
    AuthorTilePublicDto.fromAuthorName(
        id = row.id,
        author = row.author,
        songCount = if (onlyPublished) row.readySongsCount else row.totalSongsCount,
        isSpecialOrder = row.isSpecialOrder,
    )
}
```

SQL:
```sql
SELECT id, author, ready_songs_count, total_songs_count, is_special_order
FROM tbl_authors
WHERE skip = false
  [AND is_special_order = ?]   -- зависит от scope
  AND (ready_songs_count > 0 OR total_songs_count > 0)  -- onlyPublished=true/false
ORDER BY author
```

### На стороне cache invalidation

L2-кеш `authorsTilesCache` (TTL=30 мин, спека 248) сбрасывается через `StatBySong.consumeDirty()`:
1. При изменении `id_status` песни (готово/не готово) `karaoke-app` вызывает `notifyStatsDirty()` → POST на `/api/internal/stats/mark-dirty` на `karaoke-web` → `StatBySong.markDirty()`.
2. Следующий запрос `/api/public/authors-tiles` триггерит `consumeDirty()` → кэш очищается → новая выборка (уже с актуальными счётчиками).

## Граничные случаи

| Случай | Поведение |
|--------|-----------|
| Skip-автор (`tbl_authors.skip = true`) | Счётчики обновляются, но плашка не показывается (UI-фильтр). |
| «Висящая» песня (`song_author` без соответствия в `tbl_authors`) | Триггер — no-op (UPDATE 0 строк), без `RAISE EXCEPTION`. |
| UPDATE `song_author` (перенос песни) | Декремент у OLD-автора + инкремент у NEW-автора, с учётом OLD/NEW `id_status`. |
| Переход `id_status` 1 → 6 | `ready_songs_count +1`, `total_songs_count` без изменений. |
| Переход `id_status` 6 → 5 | `ready_songs_count -1`. |
| Конкурентные `save()` песен одного автора | Триггеры на разных строках `tbl_songs` — независимы. На уровне JVM гонок нет (один процесс `karaoke-app`). |
| Миграция на SERVER-БД (где `tbl_songs` пуста) | Триггер создаётся, но никогда не срабатывает. Backfill даст `0,0` для всех авторов, реальные значения приедут через sync. |

## Тестирование

См. `specs/286-author-song-counts-cache/quickstart.md` — пошаговое руководство:
1. Применение миграции и проверка структуры.
2. 8 тестов триггера (insert/update/delete/transfer/skip/ghost).
3. 5 тестов API (аноним/редактор, GROUP BY в логе, cache invalidation).
4. 2 теста sync LOCAL → SERVER.
5. Cleanup тестовых данных.

## Связанные спеки

- `specs/013-song-status-filter` — порог `id_status >= 6` для публичной готовности.
- `specs/017-editor-status-bypass` — для редактора фильтр по статусу снят, `songCount` = `total_songs_count`.
- `specs/022-song-status-lifecycle` — жизненный цикл статуса песни (значения 0..6).
- `specs/008-special-orders` — `is_special_order` флаг.
- `specs/248-authors-tiles-cache` — L2-кеш `authorsTilesCache` (TTL=30 мин).
- `specs/258-zakroma-routing-refactor` — `RT-1.A1` (id в `AuthorTilePublicDto`).
- `specs/270-db-indexes-verification` — индексы на `tbl_songs.song_author` и `id_status`.

## История

- **2026-08-31**: спека `286-author-song-counts-cache` — Pass 282, обсуждение с пользователем о снижении нагрузки на `/zakroma`. Миграция `44_author_song_counts.sql`, триггер `trg_tbl_songs_update_author_counts`, `Author.loadAuthorTilesWithCounts`, `notifyStatsDirty()` для `id_status`.
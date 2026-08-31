# Data Model: Кэш счётчиков песен автора в `tbl_authors`

**Feature**: 286-author-song-counts-cache
**Date**: 2026-08-31
**Status**: Phase 1 — модель данных спроектирована

---

## Изменения схемы БД

### Таблица `tbl_authors` — две новые колонки

| Колонка | Тип | Default | Описание |
|---------|-----|---------|----------|
| `ready_songs_count` | `BIGINT NOT NULL` | `0` | Количество песен автора со статусом `id_status >= 6` |
| `total_songs_count` | `BIGINT NOT NULL` | `0` | Количество всех песен автора в `tbl_songs` (включая skip-песни, включая неготовые) |

**Семантика** (зафиксирована в Clarifications):
- Счётчики обновляются для **всех авторов**, у которых `song_author` есть в `tbl_authors`, **независимо от `tbl_authors.skip`** (UI скрывает skip-авторов отдельно, БД остаётся консистентной).
- «Готовая песня» = `id_status >= 6` (зеркалит `Song.loadAuthorSongCounts` и `Zakroma.getZakroma`).
- «Общее количество» = `COUNT(*) FROM tbl_songs WHERE song_author = author.author` — все песни, независимо от `id_status`.

### Таблица `tbl_songs` — без изменений колонок

Добавляется только триггер `trg_tbl_songs_update_author_counts` (см. ниже).

---

## Триггер `trg_tbl_songs_update_author_counts` (только на LOCAL)

**Назначение**: атомарно обновлять `tbl_authors.ready_songs_count` и `tbl_authors.total_songs_count` при любых изменениях `tbl_songs`.

**Покрывает**:
- INSERT — `+1` в `total_songs_count`; `+1` в `ready_songs_count` если `id_status >= 6`.
- UPDATE поля `id_status`:
  - `old_id_status < 6 AND new_id_status >= 6` → `+1` в `ready_songs_count`.
  - `old_id_status >= 6 AND new_id_status < 6` → `-1` в `ready_songs_count`.
  - `total_songs_count` — без изменений.
- UPDATE поля `song_author` (перенос песни):
  - декремент у `OLD.song_author` (по правилам выше, в зависимости от `OLD.id_status`).
  - инкремент у `NEW.song_author` (по правилам выше, в зависимости от `NEW.id_status`).
  - учитывать, что `id_status` мог тоже поменяться в этом же UPDATE — четыре комбинации переходов.
- DELETE — `-1` в `total_songs_count`; `-1` в `ready_songs_count` если `OLD.id_status >= 6`.

**Граничные случаи** (зафиксированы в Clarifications):
- **Skip-автор**: триггер обновляет счётчики независимо от `tbl_authors.skip`. UI скрывает автора, БД остаётся консистентной.
- **«Висящая» песня** (`song_author` без соответствия в `tbl_authors`): триггер выполняет `UPDATE tbl_authors SET ... WHERE author = NEW/OLD.song_author`. Если 0 строк затронуто — no-op, без `RAISE EXCEPTION`.

**Реализация** (PL/pgSQL, скетч — точная реализация в `deploy/karaoke-db/44_author_song_counts.sql`):

```sql
CREATE OR REPLACE FUNCTION trg_tbl_songs_update_author_counts()
RETURNS TRIGGER AS $$
DECLARE
    v_old_ready_delta INT := 0;
    v_new_ready_delta INT := 0;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.id_status >= 6 THEN
            UPDATE tbl_authors SET ready_songs_count = ready_songs_count + 1
                WHERE author = NEW.song_author;
        END IF;
        UPDATE tbl_authors SET total_songs_count = total_songs_count + 1
            WHERE author = NEW.song_author;
        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.id_status >= 6 THEN
            UPDATE tbl_authors SET ready_songs_count = ready_songs_count - 1
                WHERE author = OLD.song_author;
        END IF;
        UPDATE tbl_authors SET total_songs_count = total_songs_count - 1
            WHERE author = OLD.song_author;
        RETURN OLD;

    ELSIF TG_OP = 'UPDATE' THEN
        -- Перенос песни между авторами (OLD.song_author <> NEW.song_author)
        IF OLD.song_author IS DISTINCT FROM NEW.song_author THEN
            -- Декремент у старого автора
            IF OLD.id_status >= 6 THEN
                UPDATE tbl_authors SET ready_songs_count = ready_songs_count - 1
                    WHERE author = OLD.song_author;
            END IF;
            UPDATE tbl_authors SET total_songs_count = total_songs_count - 1
                WHERE author = OLD.song_author;
            -- Инкремент у нового автора
            IF NEW.id_status >= 6 THEN
                UPDATE tbl_authors SET ready_songs_count = ready_songs_count + 1
                    WHERE author = NEW.song_author;
            END IF;
            UPDATE tbl_authors SET total_songs_count = total_songs_count + 1
                WHERE author = NEW.song_author;
        ELSE
            -- Тот же автор, меняется только id_status
            IF OLD.id_status < 6 AND NEW.id_status >= 6 THEN
                UPDATE tbl_authors SET ready_songs_count = ready_songs_count + 1
                    WHERE author = NEW.song_author;
            ELSIF OLD.id_status >= 6 AND NEW.id_status < 6 THEN
                UPDATE tbl_authors SET ready_songs_count = ready_songs_count - 1
                    WHERE author = NEW.song_author;
            END IF;
            -- total_songs_count не меняется
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tbl_songs_update_author_counts
    AFTER INSERT OR UPDATE OR DELETE
    ON tbl_songs
    FOR EACH ROW
    EXECUTE FUNCTION trg_tbl_songs_update_author_counts();
```

---

## Миграция `deploy/karaoke-db/44_author_song_counts.sql`

Структура файла (по образцу `27_author_special_order.sql`):

1. **ADD COLUMN** для двух новых колонок в `tbl_authors`.
2. **CREATE OR REPLACE FUNCTION update_tbl_authors_recordhash()** с включением `ready_songs_count`, `total_songs_count` в md5.
3. **Backfill счётчиков** одним UPDATE:
   ```sql
   UPDATE tbl_authors a SET
       ready_songs_count = COALESCE(s.ready_cnt, 0),
       total_songs_count = COALESCE(s.total_cnt, 0)
   FROM (
       SELECT
           song_author AS author,
           COUNT(*) FILTER (WHERE id_status >= 6) AS ready_cnt,
           COUNT(*) AS total_cnt
       FROM tbl_songs
       GROUP BY song_author
   ) s
   WHERE a.author = s.author;
   ```
   (Авторы без песен — `ready_songs_count = 0, total_songs_count = 0` благодаря `COALESCE`.)
4. **Backfill recordhash** для существующих строк `tbl_authors` (триггер сработает только на новые INSERT/UPDATE).
5. **CREATE OR REPLACE FUNCTION trg_tbl_songs_update_author_counts()** + **CREATE TRIGGER** (только на LOCAL — проверка `database_name = 'karaoke_local'` или комментарий в README миграции, что SERVER часть пропускает trigger).

**Применение**:
- На LOCAL: весь файл, включая trigger.
- На SERVER: всё кроме `CREATE TRIGGER` (или trigger создаётся, но он никогда не сработает, потому что `tbl_songs` пустая на SERVER — это безопасно).
- Принято решение: trigger создаётся на обеих БД для единообразия; на SERVER он no-op, потому что `tbl_songs` пуста. Это упрощает миграцию и не требует условной логики.

---

## Изменения в Kotlin-коде

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`

Добавляется:

```kotlin
/**
 * Строка для плитки автора в /zakroma: id + счётчики готовых/общих песен +
 * флаг спецзаказа. Возвращается из [loadAuthorTilesWithCounts] для прямого
 * формирования AuthorTilePublicDto без дополнительных lookup-вызовов.
 *
 * @see specs/286-author-song-counts-cache
 * @see docs/features/author-song-counts-cache.md
 */
data class AuthorTileRow(
    val id: Long,
    val author: String,
    val readySongsCount: Long,
    val totalSongsCount: Long,
    val isSpecialOrder: Boolean,
)

companion object {
    /**
     * Загружает плитки авторов с предрассчитанными счётчиками песен из
     * tbl_authors.ready_songs_count / total_songs_count (заполняются триггером
     * trg_tbl_songs_update_author_counts, см. миграцию 44). Один SQL-запрос —
     * заменяет связку Song.loadAuthorSongCounts + Song.loadListAuthors +
     * Author.loadIdsByNames в PublicApiController.authorsTiles().
     *
     * @param onlyPublished true для анонимов/обычных (показываем только готовые
     *   песни), false для редактора (полное количество песен).
     * @param isSpecialOrder null = все, true = только спецзаказные авторы,
     *   false = только не-спецзаказные.
     * @param database KaraokeConnection.
     * @return список строк, отсортированный по author ASC. Плитки с нулём
     *   песен (для своего scope) уже отфильтрованы в SQL.
     *
     * @see specs/286-author-song-counts-cache FR-006
     * @see docs/features/author-song-counts-cache.md
     */
    fun loadAuthorTilesWithCounts(
        onlyPublished: Boolean,
        isSpecialOrder: Boolean?,
        database: KaraokeConnection,
    ): List<AuthorTileRow> {
        // Реализация — один SELECT с условной фильтрацией.
        // Аналогично существующему Author.loadIdsByNames (см. Author.kt:302).
        // ...
    }
}
```

**Без изменений в существующих полях `Author`** — `@KaraokeDbTableField` для `ready_songs_count` / `total_songs_count` НЕ добавляются (эти поля только читаются напрямую через SQL, не через reflection ORM).

### `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`

Заменяется блок в `authorsTiles()` (строки 277-289):

```kotlin
// Было:
val counts = Song.loadAuthorSongCounts(
    isSpecialOrder = isSpecialOrderFilter,
    onlyPublished = onlyPublished,
    database = WORKING_DATABASE,
)
val loadedAuthors: List<String> =
    Song.loadListAuthors(
        withSkiped = false,
        isSpecialOrder = isSpecialOrderFilter,
        database = WORKING_DATABASE,
    ).filter { (counts[it] ?: 0L) > 0L }
val specialFlags: Map<String, Boolean> = loadedAuthors.associateWith {
    it in counts.keys && (isSpecialOrderFilter ?: false)
}
val authorIdsByName: Map<String, Long> = Author.loadIdsByNames(loadedAuthors, WORKING_DATABASE)
loadedAuthors.mapNotNull { authorName -> ... }

// Стало:
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

Кэш `getCachedAuthorsTiles()` (L2) остаётся без изменений.

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`

В эндпоинте `songUpdate` (или эквивалентном — где меняется `id_status`) добавляется вызов `notifyStatsDirty()` если `id_status` изменился.

Найти точку: см. `ApiController.kt:3166` где уже есть `if (songValue.free != freeBefore) notifyStatsDirty()`. Добавить аналогичную проверку для `id_status`:

```kotlin
songValue.saveToDb()
songValue.saveToFile()
if (songValue.free != freeBefore) notifyStatsDirty()
if (songValue.idStatus != idStatusBefore) notifyStatsDirty()  // <-- новый
```

(Имя поля `idStatus` — уточнить по реальному коду; может быть `id_status` через reflection.)

---

## Связь с sync

- `tbl_authors` участвует в sync через `AuthorsSyncTarget` (см. `SyncTarget.kt:281`).
- `sync_authors_push_update_allowed = true` (по умолчанию) — изменения `ready_songs_count`, `total_songs_count` пушатся на SERVER.
- `recordhash`-функция для `tbl_authors` пересоздаётся с включением новых колонок (FR-009 спеки).
- На SERVER миграция добавляет колонки и backfill-ит их (используя, например, `LEFT JOIN ... COALESCE` — на SERVER `tbl_songs` пуста, поэтому backfill даст 0 для всех, но это нормально, потому что sync привезёт реальные значения с LOCAL).

---

## Чек-лист «изменения в схеме»

- [x] `tbl_authors`: +`ready_songs_count BIGINT NOT NULL DEFAULT 0`
- [x] `tbl_authors`: +`total_songs_count BIGINT NOT NULL DEFAULT 0`
- [x] `tbl_authors.recordhash`: пересоздать функцию с включением 2 новых колонок
- [x] `tbl_songs`: триггер `trg_tbl_songs_update_author_counts` (AFTER INSERT/UPDATE/DELETE)
- [ ] `tbl_songs.song_author`, `tbl_songs.id_status`: без изменений (только чтение триггером)

## Чек-лист «изменения в коде»

- [x] `Author.kt`: +`data class AuthorTileRow` + `fun loadAuthorTilesWithCounts()`
- [x] `PublicApiController.authorsTiles()`: заменить 3 запроса на 1
- [x] `ApiController.songUpdate` (или эквивалент): +`notifyStatsDirty()` при изменении `id_status`
- [x] `docs/features/author-song-counts-cache.md`: новый per-feature документ

## Чек-лист «sync»

- [x] `recordhash` для `tbl_authors` пересоздан (LOCAL и SERVER)
- [x] Backfill recordhash выполнен
- [x] Sync-флаги `sync_authors_*` остаются `true`
- [x] `AuthorsSyncTarget` уже зарегистрирован (без изменений)
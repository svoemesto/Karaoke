# Component: author-cache

> **Домен**: [caching](../domain.md)
> **Компонент**: специфика `AuthorsCache` (денормализация в БД) и
> `AuthorTilesCache` (in-memory кеш тайлов).

## Ответственность | Responsibility

В Karaoke есть **126+ исполнителей** (Authors), и для главной страницы
нужны счётчики песен по каждому (`total_songs_count`, `ready_songs_count`).
Прямой `SUM` по `tbl_settings` для 126 авторов — **~200 мс**, что
неприемлемо для hot path.

Решение — **денормализация** в `tbl_authors`:

```sql
ALTER TABLE tbl_authors ADD COLUMN total_songs_count INT DEFAULT 0;
ALTER TABLE tbl_authors ADD COLUMN ready_songs_count INT DEFAULT 0;
```

Это ускоряет чтение до **~2 мс** (indexed lookup).

**[WARN]** Денормализация требует **синхронизации** — при
INSERT/UPDATE/DELETE в `tbl_settings` нужно обновить
`tbl_authors.total_songs_count`. Если забыть — рассинхрон.

## Интерфейсы и Контракты | Interfaces and Contracts

### `tbl_authors.total_songs_count` (INT)

- **Значение**: COUNT(*) FROM tbl_settings WHERE author_id = ?.
- **Обновляется**: trigger на `tbl_settings` или scheduler.
- **Использование**: главная страница (Author tiles), фильтры
  (например, «авторы с >= 10 песнями»).

### `tbl_authors.ready_songs_count` (INT)

- **Значение**: COUNT(*) FROM tbl_settings WHERE author_id = ? AND id_status = 6.
- **Обновляется**: trigger / scheduler.
- **Использование**: фильтр «доступные исполнители».

### Кеш тайлов авторов — без класса `AuthorTilesCache`

**[WARN] Поправка Pass 474: классов `AuthorTilesCache` и
`AuthorsCache` в репозитории нет** (проверено `find` + grep по
`*.kt`). Реальная реализация — companion-object внутри
`karaoke-web/.../controllers/PublicApiController.kt` (`:74-100`):

- `ConcurrentHashMap<String, CachedAuthorsTiles>`;
- `CACHE_TTL_MS = 30 * 60 * 1000L` (30 минут);
- включается флагом `karaoke.public.authors-tiles-cache.enabled`
  (`KaraokeProperties.kt:447-449`);
- инвалидация — через `StatBySong.consumeDirty`.

Ни `bgExecutor`, ни `frozenAtStartup`, ни `getTopTiles(limit=50)`,
ни cron-обновления в коде нет.

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм поддержки денормализации

#### Через trigger (синхронно)

```sql
CREATE OR REPLACE FUNCTION update_author_song_counts() RETURNS TRIGGER AS $$
BEGIN
    -- [1] Обновить total_songs_count
    UPDATE tbl_authors SET total_songs_count = (
        SELECT COUNT(*) FROM tbl_settings WHERE author_id = NEW.author_id
    ) WHERE id = NEW.author_id;

    -- [2] Обновить ready_songs_count
    UPDATE tbl_authors SET ready_songs_count = (
        SELECT COUNT(*) FROM tbl_settings
        WHERE author_id = NEW.author_id AND id_status = 6
    ) WHERE id = NEW.author_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_author_counts
AFTER INSERT OR UPDATE OR DELETE ON tbl_settings
FOR EACH ROW EXECUTE FUNCTION update_author_song_counts();
```

**Плюсы**: всегда консистентно.
**Минусы**: trigger добавляет latency на INSERT/UPDATE/DELETE.
Для hot path (массовый импорт) — лучше scheduler.

#### Через scheduler (асинхронно)

```kotlin
@Scheduled(cron = "0 */15 * * * *")  // каждые 15 минут
fun refreshAuthorCounts() {
    // SQL: UPDATE tbl_authors SET total_songs_count = (SELECT COUNT(*) ...)
    // SQL: UPDATE tbl_authors SET ready_songs_count = (SELECT COUNT(*) ...)
}
```

**Плюсы**: не замедляет hot path.
**Минусы**: до 15 минут stale.

**[WARN]** Поправка Pass 474: используется **DB-триггер**, а не
scheduler. `deploy/karaoke-db/44_author_song_counts.sql:187` создаёт
`TRIGGER trg_tbl_songs_update_author_counts AFTER INSERT OR UPDATE OR
DELETE ON public.tbl_songs FOR EACH ROW` (функция — `:114-183`;
шапка миграции `:7` прямо говорит «колонки поддерживаются
актуальными DB-триггером»). Функции `refreshAuthorCounts` в коде нет
(grep по `*.kt` — 0), а приведённый ниже `@Scheduled`-вариант —
описание альтернативы, а не текущего механизма.

### Алгоритм cold-start `AuthorTilesCache`

```kotlin
@Component
class AuthorTilesCache {
    private val bgExecutor = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var tiles: List<AuthorTile> = emptyList()
    private val frozenAtStartup = AtomicBoolean(true)

    init {
        // [1] Стартуем async refresh
        bgExecutor.submit { refresh() }
    }

    fun getTiles(): List<AuthorTile> {
        if (frozenAtStartup.get()) {
            log.warn("cache:authorTiles cold-start, returning empty")
        }
        return tiles
    }

    fun refresh() {
        tiles = authorService.getTopTiles(limit = 50)
        frozenAtStartup.set(false)
    }
}
```

**Cold-start поведение**: HTTP-запрос `/api/main/authors` возвращает
`emptyList()` пока refresh не закончен. UI должен показывать
«Загрузка…».

## Зависимости | Dependencies

- → [domain](../domain.md) — `AuthorsCache`, `AuthorTilesCache`.
- → [caching-patterns](caching-patterns.md) — паттерны 4 и 5.
- → [catalog domain](../../catalog/domain.md) — `Song.author_id`
  для денормализации.

## Ловушки и предупреждения

**[WARN] Trigger vs scheduler** — в Karaoke используется scheduler.
Если перейти на trigger, hot path замедлится (INSERT/UPDATE/DELETE
в `tbl_settings` будет ждать `COUNT(*)` по 126 авторам).

**[WARN] Денормализация может рассинхронизироваться** при прямых
SQL-операциях мимо приложения (например, `psql` миграция).
Рекомендуется: после миграции всегда вызывать `refreshAuthorCounts()`
вручную или ждать scheduler'а (≤15 мин).

**[WARN] `AuthorTilesCache` empty при cold-start** — UI не должен
ломаться, должен показывать placeholder.

**[WARN] `LIMIT 50` в `getTopTiles`** — если у вас > 50 авторов,
нужен pagination, иначе показываются только TOP-50.

## Связанные фичи

- `286-author-song-counts-cache` — спека денормализации.
- `248-authors-tiles-cache` — спека AuthorTilesCache.
- `289-fix-statbysong-cache-on-cold-start` — async cold-start refresh.

## Связанные ADR | Related ADRs

- ADR по денормализации (TODO, задокументировать trade-off trigger vs scheduler).

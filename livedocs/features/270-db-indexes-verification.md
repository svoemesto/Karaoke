---
status: Active
slug: 270-db-indexes-verification
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/270-db-indexes-verification/spec.md
  - 241-db-storage-perf-audit
---

# 270 — Верификация и идемпотентное создание индексов FR-110 (LiveDoc)

> Drill-down — [specs/270-db-indexes-verification/spec.md](../../specs/270-db-indexes-verification/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md) — Tier-2 / FR-110.

## Что делает

Идемпотентная SQL-миграция `deploy/karaoke-db/41_db_indexes_verification.sql`, которая через
`CREATE INDEX IF NOT EXISTS` гарантирует наличие трёх индексов на `tbl_songs` и `tbl_events`.

## ⚠️ Контекстная находка (важно)

**Все три индекса уже созданы** в исходном `01_initdb.sql:148,173` (исходно как
`tbl_settings_*_index`, переименованы в `28_rename_settings_to_songs.sql:48,66`).
`tbl_events_song_id_index` — в исходном `01_initdb.sql` (см. также дамп
`karaoke_clear_dump.sql:2542-2545`).

**На текущем проде миграция — no-op** (`CREATE INDEX IF NOT EXISTS` пропускает уже
существующие индексы).

### Почему миграция всё равно ценна

1. **Документирование в git-истории** — явный артефакт в `deploy/karaoke-db/`. Спека 241
   FR-110 была аналитической находкой; без миграции она «существует только в спецификации».
2. **Защита от восстановления БД из старого дампа**, в котором индексов нет (например,
   бэкап до `28_rename_settings_to_songs.sql`).
3. **Baseline для будущих фич** — явная точка отсчёта, на которой можно строить новые
   оптимизации.

## Effect

| Индекс | Используется в запросе | Hot endpoint |
|--------|------------------------|--------------|
| `tbl_songs_song_author_index` | `Song.loadListAuthors`, `Song.loadAuthorSongCounts` (DISTINCT/GROUP BY song_author) | `/api/public/authors-tiles` (главная «Закромов») |
| `tbl_songs_id_status_index` | `Song.loadAuthorSongCounts` с `onlyPublished=true` (`WHERE id_status >= 6`) | `/api/public/authors-tiles` для анонимов |
| `tbl_events_song_id_index` | `StatBySong.getStatBySong` (17 условных `count(*) FILTER (...)` + `GROUP BY song_id`) | Thymeleaf `/statbysong`, фоновый refresh |

Без индексов — full scan + sort. С индексами — Index Scan / Bitmap Index Scan.

## Реализация

`deploy/karaoke-db/41_db_indexes_verification.sql` — 3 строки `CREATE INDEX IF NOT EXISTS`:

```sql
CREATE INDEX IF NOT EXISTS tbl_songs_song_author_index ON public.tbl_songs USING btree (song_author);
CREATE INDEX IF NOT EXISTS tbl_songs_id_status_index ON public.tbl_songs USING btree (id_status);
CREATE INDEX IF NOT EXISTS tbl_events_song_id_index ON public.tbl_events USING btree (song_id);
```

### Convention имён

`tbl_<table>_<column>_index` (НЕ `idx_*`). Это convention проекта с `01_initdb.sql` —
все индексы названы по этой схеме. Миграция использует существующие имена, чтобы
`IF NOT EXISTS` сработал корректно.

### Почему НЕ `CREATE INDEX CONCURRENTLY`

`CREATE INDEX CONCURRENTLY` несовместимо с `IF NOT EXISTS` в PostgreSQL <14 (даже в 14+
даёт неожиданное поведение). Миграция применяется либо на текущем проде (no-op), либо
на маленькой dev-БД, где блокировка миллисекунды — ок.

## Runtime-валидация (опционально)

Пользователь может проверить на проде (после deploy миграции):

```sql
-- Должен показать Index Scan или Bitmap Index Scan, не Seq Scan:
EXPLAIN ANALYZE SELECT song_author, count(*) FROM tbl_songs
    WHERE id_status >= 6 GROUP BY song_author;

EXPLAIN ANALYZE SELECT song_id, count(*) FROM tbl_events
    WHERE song_id > 0 GROUP BY song_id;
```

## Backward-compat

**Полная совместимость** — миграция либо создаёт индексы, либо no-op. Никаких изменений
в схеме данных (DDL `CREATE INDEX`, не `ALTER TABLE ADD COLUMN`). Никаких изменений в
коде. Никаких изменений в API. Контракт `tbl_songs` / `tbl_events` сохраняется.

## Никакого Kotlin-кода

Это «dba-fix», не фича кода. Изменения только в:
- `deploy/karaoke-db/41_db_indexes_verification.sql` (NEW)
- `livedocs/features/270-db-indexes-verification.md` (NEW)
- `specs/270-db-indexes-verification/{spec,plan,tasks,checklists}.md` (NEW)
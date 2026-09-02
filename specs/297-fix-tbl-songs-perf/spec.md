---
status: Active
work_package: 47
created: 2026-09-02
source: specs/296-Проверка-инцидентов-на-проде/REPORT.md
---

# Feature Specification: 297 — Оптимизация производительности `tbl_songs`

**Feature Branch**: `297-fix-tbl-songs-perf`
**Created**: 2026-09-02
**Input**: Pass 296 (отчёт `tools/analyze-prod-incident.sh 24`) — найдены 🔴 медленные SQL:
- `SELECT count(*) AS cnt FROM tbl_songs s` — **4-5 сек × 17 запросов за 24ч**
- `select song_author, count(*) as cnt from tbl_songs ... group by song_author` — dead code, но найден в коде
- `COPY public.tbl_songs (...)` — 23 сек (ежедневный backup через pg_dump; **норма для backup**)

## Контекст

В спецификации 296 «Проверка инцидентов на проде» через
`tools/analyze-prod-incident.sh 24` обнаружено:

| Проблема | Частота | Где |
|---|---|---|
| `count(*) FROM tbl_songs` 4-5 сек | 17 за 24ч | `StatBySong.refreshCache()` (Pass 289, JOIN с tbl_authors) |
| `SELECT song_author, count(*) ... group by song_author` | dead code (0 вызовов) | `Song.kt:7289` `loadAuthorSongCounts()` |
| `COPY tbl_songs (...)` 23 сек | 1 за 24ч | `deploy/karaoke-db-backup.sh` (pg_dump) |

**Текущее состояние таблицы `tbl_songs` на проде** (по `pg_stat_user_tables`):

```
n_live_tup    = 32958     -- строк
size          = 113 MB
seq_scan      = 9847      -- full table scans
seq_tup_read  = 220793713 -- строк прочитано через seq_scan (~6700 раз вся таблица)
idx_scan      = 523315    -- через индексы (в ~53 раза больше, но всё равно есть куча seq)
```

**Существующие индексы `tbl_songs`** (по `pg_indexes`):

| Index | idx_scan | Размер |
|---|---|---|
| `tbl_songs_pkey` (id) | 519k | 1400 kB |
| `idx_gin_result_text` (GIN) | 79 | 23 MB |
| `idx_tbl_songs_recordhash` | 0 | 4640 kB |
| `tbl_songs_file_name_index` | 0 | 3840 kB |
| `tbl_songs_song_name_index` | 0 | 1848 kB |
| `tbl_songs_id_boosty_files_index` | 0 | 1624 kB |
| ... (много unused индексов по `id_boosty`, `id_vk_*`, `id_dzen_*`, `id_telegram_*`, `id_pl_*`) | 0 | ~10 MB |
| `tbl_songs_id_status_index` | — | — |
| `tbl_songs_publish_date_index` | — | — |
| `tbl_songs_publish_time_index` | — | — |
| `tbl_songs_song_author_index` | — | — |

**Отсутствуют индексы на `tbl_songs`:**
- `tags` (text) — для фильтра `'SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))`
- `free` (boolean) — для `WHERE free = true`
- `source_markers` (text) — для `WHERE btrim(coalesce(source_markers, '')) != ''`
- `root_id` (integer) — для запросов из `Utils.kt:106,273,423`

**`tbl_authors` — отсутствует индекс на `skip`** (boolean, 126 строк).
Используется в JOIN каждый час в `StatBySong.refreshCache()`.

## Гипотезы

### 🔴 1. `count(*) FROM tbl_songs s` без WHERE — full table scan

В `StatBySong.refreshCache()` (Pass 289) JOIN с `tbl_authors` уже ускорил freeNow с ~8 сек до ~2 сек.
Но 17 медленных запросов за 24ч (~14% вызовов `refreshCache`) — это либо cold-start до
того как hash на `tbl_authors` построен, либо peak hours когда запрос дольше.

**Фикс**:
1. Добавить индекс `tbl_authors_skip_idx` (btree, partial `WHERE skip = false`) — ускорит JOIN.
2. Добавить MATERIALIZED VIEW `mv_songs_free_now` (обновляется раз в 5 минут через cron).

### 🔴 2. Dead code в `Song.kt`

- `Song.kt:7087 totalCount()` — `@Suppress("unused")`, можно удалить.
- `Song.kt:7267 loadAuthorSongCounts()` — нигде не вызывается (0 использований).

**Фикс**: удалить как legacy.

### ✅ 3. `COPY tbl_songs` 23 сек — НЕ ТРОГАЕМ

Это ежедневный backup (`deploy/karaoke-db-backup.sh` → `pg_dump`). 23 сек для таблицы в 113 MB — **норма**.

## User Stories

- **US1 (P1)**: После оптимизации средний `refreshCache()` ≤500 мс (сейчас 2-5 сек).
- **US2 (P1)**: Ни одного медленного SQL (`duration: >1s`) в pg_log за 24ч.
- **US3 (P2)**: Dead code удалён — компиляция чистая.
- **US4 (P2)**: Миграция БД идемпотентная (можно запускать повторно).

## Functional Requirements

- **FR-001**: Создать индекс `tbl_authors_skip_idx` (btree, partial `WHERE skip = false`).
- **FR-002**: Создать индекс `tbl_songs_tags_idx` (btree на `upper(tags)` с опциональной `WHERE tags IS NOT NULL`).
- **FR-003**: Создать индекс `tbl_songs_free_partial_idx` (btree partial `WHERE free = true`).
- **FR-004**: Создать MATERIALIZED VIEW `mv_songs_free_now` (см. ниже SQL).
- **FR-005**: Создать refresh-функцию `refresh_mv_songs_free_now()` + триггер/cron.
- **FR-006**: Удалить `Song.kt:7087 totalCount()` (dead code — `@Suppress("unused")`).
- **FR-007**: ~~Удалить `Song.kt:7267 loadAuthorSongCounts()`~~ — **ОТМЕНЕНО**: функция используется
  в `PublicApiController.kt:443` (metaExpectedCount). Замена на
  `Author.loadAuthorTilesWithCounts` (Pass 286) — отдельный follow-up.
- **FR-008**: Добавить миграцию `deploy/karaoke-db/44_optimize_tbl_songs.sql` — идемпотентную.
- **FR-009**: Обновить `StatBySong.refreshCache()` — `freeNow` читать из `mv_songs_free_now` (а не из JOIN с фильтрами).
- **FR-010**: Деплой на прод + наблюдение 24ч (через `analyze-prod-incident.sh 24`).

## Acceptance Criteria

- [ ] **AC1**: `EXPLAIN ANALYZE SELECT count(*) FROM tbl_songs s JOIN tbl_authors a ON ... WHERE a.skip = false AND ...` ≤500 мс.
- [ ] **AC2**: `analyze-prod-incident.sh 24` после деплоя показывает 0 медленных SQL `duration: >1000ms` (для count и GROUP BY).
- [ ] **AC3**: `pg_stat_user_tables.tbl_songs.seq_tup_read` не растёт (или растёт линейно с n_live_tup, а не × 6700).
- [ ] **AC4**: Миграция `44_optimize_tbl_songs.sql` идемпотентна (можно запускать повторно без ошибок).
- [ ] **AC5**: `git grep "totalCount\|loadAuthorSongCounts"` — 0 результатов в коде.

## SQL (миграция `deploy/karaoke-db/44_optimize_tbl_songs.sql`)

```sql
-- 1. tbl_authors.skip — для JOIN в StatBySong.refreshCache()
CREATE INDEX IF NOT EXISTS tbl_authors_skip_idx
  ON public.tbl_authors USING btree (skip)
  WHERE skip = false;

-- 2. tbl_songs.tags — для фильтра SKIP-тегов (Pass 293)
CREATE INDEX IF NOT EXISTS tbl_songs_tags_idx
  ON public.tbl_songs USING btree (upper(tags))
  WHERE tags IS NOT NULL AND tags <> '';

-- 3. tbl_songs.free — partial для freeNow
CREATE INDEX IF NOT EXISTS tbl_songs_free_partial_idx
  ON public.tbl_songs USING btree (free)
  WHERE free = true;

-- 4. MATERIALIZED VIEW для freeNow (самая тяжёлая часть refreshCache)
CREATE MATERIALIZED VIEW IF NOT EXISTS public.mv_songs_free_now AS
SELECT
    s.id,
    s.song_author,
    s.tags,
    s.id_status,
    s.source_markers,
    s.free,
    s.publish_date,
    s.publish_time
FROM public.tbl_songs s
JOIN public.tbl_authors a ON a.author = s.song_author
WHERE a.skip = false
  AND s.id_status >= 6
  AND btrim(coalesce(s.source_markers, '')) != '';

CREATE UNIQUE INDEX IF NOT EXISTS mv_songs_free_now_id_idx
  ON public.mv_songs_free_now USING btree (id);

-- 5. refresh function (вызывается из cron каждые 5 минут)
CREATE OR REPLACE FUNCTION public.refresh_mv_songs_free_now()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY public.mv_songs_free_now;
END;
$$ LANGUAGE plpgsql;
```

## Связь с другими спеками

- **Pass 286**: уже денормализовал `total_songs_count / ready_songs_count` в `tbl_authors`.
  Этот pass добавляет partial index на `tbl_authors.skip` для JOIN.
- **Pass 289**: оптимизировал `StatBySong.refreshCache` (уже использовал JOIN).
  Этот pass выносит freeNow в MATERIALIZED VIEW.
- **Pass 293**: SKIP-авторы и песни — наш новый индекс на `upper(tags)` помогает фильтру.

## Связь с OpenProject

- Work package: TBD (создать в этом pass)
- Status: New → In progress → In review → Closed
- URL: http://localhost:8080/work_packages/47

## История

- 2026-09-02 — спека создана по итогам Pass 296 (отчёт analyze-prod-incident)
- 2026-09-02 — исследование `pg_stat_user_tables` + `pg_indexes` на проде

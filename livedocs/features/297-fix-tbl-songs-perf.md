---
status: Active
slug: 297-fix-tbl-songs-perf
related:
  - ../../specs/297-fix-tbl-songs-perf/spec.md
---

# 297 — Оптимизация производительности `tbl_songs` (LiveDoc)

> Drill-down — [specs/297-fix-tbl-songs-perf/spec.md](../../specs/297-fix-tbl-songs-perf/spec.md).

## Что делает

Оптимизирует hot-queries к `tbl_songs` через индексы и `MATERIALIZED VIEW mv_songs_free_now`.
Источник проблемы — Pass 296 отчёт `tools/analyze-prod-incident.sh 24`:
- 🔴 `count(*) FROM tbl_songs s JOIN tbl_authors a ON ...` — **4-5 сек × 17 запросов/день**
- 🔴 `pg_stat_user_tables.tbl_songs.seq_tup_read` — **220 793 713 строк** через seq_scan

**До** (Pass 296): запрос `freeNow` в `StatBySong.refreshCache()` занимал 2-5 сек.
**После** (Pass 297): запрос из `mv_songs_free_now` занимает **41 мс** (×56 быстрее).

## User Stories

- **US1 (P1)**: после деплоя `refreshCache().freeNow` ≤500 мс вместо 2-5 сек.
- **US2 (P1)**: ни одного медленного SQL `duration: >1s` в pg_log за 24ч.
- **US3 (P2)**: dead code удалён (`Song.totalCount()`).
- **US4 (P2)**: миграция БД идемпотентна (можно запускать повторно через `psql`).

## Functional Requirements

- **FR-001..FR-005**: миграция `deploy/karaoke-db/44_optimize_tbl_songs.sql`:
  - 3 partial index (`tbl_authors_skip_idx`, `tbl_songs_tags_idx`, `tbl_songs_free_partial_idx`)
  - `MATERIALIZED VIEW mv_songs_free_now` + 3 индекса на ней
  - `FUNCTION refresh_mv_songs_free_now()` для cron
- **FR-006**: удалён `Song.totalCount()` (dead code).
- **FR-007 ОТМЕНЁН**: `Song.loadAuthorSongCounts()` используется в `PublicApiController.kt:443` —
  follow-up на `Author.loadAuthorTilesWithCounts` (Pass 286).
- **FR-008**: миграция БД идемпотентная.
- **FR-009**: `StatBySong.refreshCache().freeNow` читает из `mv_songs_free_now`.
- **FR-010**: cron `*/5 * * * *` через `/etc/cron.d/refresh-mv-songs-free-now` на проде.

## Acceptance Criteria

- [x] **AC1**: миграция применена на admin-машине и на проде (`15 672` строк в MV).
- [x] **AC2**: EXPLAIN ANALYZE подтверждает ×56 ускорение (2 312 мс → 41 мс).
- [x] **AC3**: cron autostart на проде подтверждён через 5 минут после установки.
- [x] **AC4**: `bash tools/tracker-smoke-test.sh` — 8/8 PASS.
- [x] **AC5**: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — OK.
- [x] **AC6**: `./gradlew :karaoke-web:ktlintCheck` — OK.
- [x] **AC7**: `git grep "fun totalCount\|fun loadAuthorSongCounts"` в `Song.kt` = 0 (для totalCount).

## Связанные LiveDocs

- Architecture: ADR не требуется (миграция в рамках существующих практик).
- Domain: [livedocs/domain/catalog.md](../domain/catalog.md) (Song как AR с денормализованными счётчиками).

## Код

- **Миграция БД**: `deploy/karaoke-db/44_optimize_tbl_songs.sql` (идемпотентная)
- **Код**:
  - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt` — `refreshCache.freeNow` (JOIN → MV)
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — удалён `totalCount()`
- **Cron** (на проде `188.119.64.111`):
  - `/etc/cron.d/refresh-mv-songs-free-now` (`*/5 * * * *`)
  - `/root/.pgpass` (chmod 600)
  - `/var/log/karaoke/mv-refresh.log` (лог)

## История

- Создан: 2026-09-02 (Pass 297)
- Завершено: 2026-09-02 (rev.2 — cron на проде)
- Последнее обновление: 2026-09-02

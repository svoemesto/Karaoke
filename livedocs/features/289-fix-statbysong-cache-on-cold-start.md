---
status: Active
slug: 289-fix-statbysong-cache-on-cold-start
related:
  - ../features/187-site-traffic-anomaly-investigation.md
  - ../features/241-db-storage-perf-audit.md
  - ../features/248-authors-tiles-cache.md
  - ../features/286-author-song-counts-cache.md
  - ../domain/caching.md
  - ../architecture/observability.md
  - ../../specs/289-fix-statbysong-cache-on-cold-start/spec.md
  - ../../specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md
---

# 289 — Устранение блокирующего `StatBySong.refreshCache()` при cold-start (LiveDoc)

> Drill-down — [specs/289-fix-statbysong-cache-on-cold-start/spec.md](../../specs/289-fix-statbysong-cache-on-cold-start/spec.md).
> Формат логов — [specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md](../../specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md).

## Что делает

`StatBySong.refreshCache()` в `karaoke-web` пересчитывал счётчики песен (`total`, `collection`, `freeNow`, `subscriptionOnly`, `inWork`) для главной страницы и Закромов через **3 full-scan запроса к `tbl_songs` (~12 сек на 23k записей)**. Синхронный вызов из `ensureCacheInitialized()` на первом HTTP-запросе после cold-start **блокировал HTTP-тред** на эти 12 сек. Это могло вызывать зависания сайта (например, после одобрения задания редактора, если запрос попадал на cold-start).

**2 направления**:

1. **Async cold-start refresh** через `ScheduledExecutorService` (single-thread, daemon) в `companion object StatBySong`. `ensureCacheInitialized()` запускает refresh в фоне через `AtomicBoolean.compareAndSet(false, true)` (single-flight guard). HTTP-тред возвращает fallback (0) за **< 100 мс** вместо блокировки 12 сек.

2. **Денормализация из `tbl_authors`** (Variant A): `total` и `collection` берутся из предрассчитанных `tbl_authors.total_songs_count` / `ready_songs_count` (specs/286) через `SUM(...) WHERE skip = false` (~2 мс вместо ~3 сек). `freeNow` остаётся через JOIN с `tbl_authors` (~2 сек).

**Итого `refreshCache()`** в фоне: **~2.1 сек** (vs 12 сек до фикса, ускорение 6×). В фоне HTTP-треды получают fallback.

## Семантическое замечание

Старое поведение считало **песни без тега SKIP** (22948). Новое — **песни не-skip авторов** (22892). Расхождение ~56 песен (skip-авторы с не-SKIP песнями, например Кино с фильмом «СПИД»). Обсуждено с пользователем.

## SLF4J-логирование (категория `infra.cache.statbysong`)

| Событие | Уровень | Когда |
|---------|---------|-------|
| `cache:coldStart triggering background refresh` | WARN | Первый запрос после cold-start (ровно 1 раз за refresh) |
| `cache:refreshed total=N collection=M freeNow=K subscriptionOnly=P inWork=Q durationMs=X` | INFO | После успешного refresh (sync или background) |
| `cache:refreshFailed error="..." exceptionClass=...` | WARN | При exception в `refreshCache()` |

## User Stories (краткий список)

- **US1** (P1): Cold-start refresh **не блокирует** HTTP-тред (FR-004..FR-008).
- **US2** (P1): Индекс `idx_songs_id_status_source_markers` — **признан избыточным** на текущем объёме (23k записей), seq scan быстрее index scan. Future enhancement при >100k.
- **US3** (P2): Single-flight guard через `AtomicBoolean refreshing` (FR-005) — 5 параллельных HTTP → 1 набор из 3 SQL.

## Acceptance Criteria

- **SC-001**: cold-start HTTP-запрос за < 100 мс.
- **SC-003**: 5 параллельных curl → `pg_log` имеет ровно 3 SQL `count(*)` (а не 15).
- **SC-007**: после фикса refreshCache ~2.1 сек в фоне (vs 12 сек до).

## Связанные LiveDocs

- Domain: [caching.md](../domain/caching.md), [monitoring.md](../domain/monitoring.md).
- Feature: [286-author-song-counts-cache.md](286-author-song-counts-cache.md) — `total_songs_count` / `ready_songs_count` в `tbl_authors`.
- Architecture: [observability.md](../architecture/observability.md).
- Runbook: [docs/ops/log-correlation.md](../../docs/ops/log-correlation.md).

## Код

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt` — async refresh, AtomicBoolean, SLF4J, denorm из `tbl_authors`.
- `deploy/karaoke-db/44_author_song_counts.sql` — `tbl_authors.total_songs_count` / `ready_songs_count` (specs/286, не эта фича).

## История

- Создан: 2026-09-01 (после находки hotspot через фичу 288: `pg_log` показал `duration: 4655 ms` для `select count(DISTINCT id) from tbl_songs where id_status >= 6 AND ...`).
- Реализация: Variant A (денормализация из `tbl_authors`, а не индекс — см. research.md D-1..D-4).
- Последнее обновление: 2026-09-01 (deploy + валидация).
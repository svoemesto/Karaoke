---
status: Active
slug: 242-db-sync-batch-worker
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/242-db-sync-batch-worker/spec.md
  - 241-db-storage-perf-audit
---

# 242 — Батч-синхронизация sync-записей в KaraokeProcessWorker (LiveDoc)

> Drill-down — [specs/242-db-sync-batch-worker/spec.md](../../specs/242-db-sync-batch-worker/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md).

## Что делает

Устраняет N+1 в sync-цикле `KaraokeProcessWorker.doStart()` (строки 994-1106):
1 SELECT sync + N SELECT LOCAL + N DELETE REMOTE → 1 + 2*(N/chunk) запросов.

## Эффект

* 100 sync-записей: **201 SQL → ≤ 11 SQL** (снижение в 18×).
* 1000 sync-записей: 2001 → ≤ 82 SQL.

## Реализация

* Helper `processRemoteSongsSyncBatch` + `applyRenderSideEffect`.
* `KaraokeDbTable.deleteIn` (существующий) — батч-Delete через PostgreSQL `Array`.
* chunk: SELECT=25 (`SongSyncTarget.rowChunkSize`), DELETE=200 (`SyncRegistry.DELETE_CHUNK_SIZE`).
* `tags = "RENDER"` side-effect — per-record (не батчится).

## Admin-only

Endpoint вызывается только на admin-машине (karaoke-app). На проде (karaoke-web) НЕ влияет.
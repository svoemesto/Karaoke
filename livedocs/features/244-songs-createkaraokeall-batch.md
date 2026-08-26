---
status: Active
slug: 244-songs-createkaraokeall-batch
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/244-songs-createkaraokeall-batch/spec.md
  - 241-db-storage-perf-audit
---

# 244 — Батч-загрузка песен в getSongsCreateKaraokeAll (LiveDoc)

> Drill-down — [specs/244-songs-createkaraokeall-batch/spec.md](../../specs/244-songs-createkaraokeall-batch/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md).

## Что делает

Устраняет N+1 в `ApiController.getSongsCreateKaraokeAll` (строки 3664-3778):
`ids.forEach { Song.loadFromDbById }` → `loadSongsBatch(ids.chunked(25))`.

## Эффект

* 100 ID: **100 SQL → ≤ 5 SQL** (~20× снижение).
* Latency 5-15 сек → ≤ 3 сек на 100 ID (cold path).
* `.distinct()` — убирает дублирующие INSERT в `tbl_processes`.
* `result = ids.isNotEmpty()` — исправлен баг (было `result = true` без проверки).

## Admin-only

Endpoint `POST /api/songs/createkaraokeall` доступен только в karaoke-app (admin).
На проде karaoke-web не используется.

## Реализация

* `loadSongsBatch(ids, database, storageService, storageApiClient): Map<Long, Song>` — приватный helper в `ApiController`.
* `SELECT_CHUNK_SIZE = 25` в `companion object` (validated by `SongSyncTarget.rowChunkSize`).
* Side-effect `KaraokeProcess.createProcess` остаётся per-record (атомарность).
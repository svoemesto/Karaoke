---
status: Active
slug: 241-db-storage-perf-audit
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/241-db-storage-perf-audit/spec.md
  - 242-db-sync-batch-worker
  - 243-db-table-schema-cache
  - 244-songs-createkaraokeall-batch
  - 245-storage-download-streaming
---

# 241 — Аудит производительности БД и хранилища (prod) (LiveDoc)

> Drill-down — [specs/241-db-storage-perf-audit/spec.md](../../specs/241-db-storage-perf-audit/spec.md).

## Что делает

Каталог hotspots производительности в коде karaoke (PostgreSQL + MinIO).
Аналитический документ: не фиксит код, а собирает все места, где есть N+1,
full-scan, reflection-heavy, OOM-риск и пр. — с file:line, severity (P0/P1/P2)
и предложенным решением.

## Tier-1 фичи (P0, все реализованы в Pass 241)

* **[242-db-sync-batch-worker](242-db-sync-batch-worker.md)** — N+1 в sync-цикле `KaraokeProcessWorker` (201 → 11 SQL).
* **[243-db-table-schema-cache](243-db-table-schema-cache.md)** — кеш `information_schema.columns` (100 → 1 SQL).
* **[244-songs-createkaraokeall-batch](244-songs-createkaraokeall-batch.md)** — N+1 в `ApiController.getSongsCreateKaraokeAll` (100 → 5 SQL).
* **[245-storage-download-streaming](245-storage-download-streaming.md)** — streaming вместо `readAllBytes()` (OOM-free 500 MB).

## Tier-2/Tier-3 (backlog)

См. spec.md, раздел A.5. Каждый пункт будет отдельной фичей.
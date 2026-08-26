---
status: Active
slug: 248-authors-tiles-cache
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/248-authors-tiles-cache/spec.md
  - 241-db-storage-perf-audit
---

# 248 — Кеш для /api/public/authors-tiles (LiveDoc)

> Drill-down — [specs/248-authors-tiles-cache/spec.md](../../specs/248-authors-tiles-cache/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md) — Tier-2 / FR-105.

## Что делает

TTL-кеш (30 мин) для публичной плитки авторов. Сейчас на каждый запрос — 2
тяжёлых full-scan к `tbl_songs` (DISTINCT + GROUP BY). С кешем — 1 cold start + cache hits.

## Effect

* Warm path latency **< 50 мс** (vs 200-500 мс baseline).
* ≥ 70% снижение SQL к `tbl_songs` в `pg_log` за 24 ч.
* Cache hit rate > 90% при типичной SPA-навигации.

## Реализация

* `companion object` в `PublicApiController`:
  * `CACHE_TTL_MS = 30 * 60 * 1000L`
  * `data class CachedAuthorsTiles(value, expiresAtMs)`
  * `ConcurrentHashMap<String, CachedAuthorsTiles> authorsTilesCache`
* `getCachedAuthorsTiles(scope, onlyPublished, loadFn)` — helper с cache check,
  TTL refresh, dirty-flag инвалидация через `StatBySong.consumeDirty()`.
* Kill-switch через `KaraokeProperties.getBoolean("karaoke.public.authors-tiles-cache.enabled", default=true)`.

## PROD-критичная

Endpoint вызывается на главной странице «Закромов» и при навигации по фильтрам
(`scope=main/special/all`). На prod (`karaoke-web`) — публичный endpoint.

## Backward-compat

Signature endpoint'а НЕ меняется: `@GetMapping("/authors-tiles")` с параметрами
`scope`, `request`. Клиенты (`karaoke-public`) работают без изменений.
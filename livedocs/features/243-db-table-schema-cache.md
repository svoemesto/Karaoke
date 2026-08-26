---
status: Active
slug: 243-db-table-schema-cache
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/243-db-table-schema-cache/spec.md
  - 241-db-storage-perf-audit
---

# 243 — Schema-cache в KaraokeDbTable.loadList (LiveDoc)

> Drill-down — [specs/243-db-table-schema-cache/spec.md](../../specs/243-db-table-schema-cache/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md).

## Что делает

Thread-safe TTL-кеш (`ConcurrentHashMap<Pair<tableName, db.name>, …>`, TTL=1ч) для метода
`KaraokeDbTable.columns()`. Устраняет дополнительный SQL к `information_schema.columns`
на каждый `loadList` с `ignoreUseInList=false`.

## Эффект

* 100 повторных `loadList`: **100 SQL → 1 SQL** к `information_schema.columns`.
* Headoverhead: ~100 KB на 24 таблицы.
* 41 caller `loadList` — **без изменений** (backwards-compatible).

## Реализация

* `SchemaCacheEntry(columnNames, expiresAtMs)` — immutable data class.
* `isSchemaCacheEnabled()` через `KaraokeProperties.getBoolean("karaoke.db.schema_cache.enabled", default=true)`.
* `@Suppress("unused") fun invalidateSchemaCache(tableName?, database?)` — публичный API для DDL-миграций.
* НЕ сохраняет пустые/ошибочные результаты (FR-003) — cache miss повторит попытку.
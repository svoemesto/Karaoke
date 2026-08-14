# ADR-0001: Raw JDBC without JPA/Hibernate for DB access

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, ratified in Constitution § II)
* **Deciders**: Karaoke team

> **Russian version**: [`../../livedocs/architecture/decisions/0001-raw-jdbc.md`](../../livedocs/architecture/decisions/0001-raw-jdbc.md)

## Context

Karaoke project works with **PostgreSQL 16** directly, without ORM. Initially
(2023-2024) a conscious choice was made against Spring Data JPA / Hibernate
for several reasons:

1. **Control over SQL**. The team writes SQL manually — this is important for
   features like "Sync LOCAL↔SERVER" and `recordhash` triggers, which don't
   fit well with JPA/Hibernate (dirty cache, lazy loading, N+1).
2. **Performance**. DB queries are in the hot path (sync, queue, `SiteAdmin.vue`,
   statistics). On prod ~18k records in `tbl_settings`; O(n²) record comparison
   takes 3+ minutes; O(n) takes seconds.
3. **Direct control over `java.sql.Connection`** (lifecycle, isolation,
   closing). Critical for queue with `ProcessBuilder` and restarts.
4. **Minimize dependencies**. Spring Boot already provides JdbcTemplate,
   which gives exactly what's needed (no ORM overhead).

## Decision

We use **raw JDBC** through:

- `org.springframework.jdbc.core.JdbcTemplate` for most queries.
- `KaraokeConnection` (singleton) — facade over `Connection.local()/remote()/virtual()`.
- `recordhash` — md5 of canonicalized table row, computed by triggers in DB.

**Forbidden**: Spring Data JPA, Hibernate, Exposed, any other ORM.
**Reason for prohibition is fixed** in Constitution § II (NON-NEGOTIABLE).

### Pattern "O(n) not O(n²)"

All mass comparisons go through `associateBy { it.id }`:

```kotlin
// ✅ CORRECT (O(n))
val localById = localList.associateBy { it.id }
val serverById = serverList.associateBy { it.id }
localById.keys.intersect(serverById.keys).forEach { id ->
  if (localById[id]!!.recordhash != serverById[id]!!.recordhash) {
    pushToServer(localById[id]!!)
  }
}

// ❌ WRONG (O(n²) — 3+ minutes on 18k records!)
localList.forEach { local ->
  serverList.none { it.id == local.id && it.recordhash == local.recordhash }
}
```

### Batch loading, not one-by-one

```kotlin
// ✅ CORRECT — 1 query
jdbcTemplate.queryForObject(
  "SELECT * FROM tbl_settings WHERE id IN ($idsCsv)",
  rowMapper
)

// ❌ WRONG — N+1 queries
ids.forEach { id ->
  jdbcTemplate.queryForObject("SELECT * FROM tbl_settings WHERE id = ?", ...)
}
```

## Consequences

**Positive**:
- Full control over SQL and execution plans (`EXPLAIN ANALYZE` used often).
- No N+1 / dirty cache / lazy loading issues.
- Easier performance debugging (`pg_stat_statements` shows each query).
- Fewer dependencies → less supply-chain risk.
- `recordhash` triggers in SQL give O(n) diff between DBs.

**Negative**:
- More boilerplate (`ResultSet → DTO` mapping manually).
- No query auto-generation.
- Developer must know SQL/PostgreSQL.
- Migrations are pure SQL, without Flyway/Liquibase (though can be added).

**Neutral**:
- Need discipline: every query in `try-with-resources` (or explicit
  `close()` in `finally`) to not leak Connection (see `specs/091-fix-connection-leak`,
  LiveDoc `091`).

## Alternatives Considered

- **Spring Data JPA + Hibernate**: rejected — dirty cache, sync debugging
  complexity, poor performance on 18k records.
- **Exposed (JetBrains)**: considered — Kotlin-native, but no benefit over
  JdbcTemplate and added dependency.
- **JOOQ**: considered — type-safe SQL, but useful only with hundreds of
  unique queries; we have ~50, no ROI.
- **Raw `DriverManager` without Spring**: rejected — lose DI, AOP, testability.
- **Hibernate with explicit flush/clear**: hypothetically workable, but
  adds significant complexity without clear benefit.

## References

- [Constitution § II](.specify/memory/constitution.md) — NON-NEGOTIABLE principle.
- [livedocs/architecture/data-sync.md](../../livedocs/architecture/data-sync.md) — `recordhash` and
  `SyncRegistry` (practical application of the principle).
- [livedocs/features/087-fix-shared-db-connection.md](../../livedocs/features/087-fix-shared-db-connection.md) —
  ThreadLocal Connection isolation (consequence of fix 087).
- [livedocs/features/091-fix-connection-leak.md](../../livedocs/features/091-fix-connection-leak.md) —
  JDBC leak from one-shot threads (consequence of fix 087).
- [Constitution § III](.specify/memory/constitution.md) — mandatory participation
  in `SyncRegistry` for bidirectional sync.
# Local ADR-0002: Exception handling pattern in `KaraokeDbTable.save()`

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: Karaoke team

> **Russian version**: [../../../livedocs/architecture/decisions/local-0002-save-exception-handling.md](../../../livedocs/architecture/decisions/local-0002-save-exception-handling.md)
>
> **Note**: this is **local** ADR — describes specific pattern in code
> (not global architecture decision).

## Context

`karaoke-app/.../KaraokeDbTable.kt` — single save() point for all entities.
Used in hundreds of places:

```kotlin
songList.forEach { song ->
    song.saveToDb()  // inside: KARAOKE_CONNECTION.getConnection().save()
}
```

Problem: when failure occurs (dead-lock, broken FK, network error),
exception is thrown up — and:
- Next records in forEach are NOT saved.
- UI receives 500 (if from HTTP).
- Worker stops `doStart()` cycle (see [087-fix-shared-db-connection](../../features/087-fix-shared-db-connection.md)).

Historically — **different handling** in different places:
- `KaraokeProcess.save()` — propagates `SQLException` (correct).
- `getCountWaiting()` — swallows, returns 0 (see [088](../../features/088-fix-queue-swallowed-errors.md)).
- `getProcessesToStart()` — swallows, returns empty map.

This caused **two different scenarios** on same DB-failure:
- If save() fails → worker goes into retry.
- If getCountWaiting() fails → worker silently spins without doing anything.

## Decision

**Unified pattern** for all DB access within worker:

```kotlin
// ✅ CORRECT — unified handling (retry trigger)
class KaraokeProcessWorker {
    fun doStart() {
        while (true) {
            try {
                val processes = getProcessesToStart()  // may throw
                processes.forEach { process ->
                    try {
                        process.saveToDb()  // may throw
                        // ...
                    } catch (e: Exception) {
                        logger.error("Save failed for ${process.id}", e)
                        // Don't propagate — process will be retried via recordhash
                    }
                }
                Thread.sleep(1000)
            } catch (e: Exception) {  // worker-level
                logger.error("doStart failed, retry in 5s", e)
                Thread.sleep(5000)
            }
        }
    }
}

// ❌ WRONG
fun getCountWaiting(): Int {
    try {
        return jdbcTemplate.queryForObject("SELECT count...", Integer::class)
    } catch (e: Exception) {
        return 0  // SWALLOWS — wrong
    }
}
```

**Rules**:

1. **All methods that return data MUST propagate exceptions up**.
2. **Worker doStart()** catches Exception → logs → retries (see
   [architecture/dual-db-access.md](../../dual-db-access.md) — retry 5x).
3. **UI (webvue3)** catches Exception → shows friendly error.
4. **Catch in prod (HTTP)** — for diagnostic logging, NOT for fallback.
5. **NO silent fallbacks in service layer** — this hides problems.

## Consequences

### Positive
- Unified handling (not "problem manifests randomly").
- Worker retry works predictably (5 attempts, exponential backoff).
- One save() failure in forEach doesn't stop entire cycle (graceful skip).
- Logs show **complete picture** of errors, not "silent 0".

### Negative
- More try/catch in code.
- Need discipline — every new DB method MUST propagate.

### Neutral
- Logs can be noisy (if DB falls often). In worker — `WARN` after 5 retries,
  then `ERROR`.

## Alternatives Considered

- **Catch-all in service, fallback to empty/0/null**: rejected — diagnostic-friendly
  fallback is false-friendly.
- **Panic-style — all throws mandatory, many try-catch in forEach**:
  this is the current recommended approach.

## References

- [architecture/dual-db-access.md](../../dual-db-access.md) — retry behavior.
- [architecture/observability.md](../../observability.md) — where to log.
- [features/088-fix-queue-swallowed-errors.md](../../features/088-fix-queue-swallowed-errors.md) — original
  diagnosis of "silent fallbacks".
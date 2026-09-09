# Circuit Breaker State Contract (Pass 351)

**Phase**: 1 — Design & Contracts
**Date**: 2026-09-09

## Endpoint: `GET /api/health/circuit-breaker` (P3, optional)

**Auth**: admin (`permitAll`, как `/api/health/cacheStats`).
**HTTP method**: GET
**Content-Type**: application/json
**Side effects**: none (read-only).

### Response 200 OK

```json
{
  "state": "CLOSED",
  "failureCount": 0,
  "lastFailureAt": null,
  "totalSuccesses": 12300,
  "totalNetworkFailures": 5,
  "threshold": 5,
  "cooldownSeconds": 30,
  "timeoutSeconds": 5
}
```

### Schema

```typescript
type CircuitBreakerResponse = {
  state: "CLOSED" | "HALF_OPEN" | "OPEN";
  failureCount: number;        // consecutive failures since last success
  lastFailureAt: number | null; // epoch ms
  totalSuccesses: number;       // lifetime
  totalNetworkFailures: number; // lifetime
  threshold: number;            // failures to OPEN
  cooldownSeconds: number;     // delay before HALF_OPEN
  timeoutSeconds: number;      // per-call timeout
};
```

### State semantics

| State | Meaning | Behavior |
|---|---|---|
| `CLOSED` | Normal operation. All calls go to MinIO. | All `decorate(...)` return Allow. |
| `OPEN` | Remote is degraded. Last `threshold` calls failed. | `decorate(...)` returns FastFail. No MinIO call. After `cooldownSeconds`, transitions to HALF_OPEN on next call. |
| `HALF_OPEN` | Trial. Single call probes the remote. | First call after OPEN → HALF_OPEN. Success → CLOSED. Failure → OPEN (reset cooldown). Concurrent calls see OPEN → FastFail. |

### Edge cases

- **Cold start**: state=CLOSED, failureCount=0, totalSuccesses=0, totalNetworkFailures=0.
- **No failures ever**: lastFailureAt=null, failureCount=0.
- **State transition logging**: each transition (CLOSED→OPEN, OPEN→HALF_OPEN, HALF_OPEN→CLOSED, HALF_OPEN→OPEN) emits `cache:circuit:state` log at INFO.

---

## Endpoint: `POST /api/health/circuit-breaker/reset` (P3, optional)

**Auth**: admin.
**HTTP method**: POST
**Content-Type**: application/json
**Side effects**: yes — resets `state` to CLOSED, `failureCount` to 0, `openedAtMs` to 0.

### Response 200 OK

```json
{
  "previousState": "OPEN",
  "newState": "CLOSED",
  "resetAt": "2026-09-09T15:35:00Z"
}
```

### Use case

Admin замечает, что MinIO recovered, но cooldown ещё не истёк. Reset ускоряет recovery.

---

## Cache Stats Contract (extended from Pass 344)

### Endpoint: `GET /api/health/cacheStats`

Response 200 OK (с Pass 351 дополнениями):

```json
{
  "local": {
    "entries": 1234,
    "hits": 5678,
    "misses": 42,
    "hitRatio": 0.99,
    "ttlSeconds": 300,
    "evictions": 10,
    "networkFailures": 5,
    "circuitBreaker": {
      "state": "CLOSED",
      "failureCount": 0,
      "lastFailureAt": null,
      "totalSuccesses": 12300,
      "totalNetworkFailures": 5,
      "threshold": 5,
      "cooldownSeconds": 30,
      "timeoutSeconds": 5
    }
  },
  "remote": {...}
}
```

**Backwards compatible**: `entries`, `hits`, `misses`, `hitRatio`, `ttlSeconds`, `evictions` — existing. New: `networkFailures`, `circuitBreaker`. Existing clients игнорируют new fields.

---

## Log Events Contract

### SLF4J category: `infra.cache.storage` (existing, Pass 344)

| Event | Level | When | Fields |
|---|---|---|---|
| `cache:miss` (Pass 344) | INFO | Cache miss + loader called | key, bucket, fileName, source, operation, durationMs |
| `cache:hit` (Pass 344) | INFO | Cache hit | key, bucket, fileName, source |
| `cache:evicted` (Pass 344) | INFO | Entry evicted | reason, count |
| `cache:network:failure` (NEW) | WARN | Per-call network failure | key, bucket, fileName, error, durationMs, source |
| `cache:circuit:state` (NEW) | WARN | State transition | from, to, failureCount, openedAtMs |

Примеры:
```
2026-09-09 15:34:12 [INFO ] infra.cache.storage - cache:miss key="REMOTE:karaoke/song-1.flac" source=REMOTE durationMs=80
2026-09-09 15:34:12 [WARN ] infra.cache.storage - cache:network:failure key="REMOTE:karaoke/song-1.flac" error="SocketTimeoutException" durationMs=5000
2026-09-09 15:34:13 [WARN ] infra.cache.storage - cache:circuit:state from=CLOSED to=OPEN failureCount=5
```

---

## Internal StorageAPI contract

### `StorageApiClient.fileExists` (existing, modified)

**Signature** (unchanged):
```kotlin
fun fileExists(bucketName: String, fileName: String): Boolean
```

**Behavior change** (Pass 351):
- Wraps MinIO call in `StorageCircuitBreaker.decorate`.
- Timeout: `storage.file-exists-timeout-seconds` (5s default).
- On circuit OPEN: returns `false` immediately.
- On timeout: returns `false` + `cache:network:failure` log.
- On success: returns `true` (if file exists) or `false` (if not).
- On any exception: returns `false` + `cache:network:failure` log.

### `StorageApiClient.fileIsActual` (existing, modified)

**Signature** (unchanged): `fun fileIsActual(bucketName: String, fileName: String, pathToFileOnDisk: String): Boolean`

**Behavior change**: same pattern as `fileExists`.

### `StorageApiClient.getFileInfo` (existing, modified)

**Signature** (unchanged): `fun getFileInfo(bucketName: String, fileName: String): Mono<StorageFileInfo>`

**Behavior change**:
- On circuit OPEN: returns `Mono.empty()`.
- On timeout: returns `Mono.empty()`.
- On success: returns `Mono.just(StorageFileInfo(...))`.

---

## Resolved clarifications

- ✅ In-memory only (per Q1)
- ✅ All file-* methods (per Q2)
- ✅ Half-open probe (per Q3)

## Ready for /speckit.tasks

Contracts defined. Backwards compatibility maintained. Ready to proceed to tasks.md generation.

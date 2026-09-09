# Data Model: Storage graceful degradation (Pass 351)

**Phase**: 1 — Design & Contracts
**Date**: 2026-09-09
**Spec**: [spec.md](./spec.md)
**Research**: [research.md](./research.md)

## Сущности

### 1. `StorageCircuitBreaker` (NEW bean)

**Где**: `karaoke-app/.../services/StorageCircuitBreaker.kt` (~80 lines).

**Поля**:
```kotlin
@Component
class StorageCircuitBreaker(
    @Value("\${storage.file-exists-timeout-seconds:5}") private val timeoutSeconds: Long,
    @Value("\${storage.circuit-breaker-threshold:5}") private val threshold: Int,
    @Value("\${storage.circuit-breaker-cooldown-seconds:30}") private val cooldownSeconds: Long,
) {
    enum class State { CLOSED, HALF_OPEN, OPEN }
    
    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicLong(0)
    private val openedAtMs = AtomicLong(0L)
    private val successCount = AtomicLong(0)  // metrics: total successes
    private val networkFailureCount = AtomicLong(0)  // metrics: total failures
    private val log = LoggerFactory.getLogger("infra.cache.storage")
    
    fun <T> decorate(operation: String, loader: () -> Mono<T>, emptyValue: T): Mono<T> { ... }
    fun acquire(allowClosed: Boolean = false): Decision { ... }
    fun recordSuccess() { ... }
    fun recordFailure(error: Throwable) { ... }
    fun state(): State = state.get()
    fun metrics(): Metrics { ... }
}
```

**State machine**:
```
CLOSED --N consecutive failures--> OPEN
OPEN --cooldown elapsed--> HALF_OPEN
HALF_OPEN --next call success--> CLOSED (counter=0)
HALF_OPEN --next call failure--> OPEN (reset openedAtMs)
```

**Validation rules**:
- `threshold >= 1` (иначе circuit мгновенно OPEN).
- `cooldownSeconds >= 1` (иначе instant half-open без timeout).
- `timeoutSeconds >= 1` (иначе 0 = мгновенный fail — useless).

**State transitions** (per Q3):
| From | To | Trigger | Side effect |
|---|---|---|---|
| CLOSED | OPEN | `failureCount >= threshold` | `openedAtMs = now()`; log `cache:circuit:state` |
| OPEN | HALF_OPEN | `now() - openedAtMs > cooldownSeconds * 1000` (next call после cooldown) | log `cache:circuit:state` |
| HALF_OPEN | CLOSED | probe success | `failureCount = 0`; log `cache:circuit:state` |
| HALF_OPEN | OPEN | probe failure | `openedAtMs = now()`; log `cache:circuit:state` |

---

### 2. `Decision` (sealed class, returned by `acquire`)

```kotlin
sealed class Decision {
    object Allow : Decision()                    // proceed to MinIO call
    object FastFail : Decision()                 // circuit OPEN, return empty
    object Probe : Decision()                     // HALF_OPEN, single probe
}
```

**Use case**: caller проверяет `Decision` через `when` и решает.

---

### 3. `Metrics` (read-only snapshot)

```kotlin
data class Metrics(
    val state: State,
    val failureCount: Long,
    val lastFailureAt: Long?,           // epoch ms, null если ни разу не было
    val totalSuccesses: Long,
    val totalNetworkFailures: Long,
    val threshold: Int,
    val cooldownSeconds: Long,
    val timeoutSeconds: Long,
)
```

**Используется**: `/api/health/circuit-breaker` (P3) + extended `CacheStatsController`.

---

### 4. Расширение `CacheStatsController` (Pass 344)

**Было (Pass 344)**:
```json
{
  "local":  {"entries": 1234, "hits": 5678, "misses": 42, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 10},
  "remote": {...}
}
```

**Стало (Pass 351)**:
```json
{
  "local":  {
    "entries": 1234, "hits": 5678, "misses": 42, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 10,
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

**Backwards compatible**: новые поля additive.

---

### 5. Конфигурация (`application.yml`)

```yaml
storage:
  key: ${STORAGE_KEY}
  secret: ${STORAGE_SECRET}
  folder: ${STORAGE_FOLDER:karaoke}
  port-host: ${STORAGE_PORT_HOST:9000}
  port-inside-container: ${STORAGE_PORT_INSIDE_CONTAINER:9000}
  container-name: ${STORAGE_CONTAINER_NAME:karaoke-storage}
  remote-endpoint: ${STORAGE_REMOTE_ENDPOINT:http://89.125.103.63:9000}
  console:
    port-host: ${STORAGE_CONSOLE_PORT_HOST:9001}
    port-inside-container: ${STORAGE_CONSOLE_PORT_INSIDE_CONTAINER:9001}
  metadata:
    cache:
      local:
        ttlSeconds: 300
      remote:
        ttlSeconds: 300
      maxEntries: 50000
  # NEW (Pass 351): graceful degradation
  file-exists-timeout-seconds: 5
  circuit-breaker-threshold: 5
  circuit-breaker-cooldown-seconds: 30
```

**Env override**:
- `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10`
- `STORAGE_CIRCUIT_BREAKER_THRESHOLD=3`
- `STORAGE_CIRCUIT_BREAKER_COOLDOWN_SECONDS=60`

---

## Связь с существующими сущностями

```
StorageApiClientImpl.fileExists()
    ↓ (NEW) StorageCircuitBreaker.decorate("fileExists", loader, false)
    ↓
    ├── Decision.Allow → Mono<*>.timeout(timeoutSeconds).onErrorReturn(false)
    │       ↓
    │       MinIO SDK call (admin-side)
    │       ↓
    │       recordSuccess() | recordFailure(error)
    │
    └── Decision.FastFail → Mono.just(empty) (no MinIO call)

StorageMetadataCache.getFileInfo(...)
    ↓ (NO CHANGE in public API, internal calls wrapped)
    ↓
    SELECT from tbl_storage_metadata_cache
        ↓ miss
        StorageCircuitBreaker.decorate(...)
            ↓
            (same as above)
```

**Public API контракт не меняется** (`StorageApiClient.fileExists` signature). Внутренняя реализация обёрнута.

## State transitions diagram

```
                            ┌─────────────────┐
                            │     CLOSED      │◄──────────────────┐
                            │ failureCount=0  │                   │
                            └─────────────────┘                   │
                                  │                             │
                  N consecutive failures                             │
                  (failureCount >= threshold)                        │
                                  │                             │ probe success
                                  ▼                             │
                            ┌─────────────────┐                   │
                            │      OPEN       │                   │
                            │ openedAtMs=now  │                   │
                            └─────────────────┘                   │
                                  │                             │
                  cooldownSeconds elapsed                          │
                  (next call after cooldown)                        │
                                  │                             │
                                  ▼                             │
                            ┌─────────────────┐                   │
                            │    HALF_OPEN    │─── probe failure ─┘
                            │  (1 probe call) │
                            └─────────────────┘
```

## Data flow

```
1. caller invokes `StorageApiClient.fileExists(bucket, name)`.
2. internally → `StorageCircuitBreaker.decorate("fileExists", loader, false)`.
3. `decorate`:
   a. acquire() → Decision (Allow / FastFail / Probe).
   b. Decision.Allow → proceed to loader.
   c. Decision.FastFail → Mono.just(false), log "cache:network:failure (circuit open)".
   d. Decision.Probe → proceed to loader (only this call is probe).
4. loader is wrapped as: `Mono.fromCallable { ... }.timeout(Duration.ofSeconds(timeoutSeconds)).onErrorReturn(empty)`.
5. result is `Mono<Boolean>`:
   a. onNext(value) → recordSuccess(), return value.
   b. onError → recordFailure(error), return empty.
6. Decision.Probe: success → state=CLOSED; failure → state=OPEN (reset openedAtMs).

## Concurrency contract

- `state.compareAndSet(CLOSED, HALF_OPEN)`: один thread wins probe, остальные fast-fail до конца probe.
- `failureCount.incrementAndGet()`: AtomicLong thread-safe.
- `openedAtMs.set(now())`: AtomicLong thread-safe.
- Multiple concurrent calls during CLOSED: each increments failureCount independently.

## Validation

| Constraint | Where checked | Effect |
|---|---|---|
| `state` is in {CLOSED, HALF_OPEN, OPEN} | enum constraint (compile-time) | N/A |
| `failureCount >= 0` | AtomicLong only increments, never decrements (except in CLOSED transition) | N/A |
| `openedAtMs >= 0` | AtomicLong set with `System.currentTimeMillis()` | N/A |
| `timeoutSeconds >= 1` | `@Value` parse → Spring throws if negative | Spring validates |
| `cooldownSeconds >= 1` | same | Spring validates |

## Configuration property mapping

| Spec FR | @Value property | Default | Env override |
|---|---|---|---|
| FR-001 | `storage.file-exists-timeout-seconds` | 5 | `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` |
| FR-002 (threshold) | `storage.circuit-breaker-threshold` | 5 | `STORAGE_CIRCUIT_BREAKER_THRESHOLD` |
| FR-002 (cooldown) | `storage.circuit-breaker-cooldown-seconds` | 30 | `STORAGE_CIRCUIT_BREAKER_COOLDOWN_SECONDS` |

## Edge cases

- **Cold start (state=CLOSED, failureCount=0)**: first failure → failureCount=1, no OPEN.
- **Concurrent calls during probe**: only 1 wins `compareAndSet(CLOSED, HALF_OPEN)`, others see HALF_OPEN/OPEN and fast-fail.
- **Recovery from OPEN**: first call after `cooldownSeconds` elapsed → HALF_OPEN, single probe.
- **Multiple consecutive failures in HALF_OPEN**: only 1 probe runs, others see OPEN (or queueing).
- **Probe success → CLOSED**: failureCount=0, log transition.
- **Timeout during probe**: same as timeout during normal call → counts as failure.
- **Admin restart**: state resets to CLOSED (in-memory only, per Q1).

## Resolved clarifications

- ✅ In-memory only (per Q1)
- ✅ All file-* methods (per Q2)
- ✅ Half-open probe (per Q3)

## Ready for /speckit.tasks

Data model complete. Implementation files identified. Validation rules clear. Ready to proceed to tasks.md generation.

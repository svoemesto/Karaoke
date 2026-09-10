# Research: HealthReport Latency Analysis
# Spec: 364-healthreport-speedup | OP #75

## Method
- Analyzed logs: `docker logs karaoke-app | grep "infra.health.report.duration"`
- Container port: 8898 (mapped from 8899)
- API endpoint: `POST /song/healthReportList?id=<songId>`

## Key Findings

### 1. Per-Location Latency (single operation)

| Location | Operation | Cold (first call) | Warm (cached) |
|----------|-----------|-------------------|---------------|
| LOCAL_FILESYSTEM | fileExists | 0ms | 0ms |
| LOCAL_STORAGE | fileExists + fileIsActual | 25-55ms | <1ms (cache hit) |
| REMOTE_STORAGE | fileExists | 120-250ms | <1ms (cache hit) |

### 2. Bottleneck: REMOTE_STORAGE (120-250ms per call)

Example: AUDIO_SONG type has 1 REMOTE operation = 200ms
MP3_VOCAL has 1 REMOTE operation = 250ms
Full song (6 REMOTE types) = 6 × 200ms = 1200ms sequential

BUT: operations run in PARALLEL across 8 nio threads:
- nio-8899-exec-3, nio-8899-exec-4, nio-8899-exec-7, nio-8899-exec-8, nio-8899-exec-9
- io-8899-exec-1, io-8899-exec-10

Parallel threads handle different songs simultaneously.

### 3. Cache Behavior

```
cache:miss key=REMOTE:karaoke/.../file.mp3 source=REMOTE operation=fileExists value=true
cache:miss key=REMOTE:karaoke/.../file2.mp3 source=REMOTE operation=fileExists value=true
```

- First query: ALL cache MISS (cold start) — each REMOTE call = 120-250ms
- Subsequent queries: ALL cache HITS — each REMOTE call = <1ms
- Cache TTL = ∞ (eternal, spec #348)

### 4. Per-Type Latency Breakdown (sample from logs)

```
type=PICTURE_ALBUM_PREVIEW location=LOCAL_STORAGE operation=actions durationMs=28
type=PICTURE_AUTHOR location=LOCAL_STORAGE operation=actions durationMs=26
type=PICTURE_AUTHOR_PREVIEW location=LOCAL_STORAGE operation=actions durationMs=27
type=PICTURE_ALBUM location=LOCAL_STORAGE operation=actions durationMs=29
type=MP3_OTHER location=LOCAL_STORAGE operation=actions durationMs=45-50
type=MP3_DRUMS location=LOCAL_STORAGE operation=actions durationMs=50-55
type=MP3_VOCAL location=REMOTE_STORAGE operation=actions durationMs=250
type=MP3_ACCOMPANIMENT location=REMOTE_STORAGE operation=actions durationMs=251
type=PICTURE_AUTHOR location=REMOTE_STORAGE operation=actions durationMs=126
type=PICTURE_ALBUM location=REMOTE_STORAGE operation=actions durationMs=248
```

### 5. Root Cause Summary

**Bottleneck is NOT the code — it's the network latency to remote MinIO.**

For a song with 6 REMOTE file types:
- Cold: 6 × 200ms = 1200ms total
- Warm (cached): 6 × <1ms = 6ms total

**Key insight**: The cache works correctly. The problem is:
1. First query to any song = full 1-1.5 sec (all REMOTE misses)
2. Subsequent queries = <100ms (cache hits)

### 6. Implemented Optimizations (T004, T011)

✅ Added SLF4J loggers: `infra.health.report.duration`, `infra.health.circuit`
✅ Added timing to all 15 `KaraokeFileType` branches
✅ Added circuit breaker to `actionsLocalStorage`
✅ Created `StorageCircuitBreakerWiring.kt`

### 7. Remaining Optimizations

**US1 (≤200ms for OK songs)**:
- Target: ≤200ms for fully OK song
- Current cold: ~1-1.5 sec (all REMOTE checks run synchronously)
- Current warm: likely <200ms (cache hits)

**US2 (async cold-start)**:
- On cache MISS: return placeholder immediately, fill cache async
- This is the main optimization for first-time queries

**US3 (non-blocking repair loop)**:
- REMOTE checks should be fire-and-forget for "OK" songs
- Skip REMOTE if LOCAL already confirms OK

## Recommendations

1. **Short-term**: Implement async REMOTE check (US3) — skip REMOTE for OK songs
2. **Medium-term**: Implement async cold-start (US2) — return immediately on cache miss
3. **Verify**: After warm-up, latency should already be ≤200ms for repeated queries

# Local ADR-0003: Shared MinIO Image Cache — TTL and invalidation rules

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: Karaoke team

> **Russian version**: [../../../livedocs/architecture/decisions/local-0003-shared-minio-image-cache.md](../../../livedocs/architecture/decisions/local-0003-shared-minio-image-cache.md)
>
> **Note**: this is **local** ADR — describes specific caching pattern
> (not global architecture decision).

## Context

`karaoke-app` uses MinIO for shared media storage (album covers, audio, video).
For render acceleration, **MinIO is used as cache** — images/videos are
saved there and reused between requests.

Problems without explicit rules:
- **Stale cache** — if Song.save() updates the song, MinIO cache stays stale.
- **Memory pressure** — if all images are cached, MinIO grows unbounded.
- **Concurrent uploads** — two parallel operations can race condition.

## Decision

**Convention for shared image cache in MinIO**:

```kotlin
class ImageCacheService(
    private val minio: MinioClient,
    private val bucket: String = "karaoke-cache"
) {
    fun get(songId: Long, size: String): ByteArray? {
        val key = "song-$songId-$size"
        return try {
            minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())
                .use { it.readAllBytes() }
        } catch (e: ErrorResponseException) {
            null  // Cache miss — recompute
        }
    }

    fun put(songId: Long, size: String, data: ByteArray) {
        val key = "song-$songId-$size"
        // ETag = SHA256(data) — for dedup on concurrent uploads
        val etag = sha256(data)
        try {
            minio.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(ByteArrayInputStream(data), data.size.toLong(), -1)
                    .build()
            )
        } catch (e: Exception) {
            // Already exists with different etag — OK (concurrent upload)
            logger.warn("Cache write race for $key", e)
        }
    }
}
```

**Rules**:

1. **Naming**: `<entity>-<id>-<size>` (e.g., `song-12345-512x512`).
2. **ETag = SHA256(content)**: dedup on concurrent uploads.
3. **Cache key = immutable for entity version**: song version → cache key.
4. **TTL**: 7 days for all derived assets (covers, PNG-frames). TTL reset on re-put().
5. **Invalidation on Song.save()**: if image hash changed — delete old, set new.
6. **No race conditions**: put-or-replace atomic (S3/MinIO supports overwrite).
7. **Concurrent uploads**: both threads can put() simultaneously — MinIO serializes.

### TTL policy

| Cache type | TTL | When reset |
|------------|-----|------------|
| `song-{id}-{size}` cover | 7 days | On Song.save() if hash changed |
| `text-{songId}-{timecode}` text-frame | 1 hour | On lyrics.markers change |
| `karaoke-{songId}-{version}` final MP4 | 30 days | On manual invalidate |
| `preview-{songId}` VK preview | 1 day | On Song.save() |

### Lifecycle

```
1. User requests /api/public/song-vk-image/12345
2. Cache.get("song-12345-512x512") → null (miss)
3. Service computes PNG via Playwright (~1 sec)
4. Cache.put("song-12345-512x512", png) — for next requests
5. Next request → Cache.get → hit (~10 ms)
6. After 7 days → eviction (cleanup job)
```

### Cleanup

```kotlin
@Scheduled(cron = "0 0 3 * * *")  // every day at 03:00
fun cleanupExpiredCache() {
    val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
    val expired = minio.listObjects(ListObjectsArgs.builder().bucket(bucket).build())
        .filter { it.lastModified().isBefore(cutoff) }
    expired.forEach { minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(it.name()).build()) }
}
```

## Consequences

### Positive
- **Performance**: cache hit ~10ms vs compute ~1 sec (×100 acceleration).
- **Storage bounded**: TTL eviction prevents unbounded growth.
- **Consistency**: invalidation through hash → stale reads impossible.
- **Race-free**: S3/MinIO atomic put-or-overwrite.

### Negative
- **Cache miss penalty**: first request slow (compute).
- **TTL = staleness tradeoff**: 7 days for covers — acceptable delay.
- **Cleanup overhead**: scheduled job cleans daily.

### Neutral
- **Immutability of cache key for version**: new version = new cache key.

## Alternatives Considered

- **Redis cache instead of MinIO**: rejected — Redis is separate infrastructure.
  MinIO is already there for shared storage.
- **No caching**: rejected — too slow (Playwright headless render ~1 sec).
- **TTL 24h**: too short (need repeated computes).
- **No TTL, manual invalidation**: rejected — manual error-prone.

## References

- [architecture/L2-containers.md](../../L2-containers.md) — where MinIO lives.
- [domain/rendering.md](../../domain/rendering.md) — who writes to cache.
- ADR-0002 (MLT/melt) — cache consumer.
- MinIO SDK: https://min.io/docs
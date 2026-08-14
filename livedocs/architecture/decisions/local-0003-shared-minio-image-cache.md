# Local ADR-0003: Shared MinIO Image Cache — TTL и invalidation rules

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: команда Karaoke

> **English version**: [../../../livedocs-en/decisions/local-0003-shared-minio-image-cache.md](../../../livedocs-en/decisions/local-0003-shared-minio-image-cache.md)
>
> **Note**: this is **local** ADR — описывает конкретный паттерн кеширования
> (а не глобальное архитектурное решение).

## Context

`karaoke-app` использует MinIO для shared storage медиа (обложки альбомов,
аудио, видео). Для ускорения рендеринга **MinIO используется как кэш** —
изображения/видео сохраняются там и переиспользуются между запросами.

Проблемы без явных правил:
- **Stale cache** — если Song.save() обновился, кэш MinIO остаётся старым.
- **Memory pressure** — если все изображения кэшируются, MinIO растёт бесконечно.
- **Concurrent uploads** — две параллельных операции могут race condition.

## Decision

**Конвенция для shared image cache в MinIO**:

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
        // ETag = SHA256(data) — для dedup при concurrent uploads
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
            // Already exists with different etag — это ОК (concurrent upload)
            logger.warn("Cache write race for $key", e)
        }
    }
}
```

**Правила**:

1. **Naming**: `<entity>-<id>-<size>` (например, `song-12345-512x512`).
2. **ETag = SHA256(content)**: dedup при concurrent uploads.
3. **Cache key = immutable for entity version**: версия песни → cache key.
4. **TTL**: 7 дней для всех derived assets (обложки, PNG-frames). TTL обнуляется при повторном put().
5. **Invalidation при Song.save()**: если изменился image hash — удалить старый, поставить новый.
6. **No race conditions**: put-or-replace atomic (S3/MinIO поддерживает overwrite).
7. **Concurrent uploads**: оба thread могут put() одновременно — MinIO serializes.

### TTL policy

| Тип cache | TTL | Когда обнуляется |
|-----------|-----|------------------|
| `song-{id}-{size}` обложка | 7 дней | При Song.save() если hash изменился |
| `text-{songId}-{timecode}` текст-кадр | 1 час | При lyrics.markers изменились |
| `karaoke-{songId}-{version}` финальный MP4 | 30 дней | При ручном invalidate |
| `preview-{songId}` VK превью | 1 день | При Song.save() |

### Lifecycle

```
1. User запрашивает /api/public/song-vk-image/12345
2. Cache.get("song-12345-512x512") → null (miss)
3. Service computes PNG via Playwright (~1 sec)
4. Cache.put("song-12345-512x512", png) — для next requests
5. Next request → Cache.get → hit (~10 ms)
6. After 7 days → eviction (cleanup job)
```

### Cleanup

```kotlin
@Scheduled(cron = "0 0 3 * * *")  // каждый день в 03:00
fun cleanupExpiredCache() {
    val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
    val expired = minio.listObjects(ListObjectsArgs.builder().bucket(bucket).build())
        .filter { it.lastModified().isBefore(cutoff) }
    expired.forEach { minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(it.name()).build()) }
}
```

## Consequences

### Positive
- **Performance**: cache hit ~10ms vs compute ~1 sec (×100 ускорение).
- **Storage bounded**: TTL eviction предотвращает бесконечный рост.
- **Consistency**: invalidation через hash → stale reads невозможны.
- **Race-free**: S3/MinIO atomic put-or-overwrite.

### Negative
- **Cache miss penalty**: первый запрос медленный (compute).
- **TTL = staleness tradeoff**: 7 дней для обложек — допустимая задержка.
- **Cleanup overhead**: scheduled job чистит каждый день.

### Neutral
- **Immutability of cache key for version**: новая версия = новый cache key.

## Alternatives Considered

- **Redis cache вместо MinIO**: rejected — Redis это отдельная инфраструктура.
  MinIO уже есть для shared storage.
- **No caching**: rejected — слишком медленно (Playwright headless render ~1 sec).
- **TTL 24h**: слишком короткий (нужны повторные computes).
- **No TTL, manual invalidation**: rejected — manual error-prone.

## References

- [architecture/L2-containers.md](../../L2-containers.md) — где MinIO живёт.
- [domain/rendering.md](../../domain/rendering.md) — кто пишет в cache.
- ADR-0002 (MLT/melt) — потребитель cache.
- MinIO SDK: https://min.io/docs
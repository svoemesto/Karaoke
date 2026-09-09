# Storage Metadata Cache (OpenProject #69, спека #344)

> Per-feature документ (Constitution § VI FR-009). Описывает in-memory TTL-кеш
> метаданных MinIO, устраняющий 72k+ HTTP round-trip на странице Songs в webvue3.

**Прецедент**: спека #339 (2026-09-09) была провалена из-за пропуска Knowledge-first
(агент изобрёл форму кеша как Postgres-таблицу `storage_file_cache`, не зная про
готовый `PollingCache<V>`). Данная реализация использует устоявшийся паттерн из
[`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md).

**Связанные документы**:
- [`specs/344-storage-metadata-cache/spec.md`](../../specs/344-storage-metadata-cache/spec.md)
- [`specs/344-storage-metadata-cache/plan.md`](../../specs/344-storage-metadata-cache/plan.md)
- [`specs/344-storage-metadata-cache/contracts/cache-stats-api.md`](../../specs/344-storage-metadata-cache/contracts/cache-stats-api.md)
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
- [`knowledge/domains/health/domain.md`](../../knowledge/domains/health/domain.md)
- [`knowledge/domains/caching/components/web-caches.md`](../../knowledge/domains/caching/components/web-caches.md) ← источник `PollingCache`

## Контекст

`HealthReport.getHealthReportList(song)` (в `karaoke-app/.../HealthReport.kt`) для каждой песни
делает ~4 вызова `StorageApiClient.fileExists` через nginx path-proxy (`minio-proxy` на проде,
~50–100 ms каждый). На странице Songs в webvue3 (18 000+ песен × 4 типов × 2 источника =
**72 000+ HTTP round-trip**), загрузка занимает минуты. UI «висит», админ не может работать.

Решение — in-memory TTL-кеш, **без новой инфраструктуры**, **без БД-таблицы**
(см. § ADR Decisions).

## Архитектура

```text
HealthReport.actionsLocalStorage / actionsRemoteStorage
          │
          ▼ (autowired через companion-object @JvmStatic helper)
   HealthReport.Companion.cachedFileExists(source, bucket, name, loader)
          │
          ▼
   StorageMetadataCache.getFileExists(source, bucket, name, loader)
          │
          ├── PollingCache<CacheResult> (local) ← in-memory ConcurrentHashMap
          ├── PollingCache<CacheResult> (remote) ← in-memory ConcurrentHashMap
          └── LongAdder counters (hits, misses, evictions)
```

**Никакого AOP.** `HealthReport` — массивный companion object (2440 строк). Spring autowire в instance fields невозможен. Используем паттерн «static reference + bridge bean»:

- `HealthReport.Companion.storageMetadataCache: StorageMetadataCache?` — volatile static field.
- `StorageMetadataCacheWiring` (`@Component`) — `@PostConstruct` вызывает
  `HealthReport.attachStorageMetadataCache(cache)`.
- `cachedFileExists(...)` — `@JvmStatic` helper, fallback на прямой вызов loader'а
  если кеш ещё не инициализирован (early startup).

## API контракт (`StorageMetadataCache`)

```kotlin
fun getFileExists(source: String, bucket: String, fileName: String, loader: () -> Boolean): Boolean
fun getFileIsActual(source: String, bucket: String, fileName: String, loader: () -> Boolean): Boolean
fun getFileInfo(source: String, bucket: String, fileName: String, loader: () -> StorageFileInfo): StorageFileInfo
fun stats(): CacheStatsDto
fun clear()  // для тестов
```

Где:
- `source ∈ {LOCAL, REMOTE}` — обязательно, иначе `IllegalArgumentException`.
- `bucket`, `fileName` — не пустые (иначе `IllegalArgumentException`).
- URL-decoded имя файла — ответственность caller'а (FR-012).
- Cache key формируется как `"$source:$bucket/$fileName:$operation"` (FR-004).
- Loader **MUST быть blocking** (`Boolean` / `StorageFileInfo`, **НЕ** `reactor.core.publisher.Mono`).
  `Mono.block()` внутри loader'а запрещён (усугубляет race-condition #65).

## Endpoint `GET /api/health/cacheStats`

[`contracts/cache-stats-api.md`](../../specs/344-storage-metadata-cache/contracts/cache-stats-api.md) — полный контракт. Кратко:

```json
{
  "local":  {"entries": 1234, "hits": 5678, "misses": 42, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 10},
  "remote": {"entries": 2345, "hits": 9876, "misses": 123, "hitRatio": 0.99, "ttlSeconds": 300, "evictions": 15}
}
```

Используется admin UI для наблюдения. Публичного эквивалента в `karaoke-public` НЕТ
(это внутренний инструмент `karaoke-app`).

## Метрики и observability

### SLF4J-категория `infra.cache.storage`

Регистрируется в
[`log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
(Pass 341+, зафиксировано в спеке #344 Phase 1). Уровень по умолчанию **INFO**.
Образцы сообщений:

```
INFO  cache:miss key="LOCAL:karaoke/song-123.mp4" source=LOCAL operation=fileExists
INFO  cache:miss key="REMOTE:karaoke/song-123.mp4" source=REMOTE durationMs=80
INFO  cache:hit  key="LOCAL:karaoke/song-123.mp4" source=LOCAL
```

(`PollingCache.kt` локально инкрементирует hits/misses внутри — **не дублируем**
в `StorageMetadataCache` для hit-case.)

### Counter'ы через `LongAdder`

- `localHits.sum()`, `localMisses.sum()`, `localEvictions.sum()` — thread-safe
  под hot-contention (Spring Boot worker pool, web UI threads).
- `LongAdder` выбран вместо `AtomicLong` для лучшей производительности при
  concurrent updates (Wait-Free increments).

## Конфигурация (`application.yml`)

```yaml
storage:
  metadata:
    cache:
      local:
        ttlSeconds: 300       # 5 минут
      remote:
        ttlSeconds: 300       # 5 минут
      maxEntries: 50000       # FIFO hard-cap (NFR-002)
```

Override через env: `STORAGE_METADATA_CACHE_LOCAL_TTL_SECONDS=600`.

## Edge cases и ловушки

### Покрытые edge cases (в спеке)

- **TTL = 0**: loader вызывается каждый раз → фактически bypass.
- **TTL = 1 день**: stale data; UI долго показывает OK для удалённого файла. Допустимо.
- **MinIO down**: loader бросает exception → пробрасывается caller'у, **НЕ кешируется** (FR-006).
- **URL-encoded имя файла**: caller ДОЛЖЕН вызвать `decodeFileNameIfEncoded` ПЕРЕД cache key.
- **local vs remote коллизия**: key включает source → `"LOCAL:karaoke/song.mp4"` ≠ `"REMOTE:karaoke/song.mp4"`.
- **Race #65**: cache смягчает последствия (повторные miss идут после cold-start),
  но root cause не чинит (отдельная задача #65).

### Ловушки реализации

- **НЕ оборачивайте reactive в cache-loader**: `Mono.toFuture().get()` внутри loader'а
  блокирует event loop и **усугубляет** #65. Cache работает только с blocking-методами.
- **НЕ передавайте кеш между LOCAL и REMOTE в одном call-site**: `StorageMetadataCache` имеет
  отдельные инстансы для local и remote (`localCache` vs `remoteCache`), cross-contamination
  через shared key невозможна.
- **НЕ используйте write-through persistence** (P3 user story) — отложено в отдельную спеку.
- **`PollingCache.kt` в `karaoke-app/` — ЛОКАЛЬНАЯ КОПИЯ** `karaoke-web/.../services/PollingCache.kt`.
  Это deliberate workaround (см. `web-caches.md#known-gaps` — shared Gradle модуль — overengineering
  для 80 строк). KDoc со ссылкой на оригинал обязателен (FR-006).
- **`maxEntries` hard-cap (NFR-002)** — FIFO eviction в `PollingCache.enforceMaxEntriesIfNeeded()`.
  `ConcurrentHashMap.entrySet().iterator()` не гарантирует insertion order, так что eviction
  НЕ строго FIFO (см. unit test `maxEntries hard cap triggers eviction when size exceeds limit`).

## Тесты

[`specs/344-storage-metadata-cache/quickstart.md`](../../specs/344-storage-metadata-cache/quickstart.md)
— сценарии 1-4 + N1/N2.

Покрытие unit-тестами:

| Файл | Тесты | Что проверяют |
|---|---|---|
| `PollingCacheTest.kt` | 6 | miss/hit/TTL/concurrent/size/clear + maxEntries cap |

**Не покрыто unit-тестами** (требует Spring context, рекомендуется integration test в следующем PR):
- `StorageMetadataCache.kt` — Spring boot up + autowire (smoke).
- `HealthReport.Companion.cachedFileExists(...)` — гарантирует fallback на loader если cache == null.
- `CacheStatsController` — JSON shape (cold-start hitRatio=1.0).

## Миграция и откат

### Initial migration

Никаких миграций БД не требуется. Это in-memory cache.

### Rollback

`git revert <merge-commit>` — отключает cache. `HealthReport.cachedFileExists(...)`
helper с fallback на loader продолжает работать (без cache, всё идёт через MinIO
как раньше). Никаких side-effects на проде.

### Апгрейд с v0 на v1 (будущее)

Если в v1 потребуется write-through persistence (P3 user story):
- Добавить `BatchingStorageMetadataCache implements StorageMetadataCache` (open class).
- Расширить `SpringMetadataCache` Spring DI на выбор между in-memory / batching.
- Postgres миграция: `CREATE TABLE storage_metadata_cache (key VARCHAR PRIMARY KEY, value JSONB, expires_at TIMESTAMP)`.

Open design question — это **отдельная спека 345+**, не входит в scope #344.

## Связь с другими задачами

- **#65** (race in `fileExists` for remote storage): cache смягчает последствия, но не чинит root cause.
  Отдельный fix остаётся in-progress.
- **#286** (author song counts cache): использовал SQL-драйвен денормализацию; данная спека — in-memory TTL.
  Разные подходы для разных сценариев.
- **#339** (предыдущая провалившаяся спека, освобождена): агенту напоминание про Knowledge-first.

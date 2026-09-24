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

## UI-сброс кеша (спека #446)

Persistent-кеш (TTL=∞) не переживает смену endpoint хранилища сам по себе.
Появились UI-кнопки:

- **Одна песня** — `HealthReportTableHeader` → «Сбросить кеш».
- **Страница песен** — футер `SongsTable.vue` → «Сбросить кеш хранилища».

Backend: `POST /api/song/resetStorageCache?ids=1;2;3` — для каждой песни удаляет
все её storage-ключи (LOCAL+REMOTE). Формулы имён — `StorageCacheReset`
(единый с `HealthReport` источник, тест `StorageCacheResetTest`).

**Ловушка (прецедент 2026-09-24):** после смены `storage.remote-endpoint`
обязателен сброс REMOTE-кеша — иначе health-report показывает «0 ошибок» при
отсутствующих файлах.

## Связь с другими задачами

- **#65** (race in `fileExists` for remote storage): cache смягчает последствия, но не чинит root cause.
  Отдельный fix остаётся in-progress.
- **#286** (author song counts cache): использовал SQL-драйвен денормализацию; данная спека — in-memory TTL.
  Разные подходы для разных сценариев.
- **#339** (предыдущая провалившаяся спека, освобождена): агенту напоминание про Knowledge-first.
- **#71 / Spec #352 / Pass 351**: graceful degradation через circuit breaker
  (`StorageCircuitBreaker` оборачивает `fileExists`/`fileIsActual`/`getFileInfo`
  в `decorate(...)` с per-call timeout + FSM CLOSED↔OPEN↔HALF_OPEN).
  См. [specs/352-storage-graceful-degradation/spec.md](../../specs/352-storage-graceful-degradation/spec.md).
- **#131 / Spec #405 / Pass 372**: watchdog + manual reset endpoint — follow-up
  на #71. Production-incident 2026-09-17 — circuit breaker застрял в HALF_OPEN
  на 8+ минут. Добавлен `ScheduledExecutorService` watchdog (FR-001..FR-004)
  + `POST /api/health/circuit-breaker/reset` (FR-005). См.
  [specs/405-storage-circuit-breaker-watchdog/spec.md](../../specs/405-storage-circuit-breaker-watchdog/spec.md).

- **#150 / Spec #426 / Pass 426**: реальный timeout для блокирующего loader —
  follow-up на #71/#131. Production-incident 2026-09-22 — `circuit=OPEN storage=local`
  висел бесконечно: `.timeout(5s)` не прерывал блокирующий MinIO-вызов на
  вызывающем потоке (OkHttp `connectTimeout=15s` > timeout+buffer), watchdog
  вечно переводил HALF_OPEN→OPEN. Fix: `subscribeOn(Schedulers.boundedElastic())`
  в `decorate`/`decorateOrEmpty` + выравнивание OkHttp timeout + исправление
  диагностического лога (`storage=remote`). См.
  [specs/426-storage-circuit-breaker-blocking-timeout/spec.md](../../specs/426-storage-circuit-breaker-blocking-timeout/spec.md).
- **#152 / Spec #428 / Pass 428**: interrupt-безопасность — follow-up на Pass 426.
  При timeout-отмене реактор прерывал worker, MinIO бросал
  `RuntimeException(InterruptedException)` мимо `MinioException` → `onErrorDropped`
  ERROR со стеком. Fix: `runBlockingMinioOrNull { ... }` + `isInterruptWrapped`.
  См. [specs/428-storage-timeout-interrupt-noise/spec.md](../../specs/428-storage-timeout-interrupt-noise/spec.md).

- **#153 / Spec #429 / Pass 429**: разъединение circuit breaker — два независимых
  breaker'а (local/remote). Раньше единый breaker защищал remote, но
  `actionsLocalStorage` читал его для local → remote-сбой валил локальный путь.
  Fix: `StorageCircuitBreakerConfig` (два `@Bean`), local-путь через
  `executeBlocking` в `KaraokeStorageServiceImpl`, корректные qualifier'ы,
  `GET /api/health/circuit-breaker` → `{ local, remote }`, reset `?storage=`.
  См. [specs/429-split-local-remote-circuit-breakers/spec.md](../../specs/429-split-local-remote-circuit-breakers/spec.md).

- **#154 / Spec #430 / Pass 430**: таймаут remote-хранилища 5s → 20s
  (`storage.file-exists-timeout-seconds`) — снижает ложные срабатывания circuit
  на нестабильном канале. Local не затронут (10s/30s).
  См. [specs/430-remote-storage-timeout-20s/spec.md](../../specs/430-remote-storage-timeout-20s/spec.md).

- **#156 / Spec #432 / Pass 432**: диагностика probe — `HealthReport` использует
  нетранзишн `isFastFail()` вместо `acquire()`. Раньше диагностический `acquire()`
  при истёкшем cooldown выигрывал `OPEN→HALF_OPEN` и возвращал `Probe`, но реальный
  MinIO-вызов не исполнял → probe терялся → watchdog возвращал OPEN (вечный цикл).
  См. [specs/432-circuit-probe-consumed-by-healthcheck/spec.md](../../specs/432-circuit-probe-consumed-by-healthcheck/spec.md).

- **#157 / Spec #433 / Pass 433**: cooldown-aware `isFastFail()` — регресс #156:
  после cooldown диагностика больше не fast-fail'ит, вызов доходит до `decorate`
  и становится probe'ом (circuit восстанавливается, а не залипает в OPEN).
  См. [specs/433-circuit-fastfail-cooldown-aware/spec.md](../../specs/433-circuit-fastfail-cooldown-aware/spec.md).

- **#158 / Spec #434 / Pass 434**: `getFileInfo`/`fileIsActual` из кеша (etag/size) —
  раньше HealthReport ходил в MinIO за `statObject` даже на тёплом кеше.
  `selectFileInfo` null-guard (size IS NULL → miss), удалён мёртвый `getFileIsActual`.
  См. [specs/434-healthreport-cache-fileinfo/spec.md](../../specs/434-healthreport-cache-fileinfo/spec.md).

- **#159 / Spec #435 / Pass 435**: backfill `etag`/`size` в кеше — кнопка на главном
  экране админки, фоновый проход, circuit-aware REMOTE, `size=-1` → `exists=false`.
  Изначально обрабатывал только `exists=true`; **#181 / Spec #448** расширил выборку
  на `NOT exists` (самокоррекция: файл мог появиться/быть удалён мимо Karaoke).
  См. [specs/435-cache-etag-size-backfill/spec.md](../../specs/435-cache-etag-size-backfill/spec.md),
  [specs/448-backfill-all-records/spec.md](../../specs/448-backfill-all-records/spec.md).

## История версий

- **V2.11** (Pass 448, 2026-09-24, OpenProject #181): backfill проходит по **всем**
  записям кеша (`NOT exists OR пустые etag/size`), не только `exists=true` —
  самокорректирует `exists` в обе стороны через `getFileInfo`.

- **V2.7** (Pass 446, 2026-09-24, OpenProject #180): UI-сброс кеша — кнопка
  «Сбросить кеш» в health-report песни и «Сбросить кеш хранилища» в футере
  таблицы песен; backend `POST /api/song/resetStorageCache`; формулы имён файлов
  вынесены в `StorageCacheReset` (единый источник с `HealthReport`).

- **V2.10** (Pass 435, 2026-09-22, OpenProject #159): backfill `etag`/`size` для
  строк кеша с `exists=true` и пустыми info (кнопка на главном экране; прогресс в
  логах; файл не найден → `exists=false`).
- **V2.9** (Pass 434, 2026-09-22, OpenProject #158): `getFileInfo`/`fileIsActual`
  из кеша — тёплый кеш не ходит в MinIO за etag/size; `selectFileInfo` null-guard;
  удалён мёртвый `getFileIsActual`.
- **V2.8** (Pass 433, 2026-09-22, OpenProject #157): `isFastFail()` cooldown-aware —
  circuit не залипает в OPEN после cooldown (регресс #156).
- **V2.7** (Pass 432, 2026-09-22, OpenProject #156): диагностика circuit через
  нетранзишн `isFastFail()` — устранён вечный цикл `OPEN→HALF_OPEN→watchdog OPEN`
  (probe больше не «съедается» HealthReport).
- **V2.6** (Pass 430, 2026-09-22, OpenProject #154): таймаут remote-хранилища 5s → 20s
  (`storage.file-exists-timeout-seconds`; circuit `.timeout` + OkHttp connect/read).
  Watchdog-дедлайн 15s → 30s. Local не затронут.
- **V2.5** (Pass 429, 2026-09-22, OpenProject #153): раздельные local/remote circuit breaker.
- **V2.4** (Pass 428, 2026-09-22, OpenProject #152): interrupt-безопасные блокирующие
  вызовы (`runBlockingMinioOrNull`) — убран `onErrorDropped`/`InterruptedException`
  ERROR-шум при timeout-отмене (follow-up Pass 426).
- **V2.3** (Pass 426, 2026-09-22, OpenProject #150): blocking-loader timeout
  (`subscribeOn(boundedElastic)`), OkHttp timeout alignment, `storage=remote` log.
- **V2.2** (Pass 372, 2026-09-17, OpenProject #131): добавлен watchdog +
  manual reset endpoint.
- **V2.1** (Pass 351, 2026-09-09, OpenProject #71): добавлен circuit breaker.
- **V2** (Pass 344/345, 2026-09-09, OpenProject #69): persistent metadata cache
  (Postgres-backed, см. § Апгрейд).
- **V1** (Pass 343, 2026-09-08): in-memory TTL cache.

# Component: web-caches

> **Домен**: [caching](../domain.md)
> **Компонента**: каталог **готовых in-memory паттернов кеша**,
> которые уже используются в `karaoke-web`.

## Ответственность | Responsibility

Документирует **два существующих** in-memory кеша в `karaoke-web/services/`,
которые можно **переиспользовать** в новых фичах (например, для
кеширования MinIO-метаданных в задаче #69).

**[WARN]** Прецедент 2026-09-09 (Pass 340, спека #339): агент
предложил создавать новую структуру `storage_file_cache` в БД, **не
зная** об этих готовых паттернах. Это нарушение Knowledge-first.

## Существующие паттерны

### `DedupCache` — дедупликация событий

Файл: `karaoke-web/.../services/DedupCache.kt` (86 строк).

**Что делает**: потокобезопасный TTL-кеш для **дедупликации**
повторяющихся событий. Хранит `key → lastSeenAtMs`. При вызове
`isDuplicate(key)` возвращает `true`, если тот же ключ был за последние
`ttlMs`.

**Где используется**:

- `SamplingFilter.shouldSkip` — дедуп событий `tbl_events`.
- `EventsBuffer` — при батчинге событий в БД.

**TTL**: управляется через `KaraokeProperties.eventsDedupTtlSeconds`.

**Ключ формируется как**: `(restName, canonical(parameters), anonId-or-userId)`.

**Реализация**: `ConcurrentHashMap<String, Long>` + `AtomicLong`
cleanup counter. **Без внешних зависимостей** (Caffeine/Guava
намеренно не используются).

**Lazy cleanup**: на каждом N-ном вызове (cleanupEvery = 1000)
удаляются истёкшие записи. O(1) средняя стоимость, O(1) amortized
cleanup.

**Почему НЕ Caffeine/Guava**: см. KDoc в файле — «намеренный минимум
зависимостей, для текущей нагрузки ~30 req/min ConcurrentHashMap
достаточен».

### `PollingCache<V>` — TTL-кеш для polling-эндпоинтов

Файл: `karaoke-web/.../services/PollingCache.kt` (80 строк).

**Что делает**: потокобезопасный TTL-кеш **общего назначения** с
`loader: () -> V`. Хранит `key → (value, expiresAtMs)`. При вызове
`getOrCompute(key, ttlSeconds, loader)` возвращает кешированное
значение, если живо, иначе вызывает loader, сохраняет результат с
TTL и возвращает.

**Где используется**:

- `PublicNewsController` — `/api/public/news/since` (TTL=60s).
- `PublicChatController` — `/api/public/account/chat/unreadcount`
  (TTL=10s, UX бейджа).
- `PublicShareController` — `/api/public/share/heartbeat` (TTL=15s,
  heartbeat 25s, каждый 2-й no-op).

**Реализация**: `ConcurrentHashMap<String, CacheEntry<V>>` +
`AtomicLong` cleanup counter. Generic по типу V.

**Параллельные вызовы НЕ дедуплицируются**: loader может вызываться
дважды в race condition. Это приемлемо для polling-кеша — два
SQL-запроса с интервалом <100ms случаются редко.

**Lazy cleanup**: cleanupEvery = 500.

**Почему НЕ Spring `@Cacheable`**: см. KDoc в файле — не хочется
global cache manager ради 3 endpoints, TTL разный per-endpoint.

## Когда использовать какой паттерн

| Сценарий | Паттерн |
|---|---|
| Дедупликация событий / запросов | `DedupCache` |
| Polling-эндпоинт с TTL | `PollingCache<V>` |
| Read-mostly счётчики | `AtomicInteger` (см. [caching-patterns](caching-patterns.md)) |
| Dirty-флаг | см. [caching-patterns](caching-patterns.md) |
| **Кеш метаданных MinIO** (задача #69) | **`PollingCache<V>`** — лучший fit |

## Применимость к OpenProject #69

Задача #69 «Кеширование информации из хранилища» требует in-memory
кеша для результатов `StorageApiClient.fileExists` /
`fileIsActual`. Прямой кандидат:

```kotlin
// В KaraokeStorageService (или новый StorageMetadataCache.kt):
@Service
class StorageMetadataCache {
    private val cache = PollingCache<StorageFileInfo>()

    fun getFileInfo(bucket: String, name: String, loader: () -> StorageFileInfo): StorageFileInfo {
        val key = "$bucket/$name"
        // TTL=300s (5 мин) — текущее значение из OpenProject #69
        return cache.getOrCompute(key, ttlSeconds = 300, loader = loader)
    }
}
```

**NB**: предыдущая спека #339 предлагала БД-таблицу
`storage_file_cache` — это **overengineering**. PollingCache уже
имеет всё необходимое (lazy cleanup, generic, TTL).

## Известные ограничения

- **Нет persistence**: оба паттерна чисто in-memory, при рестарте
  `karaoke-web` кеш теряется. Это OK для polling-кеша (cold-start
  подход: первый запрос = miss, дальше hit). Для долгоживущего
  кеша метаданных (#69) может быть недостаточно — нужно подумать
  про persistence (например, write-through в БД).

- **Race conditions на первый loader**: `PollingCache` не
  single-flight'ит первый вызов loader (см. KDoc). Для metadata
  cache это OK (один MinIO-запрос), но если бы было дорого — нужен
  паттерн 6 из `caching-patterns.md` (single-flight guard).

- **TTL в `PollingCache` фиксированный на момент создания entry**:
  если `ttlSeconds` меняется между вызовами, новые записи получают
  новый TTL, старые — старый. Это OK для #69.

## Связь с другими компонентами

- **caching-patterns** (`caching-patterns.md`): базовый каталог
  паттернов. Эта компонента — **дополнение**, документирующее
  готовые имплементации `DedupCache` и `PollingCache`.

- **OpenProject #69**: см. секцию «Применимость».

## Код (физическая реализация)

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DedupCache.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt`
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingFilter.kt`
  (использует `DedupCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsBuffer.kt`
  (использует `DedupCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicChatController.kt`
  (использует `PollingCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt`
  (использует `PollingCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt`
  (использует `PollingCache`)

## Known gaps

- [ ] **`PollingCache` в `karaoke-app`** — есть или только в `karaoke-web`?
      Если только в web, нужно переиспользовать или скопировать.
- [ ] **Caffeine/Guava как замена** — если нагрузка вырастет, может
      быть нужно. Решение — пересмотр через год.
- [ ] **Метрики cache hit/miss rate** — **отсутствуют** (проверено:
      `grep -nE "log|Logger|println" DedupCache.kt PollingCache.kt`
      — нет вхождений). Если нужны — добавить SLF4J-категорию
      `infra.cache.dedup` / `infra.cache.polling` (по образцу
      [log-categories](../../monitoring/components/log-categories.md)).
- [ ] **Persistence для metadata cache** — нужно ли, как реализовать.
      Связано с #69.

## Changelog

- **Pass 341** (2026-09-09): Initial. Прецедент: задача #69.
  Автор: agent (Karaoke).
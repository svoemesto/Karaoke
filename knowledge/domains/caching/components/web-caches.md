# Component: web-caches

> **Домен**: [caching](../domain.md)
> **Компонента**: каталог **готовых in-memory паттернов кеша**,
> которые уже используются в `karaoke-web`.

## Ответственность | Responsibility

Документирует **два существующих** in-memory кеша, которые можно
**переиспользовать** в новых фичах.

**Pass 456 (2026-09-26)** — `PollingCache` перенесён в `karaoke-app`
(`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PollingCache.kt`),
копия в `karaoke-web` удалена. Причина: `karaoke-web` зависит от `karaoke-app`,
а не наоборот, поэтому копия в web была физически недоступна ядру — это и был
known gap, зафиксированный ниже. Теперь у обоих модулей одна реализация.
`DedupCache` остаётся в `karaoke-web` (пока его никто в ядре не использует).

**[WARN]** Прецедент 2026-09-09 (Pass 340, спека #339): агент
предложил создавать новую структуру `storage_file_cache` в БД, **не
зная** об этих готовых паттернах. Это нарушение Knowledge-first.

## Интерфейсы и Контракты | Interfaces and Contracts

### `DedupCache` (`karaoke-web`)

Файл: `karaoke-web/.../services/DedupCache.kt` (86 строк).

```kotlin
class DedupCache(private val ttlMs: () -> Long) {
    fun isDuplicate(key: String): Boolean   // true = дубликат за последние ttlMs
    fun size(): Int
    fun clear()
}
```

Состояние: `ConcurrentHashMap<String, Long>` (`key → lastSeenAtMs`) +
`AtomicLong` cleanup counter. `ttlMs` — лямбда, чтобы TTL читался на
каждом вызове. Единственный потребитель — `SamplingFilter.shouldSkip`
(дедуп событий `tbl_events`); TTL — `KaraokeProperties.eventsDedupTtlSeconds`,
ключ — `(restName, canonical(parameters), anonId-or-userId)`.

### `PollingCache<V>` (`karaoke-app`)

Файл: `karaoke-app/.../services/PollingCache.kt` (Pass 456; до этого — в
`karaoke-web`).

```kotlin
class PollingCache<V> {
    fun getOrCompute(
        key: String,
        ttlSeconds: Long,
        shouldCache: (V) -> Boolean = { true },
        loader: () -> V,
    ): V
    fun size(): Int
    fun clear()
}
private data class CacheEntry<V>(val value: V, val expiresAtMs: Long)
```

Состояние: `ConcurrentHashMap<String, CacheEntry<V>>` + `AtomicLong`
cleanup counter. `shouldCache` (Pass 456, по умолчанию `{ true }`) —
предикат «сохранять ли результат loader'а»: вернув `false`, вызывающий
получает значение, но в кеше его не остаётся. Мотивирующий случай —
детект ВПН (`isVpnActive`, `karaoke-app/.../Utils.kt`): «страну
определить не удалось» это fail-open, и кэшировать эту неудачу нельзя,
иначе разовый сетевой сбой «залипнет» на весь TTL и машина с включённым
ВПН пойдёт в Яндекс.Музыку, где её заблокируют.

Потребители и TTL:

- `PublicNewsController` — `/api/public/news/since` (TTL=60s).
- `PublicChatController` — `/api/public/account/chat/unreadcount`
  (TTL=10s, UX бейджа).
- `PublicShareController` — `/api/public/share/heartbeat` (TTL=15s,
  heartbeat 25s, каждый 2-й no-op).

## Логика и Алгоритмы | Logic and Algorithms

### `DedupCache` — дедупликация событий

1. `isDuplicate(key)` берёт `now` и `cutoff = now - ttlMs()`; через
   `ConcurrentHashMap.compute` по ключу: если `lastSeen >= cutoff` —
   возвращает `true` (запись не трогает), иначе пишет `now` и
   возвращает `false`. `compute` сериализует доступ к ключу атомарно.
2. **Lazy cleanup**: на каждом N-ном вызове (cleanupEvery = 1000)
   удаляются истёкшие записи. O(1) средняя стоимость, O(1) amortized
   cleanup.
3. **Без внешних зависимостей** (Caffeine/Guava намеренно не
   используются). Обоснование в KDoc: «намеренный минимум
   зависимостей, для текущей нагрузки ~30 req/min ConcurrentHashMap
   достаточен» (N записей при N=10k ≈ 500 КБ heap).

### `PollingCache<V>` — TTL-кеш для polling-эндпоинтов

1. `getOrCompute` читает `store[key]`; если `expiresAtMs >
   System.currentTimeMillis()` — отдаёт значение **без вызова loader**.
2. На miss/истечении вызывает `loader()`, и только если
   `shouldCache(fresh)` — кладёт `CacheEntry(fresh, now + ttlSeconds*1000)`.
3. **`expiresAtMs` считается ПОСЛЕ вызова loader'а** (исправлено в
   Pass 456; раньше `now` снимался до вызова, поэтому медленный loader
   съедал часть TTL — у детекта ВПН loader идёт до 5+5 с на сервис).
4. **Lazy cleanup**: cleanupEvery = 500, `removeIf { expiresAtMs <= now }`.
5. **TTL фиксируется на момент создания entry**: если `ttlSeconds`
   меняется между вызовами, новые записи получают новый TTL, старые —
   старый.
6. **Параллельные вызовы НЕ дедуплицируются**: loader может вызываться
   дважды в race condition. Приемлемо для polling-кеша — два SQL-запроса
   с интервалом <100ms случаются редко.
7. **Почему НЕ Spring `@Cacheable`**: не хочется global cache manager
   ради 3 endpoints, TTL разный per-endpoint, а явный `loader` делает
   cache-miss path очевидным.

## Когда использовать какой паттерн

| Сценарий | Паттерн |
|---|---|
| Дедупликация событий / запросов | `DedupCache` |
| Polling-эндпоинт с TTL | `PollingCache<V>` |
| Read-mostly счётчики | `AtomicInteger` (см. [caching-patterns](caching-patterns.md)) |
| Dirty-флаг | см. [caching-patterns](caching-patterns.md) |
| **Кеш метаданных MinIO** (задача #69) | **`PollingCache<V>`** — лучший fit |
| TTL-кеш значения с загрузчиком, который может «не получиться» | **`PollingCache<V>` + `shouldCache`** (Pass 456; пример — детект ВПН) |

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

**NB (Pass 486)**: приведённый выше эскиз — уже не «кандидат», а
реализованный факт. Реальный кеш метаданных — `StorageMetadataCache`
(`karaoke-app/.../services/StorageMetadataCache.kt`, `@Component`,
spec #348), и он построен **не** на `PollingCache`: TTL = ∞,
single source of truth — таблица `tbl_storage_metadata_cache`
(migration 48), инвалидация write-through из
`StorageApiClient`/`KaraokeStorageService` (`recordUpload`/`recordDelete`)
+ ручной `POST /api/health/cache/refresh`. Детали —
[storage-api-client.md](../../storage/components/storage-api-client.md)
и [health-report.md](../../health/components/health-report.md).

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

## Зависимости | Dependencies

- **caching-patterns** (`caching-patterns.md`): базовый каталог
  паттернов. Эта компонента — **дополнение**, документирующее
  готовые имплементации `DedupCache` и `PollingCache`.

- **OpenProject #69**: см. секцию «Применимость».

## Код (физическая реализация)

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DedupCache.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PollingCache.kt`
  (Pass 456; используется и из `karaoke-web`)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`
  (детект ВПН — потребитель `PollingCache` с `shouldCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingFilter.kt`
  (использует `DedupCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsBuffer.kt`
  (упоминает `DedupCache` в KDoc; сам dedup делает `SamplingFilter`)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt`
  (вечный кеш метаданных MinIO, spec #348 — см. NB в «Применимость»)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicChatController.kt`
  (использует `PollingCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt`
  (использует `PollingCache`)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt`
  (использует `PollingCache`)

## Known gaps

- [x] **`PollingCache` в `karaoke-app`** — **закрыто в Pass 456**: класс перенесён
      в `karaoke-app`, копия в `karaoke-web` удалена, оба модуля используют одну
      реализацию.
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

- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 456** (2026-09-26): `PollingCache` перенесён в `karaoke-app`
  (закрыт known gap), добавлен `shouldCache`, исправлен расчёт `expiresAtMs`.
  Повод: агент сделал ad-hoc TTL-кэш для детекта ВПН, не зная об этом
  документе — то есть повторил прецедент Pass 340 / спеки #339, ради
  предотвращения которого страница и написана.
- **Pass 341** (2026-09-09): Initial. Прецедент: задача #69.
  Автор: agent (Karaoke).
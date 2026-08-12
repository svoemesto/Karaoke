# Data Model: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

## 1. Сущности

### 1.1 `StatsCacheKey` (data class, in-memory)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsCacheKey.kt`

```kotlin
data class StatsCacheKey(
    val endpoint: String,           // "summary" | "timeseries" | "channels" | "countries" | "referrers" | "monetization"
    val params: Map<String, String> = emptyMap(),
)
```

**Назначение**: ключ для `StatsCache`. `params` — нормализованные
query-параметры (для чистых агрегатов всегда `emptyMap()`).

**Поля**:
| Поле | Тип | Описание |
|---|---|---|
| `endpoint` | `String` | Имя endpoint'а (без `/api/stats/` prefix) |
| `params` | `Map<String, String>` | Query-параметры, отсортированные по ключу |

**Валидация**: нет. `endpoint` — фиксированный enum-like набор из 6
значений (см. [spec.md](./spec.md) FR-004).

**Lifecycle**: stateless, immutable. Garbage-collected при удалении из
`StatsCache` через TTL expiry.

### 1.2 `StatsCacheEntry` (data class, in-memory)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsCacheKey.kt`
(в том же файле, что и `StatsCacheKey`)

```kotlin
data class StatsCacheEntry(
    val value: Any,                  // тип зависит от endpoint'а (Map<String, Any>, List<*>, и т.п.)
    val expiresAt: java.time.Instant,
)
```

**Поля**:
| Поле | Тип | Описание |
|---|---|---|
| `value` | `Any` | Кешированный ответ (тело JSON-ответа) |
| `expiresAt` | `Instant` | Момент истечения TTL (createdAt + 60 сек) |

**Lifecycle**: создаётся при cache miss, удаляется при lazy expiry
(проверка `expiresAt > Instant.now()` при чтении) или при
`invalidateAll()`.

### 1.3 `StatsCache` (object singleton, in-memory)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsCache.kt`

```kotlin
object StatsCache {
    private const val TTL_SECONDS = 60L
    private val cache = ConcurrentHashMap<StatsCacheKey, StatsCacheEntry>()

    fun get(key: StatsCacheKey): Any? { ... }    // null = miss OR expired
    fun put(key: StatsCacheKey, value: Any) { ... }
    fun invalidateAll() { cache.clear() }
    fun snapshot(): Map<StatsCacheKey, StatsCacheEntry> { ... }  // для debug endpoint
}
```

**Поля**:
| Поле | Тип | Описание |
|---|---|---|
| `TTL_SECONDS` | `Long` = 60 | Per FR-004 (см. spec.md) |
| `cache` | `ConcurrentHashMap<StatsCacheKey, StatsCacheEntry>` | Хранилище |

**Методы**:
| Метод | Описание |
|---|---|
| `get(key)` | Возвращает `value` если cache hit И `expiresAt > now()`, иначе `null` |
| `put(key, value)` | Записывает `StatsCacheEntry(value, now() + 60s)` |
| `invalidateAll()` | Очищает весь кеш (для будущей SSE-инвалидации, сейчас не вызывается) |
| `snapshot()` | Возвращает `Map` с всеми записями (включая expired) — для debug |

**Thread-safety**: `ConcurrentHashMap` гарантирует atomic put/get.
Lazy expiration — `get()` проверяет `expiresAt` под lock-free read.
Это даёт stale-once-after-expiry гарантию: одна запись может быть
прочитана сразу после формального истечения (race с put), но это OK —
следующий вызов увидит expired и перезапишет.

**Lifecycle**: singleton, живёт весь lifecycle приложения. Не
персистится между перезапусками `karaoke-app`.

### 1.4 `DbOverloadBanner` (Vue component, in-browser)

**Файл**: `webvue3/src/components/Stats/DbOverloadBanner.vue`

```vue
<template>
  <div class="db-overload-banner" role="alert" aria-live="polite">
    <strong>БД перегружена.</strong>
    Retry через {{ countdown }} сек.
    <BButton :disabled="!canRetry" @click="onRetry">Retry now</BButton>
  </div>
</template>
```

**Props**:
| Prop | Тип | Обязательный | Описание |
|---|---|---|---|
| `retryAfterSeconds` | `Number` | да | TTL из `503 Retry-After` заголовка |
| `errorCode` | `String` | да | `stats.unavailable` |

**Data**:
| Поле | Тип | Описание |
|---|---|---|
| `countdown` | `Number` | Обратный отсчёт (начинается с `retryAfterSeconds`) |
| `canRetry` | `Boolean` | `true` когда `countdown === 0` |

**Emits**:
| Event | Payload | Когда |
|---|---|---|
| `retry` | `()` | Клик по «Retry now» (когда `canRetry === true`) |

**Lifecycle**:
- `mounted()`: `setTimeout(retryCallback, retryAfterSeconds * 1000)` —
  один авто-retry. `setInterval(updateCountdown, 1000)` — countdown UI.
- `beforeUnmount()`: оба таймера `clearTimeout`/`clearInterval`.
- При F5 — компонент пересоздаётся, countdown сбрасывается (per FR-011).

**Accessibility (per deferred a11y из clarification)**:
- `role="alert"` — скринридер прочитает сразу.
- `aria-live="polite"` — обновления countdown не агрессивные.
- `aria-label` на кнопке «Retry now» — текст кнопки уже говорит сам за себя.

### 1.5 `StatsDebugDto` (data class, response DTO)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsDebugDto.kt`

```kotlin
data class StatsDebugDto(
    val cacheSize: Int,                          // текущий размер кеша
    val cacheKeys: List<CacheKeyInfo>,           // список ключей с возрастом
    val pgActiveConnections: Int,                // count(*) from pg_stat_activity
    val timestamp: String,                       // ISO-8601 UTC
)

data class CacheKeyInfo(
    val endpoint: String,
    val params: Map<String, String>,
    val ageSeconds: Long,                        // now - createdAt
    val expired: Boolean,                        // now > expiresAt
)
```

**Поля**: см. [contracts/stats-debug.md](./contracts/stats-debug.md) для
полного JSON-формата.

## 2. State Transitions

`StatsCache.get(key)`:
- **Cache hit + not expired**: возврат `value`, no state change.
- **Cache hit + expired** (lazy): возврат `null`, eventual removal (при следующем put).
- **Cache miss**: возврат `null`, caller должен вычислить и вызвать `put(key, value)`.

`DbOverloadBanner`:
- **mounted** → `mounted` (countdown active).
- **countdown === 0** → emit `retry` → parent перезапрашивает → **mounted** (новый цикл при 200) ИЛИ **mounted** (новый цикл при следующем 503).
- **beforeUnmount** → unmounted (timers cleared).

## 3. Relationships

```
StatsController.statsBySong()     ──uses──►  StatsCache.get/put
StatsController.summary()         ──uses──►  StatsCache.get/put
StatsController.timeseries()      ──uses──►  StatsCache.get/put
StatsController.channels()        ──uses──►  StatsCache.get/put
StatsController.countries()       ──uses──►  StatsCache.get/put
StatsController.referrers()       ──uses──►  StatsCache.get/put
StatsController.monetizationSummary()  ──uses──►  StatsCache.get/put
StatsController.bySong()/topUsers()/webEvents()  ──no cache──► direct DB query

StatsDebugController.debug()      ──reads──►  StatsCache.snapshot()
                                         ──queries──► pg_stat_activity

StatsView.vue (webvue3)           ──renders──►  DbOverloadBanner
                                        ──emits──►  retry → reloadXxx()
```

## 4. Persistence

**Нет persistence**. Все сущности — in-memory или in-browser. После
перезапуска `karaoke-app` или F5 `webvue3` кеш пуст.

## 5. Validation rules

- `StatsCacheKey.endpoint` ∈ {"summary", "timeseries", "channels",
  "countries", "referrers", "monetization"} — enforce в caller, не в кеше.
- `DbOverloadBanner.retryAfterSeconds > 0` — enforce в caller (если 0
  или отсутствует, компонент не показывается).

## 6. Volume / Scale

- `StatsCache`: ≤6 записей в любой момент времени (по числу кешируемых
  endpoint'ов). Пренебрежимо мало.
- `DbOverloadBanner`: ≤1 экземпляр на таб в `StatsView`. Всего ≤7
  экземпляров на странице.

Все сущности укладываются в существующие лимиты проекта. Никаких
изменений схемы БД не требуется.

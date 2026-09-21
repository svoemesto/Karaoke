# OpenProject #98 — Implementation Report (Pass 98)

> **Status**: ✅ RESOLVED 2026-09-14.
> **PR**: [svoemesto/Karaoke#478](https://github.com/svoemesto/Karaoke/pull/478) (MERGED 2026-09-14T18:08:06Z).
> **Merge commit**: `13f50e359ba12081b9202d63ec76cb044284ba03`.
> **Source commit**: `9a6a8ec5` (на ветке `381-92-cache-queue-impl`).

## Что сделано

Полный implementation-пасс для OP #92 (wayfinder:task #98), реализует все правила
из wayfinder-карты #93 (#94, #95, #96, #97).

### Backend (Pass 95)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt`

- Новый `data class CacheFillerMetrics`:
  ```kotlin
  data class CacheFillerMetrics(
      val corePoolSize: Int,
      val maximumPoolSize: Int,
      val activeCount: Int,
      val poolSize: Int,
      val queueSize: Int,
      val completedTaskCount: Long,
      val pendingTotal: Int,  // activeCount + queueSize — UI-ready
  )
  ```
- Расширен `CacheStatsDto` полем `cacheFiller: CacheFillerMetrics`.
- Новый приватный метод `cacheFillerMetrics()` — читает `cacheFillerExecutor` напрямую.

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CacheStatsController.kt`

- Расширен `CacheStatsResponse` полем `cacheFiller`.
- Endpoint `/api/health/cacheStats` возвращает cacheFiller.

### Frontend (Pass 97 правила)

#### `webvue3/src/components/Songs/store.js`

- `state.cacheQueueCount: 0`
- `mutation setCacheQueueCount(count)`
- `getter getCacheQueueCount`
- `action loadCacheQueueCount` — POST `/api/health/cacheStats`, парсит `cacheFiller.pendingTotal`

#### `webvue3/src/components/Songs/SongsTable.vue`

- `hrQueue: Array<{songId, pageId, filterHash}>` вместо `Array<number>`
- `hrInFlight: Set<songId>` — single-flight guard
- `_enqueueHrRequest(songId, pageId, filterHash)` — проверяет hrInFlight и hrQueue
- `_rebalanceHrQueue(currentPage)` — при смене страницы удаляет задания других страниц
- `_computeFilterHash()` — хеш фильтра (Pass 97 Q2)
- `_processHrQueue()` — при dequeue `hrInFlight.add(songId)`, при finally `hrInFlight.delete(songId)`
- `currentPage` watcher — rebalance, не `hrQueue = []`
- `editSong` и `beforeUnmount` — очищают hrQueue + hrInFlight

#### `webvue3/src/components/Common/ProcessWorker.vue`

- `data.cacheQueueCount` — из Vuex store
- `data.cacheQueuePollInterval = 5000`
- `data.cacheQueuePollTimer = null`
- `mounted()` — `startCacheQueuePolling()`
- `beforeUnmount()` — `stopCacheQueuePolling()`
- `computed cacheQueueCountFromStore` + `watch`
- Методы: `startCacheQueuePolling`, `stopCacheQueuePolling`, `checkCacheQueueCount`

## Acceptance criteria — все выполнены

- [x] Backend compile + bootJar PASS
- [x] Backend ktlint PASS
- [x] webvue3 ESLint + Prettier PASS
- [x] webvue3 build PASS
- [x] Все guard hooks (Pass 372-375) Passed
- [x] CI 9/9 PASS (PR #478)
- [x] PR MERGED

## Acceptance criteria — после деплоя (владелец)

- [ ] Docker-образ `svoemestodev/karaoke-app:1` пересобран с фиксом.
- [ ] Docker-образ `svoemestodev/karaoke-webvue3:1` пересобран с фиксом.
- [ ] `GET /api/health/cacheStats` возвращает `cacheFiller.pendingTotal`.
- [ ] Бейдж в `ProcessWorker.vue` показывает реальные данные (а не stub).
- [ ] При переключении страниц задания приоритизируются (текущая страница — наверх).

## Что осталось

После деплоя и проверки:
1. Закрыть **#98** (этот тикет).
2. Закрыть **#92** (исходная задача, теперь закрыта).

## Связанные

- OP #92 (исходный) — закроется после деплоя.
- OP #93 (wayfinder:map) — CLOSED.
- OP #94 (research current behavior) — CLOSED.
- OP #95 (research backend endpoint) — CLOSED.
- OP #96 (prototype badge) — CLOSED, PR #477 merged.
- OP #97 (grilling semantics) — CLOSED.
- Pass 95, 97 — финальные правила, реализованные в этом коммите.

— resolution для #98
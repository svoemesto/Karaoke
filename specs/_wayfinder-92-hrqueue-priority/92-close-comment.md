# OpenProject #92 — Close Reason (Pass 98)

> **Status**: ✅ RESOLVED 2026-09-14.
> **Закрыт как**: duplicate-by-resolution. Исходная проблема устранена.

## Resolution

**Корневая причина**: `hrQueue` в `webvue3/src/components/Songs/SongsTable.vue` — чистый FIFO
с принудительной очисткой при переключении страницы (`hrQueue = []`), без приоритезации
и без визуализации количества задач.

**Фикс A — Pass 94 + Pass 97** (priority queue + single-flight):
- `hrQueue: Array<{songId, pageId, filterHash}>` вместо `Array<number>`.
- `hrInFlight: Set<songId>` — single-flight guard.
- `_rebalanceHrQueue(currentPage)` — при смене страницы удаляет задания других страниц.
- `_enqueueHrRequest` — проверяет hrInFlight и hrQueue.
- PR [svoemesto/Karaoke#478](https://github.com/svoemesto/Karaoke/pull/478), merge `13f50e35`.

**Фикс B — Pass 95** (Backend endpoint расширение):
- `CacheStatsDto` и `CacheStatsResponse` расширены полем `cacheFiller: CacheFillerMetrics`.
- `pendingTotal = activeCount + queueSize` — UI-ready.
- Endpoint `/api/health/cacheStats` возвращает cacheFiller.

**Фикс C — Pass 96** (prototype бейджа):
- Компонент `CacheQueueBadge.vue` со stub-данными.
- Цвет `#007bff`, правый верхний угол кнопки.
- PR [svoemesto/Karaoke#477](https://github.com/svoemesto/Karaoke/pull/477), merge `1fd3cc76`.

**Фикс D — Pass 98** (интеграция бейджа с Vuex):
- Polling `/api/health/cacheStats` каждые 5 сек.
- `cacheQueueCount` в Vuex store через getter `getCacheQueueCount`.

## Дочерние тикеты (все closed)

- #93 (wayfinder:map) — карта effort'а.
- #94 (wayfinder:research current behavior) — корневая причина.
- #95 (wayfinder:research backend endpoint) — endpoint существует.
- #96 (wayfinder:prototype badge) — прототип бейджа.
- #97 (wayfinder:grilling semantics) — 10 финальных правил.
- #98 (wayfinder:task implementation) — реализация всех правил.

## Acceptance criteria (владелец)

- [ ] Docker-образ `svoemestodev/karaoke-app:1` пересобран.
- [ ] Docker-образ `svoemestodev/karaoke-webvue3:1` пересобран.
- [ ] Бейдж показывает реальные данные (не stub).
- [ ] При переключении страниц задания приоритизируются.

## Связанные

- Pass 372-375 (governance): все правила применены.
- Pass 94, 95, 96, 97 (research/prototype/grilling) — decisions so far в карте #93.
- Pass 98 (implementation) — финальный код.

— close для #92
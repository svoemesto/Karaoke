## SSE CACHE_QUEUE_SIZE — отдельный канал (PR #499)

### Что сделано

Создан **отдельный** SSE-канал `CACHE_QUEUE_SIZE` (не трогая существующий
`PROCESS_COUNT_WAITING`):

**Backend**:
- `SseNotificationType.CACHE_QUEUE_SIZE("cacheQueueSize")` — новый enum.
- `CacheQueueSizeMessage(cacheQueueSize: Int)` — DTO.
- `SseNotification.cacheQueueSize(message)` — factory.
- `KaraokeProcessWorker.sendCacheQueueSizeMessage(Int)` — аналог `sendCountWaitingMessage`,
  с дедупликацией через `@Volatile lastSentCacheQueueSize: Int?`.
- Вызывается из:
  - `start()` — начальное значение после рестарта воркера.
  - `KaraokeProcessThread.run()` — после старта subprocess.
  - `KaraokeProcessThread.run()` — после завершения subprocess.
  - `StorageMetadataCache.submitBack/submitFront` — event-driven (не poll).

**Frontend**:
- `App.vue`: case `CACHE_QUEUE_SIZE` → `setCacheQueueSize` dispatch.
- `ProcessWorker.vue`: убран `setInterval` (poll). initial `checkCacheQueueSize()`
  оставлен на случай если SSE ещё не подключился.

### Что НЕ делал

- Не трогал `PROCESS_COUNT_WAITING` (структура, дедупликация, периодические вызовы).
- Не встраивал `cacheQueueSize` в `ProcessCountWaitingMessage`.

CI 12/12 PASS.

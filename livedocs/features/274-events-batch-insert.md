---
status: Active
slug: 274-events-batch-insert
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/274-events-batch-insert/spec.md
  - 241-db-storage-perf-audit
  - 270-db-indexes-verification
---

# 274 — Batch INSERT для tbl_events (LiveDoc)

> Drill-down — [specs/274-events-batch-insert/spec.md](../../specs/274-events-batch-insert/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md) — Tier-3 / FR-109.

## Что делает

Буферизует события `tbl_events` в памяти через `@Service EventsBuffer` и делает flush через
JDBC `addBatch()` + `executeBatch()` каждые 5 сек (дефолт). При переполнении буфера —
backpressure flush немедленно. Kill-switch `karaoke.web.events.batch-enabled` (дефолт **false**
— opt-in).

## Effect

- **При включении**: ≥80% снижение RPS INSERT к PostgreSQL (50 INSERT/5 сек → 1 batch)
- **При выключенном (default)**: поведение идентично прежнему — sync INSERT (без изменений)

## Реализация

### 1. `EventsBuffer.kt` (`karaoke-web/.../services/`)

Новый `@Service` singleton с:
- `data class EventRecord` (immutable: fieldsValues, eventType, clientIp, userAgent, anonId, siteUserId)
- `buffer: ConcurrentLinkedQueue<EventRecord>` (thread-safe очередь)
- `flushing: AtomicBoolean` (предотвращает двойной flush)
- `add(record)` — добавляет в буфер ИЛИ делает sync INSERT (kill-switch false)
- `@Scheduled(fixedDelayString = "${...batch-flush-interval-ms:5000}") flush()` — drain + batch
- `executeBatch(batch)` — JDBC inserts в одном соединении
- `executeSingle(record)` — sync INSERT (fallback / kill-switch false)
- `buildInsertSql(fieldsValues)` — копия логики из `MainController.insertEvent`
- `isEnabled()` / `maxBufferSize()` — через `KaraokeProperties.getBoolean/getInt`

### 2. `MainController.kt:141` — `insertEvent` → `eventsBuffer.add()`

**Было** (sync INSERT):
```kotlin
fun insertEvent(fieldsValues: MutableList<Pair<String, Any>>): Boolean {
    // ... SQL-формирование + connection.prepareStatement + executeUpdate
}
```

**Стало** (FR-006):
```kotlin
fun insertEvent(fieldsValues: MutableList<Pair<String, Any>>): Boolean {
    val record = EventsBuffer.EventRecord(
        fieldsValues = fieldsValues.toList(),
        eventType = eventType,
        clientIp = clientIp,
        userAgent = userAgent,
        anonId = anonId,
        siteUserId = siteUserId,
    )
    return eventsBuffer.add(record)
}
```

Также добавлен `private val eventsBuffer: EventsBuffer` в конструктор `MainController` (autowired Spring).

## Архитектурные решения

### Почему opt-in (kill-switch default false)

Tier-3 P2 — на текущем проде INSERT маленький (SamplingFilter + DedupCache уже уменьшают
поток до ~30 req/min). Безопасный rollout:
- Меняет поведение (sync → async flush) — пусть и небольшое (5 сек задержка).
- Может терять события при крэше (in-memory).
- Требует runtime-наблюдения для верификации эффекта.

Администратор может включить после наблюдения baseline.

### Почему JDBC `addBatch()`, а не multi-row INSERT

Multi-row INSERT (один SQL `INSERT INTO ... VALUES (..), (..), (..)`) требует **одинакового
набора колонок**. У нас разные `eventType` имеют разные поля:
- `CLICK_TO_LINK` — `link_type`, `link_name`, опционально `song_id`, `song_version`
- `PLAY` — `song_id`, `song_version`
- `PLAYER` — `link_type`, `link_name`, `song_id`
- ...

JDBC `addBatch()` гибче: каждый event добавляется через свой `PreparedStatement.addBatch()`,
даже если поля разные. PostgreSQL JDBC driver оптимизирует через `reWriteBatchedInserts=true`.

### Почему fail-open (потеря при крэше допустима)

`tbl_events` — логирование событий вовлечённости (просмотры, клики, плеер). Это **не
транзакционные данные** — их потеря не влияет на бизнес-логику. Если потеря критична —
нужен persistent queue (отдельная будущая фича, не в скоупе Tier-3).

## Конфигурация (в `application.yml` или `deploy/do.env`)

```yaml
karaoke:
  web:
    events:
      batch-enabled: false  # default: opt-in
      batch-flush-interval-ms: 5000  # default: 5 сек
      batch-max-buffer-size: 500  # default: backpressure trigger
```

## Sister services

- [SamplingFilter](archive/docs/features/site-traffic-resilience.md) — sampling 1/N + dedup
  TTL 30 сек (уменьшает поток ДО EventsBuffer).
- [DedupCache](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DedupCache.kt) — sister in-memory кеш (паттерн
  ConcurrentHashMap + lazy cleanup, аналогично EventsBuffer).

## Runtime-валидация (опционально, делается пользователем)

1. Установить `karaoke.web.events.batch-enabled = true` в `deploy/do.env` или `application.yml`.
2. Перезапустить `karaoke-web`.
3. Нагрузить `/api/public/zakroma` 50 запросами за 5 сек.
4. Проверить `pg_log` — должно быть **1 INSERT** (multi-statement batch), а не 50.
5. Проверить docker logs — должны быть строки `[EventsBuffer] flushed N events in X ms`.

## Backward-compat

**Полная совместимость при выключенном kill-switch (default)**:
- SQL-формирование идентично прежнему (escape через `rightFileName()` + referer).
- Все колонки и значения совпадают.
- Семантика `tbl_events` не меняется.

При включении — 5 сек задержка логирования (допустимо для метрик вовлечённости).

## Backlog

- Persistent queue (RabbitMQ / Redis Streams) — если потеря событий при крэше станет критичной.
- Мониторинг/метрики (Prometheus) — буфер size, flush latency, dropped events.
- Multi-row INSERT (если схема `tbl_events` стабилизируется).
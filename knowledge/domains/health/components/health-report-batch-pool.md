# Component: health-report-batch-pool

> **Домен**: [health](../domain.md)
> **Компонента**: `HealthReportBatchPool` — асинхронный пул с приоритетной
> очередью для батч-запросов `healthReportList` (OpenProject #128,
> specs/128-async-health-report-list).

## Ответственность | Responsibility

Решает проблему «каскада синхронных HTTP-запросов» при открытии страницы
админки «Песни». Вместо того чтобы фронт делал N HTTP round-trip'ов
(по одному на песню), он шлёт **один** POST `/api/song/healthReportListBatch`
с массивом id, бэк **мгновенно** отвечает `202 Accepted`, ставит песни в
приоритетную очередь и обрабатывает их в **10 параллельных worker-потоках**.

Готовые отчёты рассылаются через **существующий SSE-канал `HEALTH_REPORTS`**
(payload не изменился — фронт уже умеет).

## Интерфейсы и Контракты | Interfaces and Contracts

### Spring-бин `HealthReportBatchPool`

```kotlin
@Service
class HealthReportBatchPool(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
)
```

| Метод | Сигнатура | Назначение |
|---|---|---|
| `enqueue(songIds: List<Long>)` | `fun` | Добавить песни в приоритетную очередь. Дедуп + move-to-front (см. алгоритм). |
| `queueSize(): Int` | `fun` | Текущий размер очереди. Используется в unit-тестах и диагностике. |
| `parseSongIds(raw: String?)` | `companion fun` | Парсит входную строку формата `"1;2;3"` в `List<Long>`. Невалидные значения молча отбрасываются. |

**internal (для unit-тестов)**:

| Поле | Назначение |
|---|---|
| `executor: ExecutorService` | Подменяется на `DirectExecutorService` в тестах, чтобы изолировать от реального `recomputeAndBroadcast`. |
| `workersEnabled: Boolean` | Если `false` — `enqueue` НЕ запускает worker'ы (для тестов, чтобы не уходить в БД/MinIO). |

### Endpoint `POST /api/song/healthReportListBatch`

- **Параметры**: `songIds: String` — id песен через `;` (конвенция проекта,
  см. `KaraokeProcessAdminController.kt:185` и `Processes/store.js`).
- **Ответ**: `202 Accepted` (без тела).
- **Поведение**: `enqueue(parsedSongIds)` — мгновенно, без ожидания.

## Логика и Алгоритмы | Logic and Algorithms

### Приоритетная очередь

Реализована как `MutableList<Long>` под `ReentrantLock`. Worker'ы берут из
позиции `0`. Семантика:

1. **`enqueue(songIds)`** под `priorityLock`:
   - Идём по `songIds.asReversed()` — это гарантирует, что **первый id** батча
     окажется **на позиции 0** после серии `add(0, ...)` (каждый следующий
     `add(0, id)` сдвигает ранее вставленные вправо).
   - Для каждого id: `remove(id)` (если был в очереди) + `add(0, id)`.
2. **Move-to-front**: если `songId` уже в очереди, он удаляется и вставляется
   в начало — это требование #128.
3. **Дедуп**: `remove(id)` + `add(0, id)` гарантируют, что `songId` встретится
   в очереди **ровно один раз**.

Пример:

```
enqueue([1, 2, 3])   →  priorityQueue = [1, 2, 3]
enqueue([2])         →  priorityQueue = [2, 1, 3]   // 2 — в начало, 1, 3 — сдвинуты
enqueue([4, 5, 6])   →  priorityQueue = [4, 5, 6, 2, 1, 3]  // новый батч впереди
```

### Worker-loop

`Executors.newFixedThreadPool(10)` — 10 постоянных worker-потоков.

Каждый worker в цикле:

1. `takeNext()` — взять `Long?` с позиции `0` под `priorityLock`.
2. Если `null` — `Thread.sleep(50)` и continue (простой).
3. `tryEnter(songId)` — single-flight через `AtomicBoolean.compareAndSet(false, true)`.
   Если `false` (другой worker уже считает эту песню) — пропускаем.
4. `HealthReport.recomputeAndBroadcast(songId, ...)` — пересчёт + SSE-рассылка.
5. `exit(songId)` в `finally` — снять single-flight флаг (ключ НЕ удаляется
   из map — паттерн из `race-fixed-65.md`, избегаем memory churn).

### Single-flight guard

```kotlin
private val inFlight: ConcurrentHashMap<Long, AtomicBoolean> = ConcurrentHashMap()

private fun tryEnter(songId: Long): Boolean =
    inFlight.computeIfAbsent(songId) { AtomicBoolean(false) }
        .compareAndSet(false, true)

private fun exit(songId: Long) {
    inFlight[songId]?.set(false)
}
```

Lock-free через `AtomicBoolean`. Паттерн заимствован из
[race-fixed-65.md](race-fixed-65.md).

### Graceful shutdown

`@PreDestroy`:

```kotlin
executor.shutdown()
if (!executor.awaitTermination(5, SECONDS)) {
    executor.shutdownNow()
}
```

NB: `try/catch` в worker-loop'е — одна проблемная песня не валит весь пул.
Ошибка логируется через SLF4J-категорию `infra.cache.hrpool`.

### Wake-up

`wakeupWorkers()` запускает 10 новых submit'ов в `executor` после каждого
`enqueue`. Если worker уже работает — он возьмёт следующую песню сам;
если простаивает — выполнит одну итерацию (взял null → sleep → выход).
Дубликаты тасков безвредны: `takeNext()` атомарно разбирает очередь.

## Связь с другими компонентами

- **HealthReport** ([health-report.md](health-report.md)):
  worker вызывает `recomputeAndBroadcast(songId, ...)` — та же единая точка
  пересчёта + SSE-рассылки, что используется в синхронном endpoint'е
  `/song/healthReportList`.
- **SseNotificationService** ([sse domain](../../sse/domain.md)):
  `SseNotification.healthReports(songId, dtos)` — broadcast всем
  подписчикам `userId=1`. Payload не изменился.
- **race-fixed-65.md** ([race-fixed-65.md](race-fixed-65.md)):
  паттерн single-flight guard через `ConcurrentHashMap<Long, AtomicBoolean>`
  заимствован отсюда.

## Зависимости | Dependencies

- `KaraokeConnection` (через `WORKING_DATABASE`) — соединение с локальной БД.
  Доступ к песням и их health-report'ам.
- `KaraokeStorageService` — локальное MinIO (для проверки файлов песен).
- `StorageApiClient` (через `StorageApiClientImpl`) — удалённое MinIO
  (для проверки файлов песен на проде).
- `HealthReport.recomputeAndBroadcast` — пересчёт + SSE-рассылка (см.
  [health-report.md](health-report.md)).
- `Executors.newFixedThreadPool(10)` — Java-стандарт, не новая зависимость.
- `ReentrantLock`, `ConcurrentHashMap`, `AtomicBoolean` — Java-стандарт.

## Конвенция `;`-separated id

Используется во всём проекте для передачи массивов чисел из webvue3 в
karaoke-app. См.:

- `KaraokeProcessAdminController.kt:185` — `ids.split(";").mapNotNull { it.trim().toIntOrNull() }`.
- `webvue3/src/components/Processes/store.js` — `ids.join(';')`.

## Код (физическая реализация)

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPool.kt` (~190 строк).
- Endpoint: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` —
  сразу после строки `/song/healthReportList` (Pass 128).
- Frontend action: `webvue3/src/components/Songs/store.js` — `loadHealthReportBatch`.
- Frontend каскад: `webvue3/src/components/Songs/SongsTable.vue` —
  `_enqueueHrBatch` + `_collectMissingHrSongIds`.

## Тесты

`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPoolTest.kt` —
10 unit-тестов (parseSongIds, enqueue, dedup, move-to-front, batch-at-front,
concurrent enqueue). Все 10/10 PASS за ~1.1s.

## Связанные ADR | Related ADRs

- `archive/docs/features/monitoring.md` — оригинальный документ,
  archive (НЕ Knowledge). Требует миграции в Knowledge.
- `archive/docs/features/dual-db-sync.md` — упомянут в KDoc.

## История изменений

- **Pass 128** (2026-09-15): Initial. Автор: agent (Karaoke).
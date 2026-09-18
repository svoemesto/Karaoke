# Feature Specification: Real pool WAITING-задач HR с воркерами (Pass 132, #132)

**Feature Branch**: `408-hrwaiting-pool`
**Created**: 2026-09-18
**Status**: Implemented (In review)
**Input**: OpenProject #132 «Бейдж количества WAITING-задач - 2». Задача #130 работает
не так, как нужно владельцу.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#132`.
- **Title**: «Бейдж количества WAITING-задач - 2».
- **Parent map**: `#133` (`[wayfinder:map] #132 Real pool WAITING-задач HR с воркерами`).
- **Дочерние тикеты карты**: #137 (backend), #138 (frontend), #139 (cleanup #130),
  #140 (tests).
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 137` (backend-тикет, In progress).
  2. **Add comment с отчётом** (после merge): `bash tools/tracker.sh add-comment 137 --file specs/132-hrwaiting-pool/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 137`.
  4. **Close** (owner, после merge + рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 137` и `close-issue 132`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-18
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -ril 'waiting' knowledge/domains/health knowledge/domains/caching knowledge/domains/sse` → `knowledge/domains/sse/domain.md` (`HEALTH_REPORT_WAITING_COUNT`).
  2. `grep -rn 'HealthReportBatchPool|priorityQueue|enqueue' knowledge/` → `knowledge/domains/health/components/health-report-batch-pool.md` (контракт пула, move-to-front, single-flight, `@PreDestroy`).
  3. `grep -rn 'pool|очеред|queue' knowledge/domains/caching/components/*.md` → 0 hits (пул — не caching-паттерн, а processing-паттерн).
  4. `grep -rn 'WAITING' karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` → единственное место создания WAITING — `actionsRemoteStorage` (cache miss REMOTE).
  5. `grep -rn 'attachStorageMetadataCache|attachStorageCircuitBreaker' karaoke-app/src/main/kotlin/` → паттерн bridge-бина (`*Wiring.kt @PostConstruct`).

### Knowledge files consulted

- [`knowledge/domains/health/components/health-report-batch-pool.md`](../../knowledge/domains/health/components/health-report-batch-pool.md)
  — контракт `HealthReportBatchPool` (Pass 128): `priorityQueue` под `priorityLock`,
  `executor` (10), `inFlight` single-flight, move-to-front, `@PreDestroy`.
- [`knowledge/domains/health/domain.md`](../../knowledge/domains/health/domain.md)
  — `HealthReportStatus.WAITING` (#FFCCFF, спека #368), `recomputeAndBroadcast` —
  единая точка пересчёта + SSE.
- [`knowledge/domains/sse/domain.md`](../../knowledge/domains/sse/domain.md)
  — таблица SSE-типов, broadcast, `HEALTH_REPORT_POOL_COUNT`, `HEALTH_REPORTS`.
- [`knowledge/domains/caching/components/caching-patterns.md`](../../knowledge/domains/caching/components/caching-patterns.md)
  — single-flight guard, async cold-start.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — `StorageMetadataCache` / `cachedFileExistsAsync`, cache miss.

### Прецедент

#130 (specs/130-hrwaiting-badge) реализовала голубой бейдж как **сумму WAITING-записей
по каждой песне** (`waitingCountBySongId: ConcurrentHashMap<SongId, AtomicLong>`).
Владелец: «Задача 130 работает как-то не так». Требуется **реальный пул**
WAITING-задач на бэке, разгребаемый 20 воркерами; счётчик — размер этого пула
(взял задачу → пул уменьшился → бейдж обновился). Механизм всплытия — как у
зелёного бейджа #129 (`HealthReportBatchPool.priorityQueue` move-to-front).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Бейдж показывает реальный размер пула WAITING-задач (Priority: P1)

**Why this priority**: Это прямая просьба владельца: голубой бейдж — размер пула
WAITING-задач, а не сумма-суррогат.

**Independent Test**: При cold-start `StorageMetadataCache` бейдж растёт по мере
поступления WAITING-записей; 20 воркеров разбирают пул — бейдж уменьшается до 0
и скрывается.

**Acceptance Scenarios**:

1. **Given** `recomputeAndBroadcast` вернул N WAITING-записей, **When** они
   ставятся в пул, **Then** размер пула вырос на N, фронт получил SSE
   `HEALTH_REPORT_WAITING_POOL_SIZE`.
2. **Given** пул не пуст, **When** воркер берёт задачу, **Then** размер пула
   уменьшается и фронт получает новое SSE.
3. **Given** пул пуст, **Then** голубой бейдж скрыт (`count == 0`).

### User Story 2 — Всплытие при возврате на посещённую страницу (Priority: P2)

**Independent Test**: Повторный `enqueueWaiting` для задачи, уже стоящей в
очереди, поднимает её в голову (move-to-front), как у зелёного бейджа.

### Edge Cases

- **Разные файлы одной песни** — разные задачи пула.
- **Одна задача взята воркером и снова обнаружена WAITING** — не возвращается
  в пул, пока `waitingInFlight[task] == true` (иначе busy-loop).
- **Ошибка проверки файла в воркере** — логируется, задача завершается,
  пул не падает.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `HealthReportBatchPool` ДОЛЖЕН содержать второй пул
  `waitingQueue: LinkedBlockingDeque<WaitingFileTask>` + `waitingExecutor`
  (`Executors.newFixedThreadPool(20)`) + `waitingInFlight` для single-flight.
  **Одно задание = один файл** (`WaitingFileTask(songId, source, bucket, fileName)`).
- **FR-002**: `enqueueWaiting(tasks)` ДОЛЖЕН поддерживать move-to-front и дедуп
  по задаче-файлу; in-flight задачи пропускаются.
- **FR-003**: Каждое изменение размера `waitingQueue` ДОЛЖНО рассылать SSE
  `HEALTH_REPORT_WAITING_POOL_SIZE` (`{count: Long}`) с подавлением дублей.
- **FR-004**: `HealthReport.recomputeAndBroadcast` ДОЛЖЕН для каждой WAITING-записи
  (несущей `waitingFileTask`) вызвать `enqueueWaiting` (батчем) через статическую
  ссылку `healthReportBatchPool`.
- **FR-005**: `@PreDestroy` ДОЛЖЕН shutdown'ить **оба** executor'а (иначе executor leak).
- **FR-006**: Старая SUM-реализация #130 (`waitingCountBySongId`,
  `lastSentWaitingCount`, `HealthReportWaitingCountMessage`,
  `HEALTH_REPORT_WAITING_COUNT`, Vuex `healthReportWaitingCount`) ДОЛЖНА быть удалена.
- **FR-007**: Фронт (`Processes/store.js`, `App.vue`, `ProcessWorker.vue`) ДОЛЖЕН
  показывать `healthReportWaitingPoolSize` голубым бейджем, скрытым при `0`.
- **FR-008**: Worker пула ДОЛЖЕН **сам** синхронно выполнять проверку файла и
  заполнять `StorageMetadataCache` (не fire-and-forget) — иначе с
  `cacheFillerExecutor` (unbounded-очередь, `corePoolSize=0`) параллелизма нет.
- **FR-009**: После заполнения кеша worker ДОЛЖЕН поставить песню в song-пул
  (`enqueue`) для пересчёта HR — без дублирования `recomputeAndBroadcast`.

### Key Entities

- **`HealthReportBatchPool.WaitingFileTask`** — `(songId, source, bucket, fileName)`.
- **`HealthReportWaitingPoolSizeMessage(count: Long)`** — SSE-payload.
- **`SseNotificationType.HEALTH_REPORT_WAITING_POOL_SIZE`** — новый тип события.
- **`HealthReport.waitingFileTask`** — задача-файл для WAITING-записи.
- **`StorageMetadataCache.peekFileExists`** — неблокирующее чтение кеша.

## Success Criteria *(mandatory)*

- **SC-001**: 20/20 unit-тестов `HealthReportWaitingPoolTest` PASS; 13/13
  `HealthReportBatchPoolTest` PASS.
- **SC-002**: `:karaoke-app:compileKotlin`, `ktlintCheck`, JPA/MP4 guards PASS.
- **SC-003**: `webvue3 npm run lint` + `npm run build` PASS.
- **SC-004**: Knowledge SSoT обновлён (`sse/domain.md`, `health-report-batch-pool.md`,
  `health/domain.md`).

## Assumptions

- `HealthReport` — companion object без конструктора; ссылка на пул идёт через
  статическое поле + `@PostConstruct` (паттерн `StorageMetadataCacheWiring`).
- Число worker'ов 20 — фиксированное (не `cachedThreadPool`), чтобы не исчерпать
  Postgres connection pool.
- Голубой цвет `#17a2b8` — консистентность с #130.

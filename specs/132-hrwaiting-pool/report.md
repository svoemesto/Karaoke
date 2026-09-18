# Report #132 — Real pool WAITING-задач HR с воркерами (Pass 132)

> **OpenProject**: #132 «Бейдж количества WAITING-задач - 2».
> **Parent map**: #133 (`[wayfinder:map] #132 Real pool WAITING-задач HR с воркерами`).
> **Spec**: [spec.md](spec.md).
> **Branch**: `408-hrwaiting-pool`.
> **Status**: In review.

## Что сделано

### Backend (`karaoke-app`)

- **`HealthReportBatchPool.kt`** — расширен вторым пулом WAITING-задач:
  - companion `POOL_SIZE_WAITING = 20`.
  - `WaitingFileTask(songId, source, bucket, fileName)` — **одно задание = один файл**.
  - `waitingQueue: LinkedBlockingDeque<WaitingFileTask>` (thread-safe, без внешнего lock).
  - `waitingExecutor: ExecutorService = Executors.newFixedThreadPool(20)`.
  - `waitingInFlight: ConcurrentHashMap<WaitingFileTask, AtomicBoolean>` — single-flight.
  - `enqueueWaiting(tasks)` — move-to-front, дедуп, пропуск in-flight.
  - `waitingQueueSize()`, `takeWaitingNext()` (`pollFirst` — LIFO-голова).
  - `waitingWorkerLoop()` — берёт задачу → SSE-обновление размера →
    **сам синхронно** проверяет файл (`HealthReport.cachedFileExists`) и
    заполняет `StorageMetadataCache` → ставит песню в song-пул (`enqueue`).
  - companion `lastSentWaitingPoolSize` + `sendWaitingPoolSizeMessage(count)` (dedup).
  - `@PostConstruct` привязывает пул к `HealthReport` и стартует 10+20 worker'ов.
  - `@PreDestroy` shutdown'ит **оба** executor'а (fix executor leak).
  - `waitingWorkersEnabled` — test-флаг.

- **`SseNotificationType.kt`** — удалён `HEALTH_REPORT_WAITING_COUNT`, добавлен
  `HEALTH_REPORT_WAITING_POOL_SIZE("healthReportWaitingPoolSize")`.

- **`Messages.kt`** — `HealthReportWaitingCountMessage` заменён на
  `HealthReportWaitingPoolSizeMessage(count: Long)`.

- **`SseNotification.kt`** — helper `healthReportWaitingPoolSize(...)`.

- **`HealthReport.kt`**:
  - Удалена SUM-реализация #130 (`waitingCountBySongId`, `lastSentWaitingCount`,
    `sendWaitingCountMessage`, `waitingCountTotal`, test-hooks) и импорт `AtomicLong`.
  - Добавлена статическая ссылка `healthReportBatchPool` + `attachHealthReportBatchPool`.
  - Поле `waitingFileTask` в `HealthReport` (задача-файл для WAITING-записи).
  - `actionsRemoteStorage`: fire-and-forget `cachedFileExistsAsync` заменён на
    `peekCachedFileExists` (только чтение кеша) + WAITING-запись с `waitingFileTask`.
  - `recomputeAndBroadcast` после SSE `HEALTH_REPORTS` вызывает
    `enqueueWaitingTasks(reports)` — фильтр WAITING + `waitingFileTask` +
    батч `pool.enqueueWaiting(tasks)`.

- **`StorageMetadataCache.kt`** — добавлен `peekFileExists` (неблокирующее
  чтение кеша без fill).

- **`HealthReportWaitingCountTest.kt`** — удалён (6 тестов SUM-подхода).

- **`HealthReportWaitingPoolTest.kt`** (новый, 16 тестов): enqueueWaiting
  (голова/дедуп/move-to-front/разные файлы/фильтр/concurrent), single-flight,
  SSE-dedup, attach, `stop` обоих executor'ов.

### Root cause «разгребается в один поток» (диагностика)

Первый прогон показал: все `cache:filled` шли из `pool-2-thread-1`, а 20
waiting-воркеров простаивали. Причина — fire-and-forget `cachedFileExistsAsync`
уходил в `StorageMetadataCache.cacheFillerExecutor` =
`ThreadPoolExecutor(corePoolSize=0, maxPoolSize=4, unbounded LinkedBlockingDeque)`.
С безразмерной очередью Java **не поднимает больше одного потока** (рост только
при заполненной очереди) — thread dump подтвердил:
`getFileExistsAsync$lambda$0 → recomputeAndBroadcast → ...actionsRemoteStorage`.
Фикс: worker пула сам синхронно проверяет файл (FR-008), fire-and-forget для
REMOTE заменён на `peekFileExists` (FR-004/FR-009).

**Backend hard gates**:
- `:karaoke-app:compileKotlin` — PASS.
- `:karaoke-app:ktlintCheck` — PASS.
- `HealthReportWaitingPoolTest` — **16/16 PASS**.
- `HealthReportBatchPoolTest` — **13/13 PASS**.
- `check-no-jpa-imports`, `check-no-mp4-mentions` — PASS.

### Frontend (`webvue3`)

- **`App.vue`** — case `HEALTH_REPORT_WAITING_POOL_SIZE` → `setHealthReportWaitingPoolSize`.
- **`Processes/store.js`** — вместо `healthReportWaitingCount`:
  state/getter/mutation/action `healthReportWaitingPoolSize` (initial `0`).
- **`ProcessWorker.vue`** — computed `healthReportWaitingPoolSize` +
  `showWaitingPoolBadge` (`count > 0`), `<div v-show=... class="text-count-waiting-blue">`,
  CSS `.text-count-waiting-blue` (`#17a2b8`, правый верхний угол).

**Frontend hard gates**:
- `npm run lint` — PASS.
- `npm run build` — PASS (built in 7.55s).

### Knowledge SSoT

- `knowledge/domains/sse/domain.md` — `HEALTH_REPORT_WAITING_COUNT` заменён на
  `HEALTH_REPORT_WAITING_POOL_SIZE`.
- `knowledge/domains/health/components/health-report-batch-pool.md` — секция
  «Два пула», методы, тесты, changelog.
- `knowledge/domains/health/domain.md` — упоминание второго пула.

## Acceptance criteria

| # | Критерий | Статус |
|---|---|---|
| FR-001 | Второй пул + 20 worker'ов + single-flight | OK |
| FR-002 | `enqueueWaiting` move-to-front + дедуп + skip in-flight | OK |
| FR-003 | SSE `HEALTH_REPORT_WAITING_POOL_SIZE` с dedup | OK |
| FR-004 | `recomputeAndBroadcast` enqueue WAITING-задач | OK |
| FR-005 | `@PreDestroy` shutdown обоих executor'ов | OK |
| FR-006 | Старая SUM-реализация #130 удалена | OK |
| FR-007 | Frontend бейдж (hidden при 0) | OK |
| FR-008 | Worker сам синхронно проверяет файл (не fire-and-forget) | OK |
| FR-009 | После fill — постановка песни в song-пул | OK |
| SC-001 | 16/16 + 13/13 тестов | OK |
| SC-002 | Kotlin/ktlint/guards | OK |
| SC-003 | lint + Vite build | OK |
| SC-004 | Knowledge SSoT | OK |

## Что осталось владельцу

1. CI: дождаться PASS на PR.
2. Merge: `gh pr merge --merge` (без `--delete-branch`).
3. Docker: `deploy/do.sh build_karaoke-app && build_webvue3`.
4. Deploy (по согласию AGENTS.md Tier-1).
5. Ручная проверка: при cold-start `StorageMetadataCache` голубой бейдж растёт и
   убывает синхронно с разбором пула 20 воркерами.
6. `tracker.sh close-issue 137` и `close-issue 132`.

## Известные ограничения

- `waitingQueue` — in-memory, сбрасывается при рестарте бэкенда.
- Пул пока наполняется только для REMOTE-storage cache miss (единственный
  источник `WAITING`, см. `actionsRemoteStorage`). LOCAL_* не дают WAITING.
- `getFileExistsAsync`/`cachedFileExistsAsync` остаются публичными примитивами
  кеша, но health-flow их больше не использует.
- Проверка в воркере — синхронная (`fileExists` может идти секунды при медленном
  MinIO); `min 20` файлов обрабатываются параллельно.

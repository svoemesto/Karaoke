# Report #132 — Бейдж количества WAITING-задач (реальный пул с 20 воркерами)

> **OpenProject**: #132 «Бейдж количества WAITING-задач - 2».
> **Parent map**: #133.
> **PR**: [#514](https://github.com/svoemesto/Karaoke/pull/514) (merged, merge-commit `7b4fa901`).
> **Branch**: `411-combined-all`.
> **Spec**: [specs/132-hrwaiting-pool/spec.md](../../specs/132-hrwaiting-pool/spec.md).
> **Status**: Done, контейнер `karaoke-app` перезапущен, проверено на проде.

## Итог одной строкой

Синий бейдж у кнопки Старт/Стоп показывает **реальный размер backend-пула
WAITING-задач** (`HealthReportBatchPool.waitingQueue`), который разгребают
**20 worker-потоков**; каждое изменение рассылается по SSE. Задача #130 с
суммой WAITING-записей удалена как неверная.

## Что сделано

### Backend (`karaoke-app`)

- **`HealthReportBatchPool`** — второй пул WAITING-задач:
  - `waitingQueue: LinkedBlockingDeque<WaitingFileTask>` — **одно задание = один файл**;
  - `waitingExecutor` = `Executors.newFixedThreadPool(20)`;
  - `waitingInFlight: ConcurrentHashMap<WaitingFileTask, AtomicBoolean>` — single-flight;
  - `enqueueWaiting(tasks)` — move-to-front (всплытие), дедуп, пропуск in-flight;
  - `waitingWorkerLoop()` — берёт задачу → **сам синхронно** проверяет файл и
    заполняет `StorageMetadataCache` → ставит песню в song-пул (`enqueue`) на
    пересчёт HR/SSE;
  - `parseWaitingTask` / `WaitingFileTask(songId, source, bucket, fileName)`;
  - `@PreDestroy` shutdown'ит **оба** executor'а (fix executor leak Pass 128).
- **SSE**: новый тип `HEALTH_REPORT_WAITING_POOL_SIZE` + payload
  `HealthReportWaitingPoolSizeMessage(count)` + helper `healthReportWaitingPoolSize`,
  рассылка с подавлением дублей (`lastSentWaitingPoolSize`).
- **`HealthReport`**: поле `waitingFileTask` у WAITING-записи; `recomputeAndBroadcast`
  батчем ставит задачи в пул; удалена SUM-реализация #130
  (`waitingCountBySongId`, `lastSentWaitingCount`, `HealthReportWaitingCountMessage`,
  `HEALTH_REPORT_WAITING_COUNT`, тест `HealthReportWaitingCountTest`).
- **`StorageMetadataCache.peekFileExists`** — неблокирующее чтение кеша без fill
  (заменило fire-and-forget `cachedFileExistsAsync` в REMOTE-ветке).

### Frontend (`webvue3`)

- `App.vue`: case `HEALTH_REPORT_WAITING_POOL_SIZE` → `setHealthReportWaitingPoolSize`.
- `Processes/store.js`: state/getter/mutation/action `healthReportWaitingPoolSize`.
- `ProcessWorker.vue`: **синий** бейдж (`.text-waiting-pool-size`, `#0d6efd`,
  правый верхний угол кнопки), скрыт при `count == 0`. Голубой бейдж #17a2b8
  удалён как дубль.

### Тесты

`HealthReportWaitingPoolTest` — 16 тестов (enqueueWaiting: голова/дедуп/
move-to-front/разные файлы/фильтр/concurrent; single-flight; SSE-dedup; attach;
`stop` обоих executor'ов). `HealthReportBatchPoolTest` — 13 тестов (регрессия).

## Диагностика по ходу работы (3 отдельных дефекта)

1. **Почему разгребало одним потоком.** Первый прогон показал `pool-2-thread-1`
   вместо 20. Причина: REMOTE-ветка `actionsRemoteStorage` уходила в
   fire-and-forget `cachedFileExistsAsync` → `cacheFillerExecutor`
   (`ThreadPoolExecutor(corePoolSize=0, maxPoolSize=4, unbounded
   LinkedBlockingDeque)`). С безразмерной очередью Java не поднимает больше
   **одного** потока. Фикс: worker пула сам синхронно проверяет файл (FR-008),
   fire-and-forget заменён на `peekFileExists`.

2. **Утечка MinIO-соединения** (не связана с #132). `KaraokeStorageService.downloadFile(path)`
   открывал `GetObjectResponse`, затем `FileOutputStream(file)` падал (нет
   родительского каталога) — поток не закрывался, OkHttp-соединение утекало с
   недочитанным mp3, следующий запрос читал `ID3...Lavf` как HTTP-статус
   (`ProtocolException`). Фикс: `file.parentFile?.mkdirs()` + `.use` вокруг потока
   MinIO **до** `FileOutputStream`; то же в `Utils.syncRemotePicturesInStorage`.

3. **Бесконечный цикл UPLOAD_TO_REMOTE_STORE** (не связан с #132). HealthReport
   решает по persistent-кешу (`REMOTE exists=false` после временного сбоя MinIO),
   а `executeUploadToRemoteStore` проверяет хранилище напрямую — файл там есть,
   загрузка пропускается, но функция возвращала `true` → «DONE успешно» за 100 мс →
   цикл. Фикс: `refreshStorageMetadataCache()` (write-through кеша после
   direct-check), `uploadFile==null` → `false` (ERROR, не ложный DONE), «нечего
   загружать» → `false`; то же для local store.

## Проверки

- `:karaoke-app:compileKotlin`, `:karaoke-app:ktlintCheck` — PASS.
- `HealthReportWaitingPoolTest` 16/16, `HealthReportBatchPoolTest` 13/13 — PASS.
- `check-no-jpa-imports`, `check-no-mp4-mentions` — PASS.
- `webvue3 npm run lint` + `npm run build` — PASS.
- Knowledge SSoT: `sse/domain.md`, `health/domain.md`,
  `health/components/health-report-batch-pool.md`, `health-report.md` обновлены;
  `lint-knowledge` / `check-knowledge-structure` / cross-links — PASS.
- CI PR #514 — 12/12 PASS.

## Прод-проверка (после рестарта)

- Startup: `started with 10 workers + 20 waiting-workers`.
- Thread dump: `pool-4` = 10 (priority), `pool-5` = 20 (waiting), `pool-2` вызова
  практически исчезли (2 упоминания против 434 событий `cache:filled` из 20
  потоков `pool-5-thread-*`).

## Ограничения

- `waitingQueue` — in-memory, сбрасывается при рестарте бэкенда.
- Пул наполняется только для REMOTE-storage cache miss (единственный источник
  `WAITING`).
- `getFileExistsAsync`/`cachedFileExistsAsync` остаются публичными примитивами
  кеша, но health-flow их больше не использует.

## Известные уроки

- Образ `karaoke-app` собирается с **текущей ветки**; при трёх параллельных
  фиксах легко собрать неполный набор (был регресс «опять `pool-2-thread-1`»).
  Фиксы объединены в один PR #514.

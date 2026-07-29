# Async-очередь задач `KaraokeProcess*`

> **Status**: active
> **Feature Key**: async-process-queue
> **Last Updated**: 2026-07-29

## Что делает

Длительные операции (ffmpeg/melt-рендер, Demucs-сепарация, Sheetsage-
распознавание key/BPM/chords, copy/symlink, загрузка в MinIO) запускаются
как OS-подпроцессы через очередь с приоритетами и независимыми thread-
лейнами. Прогресс парсится из stdout и рассылается по SSE.

## Зачем

Все тяжёлые операции в Karaoke — I/O-bound или CPU-bound, длятся от секунд
до десятков минут. Если выполнять их синхронно в HTTP-потоке, сервер
зависнет. Очередь даёт:
- **Независимость** — рендер одной песни не блокирует другую.
- **Приоритизацию** — срочные задачи (например, превью) идут первыми.
- **Прогресс** — UI получает обновления в реальном времени.
- **CPU-лимит** — три уровня ограничения (docker `--cpus` / `MLT_CPU_LIMIT`
  / `docker update`).

## Как работает (кратко)

1. **Задание** — строка `tbl_processes` (модель `KaraokeProcess` с
   enum `KaraokeProcessTypes`).
2. **Приоритет** — числовое поле, чем меньше, тем выше приоритет.
3. **Thread-лейн** — `threadId` группирует задания в независимые
   последовательные очереди:
   - `THREAD_LANE_HEAVY_RENDER=0` — MLT/melt-рендер (тяжёлый, CPU).
   - `THREAD_LANE_LIGHT_BACKGROUND=-1` — копирование, symlink, мелочи.
   - `THREAD_LANE_REMOTE_STORE_UPLOAD=-2` — загрузка в MinIO.
   - `THREAD_LANE_STEM_JOBS=-3` — премиум-стемы (отдельный лейн, чтобы
     не забивать рендер).
4. **Worker** — `KaraokeProcessWorker` берёт задание из очереди,
   создаёт `KaraokeProcessThread` (subprocess через `ProcessBuilder`),
   парсит stdout регексами (ffmpeg `time=`/`Duration:`, Sheetsage `NN%|`)
   и обновляет `percentage` в БД.
5. **SSE** — UI получает `processWorkerState` через `SseNotificationService`.
6. **Functional types** — некоторые типы (`KEY_BPM_FROM_FILE`,
   `UPLOAD_TO_LOCAL/REMOTE_STORE`, `FORCED_ALIGN_MARKERS`) выполняются как
   Kotlin-функция (`runFunctionWithArgs`), не как subprocess.
   `FORCED_ALIGN_MARKERS` (`Utils.executeForcedAlignMarkers`) — фоновый
   аналог кнопки «Точные маркеры» в SubsEdit (см. `alignment-ml/README.md`):
   расставляет маркеры сразу для всех голосов песни и сохраняет результат
   (в отличие от `SongEditorController.editForcedAlignMarkers`, который
   только возвращает черновик на подтверждение фронта). **Нельзя** ставить
   в очередь для песни со статусом `idStatus >= 3` (маркеры уже финальны) —
   проверяется и на постановку в очередь (`ApiController.doProcessForcedAlignMarkers`/
   `getSongsCreateForcedAlignMarkersAll`), и повторно внутри самого
   `executeForcedAlignMarkers` (статус мог измениться, пока задание ждало
   своей очереди). При успехе `idStatus` поднимается до `2`, если он был
   меньше.

## Инварианты / правила

- **MUST**: `ProcessBuilder.redirectErrorStream(true)` ВСЕГДА (см.
  [CONTRIBUTING.md#kotlin-processbuilder-redirect-error-stream](../../CONTRIBUTING.md)).
  `false` ЗАПРЕЩЁН — буфер stderr переполняется.
- **MUST**: каждый новый `KaraokeProcessTypes` имеет явное `runFunctionWithArgs`
  ИЛИ вызов `Utils.execute*` через subprocess. Нет «магических» путей
  исполнения.
- **MUST**: `forceStop` (`stopReason != null`) корректно убивает
  subprocess (`process.destroyForcibly()`), и worker не оставляет
  zombie-процессов.
- **MUST**: после завершения (успешного или с ошибкой) текущего задания
  thread-лейна следующее `WAITING`-задание того же лейна стартует
  автоматически, независимо от результата предыдущего и от состояния
  других лейнов (см. `specs/029-fix-queue-lane-stall/spec.md`). Для этого:
  - `try/catch` в `KaraokeProcessThread.run()` охватывает не только чтение
    stdout, но и сам запуск subprocess (`processBuilder.start()`) — иначе
    задание может навсегда остаться в статусе `WORKING` и заблокировать
    диагностику своего лейна (запись просто выпадает из `WAITING`, но не
    попадает ни в `DONE`, ни в `ERROR`).
  - Общее состояние воркера (`isWork`, `stopAfterThreadIsDone`,
    `withoutControl`, `threadsMap` в `companion object`
    `KaraokeProcessWorker`) мутируется как минимум из двух потоков (цикл
    `doStart()` и HTTP-обработчики `stop()`/`forceStop()`) — поля помечены
    `@Volatile`, `threadsMap` — `ConcurrentHashMap`.
  - `start()` защищён `startStopLock`: атомарная проверка-и-установка
    `isWork` гарантирует, что цикл `doStart()` запускается не более чем в
    одном экземпляре одновременно (иначе два быстрых подряд вызова
    `/api/processes/workerstartstop` могли запустить два параллельных
    воркера на одном `threadsMap`). Сам цикл `doStart()` выполняется в
    отдельном демон-потоке — вызывающий HTTP-поток не блокируется.
  - Если цикл `doStart()` падает с необработанным исключением, `isWork`
    гарантированно сбрасывается в `false` (safety-net `try/catch/finally` в
    `start()`) — иначе очередь выглядела бы «работающей» в UI/мониторинге,
    но фактически была бы мертва.
- **SHOULD**: каждое задание с CPU-нагрузкой > 30 секунд идёт в
  `THREAD_LANE_HEAVY_RENDER`, чтобы не блокировать лёгкие задачи.
- **SHOULD**: регулирование `MLT_CPU_LIMIT` (env-переменная для
  `docker compose`) применяется на admin-машине; на прод-сервере
  ограничение через `docker update` или `--cpus`.

## Известные ловушки

- **`stop-loop`**: пользователь нажимает «Стоп» несколько раз → может
  привести к гонке `process.destroyForcibly()`. Всегда проверяйте
  `stopReason` перед запуском subprocess.
- **`per-thread UI progress`**: `SseNotification` отправляется на
  конкретный `threadId`, а не на всё приложение. Если UI подписан только
  на broadcast, прогресс конкретного потока не виден.
- **Long-running Demucs**: Demucs-сепарация на 4-минутной песне может
  идти 10+ минут. На маломощной admin-машине лучше ставить в очередь
  с низким приоритетом.
- **Sheetsage без GPU**: Sheetsage-распознавание требует GPU или долго
  работает на CPU. Если на admin-машине нет GPU, Sheetsage-задания
  лучше не запускать в рабочие часы.
- **Зависание отдельного лейна** (устранено в specs/029-fix-queue-lane-stall,
  2026-07-29): периодически, особенно при параллельной работе нескольких
  лейнов, следующее задание лейна не стартовало после завершения/ошибки
  текущего. Причина — гонка данных вокруг общего состояния воркера (см.
  инвариант выше) и необработанное исключение при запуске subprocess.
  Защитная сетка на случай будущих регрессий — проверка мониторинга
  `LaneStalledCheck` (см. `docs/features/monitoring.md`), которая
  оповещает про конкретный зависший лейн (не только про полную остановку
  воркера, как более старый `RenderQueueStalledCheck`).

## Ссылки на ключевые классы/файлы

- [`KaraokeProcess.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt) — модель задания
- [`KaraokeProcessTypes.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessTypes.kt) — enum типов
- [`KaraokeProcessStatuses.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessStatuses.kt) — enum статусов
- [`KaraokeProcessWorker.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt) — главный воркер (`class KaraokeProcessWorker`) и обёртка subprocess (`class KaraokeProcessThread`, объявлен в том же файле)
- [`tbl_processes` (`01_initdb.sql`)](../../deploy/karaoke-db/01_initdb.sql) — таблица заданий

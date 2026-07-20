# Async-очередь задач `KaraokeProcess*`

> **Status**: active
> **Feature Key**: async-process-queue
> **Last Updated**: 2026-07-20

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
   `UPLOAD_TO_LOCAL/REMOTE_STORE`) выполняются как Kotlin-функция
   (`runFunctionWithArgs`), не как subprocess.

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

## Ссылки на ключевые классы/файлы

- [`KaraokeProcess.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeProcess.kt) — модель задания
- [`KaraokeProcessTypes.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeProcessTypes.kt) — enum типов
- [`KaraokeProcessWorker.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessWorker.kt) — главный воркер
- [`KaraokeProcessThread.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessThread.kt) — обёртка subprocess
- [`KaraokeProcessWorkerThread.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessWorkerThread.kt) — внутренний воркер
- [`tbl_processes.sql`](../../deploy/karaoke-db/) — таблица заданий

# Async-очередь задач `KaraokeProcess*`

> **Status**: active
> **Feature Key**: async-process-queue
> **Last Updated**: 2026-07-30

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
   - `THREAD_LANE_HEAVY_RENDER=0` — MLT/melt-рендер, ручной запуск
     `DEMUCS*`/`SHEETSAGE` (тяжёлые задачи, нельзя гнать параллельно).
   - `THREAD_LANE_LIGHT_BACKGROUND=-1` — копирование, symlink, мелочи.
   - `THREAD_LANE_REMOTE_STORE_UPLOAD=-2` — загрузка в MinIO.
   - `THREAD_LANE_HEALTH_REPORT=1` — каскад автоисправления `HealthReport`
     (кортеж задач одной песни: демукс → mp3 музыки/голоса → загрузка в
     локальное/удалённое хранилище, см. инвариант ниже).
   - `THREAD_LANE_STEM_JOBS=2` — премиум-фича «Создать минусовку» (StemJob,
     отдельный лейн, чтобы не блокировать и не блокироваться обычным
     пайплайном выпуска песен).
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
  - Если цикл `doStart()` падает с необработанным исключением — до 5 попыток
    возобновить его в том же демон-потоке с нарастающей паузой между ними
    (2с → 5с → 15с → 30с → 60с, `KaraokeProcessWorker.start()`,
    specs/087-fix-shared-db-connection). `isWork` остаётся `true` на время
    retry. Только после исчерпания попыток (или если `isWork` сброшен извне,
    например `forceStop()`, во время паузы) — прежний safety-net
    `try/catch/finally` гарантированно сбрасывает `isWork` в `false`, иначе
    очередь выглядела бы «работающей» в UI/мониторинге, но фактически была
    бы мертва; `RenderQueueStalledCheck` подхватывает как и раньше.
- **MUST**: каждый `KaraokeConnection`-инстанс (в т. ч. `WORKING_DATABASE`)
  кеширует по одному физическому JDBC-соединению **на поток выполнения**
  (`ThreadLocal`, `KaraokeConnection.getConnection()`,
  specs/087-fix-shared-db-connection) — не одно общее на весь инстанс.
  PostgreSQL JDBC `Connection` не рассчитан на конкурентное использование из
  разных потоков; до этой фичи общее соединение между HTTP-потоками и
  потоком очереди приводило к протокольным сбоям (`SocketTimeoutException`/
  «соединение уже закрыто»), ронявшим главный цикл очереди. Self-healing
  (пересоздание при `isClosed`/`!isValid(3)`) применяется к соединению
  текущего потока и не меняет сигнатуру/поведение для вызывающего кода.
- **MUST**: кортеж заданий одной песни, добавленной через «Добавить файлы
  из папки» (демукс → создание mp3 музыки/голоса → загрузка в локальное
  хранилище → загрузка в удалённое хранилище), ДОЛЖЕН целиком оставаться
  в `THREAD_LANE_HEALTH_REPORT` (specs/082-fix-import-folder-oom). Первый
  шаг (`DEMUCS2`) ставится в этот лейн явно в `Song.createFromPath`;
  остальные шаги (`FF_MP3_ACCOMPANIMENT`/`FF_MP3_VOCAL`,
  `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE`) наследуют этот лейн по
  умолчанию через каскад `HealthReport.startRepairAll`/`onRepairProcessFinished`
  (`HealthReport.actionsLocalFileSystem`, дефолт `threadId = THREAD_LANE_HEALTH_REPORT`,
  либо наследование от уже запущенного «родителя» того же кортежа — никогда
  не от постороннего лейна).
  - **Исключение — `KEY_BPM_FROM_FILE` НЕ входит в этот кортеж.** При
    импорте он намеренно ставится в `THREAD_LANE_STEM_JOBS` (см. комментарий
    в `Song.createFromPath` рядом с его постановкой) — определение key/BPM
    ни от чего в кортеже не зависит и ничего в кортеже от него не зависит,
    поэтому не должно занимать слот в лейне кортежа. Расхождение лейнов
    `KEY_BPM_FROM_FILE` (2) и `DEMUCS2` (1) в одной и той же функции —
    это осознанное решение, а не баг рассогласования (см. `research.md`,
    Находка C в specs/082-fix-import-folder-oom).
  - **Не путать N одинаковых строк `tbl_processes` с задвоением.** Один
    логический тип задания (`process_type`) может представлять собой
    многошаговый shell-пайплайн (`Song.args*()`, например `argsDemucs2()`/
    `argsKeyBpmFinder()`) — `KaraokeProcess.separate()` разбивает такой
    пайплайн на N отдельных строк `tbl_processes` (по одной на команду) для
    пошагового отслеживания прогресса; все дочерние строки наследуют
    `threadId` родителя. N строк с одинаковым `settings_id`+`process_type`+
    `thread_id` — это штатное поведение, не дублирование.
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
- **Непоследовательная обработка ошибок БД внутри `doStart()`**: не все
  DB-вызовы в главном цикле ведут себя одинаково при сбое соединения.
  `KaraokeProcess.save()` пробрасывает `SQLException` наружу (что и
  запускает retry, см. инвариант выше), а `KaraokeProcess.getCountWaiting()`/
  `getProcessesToStart()` (`KaraokeProcess.kt`, район строк 480-500 и 779)
  ловят `SQLException` внутри себя и молча возвращают
  `0`/пустой список, не пробрасывая ошибку дальше. При длительном сбое БД
  это означает, что `doStart()` может НЕ упасть вовсе (и retry не
  сработает), а просто тихо крутиться, ничего не делая, пока соединение не
  восстановится само (обнаружено при живой проверке
  specs/087-fix-shared-db-connection, T010 — не баг этой фичи, существующее
  расхождение в обработке ошибок между разными местами `doStart()`).
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
- [`KaraokeConnection.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt) — `getConnection()`, кеш соединения по потоку (`ThreadLocal`)
- [`HealthReport.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt) — каскад автоисправления (`startRepairAll`/`onRepairProcessFinished`), формирует кортеж заданий одной песни
- [`Song.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — `createFromPath` (постановка первых шагов кортежа при импорте из папки), `args*()` (шаги shell-пайплайнов для `KaraokeProcess.separate()`)
- [`tbl_processes` (`01_initdb.sql`)](../../deploy/karaoke-db/01_initdb.sql) — таблица заданий

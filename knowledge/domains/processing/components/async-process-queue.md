# Component: async-process-queue

> **Домен**: [processing](../domain.md)
> **Компонента**: описание `KaraokeProcess` + `KaraokeProcessWorker` +
> async-очереди задач karaoke-app.

## Ответственность | Responsibility

**Async-очередь задач** для karaoke-app. Каждое длительное действие
(рендер, стем-сепарация, upload, smart copy, ...) представлено как
запись `KaraokeProcess` в `tbl_processes` со статусом
`WAITING/WORKING/DONE/ERROR`. Несколько worker-потоков запускают
subprocess (`ProcessBuilder`) и парсят stdout.

Используется ВЕЗДЕ, где есть длительная операция:

- MLT-генерация и рендер (`MELT_LYRICS`, `RENDER_MP4_*`).
- Стем-сепарация (`DEMUCS2`/`DEMUCS5`/`STEM_JOB_DEMUCS*`).
- Sheetsage/`AudioAnalize` (`SHEETSAGE`, `SHEETSAGE2`).
- Upload в MinIO (`UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE`).
- Forced alignment маркеров (`FORCED_ALIGN_MARKERS`).
- HealthReport repair-actions.
- Premium StemJob (создание минусовки).

**Граница**: контекст НЕ отвечает за:

- Содержимое самих задач (что рендерить, что стемить) — это другие
  домены.
- Управление БД как таковой (это KaraokeConnection).
- UI прогресс-бар (это SSE + Vuex в webvue3).

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`KaraokeProcess`** | Задание в очереди. Класс-модель (`tbl_processes`) с аннотациями `@KaraokeDbTableField` | `KaraokeProcess.kt:49` |
| **`KaraokeProcessThread`** | Java-поток, обёртка вокруг subprocess | `KaraokeProcessWorker.kt:71` |
| **`KaraokeProcessWorker`** | Главный воркер (singleton, `companion object`). Создаёт потоки, синхронизирует БД, цикл sync | `KaraokeProcessWorker.kt:531` |
| **`KaraokeProcessStatuses`** | `CREATING` / `WAITING` / `WORKING` / `DONE` / `ERROR` | `KaraokeProcessStatuses.kt` |
| **`KaraokeProcessTypes`** | Конкретные типы (MELT_LYRICS, DEMUCS2, UPLOAD_TO_*, ...) | `KaraokeProcessTypes.kt` |
| **`threadId`** | Lane (`Int`): какой worker-поток берёт задание (см. ниже) | `KaraokeProcess.kt:106` |
| **`HR_REPAIR_PROCESS_TYPES`** | Set типов, которые вызывают `HealthReport.onRepairProcessFinished` | `HealthReport.kt` (см. P0 gaps) |
| **`processChainId`** | ID родительского задания (цепочки) | `KaraokeProcess.kt` |
| **`runFunctionWithArgs`** | Маркер «это Kotlin-функция, а не subprocess» | `KaraokeProcess.kt` (args[0][0]) |

### Thread-lanes

Lane определяется через `threadId` (Long). Один lane = один worker-поток.

| Lane (`threadId`) | Назначение | Константа |
|---|---|---|
| `0` | Тяжёлый рендер (MELT_*, DEMUCS*, SHEETSAGE, RENDER_MP4_*) — НЕЛЬЗЯ параллелить | `THREAD_LANE_HEAVY_RENDER` |
| `-1` | Лёгкий фон: SmartCopy, uploadToLocalStore | `THREAD_LANE_LIGHT_BACKGROUND` |
| `-2` | Upload в remote MinIO (сетевой) | `THREAD_LANE_REMOTE_STORE_UPLOAD` |
| `1` | HealthReport repair (кроме MELT_*, которые на 0) | `THREAD_LANE_HEALTH_REPORT` |
| `2` | Premium StemJob (своя очередь, чтобы не блокировать пайплайн) | `THREAD_LANE_STEM_JOBS` |

**Архитектурное решение**: один worker-поток на lane = **гарантия
serial execution** внутри lane. Тяжёлые рендеры (`0`) идут строго по
одному, потому что MLT/Demucs жрут весь CPU и RAM.

## Интерфейсы и Контракты | Interfaces and Contracts

### `KaraokeProcess.createProcess(...)` — постановка задания

```kotlin
KaraokeProcess.createProcess(
    song = song,
    action = KaraokeProcessTypes.MELT_LYRICS,
    doWait = false,
    prior = 1,
    threadId = THREAD_LANE_HEAVY_RENDER,
    context = emptyMap(),
): Long
```

- `action` — `KaraokeProcessTypes` (`MELT_LYRICS`, `DEMUCS2`, `UPLOAD_TO_*`, ...).
- `doWait = true` — задание сразу создаётся в `WAITING` и блокирует
  вызывающий поток до DONE/ERROR (используется в HealthReport для
  атомарности repair).
- `threadId` (`Int`) — lane задания (см. таблицу lanes выше).
- `context` — доп. параметры; для `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE`
  `karaokeFileType` уточняет задачу, чтобы загрузки разных файлов одной
  песни не затирали друг друга.
- Single-flight по `(song_id, process_type, thread_id)`: если такой процесс
  уже в `WORKING` — возврат `0`; иначе не-WORKING записи удаляются и
  создаётся новая. Для `KEY_BPM_FROM_FILE` при уже валидном файле процесс
  не создаётся (возврат `0`).
- Возврат: id созданного процесса; `0` — процесс уже в работе либо
  сработал skip; `-1` — отдельные ветки «файл-назначение уже существует».

### Прочие точки входа `KaraokeProcess` (companion object)

| Метод | Контракт |
| --- | --- |
| `separate(parentProcess): List<KaraokeProcess>` | Разворачивает цепочку: если у процесса одни args — возвращает его же, иначе создаёт дочерние. |
| `deleteDone(database)` | Удаляет завершённые задания. |
| `setWorkingToWaiting(database)` | Recovery после рестарта: все `WORKING` → `WAITING`. |
| `setWorkingToWaitingForThread(database, threadId)` | Точечный сброс `WORKING` → `WAITING` только для одного lane. |

### `KaraokeProcessWorker` (companion object, singleton)

| Метод / свойство | Контракт |
| --- | --- |
| `start(database, storageService, storageApiClient)` | Под `startStopLock` защищает от двойного запуска: `deleteDone` + `setWorkingToWaiting`, затем **одноразовый** поток с главным циклом `doStart` (создаётся заново на каждый `start()`). |
| `stop()` | Мягкая остановка: ждёт завершения текущей цепочки (`stopAfterThreadIsDone`). |
| `forceStop()` | Жёсткая остановка всей очереди: взводит `forceStopped`, убивает docker-контейнеры, возвращает незавершённые задания в `WAITING`. Аргументов нет — это остановка очереди, а не отдельного задания. |
| `isWork`, `stopAfterThreadIsDone` | `@Volatile`-флаги состояния воркера (читаются HTTP-контроллерами). |
| `threadsMap` | `ConcurrentHashMap<Int, KaraokeProcessThread?>` — живые потоки по lane. |

### HTTP-контракты управления очередью

| Метод / URL | Контракт |
| --- | --- |
| `GET /process/start`, `GET /process/stop` | Запуск/мягкая остановка воркера (`MainController`). |
| `GET /process/deletedone` | `KaraokeProcess.deleteDone(WORKING_DATABASE)`. |
| `GET /process/isworking`, `GET /process/isstopafterthreadssdone` | Текущее состояние воркера. |
| `POST /processes/workerstartstop` | Инвертирует состояние: если `isWork` — `stop()`, иначе `start(...)`. |
| `POST /processes/workerforcestop` | Жёсткий `forceStop()`. |
| `POST /processes/workerstatus` | `{isWork, stopAfterThreadIsDone}`. |
| `POST /processes/deletedone` | `KaraokeProcess.deleteDone(WORKING_DATABASE)`. |
| `/api/admin/processes` | `KaraokeProcessAdminController`: `GET` (list), `GET /{id}`, `POST /{id}/edit`, `POST /{id}/delete`, `POST /{id}/retry`, `GET /{id}/audit`, `POST /bulk-update`, `POST /bulk-delete`, `POST /bulk-update-async`, `POST /bulk-delete-async`, `GET /bulk/snapshot`. |

Статусы — `KaraokeProcessStatuses` (`CREATING`/`WAITING`/`WORKING`/`DONE`/`ERROR`),
типы — `KaraokeProcessTypes`.

## Логика и Алгоритмы | Logic and Algorithms

### Жизненный цикл задания

```
CREATE
  └─► INSERT в tbl_processes со status=CREATING
       └─► status = WAITING
            └─► KaraokeProcessWorker видит (poll БД)
                 └─► создаёт KaraokeProcessThread
                      └─► status = WORKING, start=now()
                           └─► ProcessBuilder.start() / runFunctionWithArgs()
                                └─► читает stdout, парсит percentage
                                     └─► subprocess завершился
                                          ├─► exit 0 → status = DONE
                                          ├─► exit ≠ 0 → status = ERROR
                                          └─► если forceStopped → status = WAITING (переигровка)
                                                └─► post-хук:
                                                     └─► если тип ∈ HR_REPAIR_PROCESS_TYPES
                                                          → HealthReport.onRepairProcessFinished(songId)
```

`forceStopped` — флаг, выставляемый извне ДО убийства subprocess.
Используется, чтобы при ручной отмене задание пошло в
`WAITING` (а не `ERROR`) и было перезапущено.

### Код возврата и ожидание завершения

С аудита #336 (OpenProject #364, BR-029) воркер не «проглатывает»
результат внешнего процесса:

- после EOF слитого stdout (`redirectErrorStream(true)`, ADR-0006)
  вызывается `awaitProcessExit(...)` (`ProcessExitGuard.kt`):
  `waitFor(timeout)`, таймаут по умолчанию 60 с
  (`KARAOKE_PROCESS_EXIT_TIMEOUT_SECONDS`), при превышении —
  `destroyForcibly()` и `ProcessExitException`;
- `exitValue() != 0` → `ProcessExitException` → статус `ERROR`
  (`processExitFailure`): в сообщении есть команда, код, распознавание
  типовых кодов docker/ffmpeg/melt и хвост stdout/stderr (stderr в нём
  потому, что потоки слиты — R-43/ADR-0006);
- единственное исключение из правила — `SHEETSAGE` с известным
  `NotImplementedError` в последней строке: для него штатно создаётся
  `SHEETSAGE2`, и ненулевой код не переводит задание в `ERROR`;
- force-stop по-прежнему даёт `WAITING` (переигровка), а не `ERROR`.

До исправления код возврата не проверялся вообще (`waitFor`/`exitValue`
в файле отсутствовали): любой ненулевой exit от `docker compose run`,
`ffmpeg` или `melt` давал «успешно завершено» и `DONE`.

### Парсинг stdout

Известные regex'ы (см. KDoc + парсер в `KaraokeProcessThread.run()`):

- **ffmpeg**: `time=HH:MM:SS.ms` → конвертация в процент от total duration.
- **Sheetsage**: `NN%|` (прогресс-бар).
- **Demucs**: `100%|##########| ...`.
- **MLT melt**: `--progress` (если есть).

NB: каждое обновление `percentage` пишется в БД. На горячих задачах
(большие файлы) это создаёт много UPDATE'ов → нужен batch.

### Цепочка шагов, атомарный claim и прогресс (аудит #336, PKG-S2-10)

С пакета `PKG-S2-10` (OpenProject #373, BR-030..BR-033) у задания
появились три контракта, которых раньше не было.

**1. Один писатель на строку прогресса (BR-030).**
Цикл `doStart` больше не вызывает полный `save()` для прогресса: он зовёт
`KaraokeProcess.saveProgress()`, который делает узкий `UPDATE` только
percentage-колонок с гардом `WHERE id = ? AND process_status = 'WORKING'`.
Терминальные поля (`process_status`, `process_end`, `process_priority`)
пишет только поток задания. Раньше два потока сохраняли один объект без
синхронизации, и запоздавший `UPDATE` из `doStart` возвращал строку в
`WORKING` после `DONE` (лейн зависал до рестарта).

**2. Атомарный claim (BR-031).**
Поток задания стартует с `KaraokeProcess.claimForWorking()`:
`UPDATE tbl_processes SET process_status='WORKING', process_start=? WHERE id=? AND process_status='WAITING'`
с проверкой «ровно 1 строка». Если строка уже не `WAITING` — поток не
работает. `createProcess()` больше не удаляет `WAITING`/`CREATING`:
активное задание — это single-flight (`return 0`), пересоздаются только
терминальные (`DONE`/`ERROR`). Раньше удалялось всё, кроме `WORKING`:
пока выбранный шаг ещё не записал `WORKING`, второй вызов удалял его
строку, а у выполняющейся цепочки — её `WAITING`-шаги 2..N.

**3. Цепочка как единица исполнения (BR-032).**
`KaraokeProcess.separate()` проставляет шагам `process_order = 0..N-1` и
общий `process_chain_id` (id головного шага). Воркер выбирает
`WAITING` по `(priority, order, id)`, поэтому шаг N+1 не стартует, пока
N не `DONE`. При `ERROR` шага `stopChainSiblingsOnError()` переводит
оставшиеся `WAITING`/`CREATING`-шаги в `ERROR`, а агрегированный статус
песни (`updateStatusProcessSong`) обновляется по цепочке только когда
незавершённых шагов не осталось (нетерминальные `WORKING`/`CREATING`
по-прежнему видны песне сразу). Раньше `DONE` первого шага сразу
выставлял песне `DONE`, хотя 720p и симлинки ещё не были сделаны.

**4. Валидация `process_args` (BR-033).**
`ProcessArgsPolicy.validationError()` требует «непустой список непустых
списков». `createProcess()` отклоняет невалидное задание до вставки, а
цикл `doStart` переводит уже существующую невалидную строку в `ERROR` с
причиной в описании. Раньше `args[0]` на пустом списке ронял главный цикл
(5 retry → `isWork=false`, вся очередь вставала), а `args=[[]]` навсегда
блокировал лейн.

### Синхронизация (LOCAL ↔ SERVER)

`KaraokeProcess` участвует в **two-DB sync** через `SyncRegistry`:

- 8 флагов `sync_process_*_<push|pull>_<insert|update|delete|move>_allowed`.
- См. [two-db-sync](two-db-sync.md) (в том же
  домене — sibling component, не отдельный P1).

Поскольку `tbl_processes` синхронизируется, статус задания в karaoke-web
**виден** через sync — webvue3 показывает прогресс (через SSE).

### Force-stop

`KaraokeProcessWorker.forceStop()` — жёсткая остановка всей очереди:

1. Взвести `thread.forceStopped = true` всем живым потокам из `threadsMap`
   (ДО убийства subprocess).
2. `isWork = false`, `stopAfterThreadIsDone = true` — главный цикл `doStart`
   выходит и не перезапускает уже убитые задания.
3. Убить docker-контейнеры выполняющихся заданий (`killRunningDockerContainers`).
4. Каждый поток: `status = WAITING` + `save()`, `osProcess?.destroyForcibly()`,
   `interrupt()`.

Per-process force-stop в коде нет: остановка адресуется очереди целиком.
Мягкая альтернатива — `stop()` (ждёт завершения текущей цепочки).

### Потокобезопасность

`forceStopped` и `osProcess` помечены `@Volatile` — читаются из
другого потока (force-stop).

`threadsMap` — `ConcurrentHashMap<Int, KaraokeProcessThread?>`, ключ — lane
(`threadId`). Запись в `isWork` защищена `startStopLock`, чтобы два быстрых
`start()` не подняли два параллельных воркера.

## Зависимости | Dependencies

- **HealthReport**: `HealthReport.actionsLocalFileSystem/...`
  добавляют `KaraokeProcess.createProcess(...)` в `solutionActions`.
  После DONE — `HealthReport.onRepairProcessFinished`.
- **Two-DB sync**: см. P1 компоненту.
- **SSE**: статус/percentage рассылаются через `KaraokeWebService`
  (см. P2).

## Известные TODO

- [ ] **`KaraokeProcessAdminController`** — admin UI для управления
      очередью (приоритеты, force-stop, retry).
- [ ] **`KaraokeProcessAdminService.loadProcesses`** — фильтры,
      пагинация, SQL (Pass 342).
- [ ] **`separate(parentProcess)`** — создание цепочки задач
      (parent → child1 → child2). Где используется.
- [ ] **`setWorkingToWaiting`** — recovery после рестарта (задания,
      оставшиеся в WORKING, должны перейти в WAITING).
- [ ] **`HR_REPAIR_PROCESS_TYPES`** в `HealthReport.kt` — где
      определён, какие типы.
- [ ] **Логика priority** — как `priority` влияет на выбор следующего
      задания из WAITING.
- [ ] **Cleanup `deleteDone`** — авто-удаление старых DONE-заданий
      или ручное.
- [ ] **`runFunctionWithArgs`** — какие именно функции можно вызывать
      (список в коде?).
- [ ] **Batch update percentage** — есть ли, чтобы не флудить БД.

## Код (физическая реализация)

- `karaoke-app/.../KaraokeProcess.kt` (~700 строк)
- `karaoke-app/.../KaraokeProcessStatuses.kt`
- `karaoke-app/.../KaraokeProcessTypes.kt`
- `karaoke-app/.../KaraokeProcessDTO.kt`
- `karaoke-app/.../KaraokeProcessWorker.kt` (~1422 строки)
- `karaoke-app/.../KaraokeProcessAdminService.kt`
- `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`
- `karaoke-app/.../StemJobProcessing.kt`
- `karaoke-app/.../StemJobPollScheduler.kt`
- `karaoke-app/.../StemJobCleanup.kt`
- `karaoke-app/.../PremiumAutoPublishScheduler.kt`
- `karaoke-app/.../TelegramAutoPublishScheduler.kt`
- `karaoke-app/.../TelegramAutoPublishSchedulerStarter.kt`
- `karaoke-app/.../VkAutoPublishScheduler.kt`
- `karaoke-app/.../VkAutoPublishSchedulerStarter.kt`
- `karaoke-app/.../SponsrSyncScheduler.kt`
- `karaoke-app/.../AutoOneClickSyncScheduler.kt`
- `karaoke-app/.../VkIdTokenRefreshScheduler.kt`

## Жизненный цикл JDBC-соединения потока

Потоки воркера **одноразовые**: и главный цикл `doStart`, и поток задания
(`KaraokeProcessThread`), и post-hook рендера создаются заново и умирают.
Поэтому их thread-local JDBC-соединение (`KaraokeConnection`, см. домен
[persistence](../../persistence/domain.md)) обязано закрываться детерминированно
— в `finally`. Канонический способ: `startOneShotThread(database, name) { … }`
(`karaoke-app/.../services/OneShotThread.kt`), который закрывает соединение при
любом исходе (успех/исключение).

До аудита #336 (OpenProject #368, BR-038) главный цикл `doStart` соединение не
закрывал: каждый цикл stop/start оставлял открытый физический канал к БД до
недетерминированной финализации сборщиком мусора. Поток задания
(`KaraokeProcessThread.run`) закрывал — этот паттерн и стал образцом.

## Старт воркера и видимость soft-deleted заданий (BR-034/036/037)

Зафиксировано пакетом `PKG-S3-22` (аудит #336, OpenProject #394). Три контракта,
которые легко нарушить, потому что нарушение не видно в UI.

### `loadList` по умолчанию скрывает soft-deleted строки

- `KaraokeProcess.loadList` добавляет `process_deleted_at IS NULL` в `WHERE`,
  если в `args` нет явного `include_deleted`. Раньше soft-deleted задание
  (админский `POST /api/admin/processes/{id}/delete` ставит `process_deleted_at`,
  но НЕ меняет `process_status`) оставалось видимым в `/api/processesdigests`,
  а `hasActiveProcess` навсегда видел `WAITING` — переименование файла песни
  блокировалось сообщением «выполняется фоновая обработка», sync-audio пропускал
  ребёнка. Эталон — `getProcessesToStart`, который фильтровал deleted всегда.
- `KaraokeProcessDTO.processDeletedAt` отдаёт признак наружу: клиент должен
  отличать «удалено» от «активно», когда строки запрошены с `include_deleted`.
- **Не убирайте фильтр из `loadList` ради «полной» админской выборки.** Для этого
  есть `include_deleted`; `createProcess` (single-flight по `existedProcesses`)
  опирается на тот же фильтр — иначе soft-deleted `WAITING` навсегда запрещал бы
  пересоздание задания.

### Сбой восстановления при старте — видимый, а не проглоченный

- `KaraokeProcess.deleteDone` и `setWorkingToWaiting` возвращают `Boolean`:
  `false` — сбой БД, состояние восстановления неизвестно. Исключение пишется
  в лог со стеком (SLF4J), а `KaraokeProcessWorker.start` при `false` отправляет
  SSE-уведомление об ошибке.
- Почему это важно: воркер выбирает только `WAITING`. Если `WORKING`-строки
  остались, лейн стоит, а очередь выглядит работающей (`isWork = true`) — и
  `RenderQueueStalledCheck` молчит.

### Причина `ERROR` — в `process_description`

- Отдельной колонки под причину в `tbl_processes` нет, поэтому
  `KaraokeProcess.appendErrorReason(description, cause)` дописывает хвост
  ` | ERROR: <тип>: <сообщение>` (идемпотентно, до 500 символов) и сохраняется
  обычным `save()`.
- Описание видно оператору в админке. Раньше причина уходила только в stdout
  контейнера, и задание выглядело «упало, почему — неизвестно».

### `filter_limit` — срез без падения

- `KaraokeProcess.applyFilterLimit(result, value)`: `take(limit)`, а не
  `result[i]`; нечисловое/отрицательное значение — `IllegalArgumentException`
  (на HTTP-границе `POST /api/processesdigests` отвечает 400; HTML-страница
  `/processes` просто игнорирует некорректный необязательный фильтр).
- Раньше `toInt()` на нечисле давал `NumberFormatException`, а лимит больше
  числа строк — `IndexOutOfBoundsException`; оба не ловились (`catch` только
  `SQLException`) и превращались в HTTP 500.

### Batch-эндпоинты постановки в очередь (BR-053/055/059, PKG-S3-22)

Четыре «…all»-эндпоинта (`createdemucs2all`, `createdemucs5all`, `sheetsageall`,
`createforcedalignmarkersall`) и `createkaraokeall` подчиняются одному контракту:

- **Песни грузятся пакетно** — `loadSongsBatch` (`WHERE id IN`, пачки по 25), а не
  `Song.loadFromDbById` в цикле: строка `tbl_songs` тяжёлая (`source_text`,
  `result_text`, `source_markers`). Дубликаты `ids` отсекаются `distinct()`.
- **«Успех» = хотя бы один реально поставленный процесс.** Несуществующий id не
  должен давать SSE «прошло успешно» при пустой очереди. В сообщении — сколько
  поставлено и сколько пропущено (и почему: песня не найдена / MIDI уже есть /
  статус выше порога).
- **Порог пропуска — константа.** Для «Точных маркеров» это
  `FORCED_ALIGN_MAX_ID_STATUS = 4`: условие (`idStatus < 4`) и текст сообщения
  строятся из одной константы (`ApiController.forcedAlignSummary`). Раньше в
  тексте стояло «>= 3», и оператор получал неверное объяснение пропуска.

### Фоновые операции контроллера (BR-061, PKG-S3-22)

- Тело каждого фонового потока (`startOneShotThread`) обязано быть обёрнуто в
  `runBackgroundReported(head) { … }`: ловит `Throwable` (не только `Exception`),
  пишет `ERROR` со стеком и отправляет SSE-уведомление. Иначе `Error`
  (OOM, `NoClassDefFoundError`) убивает поток молча: кнопка отработала, тоста нет,
  оператор не знает, что операция не выполнена.
- Сырые `Thread { … }` в `ApiController` запрещены (`OneShotThreadWiringTest`):
  одноразовые потоки идут через `startOneShotThread` (закрытие thread-local
  соединения). Для чтения вывода внешнего процесса используйте
  `redirectOutput(файл)` + `awaitProcessExit` — это не поток жизненного цикла.

## Связанные ADR

- [0006-processbuilder-redirect-errorstream](../../../adr/0006-processbuilder-redirect-errorstream.md) —
  `redirectErrorStream(true)` для всех `ProcessBuilder`-вызовов очереди
  (без него stderr переполняется и subprocess блокируется).
- `archive/docs/features/async-process-queue.md` — оригинальный
  документ (НЕ Knowledge). Требует миграции.
- [local-0009-thread-local-connection-lifecycle](../../../adr/local-0009-thread-local-connection-lifecycle.md) —
  контракт закрытия thread-local JDBC-соединения: одноразовый поток очереди
  закрывает соединение в `finally`, переиспользуемый — не закрывает.
- `archive/docs/features/dual-db-sync.md` — sync для `tbl_processes`.
- `archive/docs/features/approve-pipeline.md` — фича 131: пост-хук
  публикации в Telegram сразу после DONE для RENDER_MP4_DEMO.

## Источник деталей

Подробный разбор контрактов, зафиксированных пакетами аудита:

- [`specs/394-pkg-s3-22/report.md`](../../../../specs/394-pkg-s3-22/report.md) —
  контракты batch-эндпоинтов (пакетная загрузка, «успех = есть процессы», порог
  пропуска из одной константы), фоновые операции контроллера
  (`runBackgroundReported`, запрет сырых `Thread`), контракты `loadList`
  (фильтр `process_deleted_at IS NULL` + `include_deleted`), видимый сбой
  восстановления при старте воркера (`deleteDone`/`setWorkingToWaiting` →
  `Boolean`, лог со стеком, SSE), причины `ERROR` в `process_description`,
  безопасный срез `filter_limit` (`applyFilterLimit`).
- [`specs/373-pkg-s2-10/report.md`](../../../../specs/373-pkg-s2-10/report.md) —
  контракты «один писатель прогресса», атомарный claim (`claimForWorking`),
  цепочка шагов (`process_chain_id` + `process_order`, остановка при `ERROR`,
  агрегация статуса песни по завершении цепочки), валидация `process_args`.

## Changelog

- **Pass 485** (2026-09-27, spec `485-knowledge-domain-processing`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 341 P1** (2026-09-09): Initial. Автор: agent (Karaoke).


## Кортежное исполнение (674, KARAOKE-313/314/315)

Действует с 674: кортеж — единица исполнения, а не отдельный шаг.

- **Выбор.** `getProcessesToStart` выбирает один кортеж на лейн и его первый исполняемый шаг:
  голова `process_chain_id = id`, кандидат — только при наличии `WAITING`-шага; уже начатый
  кортеж (`started DESC`) не вытесняется новым по приоритету. Между кортежами `process_order`
  не сравнивается (это и был механизм дефекта 308).
- **Поток.** `TupleExecutor` исполняет шаги `0..N-1` подряд в одном потоке лейна; `runStep`
  остаётся в `KaraokeProcessWorker.kt` (exit-code guard, `redirectErrorStream(true)`).
- **scopeId.** `process_chain_id ?: id`; временные каталоги `.../tmp/<scopeId>/`, контейнеры
  `demucs-<scopeId>`, `keybpmfinder-<scopeId>`. Уборка — финальный шаг своего кортежа;
  `TupleJanitor` удаляет осиротевшие каталоги по TTL (`queue.tuple.temp_ttl_hours`).
- **Сбой.** Ненулевой код/исключение → все не-`DONE` шаги кортежа `ERROR`, причина в
  `process_description`, приоритет кортежа = -1; один CPU-retry (`DEMUCS2/5`, живой источник,
  свой scope, свой `cp`) — отдельным кортежем.
- **Содержимое.** `finalizeDemucs` сверяет длительность каждого стема с источником (±0.5 с) и
  свежесть; key/bpm применяются только при совпадении sidecar `<file> [key].json.meta`.
- **Single-flight.** Живой кортеж блокирует постановку целиком; терминальные строки живого
  кортежа не удаляются; `deleteDone` удаляет только полностью `DONE`-кортежи.
- **Восстановление.** `normalizeTuples()` при старте и resume с первого не-`DONE` шага.

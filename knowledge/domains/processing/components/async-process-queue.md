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
| **`KaraokeProcess`** | Задание в очереди. Data class с аннотациями `@KaraokeDbTableField` | `KaraokeProcess.kt` |
| **`KaraokeProcessThread`** | Java-поток, обёртка вокруг subprocess | `KaraokeProcessWorker.kt:70` |
| **`KaraokeProcessWorker`** | Главный воркер (companion object, singleton). Создаёт потоки, синхронизирует БД, цикл sync | `KaraokeProcessWorker.kt:526` |
| **`KaraokeProcessStatuses`** | `CREATING` / `WAITING` / `WORKING` / `DONE` / `ERROR` | `KaraokeProcessStatuses.kt` |
| **`KaraokeProcessTypes`** | Конкретные типы (MELT_LYRICS, DEMUCS2, UPLOAD_TO_*, ...) | `KaraokeProcessTypes.kt` |
| **`threadId`** | Lane (см. ниже) | `KaraokeProcess.kt` |
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

## Жизненный цикл задания

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

## Парсинг stdout

Известные regex'ы (см. KDoc + парсер в `KaraokeProcessThread.run()`):

- **ffmpeg**: `time=HH:MM:SS.ms` → конвертация в процент от total duration.
- **Sheetsage**: `NN%|` (прогресс-бар).
- **Demucs**: `100%|##########| ...`.
- **MLT melt**: `--progress` (если есть).

NB: каждое обновление `percentage` пишется в БД. На горячих задачах
(большие файлы) это создаёт много UPDATE'ов → нужен batch.

## Синхронизация (LOCAL ↔ SERVER)

`KaraokeProcess` участвует в **two-DB sync** через `SyncRegistry`:

- 8 флагов `sync_process_*_<push|pull>_<insert|update|delete|move>_allowed`.
- См. [two-db-sync компоненту P1] (TODO Pass 342).

Поскольку `tbl_processes` синхронизируется, статус задания в karaoke-web
**виден** через sync — webvue3 показывает прогресс (через SSE).

## Создание задания: `createProcess(...)`

```kotlin
KaraokeProcess.createProcess(
    song = song,
    action = KaraokeProcessTypes.MELT_LYRICS,
    doWait = false,
    prior = 1,
    threadId = THREAD_LANE_HEAVY_RENDER,
)
```

Возвращает `KaraokeProcess?` (null при ошибке).

`doWait = true` — блокирует вызывающий поток до DONE/ERROR (используется
в HealthReport для атомарности repair).

## Force-stop

`KaraokeProcessWorker.forceStop(processId: Long)`:

1. Найти `KaraokeProcessThread` в `runningThreads` Map по processId.
2. Взвести `thread.forceStopped = true`.
3. `thread.osProcess?.destroyForcibly()`.
4. Subprocess убит, `forceStopped = true` → статус → `WAITING`.

## Потокобезопасность

`forceStopped` и `osProcess` помечены `@Volatile` — читаются из
другого потока (force-stop).

`runningThreads` — `ConcurrentHashMap<Long, KaraokeProcessThread>`.

## Связь с другими компонентами

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

## Связанные ADR

- `archive/docs/features/async-process-queue.md` — оригинальный
  документ (НЕ Knowledge). Требует миграции.
- `archive/docs/features/dual-db-sync.md` — sync для `tbl_processes`.
- `archive/docs/features/approve-pipeline.md` — фича 131: пост-хук
  публикации в Telegram сразу после DONE для RENDER_MP4_DEMO.

## Changelog

- **Pass 341 P1** (2026-09-09): Initial. Автор: agent (Karaoke).
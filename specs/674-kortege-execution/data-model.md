# Data Model: Кортеж как единица исполнения очереди

**Feature**: `674-kortege-execution` · **Date**: 2026-10-29 · **План**: [plan.md](./plan.md)

Модель **не добавляет таблиц и колонок**. Все сущности отображаются на существующую схему
`tbl_processes` (сырой JDBC, модель `KaraokeProcess`, `KaraokeProcess.kt:50-130`) и файловую систему
`/sm-karaoke/system/**`. Причина — FR-017 и `deploy/recordhash_processes.sql` (см. research D13).

---

## 1. Сущности

### 1.1 Кортеж (Tuple) — единица планирования и исполнения

Набор строк `tbl_processes` одной песни, одного `process_type`, одного лейна, с общим
`process_chain_id = id` головы. Одиночное задание — кортеж из одной строки (FR-006).

| Поле | Колонка | Смысл в кортежной модели | Валидация |
|---|---|---|---|
| `chainId` | `process_chain_id` | id кортежа; у головы равен собственному `id`; у одиночного — равен `id` | `> 0`, обязателен после нормализации |
| `headId` | `id` головы | строка с `process_chain_id = id`; единственная точка выбора планировщиком | ровно одна на кортеж |
| `priority` | `process_priority` | приоритет кортежа; одинаков у всех шагов | все строки кортежа равны |
| `threadId` | `thread_id` | лейн; одинаков у всех шагов | все строки кортежа равны |
| `state` | вычисляется из `process_status` строк | `WAITING` (есть `WAITING`, нет живых), `WORKING` (есть `WORKING`), `ERROR` (все терминальные, есть `ERROR`), `DONE` (все `DONE`) | — |
| `stepIndex` | `process_order` текущего шага | 0-based индекс шага; «k из N» в UI = `stepIndex + 1` | `0 ≤ stepIndex < stepsTotal` |
| `stepsTotal` | `COUNT(*)` по `chain_id` | число шагов кортежа N | `≥ 1` |
| `failedStep` | `process_order` строки в `ERROR` | шаг, на котором упал кортеж | — |
| `reason` | `process_description` | причина сбоя (хвост ` | ERROR: …`, `KaraokeProcess.ERROR_REASON_MARKER`) | непустой у `ERROR` |
| `songId` | `song_id` | песня — владелец артефактов | `> 0` для песенных кортежей |
| `type` | `process_type` | тип кортежа (`DEMUCS2/5`, `KEY_BPM_FROM_FILE`, `MELT_*`, …) | значение `KaraokeProcessTypes` |
| `deletedAt` | `process_deleted_at` | soft-delete шага; живые шаги кортежа не удаляются | — |

**Производные величины** (не хранятся): `scopeId = chainId` (кортеж), `isLive` (есть
`WAITING/WORKING/CREATING`), `isComplete` (все `DONE`), `isFailed` (все терминальные, есть `ERROR`),
`progress` (прогресс текущего шага, `process_percentage*`).

### 1.2 Шаг кортежа (Step)

| Поле | Колонка | Смысл |
|---|---|---|
| `id` | `id` | идентификатор шага |
| `chainId` | `process_chain_id` | владелец |
| `order` | `process_order` | 0..N-1; голова = 0 |
| `command` | `process_command` | `""` у головы, `"tail"` у остальных (сохраняется как признак «не первая нога») |
| `status` | `process_status` | `WAITING / WORKING / DONE / ERROR / CREATING` |
| `argv` | `process_args` | JSON `List<List<String>>`; содержит токен `{scope}` |
| `envs` | `process_envs` | JSON `Map<String,String>` |
| `description` | `process_description` | описание шага + причина ошибки |
| `start`/`end` | `process_start`/`process_end` | таймстампы |
| `percentage*` | `process_percentage*`, `process_time_*` | прогресс (пишет `saveProgress`, `KaraokeProcess.kt:424-475`) |
| `priority` | `process_priority` | приоритет кортежа |
| `threadId` | `thread_id` | лейн |
| `withoutControl` | `without_control` | отключает агрегацию статуса песни и остановку соседей (сохраняется) |

**Жизненный цикл шага:**

```
CREATING ──(нормализация/старт)──► WAITING
WAITING ──claimForWorking()──► WORKING ──exit 0 + проверка содержимого──► DONE
                                     └── exit ≠ 0 / исключение / провал проверки ──► ERROR
WORKING ──forceStop / рестарт──► WAITING   (process_end и прогресс сбрасываются)
ERROR ──admin retry (tuple-aware)──► WAITING  (вместе со всем кортежем)
```

`DONE`/`ERROR` — терминальные для шага; кортеж терминален, когда терминальны все его шаги.

### 1.3 Лейн (Lane)

| Поле | Источник | Смысл |
|---|---|---|
| `threadId` | `thread_id` | номер лейна: `0` тяжёлые, `1` HR/импорт, `2` премиум-стемы, `-1`, `-2` (`KaraokeProcess.kt:660-664`) |
| `liveTuple` | `threadsMap` в `KaraokeProcessWorker` | не более одного живого потока на лейн (`:640`, `:1154-1200`) |

Инвариант: в лейне одновременно исполняется не более одного кортежа; шаги кортежа не перемежаются
с шагами других кортежей (FR-001/FR-002).

### 1.4 Временный ресурс кортежа (Scope resource)

| Поле | Значение | Владелец |
|---|---|---|
| `scopeId` | `process_chain_id ?: id` | кортеж |
| корень demucs | `/sm-karaoke/system/demucs-docker/tmp` (`Constants.kt:14`) | приложение |
| каталог demucs | `<корень>/<scopeId>/` | кортеж |
| корень keybpm | `/sm-karaoke/system/keybpmfinder-docker/tmp` (`:21`) | приложение |
| каталог keybpm | `<корень>/<scopeId>/` | кортеж |
| корень sheetsage | новый `PATH_TO_TEMP_SHEETSAGE_FOLDER` (вместо фиксированных `source.wav`/`output/output`) | приложение |
| каталог sheetsage | `<корень>/<scopeId>/` | кортеж |
| контейнер demucs | `demucs-<scopeId>` | кортеж |
| контейнер keybpm | `keybpmfinder-<scopeId>` | кортеж |
| stemjob | `.../stemjob-docker/tmp/{jobId}`, `stemjob-<jobId>` | задание (уже изолировано) |

Правила: читать/писать/удалять может только владелец; финальный шаг удаляет только свой каталог;
`TupleJanitor` удаляет каталоги без живых строк кортежа и старше TTL (`queue.tuple.temp_ttl_hours`,
default 24).

### 1.5 Артефакт песни (Artifact)

| Поле | Значение |
|---|---|
| вид | flac-стем (`accompaniment`, `vocals`, `drums`, `bass`, `other`), mp3, `[key].json` |
| путь | каталог песни (`<rootFolder>/hdemucs_mmi/…`, `song.pathToFileKeyBpmFinder`) |
| проверка | стем: `duration ≈ duration(source) ± 0.5 с`; key/bpm: sidecar-отпечаток источника |
| sidecar | `<file> [key].json.meta` — `{sourceSize, sourceDurationMs, sourceHash, scopeId, computedAt}` |
| «чужой» | длительность не совпадает / отпечаток не совпадает / sidecar отсутствует |
| «старый» | `mtime(artifact) < mtime(source)` → перегенерировать, но не удалять до успешной замены |

### 1.6 Находка проверки целостности (IntegrityFinding)

| Поле | Значение |
|---|---|
| `songId` | песня |
| `kind` | `missingStem`, `foreignDuration`, `staleArtifact`, `missingSidecar`, `sidecarMismatch`, `emptyKeyBpm` |
| `artifact` | путь артефакта |
| `expected`/`actual` | длительность/отпечаток |
| `detectedAt` | таймстамп |
| `reportPath` | `PATH_TO_LOGS/artifact-integrity/<yyyy-MM-dd>.json` |

Находка **не** влечёт правок; ремонт — отдельный явный вызов (`SongArtifactRepairService`).

---

## 2. Состояния кортежа

```
                 (создан)                     (все шаги DONE)
                    │                               │
                    ▼                               ▼
   WAITING ──claim первого WAITING──► WORKING ──► DONE (кортеж)
      ▲                                  │
      │                                  ├─ ненулевой код / исключение
      │                                  ▼
      └────admin retry (весь кортеж)── ERROR (кортеж): все не-DONE шаги → ERROR,
                                             причина в process_description,
                                             приоритет кортежа = -1
```

Переходы:

| Из | Событие | В | Побочные эффекты |
|---|---|---|---|
| `WAITING` | планировщик выбрал голову | работа потока | поток занял лейн |
| `WAITING` шага | `claimForWorking()` | `WORKING` | `process_start = now` |
| `WORKING` | exit 0 + проверка содержимого | `DONE` шага | `process_end`; если больше нет `WAITING` — `DONE` кортежа, `priority = 999` |
| `WORKING` | exit ≠ 0 / исключение / провал проверки | `ERROR` шага | причина; все не-терминальные шаги `ERROR`; `priority = -1`; кортеж не выбирается |
| `WORKING` | `forceStop()` | `WAITING` шага | `process_end`/прогресс сбрасываются; остальные шаги уже `WAITING` |
| `WORKING` | рестарт JVM | `WAITING` шага | `setWorkingToWaiting` + нормализация |
| `CREATING` | рестарт/нормализация | `WAITING` | мёртвый статус не остаётся |
| `ERROR` | admin retry | `WAITING` всех `ERROR`-шагов кортежа | приоритет кортежа восстановлен |
| `DONE` кортеж | purge при старте/новой постановке | удаление | удаляется только полностью `DONE`-кортеж |

---

## 3. Инварианты

- **I-1.** У кортежа ровно одна голова: `COUNT(*) WHERE process_chain_id = id` = 1 (после нормализации).
- **I-2.** Все строки кортежа имеют один `thread_id` и один `process_priority`.
- **I-3.** `process_order` уникален в кортеже и образует `0..N-1`.
- **I-4.** Планировщик выбирает только головы; строка с `process_chain_id ≠ id` никогда не выбирается.
- **I-5.** Шаг исполняется только если все шаги с меньшим `order` — `DONE` (следствие I-4 + цикл).
- **I-6.** `scopeId` уникален для живого кортежа; каталоги/контейнеры разных кортежей не пересекаются.
- **I-7.** Ни один шаг не удаляет корень временного каталога и не трогает чужой `scopeId`-каталог.
- **I-8.** Ненулевой код возврата любого шага ⇒ кортеж в `ERROR`, причина непуста, оставшиеся шаги
  не исполняются.
- **I-9.** `DONE` ставится только после проверки содержимого (длительность/ sidecar), а не по факту
  существования файла.
- **I-10.** key/bpm записываются в `tbl_songs` только при совпадении sidecar-отпечатка источника.
- **I-11.** Терминальные строки живого кортежа не удаляются ни `createProcess`, ни `deleteDone`.
- **I-12.** После рестарта/остановки кортеж продолжается с первого не-`DONE` шага; строки не остаются
  в `WORKING`/`CREATING` без живого обработчика.
- **I-13.** Поток кортежа одноразовый: thread-local соединение `KaraokeConnection` закрывается
  **один раз** в `finally` после всего цикла шагов, не на каждом шаге (ADR `local-0009`,
  `KaraokeProcessWorker.kt:511-513`); каждый кортеж — новый поток.
- **I-14.** `withoutControl` (у `SYMLINK`) не влияет на остановку кортежа, порядок шагов и resume;
  он подавляет только per-step агрегат статуса песни и «шумный» прогресс. `markDone`/`markError`
  и `process_status` пишутся независимо от флага.

---

## 4. Программная модель (новый пакет `tuple/`)

```text
TupleScope
  fun scopeId(chainId: Long?, stepId: Long): Long
  fun expand(argv: List<String>, scopeId: Long): List<String>

TupleStore (интерфейс; JDBC-реализация + InMemoryTupleStore для тестов)
  fun firstRunnable(chainId: Long): KaraokeProcess?        // MIN(order) WAITING, not deleted
  fun claim(step: KaraokeProcess): Boolean                 // WAITING → WORKING
  fun nextWaiting(chainId: Long): KaraokeProcess?          // следующий шаг или null
  fun markDone(step: KaraokeProcess)
  fun markError(step: KaraokeProcess, reason: String)
  fun markWaiting(step: KaraokeProcess)                    // force-stop
  fun completeTuple(chainId: Long)                         // priority=999, агрегат песни
  fun failTuple(chainId: Long, reason: String)             // все не-DONE → ERROR, priority=-1
  fun aggregateState(chainId: Long): TupleState            // для админки/логов

TupleExecutor(store, stepRunner)
  fun execute(chainId: Long, stepsTotal: Int): TupleOutcome

TupleJanitor(store, clock, ttl, roots)
  fun sweep(): JanitorReport

ArtifactIntegrity(probe)
  fun verifySong(songId: Long): List<IntegrityFinding>
```

`TupleOutcome` = `DONE` | `FAILED(reason)` | `STOPPED` (force-stop) — используется пост-хуками и SSE.

`KaraokeProcessThread` остаётся наследником `Thread` (force-stop нужны `forceStopped`/`osProcess`),
но оборачивает **весь** цикл кортежа в `try/finally`, где `database.closeThreadConnection()`
вызывается один раз (ADR `local-0009`); цикл шагов не создаёт новых потоков и не переиспользует
поток между кортежами.

---

## 5. Что НЕ меняется (и почему)

| Что | Статус | Почему |
|---|---|---|
| DDL `tbl_processes` | без изменений | FR-017; `recordhash_processes.sql` (md5 + backfill) не трогается |
| Миграции `deploy/karaoke-db/` | не нужны | нет новых колонок/таблиц |
| `KaraokeProcessTypes` | без новых значений | кортежность — свойство строк, а не новый тип |
| `SyncRegistry` | без изменений | `tbl_processes` не синхронизируется; поля песни идут через существующий `SongSyncTarget` |
| `process_status` | те же 5 значений | потребители (админка, мониторинг, внешние сервисы) не ломаются |
| `redirectErrorStream(true)` | сохраняется | Принцип IV, ADR-0006, R-43 |

Если в будущем понадобится хранить состояние кортежа отдельно (например, `tuple_state`,
`tuple_attempt`), путь изменения зафиксирован: колонка в модель `KaraokeProcess` →
`deploy/karaoke-db/NN_*.sql` → `deploy/recordhash_processes.sql` (md5 + backfill) →
`tools/check-recordhash-consistency.py` → миграция на обеих БД (LOCAL и PROD). В этой спеке такой
необходимости нет.

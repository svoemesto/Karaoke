# Tasks: Кортеж как единица исполнения очереди обработки

**Input**: Design documents from `specs/674-kortege-execution/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required),
[research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/),
[quickstart.md](./quickstart.md)

**Tests**: REQUIRED. План (§P7) и приёмка KARAOKE-316 требуют unit на планировщик/claim/
single-flight/resume/janitor/проверку содержимого, integration на изоляцию (порт
`.scratch/wf308/exp/queue.py`) и e2e на копиях 8 песен. Порядок — TDD: тест пишется и падает
до реализации.

**Organization**: задачи сгруппированы по user stories из `spec.md`, чтобы каждую историю можно
было реализовать и проверить независимо.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: `US1`…`US4` — только в фазах user stories
- В каждой задаче — точный путь к файлу

## Constraints (не нарушать)

- `GRADLE_USER_HOME` и `DOCKER_CONFIG` — от корня репозитория (AGENTS.md Pass 372/373).
- Контейнеры — только `deploy/do.sh`; `docker restart` запрещён; `karaoke-app` не перезапускать
  без согласия владельца и без остановки очереди (Pass 380).
- `ProcessBuilder.redirectErrorStream(true)` обязателен (R-43, ADR-0006).
- Без JPA/Hibernate — только сырой JDBC (R-07).
- Фронтенд — `cd webvue3 && npm run …` (R-375). Каталог именно `webvue3`, не `webview3`.
- Прод не трогать: никаких постановок в очередь, DDL/DML к прод-БД, перезапусков; e2e — на копиях.
- Схема `tbl_processes` не меняется: `deploy/recordhash_processes.sql` и миграции не трогаются.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: каркас нового пакета и настройки, без которых не начинаются истории.

- [ ] T001 Создать пакет `tuple/` с KDoc и `@see specs/674-kortege-execution/spec.md`:
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleScope.kt`,
  `tuple/TupleStore.kt`, `tuple/TupleExecutor.kt`, `tuple/TupleSchedulerSql.kt`,
  `tuple/TupleJanitor.kt`, `tuple/ArtifactIntegrity.kt`
- [ ] T002 [P] Добавить настройки в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`:
  `queue.tuple.temp_ttl_hours`=24, `queue.tuple.janitor_interval_minutes`=60,
  `queue.integrity.enabled`=false, `queue.integrity.cron`=`0 0 4 * * *` (публичные дефолты, без секретов)
- [ ] T003 [P] Зафиксировать базовую сборку `karaoke-app/build.gradle.kts`:
  `GRADLE_USER_HOME=... ./gradlew :karaoke-app:compileKotlin :karaoke-app:ktlintCheck`
  проходит до изменений (baseline для ktlint)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: SQL выбора кортежа, интерфейс состояния, нормализация и единое создание цепочек.
Без этого не работает ни одна история.

**⚠️ CRITICAL**: истории не начинать, пока фаза не завершена.

- [ ] T004 [P] Вынести SQL выбора головы кортежа в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleSchedulerSql.kt`
  (`SELECT_TUPLE_HEADS`, `FIRST_RUNNABLE_STEP`, `NEXT_WAITING_STEP`) по контракту
  `contracts/tuple-scheduler.md` §2: `process_chain_id = id`, `EXISTS WAITING`, `(priority, id)`,
  один кортеж на лейн
- [ ] T005 [P] Определить `TupleStore` (интерфейс) и `JdbcTupleStore` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleStore.kt`:
  `firstRunnable`, `claim`, `nextWaiting`, `markDone`, `markError`, `markWaiting`,
  `completeTuple`, `failTuple` (тонкая обёртка над `claimForWorking`/`save`)
- [ ] T006 Заменить `getProcessesToStart` на выбор голов в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` (SQL из T004,
  KDoc по контракту), добавить `firstRunnableStep(chainId)`
- [ ] T007 Реализовать `normalizeTuples()` и `purgeFinishedTuples()` в `KaraokeProcess.kt`:
  `chainless → chain_id=id/order 0`; безголовая цепочка → голова = min `order`;
  `CREATING → WAITING`; `WORKING → WAITING` со сбросом `end`/`percentage*`;
  удаление только полностью `DONE`-кортежей. Вызвать в `KaraokeProcessWorker.start()`
  (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:712-713`)
- [ ] T008 Единое создание цепочек: `markAsHead()` и `createChain(...)` в `KaraokeProcess.kt`;
  перевести `createProcess` (одиночный путь), `Utils.kt` (CPU-retry),
  `StemJobPollScheduler.kt`, `StemJobProcessing.kt` на `createChain`
- [ ] T009 [P] Тестовая инфраструктура: `InMemoryTupleStore` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/InMemoryTupleStore.kt`
- [ ] T010 [P] `TupleSchedulerSqlTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleSchedulerSqlTest.kt`:
  SQL выбирает только голову, `EXISTS WAITING`, без `process_order` между кортежами,
  `firstRunnableStep` = `MIN(order)` среди `WAITING` и не отдаёт шаг при не-`DONE` предыдущем

**Checkpoint**: планировщик выбирает кортежи, нормализация и создание цепочек готовы — истории
можно начинать.

---

## Phase 3: User Story 1 — Пачка песен обрабатывается целиком и без подмены (Priority: P1) 🎯 MVP

**Goal**: кортеж исполняется как единица, временные ресурсы изолированы по `scopeId`, лейн занят
кортежем до конца; ни одна песня не остаётся без стемов и не получает чужие.

**Independent Test**: `TupleIsolationIT` — два кортежа `DEMUCS2` в одном лейне на копиях: у каждого
свои стемы, длины совпадают со своим источником, нет `cannot stat`/`already in use`.

### Tests for User Story 1 (TDD — сначала падающие тесты)

- [ ] T011 [P] [US1] `TupleScopeTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleScopeTest.kt`:
  `scopeId = chainId ?: id`, подстановка `{scope}`, уникальные каталоги/контейнеры двух кортежей,
  финальный шаг чистит только свой каталог
- [ ] T012 [P] [US1] `TupleThreadLifecycleTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleThreadLifecycleTest.kt`:
  `FakeKaraokeConnection` — `closeThreadConnection()` ровно один раз за кортеж (не на шаг),
  после последнего шага; второй кортеж закрывает своё соединение; `finally` вокруг цикла
  (ADR `local-0009`)
- [ ] T013 [P] [US1] `TupleIsolationIT` в
  `karaoke-app/src/manualTest/kotlin/com/svoemesto/karaokeapp/tuple/TupleIsolationIT.kt`:
  порт `.scratch/wf308/exp/queue.py` на Kotlin (`InMemoryTupleStore` + `TupleExecutor`,
  stub-`docker`, реальные argv `Song.argsDemucs2`), два кортежа в одном лейне

### Implementation for User Story 1

- [ ] T014 [US1] `TupleScope` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleScope.kt`: `SCOPE_TOKEN="{scope}"`,
  `scopeId(chainId, stepId)`, `expand(argv, scopeId)`, `scopeDir(root, scopeId)`
- [ ] T015 [P] [US1] `model/Song.kt`: `argsDemucs2`/`argsDemucs5`/`argsDemucs2RetryCpu`/
  `argsDemucs5RetryCpu` — пути `.../tmp/{scope}/file*.flac`, контейнер `demucs-{scope}`,
  `scopeId={scope}` в финальном шаге
- [ ] T016 [P] [US1] `model/Song.kt`: `argsKeyBpmFinder` — каталог `.../tmp/{scope}/`,
  контейнер `keybpmfinder-{scope}`, `rm -rf` только своего каталога, `scopeId={scope}`
- [ ] T017 [US1] SHEETSAGE-спайк + изоляция: новый `PATH_TO_TEMP_SHEETSAGE_FOLDER` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Constants.kt`, правки
  `KaraokeProcess.kt` (SHEETSAGE/SHEETSAGE2: `.../{scope}/source.wav`, `-o` в свой каталог,
  `rm -f` своих файлов); при cwd-относительной записи — `ProcessBuilder.directory(scopeDir)`.
  Критерий — `SheetsageScopeSpikeIT` (T037)
- [ ] T018 [US1] `TupleExecutor` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleExecutor.kt`: цикл
  `claim → runStep → markDone → nextWaiting`, `null` → `completeTuple`, ошибка → `markError`+
  `failTuple`, `forceStopped` → `markWaiting`; `stepsTotal`/`stepIndex` для прогресса
- [ ] T019 [US1] `KaraokeProcessWorker.kt`: `KaraokeProcessThread` — поток кортежа
  (`chainId`, `var karaokeProcess`, `stepIndex`/`stepsTotal`), цикл из T018, обновление
  `threadsMap` на каждом шаге, `finally { database.closeThreadConnection() }` вокруг всего цикла
- [ ] T020 [US1] `KaraokeProcess.kt`: `getCountWaiting` — `COUNT(DISTINCT process_chain_id)` по
  `WAITING`; `filter_notail` в `loadList` — на уровне кортежей; синхронизировать
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/LaneStalledCheck.kt`
- [ ] T021 [US1] `TupleJanitor` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleJanitor.kt` + вызов при старте
  и по `queue.tuple.janitor_interval_minutes` в `KaraokeProcessWorker.kt`; TTL
  `queue.tuple.temp_ttl_hours`, живые кортежи и корни не трогать
- [ ] T022 [US1] `Utils.kt`: `killRunningDockerContainers` — фильтры `name=^demucs-`,
  `name=^keybpmfinder-` (+ переходно точные старые имена)
- [ ] T023 [US1] Прогнать `TupleIsolationIT` и `TupleThreadLifecycleTest`; зафиксировать базовое
  время обработки пачки (SC-006) в прогоне по `specs/674-kortege-execution/quickstart.md` §2–3

**Checkpoint**: US1 работает независимо — кортеж исполняется целиком, изоляция подтверждена.

---

## Phase 4: User Story 2 — Сбой виден, а не замаскирован (Priority: P1)

**Goal**: любой ненулевой код шага → весь кортеж `ERROR` с причиной; успех подтверждается
содержимым (длительность стема, key/bpm из своего файла); один CPU-retry.

**Independent Test**: `TupleFailureTest` + `ArtifactVerificationTest` — сбой посередине кортежа
останавливает весь кортеж, причина видна; чужой/старый артефакт не считается готовым.

### Tests for User Story 2

- [ ] T024 [P] [US2] `TupleFailureTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleFailureTest.kt`: ненулевой код →
  весь кортеж `ERROR`, причина в `process_description`, следующий шаг не стартует, второй retry
  не ставится (`retriedOnCpu=true`)
- [ ] T025 [P] [US2] `ArtifactVerificationTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/ArtifactVerificationTest.kt`:
  ±0.5 с; отсутствие/чужой sidecar → key/bpm не применяются; `bpm=0`/пустой `key` → ошибка
- [ ] T026 [P] [US2] `TupleWithoutControlTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleWithoutControlTest.kt`:
  `withoutControl=true` не отменяет fail-fast, порядок и resume

### Implementation for User Story 2

- [ ] T027 [US2] `KaraokeProcess.kt`: `failTuple` — все не-`DONE` шаги → `ERROR` с причиной,
  `process_end`, `priority=-1` всему кортежу; расширить `stopChainSiblingsOnError` фильтром
  `process_deleted_at`; `hasPendingChainSiblings` — тот же фильтр
- [ ] T028 [US2] `Utils.kt`: `AudioProbe.durationMs(path)` (всегда `ffprobe`, таймаут, `null` при
  ошибке) — новый файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/AudioProbe.kt`
- [ ] T029 [US2] `Utils.kt`: `executeFinalizeDemucs` — проверки существования/длины/длительности/
  свежести; при провале — один CPU-retry отдельным кортежем (T008) с собственным `cp` источника,
  исчерпание — `*.rejected-<timestamp>` + удаление своего scope-каталога, `false`
- [ ] T030 [US2] `Utils.kt`: `executeGetKeyBpmFromFile` — `scopeId` из параметров, проверка
  sidecar-отпечатка, `key`/`bpm` пустые → `false`, запись через `saveToDbLocked()` только после
  проверки; `model/Song.kt`: `getKeyBpmFromFile` без перезапуска `argsKeyBpmFinder`,
  `applyKeyBpmFromFileIfExists` — проверка sidecar
- [ ] T031 [P] [US2] `KaraokeProcess.kt`: `FF_MP3_*` — `rm` → `rm -f`
- [ ] T032 [US2] `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/TupleExecutor.kt` и
  `KaraokeProcess.kt`: `failTuple`/`completeTuple` независимы от `withoutControl`; `save()`-гейт
  не влияет на `markDone`/`markError`; обновить KDoc
- [ ] T033 [US2] `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`:
  пост-хуки (`HealthReport.onRepairProcessFinished`, Telegram, `sendCountWaiting`) — один раз на
  кортеж; причина сбоя в `process_description` и SSE

**Checkpoint**: US1 и US2 работают независимо; «тихих успехов» нет.

---

## Phase 5: User Story 3 — Повторный запуск и ремонт не портят готовое (Priority: P2)

**Goal**: повтор не создаёт дублей и не удаляет живое; ремонт перегенерирует по содержимому;
рестарт продолжает с первого не-`DONE`; ежедневная проверка только сообщает.

**Independent Test**: `SingleFlightTest`, `TupleRecoveryTest` + e2e повторного прогона и рестарта.

### Tests for User Story 3

- [ ] T034 [P] [US3] `SingleFlightTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/SingleFlightTest.kt`: живой кортеж →
  `SKIP`; `DONE`-строка живого кортежа не удаляется; завершённый кортеж → `CREATE`
- [ ] T035 [P] [US3] `TupleRecoveryTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleRecoveryTest.kt`: resume с первого
  не-`DONE`; `CREATING→WAITING`; безголовая цепочка получает голову; soft-deleted сосед не
  подвешивает кортеж
- [ ] T036 [P] [US3] `ArtifactIntegrityTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/ArtifactIntegrityTest.kt`:
  все виды находок (`missingStem`, `foreignDuration`, `staleArtifact`, `missingSidecar`,
  `sidecarMismatch`, `emptyKeyBpm`), правок нет
- [ ] T037 [P] [US3] `SheetsageScopeSpikeIT` в
  `karaoke-app/src/manualTest/kotlin/com/svoemesto/karaokeapp/tuple/SheetsageScopeSpikeIT.kt`
  (критерий спайка из T017)

### Implementation for User Story 3

- [ ] T038 [US3] `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`:
  `createProcess` single-flight по кортежу: живой кортеж → `return 0` без удалений; удаление только
  полностью `DONE`-кортежей; `KEY_BPM` skip только при подтверждённом sidecar
- [ ] T039 [US3] `Utils.kt`/`KaraokeProcess.kt`: CPU-retry — отдельный кортеж через `createChain`,
  свой `scopeId`, свой `cp` источника, `retriedOnCpu=true`, ровно один
- [ ] T040 [US3] Recovery-края в `KaraokeProcess.kt`: `setWorkingToWaitingForThread` со сбросом
  `end`/прогресса; обработка soft-deleted кортежа (все шаги удалены → лог, лейн не блокируется)
- [ ] T041 [US3] `ArtifactIntegrity.verifySong` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/ArtifactIntegrity.kt`
  (read-only, контракт `contracts/artifact-verification.md` §6)
- [ ] T042 [US3] `SongArtifactRepairService` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongArtifactRepairService.kt`:
  `dryRun=true` по умолчанию, постановка `DEMUCS2/5` + `KEY_BPM_FROM_FILE` в лейн 1, приоритет 0
- [ ] T043 [US3] `ArtifactAdminController` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ArtifactAdminController.kt`:
  `POST /api/admin/artifacts/repair`, `GET /api/admin/artifacts/integrity` (контракт
  `contracts/admin-api.md` §4–5)
- [ ] T044 [US3] `ArtifactIntegrityScheduler` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/ArtifactIntegrityScheduler.kt`:
  `@Scheduled` по `queue.integrity.cron`, отчёт в `PATH_TO_LOGS/artifact-integrity/<date>.json`,
  последний отчёт в памяти, SSE-уведомление, **без правок**
- [ ] T045 [US3] Разовый ремонт 8 песен 30390–30397 на стенде по
  `specs/674-kortege-execution/quickstart.md` §3.5: `dryRun=true` → подтверждение находок →
  `dryRun=false`; после — отчёт целостности `findingsTotal=0` (SC-005)
- [ ] T046 [US3] e2e повторного прогона (SC-004) по `specs/674-kortege-execution/quickstart.md` §3.3:
  0 изменений готовых артефактов, 0 новых кортежей

**Checkpoint**: US1–US3 работают; ремонт и повтор не портят готовое.

---

## Phase 6: User Story 4 — Оператор видит, где идёт кортеж (Priority: P3)

**Goal**: в админке видно песню, шаг k из N, состояние кортежа и причину сбоя; retry — по кортежу.

**Independent Test**: открыть админку во время выполнения кортежа; после сбоя — причина; retry
возвращает весь кортеж.

### Tests for User Story 4

- [ ] T047 [P] [US4] `AdminDoneFilterTest` в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/AdminDoneFilterTest.kt`:
  `status=DONE` и top-level выдача после `purgeFinishedTuples`; `DONE`-голова живого кортежа видна;
  завершённый удалён как при `deleteDone`; `tbl_processes_audit` не задет
- [ ] T048 [P] [US4] Source-contract тест админских маршрутов/фильтра в
  `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/AdminTupleContractTest.kt`:
  `topLevelOnly` содержит `process_chain_id = id`, DTO содержит `chainState`/`chainStepIndex`

### Implementation for User Story 4

- [ ] T049 [US4] `services/KaraokeProcessAdminService.kt`: `topLevelOnly` →
  `(process_chain_id IS NULL OR process_chain_id = id)`; агрегат кортежа одним запросом
  (`LEFT JOIN`), фильтр `chainState`
- [ ] T050 [US4] `dto/admin/KaraokeProcessAdminDTO.kt`: `chainId`, `chainStepIndex`,
  `chainStepsTotal`, `chainState`, `chainFailedStep`, `chainReason`
- [ ] T051 [US4] `services/KaraokeProcessAdminService.kt`: `retryProcess` — tuple-aware (все
  `ERROR`-шаги кортежа → `WAITING`, приоритет кортежа, одна audit-запись)
- [ ] T052 [P] [US4] `webvue3/src/components/Processes/ProcessesTable.vue` и
  `webvue3/src/components/Processes/store.js`: колонка «Шаг k из N», состояние кортежа, причина,
  фильтр `chainState`
- [ ] T053 [US4] Ручная проверка админки (SC-007) на
  `webvue3/src/components/Processes/ProcessesTable.vue`: причина сбоя без чтения логов,
  шаг/прогресс, tuple-retry; `cd webvue3 && npm run lint && npm run build`

**Checkpoint**: все 4 истории работают независимо.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T054 [P] `docs/features/kortege-execution.md` — per-feature документ (FR-009) + `@see` из KDoc
- [ ] T055 [P] `knowledge/domains/processing/components/async-process-queue.md` — переписать факты
  (кортеж, голова/шаги, scope, janitor, recovery, single-flight, приоритет) и 17 расхождений из
  `03-semantics.md` §10
- [ ] T056 [P] `knowledge/domains/processing/domain.md` — термин «кортеж», инварианты, таблица лейнов
- [ ] T057 [P] `knowledge/domains/processing/components/key-bpm-from-file.md` и
  `knowledge/domains/processing/components/process-admin.md` — sidecar, запрет тихого успеха,
  поля кортежа в админке, tuple-retry
- [ ] T058 [P] `knowledge/domains/catalog/components/dictionaries.md` — SSoT-gate после правки
  `model/Song.kt` (`.ssot-map.yml:41-43`)
- [ ] T059 [P] `docs/architecture-notes.md` — запись о PR
- [ ] T060 Полная проверка из AGENTS.md (`karaoke-app/build.gradle.kts`, `webvue3/package.json`):
  `GRADLE_USER_HOME=... ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`,
  `:karaoke-app:ktlintCheck`, `:karaoke-web:bootJar`,
  `cd webvue3 && npm run lint && npm run build && npm run format:check`
- [ ] T061 Прогнать `quickstart.md` §3 целиком на копиях 8 песен: SC-001 (по артефактам), SC-002,
  SC-003, SC-005, SC-007, SC-008; записать результат
- [ ] T062 Замер SC-006 на копиях 8 песен до/после, допуск +20% (сценарий
  `specs/674-kortege-execution/quickstart.md` §3); записать в отчёт
- [ ] T063 Финальная гигиена репозитория (`specs/674-kortege-execution/`, `.gitignore`): `git status`,
  отсутствие секретов и временных файлов; `report.md` не требуется до merge (появится после PR)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей, стартует сразу.
- **Foundational (Phase 2)**: после Setup; **блокирует все истории**.
- **User Stories (Phase 3–6)**: после Foundational; внутри — тесты → реализация.
- **Polish (Phase 7)**: после нужных историй (минимум US1–US3 для приёмки).

### User Story Dependencies

- **US1 (P1)**: после Foundational; независима. Даёт MVP.
- **US2 (P1)**: после Foundational; использует `TupleExecutor`/`TupleStore` из US1 (T018),
  но проверяется своими тестами до интеграции с US1-путями.
- **US3 (P2)**: после Foundational; зависит от fail-fast (US2) для retry и от `scopeId` (US1)
  для уборки; тесты `SingleFlightTest`/`TupleRecoveryTest` независимы.
- **US4 (P3)**: после Foundational и US1–US2 (нужны реальные `chainState`/причины); UI-правки
  независимы от остальных.

### Within Each User Story

- Тесты (T011–T013, T024–T026, T034–T037, T047–T048) — до реализации, должны падать.
- Модель/интерфейсы → сервис → интеграция.
- Каждая история заканчивается своим checkpoint-прогоном.

### Parallel Opportunities

- Setup: T002, T003 — параллельно.
- Foundational: T004, T005, T009, T010 — параллельно (разные файлы); T006–T008 — последовательно
  (правят `KaraokeProcess.kt`).
- US1: T011–T013 — параллельно; T015/T016 — параллельно; T014 → T018 → T019 последовательно.
- US2: T024–T026 — параллельно; T028/T031 — параллельно; T027 → T029 → T030 → T032 → T033.
- US3: T034–T037 — параллельно; T038–T044 — по зависимостям (T042/T043 зависят от T041).
- US4: T047/T048 — параллельно; T049–T051 последовательно; T052 параллельно с бэкендом.
- Polish: T054–T059 — параллельно.

---

## Parallel Example: User Story 1

```text
# Тесты (падают до реализации):
Task: "TupleScopeTest в karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleScopeTest.kt"
Task: "TupleThreadLifecycleTest в karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/TupleThreadLifecycleTest.kt"
Task: "TupleIsolationIT в karaoke-app/src/manualTest/kotlin/com/svoemesto/karaokeapp/tuple/TupleIsolationIT.kt"

# Независимые правки построителей:
Task: "argsDemucs2/5/RetryCpu со scope в karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt"
Task: "argsKeyBpmFinder со scope в karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (T001–T003) → Phase 2 (T004–T010).
2. Phase 3 (T011–T023): кортеж исполняется целиком, изоляция подтверждена.
3. **STOP and VALIDATE**: `TupleIsolationIT` + админский список (без UI-правок) на стенде.
4. MVP готов, если пачка 8 песен даёт свои стемы без чужих.

### Incremental Delivery

1. US1 → проверка изоляции → приёмка MVP.
2. US2 → проверка сбоя и содержимого (SC-002/003/007).
3. US3 → повтор, ремонт, рестарт (SC-004/005/008) + ежедневная проверка.
4. US4 → админка (SC-007), tuple-retry.
5. Polish → SSoT, полная сборка, e2e (SC-001/006).

### Constraints

- Любая выкатка/перезапуск — только по слову владельца и после остановки очереди (Pass 380).
- E2E — на копиях; прод-очередь, прод-БД и `karaoke-app` не трогаются.
- Схема не меняется; миграции и `recordhash_processes.sql` не затрагиваются.

---

## Notes

- `[P]` = разные файлы, нет зависимости от незавершённых задач.
- `[Story]` связывает задачу с историей из `spec.md`.
- Тесты обязательны (план §P7, KARAOKE-316): unit / integration на копиях / e2e на копиях.
- Проверка содержимого — `ffprobe` длительности ±0.5 с и sidecar key/bpm; «файл есть» не считается
  успехом.
- Приёмка SC-001 — по артефактам и отчёту, `ERROR` исходного кортежа при успешном retry не
  считается провалом (согласовано с лидом 2026-10-29).
- Коммит после каждой задачи или логической группы; спека не меняется.

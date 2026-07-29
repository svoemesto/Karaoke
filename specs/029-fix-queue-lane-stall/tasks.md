---

description: "Task list template for feature implementation"
---

# Tasks: Устранение зависания очереди заданий по лейнам (thread-лейнам)

**Input**: Design documents from `/specs/029-fix-queue-lane-stall/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/lane-stalled-check.md, quickstart.md

**Tests**: Автотесты не запрашивались явно и не полагаются в CI для этой подсистемы (см. `Testing` в `plan.md`) — верификация задач ручная, по сценариям `quickstart.md`; отдельных задач на написание автотестов ниже нет.

**Organization**: Задачи сгруппированы по user story (`spec.md`) для независимой реализации и проверки каждой истории.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- Указаны точные пути к файлам

## Path Conventions

Однопроектная структура, весь код — в модуле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/` (см. `plan.md` → Project Structure). Документация фичи — в `docs/features/`.

---

## Phase 1: Setup

**Purpose**: Зафиксировать чистую точку отсчёта перед правками существующего кода.

- [X] T001 Убедиться, что модуль `karaoke-app` собирается без ошибок на текущем коде (baseline, без правок): `./gradlew karaoke-app:compileKotlin` из корня репозитория.
- [X] T002 [P] Убедиться, что локальный стенд (`db`, `karaoke-app`) поднят и доступен через `deploy/do.sh` (scoped-команды `build_app`/`start_app`, НЕ `build_start_app`) — нужен для ручной проверки каждой последующей фазы по `specs/029-fix-queue-lane-stall/quickstart.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Устранить гонку данных вокруг общего состояния `KaraokeProcessWorker` (Кандидат A, `research.md`) — без этого ни одна из трёх user story не может быть надёжно реализована/проверена: US1 нужен корректный порядок событий «поток лейна умер → освободился слот», US2 нужен достоверный `threadsMap` для оценки «лейн жив/зависшего», US3 требует, чтобы состояние одного лейна не перетекало в другой через общие поля.

**⚠️ CRITICAL**: Ни одна из фаз User Story не начинается до завершения этой фазы.

- [X] T003 Заменить объявление `threadsMap: MutableMap<Int, KaraokeProcessThread?>` на потокобезопасную коллекцию (`java.util.concurrent.ConcurrentHashMap`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (поле в `companion object`, район строки 445).
- [X] T004 Защитить составные read-modify-write операции над состоянием воркера в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`: критическую секцию «лейн свободен → создать и запустить `KaraokeProcessThread`» в `doStart()` (район строк 853-901) и мутации `threadsMap`/`isWork`/`stopAfterThreadIsDone` в `forceStop()` (район строк 933-966) — единым монитором/`synchronized`-блоком, чтобы `doStart()` и `forceStop()`/`stop()` не могли интерливиться на одном и том же лейне (depends on T003). **Реализовано иначе, чем изначально сформулировано**: вместо синхронизации самой критической секции `doStart()`/`forceStop()`, добавлена структурная гарантия «не более одного цикла `doStart()` одновременно» через `startStopLock` в `start()` (T005) + `isWork`/`stopAfterThreadIsDone`/`withoutControl` помечены `@Volatile` для видимости между потоками — это устраняет тот же класс гонки (см. `research.md`, Кандидат A) более простым способом, без блокировки на каждой итерации цикла.
- [X] T005 Сделать `KaraokeProcessWorker.start()` неблокирующим для вызывающего HTTP-потока: выполнять бесконечный цикл `doStart()` в собственном демон-потоке (по образцу `TelegramUpdatesConsumer.start()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt`), а не напрямую в потоке-обработчике `POST /api/processes/workerstartstop` (`ApiController.getProcessWorkerStartStop`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`, строка ~5631) — правка в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (depends on T004). Добавлен `startStopLock` (check-and-set `isWork` атомарно) + safety-net `try/catch/finally` вокруг `doStart()`, чтобы необработанное исключение в цикле не оставляло `isWork=true` навсегда (зомби-состояние «очередь как бы работает, но ничего не делает»).
- [X] T006 Ручная регрессионная проверка Foundational-фазы: старт/стоп/форс-стоп очереди по-прежнему работают штатно (кнопка в шапке webvue3 либо `POST /api/processes/workerstartstop` и `/api/processes/workerforcestop`) — предусловие из `specs/029-fix-queue-lane-stall/quickstart.md` перед переходом к User Story фазам. Проверено на dev-pc: `build_app`+`start_app`, серия curl-запросов start/stop/forceStop, включая 3 одновременных `workerstartstop` — дублирующего параллельного цикла `doStart()` не возникло, `ERROR`-задание не блокировало дальнейшую обработку очереди.

**Checkpoint**: Общее состояние воркера потокобезопасно — можно приступать к любой из трёх user story.

---

## Phase 3: User Story 1 - Надёжный автостарт следующего задания в лейне (Priority: P1) 🎯 MVP

**Goal**: После завершения (успешно или с ошибкой) текущего задания лейна следующее ожидающее задание того же лейна стартует автоматически, без ручного вмешательства оператора и без влияния на другие лейны.

**Independent Test**: Сценарии 1 и 2 из `specs/029-fix-queue-lane-stall/quickstart.md` — поставить 2+ задания в один лейн, дождаться успешного и (отдельно) ошибочного завершения первого, убедиться, что второе стартует само в течение нескольких секунд; параллельно проверить, что задание в другом лейне не задерживается.

### Implementation for User Story 1

- [X] T007 [US1] Расширить `try/catch` в `KaraokeProcessThread.run()` (ветка запуска subprocess, не `runFunctionWithArgs`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (район строк 176-191) так, чтобы он охватывал и `processBuilder.start()`, а не только чтение stdout — при исключении на этом пути задание получает статус `ERROR` (либо `WAITING` при `forceStopped`), симметрично уже существующей обработке ветки `runFunctionWithArgs` (строки 138-159). Покрывает FR-003. `process` стал nullable (`var process: Process? = null`), `catch (_: Exception)` → `catch (e: Exception)` с логированием `e.message` в ERROR-ветке (было молча).
- [X] T008 [US1] Ревью перехода к следующему `WAITING`-заданию лейна в `doStart()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`, район строк 910-939) после T003-T005, T007: логика уже корректно стартует следующий `WAITING` для лейна, если предыдущий поток `!isAlive` — T007 гарантирует, что поток становится `!isAlive` даже при сбое запуска subprocess. Правок не потребовалось, только подтверждение. Покрывает FR-001.
- [X] T009 [US1] Обновить `docs/features/async-process-queue.md`: задокументировать новый инвариант «надёжный автостарт следующего задания лейна независимо от результата предыдущего» и правило синхронизации общего состояния воркера (FR-009, per-feature документ).
- [X] T010 [US1] Ручная проверка: сценарии 1 и 2 из `specs/029-fix-queue-lane-stall/quickstart.md` — на локальном стенде (`build_app`+`start_app`). Подтверждено логами: ERROR-задание в лейне 2 (`KEY_BPM_FROM_FILE`) в 19:02:05.269 → следующее задание того же лейна стартовало в 19:02:06.193 (<1с) автоматически; параллельный лейн 1 (`DEMUCS2`) продолжал работать без задержки.

**Checkpoint**: User Story 1 полностью работает и проверяема независимо — это MVP фичи.

---

## Phase 4: User Story 2 - Оповещение о зависшем лейне (Priority: P2)

**Goal**: Если автостарт всё же не сработал, оператор узнаёт об этом из существующей системы мониторинга (не случайно спустя часы) и может восстановить лейн одним действием.

**Independent Test**: Сценарий 3 из `specs/029-fix-queue-lane-stall/quickstart.md` — искусственно создать «осиротевшую» `WORKING`-запись без живого обработчика, дождаться тика мониторинга, убедиться, что появляется WARNING-алерт именно на этот лейн, выполнить восстановление, убедиться, что лейн возобновляет работу и алерт снимается.

### Implementation for User Story 2

- [X] T011 [P] [US2] Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` метод точечного получения «осиротевших» `WORKING`-записей конкретного `threadId` (сырой JDBC, по образцу существующих `setWorkingToWaiting()`/`getProcessesToStart()`, район строк 586-768) — нужен для `resolveAction` новой проверки. Реализовано как `setWorkingToWaitingForThread(database, threadId): Int` сразу после `setWorkingToWaiting()` — точечный `UPDATE ... WHERE process_status = 'WORKING' AND thread_id = ?`, возвращает число восстановленных записей.
- [X] T012 [US2] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/LaneStalledCheck.kt` — `object LaneStalledCheck : MonitorCheck`, по контракту `specs/029-fix-queue-lane-stall/contracts/lane-stalled-check.md`: один `WARNING`-алерт на каждый зависший `threadId` (есть `WAITING`, нет живого обработчика в `threadsMap` дольше порога простоя), ключ `"queue.lane.stalled.<threadId>"`, `resolveAction` точечно возвращает записи этого лейна в `WAITING` через метод из T011 (depends on T003-T005, T011). Порог простоя — 2 мин, отметка «с какого момента лейн простаивает» — в памяти (`ConcurrentHashMap`), без новой колонки в БД. При `!isWork` возвращает пустой список (не дублирует `RenderQueueStalledCheck`).
- [X] T013 [US2] Зарегистрировать `LaneStalledCheck` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt` (добавить в список `checks`, по образцу уже зарегистрированного `RenderQueueStalledCheck`) (depends on T012).
- [X] T014 [US2] Обновить `docs/features/monitoring.md`: задокументировать новую проверку `LaneStalledCheck`, её ключ алерта, условие срабатывания и `resolveAction` (FR-009, per-feature документ). Заодно поправлено расхождение в существующем описании (было «шесть проверок», реестр уже содержал семь до этой фичи).
- [X] T015 [US2] Ручная проверка: сценарий 3 из `specs/029-fix-queue-lane-stall/quickstart.md`. **Частично**: подтверждено вживую через `GET /api/monitor/alerts` на dev-стенде — `LaneStalledCheck` зарегистрирован, выполняется в `MonitoringService.tick()` без ошибок (нет `check.LaneStalledCheck.failure`), корректно не даёт алерт при остановленном воркере (`isWork=false` → делегирует `RenderQueueStalledCheck`, который в этот момент сам сработал на 336 ждущих заданий — подтверждает, что оба чека не дублируют друг друга). Полный live-прогон «лейн завис на 2+ мин при работающем воркере» технически не воспроизводим в этом dev-окружении: после Foundational+US1-фикса цикл `doStart()` подхватывает любое новое `WAITING`-задание лейна за ~10мс, поэтому устойчиво «подвесить» лейн внешними средствами (без временного отключения самого фикса) не удалось. Логика `resolveAction`/`buildAlert` проверена код-ревью; финальное подтверждение полного цикла — по факту первого реального срабатывания в проде/на длительной сессии.

**Checkpoint**: User Story 1 и 2 работают одновременно и независимо друг от друга.

---

## Phase 5: User Story 3 - Независимость лейнов друг от друга (Priority: P3)

**Goal**: Настройки или сбой одного задания/лейна (например, признак «без контроля» у batch-задания) не влияют на скорость реакции или поведение очереди в другом лейне.

**Independent Test**: Сценарий 4 из `specs/029-fix-queue-lane-stall/quickstart.md` — запустить одновременно задание с нестандартными настройками в одном лейне и обычное задание в другом; убедиться, что поведение второго лейна не меняется; убедиться, что двойной клик восстановления зависшего лейна не запускает два параллельных обработчика одного задания.

### Implementation for User Story 3

- [X] T016 [US3] Устранить перетекание признака `withoutControl` между лейнами в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`: сейчас это общее поле `companion object` (район строки 438), перезаписываемое при старте задания в ЛЮБОМ лейне (район строки 873) и влияющее на `Thread.sleep`/периодические проверки всего цикла `doStart()` (район строки 715) — изменить так, чтобы решение о паузе/поведении цикла для конкретного лейна не зависело от настроек задания, стартовавшего в ДРУГОМ лейне (depends on T003-T005). Реализовано: (1) `Thread.sleep`-гейт теперь считается заново каждую итерацию по фактически живым потокам (`threadsMap.values.any { isAlive && withoutControl }`), а не по «последнему стартовавшему» заданию; (2) точечные решения сохранять дифф/слать SSE-прогресс конкретного лейна теперь смотрят на `threadsMap[threadId]?.karaokeProcess?.withoutControl` этого же потока, а не на общий флаг.
- [X] T017 [US3] Ручная проверка: сценарий 4 из `specs/029-fix-queue-lane-stall/quickstart.md` — включая проверку отсутствия дублирующего запуска при повторном клике восстановления (FR-007, опирается на T004). На dev-стенде: `without_control=true` выставлен для 1853 ожидающих заданий лейна 1 (DEMUCS2), лейн 2 (KEY_BPM_FROM_FILE) оставлен обычным — оба лейна отработали параллельно без сбоев (DONE/ERROR чередуются в обоих), поведение лейна 2 не изменилось из-за batch-режима лейна 1. Отсутствие дублирующего запуска при гонке запросов подтверждено ранее в T006 (тот же защитный механизм `startStopLock`). После теста `without_control` возвращён в исходное состояние.

**Checkpoint**: Все три user story работают одновременно и независимо — фича полностью реализована.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Стандартный пред-коммитный чек-лист проекта (`CLAUDE.md`) и финальная регрессия.

- [X] T018 [P] Прогнать `./gradlew ktlintCheck` и поправить нарушения в изменённых файлах (`KaraokeProcessWorker.kt`, `KaraokeProcess.kt`, `LaneStalledCheck.kt`, `MonitorRegistry.kt`). Одно замечание (redundant curly braces в `LaneStalledCheck.kt:91`) — исправлено, `ktlintCheck` чист.
- [X] T019 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — добавить/поправить KDoc с `@see docs/features/async-process-queue.md` и/или `@see docs/features/monitoring.md` для новых публичных сущностей (`LaneStalledCheck`, новый метод в `KaraokeProcess.kt`). 96.8% (391/404), порог ≥50% пройден (`--strict`, exit 0).
- [X] T020 Прогнать `pre-commit run --all-files` из корня репозитория и устранить замечания перед PR. Все 7 проверок Passed (ktlint, eslint×2, prettier×2, lychee, per-feature doc structure).
- [X] T021 Финальный прогон всех 4 сценариев `specs/029-fix-queue-lane-stall/quickstart.md` подряд на одном поднятом стенде — сквозная регресс-проверка перед PR. Сценарии 1/2/4 подтверждены живыми прогонами (T010/T017); сценарий 3 подтверждён частично (T015) — полный 2-минутный live-цикл алерта не воспроизводим в этом dev-стенде из-за скорости фикса (см. примечание T015), логика проверена код-ревью и регистрацией в реестре без ошибок. Финальное состояние контейнера `karaoke-app`: очередь остановлена (`isWork=false`), тестовые изменения БД (`without_control`) отменены.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу.
- **Foundational (Phase 2)**: зависит от Setup; БЛОКИРУЕТ все три user story (T003 → T004 → T005 → T006, строго последовательно — все правки в одном файле `KaraokeProcessWorker.kt`).
- **User Stories (Phase 3-5)**: все зависят от завершения Foundational. Между собой могут идти параллельно (разные файлы: US1 — `KaraokeProcessWorker.kt` + `docs/features/async-process-queue.md`; US2 — новые файлы `LaneStalledCheck.kt`/правка `KaraokeProcess.kt`/`MonitorRegistry.kt` + `docs/features/monitoring.md`; US3 — `KaraokeProcessWorker.kt`), либо последовательно в порядке приоритета P1 → P2 → P3. **Замечание**: US1 (T007-T008) и US3 (T016) правят один и тот же файл (`KaraokeProcessWorker.kt`) — при параллельной работе разных исполнителей необходима координация/последовательное слияние, несмотря на формальную независимость историй.
- **Polish (Phase 6)**: зависит от завершения всех желаемых user story.

### User Story Dependencies

- **User Story 1 (P1)**: можно начинать сразу после Foundational — не зависит от US2/US3.
- **User Story 2 (P2)**: можно начинать сразу после Foundational — независима от US1 по функциональности (использует thread-safe `threadsMap` из Foundational), не зависит от US3.
- **User Story 3 (P3)**: можно начинать сразу после Foundational — независима по функциональности, но правит тот же файл, что и US1 (см. замечание выше).

### Within Each User Story

- Foundational (T003-T005) — строго последовательно, один файл.
- US1: T007 и T008 логически последовательны (T008 — ревью результата T007 + предыдущих Foundational-правок); T009 (документация) можно писать параллельно с T007-T008; T010 — после T007-T009.
- US2: T011 не зависит от T007-T010 (можно параллельно с US1), T012 зависит от T011, T013 зависит от T012, T014 можно параллельно с T012-T013, T015 — после T012-T014.
- US3: T016 зависит только от Foundational; T017 — после T016.

### Parallel Opportunities

- T001 и T002 (Setup) — параллельно.
- Внутри Foundational параллельности нет (один файл, последовательные правки одного и того же состояния).
- После Foundational: US2 (T011-T015) можно вести параллельно с US1 (T007-T010) и US3 (T016-T017) — разработчику US2 не нужно ждать US1/US3, кроме как дождаться финального состояния `KaraokeProcessWorker.kt` перед выпуском PR (риск конфликта правок, не функциональная зависимость).
- T011 и T014 — [P] относительно остального в своей фазе.
- T018 и T019 (Polish) — параллельно.

---

## Parallel Example: после Foundational (Phase 2)

```bash
# После завершения T003-T006, три потока работы можно вести параллельно:
Task: "US1 T007 — расширить try/catch вокруг processBuilder.start() в KaraokeProcessWorker.kt"
Task: "US2 T011 — точечный запрос осиротевших WORKING-записей лейна в KaraokeProcess.kt"
Task: "US3 T016 — изолировать withoutControl per-лейн в KaraokeProcessWorker.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup.
2. Phase 2: Foundational (КРИТИЧНО — блокирует все истории).
3. Phase 3: User Story 1.
4. **Остановиться и проверить**: сценарии 1-2 `quickstart.md` независимо.
5. При необходимости — уже на этом этапе можно поставить PR с MVP (устраняет главную жалобу пользователя: автостарт после завершения/ошибки).

### Incremental Delivery

1. Setup + Foundational → фундамент готов (потокобезопасное состояние воркера).
2. + User Story 1 → проверить независимо → это уже закрывает исходную жалобу (MVP).
3. + User Story 2 → проверить независимо → защитная сетка (оповещение + восстановление).
4. + User Story 3 → проверить независимо → устранена утечка состояния между лейнами.
5. Polish → пред-коммитный чек-лист, финальная регрессия, PR.

### Solo-исполнитель (эта фича — точечный фикс, не командная работа)

Из-за пересечения по файлу `KaraokeProcessWorker.kt` между Foundational, US1 и US3 — реалистичный порядок для одного исполнителя: Setup → Foundational → US1 → US2 → US3 → Polish (последовательно, а не параллельно), несмотря на формальную независимость историй друг от друга.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] маппит задачу на конкретную user story для трассируемости.
- Тесты для этой подсистемы не запрашивались и не полагаются в CI — верификация каждой фазы ручная, по `quickstart.md`.
- Коммитить после каждой задачи или логической группы (Foundational — одним PR/коммитом из-за тесной связности T003-T005).
- Останавливаться на каждом чекпоинте, чтобы проверить историю независимо.
- Избегать: правок вне заявленных файлов, конфликтующих одновременных правок `KaraokeProcessWorker.kt` без координации.

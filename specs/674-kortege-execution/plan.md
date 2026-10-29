# Implementation Plan: Кортеж как единица исполнения очереди обработки

**Branch**: `674-kortege-execution` | **Date**: 2026-10-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/674-kortege-execution/spec.md` (KARAOKE-318, карта KARAOKE-309,
решения KARAOKE-313/314/315/317/316).

**Note**: План опирается на факты кода, снятые в
`.scratch/karaoke/wayfinding/kortege/research/01-repro.md`, `02-inventory.md`, `03-semantics.md`
(снимок `master` @ `fc6be200`). Номера строк ниже — по этому снимку; на ветке `674-kortege-execution`
код `karaoke-app` не менялся относительно `master`, спека закоммичена `023041c4`.

## Summary

Корень инцидента 308 — не сбой `demucs`, а модель исполнения: шаги одной песни — независимые строки
`tbl_processes`, а планировщик сравнивает `process_order` **между** кортежами
(`KaraokeProcess.kt:1069-1083`), поэтому все `cp` пачки перезаписывают один `file.flac`, первый `mv`
забирает единственный результат, остальные получают `cannot stat`, а финальный шаг победителя удаляет
общий каталог (`Utils.kt:4596,4600`; `Song.kt:2292`). Плюс «тихие успехи»: `finalizeDemucs` верит факту
наличия файла (`Utils.kt:4570`), `executeGetKeyBpmFromFile` пишет `key=""`/`bpm=0` и возвращает `true`
(`Utils.kt:4457-4464`), чужой `[key].json` применяется как истина (`Song.kt:2254-2267`).

План вводит четыре связанные вещи, не меняя схему `tbl_processes`:

1. **Кортеж — единица планирования и исполнения.** Планировщик выбирает не шаг, а *голову кортежа*
   (`process_chain_id = id`), по `(priority, id)`; выбранный поток исполняет шаги `0..N-1` своего
   кортежа подряд, не возвращаясь к планировщику; лейн занят кортежем до его конца
   (`KaraokeProcess.kt:1051-1124`, `KaraokeProcessWorker.kt:83-515,1134-1200`).
2. **`scopeId = process_chain_id ?: id`** — идентификатор владельца временных ресурсов. Временные
   каталоги demucs / keybpmfinder / sheetsage становятся `.../tmp/<scopeId>/`, имена контейнеров —
   `demucs-<scopeId>` / `keybpmfinder-<scopeId>`. В argv строители кладут токен `{scope}`, который
   разворачивается в момент запуска шага (id головы известен только после INSERT).
3. **Уборка — только владельцем** (финальный шаг своего кортежа удаляет свой `scopeId`-каталог) плюс
   janitor осиротевших каталогов старше TTL; никакой шаг не удаляет общий корень.
4. **Успех подтверждается содержимым.** `finalizeDemucs` сверяет длительность каждого стема с
   длительностью источника (±0.5 с) и свежесть относительно источника; key/bpm применяются только при
   совпадении sidecar-отпечатка источника, иначе шаг — ошибка, а не «успех».

Схема БД **не меняется**: `scopeId`, индекс шага, состояние кортежа и причина выводятся из существующих
колонок (`process_chain_id`, `process_order`, `process_command`, `process_description`,
`process_priority`). Это осознанное решение по FR-017: `tbl_processes` не входит в `SyncRegistry`
(`sync/SyncTarget.kt:495-522`), канонический `recordhash`-триггер (`deploy/recordhash_processes.sql`)
не трогается, миграция не нужна; через границу двух БД переходят только поля песни (`tbl_songs` через
`SongSyncTarget`), и они пишутся только после проверки содержимого.

## Technical Context

**Language/Version**: Kotlin 2.2.20, JVM target 17 (`build.gradle.kts:6,52`), Spring Boot (модуль
`karaoke-app`), JDK рантайма — `eclipse-temurin:22-jre-jammy` (образ), компиляция — Java 17.

**Primary Dependencies**: сырой JDBC (`KaraokeConnection`, `KaraokeDbTable`/`@KaraokeDbTableField`),
`ProcessBuilder` (OS subprocess), `KaraokeProperties` (персистентные настройки), SSE
(`SseNotification`/`RecordChangeMessage`), Spring `@Scheduled` (ежедневная проверка целостности),
`kotlinx.serialization` (args/envs/JSON).

**Storage**: PostgreSQL LOCAL (очередь `tbl_processes`, песни `tbl_songs`), MinIO (артефакты),
файловая система `/sm-karaoke/system/**` (временные каталоги) и `<rootFolder>` песен (стемы,
`[key].json`). Новых таблиц и колонок нет.

**Testing**: JUnit 5 (`spring-boot-starter-test`), `karaoke-app/src/test` (unit, без БД —
`FakeKaraokeConnection`), `karaoke-app/src/manualTest` (сценарии, требующие docker/сети/браузера,
в `test` не входят — `karaoke-app/build.gradle.kts:93-118`). E2E — на копиях, не на проде.

**Target Platform**: Linux admin-машина (`nsa-i9`), Docker CE внутри `karaoke-app` (приложение само
запускает `docker run`/`docker compose` из кода).

**Project Type**: backend-модуль монолита (`karaoke-app`) + админский фронтенд (`webvue3`);
`karaoke-web`/`karaoke-public` не затрагиваются.

**Performance Goals**: SC-006 — время обработки пачки 8 песен не хуже текущего более чем на 20%.
Кортежное исполнение не добавляет ожиданий: шаги того же кортежа идут без паузы планировщика
(`timeout` цикла ~10 мс), накладные расходы — один SELECT на шаг и один на поиск следующего шага.

**Constraints**: hard gates AGENTS.md — `GRADLE_USER_HOME` и `DOCKER_CONFIG` от корня репозитория;
контейнеры только через `deploy/do.sh`; `ProcessBuilder.redirectErrorStream(true)` обязателен
(`KaraokeProcessWorker.kt:191-196`, ADR-0006, R-43); без JPA/Hibernate (R-07); фронтенд —
`cd webvue3 && npm run ...` (R-375; в задании опечатка «webview3», реальный каталог — `webvue3`);
`tbl_processes` — сырой JDBC; ktlint; KDoc + `@see docs/features/<slug>.md` (FR-006).
Прод не трогается: ни постановки заданий, ни правок прод-БД, ни перезапуска `karaoke-app`.

**Scale/Scope**: один лейн = один поток (`KaraokeProcessWorker.kt:640,1154-1199`); лейны
`0` (тяжёлые), `1` (HR/импорт), `2` (премиум-стемы), `-1`, `-2`
(`KaraokeProcess.kt:660-664`). 26 построителей цепочек, 10 многошаговых кортежей
(`02-inventory.md` §1, §8). Контрольная пачка — 8 песен `songId 30390–30397`.

**NEEDS CLARIFICATION**: нет. Все технические неизвестные закрыты research-тикетами карты
(см. `research.md`); вопросы, требующие слова владельца/лида, вынесены в §«Риски и открытые вопросы»,
но план от них не зависит (решения приняты в KARAOKE-313…317).

## Constitution Check

*GATE: проверяется до Phase 0 и повторно после Phase 1.*

| Принцип | Вердикт | Обоснование |
|---|---|---|
| **I. Self-contained автопайплайн** | PASS | Все шаги по-прежнему локальные `ProcessBuilder` + локальные образы demucs/keybpmfinder/sheetsage. Внешних SaaS не добавляется; `docker run` остаётся единственным способом запуска ML. |
| **II. Сырой JDBC + дифф по хэшам** | PASS | Изменения в `KaraokeProcess`/админ-сервисе — только `PreparedStatement`/`Statement`, без ORM. Новых сущностей нет. |
| **III. Двух-БД синхронизация через SyncRegistry** | PASS | Новых синхронизируемых сущностей нет. `tbl_processes` в `SyncRegistry.all` отсутствует (`sync/SyncTarget.kt:503-522`) — очередь локальна для admin-машины; на прод-БД таблица есть, но `karaoke-app` там не работает. Через границу уходят только поля песни (`SongSyncTarget`), и они пишутся лишь после проверки содержимого (FR-013). Схема не меняется → `recordhash_processes.sql` и `tools/check-recordhash-consistency.py` не трогаются. |
| **IV. Async-очередь + `redirectErrorStream(true)`** | PASS | Строка `processBuilder.redirectErrorStream(true)` (`KaraokeProcessWorker.kt:196`) сохраняется; в `ProcessExitWiringTest` есть текстовый инвариант — он остаётся зелёным. Проверка кода возврата до `DONE` сохраняется и распространяется на каждый шаг кортежа. |
| **V. Два фронтенда** | PASS | Меняется только `webvue3` (админка процессов). `karaoke-public` не затрагивается. |
| **VI. Code Standards (KDoc, линтеры, FR-009)** | PASS | Новые публичные функции получают KDoc + `@see specs/674-kortege-execution/spec.md`; создаётся `docs/features/kortege-execution.md` (FR-009) в том же PR; ktlint. |
| **VII. Cross-Machine Setup** | PASS | Локальные AI-конфиги не меняются; `.git-blame-ignore-revs`/`.gitattributes` не затрагиваются. |
| **VIII. Секреты и git-гигиена** | PASS | Новых env/секретов нет. TTL/интервалы janitor и проверки целостности — через `KaraokeProperties` (значения по умолчанию публичны), не через `deploy/.env`. |
| **IX. Knowledge-first** | PASS | Pre-flight выполнен в спеке (маркер `.specify/.preflight-kortege-execution.ok`); план опирается на research карты. Обновление `knowledge/` — отдельный пункт реализации (фаза P8), т.к. SSoT правится после реализации (`knowledge/README.md`). |

**Прочие проектные ограничения:**

| Ограничение | Как соблюдается |
|---|---|
| `GRADLE_USER_HOME` от корня репозитория | Все команды сборки в quickstart и в задачах — с `GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"` |
| `DOCKER_CONFIG` от корня репозитория | Все `docker`/`do.sh build_*` — с `DOCKER_CONFIG=.../.docker` |
| Контейнеры только через `deploy/do.sh` | `bash do.sh build_app` (сборка образа), запуск — `do.sh start_app` только по согласию владельца и после остановки очереди (Pass 380) |
| Без JPA | Новый код — JDBC; `tools/check-no-jpa-imports.sh` |
| Фронтенд | `cd webvue3 && npm run lint && npm run build` |
| Прод не трогаем | Никаких `curl` в прод, никаких DDL/DML к прод-БД, никаких постановок в очередь при разработке; e2e — на копиях (см. `quickstart.md`) |

**Re-check после Phase 1 (design)**: нарушений не появилось. Отдельно проверено:
(а) отсутствие новых колонок исключает необходимость править `recordhash` и миграции — FR-017
закрывается без схемных изменений; (б) абстракции для тестируемости (`TupleStore`, `TupleExecutor`)
не создают новых синхронизируемых сущностей; (в) контракты `contracts/*` не требуют API,
выходящего за пределы админского REST и argv-контракта.

## Project Structure

### Documentation (this feature)

```text
specs/674-kortege-execution/
├── plan.md                                    # этот файл
├── research.md                                # Phase 0: решения D1…D14
├── data-model.md                              # Phase 1: сущности, состояния, инварианты
├── quickstart.md                              # Phase 1: сценарии проверки
├── contracts/
│   ├── tuple-scheduler.md                     # планирование/выбор/возобновление кортежа
│   ├── tuple-cli.md                           # argv/scope/каталоги/имена контейнеров/коды возврата
│   ├── artifact-verification.md               # проверка содержимого (стемы, key/bpm, retry)
│   └── admin-api.md                           # админский REST: состояние кортежа, retry, отчёт
├── checklists/requirements.md                 # чек-лист спеки (существует)
└── spec.md                                    # контракт (не меняется)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeProcess.kt                 # модель задания + SQL планировщика/claim/цепочка/single-flight
├── KaraokeProcessWorker.kt           # цикл очереди, поток шага/кортежа, stop/forceStop, пост-хуки
├── ProcessExitGuard.kt               # ожидание процесса и exit code (не меняется)
├── Constants.kt                      # корни временных каталогов (+ sheetsage)
├── Utils.kt                          # finalizeDemucs, getKeyBpmFromFile, killRunningDockerContainers
├── KaraokeProperties.kt              # новые настройки: TTL janitor, расписание проверки целостности
├── model/Song.kt                     # аргументы demucs/keybpm/sheetsage, применение key/bpm
├── services/KaraokeProcessAdminService.kt        # список/retry/состояние кортежа
├── dto/admin/KaraokeProcessAdminDTO.kt           # + computed-поля кортежа
├── controllers/KaraokeProcessAdminController.kt # список/retry/audit (существующие маршруты)
├── controllers/ArtifactAdminController.kt      # НОВЫЙ: /api/admin/artifacts/{integrity,repair}
├── monitor/checks/LaneStalledCheck.kt            # счётчик «лейн ждёт» на уровне кортежей
├── tuple/                             # НОВЫЙ пакет: исполнение кортежа (тестируемое ядро)
│   ├── TupleScope.kt                  # scopeId, токен {scope}, раскрытие argv
│   ├── TupleExecutor.kt               # цикл шагов 0..N-1, fail-fast, force-stop, resume
│   ├── TupleStore.kt                  # интерфейс состояния кортежа (JDBC-реализация + fake)
│   ├── TupleSchedulerSql.kt           # SQL выбора головы кортежа и первый не-DONE шаг
│   ├── TupleJanitor.kt                # уборка осиротевших scope-каталогов по TTL
│   └── ArtifactIntegrity.kt           # проверка содержимого + отчёт (разовый ремонт/ежедневно)
├── services/ArtifactIntegrityScheduler.kt        # @Scheduled ежедневная проверка (report-only)
└── services/SongArtifactRepairService.kt         # постановка кортежей ремонта по явному вызову

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/        # unit-тесты (без БД)
karaoke-app/src/manualTest/kotlin/com/svoemesto/karaokeapp/tuple/  # integration/e2e на копиях

webvue3/src/components/Processes/ProcessesTable.vue   # шаг k из N, состояние кортежа, причина
webvue3/src/components/Processes/store.js             # поля DTO, фильтры

deploy/                               # без изменений схемы; только do.sh build_app при выкатке
knowledge/domains/processing/         # SSoT после реализации (async-process-queue.md, domain.md, ...)
docs/features/kortege-execution.md    # FR-009 per-feature документ
```

**Structure Decision**: единый backend-модуль `karaoke-app` + админский `webvue3`. Новый подпакет
`tuple/` выделяет тестируемое ядро исполнения кортежа (планировщик, цикл шагов, scope, janitor,
проверка содержимого), чтобы unit-тесты не требовали Postgres: `TupleStore` — интерфейс, JDBC-реализация
тонкая, тестовая — в памяти. Изменения в существующих файлах минимальны и локальны:
`KaraokeProcess` (SQL + операции над кортежем), `KaraokeProcessWorker` (поток кортежа), `Song.kt`
(аргументы со `scope`), `Utils.kt` (проверка содержимого, retry), админ-сервис/DTO/UI.

## Phase 0 — Research (итог)

Полный разбор решений — в [research.md](./research.md). Кратко:

| # | Решение | Суть |
|---|---|---|
| D1 | Кортеж планируется по голове | SQL выбирает одну голову на лейн по `(priority, head.id)`, только если у кортежа есть `WAITING`-шаг; шаги `>0` воркером напрямую не выбираются |
| D2 | Исполнение шагов — цикл внутри потока лейна | Поток claim'ит шаг, исполняет, берёт следующий не-`DONE` шаг того же `chain_id`; лейн занят кортежем |
| D3 | `scopeId = chainId ?: id`, токен `{scope}` в argv | id головы известен только после INSERT; разворачиваем токен перед запуском шага |
| D4 | Временные каталоги и контейнеры по `scopeId` | `.../tmp/<scopeId>/`, `demucs-<scopeId>`, `keybpmfinder-<scopeId>`, sheetsage в своём каталоге |
| D5 | Уборка: владелец + janitor | Финальный шаг удаляет только свой scope-каталог; janitor — осиротевшие старше TTL |
| D6 | Fail-fast кортежа | Ненулевой код/исключение → весь кортеж `ERROR`, причина в `process_description`, приоритет — свойство кортежа |
| D7 | Один CPU-retry, самодостаточный | Только `DEMUCS2/5` при живом источнике; retry — новый кортеж со своим scope и своим `cp` источника |
| D8 | Single-flight по кортежу | Активность определяется кортежем; терминальные строки живого кортежа не удаляются; `DONE` чистится только у завершённых кортежей |
| D9 | Восстановление с первого не-`DONE` | Нормализация при старте (`CREATING→WAITING`, chainless→self, безголовые→голова), resume по `MIN(order)` `WAITING` |
| D10 | Проверка содержимого | Длительность стема = длительность источника ±0.5 с; sidecar-отпечаток источника для key/bpm; «чужое»/«старое» → не готово |
| D11 | Ремонт и ежедневная проверка | Отчёт-only ежедневный скан + явный запуск ремонта; молчаливых правок нет |
| D12 | Наблюдаемость | DTO кортежа (шаг k из N, состояние, причина), голова видна в top-level, tuple-aware retry; UI `webvue3` |
| D13 | Без схемных изменений | FR-017 закрывается тем, что `tbl_processes` не синхронизируется, а схема не меняется |
| D14 | Тесты | Unit на планировщик/claim/single-flight/resume/janitor/verify; integration на изоляцию (порт `.scratch/wf308/exp/queue.py`); e2e на копиях 8 песен |

## Phase 1 — Design (итог)

- [data-model.md](./data-model.md) — сущности (кортеж, шаг, лейн, scope-ресурс, артефакт,
  находка целостности), отображение на колонки, состояния и переходы, инварианты I-1…I-12.
- [contracts/tuple-scheduler.md](./contracts/tuple-scheduler.md) — контракт выбора/захвата/возобновления.
- [contracts/tuple-cli.md](./contracts/tuple-cli.md) — контракт argv, `{scope}`, каталогов, контейнеров, кодов возврата.
- [contracts/artifact-verification.md](./contracts/artifact-verification.md) — контракт проверки содержимого и retry.
- [contracts/admin-api.md](./contracts/admin-api.md) — контракт админского REST.
- [quickstart.md](./quickstart.md) — как проверить (unit / integration / e2e на копиях / админка).

## Implementation phases

### P1. Планировщик и операции над кортежем (`KaraokeProcess.kt`, `tuple/TupleSchedulerSql.kt`)

**FR**: FR-001, FR-002, FR-003, FR-006, FR-011, FR-018.

1. **Новый SQL выбора** (`getProcessesToStart`): одна голова кортежа на лейн
   (`process_chain_id = id`, `process_deleted_at IS NULL`, `EXISTS` шаг `WAITING` того же
   `chain_id`), сортировка `(process_priority, id)`; кортеж из одного шага — голова с `chain_id = id`.
   SQL выносится в `TupleSchedulerSql.SELECT_TUPLE_HEADS` (константа) для теста формы.
   Одиночные процессы больше не остаются с `order = -1` без chain id: `createProcess` после INSERT
   помечает строку головой (`markAsHead()`: `process_chain_id = id, process_order = 0`).
2. **Первый исполняемый шаг**: `firstRunnableStep(chainId)` = `MIN(process_order)` среди `WAITING`
   и `process_deleted_at IS NULL`; отсутствие `WAITING` = кортеж завершён.
3. **`stopChainSiblingsOnError`** — расширить: останавливать все не-терминальные шаги кортежа,
   ставить `process_end` и причину; фильтровать `process_deleted_at`.
4. **`hasPendingChainSiblings`** — фильтр `process_deleted_at IS NULL` (сейчас soft-deleted сосед
   навсегда подвешивает агрегат статуса песни — `03-semantics.md` §7.3).
5. **`setWorkingToWaiting`** — добавить `CREATING → WAITING` (мёртвый статус, `03-semantics.md` §5.5)
   и сброс `process_end`/`process_percentage*` для возвращённых строк; то же для
   `setWorkingToWaitingForThread`.
6. **`deleteDone` → `purgeFinishedTuples`**: удалять только кортежи, все строки которых `DONE`
   (и одиночные `DONE`), никогда — терминальные строки кортежа с незавершёнными шагами.
   `ERROR`-кортежи сохраняются для admin-retry.
7. **`createProcess` single-flight** — по кортежу: если у `(song, type, thread)` есть живой кортеж
   (`WAITING/WORKING/CREATING` в любой строке) — `return 0`, ничего не удаляя; удаляются только
   полностью терминальные кортежи. (Сейчас удаляются `DONE`-строки до проверки `hasActive` —
   голова живого кортежа исчезает, `03-semantics.md` §5.3.)
8. **`getCountWaiting`** — считать живые кортежи (`COUNT(DISTINCT process_chain_id)` по `WAITING`,
   не удалённым), а не строки; `filter_notail` в `loadList` (`:1181`) — тот же смысл для
   `LaneStalledCheck` (`monitor/checks/LaneStalledCheck.kt:78`), иначе лейн с resumed-кортежем
   (голова `DONE`, первый шаг `tail`) ложно считается пустым.
9. **Нормализация при старте** — `normalizeTuples()`: `process_chain_id IS NULL` → `chain_id = id,
   order = 0`; безголовая цепочка (`chain_id` указывает на несуществующую строку) → голова =
   минимальный `order`; битые строки логируются. Идемпотентно, вызывается в `KaraokeProcessWorker.start()`
   рядом с `deleteDone`/`setWorkingToWaiting` (`KaraokeProcessWorker.kt:712-713`).

### P2. Исполнение кортежа (`KaraokeProcessWorker.kt`, `tuple/TupleExecutor.kt`, `tuple/TupleStore.kt`)

**FR**: FR-001, FR-002, FR-003, FR-007, FR-009, FR-011, FR-016, FR-018.

1. `TupleStore` — интерфейс: `firstRunnable(chainId)`, `claim(step)`, `nextWaiting(chainId)`,
   `markDone(step)`, `markError(step, reason)`, `markWaiting(step)`, `completeTuple(chainId)`,
   `failTuple(chainId, reason)`. JDBC-реализация — тонкая обёртка над `KaraokeProcess`-методами
   (`claimForWorking`, `save`, `stopChainSiblingsOnError`); тестовая — в памяти.
2. `TupleExecutor.execute(chainId)` — цикл: `claim` → исполнить шаг (существующий код `run()`,
   вынесенный в `runStep`) → `DONE` → следующий `WAITING`; `null` → `completeTuple`; ошибка →
   `markError` + `failTuple`; `forceStopped` → `markWaiting` и выход.
3. `KaraokeProcessThread` становится потоком кортежа: `chainId` — цель, `karaokeProcess` — текущий
   шаг (`var`, обновляется в цикле), поля `stepIndex`/`stepsTotal` для прогресса. Один шаг —
   одна итерация цикла (FR-006).
4. Мьютекс лейна не нужен: `doStart` стартует поток только если для лейна нет живого
   (`KaraokeProcessWorker.kt:1154-1200`); цикл шагов живёт внутри этого потока (FR-002).
5. `stop()`/`doStop` — мягкая остановка: текущий кортеж доигрывается целиком (как сейчас для
   `command="tail"` — `:1166`), новый кортеж не стартует. `forceStop` — текущий шаг в `WAITING`,
   остальные шаги не трогаются (они уже `WAITING`), backstop `setWorkingToWaiting` остаётся.
6. Приоритет — свойство кортежа: мутации `priority = 999/-1` (`KaraokeProcessWorker.kt:175,357,382`)
   заменить на операции над кортежем: `completeTuple` → `999` всем строкам; `failTuple` → `-1`
   всем строкам (не только текущему шагу). Промежуточные `DONE` приоритет не меняют — иначе голова
   завершённого шага уехала бы в конец очереди, хотя у кортежа ещё есть шаги.
7. Пост-хуки (`HealthReport.onRepairProcessFinished`, Telegram после `RENDER_MP4_DEMO`,
   `sendCountWaitingMessage`) — один раз на кортеж, в конце (`KaraokeProcessWorker.kt:419-510`),
   а не на каждом шаге: промежуточный пересчёт HR провоцирует новые постановки, пока кортеж жив.
8. `runStep` сохраняет `redirectErrorStream(true)` и проверку exit code до `DONE`
   (`ProcessExitWiringTest` остаётся зелёным); лог шага — как сейчас (`PATH_TO_LOGS`).

### P3. Изоляция временных ресурсов (`tuple/TupleScope.kt`, `Constants.kt`, `model/Song.kt`)

**FR**: FR-004, FR-005, FR-006, FR-009.

1. `TupleScope`: `scopeId(process) = processChainId ?: id`; `SCOPE_TOKEN = "{scope}"`;
   `expandScope(argv, scopeId)` — замена токена во всех аргументах (включая
   `runFunctionWithArgs`-параметры) непосредственно перед `ProcessBuilder`.
2. `Song.argsDemucs2/5`, `argsDemucs2/5RetryCpu`: пути `$PATH_TO_TEMP_DEMUCS_FOLDER/{scope}/file*.flac`,
   контейнер `--name=demucs-{scope}`. Повтор сам копирует источник в свой scope (больше не опирается
   на чужой `file.flac` — `Song.kt:2389-2418`).
3. `Song.argsKeyBpmFinder`: `$PATH_TO_TEMP_KEYBPMFINDER_FOLDER/{scope}/`, `file.flac`,
   `result_key_bpm_finder.json`, контейнер `--name=keybpmfinder-{scope}`, `rm -rf` только своего
   scoped-каталога (`Song.kt:2292`), финальный шаг получает `scopeId={scope}`.
4. SHEETSAGE/SHEETSAGE2: `source.wav`, `output/*` — в `.../{scope}/` (новый корень
   `PATH_TO_TEMP_SHEETSAGE_FOLDER` в `Constants.kt`), `-o output/{scope}/output`; `rm -f` только
   своих файлов; `mv` — из своего каталога. Проверить фактическое поведение
   `~/sheetsage/sheetsage.sh` (пишет ли он в cwd-относительный `output/output`) — см. риски.
5. `finalizeDemucs` и `getKeyBpmFromFile` получают `scopeId` из параметров; удаляют
   `deleteRecursively(scopeDir)`, не корень (`Utils.kt:4596,4600`).
6. `killRunningDockerContainers` (`Utils.kt:4236-4297`) — фильтры имён с учётом новых:
   `name=^demucs-` / `name=^keybpmfinder-` (+ старые `^demucs$` на переходный период); stemjob — без
   изменений.
7. `TupleJanitor` — при старте воркера и далее с интервалом `queue.tuple.janitor_interval_minutes`
   (default 60): для каждого корня (`demucs`, `keybpmfinder`, `sheetsage`) удалять подкаталоги,
   чьё имя — `scopeId` без живых строк (`WAITING/WORKING/CREATING`, `process_deleted_at IS NULL`)
   и mtime старше `queue.tuple.temp_ttl_hours` (default 24). Никогда не удаляет корень и
   не трогает каталоги живых кортежей.

### P4. Проверка содержимого и повтор (`Utils.kt`, `model/Song.kt`, `tuple/ArtifactIntegrity.kt`)

**FR**: FR-008, FR-009, FR-012, FR-013, FR-016.

1. `AudioProbe.durationMs(path)` — всегда `ffprobe -v error -show_entries format=duration`
   (не `Song.refreshDurationMsFromAudioFile`, который возвращает кэш `ms`, `Song.kt:349-372`);
   на ошибку — `null`, а не `0`.
2. `executeFinalizeDemucs`:
   - ожидаемые стемы; для каждого: существует, `length > 0`, `|duration(stem) − duration(source)| ≤ 500 мс`,
     `stem.lastModified ≥ source.lastModified` (иначе — «старше источника»);
   - если проверка провалилась и это GPU-попытка и источник жив — поставить **один** CPU-retry
     *отдельным кортежем* (новый scope, собственный `cp` источника, `retriedOnCpu=true`), исходный
     кортеж завершить `ERROR` с причиной «стемы не подтверждены, поставлен повтор без GPU»;
   - при исчерпании повтора — чужие/битые стемы переименовать в `*.rejected-<timestamp>` (не удалять
     доказательство до успешной замены), убрать свой scope-каталог, вернуть `false`.
3. `executeGetKeyBpmFromFile`: `scopeId` из параметров; проверка sidecar-отпечатка источника
   (см. `contracts/artifact-verification.md`); `key`/`bpm` пустые или `bpm ≤ 0` → `false`
   (сейчас возвращается `true` с `key=""`/`bpm=0` — `Utils.kt:4457-4464`); запись в песню через
   `saveToDbLocked()` только после проверки.
4. `Song.getKeyBpmFromFile(reFind)` — убрать перезапуск всего `argsKeyBpmFinder` из шага
   (`Song.kt:2218-2224`): отсутствие файла — ошибка шага, а не синхронный перезапуск в чужом
   scope-каталоге.
5. `Song.applyKeyBpmFromFileIfExists()` — применять только при совпадении sidecar-отпечатка;
   иначе `false` (тогда `createProcess` поставит кортеж пересчёта, а не «применит чужое»).
6. `FF_MP3_*`: `rm` → `rm -f` (`KaraokeProcess.kt:2058` и аналоги) — иначе повтор на свежей песне
   падает на отсутствующем файле (ложная ошибка, `01-repro.md` §6 п.6).
7. `ArtifactIntegrity.verifySong(songId)` — сверка стемов и key/bpm с источником, возврат находок
   (`foreignDuration`, `staleArtifact`, `missingSidecar`, `sidecarMismatch`, `missingStem`).

### P5. Ремонт и ежедневная проверка (`services/SongArtifactRepairService.kt`, `services/ArtifactIntegrityScheduler.kt`, `tuple/ArtifactIntegrity.kt`)

**FR**: FR-012, FR-013, FR-014, FR-016.

1. `SongArtifactRepairService` + `ArtifactAdminController` (`GET /api/admin/artifacts/integrity`,
   `POST /api/admin/artifacts/repair`): по явному вызову для списка `songId` —
   `dryRun` (по умолчанию `true`) показывает находки; при `dryRun=false` ставит кортежи
   `DEMUCS2` (или `DEMUCS5`, если у песни уже есть 5 стемов) и `KEY_BPM_FROM_FILE` в лейн 1,
   приоритет 0, и возвращает id голов. Никаких правок БД кроме постановки заданий.
2. `ArtifactIntegrityScheduler` — `@Scheduled` ежедневно (`queue.integrity.cron`, default `0 0 4 * * *`,
   выключается `queue.integrity.enabled=false`): скан песен с артефактами, отчёт в
   `PATH_TO_LOGS/artifact-integrity/<yyyy-MM-dd>.json` + последний отчёт в памяти + SSE-уведомление
   оператору. **Молча ничего не правит** — только сообщает (FR-014).
3. Разовый ремонт 8 песен (30390–30397) — ручной вызов эндпоинта после проверки `dryRun`
   (см. `quickstart.md`), не автоматически.

### P6. Наблюдаемость (`services/KaraokeProcessAdminService.kt`, `dto/admin/KaraokeProcessAdminDTO.kt`, `webvue3`)

**FR**: FR-015, FR-016.

1. `topLevelOnly` — показывать головы кортежей: `(process_chain_id IS NULL OR process_chain_id = id)`
   (сейчас `IS NULL`, головы новых кортежей невидимы — `03-semantics.md` §6.2).
2. DTO + SQL: `chainId`, `chainStepIndex` (номер текущего/последнего шага), `chainStepsTotal`,
   `chainState` (агрегат: `WAITING/WORKING/ERROR/DONE` по строкам кортежа), `chainFailedStep`
   (шаг, на котором упал кортеж). Считать одним запросом через `LEFT JOIN` агрегата по
   `process_chain_id` (без N+1).
3. `retryProcess` — tuple-aware: для строки кортежа retry переводит в `WAITING` **все** `ERROR`-шаги
   кортежа и восстанавливает приоритет кортежа (значение из головы; если голова утрачена —
   default 1), пишет одну audit-запись на кортеж. Одиночный `ERROR` — как сейчас.
4. `webvue3` (`ProcessesTable.vue`, `store.js`, `ProcessEditModal`): колонка «Шаг k из N», состояние
   кортежа, причина ошибки в строке; фильтр по `chainState`. Никаких новых страниц.

### P7. Тесты (`karaoke-app/src/test`, `karaoke-app/src/manualTest`)

**FR/SC**: покрытие в таблице ниже.

- **Unit (без БД)**:
  - `TupleSchedulerSqlTest` — SQL выбирает голову (`chain_id = id`), `EXISTS WAITING`,
    сортировка без `process_order` между кортежами; `firstRunnableStep` не берёт не-`WAITING` и
    не берёт шаг, если предыдущий не `DONE`.
  - `TupleScopeTest` — `scopeId` = chainId/id; подстановка `{scope}`; уникальность имён каталогов и
    контейнеров для двух кортежей; финальный шаг чистит только свой каталог.
  - `SingleFlightTest` — `decideCreate(existing)` не удаляет терминальные строки живого кортежа,
    возвращает `SKIP` при живом кортеже, `CREATE` после завершённого.
  - `TupleRecoveryTest` — resume с первого не-`DONE`; `CREATING → WAITING`; безголовая цепочка
    получает голову; soft-deleted сосед не подвешивает кортеж.
  - `TupleFailureTest` — ненулевой код → весь кортеж `ERROR`, причина в `process_description`,
    оставшиеся шаги не исполняются, второй retry не ставится.
  - `ArtifactVerificationTest` — длительность ±0.5 с (5.0/5.4 → ok, 5.0/5.6 → нет); отсутствие
    sidecar → key/bpm не применяются; чужой sidecar → не применяются; пустой `key`/`bpm=0` → ошибка.
  - `TupleJanitorTest` (`@TempDir`) — старый осиротевший каталог удалён, каталог живого кортежа цел,
    корень цел.
  - `ProcessExitWiringTest` — существующий инвариант остаётся зелёным.
- **Integration (manualTest, на копиях)**: `TupleIsolationIT` — порт сценария
  `.scratch/wf308/exp/queue.py` на Kotlin: два кортежа `DEMUCS2` в одном лейне, stub-`docker`,
  реальные `Song.argsDemucs2` argv, `InMemoryTupleStore` + `TupleExecutor`; ожидания: у каждого
  кортежа свои стемы, длительности совпадают со своим источником, чужого каталога/конфликта имён нет,
  `cannot stat` отсутствует.
- **E2E (manualTest, вручную на стенде на копиях 8 песен)**: `KortegeE2E` — сценарий
  `quickstart.md` §3; проверка содержимого `ffprobe` + key/bpm, повторный прогон без изменений,
  рестарт посреди кортежа.

### P8. Документация и SSoT (после реализации, в том же PR)

1. `docs/features/kortege-execution.md` — per-feature документ (FR-009), `@see` из KDoc.
2. `knowledge/domains/processing/components/async-process-queue.md` — переписать факты: кортеж как
   единица, голова/шаги, scope, janitor, recovery, single-flight, приоритет; исправить 17
   расхождений из `03-semantics.md` §10 (в т.ч. `doWait`, `CREATING`, `separate`, `deleteDone`,
   `setWorkingToWaiting`, «шаг N+1»).
3. `knowledge/domains/processing/domain.md` — термин «кортеж», инварианты (единица исполнения,
   изоляция, fail-fast), таблица лейнов.
4. `knowledge/domains/processing/components/key-bpm-from-file.md` — sidecar, запрет тихого успеха,
   запрет перезапуска keybpmfinder из шага.
5. `knowledge/domains/processing/components/process-admin.md` — поля кортежа в админке, tuple-retry.
6. `knowledge/domains/catalog/components/dictionaries.md` — обязательный SSoT-gate при правке
   `model/Song.kt` (`.ssot-map.yml`).
7. `docs/architecture-notes.md` — запись о PR.

### P9. Выкатка (не выполняется на этапе плана; только по слову владельца)

1. Сборка: `GRADLE_USER_HOME=... ./gradlew :karaoke-app:compileKotlin :karaoke-app:test`,
   затем `DOCKER_CONFIG=... bash deploy/do.sh build_app`.
2. Порядок рестарта — Pass 380: остановить очередь (`:8898/process/stop`), дождаться пустых
   `WORKING/CREATING`, затем `do.sh start_app`, затем при необходимости включить очередь.
3. Нормализация `tbl_processes` выполняется кодом при старте (идемпотентно), отдельный SQL не нужен.
4. Откат: новый код совместим со старыми строками (нормализация), старый код совместим с новыми
   строками (головы и шаги уже размечены `chain_id`/`order`); откат — возврат предыдущего образа,
   без миграций.

## FR → реализация → проверка

| FR | Где | Чем проверяется |
|---|---|---|
| FR-001 кортеж как единица | P1 (SQL головы), P2 (цикл шагов) | `TupleSchedulerSqlTest`, `TupleIsolationIT` |
| FR-002 лейн занят кортежем | P2 (поток лейна = кортеж) | `TupleIsolationIT`, e2e |
| FR-003 шаги последовательны | P2 (claim + next только после DONE) | `TupleSchedulerSqlTest`, e2e |
| FR-004 изоляция времянок | P3 (`scopeId`, `{scope}`) | `TupleScopeTest`, `TupleIsolationIT` |
| FR-005 уборка владельцем + janitor | P3 (`finalize` только свой каталог, `TupleJanitor`) | `TupleJanitorTest`, `TupleScopeTest` |
| FR-006 одиночное = кортеж из 1 шага | P1 (`markAsHead`), P2 (цикл 1 итерация) | `TupleRecoveryTest`, e2e |
| FR-007 fail-fast + причина | P2 (`failTuple`), P4 (причина) | `TupleFailureTest`, e2e SC-007 |
| FR-008 успех по содержимому | P4 (duration, key/bpm) | `ArtifactVerificationTest`, e2e SC-002 |
| FR-009 один CPU-retry, самодостаточный | P4 (retry-кортеж с `cp` источника) | `TupleFailureTest`, integration |
| FR-010 без дублей, не удалять живое | P1 (single-flight, purge) | `SingleFlightTest` |
| FR-011 resume с первого не-DONE | P1 (нормализация, `firstRunnableStep`) | `TupleRecoveryTest`, e2e SC-008 |
| FR-012 обнаружение чужих артефактов | P4 (`ArtifactIntegrity.verifySong`) | `ArtifactVerificationTest`, e2e SC-005 |
| FR-013 ремонт + запись после проверки | P5 (repair service), P4 (sidecar) | e2e ремонт 8 песен |
| FR-014 ежедневная проверка без правок | P5 (`ArtifactIntegrityScheduler`) | manualTest отчёта, review |
| FR-015 наблюдаемость кортежа | P6 (DTO/UI) | ручная проверка админки, `webvue3` build |
| FR-016 исчерпание попыток видно | P2/P4 (без второго retry) | `TupleFailureTest` |
| FR-017 two-DB sync не ломается | D13 (схема не меняется) | `SyncRegistry`-инвариант тестом, review |
| FR-018 stop/forceStop не ломают resume | P2 (stop/forceStop) | `TupleRecoveryTest`, e2e SC-008 |

**SC**: SC-001/SC-002 — e2e на 8 песнях; SC-003 — `TupleFailureTest` + e2e «искусственно сломанный шаг»;
SC-004 — e2e повторный прогон (0 изменений, 0 новых кортежей); SC-005 — ежедневная проверка после
ремонта (0 находок); SC-006 — замер времени пачки до/после (±20%); SC-007 — админка: причина без логов;
SC-008 — e2e рестарт посреди кортежа.

## Complexity Tracking

Нарушений Constitution Check нет. Осознанные упрощения (альтернативы отклонены в research.md):

| Решение | Почему | Что отклонено |
|---|---|---|
| Цикл шагов внутри потока лейна | не добавляет состояния и гонок; лейн уже сериализован одним потоком | отдельный scheduler-цикл с блокировкой кортежа в БД (новая таблица/колонка, гонки, миграция) |
| `scopeId` из `process_chain_id`, без новой колонки | нулевая миграция, `recordhash` не трогается, FR-017 без схемных изменений | `process_scope_id`/`tuple_state` колонки (миграция + recordhash + guard + две БД) |
| `TupleStore` интерфейс | unit/integration без Postgres — требование приёмки | тесты только через живую БД (нестабильно, на проде запрещено) |

## Риски и открытые вопросы (к лиду/владельцу — не блокируют план)

1. **CPU-retry как отдельный кортеж.** Исходный кортеж завершается `ERROR` (причина — «поставлен
   повтор»), артефакт создаёт retry-кортеж. Это соответствует FR-007/FR-009, но SC-001
   («100% кортежей доходят до конца») следует мерить по артефактам, а не по строкам `ERROR`.
   Нужно подтверждение формулировки приёмки.
2. **SHEETSAGE**: скрипт `~/sheetsage/sheetsage.sh` вызывается с `-o output/output` и может писать
   в каталог относительно cwd, а не `source.wav`. Изоляция по `scopeId` требует проверки на месте
   (не на проде): при реализации — прогон на копии и, при необходимости, запуск с `cwd` =
   scope-каталог.
3. **Tuple-retry из админки** сбрасывает приоритет кортежа к значению головы (если голова утрачена —
   1). Это меняет текущее поведение (сейчас приоритет `-1` остаётся и задание прыгает в начало).
   Считаю правильным, но фиксирую как изменение поведения.
4. **Периодическая проверка целостности**: отчёт отдаётся файлом + эндпоинтом + SSE; отдельная
   страница админки — вне рамок спеки. Если владелец хочет видеть отчёт в UI — отдельная небольшая
   задача (пограничный случай с Out of scope «интерфейс сверх состояния кортежа»), нужно слово.
5. **Разовый ремонт 8 песен** выполняется явным вызовом после `dryRun`; автоматический запуск
   ремонта в план не заложен (FR-014 запрещает молчаливые правки).
6. **Удаление `DONE`-строк**: `purgeFinishedTuples` меняет поведение `deleteDone` (раньше удалялись
   все `DONE`-строки, включая головы живых кортежей). Риск для админских фильтров «DONE» —
   проверить на стенде.
7. **SC-006**: замер «до/после» на проде не производится; мерить на копиях 8 песен, допуск +20%.

## Миграция `tbl_processes` и two-DB sync (FR-017)

**Нужна ли миграция: нет.** Проверено по коду и каноническому SQL:

| Факт | Источник |
|---|---|
| `tbl_processes` не входит в `SyncRegistry.all` — очередь не синхронизируется между LOCAL и SERVER | `sync/SyncTarget.kt:495-522` |
| Канонический `recordhash`-триггер фиксирует «таблица не входит в SyncRegistry» и содержит ровно те колонки модели, которые входят в md5 | `deploy/recordhash_processes.sql:18-41,78-123` |
| `karaoke-app` (и очередь) разворачивается только на admin-машине; на проде его нет | `constitution.md` §«Технологический стек», §«Ограничения и доступы» |
| Новых колонок/статусов нет — `scopeId`, `chainId`, шаг, состояние и причина выводятся из существующих колонок | `data-model.md` §1, §5 |

**Что это значит для FR-017.** «Статусы и поля кортежа переносятся согласованно» выполняется на
уровне наблюдаемого эффекта: через границу двух БД идут только поля песни (`tbl_songs` через
`SongSyncTarget`, 8 флагов `sync_songs_*`), а они пишутся исключительно после проверки содержимого
(FR-013). Сами строки очереди локальны, поэтому «рассинхронизировать» их между базами невозможно.
Никаких DDL/DML к прод-БД план не содержит.

**Если в будущем понадобится хранить состояние кортежа отдельно** (например, `tuple_state`,
`tuple_attempt`, `process_scope_id`), обязательный путь: колонка в `KaraokeProcess` с
`@KaraokeDbTableField` → миграция `deploy/karaoke-db/NN_*.sql` → `deploy/recordhash_processes.sql`
(md5-формула + бэкфилл в конце файла) → `tools/check-recordhash-consistency.py` → применение на обеих
БД (LOCAL и PROD). В этой спеке такой необходимости нет; отклонённая альтернатива — research D13.

## Rollout / rollback

- Схема не меняется → миграций нет, `deploy/recordhash_processes.sql` не применяется.
- Нормализация строк идемпотентна, выполняется при каждом старте воркера.
- Совместимость: новый код понимает старые строки (нормализация), старый код понимает новые
  (головы и шаги уже размечены `chain_id`/`order`; одиночные процессы получают `chain_id = id`,
  что для старого SQL безразлично). Откат — предыдущий образ, без правок БД.
- Выкатка — только по слову владельца и после остановки очереди (Pass 380).

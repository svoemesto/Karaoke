# Implementation Plan: Устранение зависания очереди заданий по лейнам (thread-лейнам)

**Branch**: `029-fix-queue-lane-stall` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/029-fix-queue-lane-stall/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Очередь заданий `KaraokeProcessWorker` периодически перестаёт продвигаться в
отдельных thread-лейнах: после завершения (успешного или с ошибкой) текущего
задания следующее ожидающее задание того же лейна иногда не стартует
автоматически, особенно когда параллельно работают несколько лейнов.
Технический подход (см. `research.md`): (1) устранить гонку данных вокруг
общих (companion-object) полей `KaraokeProcessWorker` — `threadsMap`,
`isWork`, `stopAfterThreadIsDone`, `withoutControl` — которые сейчас читаются
и пишутся без синхронизации из как минимум двух потоков одновременно (поток,
исполняющий бесконечный цикл `doStart()`, и HTTP-поток, вызывающий
`stop()`/`forceStop()`); (2) гарантировать, что задание, чей подпроцесс упал
с необработанным исключением до записи финального статуса, всё равно
переводится в состояние, позволяющее лейну продолжить работу; (3) добавить
новую проверку в существующую подсистему мониторинга (`MonitorCheck`) для
обнаружения зависшего лейна на уровне отдельного `threadId`, с одноклик-
восстановлением — по образцу уже существующего `RenderQueueStalledCheck`,
который сейчас видит только полную остановку воркера, а не зависание
отдельного лейна при работающем воркере.

## Technical Context

**Language/Version**: Kotlin (JDK 17), Spring Boot 3.x — модуль `karaoke-app` (only; фикс не затрагивает `karaoke-web`/фронтенды)

**Primary Dependencies**: существующие `KaraokeProcessWorker`/`KaraokeProcessThread`/`KaraokeProcess` (очередь), `MonitorCheck`/`MonitorRegistry`/`MonitoringService` (мониторинг, `@Scheduled` тик раз в минуту), `SseNotificationService`/`SNS` (broadcast алертов и состояния очереди) — новых внешних зависимостей не требуется

**Storage**: PostgreSQL через сырой JDBC (`tbl_processes`, поля `process_status`, `thread_id` уже существуют) — новых колонок/миграций не предполагается; изменения только в логике чтения/записи существующих полей

**Testing**: В CI автотестов для этой подсистемы нет (Governance/Рабочий процесс constitution.md: «не полагаться на них»); проверка — вручную на dev-pc через сценарии `quickstart.md` (реальная очередь `karaoke-app` в docker), см. допуски `dev-pc`/`dev` в разделе «Ограничения и доступы агента»

**Target Platform**: admin-машина (`karaoke-app` в Docker), Linux; на прод-сервере `karaoke-app` не разворачивается (сервер не затронут этим фиксом)

**Project Type**: точечный бэкенд-фикс в одном модуле (`karaoke-app`), без новых проектов/приложений; фронтенд-изменений не требуется — `MonitorAlertDto`/UI мониторинга уже общий для любых `MonitorCheck`

**Performance Goals**: автостарт следующего задания лейна — в течение нескольких секунд после завершения предыдущего (SC-001); обнаружение зависшего лейна — не позднее порогового времени простоя (по умолчанию ~2 минуты, SC-002)

**Constraints**: НЕЛЬЗЯ нарушать инвариант `redirectErrorStream(true)` (Principle IV); НЕЛЬЗЯ вводить JPA/Hibernate (Principle II) — только сырой JDBC; НЕЛЬЗЯ добавлять внешние SaaS-зависимости в горячий путь обработки (Principle I); новая проверка мониторинга должна переиспользовать существующий цикл `MonitoringService.tick()` (раз в минуту), а не заводить отдельный always-on поток

**Scale/Scope**: единственный экземпляр `karaoke-app` на admin-машине, ~5-6 thread-лейнов (`HEAVY_RENDER=0`, `LIGHT_BACKGROUND=-1`, `REMOTE_STORE_UPLOAD=-2`, `STEM_JOBS=-3` и т.п.), не высоконагруженный сценарий — фикс про корректность/надёжность, не про throughput

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | PASS | Фикс полностью внутри `karaoke-app`, никаких новых внешних SaaS в горячем пути. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | PASS | Любые новые/изменённые запросы к `tbl_processes` — через `KaraokeConnection`/сырой JDBC, без JPA; массовых diff-сравнений между БД в этой фиче нет (очередь не участвует в LOCAL↔SERVER sync). |
| III. Двух-БД синхронизация через SyncRegistry | N/A | `tbl_processes` не входит в `SyncRegistry.all` — очередь заданий локальна для каждого экземпляра `karaoke-app`, синхронизировать нечего. |
| IV. Async-очередь задач с парсингом stdout (NON-NEGOTIABLE) | PASS (целевой принцип фикса) | Это ядро исправляемой подсистемы. `redirectErrorStream(true)` не трогается/не нарушается. `threadId`-лейны и их независимость — явное требование FR-002. `forceStop` не должен оставлять zombie-процессы — учтено в Edge Cases (гонка форс-стопа и восстановления, FR-007). |
| V. Двух-фронтенд: админка и публичный сайт | N/A | Фронтенд не меняется — `MonitorAlertDto`/UI мониторинга уже общие для любого `MonitorCheck`, новых компонентов не требуется. |
| VI. Code Standards (NON-NEGOTIABLE) | PASS (обязательства на этапе реализации) | Новые публичные классы/функции — с KDoc (`@see docs/features/async-process-queue.md` и/или `monitoring.md`); ktlint clean; per-feature документы (`docs/features/async-process-queue.md`, `docs/features/monitoring.md`) обновляются в том же PR (FR-009). |
| VII. Cross-Machine Setup | N/A | Личных AI-конфигов/cross-machine файлов фикс не касается. |
| Ограничения агента (dev-pc/dev) | OK | Работа ведётся на `dev-pc`/`dev` — разрешены локальные пересборка/перезапуск `karaoke-app` и операции с локальной БД без отдельного согласия (см. Governance-исключение); прод не затрагивается. |

Нарушений, требующих секции «Complexity Tracking», не выявлено.

## Project Structure

### Documentation (this feature)

```text
specs/029-fix-queue-lane-stall/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeProcessWorker.kt          # doStart()/KaraokeProcessThread: синхронизация threadsMap/isWork/
│                                     # stopAfterThreadIsDone/withoutControl; гарантия финального статуса
│                                     # задания даже при непредвиденном сбое подпроцесса
├── KaraokeProcess.kt                 # при необходимости: запрос "лейны с WAITING, но без WORKING" для
│                                     # проверки мониторинга (переиспользует существующие паттерны
│                                     # сырого JDBC, без JPA)
├── monitor/
│   ├── checks/
│   │   └── LaneStalledCheck.kt       # НОВЫЙ: per-lane аналог RenderQueueStalledCheck — обнаружение
│   │                                 # зависшего лейна при работающем воркере + resolveAction
│   └── MonitorRegistry.kt            # регистрация LaneStalledCheck (одна строка)
└── (без изменений в контроллерах — существующие HTTP-эндпоинты
     /api/processes/* и /api/monitor/resolve переиспользуются как есть)

docs/features/
├── async-process-queue.md            # обновление по FR-009: новый инвариант надёжного автостарта
└── monitoring.md                     # обновление по FR-009: новая проверка LaneStalledCheck
```

**Structure Decision**: Однопроектная структура — весь фикс локализован в модуле
`karaoke-app` (backend). Новых модулей/приложений не создаётся; фронтенд
(`webvue3`) не меняется, так как UI мониторинга уже общий для всех
`MonitorCheck`. Директория `tests/` не заводится — валидация фичи ручная, по
сценариям `quickstart.md` (см. `Testing` в Technical Context).

## Post-Design Constitution Check

*Ре-проверка после Phase 1 (`data-model.md`, `contracts/`, `quickstart.md`).*

Дизайн не добавил новых зависимостей, модулей, колонок БД или
HTTP-эндпоинтов сверх того, что было оценено в Constitution Check выше —
`LaneStalledCheck` целиком укладывается в существующий паттерн `MonitorCheck`
(Principle IV/VI), схема `tbl_processes` не меняется (Principle II), фронтенд
не затрагивается (Principle V). Повторных нарушений не выявлено, статус
таблицы Constitution Check не меняется.

## Complexity Tracking

> Не заполняется — нарушений Constitution Check, требующих обоснования, нет.

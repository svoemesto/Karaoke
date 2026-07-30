# Implementation Plan: Изоляция соединений с БД по потокам + устойчивость очереди к сетевым сбоям

**Branch**: `087-fix-shared-db-connection` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/087-fix-shared-db-connection/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Инцидент на admin-машине: `KaraokeConnection.getConnection()` кеширует
**один** `java.sql.Connection` на весь `KaraokeConnection`-инстанс
(`@Volatile private var connection`), а `WORKING_DATABASE`
(`Constants.kt:204`) — единственный такой инстанс на всё приложение,
используемый одновременно и потоком очереди заданий
(`KaraokeProcessWorker.doStart()`, в т.ч. очень «горячий» путь —
периодическое сохранение diff'ов прогресса, `KaraokeProcessWorker.kt:938/958/970`),
и всеми HTTP-потоками (43 файла используют `WORKING_DATABASE` напрямую).
PostgreSQL JDBC `Connection` не рассчитан на конкурентное использование из
разных потоков — совпадение по времени двух обращений вызывает протокольный
сбой (`SocketTimeoutException`/«соединение уже закрыто»), что уронило
главный цикл очереди. Технический подход (см. `research.md`): (1) перевести
кеширование соединения в `KaraokeConnection` с одного общего поля на
`ThreadLocal` — по одному физическому соединению на поток, тот же
self-healing (`isClosed`/`isValid(3)`), полностью прозрачно для всех 43
вызывающих мест; (2) обернуть `KaraokeProcessWorker.start()`'s
`Thread { doStart() }` в ограниченный retry с нарастающей паузой вместо
одной попытки — при исчерпании retry сохраняется существующий
safety-net (`isWork=false` + `RenderQueueStalledCheck`).

## Technical Context

**Language/Version**: Kotlin 2.2.20, Spring Boot 3.5.6, JDK 17 — модуль `karaoke-app` (only; фикс не затрагивает `karaoke-web`/фронтенды, см. Assumptions в `spec.md`)

**Primary Dependencies**: `org.postgresql:postgresql:42.7.8` (raw JDBC, `DriverManager.getConnection`) — новых зависимостей не требуется; используется только `java.lang.ThreadLocal` из стандартной библиотеки JDK

**Storage**: PostgreSQL через сырой JDBC — новых таблиц/колонок не предполагается; изменения только в логике управления самими соединениями (in-memory, не персистентно)

**Testing**: В CI автотестов для `karaoke-app` почти нет (`AGENTS.md`: «не полагайся на них»); `KaraokeConnection.getConnection()` требует реального PostgreSQL (нет фейка/мока для `DriverManager`) — узкий unit-тест непрактичен без сетевого стенда. Устойчивость к параллельному доступу и retry-восстановление очереди — ручная проверка по `quickstart.md` на dev-pc (искусственный обрыв сети до `karaoke-db`)

**Target Platform**: admin-машина (`karaoke-app` в Docker), Linux; на прод-сервере `karaoke-app` не разворачивается (не затронут)

**Project Type**: точечный бэкенд-фикс в одном модуле (`karaoke-app`), без новых проектов/приложений; фронтенд не меняется

**Performance Goals**: изоляция по потокам не должна открывать новое соединение на каждый HTTP-запрос — переиспользуемые потоки пула Tomcat должны переиспользовать своё собственное соединение между запросами, как и раньше (SC-003, FR-004)

**Constraints**: НЕЛЬЗЯ менять поведение/контракт вызывающего кода (FR-007) — правка только внутри `KaraokeConnection`; НЕЛЬЗЯ вводить JPA/Hibernate/пул соединений вроде HikariCP (Principle II — только сырой JDBC; кроме того, переход на реальный пул потребовал бы аудита и правки всех 43+ вызывающих мест на explicit close/borrow-return, что выходит за рамки точечного фикса — см. Alternatives в `research.md`); число открытых соединений ограничено числом активных потоков (Tomcat `server.tomcat.threads.max`, по умолчанию 200) — сверить с `max_connections` PostgreSQL, при необходимости задокументировать рекомендацию по настройке

**Scale/Scope**: единственный экземпляр `karaoke-app` на admin-машине; не высоконагруженный сценарий (самостоятельный self-hosted инструмент), но с ОЧЕНЬ частыми обращениями к БД из потока очереди (сохранение diff прогресса на каждой итерации `while(isWork)`) — именно поэтому окно для гонки не редкость, а фоновый постоянный риск

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | PASS | Фикс не вводит внешних SaaS-зависимостей — только `ThreadLocal` из JDK. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | PASS | `KaraokeConnection`/`DriverManager` остаются как есть по сути (сырой JDBC) — меняется только стратегия кеширования соединения, не способ работы с БД. Никакого ORM/пула-фреймворка не вводится. |
| III. Двух-БД синхронизация через SyncRegistry | N/A | Фикс не меняет состав/поведение синхронизируемых таблиц — только транспортный уровень соединения. |
| IV. Async-очередь задач с парсингом stdout (NON-NEGOTIABLE) | PASS (целевой принцип для US2) | `redirectErrorStream(true)` не трогается. Retry-обёртка вокруг `doStart()` не меняет модель thread-лейнов (specs/029) — она оборачивает уже существующий `Thread { doStart() }` в `start()`, не добавляет новых лейнов. |
| V. Двух-фронтенд: админка и публичный сайт | N/A | Фронтенд не меняется. |
| VI. Code Standards (NON-NEGOTIABLE) | PASS (обязательства на этапе реализации) | Изменённые публичные функции (`KaraokeConnection.getConnection`, `KaraokeProcessWorker.start`) — с обновлённым KDoc; ktlint clean; `docs/features/async-process-queue.md` обновляется в том же PR (FR-009, новый инвариант retry). Заодно исправляется устаревший/неверный KDoc класса `Connection` (описывает несуществующее поведение «новое соединение на каждый вызов»). |
| VII. Cross-Machine Setup | N/A | Личных AI-конфигов/cross-machine файлов фикс не касается. |
| Ограничения агента (dev-pc/dev) | OK | Работа ведётся на `dev-pc`/`dev` — разрешены локальные пересборка/перезапуск `karaoke-app` и операции с локальной БД без отдельного согласия. |

Нарушений, требующих секции «Complexity Tracking», не выявлено.

## Project Structure

### Documentation (this feature)

```text
specs/087-fix-shared-db-connection/
├── plan.md               # This file (/speckit.plan command output)
├── research.md           # Phase 0 output (/speckit.plan command)
├── data-model.md          # Phase 1 output (/speckit.plan command)
├── quickstart.md         # Phase 1 output (/speckit.plan command)
├── contracts/            # Phase 1 output (/speckit.plan command) — N/A, см. ниже
└── tasks.md              # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeConnection.kt              # getConnection(): общее @Volatile-поле → ThreadLocal<Connection>,
│                                     # тот же self-healing (isClosed/isValid(3)) внутри ThreadLocal
├── Connection.kt                     # KDoc класса поправить (сейчас неверно описывает «новое
│                                     # соединение на каждый вызов» — это относится к withDb{}-паттерну
│                                     # в части контроллеров, не к WORKING_DATABASE)
└── KaraokeProcessWorker.kt           # start(): Thread { doStart() } обернуть в ограниченный retry
                                     # с нарастающей паузой (Кандидат B, research.md); при исчерпании
                                     # попыток — тот же существующий safety-net (isWork=false,
                                     # sendStateMessage(), RenderQueueStalledCheck подхватывает)

docs/features/
└── async-process-queue.md            # обновление по FR-009: новый инвариант — соединение с БД
                                     # изолировано по потоку, главный цикл очереди пытается
                                     # самовосстановиться ограниченное число раз перед остановкой
```

**Structure Decision**: Однопроектная структура — весь фикс локализован в
модуле `karaoke-app` (backend), три файла. Новых модулей/эндпоинтов/колонок
БД не создаётся; фронтенд не меняется. `contracts/` не создаётся — фикс не
вводит и не меняет ни одного HTTP-эндпоинта/внешнего интерфейса (см.
`Project Type` в Technical Context).

## Post-Design Constitution Check

*Ре-проверка после Phase 1 (`data-model.md`, `quickstart.md`).*

Дизайн не добавил новых зависимостей, модулей, HTTP-эндпоинтов или
персистентных сущностей сверх того, что было оценено в Constitution Check
выше — `ThreadLocal` и bounded-retry реализуются в рамках уже существующих
классов (`KaraokeConnection`, `KaraokeProcessWorker`) по уже принятым в
проекте паттернам. Повторных нарушений не выявлено, статус таблицы
Constitution Check не меняется.

## Complexity Tracking

> Не заполняется — нарушений Constitution Check, требующих обоснования, нет.

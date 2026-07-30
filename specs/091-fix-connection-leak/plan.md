# Implementation Plan: Устранить утечку соединений с БД от одноразовых потоков очереди

**Branch**: `091-fix-connection-leak` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/091-fix-connection-leak/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Активный инцидент на admin-машине: `FATAL: sorry, too many clients already`.
Причина — прямая регрессия от specs/087-fix-shared-db-connection.
`KaraokeConnection.getConnection()` теперь кеширует по одному физическому
`java.sql.Connection` **на поток** (`ThreadLocal`) и никогда не закрывает
его явно — это верно для переиспользуемых потоков (пул Tomcat, единственный
долгоживущий поток `KaraokeProcessWorker.doStart()`), но `KaraokeProcessThread`
(`KaraokeProcessWorker.kt:62`, `class KaraokeProcessThread(...) : Thread()`)
создаётся **заново на каждое задание очереди**
(`threadsMap[threadId] = KaraokeProcessThread(karaokeProcess); .start()`,
`doStart()`) и никогда не переиспользуется — классический одноразовый
поток. Каждый такой поток обращается к БД минимум дважды
(`karaokeProcess.save()` при старте/завершении, `KaraokeProcess.getCountWaiting()`
при обновлении счётчика на бейдже — `KaraokeProcessWorker.kt:204`), получает
своё физическое соединение через `ThreadLocal` и никогда его не освобождает
— поток умирает, а открытое соединение остаётся висеть на стороне
PostgreSQL навсегда. При тысячах обработанных заданий это неизбежно
исчерпывает `max_connections`.

Технический подход (см. `research.md`): добавить в `KaraokeConnection`
метод `closeThreadConnection()` — явно закрывает и удаляет из `ThreadLocal`
соединение **вызывающего потока** (не трогает соединения других потоков,
так как `ThreadLocal` изолирован per-thread по конструкции). Вызвать его
ровно один раз, гарантированно (в `finally`), в конце
`KaraokeProcessThread.run()` — единственном известном сегодня одноразовом
потоке, использующем `KaraokeConnection`. Долгоживущие потоки (Tomcat,
`doStart()`) этот метод не вызывают — их кеш соединения продолжает
работать как в specs/087, без изменений.

## Technical Context

**Language/Version**: Kotlin 2.2.20, Spring Boot 3.5.6, JDK 17 — модуль `karaoke-app` (only)

**Primary Dependencies**: без новых зависимостей — используется существующий `java.sql.Connection.close()`

**Storage**: PostgreSQL через сырой JDBC — без изменений схемы; правка только в управлении жизненным циклом физических соединений

**Testing**: В CI автотестов для `karaoke-app` почти нет; поведение зависит от реального PostgreSQL (число живых соединений видно только через `pg_stat_activity`) — верификация ручная, по `quickstart.md` на dev-pc (прогнать много заданий через очередь, следить за `pg_stat_activity`)

**Target Platform**: admin-машина (`karaoke-app` в Docker), Linux; на прод-сервере `karaoke-app` не разворачивается — но именно на admin-машине уже произошёл активный инцидент

**Project Type**: точечный бэкенд-фикс в одном модуле (`karaoke-app`), 2 файла (`KaraokeConnection.kt`, `KaraokeProcessWorker.kt`); фронтенд и HTTP-контракт не меняются

**Performance Goals**: не применимо — фикс не меняет частоту/объём запросов к БД, только момент освобождения физического соединения

**Constraints**: НЕЛЬЗЯ вызывать `closeThreadConnection()` из переиспользуемых потоков (Tomcat, `doStart()`) — это отменило бы весь смысл specs/087 (пришлось бы открывать новое соединение на каждое обращение); НЕЛЬЗЯ закрывать соединение ДО завершения всей полезной работы потока — `run()` обращается к БД (`.save()`) в нескольких разных ветках (успех/ошибка/форс-стоп), закрытие должно происходить строго один раз, в самом конце, после всех этих веток; НЕЛЬЗЯ оставлять закрытие негарантированным — оно обязано сработать даже при необработанном исключении внутри `run()`, иначе часть утечки останется неустранённой

**Scale/Scope**: единственный экземпляр `karaoke-app` на admin-машине; очередь на практике обрабатывает тысячи заданий подряд без перезапуска приложения — именно поэтому утечка «в одно соединение за раз» стала заметной проблемой

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | PASS | Фикс не вводит внешних зависимостей. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | PASS | Используется штатный `java.sql.Connection.close()` — сырой JDBC, без ORM/пула. |
| III. Двух-БД синхронизация через SyncRegistry | N/A | Не затрагивается. |
| IV. Async-очередь задач с парсингом stdout (NON-NEGOTIABLE) | PASS (целевой принцип) | Правка — внутри `KaraokeProcessThread.run()`, уже описанного инвариантами specs/029 (`try/catch` вокруг всего run() для гарантированного терминального статуса) — новый `finally` для закрытия соединения логически расширяет тот же принцип «гарантированная очистка по завершении потока задания», не нарушая его. |
| V. Двух-фронтенд: админка и публичный сайт | N/A | Не затрагивается. |
| VI. Code Standards (NON-NEGOTIABLE) | PASS (обязательства на этапе реализации) | Новый публичный метод `closeThreadConnection()` — с KDoc, явно предупреждающим об области применения (только одноразовые потоки); ktlint clean; `docs/features/async-process-queue.md` обновляется в том же PR (FR-009). |
| VII. Cross-Machine Setup | N/A | Не затрагивается. |
| Ограничения агента (dev-pc/dev) | OK | Работа на `dev-pc`/`dev` — локальные пересборка/перезапуск `karaoke-app` и операции с локальной БД разрешены без отдельного согласия. |

Нарушений, требующих секции «Complexity Tracking», не выявлено.

## Project Structure

### Documentation (this feature)

```text
specs/091-fix-connection-leak/
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
├── KaraokeConnection.kt               # + closeThreadConnection(): закрывает и удаляет из
│                                      # ThreadLocal соединение ВЫЗЫВАЮЩЕГО потока; KDoc явно
│                                      # предупреждает не вызывать из переиспользуемых потоков
└── KaraokeProcessWorker.kt            # KaraokeProcessThread.run() (~строки 74-376): обернуть
                                      # тело внутри `if (karaokeProcess != null) { ... }` в
                                      # try/finally, вызвать karaokeProcess.database
                                      # .closeThreadConnection() в finally - гарантированно,
                                      # один раз, в самом конце, после всех веток (успех/
                                      # ошибка/форс-стоп)

docs/features/
└── async-process-queue.md             # обновление по FR-009: новый инвариант - одноразовые
                                      # потоки очереди обязаны освобождать своё соединение по
                                      # завершении
```

**Structure Decision**: Однопроектная структура, весь фикс — 2 файла в
модуле `karaoke-app` (backend). Новых модулей/эндпоинтов/колонок БД не
создаётся; фронтенд не меняется. `contracts/` не создаётся — фикс не
вводит и не меняет ни одного HTTP-эндпоинта/внешнего интерфейса.

## Post-Design Constitution Check

*Ре-проверка после Phase 1 (`data-model.md`, `quickstart.md`).*

Дизайн не добавил новых зависимостей, модулей, HTTP-эндпоинтов сверх того,
что было оценено в Constitution Check выше — `closeThreadConnection()`
реализуется в рамках уже существующего класса `KaraokeConnection`,
переиспользует штатный `java.sql.Connection.close()`. Повторных нарушений
не выявлено, статус таблицы Constitution Check не меняется.

## Complexity Tracking

> Не заполняется — нарушений Constitution Check, требующих обоснования, нет.

# Implementation Plan: Единообразная обработка сбоев БД в главном цикле очереди

**Branch**: `088-fix-queue-swallowed-errors` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/088-fix-queue-swallowed-errors/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

`KaraokeProcess.getCountWaiting()`/`getProcessesToStart()` (`KaraokeProcess.kt:479-506`,
`:750-814`) ловят `SQLException` внутри себя (и молча возвращают `0`/пустую
карту при `connection == null`), в отличие от `.save()` и `createDbInstance()`
(вставка строки), которые пробрасывают ошибку наружу — что и ловится
retry-механизмом `KaraokeProcessWorker.start()` (specs/087-fix-shared-db-connection).
Из-за этого во время сбоя БД поведение главного цикла очереди
(`doStart()`) непредсказуемо зависит от того, какой конкретно вызов
столкнулся со сбоем первым: `.save()` → падение → retry; `getCountWaiting()`/
`getProcessesToStart()` → тихий простой без единого признака проблемы (живьём
воспроизведено при проверке specs/087 — `docker pause karaoke-db` на 4.5
минуты не уронил `doStart()` ни разу).

Технический подход (см. `research.md`): добавить обеим функциям параметр
`throwOnError: Boolean = false` (сохраняет сегодняшнее поведение по
умолчанию для всех существующих вызывающих мест) — при `true` ошибка
пробрасывается вместо тихого возврата заглушки. Ровно 3 вызова **внутри
`doStart()`** (`KaraokeProcessWorker.kt:955` через приватный
`getKaraokeProcessesToStart()`, `:979`, `:1028`) переводятся на
`throwOnError = true`. Четыре ДРУГИХ существующих вызывающих места
(`KaraokeProcess.kt:744` — уведомление после создания задания;
`KaraokeProcessWorker.kt:204` — уведомление в `KaraokeProcessThread.run()`;
`:536` — уведомление при старте очереди, синхронно на HTTP-потоке, до
`Thread{...}`; `:1090` — уведомление в конце `forceStop()`) остаются на
дефолте `false`,
поведение не меняется ни на бит (FR-004/FR-005). Мониторинг
(`RenderQueueStalledCheck`/`LaneStalledCheck`) тоже не трогается — эти
вызовы используют дефолтный (нетребующий) режим, их поведение при сбое БД
не меняется (они и так уже безопасно обёрнуты в `MonitoringService.tick()`,
это отдельный, не затрагиваемый этой фичей путь).

## Technical Context

**Language/Version**: Kotlin 2.2.20, Spring Boot 3.5.6, JDK 17 — модуль `karaoke-app` (only)

**Primary Dependencies**: без новых зависимостей — правка использует существующие `java.sql.SQLException`/`Statement`/`ResultSet`

**Storage**: PostgreSQL через сырой JDBC — без изменений схемы/запросов, только обработка ошибок вокруг уже существующих SQL-запросов

**Testing**: В CI автотестов для `karaoke-app` почти нет (`AGENTS.md`); обе функции требуют реального PostgreSQL (нет мока для `DriverManager`/`Statement`) — unit-тест на реальном соединении непрактичен в CI. Верификация — ручная, по `quickstart.md` на dev-pc (`docker pause`/`unpause karaoke-db`, аналогично проверке specs/087)

**Target Platform**: admin-машина (`karaoke-app` в Docker), Linux; на прод-сервере `karaoke-app` не разворачивается

**Project Type**: точечный бэкенд-фикс в одном модуле (`karaoke-app`), 2 файла (`KaraokeProcess.kt`, `KaraokeProcessWorker.kt`); фронтенд не меняется, HTTP-контракт не меняется

**Performance Goals**: не применимо — фикс не меняет запросы/частоту обращений к БД, только реакцию на ошибку

**Constraints**: НЕЛЬЗЯ менять поведение по умолчанию (`throwOnError=false`) — четыре существующих вызывающих места (`KaraokeProcess.kt:744`, `KaraokeProcessWorker.kt:204`, `:536`, `:1090`) должны продолжать работать побайтово так же, как сегодня (FR-004/FR-005); НЕЛЬЗЯ вводить новый тип исключения — переиспользовать `java.sql.SQLException`, чтобы retry-механизм `KaraokeProcessWorker.start()` (уже ловит `catch (e: Exception)`) обрабатывал новый источник ошибки без отдельной правки

**Scale/Scope**: единственный экземпляр `karaoke-app` на admin-машине; фикс касается только внутреннего поведения двух функций и 3 конкретных вызовов внутри `doStart()`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | PASS | Фикс не вводит внешних зависимостей. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | PASS | Правка — только реакция на `SQLException` вокруг уже существующих `Statement`/`ResultSet`-запросов; сами запросы и способ доступа к БД не меняются. |
| III. Двух-БД синхронизация через SyncRegistry | N/A | `tbl_processes` не входит в `SyncRegistry.all`. |
| IV. Async-очередь задач с парсингом stdout (NON-NEGOTIABLE) | PASS (целевой принцип) | Фикс напрямую усиливает уже существующий инвариант «сбой БД в `doStart()` даёт шанс retry-механизму» (specs/087) — распространяет его на все внутренние DB-вызовы цикла, а не только на `.save()`. `redirectErrorStream`/thread-лейны не затрагиваются. |
| V. Двух-фронтенд: админка и публичный сайт | N/A | Фронтенд не меняется. |
| VI. Code Standards (NON-NEGOTIABLE) | PASS (обязательства на этапе реализации) | Изменённые публичные функции — с обновлённым KDoc (новый параметр `throwOnError`); ktlint clean; `docs/features/async-process-queue.md` обновляется в том же PR (FR-009) — заменить/уточнить «Известную ловушку», добавленную specs/087. |
| VII. Cross-Machine Setup | N/A | Не затрагивается. |
| Ограничения агента (dev-pc/dev) | OK | Работа на `dev-pc`/`dev` — локальные пересборка/перезапуск `karaoke-app` и операции с локальной БД разрешены без отдельного согласия. |

Нарушений, требующих секции «Complexity Tracking», не выявлено.

## Project Structure

### Documentation (this feature)

```text
specs/088-fix-queue-swallowed-errors/
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
├── KaraokeProcess.kt                 # getCountWaiting()/getProcessesToStart(): добавить параметр
│                                     # throwOnError: Boolean = false (дефолт сохраняет сегодняшнее
│                                     # поведение); при true - пробрасывать SQLException вместо
│                                     # тихого возврата заглушки (0L / emptyMap())
└── KaraokeProcessWorker.kt           # getKaraokeProcessesToStart() (приватный wrapper, ~строка 648)
                                     # и два прямых вызова getCountWaiting() внутри doStart()
                                     # (~строки 979, 1028) - передать throwOnError = true.
                                     # Четыре ДРУГИХ вызывающих места (KaraokeProcess.kt:744,
                                     # KaraokeProcessWorker.kt:204, :536 и :1090) НЕ трогать -
                                     # остаются на дефолте false

docs/features/
└── async-process-queue.md            # обновление по FR-009: заменить «Известную ловушку» про
                                     # непоследовательную обработку (добавленную specs/087) на
                                     # описание устранённого инварианта
```

**Structure Decision**: Однопроектная структура, весь фикс — 2 файла в
модуле `karaoke-app` (backend). Новых модулей/эндпоинтов/колонок БД не
создаётся; фронтенд не меняется. `contracts/` не создаётся — фикс не вводит
и не меняет ни одного HTTP-эндпоинта/внешнего интерфейса.

## Post-Design Constitution Check

*Ре-проверка после Phase 1 (`data-model.md`, `quickstart.md`).*

Дизайн не добавил новых зависимостей, модулей, HTTP-эндпоинтов или типов
исключений сверх того, что было оценено в Constitution Check выше —
`throwOnError`-параметр с дефолтом `false` реализуется в рамках уже
существующих функций, переиспользует `java.sql.SQLException`. Повторных
нарушений не выявлено, статус таблицы Constitution Check не меняется.

## Complexity Tracking

> Не заполняется — нарушений Constitution Check, требующих обоснования, нет.

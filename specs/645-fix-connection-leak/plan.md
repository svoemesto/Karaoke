# Implementation Plan: Устранение утечки соединений с БД (BR-627)

**Branch**: `648-fix-connection-leak-impl` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/645-fix-connection-leak/spec.md` (OpenProject #410, аудит #336, BR-627)

**Note**: ветка реализации — `648-fix-connection-leak-impl` (worktree `.worktrees/br627`).
Спека `645-fix-connection-leak` — контракт; FR-001…FR-012 и SC-001…SC-005 не менялись.

## Summary

Утечка физических JDBC-соединений: прикладной код открывает одно физическое
соединение на поток (`KaraokeConnection` кэширует его в `ThreadLocal`) и не
закрывает его, когда поток умирает. Канал к PostgreSQL остаётся жить до
недетерминированной финализации сборщиком мусора; при `max_connections` это
отказ БД для всего приложения (`sorry, too many clients already` → очередь
`KaraokeProcess` встаёт → сайт отдаёт 5xx).

**Технический подход** (три слоя, все — точечные, без перехода на пул соединений):

1. **Код**: 4 оставшихся сырых фоновых потока (`kotlin.concurrent.thread`) в
   прикладном коде переводятся на канонический `startOneShotThread(database, name) { … }`,
   который закрывает thread-local соединение потока в `finally` при любом исходе
   (успех/исключение). Канонический механизм **не изобретается заново** — он
   дорабатывается одним аддитивным параметром: поток, который ходит в ДВЕ БД
   (LOCAL + SERVER), должен закрывать оба соединения, а не только переданное.
2. **Проверка**: регрессионная проверка `OneShotThreadWiringTest` перестаёт быть
   привязанной к фиксированному перечню файлов — она **обнаруживает все файлы
   прикладного кода, обращающиеся к БД**, и требует отсутствия в них сырых
   фоновых потоков. Негативная фикстура «до/после» доказывает, что проверка
   действительно падает на внесённом нарушении и не считает нарушением сырой
   поток в файле без обращений к БД.
3. **Документация (SSoT)**: правило закрытия по типам потока, признанное
   ограничение «третьего типа», компенсирующая мера (`max_connections=300`),
   признак исчерпания и способ проверки числа соединений, а также регламент
   запуска тестов (тестовые потоки тоже оставляют соединения) — в одном месте,
   без поиска по нескольким документам.

**Вне объёма (FR-010)**: переход на ограниченный пул соединений (HikariCP) —
отдельное решение со своими замерами; выбор владельца по Q1 зафиксирован как
вариант A (точечный фикс).

## Technical Context

**Language/Version**: Kotlin 2.x (JDK 17 target), Gradle multi-module

**Primary Dependencies**: Spring Boot 3.5.x, `pgjdbc` (сырой JDBC, без ORM),
SLF4J/Logback, JUnit 5

**Storage**: PostgreSQL 16 (сырой JDBC, `KaraokeConnection` = ThreadLocal-кеш
одного физического соединения на поток), MinIO (S3), Redis не используется

**Testing**: JUnit 5 (`./gradlew :karaoke-app:test`), backend-тесты требуют
`env -u DISPLAY`; тесты идут против локальной БД

**Target Platform**: Linux (admin-машина `nsa-i9`, прод-хост), Docker

**Project Type**: web-service (Spring Boot) + два SPA (Vue 3)

**Performance Goals**: не менять поведение пользователей; время отклика
фоновых операций — без изменений

**Constraints**:

- FR-010: НЕ переводить работу с БД на ограниченный пул соединений;
- FR-011: НЕ менять публичные HTTP-контракты, НЕ требовать миграции данных;
- ADR local-0009: `closeThreadConnection()` запрещён в переиспользуемых потоках
  (пул Tomcat, `@Scheduled`) и обязателен в одноразовых;
- Constitution II: только сырой JDBC (без JPA/Hibernate) — не нарушается;
- Hard-gates проекта: `GRADLE_USER_HOME=<корень репозитория>/.gradle`,
  `DOCKER_CONFIG=<корень репозитория>/.docker`, `--max-workers=2`,
  `-Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"`,
  `-Djava.awt.headless=true`.

**Scale/Scope**: 4 места в прикладном коде; проверка покрывает ~160 файлов
прикладного кода, обращающихся к БД (из ~420 `.kt` в `karaoke-app` + `karaoke-web`)

## Constitution Check

| Principle | Статус | Обоснование |
|---|---|---|
| I. Self-contained автопайплайн | N/A | Пайплайн не затрагивается |
| II. Сырой JDBC + дифф по хэшам | PASS | Новых способов доступа к БД не появляется; `startOneShotThread` работает поверх существующего `KaraokeConnection` |
| III. Двух-БД синхронизация через SyncRegistry | PASS | Поток sync-related в `SongEditorController` ходит и в LOCAL, и в SERVER — теперь закрываются оба соединения (аддитивный параметр `alsoClose`), т.е. синхронизация не меняется, а её побочный эффект (канал в БД) устраняется |
| IV. Async-очередь заданий | PASS | Поток главного цикла очереди уже идёт через `startOneShotThread`; дорабатывается только закрытие соединения к SERVER (тот же класс утечки) |
| V. Двух-фронтенд | N/A | Фронтенды не затрагиваются |
| VI. Code Standards | PASS | KDoc на публичный API (`startOneShotThread`), per-feature документ `docs/features/fix-connection-leak.md` (FR-009 Constitution), ktlint — в прогоне |
| VII. Cross-Machine Setup | N/A | Не затрагивается |
| VIII. Секреты и git-гигиена | PASS | Секретов нет; `.env.local-tracker` — в `.gitignore` (проверено `git check-ignore`) |
| IX. Knowledge-first | PASS | Pre-flight выполнен: `knowledge/README.md` + `knowledge/domains/README.md`, 4 grep-запроса по `knowledge/`, `knowledge/domains/persistence/domain.md` + ADR `local-0009`/`0001`/`0002`/`0005`, `knowledge/domains/processing/components/async-process-queue.md`, `knowledge/system/infra/deploy-overview.md`, `knowledge/guidelines/architecture-conventions.md` — до обращения к `src/` |
| IX.3. Subagent isolation | PASS | Реализация ведётся в отдельном worktree `.worktrees/br627`, ветка `648-fix-connection-leak-impl` |

**Gate**: PASS — нарушений, требующих обоснования, нет.

## Project Structure

### Documentation (this feature)

```text
specs/645-fix-connection-leak/
├── spec.md              # контракт (не меняется)
├── spec-summary.md      # сводка для владельца
├── plan.md              # этот файл
├── research.md          # Phase 0: технические решения (D-1..D-8)
├── data-model.md        # Phase 1: сущности/инварианты проверки
├── quickstart.md        # Phase 1: как проверить фичу
├── contracts/
│   └── connection-lifecycle.md   # контракт жизненного цикла соединения потока
├── tasks.md             # Phase 2: задачи (создаётся /speckit.tasks)
└── report.md            # отчёт (обязательный артефакт)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── ExportAlignmentDataset.kt                  # T-01: сырой thread { → startOneShotThread
├── StorageCacheWarm.kt                        # T-02: сырой thread { → startOneShotThread
├── CacheEtagSizeBackfill.kt                   # T-03: сырой thread { → startOneShotThread
├── controllers/SongEditorController.kt         # T-04: сырой thread { → startOneShotThread (+ SERVER)
├── services/OneShotThread.kt                  # T-05: аддитивный параметр alsoClose
└── KaraokeProcessWorker.kt                    # T-05b: закрытие соединения к SERVER в потоке очереди

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
├── OneShotThreadWiringTest.kt                 # T-06: динамический охват + негативная фикстура
├── RawThreadDbGuard.kt                        # T-06a: детектор (в тестовых исходниках)
└── OneShotThreadTest.kt                       # FR-005: поведенческий тест (уже есть — расширяется)

docs/features/fix-connection-leak.md            # T-08: per-feature документ (Constitution VI)

knowledge/domains/persistence/domain.md        # T-09: SSoT контракта соединения
knowledge/system/infra/deploy-overview.md     # T-10: признак + проверка числа соединений
knowledge/system/infra/ci-tools.md            # T-11: регламент запуска тестов
knowledge/adr/local-0009-thread-local-connection-lifecycle.md  # T-12: поправка ADR
```

**Structure Decision**: модульная структура проекта не меняется. Правки
прикладного кода — точечные, в существующих файлах; проверка живёт в
`karaoke-app/src/test` рядом с уже существующей `OneShotThreadWiringTest`,
потому что спека FR-003 требует «автоматическую проверку», а лид указал
именно на этот тест как на точку расширения. Отдельный shell-guard в `tools/`
не заводится (см. research D-5).

## Phase 0 — Research

См. [research.md](research.md): D-1 (объём фикса), D-2 (две БД у одного потока),
D-3 (динамическое обнаружение DB-файлов), D-4 (что считать «сырым потоком»),
D-5 (где живёт проверка), D-6 (негативная фикстура), D-7 (регламент тестов),
D-8 (SSoT-раскладка).

## Phase 1 — Design

- Модель сущностей и инвариантов проверки — [data-model.md](data-model.md).
- Контракт жизненного цикла соединения — [contracts/connection-lifecycle.md](contracts/connection-lifecycle.md).
- Сценарии приёмки и команды прогона — [quickstart.md](quickstart.md).

### Re-evaluated Constitution Check (после дизайна)

| Principle | Статус | Изменения дизайна |
|---|---|---|
| II | PASS | Аддитивный параметр `alsoClose` не меняет способ доступа к БД |
| III | PASS | `alsoClose` закрывает соединение к SERVER в потоке sync-related — синхронизация не меняется |
| VI | PASS | Появляется per-feature документ; KDoc на `startOneShotThread` дополняется |
| IX | PASS | SSoT правится в том же PR (FR-012) |

**Gate**: PASS.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|------------------------------------|
| Аддитивный параметр `alsoClose: List<KaraokeConnection>` в `startOneShotThread` | Поток sync-related (`SongEditorController`) и поток очереди (`KaraokeProcessWorker`) ходят в ДВЕ БД (LOCAL + SERVER). У каждого `KaraokeConnection` — свой `ThreadLocal`, поэтому закрытия только переданного экземпляра недостаточно: соединение к SERVER осталось бы висеть — ровно та же утечка | Ручной `finally { Connection.remote().closeThreadConnection() }` внутри тела: закрытие перестаёт быть гарантией канонического механизма и может быть забыто (это тот же класс дефекта, который чинится) |
| Динамическое обнаружение файлов, обращающихся к БД (эвристика по маркерам) | FR-003 требует охвата всех таких файлов, а не фиксированного перечня из двух | Фиксированный перечень — исходный дефект (BR-060/BR-627 проскочили именно из-за него) |

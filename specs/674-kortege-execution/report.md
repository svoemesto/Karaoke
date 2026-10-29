# Отчёт 674-kortege-execution

**Issue**: KARAOKE-318 (Plane) · **Ветка**: 674-kortege-execution · **Дата**: 2026-10-29
**Спека**: `specs/674-kortege-execution/spec.md` (не менялась)

## Что сделано

- **Foundational**: `TupleSchedulerSql` (выбор кортежа и первого исполняемого шага, `started DESC`,
  ретро-guard «нет более раннего не-DONE»), `TupleStore`/`JdbcTupleStore`, `normalizeTuples`
  (`CREATING/WORKING→WAITING`, chainless→self, безголовые→голова, ERROR-кортеж гасит WAITING),
  `purgeFinishedTuples` (имя `deleteDone` сохранено), `createChain`/`markAsHead`, single-flight по кортежу.
- **Исполнение**: `TupleExecutor` в потоке лейна (шаги 0..N-1 подряд), `runStep` остался в
  `KaraokeProcessWorker.kt` (exit-code guard, `redirectErrorStream(true)`), пост-хуки — один раз
  на кортеж (`runPostHooks`), `finally { closeThreadConnection() }`.
- **Изоляция**: `scopeId = chain_id ?: id`, каталоги `.../tmp/<scopeId>/`, контейнеры
  `demucs-<scopeId>`/`keybpmfinder-<scopeId>`, SHEETSAGE cwd в scope; уборка владельцем +
  `TupleJanitor` (TTL, живые не трогает); kill контейнеров по префиксам.
- **Содержимое**: проверка стемов по длительности (±0.5 с) и свежести, `.rejected-*`,
  CPU-retry отдельным самодостаточным кортежем; sidecar `<file> [key].json.meta`,
  `legacyNoSidecar` отделён от `sidecarMismatch`; `SidecarBackfillService` (dryRun) +
  `POST /api/admin/artifacts/backfill-sidecar`.
- **Ремонт/отчёт**: `ArtifactIntegrity.verifySong`, `ArtifactIntegrityScheduler` (report-only,
  guard `queue.integrity.enabled=false`), `SongArtifactRepairService`, `ArtifactAdminController`
  (`/repair`, `/integrity`).
- **Админка**: `topLevelOnly` с головой кортежа, DTO (`chainStepIndex/StepsTotal/State/FailedStep/
  Reason/progress`), tuple-retry через `inTransaction`, колонка «Кортеж» в `webvue3`.
- **Документация/SSoT**: `docs/features/kortege-execution.md`, разделы в `async-process-queue.md`,
  `domain.md`, `key-bpm-from-file.md`, `process-admin.md`, `dictionaries.md`, `architecture-notes.md`.

## Тесты

- `:karaoke-app:test` — полный прогон зелёный (включая `ProcessExitWiringTest`,
  `OneShotThreadWiringTest`/`OneShotThreadTest`, `PkgS322ProcessTest`,
  `PkgS323Batch4SourceContractsTest`, `PkgS322SecurityTest`).
- Новые unit: `TupleExecutorTest`, `TupleScopeTest`, `TupleSchedulerSqlTest`, `TupleJanitorTest`,
  `KeyBpmSidecarTest`, `TupleThreadLifecycleTest`, `AdminTupleContractTest`,
  `ArtifactIntegritySchedulerTest`, `SyncTargetContractTest`, `ArtifactVerificationTest`,
  `ArtifactIntegrityTest`, `SingleFlightTest`, `TupleRecoveryTest`, `TuplePostHooksTest`,
  `AdminDoneFilterTest`.
- `:karaoke-app:ktlintCheck`, `:karaoke-app:bootJar`, `:karaoke-web:compileKotlin` — зелёные.
- `webvue3`: `npm run lint`, `npm run build`, `npm run format:check` — зелёные.
- `tools/check-ssot-impact.py` — 0 нарушений.

## Не выполнено (нужен стенд/образы)

- T013/T024 — `TupleIsolationIT` и прогон: нужен stub-docker/стенд с копиями.
- T018 — SHEETSAGE-спайк: скрипта `~/sheetsage/sheetsage.sh` и образа нет.
- T046/T047/T062/T063 — ремонт и e2e на 8 песнях, замер SC-006: нужен стенд с копиями и отдельной БД.
- T054 — ручная проверка админки в браузере (UI собран, но стенд не поднимался).

## Отклонения

- `PkgS325Batch2SourceContractsTest` (BR-260) обновлён под `createChain` (инварианты сохранены).
- `ArtifactIntegrityScheduler`: cron через Spring-свойство `karaoke.integrity.cron`, включение —
  `queue.integrity.enabled`; скан — песни с DONE-процессами DEMUCS/KEY_BPM.
- `SidecarBackfillService` — дополнительный эндпоинт сверх исходного tasks.md (следствие риска
  легаси без отпечатка), спека не менялась.
- `deleteDone` сохранён по имени (семантика purge).

## Статус

Закрыто 57/65 задач; остаток требует стенда/образов. Прод не затрагивался, push не делался.

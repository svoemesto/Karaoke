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

Пошаговый список для стендового прогона — `specs/674-kortege-execution/stand-checklist.md`.

## Отклонения

- `PkgS325Batch2SourceContractsTest` (BR-260) обновлён под `createChain` (инварианты сохранены).
- `ArtifactIntegrityScheduler`: включение — `queue.integrity.enabled`, расписание — Spring-свойство
  `karaoke.integrity.cron` (единственный ключ cron; `queue.integrity.cron` из KaraokeProperties удалён);
  скан — песни с DONE-процессами DEMUCS/KEY_BPM.
- `SidecarBackfillService` — дополнительный эндпоинт сверх исходного tasks.md (следствие риска
  легаси без отпечатка), спека не менялась.
- `deleteDone` сохранён по имени (семантика purge).

## Статус

Закрыто 57/65 задач; остаток требует стенда/образов. Прод не затрагивался, push не делался.

## Дополнение 2026-11-03: повтор без GPU не срабатывал (KARAOKE-391)

### Симптом

Песня 30428 «Песня друзей» (Король и Шут): при импорте из папки не определилась
длительность (`song_ms=0`), демукс не прошёл, и починка HR упиралась в тот же сбой.

### Разбор

| Уровень | Что было |
|---|---|
| Железо | GPU занят локальной LLM (LM Studio `llama-server`, 10.3 ГБ) + `api_server` (1.15 ГБ) → свободно ~104 МиБ |
| demucs | полный файл песни на `cuda` → `torch.cuda.OutOfMemoryError` (воспроизведено вручную; 6-секундный клип проходит, полный — нет) |
| Контейнер | `demucs2` не проверяет код возврата `python3 -m demucs` → контейнер выходит с кодом **0**, сбой проглочен |
| Цепочка | шаги `mv`/`chmod` стояли ПОСЛЕ `docker run` → на первом же `mv` цепочка рвалась («No such file or directory») |
| Финальный шаг | `finalizeDemucs` (ставит повтор без GPU по FR-009 и пишет `song_ms`) не доходил **никогда** |

Последний пункт — суть: спека требовала «ровно один автоматический повтор без GPU», но
механизм повтора жил в недостижимом шаге. Поэтому «GPU занят → надо на CPU» не
срабатывало ни разу, длительность не определялась, починка HR повторяла тот же путь.

Доказательства: лог шага `/sm-karaoke/system/logs/[2026-11-03 22-17-24…] … - Демукс 2.log`
(содержит `torch.cuda.OutOfMemoryError`); процессы песни 1652686–1652717 в `tbl_processes`
(цепочка DEMUCS2 умерла на шаге `mv`, `finalizeDemucs` не выполнялся).

### Исправление

- `tuple/DemucsStems.move` — перенос `file-<stem>.flac` из папки кортежа в целевые файлы
  (права 666) с отчётом о недостающих стемах.
- `Song.argsDemucs2/5`, `argsDemucs2RetryCpu/5RetryCpu` — шаги `mv`/`chmod` убраны: между
  `docker run` и `finalizeDemucs` шагов, зависящих от наличия файлов результата, больше нет.
- `Utils.executeFinalizeDemucs` — сначала перенос, потом проверка содержимого; при
  недостающих стемах — прежний повтор без GPU и явная причина в описании задания.
- `KaraokeProcessWorker` — причина исключения функционального шага и хвост вывода шага
  `docker` попадают в `process_description` (оператор видит их в админке).

Инвариант «в цепочке демукса нет шагов `mv`/`chmod`» закреплён тестом
`DemucsChainShapeTest`; поведение переноса — `DemucsStemsTest`; диагностика —
`KaraokeProcessWorkerOutputTailTest`.

### Проверки

```
:karaoke-app:compileKotlin          → BUILD SUCCESSFUL
:karaoke-app:ktlintCheck             → BUILD SUCCESSFUL
:karaoke-app:test --tests *Demucs*  → BUILD SUCCESSFUL (6 тестов)
:karaoke-app:test --tests *OutputTail* → BUILD SUCCESSFUL (3 теста)
lint-knowledge / check-doc-references → OK
```

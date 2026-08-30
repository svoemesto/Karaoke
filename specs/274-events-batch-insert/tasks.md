---
description: "Task list для 274-events-batch-insert — EventsBuffer для batch INSERT (FR-109)"
---

# Tasks: Batch INSERT для tbl_events (FR-109)

**Input**: Design documents from `/specs/274-events-batch-insert/`
- plan.md (required)
- spec.md (required for user stories)

## Phase 1: Setup

- [x] T001 Создать спеку (spec.md) с FR-001..FR-007, Clarifications, Success Criteria
- [x] T002 Создать plan.md с Implementation Steps + Risks + Constitution Check
- [x] T003 Создать checklists/requirements.md
- [x] T004 Создать tasks.md (этот файл)

## Phase 2: Foundational

- [ ] T005 Создать feature-ветку `274-events-batch-insert` от master
- [ ] T006 Создать `EventsBuffer.kt` `@Service`:
  - [ ] T006a `data class EventRecord` (immutable)
  - [ ] T006b `companion object` с константами (MAX_BUFFER_SIZE=500, FLUSH_INTERVAL_MS=5000)
  - [ ] T006c `buffer: ConcurrentLinkedQueue<EventRecord>` + `flushing: AtomicBoolean`
  - [ ] T006d `enqueue(record)` — добавляет или делает sync INSERT (kill-switch)
  - [ ] T006e `@Scheduled flush()` — drain буфера, batch INSERT
  - [ ] T006f `executeBatch(batch)` + `executeSingle(record)` — JDBC helpers
  - [ ] T006g `buildInsertSql(record)` — копия логики из MainController
  - [ ] T006h `isEnabled()` через `KaraokeProperties.getBoolean` (default false)
  - [ ] T006i KDoc 100% на все public/protected элементы
- [ ] T007 Изменить `MainController.kt:141-173` (`insertEvent`):
  - [ ] T007a Добавить `@Autowired lateinit var eventsBuffer: EventsBuffer`
  - [ ] T007b Заменить прямой `ps.executeUpdate()` на `eventsBuffer.enqueue(EventRecord(...))`
  - [ ] T007c Добавить KDoc на `insertEvent` со ссылкой на FR-109

## Phase 3: Polish

- [ ] T008 Создать LiveDoc `livedocs/features/274-events-batch-insert.md`
- [ ] T009 Проверить все 7 CI gates:
  - [ ] `./gradlew :karaoke-web:compileKotlin --parallel`
  - [ ] `./gradlew :karaoke-web:ktlintCheck`
  - [ ] `bash tools/check-kdoc-coverage.sh`
  - [ ] `cd webvue3 && npm run lint:check` (sanity check)
  - [ ] `pre-commit run --all-files`
- [ ] T010 Создать PR через `gh pr create --base master`
- [ ] T011 Дождаться `gh pr checks` (CI 7/7 PASS)
- [ ] T012 Merge в master
- [ ] T013 Обновить parent спеку 241:
  - [ ] `specs/241-db-storage-perf-audit/tasks.md` — T012.5 → `[x]`
  - [ ] `livedocs/architecture-notes.md` §Pass 241 — отметить FR-109 как done

## Definition of Done

- [ ] spec.md содержит FR-001..FR-007 + Success Criteria + Clarifications
- [ ] plan.md содержит Implementation Steps + Risks + Constitution Check
- [ ] `EventsBuffer.kt` создан с KDoc 100% (FR-007)
- [ ] `MainController.insertEvent` использует `eventsBuffer.enqueue()` (FR-006)
- [ ] Kill-switch `karaoke.web.events.batch-enabled` через `KaraokeProperties.getBoolean` (default false)
- [ ] LiveDoc создан в `livedocs/features/274-events-batch-insert.md`
- [ ] Все 7 CI gates PASS
- [ ] PR создан и замержен в master
- [ ] Parent спека 241 обновлена (T012.5 → done)

## Notes

- Эта фича — Tier-3 P2 из parent спеки 241, FR-109 (см. spec.md Clarifications Session 2026-08-26).
- **Opt-in default**: kill-switch `false` — на текущем проде INSERT как раньше, безопасный rollout.
- **Fail-open**: при крэше буфер теряется (in-memory). Потеря допустима для логирования.
- **JDBC `addBatch()`**: PostgreSQL оптимизирует через `reWriteBatchedInserts=true`.
- **SamplingFilter + DedupCache** уже уменьшают поток (30 req/min после фильтров). Батчинг — дополнительный уровень.
- Runtime-валидация (опционально, делается пользователем): включить kill-switch, нагрузить, проверить `pg_log`.
- См. plan.md Risks & Mitigations для деталей про fail-open, race conditions и JDBC batch.
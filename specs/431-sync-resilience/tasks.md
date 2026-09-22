# Tasks: Устойчивость синхронизации с прод-сайтом (Pass 431, #155)

**Input**: `/specs/431-sync-resilience/{spec,plan}.md`

## Phase 1: SyncRemoteClient (US1/US2) 🎯 MVP

- [ ] T001 Create `services/SyncRemoteClient.kt`:
  - `object SyncRemoteClient`, logger `infra.sync.remote`.
  - `postChangeRecords(body): Boolean` + `internal postChangeRecords(body, send)`.
  - timeouts connect 10s / request 60s; 1 retry (2s) на транзиентные; лог ok/failure.
  - `isTransient(e)`.

## Phase 2: Замена call sites (US1)

- [ ] T002 Modify `Utils.kt` — 6 call sites (936, 963, 1160, 1188, 1216, 1253) →
  `SyncRemoteClient.postChangeRecords(requestBody)`; не пропагировать наружу.
- [ ] T003 Modify `ApiController.postSyncOneClick` — per-target `try/catch` +
  `error: String?` в `SyncOneClickResultDto`.

## Phase 3: Frontend (US3)

- [ ] T004 Modify `webvue3/src/components/Sync/SyncTable.vue::showResultAlert` —
  показать `r.error` (если есть).

## Phase 4: Tests

- [ ] T005 [P] Create `services/SyncRemoteClientTest.kt`:
  - 1-я попытка падает транзиентно, 2-я успешна → true (retry).
  - обе падают → false, без исключения.
  - нетранзиентная ошибка → сразу false (без retry).
  - успех с 1-й попытки.
- [ ] T006 Run `:karaoke-app:test --tests "*SyncRemoteClientTest"` — PASS.

## Phase 5: Knowledge & docs (SSoT)

- [ ] T007 [P] `knowledge/domains/processing/components/two-db-sync.md` — helper + resilience.
- [ ] T008 [P] `knowledge/domains/processing/components/run-entity-sync.md` — error в DTO.
- [ ] T009 [P] `knowledge/domains/monitoring/components/log-categories.md` — `infra.sync.remote`.
- [ ] T010 [P] `docs/features/sync-resilience.md` (NEW).

## Phase 6: Validation & PR

- [ ] T011 Validation:
  ```bash
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*SyncRemoteClientTest"
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel
  cd webvue3 && npm run lint && cd ..
  bash tools/check-knowledge-structure.sh
  ```
- [ ] T012 `report.md`; commit/push/PR; CI; merge; tracker `add-comment`/`mark-review`.
- [ ] T013 Запросить рестарт `karaoke-app` (владелец).

## Dependencies

Phase 1 → 2 → 3 → 4 → 5 → 6. MVP: T001–T006 + T011–T013.

## Ready for implementation

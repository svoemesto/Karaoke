# Tasks: Разъединение circuit breaker local/remote (Pass 429, #153)

**Input**: `/specs/429-split-local-remote-circuit-breakers/{spec,plan}.md`

## Phase 1: Core — два независимых breaker'а (US1/US2, P1) 🎯 MVP

- [ ] T001 Modify `services/StorageCircuitBreaker.kt`:
  - add `val storageType: String = "remote"` (ctor param, default — backward compat).
  - add `fun <T : Any> executeBlocking(operation: String, loader: () -> T, emptyValue: T): T`
    (FastFail→emptyValue; Allow/Probe→try/catch recordSuccess/Failure).
  - add `storage={}` в логи `cache:network:failure` / `cache:circuit:state` / watchdog / reset.

- [ ] T002 Create `services/StorageCircuitBreakerConfig.kt`:
  - `@Configuration`; два `@Bean`: `localStorageCircuitBreaker` (storageType="local"),
    `remoteStorageCircuitBreaker` (storageType="remote"); общие `@Value`-пороги.

- [ ] T003 Modify `services/StorageApiClient.kt`:
  - конструктор: `@Qualifier("remoteStorageCircuitBreaker")` перед `storageCircuitBreaker`.

- [ ] T004 Modify `services/KaraokeStorageService.kt` (`KaraokeStorageServiceImpl`):
  - конструктор: `@Qualifier("localStorageCircuitBreaker") private val localStorageCircuitBreaker: StorageCircuitBreaker`.
  - обернуть `fileExists`, `getFileStat` (и `getFileInfo` через них) в
    `executeBlocking(...)`; `fileIsActual` — корректная обработка empty.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: HealthReport + wiring (US1/US2)

- [ ] T005 Modify `services/StorageCircuitBreakerWiring.kt`:
  - принимать оба bean (`@Qualifier`), attach в `HealthReport`.

- [ ] T006 Modify `HealthReport.kt`:
  - два static volatile-поля: `localStorageCircuitBreaker`, `remoteStorageCircuitBreaker`.
  - `attachStorageCircuitBreakers(local, remote)`.
  - `actionsLocalStorage` (строка ~638) → `localStorageCircuitBreaker`, FATAL_ERROR
    «Локальное хранилище недоступно (circuit breaker open)», лог `storage=local`.
  - remote-проверки → `remoteStorageCircuitBreaker`.

**Checkpoint**: compile OK.

## Phase 3: Controllers (US3, P2)

- [ ] T007 Modify `controllers/CircuitBreakerController.kt`:
  - GET `/api/health/circuit-breaker` → `{ local, remote }` (оба Metrics).
  - POST `/reset?storage=local|remote|all` (default all, backward-compat).

- [ ] T008 Modify `controllers/CacheStatsController.kt`:
  - `circuitBreaker` → объект `{ local, remote }` (backward-compat: добавить оба).

**Checkpoint**: compile OK.

## Phase 4: Tests

- [ ] T009 [P] Create `services/StorageCircuitBreakerIsolationTest.kt`:
  - local OPEN не влияет на remote и наоборот (разные экземпляры).
  - `executeBlocking` FastFail при OPEN (без вызова loader).
  - `executeBlocking` success → recordSuccess; failure → recordFailure.
- [ ] T010 Run `--tests "*StorageCircuitBreaker*"` + `--tests "*StorageTimeoutInterrupt*"` — PASS.

## Phase 5: Knowledge & docs (SSoT)

- [ ] T011 [P] `knowledge/domains/health/components/health-report.md` — исправить
  расхождение (breaker защищает оба, раздельно).
- [ ] T012 [P] `knowledge/domains/storage/domain.md` + `components/storage-api-client.md`
  + `components/karaoke-storage-service.md` + `components/storage-flow.md` — два breaker.
- [ ] T013 [P] `knowledge/domains/monitoring/components/log-categories.md` — `storage=`.
- [ ] T014 [P] `docs/features/storage-metadata-cache.md` — V2.5.

## Phase 6: Validation & PR

- [ ] T015 Validation (сборка/линтеры/guards).
- [ ] T016 `report.md`; commit/push/PR; CI; merge; tracker `add-comment`/`mark-review`.
- [ ] T017 Запросить рестарт `karaoke-app` (владелец) + проверка: remote OPEN не
  даёт local FATAL_ERROR.

## Dependencies

Phase 1 → 2 → 3 → 4 → 5 → 6. MVP: T001–T010 + T015–T017.

## Ready for implementation

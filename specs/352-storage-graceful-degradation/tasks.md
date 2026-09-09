# Tasks: Storage graceful degradation (Pass 351, OpenProject #71)

**Input**: Design documents from `/specs/352-storage-graceful-degradation/`
- [plan.md](./plan.md) (tech stack, libraries, structure)
- [spec.md](./spec.md) (user stories P1, P2, P3)
- [data-model.md](./data-model.md) (StorageCircuitBreaker FSM)
- [contracts/circuit-breaker-state.md](./contracts/circuit-breaker-state.md) (API contracts)
- [research.md](./research.md) (decisions: in-memory, all file-*, half-open)
- [quickstart.md](./quickstart.md) (test scenarios)

**Tests**: explicitly required — `StorageCircuitBreakerTest` для state transitions, integration tests через `quickstart.md`.

**Organization**: Tasks grouped by user story. Phase 1 setup, Phase 2 foundational (must complete before stories), Phases 3-5 stories (P1, P2, P3), Phase 6 polish.

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: parallelizable (different files, no deps on incomplete tasks)
- **[Story]**: US1/US2/US3 for user story phases only

---

## Phase 1: Setup (StorageCircuitBreaker skeleton + config)

**Purpose**: Создать новый bean skeleton + configuration property structure.

- [x] T001 Create `karaoke-app/.../services/StorageCircuitBreaker.kt` skeleton: `@Component` class, `enum class State { CLOSED, HALF_OPEN, OPEN }`, fields `state: AtomicReference<State>`, `failureCount: AtomicLong`, `openedAtMs: AtomicLong`, `successCount: AtomicLong`, `networkFailureCount: AtomicLong`, `totalSuccesses: AtomicLong`, `totalNetworkFailures: AtomicLong`. Constructor with 3 `@Value` properties: `timeoutSeconds: Long`, `threshold: Int`, `cooldownSeconds: Long`. String logger `infra.cache.storage`. Empty methods: `acquire(): Decision`, `recordSuccess()`, `recordFailure(error: Throwable)`, `state(): State`, `metrics(): Metrics`, `decorate<T>(operation, loader, emptyValue): Mono<T>`. KDoc with `@see docs/features/storage-metadata-cache.md`.
- [x] T002 Modify `karaoke-app/src/main/resources/application.yml`: append to `storage:` block — `file-exists-timeout-seconds: 5`, `circuit-breaker-threshold: 5`, `circuit-breaker-cooldown-seconds: 30`. Comments referencing `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` etc. env overrides.

**Checkpoint**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — must compile (skeleton has no logic yet).

---

## Phase 2: Foundational (State machine + atomic operations)

**Purpose**: Реализовать state machine + counters + acquire logic — блокирующая зависимость для всех file-* методов (US1).

- [x] T003 [P] Implement `acquire()` method in `StorageCircuitBreaker.kt`:
  - Read current `state` (AtomicReference.get).
  - CLOSED → return `Decision.Allow`.
  - OPEN: check `now() - openedAtMs > cooldownSeconds * 1000`; if true → `compareAndSet(OPEN, HALF_OPEN)`; if won → return `Decision.Probe`, else → return `Decision.FastFail`. If cooldown not elapsed → `Decision.FastFail`.
  - HALF_OPEN → return `Decision.FastFail` (only first thread wins probe via `compareAndSet(HALF_OPEN, OPEN)` for the duration of probe).
  - Log transition `cache:circuit:state` at INFO level on state change.
- [x] T004 [P] Implement `recordSuccess()` method: `failureCount.set(0)`; if `state` was `HALF_OPEN` → `state.set(CLOSED)`; `totalSuccesses.incrementAndGet()`. Log `cache:circuit:state HALF_OPEN → CLOSED` on transition.
- [x] T005 [P] Implement `recordFailure(error)` method: `networkFailureCount.incrementAndGet(); totalNetworkFailures.incrementAndGet()`. If `state` is `HALF_OPEN` → `state.set(OPEN); openedAtMs.set(now())`. If `state` is `CLOSED` → `failureCount.incrementAndGet()`; if `>= threshold` → `state.set(OPEN); openedAtMs.set(now())`. Log `cache:network:failure` (WARN) + `cache:circuit:state` on OPEN transition.
- [x] T006 Implement `decorate<T>(operation: String, loader: () -> Mono<T>, emptyValue: T): Mono<T>`:
  - `val decision = acquire()`.
  - `Mono.defer { when (decision) { Allow, Probe -> loader().timeout(Duration.ofSeconds(timeoutSeconds)).doOnSuccess { recordSuccess() }.doOnError { recordFailure(it) }.onErrorReturn(emptyValue); FastFail -> Mono.just(emptyValue) } }`.

**Checkpoint**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — must compile.

---

## Phase 3: User Story 1 — file-* methods with circuit (Priority: P1) 🎯 MVP

**Goal**: `StorageApiClient.fileExists`/`fileIsActual`/`getFileInfo` используют `StorageCircuitBreaker`. При network outage возвращают `false` (или `empty` для `getFileInfo`) за `timeoutSeconds` (5s), а НЕ блокируются на 60s.

**Independent Test**: Сценарий 1+2 из `quickstart.md` — `tools/with-dead-network.sh` + `tools/measure-fileExists-latency.sh`. Verify latency ≤ 5s + state transitions через `/api/health/circuit-breaker`.

### Tests for User Story 1

- [x] T007 [P] [US1] Create `karaoke-app/src/test/kotlin/.../services/StorageCircuitBreakerTest.kt`. 8 unit-теста:
  - `closed state allows calls` (acquire returns Allow).
  - `threshold failures opens circuit` (5 fails → OPEN, next acquire → FastFail).
  - `cooldown half-opens circuit` (OPEN + sleep cooldown + acquire → Probe).
  - `probe success closes circuit` (HALF_OPEN + recordSuccess → CLOSED).
  - `probe failure reopens circuit` (HALF_OPEN + recordFailure → OPEN, openedAtMs reset).
  - `concurrent acquire allows only one probe` (10 потоков, only 1 Probe).
  - `concurrent failures transition once to OPEN` (10 потоков × recordFailure, exactly 1 OPEN transition via state.compareAndSet — validates NFR-005).
  - `overhead closed state under 1ms` (1000 acquires in CLOSED state, total < 1000ms — validates NFR-003).
- [x] T008 [P] [US1] Run `:karaoke-app:test --tests "StorageCircuitBreakerTest"` — verify 8/8 PASS.

### Implementation for User Story 1

- [x] T009 [US1] Modify `karaoke-app/.../services/StorageApiClient.kt`:
  - Add `constructor(private val storageCircuitBreaker: StorageCircuitBreaker)` to `StorageApiClientImpl`.
  - Wrap `fileExists` (line 339-351): change `checkIfExists(...).block()` to `storageCircuitBreaker.decorate("fileExists", { checkIfExists(bucketName, fileName) }, false).block()`. Catch exception in `.block()` and return false.
  - Wrap `fileIsActual` (line 353+): same pattern with emptyValue=false.
  - Wrap `getFileInfo` (line 327+): same pattern with emptyValue=null. Returns `Mono<StorageFileInfo>` or `Mono.empty()`.
- [x] T010 [US1] Modify `karaoke-app/.../services/StorageApiClient.kt` for `StorageApiClientImpl` constructor: add `storageCircuitBreaker: StorageCircuitBreaker` parameter. Verify Spring can autowire (single bean of that type).

**Checkpoint**: 6/6 unit tests pass + `fileExists` integration scenario: dead network → returns false in ≤5s.

---

## Phase 4: User Story 2 — Configuration via @Value (Priority: P2)

**Goal**: Configuration через `application.yml` + env-vars работает. Override через `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10` → `timeoutSeconds=10`.

**Independent Test**: Сценарий 4 из `quickstart.md` — env-vars override + verify через `/api/health/circuit-breaker`.

### Implementation for User Story 2

- [x] T011 [US1] Verify `@Value` resolution from `application.yml` works (Pass 1 implementation). Run karaoke-app + check `curl /api/health/circuit-breaker | jq .timeoutSeconds` = 5 (default).
- [x] T012 [P] [US2] Add `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10 STORAGE_CIRCUIT_BREAKER_THRESHOLD=2 STORAGE_CIRCUIT_BREAKER_COOLDOWN_SECONDS=60` to deployment docs (`docs/architecture-notes.md` or `docs/operations/circuit-breaker.md` NEW). Include: name (STORAGE_FILE_EXISTS_TIMEOUT_SECONDS), default value, range, example. Skip if not creating new doc — extend architecture-notes.md.
- [x] T013 [P] [US2] Integration test (manual or via `tools/with-env.sh` NEW): set env vars, run karaoke-app, verify `/api/health/circuit-breaker` returns override values. Document expected output in `quickstart.md` Scenario 4.

**Checkpoint**: Configuration override works without code change.

---

## Phase 5: User Story 3 — Observability endpoint (Priority: P3)

**Goal**: `GET /api/health/circuit-breaker` endpoint returns full snapshot. `CacheStatsController` extended with `networkFailures` and `circuitBreaker` sub-object.

**Independent Test**: Сценарий 5 из `quickstart.md` — verify all observability channels.

### Implementation for User Story 3

- [x] T014 [P] [US3] Modify `karaoke-app/.../controllers/CacheStatsController.kt`:
  - Inject `StorageCircuitBreaker` via constructor.
  - In `cacheStats()` method, append to each (local/remote) StatsBucket: `networkFailures = cb.networkFailureCount.sum()`, `circuitBreaker = cb.metrics()`.
  - Update `CacheStatsDto` (in `StorageMetadataCache.kt`) to include the new fields.
- [x] T015 [P] [US3] Create `karaoke-app/.../controllers/CircuitBreakerController.kt` (P3 endpoint):
  - `@RestController @RequestMapping("/api/health/circuit-breaker")`.
  - `GET /` → `CircuitBreakerResponse` JSON (state, failureCount, lastFailureAt, totalSuccesses, totalNetworkFailures, threshold, cooldownSeconds, timeoutSeconds).
  - `POST /reset` → resets state to CLOSED + clears counters, returns previousState.
- [x] T016 [P] [US3] Add `@see` to `StorageCircuitBreaker.kt` class KDoc → `knowledge/domains/storage/components/storage-api-client.md` (existing). Update `docs/features/storage-metadata-cache.md` to mention circuit breaker (V2 → V2.1).

**Checkpoint**: `/api/health/circuit-breaker` returns full snapshot, `/api/health/cacheStats` includes new fields.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Knowledge updates, per-feature doc, integration with Pass 344/345 cache, OpenProject workflow.

- [ ] T017 [P] Modify `knowledge/domains/storage/components/storage-api-client.md`: add section "Pass 351: graceful degradation" referencing `StorageCircuitBreaker`, `decorate` pattern, default timeouts. Cross-link to `specs/352-storage-graceful-degradation/spec.md`.
- [ ] T018 [P] Modify `docs/features/storage-metadata-cache.md` (V2 from Pass 344/345): add section "Pass 351: circuit breaker" — explains fileExists/fileIsActual/getFileInfo now wrapped in `StorageCircuitBreaker.decorate(...)`. Include `infra.cache.storage` new events (`cache:network:failure`, `cache:circuit:state`).
- [ ] T019 [P] Modify `knowledge/domains/monitoring/components/log-categories.md`: add 2 new events under `infra.cache.storage`:
  - `cache:network:failure` — per-call network failure.
  - `cache:circuit:state` — state transition (CLOSED→OPEN, OPEN→HALF_OPEN, HALF_OPEN→CLOSED, HALF_OPEN→OPEN).
- [ ] T020 [P] Modify `docs/architecture-notes.md`: add Pass 351 entry — `StorageCircuitBreaker` for file-* methods, semi-open probe, in-memory only, three new env-vars. Cross-link to `specs/352-storage-graceful-degradation/spec.md`.
- [ ] T021 [P] Update `tools/README.md` — add `StorageCircuitBreaker` section. Mention: per-call timeout, half-open probe, in-memory, no DB.
- [ ] T022 [P] Create `tools/with-dead-network.sh` (NEW helper): mock network outage. Args: `setup` (block via `/etc/hosts` or iptables) / `restore` / `status`. Used in `quickstart.md` Scenario 1+2. Idempotent.
- [ ] T023 [P] Create `tools/measure-fileExists-latency.sh` (NEW helper): measures `fileExists` latency. Used in `quickstart.md` Scenario 1+2. Returns JSON `{latencyMs, result, exitCode}`.
- [ ] T024 [P] Update `AGENTS.md` § "Ограничения агента" — add that `StorageCircuitBreaker` introduces new test commands and a `--with-dead-network` flag. Reflect new files in PASS sections.
- [x] T025 [P] Update `specs/352-storage-graceful-degradation/spec.md` § OpenProject Tracking with workflow status: claim → done (Pass 350), add-comment → next, mark-review → next, close → after merge.
- [x] T026 [US3] OpenProject workflow (Pass 350 hooks): after merge, run `bash tools/tracker-implement-done.sh 71` to auto-comment + mark-review.
- [x] T027 [US3] Manual close-#65 (Optional deliverable — addresses SC-005). After `#71` merged and owner reviewed, run `bash tools/tracker.sh close-issue 65` — root cause finally fixed (Pass 343 single-flight guard + Pass 344/345 cache + Pass 351 circuit breaker). Update `docs/architecture-notes.md` Pass 351 final entry: «#65 closed via tracker.sh». Owner decision final.

**Checkpoint**: All docs updated, knowledge cross-linked, OpenProject workflow completed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No deps. T001+T002 can be parallel.
- **Foundational (Phase 2)**: Depends on Phase 1 (T001 for the bean class). T003+T004+T005 parallel, T006 depends on T003+T004+T005.
- **US1 (Phase 3)**: Depends on Phase 2. T007+T008 parallel (test create+run). T009 depends on T001+T003-T006 (uses `decorate`).
- **US2 (Phase 4)**: Depends on US1 (config validates). T011+T012+T013 can be parallel.
- **US3 (Phase 5)**: Depends on US1 (endpoint reuses bean). T014+T015+T016 parallel.
- **Polish (Phase 6)**: Depends on US1+US2+US3. Most tasks parallel.

### Story Dependencies

- **US1 (P1)**: independent (MVP)
- **US2 (P2)**: depends on US1
- **US3 (P3)**: depends on US1

### MVP (US1 only)

Достаточно T001-T010 для закрытия SC-001 (latency ≤ 5s на network outage) + SC-002 (circuit OPEN после 5 failures). US2/US3 опциональны.

## Parallel Opportunities

| Phase | Parallel Tasks |
|---|---|
| Phase 1 | T001 ‖ T002 |
| Phase 2 | T003 ‖ T004 ‖ T005 (after T001); T006 after these |
| Phase 3 | T007 ‖ T008 (tests); T009 after T006; T010 after T009 |
| Phase 5 | T014 ‖ T015 ‖ T016 (independent) |
| Phase 6 | T017-T024 all parallel (different files) |

## Implementation Strategy

### MVP first (US1 only)

1. T001: Create `StorageCircuitBreaker` skeleton.
2. T002: Update `application.yml`.
3. T003-T005: State machine + counters.
4. T006: `decorate` implementation.
5. T007-T008: Unit tests (verify state machine).
6. T009-T010: Wrap file-* methods in `StorageApiClientImpl`.
7. **Verify**: `bash quickstart.md` Scenario 1+2 — fileExists returns false за ≤ 5s + circuit OPEN.
8. **Stop** — базовая функциональность готова.

### Incremental Delivery

1. MVP (steps 1-7 above).
2. US2 (P2): добавить env-vars, integration test.
3. US3 (P3): добавить observability endpoint.
4. Polish: knowledge, docs, OpenProject workflow.

## Format Validation

- ✅ Все задачи имеют checkbox `- [ ]`.
- ✅ Task IDs sequential T001-T027.
- ✅ Story labels только на Phase 3+ (US1, US2, US3).
- ✅ Setup/Foundational/Polish — без story labels.
- ✅ File paths в каждой задаче.
- ✅ [P] marker где parallelizable.

## Ready for /speckit.implement

tasks.md complete with 27 tasks. Format validated. Dependencies mapped. MVP scope (US1) clearly defined.

**MVP task count**: 10 tasks (T001-T010) — sufficient to demonstrate Pass 351 functionality.
**Full task count**: 27 tasks (T001-T027).
**Parallel opportunities**: ~12 tasks parallelizable.

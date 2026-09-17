# Tasks: StorageCircuitBreaker watchdog + manual reset (Pass 372, #131)

**Input**: Design documents from `/specs/405-storage-circuit-breaker-watchdog/`
- [plan.md](./plan.md) (tech stack, архитектура, контракты)
- [spec.md](./spec.md) (user stories P1, FR/NFR/SC)

**Organization**: Tasks grouped by phase. Phase 1 setup, Phase 2 watchdog (P1 US1), Phase 3 reset endpoint (P1 US2), Phase 4 docs.

**Format**: `[ID] [P?] [Story?] Description with file path`

- **[P]**: parallelizable (different files, no deps on incomplete tasks)
- **[Story]**: US1/US2/US3 for user story phases only

---

## Phase 1: Setup (application.yml defaults)

**Purpose**: Добавить 3 новых property с дефолтами в `application.yml`, чтобы watchdog мог стартовать при инициализации bean.

**NB**: `KaraokeProperties.kt` НЕ используется для circuit breaker — настройки читаются через `@Value` в конструкторе `StorageCircuitBreaker.kt` (см. строки 32-34 существующего кода: `@Value("\${storage.file-exists-timeout-seconds:5}")`). Достаточно добавить defaults в yml + добавить @Value параметры в StorageCircuitBreaker.kt.

- [ ] T001 Modify `karaoke-app/src/main/resources/application.yml`:
  - После `circuit-breaker-cooldown-seconds: 30` (строка 73) добавить:
    ```yaml
      # NEW (Pass 372, спека #405): watchdog для HALF_OPEN state + manual reset endpoint.
      # Override через env: STORAGE_CIRCUIT_BREAKER_WATCHDOG_*.
      circuit-breaker-watchdog-enabled: true
      circuit-breaker-watchdog-buffer-seconds: 10
      circuit-breaker-watchdog-check-interval-seconds: 1
    ```

**Checkpoint**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — must compile.

---

## Phase 2: Watchdog (User Story 1, Priority: P1) 🎯 MVP

**Goal**: `StorageCircuitBreaker` запускает ScheduledExecutorService watchdog при `@PostConstruct`. Если state=HALF_OPEN дольше `timeoutSeconds + watchdogBufferSeconds` без recordSuccess/recordFailure — watchdog принудительно переводит в OPEN.

**Independent Test**: Unit-тест `testWatchdogReopensStuckProbe` (T007).

### Implementation for User Story 1

- [ ] T003 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt`:
  - Добавить imports: `jakarta.annotation.PostConstruct`, `jakarta.annotation.PreDestroy`, `java.util.concurrent.Executors`, `java.util.concurrent.TimeUnit`, `java.util.concurrent.ScheduledExecutorService`.
  - Constructor: добавить 3 `@Value` параметра: `watchdogEnabled: Boolean`, `watchdogBufferSeconds: Long`, `checkIntervalSeconds: Long` (с дефолтами в @Value для backward compat).
  - Fields:
    - `private val watchdogExecutor: ScheduledExecutorService?` (nullable, lazy init в @PostConstruct).
    - `private val halfOpenSinceMs = AtomicLong(0L)`.
  - Modify `acquire()`: после успешного CAS `OPEN→HALF_OPEN` — `halfOpenSinceMs.set(System.currentTimeMillis())`.
  - Modify `recordSuccess()`: после успешного CAS `HALF_OPEN→CLOSED` — `halfOpenSinceMs.set(0L)`.
  - Modify `recordFailure()` для HALF_OPEN: после успешного CAS `HALF_OPEN→OPEN` — `halfOpenSinceMs.set(0L)`.
  - Add `initWatchdog()` (`@PostConstruct`): если `watchdogEnabled` — создать single-thread daemon ScheduledExecutorService, scheduleAtFixedRate с initialDelay=checkIntervalSeconds, period=checkIntervalSeconds.
  - Add `destroyWatchdog()` (`@PreDestroy`): если `watchdogExecutor != null` — `shutdown()`, awaitTermination(5s).
  - Add `private fun watchdogTick()`:
    - Read `state.get()`.
    - If != HALF_OPEN — return.
    - Compute `now - halfOpenSinceMs`. Если > `(timeoutSeconds + watchdogBufferSeconds) * 1000`:
      - Попробовать CAS `HALF_OPEN → OPEN`.
      - Если выиграли: `openedAtMs.set(now)`, `halfOpenSinceMs.set(0)`, `log.warn("cache:circuit:watchdog state=HALF_OPEN->OPEN durationMs={}", ...)`.
  - KDoc updates: добавить секцию «Watchdog (Pass 372)» с описанием.

**Checkpoint**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — must compile.

### Tests for User Story 1

- [ ] T004 [P] Modify `karaoke-app/src/test/kotlin/.../services/StorageCircuitBreakerTest.kt`:
  - Add `testWatchdogReopensStuckProbe`:
    1. Создать cb с `watchdogBufferSeconds=1`, `timeoutSeconds=1`, `checkIntervalSeconds=1`.
    2. `cb.acquire()` (CLOSED) → Allow.
    3. `cb.recordFailure(SocketTimeoutException)` × 5 → state=OPEN.
    4. `Thread.sleep(cooldownSeconds * 1000 + 100)` → next acquire → Probe (HALF_OPEN). `halfOpenSinceMs` должен быть > 0.
    5. **Не вызывать** recordSuccess/recordFailure.
    6. `Thread.sleep(timeoutSeconds * 1000 + watchdogBufferSeconds * 1000 + 500)`.
    7. Assert: `cb.state() == OPEN`, `cb.metrics().lastFailureAt > originalOpenedAtMs`.
    8. Assert: log message `cache:circuit:watchdog state=HALF_OPEN->OPEN` (через LogCaptor или ListAppender).

- [ ] T005 [P] Run `:karaoke-app:test --tests "*StorageCircuitBreakerTest"` — verify 9/9 PASS (8 existing + 1 new).

**Checkpoint**: Watchdog работает, тест зелёный.

---

## Phase 3: Manual reset endpoint (User Story 2, Priority: P1)

**Goal**: `POST /api/health/circuit-breaker/reset` сбрасывает circuit в CLOSED. Возвращает previousState/currentState.

**Independent Test**: Unit-тест `testResetEndpointTransitionsToClosed` (T008) + `curl -X POST` после manual setup.

### Implementation for User Story 2

- [ ] T006 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt`:
  - Add `fun reset(): Metrics`:
    - Read `previousState = state.get()`.
    - `state.set(CLOSED)`.
    - `failureCount.set(0)`.
    - `openedAtMs.set(0)`.
    - `halfOpenSinceMs.set(0)`.
    - Log `cache:circuit:reset reason=manual_request previousState=<X>`.
    - Return updated `metrics()`.

- [ ] T007 [P] Create `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CircuitBreakerController.kt`:
  - `@RestController @RequestMapping("/api/health/circuit-breaker")`.
  - Constructor: `private val storageCircuitBreaker: StorageCircuitBreaker`.
  - `data class CircuitBreakerResetResponse(...)` — см. plan.md контракт.
  - `@GetMapping fun state(): StorageCircuitBreaker.Metrics = storageCircuitBreaker.metrics()` (move from CacheStatsController? — нет, оставить оба, чтобы не ломать backward compat).
  - `@PostMapping("/reset") fun reset(): CircuitBreakerResetResponse = ...` — вызвать `storageCircuitBreaker.reset()`, build response.
  - KDoc с `@see specs/405-storage-circuit-breaker-watchdog/spec.md`.

- [ ] T008 [P] Add unit-тест `testResetEndpointTransitionsToClosed` в `StorageCircuitBreakerTest.kt`:
  1. Заставить cb в OPEN (5 fails).
  2. `cb.reset()`.
  3. Assert: `cb.state() == CLOSED`, `cb.metrics().failureCount == 0`, `cb.metrics().lastFailureAt == null`.
  4. Повторный `cb.reset()` → assert state == CLOSED (no-op).

- [ ] T009 Run `:karaoke-app:test --tests "*StorageCircuitBreakerTest"` — verify 10/10 PASS.

**Checkpoint**: Reset endpoint работает, тесты зелёные.

---

## Phase 4: Documentation & Knowledge (User Story 3, Priority: P2)

**Goal**: Обновить knowledge (storage-api-client.md, log-categories.md) + спеку #352 (cross-link, исправить T015) + docs/features.

### Implementation for User Story 3

- [ ] T010 [P] Modify `knowledge/domains/storage/components/storage-api-client.md`:
  - Append section «Pass 372: watchdog (Spec #405, OpenProject #131)»:
    - Описание watchdog (ScheduledExecutorService).
    - Когда срабатывает: state=HALF_OPEN > timeoutSeconds+watchdogBufferSeconds.
    - SLF4J events: `cache:circuit:watchdog`, `cache:circuit:reset`.
    - Cross-link на `specs/405-storage-circuit-breaker-watchdog/spec.md`.
    - Production-incident 2026-09-17 (HALF_OPEN зависание).

- [ ] T011 [P] Modify `knowledge/domains/monitoring/components/log-categories.md`:
  - В строке `infra.cache.storage` добавить 2 новых event:
    - `cache:circuit:watchdog` (WARN) — watchdog перевёл HALF_OPEN→OPEN (probe stuck).
    - `cache:circuit:reset` (INFO) — manual reset endpoint вызван.

- [ ] T012 [P] Modify `specs/352-storage-graceful-degradation/tasks.md`:
  - Uncheck T015 (помечен ложно): изменить `[x]` → `[ ]` с комментарием «2026-09-17: file не создан, перенесено в спеку #405 (Pass 372)».
  - Add cross-link в Phase 6 Polish: «See specs/405-storage-circuit-breaker-watchdog/spec.md для follow-up watchdog + reset endpoint (Pass 372, #131)».

- [ ] T013 [P] Modify `specs/352-storage-graceful-degradation/spec.md`:
  - В § «Clarifications» добавить: «2026-09-17: Production-наблюдение HALF_OPEN зависание → follow-up #131, спека #405 (Pass 372)».
  - FR-007 повышен с «P3 optional» до hard requirement (cross-link на спеку #405).

- [ ] T014 [P] Modify `docs/features/storage-metadata-cache.md`:
  - Обновить V2.1 → V2.2: добавить секцию «Pass 372: watchdog + reset endpoint» с cross-link на спеку #405.

**Checkpoint**: `bash tools/check-ssot-impact.py` — 0 violations. `bash tools/check-knowledge-structure.sh` — 9/9 OK. `python3 tools/lint-knowledge.py` — 0 violations.

---

## Phase 5: Polish & Validation

**Purpose**: Финальные проверки, build, PR.

- [ ] T15 [P] Run validation:
  ```bash
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*StorageCircuit*"
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel
  bash tools/check-ssot-impact.py
  bash tools/check-knowledge-structure.sh
  python3 tools/lint-knowledge.py
  ```
- [ ] T16 Push + PR:
  ```bash
  git add specs/405-storage-circuit-breaker-watchdog/ \
          specs/352-storage-graceful-degradation/ \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProperties.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CircuitBreakerController.kt \
          karaoke-app/src/main/resources/application.yml \
          karaoke-app/src/test/kotlin/.../services/StorageCircuitBreakerTest.kt \
          knowledge/domains/storage/components/storage-api-client.md \
          knowledge/domains/monitoring/components/log-categories.md \
          docs/features/storage-metadata-cache.md
  git commit -m "405: StorageCircuitBreaker watchdog + manual reset endpoint (Pass 372, #131)"
  git push -u origin 405-storage-circuit-breaker-watchdog
  gh pr create --base master --title "405: StorageCircuitBreaker watchdog + manual reset (Pass 372, #131)" --body "..."
  ```
- [ ] T17 CI checks:
  ```bash
  gh pr checks
  ```
  Ждать пока все PASS.
- [ ] T18 Merge:
  ```bash
  gh pr merge --merge   # БЕЗ --delete-branch
  ```
- [ ] T19 OpenProject workflow:
  ```bash
  ./tools/tracker.sh add-comment 131 --file specs/405-storage-circuit-breaker-watchdog/report.md
  ./tools/tracker.sh mark-review 131
  ```
- [ ] T20 Запросить согласие у владельца на рестарт `karaoke-app`:
  ```bash
  cd deploy && bash do.sh restart_karaoke_app
  ```

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** (Setup): No deps.
- **Phase 2** (Watchdog): Depends on Phase 1 (нужны property).
- **Phase 3** (Reset): Depends on Phase 2 (использует cb.reset(), который добавляется в Phase 2... хотя можно и параллельно, они независимы).
- **Phase 4** (Docs): Depends on Phase 2+3 (знать что описывать).
- **Phase 5** (Polish): Depends on Phase 4.

### Parallel Opportunities

| Phase | Parallel Tasks |
|---|---|
| Phase 1 | T001 ‖ T002 |
| Phase 2 | T003 (impl) ‖ T004 (test) |
| Phase 3 | T006 (cb.reset) ‖ T007 (controller) ‖ T008 (test) |
| Phase 4 | T010 ‖ T011 ‖ T012 ‖ T013 ‖ T014 |
| Phase 5 | T15 (validation) → T16 (commit/push) → T17 (CI) → T18 (merge) → T19 (tracker) → T20 (deploy) |

### MVP Scope (минимум для production-fix)

Достаточно T001-T009 + T16-T20 для:
- Закрытия SC-001 (watchdog timeout).
- Закрытия SC-002 (reset endpoint).
- Production-ready после рестарта.

T010-T14 (docs) — могут быть в следующем PR, но Hard Gate Tier-1 SSoT требует их в этом же PR.

## Implementation Strategy

### MVP first

1. T001-T002: Property + yml (5 min).
2. T003: Watchdog в StorageCircuitBreaker (30 min).
3. T004: Unit-тест watchdog (15 min).
4. T005: Verify test green (5 min).
5. T006: reset() method в cb (10 min).
6. T007: CircuitBreakerController (15 min).
7. T008: Unit-тест reset (10 min).
8. T009: Verify tests green (5 min).
9. T010-T014: Knowledge + docs (30 min).
10. T15-T20: Validation + PR + merge + tracker + deploy (15 min).

**Total estimated time**: ~2.5 hours.

## Ready for implementation

tasks.md complete. Format validated. Dependencies mapped. MVP scope defined.

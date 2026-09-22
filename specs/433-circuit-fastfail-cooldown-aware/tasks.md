# Tasks: isFastFail cooldown-aware (Pass 433, #157)

**Input**: `/specs/433-circuit-fastfail-cooldown-aware/{spec,plan}.md`

## Phase 1: Core fix

- [ ] T001 Modify `services/StorageCircuitBreaker.kt` — `isFastFail()` cooldown-aware:
  CLOSED→false; HALF_OPEN→true; OPEN→`!(cooldown elapsed)`.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Tests

- [ ] T002 Modify `StorageCircuitBreakerTest.kt`:
  - `diagnostics do not consume probe` — обновить: OPEN + cooldown elapsed →
    `isFastFail()`=false; `acquire()`=Probe → CLOSED.
  - `isFastFail mirrors acquire` — обновить под cooldown-семантику.
  - NEW: `isFastFail true before cooldown, false after`.
- [ ] T003 Run `--tests "*StorageCircuitBreaker*"` — PASS.

## Phase 3: Knowledge & docs

- [ ] T004 [P] `knowledge/domains/storage/components/storage-api-client.md` — Pass 433.
- [ ] T005 [P] `docs/features/storage-metadata-cache.md` — V2.8.

## Phase 4: Validation & PR

- [ ] T006 Validation (сборка/линтеры/guards).
- [ ] T007 `report.md`; commit/push/PR; CI; merge; tracker.
- [ ] T008 Запросить рестарт `karaoke-app`.

## Dependencies

Phase 1 → 2 → 3 → 4.

## Ready for implementation

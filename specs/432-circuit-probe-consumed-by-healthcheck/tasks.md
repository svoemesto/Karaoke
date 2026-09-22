# Tasks: Circuit probe не должен съедаться диагностикой (Pass 432, #156)

**Input**: `/specs/432-circuit-probe-consumed-by-healthcheck/{spec,plan}.md`

## Phase 1: Core fix

- [ ] T001 Modify `services/StorageCircuitBreaker.kt` — добавить `fun isFastFail(): Boolean`
  (нетранзишн: OPEN/HALF_OPEN → true; CLOSED → false).
- [ ] T002 Modify `HealthReport.kt` — в `actionsLocalStorage` и `actionsRemoteStorage`
  заменить `cb.acquire()` на `cb.isFastFail()` (fail-fast без потребления probe).

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Tests

- [ ] T003 [P] Modify `StorageCircuitBreakerTest.kt` (или отдельный тест):
  - `diagnostics does not consume probe`: OPEN + cooldown elapsed → `isFastFail()`=true,
    state остаётся OPEN; затем `acquire()`=Probe → loader → recordSuccess → CLOSED.
  - `isFastFail false when CLOSED`.
  - `isFastFail true when HALF_OPEN`.
- [ ] T004 Run `--tests "*StorageCircuitBreaker*"` — PASS.

## Phase 3: Knowledge & docs

- [ ] T005 [P] `knowledge/domains/storage/components/storage-api-client.md` — Pass 432.
- [ ] T006 [P] `knowledge/domains/health/components/health-report.md` — диагностика
  через `isFastFail`.
- [ ] T007 [P] `docs/features/storage-metadata-cache.md` — V2.7.

## Phase 4: Validation & PR

- [ ] T008 Validation (сборка/линтеры/guards).
- [ ] T009 `report.md`; commit/push/PR; CI; merge; tracker.
- [ ] T010 Запросить рестарт `karaoke-app`.

## Dependencies

Phase 1 → 2 → 3 → 4.

## Ready for implementation

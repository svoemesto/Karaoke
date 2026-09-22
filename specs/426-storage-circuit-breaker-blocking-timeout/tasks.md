# Tasks: StorageCircuitBreaker — реальный timeout для блокирующего loader (Pass 426, #150)

**Input**: Design documents from `/specs/426-storage-circuit-breaker-blocking-timeout/`
- [plan.md](./plan.md) (tech stack, архитектура, контракты)
- [spec.md](./spec.md) (user stories P1/P2, FR/NFR/SC)

**Organization**: Phase 1 — core fix (US1, P1), Phase 2 — диагностика (US2, P2),
Phase 3 — tests, Phase 4 — docs/knowledge, Phase 5 — validation/PR.

**Format**: `[ID] [P?] [Story?] Description with file path`

- **[P]**: parallelizable (different files, no deps)
- **[Story]**: US1/US2

---

## Phase 1: Core fix (User Story 1, Priority: P1) 🎯 MVP

**Goal**: `decorate`/`decorateOrEmpty` реально прерывает блокирующий loader по `timeoutSeconds`.

- [ ] T001 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt`:
  - import `reactor.core.scheduler.Scheduler`, `reactor.core.scheduler.Schedulers`.
  - Поле `private val blockingScheduler: Scheduler = Schedulers.boundedElastic()`.
  - В `decorate` (ветка `Allow`/`Probe`) — вставить `.subscribeOn(blockingScheduler)`
    перед `.timeout(Duration.ofSeconds(timeoutSeconds))`.
  - В `decorateOrEmpty` — то же.
  - KDoc: секция «Pass 426: blocking loader timeout (spec #426, #150)».

- [ ] T002 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt`
  (`StorageApiClientImpl`):
  - Добавить конструкторный `@Value($$"${storage.file-exists-timeout-seconds:5}") val fileExistsTimeoutSeconds: Long`.
  - OkHttp: `.connectTimeout(fileExistsTimeoutSeconds, TimeUnit.SECONDS)`,
    `.readTimeout(fileExistsTimeoutSeconds, TimeUnit.SECONDS)`.
  - `writeTimeout` (300s) не трогать. KDoc обновить (edge case из knowledge).

**Checkpoint**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — must compile.

---

## Phase 2: Диагностика (User Story 2, Priority: P2)

- [ ] T003 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`:
  - `actionsLocalStorage` FastFail-лог → `circuit=OPEN storage=remote` (circuit защищает
    remote MinIO через `StorageApiClientImpl`), `problemText`/`solutionText` — про
    удалённое хранилище. Поведенческое разъединение — Out of Scope.

**Checkpoint**: compile OK.

---

## Phase 3: Tests

- [ ] T004 [P] Modify `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreakerTest.kt`:
  - `decorate blocking loader times out at timeoutSeconds`: loader = `Mono.fromCallable { Thread.sleep(10_000); "x" }`
    (или `Thread.sleep` в loader), `timeout=1s`; assert `block()` вернул fallback за ≈1s (< 3s),
    `failureCount == 1`.
  - `probe success after blocking failure closes circuit`: threshold=1, cooldown=0; failure →
    acquire (Probe) → success → CLOSED.

- [ ] T005 Run `:karaoke-app:test --tests "*StorageCircuitBreakerTest"` — all PASS.

**Checkpoint**: тесты зелёные.

---

## Phase 4: Documentation & Knowledge (SSoT)

- [ ] T006 [P] Modify `knowledge/domains/storage/components/storage-api-client.md`:
  - Секция «Pass 426: блокирующий loader + реальный timeout» (root cause, `subscribeOn`,
    выравнивание OkHttp timeout, cross-link на спеку #426 и #150).
- [ ] T007 [P] Modify `knowledge/domains/storage/domain.md`:
  - Hot paths (#75): отметить, что circuit recovery починен (Pass 426).
- [ ] T008 [P] Modify `knowledge/domains/monitoring/components/log-categories.md`:
  - Уточнить `storage=local|remote` для `infra.health.circuit`.
- [ ] T009 [P] Modify `docs/features/storage-metadata-cache.md`:
  - Секция «Pass 426: blocking-loader timeout».

**Checkpoint**: `bash tools/check-ssot-impact.py` — 0 violations;
`bash tools/check-knowledge-structure.sh` — 9/9 OK; `python3 tools/lint-knowledge.py` — 0.

---

## Phase 5: Polish & Validation

- [ ] T010 Validation:
  ```bash
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*StorageCircuitBreakerTest"
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel
  bash tools/check-ssot-impact.py
  bash tools/check-knowledge-structure.sh
  python3 tools/lint-knowledge.py
  ```
- [ ] T011 Write `specs/426-storage-circuit-breaker-blocking-timeout/report.md` (REQUIRED artifact).
- [ ] T012 Commit / push / PR:
  ```bash
  git add specs/426-storage-circuit-breaker-blocking-timeout/ \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt \
          karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreakerTest.kt \
          knowledge/domains/storage/ knowledge/domains/monitoring/components/log-categories.md \
          docs/features/storage-metadata-cache.md
  git commit -m "426: StorageCircuitBreaker реальный timeout для блокирующего loader (Pass 426, #150) [tracker-claim-150]"
  git push -u origin 426-storage-circuit-breaker-blocking-timeout
  gh pr create --base master
  ```
- [ ] T013 CI: `gh pr checks` — all PASS.
- [ ] T014 Merge: `gh pr merge --merge` (БЕЗ `--delete-branch`).
- [ ] T015 OpenProject workflow:
  ```bash
  ./tools/tracker.sh add-comment 150 --file specs/426-storage-circuit-breaker-blocking-timeout/report.md
  ./tools/tracker.sh mark-review 150
  ```
- [ ] T016 Запросить у владельца согласие на рестарт `karaoke-app` (nsa-i9: только владелец).

---

## Dependencies & Execution Order

- **Phase 1** — no deps.
- **Phase 2** — independent от Phase 1.
- **Phase 3** — после Phase 1.
- **Phase 4** — после Phase 1+2 (знать что описывать).
- **Phase 5** — после Phase 4.

### MVP Scope

T001–T005 (+T010–T016) достаточно для production-fix; T006–T009 обязательны из-за
Tier-1 Hard Gate Knowledge SSoT — в том же PR.

## Ready for implementation

tasks.md complete. Format validated. Dependencies mapped. MVP scope defined.

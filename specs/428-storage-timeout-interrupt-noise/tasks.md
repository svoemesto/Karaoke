# Tasks: StorageCircuitBreaker — убрать onErrorDropped/InterruptedException (Pass 428, #152)

**Input**: Design documents from `/specs/428-storage-timeout-interrupt-noise/`

## Phase 1: Core fix (US1, P1) 🎯 MVP

- [ ] T001 Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageApiClient.kt`:
  - Добавить `internal fun isInterruptWrapped(throwable: Throwable): Boolean` (проход по cause).
  - Добавить `internal fun <T> runBlockingMinioOrNull(block: () -> T): T?`:
    ловит `MinioException`→null; `InterruptedException`→(interrupt+nil);
    `RuntimeException` с interrupt-cause→(interrupt+nil), иначе проброс.
  - `statObjectOrNull`: тело через `runBlockingMinioOrNull { ... }`.
  - KDoc: Pass 428 (#152).

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Tests

- [ ] T002 [P] Create `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageTimeoutInterruptTest.kt`:
  - `isInterruptWrapped` — прямой `InterruptedException`, `RuntimeException(cause=InterruptedException)`,
    вложенный, не-interrupt → false.
  - `runBlockingMinioOrNull` — interrupt→null+флаг; MinioException→null; прочий RuntimeException→проброс.
  - no-onErrorDropped: `Hooks.onErrorDropped` перехватывает; `decorate` с loader'ом,
    который бросает `RuntimeException(InterruptedException)` после timeout → dropped не вызван.

- [ ] T003 Run `:karaoke-app:test --tests "*StorageTimeoutInterruptTest"` + `*StorageCircuitBreakerTest` — PASS.

## Phase 3: Knowledge & docs (SSoT)

- [ ] T004 [P] Modify `knowledge/domains/storage/components/storage-api-client.md` — секция Pass 428.
- [ ] T005 [P] Modify `docs/features/storage-metadata-cache.md` — V2.4.

## Phase 4: Validation & PR

- [ ] T006 Validation:
  ```bash
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*StorageTimeoutInterruptTest"
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel
  bash tools/check-no-mp4-mentions.sh
  bash tools/check-knowledge-structure.sh
  ```
- [ ] T007 Write `specs/428-storage-timeout-interrupt-noise/report.md`.
- [ ] T008 Commit / push / PR (`[tracker-claim-152]`).
- [ ] T009 CI `gh pr checks` → merge.
- [ ] T010 Tracker: `add-comment 152` + `mark-review 152`.
- [ ] T011 Запросить согласие владельца на рестарт `karaoke-app`.

## Dependencies & Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4.

### MVP Scope

T001–T003 + T006–T011; T004–T005 обязательны (Tier-1 Knowledge SSoT).

## Ready for implementation

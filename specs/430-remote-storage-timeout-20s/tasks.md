# Tasks: Таймаут доступа к удалённому хранилищу 5s → 20s (Pass 430, #154)

**Input**: `/specs/430-remote-storage-timeout-20s/{spec,plan}.md`

## Phase 1: Config change

- [ ] T001 Modify `karaoke-app/src/main/resources/application.yml`:
  - `file-exists-timeout-seconds: 5` → `20`; обновить комментарий (remote; Pass 430, #154).
- [ ] T002 [P] Modify `services/StorageCircuitBreaker.kt` — `@Value(...:5)` → `:20`.
- [ ] T003 [P] Modify `services/StorageCircuitBreakerConfig.kt` — оба `@Value(...:5)` → `:20`.
- [ ] T004 [P] Modify `services/StorageApiClient.kt` — `@Value(...:5)` → `:20`;
  поправить комментарий про OkHttp timeout.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Knowledge & docs

- [ ] T005 [P] Modify `knowledge/domains/storage/components/storage-api-client.md` —
  default 5s → 20s (Pass 426/428).
- [ ] T006 [P] Modify `docs/features/storage-metadata-cache.md` — V2.6.

## Phase 3: Validation & PR

- [ ] T007 Validation:
  ```bash
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*Storage*"
  GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel
  bash tools/check-knowledge-structure.sh
  ```
- [ ] T008 `report.md`; commit/push/PR; CI; merge; tracker `add-comment`/`mark-review`.
- [ ] T009 Запросить рестарт `karaoke-app` (владелец) + проверить
  `GET /api/health/circuit-breaker` → `timeoutSeconds: 20`.

## Dependencies

Phase 1 → 2 → 3.

## Ready for implementation

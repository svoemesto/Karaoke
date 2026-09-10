# Tasks: HealthReport Speedup

**Input**: Design documents from `/specs/364-healthreport-speedup/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), data-model.md

**Tests**: Не запрошены в спецификации (верификация — пользователем)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story (US1, US2, US3)
- Exact file paths in descriptions

---

## Phase 1: Research & Profiling Infrastructure

**Goal**: Подготовить инфраструктуру для замера latency и выявления bottleneck'ов.

- [X] T001 Найти и прочитать `HealthReport.kt:1119` — точка входа `getHealthReportList(song: Song)`. Определить все `KaraokeFileType` × `KaraokeFileTypeLocations` комбинации. Документировать текущий decision tree.
- [X] T002 [P] Прочитать `StorageMetadataCache.kt` (Pass 344) — понять как работает TTL-кеш для `fileExists`/`fileIsActual`.
- [X] T003 [P] Прочитать `StorageCircuitBreaker.kt` (Pass 351) — понять state machine: CLOSED → OPEN → HALF_OPEN. Зафиксировать API: `recordSuccess()`, `recordFailure()`, `canExecute()`.
- [X] T004 Добавить в `HealthReport.kt` companion object SLF4J logger `infra.health.report.duration` и `infra.health.circuit`. Использовать аналогичный формат как `infra.cache.storage`.
- [ ] T005 Замерить baseline: вызвать `getHealthReportList` для 3 разных песен (1 «OK», 1 с ошибками, 1 с missing files). Записать результаты в `specs/364-healthreport-speedup/research.md`.

---

## Phase 2: Foundational — Circuit Breaker Integration

**Goal**: Защитить MinIO-вызовы от cascade failure (FR-006).

**⚠️ CRITICAL**: Этот phase — prerequisite для всех user stories.

- [X] T006 Прочитать `HealthReport.kt:501` (`actionsLocalStorage`) и `HealthReport.kt:789` (`actionsRemoteStorage`). Определить, где именно вызываются `fileExists` / `fileIsActual`.
- [X] T007 Добавить `StorageCircuitBreaker` в `actionsLocalStorage`: обернуть вызовы `storageService.fileExists()` / `storageService.fileIsActual()` в `if (circuitBreakerLocal.canExecute())`. При `StorageUnavailableException` — возвращать `FATAL_ERROR` с текстом «Storage unavailable».
- [X] T008 Добавить `StorageCircuitBreaker` в `actionsRemoteStorage` аналогично T007. **NOTE**: remote storage уже защищён через `StorageApiClient` (Pass 351). circuit breaker добавлен ТОЛЬКО для local storage (`actionsLocalStorage`).
- [X] T009 Логировать circuit breaker state transitions в `infra.health.circuit`: CLOSED → OPEN, OPEN → HALF_OPEN, HALF_OPEN → CLOSED. Формат аналогичен `infra.cache.storage`.
- [ ] T010 Протестировать circuit breaker: остановить MinIO, вызвать HealthReport API, убедиться что ответ приходит за ≤10 мс с `error: "Storage unavailable"`.

---

## Phase 3: User Story 1 — Admin sees Songs table faster (Priority: P1) 🎯 MVP

**Goal**: Уменьшить latency `getHealthReportList` для «OK» песни с ~1 сек до ≤200 мс.

**Independent Test**: `curl "http://localhost:8899/api/health/getHealthReportList?songId=<ID>" | jq '.durationMs'` возвращает ≤200 мс.

- [X] T011 [P] Добавить per-file-type замеры времени в `getHealthReportList`: для каждого `KaraokeFileType` × `KaraokeFileTypeLocation` замерять `System.nanoTime()` и логировать: `type=<type> location=<location> operation=<op> durationMs=<ms>`. **ДONE**: 15 branches обёрнуты в `startNanos` + `durationLog.info`.
- [X] T012 [P] В `actionsLocalStorage`: убедиться что `StorageMetadataCache` используется для `fileExists`/`fileIsActual` (FR-003/004). Cache hit должен быть ≤5 мс. **NOTE**: `cachedFileExists` уже использует `StorageMetadataCache`.
- [X] T013 [P] В `actionsRemoteStorage`: убедиться что `StorageMetadataCache` используется для remote storage аналогично T012. **NOTE**: `cachedFileExists` уже использует `StorageMetadataCache`.
- [ ] T014 Проверить что параллельные вызовы `getHealthReportList` для разных песен не блокируют друг друга (FR-005). Замерить 5 параллельных запросов.
- [X] T015 Собрать `karaoke-app:bootJar`: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel`. **BUILD SUCCESSFUL**.
- [ ] T016 Замерить latency: 10 «OK» песен, вычислить median и p95. Документировать в `specs/364-healthreport-speedup/research.md`. Убедиться что ≤200 мс.

---

## Phase 4: User Story 2 — HealthReport async без блокировки UI (Priority: P2)

**Goal**: Cache miss возвращает `IN_PROGRESS` placeholder немедленно, cache fill асинхронно (FR-007).

**Independent Test**: Открыть Songs page после очистки кеша. UI показывает placeholder ≤100 мс. Повторный запрос — данные готовы.

- [ ] T017 [P] Прочитать `StorageMetadataCache.getOrCompute()` — понять текущую реализацию cache miss behavior.
- [ ] T018 [P] Модифицировать `StorageMetadataCache.getOrCompute()`: при cache miss возвращать `null` или placeholder немедленно, а loader выполнять асинхронно (использовать `kotlinx.coroutines.CoroutineScope` внутри Spring context, или `CompletableFuture.supplyAsync()`). Не блокировать HTTP-тред. Loader запускается в background executor, результат кладётся в кеш при готовности.
- [ ] T019 В `getHealthReportList` обрабатывать cache miss: если `StorageMetadataCache` вернул placeholder — возвращать `IN_PROGRESS` статус для этого `KaraokeFileType`, но НЕ блокировать остальные.
- [ ] T020 Протестировать: перезапустить `karaoke-app`, вызвать HealthReport для песни, убедиться что ответ приходит ≤100 мс с `IN_PROGRESS` для ещё не заполненного кеша.
- [ ] T021 Собрать и проверить: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar`

---

## Phase 5: User Story 3 — Repair-loop не блокирует UI (Priority: P3)

**Goal**: Repair-loop работает в фоне, UI не блокируется при просмотре Songs.

**Independent Test**: Запустить repair для 5 песен, параллельно открыть Songs page. Страница загружается без блокировки.

- [ ] T022 [P] Прочитать `HealthReport.kt:2259` (`startRepairAll`) и `HealthReport.kt:2153` (`recomputeAndBroadcast`). Понять thread model.
- [ ] T023 [P] Проверить что `StorageMetadataCache` уже используется в repair-loop (FR-003/004). Если нет — добавить.
- [ ] T024 Убедиться что `startRepairAll` запускается в отдельном thread lane (`THREAD_LANE_HEALTH_REPORT`) и не блокирует HTTP-requests к `/api/health/getHealthReportList`.
- [ ] T025 Протестировать: запустить repair для 5 песен, параллельно вызвать HealthReport API. Убедиться что второй запрос отвечает ≤200 мс.

---

## Phase 6: Polish & Metrics Validation

**Goal**: Финализировать логирование, проверить Success Criteria.

- [ ] T026 Проверить SC-002: замерить время загрузки страницы Songs с 20 песнями. Цель: ≤3 секунды.
- [ ] T027 Проверить SC-003: подсчитать количество уникальных MinIO-запросов на страницу через логи `infra.health.report.duration`. Цель: ≤40 (而不是 ~144).
- [ ] T028 Проверить SC-004: проверить cache hit rate ≥80% при повторной загрузке страницы. Смотреть логи `infra.cache.storage`.
- [ ] T029 Убедиться что KDoc覆盖率 для изменённых методов ≥50% (FR-006). KDoc для каждого публичного метода с `@see` на спеку.
- [ ] T030 Запустить линтеры: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck`
- [ ] T031 Собрать финальный `karaoke-app:bootJar` и `karaoke-web:bootJar`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Research)**: No dependencies — can start immediately
- **Phase 2 (Circuit Breaker)**: Depends on Phase 1 — CRITICAL, blocks all user stories
- **Phase 3 (US1 — MVP)**: Depends on Phase 2 — this is the MVP
- **Phase 4 (US2)**: Depends on Phase 2
- **Phase 5 (US3)**: Depends on Phase 2
- **Phase 6 (Polish)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: MVP after Phase 2 — latency measurement and optimization
- **US2 (P2)**: After Phase 2 — async cold-start fallback
- **US3 (P3)**: After Phase 2 — repair-loop non-blocking

### Parallel Opportunities

- Phase 1 tasks T001–T003 can run in parallel (reading different files)
- Phase 3 tasks T011–T013 can run in parallel (different code paths)
- Phase 4 tasks T017–T018 can run in parallel (understanding + implementation)

---

## Implementation Strategy

### MVP First (US1 + Circuit Breaker)

1. Complete Phase 1: Research + baseline measurement
2. Complete Phase 2: Circuit breaker integration
3. Complete Phase 3 (US1): Latency optimization
4. **STOP and VALIDATE**: Measure ≤200ms, document findings
5. Deploy/demo

### Incremental Delivery

1. Phase 1 → Phase 2 → Phase 3 (US1) → Test → Deploy (MVP)
2. Phase 4 (US2) → Test → Deploy
3. Phase 5 (US3) → Test → Deploy
4. Phase 6 (Polish) → Final validation

---

## Notes

- Тесты не включены: верификация выполняется пользователем через API и browser DevTools
- Все gradle-команды с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`
- Bottle neck'и выявляются через T005 и T016 (замеры latency)
- Circuit breaker использует существующий `StorageCircuitBreaker.kt` из Pass 351

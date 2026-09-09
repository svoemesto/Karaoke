# Tasks: Storage metadata cache (OpenProject #69)

**Input**: Design documents from `/specs/344-storage-metadata-cache/`
- [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/cache-stats-api.md](./contracts/cache-stats-api.md), [quickstart.md](./quickstart.md)

**Organization**: Tasks are grouped by user story. Tests included (knowledge compliance: PollingCacheTest follows `HealthReportRepairRaceTest.kt` pattern).

**Branch**: `344-storage-metadata-cache` (created by `tools/specify-bootstrap.sh` on 2026-09-09).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1=Story 1, US2=Story 2, US3=Story 3, US4=Story 4)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline project artifacts (branch, Knowledge registration) before any code work.

- [x] T001 Confirm branch `344-storage-metadata-cache` is active and clean (info-only — created by hook).
- [x] T002 [P] Register new SLF4J category `infra.cache.storage` in `knowledge/domains/monitoring/components/log-categories.md` (FR-010, see categories table). Add row: subsystem=`cache`, feature=`storage`, file=`karaoke-app/.../StorageMetadataCache.kt`, events=`cache:hit/miss/evicted`, default level=`INFO`.
- [x] T003 [P] Document the storage-metadata-cache feature plan/changelog entry in `docs/architecture-notes.md` (Pass 344 entry — short summary + pointers).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core cache infrastructure MUST be complete before User Stories can wire it in.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 Copy `PollingCache.kt` to `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PollingCache.kt` from `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt`. Change package to `com.svoemesto.karaokeapp.services`. Add KDoc block with `@see` reference to the original file (FR-006 KDoc coverage, FR-009 per-feature-doc reference). 80 строк, no logic changes.
- [x] T005 [P] Create `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt` (@Component, ~150 lines). Internal `CacheResult` sealed class (BooleanResult/FileInfoResult). Constructor with `@Value("\${storage.metadata.cache.local.ttlSeconds:300}")` and same for remote. Methods: `getFileExists(source, bucket, name, loader)`, `getFileIsActual(source, bucket, name, loader)`, `getFileInfo(source, bucket, name, loader)`, `stats()`. Internal `LongAdder` counters for local/remote hits/misses/evictions. String logger `infra.cache.storage` (FR-007, ADR `local-0005`).
- [x] T006 [P] Add `logging.level.infra.cache.storage: INFO` to `karaoke-app/src/main/resources/application.yml` (FR-007).
- [x] T007 [P] Create `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/PollingCacheTest.kt` (5 tests). Port pattern from `HealthReportRepairRaceTest.kt`. Tests: (1) miss returns loader result, (2) hit returns cached value, (3) TTL expiry causes re-load, (4) concurrent access thread-safe (10 threads × 100 calls), (5) `size()` and `clear()` work.
- [x] T008 Run `:karaoke-app:test --tests "PollingCacheTest"` and verify 5/5 PASS. If any FAIL — fix before proceeding. Required gradle command: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.services.PollingCacheTest"`.
- [x] T030 [P] Implement `maxEntries` hard cap в `PollingCache.kt` (NFR-002). При `size >= maxEntries` — eviction old entries (FIFO подход — удалить самые ранние вставки; соответствует существующему `cleanupEvery` lazy pattern). Default `maxEntries = 50_000` per-instance, настраивается через `@Value("\${storage.metadata.cache.maxEntries:50000}")` в `StorageMetadataCache` constructor и пробрасывается в оба `PollingCache` instances. Добавить unit-test в `PollingCacheTest.kt`: «6-й insert при maxEntries=5 → size ≤ 5, evicted counter увеличился».

**Checkpoint**: Foundation ready — `PollingCache` работает, `StorageMetadataCache` готов принимать вызовы, юнит-тесты зелёные.

---

## Phase 3: User Story 1 — Ускорение загрузки HealthReport и страницы Songs (Priority: P1) 🎯 MVP

**Goal**: webvue3 Songs page: с минут на <5 секунд при повторных открытиях в течение TTL.

**Independent Test**: Сценарий 1+2 из `quickstart.md`: первый запрос = miss, F5 в течение 5 минут = все hit, latency <1ms.

### Tests for User Story 1

- [x] T009 [P] [US1] Add 3 integration-style tests in `PollingCacheTest.kt` (or new file `StorageMetadataCacheTest.kt`) — verify: (a) `getFileExists` returns loader result on miss; (b) `getFileExists` returns cached on hit; (c) exception from loader propagates (FR-006: не маскируется).
- [x] T010 [P] [US1] Run `:karaoke-app:test --tests "*StorageMetadataCache*" --tests "*PollingCache*"` — verify 8+/8+ PASS.

### Implementation for User Story 1

- [x] T011 [US1] Add autowire of `StorageMetadataCache` to `HealthReport.kt:companion object` (constructor or @Autowired field). Depends on T005.
- [x] T012 [US1] Modify `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` — `actionsLocalStorage` (around line 501, see `health-report.md`). Replace direct `storageService.fileExists(bucket, name)`, `storageService.fileIsActual(...)`, `storageService.getFileInfo(...)` with `cache.getFileExists("LOCAL", bucket, name) { ... }`, etc. Add `decodeFileNameIfEncoded(name)` BEFORE the cache key (FR-012). Minimize diff — surgical changes only.
- [x] T013 [US1] Same as T012 for `actionsRemoteStorage` (around line 789 in `HealthReport.kt`) — same 3 call-sites, with `source = "REMOTE"`.
- [x] T014 [US1] Run `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel` — verify compile OK (post-edit health-check per AGENTS.md § "kara-post-edit").
- [x] T015 [US1] Run `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` (lint). Fix any ktlint violations. NOTE: actually `:karaoke-app:ktlintCheck` since the file is in karaoke-app.

**Checkpoint**: User Story 1 fully functional and testable — admin can verify by opening Songs page in webvue3, F5, expecting <5s.

---

## Phase 4: User Story 2 — Ускорение bulk-операций в HealthReport repair-loop (Priority: P1)

**Goal**: Repair-loop не делает повторных MinIO round-trip для тех же `(bucket, fileName)`.

**Independent Test**: Запустить `startRepairAll` на 1k песен с повторяющимися file keys; замерить ускорение vs без кеша.

**Note**: User Story 2 НЕ требует отдельного кода — он работает автоматически после User Story 1 (тот же wiring в `actionsLocalStorage` / `actionsRemoteStorage` обслуживает repair-loop). Verification через log grep.

### Implementation for User Story 2

- [x] T016 [US2] Verify no code changes needed: `grep -n "storageService.fileExists\|storageApiClient.fileExists" karaoke-app/.../HealthReport.kt | grep -v "actionsLocalStorage\|actionsRemoteStorage"` — если outside actionsLocalStorage/Remote, нужен код (ничего не должно быть).
- [x] T017 [US2] Document thread-safety verification: записать в `quickstart.md` пост-экспериментальный note после прогона `startRepairAll` на 1k песен с concurrent webvue3 refresh (Acceptance Scenario 2). Не блокирует — фоновый sanity-check.

**Checkpoint**: User Story 1 + 2 оба работают (один и тот же wiring).

---

## Phase 5: User Story 3 — Метрики кеша для мониторинга (Priority: P2)

**Goal**: `/api/health/cacheStats` показывает entries/hits/misses/hitRatio/evictions для local и remote.

**Independent Test**: `curl /api/health/cacheStats | jq .local.hitRatio` — после прогрева >0.9.

### Tests for User Story 3

- [x] T018 [P] [US3] Add test `CacheStatsControllerTest.kt` (или в `StorageMetadataCacheTest.kt`) — verify JSON shape: 6 полей в каждом из local/remote buckets + correct hitRatio calculation (cold-start → 1.0, hits=10 misses=0 → 1.0, hits=10 misses=10 → 0.5).

### Implementation for User Story 3

- [x] T019 [US3] Create `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CacheStatsController.kt` (~30 lines + 30 lines DTOs). `@RestController @RequestMapping("/api/health")`. Internal data classes: `CacheStatsDto(local, remote)` and `StatsBucket(entries, hits, misses, hitRatio, ttlSeconds, evictions)`. Single endpoint `GET /cacheStats` returning `cache.stats()`. KDoc с `@see specs/344-storage-metadata-cache/spec.md (FR-008)`.
- [x] T020 [US3] Add `hitRatio()` private helper in `StorageMetadataCache.kt` (T005 follow-up) — `hits.toDouble() / (hits + misses)`, округление до 4 знаков, edge case `(0,0) → 1.0`.
- [x] T021 [US3] Run `:karaoke-app:compileKotlin` + `:karaoke-app:test --tests "*CacheStats*"` — verify compile + 1+/1+ test PASS.
- [x] T022 [US3] Run `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar --parallel` — verify boot jar собирается (post-edit health-check, AGENTS.md).

**Checkpoint**: User Story 3 ready, метрики доступны для мониторинга.

---

## Phase 6: User Story 4 — Persisted (write-through) вариант (Priority: P3)

**Goal**: Deferred — НЕ в этом PR.

- [x] T023 [P] [US4] Document decision в `spec.md § Out of Scope`: write-through persistence отложено в отдельную спеку 345+. Reference: Assumption 8 в spec.md.

**Checkpoint**: User Story 4 явно отложен.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Per-feature doc + integration + final CI gate.

- [x] T024 [P] Create `docs/features/storage-metadata-cache.md` (~80 lines). Per-feature doc per FR-009 + Constitution Principle VI. Sections: Контекст (OpenProject #69, проблема 72k round-trip), Scope, API контракт (`StorageMetadataCache.get*` methods, `PollingCache` integration, `/api/health/cacheStats` endpoint), Метрики (LongAdder counters), Edge cases, Ловушки (5+).
- [x] T025 [P] Run `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` — verify all Kotlin OK. Fix any violations.
- [x] T026 [P] Run `bash tools/check-kdoc-coverage.sh` — verify ≥50% KDoc coverage (Constitution VI FR-006).
- [x] T027 [P] Run `pre-commit run --all-files` — verify 7/7 passes. Fix any violations.
- [x] T028 Run Scenarios 1-4 + N1/N2 из `quickstart.md`. Document results inline в `quickstart.md` (добавить секцию "Validation results" с реальными числами для SC-001/SC-002/SC-003/SC-004).
- [x] T029 [P] `git add` + `git commit` per AGENTS.md § "Git workflow": message format `feat(storage): add storage metadata cache (#344, OP#69)`. Не пушить без подтверждения владельца (CARO в AGENTS.md § Доступ — только по согласию для деплоя, но git push OK).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No deps. T001-T003 можно параллельно.
- **Foundational (Phase 2)**: Зависит от Setup (T002 = Knowledge update, T004 копирует PollingCache). T005/T006/T007 параллельны между собой и зависят от T004 (поэтапно).
- **User Stories (Phase 3+)**: Все зависят от Foundational.
  - US1 (Phase 3) — критично для MVP.
  - US2 (Phase 4) — verification, не код (зависит от US1 complete).
  - US3 (Phase 5) — независимо от US1/US2 (только зависит от US1 для `StorageMetadataCache.stats()`).
  - US4 (Phase 6) — declarative, only 1 task.
- **Polish (Phase 7)**: Зависит от US1+US2+US3 complete.

### User Story Dependencies

- **US1 (P1)**: Зависит от Phase 2 (T005 = StorageMetadataCache бина). Без зависимостей от других stories.
- **US2 (P1)**: Зависит от US1 (тот же wiring обслуживает repair-loop). Verification-only.
- **US3 (P2)**: Зависит от US1 (`StorageMetadataCache.stats()`). Не зависит от US2.
- **US4 (P3)**: Независимо, отложено.

### Within Each User Story

- Tests first (T009, T018).
- Models/before services (T005 → T011/T012).
- Services before endpoints (T019 follows T020).
- Core implementation before integration (Phase 7 после US1+US2+US3).
- Story complete before next priority.

### Parallel Opportunities

| Phase | Параллельные задачи |
|---|---|
| Phase 1 (Setup) | T002 (Knowledge reg), T003 (architecture-notes) — могут параллельно. T001 (info-only) |
| Phase 2 (Foundational) | T005 (StorageMetadataCache.kt), T006 (application.yml), T007 (PollingCacheTest.kt) — параллельно после T004. |
| Phase 3 (US1) | T009, T010, T015 — параллельно. T011 → блокирует T012 → блокирует T013. |
| Phase 5 (US3) | T018, T019, T020 частично параллельны. T021 — после T019+T020. |
| Phase 7 (Polish) | T024, T025, T026, T027 — параллельно (разные файлы/инструменты). |

---

## Parallel Example: User Story 1

```bash
# Внутри US1 параллельно можно запустить:
Task T009: "Add 3 tests getFileExists cases to PollingCacheTest.kt"
Task T010: "Run :karaoke-app:test --tests '*StorageMetadataCache*'"
Task T011: "Add autowire of StorageMetadataCache to HealthReport.kt"  # блокирует T012/T013
Task T015: ":karaoke-app:ktlintCheck"  # wait, это Phase 7

# Реально параллельны в US1: T009 + T011 + T015 (lint).
# T012, T013, T014 — sequentially after T011.
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1 (Setup)
2. Complete Phase 2 (Foundational — без этого нечего интегрировать)
3. Complete Phase 3 (User Story 1 — Songs page speed)
4. **STOP and VALIDATE**: ручной тест Songs page F5.
5. **MVP**: US1 alone уже устраняет главный болевой путь (минуты → секунды).

### Incremental Delivery

1. Setup + Foundational → cache бин работает (юнит-тесты).
2. Add US1 → cold-start first miss, F5 = all hits (MVP!).
3. Add US2 → verification только (no code change).
4. Add US3 → метрики видны в endpoint (мониторинг готов).
5. (Future) US4 → persistence — отдельный PR.

### Parallel Team Strategy

С одним разработчиком (current setup): строго последовательно по приоритетам.
С двумя: после Foundational — Dev A (US1+US2), Dev B (US3 параллельно).

---

## Notes

- **[P] tasks** = different files, no dependencies. Mark carefully.
- **[Story] label** ensures traceability — каждый task привязан к user story.
- **Каждая user story должна быть independently completable and testable** — verified on disk.
- **Verify tests FAIL before implementing** — T009 перед T012 (например, новый сценарий fileExists → loader).
- **Commit after each phase** (not after each task) — git history cleaner. Pass 282 dev-pc exception applies: на dev-pc можно без подтверждения.
- **Avoid vague tasks** — каждое описание имеет file path и specific change.
- **Avoid same-file conflicts** — T004, T005 — Sequential (T005 ссылается на T004). T012/T013 — sequential (тот же файл).
- **Метрики успеха** (для final validation в Phase 7 T028): SC-001 (≤5 сек на F5), SC-002 (≥90% hit-rate), SC-003 (cold-start без burst), SC-004 (zero errors в логах), SC-005 (grep `infra.cache.storage` возвращает события).

---

## Critical Reminders (per AGENTS.md, Constitution)

1. **Конвенция логирования** (ADR `local-0005`): string logger `infra.cache.storage`, НЕ `LoggerFactory.getLogger(Class)`.
2. **KDoc + @see на per-feature doc** (FR-006 + FR-009): обязательно на каждом публичном API + ссылка на `docs/features/storage-metadata-cache.md` после T024.
3. **gradle с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`** (NON-NEGOTIABLE) — во всех командах.
4. **Post-edit health-check** (AGENTS.md Pass 239+245): compile + ktlint + bootJar после ЛЮБОГО изменения кода.
5. **Без AOP** (Assumption 7 в spec.md): explicit DI autowire, не скрытый proxy.
6. **KaraokeProperties НЕ изменяются** (а не как `karaoke-web`'s @ConfigurationProperties): используем `@Value` напрямую.
7. **Knowledge cross-link**: после T024 запись в `docs/features/storage-metadata-cache.md` должна явно ссылаться на `knowledge/domains/caching/components/web-caches.md` (где живёт исходный PollingCache) для traceability.

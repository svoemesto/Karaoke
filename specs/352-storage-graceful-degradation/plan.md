# Implementation Plan: Storage graceful degradation (Pass 351, #71)

**Branch**: `[352-storage-graceful-degradation]` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/352-storage-graceful-degradation/spec.md`

**Note**: Этот план заполнен через `/speckit.plan` команду.

## Summary

Реализует OpenProject #71 «Graceful degradation для remote StorageApiClient (#65 follow-up)» — root-cause fix для #65 «Ошибка при проверке наличия файла в удаленном хранилище». Текущее поведение (`Mono.block()` с hardcoded 60s readTimeout) блокирует caller thread при network outage. Spec предлагает: configurable timeout + circuit breaker (in-memory, AtomicReference) + observability через `infra.cache.storage` SLF4J-категорию.

**Технический подход**:
- Новый `StorageCircuitBreaker` (@Component, in-memory) с 3-state FSM (CLOSED/HALF_OPEN/OPEN).
- `StorageApiClientImpl.fileExists/fileIsActual/getFileInfo` обернуты в `Mono<*>.timeout(...).onErrorReturn(empty)`.
- 3 новых `@Value` properties в `application.yml`: `storage.file-exists-timeout-seconds`, `storage.circuit-breaker-threshold`, `storage.circuit-breaker-cooldown-seconds`.
- Расширение `CacheStatsController` (Pass 344) — поле `networkFailures`.
- `infra.cache.storage` SLF4J-категория — новые события: `cache:network:failure`, `cache:circuit:state`.
- Unit-тесты: `StorageCircuitBreakerTest` (state transitions, concurrent).

## Technical Context

**Language/Version**: Kotlin 2.x, JVM (JDK 17). Module: `karaoke-app` (admin-side).
**Primary Dependencies**: spring-context 6.x, `io.minio:minio:8.6.0`, `org.slf4j.Logger`, `java.util.concurrent.atomic.AtomicReference`, `java.util.concurrent.atomic.LongAdder`. **NO new external dependencies** (no Resilience4j, no Failsafe — in-memory AtomicReference достаточно для in-process circuit).
**Storage**: N/A (in-memory only, per Q1 clarification).
**Testing**: JUnit 5 (existing pattern `HealthReportRepairRaceTest`).
**Target Platform**: Linux admin-machine (karaoke-app, hostname `nsa-i9`).
**Project Type**: Backend service in `karaoke-app/.../services/`.
**Performance Goals**: circuit breaker overhead ≤ 1ms per call (in-memory AtomicReference read).
**Constraints**: latency fileExists ≤ `file-exists-timeout-seconds` (5s default) at p99.
**Scale/Scope**: 18k песен × 4 типов × 1 remote MinIO. Cold start with dead network = N failures → circuit OPEN → fast-fail (ms, not min).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Проверка | Pass? |
|---|---|---|
| **I. Self-contained pipeline** | Фича работает на admin-machine, не вводит новых внешних зависимостей (no Resilience4j, in-house AtomicReference). | ✅ |
| **II. Raw JDBC + diff** | Фича НЕ использует БД (per Q1: in-memory only). | ✅ (N/A) |
| **III. SyncRegistry** | Фича НЕ трогает two-DB sync. | ✅ (N/A) |
| **IV. Async-очередь** | Фича работает в caller thread (для совместимости с `HealthReport.cachedFileExists` pattern). Заменяет blocking `.block()` на non-blocking `Mono.timeout().onErrorReturn()`. | ✅ |
| **V. Двух-фронтенд** | Только `StorageApiClientImpl` в `karaoke-app`. `StorageApiClientWeb` (karaoke-web) — НЕ затрагивается (out of scope). | ✅ |
| **VI. Code Standards (FR-006, FR-009)** | `StorageCircuitBreaker` — public API class, MUST иметь KDoc + `@see docs/features/storage-metadata-cache.md`. Per-feature doc: обновить существующий `docs/features/storage-metadata-cache.md` (V2 → V2.1). | ✅ (с TODO в tasks.md) |
| **VII. Cross-Machine Setup** | Только Kotlin файлы + `application.yml`; line endings не затрагиваются. | ✅ (N/A) |
| **VIII. Secrets** | Новые env-vars (`STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` etc.) НЕ секреты; только таймауты/counters. `KaraokeProperties` загружает через base64-файл (для админ-настроек). | ✅ (N/A) |
| **IX. Knowledge-first** | Pre-flight: 5 grep queries, 7 knowledge files consulted. Прецедент #339 учтён. | ✅ |

**Все 9 Principles PASS. Constitution Check = GREEN.**

## Project Structure

### Documentation (this feature)

```text
specs/352-storage-graceful-degradation/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── circuit-breaker-state.md
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
# В Karaoke multi-module (Spring Boot 3.x), модифицируется только:
karaoke-app/
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── services/
│   │   ├── StorageCircuitBreaker.kt            # NEW, ~80 lines
│   │   ├── StorageApiClient.kt                 # MODIFIED — обёртка file-* в circuit
│   │   ├── StorageMetadataCache.kt              # MODIFIED — ссылка на circuit
│   │   ├── KaraokeProperties.kt                # MODIFIED — +3 property
│   │   └── StorageApiClientWeb.kt              # НЕ затрагивается (out of scope)
│   └── controllers/
│       └── CacheStatsController.kt             # MODIFIED — +networkFailures
├── src/main/resources/
│   └── application.yml                        # MODIFIED — default values
└── src/test/kotlin/com/svoemesto/karaokeapp/
    └── services/
        └── StorageCircuitBreakerTest.kt        # NEW, ~150 lines
```

**Structure Decision**: модификация mono-module (karaoke-app). Никаких новых модулей. `StorageCircuitBreaker` — `@Component` Spring bean. Тесты в `karaoke-app/src/test/...`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет violations) | — | — |

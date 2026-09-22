# Implementation Plan: Разъединение circuit breaker local/remote

**Branch**: `429-split-local-remote-circuit-breakers` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/429-split-local-remote-circuit-breakers/spec.md`

## Summary

Один `StorageCircuitBreaker` смешивает два независимых бэкенда: защищает remote
(`StorageApiClientImpl.decorate`), но `HealthReport.actionsLocalStorage` читает его же
для local → remote-сбой даёт FATAL_ERROR на local; при этом local-путь вообще без
circuit. Делаем **два независимых bean** (`localStorageCircuitBreaker`,
`remoteStorageCircuitBreaker`) с общим config и раздельным состоянием; оборачиваем
local read-методы через blocking-API; привязываем HealthReport к правильному бэкенду.

## Technical Context

**Language/Version**: Kotlin (JVM 21), Spring Boot (DI via `@Bean`/`@Qualifier`)
**Primary Dependencies**: reactor (`Mono`), MinIO SDK, OkHttp, `Schedulers.boundedElastic`
**Storage**: local MinIO (`karaoke-storage:9000`) и remote (`storage.remote-endpoint`)
**Testing**: JUnit 5 (plain unit-тесты `StorageCircuitBreakerTest`)
**Target Platform**: Linux, контейнер `karaoke-app`
**Performance Goals**: local fail-fast <1ms при OPEN; hot path без регрессии
**Constraints**: backward-compatible config и reset-endpoint
**Scale/Scope**: `StorageCircuitBreaker` + config + 2 impl + HealthReport + 2 controllers

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle IX (Knowledge-first)** — pre-flight выполнен; найдено расхождение
  `health-report.md` с кодом → исправляем (SSoT).
- **Tier-1 Hard Gate — Knowledge SSoT** — обновляем `domains/storage/*`, `domains/health/*`,
  `domains/monitoring/*` + `docs/features/*`.
- **Tier-1 Hard Gate — Git CI-gate** — ветка `429-split-local-remote-circuit-breakers` + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.
- **R-04..R-11, R-43, R-44** — не затрагиваются.

Нарушений нет; Complexity Tracking не требуется.

## Project Structure

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/
├── StorageCircuitBreaker.kt          # MODIFY: storageType, executeBlocking
├── StorageCircuitBreakerConfig.kt    # NEW: два @Bean (local/remote)
├── StorageCircuitBreakerWiring.kt    # MODIFY: attach local + remote
├── StorageApiClient.kt               # MODIFY: @Qualifier remote + okhttp timeout
└── KaraokeStorageService.kt          # MODIFY: @Qualifier local + decorate read

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── HealthReport.kt                   # MODIFY: два static-поля, привязка
└── controllers/
    ├── CircuitBreakerController.kt   # MODIFY: reset?storage=
    └── CacheStatsController.kt       # MODIFY: оба состояния

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
└── StorageCircuitBreakerIsolationTest.kt   # NEW
```

## Phase 0 — Research

- Spring `@Configuration` с двумя `@Bean`, каждый получает один и тот же набор
  `@Value`-порогов; `@Qualifier` у потребителей.
- Для local (синхронный интерфейс `KaraokeStorageService`) нужен blocking-API:
  `executeBlocking(operation, loader, emptyValue)` — переиспользует acquire/record
  и interrupt-safe обработку (Pass 428).
- `HealthReport` companion — два volatile static-поля (`localStorageCircuitBreaker`,
  `remoteStorageCircuitBreaker`), attach через wiring.

## Phase 1 — Design

### StorageCircuitBreaker (ADD)

```kotlin
class StorageCircuitBreaker(
    ...существующие @Value...,
    val storageType: String = "remote",   // только логи/диагностика
) {
    /** Blocking-вариант для синхронного KaraokeStorageService (Pass 429). */
    fun <T : Any> executeBlocking(operation: String, loader: () -> T, emptyValue: T): T =
        when (acquire()) {
            Decision.FastFail -> {
                log.warn("cache:network:failure storage={} operation={} decision=FastFail (circuit open)", storageType, operation)
                emptyValue
            }
            Decision.Allow, Decision.Probe ->
                try {
                    loader().also { recordSuccess() }
                } catch (e: Exception) {
                    recordFailure(e)
                    emptyValue
                }
        }
}
```

### Config (NEW)

```kotlin
@Configuration
class StorageCircuitBreakerConfig {
    @Bean("localStorageCircuitBreaker")
    fun localStorageCircuitBreaker(... @Value пороги ...) =
        StorageCircuitBreaker(..., storageType = "local")

    @Bean("remoteStorageCircuitBreaker")
    fun remoteStorageCircuitBreaker(... те же @Value ...) =
        StorageCircuitBreaker(..., storageType = "remote")
}
```

### Логи

Все `cache:*` события добавляют `storage=<type>` (local|remote). `infra.health.circuit`
FastFail — `storage=local|remote` по бэкенду.

### Контроллеры

```kotlin
// GET /api/health/circuit-breaker → { local, remote }
// POST /api/health/circuit-breaker/reset?storage=local|remote|all (default all)
// GET /api/health/cacheStats → { local, remote, circuitBreaker: { local, remote } }
```

### HealthReport

```kotlin
@JvmStatic @Volatile var localStorageCircuitBreaker: StorageCircuitBreaker? = null
@JvmStatic @Volatile var remoteStorageCircuitBreaker: StorageCircuitBreaker? = null
// actionsLocalStorage → localStorageCircuitBreaker
// remote-проверки → remoteStorageCircuitBreaker
```

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).

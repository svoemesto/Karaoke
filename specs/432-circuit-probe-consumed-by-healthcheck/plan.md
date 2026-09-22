# Implementation Plan: Circuit probe не должен съедаться диагностикой

**Branch**: `432-circuit-probe-not-consumed-by-healthcheck` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

`HealthReport.actionsLocalStorage`/`actionsRemoteStorage` вызывают `cb.acquire()`,
который при истёкшем cooldown делает CAS `OPEN→HALF_OPEN` и возвращает
`Decision.Probe` — но реальный MinIO-вызов не запускает. `decorate` приходит позже
и получает `FastFail`. Probe не исполняется → watchdog возвращает OPEN → вечный цикл.

**Решение**: добавить нетранзишн-метод `isFastFail()` (только чтение state) и
использовать его в диагностике; `acquire()` остаётся за `decorate`/`executeBlocking`,
которые реально исполняют probe.

## Technical Context

**Language/Version**: Kotlin (JVM 21), Spring Boot
**Primary Dependencies**: reactor, MinIO SDK
**Testing**: JUnit 5
**Target Platform**: Linux, контейнер `karaoke-app`
**Constraints**: не менять `acquire()`, FSM, watchdog, reset
**Scale/Scope**: 1 метод + 2 call sites + тесты

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (см. spec.md); спека #405
  явно отложила эту задачу.
- **Tier-1 Hard Gate — Knowledge SSoT** — `storage-api-client.md`, `health-report.md`,
  `docs/features/*`.
- **Tier-1 Hard Gate — Git CI-gate** — ветка + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.

Нарушений нет.

## Project Structure

```text
karaoke-app/.../services/StorageCircuitBreaker.kt   # MODIFY: isFastFail()
karaoke-app/.../HealthReport.kt                      # MODIFY: acquire -> isFastFail (2)
karaoke-app/src/test/.../StorageCircuitBreakerTest.kt  # MODIFY/ADD
knowledge/domains/storage/components/storage-api-client.md
knowledge/domains/health/components/health-report.md
docs/features/storage-metadata-cache.md
```

## Phase 1 — Design

```kotlin
/**
 * Pass 432 (#156): нетранзишн-проверка для диагностики (HealthReport).
 * true — circuit сейчас отвергнет вызов (OPEN/HALF_OPEN); НЕ переводит
 * OPEN→HALF_OPEN и НЕ запускает probe (в отличие от [acquire]).
 */
fun isFastFail(): Boolean {
    val current = state.get()
    return current == State.OPEN || current == State.HALF_OPEN
}
```

HealthReport:

```kotlin
val cb = localStorageCircuitBreaker
if (cb != null && cb.isFastFail()) {
    circuitLog.warn("circuit=OPEN storage=local reason=Circuit breaker open")
    result.add(... FATAL_ERROR ...)
    return result
}
```

Аналогично для remote.

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).

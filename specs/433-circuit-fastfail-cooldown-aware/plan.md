# Implementation Plan: isFastFail cooldown-aware

**Branch**: `433-circuit-fastfail-cooldown-aware` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

Pass 432 сделал `isFastFail() = OPEN || HALF_OPEN`, из-за чего HealthReport
fast-fail'ит и после истечения cooldown → реальные вызовы не доходят до `decorate`
→ `acquire()` не вызывается → `OPEN→HALF_OPEN` не происходит → circuit залипает в
OPEN. Делаем `isFastFail()` cooldown-aware: `OPEN` → true только пока cooldown не
истёк; после — false (вызов пропускается к probe).

## Technical Context

**Language/Version**: Kotlin (JVM 21)
**Testing**: JUnit 5
**Constraints**: не менять `acquire()`, FSM, watchdog, reset
**Scale/Scope**: 1 метод + тесты + knowledge

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен.
- **Tier-1 Hard Gate — Knowledge SSoT** — `storage-api-client.md`, `docs/features/*`.
- **Tier-1 Hard Gate — Git CI-gate** — ветка + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.

Нарушений нет.

## Project Structure

```text
karaoke-app/.../services/StorageCircuitBreaker.kt   # isFastFail cooldown-aware
karaoke-app/src/test/.../StorageCircuitBreakerTest.kt
knowledge/domains/storage/components/storage-api-client.md
docs/features/storage-metadata-cache.md
```

## Phase 1 — Design

```kotlin
fun isFastFail(): Boolean {
    val current = state.get()
    return when (current) {
        State.CLOSED -> false
        State.HALF_OPEN -> true
        State.OPEN -> {
            val cooldownElapsed =
                (System.currentTimeMillis() - openedAtMs.get()) > cooldownSeconds * 1000L
            !cooldownElapsed
        }
    }
}
```

Семантика: `isFastFail()` теперь ровно «отвергнется ли вызов **прямо сейчас**,
если его отдать `decorate`» — без побочных эффектов.

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).

# Implementation Plan: StorageCircuitBreaker — убрать onErrorDropped/InterruptedException

**Branch**: `428-storage-timeout-interrupt-noise` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/428-storage-timeout-interrupt-noise/spec.md`

## Summary

Pass 426 добавил `subscribeOn(boundedElastic)` + `timeout(5s)`. При таймауте reactor
отменяет подписку и **прерывает** worker; MinIO `statObject` оборачивает
`InterruptedException` в `RuntimeException`, а `statObjectOrNull` ловит только
`MinioException` → исключение улетает после терминации Mono → `onErrorDropped` (ERROR + стек).

**Решение**: блокирующие MinIO-вызовы, используемые в `decorate`/`decorateOrEmpty`,
должны **не бросать** исключение при прерывании: ловить interrupt (`InterruptedException`
и `RuntimeException`-обёртку) → вернуть `null` + восстановить флаг прерывания. Выделяем
тестируемый `internal` helper `runBlockingMinioOrNull` + `isInterruptWrapped`.

## Technical Context

**Language/Version**: Kotlin (JVM 21), reactor-core 3.7.11, MinIO SDK 8.6.0
**Primary Dependencies**: `reactor.core.scheduler.Schedulers.boundedElastic`, `io.minio.MinioClient`
**Storage**: remote MinIO
**Testing**: JUnit 5 + `Hooks.onErrorDropped`
**Target Platform**: Linux, контейнер `karaoke-app`
**Performance Goals**: без изменения latency; убрать ERROR-шум
**Constraints**: не откатывать Pass 426; не менять публичные сигнатуры
**Scale/Scope**: один файл сервиса + его тесты + knowledge

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle IX (Knowledge-first)** — pre-flight выполнен, см. spec.md § Knowledge References.
- **Tier-1 Hard Gate — Knowledge SSoT** — обновляем `storage-api-client.md` + `docs/features/*`.
- **Tier-1 Hard Gate — Git CI-gate** — ветка `428-storage-timeout-interrupt-noise` + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.
- **R-04..R-11, R-43, R-44** — не затрагиваются.

Нарушений нет; Complexity Tracking не требуется.

## Project Structure

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/
└── StorageApiClient.kt        # MODIFY: runBlockingMinioOrNull, isInterruptWrapped, statObjectOrNull

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
└── StorageTimeoutInterruptTest.kt   # NEW: interrupt-aware helper + no-onErrorDropped
```

## Phase 0 — Research

- `MinioClient.statObject` → `CompletableFuture.get()`; interrupt → `RuntimeException(InterruptedException)`
  (`MinioClient.java:169`).
- `ThreadPoolExecutor.getTask()` глотает `InterruptedException` и сбрасывает флаг →
  восстановление `Thread.currentThread().interrupt()` безопасно для пула.
- `Hooks.onErrorDropped(Consumer)` заменяет дефолтный `log.error(...)` — тестируемо.

## Phase 1 — Design

```kotlin
internal fun isInterruptWrapped(throwable: Throwable): Boolean {
    var t: Throwable? = throwable
    while (t != null) {
        if (t is InterruptedException) return true
        t = t.cause
    }
    return false
}

internal fun <T> runBlockingMinioOrNull(block: () -> T): T? =
    try {
        block()
    } catch (_: MinioException) {
        null
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: RuntimeException) {
        if (isInterruptWrapped(e)) {
            Thread.currentThread().interrupt()
            null
        } else {
            throw e
        }
    }
```

`statObjectOrNull` → `runBlockingMinioOrNull { storageClient.statObject(...) }`.

### Контракт

| Ситуация | До | После |
|---|---|---|
| timeout-отмена (interrupt) | `RuntimeException(InterruptedException)` → onErrorDropped ERROR | `null`, флаг восстановлен, тихо |
| `MinioException` (not found) | `null` | `null` (без изменений) |
| прочий RuntimeException | проброс | проброс (без изменений) |

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).

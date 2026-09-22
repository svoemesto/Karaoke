# Implementation Plan: StorageCircuitBreaker — реальный timeout для блокирующего loader

**Branch**: `426-storage-circuit-breaker-blocking-timeout` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/426-storage-circuit-breaker-blocking-timeout/spec.md`

## Summary

`StorageCircuitBreaker.decorate`/`decorateOrEmpty` оборачивают блокирующий MinIO-вызов
(`Mono.fromCallable { storageClient... }`) в `.timeout(timeoutSeconds)`, но выполняют его
на вызывающем потоке, где затем вызывается `.block()`. Из-за этого `timeout` не срабатывает:
фактическое ожидание = OkHttp `connectTimeout` (15s) > `timeoutSeconds + watchdogBuffer` (15s),
watchdog навсегда переводит HALF_OPEN→OPEN, circuit не восстанавливается.

**Решение**: выполнять `loader()` на `Schedulers.boundedElastic()` (`subscribeOn`), чтобы
`.timeout()` получил реальную силу и прерывал ожидание на `timeoutSeconds`. Дополнительно —
выровнять OkHttp `connectTimeout`/`readTimeout` с `storage.file-exists-timeout-seconds`, и
исправить вводящий в заблуждение диагностический лог `storage=local` в `HealthReport`.

## Technical Context

**Language/Version**: Kotlin (JVM 21, Spring Boot)
**Primary Dependencies**: reactor-core (`Mono`, `Schedulers`), MinIO Java SDK (`io.minio`), OkHttp
**Storage**: MinIO (local `karaoke-storage:9000`, remote `storage.remote-endpoint`)
**Testing**: JUnit 5 (`org.junit.jupiter`), plain unit-тесты `StorageCircuitBreakerTest`
**Target Platform**: Linux, Docker-контейнер `karaoke-app`
**Project Type**: single backend service (`karaoke-app`)
**Performance Goals**: hot path `fileExists` (CLOSED) без регрессии > единиц ms; время ожидания
при сбое ≤ `timeoutSeconds` (+jitter), а не время блокировки
**Constraints**: backward-compatible API `decorate`/`decorateOrEmpty`; single replica
**Scale/Scope**: правка одного сервиса + его тестов + knowledge/docs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (self-contained pipeline)** — не затрагивается.
- **Principle II (raw JDBC + hash diff)** — не затрагивается.
- **Principle VII / VI (per-feature doc, FR-009)** — обновляем `docs/features/storage-metadata-cache.md`.
- **Principle IX (Knowledge-first)** — pre-flight выполнен, см. spec.md § Knowledge References.
- **Tier-1 Hard Gate — Knowledge SSoT** — обновляем `knowledge/domains/storage/*`,
  `knowledge/domains/monitoring/components/log-categories.md` в том же PR.
- **Tier-1 Hard Gate — Git CI-gate** — работа только в ветке
  `426-storage-circuit-breaker-blocking-timeout`, merge через PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — host `nsa-i9`/`nsa`: `karaoke-app` rebuild ✅,
  restart контейнера ❌ (только владелец).
- **R-43 (redirectErrorStream)** / **R-44 (MLT)** — не затрагиваются.
- **NFR-002 спеки #352** (LOCAL_STORAGE ≤200ms) — сохраняется.

Нарушений нет; Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/426-storage-circuit-breaker-blocking-timeout/
├── spec.md              # /speckit.specify output
├── plan.md              # этот файл
├── tasks.md             # /speckit.tasks output
└── report.md            # REQUIRED artifact для tracker workflow
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/StorageCircuitBreaker.kt        # MODIFY: subscribeOn(boundedElastic)
├── services/StorageApiClient.kt             # MODIFY: OkHttp timeout = file-exists-timeout
└── HealthReport.kt                          # MODIFY: storage=local|remote в FastFail-логе

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
└── StorageCircuitBreakerTest.kt             # MODIFY: +2 теста (blocking loader, HALF_OPEN success)
```

**Structure Decision**: single-project (существующий Gradle multi-module). Все правки —
в `karaoke-app`, без новых модулей/файлов production-кода.

## Phase 0 — Research

См. spec.md § Clarifications. Ключевые выводы:

1. `Mono.fromCallable { blocking }` исполняет `blocking` на потоке подписки; `subscribeOn`
   переносит подписку (и вызов) на `boundedElastic`, давая `timeout`-оператору возможность
   вернуть управление через `timeoutSeconds`.
2. `timeout` использует `Schedulers.parallel()` для таймера и не зависит от `subscribeOn` —
   комбинация `subscribeOn(boundedElastic).timeout(...)` корректна.
3. Блокирующий вызов на `boundedElastic`-потоке всё равно доработает до конца (отмена
   `fromCallable` невозможна), но вызывающий поток освобождается через `timeoutSeconds` —
   это и есть цель (защита caller thread).
4. Двойной бюджет (5s timeout + 15s OkHttp) устраняется выравниванием OkHttp-таймаута.

## Phase 1 — Design

### Изменение 1: `StorageCircuitBreaker.decorate` / `decorateOrEmpty`

```kotlin
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

private val blockingScheduler: Scheduler = Schedulers.boundedElastic()

// в decorate / decorateOrEmpty, ветка Allow/Probe:
loader()
    .subscribeOn(blockingScheduler)
    .timeout(Duration.ofSeconds(timeoutSeconds))
    .doOnSuccess { recordSuccess() }
    .doOnError { recordFailure(it) }
    .onErrorReturn(emptyValue)
```

- Scheduler создаётся один раз (поле), не per-call.
- `Schedulers.boundedElastic()` — глобальный singleton, отдельного `dispose()` не требует;
  при shutdown JVM/Spring ресурс освобождается. (Опционально: `@PreDestroy` no-op — не нужен.)

### Изменение 2: OkHttp timeouts в `StorageApiClientImpl`

```kotlin
// было: connectTimeout=15s, readTimeout=60s
.connectTimeout(fileExistsTimeoutSeconds, TimeUnit.SECONDS)
.readTimeout(fileExistsTimeoutSeconds, TimeUnit.SECONDS)
```

- Новый `@Value("\${storage.file-exists-timeout-seconds:5}")` параметр в конструкторе.
- `writeTimeout` (300s, для больших upload) — НЕ трогаем: он не участвует в `fileExists`.

### Изменение 3: Диагностический лог в `HealthReport`

В `actionsLocalStorage` FastFail-лог → `storage=local`; в `actionsRemoteStorage` (если есть
аналогичная проверка circuit) → `storage=remote`. Сигнатура helper'а лога не меняется.

### Контракт

| Метод | До | После |
|---|---|---|
| `decorate(op, loader, empty)` при блокирующем loader | ждёт фактическое время блокировки | возвращает `empty` через ≈`timeoutSeconds` |
| `decorate(op, loader, empty)` при быстром loader | как есть | +1 переключение потока, результат тот же |
| `fileExists` при OPEN circuit | FastFail | FastFail (без изменений) |

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).

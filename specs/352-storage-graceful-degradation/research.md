# Research: Storage graceful degradation (Pass 351, #71)

**Phase**: 0 — Outline & Research
**Date**: 2026-09-09
**Owner**: agent (Karaoke)
**Spec**: [spec.md](./spec.md)

## Контекст

OpenProject #65 «Ошибка при проверке наличия файла в удаленном хранилище» —
bug 2026-09-07, описывающий network degradation symptoms:
- `SocketTimeoutException: Connect timed out` при `fileExists`.
- `NoRouteToHostException: No route to host` — каскадные retries.
- «**Нужно чтобы при такой ошибке не было повторной попытки и программа шла дальше**».

**Частичные mitigations (уже в master)**:
- **Pass 343** (merge `e746a867`): perSong single-flight guard в `HealthReport.repair-loop` (НЕ root-cause для #65, но снижает symptom).
- **Pass 344/345** (merge `4b9d5989` / `81a3d1e1`): persistent metadata cache в `StorageMetadataCache`. Cache hit → 0 MinIO round-trip; cache miss — всё ещё blocking.

**Что НЕ сделано (root cause)**:
- `StorageApiClient.fileExists:343-349` использует `.block()` с hardcoded 60s readTimeout.
- При network outage caller thread блокируется на 60s.
- `fileIsActual` / `getFileInfo` НЕ защищены (cache-miss path).
- Нет circuit breaker, нет observability для network failure events.

## Resolved questions (clarification session 2026-09-09)

| Вопрос | Решение |
|---|---|
| Где хранить circuit breaker state? | **In-memory only** (AtomicReference). При рестарте `karaoke-app` state сбрасывается в CLOSED. |
| Какие методы защищаем? | **Все file-* методы** (`fileExists`, `fileIsActual`, `getFileInfo`) — shared circuit. |
| Поведение в OPEN state? | **Half-open probe**: после cooldown — probe call; success → CLOSED, fail → OPEN. |

## Technology decisions

### Decision 1: In-house `StorageCircuitBreaker` (vs Resilience4j/Failsafe)

**Rationale**:
- Существующий проект не использует Resilience4j (проверено: `build.gradle.kts`, `libs.versions.toml` — нет).
- Dependency addition = 200+ KB к JVM classpath, новые абстракции (CircuitBreakerRegistry, CircuitBreakerConfig), которые overkill для нашего scope.
- In-house `AtomicReference<State>` + `AtomicLong failureCount` = ~50 строк кода, zero deps.
- Совместим с существующим `StorageMetadataCache` (Pass 344/345) — просто ещё один bean.

**Alternatives considered**:
- **Resilience4j `io.github.resilience4j:resilience4j-circuitbreaker:2.2.0`** — зрелая библиотека, но добавляет complexity (Registry, Events, Config). Rejected: overkill.
- **Spring Retry (`spring-retry`)** — нет circuit breaker semantic, только retry. Rejected: не подходит.
- **Failsafe (`dev.failsafe`)** — меньше community, нет широкого adoption. Rejected.
- **Akka / Resilience4j Enterprise** — heavy. Rejected.

**Notes**:
- Прямой `AtomicReference<State>` (CLOSED/HALF_OPEN/OPEN) + `AtomicLong failureCount` + `AtomicLong openedAtMs` (для cooldown).
- `Mono.defer { circuit.tryAcquire(); source.timeout(...).onErrorReturn(empty) }` — Compose с Project Reactor (используется в `StorageApiClientImpl` для Mono-методов).

### Decision 2: Configuration через `@Value` + env-vars (vs `KaraokeProperties` base64)

**Rationale**:
- Существующий `StorageApiClientImpl` использует `@Value($$"${storage.key}")` из `application.yml`. Этот pattern работает и для новых properties.
- `KaraokeProperties` — base64-encoded file (`/sm-karaoke/system/Karaoke.properties`), load через `Karaoke.loadPropertiesMap()`. Используется для runtime-tunable на admin-машине.
- Для Pass 351 **используем `@Value`** + env-vars (как `StorageApiClientImpl` уже делает). Это проще и согласуется с текущим pattern.
- Если потребуется hot-reload через admin UI (Pass 002+ future) — можно переехать на `KaraokeProperties`.

**Alternatives considered**:
- **`KaraokeProperties` base64 file** — для admin-only настроек, требует файл-маяк. Rejected: overkill для read-only timeouts.
- **Hardcoded constants** — упрощает deployment, но нет runtime tuning. Rejected: spec требует FR-002 (configurable).

**Notes**:
- `application.yml` добавляет: `storage.file-exists-timeout-seconds`, `storage.circuit-breaker-threshold`, `storage.circuit-breaker-cooldown-seconds`.
- Override через env: `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10`.
- Default values: 5/5/30 (per Q1..Q3 answers).

### Decision 3: Half-open probe vs Hard reset

**Rationale** (per Q3):
- Michael Nygard "Release It!" (2007) — стандартный паттерн CB.
- Half-open probe безопаснее: одна новая ошибка не сбрасывает state. Без ложных positives.
- `failureCount` уже ведётся; при HALF_OPEN — single probe call:
  - Success → `state = CLOSED`, `failureCount = 0`.
  - Failure → `state = OPEN`, `openedAtMs = now()`.

**Alternatives considered**:
- **Hard reset по cooldown** — проще, но первый же вызов после cooldown пройдёт без timeout-guard. Rejected per Q3.
- **Manual reset only** — operator-driven, плохой опыт. Rejected per Q3.

**Notes**:
- Probe call ВЫПОЛНЯЕТСЯ с timeout (тот же, что и normal calls). Это предотвращает ситуацию, когда remote всё ещё down, и probe зависает на 60s.
- Если probe fail — `state = OPEN` + `openedAtMs = now()` (новый cooldown start).

## Dependency on existing knowledge

- `knowledge/domains/caching/components/web-caches.md` — `PollingCache` pattern (Pass 344), НЕ применим напрямую (мы НЕ TTL кеш).
- `knowledge/domains/storage/components/karaoke-storage-service.md` — `KaraokeStorageServiceImpl` (local) использует hardcoded OkHttpClient timeouts. Out of scope.
- `knowledge/domains/monitoring/components/log-categories.md` — категория `infra.cache.storage` уже зарегистрирована. Расширяем.
- `specs/316-search-timeout-configurable/plan.md` — пример specs на timeout config (ref).
- `specs/349-tracker-must-link/report-65.md` — context для #65 root cause.

## Technology choice summary

| Aspect | Choice | Rationale |
|---|---|---|
| CB implementation | In-house `AtomicReference` | Zero deps, ~50 LOC |
| Configuration | `@Value` + env-vars | Совместим с `StorageApiClientImpl` pattern |
| State model | CLOSED / HALF_OPEN / OPEN | Nygard "Release It!" standard |
| Half-open | Probe call (с timeout) | Безопасный recovery |
| Persistence | In-memory only | Per Q1 |
| Scope | Все file-* методы | Per Q2 (shared circuit) |
| Observability | `infra.cache.storage` SLF4J | Уже зарегистрирована |

## Open questions for Implementation (will be resolved in plan)

- Q1: thread-safety контракт `circuit.tryAcquire()` при высокой concurrency. Plan-stage.
- Q2: точные счётчики (failureCount для CLOSED→OPEN transition) + edge cases (concurrent calls). Plan-stage.
- Q3: как интегрировать в `StorageMetadataCache` (Pass 344/345) без breaking changes. Plan-stage.

## Resolved (Stage 2 clarifications)

1. ✅ In-memory only persistence (no DB).
2. ✅ All file-* methods use shared circuit.
3. ✅ Half-open probe pattern (not hard reset).

**All clarifications resolved. Spec ready for Phase 1 (Design) + tasks.md.**

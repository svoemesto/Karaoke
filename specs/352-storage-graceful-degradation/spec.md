# Feature Specification: Storage graceful degradation (#65 root-cause fix)

**Feature Branch**: `[352-storage-graceful-degradation]`
**Created**: 2026-09-09
**Status**: Draft
**Input**: `Работа над задачей #71 в трекере OpenProject` (Pass 350 short-form expansion)

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#71` (Graceful degradation для remote StorageApiClient (#65 follow-up)).
- **Title**: «Graceful degradation для remote StorageApiClient».
- **Created in OpenProject**: 2026-09-09.
- **Status на момент старта**: New (Pass 350 hook auto-claim через `tracker.sh claim-issue 71` — In progress, assignee=ai-agent).
- **Связь**: это implementation для #65 «Ошибка при проверке наличия файла в удаленном хранилище» (root-cause fix; см. `specs/349-tracker-must-link/report-65.md`).

**Workflow**:
1. **Claim**: `bash tools/tracker.sh claim-issue 71` (выполнено через Pass 350 hook).
2. **Add comment с отчётом** (после merge): `bash tools/tracker.sh add-comment 71 --file specs/352-storage-graceful-degradation/report.md`.
3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 71`.
4. **Close** (owner): `bash tools/tracker.sh close-issue 71` после Pass 351 merge.
5. **Close #65** (owner, optional): после успешного #71 — `bash tools/tracker.sh close-issue 65` (root cause наконец починен).

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -rn "StorageApiClient\|fileExists\|StorageMetadataCache" knowledge/` → 10 hits. См. `knowledge/domains/storage/components/{storage-api-client.md, karaoke-storage-service.md, ...}`.
  2. `grep -ri "circuit.b\|timeout.config\|KaraokeProperties" knowledge/` → 8 hits. См. `knowledge/domains/integration/components/external-api-clients.md`, `knowledge/domains/processing/components/two-db-sync.md` (use `@ConfigurationProperties` pattern).
  3. `grep -l "KaraokeProperties\|maxFileSize\|@ConfigurationProperties" karaoke-app/` → 5 hits. Properties через base64-файл (`/sm-karaoke/system/Karaoke.properties`).
  4. `grep -rn "StorageApiClient\|fileExists\|StorageMetadataCache" knowledge/` → **тот же результат** (dedup, подтверждает знание домена).
  5. `find knowledge -name "race-fixed-65.md"` → найден. **Pass 343 fixed только perSong race в HealthReport, НЕ root cause #65**.

### Knowledge files consulted

- **`knowledge/domains/health/components/race-fixed-65.md`** (Pass 343) — fixed только perSong single-flight guard в `HealthReport.repair-loop`. **НЕ root-cause fix для #65** (см. сценарии 4-5 в разделе "Защита от race сценариев"). Каскадные retries уже исключены, но `Mono.block()` на 60s readTimeout остался.
- **`knowledge/domains/storage/components/storage-api-client.md`** — current `StorageApiClient.fileExists` impl, hardcoded timeouts (15s/60s/300s), `StorageApiClientWeb` — отдельная WebClient-реализация.
- **`knowledge/domains/storage/components/karaoke-storage-service.md`** — `fileExists` для local MinIO (также blocking, но local-канал быстрее).
- **`knowledge/domains/storage/domain.md`** § "Hot paths" — таблица частот с Pass 344/345 cache (Cache hit → 0 round-trip; cache miss → MinIO).
- **`knowledge/domains/integration/components/external-api-clients.md`** — паттерн `KaraokeProperties` для config (URL, token, timeout, retry).
- **`knowledge/domains/monitoring/components/log-categories.md`** — зарегистрирована категория `infra.cache.storage` (Pass 344). В этой спеке — расширяем на `infra.cache.storage.network.failure`.
- **`knowledge/domains/processing/components/two-db-sync.md`** — пример `@ConfigurationProperties` pattern + динамические интервалы через SpEL.
- **`specs/316-search-timeout-configurable/contract*`** — пример specs/316 (timeout config). Полезен как reference для spec.md структуры.

### Прецедент

2026-09-09, OpenProject #65 «Ошибка при проверке наличия файла в удаленном хранилище» (Pass 343, 344, 345): смягчено через perSong single-flight guard + persistent cache, но root cause (60s blocking `.block()` при network outage) НЕ починен. OpenProject #71 создан как follow-up. Эта спека — implementation #71.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — File check во время network outage (Priority: P1)

**Описание**: Admin работает с Songs page в webvue3. Сеть до remote MinIO нестабильна (NoRouteToHostException / SocketTimeoutException). `HealthReport.cachedFileExists` идёт в cache-miss, потом в `StorageApiClient.fileExists`. Сейчас блокируется на 60s (readTimeout). После Pass 351: блокируется максимум на `fileExistsTimeoutSeconds` (5s по умолчанию), потом `false` (graceful degradation), и admin продолжает работать без "не затормаживала остальное" (см. текст #65). Все file-* методы (`fileExists`, `fileIsActual`, `getFileInfo`) используют один shared circuit.

**Why P1**: основная боль из #65. Без фикса страница Songs не отвечает часами при network outage.

**Independent Test**: Запустить `bash tools/with-dead-network.sh fileExists` (mock-скрипт) → должен вернуть `false` за < 5s, не за 60s.

**Acceptance Scenarios**:

1. **Given** remote MinIO недоступен (network down), **When** `StorageApiClient.fileExists(bucket, name)` вызывается, **Then** возвращает `false` за время ≤ `fileExistsTimeoutSeconds` (5s default), а НЕ 60s.
2. **Given** cache hit в `StorageMetadataCache`, **When** `cachedFileExists` вызывается, **Then** возвращает значение БЕЗ обращения к MinIO (НЕ блокируется на network — это уже работает с Pass 344/345).
3. **Given** circuit breaker OPEN (после N consecutive failures), **When** `StorageApiClient.fileExists` вызывается, **Then** возвращает `false` сразу (без попытки MinIO-call) — fast-fail.
4. **Given** circuit breaker OPEN, **When** cooldown истекает (например, 30s), **Then** следующий вызов — проба (HALF_OPEN); если success → CLOSED, если fail → снова OPEN.
5. **Given** `fileExists` fails (network или circuit OPEN), **When** в логи пишется, **Then** счётчик `infra.cache.storage.network.failure` инкрементируется И structured log (WARN) с `key=..., error=..., durationMs=...`.

---

### User Story 2 — Configuration (Priority: P2)

**Описание**: Через `KaraokeProperties` (admin UI) или env-vars можно настроить:
- `fileExistsTimeoutSeconds` (default 5).
- `circuitBreakerThreshold` (consecutive failures до OPEN, default 5).
- `circuitBreakerCooldownSeconds` (default 30).

Без рестарта admin-UI нельзя, но env-vars + restart OK.

**Why P2**: для production обязательно, но первый PR может зафиксить hardcoded defaults.

**Acceptance Scenarios**:

1. **Given** env `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10`, **When** `karaoke-app` стартует, **Then** `StorageMetadataCache.circuitBreaker` инициализирован с timeout 10s.
2. **Given** env `STORAGE_CIRCUIT_BREAKER_THRESHOLD=3`, **When** 3 последовательных `SocketTimeoutException`, **Then** circuit OPEN.

---

### User Story 3 — Observability endpoint (Priority: P3)

**Описание**: Endpoint `GET /api/health/circuit-breaker` возвращает текущее состояние circuit breaker'а (closed/half_open/open), счётчики failures, last failure timestamp.

**Why P3**: nice-to-have, отложить если не успеем.

**Acceptance Scenarios**:

1. **Given** circuit breaker в состоянии CLOSED, **When** GET `/api/health/circuit-breaker`, **Then** JSON `{state: "CLOSED", failureCount: 0, lastFailure: null, threshold: 5, cooldownSeconds: 30}`.
2. **Given** circuit breaker в OPEN, **Then** JSON `{state: "OPEN", failureCount: 5, lastFailure: "2026-09-09T15:30:00Z", ...}`.

---

### Edge Cases

- **`fileExists` на зависшем MinIO (read delay 30s)**: timeout 5s срабатывает, exception → breaker counter++, возвращается `false`. Caller получает ответ через 5s, не 60s.
- **`fileExists` на OK remote**: normal path, breaker counter=0.
- **Параллельные `fileExists` (10 потоков)**: каждый независимо проверяет circuit, все получают false если OPEN. Параллельного зависания нет.
- **Cold start (circuit неизвестен)**: первый fail → `failureCount=1`, не открывает сразу.
- **Partial degradation** (1 из 5 запросов fail): breaker не OPEN (threshold=5). Caller получает true/false от MinIO.
- **Health check vs real `fileExists`**: circuit разделяемый — `infra.prod.db` health check НЕ использует этот circuit.

## Requirements *(mandatory)*

### Functional

- **FR-001**: System MUST применять configurable timeout к каждому `StorageApiClient.file*` (fileExists, fileIsActual, getFileInfo) HTTP/SDK вызову. Default 5s. Env: `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` (название сохранено для обратной совместимости, applies ко всем file-*).
- **FR-002**: System MUST вести circuit breaker с 3 состояниями: CLOSED, HALF_OPEN, OPEN. **In-memory only** (AtomicReference — при рестарте karaoke-app state сбрасывается в CLOSED). Конфигурация: `STORAGE_CIRCUIT_BREAKER_THRESHOLD` (default 5), `STORAGE_CIRCUIT_BREAKER_COOLDOWN_SECONDS` (default 30). Применяется ко всем file-* методам (shared circuit).
- **FR-003**: System MUST возвращать `false` из `fileExists`/`fileIsActual` (НЕ throw) при: timeout, SocketTimeoutException, NoRouteToHostException, circuit OPEN, HALF_OPEN-then-fail. `getFileInfo` возвращает `null` в тех же условиях.
- **FR-004**: System MUST логировать сетевые сбои в SLF4J-категорию `infra.cache.storage` с ключами `cache:network:failure` и `cache:circuit:state` (при изменении state).
- **FR-005**: System MUST инкрементировать счётчик `infra.cache.storage.network.failure` (LongAdder) на каждое failure. Endpoint `/api/health/cacheStats` (Pass 344) расширяется с полем `networkFailures` per source.
- **FR-006**: System MUST НЕ использовать `Mono.block()` в caller thread для file-* методов. Вместо — `Mono<*>.timeout(...)` + `.onErrorReturn(empty)` (non-blocking fallback).
- **FR-007**: System MUST поддержать manual reset circuit breaker'а. Reset происходит автоматически в `HALF_OPEN` → `CLOSED` при первом success после cooldown (per Q3). Дополнительно — admin endpoint `POST /api/health/circuit-breaker/reset` (P3 optional).
- **FR-008**: System MUST добавить optional endpoint `GET /api/health/circuit-breaker` (P3 — может быть отложено).

### Non-Functional

- **NFR-001**: `fileExists` timeout НЕ блокирует caller thread более чем `fileExistsTimeoutSeconds` (5s default).
- **NFR-002**: Circuit breaker state changes атомарны (AtomicReference или synchronized). Параллельные thread'ы видят consistent state.
- **NFR-003**: Performance overhead circuit breaker ≤ 1ms per call (in-memory AtomicReference).
- **NFR-004**: Observability: `infra.cache.storage.network.failure` и `infra.cache.storage.circuit.state` логируются в существующий SLF4J setup (см. `ADR local-0005`).
- **NFR-005**: Concurrent `fileExists` (100 потоков) на broken MinIO → circuit OPEN за ≤ 5 failures (concurrent), нет thread leak.

### Key Entities

- **`StorageCircuitBreaker`** — NEW bean (`@Component`) в `karaoke-app/.../services/`. AtomicReference<State> где State = `{CLOSED, HALF_OPEN, OPEN}`. `failureCount: AtomicLong`, `lastFailureAt: AtomicLong` (epoch ms). Метод `tryAcquire(): Decision` (ALLOW_OPEN, FAST_FAIL).
- **`StorageMetadataCache.circuit`** — поле в существующем кеше (Pass 344/345), инициализируется через `@Value` или `@PostConstruct` autowire `StorageCircuitBreaker`.
- **`KaraokeProperties.storageFileExistsTimeoutSeconds`** — new property. Default 5.
- **`KaraokeProperties.storageCircuitBreakerThreshold`** — new property. Default 5.
- **`KaraokeProperties.storageCircuitBreakerCooldownSeconds`** — new property. Default 30.

## Success Criteria *(mandatory)*

- **SC-001**: При `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=5` и dead remote MinIO, `fileExists` возвращает `false` за время ≤ 5s (вместо текущих 60s). Verified через `bash tools/measure-fileExists-latency.sh`.
- **SC-002**: Circuit breaker переходит в OPEN после `STORAGE_CIRCUIT_BREAKER_THRESHOLD=5` consecutive failures. Каждый последующий `fileExists` возвращает `false` мгновенно (< 1ms) без MinIO-call.
- **SC-003**: После `cooldownSeconds=30` без failures, circuit автоматически переходит HALF_OPEN → CLOSED при первом success.
- **SC-004**: `infra.cache.storage.network.failure` счётчик виден в `/api/health/cacheStats` (`networkFailures` поле).
- **SC-005**: `OpenProject #65` может быть закрыт после Pass 351 merge: original cascade-retries + slow-blocking symptoms больше не воспроизводятся.

## Assumptions

1. **Network is the primary failure mode** — DNS, SocketTimeout, NoRouteToHost. Disk-full, 5xx errors считаются transient и НЕ открывают circuit.
2. **`StorageApiClientWeb` (karaoke-web) НЕ затрагивается** — этот fix только в `StorageApiClientImpl` (admin). `StorageApiClientWeb` используется на проде только для StemJobs и upload; можно сделать отдельный circuit для web позже.
3. **`fileIsActual` и `getFileInfo` используют тот же circuit** — они идут через `StorageApiClient`. Если хочется отдельный — вынести в NFR.
4. **Cold start circuit state = CLOSED** — нет warmup. Первый failure → counter=1, не OPEN.
5. **`StorageMetadataCache` (Pass 344/345) остаётся в основе** — circuit breaker ПЕРЕД `fileExists` MinIO call, чтобы в OPEN state кеш-miss не инициировал MinIO-call.

## Out of Scope

- `StorageApiClientWeb` circuit (только `Impl`).
- `KaraokeStorageService.fileExists` circuit (local MinIO — быстрее, реже падает, отдельная задача).
- 5xx error handling (можно добавить в future iteration).
- Circuit breaker visualization в admin UI.
- Distributed circuit (multi-replica — не актуально, 1 admin-машина).

## Migration Path

### Что нужно для существующих мест

- `StorageApiClientImpl.fileExists` — обернуть в circuit + timeout.
- `StorageMetadataCache` (Pass 344/345) — ссылается на circuit (либо autowire, либо статический).
- `KaraokeProperties` — добавить 3 новых property.
- `CacheStatsController` (Pass 344) — расширить JSON с `networkFailures`.
- `infra.cache.storage` SLF4J-категория — добавить новые события.

### Что НЕ нужно для существующих

- 0 spec-миграций (Pass 349 grandfather rule).
- `CacheStatsController` обратная совместимость (новые поля — additive, старые клиенты игнорируют).

## Реализация (План будет в следующей фазе /speckit.plan)

### Шаги

1. `StorageCircuitBreaker.kt` (NEW, ~80 LOC, AtomicReference-based FSM).
2. `StorageApiClientImpl.fileExists` — обернуть в `Mono<Boolean>.timeout(...).onErrorReturn(false)` через `CircuitBreaker.decorate` (или custom).
3. `KaraokeProperties` — добавить 3 property (default 5, 5, 30).
4. `CacheStatsController` (Pass 344) — расширить JSON с `networkFailures`.
5. SLF4J-категория `infra.cache.storage` — добавить `cache:network:failure` и `cache:circuit:state` события (см. `log-categories.md`).
6. Unit-тесты: `StorageCircuitBreakerTest.kt` (state transitions).
7. (Optional P3) `CacheAdminController` — добавить `GET /api/health/circuit-breaker`.
8. Commit + PR + merge.

### Файлы изменены (план)

```
A  karaoke-app/.../services/StorageCircuitBreaker.kt   # NEW ~80 LOC
A  karaoke-app/.../test/.../services/StorageCircuitBreakerTest.kt  # NEW ~150 LOC
M  karaoke-app/.../services/StorageApiClient.kt       # fileExists обёрнут
M  karaoke-app/.../services/KaraokeProperties.kt     # +3 property
M  karaoke-app/.../controllers/CacheStatsController.kt  # +networkFailures
M  knowledge/domains/monitoring/components/log-categories.md  # +cache:network:failure, +cache:circuit:state
M  docs/features/storage-metadata-cache.md          # обновить V2 → V2.1
```

## Validation

| Проверка | Ожидаемо |
|---|---|
| `bash tools/measure-fileExists-latency.sh` (NEW) | latency ≤ 5s при dead network |
| `python3 tools/check-spec-issue-link.py` | exit 0 (1/1 modern) |
| `bash tools/check-knowledge-structure.sh` | 9/9 OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:test --tests "*StorageCircuit*" ` | 6/6 PASS (state transitions) |
| `:karaoke-app:bootJar` | OK |
| `gh pr checks <#352>` | 9/9 PASS |

## Rollback

`git revert <merge-commit>`:
- `StorageApiClient.fileExists` откатывается к hardcoded `.block()` 60s.
- `KaraokeProperties` новые property не загружаются.
- Владелец может открыть #65 обратно.

## Clarifications

### Session 2026-09-09

- Q: Где хранить circuit breaker state? → A: In-memory only (AtomicReference). При рестарте `karaoke-app` state сбрасывается в CLOSED. Просто, без БД, без write-thrashing.
- Q: Какие методы защищаем circuit breaker'ом? → A: Все file-* методы (`fileExists`, `fileIsActual`, `getFileInfo`) — shared circuit. Лучше protection, единая логика.
- Q: Поведение circuit в OPEN state? → A: Half-open probe. После cooldown — probe call: success → CLOSED, fail → OPEN. Безопасный recovery, одна ошибка не сбрасывает state.

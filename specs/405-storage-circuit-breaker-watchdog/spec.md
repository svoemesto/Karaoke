# Feature Specification: StorageCircuitBreaker watchdog + manual reset (Pass 372, #131)

**Feature Branch**: `[405-storage-circuit-breaker-watchdog]`
**Created**: 2026-09-17
**Status**: Draft
**Input**: Production-наблюдение 2026-09-17 — circuit breaker застрял в HALF_OPEN на 8+ минут после 5×SocketTimeoutException. Follow-up на OpenProject #71 (Pass 351, спека #352).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#131` («StorageCircuitBreaker: watchdog + manual reset»).
- **Title**: «StorageCircuitBreaker: watchdog + manual reset (Pass 372 follow-up #71)».
- **Created in OpenProject**: 2026-09-17.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 131` (выполнено).
  2. **Add comment с отчётом** (после merge): `bash tools/tracker.sh add-comment 131 --file specs/405-storage-circuit-breaker-watchdog/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 131`.
  4. **Close** (owner, после Pass 372 merge + рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 131`.
- **Связь**: follow-up на #71 (Pass 351) и #463 (Pass 364) — обе имплементации `StorageCircuitBreaker` страдают от одного root cause.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-17
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -rn "StorageCircuitBreaker\|HALF_OPEN\|circuit" knowledge/` → 7 hits (см. `knowledge/domains/storage/components/storage-api-client.md`, `knowledge/domains/storage/domain.md`, `knowledge/domains/monitoring/components/log-categories.md`, `knowledge/domains/health/components/health-report.md`).
  2. `grep -rn "circuit breaker\|circuit-breaker" specs/` → 5 hits (см. `specs/352-storage-graceful-degradation/spec.md`, `tasks.md`, `contracts/circuit-breaker-state.md`, `data-model.md`).
  3. `grep -rn "watchdog\|probe stuck\|HALF_OPEN hangs" knowledge/ specs/` → 0 hits. **Концепция watchdog'а — новая**, нет прецедента в knowledge base.
  4. `grep -rn "recordFailure\|recordSuccess\|HALF_OPEN\|state.compareAndSet" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt` → подтверждает текущую реализацию FSM без watchdog'а.
  5. `grep -rn "cb.acquire()\|storageCircuitBreaker" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` → подтверждает, что HealthReport использует `acquire()` напрямую (минуя `decorate`), что объясняет, почему probe не запускается через этот путь.

### Knowledge files consulted

- **`knowledge/domains/storage/components/storage-api-client.md`** — Pass 351 описание circuit breaker, FSM, decorate pattern. Нужно дополнить секцией «Pass 372: watchdog».
- **`knowledge/domains/storage/domain.md`** — Hot paths, FR-006 (circuit breaker IMPLEMENTED). Нужно cross-link.
- **`knowledge/domains/monitoring/components/log-categories.md`** — категория `infra.cache.storage`, события `cache:network:failure`, `cache:circuit:state`. Нужно добавить `cache:circuit:watchdog`.
- **`knowledge/domains/health/components/health-report.md`** — State machine упоминается, но без деталей HALF_OPEN edge case.
- **`specs/352-storage-graceful-degradation/spec.md`** (FR-002, FR-007) — задача #71, спека Pass 351. FR-007 «Дополнительно — admin endpoint `POST /api/health/circuit-breaker/reset` (P3 optional)» — это то, что мы делаем в этой спеке как **hard requirement** (а не P3 optional).
- **`specs/352-storage-graceful-degradation/tasks.md`** — T015 «Create `CircuitBreakerController.kt` (P3 endpoint)» помечен `[x]`, но файл **НЕ существует** (`POST /api/health/circuit-breaker/reset` → 404). Это ошибка в чекбоксах. В этой спеке — действительно создаём файл и добавляем watchdog.

### Прецедент

2026-09-17 13:11–13:20 на nsa-i9: circuit breaker открылся после 5×SocketTimeoutException к REMOTE MinIO, перешёл в HALF_OPEN через cooldown 30s, но **probe завис навечно**. State=HALF_OPEN уже 8+ минут, `lastFailureAt=1789639926202` не двигается с момента открытия. MinIO доступен (health=200, 0.1–0.2s latency, 66% disk used), но `acquire()` для всех потоков возвращает `FastFail` — и новый probe не запускается, потому что в HALF_OPEN только один поток может стартовать probe, а его callback (`recordSuccess`/`recordFailure`) не доходит (Mono.timeout race / GC pause / thread death).

Production-impact: HealthReport.actionsLocalStorage массово создаёт `FILE_VIOLATION FATAL_ERROR` («Локальное хранилище недоступно (circuit breaker open)»), что блокирует редактирование песен в admin UI. StorageApiClient.fileExists для REMOTE тоже отдаёт false (FastFail), хотя MinIO живой.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Watchdog для зависшего probe (Priority: P1)

**Описание**: `StorageCircuitBreaker` запускает **watchdog** через `ScheduledExecutorService`. Если `state=HALF_OPEN` дольше чем `timeoutSeconds + buffer` (default: 5s + 10s = 15s) **без** `recordSuccess`/`recordFailure` — watchdog принудительно переводит state в `OPEN` с обновлённым `openedAtMs = now()`. Это даёт circuit следующий шанс на probe через `cooldownSeconds` (30s).

**Why P1**: основная боль, root cause. Без watchdog'а баг вернётся при любом следующем network glitch.

**Independent Test**: Unit-тест `StorageCircuitBreakerTest.testWatchdogReopensStuckProbe()`:
1. Заставить circuit перейти в HALF_OPEN (через 5 фейлов + cooldown).
2. **НЕ вызывать** `recordSuccess`/`recordFailure` (имитация зависшего callback).
3. Подождать `timeoutSeconds + buffer + 100ms`.
4. Проверить, что state == OPEN, `openedAtMs` обновлён.

**Acceptance Scenarios**:

1. **Given** circuit в HALF_OPEN, **When** прошло `timeoutSeconds + buffer` без recordSuccess/recordFailure, **Then** watchdog переводит state в OPEN, обновляет `openedAtMs`, логирует `cache:circuit:watchdog state=HALF_OPEN->OPEN (probe stuck)`.
2. **Given** circuit в HALF_OPEN, **When** `recordSuccess` вызывается в течение `timeoutSeconds + buffer`, **Then** watchdog не вмешивается, normal HALF_OPEN→CLOSED transition.
3. **Given** circuit в CLOSED или OPEN, **When** watchdog tick, **Then** watchdog не делает ничего (no-op для не-HALF_OPEN состояний).
4. **Given** watchdog работает, **When** 100 concurrent fileExists на broken MinIO, **Then** circuit не зависает в HALF_OPEN > `timeoutSeconds + buffer` (NFR-002 из спеки #352 не нарушается).

---

### User Story 2 — Manual reset endpoint (Priority: P1)

**Описание**: `POST /api/health/circuit-breaker/reset` сбрасывает circuit breaker в начальное состояние: `state=CLOSED`, `failureCount=0`, `openedAtMs=0`, `lastFailureAt=null`. Endpoint возвращает JSON с previousState и currentState. Это emergency escape hatch, если watchdog по какой-то причине не сработал.

**Why P1**: FR-007 из спеки #352 (хотя помечен P3 optional — мы повышаем до P1, потому что без него при баге watchdog'а нет другого способа починить, кроме рестарта `karaoke-app`).

**Independent Test**: 
- `curl -X POST http://karaoke-app:8899/api/health/circuit-breaker/reset` → 200 + `{"previousState":"HALF_OPEN","currentState":"CLOSED"}`.
- После reset — следующий `fileExists` идёт в MinIO напрямую (без FastFail).

**Acceptance Scenarios**:

1. **Given** circuit в HALF_OPEN, **When** POST `/api/health/circuit-breaker/reset`, **Then** 200 OK + `{"previousState":"HALF_OPEN","currentState":"CLOSED","failureCount":0,"lastFailureAt":null}`.
2. **Given** circuit в OPEN, **When** POST reset, **Then** 200 OK + previousState=OPEN.
3. **Given** circuit в CLOSED, **When** POST reset, **Then** 200 OK + previousState=CLOSED (no-op, но endpoint идемпотентен).
4. **Given** watchdog scheduler, **When** reset вызван, **Then** watchdog не вмешивается в течение `timeoutSeconds + buffer` после reset (т.е. не триггерит ложный OPEN из-за устаревшего HALF_OPEN).

---

### User Story 3 — Обновление knowledge base (Priority: P2)

**Описание**: После реализации watchdog'а — обновить `knowledge/domains/storage/components/storage-api-client.md` (секция «Pass 372: watchdog») и `knowledge/domains/monitoring/components/log-categories.md` (новый event `cache:circuit:watchdog`). Это SSoT (Hard Gate Tier-1).

**Why P2**: AGENTS.md Tier-1 Hard Gate — Knowledge SSoT обязателен. Без обновления knowledge следующий разработчик не узнает про watchdog.

**Acceptance Scenarios**:

1. **Given** PR смержен, **When** `bash tools/check-ssot-impact.py` запущен, **Then** нет violations (новые файлы/поля в `StorageCircuitBreaker.kt` имеют cross-link в knowledge).
2. **Given** `knowledge/domains/monitoring/components/log-categories.md`, **When** PR открыт, **Then** в таблице `infra.cache.storage` есть строка `cache:circuit:watchdog`.

---

## Requirements *(mandatory)*

### Functional

- **FR-001**: System MUST запускать watchdog при старте `StorageCircuitBreaker` (через `@PostConstruct`). Watchdog MUST работать в одном `ScheduledExecutorService` (single daemon thread, не блокирует Spring shutdown).
- **FR-002**: System MUST проверять state каждые `checkIntervalSeconds` (default: 1s). Если `state=HALF_OPEN` и с момента перехода в HALF_OPEN прошло `timeoutSeconds + watchdogBufferSeconds` (default: 5+10=15s) — watchdog MUST принудительно перевести state в OPEN с `openedAtMs.set(now())`.
- **FR-003**: Watchdog MUST быть идемпотентным и атомарным (через `state.compareAndSet(HALF_OPEN, OPEN)` — несколько watchdog ticks не должны делать двойную работу).
- **FR-004**: Watchdog MUST логировать `cache:circuit:watchdog state=HALF_OPEN->OPEN (probe stuck) durationMs=<X>` в SLF4J-категорию `infra.cache.storage` на каждом срабатывании.
- **FR-005**: System MUST предоставить endpoint `POST /api/health/circuit-breaker/reset` в `CircuitBreakerController.kt` (NEW). Метод MUST сбрасывать `state=CLOSED`, `failureCount=0`, `openedAtMs=0`. Возвращает `CircuitBreakerResetResponse { previousState, currentState }`.
- **FR-006**: System MUST останавливать watchdog при shutdown `StorageCircuitBreaker` (через `@PreDestroy`). Executor MUST быть `shutdown()` без `shutdownNow()` (graceful).
- **FR-007**: System MUST логировать `cache:circuit:reset reason=manual_request` при каждом вызове reset endpoint (для observability ручных действий).
- **FR-008**: Endpoint `/api/health/circuit-breaker` (GET, уже существует из Pass 351) MUST продолжать работать и возвращать обновлённый state.

### Non-Functional

- **NFR-001**: Watchdog overhead: ≤ 1 проверка в секунду, ≤ 0.1ms CPU per check (single AtomicReference read + comparison).
- **NFR-002**: Reset endpoint MUST быть идемпотентен — повторный вызов в CLOSED не приводит к ошибке.
- **NFR-003**: Reset endpoint MUST НЕ требовать авторизации (admin-only, как и весь `/api/health/*`). См. существующие `CacheStatsController`, `MonitoringController`.
- **NFR-004**: Все новые методы MUST быть покрыты unit-тестами (минимум 2 новых теста: watchdog timeout + reset endpoint).
- **NFR-005**: Watchdog MUST быть выключаемым через `KaraokeProperties.storage.circuit-breaker-watchdog-enabled` (default: true) — для emergency disable через env-var без ребилда.

### Key Entities

- **`StorageCircuitBreaker`** (MODIFY) — добавить:
  - `watchdogExecutor: ScheduledExecutorService` (single daemon thread).
  - `halfOpenSinceMs: AtomicLong` (timestamp перехода OPEN→HALF_OPEN).
  - `watchdogBufferSeconds: Long` (configurable, default 10).
  - `checkIntervalSeconds: Long` (configurable, default 1).
  - `reset()` — public method для сброса state.
  - `@PostConstruct initWatchdog()` — запуск scheduled task.
  - `@PreDestroy destroyWatchdog()` — graceful shutdown.
- **`CircuitBreakerController`** (NEW) — `@RestController @RequestMapping("/api/health/circuit-breaker")`. Метод `POST /reset` + `GET /` (state).
- **`KaraokeProperties`** (MODIFY) — добавить `storage.circuit-breaker-watchdog-buffer-seconds` (default 10), `storage.circuit-breaker-watchdog-check-interval-seconds` (default 1), `storage.circuit-breaker-watchdog-enabled` (default true).

## Success Criteria *(mandatory)*

- **SC-001**: Circuit breaker не может находиться в HALF_OPEN дольше `timeoutSeconds + watchdogBufferSeconds` (default: 15s). Verified через unit-тест `testWatchdogReopensStuckProbe` + интеграционный сценарий в quickstart.md.
- **SC-002**: `POST /api/health/circuit-breaker/reset` возвращает 200 + previousState/currentState. Verified через `curl` после ручного зависания probe.
- **SC-003**: Все 8 существующих unit-тестов `StorageCircuitBreakerTest` + 2 новых (watchdog, reset) = 10/10 PASS.
- **SC-004**: `bash tools/check-ssot-impact.py` — 0 violations (новые поля в `StorageCircuitBreaker.kt` имеют cross-link в `storage-api-client.md`).
- **SC-005**: `ktlintCheck` — 0 violations. `:karaoke-app:bootJar` OK. `gh pr checks` — все CI jobs PASS.

## Assumptions

1. **Single replica of `karaoke-app`** — watchdog работает per-instance. Multi-replica watchdog не требуется (см. Out of Scope спеки #352).
2. **`ScheduledExecutorService` доступен** — стандартная JSR-236, не требует внешних зависимостей.
3. **Watchdog buffer (15s)** достаточно для всех известных timeout scenarios (Mono.timeout 5s + GC pause 5s + thread scheduling jitter 5s). Если баг повторится с buffer=15s — увеличить через env-var.
4. **`HealthReport` использует `cb.acquire()` напрямую** (минуя `decorate`) — это by design для fast-fail без MinIO-call. Watchdog НЕ исправляет это — это отдельная задача.

## Out of Scope

- **HealthReport integration с `decorate`** — сейчас `cb.acquire()` без loader. Это значит, что HALF_OPEN→CLOSED может произойти только через `StorageApiClient.fileExists`, который вызывает `decorate` с реальным loader. Чтобы circuit мог восстановиться **через HealthReport**, нужно рефакторить `HealthReport.actionsLocalStorage` — это отдельная задача.
- **`KaraokeStorageService.fileExists` circuit integration** — для LOCAL MinIO сейчас нет `decorate` обёртки. Тоже отдельная задача.
- **Distributed circuit (multi-replica)** — не актуально (1 admin-машина).
- **5xx error handling** — можно добавить в future iteration (Assumption #1 спеки #352).
- **Circuit breaker visualization в admin UI** — не в этом PR.

## Migration Path

### Что нужно изменить

- `StorageCircuitBreaker.kt` (MODIFY) — добавить watchdog + reset() method.
- `KaraokeProperties.kt` (MODIFY) — добавить 3 новых property.
- `CircuitBreakerController.kt` (NEW, ~50 LOC) — POST /reset + GET /.
- `StorageCircuitBreakerTest.kt` (MODIFY) — добавить 2 unit-теста.
- `specs/352-storage-graceful-degradation/tasks.md` (MODIFY) — добавить T028, T029. Исправить ложно отмеченный T015.
- `specs/352-storage-graceful-degradation/spec.md` (MODIFY) — FR-007 повышен с P3 optional до hard requirement. Cross-link на спеку #405.
- `knowledge/domains/storage/components/storage-api-client.md` (MODIFY) — секция «Pass 372: watchdog».
- `knowledge/domains/monitoring/components/log-categories.md` (MODIFY) — добавить event `cache:circuit:watchdog`.
- `docs/features/storage-metadata-cache.md` (MODIFY) — обновить V2 → V2.1 → V2.2.

### Что НЕ нужно менять

- `StorageApiClient.kt` — уже использует `decorate`/`decorateOrEmpty`, watchdog совместим.
- `HealthReport.kt` — использует `acquire()` напрямую (см. Out of Scope).
- `CacheStatsController.kt` — уже возвращает `circuitBreaker` block.
- Существующие unit-тесты — только добавляем, не меняем.

## Реализация (План в plan.md)

### Шаги (high-level)

1. Создать спеку `specs/405-storage-circuit-breaker-watchdog/{spec,plan,tasks}.md`.
2. `KaraokeProperties.kt` — добавить 3 property.
3. `StorageCircuitBreaker.kt` — добавить watchdog (ScheduledExecutorService + @PostConstruct/@PreDestroy), `reset()` method, `halfOpenSinceMs` field, обновить `recordSuccess`/`recordFailure`/`acquire` для трекинга.
4. `CircuitBreakerController.kt` (NEW) — POST /reset + GET /.
5. `StorageCircuitBreakerTest.kt` — добавить 2 unit-теста.
6. `application.yml` — добавить дефолты для 3 property.
7. Обновить knowledge (`storage-api-client.md`, `log-categories.md`).
8. Обновить спеку #352 (cross-link, T028/T029, исправить T015).
9. `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*StorageCircuit*"` — 10/10 PASS.
10. `:karaoke-app:ktlintCheck` — 0 violations.
11. `:karaoke-app:bootJar` — OK.
12. `git push -u origin 405-storage-circuit-breaker-watchdog` + `gh pr create`.
13. CI green → `gh pr merge --merge`.
14. `tracker.sh add-comment 131 --file specs/405-storage-circuit-breaker-watchdog/report.md` → `mark-review 131`.
15. Owner рестартует `karaoke-app` (после согласия).

## Validation

| Проверка | Ожидаемо |
|---|---|
| `:karaoke-app:test --tests "*StorageCircuit*"` | 10/10 PASS (8 existing + 2 new) |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:bootJar` | OK |
| `bash tools/check-ssot-impact.py` | 0 violations |
| `bash tools/check-knowledge-structure.sh` | 9/9 OK |
| `curl -X POST http://localhost:8898/api/health/circuit-breaker/reset` | 200 + previousState/currentState |
| `gh pr checks <#405>` | all PASS |

## Rollback

`git revert <merge-commit>`:
- `StorageCircuitBreaker.kt` откатывается — watchdog исчезает, reset() исчезает.
- `CircuitBreakerController.kt` остаётся, но POST /reset даёт 405 (не зарегистрирован бин).
- Без watchdog'а проблема HALF_OPEN зависания возвращается — но рестарт `karaoke-app` решает.

## Clarifications

### Session 2026-09-17

- **Q**: Почему watchdog, а не просто «разрешить нескольким потокам делать probe»?
  - **A**: HALF_OPEN design с single probe — by design (FR-002 спеки #352, Q3 Clarifications): «success → CLOSED, failure → OPEN. Безопасный recovery, одна ошибка не сбрасывает state». Watchdog защищает single probe от зависания, не меняет семантику.
- **Q**: Почему reset endpoint, если есть watchdog?
  - **A**: Watchdog — automatic protection. Reset endpoint — manual escape hatch. Если watchdog почему-то не сработает (баг в логике watchdog'а), reset даёт быстрый способ починить без рестарта `karaoke-app`. Это defense-in-depth.
- **Q**: Watchdog buffer — 10s. Не слишком мало?
  - **A**: Достаточно для `timeoutSeconds=5` (Mono.timeout) + GC pause 5s. Если GC pause > 5s — это уже серьёзная проблема JVM, watchdog buffer всё равно её не спасёт. Если watchdog будет срабатывать ложно — увеличить через `STORAGE_CIRCUIT_BREAKER_WATCHDOG_BUFFER_SECONDS=30`.

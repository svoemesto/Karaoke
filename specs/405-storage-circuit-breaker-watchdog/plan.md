# Plan: StorageCircuitBreaker watchdog + manual reset (Pass 372, #131)

**Input**: `specs/405-storage-circuit-breaker-watchdog/spec.md`
**Tech stack**: Kotlin 1.9, Spring Boot 3.x, Reactor (`Mono`), Resilience-style AtomicReference FSM, `java.util.concurrent.ScheduledExecutorService`.

## Архитектура

### Слои

```
┌────────────────────────────────────────────────────────┐
│ karaoke-app (Spring)                                   │
│                                                        │
│  StorageApiClient.fileExists/fileIsActual/getFileInfo  │
│       │                                                │
│       ▼                                                │
│  StorageCircuitBreaker.decorate() / decorateOrEmpty()  │
│       │                                                │
│       ├─ acquire() → Decision (Allow/Probe/FastFail)   │
│       │                                                │
│       └─ recordSuccess() / recordFailure() → state FSM │
│           (CLOSED ↔ OPEN ↔ HALF_OPEN, AtomicReference) │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Watchdog (NEW)                                   │  │
│  │  ScheduledExecutorService (1 daemon thread)      │  │
│  │  tick every checkIntervalSeconds (default 1s)    │  │
│  │  if state=HALF_OPEN &&                          │  │
│  │     now - halfOpenSinceMs > timeout+buffer:     │  │
│  │    state.compareAndSet(HALF_OPEN, OPEN)         │  │
│  │    openedAtMs.set(now())                        │  │
│  │    log "cache:circuit:watchdog"                 │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  CircuitBreakerController (NEW)                       │
│    GET  /api/health/circuit-breaker      → Metrics     │
│    POST /api/health/circuit-breaker/reset → reset()    │
└────────────────────────────────────────────────────────┘
```

## Файлы

### Создать

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CircuitBreakerController.kt` (~50 LOC)
- `specs/405-storage-circuit-breaker-watchdog/{spec,plan,tasks,quickstart,report}.md`

### Изменить

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreaker.kt` (~+60 LOC):
  - `import jakarta.annotation.PostConstruct` + `import jakarta.annotation.PreDestroy`.
  - `import org.springframework.beans.factory.annotation.Value` (уже есть).
  - Fields: `watchdogExecutor`, `halfOpenSinceMs: AtomicLong`, `watchdogBufferSeconds`, `checkIntervalSeconds`, `watchdogEnabled`.
  - Methods: `initWatchdog()`, `destroyWatchdog()`, `watchdogTick()`, `reset()`.
  - Modify `acquire()`: на успешном `OPEN→HALF_OPEN` CAS — `halfOpenSinceMs.set(now())`.
  - Modify `recordSuccess()`/`recordFailure()` для HALF_OPEN: `halfOpenSinceMs.set(0)` на успешном переходе в CLOSED.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProperties.kt` — НЕ модифицируется (настройки circuit breaker идут через `@Value` в конструкторе `StorageCircuitBreaker.kt` + defaults в `application.yml`).
- `karaoke-app/src/main/resources/application.yml` (+3 key под `storage:`).
- `karaoke-app/src/test/kotlin/.../services/StorageCircuitBreakerTest.kt` (+2 unit-теста).
- `specs/352-storage-graceful-degradation/{spec,tasks}.md` (cross-link + исправить T015).
- `knowledge/domains/storage/components/storage-api-client.md` (+ секция «Pass 372: watchdog»).
- `knowledge/domains/monitoring/components/log-categories.md` (+ event `cache:circuit:watchdog`).
- `docs/features/storage-metadata-cache.md` (V2.1 → V2.2).

## Контракт `POST /api/health/circuit-breaker/reset`

### Request

```
POST /api/health/circuit-breaker/reset
Content-Type: application/json (no body)
```

### Response (200 OK)

```json
{
  "previousState": "HALF_OPEN",
  "currentState": "CLOSED",
  "failureCount": 0,
  "lastFailureAt": null,
  "resetAtMs": 1789641123456,
  "totalSuccesses": 355470,
  "totalNetworkFailures": 5
}
```

### Edge cases

- Если circuit уже CLOSED — previousState=CLOSED, currentState=CLOSED (no-op, 200 OK).
- Если watchdog уже сработал параллельно — CAS атомарен, гарантирует consistent state.
- Если `karaoke-app` в процессе shutdown — endpoint может вернуть 503 (graceful shutdown), но обычно 200.

## Контракт SLF4J events

| Event | Уровень | Когда | Поля |
|---|---|---|---|
| `cache:network:failure` | WARN | Per-call network failure (existing, Pass 351) | error, failureCount, threshold |
| `cache:circuit:state` | INFO | State transition (existing, Pass 351) | from, to, failureCount, openedAtMs |
| `cache:circuit:watchdog` (NEW) | WARN | Watchdog перевёл HALF_OPEN→OPEN (probe stuck) | state, durationMs, openedAtMs |
| `cache:circuit:reset` (NEW) | INFO | Reset endpoint вызван | reason=manual_request, previousState, currentState |

## Тестирование

### Unit-тесты (StorageCircuitBreakerTest.kt)

#### Existing (Pass 351, 8 тестов)

1. `closed state allows calls` (acquire returns Allow).
2. `threshold failures opens circuit`.
3. `cooldown half-opens circuit`.
4. `probe success closes circuit`.
5. `probe failure reopens circuit`.
6. `concurrent acquire allows only one probe`.
7. `concurrent failures transition once to OPEN`.
8. `overhead closed state under 1ms`.

#### NEW (Pass 372, 2 теста)

9. `testWatchdogReopensStuckProbe`:
   - Заставить circuit в HALF_OPEN.
   - **Не** вызывать recordSuccess/recordFailure.
   - Подождать `timeoutSeconds + watchdogBufferSeconds + 100ms`.
   - Assert: state == OPEN, openedAtMs обновлён.
   - В логах есть `cache:circuit:watchdog state=HALF_OPEN->OPEN`.

10. `testResetEndpointTransitionsToClosed`:
    - Заставить circuit в HALF_OPEN (или OPEN).
    - Вызвать `cb.reset()`.
    - Assert: state == CLOSED, failureCount == 0, openedAtMs == 0.
    - Повторный reset: previousState=CLOSED, currentState=CLOSED (идемпотентно).

### Integration scenarios (quickstart.md)

Сценарий A: **Watchdog срабатывает в production**
1. Добиться зависания probe (теоретически — или через специальный test mode).
2. Подождать 15s.
3. `curl /api/health/circuit-breaker` → state=OPEN, openedAtMs свежий.
4. В логах `cache:circuit:watchdog`.

Сценарий B: **Reset endpoint**
1. Добиться OPEN state (5 failures).
2. `curl -X POST /api/health/circuit-breaker/reset`.
3. Проверить 200 + previousState=OPEN, currentState=CLOSED.
4. Следующий `fileExists` идёт в MinIO (Allow через acquire).

## Безопасность

- `POST /api/health/circuit-breaker/reset` — **БЕЗ авторизации**, как и остальные `/api/health/*` (admin-only сеть, не публичный endpoint).
- Это by design: `/api/health/*` не делают state-changing операции обычно, но reset — exception. Альтернатива: Basic Auth или IP whitelist. Обсудить с owner если нужно.
- Watchdog не делает destructive операций — только state transition.

## Совместимость

- **API**: новые endpoint, additive. Старые клиенты `/api/health/circuit-breaker` (GET) продолжают работать.
- **State machine**: backward compatible. Старый код, использующий `acquire()`/`decorate`, продолжает работать. Watchdog только добавляет safety net.
- **Config**: новые property с default values — backward compatible. Если `application.yml` не обновлён, используются defaults.

## Открытые вопросы

- (Решено) Авторизация на reset endpoint — пока без auth, по аналогии с `/api/health/*`.
- (Решено) Watchdog buffer = 10s — компромисс между «ложные срабатывания» и «время восстановления».
- (Открыто для owner) Нужен ли rate-limit на reset endpoint? — пока нет (admin-only).

# Отчёт по #150 — StorageCircuitBreaker: реальный timeout для блокирующего loader

> **Спека**: [spec.md](spec.md) | **Ветка**: `426-storage-circuit-breaker-blocking-timeout` |
> **Дата**: 2026-09-22 | **Pass**: 426.

## Симптом

Логи `karaoke-app` (nsa-i9) бесконечным потоком:

```
09:46:02 cache:network:failure error="SocketTimeoutException" failureCount=5 threshold=5
09:46:02 cache:circuit:state from=CLOSED to=OPEN failureCount=5
09:46:32 cache:circuit:state from=OPEN to=HALF_OPEN
09:46:48 cache:circuit:watchdog state=HALF_OPEN->OPEN (probe stuck) durationMs=15887
... цикл повторяется каждые ~45s ...
```

плюс тысячи `circuit=OPEN storage=local reason=Circuit breaker open` и
`KaraokeProcessThread: ERROR (данные не найдены) ... UPLOAD_TO_LOCAL_STORE`.

## Диагностика

Проверено вживую:

- TCP до remote MinIO (`89.125.103.63:9000`): 20 проб → 5–6 таймаутов;
  connect из контейнера `karaoke-app`: 0.003s → 1s → 2s → **5.16s**.
- Локальный MinIO (`karaoke-storage:9000`) — жив (health 200).
- Circuit защищает **remote** MinIO (`StorageApiClientImpl` → `storage.remote-endpoint`),
  а не локальный — лог `storage=local` вводил в заблуждение.

**Root cause**: `StorageCircuitBreaker.decorate`/`decorateOrEmpty` оборачивали
блокирующий MinIO-вызов (`Mono.fromCallable { ... }`) в `.timeout(timeoutSeconds)`,
но исполняли его на **вызывающем** потоке (`.block()`). Оператор `timeout` не может
прервать блокировку → фактическое ожидание = OkHttp `connectTimeout` (15s), что
больше `timeoutSeconds(5) + watchdogBuffer(10) = 15s`. Watchdog всегда переводил
HALF_OPEN→OPEN, circuit никогда не закрывался даже при доступном MinIO.

## Что сделано

- **`StorageCircuitBreaker.kt`**: в `decorate`/`decorateOrEmpty` добавлен
  `.subscribeOn(blockingScheduler)`, где `blockingScheduler = Schedulers.boundedElastic()`
  (единый на bean). `.timeout(timeoutSeconds)` теперь реально возвращает управление
  (≈5s), а не ждёт блокировку.
- **`StorageApiClient.kt`** (`StorageApiClientImpl`): OkHttp `connectTimeout`/`readTimeout`
  выровнены с `storage.file-exists-timeout-seconds` (5s) вместо 15s/60s;
  `writeTimeout` (300s, для upload) не тронут.
- **`HealthReport.kt`**: диагностический лог FastFail исправлен —
  `circuit=OPEN storage=remote`, `problemText`/`solutionText` про удалённое хранилище
  (circuit защищает remote MinIO).
- **`StorageCircuitBreakerTest.kt`**: +2 unit-теста — блокирующий loader обрывается
  по `timeoutSeconds`; успешный probe после сбоя закрывает circuit.
- **Knowledge/docs**: `storage-api-client.md`, `storage/domain.md`,
  `log-categories.md`, `docs/features/storage-metadata-cache.md` (V2.3).

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `compileTestKotlin` | OK |
| `:karaoke-app:ktlintCheck` | OK (0 violations) |
| `:karaoke-app:test --tests "*StorageCircuitBreakerTest"` | 14/14 PASS |
| `:karaoke-app:bootJar` | OK |
| `tools/check-spec-issue-link.py` | OK (23/23) |
| `tools/check-knowledge-structure.sh` | 9/9 OK |
| `tools/lint-knowledge.py` | exit 0 (baseline-состояние) |

## Открытые вопросы / follow-up

- Нестабильность сетевого канала до remote MinIO (5–6 таймаутов из 20 TCP-проб) —
  **внешняя причина**, спека не исправляет канал, а обеспечивает корректное
  восстановление circuit. Инфраструктурная задача — отдельно.
- Поведенческое разъединение проверки circuit в `actionsLocalStorage` (чтобы
  remote-сбой не валил локальную заливку `UPLOAD_TO_LOCAL_STORE`) — Out of Scope,
  отдельная задача.
- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9: агент рестарт не делает).

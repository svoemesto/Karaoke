# Quickstart: Storage graceful degradation (Pass 351)

**Phase**: 1 — Design & Contracts
**Date**: 2026-09-09
**Spec**: [spec.md](./spec.md)
**Data model**: [data-model.md](./data-model.md)
**Contracts**: [contracts/circuit-breaker-state.md](./contracts/circuit-breaker-state.md)

> **Validation guide.** Это НЕ полная реализация, а набор **запускаемых сценариев**,
> которые доказывают, что фича работает end-to-end.

## Prerequisites

1. `karaoke-app` собран с `StorageCircuitBreaker` bean:
   ```bash
   GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar
   ```

2. Юнит-тесты `StorageCircuitBreakerTest` PASS:
   ```bash
   GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.services.StorageCircuitBreakerTest"
   ```
   Ожидаемо: **6/6 tests passed**.

3. **Mock-сценарий для network outage** — отдельный bash-скрипт `tools/with-dead-network.sh` (NEW, в tasks.md) имитирует network failures.

## Scenario 1 — Cold start с network outage

**Goal**: При cold start (state=CLOSED, failureCount=0) и недоступном remote MinIO — `fileExists` возвращает `false` за `timeoutSeconds`, а НЕ блокируется на 60s.

### Steps

1. **Setup**: `bash tools/with-dead-network.sh setup` — модифицирует `/etc/hosts` чтобы `89.125.103.63` (remote MinIO endpoint) был недоступен.

2. **Run**: 
   ```bash
   time bash tools/measure-fileExists-latency.sh
   ```
   Ожидаемо: `latency: ~5s`, `result: false`, exit 0.

3. **Verify** logs: `grep "infra.cache.storage" /var/log/karaoke-app.log | grep "network:failure"` — должна быть запись с `error="SocketTimeoutException"`.

4. **Verify metrics**: `curl /api/health/cacheStats | jq '.remote.networkFailures'` — должен быть >= 1.

## Scenario 2 — Circuit OPEN после 5 failures

**Goal**: После `threshold=5` consecutive failures circuit OPEN, все последующие `fileExists` возвращают `false` мгновенно.

### Steps

1. **Setup**: продолжаем с Scenario 1 (network down).

2. **Run 5 fileExists calls**:
   ```bash
   for i in 1 2 3 4 5; do
     time bash tools/measure-fileExists-latency.sh
   done
   ```

3. **Verify state**: 
   ```bash
   curl -s http://localhost:8899/api/health/circuit-breaker | jq '.state'
   ```
   Ожидаемо: `"OPEN"`.

4. **Verify latency for 6th call**:
   ```bash
   time bash tools/measure-fileExists-latency.sh
   ```
   Ожидаемо: `latency: < 100ms` (circuit fast-fail, без MinIO call).

5. **Verify log**: `grep "cache:circuit:state" /var/log/karaoke-app.log` — должна быть запись `from=CLOSED to=OPEN`.

## Scenario 3 — Half-open probe после cooldown

**Goal**: После `cooldownSeconds=30` (default) — circuit переходит в HALF_OPEN на следующий вызов, success → CLOSED.

### Steps

1. **Setup**: продолжаем с Scenario 2 (circuit OPEN, network всё ещё down для теста — **используем mock**).

2. **Wait**: `sleep 31` (cooldown + 1s buffer).

3. **Run probe**:
   ```bash
   time bash tools/measure-fileExists-latency.sh
   ```
   Ожидаемо: `latency: ~5s` (probe fails because network down), `result: false`.

4. **Verify state**: `curl /api/health/circuit-breaker | jq '.state'` → `"OPEN"` (probe failed, back to OPEN with reset openedAtMs).

5. **Restore network**: `bash tools/with-dead-network.sh restore`.

6. **Wait + probe again**: `sleep 31 && bash tools/measure-fileExists-latency.sh` — теперь probe success, `result: true` (file exists), `latency: ~50ms`.

7. **Verify state**: `curl /api/health/circuit-breaker | jq '.state'` → `"CLOSED"`, `failureCount: 0`.

## Scenario 4 — Configuration через env

**Goal**: Verify env-vars override defaults.

### Steps

1. **Run with override**:
   ```bash
   STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10 \
   STORAGE_CIRCUIT_BREAKER_THRESHOLD=2 \
   STORAGE_CIRCUIT_BREAKER_COOLDOWN_SECONDS=60 \
   ./deploy/do.sh restart_karaoke-app
   ```

2. **Verify**: 
   ```bash
   curl /api/health/circuit-breaker | jq '{timeoutSeconds, threshold, cooldownSeconds}'
   ```
   Ожидаемо: `{ "timeoutSeconds": 10, "threshold": 2, "cooldownSeconds": 60 }`.

3. **Verify behavior**: 2 failures → OPEN (было 5).

## Scenario 5 — Полная observability

**Goal**: Verify все observability-каналы работают.

### Steps

1. **Trigger failure** (см. Scenario 1).

2. **Logs**: `grep "infra.cache.storage" /var/log/karaoke-app.log` — должны быть:
   - `cache:network:failure` — на каждый fail.
   - `cache:circuit:state` — при transitions.

3. **Metrics endpoint**: `curl /api/health/cacheStats | jq '.remote'` — должны быть поля `networkFailures`, `circuitBreaker.state`, `circuitBreaker.totalSuccesses`, `circuitBreaker.totalNetworkFailures`.

4. **(Optional P3)**: `curl /api/health/circuit-breaker` — возвращает полный snapshot.

## Cleanup

```bash
# Восстановить сеть:
bash tools/with-dead-network.sh restore

# Сбросить circuit вручную (если нужно):
curl -X POST http://localhost:8899/api/health/circuit-breaker/reset
```

## Связь с задачами (Tasks.md)

Этот quickstart покрывает acceptance-критерии для User Stories 1-3. Tasks.md разобьёт эти сценарии на конкретные задачи.

## Готовность

Все design-артефакты (research.md, data-model.md, contracts/circuit-breaker-state.md, quickstart.md) написаны. Constitution Check пройден. Можно переходить к `/speckit.tasks`.

# Отчёт по #156 — Circuit probe не должен съедаться диагностикой

> **Спека**: [spec.md](spec.md) | **Ветка**: `432-circuit-probe-not-consumed-by-healthcheck` |
> **Дата**: 2026-09-22 | **Pass**: 432.

## Симптом

После рестарта с фиксами #150–#154, #431 в логах `karaoke-app` (nsa-i9) circuit
`remote` зациклился:

```
15:05:56 remote CLOSED -> OPEN (failureCount=5)
15:06:27 remote OPEN -> HALF_OPEN
15:06:57 watchdog remote HALF_OPEN->OPEN (probe stuck) durationMs=30106
15:07:38 remote OPEN -> HALF_OPEN
15:08:08 watchdog remote HALF_OPEN->OPEN (probe stuck)
```

При этом remote MinIO **полностью доступен**: 3/3 health-пробы `200` за ~9 ms.
Диагностика: `probe failed`=0, `HALF_OPEN→CLOSED`=0.

## Root cause

`HealthReport.actionsLocalStorage`/`actionsRemoteStorage` вызывали `cb.acquire()`
для fail-fast. Когда cooldown истёк, **диагностический** вызов выигрывал CAS
`OPEN→HALF_OPEN` и получал `Decision.Probe`, но реальный MinIO-вызов не исполнял
(HealthReport не делает storage-вызовов). Реальный probe (`decorate` в
`StorageApiClientImpl` / `executeBlocking`) приходил позже и получал `FastFail`
(state=HALF_OPEN). Итог: probe не исполнялся **ни разу** → `recordSuccess`/`recordFailure`
не вызывались → watchdog через `timeout+buffer` (30s) возвращал OPEN. **Вечный
цикл**, даже при живом хранилище.

Это ровно тот gap, который спека #405 (Pass 372) пометила в **Out of Scope**:
«HealthReport использует `cb.acquire()` напрямую… нужно рефакторить — отдельная
задача». Pass 429 (изоляция) сделал баг заметным.

## Что сделано

- **`StorageCircuitBreaker.isFastFail(): Boolean`** — нетранзишн-проверка: только
  читает `state` (`OPEN`/`HALF_OPEN` → true), **без** CAS `OPEN→HALF_OPEN` и без
  probe.
- **`HealthReport`** — оба места (`actionsLocalStorage`, `actionsRemoteStorage`)
  переведены с `cb.acquire()` на `cb.isFastFail()`.
- `acquire()` (с CAS и `Probe`) остаётся только у
  `decorate`/`decorateOrEmpty`/`executeBlocking` — единственных исполнителей probe.
- **Knowledge SSoT**: `storage-api-client.md` (Pass 432), `health-report.md`,
  `docs/features/storage-metadata-cache.md` (V2.7).

## Тесты

- `diagnostics do not consume probe` (NEW): OPEN + cooldown elapsed → `isFastFail()`=true,
  state остаётся OPEN; затем `acquire()`=Probe → `recordSuccess` → CLOSED.
- `isFastFail mirrors acquire for CLOSED and HALF_OPEN` (NEW).
- Всего `StorageCircuitBreakerTest` — 16/16 PASS.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `StorageCircuitBreakerTest` | 16/16 PASS |
| `:karaoke-app:bootJar` | OK |
| knowledge 9/9, cross-links 631/631, R-11 OK | OK |

## Поведение после фикса

| Ситуация | До | После |
|---|---|---|
| OPEN + cooldown elapsed, диагностика | съедает probe (→HALF_OPEN) | остаётся OPEN, probe свободен |
| Реальный storage-вызов | FastFail (probe потерян) | получает Probe → при успехе CLOSED |
| Живое хранилище | вечный цикл | штатное восстановление circuit |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9).
- После рестарта: `grep 'probe stuck'` не должен расти при живом MinIO;
  `GET /api/health/circuit-breaker` — remote закрывается после успешного probe.

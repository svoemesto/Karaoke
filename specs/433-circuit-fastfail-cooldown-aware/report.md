# Отчёт по #157 — isFastFail cooldown-aware (регресс #156)

> **Спека**: [spec.md](spec.md) | **Ветка**: `433-circuit-fastfail-cooldown-aware` |
> **Дата**: 2026-09-22 | **Pass**: 433.

## Симптом

После рестарта с Pass 432 (#525) circuit `remote` залип в **OPEN**:

```
18:15:04 remote CLOSED -> OPEN (failureCount=5)
... спустя 3+ минуты: state всё ещё OPEN ...
```

При этом: `OPEN→HALF_OPEN` = **0**, `probe stuck` = 0, `decision=FastFail`
(decorate) = **0**. remote MinIO доступен (health 200 за ~9 ms).

## Root cause (регресс от #156)

Pass 432 сделал `isFastFail() = (state == OPEN || state == HALF_OPEN)`. Это
fast-fail'ит диагностику **и после истечения cooldown**:

1. `HealthReport` всегда возвращает `FATAL_ERROR` для remote, не доходя до
   storage-вызова;
2. реальный вызов (`decorate`/`executeBlocking`) не запускается;
3. `acquire()` — единственный, кто делает `OPEN→HALF_OPEN` — не вызывается **никем**;
4. probe не стартует → circuit **залипает в OPEN навсегда**.

До #156 диагностический `acquire()` хотя бы переводил `OPEN→HALF_OPEN` (но терял
probe). #156 убрал потерю probe, но и убрал сам переход.

## Что сделано

`StorageCircuitBreaker.isFastFail()` стал **cooldown-aware**:

```kotlin
CLOSED    -> false
HALF_OPEN -> true                        // probe в полёте
OPEN      -> !(cooldown elapsed)         // true до cooldown, false после
```

- До cooldown: fail-fast (без MinIO-вызова) — как задумано.
- После cooldown: `false` → вызов доходит до `decorate` → `acquire()` → Probe →
  при успехе CLOSED.
- `isFastFail()` по-прежнему **не** делает CAS и не запускает probe (сохранён #156).
- `acquire()`, FSM, watchdog, reset — не изменены.

## Тесты

- `diagnostics do not consume probe` (обновлён): OPEN после cooldown → `isFastFail()`
  = false, state остаётся OPEN; `acquire()`=Probe → `recordSuccess` → CLOSED.
- `isFastFail is cooldown-aware` (NEW): CLOSED=false; OPEN до cooldown=true;
  HALF_OPEN=true.
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

| Ситуация | После #156 (баг) | После #157 |
|---|---|---|
| OPEN, cooldown идёт | fast-fail (верно) | fast-fail (верно) |
| OPEN, cooldown истёк | fast-fail (залипание) | пропуск → probe → CLOSED |
| HALF_OPEN | fast-fail | fast-fail |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9).
- После рестарта: circuit remote должен восстанавливаться
  (`OPEN→HALF_OPEN→CLOSED`) при живом MinIO; `probe stuck` не растёт.

# Pass 372 — StorageCircuitBreaker watchdog + manual reset (OpenProject #131)

**Status**: implemented (PR открыт, ожидает merge).
**Date**: 2026-09-17.
**Branch**: `405-storage-circuit-breaker-watchdog` (merge в master через PR).

## Контекст

2026-09-17 13:11–13:20 на nsa-i9 (admin-машина) production-инцидент: circuit breaker
открылся после 5×SocketTimeoutException к REMOTE MinIO (Pass 351, OpenProject #71),
перешёл в HALF_OPEN через cooldown 30s, но **probe завис навечно**. State=HALF_OPEN
уже 8+ минут, `lastFailureAt` не двигается с 13:12:06. MinIO доступен (health=200,
0.1–0.2s latency, 66% disk used), но `acquire()` для всех потоков возвращает
`FastFail` — новый probe не запускается, потому что в HALF_OPEN только один поток
может стартовать probe, а его callback (`recordSuccess`/`recordFailure`) не доходит
(Mono.timeout race / GC pause / thread death).

Production-impact: HealthReport.actionsLocalStorage массово создаёт
`FILE_VIOLATION FATAL_ERROR` («Локальное хранилище недоступно (circuit breaker open)»),
что блокирует редактирование песен в admin UI. `StorageApiClient.fileExists`
для REMOTE тоже отдаёт `false` (FastFail), хотя MinIO живой.

## Что сделано (Pass 372)

### Код (`karaoke-app`)

1. **`StorageCircuitBreaker.kt`** — добавлен **watchdog** (FR-001..FR-004):
   - `ScheduledExecutorService` (single daemon thread) запускается через `@PostConstruct`.
   - Каждые `checkIntervalSeconds` (default 1s) проверяет state.
   - Если `state=HALF_OPEN` и `now - halfOpenSinceMs > timeoutSeconds + watchdogBufferSeconds`
     (default: 5s + 10s = 15s), watchdog принудительно переводит state в OPEN
     с обновлённым `openedAtMs` через CAS.
   - Логирует `cache:circuit:watchdog state=HALF_OPEN->OPEN (probe stuck) durationMs=X`.
   - Останавливается через `@PreDestroy` (graceful shutdown, awaitTermination 5s).
   - Новый метод `fun reset(): Metrics` (FR-005) — manual escape hatch.

2. **`CircuitBreakerController.kt`** (NEW `@RestController`) — добавлен endpoint
   `POST /api/health/circuit-breaker/reset`:
   - Сбрасывает state в CLOSED + обнуляет counters.
   - Возвращает JSON с `previousState`/`currentState`/`resetAtMs`.
   - Идемпотентен (повторный вызов в CLOSED — no-op).

3. **`application.yml`** — добавлены 3 property:
   - `storage.circuit-breaker-watchdog-enabled: true`.
   - `storage.circuit-breaker-watchdog-buffer-seconds: 10`.
   - `storage.circuit-breaker-watchdog-check-interval-seconds: 1`.

4. **`StorageCircuitBreakerTest.kt`** — добавлено 2 unit-теста:
   - `watchdog reopens stuck HALF_OPEN` — симулирует зависший probe и проверяет, что watchdog переводит в OPEN.
   - `reset transitions to CLOSED idempotently` — проверяет manual reset.

### Документация

1. **`specs/405-storage-circuit-breaker-watchdog/`** (NEW спека): spec.md, plan.md, tasks.md.
2. **`specs/352-storage-graceful-degradation/tasks.md`** — добавлен cross-link на спеку #405.
3. **`knowledge/domains/storage/components/storage-api-client.md`** — секция «Pass 372: watchdog + manual reset».
4. **`knowledge/domains/monitoring/components/log-categories.md`** — 2 новых SLF4J events:
   `cache:circuit:watchdog` (WARN), `cache:circuit:reset` (INFO).
5. **`docs/features/storage-metadata-cache.md`** — version history V2.2.

## Валидация

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` | BUILD SUCCESSFUL |
| `:karaoke-app:test --tests "*StorageCircuit*"` | 12/12 PASS (10 existing + 2 new) |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:bootJar` | OK |
| `bash tools/check-knowledge-structure.sh` | 9/9 OK |

## Production-deployment

После merge — рестарт `karaoke-app` через `deploy/do.sh restart_karaoke_app`
(требует согласия владельца, по правилу Pass 282 для nsa-i9).

После рестарта watchdog автоматически стартует через `@PostConstruct`, и circuit
больше не может зависнуть в HALF_OPEN дольше `timeoutSeconds + watchdogBufferSeconds`.

Для аварийного сброса (если watchdog почему-то не сработает) —

```bash
curl -X POST http://localhost:8898/api/health/circuit-breaker/reset
```

## Cross-references

- OpenProject #131: https://openproject/projects/karaoke/work_packages/131
- Спека: `specs/405-storage-circuit-breaker-watchdog/spec.md`
- Pass 351 / OpenProject #71: `specs/352-storage-graceful-degradation/spec.md`
- Pass 364 / PR #463: HealthReport integration с circuit breaker
- ADR local-0005 (structured logging) — для SLF4J-категории `infra.cache.storage`
- Knowledge: `knowledge/domains/storage/components/storage-api-client.md`
- Knowledge: `knowledge/domains/monitoring/components/log-categories.md`

## Lessons learned

1. **Любой single-probe design должен иметь watchdog**. HALF_OPEN — потенциальная
   точка отказа: один поток делает probe, остальные ждут. Если probe зависает —
   система в deadlock.
2. **Watchdog buffer должен быть больше, чем самый долгий loader timeout**. У нас
   timeoutSeconds=5s (Mono.timeout), watchdogBuffer=10s → итого 15s — покрывает
   GC pause 5s + thread scheduling jitter 5s.
3. **Manual reset endpoint — defense in depth**. Watchdog — automatic protection,
   reset — manual escape hatch. Стоят дёшево, дают страховку.
4. **`tasks.md` чекбоксы могут быть ложными**. T015 был отмечен `[x]` в Pass 351,
   но файл не был создан. Проверять руками перед merge.

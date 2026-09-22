# Отчёт по #153 — Разъединение circuit breaker local/remote

> **Спека**: [spec.md](spec.md) | **Ветка**: `429-split-local-remote-circuit-breakers` |
> **Дата**: 2026-09-22 | **Pass**: 429.

## Проблема

Один `StorageCircuitBreaker` смешивал два независимых бэкенда:

- защищал **remote** (`StorageApiClientImpl.decorate`);
- но `HealthReport.actionsLocalStorage` читал его же для **локального** пути;
- локальный путь (`KaraokeStorageServiceImpl`) вообще не был защищён circuit'ом.

Следствия: сбой remote давал `FATAL_ERROR`/`UPLOAD_TO_LOCAL_STORE ERROR` на local
(ложный каскад), а сбой local мог блокировать вызовы без fail-fast. Требование
владельца: проблемы одного хранилища не должны влиять на другое.

## Что сделано

- **Два независимых bean** (`StorageCircuitBreakerConfig`):
  `localStorageCircuitBreaker` (storageType=local) и
  `remoteStorageCircuitBreaker` (storageType=remote). Общий config, раздельное
  состояние (state/counters/watchdog).
- **`StorageCircuitBreaker`**: добавлены `storageType` (в логи) и blocking-API
  `executeBlocking(operation, loader, emptyValue)` (fail-fast при OPEN, без
  MinIO-вызова). Все события `cache:*` теперь содержат `storage=local|remote`.
- **`KaraokeStorageServiceImpl.fileExists`/`getFileStat`** обёрнуты local-брейкером
  через `executeBlocking` (`@Qualifier("localStorageCircuitBreaker")`).
- **`StorageApiClientImpl`** → `@Qualifier("remoteStorageCircuitBreaker")`.
- **`HealthReport`**: два static-поля; `actionsLocalStorage` → local-брейкер,
  `actionsRemoteStorage` → remote-брейкер (симметричный fail-fast).
- **Контроллеры**: `GET /api/health/circuit-breaker` → `{ local, remote }`;
  `POST /api/health/circuit-breaker/reset?storage=local|remote|all` (default all,
  backward-compatible); `cacheStats.circuitBreaker` → `{ local, remote }`.
- **Knowledge SSoT**: исправлено расхождение `health-report.md` (было «защищает
  local», на деле remote), обновлены `storage-api-client.md`, `domain.md`,
  `storage-flow.md`, `log-categories.md`, `docs/features/storage-metadata-cache.md` (V2.5).

## Тесты

- `StorageCircuitBreakerIsolationTest` (NEW, 6): local OPEN не влияет на remote и
  наоборот; раздельные счётчики; `executeBlocking` fast-fail без вызова loader;
  success/failure; remote работает при OPEN local.
- `StorageCircuitBreakerConfigTest` (NEW, 1): Spring-контекст отдаёт два разных
  bean по квалификаторам (ловит DI-ошибку до прода).
- Существующие `StorageCircuitBreakerTest` (14) + `StorageTimeoutInterruptTest` (9) — PASS.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:test --tests "*Storage*"` | 30/30 PASS |
| `:karaoke-app:bootJar` | OK |
| `check-knowledge-structure.sh` | 9/9 OK |
| `check-knowledge-cross-links.sh` | 631/631 OK |
| `lint-knowledge.py --baseline` | PASS (no new) |

## Поведение после фикса

| Ситуация | До | После |
|---|---|---|
| remote OPEN, local жив | local FATAL_ERROR (ложно) | local работает; remote FATAL_ERROR |
| local OPEN | нет fail-fast (блок) | local FATAL_ERROR <1ms; remote работает |
| reset | один breaker | `?storage=local\|remote\|all` |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9).
- Проверить после рестарта: при нестабильном remote (`storage=remote` OPEN)
  локальная заливка (`UPLOAD_TO_LOCAL_STORE`) не падает; `GET
  /api/health/circuit-breaker` показывает оба состояния.

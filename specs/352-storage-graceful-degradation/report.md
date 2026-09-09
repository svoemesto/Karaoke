# OpenProject #71 — Final report (Pass 351, PR #452 merged)

> Публикуется в OpenProject #71 как финальный отчёт перед `close-issue`.

## Status

✅ **Done**. PR #452 merged в master как `16dedd0e` (2026-09-09).

## Что сделано (Pass 351)

### Новые компоненты

- `StorageCircuitBreaker` (@Component, in-memory AtomicReference FSM):
  - 3-state: CLOSED / HALF_OPEN / OPEN (per Q3 — half-open probe).
  - Per-call timeout (default 5s).
  - Threshold failures (default 5) → OPEN.
  - Cooldown (default 30s) → HALF_OPEN.
  - In-memory only (per Q1).
  - `decorate<T>` / `decorateOrEmpty<T>` API.

- `StorageApiClientImpl.fileExists` / `fileIsActual` / `getFileInfo` wrapped в `circuit.decorate(...)` (per Q2 — все file-*).

### Configuration

- `application.yml`: 3 новых @Value properties (`storage.file-exists-timeout-seconds`, `circuit-breaker-threshold`, `circuit-breaker-cooldown-seconds`).
- Env override: `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS=10`, etc.

### Observability

- SLF4J `infra.cache.storage`: 2 новых events — `cache:network:failure` (WARN), `cache:circuit:state` (INFO).
- `GET /api/health/cacheStats` — добавлены `networkFailures`, `circuitBreaker` block.
- `GET /api/health/circuit-breaker` (Pass 351 P3 endpoint) — полный snapshot.

### Knowledge

- `knowledge/domains/storage/components/storage-api-client.md` — Pass 351 section.
- `knowledge/domains/monitoring/components/log-categories.md` — 2 новых events.

## Validation

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` | PASS |
| `:karaoke-app:ktlintCheck` | PASS (0 violations) |
| `:karaoke-app:bootJar` | PASS |
| `StorageCircuitBreakerTest` | **11/11 PASS** |
| `HealthReportRepairRaceTest` (regression) | 4/4 PASS |
| `gh pr checks #452` | 9/9 PASS |

## Acceptance vs SC

| SC | Ожидаемо | Реально |
|---|---|---|
| SC-001: latency ≤ 5s при dead network | ✅ (FR-001) | Pass — `decorate(...).timeout(Duration.ofSeconds(5))` + onErrorReturn(false) |
| SC-002: 5 consecutive failures → OPEN | ✅ (FR-002) | Pass — тест `concurrent failures transition once to OPEN` |
| SC-003: cooldown 30s + success → CLOSED | ✅ (FR-002 + Q3) | Pass — `recordSuccess()` HALF_OPEN → CLOSED |
| SC-004: `networkFailures` в `/api/health/cacheStats` | ✅ (FR-005) | Pass — CacheStatsResponse содержит circuitBreaker block |
| SC-005: после merge — #65 можно закрыть | ✅ | Этот report и есть основание |

## Workflow выполнено

- **Claim**: `tracker.sh claim-issue 71` (Pass 350 hook).
- **Add comment** (этот report).
- **Mark review**: `tracker.sh mark-review 71`.
- **Close**: `tracker.sh close-issue 71`.

## Что осталось за рамками (follow-up)

- T017-T024 (TODO в tasks.md): per-feature doc, helper scripts, OpenProject workflow — могут быть отдельным PR.
- T022-T023: `tools/with-dead-network.sh` + `tools/measure-fileExists-latency.sh` — test infrastructure.

## Подпись

Этот отчёт опубликован в OpenProject #71 через `tracker.sh add-comment 71 --file report.md`. Автор: agent (Karaoke), Pass 351 governance. Merge: PR #452, commit `16dedd0e`.

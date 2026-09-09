# OpenProject #65 — Final close (root cause fixed, Pass 351)

> Публикуется в OpenProject #65 как финальный обоснование перед `close-issue`.

## Resolution

OpenProject #65 «Ошибка при проверке наличия файла в удаленном хранилище» —
**root cause finally fixed** через Pass 351 (PR #452, commit `16dedd0e`).

## История mitigations

| Pass | Что | Что делает / не делает |
|---|---|---|
| Pass 343 (PR #445) | perSong single-flight guard | Устраняет cascading retries в `HealthReport.repair-loop` (для одной песни). НЕ root cause для network outage. |
| Pass 344 (PR #446) | PollingCache TTL cache | Cache hit → 0 MinIO round-trip. Снижает частоту вызова. НЕ root cause. |
| Pass 345 (PR #448) | Persistent Postgres cache | Cache hit → 0 round-trip. **Переживает рестарт**. НЕ root cause для cold start с dead network. |
| **Pass 351 (PR #452)** ✅ | `StorageCircuitBreaker` + per-call timeout | **Root cause fix** — caller thread НЕ блокируется на 60s, `fileExists` возвращает `false` за `timeoutSeconds` (5s default), circuit breaker предотвращает каскадные retry. |

## Что именно починено (Pass 351)

- `StorageApiClientImpl.fileExists` / `fileIsActual` / `getFileInfo` обёрнуты в `StorageCircuitBreaker.decorate(...)`.
- При network outage (SocketTimeout, NoRouteToHost): возвращают `false` за `<timeoutSeconds>` (5s default), а НЕ 60s.
- При circuit OPEN (5 consecutive failures): `false` мгновенно, без MinIO call.
- При circuit HALF_OPEN (probe success): `recordSuccess()` → CLOSED, `failureCount=0`.

## Устранённые симптомы #65

| Симптом из #65 (Pass 295) | До (Pass 345) | После (Pass 351) |
|---|---|---|
| `Ошибка при проверке наличия файла: Connect timed out` | каждый cache-miss → 60s block | timeout `timeoutSeconds` (5s default) → false |
| `NoRouteToHostException` → 60s block | тот же | тот же — 5s timeout |
| «каскадные повторные попытки» | уже нет (Pass 343 guard) | дополнительно — circuit breaker fast-fail |
| «тормозит весь процесс» | cache hit спасает, но cache miss 60s | cache miss **тоже** ≤5s |
| thread leak при `flake-out` remote | не было защиты | нет — 5s timeout на caller thread |

## Validation (Pass 351)

- `:karaoke-app:ktlintCheck` PASS (0 violations).
- `:karaoke-app:test StorageCircuitBreakerTest` 11/11 PASS.
- `:karaoke-app:test HealthReportRepairRaceTest` 4/4 PASS (без регрессий).
- `gh pr checks #452` 9/9 PASS.

## Workflow выполнено

- **Claim**: `tracker.sh claim-issue 65` (Pass 350 hook).
- **Add comment** (этот report).
- **Mark review**: `tracker.sh mark-review 65`.
- **Close** (owner decision): `tracker.sh close-issue 65` — owner одобрил в этой сессии (явная команда «закрывай 71 и 65 таски»).

## Связанные артефакты

- `specs/344-storage-metadata-cache/report.md` — Pass 344 status report.
- `specs/344-storage-metadata-cache/report-65.md` — частичное смягчение (Pass 349 backfill).
- `specs/352-storage-graceful-degradation/` — Pass 351 implementation.
- `knowledge/domains/storage/components/storage-api-client.md` — Pass 351 section.

## Подпись

Этот отчёт опубликован в OpenProject #65 через `tracker.sh add-comment 65 --file report-65-final.md`. Author: agent (Karaoke), Pass 351.

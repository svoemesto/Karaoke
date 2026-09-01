---
status: Active
slug: 288-prod-diagnostics-logging
related:
  - ../features/187-site-traffic-anomaly-investigation.md
  - ../features/241-db-storage-perf-audit.md
  - ../features/248-authors-tiles-cache.md
  - ../domain/monitoring.md
  - ../architecture/observability.md
  - ../../specs/288-prod-diagnostics-logging/spec.md
  - ../../specs/288-prod-diagnostics-logging/contracts/log-format.md
---

# 288 — Расширенное логирование прода для отлова зависаний (LiveDoc)

> Drill-down — [specs/288-prod-diagnostics-logging/spec.md](../../specs/288-prod-diagnostics-logging/spec.md).
> Формат логов — [specs/288-prod-diagnostics-logging/contracts/log-format.md](../../specs/288-prod-diagnostics-logging/contracts/log-format.md).
> Runbook по корреляции — [docs/ops/log-correlation.md](../../docs/ops/log-correlation.md).

## Что делает

Прод-сервер `sm-karaoke.ru` (188.119.64.111) периодически подвисал на 7-10 минут. Первая итерация фиксов (спека 187) снизила фоновую нагрузку, но инциденты продолжались. Эта фича добавляет **диагностическую инфраструктуру** для root-cause analysis: post-hoc диагностика через `pg_log` + автоматические алерты через `ProdContainerCheck` + runbook.

**3 направления**:
1. **PostgreSQL runtime-параметры** (`ALTER SYSTEM SET` + `pg_reload_conf()` без рестарта):
   `log_min_duration_statement=1000`, `log_temp_files=0`, `log_lock_waits=on`, `log_autovacuum_min_duration=0`, `log_checkpoints=on`, `log_line_prefix='%m [%p] %q%u@%d from %h '`, `log_timezone/timezone='Europe/Moscow'`. Префикс строк с timestamp + PID + user@database + host.
2. **SLF4J в `ProdContainerCheck`** — категории `infra.prod.ping` (HTTP-пинг сайта) и `infra.prod.db` (JDBC-пинг прод-БД). Логируют durationMs, error, exceptionClass. WARN при ошибке, INFO при восстановлении (`ping:recovered downForMin=N`).
3. **TZ-синхронизация** Europe/Moscow между PostgreSQL, JVM karaoke-web, JVM karaoke-app — для однозначной корреляции по времени.

## Followup (после инцидента 2026-09-01 20:47-20:57 MSK)

- `pingSite()` переведён на `java.net.http.HttpClient` (Java 11+) — современный TLS stack решает ложные срабатывания на маршруте admin-машина → прод (MTU/TLS проблемы с `HttpURLConnection`).
- Диагностический fallback через `curl` (`ProcessBuilder`) при ошибке HttpClient: `INFO hint=javaClientIssue` (Java-проблема, ложная тревога) vs `WARN hint=realProdIssue` (реальный инцидент).
- Категория `infra.prod.ping` пополнилась событиями `ping:curlDiagnostic` (см. [contracts/log-format.md](../../specs/288-prod-diagnostics-logging/contracts/log-format.md)).

## Grep-маркеры

| Категория | Что | Когда логируется |
|-----------|-----|------------------|
| `infra.prod.ping - ping:failed` | WARN | HttpClient не смог (5 сек timeout) |
| `infra.prod.ping - ping:recovered` | INFO | Смена состояния WARNING → OK |
| `infra.prod.ping - ping:curlDiagnostic ok` | INFO | curl прошёл, Java упал → Java-проблема |
| `infra.prod.ping - ping:curlDiagnostic failed` | WARN | curl не прошёл → реальный инцидент |
| `infra.prod.db - db:failed` | WARN | JDBC-пинг прод-БД упал |

## User Stories (краткий список)

- **US1** (P1): Медленные SQL видны в pg_log с префиксом timestamp.
- **US2** (P1): `ProdContainerCheck` пишет структурированные WARN/INFO вместо `println`.
- **US3** (P1): Синхронизированная TZ во всех логах прода.
- **US4** (P2): Документация по корреляции (runbook).

## Acceptance Criteria

- **SC-001**: `docker logs karaoke-db --since "1m"` показывает префикс строк с `YYYY-MM-DD HH:MM:SS MSK`.
- **SC-002**: SQL > 1 сек логируется с `duration: NNNN ms`.
- **SC-003**: `grep "ProdContainerCheck: ping"` возвращает 0 (нет println).
- **SC-006**: Post-hoc диагностика инцидента за ≤ 15 минут (playbook в runbook).

## Связанные LiveDocs

- Domain: [monitoring.md](../domain/monitoring.md).
- Feature: [187-site-traffic-anomaly-investigation.md](187-site-traffic-anomaly-investigation.md), [241-db-storage-perf-audit.md](241-db-storage-perf-audit.md), [248-authors-tiles-cache.md](248-authors-tiles-cache.md).
- Architecture: [observability.md](../architecture/observability.md).
- Runbook: [docs/ops/log-correlation.md](../../docs/ops/log-correlation.md).
- Tool: [tools/analyze-prod-incident.sh](../../tools/analyze-prod-incident.sh).

## Код

- `karaoke-app/src/main/kotlin/.../monitor/checks/ProdContainerCheck.kt` — SLF4J + HttpClient + curlDiagnostic.
- `deploy/.env` — `WEB_JAVA_OPTS=-Xmx2g -Duser.timezone=Europe/Moscow` (TZ JVM).
- `deploy/docker-compose-database.yml` — `TZ: Europe/Moscow` (TZ контейнера).
- `deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql` — НЕ применён (см. спеку 289).

## История

- Создан: 2026-09-01 (Pass 282 + post-mortem инцидента 20:47-20:57 MSK).
- Последнее обновление: 2026-09-01 (followup: HttpClient + curlDiagnostic).
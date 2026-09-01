---
status: Active
slug: monitoring
type: bounded-context
related:
  - ../domain/stats.md
  - ../domain/identity.md
  - ../features/288-prod-diagnostics-logging.md
  - ../features/289-fix-statbysong-cache-on-cold-start.md
  - ../architecture/observability.md
---

# Domain: Monitoring

> Bounded context для мониторинга инфраструктуры и продуктовых метрик.

## Что делает

Регулярные проверки здоровья компонентов Karaoke (PostgreSQL, karaoke-web, karaoke-app, nginx) и алерты при деградации. Также — сбор логов для post-hoc анализа инцидентов.

## Ключевые компоненты

- **`MonitoringService`** (`karaoke-app/.../monitor/MonitoringService.kt`) — тикает раз в минуту, вызывает 7 monitor checks.
- **`MonitorCheck`** — интерфейс для всех проверок.
- **`MonitorRegistry`** — реестр проверок.
- **`MonitorAlert`** / **`MonitorSeverity`** (WARNING, CRITICAL) — структура алерта.

## Текущие проверки

| Check | Что | Файл |
|-------|-----|------|
| `ProdContainerCheck` | HTTP-пинг `https://sm-karaoke.ru/` + JDBC-пинг прод-БД | `monitor/checks/ProdContainerCheck.kt` |
| `RenderQueueStalledCheck` | Очередь рендера не stalled | `monitor/checks/RenderQueueStalledCheck.kt` |
| `LaneStalledCheck` | Лейн очереди не stalled | `monitor/checks/LaneStalledCheck.kt` |
| `TelegramPollingDisabledCheck` | Telegram polling работает | `monitor/checks/TelegramPollingDisabledCheck.kt` |
| `UnreadChatMessagesCheck` | Нет непрочитанных сообщений | `monitor/checks/UnreadChatMessagesCheck.kt` |
| `SubmittedAssignmentsCheck` | Задания редактора не зависли | `monitor/checks/SubmittedAssignmentsCheck.kt` |
| `StemJobsStuckCheck` | Stem jobs не зависли | `monitor/checks/StemJobsStuckCheck.kt` |

## Логирование

Фича 288 добавила SLF4J-логирование в `ProdContainerCheck` через категории:
- `infra.prod.ping` — HTTP-пинг сайта (WARN при ошибке, INFO при восстановлении).
- `infra.prod.db` — JDBC-пинг прод-БД.

Фича 289 добавила категорию `infra.cache.statbysong` для `StatBySong.refreshCache()`.

## Runbook

[`docs/ops/log-correlation.md`](../../docs/ops/log-correlation.md) — карта источников логов, grep-маркеры, типичные сценарии.

## Код

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitoringService.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorCheck.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/*.kt`

## История

- Создан: 2026-09-01 (фича 288-prod-diagnostics-logging — добавлены LiveDocs cross-links).
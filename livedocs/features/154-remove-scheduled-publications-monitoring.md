---
status: Active
slug: 154-remove-scheduled-publications-monitoring
related:
  - ../domain/publishing.md
  - ../domain/rendering.md
  - ../features/128-news-publish-templates.md
  - ../../specs/154-remove-scheduled-publications-monitoring/spec.md
  - ../../specs/288-prod-diagnostics-logging/spec.md
---

# 154 — Убрать мониторинг запланированных публикаций (LiveDoc)

> Drill-down — [specs/154-remove-scheduled-publications-monitoring/spec.md](../../specs/154-remove-scheduled-publications-monitoring/spec.md).

## Что делает

Сейчас в админ-мониторинге (`/monitoring`, «светофор» + список алертов)
есть проверка **«горизонта запланированных публикаций»** — но сейчас
публикации **не планируются** (они генерируются в нужное время ботом, см.
`features/128-news-publish-templates.md` и `113-telegram-demo-publish.md`),
так что этот тип мониторинга стал ненужным.

**Фикс**: убрать правило мониторинга из списка, не генерировать алерты по
«горизонт запланированных публикаций».

## User Stories (краткий список)

- **US1** (P1): Мониторинг больше не жалуется на горизонт запланированных
  публикаций (тип проверки удалён).

## Functional Requirements (указатель)

- **FR-001**: Удалить правило из `MonitoringService.kt`.
- **FR-002**: Сохранить остальные правила (RenderQueueStalledCheck, LaneStalledCheck).

## Acceptance Criteria

- [ ] **AC1**: «Горизонт запланированных публикаций» НЕ появляется в списке алертов.
- [ ] **AC2**: Остальные алерты работают как раньше.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md), [rendering.md](../domain/rendering.md)
- Feature: [128-news-publish-templates.md](128-news-publish-templates.md), [113-telegram-demo-publish.md](113-telegram-demo-publish.md)
- Architecture: [observability.md](../architecture/observability.md) — где живут проверки мониторинга

## Код

- `karaoke-app/.../monitoring/MonitoringService.kt` — удалить правило.
- `karaoke-web/.../controllers/AdminMonitoringController.kt` — не показывать в API.

## `ProdContainerCheck`: SLF4J-логирование (288-prod-diagnostics-logging)

Спека [288-prod-diagnostics-logging](../../specs/288-prod-diagnostics-logging/spec.md) добавила
структурированное SLF4J-логирование в `ProdContainerCheck` (одну из проверок, оставшихся
в `MonitorRegistry`):

- **WARN** `infra.prod.ping - ping:failed url=... durationMs=N error="..." exceptionClass=...`
  — при неуспешном HTTP-пинге `https://sm-karaoke.ru/` (с exception + stacktrace через SLF4J).
- **INFO** `infra.prod.ping - ping:recovered url=... downForMin=N` — при смене состояния
  WARNING/CRITICAL → OK (только при переходе, не каждый тик).
- **WARN** `infra.prod.db - db:failed host=... port=... durationMs=N error="..."` — при
  неуспешном JDBC-пинге прод-БД (только `host`+`port`, **не** JDBC URL — FR-022 / Constitution § VIII.5).
- **NO-OP** в обычном режиме (когда пинги постоянно OK) — минимум шума.

Эти категории используются как grep-маркеры для корреляции с `pg_log` PostgreSQL и nginx
`access.log` по общему timestamp (TZ Europe/Moscow после FR-007/FR-008/FR-010).

Контракт формата: [specs/288-prod-diagnostics-logging/contracts/log-format.md](../../specs/288-prod-diagnostics-logging/contracts/log-format.md).
Runbook по корреляции: [docs/ops/log-correlation.md](../../docs/ops/log-correlation.md).

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-09-01 (288-prod-diagnostics-logging — SLF4J-логирование ProdContainerCheck)
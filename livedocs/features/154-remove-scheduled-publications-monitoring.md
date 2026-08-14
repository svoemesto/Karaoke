---
status: Active
slug: 154-remove-scheduled-publications-monitoring
related:
  - ../domain/publishing.md
  - ../domain/rendering.md
  - ../features/128-news-publish-templates.md
  - ../../specs/154-remove-scheduled-publications-monitoring/spec.md
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

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
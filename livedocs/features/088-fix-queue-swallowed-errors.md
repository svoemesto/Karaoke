---
status: Active
slug: 088-fix-queue-swallowed-errors
related:
  - ../domain/processing.md
  - ../features/087-fix-shared-db-connection.md
  - ../features/091-fix-connection-leak.md
  - ../architecture/L3-components.md
  - ../../specs/088-fix-queue-swallowed-errors/spec.md
  - ../../archive/docs/features/async-process-queue.md
---

# 088 — Единообразная обработка сбоев БД в очереди задач (LiveDoc)

> Drill-down — [specs/088-fix-queue-swallowed-errors/spec.md](../../specs/088-fix-queue-swallowed-errors/spec.md).

## Что делает

В `KaraokeProcessWorker.doStart()` обращения к БД обрабатывали сбои
непоследовательно:
- `KaraokeProcess.save()` — пробрасывает `SQLException` наружу (правильно, retry
  ловит).
- `getCountWaiting()` и `getProcessesToStart()` — **молча** возвращают
  заглушку (0 / пустую карту), НЕ пробрасывают.

При живой проверке (docker pause karaoke-db на 4.5 минуты) — `doStart()` не
упал ни разу, retry ни разу не сработал, хотя БД была реально недоступна.

**Фикс** — единообразная обработка:
- Все обращения к БД в `doStart()` пробрасывают `SQLException`.
- Retry (из `087-fix-shared-db-connection`) срабатывает на любую из них.
- Другие места (`RenderQueueStalledCheck`, `LaneStalledCheck`, SSE-уведомление
  о count) сохраняют свою текущую семантику (с общим catch на уровне сервиса).

## User Stories (краткий список)

- **US1** (P1): Любой сбой БД в `doStart()` ведёт себя предсказуемо (retry).

## Functional Requirements (указатель)

- **FR-001**: `getCountWaiting()` и `getProcessesToStart()` пробрасывают `SQLException`.
- **FR-002**: Retry-triggered мониторинг (см. `087`).

## Acceptance Criteria

- [ ] **AC1**: `docker pause karaoke-db` → воркер уходит в retry-state, логи показывают retry.
- [ ] **AC2**: `docker unpause` → воркер возобновляется без потери заданий.

## Связанные LiveDocs

- Domain: [processing.md](../domain/processing.md) (queue)
- Feature: [087-fix-shared-db-connection.md](../features/087-fix-shared-db-connection.md), [091-fix-connection-leak.md](../features/091-fix-connection-leak.md)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-app/.../KaraokeProcessWorker.kt` — `doStart()` — убрать inner catch
- Backend: `karaoke-app/.../KaraokeProcess.kt` — `getCountWaiting()`, `getProcessesToStart()` — throw on SQLException

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
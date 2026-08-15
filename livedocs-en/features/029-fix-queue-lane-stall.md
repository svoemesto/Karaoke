---
status: Active
slug: 029-fix-queue-lane-stall
related:
  - ../domain/processing.md
  - ../features/087-fix-shared-db-connection.md
  - ../architecture/queue-lanes.md
  - ../../specs/029-fix-queue-lane-stall/spec.md
---

# 029 — Fix queue lane stall (LiveDoc)

> Drill-down — [specs/029-fix-queue-lane-stall/spec.md](../../specs/029-fix-queue-lane-stall/spec.md).

## What it does

При запущенных процессах в разных лейнах (`threadId` 1 и 2) — после
завершения/ошибки текущего процесса **не стартовал следующий по очереди**.
Очередь «залипала» в этом лейне, требовался ручной restart.

**Корневая причина**: race в `KaraokeProcessWorker.doStart()` при выборе
следующего задания для лейна — при определённых условиях задание помечалось
как `STARTED` двумя потоками одновременно.

**Фикс**:
- Атомарный `SELECT ... FOR UPDATE SKIP LOCKED` для выбора следующего задания в лейне.
- Дедуп при старте (`UPDATE tbl_processes SET status='STARTED' WHERE id=? AND status='QUEUED'` —
  проверка возврата updated_rows).
- Safety-net `RenderQueueStalledCheck` — ловит зависания (см. `087-fix-shared-db-connection`).

## User Stories (краткий список)

- **US1** (P1): Надёжный автостарт следующего задания в лейне.

## Functional Requirements (указатель)

- **FR-001**: Atomic `SELECT FOR UPDATE SKIP LOCKED`.
- **FR-002**: WHERE-условие в UPDATE — только при `status='QUEUED'`.
- **FR-003**: Telemetry: логирование попыток выбора задания.

## Acceptance Criteria

- [ ] **AC1**: 100 заданий в двух лейнах параллельно → нет залипаний.
- [ ] **AC2**: При сбое текущего задания — следующее стартует ≤ 5 сек.

## Related LiveDocs

- Domain: [processing.md](../domain/processing.md) (queue)
- Feature: [087-fix-shared-db-connection.md](../features/087-fix-shared-db-connection.md)
- Architecture: [queue-lanes.md](../architecture/queue-lanes.md)

## Code

- Backend: `karaoke-app/.../KaraokeProcessWorker.kt` — `doStart()` — atomic SQL
- Backend: `karaoke-app/.../KaraokeProcess.kt` — `getProcessesToStart()` — `FOR UPDATE SKIP LOCKED`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14
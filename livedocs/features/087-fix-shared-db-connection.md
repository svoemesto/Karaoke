---
status: Active
slug: 087-fix-shared-db-connection
related:
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../features/088-fix-queue-swallowed-errors.md
  - ../../specs/087-fix-shared-db-connection/spec.md
---

# 087 — Изоляция JDBC по потокам + retry очереди при сетевом сбое (LiveDoc)

> Drill-down — [specs/087-fix-shared-db-connection/spec.md](../../specs/087-fix-shared-db-connection/spec.md).

## Что делает

Singleton `WORKING_DATABASE` кешировал один `java.sql.Connection`, общий для
HTTP-потоков и потока очереди `KaraokeProcessWorker`. PostgreSQL JDBC Connection
**не рассчитан** на конкурентное использование из разных потоков — SocketTimeout
«Read timed out», «Connection already closed», падение цикла очереди.

**Фикс**:
- `KaraokeConnection.getConnection()` → **ThreadLocal-кэширование** (по одному
  Connection на поток).
- Self-healing: если `isClosed`/`invalid` — пересоздать.
- Воркер очереди — **retry при сетевом сбое** (5 попыток с нарастающей паузой).
- Альтернатива авто-restart: воркер перезапускается через RenderQueueStalledCheck
  (≈1 минута) — ручного one-click resume не нужно.

## User Stories (краткий список)

- **US1** (P1): Параллельная работа очереди и админки НЕ роняет друг друга.

## Functional Requirements (указатель)

- **FR-001**: ThreadLocal-кэш в `KaraokeConnection`.
- **FR-002**: Retry 5x с exponential backoff.
- **FR-003**: Self-healing на `isClosed`/`invalid`.
- **FR-004**: Авто-restart воркера в RenderQueueStalledCheck.

## Acceptance Criteria

- [ ] **AC1**: HTTP + queue одновременно — без конфликтов.
- [ ] **AC2**: `docker pause karaoke-db` → queue retry 5x, recovery.
- [ ] **AC3**: Воркер автоматически возобновляется без ручного resume.

## Связанные LiveDocs

- Domain: [processing.md](../processing.md)
- Feature: [088-fix-queue-swallowed-errors.md](../features/088-fix-queue-swallowed-errors.md), [091-fix-connection-leak.md](../features/091-fix-connection-leak.md)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-app/.../KaraokeConnection.kt` — ThreadLocal
- Backend: `karaoke-app/.../monitoring/RenderQueueStalledCheck.kt` — авто-restart
- Backend: `karaoke-app/.../KaraokeProcessWorker.kt` — retry loop

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
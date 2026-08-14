---
status: Active
slug: 091-fix-connection-leak
related:
  - ../domain/processing.md
  - ../features/087-fix-shared-db-connection.md
  - ../architecture/L3-components.md
  - ../../specs/091-fix-connection-leak/spec.md
---

# 091 — Устранить утечку JDBC от одноразовых потоков очереди (LiveDoc)

> Drill-down — [specs/091-fix-connection-leak/spec.md](../../specs/091-fix-connection-leak/spec.md).

## Что делает

Регрессия от `087-fix-shared-db-connection`: `KaraokeConnection.getConnection()`
кеширует по одному физическому `java.sql.Connection` на **каждый ThreadLocal**,
и **никогда не закрывает**. Для долгоживущих потоков (Tomcat pool,
`KaraokeProcessWorker.doStart()`) — это корректно. Но `KaraokeProcessThread`
(`KaraokeProcessWorker.kt`, `extends Thread()`) создаётся **на каждое задание
очереди** и никогда не переиспользуется → каждое задание → новое соединение →
утечка → `FATAL: too many clients already`.

**Фикс**:
- Одноразовые потоки (флаг `isOneShot = true` в конструкторе) **явно**
  освобождают соединение в `finally` блоке после `run()`.
- Долгоживущие потоки остаются с ThreadLocal-кэшированием (как решил `087`).

**Эффект**: при тысячах заданий очереди число одновременно открытых
соединений остаётся стабильным (а не растёт линейно).

## User Stories (краткий список)

- **US1** (P1): 1000+ заданий очереди НЕ исчерпывают лимит подключений БД.

## Functional Requirements (указатель)

- **FR-001**: `KaraokeProcessThread(isOneShot=true) : Thread()` — в `run()` добавить `finally { KaraokeConnection.releaseForThisThread() }`.
- **FR-002**: `KaraokeConnection.releaseForThisThread()` — закрыть ThreadLocal-cached connection.
- **FR-003**: Тест: 1000 заданий → `pg_stat_activity` показывает стабильное число.

## Acceptance Criteria

- [ ] **AC1**: 1000 заданий очереди подряд → `pg_stat_activity` показывает ≤ 5 активных соединений.
- [ ] **AC2**: «FATAL: too many clients» больше не возникает.

## Связанные LiveDocs

- Domain: [processing.md](../processing.md) (queue, ThreadId lanes)
- Feature: `specs/087-fix-shared-db-connection` (предыдущая регрессия)
- Architecture: [L3-components.md](../architecture/L3-components.md) (Queue компонент)

## Код

- Backend: `karaoke-app/.../KaraokeConnection.kt` — добавить `releaseForThisThread()`
- Backend: `karaoke-app/.../KaraokeProcessWorker.kt` — `KaraokeProcessThread` + finally
- Tests: `karaoke-app/src/test/.../ConnectionLeakTest.kt`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
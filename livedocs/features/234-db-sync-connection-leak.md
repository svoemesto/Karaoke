---
status: Active
slug: 234-db-sync-connection-leak
related:
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../features/087-fix-shared-db-connection.md
  - ../features/091-fix-connection-leak.md
  - ../features/174-fix-stats-connection-leak.md
  - ../../specs/234-db-sync-connection-leak/spec.md
  - ../../archive/docs/features/dual-db-sync.md
---

# 234 — Singleton Connection-фабрики: устранение утечки JDBC при «Синхронизации БД в 1 клик» (LiveDoc)

> Drill-down — [specs/234-db-sync-connection-leak/spec.md](../../specs/234-db-sync-connection-leak/spec.md).

## Что делает

Фабрики `Connection.Companion.local()`/`remote()`/`virtual()` в `karaoke-app/.../Connection.kt` и `karaoke-web/.../Connection.kt` до этой спеки возвращали **новый инстанс** `Connection` на каждый вызов. У каждого инстанса — свой `ThreadLocal<java.sql.Connection?>`, который при первом `getConnection()` открывал **отдельное физическое JDBC-соединение** к Postgres. Один HTTP-запрос `POST /api/sync/oneclick` = 18 `SyncTarget` × 2 БД (`local` + `remote`) = **36 свежих инстансов Connection** на одном Tomcat-потоке, которые никогда не закрывались (`KaraokeConnection.closeThreadConnection()` явно запрещён к вызову из долгоживущих потоков). При `max_connections=100` (Postgres дефолт) пул быстро упирался — каскад `FATAL: sorry, too many clients already` на каждом клике.

**Фикс:**
- Фабрики теперь **singleton** через Kotlin `by lazy(LazyThreadSafetyMode.SYNCHRONIZED)` — один инстанс `Connection` на процесс, его `ThreadLocal` кеширует по одному физическому каналу **на поток** (контракт спеки `087-fix-shared-db-connection` сохранён — без общего канала, без `SocketTimeoutException`).
- Симметричный фикс в `karaoke-web/.../Connection.kt` (та же утечка была в `webvue3`-эндпоинтах через `withDb { ... }`).
- Дополнительно: `KaraokeConnection.getConnection()` и `closeThreadConnection()` теперь логируют сбои через SLF4J `log.warn(...)` с placeholder'ами `target={} thread={} cause={}` в дополнение к существующему `println` — для структурированной диагностики инцидентов в Kibana/Loki.

## User Stories (краткий список)

- **US1** (P1, MVP): «Синхронизация БД в 1 клик» работает без каскада `too many clients`.
- **US2** (P2): При реальной перегрузке БД — структурированный SLF4J `warn` с `target`/`thread`/`cause`.
- **US3** (P1): Существующие 174+ вызовов `getConnection()` продолжают работать без регрессий.

## Functional Requirements (указатель)

- **FR-001**: `Connection.Companion.local()`/`remote()` — singleton через `by lazy(SYNCHRONIZED)`.
- **FR-002**: `Connection.Companion.virtual()` — singleton, поведение не меняется.
- **FR-003**: Singleton-инстансы — thread-safe (double-checked locking через `SYNCHRONIZED` `lazy`).
- **FR-004**: SLF4J `log.warn` с полями `target`/`thread`/`cause` при сбоях `getConnection()` и `closeThreadConnection()`.
- **FR-005**: `KaraokeConnection.log = LoggerFactory.getLogger(...)` — без новых зависимостей.
- **FR-006**: Сигнатура `getConnection(): java.sql.Connection?` сохранена — 174+ вызывающих мест не затронуты.
- **FR-007**: `KaraokeConnection.closeThreadConnection()` — контракт спеки `091` сохранён.
- **FR-008**: Симметричный singleton в `karaoke-web/.../Connection.kt`.
- **FR-009**: Существующие `withDb { ... }` хелперы в контроллерах остаются (после singleton становятся избыточными, но не ломают логику).
- **FR-010**: KDoc `Connection.kt` обновлён — явно указано singleton + ссылка на спеку `087`.
- **FR-011**: Существующие тесты (если есть) проходят без изменений.
- **FR-012**: Документация `archive/docs/features/dual-db-sync.md` обновлена (секция «Singleton Connection-фабрики» + пункт в «Известные ловушки»).
- **FR-013**: HikariCP/connection pool **НЕ** включается в эту спеку (отдельная задача при необходимости).

## Acceptance Criteria

- [ ] **AC1** (SC-001): 10 кликов «Синхронизация БД в 1 клик» подряд → `docker logs karaoke-app --since 5m | grep -c "too many clients"` = **0**.
- [ ] **AC2** (SC-002): `pg_stat_activity WHERE application_name='karaoke-app'` ≤ **10** при 10 кликах.
- [ ] **AC3** (SC-003): Искусственная перегрузка БД (`pg_terminate_backend` или `max_connections=5`) → структурированный `WARN KaraokeConnection connect failure target=local|remote thread=<name> cause=<msg>` через SLF4J.
- [ ] **AC4** (SC-004): Smoke-тест (Статистика + редактор + sync по одной сущности + фоновая задача + «1 клик») — без регрессий.
- [ ] **AC5** (SC-005): 5 параллельных HTTP-запросов к разным эндпоинтам → `pg_stat_activity` ≤ **30**.

## Связанные LiveDocs

- Domain: [processing.md](../domain/processing.md)
- Feature: [087-fix-shared-db-connection.md](../features/087-fix-shared-db-connection.md) (предшественник — ThreadLocal-per-поток), [091-fix-connection-leak.md](../features/091-fix-connection-leak.md) (`closeThreadConnection()` для одноразовых потоков), [174-fix-stats-connection-leak.md](../features/174-fix-stats-connection-leak.md) (аналогичная проблема для дашборда)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt` — singleton `LOCAL_INSTANCE`/`REMOTE_INSTANCE`/`VIRTUAL_INSTANCE` через `by lazy(SYNCHRONIZED)`
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt` — SLF4J `log.warn` при сбоях
- Backend: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/Connection.kt` — симметричный singleton для `webvue3`-эндпоинтов
- Docs: `archive/docs/features/dual-db-sync.md` — секция «Singleton Connection-фабрики» + пункт в «Известные ловушки»

## История

- Создан: 2026-08-16
- Последнее обновление: 2026-08-16

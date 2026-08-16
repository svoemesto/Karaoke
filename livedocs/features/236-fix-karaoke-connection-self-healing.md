---
status: Active
slug: 236-fix-karaoke-connection-self-healing
related:
  - ../domain/processing.md
  - ../features/087-fix-shared-db-connection.md
  - ../features/234-db-sync-connection-leak.md
---

# 236 — Self-healing `KaraokeConnection.getConnection()` после неудачной попытки (LiveDoc)

> Drill-down — [`specs/236-fix-karaoke-connection-self-healing/spec.md`](../../specs/236-fix-karaoke-connection-self-healing/spec.md).

## Что делает

В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt` метод
`getConnection()` (line 49) при входе в ветку пересоздания соединения **сбрасывает
`ThreadLocal`** до попытки `DriverManager.getConnection(...)`. Раньше при исключении в
`ThreadLocal` оставалось закрытое/протухшее соединение от предыдущего успешного
открытия — следующий вызов `getConnection()` в этом же потоке проходил проверку
`conn != null && !conn.isClosed` и возвращал мёртвый объект. Любой `createStatement`/
`prepareStatement` на нём падал с `org.postgresql.util.PSQLException: Соединение уже
было закрыто` (`PgConnection.checkClosed`, `PgConnection.java:1015`).

Дополнительно: в SLF4J `warn` добавлено поле `urlHost` (хост из JDBC URL без
credentials) — сразу видно, к какому хосту/порту шла попытка. Полезно при
расследовании инцидентов, когда `DB_REMOTE_HOST` пуст или ведёт на закрытый порт.

## Контекст инцидента

В проде контейнер `karaoke-app` поднялся без `DB_REMOTE_HOST` в env (URL SERVER-БД
вырождался в `jdbc:postgresql://:5433/karaoke...`). Каждый вызов
`Connection.remote().getConnection()` падал на `DriverManager.getConnection(...)`. Из-за
описанного дефекта self-healing — следующий вызов в потоке планировщика
(`SubmittedAssignmentsCheck`, `StemJobPollScheduler`) и HTTP-контроллера
(`SongEditorController.submittedCount`) переиспользовал мёртвое соединение →
каскад `PSQLException: Соединение уже было закрыто` в логах контейнера.

Решено в два этапа:

1. **Немедленно**: оператор прописал `DB_REMOTE_HOST` в env контейнера (отдельный
   коммит в `application.yml`, не часть спеки).
2. **Корневой фикс**: спека 236 — self-healing в `KaraokeConnection.getConnection()`
   + `urlHost` в warn.

## User Stories (краткий список)

- **US1** (P1, MVP): при сбое `DriverManager.getConnection(...)` следующий вызов
  `getConnection()` в том же потоке **гарантированно** пытается открыть новое
  соединение, а не переиспользует закрытое.
- **US2** (P2): SLF4J `warn` содержит `urlHost` — оператор/агент сразу видит, к
  какому хосту/порту шла неудачная попытка (полезно при `DB_REMOTE_HOST=""` или
  неверном env).

## Functional Requirements (указатель)

- **FR-001**: `KaraokeConnection.getConnection()` при входе в ветку пересоздания
  (`conn == null || conn.isClosed || !conn.isValid(3)`) **обязательно** вызывает
  `threadLocalConnection.set(null)` до `DriverManager.getConnection(...)`.
- **FR-002**: В блоке `catch` существующий `println` (для stdout контейнера) сохранён.
- **FR-003**: В SLF4J `warn` добавлено поле `urlHost` — вычисляется как
  `url.substringAfter("://").substringBefore("/")` (без credentials, безопасно для логов).
- **FR-004**: Существующий контракт `getConnection(): java.sql.Connection?` сохранён —
  179 вызывающих мест не затронуты.
- **FR-005**: Фикс работает для **обоих** наследников `KaraokeConnection` —
  `karaoke-app/.../Connection.kt` (LOCAL/SERVER/VIRTUAL) и `karaoke-web/.../Connection.kt`
  (та же иерархия).
- **FR-006**: KDoc `KaraokeConnection` обновлён — ссылка на спеку 236, явно описан
  сброс `ThreadLocal` при неудачной попытке.

## Acceptance Criteria

- [ ] **AC1** (SC-001): при принудительном сбое подключения (например,
  `DB_REMOTE_HOST` ведёт на закрытый порт) в логах видны **только**
  `WARN KaraokeConnection connect failure target=SERVER thread=<name> urlHost=<host>:<port> cause=<msg>` —
  без последующего `PSQLException: Соединение уже было закрыто` в этом же потоке.
- [ ] **AC2** (SC-002): после починки env (`DB_REMOTE_HOST` указывает на живую БД)
  `docker logs karaoke-app --since 5m | grep -c "connect failure target=SERVER"` →
  **0** в течение 5 минут нормальной работы планировщиков.
- [ ] **AC3** (SC-003): smoke-тест — Статистика + редактор песен + sync по одной
  сущности + фоновая задача — без регрессий.
- [ ] **AC4** (SC-004): `karaoke-web` контейнер — `getConnection()` в
  `webvue3`-эндпоинтах (новости, шаблоны, словари) продолжает работать без регрессий.

## Связанные LiveDocs

- Domain: [processing.md](../domain/processing.md)
- Feature: [087-fix-shared-db-connection.md](../features/087-fix-shared-db-connection.md) (ThreadLocal-per-поток),
  [234-db-sync-connection-leak.md](../features/234-db-sync-connection-leak.md)
  (singleton Connection-фабрики, SLF4J warn — предшественник)

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`
  — `getConnection()`: сброс `ThreadLocal` + `urlHost` в warn
- Наследники (получают фикс автоматически):
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt` (LOCAL/SERVER/VIRTUAL)
  - `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/Connection.kt` (webvue3)

## История

- Создан: 2026-08-16
- Последнее обновление: 2026-08-16

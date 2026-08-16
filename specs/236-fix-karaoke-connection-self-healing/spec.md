# 236 — Self-healing `KaraokeConnection.getConnection()` после неудачной попытки

## Контекст

В проде контейнер `karaoke-app` поднялся без `DB_REMOTE_HOST` в env. URL SERVER-БД
вырождался в `jdbc:postgresql://:5433/karaoke?...`. Каждый вызов
`Connection.remote().getConnection()` падал на `DriverManager.getConnection(...)`.

Из-за дефекта self-healing в `KaraokeConnection.getConnection()` (line 49) при
исключении в `ThreadLocal` оставалось закрытое соединение от предыдущего успешного
открытия. Следующий вызов в этом же потоке проходил проверку
`conn != null && !conn.isClosed` → возвращал мёртвый объект → любой `createStatement`
падал с `PSQLException: Соединение уже было закрыто`
(`org.postgresql.jdbc.PgConnection.checkClosed`, `PgConnection.java:1015`).

Каскад затронул:
- `SubmittedAssignmentsCheck.run` (monitor/checks/SubmittedAssignmentsCheck.kt:22)
- `StemJobPollScheduler.pollWaiting` (StemJobPollScheduler.kt:46)
- `SongEditorController.submittedCount` через `withDb` (controllers/SongEditorController.kt:683)

## Решение

1. **Немедленно**: оператор прописал `DB_REMOTE_HOST` в env контейнера `karaoke-app`.
2. **Корневой фикс**: `KaraokeConnection.getConnection()` при входе в ветку
   пересоздания **обязательно** сбрасывает `ThreadLocal` до попытки
   `DriverManager.getConnection(...)`. В SLF4J `warn` добавлено поле `urlHost` —
   хост из JDBC URL без credentials.

## Functional Requirements

См. `livedocs/features/236-fix-karaoke-connection-self-healing.md` (FR-001..FR-006).

## Acceptance Criteria

См. LiveDoc (AC1..AC4).

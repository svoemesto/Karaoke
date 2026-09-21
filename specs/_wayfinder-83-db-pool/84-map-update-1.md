## Destination

Закрыть задачу #83 без регрессий: при быстром переключении страниц в админке (50 песен на странице × переключение через 1-2 сек) больше **не должно** возникать каскада `KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already`. Результат — спека/правка, по которой пул Postgres не превышает `max_connections=100` даже при агрессивном UI-переключении.

## Notes

**Skill sets** для сессий по этой карте:
- Knowledge-first MUST #0 (`AGENTS.md`) — особенно `domains/persistence/`, `domains/health/`, `system/frontend/vuex-patterns.md`.
- Прецеденты: #65 (race condition), #69 (StorageMetadataCache), #75 (Pass 364 async cold-start), #087 (ThreadLocal), #234/#236 (connection leak + self-healing).
- Логи контейнера `karaoke-app` — `pool-2-thread-*` (Tomcat worker pool) — точка диагностики.
- Конституция (`constitution.md`) — raw JDBC, KDoc 100%.

**Текущее состояние**:
- `WORKING_DATABASE` = `Connection.local()` (singleton, Pass 234).
- `KaraokeConnection.getConnection()` кеширует **по одному физическому JDBC на поток** через `ThreadLocal` (Pass 087 — `fix-shared-db-connection`).
- `SongsTable.vue` лимитирует HR-запросы `HR_MAX_CONCURRENT=3` на стороне фронта (Pass 341).
- Однако **сам HR-запрос `/api/song/healthReportList` дёргает `recomputeAndBroadcast(songId)`** на стороне backend, который:
  1. `Song.loadFromDbById(id)` — `getConnection()` (ThreadLocal: новый connection для нового Tomcat worker-thread).
  2. `song.healthReportList()` — для каждой `(KaraokeFileType, Location)` комбинации дёргает `StorageMetadataCache.cachedFileExistsAsync()` (отдельные треды `pool-2-thread-*`, см. логи 2026-09-13).
  3. `reconcilePlayerReadinessFlags()` → `Song.saveToDb()` ещё одно соединение.
- Переключение страниц × 50 песен × быстрые клики → все worker-threads активны → суммарное количество JDBC connection превышает `max_connections=100`.

**Домены** (Knowledge-first):
- `domains/persistence/domain.md` — `KaraokeDbTable`, `KaraokeConnection`, "connection leak" gap.
- `domains/health/components/health-report.md` — `getHealthReportList`, `recomputeAndBroadcast`.
- `domains/catalog/components/song-entity.md` — `healthReportList()` на уровне Song.
- `system/frontend/vuex-patterns.md` — `HR_MAX_CONCURRENT=3`, SSE-driven updates.
- ADR `local-0004-lazy-eager-load-webvue3-pagination` — server-side pagination.
- ADR `0001-raw-jdbc.md` — нет JPA/Hibernate, raw JDBC.

## Decisions so far

- [**[#85] Корневая причина: кто и как забивает DB connection pool**](#85): узкое место определено — неограниченный `cacheFillerExecutor` в `StorageMetadataCache.kt:65-68` (`Executors.newCachedThreadPool()` без аргументов = `maxPoolSize=Integer.MAX_VALUE`); каждый REMOTE cache miss создаёт новый ThreadLocal-connection. Резолюция: фиксы A (executor `maxPoolSize=16`) + B (karaoke-web Tomcat `threads.max: 50`). Полный отчёт: `research/83-db-pool-root-cause/REPORT.md`.

## Not yet specified

1. **Batching на стороне бэкенда**: `POST /api/song/healthReportListBatch?ids=...` — снижает HTTP/worker-thread overhead, но **необязательно** после фикса A. Зависит от того, хватит ли executor limit. Выпускник после наблюдения за результатом фикса #89.
2. **Приоритизация страниц** в `SongsTable.vue` (#86, prototype): алгоритм владельца — вытеснение в начало очереди + всплытие. Может быть overkill после фикса executor. Тоже зависит от результата #89.
3. **Мониторинг**: метрика «сколько одновременно JDBC-соединений открыто» (через `pg_stat_activity` scrape или Spring Boot Actuator) — помогает **предвидеть** подобные проблемы. Выпускник после фикса A и B.

## Out of scope

- Полная миграция с ThreadLocal на HikariCP — это не баг-фикс, а рефакторинг архитектуры. Если выбрать, оформить **отдельной** спекой. Прецедент #087 вводил ThreadLocal **специально** против shared connection races.
- Замена Spring Boot на другую runtime — не обсуждается.
- **Изменения Postgres `max_connections`** (например до 200) — **out of scope для этой карты**. Требует согласования с владельцем БД и не решает корневую причину. Если владелец хочет — отдельный тикет, не #83.

## Связанные документы

- `knowledge/domains/persistence/domain.md`
- `knowledge/domains/health/components/health-report.md`
- `knowledge/system/frontend/vuex-patterns.md`
- `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`
- `research/83-db-pool-root-cause/REPORT.md` — root cause analysis.
- `specs/_wayfinder-83-db-pool/85-research-resolution.md` — resolution для #85.
- OpenProject #65, #69, #75, #87, #234, #236 — прецеденты и смежные фиксы.
# #85 Resolution — Корневая причина найдена

> **Resolution**: 2026-09-13, wayfinder-сессия после research-агента.
> **Источник**: `research/83-db-pool-root-cause/REPORT.md` (25 761 байт, SHA256 `ac86fc25b991ca7f4bf6564b9a847fcfaab6b3b1268b738de44e4de5ec8f4ffe`).

## Verdict (в 1 строку)

**Узкое место определено**: `cacheFillerExecutor` в `StorageMetadataCache.kt:65-68` — `Executors.newCachedThreadPool()` без аргументов = `maxPoolSize = Integer.MAX_VALUE`. Каждый REMOTE cache miss порождает **новый** ThreadLocal-connection к Postgres. При быстром переключении страниц это превышает `max_connections = 100`.

## Ключевые факты (для спеки)

1. **`StorageMetadataCache.kt:65-68`** — `Executors.newCachedThreadPool()`. KDoc врёт (пишет `maxPoolSize=16`).
2. **`StorageMetadataCache.kt:340, 291`** — `upsert` → `withConn { conn -> ... }` → `Connection.local().getConnection()` = новый физический JDBC на каждый поток `cacheFillerExecutor`.
3. **`HealthReport.actionsRemoteStorage.cachedFileExistsAsync("REMOTE", ...)`** → `cacheFillerExecutor.submit { ... }` на каждый cache miss.
4. **На одну песню без кеша**: 18 actions × до 1-2 SELECT = **23-25 SELECT + 4 background UPSERT** = до **29 DB-операций** + **4 новых ThreadLocal connection**.
5. **`server.tomcat.threads.max`**:
   - `karaoke-app`: **50** (явно задан в `application.yml`, фикс #087).
   - `karaoke-web`: **НЕ задан** → дефолт Spring Boot = **200** (потолок риска).
6. **`max_connections = 100`** (дефолт образа `postgres:16`, в `deploy/` не задан).
7. **`pg_stat_activity` сейчас**: `karaoke-app` 50 idle + `karaoke-web` 37 idle + 1 active = **87/100** занято, осталось 13.
8. **За 24ч — 396 ошибок**, из них **392 за 2 минуты** (`2026-09-13T10:49-10:51`) — до **41 одновременных `ol-2-thread-N`** в секунду.

## Минимальное вмешательство (рекомендация для спеки)

В порядке убывания приоритета:

### A. **`cacheFillerExecutor` → `maxPoolSize=16`** (5 минут правки)

`StorageMetadataCache.kt:65-68` — заменить на явный `ThreadPoolExecutor`:

```kotlin
private val cacheFillerExecutor = java.util.concurrent.ThreadPoolExecutor(
    0, 16, 60L, java.util.concurrent.TimeUnit.SECONDS,
    java.util.concurrent.LinkedBlockingQueue(),
).also { executor ->
    Runtime.getRuntime().addShutdownHook(Thread { executor.shutdown() })
}
```

Бонус: исправляет несоответствие KDoc с кодом.

### B. **`server.tomcat.threads.max: 50`** в `karaoke-web/src/main/resources/application.yml` (3 строки)

Симметрично `karaoke-app`, защита от будущего роста web-нагрузки.

### C. **`max_connections=200`** в `deploy/karaoke-db/` (опционально, компромисс)

Не решает корневую причину, но удваивает запас.

### D. (Long-term) HikariCP вместо ThreadLocal — отдельная спека

Радикальное решение, ломает архитектуру Pass 087.

## Что осталось неизвестным (низкий приоритет)

- Точное число одновременных `pg_stat_activity` строк в момент пика 10:49 (сейчас +18ч, видно 87, в пике было ≥100).
- `repairExecutor` (4 потока) и `KaraokeProcessWorker` (main loop) — держат ThreadLocal connections, но их вклад мал.
- `db-sync` job — нужен `grep` для полного списка (прецедент #234 уже частично делал).

## Что НЕ в сде (out of scope для этой спеки)

- HikariCP миграция — отдельная спека.
- Изменение `max_connections` без согласования с владельцем.
- Изменение архитектуры ThreadLocal per-thread connection.

## Цепочка событий (для спеки)

```
SongsTable.vue:1065-1073  watcher.currentPage → updateHealthReportForCurrentPage
SongsTable.vue:1312-1325  enqueueHrRequest → hrQueue.push(songId) для каждой песни страницы
SongsTable.vue:1330-1339  _processHrQueue → while hrRunning < HR_MAX_CONCURRENT(=3):
                              fetch POST /api/song/healthReportList (3 параллельно)
                              ↓
ApiController.kt:7674-7686  getHealthReportList(id) → HealthReport.recomputeAndBroadcast(songId)
                              ↓
HealthReport.kt:2353-2371  recomputeAndBroadcast → Song.loadFromDbById + song.healthReportList()
                              ↓
HealthReport.kt:1294-2257  getHealthReportList → цикл по 10 нет-LEGACY KaraokeFileType
                              × 1-3 locations → вызов actions(...) → actionsRemoteStorage(...)
                              ↓
HealthReport.kt:986-1004   cachedFileExistsAsync("REMOTE", ...) на каждый cache miss:
                              ↓
StorageMetadataCache.kt:439-466  cacheFillerExecutor.submit { loader(); upsert(...) }
                              ↓
                              Создаётся НОВЫЙ ol-2-thread-N (maxPoolSize=∞)
                              ↓
StorageMetadataCache.kt:340    upsert(...) → withConn → Connection.local().getConnection()
                              ↓
KaraokeConnection.kt:54-83      ThreadLocal cache miss → DriverManager.getConnection()
                              ↓
                              Postgres: 100/100 connections → FATAL: sorry, too many clients already
```

## Граф graduation (новые тикеты)

Research ответ дал специфицируемые ответы на 3 пункта из `Not yet specified`:

| Туман | Статус | Graduate в |
|---|---|---|
| Где ставить лимит | ✅ Определено: backend `cacheFillerExecutor` | → task #89 |
| Batching на стороне бэкенда | ❌ Не нужно (фикс executor решает 90% проблемы) | → out of scope |
| HikariCP | ❌ Отдельная большая спека | → out of scope |
| Тюнинг Postgres `max_connections` | ⚠️ Опционально, требует согласования | → task #90 |
| Приоритизация страниц | ⚠️ Стоит обсудить (UX) | → grilled в #87 |
| Мониторинг pg_stat_activity | ⚠️ Низкий приоритет | → out of scope (отдельная задача) |

— resolution для #85
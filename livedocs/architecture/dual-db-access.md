---
status: Active
slug: dual-db-access
type: topic
related:
  - ../domain/catalog.md
  - L3-components.md
  - data-sync.md
  - ../features/087-fix-shared-db-connection.md
  - ../features/091-fix-connection-leak.md
  - decisions/0001-raw-jdbc.md
---

# Доступ к БД: `KaraokeConnection` (local / remote / virtual)

> Drill-down для [L3-components.md](L3-components.md) и [data-sync.md](data-sync.md).

## Что показывает

Как именно код обращается к PostgreSQL — через единственный фасад
`KaraokeConnection` и статические фабрики `Connection.local()/remote()/virtual()`.
Layer абстракции над сырым JDBC (см. [ADR-0001](decisions/0001-raw-jdbc.md)).

**Когда читать**:
- Пишете новый код, который читает/пишет `tbl_settings`, `tbl_albums`, и т.п.
- Сталкиваетесь с конфликтом доступа (один поток закрывает соединение другого).
- Тюните производительность на больших таблицах (`tbl_settings` ~18k).

## Диаграмма

```mermaid
flowchart LR
    HTTP[HTTP thread pool]
    Queue[KaraokeProcessWorker<br/>doStart loop]
    OneShot[KaraokeProcessThread<br/>one-shot]

    Local[(LOCAL PG)]
    Remote[(PROD PG)]
    Virtual[VirtualRecordHash<br/>diff-only]

    KC[KaraokeConnection<br/>singleton, ThreadLocal]

    HTTP --> KC
    Queue --> KC
    OneShot --> KC

    KC -->|local()| Local
    KC -->|remote()| Remote
    KC -->|virtual()| Virtual
```

## API

```kotlin
// KaraokeConnection.kt
object KaraokeConnection {
  fun getConnection(target: Target = Target.LOCAL): Connection
  fun releaseForThisThread()  // для one-shot потоков (см. ADR-0001 + 091-fix-connection-leak)
  enum class Target { LOCAL, REMOTE, VIRTUAL }
}
```

### Target.LOCAL — read/write в LOCAL PG

```kotlin
val conn = KaraokeConnection.getConnection(KaraokeConnection.Target.LOCAL)
try {
  val songs = conn.createStatement().executeQuery(
    "SELECT * FROM tbl_settings WHERE id_status >= 3"
  )
  // ... обработка
} finally {
  conn.close()  // или releaseForThisThread() — смотрите секцию "ThreadLocal"
}
```

**Используется**:
- `karaoke-web` (admin-контейнер).
- `karaoke-app` на admin-машине (полный доступ).

### Target.REMOTE — read/write в PROD PG

```kotlin
val conn = KaraokeConnection.getConnection(KaraokeConnection.Target.REMOTE)
// ... DML на проде через SQL/триггеры
```

**Используется**:
- `karaoke-web` на проде (НЕ `karaoke-app`).
- Sync from LOCAL→REMOTE через `updateRemoteSongFromLocalDatabase`.

### Target.VIRTUAL — только вычисление recordhash без подключения

```kotlin
val conn = KaraokeConnection.getConnection(KaraokeConnection.Target.VIRTUAL)
// используется для "виртуальных" рекордов, у которых нет реального подключения
// (см. CONCEPT в `sync/SyncRegistry.kt`)
```

## ThreadLocal isolation

После `fix-shared-db-connection` (см. `087-fix-shared-db-connection.md`)
`KaraokeConnection` кеширует `java.sql.Connection` **по потоку** (через
`ThreadLocal`), а не глобально:

```kotlin
private val threadLocal = ThreadLocal<Connection>()

fun getConnection(target: Target): Connection {
  var conn = threadLocal.get()
  if (conn == null || conn.isClosed) {
    conn = createPhysicalConnection(target)
    threadLocal.set(conn)
  }
  return conn
}
```

**Почему**: PostgreSQL JDBC Connection **не рассчитан** на конкурентное
использование из разных потоков (SocketTimeout «Read timed out» + «Connection
already closed»). См. [ADR-0001](decisions/0001-raw-jdbc.md).

### Два типа потоков

| Поток | Time | Action |
|-------|------|--------|
| **Long-lived** (Tomcat HTTP pool, `KaraokeProcessWorker.doStart`) | Много заданий | ThreadLocal-кэш ОК (см. `087-fix-shared-db-connection.md`) |
| **One-shot** (`KaraokeProcessThread per task`) | Одно задание | **Должен явно** вызвать `releaseForThisThread()` в `finally` (см. `091-fix-connection-leak.md`) |

```kotlin
// ✅ Одноразовый поток — обязательно!
class KaraokeProcessThread(...) : Thread() {
  override fun run() {
    try {
      // ... обработка задания
    } finally {
      KaraokeConnection.releaseForThisThread()  // MUST!
    }
  }
}
```

## Self-healing (закрытое/invalid → пересоздать)

```kotlin
fun getConnection(target: Target): Connection {
  var conn = threadLocal.get()
  if (conn == null || conn.isClosed || !conn.isValid(2 /* seconds */)) {
    conn = createPhysicalConnection(target)
    threadLocal.set(conn)
  }
  return conn
}
```

Покрывает `docker pause karaoke-db` на 30 секунд → `conn.isValid()` →
пересоздаётся автоматически.

## Retry в `KaraokeProcessWorker.doStart()`

После `087-fix-shared-db-connection` воркер очереди имеет **retry-логику**:
5 попыток с нарастающей паузой (1s, 5s, 30s, 5m, 30m). Любой
`SQLException` на любом из методов доступа (`save`, `getCountWaiting`,
`getProcessesToStart`) → retry.

См. [livedocs/features/088-fix-queue-swallowed-errors.md](../features/088-fix-queue-swallowed-errors.md)
— единообразная обработка (раньше `getCountWaiting()` проглатывал исключение).

## Что НЕЛЬЗЯ делать

- ❌ **Делить Connection между потоками**: PostgreSQL JDBC Connection **не
  thread-safe** (см. ThreadLocal выше).
- ❌ **Забыть `releaseForThisThread()`** в одноразовом потоке → утечка
  (см. `091-fix-connection-leak.md`).
- ❌ **`conn.createStatement().executeQuery(...)` без `try-with-resources`**
  → ResultSet может утечь.
- ❌ **Хардкодить `10.0.0.1:5432`** для подключения (см. Constitution § VIII.5
  — IP-адреса через env).

## Что МОЖНО делать

- ✅ Использовать `try-with-resources` для `ResultSet`.
- ✅ Использовать JdbcTemplate из Spring (с DI).
- ✅ Использовать `KaraokeConnection.releaseForThisThread()` в
  одноразовых потоках.
- ✅ На проде использовать `Connection.REMOTE` (НЕ `LOCAL` — на проде
  нет LOCAL БД).

## Связанные LiveDocs

- [ADR-0001](decisions/0001-raw-jdbc.md) — почему raw JDBC.
- [L3-components.md](L3-components.md) — где `KaraokeConnection` живёт.
- [data-sync.md](data-sync.md) — как sync использует Connection для LOCAL↔REMOTE.
- Feature: [087-fix-shared-db-connection.md](../features/087-fix-shared-db-connection.md),
  [091-fix-connection-leak.md](../features/091-fix-connection-leak.md),
  [088-fix-queue-swallowed-errors.md](../features/088-fix-queue-swallowed-errors.md).

## Код

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt` —
  фасад.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt` —
  фабрики `local/remote/virtual`.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
# Component: run-entity-sync

> **Домен**: [processing](../domain.md)
> **Компонента**: описание `runEntitySync` и `updateDatabases` —
> главной логики двух-БД sync. Детальное продолжение
> [two-db-sync.md](two-db-sync.md).

## Ответственность | Responsibility

`runEntitySync` + `updateDatabases` — **главная логика sync** одной
entity между двумя БД (LOCAL ↔ SERVER).

`runEntitySync` — высокоуровневый entry point: разрешает
направление sync, выбирает источник/цель, делегирует в
`updateDatabases`.

`updateDatabases` — низкоуровневая логика: загружает записи из обеих
БД, вычисляет diff (create/update/delete/move), применяет операции
через HTTP на `/api/sync/changerecords`.

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`runEntitySync(key, direction, id?)`** | Синхронизировать одну entity по ключу | `Utils.kt:1055` |
| **`updateDatabases(from, to, keys, idFilter)`** | Внутренняя логика sync | `Utils.kt:1075` |
| **`SyncResult`** | Result data class с 4 списками: created, updated, deleted, moved | см. gaps |
| **`SyncDirection.LOCAL_TO_SERVER`** | Push (admin → prod) | `sync/SyncTarget.kt:43` |
| **`SyncDirection.SERVER_TO_LOCAL`** | Pull (prod → admin) | `sync/SyncTarget.kt:43` |
| **`SyncOperation`** | `INSERT` / `UPDATE` / `DELETE` / `MOVE` | `sync/SyncTarget.kt:52` |
| **`idFilter`** | Опциональный фильтр по конкретному id | `updateDatabases` |

## Логика и Алгоритмы

### `runEntitySync`

```kotlin
fun runEntitySync(
    key: String,
    direction: SyncDirection,
    id: Long? = null,
): SyncResult {
    val (fromDatabase, toDatabase) =
        if (direction == SyncDirection.LOCAL_TO_SERVER) {
            Connection.local() to Connection.remote()
        } else {
            Connection.remote() to Connection.local()
        }
    return updateDatabases(
        fromDatabase = fromDatabase,
        toDatabase = toDatabase,
        keys = setOf(key),
        idFilter = id?.let { mapOf(key to it) } ?: emptyMap(),
    )
}
```

- Разрешает `direction` → `(from, to)`.
- Если `id != null` — фильтрует по конкретной записи (для sync одной
  песни после правки).
- Делегирует в `updateDatabases`.

### `updateDatabases`

```kotlin
fun updateDatabases(
    fromDatabase: KaraokeConnection,
    toDatabase: KaraokeConnection,
    keys: Set<String>,
    idFilter: Map<String, Long> = emptyMap(),
): SyncResult {
    if (fromDatabase == toDatabase) return SyncResult(empty, empty, empty, empty)
    // ... ~450 строк ...
}
```

#### Алгоритм (высокоуровневый):

```
1. Если fromDatabase == toDatabase → no-op (return empty).
2. Установить соединения (см. ниже).
3. Для каждого key в keys:
   a. resolve target (SyncTarget по ключу из SyncRegistry.all).
   b. Для каждого направления (push / pull), если isAllowed:
      - Проверить флаги в KaraokeProperties (sync_<key>_<push|pull>_<op>_allowed).
      - Для каждой операции (INSERT/UPDATE/DELETE/MOVE):
        - Если operation == MOVE: загрузить записи с mark_deleted = true.
        - Если INSERT: загрузить записи из источника, отсутствующие в цели
          (по recordhash или по id).
        - Если UPDATE: загрузить записи с разным recordhash в источнике/цели.
        - Если DELETE: загрузить записи, которые есть в цели, но нет в источнике.
4. Применить операции через HTTP POST /api/sync/changerecords:
   - chunks по DELETE_CHUNK_SIZE = 200 (см. two-db-sync.md).
5. Возвратить SyncResult с 4 списками: created/updated/deleted/moved.
```

NB: MOVE — отдельная операция: «удалить в источнике И вставить в
цель». Списки `listToDelete` (цель) и `listToDeleteFromSource` (источник)
разделены, потому что они адресуются к разным БД. Флашится **после**
записи в цель.

## Соединения

```kotlin
val connFrom = fromDatabase.getConnection()
val connTo = toDatabase.getConnection()
```

**NB**: каждое соединение открывается на вызов sync. Закрытие — в
`finally` блоках (нужно прочитать полный код для подтверждения —
см. gaps).

Если `getConnection() == null` (БД недоступна) — `runEntitySync`
возвращает пустой `SyncResult`, ошибка **НЕ** пробрасывается. Это
by design: ручной клик «не должен крашить UI».

## Ловушки

1. **`fromDatabase == toDatabase`** → no-op. Это защита от случайного
   sync LOCAL→LOCAL.
2. **HTTP 500 на `/api/sync/changerecords`** → необработанное
   исключение. Sync целиком падает. (См. Constitution II о retry-policy.)
3. **Логирование через `println`** — НЕ через SLF4J-категории. См.
   gaps.
4. **Concurrency**: `updateDatabases` НЕ thread-safe. Параллельные
   sync ОДНОЙ entity могут создать race. Защита — `AutoOneClickSyncScheduler.running`
   AtomicBoolean (см. two-db-sync.md) + ручной клик получает 409 Conflict.

## Связь с другими компонентами

- **Two-DB sync** ([two-db-sync.md](two-db-sync.md)) — главный
  контекст, SyncRegistry, AutoOneClickSyncScheduler.
- **Crypto** — sync шифрует SQL-команды через `Crypto.encrypt` (см.
  [system/utilities](../../../system/utilities.md) — ⚠️ security
  issue с hardcoded ключом).
- **KaraokeProperties** — per-target флаги синхронизации.

## Известные TODO

- [ ] **`SyncResult`** — точное определение (data class с 4 полями).
- [ ] **`SyncTarget.resolveWhere`** — генерация WHERE clause для
  конкретной операции.
- [ ] **HTTP `/api/sync/changerecords`** — endpoint в `karaoke-web`
  для приёма операций.
- [ ] **Concurrency**: полная защита от одновременных sync.
- [ ] **Retry policy** при HTTP failure.
- [ ] **Логирование**: заменить `println` на SLF4J `infra.sync.*`.
- [ ] **Сравнение по `recordhash`** — md5 гарантирует отсутствие
  коллизий на масштабе 18k песен?

## Код (физическая реализация)

- `karaoke-app/.../Utils.kt:1055` — `runEntitySync` (~20 строк).
- `karaoke-app/.../Utils.kt:1075` — `updateDatabases` (~450 строк).
- `karaoke-app/.../sync/SyncTarget.kt` — `SyncTarget`, `SyncRegistry`.
- `karaoke-app/.../services/AutoOneClickSyncScheduler.kt` —
  автозапуск `updateDatabases` каждые 3 часа.
- `karaoke-app/.../controllers/ApiController.kt` — `postSyncOneClick`
  (ручной запуск).

## Changelog

- **Pass 343** (2026-09-09): Initial. Прецедент: задачи #65, #69.
  Автор: agent (Karaoke).
# Component: two-db-sync

> **Домен**: [processing](../domain.md) (под-домен infra)
> **Компонента**: описание механизма синхронизации LOCAL↔SERVER
> (PostgreSQL две базы).

## Ответственность | Responsibility

**Two-DB sync** — механизм, который позволяет karaoke-admin'у
(`karaoke-app`, LOCAL Postgres) и прод-серверу (`karaoke-web`,
SERVER Postgres) обмениваться данными о песнях, обложках,
подписках и т.п. Не путать с MinIO-sync (см. [storage
domain](../../storage/domain.md)).

**Архитектурное решение**: «Синхронизация в 1 клик» — пользователь
жмёт кнопку в admin, после чего все изменения из LOCAL едут на
SERVER, и наоборот, согласно per-target флагам. Также есть
автозапуск (`AutoOneClickSyncScheduler`, spec 235).

**Граница**: контекст НЕ отвечает за:

- Сами таблицы — это другие домены.
- Различие schema/migrations — handled by SQL-миграции.
- MinIO-операции — отдельный поток (см. [storage](../../storage/)).

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`SyncTarget<T>`** | Описание одной синхронизируемой таблицы. Generic по типу записи. | `sync/SyncTarget.kt:166` (Generic) и далее конкретные (`SongSyncTarget:221`) |
| **`SyncDirection`** | `LOCAL_TO_SERVER` (push) / `SERVER_TO_LOCAL` (pull) | `SyncTarget.kt:43` |
| **`SyncOperation`** | `INSERT` / `UPDATE` / `DELETE` / `MOVE` (последний — «удалить в источнике, вставить в цель») | `SyncTarget.kt:52` |
| **`SyncRegistry.all`** | Список всех зарегистрированных `SyncTarget`'ов | `SyncTarget.kt:503` |
| **`RecordDiff`** | Data class: разница двух записей по одному полю | `model/RecordDiff.kt` |
| **`RecordHash`** | Data class: `(id, recordhash)` — md5 от канонизированной строки | `model/RecordHash.kt` |
| **recordhash-триггер** | SQL-триггер, обновляющий поле `recordhash` при UPDATE | (Pass 342 — где именно) |
| **`runEntitySync`** | Top-level функция запуска синхронизации одной entity | `Utils.kt` (см. gaps) |
| **`AutoOneClickSyncScheduler`** | Периодический запуск «Синхронизации в 1 клик» раз в N часов (по умолчанию 3ч) | `services/AutoOneClickSyncScheduler.kt` |
| **`AutoOneClickSyncRun`** | Запись о результате одного тика автосинка (in-memory, ≤10 записей) | `services/AutoOneClickSyncRun.kt` |

## Интерфейсы и Контракты | Interfaces and Contracts

### Контракт `SyncTarget<T>`

Абстрактный класс `SyncTarget<T : Any>` (`sync/SyncTarget.kt:89`) — описание
одной синхронизируемой таблицы. Публичные члены:

| Метод / свойство | Контракт |
| --- | --- |
| `key`, `tableName`, `displayName` | Идентификация сущности/таблицы для `SyncRegistry`, UI и логов. |
| `oneClickDirection` | Направление, в котором сущность едет в режиме «1 клик». |
| `rowChunkSize` | Размер пачки для READ/INSERT/UPDATE (DELETE — общий `SyncRegistry.DELETE_CHUNK_SIZE`). |
| `listHashes(db, whereText): List<RecordHash>?` | Пары `(id, recordhash)` для O(n)-сравнения LOCAL↔SERVER. `null`, если `whereText` недопустим. |
| `loadByIds(ids, db): Map<Long, T>` | Пакетная загрузка `WHERE id IN (...)`. |
| `getDiff(from, to): List<RecordDiff>` | Field-level diff двух версий записи (пустой список = идентичны). |
| `getSqlToInsert(item): String` | SQL `INSERT` с `?`-плейсхолдерами для prepared-statement. |
| `deleteLocal(id, db): Boolean` | Удаление записи из локальной БД (MOVE LOCAL→SERVER). |
| `label(item): String` | Человекочитаемая метка записи. |
| `shouldPush(diff): Boolean` | Открытый метод; по умолчанию `diff.isNotEmpty()`. |

### `SyncRegistry`

- `SyncRegistry.all: List<SyncTarget<*>>` — 18 зарегистрированных сущностей (`SyncTarget.kt:503`).
- `SyncRegistry.byKey(key): SyncTarget<*>?` — поиск по ключу; `null`, если ключа нет (`SyncTarget.kt:525`).
- `SyncRegistry.DELETE_CHUNK_SIZE = 200` — общий размер пачки для DELETE (`SyncTarget.kt:501`).

### Флаги операций (extension-функции)

- `operationPropertyKey(direction, op)` → ключ `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`.
- `isOperationAllowed(direction, op): Boolean` — читает флаг из `KaraokeProperties`.
- `isAllowed(direction): Boolean` — `true`, если разрешена хотя бы одна операция этого направления.

### Точки входа

- `runEntitySync(key, direction, id?): SyncResult` — одна entity по ключу (`Utils.kt:1039`).
- `updateDatabases(fromDatabase, toDatabase, keys, idFilter): SyncResult` — низкоуровневая логика (`Utils.kt:1059`).
- `SyncRemoteClient.postChangeRecords(body): Boolean` — POST на `/changerecords`; `false` при исчерпании попыток (`services/SyncRemoteClient.kt`).

### HTTP-контракты

| Метод / URL | Контракт |
| --- | --- |
| `GET /sync/entities` | `List<SyncEntityInfoDto>` — все targets с флагами (push/pull × insert/update/delete/move). |
| `POST /sync/setflag` | Параметры `key`, `direction` (`PUSH`/`PULL`), `operation`, `value`; пишет флаг через `operationPropertyKey`; `400`, если ключ/направление/операция неизвестны. |
| `POST /sync/run` | Параметры `key`, `direction`, `id?`; `403 sync_not_allowed`, если направление запрещено; возвращает `SyncRunResultDto`. |
| `POST /sync/oneclick` | Прогон всех targets по их `oneClickDirection`; `403 vpn_active` при активном VPN; `409 sync_in_progress`, если занят `AutoOneClickSyncScheduler.running`; возвращает `List<SyncOneClickResultDto>`. |
| `POST /utils/tosync` | Параметр `id` — добавляет одну песню в sync-таблицу. |
| `POST /changerecords` (karaoke-web) | Тело: `word` (зашифрованное кодовое слово) + `dataCreate`/`dataUpdate`/`dataDelete`; возвращает `String` (`"OK"` или текст ошибки). См. [main-controller.md](../../karaoke-web/components/main-controller.md). |

DTO контрактов (`SyncEntityInfoDto`, `SyncRunResultDto`,
`SyncOneClickResultDto`) объявлены в `controllers/ApiController.kt:146/168/180`.

## Архитектура

### Зарегистрированные сущности (`SyncRegistry.all`)

18 сущностей участвуют в двух-БД sync (см. `SyncTarget.kt:503-523`):

`Song`, `Pictures`, `Author`, `Album`, `SongCoAuthor`, `Dictionary`,
`News`, `SiteUser`, `SitePlaylist`, `SitePlaylistItem`,
`ListeningHistory`, `SongAssignment`, `SongAssignmentDraft`,
`SongShareLink`, `WebEvent`, `PriceTariff`, `Subscription`,
`SiteChatMessage`.

**Не в sync** (важно!): `KaraokeProcess`, `StemJob`, `CartItem`,
`SearchAsync`, `SearchResult`, `PromoRule`, `CrossSong` — только
LOCAL. Это значит, что **repair-процессы не видны прод-серверу**.

### Per-target флаги

144 флага в `KaraokeProperties.kt` (уникальных `sync_*_allowed`; сверено в Pass 473) (`sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`),
например:

- `sync_songs_push_insert_allowed`
- `sync_songs_pull_update_allowed`
- `sync_subscriptions_push_insert_allowed`
- ...

Это позволяет **тонкую настройку**: например, push разрешён для
новых песен, но не для удаления (delete только через ручной процесс).

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм sync

Один `runEntitySync(target, direction)` для каждого target'а:

```
1. Открыть LOCAL и SERVER соединения.
2. Для целевого направления:
   - INSERT: получить записи из источника, которых нет в цели (по recordhash).
   - UPDATE: получить записи с разным recordhash в источнике и цели.
   - DELETE: получить записи, которые есть в цели, но нет в источнике.
   - MOVE: получить записи, помеченные как удалённые в источнике.
3. Для каждой операции: HTTP POST на /changerecords (server-side endpoint).
   Pass 431 (#155): через `SyncRemoteClient` — таймауты 10s/60s, 1 retry (2s) на
   транзиентные сетевые сбои, ошибка не пропагируется (см. run-entity-sync.md).
4. Chunks по DELETE_CHUNK_SIZE = 200 для DELETE.
5. Логировать результат: created/updated/deleted/moved counts.
```

NB: `recordhash` — md5 от канонизированной строки таблицы. **Без
него** diff работал бы через покабельное сравнение, что долго.

**Порядок и батчи (проверено в `updateDatabases`, `Utils.kt:1059`)**:

- Для каждого target с ключом из `keys` вызывается `collectSyncOps(...)`;
  при `id`-фильтре `whereText = "WHERE id = <id>"`, иначе `""`.
- Запись в SERVER-цель идёт батчами в порядке **INSERT → DELETE → UPDATE**.
- Размер пачки INSERT/UPDATE — `min(rowChunkSize)` по участвующим targets
  (fallback `100`), DELETE — `SyncRegistry.DELETE_CHUNK_SIZE = 200`.
- Удаление перемещённых строк из ИСТОЧНИКА (MOVE) — **после** записи в цель:
  SERVER-источник — зашифрованным HTTP-DELETE, LOCAL-источник — прямым
  JDBC-DELETE. Так источник чистится только по подтверждённой цели.
- Если `collectSyncOps` вернул `false` — sync целиком возвращает пустой
  `SyncResult`.

### `AutoOneClickSyncScheduler`

Автоматический запуск `runEntitySync` для всех targets каждые N часов
(по умолчанию 3). Несколько архитектурных решений:

- **`@Scheduled(fixedDelay = 60_000L)` + ручная проверка `now -
  lastRunMs >= intervalMs`**: Spring `@Scheduled` не позволяет
  динамический `intervalMs` через SpEL (`KaraokeProperties` не в
  Spring Environment). Поэтому — manual check внутри минутного
  тика.
- **Singleton bean с `AtomicBoolean running`**: общий lock с
  `ApiController.postSyncOneClick`. Ручной клик во время автосинка
  → HTTP 409 Conflict.
- **Двухуровневая защита от exceptions**:
  - Внутри per-target — `try/catch(Throwable)`, один упавший target
    не ломает остальные.
  - Снаружи всего тика — `try/catch(Throwable)`, scheduler-бин не
    останавливается. Следующий тик через `fixedDelay` пытается
    снова.
- **In-memory history**: `ConcurrentLinkedDeque<AutoOneClickSyncRun>`
  длиной ≤10. **НЕ персистится в БД** (by design, spec 235 A-007).

### Логирование

- `[AutoOneClickSyncScheduler] disabled by config (autoOneClickSyncEnabled=false)`.
- `[AutoOneClickSyncScheduler] tick=<ISO> RUNNING/SUCCESS/FAILED totals=…`.
- `[AutoOneClickSyncScheduler] target=<key> failed: <message>`.

## Domain Invariants

1. **Все таблицы в `SyncRegistry.all` MUST иметь `recordhash`-триггер**
   в БД (Constitution Principle III). Без триггера md5 не обновится
   → sync не увидит diff.
2. **`SyncTarget.key` MUST быть уникальным** в `SyncRegistry.all`.
   Дубли → silent overwrite в `SyncRegistry.byKey`.
3. **Per-target флаги MUST быть в `KaraokeProperties.kt`** —
   автоматический поиск невозможен.
4. **`runEntitySync` MUST быть идемпотентным**: повторный запуск
   не должен создавать дубли (через recordhash это гарантируется).
5. **При добавлении колонок** в таблицу, участвующую в sync —
   ОБЯЗАТЕЛЬНО пересоздать `recordhash`-триггер (см. Constitution
   III).

## Hot paths

- **Sync запускается часто**: каждые 3 часа + ручные клики + при
  approve публикации. На больших таблицах (`Song` — 18k записей,
  `WebEvent` — миллионы) sync может занимать минуты.
- **SQL chunk sizes**:
  - SELECT chunk = 25 (под `socketTimeout=30` на тяжёлых строках
    Song, см. parent 241 A.4).
  - DELETE chunk = 200 (лёгкие, можно крупные пачки).
- **HTTP round-trips**: один на чанк записей через `/changerecords`.

## Зависимости | Dependencies

- **Async Process Queue** ([async-process-queue](async-process-queue.md)):
  `KaraokeProcess` НЕ в sync — repair-процессы только LOCAL.
- **Two-DB sync + Monitoring**: `AutoOneClickSyncStatusController`
  отдаёт последние 10 тиков для UI.
- **Web side**: `/changerecords` endpoint принимает sync-операции.

## Известные TODO

- [ ] **recordhash-триггеры** — где определены, как пересоздаются
      при миграциях.
- [x] **`runEntitySync`** в `Utils.kt` — описан в [run-entity-sync.md](run-entity-sync.md).
- [ ] **`/changerecords`** на web-стороне — endpoint для
      приёма операций.
- [ ] **Why NOT cluster lock**: karaoke-app — desktop, однопроцессный.
      Что если два админа одновременно запустят sync?
- [ ] **`AutoOneClickSyncStatusController`** — где, какие поля
      возвращает.
- [ ] **Deduplication**: что если одна и та же запись INSERT'ится
      дважды (например, повторный sync после сбоя сети на полпути)?
- [ ] **Сравнение по `recordhash`**: гарантирует ли md5 отсутствие
      коллизий на нашем масштабе?

## Код (физическая реализация)

- `karaoke-app/.../sync/SyncTarget.kt` (542 строки — большой файл)
- `karaoke-app/.../model/RecordDiff.kt`
- `karaoke-app/.../model/RecordHash.kt`
- `karaoke-app/.../services/AutoOneClickSyncScheduler.kt`
- `karaoke-app/.../services/AutoOneClickSyncRun.kt`
- `karaoke-app/.../controllers/AutoOneClickSyncStatusController.kt`
- `karaoke-app/.../controllers/ApiController.kt` (`postSyncOneClick`)
- `karaoke-app/.../controllers/dto/AutoOneClickSyncDtos.kt`
- `karaoke-web/.../controllers/...` (`/changerecords` endpoint)

## Связанные ADR

- `knowledge/adr/0001-raw-jdbc.md` — общий принцип JDBC.
- Constitution Principle III — обязательный recordhash-триггер.
- `archive/docs/features/dual-db-sync.md` — оригинальный документ
  (НЕ Knowledge). Требует миграции.
- `archive/docs/features/dual-db-sync.md` — документ автосинка (каталога `livedocs/` в репозитории нет; если
  существует).

## Changelog

- **Pass 485** (2026-09-27, spec `485-knowledge-domain-processing`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 341 P1** (2026-09-09): Initial. Автор: agent (Karaoke).
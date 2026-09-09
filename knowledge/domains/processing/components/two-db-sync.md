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

40 флагов в `KaraokeProperties.kt` (`sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`),
например:

- `sync_songs_push_insert_allowed`
- `sync_songs_pull_update_allowed`
- `sync_subscriptions_push_insert_allowed`
- ...

Это позволяет **тонкую настройку**: например, push разрешён для
новых песен, но не для удаления (delete только через ручной процесс).

### Алгоритм sync

Один `runEntitySync(target, direction)` для каждого target'а:

```
1. Открыть LOCAL и SERVER соединения.
2. Для целевого направления:
   - INSERT: получить записи из источника, которых нет в цели (по recordhash).
   - UPDATE: получить записи с разным recordhash в источнике и цели.
   - DELETE: получить записи, которые есть в цели, но нет в источнике.
   - MOVE: получить записи, помеченные как удалённые в источнике.
3. Для каждой операции: HTTP POST на /api/sync/changerecords (server-side endpoint).
4. Chunks по DELETE_CHUNK_SIZE = 200 для DELETE.
5. Логировать результат: created/updated/deleted/moved counts.
```

NB: `recordhash` — md5 от канонизированной строки таблицы. **Без
него** diff работал бы через покабельное сравнение, что долго.

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
- **HTTP round-trips**: один на чанк записей через `/api/sync/changerecords`.

## Связь с другими компонентами

- **Async Process Queue** ([async-process-queue](async-process-queue.md)):
  `KaraokeProcess` НЕ в sync — repair-процессы только LOCAL.
- **Two-DB sync + Monitoring**: `AutoOneClickSyncStatusController`
  отдаёт последние 10 тиков для UI.
- **Web side**: `/api/sync/changerecords` endpoint принимает sync-операции.

## Известные TODO

- [ ] **recordhash-триггеры** — где определены, как пересоздаются
      при миграциях.
- [ ] **`runEntitySync`** в `Utils.kt` — точная реализация, какие
      edge-cases.
- [ ] **`/api/sync/changerecords`** на web-стороне — endpoint для
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
- `karaoke-web/.../controllers/...` (`/api/sync/changerecords` endpoint)

## Связанные ADR

- `knowledge/adr/0001-raw-jdbc.md` — общий принцип JDBC.
- Constitution Principle III — обязательный recordhash-триггер.
- `archive/docs/features/dual-db-sync.md` — оригинальный документ
  (НЕ Knowledge). Требует миграции.
- `livedocs/features/235-auto-sync-3h.md` — спека автосинка (если
  существует).

## Changelog

- **Pass 341 P1** (2026-09-09): Initial. Автор: agent (Karaoke).
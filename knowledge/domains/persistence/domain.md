---
id: domain-persistence
title: "Domain: Persistence (KaraokeDbTable + DB I/O)"
status: Active
slug: persistence
related:
  - ../processing/domain.md
  - ../health/domain.md
  - ../../adr/0001-raw-jdbc.md
---

# Domain: Persistence (KaraokeDbTable + DB I/O)

> Bounded context для абстракции БД-персистентности. Все 24 DB-сущности (сверено в Pass 473: 24 реализации `getTableName`)
> karaoke-app реализуют интерфейс `KaraokeDbTable`. Прецедент
> создания — P2 Knowledge-аудита (Pass 341).

## Обзор контекста (Bounded Context)

**KaraokeDbTable** — единая абстракция для всех персистентных
сущностей Karaoke. Это не ORM (нет JPA/Hibernate), а **лёгкая
reflection-based обёртка**:

1. **Reflection-based mapping** — `@KaraokeDbTableField` аннотации
   на полях → маппинг на колонки БД.
2. **Diff-based save** — `save()` загружает текущее состояние из БД,
   сравнивает с in-memory, генерирует UPDATE только для изменённых
   колонок.
3. **SSE-уведомления** — после `save()` рассылается `recordChange`
   всем UI-вкладкам.
4. **Sync-готовность** — наличие колонки `recordhash` (md5) позволяет
   участвовать в two-DB sync.

**Граница**: контекст НЕ отвечает за:

- Сами таблицы и поля (это другие домены).
- Two-DB sync как процесс (→ [processing/two-db-sync](../processing/components/two-db-sync.md)).
- SQL-миграции (отдельный процесс).

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | | **`KaraokeDbTable`** | Интерфейс, который реализуют все DB-сущности | `model/KaraokeDbTable.kt:62` |
| **`@KaraokeDbTableField`** | Аннотация: имя колонки, `isId`, и т.п. | `model/KaraokeDbTableField.kt` |
| **`getTableName()`** | Имя таблицы (`"tbl_songs"`, `"tbl_authors"`, ...) | `KaraokeDbTable.kt:72` |
| **`save()`** | Сохранить in-memory → БД (INSERT или UPDATE с diff) | `KaraokeDbTable.kt:95` |
| **`toDTO()`** | Сериализация для API/SSE | `KaraokeDbTable.kt:203` |
| **`loadList(args, tableName, database)`** | Загрузить список записей с фильтрами | `KaraokeDbTable.kt:441` |
| **`loadById`** | Загрузить одну запись по id | `KaraokeDbTable.kt:579` |
| **`loadByIds`** | Загрузить несколько записей (пакетно) | `KaraokeDbTable.kt:600` |
| **`createDbInstance`** | INSERT + RETURNING id | `KaraokeDbTable.kt:622` |
| **`getDiff(new, old)`** | Field-level diff между двумя сущностями | `KaraokeDbTable.kt:762` |
| **`getListHashes`** | Получить `(id, recordhash)` для sync | `KaraokeDbTable.kt:688` |
| **`delete(id, database)`** | DELETE по id | `KaraokeDbTable.kt:788` |
| **`invalidateSchemaCache`** | Сбросить reflection-cache (для тестов) | `KaraokeDbTable.kt:425` |

## Архитектура

### Reflection-based mapping

Каждая сущность (например, `Song`) объявляет поля с аннотацией:

```kotlin
class Song(...) : KaraokeDbTable {
    override var id: Long = 0
    @KaraokeDbTableField(name = "song_file_name") var fileName: String = ""
    @KaraokeDbTableField(name = "song_year") var year: Int = 0
    // ...
}
```

При загрузке `loadList` использует reflection для:

1. Получения всех `@KaraokeDbTableField` полей.
2. Генерации SQL `SELECT <columns> FROM <table> WHERE <args>`.
3. Маппинга `ResultSet` → поля Kotlin.

**Кеш схемы**: `invalidateSchemaCache` — сбрасывает кеш reflection.
Используется в тестах.

### `save()` — diff-based UPDATE

Алгоритм:

```
1. Если id == 0L → INSERT (createDbInstance).
2. Иначе:
   - loadById текущего состояния из БД.
   - getDiff(this, savedEntity) — field-level diff.
   - Если diff пустой → ничего не делать.
   - Генерируется UPDATE только для изменённых колонок.
   - После успешного UPDATE → SSE recordChange.
```

**NB**: `save()` **не пробрасывает** UNIQUE-конфликты и другие
SQLException — ошибка логируется, но вызов продолжается. Для сущностей
с UNIQUE-индексом проверяйте конфликт ДО `save()` в контроллере.

(Уточнение Pass 476: слово «молча» было неточным — ошибка печаталась
в `println`; теперь идёт через SLF4J (`log.warn`), как требует ADR
local-0005. Поведение — не пробрасывать — не менялось.)

**[WARN] Конфликт с ADR**: правило 1 и правило 5
[`local-0002-save-exception-handling.md`](../../adr/local-0002-save-exception-handling.md)
требуют «пробрасывать исключения наверх» и прямо запрещают silent
fallback'ы. Текущее поведение `save()` этому противоречит. Расхождение
зафиксировано, но НЕ устранено: выбор между правкой кода и уточнением
области действия ADR — решение владельца (ADR append-only).

### `getDiff`

Сравнивает поле-за-полем между двумя экземплярами одной сущности.
Возвращает `List<RecordDiff>` — каждое изменение как
`(name, oldValue, newValue)`.

### `loadList(args, ...)`

Аргументы — Map<String, String> для фильтрации (`song_id=123`,
`is_active=true`). Генерирует `WHERE` clause динамически.

### Соединение на поток (ThreadLocal) и его жизненный цикл

`KaraokeConnection` кэширует **физическое** `java.sql.Connection` в `ThreadLocal`
потока (см. `specs/087-fix-shared-db-connection`); фабрики `Connection.local()`/
`remote()` — singleton'ы (`specs/234-db-sync-connection-leak`). Итог: **один
устойчивый канал к БД на поток выполнения**, переиспользуемый между запросами.

Отсюда — два разных (и взаимоисключающих) требования к закрытию:

| Тип потока | Примеры | `closeThreadConnection()` | Почему |
|---|---|---|---|
| **Одноразовый** | фоновые задачи API-контроллеров, поток очереди `KaraokeProcessWorker`, воркер `SyncAudioDescendants`, `KaraokeProcessThread` | **ОБЯЗАТЕЛЬНО** в `finally` | поток умирает и больше не позовёт `getConnection()` — соединение нечем переиспользовать, канал остаётся открытым до финализации сборщиком мусора |
| **Переиспользуемый** | пул потоков Tomcat (HTTP-запросы), `@Scheduled`, Spring-пулы | **ЗАПРЕЩЕНО** | `close()` разрушит thread-local кеш: следующий запрос откроет НОВОЕ физическое соединение (TCP + auth handshake + `isValid(3)`) — churn и риск исчерпания `max_connections` |

**Канонический способ для одноразовых потоков** — хелпер
`startOneShotThread(database, name) { … }`
(`karaoke-app/.../services/OneShotThread.kt`): он сам оборачивает тело в
`try/finally` и закрывает соединение потока при любом исходе (успех/исключение).
Сырой `kotlin.concurrent.thread` в прикладном коде запрещён.

**Что освобождается на месте**: `PreparedStatement`/`ResultSet` — через `use { }`.
Физическое соединение — по правилам таблицы выше.

Инвариант проверяется тестами:
`karaoke-app/src/test/.../services/OneShotThreadTest.kt` (гарантия закрытия),
`.../services/OneShotThreadWiringTest.kt` (в аудируемых файлах нет сырых
одноразовых потоков), `karaoke-app/src/test/.../controllers/ChatControllerThreadLocalTest.kt`
и `karaoke-web/src/test/.../services/EventsBufferThreadLocalTest.kt`
(переиспользуемый поток НЕ закрывает соединение).

Полный разбор решения — [local-ADR-0009](../../adr/local-0009-thread-local-connection-lifecycle.md).

### `recordhash`-колонка

Каждая таблица в `SyncRegistry.all` MUST иметь:

- Колонку `recordhash VARCHAR(32)` (md5 от канонизированной строки).
- SQL-триггер `tg_<table>_recordhash`, поддерживающий колонку на
  INSERT/UPDATE/DELETE.

Без триггера sync не увидит diff.

### SSE после save

`save()` рассылает `SseNotification.recordChange(...)` через
`SseNotificationService.send(...)`. UI (`webvue3`) обновляет
представление без polling.

## Архитектурные решения

### Решение 1: Не используем JPA/Hibernate

Сырой JDBC + reflection. См. Constitution Principle II.

Рациональ: 18k+ записей на проде, O(n²) сравнения занимали 3+ минуты,
O(n) — секунды.

### Решение 2: Diff-based UPDATE

Генерируем UPDATE только для изменённых колонок → меньше шанс на
race condition в multi-user среде, меньше нагрузки на БД.

### Решение 3: Recordhash для sync

md5 от канонизированной строки. Позволяет O(n) diff через
`associateBy { it.id }`.

### Решение 4: SSE после save

UI обновляется без polling → экономия трафика и времени.

## Публичные контракты (API)

### HTTP-API

У домена **нет HTTP-эндпоинтов**: `KaraokeDbTable` — библиотечный слой
персистентности, к которому обращаются контроллеры и сервисы других доменов.
Единственный «наружу»-эффект — SSE-события после записи (см. ниже).

### Internal API

- `KaraokeDbTable` (реализуют все DB-сущности):
  - `getTableName(): String`, `toDTO(): KaraokeDbTableDto`,
    `getSqlToInsert(): String`;
  - `save()` — INSERT при `id == 0L` (через `createDbInstance`), иначе
    diff-UPDATE только изменённых колонок; после UPDATE шлёт SSE `recordChange`;
  - companion-загрузка: `loadList(clazz, tableName, whereList, limit, offset,
    database, storageService, storageApiClient, sync, ignoreUseInList)` →
    `List<KaraokeDbTable>`, `loadById(...)` → `KaraokeDbTable?`, `loadByIds(...)` →
    `List<KaraokeDbTable>`;
  - `createDbInstance(entity, database): KaraokeDbTable?` — INSERT + id/self-heal
    сиквенса; `delete(tableName, id, database, sync): Boolean`,
    `deleteIn(tableName, ids, database, sync): Int`;
  - `getDiff(entityA, entityB): List<RecordDiff>` — порядок колонок по
    `KaraokeDbTableField.name` (Pass 451);
  - `getListHashes(tableName, database, whereText): List<RecordHash>?` — пары
    `(id, recordhash)` для two-DB sync;
  - `invalidateSchemaCache(tableName?, database?)` — сброс reflection-кеша.
- `@KaraokeDbTableField(name, isId, useInDiff, useInHash, useInList)` — контракт
  аннотации: имя колонки и участие поля в diff/hash/`loadList`.
- `RecordDiff(recordDiffName, recordDiffValueNew, recordDiffValueOld,
  recordDiffRealField)` — единица изменения; `RecordHash(id, recordhash)` —
  единица sync-diff.
- `KaraokeConnection.getConnection(): java.sql.Connection?` — соединение на поток
  (ThreadLocal) с self-healing; `closeThreadConnection()` — закрывает соединение
  ТОЛЬКО текущего потока. Контракт вызова: одноразовый поток — обязателен,
  переиспользуемый — запрещён (см. «Соединение на поток» выше и
  [local-ADR-0009](../../adr/local-0009-thread-local-connection-lifecycle.md)).
  Оба метода — `open` (шов для юнит-тестов).
- `startOneShotThread(database, name) { … }`
  (`karaoke-app/.../services/OneShotThread.kt`) — запуск одноразового фонового
  потока с гарантированным закрытием его JDBC-соединения в `finally`.
- `Connection.local()` / `Connection.remote()` / `Connection.virtual()` —
  singleton-фабрики (`name = LOCAL | SERVER | VIRTUAL`); URL/credentials зависят
  от `APP_WORK_*`.

### Внешний артефакт

- SSE `recordChange` / `recordAdd` / `recordDelete`
  (см. [sse domain](../sse/domain.md)) — публичный побочный эффект записи.

## Структура компонентов (C4 L3)

L3-компонентов у домена пока нет: контекст описан целиком в этом
`domain.md` (см. [домены](../README.md)). Создание компонентных документов —
отдельная задача; сам факт отсутствия L3 зафиксирован здесь, чтобы ссылка
«структура компонентов» не выглядела потерянной.

## Domain Invariants

1. **`save()` MUST быть идемпотентным** в части «diff is empty → no-op»
   (повторный save без изменений не генерирует UPDATE).
2. **`loadList` MUST использовать `WHERE id IN (..)`** для batch-операций,
   не по одной записи в цикле (Constitution II).
3. **`recordhash`-триггер MUST быть на каждой sync-таблице**.
4. **Соединение одноразового потока MUST закрываться детерминированно**
   (`startOneShotThread` → `closeThreadConnection()` в `finally`), а соединение
   переиспользуемого потока MUST оставаться в thread-local кеше (не закрывать).
5. **Nullable-колонки БД → nullable Kotlin-поля**. Иначе `loadList` бросает NPE на SQL NULL (см. ловушки в KDoc).

## Hot paths

- **`Song.loadList(...)`** — на странице «Песни» webvue3
  загружается с фильтрами. SELECT может быть тяжёлым (18k+ записей).
- **`Song.save()`** — на любой правке метаданных песни → UPDATE +
  SSE.
- **`getListHashes`** — на старте sync. Полный список `(id, recordhash)`.
  SELECT быстрый (только 2 колонки).

## Ловушки (из KDoc и DEVELOPER-опыта)

1. **`loadList` + SQL NULL**: nullable-колонка в БД → non-null поле
   в Kotlin → NPE при чтении. **Решение**: nullable-поля.
2. **`save()` проглатывает UNIQUE-конфликты**: проверяйте конфликт
   ДО save в контроллере (см. CONSTITUTION_PATTERN).
3. **`close()` в переиспользуемом потоке разрушает thread-local кеш**: код вида
   `finally { db.getConnection()?.close() }` в HTTP-контроллере не «возвращает
   соединение в пул» (пула нет) — он заставляет следующий запрос на том же потоке
   открыть новое физическое соединение. Прецедент — BR-006/BR-027/BR-116
   (аудит #336).
4. **Отсутствие `closeThreadConnection()` в одноразовом потоке** — утечка
   физического канала к PostgreSQL до финализации сборщиком мусора; при
   `max_connections` это отказ БД для всего приложения. Прецедент — BR-038/BR-060
   (аудит #336).
5. **`getDiff` field order** — **FIXED in Pass 451** [OK]:
   `KaraokeDbTable.getDiff` теперь сортирует по
   `KaraokeDbTableField.name` для детерминированного UPDATE column
   order. Без сортировки порядок зависел от reflection
   (`kClassEntityA.members`) — JVM НЕ гарантирует порядок, что
   приводило к нестабильному `recordhash` и race в two-DB sync.

## Зависимости | Dependencies

- **Two-DB sync** ([processing/two-db-sync](../processing/components/two-db-sync.md)):
  использует `getListHashes` для быстрого diff.
- **Async Process Queue** ([processing/async-process-queue](../processing/components/async-process-queue.md)):
  `KaraokeProcess.save()` обновляет статус.
- **SSE** ([sse domain](../sse/domain.md)):
  `recordChange` события после save.
- **HealthReport** ([health domain](../health/domain.md)):
  `Song.save()` для смены `idStatus`.

## Известные TODO

- [ ] **Какие сущности НЕ реализуют `KaraokeDbTable`**: проверка
      списка (предположительно все 30, см. P0 отчёт).
- [ ] **Конкретные сущности с UNIQUE-индексом**: какие из них есть,
      какой паттерн проверки конфликта.
- [ ] **`KaraokeDbTableField`** — где определён, все ли поля аннотированы.
- [ ] **Schema cache invalidation** — как тесты работают с этим.
- [ ] **Логирование при ошибках save**: куда, в каком формате.
- [ ] **Connection leak** — частично закрыт аудитом #336 (BR-006/027/038/060/116,
      контракт зафиксирован в [local-ADR-0009](../../adr/local-0009-thread-local-connection-lifecycle.md)).
      Осталось family-семейство `withDb` в 16 контроллерах `karaoke-app`
      (`TariffsController`, `SiteUsersController`, `SponsrSyncController`,
      `StatsController`, `StemJobsAdminController`, `SubscriptionsController`,
      `SitePlaylistsController`, `SongEditorController`, `NewsController`,
      `NewsTemplateController`, `PublicSettingsController`, `DictionariesController`,
      `PromoController`, `ListeningHistoryController`, `ShareLinksAdminController`)
      и `StorageMetadataCache.refreshAll` — отдельная задача.

## Код (физическая реализация)

- `karaoke-app/.../model/KaraokeDbTable.kt` (~840 строк — большой
  файл)
- `karaoke-app/.../model/KaraokeDbTableField.kt`
- `karaoke-app/.../model/KaraokeDbTableDto.kt`
- `karaoke-app/.../Connection.kt` (local/remote/virtual factory)
- `karaoke-app/.../KaraokeConnection.kt` (обёртка над JDBC, thread-local кеш)
- `karaoke-app/.../services/OneShotThread.kt` (`startOneShotThread` — одноразовый
  поток с гарантированным закрытием соединения)
- `karaoke-app/.../KaraokeProperties.kt` (через getString/getLong/...)

## Changelog

- **Pass 341 P2** (2026-09-09): Initial. Автор: agent (Karaoke).
- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): добавлена секция «Публичные контракты (API)». Автор: agent (Karaoke).
- **OpenProject #368** (2026-10-24, аудит #336, BR-006/027/038/060/116): добавлен
  раздел «Соединение на поток (ThreadLocal) и его жизненный цикл», инвариант
  жизненного цикла соединения, ловушки (закрытие в переиспользуемом потоке /
  отсутствие закрытия в одноразовом) и контракт `startOneShotThread`. Решение
  зафиксировано в [local-ADR-0009](../../adr/local-0009-thread-local-connection-lifecycle.md).
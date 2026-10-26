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

### Параметризованный `WHERE` (`SqlWhere`)

Значения фильтров **никогда** не склеиваются в SQL: они уходят в
`PreparedStatement` через `?`, а текст запроса содержит только имена
колонок-константы. Это правило закрывает SQL-инъекции админских фильтров
(BR-028, BR-100, BR-175, BR-182, BR-237; аудит #336, OpenProject #383).

| Элемент | Роль |
|---|---|
| `model/SqlWhere.kt` → `SqlWhere` | фрагменты SQL с `?` + список значений в том же порядке; `bind(ps)` привязывает их |
| `model/SqlWhere.kt` → `SqlWhereBuilder` | парное добавление «фрагмент + значения» (`add`), константа (`raw`), отказ (`none` → `1=0`) |
| `KaraokeDbTable.loadList(where = SqlWhere, ...)` | при непустом `params` исполняет через `PreparedStatement`; старый overload `whereList: List<String>` остался для непереведённых вызывающих |

Инварианты:

- **MUST**: значения из `@RequestParam` не попадают в текст SQL ни при каких
  условиях — только в `SqlWhere.params` (имена таблиц/колонок — константы кода).
- **MUST**: динамический набор колонок — только белый список (`AlbumType.entries`
  для `album_type`, список `flag_*`/`filter_*` в `Song.getWhereList`).
- **MUST**: числовые фильтры разбираются через `toLongOrNull()`; невалидное
  значение даёт фрагмент `1=0` (пустая выборка), а не «пропустить условие» —
  иначе фильтр расширил бы выборку.
- **SHOULD**: новый фильтр добавляется в `getWhereList` парой
  `where.add("<колонка> = ?", value)`, а не строковой интерполяцией.

Исполнение: `Song.loadListFromDb`/`Song.countMatchingAttr`, `KaraokeProcess.loadList`
и `KaraokeDbTable.loadList` выбирают `PreparedStatement`, если `params` непуст.
Регрессия — `karaoke-app/src/test/.../model/SqlInjection*Test.kt`; per-feature
документ — [sql-injection-hardening](../../../docs/features/sql-injection-hardening.md).

### Соединение на поток (ThreadLocal) и его жизненный цикл

`KaraokeConnection` кэширует **физическое** `java.sql.Connection` в `ThreadLocal`
потока (см. `specs/087-fix-shared-db-connection`); фабрики `Connection.local()`/
`remote()` — singleton'ы (`specs/234-db-sync-connection-leak`). Итог: **один
устойчивый канал к БД на поток выполнения**, переиспользуемый между запросами.

Отсюда — три разных (и взаимоисключающих) требования к закрытию:

| Тип потока | Примеры | `closeThreadConnection()` | Почему |
|---|---|---|---|
| **Одноразовый** | фоновые задачи API-контроллеров, поток очереди `KaraokeProcessWorker`, воркер `SyncAudioDescendants`, `KaraokeProcessThread` | **ОБЯЗАТЕЛЬНО** в `finally` | поток умирает и больше не позовёт `getConnection()` — соединение нечем переиспользовать, канал остаётся открытым до финализации сборщиком мусора |
| **Переиспользуемый** | пул потоков Tomcat (HTTP-запросы), `@Scheduled`, Spring-пулы | **ЗАПРЕЩЕНО** | `close()` разрушит thread-local кеш: следующий запрос откроет НОВОЕ физическое соединение (TCP + auth handshake + `isValid(3)`) — churn и риск исчерпания `max_connections` |
| **Завершаемый пулом** | потоки `kotlinx.coroutines` `Dispatchers.IO` / `Dispatchers.Default` (пул держит поток около минуты, затем уничтожает его), потоки Tomcat, вытесненные из пула | **не определено** | поток не знает, что он последний: закрыть соединение изнутри нельзя (это ещё не конец жизни потока), а закрыть «после» — некому. Физический канал остаётся в БД навсегда |

**Запас по числу соединений.** PostgreSQL поднимается с
`max_connections=300` (`deploy/docker-compose-database.yml` и
`deploy/prod-single-host/docker-compose-database.yml`; переопределяется
переменной `DB_MAX_CONNECTIONS`). При исчерпании лимита БД отвечает
`FATAL: sorry, too many clients already`, и приложение **не падает, а
деградирует**: очередь `KaraokeProcess` встаёт (`GET /process/isworking` →
`false`), планировщики перестают читать БД, запросы сайта отдают 5xx.
Проверка состояния: `SELECT count(*) FROM pg_stat_activity;` — сравнить с
`max_connections`.

Увеличенный лимит — **страховка**, а не решение: он отодвигает отказ, но не
устраняет утечку каналов завершаемых пулом потоков (третья строка таблицы).
Окончательное решение (ограниченный пул соединений вместо ThreadLocal-кеша
либо детерминированное закрытие) — **OpenProject #410**: оно меняет контракт
жизненного цикла соединения целиком и требует отдельной спеки с замерами.

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

Инварианты формулы (аудит #336, BR-540/541/553/533, OpenProject #363):

- **MUST**: в md5 входит каждая колонка, объявленная в модели через
  `@KaraokeDbTableField`, кроме помеченных `useInHash = false` (пример —
  `WebEvent.lastUpdate`: его авто-обновляет триггер в каждой БД). Пропущенная колонка =
  тихая потеря синхронизации: diff сравнивает строки только по `recordhash`, поэтому
  изменение поля не считается расхождением и на другую БД не уезжает.
- **MUST**: в теле триггерной функции нет «голых» имён колонок — только `NEW.<колонка>`.
  В PL/pgSQL неизвестный идентификатор уходит в SQL-парсер как ссылка на колонку, а у
  триггерной функции нет FROM → `ERROR: column "…" does not exist` на первой же записи;
  `CREATE FUNCTION` этого не ловит.
- **MUST**: имена колонок в md5 совпадают с текущей схемой (после переименования
  `tbl_processes.settings_id → song_id` канонический файл ещё год ссылался на старое имя —
  BR-540).
- **MUST**: бэкфилл в конце канонического файла повторяет формулу функции один-в-один,
  иначе существующие строки остаются со старым хэшем до первого UPDATE.
- **MUST**: для таблиц sync-движка канонический `deploy/recordhash_<table>.sql` —
  единственное определение функции; миграция, добавляющая колонку, обновляет и его.
- **MUST**: миграция, которая переопределяет функцию сама (файлы применяются по одному),
  повторяет ПОЛНЫЙ набор и порядок колонок канонического файла и сама добавляет
  `ADD COLUMN IF NOT EXISTS` для всех упомянутых колонок. Иначе повторный прогон старой
  миграции откатывает формулу — прецедент BR-044 (`tbl_site_users`: семь версий функции,
  последняя без `personal_discount_percent`).
- **MUST**: `tbl_songs_sync` содержит все колонки, которые пишет
  `Song.getSqlToInsert(sync = true)`; иначе `INSERT` падает на первой песне батча.

Проверяется статически, без БД: `python3 tools/check-recordhash-consistency.py`
(CI + pre-commit). Регламент миграций —
[sql-migrations.md](../../system/infra/sql-migrations.md), Решение 3.

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

1. **`save()` MUST быть идемпотентным**
   - **Правка BR-168 (аудит #336, `PKG-S3-23`)**: изменение и его СЛЕД
     (audit-запись в `tbl_processes_audit`) выполняются в **одной транзакции**
     (`autoCommit=false` → `commit`/`rollback`). Иначе падение audit-INSERT'а
     давало 500 при уже применённой правке, и история правки терялась
     безвозвратно. Тот же контракт — для bulk-операций.
 в части «diff is empty → no-op»
   (повторный save без изменений не генерирует UPDATE).
2. **`loadList` MUST использовать `WHERE id IN (..)`** для batch-операций,
   не по одной записи в цикле (Constitution II).
3. **`recordhash`-триггер MUST быть на каждой sync-таблице**.
4. **`recordhash`-функция MUST покрывать ВСЕ значимые колонки таблицы в
   фиксированном порядке.** `getListHashes` сравнивает строки только по
   `recordhash`, поэтому пропущенная колонка = «изменение не видно, на
   SERVER не уезжает», а смена порядка = разные md5 у одной строки на
   LOCAL и SERVER (полный re-sync на каждом проходе). Для `tbl_authors`
   канонический набор (17 колонок) и порядок зафиксированы в
   `deploy/karaoke-db/46_author_sort_order.sql` и `18_author_aliases.sql`,
   обоснование — [sql-migrations.md](../../system/infra/sql-migrations.md)
   Решение 6 (BR-534). Для `tbl_site_users` канонический набор (19 колонок) — в
   `deploy/recordhash_site_users.sql` (BR-044); `last_login_at`/`created_at` намеренно
   помечены `useInHash = false`.
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

## NULL в `save()` и ошибка UPDATE

- `bindRecordDiffValue` биндит `null` как типизированный SQL NULL
  (`ps.setNull(index, sqlTypeForNull(oldValue))`): `Timestamp → TIMESTAMP`,
  `Boolean → BOOLEAN`, `String → VARCHAR` и т.д. Раньше `null` попадал в
  `ps.setString(index, "null")`, PostgreSQL падал на `timestamp`/`boolean`, и
  терялся **весь** пакет правок одного `save()` (UPDATE — один `SET`).
- Ошибка `executeUpdate()` логируется и **пробрасывается**
  (`IllegalStateException`): контроллер отдаёт 5xx вместо ложного успеха, SSE
  `recordChange` не рассылается. Это соответствует ADR local-0002 (ошибки БД не
  подменяются пустым результатом).

**Ловушка.** `clearPublishAt=true` (`POST /api/news/update`) — штатный путь
обнуления `News.publish_at`; без `setNull` он «успешно» не сохранял ни
`publish_at`, ни остальные поля запроса. Регрессия:
`KaraokeDbTableNullBindingTest`.

## Инварианты чтения и create-or-get

- **Сбой чтения ≠ «строк нет».** `KaraokeDbTable.loadList(throwOnError = true)`
  пробрасывает `SQLException` (в т.ч. при отсутствии соединения) — для путей
  create-or-get, где пустой список приводил к созданию дубликата. BR-184.
- **Create-or-get не создаёт сущность при сбое чтения** — `Album.findOrCreateForSongImport`,
  `Album.findOrCreateForSongImportRaw`, `Utils.getAuthorForRequest`. BR-184.
- **Базовый слой логирует через SLF4J** (ADR `local-0005`): `printStackTrace()` и
  `println()` в `KaraokeDbTable` заменены на `log.error` / `log.warn` с объектом
  исключения. BR-187.
- **Ошибка вставки различается с гонкой**: `createDbInstance` логирует `SQLState`;
  `23505` (unique violation) — ожидаемая гонка (INFO), остальное — ошибка (ERROR).
  BR-181.

## Changelog

- **Pass 341 P2** (2026-09-09): Initial. Автор: agent (Karaoke).
- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): добавлена секция «Публичные контракты (API)». Автор: agent (Karaoke).

# Feature Specification: Schema-cache в KaraokeDbTable.loadList

**Feature Branch**: `243-db-table-schema-cache`
**Created**: 2026-08-26
**Status**: Draft
**Input**: Tier-1 P0 фикс из parent спеки `specs/241-db-storage-perf-audit/spec.md` (FR-102).
Устранить дополнительный SQL к `information_schema.columns` на каждый `loadList` с `ignoreUseInList=false`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Прозрачный schema-cache при `loadList` (Priority: P1)

Разработчик/админ запускает операцию, которая вызывает `KaraokeDbTable.loadList(..., ignoreUseInList=false)`
для конкретной таблицы (например, `loadList` для `tbl_songs`, `tbl_authors`, `tbl_pictures` и др.).
Первый вызов для пары `(tableName, databaseName)` идёт в БД и кеширует список колонок.
Все последующие вызовы `loadList` (с той же парой ключей) **за время жизни процесса** возвращают
список колонок из in-memory кеша без сетевого round-trip к PostgreSQL.
Существующие 41 caller `loadList` НЕ требуют изменений — кеш полностью прозрачен.

**Why this priority**: Tier-1 P0 фикс из аудита производительности БД. На каждое обращение к
`loadList(ignoreUseInList=false)` сейчас идёт дополнительный запрос к `information_schema.columns`.
На больших операциях (2500 песен в `Song.loadList` и др.) это лишний round-trip; в масштабе проекта
накопленный эффект — десятки-сотни лишних SQL-запросов на одну пользовательскую операцию.

**Independent Test**: Открыть DevTools/журнал SQL в DEBUG режиме, выполнить `Song.loadList(...)`
дважды подряд для одной и той же таблицы/БД — на второй вызов `SELECT column_name FROM information_schema.columns WHERE ...`
НЕ выполняется (нет в логе PostgreSQL). Список колонок для SELECT-проекции в обоих случаях
идентичный.

**Acceptance Scenarios**:

1. **Given** кеш пуст и `karaoke.db.schema_cache.enabled = true` (дефолт), **When** любой `loadList(..., ignoreUseInList=false)` вызывается впервые для пары `(tbl_xxx, <db>)`, **Then** выполняется **один** SQL к `information_schema.columns`, результат сохраняется в кеш и используется для построения `SELECT ... FROM tbl_xxx`.
2. **Given** кеш для пары `(tbl_xxx, <db>)` уже есть и TTL не истёк (1 час), **When** любой `loadList(..., ignoreUseInList=false)` вызывается снова, **Then** SQL к `information_schema.columns` НЕ выполняется, проекция строится из кеша.
3. **Given** кеш для пары `(tbl_xxx, <db>)` истёк (TTL=1ч), **When** следующий `loadList` вызывается, **Then** старая запись игнорируется, идёт один SQL к `information_schema.columns`, кеш обновляется.
4. **Given** `karaoke.db.schema_cache.enabled = false`, **When** любой `loadList` вызывается, **Then** кеш не используется, каждый вызов идёт в БД (старое поведение, поведение для отладки).
5. **Given** SQL к `information_schema.columns` вернул пустой список или упал с `SQLException`, **When** сохраняется результат в кеш, **Then** пустой/ошибочный результат НЕ кешируется (FR-003 — следующий вызов повторит попытку).

### User Story 2 — Управляемая инвалидация кеша (Priority: P2)

Разработчик/админ после миграции схемы (добавление/удаление колонок) может **принудительно сбросить**
кеш schema для конкретной таблицы, конкретной БД или глобально. Используется в пост-миграционных хуках
или вручную из тестового endpoint'а. Без инвалидации кеш устареет максимум через TTL=1ч.

**Why this priority**: Без инвалидации после `ALTER TABLE ADD COLUMN ...` закэшированный список колонок
будет неполным до 1ч — это видно как «нет новой колонки в SELECT» / «unknown column». P2, потому что
TTL=1ч — приемлемый компромисс в большинстве случаев; ручная инвалидация нужна для hot-fixes и тестов.

**Independent Test**: Вызвать `loadList` для пары `(tbl_xxx, <db>)`, затем вызвать `invalidateSchemaCache("tbl_xxx", db)`, затем снова `loadList` — второй вызов идёт в БД (SQL к `information_schema.columns` присутствует в логе), третий — снова из кеша.

**Acceptance Scenarios**:

1. **Given** кеш заполнен для нескольких таблиц и БД, **When** вызывается `invalidateSchemaCache()` без аргументов, **Then** весь кеш очищается (следующие обращения идут в БД).
2. **Given** кеш заполнен для нескольких таблиц, **When** вызывается `invalidateSchemaCache(tableName = "tbl_songs")`, **Then** удаляются только записи с этим `tableName` (все БД), остальные таблицы остаются в кеше.
3. **Given** кеш заполнен для нескольких БД, **When** вызывается `invalidateSchemaCache(database = someDb)`, **Then** удаляются только записи для этой БД (все таблицы), остальные БД остаются.
4. **Given** кеш заполнен, **When** вызывается `invalidateSchemaCache("tbl_songs", someDb)`, **Then** удаляется ровно одна запись для пары `(tbl_songs, someDb.name)`.

## Edge Cases

- **`loadList` с `ignoreUseInList=true`**: `columns()` НЕ вызывается вообще (старая ветка кода использует `SELECT table.*`); кеш не задействован. Поведение неизменно.
- **Ошибка подключения к БД в `columns()`**: `emptyList()` возвращается, НЕ кешируется; SQL-проекция `SELECT ...` для `loadList` строится как пустая → скорее всего `WHERE` отработает по пустой проекции (старое поведение, без изменений).
- **`KaraokeConnection.name` пустой или совпадает у разных инстансов**: ключ `Pair(tableName, database.name)` корректно разделяет кеш между разными БД. Если кто-то осознанно дал одинаковый `name` двум разным подключениям — они разделят кеш (это документировано в KDoc).
- **TTL 1ч и долгоживущий процесс**: через час запись устаревает, проверка через `expiresAtMs > System.currentTimeMillis()`. Процесс НЕ делает фоновой очистки просроченных записей — они удаляются по факту обращения (`remove(key)` после промаха) или по явному `invalidateSchemaCache()`. Это приемлемо: 1 запись = `Pair<String, String>` ключ + 2-3 колонки = десятки байт.
- **Параллельные первые обращения к одной таблице**: `ConcurrentHashMap` гарантирует, что put выполняется атомарно; худший сценарий — два параллельных SQL к `information_schema.columns` для одной пары → оба результата эквивалентны, последний `put` перезаписывает первый. Допустимо.
- **Выключение кеша через `karaoke.db.schema_cache.enabled = false`**: каждый вызов идёт в БД (поведение как до спеки). Используется при отладке schema-related багов.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Метод `KaraokeDbTable.Companion.columns()` MUST перед SQL к `information_schema.columns` проверить наличие свежей записи в in-memory кеше `ConcurrentHashMap<Pair<String, String>, SchemaCacheEntry>` для ключа `(tableName, database.name)` и использовать её, если `expiresAtMs > System.currentTimeMillis()`.
- **FR-002**: После успешного SQL (результат не пустой) MUST записать `SchemaCacheEntry(columnNames = ..., expiresAtMs = System.currentTimeMillis() + 3_600_000L)` в кеш.
- **FR-003**: Пустой результат SQL (`emptyList()`) или результат при `SQLException` MUST **НЕ** сохраняться в кеш. Следующий вызов повторит попытку SQL.
- **FR-004**: Kеш MUST быть процесс-локальным (`private` `companion object`-поле), без сериализации и без внешнего состояния. Перезапуск процесса = чистый кеш.
- **FR-005**: Публичный метод `invalidateSchemaCache(tableName: String? = null, database: KaraokeConnection? = null)` MUST поддерживать четыре режима: очистка всего кеша; только по `tableName`; только по `database.name`; по конкретной паре `(tableName, database.name)`.
- **FR-006**: Существующая логика SQL к `information_schema.columns` MUST быть вынесена в `private fun columnsFromDb(tableName, database): List<String>` для тестируемости и читаемости (новая функция-обёртка для прозрачного переключения кеш/БД).
- **FR-007**: Kеш MUST быть опционально отключаемым через свойство `karaoke.db.schema_cache.enabled` в `KaraokeProperties` (дефолт `true`). Если `KaraokeProperties.getBoolean("karaoke.db.schema_cache.enabled")` возвращает `false` — кеш не используется, каждый вызов идёт в БД.
- **FR-008**: KDoc на `columns()`, `columnsFromDb()` и `invalidateSchemaCache()` MUST присутствовать со ссылкой на эту спеку и кратким описанием (Constitution § VI — KDoc на публичных и существенных `private` символах).
- **FR-009**: Существующие 41 caller `KaraokeDbTable.loadList` MUST НЕ требовать изменений — изменение полностью прозрачно на уровне companion object.
- **FR-010**: Иммутабельность записи кеша MUST обеспечиваться `private data class SchemaCacheEntry(val columnNames: List<String>, val expiresAtMs: Long)` (без `var`).

### Key Entities

- **SchemaCacheEntry**: `private data class` в companion object, поля: `columnNames: List<String>`, `expiresAtMs: Long`. Неизменяемая (`val`-only). Хранится в `ConcurrentHashMap` как value.
- **schemaCache**: `private val ConcurrentHashMap<Pair<String, String>, SchemaCacheEntry>` в companion object. Ключ — пара `(tableName, databaseName)`. Thread-safe по контракту `ConcurrentHashMap`.
- **SCHEMA_CACHE_TTL_MS**: `private const val Long = 3_600_000L` (= 1 час).

### Success Criteria

- **SC-001**: При первом вызове `Song.loadList(args, db)` SQL к `information_schema.columns` виден в логе PostgreSQL ровно один раз. При втором вызове той же таблицы и той же БД — этого SQL нет.
- **SC-002**: После изменения `karaoke.db.schema_cache.enabled = false` в `KaraokeProperties` следующие вызовы `loadList` идут в БД на каждое обращение (старое поведение восстановлено).
- **SC-003**: После `invalidateSchemaCache("tbl_songs", db)` следующий вызов `loadList` для `tbl_songs` в `db` идёт в БД; последующие — снова из кеша.
- **SC-004**: Проект успешно компилируется (`./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` PASS), проходит `ktlintCheck` без НОВЫХ нарушений, `bootJar` собирается.
- **SC-005**: Все 41 caller `KaraokeDbTable.loadList` работают без изменений сигнатур — бинарная совместимость сохранена.

## Assumptions

- **A-001**: `KaraokeProperties` уже инициализирован к моменту первого обращения к `columns()` (стандартный flow, не нововведение).
- **A-002**: TTL=1ч — разумный компромисс между агрессивным кешированием (риск stale данных после миграции) и минимизацией SQL round-trip'ов. При миграции схемы вызывающий код вызывает `invalidateSchemaCache(...)` явно.
- **A-003**: Потокобезопасность через `ConcurrentHashMap` достаточна; дополнительной синхронизации не требуется.
- **A-004**: `database.name` — стабильный идентификатор подключения (не меняется на лету). Если меняется — кеш для старого name остаётся «висеть» до TTL или явной инвалидации (приемлемо для in-memory кеша).

## Out of Scope

- Hot-reload настроек `karaoke.db.schema_cache.enabled` без перезапуска — настройка читается на каждый вызов `columns()`, изменение подхватывается естественным образом.
- Персистентный кеш (Redis/файл) — out of scope, in-memory достаточно.
- Метрики/счётчики попаданий в кеш — out of scope, можно добавить отдельной спекой.
- Авто-инвалидация при `ALTER TABLE` через PostgreSQL `LISTEN/NOTIFY` — out of scope, ручная инвалидация достаточна для типовых сценариев.

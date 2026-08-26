# Feature Specification: Батч-синхронизация sync-записей в KaraokeProcessWorker

**Feature Branch**: `242-db-sync-batch-worker`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-101
**Input**: User description (через parent спеку): "Батч-синхронизация sync-записей в `KaraokeProcessWorker` — устранить N+1 в sync-цикле (каждые 24 сек), снизить 1+2N SQL до 1+2*(N/chunk) на каждую итерацию."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Админ видит низкую нагрузку на БД от sync-цикла (Priority: P1)

Администратор `karaoke-app` (admin-машина) после большого push LOCAL→SERVER (например, 100 песен через `SyncRegistry.tick()`) не наблюдает лавины запросов к БД. Sync-цикл в `KaraokeProcessWorker` обрабатывает N sync-записей через batch `WHERE id IN (...)` + батч-Delete, а не по одной. Замер `pg_log` за 24 часа до/после деплоя показывает снижение SQL-запросов от sync-цикла в 20–40× при N=100 (с 201 до 5–11).

**Why this priority**: нарушает Constitutional Principle II («Загрузка записей для diff — пакетно `WHERE id IN (..)`, не по одной в цикле»). На 100 sync-записях = 201 SQL за один цикл каждые 24 сек — это самый «горячий» hotspot в `KaraokeProcessWorker` (см. parent спека, раздел A.1, H-2).

**Independent Test**: при моке 100 sync-записей в `tbl_songs_sync` цикл `doStart()` делает ≤ 11 SQL-запросов вместо 201.

**Acceptance Scenarios**:
1. **Given** 100 sync-записей в `tbl_songs_sync` (REMOTE), **When** цикл `KaraokeProcessWorker.doStart()` доходит до `intervalCheckFiles` (24 сек), **Then** общее число SQL-запросов за итерацию ≤ 11 (1 SELECT всех sync + 1 SELECT LOCAL по чанкам + 1 DELETE_REMOTE по чанкам).
2. **Given** 0 sync-записей в `tbl_songs_sync`, **When** цикл доходит до `intervalCheckFiles`, **Then** общее число SQL-запросов за итерацию = 1 (только SELECT всех sync, который возвращает пустой ResultSet).
3. **Given** sync-запись с `tags = "RENDER"` (триггер на создание karaoke-процессов), **When** она обрабатывается, **Then** батч-логика НЕ ломает существующий side-effect (создание `.srt`-файлов и `KaraokeProcess.createProcess`).

---

### User Story 2 — Высокая отзывчивость UI при больших sync-окнах (Priority: P2)

UI админ-панели (`webvue3`) во время синхронизации LOCAL→SERVER остаётся отзывчивым: администратор может переключать вкладки, открывать Song Editor, и реакция < 200 мс, потому что sync-цикл не блокирует JDBC-соединение других потоков.

**Why this priority**: текущий sync-цикл держит 1 JDBC-соединение (из ThreadLocal) на всё время 1+2N запросов. На 100 записях это может быть 1–3 сек блокировки соединения, что в админ-пуле (Tomcat) задерживает другие админские операции.

**Independent Test**: под нагрузкой (admin параллельно открывает Song Editor) — `getSongsCreateKaraokeAll` (FR-103) и sync-цикл выполняются параллельно без contention на соединении.

**Acceptance Scenarios**:
1. **Given** sync-цикл выполняется, **When** администратор параллельно открывает Song Editor, **Then** UI не виснет > 200 мс.

---

### Edge Cases

- **Что если chunk_size выбран слишком большим и упрётся в `socketTimeout=30` на remote?** Это уже учтено в `SongSyncTarget.rowChunkSize = 25` (см. parent спека, раздел A.4). Батч-логика должна использовать тот же chunk_size.
- **Что если sync-запись была удалена между SELECT sync и SELECT local?** Текущий код: если `songLocal == null`, то INSERT из sync — это сохраняем.
- **Что если `tags = "RENDER"` для большого числа sync-записей?** Каждая такая запись запускает тяжёлую операцию (создание karaoke-процессов). Батч-логика НЕ должна объединять их в один процесс — это per-song side-effect, который остаётся как есть.
- **Что если sync-цикл прерывается посередине (исключение в одном из запросов)?** Должна быть гарантия, что мы не оставим «половину» обработанных записей: либо rollback через транзакцию, либо пропуск с логированием.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `KaraokeProcessWorker.doStart()` sync-цикл (строки ~998–1106) MUST заменить `listSongsSync.forEach { songSync -> Song.loadFromDbById(songSync.id, database = Connection.local(), ...) }` на пакетный `Song.loadListFromDbByIds(listSongsSync.map { it.id }.chunked(CHUNK_SIZE), database = Connection.local(), ...)`. Это снижает N SELECT к LOCAL до `ceil(N / CHUNK_SIZE)` запросов.
- **FR-002**: Тот же цикл MUST заменить `listSongsSync.map { it.id }.forEach { idToDel -> Song.deleteFromDb(id = idToDel, database = Connection.remote(), sync = true) }` на батч-Delete `Song.deleteFromDbByIds(listSongsSync.map { it.id }.chunked(DELETE_CHUNK_SIZE), database = Connection.remote(), sync = true)`. Это снижает N DELETE к REMOTE до `ceil(N / DELETE_CHUNK_SIZE)`.
- **FR-003**: `CHUNK_SIZE` для SELECT к LOCAL MUST быть равен `SongSyncTarget.rowChunkSize = 25` (см. parent спека, раздел A.4 — паттерн «тяжёлые строки по 25»). `DELETE_CHUNK_SIZE` MUST быть равен `SyncRegistry.DELETE_CHUNK_SIZE` (уже есть большой общий размер для DELETE).
- **FR-004**: После замены цикл MUST корректно обрабатывать `songLocal == null` (запись отсутствует в LOCAL → INSERT из sync). Логика INSERT (строки ~1056–1065) остаётся per-record, но может быть оптимизирована в отдельной задаче.
- **FR-005**: Side-effect для `tags == "RENDER"` (строки ~1067–1100 — генерация `.srt` + создание karaoke-процессов) MUST оставаться per-record, не объединяется в батч (это per-song операция, см. Edge Cases).
- **FR-006**: Sync-цикл MUST логировать: количество обработанных sync-записей, количество сделанных SQL-запросов (для верификации эффекта через `pg_log`). Логирование — в текущий формат `println("[${Timestamp.from(Instant.now())}] …")` (для совместимости с docker logs).

### Key Entities

- **`tbl_songs_sync`** (REMOTE) — таблица, в которую `SongSyncTarget.getSqlToInsert` пишет при push LOCAL→SERVER. Sync-цикл в `KaraokeProcessWorker` забирает из неё записи и переносит в `tbl_songs` LOCAL (или применяет UPDATE).
- **`Song.loadListFromDbByIds`** (уже существует, см. parent спека, A.4) — batch-загрузка по `WHERE id IN (...)`, чанками.
- **`Song.deleteFromDbByIds`** / **батч-Delete через `id = ANY(?)`** — существует упоминание в `KaraokeDbTable.deleteBatch` (см. parent спека, H-12). Если ещё нет — добавить.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При 100 sync-записях в `tbl_songs_sync` цикл `KaraokeProcessWorker.doStart()` (sync-фаза, каждые 24 сек) делает ≤ 11 SQL-запросов (1 + 4 SELECT_LOCAL + 1 + 4 DELETE_REMOTE, при `chunk = 25`). Текущее значение — 201.
- **SC-002**: При 1000 sync-записях (большое окно после импорта) цикл делает ≤ 82 SQL-запроса. Текущее — 2001.
- **SC-003**: `pg_log` за 24 часа до/после деплоя показывает снижение SQL от `KaraokeProcessWorker` на ≥ 30% в часы пиковой синхронизации (дни, когда админ делает большой push).
- **SC-004**: Code metrics: новая функция `KaraokeProcessWorker.processRemoteSongsSyncBatch(database, storageService, storageApiClient)` имеет цикломатическую сложность ≤ 8 и ≤ 60 строк кода (извлечение из 100-строчного блока делает код читаемее).
- **SC-005**: Поведение для `tags = "RENDER"` сохраняется 1-в-1 (regression-тест: после большого push с RENDER-тегом все песни получают karaoke-процессы, как и раньше).
- **SC-006**: Существующие `loadListFromDbByIds` и `KaraokeDbTable.deleteBatch` (если уже есть) переиспользуются без дублирования логики. Если `deleteBatch` ещё не реализован — добавляется как утилитарный метод в `KaraokeDbTable` с batch через `id = ANY(?)`.

## Assumptions

- **`Song.loadListFromDbByIds`** уже существует (упоминается в `SyncTarget.kt:241`) и использует паттерн `chunked(rowChunkSize)` с `WHERE id IN (...)`. Поведение проверено в `SongSyncTarget.loadByIds`.
- **`KaraokeDbTable.deleteBatch`** (или эквивалент) либо уже есть, либо будет добавлен в рамках этой фичи как утилитарный метод. Альтернатива: использовать существующий `KaraokeDbTable.delete(tableName, id, database)` в цикле — НЕ подходит (это и есть N+1, от которого уходим).
- **Соединения к REMOTE и LOCAL** — это singleton-фабрики `Connection.remote()` / `Connection.local()` (см. parent спека, A.4, specs/234-db-sync-connection-leak). Батч-логика НЕ создаёт новых инстансов.
- **`socketTimeout=30`** на remote БД (см. `Connection.kt` URL). `CHUNK_SIZE = 25` уже подобран под этот таймаут для Song (тяжёлые строки); для DELETE (лёгкие `DELETE FROM ... WHERE id = ?`) — `DELETE_CHUNK_SIZE` существенно больше (см. `SyncRegistry.DELETE_CHUNK_SIZE` в parent спеке).
- **`tags = "RENDER"`** — это side-effect для запуска render-процессов на новой песне. Эта логика per-song, батчинг НЕ применяется (см. Edge Cases).
- **Pre-commit/CI-gate** не ломается: правки в `KaraokeProcessWorker.kt` проходят через обычный CI/lint/compile pipeline (см. AGENTS.md, секция «Обязательная проверка после ЛЮБОГО изменения кода»).
- **Замер эффекта**: pre/post `pg_log` (24 часа до/после деплоя) + ручные `EXPLAIN ANALYZE` конкретных запросов (см. parent спека, Clarifications Session 2026-08-26).

## Out of Scope (явно НЕ делается в этой фиче)

- Рефакторинг `KaraokeProcessWorker.doStart()` целиком (1264 строк) — только sync-блок (~100 строк).
- Оптимизация per-song INSERT-логики (строки ~1056–1065, `songSync.getSqlToInsert()` в цикле) — это потенциальный Tier-3 hotspot, отдельная фича.
- Изменение side-effect для `tags = "RENDER"` — только проверка, что он сохранился.
- Изменение `intervalCheckFiles = 24_000` (24 сек) — константа, не трогаем.
- Изменение паттерна `SongSyncTarget` (он уже использует правильный `loadByIds` через `chunked`).
- Tier-2/Tier-3 оптимизации из parent спеки — отдельные фичи.
- Изменение стека доступа к БД (Constitution § II «Сырой JDBC»).
- Включение `pg_stat_statements` — перенесено в backlog (см. parent спека, Clarifications).

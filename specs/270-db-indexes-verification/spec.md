# Feature Specification: Верификация и идемпотентное создание индексов FR-110

**Feature Branch**: `270-db-indexes-verification`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-2 / FR-110
**Input**: User description (через parent спеку FR-110): "PostgreSQL MUST иметь индекс `idx_songs_song_author` на `tbl_songs(song_author)`, `idx_songs_id_status` на `tbl_songs(id_status)`, `idx_events_song_id` на `tbl_events(song_id)` — для ускорения всех `GROUP BY song_author`, `WHERE id_status>=6`, `GROUP BY song_id` запросов. Проверить наличие и добавить через SQL-миграцию если нет."

## Clarifications

### Session 2026-08-26

- **Q**: Все три индекса уже созданы в `01_initdb.sql` (изначально как `tbl_settings_*_index`, переименованы в `28_rename_settings_to_songs.sql`) — есть ли вообще что делать?
  **A**: A — создать **идемпотентную** миграцию `41_db_indexes_verification.sql` через `CREATE INDEX IF NOT EXISTS`. На текущем проде это **no-op** (индексы уже есть), но: (1) задокументировано в git-истории; (2) защита от случая поднятия БД из старого дампа без индексов; (3) baseline для будущих фич, которым понадобятся эти индексы.
- **Q**: Какие именно имена индексов использовать — `idx_*` (как в спецификации FR-110) или существующие `tbl_*_*_index` (PostgreSQL convention проекта)?
  **A**: B — **существующие имена `tbl_*_*_index`** (convention проекта). Спека FR-110 использовала `idx_*` для краткости, но в `01_initdb.sql` все индексы названы `tbl_<table>_<column>_index`. Миграция должна использовать существующие имена, чтобы `CREATE INDEX IF NOT EXISTS` сработал корректно.
- **Q**: Использовать `CONCURRENTLY` (без блокировки таблицы на запись) или обычный `CREATE INDEX`?
  **A**: A — `CREATE INDEX IF NOT EXISTS` (без CONCURRENTLY). Поскольку индекс либо уже есть (на проде), либо таблица маленькая в dev (миграция применяется на свежей БД в dev). CONCURRENTLY нельзя использовать внутри транзакции и с IF NOT EXISTS — это известное ограничение PostgreSQL.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Индексы гарантированно присутствуют на любой БД (Priority: P2)

Администратор/DBA поднимает БД из любого источника (свежий дамп, восстановление из backup, копия старого дампа) и запускает миграции 01-41. Все три индекса `tbl_songs_song_author_index`, `tbl_songs_id_status_index`, `tbl_events_song_id_index` гарантированно созданы.

**Why this priority**: tier-2 P1 фикс для parent спеки 241 (FR-110) — на текущем проде индексы уже есть (созданы в `01_initdb.sql`), но без явной миграции нет гарантии, что новый deployment или восстановление из старого дампа будет их содержать.

**Independent Test**: запустить миграцию на пустой БД → проверить `\d tbl_songs` и `\d tbl_events` в psql — все три индекса присутствуют. Запустить миграцию на БД с уже существующими индексами → no-op (IF NOT EXISTS).

**Acceptance Scenarios**:
1. **Given** пустая БД (или БД без индексов), **When** применяется `41_db_indexes_verification.sql`, **Then** создаются 3 индекса: `tbl_songs_song_author_index`, `tbl_songs_id_status_index`, `tbl_events_song_id_index`.
2. **Given** БД с уже существующими индексами (текущий прод), **When** применяется миграция, **Then** все `CREATE INDEX IF NOT EXISTS` — no-op, ошибок нет.
3. **Given** таблица `tbl_songs` содержит 18k+ записей, **When** выполняется `EXPLAIN ANALYZE SELECT song_author, count(*) FROM tbl_songs WHERE id_status >= 6 GROUP BY song_author`, **Then** план запроса использует `tbl_songs_id_status_index` для WHERE-фильтра и `tbl_songs_song_author_index` для GROUP BY (Index Scan / Bitmap Index Scan, не Seq Scan).
4. **Given** таблица `tbl_events` содержит 100k+ записей, **When** выполняется `EXPLAIN ANALYZE SELECT song_id, count(*) FROM tbl_events WHERE song_id > 0 GROUP BY song_id`, **Then** план использует `tbl_events_song_id_index` (Index Scan, не Seq Scan).

### User Story 2 — Документирование в git-истории (Priority: P3)

Каждое архитектурное решение в проекте отражено в git-истории через миграцию в `deploy/karaoke-db/`. FR-110 из parent спеки 241 теперь имеет явный артефакт в `deploy/karaoke-db/41_db_indexes_verification.sql`.

**Why this priority**: проект использует `deploy/karaoke-db/<NN>_<slug>.sql` как **единственный источник истины** для изменений схемы БД. Без явной миграции FR-110 «существует только в спецификации», что нарушает FR-014 (LiveDoc) и общий принцип «specs ↔ code ↔ migrations» согласованы.

**Independent Test**: `git log --oneline deploy/karaoke-db/41_db_indexes_verification.sql` — коммит с миграцией существует и ссылается на FR-110.

**Acceptance Scenarios**:
1. **Given** parent спека 241 (FR-110), **When** разработчик ищет соответствующую миграцию, **Then** находит `deploy/karaoke-db/41_db_indexes_verification.sql` с комментарием-ссылкой на FR-110.
2. **Given** LiveDoc `270-db-indexes-verification.md`, **When** разработчик читает его, **Then** видит ссылку на миграцию и parent спеку.

## Edge Cases

- **Что если индекс с таким именем уже существует, но на другой колонке**? `CREATE INDEX IF NOT EXISTS` выдаст warning, но не упадёт. Миграция предполагает, что имена уникальны в пределах схемы (это convention проекта — см. `01_initdb.sql`).
- **Что если таблица не существует** (БД fresh, до `01_initdb.sql`)? `CREATE INDEX` упадёт с ошибкой. Миграция должна применяться **после** всех миграций до 28 (rename `tbl_settings` → `tbl_songs`) — это естественный порядок по номеру.
- **Что если `tbl_events` ещё не создана** (БД fresh до `03_events.sql`)? Та же логика — миграция применяется после `03_events.sql`. Поскольку миграция `41`, она естественно применяется после всех предыдущих.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: MUST существовать файл `deploy/karaoke-db/41_db_indexes_verification.sql` с SQL-командами `CREATE INDEX IF NOT EXISTS` для трёх индексов.
- **FR-002**: Миграция MUST содержать в начале header-комментарий с описанием (что делает, зачем, FR-ссылка) и блоком **Apply** (локально + прод), как в `40_site_user_can_self_assign_tasks.sql`.
- **FR-003**: Индексы MUST иметь имена **существующих** индексов из `01_initdb.sql`: `tbl_songs_song_author_index`, `tbl_songs_id_status_index`, `tbl_events_song_id_index` — НЕ `idx_*` (это convention проекта).
- **FR-004**: Миграция MUST быть идемпотентной (`CREATE INDEX IF NOT EXISTS` — повторное применение безопасно).
- **FR-005**: MUST существовать LiveDoc `livedocs/features/270-db-indexes-verification.md` со ссылками на parent спеку, миграцию, и существующие индексы в `01_initdb.sql`.
- **FR-006**: Миграция MUST НЕ использовать `CREATE INDEX CONCURRENTLY` (несовместимо с `IF NOT EXISTS` в PostgreSQL <14; даже в 14+ IF NOT EXISTS + CONCURRENTLY даёт неожиданное поведение при отсутствии индекса).

### Key Entities

- **Migration file** (`41_db_indexes_verification.sql`): SQL-скрипт с тремя `CREATE INDEX IF NOT EXISTS`.
- **Index** (`tbl_*_*_index`): btree-индекс на колонку таблицы. Уже существует с `01_initdb.sql` (исходно `tbl_settings_*_index`, переименован в `28_rename_settings_to_songs.sql`).

### Existing Indexes (уже в `01_initdb.sql`)

| Index name | Table | Column | Created in |
|------------|-------|--------|------------|
| `tbl_songs_song_author_index` | tbl_songs | song_author | 01_initdb.sql:173 → 28_rename_settings_to_songs.sql:66 |
| `tbl_songs_id_status_index` | tbl_songs | id_status | 01_initdb.sql:148 → 28_rename_settings_to_songs.sql:48 |
| `tbl_events_song_id_index` | tbl_events | song_id | dump (verified in `karaoke_clear_dump.sql:2542-2545`) |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: На свежей БД (после применения 01-41 миграций) — `psql \d tbl_songs` показывает оба индекса, `psql \d tbl_events` показывает индекс на `song_id`.
- **SC-002**: На текущем проде (где индексы уже есть) — повторное применение миграции **no-op** (0 ошибок, 0 изменений).
- **SC-003**: `EXPLAIN ANALYZE SELECT song_author, count(*) FROM tbl_songs WHERE id_status >= 6 GROUP BY song_author` использует индексы (Index Scan / Bitmap Index Scan, не Seq Scan).
- **SC-004**: `EXPLAIN ANALYZE SELECT song_id, count(*) FROM tbl_events WHERE song_id > 0 GROUP BY song_id` использует `tbl_events_song_id_index` (Index Scan, не Seq Scan).
- **SC-005**: Миграция + LiveDoc + спека — единый coherent набор артефактов в git-истории.

## Assumptions

- **Convention проекта**: имена индексов = `tbl_<table>_<column>_index` (см. `01_initdb.sql`). Не `idx_*` (несмотря на спецификацию FR-110).
- **Миграция применяется ПОСЛЕ** всех предыдущих миграций (≤40), потому что таблицы `tbl_songs` и `tbl_events` уже созданы. По номеру 41 это естественно.
- **Текущий прод уже содержит индексы** (созданы в `01_initdb.sql:148,173` и переименованы в `28_rename_settings_to_songs.sql:48,66`; `tbl_events_song_id_index` — в исходном `01_initdb.sql` или дампе). Миграция — **no-op** на текущем проде.
- **PostgreSQL версии** на проде поддерживает `CREATE INDEX IF NOT EXISTS` (есть с PostgreSQL 9.5, проект на 14+, см. `pg_hba.conf`).
- **Тестовая БД** для проверки миграции — локальный docker-compose с `karaoke-db` (есть в `deploy/`).
- **`CREATE INDEX IF NOT EXISTS` warnings** (если индекс существует, но на другой колонке) — допустимы, convention проекта исключает такой случай.

## Out of Scope

- Создание новых индексов (всё уже есть).
- `CONCURRENTLY` (см. Clarifications Q3).
- Изменение существующих индексов (DROP/CREATE).
- Partitioning таблиц (Constitution не требует).
- Полный аудит всех индексов проекта — только три индекса из FR-110.

## Reference

- Parent спека: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md), FR-110, H-5, H-6, H-110.
- Existing migration: [`deploy/karaoke-db/01_initdb.sql:148,173`](../../deploy/karaoke-db/01_initdb.sql).
- Renamed indexes: [`deploy/karaoke-db/28_rename_settings_to_songs.sql:48,66`](../../deploy/karaoke-db/28_rename_settings_to_songs.sql).
- Dumps reference: [`deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql:2542-2545, 2731-2734, 2857-2860`](../../deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql).
- Migration template: [`deploy/karaoke-db/40_site_user_can_self_assign_tasks.sql`](../../deploy/karaoke-db/40_site_user_can_self_assign_tasks.sql).
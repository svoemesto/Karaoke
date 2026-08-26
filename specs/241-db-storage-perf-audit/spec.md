# Feature Specification: Аудит производительности БД и хранилища (prod)

**Feature Branch**: `241-db-storage-perf-audit`
**Created**: 2026-08-26
**Status**: Draft
**Input**: User description: "Несмотря на все наши старания сервер периодически подвисает. Проведи анализ всех мест, где идёт обращение к базе данных и хранилищу при работе на проде, выяви места где могут быть массовые запросы, которые могут тормозить сервер, предложи варианты оптимизации."

## Clarifications

### Session 2026-08-26

- **Q**: Какие именно из 4 P0 пунктов берём в первый спринт как самостоятельные фичи (каждая со своим `/speckit.specify` → branch → PR)?
  **A**: A — все 4 P0 (FR-101, FR-102, FR-103, FR-104), каждая отдельной фичей.
- **Q**: Нужно ли включать `pg_stat_statements` ДО начала Tier-1 оптимизаций (baseline метрики)?
  **A**: D — не включать. Замеры через логи `pg_log` + ручные `EXPLAIN ANALYZE`. FR-108 переводится в backlog.
- **Q**: После деплоя каждой из 4 Tier-1 оптимизаций — как валидировать эффект (что SC-001/SC-002/SC-004/SC-005 достигнуты)?
  **A**: A — pre/post `pg_log` (24 часа до/после) + ручные `EXPLAIN ANALYZE` конкретных hot queries. Workflow описан в Assumptions → «Замеры эффекта оптимизаций».

## Scope *(mandatory)*

### In Scope (первый спринт — 4 самостоятельных фичи, каждая в отдельной feature-ветке)

- **FR-101 [P0]** — Батч в sync-цикле `KaraokeProcessWorker` (`KaraokeProcessWorker.kt:998-1106`).
- **FR-102 [P0]** — Schema-cache в `KaraokeDbTable.loadList` (`KaraokeDbTable.kt:259-395, 224`).
- **FR-103 [P0]** — Батч в `getSongsCreateKaraokeAll` (`ApiController.kt:3683-3755`).
- **FR-104 [P0]** — Streaming для `StorageController.downloadFile` (`StorageController.kt:116-146`).

### Out of Scope (явно НЕ делается в первом спринте)

- Tier-2 оптимизации (P1): кеш для `/api/public/authors-tiles`, `getProperty`, индексы, `loadByIds`-батчи в админских контроллерах.
- Tier-3 оптимизации (P2): Thymeleaf `/statbysong`, batch INSERT для `tbl_events`, `pg_stat_statements`-инструмент наблюдения.
- Изменение стека доступа к БД (Constitution § II «Сырой JDBC»): никакого JPA/Hibernate/Exposed. Все правки — внутри текущего стека.
- Шардинг PostgreSQL, репликация, миграция на новые СУБД.
- Изменение публичного API или DTO-контрактов (`ZakromaPublicDto`, `AuthorTilePublicDto`, и т.д.) — Tier-1 оптимизации сохраняют контракт.
- Замена MinIO на альтернативное хранилище — остаётся MinIO через nginx-proxy.
- Переписывание reflection в `KaraokeDbTable` (это Tier-3/H-1 — большой рефакторинг, не помещается в один спринт; FR-102 — schema-cache как компромиссное решение).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Дашборд «hotspots БД и хранилища» (Priority: P1)

Администратор/разработчик открывает один документ и видит каталог ВСЕХ мест в коде, где идут обращения к PostgreSQL (`getConnection` → `loadList`/`forEach { loadFromDbById }`/`loadListAuthors`/`getStatBySong`) и MinIO (`downloadFile`/`fileExists`/`listFiles`), с разбивкой по severity (P0/P1/P2) и точной ссылкой `file:line`. Для каждого hotspot указаны: текущий паттерн (N+1 / full-scan / reflection / hot-loop), оценка влияния на прод (RPS / латентность / память), и предложенное исправление (batch WHERE id IN / cache / streaming / bulk statement / schema-cache).

**Why this priority**: без каталога hotspots любые оптимизации — пальцем в небо. Pass 239 (zakroma 2500 SQL-запросов per-load) показал, что даже один hotspots может «ронять» сайт на крупных авторах. Аналогичные неисправленные hotspots могут в любой момент стать узким местом при росте нагрузки.

**Independent Test**: документ существует в `specs/241-db-storage-perf-audit/spec.md`, содержит разделы «Hotspots в БД», «Hotspots в MinIO/S3», «Hotspots в фоновых задачах», «План оптимизации» с конкретными file:line.

**Acceptance Scenarios**:
1. **Given** кодовая база karaoke-app + karaoke-web, **When** аналитик запускает обзор, **Then** все 24 имплементации `KaraokeDbTable` (Song, Author, Album, KaraokeProcess, Picture, …) упомянуты с указанием, как часто их `loadList` вызывается из публичных/админских эндпоинтов и фоновых задач.
2. **Given** эндпоинт `/api/public/zakroma`, **When** он вызывается на крупном авторе (>=500 песен), **Then** в спеке указано, какие SQL идут под капотом, сколько round-trip к БД, и где возможен N+1.
3. **Given** функция `StorageController.downloadFile`, **When** она вызывается на MP4 100 MB+, **Then** указано, что `readAllBytes()` грузит весь файл в heap (OOM-риск) и какой streaming-паттерн предлагается взамен.

---

### User Story 2 — План оптимизации Tier 1 (критично) (Priority: P1)

Администратор получает упорядоченный по приоритету план: 5–8 hotspots, которые нужно починить **в первую очередь** (P0), с конкретными FR (например, FR-002 «KaraokeDbTable.loadList MUST использовать schema-cache вместо запроса к information_schema.columns на каждый вызов»).

**Why this priority**: починка одного Tier-1 hotspot может снизить нагрузку на БД в разы (например, батч в sync-цикле KaraokeProcessWorker с N+1 → 1 запрос). Без явного приоритета команда рискует чинить косметику, пока прод горит.

**Independent Test**: раздел «План оптимизации — Tier 1» содержит 3–7 пунктов, каждый с: номер FR, описание текущего поведения (file:line), описание нового поведения, ожидаемый эффект (например, «в sync-цикле при 100 sync-записях: 201 запросов → 5 запросов»).

**Acceptance Scenarios**:
1. **Given** spec, **When** разработчик читает Tier 1, **Then** каждый пункт содержит ссылку на конкретный файл/строку и измеримый эффект.
2. **Given** Tier 1, **When** администратор сравнивает с текущим кодом, **Then** все пункты соответствуют Constitutional Principle II («Сырой JDBC + дифф по хэшам», в т.ч. «Загрузка записей для diff — пакетно `WHERE id IN (..)`, не по одной в цикле»).

---

### User Story 3 — План оптимизации Tier 2/3 (важно и желательно) (Priority: P2)

Администратор получает расширенный план на следующие спринты: 5–10 hotspots P1+P2 (streaming для downloadFile, кеш для `/api/public/authors-tiles`, N+1 в `getSongsCreateKaraokeAll`, индексы на `tbl_songs.song_author` / `tbl_events.song_id`, batch INSERT для `tbl_events`, и т.д.).

**Why this priority**: Tier 2/3 — оптимизации, которые не «ронят» прод прямо сейчас, но снижают нагрузку и повышают отзывчивость. Их удобно брать в спринт как самостоятельные задачи с измеримым эффектом.

**Independent Test**: разделы «Tier 2» и «Tier 3» содержат пункты с file:line, описанием текущего/нового поведения, и метрикой эффекта.

**Acceptance Scenarios**:
1. **Given** Tier 2/3, **When** разработчик берёт один пункт в спринт, **Then** он может сразу найти место в коде по file:line и прикинуть объём правки.
2. **Given** Tier 2/3, **When** администратор смотрит совокупный эффект, **Then** спека указывает ожидаемое снижение RPS к БД / MinIO.

---

### User Story 4 — Карта scheduled-задач и их нагрузки (Priority: P2)

Администратор видит полный список всех `@Scheduled`-задач в karaoke-app + karaoke-web (на момент анализа — 11 классов, многие тикают раз в 15–60 секунд), и понимает совокупную фоновую нагрузку на БД, которая складывается с пользовательской.

**Why this priority**: 11 фоновых задач с тиком 1 мин × N HTTP-запросов = ощутимая постоянная нагрузка. Без явной карты легко пропустить, что новая фича добавила ещё одну `SELECT` каждую минуту.

**Independent Test**: раздел «Фоновые задачи и их нагрузка» содержит таблицу: имя класса, частота, какие SQL делает, где определён (file:line).

**Acceptance Scenarios**:
1. **Given** все `@Scheduled` в коде, **When** они собраны в одну таблицу, **Then** администратор видит, какие из них тикают чаще раза в минуту, и какие из них делают `SELECT` на каждую итерацию.

---

### Edge Cases

- **Что если hotspot «лже-положительный»**? Если в спеке указан N+1, но фактически он не на горячем пути — это должно быть видно по малости вызывающих мест и нулевой пользовательской нагрузке.
- **Что если предлагаемое исправление ломает Constitutional Principle II (сырой JDBC)**? Все предложения должны сохранять текущий стек доступа к БД (см. Constitution § Core Principles II).
- **Что если оптимизация требует данных, которых у нас нет** (метрики прод-нагрузки, профиль CPU)? В таких случаях спека отмечает «нужно сначала собрать данные» и предлагает инструменты (логи, pg_stat_statements, async-profiler).
- **Что если hotspots лежит в karaoke-app, который не развёрнут на проде** (см. Constitution § Технологический стек: «karaoke-app на проде не разворачивается вовсе»)? Такой hotspots помечен как «локальный (admin-only), на проде не влияет» и попадает в Tier 3.

## Requirements *(mandatory)*

### Functional Requirements

#### Документация (эта спека)

- **FR-001**: Спека MUST содержать раздел «Hotspots в БД» с перечнем конкретных file:line и описанием паттерна (N+1, full-scan, reflection, hot-loop).
- **FR-002**: Спека MUST содержать раздел «Hotspots в MinIO/S3» с file:line и оценкой риска (OOM, N round-trip, лишний HEAD).
- **FR-003**: Спека MUST содержать раздел «Фоновые задачи и их нагрузка» с таблицей всех `@Scheduled`-классов проекта.
- **FR-004**: Спека MUST содержать раздел «План оптимизации» с приоритетами P0/P1/P2 и для каждого пункта: file:line, текущее поведение, предлагаемое поведение, ожидаемый эффект.
- **FR-005**: Спека MUST ссылаться на уже существующие фичи, где уже сделаны похожие оптимизации (Pass 186, 187, 234, 235, 236, 239) — чтобы не дублировать и не противоречить.

#### Будущие фичи оптимизации (после `/speckit.clarify`/`/speckit.plan`)

- **FR-101 [P0]**: `KaraokeProcessWorker` sync-цикл (каждые 24 сек) MUST заменить `listSongsSync.forEach { songSync -> Song.loadFromDbById(...) }` и `listSongsSync.map { it.id }.forEach { idToDel -> Song.deleteFromDb(...) }` на пакетные `loadByIds(ids.chunked)`. Это снижает `1 + 2N` запросов до `1 + 2*(N/25) + 1` запросов (chunk=25, как у `SongSyncTarget`).
- **FR-102 [P0]**: `KaraokeDbTable.loadList` MUST НЕ делать `SELECT column_name FROM information_schema.columns` на каждый вызов с `ignoreUseInList=false`. Схема таблиц кешируется в `ConcurrentHashMap<String, List<String>>` с TTL 1 час (или инвалидируется по DDL-триггеру).
- **FR-103 [P0]**: `ApiController.getSongsCreateKaraokeAll` MUST заменить `ids.forEach { id -> Song.loadFromDbById(id) }` на `Song.loadListFromDbByIds(ids.chunked(N))`. Это снижает N запросов до 1 при N≤chunk.
- **FR-104 [P1]**: `StorageController.downloadFile` MUST использовать `StreamingResponseBody` или `Resource` (вместо `readAllBytes()`), чтобы не грузить весь MP4 100MB+ в heap. Для больших файлов добавить поддержку Range-запросов.
- **FR-105 [P1]**: Эндпоинт `/api/public/authors-tiles` MUST кешировать результат `Song.loadAuthorSongCounts + Song.loadListAuthors` в `KaraokeProperties`-совместимом кеше с TTL 30 сек (или по dirty-флагу, как `StatsCacheScheduler`). Сейчас на каждый запрос — 2 тяжёлых full-scan с DISTINCT/GROUP BY по `tbl_songs`.
- **FR-106 [P1]**: `PublicSettingsWebController.getProperty` MUST кешировать результат в `ConcurrentHashMap<String, String>` с TTL 60 сек. Сейчас на каждый запрос — `SELECT value FROM tbl_public_settings WHERE key=?`.
- **FR-107 [P2]**: `MainController.doStatBySong` (Thymeleaf `/statbysong`) MUST ограничить `limit` разумным значением (≤ 1000) или выводить в CSV-формате постранично. Сейчас `limit = 100_000` + 17 условных count(*) filter — это минутный запрос на полную таблицу `tbl_events`.
- **FR-108 [P2 / backlog]**: Администратору SHOULD быть доступна страница или эндпоинт `/api/monitor/sql-stats` (top-10 запросов к БД по латентности за последний час), построенная на данных `pg_stat_statements`. Это инструмент для подтверждения/опровержения hotspots из спеки. **Переведён в backlog** (Clarifications Session 2026-08-26): требует `shared_preload_libraries` + рестарт кластера PostgreSQL; для первого спринта используется `pg_log` + ручные `EXPLAIN ANALYZE`.
- **FR-109 [P2]**: `tbl_events` INSERT из `/api/public/zakroma` (через `doRegisterEvent`) MUST иметь возможность буферизации (batch INSERT раз в N секунд) — снижает RPS INSERT на пиках навигации по сайту.
- **FR-110 [P2]**: PostgreSQL MUST иметь индекс `idx_songs_song_author` на `tbl_songs(song_author)`, `idx_songs_id_status` на `tbl_songs(id_status)`, `idx_events_song_id` на `tbl_events(song_id)` — для ускорения всех `GROUP BY song_author`, `WHERE id_status>=6`, `GROUP BY song_id` запросов. Проверить наличие и добавить через SQL-миграцию если нет.

### Key Entities

- **Hotspot**: конкретное место в коде (file:line) с паттерном, оценкой влияния и предложенным исправлением. Не «сущность» в смысле БД, а артефакт спеки.
- **Tier (P0/P1/P2)**: приоритет исправления, основанный на (частота × латентность × кол-во записей).
- **Фоновая задача (@Scheduled)**: класс с `@Scheduled`-методом, частотой тика и набором SQL-запросов, которые он делает.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tier-1 оптимизации (P0) снижают суммарный RPS к PostgreSQL на проде как минимум на 50% при текущей пользовательской нагрузке (замер через `pg_stat_statements` до/после).
- **SC-002**: `KaraokeProcessWorker` sync-цикл при 100 sync-записях в очереди делает ≤ 10 SQL-запросов вместо 201 (1 + 2N).
- **SC-003**: `/api/public/authors-tiles` отвечает из кеша за <50 мс (warm path), холодный первый запрос — за <500 мс (вместо текущих full-scan с DISTINCT).
- **SC-004**: `StorageController.downloadFile` для MP4 100 MB потребляет <100 MB heap вместо полной загрузки файла в память.
- **SC-005**: Полное время выполнения `KaraokeDbTable.loadList(tbl_songs, без фильтра)` для всех 18 000+ записей сокращается минимум в 3 раза после рефакторинга reflection (замер на тестовом окружении с пустой БД).
- **SC-006**: 95-й перцентиль (p95) латентности публичных эндпоинтов karaoke-web (`/api/public/zakroma`, `/api/public/authors-tiles`, `/api/public/stats`) — ≤ 200 мс при пиковой нагрузке (10 RPS) после Tier-1 и Tier-2 оптимизаций.
- **SC-007**: Документация: спека + ADR (Architecture Decision Record) + обновлённые per-feature документы в `livedocs/` для тех hotspots, которые были починены в её рамках.

## Assumptions

- **Текущая нагрузка на проде**: до 50 RPS (посетители публичного сайта + админ + API). На проде развёрнут только `karaoke-web` + MinIO + PostgreSQL; `karaoke-app` — только на admin-машине (см. Constitution § Технологический стек).
- **Текущий размер БД**: ~18 000 песен (`tbl_songs`), ~125 авторов (`tbl_authors`), десятки/сотни тысяч событий в `tbl_events` (ежедневный прирост, ротация по `EventsRetentionScheduler` раз в сутки в 3 ночи). `tbl_listening_history` — апсёрт, ротации нет.
- **Текущий объём MinIO**: тысячи объектов в bucket `karaoke` (mp3, mp4, jpg, srt), один bucket для всего. На проде MinIO на отдельном хосте, доступ через nginx-proxy (см. KDoc `WebKaraokeStorageServiceImpl`).
- **JDBC-драйвер PostgreSQL**: текущая версия поддерживает `socketTimeout=30&loginTimeout=10` (см. URL в `Connection.kt`). На удалённую БД (`Connection.remote()`) — те же таймауты; sync может упираться в них при больших payload (см. KDoc `SyncTarget`).
- **Constitution § Principle II (сырой JDBC + дифф по хэшам)** сохраняется без изменений. Все оптимизации — внутри текущего стека, без JPA/Hibernate.
- **Замеры эффекта оптимизаций**: через логи `pg_log` (включён на проде по умолчанию) + ручные `EXPLAIN ANALYZE` на тестовой БД с теми же объёмами. `pg_stat_statements` НЕ включается (требует `shared_preload_libraries` + рестарт кластера, что выходит за scope этой спеки). Для SC-001/SC-006 замер делается так: (1) снимается baseline `pg_log` за 24 часа до деплоя; (2) оптимизация деплоится; (3) снимается `pg_log` за 24 часа после; (4) ручной `EXPLAIN ANALYZE` конкретных запросов сравнивается.
- **Pre-commit/CI-gate** не ломается: все правки в рамках этой спеки (будущие фичи) проходят через обычный CI/lint/compile pipeline (см. AGENTS.md, секция «Обязательная проверка после ЛЮБОГО изменения кода»).
- **Scope первого спринта** (см. раздел Scope → In Scope): все 4 P0 FR берутся как отдельные фичи (4 branch/PR). Каждая фича наследует Constitutional § VI Code Standards — самостоятельный PR, отдельный документ в `specs/242-…`, `243-…`, `244-…`, `245-…` (нумерация следующая свободная после 241).
- **FR-108 (`pg_stat_statements`) переведён в backlog** (см. Clarifications Session 2026-08-26, второй вопрос): не делается в первом спринте, остаётся как future enhancement для более точных метрик.

---

## Приложение A — Каталог hotspots (для справки и будущих фич)

> Это НЕ формальная часть спеки — это «сырой» список, который агент составил при первичном обзоре
> кода, чтобы дать пользователю точку опоры для `/speckit.clarify` (что брать в Tier 1) и будущих
> `/speckit.plan` (по каждому hotspots отдельная фича). Все file:line указаны по состоянию
> кодовой базы на момент анализа (master @ 7e4f8da5).

### A.1 Hotspots в БД (PostgreSQL)

| # | file:line | Severity | Паттерн | Текущее поведение | Предложение |
|---|-----------|----------|---------|-------------------|-------------|
| H-1 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt:259-395` | **P0** | Reflection | `loadList` для каждой записи вызывает `clazz.primaryConstructor.call(...)`, итерирует `kClass.members`, делает `property.findAnnotation<KaraokeDbTableField>()`, `property.setter.isAccessible=true`, `property.setter.call(entity, v)`. На 18k записей × 30 полей = ~540k reflection-вызовов. Также `columns()` (line 224) делает **дополнительный** SQL к `information_schema.columns` на каждый вызов с `ignoreUseInList=false`. | Заменить reflection на прямые SQL-mapper'ы (для каждой сущности — таблица соответствий column→field), ИЛИ хотя бы кешировать FieldMetadata в companion-объекте (O(1) на запись вместо O(M)). `columns()` — кешировать в `ConcurrentHashMap<tableName, List<String>>` с TTL 1 час. |
| H-2 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:998-1106` | **P0** | N+1 | Цикл sync раз в 24 сек: `listSongsSync.forEach { songSync -> Song.loadFromDbById(id, local) }` → N SELECT; `listSongsSync.map { it.id }.forEach { idToDel -> Song.deleteFromDb(id, remote, sync=true) }` → N DELETE. На 100 sync-записях = **201 SQL-запрос** за 1 цикл. **Нарушает Constitutional Principle II** («пакетно WHERE id IN, не по одной»). | Использовать `Song.loadListFromDbByIds(ids.chunked(25))` (как `SongSyncTarget`) и батч-Delete через `id = ANY(?)` (как в `KaraokeDbTable.deleteBatch`). |
| H-3 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3683-3755` | **P0** | N+1 | `getSongsCreateKaraokeAll`: `ids.forEach { id -> Song.loadFromDbById(id, local) }` — N отдельных SELECT. На 100 id = 100 запросов. | Заменить на `Song.loadListFromDbByIds(ids.chunked(25))` или добавить параметр `?ids=1,2,3,...` в существующий `loadListFromDbByIds`. |
| H-4 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:141-181` | **P1** | Hot endpoint, full-scan, no cache | `/api/public/authors-tiles` на каждый запрос: (1) `Song.loadAuthorSongCounts` (Song.kt:7171) — full scan `tbl_songs` с GROUP BY; (2) `Song.loadListAuthors` (Song.kt:7115) — `select DISTINCT song_author from tbl_songs where song_author in (select author from tbl_authors where skip=false)`. Главная страница публичного сайта и навигация по «Закромам» вызывают это часто. | Кеш в `StatsCacheScheduler`-стиле: TTL 30 сек + инвалидация через dirty-флаг (уже есть `consumeDirty()`-паттерн в `StatsCacheScheduler.refreshIfDirty`). |
| H-5 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7115-7164` | **P1** | Full scan, no index | `loadListAuthors` — `select DISTINCT song_author from tbl_songs order by song_author`. На 18k+ записей без индекса на `song_author` = sort файла. | Индекс `idx_songs_song_author` на `tbl_songs(song_author)`. + заменить `DISTINCT` на `GROUP BY song_author` (PostgreSQL умеет). |
| H-6 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7171-7211` | **P1** | Full scan, GROUP BY | `loadAuthorSongCounts` — full scan `tbl_songs` с `GROUP BY song_author` на каждом `/api/public/authors-tiles`. | Индекс + материализованное представление (refresh раз в час через `StatsCacheScheduler`). |
| H-7 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt:98-117` | **P1** | Hot endpoint, no cache | `getProperty` — `SELECT value FROM tbl_public_settings WHERE key=?` на каждый вызов. Если эндпоинт используется SPA-фронтом для boot-strap настроек — это десятки запросов в минуту на одного пользователя. | Кеш `ConcurrentHashMap<String, String>` с TTL 60 сек, инвалидация через `InternalStatsController.markDirty`-паттерн. |
| H-8 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7847-7863` | **P1** | Reflection per row | `loadFromDbById(id)` → `loadListFromDb(mapOf("id" to id))` → весь reflection-pipeline `loadList`. На каждое обращение к одной песне — reflection на 30 полей. | После H-1 (reflection-free `loadList`) — автоматически ускорится. Альтернатива: добавить `loadFromDbByIdRaw(id)` с прямым `SELECT ... WHERE id=?` без reflection. |
| H-9 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:2080-2085` | **P2** | Hot endpoint, full scan | `POST /api/songs/authors` → `Song.loadListAuthors(...)` → full scan `tbl_songs` с DISTINCT. Используется админ-таблицей Songs в `webvue3` (фильтр по автору). | Кеш (см. H-4) или индекс (см. H-5). |
| H-10 | `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:486-496` | **P2** | Heavy query, no limit | `doStatBySong` (Thymeleaf `/statbysong`) — `getStatBySong(limit = 100_000)`: 17 условных `count(*) filter (...)` + `group by song_id, song_author, song_album, song_name` + LEFT JOIN `tbl_songs` + `where e.song_id is not null and e.song_id > 0`. На реальных объёмах `tbl_events` — минутный запрос. | Снизить `limit` до 1000, добавить пагинацию в UI (или перевести на webvue3-таблицу). Альтернатива: предрассчитывать top-100 раз в час в `StatBySong.refreshCache` (как уже делается для счётчиков главной). |
| H-11 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt:449-541` | **P2** | Heavy query | `getStatBySong` — без `limit=100_000` это OK; но `StatBySong.refreshCache` (вызывается из `StatsCacheScheduler.refreshHourly` раз в час + dirty каждую минуту) пересчитывает 17 счётчиков — нужно проверить, что они идут за один запрос, а не за 17. | Code-review: убедиться, что все 17 счётчиков — в одном SQL-запросе (см. KDoc «Одна группировка по song_id + условные count(*) filter вместо 8 LEFT JOIN-подзапросов» — уже оптимизировано). |
| H-12 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt:606-628` | **P3** | Single-row delete | `delete(tableName, id)` — `DELETE FROM ... WHERE id = ?`. На каждое удаление — round-trip. Для batch-операций (sync DEL, «Удалить все одобренные») — нужен `id = ANY(?)`. См. KDoc в файле — упоминается, что batch уже есть, но в `KaraokeProcessWorker.kt:1103-1105` всё равно используется single-delete. | После H-2 batch в sync-цикле — автоматически решится. |
| H-13 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/LaneStalledCheck.kt:42-99` | **P3** | Repeated loadList | На каждый тик мониторинга (1 мин) — `KaraokeProcess.getProcessesToStart(ctx.localDb)` + (для алерта) `KaraokeProcess.loadList(args = ...)` — два SQL на лейн. На 6 лейнов = 12 запросов в минуту только от этого check. | Code-review: объединить в один запрос с GROUP BY по thread_id + условным count. |
| H-14 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:283-303` | **P3** | Hot loop | `SongEdit`: `subscriptions.forEach { a -> val s = Song.loadFromDbById(a.songId) }` — на большом списке подписок — N запросов. Используется админ-таблицей Songs. | Заменить на `Song.loadListFromDbByIds(songIds.distinct().chunked(25))`. |

### A.2 Hotspots в MinIO/S3

| # | file:line | Severity | Паттерн | Текущее поведение | Предложение |
|---|-----------|----------|---------|-------------------|-------------|
| M-1 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt:116-146` | **P0** | OOM | `downloadFile`: `val bytes = inputStream.readAllBytes()` — **весь файл грузится в heap**. Для MP4 100MB+ — риск `OutOfMemoryError` (Spring heap по умолчанию 256-512 MB). | Использовать `ResponseEntity<StreamingResponseBody>` или `Resource` (через `InputStreamResource`). Для больших файлов — поддержка `Range: bytes=X-Y` (MinIO поддерживает SigV4 Range-запросы). |
| M-2 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt:84, 127, 159, 203, 272, 290` | **P1** | HEAD per request | `fileExists()` делает HEAD-запрос к MinIO на каждый вызов. Если в цикле (например, загрузка списка файлов с проверкой каждого) — N round-trip к MinIO. На проде MinIO за nginx-proxy (MTU black-hole делает прямое соединение невозможным), overhead ×N ощутим. | Для списочных операций — единый `listObjects` с фильтром, без `fileExists` на каждый объект. Для одиночных — оставить как есть (latency-acceptable). |
| M-3 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt:176-190, 297-308` | **P2** | No pagination | `listFiles` / `listFilesInfo` — возвращают ВСЕ файлы в бакете без пагинации. На больших бакетах (тысячи объектов) — тяжёлый ответ + сериализация. | Добавить `?prefix=...&recursive=true&limit=N` (MinIO поддерживает), возвращать `{files: [...], nextContinuationToken: "..."}`. |
| M-4 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageServiceImpl.kt` (файл не показан в codegraph, см. kdoc) | **P3** | Connection reuse | См. KDoc `WebKaraokeStorageServiceImpl`: на проде MinIO через nginx-proxy. Убедиться, что `KaraokeStorageServiceImpl` для admin-приложения использует HTTP keepalive connection pool (Apache HttpClient с `setKeepAliveStrategy`), а не создаёт новое соединение на каждый запрос. | Code-review: добавить http-keepalive-pool. Замер RTT до MinIO до/после. |
| M-5 | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageService.kt:104-114` | **P3** | Двойной `fileIsActual` | `fileIsActual(bucket, fileName, pathToFileOnDisk)` и `fileIsActual(bucket, fileName, storageFileInfo)` — обе реализации вероятно делают HEAD или stat; если обе используются в одной операции (сравнение local vs remote) — 2 запроса вместо 1. | Code-review: объединить в один stat + локальное сравнение etag/mtime/size. |

### A.3 Фоновые задачи (`@Scheduled`) и их нагрузка

| Класс | Файл:строка | Частота | Что делает с БД | Совокупный RPS к БД (оценка) |
|-------|-------------|---------|-----------------|------------------------------|
| `MonitoringService` | `karaoke-app/.../monitor/MonitoringService.kt:40` | **1 мин** | Запускает 7 monitor checks (`RenderQueueStalledCheck`, `LaneStalledCheck`, `StemJobsStuckCheck`, `SubmittedAssignmentsCheck`, `TelegramPollingDisabledCheck`, `UnreadChatMessagesCheck`, `ProdContainerCheck`). Каждый check делает 1–N SQL. | 5–15 SQL/мин |
| `AutoOneClickSyncScheduler` | `karaoke-app/.../services/AutoOneClickSyncScheduler.kt:156` | **1 мин** | `SyncRegistry.tick()` — для каждой таблицы `getListHashes` LOCAL + REMOTE + diff + apply. На таблице `tbl_songs` 18k записей — два SELECT по 18k записей каждую минуту. См. `specs/235-auto-sync-3h` (но фактически тикает 1 мин). | 2 large SELECT + N×4 UPDATE/INSERT в минуту (на большие таблицы). Это **самый нагруженный** scheduler. |
| `TelegramAutoPublishScheduler` | `karaoke-app/.../services/TelegramAutoPublishScheduler.kt:53` | **1 мин** | Проверка песен в автопубликации (SELECT + UPDATE state). | 2–5 SQL/мин |
| `VkAutoPublishScheduler` | `karaoke-app/.../services/VkAutoPublishScheduler.kt:46` | **1 мин** | То же для VK. | 2–5 SQL/мин |
| `SseNotificationService` | `karaoke-app/.../services/SseNotificationService.kt:165` | **15 сек** | См. KDoc — требует `@EnableScheduling`. Вероятно чистка dead-SSE-клиентов. | 1–2 SQL / 15 сек |
| `PremiumAutoPublishScheduler` | `karaoke-app/.../services/PremiumAutoPublishScheduler.kt:70` | **30 сек** | Аналогично для Premium-публикаций. | 1–3 SQL / 30 сек |
| `VkIdTokenRefreshScheduler` | `karaoke-app/.../services/VkIdTokenRefreshScheduler.kt:44` | **cron `0 0 * * * *`** | Обновление VK ID token — внешний API, не БД. | 0 SQL/час |
| `SponsrSyncScheduler` | `karaoke-app/.../services/SponsrSyncScheduler.kt:25` | **12 часов** | Скрейпинг Sponsr + sync подписчиков. | 1 burst |
| `StemJobPollScheduler` (метод 1) | `karaoke-app/.../StemJobPollScheduler.kt:41` | **45 сек** | Опрос заданий стемов с прод-сайта (`SELECT ... FROM tbl_stem_jobs`). | 1–2 SQL / 45 сек |
| `StemJobPollScheduler` (метод 2) | `karaoke-app/.../StemJobPollScheduler.kt:192` | **5 мин** | Cleanup старых заданий. | 1–2 SQL / 5 мин |
| `StatsCacheScheduler.refreshHourly` | `karaoke-web/.../services/StatsCacheScheduler.kt:55` | **cron `0 0 * * * *`** | Полный пересчёт `StatBySong` — 17 условных count(*). | 1 large SQL/час |
| `StatsCacheScheduler.refreshIfDirty` | `karaoke-web/.../services/StatsCacheScheduler.kt:68` | **1 мин** | Почти no-op (consumeDirty), пересчёт только если флаг взведён. | 0–1 SQL/мин |
| `ShareLinkSweeper` | `karaoke-web/.../services/ShareLinkSweeper.kt:28` | **1 мин** | Удаление протухших share-ссылок. | 0–1 SQL/мин |
| `StemJobTempCleanupScheduler` | `karaoke-web/.../services/StemJobTempCleanupScheduler.kt:24` | **30 мин** | Cleanup temp-файлов стемов на диске. | 0–1 SQL/30 мин |
| `SongReleaseAnnouncementScheduler` | `karaoke-web/.../services/SongReleaseAnnouncementScheduler.kt:33` | **5 мин** | Анонсы новых песен. | 1–2 SQL/5 мин |
| `EventsRetentionScheduler` | `karaoke-web/.../services/EventsRetentionScheduler.kt:47` | **cron `0 0 3 * * *`** (3 ночи) | Ротация `tbl_events`. | 1 large DELETE/сутки |
| `SubscriptionRenewalScheduler` | `karaoke-web/.../services/SubscriptionRenewalScheduler.kt:49` | **cron `0 0 3 * * *`** (3 ночи) | Продление подписок. | burst |

**Совокупная фоновая нагрузка на karaoke-app (admin-машина)**: 10–30 SQL/мин постоянно. На проде (`karaoke-web` без `karaoke-app` нет этих scheduler'ов, кроме того, что в `karaoke-web`).

**Совокупная фоновая нагрузка на karaoke-web (prod)**: 5–10 SQL/мин постоянно + 1 large SQL/час (StatBySong refresh).

### A.4 Уже сделанные оптимизации (для справки — не дублировать)

- **specs/087-fix-shared-db-connection**: один JDBC Connection на поток (ThreadLocal) вместо общего — решило `SocketTimeoutException`.
- **specs/186-zakroma-songs-fast-load**: ускорение загрузки песен в Закромах (без точного знания правок, см. git log 53a0645e).
- **specs/234-db-sync-connection-leak**: фикс утечки JDBC-соединений в `Connection.local()/remote()/virtual()` через `by lazy(SYNCHRONIZED)`. До этого 36 новых инстансов на каждый клик «Синхронизация БД в 1 клик» × N вызовов = 100+ физических каналов.
- **specs/235-auto-sync-3h**: автоматическая синхронизация LOCAL↔SERVER (фактически тикает раз в 1 мин).
- **specs/236-fix-karaoke-connection-self-healing**: сброс ThreadLocal при неудачной попытке переподключения.
- **specs/187-site-traffic-anomaly-investigation**: расследование аномалии трафика, прямой URL на MinIO через nginx в `AuthorTilePublicDto` (минуя Spring).
- **specs/239-zakroma-author-songs-batch-render** (Pass 239): freeze-баг цензурирования (2500 SQL-запросов per-load на крупных авторах) — устранён через per-row readiness → статусные флаги в DTO, **специально отмечено в AGENTS.md как обязательный контекст для будущих оптимизаций Закромов**.

### A.5 План оптимизации (приоритезированный)

#### Tier 1 — P0 (делать в первую очередь)

1. **FR-101 / H-2**: Батч в sync-цикле `KaraokeProcessWorker` — снижает 1+2N SQL до ~5 SQL на 100 sync-записей.
2. **FR-102 / H-1**: Schema-cache в `KaraokeDbTable` — убирает дополнительный SELECT к `information_schema.columns` на каждый `loadList`.
3. **FR-103 / H-3**: Батч в `getSongsCreateKaraokeAll` — снижает N SQL до 1 SQL.
4. **FR-104 / M-1**: Streaming для `StorageController.downloadFile` — убирает OOM-риск для больших файлов.

#### Tier 2 — P1 (важно, в следующий спринт)

5. **FR-105 / H-4 + H-6**: Кеш для `/api/public/authors-tiles` (counts + authors).
6. **FR-106 / H-7**: Кеш для `getProperty`.
7. **H-5 + H-110**: Индексы `idx_songs_song_author`, `idx_songs_id_status`, `idx_events_song_id` через SQL-миграцию.
8. **H-8**: После H-1 — автоматическое ускорение `loadFromDbById`.
9. **M-2**: HEAD per request → `listObjects` с фильтром для списочных операций.

#### Tier 3 — P2 (желательно, в backlog)

10. **FR-107 / H-10**: Ограничить `limit` в Thymeleaf `/statbysong`.
11. **FR-108**: Инструмент наблюдения `/api/monitor/sql-stats` через `pg_stat_statements`.
12. **FR-109**: Batch INSERT для `tbl_events` из `doRegisterEvent`.
13. **H-13**: Объединить 2 SELECT в `LaneStalledCheck` в один с GROUP BY.
14. **H-14**: Заменить `forEach { loadFromDbById }` в `SongEdit` на `loadListFromDbByIds`.
15. **M-3**: Пагинация в `listFiles` / `listFilesInfo`.
16. **M-4**: HTTP keepalive pool в `KaraokeStorageServiceImpl`.
17. **M-5**: Code-review `fileIsActual` на дублирование HEAD/stat.
18. **H-9, H-11, H-12, M-5**: Code-review мелких hotspots.

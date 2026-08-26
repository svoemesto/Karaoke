# Feature Specification: Батч-загрузка песен в getSongsCreateKaraokeAll

**Feature Branch**: `244-songs-createkaraokeall-batch`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-103
**Input**: User description (через parent спеку): "Батч-загрузка песен в `ApiController.getSongsCreateKaraokeAll` — устранить N+1 (N отдельных `Song.loadFromDbById` в цикле), заменить на пакетный `Song.loadListFromDbByIds(ids.chunked(N))`."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Быстрый запуск массового создания караоке (Priority: P1)

Администратор через webvue3 (admin SPA) выбирает 100 песен в таблице Songs и нажимает «Создать караоке для всех» (`POST /api/songs/createkaraokeall`). Операция завершается за ≤ 3 сек (cold path) без подвисания UI. Сейчас при 100 песнях делается 100 отдельных `SELECT ... WHERE id=?` через reflection — UI замирает на 5–15 сек.

**Why this priority**: нарушает Constitution § II (батч вместо цикла). Это типовой admin-сценарий «создать караоке для всех песен альбома» — в проде не критично для пользователей, но мешает админу. Также анти-паттерн N+1 в самом очевидном месте кода — это референсный пример для code review.

**Independent Test**: при 100 ID в `songsIds` запрос `POST /api/songs/createkaraokeall` делает ≤ 5 SQL-запросов вместо 100.

**Acceptance Scenarios**:
1. **Given** 100 ID в параметре `songsIds`, **When** админ вызывает `POST /api/songs/createkaraokeall`, **Then** SQL-запросов к БД для загрузки песен = `ceil(100 / 25) = 4` (при `chunk = 25`). Текущее — 100.
2. **Given** 1000 ID, **When** админ вызывает тот же endpoint, **Then** SQL-запросов = 40 (chunk=25). Текущее — 1000.
3. **Given** 0 ID (пустой `songsIds`), **When** endpoint вызван, **Then** SQL = 0 (early return), endpoint возвращает `result = false` без SSE-уведомления (или с warning-уведомлением).
4. **Given** ID, который не существует в БД (например, был удалён), **When** батч-загрузка выполняется, **Then** `song` будет null для этого ID, и `KaraokeProcess.createProcess` НЕ вызывается (сохраняется текущее поведение `song?.let { ... }`).

---

### User Story 2 — Читаемость admin-кода (Priority: P2)

Разработчик, открывший `ApiController.getSongsCreateKaraokeAll`, видит батч-логику (1 SQL + per-song side-effect), а не N+1 в цикле. Это упрощает code review и снижает bus-factor.

**Why this priority**: это Tier-1 фикс не только ради производительности, но и как сигнал «вот как надо писать в этой кодовой базе». Аналогичный код есть в `SongEditorController.kt:283-303`, `Utils.kt:125,149,181`, и `MainController.kt` (десятки вызовов `loadFromDbById` подряд) — после этой фичи они тоже могут быть отрефакторены в отдельных задачах.

**Independent Test**: метод `getSongsCreateKaraokeAll` имеет ≤ 80 строк (сейчас ~95) и использует helper `loadSongsBatch(songsIds, database)` для инкапсуляции батч-логики.

**Acceptance Scenarios**:
1. **Given** исходный код метода, **When** разработчик смотрит на него, **Then** видит 1 вызов батч-загрузки вместо N вызовов в цикле.

---

### Edge Cases

- **Что если `songsIds` содержит дубликаты?** Текущий код: `songsIds.split(";").map { it }.filter { it != "" }.map { it.toLong() }` — без `distinct()`. Это значит, что при дубликатах делается N одинаковых `loadFromDbById` (ещё хуже: N одинаковых INSERT в `KaraokeProcess`). Батч-логика может делать `distinct()` без потери семантики.
- **Что если `priorLyrics`/`priorKaraoke`/etc. — пустые строки?** Текущая проверка `priorLyrics != "" && priorLyrics != null` уже есть. Сохраняется.
- **Что если для одной песни указано создать несколько типов karaoke (lyrics, karaoke, chords, melody, demo)?** Текущий код вызывает `KaraokeProcess.createProcess` несколько раз подряд (до 5 раз). Это нормально — каждая песня → несколько процессов. Батч-логика на это не влияет.
- **Что если одна из песен падает на `KaraokeProcess.createProcess` (исключение)?** Текущий код: `song?.let { ... }` — если `createProcess` бросает, то исключение пробросится наверх. Сохраняется текущее поведение (fail-fast).
- **Что если chunk_size выбран слишком большим?** Те же риски, что в FR-101 (sync-batch): `socketTimeout=30` на remote + память на большие ResultSet'ы. `chunk = 25` — проверенное значение.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `ApiController.getSongsCreateKaraokeAll` (строки ~3664–3778) MUST заменить `ids.forEach { id -> Song.loadFromDbById(id = id, database = WORKING_DATABASE, storageService, storageApiClient) }` на пакетный `loadSongsBatch(ids, WORKING_DATABASE, storageService, storageApiClient)`, где `loadSongsBatch` использует `Song.loadListFromDbByIds(ids.chunked(CHUNK_SIZE), ...)`.
- **FR-002**: `CHUNK_SIZE` MUST быть равен `SongSyncTarget.rowChunkSize = 25` (или другому разумному значению, обсуждаемому в `/speckit.plan`). Аргументация: тяжёлые строки Song (source_text, result_text, source_markers) → chunk не больше 25, чтобы не упираться в heap/socketTimeout.
- **FR-003**: Результат `loadSongsBatch` — `Map<Long, Song>` (id → Song), чтобы дальнейший код мог найти песню по ID. Текущий код использует `song?.let { ... }` per ID — сохраняется с использованием `map[id]?.let { ... }`.
- **FR-004**: Сохраняется текущий side-effect: для каждой найденной песни вызывается `KaraokeProcess.createProcess(...)` (от 1 до 5 раз в зависимости от `priorLyrics`/`priorKaraoke`/`priorChords`/`priorMelody`/`priorDemo`). Это per-song операция, батчинг НЕ применяется.
- **FR-005**: Должна быть возможность сделать `distinct()` на `ids` (убрать дубликаты) для предотвращения дублирующих INSERT в `KaraokeProcess`. Текущее поведение — без `distinct()`. Это улучшение, согласовать с пользователем в `/speckit.clarify` или принять по умолчанию.
- **FR-006**: Endpoint MUST сохранять текущее SSE-уведомление (`SNS.send(SseNotification.message(...))`) с тем же текстом и поведением (success / warning).
- **FR-007**: Endpoint MUST сохранять текущий контракт: `@PostMapping("/songs/createkaraokeall")` с параметрами `songsIds`, `priorLyrics`, `priorKaraoke`, `priorChords`, `priorMelody`, `priorDemo`, `threadId` (см. ApiController.kt:3664-3673).

### Key Entities

- **`Song.loadListFromDbByIds`** (уже существует, см. parent спека, A.4) — batch-загрузка по `WHERE id IN (...)`, чанками. Используется в `SongSyncTarget.loadByIds`.
- **Новый helper `loadSongsBatch(ids: List<Long>, database, storageService, storageApiClient): Map<Long, Song>`** — приватная функция в `ApiController` (или utility в `Song.kt`), инкапсулирующая батч-логику. Возвращает `Map` для O(1) lookup per ID.
- **`KaraokeProcess.createProcess`** (без изменений) — per-song side-effect, остаётся как есть.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При 100 ID в `songsIds` endpoint делает ≤ 5 SQL-запросов для загрузки песен. Текущее — 100.
- **SC-002**: При 1000 ID — ≤ 41 SQL-запрос (chunk=25). Текущее — 1000.
- **SC-003**: End-to-end latency endpoint'а при 100 ID: ≤ 3 сек (cold path, вкл. side-effect создания процессов). Текущее — 5–15 сек.
- **SC-004**: Code metrics: метод `getSongsCreateKaraokeAll` имеет ≤ 80 строк (было ~95). Helper `loadSongsBatch` — ≤ 15 строк.
- **SC-005**: Количество `KaraokeProcess.createProcess` вызовов остаётся равным текущему (1–5 на песню, в зависимости от `priorXxx` параметров). Regression: для 100 песен с `priorLyrics="10"` создаётся 100 процессов `RENDER_MP4_LYRICS`.
- **SC-006**: `pg_log` за 24 часа до/после деплоя показывает снижение SQL-запросов к `tbl_songs` от admin-операций на ≥ 50% в часы активного использования (когда админ массово создаёт караоке).
- **SC-007**: Дублирующиеся ID в `songsIds` НЕ приводят к дублирующим INSERT в `tbl_processes` (если FR-005 про `distinct()` принят).

## Assumptions

- **`Song.loadListFromDbByIds`** уже существует и использует `chunked(rowChunkSize)` с `WHERE id IN (...)` (см. parent спека, A.4, `SongSyncTarget.loadByIds`).
- **`chunk = 25`** — оптимальное значение для Song (тяжёлые строки с text/markers/base64). Альтернативы (50, 100) — больше риск OOM/socketTimeout, проверено в `SongSyncTarget`.
- **ADMIN-сценарий**, не пользовательский. Endpoint `POST /api/songs/createkaraokeall` доступен только в `karaoke-app` (admin-машина), не в `karaoke-web` (прод). На проде это НЕ влияет на публичных пользователей — это снижает риск и приоритет (админ сам может подождать).
- **Side-effect `KaraokeProcess.createProcess`** — отдельный SQL INSERT на каждый процесс. Это тоже N запросов (но per-process, не per-id), и это нормально для архитектуры (каждый процесс — атомарная запись в очередь). Батчинг INSERT в `tbl_processes` — отдельная задача, не входит в scope.
- **SSE-уведомление** — отправляется ОДИН раз в конце метода (через `SNS.send`), не per-id. Сохраняется.
- **Pre-commit/CI-gate** не ломается: правки в `ApiController.kt` проходят через обычный CI/lint/compile pipeline.
- **Замер эффекта**: pre/post `pg_log` (24 часа до/после деплоя) — согласовано в parent спеке (Clarifications Session 2026-08-26).

## Out of Scope (явно НЕ делается в этой фиче)

- Батчинг INSERT в `tbl_processes` для `KaraokeProcess.createProcess` (parent спека, Tier-3). Per-process INSERT остаётся.
- Изменение API-контракта endpoint'а (FR-007).
- Рефакторинг других мест в `ApiController.kt`, где есть `forEach { loadFromDbById }` (MainController.kt, NewsTemplateController.kt, ExportAlignmentDataset.kt — десятки мест). Это отдельные фичи.
- Замена `Song.loadFromDbById` per-id в других admin-контроллерах (parent спека, H-14 в `SongEdit`).
- Изменение side-effect `KaraokeProcess.createProcess`.
- Изменение логики `priorLyrics`/`priorKaraoke`/etc.
- Tier-2/Tier-3 оптимизации из parent спеки — отдельные фичи.
- Изменение стека доступа к БД (Constitution § II «Сырой JDBC»).
- Включение `pg_stat_statements` — перенесено в backlog.

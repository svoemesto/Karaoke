# Feature Specification: Batch INSERT для tbl_events (FR-109)

**Feature Branch**: `274-events-batch-insert`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-3 / FR-109
**Input**: User description (через parent спеку FR-109): "`tbl_events` INSERT из `/api/public/zakroma` (через `doRegisterEvent`) MUST иметь возможность буферизации (batch INSERT раз в N секунд) — снижает RPS INSERT на пиках навигации по сайту."

## Clarifications

### Session 2026-08-26

- **Q**: Какой вариант реализации выбрать?
  - A) In-memory буфер с `@Scheduled` flush (потеря данных при крэше)
  - B) Persistent queue через `LISTEN/NOTIFY` (сложно)
  - C) DB-side буфер через `TEMPORARY TABLE` (overkill)
  - **A — In-memory буфер** (простота + допустимая потеря при крэше).
- **Q**: Какой kill-switch default — `true` (включен сразу) или `false` (выключен, opt-in)?
  **A**: B — **default `false` (opt-in)**. Безопасный rollout — на текущем проде INSERT как раньше,
  администратор может включить батчинг после наблюдения baseline. Это Tier-3 P2, не критично для прода.
- **Q**: Какой flush trigger?
  - A) Только `@Scheduled` каждые N секунд (5 сек)
  - B) Только размер буфера (M событий)
  - C) **Оба**: `@Scheduled` ИЛИ переполнение → flush немедленно
  **A**: C — **оба триггера**. Scheduled = обычный случай. Переполнение = backpressure для пиков.
- **Q**: Multi-row INSERT или JDBC `addBatch()` + `executeBatch()`?
  **A**: B — **`addBatch()` + `executeBatch()`**. Multi-row INSERT требует одинакового набора колонок
  во всех событиях, что не так (разные `eventType` имеют разные поля). JDBC `addBatch()` гибче.
- **Q**: Что делать при переполнении буфера (backpressure)?
  **A**: A — **синхронный INSERT** (fallback). При буфере > MAX_BUFFER_SIZE — делаем sync INSERT
  (как раньше), чтобы не терять события. Это редкий случай (только при экстремальной нагрузке).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Снижение RPS INSERT на пиках (Priority: P2)

На пике нагрузки (пользователь листает «Закрома», каждая песня триггерит события на показ,
player, и т.д. — десятки INSERT/сек на одного клиента) — буферизация в `EventsBuffer` + flush
каждые 5 секунд через JDBC `addBatch()` + `executeBatch()` снижает фактический RPS INSERT
к PostgreSQL. Например, 30 INSERT/сек в пике → 1 batch INSERT раз в 5 сек с 150 событиями
(вместо 150 отдельных INSERT).

**Why this priority**: Tier-3 P2 (FR-109 в parent спеке 241) — на текущем проде нагрузка
небольшая (SamplingFilter + DedupCache уже уменьшают поток), но при росте трафика это станет
узким местом. Также batch INSERT экономит CPU на стороне PostgreSQL (меньше парсинга SQL).

**Independent Test**: запустить load test (curl 50 параллельных запросов на `/registerevent`),
замерить SQL в `pg_log` — должно быть **1 batch INSERT (multi-statement)** вместо 50 одиночных.

**Acceptance Scenarios**:
1. **Given** батчинг включён (`karaoke.web.events.batch-enabled = true`), **When** 100 событий
   приходят за 5 секунд, **Then** они буферизуются в `EventsBuffer`, flush через `@Scheduled`
   выполняет **1 JDBC batch INSERT** (multi-statement) с 100 строками. SQL в `pg_log`:
   1 строка (а не 100).
2. **Given** батчинг включён, буфер переполнен (>MAX_BUFFER_SIZE), **When** приходит ещё одно
   событие, **Then** срабатывает `flushImmediately()` — синхронный batch INSERT текущего буфера
   (backpressure). Новое событие может попасть в следующий batch.
3. **Given** батчинг выключен (kill-switch false), **When** событие приходит, **Then**
   `enqueue()` сразу делает sync INSERT (как раньше). Поведение не меняется.
4. **Given** batch INSERT упал с SQL exception, **When** flush выполняется, **Then** буфер
   очищается (fail-open: потеря событий допустима для логирования), ошибка логируется через
   SLF4J (как в текущем `insertEvent`).

### User Story 2 — Корректность семантики (Priority: P2)

Батчинг НЕ должен менять семантику `tbl_events`: те же поля, те же значения, те же SQL
constraints (PRIMARY KEY, IDENTITY sequence). На текущем коде используется
`OVERRIDING SYSTEM VALUE` — нужно сохранить совместимость (или явно убрать с пометкой в KDoc).

**Why this priority**: Tier-3 — даже Tier-3 не должен ломать существующие данные или контракты.
Любое отклонение должно быть задокументировано.

**Independent Test**: сравнить одну запись в `tbl_events` до и после включения батчинга —
должны совпадать все поля (event_type, link_type, link_name, song_id, song_version,
client_ip, anon_id, site_user_id, user_agent, referer, last_update).

**Acceptance Scenarios**:
1. **Given** одиночный INSERT работает корректно, **When** батчинг включён, **Then** запись
   в `tbl_events` идентична одиночному INSERT (все колонки, все значения).
2. **Given** `OVERRIDING SYSTEM VALUE` используется в одиночном INSERT, **When** переходим
   на batch, **Then** сохраняем тот же синтаксис (или убираем с пометкой: «IDENTITY column,
   no need to OVERRIDING»).
3. **Given** SQL escaping через `rightFileName()` (sanitize для filename), **When** батчинг
   применяется к N событиям, **Then** каждое событие escape'ится индивидуально (не теряется
   per-event escaping).

### User Story 3 — Наблюдаемость (Priority: P3)

Администратор может видеть статистику батчинга: размер буфера, число flushes, число событий
в flush, время последнего flush. Kill-switch через `karaoke.web.events.batch-enabled`.

**Why this priority**: observability — стандарт для Tier-3 фичи (не блокер, но полезно при
отладке). Минимальная реализация: логирование через SLF4J при каждом flush + kill-switch
через `KaraokeProperties`.

**Independent Test**: включить батчинг, открыть docker logs → видеть `[EventsBuffer] flushed
N events in X ms` каждые 5 секунд. Установить `batch-enabled = false` → логи исчезают.

**Acceptance Scenarios**:
1. **Given** батчинг включён, **When** flush выполняется, **Then** в логах появляется строка
   `[EventsBuffer] flushed N events in X ms` (N ≥ 1, X < 100 ms для типичной нагрузки).
2. **Given** kill-switch выставлен в false, **When** события приходят, **Then** flush
   НЕ выполняется (буферизация отключена, sync INSERT).
3. **Given** администратор хочет изменить интервал flush, **When** он меняет
   `karaoke.web.events.batch-flush-interval-ms`, **Then** новый интервал применяется после
   рестарта контейнера (Spring `@Scheduled` считывает значение при init).

## Edge Cases

- **Что если БД недоступна в момент flush**? Буфер очищается (fail-open: потеря событий допустима).
  Ошибка логируется через SLF4J. Sync INSERT на следующем вызове `enqueue()` тоже упадёт — как раньше.
- **Что если процесс крэшится между `enqueue()` и flush**? Потеря буфера (in-memory). Допустимо
  для логирования событий (не транзакционные данные). Если потеря критична — нужна persistent queue
  (не в скоупе Tier-3).
- **Что если все события имеют разные наборы полей** (например, `event_type=CLICK_TO_LINK`
  vs `event_type=PLAY`)? JDBC `addBatch()` поддерживает разные SQL statements в одном batch
  через `PreparedStatement` — каждый event добавляется отдельно. Это работает в PostgreSQL.
- **Что если `enqueue()` вызывается параллельно из разных HTTP-запросов**? `ConcurrentLinkedQueue`
  thread-safe. `flushing: AtomicBoolean` предотвращает двойной flush.
- **Что если `rightFileName()` escape'ит что-то неожиданное для batch**? Каждое событие escape'ится
  независимо в `enqueue()` ПЕРЕД добавлением в буфер — не зависит от flush.
- **Что если `last_update = now()` используется в SQL** (default value)? Работает как раньше —
  PostgreSQL вычисляет `now()` для каждой строки в batch.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Новый сервис `EventsBuffer` (`@Service`) MUST буферизовать события `tbl_events`
  в `ConcurrentLinkedQueue<PreparedStatement>` или `ConcurrentLinkedQueue<EventRecord>`.
- **FR-002**: `EventsBuffer.enqueue(eventRecord)` MUST добавлять событие в очередь. Если
  `karaoke.web.events.batch-enabled = false` (или `KaraokeProperties.getBoolean` упал с
  default `false`) — сразу выполнять sync INSERT (как сейчас в `insertEvent`).
- **FR-003**: `EventsBuffer.flush()` MUST выполняться через `@Scheduled(fixedDelayString =
  "${karaoke.web.events.batch-flush-interval-ms:5000}")` (дефолт 5 сек). Использует JDBC
  `addBatch()` + `executeBatch()` для batch INSERT.
- **FR-004**: При переполнении буфера (> `MAX_BUFFER_SIZE = 500`) MUST срабатывать
  `flushImmediately()` (backpressure) — синхронный batch INSERT текущего буфера.
- **FR-005**: При ошибке batch INSERT MUST буфер очищаться (fail-open), ошибка логируется
  через SLF4J (как в текущем `insertEvent`).
- **FR-006**: `MainController.doRegisterEvent.insertEvent(...)` MUST быть изменён — вместо прямого
  `ps.executeUpdate()` использовать `eventsBuffer.enqueue(eventRecord)`. SQL-формирование
  остаётся в `insertEvent` (escape через `rightFileName()`, санитизация referer).
- **FR-007**: KDoc MUST быть добавлен на `EventsBuffer` + изменённый `insertEvent` со ссылками
  на FR-109 parent спеки 241 (Constitution § VI Code Standards, FR-006).

### Key Entities

- **EventRecord**: data class с полями `(fieldsValues: List<Pair<String, Any>>, eventType: String,
  clientIp: String?, userAgent: String?, anonId: String?, siteUserId: Long)`. Immutable.
- **EventsBuffer**: `@Service` с `@Scheduled` flush, `ConcurrentLinkedQueue<EventRecord>`,
  `AtomicBoolean flushing`, `MAX_BUFFER_SIZE`, `FLUSH_INTERVAL_MS`.

### Configuration

- `karaoke.web.events.batch-enabled` (Boolean, default `false`): kill-switch.
- `karaoke.web.events.batch-flush-interval-ms` (Long, default `5000`): интервал flush.
- `karaoke.web.events.batch-max-buffer-size` (Int, default `500`): backpressure trigger.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При батчинге включённом и нагрузке 50 событий/5 сек — в `pg_log` **1 batch INSERT**
  (multi-statement) вместо 50 одиночных INSERT.
- **SC-002**: RPS INSERT к `tbl_events` снижается на ≥80% при типичной нагрузке (SamplingFilter
  + DedupCache уже уменьшают поток; батчинг даёт дополнительный множитель).
- **SC-003**: Семантика записей НЕ меняется (все поля, все значения идентичны одиночному INSERT).
- **SC-004**: KDoc coverage 100% (Constitution § VI FR-006).
- **SC-005**: ktlint PASS, все 7 CI gates PASS.

## Assumptions

- **Нагрузка**: текущая нагрузка на проде ~30 req/min после SamplingFilter + DedupCache.
  Батчинг — opt-in, не меняет поведение по умолчанию.
- **SamplingFilter и DedupCache уже уменьшают поток** (см. `archive/docs/features/site-traffic-resilience.md`).
  Батчинг — дополнительный уровень оптимизации, не замена.
- **JDBC `addBatch()` поддерживает разные SQL** через `PreparedStatement`: PostgreSQL JDBC driver
  обрабатывает multi-statement batches через `reWriteBatchedInserts=true` (оптимизация драйвера).
- **SamplingFilter уже подключен** — события в `EventsBuffer` уже отфильтрованы (sampling 1/N
  + dedup 30 сек TTL).
- **MySQL/PostgreSQL**: проект на PostgreSQL (см. `Connection.kt`). JDBC batch — стандарт.
- **In-memory потеря при крэше**: допустимо для логирования событий (не критичные данные).
  Если потеря критична — нужен persistent queue (отдельная будущая фича, не в скоупе).
- **`WORKING_DATABASE` connection pooling**: уже есть (см. FR-087). Наш batch INSERT
  использует одно соединение на flush — OK.

## Out of Scope

- Persistent queue (RabbitMQ, Redis Streams) — слишком сложно для Tier-3.
- Multi-row INSERT через один SQL — требует одинакового набора колонок.
- Async flush через `LISTEN/NOTIFY` или webhook — overengineering.
- Изменение `SamplingFilter` / `DedupCache` — они уже работают.
- Мониторинг/метрики (Prometheus) — отдельная будущая фича.

## Reference

- Parent спека: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md), FR-109, H-12.
- Current implementation: [`MainController.kt:141-173`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt).
- Schema: [`deploy/karaoke-db/03_events.sql`](../../deploy/karaoke-db/03_events.sql).
- Sister services (паттерн): [`DedupCache.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DedupCache.kt).
- SamplingFilter: [`services/SamplingFilter.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingFilter.kt).
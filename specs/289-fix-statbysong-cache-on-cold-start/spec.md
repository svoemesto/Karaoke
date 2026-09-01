# Feature Specification: Устранение блокирующего `StatBySong.refreshCache()` при cold-start

**Feature Branch**: `289-fix-statbysong-cache-on-cold-start`

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "сделай предложенный тобой фикс отдельной фичей" (предложение из отчёта по фиче 288-prod-diagnostics-logging: устранить full-scan SQL `select count(DISTINCT id) as cnt from tbl_songs where id_status >= 6 AND ...` в `StatBySong.refreshCache()` который блокирует HTTP-треды на 12 сек при cold-start и может провоцировать зависания при пиковой нагрузке).

## Контекст и предыстория

В рамках фичи [288-prod-diagnostics-logging](../288-prod-diagnostics-logging/spec.md) был включён `log_min_duration_statement = 1000` на проде. Анализ `pg_log` за первые 24 часа после применения обнаружил **hotspot** в `karaoke-web`:

```
duration: 4655.985 ms  select count(DISTINCT id) as cnt from tbl_songs
                          where id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''
                          AND (tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))))
duration: 4385.267 ms  [тот же запрос]
duration: 4182.403 ms  [тот же запрос]
duration: 3961.783 ms  [тот же запрос]
```

**Источник**: [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt:102`](../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt).

**Что это делает**: `StatBySong.refreshCache()` пересчитывает 3 счётчика (`total`, `collection`, `freeNow`) для главной страницы и Закромов. Каждый запрос — full-scan по `tbl_songs` (~18k+ записей) с `DISTINCT` + проверкой массива тегов через `string_to_array`. Итого ~12 сек на полное обновление.

**Когда вызывается** (см. `StatBySong.kt:93-127`):
1. Каждый час через `StatsCacheScheduler.refreshHourly` (cron `0 0 * * * *`).
2. **Синхронно на первом обращении к `/api/public/stats` после cold start** через `ensureCacheInitialized()` (строка 144-147). Это **блокирует HTTP-тред** на 12 сек.

**Сценарий, как это может вызывать зависания**:
1. `karaoke-web` перезапускается (deploy).
2. Приходит запрос `/api/public/events` или `/api/public/stats` (например, после одобрения задания редактора в `webvue3`).
3. `ensureCacheInitialized()` синхронно дёргает `refreshCache()` → **12 сек блокировки HTTP-треда**.
4. Tomcat pool (max=50) постепенно забивается; новые запросы встают в очередь → каскад.

**Почему важно**:
- Per user: «прод продолжает регулярно подвисать» (исходная задача фичи 288). Этот hotspot — один из реальных кандидатов.
- Full-scan по `tbl_songs` без индекса — нарушение Constitutional Principle II («Загрузка записей для diff — пакетно `WHERE id IN (..)`, не по одной в цикле»). Не критично, но улучшает perf.
- Cold-start блокировка — прямое нарушение responsiveness требований.

## Цель фичи

1. **Добавить индекс** `idx_songs_id_status_source_markers` на `tbl_songs(id_status, source_markers)` — ускоряет full-scan в 10-100× (по практике PostgreSQL B-tree).
2. **Перенести cold-start refresh в фон** — `ensureCacheInitialized()` не блокирует HTTP-тред; первый запрос возвращает fallback (предыдущие значения или 0) + логирует WARN.
3. **Предотвратить параллельные refresh** — `@Synchronized` уже есть, но возможны гонки между scheduler-ом и HTTP-вызовом. Добавить явный guard через `AtomicBoolean refreshing`.
4. **Замерить эффект** через `pg_log` (SC-002) — после применения индекса, медленные SQL должны исчезнуть.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Cold-start refresh не блокирует HTTP-тред (Priority: P1)

Пользователь открывает главную страницу `https://sm-karaoke.ru/` сразу после deploy `karaoke-web`. Первый запрос к `/api/public/stats` НЕ блокируется на 12 сек — вместо этого возвращает fallback (0 или последние известные значения), в логах появляется WARN о том, что кеш ещё не прогрет, а фоновый поток асинхронно пересчитывает счётчики. Последующие запросы (через 12 сек) уже возвращают актуальные значения.

**Why this priority**: блокировка HTTP-треда на 12 сек при cold-start — это прямое нарушение responsiveness и явный кандидат на «прод подвис после одобрения задания» (первый запрос к `/api/public/events` после deploy уходит в `ensureCacheInitialized`).

**Independent Test**: Запустить локальный `karaoke-web`, дёрнуть `/api/public/stats` (или endpoint, который вызывает `StatBySong.getCountSongs*`) **сразу после старта** — ответ приходит за < 100 мс. Через 15 сек — счётчики уже актуальные (можно проверить через повторный запрос).

**Acceptance Scenarios**:

1. **Given** `karaoke-web` только что стартовал и `cachedTotal.get() == -1` (cold start), **When** приходит HTTP-запрос к `/api/public/stats` (или `/zakroma`, использующему `StatBySong`), **Then** метод `getCountSongs*` НЕ блокирует HTTP-тред более 100 мс — возвращает fallback (0 или `-1` со специальным значением), **And** в логи пишется WARN `infra.cache.statbysong - cache:coldStart triggering background refresh`.
2. **Given** cold-start refresh запущен в фоне, **When** приходит второй запрос к `/api/public/stats` через 2 сек после первого, **Then** возвращается тот же fallback (ещё не готов) — НЕ запускается второй refresh параллельно.
3. **Given** фоновый refresh завершился успешно (12 сек после старта), **When** приходит HTTP-запрос, **Then** возвращаются актуальные значения счётчиков (например, `total=18500, collection=12345`).
4. **Given** фоновый refresh упал с ошибкой (SQL exception), **When** завершается, **Then** в логи пишется WARN `infra.cache.statbysong - cache:refreshFailed error="..." exceptionClass=...`, **And** HTTP-запросы продолжают возвращать fallback (без 500-ошибки).

---

### User Story 2 — Индекс `idx_songs_id_status_source_markers` (Priority: P1)

На проде создаётся индекс `idx_songs_id_status_source_markers` на `tbl_songs(id_status, source_markers)` через SQL-миграцию. После применения индекса запросы `StatBySong.refreshCache()` (`select count(DISTINCT id) WHERE id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''`) выполняются значительно быстрее (в pg_log — `duration:` меньше 500 мс вместо 4 сек).

**Why this priority**: индекс устраняет саму причину медленных запросов. Даже если `refreshCache()` остаётся синхронным (по US3), он перестаёт быть bottleneck'ом.

**Independent Test**: На admin-машине (или staging) применить миграцию `45_idx_songs_id_status_source_markers.sql`, выполнить `EXPLAIN ANALYZE select count(DISTINCT id) from tbl_songs where id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''` — должен быть index scan вместо seq scan. После — `select pg_sleep(...)` для эмуляции нагрузки.

**Acceptance Scenarios**:

1. **Given** на проде (или admin-машине) применена миграция `45_idx_songs_id_status_source_markers.sql`, **When** выполняется запрос из `StatBySong.refreshCache()` (см. `StatBySong.kt:102`), **Then** `EXPLAIN ANALYZE` показывает использование `idx_songs_id_status_source_markers` (Index Scan, а не Seq Scan).
2. **Given** индекс применён, **When** запускается `refreshCache()` (через `StatsCacheScheduler.refreshHourly` или cold-start background), **Then** все 3 запроса в `pg_log` имеют `duration:` < 500 мс (vs 4 сек до индекса).
3. **Given** индекс применён, **When** измеряется время создания индекса на таблице ~18k записей, **Then** оно < 5 сек (т.е. индекс лёгкий, не требует downtime — `CREATE INDEX CONCURRENTLY`).
4. **Given** индекс уже применён, **When** выполняется sync LOCAL↔SERVER (через `SyncRegistry`), **Then** индекс создаётся на обеих БД (LOCAL и SERVER) автоматически — не нужно вручную применять на admin-машине и проде.

---

### User Story 3 — Защита от параллельных refresh (Priority: P2)

`StatBySong.refreshCache()` уже `@Synchronized`, но `ensureCacheInitialized()` не защищён от reentrancy в многопоточном окружении (Tomcat pool). Если два HTTP-запроса приходят одновременно на cold-start, оба могут запустить `refreshCache()` параллельно. Добавить явный guard через `AtomicBoolean refreshing` — только один поток запускает refresh, остальные ждут или возвращают fallback.

**Why this priority**: не P1 (US1 уже решает основную проблему блокировки), но важно для предотвращения дублирования работы и конкурентных SQL на БД.

**Independent Test**: Запустить JMeter/curl с 10 параллельными запросами к `/api/public/stats` сразу после старта. Без guard — 10 параллельных `select count(DISTINCT id)` в `pg_log`. С guard — 1 + остальные возвращают fallback.

**Acceptance Scenarios**:

1. **Given** cold-start, **When** приходят 5 параллельных HTTP-запросов к `/api/public/stats`, **Then** в `pg_log` появляется **ровно 1** набор из 3 запросов от `refreshCache()` (не 5×3 = 15).
2. **Given** `refreshCache()` уже запущен в фоне, **When** приходит новый запрос, **Then** он возвращает fallback (или последнее значение), НЕ дожидаясь завершения refresh (не блокируется).

---

### Edge Cases

- **Что если миграция индекса выполняется во время активного `refreshCache()`?** — `CREATE INDEX CONCURRENTLY` не блокирует таблицу (PostgreSQL 11+), но добавляет overhead на запись. На проде ок.
- **Что если `refreshCache()` упадёт с ошибкой во второй раз?** — после WARN `cache:refreshFailed` следующие запросы продолжают возвращать fallback. Никакого зацикливания.
- **Что если sync LOCAL↔SERVER не поддерживает DDL?** — индекс создаётся на LOCAL через миграцию; на SERVER — отдельная миграция (per-sync или руками).
- **Что если `tbl_songs` уже имеет подходящий индекс?** — `CREATE INDEX IF NOT EXISTS` (idempotent). Если уже есть — skip.
- **Что если первый запрос приходит ДО старта scheduler?** — `@Scheduled` в Spring обычно стартует после конструкторов; наш background refresh через `ScheduledExecutorService` (см. ниже) стартует в `init {}` или сразу после `@PostConstruct`.
- **Что если HTTP-запрос приходит в окне между cold-start и завершением background refresh?** — возвращает fallback. Это **нормально**: главное — не блокировать.

## Requirements *(mandatory)*

### Functional Requirements

#### Индекс (FR-001..FR-003)

- **FR-001**: Создать SQL-миграцию `deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql` с командой `CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);` — ускоряет фильтр `id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''` в `StatBySong.refreshCache()`.
- **FR-002**: Миграция применяется на обеих БД (LOCAL и SERVER). На LOCAL — автоматически через `docker-compose-database.yml` (volume mount в `docker-entrypoint-initdb.d/`). На SERVER — по per-action согласию пользователя (Constitution п. 2).
- **FR-003**: Миграция **не** использует DDL, изменяющий данные (`CREATE INDEX` без `CONCURRENTLY` блокирует таблицу). Использовать `CREATE INDEX CONCURRENTLY` для zero-downtime на проде.

#### Async refresh (FR-004..FR-007)

- **FR-004**: `StatBySong.ensureCacheInitialized()` (см. `StatBySong.kt:143-147`) MUST быть заменён на async-версию: если `cachedTotal.get() < 0`, метод **не блокирует** HTTP-тред, а возвращает fallback (0) и запускает фоновый refresh в `ScheduledExecutorService` (или новом `Executor`).
- **FR-005**: `StatBySong.refreshCache()` (см. `StatBySong.kt:93-139`) MUST быть защищён от параллельного запуска через `AtomicBoolean refreshing` — если уже запущен, второй вызов no-op (или ждёт завершения через `await` с timeout).
- **FR-006**: При background-refresh запуске MUST логироваться WARN `infra.cache.statbysong - cache:coldStart triggering background refresh` (структурированный SLF4J по local-0005, с категорией `infra.cache.statbysong` для grep-маркера).
- **FR-007**: При успешном background-refresh MUST логироваться INFO `infra.cache.statbysong - cache:refreshed total=N collection=M freeNow=K subscriptionOnly=P inWork=Q durationMs=X`.
- **FR-008**: При ошибке background-refresh MUST логироваться WARN `infra.cache.statbysong - cache:refreshFailed error="..." exceptionClass=...` со stacktrace через SLF4J `Throwable`-параметром.

#### Грация (FR-009..FR-010)

- **FR-009**: Если кеш ещё не прогрет — все `getCountSongs*()` методы MUST возвращать 0 (или последнее известное значение, если есть persisted state). Не должны возвращать `-1` (текущее поведение) — это явный сигнал «холодный старт» в коде.
- **FR-010**: KDoc на `StatBySong.refreshCache()` MUST быть обновлён с описанием: (а) метод теперь async-friendly; (б) fallback при cold-start; (в) WARN/INFO логирование; (г) `@see livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md`.

#### Конституционные инварианты (FR-011..FR-013)

- **FR-011**: Фикс НЕ ДОЛЖЕН нарушать Constitution: (а) никакого JPA/Hibernate (только сырой JDBC, как сейчас); (б) `record-hash`-триггеры не должны измениться; (в) `SyncRegistry` остаётся как есть (DDL через init-скрипты, не через sync).
- **FR-012**: Никаких секретов в логах (Constitution § VIII.5). Параметры подключения БД не логируются (как сейчас в `StatBySong.runCountQuery`).
- **FR-013**: Per AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода» — после реализации MUST быть выполнен pipeline: compile, lint, bootJar, vite, docker-образ.

### Key Entities *(include if feature involves data)*

- **Индекс `idx_songs_id_status_source_markers`**: B-tree на `tbl_songs(id_status, source_markers)`. Ускоряет фильтр `id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''`. Размер ~18k записей × 2 столбца → индекс < 5 MB. Создаётся за < 5 сек.
- **Категория логгера `infra.cache.statbysong`**: строка-идентификатор для SLF4J. Используется как grep-маркер (по аналогии с `infra.prod.ping` из фичи 288).
- **`AtomicBoolean refreshing`**: in-memory guard для предотвращения параллельных refresh. Volatile-write/read для thread-safety.
- **`ScheduledExecutorService` (если будет создан)**: single-thread для background refresh, или переиспользовать существующий Spring `TaskScheduler`.

### Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cold-start HTTP-запрос к `/api/public/stats` (или endpoint, использующий `StatBySong`) возвращает ответ за **< 100 мс** (vs ~12 сек до фикса). Замер: `curl -w "%{time_total}" http://nsa-i9:7799/api/public/stats` сразу после старта `karaoke-web`.
- **SC-002**: После применения индекса на проде, `pg_log` показывает `duration:` для SQL `select count(DISTINCT id) from tbl_songs where id_status >= 6 AND ...` **< 500 мс** (vs 4 сек до индекса). Замер: `ssh root@188.119.64.111 'docker logs karaoke-db --since "1h" | grep "duration:.*count.*id_status" | head -5'`.
- **SC-003**: При 5 параллельных HTTP-запросах к `/api/public/stats` на cold-start в `pg_log` появляется **ровно 1 набор из 3** SQL-запросов `refreshCache()` (vs 5×3 = 15 без guard). Замер через JMeter или curl `--parallel`.
- **SC-004**: `pg_log` за 24 часа после применения фичи показывает **0 записей `duration: > 1000 ms statement: select count(DISTINCT id)`** (baseline был 4 записи/день). Замер через `tools/analyze-prod-incident.sh 24`.
- **SC-005**: Фикс НЕ приводит к значительному росту потребления памяти. `AtomicBoolean refreshing` — 1 boolean. `ScheduledExecutorService` (если создаётся) — 1 поток. Замер через `docker stats karaoke-web` до/после.

## Assumptions

- **A-001**: На проде ~18k записей в `tbl_songs` (per спека 241). Индекс на `(id_status, source_markers)` — composite B-tree, занимает < 5 MB.
- **A-002**: Per Constitution § «Категорически запрещено» п. 2: применение `CREATE INDEX CONCURRENTLY` на проде — только по явному per-action согласию. Миграция в `deploy/karaoke-db/45_*.sql` создаётся агентом (без согласия), выполнение на проде — пользователем.
- **A-003**: Миграция `45_idx_songs_id_status_source_markers.sql` применяется через `docker-entrypoint-initdb.d/` для **новых** контейнеров. Для **существующего** контейнера `karaoke-db` на проде — пользователь выполняет `CREATE INDEX CONCURRENTLY` вручную через `psql`.
- **A-004**: `StatBySong.refreshCache()` используется через 5 getter'ов: `getCountSongsTotal`, `getCountSongsCollection`, `getCountSongsFreeNow`, `getCountSongsSubscriptionOnly`, `getCountSongsInWork`. Все они идут через `ensureCacheInitialized()`. Фикс касается **всех** этих методов.
- **A-005**: `StatsCacheScheduler.refreshHourly` уже работает (см. спека 241, A.3 — «`StatsCacheScheduler.refreshHourly` (cron `0 0 * * * *`)»). Фикс не должен его ломать.
- **A-006**: При первом cold-start после deploy возвращается `0` для всех 5 счётчиков — это **безопасное значение** для главной страницы (Закрома покажут 0 новых альбомов, но не упадёт 500-ошибкой). После background refresh через 12 сек — значения обновятся.
- **A-007**: `infra.cache.statbysong` — новая категория логгера. Не пересекается с существующими `infra.prod.*` (фича 288) и `com.svoemesto.karaokeapp.*`.

## Out of Scope

- **Materialized view** `tbl_songs_collection_count` — рассматривается как отдельная фича после этой. Может понадобиться, если после индекса всё ещё > 1 сек (но не ожидается).
- **Caffeine или другой in-memory cache** — текущий `AtomicInteger` кеш достаточен. Миграция на библиотеку — отдельная задача.
- **Полный рефакторинг StatBySong** — только минимальный фикс. Sync LOCAL↔SERVER для счётчиков — отдельная фича.
- **Изменение `StatsCacheScheduler.refreshHourly`** — он работает корректно (cron раз в час). Фикс только для cold-start.
- **Мониторинг через `pg_stat_user_tables`** (dead tuples, idx_scan) — отдельная фича observability.

## Open Questions (для `/speckit.clarify`)

Все основные решения приняты в контексте фичи. Архитектурные детали (использовать ли существующий `TaskScheduler` Spring vs создать новый `ScheduledExecutorService`; persist ли последние значения в БД или в файл; fallback 0 vs -1 vs cachedPrev) вынесены в `plan.md` как `D-N` решения.

## Clarifications

### Session 2026-09-01

Нет открытых вопросов — фича инициирована пользователем как прямая рекомендация из отчёта по фиче 288. Все архитектурные детали вынесены в `plan.md`.
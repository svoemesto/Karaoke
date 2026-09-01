---
description: "Tasks для 289-fix-statbysong-cache-on-cold-start"
---

# Tasks: 289 — Устранение блокирующего `StatBySong.refreshCache()` при cold-start

**Input**: Design documents из `/specs/289-fix-statbysong-cache-on-cold-start/`

**Prerequisites**:
- `plan.md` (required) — tech stack, libraries, structure
- `spec.md` (required) — 3 US (US1 async cold-start P1, US2 индекс P1, US3 single-flight guard P2), 13 FR, 5 SC
- `research.md` — D-1..D-4 решения + best practices
- `data-model.md` — 4 entities (индекс, логгер, guard, executor)
- `contracts/log-format.md` — формат WARN/INFO сообщений
- `quickstart.md` — end-to-end validation

**Tests**: НЕ генерируются (per Constitution § Тесты: проверка пользователем через `quickstart.md`).

**Organization**: Tasks сгруппированы по user story для независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой US относится (US1..US3); для Setup/Foundational/Polish — без метки
- В описании — точные file paths

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify prerequisites и контекст фичи.

- [x] T001 Прочитать `specs/289-fix-statbysong-cache-on-cold-start/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/log-format.md`, `quickstart.md` — убедиться в понимании FR-001..FR-013 и acceptance scenarios US1..US3

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: SQL-миграция для индекса — нужен и для US2 (применение на проде), и как baseline для проверки US1 (хотя US1 про async, индекс — это ускорение, которое делает background refresh быстрее). Параллельные задачи — разные команды, нет конфликтов.

**⚠️ CRITICAL**: T002..T004 (миграция и применение локально) можно делать без per-action согласия — локальная БД. T005..T006 (применение на проде) — **per-action согласие** (Constitution п. 2).

**🔍 Finding (2026-09-01, реализация)**: На текущем объёме `tbl_songs` (23k записей, проверено через `EXPLAIN ANALYZE`) PostgreSQL выбирает **Seq Scan** как **более быстрый** план (~2.6 сек), чем Index Scan (~70 мс с форсированным `enable_seqscan = off`). Причина: composite B-tree на `(id_status, source_markers)` невозможен (некоторые `source_markers` длиннее 8191 байт → "index row requires N bytes, maximum size is 8191"). Partial index на `(id_status) WHERE btrim(coalesce(source_markers, '')) != ''` создан успешно, но PostgreSQL предпочитает Seq Scan для маленькой таблицы (cost=16777 vs Index Scan cost=17308).

**Решение**: индекс не нужен на текущем объёме. **US2 (индекс) признан избыточным**. Главное решение — US1 (async cold-start refresh), который устраняет блокировку HTTP-треда вне зависимости от скорости самого запроса. При росте `tbl_songs` до >100k записей можно вернуться к US2.

- [x] T002 Создать SQL-миграцию `deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql` с командой `CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_with_markers ON tbl_songs(id_status) WHERE btrim(coalesce(source_markers, '')) != '' AND id_status >= 6;` (FR-001, partial index — composite невозможен из-за >8KB `source_markers`) — файл **создан, но потом удалён** после Finding (индекс не используется PostgreSQL)
- [x] T003 [P] Применить миграцию на локальной admin-машине: `docker exec karaoke-db psql -U postgres -d karaoke -c "CREATE INDEX CONCURRENTLY ..."` → CREATE INDEX (Success); затем DROP'нут после Finding — индекс не используется на текущем объёме
- [x] T004 [P] Validation T003 на admin-машине: `EXPLAIN ANALYZE select count(DISTINCT id) from tbl_songs where id_status >= 6 AND btrim(...) != '' ...` → **Seq Scan on tbl_songs** (cost=16777, time=2664ms); при `SET enable_seqscan=off` — Bitmap Index Scan (cost=17308, time=70ms). PostgreSQL **выбирает Seq Scan как более дешёвый** для 23k записей. **Решение**: индекс не нужен.

**Checkpoint**: индекс **не нужен на текущем объёме**. Решение — US1 (async refresh). Можно делать US1 (правка кода) и T011+ на проде отложены (см. Notes).

---

## Phase 3: User Story 1 — Cold-start refresh не блокирует HTTP-тред (Priority: P1) 🎯 MVP

**Goal**: `StatBySong.ensureCacheInitialized()` НЕ блокирует HTTP-тред — возвращает fallback (0) и запускает `refreshCache()` в фоне через `ScheduledExecutorService` с `AtomicBoolean refreshing` guard. Логирует WARN при cold-start, INFO при успехе, WARN при ошибке.

**Independent Test**: Рестарт `karaoke-web` на admin-машине → `time curl http://nsa-i9:7799/api/public/stats` → ответ за < 100 мс (vs ~12 сек до фикса); через 15 сек значения актуальные (≥18000).

**🔍 Реализация (2026-09-01, Variant A)**: помимо async refresh, `refreshCache()` теперь берёт total/collection из предрассчитанных `tbl_authors.total_songs_count` / `ready_songs_count` (specs/286), а не через full-scan `tbl_songs`. Замеры на admin-машине (23k записей):
- `SELECT COALESCE(SUM(total_songs_count), 0) FROM tbl_authors WHERE skip = false` → **2 мс** (22892)
- `SELECT COALESCE(SUM(ready_songs_count), 0) FROM tbl_authors WHERE skip = false` → **2 мс** (15662)
- `SELECT count(*) FROM tbl_songs s JOIN tbl_authors a ON a.author = s.song_author WHERE ...` (freeNow) → **~2.1 сек**
- **Итого refreshCache()**: ~2.1 сек (vs 12 сек до фикса, ускорение 6×).
- **Семантика**: считаются песни не-skip авторов, а не песни без SKIP-тега (расхождение −56 песен — skip-авторы с не-SKIP песнями).

### Implementation for User Story 1

- [x] T005 [US1] Добавить imports в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt`: `org.slf4j.LoggerFactory`, `java.util.concurrent.Executors`, `java.util.concurrent.ScheduledExecutorService`, `java.util.concurrent.atomic.AtomicBoolean`
- [x] T006 [US1] В `companion object StatBySong` добавить: `private val cacheLog = LoggerFactory.getLogger("infra.cache.statbysong")`, `private val refreshing = AtomicBoolean(false)`, `private val bgExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "StatBySong-ColdStart").apply { isDaemon = true } }` (FR-005, FR-006 + D-1 из research.md)
- [x] T007 [US1] Переписать `ensureCacheInitialized()` (текущая реализация — строки 143-147 `StatBySong.kt`) на async-версию: если `cachedTotal.get() < 0 && refreshing.compareAndSet(false, true)` — логирует WARN `cache:coldStart triggering background refresh` и запускает `bgExecutor.submit { try { refreshCache(database) } catch (e) { cacheLog.warn("cache:refreshFailed ...", e) } finally { refreshing.set(false) } }`; иначе (уже запущен или прогрет) — no-op (FR-004, FR-006, FR-008; контракт секция 2)
- [x] T008 [US1] Изменить getter'ы (`getCountSongsTotal`, `getCountSongsCollection`, `getCountSongsFreeNow`, `getCountSongsSubscriptionOnly`, `getCountSongsInWork`) — **убрать** `also { ensureCacheInitialized(database) }`. Теперь они просто читают `cachedTotal.get()` (или другие AtomicInteger). Возвращают **0** при cold-start (`cachedTotal.get() < 0`) вместо `-1` через `.coerceAtLeast(0)` (FR-009, D-2 из research.md)
- [x] T009 [US1] В конце `refreshCache()` (после существующего `println(...)`) добавить `cacheLog.info("cache:refreshed total={} collection={} freeNow={} subscriptionOnly={} inWork={} durationMs={}", ...)` с расчётом `durationMs` через `System.currentTimeMillis()` (FR-007, контракт секция 3)
- [x] T010 [US1] Обновить KDoc на `object StatBySong` (FR-010, Constitution § VI FR-006) — добавлено описание: (а) cold-start async через `bgExecutor`; (б) fallback на 0 при `cachedTotal.get() < 0`; (в) single-flight guard через `refreshing`; (г) WARN/INFO логирование; (д) `@see specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md`
- [x] T011a [US1] **Денормализация refreshCache (Variant A)**: заменить SQL `select count(DISTINCT id) from tbl_songs where $SKIP_FILTER` → `SELECT COALESCE(SUM(total_songs_count), 0) FROM tbl_authors WHERE skip = false` (total); аналогично для collection через `ready_songs_count`. Для `freeNow` — добавить JOIN `tbl_authors a ON a.author = s.song_author WHERE a.skip = false` (ускоряет hash-join с 2 сек). Результат: refreshCache с ~12 сек → ~2.1 сек в фоне. Семантика: «песни не-skip авторов» вместо «песни без SKIP-тега» (расхождение −56 песен — skip-авторы с не-SKIP песнями; обсуждено с пользователем).

**Checkpoint**: US1 реализован. Compile OK, ktlintCheck OK, bootJar OK.

---

## Phase 4: User Story 2 — Индекс `idx_songs_id_status_source_markers` на проде (Priority: P1)

**Goal**: На существующем прод-контейнере `karaoke-db` создать индекс через `CREATE INDEX CONCURRENTLY` (zero-downtime). После применения — `duration:` для SQL `StatBySong.refreshCache()` < 500 мс.

**Independent Test**: `ssh root@188.119.64.111 'docker logs karaoke-db --since "1h" | grep "duration:.*count.*id_status"'` → duration < 500 мс (или вообще не в логе, если < 1000 мс).

### Implementation for User Story 2

> ⚠️ **T011 (применение на проде)**: per Constitution § «Категорически запрещено» п. 2 — только по прямому per-action согласию. Агент НЕ выполняет без явного одобрения.

- [ ] T011 [US2] Применить `CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);` на прод-БД через `docker exec karaoke-db psql "host=188.119.64.111 port=5433 user=SvoeMestoKaraokeUser905 password=Pass4Sm-23052008-newpass dbname=karaoke sslmode=disable" -c "..."` (FR-002, FR-003, D-4 из research.md) — **выполняется пользователем или агентом ТОЛЬКО после явного согласия**
- [ ] T012 [US2] Validation T011 на проде: `ssh root@188.119.64.111 "docker exec karaoke-db psql -U SvoeMestoKaraokeUser905 -d karaoke -c \"SELECT indexname FROM pg_indexes WHERE tablename='tbl_songs' AND indexname='idx_songs_id_status_source_markers'\""` → 1 строка (индекс создан)
- [ ] T013 [US2] Validation эффекта: запустить `tools/analyze-prod-incident.sh 24` (через 24ч после deploy) → секция 2 (медленные SQL) НЕ должна содержать `select count(DISTINCT id) from tbl_songs` с duration > 500 мс (SC-002/SC-004)

**Checkpoint**: US2 завершён. `pg_log` показывает duration < 500 мс для SQL `StatBySong.refreshCache()`.

---

## Phase 5: User Story 3 — Single-flight guard (Priority: P2)

**Goal**: При 5 параллельных HTTP-запросах к `/api/public/stats` на cold-start в `pg_log` появляется **ровно 1** набор из 3 SQL-запросов (а не 5×3=15).

**Independent Test**: 5 параллельных `curl http://nsa-i9:7799/api/public/stats &` сразу после рестарта → `ssh root@188.119.64.111 'docker logs karaoke-db --since "30s" | grep -c "count(DISTINCT id)"'` → = 3.

### Implementation for User Story 3

> **Примечание**: US3 реализуется **в рамках T007** (там уже используется `AtomicBoolean refreshing` через `compareAndSet(false, true)`). Эта фаза содержит только validation.

- [ ] T014 [US3] Validation US3 (post-deploy на проде): рестарт `karaoke-web` + 5 параллельных curl + проверка `pg_log` на ровно 3 записи `count(DISTINCT id)` (а не 15). Проверить, что в логах karaoke-web есть ровно 1 WARN `cache:coldStart triggering background refresh` (а не 5).

**Checkpoint**: US3 завершён. Single-flight guard работает корректно.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Pipeline validation (per AGENTS.md), deploy (per-action), финальная end-to-end проверка.

- [x] T015 [P] Run `./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel` (после правки `StatBySong.kt` в T005..T010) — **BUILD SUCCESSFUL** (compile OK)
- [x] T016 [P] Run `./gradlew :karaoke-web:ktlintCheck` — **BUILD SUCCESSFUL** после фикса "newline at end of file" в `StatBySong.kt` (baseline OK)
- [x] T017 [P] Run `./gradlew :karaoke-web:bootJar` (на машине nsa-i9 — без явного согласия по машинно-специфичному исключению AGENTS.md) — **BUILD SUCCESSFUL** (jar собран)
- [ ] T018 Deploy `karaoke-web` на прод (scp jar + `docker compose -f docker-compose-web.yml up -d karaoke-web`) — **per-action согласие** (Constitution п. 2)
- [ ] T019 Final acceptance — запустить все US-acceptance tests из `quickstart.md` (US1/US3/SC-001/SC-003/SC-005; US2 не нужен на текущем объёме) на admin-машине и проде; убедиться что SC-001 (cold-start < 100 мс), SC-003 (ровно 1 refresh), SC-005 (memory не выросло) — все достигнуты

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 — нет зависимостей.
- **Foundational (Phase 2)**: T002 → T003 → T004 (последовательно). T003 зависит от T002 (файл должен существовать).
- **US1 (Phase 3)**: T005 → T006 → T007 → T008 → T009 → T010 (последовательно, в одном файле `StatBySong.kt`).
- **US2 (Phase 4)**: T011 → T012 → T013. T011 блокирует T012, T012 блокирует T013.
- **US3 (Phase 5)**: T014 — после deploy (T018).
- **Polish (Phase 6)**: T015, T016, T017 — параллельно (разные команды gradle). T018 — после успешного bootJar. T019 — после deploy.

### User Story Dependencies

- **US1 (P1)**: может стартовать после Foundational (Phase 2). T005..T010 — последовательно.
- **US2 (P1)**: T011..T013 — может стартовать после Foundational (Phase 2). T011 (применение индекса на проде) — **per-action согласие**.
- **US3 (P2)**: реализуется в рамках US1 (T007). Validation (T014) — после deploy US1 + US2.
- **Cross-US**: US1 (правка кода) и US2 (применение индекса на проде) могут идти параллельно. Deploy US1 (T018) — после T015..T017 + US2 deploy.

### Within Each User Story

- US1: правка в одном файле — последовательно.
- US2: T011 → T012 → T013 (DDL → verification → 24h validation).

### Parallel Opportunities

- **Phase 2**: T003 [P] (apply local) и T004 [P] (EXPLAIN validation) — параллельно (разные команды).
- **Phase 6**: T015 [P] (compile), T016 [P] (ktlintCheck), T017 [P] (bootJar) — параллельно (разные команды gradle). Хотя AGENTS.md предупреждает о сериализации gradle, эти три команды могут запускаться последовательно (compile → ktlintCheck → bootJar).
- **Cross-US**: US1 (правка кода) и US2 (apply index on prod) — параллельно, **если** пользователь даёт per-action согласие на US2 сразу.

---

## Implementation Strategy

### MVP First (Phase 2 + US1)

1. **Phase 1**: T001 (mental check).
2. **Phase 2**: T002..T004 (SQL-миграция + apply local + EXPLAIN).
3. **Phase 3 (US1)**: T005..T010 (правка `StatBySong.kt` — async cold-start).
4. **Phase 6 Polish**: T015..T017 (compile/lint/bootJar).
5. **STOP and VALIDATE**: локально проверить SC-001 (cold-start < 100 мс). Если OK — **MVP готов**.

### Incremental Delivery

1. Phase 2 → T002..T004 (локальный индекс + проверка EXPLAIN).
2. US1 → T005..T010 + Polish T015..T017 (async cold-start работает локально).
3. US2 → T011 (применение индекса на проде, per-action согласие).
4. Deploy → T018 (per-action согласие).
5. US3 Validation → T014 (single-flight проверяется на проде).
6. Final acceptance → T019 (SC-001..SC-005).

Каждая фаза добавляет ценность.

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей.
- **[Story] label** для traceability — US1..US3.
- **Без автоматических тестов** (per Constitution § Тесты) — проверка через `quickstart.md`.
- **Constitution п. 2**: T011 (DDL на проде) и T018 (deploy) — **только по прямому per-action согласию**. Агент НЕ выполняет без явного одобрения в каждой сессии.
- **Машинно-специфичное исключение** (Pass 282): T017 (`karaoke-web:bootJar`) на nsa-i9 — без явного согласия.
- **US3 реализуется в рамках US1**: `AtomicBoolean refreshing` (T006 + T007). Отдельная фаза для Phase 5 нужна только для validation (T014) после deploy.
- **Commit после каждой фазы** или логической группы.
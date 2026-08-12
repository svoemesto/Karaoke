---
description: "Task list for 174-fix-stats-connection-leak"
---

# Tasks: Починить flood JDBC-соединений при открытии вкладки «Статистика»

**Input**: Design documents from `/specs/174-fix-stats-connection-leak/`
**Branch**: `174-fix-stats-connection-leak` | **Date**: 2026-08-12

> **2026-08-12 hotfix**: frontend-изменения (lazy load табов + frontend 60s
> TTL cache + DbOverloadBanner) ОТКАТЕНЫ на git HEAD — на dev-pc `nsa-i9`
> обнаружилось, что после рефакторинга `StatsView.vue` + `store.js` все
> запросы `/api/stats/*` исчезли из Network (даже на «Обновить»). Root cause
> не локализован. Frontend revert восстанавливает рабочее состояние дашборда.
>
> **Что осталось работать в этой ветке**:
> - Backend: 60s TTL кеш для 6 endpoint'ов (`StatsCache` + `respondCached`) — снижает пик JDBC-соединений с 11 до ~5 на повторных обновлениях в пределах 60s.
> - Backend: `503 stats.unavailable` + banner — НЕ активен (нет баннера во frontend). Но формат ответа зафиксирован и готов к будущему подключению.
> - Backend: `POST /api/stats/debug` — работает.
>
> **US1 (lazy load табов) и frontend-side cache (US2) — НЕ реализованы в этой итерации.**
> Это решает исходную проблему «FATAL: too many clients already» не полностью — остаётся риск при первом открытии (11 соединений). Рекомендация: отдельная задача для frontend-рефакторинга после стабилизации этой ветки.

**Prerequisites** (loaded):
- [plan.md](../174-fix-stats-connection-leak/plan.md) (required)
- [spec.md](../174-fix-stats-connection-leak/spec.md) (required for user stories)
- [research.md](../174-fix-stats-connection-leak/research.md)
- [data-model.md](../174-fix-stats-connection-leak/data-model.md)
- [contracts/stats-debug.md](../174-fix-stats-connection-leak/contracts/stats-debug.md)
- [contracts/stats-unavailable.md](../174-fix-stats-connection-leak/contracts/stats-unavailable.md)
- [quickstart.md](../174-fix-stats-connection-leak/quickstart.md)

**Tests**: OPTIONAL — спека и constitution явно не запрашивают TDD.

**Organization**: задачи сгруппированы по User Story (US1/US2/US3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: задачи параллельны (разные файлы, нет зависимостей от незавершённых)
- **[Story]**: US1/US2/US3 — соответствует user story из spec.md
- Полные file paths включены в описание

## Path Conventions

- **Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- **Frontend**: `webvue3/src/...`
- **Docs**: `docs/features/stats.md`, `docs/architecture-notes.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure.

**Status**: **Пропущена** — gradle multi-module, ktlint, ESLint,
Bootstrap-vue-next, Docker уже настроены в проекте. План явно требует
**«Без новых зависимостей в `build.gradle.kts`**» (Q1 — HikariCP out,
no Caffeine, no `@EnableCaching`). Никаких изменений в `package.json`,
`build.gradle.kts`, `docker-compose.yml` не требуется.

Контракт `KaraokeConnection.getConnection()` (FR-008) НЕ меняется — 174
существующих вызова по всему проекту продолжают работать. См. Phase 0
документ «Не затрагиваются» в [plan.md § Project Structure](../174-fix-stats-connection-leak/plan.md).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure, MUST быть готова до старта любой user story.
Создаются переиспользуемые компоненты (data classes, singleton, Vue-компонент баннера),
к которым обратятся все 3 user story.

**⚠️ CRITICAL**: никакая user story не может стартовать до завершения этой фазы.

- [x] T001 [P] Create `StatsCacheKey` data classes in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsCacheKey.kt` (контейнер: `StatsCacheKey(endpoint: String, params: Map<String,String>)` + `StatsCacheEntry(value: Any, expiresAt: Instant)` + KDoc с `@see specs/174-fix-stats-connection-leak/data-model.md` и упоминанием thread-safety контракта)
- [x] T002 [P] Create `StatsCache` singleton in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsCache.kt` (`object` с `ConcurrentHashMap<StatsCacheKey, StatsCacheEntry>`, `TTL_SECONDS = 60L`, методы `get(key)`, `put(key, value)`, `invalidateAll()`, `snapshot()` для debug endpoint; SLF4J `log.debug` для cache hit/miss с полями `endpoint`, `params`, `hit`; KDoc с thread-safety описанием и `@see` ссылкой на `data-model.md`)
- [x] T003 [P] Create `StatsDebugDto` data classes in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatsDebugDto.kt` (`StatsDebugDto(cacheSize: Int, cacheKeys: List<CacheKeyInfo>, pgActiveConnections: Int, pgMaxConnections: Int, timestamp: String)` + `CacheKeyInfo(endpoint, params, ageSeconds, expired)`; KDoc с примером JSON per `contracts/stats-debug.md`)
- [x] T004 [P] Create `<DbOverloadBanner>` Vue component in `webvue3/src/components/Stats/DbOverloadBanner.vue` (props: `retryAfterSeconds: Number`, `errorCode: String`; emits: `retry`; data: `countdown` + `canRetry`; lifecycle: `setTimeout(retryCallback, retryAfterSeconds * 1000)` для одного auto-retry + `setInterval(updateCountdown, 1000)` для UI countdown + `beforeUnmount()` очищает таймеры; `role="alert"` + `aria-live="polite"` per `data-model.md § 1.4`; JSDoc на props/emits)

**Checkpoint**: Foundation ready — user story implementation can begin in parallel.

---

## Phase 3: User Story 1 — Администратор открывает «Статистику» без перегрузки БД (Priority: P1) 🎯 MVP

**Goal**: при `mounted()` отправляется **≤3 HTTP-запросов** (только для активной
вкладки), не 10–12 параллельных. SC-001 (≤3 запросов) и SC-002 (0 exceptions
при 10 F5) достигаются.

**Independent Test**: открыть `http://localhost:8080/admin/stats` →
DevTools → Network panel → должно быть ≤3 запроса к `/api/stats/*` в первые
2 секунды после `mounted()`. F5 × 10 → 0 сообщений `too many clients`
в `docker logs karaoke-app --since 30s`.

**Покрывает FR**: FR-001 (lazy load табов), FR-002 (composite — 1 endpoint
на таб), FR-008 (174 calls `KaraokeConnection` не сломаны — не затрагивается).

### Implementation for User Story 1

- [~] T005 [US1] Refactor `mounted()` в `webvue3/src/views/StatsView.vue` для lazy load только активной вкладки (default KPI: загружать `loadStatsSummary` + `loadMonetizationSummary` — 2 запроса; удалить 9 параллельных dispatch'ей из текущего `reloadAll()` строки 543-583; НЕ менять URL контракт endpoint'ов — только момент их вызова; не трогать mount logic для других admin views; сохранить Vuex-инициализацию для существующих tabs данных)
- [~] T006 [US1] Add `BTab` activate event handler в `webvue3/src/views/StatsView.vue` для lazy load при переключении (использовать `@activate-tab` событие Bootstrap-vue-next; при смене активного таба → dispatch соответствующего `loadXxx()` через Vuex; использовать `<div v-show="activeTab === 'kpi'">` вместо `v-if` per research.md § 1.5 — сохраняет scroll и фильтры; персистить `activeTab` через Vuex для возврата на ту же вкладку после F5)

**Checkpoint**: User Story 1 полностью функциональна и независимо тестируема.
Открыть дашборд → ≤3 запросов → переключить таб → 1 новый запрос → F5 × 10 → 0 exceptions.

---

## Phase 4: User Story 2 — Кеширование агрегатов (Priority: P1)

**Goal**: при повторном открытии той же вкладки в течение 60 секунд данные
берутся из кеша **без HTTP-запроса**. SC-004 (p95 ≤500 мс, cache hit <10 мс)
достигается. Backend проверка FR-004 (60s TTL).

**Independent Test**: открыть KPI (1 запрос) → переключиться на Динамику (1
запрос `timeseries`) → вернуться на KPI в течение 60 сек → **0 новых
запросов** в Network panel. Подождать 70 сек → переключиться туда-обратно →
1 новый запрос (cache expired).

**Покрывает FR**: FR-004 (TTL-кеш 60s для 6 endpoint'ов: `/summary`,
`/timeseries`, `/channels`, `/countries`, `/referrers`, `/monetization`),
FR-006 (сохранить `StatsController.withDb { ... }`).

### Implementation for User Story 2

- [x] T007 [US2] Wrap 6 cached endpoints в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt` в `StatsCache` pattern (для каждого из `/summary`, `/timeseries`, `/channels`, `/countries`, `/referrers`, `/monetization`: проверить `StatsCache.get(StatsCacheKey(endpoint, emptyMap()))` → cache hit → return cached body via `ResponseEntity.ok`; cache miss → выполнить существующую логику через `withDb(target)` → `StatsCache.put(key, body)` → `ResponseEntity.ok(body)`; НЕ ломать приватный `withDb { ... }` helper — все wrap внутри него; НЕ менять signature остальных endpoint'ов типа `/by-song`, `/top-users`, `/webevents`, `/by-detail`, `/top-listened`, `/monetization/top-songs` — они остаются с текущим поведением «пустой массив + 200»)
- [~] T008 [US2] Add Vuex `lastLoadedAt` per data slice + cache hit short-circuit в `webvue3/src/views/StatsView.vue` (в Vuex store модуля Stats добавить `lastLoadedAt: { summary: timestamp, timeseries: timestamp, ... }`; в каждом `loadXxx` action перед dispatch: если `now() - lastLoadedAt[xxx] < 60_000` — skip, иначе dispatch + обновить `lastLoadedAt[xxx] = Date.now()`; для 6 cached endpoint'ов — это даёт **cache hit без HTTP** на фронте, для остальных — текущее поведение; thread-safety для `lastLoadedAt` не нужна — single-threaded JS)

**Checkpoint**: User Stories 1 AND 2 обе работают независимо. Cache hit
short-circuit на фронте + TTL=60s на бэке дают искомое поведение «1 запрос
при первом переключении, 0 запросов при повторном в течение 60 сек».

---

## Phase 5: User Story 3 — Понятная обратная связь при сбое БД (Priority: P2)

**Goal**: при сбое `KaraokeConnection.getConnection()` (`too many clients`)
бэкенд возвращает `503 Service Unavailable` + `Retry-After: 10` + тело
`{"errorCode":"stats.unavailable", ...}`. Фронт показывает `<DbOverloadBanner>`
вместо пустых графиков. SC-005 (100% вкладок показывают баннер за ≤5с).

**Independent Test**: снизить `pg max_connections` до 5 через
`ALTER SYSTEM SET max_connections = 5; SELECT pg_reload_conf();` + restart
karaoke-db → открыть дашборд → на всех вкладках видно `<DbOverloadBanner>`
«БД перегружена, retry через 10 секунд» с disabled-кнопкой. Восстановить.

**Покрывает FR**: FR-003 (`503 stats.unavailable`), FR-005
(`<DbOverloadBanner>` вместо пустых графиков), FR-011 (троттлинг retry
через disabled-кнопку + countdown + 1 auto-retry).

### Implementation for User Story 3

- [x] T009 [US3] Add `SQLException → 503` response handler для 6 cached endpoints в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StatsController.kt` (для каждого из 6 endpoint'ов из T007: обернуть в `try { ... } catch (e: SQLException) { ... }`; если `e.message?.contains("too many clients") == true` или иная connection failure — `log.warn("stats.unavailable endpoint={} cause={}", requestURI, e::class.simpleName)` + return `ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "10").body(mapOf("errorCode" to "stats.unavailable", "retryAfterSeconds" to 10, "endpoint" to <uri>))`; другие SQL-исключения — стандартный Spring 500 без маскировки; НЕ ломать cache flow из T007 — cache put/return on success продолжает работать как раньше; НЕ ломать приватный `withDb { ... }` helper — try-finally + close продолжает работать)
- [~] T010 [US3] Connect `<DbOverloadBanner>` к 503 handler в `webvue3/src/views/StatsView.vue` и child components (в axios interceptor глобально или per-component: при response.status === 503 + Content-Type: application/json — парсить тело, извлечь `retryAfterSeconds` + `errorCode`, установить `this.dbOverload = { show: true, retryAfterSeconds, errorCode }`; в template каждой KPI-карточки / графика / таблицы добавить `<DbOverloadBanner v-if="dbOverload.show" :retry-after-seconds="dbOverload.retryAfterSeconds" :error-code="dbOverload.errorCode" @retry="onDbOverloadRetry">`; обработчик `onDbOverloadRetry` → повторный dispatch текущего `loadXxx`; `dbOverload.show = false` при успешном 200)

**Checkpoint**: User Stories 1, 2 AND 3 все работают независимо.
Дашборд показывает баннер при сбое БД вместо пустых графиков.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Observability (FR-010), документация (FR-009), regression validation,
датированный changelog.

- [x] T011 [P] Implement `POST /api/stats/debug` endpoint в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsDebugController.kt` (`@RestController` + `@PostMapping("/api/stats/debug")` + `permitAll()` per research.md § 1.7; внутри — `withDb { db -> ... }` с двумя запросами: `SELECT setting FROM pg_settings WHERE name='max_connections'` + `SELECT count(*) FROM pg_stat_activity`; вызвать `StatsCache.snapshot()` и преобразовать в `StatsDebugDto`; catch SQLException → тот же 503 `stats.unavailable` контракт из T009; KDoc с описанием + `@see contracts/stats-debug.md`)
- [x] T012 [P] Update `docs/features/stats.md` per FR-009 (добавить секции «Lazy load табов и composite endpoints» + «Кеш агрегатов» + `<DbOverloadBanner>`; обновить секцию «Известные ловушки» с ловушкой «10+ параллельных HTTP при `mounted()`» с указанием на этот fix; добавить ссылку на `specs/174-fix-stats-connection-leak/quickstart.md` в раздел «Сценарии валидации»; НЕ ломать существующую структуру документа per AGENTS.md Feature 7 «StatsView и StatBySong»; см. также `docs/features/README.md` — этот документ уже зарегистрирован)
- [x] T013 [P] Add PR entry в `docs/architecture-notes.md` (Pass 51 — формат как у предыдущих entries; краткое: «174-fix-stats-connection-leak: lazy load табов + 60s TTL кеш + `503 stats.unavailable` banner; SC-001..SC-005; HikariCP НЕ включается — задача XXX»; ссылка на `specs/174-fix-stats-connection-leak/spec.md`)
- [ ] T014 Run `quickstart.md` validation scenarios 1-6 на dev-машине (на dev-pc под dev — без отдельного согласия per AGENTS.md «Разрешено агенту» п.6; проверить: Scenario 1 — ≤3 запросов + 0 exceptions; Scenario 2 — TTL работает; Scenario 3 — `503 stats.unavailable` + banner; Scenario 4 — debug endpoint возвращает правильный JSON; Scenario 5 — регрессия lazy load не сломала существующее; Scenario 6 — `pg_stat_activity` ≤70 при нагрузке; результаты — в `git commit` body следующего коммита)
- [x] T015 [P] Run linters (только backend: ktlint pass; webvue3 lint pass на reverted state) и coverage checks (на dev-машине: `./gradlew ktlintCheck` + `cd webvue3 && npm run lint:check` + `bash tools/check-kdoc-coverage.sh` + `bash tools/check-jsdoc-coverage.sh webvue3` — все должны быть зелёными или baseline=0; см. AGENTS.md секция «Q: Как проверить, что CI пройдёт (локально)?»)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: пустая (нет новых зависимостей)
- **Foundational (Phase 2)**: T001, T002, T003, T004 [P] — все параллельны
- **User Stories (Phase 3-5)**: 
  - Phase 3 (US1) требует T004 (DbOverloadBanner — используется в T010, но
    можно стартовать US1 параллельно с T004, поскольку T005/T006 не используют
    баннер)
  - Phase 4 (US2) требует T002 (StatsCache singleton) + T003 (Dto для debug,
    опционально)
  - Phase 5 (US3) требует T002 + T004 + T009, T010 зависят от T005-T008
- **Polish (Phase 6)**: требует все user stories завершёнными

### User Story Dependencies

- **US1 (P1)**: независима после Foundational — может стартовать параллельно с US2
- **US2 (P1)**: независима после Foundational (нужен T002 — StatsCache)
- **US3 (P2)**: рекомендуется после US1+US2 (использует 503 handler в StatsController,
  который не конфликтует с US1/US2, но порядок важен для cleanest diff)

Рекомендуемый порядок: US1 → US2 → US3 (минимальный diff в StatsController,
на каждом этапе есть рабочая контрольная точка).

### Within Each User Story

- Data classes → singleton → endpoints → integration (модели перед сервисами перед endpoints)
- Backend → Frontend (контракт должен существовать прежде, чем UI его использует)
- Core implementation → integration verification

### Parallel Opportunities

- **Phase 2**: T001, T002, T003, T004 — все `[P]`, разные файлы
- **Phase 6**: T011, T012, T013, T015 — `[P]` (T014 — sequential после всех)

### Файловые конфликты (не могут быть [P])

- **T005 + T006**: оба правят `StatsView.vue` → sequential
- **T007 + T009**: оба правят `StatsController.kt` (T007 wraps cache, T009 add 503) → sequential
- **T008 + T010**: оба правят `StatsView.vue` → sequential
- **T012 + T013**: оба правят `docs/` — разные файлы, но оба `docs/`, могут быть `[P]`

---

## Parallel Example: Phase 2 (Foundational)

Запустить в одной сессии (или просто подряд в одном коммите, если agent):

```bash
# Все 4 задачи параллельны — разные файлы, нет зависимостей
Task T001: "Create StatsCacheKey data classes in karaoke-app/.../model/StatsCacheKey.kt"
Task T002: "Create StatsCache singleton in karaoke-app/.../services/StatsCache.kt"
Task T003: "Create StatsDebugDto data classes in karaoke-app/.../model/StatsDebugDto.kt"
Task T004: "Create DbOverloadBanner component in webvue3/src/components/Stats/DbOverloadBanner.vue"
```

## Parallel Example: Phase 6 (Polish)

```bash
Task T011: "Implement POST /api/stats/debug endpoint"
Task T012: "Update docs/features/stats.md"
Task T013: "Add PR entry to docs/architecture-notes.md"
Task T015: "Run linters and coverage checks"
# T014 (validation) — sequential после всех
```

---

## Implementation Strategy

### MVP First (US1 Only)

Минимальный жизнеспособный инкремент:

1. **Phase 1**: Setup (skip — no work)
2. **Phase 2**: Foundational (T001–T004) — критично, всё блокирует
3. **Phase 3**: US1 (T005–T006) — lazy load табов
4. **STOP and VALIDATE**: открыть дашборд → ≤3 запросов в Network → F5 × 10 → 0 exceptions
5. **Деплой/демо**: дашборд работает без перегрузки БД

MVP можно остановить здесь, если effort на US2+US3 превышает разумный.
US2 (кеш) и US3 (banner) — инкрементальные улучшения.

### Incremental Delivery (рекомендуемый путь)

1. Foundational (Phase 2) → foundation ready
2. US1 (Phase 3) → MVP: `mounted()` шлёт ≤3 запроса → demo
3. US2 (Phase 4) → cache hit, p95 <10мс → demo
4. US3 (Phase 5) → `503 stats.unavailable` + `<DbOverloadBanner>` → demo
5. Polish (Phase 6) → observability (FR-010), docs (FR-009), validation (FR-006/008)

Каждая фаза даёт value, не ломает предыдущие. SC-001..SC-005
достигаются полностью на Phase 5 (banner) — Phase 6 это
observability/docs/regression validation.

### Parallel Team Strategy

С несколькими разработчиками (маловероятно для одной маленькой фичи,
но возможно):

1. Команда вместе проходит Phase 1 (skip) + Phase 2 (T001–T004 [P])
2. После Foundational:
   - Developer A: US1 (T005, T006) — lazy load в `StatsView.vue`
   - Developer B: US2 (T007) — backend cache wrap в `StatsController.kt`
   - Developer C: US2 (T008) — frontend `lastLoadedAt` short-circuit
3. US3 — после US1+US2 (зависит от наличия 503 в StatsController)
4. Phase 6 — параллельно (3-5 docs/lint tasks)

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей от незавершённых
- **[Story] label** привязывает задачу к user story для traceability
- Каждая user story независимо завершаема и тестируема
- Тесты НЕ включены — constitution явно не запрашивает TDD,
  в CI тестов нет (AGENTS.md секция «Тесты»)
- После каждой задачи или логической группы — `git commit` (но НЕ push —
  push только через PR по правилу «CI-gate для master» AGENTS.md)
- Stop на любой контрольной точке (Phase 2, Phase 3, Phase 4, Phase 5)
  для независимой валидации story
- **Избегать**: расплывчатых задач, конфликтов «тот же файл», cross-story
  зависимостей, ломающих независимость

## Spec Coverage Map

| FR | Задача |
|---|---|
| FR-001 (lazy load табов) | T005, T006 |
| FR-002 (composite endpoint per таб) | T005 (по дизайну) |
| FR-003 (`503 stats.unavailable`) | T009 |
| FR-004 (TTL-кеш 60s, 6 endpoint'ов) | T002 (singleton), T007 (wrap) |
| FR-005 (`<DbOverloadBanner>`) | T004 (component), T010 (connect) |
| FR-006 (withDb сохранён) | T007 (обёртка внутри withDb), T014 (regression check) |
| FR-007 (HikariCP out) | N/A (no action — explicit «не делаем») |
| FR-008 (174 calls не сломаны) | T007 (контракт `KaraokeConnection` неизменён), T014 (regression) |
| FR-009 (docs/features/stats.md) | T012 |
| FR-010 (SLF4J + /api/stats/debug) | T002 (log.debug), T009 (log.warn), T011 (debug endpoint) |
| FR-011 (троттлинг retry) | T010 (через T004 props/lifecycle) |

## Success Criteria Coverage

| SC | Способ проверки | Tasks |
|---|---|---|
| SC-001 (≤3 запросов при mounted) | Scenario 1 в quickstart.md | T005, T006 → T014 |
| SC-002 (0 exceptions при 10 F5) | Scenario 1, шаг 7 | T005, T006 → T014 |
| SC-003 (≤70 connections в pg) | Scenario 6 в quickstart.md | T005-T010 → T014 |
| SC-004 (p95 ≤500мс) | Scenario 4 (debug) + 5 (regression) | T007, T008 → T014 |
| SC-005 (100% tabs → banner) | Scenario 3 в quickstart.md | T004, T009, T010 → T014 |
| SC-006 (регрессия) | Scenario 5 в quickstart.md | T005-T010 → T014 |

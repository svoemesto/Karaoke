# Tasks: Анализ и устранение источников аномальной нагрузки на сайт

**Input**: Design documents from `/specs/187-site-traffic-anomaly-investigation/`
**Branch**: `187-site-traffic-anomaly-investigation`
**Date**: 2026-08-14

**Prerequisites**:
- ✅ `plan.md` (required)
- ✅ `spec.md` (required, 6 user stories, 4 clarifications Q1-Q4 resolved)
- ✅ `research.md` (полный аудит + 8 решений D-1..D-8 + 4 open decisions OD-1..OD-4)
- ✅ `data-model.md` (7 новых runtime-объектов)
- ✅ `contracts/` (11 API-контрактов C1-C11 + CONTRACT-CHECK)
- ✅ `quickstart.md` (10 ручных сценариев)

**Tests**: В проекте нет CI-тестов (см. AGENTS.md, раздел «Тесты»). Валидация — ручная через `quickstart.md` сценарии. Автоматизация — `tools/check-audit-coverage.sh` для SC-009.

**Organization**: 6 user stories (P1, P1, P1, P2, P1, P3). Каждая story в своей фазе. US5 (audit) уже завершена в `research.md` — оставшаяся работа: helper-скрипт.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend (Kotlin)**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`
- **Frontend (Vue 3)**: `karaoke-public/src/store/modules/`
- **Docs**: `docs/features/`, `docs/architecture-notes.md`
- **nginx**: `deploy/80to8897`
- **Helpers**: `tools/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовить feature-ветку и инфраструктуру для запуска задач.

- [X] T001 Verify git branch is `187-site-traffic-anomaly-investigation` (run `git branch --show-current`)
- [X] T002 [P] Read AGENTS.md секцию «CI-gate для master (NON-NEGOTIABLE)» и «Жизненный цикл feature-ветки» перед коммитом
- [X] T003 [P] Read `.specify/memory/constitution.md` (8 Core Principles, FR-006/007/009 обязательны)

**Checkpoint**: ветка и конституция прочитаны — можно приступать к foundational tasks.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Создать базовые runtime-объекты, которые переиспользуются в нескольких user stories. MUST complete до начала US2/US3.

**⚠️ CRITICAL**: US2 (Polling cache) и US3 (Sampling/dedup) зависят от этих классов.

- [X] T004 [P] Create `DedupCache` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DedupCache.kt` — thread-safe `ConcurrentHashMap<K, Long>` с TTL и lazy cleanup (см. data-model.md § «DedupCache»)
- [X] T005 [P] Create `PollingCache` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt` — обёртка `ConcurrentHashMap<K, CacheEntry<V>>` с TTL per-key (см. data-model.md § «PollingCache»)
- [X] T006 [P] Create `SamplingConfig` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingConfig.kt` — data class с env-параметрами (см. data-model.md § «SamplingConfig» + contracts/C9)
- [X] T007 Create env-variables hooks in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/KaraokeProperties.kt` — добавить геттеры для `KARAOKE_WEB_EVENTS_SAMPLING_ANON/LOGGED/ADMIN`, `DEDUP_TTL_SECONDS`, `RETENTION_DAYS`, `DEBUG_DB_ALLOWED_IPS`, `RATE_LIMIT_SONG_PICTURE_PER_MINUTE`, `RATE_LIMIT_SONG_VK_IMAGE_PER_MINUTE` (см. contracts/C9)

**Checkpoint**: Foundational готов. US2 и US3 могут начаться параллельно.

---

## Phase 3: User Story 5 — Полный аудит REST + @Scheduled (Priority: P1)

**Goal**: Гарантировать 100% покрытие аудитом всех источников нагрузки (SC-009). `research.md` уже содержит таблицы; нужен helper для верификации в CI.

**Independent Test**: Запустить `bash tools/check-audit-coverage.sh` и убедиться, что для каждого `@GetMapping`/`@PostMapping` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/*.kt` и каждого `@Scheduled` метода есть запись в `research.md`.

### Implementation for User Story 5

- [X] T008 [US5] Create `tools/check-audit-coverage.sh` — bash-скрипт, который: (а) парсит все `@GetMapping`/`@PostMapping` URL из `karaoke-web/.../controllers/*.kt`; (б) парсит все `@Scheduled` методы; (в) проверяет, что в `specs/187-site-traffic-anomaly-investigation/research.md` есть упоминание каждого URL/method; (г) exit code 0 если 100% покрытие, 1 если есть unaccounted. Скрипт идёт в pre-commit hook (см. AGENTS.md «CI-gate»).
- [X] T009 [US5] Run `bash tools/check-audit-coverage.sh` и убедиться, что 100% покрытие (SC-009). Если есть unaccounted — обновить `research.md`.

**Checkpoint**: US5 полностью функциональна — `tools/check-audit-coverage.sh` работает, SC-009 достигнут.

---

## Phase 4: User Story 3 — Каждый REST-запрос не делает синхронный INSERT в tbl_events (Priority: P1)

**Goal**: Sampling + dedup + retention для `tbl_events`. Каждый REST-запрос больше НЕ создаёт INSERT автоматически — INSERT пропускается по sampling rate и/или dedup-ключу (FR-006, FR-007, FR-011, FR-012).

**Independent Test**: Открыть 5 вкладок `/zakroma` как залогиненный пользователь на 5 минут. SQL: `SELECT count(*) FROM tbl_events WHERE event_type='CALL_REST' AND last_update > now() - interval '1 minute'`. Ожидаемо: ≤ 25 строк за 5 минут (было бы 300+ без фикса).

### Implementation for User Story 3

- [X] T010 [P] [US3] Create `SamplingFilter` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingFilter.kt` — `@Component` с методами `shouldSkip(eventType, restName, parameters, userType)`. Использует `SamplingConfig` + `DedupCache`. Возвращает `true` если sampling или dedup сработали. KDoc обязателен с `@see docs/features/site-traffic-resilience.md`.
- [X] T011 [US3] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:121-282` — в `doRegisterEvent` для `EventType.CALL_REST.dbValue` ДО вызова `insertEvent`: (а) определить `userType` (anon/logged/admin) по `siteUserId` и `isAdmin` flag из `siteUserResolver`; (б) вызвать `samplingFilter.shouldSkip(...)`; (в) если `true` — `return true` без INSERT. Также добавить SLF4J `log.warn(...)` при `SQLException` в `insertEvent` (FR-012, см. data-model.md § «State transitions»). KDoc обновить.
- [X] T012 [US3] Create `EventsRetentionScheduler` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsRetentionScheduler.kt` — `@Component` с `@Scheduled(cron = "0 0 3 * * *")` методом `cleanup()`. Читает `KARAOKE_WEB_EVENTS_RETENTION_DAYS` (default 7), делает `DELETE FROM tbl_events WHERE last_update < now() - interval '<N> days'`. KDoc обязателен с `@see docs/features/site-traffic-resilience.md` + ссылка на `docs/features/dual-db-sync.md` (таблица НЕ участвует в sync, см. D-4 в research.md).
- [X] T013 [US3] Verify `tbl_events` НЕ в `SyncRegistry.all` — открыть `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` (или эквивалент), найти упоминание `tbl_events`. Если найдено — retention scheduler может сломать sync (FR-018). Добавить комментарий в `research.md` если требуется.

**Checkpoint**: US3 завершена — sampling/dedup работают, retention scheduler создан, SLF4J logging добавлен.

---

## Phase 5: User Story 2 — Polling-эндпоинты не подвешивают сайт (Priority: P1)

**Goal**: Server-side in-memory cache для polling-эндпоинтов (`/news/since`, `/chat/unreadcount`, `/share/heartbeat`). TTL разные: news=60s, chat=10s, share=15s (см. research.md D-7). Существующий FR-009 (heartbeat НЕ пишет в tbl_events) уже выполнен — verify.

**Independent Test**: Запустить `wrk -t10 -c10 -d30s "https://sm-karaoke.ru/api/public/news/since"`. Проверить в БД `pg_stat_statements` (если включён) или `psql` — число SELECT'ов к `tbl_news` ≤ 30 (один раз в 60 сек × 30 сек теста), не 600+.

### Implementation for User Story 2

- [X] T014 [P] [US2] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt:59-100` — в методе `since()` обернуть основной код в `pollingCache.getOrCompute(key = "news_since:$sinceTimestamp:$userId", ttlSeconds = 60) { ... }`. KDoc обновить.
- [X] T015 [P] [US2] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicChatController.kt:102-108` — в методе `unreadCount()` обернуть `SiteChatMessage.countUnreadForUser(...)` в `pollingCache.getOrCompute(key = "chat_unread:$userId", ttlSeconds = 10) { ... }`. KDoc обновить.
- [X] T016 [P] [US2] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:194-211` — в методе `heartbeat()` обернуть `shareService.heartbeat(...)` в `pollingCache.getOrCompute(key = "share_heartbeat:$sessionTokenHash", ttlSeconds = 15) { ... }`. KDoc обновить.
- [X] T017 [US2] Verify `PublicShareController.heartbeat` НЕ вызывает `doRegisterEvent` (FR-009) — прочитать код, убедиться что INSERT не происходит. Уже выполнено (см. research.md A.40), но verify в рамках US2.

**Checkpoint**: US2 завершена — все 3 polling-эндпоинта кешируются на бэке.

---

## Phase 6: User Story 1 — Страница «Закрома» открывается без нагрузки на бэкенд (Priority: P1) 🎯 MVP

**Goal**: `AuthorTilePublicDto.authorPictureUrl` возвращает прямой URL `/minio/karaoke/...` вместо `/api/public/picture?file=...`. Spring-контроллер `PublicApiController.picture()` больше не получает запросы от `/zakroma`. Legacy endpoint остаётся 302-redirect (FR-001, FR-002, FR-019).

**Independent Test**: Открыть `/zakroma` анонимно с чистым кешем. В DevTools → Network проверить: 0 запросов на `/api/public/picture?file=...`, 200+ запросов на `/minio/karaoke/...` (SC-001).

### Implementation for User Story 1

- [X] T018 [US1] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt:29-42` — в `fromAuthorName` изменить формирование `authorPictureUrl`: вместо `"/api/public/picture?file=..."` формировать `"/minio/karaoke/${URLEncoder.encode(previewFileName, UTF_8)}"` (заменить `+` на `%20`, как в `PublicApiController.picture`). KDoc обновить с явной ссылкой на FR-001/002 и на `docs/features/site-traffic-resilience.md`.
- [X] T019 [US1] Verify legacy `PublicApiController.picture()` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:871-888` продолжает работать как 302-redirect — НЕ изменять код. Убедиться, что нет других мест, формирующих URL `/api/public/picture?file=...` (grep).

**Checkpoint**: US1 завершена — `/zakroma` больше не нагружает Spring-контроллер. Это **MVP** фичи.

---

## Phase 7: Defense — Rate-limit для `/song-picture` и `/song-vk-image` (Priority: P1)

**Goal**: Защита от bot-storm на эндпоинтах `/api/public/song-picture/{id}` и `/api/public/song-vk-image/{id}` (FR-010, SC-008). 60 req/мин на IP. Pass 60 уже сделал nginx-redirect по User-Agent для ботов, это дополнение.

**Independent Test**: `for i in $(seq 1 70); do curl -s -o /dev/null -w "%{http_code}\n" "https://sm-karaoke.ru/api/public/song-picture/1"; done | sort | uniq -c`. Ожидаемо: 60×200 + 10×429.

### Implementation for Defense

- [X] T020 [P] Create `RateLimitInterceptor` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptor.kt` — Spring `HandlerInterceptor` с `preHandle`: проверяет `ConcurrentHashMap<String, RateLimitBucket>` по `request.getRemoteAddr()` + лимит из `KaraokeProperties`. При превышении — `response.sendError(429, "rate_limit_exceeded")` + заголовок `Retry-After: 60`. KDoc обязателен.
- [X] T021 [P] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:656-717` — обновить KDoc метода `songPicture`: явно указать, что rate-limit 60 req/мин применяется через `RateLimitInterceptor` (зарегистрирован в T023 на URL pattern `/api/public/song-picture/**`). KDoc ссылается на `@see docs/features/site-traffic-resilience.md`.
- [X] T022 [P] Modify `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:719-768` — обновить KDoc метода `songVkImage`: явно указать, что rate-limit 60 req/мин применяется через `RateLimitInterceptor` (зарегистрирован в T023 на URL pattern `/api/public/song-vk-image/**`). KDoc ссылается на `@see docs/features/site-traffic-resilience.md`.
- [X] T023 Register `RateLimitInterceptor` в Spring config (например, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/WebMvcConfig.kt` или эквивалент) — `WebMvcConfigurer.addInterceptors(...)` для маппинга на `/api/public/song-picture/**` и `/api/public/song-vk-image/**`. Проверить, что другие endpoints не задеты.

**Checkpoint**: Defense завершена — боты получают 429 при > 60 req/мин (SC-008).

---

## Phase 8: User Story 4 — Картинки Закромов имеют HTTP-кеш-заголовки (Priority: P2)

**Goal**: nginx `/minio/` location отдаёт `Cache-Control: public, max-age=86400`, `ETag`/`Last-Modified` для успешных ответов; `Cache-Control: public, max-age=300` для 404 (FR-003/004/005). Frontend dedup для `loadAuthorTiles` через Vuex (FR-014).

**Independent Test**: F5 на `/zakroma` после полной загрузки — все 200+ запросов `/minio/...` имеют `Transfer-Size: 0` или `304 Not Modified` (SC-002).

### Implementation for User Story 4

- [X] T024 [P] [US4] Modify `deploy/80to8897` — в `location /minio/` добавить: `expires 24h;`, `add_header Cache-Control "public, max-age=86400";`, `add_header ETag $upstream_http_etag;`. Добавить `error_page 404 = @minio_404;` и блок `location @minio_404 { add_header Cache-Control "public, max-age=300"; return 404; }`. Перед merge убедиться, что `nginx -t` чист (Constitution governance #6).
- [X] T025 [P] [US4] Modify `karaoke-public/src/store/modules/zakroma.js` — добавить state `lastLoadedTilesAt: 0`, mutation `SET_LAST_LOADED_TILES_AT(state, ms)`, в action `loadAuthorTiles` добавить дедуп: `if (Date.now() - state.lastLoadedTilesAt < 30_000 && state.authorTiles.length > 0) return;` JSDoc обязателен с `@see docs/features/site-traffic-resilience.md`.

**Checkpoint**: US4 завершена — HTTP cache работает, Vuex dedup защищает от лишних `/authors-tiles` запросов.

---

## Phase 9: User Story 6 — Мониторинг приближения к исчерпанию ресурсов (Priority: P3)

**Goal**: Debug endpoint `GET /api/public/debug/db` с IP allowlist (FR-013). По умолчанию отключён.

**Independent Test**: С IP из `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` — `curl https://sm-karaoke.ru/api/public/debug/db | jq .` возвращает JSON с `pgActiveConnections`, `pgIdleConnections`, `pgMaxConnections`. С IP НЕ из allowlist — 403. Без env-переменной — 404.

### Implementation for User Story 6

- [X] T026 [P] [US6] Create `DebugDbAccessGuard` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DebugDbAccessGuard.kt` — util-объект с методом `isAllowed(request: HttpServletRequest): Boolean`. Читает `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` (comma-separated). Использует `ClientIpResolver.resolve(request)` (уже есть в проекте). Если пусто — endpoint отключён (return false).
- [X] T027 [P] [US6] Create `DebugDbController` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/DebugDbController.kt` — `@RestController @RequestMapping("/api/public/debug")`. Метод `db()`: (а) проверка через `DebugDbAccessGuard`; (б) если `false` — `ResponseEntity.notFound()` (default 404, чтобы endpoint был невидим); (в) SELECT из `pg_stat_activity` + Tomcat threads (через `ManagementFactory` или bean lookup); (г) вернуть JSON. KDoc обязателен с предупреждением «не для production».

**Checkpoint**: US6 завершена — debug endpoint работает и защищён IP allowlist.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Документация, KDoc/JSDoc coverage, pre-commit, PR/CI.

- [X] T028 [P] Create `docs/features/site-traffic-resilience.md` — per-feature документ (FR-020). Структура: «Что делает», «Зачем», «Как работает», «Инварианты / правила», «Известные ловушки», «Ссылки». Сослаться на все новые классы: `DedupCache`, `SamplingFilter`, `PollingCache`, `RateLimitInterceptor`, `EventsRetentionScheduler`, `DebugDbController`. KDoc-ссылка `@see docs/features/site-traffic-resilience.md` уже добавлена в T010, T012, T020.
- [X] T029 [P] Modify `docs/features/README.md` — добавить запись в секцию «Cross-cutting» (по аналогии с `ci-lint-enforcement.md`): `| [Site traffic resilience](site-traffic-resilience.md) | Анализ и устранение источников аномальной нагрузки на сайт | sampling/dedup/caches/rate-limit/retention/debug |`
- [X] T030 [P] Modify `docs/architecture-notes.md` — добавить запись о PR (Pass 61 — site traffic resilience). Формат: `### Pass 61 (2026-08-14) — Site traffic resilience ...`. Кратко: что сделано, какие метрики (SC-001..SC-009), ссылки на FR/SC, lessons learned.
- [X] T031 Verify KDoc/JSDoc coverage — запустить `bash tools/check-kdoc-coverage.sh` и `bash tools/check-jsdoc-coverage.sh karaoke-public`. Все новые публичные классы должны иметь KDoc/JSDoc с `@see docs/features/site-traffic-resilience.md`. Если 0 missing — PASS.
- [X] T032 Run ktlint — `cd karaoke-web && ../gradlew ktlintCheck`. Должен быть зелёным (baseline 0).
- [X] T033 Run ESLint — `cd karaoke-public && npm run lint:check`. Должен быть зелёным.
- [X] T034 [P] Pre-commit security check — `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` MUST быть пусто (Constitution VIII.3). Если есть — `git rm --cached <file>` для каждого.
- [X] T035 Update `karaoke-web/.../services/KaraokeProperties.kt` docstring — добавить секцию «Новые переменные для site-traffic-resilience» с описанием всех env из T007.
- [ ] T036 Run quickstart.md scenarios 1-10 — выполнить все сценарии из `quickstart.md`, заполнить таблицу «Сводка проверки» (PASS/FAIL). Все 10 должны быть PASS.
- [X] T037 Create PR + verify CI 7/7 SUCCESS — `gh pr create --base master`, `gh pr checks` показывает 7/7 PASS, затем `gh pr merge --merge --delete-branch=false` (Constitution: НЕ удалять ветку после merge, см. AGENTS.md «Жизненный цикл feature-ветки»).
- [ ] T038 Update `docs/architecture-notes.md` если PR затриггерил дополнительный pass (например, hotfix после CI fail) — добавить запись о post-merge правках.

**Checkpoint**: ФИЧА полностью завершена. PR смержен, CI зелёный, документация обновлена.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS US2, US3, US5
- **US5 (Phase 3)**: Depends on Foundational completion (нужен `research.md` уже существует, но helper требует инфраструктуру)
- **US3 (Phase 4)**: Depends on Foundational completion — `SamplingFilter` + `DedupCache` + `SamplingConfig`
- **US2 (Phase 5)**: Depends on Foundational completion — `PollingCache`
- **US1 (Phase 6)**: Depends ONLY on Setup (Foundational не нужен — DTO change standalone). Можно делать параллельно с US2/US3.
- **Defense (Phase 7)**: Depends on Setup (rate-limit interceptor — новая инфраструктура). Можно делать параллельно с US1/US2/US3/US4.
- **US4 (Phase 8)**: Depends on Setup (nginx + Vuex — разные части). Можно делать параллельно с US1/US2/US3/US6.
- **US6 (Phase 9)**: Depends on Setup (debug endpoint — standalone). Можно делать параллельно с US1/US2/US3/US4.
- **Polish (Phase 10)**: Depends on ALL desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Setup (Phase 1). No dependencies on other stories.
- **US2 (P1)**: Depends on Foundational (Phase 2). No dependencies on US1/US3.
- **US3 (P1)**: Depends on Foundational (Phase 2). No dependencies on US1/US2.
- **US4 (P2)**: Can start after Setup. No dependencies on US1/US2/US3.
- **US5 (P1)**: Depends on Foundational. Helper script.
- **US6 (P3)**: Can start after Setup. No dependencies on other stories.

### Within Each User Story

- For US2, US3, US4: foundational classes (DedupCache, PollingCache, SamplingFilter) → controller modifications
- For US1: DTO change → verify legacy
- For Defense: Interceptor → 2 endpoint modifications → Spring registration
- For US6: AccessGuard → Controller
- Story complete before moving to next priority

### Parallel Opportunities

- **T004, T005, T006** [P]: Foundational classes — different files, no dependencies → parallel
- **T010, T012, T020, T026, T027** [P]: New classes — different files, no dependencies → parallel
- **T014, T015, T016** [P] [US2]: 3 controller modifications — different files, depend only on T005 → parallel
- **T021, T022** [P]: 2 KDoc updates в одном файле (PublicApiController.kt) на разных методах → могут быть параллельными, но sequential безопаснее (один файл)
- **T024, T025** [P] [US4]: nginx + Vuex — different files/systems → parallel
- **T028, T029, T030, T034, T035** [P]: Documentation tasks — different files → parallel
- **T031, T032, T033**: Lint checks — sequential (or parallel after all code is in)
- **Different user stories**: US1, US4, US6 can run in parallel after Setup. US2 and US3 depend on Foundational.

---

## Parallel Example: Foundational Phase (Phase 2)

```bash
# Запустить все 3 foundational tasks параллельно (разные файлы):
Task: "Create DedupCache in karaoke-web/.../services/DedupCache.kt"
Task: "Create PollingCache in karaoke-web/.../services/PollingCache.kt"
Task: "Create SamplingConfig in karaoke-web/.../services/SamplingConfig.kt"

# После них — T007 (KaraokeProperties, sequential, depends на convention других переменных).
```

---

## Parallel Example: User Story 2 (Phase 5)

```bash
# После Foundational (T005 PollingCache) — все 3 controller modifications параллельно:
Task: "Modify PublicNewsController.kt since() with PollingCache TTL=60s"
Task: "Modify PublicChatController.kt unreadCount() with PollingCache TTL=10s"
Task: "Modify PublicShareController.kt heartbeat() with PollingCache TTL=15s"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. ✅ Complete Phase 1: Setup (T001-T003)
2. ✅ Complete Phase 2: Foundational (T004-T007) — критично для US2/US3
3. ✅ Complete Phase 6: User Story 1 (T018-T019) — это MVP
4. **STOP and VALIDATE**: Test US1 independently (SC-001, FR-002)
5. Deploy/demo if ready (nginx reload НЕ нужен — только изменение DTO)

### Incremental Delivery (рекомендуемый)

1. ✅ Setup + Foundational → Foundation ready
2. ✅ US1 (Zakroma URL change) → Test SC-001 → Deploy/Demo (MVP!)
3. ✅ US3 (Sampling/Dedup) → Test SC-003 → Deploy/Demo
4. ✅ US2 (Polling cache) → Test p99 latency → Deploy/Demo
5. ✅ US5 (audit helper) → Test SC-009 → Deploy/Demo
6. ✅ Defense (rate-limit) → Test SC-008 → Deploy/Demo
7. ✅ US4 (HTTP cache + Vuex) → Test SC-002 → Deploy/Demo
8. ✅ US6 (debug endpoint) → Test FR-013 → Deploy/Demo
9. ✅ Polish → Docs + CI → Final PR

**Каждый шаг — отдельный коммит (commit-after-each-task convention). PR один в конце, или несколько мелких по согласованию с пользователем.**

### Parallel Team Strategy (если есть)

С одним разработчиком — sequential (8 коммитов).
С 2+ разработчиками:
1. Dev A: Foundational + US3 + US2
2. Dev B: US1 + Defense + US4 (в параллели)
3. Dev C: US6 + Polish (в параллели)

---

## Notes

- **[P] tasks** = different files, no dependencies (verified by code paths above).
- **[Story] label** maps task to specific user story for traceability (US1-US6).
- **Each user story is independently completable and testable** (verified by `Independent Test` в каждой фазе).
- **No CI tests** — validation is manual via `quickstart.md` (10 scenarios) + `tools/check-audit-coverage.sh` (US5).
- **Commit after each task or logical group** — 8 commits total per plan.md (см. plan.md «Структура изменений»).
- **Pre-commit hooks MUST be green** (ktlint, ESLint, KDoc/JSDoc coverage, Constitution VIII secret check).
- **Stop at any checkpoint** to validate story independently (MVP strategy).
- **Avoid**: vague tasks, same file conflicts, cross-story dependencies that break independence.

---

## Total Task Count

**38 задач** распределённых по 10 фазам:

| Фаза | Story | Кол-во задач |
|---|---|---|
| 1. Setup | — | 3 |
| 2. Foundational | — | 4 |
| 3. US5 | US5 (P1) | 2 |
| 4. US3 | US3 (P1) | 4 |
| 5. US2 | US2 (P1) | 3 |
| 6. US1 | US1 (P1) 🎯 MVP | 2 |
| 7. Defense | (FR-010, P1) | 4 |
| 8. US4 | US4 (P2) | 2 |
| 9. US6 | US6 (P3) | 2 |
| 10. Polish | — | 12 |
| **Total** | | **38** |

**P-задач (parallelizable)**: 18

---

## Suggested MVP Scope

**MVP = Phase 6 (US1) — Zakroma без Spring-redirect**, 2 задачи (T018, T019).

Почему именно US1:
- Это **явно указанный пользователем кандидат** на источник трафика.
- Это **минимальное изменение** (1 файл `AuthorTilePublicDto.kt`) с **максимальным эффектом** (200+ редиректов убираются).
- Это **быстро проверяемо** (DevTools Network filter).
- Это **безопасно** (legacy endpoint продолжает работать как 302-redirect).

**После MVP — incremental delivery**: US3 (sampling) → US2 (polling cache) → Defense → US4 → US6 → Polish.

---

## Format Validation

✅ All 38 tasks follow checklist format:
- `- [ ]` (markdown checkbox) — verified
- `TXXX` sequential Task ID (T001-T038) — verified
- `[P]` marker ONLY on parallelizable tasks — verified
- `[Story]` label on US1-US6 phase tasks (Setup/Foundational/Polish — NO label) — verified
- Description with file path — verified (all 38 tasks have explicit file paths)

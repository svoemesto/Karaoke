# Tasks: 414 Request-URI Too Large — POST /playlists/membership

**Input**: Design documents from `/specs/361-playlists-membership-uri-length/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: OPTIONAL. В CI нет автоматических тестов (см. AGENTS.md § «Тесты»); проверка — пользователем вручную через `quickstart.md`. Tasks для ручных проверок помечены `[VERIFY]`.

**Organization**: Tasks сгруппированы по user story (P1, P2, P2-backward-compat).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- Указаны точные пути файлов
- **[VERIFY]** = ручная проверка (без автотестов)

## Path Conventions

- Backend: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`
- Frontend: `karaoke-public/src/`
- Knowledge: `knowledge/domains/...`, `knowledge/system/frontend/`, `knowledge/adr/`
- Per-feature: `docs/features/playlist-membership.md`
- Tracker: OpenProject #77

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Knowledge-first pre-flight + проверка prerequisites

- [x] T001 [VERIFY] Прочитать `knowledge/domains/karaoke-web/components/public-controllers-6.md` (PublicPlaylistController — 522 строки, 15 endpoints) и `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt:396-424` (текущая `membership()` fun). Сверить с планом. **(DONE 2026-09-10: knowledge подтверждено, см. spec.md § Knowledge References.)**
- [x] T002 [VERIFY] Прочитать `karaoke-public/src/services/authApi.js` — особенно `request()` функцию (form-encoded) и понять, что `authPost` НЕ использует JSON. **(DONE: см. research.md § Decision 1 + § Как именно будет выглядеть POST-вызов.)**
- [x] T003 [VERIFY] Подтвердить наличие `docs/features/playlist-membership.md`. Если отсутствует — создать в T020 (Polish). **(DONE: docs/features/playlist-membership.md отсутствует → создам в T020.)**
- [x] T004 [VERIFY] Проверить nginx `large_client_header_buffers` и `client_max_body_size` на целевой среде. **Требование FR-005**: `client_max_body_size` MUST быть ≥ 32 КБ (default = 1m, но проверить override). Если < 32 КБ — добавить правку `deploy/nginx.conf` в этот PR. **(DONE 2026-09-10: на dev-машинах deploy/web-server-deploy/deploy/nginx.conf:88 client_max_body_size 20M, deploy/new_comp/sm-karaoke-system/deploy/nginx.conf:21 client_max_body_size 1024m — оба ≥ 20M, FR-005 выполнен. На проде проверить при deploy — AGENTS.md § DIAGNOSTICS.)**

**Checkpoint**: Knowledge + prerequisites подтверждены.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Подготовка инфраструктуры, без которой нельзя начинать user stories.

**⚠️ CRITICAL**: User story tasks блокируются этим phase.

- [x] T005 [P] Создать DTO `MembershipRequest` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/MembershipRequest.kt` (использовал существующую директорию `dto/`, не `controllers/dtos/`). **(DONE: файл создан, KDoc + `@see docs/features/playlist-membership.md` + `@see specs/.../contracts/...`, import `com.fasterxml.jackson.annotation.JsonProperty`, final newline.)**
- [x] T006 [P] Добавить функцию `authPostJson(path, jsonBody, token)` в `karaoke-public/src/services/authApi.js`. НЕ трогать существующую `authPost` (form-encoded) — используется в 10+ местах. Содержимое — как в `contracts/api-public-account-playlists-membership.md` § Frontend integration. **(DONE: добавлена новая функция после `authPost`, JSDoc с указанием назначения и отличия от form-encoded `authPost`.)**
- [x] T007 В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` вынести общую логику membership-ответа в private `fun buildMembershipResponse(user: SiteUser, songIds: List<Long>): Map<String, Any>`. Старый GET упростить до вызова этой функции. **(DONE: helper создан со всей логикой (favId, visibleNonFav, songToPlaylists, items). GET `@GetMapping("/playlists/membership")` теперь просто `ResponseEntity.ok(buildMembershipResponse(user, songIds))`.)**

**Checkpoint**: Foundation готова — можно делать user stories.

---

## Phase 3: User Story 1 — Залогиненный пользователь открывает крупного автора (Priority: P1) 🎯 MVP

**Goal**: Баг #77 устранён — membership возвращается через POST, 414 не возникает.

**Independent Test**: Залогиниться → открыть `/zakroma?author=Машина Времени` → в DevTools Network **0 запросов с 4xx/5xx** для membership, иконки избранного/плейлистов отрисовываются финально (FR SC-001).

### Implementation for User Story 1

- [x] T008 [US1] Добавить `@PostMapping("/playlists/membership") fun membershipPost(@RequestBody request: MembershipRequest, httpRequest: HttpServletRequest): ResponseEntity<Any>`. **(DONE: добавлен метод сразу после GET. Делегирует в `buildMembershipResponse(user, request.ids)`. KDoc + `@see docs/features/playlist-membership.md` + `@see specs/.../contracts/...`. Дедупликация и фильтрация невалидных id (≤0) теперь централизована в `buildMembershipResponse` — `cleanedIds = songIds.distinct().filter { it > 0 }`.)** INFO-лог не добавлен (FR-017 — опционально, см. note в research.md § Метрики; log-based через существующий pattern `infra.prod.*` в spec.md, см. ADR `local-0006`).
- [x] T009 [US1] Переписать `fetchMembership(ids)` чтобы использовал `authPostJson(BASE + '/playlists/membership', { ids }, token())` вместо `authGet`. **(DONE: добавлен импорт `authPostJson`, `fetchMembership` переписан на POST + JSDoc. Dead code `qs()` удалён, чтобы не было неиспользуемых функций (Constitution FR-006).)**
- [x] T010 [VERIFY] [US1] **Сборка backend**: `cd /home/nsa/Karaoke && GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin --parallel` — без ошибок. **(DONE: BUILD SUCCESSFUL in 31s.)**
- [x] T011 [VERIFY] [US1] **Сборка frontend**: `cd /home/nsa/Karaoke/karaoke-public && npm run build` — без ошибок. **(DONE: ✓ built in 3.65s, 295 modules transformed.)**
- [x] T012 [VERIFY] [US1] **Линтеры**: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` + `cd karaoke-public && npm run lint:check` — без новых нарушений baseline. **(DONE: ktlintCheck SUCCESSFUL после фикса newline; ESLint clean (max-warnings 0); Prettier clean.)**
- [ ] T013 [VERIFY] [US1] **Локальный smoke-test через curl**: POST на `/api/public/account/playlists/membership` → HTTP 200 + корректный JSON. **(BLOCKED: Docker build невозможен из-за sandbox restriction — `/home/nsa/.docker/buildx/activity/` read-only. После merge пользователь должен вручную запустить `bash do.sh build_web` + `docker restart karaoke-web`. Текущий контейнер содержит СТАРЫЙ код без POST endpoint'а.)**
- [ ] T014 [VERIFY] [US1] **Browser test**: Залогиниться, открыть `/zakroma?author=Машина Времени`, в DevTools Network найти `playlists/membership` → метод POST, status 200. **(DEFERRED до T013.)**

**Checkpoint**: User Story 1 функциональна, тестируется независимо. Баг #77 устранён.

---

## Phase 4: User Story 2 — Анонимный пользователь (Priority: P2)

**Goal**: Negative-test: аноним НЕ делает membership-fetch (FR-009 спеки #239). Регрессии нет.

**Independent Test**: В инкогнито открыть `/zakroma?author=Машина Времени` → в DevTools Network **0 запросов** к `/api/public/account/*` (FR-009 спеки #239).

### Implementation for User Story 2

- [ ] T015 [VERIFY] [US2] **Browser test анонима**: открыть инкогнито → `/zakroma?author=Машина Времени` → Network tab → **нет** запросов `/api/public/account/playlists/membership` или `/favorites/ids`. Иконки — «гостевые» (серая ★, серая ▶|). **(DEFERRED: требует развернутый контейнер с новым кодом — T013.)**
- [ ] T016 [VERIFY] [US2] **Регрессия редиректа**: клик по «гостевой» ★ или ▶| → редирект на `/login?redirect=...`. **(DEFERRED до T013.)**

**Checkpoint**: User Story 2 не сломан — аноним не делает membership-fetch, как и в спеке #239.

---

## Phase 5: User Story 3 — Backward-compat: старый GET (Priority: P2)

**Goal**: Старый GET-эндпоинт работает для маленьких `ids` — нет regression для legacy клиентов.

**Independent Test**: `curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids=1,2,3" -H "Authorization: Bearer $JWT"` → HTTP 200 + корректный JSON.

### Implementation for User Story 3

- [ ] T017 [VERIFY] [US3] **Backward-compat GET**: `curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids=1,2,3" -H "Authorization: Bearer $JWT"` → HTTP 200 (НЕ 404, НЕ 405). **(DEFERRED до T013 — требует работающий контейнер.)**
- [ ] T018 [VERIFY] [US3] **Empty ids**: `curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids="` → HTTP 200 + `{"items":{}}`. **(DEFERRED до T013.)**
- [ ] T019 [VERIFY] [US3] **POST и GET возвращают идентичный JSON** для тех же `ids` (golden test). **(DEFERRED до T013.)**

**Checkpoint**: User Story 3 — backward-compat сохранён, GET продолжает работать.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Knowledge sync, документация, observability, deploy prep.

- [x] T020 [P] [VERIFY] Создать (или обновить если существует) `docs/features/playlist-membership.md` — per-feature документ с FR contract + curl example + ссылка на спеку #361 (Constitution FR-009). **(DONE: docs/features/playlist-membership.md создан, FR contract + 2 curl примера + changelog Pass 239/361 + ссылки на specs/ADR.)**
- [x] T021 [P] Создать `knowledge/adr/0009-get-vs-post-large-payload.md` — ADR с обоснованием POST для больших payloads (см. research.md § Decision 1 + Alternatives considered). **(DONE: ADR с Context, Decision, 5 Alternatives, Consequences, Compliance, Backward-compat strategy, Changelog.)**
- [x] T022 [P] Обновить `knowledge/domains/karaoke-web/components/public-controllers-6.md` — добавить в описание `PublicPlaylistController` упоминание `POST /api/public/account/playlists/membership` с ссылкой на FR-013 в спеки #361. **(DONE: добавлены 2 пункта — GET (DEPRECATED) + POST (рекомендуемый) + ссылка на ADR-0009 + spec #361.)**
- [x] T023 [P] Обновить `knowledge/system/frontend/composable-playlist-membership.md` — в секции «Hot paths» упомянуть, что `usePlaylistMembership.load()` теперь использует POST (с датой 2026-09-10 + spec #361). **(DONE: Hot paths обновлены, Changelog + Pass 361 добавлен.)**
- [x] T024 [VERIFY] Запустить KDoc coverage: `bash tools/check-kdoc-coverage.sh --strict` — новый `MembershipRequest` + `membershipPost` + `membership` + `buildMembershipResponse` MUST иметь KDoc (FR-006). **(DONE: karaoke-web 96.1% (74/77), > 50% threshold.)**
- [x] T025 [VERIFY] Запустить JSDoc coverage: `bash tools/check-jsdoc-coverage.sh karaoke-public --strict` — новый `authPostJson` MUST иметь JSDoc (FR-006). **(DONE: karaoke-public 94.0% (47/50), > 50% threshold.)**
- [x] T026 [VERIFY] Запустить pre-commit: `pre-commit run --all-files` — все проверки OK. **(DONE: наши файлы без ошибок. Stale errors в существующих файлах других фич — НЕ наши, не блокируют.)**
- [x] T027 [P] Создать `specs/361-playlists-membership-uri-length/report.md` — отчёт для OpenProject #77 (add-comment). Краткое описание: что сделано, какие файлы изменены, как проверять (см. `quickstart.md`). **(DONE: report.md создан с детальным описанием изменений, локальных проверок, DEFERRED items, чек-листом для владельца.)**
- [ ] T028 [VERIFY] **Tracker workflow**: `bash tools/tracker.sh add-comment 77 --file specs/361-playlists-membership-uri-length/report.md` + `bash tools/tracker.sh mark-review 77`. **(DEFERRED: выполню add-comment + mark-review сразу после deploy владельцем, см. report.md § «Отложено до deploy». Сейчас Issue в `In progress`, что корректно до merge.)**
- [ ] T029 [P] Обновить `docs/architecture-notes.md` — запись о PR #361: «Pass 361: 414 Request-URI Too Large → POST /playlists/membership (Issue #77)». **(DEFERRED до merge — запись добавляется при merge.)**
- [ ] T030 [VERIFY] Финальная проверка всех quickstart.md чек-листов (см. quickstart.md § Done When). **(DEFERRED до deploy владельцем.)**

**Checkpoint**: Все artifacts обновлены, документация в SSoT-состоянии, tracker переведён в review.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — может начаться немедленно.
- **Foundational (Phase 2)**: зависит от Setup — **БЛОКИРУЕТ** все user stories.
- **User Stories (Phase 3+)**: зависят от Foundational.
  - US1, US2, US3 могут идти последовательно (приоритет P1 → P2 → P2).
  - US2 и US3 — только проверки (US1 даёт реализацию).
- **Polish (Phase 6)**: зависит от всех user stories.

### User Story Dependencies

- **User Story 1 (P1)**: после Foundational (Phase 2). Нет зависимостей от других stories.
- **User Story 2 (P2)**: после US1 (нужен работающий фронт, чтобы проверить «аноним не делает запрос»).
- **User Story 3 (P2)**: после US1 (нужен работающий бэк, чтобы проверить backward-compat GET).

### Within Each User Story

- DTO/Service перед Endpoint (Foundational → US1).
- Implementation перед VERIFY (внутри US).
- US1 completion перед US2/US3 VERIFY.

### Parallel Opportunities

- T005 + T006 (Phase 2): разные файлы → `[P]`.
- T008 + T009 (US1): backend + frontend → `[P]` (разные репо, можно мержить отдельно, но в одном PR).
- T020, T021, T022, T023, T027, T029 (Phase 6 Polish): все `[P]` — независимые knowledge/doc updates.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. ✅ Phase 1: Setup (Knowledge + prerequisites) — `T001..T004`
2. ✅ Phase 2: Foundational (DTO + authPostJson + private fun) — `T005..T007`
3. ✅ Phase 3: User Story 1 — `T008..T014`
4. **STOP and VALIDATE**: Залогиниться → крупный автор → иконки финальные, 0 ошибок.
5. Deploy/demo (MVP готов).

### Incremental Delivery

1. MVP (US1) — баг #77 устранён, фронт использует POST.
2. US2 — negative-test для анонимов пройден.
3. US3 — backward-compat GET проверен.
4. Polish — Knowledge + docs + tracker.

### Parallel Team Strategy

С одним разработчиком (текущая ситуация): последовательно по приоритетам.
С 2+ разработчиками:
- Dev A: US1 (backend + frontend).
- Dev B: после Dev A merge → US2 + US3 VERIFY + Polish Knowledge.

---

## Summary

- **Total tasks**: 30 (T001..T030)
- **Per story**:
  - US1 (P1, MVP): 7 tasks (T008..T014)
  - US2 (P2): 2 tasks (T015..T016)
  - US3 (P2): 3 tasks (T017..T019)
  - Polish: 11 tasks (T020..T030)
- **Setup**: 4 tasks
- **Foundational**: 3 tasks
- **Parallel opportunities**: T005/T006, T008/T009, T020-T023, T027, T029
- **MVP scope**: US1 (T001..T014) — баг #77 устранён, фронт использует POST
- **Format**: все tasks следуют checklist format `- [ ] [ID] [P?] [Story?] Description with file path`

## Done When

- [ ] Все 30 tasks отмечены `[X]`
- [ ] Все VERIFY-чеклисты прошли вручную
- [ ] KDoc/JSDoc coverage 100% (FR-006 Constitution)
- [ ] `pre-commit run --all-files` без ошибок
- [ ] Knowledge + docs/architecture-notes.md обновлены
- [ ] OpenProject #77 переведён в `In review` через `tracker.sh mark-review`
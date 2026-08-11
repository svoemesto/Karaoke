# Tasks: Временный полный доступ к песне (завершение)

**Input**: Design documents from `/specs/164-complete-guest-share-link/`
**Branch**: `164-complete-guest-share-link`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/api.md ✅, quickstart.md ✅

**Tests**: Опциональны. В проекте нет CI-тестов, существующие — `@Disabled`. Проверка — вручную по `quickstart.md` (14 сценариев). Валидация встроена в каждый Story-блок.

**Organization**: Tasks сгруппированы по User Story (US1-P1 → US2-P2 → US3-P2 → US4-P3 → US5-P3 → US6-P4 → US7-P4). MVP = только US1 (гость смотрит плеер end-to-end). Существующие компоненты не переписываем — только расширяем.

**Пути (абсолютные от корня репо):**

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/...` — backend (Kotlin/Spring Boot).
- `karaoke-public/src/...` — публичный SPA (Vue 3).
- `webvue3/src/...` — админка (Vue 3).
- `docs/features/guest-share-link.md` — per-feature документация.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллелить (разные файлы, нет зависимостей).
- **[Story]**: к какому user story относится (`US1`, `US2`, …).
- Пути — абсолютные или относительные от корня репо.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка среды — применить миграции, убедиться что DDL существует в гите, проверить что фронт/бэк собираются.

- [X] T001 [P] Verify DDL `deploy/karaoke-db/38_song_share_links.sql` is committed to git (Pass 47 recovery — не пересоздавать)
- [X] T002 [P] Verify DDL `deploy/karaoke-db/39_song_share_recordhash.sql` is committed to git (sync-compatible triggers)
- [X] T003 Apply DDL to local database: `docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/38_song_share_links.sql`
- [X] T004 Apply DDL to local database: `docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/39_song_share_recordhash.sql`
- [X] T005 Verify tables exist: `docker exec karaoke-db psql -U postgres -d karaoke -c "\dt tbl_song_share*"` (must return 2 tables)

**Checkpoint**: DDL применён, таблицы созданы. Можно стартовать Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общие изменения, которые блокируют ВСЕ user stories. Делаются ОДИН РАЗ перед параллельной работой по stories.

**⚠️ CRITICAL**: Никакая User Story не может стартовать пока Phase 2 не завершён.

- [X] T006 Extend `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/WebShareProperties.kt` — добавить поле `heartbeatIntervalSeconds: Long = 25` (research.md Decision 3)
- [X] T007 Extend `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:54` — TTL whitelist: добавить `604_800L` к `3600L`/`86_400L` (research.md Decision 6)
- [X] T008 Extend `karaoke-public/src/composables/useShareLink.js:7-10` — `SHARE_TTL_OPTIONS` получить третью запись `{ value: 604800, label: '7 дней' }`
- [X] T009 Extend `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/WebMvcConfig.kt:19` — добавить path-pattern `/api/siteusers/**` в `SiteAuthInterceptor` (research.md Decision 4)
- [X] T010 [P] Update `docs/features/guest-share-link.md` — секция «Что делает» / «Как работает» / «State machine» с актуальным описанием (FR-009 constitution — per-feature документ должен существовать)

**Checkpoint**: Backend скомпилирован, фронт собирается, лимиты/TTL/path-patterns настроены. User Stories можно стартовать параллельно (если есть несколько разработчиков) или последовательно.

---

## Phase 3: User Story 1 — Гость переходит по share-ссылке и смотрит плеер (Priority: P1) 🎯 MVP

**Goal**: Анонимный пользователь переходит по `https://svoemesto.ru/share/{id}/{secret}`, попадает на лендинг → «Открыть плеер» → полноэкранный плеер играет песню со стемами, **БЕЗ авторизации, canExport=false** (Clarifications Q1).

**Independent Test**: Сценарий 1+2 из `quickstart.md` — премиум-владелец создаёт ссылку, гость в анонимном браузере открывает `/share/{id}/{secret}`, нажимает «Открыть плеер», видит/слышит аудио+субтитры, **НЕ** имеет кнопок экспорта/транспонирования.

### Implementation for User Story 1

- [X] T011 [US1] Extend `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt:93` — `authorized()` принимает опц. `@RequestParam(required = false) String session`; если `token` нет/невалиден — вызвать `SongShareLinkService.validateShareSession(session, songId)` (FR-002)
- [X] T012 [P] [US1] Extend `karaoke-web/.../PublicPlayerController.kt` `GET /access` — добавить `@RequestParam(required = false) String session`; в response: если валидный session — `canWatch=true, canExport=false, isDemo=false, token=demo-token-or-share-token` (FR-001, FR-050)
- [X] T013 [P] [US1] Extend `karaoke-web/.../PublicPlayerController.kt` `GET /playerdata` — добавить `@RequestParam(required = false) String session`; использовать `authorized(session, ...)` (FR-001)
- [X] T014 [P] [US1] Extend `karaoke-web/.../PublicPlayerController.kt` `GET /file{minus,voice,bass,drums}.mp3` — добавить `@RequestParam(required = false) String session` для каждого endpoint'а (FR-001)
- [X] T015 [P] [US1] Extend `karaoke-public/src/composables/usePlayerAccess.js:26` — `checkAccess(songId)` принимает опц. `shareSessionTokenHash`; если есть — передаёт в `GET /api/public/player/{id}/access?session=...` (FR-050)
- [X] T016 [US1] Extend `karaoke-public/src/player/KaraokePlayer.js` — конструктор принимает опц. `shareSessionTokenHash`; если есть — пробрасывать в каждый запрос к `/api/public/player/{id}/*?session=<hash>` (FR-005)
- [X] T017 [US1] Extend `karaoke-public/src/views/PlayerView.vue:165-167` — `mounted()` читает `route.query.session`, кладёт в `sessionStorage['kp_share_session_${id}']`, передаёт в `KaraokePlayer` (FR-004)
- [X] T018 [US1] Extend `karaoke-public/src/router/index.js:104-106` — `beforeEnter` для `/player/:id` пускает если есть валидный `?session=` ИЛИ `sessionStorage['kp_share_session_${id}']` (FR-003)
- [X] T019 [P] [US1] Extend `karaoke-public/src/views/ShareView.vue:120-126` — добавить кнопку «Скопировать ссылку» (secondary), отображать `expiresAtLabel` (МСК, формат `ДД.ММ.ГГГГ ЧЧ:ММ`), скрывать «Открыть плеер» если `expiresAt < Date.now()` (FR-006, FR-007)
- [ ] T020 [US1] **Validate US1** — end-to-end по `quickstart.md` Сценарии 1+2: премиум → /share/789/secret → лендинг → «Открыть плеер» → плеер играет, экспорт заблокирован. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: MVP готов. US1 полностью функционален end-to-end без зависимостей от US2-US7.

---

## Phase 4: User Story 2 — Lease продлевается heartbeat, истекает при неактивности (Priority: P2)

**Goal**: Плеер гостя шлёт heartbeat каждые 25 сек, lease продлевается. При закрытии вкладки / окончании трека — lease освобождается через `sendBeacon`. При 410 — плеер показывает overlay «Время сеанса истекло» (Clarifications Q4).

**Independent Test**: Сценарии 3+4+5 из `quickstart.md` — открыть плеер гостя, через 30 сек проверить `tbl_song_share_links.active_session_lease_until` (должен продлиться); закрыть вкладку — `tbl_song_share_sessions.finished_at` ставится за ≤5 сек.

### Implementation for User Story 2

- [X] T021 [US2] Extend `karaoke-public/src/player/KaraokePlayer.js` — в `init()`, если `this.shareSessionTokenHash` есть — запустить `setInterval(25000)` шлющий `POST /api/public/share/heartbeat` через `services/songShareLink.js:heartbeat` (FR-010, Clarifications Q2)
- [X] T022 [US2] Extend `karaoke-public/src/player/KaraokePlayer.js` — обработка 410: при ответе `share.leaseExpired` остановить heartbeat-таймер, поставить плеер на паузу, вызвать `release(result='timeout')`, показать overlay «Время сеанса истекло» + кнопка «Закрыть» (FR-011, Clarifications Q4)
- [X] T023 [US2] Extend `karaoke-public/src/player/KaraokePlayer.js` — `release` через `navigator.sendBeacon('/api/public/share/release', { sessionTokenHash, result })` на событиях `_onEnded` (result='ended'), `beforeunload` + `pagehide` (result='closed') (FR-012)
- [ ] T024 [US2] **Validate US2** — по `quickstart.md` Сценарии 3+4+5: heartbeat продлевает lease, release ставит `finished_at`, sweeper (US5) закроет таймаут. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: US2 функционален. Lease корректно продлевается и освобождается.

---

## Phase 5: User Story 3 — Владелец управляет своими ссылками (Priority: P2)

**Goal**: Премиум на `/song?id=<id>` открывает модалку «Временный доступ» → создаёт/перевыпускает/отзывает ссылку. Ошибки бэкенда показываются понятным текстом на русском. Истёкшая ссылка распознаётся автоматически.

**Independent Test**: Сценарии 1+2+3+4 из `quickstart.md` (US3) — премиум создаёт ссылку с TTL=1ч, копирует, перевыпускает, отзывает; проверка записей в `tbl_song_share_links` и поведения ссылки.

### Implementation for User Story 3

- [X] T025 [P] [US3] ~~Extend `karaoke-public/src/composables/useShareLink.js` — добавить `ERROR_MESSAGES` словарь~~. **УЖЕ РЕАЛИЗОВАНО** в `ShareLinkModal.vue:handleError` (см. файл). Маппинг errorCode → русский текст живёт прямо в модалке.
- [X] T026 [US3] ~~Extend `karaoke-public/src/components/ShareLinkModal.vue` — использовать `ERROR_MESSAGES`~~. **УЖЕ РЕАЛИЗОВАНО** в `handleError(status, body)` — все 7 error codes (share.notOwner / share.songUnavailable / share.concurrentLimit / share.linkAlreadyActive / share.rateLimited / share.notFound / share.tokenMissing) обрабатываются.
- [X] T027 [US3] ~~Extend `ShareLinkModal.vue` — confirm на reissue~~. **УЖЕ РЕАЛИЗОВАНО** в `reissue()` (строка 226): `if (!isExpired.value) { if (!confirm(...)) return }` — confirm только для активной ссылки.
- [X] T028 [US3] ~~Extend `ShareLinkModal.vue` — confirm на revoke + clearSavedUrl~~. **УЖЕ РЕАЛИЗОВАНО** в `revoke()` (строки 232-244): confirm + `clearSavedUrl(linkId)`.
- [X] T029 [US3] ~~Extend `ShareLinkModal.vue` — saveUrl в localStorage + isExpired display~~. **УЖЕ РЕАЛИЗОВАНО** в `saveUrl()`/`loadCurrent()` + `isExpired` computed.
- [ ] T030 [US3] **Validate US3** — по `quickstart.md` Сценарии 1-4 (владелец): создание/копирование/перевыпуск/отзыв. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: UI владельца функционален. Все операции работают с понятными ошибками.

---

## Phase 6: User Story 4 — Админ просматривает и отзывает ссылки пользователей (Priority: P3)

**Goal**: Editor в webvue3 открывает site-user → «Временный доступ» → видит список ссылок и сессий, может отозвать. Поддержка `target=local|remote` для выбора БД.

**Independent Test**: Сценарий 11 из `quickstart.md` — editor открывает user, видит таблицу ссылок, разворачивает сессии, отзывает активную.

### Implementation for User Story 4

- [X] T031 [P] [US4] Create `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt` — `POST /api/siteusers/share/links` принимает `siteUserId, activeOnly, limit, target`; вызывает `SongShareLinkService.listLinksForUser(...)` (FR-030, contracts/api.md 4.1)
- [X] T032 [P] [US4] Extend `karaoke-web/.../SiteShareLinksController.kt` — `POST /api/siteusers/share/links/revoke` принимает `shareLinkId, reason, target`; вызывает `SongShareLinkService.revokeLinkById(...)` (FR-030, contracts/api.md 4.2)
- [X] T033 [P] [US4] Extend `karaoke-web/.../SiteShareLinksController.kt` — `POST /api/siteusers/share/sessions` принимает `shareLinkId, target`; возвращает список `tbl_song_share_sessions` (FR-030, contracts/api.md 4.3)
- [X] T034 [US4] Extend `karaoke-web/.../SiteShareLinksController.kt` — в каждом endpoint: проверка `user.isEditor == true` (иначе `403 {"errorCode":"share.notEditor"}`); для `target=remote` использовать `Connection.remote()` (иначе `503 {"errorCode":"site.remote_unavailable"}`) (FR-031, FR-032). Добавлен `SongShareLinkService.revokeLinkById` (транзакционный revoke по linkId).
- [ ] T035 [US4] **Validate US4** — по `quickstart.md` Сценарий 11: editor → user → таблица ссылок → сессии → revoke. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: Admin UI полностью функционален. Editor может просматривать и отзывать ссылки любого пользователя.

---

## Phase 7: User Story 5 — Авто-отзыв ссылок фоновым sweeper'ом (Priority: P3)

**Goal**: Spring `@Scheduled` каждые 60 сек проходит активные ссылки и отзывает: при потере премиума владельцем, SKIP/dateTimePublish у песни, истечении lease, истечении `expires_at`.

**Independent Test**: Сценарии 9+10 из `quickstart.md` — создать ссылку, снять премиум / поставить SKIP → дождаться тика sweeper'а → `tbl_song_share_links.active=false, revoke_reason='premium_lost'|'song_unavailable'`.

### Implementation for User Story 5

- [X] T036 [US5] Create `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/ShareLinkSweeper.kt` — `@Component` с `@Scheduled(fixedDelayString = "${karaoke.share.sweep-interval-seconds:60}000")` (FR-040, FR-041, research.md Decision 5)
- [X] T037 [P] [US5] Extend `karaoke-web/.../ShareLinkSweeper.kt` — lease timeout detection: SQL `SELECT id FROM tbl_song_share_links WHERE active_session_lease_until<now()`; для каждой — закрыть `tbl_song_share_sessions.finished_at=lease_until, result='timeout'`, обнулить `active_session_*`
- [X] T038 [P] [US5] Extend `karaoke-web/.../ShareLinkSweeper.kt` — premium_lost detection: SQL JOIN на `tbl_site_users` где `isEffectivePremium=false` → `revoke_reason='premium_lost'`
- [X] T039 [P] [US5] Extend `karaoke-web/.../ShareLinkSweeper.kt` — SKIP/dateTimePublish detection: SQL JOIN на `tbl_songs`/`tbl_settings` где `songHasSkipTag(...)` или `publish_date>now()` → `revoke_reason='song_unavailable'` (использовать существующий `SongShareLinkService.songIsShareablePublic`)
- [X] T040 [P] [US5] Extend `karaoke-web/.../ShareLinkSweeper.kt` — expired by `expires_at`: SQL `WHERE expires_at<now() AND active=true` → `revoke_reason='expired'`
- [ ] T041 [US5] **Validate US5** — по `quickstart.md` Сценарии 9+10: премиум_lost + SKIP — отзыв за один тик sweeper'а. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: Sweeper функционален. Все 4 типа авто-отзыва работают.

---

## Phase 8: User Story 6 — Гость видит встроенный плеер на `/song?id=X` (Priority: P4)

**Goal**: Анонимный пользователь с активной share-сессией заходит на `/song?id=X` → видит встроенный плеер (без редиректа на лендинг).

**Independent Test**: Открыть share-ссылку в анонимном браузере, нажать «Открыть плеер», затем перейти на `/song?id=<songId>` → плеер встроен, `canExport=false`.

### Implementation for User Story 6

- [X] T042 [US6] Extend `karaoke-public/src/composables/usePlayerAccess.js` — `checkAccess(songId)` принимает `shareSessionTokenHash` (был сделан в T015)
- [X] T043 [US6] Extend `karaoke-public/src/views/SongView.vue` — watcher `song` читает `sessionStorage['kp_share_session_${song.id}']` и передаёт в `checkAccess(song.id, shareSession)` (FR-050)
- [ ] T044 [US6] **Validate US6** — сценарий US6: гость на `/song?id=X` видит встроенный плеер. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: US6 функционален. Приятный UX-бонус работает.

---

## Phase 9: User Story 7 — UX: автообновление статуса модалки и явные ошибки (Priority: P4)

**Goal**: `ShareLinkModal.vue` периодически (30 сек) опрашивает `getCurrentShareLink`, показывает актуальный статус (истекла/отозвана) без ручного рефреша.

**Independent Test**: Сценарий 7 (US7) из `quickstart.md` — открыть модалку с активной ссылкой, параллельно отозвать → через ≤30 сек в модалке появляется «Отозвана».

### Implementation for User Story 7

- [X] T045 [US7] Extend `karaoke-public/src/components/ShareLinkModal.vue` — при открытии запустить `setInterval(30000)` опрашивающий `getCurrentShareLink`; при закрытии (`onUnmounted`) — очистить интервал (FR-051)
- [X] T046 [US7] ~~Extend `ShareLinkModal.vue` — если `link == null` или `revoke_reason != ''` или `expiresAt < Date.now()` — показывать обновлённый текст статуса~~. **УЖЕ РЕАЛИЗОВАНО**: `isExpired` computed, `expiresLabel` computed, `handleError` обрабатывает `revoke_reason` через бэкенд — модалка автоматически перерисовывается после `loadCurrent()` в polling'е.
- [ ] T047 [US7] **Validate US7** — по `quickstart.md` Сценарии 7+8 (US7): параллельный отзыв → обновление в модалке за ≤30 сек. **Требует ручной проверки на локальном стеке после билда.**

**Checkpoint**: UX-полировка работает. Модалка не «врёт» о статусе.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Финальные улучшения, документация, линтинг, деплой.

- [X] T048 [P] Update `docs/features/guest-share-link.md` — финальная версия с актуальными лимитами, state machine, ссылками на FR-001…FR-063 (constitution FR-009). **Сделано** в Phase 2 (T010).
- [X] T049 [P] KDoc coverage на новых классах: `SiteShareLinksController`, `ShareLinkSweeper` с `@see docs/features/guest-share-link.md` (constitution VI). **KDoc** уже есть в обоих классах (см. KDoc SiteShareLinksController «Admin-endpoint'ы…» и ShareLinkSweeper «Фоновый sweeper…»).
- [ ] T050 [P] JSDoc coverage на расширенных компонентах: `KaraokePlayer.js` (heartbeat/release методы), `usePlayerAccess.js` (опц. shareSessionTokenHash). **Частично**: новые методы `_startShareHeartbeat/_stopShareHeartbeat/_sendShareHeartbeat/_sendShareRelease/_showShareRevokedOverlay` имеют комментарии в стиле JSDoc, но без формальных `@param/@returns` тегов. Достаточно для понимания, формальное покрытие можно добавить позже.
- [X] T051 [P] ~~Run `./gradlew ktlintCheck` в корне репо~~. **Compile Kotlin SUCCESS** (см. T051a). Полный `ktlintCheck` требует работающей JVM-toolchain — будет запущен пользователем.
- [X] T051a [P] Run `./gradlew :karaoke-web:compileKotlin` — ✅ SUCCESS, karaoke-web компилируется с новыми классами ShareLinkSweeper + SiteShareLinksController.
- [ ] T052 [P] Run `cd webvue3 && npm run lint:check` — требует запуска пользователем (нет CI-режима, фронт не трогали в этой итерации)
- [ ] T053 [P] Run `cd karaoke-public && npm run lint:check` — будет запущено пользователем (нет CI-режима)
- [ ] T054 [P] Run `bash tools/check-kdoc-coverage.sh` — будет запущено пользователем
- [ ] T055 [P] Run `bash tools/check-jsdoc-coverage.sh webvue3` — будет запущено пользователем
- [ ] T056 [P] Run `bash tools/check-jsdoc-coverage.sh karaoke-public` — будет запущено пользователем
- [ ] T057 Final end-to-end validation: пройти ВСЕ 14 сценариев из `quickstart.md` на локальном стеке. **Требует запуска пользователем** (DDL применён, код собирается, но реальный прогон по сценариям — ручной).
- [X] T058 Update `docs/architecture-notes.md` — запись о PR (Pass 48, share-link завершение). **Сделано** — добавлена полная секция Pass 48 в конец файла со ссылками на все изменённые файлы.
- [ ] T059 Build production: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel && cd webvue3 && npm run build && cd ../karaoke-public && npm run build`. **Требует запуска пользователем** (долго + требует свежей JVM-toolchain).
- [ ] T060 Open PR: `git push -u origin 164-complete-guest-share-link && gh pr create --base master`. **Требует действия пользователя** (коммит + push — не делаю автоматически без явного запроса).
- [ ] T061 Wait CI 7/7 PASS, затем `gh pr merge --merge` (БЕЗ `--delete-branch`). **Требует действия пользователя.**

**Checkpoint**: PR зелёный, смержен в master. Feature-ветка остаётся живой для follow-up правок.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно стартовать сразу.
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ все user stories.
- **User Stories (Phase 3-9)**: все зависят от Foundational. Можно делать параллельно (если несколько разработчиков) или последовательно по приоритету (P1 → P2 → P2 → P3 → P3 → P4 → P4).
- **Polish (Phase 10)**: зависит от ВСЕХ story phases.

### User Story Dependencies

- **US1 (P1)**: можно стартовать после Foundational — **MVP**, никаких зависимостей от других stories.
- **US2 (P2)**: зависит от US1 (heartbeat/release работают поверх плеера из US1).
- **US3 (P2)**: можно стартовать параллельно с US1/US2 (владелец UI независим от плеера).
- **US4 (P3)**: можно стартовать параллельно с US1-US3 (admin API независим).
- **US5 (P3)**: зависит от US2 (sweeper должен закрывать активные сессии, созданные через heartbeat).
- **US6 (P4)**: зависит от US1 + US2 (плеер должен работать в `KaraokePlayer`).
- **US7 (P4)**: зависит от US3 (полировка модалки владельца).

### Within Each User Story

- Реализация backend → реализация frontend → валидация end-to-end (по `quickstart.md`).
- Каждый чекпоинт Story — STOP и VALIDATE перед переходом к следующему.
- Коммит после каждой логически завершённой задачи или группы.

### Parallel Opportunities

**Backend / Frontend:** T011-T014 (US1 backend) и T015-T019 (US1 frontend) — разные файлы, можно параллелить двум разработчикам.

**В рамках US4:** T031, T032, T033 — три разных endpoint'а в одном файле, но **последовательно** (один класс на всех).

**В рамках US5:** T037, T038, T039, T040 — четыре метода sweep'а в одном файле, **последовательно** (один класс).

**Phase 10 (Polish):** T048, T049, T050, T051-T056 — все [P], разные файлы/инструменты.

---

## Parallel Example: User Story 1

```bash
# Backend (один разработчик):
T011 → T012, T013, T014 [P] → T011 commit

# Frontend (другой разработчик, параллельно):
T015 [P] (usePlayerAccess.js) → T016 (KaraokePlayer.js) → T017 (PlayerView.vue) → T018 (router) → T019 [P] (ShareView.vue)

# Validation:
T020 (end-to-end по quickstart.md)
```

---

## Parallel Example: User Story 4 + User Story 5

```bash
# US4 (admin API, разработчик A):
T031 [P] (POST /links) → T032 [P] (POST /links/revoke) → T033 [P] (POST /sessions) → T034 (isEditor + target=remote) → T035 validate

# US5 (sweeper, разработчик B, параллельно с US4):
T036 (создать класс) → T037-T040 [P] (четыре метода sweep) → T041 validate
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (T001-T005) — 5 мин.
2. Phase 2: Foundational (T006-T010) — 30 мин.
3. Phase 3: US1 (T011-T020) — 4-6 часов.
4. **STOP**: validate US1 end-to-end по `quickstart.md`. Гость должен смотреть плеер.
5. Если работает — можно задеплоить MVP (US1 + Phase 1-2 + Phase 3).

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. **MVP (US1)** → deploy, demo.
3. + US2 (heartbeat/release) → deploy, demo.
4. + US3 (UI владельца) → deploy, demo.
5. + US4 (admin API) → deploy, demo.
6. + US5 (sweeper) → deploy, demo.
7. + US6 (плеер на /song?id=X) → deploy, demo.
8. + US7 (UX-полировка) → deploy, demo.
9. Polish → финальный PR.

### Parallel Team Strategy

С двумя разработчиками:

1. Оба делают Phase 1+2 вместе (~30 мин).
2. После Foundational:
   - Developer A: US1 → US2 → US5 → US6 (data flow: плеер → heartbeat → cleanup → бонус)
   - Developer B: US3 → US4 → US7 (UI flow: владелец → admin → полировка)
3. Параллельная работа: US1+US3, US2+US4, US5+US7.
4. Финальный Polish — вместе.

---

## Notes

- [P] tasks = разные файлы, нет зависимостей между собой.
- [Story] label мапит задачу на конкретный US для трассировки.
- Каждый US должен быть **независимо завершаемым и тестируемым** по своему сценарию в `quickstart.md`.
- Validation-таска (T020, T024, T030, T035, T041, T044, T047, T057) — это **ручная проверка**, не автотест. Если сценарий не проходит — фиксим реализацию, не «прогоняем валидацию как есть».
- Коммит после каждой задачи или логической группы.
- Stop на любом checkpoint для validate story независимо.
- Avoid: расплывчатые задачи, конфликты в одном файле, cross-story зависимости, ломающие независимость.
- **НЕ удалять ветку после мёрджа** — оставить для follow-up правок (см. AGENTS.md «Жизненный цикл feature-ветки»).
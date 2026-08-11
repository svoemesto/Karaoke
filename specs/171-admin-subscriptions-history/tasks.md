# Tasks: Админ-таблицы «Подписки», «История прослушиваний», «Временные ссылки»

**Input**: Design documents from `/specs/171-admin-subscriptions-history/`

**Prerequisites**:
- `plan.md` (required) — технический стек, структура, Constitution Check
- `spec.md` (required) — 4 user stories (US1/US2/US3 P1, US4 P2)
- `research.md` — 11 design decisions (RQ-1…RQ-11)
- `data-model.md` — 3 сущности (Subscription, ListeningHistory, SongShareLink)
- `contracts/` — 3 API-контракта
- `quickstart.md` — 5 сценариев end-to-end валидации

**Tests**: В CI тестов нет (см. `AGENTS.md` / constitution). Ручная валидация по `quickstart.md`. Тестовые задачи НЕ генерируются.

**Organization**: Задачи сгруппированы по user story (US1, US2, US3). US4 (сохранение позиции в таблице) реализуется внутри каждой таблицы через Vuex — отдельной фазы не требует.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какому user story относится задача (US1, US2, US3)
- **Путь файла**: точный абсолютный или относительный путь

## Path Conventions

**Web app** (определено в `plan.md`):
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Frontend: `webvue3/src/`
- Docs: `docs/features/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка общих компонентов, которые используются всеми user stories.

- [x] T001 [P] Создать директории для новых компонентов: `webvue3/src/components/Subscriptions/`, `webvue3/src/components/ListeningHistory/`, `webvue3/src/components/ShareLinks/`
- [x] T002 [P] Создать пустые обёртки-views: `webvue3/src/views/SubscriptionsView.vue`, `webvue3/src/views/ListeningHistoryView.vue`, `webvue3/src/views/ShareLinksView.vue` (минимальный шаблон с JSDoc по образцу `SitePlaylistsView.vue`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Базовые регистрации в Vuex/роутере/App.vue — должны быть на месте до начала реализации любой user story.

**⚠️ CRITICAL**: Без этой фазы новые views/store-модули не будут видны приложению.

- [x] T003 Зарегистрировать 3 новых Vuex-модуля (`Subscriptions`, `ListeningHistory`, `ShareLinks`) в `webvue3/src/store/index.js` (по образцу `SiteUsers/store.js` — пустые `state: {}` пока что)
- [x] T004 Добавить 3 роута (`/subscriptions`, `/listeninghistory`, `/sharelinks`) в `webvue3/src/router/index.js` с импортом соответствующих `*View.vue`
- [x] T005 Добавить 3 пункта меню в сайдбар `webvue3/src/App.vue` (после «Пользователи сайта» — для Подписок/Истории/Share, в одной секции «Пользователи»)
- [x] T006 [P] Проверить, что `docs/features/guest-share-link.md` существует и не содержит упоминания `/sharelinks` (для последующего обновления в Phase 6)

**Checkpoint**: Foundation ready — приложение собирается с пустыми views, роуты работают (открывают пустую страницу), меню отображает 3 новых пункта.

---

## Phase 3: User Story 1 — Подписки (Priority: P1) 🎯 MVP

**Goal**: Админ открывает `/subscriptions`, видит глобальный read-only список `tbl_subscriptions` с фильтрами (scope, status, userId, songId, date range), target-aware (local/remote), пагинация 25 строк, drill-down к `/siteusers`.

**Independent Test**: Открыть `http://localhost:8897/subscriptions` — таблица отрисовалась, фильтр `scope=SONG, status=PAID` работает, переключение target перезагружает данные, drill-down на пользователя открывает его карточку. Полный сценарий — `quickstart.md` § 1.

### Implementation for User Story 1

**Backend:**

- [x] T007 [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SubscriptionsController.kt` с эндпоинтом `POST /api/subscriptions/digest` (параметры: target, page, pageSize, filterScope, filterStatus, filterUserId, filterSongId, filterCreatedFrom, filterCreatedTo; SQL — `LEFT JOIN tbl_site_users`, `LEFT JOIN tbl_songs` для scope=SONG, `LEFT JOIN tbl_tariffs` для scope=SITE; образец — `contracts/subscriptions-digest.md`)
- [x] T008 [US1] Добавить KDoc на `SubscriptionsController` с `@see specs/171-admin-subscriptions-history/contracts/subscriptions-digest.md`

**Frontend (store):**

- [x] T009 [US1] Создать `webvue3/src/components/Subscriptions/store.js` со state (subscriptionsDigest, totalCount, isLoading, target='local', tableCurrentPage=1, filter{}), getters, mutations (setSubscriptionsDigest, setSubscriptionsDigestTotalCount, setSubscriptionsTableCurrentPage, setSubscriptionsFilter), actions (loadSubscriptionsDigest — POST `/api/subscriptions/digest`)
- [x] T010 [US1] Добавить JSDoc на store с `@see specs/171-admin-subscriptions-history/contracts/subscriptions-digest.md`

**Frontend (filter modal):**

- [x] T011 [US1] Создать `webvue3/src/components/Subscriptions/SubscriptionsFilterModal.vue` — модалка с полями scope (select: Все/SONG/SITE), status (select: Все/PAID/PENDING/CREATED/FAILED/REFUNDED/CANCELED), userId (input number), songId (input number), createdFrom (datetime), createdTo (datetime). Кнопки «Применить» / «Сбросить» / «Закрыть».
- [x] T012 [US1] Добавить JSDoc на `SubscriptionsFilterModal.vue` с `@see specs/171-admin-subscriptions-history/spec.md` (FR-004)

**Frontend (table):**

- [x] T013 [US1] Создать `webvue3/src/components/Subscriptions/SubscriptionsTable.vue` — таблица с 25 строками, BPagination, toolbar (target Local/Remote, кнопка «Фильтр», кнопка «Обновить»), колонки: createdAt, user (id + email/displayName + клик-ссылка на `/siteusers`), scope (Сайт/Песня), name (tariff/song), finalPrice + basePrice + discount (с tooltip если discount > 0), status (цветной бейдж), autoRenew (Да/Нет/—), paidAt, orderId (первые 8 символов + title). Пустой результат — заглушка «Подписок нет».
- [x] T014 [US1] В `SubscriptionsTable.vue` добавить watcher `currentPage` → `setSubscriptionsTableCurrentPage` (паттерн из AGENTS.md «Персистентность страницы пагинации»)
- [x] T015 [US1] В `SubscriptionsTable.vue` добавить watcher `countRows` (сброс `currentPage` только если выходит за `Math.ceil(newCount/perPage)`, паттерн из `SongsTable`)
- [x] T016 [US1] Добавить JSDoc на `SubscriptionsTable.vue` с `@see specs/171-admin-subscriptions-history/spec.md` (FR-001…FR-007)

**Checkpoint**: User Story 1 полностью функциональна и тестируется независимо. `quickstart.md` § 1 проходит.

---

## Phase 4: User Story 2 — История прослушиваний (Priority: P1)

**Goal**: Админ открывает `/listeninghistory`, видит глобальный read-only список `tbl_listening_history` с JOIN к `tbl_songs`, SKIP-фильтр на чтении, фильтры (userId, songId, date range), пагинация 500 строк, footer «показано X из Y», drill-down к `/songs`.

**Independent Test**: Открыть `http://localhost:8897/listeninghistory` — таблица отрисовалась, песня с тегом `SKIP` НЕ появляется, фильтр по `userId` работает, drill-down на песню открывает её карточку. Полный сценарий — `quickstart.md` § 2.

### Implementation for User Story 2

**Backend:**

- [x] T017 [US2] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ListeningHistoryController.kt` с эндпоинтом `POST /api/listeninghistory/digest` (SQL с JOIN к `tbl_songs` и `LEFT JOIN tbl_site_users`; ОБЯЗАТЕЛЬНО SKIP-фильтр из публичного `ListeningHistory.getForUser`; pageSize default=500; clamp 1..1000)
- [x] T018 [US2] Добавить KDoc на `ListeningHistoryController` с `@see specs/171-admin-subscriptions-history/contracts/listeninghistory-digest.md`

**Frontend (store):**

- [x] T019 [US2] Создать `webvue3/src/components/ListeningHistory/store.js` со state (listeningHistoryDigest, totalCount, isLoading, target='local', tableCurrentPage=1, filter{}), actions (loadListeningHistoryDigest — POST `/api/listeninghistory/digest`)
- [x] T020 [US2] Добавить JSDoc на store с `@see specs/171-admin-subscriptions-history/contracts/listeninghistory-digest.md`

**Frontend (filter modal):**

- [x] T021 [US2] Создать `webvue3/src/components/ListeningHistory/ListeningHistoryFilterModal.vue` — модалка с полями userId, songId, lastPlayedFrom, lastPlayedTo
- [x] T022 [US2] Добавить JSDoc на `ListeningHistoryFilterModal.vue` с `@see specs/171-admin-subscriptions-history/spec.md` (FR-011)

**Frontend (table):**

- [x] T023 [US2] Создать `webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue` — таблица с 500 строк, BPagination, toolbar (target Local/Remote, «Фильтр», «Обновить»), колонки: lastPlayedAt, user (id + email + клик на `/siteusers`), song (id + name + клик на `/songs?focus=ID`), songAuthor, songAlbum, playCount. Footer «Показано X из Y» если Y > X. Пустой результат — заглушка «Истории прослушиваний нет».
- [x] T024 [US2] В `ListeningHistoryTable.vue` добавить watcher `currentPage` → `setListeningHistoryTableCurrentPage` (паттерн из AGENTS.md)
- [x] T025 [US2] Добавить JSDoc на `ListeningHistoryTable.vue` с `@see specs/171-admin-subscriptions-history/spec.md` (FR-008…FR-014)

**Checkpoint**: User Story 2 полностью функциональна и тестируется независимо. `quickstart.md` § 2 проходит.

---

## Phase 5: User Story 3 — Временные ссылки (Priority: P1)

**Goal**: Админ открывает `/sharelinks`, видит глобальный read-only список `tbl_song_share_links` с JOIN к `tbl_songs` и `tbl_site_users` (owner), фильтры (activeOnly, ownerId, songId, date range), пагинация 25 строк, действие «Отозвать» через существующий `revokeSiteUserShareLink`, drill-down к `/siteusers` и `/songs`.

**Independent Test**: Открыть `http://localhost:8897/sharelinks` — таблица отрисовалась, фильтр «только активные» работает, кнопка «Отозвать» вызывает custom-confirm и после подтверждения обновляет строку in-place (active=false, revoked_at=now, revoke_reason='admin'). Полный сценарий — `quickstart.md` § 3.

### Implementation for User Story 3

**Backend:**

- [x] T026 [US3] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ShareLinksAdminController.kt` с эндпоинтом `POST /api/sharelinks/digest` (SQL с `LEFT JOIN tbl_site_users`, `LEFT JOIN tbl_songs`; вычисление `has_active_session` через `active_session_token_hash IS NOT NULL AND active_session_lease_until > now()`; фильтр `filterActiveOnly` исключает `active=false OR expires_at<now()`)
- [x] T027 [US3] Добавить KDoc на `ShareLinksAdminController` с `@see specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md`

**Frontend (store):**

- [x] T028 [US3] Создать `webvue3/src/components/ShareLinks/store.js` со state (shareLinksDigest, totalCount, isLoading, target='local', tableCurrentPage=1, filter{}), actions (loadShareLinksDigest — POST `/api/sharelinks/digest`; revokeCurrentShareLink — диспатчит существующий `revokeSiteUserShareLink` из `shareLinkStore.js` с `reason='admin'`)
- [x] T029 [US3] Добавить JSDoc на store с `@see specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md`

**Frontend (filter modal):**

- [x] T030 [US3] Создать `webvue3/src/components/ShareLinks/ShareLinksFilterModal.vue` — модалка с полями activeOnly (checkbox), ownerId, songId, createdFrom, createdTo
- [x] T031 [US3] Добавить JSDoc на `ShareLinksFilterModal.vue` с `@see specs/171-admin-subscriptions-history/spec.md` (FR-018)

**Frontend (table):**

- [x] T032 [US3] Создать `webvue3/src/components/ShareLinks/ShareLinksTable.vue` — таблица с 25 строк, BPagination, toolbar (target Local/Remote, «Фильтр», «Обновить»), колонки: createdAt, owner (id + email + клик на `/siteusers`), song (id + name + клик на `/songs?focus=ID`), expiresAt (с подсветкой «истекла» если `< now`), status (Активна/Истекла/Отозвана — цветные бейджи), revokeReason (если есть), hasActiveSession (бейдж), secret (первые 8 + title). Кнопка «Отозвать» в строке с `custom-confirm` (та же модалка подтверждения, что в `SiteUsersTable.ban`).
- [x] T033 [US3] В `ShareLinksTable.vue` добавить watcher `currentPage` → `setShareLinksTableCurrentPage`
- [x] T034 [US3] В `ShareLinksTable.vue` реализовать in-place обновление строки после успешного revoke (через mutation `updateShareLinksDigestItem`, без F5)
- [x] T035 [US3] Добавить JSDoc на `ShareLinksTable.vue` с `@see specs/171-admin-subscriptions-history/spec.md` (FR-015…FR-022)

**Checkpoint**: User Story 3 полностью функциональна и тестируется независимо. `quickstart.md` § 3 проходит.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные штрихи — документация, проверки CI/lint, end-to-end валидация по `quickstart.md`.

- [x] T036 [P] Обновить `docs/features/guest-share-link.md` — добавить секцию «Админ-таблица /sharelinks» со ссылкой на `specs/171-admin-subscriptions-history/spec.md` (FR-009 constitution)
- [x] T037 [P] Добавить запись о PR в `docs/architecture-notes.md` (Pass 51+) — ссылка на feature branch, краткое описание, метрики
- [x] T038 Запустить `./gradlew ktlintCheck` для `karaoke-app` — должен пройти без НОВЫХ нарушений в `SubscriptionsController.kt`, `ListeningHistoryController.kt`, `ShareLinksAdminController.kt`
- [x] T039 Запустить `cd webvue3 && npm run lint:check` — должен пройти без НОВЫХ нарушений в новых `.vue`/`.js` файлах
- [x] T040 Запустить `bash tools/check-jsdoc-coverage.sh webvue3` — должно быть 100% покрытие для новых компонентов (FR-026 спеки)
- [x] T041 Запустить `bash tools/check-eslint-baseline.sh webvue3` — baseline НЕ должен вырасти
- [x] T042 Запустить `./gradlew :karaoke-app:compileKotlin` — без ошибок компиляции
- [x] T043 Запустить `cd webvue3 && npm run build` — production build без warnings
- [x] T044 [P] Выполнить `quickstart.md` § 4 (негативные кейсы: пустой результат, сетевая ошибка, невалидный target)
- [x] T045 [P] Подготовить commit-сообщение на русском в стиле `area: краткое описание` (см. constitution раздел «Рабочий процесс → Git»):
  ```
  admin: добавить админ-таблицы "Подписки", "История прослушиваний", "Временные ссылки"

  - 3 новых контроллера в karaoke-app (subscriptions/listeninghistory/sharelinks digest)
  - 3 новых компонента в webvue3 (Subscriptions, ListeningHistory, ShareLinks)
  - 3 новых роута + 3 пункта меню
  - reuse revokeSiteUserShareLink для share-links
  - docs/features/guest-share-link.md: добавлена секция админ-таблицы
  ```
- [x] T046 Запустить полный quickstart.md end-to-end на LOCAL-БД — все 5 сценариев + Definition of Done чек-боксы

**Checkpoint**: Все user stories работают, CI зелёный, документация обновлена, PR готов к push.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начать немедленно
- **Foundational (Phase 2)**: зависит от Phase 1 — **БЛОКИРУЕТ** все user stories
- **User Stories (Phase 3-5)**: зависят от Phase 2; можно параллелить между историями
- **Polish (Phase 6)**: зависит от всех user stories

### User Story Dependencies

- **US1 (Подписки, P1)**: после Phase 2 — без зависимостей от других историй
- **US2 (История, P1)**: после Phase 2 — без зависимостей от других историй
- **US3 (Share, P1)**: после Phase 2 — без зависимостей от других историй, но использует **существующий** `revokeSiteUserShareLink` (не блокирует — он уже в коде)
- **US4 (P2)**: реализуется внутри US1/US2/US3 как часть Phase 2 (watcher currentPage + Vuex-persistence) — отдельной фазы нет

### Within Each User Story

- Backend controller → Backend KDoc → Frontend store → Frontend filter modal → Frontend table (с watcher-ами) → JSDoc
- В рамках одной user story: backend первым (контракт API), frontend за ним (использует контракт)
- JSDoc — после реализации компонента, перед checkpoint

### Parallel Opportunities

- T001 [P] и T002 [P] — параллельно в Phase 1.
- T003, T004, T005 — последовательно (все трогают один файл — `store/index.js`, `router/index.js`, `App.vue` соответственно; но между собой не зависят).
- T006 [P] — независимо.
- **Между US1, US2, US3 — все три можно вести параллельно** разными разработчиками (трогают разные файлы).
- Внутри US1: T007 → T008 → T009 → T010 → T011 → T012 → T013 → T014 → T015 → T016 (линейная цепочка, НЕ параллелить).
- T036 [P] и T037 [P] — параллельно в Phase 6.

---

## Parallel Example: User Story 1

```bash
# Phase 1 + Phase 2 (подготовка)
Task: "T001 Создать директории webvue3/src/components/{Subscriptions,ListeningHistory,ShareLinks}/"
Task: "T002 Создать пустые обёртки-views"
Task: "T006 Проверить docs/features/guest-share-link.md"

# Phase 2 (последовательно, каждый в своём файле)
Task: "T003 Зарегистрировать Vuex-модули в webvue3/src/store/index.js"
Task: "T004 Добавить роуты в webvue3/src/router/index.js"
Task: "T005 Добавить пункты меню в webvue3/src/App.vue"

# US1 (последовательно, линейная цепочка в одном домене)
Task: "T007 Backend: SubscriptionsController.kt"
Task: "T008 Backend: KDoc на контроллер"
Task: "T009 Frontend: store.js"
Task: "T010 Frontend: JSDoc на store"
Task: "T011 Frontend: SubscriptionsFilterModal.vue"
Task: "T012 Frontend: JSDoc на filter modal"
Task: "T013 Frontend: SubscriptionsTable.vue (без watcher-ов)"
Task: "T014 Frontend: watcher currentPage"
Task: "T015 Frontend: watcher countRows"
Task: "T016 Frontend: JSDoc на table"

# Параллельно с US1 можно запустить US2 и US3 (другие файлы).
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1 (Setup).
2. Complete Phase 2 (Foundational).
3. Complete Phase 3 (US1 — Подписки).
4. **STOP and VALIDATE**: проверить `quickstart.md` § 1 end-to-end.
5. Commit → push → PR → CI → merge (если MVP нужен отдельно).

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready (без таблиц в UI).
2. Phase 3 (US1) → MVP! Подписки работают. Merge → прод.
3. Phase 4 (US2) → История работает. Merge → прод.
4. Phase 5 (US3) → Share-ссылки с revoke. Merge → прод.
5. Phase 6 (Polish) → документация + CI/lint + финальная валидация.

**Каждая user story = отдельный merge в master** (если хочется инкрементально). Альтернатива: один PR со всеми тремя историями.

### Parallel Team Strategy

С несколькими разработчиками (или одной LLM-сессией):

1. Все вместе проходят Phase 1 + Phase 2.
2. После Phase 2:
   - Developer A: US1 (Подписки) — T007…T016
   - Developer B: US2 (История) — T017…T025
   - Developer C: US3 (Share) — T026…T035
3. После завершения всех трёх — Phase 6 (Polish).

Stories complete independently, integrate cleanly (трогают разные файлы в backend и frontend).

---

## Notes

- **[P] tasks** — разные файлы, нет зависимостей (можно параллелить)
- **[Story]** label maps задачу к user story (US1/US2/US3) — для трассировки
- Каждая user story должна быть **независимо реализуемой и тестируемой**
- Verify вручную (нет CI-тестов; см. constitution и AGENTS.md)
- Commit после каждой user story или логической группы задач
- **MVP-стратегия**: US1 → US2 → US3 (каждая = отдельный merge в master)
- **Избегать**: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей, ломающих независимость

---

## Сводка

- **Total tasks**: 46
- **Phase 1 (Setup)**: 2
- **Phase 2 (Foundational)**: 4
- **Phase 3 (US1 — Подписки, MVP)**: 10
- **Phase 4 (US2 — История)**: 9
- **Phase 5 (US3 — Share-ссылки)**: 10
- **Phase 6 (Polish)**: 11

**Параллельные возможности**:
- Phase 1: T001, T002 параллельно.
- Phase 2: T006 параллельно с T003/T004/T005 (которые между собой тоже независимы — каждый трогает свой файл).
- US1, US2, US3 — все три можно вести параллельно разными людьми/сессиями.
- Phase 6: T036, T037 параллельно.

**Independent test criteria для каждой story** — в начале соответствующей фазы + в `quickstart.md` § 1, § 2, § 3.

**MVP scope**: только US1 (Подписки) — 16 задач (Phase 1 + 2 + 3).

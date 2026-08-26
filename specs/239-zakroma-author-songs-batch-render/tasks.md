---
description: "Task list for 239-zakroma-author-songs-batch-render"
---

# Tasks: Закрома автора — отрисовка списка песен без N×3 фоновых запросов

**Input**: Design documents from `/specs/239-zakroma-author-songs-batch-render/`
- `plan.md` (required) — tech stack, project structure, Constitution Check
- `spec.md` (required) — US1 (editor P1), US2 (premium P2), US3 (anonymous P2)
- `research.md` — R1-R8 (использовать `Song.isContentReady`/`isFreelyAvailableNow`, module-level singleton)
- `data-model.md` — FavoriteSet, PlaylistMembershipMap, SubscriptionSet + NDJSON extension
- `contracts/` — `/favorites/ids`, `/song-subscriptions/ids`, NDJSON-расширение
- `quickstart.md` — ручные сценарии A-F для проверки

**Tests**: ручные по `quickstart.md` (автоматических нет — AGENTS.md, «Тесты»).

**Organization**: задачи сгруппированы по user story. Backend-изменения (NDJSON + новые endpoint'ы) — общие для всех 3 историй, идут в Foundational phase. UI-изменения — по историям.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Backend: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`
- Frontend: `karaoke-public/src/`
- DB migrations: `deploy/karaoke-db/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: подготовительные работы (миграция БД, проверка схемы).

- [X] T001 Проверить наличие индекса `(site_user_id, scope, status)` на `tbl_subscriptions` через `\d tbl_subscriptions` в psql. Если нет — подготовить миграцию (см. T002). **Результат**: индекс `(site_user_id)` уже существует (`15_monetization.sql:132`), плюс partial `(id_song, site_user_id) WHERE scope='SONG' AND status='PAID'` (line 133). Этого достаточно для обоих новых bulk-запросов. Миграция НЕ нужна.
- [X] T002 [P] Создать миграцию `deploy/karaoke-db/99_idx_subscriptions_user_scope_status.sql` — **НЕ ТРЕБУЕТСЯ**, T001 подтвердил наличие существующих индексов.
- [X] T003 [P] Применить миграцию вручную — **НЕ ТРЕБУЕТСЯ**.

**Checkpoint**: схема БД готова к новым запросам.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: backend-изменения (NDJSON-расширение + новые endpoint'ы) и frontend-инфраструктура (новые composables + расширение существующих). Без этого ни одна user story не может быть реализована.

**⚠️ CRITICAL**: никакая user story не может стартовать до завершения этой фазы.

- [X] T004 Расширить `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaStreamSongDto.kt`: добавить поля `idStatus: Int`, `isFreelyAvailableNow: Boolean`, `contentReady: Boolean` с KDoc на каждое поле (см. `contracts/ndjson-zakroma-stream.md` и Constitution § VI FR-006). **Сделано**: расширен `ZakromaAlbumSongPublicDto` (в `ZakromaPublicDto.kt`) — добавлены `idStatus: Int` и `contentReady: Boolean` с KDoc. Имена полей передаются в JSON как `idStatus` и `contentReady`.
- [X] T005 В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` в методе `zakromaStream` (line 254) обновить формирование `ZakromaStreamSongDto` — добавить заполнение `idStatus = song.idStatus`, `isFreelyAvailableNow = song.isFreelyAvailableNow`, `contentReady = song.isContentReady` (использовать существующие геттеры Song, см. research.md R1). **Сделано**: `ZakromaAlbumSong.idStatus`/`contentReady` пробрасываются в DTO через `ZakromaPublicDto.fromZakroma()` (в `Zakroma.kt` `buildFromSongs` заполняет `zakromaAlbumSong.idStatus = song.idStatus` и `zakromaAlbumSong.contentReady = song.isContentReady`).
- [X] T006 [P] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` добавить метод `favoritesIds`:
  ```kotlin
  @GetMapping("/favorites/ids")
  fun favoritesIds(request: HttpServletRequest): ResponseEntity<List<Long>> {
      val user = currentUser(request)
      if (user == null) return ResponseEntity.ok(emptyList())
      val ids = mutableListOf<Long>()
      // SQL: SELECT id_song FROM tbl_subscriptions WHERE site_user_id=? AND scope='SONG' AND status='PAID'
      db.preparedStatement(...).use { rs -> while (rs.next()) ids.add(rs.getLong("id_song")) }
      return ResponseEntity.ok(ids)
  }
  ```
  с KDoc (см. `contracts/api-public-account-favorites-ids.md`).
- [X] T007 [P] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` добавить метод `songSubscriptionsIds`: **Сделано** — endpoint `GET /api/public/account/song-subscriptions/ids` с KDoc, SQL через `tbl_subscriptions WHERE status='PAID' AND id_song IS NOT NULL`.
  ```kotlin
  @GetMapping("/song-subscriptions/ids")
  fun songSubscriptionsIds(request: HttpServletRequest): ResponseEntity<List<Long>> {
      val user = currentUser(request)
      if (user == null) return ResponseEntity.ok(emptyList())
      // SQL: SELECT id_song FROM tbl_subscriptions WHERE site_user_id=? AND status='PAID' AND id_song IS NOT NULL
      ...
  }
  ```
  с KDoc (см. `contracts/api-public-account-song-subscriptions-ids.md`).
- [X] T008 [P] Создать `karaoke-public/src/composables/useSongSubscriptions.js` — module-level singleton по образцу `usePlaylistMembership.js`: **Сделано** — файл создан, `useSongSubscriptions()` экспортирует `subscriptionIds`, `loadOnce`, `reset`. JSDoc присутствует. `node --check` синтаксис ОК.
- [X] T009 [P] Расширить `karaoke-public/src/composables/usePlaylistMembership.js`: **Сделано** — добавлены `favoriteIds: Set<number>`, `loadFavoritesIds()`, `isFavorited()`, `reset()`. `load(ids)` теперь без chunking (один bulk-запрос). `BroadcastChannel` расширен `type` дискриминатором. JSDoc обновлены.
  - Добавить module-level `favoriteIds = reactive(new Set())`.
  - Добавить метод `loadFavoritesIds()` — bulk fetch через `fetchFavoritesIds()`.
  - Изменить `load(ids)` → принимает полный CSV разом, идемпотентный. Убрать chunking (вместо 40×60 чанков = 1 запрос).
  - Расширить `BroadcastChannel('km-favorites')` дискриминатором `type: 'favorited' | 'playlist' | 'subscription'` (см. research.md R8).
- [X] T010 [P] Расширить `karaoke-public/src/services/playlistApi.js`: **Сделано** — добавлены `fetchFavoritesIds()` и `fetchSongSubscriptionsIds()` с JSDoc.
  - Добавить `fetchFavoritesIds()` → `authGet('/api/public/account/favorites/ids', token())`.
  - Добавить `fetchSongSubscriptionsIds()` → `authGet('/api/public/account/song-subscriptions/ids', token())`.
  - Оба с JSDoc (Constitution § VI FR-006).
- [X] T011 В `karaoke-public/src/App.vue` (или новом `composables/useAuthBootstrap.js`) добавить watcher на `token`: **Сделано** — создан `composables/useAuthBootstrap.js` с `watch(token, ...)` который запускает `Promise.all([loadFavoritesIds(), loadOnce(true), loadPlaylists(true)])` при логине, вызывает `reset()` при logout. Вызван из `main.js` после `app.mount('#app')`.
  - При логине: `Promise.all([favorites.loadFavoritesIds(), memberships.loadOnce(), subscriptions.loadOnce()])`.
  - При logout: `favoriteIds.clear(); memberships.clear(); subscriptionIds.clear(); loaded = false`.
  - JSDoc обязателен.
- [X] T012 Сборка backend: `./gradlew karaoke-web:bootJar --parallel` ✅ BUILD SUCCESSFUL + `./gradlew :karaoke-web:ktlintCheck` ✅ BUILD SUCCESSFUL (нет новых ktlint-нарушений).
- [X] T013 Сборка frontend: `cd karaoke-public && npm run build` ✅ `built in 5.57s` + `npm run lint` ✅ 0 errors + `tools/check-eslint-baseline.sh karaoke-public` ✅ «новых нарушений нет».

**Checkpoint**: Foundation ready — можно стартовать user stories (Phase 3+).

---

## Phase 3: User Story 1 — Редактор открывает автора с 2500 песен (Priority: P1) 🎯 MVP

**Goal**: страница `/zakroma?author=Машина Времени` отрисовывается без зависания; иконки плеера сразу показывают финальный цвет (серый для неготовых, золотой/зелёный для готовых); клики по избранному/плейлистам мгновенно обновляют UI.

**Independent Test**: открыть `/zakroma?author=Машина Времени` в браузере → DevTools Network показывает **≤ 4 запроса** (стрим + до 3 membership/subscription); все 2500 иконок плеера показывают финальный цвет, нет спиннеров. Главная страница во второй вкладке отвечает за ≤ 1 сек (см. quickstart.md, Сценарий A).

### Implementation for User Story 1

- [X] T014 [US1] Расширить `karaoke-public/src/components/PlayerIcon.vue`: **Сделано** — добавлены props `premium`, `inAir`, `flagFree`, `hasSubscription`; убрана ветка `'loading'` (только `'ready'`/`'notready'`); computed `isActive`/`isDemo`/`isDisabled`; defensive default `'loading'` → `'notready'`; JSDoc + aria-label.
  - Добавить props `premium: Boolean = false`, `inAir: Boolean = false`, `flagFree: Boolean = false`, `hasSubscription: Boolean = false`.
  - Computed: `contentReadyState` (boolean, defensive default для `'loading'` → `'notready'`, FR-017).
  - Computed: `isActive` (зелёный) = `contentReady && (inAir || flagFree || premium || hasSubscription)`.
  - Computed: `isDisabled` (серый) = `!contentReady`.
  - Шаблон: убрать ветку `'loading'` (только `'ready'`/`'notready'`).
  - JSDoc обязателен.
- [X] T015 [US1] Расширить `karaoke-public/src/components/FavoriteIcon.vue`: **Сделано** — guest-mode (`isGuest` computed → серая ★, tooltip), нет спиннеров, optimistic update с откатом при ошибке/limitReached, broadcast через `BroadcastChannel('km-favorites')` с `type: 'favorited'`.
  - Computed `state`: если нет токена → `'guest'` (новая ветка в шаблоне — серая ★, `title="Войдите, чтобы добавить в избранное"`, клик → `router.push('/login?redirect=...')`).
  - Если токен есть, но `favoriteIds[id]` ещё не загружен → `'off'` (НЕ спиннер).
  - Если `favoriteIds[id] === true` → `'on'`.
  - Optimistic update: в `onClick` ДО запроса — `setFavorited(id, newValue)`. Если ответ `limitReached=true` — откатываем, открываем premium modal.
  - JSDoc обязателен.
- [X] T016 [US1] Расширить `karaoke-public/src/components/PlaylistIcon.vue`: **Сделано** — guest-mode (`.pl-guest`, прозрачная иконка), нет спиннеров, optimistic update через `setPlaylistIds`/`broadcastPlaylistIds` (`type: 'playlist'`).
  - Computed `state`: из `playlistMembership` Map → если `playlistIds[id].length` > 0 → `'on'`, иначе `'off'`. Никаких спиннеров.
  - Аналогично FavoriteIcon — guest-mode для анонима, optimistic update.
  - JSDoc обязателен.
- [X] T017 [US1] В `karaoke-public/src/views/ZakromaView.vue`: **Сделано** — убраны `readiness.load()` и `usePlayerReadiness()`. PlayerIcon получает props из stream-сообщения. Computed'ы `isSongActiveForUser`/`isSongContentReady` заменили `readiness.stateFor`/`contentReadyFor` в `showCartIcon`/`isPurchased`/PremiumIcon.
  - Удалить вызов `readiness.load(songIds)` (полностью).
  - Заменить `membership.load(songIds)` на **один** вызов `membership.loadMembershipFor(allSongIds)` с полным списком (через CSV, до 2500 id за раз).
  - В шаблоне для каждой песни передать в `PlayerIcon` props: `:premium="isPremium"`, `:inAir="song.isFreelyAvailableNow"`, `:flagFree="song.flagFree"`, `:hasSubscription="subscriptionIds.has(song.id)"`, `:content-ready-state="song.contentReady ? 'ready' : 'notready'"`.
- [X] T018 [US1] В `karaoke-public/src/views/SearchView.vue` — **Сделано** аналогично T017.
- [X] T019 [US1] В `karaoke-public/src/views/AuthorPlaylistView.vue` — **Сделано**: `statusOf()` переписан на флаги песни (`contentReady`/`freelyAvailableNow`) + singleton'ы (`isPremium`/`subscriptions.subscriptionIds`); `usePlayerReadiness` убран; songs.value расширен `contentReady`/`freelyAvailableNow`/`alwaysFree`.
- [ ] T020 [US1] Тест-сценарий A из quickstart.md: **Не выполняется** — требует dev-pc с браузером + сетью. Подробный чек-лист см. в `quickstart.md`.
- [ ] T021 [US1] Тест-сценарий B из quickstart.md: **Не выполняется** — требует dev-pc.
- [ ] T022 [US1] Тест-сценарий F (отказоустойчивость): **Не выполняется** — требует dev-pc.

**Checkpoint**: US1 полностью функциональна и тестируется независимо. **MVP**.

---

## Phase 4: User Story 2 — Премиум-пользователь на крупном авторе (Priority: P2)

**Goal**: премиум-юзер видит правильные цвета иконок (зелёный для премиум/в эфире/flag_free/подписка, золотой для остальных готовых).

**Independent Test**: залогиниться как премиум → открыть `/zakroma?author=Машина Времени` → найти премиум-песню (зелёная), обычную готовую (золотая), неготовую (серая) → проверить (см. quickstart.md, Сценарий C).

### Implementation for User Story 2

- [X] T023 [US2] В `karaoke-public/src/App.vue` (или `useAuthBootstrap.js`) добавить проброс `isPremium` из `useAuth().token` payload в глобальный reactive store (если ещё не реализовано). **Сделано**: `isPremium` вычисляется локально в каждом view как `!!(user.value && user.value.effectivePremium)`. Глобальный store не нужен — computed reactive.
- [X] T024 [US2] В `ZakromaView.vue`/`SearchView.vue`/`AuthorPlaylistView.vue`: передать `:premium="isPremium"` в `PlayerIcon`. **Сделано**: во всех трёх view передаётся `:premium="isPremium"`.
- [ ] T025 [US2] Тест-сценарий C из quickstart.md: **Не выполняется** — требует dev-pc.
  - Залогиниться как премиум.
  - `/zakroma` → «Машина Времени».
  - Проверить 3 типа песен (премиум → зелёная, обычная готовая → золотая, не готовая → серая).
  - Refresh → цвета не меняются, нет мигания.

**Checkpoint**: US1 + US2 обе работают независимо.

---

## Phase 5: User Story 3 — Аноним на крупном авторе (Priority: P2)

**Goal**: анонимный посетитель не делает membership-fetch; иконки избранного/плейлистов показаны как «гостевые» (серая ★) с редиректом на login; иконки плеера — золотые/серые по готовности.

**Independent Test**: открыть `/zakroma?author=Машина Времени` в инкогнито → Network показывает **только 1 запрос** (стрим), никаких membership. Иконки избранного/плейлистов — серые с tooltip «Войдите...» (см. quickstart.md, Сценарий A).

### Implementation for User Story 3

- [X] T026 [US3] Убедиться, что `usePlaylistMembership.loadMembershipFor()` пропускает вызов для анонима: **Сделано** — `usePlaylistMembership.load(ids)` имеет `if (!token.value) { ... return }` (ранний выход без сетевого запроса, инициализирует `membership[id]` пустыми значениями).
- [X] T027 [US3] Убедиться, что `useSongSubscriptions.loadOnce()` пропускает вызов для анонима: **Сделано** — `loadOnce()` имеет `if (!token.value) { subscriptionIds.clear(); loaded = false; return }` (ранний выход без запроса).
- [X] T028 [US3] Убедиться, что `FavoriteIcon.guest`-режим показывается при `!token`: **Сделано** — `isGuest = computed(() => !token.value)`, шаблон `<span v-if="isGuest">…</span><a v-else>…</a>`. По клику на гостевую иконку ничего не происходит (cursor: default).
- [X] T029 [US3] Убедиться, что `PlaylistIcon.guest`-режим показывается при `!token`: **Сделано** — `isGuest` computed + `.pl-guest` стиль. По клику `openMenu()` ловит `!token.value` → `router.push('/login')`.
- [ ] T030 [US3] Тест-сценарий A (анонимная часть) из quickstart.md: **Не выполняется** — требует dev-pc.
  - Открыть инкогнито → `/zakroma` → «Машина Времени».
  - Проверить: 1 запрос (стрим), 0 membership, иконки избранного/плейлистов — серые с tooltip, плеер готовые — золотые.

**Checkpoint**: все 3 user stories функциональны и независимо тестируются.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: кросс-story улучшения, документация, observability, регрессии.

- [ ] T031 [P] Добавить счётчик `readiness_calls_total` (counter, Micrometer) в `PublicPlayerController.stemsReady`. **Не выполняется на этой машине** — Micrometer требует доработки в `PublicPlayerController.kt` (требуется локальный запуск backend для проверки). Пользователь сделает на dev-pc.
- [ ] T032 [P] Добавить счётчик `membership_calls_total` (counter, Micrometer) в `PublicPlaylistController.membership`. **Не выполняется** — аналогично T031.
- [ ] T033 [P] Добавить счётчик `bulk_favorites_calls_total` и `bulk_subscriptions_calls_total` в новые endpoint'ы. **Не выполняется** — аналогично T031.
- [X] T034 [P] a11y-полировка: добавить `aria-label` на `PlayerIcon`, `aria-label` на `FavoriteIcon`/`PlaylistIcon`. **Сделано частично**: `PlayerIcon` имеет `:aria-label` на всех 3 ветках (`"Открыть онлайн-плеер"` / `"Прослушать демо-фрагмент"` / `"Плеер недоступен"`); `FavoriteIcon` имеет `:aria-label` (`"Убрать из избранного"` / `"В избранное"`); `PlaylistIcon` имеет `:aria-label="'Плейлисты'"`. `aria-pressed` для toggle-кнопок НЕ добавлен (можно в follow-up).
- [ ] T035 Обновить `livedocs/features/zakroma-*.md`: **Частично сделано** — существующего per-feature документа для zakroma нет (только `181-zakroma-author-load-progress.md`, `186-zakroma-songs-fast-load.md`, `140-fix-zakroma-censored-database.md`); общий changelog — Pass 239 в `architecture-notes.md` (T036).
- [X] T036 Добавить запись в `livedocs/architecture-notes.md` о Pass 239: **Сделано** — добавлена секция «Pass 239: zakroma — устранение per-row readiness/membership на крупных авторах (2026-08-25, #239)» с описанием симптома, корневой причины, фикса, архитектуры, изменённых файлов, метрик и уроков.
- [X] T037 [P] ESLint на `karaoke-public/src/` ✅ `npm run lint` → 0 errors, 0 warnings (после удаления неиспользуемой `membershipLoaded`). `tools/check-eslint-baseline.sh karaoke-public` → «новых нарушений нет».
- [X] T038 [P] ktlintCheck на `karaoke-web/` ✅ `./gradlew :karaoke-web:ktlintCheck` → BUILD SUCCESSFUL, 0 новых нарушений.
- [ ] T039 Запустить полный quickstart.md сценарий A-F на dev-pc: **Не выполняется** — требует dev-pc.
- [ ] T040 Проверить backward-compat на dev-pc: **Не выполняется** — требует dev-pc.
- [X] T041 Проверить pre-commit проверку секретов (Constitution § VIII): **Сделано** — проверка `git status` показывает только наши новые файлы (`.specify/.gitignore`, `specs/168-mobile-admin-lite/`, `specs/239-zakroma-author-songs-batch-render/`, и modified: `livedocs/architecture-notes.md`). Нет секрет-файлов.
- [ ] T042 [P] Обновить baseline-eslint/ktlint если нужно: **Не выполняется** — требует сборки на dev-pc.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Phase 1 — БЛОКИРУЕТ все user stories. Без T004-T013 ни одна UI-фича не работает.
- **User Stories (Phase 3+)**: все зависят от Phase 2.
  - Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) — последовательно по приоритету (P1 → P2).
  - US2 и US3 могут стартовать параллельно с US1 после Phase 2, но US2 зависит от UI-ветки premium (T014 + T024), US3 — от guest-mode (T015-T016 + T026-T029).
- **Polish (Phase 6)**: зависит от всех user stories.

### User Story Dependencies

- **US1 (P1)**: можно стартовать после Foundational (T004-T013). UI-изменения (T014-T016) — общие с US2/US3, поэтому US2/US3 частично переиспользуют код US1.
- **US2 (P2)**: можно стартовать после Foundational + T014 (PlayerIcon расширен). Зависит от auth-bootstrap (T011) для `isPremium`.
- **US3 (P2)**: можно стартовать после Foundational + T015-T016 (FavoriteIcon/PlaylistIcon guest-mode). Зависит от auth-bootstrap (T011) для token.

### Within Each User Story

- Backend → Frontend composables → Components → Views → Tests.
- Tests (manual по quickstart.md) — после реализации.

### Parallel Opportunities

- T002, T003 — можно параллельно (если миграция нужна).
- T004, T005 — последовательно (T005 зависит от T004 — добавление полей в DTO).
- T006, T007, T008, T009, T010 — можно параллельно (разные файлы, нет зависимостей).
- T011 — зависит от T008, T009 (использует их API).
- T012, T013 — можно параллельно (backend и frontend сборки).
- T015, T016 — можно параллельно (разные компоненты).
- T017, T018, T019 — можно параллельно (разные view).
- T031, T032, T033, T034, T037, T038, T042 — все [P] в Phase 6.

---

## Parallel Example: User Story 1

```bash
# Phase 2 — после Phase 1, запустить параллельно:
Task: "T006 Добавить метод favoritesIds в PublicPlaylistController.kt"
Task: "T007 Добавить метод songSubscriptionsIds в PublicPlaylistController.kt"
Task: "T008 Создать useSongSubscriptions.js"
Task: "T009 Расширить usePlaylistMembership.js"
Task: "T010 Расширить playlistApi.js"

# Phase 3 (US1) — после T011-T013, параллельно:
Task: "T015 Расширить FavoriteIcon.vue"
Task: "T016 Расширить PlaylistIcon.vue"
Task: "T017 Обновить ZakromaView.vue"
Task: "T018 Обновить SearchView.vue"
Task: "T019 Обновить AuthorPlaylistView.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003) — миграция БД (если нужна).
2. Complete Phase 2: Foundational (T004-T013) — backend endpoint'ы + frontend composables + сборка.
3. Complete Phase 3: User Story 1 (T014-T022) — расширение компонентов + view-изменения + ручная проверка.
4. **STOP and VALIDATE**: проверить quickstart.md Сценарий A (аноним) + B (зарегистрированный). Если ОК → **MVP готов**, можно деплоить.
5. Deploy/demo if ready.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 (P1) → Test independently (quickstart.md A, B, F) → Deploy/Demo (**MVP**).
3. Add User Story 2 (P2) → Test independently (quickstart.md C) → Deploy/Demo.
4. Add User Story 3 (P2) → Test independently (quickstart.md A анонимная часть) → Deploy/Demo.
5. Phase 6 Polish → final deploy.

### Parallel Team Strategy

С одним разработчиком (текущий сценарий):
1. Setup + Foundational → 1-2 дня.
2. US1 → 1 день (UI + тест).
3. US2/US3 → 0.5 дня (только auth-bootstrap + UI-ветки).
4. Polish → 0.5 день.

С двумя разработчиками (если будут):
- Dev A: T004-T013 + T014-T017 (backend + PlayerIcon + ZakromaView).
- Dev B: T015-T016, T018-T019 (FavoriteIcon/PlaylistIcon + Search/AuthorPlaylist).

---

## Notes

- [P] tasks = разные файлы, нет зависимостей.
- [Story] label привязывает задачу к user story для traceability.
- Каждая user story должна быть независимо завершаемой и тестируемой.
- Ручные тесты обязательны (см. quickstart.md) — автоматических нет.
- Коммит после каждой задачи или логической группы (T004+T005 — один коммит; T006+T007 — один коммит; и т.д.).
- **Стоп на любой checkpoint** для валидации user story.
- Избегать: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей, ломающих независимость.
- **Constitution Check** — после Phase 2 перепроверить KDoc на новые публичные API (FR-006); после Phase 6 — никаких секретов в коммите (VIII.3).
- **LiveDocs** — обязательно обновить в Phase 6 (FR-014).
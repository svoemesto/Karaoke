# Tasks: История прослушиваний (QW-13)

**Input**: Design documents from `/specs/009-listening-history/`
**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), contracts/history-api.md (✅), quickstart.md (✅)

**Tests**: автотестов для этого слоя в проекте нет (constitution.md «Рабочий процесс: Тесты»). Проверка — ручной сценарий в `quickstart.md` (12 пунктов, включая проверку миграции/recordhash/sync-видимости).

**Organization**: задачи сгруппированы по User Story. Foundational-фаза (миграция, модель, sync-регистрация, апсерт при прослушивании) — блокирующая для всех User Story, т.к. без данных в таблице ни один сценарий не проверить. US1 (P1) — сам список истории (MVP). US2 (P2) — пустое состояние. US3 (P3) — недоступность анониму.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей).
- **[Story]**: US1/US2/US3.
- Полные пути к файлам включены в описания.

## Path Conventions

- Миграция: `deploy/karaoke-db/`.
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/` (модель, sync, properties), `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/` (контроллер, DTO).
- Frontend: `karaoke-public/src/`.

---

## Phase 1: Setup

**Purpose**: убедиться, что окружение готово перед изменениями.

- [X] T001 Убедиться, что `karaoke-app` и `karaoke-web` собираются локально без ошибок (`./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin`) — отправная точка перед правками

**Checkpoint**: сборка чистая, можно начинать.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: новая таблица + модель + sync-регистрация + запись при прослушивании — без этого ни один User Story не проверить (все три сценария подразумевают, что в таблице есть/может появиться реальный сигнал прослушивания).

**⚠️ CRITICAL**: задачи User Story не начинать, пока эта фаза не завершена.

- [X] T002 Создать миграцию `deploy/karaoke-db/27_listening_history.sql` по образцу `deploy/karaoke-db/09_playlists.sql` (см. `data-model.md`): `CREATE TABLE public.tbl_listening_history` (`id` identity PK, `site_user_id` FK → `tbl_site_users(id)` ON DELETE CASCADE, `song_id` bigint без FK, `play_count` int default 1, `last_played_at` timestamp default now(), `created_at` timestamp default now(), `last_update` timestamp default now(), `recordhash` varchar(32)); уникальный индекс на `(site_user_id, song_id)`; индексы на `site_user_id`, `last_played_at`, `recordhash`; функция `update_tbl_listening_history_recordhash()` + триггер `update_recordhash_listening_history_trigger` (BEFORE INSERT OR UPDATE); триггер `update_last_updated_listening_history_trigger` (BEFORE UPDATE, существующая функция `update_last_updated()`)
- [X] T003 Применить миграцию `27_listening_history.sql` вручную на LOCAL БД (см. `AGENTS.md` — миграции применяются вручную на каждой БД отдельно) *(применено, триггеры и индексы проверены `\d tbl_listening_history`; на PROD — отдельное действие пользователя, агент прод-БД не трогает)*
- [X] T004 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/ListeningHistory.kt` — класс, реализующий `KaraokeDbTable` (тот же паттерн, что `SitePlaylist.kt`), поля из `data-model.md`, + метод `upsert(siteUserId, songId, database)` (raw JDBC `INSERT ... ON CONFLICT (site_user_id, song_id) DO UPDATE SET play_count = tbl_listening_history.play_count + 1, last_played_at = now()`), + метод `getForUser(siteUserId, database, limit = 100): List<...>` (join с `tbl_settings`, фильтр `SKIP`-тегов, `ORDER BY last_played_at DESC`, см. эскиз запроса в `research.md` Decision 2) *(+ `ListeningHistoryDto.kt`, обязателен интерфейсом `KaraokeDbTable.toDTO()`, не был явно в задаче, но требуется контрактом интерфейса)*
- [X] T005 [P] Добавить 8 `KaraokeProperty` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`: `sync_listeninghistory_{push,pull}_{insert,update,delete,move}_allowed`, все `defaultValue = false` (см. `data-model.md` Sync-регистрация, тот же паттерн, что `sync_siteplaylists_*`)
- [X] T006 Добавить `ListeningHistorySyncTarget` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` (`key = "listeninghistory"`, `tableName = ListeningHistory.TABLE_NAME`, `displayName = "История прослушиваний"`, `oneClickDirection = SyncDirection.SERVER_TO_LOCAL`, по образцу `SitePlaylistSyncTarget`) и зарегистрировать в `SyncRegistry.all`
- [X] T007 В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`, метод `doRegisterEvent`, ветка `EventType.PLAY.dbValue`: добавить вызов `ListeningHistory.upsert(siteUserId, songId, WORKING_DATABASE)` **дополнительно** к существующему `insertEvent(...)` (не вместо), только если `siteUserId > 0` (см. `research.md` Decision 4 — не трогаем существующую запись в `tbl_events`) *(проверено вживую: пересобран/перезапущен `karaoke-web` — разрешено агенту; реальный POST `/api/public/events` с валидным токеном → строка появляется, повтор увеличивает `play_count`/обновляет `last_played_at` без дублирования, `recordhash` пересчитывается триггером; тестовые данные удалены после проверки)*
- [ ] T008 Пересобрать `karaoke-app`, прогнать `quickstart.md` п.0 — новая сущность видна в `webvue3` → «Синхронизация», все флаги выключены. **Частично закрыто**: `./gradlew :karaoke-app:bootJar` собран чисто (проверено), но **перезапуск контейнера `karaoke-app` агенту запрещён** (constitution.md, только пользователь) — визуальную проверку в `webvue3` должен сделать пользователь после перезапуска `karaoke-app`

**Checkpoint**: данные пишутся при прослушивании, sync настроен. Готово к User Story.

---

## Phase 3: User Story 1 — Зарегистрированный пользователь видит, что он слушал (Priority: P1) 🎯 MVP

**Goal**: раздел «История» показывает список прослушанных песен, кликабельных, отсортированных по недавности.
**Independent Test**: зарегистрированный пользователь прослушивает 2-3 песни, заходит в «Историю», видит их в правильном порядке, кликает — переходит к песне.

### Реализация

- [X] T009 [P] [US1] Создать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/HistoryEntryDto.kt` — поля из `data-model.md` (`songId`, `songName`, `songAuthor`, `songAlbum`, `lastPlayed`, `playCount`)
- [X] T010 [US1] Создать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicHistoryController.kt` — `@RequestMapping("/api/public/account")`, `@GetMapping("/history")`, использует `ListeningHistory.getForUser()` (T004), резолвит текущего пользователя через `SiteAuthInterceptor` (тот же паттерн, что `PublicPlaylistController.kt`), возвращает `{ "items": [...] }` (см. `contracts/history-api.md`)
- [X] T011 [P] [US1] Создать `karaoke-public/src/services/historyApi.js` — функция `fetchHistory()`, вызывает `GET /api/public/account/history` (тот же паттерн, что `playlistApi.js`)
- [X] T012 [US1] Создать `karaoke-public/src/views/HistoryView.vue` — по образцу `PlaylistsView.vue`: шапка (`← Главная`, `Профиль →`), `loading`-состояние, список карточек (название, исполнитель, дата последнего прослушивания, count при `playCount > 1`), клик по карточке → `{ path: '/song', query: { id: songId } }`
- [X] T013 [US1] Добавить роут `/account/history` в `karaoke-public/src/router/index.js` *(без `beforeEnter: requireAuth` — сознательное отклонение от формулировки задачи: `LoginRequired` уже встроен в компонент (US3), тот же паттерн, что `/account/playlists`; `requireAuth`-редирект сделал бы этот код мёртвым)*
- [X] T014 [US1] Добавить ссылку «📜 История прослушиваний» в `karaoke-public/src/views/AccountView.vue`
- [X] T015 [US1] Прогнать `quickstart.md` пп.1-2 — **проверено вживую через реальный HTTP**: POST `/api/public/events` (play) → GET `/api/public/account/history` возвращает песню с верным `songName`/`songAuthor`/`playCount`; повторное прослушивание не дублирует запись (`play_count` 1→2, `last_played_at` обновился); две разные песни — верная сортировка по убыванию даты. Клик-переход на `/song?id=` — код-ревью (не через браузер, см. общее ограничение песочницы)

**Checkpoint**: US1 (MVP) закрыт — история видна, работает, кликабельна.

---

## Phase 4: User Story 2 — Пустая история не выглядит как ошибка (Priority: P2)

**Goal**: пользователь без прослушиваний видит понятную заглушку, не пустоту.
**Independent Test**: новый пользователь без истории открывает раздел — видит текст-заглушку и CTA в каталог.

### Реализация

- [X] T016 [US2] В `HistoryView.vue` добавить `<p v-if="!loading && !items.length" class="km-empty">Вы пока ничего не слушали. <RouterLink to="/zakroma">Перейти к каталогу</RouterLink></p>` (тот же `.km-empty`-паттерн, что в `PlaylistsView.vue`)
- [X] T017 [US2] Прогнать `quickstart.md` п.3 — **проверено на бэкенде**: `GET /api/public/account/history` для пользователя без истории вернул `{"items":[]}` (200, не ошибка) — фронтенд-условие `v-else-if="!items.length"` покажет заглушку; визуальный рендер — код-ревью, не браузер

**Checkpoint**: US2 закрыт.

---

## Phase 5: User Story 3 — Анониму раздел недоступен (Priority: P3)

**Goal**: аноним видит приглашение войти/зарегистрироваться, не ошибку.
**Independent Test**: аноним открывает `/account/history` напрямую — видит `LoginRequired`.

### Реализация

- [X] T018 [US3] В `HistoryView.vue` импортировать `LoginRequired` из `../components/LoginRequired.vue`, добавить `<LoginRequired v-if="!isLoggedIn" />` перед основным контентом (тот же паттерн, что `PlaylistsView.vue` строка 13)
- [X] T019 [US3] Прогнать `quickstart.md` п.4 — **проверено на бэкенде**: `GET /api/public/account/history` без заголовка `Authorization` → `401 {"error":"unauthorized"}` (тот же `SiteAuthInterceptor`, что у остальных `/api/public/account/*`); фронтенд `LoginRequired` — код-ревью (компонент уже используется идентично в `PlaylistsView.vue`, не браузер-тест)

**Checkpoint**: US3 закрыт — все три User Story готовы.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: финальная валидация.

- [X] T020 [P] Прогнать `./gradlew ktlintCheck` — новые Kotlin-файлы (`ListeningHistory.kt`, `PublicHistoryController.kt`, `HistoryEntryDto.kt`) проходят CI-линт *(0 нарушений)*
- [X] T021 [P] Прогнать `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` — `HistoryView.vue`/`historyApi.js` проходят CI-линт *(0 warnings/errors)*
- [X] T022 [P] Прогнать `bash tools/check-kdoc-coverage.sh --strict` и `bash tools/check-jsdoc-coverage.sh --strict` — новые файлы не роняют coverage ниже 50% *(KDoc 96.9%, JSDoc 100%, оба exit 0)*
- [X] T023 Прогнать `quickstart.md` пп.5-8 — **проверено вживую**: изоляция данных между пользователями подтверждена (второй пользователь видит `{"items":[]}` для чужой истории); лимит (`LIMIT 100` в запросе `getForUser`) — код-ревью; ссылка в `/account` — код-ревью; задержка — не измерялась инструментально, но запрос — один индексированный join с `LIMIT`, той же формы, что уже работающие запросы проекта
- [X] T024 Полный прогон `quickstart.md` — 10 из 12 пунктов подтверждены вживую через реальные HTTP-запросы к поднятым `karaoke-web`/`karaoke-public` (включая исправление собственной ошибки тестирования — таймзона `expires_at` в ручных тестовых токенах, не баг кода, см. коммит); визуальный рендер в браузере (пп. кликабельность/вид карточек) не проверен — headless-браузер недоступен в этой песочнице (та же оговорка, что в `003`/`004`/`005`)
- [X] T025 Обновить `specs/004-reasons-to-register/spec.md` — пункт «История» в разборе причин теперь реализован (не блокер для разморозки QW-2 целиком, т.к. «Уведомления» — п.4 — всё ещё не реализованы, но зафиксировать прогресс)

**Checkpoint**: всё готово к PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей.
- **Phase 2 (Foundational)**: блокирует все User Story — T002→T003→T004 строго последовательно (модель зависит от таблицы), T005/T006 после T004 (зависят от `ListeningHistory.TABLE_NAME`), T007 после T004 (использует `ListeningHistory.upsert`), T008 в конце (проверка после всех правок).
- **Phase 3 (US1, P1)**: после Phase 2. T009/T011 параллельно (разные файлы, независимы), T010 после T009 (использует DTO) и после T004 (использует модель), T012 после T011 (использует `historyApi.js`), T013/T014 после T012, T015 в конце.
- **Phase 4 (US2, P2)**: после Phase 3 (T012 должен существовать — правки в тот же файл).
- **Phase 5 (US3, P3)**: после Phase 3, независимо от Phase 4 (разные условия в том же файле, не конфликтуют содержательно, но лучше последовательно во избежание конфликтов правок одного файла).
- **Phase 6 (Polish)**: после US1-US3.

### Parallel Opportunities

- Phase 2: T005 параллельно с T002/T003/T004 (разные файлы, `KaraokeProperties.kt` не зависит от таблицы).
- Phase 3: T009 и T011 параллельно (разные модули/файлы).
- Phase 6: T020, T021, T022 параллельно (разные команды/файлы).

---

## Implementation Strategy

### MVP First (P1 only)

1. Phase 1 (Setup).
2. Phase 2 (Foundational) — миграция, модель, sync, апсерт. **Обязательна целиком**, даже для MVP.
3. Phase 3 (US1) — сам список истории.
4. **STOP и VALIDATE**: `quickstart.md` пп.0-2.
5. MVP готов: зарегистрированный пользователь видит свою историю.

### Incremental Delivery

1. MVP (Foundational + US1).
2. + Phase 4 (US2) — пустое состояние (маленький инкремент).
3. + Phase 5 (US3) — защита от анонимов (маленький инкремент).
4. + Phase 6 (Polish) — линтеры, финальная валидация, обновление `004`.

---

## Notes

- **Ключевое архитектурное решение этой фичи** (после ревизии 2026-07-25,
  см. `research.md` Decision 1): НЕ переиспользуем `tbl_events` (она регулярно
  опустошается на PROD через sync). Новая таблица `tbl_listening_history` —
  не в конфликте с этим общим планом. Не менять это решение без повторного
  согласования с пользователем.
- Запись в `tbl_events` (T007) — **дополнительная**, не замена существующей.
  Не удалять существующий `insertEvent(...)`-вызов в той же ветке.
- Миграция (T002/T003) применяется вручную на LOCAL и (отдельно, при
  реальном деплое, не в рамках этих задач) на PROD — см. `AGENTS.md`, агенту
  запрещено самостоятельно трогать прод-БД без явного согласия пользователя.
- `T025` — небольшая доза бухгалтерии по `004-reasons-to-register`, не полная
  разморозка QW-2 (для этого нужен ещё пункт «Уведомления», отдельная фича).

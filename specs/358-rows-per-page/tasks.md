# Tasks: Настраиваемое количество строк на странице таблиц в админке

**Input**: Design documents from `/specs/358-rows-per-page/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/api-properties.md ✅, quickstart.md ✅

**Tests**: NOT requested in spec.md (только manual E2E по quickstart.md).

**Organization**: Tasks grouped by user story + foundational work + polish. US1 (Songs) = MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend (Kotlin/Spring)**: `karaoke-app/src/main/kotlin/...`
- **Frontend (Vue 3/Vuex)**: `webvue3/src/...`
- **Per-feature docs**: `docs/features/<slug>.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Создать базовые артефакты и убедиться что ветка/спека/issue готовы.

- [x] T001 Подтвердить, что на ветке `359-rows-per-page` и спека `specs/358-rows-per-page/spec.md` готова (check `git status --short` и `git branch --show-current`).
- [x] T002 Создать per-feature документ `docs/features/rows-per-page.md` со ссылками на FR-001..FR-011, NFR-001..NFR-003, SC-001..SC-007 (per FR-009 constitution; см. `docs/features/README.md` для формата).
- [x] T003 [P] Проверить, что `webvue3/src/store/index.js` существует и имеет `modules: {}` для регистрации нового модуля.

**Checkpoint**: Setup ready — можно создавать Vuex-модуль и backend-параметры.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend + Vuex store — обязательные зависимости для всех user stories.

**⚠️ CRITICAL**: US1/US2/US3 не могут начаться без завершения этой фазы.

- [x] T004 [P] Добавить 14 параметров в `listKaraokeProperties` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`: ключи `ui.songs.rows_per_page` (50), `ui.authors.rows_per_page` (30), `ui.albums.rows_per_page` (30), `ui.pictures.rows_per_page` (30), `ui.site_users.rows_per_page` (30), `ui.subscriptions.rows_per_page` (25), `ui.share_links.rows_per_page` (25), `ui.dictionaries.rows_per_page` (30), `ui.properties.rows_per_page` (50), `ui.site_playlists.rows_per_page` (30), `ui.listening_history.rows_per_page` (500), `ui.processes.rows_per_page` (50), `ui.news.rows_per_page` (30), `ui.stats.rows_per_page` (30). Тип `INT`, `isHidden=false`. Дефолты совпадают с существующими hardcoded `perPage` (FR-010).
- [x] T005 [P] Добавить серверную валидацию `1..1000` в эндпоинт `/api/properties/setproperty` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: если `key` имеет шаблон `ui.*.rows_per_page`, парсить `stringValue` как Int и валидировать диапазон. Невалидное → HTTP 400 с сообщением (FR-006).
- [x] T006 [P] Создать Vuex-модуль `webvue3/src/store/modules/tableSettings.js` с state `{ rowsPerPage: {}, loaded: false, saving: {} }`, геттерами `getRowsPerPage(tableKey)`, `isSavingRowsPerPage(tableKey)`, actions `loadTableSettings()` (POST `/api/propertiesdigests`, фильтр по `ui.*.rows_per_page`) и `setRowsPerPage({tableKey, value})` (POST `/api/properties/setproperty`). Использовать существующий `promisedXMLHttpRequest` из `webvue3/src/lib/utils.js`. Контракт см. `data-model.md`.
- [x] T007 Зарегистрировать новый модуль в `webvue3/src/store/index.js`: `modules: { ..., tableSettings: tableSettingsModule }` (импорт из `./modules/tableSettings.js`).
- [x] T008 [P] Запустить `bash tools/check-knowledge-structure.sh` и `bash tools/check-ssot-impact.py` — убедиться, что нет regression в Knowledge.

**Checkpoint**: Foundation ready — можно менять 14 таблиц.

---

## Phase 3: User Story 1 - Songs: регулировка количества строк (Priority: P1) 🎯 MVP

**Goal**: В таблице «Песни» появляется поле «Строк на странице», изменение применяется и сохраняется.

**Independent Test**: Зайти в `/songs`, изменить поле с 50 на 100, проверить что таблица отображает 100 строк без перезагрузки, перезагрузить страницу — значение 100 восстановлено.

### Implementation for User Story 1

- [x] T009 [US1] В `webvue3/src/components/Songs/SongsTable.vue`:
  - Изменить `data().perPage` (текущее значение 50) на computed или initial из `this.$store.getters.getRowsPerPage('songs')`.
  - В блоке пагинации добавить `<b-form-input type="number" min="1" max="1000" :model-value="perPage" :disabled="this.$store.getters.isSavingRowsPerPage('songs')" @change="onPerPageChange" />` слева от `<b-pagination>`.
  - Добавить метод `async onPerPageChange(newValue)`: парсинг, проверка диапазона, `this.currentPage = 1`, `await this.$store.dispatch('setRowsPerPage', { tableKey: 'songs', value: parsed })`, `this.loadData()`.
  - В `created()` добавить `await this.$store.dispatch('loadTableSettings')` перед существующим `loadData()`.
  - Добавить KDoc/JSDoc для нового метода.
- [x] T010 [US1] Запустить `cd webvue3 && npm run lint:check && npm run build && npm run format:check && cd ..` — убедиться, что линтеры и билд проходят.

**Checkpoint**: User Story 1 should be fully functional and testable independently (Songs работает с настраиваемым rowsPerPage).

---

## Phase 4: User Story 2 - Остальные таблицы (Priority: P1)

**Goal**: Те же UI-изменения в 13 других таблицах (Authors, Albums, Pictures, SiteUsers, Subscriptions, ShareLinks, Dictionaries, Properties, SitePlaylists, ListeningHistory, Processes, News, Stats).

**Independent Test**: На каждой из 13 таблиц — поле «Строк на странице» с правильным дефолтом; изменение применяется и сохраняется.

### Implementation for User Story 2

Эти задачи **все параллельны** (разные файлы), можно запускать вместе.

- [x] T011 [P] [US2] В `webvue3/src/components/Authors/AuthorsTable.vue`: привязать `perPage` к store, добавить `<b-form-input>`, метод `onPerPageChange`, `loadTableSettings` в `created()` (tableKey='authors').
- [x] T012 [P] [US2] В `webvue3/src/components/Albums/AlbumsTable.vue`: то же (tableKey='albums').
- [x] T013 [P] [US2] В `webvue3/src/components/Pictures/PicturesTable.vue`: то же (tableKey='pictures').
- [x] T014 [P] [US2] В `webvue3/src/components/SiteUsers/SiteUsersTable.vue`: то же (tableKey='site_users').
- [x] T015 [P] [US2] В `webvue3/src/components/Subscriptions/SubscriptionsTable.vue`: то же (tableKey='subscriptions').
- [x] T016 [P] [US2] В `webvue3/src/components/ShareLinks/ShareLinksTable.vue`: то же (tableKey='share_links').
- [x] T017 [P] [US2] В `webvue3/src/components/Dictionaries/DictionariesTable.vue`: то же (tableKey='dictionaries').
- [x] T018 [P] [US2] В `webvue3/src/components/Properties/PropertiesTable.vue`: то же (tableKey='properties').
- [x] T019 [P] [US2] В `webvue3/src/components/SitePlaylists/SitePlaylistsTable.vue`: то же (tableKey='site_playlists').
- [x] T020 [P] [US2] В `webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue`: то же (tableKey='listening_history').
- [x] T021 [P] [US2] В `webvue3/src/components/Processes/ProcessesTable.vue`: то же (tableKey='processes').
- [x] T022 [P] [US2] В `webvue3/src/components/News/NewsTable.vue`: то же (tableKey='news').
- [x] T023 [US2] Запустить `cd webvue3 && npm run lint:check && npm run build && npm run format:check && cd ..` — убедиться, что все 13 таблиц проходят линтеры.

**Checkpoint**: User Story 2 should be fully functional and testable independently (все 14 таблиц работают).

---

## Phase 5: User Story 3 - Persistence per-table (Priority: P1)

**Goal**: Настройки сохраняются в `Karaoke.properties` и восстанавливаются при перезагрузке.

**Independent Test**: Задать значение в Songs, перезагрузить вкладку — значение восстановлено (через `loadTableSettings` + `/api/propertiesdigests`).

### Implementation for User Story 3

**Замечание**: эта фаза не требует отдельных задач — persistence уже реализована через T004 (добавление параметров в `KaraokeProperties`) + T006 (Vuex `loadTableSettings`).

**Checkpoint**: User Story 3 should be fully functional (persistence работает для всех 14 таблиц).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Линтеры, тесты, документация, build, деплой.

- [x] T025 [P] Backend compile: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`.
- [x] T026 [P] Backend lint: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` (Kotlin).
- [x] T027 [P] Frontend lint: `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..` (ESLint + Prettier).
- [x] T028 [P] Documentation coverage: `bash tools/check-kdoc-coverage.sh --strict` и `bash tools/check-jsdoc-coverage.sh webvue3 --strict`.
- [x] T029 Backend bootJar: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel`.
- [x] T030 Frontend Vite build: `cd webvue3 && npm run build && cd ..`.
- [x] T031 [P] Docker-образ: `cd deploy && bash do.sh build_webvue3`.
- [x] T032 [P] Создать `specs/358-rows-per-page/report.md` с описанием реализации (отчёт для OpenProject tracker).
- [ ] T033 Запустить manual E2E по `quickstart.md` (все 8 шагов).
- [x] T034 [P] Добавить запись в `docs/architecture-notes.md` о фиче (Pass N+1, краткое описание).

**Checkpoint**: Все 7/7 CI проверок пройдены, образ собран, manual E2E прошёл.

---

## Phase 7: OpenProject Workflow (NON-NEGOTIABLE)

**Purpose**: Закрыть workflow шаги 4-6 (add-comment, mark-review, close — последний после ревью владельцем).

- [ ] T035 `bash tools/tracker.sh add-comment 74 --file specs/358-rows-per-page/report.md` — опубликовать отчёт.
- [ ] T036 `bash tools/tracker.sh mark-review 74` — перевести в `In review`.
- [ ] T037 (После ревью владельцем) `bash tools/tracker.sh close-issue 74` — закрыть задачу.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user stories.
- **Phase 3 (US1 - Songs)**: Depends on Phase 2.
- **Phase 4 (US2 - 13 other tables)**: Depends on Phase 2 + Phase 3 (T009 должен показать, что паттерн работает; после этого 13 параллельных задач). В принципе можно параллельно с Phase 3 если знать паттерн, но безопаснее последовательно.
- **Phase 5 (US3 - Persistence)**: Реализуется в Phase 2; отдельных задач не требуется.
- **Phase 6 (Polish)**: Depends on Phase 3 + Phase 4.
- **Phase 7 (Tracker Workflow)**: Depends on Phase 6.

### User Story Dependencies

- **US1 (Songs)**: Только foundational.
- **US2 (Other tables)**: Только foundational + US1 (как reference pattern).
- **US3 (Persistence)**: Нет отдельных задач — встроено в Phase 2.

### Within Each Phase

- T004-T008 — все [P], можно параллельно (кроме T007 — зависит от T006).
- T011-T023 — все [P], можно параллельно (разные файлы).
- T025-T031 — все [P] кроме зависимостей (bootJar зависит от compile).

### Parallel Opportunities

- **Phase 2**: T004, T005, T006, T008 — все параллельно (T007 после T006).
- **Phase 4**: T011-T023 — все параллельно (13 разных файлов).
- **Phase 6**: T025-T028, T031, T032, T034 — параллельно.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001-T003) — quick.
2. Phase 2: Foundational (T004-T008) — backend + Vuex store.
3. Phase 3: US1 Songs (T009-T010) — первая таблица, проверка паттерна.
4. **STOP and VALIDATE**: Manual test Songs — поле работает, persistence есть.
5. Если OK — продолжить Phase 4.

### Incremental Delivery

1. Phase 1+2: foundation (backend + Vuex store).
2. Phase 3: US1 Songs → manual test → demo MVP.
3. Phase 4: US2 (13 таблиц, параллельно) → manual test → demo.
4. Phase 5: persistence уже работает.
5. Phase 6: polish + lint + build.
6. Phase 7: tracker workflow.

### Parallel Team Strategy

Один разработчик последовательно (Phase 2 → Phase 3 → Phase 4). Если несколько — Phase 4 тривиально параллелится (13 файлов).

---

## Notes

- [P] tasks = разные файлы, нет зависимостей → можно параллелить.
- [Story] label мапится на user story для traceability.
- Каждая user story независимо завершаема и тестируема.
- Manual E2E обязателен (CI автоматических тестов нет).
- Commit после каждой задачи или логической группы.
- STOP на checkpoint валидации story.
- Никаких изменений в `Karaoke.properties` файле на проде до ручного одобрения пользователя (per AGENTS.md «Граница доступа к MLT/Karaoke.properties»).
- Избегать: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей.
- **Важно**: T004 (добавление параметров) требует, чтобы файл `Karaoke.properties` на проде БЫЛ перезаписан со списком всех параметров. Это безопасная операция (добавляются новые ключи, не удаляются), но требует deploy и рестарта `karaoke-app`.

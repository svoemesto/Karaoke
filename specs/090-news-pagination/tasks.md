---

description: "Task list template for feature implementation"
---

# Tasks: Пагинация ленты новостей

**Input**: Design documents from `/specs/090-news-pagination/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/news-api.md, quickstart.md

**Tests**: Не запрашивались — в проекте нет CI-тестов для этого стека (см. constitution.md, «Рабочий процесс»); проверка — вручную по quickstart.md.

**Organization**: Задачи сгруппированы по пользовательским историям (US1 — публичная лента, US2 — админка), каждая независимо реализуема и проверяема.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: US1 или US2
- Пути файлов — точные, из plan.md

## Phase 1: Setup

**Purpose**: подготовка объёма тестовых данных для реалистичной проверки (без него SC-001/SC-002 нельзя проверить)

- [X] T001 Наполнить локальный `tbl_news` тестовыми строками (~19000+, `source='auto'`) по quickstart.md сценарий 0, чтобы обе истории проверялись на объёме, сопоставимом с прод-инцидентом

---

## Phase 2: Foundational

**Purpose**: общая для обеих историй инфраструктура

**Отсутствует по существу**: US1 (karaoke-web + karaoke-public) и US2 (karaoke-app + webvue3) не имеют общего кода за пределами Setup (Principle V — разделение админки и публичного сайта). Обе истории можно начинать сразу после T001.

---

## Phase 3: User Story 1 - Посетитель листает ленту новостей порциями (Priority: P1) 🎯 MVP

**Goal**: страница «Новости проекта» отдаёт и показывает новости порциями, а не всем списком разом

**Independent Test**: открыть `/news` при 19000+ строках в `tbl_news` — страница загружается быстро и показывает ограниченную первую порцию с рабочей кнопкой «Показать ещё» (quickstart.md, сценарии 1-2)

### Implementation for User Story 1

- [X] T002 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt` заменить `loadPublished(database)` на `loadPublished(database, limit, offset)` (SQL `ORDER BY publish_at DESC, id DESC LIMIT ? OFFSET ?`) и добавить `countPublished(database): Long` (`SELECT COUNT(*) ... WHERE publish_at IS NOT NULL AND publish_at <= now()`); обновить KDoc
- [X] T003 [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt` изменить `list()`: параметры `page: Int = 0`, `size: Int = 20`; вернуть `{ items, total, hasMore }` вместо плоского списка (см. contracts/news-api.md); обновить KDoc (depends on T002)
- [X] T004 [P] [US1] В `karaoke-public/src/services/newsApi.js` изменить `fetchNews()` → `fetchNews(page = 0, size = 20)`, передавать параметры в `apiGet`
- [X] T005 [US1] В `karaoke-public/src/views/NewsView.vue`: добавить `page`/`total`/`loadingMore` в `data()`, `mounted()` грузит первую порцию через новую форму ответа (`{items, total, hasMore}`), добавить кнопку «Показать ещё» (дозагрузка следующей порции с добавлением к `news`, без сброса уже показанных карточек), скрыть/задизейблить кнопку при `hasMore: false` (depends on T004)
- [X] T006 [US1] Ручная проверка по quickstart.md сценарии 0-2 (загрузка первой порции быстрее 2 сек, дозагрузка без дублей/пропусков, пустая пагинация при малом числе новостей)

**Checkpoint**: публичная лента полностью работает независимо от админки

---

## Phase 4: User Story 2 - Администратор листает список новостей в админке порциями (Priority: P1)

**Goal**: раздел «Новости» в webvue3 отдаёт и показывает новости постранично, с рабочим переходом на любую страницу и без потери CRUD-функциональности

**Independent Test**: открыть раздел «Новости» при 19000+ строках — таблица показывает одну страницу с корректной `<b-pagination>`, переход на страницу N в любом порядке работает, создание/редактирование/удаление продолжают работать (quickstart.md, сценарии 3-5)

### Implementation for User Story 2

- [X] T007 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt` заменить `loadAll(database, storageService, storageApiClient)` (сейчас — generic `loadList` + `sortedByDescending{it.id}` в памяти) на прямой SQL с `ORDER BY id DESC LIMIT ? OFFSET ?` и параметрами `limit`, `offset`; добавить `countAll(database): Long` (`SELECT COUNT(*) FROM tbl_news`); обновить KDoc
- [X] T008 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/NewsController.kt` изменить `list()`: параметры `page: Int = 0`, `pageSize: Int = 50` (плюс существующий `target`); вернуть `{ news, total }` (см. contracts/news-api.md) (depends on T007)
- [X] T009 [P] [US2] В `webvue3/src/components/News/store.js` добавить state `newsTotalCount`, `newsCurrentPage` (1-based), `newsPerPage` (константа 50); изменить `loadNews` — передавать `page`/`pageSize` (пересчитанные из `newsCurrentPage`), сохранять `total` из ответа; `setNewsTarget`/при смене target — сбрасывать `newsCurrentPage` на 1 (FR-007 spec.md)
- [X] T010 [US2] В `webvue3/src/components/News/NewsTable.vue` добавить `<b-pagination v-model="currentPage" :total-rows="newsTotalCount" :per-page="newsPerPage">` (паттерн — как в `DictionariesTable.vue`), но данные всегда содержат только текущую страницу (без клиентской нарезки полного списка — не передавать `:per-page`/`:current-page` в `b-table`, т.к. `newsList` уже равен одной странице); смена страницы триггерит `loadNews` (depends on T009)
- [X] T011 [US2] Ручная проверка по quickstart.md сценарии 3-5 (переход на произвольную страницу, создание/удаление не ломает пагинацию, смена target сбрасывает страницу на 1)

**Checkpoint**: обе истории работают полностью независимо друг от друга и от specs/089

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: финальные проверки качества перед коммитом (см. CLAUDE.md «Обязательно перед каждым git commit»)

- [X] T012 [P] Проверить/дополнить KDoc (`News.kt`, `PublicNewsController.kt`, `NewsController.kt`) и JSDoc (`newsApi.js`, `NewsView.vue`, `store.js`, `NewsTable.vue`) для всех изменённых публичных функций/компонентов (FR-006 constitution.md)
- [X] T013 Прогнать `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check && npx prettier --check ...`, `cd karaoke-public && npm run lint:check && npx prettier --check ...`, `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`, `bash tools/check-jsdoc-coverage.sh karaoke-public`, `pre-commit run --all-files`
- [X] T014 Полный сквозной прогон quickstart.md (все сценарии 0-5 подряд) на локальном стенде, затем очистка тестовых данных (последняя команда quickstart.md)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей, выполняется первым.
- **Foundational (Phase 2)**: отсутствует по существу — можно сразу переходить к US1/US2.
- **User Stories (Phase 3-4)**: обе зависят только от Setup (T001); независимы друг от друга (разные файлы/модули — karaoke-web+karaoke-public для US1, karaoke-app+webvue3 для US2). Единственная точка соприкосновения — оба меняют разные функции в одном файле `News.kt` (T002 и T007), поэтому не помечены [P] относительно друг друга при последовательном выполнении одним агентом.
- **Polish (Phase 5)**: зависит от завершения обеих историй.

### Within Each User Story

- Backend (модель → контроллер) перед frontend (API-обёртка → компонент).
- T003 зависит от T002 (сигнатура функции). T005 зависит от T004 (форма ответа API-обёртки). T008 зависит от T007. T010 зависит от T009.

### Parallel Opportunities

- T004 (`karaoke-public/newsApi.js`) можно готовить параллельно с T002/T003 (разные модули), но интегрировать (T005) только после T004.
- T009 (`webvue3/store.js`) можно готовить параллельно с T007/T008, интегрировать (T010) только после T009.
- US1 и US2 в целом можно вести параллельно двумя разработчиками/агентами при аккуратной последовательной правке `News.kt` (T002 и T007 — в разных PR-коммитах, не одновременно в одной ветке).
- T012 (документация) параллелизуема по файлам.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001 (Setup).
2. Phase 3 (US1) целиком → **STOP and VALIDATE** по quickstart.md сценарии 1-2.
3. Это уже устраняет самую острую проблему — публичная лента на проде.

### Incremental Delivery

1. Setup → US1 (публичная лента) → проверить и, при готовности, задеплоить (с согласия пользователя, см. constitution.md «Ограничения и доступы агента»).
2. US2 (админка) → проверить.
3. Polish → финальный сквозной прогон.

---

## Notes

- [P] = разные файлы, нет зависимостей.
- Обе истории — приоритет P1 (обе чинят один и тот же прод-инцидент: 19000+ строк без пагинации), но реализуются и коммитятся раздельно для независимой проверки.
- Логика создания новостей (specs/089, `SongReleaseAnnouncementService`) не затрагивается ни одной задачей.

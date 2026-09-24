---

description: "Task list for 450-author-filter-datalist"
---

# Tasks: Список авторов в фильтрах

**Input**: Design documents from `/specs/450-author-filter-datalist/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/ui-author-datalist.md, quickstart.md

**Tests**: НЕ включены — в `webvue3` нет раннера для Vue-компонентов, тест-инфраструктура не запрошена (см. `research.md` Decision 6). Верификация — ручной E2E по `quickstart.md` + сборка/линт.

**Organization**: Задачи сгруппированы по user story (US1 P1 → US2 P2 → US3 P3); каждая story независимо тестируема.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: US1 / US2 / US3
- Пути к файлам указаны относительно корня репозитория

## Path Conventions

- Admin SPA: `webvue3/src/...`
- Per-feature документ: `docs/features/<slug>.md`
- SSoT: `knowledge/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Документационные артефакты, требуемые Constitution VI (FR-009) и Knowledge-SSoT.

- [X] T001 Создать per-feature документ `docs/features/author-filter-datalist.md` по контракту `specs/001-code-standards-docs/contracts/per-feature-doc.md` (6 обязательных секций: «Что делает», «Зачем», «Как работает», «Инварианты», «Известные ловушки», «Ссылки»; `Status: active`)
- [X] T002 [P] Дополнить `knowledge/system/frontend/filter-stores.md` отметкой, что поля «Автор:» в фильтрах «Авторы»/«Альбомы» используют тот же источник подсказок (`songAuthorsPromise` → `POST /api/songs/authors`), что и фильтр «Песни»

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Блокирующих задач нет.

**⚠️ Решение**: Новой инфраструктуры не требуется — используется существующий Vuex-геттер `getters.songAuthorsPromise` (`webvue3/src/components/Songs/store.js:941`) и существующий endpoint `POST /api/songs/authors` (backend не меняется). User stories можно реализовывать сразу.

**Checkpoint**: Базовая инфраструктура уже готова (существующий геттер/endpoint).

---

## Phase 3: User Story 1 - Подсказки авторов в фильтре «Авторы» (Priority: P1) 🎯 MVP

**Goal**: Поле «Автор:» в модалке фильтра компонента «Авторы» показывает выпадающий список имён авторов (нативный `<datalist>`), как в фильтре «Песни».

**Independent Test**: Открыть «Авторы» → фильтр, начать вводить имя известного автора → появляется список подсказок; выбор подставляет точное имя и фильтр возвращает записи этого автора.

### Implementation for User Story 1

- [X] T003 [US1] В `webvue3/src/components/Authors/filter/AuthorsFilterModal.vue` добавить `dictAuthors: []` в `data()` и в `mounted()` загрузить список через `this.$store.getters.songAuthorsPromise` → `JSON.parse(data).authors` (по образцу `SongsFilterModal.vue:838-843`); при ошибке оставить `dictAuthors = []`
- [X] T004 [US1] В `webvue3/src/components/Authors/filter/AuthorsFilterModal.vue` добавить `<datalist id="authorsDictAuthorsId">` с `v-for="val in dictAuthors"` и привязать поле «Автор:» через `list="authorsDictAuthorsId"` (сохранив `v-model="authorsFilterAuthor"` и `class="afm-input-field"`)
- [X] T005 [US1] В `webvue3/src/components/Authors/filter/AuthorsFilterModal.vue` дополнить JSDoc компонента ссылкой `@see docs/features/author-filter-datalist.md` (FR-006)

**Checkpoint**: Фильтр «Авторы» показывает подсказки авторов, работает независимо.

---

## Phase 4: User Story 2 - Подсказки авторов в фильтре «Альбомы» (Priority: P2)

**Goal**: Поле «Автор:» в модалке фильтра компонента «Альбомы» показывает такой же список подсказок, как в фильтре «Авторы»/«Песни».

**Independent Test**: Открыть «Альбомы» → фильтр, начать вводить имя автора → появляется выпадающий список; выбор подставляет точное имя и фильтр возвращает альбомы этого автора.

### Implementation for User Story 2

- [X] T006 [US2] В `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue` добавить `dictAuthors: []` в `data()` и в `mounted()` загрузить список через `this.$store.getters.songAuthorsPromise` → `JSON.parse(data).authors`; при ошибке оставить `dictAuthors = []`
- [X] T007 [US2] В `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue` добавить `<datalist id="albumsDictAuthorsId">` с `v-for="val in dictAuthors"` и заменить прежнюю привязку поля «Автор:» `list="list_authors"` на `list="albumsDictAuthorsId"` (сохранив `v-model="albumsFilterAuthorName"` и `class="afm-input-field"`)
- [X] T008 [US2] В `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue` дополнить JSDoc компонента ссылкой `@see docs/features/author-filter-datalist.md` (FR-006)

**Checkpoint**: Обе модалки (Авторы, Альбомы) показывают подсказки, работают независимо.

---

## Phase 5: User Story 3 - Единообразие подсказок во всех трёх фильтрах (Priority: P3)

**Goal**: Источник и поведение подсказок совпадают в фильтрах «Песни», «Авторы», «Альбомы».

**Independent Test**: Сравнить список подсказок в трёх фильтрах — он идентичен; поведение при пустом поле/опечатке одинаково (quickstart.md Сценарий 3).

### Implementation for User Story 3

- [X] T009 [US3] Сверить единый источник и поведение подсказок в `webvue3/src/components/Songs/filter/SongsFilterModal.vue`, `webvue3/src/components/Authors/filter/AuthorsFilterModal.vue`, `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue` (все три читают `songAuthorsPromise`); зафиксировать расхождения и при необходимости устранить

**Checkpoint**: Все три фильтра используют один источник подсказок.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Проверки качества, SSoT и отчётность.

- [X] T010 В `webvue3/` выполнить `npm run lint`, `npm run build`, `npm run format:check` (см. AGENTS.md Tier-2 «Обязательная проверка после изменения»); устранить новые ошибки
- [X] T011 [P] Выполнить линтер Knowledge `python3 tools/lint-knowledge.py` и проверку per-feature документа `tools/check-feature-doc.sh docs/features/author-filter-datalist.md`; убедиться в отсутствии новых нарушений
- [ ] T012 Пройти ручные сценарии 1-4 из `specs/450-author-filter-datalist/quickstart.md` (включая недоступность `/api/songs/authors` и сохранённый фильтр после F5)
- [X] T013 Создать `specs/450-author-filter-datalist/report.md` и после merge PR выполнить tracker-workflow: `bash tools/tracker.sh add-comment 183 --file specs/450-author-filter-datalist/report.md` затем `bash tools/tracker.sh mark-review 183` (см. spec.md § OpenProject Tracking)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: Блокирующих задач нет
- **User Stories (Phase 3-5)**: Зависят от Setup-документов (для `@see`), но код независим
- **Polish (Phase 6)**: Зависит от завершения нужных user stories

### User Story Dependencies

- **US1 (P1)**: Независима — правит только `AuthorsFilterModal.vue`
- **US2 (P2)**: Независима — правит только `AlbumsFilterModal.vue`; использует тот же геттер
- **US3 (P3)**: Зависит от US1 и US2 (сверка результата); кода не добавляет

### Within Each User Story

- Состояние (`data()` + `mounted()`) → разметка (`<datalist>` + `list`) → JSDoc
- Story завершается независимо проверяемым инкрементом

### Parallel Opportunities

- T002 [P] (knowledge) может идти параллельно с T001
- T001 и T003/T006 касаются разных файлов
- US1 и US2 можно вести параллельно (разные файлы, один источник)
- T011 [P] (линт knowledge) параллелится с T012 (ручной E2E) после T010

---

## Parallel Example: Setup

```bash
# Документационные задачи параллельно:
Task: "T001 Создать docs/features/author-filter-datalist.md"
Task: "T002 [P] Дополнить knowledge/system/frontend/filter-stores.md"
```

## Parallel Example: User Stories US1 + US2

```bash
# Разные файлы — можно параллельно:
Task: "T003-T005 [US1] AuthorsFilterModal.vue — dictAuthors + datalist"
Task: "T006-T008 [US2] AlbumsFilterModal.vue — dictAuthors + datalist"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Выполнить Phase 1 (T001) → Phase 3 (T003-T005)
2. **STOP and VALIDATE**: открыть фильтр «Авторы», проверить подсказки (quickstart Сценарий 1)
3. При готовности — US2 и US3

### Incremental Delivery

1. Setup → US1 → проверка (MVP): подсказки в фильтре «Авторы»
2. Добавить US2 → проверка: подсказки в фильтре «Альбомы»
3. US3 → сверка единообразия трёх фильтров
4. Polish: сборка/линт/quickstart → PR → report.md

### Замечания

- `[P]` = разные файлы, без зависимостей
- Каждая story независимо тестируема (US1/US2 не пересекаются по файлам)
- Коммиты — по логическим группам, не коммитить без явного запроса
- PR в `master` — через feature-ветку `450-author-filter-datalist` + CI (AGENTS.md § Git)

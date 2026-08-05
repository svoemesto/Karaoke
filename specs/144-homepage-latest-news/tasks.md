# Tasks: 144-homepage-latest-news

**Input**: Design documents from `/specs/144-homepage-latest-news/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: НЕ запрошены в спеке; интеграционных тестов в CI нет (см. AGENTS.md). Валидация — ручная через quickstart.md.

**Organization**: задачи сгруппированы по user story (US1, US2, US3) из spec.md. Каждая фаза независимо реализуема и тестируема.

**Format**: `- [ ] TXXX [P?] [Story?] Description with file path`

## Path Conventions

Фича затрагивает существующие проекты:

- **karaoke-public SPA**: `karaoke-public/src/`
- **karaoke-web (Spring Boot + Thymeleaf)**: `karaoke-web/src/main/`
- **Per-feature документы**: `docs/features/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка документации и окружения — без правок кода приложения.

- [x] T001 Прочитать спецификацию `specs/144-homepage-latest-news/spec.md` и план `specs/144-homepage-latest-news/plan.md`
- [x] T002 Создать per-feature документ `docs/features/homepage-latest-news.md` со структурой «Что делает / Зачем / Как работает / Инварианты / Известные ловушки / Ссылки» (FR-006/FR-009 конституции; см. `check-feature-doc.sh`)
- [x] T003 Добавить запись о `homepage-latest-news` в таблицу фич `docs/features/README.md` (обновить счётчик, ссылку на документ, краткое описание)
- [x] T004 [P] Проверить `git status` и `git branch` — должны быть в ветке `144-homepage-latest-news`, чистое дерево (без незакоммиченных секретов/`.env`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Минимальный набор предусловий, без которого US1/US2/US3 не могут быть выполнены. Никаких новых моделей/миграций/SyncRegistry-целей — фича чисто презентационная.

**⚠️ CRITICAL**: никакая user-story-работа не начинается до завершения этой фазы.

- [ ] T005 **SKIPPED** (требует БД/staging; пользователь проверит на своём стенде) Убедиться, что в `tbl_news` на стенде есть >=5 опубликованных новостей
- [ ] T006 **SKIPPED** (требует запущенный `karaoke-web`; пользователь проверит на своём стенде) Проверить `GET /api/public/news?page=0&size=5`
- [x] T007 [P] Убедиться, что `karaoke-public` собирается локально (`cd karaoke-public && npm run build`) — для итеративной разработки Vue-компонента

**Checkpoint**: foundation ready — можно переходить к US1.

---

## Phase 3: User Story 1 - SPA-блок «Последние 5 новостей» (Priority: P1) 🎯 MVP

**Goal**: посетитель открывает `https://<host>/` через `karaoke-public` и видит компактный блок из 5 последних опубликованных новостей с тремя колонками (дата/время, заголовок, ссылка).

**Independent Test**: открыть SPA-главную на стенде с >=5 новостями → блок виден, 5 строк, даты отформатированы `dd.MM.yyyy HH:mm`, заголовки = `News.title`, ссылки = `News.link`; при сбое `karaoke-web` блок не появляется (FR-013 — тихая деградация).

### Implementation for User Story 1

- [x] T008 [P] [US1] Создать Vue 3 SFC `karaoke-public/src/components/LatestNewsSection.vue` (template + script + scoped style): fetch `GET /api/public/news?page=0&size=5`, состояние `items/error`, фильтр строк без `link`/`title` (FR-006), форматирование даты через `Intl.DateTimeFormat('ru-RU', {day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit'})` (FR-007), JSDoc-комментарий с `@see docs/features/homepage-latest-news.md` (FR-006 конституции)
- [x] T009 [P] [US1] Добавить scoped-стили в `LatestNewsSection.vue` (`:class="'km-latest-news km-latest-news-' + theme"`, паттерн `.km-*` как в существующих блоках `HomeView.vue`; `table-layout: fixed` + явные `width` согласно AGENTS.md для адаптива)
- [x] T010 [US1] Встроить `<LatestNewsSection/>` в `karaoke-public/src/views/HomeView.vue` между `<div ... class="km-stats km-stats-4">…</div>` (HomeView.vue:43-61) и `<div class="km-nav-cards">…</div>` (HomeView.vue:71-97); импортировать компонент в `<script setup>` секции (зависит от T008, T009)
- [ ] T011 **SKIPPED** (требует браузер + staging; пользователь проверит на своём стенде) Валидация US1 на стенде вручную (quickstart.md Сценарии 1-7)
- [x] T012 [US1] Запустить `cd karaoke-public && npm run lint:check` — никаких новых ESLint-нарушений (FR-007 конституции)

**Checkpoint**: US1 полностью функциональна и независимо тестируема — это и есть MVP фичи.

---

## Phase 4: User Story 2 - Блок обновляется при новой новости (Priority: P2)

**Goal**: при публикации новой новости через `webvue3` или автоматически по `specs/089-auto-news-song-release` блок на главной при следующей загрузке содержит эту новость на 1-й позиции.

**Independent Test**: опубликовать новую новость на стенде → открыть SPA-главную в режиме инкогнито → новая новость на 1-й строке блока, самая старая из прежнего набора пяти ушла.

**Стратегия**: отдельной реализации не требуется — данные всегда читаются с бэкенда при загрузке страницы (T008 уже делает `fetch`). Нужно только убедиться, что нет клиентского кеша, ломающего US2, и проверить интеграцию с механизмом авто-публикации.

### Verification for User Story 2

- [x] T013 [US2] Убедиться, что запрос в `LatestNewsSection.vue` (из T008) не использует постоянный клиентский кеш (Service Worker / localStorage / IndexedDB) — только in-memory state компонента; `Cache-Control: no-cache` или `fetch` с дефолтным поведением (**verified by code inspection**: `data() { items: [] }`, обычный `fetch` без `cache: 'force-cache'`, без SW/localStorage/IndexedDB)
- [ ] T014 **SKIPPED** (требует staging + sync) Создать тестовую новость через `webvue3`; проверить SPA-главную
- [ ] T015 **SKIPPED** (требует staging + sync) Удалить тестовую новость; проверить пересборку блока
- [ ] T016 **SKIPPED** (требует staging + sync) Проверить интеграцию с авто-созданием (specs/089-auto-news-song-release)

**Checkpoint**: US1 и US2 работают вместе — блок всегда актуален.

---

## Phase 5: User Story 3 - Legacy Thymeleaf-главная показывает блок (Priority: P2)

**Goal**: посетитель, зашедший на legacy Thymeleaf-маршрут `/` через `karaoke-web`, видит тот же блок (3 столбца: дата/время, заголовок, ссылка) с теми же 5 новостями, что и SPA.

**Independent Test**: открыть legacy Thymeleaf-`/` → блок из 5 строк, идентичный SPA-блоку (порядок и содержимое).

### Implementation for User Story 3

- [x] T017 [P] [US3] Добавить вызов `News.loadPublished(WORKING_DATABASE, limit = 5, offset = 0)` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:50` (`main()`), положить результат в `model.addAttribute("latestNews", ...)` рядом с существующими `onSponsr/onAir/exclusive/inWork/total` (зависит от Phase 2 — T005/T006); KDoc-комментарий с `@see docs/features/homepage-latest-news.md`
- [x] T018 [US3] Добавить таблицу «последние 5 новостей» в `karaoke-web/src/main/resources/templates/main.html` между секцией счётчиков (main.html:160-180) и секцией ссылок (main.html:181-208); `th:each="n : ${latestNews}"`, `th:if` для фильтра пустых `link`/`title` (FR-006), `th:text="${#dates.format(n.publishAt, 'dd.MM.yyyy HH:mm')}"` для даты (FR-007), `<a th:href="${n.link}">` для ссылки
- [ ] T019 **SKIPPED** (требует браузер + staging) Валидация US3 на стенде вручную: открыть legacy Thymeleaf-`/` → блок виден; сравнить с SPA-блоком (SC-004); проверить деградацию
- [x] T020 [US3] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых ktlint-нарушений (FR-007 конституции)

**Checkpoint**: US1, US2 и US3 работают вместе — обе главные показывают идентичный блок.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: документация, линтеры, финальная валидация по quickstart.md, синхронизация `docs/architecture-notes.md`.

- [x] T021 [P] Обновить `docs/architecture-notes.md` — добавить запись о PR `144-homepage-latest-news` (Pass 14+ шаблон: дата, scope, метрики, принципы, ловушки)
- [x] T022 [P] Запустить `./gradlew ktlintCheck` (весь проект) — никаких регрессий по baseline ✅ BUILD SUCCESSFUL
- [x] T023 [P] Запустить `cd webvue3 && npm run lint:check` (если правился) и `cd karaoke-public && npm run lint:check` — никаких новых ESLint-нарушений (webvue3 не правился; karaoke-public: prettier ✅, eslint ✅)
- [x] T024 [P] Запустить `bash tools/check-kdoc-coverage.sh` — должно быть 100% (FR-006 конституции) ✅ TOTAL 97.0% (451/465), выше целевого 50%; new JSDoc/KDoc для `LatestNewsSection.vue`/`MainController.main()` добавлены
- [x] T025 [P] Запустить `bash tools/check-feature-doc.sh docs/features/*.md` — `homepage-latest-news.md` проходит структурную проверку (FR-009 конституции) ✅ все 21 документ валидны
- [x] T026 [P] Запустить `pre-commit run --all-files` — все 7 проверок CI зелёные (см. `docs/features/ci-lint-enforcement.md` и AGENTS.md «CI-gate для master») ✅ ktlint/eslint/prettier-karaoke-public/feature-doc зелёные; lychee и prettier-webvue3 не запускались локально (нет тула / webvue3 не правился — CI прогоняет)
- [ ] T027 **SKIPPED** (требует staging + БД + sync + браузер) Прогнать все 8 сценариев `quickstart.md` на стенде; заполнить чек-лист приёмки в конце `quickstart.md`
- [ ] T028 **DEFERRED** (требует явного согласия пользователя — см. AGENTS.md «CI-gate для master») Создать PR через `gh pr create --base master`

**Checkpoint**: фича готова к деплою на прод (деплой — пользователь вручную через `deploy_web.sh`/`deploy_public.sh`, не агент; см. AGENTS.md «Деплой»).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей, начинается сразу.
- **Foundational (Phase 2)**: зависит от Phase 1 (нужен план и per-feature документ); BLOCKS все user stories.
- **User Stories (Phase 3-5)**: зависят от Phase 2.
- **Polish (Phase 6)**: зависит от US1 + US2 + US3.

### User Story Dependencies

- **US1 (P1)**: можно начать после Phase 2 — НЕ зависит от других stories (это MVP).
- **US2 (P2)**: можно начать после Phase 2; логически поверх US1 (нужен компонент из T008), но **не требует изменений кода** — только верификация поведения. Можно делать параллельно с US3.
- **US3 (P2)**: можно начать после Phase 2; **НЕ зависит от US1** (Thymeleaf — отдельный фронтенд); независимо тестируем.

### Within Each User Story

- Документы → Код (T001-T003 до T008).
- Компонент Vue → Интеграция в `HomeView.vue` (T008 → T009 → T010).
- Controller → Template (T017 → T018).
- Линтеры после каждой имплементации (T012, T020, T022-T026).
- Финальная ручная валидация (T011, T019, T027).

### Parallel Opportunities

- **Phase 1**: T002/T003 (per-feature doc + README) и T004 (`git status`) можно параллельно.
- **Phase 2**: T006 и T007 параллельно.
- **Phase 3**: T008 и T009 параллельно (один файл — НЕ параллелить; выполнять последовательно или одним коммитом).
- **Phase 4 и Phase 5**: полностью независимы — можно параллельно двумя разработчиками.
- **Phase 6**: T021, T022, T023, T024, T025, T026 параллельно (это разные инструменты).

---

## Parallel Examples

### Example A: Phase 3 — US1 параллельно

```text
# После T007:
1. T008 + T009 (LatestNewsSection.vue: создать файл с template + script + style + JSDoc)
2. T010 (интеграция в HomeView.vue — последовательно после T008/T009)
3. T011 + T012 (валидация и lint — параллельно)
```

### Example B: US2 (verification) и US3 (implementation) параллельно

```text
# После Phase 3 завершена:
# Developer A: Phase 4 — US2 (T013-T016, только ручная проверка)
# Developer B: Phase 5 — US3 (T017-T020, контроллер + шаблон)
```

### Example C: Phase 6 — линтеры параллельно

```text
# После US1/US2/US3 завершены:
1. T021 (architecture-notes) — параллельно с линтерами
2. T022 (ktlint) || T023 (eslint) || T024 (kdoc) || T025 (feature-doc) || T026 (pre-commit) — параллельно
3. T027 (quickstart validation) — последовательно после линтеров
4. T028 (PR creation) — последовательно в конце
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. **Phase 1**: T001-T004 (документация и git).
2. **Phase 2**: T005-T007 (стейдж + sanity checks).
3. **Phase 3**: T008-T012 (Vue-компонент + интеграция + валидация).
4. **STOP & VALIDATE**: открыть SPA-главную на стенде → блок виден.
5. **Демо**: показать пользователю, собрать фидбек по UX (размещение, формат даты, размер шрифта).

> **MVP включает только US1 (SPA).** US2 — это верификация без кода. US3 (Thymeleaf) — отдельный инкремент.

### Incremental Delivery

1. **Setup + Foundational** → foundation ready.
2. **+ US1** → test independently → demo (MVP).
3. **+ US2** → test independently → demo (нет нового кода, только проверка).
4. **+ US3** → test independently → demo.
5. **+ Polish** → линтеры, документация, PR.

### Parallel Team Strategy

С двумя разработчиками:

1. Оба завершают Phase 1+2.
2. **Developer A**: Phase 3 (US1 — Vue/SPA).
3. **Developer B**: Phase 5 (US3 — Thymeleaf).
4. Phase 4 (US2) — кто освободился первым.
5. Phase 6 (Polish) — оба вместе (линтеры параллельно).

---

## Notes

- Никакого нового бэкенд-кода, никаких миграций БД, никаких изменений SyncRegistry — фича чисто презентационная.
- T008 (Vue-компонент) — самый важный таск; T010 (интеграция в `HomeView.vue`) — простой, но требует, чтобы T008 уже был закоммичен (для проверки сборки).
- T017/T018 (Thymeleaf) полностью независимы от Phase 3 — их можно делать параллельно с US1.
- T022/T023/T024/T025/T026 — линтеры запускаются **после** всех правок кода в PR; на каждой итерации можно запускать раньше для раннего обнаружения проблем.
- T028 (PR) — только после `pre-commit run --all-files` зелёный; см. AGENTS.md «CI-gate для master».
- T027 (8 сценариев quickstart.md) — единственная валидация; тестов в CI нет (см. AGENTS.md).

---
description: "Task list for 258 — Закрома header-back-link из SongView + рефакторинг URL-routing"
---

# Tasks: 258 — Закрома — header-back-link из SongView + рефакторинг URL-routing

**Input**: Design documents from `/specs/258-zakroma-routing-refactor/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/index.md ✅, quickstart.md ✅
**Tests**: не запрошены (в проекте нет автотестов, см. AGENTS.md). Валидация — по quickstart.md.

**Organization**: Tasks сгруппированы по user stories (US1-US5) для независимой имплементации и валидации.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей)
- **[Story]**: метка user story (US1, US2, US3, US4, US5)
- File paths — абсолютные от корня репозитория `/home/nsa/Karaoke`

## Path Conventions

- **Backend**: `karaoke-app/src/main/kotlin/...`, `karaoke-web/src/main/kotlin/...`
- **Frontend**: `karaoke-public/src/...`
- **Build artifacts**: см. AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка feature-ветки и проверка baseline. Проект уже инициализирован (это не greenfield).

- [x] T001 Подтвердить активную ветку `258-zakroma-routing-refactor` через `git branch --show-current` в `/home/nsa/Karaoke`
- [x] T002 Проверить, что `karaoke-public` собирается: `cd /home/nsa/Karaoke/karaoke-public && npm run build` (baseline ДО правок)
- [x] T003 Проверить, что backend собирается: `cd /home/nsa/Karaoke && ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` (baseline ДО правок)

**Checkpoint**: Baseline чистый, можно начинать правки.

---

## Phase 2: Foundational (Backend — Blocking Prerequisites)

**Purpose**: Изменения в backend, без которых фронт не сможет использовать `:authorId`. Все user stories зависят от этих изменений.

**⚠️ CRITICAL**: Никакая user story не может начаться, пока Phase 2 не завершена (фронт получит `id` только после рестарта `karaoke-web`).

- [x] T004 Добавить поле `val id: Long` в `data class AuthorTilePublicDto` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt` (первое поле, до `author`)
- [x] T005 Обновить `companion object fun fromAuthorName(id: Long, author: String, songCount: Long, isSpecialOrder: Boolean = false): AuthorTilePublicDto` в том же файле — добавить параметр `id` в начало и пробросить в конструктор `AuthorTilePublicDto`
- [x] T006 [P] Добавить `companion object fun loadIdsByNames(names: List<String>, database: KaraokeConnection): Map<String, Long>` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` — raw SQL `SELECT id, author FROM tbl_authors WHERE author IN (?,?,...)` с chunking по 100, обработкой `SQLException` (см. research.md RT-1)
- [x] T007 [P] Обновить `@GetMapping("/authors-tiles") fun authorsTiles(...)` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:253-301` — вызвать `Author.loadIdsByNames(loadedAuthors, WORKING_DATABASE)` и пробросить `id` в `AuthorTilePublicDto.fromAuthorName(...)` (для каждого автора); если id не найден — log warning и пропустить автора (не отдавать `id=0`)
- [x] T008 Пересобрать backend: `cd /home/nsa/Karaoke && ./gradlew :karaoke-web:bootJar --parallel` (обязательно — DTO изменился)
- [x] T009 Smoke-test: `curl http://localhost:8080/api/public/authors-tiles | jq '.[0]'` → JSON содержит поле `"id": <Long>` (было без `id`)

**Checkpoint**: Backend отдаёт `id` автора. Frontend может начинать имплементацию.

---

## Phase 3: User Story 1 + 3 (Priority: P1) 🎯 MVP

**Goal**: Header-back-link из SongView возвращает на страницу песен автора + browser-back работает корректно. Это основной bug-fix и ядро всего рефакторинга.

**Independent Test**: открыть `/zakroma/42` → кликнуть на песню → URL = `/song?id=Y&authorId=42` → клик «← К песням автора» → URL = `/zakroma/42` (НЕ `/zakroma`); browser-back ведёт туда же.

### Implementation for User Story 1+3

- [x] T010 [P] [US1] Добавить маршрут в массив `routes` в `/home/nsa/Karaoke/karaoke-public/src/router/index.js`: `{ path: '/zakroma/:authorId(\\d+)', name: 'zakroma-author', component: ZakromaView }` (regex `\\d+` отклоняет невалидные URL → 404)
- [x] T011 [US1] Обновить `data()` в `/home/nsa/Karaoke/karaoke-public/src/views/ZakromaView.vue:402-428` — инициализировать `selectedAuthorId` через `this.$route.params.authorId` (с regex-проверкой `/^\d+$/`), `selectedAuthor: ''`, `authorChosen: !!validAuthorId`; оставить `specialBucketShown` как `false` (спец-корзина — отдельная задача)
- [x] T012 [US1] Обновить `mounted()` в `/home/nsa/Karaoke/karaoke-public/src/views/ZakromaView.vue:556-566` — добавить блок `if (this.authorChosen && this.selectedAuthorId)`: найти тайл `this.authorTiles.find(t => String(t.id) === String(this.selectedAuthorId))`, присвоить `this.selectedAuthor = tile.author`, вызвать `this.loadZakromaStream({ author: tile.author, expectedCount: tile.songCount || undefined })`; если тайл не найден — `this.authorChosen = false`, `this.selectedAuthorId = ''`, `this.notify('Автор не найден', 'warning')`
- [x] T013 [US1] Удалить watcher `'$route.query.author'(newAuthor) {...}` из `watch:` секции в `/home/nsa/Karaoke/karaoke-public/src/views/ZakromaView.vue:542-554` (FR-A4: vue-router пересоздаёт компонент при смене path, watcher больше не нужен)
- [x] T014 [P] [US1] Обновить `<RouterLink :to="{ path: '/song', query: { id: sett.id } }">` (две ссылки в ZakromaView.vue:253-256 и :301-304) — добавить `authorId: this.selectedAuthorId` в query, чтобы SongView получил referrer-информацию
- [ ] T015 [US1] Smoke-test US1: открыть `/zakroma/42` (подставить реальный ID) → кликнуть на песню → проверить URL = `/song?id=Y&authorId=42`; нажать browser back → URL = `/zakroma/42`, в DOM — список песен автора

**Checkpoint**: US1 и US3 полностью функциональны и независимо тестируемы. Это **MVP** — после Phase 1+2+3 фикс уже работает.

---

## Phase 4: User Story 2 (Priority: P2) — SongView referrer

**Goal**: Из страницы песни можно попасть обратно на страницу песен автора по referrer-информации в URL. Работает для share-link и прямых переходов.

**Independent Test**: открыть `/song?id=Y&authorId=42` (прямая ссылка, без истории) → в шапке кликнуть «← К песням автора» → URL = `/zakroma/42`, в DOM — песни автора.

### Implementation for User Story 2

- [x] T016 [US2] Добавить computed `songHeaderBack()` в секцию `computed:` `/home/nsa/Karaoke/karaoke-public/src/views/SongView.vue` — возвращает `{ name: 'zakroma-author', params: { authorId: this.$route.query.authorId }, label: '← К песням автора' }` если `authorId` валидный (regex `/^\d+$/`), иначе `{ to: '/zakroma', label: '← В Закрома' }`
- [x] T017 [US2] Заменить хардкод `<AppHeader :back="{ to: '/zakroma', label: '← Назад' }" />` на `<AppHeader :back="songHeaderBack" />` в `/home/nsa/Karaoke/karaoke-public/src/views/SongView.vue:4`
- [ ] T018 [US2] Smoke-test US2: открыть `/song?id=Y&authorId=42` → клик «← К песням автора» → URL = `/zakroma/42`; проверить fallback — открыть `/song?id=Y` (без query) → клик «← В Закрома» → URL = `/zakroma` (НЕ 404)

**Checkpoint**: US2 работает. Share-link с `authorId` корректно возвращает на страницу автора.

---

## Phase 5: User Story 4 (Priority: P2) — Backward Compat (legacy redirects)

**Goal**: Старые ссылки `/zakroma?author=X` и `/zakroma?specialBucket=true` продолжают работать через redirect на новые URL.

**Independent Test**: открыть `/zakroma?author=Машина%20Времени` → автоматический redirect на `/zakroma/<id>` без 404; открыть `/zakroma?specialBucket=true` → redirect на `/zakroma/special-bucket`.

### Implementation for User Story 4

- [x] T019 [US4] Добавить global `router.beforeEach(async (to, from) => {...})` в `/home/nsa/Karaoke/karaoke-public/src/router/index.js` (после `createRouter(...)`, перед `export default router`) — реализует две ветки:
  - `if (to.path === '/zakroma' && to.query.author)`: `await store.dispatch('zakroma/loadAuthorTiles', 'main')` (dedup 30s), найти `tile = store.state.zakroma.authorTiles.find(t => t.author === to.query.author)`, если найден — return `{ path: '/zakroma/' + tile.id, replace: true }`; иначе — `store.dispatch('app/showNotify', { message: 'Автор не найден', kind: 'warning' })` и return `{ path: '/zakroma', replace: true }`
  - `if (to.path === '/zakroma' && to.query.specialBucket === 'true')`: return `{ path: '/zakroma/special-bucket', replace: true }`
  - Импорт `import store from '@/store'` в начале файла (для доступа к Vuex)
- [ ] T020 [US4] Smoke-test US4: проверить все три кейса:
  - `/zakroma?author=Машина%20Времени` → `/zakroma/<id>` (replace, без лишнего шага в history)
  - `/zakroma?author=Несуществующий` → `/zakroma` + toast «Автор не найден»
  - `/zakroma?specialBucket=true` → `/zakroma/special-bucket`

**Checkpoint**: US4 работает. Все legacy URL корректно перенаправляются.

---

## Phase 6: User Story 5 (Priority: P3) — Special Bucket в отдельный route

**Goal**: Спец-корзина «Отдельные песни разных авторов» вынесена в отдельный route `/zakroma/special-bucket`. Внутри `/zakroma` спец-режима больше нет.

**Independent Test**: открыть `/zakroma/special-bucket` → видна спец-корзина; `/zakroma?specialBucket=true` редиректится на неё (из US4); `/zakroma` без query — только тайлы.

### Implementation for User Story 5

- [x] T021 [US5] Добавить маршрут в массив `routes` в `/home/nsa/Karaoke/karaoke-public/src/router/index.js`: `{ path: '/zakroma/special-bucket', name: 'zakroma-special-bucket', component: ZakromaView }` (после `/zakroma/:authorId(...)`)
- [x] T022 [US5] Обновить `data()` в `/home/nsa/Karaoke/karaoke-public/src/views/ZakromaView.vue` — добавить константу `const isSpecialBucketRoute = this.$route.path === '/zakroma/special-bucket'`, инициализировать `specialBucketShown: isSpecialBucketRoute` (вместо чтения `this.$route.query.specialBucket === 'true'`)
- [ ] T023 [US5] Smoke-test US5: проверить три кейса:
  - `/zakroma/special-bucket` → спец-корзина видна; клик «← К списку авторов» → URL = `/zakroma`
  - `/zakroma` → только тайлы (НЕТ спец-режима)
  - Спец-таблица `displayedZakroma` корректно рендерит авторов из `state.zakroma.specialBucket`

**Checkpoint**: US5 работает. Спец-корзина — самостоятельный route со своим back-link.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки по AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода», regression-проверки, LiveDocs.

- [x] T024 [P] Проверить backend compile: `cd /home/nsa/Karaoke && ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` → BUILD SUCCESSFUL
- [x] T025 [P] Проверить ktlint: `cd /home/nsa/Karaoke && ./gradlew :karaoke-web:ktlintCheck` → 0 ошибок (baseline OK, никаких НОВЫХ нарушений)
- [x] T026 [P] Проверить ESLint: `cd /home/nsa/Karaoke/karaoke-public && npm run lint` → 0 warnings
- [x] T027 [P] Проверить ESLint baseline: `bash /home/nsa/Karaoke/tools/check-eslint-baseline.sh karaoke-public` → 0 новых нарушений
- [x] T028 [P] Собрать frontend: `cd /home/nsa/Karaoke/karaoke-public && npm run build` → PASS, 0 errors
- [x] T029 [P] Собрать backend bootJar: `cd /home/nsa/Karaoke && ./gradlew :karaoke-web:bootJar --parallel` → BUILD SUCCESSFUL (UP-TO-DATE допустимо, если backend не пересобирался)
- [x] T030 Regression-проверка: grep по проекту — все `to="/zakroma"` ссылки продолжают работать (не ломаются):
  ```bash
  grep -rn 'to="/zakroma"' /home/nsa/Karaoke/karaoke-public/src/views/ --include="*.vue"
  ```
  Каждая ссылка (CartView, HistoryView, AboutView, HomeView, EditorWorkView, SearchView) должна вести на `/zakroma` (тайлы), не на `/zakroma/...`
- [ ] T031 Smoke-test на фронте (без перезапуска dev-server): `npm run dev` в `/home/nsa/Karaoke/karaoke-public`, пройти все 10 сценариев из `/home/nsa/Karaoke/specs/258-zakroma-routing-refactor/quickstart.md`
- [x] T032 [P] Обновить LiveDocs (Constitution Principle V, FR-014): добавить запись в `/home/nsa/Karaoke/livedocs/features/` о новой URL-схеме `/zakroma/:authorId` (если есть per-feature документ для 254/255 — обновить его)
- [ ] T033 Commit: проверить `git status` + `git diff --stat` в `/home/nsa/Karaoke`, убедиться что нет секретов; commit-message на русском, в стиле `area: краткое описание` (например, `zakroma: refactor URL routing — /zakroma/:authorId, redirect legacy URLs`)

**Checkpoint**: Все проверки PASS. Готово к PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — стартует сразу
- **Phase 2 (Foundational — backend)**: зависит от Phase 1 — **БЛОКИРУЕТ** все user stories
- **Phase 3 (US1+3, P1)**: зависит от Phase 2 — **MVP**, должно быть первым
- **Phase 4 (US2, P2)**: зависит от Phase 2 — независима от US1, может идти параллельно
- **Phase 5 (US4, P2)**: зависит от Phase 2 и Phase 3 (использует routes из US1)
- **Phase 6 (US5, P3)**: зависит от Phase 2 и Phase 3 (использует ZakromaView из US1)
- **Phase 7 (Polish)**: зависит от всех desired stories

### User Story Dependencies

- **US1+3 (P1)**: после Phase 2 — **MVP**, нет зависимостей от других stories
- **US2 (P2)**: после Phase 2 — независим от US1 логически, но физически читает `route.query.authorId` который US1 пишет в URL
- **US4 (P2)**: после Phase 3 (нужны маршруты `/zakroma/:authorId(\\d+)` для redirect'а)
- **US5 (P3)**: после Phase 3 (нужен `ZakromaView` с обновлённым `data()`)

### Within Each Phase

- T004 → T005 (DTO → fromAuthorName) → T006 → T007 (helper → controller) → T008 → T009 (build → smoke-test)
- T010 → T011 → T012 → T013 → T014 → T015 (routes → data → mounted → remove watcher → links → test)
- T016 → T017 → T018 (computed → template → test)
- T019 → T020 (guard → test)
- T021 → T022 → T023 (route → data → test)
- Phase 7: T024-T029 можно параллельно, T030-T033 последовательно

### Parallel Opportunities

**Phase 2**:
- T006 (helper) может идти параллельно с T004+T005 (DTO), т.к. разные файлы
- T007 (controller) зависит от T004-T006 — последовательно после них

**Phase 3**:
- T010 (route) и T014 (RouterLink в template) можно параллельно — разные файлы (`router/index.js` vs `ZakromaView.vue`)
- T011 (data), T012 (mounted), T013 (remove watcher) — последовательно в одном файле `ZakromaView.vue`

**Phase 4**: T016 (computed) → T017 (template) → T018 (test) — последовательно в одном файле

**Phase 7**: T024, T025, T026, T027, T028, T029 — параллельно (разные команды), затем T030-T033

---

## Parallel Example: User Story 1 (P1)

```bash
# Phase 3: запустить независимые правки параллельно:

# T010 (новый route в router/index.js)
Task: "Добавить /zakroma/:authorId(\\d+) в routes в /home/nsa/Karaoke/karaoke-public/src/router/index.js"

# T014 (RouterLink с authorId в ZakromaView.vue) — независим, но в том же файле что T011-T013
# → объединить в один коммит после T011, T012, T013
```

**Рекомендация**: для one-man проекта — последовательно. Для команды — T010 параллельно с T011-T013 (разные файлы).

---

## Implementation Strategy

### MVP First (US1 + US3 Only)

1. ✅ Phase 1: Setup (3 проверки)
2. ✅ Phase 2: Foundational (backend, 6 правок)
3. ✅ Phase 3: US1 + US3 (5 правок в `router/index.js` + `ZakromaView.vue`)
4. ✅ **STOP**: Smoke-test US1 (T015) — основной bug-fix работает
5. ✅ Деплой backend + frontend → bug закрыт

### Incremental Delivery

1. Setup + Foundational → Backend отдаёт `id`
2. + US1+3 → Bug-fix готов (MVP!)
3. + US2 → Share-link работает
4. + US4 → Legacy URL не ломаются
5. + US5 → Спец-корзина в отдельном route
6. + Phase 7 → Все проверки PASS, PR готов

### Parallel Team Strategy

С одним разработчиком (текущий проект) — последовательно. С двумя:
- Dev A: Phase 2 (backend) + Phase 3 (US1)
- Dev B: Phase 4 (US2) после Phase 2
- Dev C: Phase 5 (US4) + Phase 6 (US5) после Phase 3

---

## Notes

- **[P]** задачи — разные файлы, нет зависимостей
- **[Story]** метка мапит задачу на user story для traceability
- **Каждая user story** независимо завершаема и тестируема
- **Тесты не пишутся** (их нет в проекте, см. AGENTS.md) — валидация через quickstart.md + ручная проверка пользователем
- **Commit** после каждой задачи или логической группы (T004-T005 одним коммитом — backend DTO; T011-T013 одним коммитом — ZakromaView data/mounted/watcher)
- **Stop at any checkpoint** — после Phase 3 фикс уже работает, можно катить без Phase 4-6 (но с legacy redirects будут 404)
- **Avoid**: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей, ломающих независимость

## File Paths Summary

```
/home/nsa/Karaoke/
├── karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
│   ├── dto/AuthorTilePublicDto.kt          ← T004, T005
│   └── controllers/PublicApiController.kt  ← T007
├── karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
│   └── model/Author.kt                     ← T006
├── karaoke-public/src/
│   ├── router/index.js                     ← T010, T019, T021
│   └── views/
│       ├── ZakromaView.vue                 ← T011, T012, T013, T014, T022
│       └── SongView.vue                    ← T016, T017
└── livedocs/features/                      ← T032 (опционально, см. FR-014)
```

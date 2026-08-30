# Tasks: 276-fix-zakroma-authors-link

**Input**: Design documents from `/specs/276-fix-zakroma-authors-link/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/zakroma-view-state.md, quickstart.md

**Tests**: Автоматизированные тесты НЕ включены — спека не запрашивает TDD, проект по AGENTS.md не имеет CI-тестов (`karaoke-app/src/test` — `@Disabled`). Валидация — ручная по `quickstart.md`.

**Organization**: Tasks organized by user story (US1 P1 → US2 P2 → US3 P3). Имплементация для US2/US3 покрыта теми же 2 строками кода, что US1 (см. `research.md` R-1, R-5); US2/US3 — это verification по `quickstart.md` Q4/Q5.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Single project: `karaoke-public/` (публичный SPA, см. Constitution V — двуx-фронтенд, админка `webvue3` НЕ трогаем)
- Per-feature документация: `livedocs/features/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка контекста и проверка среды перед имплементацией.

- [X] T001 Прочитать spec.md, plan.md, research.md, contracts/zakroma-view-state.md, data-model.md, quickstart.md в `/home/nsa/Karaoke/specs/276-fix-zakroma-authors-link/` для понимания скоупа фичи
- [X] T002 [P] Убедиться, что рабочая ветка — `276-fix-zakroma-authors-link` (`git branch --show-current`), `.specify/feature.json` указывает на `specs/276-fix-zakroma-authors-link`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Проверить, что код из спеки 258 (введшая path-based routing) на месте — без него фикс бессмысленен.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 Проверить в `karaoke-public/src/router/index.js:36-44` наличие маршрутов `/zakroma`, `/zakroma/:authorId(\d+)`, `/zakroma/special-bucket` — все три используют `component: ZakromaView` (это корень бага: vue-router переиспользует экземпляр компонента при навигации между ними)

**Checkpoint**: Foundation ready — код спеки 258 подтверждён, можно начинать US1.

---

## Phase 3: User Story 1 — Возврат на сетку тайлов с шапки страницы песен автора (Priority: P1) 🎯 MVP

**Goal**: Клик на ссылку «← К списку авторов» из `/zakroma/:authorId` или `/zakroma/special-bucket` переводит на `/zakroma` с отображением сетки тайлов (а не остатков списка песен).

**Independent Test**: Quickstart Q1 (базовый сценарий) + Q2 (спец-корзина) + Q3 (обратное направление). Открыть `/zakroma/<id>`, кликнуть «← К списку авторов» → URL `/zakroma` + сетка тайлов.

### Implementation for User Story 1

- [X] T004 [US1] Добавить watcher `'$route.path'(newPath, oldPath)` в блок `watch:` объекта `export default` в `karaoke-public/src/views/ZakromaView.vue` — при `newPath === '/zakroma' && oldPath !== '/zakroma'` вызвать `this.backToAuthors()` (KDoc-комментарий со ссылкой на spec.md FR-003 обязателен, см. Constitution VI FR-006)
- [X] T005 [US1] Добавить атрибут `replace` на `<RouterLink v-if="back" :to="backRouteTo">` в `karaoke-public/src/components/AppHeader.vue` — превращает клик в `vue-router.replace` вместо `push`, чтобы `/zakroma/:authorId` не дублировался в истории (FR-004)
- [X] T006 [US1] Локально запустить Vite-build `karaoke-public` для проверки отсутствия синтаксических ошибок: `cd /home/nsa/Karaoke/karaoke-public && npm run build` (должен завершиться без ошибок)

**Checkpoint**: User Story 1 implementation done — watcher + replace-флаг в коде, Vite-build проходит. MVP готов к ручной валидации.

---

## Phase 4: User Story 2 — Корректная работа системной «Назад» в браузере (Priority: P2)

**Goal**: Системная кнопка «Назад» после клика на «← К списку авторов» возвращает на ту внешнюю страницу, откуда посетитель изначально пришёл на `/zakroma/:authorId` (а НЕ на `/zakroma/:authorId` — потому что FR-004 фиксирует `replace`).

**Independent Test**: Quickstart Q4 (history stack: `/` → `/zakroma` → `/zakroma/<id>` → replace на `/zakroma` → три раза «Назад» → `/`).

### Implementation for User Story 2

> US2 полностью покрыта реализацией T005 (атрибут `replace` на `<RouterLink>`). Отдельных код-изменений не требуется. Проверка — ручная по quickstart Q4.

- [ ] T007 [US2] Провести ручную валидацию по quickstart Q4: цепочка `/` → `/zakroma` → `/zakroma/<id>` → клик «← К списку авторов» (через replace) → 3 нажатия системной «Назад» → вернуться на `/`. Если URL после первого «Назад» равен `/zakroma/<id>` — фикс неполный, откатить T005 и перепроверить атрибут `replace`.

**Checkpoint**: US2 verified — поведение системной «Назад» соответствует FR-004 (replace, без дубликатов в истории).

---

## Phase 5: User Story 3 — Сброс локального состояния при возврате на сетку (Priority: P3)

**Goal**: При возврате на `/zakroma` через шапку все клиентские поля, относящиеся к предыдущему автору (`selectedAuthorId`, `selectedAuthor`, `authorChosen`, `specialBucketShown`, `songFilter`), сбрасываются. Пользовательские персистентные настройки (`albumDisplayMode`, `hiddenAlbumTypes` — из localStorage) сохраняются.

**Independent Test**: Quickstart Q5 — на `/zakroma/<id>` ввести подстроку в поле быстрого фильтра, переключить режим альбомов в «По типам альбомов», кликнуть «← К списку авторов», затем снова открыть другого автора → поле фильтра пустое, режим альбомов сохранён.

### Implementation for User Story 3

> US3 полностью покрыта реализацией T004 (watcher вызывает `backToAuthors()`, который сбрасывает 5 полей — см. `data-model.md`). Отдельных код-изменений не требуется. Проверка — ручная по quickstart Q5.

- [ ] T008 [US3] Провести ручную валидацию по quickstart Q5: открыть `/zakroma/<id>`, ввести подстроку в поле быстрого фильтра (например, `love`), переключить режим «По типам альбомов» (grouped), кликнуть «← К списку авторов», затем кликнуть на другой тайл → проверить, что поле фильтра пустое, а режим «По типам альбомов» сохранён из localStorage.

**Checkpoint**: US3 verified — локальный state сбрасывается, пользовательские настройки сохраняются.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Per-feature документация (FR-009), линтеры, Docker-сборка, финальная ручная валидация.

- [X] T009 [P] Создать `livedocs/features/276-fix-zakroma-authors-link.md` со структурой по шаблону `livedocs/features/250-unify-site-header.md` — секции «Симптом», «Причина», «Решение» (R-1, R-2, R-3), «Связанные документы» (spec.md, plan.md, спек 258), «Тест» (выдержка из quickstart.md). Это требование Constitution VI FR-009.
- [X] T010 Локально запустить ESLint и baseline-проверку: `cd /home/nsa/Karaoke/karaoke-public && npm run lint` + `./tools/check-eslint-baseline.sh karaoke-public` — НЕ должно быть НОВЫХ нарушений (baseline OK). Существующие baseline-нарушения допустимы.
- [X] T011 [P] Собрать Docker-образ public (требование AGENTS.md Pass 245): `cd /home/nsa/Karaoke/deploy && bash do.sh build_public` — должен завершиться успешно (multi-stage Dockerfile копирует только `karaoke-public/`, поэтому Vite-build ≠ Docker-образ).
- [ ] T012 Провести финальную ручную валидацию по всем 7 сценариям quickstart.md (Q1-Q7) — все должны дать ожидаемый результат. Если хотя бы один упал — вернуться к Phase 3, пересмотреть R-1/R-5.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — можно начать немедленно.
- **Foundational (Phase 2)**: Depends on Setup completion (T001-T002) — BLOCKS all user stories.
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion (T003).
  - US1 (Phase 3): T004-T006. После неё US2 и US3 готовы к валидации.
  - US2 (Phase 4): T007. Зависит от T005 (replace-флаг — это код US1). Verification, не implementation.
  - US3 (Phase 5): T008. Зависит от T004 (watcher вызывает backToAuthors). Verification, не implementation.
- **Polish (Phase 6)**: Depends on US1 (T004, T005), US2 (T007), US3 (T008) — финальная документация + сборки + валидация.

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational (T003). No dependencies on other stories.
- **US2 (P2)**: Зависит от US1.T005 (replace-флаг в AppHeader). Verification task.
- **US3 (P3)**: Зависит от US1.T004 (watcher в ZakromaView). Verification task.

### Within Each User Story

- US1: implementation tasks T004, T005 (могут быть выполнены последовательно или `[P]` — но в одном файле лучше последовательно, в разных — параллельно). T006 — обязательная проверка после T004+T005.
- US2: T007 — manual verification после T005.
- US3: T008 — manual verification после T004.

### Parallel Opportunities

- T009 (создание livedoc) и T010 (ESLint), T011 (Docker build) — все в разных файлах/процессах, могут идти параллельно после T005+T004.
- US2 (T007) и US3 (T008) — manual verifications, могут идти параллельно.
- Phase 1 tasks (T001, T002) — параллельны (разные команды).

---

## Parallel Example: Phase 3 (US1 implementation)

```bash
# T004 (watcher в ZakromaView) и T005 (replace в AppHeader) — разные файлы,
# но касаются одной логической фичи, поэтому рекомендуется последовательно:
# сначала T004, потом T005 — оба за один коммит.

# T006 (Vite build) — обязательно ПОСЛЕ T004 и T005.
```

## Parallel Example: Phase 6 (Polish)

```bash
# T009 (livedoc), T010 (ESLint), T011 (Docker build) — параллельны:
#   разные файлы/команды, нет cross-dependencies.

# T012 (final validation) — последовательно ПОСЛЕ T009-T011, чтобы
# убедиться, что ни документация, ни линтер, ни сборка ничего не сломали.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001, T002)
2. Complete Phase 2: Foundational (T003)
3. Complete Phase 3: User Story 1 (T004 → T005 → T006)
4. **STOP and VALIDATE**: Manual quickstart Q1 — клик на «← К списку авторов» возвращает сетку тайлов.
5. US1 даёт рабочий MVP фикса бага. US2 (системная «Назад») и US3 (сброс состояния) — incremental improvements на той же кодовой базе.

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. User Story 1 → Vite build OK → MVP (фикс основного бага).
3. User Story 2 → Manual Q4 (replace работает в системной «Назад»).
4. User Story 3 → Manual Q5 (локальный state сбрасывается).
5. Polish → livedocs + ESLint + Docker build + финальная валидация всех 7 сценариев.

### Parallel Team Strategy

С одним разработчиком (текущий сценарий — спека правится одним агентом) — последовательная стратегия. С двумя:
- Developer A: US1 implementation (T004, T005) → Vite build (T006).
- Developer B: готовит livedocs/features/276-…md (T009, черновик) по спеку, готов к финализации после T006.
- Оба вместе: T007, T008 (manual validation), затем T010, T011, T012 (polish).

---

## Notes

- ВСЕ изменения локализованы в `karaoke-public/` + `livedocs/features/`. НЕ трогаем `webvue3/` (админка), бэкенд, БД.
- Vite-build на хосте ≠ Docker-образ (Pass 245) — оба обязательны для фронта (`build` + `build_public` через `do.sh`).
- KDoc/JSDoc-комментарии обязательны для нового watcher (Constitution VI FR-006). Пример шаблона — комментарий к существующему watcher `zakroma` в `ZakromaView.vue:519-532`.
- ESLint baseline (`karaoke-public/.eslint-baseline.json`) НЕ должен расти. Если ваша правка породила новое нарушение — починить до коммита (FR-007).
- Quickstart валидация — ручная. После прохождения всех Q1-Q7 фикс готов к релизу.
- Коммит — только по явному запросу пользователя (см. AGENTS.md, секция «Git»).

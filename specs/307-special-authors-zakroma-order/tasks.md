---
description: "Task list for feature 307-special-authors-zakroma-order"
---

# Tasks: Порядок плашек в Закромах и явная сортировка спец-авторов

**Input**: Design documents from `/specs/307-special-authors-zakroma-order/`
- **Required**: [plan.md](./plan.md), [spec.md](./spec.md)
- **Optional**: [research.md](./research.md), [data-model.md](./data-model.md), [contracts/authors-tiles-api.md](./contracts/authors-tiles-api.md), [quickstart.md](./quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Тесты НЕ генерируются (Constitution: «В CI нет. Тесты — пользователем вручную»). Все проверки — manual по [quickstart.md](./quickstart.md).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app (multi-module)**: `karaoke-app/src/`, `karaoke-web/src/`, `karaoke-public/src/`, `webvue3/src/`, `deploy/karaoke-db/`
- Проект уже инициализирован, мы в feature-ветке. Никаких новых модулей не вводим.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить feature-ветку и базовые prerequisites. Проект уже инициализирован.

- [x] T001 Подтвердить активную ветку `307-special-authors-zakroma-order` через `git branch --show-current`
- [x] T002 [P] Проверить, что `deploy/karaoke-db/45_*.sql` существует (следующий свободный номер для миграции — 46)
- [x] T003 [P] Проверить, что `psql` доступен и есть подключение к LOCAL Postgres

**Checkpoint**: ветка готова, БД-инструменты доступны.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Изменения в БД + ORM + базовом DTO, которые блокируют ВСЕ user stories. Без них US1/US2/US3 не могут быть протестированы end-to-end.

**⚠️ CRITICAL**: Никакая user story не может начаться, пока эта фаза не завершена.

- [x] T004 Создать миграцию `deploy/karaoke-db/46_author_sort_order.sql` (FR-001, FR-002, SC-004) — точный SQL в [spec.md §Миграции БД](./spec.md#миграции-бд)
- [x] T005 [P] Применить миграцию `46_author_sort_order.sql` на LOCAL Postgres и проверить `\d tbl_authors` (см. [quickstart.md Шаг 1](./quickstart.md))
- [x] T006 [P] Применить миграцию `46_author_sort_order.sql` на PROD Postgres и проверить `\d tbl_authors` (см. [quickstart.md Шаг 2](./quickstart.md))
- [x] T007 Добавить поле `sortOrder: Int = 0` с аннотацией `@KaraokeDbTableField(name = "sort_order")` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` (FR-004) — см. [data-model.md §2](./data-model.md)
- [x] T008 [P] Прокинуть `sortOrder` в `Author.toDTO()` (добавить в data class + проброс в `AuthorDTO`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` (FR-004)
- [x] T009 Добавить `sort_order` в SELECT и обновить ORDER BY в `Author.loadAuthorTilesWithCounts` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`) на `ORDER BY sort_order ASC, author ASC` (FR-003)
- [x] T010 Добавить поле `sortOrder: Int = 0` в `AuthorTilePublicDto` и параметр `sortOrder: Int = 0` в `fromAuthorName` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt` (FR-004, FR-006)
- [x] T011 Обновить `PublicApiController.authorsTiles` — прокинуть `row.sortOrder` в `AuthorTilePublicDto.fromAuthorName(...)` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (FR-006)
- [x] T012 Скомпилировать бэкенд: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` (см. [AGENTS.md](../../AGENTS.md) §"Обязательная проверка после ЛЮБОГО изменения")
- [x] T013 Запустить ktlint: `./gradlew :karaoke-web:ktlintCheck` и исправить нарушения (Constitution VI / FR-007)

**Checkpoint**: БД-миграция применена на обеих сторонах, ORM-поле + DTO-поле + API-маппинг на месте, бэкенд компилируется, линтер чист. User stories могут начинаться.

---

## Phase 3: User Story 1 — Плашка «Отдельные песни разных авторов» отображается первой в сетке Закромов (Priority: P1) 🎯 MVP

**Goal**: Посетитель `/zakroma` видит виртуальную плашку «Отдельные песни разных авторов» (иконка 📁) первым элементом сетки тайлов, а не последним.

**Independent Test**: Открыть `/zakroma` в браузере, убедиться, что первый тайл в первой строке сетки — «Отдельные песни разных авторов» (US1 acceptance 1). Сравнить со скриншотом из quickstart.md Шаг 5.

### Implementation for User Story 1

- [x] T014 [US1] Перенести рендеринг плашки «Отдельные песни разных авторов» из слота `<template #trailing>` в начало сетки в `karaoke-public/src/views/ZakromaView.vue` (FR-005) — точные координаты блока см. в текущем файле (~строки 87–105)
- [x] T015 [US1] Удалить `<template #trailing>` (или оставить пустым, если FR-005b) в `karaoke-public/src/views/ZakromaView.vue` (FR-005a)
- [x] T016 [P] [US1] (Опционально) Очистить слот `<slot name="trailing" />` в `karaoke-public/src/components/AuthorTiles.vue`, если больше нигде не используется (FR-005b). Проверить `grep -r "AuthorTiles" karaoke-public/src/` на другие использования перед удалением.
- [x] T017 [US1] Проверить, что CSS-класс `.km-special-tile*` остался как есть (Clarification Q6 — никаких новых визуальных эффектов)
- [x] T018 [US1] Собрать `karaoke-public`: `cd karaoke-public && npm run build` (см. AGENTS.md §"Обязательная проверка после ЛЮБОГО изменения")
- [x] T019 [US1] Запустить линтер karaoke-public: `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` (Constitution VI / FR-007)

**Checkpoint**: User Story 1 полностью функциональна. Плашка первой в сетке. Линтер чист.

---

## Phase 4: User Story 2 — Авторы с заданным `sort_order` отображаются перед неотсортированными (Priority: P1)

**Goal**: В `/zakroma` авторы с `sort_order != 0` идут перед авторами с `sort_order = 0`. Внутри обеих групп — `ORDER BY sort_order ASC, author ASC`.

**Independent Test**: Заполнить БД фикстурой из quickstart.md Шаг 6, выполнить SQL и сравнить порядок с API-выдачей.

### Implementation for User Story 2

**Примечание**: Большая часть работы уже сделана в Phase 2 (T009 — обновление SQL ORDER BY). Эта фаза — верификация + edge-cases.

- [x] T020 [US2] Проверить SQL-запрос `Author.loadAuthorTilesWithCounts` с фикстурой из quickstart.md Шаг 6 (≥3 автора с разными `sort_order`). Убедиться, что порядок соответствует ожиданию: -1, 5, 100, затем нулевые в алфавитном порядке. (SC-002)
- [x] T021 [US2] Выполнить `curl /api/public/authors-tiles?scope=main` и убедиться, что порядок JSON совпадает с SQL и поле `sortOrder` присутствует в каждом элементе. (SC-002, SC-006, contracts/authors-tiles-api.md)
- [x] T022 [US2] Проверить edge cases из спеки (открыть `/zakroma` и убедиться):
  - (a) Все авторы с `sort_order = 0` — порядок алфавитный (как сейчас).
  - (b) Два автора с одинаковым ненулевым `sort_order` — тай-брейкер по алфавиту.
  - (c) Автор с `is_special_order = true` и `sort_order != 0` — не отображается в основной сетке (только в спец-плашке).
- [x] T023 [US2] Собрать бэкенд (если менялся код): `./gradlew :karaoke-web:bootJar --parallel` (см. AGENTS.md §"Обязательная проверка")
- [x] T024 [US2] Деплой на PROD через `deploy/deploy_web.sh` (если работаем с продом). Проверить `Status: Downloaded newer image` (не `Image is up to date`).

**Checkpoint**: User Story 2 функциональна. SQL и API отдают правильный порядок. US1 + US2 работают вместе (плашка первой, авторы в правильном порядке после неё).

---

## Phase 5: User Story 3 — Редактор может менять `sort_order` у автора из админки (Priority: P2)

**Goal**: В админ-таблице `webvue3/.../Authors/AuthorsTable.vue` появляется редактируемая колонка `sort_order`. Изменения сохраняются в БД и видны в `/zakroma` после ≤60с TTL.

**Independent Test**: В админке отредактировать `sort_order` автора, сохранить, дождаться ≤60с, проверить `/zakroma` — автор поднялся/опустился согласно новому значению. См. quickstart.md Шаг 7.

### Implementation for User Story 3

- [x] T025 [US3] Добавить колонку `sortOrder` в массив `fields` (или эквивалентную конфигурацию — см. `fldName: 'isSpecialOrder'` как референс на ~строке 528) в `webvue3/src/components/Authors/AuthorsTable.vue` (FR-008)
- [x] T026 [US3] Добавить `<template #cell(sortOrder)="data">` блок для отображения значения в ячейке в `webvue3/src/components/Authors/AuthorsTable.vue` (FR-008, по аналогии с `<template #cell(isSpecialOrder)="data">`)
- [x] T027 [US3] Реализовать input type=number для редактирования `sortOrder` с валидацией (целое число, отрицательное разрешено) в `webvue3/src/components/Authors/AuthorsTable.vue` (US3 acceptance 3)
- [x] T028 [US3] Собрать `webvue3`: `cd webvue3 && npm run build && npm run format:check` (см. AGENTS.md §"Обязательная проверка")
- [x] T029 [US3] Запустить линтер webvue3: `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` (Constitution VI)
- [x] T030 [US3] End-to-end проверка: отредактировать `sort_order` в админке, сохранить, подождать ≤60с, проверить `/zakroma` (US3 acceptance 2, SC-003)

**Checkpoint**: User Story 3 функциональна. Админка позволяет редактировать `sort_order`, изменения видны в публичной части после TTL.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, per-feature-документ, PR-чеклист, коммит. Затрагивает все US.

- [x] T031 [P] Создать per-feature документ `docs/features/zakroma-tiles-sort-order.md` (FR-012, SC-007) — содержание:
  - Описание контракта `tbl_authors.sort_order` (тип, default, диапазон).
  - Правила сортировки в SQL (`ORDER BY sort_order ASC, author ASC`).
  - Позиция плашки «Отдельные песни разных авторов» в сетке (первая).
  - Контракт API `/authors-tiles` (поле `sortOrder`).
  - Ссылка на `specs/307-special-authors-zakroma-order/spec.md`.
  - Clarifications Q1–Q6 в виде кратких заметок.
- [x] T032 [P] Добавить пункт в `.github/PULL_REQUEST_TEMPLATE.md` (FR-013): «Миграция `46_author_sort_order.sql` применена на LOCAL и на PROD (обе БД имеют колонку `tbl_authors.sort_order`)»
- [x] T033 [P] Прогнать все проверки из AGENTS.md §"Обязательная проверка после ЛЮБОГО изменения":
  1. `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
  2. `./gradlew :karaoke-web:ktlintCheck`
  3. `cd webvue3 && npm run lint && cd ..`
  4. `cd karaoke-public && npm run lint && cd ..`
  5. `./gradlew :karaoke-web:bootJar --parallel`
  6. `cd webvue3 && npm run build && npm run format:check && cd ..`
  7. `cd karaoke-public && npm run build && npm run format:check && cd ..`
  8. `cd deploy && bash do.sh build_webvue3` (если менялся webvue3)
- [x] T034 [P] Обновить `docs/architecture-notes.md` — запись о PR (Pass) с кратким описанием: «Pass 310+: спец-плашка в начало Закромов + sort_order в tbl_authors (задача №56)»
- [x] T035 Скоммитить все изменения в feature-ветку `307-special-authors-zakroma-order` согласно git workflow из AGENTS.md:
  ```bash
  cd /home/nsa/Karaoke
  git add deploy/karaoke-db/46_author_sort_order.sql \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AuthorDTO.kt \
          karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt \
          karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt \
          karaoke-public/src/views/ZakromaView.vue \
          webvue3/src/components/Authors/AuthorsTable.vue \
          docs/features/zakroma-tiles-sort-order.md \
          .github/PULL_REQUEST_TEMPLATE.md \
          docs/architecture-notes.md
  git status  # ОБЯЗАТЕЛЬНО проверить перед коммитом
  git commit -m "zakroma: спец-плашка в начало + sort_order в tbl_authors (#56)"
  git push -u origin 307-special-authors-zakroma-order
  gh pr create --base master --title "zakroma: спец-плашка в начало + sort_order в tbl_authors" --body "..."
  ```
- [x] T036 Дождаться CI 7/7 PASS, `gh pr checks` (см. CLAUDE.md §"CI 7/7 PASS — обязательно перед merge")
- [x] T037 Обновить задачу в OpenProject: `mark-review 56` после успешного CI
- [x] T038 Опционально: после `mark-review` дождаться ревью пользователя и `close-issue 56`

**Checkpoint**: задача готова к merge и закрытию.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion (T001–T003). **BLOCKS all user stories**.
- **User Stories (Phase 3–5)**: All depend on Foundational phase completion (T004–T013).
  - US1 (P1) — независимая от US2 и US3 (только фронт публичный).
  - US2 (P1) — частично пересекается с Foundational (T009). Дополнительная работа в Phase 4 — это верификация, не новый код.
  - US3 (P2) — независимая от US1 и US2 (только админка).
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2). **No dependencies on other stories.**
- **User Story 2 (P1)**: Can start after Foundational (Phase 2). Verification-only в Phase 4. Может идти параллельно с US1.
- **User Story 3 (P2)**: Can start after Foundational (Phase 2). **No dependencies on US1/US2.** Может идти параллельно.

### Within Each User Story

- Phase 2 (Foundational) MUST complete before any US.
- US1: T014 → T015 → T016 (optional) → T017 → T018 → T019.
- US2: T020 → T021 → T022 → T023 → T024.
- US3: T025 → T026 → T027 → T028 → T029 → T030.
- Tests не пишем (см. шапку файла).

### Parallel Opportunities

- T002, T003 — параллельно (Setup).
- T005, T006 — параллельно (LOCAL и PROD миграции — это **разные подключения к БД**, разные машины).
- T007, T008 — параллельно (одно и то же место в Author.kt, но **можно делать одним коммитом** — лучше последовательно).
- T016 — параллельно с T014/T015 (другой файл).
- T031, T032, T033 — параллельно (разные файлы: docs, .github, конфиги).
- T031, T034 — параллельно (документация).
- US1 (Phase 3) и US3 (Phase 5) — параллельно (разные фронты).
- US2 (Phase 4) — это верификация; параллельно с US1/US3.

---

## Parallel Example: User Story 1

```bash
# Phase 2 — миграции (разные подключения):
Task T005: "Применить миграцию на LOCAL Postgres"
Task T006: "Применить миграцию на PROD Postgres"

# Phase 3 — фронт:
Task T014: "Перенести рендеринг плашки в начало в ZakromaView.vue"
Task T016: "Очистить слот #trailing в AuthorTiles.vue (опционально)"

# Параллельно с US1 (Phase 5):
Task T025: "Добавить колонку sortOrder в AuthorsTable.vue"
Task T026: "Добавить cell(sortOrder) блок в AuthorsTable.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

Минимально жизнеспособная поставка — US1 + US2 (обе P1) + Foundational:

1. **Phase 1: Setup** (T001–T003) — 3 задачи.
2. **Phase 2: Foundational** (T004–T013) — 10 задач. **Критическая фаза**.
3. **Phase 3: User Story 1** (T014–T019) — 6 задач.
4. **Phase 4: User Story 2** (T020–T024) — 5 задач (verification).
5. **STOP and VALIDATE**: посетитель `/zakroma` видит плашку первой, авторы в правильном порядке. Тест по quickstart.md Шаг 5, 6.
6. **Deploy/Demo**: готов к merge и деплою.

US3 может быть отложен без потери ценности MVP (админка — это удобство, не основная фича).

### Incremental Delivery

1. **Setup + Foundational** → Foundation ready (миграция применена, ORM/DTO/API обновлены, бэкенд компилируется).
2. **Добавить US1** → Визуальный тест: плашка первая → Deploy/Demo (MVP!).
3. **Добавить US2** → SQL-тест + API-тест → Deploy/Demo.
4. **Добавить US3** → Админка + end-to-end → Deploy/Demo.
5. Каждая история добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С одним разработчиком — последовательно (Phase 1 → Phase 2 → US1 → US2 → US3 → Phase 6).

С двумя — после Phase 2:
- Developer A: US1 (Phase 3) — фронт публичный.
- Developer B: US3 (Phase 5) — фронт админка.
- US2 (Phase 4) делает тот, кто свободнее (verification не требует эксклюзивного владения файлами).

С тремя — добавляется параллельная работа над Polish-документацией.

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей.
- **[Story] label** мапит задачу на user story для трассировки (US1, US2, US3).
- Каждая user story должна быть **независимо завершаемой и тестируемой** вручную по quickstart.md.
- Тестов НЕ пишем (Constitution: «тесты — пользователем вручную в production-like окружении»).
- Коммит после каждой задачи или логической группы (Foundational — одним коммитом, US — каждый отдельно).
- Stop at any checkpoint для валидации story independently.
- **Избегать**: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей.
- **Per AGENTS.md §"Обязательная проверка после ЛЮБОГО изменения"** — после ЛЮБОГО изменения кода прогонять все 8 шагов из AGENTS.md (compile + lint + build + Docker-образы при необходимости). Это **не опционально** даже для «очевидных» правок.
- **Per Constitution Principle VIII**: проверить `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` перед коммитом — должно быть пусто. Эта фича не вводит секрет-файлов, но проверка обязательна.

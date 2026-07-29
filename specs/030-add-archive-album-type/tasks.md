---
description: "Task list for 030-add-archive-album-type"
---

# Tasks: Добавить тип альбома «Архивные записи»

**Input**: Design documents from `/specs/030-add-archive-album-type/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md

**Tests**: OPTIONAL — в спеке не запрошены, не генерируются.
Валидация — ручная по `quickstart.md` (6 сценариев).

**Organization**: Tasks grouped by user story (3 stories из spec.md: P1/US1,
P2/US2, P3/US3). Каждая story — независимый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, без зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- В описании — точные пути файлов

## Path Conventions

Multi-module web-service (см. plan.md «Project Structure»):
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Admin SPA: `webvue3/src/components/`
- Артефакты спеки: `specs/030-add-archive-album-type/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка ветки и контекста перед правкой кода.

- [x] T001 Создать и переключиться на ветку `080-add-archive-album-type` через `./tools/reserve-branch-number.sh add-archive-album-type` (свободный номер на момент резервации; 030 уже занят на origin). Сделано: 2026-07-29
- [x] T002 [P] Прочитать `specs/030-add-archive-album-type/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/api.md`, `quickstart.md` для контекста. Сделано: 2026-07-29 (прочитано на этапе /speckit.plan и /speckit.tasks)

**Checkpoint**: ветка готова, контекст прочитан, можно приступать к коду.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend-изменение, без которого НИ ОДНА user story не сможет
работать. Enum-константа `ARCHIVE` в `AlbumType` + её позиция в
`ZAKROMA_GROUP_ORDER` — единственный источник истины для всех
потребителей (админка, Закрома, sync).

**⚠️ CRITICAL**: Все user stories (US1, US2, US3) зависят от этой фазы.

- [x] T003 Добавить enum-константу `ARCHIVE(dbValue = "archive", description = "Исторические/архивные записи", groupLabel = "Архивные записи", filterLabel = "Архивные")` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt:32` (после `SINGLE`, перед `;`). KDoc на новую константу — по образцу остальных (без backticks вокруг идентификаторов, см. AGENTS.md «KDoc с backticks ломает парсер»). Сделано: 2026-07-29 (добавлена строка 33, обновлён комментарий шапки файла на строке 5)
- [x] T004 Добавить `ARCHIVE` последним элементом в `ZAKROMA_GROUP_ORDER` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt:42`. Итоговый порядок: `STUDIO, SINGLE, LIVE, COMPILATION, BOOTLEG, ARCHIVE`. Сделано: 2026-07-29 (строка 43, обновлён KDoc)

**Checkpoint**: Backend готов. `AlbumType.entries` содержит 6 констант,
`ZAKROMA_GROUP_ORDER` — 6 элементов. `AlbumType.fromDb("archive")`
возвращает `ARCHIVE` (проверить локально через REPL или unit-test —
опционально).

---

## Phase 3: User Story 1 — Админ может назначить альбому тип «Архивные записи» (Priority: P1) 🎯 MVP

**Goal**: В таблице «Альбомы» (webvue3) inline-`<select>` для типа альбома
содержит опцию «Архивный»; в модалке «Альбомы автора» тип альбома
отображается лейблом «Архивный» (без возможности редактирования — read-only).

**Independent Test**: см. `quickstart.md` сценарий 1 + 2. Двойной клик по
любому альбому → выбор «Архивный» → сохранение → `SELECT ... WHERE
album_type = 'archive'` возвращает 1+ строк.

### Implementation for User Story 1

- [x] T005 [P] [US1] Добавить `'archive'` в массив `ALBUM_TYPE_OPTIONS` и `archive: 'Архивный'` в `ALBUM_TYPE_LABELS` в `webvue3/src/components/Albums/AlbumsTable.vue:172-179`. Комментарий «Значения соответствуют AlbumType.dbValue» сохранить. Сделано: 2026-07-29
- [x] T006 [P] [US1] Добавить `archive: 'Архивный'` в `ALBUM_TYPE_LABELS` в `webvue3/src/components/Authors/AuthorAlbumsModal.vue:59-65`. Комментарий «Значения соответствуют AlbumType.dbValue» сохранить. Сделано: 2026-07-29

**Checkpoint**: US1 готов. Админ может назначить тип «Архивный» в
`AlbumsTable`, лейбл отображается в `AuthorAlbumsModal`. Сохранение в
БД работает через существующий `apisUpdateAlbum` (без правок контроллера).

---

## Phase 4: User Story 2 — На Закромах (karaoke-public) раздел «Архивные записи» (Priority: P2)

**Goal**: У исполнителей с альбомами `album_type = 'archive'` на странице
Закромов внизу (после «Бутлеги») появляется раздел «Архивные записи» с
соответствующими альбомами.

**Independent Test**: см. `quickstart.md` сценарий 4. Выбрать автора
с архивным альбомом → раздел «Архивные записи» виден → альбом помечен
лейблом «Исторические/архивные записи».

### Implementation for User Story 2

- [x] T007 [US2] Проверить (без правок кода), что `ZakromaPublicDto.albumTypeCounts` (см. `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt:111-129`) автоматически содержит элемент `archive` после T003+T004. Никаких правок DTO/контроллеров НЕ требуется — список формируется из `ZAKROMA_GROUP_ORDER`. Сделано: 2026-07-29 (read-only verify — `ZakromaPublicDto.kt:111-129` использует `AlbumType.ZAKROMA_GROUP_ORDER.mapNotNull { ... }` и после T004 ARCHIVE автоматически попадёт в JSON; live-проверка на Закромах — за пользователем после сборки/деплоя)

**Checkpoint**: US2 готов. Публичный фронт автоматически показывает
новый раздел (благодаря `ZAKROMA_GROUP_ORDER` в Phase 2). Если раздел
НЕ появился — проверить, что `karaoke-public` собран с актуальным
`karaoke-web` (через `deploy/do.sh build_start_web`).

---

## Phase 5: User Story 3 — Фильтр в админке по типу «Архивные» (Priority: P3)

**Goal**: В модалке фильтра таблицы «Альбомы» (webvue3) появляется опция
«Архивный»; при выборе таблица показывает только альбомы с
`album_type = 'archive'`.

**Independent Test**: см. `quickstart.md` сценарий 3. webvue3 →
«Альбомы» → «Фильтр» → выбрать «Архивный» → таблица показывает только
архивные альбомы; счётчик у кнопки фильтра совпадает с
`SELECT COUNT(*) FROM tbl_albums WHERE album_type = 'archive'`.

### Implementation for User Story 3

- [x] T008 [P] [US3] Добавить `{ value: 'archive', label: 'Архивный' }` в `ALBUM_TYPE_LABEL_OPTIONS` в `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue:128-134`. Комментарий «Значения соответствуют AlbumType.dbValue — держать в синхроне при добавлении новых типов» сохранить. Сделано: 2026-07-29

**Checkpoint**: US3 готов. Фильтр по типу «Архивный» работает. Счётчик
в кнопке фильтра корректен (берётся из `getAlbumsTypeCounts` на
бэкенде, без правок).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Сборка, линт, end-to-end валидация, документирование.

- [ ] T009 [P] Собрать backend: `cd /home/nsa/Karaoke && ./gradlew karaoke-app:bootJar karaoke-web:bootJar --parallel` (lock `build/` через `deploy/build-lock.sh`, см. constitution §Рабочий процесс)
- [ ] T010 [P] Собрать webvue3: `cd /home/nsa/Karaoke/webvue3 && npm run build`
- [ ] T011 [P] Собрать karaoke-public: `cd /home/nsa/Karaoke/deploy && bash do.sh build_start_public` (через Docker — см. AGENTS.md)
- [ ] T012 Запустить `./gradlew ktlintCheck` + `cd webvue3 && npm run lint:check` + `cd karaoke-public && npm run lint:check` — 0 новых violations (допускается baseline). Проверить `tools/baseline-stats.sh` — не должно расти
- [ ] T013 Ручное end-to-end тестирование по `specs/030-add-archive-album-type/quickstart.md` — все 6 сценариев + чек-лист регрессий (8 пунктов). Результат зафиксировать в PR-описании
- [ ] T014 [P] Если изменения затрагивают per-feature документ (FR-009 constitution) — обновить соответствующий файл в `docs/features/`. Для этой фичи: НЕ требуется (AlbumType — атрибут сущности Album, не самостоятельная подсистема; cross-reference в `docs/features/dual-db-sync.md` уже достаточен)
- [ ] T015 [P] Обновить запись в `docs/architecture-notes.md` (Pass N) — краткое описание PR: добавление enum-константы `ARCHIVE`, синхронные правки 3 хардкод-списков в webvue3, 0 миграций БД
- [ ] T016 Создать коммит на ветке `080-add-archive-album-type` по образцу `area: краткое описание` (русский, по стилю проекта; см. constitution §Рабочий процесс). НЕ пушить без явного согласия пользователя. Состав коммита: 1) `album-type: add ARCHIVE enum constant` (AlbumType.kt), 2) `webvue3: add archive type to album tables/filter` (3 .vue-файла). Опционально — отдельным коммитом добавить спеку `specs/030-add-archive-album-type/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 → T002. Можно начать сразу.
- **Foundational (Phase 2)**: T003 → T004. Зависит от Phase 1.
  **Блокирует US1, US2, US3** — без ARCHIVE в enum фронт и Закрома
  не работают.
- **User Stories (Phase 3-5)**: зависят от Phase 2. Можно идти
  последовательно P1 → P2 → P3, или параллельно (если есть ресурсы).
- **Polish (Phase 6)**: зависит от всех желаемых US.

### User Story Dependencies

- **US1 (P1)**: зависит от Phase 2. **Независима** от US2/US3.
- **US2 (P2)**: зависит от Phase 2. **Независима** от US1/US3
  (verify-only, без правок кода).
- **US3 (P3)**: зависит от Phase 2. **Независима** от US1/US2.

### Within Each User Story

- T003 (enum) → T004 (ZAKROMA_GROUP_ORDER) — последовательно,
  один файл.
- T005, T006, T008 — **разные файлы**, можно параллельно.
- T007 — verify-only, без правок.

### Parallel Opportunities

```bash
# После Phase 2 — можно запустить параллельно:
Task: "T005 AlbumsTable.vue"          # [P] [US1]
Task: "T006 AuthorAlbumsModal.vue"    # [P] [US1]
Task: "T008 AlbumsFilterModal.vue"    # [P] [US3]
# T007 [US2] — verify-only, можно делать параллельно или после T005-T008
```

```bash
# В Phase 6 (Polish) — параллельно:
Task: "T009 gradle bootJar"
Task: "T010 webvue3 npm run build"
Task: "T011 karaoke-public build_start_public"
Task: "T015 docs/architecture-notes.md"  # после T013
```

---

## Implementation Strategy

### MVP First (User Story 1 + Foundational)

Минимально жизнеспособный релиз — это Phase 2 (T003+T004) + Phase 3
(T005+T006). После этого:

1. **Backend** отдаёт `ARCHIVE` через `AlbumType.entries` и
   `ZAKROMA_GROUP_ORDER`.
2. **Админка** позволяет назначить тип «Архивный» (US1).
3. **Закрома** автоматически показывают раздел (US2) — без правок
   фронта (только verify).
4. **Фильтр** по типу «Архивный» (US3) — опционально, можно отложить.

### Incremental Delivery

1. Phase 1 + Phase 2 → Backend готов.
2. + Phase 3 (US1) → MVP: админ может назначить тип. Деплой/демо.
3. + Phase 4 (US2) → Закрома показывают новый раздел. Деплой/демо.
4. + Phase 5 (US3) → Фильтр работает. Деплой/демо.
5. + Phase 6 (Polish) → Линт, документация, регрессии.

Каждая фаза добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С одним разработчиком — последовательно по приоритету.
С 2+ разработчиками:

- Dev A: Phase 2 (T003, T004) — критично, последовательно.
- Dev B (после T004): Phase 3 (T005, T006) [US1] + Phase 5 (T008) [US3] — параллельно, разные файлы.
- Dev C (после T004): Phase 4 (T007) [US2] — verify-only, можно в любой момент.
- Все вместе: Phase 6 (Polish) — после всех US.

---

## Notes

- [P] задачи = разные файлы, без зависимостей.
- [Story] лейбл — для traceability (US1, US2, US3 из spec.md).
- Каждая user story независимо завершаема и тестируема.
- Тесты в фиче НЕ пишутся (в спеке не запрошены, в CI их нет).
  Валидация — ручная по quickstart.md.
- Коммитить после каждой задачи или логической группы (Phase 2 — один
  коммит «album-type: add ARCHIVE enum constant»; Phase 3 — один
  коммит «webvue3: add archive type to album filters and tables»).
- **Не пушить** без явного согласия пользователя (см. constitution
  §Рабочий процесс и AGENTS.md «Git»).
- **Не коммитить** `deploy/ollama_data/`, `dist/`, `node_modules/`,
  `deploy/.env`, `deploy/do.env`.
- **На машине с hostname `dev-pc` под пользователем `dev`**: можно
  пересобирать/перезапускать локальные контейнеры и делать локальные
  миграции БД без согласия пользователя. На любой другой машине —
  только с явного согласия.
- Никаких миграций БД, никаких `ALTER TABLE`, никаких изменений
  `recordhash`-триггеров (см. plan.md §Constitution Check).
- Никаких новых per-feature документов в `docs/features/` (AlbumType —
  атрибут сущности, не подсистема).

# Tasks: 293 — Галочка «Работа со SKIP-авторами и песнями» в настройках пользователя

**Input**: Design documents from `/specs/293-skip-author-toggle/`
**Branch**: `293-skip-author-toggle`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: НЕ включены — в проекте нет CI-тестов (`Constitution §II`,
существующие тесты `@Disabled`); валидация — ручная по `quickstart.md`.

**Organization**: Задачи сгруппированы по user story. Каждая фаза —
независимый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: маппинг на user story (US1, US2, US3)
- Включать абсолютные пути

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка БД — миграция для новой колонки `can_work_with_skipped`.
Это MUST быть первой фазой, т.к. без колонки БД весь последующий код
не скомпилируется / сломается reflection-loader `KaraokeDbTable`.

- [X] T001 Создать SQL-миграцию `deploy/karaoke-db/45_site_user_can_work_with_skipped.sql` — `ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS can_work_with_skipped boolean DEFAULT false NOT NULL` + пересоздание `update_tbl_site_users_recordhash` с новой колонкой + UPDATE для пересчёта md5 на существующих строках (см. `data-model.md` скелет SQL)
- [X] T002 Применить миграцию V45 локально через `docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/45_site_user_can_work_with_skipped.sql` и проверить схему через `SELECT column_name FROM information_schema.columns WHERE table_name = 'tbl_site_users' AND column_name = 'can_work_with_skipped'` (ожидается 1 строка)

**Checkpoint**: колонка `can_work_with_skipped` существует в `tbl_site_users`, recordhash-триггер обновлён.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Базовая модель данных, от которой зависят все user stories.
Без этих изменений код не собирается / reflection-loader не подхватывает
новое поле.

- [X] T003 [P] Добавить поле `canWorkWithSkipped` в Kotlin-модель `SiteUser` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt` с аннотацией `@KaraokeDbTableField(name = "can_work_with_skipped")` и KDoc, ссылающимся на спеку 293 (паттерн — поле `canSelfAssignTasks` строка 139–140)
- [X] T004 [P] Добавить поле `canWorkWithSkipped` в `SiteUserDto` data class в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUserDto.kt` с аннотацией `@get:JsonProperty("canWorkWithSkipped")` и KDoc (паттерн — поле `canSelfAssignTasks` строка 32–33)
- [X] T005 Прокинуть `canWorkWithSkipped = canWorkWithSkipped` в `SiteUser.toDTO()` (в `SiteUser.kt:168-190`) и `entity.canWorkWithSkipped = canWorkWithSkipped` в `SiteUserDto.fromDto()` (в `SiteUserDto.kt:59-73`) — **зависит от T003, T004**

**Checkpoint**: backend компилируется (`./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`), reflection-loader `KaraokeDbTable` подхватывает новую колонку автоматически.

---

## Phase 3: User Story 1 — Админ выдаёт редактору право работать с SKIP (Priority: P1) 🎯 MVP

**Goal**: Администратор в `webvue3` открывает карточку пользователя и
видит новую галочку «Может работать со SKIP-авторами и песнями»;
может её выставить/снять и сохранить изменения.

**Independent Test**: Открыть карточку любого пользователя → галочка
видна; переключить → нажать «Сохранить» → значение сохранено в БД
(проверяется через `SELECT can_work_with_skipped FROM tbl_site_users WHERE id = ?`).

### Implementation for User Story 1

- [X] T006 [US1] Добавить новый блок «label-and-input» с чекбоксом `v-model="siteUserCurrent.canWorkWithSkipped"` в `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue` после блока «Может сам назначать себе задания» (строка 132–149), с лейблом «Может работать со SKIP-авторами и песнями:» и подсказкой про снятие фильтра SKIP и бейдж в UI (полный текст подсказки — `contracts/admin-site-user-api.md`)
- [X] T007 [US1] Убедиться, что `webvue3/src/services/api.js` (или эквивалентный endpoint-payload для `SiteUsersController`) передаёт `canWorkWithSkipped` в PUT/POST — проверить, что JSON-payload содержит поле `canWorkWithSkipped` (стандартный паттерн для `canSelfAssignTasks` уже работает в `webvue3`)

**Checkpoint**: Админ может открыть карточку пользователя, увидеть новую галочку, переключить её и сохранить. БД обновляется.

---

## Phase 4: User Story 2 — Редактор с галочкой видит SKIP-контент (Priority: P1)

**Goal**: Залогиненный пользователь с `can_work_with_skipped = TRUE` на
`karaoke-public` видит SKIP-авторов и SKIP-песни во всех публичных
списках («Закрома», история прослушиваний), страницах песен и share-link
(с запретом создания share-link для SKIP — compliance). Видит бейдж
«SKIP» в UI как визуальный индикатор скрытого контента.

**Independent Test**: Залогиниться редактором с галочкой → `/zakroma`
показывает skip-автора; `/account/history` показывает SKIP-песни; бейджи
«SKIP» видны. Анонимный пользователь — поведение прежнее (SKIP скрыт).
Создание share-link на SKIP-песню → 409 Conflict.

### Implementation for User Story 2

- [X] T008 [P] [US2] Расширить сигнатуру `isSkipped(song: Song): Boolean` → `isSkipped(song: Song, canSeeSkipped: Boolean = false): Boolean` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt:437`; обновить все 3 вызывающих места (строки 106, 267, 333) для прокидывания `siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false` (см. `research.md §R4`)
- [X] T009 [P] [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:zakroma()` (строка 97–125) заменить хардкод `withSkiped = false` на `withSkiped = siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false` в вызове `Song.loadListAuthors(...)`; аналогично для `Zakroma.getZakroma(...)` — добавить параметр `canSeeSkipped` (см. `research.md §R10`) и пробросить
- [X] T010 [P] [US2] Расширить сигнатуру `Zakroma.getZakroma(author, ..., canSeeSkipped: Boolean = false)` и `Zakroma.getZakromaBySpecialOrder(..., canSeeSkipped: Boolean = false)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` (строки 28–46, 60–84); внутри `buildFromSongs` добавить пост-фильтрацию SKIP-тегов песен, если `!canSeeSkipped`
- [X] T011 [P] [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:zakroma()` (строка 297–319) и `getZakromaBySpecialOrder` пробросить `canSeeSkipped` аналогично MainController
- [X] T012 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:981` добавить **в самом начале** метода создания share-link проверку `if (songHasSkipTag(song.tags)) throw ShareLinkForSkippedContentException(...)` (compliance — FR-012); создать новый exception-класс в том же пакете; в вызывающем контроллере перевести exception в HTTP `409 Conflict` с телом `{"error":"share_link_forbidden","message":"Невозможно создать share-link для SKIP-контента"}`
- [X] T013 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ListeningHistoryController.kt:117-121` (фильтр `songHasSkipTag` после батч-JOIN) добавить обход фильтра для пользователей с `canWorkWithSkipped`: `when { canSeeSkipped -> true; else -> !songHasSkipTag(song.tags) }`. Использовать `usersById[siteUserId]?.canWorkWithSkipped` (поле уже подгружается в строке 154–157)
- [X] T014 [US2] Добавить inline-бейдж «SKIP» в карточки автора и песни в `karaoke-public/src/views/ZakromaView.vue` и `karaoke-public/src/views/AuthorPlaylistView.vue` — `<span v-if="canSeeSkipped && hasSkipTag" class="badge text-bg-warning ms-2" title="Удалено по требованию правообладателя">SKIP</span>` (полная разметка — `contracts/public-zakroma-api.md` и `contracts/public-song-skip-api.md`)
- [X] T015 [US2] В `karaoke-public/src/views/SongView.vue` добавить бейдж «SKIP» в карточку песни (по `song.tags.includes('SKIP') && user.canWorkWithSkipped`) и скрыть/отключить кнопку «Поделиться» для SKIP-песен (`v-if="!songHasSkippedTag(song.tags)"` — независимо от `canWorkWithSkipped`, см. FR-012)

**Checkpoint**: Редактор с галочкой видит SKIP-контент в «Закромах», истории, на страницах песен; бейджи «SKIP» видны; share-link для SKIP запрещён. Анонимный пользователь — поведение прежнее.

---

## Phase 5: User Story 3 — Админ видит галочку в таблице пользователей (Priority: P2)

**Goal**: В таблице пользователей в `webvue3` есть колонка «SKIP-доступ»
с ✓/пусто для каждого пользователя, чтобы админ мог быстро найти,
кому уже выдано право.

**Independent Test**: Открыть таблицу пользователей → колонка
«SKIP-доступ» отображается; значения соответствуют БД.

### Implementation for User Story 3

- [X] T016 [US3] Добавить новую колонку «SKIP-доступ» в `webvue3/src/components/SiteUsers/SiteUsersTable.vue`: в массив `columns` добавить объект `{ key: 'canWorkWithSkipped', label: 'SKIP-доступ', sortable: true, formatter: (value) => value ? '✓' : '' }`; в template добавить `<template #cell(canWorkWithSkipped)="data">` с условным рендером ✓/пусто (полная разметка — `contracts/admin-site-user-api.md`)

**Checkpoint**: US3 полностью функциональна. Админ видит колонку.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация (Constitution §VI FR-009), LiveDocs, финальная
валидация по `quickstart.md`.

- [X] T017 [P] Создать per-feature документ `docs/features/editor-skipped-content-access.md` (Constitution §VI FR-009 — обязательно в том же PR): описание фичи, мотивация, контракт, влияние, операционные замечания, диа-ка. Использовать `contracts/admin-site-user-api.md`, `contracts/public-zakroma-api.md`, `contracts/public-song-skip-api.md` как ссылочные материалы
- [X] T018 [P] Создать LiveDoc `livedocs/features/293-skip-author-toggle.md` — SDD-сводка фичи (1-2 страницы) со ссылками на spec.md, plan.md, data-model.md, contracts/. Зарегистрировать в `livedocs/features/README.md` и `livedocs/INDEX.md`
- [X] T019 Выполнить **обязательную проверку после изменения** (AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»): (1) `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`; (2) `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint`; (3) `./gradlew :karaoke-web:bootJar :karaoke-app:bootJar --parallel`; (4) `cd webvue3 && npm run build && npm run format:check`, затем `cd karaoke-public && npm run build && npm run format:check`; (5) `cd deploy && bash do.sh build_webvue3` (и `bash do.sh build_public` если менялся). Только после всех 5 шагов OK — сообщать «готово к деплою»
- [X] T020 Выполнить ручную валидацию по `quickstart.md`: регресс-тест анонима (curl + diff), сценарий 2 (админ выдаёт галочку), сценарий 3 (редактор видит SKIP), сценарий 5 (409 на share-link). Применить миграцию на прод ТОЛЬКО после согласия пользователя (Constitution, AGENTS.md, п. 2 «Категорически запрещено»)

**Checkpoint**: Документация создана, LiveDoc зарегистрирован, валидация пройдена, готовность к деплою подтверждена.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — стартует немедленно. Блокирует все последующие фазы (без колонки БД код не собирается).
- **Phase 2 (Foundational)**: зависит от Phase 1 — блокирует все user stories.
- **Phase 3 (US1)**: зависит от Phase 2 (нужно поле `canWorkWithSkipped` в SiteUser/SiteUserDto, чтобы webvue3 его увидел).
- **Phase 4 (US2)**: зависит от Phase 2 + Phase 3 (нужна возможность галочки, чтобы её эффект имело смысл проверять).
- **Phase 5 (US3)**: зависит от Phase 2 (нужно поле в DTO). Может стартовать параллельно с Phase 4 (разные файлы: webvue3 vs karaoke-web/karaoke-public).
- **Phase 6 (Polish)**: зависит от всех user stories.

### User Story Dependencies

- **User Story 1 (P1)**: после Phase 2 — нет зависимостей от других stories.
- **User Story 2 (P1)**: после Phase 2 + Phase 3 (US1) — функционально зависит от того, что галочка может быть выставлена (иначе нечего проверять). Можно делать **код US2 параллельно с US3** (разные файлы), но валидация US2 — после US1.
- **User Story 3 (P2)**: после Phase 2 — независима от US1/US2 по коду, можно делать параллельно с US2.

### Within Each Phase

- **Phase 2**: T003 и T004 параллельны ([P]) — разные файлы; T005 зависит от T003+T004.
- **Phase 4**: T008, T009, T010, T011 параллельны ([P]) — разные файлы в разных модулях; T012, T013, T014, T015 зависят от своих prerequisite (T014, T015 — после Phase 2, не требуют T008-T011).
- **Phase 6**: T017 и T018 параллельны ([P]).

### Parallel Opportunities

```bash
# Phase 2 — параллельно (разные файлы):
Task T003: "Add canWorkWithSkipped to SiteUser.kt"
Task T004: "Add canWorkWithSkipped to SiteUserDto.kt"

# Phase 4 — параллельно (разные файлы):
Task T008: "Extend isSkipped in PublicOgSongController.kt"
Task T009: "Update MainController.zakroma()"
Task T010: "Extend Zakroma.getZakroma signature"
Task T011: "Update PublicApiController.zakroma()"

# Phase 4 + Phase 5 — параллельно между stories:
#   Разработчик A — Phase 4 (backend фильтрация + UI бейдж)
#   Разработчик B — Phase 5 (webvue3 таблица)
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2 + Phase 3)

1. Завершить Phase 1 (Setup) — миграция V45 применена локально.
2. Завершить Phase 2 (Foundational) — модель и DTO обновлены, backend компилируется.
3. Завершить Phase 3 (US1) — галочка в webvue3 admin-форме.
4. **STOP и VALIDATE**: открыть карточку пользователя в webvue3 → галочка видна, переключается, сохраняется в БД.
5. Деплой/демо если готов (админ уже может выдавать галочку, даже если runtime-фильтрация ещё не подключена).

**Это безопасный MVP**: даже если остальные фазы не сделаны, админ
уже не сломает ничего — сохранение нового флага не влияет на текущее
поведение (дефолт FALSE → ничего не меняется для анонимов и обычных
пользователей).

### Incremental Delivery

1. Setup + Foundational → Foundation ready (backend компилируется).
2. + US1 → MVP (админ выдаёт галочку, БД обновляется).
3. + US2 → runtime-эффект (редактор видит SKIP-контент).
4. + US3 → admin overview (колонка в таблице).
5. + Polish → документация и валидация.

### Parallel Team Strategy

С несколькими разработчиками:

1. Team завершает Setup + Foundational вместе.
2. После Phase 2:
   - Разработчик A: US1 + US3 (всё в webvue3)
   - Разработчик B: US2 (backend фильтрация в karaoke-web/karaoke-public)
3. US2 валидация требует US1 (нужна галочка для проверки эффекта).

---

## Notes

- **[P] tasks**: разные файлы, нет зависимостей.
- **[Story] label**: US1, US2, US3 для traceability к спеке.
- Каждая user story независимо завершаема и тестируема.
- **Перед каждым коммитом** проверить: `git status` + `git diff --stat`,
  никаких секретов в индексе (`git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` пусто).
- **Применение миграции на прод** (`Step 6` в `quickstart.md`) — только
  с согласия пользователя (Constitution, AGENTS.md, п. 2 «Категорически
  запрещено»).
- **Перезапуск `karaoke-app`** — только с согласия пользователя (AGENTS.md,
  машинно-специфичное исключение nsa-i9/nsa).
- **Полная последовательность сбор/проверки** — см. `AGENTS.md § «Обязательная
  проверка после ЛЮБОГО изменения кода»` (5 шагов: compile → lint →
  bootJar → Vite build → Docker).
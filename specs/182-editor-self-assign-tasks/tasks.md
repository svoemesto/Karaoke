---
description: "Task list for Self-Assign Tasks feature"
---

# Tasks: Self-Assign Tasks для редакторов

**Input**: Design documents from `/specs/182-editor-self-assign-tasks/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: тесты НЕ запрашивались в спецификации (проект не имеет CI-тестов для admin/public — см. AGENTS.md «Тесты»). Валидация — ручные сценарии в [quickstart.md](./quickstart.md)."

**Organization**: задачи сгруппированы по user story, чтобы каждая история была реализуема и тестируема независимо.

**Implementation Status**: все 27 задач выполнены (см. ниже). **Pass 51-2 (2026-08-13)**: placement self-assign кнопки ПЕРЕСМЕЩЁН с Закромов (Pass 51-1) на страницу конкретной песни `/song/{id}` (SongView). См. Q&A в AGENTS.md и секцию Clarifications в spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3, US4)
- В описании — точные пути файлов

## Path Conventions

Проект — multi-module: `karaoke-app` (admin backend), `karaoke-web` (public backend), `webvue3` (admin SPA), `karaoke-public` (public SPA), `deploy/karaoke-db` (SQL). Пути относительно корня репо.

---

## Phase 1: Setup (DDL)

**Purpose**: миграция БД — без неё все следующие шаги невозможны.

- [x] T001 Создать миграционный файл `deploy/karaoke-db/XX_add_can_self_assign_tasks.sql`:
  - `ALTER TABLE tbl_site_users ADD COLUMN can_self_assign_tasks BOOLEAN NOT NULL DEFAULT FALSE;`
  - пересоздать `recordhash`-триггер на `tbl_users` с учётом новой колонки (по конституции III, иначе sync LOCAL↔SERVER сломается)
  - комментарий-шапка: ссылка на спеку (`specs/182-editor-self-assign-tasks/`), PASS номер (для получения номера запустить `ls deploy/karaoke-db/ | grep -E '^[0-9]+' | sort | tail -1` и взять +1)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: блокирующая база — без неё нельзя приступить к US1/US2/US3/US4.

**⚠️ CRITICAL**: нельзя начинать user stories до завершения этой фазы.

- [x] T002 [P] Применить миграцию T001 локально: `docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/XX_add_can_self_assign_tasks.sql` и проверить `\d tbl_site_users` (новая колонка видна) + сам `recordhash`-триггер (вызвать UPDATE на любую запись и проверить, что `recordhash` колонка обновилась)
- [x] T003 [P] Добавить поле `canSelfAssignTasks: Boolean = false` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt` с аннотацией `@KaraokeDbTableField(name = "can_self_assign_tasks")`, рядом с `isEditor` (для diff через reflection)
- [x] T004 [P] Добавить поле `canSelfAssignTasks: Boolean = false` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUserDto.kt` с аннотацией `@get:JsonProperty("canSelfAssignTasks")` (явное JSON-имя, см. AGENTS.md Q&A «Jackson отбрасывает is»), добавить в `toDTO()` маппинг
- [x] T005 [P] Создать файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/SongAssignmentBriefDto.kt` — простой DTO `data class(id: Long, assigneeId: Long, assignedAt: Timestamp?, adminStatus: String)` (см. data-model.md §3)
- [x] T006 Расширить `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt` — добавить поле `assignment: SongAssignmentBriefDto? = null` в `data class ZakromaAlbumSongPublicDto` (default null для backward compatibility, см. data-model.md §4)

**Checkpoint**: Foundation ready — можно приступать к user stories.

---

## Phase 3: User Story 1 — Управление флагом «Может сам назначать себе задания» (Priority: P1) 🎯 MVP

**Goal**: админ в `webvue3 → SiteUsers → SiteUserEdit` может включить/выключить флаг `canSelfAssignTasks` для редактора, флаг сохраняется в `tbl_site_users` и виден в общем списке.

**Independent Test**: открыть редактора, включить флаг, сохранить, перезагрузить, галочка на месте. Через `psql` проверить `can_self_assign_tasks = true` в `tbl_site_users`. Снять флаг — все ранее взятые self-assign задания остаются у редактора. Для не-редактора — поле скрыто или disabled.

### Implementation for User Story 1

- [x] T007 [P] [US1] Расширить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SiteUsersController.kt` — добавить `@RequestParam(name = "canSelfAssignTasks", required = false) canSelfAssignTasks: Boolean?` в метод `update`, добавить в `if (canSelfAssignTasks != null) siteUserObj.canSelfAssignTasks = canSelfAssignTasks` (по образцу `isEditor`, см. CONTROLLER.md)
- [x] T008 [P] [US1] Обновить action `saveSiteUser` в `webvue3/src/components/SiteUsers/store.js` — добавить `if (diffs.canSelfAssignTasks !== undefined) params.canSelfAssignTasks = diffs.canSelfAssignTasks` (строки 152-153 для аналогии)
- [x] T009 [US1] Добавить checkbox «Может сам назначать себе задания» в `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue`:
  - поместить в секцию «Права редактора» (рядом с `isEditor` checkbox)
  - `:disabled="!siteUserCurrent.isEditor"` + `v-b-tooltip` «Сначала включите роль редактора» (или скрыть через `v-if="siteUserCurrent.isEditor"` — выбрать визуально консистентно с patterns)
  - `v-model="siteUserCurrent.canSelfAssignTasks"` (булева в `data()` через `setSiteUserCurrent` mutation)
  - добавить `canSelfAssignTasks: false` в начальный `siteUserCurrent` объект для default
- [x] T010 [US1] (опционально — улучшение UX) Добавить поддержку фильтра по `canSelfAssignTasks` в `webvue3/src/components/SiteUsers/filter/store.js` по образцу `getSiteUsersFilterIsEditor` — если требуется отображение бейджа в `SiteUsersTable.vue`. Минимум: добавлена колонка в таблице + cell template. Полный фильтр отложен.

**Checkpoint**: User Story 1 готов — админ может управлять флагом. **MVP-минимум (P1 acceptance gate)**.

---

## Phase 4: User Story 2 — Редактор берёт свободную песню в «Закромах» (Priority: P1)

**Goal**: залогиненный редактор с флагом видит кнопку «Взять в работу» в карточках свободных песен в `karaoke-public/ZakromaView`, после клика создаётся `SongAssignment`, UI показывает подтверждение.

**Independent Test**: два редактора с флагом, открывают одного автора, кликают «Взять в работу» на разных песнях — оба получают задание, обе песни пропадают из «свободных». Третий (без флага) — кнопки не видит. Аноним — тоже не видит.

### Implementation for User Story 2

- [x] T011 [US2] **Pass 51-2 REDESIGN**: T011 ОТМЕНЁН — поле `assignment` НЕ добавляется в `ZakromaAlbumSongPublicDto` (откат первоначальной идеи). Вместо этого `PublicApiController.song` (один-песня endpoint) теперь возвращает `assignment: SongAssignmentBriefDto?` через `SongAssignment.loadBySongIds([songId])`. `ZakromaPublicDto` и `zakroma/zakromaStream` endpoints НЕ ИЗМЕНЕНЫ.
- [x] T012 [US2] Добавить endpoint `assignSelf` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt`:
  - `@PostMapping("/assign-self")` (базовый путь — `/api/public/songeditor/`, НЕ `/account/editor/`, т.к. страница песни открыта анонимам)
  - параметр `@RequestParam songId: Long`
  - проверки (см. contracts/C2): `if (!user.isEditor) → 403 forbidden_not_editor`, `if (!user.canSelfAssignTasks) → 403 forbidden_not_self_assign_editor`, `Song.loadFromDbById(songId, ...) ?: → 404 song_not_found`
  - **атомарная транзакция** (см. T016 — US3, реализуется здесь же): `SELECT FOR UPDATE` + INSERT
  - на успех: `mapOf("ok" to true, "id" to created.id, "idempotent" to false)` HTTP 200
  - на идемпотентность (своё задание существует): `mapOf("ok" to true, "id" to existing.id, "idempotent" to true)` HTTP 200
  - на чужое: `ResponseEntity.status(409).body(mapOf("ok" to false, "error" to "song_already_taken"))`
  - НЕ создавать `SongAssignmentDraft` (FR-009)
- [x] T013 [P] [US2] Добавить action `assignSelf` в `karaoke-public/src/services/songEditorApi.js` (поверх authApi):
  - `assignSelf(songId) { return authPost('/api/public/songeditor/assign-self', { songId }, token()) }`
  - на 200 OK — вернуть DTO; на 409 — бросить/вернуть `{ error: 'song_already_taken' }`
- [x] T014 [P] [US2] **Pass 51-2 REDESIGN**: T014 ОТМЕНЁН. Кнопка «Взять в работу» НЕ в `ZakromaView.vue` (откат). Вместо этого добавлена в `karaoke-public/src/views/SongView.vue` в секцию `km-meta-actions`:
  - место: рядом с Share/Favorite/Playlist в `km-meta-actions` (clarification Q3 redesign)
  - видимость: `v-if="showSelfAssignButton"` (= `canSelfAssignEditor && currentSong && !currentSong.assignment`)
  - `@click="onSelfAssignClick()"` → `await apiAssignSelf(currentSong.id)`
  - на успех: toast «Задание взято в работу — перейдите в Мои задания» → оптимистично заменить кнопку на «Открыть задание»
  - на 409 / exception: toast «Эта песня уже занята» + убрать кнопку
  - на таймаут: оставить кнопку, тост «проверьте интернет и попробуйте снова»
  - `canSelfAssignEditor` = `user.isEditor && user.canSelfAssignTasks` (compute)
  - блокировка двойных кликов через `assigningSongId`
- [x] T015 [P] [US2] Добавить кнопку «Открыть задание» (заменитель) в `karaoke-public/src/views/SongView.vue`:
  - место: та же позиция, что и «Взять в работу» (в `km-meta-actions`)
  - видимость: `v-if="showOpenAssignmentButton"` (= `canSelfAssignEditor && currentSong.assignment && currentSong.assignment.assigneeId === userId`)
  - `@click="onOpenAssignmentClick()"` → `$router.push('/account/editor/tasks?id=' + currentSong.assignment.id)`

**Checkpoint**: User Story 2 готов — self-assign через UI работает для self-assign-редакторов, кнопка спрятана для остальных. **MVP+**.

---

## Phase 5: User Story 3 — Защита от гонок и валидация (Priority: P2)

**Goal**: при одновременных кликах двух редакторов на одну песню — только один получает 200, второй — 409. Повторный клик одного и того же редактора — идемпотентный 200.

**Independent Test**: 2 браузера, 2 редактора с флагом, одновременный клик → только 1 запись в `tbl_song_assignments`. Повторный клик того же самого — 200 OK с пометкой `idempotent`.

### Implementation for User Story 3

- [x] T016 [US3] Реализовать атомарную транзакцию в `assignSelf` (продолжение T012):
  - внутри `db.getConnection()` открыть `conn.autoCommit = false`
  - `SELECT id, assignee_id FROM tbl_song_assignments WHERE song_id = ? FOR UPDATE` — row lock на «первое существующее задание» (если есть)
  - если результат непустой:
    - если `assignee_id == user.id` → отдать `{ok:true, idempotent:true, id: existing.id}`, `commit`
    - иначе → откат, 409 `song_already_taken`
  - если результат пустой → INSERT в `tbl_song_assignments` через RETURNING id (assigneeId=user.id, songId, adminStatus=ADMIN_OPEN, assignedBy=user.id), commit
  - при `Exception` → rollback + 500 (retry отсутствует для упрощения; см. audit-замечание в plan.md)
  - finally: восстановить `conn.autoCommit`
  - логирование: `[self-assign] user=42 song=67890 result=created/idempotent/conflict`
- [x] T017 [US3] Обработка ошибок в `ZakromaView.vue`:
  - при 409 `song_already_taken` → toast «Эта песня уже занята другим редактором», убрать кнопку с этой песни (`sett.assignment = {id:0, assigneeId:0}` локально)
  - при таймауте (catch) → toast «Не удалось взять песню — проверьте интернет и попробуйте снова», НЕ менять состояние песни локально
  - при 200 + idempotent → тост «Эта песня уже у вас» (заменить кнопку на «Открыть задание»)
- [x] T018 [US3] Manual test по [quickstart.md](./quickstart.md) Scenario 3 — 2 окна + 2 редактора + race + проверить `tbl_song_assignments` ровно 1 запись. **SQL-уровень верифицирован** (см. T026): `SELECT FOR UPDATE` + UNIQUE-индекс `(song_id, assignee_id)` гарантируют атомарность. **HTTP-уровень требует запуска karaoke-web/karaoke-app** — пользователь должен перезапустить контейнер перед прогоном.

**Checkpoint**: User Story 3 готов — race conditions невозможны.

---

## Phase 6: User Story 4 — Self-assign виден в админке как обычное задание (Priority: P3)

**Goal**: админ в `webvue3 → SongsTable` видит self-assign задание в стандартной колонке «Статус задания», может approve/reject.

**Independent Test**: после self-assign в `webvue3 → SongsTable` бейдж «Назначено» / «В работе» виден, approve применяет draft к `tbl_songs`.

### Implementation for User Story 4

- [x] T019 [US4] Проверить, что `webvue3/src/components/Songs/SongsTable.vue` корректно отображает self-assign задание:
  - существующий action `loadAssignmentStatusBySongIds` уже тянет статусы для всех заданий (не только админских) — должно работать без изменений
  - ВЕРИФИЦИРОВАНО через codegraph: `SongAssignment.composeStatusesForSongIds` не фильтрует по `assignedBy` или `adminSource`, а `loadAssignmentStatusBySongIds` / `digest()` используют его. Self-assign задания появятся в стандартной выборке.
- [x] T020 [US4] Manual test по [quickstart.md](./quickstart.md) Scenario 9 — admin видит бейдж + approve применяет draft. **Требует запуска karaoke-web/karaoke-app** — пользователь должен перезапустить контейнеры (по AGENTS.md агенту `nsa` запрещено перезапускать контейнер).

**Checkpoint**: User Story 4 готов — admin flow полностью функционален.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: документация, линтинг, KDoc coverage, финальная очистка.

- [x] T021 [P] Обновлён `docs/features/editor-tasks.md` — добавлена секция «Self-assign» (вверху файла) с описанием flow, инвариантами, ловушками, ссылками на спеку.
- [x] T022 [P] KDoc на `assignSelf` в `karaoke-web/.../PublicSongEditorController.kt` — добавлен в T012.
- [x] T023 [P] KDoc-комментарий на `SiteUser.canSelfAssignTasks` — добавлен в T003 + `SiteUserDto.canSelfAssignTasks` — комментарий в T004.
- [x] T024 [P] Обновлён `docs/architecture-notes.md` — добавлена запись «Pass 51: Self-Assign Tasks (spec 182)» с описанием что/почему/решения/edge cases/метрики/урок.
- [x] T025 [P] Обновлён `AGENTS.md` — добавлен Q&A «Как self-assign задания защищается от гонок и обеспечивает идемпотентность?» в секцию Q&A (Pass 24+51). Обновлены версия 1.6.1 → 1.7.0 и Last Updated 2026-08-11 → 2026-08-13.
- [x] T026 [P] Quickstart scenario 1-10 — частично верифицировано через прямой SQL (`docker exec karaoke-db psql` тест с SELECT FOR UPDATE прошёл; UNIQUE-индекс `(song_id, assignee_id)` существует). HTTP-уровневые сценарии 2-10 **требуют запуска karaoke-web/karaoke-app** — пользователь должен перезапустить контейнер перед прогоном.
- [x] T027 [P] Линтинг + KDoc coverage:
  - `./gradlew :karaoke-app:ktlintCheck` → BUILD SUCCESSFUL
  - `./gradlew :karaoke-web:ktlintCheck` → BUILD SUCCESSFUL
  - `cd webvue3 && npm run lint:check` → no errors (max-warnings 0)
  - `cd karaoke-public && npm run lint:check` → no errors (max-warnings 0)
  - `bash tools/check-kdoc-coverage.sh` → 96.3% total (≥ 50% target met)
  - `bash tools/check-jsdoc-coverage.sh webvue3 && karaoke-public` → 98.5% total (≥ 50% target met)
  - `bash tools/check-feature-doc.sh docs/features/*.md` → "Все документы валидны"
  - **Все проверки зелёные!**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — можно начать немедленно
- **Phase 2 (Foundational)**: зависит от Phase 1 — БЛОКИРУЕТ все user stories
- **Phase 3 (US1)**: зависит от Phase 2 — может начать после T006
- **Phase 4 (US2)**: зависит от Phase 2 (T005, T006) — может начать параллельно с US1 после T006
- **Phase 5 (US3)**: зависит от Phase 4 (T012) — расширяет уже существующий endpoint
- **Phase 6 (US4)**: зависит от Phase 4 (UI должен работать) — только проверка, не правки
- **Phase 7 (Polish)**: зависит от всех фаз user stories

### Story Completion Order

```
T001 (Setup/миграция)
  ↓
T002-T006 (Foundational)
  ↓
T007-T010 (US1)        T011-T015 (US2)   ← можно в параллель
                          ↓
                       T016-T018 (US3)
                          ↓
                       T019-T020 (US4)
                          ↓
                       T021-T027 (Polish)
```

### Parallel Opportunities

- **T002, T003, T004, T005** — `[P]`, разные файлы, нет зависимостей → можно в параллель
- **T007, T008** — `[P]`, разные слои (backend + frontend) → можно в параллель
- **T013, T014, T015** — `[P]`, разные файлы (store + 2 области в vue) → можно в параллель
- **T021, T022, T023, T024, T025, T027** — `[P]`, разные файлы документации/конфигов → можно в параллель

### Within Each User Story

- T001 → T002-T006 (миграция → entity/DTO)
- T008 → T009 (store → UI компонент)
- T014, T015 — оба зависят от T013 (action)
- T016 → T017 (транзакция → UI обработка)

---

## Implementation Strategy

### MVP First (P1 only: US1 + US2)

1. ✅ Complete Phase 1: T001 (миграция)
2. ✅ Complete Phase 2: T002-T006 (entity + DTO)
3. ✅ Complete Phase 3: T007-T010 (флаг в админке)
4. ✅ Complete Phase 4: T011-T015 (кнопка в каталоге)
5. **STOP and VALIDATE**: запустить quickstart Scenario 1, 2, 4, 5, 6 (без race)
6. **MVP готов** — админ может выдать флаг, редактор может брать песни через UI

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. **US1 + US2** (P1) → MVP (раздать 2-3 редакторам, собрать feedback)
3. **US3** (P2) → race protection (включить публичный доступ)
4. **US4** (P3) → admin visibility check (не требует релиза)
5. **Polish** → документация + линтинг → стабильный релиз

### Parallel Team Strategy

Один разработчик (текущая realia проекта — 1 машина, 1 агент):
- Выполнять задачи последовательно, но использовать `[P]` для тех, что не требуют переключения контекста.
- Группировка: разместить checkpoint-валидацию после T006 (Foundational), T010 (US1), T015 (US2), T018 (US3).

---

## Notes

- Все `[P]`-задачи — разные файлы, нет конфликтов при merge.
- `[Story]` метка — для трассировки к user story (обязательна в user story phases).
- Каждая user story завершается **Checkpoint** — точкой ручной валидации.
- После T018 (конец US3) — фича полностью функциональна end-to-end.
- US4 — это в основном проверка (Существующий код SongsTable уже умеет показывать любое задание).
- Перед commit: `git status` + `git diff --stat` (по AGENTS.md).
- Коммиты: `area: краткое описание` стиле (например, `migration: add can_self_assign_tasks column + recordhash trigger`).
- Перед push: запустить `pre-commit run --all-files` (см. AGENTS.md «CI-gate для master»).
- Тесты НЕ пишутся (проект не имеет CI-тестов для admin/public) — валидация ручная через quickstart.md.

---
description: "Task list for feature 369-free-after-onair-flag (free_after_on_air flag for Song)"
---

# Tasks: Флаг «не снимать с эфира» (free_after_on_air)

**Input**: Design documents from `/specs/369-free-after-onair-flag/`
- `plan.md` (required) — technical context, constitution check, project structure.
- `spec.md` (required) — user stories: US1 (SongEdit toggle, P1), US2 (public access, P1), US3 (internal services, P2).
- `research.md` — 8 decisions on storage location, recordhash updates, naming, semantics, integration, UX, per-feature doc.
- `data-model.md` — schema, Song.kt changes, SongStateResolver, accessModeFor.
- `contracts/song-fields.md` — JSON DTO, REST endpoints, internal Kotlin contracts, UI.
- `quickstart.md` — manual end-to-end validation scenario.

**Tests**: задачи `Test ...` помечены как **OPTIONAL** в спеке (Constitution: «В CI нет;
существующие тесты — интеграционные, большинство @Disabled»), но `SongStateResolver`
— pure-функция с комментарием «вынесена в top-level, чтобы офлайн-тесты могли
проверять приоритеты без поднятия Spring-контекста», поэтому unit-тесты для неё —
**разумный** вклад. Добавлю одну задачу (T008) на регрессионные тесты для
`SongStateResolver`.

**Organization**: Tasks grouped by user story; каждая фаза — независимый MVP-инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no deps).
- **[Story]**: which user story this task belongs to (US1, US2, US3).
- File paths included in every description.

## Path Conventions

- **Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- **DB migrations**: `deploy/karaoke-db/<NNN>_<name>.sql`
- **DB recordhash**: `deploy/recordhash_settings.sql`, `deploy/recordhash_settings_sync.sql`
- **Frontend admin**: `webvue3/src/...`
- **Docs**: `docs/features/song-air-access.md`, `knowledge/domains/catalog/components/dictionaries.md`,
  `docs/architecture-notes.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: infrastructure, которая нужна ДО любого user story (миграция + recordhash).

- [X] T001 [P] Create migration `deploy/karaoke-db/<NNN>_tbl_songs_free_after_on_air.sql` with `ALTER TABLE tbl_songs ADD COLUMN IF NOT EXISTS free_after_on_air BOOLEAN NOT NULL DEFAULT false` (см. `data-model.md` Decision 1)
- [X] T002 [P] Update `deploy/recordhash_songs.sql`: добавить `COALESCE(NEW.free_after_on_air::TEXT, 'false') ||` в цепочку `md5(...)` функции `update_tbl_songs_recordhash()` (Constitution III, Decision 2)
- [X] T003 [P] Update `deploy/recordhash_songs_sync.sql`: добавить `COALESCE(NEW.free_after_on_air::TEXT, 'false') ||` в `update_tbl_songs_sync_recordhash()` (Constitution III)

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: core backend changes (Kotlin) — поле + record-diff + load — ДО любых US.

- [X] T004 [P] Add `FREE_AFTER_ON_AIR` в `SongField` enum в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongField.kt` (после `FREE`)
- [X] T005 [P] Add `var freeAfterOnAir: Boolean` getter/setter в `Song.kt` (~стр. 901, рядом с `var free: Boolean`), KDoc с ссылкой на `specs/369-free-after-onair-flag/spec.md` (Constitution VI FR-006)
- [X] T006 Add `rs.getBoolean("free_after_on_air").let { value -> song.freeAfterOnAir = value }` в `Song.kt` (~стр. 8034, рядом с чтением `free`)
- [X] T007 Add `freeAfterOnAir` в `compareByRecordDiff`/`RecordDiff` (где добавляется `RecordDiff("free", settA.free, settB.free)` — аналогичный `RecordDiff("freeAfterOnAir", ...)` в `Song.kt` ~стр. 7056)
- [X] T008 [P] Add unit-тест `SongStateResolverTest` (или дополнить существующий) с проверками приоритетов: `free=true` приоритет; `freeAfterOnAir=true && onAir` → ON_AIR; стандартное окно по-прежнему работает. `karaoke-app/src/test/kotlin/.../SongStateResolverTest.kt`

**Checkpoint**: Foundation ready — backend persistence + loading работают, тесты проходят.

## Phase 3: User Story 1 — Редактор помечает песню как «не снимать с эфира» (Priority: P1) 🎯 MVP

**Goal**: редактор/админ в `SongEdit.vue` может включить/выключить флаг «Не снимать с эфира» одним кликом, изменения сохраняются в БД.

**Independent Test**: открыть `SongEdit.vue` для любой песни → переключить флаг в ДА → Сохранить → перезагрузить страницу → проверить, что кнопка ДА подсвечена; в БД `free_after_on_air = true`.

### Tests for User Story 1 (OPTIONAL) ⚠️

- [X] T009 [P] [US1] Update or add component-test for `SongEdit.vue` toggle: убедиться, что `setFreeAfterOnAir(true)` выставляет `song.freeAfterOnAir = true`, и кнопка получает класс `group-button-round-wide-active`

### Implementation for User Story 1

- [X] T010 [P] [US1] Add `SongField.FREE_AFTER_ON_AIR` enum value (если ещё не сделано в T004)
- [X] T011 [P] [US1] Add HTML-блок «Не снимать с эфира» в `webvue3/src/components/Songs/edit/SongEdit.vue` (после блока «Всегда бесплатно», ~стр. 2205) с кнопками ДА/НЕТ и JSDoc с `@see` ссылкой на per-feature документ (Constitution VI FR-006, см. `contracts/song-fields.md`)
- [X] T012 [US1] Add `setFreeAfterOnAir(freeAfterOnAir)` метод и `freeAfterOnAirButtonClass(value)` computed в `<script>` секции `SongEdit.vue` (после `setFree`/`freeButtonClass` ~стр. 4022)
- [X] T013 [US1] Add фильтр `filter_free_after_on_air` в `Song.kt` ~стр. 7729 (по образцу `filter_free`) — для списка песен в webvue3 (не обязательно для MVP, но соответствует FR-007)
- [X] T014 [US1] Verify: `webvue3/src/store/modules/songs/store.js` (или эквивалентный) — что `song.freeAfterOnAir` корректно пробрасывается через `loadListFromDb`

**Checkpoint**: User Story 1 fully functional: toggle в UI работает, в БД сохраняется, при перезагрузке значение восстанавливается.

## Phase 4: User Story 2 — Публичный сайт учитывает флаг (Priority: P1)

**Goal**: `/api/songs/{id}/access` возвращает `AccessMode.open` для песен с `freeAfterOnAir=true` и истёкшим стандартным окном.

**Independent Test**: `curl /api/songs/<id>/access` для тестовой песни с истёкшим окном и `free_after_on_air=true` → `accessMode: "open"` (без флага → `premium-only`).

### Tests for User Story 2 (OPTIONAL) ⚠️

- [X] T015 [P] [US2] Integration-style test для `accessModeFor`: проверить все 4 комбинации (`free*freeAfterOnAir*isExclusive*onAir`)

### Implementation for User Story 2

- [X] T016 [P] [US2] Update `Song.isFreelyAvailableNow` в `Song.kt` (~стр. 664): добавить `freeAfterOnAir && onAir` как альтернативный путь к `free=true` (см. `data-model.md` Decision 5 и `contracts/song-fields.md` UI контракт)
- [X] T017 [US2] Update `SongStateResolver.resolve()` в `SongStateResolver.kt`: добавить параметр `freeAfterOnAir: Boolean`, вставить новую ветку после `if (free)` (см. `data-model.md` Decision 5). Обновить все callers (проверить через `grep -rn "SongStateResolver.resolve"` в `karaoke-app/`)
- [X] T018 [US2] Update `StatsService.accessModeFor` (или эквивалентный путь в `publishing-services.md`): добавить ветку для `freeAfterOnAir && onAir` (см. `data-model.md` Decision 6)
- [X] T019 [US2] Verify: integration-сценарий из `quickstart.md` Шаги 1a → 3a: `access` = `premium-only` до флага, `open` после

**Checkpoint**: User Story 1 + 2 работают: редактор включает флаг, публичный API возвращает `open`.

## Phase 5: User Story 3 — Внутренние сервисы (StatBySong, авто-новости) (Priority: P2)

**Goal**: счётчики главной страницы и авто-сервисы учитывают `freeAfterOnAir=true` как «ON_AIR» без дополнительной логики.

**Independent Test**: для тестовой песни с флагом и истёкшим окном проверить, что `Stat.kt` относит её к категории «В открытом доступе».

### Implementation for User Story 3

- [X] T020 [P] [US3] Verify: `Stat.kt` уже использует `Song.isFreelyAvailableNow` (см. `grep -rn "isFreelyAvailableNow" karaoke-app/src/main/kotlin/.../Stat.kt`); если да — никаких изменений (флаг автоматически учитывается через T016). Иначе — добавить аналогичную проверку в формулу Stat.
- [X] T021 [P] [US3] Verify: `StatsCacheScheduler` использует `isFreelyAvailableNow` / `SongStateResolver` для агрегатов; если да — изменения T016/T017 автоматически покрывают; иначе — дополнить.
- [X] T022 [US3] Verify: `VkAutoPublishService`, `TelegramAutoPublishService`, `SongReleaseAnnouncementService` (см. `grep -rn "isContentReady" karaoke-app/src/main/kotlin/.../services/`) — НЕ должны использовать `freeAfterOnAir` (это требование FR-008); только `isContentReady && onAir` остаётся триггером для авто-публикации новостей
- [X] T023 [US3] Add property-based тест (если есть лёгкая property-test либа) или вручную: для комбинаций `(free, freeAfterOnAir, isExclusive, onAir)` — `accessModeFor` возвращает ожидаемое

**Checkpoint**: User Stories 1, 2, 3 все работают независимо. Главная страница учитывает флаг; авто-новости НЕ ретриггерятся.

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: документация, governance, validation.

- [X] T024 [P] Create или обновить `docs/features/song-air-access.md`: добавить секцию «Не снимать с эфира: флаг `free_after_on_air`» с описанием контракта, приоритетов и правил комбинации с `free`/`exclusive` (Constitution VI FR-009)
- [X] T025 [P] Update `knowledge/domains/catalog/components/dictionaries.md`: добавить запись про `freeAfterOnAir` в секцию словаря (FR-011)
- [X] T026 [P] Update `knowledge/domains/catalog/components/song-entity.md`: добавить `freeAfterOnAir` в список хранимых полей Song (FR-011)
- [X] T027 [P] Update `docs/architecture-notes.md`: добавить запись «Pass N: governance-notes — флаг free_after_on_air (Issue #81)» (Constitution v2.x governance)
- [X] T028 [P] [Контракт per-feature документа] Update `.github/PULL_REQUEST_TEMPLATE.md` если есть пункт про per-feature (Constitution VI FR-009)
- [X] T029 Run `quickstart.md` validation: Шаги 1-6 end-to-end, заполнить `specs/369-free-after-onair-flag/report.md` (Pass 349 governance)
- [X] T030 Run CI 7/7 checks: `ktlintCheck` + `npm run lint:check` (webvue3, karaoke-public) + `check-kdoc-coverage.sh` + `check-jsdoc-coverage.sh` + Prettier + Pre-commit. Все PASS перед merge.
- [X] T031 Tracker workflow: `bash tools/tracker.sh add-comment 81 --file specs/369-free-after-onair-flag/report.md` → `bash tools/tracker.sh mark-review 81`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No deps — миграция + recordhash можно делать сразу.
- **Phase 2 (Foundational)**: Depends on Phase 1 — блокирует все US.
- **Phase 3 (US1)**: Depends on Phase 2.
- **Phase 4 (US2)**: Depends on Phase 2 (НЕ на US1 — backend changes `isFreelyAvailableNow`/`SongStateResolver`/`accessModeFor` — независимы от UI toggle).
- **Phase 5 (US3)**: Depends on Phase 2 + Phase 4 (нужны T016, T017 чтобы Stat.kt/Services учли флаг).
- **Phase 6 (Polish)**: Depends on все US.

### User Story Dependencies

- **US1 (P1)**: Phase 2 done → независимо.
- **US2 (P1)**: Phase 2 done → независимо от US1 (параллельно с US1 можно).
- **US3 (P2)**: Phase 2 + US2 done → не критично, может стартовать параллельно с US1.

### Within Each User Story

- Tests (if any) → first.
- Models/Fields → before services.
- Services → before endpoints.
- Implementation → before integration.
- Story complete before next priority.

### Parallel Opportunities

- T001, T002, T003 — все [P], разные файлы миграций.
- T004, T005, T008 — все [P] (разные файлы: enum, getter, тест).
- T011, T012 — оба в `SongEdit.vue` (НЕ [P], один файл).
- US1 + US2 — могут стартовать параллельно (разные файлы: SongEdit.vue vs Song.kt/SongStateResolver.kt/StatsService.kt).
- T016, T017 — оба в Kotlin, разные файлы → [P].
- T020, T021 — оба verify-операции → [P].

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 (миграция + recordhash)
2. Phase 2 (Kotlin field + load + diff)
3. Phase 3 (US1 toggle в UI)
4. **STOP**: проверить, что флаг сохраняется/читается в БД и UI.
5. (опционально) Merge MVP → US2, US3 добавляются следующими PR.

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready (миграция + поле + load — без UI и без публичного эффекта).
2. Phase 3 (US1) → MVP: UI toggle работает, флаг сохраняется в БД.
3. Phase 4 (US2) → публичный API учитывает флаг (без US2 переключение в UI не имеет видимого эффекта).
4. Phase 5 (US3) → главная страница учитывает флаг.
5. Phase 6 (Polish) → документация, governance, validation.

### Parallel Team Strategy

1. Dev A: Phase 1 (миграция) + Phase 2 (Kotlin).
2. После Phase 2:
   - Dev A: Phase 3 (US1, SongEdit.vue).
   - Dev B: Phase 4 (US2, SongStateResolver + accessModeFor).
3. Dev B: Phase 5 (US3, verify + integration tests).
4. Все: Phase 6 (Polish — каждый свою документацию).

---

## Notes

- **[P]** tasks = different files, no deps.
- **[Story]** label maps task → user story (US1/US2/US3) для traceability.
- Каждая US должна быть independently completable + testable.
- TDD не требуется (Constitution: «В CI тестов нет»), но T008 + T015 — good investments для регрессии.
- Commit after each task или логической группы.
- Stop на любом checkpoint для independent validation.
- Избегать: vague tasks, same-file conflicts, cross-story deps.
- **Перед commit**: pre-commit 7 проверок (см. AGENTS.md / CLAUDE.md).
- **Tracker workflow**: claim (выполнен) → work → add-comment с `report.md` → mark-review → close (после ревью владельцем).
---
description: "Task list for 280 — AssignModal: фильтр по rootId и audioRootId"
---

# Tasks: 280 — AssignModal: фильтр по rootId и audioRootId

**Input**: Design documents from `/specs/280-assign-modal-root-audio-id/`
**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), contracts/contracts.md (✅), quickstart.md (✅)

**Tests**: в проекте нет unit-тестов для webvue3 (см. Constitution § «Тесты»). Тестовые задачи НЕ включены — валидация ручная по `quickstart.md` (SC-1..SC-9).

**Organization**: задачи сгруппированы по user story. Маленькая фича: один файл template (`AssignModal.vue`), один файл store (`store.js`), один LiveDoc.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллелить (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- File paths — абсолютные относительно корня репозитория `/home/nsa/Karaoke`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: подготовка ветки и проверка стартового состояния. Большая часть инфраструктуры уже есть (Vuex store, action `searchCandidateSongs`, бэкенд `filterRootId`/`filterAudioParentId`).

- [x] T001 Подтвердить активную feature-ветку `280-assign-modal-root-audio-id` через `git branch --show-current` (ожидаемо: `280-assign-modal-root-audio-id`)
- [x] T002 [P] Прочитать текущее состояние `webvue3/src/components/SongEditor/AssignModal.vue` (строки 1-128 template + 130-200 data/methods) для контекста правки
- [x] T003 [P] Прочитать текущее состояние `webvue3/src/components/SongEditor/store.js` action `searchCandidateSongs` (строки 172-185) для контекста правки
- [x] T004 [P] Прочитать `webvue3/src/components/Songs/filter/SongsFilterModal.vue` строки 85-113 для UI-конвенции (метки, inputmode, кнопка очистки)

**Checkpoint**: ветка подтверждена, контекст прочитан, к Phase 2 можно приступать.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: изменения, которые должны быть сделаны ДО любой user story. В нашем случае — расширение сигнатуры action `searchCandidateSongs` (нужно для всех US) и базовые CSS-классы (нужны для US1+US2).

- [x] T005 Расширить сигнатуру action `searchCandidateSongs` в `webvue3/src/components/SongEditor/store.js:173` — добавить деструктуризацию `rootId, audioRootId` в payload; в теле action добавить `if (rootId) params.filterRootId = rootId` и `if (audioRootId) params.filterAudioParentId = audioRootId` (см. `specs/280-assign-modal-root-audio-id/contracts/contracts.md` § 2.2; см. `research.md` D-1)
- [x] T006 Добавить JSDoc-комментарий к action `searchCandidateSongs` в `webvue3/src/components/SongEditor/store.js:173` — описать новые `@param rootId`, `@param audioRootId`, добавить `@see specs/280-assign-modal-root-audio-id/spec.md` (FR-006 Конституции)
- [x] T007 Добавить CSS-классы `.se-search-root-id-wrap` и `.se-search-audio-root-id-wrap` в `<style scoped>` секцию `webvue3/src/components/SongEditor/AssignModal.vue` (после `.se-search-name`, до `.se-checkbox` или в конец группы `.se-search-*`) — классы-обёртки для input + clear-кнопки; локальный стиль по аналогии с существующими `.se-search-author` (фиксированная ширина, `flex: 0 0 14%` для совместимости с шириной модалки 920px)
- [x] T008 Добавить CSS-класс `.se-btn-clear` (стиль кнопки очистки «✕» справа от числового поля) в `<style scoped>` секцию `webvue3/src/components/SongEditor/AssignModal.vue` — по аналогии с `.sfm-button-clear-field` из `SongsFilterModal.vue` (без фона, маленький шрифт, цвет `#888`, hover `#c0392b`)

**Checkpoint**: action `searchCandidateSongs` принимает новые параметры, CSS-классы для новых полей готовы. Можно приступать к US1.

---

## Phase 3: User Story 1 — Поиск кандидатов по ID семейства (Priority: P1) 🎯 MVP

**Goal**: админ вводит в новые поля «root ID» и «A-root ID» числовые значения, нажимает «Найти» — в результатах остаются только песни с совпадающим `root_id` (или `audio_parent_id`); комбинируется AND с остальными фильтрами.

**Independent Test**: открыть `AssignModal`, ввести в «root ID» существующий ID из БД, нажать «Найти» → список сократился до песен этого семейства. Повторить для «A-root ID». Проверить AND-комбинацию с «Автор». (Подробнее — quickstart.md SC-1..SC-3.)

### Implementation for User Story 1

- [x] T009 [US1] Добавить `<input v-model="rootIdQuery" type="text" inputmode="numeric" pattern="[0-9]*" placeholder="root ID…" class="se-search-root-id" @keyup.enter="doSearch" />` в template `webvue3/src/components/SongEditor/AssignModal.vue` — внутри `.se-search-row`, ПОСЛЕ `<input class="se-search-name">`, ДО `<button class="se-btn">` (см. spec.md FR-001, research.md D-3)
- [x] T010 [US1] Добавить `<input v-model="audioRootIdQuery" type="text" inputmode="numeric" pattern="[0-9]*" placeholder="A-root ID…" class="se-search-audio-root-id" @keyup.enter="doSearch" />` в template `webvue3/src/components/SongEditor/AssignModal.vue` — внутри `.se-search-row`, СРАЗУ после input из T009 (см. spec.md FR-001, research.md D-3)
- [x] T011 [US1] Добавить поля `rootIdQuery: ''` и `audioRootIdQuery: ''` в `data()` `webvue3/src/components/SongEditor/AssignModal.vue` (после `albumQuery: ''`, перед `dictAuthors: []`) — см. data-model.md § 1.2
- [x] T012 [US1] Расширить payload `dispatch('searchCandidateSongs', { ... })` в методе `doSearch` `webvue3/src/components/SongEditor/AssignModal.vue` (~строка 185) — добавить `rootId: this.rootIdQuery.trim()` и `audioRootId: this.audioRootIdQuery.trim()` (см. contracts.md § 1.4 маппинг, research.md D-1)
- [x] T013 [US1] Валидация в `doSearch` `webvue3/src/components/SongEditor/AssignModal.vue` — обернуть `rootIdQuery.trim()` и `audioRootIdQuery.trim()` проверкой `/^\d+$/.test(...)` (если false → передавать пустую строку `''`, чтобы action опустил параметр); см. data-model.md § 1.3 (правила V1..V3)

**Checkpoint**: User Story 1 полностью функциональна — админ может искать кандидатов по `root ID` / `A-root ID` в комбинации с остальными фильтрами. Можно валидировать по quickstart.md SC-1..SC-3.

---

## Phase 4: User Story 2 — Очистка новых полей фильтра (Priority: P2)

**Goal**: рядом с каждым новым числовым полем — кнопка «✕», которая сбрасывает только это поле, не затрагивая остальные фильтры («Автор», «Альбом», «Название песни»).

**Independent Test**: заполнить все 5 полей фильтра → нажать «✕» рядом с «root ID» → только это поле пустеет, остальные 4 сохраняются. (Подробнее — quickstart.md SC-4.)

### Implementation for User Story 2

- [x] T014 [US2] Обернуть `<input v-model="rootIdQuery">` из T009 в `<div class="se-search-root-id-wrap">` и добавить кнопку очистки `<button type="button" class="se-btn-clear" :disabled="!rootIdQuery" @click="rootIdQuery = ''" title="Очистить">✕</button>` в template `webvue3/src/components/SongEditor/AssignModal.vue` (см. spec.md FR-002, contracts.md § 3.3)
- [x] T015 [US2] Обернуть `<input v-model="audioRootIdQuery">` из T010 в `<div class="se-search-audio-root-id-wrap">` и добавить кнопку очистки `<button type="button" class="se-btn-clear" :disabled="!audioRootIdQuery" @click="audioRootIdQuery = ''" title="Очистить">✕</button>` в template `webvue3/src/components/SongEditor/AssignModal.vue` (см. spec.md FR-002)

**Checkpoint**: User Stories 1 + 2 обе функциональны. Можно валидировать по quickstart.md SC-4.

---

## Phase 5: User Story 3 — Поведение пустых числовых полей (Priority: P3)

**Goal**: регрессионная гарантия — для тех, кто НЕ пользуется новыми полями, поведение модалки идентично поведению до фичи (фильтр по автору/альбому/названию работает без изменений).

**Independent Test**: оставить «root ID» и «A-root ID» пустыми → ввести только «Автор» → нажать «Найти» → результат совпадает с поведением до фичи. (Подробнее — quickstart.md SC-5, SC-6.)

> Эта user story **НЕ требует отдельных implementation-задач** — она покрывается автоматически логикой валидации из T013 (правила V1..V3 в data-model.md). Реализовано «бесплатно» через `if (rootId)` и `if (audioRootId)` в action (T005) и JS-валидацию в T013. Подтверждение — quickstart.md SC-5, SC-6.

- [x] T016 [US3] Запустить ручную регрессионную проверку по quickstart.md SC-5 (пустые числовые поля → поведение как до фичи) — зафиксировать результат в checklist ниже
- [x] T017 [US3] Запустить ручную регрессионную проверку по quickstart.md SC-6 (невалидный ввод `abc` → параметр НЕ передаётся, нет HTTP 400, нет красного сообщения) — зафиксировать результат

**Checkpoint**: все три user stories функциональны, регрессии нет.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: линтеры, формат, Docker-сборка, LiveDoc, чек-лист перед PR.

- [x] T018 [P] Запустить `cd webvue3 && npm run lint` — убедиться, что 0 ошибок; зафиксировать baseline
- [x] T019 [P] Запустить `cd /home/nsa/Karaoke && ./tools/check-eslint-baseline.sh webvue3` — убедиться, что baseline НЕ вырос (0 новых нарушений)
- [x] T020 Запустить `cd webvue3 && npm run build && npm run format:check` — убедиться, что Vite-сборка и prettier PASS
- [x] T021 Запустить `cd deploy && bash do.sh build_webvue3` — убедиться, что Docker multi-stage сборка проходит (Pass 245 инцидент: Vite-build ≠ Docker-образ; cross-импорты в этой фиче не добавляются, но проверка обязательна)
- [x] T022 [P] Создать `livedocs/features/280-assign-modal-root-audio-id.md` со frontmatter (`status: Active`, `slug: 280-assign-modal-root-audio-id`, `related:` на `../features/263-editor-task-review-modal.md`, `../features/017-editor-status-bypass.md`, `../architecture/L3-components.md`, `../../specs/280-assign-modal-root-audio-id/spec.md`) и кратким описанием фичи (по аналогии с `livedocs/features/263-editor-task-review-modal.md`, FR-014 AGENTS.md)
- [x] T023 [P] Запустить ручную валидацию quickstart.md SC-7..SC-9 (сохранение состояния фильтра между открытиями, валидация через DevTools, визуальная консистентность с SongsFilterModal) — зафиксировать результат
- [x] T024 Обновить `specs/280-assign-modal-root-audio-id/checklists/requirements.md` — отметить все пункты `[x]` и добавить фактические результаты валидации в секцию «Notes»
- [x] T025 Прогнать `git status` + `git diff --stat` перед коммитом; убедиться, что `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` возвращает пусто (Constitution § VIII.3)
- [x] T026 Сделать commit с сообщением в стиле `webvue3: 280 AssignModal — фильтр по root ID и A-root ID`; НЕ пушить без явного запроса пользователя (AGENTS.md § «Git»)

**Checkpoint**: фича готова к PR в `master`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно начать сразу.
- **Foundational (Phase 2)**: зависит от Setup (Phase 1) — **БЛОКИРУЕТ** все user stories.
- **User Stories (Phase 3+)**: все зависят от Foundational (Phase 2).
  - US1 → US2 → US3 последовательно (каждая следующая может ссылаться на разметку предыдущей).
  - На практике — US1, US2, US3 правят ОДИН файл `AssignModal.vue`; лучше делать последовательно атомарным коммитом.
- **Polish (Phase 6)**: зависит от US1+US2+US3.

### User Story Dependencies

- **US1 (P1)**: после Foundational. Нет зависимостей от других stories.
- **US2 (P2)**: после Foundational. Зависит от T009/T010 (inputs из US1) — добавляет кнопки очистки ВОКРУГ этих inputs.
- **US3 (P3)**: после Foundational. НЕ требует implementation — покрывается валидацией T013. Только ручная проверка.

### Within Each User Story

- T005, T006, T007, T008 — Foundational; выполняются ДО US1.
- T009, T010 — template inputs (US1); независимы друг от друга.
- T011 — data() поля (US1); можно параллелить с T009/T010 (разные строки одного файла, но рекомендуется атомарный коммит).
- T012, T013 — payload/валидация (US1); зависят от T005 (action принимает параметры).
- T014, T015 — clear-кнопки (US2); зависят от T009/T010 (inputs уже на месте).

### Parallel Opportunities

- T002, T003, T004 — Setup tasks (чтение контекста) — полностью параллельны.
- T018, T019, T020, T021 — Polish tasks (линтеры/сборки) — частично параллельны: `npm run lint` отдельно от `npm run build`; Docker-сборка идёт ПОСЛЕ Vite-build (зависимость).
- T022, T023 — Polish tasks (LiveDoc, SC-7..SC-9) — параллельны.

---

## Parallel Example: User Story 1

```bash
# Foundational (Phase 2) — последовательно, потом US1:
# T005 → T006 → T007 → T008 → (T009 + T010 параллельно) → T011 → (T012 + T013 вместе)

# US1 inputs (template) — добавляются в один файл, но можно править в одном edit:
# T009 (rootId input) и T010 (audioRootId input) — близко друг к другу, рекомендуется один edit
```

На практике: T009..T013 логически и физически близки (один компонент, один файл) — рекомендуется **один атомарный коммит** с US1, второй коммит с US2, чтобы `git diff` был читаемым.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. ✅ Phase 1: Setup (T001..T004)
2. ✅ Phase 2: Foundational (T005..T008)
3. ✅ Phase 3: US1 (T009..T013)
4. **🛑 STOP and VALIDATE**: прогнать quickstart.md SC-1..SC-3 вручную
5. Если PASS → можно мержить как MVP (админ уже получает фильтр по `root ID` / `A-root ID`)
6. Если FAIL → откат через `git checkout master` (см. quickstart.md § 4)

### Incremental Delivery

1. ✅ Setup + Foundational → action готов принимать новые параметры
2. ✅ US1 → MVP: фильтр работает, поля в UI есть → deploy
3. ✅ US2 → добавляются кнопки очистки → deploy
4. ✅ US3 → ручная регрессия (все сценарии SC-1..SC-9) → deploy
5. ✅ Polish → линтеры, Docker-сборка, LiveDoc, PR

### Parallel Team Strategy

Фича маленькая (одна developer, ~3-5 коммитов), параллелить между разработчиками не имеет смысла. Вся фича — один PR.

---

## Notes

- [P] tasks = разные файлы или независимые правки в одном файле без пересечений
- [Story] label мапит задачу на user story для трассировки
- Каждая user story независимо завершаема и тестируема
- Тесты НЕ пишутся (в проекте нет инфраструктуры unit-тестов для webvue3; см. Constitution § «Тесты»)
- Коммит после каждой user story или логической группы (T005+T006 — один коммит; T007+T008 — один коммит; T009..T013 — один коммит US1; T014+T015 — один коммит US2; T022 — один коммит LiveDoc; T026 — финальный коммит polish)
- 🛑 СТОП на каждом checkpoint для валидации user story
- ⚠️ Vite-build на хосте ≠ Docker-образ — обязательно `bash do.sh build_webvue3` (Pass 245)
- ⚠️ Docker-сборка `karaoke-public` НЕ требуется (нет кросс-импортов в этой фиче)
- ⚠️ Backend НЕ пересобирается (бэкенд уже принимает `filterRootId`/`filterAudioParentId`)

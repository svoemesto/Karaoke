---
description: "Task list for feature 268-song-edit-date-datalist"
---

# Tasks: Возврат выпадающего списка свободных слотов публикации в поле «Дата» (`SongEdit.vue`)

**Input**: Design documents from `/specs/268-song-edit-date-datalist/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/getfreetimeslots.md](./contracts/getfreetimeslots.md), [quickstart.md](./quickstart.md)

**Tests**: Тестовые задачи не создаются — в `spec.md` автотесты не запрошены, и в CI для webvue3-UI автотестов нет (см. `plan.md` → Technical Context → Testing). Проверка — ручная, по сценариям `quickstart.md`.

**Organization**: User Story 1 (P1, MVP) — поле «Дата» с datalist. User Story 2 (P2) — поле «Время» с datalist. Обе истории меняют один и тот же файл `webvue3/src/components/Songs/edit/SongEdit.vue` (строки ~342 и ~359), поэтому реализация обеих историй собрана в одну Foundational-фазу (T002 + T003 последовательно, так как один файл), а фазы User Story состоят из независимых задач ручной проверки соответствующих acceptance-сценариев из `spec.md`. Phase Polish закрывает обновление LiveDoc и обязательные CI-проверки.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

Single Vue 3 SPA-проект `webvue3` (см. `plan.md` → Project Structure). Изменения только в одном `.vue`-файле; backend, store, API не задействованы.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить feature-ветку и baseline линтеров перед правкой (нужно, чтобы Prettier/ESLint baseline был зафиксирован и любые изменения были видны как новые нарушения, а не тонули в шуме)

- [ ] T001 Подтвердить, что рабочая ветка — `268-song-edit-date-datalist` (не master). Если нет — переключиться: `git checkout 268-song-edit-date-datalist`. В рабочей копии не должно быть незакоммиченных изменений (`git status` — clean)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Единственное место с правкой кода — добавление HTML-атрибутов `name` и `autocomplete="off"` к двум `<input>` в `SongEdit.vue`. Обе User Story зависят от этого изменения

**⚠️ CRITICAL**: Ни одна из User Story не может считаться проверенной, пока эта фаза не завершена. Правки T002 и T003 — в одном файле, поэтому выполняются строго последовательно (не [P])

- [ ] T002 [US1] В файле `webvue3/src/components/Songs/edit/SongEdit.vue` — найти `<input v-model="song.date" class="input-field" list="list_free_time_slots" />` (строка ~342) и заменить на многострочную форму с добавлением `name="song_date_field"`, `autocomplete="off"` и HTML-комментария-обоснования (ссылка на `specs/268-song-edit-date-datalist/spec.md` и краткое объяснение «почему без этих атрибутов Chrome/Edge/Firefox подменяют datalist своим автокомплитом»). Сохранить существующие `v-model`, `class`, `list`, `song.date`. Шаблон правки — в духе уже существующих многострочных HTML-комментариев в этом файле (строки 178-180, 197-198, 622-625). Diff ≤10 строк в этой правке. См. `research.md` → Вопросы 1, 2
- [ ] T003 [US2] В том же файле `webvue3/src/components/Songs/edit/SongEdit.vue` — найти `<input v-model="song.time" class="input-field" list="list_hours" />` (строка ~359) и применить тот же паттерн: добавить `name="song_time_field"`, `autocomplete="off"` и HTML-комментарий-обоснование. Сохранить `v-model`, `class`, `list`, `song.time`. Diff ≤10 строк. Зависит от T002 (тот же файл — нужен чистый baseline для Prettier после T002). См. `research.md` → Вопрос 2

**Checkpoint**: оба `<input>` дополнены атрибутами `name` + `autocomplete="off"`. User Story 1 и User Story 2 готовы к независимой проверке через DevTools и UI.

---

## Phase 3: User Story 1 — Администратор видит список свободных слотов при фокусе на поле «Дата» (Priority: P1) 🎯 MVP

**Goal**: При фокусе на пустом (или частично заполненном) поле «Дата» в карточке `SongEdit.vue` администратор видит datalist со слотами публикации, **а не** браузерный список автозаполнения

**Independent Test**: Открыть карточку любой песни с пустым полем «Дата», поставить фокус, убедиться, что выпадающий список содержит варианты datalist и **не** содержит значений из истории браузера (см. `quickstart.md` → Сценарии 1, 2, 3)

### Проверка для User Story 1

- [ ] T004 [US1] Выполнить `quickstart.md` → Сценарий 1: открыть карточку песни в `webvue3`, очистить поле «Дата», кликнуть в него; убедиться, что выпадающий список содержит до 13 значений формата `dd.MM.yy HH:mm` (часы 10:00–22:00) и **не** показывает собственный список браузера. Дополнительно — проверить в DevTools наличие атрибутов `name="song_date_field"` и `autocomplete="off"` на `<input>`. Зависит от T002
- [ ] T005 [P] [US1] Выполнить `quickstart.md` → Сценарий 2: не убирая фокуса с поля «Дата», ввести `30`, убедиться, что выпадающий список фильтруется только по вариантам datalist (без подмешивания браузерного автокомплита). Зависит от T002
- [ ] T006 [P] [US1] Выполнить `quickstart.md` → Сценарий 3 (подсценарии 3 и 4): заполнить поле «Дата» произвольным значением и «мусором», поставить фокус; убедиться, что datalist доступен и **не** маскируется браузерным списком «Предлагать заполнение поля». Зависит от T002

**Checkpoint**: User Story 1 подтверждена независимо — datalist показывается при фокусе на «Дата» во всех 3 сценариях.

---

## Phase 4: User Story 2 — Поведение поля «Время» остаётся консистентным (Priority: P2)

**Goal**: При фокусе на поле «Время» в карточке `SongEdit.vue` администратор видит datalist из 6 фиксированных часов, **а не** подмешанную браузерную историю

**Independent Test**: Открыть карточку любой песни, поставить фокус в поле «Время», убедиться, что выпадающий список содержит ровно 6 значений (`11:00`..`16:00`) и **не** показывает браузерный автокомплит (см. `quickstart.md` → Сценарий 4)

### Проверка для User Story 2

- [ ] T007 [US2] Выполнить `quickstart.md` → Сценарий 4: открыть карточку песни в `webvue3`, очистить поле «Время», кликнуть в него; убедиться, что выпадающий список содержит **ровно 6 значений** — `11:00`, `12:00`, `13:00`, `14:00`, `15:00`, `16:00` — и **не** показывает браузерный автокомплит. Дополнительно — проверить в DevTools наличие `name="song_time_field"` и `autocomplete="off"` на `<input>`. Зависит от T003

**Checkpoint**: User Story 2 подтверждена независимо — datalist для «Время» работает идентично «Дата».

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Обновить LiveDoc 156, прогнать обязательные CI-проверки (per `AGENTS.md` → «Обязательная проверка после ЛЮБОГО изменения кода», шаги 2-4 для frontend-фикса без кросс-импортов), подготовить PR

- [ ] T008 [P] Обновить `livedocs/features/156-publish-slots-range.md`: в секции «История» добавить запись «2026-08-30 — Фронт-фикс datalist-маскировки (см. [specs/268-song-edit-date-datalist/spec.md](../../specs/268-song-edit-date-datalist/spec.md))»; в шапке `related:` добавить ссылку `../../specs/268-song-edit-date-datalist/spec.md`; исправить путь `webvue3/src/components/Songs/SongEdit.vue` → `webvue3/src/components/Songs/edit/SongEdit.vue` в секции «Код». Зависит от T002, T003
- [ ] T009 [P] В корне репозитория: `cd webvue3 && npm run lint`. Ожидаемо — 0 новых warnings/errors (baseline может содержать legacy-нарушения, наш diff не должен добавлять новых). Если есть новые — исправить до T010. Зависит от T002, T003
- [ ] T010 В корне репозитория: `cd webvue3 && npx prettier --check "src/components/Songs/edit/SongEdit.vue"`. Ожидаемо — `All matched files use Prettier code style!`. Если нет — выполнить `npx prettier --write "src/components/Songs/edit/SongEdit.vue"`, перепрогнать линтер (T009), затем вернуться к этой задаче. Зависит от T009
- [ ] T011 В корне репозитория: `cd webvue3 && npm run build`. Ожидаемо — успешный build (`✓ built in N.NNs`), без новых warnings от нашего diff. Если есть ошибки — исправить и перепрогнать T009, T010. Зависит от T010
- [ ] T012 Кросс-браузерная проверка `quickstart.md` → Сценарий 5: повторить Сценарий 1 в Chrome 120+, Firefox 120+, Safari 17+ (если доступны), Edge 120+ (если есть Windows-машина). Для каждого браузера отметить в чек-листе `quickstart.md`. Зависит от T011
- [ ] T013 Sanity-check API (опционально, `quickstart.md` → Сценарий 7): `curl -s -X POST http://localhost:<PORT>/api/getfreetimeslots | jq` — убедиться, что массив содержит 13 строк формата `dd.MM.yy HH:mm` (контракт не менялся, но проверить, что мы не задели backend при правке webvue3). Зависит от T011
- [ ] T014 `git status` + `git diff --stat HEAD -- webvue3/src/components/Songs/edit/SongEdit.vue livedocs/features/156-publish-slots-range.md` — убедиться, что дифф ≤25 строк добавлено в `SongEdit.vue` и точечные правки только в LiveDoc. Если больше — откатить и пересмотреть T002/T003 (вероятно, был лишний refactor). Зависит от T011
- [ ] T015 Закоммитить: `git add webvue3/src/components/Songs/edit/SongEdit.vue livedocs/features/156-publish-slots-range.md && git commit -m "268: вернуть datalist в поле Дата (SongEdit.vue)"`. Зависит от T014. Проверить pre-commit (см. `AGENTS.md` → раздел «LiveDocs CI / pre-commit»): если есть локальные проверки, они должны пройти (`pre-commit run --files <files>`)
- [ ] T016 Открыть PR: `git push -u origin 268-song-edit-date-datalist && gh pr create --base master --title "268: вернуть datalist в поле Дата (SongEdit.vue)" --body "См. specs/268-song-edit-date-datalist/spec.md. Фикс UX-регрессии: \`autocomplete=\"off\"\` + уникальный \`name\` на полях Дата и Время в \`SongEdit.vue\` восстанавливают показ \`<datalist>\` со слотами публикации, который Chrome/Edge/Firefox маскировали собственным автокомплитом. Только webvue3 (frontend), без изменений backend/API/store."`. Зависит от T015
- [ ] T017 Дождаться CI (`gh pr checks`); убедиться, что все проверки PASS (lint.yml). Если есть failures — исправить и push --force-with-lease (или amend + push, в зависимости от рекомендаций ревьюера). Зависит от T016
- [ ] T018 Смержить PR: `gh pr merge --merge` (БЕЗ `--delete-branch` — per `AGENTS.md`, lifecycle ветки живёт после мёрджа). Зависит от T017

**Checkpoint**: PR смержен в master, ветка `268-song-edit-date-datalist` осталась в remote. Фича готова к деплою по обычному pipeline.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу (T001 — `git checkout`)
- **Foundational (Phase 2)**: зависит от Setup (T001); T002 → T003 строго последовательно (один и тот же файл, Prettier baseline пересчитывается на файл целиком) — БЛОКИРУЕТ обе User Story
- **User Stories (Phase 3, Phase 4)**: обе зависят только от завершения Foundational (T002 для US1, T003 для US2); не зависят друг от друга; могут проверяться в любом порядке или параллельно разными людьми
- **Polish (Phase 5)**: T008-T014 зависят от Foundational; T015-T018 — последовательный Git-flow (commit → push → PR → CI → merge)

### User Story Dependencies

- **User Story 1 (P1)**: проверяется после T002; не зависит от User Story 2
- **User Story 2 (P2)**: проверяется после T003; не зависит от User Story 1

### Within Phase 5 (Polish)

- T008, T009 — `[P]` (разные файлы: LiveDoc и ESLint-вызов)
- T010 — после T009 (Prettier прогоняется после успешного ESLint)
- T011 — после T010 (build — финальная проверка)
- T012-T014 — после T011 (UI-проверки и sanity-check после успешной сборки)
- T015 — после T014 (commit — после подтверждения размера diff)
- T016 — после T015 (push + PR)
- T017 — после T016 (CI)
- T018 — после T017 (merge)

### Parallel Opportunities

- T005, T006 — `[P]` относительно T004 (разные сценарии проверки одного результата, можно гонять в разных вкладках браузера)
- T008 — `[P]` относительно T009 (LiveDoc и линтер — независимые файлы/команды)
- T011-T014 можно частично параллелить, если есть dev-окружение с разными браузерами

---

## Parallel Example: после Foundational

```bash
# После T002 (поле «Дата» готово) можно параллельно:
Task: "T004 — UI-проверка поля «Дата» (quickstart.md Сценарий 1)"
Task: "T005 — фильтрация при наборе (quickstart.md Сценарий 2)"
Task: "T006 — проверка непустого/мусорного значения (quickstart.md Сценарий 3)"

# После T003 (поле «Время» готово) можно параллельно с US1-проверками:
Task: "T007 — UI-проверка поля «Время» (quickstart.md Сценарий 4)"

# В Phase 5 параллельно (после T011):
Task: "T008 — обновить LiveDoc 156"
Task: "T009 — npm run lint"
Task: "T012 — кросс-браузерная проверка"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (T001 — `git checkout`)
2. Phase 2: Foundational (T002 — только поле «Дата»)
3. Phase 3: User Story 1 (T004-T006) — **STOP and VALIDATE**
4. Продемонстрировать пользователю: datalist в поле «Дата» работает
5. Если пользователь одобряет — продолжить с T003 (User Story 2)

### Incremental Delivery

1. Setup (T001)
2. Foundational (T002 — поле «Дата»)
3. User Story 1 (T004-T006) → подтверждено → показать пользователю
4. Foundational (T003 — поле «Время»)
5. User Story 2 (T007) → подтверждено
6. Polish (T008-T018) → LiveDoc, линтеры, build, PR, merge

### Один разработчик (типичный случай для этой фичи)

Поскольку обе User Story меняют один файл, и суммарный diff ≤25 строк,
параллельная работа нескольких людей не даёт выигрыша. Все задачи
выполняются последовательно одним человеком/агентом, T005/T006/T007 —
проверочные и могут идти в любом порядке после T002/T003.

---

## Notes

- **[P] tasks** = разные файлы или независимые проверки без пересечения
- **[Story] label** связывает задачу с конкретной User Story из `spec.md`; T002/T003 имеют [Story] хотя и в Foundational-фазе — потому что технически каждая правка закрывает одну историю, и так удобнее отслеживать, какой именно сценарий блокируется
- **T002-T003** — единственные задачи, меняющие production-код; T004-T007 — ручные проверки (в проекте нет CI-тестов для webvue3-UI, см. `plan.md` → Technical Context → Testing); T008 — обновление документации; T009-T018 — CI/Git-flow
- **Перед commit (T015)** — обязательный чек-лист из `AGENTS.md` (ESLint, Prettier, Vite build — T009, T010, T011)
- **Docker-образы НЕ пересобираются**: diff только во Vue-шаблоне, нет кросс-импортов между `webvue3` и `karaoke-public` (Pass 245 из AGENTS.md — Vite-build на хосте достаточен)
- **Избегать**: расширения scope (другие datalist-поля в `HomeView`/`SongsFilterModal`/etc — вне этой спеки, см. `research.md` → Вопрос 4); замены `<input>` на `<input type="date">` (меняет UX и формат данных, вне FR-006 спеки); добавления JS-обёртки вокруг `autocomplete` (избыточно для HTML-атрибутов, нарушает NFR-001 «дифф ≤25 строк»)
- **Если T009/T010/T011 выявят нарушения** — исправить точечно в `SongEdit.vue`, не задевая существующую разметку; перепрогнать весь цикл T009 → T010 → T011 до перехода к T012
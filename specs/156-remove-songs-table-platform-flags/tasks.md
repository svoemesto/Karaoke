---
description: "Task list для фичи 156 — удаление 18 столбцов-флагов публикации из таблицы «Песни» в admin SPA webvue3"
---

# Tasks: Удалить из таблицы «Песни» 18 столбцов-флагов публикации

**Input**: Design documents from `/specs/156-remove-songs-table-platform-flags/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/README.md ✅, quickstart.md ✅

**Tests**: Тесты НЕ запрашивались в feature specification. Проверка — ручная через [quickstart.md](./quickstart.md) (8 шагов).

**Organization**: Tasks сгруппированы по user stories для возможности независимой реализации и проверки.

## Format: `[ID] [P?] [Story] Описание`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- **File path**: абсолютный путь или относительный от корня репозитория `/home/nsa/Karaoke`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Создать feature-ветку и подготовить рабочее окружение. Поскольку фича маленькая и не требует новых модулей/зависимостей, Phase 1 сводится к одному шагу.

- [X] T001 Создать feature-ветку `156-remove-songs-table-platform-flags` от master и переключиться на неё: `cd /home/nsa/Karaoke && ./tools/reserve-branch-number.sh remove-songs-table-platform-flags` → `git checkout -b "${N}-remove-songs-table-platform-flags"` (если ещё не сделано). **Результат: ✅ зарезервирован 159 (скрипт), переименован в `156-remove-songs-table-platform-flags` для согласованности с `specs/156-...`.**

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Нет blocking prerequisites. Никаких новых зависимостей, миграций БД или API-контрактов — Phase 2 пустая.

**⚠️ CRITICAL**: Phase 2 пропускается (нет foundational-задач). User Stories можно начинать сразу после Phase 1.

---

## Phase 3: User Story 1 — Администратор видит компактную таблицу без 18 шумовых столбцов (Priority: P1) 🎯 MVP

**Goal**: Удалить из `SongsTable.vue` все следы 18 столбцов (определения, шаблоны, CSS, методы), чтобы в UI больше не отрисовывались SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM.

**Independent Test**: Открыть `http://localhost:5173/songs` — в шапке таблицы 18 `<th>` (без удалённых); ширина уменьшилась на ≈ 360px; `npm run build` и `npm run lint:check` проходят успешно.

### Implementation for User Story 1

**Все задачи в одном файле** — выполняются последовательно одним коммитом или пакетом.

- [X] T002 [US1] Удалить 18 объектов из массива `fields[]` (строки 1085-1308 файла `webvue3/src/components/Songs/SongsTable.vue`) для ключей: `flagSponsr`, `flagVk`, `flagDzenLyrics`, `flagDzenKaraoke`, `flagDzenChords`, `flagDzenMelody`, `flagVkLyrics`, `flagVkKaraoke`, `flagVkChords`, `flagVkMelody`, `flagTelegramLyrics`, `flagTelegramKaraoke`, `flagTelegramChords`, `flagTelegramMelody`, `flagMaxLyrics`, `flagMaxKaraoke`, `flagMaxChords`, `flagMaxMelody`. Шапка таблицы должна сохранить оставшиеся 18 столбцов в порядке: ID, Композиция, Исполнитель, Год, Альбом, №, Дата, Время, Tags, Status, V, BOO, Редактор, ▶ (player), ▶ (playerDemo), DE (flagPlayerDemo), TG (telegramPublish), FR (flagFree).

- [X] T003 [US1] Удалить 18 ячеек-шаблонов `<template #cell(flagX)="data">` (строки 325-541 файла `webvue3/src/components/Songs/SongsTable.vue`) для тех же 18 ключей, что и в T002. **НЕ трогать** `<template #cell(flagPlayerDemo)>`, `<template #cell(telegramPublish)>`, `<template #cell(flagFree)>` — они остаются. Шаблон `<!-- ... flagPl* ... -->` (закомментированные) оставить как был.

- [X] T004 [US1] Удалить 18 CSS-блоков `.fld-flag-*` (строки 2458-2570 файла `webvue3/src/components/Songs/SongsTable.vue`) для классов: `.fld-flag-sponsr`, `.fld-flag-vk`, `.fld-flag-dzen-lyrics`, `.fld-flag-dzen-karaoke`, `.fld-flag-dzen-chords`, `.fld-flag-dzen-melody`, `.fld-flag-vk-lyrics`, `.fld-flag-vk-karaoke`, `.fld-flag-vk-chords`, `.fld-flag-vk-melody`, `.fld-flag-tg-lyrics`, `.fld-flag-tg-karaoke`, `.fld-flag-tg-chords`, `.fld-flag-tg-melody`, `.fld-flag-max-lyrics`, `.fld-flag-max-karaoke`, `.fld-flag-max-chords`, `.fld-flag-max-melody`. **НЕ трогать** `.fld-flag-player-demo` и `.fld-flag-free` — они остаются. **НЕ трогать** закомментированные `.fld-flag-pl-*` (если есть).

- [X] T005 [US1] Удалить 4 метода `playLyrics(id)`, `playKaraoke(id)`, `playChords(id)`, `playTabs(id)` (строки 2154-2168 файла `webvue3/src/components/Songs/SongsTable.vue`). **НЕ трогать** метод `playDemo(id)` — он используется ячейкой `flagPlayerDemo` (DE), которая не удаляется. Vuex-геттеры `this.$store.getters.playLyrics`/etc. остаются без изменений — они используются в `Songs/edit/SongEdit.vue` и `Publish/components/PublishTableBodyTd.vue` (FR-007).

**Checkpoint**: после T002-T005 в `SongsTable.vue` не должно остаться ни одного упоминания удалённых `flagSponsr`/`flagVk`/`flagDzen*`/`flagVk*`/`flagTelegram*`/`flagMax*`. Проверка: `grep -nE "flagSponsr|flagDzenLyrics|flagDzenKaraoke|flagDzenChords|flagDzenMelody|flagVkLyrics|flagVkKaraoke|flagVkChords|flagVkMelody|flagTelegramLyrics|flagTelegramKaraoke|flagTelegramChords|flagTelegramMelody|flagMaxLyrics|flagMaxKaraoke|flagMaxChords|flagMaxMelody" webvue3/src/components/Songs/SongsTable.vue` — должно вернуть 0 совпадений.

---

## Phase 4: User Story 2 — Фильтры больше не показывают удалённые поля (Priority: P2)

**Goal**: Удалить 10 определений из `state.fieldSongParams[]` Vuex-store, чтобы массив соответствовал видимым полям таблицы. Геттер `getFieldSongParams` остаётся (на случай будущих потребителей), но возвращает укороченный массив.

**Independent Test**: Открыть `http://localhost:5173/songs`, открыть devtools → Vue → `$store.state.songs.fieldSongParams` — массив содержит только 12 элементов (без удалённых 10). Если в админке есть модальное окно фильтров — открыть, убедиться, что удалённые поля в нём не предлагаются.

### Implementation for User Story 2

**Файл отличается от US1 — можно выполнять параллельно с US1 (разработчик А делает SongsTable.vue, разработчик Б делает store.js), или в одном PR одним коммитом.**

- [X] T006 [P] [US2] Удалить 10 объектов из массива `state.fieldSongParams[]` (строки 230-358 файла `webvue3/src/components/Songs/store.js`) для имён: `flagSponsr`, `flagVk`, `flagDzenLyrics`, `flagDzenKaraoke`, `flagVkLyrics`, `flagVkKaraoke`, `flagTelegramLyrics`, `flagTelegramKaraoke`, `flagMaxLyrics`, `flagMaxKaraoke`. Для 8 остальных (`flagDzenChords/Melody`, `flagVkChords/Melody`, `flagTelegramChords/Melody`, `flagMaxChords/Melody`) определения в `fieldSongParams[]` уже отсутствуют — удалять нечего. Геттер `getFieldSongParams` (строка 487-490) остаётся без изменений.

**Checkpoint**: после T002-T006 во всех трёх местах (`fields[]` в SongsTable.vue, `<template #cell>` шаблоны, `fieldSongParams[]` в store.js) удалено всё, что относится к 18 столбцам.

---

## Phase 5: User Story 3 — Сборка webvue3 проходит без ошибок и предупреждений (Priority: P3)

**Goal**: Запустить `npm run build` и `npm run lint:check` в `webvue3/`, убедиться что:
- Сборка успешна (exit 0)
- Размер JS-чанка уменьшился на ≥ 1KB
- ESLint не выдаёт новых ошибок (baseline не увеличивается)

**Independent Test**: Выполнить `cd /home/nsa/Karaoke/webvue3 && npm run build && npm run lint:check` — обе команды завершаются с кодом 0.

### Implementation for User Story 3

- [X] T007 [US3] Запустить `cd /home/nsa/Karaoke/webvue3 && npm run build` — убедиться, что сборка успешна (exit 0), размер бандла уменьшился. Если сборка падает — исправить ошибки (частая причина: забыли удалить ссылку на удалённый CSS-класс где-то ещё, или ESLint `no-unused-vars` ругается на удалённые методы). **Результат: ✅ exit 0, 471 модулей, 7.55s, dist/assets/index-*.css = 414.04 kB, dist/assets/index-*.js = 2179.80 kB.**

- [X] T008 [US3] Запустить `cd /home/nsa/Karaoke/webvue3 && npm run lint:check` — убедиться, что нет новых ошибок. Если есть — исправить. Допускаются только те предупреждения, что уже есть в `webvue3/.eslint-baseline.json` (baseline не должен расти). **Результат: ✅ exit 0, 0 предупреждений, 0 ошибок.**

**Checkpoint**: после T007-T008 фича готова к code review и PR.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, changelog, ручная проверка end-to-end. Все три задачи в разных файлах — можно параллелить.

- [X] T009 [US1] Обновить per-feature документ `/home/nsa/Karaoke/docs/features/songs-table.md`: удалить из него перечисление/описание 18 удалённых столбцов или явно отметить их как удалённые (пометка «Удалено в #NNN, 2026-08-06»). Это обязательно per FR-009 Конституции (Code Standards). Проверка: `bash /home/nsa/Karaoke/tools/check-feature-doc.sh /home/nsa/Karaoke/docs/features/*.md` (если существует) должен пройти. **Результат: ✅ добавлен блок «Состав колонок (после фичи #156, 2026-08-06)» с перечислением 18 видимых колонок и явным списком удалённых 18.**

- [X] T010 [P] [US1] Добавить запись в `/home/nsa/Karaoke/docs/architecture-notes.md` о PR с фичей: дата 2026-08-06, ссылка на PR, краткое описание (1-2 предложения: «Удалены 18 столбцов-флагов публикации из таблицы песен в admin SPA webvue3, FR-008/FR-009 docs обновлены»). Это changelog-практика проекта для отслеживания архитектурных решений по датам. **Результат: ✅ добавлена запись `### 2026-08-06 — PR #NNN: 156-remove-songs-table-platform-flags` с разделами Что/Зачем/Альтернативы/Уроки/Ссылки.**

- [X] T011 [P] Выполнить ручную проверку по `/home/nsa/Karaoke/specs/156-remove-songs-table-platform-flags/quickstart.md` (8 шагов). Все 8 шагов должны пройти успешно. Если что-то падает — вернуться к соответствующей задаче (T002-T008) и исправить. **Результат: ✅ автоматизированные шаги прошли — Шаг 4 (grep) = 0 совпадений в SongsTable.vue и store.js; Шаг 5 (build + lint) = exit 0; Шаг 8 (docs) = grep нашёл упоминания фичи 156 в обоих документах. Шаги 1-3 и 6-7 (визуальный осмотр в браузере, регресс /publications и /songs/:id/edit) — для пользователя.**

---

## Phase 7: Code Review & Merge (NON-NEGOTIABLE per AGENTS.md)

**Purpose**: Создать PR, прогнать CI 7/7, смержить в master. По AGENTS.md «CI-gate для master» — прямые пуши в master ЗАПРЕЩЕНЫ.

- [ ] T012 Коммит: `cd /home/nsa/Karaoke && git add webvue3/src/components/Songs/SongsTable.vue webvue3/src/components/Songs/store.js && git commit -m "webvue3(songs): удалить 18 столбцов-флагов публикации из таблицы песен"` (commit message на русском, в стиле `area: краткое описание` per constitution § «Рабочий процесс»). **НЕ выполнено — оставлено пользователю (per AGENTS.md «NEVER commit without explicit request»).**

- [ ] T013 Коммит: `cd /home/nsa/Karaoke && git add docs/features/songs-table.md docs/architecture-notes.md && git commit -m "docs(songs-table): обновить per-feature документ и changelog для фичи 156"` (отдельный коммит для документации, чтобы в git log было видно разделение код/документация). **НЕ выполнено — оставлено пользователю.**

- [ ] T014 Push feature-ветки: `cd /home/nsa/Karaoke && git push -u origin 156-remove-songs-table-platform-flags`. **НЕ выполнено — оставлено пользователю.**

- [ ] T015 Создать PR: `cd /home/nsa/Karaoke && gh pr create --base master --title "webvue3(songs): удалить 18 столбцов-флагов публикации из таблицы песен (#156)" --body "..."` — body должно содержать ссылку на specs/156-remove-songs-table-platform-flags/spec.md, список изменённых файлов, и инструкцию для ревьюера «пройти 8 шагов quickstart.md». **НЕ выполнено — оставлено пользователю.**

- [ ] T016 Дождаться CI 7/7 SUCCESS: `gh pr checks <N>` или `gh run watch`. Если падает — исправить и push --amend / новый коммит. Дождаться зелёного CI обязательно (NON-NEGOTIABLE per AGENTS.md). **НЕ выполнено — оставлено пользователю.**

- [ ] T017 Merge PR: `gh pr merge <N> --merge` (БЕЗ `--delete-branch` per AGENTS.md «Жизненный цикл feature-ветки» — ветка остаётся жить для возможных правок). **НЕ выполнено — оставлено пользователю.**

- [ ] T018 Проверить post-merge: `git log -1 master` должен показать merge-коммит (`Merge pull request #NNN from svoemesto/156-remove-songs-table-platform-flags`), а НЕ одиночный коммит напрямую в master (self-check per AGENTS.md). **НЕ выполнено — оставлено пользователю.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей, можно начать сразу.
- **Phase 2 (Foundational)**: пропущена (нет задач).
- **Phase 3 (US1)**: зависит от Phase 1 (T001 — feature-ветка).
- **Phase 4 (US2)**: зависит от Phase 1 (T001) — можно параллельно с US1.
- **Phase 5 (US3)**: зависит от Phase 3 (T002-T005) и Phase 4 (T006) — сборка поверх их изменений.
- **Phase 6 (Polish)**: зависит от Phase 5 (T007-T008) — документация поверх зелёной сборки.
- **Phase 7 (Review & Merge)**: зависит от Phase 6 (T009-T011) — PR поверх полностью готовой фичи.

### User Story Dependencies

- **US1 (P1)**: можно начать после T001 — нет зависимостей от других историй. Это **MVP**.
- **US2 (P2)**: можно начать после T001 — параллельно с US1 (разные файлы).
- **US3 (P3)**: зависит от US1+US2 (сборка поверх их изменений).
- **Polish (Phase 6)**: зависит от всех US.

### Within Each User Story

- T002 → T003 → T004 → T005 (все в одном файле `SongsTable.vue`, последовательно).
- T006 (отдельный файл `store.js`) — независимо от T002-T005.
- T007 → T008 (последовательно: build перед lint, или наоборот).
- T009, T010, T011 — параллельно (разные файлы, разные виды работ).
- T012 → T013 → T014 → T015 → T016 → T017 → T018 (строго последовательно).

---

## Parallel Opportunities

### Параллелизация Phase 3 (US1) и Phase 4 (US2)

Поскольку US1 (правки `SongsTable.vue`) и US2 (правки `store.js`) затрагивают **разные файлы**, эти работы можно делать параллельно:

```bash
# Разработчик A — US1 (SongsTable.vue):
cd /home/nsa/Karaoke
$EDITOR webvue3/src/components/Songs/SongsTable.vue   # T002-T005

# Разработчик B (или тот же разработчик в другом терминале) — US2 (store.js):
cd /home/nsa/Karaoke
$EDITOR webvue3/src/components/Songs/store.js          # T006
```

### Параллелизация Phase 6 (Polish)

T009, T010, T011 можно делать параллельно (документация, changelog, ручная проверка — разные файлы и виды работ):

```bash
# T009 — обновить per-feature документ:
$EDITOR /home/nsa/Karaoke/docs/features/songs-table.md

# T010 (параллельно) — changelog:
$EDITOR /home/nsa/Karaoke/docs/architecture-notes.md

# T011 (параллельно или после T007-T008) — открыть в браузере:
xdg-open http://localhost:5173/songs
```

---

## Implementation Strategy

### MVP First (только User Story 1 + 2 + 3)

Эта фича — маленькая, поэтому MVP = полная фича (US1+US2+US3). Нет смысла релизить «только таблицу без столбцов» без фильтров и сборки — это одно логическое изменение.

1. **T001**: создать feature-ветку.
2. **T002-T006**: основной код (можно параллельно: US1 в `SongsTable.vue`, US2 в `store.js`).
3. **T007-T008**: сборка + lint (проверка что ничего не сломалось).
4. **T009-T011**: документация + changelog + ручная проверка.
5. **T012-T018**: PR → CI 7/7 → merge.

### Альтернатива: всё одним коммитом

Поскольку фича очень маленькая (~430 строк diff, 99% — удаление), все T002-T011 можно сделать одним коммитом `T012`, минуя T013 (отдельный коммит для документации). Но лучше разделить на 2-3 коммита для чистоты истории:

1. **Код**: T002-T008 в одном коммите (build + lint проверены).
2. **Документация**: T009-T010 в отдельном коммите (логически отделено от кода).
3. **PR + merge**: T014-T018.

---

## Notes

- [P] tasks = разные файлы, нет зависимостей.
- [Story] label привязывает задачу к user story для трассировки.
- Каждая user story независимо завершаема и проверяема (но в этой фиче они объединены в один PR — слишком маленькие для отдельных релизов).
- Тестов нет — проверка ручная через [quickstart.md](./quickstart.md).
- Коммиты после каждой задачи или логической группы (T002-T005 одним коммитом, T006 отдельно, T009-T010 отдельно).
- Останавливаться на любом checkpoint для валидации.
- **Избегать**: расплывчатых задач, конфликтов в одном файле, кросс-story зависимостей.

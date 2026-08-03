# Tasks: Доступность плеера в таблице «Песни» при статусе ≥4

**Input**: Design documents from `/specs/125-player-status-gate/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Не запрошены явно и не требуются — в проекте нет CI/юнит-тестов для `webvue3` (см. constitution.md «Рабочий процесс» → «Тесты»); проверка выполняется вручную по `quickstart.md`.

**Organization**: Одна пользовательская история (US1, P1) — фича предельно мала (одно условие в одном компоненте), Setup/Foundational фазы не нужны (новых зависимостей, схем, инфраструктуры не требуется).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1)
- Include exact file paths in descriptions

---

## Phase 1: Setup

Не требуется — новых зависимостей/каркаса не добавляется, существующий `webvue3`-проект уже настроен.

## Phase 2: Foundational

Не требуется — нет общей для будущих историй инфраструктуры (история одна, изменение точечное).

---

## Phase 3: User Story 1 - Плеер доступен с MARKERS_CREATED (Priority: P1) 🎯 MVP

**Goal**: В таблице «Песни» иконка основного плеера становится активной начиная со статуса 4 (MARKERS_CREATED), а не 6 (READY); DEMO-плеер и кнопка «Открыть плеер» в SongEdit не меняются.

**Independent Test**: Открыть таблицу «Песни», убедиться что песня со статусом 4 показывает активную иконку плеера и открывает `/player/{id}` по клику; песня со статусом 3 — по-прежнему неактивную; DEMO-плеер и SongEdit не затронуты (см. `quickstart.md`).

### Implementation for User Story 1

- [X] T001 [US1] В `webvue3/src/components/Songs/SongsTable.vue` в блоке `#cell(player)` (~строки 236-267) заменить условие `data.item.idStatus >= 6` в `v-if` на `data.item.idStatus >= 4`, и текст `title` неактивной иконки (`v-else`) с «Плеер недоступен (статус < 6)» на «Плеер недоступен (статус < 4)». Блок `#cell(playerDemo)` (~строки 270-303) НЕ трогать — он должен остаться на `idStatus >= 6`.

### Verification for User Story 1

- [ ] T002 [US1] Пройти `quickstart.md` шаги 1-4: проверить в браузере, что иконка `player` неактивна на статусе 3, активна и кликабельна (открывает `/player/{id}`) на статусах 4 и 6.
- [ ] T003 [US1] Пройти `quickstart.md` шаги 5-6: убедиться, что колонка `playerDemo` для песни со статусом 4-5 осталась неактивной, а кнопка «▶ Открыть плеер» в SongEdit по-прежнему кликабельна на любом статусе (регрессий в незатронутых компонентах нет).

**Checkpoint**: User Story 1 полностью функциональна и проверена независимо — MVP фичи готов.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [X] T004 [P] Обновить `docs/features/songs-table.md` — зафиксировать новый порог доступности плеера (статус ≥4 вместо ≥6) в описании подсистемы (FR-009 constitution.md/spec.md `001-code-standards-docs`).
- [X] T005 Прогнать обязательные проверки перед коммитом: `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`, `bash tools/check-jsdoc-coverage.sh webvue3`, `pre-commit run --all-files`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup / Foundational**: отсутствуют, старт сразу с Phase 3.
- **User Story 1 (Phase 3)**: T001 → T002/T003 (проверка после реализации; T002 и T003 можно проводить в любом порядке/параллельно вручную, но оба зависят от T001).
- **Polish (Phase 4)**: T004 может выполняться параллельно с T001-T003 (другой файл); T005 — последним, после того как код и документация готовы.

### Parallel Opportunities

- T004 (обновление `docs/features/songs-table.md`) — [P], не пересекается по файлу с T001.
- T002 и T003 затрагивают один и тот же экран, но разные строки/компоненты таблицы — можно проверить за один проход вручную.

---

## Parallel Example: User Story 1

```bash
# T001 (SongsTable.vue) и T004 (songs-table.md) можно делать параллельно — разные файлы:
Task: "Снизить порог idStatus в #cell(player) SongsTable.vue с >=6 на >=4"
Task: "Обновить docs/features/songs-table.md с новым порогом доступности плеера"
```

---

## Implementation Strategy

### MVP First (и единственный скоуп)

1. T001 — точечное изменение условия и текста в `SongsTable.vue`.
2. T002, T003 — ручная проверка по `quickstart.md` (позитив + regression-check незатронутых компонентов).
3. T004 — обновление per-feature документа (обязательно по FR-009, т.к. `songs-table` входит в 12+ ключевых подсистем `docs/features/README.md`).
4. T005 — линтеры/pre-commit перед коммитом.

Инкрементальная поставка не требуется — вся фича умещается в один PR/коммит.

---

## Notes

- [P] tasks = разные файлы, независимы
- Фича не создаёт новых сущностей/эндпоинтов/тестовых файлов — Setup, Foundational и тестовые задачи из шаблона намеренно опущены как неприменимые.
- Commit — один логический коммит после T001-T005 (или по договорённости с пользователем).

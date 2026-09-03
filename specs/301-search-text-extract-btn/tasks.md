---
description: "Task list for feature: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки"
---

# Tasks: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

**Input**: Design documents from `/specs/301-search-text-extract-btn/`
- [plan.md](plan.md) (required) — технический подход
- [spec.md](spec.md) (required) — User Stories с приоритетами
- [research.md](research.md) — корневые причины и decisions
- [data-model.md](data-model.md) — client state + state transitions
- [contracts/README.md](contracts/README.md) — reference backend-эндпоинта + UI contract
- [quickstart.md](quickstart.md) — 7 ручных validation scenarios

**Source**: OpenProject #51 — «Кнопка "Получить текст по ссылке"»
**Branch**: `301-search-text-extract-btn`
**Tests**: Опциональны (в проекте нет автотестов для `webvue3`); финальная проверка — пользователем вручную по `quickstart.md` (Constitution § «Рабочий процесс» → «Тесты»).

**Organization**: Минимальный объём фикса (3 строки в 1 файле). Tasks сгруппированы по user story (P1 → P1 → P2) с phase блоком для per-feature документа (Foundational) и финальным Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- В описании — точные пути файлов

---

## Phase 1: Setup (Shared Infrastructure)

**Цель**: проверить, что dev-окружение в порядке и фикс-ветка готова к правкам кода.

- [x] T001 Подтвердить ветку `301-search-text-extract-btn` и чистоту `git status` (нет незакоммиченных изменений из других задач)
- [x] T002 [P] Убедиться, что `node_modules` в `webvue3/` установлен (`ls webvue3/node_modules >/dev/null 2>&1 || npm ci`); baseline `npm run lint:check` отрабатывает успешно

**Checkpoint**: окружение готово, можно приступать к правкам кода.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: подготовить per-feature документ (Constitution FR-009). Без него нельзя коммитить — нужен `@see` для JSDoc.

**⚠️ CRITICAL**: Без завершения Phase 2 нельзя переходить к Phase 3 — в JSDoc правок нужен путь к per-feature документу.

- [x] T003 Создать per-feature документ `docs/features/search-text-extract-btn.md` (Constitution FR-009). Структура: (1) **Bug description** — ссылка на OpenProject #51, симптом «приходится закрывать модалку», корневая причина (`v-text` в `<textarea` игнорируется в Vue2); (2) **Корневые причины** — `<textarea v-text="resultText" />` → `v-text` устанавливает `textContent`, не `.value`; `<textarea />` — невалидный самозакрытый тег; (3) **Fix template** — фрагмент кода `:value="resultText"` с комментариями и `@see` на этот документ (Constitution FR-006); (4) **CSS-fix** — добавление `display: block` в `.group-button` для гарантии столбика; (5) **Why other components are correct** — `extractLyricsFromSelectedResult` и `SearchTextResultsTable.vue` не требуют правок; (6) **Future work** — AbortController для race-condition, автоматическое обновление `currentId`; (7) **See also** — ссылки на `specs/301-search-text-extract-btn/{spec,plan,research,data-model,quickstart,contracts}.md` и OpenProject #51.

**Checkpoint**: per-feature документ создан, можно ссылаться на него из JSDoc.

---

## Phase 3: User Story 1 — Фикс textarea в модалке (Priority: P1) 🎯 MVP

**Goal**: исправить баг OP#51 — после нажатия «Получить текст по ссылке» textarea справа показывает полученный текст, и соответствующий пункт в списке перестаёт быть серым — **без закрытия модалки**.

**Independent Test**: на dev-машине с заполненной БД открыть `SongEdit.vue` для песни → нажать «Поиск текста в интернете» → дождаться результатов → выбрать «серый» результат → нажать «Получить текст по ссылке» → через 3-15 сек textarea справа содержит полученный текст (главный критерий!), пункт в списке перестал быть серым, модалка остаётся открытой.

### Implementation for User Story 1

- [x] T004 [US1] Заменить `<textarea class="result-text" v-text="resultText" />` на `<textarea class="result-text" :value="resultText"></textarea>` в `webvue3/src/components/Songs/edit/SearchText.vue:36`. Добавить комментарий-обоснование (3-5 строк перед `<textarea>`): «v-text в Vue2 устанавливает textContent, который игнорируется для `<textarea>` (textarea хранит значение в `.value`). Заменено на `:value` для реактивного обновления `.value`. Также исправлен самозакрытый тег `<textarea />` → `<textarea></textarea>` для валидности HTML5. @see docs/features/search-text-extract-btn.md (OP#51)». Коммит: `fix(search-text): textarea реактивно обновляется после извлечения текста (refs OP#51)`.

**Checkpoint**: фикс template применён. Можно вручную проверить Scenario 1 из `quickstart.md` после сборки.

---

## Phase 4: User Story 2 — Фикс расположения кнопки (Priority: P1)

**Goal**: гарантировать, что кнопка «Получить текст по ссылке» визуально расположена столбиком под кнопкой «Открыть на сайте» в одном контейнере.

**Independent Test**: открыть модалку, выбрать «серый» результат → обе кнопки видны в правом столбце, столбиком, одинаковой ширины.

### Implementation for User Story 2

- [x] T005 [US2] Добавить `display: block;` в `.group-button` CSS-правило в `webvue3/src/components/Songs/edit/SearchText.vue:500-505`. Добавить комментарий-обоснование (1-2 строки перед `display: block`): «Гарантирует вертикальное расположение кнопок в `.st-body-column-2` (одна под другой). @see docs/features/search-text-extract-btn.md (FR-001, OP#51)». Коммит: `fix(search-text): кнопки в правом столбце модалки гарантированно столбиком (refs OP#51)`.

**Checkpoint**: фикс CSS применён. Можно проверить Scenario 2 из `quickstart.md`. Регрессию в `.st-footer` проверить по Scenario 4.

---

## Phase 5: User Story 3 — Корректная работа UI-контролей (Priority: P2)

**Goal**: убедиться, что после применения фикса не сломано: скрытие кнопки при `canExtractLyrics === false`, disabled во время запроса, alert при ошибке, кнопки в footer, открытие ссылки в новой вкладке.

**Independent Test**: выполнить Scenarios 3-7 из `quickstart.md` на dev-машине.

### Validation for User Story 3

- [ ] T006 [P] [US3] Выполнить ручную проверку Scenarios 3 (скрытие кнопки при уже полученном тексте), 4 (кнопки в footer — регрессия), 5 (открытие ссылки), 6 (disabled во время запроса), 7 (alert при ошибке) на dev-машине. Зафиксировать результат в комментарии к PR (если есть проблемы — зафиксировать как «known limitations» или откатить отдельные коммиты).

**Checkpoint**: все регрессионные сценарии проверены. Если что-то сломано — вернуться в Phase 3/4 и дофиксить (например, уточнить CSS-селектор `.st-footer .group-button` если `.group-button display: block` сломал расположение в footer).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Цель**: привести код к CI-требованиям (7/7 PASS), подготовить PR, закрыть задачу.

- [x] T007 [P] Запустить линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint:check` + `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"`. Все три — PASS. Если FAIL — исправить (только в `webvue3/src/components/Songs/edit/SearchText.vue`).
- [x] T008 [P] Обновить `docs/architecture-notes.md` (Pass 14+) — краткая запись: «Pass 301: исправлен баг #51 (OP) — `<textarea v-text>` → `<textarea :value>` в `SearchText.vue` для реактивного обновления после `extractLyricsBySearchResultId`. Также добавлен `display: block` в `.group-button` для гарантии столбика. Backend не менялся».
- [x] T009 [P] Обновить `livedocs/features/` — создать `livedocs/features/301-search-text-extract-btn.md` с frontmatter `status: Active, slug: 301-search-text-extract-btn`, related на `specs/301-search-text-extract-btn/spec.md`, `plan.md`, `audit.md` (нет audit, ссылка только на спеку/план) и `docs/features/search-text-extract-btn.md`. Краткое описание (50-100 строк): что делает, где в в (SearchText.vue:36 + CSS), почему другой правильный паттерн (News в спеке 300 использовал watcher на countRows, здесь — фикс v-text). Это требование Constitution FR-014 + LiveDocs structure check в CI (блокирующий).
- [x] T010 Пересобрать `webvue3`: `cd webvue3 && npm run build && npm run format:check`. Убедиться, что `dist/` обновился и в нём присутствует исправленный SearchText.vue (через `grep -l 'value="resultText"' webvue3/dist/assets/*.js` или через поиск строки `:value=resultText` в minified bundle).
- [x] T011 Пересобрать Docker-образ: `cd deploy && bash do.sh build_webvue3`. Убедиться, что образ успешно собран и содержит исправленный SearchText.vue (через `docker run --rm svoemestodev/karaoke-webvue3:1 sh -c 'grep -l "resultText" /app/assets/*.js'`). Согласно AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода», шаги 4 (Vite) и 5 (Docker) обязательны.
- [ ] T012 Выполнить полный smoke-чек по `quickstart.md`: пройти все 7 сценариев на dev-машине. Зафиксировать результат в комментарии к PR.
- [ ] T013 Подготовить PR: `git push -u origin 301-search-text-extract-btn && gh pr create --base master`. В описании: (а) ссылка на OpenProject #51, (б) ссылка на `specs/301-search-text-extract-btn/spec.md`, (в) список изменённых файлов (1 `SearchText.vue` + 1 `pagination-filter-admin-tables.md` (старый) + правки в `architecture-notes.md` и `livedocs/features/`), (г) чеклист «CI 7/7 PASS», (д) «Manual validation: все 7 сценариев из `quickstart.md` пройдены».
- [ ] T014 Дождаться `gh pr checks` (CI 7/7) и одобрения пользователем. После approval — `gh pr merge --merge` (БЕЗ `--delete-branch`). После мёрджа — закрыть OpenProject #51 (`./tools/tracker.sh mark-review 51` затем `./tools/tracker.sh close-issue 51`).

**Checkpoint**: PR создан, CI зелёный, manual validation пройдена, PR смёржен, OpenProject #51 закрыт.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Setup (T1-T2). **Блокирует** User Stories (T004-T006).
- **User Stories (Phase 3-5)**: зависят от Foundational (T003) → могут идти параллельно или последовательно в порядке приоритета (P1 → P1 → P2).
- **Polish (Phase 6)**: зависит от завершения всех user stories.

### User Story Dependencies

- **User Story 1 (P1)**: после Foundational → независим. Главный MVP-фикс (textarea).
- **User Story 2 (P1)**: после Foundational → независим от US1 (тот же файл, но другая область — CSS). Можно делать в одном коммите с US1 (файл один).
- **User Story 3 (P2)**: после завершения US1 и US2 (валидирует их результат).

### Within Each Phase

- Phase 1: T002 параллельно с T001.
- Phase 3: T004 (одна задача).
- Phase 4: T005 (одна задача). Можно объединить с T004 в один коммит (тот же файл) — решает агент по обстоятельствам.
- Phase 5: T006 (одна задача, ручная валидация).
- Phase 6: T007, T008, T009 параллельно. T010 — последовательно (зависит от T007-T009). T011 — последовательно (зависит от T010). T012 — последовательно (зависит от T011). T013 — последовательно (зависит от T012). T014 — последовательно (зависит от T013).

### Parallel Opportunities

- **Phase 1**: T002 параллельно с T001 (T001 — проверка ветки, T002 — npm).
- **Phase 6**: T007, T008, T009 параллельно (линтерты, docs, LiveDoc — разные файлы).

---

## Parallel Example: Phase 6 Polish

```bash
# Параллельно:
Task: "Запустить линтеры (ktlintCheck + npm run lint:check + prettier --check)"
Task: "Обновить docs/architecture-notes.md"
Task: "Создать livedocs/features/301-search-text-extract-btn.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Завершить Phase 1: Setup.
2. Завершить Phase 2: Foundational.
3. Завершить Phase 3: User Story 1 (фикс textarea).
4. **STOP and VALIDATE**: Scenario 1 из `quickstart.md` вручную на dev-машине.
5. Если MVP нужен немедленно — можно сделать PR только с Phase 3 + минимальным Phase 6 (T007, T010, T011, T013, T014). Phase 4 (CSS) и Phase 5 (регрессия) — следующим коммитом.

### Incremental Delivery (рекомендуется)

1. Setup + Foundational → готов фундамент.
2. User Story 1 → тест → demo (MVP — закрывает основную часть OP#51: textarea обновляется).
3. User Story 2 → тест → demo (закрывает вторую часть OP#51: кнопка под кнопкой).
4. User Story 3 → тест → demo (валидация регрессий).
5. Каждая стадия добавляет ценность, не ломая предыдущие.

**Рекомендация**: T004 (US1) и T005 (US2) — в **одном коммите** (один файл, один логический блок изменений), затем T006 (US3 валидация), затем Phase 6.

### Parallel Team Strategy

С одним разработчиком (текущий случай) — последовательно. Если несколько разработчиков:
1. Один делает Phase 1 + 2 + 3 + 4 + 6.
2. Второй параллельно делает Phase 5 (ручная валидация) после T004+T005.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] лейбл связывает задачу с user story для traceability.
- Каждая user story должна быть независимо завершаемой и тестируемой.
- Коммит после каждой задачи или логической группы (T004+T005 — можно одним коммитом в один файл, решает агент).
- Останавливаться на любом checkpoint для валидации story независимо.
- Избегать: расплывчатых задач, конфликтов на одном файле (T004 и T005 оба в SearchText.vue — последовательно).
- Согласно AGENTS.md: **не коммитить без явного запроса пользователя** — коммит/PR ожидает явной инструкции.
- Согласно AGENTS.md: перед каждым коммитом — линтеры (`./gradlew :karaoke-web:ktlintCheck` + `webvue3/npm run lint:check` + `webvue3/npx prettier --check`).
- Согласно AGENTS.md: после ЛЮБОГО изменения кода — обязательная 5-шаговая проверка (для этой задачи: lint + Vite + Docker; backend не менялся → compile/bootJar не нужны).
- В LiveDocs-файле (T009) обязательно указать related на `docs/features/search-text-extract-btn.md` — иначе CI LiveDocs structure check упадёт.
---
description: "Task list for 263 — улучшение блоков «Текст пользователя», «Разметка» и «Маркеры» в модалке проверки задания"
---

# Tasks: 263 — Улучшение модалки проверки задания редактора

**Input**: Design documents from `/specs/263-editor-task-review-modal/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: В CI нет (см. AGENTS.md, Constitution § «Тесты», plan.md «Testing»). Smoke-п-проверки выполняются через `npm run lint`, `npm run build`, `npm run format:check`. Тесты НЕ генерируются.

**Organization**: Tasks grouped by user story для независимой реализации и тестирования. Все 4 US имеют приоритет P1 (явно запрошены пользователем в одном сообщении, разделение на P1/P2 не имеет смысла — MVP = все 4 US).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app** (admin SPA): `webvue3/src/...` (Vite-проект `webvue3`)
- **LiveDoc**: `livedocs/features/263-editor-task-review-modal.md`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline smoke-checks ДО любых правок. Зафиксировать состояние baseline ESLint/prettier/build, чтобы потом видеть «новые» нарушения.

- [x] T001 Проверить baseline ESLint: `cat /home/nsa/Karaoke/webvue3/.eslint-baseline.json` (ожидаемый вывод: `[]`). Если есть нарушения — стоп, обсудить с пользователем.
- [x] T002 Проверить baseline prettier: `cd /home/nsa/Karaoke/webvue3 && npm run format:check` (ожидаемый вывод: `All matched files use Prettier code style!`). Если есть нарушения — стоп, обсудить.
- [x] T003 Проверить baseline Vite-сборку: `cd /home/nsa/Karaoke/webvue3 && npm run build` (ожидаемый вывод: `✓ built in ...`). Если есть ошибки — стоп.

**Checkpoint**: baseline зафиксирован, можно начинать правки.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общие для ВСЕХ user stories изменения — фундамент компонента (новые data-поля, computed, импорты, JSDoc). Без этого US1/US2/US3/US4 не могут стартовать.

**⚠️ CRITICAL**: Все user stories зависят от этой фазы.

- [x] T004 Добавить импорт `formatText` и `loadEditorSettings` в `<script>` `webvue3/src/components/SongEditor/ReviewModal.vue` (рядом с существующим блоком импортов, после строки 144 — `import { formatText, loadEditorSettings } from '../../../../karaoke-public/src/composables/useKaraokeEditor'`).
- [x] T005 Добавить data-поля `textFontSize` и `previewFontSize` в `data()` компонента `webvue3/src/components/SongEditor/ReviewModal.vue` (со значениями `EDITOR_DEFAULTS.textFontSize = 16` и `EDITOR_DEFAULTS.previewFontSize = 18` как fallback для SSR/первого рендера до `mounted()`).
- [x] T006 Добавить computed `parsedMarkupHtml` в `computed:` блок `webvue3/src/components/SongEditor/ReviewModal.vue` (после `parsedMarkers`): `parsedMarkupHtml() { return formatText(this.parsedMarkers, -1) }`. С JSDoc-комментарием с `@see` на `useKaraokeEditor.js:447` (Constitution Principle VI, FR-006 Constitution).
- [x] T007 Добавить вызов `loadEditorSettings()` в `mounted()` `webvue3/src/components/SongEditor/ReviewModal.vue`: результат пишется в `this.textFontSize` / `this.previewFontSize`. Защита try/catch от возможных ошибок localStorage (FR-010).

**Checkpoint**: Фундамент готов — все US могут стартовать параллельно.

---

## Phase 3: User Story 1 — Текст пользователя выравнивается по левому краю (Priority: P1) 🎯

**Goal**: Блок «Текст пользователя» имеет явное выравнивание по левому краю, независимо от контекста модалки.

**Independent Test**: Открыть задание с длинным текстом → `getComputedStyle('.se-text').textAlign === 'left'` при любой ширине окна.

### Implementation for User Story 1

- [x] T008 [US1] Добавить `text-align: left;` в CSS-правило `.se-text` в `<style scoped>` блоке `webvue3/src/components/SongEditor/ReviewModal.vue` (строка ~526, FR-001).

**Checkpoint**: US1 done. Блок «Текст пользователя» выровнен по левому краю во всех 6 точках входа модалки.

---

## Phase 4: User Story 2 — Добавлен блок «Разметка» в формате онлайн-редактора (Priority: P1) 🎯

**Goal**: Новый блок «Разметка» рядом с «Текст пользователя», отображающий разметку в формате karaoke-public (HTML от `formatText()`, палитра `.ke-fx-*` на чёрном фоне).

**Independent Test**: Открыть задание с разметкой (`parsedMarkers.length > 0`) → в модалке виден блок «Разметка» с цветными группами голосов на чёрном фоне, пиксель-в-пиксель идентично правой колонке karaoke-public `EditorWorkView.vue`.

### Implementation for User Story 2

- [x] T009 [US2] Добавить новый template-блок `.se-col-markup` с заголовком «Разметка» и `<div class="se-markup" :style="{ fontSize: previewFontSize + 'px' }" v-html="parsedMarkupHtml || '(пусто)'" />` в `<template>` блок `webvue3/src/components/SongEditor/ReviewModal.vue` (после `.se-col` с «Текст пользователя», перед `.se-col` с «Маркеры», FR-002).
- [x] T010 [US2] Добавить CSS-правило `.se-markup` в `<style scoped>` `webvue3/src/components/SongEditor/ReviewModal.vue`: `background: #000; border-radius: 8px; padding: 0.6rem; max-height: 220px; overflow: auto; white-space: pre-wrap; font-weight: 400; text-align: left;` (FR-006, Key Entities `.se-markup`).
- [x] T011 [US2] Добавить 6 CSS-правил `.se-markup :deep(.ke-fx-…)` в `<style scoped>` `webvue3/src/components/SongEditor/ReviewModal.vue` (`:deep(.ke-fx-cur)`, `:deep(.ke-fx-group0)`, `:deep(.ke-fx-group1)`, `:deep(.ke-fx-group2)`, `:deep(.ke-fx-group3)`, `:deep(.ke-fx-comment)`) — копия из `karaoke-public/src/views/EditorWorkView.vue:1861-1888` (FR-006, SC-002).

**Checkpoint**: US2 done. Блок «Разметка» отображается во всех 6 точках входа, реактивно обновляется при переключении голоса (FR-009).

---

## Phase 5: User Story 3 — Размер шрифта в обоих текстовых блоках (Priority: P1) 🎯

**Goal**: `.se-text` имеет `font-size = textFontSize` (16px дефолт), `.se-markup` имеет `font-size = previewFontSize` (18px дефолт), значения берутся из `loadEditorSettings()`.

**Independent Test**: С дефолтным localStorage `getComputedStyle('.se-text').fontSize === '16px'` и `.se-markup.fontSize === '18px'`. С кастомными настройками (`textFontSize=24`) — `'24px'`.

### Implementation for User Story 3

- [x] T012 [US3] Добавить inline-style `:style="{ fontSize: textFontSize + 'px' }"` в `<pre class="se-text">` в `webvue3/src/components/SongEditor/ReviewModal.vue` (FR-004).
- [x] T013 [US3] Удалить жёсткое `font-size: 0.82rem;` из CSS `.se-text` в `webvue3/src/components/SongEditor/ReviewModal.vue` (теперь управляется через inline-style, FR-004).

**Примечание**: `font-size` для блока «Разметка» уже задан в T009 через `:style`. Дополнительных задач не требуется.

**Checkpoint**: US3 done. Оба блока отображают шрифт, синхронизированный с настройками редактора.

---

## Phase 6: User Story 4 — Блок «Маркеры» в одну строку (Priority: P1) 🎯

**Goal**: Счётчики «Слоги · Концы строк · Новые строки · END» идут горизонтально с разделителем ` · `, заголовок «Маркеры: N» остаётся над счётчиками.

**Independent Test**: На десктопе (≥768px) блок «Маркеры» занимает ОДНУ строку: `getBoundingClientRect().height < lineHeight × 1.5`.

### Implementation for User Story 4

- [x] T014 [US4] Переформатировать template `.se-marker-summary` в `webvue3/src/components/SongEditor/ReviewModal.vue`: заменить `<div>` счётчиков на `<span>` (с разделителями `<span class="se-marker-sep">·</span>` между ними), сохранить `<div class="se-col-title">Маркеры: {{ markerCount }}</div>` сверху (FR-007).
- [x] T015 [US4] Обновить CSS `.se-marker-summary` в `<style scoped>` `webvue3/src/components/SongEditor/ReviewModal.vue`: `flex-direction: column` → `row`, добавить `flex-wrap: wrap; gap: 0.3rem 0.8rem; align-items: baseline;` (FR-007).
- [x] T016 [US4] Добавить CSS-правило `.se-marker-sep` в `<style scoped>` `webvue3/src/components/SongEditor/ReviewModal.vue`: `color: #aaa; font-weight: 400;` (визуальный разделитель).

**Checkpoint**: US4 done. Блок «Маркеры» визуально однострочный.

---

## Phase 7: User Story 5 — Адаптивная сетка `.se-cols` (Priority: P1) 🎯

**Goal**: Сетка `.se-cols` показывает 3 колонки на десктопе (≥1024px), 2 + Маркеры внизу на планшете (768–1023px), 1 колонку на мобиле (<768px). Ширина модалки увеличена до `min(96vw, 1100px)`.

**Independent Test**: На 1280px — три блока в одной строке. На 800px — Текст + Разметка в строке, Маркеры под ними. На 500px — все три вертикально (FR-008, SC-004).

### Implementation for User Story 5

- [x] T017 [US5] Обновить CSS `.se-cols` в `<style scoped>` `webvue3/src/components/SongEditor/ReviewModal.vue`: `grid-template-columns: 1fr 1fr` → `1fr` (mobile-first) с двумя `@media`-блоками: `@media (min-width: 768px) { grid-template-columns: 1fr 1fr; .se-cols .se-col:last-child { grid-column: 1 / -1; } }` и `@media (min-width: 1024px) { grid-template-columns: 1fr 1fr 1fr; .se-cols .se-col:last-child { grid-column: auto; } }` (FR-008).
- [x] T018 [US5] Обновить CSS `.se-modal-wide` в `<style scoped>` `webvue3/src/components/SongEditor/ReviewModal.vue`: `width: 760px` → `width: min(96vw, 1100px);` (FR-008, см. research.md #6).

**Checkpoint**: US5 done. Адаптивная сетка работает на всех breakpoints.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки, LiveDoc, форматирование, smoke-checks.

- [x] T019 Запустить `cd /home/nsa/Karaoke/webvue3 && npm run format` (если prettier ругается на `ReviewModal.vue` — авто-исправление), затем `npm run format:check` для верификации. Pass 244 fix (см. AGENTS.md).
- [x] T020 Запустить `cd /home/nsa/Karaoke/webvue3 && npm run lint`. Проверить, что baseline ESLint остался `[]` (никаких новых нарушений, см. AGENTS.md § «FR-007»).
- [x] T021 Запустить `cd /home/nsa/Karaoke/webvue3 && npm run build`. Если Vite падает на `Rollup failed to resolve import "formatText"` — создать fallback-файл `webvue3/src/components/SongEditor/useReviewModalFormat.js` (копия функции из `karaoke-public/src/composables/useKaraokeEditor.js:447` с `@see`-комментарием) и переключить импорт в `ReviewModal.vue` (см. contracts/README.md, Assumptions спеки). Повторить `npm run build`.
- [x] T021.1 **[fix]** Первоначально локальный `npm run build` прошёл успешно, но Docker-сборка `deploy/do.sh build_webvue3` провалилась: `Could not resolve "../../../../karaoke-public/src/composables/useKaraokeEditor"` (Docker копирует только `webvue3/`, импорт выходит за пределы). Создан локальный fallback `webvue3/src/components/SongEditor/useReviewModalFormat.js` с функциями `formatText` (генерирует `ke-fx-*`) + `loadEditorSettings` + `EDITOR_DEFAULTS`. Импорт в `ReviewModal.vue` переключён на `./useReviewModalFormat`. Docker-сборка теперь проходит: `✓ built in 8.66s`.
- [x] T021.2 **[governance fix]** Обновлён `AGENTS.md` v2.0.1 → v2.1.0 (Pass 245): добавлен новый шаг 5 «Docker-образы (NON-NEGOTIABLE)» — обязательная сборка `deploy/do.sh build_webvue3` (+ `build_public` если менялся karaoke-public) после локального Vite-build. Шаг 4 расширен на оба фронтенда (был только karaoke-public). Шаг 2 — добавлен `webvue3 npm run lint`. Файл 92 строки (лимит 100), `tools/check-livedocs-structure.sh` PASS 7/7.
- [x] T022 Создать `livedocs/features/263-editor-task-review-modal.md` по образцу `livedocs/features/154-editor-tasks-manage.md` или `163-fix-song-editor-regressions.md`. Включить frontmatter (status, slug, related), краткое «Что делает», US, FR-указатель, AC, секцию «Код» (компонент, файл), «История». (Constitution VI «FR-009» — per-feature документ в том же PR.)
- [x] T023 Запустить `tools/check-livedocs-structure.sh` — проверка структуры LiveDocs (≥5 фич, ≥5 BC, frontmatter, AGENTS.md ≤100, CI). Если новый LiveDoc сломал структуру — починить (например, добавить cross-link в `livedocs/features/README.md` если требуется).
- [x] T024 Запустить `tools/check-livedocs-cross-links.sh` — проверить cross-links (`../X.md` + `related:`).
- [ ] T025 Пройти `quickstart.md` сценарии 1–7 вручную в браузере на dev-машине: US1 (text-align), US2 (блок Разметка + палитра), US3 (font-size дефолт + кастомные + clamp), US4 (Маркеры одной строкой), US5 (адаптивная сетка на 3 ширинах), Edge cases (XSS, отсутствие запросов), Scope (все 6 точек входа). Зафиксировать результат в комментарии к PR. **ЗА ПОЛЬЗОВАТЕЛЕМ** — браузерная валидация.
- [ ] T026 Code review финальной версии `ReviewModal.vue` (template + script + scoped style): все 4 US реализованы, JSDoc на месте, нет лишних правок вне scope. Проверить, что Constitution § «Тесты» не нарушен (не добавляли новых тестов). **ЗА ПОЛЬЗОВАТЕЛЕМ** — финальный review.
- [x] T027 [Polish] **UX fix (Pass 246, 2026-08-30): ограничение высоты модалки при развёрнутом плеере.** При нажатии «▶ Прослушать (черновик)» в `ReviewModal.vue` высота `.se-modal` увеличивается (iframe-плеер 16:9 + ~110px controls), и на коротких экранах модалка «вылезает» за границы viewport — хедер (`.se-modal-title`) и футер (`.se-modal-btns`) становятся недостижимы. Фикс: добавить в `.se-modal` CSS-правила `max-height: 90vh; overflow-y: auto;`. Тогда при высоте модалки < 90vh она центрируется overlay-flex (как раньше), а при бóльшей высоте — обрезается до 90vh и появляется вертикальная прокрутка. Хедер и футер остаются в потоке документа, достижимы через скролл.
- [x] T028 [Polish] **Pass 247 (2026-08-30): слайдеры font-size в модалке.** DEPRECATED Clarifications Q (Сессия 2026-08-30) «слайдер в модалке НЕ нужен» — пользователь пересмотрел. Реализация: (1) добавить `saveEditorSettings` в `webvue3/src/components/SongEditor/useReviewModalFormat.js`; (2) импортировать её в `ReviewModal.vue`; (3) добавить два слайдера `<input type="range" min="6" max="36">` в template (по образцу `SongKaraokeEditorView.vue:287-289`) — один рядом с `.se-text`, другой рядом с `.se-markup`, подпись «Шрифт Npx»; (4) v-model.number на `textFontSize` / `previewFontSize`; (5) watcher'ы `textFontSize(v)` / `previewFontSize(v)` вызывают `saveEditorSettings({...})` с try/catch. Слайдеры модалки и редактора пишут в одну localStorage-переменную — синхронизация в обе стороны.

**Checkpoint**: фича готова к PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — можно стартовать сразу.
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS** все US (T004-T007 — фундамент для всех).
- **User Stories (Phase 3-7)**: All depend on Foundational phase completion.
  - US1, US2, US3, US4, US5 — логически независимы (каждая меняет свой блок компонента), НО конфликтуют при параллельной правке одного и того же файла `ReviewModal.vue`. Поэтому **рекомендуется последовательное выполнение** в порядке US1 → US2 → US3 → US4 → US5. Каждая US завершается checkpoint'ом, после которого можно начинать следующую.
- **Polish (Phase 8)**: Depends on all US complete.

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational. No dependencies on other stories. **Меняет только `.se-text` (CSS).**
- **US2 (P1)**: Can start after Foundational. **Меняет template (.se-col-markup) + новый CSS (.se-markup + :deep(.ke-fx-…)).** Не зависит от US1 (разные селекторы).
- **US3 (P1)**: Can start after Foundational. **Меняет `.se-text` (inline-style + удаление жёсткого font-size).** Файлы те же, что US1, но разные правила — рекомендуется **после US1**.
- **US4 (P1)**: Can start after Foundational. **Меняет `.se-marker-summary` (template + CSS) + добавляет `.se-marker-sep`.** Не зависит от других US.
- **US5 (P1)**: Can start after Foundational. **Меняет `.se-cols` и `.se-modal-wide` (CSS).** Не зависит от других US, но рекомендуется **последним**, чтобы видеть как сетка собирает все 3 блока вместе.

### Within Each User Story

- Template правки (если есть) → CSS правки → JSDoc.
- Story complete → Checkpoint → следующая US.

### Parallel Opportunities

> **Внимание**: все US меняют ОДИН файл `ReviewModal.vue`. Параллельное редактирование **невозможно** без merge-конфликтов. Поэтому для одного разработчика — последовательно.
>
> Параллелизация возможна только на уровне:
> - **T009 + T010 + T011** (внутри US2) — три позиции в ОДНОМ файле, но в разных местах (template, новое CSS-правило, шесть :deep-правил). Можно править одним коммитом.
> - **T014 + T015 + T016** (внутри US4) — аналогично.
>
> На уровне фаз:
> - **T019 + T020 + T021** (Polish) — независимые команды, можно запускать последовательно одной строкой.
> - **T023 + T024** — независимые скрипты.

---

## Parallel Example: User Story 2

```bash
# US2 — все три правки в одном файле, но в разных местах:
Task: "T009 [US2] Добавить новый template-блок .se-col-markup в <template> ReviewModal.vue"
Task: "T010 [US2] Добавить CSS-правило .se-markup в <style scoped> ReviewModal.vue"
Task: "T011 [US2] Добавить 6 :deep(.ke-fx-…) правил в <style scoped> ReviewModal.vue"

# Все три выполняются одним коммитом (правки в одном файле).
```

---

## Parallel Example: User Story 5

```bash
# US5 — две CSS-правки в одном файле:
Task: "T017 [US5] Обновить CSS .se-cols в <style scoped> ReviewModal.vue (адаптивная сетка)"
Task: "T018 [US5] Обновить CSS .se-modal-wide в <style scoped> ReviewModal.vue (ширина модалки)"

# Выполняются одним коммитом.
```

---

## Implementation Strategy

### MVP First (User Story 1)

Хотя все US имеют P1, минимальный жизнеспособный инкремент — это **US1 + US2 + US3** (визуальный стандарт модалки: выравнивание + размер шрифта + блок Разметка). US4 (Маркеры) и US5 (адаптивная сетка) — улучшения композиции, без них модалка остаётся функциональной.

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (text-align: left)
4. Complete Phase 4: User Story 2 (блок «Разметка»)
5. Complete Phase 5: User Story 3 (font-size)
6. **STOP and VALIDATE**: открыть модалку — блоки «Текст пользователя» и «Разметка» выглядят как в karaoke-public редакторе. Маркеры пока ещё 4-строчные (US4), сетка 2-колоночная (US5).
7. Complete Phase 6: User Story 4 (Маркеры одной строкой)
8. Complete Phase 7: User Story 5 (адаптивная сетка)
9. Complete Phase 8: Polish

### Incremental Delivery

Все US — P1, поэтому порядок: US1 → US2 → US3 → US4 → US5. Каждая US — checkpoint с ручной визуальной проверкой в браузере.

### Parallel Team Strategy

> **Не применимо для одного разработчика**: один файл, последовательно.
>
> **Для команды из 2**: можно разделить после Phase 2:
> - Dev A: US1 + US3 (один файл, `.se-text` блок) — последовательно US1, потом US3.
> - Dev B: US2 + US4 + US5 — последовательно, разные селекторы.
>
> Но merge-конфликты в `<style scoped>` неизбежны. **Рекомендуется 1 разработчик** на фичу.

---

## Notes

- [P] tasks = different files, no dependencies — для этой фичи минимальны (один файл), см. Parallel Opportunities.
- [Story] label — US1/US2/US3/US4/US5 (все P1; нумерация продолжает сквозную независимо от приоритета).
- Каждая user story завершается checkpoint'ом с ручной визуальной проверкой в браузере.
- Никаких тестов НЕ генерируется (в CI нет, см. plan.md Testing + Constitution § «Тесты»).
- Commit after each phase (Setup → Foundational → US1 → US2 → ... → Polish) — облегчает code review и bisect.
- Smoke-checks (T019, T020, T021) обязательны перед коммитом Polish-фазы (см. AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»).
- Избегать: правок вне `ReviewModal.vue` (кроме опционального fallback `useReviewModalFormat.js`); правок серверной части; добавления новых deps.
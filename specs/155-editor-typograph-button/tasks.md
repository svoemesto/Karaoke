---

description: "Task list for: Кнопка «Типограф» в онлайн-редакторе"
---

# Tasks: Кнопка «Типограф» в онлайн-редакторе

**Input**: Design documents from `/specs/155-editor-typograph-button/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/replacesymbolsinsong.md](./contracts/replacesymbolsinsong.md),
[quickstart.md](./quickstart.md)

**Tests**: Не запрашивались явно в спецификации, и в проекте нет CI-тестов для фронтенда
(`AGENTS.md`/`constitution.md`: «Тесты: в CI нет»). Проверка — ручная, по `quickstart.md`
(см. финальную задачу T011).

**Organization**: Задачи сгруппированы по user story из `spec.md` (US1/US2/US3, P1→P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: К какой user story относится задача (US1/US2/US3)
- Указаны точные пути к файлам

## Path Conventions

Backend не меняется. Два независимых Vue 3 SPA (Principle V Конституции — не смешивать):

- **Админка**: `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`
- **Публичный сайт**: `karaoke-public/src/views/EditorWorkView.vue`
- **Документация фичи**: `docs/features/editor-tasks.md` (подсистема #23, FR-009)

---

## Phase 1: Setup

**Purpose**: Подготовка окружения для ручной проверки изменений

- [ ] T001 Убедиться, что оба фронтенда запускаются локально для визуальной проверки: `webvue3`
      (админка) и `karaoke-public` (публичный сайт) — через `npm run dev` в соответствующей папке
      или через `deploy/do.sh` **только scoped-командами** (`build_start_webvue3`,
      `build_start_public` — НЕ безусловные `build_start_app`/`start`/`load`, см.
      `feedback_do_sh_scoped_commands` в памяти агента). Новых зависимостей фича не добавляет —
      `npm install` не требуется.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Не применимо для этой фичи.** Backend-эндпоинт `POST /api/replacesymbolsinsong` уже
реализован и не меняется (см. `contracts/replacesymbolsinsong.md`); общий JS-код
(`promisedXMLHttpRequest` в `lib/utils.js`, `splitSyllables`/`relabelSyllables`/
`syncMarkersFromSpecTags`/`onTextInput()` в `composables/useKaraokeEditor.js` и в самих
компонентах) уже существует в обоих фронтендах и не требует правок. Никаких общих/блокирующих
задач между `webvue3` и `karaoke-public` нет (Principle V — реализации независимы) — можно сразу
переходить к Phase 3.

---

## Phase 3: User Story 1 - Типографская правка текста одним кликом (Priority: P1) 🎯 MVP

**Goal**: По клику на кнопку «Типограф» текст текущего голоса заменяется теми же правилами
типографики, что и одноимённая кнопка в `SubsEdit.vue` (FR-002, FR-004, FR-006).

**Independent Test**: Открыть песню в онлайн-редакторе (админка или прод), ввести текст с
типографскими нарушениями, нажать «Типограф» и убедиться, что текст исправлен так же, как в
классическом редакторе (см. `quickstart.md`, Сценарий A/B, шаги 1-6, 8).

### Implementation for User Story 1

- [X] T002 [P] [US1] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`:
  1. Добавлен `import { promisedXMLHttpRequest } from '../../lib/utils'` в `<script>`.
  2. Внутри `<div v-if="canEdit" class="ske-kb-toolbar">` добавлена кнопка
     `<button type="button" class="ske-btn ske-btn-ghost" @click="doTypograph">Типограф</button>`
     сразу после кнопки «Очистить маркеры», плюс `<span v-if="typographError" ...>` для ошибки.
  3. Добавлен метод `async doTypograph()` рядом с `clearMarkers()`: вызывает
     `promisedXMLHttpRequest({ method: 'POST', url: '/api/replacesymbolsinsong', params: { txt: this.sourceText } })`,
     **не** пропуская ответ через `JSON.parse`, присваивает результат `this.sourceText`, затем
     вызывает `this.onTextInput()` (пересинхронизация маркеров — покрывает Phase 5/US3 сразу, не
     отдельным шагом). Обёрнут в `try/catch`: при ошибке `this.sourceText` НЕ меняется,
     выставляется **отдельное** локальное поле `typographError` (НЕ переиспользует
     `saveState`/«Ошибка сохранения» — при анализе (`/speckit-analyze`, находка U1) выяснилось,
     что `saveState` во `View.vue` объявлен, но нигде не используется/не отображается, а
     переиспользование `saveState` в `Modal.vue` дало бы вводящую в заблуждение подпись «Ошибка
     сохранения» для действия, не связанного с сохранением).
  4. `doTypograph()` сопровождён JSDoc-комментарием (`@see docs/features/editor-tasks.md`).
  5. Диалог подтверждения НЕ добавлен (FR-003) — в отличие от соседней `clearMarkers()`.

- [X] T003 [P] [US1] В `karaoke-public/src/views/EditorWorkView.vue`: аналогично T002—
  `import { promisedXMLHttpRequest } from '../lib/utils'`, кнопка «Типограф» +
  `<span v-if="typographError">` в `ke-kb-toolbar`, метод `doTypograph()` (тот же эндпоинт, сырая
  строка без `JSON.parse`, вызывает `this.onTextInput()` после присвоения `sourceText`), при
  ошибке — отдельное поле `typographError` (не `saveState`, по той же причине, что в T002 — здесь
  `saveState` реально используется для автосохранения и показывает «Ошибка сохранения», что было
  бы вводящей в заблуждение подписью). JSDoc добавлен. Без диалога подтверждения (FR-003).

**Checkpoint**: MVP готов и работает end-to-end — клик по «Типограф» в обоих онлайн-редакторах
заменяет текст текущего голоса по тем же правилам, что и в `SubsEdit.vue`, и сразу
пересинхронизирует маркеры (T006/T007 ниже — это подтверждение того, что T002/T003 уже это
делают, отдельного кода не потребовалось). Точное позиционирование/видимость — Phase 4.

---

## Phase 4: User Story 2 - Расположение и доступность кнопки рядом с «Очистить маркеры» (Priority: P2)

**Goal**: Кнопка «Типограф» видна ровно там же и при тех же условиях, что и «Очистить маркеры»
(FR-001, FR-007, FR-008).

**Independent Test**: Открыть оба онлайн-редактора в режиме редактирования и в режиме «только
чтение» и визуально сверить расположение/видимость кнопки (см. `quickstart.md`, Сценарий A/B,
шаги 3 и 9).

### Implementation for User Story 2

- [X] T004 [US2] Проверено в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`
      (после T002): кнопка «Типограф» находится непосредственно после «Очистить маркеры», внутри
      того же `<div v-if="canEdit" class="ske-kb-toolbar">` — скрывается вместе со всем тулбаром
      при `canEdit = false`. Правка markup из T002 уже это обеспечивает, дополнительных изменений
      не потребовалось. **Живая визуальная проверка в браузере (режим редактирования / «только
      чтение») остаётся за T011** (quickstart) — в этой сессии браузер недоступен.

- [X] T005 [US2] Аналогично проверено в `karaoke-public/src/views/EditorWorkView.vue` (после
      T003) — кнопка внутри того же `<div v-if="canEdit" class="ke-kb-toolbar">`, сразу после
      «Очистить маркеры». Живая визуальная проверка — за T011.

**Checkpoint**: Расположение и видимость кнопки подтверждены в обоих онлайн-редакторах и
согласованы между собой.

---

## Phase 5: User Story 3 - Согласованность маркеров после типографской замены (Priority: P3)

**Может начинаться сразу после Phase 3 (T002/T003), независимо от Phase 4** — зависимость только
от соответствующей задачи US1 в том же файле, не от US2 (см. «Dependencies & Execution Order»).

**Goal**: После замены текста маркеры остаются согласованными с новым текстом — ни один маркер
не «осиротевает» и не теряется/дублируется (FR-005).

**Independent Test**: Расставить маркеры (спецтеги `~newline~`/`~Куплет~` и т.п.) на тексте с
типографскими нарушениями, нажать «Типограф» и убедиться, что маркеры пересчитаны корректно
(см. `quickstart.md`, Сценарий A/B, шаг 7; `spec.md` User Story 3).

### Implementation for User Story 3

- [X] T006 [P] [US3] Выполнено вместе с T002: `doTypograph()` в
      `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` уже вызывает
      `this.onTextInput()` сразу после присвоения `this.sourceText` — тот же обработчик
      (`splitSyllables` → `relabelSyllables` → `syncMarkersFromSpecTags` + redraw/`$emit('change')`),
      что и при ручном вводе. Отдельная правка не потребовалась — решено сделать одним изменением,
      а не двумя последовательными (см. `research.md` §3).

- [X] T007 [P] [US3] Аналогично выполнено вместе с T003: `doTypograph()` в
      `karaoke-public/src/views/EditorWorkView.vue` вызывает `this.onTextInput()` после
      присвоения `sourceText` (пересинхронизация + `scheduleAutosave()`).

**Checkpoint**: Все три user story независимо функциональны — MVP (US1) дополнен точным
расположением (US2) и надёжной пересинхронизацией маркеров (US3).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Требования Конституции, не привязанные к конкретной user story

- [X] T008 [P] Обновлён `docs/features/editor-tasks.md` (подсистема #23, FR-009 Конституции) —
      добавлен раздел «Дополнение: кнопка «Типограф» в тулбаре редактора (spec 155)» с описанием
      поведения, ссылкой на `specs/155-editor-typograph-button/`.

- [X] T009 [P] Линтеры прогнаны: `eslint` (webvue3, karaoke-public) — 0 предупреждений/ошибок;
      `prettier --check` на обоих изменённых файлах — «All matched files use Prettier code
      style!». Замечаний нет.

- [X] T010 [P] Покрытие JSDoc проверено: `bash tools/check-jsdoc-coverage.sh` — webvue3 100.0%
      (136/136), karaoke-public 100.0% (44/44), TOTAL 100.0% — выше порога ≥50% (FR-006).

- [ ] T011 Прогнать все сценарии `quickstart.md` (A, B, C, D, E — включая добавленный при
      `/speckit-analyze` remediation Сценарий E «пустой текст») вручную в обоих онлайн-редакторах
      и сверить с Acceptance Scenarios каждой user story в `spec.md`. **Требует живого браузера —
      не выполнено в этой сессии агентом; остаётся на ручную QA пользователя** (аналогично T027 в
      `specs/154-editor-tasks-manage`). Зависит от T002-T007
      (весь код фичи).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: не применимо для этой фичи (см. выше) — можно сразу переходить к
  Phase 3
- **User Stories (Phase 3-5)**: US1 (T002/T003) не зависит от других историй; US2 (T004/T005)
  зависит от соответствующей задачи US1 в том же файле (T002→T004, T003→T005); US3 (T006/T007)
  зависит от соответствующей задачи US1 в том же файле (T002→T006, T003→T007), но **не**
  зависит от US2 — может выполняться сразу после US1, параллельно с US2
- **Polish (Phase 6)**: T008 независим; T009/T010 зависят от готового кода (T002-T007); T011
  зависит от всего кода фичи (T002-T007)

### Within Each File (admin vs public — независимы друг от друга, Principle V)

- `webvue3`: T002 → T004, T002 → T006 (T004 и T006 оба зависят от T002, но не друг от друга)
- `karaoke-public`: T003 → T005, T003 → T007 (T005 и T007 оба зависят от T003, но не друг от друга)

### Parallel Opportunities

- T002 и T003 — параллельно (разные файлы, разные фронтенды)
- После T002: T004 и T006 — параллельно (разные аспекты одного файла, не пересекаются построчно)
- После T003: T005 и T007 — параллельно
- T008, T009, T010 — параллельно друг с другом (разные файлы/команды)

---

## Parallel Example: User Story 1 (MVP)

```bash
# Запустить обе реализации US1 параллельно (разные фронтенды):
Task: "T002 [US1] Добавить кнопку «Типограф» + doTypograph() в webvue3/.../SongKaraokeEditorView.vue"
Task: "T003 [US1] Добавить кнопку «Типограф» + doTypograph() в karaoke-public/.../EditorWorkView.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational — пропустить (не применимо)
3. Phase 3: User Story 1 (T002, T003)
4. **STOP and VALIDATE**: проверить `quickstart.md` Сценарий A/B, шаги 1-6, 8 — замена текста
   работает в обоих редакторах
5. Продемонстрировать пользователю (основная ценность фичи уже доставлена)

### Incremental Delivery

1. Setup → Foundational (не применимо) → фундамент готов
2. US1 (T002-T003) → протестировать независимо → MVP готов
3. US2 (T004-T005) → протестировать независимо → расположение/видимость подтверждены
4. US3 (T006-T007) → протестировать независимо → маркеры больше не «осиротевают»
5. Polish (T008-T011) → документация, линтеры, финальная сквозная проверка по `quickstart.md`

---

## Notes

- [P]-задачи — разные файлы, нет зависимости от незавершённых задач.
- [Story]-метка привязывает задачу к конкретной user story для трассируемости.
- Backend не меняется ни в одной задаче — весь контракт уже описан в
  `contracts/replacesymbolsinsong.md` и переиспользуется как есть.
- Не смешивать код `webvue3` и `karaoke-public` (Principle V Конституции) — T002/T004/T006 и
  T003/T005/T007 остаются строго раздельными правками разных файлов.
- Коммитить после каждой завершённой user story (после Phase 3, после Phase 4, после Phase 5) —
  не всё разом.

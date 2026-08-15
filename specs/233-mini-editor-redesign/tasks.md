# Tasks: Мини-редактор песен в админке — редизайн

**Input**: Design documents from `/specs/233-mini-editor-redesign/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/ui-layout.md, quickstart.md

**Tests**: тесты НЕ включены (в CI нет, Constitution § «Рабочий процесс» → «Тесты»; ручная валидация — quickstart.md).

**Organization**: задачи сгруппированы по user story. **Важная особенность этой фичи**: ВСЕ US правят один файл `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` (~1761 строк). Поэтому:
- `[P]` (parallel) **только** для задач в **разных файлах** (LiveDoc, JSDoc, новый компонент drawer).
- Правки **внутри одного SFC** выполняются **последовательно** в порядке фаз (merge conflict в одном файле не имеет смысла делать параллельно).
- US1+US2 (P1) сгруппированы в одну фазу — они правят смежные блоки шаблона. То же для US3+US4 (P2).
- Адаптивность (FR-009/FR-010, drawer + scroll для голосов) вынесена в отдельную фазу, т.к. это кросс-сквозные CSS-правки, которые логичнее делать после структурных US.

## Path Conventions

- **Frontend-only SPA**: `webvue3/src/components/SongEditor/`
- **Per-feature LiveDoc**: `livedocs/features/233-mini-editor-redesign.md`
- **Task tool / spec files**: `specs/233-mini-editor-redesign/`

---

## Phase 1: Setup (Подготовка и обязательная документация)

**Purpose**: JSDoc-обновление (Constitution VI FR-006), per-feature LiveDoc (Constitution VI FR-009). Эти задачи блокируют остальные, потому что @see ссылается на LiveDoc.

- [x] T001 Обновить JSDoc на default export в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` — добавить `@see {@link livedocs/features/233-mini-editor-redesign.md}` (Constitution VI FR-006). Если JSDoc-блока нет — создать.
- [x] T002 [P] Создать `livedocs/features/233-mini-editor-redesign.md` со структурой frontmatter (`status: Active`, `slug: 233-mini-editor-redesign`, `related:` со ссылками на `livedocs/features/163-fix-song-editor-regressions.md` и `livedocs/features/232-admin-song-editor-local-db.md`) и секцией «Что делает» (3 карточки: плеер+голоса+волна, текст, preview + панель; скрытие спецтегов; drawer при <1024px; горизонтальный скролл голосов) — Constitution VI FR-009.

**Checkpoint**: LiveDoc существует, JSDoc ссылается на него. Можно стартовать Phase 2.

---

## Phase 2: Foundational (Подготовка шаблона)

**Purpose**: минимальные структурные правки в `<template>`, к которым «привязываются» все последующие US. Без этого шага US1–US4 пришлось бы делать в общем большом diff'е.

- [x] T003 Добавить data-свойство `rightDrawerOpen: false` в `<script>` секцию `data()` в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` (для FR-009, drawer).
- [x] T004 [P] Добавить метод `toggleRightDrawer()` в `<script>` секцию `methods:` в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` — инвертирует `rightDrawerOpen`. Используется в US5 (FR-009).
- [x] T005 Создать базовую CSS-обёртку `.ske-player-voice-wave-card` в `<style scoped>` секции `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`: общий фон/обводка, скруглённые углы, `overflow: hidden`. Без дочерних элементов пока — только стиль.

**Checkpoint**: data + method + стиль карточки готовы. Можно приступать к US.

---

## Phase 3: User Story 1 — Дубль заголовка удалён (Priority: P1) 🎯 MVP

**Goal**: в верхней части мини-редактора нет блока с названием, исполнителем и бейджем «Песня» (FR-001).

**Independent Test**: открыть мини-редактор для любой песни → в DOM нет отрендеренного `<div class="ske-header">`; название и исполнитель видны только в шапке страницы/модалки.

**Замечание**: используем `v-if="false"` (а не удаление блока), чтобы OQ-7 можно было решить в следующей итерации без восстановления шаблона (R-7 из research.md).

### Implementation for User Story 1

- [x] T006 [US1] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` заменить `<div class="ske-header">` на `<div v-if="false" class="ske-header">` (строки 3–12). Сопутствующие стили (`.ske-header`, `.ske-header-inner`, `.ske-h-song`, `.ske-h-author`, `.ske-header-right`) НЕ удалять — оставить для возможного возврата.

**Checkpoint**: US1 выполнен. Заголовок скрыт, DOM-блок сохранён. Можно переходить к US2.

---

## Phase 4: User Story 2 — Карточка «плеер + голоса + волна» (Priority: P1)

**Goal**: единый визуальный карточный блок с общим фоном/обводкой для `ske-player-toggle` + `ske-voice-tabs` + `ske-wave-card` + `ske-tail-card` + `ske-transport` (FR-002, Q3).

**Independent Test**: открыть мини-редактор для любой песни → `.ske-player-toggle`, `.ske-voice-tabs`, `.ske-wave-card` находятся внутри одного `<div class="ske-player-voice-wave-card">` с общим фоном; лёгкие разделители (`border-top`) между дочерними блоками.

### Implementation for User Story 2

- [x] T007 [US2] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` обернуть блоки `.ske-player-toggle` (стр. 20), `.ske-player-wrap` (стр. 25, опционально), `.ske-voice-tabs` (стр. 30), `.ske-wave-card` (стр. 60), `.ske-tail-card` (стр. 66), `.ske-transport` (стр. 76) в общий `<div class="ske-player-voice-wave-card">…</div>`. Не менять порядок и не разрывать существующие `v-if` (`v-if="showPlayer"` для `.ske-player-wrap`).
- [x] T008 [US2] В `<style scoped>` `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` добавить стили для `.ske-player-voice-wave-card`: `background`, `border`, `border-radius`, `padding`, `margin-bottom`. Убрать `background`/`border` у дочерних `.ske-player-toggle`, `.ske-voice-tabs`, `.ske-wave-card`, `.ske-tail-card`, `.ske-transport` (если они есть), чтобы не было двойной обводки. Добавить `border-top: 1px solid <color>` (кроме первого ребёнка) для лёгких разделителей — через `:not(:first-child)`.
- [x] T009 [US2] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` добавить в `.ske-voice-tabs` CSS-свойства: `flex-wrap: nowrap`, `overflow-x: auto`, `scrollbar-width: thin` (Firefox), кастомный `::-webkit-scrollbar` (опционально, Webkit) — для FR-010 (Q5, >4 голосов). Убедиться, что кнопки «+ Голос» и «✕ Удалить голос …» тоже попадают в скролл.

**Checkpoint**: US1 + US2 выполнены. Карточка плеера готова. Можно переходить к US3+US4.

---

## Phase 5: User Story 3 — Спецтеги скрыты по умолчанию (Priority: P2)

**Goal**: блок `.ske-spectag-toolbar` виден только при `showKeyboard === true`; существующая кнопка «Показать клавиатуру» — единый toggle (FR-003, Q1).

**Independent Test**: открыть мини-редактор → блок спецтегов «Новая строка / Куплет / …» НЕ виден; клик на «Показать клавиатуру» — одновременно появляется и клавиатура, и спецтеги.

### Implementation for User Story 3

- [x] T010 [US3] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` **вырезать** `<div v-if="canEdit" class="ske-spectag-toolbar">…</div>` (строки 197–254, 7 кнопок спецтегов) из `.ske-text-col:first-child` и **вставить** его в самое начало `<div v-if="canEdit && showKeyboard" class="ske-keyboard">…</div>` (строка 160, перед `.ske-kb-grid`). Не менять `onInsertSpecTag*` обработчики, не менять CSS-классы кнопок.

**Checkpoint**: спецтеги теперь рендерятся только при `showKeyboard`. US3 готов.

---

## Phase 6: User Story 4 — Панель маркер-действий в правой колонке (Priority: P2)

**Goal**: блок `.ske-kb-toolbar` (3 кнопки + typographError) перенесён в правую колонку, непосредственно над preview-блоком «Разметка» (FR-004, Q2).

**Independent Test**: открыть мини-редактор → кнопки «Показать клавиатуру / Очистить маркеры / Типограф» видны в правой колонке, **над** preview-блоком; в текущей старой позиции (между `.ske-transport` и `.ske-texts`) их нет.

### Implementation for User Story 4

- [x] T011 [US4] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` **вырезать** `<div v-if="canEdit" class="ske-kb-toolbar">…</div>` (строки 146–159, 3 кнопки + typographError) из текущей позиции (между `.ske-transport` и `.ske-keyboard`-v-if-блоком). **Вставить** его в `<div class="ske-text-col">` правой колонки (`:last-child`, строка 266), **перед** `<div class="ske-col-header">` с заголовком «Разметка» (т.е. первым элементом внутри колонки). Сохранить `v-if="canEdit"`. Не менять обработчики (`showKeyboard`, `clearMarkers`, `doTypograph`). — **Pass 1 выполнен, отменён в Pass 2 (см. T023).**
- [x] T014 [US5] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` **добавить** в `<template>` на корневом элементе `.ske-text-col:last-child` (правая колонка) `:class="{ 'ske-drawer-open': rightDrawerOpen }"`. Это позволяет CSS-правилу из T012 разворачивать drawer через transform.

**Checkpoint**: панель маркер-действий в правой колонке, над preview. US3 + US4 готовы.

---

## Phase 7: User Story 5 + Адаптивность (drawer + >4 голосов) (Priority: P3 + FR-009/FR-010)

**Goal**:
- Drawer: при `<1024px` правая колонка сворачивается в выдвижную панель, открывается по кнопке-«бургер» (FR-009, Q4).
- Визуальная иерархия: 3 карточки читаются с первого взгляда (US5).
- >4 голосов: горизонтальный скролл в `.ske-voice-tabs` (FR-010, Q5) — задача T009 уже в Phase 4, здесь — валидация.

**Independent Test**: ресайз окна браузера до <1024px → правая колонка скрыта; кнопка-«бургер» видна; клик — drawer выезжает с затемнением; клик на backdrop — drawer закрывается.

### Implementation for User Story 5 + Адаптивность

- [x] T012 [US5] В `<style scoped>` `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` добавить `@media (max-width: 1023.98px)` правила:
  - `.ske-text-col:last-child { position: fixed; top: 0; right: 0; bottom: 0; width: 90vw; max-width: 480px; transform: translateX(100%); transition: transform 0.2s; z-index: 10; background: <card-bg>; padding: <padding>; overflow-y: auto; box-shadow: <shadow>; }`
  - `.ske-text-col:last-child.ske-drawer-open { transform: translateX(0); }` (используем binding класса через computed, см. T014)
  - `.ske-drawer-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 9; }` (видим только при `rightDrawerOpen === true` через `v-if`)
  - `.ske-drawer-toggle { display: inline-flex; }` (на десктопе `display: none`)
- [x] T013 [US5] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` **добавить** в шаблон (рядом с `.ske-player-voice-wave-card` или в другом удобном месте — по визуальной примерке):
  - `<div v-if="rightDrawerOpen" class="ske-drawer-backdrop" @click="rightDrawerOpen = false" />`
  - `<button type="button" class="ske-drawer-toggle ske-btn ske-btn-ghost" @click="toggleRightDrawer" aria-label="Открыть панель разметки">☰</button>` (видимость управляется через CSS в T012)
- [x] T015 [US5] Опционально: создать `webvue3/src/components/SongEditor/MiniEditorDrawer.vue` — отдельный компонент-обёртку для правой колонки, инкапсулирующий drawer-логику. Использовать ТОЛЬКО если T013+ T014 в основном файле становятся слишком тяжёлыми; иначе — оставить inline. — **НЕ СОЗДАЁМ** (inline-решение достаточно; 1823 строки — приемлемо).
- [x] T016 [US5] Валидация `>4 голосов` (FR-010, Q5): открыть `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` → найти `.ske-voice-tabs` → убедиться, что стили из T009 (flex-wrap: nowrap, overflow-x: auto) присутствуют; иначе — добавить. Горизонтальный скролл должен работать для всех голосов + 2 служебных кнопок. — **T009 применил стили, визуальная валидация — за пользователем по quickstart.md шаг 3.**

### Pass 2 (2026-08-15): итерация 2 правок (по скриншоту пользователя)

- [x] T023 [US4] **Pass 2: переместить `.ske-kb-toolbar` обратно в `.ske-transport`** (отмена T011): в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` **вырезать** `.ske-kb-toolbar` из правой колонки (Pass 1) и **вставить** в `<div class="ske-transport">` сразу после `<div class="ske-sliders">…</div>` (т.е. после ползунка «Громкость»). Сохранить `v-if="canEdit"`. Снять `:class="{ 'ske-drawer-open': rightDrawerOpen }"` с `.ske-text-col:last-child` — это bind был нужен, когда панель маркер-действий была в правой колонке, теперь она в `.ske-transport`; drawer остаётся только для preview.
- [x] T024 [US2] **Pass 2: верхняя строка карточки — голоса слева, «Прослушать в плеере» справа** (уточнение Q3): в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` обернуть `.ske-voice-tabs` и `.ske-player-toggle` в новый `<div class="ske-pv-header">` с `display: flex; justify-content: space-between`. Внутри строки: `.ske-voice-tabs` слева (через `flex: 1 1 auto`), `.ske-player-toggle` справа (`flex: 0 0 auto`). `.ske-player-wrap` оставить отдельным блоком после строки (включается по клику).
- [x] T025 [US4] **Pass 2: панель `.ske-kb-toolbar` — без переноса**: в `<style scoped>` `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` для `.ske-kb-toolbar` установить `flex-wrap: nowrap`, `white-space: nowrap`, уменьшить `gap` до `0.4rem`, `padding: 0.35rem 0.6rem` и `font-size: 0.85rem` на `.ske-btn` внутри. Три кнопки (Показать клавиатуру / Очистить маркеры / Типограф) + `typographError` должны быть в одной строке внутри `.ske-transport`.
- [x] T026 [LiveDoc] Обновить `livedocs/features/233-mini-editor-redesign.md`: секция «Реализация» — отразить Pass 2 (положение панели, порядок строки карточки, новый CSS-класс `.ske-pv-header`).
- [x] T027 [Spec] Обновить `specs/233-mini-editor-redesign/spec.md`: US2, US4, FR-002, FR-004 — отразить Pass 2 (строка карточки, расположение панели). Обновить Clarifications (Q2 и Q3 — отмены). Снять `:-стили для правой колонки`, ссылающиеся на панель.
- [x] T028 [Quickstart] Обновить `specs/233-mini-editor-redesign/quickstart.md`: шаги 4, 5, 7 — отразить новое расположение панели маркер-действий (в `.ske-transport`, после Громкости), а не в правой колонке. Шаг 1 — отразить порядок в верхней строке карточки.

**Checkpoint (Pass 2)**: панель в карточке плеера, не переносится; верхняя строка карточки: голоса слева, кнопка справа; визуальная иерархия по-прежнему читается. Все 5 US закрыты (обновлены под Pass 2).

### Pass 3 (2026-08-15): убрать пустоту вокруг бегущей строки

- [x] T029 [CSS] В `<style scoped>` `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` для `.ske-tail-card` установить `min-height: 0` (было `96px`) и `padding: 0.35rem 0.5rem` (было `1rem`). Содержимое бегущей строки — одна строка текста, прежние значения давали много пустоты сверху/снизу.
- [x] T030 [CSS] Для `.ske-player-voice-wave-card > .ske-tail-card` (override общего `> * + *`) установить `margin-top: 0.25rem; padding-top: 0.25rem` (было `0.5rem / 0.5rem` — общие для всех дочерних блоков). Две красные рамки на скриншоте пользователя (между вейвформой и `.ske-tail-card`, и между `.ske-tail-card` и `.ske-transport`) сжимаются.
- [x] T031 [LiveDoc/Spec] Обновить `livedocs/features/233-mini-editor-redesign.md` и `specs/233-mini-editor-redesign/spec.md`: отразить Pass 3 — `.ske-tail-card` без `min-height`, уменьшенный padding.

**Checkpoint (Pass 3)**: бегущая строка компактная, пустоты вокруг нет, общая компоновка карточки плеера сохранена.

### Pass 4 (2026-08-15): редизайн вейвформы — паритет с SubsEdit

- [x] T032 [WaveSurfer] В `initWaveSurfer()` в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` обновить параметры `WaveSurfer.create()`: `height: 200` (было 140), `waveColor: 'rgb(200, 0, 200)'` (было `#9db4d6`), `progressColor: 'rgb(100, 0, 100)'` (было `accent`), `cursorColor: 'rgb(255, 0, 0)'` (было `#ff5252`), `cursorWidth: 3` (было 2), `barWidth: 4`, `barRadius: 2`, `barHeight: 1`, добавить `autoCenterImmediately: true`.
- [x] T033 [WaveSurfer plugin] Добавить импорт `Hover` из `'wavesurfer.js/dist/plugins/hover.esm.js'`. Подключить в `plugins: [...]`: `Hover.create({ lineColor: '#000000', lineWidth: 2, labelBackground: '#555', labelColor: '#fff', labelSize: '11px', formatTimeCallback: ... })`. Формат времени `mm:ss.mmm`. Даёт таймкоды сверху вейвформы и tooltip с текущим временем.
- [x] T034 [Markers] В `redrawRegions()` увеличить opacity маркеров: `color: this.hexToRgba(m.color, 0.7)` (было 0.35) — менее прозрачные, ближе к SubsEdit.
- [x] T035 [Spec/LiveDoc] Обновить `livedocs/features/233-mini-editor-redesign.md` (секция «Реализация» + «Что делает»): отразить Pass 4 — фиолетовая вейвформа, Hover plugin, плотные маркеры.

**Checkpoint (Pass 4)**: вейвформа в мини-редакторе визуально соответствует SubsEdit (фиолетовые бары, таймкоды сверху, tooltip с `mm:ss.mmm`, плотные маркеры). Никаких новых зависимостей, никаких изменений в data/computed/methods.

### Pass 5 (2026-08-15): размер бейджей слогов — паритет с SubsEdit

- [x] T036 [Markers] В `regionContentEl()` в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` для `markertype === 'syllables'`: убрать принудительные `fontSize: 9px`, `padding: 1px 3px` с корневого `<div>`, поставить `fontSize: 15px` на `<span>`-бейдж (вместо 9px), `display: block; width: 100%; text-align: center` — бейдж растягивается на всю ширину региона и центрируется. Добавить `line-height: 1.1; overflow: hidden; text-overflow: ellipsis` для длинных слогов. Цвет фона `beige`, цвет текста `#222`, font-weight `700` — как в SubsEdit.
- [x] T037 [Spec/LiveDoc] Обновить `livedocs/features/233-mini-editor-redesign.md` (секция «Реализация»): отразить Pass 5 — бейджи слогов 15px, растягиваются на ширину региона.

### Pass 5 fix (2026-08-15): регресс — слоги не видны

- [x] T036-fix [Markers] **Pass 5 fix**: предыдущая реализация `display: flex; align-items: center; justify-content: center; width: 100%` на `<span>`-бейдже + `overflow: hidden` на корневом `<div>` давала схлопывание высоты бейджа до 0 — слоги не были видны на вейвформе. Возврат к `display: block; width: 100%; text-align: center` на бейдже, удалён `overflow: hidden` с корневого `<div>`. Теперь бейдж виден.

**Checkpoint (Pass 5 fix)**: бейджи слогов снова видны на вейвформе, по размеру соответствуют SubsEdit (15px, beige, на всю ширину региона). Остальные типы маркеров (endofline, newline, setting END, GROUP, COMMENT) — не затронуты.

### Pass 5 v2 (2026-08-15): ширина бейджа — по ширине слога

- [x] T036-v2 [Markers] **Pass 5 v2**: бейдж слога не должен растягиваться на всю ширину региона (`width: 100%`) — на скриншоте SubsEdit бейдж **по ширине слога** (`width: fit-content`). Заменить `width: 100%` на `width: fit-content`, убрать `text-align: center` (не нужен), убрать `overflow: hidden; text-overflow: ellipsis` (не нужны — бейдж и так узкий). `display: block` оставлен (как в SubsEdit).

**Checkpoint (Pass 5 v2)**: бейдж слога узкий, по ширине текста — паритет со SubsEdit.

### Pass 5 v3 (2026-08-15): шрифт бейджа — обычный, не жирный

- [x] T036-v3 [Markers] **Pass 5 v3**: на скриншоте SubsEdit бейдж слога — обычный шрифт (не bold). Заменить `fontWeight: '700'` на `fontWeight: 'normal'` на корневом `<div>` бейджа. Текст слога теперь `normal`.

**Checkpoint (Pass 5 v3)**: бейдж слога — обычный шрифт, как в SubsEdit.

### Pass 6 (2026-08-15): перенос Pass 2/3/4/5 в `karaoke-public` (адаптированный)

- [x] T038 [Pass 2 — adapted] В `karaoke-public/src/views/EditorWorkView.vue`: обернуть блоки `.ke-player-toggle`/`.ke-player-wrap`/`.ke-voice-tabs`/`.ke-wave-card`/`.ke-tail-card`/`.ke-transport` в `<div class="ke-pv-stack">` (логическая группа, **без общего фона/обводки** — чтобы не конфликтовать с `--km-card` дизайн-системы public). Создать `.ke-pv-header` (flex space-between) с `.ke-voice-tabs` слева и `.ke-player-toggle` справа.
- [x] T039 [Pass 2 — adapted] Перенести `.ke-kb-toolbar` из отдельного блока (между `.ke-transport` и `.ke-keyboard`) в `.ke-transport`, после `.ke-sliders` (т.е. после ползунка «Громкость»). Удалить старую позицию.
- [x] T040 [Pass 2 — adapted] Перенести `.ke-spectag-toolbar` из левой текстовой колонки в `<div v-if="canEdit && showKeyboard" class="ke-keyboard">`, в самое начало (перед `.ke-kb-grid`).
- [x] T040-fix [Pass 6 fix] **Дубликат `.ke-spectag-toolbar`**: при переносе я добавил копию в `.ke-keyboard`, но **не удалил** старую из текстовой колонки. В результате блок спецтегов был в **обоих** местах. Удалить старую копию из `.ke-text-col:first-child` (после `</div>` `.ke-col-header`, перед `<textarea>`).
- [x] T041 [Pass 2 — CSS] В `<style scoped>`: `.ke-voice-tabs` — `flex-wrap: nowrap; overflow-x: auto; scrollbar-width: thin` + WebKit-стили. `.ke-pv-stack`, `.ke-pv-header` — flex-стили для строки. `.ke-kb-toolbar` — `flex-wrap: nowrap; white-space: nowrap; gap: 0.4rem` + компактный padding/font на `.ke-btn` внутри.
- [x] T042 [Pass 3 — adapted] `.ke-tail-card`: `min-height: 0` (было `96px`), `padding: 0.35rem 0.5rem` (было `1rem`). Фон/обводка/скругление через `--km-card`/`--km-border` оставлены.
- [x] T043 [Pass 4] В `initWaveSurfer()`: обновить параметры `WaveSurfer.create()` (паритет с admin/SubsEdit): `height: 200`, фиолетовые цвета, `cursorWidth: 3`, `barWidth: 4`, `barRadius: 2`, `barHeight: 1`, `autoCenterImmediately: true`. Добавить импорт `Hover` и подключить в `plugins: [...]` с `formatTimeCallback` для `mm:ss.mmm`. `redrawRegions()`: opacity маркеров 0.35 → 0.7.
- [x] T044 [Pass 5] В `regionContentEl()`: убрать `fontSize: 9px` и `padding: 1px 3px` с корневого `<div>`, `fontWeight: 700` → `normal`. На `<span>`-бейдже слога: `fontSize: 15px`, `display: block; width: fit-content; padding: 2px 4px; whiteSpace: nowrap`.
- [x] T045 [Spec/LiveDoc] Обновить `livedocs/features/233-mini-editor-redesign.md` — секция «Реализация»: отразить Pass 6 (адаптация для `karaoke-public` — без общей обводки карточки плеера, Pass 2/3/4/5 применены).
- [x] T046 [Lint+Build] `cd karaoke-public && ./node_modules/.bin/eslint src/views/EditorWorkView.vue` — 0 errors. `npm run build` — успешная сборка. Сборка `webvue3` — без регрессий.

**Checkpoint (Pass 6)**: Pass 2/3/4/5 адаптированы для `karaoke-public/EditorWorkView.vue`. Дизайн-система public (`--km-card`/`--km-border`/etc.) не сломана. Никаких изменений в `mode='assignment'`, в `EditorTasksView.vue`, в `data`/`computed`/`methods`, кроме `regionContentEl()`, `initWaveSurfer()`, `redrawRegions()`.

### Pass 7 (2026-08-15): убрать вертикальную полосу прокрутки в preview-блоке

- [x] T047 [CSS] В `karaoke-public/src/views/EditorWorkView.vue` для `.ke-preview` убрать `max-height: 620px` — он давал жёсткий cap и вертикальную полосу прокрутки при длинной разметке. Теперь `min-height: 520px` без `max-height` совпадает с `.ke-textarea` — оба блока одинаковой высоты. `overflow-y: auto` оставлен на случай очень длинной разметки (но теперь срабатывает реже, т.к. grid выравнивает высоту строки).

**Checkpoint (Pass 7)**: высота preview-блока разметки совпадает с высотой блока текста песни. Вертикальная полоса прокрутки больше не появляется при стандартных сценариях.

### Pass 7 fix (2026-08-15): пользователь передумал — нужна полоса прокрутки

- [x] T047-fix [CSS] **Pass 7 fix**: пользователь хочет вертикальную полосу прокрутки в ОБОИХ блоках. Возвращаю `max-height: 620px` на `.ke-preview` (был убран в Pass 7) и **добавляю** `max-height: 620px` на `.ke-textarea` (раньше у него был только `min-height: 520px` и `resize: vertical` — textarea могла расти бесконечно). Теперь оба блока имеют `min-height: 520px; max-height: 620px` — одинаковая высота, у обоих появляется полоса прокрутки при длинном контенте (у textarea — нативная, у preview — через `overflow-y: auto`).

**Checkpoint (Pass 7 fix)**: блоки «текст песни» и «разметка» одинаковой высоты (`min-height: 520px; max-height: 620px`), у обоих вертикальная полоса прокрутки при длинном контенте.

### Pass 7 fix2 (2026-08-15): высота preview всё равно выше — flex + column-fill balance

- [x] T047-fix2 [CSS] **Pass 7 fix2**: пользователь сообщил, что preview выше textarea. Причина: `column-fill: auto` балансирует колонки по высоте всего контента, а не контейнера — preview вылезает за max-height. Применяю: `flex: 1 1 auto` + `min-height: 0` на `.ke-textarea` и `.ke-preview` (растягиваются в flex-колонке `.ke-text-col` одинаково), `column-fill: balance` (вместо `auto`) на `.ke-preview` (балансирует по высоте контейнера). Теперь высота обоих блоков совпадает, у обоих появляется полоса прокрутки при длинном контенте.

**Checkpoint (Pass 7 fix2)**: блоки «текст песни» и «разметка» одинаковой высоты (высота строки grid = `max(textarea, preview)`), у обоих вертикальная полоса прокрутки при длинном контенте. Многоколоночность preview сохранена.

### Pass 7 fix2 → admin (2026-08-15): применить к `webvue3/SongKaraokeEditorView.vue`

- [x] T047-fix2-admin [CSS] В `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`:
  - `.ske-textarea`: добавлен `max-height: 620px` + `flex: 1 1 auto` (как в Pass 7 fix2 для public).
  - `.ske-preview`: добавлен `flex: 1 1 auto`; `column-fill: auto` → `column-fill: balance`.

**Checkpoint (Pass 7 fix2 → admin)**: блоки «текст песни» и «разметка» в `webvue3/SongKaraokeEditorView.vue` тоже одинаковой высоты, у обоих вертикальная полоса прокрутки при длинном контенте. Парность admin ↔ public восстановлена.

### Pass 7 fix3 (2026-08-15): пользователь передумал — вернуть автоподсчёт высоты

- [x] T048 [CSS] **Pass 7 fix3 (admin)**: в `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue`:
  - `.ske-textarea`: убраны `max-height: 620px` и `flex: 1 1 auto`. Возвращено: только `min-height: 520px` и `resize: vertical`.
  - `.ske-preview`: убран `flex: 1 1 auto`. Возвращено: `column-fill: balance` → `column-fill: auto`.
- [x] T049 [CSS] **Pass 7 fix3 (public)**: в `karaoke-public/src/views/EditorWorkView.vue`:
  - `.ke-textarea`: убраны `max-height: 620px` и `flex: 1 1 auto`. Возвращено: только `min-height: 520px` и `resize: vertical`.
  - `.ke-preview`: убран `flex: 1 1 auto`. Возвращено: `column-fill: balance` → `column-fill: auto`.

**Checkpoint (Pass 7 fix3)**: автоподсчёт высоты блоков «текст песни» и «разметка» возвращён к состоянию до Pass 7. Высота определяется контентом, обрезка по `max-height: 620px` с вертикальной полосой прокрутки — только при длинной разметке. Admin и public синхронизированы.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: линт, сборка, документация, ручная валидация по quickstart.md, per-feature LiveDoc обновление, git-операции.

- [x] T017 [P] Запустить `cd webvue3 && ./node_modules/.bin/eslint src/components/SongEditor/SongKaraokeEditorView.vue` — должно вернуть 0 errors и **0 new** warnings сверх baseline. Если новые warnings — исправить в этом же файле. (Constitution VI FR-007.) — **0 errors, 0 warnings.**
- [x] T018 [P] Запустить `cd webvue3 && npm run build` — должна пройти без warnings о новых undefined props/template errors. Если падает — исправить. — **Сборка успешна за 7.71s. Warnings о wavesurfer.js — предсуществующие (SubsEdit.vue), не наши.**
- [x] T019 [P] Обновить секцию «Что делает» в `livedocs/features/233-mini-editor-redesign.md`: дописать фактические детали реализации (какие блоки перенесены, какие CSS-классы добавлены, состояние `rightDrawerOpen`). Добавить в `related:` ссылку на `specs/233-mini-editor-redesign/contracts/ui-layout.md` (после реализации — список реально добавленных классов, не план). — **Секция «Реализация» добавлена в LiveDoc.**
- [ ] T020 Выполнить ручную валидацию по `specs/233-mini-editor-redesign/quickstart.md` — все 10 шагов должны пройти. Отметить чек-лист в quickstart.md. Скриншот «после» — приложить к PR-описанию. — **Оставлено пользователю: требует запуска dev-сервера, открытия в браузере, ресайза окна и реальных песен в LOCAL-БД.**
- [x] T021 [P] Запустить `git status` + `git diff --stat` — проверить, что нет изменений в `deploy/`, `*.env`, `dist/`, `node_modules/`, секрет-файлах (Constitution VIII). Если есть — `git checkout --` для этих файлов. — **`git status` показал только правки 233; `git ls-files | grep -iE 'env|key|pem'` пусто.**
- [ ] T022 [P] `cd webvue3 && npm run build` финальный (после всех правок) — должен пройти. `git add` + `git commit -m "233: мини-редактор — редизайн (карточка плеера, скрытие спецтегов, панель справа, drawer)"` — только если пользователь явно попросил. — **Оставлено пользователю (Constitution § «Git»: коммит только по явному запросу).**

**Checkpoint**: фича готова к PR. Все артефакты на месте: спекa, plan, research, data-model, contracts, quickstart, LiveDoc, JSDoc @see. Линт и сборка зелёные.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: без зависимостей — JSDoc + LiveDoc.
- **Phase 2 (Foundational)**: зависит от Phase 1 (LiveDoc должен существовать до того, как JSDoc на него ссылается — но JSDoc можно обновить и до создания LiveDoc, имя ссылки статическое). Рекомендуется: Phase 1 → Phase 2.
- **Phase 3-7 (User Stories)**: зависят от Phase 2. US правят один файл → **последовательно**: US1 → US2 → US3 → US4 → US5. Параллелизм только внутри шага T009 (CSS) и T012-T014 (CSS drawer), но они в одном файле — лучше последовательно.
- **Phase 8 (Polish)**: зависит от всех US.

### User Story Dependencies

- **US1 (P1)**: может стартовать после Phase 2. Без зависимостей от других US.
- **US2 (P1)**: должен идти после US1 (header скрыт → карточка плеера).
- **US3 (P2)**: может идти параллельно с US2 (разные блоки шаблона), но файл один — лучше последовательно после US2.
- **US4 (P2)**: должен идти после US3 (та же `.ske-keyboard` / `.ske-kb-toolbar` область).
- **US5 (P3) + адаптивность**: идёт последним, т.к. зависит от того, что `.ske-text-col:last-child` (правая колонка) уже содержит панель (T011 из US4).

### Within Each User Story

- В этой UI-фиче нет «models before services» — порядок простой:
  1. Структура шаблона (вырезать/обернуть/вставить).
  2. CSS-стили.
  3. Опционально — новые data/methods.
- Story complete перед переходом к следующему приоритету (но US1+US2 можно объединить в один коммит, т.к. они P1 + смежные).

### Parallel Opportunities

- T002 (LiveDoc) и T001 (JSDoc) — `[P]` между собой (разные файлы).
- T003 (data) и T004 (method) — `[P]` (один файл, но разные свойства; на практике лучше одной правкой).
- T017 (ESLint), T018 (build), T019 (LiveDoc update), T021 (git status) — все `[P]` друг с другом.
- T022 (commit) — последовательно после T017..T021.

---

## Parallel Example: User Story 1

US1 — однозадачная (одна правка в одном файле). Параллелизм только с US5 (другая область) или с Phase 8 polish. Реально — выполняется одна задача T006, потом сразу валидация.

---

## Implementation Strategy

### MVP First (User Story 1+2 — оба P1)

1. ✅ Phase 1: Setup (JSDoc + LiveDoc).
2. ✅ Phase 2: Foundational (data + method + CSS-обёртка).
3. ✅ Phase 3: US1 — заголовок скрыт.
4. ✅ Phase 4: US2 — карточка плеера.
5. **STOP and VALIDATE**: открыть мини-редактор → заголовка нет, карточка плеера собрана. **MVP готов**.
6. Можно отдать пользователю на раннее ревью.

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready (JSDoc, LiveDoc, базовые стили).
2. + Phase 3 + 4 (US1 + US2) → MVP: убран дубль заголовка, карточка плеера.
3. + Phase 5 + 6 (US3 + US4) → панель в правой колонке, спецтеги скрыты.
4. + Phase 7 (US5 + адаптивность) → drawer при <1024px, скролл для >4 голосов.
5. + Phase 8 → линт/сборка/валидация/коммит.

Каждая фаза добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С одним разработчиком (или AI-агентом) — последовательно по фазам. С 2+ разработчиками:
- Dev A: Phase 1 (LiveDoc) + Phase 8 (документация).
- Dev B: Phase 2-7 (код).
- С одним файлом — лучше 1 человек.

---

## Notes

- `[P]` = разные файлы, без зависимостей. В этой фиче большинство задач — в одном файле, поэтому `[P]` используется редко.
- `[Story]` label привязывает задачу к US для трассировки. Phase 1-2, 7 (адаптивность), 8 — без Story label.
- Каждая US независимо тестируема (через quickstart.md).
- Тесты не пишутся (в CI нет; Constitution § «Рабочий процесс» → «Тесты»).
- Коммит только по явной просьбе пользователя (Constitution § «Git»).
- В этом проходе НЕ делаем: полный перенос в `karaoke-public` (отдельная фича), правки OQ-4/OQ-5/OQ-6/OQ-7/OQ-8 (стилистика, следующие итерации).

---
status: Active
slug: 263-editor-task-review-modal
related:
  - ../features/154-editor-tasks-manage.md
  - ../features/184-approve-status-choice.md
  - ../architecture/L3-components.md
  - ../architecture/webvue3-patterns.md
  - ../../specs/263-editor-task-review-modal/spec.md
---

# 263 — Улучшение модалки проверки задания (LiveDoc)

> Drill-down — [specs/263-editor-task-review-modal/spec.md](../../specs/263-editor-task-review-modal/spec.md).

## Что делает

В админке `webvue3`, в модалке проверки задания редактора (`ReviewModal.vue`) — четыре визуальных улучшения:

1. **Блок «Текст пользователя»** — текст выравнивается по левому краю (`text-align: left`), шрифт управляется через настройки онлайн-редактора (по умолчанию `16px` из `EDITOR_DEFAULTS.textFontSize`, диапазон `6..36`).
2. **Новый блок «Разметка»** — HTML-представление разметки текущего голоса из `formatText()` с палитрой karaoke-public (классы `ke-fx-*` на чёрном фоне). По умолчанию шрифт `18px` (`EDITOR_DEFAULTS.previewFontSize`).
3. **Шрифт** обоих блоков подхватывает настройки редактора из `localStorage` через `loadEditorSettings()`. Один источник правды — `SongKaraokeEditorView.vue` (его слайдер «Шрифт»). **Pass 247**: добавлены слайдеры `<input type="range" min="6" max="36">` прямо в модалке (рядом с блоками «Текст пользователя» и «Разметка») — админ может менять шрифт без открытия редактора. Watcher'ы `textFontSize(v)` / `previewFontSize(v)` пишут в `localStorage['karaoke-editor-settings']` через `saveEditorSettings()`. Слайдеры модалки и редактора синхронизированы через одну переменную.
4. **Блок «Маркеры»** — счётчики «Слоги · Концы строк · Новые строки · END» выстраиваются горизонтально через `flex-direction: row` (заголовок «Маркеры: N» остаётся сверху).
5. **Адаптивная сетка** `.se-cols` — 1 / 2 / 3 колонки на mobile / tablet / desktop (mobile-first, два `@media`-блока на 768px и 1024px). Ширина модалки увеличена с `760px` до `min(96vw, 1100px)`.

**Эффект**: модалка ревью становится визуально единообразной с правой колонкой онлайн-редактора (`SongKaraokeEditorView.vue` / `EditorWorkView.vue`), админу больше не нужно открывать полный редактор только чтобы посмотреть разметку.

**Главное решение (Clarifications Q1, 2026-08-30)**: блок «Разметка» использует ту же палитру `ke-fx-*` на чёрном фоне, что и karaoke-public (`EditorWorkView.vue:1845-1888`). Это упрощает реализацию (`formatText` уже генерирует именно эти классы — см. contracts/README.md) и обеспечивает пиксель-в-пиксель идентичность с онлайн-редактором. Чёрный фон `.se-markup` визуально отделяет «Разметку» от остальной светлой модалки как «плеер-зона» (по аналогии с iframe-плеером `.se-player-wrap`).

**Импорт через границу пакетов**: `formatText` импортируется напрямую из `karaoke-public/src/composables/useKaraokeEditor.js` (`../../../../karaoke-public/...`). Vite разрешает кросс-импорт без дополнительной настройки `vite.config.js` (проверено `npm run build`). Альтернатива `webvue3/src/composables/useKaraokeEditor.js` генерирует `ske-fx-*` классы и НЕ подходит под решение Clarifications.

## User Stories (краткий список)

- **US1** (P1): «Текст пользователя» выровнен по левому краю.
- **US2** (P1): добавлен блок «Разметка» в формате karaoke-public (чёрный фон, `.ke-fx-*`).
- **US3** (P1): размер шрифта обоих блоков из `loadEditorSettings()`.
- **US4** (P1): блок «Маркеры» одной строкой.
- **US5** (P1): адаптивная сетка `.se-cols` 1/2/3 колонки, расширенная ширина модалки.

## Functional Requirements (указатель)

- **FR-001**: CSS `.se-text { text-align: left }` — явное выравнивание по левому краю.
- **FR-002**: template — новый блок `<div class="se-col">` с `<div class="se-markup" v-html="parsedMarkupHtml">`.
- **FR-003**: computed `parsedMarkupHtml = formatText(parsedMarkers, -1)` (текущий слог не подсвечивается — плеер не запущен).
- **FR-004**: `.se-text` использует inline-style `:style="{ fontSize: textFontSize + 'px' }"`.
- **FR-005**: `.se-markup` использует inline-style `:style="{ fontSize: previewFontSize + 'px' }"`.
- **FR-006**: CSS `.se-markup` имеет `background: #000` + `:deep(.ke-fx-…)` палитру из karaoke-public.
- **FR-007**: CSS `.se-marker-summary` — `flex-direction: row; flex-wrap: wrap;` + разделитель `.se-marker-sep`.
- **FR-008**: CSS `.se-cols` — адаптивная сетка 1/2/3 колонки; CSS `.se-modal-wide` — `width: min(96vw, 1100px)`.
- **FR-012** (Pass 246 UX fix, 2026-08-30): CSS `.se-modal` — `max-height: 90vh; overflow-y: auto;`. При развёрнутом плеере (iframe 16:9 + ~110px controls) высота модалки увеличивается; на коротких экранах без этого фикса хедер (`.se-modal-title`) и футер (`.se-modal-btns`) уходят за границы viewport. С `max-height: 90vh` модалка центрируется overlay-flex пока помещается, иначе обрезается до 90vh и появляется вертикальная прокрутка — хедер и футер остаются в потоке документа, достижимы через скролл.
- **FR-013** (Pass 247, 2026-08-30): два слайдера `<input type="range" min="6" max="36">` в template модалки — по одному рядом с блоками «Текст пользователя» (v-model на `textFontSize`) и «Разметка» (v-model на `previewFontSize`). Подпись «Шрифт Npx» показывает текущее значение. Watcher'ы вызывают `saveEditorSettings()` при изменении — слайдеры модалки и редактора пишут в одну `localStorage`-переменную.
- **FR-009**: блок «Разметка» реактивно пересчитывается при `currentVoiceIdx`.
- **FR-010**: `mounted()` вызывает `loadEditorSettings()`, результат пишется в `data().textFontSize`/`previewFontSize`. try/catch защита.
- **FR-011**: логика `markerStats` НЕ меняется (только CSS template).

## Acceptance Criteria

- [ ] **AC1**: `.se-text.textAlign === 'left'` при любой ширине окна ≥768px.
- [ ] **AC2**: блок «Разметка» виден рядом с «Текст пользователя», `getComputedStyle('.se-markup').backgroundColor === 'rgb(0, 0, 0)'`.
- [ ] **AC3**: с дефолтным localStorage `.se-text.fontSize === '16px'`, `.se-markup.fontSize === '18px'`.
- [ ] **AC4**: с `textFontSize=24` / `previewFontSize=30` в localStorage — соответственно `24px`/`30px`.
- [ ] **AC5**: clamp при `textFontSize=100` — не выше `36px`; при `previewFontSize=0` — не ниже `6px`.
- [ ] **AC6**: блок «Маркеры» занимает ОДНУ строку на ширине модалки ≥768px.
- [ ] **AC7**: на ширине <768px — все три блока вертикально; 768–1023px — Текст+Разметка, Маркеры под; ≥1024px — три в строке.
- [ ] **AC8**: изменение шрифта в редакторе (`SongKaraokeEditorView.vue`) сохраняется в localStorage и подхватывается в следующей открытой модалке ревью (AC4 повторно).
- [ ] **AC9**: при `voiceCount > 1` переключение голоса через табы обновляет блок «Разметка» реактивно за <50мс.
- [ ] **AC10** (Pass 246): при высоте окна < естественной высоты модалки (например, развёрнутый плеер на 768p-экране) — модалка обрезается до 90vh, появляется вертикальная прокрутка, `.se-modal-title` и `.se-modal-btns` остаются достижимы через скролл.
- [ ] **AC11** (Pass 247): при движении слайдера `.se-font-slider input[type=range]` (в модалке) мгновенно меняется `font-size` соответствующего блока (`.se-text` или `.se-markup`); при отпускании — значение сохраняется в `localStorage['karaoke-editor-settings']`. Закрытие модалки и повторное открытие — настройка подхватывается. Изменение шрифта в редакторе (`SongKaraokeEditorView.vue`) подхватывается в модалке при следующем открытии (тот же источник правды).

## Связанные LiveDocs

- Feature: [154-editor-tasks-manage.md](../features/154-editor-tasks-manage.md) (предыдущая фича, орг. табл. заданий)
- Feature: [184-approve-status-choice.md](../features/184-approve-status-choice.md) (radio 5/6 в той же модалке)
- Architecture: [L3-components.md](../architecture/L3-components.md), [webvue3-patterns.md](../architecture/webvue3-patterns.md)

## Код

- Frontend: `webvue3/src/components/SongEditor/ReviewModal.vue` — единственный изменяемый файл
- Импорт: `karaoke-public/src/composables/useKaraokeEditor.js` — функции `formatText` + `loadEditorSettings`
- Backward-compat refs: `webvue3/src/composables/useKaraokeEditor.js` (5-я копия для `SongKaraokeEditorView.vue`, `ske-fx-*` — НЕ используется в этой фиче)

## История

- Создан: 2026-08-30
- Последнее обновление: 2026-08-30 (Pass 247: добавлены слайдеры font-size прямо в модалке с watcher'ом на `saveEditorSettings` — DEPRECATED решение Clarifications про out-of-scope слайдера. Pass 246 UX fix: `max-height: 90vh; overflow-y: auto` для `.se-modal`)

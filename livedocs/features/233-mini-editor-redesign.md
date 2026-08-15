---
status: Active
slug: 233-mini-editor-redesign
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../architecture/L3-components.md
  - 163-fix-song-editor-regressions.md
  - 232-admin-song-editor-local-db.md
  - ../../specs/233-mini-editor-redesign/spec.md
  - ../../specs/233-mini-editor-redesign/contracts/ui-layout.md
  - ../../specs/233-mini-editor-redesign/quickstart.md
---

# 233 — Редизайн мини-редактора песен в админке (LiveDoc)

> Drill-down — [specs/233-mini-editor-redesign/spec.md](../../specs/233-mini-editor-redesign/spec.md).
> Контракт UI-разметки — [contracts/ui-layout.md](../../specs/233-mini-editor-redesign/contracts/ui-layout.md).

## Что делает

UI-редизайн мини-редактора песен в admin SPA (`webvue3`, `SongKaraokeEditorView.vue`,
`SongKaraokeEditorModal.vue`). Чисто визуальная перекомпоновка — без изменений
логики, API, схемы БД. Полный перенос в `karaoke-public` — отдельная фича.

### Три визуальные карточки

1. **`PlayerVoiceWaveCard`** (верх): объединяет «Прослушать в плеере»,
   переключатель голосов, вейвформу, бегущую строку и транспорт
   (play/пауза/слайдеры + панель маркер-действий) в один карточный блок
   с общим фоном/обводкой. Лёгкие `border-top` разделители между
   дочерними блоками. **Pass 4 (2026-08-15)**: вейвформа в паритете с
   SubsEdit — фиолетовые бары (`waveColor: rgb(200,0,200)`), таймкоды
   сверху и tooltip с `mm:ss.mmm` через `Hover` plugin, плотные маркеры
   (opacity 0.7).

   **Pass 2 (2026-08-15)**: верхняя строка карточки — слева
   `.ske-voice-tabs` (голоса), справа `.ske-player-toggle` (кнопка
   «Прослушать в плеере»), через `flex space-between` внутри новой
   обёртки `.ske-pv-header`. `.ske-player-wrap` (опционально, по клику)
   вынесен в отдельный блок под строкой.

2. **`TextsArea`** (левая колонка): редактор текста песни. Заголовок
   «Текст песни» + слайдер шрифта + `<textarea>`. Блок кнопок спецтегов
   («Новая строка / Куплет / Припев / Бридж / Приговор / Группа 4 /
   Комментарий…») перенесён внутрь `.ske-keyboard` — виден **только**
   когда `showKeyboard === true`.

3. **`PreviewPanel`** (правая колонка): только preview-блок «Разметка».
   **Pass 2 (2026-08-15)**: панель маркер-действий
   («Показать клавиатуру / Очистить маркеры / Типограф» + `typographError`)
   перенесена в карточку плеера — в `.ske-transport`, **после ползунка
   «Громкость»**. Причина: в Pass 1 панель была в правой колонке, и при
   ширине колонки ~50% кнопка «Типограф» переносилась на вторую строку
   (ломая горизонтальный ряд). В карточке плеера достаточно ширины.
   При ширине окна <1024px правая колонка (preview) сворачивается в
   выдвижную панель (drawer), открывается по кнопке-«бургер».

### Дубль заголовка

Блок `.ske-header` (название, исполнитель, бейдж «Песня») скрыт через
`v-if="false"` — название и исполнитель уже есть в шапке страницы/модалки.
DOM-блок сохранён, чтобы OQ-7 («что показывать в шапке?») можно было
решить в следующей итерации без восстановления шаблона.

### Адаптивность

- **Drawer при `<1024px`**: правая колонка получает `transform: translateX(100%)`,
  кнопка-«бургер` `.ske-drawer-toggle` становится видимой, клик — `translateX(0)`
  + затемнение `.ske-drawer-backdrop`. Состояние — локальный data `rightDrawerOpen`
  (без персистенции).
- **>4 голосов**: `.ske-voice-tabs` получает `flex-wrap: nowrap; overflow-x: auto` —
  горизонтальный скролл без переноса и без скрытия части голосов.

## User Stories (краткий список)

- **US1** (P1): дубль заголовка скрыт (`v-if="false"` на `.ske-header`).
- **US2** (P1): карточный блок `PlayerVoiceWaveCard` с `border-top` разделителями.
- **US3** (P2): спецтеги и клавиатура — единый toggle `showKeyboard`.
- **US4** (P2): панель маркер-действий в правой колонке, над preview.
- **US5** (P3): drawer при `<1024px` + визуальная иерархия 3 карточек.
- **FR-009**: `<1024px` → drawer в правой колонке.
- **FR-010**: `>4 голосов` → горизонтальный скролл.

## Functional Requirements (указатель)

- **FR-001**: `.ske-header` скрыт через `v-if="false"`. DOM-блок сохранён.
- **FR-002**: `PlayerVoiceWaveCard` — единая карточка с общим фоном/обводкой.
- **FR-003**: `.ske-spectag-toolbar` рендерится только при `showKeyboard === true`.
- **FR-004**: `.ske-kb-toolbar` — первый элемент в `.ske-text-col:last-child`.
- **FR-005**: правки только в `SongKaraokeEditorView.vue` / `Modal.vue`;
  `SubsEdit.vue` / `SongEdit.vue` и `mode='assignment'` — без визуальных изменений.
- **FR-006**: JSDoc на default export с `@see` на этот LiveDoc (Constitution VI FR-006).
- **FR-007**: только `webvue3`; `karaoke-public` — отдельная фича.
- **FR-008**: модульный лэйаут для лёгкой итеративной правки.
- **FR-009**: `@media (max-width: 1023.98px)` → drawer в правой колонке.
- **FR-010**: `.ske-voice-tabs` — `flex-wrap: nowrap; overflow-x: auto`.

## Реализация (фактически добавлено/изменено)

- **Новые CSS-классы**: `.ske-player-voice-wave-card` (обёртка карточки плеера);
  `.ske-pv-header` (Pass 2: строка «голоса + кнопка» в верхней части карточки);
  `.ske-drawer-toggle`, `.ske-drawer-backdrop`, `.ske-drawer-open` (responsive).
- **Изменённые CSS**: `.ske-voice-tabs` (nowrap + overflow-x); `.ske-tail-card` /
  `.ske-wave-card` (сняты background/border/border-radius — теперь у родительской карточки);
  `.ske-kb-toolbar` (Pass 2: `flex-wrap: nowrap`, `white-space: nowrap`, уменьшенный padding/font);
  `.ske-tail-card` (Pass 3: `min-height: 0` вместо `96px`, `padding: 0.35rem 0.5rem` вместо `1rem`
  — убрана пустота вокруг одной строки текста бегущей строки).
- **Новый CSS-блок**: `@media (max-width: 1023.98px)` — drawer + grid-template-columns: 1fr.
- **Новое data**: `rightDrawerOpen: false` (без персистенции).
- **Новый method**: `toggleRightDrawer()`.
- **Pass 4 (2026-08-15) — вейвформа, паритет с SubsEdit**:
  - `WaveSurfer.create()` параметры: `height: 200`, `waveColor: rgb(200,0,200)`, `progressColor: rgb(100,0,100)`,
    `cursorColor: rgb(255,0,0)`, `cursorWidth: 3`, `barWidth: 4`, `barRadius: 2`, `barHeight: 1`,
    `autoCenterImmediately: true`.
  - `Hover` plugin подключён (`lineColor: #000`, `lineWidth: 2`, `labelBackground: #555`, `labelColor: #fff`,
    `labelSize: 11px`, `formatTimeCallback` → `mm:ss.mmm`) — даёт таймкоды сверху вейвформы
    и тёмную всплывашку с текущим временем при наведении.
  - Маркеры (`redrawRegions`): `color: hexToRgba(m.color, 0.7)` (было 0.35) — плотнее, ближе к SubsEdit.
- **Pass 5 (2026-08-15) — бейджи слогов, паритет с SubsEdit**:
  - `regionContentEl()` для `markertype === 'syllables'`: убран принудительный `fontSize: 9px` и `padding: 1px 3px` с корневого `<div>`, `fontWeight: 700` → `normal` (Pass 5 v3).
  - На `<span>`-бейдже: `fontSize: 15px`, `display: block; width: fit-content` (Pass 5 v2: не на всю ширину, по ширине текста), `padding: 2px 4px`, `whiteSpace: nowrap`. Цвет фона `beige`, цвет текста `#222` — как в SubsEdit.
- **Pass 6 (2026-08-15) — перенос Pass 2/3/4/5 в `karaoke-public` (адаптированный)**:
  - **`karaoke-public/src/views/EditorWorkView.vue`** (mode='assignment' для исполнителей) получил те же визуальные улучшения, что и `webvue3/SongKaraokeEditorView.vue`:
    - **Pass 2 (адаптированный)**: обёртка `.ke-pv-stack` для группы «плеер + голоса + волна + бегущая + транспорт` (без общего фона/обводки — чтобы не конфликтовать с `--km-card` дизайн-системы public). `.ke-pv-header` (flex space-between) с `.ke-voice-tabs` слева и `.ke-player-toggle` справа. `.ke-kb-toolbar` перенесена в `.ke-transport` после ползунка «Громкость». `.ke-spectag-toolbar` перенесена внутрь `.ke-keyboard` (единый toggle `showKeyboard`).
    - **Pass 3 (адаптированный)**: `.ke-tail-card` — `min-height: 0` (было 96px), `padding: 0.35rem 0.5rem` (было 1rem). Фон/обводка через `--km-card`/`--km-border` оставлены.
    - **Pass 4**: в `initWaveSurfer()` — паритет с admin/SubsEdit: `height: 200`, фиолетовые цвета, `cursorWidth: 3`, `barWidth: 4`, `barRadius: 2`, `barHeight: 1`. `Hover` plugin подключён с `formatTimeCallback` → `mm:ss.mmm`. Маркеры opacity 0.35 → 0.7.
    - **Pass 5**: `regionContentEl()` — бейджи слогов `fontSize: 15px`, `display: block; width: fit-content`, `fontWeight: normal`, `padding: 2px 4px`.
  - Что НЕ перенесено: Pass 1 (заголовок) — у `karaoke-public` другая структура шапки (`ke-header-inner`/`ke-header-title` — это часть дизайна public, не дубликат). Также НЕ перенесён drawer (`@media max-width: 1023.98px`) — public имеет свою responsive-логику через `km-page` и `--km-*` переменные.
  - `regionContentEl()` для `markertype === 'syllables'`: убран принудительный `fontSize: 9px` и `padding: 1px 3px` с корневого `<div>`, добавлены `lineHeight: 1.1`, `overflow: hidden`, `textOverflow: ellipsis` для длинных слогов.
  - На `<span>`-бейдже: `fontSize: 15px` (вместо 9px), `display: flex; align-items: center; justify-content: center; width: 100%; padding: 2px 4px; whiteSpace: nowrap; overflow: hidden; textOverflow: ellipsis`. Бейдж растягивается на всю ширину региона и центрируется. Цвет фона `beige`, цвет текста `#222`, font-weight `700` — как в SubsEdit.
- **DOM-перемещения (Pass 1 + Pass 2)**:
  - `.ske-spectag-toolbar`: всегда видимый → внутрь `.ske-keyboard`, единый toggle.
  - `.ske-kb-toolbar`: Pass 1 — из-под плеера в правую колонку над preview; **Pass 2** — обратно в `.ske-transport`, после ползунка «Громкость».
  - Pass 2: `.ske-voice-tabs` и `.ske-player-toggle` объединены в строку `.ske-pv-header` (space-between, голоса слева, кнопка справа).
- **JSDoc**: default export в `SongKaraokeEditorView.vue` обновлён — `@see livedocs/features/233-mini-editor-redesign.md` (Constitution VI FR-006).
- **Линт**: `eslint` — 0 errors, 0 warnings. **Build**: `npm run build` — успешная сборка.

## Acceptance Criteria (быстрая сводка)

- **SC-001**: `.ske-header` не отрендерен в DOM при открытии мини-редактора.
- **SC-002**: `PlayerVoiceWaveCard` объединяет 5 блоков (player/voices/wave/tail/transport).
- **SC-003**: спецтеги скрыты, toggle «Показать клавиатуру» показывает и клавиатуру, и спецтеги.
- **SC-004**: панель маркер-действий — над preview в правой колонке.
- **SC-005**: 0 регрессий 232 (LOCAL-БД для `mode='song'`) и 163 (спецтеги).
- **SC-006**: 0 функциональных изменений в `mode='assignment'` и в полном прод-редакторе.

## Связанные артефакты

- Спека: [spec.md](../../specs/233-mini-editor-redesign/spec.md) (10 FR, 5 US, 5 closed OQ).
- Plan: [plan.md](../../specs/233-mini-editor-redesign/plan.md).
- Research: [research.md](../../specs/233-mini-editor-redesign/research.md) (7 решений R-1..R-7).
- Data model: [data-model.md](../../specs/233-mini-editor-redesign/data-model.md) (4 layout-сущности).
- UI-layout contract: [contracts/ui-layout.md](../../specs/233-mini-editor-redesign/contracts/ui-layout.md).
- Quickstart (ручная валидация): [quickstart.md](../../specs/233-mini-editor-redesign/quickstart.md).
- Tasks: [tasks.md](../../specs/233-mini-editor-redesign/tasks.md).
- Смежные LiveDoc'и: [163](163-fix-song-editor-regressions.md) (регрессии мини-редактора),
  [232](232-admin-song-editor-local-db.md) (LOCAL-БД).

## Что НЕ входит в этот проход

- Полный перенос в `karaoke-public` (отдельная фича, см. FR-007).
- Стилистические OQ-4 (визуальный разделитель preview), OQ-5 (порядок кнопок),
  OQ-6 (табы маркер↔текст), OQ-7 (формат шапки), OQ-8 (зеркало в public) —
  следующие итерации пользователя.
- Полный редактор на проде (`SubsEdit.vue` / `SongEdit.vue`) — не затрагивается.
- Функциональные изменения (LOCAL-БД, автосохранение, спецтеги) — это спекa 232 / 163.

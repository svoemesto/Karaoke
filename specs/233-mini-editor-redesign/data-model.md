# Data Model: Мини-редактор — редизайн

**Branch**: `233-mini-editor-redesign` | **Date**: 2026-08-15

> **Важно:** это UI-only фича. Доменных сущностей (БД, API) НЕ добавляется и НЕ изменяется. Ниже описаны **визуальные layout-сущности** (группы блоков) и **runtime-состояние компонента**.

## Визуальные layout-сущности

Мини-редактор песен в admin SPA рендерится в одном Vue-компоненте `SongKaraokeEditorView.vue`. После редизайна он состоит из **3 визуальных карточек** + опционального `drawer`-обёртки.

### LayoutCard: `PlayerVoiceWaveCard`

| Атрибут | Значение |
|---------|----------|
| **Что это** | Верхняя карточка: «Прослушать в плеере» + переключатель голосов + вейвформа + бегущая строка + транспорт (play/пауза/слайдеры). |
| **Внутри** | `ske-player-toggle` (кнопка «Прослушать в плеере»), `ske-voice-tabs` (голоса), `ske-wave-card` (вейвформа), `ske-tail-card` (бегущая строка), `ske-transport` (play/слайдеры). |
| **Поведение** | Цельный визуальный блок с общим фоном/обводкой. При >4 голосах — горизонтальный скролл внутри `ske-voice-tabs`. |
| **Опционально** | `ske-player-wrap` с inline-плеером (рендерится по клику «Прослушать в плеере»). |
| **CSS-класс** | `.ske-player-voice-wave-card` (новый, обёртка). |
| **Связан с** | `mode='song'` и `mode='assignment'` (одинаковый layout). |
| **Edge cases** | >4 голосов → горизонтальный скролл в `ske-voice-tabs`. 0 голосов невозможно (минимум 1 всегда создаётся в `loadVoicesFromProps`). |

### LayoutCard: `TextsArea` (центральная/левая колонка)

| Атрибут | Значение |
|---------|----------|
| **Что это** | Левая колонка двухколоночного лэйаута: редактор текста песни + панель спецтегов. |
| **Внутри** | `ske-col-header` («Текст песни» + слайдер шрифта), `ske-spectag-toolbar` (спецтеги — виден **только при `showKeyboard === true`**, см. R-3), `ske-textarea` (собственно редактор). |
| **Поведение** | Всегда виден при `width >= 1024px`. При `<1024px` остаётся в основной части страницы; правая колонка (`PreviewPanel`) сворачивается в drawer. |
| **CSS-классы** | `.ske-text-col` (существующий) — `:first-child`. |
| **Связан с** | `mode='song'` (для `mode='assignment'` — без изменений, см. FR-005). |
| **Edge cases** | Песня без маркеров → `showKeyboard` toggle всё равно работает, спецтеги вставляются как текст. |

### LayoutCard: `PreviewPanel` (правая колонка, в drawer при <1024px)

| Атрибут | Значение |
|---------|----------|
| **Что это** | Правая колонка: только preview-блок «РАЗМЕТКА». Панель маркер-действий в Pass 2 (2026-08-15) перенесена в карточку плеера. |
| **Внутри (сверху вниз)** | `ske-col-header` («Разметка» + слайдер шрифта), `ske-preview` (HTML-рендер разметки). |
| **Поведение** | Виден при `width >= 1024px`. При `<1024px` — `transform: translateX(100%)` + `rightDrawerOpen: true` → `translateX(0)` с backdrop. |
| **CSS-классы** | `.ske-text-col` (существующий) — `:last-child` + `.ske-preview-panel` (опционально, для селектора drawer). |
| **Связан с** | `mode='song'` и `mode='assignment'` (одинаковый layout). |
| **Edge cases** | Песня без маркеров → preview-блок всё равно отрисовывается, но без маркеров. |

### LayoutShell: `EditorPage` (корневой контейнер)

| Атрибут | Значение |
|---------|----------|
| **Что это** | Корневой `<div class="ske-page">` (существующий). Содержит все 3 карточки + опциональный `ske-drawer-backdrop` при `<1024px`. |
| **Поведение** | Без изменений по сравнению с текущим кодом. |
| **CSS-классы** | `.ske-page` (существующий). |

## Runtime-состояние (data/computed)

| Поле | Где живёт | Тип | Существующее/Новое | Описание |
|------|-----------|-----|---------------------|----------|
| `voices` | `data()` | `Array<{ sourceText, markers, syllables }>` | Существующее | Голоса песни. |
| `currentVoiceIdx` | `data()` | `Number` | Существующее | Активный голос. |
| `showKeyboard` | `data()` + `saveEditorSettings` | `Boolean` | Существующее | Toggle клавиатуры + спецтегов (R-3). |
| `showPlayer` | `data()` | `Boolean` | Существующее | Показывать ли inline-плеер. |
| `typographError` | `data()` | `String \| null` | Существующее | Ошибка типографа. |
| `rightDrawerOpen` | `data()` | `Boolean` | **Новое** (R-5) | Открыт ли drawer правой колонки при `<1024px`. Персистенция не требуется (только runtime). |
| `textFontSize`, `previewFontSize` | `data()` | `Number` | Существующее | Размеры шрифта. |
| `playbackRate`, `zoom`, `volume`, `activeSound` | `data()` | — | Существующее | Параметры плеера. |
| `formattedTextHtml` | `computed` | `String` | Существующее | HTML-разметка для preview. |
| `hasMarkers` | `computed` | `Boolean` | Существующее | Есть ли маркеры хотя бы у одного голоса. |

**Новые методы** (минимально):

| Метод | Описание |
|-------|----------|
| `toggleRightDrawer()` | Инвертирует `rightDrawerOpen`. Вызывается по клику на `.ske-drawer-toggle` (новая кнопка-«бургер», видимая только при `<1024px`). |
| Опционально: `watch` на `window.resize` | При ресайзе обратно к `>=1024px` — `rightDrawerOpen = false` (иначе drawer «залипнет»). Или — полагаться на CSS media-query: при `>=1024px` drawer всегда `display: none` независимо от `rightDrawerOpen`. **Рекомендация: CSS-only** (R-5). |

## Валидации

UI-фича — валидаций «формы» нет. Но есть поведенческие инварианты, которые MUST сохраняться:

| Инвариант | Где проверяется |
|-----------|-----------------|
| При `showKeyboard = true` видны и `ske-keyboard`, и `ske-spectag-toolbar`. | Шаблон: оба блока внутри одного `v-if`. |
| При `showKeyboard = false` НЕ видны ни `ske-keyboard`, ни `ske-spectag-toolbar`. | То же. |
| Кнопки «Показать клавиатуру / Очистить маркеры / Типограф» всегда видны в карточке плеера, в `.ske-transport` после ползунка «Громкость» (Pass 2, 2026-08-15). | Шаблон: `ske-kb-toolbar` встроен в `.ske-transport` после `ske-sliders`. |
| `rightDrawerOpen` влияет только на drawer (CSS `transform`), не на доступность preview-кнопок. | Шаблон: правая колонка (preview) внутри drawer'а. |
| При `width >= 1024px` drawer визуально отсутствует независимо от `rightDrawerOpen`. | CSS media-query. |
| При `width < 1024px` drawer открывается по клику; backdrop кликабелен для закрытия. | CSS + `@click="rightDrawerOpen = false"` на backdrop. |

## Связи с другими подсистемами

- **LOCAL-БД (`232`)**: никак не затрагивается — все запросы остаются в `SongKaraokeEditorView.vue` без изменений.
- **Спецтеги (`163`)**: функции `onInsertSpecTag`, `onInsertSpecTagComment`, `syncMarkersFromSpecTags` остаются как есть; редизайн перемещает только кнопки в шаблоне.
- **Vuex store `SongEditor/store.js`**: без изменений (нет новых actions/getters).
- **Per-feature LiveDoc**: создаётся `livedocs/features/233-mini-editor-redesign.md` со ссылками на эту data-model и на смежные LiveDoc'и.

## Что НЕ меняется (для ясности)

- Схема БД (`tbl_songs`, `tbl_song_assignments`).
- API-эндпоинты (`/api/songeditor/*`).
- Авторизация и `assignmentsTarget`.
- Полный редактор на проде (`SubsEdit.vue` / `SongEdit.vue`).
- `karaoke-public` (отдельная фича).

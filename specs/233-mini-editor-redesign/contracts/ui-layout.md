# UI Layout Contract: Мини-редактор — редизайн

**Branch**: `233-mini-editor-redesign` | **Date**: 2026-08-15

> Этот контракт фиксирует **наблюдаемое поведение** UI мини-редактора, на которое могут полагаться тесты (визуальные проверки, в перспективе — snapshot-тесты) и сторонние разработчики. Контракт описывает **что** видно и **где**, без привязки к конкретным CSS-свойствам (только имена классов, которые разработчик сохраняет как «публичный API» стилей).

## 1. Структура DOM

После редизайна DOM-структура `SongKaraokeEditorView.vue` (упрощённо):

```text
.ske-page                                          [корневой контейнер, без изменений]
  ├─ .ske-drawer-backdrop                          [НОВОЕ: видим только при width<1024px и rightDrawerOpen=true]
  ├─ .ske-player-voice-wave-card                   [НОВОЕ: карточка плеер+голоса+волна]
  │   ├─ .ske-player-toggle                        [без изменений]
  │   │   └─ button "Прослушать в плеере"
  │   ├─ .ske-player-wrap                          [опционально, без изменений]
  │   │   └─ .ske-player-container
  │   ├─ .ske-voice-tabs                            [без изменений по содержимому; горизонтальный скролл при >4]
  │   │   ├─ button.ske-voice-tab × N
  │   │   ├─ button.ske-voice-tab-add              [+ Голос]
  │   │   └─ button.ske-voice-tab-remove           [✕ Удалить голос N]
  │   ├─ .ske-wave-card                             [без изменений]
  │   │   ├─ .ske-waveform
  │   │   └─ .ske-time
  │   ├─ .ske-tail-card                             [без изменений]
  │   │   └─ .ske-tail-line
  │   └─ .ske-transport                             [без изменений]
  │       ├─ .ske-tbtn (⏮ ▶ ⏭)
  │       └─ .ske-sliders (скорость, масштаб, гем, громкость)
  │
  ├─ .ske-texts                                     [двухколоночный контейнер, БЕЗ изменений]
  │   ├─ .ske-text-col:first-child                  [Левая колонка = TextsArea]
  │   │   ├─ .ske-col-header
  │   │   │   ├─ .ske-col-title "Текст песни"
  │   │   │   └─ .ske-font-slider
  │   │   └─ .ske-textarea
  │   │   [ske-spectag-toolbar ПЕРЕМЕЩЁН внутрь .ske-keyboard]
  │   │
  │   └─ .ske-text-col:last-child                   [Правая колонка = PreviewPanel + MarkerActions]
  │       ├─ .ske-kb-toolbar                        [ПЕРЕМЕЩЁН сюда из старого места]
  │       │   ├─ button "Показать клавиатуру / Скрыть клавиатуру"
  │       │   ├─ button "Очистить маркеры"
  │       │   ├─ button "Типограф"
  │       │   └─ .ske-typograph-error
  │       ├─ .ske-col-header
  │       │   ├─ .ske-col-title "Разметка"
  │       │   └─ .ske-font-slider
  │       └─ .ske-preview
  │
  └─ .ske-keyboard (v-if="canEdit && showKeyboard") [без изменений по структуре; +ske-spectag-toolbar]
      ├─ .ske-spectag-toolbar                       [ПЕРЕМЕЩЁН сюда из TextsArea]
      │   ├─ button "¶ Новая строка"
      │   ├─ button "Куплет"
      │   ├─ button "Припев"
      │   ├─ button "Бридж"
      │   ├─ button "Приговор"
      │   ├─ button "Группа 4"
      │   └─ button "Комментарий…"
      └─ .ske-kb-grid
          └─ .ske-kb-row × 3
              └─ button.ske-kb-key × N
```

**Важно:** блок `.ske-header` (с названием, исполнителем, бейджем статуса) в этом проходе **скрыт через `v-if="false"`** (R-7). В DOM он остаётся, но не рендерится. Это позволяет быстро вернуть его в следующей итерации после решения OQ-7.

## 2. CSS-классы — публичный контракт

Следующие классы MUST сохраняться как «публичные» (другие компоненты / стили могут на них полагаться):

### Сохраняемые (без изменений)

- `.ske-page` — корневой.
- `.ske-header`, `.ske-header-inner`, `.ske-h-song`, `.ske-h-author`, `.ske-header-right` — скрыты `v-if`, но классы остаются.
- `.ske-player-toggle`, `.ske-player-wrap`, `.ske-player-container`.
- `.ske-voice-tabs`, `.ske-voice-tab`, `.ske-voice-tab-active`, `.ske-voice-tab-add`, `.ske-voice-tab-remove`.
- `.ske-wave-card`, `.ske-waveform`, `.ske-time`.
- `.ske-tail-card`, `.ske-tail-line`, `.ske-tail-begin`, `.ske-tail-curr`, `.ske-tail-next`, `.ske-tail-end`.
- `.ske-transport`, `.ske-tbtn`, `.ske-tbtn-play`, `.ske-sliders`, `.ske-slider`, `.ske-sound-toggle`, `.ske-sound-btn`, `.ske-sound-btn-active`.
- `.ske-kb-toolbar`, `.ske-kb-toggle`, `.ske-typograph-error`.
- `.ske-keyboard`, `.ske-kb-grid`, `.ske-kb-row`, `.ske-kb-key`, `.ske-kb-key-active`, `.ske-kb-key-inactive`, `.ske-kb-key-spacer`, `.ske-kb-key-label`, `.ske-kb-key-caption`.
- `.ske-texts`, `.ske-text-col`, `.ske-col-header`, `.ske-col-title`, `.ske-font-slider`, `.ske-textarea`, `.ske-preview`.
- `.ske-spectag-toolbar`, `.ske-spectag-btn`.
- `.ske-btn`, `.ske-btn-ghost` (утилиты).
- `.ske-badge`, `.ske-badge-*` (бейджи статуса).
- `.ske-reject-banner`.

### Новые

- `.ske-player-voice-wave-card` — обёртка для карточки «плеер + голоса + волна» (R-2). Стили: общий фон, обводка, скруглённые углы; дочерние `.ske-player-toggle`, `.ske-voice-tabs`, `.ske-wave-card` — без собственного фона, разделитель через `border-top` (кроме первого).
- `.ske-drawer-backdrop` — затемнение фона при открытом drawer'е в мобильном режиме. Стили: `position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 9`. Видим только при `@media (max-width: 1023.98px)`.
- `.ske-drawer-toggle` — кнопка-«бургер» для открытия drawer'а, видна **только при `<1024px`**. Размещается в `.ske-player-voice-wave-card` рядом с `ske-voice-tabs` (или в другом удобном месте — по результатам визуальной примерки).
- `.ske-preview-panel` (опционально) — обёртка для правой колонки, чтобы можно было одной строкой CSS скрыть её в drawer.

## 3. Состояние

### Существующее (без изменений)

- `data.showKeyboard: Boolean` — управляет видимостью `.ske-keyboard` и (теперь) `.ske-spectag-toolbar`. Персистится через `saveEditorSettings` (Vuex + localStorage).
- `data.showPlayer: Boolean` — управляет видимостью `.ske-player-wrap`. **Без изменений.**

### Новое

- `data.rightDrawerOpen: Boolean = false` — состояние drawer'а при `<1024px`. НЕ персистится (только runtime). Управляется методом `toggleRightDrawer()`. Сбрасывается в `false` при ресайзе окна обратно к `>=1024px` (опционально, через `watch` — или достаточно CSS media-query, см. R-5).

### Методы

- `toggleRightDrawer()` (новое) — инвертирует `rightDrawerOpen`. Вызывается по клику на `.ske-drawer-toggle`.
- `closeRightDrawer()` (новое, опционально) — устанавливает `rightDrawerOpen = false`. Вызывается по клику на `.ske-drawer-backdrop`.

## 4. Поведение по breakpoint'ам

| Breakpoint | Поведение |
|------------|-----------|
| `width >= 1024px` | Двухколоночный лэйаут. `rightDrawerOpen` игнорируется. `.ske-drawer-backdrop` и `.ske-drawer-toggle` `display: none`. |
| `width < 1024px` | Правая колонка скрыта через `transform: translateX(100%)`. `.ske-drawer-toggle` видим. При `rightDrawerOpen === true` — `transform: translateX(0)`, `.ske-drawer-backdrop` видим и кликабелен. |

## 5. Совместимость с `232` и `163`

| Что | Где в коде | Почему не сломается |
|-----|------------|---------------------|
| `onInsertSpecTag`, `onInsertSpecTagComment` | `<script>` methods | Не трогаем. Кнопки `.ske-spectag-btn` остаются привязаны к ним через `@click`. |
| `syncMarkersFromSpecTags` | `<script>` import | Не трогаем. |
| `saveEditorSettings({ showKeyboard })` | `watch` на `showKeyboard` | Не трогаем. Поведение toggle'а сохранено. |
| `loadVoicesFromProps`, `setCurrentVoice`, `addVoice`, `removeLastVoice` | `<script>` methods | Не трогаем. `ske-voice-tabs` остался без изменений. |
| `currentVoiceData`, `markers`, `sourceText` (computed) | `<script>` computed | Не трогаем. |
| Inline-плеер `KaraokePlayer` | `ske-player-wrap` | Не трогаем. |
| Автосохранение (через `editSave` API) | `<script>` debounce | Не трогаем — это `232`-story, не задеваем. |

## 6. Что НЕ меняется (контракт «не трогать»)

- API мини-редактора: пропсы `songId`, `songName`, `author`, `sourceTextList`, `markersPerVoice`, `mode` и т.д. — **без изменений**.
- Эмитты событий (`save`, `cancel`) — **без изменений**.
- Логика `mode='assignment'` — **без изменений** (только скрытие/показ блоков через CSS не должно ломать её).
- Полный редактор на проде (`SubsEdit.vue` / `SongEdit.vue`) — **не затрагивается**.

## 7. Совместимость с `karaoke-public` (отдельная фича)

Этот контракт — admin-only. При переносе в `karaoke-public` будут свои отличия:
- В `karaoke-public` нет `mode='assignment'`.
- В `karaoke-public` нет редактирования текста (только просмотр), поэтому `.ske-textarea` и `.ske-spectag-toolbar` не нужны.
- В `karaoke-public` — другой дизайн-система (CSS-переменные `--km-*`, два дизайна `classic`/`modern`).

Но CSS-классы (`.ske-player-voice-wave-card`, `.ske-drawer-*` и т.д.) могут быть переиспользованы как naming convention.

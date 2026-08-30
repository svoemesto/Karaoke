# Contracts — фича 263

Фича **263-editor-task-review-modal** НЕ вводит новых внешних API-контрактов (REST endpoints, GraphQL-схем, очередей, файловых форматов). Серверная часть остаётся неизменной (см. [data-model.md](../data-model.md) — раздел «Сущности БД/API — без изменений»).

Внутренний контракт один — функция `formatText`, которая уже определена в `karaoke-public/src/composables/useKaraokeEditor.js` и переиспользуется.

## Контракт `formatText(markers, curMarkerIndex)`

**Назначение**: Генерация HTML-представления разметки песни (для блока «Разметка»).

**Расположение источника**: `karaoke-public/src/composables/useKaraokeEditor.js:447`

**Сигнатура**:
```js
formatText(markers: Marker[], curMarkerIndex: number): string
```

**Параметры**:
- `markers: Marker[]` — массив маркеров разметки текущего голоса (`parsedMarkers` в `ReviewModal.vue`).
- `curMarkerIndex: number` — индекс текущего маркера для подсветки (`-1` в `ReviewModal.vue` = никто не подсвечен; см. FR-003).

**Возвращает**: HTML-строку, безопасную для `v-html`:
- `<span class="ke-fx-groupN">${syllable}</span>` для каждого слога (N ∈ {0,1,2,3}).
- `<span class="ke-fx-cur">${syllable}</span>` для слога на индексе `curMarkerIndex` (если `>= 0`).
- `<span class="ke-fx-comment">${text}</span><br>` для каждого `COMMENT|...` маркера.
- `<br>` для `endofline` и `newline` маркеров.

**Использование в `ReviewModal.vue`**:
```js
import { formatText } from '../../../../karaoke-public/src/composables/useKaraokeEditor'
// ...
computed: {
  parsedMarkupHtml() {
    return formatText(this.parsedMarkers, -1)
  },
  // ...
}
```

**Прецедент**: `webvue3/src/components/SongEditor/SongKaraokeEditorView.vue:335, 515` — та же функция, тот же источник.

**Fallback** (если Vite не разрешит межпакетный импорт): копия `formatText` в `webvue3/src/components/SongEditor/useReviewModalFormat.js` с явной ссылкой на источник (`@see`).

## Что НЕ входит в scope contracts

- ❌ Новые REST/GraphQL endpoints.
- ❌ Новые схемы БД / миграции.
- ❌ Новые WebSocket/SSE-события.
- ❌ Новые CLI-команды или скрипты.
- ❌ Новые npm-пакеты (deps или peer).
- ❌ Новые файлы конфигурации (vite/webpack/tsconfig).

Все изменения локализованы в `webvue3/src/components/SongEditor/ReviewModal.vue` (см. [data-model.md](../data-model.md) для деталей).
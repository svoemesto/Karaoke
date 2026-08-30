# Phase 1 — Data Model: 263 — Улучшение модалки проверки задания

**Date**: 2026-08-30
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)
**Research**: [research.md](./research.md)

## Цель

Описать модель данных **только на уровне Vue-компонента** `ReviewModal.vue`, поскольку фича фронтенд-only и не вводит новых сущностей БД, API, DTO. Серверная модель `SongAssignment` (см. `webvue3/src/components/SongEditor/store.js`) остаётся без изменений.

## ReviewModal (Vue-компонент) — расширенная модель

### Data (state)

| Поле | Тип | Источник | Изменение |
|---|---|---|---|
| `comment` | `string` | `data()` (уже есть) | Без изменений |
| `busy` | `boolean` | `data()` (уже есть) | Без изменений |
| `message` | `string` | `data()` (уже есть) | Без изменений |
| `isError` | `boolean` | `data()` (уже есть) | Без изменений |
| `showPlayer` | `boolean` | `data()` (уже есть) | Без изменений |
| `currentVoiceIdx` | `number` | `data()` (уже есть) | Без изменений |
| `playerHeight` | `number` | `data()` (уже есть) | Без изменений |
| `selectedIdStatus` | `5 \| 6` | `data()` (уже есть, feature 184) | Без изменений |
| **`textFontSize`** ✨ | `number` (6..36) | `mounted()` → `loadEditorSettings().textFontSize` | **НОВОЕ** (FR-004, FR-010) |
| **`previewFontSize`** ✨ | `number` (6..36) | `mounted()` → `loadEditorSettings().previewFontSize` | **НОВОЕ** (FR-005, FR-010) |

### Computed

| Computed | Тип | Источник | Изменение |
|---|---|---|---|
| `a` | `SongAssignment \| null` | `getAssignmentCurrent` getter | Без изменений |
| `isRemoteView` | `boolean` | `getAssignmentsTarget === 'remote'` | Без изменений |
| `targetForEditor` | `'local' \| 'remote'` | `getAssignmentsTarget` | Без изменений |
| `playerSrc` | `string` | computed из `a` + target | Без изменений |
| `voiceCount` | `number` | `a.draftMarkersPerVoice.length` | Без изменений |
| `currentSourceText` | `string` | `a.draftSourceTexts[currentVoiceIdx]` | Без изменений |
| `parsedMarkers` | `Marker[]` | `a.draftMarkersPerVoice[currentVoiceIdx]` | Без изменений |
| `markerCount` | `number` | `parsedMarkers.length` | Без изменений |
| `markerStats` | `{syllables, endofline, newline, end}` | reduce по `parsedMarkers` | Без изменений (FR-011) |
| `songIdStatus` | `number \| null` | `a.idStatus` | Без изменений |
| `canChooseIdStatus` | `boolean` | `songIdStatus !== null` | Без изменений |
| **`parsedMarkupHtml`** ✨ | `string` | `formatText(parsedMarkers, -1)` | **НОВОЕ** (FR-002, FR-003, FR-009) |
| **`curMarkerIndexInMarkup`** ✨ | `number` (константа `-1`) | литерал | **НОВОЕ** (FR-003) |

### Methods (без изменений)

- `statusLabel(s)` — без изменений.
- `idStatusLabel(s)` — без изменений (feature 184).
- `doApprove()`, `doReject()`, `doRevoke()` — без изменений (feature 184).
- `observeWrapAndFit()`, `fitPlayerTo16x9()` — без изменений (плеер).

### Lifecycle hooks

| Хук | Изменение |
|---|---|
| `mounted()` | **+вызов `loadEditorSettings()`** (FR-010), результат пишется в `this.textFontSize` / `this.previewFontSize`. Если `loadEditorSettings()` бросит (теоретически не должен — внутри `try/catch`) — `EDITOR_DEFAULTS` уже применены внутри, `this.textFontSize = 16; this.previewFontSize = 18;` как fallback. |
| `beforeUnmount()` | Без изменений. |

## Template — структура изменений

Текущий template (фрагмент из `ReviewModal.vue:70-84`):

```vue
<div class="se-cols">
  <div class="se-col">
    <div class="se-col-title">Текст пользователя</div>
    <pre class="se-text">{{ currentSourceText || '(пусто)' }}</pre>
  </div>
  <div class="se-col">
    <div class="se-col-title">Маркеры: {{ markerCount }}</div>
    <div class="se-marker-summary">
      <div>Слоги: {{ markerStats.syllables }}</div>
      <div>Концы строк: {{ markerStats.endofline }}</div>
      <div>Новые строки: {{ markerStats.newline }}</div>
      <div>END: {{ markerStats.end ? 'есть' : 'нет' }}</div>
    </div>
  </div>
</div>
```

После изменений (FR-001..FR-009):

```vue
<div class="se-cols">
  <!-- Текст пользователя -->
  <div class="se-col">
    <div class="se-col-title">Текст пользователя</div>
    <pre
      class="se-text"
      :style="{ fontSize: textFontSize + 'px' }"
    >{{ currentSourceText || '(пусто)' }}</pre>
  </div>
  <!-- Разметка (НОВОЕ) -->
  <div class="se-col se-col-markup">
    <div class="se-col-title">Разметка</div>
    <div
      class="se-markup"
      :style="{ fontSize: previewFontSize + 'px' }"
      v-html="parsedMarkupHtml || '(пусто)'"
    />
  </div>
  <!-- Маркеры (одной строкой) -->
  <div class="se-col">
    <div class="se-col-title">Маркеры: {{ markerCount }}</div>
    <div class="se-marker-summary">
      <span>Слоги: <strong>{{ markerStats.syllables }}</strong></span>
      <span class="se-marker-sep">·</span>
      <span>Концы строк: <strong>{{ markerStats.endofline }}</strong></span>
      <span class="se-marker-sep">·</span>
      <span>Новые строки: <strong>{{ markerStats.newline }}</strong></span>
      <span class="se-marker-sep">·</span>
      <span>END: <strong>{{ markerStats.end ? 'есть' : 'нет' }}</strong></span>
    </div>
  </div>
</div>
```

Ключевые отличия:
1. `<pre class="se-text">` получает `:style="{ fontSize: ... }"` (FR-004).
2. **Добавлен новый `.se-col-markup`** с `<div class="se-markup" v-html>` (FR-002).
4. `.se-marker-summary` меняет `<div>` на `<span>` + разделители `.se-marker-sep` (FR-007, горизонтальный flex).

## CSS — изменения в `<style scoped>`

Существующие правила, которые нужно обновить/добавить:

```css
/* Обновлено (FR-001): явный text-align: left для .se-text */
.se-text {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem;
  /* font-size: 0.82rem; ← УБРАНО, теперь через :style */
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  margin: 0;
  font-weight: 400;
  text-align: left;  /* ← ДОБАВЛЕНО */
}

/* Новый блок (FR-006) */
.se-markup {
  background: #000;
  border-radius: 8px;
  padding: 0.6rem;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  /* font-size — через :style */
  font-weight: 400;
  text-align: left;
}

/* Палитра (FR-006) — копия из karaoke-public EditorWorkView.vue:1861-1888 */
.se-markup :deep(.ke-fx-cur) {
  color: #ff0000;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group0) {
  color: #ffffff;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group1) {
  color: #ffff00;
  font-style: italic;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group2) {
  color: #00bfff;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group3) {
  color: #00ff00;
  font-style: italic;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-comment) {
  color: #d2691e;
  font-size: 0.78em;
  font-style: italic;
  font-weight: bolder;
}

/* Обновлено (FR-007): горизонтальная компоновка */
.se-marker-summary {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem;
  /* font-size: 0.85rem; ← оставлено (нет требования менять) */
  display: flex;
  flex-direction: row;       /* ← было column */
  flex-wrap: wrap;            /* ← добавлено */
  gap: 0.3rem 0.8rem;         /* ← row-gap column-gap */
  align-items: baseline;      /* ← добавлено для выравнивания счётчиков */
  font-weight: 400;
}
.se-marker-sep {
  color: #aaa;
  font-weight: 400;
}

/* Обновлено (FR-008): адаптивная сетка 1/2/3 колонки */
.se-cols {
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;                       /* mobile */
}
@media (min-width: 768px) {
  .se-cols {
    grid-template-columns: 1fr 1fr;                 /* tablet: text + markup, markers под */
  }
  /* На планшете Маркеры — на всю ширину под первыми двумя */
  .se-cols .se-col:last-child {
    grid-column: 1 / -1;
  }
}
@media (min-width: 1024px) {
  .se-cols {
    grid-template-columns: 1fr 1fr 1fr;             /* desktop: 3 колонки */
  }
  .se-cols .se-col:last-child {
    grid-column: auto;                              /* на десктопе все три в одной строке */
  }
}

/* Обновлено (FR-008): увеличенная ширина модалки */
.se-modal-wide {
  width: min(96vw, 1100px);  /* было 760px */
}
```

## Валидация / инварианты

1. **`parsedMarkupHtml` всегда возвращает валидный HTML** (или пустую строку). Если `parsedMarkers.length === 0` — пустая строка → рендерится `(пусто)`.
2. **`textFontSize` / `previewFontSize` всегда в диапазоне 6..36** (clamp в `loadEditorSettings`, FR-004 acceptance #4).
3. **`currentVoiceIdx < voiceCount`** (инвариант уже существует — `.se-voice-tabs` рендерится только при `voiceCount > 1`).
4. **`curMarkerIndexInMarkup === -1`** (константа) — НИКОГДА не подсвечивается текущий слог, как согласованно в Clarifications.

## Сущности БД/API — без изменений

Фича не вводит:
- Новых таблиц.
- Новых колонок.
- Новых endpoints.
- Новых DTO.
- Изменений в Vuex store.

`SongAssignment`, `Marker`, `Song` (idStatus) — без изменений.
# Data Model: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

**Дата**: 2026-09-03
**Связанная спека**: [spec.md](spec.md)
**Связанное research**: [research.md](research.md)

Этот документ описывает **client-side state** модалки поиска текста и связанных компонентов. Изменений в БД нет; фикс целиком на клиенте.

## Entities (client-side state)

### 1. SearchText Component State

Состояние модалки `webvue3/src/components/Songs/edit/SearchText.vue` (Vue2 Options API).

| Поле | Тип | Источник | Описание |
|------|-----|----------|----------|
| `currentSearchAsync` | `SearchAsync \| undefined` | backend `/api/searchAsync` | Текущая async-задача поиска (id, query, done). |
| `searchResultsList` | `Array<SearchResult>` | backend `/api/searchResults` | Список результатов поиска (для текущего `currentSearchAsync`). |
| `currentResult` | `SearchResult \| undefined` | UI / клик по `<search-text-results-table>` | Текущий выбранный результат. |
| `isExtractingLyrics` | `boolean` | UI | Флаг «идёт HTTP-запрос за текстом» (FR-004). |
| `searchIsDone` | `boolean` | backend | Флаг «поиск завершён». |
| `isCustomConfirmVisible` | `boolean` | UI | Флаг кастомного confirm-диалога. |
| `customConfirmParams` | `Object \| undefined` | UI | Параметры confirm-диалога. |

### 2. SearchAsync (backend response shape, read-only)

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | `number/string` | ID async-задачи |
| `query` | `string` | Поисковый запрос |
| `done` | `boolean` | Задача завершена? |

### 3. SearchResult (backend response shape, read-only)

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | `number/string` | ID результата |
| `url` | `string` | URL источника |
| `text` | `string` | Извлечённый текст (может быть пустым, если не извлечён) |
| `lastError` | `string` | Сообщение последней ошибки (если была) |

### 4. SearchTextResultsTable Component State

Состояние дочернего компонента `webvue3/src/components/Songs/edit/SearchTextResultsTable.vue`.

| Поле | Тип | Описание |
|------|-----|----------|
| `currentId` | `string/number \| undefined` | ID результата, на который кликнул пользователь (для подсветки `blue`). |

## Key relationships

```
┌─────────────────────────────────────────────────────────┐
│  SearchText.vue (родительская модалка)                  │
│  - currentResult ──────► [textarea v-text=:value]      │
│  - searchResultsList ─► (prop) ──► SearchTextResultsTable│
│  - isExtractingLyrics ► [disabled flag на кнопке]      │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼ $store.dispatch('extractLyricsBySearchResultId', {searchResultId})
┌─────────────────────────────────────────────────────────┐
│  Backend: POST /api/song/extractlyricsbysearchresultid  │
│  Response: SearchResult (с обновлённым text)            │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼ SearchResult { text: '...' }
┌─────────────────────────────────────────────────────────┐
│  Parent:                                               │
│  1. this.$set(this.searchResultsList, idx, updated)     │ ← реактивно обновляет массив
│  2. this.currentResult = updated                       │ ← реактивно обновляет textarea
└─────────────────────────────────────────────────────────┘
```

## State transitions (после фикса)

```
Pre-fix (текущее, с багом):
state = { currentResult: {id: 1, text: '', url: '...'}, searchResultsList: [{id:1, text:''}] }
       ──► user clicks "Получить текст по ссылке" ──►
extractLyricsBySearchResultId → backend → updated = {id: 1, text: 'Новый текст песни...'}
this.$set(searchResultsList, idx, updated)  ← реактивность: OK
this.currentResult = updated                  ← реактивность: OK
       ──► UI ──►
[textarea v-text="resultText"]              ← НЕ ОБНОВЛЯЕТСЯ (textContent игнорируется для textarea)
[SearchTextResultsTable]                     ← должен перерендерить строку (gray → white)

Post-fix (после изменения v-text → :value):
state = { currentResult: {id: 1, text: '', url: '...'}, searchResultsList: [{id:1, text:''}] }
       ──► user clicks "Получить текст по ссылке" ──►
extractLyricsBySearchResultId → backend → updated = {id: 1, text: 'Новый текст песни...'}
this.$set(searchResultsList, idx, updated)  ← реактивность: OK
this.currentResult = updated                  ← реактивность: OK
       ──► UI ──►
[textarea :value="resultText"]              ← ОБНОВЛЯЕТСЯ (value устанавливается реактивно)
[SearchTextResultsTable]                     ← перерендерит строку (gray → white)
```

## Validation rules

- `currentResult.text` после успешного ответа backend **не должен быть** `''` или `null` (иначе — это ошибка, см. FR-005).
- `isExtractingLyrics` устанавливается в `true` только при активном запросе, в `finally` блоке — в `false`.
- `currentResult` устанавливается через `selectedResult` (event) или через `currentResult = updated` (внутри `extractLyricsFromSelectedResult`).

## Storage

- **Client state**: Vue (in-memory).
- **Backend**: ничего не меняется.
- **БД**: не задействована.

## Edge cases (data-model specific)

- `updated.text === '' && updated.lastError === ''` (бэкенд вернул пустой результат без ошибки) — `resultText` остаётся `''`, `canExtractLyrics === true`, кнопка остаётся видимой (можно повторить попытку).
- `updated.lastError !== '' && updated.text === ''` (бэкенд вернул ошибку) — `window.alert` показывается, UI остаётся в исходном состоянии.
- `updated.lastError !== '' && updated.text !== ''` (бэкенд вернул текст + ошибку от предыдущей попытки) — текст показывается, alert не показывается (только если text пуст).
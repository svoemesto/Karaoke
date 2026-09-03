# Contracts: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки

**Дата**: 2026-09-03

## Сводка

В рамках этой задачи **backend-контракты НЕ изменяются**. Фикс целиком на стороне клиента (`webvue3/src/components/Songs/edit/SearchText.vue`).

Существующий backend-эндпоинт `/api/song/extractlyricsbysearchresultid` уже возвращает обновлённый `SearchResult` с заполненным `text` — нужно только исправить клиентский рендеринг.

## Существующие backend-эндпоинты (для справки)

### `POST /api/song/extractlyricsbysearchresultid`

**Назначение**: запустить ручное извлечение текста по выбранному `searchResultId`.

**Request params**:

| Параметр | Тип | Описание |
|----------|-----|----------|
| `searchResultId` | `number/string` | ID результата поиска (из `SearchResultsList`) |

**Response**: `SearchResult` (JSON-объект):

```json
{
  "id": 12345,
  "url": "https://example.com/lyrics/song-name",
  "text": "Полный текст песни...\n\nКуплет 1:\n...",
  "lastError": ""
}
```

**Возможные варианты ответа**:

| `text` | `lastError` | Смысл |
|--------|-------------|-------|
| `""` | `""` | Бэкенд не смог извлечь текст, но и не сообщил об ошибке (необычный случай) |
| `""` | `"Не удалось подключиться к сайту"` | Ошибка извлечения |
| `"Текст песни..."` | `""` | Успешное извлечение |
| `"Текст песни..."` | `"Ошибка от предыдущей попытки"` | Текст получен, ошибка от старого запроса (success) |

**Источник в коде**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/...` — конкретный файл будет найден в tasks-фазе через grep. Существующее API, не меняется.

## UI Contract (изменение в этой задаче)

**Файл**: `webvue3/src/components/Songs/edit/SearchText.vue`

### Было (lines 36):

```vue
<textarea class="result-text" v-text="resultText" />
```

**Проблема**: `v-text` в Vue2 устанавливает `textContent`, который для `<textarea>` игнорируется (textarea хранит значение в `.value`). После изменения `resultText` computed textarea **не обновляется визуально**.

Дополнительно: `<textarea />` — самозакрытый тег, что невалидно в HTML5 (textarea не может быть void-элементом).

### Будет:

```vue
<textarea class="result-text" :value="resultText"></textarea>
```

**Почему работает**: `:value` напрямую устанавливает DOM-свойство `.value` реактивно. При изменении `resultText` computed Vue обновит `.value` textarea, что отражается на отображаемом тексте.

`</textarea>` (закрывающий тег) — для валидности HTML5.

### Побочное изменение в `.group-button` (CSS):

```css
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 500px;
  display: block;  /* ← добавлено: гарантирует вертикальное расположение */
}
```

**Зачем**: кнопки в `<div class="st-body-column-2">` уже должны идти столбиком (block parent, block button — логично), но `<button>` по умолчанию `inline-block` мог вести себя неожиданно при `width: 500px`. Явное `display: block` гарантирует стабильное поведение.

**Риск**: `.group-button` используется также в `.st-footer` модалки (lines 56-89 — кнопки «Искать заново», «Удалить результаты поиска»). Эти кнопки **не обёрнуты** во flex/grid — после `display: block` они тоже пойдут столбиком (это уже и так должно быть их поведение). Если в `.st-footer` есть `display: flex; flex-direction: row` — поведение НЕ изменится (flex-контейнер переопределяет). **Если** есть проблема — будет видно при ручной проверке (Scenario 4 в quickstart.md).

## Логика, которая НЕ меняется

- `extractLyricsFromSelectedResult` (lines 236-258) — корректна.
- `selectedResult` (lines 225-227) — корректна.
- `openResultLink` (lines 196-198) — корректна.
- `resultText` computed (lines 148-150) — корректен.
- `canExtractLyrics` computed (lines 162-167) — корректен.
- SearchTextResultsTable.vue — корректен (`$set` в parent реактивно обновит prop).

## Что проверить в backend (для уверенности)

- Endpoint `/api/song/extractlyricsbysearchresultid` действительно возвращает обновлённый объект с заполненным `text`. Это уже подтверждено в существующей логике `extractLyricsFromSelectedResult` (lines 244-248), где идёт `$set(searchResultsList, idx, updated)`. Если бы backend возвращал `null` или не обновлённый объект — баг был бы в backend, а не в UI. **Не нужно менять backend.**

## Out of scope

- AbortController для отмены запросов (если пользователь закрыл модалку во время запроса — отдельная задача).
- Реактивность `currentId` в `SearchTextResultsTable` (если проблема останется после фикса #1 — отдельная задача).
- Backend-изменения.
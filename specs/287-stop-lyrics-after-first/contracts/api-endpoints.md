# API Contracts: 287 — Прекращение извлечения текста после первого успеха + ручная попытка по ссылке

> **Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **Data Model**: [data-model.md](data-model.md)

## Обзор

Существующие 4 endpoint-а меняют семантику (поведение «остановка после первого успеха»). Один endpoint добавляется.

| Endpoint | Изменение | Направление |
|----------|-----------|-------------|
| `POST /api/songs/searchsongtextall` | Без изменений контракта; меняется поведение внутреннего поиска | API → backend |
| `POST /api/song/searchasync` | Без изменений | API → backend |
| `POST /api/song/searchresult` | Без изменений | API → backend |
| `POST /api/song/deletesearchresults` | Без изменений | API → backend |
| `POST /api/song/extractlyricsbysearchresultid` | **НОВЫЙ** | API → backend |

## C1 — Изменение поведения существующих endpoints

### C1.1 — `POST /api/songs/searchsongtextall`

**Контракт** (без изменений):
- Request: `songsIds: String` (через `;`), `engine: String?`, `forceResearch: Boolean = false`.
- Response: `Boolean` (всегда `true` при успешной обработке списка).

**Изменение поведения** (FR-001):
- Внутри `getSearchResultsForSearchAsync` (Yandex-путь) и `getLyricsSearchViaSearchTool` (Search-tool-путь) добавлен ранний выход из цикла после первого успешного извлечения текста.
- Результат: для каждой песни — `N` записей в `tbl_search_results`, из них `1` с непустым `text`, остальные `N-1` с пустым `text`.

**Обратная совместимость**: ✅ — клиент (админка, `SubsEdit`) ожидает, что `text` первой успешной ссылки будет подставлен в `Song.sourceText` через `applyFoundLyricsIfMissing`. Поведение сохраняется.

### C1.2 — `POST /api/song/searchasync`

**Контракт** (без изменений):
- Request: `songId: Long`.
- Response: `List<SearchAsyncDTO>` — все `SearchAsync` для данной песни.

**Изменений**: нет. Endpoint возвращает то же, что и раньше.

### C1.3 — `POST /api/song/searchresult`

**Контракт** (без изменений):
- Request: `searchAsyncId: Long`.
- Response: `List<SearchResultDTO>` — все `SearchResult` для данного `SearchAsync`.

**Изменений**: нет. Возвращает ВСЕ записи, включая «серые» (с пустым `text`).

**Замечание для UI**: визуальное различие «серая» vs «с текстом» делается на frontend по `searchResult.text === ''` (без изменений в `SearchTextResultsTable.vue`).

### C1.4 — `POST /api/song/deletesearchresults`

**Контракт** (без изменений):
- Request: `songId: Long`.
- Response: `Boolean`.

**Изменений**: нет.

## C2 — Новый endpoint: `POST /api/song/extractlyricsbysearchresultid`

### Запрос

```
POST /api/song/extractlyricsbysearchresultid
Content-Type: application/x-www-form-urlencoded

searchResultId=12345
```

**Параметры**:

| Имя | Тип | Required | Описание |
|-----|-----|----------|----------|
| `searchResultId` | Long | да | ID записи в `tbl_search_results` |

### Ответ — успех (с текстом)

```
HTTP 200 OK
Content-Type: application/json

{
  "id": 12345,
  "searchAsyncId": 678,
  "songId": 100,
  "url": "https://example.com/song-lyrics",
  "html": "",
  "text": "Verse 1:\nLine 1\nLine 2\n...",
  "wrongResult": false,
  "lastError": null
}
```

### Ответ — успех HTTP, но парсер пустой

```
HTTP 200 OK
Content-Type: application/json

{
  "id": 12345,
  "searchAsyncId": 678,
  "songId": 100,
  "url": "https://example.com/no-lyrics",
  "html": "<html>...страница без текста песни...</html>",
  "text": "",
  "wrongResult": false,
  "lastError": ""
}
```

`lastError = ""` означает «парсер не нашёл текст на странице» (но HTTP-запрос успешен).

### Ответ — HTTP-ошибка (timeout, 5xx, ConnectException)

```
HTTP 200 OK
Content-Type: application/json

{
  "id": 12345,
  "searchAsyncId": 678,
  "songId": 100,
  "url": "https://example.com/timeout",
  "html": "",
  "text": "",
  "wrongResult": false,
  "lastError": "Jsoup ConnectException: Connection refused"
}
```

`lastError != ""` означает «HTTP-запрос не выполнен».

### Ответ — запись не найдена

```
HTTP 404 Not Found
```

### Ответ — параметр не передан / некорректен

```
HTTP 400 Bad Request
```

### Ответ — запись уже с текстом (FR-022, идемпотентность)

```
HTTP 200 OK
Content-Type: application/json

{
  "id": 12345,
  "searchAsyncId": 678,
  "songId": 100,
  "url": "https://example.com/song-lyrics",
  "html": "<html>...</html>",
  "text": "Verse 1:\n...",
  "wrongResult": false,
  "lastError": null
}
```

Запись возвращается как есть, **HTTP-запрос НЕ делается**. Это поведение нужно для FR-022 (защита от лишних обращений к исходному сайту).

### Идемпотентность

✅ Endpoint идемпотентен:
- Если `searchResult.text.isNotBlank()` — возвращает запись как есть, без HTTP-запроса.
- Если `searchResult.text.isBlank()` — пытается HTTP-запрос, обновляет запись.

### Race conditions

Маловероятны (один пользователь в одной модалке). Защита на frontend: кнопка `disabled` во время запроса.

`applyFoundLyricsIfMissing` НЕ вызывается из этого endpoint (FR-024) — значит race condition с Pass 281 невозможна.

### Размещение кода

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` рядом с существующими `/api/song/searchresult` (строка ~7792) и `/api/song/deletesearchresults` (строка ~7810).

### Зависимости

- `SearchResult.getSearchResultById` (уже существует, `SearchResult.kt:296-309`).
- `UtilsAI.extractLyricsBySearchResultId` (новая функция, см. R2 в research.md).
- `SearchResult.save()` (через `KaraokeDbTable`).

## C3 — Vuex action

### `extractLyricsBySearchResultId`

```js
// webvue3/src/components/Songs/store.js
extractLyricsBySearchResultId(ctx, { searchResultId }) {
  let params = { searchResultId }
  let request = {
    method: 'POST',
    url: '/api/song/extractlyricsbysearchresultid',
    params: params,
  }
  return promisedXMLHttpRequest(request)
}
```

**Размещение**: рядом с `searchTextForSong` (строка ~2631) и `deleteSearchResults` (строка ~2640).

**Возвращает**: обновлённый `SearchResultDTO` (как в ответе endpoint выше).

## C4 — UI contract для `SearchText.vue`

### Кнопка «Получить текст по ссылке»

**Размещение**: в правой колонке модалки, непосредственно под кнопкой «Открыть на сайте».

**Состояния**:

| Состояние ссылки | Состояние кнопки |
|------------------|------------------|
| Поиск ещё не завершён / нет результатов | Кнопка скрыта (нет `currentResult`) |
| `text === ""` (серая ссылка) | Кнопка доступна |
| `text !== ""` (успешная ссылка) | Кнопка `disabled` или скрыта |
| Идёт HTTP-запрос (ручная попытка) | Кнопка `disabled`, текст «Получаю текст...» |

**Поведение при клике**:
1. Установить `isExtractingLyrics = true`, `disabled = true`.
2. Вызвать `extractLyricsBySearchResultId` action с `searchResultId: currentResult.id`.
3. Получить ответ — обновлённый `SearchResultDTO`.
4. Обновить запись в `searchResultsList` (replace by id).
5. Обновить `currentResult`.
6. Если `updated.lastError` — показать уведомление (toast/alert) с текстом ошибки.
7. Установить `isExtractingLyrics = false`.

### Визуальное состояние ссылок (без изменений в `SearchTextResultsTable.vue`)

Текущий код:
```vue
:style="{
  backgroundColor:
    currentId === searchResult.id ? 'blue' : searchResult.text === '' ? 'gray' : 'white',
}"
```

Поведение сохраняется. После фикса (FR-001) для автопоиска естественным образом получается 1 белая ссылка + N-1 серых.

### Обновление списка после ручной попытки

При успехе: запись обновляется в `searchResultsList` через `Vue.set` / splice (реактивность Vue 2).

Правая колонка (`<textarea class="result-text">`) автоматически показывает новый `currentResult.text` через computed `resultText`.

## C5 — Контракт безопасности

- Endpoint `/api/song/extractlyricsbysearchresultid` — `permitAll()` (как и другие `/api/song/*` для админки `webvue3`); см. Constitution §V.
- Нет новых полей авторизации.
- Нет CSRF-токенов (как и для существующих `/api/song/*`).
- Делает исходящий HTTP-запрос к произвольному URL (пользовательскому домену из поиска) — это уже было в текущем коде (Yandex/SearXNG/FOURGET), без новых рисков.

## C6 — Логирование

Backend печатает в stdout:
- При успехе: `"Успешное извлечение текста по ссылке ${searchResult.url}, символов: ${text.length}"`.
- При HTTP-ошибке: `"Ошибка при извлечении текста по ссылке ${searchResult.url}: ${e.message}"`.
- При парсере-пустышке: `"Парсер вернул пустой результат для ${searchResult.url}"`.

UI НЕ логирует (только backend).
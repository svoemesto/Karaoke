# Data Model: 287 — Прекращение извлечения текста после первого успеха + ручная попытка по ссылке

> **Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Схема БД

**Не меняется.** SQL-миграции НЕ требуются.

Существующие таблицы:

### `tbl_search_async`

| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | integer PK | Идентификатор |
| `song_id` | integer | FK на песню |
| `url` | varchar(255) | URL API (Yandex) или пустой |
| `iam_token` | text | IAM-токен (Yandex) или пустой |
| `query` | text | Поисковый запрос |
| `body` | text | Тело запроса (Yandex) или пустой |
| `response_format` | varchar(255) | `FORMAT_XML` (Yandex) или `SEARXNG`/`FOURGET` (новое использование) |
| `operation_id` | varchar(255) | ID операции (Yandex ASYNC) или пустой |
| `done` | boolean | Готов ли ответ |
| `raw_data` | text | Тело ответа (Yandex: XML с URL; Search-tool: `\n`-separated URL) |
| `last_requested_at` | timestamp | Последний poll (Yandex ASYNC) |
| `recordhash` | varchar(32) | Для sync (см. Constitution §III) |

**Изменений**: нет.

### `tbl_search_results`

| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | integer PK | Идентификатор |
| `search_async_id` | integer | FK на поисковый запрос |
| `song_id` | integer | FK на песню |
| `url` | text | URL конкретной ссылки |
| `html` | text | Скачанная HTML-страница (или `""` если страница не скачивалась) |
| `text` | text | Извлечённый текст песни (или `""` если не удалось) |
| `wrong_result` | boolean | Флаг «неправильный результат» (LLM-rejection для SearXNG/FOURGET) |
| `last_update` | timestamp | Последнее обновление |
| `recordhash` | varchar(32) | Для sync |

**Изменений схемы**: нет.
**Изменений семантики** (FR-001..FR-004):
- После фикса: для большинства записей `text = ""` И `html = ""` (потому что HTTP-запрос НЕ делался).
- Одна запись (первая успешная) — `text = <найденный текст>`, `html = <скачанная HTML>`.
- Сохраняется обратная совместимость: `text = ""` означает «не удалось извлечь» (как раньше, для Yandex-пути `text` мог быть пустым из-за пустого парсера).
- Для ручной попытки (FR-020..FR-024): `text` обновляется, `html` обновляется (если HTTP-запрос был).

## DTO

### `SearchResultDTO` (существующий, изменение в одном поле)

```kotlin
data class SearchResultDTO(
    val id: Long,
    val searchAsyncId: Long,
    val songId: Long,
    val url: String,
    val html: String,           // отдаём "" (как раньше — внутреннее поле, не для UI)
    val text: String,
    val wrongResult: Boolean,
    val lastError: String? = null,  // НОВОЕ ПОЛЕ — null если успех/нет попытки, иначе текст ошибки
)
```

**Изменение**: добавлено опциональное поле `lastError: String?`.

**Семантика `lastError`**:
- `null` — извлечение либо успешно (`text.isNotBlank()`), либо не выполнялось (запись только создана, но HTTP-запрос ещё не делался);
- `""` — HTTP-запрос выполнен, страница получена, но парсер вернул пустой результат;
- `"<описание>"` (например, `"HTTP timeout"`, `"Jsoup ConnectException: ..."`) — HTTP-запрос не выполнен / страница не получена.

**Использование в UI**: `SearchText.vue` показывает уведомление (toast/alert) при `lastError != null && text.isBlank()`.

**Обратная совместимость**: для существующих записей `lastError = null` — UI их не показывает как ошибку (поведение как раньше).

## Vuex store

### Новый action

```js
// webvue3/src/components/Songs/store.js
async extractLyricsBySearchResultId(ctx, { searchResultId }) {
  const request = {
    method: 'POST',
    url: '/api/song/extractlyricsbysearchresultid',
    params: { searchResultId },
  }
  return promisedXMLHttpRequest(request)
}
```

**Размещение**: рядом с существующими `searchTextForSong` / `deleteSearchResults` (строки ~2631-2647).

## Сущности (без изменений)

### `Song`

| Колонка | Связь |
|---------|-------|
| `source_text` | Подставляется из первой успешной `SearchResult.text` через `applyFoundLyricsIfMissing` (Pass 020, Pass 281). Без изменений. |
| `id_status` | Устанавливается в `1` при первой успешной подстановке. Без изменений. |
| `key`, `bpm`, `audio_*` | Защита через reload-from-db-before-save (Pass 278, 281). Без изменений. |

### `SearchAsync`

Без изменений. Связь 1:N с `SearchResult` по `id ↔ search_async_id`.

## Состояния `SearchResult` (state machine)

```
                    ┌──────────────────────────┐
                    │  Создана автоматически   │
                    │  text = "", html = ""    │
                    │  lastError = null        │
                    └──────────┬───────────────┘
                               │ (HTTP-запрос не делался — остановка после первой успешной)
                               │
                               │ (может быть автоматически заполнена, если она первая успешная)
                               ▼
                    ┌──────────────────────────┐
                    │  Заполнена автоматически │
                    │  text != "", html != ""  │
                    │  lastError = null        │
                    └──────────────────────────┘

                    ┌──────────────────────────┐
                    │  Создана автоматически   │
                    │  text = "", html = ""    │
                    └──────────┬───────────────┘
                               │ (пользователь нажал «Получить текст по ссылке»)
                               ▼
                    ┌──────────────────────────┐
                    │  HTTP-запрос выполнен     │
                    │  text = ..., html = ...  │
                    │  lastError = null        │ ← успех
                    └──────────────────────────┘
                               │ (если парсер не нашёл)
                               ▼
                    ┌──────────────────────────┐
                    │  HTTP-запрос выполнен     │
                    │  text = "", html = ...   │
                    │  lastError = "" (пустой) │
                    └──────────────────────────┘
                               │ (если HTTP упал)
                               ▼
                    ┌──────────────────────────┐
                    │  HTTP-запрос не выполнен  │
                    │  text = "", html = ""    │
                    │  lastError = "HTTP ..."  │
                    └──────────────────────────┘
```

**Переходы**:
- Из любого состояния с `text.isBlank()` → повторная ручная попытка → в одно из трёх (успех / пустой / ошибка).
- Из состояния с `text.isNotBlank()` → ручная попытка **не делается** (FR-022), состояние остаётся как есть.

## Валидации (на уровне кода, не схемы)

- `searchResultId` > 0 — иначе 400 Bad Request.
- `searchResult.id` существует в `tbl_search_results` — иначе 404 Not Found.
- Песня (`searchResult.songId`) существует — иначе 404 Not Found (для контекста).
- Race condition: при `applyFoundLyricsIfMissing` уже стоит защита Pass 281. Ручной режим НЕ вызывает `applyFoundLyricsIfMissing`, поэтому race condition отсутствует.

## Допущения (повтор из спеки, для полноты data model)

- **A-1**: записи «серых» ссылок остаются в БД (нужно для ручного режима).
- **A-4**: правка делается в общих точках (`getSearchResultsForSearchAsync` + `getLyricsSearchViaSearchTool`), покрывает все 4 движка + воркер.
- **A-7**: новый эндпоинт возвращает обновлённый `SearchResultDTO`; UI использует его для обновления списка ссылок без полной перезагрузки модалки.
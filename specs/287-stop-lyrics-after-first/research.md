# Research: 287 — Прекращение извлечения текста после первого успеха + ручная попытка по ссылке

> **Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md)

## Цель research

Изучить существующую кодовую базу (`karaoke-app/.../SearchResult.kt`, `karaoke-app/.../UtilsAI.kt`, `karaoke-app/.../lym/LyricsFinderService.kt`, `karaoke-app/.../controllers/ApiController.kt`, `webvue3/.../SearchText.vue`, `webvue3/.../SearchTextResultsTable.vue`, `webvue3/.../store.js`, `webvue3/.../SubsEdit.vue`) и зафиксировать:

1. **Где именно** в коде происходит цикл «для каждой ссылки получить HTML и извлечь текст» (по каждому из 4 движков).
2. **Как** разделить «извлечение текста по одной ссылке» (для ручного режима) от «обхода всех ссылок» (для автоматического).
3. **Где** разместить новый backend-эндпоинт «получить текст по конкретному результату поиска».
4. **Как** минимизировать blast radius и сохранить регрессии Pass 020/278/281.
5. **Какой** формат запроса/ответа для нового эндпоинта.

## R1 — Места цикла «извлечение текста по каждой ссылке»

В коде есть **два независимых цикла** обхода URL-ов, по одному на каждый путь движков:

### R1.1 — Yandex-путь (`YANDEX_SYNC` + `YANDEX_ASYNC` через воркер)

Точка входа для обоих — `SearchResult.Companion.getSearchResultsForSearchAsync(searchAsync)` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchResult.kt:68-219`).

Цикл (строки 100-215):
1. `links.forEach { link -> ... }` — перебирает URL из `searchAsync.rawData`.
2. Извлекает `domain` через `extractDomain(link)`.
3. По словарю `classNamePrefixes`/`idNamePrefixes` выбирает CSS-селекторы (строки 112-183).
4. Если словарь дал селекторы — `getHtml(link)` (Jsoup GET), затем `findElementByText(...)` (парсинг по селектору).
5. Сохраняет `SearchResult` через `createNewSearchResult(...)`.
6. **Добавляет в `result` всегда** (даже если `text.isBlank()`), потому что в `result` нужно ВСЕ записи — и успешные, и неуспешные (для отображения в модалке).

Вызывающие места:
- `UtilsAI.kt:421` — `getYandexSearch` (YANDEX_SYNC).
- `KaraokeProcessWorker.kt:898` — фоновое завершение YANDEX_ASYNC.
- `UtilsAI.kt:898` — `applyFoundLyricsIfMissing` вызывается ПОСЛЕ `getSearchResultsForSearchAsync`.

**Решение**: в `getSearchResultsForSearchAsync` после строки 213 (`if (savedSearchResult != null) result.add(searchResult)`) добавить проверку «если `searchResult.text.isNotBlank()` — `return result`» (выйти из `links.forEach` через метку или сделать `links.forEach` → `for (link in links) ... return@forEach` + ранний выход). Это меняет оба пути одной правкой (фактически — одна и та же функция `getSearchResultsForSearchAsync`).

### R1.2 — Search-tool-путь (`SEARXNG` + `FOURGET`)

Точка входа — `UtilsAI.kt:202-276` (`getLyricsSearchViaSearchTool`).

Цикл (строки 250-270):
1. `for (url in urls) { val lyrics = lyricsFinderService.extractLyricsFromUrl(url); variants.add(Pair(url, lyrics ?: "")) }` — перебирает URL, для каждого вызывает LLM-парсер через `LyricsFinderService.extractLyricsFromUrl(url)` (`karaoke-app/.../llm/LyricsFinderService.kt:104-142`).
2. Потом (строки 257-270) создаёт `SearchResult` для каждого URL — и для успешных, и для неуспешных (`searchResult.text = lyrics`, где `lyrics` может быть `""` если `extractLyricsFromUrl` вернул `null`).
3. После этого вызывает `applyFoundLyricsIfMissing(song, searchedRightResultsNotEmpty.map { it.text })`.

**Решение**: в `getLyricsSearchViaSearchTool` после строки 254 (`variants.add(...)`) — добавить проверку «если `lyrics.isNullOrBlank()` → пропустить запись в `variants` как «не успешно»; если `!lyrics.isNullOrBlank()` — добавить в `variants` и далее НЕ обходить остальные URL-ы (ранний выход из цикла). НО: нужно сохранить все URL-ы как записи `SearchResult` (с пустым `text`) — поэтому цикл надо разделить на два прохода:

**Альтернатива**: сначала собрать `urls` (как сейчас), потом в ОДНОМ проходе:
```kotlin
val urlLyricsPairs = mutableListOf<Pair<String, String>>()
var foundFirst = false
for (url in urls) {
    val lyrics = lyricsFinderService.extractLyricsFromUrl(url)
    if (foundFirst) {
        urlLyricsPairs.add(Pair(url, ""))  // пустой, чтобы была запись в БД
        continue
    }
    if (lyrics.isNullOrBlank()) {
        urlLyricsPairs.add(Pair(url, ""))  // пустой — пытались, не получилось
    } else {
        urlLyricsPairs.add(Pair(url, lyrics))
        foundFirst = true
    }
}
```

Это даёт точно нужное поведение: первая успешная получает текст, остальные сохраняются как записи с пустым `text` (без HTTP-запроса).

### R1.3 — `findSongText` (legacy в `UtilsAI.kt:442-540`)

Эта функция используется только в `ApiController.kt:4960` (legacy-путь для «найти текст для одной песни», без БД-сохранения) и НЕ относится к фиче. Изменять её не нужно (она не сохраняет в `tbl_search_results`, работает in-memory). Оставляем как есть.

## R2 — Архитектурное решение: где разместить «извлечение текста по одной ссылке»

Нужна функция, которая:
- принимает 1 URL + контекст (songId, какой движок использовался, или какой парсер применять);
- возвращает `String?` (текст или `null`);
- НЕ делает HTTP-запрос повторно, если уже есть валидный результат в БД.

**Решение**: **вынести** в `UtilsAI.kt` новую функцию:

```kotlin
fun extractLyricsBySearchResultId(searchResultId: Long, ...): SearchResult
```

Она:
1. Загружает `SearchResult` по id.
2. По `searchAsync.responseFormat` / по домену URL выбирает, КАК парсить:
   - для Yandex-пути (FORMAT_XML/FORMAT_HTML): использовать `findElementByText` + словарь `classNamePrefixes`;
   - для Search-tool-пути (SEARXNG/FOURGET): использовать `lyricsFinderService.extractLyricsFromUrl`.
3. Если `text` уже непустой — возвращает запись как есть (FR-022).
4. Делает HTTP-запрос, парсит, сохраняет в БД (`searchResult.text = ...`, `searchResult.html = html`).
5. Возвращает обновлённую запись.

Эта функция — единственное место, где живёт «извлечение текста по одной ссылке», используется И для Yandex-пути, И для Search-tool-пути. Используется:
- Новым endpoint для ручной попытки;
- При будущих изменениях автоматического режима (если понадобится).

Аналогично для автоматического режима — НЕ нужна новая функция, потому что изменение точечное (R1.1 + R1.2).

## R3 — Новый endpoint: контракт

**Имя**: `POST /api/song/extractlyricsbysearchresultid`

**Параметры**:
- `searchResultId: Long` (required) — id записи в `tbl_search_results`.

**Ответ** (200 OK):
- `SearchResultDTO` — обновлённая запись (с новым `text` либо с пустым `text` + дополнительным полем).

**Дополнительное поле в DTO**: `lastError: String?` — описание ошибки (HTTP таймаут, пустой HTML, парсер вернул `null`). Используется UI для показа уведомления.

**Ошибки**:
- `400 Bad Request` — `searchResultId` не передан или некорректен.
- `404 Not Found` — запись не найдена.
- `200 OK` с пустым `text` + `lastError != null` — извлечение неуспешно (это не ошибка запроса, это результат попытки).

**Идемпотентность**: повторный вызов для одной и той же записи безопасен:
- если `text.isNotBlank()` — возвращает как есть, HTTP-запрос НЕ делается (FR-022);
- если `text.isBlank()` — повторно делает HTTP-запрос и пытается извлечь.

**Race conditions**: маловероятны (один пользователь в одной модалке). Защита от записи в `Song.sourceText` уже есть через `applyFoundLyricsIfMissing` (Pass 020 + Pass 281) — но ручной режим НЕ подставляет в `Song.sourceText` (FR-024), значит race conditions нет вообще.

**Размещение в коде**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` рядом с существующими `/api/song/searchresult` и `/api/song/deletesearchresults` (строки ~7792-7818).

## R4 — Vuex store action + Vue component

**Новый action в `webvue3/src/components/Songs/store.js`** (рядом с `searchTextForSong`/`deleteSearchResults`):
```js
async extractLyricsBySearchResultId(ctx, { searchResultId }) {
  const request = {
    method: 'POST',
    url: '/api/song/extractlyricsbysearchresultid',
    params: { searchResultId },
  }
  return promisedXMLHttpRequest(request)
}
```

**Изменения в `SearchText.vue`** (правая колонка):
```vue
<textarea class="result-text" v-text="resultText" />
<button class="group-button" title="Открыть на сайте" @click="openResultLink">
  Открыть на сайте
</button>
<!-- НОВОЕ: -->
<button
  class="group-button"
  title="Получить текст по ссылке"
  :disabled="!canExtractLyrics || isExtractingLyrics"
  @click="extractLyricsFromSelectedResult"
>
  {{ isExtractingLyrics ? 'Получаю текст...' : 'Получить текст по ссылке' }}
</button>
```

**Computed в `SearchText.vue`**:
```js
canExtractLyrics() {
  return this.currentResult && !this.currentResult.text
}
```

**Метод в `SearchText.vue`**:
```js
async extractLyricsFromSelectedResult() {
  this.isExtractingLyrics = true
  try {
    const updated = await this.$store.dispatch('extractLyricsBySearchResultId', {
      searchResultId: this.currentResult.id,
    })
    // Обновляем запись в searchResultsList
    const idx = this.searchResultsList.findIndex(r => r.id === updated.id)
    if (idx !== -1) {
      this.searchResultsList.splice(idx, 1, updated)
      this.$set(this.searchResultsList, idx, updated)
    }
    this.currentResult = updated
    if (updated.lastError) {
      // показать уведомление (toast/alert)
    }
  } finally {
    this.isExtractingLyrics = false
  }
}
```

## R5 — Визуальное состояние ссылок (без изменений в SearchTextResultsTable.vue)

Текущий код `SearchTextResultsTable.vue:6-9` уже корректно обрабатывает «серую» ссылку:
```vue
:style="{
  backgroundColor:
    currentId === searchResult.id ? 'blue' : searchResult.text === '' ? 'gray' : 'white',
}"
```

С FR-001 после изменения алгоритма это естественным образом даст N-1 серых ссылок и 1 успешную. Никаких изменений в `SearchTextResultsTable.vue` не требуется (только проверка, что новый `text` после ручной попытки корректно отражается на цвете).

## R6 — Регрессии Pass 020 / 278 / 281

| Pass | Что защищает | Как наша фича влияет |
|------|--------------|-----------------------|
| 020 (`fix-search-lyrics-autofill`) | `sourceText` подставляется через `applyFoundLyricsIfMissing`, используя `song.haveSourceText` как single source of truth | Без изменений: `applyFoundLyricsIfMissing` вызывается как и раньше, только теперь передаётся список из 1 непустого `text` (или пустой список). Логика — та же. |
| 278 (`fix-key-loss-on-lyrics-search`) | reload-from-db-before-save в `doCreateFromFolder` | Без изменений: фикс локальный для функции `doCreateFromFolder` и `setSourceMarkers`; наш цикл в `SearchResult.getSearchResultsForSearchAsync` не трогает `saveToDb` для `Song`. |
| 281 (`find-lyrics-overwrites-key-bpm`) | reload-from-db-before-save в `applyFoundLyricsIfMissing` | Без изменений: фикс остаётся в `applyFoundLyricsIfMissing`, наш код не модифицирует эту функцию. |

**Регрессионные гарантии**:
- `applyFoundLyricsIfMissing` вызывается как раньше — никаких изменений в этой функции.
- `Song.saveToDb()` — НЕ модифицируется.
- Поведение для всех 4 движков идентично (правка в общих точках: `getSearchResultsForSearchAsync` + `getLyricsSearchViaSearchTool`).
- Существующие эндпоинты (`/searchasync`, `/searchresult`, `/deletesearchresults`, `/searchsongtextall`) — поведение сохраняется, только внутренняя логика «извлечения текста» меняется (остановка после первого успеха).

## R7 — Альтернативы, которые отклонены

| Альтернатива | Почему отклонена |
|--------------|------------------|
| Сделать остановку в `applyFoundLyricsIfMissing` (вместо `getSearchResultsForSearchAsync`) | Слишком поздно — HTTP-запросы уже сделаны, страницы уже скачаны, нагрузка на исходные сайты не снижается. |
| Сделать остановку в `getYandexSearch` и `getLyricsSearchViaSearchTool` отдельно (без изменения `getSearchResultsForSearchAsync`) | Дублирование логики остановки в 3 местах. `KaraokeProcessWorker` (YANDEX_ASYNC) тоже использует `getSearchResultsForSearchAsync`, и эту правку легко забыть. **Решение**: правка в общей точке `getSearchResultsForSearchAsync` покрывает и воркер. |
| Сделать остановку на фронте (не делать HTTP-запрос для N+1 ссылки) | Не работает — HTTP-запросы делаются на backend, а не на frontend. |
| Сделать ручную попытку через тот же эндпоинт `/searchsongtextall` с `forceResearch=true` и фильтром | Семантически некорректно — «forceResearch» очищает ВСЕ результаты и стартует заново, а нам нужна попытка для ОДНОЙ конкретной записи. |
| Использовать существующий `extractLyricsFromUrl` из `LyricsFinderService` для Yandex-пути | Семантика разная: `LyricsFinderService.extractLyricsFromUrl` использует LLM-парсер (`ScraperAgent`), а Yandex-путь использует словарь CSS-селекторов (`findElementByText`). Применять LLM-парсер к Yandex-ссылкам — неправильно (он оптимизирован под fourget/searxng). **Решение**: для нового endpoint выбираем парсер по домену/формату ответа — см. R2. |
| Удалять «серые» SearchResult при первом успехе | Противоречит US3 (FR-002): нужны ВСЕ записи, чтобы пользователь мог вручную кликнуть на любую. |

## R8 — Открытые вопросы / допущения

- **A-1** (из спеки): записи «серых» ссылок остаются в БД. Подтверждено — нужно для FR-002, US3.
- **A-5** (из спеки): «первая успешная» = первая по порядку в списке URL, как их вернул движок. Подтверждено — никакого ранжирования.
- **Race в ручном режиме**: маловероятна (один пользователь, одна модалка). Защита от одновременных кликов по двум разным «серым» ссылкам — через UI (показывать `disabled` во время `isExtractingLyrics`).
- **Может ли ручной режим использоваться для ВСЕХ движков одинаково?** — Да, новая функция `extractLyricsBySearchResultId` работает для любой записи в `tbl_search_results`, потому что выбирает парсер по домену/формату. Это покрывает и Yandex-путь, и Search-tool-путь.
- **Поведение при ошибке HTTP во время ручной попытки** — возвращаем 200 OK с пустым `text` + `lastError` (а не 5xx). Это позволяет UI показать уведомление и оставить кнопку доступной.

## Решения

| # | Решение | Обоснование |
|---|---------|-------------|
| D-1 | «Остановка после первого успеха» — в `SearchResult.getSearchResultsForSearchAsync` (Yandex) + `UtilsAI.getLyricsSearchViaSearchTool` (Search-tool) | Эти 2 функции — общие точки для соответствующих путей, покрывают все 4 движка + воркер. |
| D-2 | Записи «серых» ссылок сохраняются с `text=""` и `html=""` | Нужны для ручного режима (FR-002) и для отображения в модалке. |
| D-3 | Новый endpoint `POST /api/song/extractlyricsbysearchresultid` принимает `searchResultId`, возвращает обновлённый `SearchResultDTO` (с полем `lastError?`) | Идемпотентный, безопасный для race, можно вызывать много раз. |
| D-4 | Новая функция `extractLyricsBySearchResultId` в `UtilsAI.kt` — общая для Yandex-пути и Search-tool-пути | DRY: один парсер, выбор стратегии по `searchAsync.responseFormat` + домену. |
| D-5 | В UI: новая кнопка «Получить текст по ссылке» под «Открыть на сайте», `disabled` при непустом `text` или во время запроса | FR-031, FR-032, FR-033. |
| D-6 | Визуальное состояние ссылок (серая / с текстом) — БЕЗ изменений в `SearchTextResultsTable.vue` (уже работает через `text === ''`) | R5. |
| D-7 | Регрессии Pass 020/278/281 — без изменений (все правки локальны для поисковых функций, не трогают `applyFoundLyricsIfMissing`/`saveToDb`) | R6. |

## Без изменений

- `Song.saveToDb()` — НЕ модифицируется (Pass 281).
- `applyFoundLyricsIfMissing` — НЕ модифицируется (Pass 281 + Pass 020).
- `KaraokeProcessWorker.kt` — НЕ модифицируется (использует общую точку `getSearchResultsForSearchAsync`).
- `Song.kt:3626 setSourceMarkers`, `Song.kt:3662 setSourceText` — НЕ модифицируются (Pass 278 + Pass 281).
- SQL-миграции — НЕ нужны (схема не меняется).
- `tbl_search_results` schema — НЕ меняется.
- `SearchTextResultsTable.vue` — НЕ меняется (визуальное состояние уже работает).
- `SubsEdit.vue` — НЕ меняется (только передаёт `song-id` в `SearchText`).
- Сборка/деплой — без изменений (используем стандартный flow: `bootJar` + `npm run build`).
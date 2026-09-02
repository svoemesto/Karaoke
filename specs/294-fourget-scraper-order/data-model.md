# Data Model: 294 — Порядок и состав scraper'ов fourget для поиска текстов песен

**Дата**: 2026-09-02 | **Spec**: [spec.md](spec.md)

## Назначение документа

Этот документ описывает **изменения в данных**, которые вносит фича 294:
новые свойства в `KaraokeProperties`, новый Kotlin-объект
`UselessUrlFilter`, и изменения в контракте функции `searchUrlsViaScraper`.

Фича **не затрагивает БД** (никаких миграций), **не затрагивает DTO**
(никаких новых endpoint'ов), **не затрагивает SyncRegistry** (никаких
новых таблиц). Изменения — только в Kotlin-коде и в файле
`Karaoke.properties` (JSON-lines, base64-encoded, см.
`KaraokeProperties.kt:65-86`).

## Изменения в `KaraokeProperties` (новые свойства)

### `lyricsSearchScrapers: String`

- **Тип**: `String` (а не `List<String>` — см. Q1 в `research.md`)
- **Дефолт**: `"yep;brave"`
- **Описание**: «Список scrapers для fourget (web-поиск) при поиске
  текстов песен. Перебираются по порядку; первый непустой ответ
  (после post-filter, с учётом `lyricsSearchMinResults`) используется.
  Разделитель — `;`.»
- **Где используется**: `SearchTool.searchUrls` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`
  (новый код).
- **Парсинг**: helper `private fun lyricsSearchScrapersList(): List<String>`
  — `value.split(";").map { it.trim() }.filter { it.isNotEmpty() }`.
  Если после парсинга список пустой → fallback на hardcoded дефолт
  `listOf("yep", "brave")` (defensive — не должно случиться, но
  защищаемся от misconfig).

### `lyricsSearchMinResults: Int`

- **Тип**: `Int`
- **Дефолт**: `2`
- **Описание**: «Минимальное количество URL от scraper'а (после post-filter),
  чтобы считать его ответ успешным. Меньше — пробуем следующий scraper.»
- **Где используется**: `SearchTool.searchUrls` — сравнение с размером
  отфильтрованного списка URL.
- **Валидация**: должна быть `≥0`. При некорректном значении
  (например, отрицательное через SQL) — fallback на дефолт `2` (см.
  паттерн `UtilsAI.kt:52-59` `resolveAlbumCoverSearchEngine` — там
  IllegalArgumentException → дефолт).

### `lyricsSearchUselessUrlPatterns: String`

- **Тип**: `String` (аналогично scrapers — flat-список с `;`-разделителем)
- **Дефолт**: список паттернов из FR-004 спеки (пп.4, 5, 6),
  склеенный через `;`:
  ```
  login;signup;register;auth;wp-login.php;wp-admin;administrator;sitemap.xml;sitemap;sitemap_index.xml;robots.txt;feed;rss;rss.xml;atom.xml;pdf;doc;docx;xls;xlsx;zip;rar;7z;tar;gz;mp3;mp4;wav;avi;mov;jpg;jpeg;png;gif;webp;svg;search;?utm_source=;?utm_medium=;?utm_campaign=;?utm_term=;?utm_content=;fbclid=;gclid=;yclid=;msclkid=;_ga=;ref=
  ```
  (один длинный string, парсится аналогично `lyricsSearchScrapers`).
- **Описание**: «Паттерны для post-filter «мусорных» URL (path-суффиксы,
  расширения файлов, tracking-маркеры). Хранится как `;`-joined список.
  Каждый паттерн — substring, проверяется case-insensitive в URL.»
- **Где используется**: `filterUselessLyricsUrls` в `Tools.kt`
  (новая функция, см. ниже).
- **Парсинг**: helper `private fun uselessUrlPatternsList(): List<String>`
  — split + filter non-empty.

**Примечание**: `lyricsSearchUselessUrlPatterns` — **настраиваемый**
список (можно добавить/убрать паттерны через `KaraokeProperties` без
передеплоя). Hardcoded дефолт покрывает 100% известных случаев из
FR-004; новые паттерны добавляются по мере обнаружения либо через
свойство, либо через PR (если меняется логика матчинга).

## Новый Kotlin-объект: filter logic

### Top-level function `filterUselessLyricsUrls`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`

**Сигнатура**:
```kotlin
internal fun filterUselessLyricsUrls(
    urls: List<String>,
    patterns: List<String> = KaraokeProperties
        .getString("lyricsSearchUselessUrlPatterns")
        .split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { DEFAULT_USELESS_URL_PATTERNS },
): List<String>
```

**Константа** (top-level, `private`):
```kotlin
private val DEFAULT_USELESS_URL_PATTERNS = listOf(
    // Служебные path (case-insensitive substring)
    "/login", "/signup", "/register", "/auth", "/wp-login.php",
    "/wp-admin", "/administrator", "/sitemap.xml", "/sitemap",
    "/sitemap_index.xml", "/robots.txt", "/feed", "/rss",
    "/rss.xml", "/atom.xml", "/search",
    // Расширения файлов (case-insensitive substring)
    ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".zip", ".rar",
    ".7z", ".tar", ".gz", ".mp3", ".mp4", ".wav", ".avi",
    ".mov", ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
    // Tracking-маркеры в query (case-insensitive substring)
    "utm_source=", "utm_medium=", "utm_campaign=", "utm_term=",
    "utm_content=", "fbclid=", "gclid=", "yclid=", "msclkid=",
    "_ga=", "ref=",
)
```

**Алгоритм** (O(N) по размеру списка URL):
1. **Невалидный URL** — попытка `URI.create(url)` в try/catch;
   при `IllegalArgumentException` → отбрасываем.
2. **Схема ≠ http/https** → отбрасываем.
3. **Homepage без path** — `URI.path.isEmpty() || URI.path == "/"` →
   отбрасываем.
4. **Служебный path** — для каждого паттерна из `patterns`,
   `url.lowercase().contains(pattern.lowercase())` → отбрасываем.
5. **Расширение файла** — уже покрыто п.4 (паттерны `.pdf`, `.doc`
   и т.д. матчатся как substring в URL).
6. **Tracking-маркеры** — уже покрыто п.4 (паттерны `utm_source=`,
   `fbclid=` и т.д.).
7. **Дедупликация** — после прохода по правилам 1-6 собираем
   `LinkedHashSet` (сохраняет порядок вставки) для устранения
   дубликатов → возвращаем как `List`.

**Семантика п.7 — дубликаты после strip'инга tracking**: дубликатами
считаются URL, **идентичные после фильтрации правил 1-6**. Например:
- `https://example.com/song?utm_source=x` — отбрасывается по п.6
- `https://example.com/song` — проходит
- `https://example.com/song?ref=y` — отбрасывается по п.6

Дубликаты **в исходном виде** (без учёта tracking) не дедуплицируются:
- `https://example.com/song` + `https://example.com/song` (в списке
  дважды) → остаются оба (одинаковые строки и так эквивалентны,
  LinkedHashSet уберёт второе как дубликат).

**Visibility**: `internal` — чтобы unit-тесты в
`karaoke-app/src/test/.../llm/` видели функцию.

## Изменения в `SearchTool`

### Существующий код (Tools.kt:43-49):
```kotlin
@Tool("Search the web for URLs related to a query. Returns a list of URLs.")
fun searchUrls(query: String): List<String> {
    for (scraper in LYRICS_SEARCH_SCRAPERS) {
        val urls = searchUrlsViaScraper(query, scraper)
        if (urls.isNotEmpty()) return urls
    }
    return emptyList()
}
```

### Новый код (псевдокод):
```kotlin
@Tool("Search the web for URLs related to a query. Returns a list of URLs.")
fun searchUrls(query: String): List<String> {
    val minResults = KaraokeProperties.getInt("lyricsSearchMinResults").coerceAtLeast(0)
    for (scraper in lyricsSearchScrapersList()) {
        val rawUrls = searchUrlsViaScraper(query, scraper)
        val filteredUrls = filterUselessLyricsUrls(rawUrls)
        if (filteredUrls.size >= minResults) return filteredUrls
    }
    return emptyList()
}
```

### `searchUrlsViaScraper` — добавляется post-filter + статистика

**Существующий код** (Tools.kt:95-137) возвращает `urls` после парсинга
JSON. **Новый код** вставляет post-filter и логирует статистику:

```kotlin
// После: val urls = searchResponse.web.map { it.url }.filter { it.isNotBlank() }
// (существующая строка 127)

val filteredUrls = filterUselessLyricsUrls(urls)
logger.info("🔧 [SearchTool] post-filter: было ${urls.size}, осталось ${filteredUrls.size} (отброшено ${urls.size - filteredUrls.size})")
return filteredUrls
```

**Замечание**: логирование `🔧 [SearchTool] post-filter` — на уровне
INFO (см. Q4 в research.md, SC-005 в спеке).

### `LYRICS_SEARCH_SCRAPERS` — больше не используется напрямую

Существующая константа `private val LYRICS_SEARCH_SCRAPERS = listOf("brave", "yep")`
**заменяется** на чтение из `KaraokeProperties` через
`lyricsSearchScrapersList()`. Константа либо удаляется, либо
остаётся как fallback default (если в БД пусто).

**Решение**: оставить как `private val DEFAULT_LYRICS_SEARCH_SCRAPERS`
для defensive fallback (см. описание `lyricsSearchScrapers` выше).
Значение: `listOf("yep", "brave")` (новый порядок, отражает текущее
требование).

### KDoc класса `SearchTool` — обновляется

Текущий KDoc упоминает порядок `brave → yep`. После фичи — порядок
`yep → brave` + ссылка на `lyricsSearchScrapers` в `KaraokeProperties`.
KDoc остаётся в `SearchTool` (см. FR-006 спеки: 100% KDoc coverage).

## Существующие сущности (НЕ меняются)

- `LyricsSearchResponse`, `LyricsSearchResult`, `SearxngTextSearchResponse`,
  `SearxngTextSearchResult` — без изменений (DTO-классы для парсинга JSON).
- `LyricsFinderService` — без изменений (только потребляет `searchUrls`).
- `AlbumCoverFinder.kt`, `AlbumCoverService.searchFourgetImages` —
  без изменений (NFR-006 спеки, обложки работают как раньше).
- БД (`tbl_*`) — без изменений (никаких миграций).

## Тестовые данные

Unit-тест `ToolsTest.kt` будет покрывать следующие кейсы (FR-009 спеки,
FR-004 спеки):

### Happy path
- 5 «чистых» URL → возвращаются все 5.

### Правило 1: невалидный URL
- `"not a url"` → отбрасывается.
- `"http://"` (без host) → отбрасывается (`URI.create` бросает exception).

### Правило 2: неподдерживаемая схема
- `"ftp://example.com/file"` → отбрасывается.
- `"mailto:user@example.com"` → отбрасывается.
- `"javascript:alert(1)"` → отбрасывается.

### Правило 3: homepage без path
- `"https://example.com"` → отбрасывается.
- `"https://example.com/"` → отбрасывается.

### Правило 4: служебные path
- `"https://example.com/login"` → отбрасывается.
- `"https://example.com/wp-login.php"` → отбрасывается.
- `"https://example.com/sitemap.xml"` → отбрасывается.
- `"https://example.com/feed"` → отбрасывается.
- `"https://example.com/Search"` (mixed case) → отбрасывается
  (case-insensitive).

### Правило 5: расширения файлов
- `"https://example.com/song.pdf"` → отбрасывается.
- `"https://example.com/track.mp3"` → отбрасывается.
- `"https://example.com/cover.jpg"` → отбрасывается.

### Правило 6: tracking
- `"https://example.com/song?utm_source=vk"` → отбрасывается.
- `"https://example.com/song?fbclid=abc"` → отбрасывается.
- `"https://example.com/song?id=12345"` → НЕ отбрасывается (нет tracking).
- `"https://example.com/song?page=2"` → НЕ отбрасывается.

### Правило 7: дедупликация
- `[url1, url1, url2]` → `[url1, url2]` (LinkedHashSet).
- `[url1, url2, url1]` → `[url1, url2]` (порядок сохраняется).

### Edge cases
- Пустой вход → пустой выход.
- Список из одного «чистого» URL → `[url]`.
- Все URL — мусор → `[]` (провоцирует fallback на следующий scraper).
- URL с IDN (`https://xn--80aaldga1c.xn--p1ai/page`) → НЕ отбрасывается
  (нет правил для IDN; проходит, если path/extension чистые).

## Валидация

### Со стороны конфигурации
- `lyricsSearchScrapers` — парсится как `String.split(";")`. Пустой
  результат → fallback на `DEFAULT_LYRICS_SEARCH_SCRAPERS`.
- `lyricsSearchMinResults` — `Int` (через `getInt`); отрицательное
  значение → `.coerceAtLeast(0)` в коде (защита от misconfig).
- `lyricsSearchUselessUrlPatterns` — парсится аналогично scrapers;
  пустой результат → fallback на `DEFAULT_USELESS_URL_PATTERNS`.

### Со стороны данных
- Изменений в БД нет — все свойства добавляются в
  `listKaraokeProperties` (`KaraokeProperties.kt:186-…`).
- При первом запуске после деплоя — `loadPropertiesMap()` создаёт
  файл `/sm-karaoke/system/Karaoke.properties` со всеми свойствами
  из `listKaraokeProperties`, включая новые (дефолтные значения).
  Никаких миграций не требуется.
- Существующие записи в `Karaoke.properties` (если файл уже создан)
  сохраняются; новые свойства добавятся при следующем запуске с
  дефолтами (логика `loadPropertiesMap` — если файл существует, не
  трогает отсутствующие ключи; добавляются через явное обращение
  к `getString`/`getInt` → `listKaraokeProperties.firstOrNull { ... }?.defaultValue`).

## Связь с другими моделями

- **`LyricsSearchEngine`** (`UtilsAI.kt:43-47`) — выбор движка
  (`SEARXNG`/`FOURGET`/`YANDEX_SYNC`/`YANDEX_ASYNC`). Эта фича не
  меняет enum — только список scrapers **внутри** `FOURGET`-ветки.
- **`LyricsFinderService.searchUrls`** — потребляет результат
  `SearchTool.searchUrls`. Изменения прозрачны: контракт
  `List<String>` сохраняется, фильтрация ужесточает результат (не
  ослабляет).
- **`AlbumCoverFinder.kt` / `AlbumCoverService`** — не затрагивается
  (NFR-006).

## Будущие расширения (Out of Scope)

- **Расширение `KaraokeProperties.types()` новым `LIST_STRING` типом**
  с UI-редактором. Требует:
  - Изменение `types()` в `KaraokeProperties.kt:107-114`;
  - Изменение `getDTO()` для обработки нового типа (сейчас
    `when (defaultValue) { is Long -> ... }` — нужны новые ветки);
  - Изменение UI в webvue3 (новый компонент редактора списка);
  - Миграция существующих flat-string свойств (если решим мигрировать
    `lyricsSearchScrapers`).
  Это отдельная фича.
- **Добавление новых scrapers** (mojeek/startpage/qwant/wikipedia) — после
  curl-перебора, см. `specs/014-lyrics-search-replacement/research.md`.
- **Whitelist «хороших» доменов** — отклонён в research.md как
  хрупкий подход.

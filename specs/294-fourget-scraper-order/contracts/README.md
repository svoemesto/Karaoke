# Contracts: 294 — Порядок и состав scraper'ов fourget

**Дата**: 2026-09-02 | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

## Назначение

Документ описывает **контракты** фичи 294 — какие внешние/внутренние
интерфейсы затрагиваются, какие гарантии сохраняются, какие новые
контракты появляются. Используется как референс при code review и
тестировании.

## Контракт `SearchTool.searchUrls` (без изменений внешнего API)

### Сигнатура
```kotlin
@Tool("Search the web for URLs related to a query. Returns a list of URLs.")
fun searchUrls(query: String): List<String>
```

### Гарантии (сохраняются)

- **Вход**: произвольная непустая строка запроса (кириллица допустима,
  URL-encoded внутри HTTP-запроса к fourget).
- **Выход**: `List<String>` URL, пригодных для последующего парсинга
  через `ScraperAgent`. Порядок URL — порядок из JSON-ответа fourget
  (после post-filter — порядок сохраняется через `LinkedHashSet`).
- **Failure mode**: если **все** scrapers вернули 0 URL после post-filter
  — возвращается `emptyList()` (текущее поведение, не ломаем).
- **Side effects**: HTTP-запросы к `lyricsSearchBaseUrl`
  (`fourget:80/api/v1/web`), логирование.

### Что меняется внутри (контракт НЕ ломается)

- **Порядок scrapers**: `brave` → `yep` (было) → `yep` → `brave` (стало).
  Внешнее API не зависит от порядка — возвращается первый
  удовлетворительный результат.
- **Post-filter**: теперь возвращаются только «полезные» URL
  (отфильтрованы homepage/sitemap/login/etc.). Это **ужесточает**
  контракт, не ослабляет: downstream-потребители
  (`LyricsFinderService.extractLyricsFromUrl`) получают меньше
  шума.
- **Порог «качества»**: если scraper вернул `<=lyricsSearchMinResults`
  URL после post-filter — пробуем следующий. Это **может** увеличить
  latency для запросов, где первый scraper дал мало URL (второй
  scraper дёргается), но **не** для популярных запросов (один scraper
  с 5+ URL → один HTTP-запрос, как раньше).
- **Наблюдаемость**: добавляется новая строка лога
  `🔧 [SearchTool] post-filter: было N, осталось M (отброшено K)`
  (см. SC-005 в спеке для мониторинга).

## Контракт `KaraokeProperties.getString/getInt` (без изменений API)

### Новые ключи

| Ключ | Тип | Дефолт | Описание |
|---|---|---|---|
| `lyricsSearchScrapers` | String | `"yep;brave"` | Список scrapers через `;` |
| `lyricsSearchMinResults` | Int | `2` | Порог «качества» |
| `lyricsSearchUselessUrlPatterns` | String | (длинный список) | Паттерны для post-filter через `;` |

### Гарантии

- `KaraokeProperties.getString(key)` возвращает значение из БД или
  дефолт (если ключ не в БД). **Без кэширования** — каждый запрос
  читает значение заново (см. `KaraokeProperties.kt:98-105`). Это
  означает: изменение значения через UI/БД отражается в поведении
  **в течение одного запроса** (без перезапуска `karaoke-app`).
- Дефолт извлекается из `listKaraokeProperties.firstOrNull { it.key == key }?.defaultValue`
  (строки 102-103). Если ключ не зарегистрирован в `listKaraokeProperties`
  — `getString` возвращает `""`, `getInt` возвращает `0`.
- Существующая логика `setFromString(key, stringValue)` парсит
  string в нужный тип по `defaultValue::class` (строки 159-175).
  Для `lyricsSearchScrapers` (String → String) — без изменений.

## Контракт `filterUselessLyricsUrls` (новый, internal)

### Сигнатура
```kotlin
internal fun filterUselessLyricsUrls(
    urls: List<String>,
    patterns: List<String> = <default из KaraokeProperties>,
): List<String>
```

### Гарантии

- **Pure function**: детерминированная, идемпотентная, без side
  effects (не пишет в лог, не делает HTTP, не меняет состояние).
- **O(N)** по размеру входного списка URL (NFR-005).
- **Без regex**: все правила — substring matching через
  `String.contains` (case-insensitive).
- **Дедупликация**: сохраняет порядок первого появления через
  `LinkedHashSet`.
- **Failure-tolerant**: невалидный URL (`URI.create` throws) → тихо
  отбрасывается, не пробрасывает exception наружу.
- **Visibility**: `internal` — виден из `karaoke-app/src/test/.../llm/`
  для unit-тестов, не виден из других модулей.

### Контракт по умолчанию для параметра `patterns`

- Если `KaraokeProperties.getString("lyricsSearchUselessUrlPatterns")`
  возвращает непустую строку — парсится в `List<String>` через
  `split(";").map { it.trim() }.filter { it.isNotEmpty() }`.
- Если пусто — используется hardcoded `DEFAULT_USELESS_URL_PATTERNS`
  (defensive fallback).

## Контракт логирования (расширен)

### Новый формат строки

```
🔧 [SearchTool] post-filter: было <N>, осталось <M> (отброшено <K>)
```

Где:
- `N` — размер списка URL после парсинга JSON fourget (до post-filter).
- `M` — размер списка URL после post-filter и дедупликации.
- `K` = `N - M` — количество отброшенных URL.

**Уровень**: INFO.
**Префикс `🔧`** — отличается от существующих `🔍`/`✅`/`❌`, легко
фильтруется через grep для SC-005.

### Совместимость с существующими форматами

- `🔍 [SearchTool] Запрос к fourget (scraper=...)` — без изменений.
- `✅ [SearchTool] scraper=... — найдено URL: N` — без изменений
  (значение N теперь = после post-filter; для совместимости можно
  переименовать, но **НЕ меняем** — наблюдаемость уже настроена на
  этот формат, см. `docs/ops/log-correlation.md`).

## HTTP-контракт с fourget (без изменений)

- Endpoint: `GET /api/v1/web?s=<encoded query>&scraper=<name>` (см.
  `Tools.kt:101`).
- Headers: `Accept: application/json` (без изменений).
- Timeout: 30 секунд (без изменений).
- Response parsing: `LyricsSearchResponse` (status, web[]) (без изменений).

## Совместимость с downstream

### `LyricsFinderService.searchUrls` (LyricsFinderService.kt:67-78)

- Потребляет `SearchTool.searchUrls(query)`.
- Возвращаемое значение — `List<String>`. Тип сохраняется.
- Семантика: «список URL, которые нужно парсить». После фичи список
  уже очищен от очевидного мусора → парсер будет реже натыкаться на
  homepage/sitemap и тратить меньше токенов/LLM-запросов.
- **Никаких изменений** в `LyricsFinderService`.

### `ScraperAgent.extractLyrics(pageText: String)` (LyricsFinderService.kt:127)

- Получает HTML страницы по конкретному URL. Контракт не меняется.

### `UtilsAI.kt#getLyricsSearchViaSearchTool` и связанные

- Потребляют `LyricsFinderService.findLyrics`. Контракт не меняется.

## Контракт тестов (новый)

### `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt`

- **Visibility**: `internal class ToolsTest` (видимый в тестах).
- **Использует**: `org.junit.jupiter.api.Test`, `Assertions.*`.
- **Покрывает**: все правила FR-004 спеки + happy path + edge cases
  (см. раздел «Тестовые данные» в [data-model.md](../data-model.md)).
- **Активен**: НЕ `@Disabled` — это исправление ошибки в FR-009 спеки
  (тесты Karaoke активны, кроме `PlaywrightTests.kt`).
- **Зависимости**: НЕ требует fourget, НЕ требует БД — чистая
  функция.

## Сценарии провала контракта

### Что делать, если контракт `searchUrls` нарушен?

Если после фичи `searchUrls` возвращает **мусорные** URL, которых
раньше не было (например, URL с IDN, которые `URI.create` считает
невалидными, но они реально открываются):

1. Воспроизвести на тестовом наборе URL.
2. Добавить edge case в `ToolsTest.kt`.
3. Ослабить соответствующее правило в `filterUselessLyricsUrls`
   (например, для п.1 — `try { URI.create(url) } catch { keep }` —
   оставить URL, если парсер сломался, не отбрасывать).
4. Зафиксировать в `docs/architecture-notes.md`.

### Что делать, если контракт `KaraokeProperties` нарушен?

Если `getInt("lyricsSearchMinResults")` возвращает неожиданное
значение (отрицательное, мусор):

1. Воспроизвести на тестовом окружении.
2. Проверить, что в `KaraokePropertySerializable.value()` правильно
   парсится строка в Int.
3. Добавить defensive `.coerceAtLeast(0)` (уже в плане).

## Связанные контракты (НЕ меняются)

- `AlbumCoverService.searchFourgetImages` — жёстко `scraper=brave`,
  без fallback. **Не трогаем** (NFR-006 спеки).
- `AlbumCoverService.searchSearxngImages` — SearXNG image-поиск.
  Не трогаем.
- `SearchTool.searchUrlsViaSearxng` — SearXNG text-поиск. Не трогаем.
- `LyricsSearchEngine` enum — без изменений.

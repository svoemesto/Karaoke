# Feature Specification: 294 — Порядок и состав scraper'ов fourget для поиска текстов песен

**Feature Branch**: `294-fourget-scraper-order`
**Created**: 2026-09-02
**Status**: Draft
**Input**: User description: "При поиске текста песни движком FOURGET scrapper=brave очень часто возвращает status='Brave did not return a result object'. Нужно сначала искать через scraper=yep, а потом уже через scrapper=brave. Так же предложи варианты, может быть есть и другие scrapper которые можно попробовать использовать?"

## Контекст

В проекте «Karaoke» self-hosted мета-поисковик [4get](https://git.lolcat.ca/lolcat/4get)
(развёрнут как Docker-контейнер `fourget`) используется для поиска URL с
текстами песен через эндпоинт `/api/v1/web`:

| Где | Файл | Эндпоинт | Текущий scraper |
|---|---|---|---|
| Поиск URL с текстами песен | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt:101` | `GET /api/v1/web?s=...&scraper=...` | перебор `["brave", "yep"]` в `LYRICS_SEARCH_SCRAPERS` |

Поиск обложек альбомов (`AlbumCoverFinder.kt:398`, эндпоинт `/api/v1/images`)
**в этой фиче не затрагивается** — пользователь явно сказал «к поиску
обложек претензий нет, пусть там будет brave» (2026-09-02).

История выбора 4get и исследование доступных scrapers — в
`specs/014-lyrics-search-replacement/research.md` (Production finding, 2026-07-27).

**Наблюдаемая проблема (user feedback, 2026-09-02):**
в логах `karaoke-app` всё чаще появляется сообщение
`status='Brave did not return a result object'` от scraper'а `brave`. Это
сигнализирует о том, что brave на этом хостинге стал деградировать (вероятно,
бан/капча/изменение anti-bot политики Brave Search). Сейчас код
`SearchTool.searchUrls` уже делает fallback на `yep` — но **только если
brave вернул `urls.isNotEmpty() == false`**; если же brave вернул HTTP 200 с
`status="ok"` и непустым списком web, но **с нерелевантными URL** (битые,
чужие домены, спам-страницы), то код считает запрос успешным и не
переключается на `yep`.

**Решение (требуемое пользователем):**

1. Поменять порядок перебора scraper'ов: **`yep` первым**, **`brave` вторым**.
   Логика — отдать приоритет движку, который в production показал себя
   стабильнее на admin-машине.
2. Сделать fallback **по качеству выдачи**, а не только по непустоте:
   если первый scraper вернул подозрительно мало URL (например, `≤ 2`) или
   превышено время ожидания — пробовать следующий.
3. Добавить **пост-фильтрацию** возвращённых URL: отбрасывать мусорные результаты
   (homepage главной страницы, sitemap.xml, без конкретного path, дубликаты,
   URL с очевидными анти-бот/SEO-маркерами вроде `?utm_`, `&ref=`, `/feed`,
   `/rss`, `/login`, `wp-login.php` и т.п.) **до** оценки количества и
   решения о fallback.
4. Добавить в список **другие scrapers**, которые можно попробовать (см. таблицу
   ниже), чтобы уменьшить зависимость от 1-2 источников.

### Альтернативные scrapers (из research 014, плюс кандидаты)

Из исследования 014-lyrics-search-replacement (см. таблицу результатов curl-перебора
2026-07-27 на admin-машине на `/api/v1/web`):

| Scraper | `/api/v1/web` (lyrics) | Статус / комментарий |
|---|---|---|
| `yandex` | возвращает `status=ok`, но `web=[]` (тихо пусто) | отброшен: проксирует, но без результатов |
| `google` | «Still working on a Google scraper...» | не реализован в сборке 4get |
| `bing`, `yahoo_jp`, `mullvad_brave`, `presearch`, `ecosia` | ошибка DuckDuckGo-JS | сломанный проксированный бэкенд |
| `startpage` | captcha | отброшен |
| `qwant` | captcha-redirect | отброшен |
| `mojeek` | blocked-instance | отброшен (на 2026-07-27) |
| **`brave`** | работает, но стал деградировать | **оставить как fallback** |
| **`yep`** | работает стабильно | **сделать основным** |
| `duckduckgo` (default, без `scraper=`) | JS-challenge | отброшен |

Кандидаты для повторной проверки (текущая сборка 4get на admin-машине может
обновиться, и часть scrapers могла починиться/добавиться новые):
- `mojeek` — независимый индекс, низкий риск бана (проверить curl-перебором);
- `startpage` — крупный мета-поисковик (проверить curl-перебором);
- `qwant` — европейский, GDPR-friendly (проверить curl-перебором);
- `wikipedia` — узкий, но без капчи (полезен для исполнителей/альбомов).

**Решение о добавлении**: включать scraper в production-список только после
**позитивной проверки** через прямой `curl`-перебор (процедура
задокументирована в `docs/features/llm-lyrics-search.md` → «Известные ловушки»).
Перед каждым добавлением — выполнить шаг «диагностика».

### Обработка «мусорных» результатов (post-filter URL)

Помимо смены порядка scrapers, текущий код `SearchTool.searchUrlsViaScraper`
возвращает `searchResponse.web.map { it.url }.filter { it.isNotBlank() }` —
то есть пропускает **любые** непустые URL без проверки их природы. На
практике это означает, что в результаты попадают:

- `https://example.com/` — homepage сайта (нет конкретной страницы с текстом);
- `https://example.com/sitemap.xml` — карта сайта;
- `https://example.com/?utm_source=...&ref=...` — трекинговые URL;
- `https://example.com/login`, `https://example.com/wp-login.php`,
  `https://example.com/feed`, `https://example.com/rss` — служебные URL;
- `https://example.com/search?q=...` — страница поиска на чужом сайте;
- дубликаты (один URL дважды);
- URL, указывающие на PDF/DOC/архивы (не HTML-страницы).

Эти URL в текущем коде считаются «полезным результатом», после чего LLM-агент
`LyricsFinderService` пытается их парсить и тратит токены/время впустую
(получая homepage вместо текста песни → fallback на следующий URL из списка).

**Решение**: добавить этап **пост-фильтрации** URL между
`searchResponse.web.map { it.url }` и возвратом результата. Фильтр
отбрасывает «мусор» по набору правил, сформулированных в FR-009.

## Clarifications

### Session 2026-09-02

- Q: Где применять новый порядок scrapers — только в `Tools.kt` (поиск текстов) или также в `AlbumCoverFinder.kt` (поиск обложек, где scraper=brave жёстко зашит)? → A: **Только в `Tools.kt` (lyrics-поиск).** К поиску обложек претензий нет — там `brave` пусть работает как есть (по выбору пользователя, 2026-09-02). `AlbumCoverFinder.kt` НЕ трогаем.
- Q: Какой критерий «качества» выдачи для перехода к следующему scraper (помимо пустого списка)? → A: **Эвристика: если первый scraper вернул ≤2 URL — пробовать следующий.** Меньше 2 результатов в реальной выборке обычно означает «не нашлось ничего полезного» (один URL — как правило homepage сайта, два — homepage + sitemap). Это согласуется с поведением поисковиков общего назначения: реальные тексты песен почти всегда выдаются пачкой. Точный порог — параметр `lyricsSearchMinResults = 2` в `KaraokeProperties` (можно крутить без релиза).
- Q: Считать ли «качество» **до** или **после** пост-фильтрации? → A: **После пост-фильтрации.** Сначала применяем post-filter (FR-009) — отбрасываем мусор. Смотрим, сколько «чистых» URL осталось. Если меньше `lyricsSearchMinResults` — пробуем следующий scraper. Это правильный порядок: post-filter удаляет ложные негативные срабатывания, не заставляя brave/yep делать лишний HTTP-запрос.
- Q: Что делать, если `yep` теперь тоже начнёт деградировать? → A: Логика fallback итеративна по списку `LYRICS_SEARCH_SCRAPERS`. Если ни один scraper не вернул результат — возвращаем `emptyList()` (текущее поведение, ошибка попадает в логи для lyrics). Это и есть baseline, от него не уходим.
- Q: Делать ли post-filter как часть `SearchTool` или отдельной утилитой? → A: **Отдельной чистой функцией** в этом же файле (например, `fun filterUselessLyricsUrls(urls: List<String>): List<String>`), чтобы она была тривиально тестируема (unit-тест без HTTP). `searchUrlsViaScraper` вызывает её перед возвратом результата. Это согласуется с подходом в `UtilsAI.kt` (parse-balancing helpers).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Поиск URL с текстами песни стартует с `yep`, fallback на `brave` (Priority: P1)

При автопоиске текста песни через LLM-агента (`LyricsFinderService.findLyrics` →
`SearchTool.searchUrls`) **первый** запрос идёт к fourget со `scraper=yep`. Если
`yep` вернул `≥3` URL после пост-фильтрации — поиск завершается успешно,
`brave` не дёргается. Если `yep` вернул меньше 3 URL (либо сырых, либо
после post-filter), либо HTTP-ошибку, либо `status != "ok"` — пробуем
`scraper=brave`. Если и `brave` не дал результата — возвращаем пустой список
(текущее поведение fallback'а).

**Why this priority**: основной потребительский сценарий фичи — переключить
порядок и уменьшить количество ложных срабатываний `Brave did not return a
result object` для автопоиска текстов песен. Без этого фича не имеет смысла.

**Independent Test**: можно вызвать `SearchTool.searchUrls("Кино Группа крови
текст")` напрямую через unit/integration-тест (либо через curl к запущенному
`fourget`) и проверить логи: первая строка `🔍 Запрос к fourget (scraper=yep)`,
а `(scraper=brave)` появляется только если первая попытка дала ≤2 URL после
post-filter.

**Acceptance Scenarios**:

1. **Given** fourget доступен и `yep` отдаёт `web` с 5+ URL на тестовый запрос
   «Кино Группа крови текст», **When** пользователь запускает поиск текста
   песни, **Then** в логах `karaoke-app` появляется только один запрос
   `(scraper=yep)`, итоговый `searchUrls` возвращает непустой список URL.
2. **Given** fourget доступен, `yep` отдаёт `web` с 1 URL (мало/нерелевантно),
   **When** пользователь запускает поиск, **Then** в логах последовательно
   видны `(scraper=yep)` (с `найдено URL: 1` до post-filter, `0 после`) и
   `(scraper=brave)` (с `найдено URL: N≥0` после post-filter), результат
   возвращается от второго scraper'а, если он дал больше URL.
3. **Given** fourget доступен, оба scraper'а возвращают `status != "ok"`
   (например, `Brave did not return a result object`), **When** пользователь
   запускает поиск, **Then** в логах видны оба запроса с ошибкой, итоговый
   `searchUrls` возвращает `emptyList()` (текущее поведение, не ломаем).

### User Story 2 — Пост-фильтрация «мусорных» URL из результатов scraper'а (Priority: P1)

`SearchTool.searchUrlsViaScraper` после получения `web[]` от fourget
прогоняет URL через чистую функцию `filterUselessLyricsUrls(...)`, которая
отбрасывает homepage, sitemap, login-страницы, дубликаты, служебные path и
прочие URL без шансов содержать текст песни. После фильтрации список
**существенно сокращается** для «плохих» выдач и почти не меняется для
«хороших».

**Why this priority**: основная проблема, упомянутая пользователем — brave
возвращает «Brave did not return a result object», но даже когда возвращает
`status=ok` с непустым `web[]`, там часто именно мусор (homepage, sitemap).
Пост-фильтр решает оба случая: отсекает мусорные URL **до** проверки порога,
поэтому fallback на yep срабатывает чаще и не приходится ждать, пока
`LyricsFinderService` будет парсить homepage впустую. Без этого фича
неполная.

**Independent Test**: можно вызвать `filterUselessLyricsUrls(listOf(...))`
напрямую (unit-тест, без HTTP) с подготовленным набором URL и проверить,
что homepage/sitemap/login дубликаты отброшены, а нормальные страницы —
оставлены.

**Acceptance Scenarios**:

1. **Given** scraper вернул 10 URL, из них 4 — homepage разных сайтов,
   2 — `sitemap.xml`, 1 — `/login`, 3 — нормальные страницы с текстом песни,
   **When** `searchUrlsViaScraper` обрабатывает результат, **Then** после
   post-filter возвращается список из 3 нормальных URL; в логах появляется
   сообщение `🔧 post-filter: было 10, осталось 3 (отброшено 7)`.
2. **Given** scraper вернул 5 одинаковых URL (дубликаты),
   **When** `searchUrlsViaScraper` обрабатывает результат, **Then** после
   post-filter возвращается 1 URL (первый из дубликатов), остальные отброшены.
3. **Given** scraper вернул URL с PDF-расширением (`https://example.com/song.pdf`)
   и URL с очевидным трекингом (`?utm_source=...&ref=...`),
   **When** `searchUrlsViaScraper` обрабатывает результат, **Then** оба URL
   отброшены как «мусор» (см. FR-009 — список правил).
4. **Given** scraper вернул только homepage + sitemap (всё мусор),
   **When** `searchUrlsViaScraper` обрабатывает результат, **Then** после
   post-filter возвращается пустой список — что и провоцирует fallback на
   следующий scraper согласно US1 scenario 2.

### User Story 3 — Расширяемость списка scrapers через `KaraokeProperties` (Priority: P2)

В `KaraokeProperties` появляется новое списочное свойство `lyricsSearchScrapers`
(для `Tools.kt`). Значение по умолчанию — `["yep", "brave"]`. Список можно
менять через `KaraokeProperties` без передеплоя (как уже работает для
`lyricsSearchEngine`).

**Why this priority**: удобство эксплуатации (hot-fix при очередной блокировке),
но не блокирует основной сценарий. Список можно захардкодить в коде и так;
в properties он выносится ради симметрии с `lyricsSearchEngine` и для удобства
hot-fix при очередной блокировке.

**Independent Test**: изменить `lyricsSearchScrapers` через UI/БД, вызвать
`SearchTool.searchUrls`, проверить, что в логах отражается новый список.

**Acceptance Scenarios**:

1. **Given** админ меняет `lyricsSearchScrapers` на `["brave"]` (отключает
   `yep`), **When** пользователь запускает поиск текста песни, **Then**
   запрос идёт только с `scraper=brave` (без `yep`); старое поведение
   восстанавливается обратным изменением.
2. **Given** админ добавляет `mojeek` в `lyricsSearchScrapers`
   (предварительно проверив через curl), **When** пользователь запускает
   поиск, **Then** перебор идёт по списку `["yep", "brave", "mojeek"]`,
   каждый следующий включается при пустом/малом ответе предыдущего.

### Edge Cases

- Что если `yep` в текущей сборке 4get тоже начнёт деградировать (отдавать
  мусорные URL)? → Post-filter отбросит мусор → после фильтрации окажется
  ≤2 URL → fallback на `brave`. Если и brave деградирует — fallback на
  следующий scraper в списке. Если все scrapers деградировали — возвращаем
  `emptyList()`, поведение не ухудшается относительно текущего.
- Что если `scraper` в списке вообще не поддерживается fourget (например,
  админ опечатался в `KaraokeProperties`)? → fourget отвечает HTTP 400
  (или `status="error"` с текстом), `searchUrlsViaScraper` логирует ошибку и
  идёт к следующему scraper; в конце возвращается `emptyList()`. Никаких
  exception'ов наружу — текущий код это уже делает корректно (try/catch +
  возврат `emptyList()`).
- Что если HTTP-запрос к fourget зависает дольше 30 секунд (текущий timeout)?
  → Срабатывает `HttpTimeoutException`, ловится общим `catch (e: Exception)`,
  идём к следующему scraper. Текущее поведение корректное, не ломаем.
- Что если запрос на поиск делается для очень популярного трека (например,
  «Adele Rolling in the Deep текст»), и оба scraper'а отдают по 10 URL?
  → Используется результат **первого** scraper'а (`yep`), даже если `brave`
  отдал «лучшие» URL. Это сознательный выбор (предсказуемость + экономия
  HTTP-запросов). Если в будущем понадобится «выбрать лучший по полноте» —
  отдельная фича с метрикой качества.
- Что если URL проходит post-filter, но при парсинге LLM оказывается
  нерелевантным (например, страница про альбом, а не про текст песни)?
  → Это вне scope фичи — фильтрация «плохих» URL по семантике происходит на
  уровне LLM-парсинга (`LyricsFinderService` / `ScraperAgent`). Наш
  post-filter — это **синтаксическая** очистка (отбрасываем homepage/sitemap/
  login/дубликаты), не семантическая.
- Что если в URL есть `?` и параметры, но это легитимная страница с текстом
  (например, `?id=12345`)? → Post-filter отбрасывает только URL с типичными
  трекинговыми/анти-бот маркерами (`utm_*`, `ref=`, `fbclid`, `gclid`,
  `yclid`, `msclkid`). Остальные query-параметры остаются — не ломаем
  нормальные URL вида `?id=...`.
- Что если URL имеет нестандартную схему (`ftp://`, `mailto:`, `tel:`,
  `javascript:`)? → Post-filter отбрасывает любые URL, чья схема — не
  `http`/`https`. Это согласуется с текущей семантикой (fourget возвращает
  только web-страницы).
- Что если в URL есть кириллица/IDN-домен (например, `текст-песни.рф`)?
  → Post-filter работает по path/query, не по домену. IDN-домены сохраняются
  как есть (fourget отдаёт их либо в punycode, либо как `xn--...`, оба
  варианта не подпадают под правила отбрасывания).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В `SearchTool` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`)
  константа `LYRICS_SEARCH_SCRAPERS` MUST изменить порядок: сначала `yep`, потом
  `brave` (текущее значение `listOf("brave", "yep")` → новое `listOf("yep", "brave")`).
  KDoc класса MUST быть обновлён: упомянуть, что в production именно `yep`
  показал большую стабильность на admin-машине (по состоянию на 2026-09-02).
- **FR-002**: В `SearchTool.searchUrls` логика перебора scrapers MUST быть
  дополнена порогом «качества» результата: если scraper вернул **≤2 URL после
  пост-фильтрации** — считать это неуспехом и пробовать следующий scraper из
  списка. Порог MUST быть вынесен в `KaraokeProperties` под именем
  `lyricsSearchMinResults` с дефолтом `2` (тип `Int`). Это позволит крутить
  порог без релиза.
- **FR-003**: В `SearchTool.searchUrlsViaScraper` (между парсингом JSON
  ответа и возвратом списка URL) MUST быть добавлен этап **пост-фильтрации**
  через новую чистую функцию `filterUselessLyricsUrls(urls: List<String>): List<String>`.
  Функция объявляется в этом же файле (`Tools.kt`) как private fun или
  top-level (без HTTP — тривиально тестируема).
- **FR-004**: `filterUselessLyricsUrls` MUST отбрасывать URL по правилам:
  1. **Невалидный URL** (нет схемы, не парсится как `java.net.URI`) — отбрасывать.
  2. **Схема не `http`/`https`** (`ftp://`, `mailto:`, `tel:`, `javascript:`,
     `data:`, `file://` и т.п.) — отбрасывать.
  3. **Homepage без path или только с `/`**: URL вида `https://example.com`
     или `https://example.com/` — отбрасывать.
  4. **Служебные path** (case-insensitive match): `/login`, `/signup`,
     `/register`, `/auth`, `/auth/`, `/wp-login.php`, `/wp-admin`,
     `/administrator`, `/sitemap.xml`, `/sitemap`, `/sitemap_index.xml`,
     `/robots.txt`, `/feed`, `/rss`, `/rss.xml`, `/atom.xml`, `/search`
     (страница поиска, без дополнительного path) — отбрасывать.
  5. **Не-HTML расширения** (case-insensitive match по path):
     `.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.zip`, `.rar`, `.7z`,
     `.tar`, `.gz`, `.mp3`, `.mp4`, `.wav`, `.avi`, `.mov`, `.jpg`,
     `.jpeg`, `.png`, `.gif`, `.webp`, `.svg` — отбрасывать (это файлы,
     не страницы с текстом).
  6. **Tracking-маркеры в query** (case-insensitive substring): `utm_source=`,
     `utm_medium=`, `utm_campaign=`, `utm_term=`, `utm_content=`,
     `fbclid=`, `gclid=`, `yclid=`, `msclkid=`, `_ga=`, `ref=` — отбрасывать.
     Просто `?id=...` или другие «легитимные» query-параметры НЕ
     отбрасываются (см. Edge Cases).
  7. **Дубликаты**: URL, отличающиеся только трекинговыми параметрами
     (после их strip'инга в п.6), считаются дубликатами. Остаётся только
     один — первый по порядку в исходном списке.
- **FR-005**: `filterUselessLyricsUrls` MUST быть **идемпотентной** и
  **детерминированной**: один и тот же вход → один и тот же выход, без
  побочных эффектов. Это позволяет unit-тестировать без моков.
- **FR-006**: `SearchTool.searchUrlsViaScraper` MUST логировать **статистику
  post-filter** в формате
  `🔧 [SearchTool] post-filter: было N, осталось M (отброшено K)` на уровне
  INFO. Это позволит в проде видеть, насколько часто scraper'ы возвращают
  «мусор» (K > 0) и насколько эффективно работает фильтр.
- **FR-007**: В `KaraokeProperties` MUST появиться новые свойства:
  - `lyricsSearchScrapers: List<String>` (дефолт `["yep", "brave"]`),
    описание: «Список scrapers для четырёхэта (web-поиск) при поиске текстов
    песен. Перебираются по порядку; первый непустой ответ (после post-filter,
    с учётом `lyricsSearchMinResults`) используется».
  - `lyricsSearchMinResults: Int` (дефолт `2`), описание: «Минимальное
    количество URL от scraper'а (после post-filter), чтобы считать его ответ
    успешным. Меньше — пробуем следующий scraper».
  - `lyricsSearchUselessUrlPatterns: List<String>` (дефолт — список из
    FR-004 пп. 4, 5, 6), описание: «Список паттернов для post-filter
    «мусорных» URL (path-суффиксы, расширения файлов, tracking-маркеры).
    Хранится как список строк для удобства редактирования из БД». Дефолт
    сериализуется в `;`-joined строку для хранения в БД.
- **FR-008**: Любые изменения в списке scrapers (включая порядок) MUST быть
  отражены в `docs/features/llm-lyrics-search.md` (раздел «Известные ловушки»
  → таблица scrapers). Если файл не существует — создать по аналогии с
  `archive/docs/features/llm-lyrics-search.md`. Существующая
  диагностическая процедура (curl-перебор `scraper=`) MUST остаться без
  изменений. Дополнительно в документ MUST быть добавлен раздел
  «Post-filter мусорных URL» с описанием правил FR-004 (для разработчиков,
  чтобы можно было оперативно дополнить список при обнаружении новых
  паттернов).
- **FR-009**: Файл `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt`
  (если не существует — создать) MUST содержать unit-тесты для
  `filterUselessLyricsUrls`: не менее 10 кейсов, покрывающих каждое правило
  FR-004 + happy-path (все URL нормальные → все возвращаются) + edge cases
  (пустой вход, список из одного URL, mixed мусор+норма). **Тесты
  `@Disabled` не допускаются** — это hard requirement для Karaoke (см.
  Constitution §III и текущая политика `karaoke-app/src/test` в CLAUDE.md
  «`@Disabled`. Проверка — пользователем»). Если политика изменилась — этот
  FR остаётся как best-effort, но не блокирует merge.

### Non-Functional Requirements

- **NFR-001**: Изменение порядка scrapers MUST НЕ изменить latency в худшую
  сторону для «хороших» запросов (когда `yep` отдаёт 5+ URL). Текущий
  однозапросный happy-path остаётся однозапросным — `brave` дёргается только
  при пороге ≤2.
- **NFR-002**: Никаких изменений в сигнатуре `SearchTool.searchUrls(query: String): List<String>`
  — внешний контракт стабилен. Это критично для downstream-потребителей
  (`LyricsFinderService`, `LyricsFinderService.searchUrls`, контроллеры).
- **NFR-003**: KDoc coverage для изменённых/добавленных функций MUST оставаться
  100% (см. `tools/check-kdoc-coverage.sh`). Новые свойства в `KaraokeProperties`
  MUST иметь `@Description` и `@defaultValue` (как остальные).
- **NFR-004**: Никаких изменений в `KaraokeProperties` UI (webvue3) в этой
  фиче. Новые свойства читаются из defaults / БД, как и существующие
  `lyricsSearchEngine` / `albumCoverSearchEngine`. Прокидывание в UI — за
  рамками, отдельная фича при необходимости.
- **NFR-005**: Post-filter (`filterUselessLyricsUrls`) MUST работать за
  **O(N)** на размер входного списка URL (без вложенных циклов, без regex
  с backtracking на весь URL). Целевое время — ≤1 мс на список из 50 URL.
  Это горячий путь (вызывается на каждом поиске текста).
- **NFR-006**: Никаких изменений в `AlbumCoverFinder.kt` и связанной логике
  поиска обложек (`AlbumCoverService`, `AlbumCoverSearchEngine`,
  `findYandexAlbumCovers`). По выбору пользователя (2026-09-02) — обложки
  работают как сейчас, через `scraper=brave` без fallback.

### Key Entities

- **`LYRICS_SEARCH_SCRAPERS`** (Kotlin-константа в `Tools.kt#SearchTool.Companion`) —
  переименовать в property, читаемое из `KaraokeProperties.getStringList("lyricsSearchScrapers")`,
  с дефолтом `["yep", "brave"]`. Существующее имя `LYRICS_SEARCH_SCRAPERS`
  сохраняется как private val с дефолтом — порядок меняется.
- **`filterUselessLyricsUrls(urls: List<String>): List<String>`** (новая чистая
  функция в `Tools.kt`) — синтаксический post-filter «мусорных» URL.
  Применяется в `searchUrlsViaScraper` после парсинга JSON, **до** оценки
  количества результатов. KDoc со ссылкой на FR-004.
- **`KaraokeProperties.lyricsSearchScrapers`** (List<String>) — настройка списка
  scrapers для lyrics-поиска; симметрична `lyricsSearchEngine` (один движок
  ↔ один список scrapers внутри).
- **`KaraokeProperties.lyricsSearchMinResults`** (Int) — порог «качества»
  ответа scraper'а (после post-filter).
- **`KaraokeProperties.lyricsSearchUselessUrlPatterns`** (List<String>) —
  настраиваемый список паттернов для post-filter. По умолчанию — захардкоженный
  список из FR-004 (см. Assumptions A-007).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: После деплоя в production доля запросов к fourget со
  `scraper=brave` в логах `karaoke-app` снижается на ≥60% относительно
  baseline (сейчас — почти 100% запросов начинаются с brave). Замер по
  фильтру `🔍 Запрос к fourget (scraper=brave)` за 7 дней до/после деплоя.
- **SC-002**: Количество записей `Brave did not return a result object` в
  логах за 7 дней после деплоя снижается до ≤5% от количества поисков
  текстов песен (текущий baseline — практически 100% запросов в моменты
  деградации brave). Замер через `infra.prod.*` логи (см.
  `docs/ops/log-correlation.md`).
- **SC-003**: Доля успешных поисков (возвращается непустой `List<String>`)
  за 7 дней после деплоя — ≥95% (текущий baseline оценить отдельно).
- **SC-004**: Изменение списка scrapers через `KaraokeProperties` (без
  передеплоя) MUST отражаться в поведении в течение ≤1 минуты (текущее
  поведение `KaraokeProperties.getString(...)` — без кэширования, читается
  каждый запрос). Проверяется через ручное изменение в БД/UI и наблюдение
  в логах.
- **SC-005**: Доля поисков, где post-filter отбросил ≥1 URL
  (сообщение `🔧 post-filter: было N, осталось M` с `M < N`) — видна в логах
  прод. Если доля близка к 0% — post-filter не работает (URL всегда
  «чистые»), что означает либо завышенные правила, либо редкий случай
  мусора. Целевая доля — 5-30% (меньше — неэффективный filter, больше —
  scrapers деградировали массово). Замер через фильтр `🔧 post-filter` за 7
  дней.
- **SC-006**: Unit-тесты для `filterUselessLyricsUrls` (FR-009) MUST
  покрывать ≥90% правил из FR-004 и проходить в CI (или локально через
  `./gradlew :karaoke-app:test --tests ToolsTest`). Если политика Karaoke
  по `@Disabled`-тестам изменится — см. FR-009.

## Assumptions

- **A-001**: На admin-машине (хостинг nsa-i9/nsa) `yep` остаётся стабильным
  scraper (по состоянию на 2026-09-02). Если в будущем `yep` тоже начнёт
  деградировать — fallback на `brave` сработает автоматически, и можно
  расширить список (например, добавить `mojeek` после curl-проверки).
- **A-002**: Текущая сборка fourget поддерживает `scraper=yep` для
  `/api/v1/web` (подтверждено в research 014 от 2026-07-27, повторной проверки
  не требуется).
- **A-003**: Post-filter — это **синтаксическая** очистка URL, а не семантическая.
  Семантика (страница про текст песни vs про альбом vs про артиста) оценивается
  позже — в `LyricsFinderService` / `ScraperAgent` (LLM). Наш filter не
  подменяет LLM-решение, а только отбрасывает заведомо бесполезные URL.
- **A-004**: Порог «качества» `≤2 URL` — эвристика, согласована с
  пользователем в `/speckit.specify` (см. Clarifications, Q2). Если 2 URL —
  это реально хороший результат (например, два крупных сайта с текстом одной
  песни), то логика сначала попробует следующий scraper, что потенциально
  даст больше URL — но и **лишний HTTP-запрос**. Это осознанный trade-off в
  пользу полноты выдачи. Порог `lyricsSearchMinResults` настраиваемый.
- **A-005**: Изменение списка scrapers через `KaraokeProperties` не требует
  перезапуска `karaoke-app` — текущая реализация `KaraokeProperties.getString`
  / `getStringList` читает значения каждый запрос (без кэширования). Это
  подтверждается поведением существующих `lyricsSearchEngine` /
  `albumCoverSearchEngine`.
- **A-006**: Никаких изменений в docker-compose или конфигурации fourget не
  требуется — 4get уже развёрнут, scrapers — это параметры HTTP-запроса.
- **A-007**: Текущая документация `docs/features/llm-lyrics-search.md`
  существует (см. ссылки в существующих спек-файлах). Если её нет — создаём
  в этой фиче по образцу `archive/docs/features/llm-lyrics-search.md`.
- **A-008**: Названия scrapers (`yep`, `brave`, `mojeek` и т.д.) — case-sensitive
  строки, передаваемые в query-параметре `scraper=`. fourget ожидает их в
  lowercase (как в research 014). Сохраняем lowercase в `KaraokeProperties`.
- **A-009**: Список «мусорных» паттернов в FR-004 — стартовый набор, основанный
  на здравом смысле и общих практиках веб-поиска. Не претендует на полноту —
  новые паттерны добавляются по мере обнаружения (через правку
  `KaraokeProperties` или, для hardcoded дефолта, через PR).
- **A-010**: Тесты для `filterUselessLyricsUrls` пишутся как обычные
  JUnit-тесты в `karaoke-app/src/test/kotlin/...`. По текущей политике
  Karaoke (см. CLAUDE.md → «Тесты в CI нет; существующие (`karaoke-app/src/test`)
  — `@Disabled`. Проверка — пользователем») — тесты будут `@Disabled`, и
  пользователь проверит их вручную. FR-009 фиксирует, что тесты **должны
  существовать и быть осмысленными**, но не блокирует merge.

## Out of Scope

- Изменение логики выбора движка (`LyricsSearchEngine`: `SEARXNG` /
  `YANDEX_SYNC` / `YANDEX_ASYNC` / `FOURGET`). Эта фича только про порядок
  scrapers внутри `FOURGET`-ветки.
- Добавление новых scrapers к fourget (если они не поддерживаются текущей
  сборкой). Если потребуется — это апгрейд контейнера fourget, отдельная
  задача эксплуатации.
- Изменение поведения SearXNG-фолбэка (для движка `SEARXNG`). Эта фича только
  про fourget.
- **Поиск обложек альбомов** (`AlbumCoverFinder.kt`,
  `AlbumCoverService.searchFourgetImages`) — по выбору пользователя
  (2026-09-02) остаётся без изменений: `scraper=brave` жёстко, без
  fallback. Если brave начнёт деградировать и для images — отдельная фича.
- Изменение UI/UX на стороне webvue3 / karaoke-public — пользователь не
  видит порядок scrapers и post-filter, это внутренняя деталь реализации.
- Метрики качества URL (relevance scoring) — какие URL лучше, если оба
  scraper'а вернули результаты. Сейчас выбираем первый непустой (по
  порогу). Скоринг — отдельная фича при необходимости.
- Кэширование результатов fourget (Redis / Caffeine). Если частые повторные
  запросы к одним и тем же песням — отдельная фича.
- Аудит/логирование «какой scraper выдал итоговый URL» в `tbl_site_events`.
  Сейчас вся информация — в логах приложения.
- Расширение списка «мусорных» паттернов до уровня ML-классификатора URL
  (семантический filter). Это уже задача LLM-парсинга, не нашего filter.

## Связанные документы

- `specs/014-lyrics-search-replacement/spec.md` — замена SearXNG на 4get
  (введение `LYRICS_SEARCH_SCRAPERS` с порядком `brave → yep`).
- `specs/014-lyrics-search-replacement/research.md` — Production finding
  (2026-07-27): таблица рабочих scrapers, диагностическая процедура
  curl-перебора.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` —
  текущий `SearchTool` с `LYRICS_SEARCH_SCRAPERS = listOf("brave", "yep")`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:195` —
  существующие `lyricsSearchEngine` (паттерн для новых свойств).
- `archive/docs/features/llm-lyrics-search.md` — общая документация по
  LLM-поиску текстов (если `docs/features/llm-lyrics-search.md` не существует —
  создать там же).
- `docs/ops/log-correlation.md` — карта логов прода, фильтры для замера
  SC-001/SC-002/SC-005.
- Constitution §III — инвариант «recordhash» и sync не затрагивается.
- Constitution §VI FR-007 — обновить per-feature документ
  `docs/features/llm-lyrics-search.md` (раздел scrapers + новый раздел
  post-filter) в том же PR.

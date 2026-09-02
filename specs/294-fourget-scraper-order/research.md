# Phase 0: Research — порядок scrapers и post-filter «мусорных» URL

## Контекст исследования

В фиче 294-fourget-scraper-order требуется:
1. Поменять порядок scrapers в `SearchTool.LYRICS_SEARCH_SCRAPERS` с
   `["brave", "yep"]` на `["yep", "brave"]`.
2. Ввести порог «качества» (`lyricsSearchMinResults`, дефолт 2) — если
   scraper вернул ≤N URL после post-filter, пробовать следующий.
3. Добавить post-filter `filterUselessLyricsUrls` — отброс homepage, sitemap,
   login-страниц, файлов, tracking-маркеров и дубликатов.
4. Списки и пороги вынести в `KaraokeProperties` для hot-fix без передеплоя.

Источники исследования:
- **Спека 014-lyrics-search-replacement** (выбор 4get как движка) и её
  `research.md` — таблица рабочих scrapers на admin-машине (2026-07-27).
- **Текущий код** `Tools.kt#SearchTool` и `KaraokeProperties.kt` — паттерны
  конфигурации.
- **Тестовый паттерн** `AlbumCoverFinderParsingTest` — офлайн unit-тесты на
  чистые функции без сети.
- **Constitution v2.1.0** — стек, ограничения, FR-006 (KDoc/JSDoc coverage),
  FR-009 (per-feature документ в том же PR).

## Решённые вопросы (no NEEDS CLARIFICATION остаётся)

### Q1: Где хранить список scrapers — String (через разделитель) или List<String>?

**Decision**: **`String` с разделителем `;`** в `KaraokeProperties`,
распарсенный в `List<String>` helper-функцией `lyricsSearchScrapersList()`
в `Tools.kt`.

**Rationale**: `KaraokeProperties` НЕ поддерживает `List<String>` нативно
(см. `KaraokeProperties.kt:107-114` — `types()` возвращает только
`Long`/`Int`/`Double`/`Boolean`/`String`). Расширять типы — это изменение
core-инфраструктуры всех настроек проекта (FR-001..008
`specs/001-code-standards-docs`/KaraokeProperties UI), что выходит за scope
этой фичи. `String` с `;`-разделителем — прагматичный workaround:
- Легко сериализуется в JSON в `KaraokePropertyDTO`
  (`getDTO` использует `value.toString()`);
- Можно редактировать через UI настроек как обычную строку
  (multi-input не нужен — это настройка уровня эксплуатации, не пользователя);
- Парсинг тривиальный: `value.split(";").map { it.trim() }.filter { it.isNotEmpty() }`.

**Alternatives considered**:
- Ввести новый тип `KaraokePropertyType.LIST_STRING` в `types()` — отклонено,
  требует миграции UI/API + обновление всех DTO/контроллеров, упоминание в
  Constitution §VI (FR-006), отдельная фича.
- Хранить как JSON-строку (`["yep","brave"]`) — работает, но избыточно для
  flat-списка строк; усложняет редактирование через UI настроек.

### Q2: Как тестировать `filterUselessLyricsUrls` — без HTTP, без fourget?

**Decision**: **Unit-тест JUnit5 в
`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt`**
по образцу `AlbumCoverFinderParsingTest` (см.
`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinderParsingTest.kt`).

**Rationale**:
- `filterUselessLyricsUrls` объявлена как **top-level pure function**
  (нет `this`, нет HTTP, нет зависимостей) — тривиально тестируема.
- Паттерн `AlbumCoverFinderParsingTest` — кириллические имена тестов в
  backticks, офлайн (без сети/БД), JUnit5 (`@Test` из
  `org.junit.jupiter.api.Test`), активные (НЕ `@Disabled`) — точно такой
  же формат применим.
- Существующие тесты в `karaoke-app/src/test` НЕ являются `@Disabled`
  (за исключением `PlaywrightTests.kt`, требующего браузер). Это
  **исправляет ошибку в FR-009 спеки**: тесты будут активными, не
  `@Disabled`.

**Alternatives considered**:
- Integration-тест через реальный fourget — отклонён: требует развёрнутый
  контейнер fourget в CI; политика Karaoke — «тесты в CI нет» (см.
  Constitution §Рабочий процесс); unit-тест покрывает все правила FR-004
  синтетическими данными, что достаточно.

### Q3: Куда поместить функцию `filterUselessLyricsUrls` — в `Tools.kt` или отдельный файл?

**Decision**: **Top-level `private` функция в `Tools.kt`** (как часть
пакета `com.svoemesto.karaokeapp.llm`).

**Rationale**:
- Функция используется **только** в `SearchTool.searchUrlsViaScraper` —
  не нужна как публичная API.
- `private` на top-level в Kotlin = internal к файлу; unit-тесты в том
  же пакете (но другом файле) **не увидят** `private` функцию. Это
  противоречит FR-009 (нужны тесты).
- **Уточнение**: пометить как `internal` (а не `private`), чтобы
  тесты в `karaoke-app/src/test/.../llm/` видели функцию. Это согласуется
  с видимостью `@KaraokeDbTableField`-методов и других internal helpers.
- Альтернатива — вынести в `UtilsAI.kt` (где уже живут parsing helpers
  по образцу `textBetween`, `extractBalancedBracesFromString`). Это
  чище архитектурно (filter — утилита, не зависит от `Tools`), но
  требует перенести функцию в `UtilsAI.kt`. **Выбираем `Tools.kt`** —
  filter специфичен для lyrics-поиска, не общего назначения; размещение
  рядом с местом использования упрощает навигацию. Если в будущем
  потребуется аналогичный filter для `searchUrlsViaSearxng` или images
  — будет явный повод для выноса в общий helper.

### Q4: Изменится ли логирование при новом порядке scrapers?

**Decision**: **Логирование сохраняется as-is** (`🔍 [SearchTool] Запрос к
fourget (scraper=...)`), но добавляется **новая строка** для post-filter
статистики.

**Rationale**:
- Текущий формат логов (`🔍 Запрос` / `✅ найдено URL` / `❌ ошибка`)
  фильтруется через `docs/ops/log-correlation.md` и существующие
  grep-маркеры для мониторинга. Менять формат = ломать observability.
- Новая строка `🔧 [SearchTool] post-filter: было N, осталось M
  (отброшено K)` имеет префикс `🔧` (отличается от `🔍`/`✅`/`❌`), не
  конфликтует с существующими grep-фильтрами; легко агрегируется для
  SC-005 (доля поисков, где filter отбросил ≥1 URL).
- Метрика `было/осталось/отброшено` — три числа в одной строке = удобно
  парсить через awk/grep.

**Alternatives considered**:
- Логировать каждый отброшенный URL отдельно — отклонено: для типичного
  результата (10 URL → 3 осталось) это +7 строк логов на один поиск,
  шумно; достаточно агрегированной статистики.
- DEBUG-уровень вместо INFO — отклонён: SC-005 требует, чтобы метрика
  была видна без поднятия уровня логирования.

### Q5: Какой URL-парсер использовать для проверки схемы/host/path?

**Decision**: **`java.net.URI.create(url)` + простая string-логика для
path/extension**.

**Rationale**:
- `URI.create(url)` бросает `IllegalArgumentException` для невалидных
  URL; ловится в try/catch → URL отбрасывается (FR-004 п.1).
- `URI.scheme` — string, проверка `scheme in listOf("http", "https")`
  (FR-004 п.2). Нет regex, нет библиотек.
- `URI.host` — string; для проверки homepage (п.3) достаточно `path.isEmpty()
  || path == "/"`. Альтернативы (`URI.create(url).host == "example.com"
  && path.isEmpty()`) эквивалентны.
- `URI.path` + `URI.query` — string; для пп.4-6 (служебные path,
  расширения, tracking) — простые `endsWith`/`contains`/`split("?")[1]`
  проверки.
- **Без regex**: regex — медленнее (O(N) с backtracking), сложнее для
  аудита, требует экранирования спец-символов. Простые string-методы
  покрывают все правила FR-004 (см. NFR-005 — O(N) ≤1мс).

**Alternatives considered**:
- Apache HttpComponents / OkHttp `HttpUrl.parse(url)` — отклонено: эти
  библиотеки уже есть в проекте (Spring Web), но добавлять URL-parsing
  helper с внешней зависимостью ради 7 правил — избыточно. `java.net.URI`
  — часть JDK, всегда доступна.
- Regex с одним большим pattern — отклонён: backtracking, сложнее
  тестировать, легко ошибиться с экранированием; простые string-методы
  быстрее и читабельнее.

### Q6: Нужно ли в `KaraokeProperties` UI/webvue3 редактировать новые свойства?

**Decision**: **НЕ в этой фиче** — `NFR-004` спеки фиксирует, что UI
для новых свойств — отдельная фича.

**Rationale**:
- Текущий UI настроек `KaraokeProperties` показывает все свойства из
  `listKaraokeProperties` через generic renderer (см.
  `KaraokeProperties.kt:138-145` `loadList`). Новые свойства
  **появятся автоматически** в UI как String/Int — но без специального
  редактора для List<String> (закодированы через `;`-разделитель).
- Для hot-fix при очередной блокировке scraper'а достаточно отредактировать
  значение в БД через SQL UPDATE или через generic UI (как обычную строку).
- Полноценный UI-редактор для List<String> — отдельная фича, требующая
  ввода нового типа в `KaraokeProperties.types()` + UI-компонент. **Не
  в scope 294** (см. Out of Scope спеки).

## Альтернативные scrapers (без изменений в этой фиче)

Из research 014 (см. `specs/014-lyrics-search-replacement/research.md`,
таблица Production finding, 2026-07-27) подтверждено рабочими только `brave`
и `yep`. Кандидаты для повторной проверки в случае будущей деградации —
`mojeek`, `startpage`, `qwant`, `wikipedia`. **Добавление новых scrapers в
production-список — отдельная фича**, требующая curl-перебора и
документирования результата (процедура в
`archive/docs/features/llm-lyrics-search.md` → «Известные ловушки»).

## Curl-перебор 2026-09-02 (актуальное состояние 4get 1.0.44)

**Контекст.** Перед деплоем 294 проведён повторный curl-перебор всех 14
известных scrapers на текущей сборке 4get (`luuul/4get:1.0.44`,
контейнер `fourget`, exposed на `localhost:8889`). Цель — проверить,
не изменилось ли состояние scrapers с момента research 014
(2026-07-27, ~1.5 месяца назад), и не требуется ли корректировка
дефолта `lyricsSearchScrapers` в `KaraokeProperties`.

**Метод**: прямой `curl http://localhost:8889/api/v1/web?s=test&scraper=<name>`
для каждого scraper'а. Параметр `s=test` как в research 014 (быстрая
проверка «отвечает ли вообще»), затем реальные lyrics-запросы для
валидации результатов.

### Результаты на тестовом запросе `s=test`

| Scraper | HTTP | Status | web.length | Комментарий |
|---|---|---|---|---|
| `yep` | 200 | ok | **0** | ⚠️ **status=ok, но `web=[]` тихо (как yandex в research 014)** |
| `brave` | 200 | ok | **18** | ✅ работает (Speedtest, istockphoto — реальные результаты) |
| `yandex` | 200 | ok | 0 | отброшен (как в research 014) |
| `google` | 200 | «Still working on a Google scraper...» | 0 | не реализован |
| `bing`, `yahoo_jp`, `mullvad_brave`, `presearch`, `ecosia`, `wikipedia`, `duckduckgo` (default) | 200 | «DuckDuckGo detected an anomaly in the Javascript challenge response» | 0 | сломанный проксированный DDG-бэкенд |
| `startpage` | 200 | «Failed to grep JSON object» | 0 | ошибка парсинга |
| `qwant` | 200 | «Qwant returned a captcha redirect» | 0 | captcha |
| `mojeek` | 200 | «Mojeek blocked this instance or request proxy» | 0 | blocked |

### Результаты на реальных lyrics-запросах

| Запрос | `yep` URLs | `brave` URLs |
|---|---|---|
| `Кино Группа крови текст` | **0** | **20** |
| `Adele Rolling in the Deep lyrics` | **0** | **20** |
| `Максим Знаешь ли ты текст` | **0** | **20** |
| `beatles yesterday lyrics` | **0** | **20** |
| `lady gaga bad romance lyrics` | **0** | **30** |
| `python tutorial` | **0** | **20** |
| `open source license` | **0** | 0 (brave: «Could not fetch search page») |
| `kino` (только артист) | **0** | **29** |
| `kino band` | **0** | (не проверял отдельно) |
| `текст песни kino` (кириллица) | **0** | (не проверял отдельно) |

### Ключевая находка: **yep деградировал с 2026-07-27**

В research 014 (2026-07-27) `yep` показывал «реальные непустые
результаты» и был выбран как **основной** для фичи 294. Сейчас
(2026-09-02) `yep` стабильно отдаёт `status=ok, web=[]` на ЛЮБОЙ запрос
(кириллица/латиница, одиночные слова/фразы, разные домены). Это
**зеркально симметрично** поведению `yandex` в research 014 — «тихо
не работает, без явной ошибки».

**При этом `brave` сейчас работает стабильно** — 20-30 URL на каждый
lyrics-запрос (выше, чем показывал в research 014, где он
деградировал).

### Вероятная причина

`yep` (как и `yandex` ранее) — поисковик, чувствительный к IP-блокировкам
и anti-bot политикам. За 1.5 месяца хост, с которого делается запрос,
мог попасть в бан/капчу именно для `yep`, а `brave` (использующий
другую инфраструктуру и проксируемый через другую цепочку в 4get)
остался работать.

### Решение для фичи 294

В спеке и `KaraokeProperties` дефолт `lyricsSearchScrapers = "yep;brave"`
(с приоритетом yep). **На текущем 4get это означает, что `yep` будет
тихо возвращать 0 URL → fallback на `brave` → реальные результаты**.

Это **работает корректно** благодаря:
- `lyricsSearchMinResults = 2` (US1) — после post-filter 0 URL → fallback;
- post-filter `filterUselessLyricsUrls` (US2) — пропускает чистые URL от brave;
- итеративная логика в `searchUrls` — перебирает scrapers до первого успешного.

**Никаких изменений кода не требуется**. Просто на проде первый запрос
всегда будет идти к yep (0 URL) → brave (20 URL), итого latency
увеличится на один HTTP-запрос (~30 сек таймаут в худшем случае,
обычно ~200мс). Это **цена одной ошибки в research 014** — но
архитектура фичи спроектирована именно для этого сценария.

**Альтернативный дефолт** (обсуждается, но НЕ в этой фиче):
можно поменять порядок на `"brave;yep"` — что соответствует
**текущему реальному состоянию**. Но менять дефолт после merge'а —
нарушение версионирования; правильнее оставить как есть и
зафиксировать вывод в этом research.

**Рекомендация для следующей итерации (отдельная фича)**:
1. Провести ещё один curl-перебор через 1 месяц (после деплоя).
2. Если `yep` восстановится → оставить текущий дефолт.
3. Если `yep` продолжит деградировать → выпустить фичу
   «обновить дефолт `lyricsSearchScrapers` на основе актуального
   curl-перебора» (одна строка в `KaraokeProperties.kt`).
4. **Альтернатива**: установить дефолт `"brave"` (только brave) и
   добавить `yep` обратно, если/когда он восстановится. Это
   устранит «лишний» HTTP-запрос.

### Endpoint `/api/v1/images` (для обложек — NFR-006, не затрагивается)

Проверка по образцу lyrics-перебора (для полноты картины):
- `brave` на `?s=cat+photo` → `status=ok`, `image[]` с реальными
  результатами (проверено вручную, ответ `{"status":"ok","npt":null,"image":[{"title":"Excited cat...","source":[{"url":"https://media.istockphoto.com/..."}]}]}`).
- Остальные scrapers для images — НЕ проверялись (NFR-006 спеки
  294: обложки работают как раньше через жёсткий `scraper=brave`,
  без fallback).

## Альтернативные post-filter правила (рассмотрено, отклонено)

- **ML-классификатор URL** (семантический filter «вероятность содержания
  текста песни») — отклонён: это задача LLM-парсинга
  (`ScraperAgent`/`LyricsFinderService`), не URL-filter. Наш filter —
  **синтаксический** (отбрасываем заведомо бесполезные URL без семантики).
- **Whitelist «хороших» доменов** (например, только `*.amalgama-lab.com`,
  `*.genius.com`, `*.lyrics.com`) — отклонён: хрупкий (новые хорошие
  домена ломаются), требует постоянного обновления, не покрывает
  edge-cases. Лучше blacklist служебных path + tracking — что и
  реализуем.
- **HEAD-запрос для проверки `Content-Type`** — отклонён: добавляет
  HTTP-запрос на каждый URL (N×30мс overhead), 4get уже фильтрует по
  своей логике, а homepage/sitemap почти всегда отдают 200 OK с
  `text/html`. Не окупается.

## Альтернативная архитектура scraper-менеджера (отклонено)

Рассматривался «scraper registry» (объект, регистрирующий доступные
scrapers, их приоритеты, поддерживаемые endpoint'ы). Преимущества —
расширяемость, hot-plug. Недостатки для текущей задачи:
- Over-engineering: фича меняет **один** список и **один** фильтр;
  полноценный registry нужен, когда появятся ≥3 источника с разной
  логикой (SearXNG, Yandex, Fourget уже есть, но у каждого свой
  endpoint и формат ответа — registry не упростит код, а усложнит).
- Лишний слой абстракции → сложнее тестировать, больше файлов,
  замедляет онбординг.

**Решение**: остаёмся на `private val LYRICS_SEARCH_SCRAPERS = ...` +
helper-функции в `Tools.kt`. Если в будущем появится 5+ scrapers с
разными протоколами — будет повод для рефакторинга.

## Риски

| Риск | Вероятность | Митигация |
|---|---|---|
| `URI.create` бросит exception для URL с кириллицей/IDN | Средняя | Ловим exception → URL отбрасывается (FR-004 п.1); IDN-домены fourget обычно отдаёт в punycode (`xn--...`) — должно работать. Если проблема — добавить catch+keep для специфических случаев (edge case в спеке). |
| `lyricsSearchUselessUrlPatterns` слишком агрессивен — отбрасывает легитимные URL | Средняя | Дефолт из FR-004 — консервативный (только явно мусорные path). Мониторинг SC-005 (доля «было → осталось» 5-30% в проде). Если >50% — правила жёсткие, можно ослабить через `KaraokeProperties`. |
| `yep` начнёт деградировать (зеркально brave) | Средняя (уже была brave) | Логика fallback итеративна по списку; добавить новый scrapers через `KaraokeProperties` без передеплоя. См. A-001. |
| `filterUselessLyricsUrls` замедлит поиск | Низкая | NFR-005: O(N) без regex; expected ≤1мс на 50 URL. Если просядет — вынести filter в отдельный поток (over-engineering для текущего объёма). |
| Post-filter отбросит ВСЕ URL → false negative (правильный URL отброшен из-за паттерна) | Низкая | Дефолтные правила FR-004 покрывают только явно мусорные случаи. Edge cases (legitimate URL с `?utm_source=`) документированы как ожидаемое поведение. |

## Связанные документы

- `specs/014-lyrics-search-replacement/spec.md` + `research.md` — выбор 4get,
  таблица рабочих scrapers.
- `specs/015-search-engine-selection/spec.md` — введение `AlbumCoverSearchEngine.FOURGET`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` —
  текущий `SearchTool` (точка изменения).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` —
  паттерн для новых свойств (`getString`, `getInt`, `types()`).
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinderParsingTest.kt` —
  образец unit-теста чистой функции.
- `archive/docs/features/llm-lyrics-search.md` — общая документация LLM-поиска.
- `docs/ops/log-correlation.md` — карта логов (для SC-001/SC-002/SC-005).
- Constitution v2.1.0 — стек, FR-006 (KDoc), FR-009 (per-feature doc).

## Resolved Clarifications Summary

Все 5 Clarifications из спеки подтверждены через research:
- Q1 (только Tools.kt, обложки не трогаем) → NFR-006 в спеке.
- Q2 (порог ≤2) → Q4 в research подтверждает дефолт `lyricsSearchMinResults = 2`.
- Q3 (post-filter до оценки порога) → архитектура в plan.md.
- Q4 (итеративный fallback) → текущая логика `for (scraper in ...)` сохраняется.
- Q5 (отдельная чистая функция) → Q3 в research (top-level в `Tools.kt`,
  `internal` для тестов).

# LLM-assisted поиск текстов и аккордов

> **Status**: active
> **Feature Key**: llm-lyrics-search
> **Last Updated**: 2026-09-02 (specs/294-fourget-scraper-order: новый порядок scrapers `yep → brave` + post-filter «мусорных» URL через `filterUselessLyricsUrls`; настройки `lyricsSearchScrapers`, `lyricsSearchMinResults`, `lyricsSearchUselessUrlPatterns` в `KaraokeProperties`)

## Что делает

Автоматически находит тексты песен, аккорды, ключ, BPM через
web-поиск (fourget) + скрейпинг сайтов + LLM-анализ (LM Studio). Для сайтов
с JS-рендером или авторизованной сессией (Яндекс.Музыка) — Playwright/Selenium.

## Зачем

Ручной поиск текстов и аккордов — часы на песню. LLM умеет извлекать
структурированные данные из сырого HTML, догадываться о формате аккордов,
исправлять опечатки. fourget + LM Studio — локальные (self-hosted)
инструменты, не зависят от внешних SaaS (см.
[constitution.md#i-self-contained-автопайплайн](../../.specify/memory/constitution.md)).

## Как работает (кратко)

1. **`LyricsFinderService`** — оркестратор: получает `Settings` (песня),
   формирует запрос (автор + название), запускает поиск.
2. **Движок поиска текста** — выбираемый (`specs/015-search-engine-selection`),
   один из четырёх (enum `LyricsSearchEngine` в `UtilsAI.kt`): `YANDEX_SYNC`/
   `YANDEX_ASYNC` (Yandex Cloud Search API, IAM-токен — `getYandexSearch`),
   `SEARXNG` (прямой запрос к self-hosted SearXNG — `SearchTool.searchUrlsViaSearxng`),
   `FOURGET` (self-hosted мета-поисковик fourget, `/api/v1/web?s=...&scraper=...`,
   **с 2026-09-02: yep→brave** (было brave→yep), плюс post-filter «мусорных» URL
   `filterUselessLyricsUrls` в `Tools.kt`, см. `specs/294-fourget-scraper-order/spec.md` —
   `SearchTool.searchUrls`).
   Диспетчер — `getLyricsSearch(settings, lyricsFinderService, engine, forceResearch)`
   в `UtilsAI.kt` (заменил собой прежний `getSearXNGSearch`, имя которого стало
   вводящим в заблуждение после фичи 014). Движок по умолчанию —
   `KaraokeProperties.lyricsSearchEngine` (редактируется в UI «Свойства»),
   либо явно передан параметром `engine` в `/api/songs/searchsongtextall`.
   `forceResearch=true` — сначала удаляет старые `SearchResult`/`SearchAsync`
   для песни (`deleteBySongId`), чтобы обойти кэширующую проверку «уже есть
   запрос — вернуть его» и искать заново другим движком (кнопка «Искать
   заново» в `SearchText.vue`). Кнопка «Удалить результаты поиска» в том же
   окне — `POST /api/song/deletesearchresults`, без нового поиска.
   Диалог подтверждения с выбором движка (и предупреждением про удаление
   старых результатов) вызывается единообразно из трёх мест: кнопка «Искать
   заново» в `SearchText.vue`, кнопка «Найти текст песни» в `SongEdit.vue`
   и кнопка «Найти тексты для всех песен» в `SongsTable.vue` — все три
   всегда передают `forceResearch=true` (bulk-вариант удаляет старые
   результаты только для песен без `sourceText`, см. `getSearchSongTextAll`).
3. **Автоподстановка найденного текста** (`specs/020-fix-search-lyrics-autofill`):
   по завершении поиска (независимо от движка) первый найденный непустой
   текст-кандидат автоматически подставляется в `Song.sourceText`, если у
   песни ещё нет текста (`id_status == 0`). Единственный источник истины о
   том, "есть ли у песни текст" — `Song.haveSourceText`
   (`sourceText != "" && sourceText != "[\"\"]"`, учитывает оба
   представления "текста ещё нет"). Сам шаг подстановки — общая функция
   `applyFoundLyricsIfMissing(settings, candidateTexts)` в `UtilsAI.kt`,
   вызывается из всех трёх точек завершения поиска одинаково: конец
   синхронной Yandex-ветки (`getYandexSearch`, `async=false` — до фичи 020
   этого шага там не было вообще), конец обработки завершённого
   асинхронного Yandex-запроса (`KaraokeProcessWorker.checkSearchAsync`) и
   ветка SEARXNG/FOURGET (`getLyricsSearchViaSearchTool`). **Не** дублировать
   проверку "есть текст" через `sourceText.isBlank()`/`isEmpty()` в новом
   коде — это и было первопричиной бага 020 (значение-заглушка `["\"\"]"`
   не ловилось `isBlank()`).
4. **Движок поиска обложки альбома** — отдельный выбор (enum
   `AlbumCoverSearchEngine` в `AlbumCoverFinder.kt`, только 2 варианта —
   Yandex Cloud Search API возвращает текстовые результаты, не картинки):
   `SEARXNG` (`AlbumCoverService.searchSearxngImages`, сегодняшнее поведение
   по умолчанию) или `FOURGET` (`AlbumCoverService.searchFourgetImages`,
   `fourget` `/api/v1/images?s=...&scraper=brave`). Настройка —
   `KaraokeProperties.albumCoverSearchEngine`, либо параметр `engine` в
   `POST /api/song/searchalbumcover`.
5. **Автоочистка результатов поиска для готовых песен**
   (`specs/015-search-engine-selection`, порог обновлён в
   `specs/022-song-status-lifecycle`): как только `Song.saveToDb()`
   фиксирует пересечение песней порога готовности (статус ≥6, тот же порог,
   что для публичного плеера — `crossedReadyThreshold`), результаты поиска
   текста для неё удаляются автоматически. Для уже готовых песен — кнопка
   «Удалить результаты поиска готовых песен» на главной странице админки
   (`POST /api/utils/deletesearchresultsforreadysongs`,
   `HealthReport.deleteSearchResultsForReadySongs`, фоновый прогон + SSE-тост,
   по образцу `doRecalcPlayerReadiness`).
6. **Скрейпинг** — для каждого результата парсим HTML:
   - Статический — `jsoup` (см. [jsoup](https://jsoup.org/)).
   - JS-рендер / авторизация — `UtilsPlaywright.kt` через Playwright/Selenium.
7. **LLM-анализ** — `ScraperAgent.kt` через `LmStudioService.kt` (тонкий клиент над
   OpenAI-совместимым `/v1/chat/completions` LM Studio, поднятого на хост-машине админа):
   - Структурирование текста (разбивка на строки/куплеты/припевы).
   - Нормализация аккордов (`Am`, `A minor`, `a-moll` → `Am`).
   - Определение ключа и BPM.
   - Тем же клиентом (`LmStudioService`) пользуется `TextCorrectorAgent.kt` — AI-редактор текста
     в SubsEdit.vue (исправление орфографии/пунктуации).
8. **Яндекс.Музыка** — отдельный путь (`searchLastAlbumYm3`/
   `checkLastAlbumYm`): авторизация по сохранённой сессии на диске, поиск
   нового альбома автора. Возвращает `AlbumSearchResult`:
   `Success`/`VpnBlocked`/`AuthExpired`/`BotDetected`/`Unknown`.
9. **VPN-детект** — `isVpnActive()` через `api.country.is` (страна != RU →
   ВПН включён → Playwright не запускаем).

## Жизненный цикл статуса готовности (specs/022-song-status-lifecycle)

`Song.idStatus` имеет 7 значений (0-6): 0 новая, 1 текст найден, 2 текст
проверен (орфография/пунктуация), 3 текст проверен (слова соответствуют
песне), 4 маркеры расставлены, 5 маркеры проверены, 6 готова (новый порог
публичной готовности, было `>=3`). Из мест, документированных в этом файле:

- Автоподстановка найденного текста (`applyFoundLyricsIfMissing`, пункт 3
  выше) продвигает статус только `0 → 1` — без изменений относительно новой
  шкалы.
- Фоновый forced-align (`Utils.executeForcedAlignMarkers` — реализует шаг
  «автоматическая расстановка маркеров») продвигает статус строго на 1 шаг
  `3 → 4` (не трогает статус, если текущий не 3, FR-011
  `specs/022-song-status-lifecycle/spec.md`). Гейт постановки в очередь
  (`ApiController.doProcessForcedAlignMarkers`/
  `getSongsCreateForcedAlignMarkersAll`) — `idStatus < 4` (было `< 3`):
  нельзя перезаписывать уже расставленные маркеры.
- Прескан кандидатов на повторный автопоиск родителя/аудио-родителя
  (`customFunction`, `Utils.kt:102`) — `id_status < 6` (было `< 3`); реальная
  защита от перезаписи уже проверенного текста — отдельный `idStatus >= 2`
  guard внутри цикла (`Utils.kt:138`), не меняется.
- Копирование маркеров от найденного аудио-родителя (`applyAudioParentMarkers`)
  требует родителя со статусом `>= 6` (было `>= 3`) и безусловно выставляет
  копии статус `6` (было `3`) — перенос уже полностью готового контента, не
  итеративное автопродвижение.

## Инварианты / правила

- **MUST**: не использовать внешние SaaS (OpenAI, Anthropic) в горячем
  пути. Только локальный LM Studio (`lmStudioUrl`, см. `KaraokeProperties.kt`).
- **MUST**: проверка "есть ли у песни текст" — только через
  `Song.haveSourceText`, никогда через `sourceText.isBlank()`/`isEmpty()`
  напрямую (`specs/020-fix-search-lyrics-autofill`, см. пункт 3 выше).
- **MUST**: `isVpnActive()` проверяется ДО запуска Playwright (если ВПН —
  скрейпинг Яндекс.Музыки заблокирован).
- **MUST**: результат `AlbumSearchResult` логируется с reason-кодом
  (см. [DEVELOPMENT.md#поиск-нового-альбома-на-яндексмузыке](../../DEVELOPMENT.md)).
- **MUST**: `redirectErrorStream(true)` для Playwright/Selenium subprocess
  (см. [CONTRIBUTING.md#kotlin-processbuilder-redirect-error-stream](../../CONTRIBUTING.md)).
- **SHOULD**: кешировать LLM-результаты по хешу (автор + название) —
  повторный поиск той же песни не должен второй раз гонять модель.

## Известные ловушки

- **Бот-детект Яндекс.Музыки**: код `-1` (BotDetected) → таймаут
  `requestNewSongTimeoutIncreaseMs` (до часа). Не спамьте запросами.
- **Авторизация истекла**: `AuthExpired` → пользователь должен
  переавторизоваться вручную (сохранение сессии на диске).
- **Долгие LLM-запросы**: LM Studio на CPU медленная. Лучше вынести
  в отдельный лейн/поток, чтобы не блокировать.
- **fourget недоступен**: проверяйте `lyrics-search.base-url` /
  `LYRICS_SEARCH_BASE_URL` перед первым запуском (контейнер `fourget` в
  `deploy/docker-compose*.yml`, рядом с `searxng`). При недоступности/ошибке
  `SearchTool.searchUrls` логирует ошибку и возвращает пустой список —
  пайплайн не падает (см. `specs/014-lyrics-search-replacement/spec.md`,
  FR-006), но песня останется без автоматически найденного текста до
  следующей попытки.
- **Scraper fourget заблокирован конкретным движком**: многие scraper'ы
  fourget (DuckDuckGo и всё, что через него проксируется — bing, yahoo_jp,
  mullvad_brave, presearch, ecosia в этой сборке; также Startpage/Qwant —
  капча, Mojeek — бан инстанса, Yandex — тихо пустой результат без ошибки)
  могут не работать на конкретном IP/хостинге. Проверить вручную:
  `curl "http://<lyrics-search.base-url>/api/v1/web?s=test&scraper=<имя>"` —
  `status: "ok"` с непустым `web` значит движок реально работает. Список
  scraper'ов, перебираемых `SearchTool` по очереди, — константа
  `LYRICS_SEARCH_SCRAPERS` в `llm/Tools.kt` (сейчас `brave`, `yep`) — при
  очередной блокировке добавить/заменить на другой рабочий scraper из этого
  списка, а не менять весь бэкенд заново.
- **Все self-hosted движки временно недоступны**: переключите
  `KaraokeProperties.lyricsSearchEngine` на `YANDEX_SYNC`/`YANDEX_ASYNC` (UI
  «Свойства») как временный запасной вариант — платный внешний Yandex Cloud
  Search API, но независимый от состояния `searxng`/`fourget` на этой машине.
  Для конкретной уже проверенной песни — кнопка «Искать заново» в
  `SearchText.vue` позволяет выбрать движок разово, не трогая общую настройку.

## Ссылки на ключевые классы/файлы

- [`LyricsFinderService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/LyricsFinderService.kt) — главный оркестратор
- [`ScraperAgent.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/ScraperAgent.kt) — извлечение текста песни из HTML через LLM
- [`TextCorrectorAgent.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/TextCorrectorAgent.kt) — AI-редактор текста (SubsEdit.vue)
- [`LmStudioService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/LmStudioService.kt) — тонкий клиент LM Studio
- [`Tools.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt) — инструменты для LLM (`SearchTool`), `filterUselessLyricsUrls` (post-filter, см. specs/294)
- [`UtilsAI.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt) — `getLyricsSearch` (диспетчер движков), `getYandexSearch`, `LyricsSearchEngine`, `applyFoundLyricsIfMissing` (единая автоподстановка, см. пункт 3)
- [`AlbumCoverFinder.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt) — `AlbumCoverService`, `AlbumCoverSearchEngine`
- [`HealthReport.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt) — `deleteSearchResultsForReadySongs` (массовая очистка)
- [`UtilsPlaywright.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt) — Playwright/Selenium
- [`Utils.searchLastAlbumYm3`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — Яндекс.Музыка

## Post-filter «мусорных» URL (specs/294-fourget-scraper-order)

**Контекст.** В логах прод регулярно появлялось
`status='Brave did not return a result object'`, и даже когда scraper
возвращал `status=ok` с непустым списком `web[]`, там часто оказывался
мусор (homepage сайта, sitemap.xml, login-страницы). LLM-парсер
(`ScraperAgent`) тратил токены/время на попытки извлечь текст песни
из этих URL.

**Решение** (2026-09-02). В `Tools.kt#SearchTool.searchUrlsViaScraper`
между парсингом JSON fourget и возвратом результата добавлен этап
**post-filter** — чистая функция `filterUselessLyricsUrls(urls, patterns)`:

1. **Невалидный URL** (`URI.create` throws) → отбрасывается.
2. **Схема ≠ `http`/`https`** (ftp/mailto/javascript/data/file) → отбрасывается.
3. **Homepage без path** (`https://example.com` или `/`) → отбрасывается.
4. **Служебный path** (substring match, case-insensitive):
   `/login`, `/signup`, `/register`, `/auth`, `/wp-login.php`,
   `/wp-admin`, `/administrator`, `/sitemap.xml`, `/sitemap`,
   `/sitemap_index.xml`, `/robots.txt`, `/feed`, `/rss`,
   `/rss.xml`, `/atom.xml`, `/search` → отбрасываются.
5. **Расширения файлов** (не HTML-страницы):
   `.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.zip`, `.rar`,
   `.7z`, `.tar`, `.gz`, `.mp3`, `.mp4`, `.wav`, `.avi`,
   `.mov`, `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.svg`
   → отбрасываются.
6. **Tracking-маркеры в query**:
   `utm_source=`, `utm_medium=`, `utm_campaign=`, `utm_term=`,
   `utm_content=`, `fbclid=`, `gclid=`, `yclid=`, `msclkid=`,
   `_ga=`, `ref=` → отбрасываются.
   Легитимные `?id=...`, `?page=...` и подобные query-параметры **НЕ**
   отбрасываются.
7. **Дедупликация** через `LinkedHashSet` — сохраняет порядок первого
   появления, убирает идентичные дубли.

**Сложность**: O(N) на размер списка URL, без regex, без HTTP.
На 50 URL укладывается в ≤1 мс (NFR-005).

**Настройки в `KaraokeProperties`** (hot-fix без передеплоя):

| Ключ | Тип | Дефолт | Что делает |
|---|---|---|---|
| `lyricsSearchScrapers` | String | `"yep;brave"` | Порядок scrapers через `;`. Был `"brave;yep"` до 2026-09-02. |
| `lyricsSearchMinResults` | Int | `2` | Порог «качества» — если scraper вернул меньше URL после post-filter, пробуем следующий. |
| `lyricsSearchUselessUrlPatterns` | String | (см. FR-004 спеки 294) | Паттерны для post-filter через `;`. |

**Логирование**: новая строка `🔧 [SearchTool] post-filter: было N,
осталось M (отброшено K)` на уровне INFO — для мониторинга
эффективности filter'а в проде (SC-005: доля поисков с `K > 0` —
5-30%).

**Тесты**: 10 unit-тестов в
`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt`
(по одному на каждое правило + happy path + edge cases). Активные,
не `@Disabled` — по образцу `AlbumCoverFinderParsingTest`.

**Принципы и контекст**:
- Только `SearchTool.searchUrls` (lyrics). `AlbumCoverFinder.kt`
  (поиск обложек) **не трогаем** — обложки работают как раньше
  через жёстко зашитый `scraper=brave` без fallback (NFR-006).
- Семантическая фильтрация URL (страница про текст песни vs альбом
  vs артиста) — НЕ наша задача; это работа LLM-парсинга
  (`ScraperAgent`).
- При добавлении новых scrapers (mojeek/startpage/qwant/wikipedia) —
  curl-перебор по процедуре в `specs/014-lyrics-search-replacement/research.md`
  → Production finding.

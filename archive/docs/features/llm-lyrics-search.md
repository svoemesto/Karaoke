# LLM-assisted поиск текстов и аккордов

> **Status**: active
> **Feature Key**: llm-lyrics-search
> **Last Updated**: 2026-09-26 (ветка `454-vpn-check-cache`: `isVpnActive()` кэширует определённую страну внешнего IP на `vpnCheckCacheTtlSeconds` (default 300; 0 — выключено) — кэшируется только факт страны, решение о ВПН считается заново из `vpnHomeCountry`, а неудача не кэшируется, чтобы fail-open не залипал. Попутно вскрыт дефект: HTTP-статус ответа не проверялся вовсе, поэтому резервный `ipapi.co` молча отвечал HTTP 429 (rate limit), а его тело ошибки выглядело как «страну определить не удалось». Ранее в тот же день, ветка `453-album-cover-engine-selector`: в модалке обложек появился **селектор движка-фолбэка** — форма A, только `SEARXNG`/`FOURGET`, дефолт из настройки `albumCoverSearchEngine`. Параметр `engine` наконец прокинут из фронтенда (`store.js` → `AlbumCoverModal.vue`): бэкенд умел его принимать с 2026-09-02, но фронтенд не отправлял, поэтому ручной выбор движка был недостижим. Яндекс-варианты в селекторе сознательно отсутствуют (SC-004: не умеют искать картинки), а сама Яндекс.Музыка управляется чекбоксом «Не искать в Яндекс.Музыке». Ранее в тот же день, ветка `452-album-cover-scraper-failover`: image-скрапперы fourget для поиска обложек теперь **перебираются** по настройке `albumCoverSearchScrapers` (`ddg → yahoo_japan → brave → google_cse`) с порогом `albumCoverSearchMinResults` — раньше `scraper=brave` был зашит прямо в URL и перебора не было вовсе; `AlbumCoverService.search(engine = null)` теперь читает настройку, а не жёсткий `SEARXNG` из дефолта сигнатуры. **Замер image-скрапперов 2026-09-26** на 5 студийных альбомах из библиотеки: `ddg` и `yahoo_japan` — 24/25 релевантных в топ-5, `brave` — 21/22, `google_cse` — 20/25; `baidu` (1/25) и `pinterest` (3/20) — мусор, `ftm` — поиск мемов по назначению. Ранее в тот же день, ветка `451-lyrics-scrapers-order`: порядок web-скрапперов по умолчанию — `google_cse → yahoo_japan → brave → yep` вместо `yep → brave`; нижняя граница `lyricsSearchMinResults` поднята с 0 до 1; синхронизирован второй экземпляр дефолта в `Tools.kt` (+ тест-страж против расхождения). **Актуальный curl-перебор 2026-09-26**: `brave` отдаёт `did not return a result object`, `yep` — `status=ok` с `web=[]` либо 100 нерелевантных URL; `google_cse` и `yahoo_japan` дали результат на 6 из 6 контрольных запросов. Обратите внимание: `ddg` и `brave` для **web**-поиска мертвы, но для **image**-поиска это отдельные эндпоинты, и там они рабочие.)

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
   **с 2026-09-26: google_cse→yahoo_japan→brave→yep** (до этого `yep→brave`,
   до 2026-09-02 — `brave→yep`), плюс post-filter «мусорных» URL
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
   по умолчанию) или `FOURGET` (`AlbumCoverService.searchFourgetImages`).
   Настройка — `KaraokeProperties.albumCoverSearchEngine`, либо параметр
   `engine` в `POST /api/song/searchalbumcover`. Внутри пути `FOURGET`
   image-скрапперы перебираются по настройке `albumCoverSearchScrapers`
   (`/api/v1/images?s=...&scraper=<имя>`), пока очередной не вернёт не меньше
   `albumCoverSearchMinResults` кандидатов — по образцу перебора
   web-скрапперов в `SearchTool.searchUrls` (2026-09-26, ветка
   `452-album-cover-scraper-failover`). Путь `SEARXNG` перебора не требует:
   SearXNG сам агрегирует движки внутри себя.
   **Движок выбирается и вручную**: в модалке обложек (`AlbumCoverModal.vue`)
   рядом с чекбоксом «Не искать в Яндекс.Музыке» стоит селектор «Движок
   веб-поиска», значение уходит в `engine` (`store.js` →
   `searchAlbumCoverPromise`). Дефолт селектора читается из настройки
   `albumCoverSearchEngine` при открытии модалки — как в `SearchText.vue` для
   `lyricsSearchEngine`. Форма A (решение владельца 2026-09-26): только
   `SEARXNG`/`FOURGET`, потому что селектор выбирает движок **фолбэка**, а
   ступень 1 (Яндекс.Музыка) остаётся за чекбоксом. Ручной выбор действует
   только на фолбэк: если Яндекс.Музыка нашла обложку, движок не спрашивается
   вовсе — поэтому чекбокс «Не искать в Яндекс.Музыке» и есть способ
   проверить выбранный движок (2026-09-26, ветка
   `453-album-cover-engine-selector`).
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
9. **VPN-детект** — `isVpnActive()`: страна текущего внешнего IP (через
   `api.country.is`, резерв `ipapi.co`) сравнивается со **списком** home-стран из
   `vpnHomeCountry`; страна не в списке → ВПН включён → Playwright не запускаем.
   Определённая страна кэшируется на `vpnCheckCacheTtlSeconds` секунд (Pass 454),
   поэтому частые вызовы (в том числе на каждую песню в `KaraokeProcessWorker`) не
   бьют во внешние сервисы. Резервный `ipapi.co` на 2026-09-26 отвечал HTTP 429 —
   статус ответа теперь проверяется и логируется явно.

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
  fourget могут не работать на конкретном IP/хостинге. Проверить вручную:
  `curl "http://<lyrics-search.base-url>/api/v1/web?s=test&scraper=<имя>"` —
  `status: "ok"` с непустым `web` значит движок реально работает. Список
  scraper'ов, перебираемых `SearchTool` по очереди, — **две** константы,
  которые обязаны совпадать: `defaultValue` свойства `lyricsSearchScrapers`
  в `KaraokeProperties.kt` (читается, когда ключа нет в `Karaoke.properties`)
  и `DEFAULT_LYRICS_SEARCH_SCRAPERS` в `llm/Tools.kt` (fallback, когда
  значение в `Karaoke.properties` пустое). Расхождение ловит тест
  `ToolsTest.дефолт scrapers в Tools совпадает с дефолтом в KaraokeProperties`.
  При очередной блокировке — добавить/заменить на другой рабочий scraper,
  а не менять весь бэкенд заново.
- **`status: "ok"` НЕ означает успех (тихая деградация)**: scraper умеет
  вернуть `ok` с пустым `web[]` (`yep`, `mojeek`, `marginalia`, `wiby` —
  2026-09-26), и тогда failover срабатывает только за счёт порога
  `lyricsSearchMinResults`. Обратный случай хуже: `yep` отдал **100**
  нерелевантных URL (викитека «Евгений Онегин», википедия «Талви Укко»,
  youtube) подряд, порог по количеству прошёл с запасом, и failover до
  рабочего scraper'а **не дошёл вовсе** — пайплайн получил мусорный список.
  Именно поэтому порядок важнее порога: проверять не «вернул ли scraper
  что-нибудь», а «на первом ли месте релевантный текстовый сайт».
  Прецедент: 2026-09-02 `yep` деградировал тихо (пусто) и failover работал;
  2026-09-26 он деградировал «громко» — и failover перестал помогать.
- **У скрапперов нет rate-limit в привычном смысле**: в конфиге fourget
  `BOT_PROTECTION = 0` (captcha выключена), собственного ограничителя частоты
  и обработки `429`/`Retry-After` в коде нет вовсе. Отказы приходят не
  статусом, а телом ответа, и маскируются под поломку парсера:
  `Brave did not return a result object` (ошибка разбора, `brave.php`),
  `DuckDuckGo detected an anomaly in the Javascript challenge response`,
  `Qwant returned a captcha redirect`, `Failed to grep JSON object`
  (Startpage). Вывод: нет квоты, которую можно выждать — есть невидимая
  блокировка, поэтому обязателен перебор и недопустимо считать `ok` успехом.
- **`HEADER_REGEX` блокирует ботов только на HTML-фронтенде**: конфиг
  fourget отклоняет User-Agent'ы вида `curl`/`python-requests`/`scrapy`/
  `go-http-client` (`"Tshh, blocked!"`), но проверка живёт в `frontend.php`
  и на `/api/v1/*` не распространяется — проверено `curl`/`python-requests`/
  `python-httpx`/`Go-http-client` (все получили `status: ok`). Важно при
  подключении внешних клиентов (MCP-серверов) к fourget.
- **Прокси для scraper'ов не настроены**: все `PROXY_*` в конфиге fourget —
  `false`, то есть все scraper'ы ходят с одного IP хоста. Блокировка одного
  движка неотличима от блокировки IP, а «размазать» нагрузку нечем.
- **Пагинация привязана к scraper'у**: `npt`-токен в ответе fourget кодирует
  имя scraper'а (`ddg1.<ключ>`, `google_cse33.<ключ>`) — сменить scraper
  между страницами одной выдачи нельзя.
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
| `lyricsSearchScrapers` | String | `"google_cse;yahoo_japan;brave;yep"` | Порядок scrapers через `;`. Был `"yep;brave"` (2026-09-02…2026-09-26), до этого — `"brave;yep"`. |
| `lyricsSearchMinResults` | Int | `2` | Порог «качества» — если scraper вернул меньше URL после post-filter, пробуем следующий. Значения <1 поднимаются до 1: при 0 пустая выдача считалась бы успешной и перебор не срабатывал бы. |
| `lyricsSearchUselessUrlPatterns` | String | (см. FR-004 спеки 294) | Паттерны для post-filter через `;`. |
| `albumCoverSearchScrapers` | String | `"ddg;yahoo_japan;brave;google_cse"` | Порядок **image**-scraper'ов через `;` — путь `FOURGET` при поиске обложек (2026-09-26). Независим от `lyricsSearchScrapers`: ключи разные, потому что эндпоинты разные (`/api/v1/web` vs `/api/v1/images`) и рабочие наборы scraper'ов у них не совпадают. |
| `albumCoverSearchMinResults` | Int | `2` | Порог для **image**-scraper'ов: вернул меньше картинок — пробуем следующий. Значения <1 поднимаются до 1 по той же причине, что и у `lyricsSearchMinResults`. |

**Логирование**: новая строка `🔧 [SearchTool] post-filter: было N,
осталось M (отброшено K)` на уровне INFO — для мониторинга
эффективности filter'а в проде (SC-005: доля поисков с `K > 0` —
5-30%).

**Тесты**: 11 unit-тестов в
`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/llm/ToolsTest.kt`
(по одному на каждое правило + happy path + edge cases + страж синхронности
двух экземпляров дефолта `lyricsSearchScrapers`). Активные,
не `@Disabled` — по образцу `AlbumCoverFinderParsingTest`.

## Порядок scrapers: как перепроверять (2026-09-26)

**Процедура.** Порядок в `lyricsSearchScrapers` — не константа на века, а
результат замера на конкретной машине. Проверять не «вернул ли scraper
что-нибудь», а **на каком месте первый релевантный текстовый сайт**:

```bash
curl "http://localhost:8889/api/v1/web?s=<запрос>&scraper=<имя>"   # web-поиск
curl "http://localhost:8889/api/v1/images?s=<запрос>&scraper=<имя>" # картинки
```

Брать 5-6 разных песен (русские + латиница), для каждой смотреть позицию
первого URL из числа текстовых сайтов (`genius`, `muztext`, `rulyrics`,
`amalgama-lab`, `karaoke.ru`, …) и сколько их в топ-5. Прогон на одной песне
даёт ложную картину: 2026-09-26 `brave` на первом запросе отдал 20
релевантных URL, а на следующих пяти — `did not return a result object`.

**Результат замера 2026-09-26** (6 песен × 4 scraper'а):

| Scraper | Ответов | Текстовый сайт есть | Попаданий в топ-5 (из 30) |
|---|---|---|---|
| `google_cse` | 6/6 | 6/6 (на 1-й позиции) | 14 |
| `yahoo_japan` | 6/6 | 6/6 (на 1-й, кроме одного) | 12 |
| `brave` | 1/6 | 1/6 | 3 |
| `yep` | 0/6 | — | — |

**Оговорка про `google_cse`**: это скраппер по **публичному** CSE-id, зашитому
в образ fourget (`GOOGLE_CX_ENDPOINT` в `data/config.php`), а не по
собственному API-ключу — то есть он зависит от чужого публичного эндпоинта и
может исчезнуть без предупреждения. Поэтому `yahoo_japan` (прямой скрейп
Yahoo Japan) стоит вторым, а не «в резерве»: при отказе `google_cse`
перебор дойдёт до него на том же запросе.


## Image-скрапперы fourget: как перепроверять (2026-09-26)

Тот же вопрос для **обложек** (`scraper` у `/api/v1/images`), но метрика
другая: релевантность картинки к альбому, а не наличие текстового сайта.
Рабочий признак — вхождение автора/альбома в название картинки
(`image[].title`); количество ответов само по себе ничего не значит.

**Процедура.** 5 студийных альбомов (сборники вида «Топ 50» и «Русские
хиты» для замера не годятся — канонической обложки у них нет), запрос — как
его строит `AlbumCoverService.defaultSearchQuery`:
`"<автор> <альбом> обложка альбома"`. Для каждого scraper'а считать, у
скольких из первых пяти картинок в `title` есть токен автора или альбома.

**Результат замера 2026-09-26** (5 альбомов: Ария/Герой асфальта,
ДДТ/Актриса Весна, Земфира/Прости меня моя любовь, КИНО/Группа крови,
Мельница/Знак четырёх):

| Scraper | Ответов | Релевантных в топ-5 | Что в выдаче |
|---|---|---|---|
| `ddg` | 5/5 | 24/25 | обложки (57-100 картинок) |
| `yahoo_japan` | 5/5 | 24/25 | обложки с Wikipedia (ровно 20) |
| `brave` | 5/5 | 21/22 | обложки с Discogs (37-46) |
| `google_cse` | 5/5 | 20/25 | обложки с Wikipedia (ровно 20) |
| `pinterest` | 4/5 | 3/20 | мусор |
| `baidu` | 5/5 | 1/25 | мемы («Альтушка для скуфа») |
| `ftm` | 5/5 | — | `find that meme` — поиск мемов по назначению |

**Живые, но исключённые сознательно**: `baidu` и `pinterest` отдают много
картинок и проходят любой порог по количеству — именно поэтому порог по
количеству без проверки релевантности опасен (тот же урок, что с `yep` в
web-поиске). `ftm` — не про обложки вообще.

**Мёртвые image-скрапперы** (2026-09-26, 0 ответов): `yandex` (`Failed to
decode JSON`), `google` (`Still working on a Google scraper that uses a
headful browser`), `google_api` (нужны ключи), `startpage` (`Failed to grep
JSON object`), `qwant` (`Qwant returned an API error`), `cara` (HTTP-ошибка),
`flickr`, `pexels`, `pixabay`, `unsplash` (таймаут 30 с), `fivehpx`, `vsco`
(таймаут 30 с), `imgur`, `solofield`.

**Ключевой вывод**: `ddg` и `brave` для **web**-поиска мертвы (см. замер
выше), но для **image**-поиска это отдельный эндпоинт, и там оба рабочие. Не
переносить выводы одного замера на другой эндпоинт.

**Принципы и контекст**:
- Перебор scraper'ов реализован в ДВУХ местах и по одному образцу:
  `SearchTool.searchUrls` (web, тексты песен) и
  `AlbumCoverService.searchFourgetImages` (images, обложки). Обложки
  добавлены 2026-09-26 (ветка `452-album-cover-scraper-failover`): до этого
  `scraper=brave` был зашит в URL и перебора не было — оговорка NFR-006
  «обложки не трогаем» больше не действует. Настройки независимы:
  `lyricsSearchScrapers` + `lyricsSearchMinResults` для текстов,
  `albumCoverSearchScrapers` + `albumCoverSearchMinResults` для картинок;
  менять одну, ожидая эффекта на другой, — ошибка.
- Семантическая фильтрация URL (страница про текст песни vs альбом
  vs артиста) — НЕ наша задача; это работа LLM-парсинга
  (`ScraperAgent`).
- При добавлении новых scrapers (mojeek/startpage/qwant/wikipedia) —
  curl-перебор по процедуре в `specs/014-lyrics-search-replacement/research.md`
  → Production finding.

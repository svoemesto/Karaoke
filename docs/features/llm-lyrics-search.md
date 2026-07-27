# LLM-assisted поиск текстов и аккордов

> **Status**: active
> **Feature Key**: llm-lyrics-search
> **Last Updated**: 2026-07-27

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
   brave→yep, см. `specs/014-lyrics-search-replacement/research.md` — `SearchTool.searchUrls`).
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
3. **Движок поиска обложки альбома** — отдельный выбор (enum
   `AlbumCoverSearchEngine` в `AlbumCoverFinder.kt`, только 2 варианта —
   Yandex Cloud Search API возвращает текстовые результаты, не картинки):
   `SEARXNG` (`AlbumCoverService.searchSearxngImages`, сегодняшнее поведение
   по умолчанию) или `FOURGET` (`AlbumCoverService.searchFourgetImages`,
   `fourget` `/api/v1/images?s=...&scraper=brave`). Настройка —
   `KaraokeProperties.albumCoverSearchEngine`, либо параметр `engine` в
   `POST /api/song/searchalbumcover`.
4. **Автоочистка результатов поиска для готовых песен**
   (`specs/015-search-engine-selection`): как только `Song.saveToDb()`
   фиксирует пересечение песней порога готовности (статус ≥3, тот же порог,
   что для публичного плеера — `crossedReadyThreshold`), результаты поиска
   текста для неё удаляются автоматически. Для уже готовых песен — кнопка
   «Удалить результаты поиска готовых песен» на главной странице админки
   (`POST /api/utils/deletesearchresultsforreadysongs`,
   `HealthReport.deleteSearchResultsForReadySongs`, фоновый прогон + SSE-тост,
   по образцу `doRecalcPlayerReadiness`).
5. **Скрейпинг** — для каждого результата парсим HTML:
   - Статический — `jsoup` (см. [jsoup](https://jsoup.org/)).
   - JS-рендер / авторизация — `UtilsPlaywright.kt` через Playwright/Selenium.
6. **LLM-анализ** — `ScraperAgent.kt` через `LmStudioService.kt` (тонкий клиент над
   OpenAI-совместимым `/v1/chat/completions` LM Studio, поднятого на хост-машине админа):
   - Структурирование текста (разбивка на строки/куплеты/припевы).
   - Нормализация аккордов (`Am`, `A minor`, `a-moll` → `Am`).
   - Определение ключа и BPM.
   - Тем же клиентом (`LmStudioService`) пользуется `TextCorrectorAgent.kt` — AI-редактор текста
     в SubsEdit.vue (исправление орфографии/пунктуации).
7. **Яндекс.Музыка** — отдельный путь (`searchLastAlbumYm3`/
   `checkLastAlbumYm`): авторизация по сохранённой сессии на диске, поиск
   нового альбома автора. Возвращает `AlbumSearchResult`:
   `Success`/`VpnBlocked`/`AuthExpired`/`BotDetected`/`Unknown`.
8. **VPN-детект** — `isVpnActive()` через `api.country.is` (страна != RU →
   ВПН включён → Playwright не запускаем).

## Инварианты / правила

- **MUST**: не использовать внешние SaaS (OpenAI, Anthropic) в горячем
  пути. Только локальный LM Studio (`lmStudioUrl`, см. `KaraokeProperties.kt`).
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
- [`Tools.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt) — инструменты для LLM (`SearchTool`)
- [`UtilsAI.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt) — `getLyricsSearch` (диспетчер движков), `getYandexSearch`, `LyricsSearchEngine`
- [`AlbumCoverFinder.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt) — `AlbumCoverService`, `AlbumCoverSearchEngine`
- [`HealthReport.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt) — `deleteSearchResultsForReadySongs` (массовая очистка)
- [`UtilsPlaywright.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt) — Playwright/Selenium
- [`Utils.searchLastAlbumYm3`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — Яндекс.Музыка

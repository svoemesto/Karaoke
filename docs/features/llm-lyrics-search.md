# LLM-assisted поиск текстов и аккордов

> **Status**: active
> **Feature Key**: llm-lyrics-search
> **Last Updated**: 2026-07-20

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
2. **fourget** (`lyrics-search.base-url` / `LYRICS_SEARCH_BASE_URL`,
   `SearchTool.searchUrls` в `llm/Tools.kt`) — self-hosted мета-поисковик
   (`/api/v1/web?s=...&scraper=yandex`), замена SearXNG для поиска текстов
   песен (см. `specs/014-lyrics-search-replacement/research.md`) — Yandex
   как источник лучше индексирует русскоязычные "текст песни" запросы, чем
   движки, доступные в прежнем SearXNG-бэкенде. Поиск обложек альбомов
   (`AlbumCoverService.searchSearxngImages`) по-прежнему использует SearXNG —
   эта замена его не затрагивает.
3. **Скрейпинг** — для каждого результата парсим HTML:
   - Статический — `jsoup` (см. [jsoup](https://jsoup.org/)).
   - JS-рендер / авторизация — `UtilsPlaywright.kt` через Playwright/Selenium.
4. **LLM-анализ** — `ScraperAgent.kt` через `LmStudioService.kt` (тонкий клиент над
   OpenAI-совместимым `/v1/chat/completions` LM Studio, поднятого на хост-машине админа):
   - Структурирование текста (разбивка на строки/куплеты/припевы).
   - Нормализация аккордов (`Am`, `A minor`, `a-moll` → `Am`).
   - Определение ключа и BPM.
   - Тем же клиентом (`LmStudioService`) пользуется `TextCorrectorAgent.kt` — AI-редактор текста
     в SubsEdit.vue (исправление орфографии/пунктуации).
5. **Яндекс.Музыка** — отдельный путь (`searchLastAlbumYm3`/
   `checkLastAlbumYm`): авторизация по сохранённой сессии на диске, поиск
   нового альбома автора. Возвращает `AlbumSearchResult`:
   `Success`/`VpnBlocked`/`AuthExpired`/`BotDetected`/`Unknown`.
6. **VPN-детект** — `isVpnActive()` через `api.country.is` (страна != RU →
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

## Ссылки на ключевые классы/файлы

- [`LyricsFinderService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/LyricsFinderService.kt) — главный оркестратор
- [`ScraperAgent.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/ScraperAgent.kt) — извлечение текста песни из HTML через LLM
- [`TextCorrectorAgent.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/TextCorrectorAgent.kt) — AI-редактор текста (SubsEdit.vue)
- [`LmStudioService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/LmStudioService.kt) — тонкий клиент LM Studio
- [`Tools.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt) — инструменты для LLM
- [`UtilsPlaywright.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPlaywright.kt) — Playwright/Selenium
- [`Utils.searchLastAlbumYm3`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — Яндекс.Музыка

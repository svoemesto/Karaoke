# LLM-assisted поиск текстов и аккордов

> **Status**: active
> **Feature Key**: llm-lyrics-search
> **Last Updated**: 2026-07-20

## Что делает

Автоматически находит тексты песен, аккорды, ключ, BPM через
web-поиск (SearXNG) + скрейпинг сайтов + LLM-анализ (Ollama). Использует
LangChain4j + локальные модели. Для сайтов с JS-рендером или
авторизованной сессией (Яндекс.Музыка) — Playwright/Selenium.

## Зачем

Ручной поиск текстов и аккордов — часы на песню. LLM умеет извлекать
структурированные данные из сырого HTML, догадываться о формате аккордов,
исправлять опечатки. SearXNG + Ollama — локальные инструменты, не
зависят от внешних SaaS (см.
[constitution.md#i-self-contained-автопайплайн](../../.specify/memory/constitution.md)).

## Как работает (кратко)

1. **`LyricsFinderService`** — оркестратор: получает `Settings` (песня),
   формирует запрос (автор + название), запускает поиск.
2. **SearXNG** (`SEARXNG_BASE_URL`) — мета-поисковик, агрегирует
   результаты Google/Bing/DuckDuckGo.
3. **Скрейпинг** — для каждого результата парсим HTML:
   - Статический — `jsoup` (см. [jsoup](https://jsoup.org/)).
   - JS-рендер / авторизация — `UtilsPlaywright.kt` через Playwright/Selenium.
4. **LLM-анализ** — `Agents.kt` + `Tools.kt` (LangChain4j + Ollama):
   - Структурирование текста (разбивка на строки/куплеты/припевы).
   - Нормализация аккордов (`Am`, `A minor`, `a-moll` → `Am`).
   - Определение ключа и BPM.
5. **Яндекс.Музыка** — отдельный путь (`searchLastAlbumYm3`/
   `checkLastAlbumYm`): авторизация по сохранённой сессии на диске, поиск
   нового альбома автора. Возвращает `AlbumSearchResult`:
   `Success`/`VpnBlocked`/`AuthExpired`/`BotDetected`/`Unknown`.
6. **VPN-детект** — `isVpnActive()` через `api.country.is` (страна != RU →
   ВПН включён → Playwright не запускаем).

## Инварианты / правила

- **MUST**: не использовать внешние SaaS (OpenAI, Anthropic) в горячем
  пути. Только локальный Ollama.
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
- **Долгие LLM-запросы**: Ollama на CPU медленная. Лучше вынести
  в отдельный лейн/поток, чтобы не блокировать.
- **SearXNG недоступен**: проверяйте `SEARXNG_BASE_URL` перед первым
  запуском. Фолбэк — `DuckDuckGo HTML` (без SearXNG), но он rate-limited.

## Ссылки на ключевые классы/файлы

- [`LyricsFinderService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/LyricsFinderService.kt) — главный оркестратор
- [`Agents.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Agents.kt) — LangChain4j-агенты
- [`Tools.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt) — инструменты для LLM
- [`UtilsPlaywright.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/UtilsPlaywright.kt) — Playwright/Selenium
- [`Utils.searchLastAlbumYm3`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — Яндекс.Музыка

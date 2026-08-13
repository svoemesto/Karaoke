# Feature Specification: SEO-HTML вместо генерации PNG для ботов

**Feature Branch**: `180-og-seo-html`

**Created**: 2026-08-13

**Status**: Draft

**Input**: User description: "Изменить концепцию 'OG render'. Сейчас в логах karaoke-web на проде видно много запросов от ботов (bingbot, YandexBot, Googlebot, vkShare, TelegramBot и др.) к `/api/public/og/song?id=NNN` — для каждого из них на лету генерируется PNG-картинка (через `/api/public/song-vk-image/{id}`), что занимает время и ресурсы. Это было нужно для публикаций в ВК (сниппет формировался по первой видимой картинке в 'голом' HTML), но сейчас подход изменился и необходимость отдавать ботам картинку отпала. Нужно отдавать ботам быстрый HTML с информацией о песне, максимально качественный для индексации ботами (Yandex/Google/Bing) и для сниппетов соцсетей."

## Контекст

В проекте есть endpoint `PublicOgSongController.ogSongHtml()` по пути
`/api/public/og/song?id=NNN`, в который nginx (`deploy/web-server-deploy/deploy/80to8897`,
location `/song`) перенаправляет запросы **только** от ботов (User-Agent из списка
`vkShare|TelegramBot|Twitterbot|facebookexternalhit|LinkedInBot|WhatsApp|Slackbot|ViberBot|SkypeUriPreview|Googlebot|bingbot|YandexBot|YandexImages`). Обычные браузеры идут в SPA `karaoke-public` (Vue Router рендерит `SongView`).

Сейчас этот endpoint возвращает «голый» HTML c видимым `<img src="https://sm-karaoke.ru/api/public/song-vk-image/{id}">`,
а `/api/public/song-vk-image/{id}` (см. `PublicApiController.songVkImage()`) динамически
генерирует PNG 1200×630 с обложкой альбома + автора + названием песни. Генерация
занимает ощутимое время (рисуем BufferedImage, читаем из MinIO, ресайзим) и
расходует CPU/память на каждый запрос бота. В логах прод-сервера только за один
день видны десятки таких запросов от `bingbot` и `YandexBot` (PR `180`).

Изначально endpoint проектировался для сниппетов ВК-парсера (видимая картинка в
`<body>` → сниппет поста), но с момента реализации автопубликации ВК (см.
`specs/121-vk-news-auto-publish`) подход к постам изменился: посты формируются
напрямую через VK API с прикреплением демо-MP4, а парсинг ссылок ВК больше не
является основным каналом шаринга песен. Тем не менее endpoint остаётся
единственной точкой входа для поисковых ботов (Googlebot/Bingbot/YandexBot) при
обходе страниц `/song?id=NNN` — а этим ботам картинка не нужна, им нужны
**структурированные метаданные** о песне для индексации.

## Цель

Заменить «картинку + видимый `<img>` в голом HTML» на **полноценный SEO-HTML**
(Schema.org JSON-LD, Open Graph, Twitter Card, semantic HTML c видимым
контентом для ботов), который:

1. **Возвращается за миллисекунды** (нет генерации изображения) — экономия CPU/RAM.
2. **Содержит максимум структурированной информации** о песне (название, автор,
   альбом, год, ключевые слова, описание, текст песни, аккорды/табы, ссылки на
   стриминг-платформы) — для лучшей индексации Yandex/Google/Bing.
3. **Пригоден для сниппетов** соцсетей и мессенджеров (Telegram, VK, WhatsApp,
   Twitter, Facebook, Slack) через стандартные `<meta property="og:*">` и
   `<meta name="twitter:*">` — без необходимости генерации изображения.
4. **Сохраняет обратную совместимость** по пути `/api/public/og/song` и списку
   User-Agent'ов в nginx (Pass 35, 2026-08-05, см. `docs/architecture-notes.md`,
   `80to8897`) — обычные браузеры продолжают попадать в SPA без изменений.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Поисковый бот Yandex/Bing/Google получает полную информацию о песне за миллисекунды (Priority: P1)

Поисковый бот (YandexBot, bingbot, Googlebot) обходит страницу
`https://sm-karaoke.ru/song?id=11661` (или любую другую). Nginx по
User-Agent направляет его в `/api/public/og/song?id=11661`. Karaoke-web
возвращает HTML за миллисекунды (без обращения к MinIO, без генерации
изображения), при этом HTML содержит:

- `<title>` с названием песни и автором.
- `<meta name="description">` с кратким описанием песни (поле `description`).
- Canonical URL `<link rel="canonical">` на SPA-страницу.
- Schema.org JSON-LD типа `MusicRecording` (или `Song`) с полным
  набором свойств: name, byArtist, inAlbum, datePublished, genre, inLanguage,
  description, url, image, lyrics (если есть).
- Open Graph (`og:title`, `og:description`, `og:image`, `og:url`, `og:type=music.song`,
  `og:site_name`, `og:locale=ru_RU`).
- Twitter Card (`twitter:card=summary_large_image`, `twitter:title`,
  `twitter:description`, `twitter:image`).
- Видимый semantic-контент с текстом песни, аккордами/табами, ссылками на
  стриминг-платформы (для ботов, которые читают именно видимый текст — например,
  некоторые индексаторы Yandex по состоянию на 2024–2026).

**Why this priority**: Это центральная ценность фичи — убрать дорогую генерацию
картинки для ботов и при этом дать им больше структурированной информации для
индексации. Без этой истории остальные не имеют смысла (это переход с одной
концепции на другую).

**Independent Test**: С помощью `curl --user-agent "Mozilla/5.0 (compatible;
YandexBot/3.0)" "https://sm-karaoke.ru/song?id=11661"` (через публичный URL или
локальный nginx) проверить, что ответ приходит быстро (TTFB < 50 мс без учёта
сетевой задержки), Content-Type — `text/html; charset=UTF-8`, и в HTML
присутствуют все перечисленные выше элементы.

**Acceptance Scenarios**:

1. **Given** песня с `id=11661` существует в БД (status ≥ 3, не помечена
   тегом `SKIP`), **When** бот делает запрос
   `GET /api/public/og/song?id=11661`, **Then** ответ приходит за **< 100 мс**
   (без обращения к MinIO за картинкой), HTML содержит `<title>` с
   названием и автором, canonical URL, OG-теги, Twitter Card-теги, JSON-LD
   с `@type: "MusicRecording"` (или `"Song"`), видимый `<h1>` с названием
   песни и `<h2>` с автором, видимый текст песни (если `formattedTextSong`
   не пуст) и блок со ссылками на стриминг-платформы (если они заполнены).
2. **Given** в логах прод-сервера видны десятки запросов от `bingbot` и
   `YandexBot` за час, **When** фича включена, **Then** в логах **нет**
   обращений к `/api/public/song-vk-image/{id}` от ботов (этот endpoint
   больше не вызывается при обходе OG-endpoint).
3. **Given** обычный браузер (Chrome, Firefox, Safari без bot User-Agent)
   запрашивает `/song?id=11661`, **When** nginx по правилу в `80to8897`
   не находит User-Agent в списке ботов, **Then** запрос уходит в SPA
   `karaoke-public` (порт 7907) — поведение **не меняется** фичей.

---

### User Story 2 - Сниппет в соцсетях/мессенджерах формируется из OG-тегов без обращения к изображению (Priority: P2)

При шаринге ссылки `https://sm-karaoke.ru/song?id=11661` в мессенджере
(Telegram, WhatsApp, Viber) или соцсети (VK, Facebook, Twitter, Slack) —
платформа парсит HTML OG-endpoint и формирует сниппет из
`<meta property="og:title">`, `<meta property="og:description">` и
`<meta property="og:image">`. Платформа **не** инициирует отдельный
запрос на генерацию PNG — `og:image` указывает на **готовое изображение**
(обложка альбома или обложка автора из MinIO), которое уже существует в
хранилище и отдаётся nginx'ом через `/minio/`-location без участия
Java/Spring.

**Why this priority**: Соцсети и мессенджеры исторически формировали
сниппет по первой видимой картинке в HTML (что и было причиной «голого»
HTML в первой реализации). Сейчас ВКонтакте не использует этот endpoint
для автопостинга (см. `specs/121-vk-news-auto-publish`), но Telegram/
WhatsApp/Viber/Facebook/Slack всё ещё могут парсить ссылку — для них
OG-теги с прямой ссылкой на готовую картинку альбома работают корректно
без нагрузки на бэкенд.

**Independent Test**: Отправить в тестовый Telegram-бот (через BotFather
или в личный чат через «Предпросмотр ссылки») ссылку
`https://sm-karaoke.ru/song?id=11661`. Убедиться, что Telegram подтягивает
название песни, описание и картинку альбома (а не показывает ошибку
«не удалось загрузить превью»).

**Acceptance Scenarios**:

1. **Given** у песни есть обложка альбома (`Pictures.getPictureByName("$author - $year - $album")` не null),
   **When** бот/мессенджер парсит OG-endpoint, **Then** `<meta property="og:image">`
   содержит абсолютный URL (`https://sm-karaoke.ru/minio/karaoke/<путь-к-картинке-альбома>`),
   по которому nginx отдаёт готовый PNG без обращения к Java-приложению.
2. **Given** у песни нет обложки альбома в MinIO, **When** бот/мессенджер
   парсит OG-endpoint, **Then** `<meta property="og:image">` указывает
   на дефолтный логотип (`https://sm-karaoke.ru/KARAOKE_LOGO.png` или
   `/minio/karaoke/KARAOKE_LOGO.png`), чтобы сниппет не был без картинки.
3. **Given** бот/мессенджер НЕ запрашивает `/api/public/song-vk-image/{id}`
   (старый PNG-endpoint), **When** фича включена, **Then** устаревший
   endpoint остаётся в коде, но не используется из OG-логики (см. FR-009).

---

### User Story 3 - Endpoint корректно обрабатывает крайние случаи (Priority: P2)

При обходе бот может наткнуться на:

- Песню со статусом < 3 (ещё не в коллекции).
- Песню с тегом `SKIP` (контент удалён по требованию правообладателя).
- Несуществующий id (404).
- Песню без обложки альбома/автора.

В каждом из этих случаев endpoint возвращает валидный HTML с HTTP 200
(для ботов лучше отдать осмысленный HTML, чем 404 — иначе бот
проиндексирует «не найдено» и больше не вернётся) **ИЛИ** 404 (если это
семантически правильнее — например, для несуществующего id).

**Why this priority**: Боты регулярно переобходят URL'ы из выдачи, и при
появлении/удалении песен ожидается, что endpoint ведёт себя
предсказуемо и без утечек внутренней информации.

**Independent Test**: Запустить `curl` с User-Agent YandexBot для
id=999999 (не существует), id=10 (status < 3), id=NNN с тегом `SKIP`,
id=MMM без обложки. Проверить, что во всех случаях ответ валидный
(либо 404 + короткий HTML с описанием, либо 200 + HTML с placeholder).

**Acceptance Scenarios**:

1. **Given** запрошен `id`, для которого в БД нет песни, **When** бот
   делает запрос, **Then** endpoint возвращает **HTTP 404** с коротким
   HTML-телом, объясняющим что песня не найдена. Никаких 500, никакой
   утечки внутреннего стектрейса.
2. **Given** песня существует, но помечена тегом `SKIP` (контент удалён
   по требованию правообладателя), **When** бот делает запрос, **Then**
   HTML содержит **заглушку** «Контент удалён по требованию
   правообладателя» (по тому же шаблону, что используется в публичной
   ленте — см. AGENTS.md «Тег SKIP»). Никакого реального текста песни,
   обложек, аккордов, ссылок на стриминг в HTML **не отдаётся** —
   только мета-информация (название, автор) и предупреждение.
3. **Given** у песни `idStatus < 3` (не в коллекции), **When** бот
   делает запрос, **Then** HTML содержит название и автора, но **без**
   текста песни/аккордов/табов (их ещё нет). OG-теги указывают на
   дефолтный логотип. HTTP 200.
4. **Given** у песни нет обложки альбома и обложки автора в MinIO,
   **When** бот делает запрос, **Then** `<meta property="og:image">`
   указывает на `/minio/karaoke/KARAOKE_LOGO.png` (или дефолтный
   fallback), а не на 404.

---

### User Story 4 - Endpoint остаётся в nginx-конфиге и легко отключается при необходимости (Priority: P3)

Если в будущем потребуется отключить SEO-HTML endpoint (например,
вернуть «голый» HTML с картинкой или вовсе закрыть от ботов), это
делается **минимальным изменением** — либо правкой nginx `80to8897`
(убрать/сузить список User-Agent'ов), либо отключением endpoint через
feature-flag в `KaraokeProperties` (без правки nginx). Endpoint
логирует каждый запрос с User-Agent и `song.id`, чтобы можно было
наблюдать за обходами ботов.

**Why this priority**: Гибкость инфраструктуры. Сейчас endpoint
успешно работает для ботов, но если поведение поисковиков изменится
или возникнут проблемы с индексацией — нужна возможность быстро
отключить или переключить поведение.

**Independent Test**: Через `KaraokeProperties` установить
`ogRenderEnabled=false`. Перезапустить karaoke-web. Проверить, что
endpoint `/api/public/og/song?id=NNN` возвращает осмысленный 503
или «отключено» HTML, не делая никаких обращений к БД/MinIO.

**Acceptance Scenarios**:

1. **Given** в `KaraokeProperties` установлен `ogRenderEnabled=false`,
   **When** бот делает запрос, **Then** endpoint возвращает короткий
   HTML «SEO-выдача временно отключена» с HTTP 503 (без обращения к БД
   или MinIO).
2. **Given** endpoint включён, **When** бот делает запрос, **Then** в
   логи пишется строка `OG render for song id=NNN, User-Agent=...`
   (тот же формат, что был раньше), чтобы можно было отслеживать
   обходы по `grep "OG render" karaoke-web.log`.

### Edge Cases

- **Бот делает запрос с `id=0` или без `id`** — endpoint возвращает
  HTTP 400 с коротким HTML «Не указан id песни» (как сейчас, в коде
  есть защита `if (id == null || id <= 0)`).
- **Бот делает запрос с `id=` отрицательным или очень большим
  (Number overflow)** — endpoint валидирует диапазон и при невалидном
  значении возвращает 400 (не 500).
- **Очень длинный текст песни** (> 50 КБ в `formattedTextSong`) —
  endpoint вставляет текст **как есть** в HTML (для индексации
  длинный текст полезнее, чем усечённый). Если текст превышает
  разумный предел (например, 1 МБ), endpoint обрезает с маркером
  «[…фрагмент текста песни усечён для индексации]», чтобы не
  вернуть огромный HTML и не положить nginx/бота по таймауту.
- **Бот запрашивает OG-endpoint в момент рестарта karaoke-web**
  (502 от nginx из-за fail-fast 5s на `proxy_connect_timeout`) — это
  поведение nginx, **не** endpoint'а; бот повторит обход через
  свой штатный интервал.
- **Бот с User-Agent, не входящим в список `80to8897`** (например,
  новый бот, про который мы не знаем) — nginx не редиректит его в
  OG-endpoint, запрос уходит в SPA `karaoke-public` (Vue Router
  рендерит SongView). Это поведение nginx **не меняется** фичей.
- **Тег `SKIP` стоит в `tags` вместе с другими тегами** (например,
  `SKIP rock`) — endpoint всё равно трактует песню как
  «удалённую», скрывает контент (текст, аккорды, табы, ссылки на
  стриминг) и показывает заглушку. Проверка — через
  `tags.split(" ").map { it.uppercase() }.contains("SKIP")`, тот же
  паттерн, что в `SongPublicDto.fromSong().contentRemoved`.
- **Параллельные запросы от разных ботов к одной песне** — endpoint
  должен корректно обрабатывать конкурентные обращения (stateless,
  без shared state, без блокировок). Поскольку новая логика — это
  чистый HTML из полей БД (без обращения к MinIO за картинкой), это
  выполняется автоматически.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Endpoint `GET /api/public/og/song?id=NNN` (см.
  `PublicOgSongController.ogSongHtml()` в
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt`)
  ДОЛЖЕН возвращать валидный SEO-HTML (Content-Type: `text/html;
  charset=UTF-8`) с полным набором мета-тегов и структурированных
  данных о песне — **вместо** текущего «голого» HTML c видимым
  `<img src="/api/public/song-vk-image/{id}">`. Endpoint продолжает
  принимать запросы от ботов через nginx `80to8897` без изменений
  в nginx-конфиге.

- **FR-002**: HTML ДОЛЖЕН включать следующие элементы (в указанном
  порядке в `<head>`):
  1. `<meta charset="UTF-8">`
  2. `<title>` в формате `"{songName} — {author} — Караоке на
     sm-karaoke.ru"` (если `songName` или `author` пустые — `?`).
  3. `<meta name="description">` — до 250 символов из поля
     `Song.description` (если пусто — из первых N символов
     `formattedTextSong`, очищенных от HTML/разметки). Если и
     description и текст пусты — короткий placeholder
     «Караоке-песня {author} — {songName} на сайте sm-karaoke.ru».
  4. `<link rel="canonical" href="https://sm-karaoke.ru/song?id={id}">`
     (абсолютный URL SPA-страницы).
  5. `<meta name="robots" content="index, follow">` для ботов.
  6. Open Graph (`<meta property="og:…">`):
     - `og:title` — то же, что `<title>`.
     - `og:description` — то же, что `<meta name="description">`.
     - `og:url` — canonical URL.
     - `og:type` — `music.song` (или `website` если Schema.org
       решит, что тип не подходит).
     - `og:site_name` — `Караоке на sm-karaoke.ru`.
     - `og:locale` — `ru_RU`.
     - `og:image` — абсолютный URL обложки альбома
       (`https://sm-karaoke.ru/minio/karaoke/<author>/<year> -
       <album>/<author> - <year> - <album>.album.png`), или
       дефолтный логотип если обложки нет.
     - `og:image:width` и `og:image:height` — реальные размеры
       обложки (400×400, см. `Picture.pictureAlbum` в Song.kt) или
       дефолтные для логотипа.
     - `og:image:alt` — `"{songName} — {author}"`.
  7. Twitter Card (`<meta name="twitter:…">`):
     - `twitter:card` — `summary_large_image`.
     - `twitter:title`, `twitter:description`, `twitter:image` —
       аналогично OG-тегам.
  8. JSON-LD блок `<script type="application/ld+json">` с
     Schema.org `MusicRecording` (или `Song`) — см. FR-003.
  9. Видимый semantic HTML в `<body>` — см. FR-004.

- **FR-003**: JSON-LD ДОЛЖЕН содержать Schema.org `MusicRecording`
  со следующими свойствами (поля пусты/не заполнены → свойство
  опускается, а не отдаётся как `null` или пустая строка):
  - `@context`: `https://schema.org`.
  - `@type`: `MusicRecording` (при наличии текста песни/аккордов)
    или `Song` (если нет).
  - `@id`: canonical URL `https://sm-karaoke.ru/song?id={id}`.
  - `name`: `songName`.
  - `byArtist`: объект `{ "@type": "MusicGroup", "name": "author" }`
    (или `"@type": "Person"` если автор один — определяется наличием
    «,» в строке автора).
  - `inAlbum`: объект `{ "@type": "MusicAlbum", "name": "album",
    "datePublished": year, "albumProductionType": "Studio" }`
    (только если `album` не пусто).
  - `datePublished`: год (`year`) как строка.
  - `genre`: массив тегов из `tags` (кроме `SKIP`, нижний регистр).
  - `inLanguage`: `ru` (если язык не указан явно).
  - `description`: `description` или короткий placeholder.
  - `url`: canonical URL.
  - `image`: URL обложки альбома (или дефолт).
  - `lyrics`: `{ "@type": "CreativeWork", "text": formattedTextSong }`
    (только если текст не пуст и песня не SKIP).
  - `keywords`: теги через запятую (если есть).
  - `isAccessibleForFree`: `true`/`false` на основе
    `Song.isFreelyAvailableNow` или `Song.free`.

- **FR-004**: Видимый контент в `<body>` ДОЛЖЕН быть семантически
  размечен и содержать (порядок сверху вниз):
  1. `<header>` с `<h1>{songName}</h1>` и `<h2>{author}</h2>`. Если
     есть `shortDescription` или `warning`, вывести их как `<p>`
     рядом с заголовком (warning — с CSS-классом
     `class="warning"` для красного цвета).
  2. `<section id="meta">` с метаданными песни в виде
     `<dl>` (definition list): год, альбом, трек, ключ, BPM, жанры
     (из тегов), длительность (если `ms > 0` — в формате `mm:ss`).
     Только непустые поля.
  3. `<section id="description">` с `<h3>О песне</h3>` и параграфом
     `description` (если не пуст). Если пуст — секция опускается.
  4. `<section id="lyrics">` с `<h3>Текст песни</h3>` и
     `<pre>formattedTextSong</pre>` (если не пуст и песня не SKIP).
     Для поэзии (SongType=poetry) — то же.
  5. `<section id="chords">` с `<h3>Аккорды / табы</h3>` и
     `<pre>formattedTextChords</pre>` или
     `<pre>formattedTextTabs</pre>` (если не пусто и песня не SKIP).
  6. `<section id="listen">` с `<h3>Послушать</h3>` и списком
     `<ul>` ссылок на стриминг-платформы (только непустые `link*`):
     Sponsr, Boosty, VK (karaoke/lyrics/chords), Telegram, Yandex
     Music, YouTube (через `linkDzen*`/`linkMax*`), и т.п. Каждая
     ссылка — `<a href="{link}" rel="noopener noreferrer">{label}</a>`.
  7. `<footer>` с копирайтом `© sm-karaoke.ru`, ссылкой на
     canonical URL и meta generator `<meta name="generator"
     content="sm-karaoke.ru karaoke-pipeline">` в `<head>`.

- **FR-005**: Все строковые поля в HTML ДОЛЖНЫ быть экранированы
  (HTML-escape: `&`, `<`, `>`, `"`, `'`) перед вставкой. Это
  касается названия песни, автора, альбома, описания, текста
  песни, аккордов, тегов — **всего** что приходит из БД и попадает
  в HTML. Используется существующий helper `escape()` или
  эквивалентный `org.springframework.web.util.HtmlUtils.htmlEscape()`.

- **FR-006**: Endpoint ДОЛЖЕН обрабатывать крайние случаи (см. User
  Story 3):
  - `id == null || id <= 0` → HTTP 400 с коротким HTML «Не указан id
    песни (добавьте ?id=NNN)». Без обращения к БД.
  - Песня не найдена в БД → HTTP 404 с коротким HTML «Песня не
    найдена: id=NNN». Без stack trace, без утечки SQL.
  - Песня с тегом `SKIP` → HTTP 200 с HTML, содержащим
    `<meta name="robots" content="noindex, nofollow">`, видимый
    заголовок `Контент удалён по требованию правообладателя`,
    название и автор как `<h1>`/`<h2>` (чтобы бот проиндексировал
    метаданные, но не сам контент), и **без** текста/аккордов/
    ссылок на стриминг.
  - Песня с `idStatus < 3` → HTTP 200 с HTML, содержащим
    название/автора и общую информацию (год, альбом), но **без**
    текста/аккордов/табов (их физически нет в БД для таких песен).
  - Песня без обложки альбома в MinIO → `og:image` указывает на
    `https://sm-karaoke.ru/minio/karaoke/KARAOKE_LOGO.png` (или
    абсолютный путь к дефолтному логотипу), HTTP 200.

- **FR-007**: Endpoint НЕ ДОЛЖЕН обращаться к MinIO за обложкой
  альбома «на лету» для генерации HTML. Используется **готовый**
  PNG из хранилища (если есть) или дефолтный логотип (если нет) —
  эти файлы уже лежат в MinIO и отдаются nginx'ом через
  `/minio/`-location напрямую, без участия Java. Это исключает
  регрессию по производительности: новый endpoint не должен быть
  медленнее текущего.

- **FR-008**: Endpoint ДОЛЖЕН логировать каждый запрос с тем же
  форматом строки, что был раньше:
  ```
  OG render for song id={id}, User-Agent={userAgent}
  ```
  Это позволяет оператору отслеживать обходы ботов через
  `grep "OG render" karaoke-web.log` (текущий формат лога
  остаётся неизменным).

- **FR-009**: Endpoint `/api/public/song-vk-image/{id}` (см.
  `PublicApiController.songVkImage()`) ДОЛЖЕН быть **оставлен в
  коде** в текущей реализации (PNG-генерация 1200×630 c обложкой
  альбома + автора). Он больше **не вызывается** из логики
  `PublicOgSongController` и не нужен для нового SEO-HTML endpoint,
  но:
  - удаление endpoint'а может сломать потенциальные кэшированные
    ссылки в VK/мессенджерах, которые ссылаются на старый PNG;
  - endpoint всё ещё может быть полезен для ручных публикаций
    в будущем (например, для шаринга в Twitter через
    `card_image`).
  В этой версии фичи endpoint не трогаем. Если в будущем
  анализ покажет, что endpoint не используется — он может быть
  удалён отдельным изменением с проверкой `grep` по логам
  nginx'а и аудитом ссылок.

- **FR-010**: Endpoint ДОЛЖЕН возвращать ответ за **< 100 мс**
  (без учёта сетевой задержки) на типичной нагрузке (10 одновременных
  запросов от ботов, размер БД 18k+ записей). Это включает:
  - один SELECT из `tbl_songs` по `id` (используется существующий
    `Song.loadFromDbById`, ~5–10 мс);
  - один SELECT из `tbl_settings`/`tbl_songs` для дополнительных полей
    (если потребуется, в текущей реализации все нужные поля уже
    присутствуют в `Song`);
  - генерация HTML из строковых полей БД через StringBuilder (~1–5 мс).
  Никаких обращений к MinIO, никакой генерации BufferedImage, никаких
  HTTP-запросов. Это **основная** мотивация фичи: убрать дорогую
  генерацию PNG для ботов.

- **FR-011**: Фича НЕ ДОЛЖНА вводить новых миграций БД. Все
  необходимые данные уже есть в `Song` и связанных сущностях
  (`description`, `shortDescription`, `warning`, `tags`,
  `formattedTextSong`, `formattedTextChords`, `formattedTextTabs`,
  `linkDzenKaraoke`, `linkVkKaraoke`, `linkTgKaraoke` и т.д.).
  Никаких новых колонок, никаких новых таблиц, никаких новых
  записей в `SyncRegistry`.

- **FR-012**: Фича НЕ ДОЛЖНА менять nginx-конфиг (`80to8897`).
  Список User-Agent'ов, по которому nginx редиректит в OG-endpoint,
  остаётся неизменным (см. Pass 35, 2026-08-05, `docs/architecture-notes.md`).
  Изменение списка — отдельная задача.

- **FR-013**: Фича ДОЛЖНА быть совместима с существующей логикой
  рендеринга страницы песни в браузере (SPA `karaoke-public`,
  `SongView.vue`). Никаких изменений в SPA не требуется — обычные
  браузеры продолжают попадать в SPA по правилу nginx.

- **FR-014**: KDoc и комментарии в коде
  `PublicOgSongController.kt` ДОЛЖНЫ быть обновлены:
  - описание endpoint'а должно отражать новую концепцию (SEO-HTML
    вместо «голого» HTML для VK);
  - явное указание, что endpoint больше **не** генерирует PNG
    «на лету» и не вызывает `/api/public/song-vk-image/{id}`;
  - ссылка на эту спецификацию (`specs/180-og-seo-html/`) и на
    per-feature документ (см. FR-015).

- **FR-015**: Должен быть создан или обновлён per-feature документ
  `docs/features/seo-html-for-bots.md` (или дополнен существующий
  раздел, если такой уже есть), описывающий:
  - что делает endpoint и для чего;
  - какие мета-теги включены и почему (Schema.org JSON-LD, OG,
    Twitter Card);
  - какие крайние случаи обрабатываются (SKIP, idStatus<3, нет обложки);
  - список User-Agent'ов в nginx (без изменений, см. FR-012);
  - ссылки на эту спецификацию, на nginx-конфиг, на Song.kt.
  Это требование `constitution.md` (FR-009, Core Principle VI) — при
  правке кода одной из подсистем разработчик должен в том же PR
  обновить соответствующий per-feature документ.

### Key Entities *(include if feature involves data)*

- **OG HTML-ответ** (новый, не сохраняется в БД): transient HTML-документ,
  формируемый на лету из полей `Song`. Включает встроенные
  мета-теги, JSON-LD и видимый semantic-контент. Не кэшируется
  на стороне karaoke-web (на стороне nginx возможен `proxy_cache` —
  но это вне scope данной фичи).

- **Song** (существующая сущность, без изменений): используется
  как источник данных для HTML. Поля, задействованные в HTML:
  `id`, `songName`, `author`, `album`, `year`, `track`, `key`,
  `bpm`, `description`, `shortDescription`, `warning`, `tags`,
  `songType` (song/instrumental/poetry), `ms` (длительность),
  `formattedTextSong`, `formattedTextChords`, `formattedTextTabs`,
  `link*` (все 20 платформенных ссылок), `pictureAlbum` (URL в MinIO
  если есть), `pictureAuthor` (URL в MinIO если есть),
  `free`/`isFreelyAvailableNow` (для `isAccessibleForFree`),
  `idStatus` (для условного вывода контента).

- **Album Cover URL** (вспомогательная сущность): абсолютный URL
  обложки альбома в MinIO, формируется по шаблону
  `https://sm-karaoke.ru/minio/karaoke/{author}/{year} - {album}/{author} -
  {year} - {album}.album.png`. Используется в `og:image`,
  `twitter:image`, JSON-LD `image`. Если файл отсутствует в MinIO —
  fallback на `https://sm-karaoke.ru/KARAOKE_LOGO.png`.

- **KaraokeLogoUrl** (дефолтный логотип): статический URL для случая,
  когда обложка альбома отсутствует. Должен быть согласован с
  текущим логотипом сайта (см. `KARAOKE_LOGO.png` в корне MinIO
  bucket'а `karaoke`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: TTFB (Time To First Byte) endpoint'а
  `GET /api/public/og/song?id=NNN` ДОЛЖЕН быть **< 100 мс** на
  типичной нагрузке (10 одновременных запросов от ботов, размер БД
  18k+ записей, локальный Postgres), что соответствует снижению
  latency в **≥ 5×** по сравнению с текущей реализацией (генерация
  PNG занимает 500–1500 мс по логам прод-сервера).

- **SC-002**: В логах nginx прод-сервера **нет** обращений к
  `/api/public/song-vk-image/{id}` от ботов после включения фичи
  (проверяется через `grep "/api/public/song-vk-image/" access.log` —
  ожидается 0 матчей за период ≥ 7 дней).

- **SC-003**: На тестовой странице Google Search Console (или
  эквивалентном инструменте для Yandex.Вебмастер) **нет** ошибок
  парсинга Schema.org JSON-LD для обходов `/song?id=NNN` после
  включения фичи. Проверяется через «URL Inspection» в Google Search
  Console и «Мониторинг → Диагностика → Микроразметка» в
  Yandex.Вебмастер.

- **SC-004**: HTML-ответ содержит **все обязательные элементы**
  (FR-002): `<title>`, canonical, OG-теги, Twitter Card, JSON-LD,
  `<h1>`, `<h2>`, видимый контент. Проверяется через
  автоматический тест (Kotlin-юнит-тест на сериализацию HTML) и/или
  ручную проверку через `view-source:` в браузере + Google Rich
  Results Test для конкретного URL.

- **SC-005**: Endpoint корректно обрабатывает крайние случаи (FR-006):
  - невалидный id → HTTP 400 без обращения к БД;
  - песня не найдена → HTTP 404 без stack trace;
  - песня с SKIP → HTTP 200 с `noindex,nofollow` и заглушкой;
  - песня с `idStatus < 3` → HTTP 200 без текста/аккордов;
  - песня без обложки → `og:image` указывает на дефолтный логотип.
  Проверяется через набор unit/integration-тестов (Kotlin или
  curl-скрипт в `quickstart.md`).

- **SC-006**: В логах прод-сервера формат строки логирования
  остаётся неизменным:
  `OG render for song id={id}, User-Agent={userAgent}` (FR-008).
  Это позволяет оператору продолжать использовать существующие
  `grep`-команды и dashboards без изменений.

- **SC-007**: Никаких новых миграций БД, никаких новых колонок,
  никаких новых записей в `SyncRegistry` (FR-011). Проверяется
  через `git diff` — изменения только в Kotlin-коде
  `PublicOgSongController.kt` (и, опционально, в unit-тестах).

## Assumptions

- Список User-Agent'ов в `80to8897` (см. FR-012) покрывает все
  актуальные поисковые боты и краулеры соцсетей, которые могут
  обходить страницы песен. Если появится новый бот, который не
  попадает в этот список — он пойдёт в SPA `karaoke-public`
  (приемлемое поведение, см. Pass 35).
- Картинки обложек альбомов в MinIO существуют в формате PNG 400×400
  и хранятся по пути
  `/{author}/{year} - {album}/{author} - {year} - {album}.album.png`
  (см. `Pictures.createNewPicture` в Song.kt:745 и существующий
  endpoint `/api/public/song-picture/{id}` в `PublicApiController.kt:344`).
  Если файл не существует — fallback на `KARAOKE_LOGO.png` (тот же
  путь, что используется в `songVkImage()` при отсутствии картинки
  альбома или автора).
- nginx `proxy_pass http://127.0.0.1:7907;` для обычных браузеров
  остаётся неизменным и не блокирует фичу.
- Endpoint `/api/public/song-vk-image/{id}` **не удаляется** в этой
  версии фичи (см. FR-009) — это сознательное решение для
  обратной совместимости с потенциально кэшированными ссылками.
- Endpoint отдаёт Content-Type `text/html; charset=UTF-8` — это
  гарантирует, что браузеры и боты корректно интерпретируют
  содержимое как HTML.
- Для Schema.org JSON-LD выбран тип `MusicRecording` (более
  специализированный, чем `Song`) — это согласуется с рекомендациями
  schema.org для музыкального контента и Google Rich Results
  (актуально на 2026 год). Если в будущем тип станет deprecated —
  миграция тривиальна (замена `@type`).
- Фича не затрагивает страницу песни в SPA (`SongView.vue`) —
  обычные пользователи видят ту же страницу, что и раньше.
- Тестирование проводится пользователем вручную (через `curl` +
  Google Rich Results Test) или через unit-тесты, написанные в
  рамках фичи (если применимо). Существующие тесты в
  `karaoke-app/src/test` — интеграционные, большинство `@Disabled`,
  см. AGENTS.md «Тесты» — не полагаемся на них как на проверку.
- Документация per-feature (`docs/features/seo-html-for-bots.md`
  или расширение существующего документа про OG-render) обновляется
  в том же PR (FR-009 constitution.md, FR-015).
- Фича не вводит новых properties в `KaraokeProperties` (не нужен
  feature-flag `ogRenderEnabled` для первой версии — см. User Story 4
  P3 как backlog, добавляется только если возникнет реальная
  потребность в быстром выключении).

## Связанные документы

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` — точка изменения.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:344` (`songPicture`), `:407` (`songVkImage`) — связанные endpoint'ы (не меняются, см. FR-009).
- `deploy/web-server-deploy/deploy/80to8897` — nginx-конфиг (НЕ меняется в этой фиче, см. FR-012).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — модель данных (источник полей для HTML).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` — DTO, по образцу которого выбираются поля для публичного API/HTML.
- `docs/architecture-notes.md` (Pass 35, 2026-08-05) — контекст бага с nginx `/song`-location и решением через User-Agent-фильтр.
- `specs/121-vk-news-auto-publish/spec.md` — почему endpoint больше не нужен для сниппетов ВК-постов.
- `specs/130-vk-preview-generation/spec.md` — контекст генерации PNG-картинок и почему она больше не нужна в OG-endpoint.
- `AGENTS.md` секция «Тег SKIP» — как обрабатывать песни с тегом SKIP.
- `.specify/memory/constitution.md` Principle VI (FR-009) — обязательное обновление per-feature документа в том же PR.

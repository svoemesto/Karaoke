# Per-Feature: SEO-HTML endpoint для ботов (вместо генерации PNG «на лету»)

> **Feature Key**: `seo-html-for-bots`
> **Status**: active
> **Slug**: `seo-html-for-bots`
> **Спека**: [specs/180-og-seo-html/spec.md](../../specs/180-og-seo-html/spec.md)
> **План**: [specs/180-og-seo-html/plan.md](../../specs/180-og-seo-html/plan.md)
> **Tasks**: [specs/180-og-seo-html/tasks.md](../../specs/180-og-seo-html/tasks.md)

## Что делает

Endpoint `GET /api/public/og/song?id=NNN` (контроллер
`PublicOgSongController.kt` в модуле `karaoke-web`) возвращает
полноценный **SEO-HTML** вместо «голого» HTML c видимым `<img>`,
который раньше генерировал PNG-картинку «на лету» для сниппетов
ВКонтакте. Endpoint принимает запросы от поисковых ботов и краулеров
соцсетей через nginx `80to8897` (User-Agent фильтр, см. PR
`144-homepage-latest-news`, Pass 35).

Структура ответа:

- **`<head>`** — `<title>`, canonical URL, `<meta name="description">`,
  `<meta name="robots">`, Open Graph (`og:title/description/url/type=music.song/site_name/locale/image/...`),
  Twitter Card (`twitter:card=summary_large_image/title/description/image`),
  Schema.org JSON-LD (`MusicRecording` с `byArtist/inAlbum/datePublished/genre/inLanguage/description/url/image/lyrics/isAccessibleForFree`).
- **`<body>`** — видимый semantic HTML: `<header>` с `<h1>{songName}</h1>`
  и `<h2>{author}</h2>`, секции `#meta` (год, альбом, трек, тональность,
  BPM, жанры, длительность), `#description`, `#lyrics`, `#chords`,
  `#listen` (платформенные ссылки), `<footer>`.

Источник данных — сущность `Song` (`karaoke-app/.../Song.kt`),
вспомогательно — `Pictures` для обложки альбома. Никаких новых таблиц
БД, никаких миграций (FR-011 спеки).

## Зачем

Исторически endpoint проектировался для сниппетов ВКонтакте: видимая
картинка в `<body>` → парсер VK формирует сниппет поста. С момента
реализации автопубликации ВК (см. `specs/121-vk-news-auto-publish`)
подход к постам изменился — посты формируются через VK API с прикреплением
демо-MP4, а парсинг ссылок VK больше не основной канал шаринга. Тем не
менее endpoint остался **единственной точкой входа для поисковых ботов**
(Googlebot/Bingbot/YandexBot) при обходе страниц `/song?id=NNN`.

Ботам нужна **структурированная информация** для индексации, а не
картинка. Генерация PNG 1200×630 c обложкой альбома+автора+названием
через `BufferedImage` занимала 500–1500 мс на запрос (по логам
прод-сервера видны десятки таких запросов в час от `bingbot` и
`YandexBot`). Переход на SEO-HTML даёт:

- **Latency**: TTFB < 100 мс (vs 500–1500 мс).
- **CPU**: < 5 мс CPU time (vs ~50–100 мс на генерацию BufferedImage).
- **Качество индексации**: бот получает полный набор метаданных
  (Schema.org JSON-LD, OG, Twitter Card) + видимый текст песни +
  ссылки на стриминг-платформы.

## Как работает

**Точка изменения**: один файл —
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt`.

**Поток**:

1. nginx (`80to8897`) получает запрос `/song?id=NNN` с User-Agent
   бота (vkShare, TelegramBot, Twitterbot, facebookexternalhit,
   LinkedInBot, WhatsApp, Slackbot, ViberBot, SkypeUriPreview,
   Googlebot, bingbot, YandexBot, YandexImages).
2. nginx редиректит в `/api/public/og/song?id=NNN` (rewrite в
   location `/song`, Pass 35).
3. `PublicOgSongController.ogSongHtml(id, userAgent)` делает:
   - **Один SELECT** из `tbl_songs` по `id` через
     `Song.loadFromDbById()` (~5–10 мс);
   - **Формирует HTML** через `buildSeoHtmlForBots(song)` через
     `StringBuilder` + экранирование через `escape()` / `escapeJsonLd()`
     (~1–5 мс);
   - Возвращает `text/html; charset=UTF-8` с HTTP 200 / 400 / 404.
4. **Никаких обращений к MinIO** из Java: og:image указывает на
   готовый PNG в MinIO через абсолютный URL `https://sm-karaoke.ru/minio/karaoke/{storageFileName}`,
   nginx проксирует `/minio/` напрямую в MinIO (без участия Java).
5. Если обложки альбома нет — fallback на `https://sm-karaoke.ru/KARAOKE_LOGO.png`.
6. Строка лога остаётся неизменной:
   `OG render for song id={id}, User-Agent={userAgent}` (для обратной
   совместимости с dashboards и grep-командами).

**Обработка крайних случаев** (FR-006 спеки):

- `id == null || id <= 0` → HTTP 400 + короткий HTML «Не указан id».
- Песня не найдена → HTTP 404 + короткий HTML «Песня не найдена».
- Тег `SKIP` → HTTP 200 + `<meta name="robots" content="noindex,nofollow">` +
  видимый warning + без текста/аккордов/ссылок на стриминг.
- `idStatus < 3` → HTTP 200 + без секций `#lyrics` и `#chords` (текст
  ещё не верифицирован).
- Нет обложки альбома → `og:image` указывает на `KARAOKE_LOGO.png`.

## Инварианты / правила

1. **Endpoint `/api/public/song-vk-image/{id}` остаётся в коде** (FR-009
   спеки) — для обратной совместимости с потенциально кэшированными
   ссылками. **НЕ вызывается** из нового SEO-HTML endpoint'а.
2. **nginx-конфиг `80to8897` НЕ меняется** (FR-012 спеки). Список
   User-Agent'ов остаётся прежним (см. Pass 35, 2026-08-05,
   `docs/architecture-notes.md`).
3. **Никаких миграций БД** (FR-011 спеки) — все данные берутся из
   существующих полей `Song` и `Pictures`.
4. **Формат строки логирования остаётся неизменным** (FR-008 спеки):
   `OG render for song id={id}, User-Agent={userAgent}`.
5. **HTML escape обязателен** для всех строковых полей из БД (FR-005
   спеки): `&`, `<`, `>`, `"`, `'`. Дополнительно для JSON-LD
   экранируется `\` и непечатные символы.
6. **Размер HTML ≤ 1 МБ** (R7 research.md) — текст песни обрезается с
   маркером `[...фрагмент текста песни усечён для индексации]` при
   превышении.
7. **0 обращений к MinIO из Java** (FR-007 спеки) — все URL картинок
   абсолютные, отдаются nginx'ом через `/minio/`.
8. **KDoc контроллера обновлён** (FR-014 спеки) — отражает новую
   концепцию, ссылается на эту доку и спеку.
9. **Per-feature документ (этот файл) обновлён** (FR-015 спеки) — в том
   же PR, что и код.

## Известные ловушки

- **KDoc лгал, но это пропустили** (Pass 35, см.
  `docs/architecture-notes.md:787`). В `PublicOgSongController.kt` (до
  этой фичи) было написано, что endpoint «не затрагивает обычных
  пользователей, nginx это не затрагивает» — это намерение, а не
  реальное поведение. **Урок**: KDoc должен описывать **факт**, а не
  план. В этой фиче KDoc переписан полностью и теперь отражает
  реальность: SEO-HTML endpoint вызывается ботами через nginx rewrite.
- **Endpoint `/api/public/song-vk-image/{id}` НЕ удалять** — может
  сломать потенциально кэшированные ссылки в VK. Если в будущем
  анализ покажет, что endpoint не используется — удалять отдельным
  PR с предварительным аудитом ссылок через `grep access.log` и
  аудитом VK/Telegram постов.
- **При усложнении списка полей в HTML — обновить KDoc** в этом
  документе (особенно секцию «Инварианты / правила»). Забытое поле =
  потенциальный XSS через HTML-инъекцию, если не экранируется.
- **При добавлении новых User-Agent'ов в nginx** — обновить список в
  KDoc `PublicOgSongController.kt` и в этом документе (см. секцию
  «Как работает» → пункт 1).
- **Schema.org JSON-LD тип может быть deprecate** — если Google/Yandex
  перестанут поддерживать `MusicRecording`, миграция тривиальна
  (замена `@type` + тесты).

## Связанные документы

## Ссылки

- [`PublicOgSongController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt) — реализация endpoint'а.
- [`Song.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt) — источник данных.
- [`SongPublicDto.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt) — образец выбора публичных полей.
- [`80to8897`](../../deploy/web-server-deploy/deploy/80to8897) — nginx-конфиг (НЕ меняется).
- [`docs/architecture-notes.md`](./architecture-notes.md) — Pass 35 и Pass 51.
- [`specs/121-vk-news-auto-publish/spec.md`](../../specs/121-vk-news-auto-publish/spec.md) — контекст ВК-публикаций.
- [`specs/130-vk-preview-generation/spec.md`](../../specs/130-vk-preview-generation/spec.md) — контекст PNG-генерации.
- [`specs/180-og-seo-html/spec.md`](../../specs/180-og-seo-html/spec.md) — функциональные требования.
- [`specs/180-og-seo-html/research.md`](../../specs/180-og-seo-html/research.md) — обоснование технических решений.
- [`specs/180-og-seo-html/data-model.md`](../../specs/180-og-seo-html/data-model.md) — модель данных.
- [`specs/180-og-seo-html/contracts/og-html-endpoint.md`](../../specs/180-og-seo-html/contracts/og-html-endpoint.md) — HTTP-контракт.
- [`specs/180-og-seo-html/quickstart.md`](../../specs/180-og-seo-html/quickstart.md) — сценарии ручной валидации.
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Principle VI (FR-009).
- [`AGENTS.md`](../../AGENTS.md) — CI-gate, жизненный цикл feature-ветки.

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` — реализация endpoint'а.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — источник данных (поля песни).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` — образец выбора публичных полей.
- `deploy/web-server-deploy/deploy/80to8897` — nginx-конфиг (НЕ меняется в этой фиче, см. FR-012).
- `docs/architecture-notes.md` (Pass 35, 2026-08-05) — контекст бага с nginx `/song`-location.
- `docs/architecture-notes.md` (Pass 51, 2026-08-13) — запись о PR `180-og-seo-html`.
- `specs/121-vk-news-auto-publish/spec.md` — почему endpoint больше не нужен для сниппетов ВК-постов.
- `specs/130-vk-preview-generation/spec.md` — контекст генерации PNG-картинок.
- `specs/180-og-seo-html/spec.md` — функциональные требования.
- `specs/180-og-seo-html/research.md` — обоснование технических решений.
- `specs/180-og-seo-html/data-model.md` — модель данных.
- `specs/180-og-seo-html/contracts/og-html-endpoint.md` — HTTP-контракт endpoint'а.
- `specs/180-og-seo-html/quickstart.md` — сценарии ручной валидации.
- `.specify/memory/constitution.md` Principle VI — обязательное обновление per-feature документа (FR-009).
- AGENTS.md — CI-gate для master, жизненный цикл feature-ветки.

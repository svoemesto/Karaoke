---
status: Active
slug: 180-og-seo-html
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
  - ../../specs/180-og-seo-html/spec.md
  - ../../archive/docs/features/seo-html-for-bots.md
---

# 180 — SEO-HTML вместо генерации PNG для ботов (LiveDoc)

> Drill-down — [specs/180-og-seo-html/spec.md](../../specs/180-og-seo-html/spec.md).

## What it does

При обходе `/song?id=NNN` поисковыми/соц-ботами (YandexBot, Googlebot,
bingbot, TelegramBot, VK Share, Facebook, Twitter, Slack, Viber, Skype и др.)
nginx по User-Agent направляет их в `/api/public/og/song?id=NNN`.

**Раньше** этот endpoint возвращал «голый» HTML с `<img src="...song-vk-image/{id}">`,
а `/api/public/song-vk-image/{id}` динамически генерировал PNG 1200×630
(рисуем BufferedImage, читаем из MinIO, ресайзим) — десятки секунд CPU/RAM
на каждый запрос.

**Теперь** возвращается **полноценный SEO-HTML с метаданными**:
- Schema.org JSON-LD `MusicRecording` (полная карточка песни).
- Open Graph: `og:title/description/image/url/type=music.song/site_name/locale=ru_RU`.
- Twitter Card: `summary_large_image` + title/description/image.
- `<link rel="canonical">` на SPA-страницу.
- Видимый semantic-контент: `<h1>` название, `<h2>` автор, текст песни, ссылки на стриминг-платформы.

**TTFB**: с секунд → **< 100 мс** (без генерации изображения).

## User Stories (краткий список)

- **US1** (P1): Бот Yandex/Bing/Google получает полную информацию о песне за миллисекунды (все метаданные, без обращения к MinIO).
- **US2** (P1): Логи прод-сервера НЕ содержат обращений к `/api/public/song-vk-image/{id}` от ботов.
- **US3** (P2): Обычный браузер продолжает попадать в SPA (`karaoke-public`, Vue Router → `SongView`) без изменений.

## Functional Requirements (указатель)

- **FR-001**: Endpoint `/api/public/og/song` возвращает SEO-HTML за < 100 мс.
- **FR-002**: Schema.org JSON-LD `@type: MusicRecording` (или `Song`) с `name/byArtist/inAlbum/datePublished/genre/inLanguage/description/url/image/lyrics`.
- **FR-003**: Open Graph теги `og:title/description/image/url/type=music.song/site_name/locale=ru_RU`.
- **FR-004**: Twitter Card `twitter:card=summary_large_image/title/description/image`.
- **FR-005**: Canonical URL `<link rel="canonical">` на SPA.
- **FR-006**: `<title>` с названием + автором; `<meta name="description">` с кратким описанием.
- **FR-007**: Видимый semantic-контент: `<h1>`, `<h2>`, текст песни, ссылки на стриминг-платформы.
- **FR-008**: Skip-фильтр (песни с тегом `SKIP` НЕ возвращаются).
- **FR-009**: Backward compatibility по пути и User-Agent списку nginx (Pass 35).

## Acceptance Criteria

- [ ] **AC1**: `curl --user-agent "Mozilla/5.0 ... YandexBot/3.0"` → TTFB < 100 мс, `Content-Type: text/html; charset=UTF-8`, есть все метатеги.
- [ ] **AC2**: В логах прод-сервера **нет** запросов к `/api/public/song-vk-image/{id}` от ботов.
- [ ] **AC3**: Обычный Chrome / Firefox → SPA `SongView`, без изменений.
- [ ] **AC4**: Skip-фильтр: песня с тегом `SKIP` → 410 Gone (или эквивалент, без публикации).

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song/Album/Author), [publishing.md](../domain/publishing.md) (visitor→bot segmentation), [identity.md](../domain/identity.md) (OG user)
- Architecture: [L3-components.md](../architecture/L3-components.md) (контроллер + nginx layer)
- Feature: [187-site-traffic-anomaly-investigation.md](../features/187-site-traffic-anomaly-investigation.md) (трафик ботов — контекст для приоритизации)

## Code

- Контроллер: `karaoke-web/src/main/kotlin/.../controllers/PublicOgSongController.kt` — `ogSongHtml()` (rewrite)
- Service: `karaoke-app/src/main/kotlin/.../service/SeoSongService.kt` (новый)
- DTO: `SongSeoData.kt`, `SongJsonLd.kt`, `OpenGraphData.kt`
- Nginx: `deploy/web-server-deploy/deploy/80to8897` (location `/song` — без изменений User-Agent списка)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14
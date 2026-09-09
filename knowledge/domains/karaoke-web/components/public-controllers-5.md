# Public controllers: Og/History/News/Chat/Typograph (мелкие)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: обзор 5 мелких Public-контроллеров.

## Файлы

| Store | Файл | Строк | Endpoints |
|---|---|---|---|
| `PublicOgSongController` | `webvue3/.../PublicOgSongController.kt` | 539 | 1 |
| `PublicHistoryController` | `webvue3/.../PublicHistoryController.kt` | 35 | 1 |
| `PublicNewsController` | `webvue3/.../PublicNewsController.kt` | 93 | 2 |
| `PublicChatController` | `webvue3/.../PublicChatController.kt` | 119 | 3 |
| `PublicTypographController` | `webvue3/.../PublicTypographController.kt` | 52 | 1 |

## `PublicOgSongController` (1 endpoint, 539 строк — **огромный!**)

`/api/public/og-song/...` — **Open Graph** теги для шеринга
песни в соцсетях. Генерирует OG-meta (title, image, description)
динамически.

## `PublicHistoryController` (1 endpoint, 35 строк)

`/api/public/history` — POST для регистрации прослушивания (см.
[ListeningHistory](../../domains/catalog/components/entities-catalog.md#listeninghistory)).

## `PublicNewsController` (2 endpoints, 93 строки)

`/api/public/news/*`:
- `GET /since` — **polling** непрочитанных (TTL=60s, см.
  [composable-news-unread.md](../../../system/frontend/composable-news-unread.md)).
- `GET /{id}` — одна новость.

## `PublicChatController` (3 endpoints, 119 строк)

`/api/public/account/chat/*` — **требует** auth (через
`SiteAuthInterceptor`):
- `GET /threads` — список тредов.
- `GET /messages/{threadId}` — сообщения.
- `POST /send` — отправить.

См. [unreadchatmessagescheck](../../monitoring/components/monitor-checks-detailed.md).

## `PublicTypographController` (1 endpoint, 52 строки)

`/api/public/typograph` — утилита для типографики (см.
[TypographUtils.kt](../../../system/utilities.md)). Используется
`PublicTypographController` для нормализации текста на сервере.

## Связь

- [public-controllers.md](public-controllers.md) — обзор всех 19.
- [composable-news-unread.md](../../../system/frontend/composable-news-unread.md) — consumer.

## Changelog

- **Pass 481-485** (2026-09-09): Initial. Автор: agent (Karaoke).
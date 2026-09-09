# Public controllers: PublicApiController (12 endpoints)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `PublicApiController` — главный public API контроллер.

## Файл

`karaoke-web/.../controllers/PublicApiController.kt` (**1150 строк**,
самый длинный public-контроллер)

## Endpoints (12)

| # | URL | Метод | Назначение |
|---|---|---|---|
| 1 | `/api/public/stats` | GET | Сводная статистика (см. [store-stats.md](../../../system/frontend/store-stats.md)) |
| 2 | `/api/public/authors` | GET | Список авторов (дайджест) |
| 3 | `/api/public/authors-tiles` | GET | Тайлы авторов для главной |
| 4 | `/api/public/zakroma` | GET | Закрома автора (страница) |
| 5 | `/api/public/zakroma/stream` | GET (NDJSON) | **Streaming** Закромов (см. [composable-zakroma-stream.md](../../../system/frontend/composable-zakroma-stream.md)) |
| 6 | `/api/public/zakroma/stream/metrics` | POST | Метрики стрима (FR-FE-010) |
| 7 | `/api/public/songs` | GET | Список песен (с пагинацией) |
| 8 | `/api/public/song/{id}` | GET | Одна песня (см. [song-public-dto.md](../../integration/components/song-public-dto.md)) |
| 9 | `/api/public/events` | POST | Регистрация события (см. [composable-engagement-tracking.md](../../../system/frontend/composable-engagement-tracking.md)) |
| 10 | `/api/public/song-picture/{id}` | GET | URL обложки песни |
| 11 | `/api/public/song-vk-image/{id}` | GET | URL превью VK |
| 12 | `/api/public/picture` | GET | URL обложки автора/альбома |

## Hot paths

- **`/api/public/songs`** — каждое открытие главной.
- **`/api/public/song/{id}`** — каждое открытие страницы песни.
- **`/api/public/zakroma/stream`** — каждое открытие Закромов (chunked).
- **`/api/public/events`** — каждое событие (с Sampling).

## Архитектурные решения

### Решение 1: NDJSON streaming для zakroma

`/api/public/zakroma/stream` использует `produces = "application/x-ndjson"`
(см. [composable-zakroma-stream.md](../../../system/frontend/composable-zakroma-stream.md)) —
real-time progress (FR-FE-001..011).

### Решение 2: rate limiting

`/api/public/song-picture/{id}` и `/api/public/song-vk-image/{id}` —
rate limit 60/min per IP (через `RateLimitInterceptor`, см.
[config.md](config.md)).

### Решение 3: events через Sampling

`/api/public/events` — все события проходят через
`SamplingFilter` (см. [composable-engagement-tracking.md](../../../system/frontend/composable-engagement-tracking.md))
— 1/N sampling.

## Связь

- [public-controllers.md](public-controllers.md) — обзор всех 19.
- [storage domain](../../storage/domain.md) — картинки.
- [sse domain](../../sse/domain.md) — не подключен напрямую
  (SSE через karaoke-app).

## Известные TODO

- [ ] **Каждый endpoint** — детальный contract (Pass 343+).
- [ ] **Кеширование** — `Cache-Control` headers (Pass 343+).

## Changelog

- **Pass 475** (2026-09-09): Initial. Автор: agent (Karaoke).
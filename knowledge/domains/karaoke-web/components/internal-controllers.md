# Component: internal-controllers

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный обзор 6 non-Public контроллеров (admin/internal/debug).

## Назначение

`karaoke-web/.../controllers/{Main,Internal*,Debug*,SiteShareLinks*,WebSocket*}` —
**непубличные** контроллеры. Thymeleaf HTML для статических страниц,
internal endpoints (admin→web), debug endpoints, share-линки.

## Каталог

| # | Контроллер | Markers | URL prefix | Назначение |
|---|---|---|---|---|
| 1 | `MainController` | 23 | `/`, `/zakroma`, `/login`, etc. | Thymeleaf HTML (статические страницы) |
| 2 | `InternalStatsController` | 2 | `/api/internal/stats/...` | Внутренняя статистика |
| 3 | `InternalStemJobController` | 3 | `/api/internal/stem-jobs/...` | Скачивание оригинала для StemJob |
| 4 | `DebugDbController` | 2 | `/api/public/debug/db` | Debug DB (защищённый) |
| 5 | `SiteShareLinksController` | 4 | `/api/share-links/...` | Управление share-линками (admin) |
| 6 | `WebSocketConfig` | 0 | `/ws` | WebSocket/SSE endpoint config |

## Детальные контракты

### `MainController` (Thymeleaf, 23 endpoints)

**Файл**: `karaoke-web/.../controllers/MainController.kt`.

**Endpoints** (по grep `@GetMapping`/`@PostMapping`):

- Thymeleaf-страницы (HTML рендеринг): `/`, `/zakroma`, `/login`,
  `/player/{id}`, `/news/{id}`, `/playlists/{id}`, etc.
- API endpoints для них (JSON): `/api/web/...`.

**Архитектура**: Thymeleaf-шаблоны в `templates/`, контроллер
принимает `Model` и возвращает HTML.

**Dependencies** (по imports):

- `StorageApiClient` (для read MinIO).
- `SamplingFilter` (для WebEvent analytics).
- `EventsBuffer` (FR-109: batch INSERT в tbl_events, kill-switch
  `karaoke.web.events.batch-enabled` default false).
- `SiteUserResolver` (для /zakroma с canWorkWithSkipped).
- `SimpMessagingTemplate` (WebSocket — NB: `WEBSOCKET` lateinit).

**Логика**:

- `RestName` enum — endpoints регистрируются.
- `EventType` enum — типы событий.
- `LinkType` enum — типы ссылок.
- `PlayerAction` enum — действия игрока.
- `Crypto` используется для шифрования SQL (security issue см.
  [utilities.md](../../../system/utilities.md)).

### `InternalStatsController`

**Endpoints** (по grep):

- `/api/internal/stats/...` — внутренняя статистика для admin.

### `InternalStemJobController`

**Endpoints** (по grep):

- `/api/internal/stem-jobs/{id}/download-original` — скачивание
  оригинала для StemJob обработки. **Внутренний** — только
  karaoke-app может вызывать (через nginx allowlist).

**Логика**: см. [storage-flow.md](../../storage/components/storage-flow.md)
+ [stem-job.md](../../../catalog/components/remaining-models.md#stemjob).

### `DebugDbController`

**Endpoints**:

- `/api/public/debug/db` — execute SQL для отладки.
- `/api/public/debug/db/status` — статус.

**Защита** (FR-006 из site-traffic-resilience):

- `DebugDbAccessGuard` — IP allowlist.
- `KARAOKE_WEB_DEBUG_DB_ENABLED` — master switch (default `false`).
- `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` — comma-separated CIDR/IPv4.

**NB**: дефолт `enabled=false` = endpoint выключен. Включать
только на dev-стенде.

### `SiteShareLinksController`

**Endpoints** (по grep):

- `/api/share-links/...` — управление share-линками (admin).

### `WebSocketConfig`

WebSocket/SSE endpoint config. **Без endpoints** (только config).

**NB**: SSE на самом деле реализован не через WebSocket, а через
`SseEmitter` (см. [sse domain](../../sse/domain.md)). Этот класс
настраивает `WebSocket` для других целей (возможно, чат или
push-уведомления).

## Архитектурные решения

### Решение 1: Thymeleaf для HTML, REST для JSON

`MainController` рендерит HTML через Thymeleaf шаблоны. REST
endpoints возвращают JSON. Гибрид — для статических страниц
(SEO/SSR) и для динамических SPA-вызовов.

### Решение 2: Internal endpoints защищены nginx

`/api/internal/*` — только для karaoke-app. На проде nginx allowlist
по IP (например, только `127.0.0.1` или IP admin-машины).

### Решение 3: DebugDbController — fail-safe

`KARAOKE_WEB_DEBUG_DB_ENABLED=false` по умолчанию. Нельзя случайно
включить на проде.

## Связь с другими компонентами

- **SSE** ([sse domain](../../sse/domain.md)) — `WebSocketConfig`.
- **Storage** ([storage domain](../../storage/domain.md)) — read-only
  MinIO через `StorageApiClientWeb`.
- **Schedulers** ([schedulers.md](../../processing/components/schedulers.md)) —
  `EventsRetentionScheduler`, `ShareLinkSweeper`, etc.

## Известные TODO

- [ ] **Каждый endpoint** — полный request/response (Pass 343+).
- [ ] **Thymeleaf шаблоны** — какие есть, где определены.
- [ ] **`WEBSOCKET` lateinit** — где инициализируется.
- [ ] **`EventsBuffer`** — детальный flow batch INSERT.
- [ ] **`SamplingFilter`** — детальный алгоритм (Pass 343+).
- [ ] **`SiteUserResolver`** — полная логика.

## Changelog

- **Pass 364** (2026-09-09): Initial. Автор: agent (Karaoke).
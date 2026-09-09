# Component: MainController (karaoke-web Thymeleaf)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `MainController` — Thymeleaf-страницы + internal
> endpoints.

## Файл

`karaoke-web/.../controllers/MainController.kt` (~500+ строк)

## Назначение

**Thymeleaf HTML-страницы** (статические HTML) + **internal JSON
endpoints** (для server-to-server между karaoke-app и karaoke-web).

## Endpoints (Thymeleaf HTML)

| URL | Что |
|---|---|
| `GET /` | Главная |
| `GET /zakroma` | Закрома автора |
| `GET /testpage/{id}` | Тестовая страница (Pass 343+) |
| `GET /filter` | Фильтр |
| `GET /song` | Песня (HTML view) |
| `GET /statbysong` | Счётчики (StatBySong) |
| `GET /webevents` | События |

## Endpoints (JSON, internal)

| URL | Что |
|---|---|
| `POST /registerevent` | Регистрация события (с ClientIp + tracking) |
| `POST /changerecords` | Приём изменений two-DB sync (зашифрованный SQL через `Crypto.encrypt`) |
| `GET /api/internal/stem-jobs/{id}/download-original` | См. [internal-stem-job-controller.md](internal-stem-job-controller.md) |

## Логика

- **Импорты**: `KaraokeStorageService`, `StorageApiClient`,
  `SongReleaseAnnouncementService`, `SamplingFilter`,
  `SiteUserResolver`.
- **Enums**: `EventType`, `LinkType`, `PlayerAction`, `RestName`.
- **Crypto**: `Crypto.encrypt(sql)` для `/changerecords` (теперь
  через env, см. [system/utilities.md](../../../system/utilities.md)).

## Hot paths

- **Каждое открытие** страницы сайта = `/` или `/zakroma` =
  Thymeleaf-рендеринг.
- **Каждое событие** = `POST /registerevent` (через `SamplingFilter`).
- **Two-DB sync** = `POST /changerecords` (зашифрованный SQL).

## Архитектурные решения

### Решение 1: Thymeleaf + REST в одном контроллере

`MainController` — **гибрид** (Thymeleaf HTML для страниц + JSON
endpoints для internal). Разделение — по `produces` и типу
возврата.

### Решение 2: Sampling защита

Каждое событие проходит через `SamplingFilter` (см.
[composable-engagement-tracking.md](../../../system/frontend/composable-engagement-tracking.md)) —
1/N sampling для web-аналитики (см.
[monitoring/log-categories.md](../../monitoring/components/log-categories.md)).

### Решение 3: WebSocket + SSE

`SimpMessagingTemplate` — для SSE-push (см.
[sse domain](../../sse/domain.md)). ВThymeleaf-страницах
используется для real-time обновлений (например, новые сообщения в
чате).

## Связь

- [internal-controllers.md](internal-controllers.md) — общий обзор.
- [services-overview.md](services-overview.md) — сервисы.
- [public-controllers.md](public-controllers.md) — публичные API.

## Известные TODO

- [ ] **Каждый endpoint** — детальный contract (Pass 343+).
- [ ] **Thymeleaf-шаблоны** — где определены (Pass 343+).
- [ ] **`/registerevent` schema** — какие поля, валидация.
- [ ] **`/changerecords` security** — только internal IP, защита
      nginx'ом.

## Changelog

- **Pass 436-438** (2026-09-09): Initial. Автор: agent (Karaoke).
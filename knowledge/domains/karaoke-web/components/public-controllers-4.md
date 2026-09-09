# Public controllers: Player (8 endpoints)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `PublicPlayerController` — endpoints плеера.

## Файл

`karaoke-web/.../controllers/PublicPlayerController.kt` (589 строк)

## Endpoints (8)

| # | URL | Метод | Назначение |
|---|---|---|---|
| 1 | `/api/public/player/{id}/access` | GET | Проверка доступа (premium) |
| 2 | `/api/public/player/readiness` | POST | Готовность плеера (массовая) |
| 3 | `/api/public/player/{id}/fileminus.mp3` | GET | Стрим минусовки |
| 4 | `/api/public/player/{id}/filevoice.mp3` | GET | Стрим вокала |
| 5 | `/api/public/player/{id}/filebass.mp3` | GET | Стрим баса |
| 6 | `/api/public/player/{id}/filedrums.mp3` | GET | Стрим ударных |
| 7 | `/api/public/player/{id}/playerdata` | GET | JSON playerdata |
| 8 | `/api/public/player/{id}/playerfile` | GET | Полный playerfile (playerdata + zip) |

## Hot paths

- **`/{id}/fileminus.mp3`** — каждое воспроизведение песни.
- **`/{id}/access`** — каждое открытие страницы песни (проверка premium).
- **`/readiness`** — каждые 5 минут из Закромов.

## Архитектурные решения

### Решение 1: stream через nginx-proxy

Файлы (`.mp3`) стримятся через nginx path-proxy (см.
[storage-flow.md](../../storage/components/storage-flow.md))
— **НЕ** через `KaraokeStorageService` или `StorageApiClientWeb`.

### Решение 2: `/access` для premium проверки

Возвращает JSON `{canWatch, canExport, isPremiumUser, isDemo}` — для
UI «золотой/серебряной монетки» (см.
[composable-use-player-access.md](../../../system/frontend/composable-use-player-access.md)).

### Решение 3: `/readiness` для батч-проверки

`/readiness` принимает **массив id** песен, возвращает их
готовность. Используется `usePlayerReadiness` (Pass 372) для
chunked-проверки (chunk=20, MAX_CONCURRENT=3).

## Связь

- [public-controllers.md](public-controllers.md) — обзор.
- [storage-flow.md](../../storage/components/storage-flow.md) — stream via nginx.

## Известные TODO

- [ ] **Каждый endpoint** — детальный contract (Pass 343+).

## Changelog

- **Pass 476** (2026-09-09): Initial. Автор: agent (Karaoke).
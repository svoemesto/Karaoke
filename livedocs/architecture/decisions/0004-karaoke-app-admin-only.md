# ADR-0004: KaraokeApp — только на admin-машине, не на проде

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, фундаментальное; см. Constitution § «Деплой-окружения»)
* **Deciders**: команда Karaoke

## Context

Проект Karaoke состоит из нескольких контейнеров (см. [L2-containers.md](../L2-containers.md)):

- `karaoke-web` — на **проде** (8090, behind nginx).
- `karaoke-public`, `webvue3` — на **проде** (Vue SPA статика).
- `karaoke-app` — где?

Архитектурное решение: **`karaoke-app` НЕ разворачивается на проде**.
Только на admin-машине. Это неочевидное для новых разработчиков решение.

## Decision

**`karaoke-app` разворачивается только на admin-машине**, не на проде.

На проде используется ТОЛЬКО `karaoke-web` — он предоставляет все
публичные HTTP-endpoint'ы, но **не запускает тяжёлые задачи** (рендеринг
MLT, Demucs, Sheetsage, Telegram-бот, sync LOCAL↔SERVER — всё это делается
на admin-машине, где есть `karaoke-app`).

## Что работает на admin-машине (с `karaoke-app`)

- Очередь задач `KaraokeProcessWorker` + lanes
  (HEAVY_RENDER, LIGHT_BACKGROUND, REMOTE_STORE_UPLOAD, STEM_JOBS).
- MLT/melt + Playwright + ffmpeg рендеринг → MP4 в MinIO.
- Demucs (стем-сепарация) и Sheetsage (key/BPM/chords) — Python в Docker.
- `pgsql` → sync LOCAL ↔ SERVER PROD.
- Telegram-бот / VK авто-публикация.
- Lyrics search engines (Ollama + SearXNG).
- `setWebvueProp` server-side кэш (для webvue3).

## Что работает на проде (только `karaoke-web`)

- REST API для публичных страниц (`/api/public/...`).
- REST API для webvue3 (`/api/admin/...`, `/api/editor/...`).
- Thymeleaf-страницы (legacy).
- Прокси и nginx-обвязка (`80to8897`).
- Подключение к `tbl_*` БД на проде — через `KaraokeConnection.Target.REMOTE`.

## Почему `karaoke-app` НЕ на проде

1. **Тяжёлые зависимости**. MLT/melt + Playwright + Python + ML-модели (1-2 ГБ).
   Контейнер тяжёлый, требует GPU/CPU, занимает диск. На проде это не нужно
   (рендеринг делается на admin-машине, результат → MinIO → прод).
2. **Webhook'и и long-running jobs**. Karaoke-app работает как orchestrator
   для многих async-операций (рендеры, демксы, Telegram-bot). На проде это
   лишний шум в логах публичного сайта.
3. **KaraokeProperties содержит секреты** (Telegram tokens, OAuth secrets,
   и т.п.) — на проде они не нужны, но в образе Karaoke-app они определены.
4. **Безопасность**. Чем меньше контейнеров на проде — тем меньше attack
   surface. Karaoke-app не нужен для публичного HTTP.

## Контракт: что доступно на проде

`karaoke-web` (на проде) импортирует модули из `karaoke-app` как JAR
(`./gradlew clean karaoke-app:bootJar`). То есть **тот же код** доступен
на проде (через `karaoke-web` fat jar), но **не запускается** как процесс.

Что это значит:
- `KaraokeDbTable`, `KaraokeConnection` — на проде работают (через `karaoke-web`).
- `@Scheduled` методы (например, `SongAirScheduler`) — НЕ работают на проде
  (нет `karaoke-app` бина).
- `KaraokeProcessWorker.doStart()` — НЕ работает на проде.

Это **намеренно** — sync триггеры, рендер-триггеры должны быть на admin-машине.

## Исключения (Pass 21+)

- На машине с hostname `dev-pc` под пользователем `dev` агенту **разрешено**
  пересобирать/перезапускать `karaoke-app` (см. Constitution § «Ограничения
  и доступы агента»). Это для удобства разработки.

## Деплой-окружения

| Окружение | Хост | Контейнеры |
|-----------|------|-------------|
| **Local DB** | `dev-pc` admin-машина | `karaoke-db` |
| **Local KaraokeApp** | `dev-pc` admin-машина | `karaoke-app` + `minio` |
| **Local KaraokeWeb** | `dev-pc` admin-машина | `karaoke-web` + `nginx` (development) |
| **PROD** | prod-сервер `<PROD_HOST>` | `karaoke-web` + `nginx` (80to8897) |
| **PROD БД** | prod-сервер `<PROD_HOST>` | `karaoke-db` |

## Что НЕ работает на проде (важно!)

- ❌ `KaraokeProperties.vkUserAccessToken`, `telegramBotToken` (не читаются на проде — см. ADR-0001).
- ❌ `tbl_public_settings.kill_switch` — читается на проде через `CaptchaConfigService`-подобный сервис (см. `005-news-flags-backfix`).
- ❌ Sync LOCAL → SERVER через UI (только через ручной bash).
- ❌ Service `KaraokeProcessWorker` / async очередь.

## Alternatives Considered

- **`karaoke-app` на проде**: утяжелит образ, потребует ML-зависимостей на
  проде, лишний attack surface.
- **`karaoke-app` и `karaoke-web` как один процесс**: смешивает ответственности.
  Уже использовали такой подход до Phase 001, выяснилось, что это создаёт
  сложности с диагностикой (что упало — web или app?).
- **Лёгкий `karaoke-app` без async**: урезать функциональность. Не делали —
  два бина одного проекта, просто `_app` бина не загружается на проде
  через `ComponentScan`.

## Ссылки

- [Constitution § «Деплой-окружения»](.specify/memory/constitution.md).
- [L2-containers.md](../L2-containers.md) — где что работает.
- [`docs/deployment-notes.md`](../../../docs/deployment-notes.md) — конкретные
  команды deploy.
- ADR-0005 — следующий ADR объясняет, как связан с self-hosted ML.
# DEVELOPMENT.md

> **Status**: active
> **Last Updated**: 2026-07-21 (PR #24, 012-development-md-rewrite)

## What this is

"Karaoke" (svoemesto) is a self-hosted pipeline that automates production of karaoke videos:
it finds/fetches song audio, lyrics, chords and metadata, runs audio analysis and source
separation, generates MLT (the `melt`/MLT multimedia framework) project files describing the
on-screen karaoke composition (scrolling lyrics, chords, fingerboard, watermark, counters,
splash screen, etc.), renders the videos via external CLI tools, and publishes them.
A Vue 3 admin SPA drives the whole pipeline against a Kotlin/Spring Boot backend.

## Где что

| Что | Где |
|-----|-----|
| Правила оформления кода (MUST/SHOULD) | [CONTRIBUTING.md](./CONTRIBUTING.md) |
| Описание 9 ключевых подсистем | [docs/features/](./docs/features/README.md) |
| KDoc/JSDoc инструкция | [docs/api/README.md](./docs/api/README.md) |
| Непреложные принципы | [.specify/memory/constitution.md](./.specify/memory/constitution.md) |
| Деплой на прод | [docs/deployment.md](./docs/deployment.md) |
| Публичные модули (karaoke-public, плеер, аккаунт) | [docs/public-modules.md](./docs/public-modules.md) |
| `tbl_public_settings`, миграции | [docs/database.md](./docs/database.md) |
| Ключевые инварианты (ловушки) | [docs/invariants.md](./docs/invariants.md) |
| Датированная история фич и «ловушек» | [docs/architecture-notes-archive.md](./docs/architecture-notes-archive.md) |

## Modules / layout

- `karaoke-app` — the core engine. Spring Boot (Kotlin) app containing almost all domain logic:
  DB models, the MLT project generator, the async job-processing queue, AI/web-search lyrics
  finder, browser automation, storage client, OAuth2 authorization server. Everything else depends
  on it. **Runs only on the local admin machine — never deployed to the production server**.
  Anything that must also work in production (settings read by `karaoke-web`, etc.) cannot rely on
  an HTTP call to `karaoke-app`; it needs to live in Postgres instead (see
  [docs/database.md](./docs/database.md)).
- `karaoke-web` — thin Spring Boot module (`implementation(project(":karaoke-app"))`) exposing the
  public-facing website endpoints (song pages, stats, web events) and a websocket config. Reuses
  `karaoke-app`'s DB models and services directly. **Deployed to prod** ([docs/deployment.md](./docs/deployment.md)).
- `karaoke-db` — standalone plain-Java Gradle module containing only `Main.java`; not wired into the
  app's data access (which talks to Postgres directly via raw JDBC from `karaoke-app`). Legacy/
  placeholder — don't assume it's load-bearing.
- `karaoke-vue` — legacy Vue app, abandoned; only `src/assets` remain, no real source. Not part
  of the build.
- `webvue3` — the current admin frontend: Vue 3 + Vite, Bootstrap-vue-next, Vuex. This is what's
  actively developed for the UI today.
- `karaoke-public` — the current public-facing SPA (Vue 3 + Vite, Bootstrap 5), replacing the
  legacy Thymeleaf templates of `karaoke-web`. Deployed to prod.
- `deploy/` — docker-compose files (one per service: app, web, db, webvue3, storage/MinIO, ollama,
  searxng) plus `do.sh`, the build/deploy orchestration script, and server setup notes.
- Root `pom.xml` is a leftover Maven file, unused — the project builds with Gradle.

## Build & run

Backend (Kotlin/Spring Boot, multi-module Gradle, JDK 17):
```
./gradlew karaoke-app:bootJar                                       # build the core engine jar
./gradlew karaoke-web:bootJar                                       # build the public web module jar
./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel   # what deploy/do.sh uses
./gradlew karaoke-app:test                                          # run JUnit tests for a module
./gradlew karaoke-app:test --tests "com.svoemesto.karaokeapp.SomeTest"  # run a single test
```

Note: the existing tests under `karaoke-app/src/test` (`SearchLastAlbumVkTest`, `PlaywrightTests`)
are manual integration checks that hit live network services / require a real browser and
credentials — not a CI safe-guard suite (mostly `@Disabled` or require env setup).

Frontend (`webvue3`, Vue 3 + Vite, Node):
```
cd webvue3 && npm install && npm run dev    # dev server; npm run build / npm run preview
```

`karaoke-public` собирается через Docker (см. do.sh ниже) или локально
`nvm use v25.7.0 && npm run build`. No lint/test script is configured in either frontend's
`package.json`.

Local dev / deployment (`deploy/do.sh`, a single entrypoint wrapping gradle + docker compose).
**Важно: все команды do.sh запускать из директории `deploy/` или передавать полный путь.**
```
cd deploy
bash do.sh build                # gradle build jars + build all docker images
bash do.sh build_app            # build only the karaoke-app image
bash do.sh build_web            # build only the karaoke-web image
bash do.sh build_start_public   # rebuild karaoke-public (Vue→nginx) and (re)start (порт 7907)
bash do.sh build_webvue3        # rebuild webvue3 image
bash do.sh build_demucs / push_demucs
bash do.sh start [all] / stop [all] / start_db / stop_db / push / pull / ps
```

`deploy/do.env` / `deploy/.env` hold the environment (ports, registry, host folder mounts).

**Подробности о деплое** — [docs/deployment.md](./docs/deployment.md).

## Architecture notes (durable-карта)

> Здесь — **краткий** durable-обзор: как устроены ключевые подсистемы. **Датированная история
> и детали ловушек** — в [docs/architecture-notes-archive.md](./docs/architecture-notes-archive.md).

**Custom DB layer, no JPA/Hibernate.** Каждая персистентная сущность реализует `KaraokeDbTable`
(`model/KaraokeDbTable.kt`): plain Kotlin data class + raw JDBC (`Connection`/`KaraokeConnection`);
на `save()` reflection диффит in-memory состояние против строки в Postgres. Полученный
field-level дифф (`RecordDiff`) превращается в `UPDATE .. SET` и одновременно рассылается как
`SseNotification.recordChange` — так admin-UI (`webvue3`, подписан по SSE/websocket) видит
live-изменения без polling. Этот же recordhash/diff-механизм (`deploy/recordhash_*.sql`)
синхронизирует LOCAL и SERVER БД. **Подробности** — [docs/features/dual-db-sync.md](./docs/features/dual-db-sync.md).

**SSE-уведомления (`SseNotificationService`).** Один эндпоинт `GET /api/subscribe` (чистый
`EventSource`/`EventSourcePolyfill`, **не** SockJS/STOMP). Подписка ключуется
`UserKey(userId, tabId)`. `send()` различает broadcast и адресную доставку. Heartbeat —
независимый `@Scheduled(fixedRate = 15_000)`. **Подробности** —
[docs/features/sse-notifications.md](./docs/features/sse-notifications.md).

**Dual database targets.** `Connection.kt` даёт фабрики `local()`/`remote()`/`virtual()`. Per-request
`Connection.local()/remote()` **обязательно** закрывать (обёртка `withDb` в контроллерах
с `target=local|remote`) — иначе утечка соединений.

**Async job pipeline (`KaraokeProcess*`).** Тяжёлая работа (ffmpeg/`melt`-рендер, Demucs,
Sheetsage key/BPM/chords, copy/symlink) моделируется строкой `KaraokeProcess` с enum
`KaraokeProcessTypes`, исполняется `KaraokeProcessWorker`/`KaraokeProcessThread` как
OS-подпроцесс (`ProcessBuilder`). Задания имеют приоритет и **thread-лейны** (`threadId`
группирует в независимые последовательные очереди). **Подробности** —
[docs/features/async-process-queue.md](./docs/features/async-process-queue.md).

**MLT video generation (пакет `mlt/`).** Сборка видео = генерация MLT XML-проекта
(для `melt` CLI). `MltGenerator`/`MltProp`/`MltPropBuilder` строят named producers/tractors/
filters; `mlt/mko/*` («Mlt Karaoke Object») — билдеры визуальных слоёв (текст, аккорды, гриф,
ноты, счётчики, горизонт, watermark, фон). `KaraokeProperties.kt` держит ~150 настраиваемых
параметров рендера. **Подробности** — [docs/features/mlt-generator.md](./docs/features/mlt-generator.md).

**Премиум-фича «Создать минусовку» (`StemJob`, `tbl_stem_jobs`).** Пользователь karaoke-public
загружает произвольный аудиофайл и получает исходник + стемы (demucs2/demucs5) — переиспользует
движок выше (свой thread-лейн `THREAD_LANE_STEM_JOBS`), но задание живёт целиком на PROD-БД (не в
`SyncRegistry`) и не привязано к `Settings`. **Подробности** — [docs/features/premium-stems.md](./docs/features/premium-stems.md).

**Storage (MinIO).** Сгенерированная медиа (аудио-стемы, видео, картинки) живёт в
MinIO-совместимом объектном хранилище (`services/StorageApiClient.kt` /
`KaraokeStorageService.kt`). **Размеры картинок:** альбом 400×400 + превью 50×50;
автор 1000×400 + превью 125×50. Base64 в `tbl_pictures` не используется — `picture_full`
всегда `""`, картинки только в MinIO (`PicturesDTO` несёт `previewUrl`/`fullUrl`).

**Frontend ↔ backend wiring.** `webvue3` — Vuex-modules-per-entity SPA поверх REST `karaoke-app`
(`controllers/ApiController.kt` ~3400 строк). Live-обновления — SSE. `webvue3` (admin) **не
гейтится авторизацией** — `SecurityConfig.kt` пускает всё (`permitAll()`, кроме
`/api/private/**`); отдельного admin-логина в проекте больше нет. **Публичные модули
(karaoke-public, плеер, аккаунт)** — [docs/public-modules.md](./docs/public-modules.md).

**Ловушки и инварианты** — [docs/invariants.md](./docs/invariants.md).

## Git — что НЕ добавлять в репозиторий

- `deploy/ollama_data/` — SSH-ключи (`id_ed25519`) и большие модели Ollama. Уже в `.gitignore`.
- `karaoke-public/dist/`, `webvue3/dist/` — артефакты сборки (`dist/`). Уже в `.gitignore`.
- `karaoke-public/node_modules/`, `webvue3/node_modules/`. Уже в `.gitignore`.
- `deploy/.env`, `deploy/do.env` — секреты (пароли, порты). Уже в `.gitignore`.

При коммите всегда проверяй `git status` перед `git add` — особенно `ollama_data/`, `dist/`,
`node_modules/` и любые файлы с ключами/паролями.

## См. также

- [docs/features/](./docs/features/README.md) — 9 ключевых подсистем
- [docs/architecture-notes-archive.md](./docs/architecture-notes-archive.md) — dated-история
- [.specify/memory/constitution.md](./.specify/memory/constitution.md) — принципы

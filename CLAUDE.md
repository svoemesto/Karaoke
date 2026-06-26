# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Karaoke" (svoemesto) is a self-hosted pipeline that automates production of karaoke videos: it finds/fetches song
audio, lyrics, chords and metadata, runs audio analysis and source separation, generates MLT (the `melt`/MLT
multimedia framework) project files describing the on-screen karaoke composition (scrolling lyrics, chords,
fingerboard, watermark, counters, splash screen, etc.), renders the videos via external CLI tools, and publishes
them. A Vue 3 admin SPA drives the whole pipeline against a Kotlin/Spring Boot backend.

## Modules / layout

- `karaoke-app` — the core engine. Spring Boot (Kotlin) app containing almost all domain logic: DB models, the
  MLT project generator, the async job-processing queue, AI/web-search lyrics finder, browser automation, storage
  client, OAuth2 authorization server. Everything else depends on it.
- `karaoke-web` — thin Spring Boot module (`implementation(project(":karaoke-app"))`) exposing the public-facing
  website endpoints (song pages, stats, web events) and a websocket config. Reuses `karaoke-app`'s DB models and
  services directly.
- `karaoke-db` — standalone plain-Java Gradle module containing only `Main.java`; not wired into the app's data
  access (which talks to Postgres directly via raw JDBC from `karaoke-app`). Legacy/placeholder — don't assume it's
  load-bearing.
- `karaoke-vue` — legacy Vue app, abandoned; only `src/assets` remain, no real source. Not part of the build.
- `webvue3` — the current admin frontend: Vue 3 + Vite, Bootstrap-vue-next, Vuex. This is what's actively
  developed for the UI today.
- `deploy/` — docker-compose files (one per service: app, web, db, webvue3, storage/MinIO, ollama, searxng) plus
  `do.sh`, the build/deploy orchestration script, and server setup notes.
- Root `pom.xml` is a leftover Maven file, unused — the project builds with Gradle.

## Build & run

Backend (Kotlin/Spring Boot, multi-module Gradle, JDK 17):
```
./gradlew karaoke-app:bootJar              # build the core engine jar
./gradlew karaoke-web:bootJar              # build the public web module jar
./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel   # what deploy/do.sh uses for release builds
./gradlew karaoke-app:test                 # run JUnit tests for a module
./gradlew karaoke-app:test --tests "com.svoemesto.karaokeapp.SomeTest"   # run a single test
```
Note: the existing tests under `karaoke-app/src/test` (`SearchLastAlbumVkTest`, `PlaywrightTests`) are manual
integration checks that hit live network services / require a real browser and credentials — they are not a CI
safe-guard suite, mostly `@Disabled` or require env setup before they'll pass.

Frontend (`webvue3`, Vue 3 + Vite, Node):
```
cd webvue3
npm install
npm run dev          # dev server
npm run build        # production build
npm run preview      # preview a production build
```
No lint/test script is configured in `webvue3/package.json`.

Local dev / deployment (`deploy/do.sh`, a single entrypoint wrapping gradle + docker compose):
```
deploy/do.sh build            # gradle build jars + build all docker images
deploy/do.sh build_app        # build only the karaoke-app image
deploy/do.sh build_start_app  # rebuild karaoke-app and (re)start containers
deploy/do.sh start [all]      # start containers (add "all" to also (re)start the DATABASE)
deploy/do.sh stop [all]
deploy/do.sh start_db / stop_db
deploy/do.sh push / pull      # push/pull images to/from DOCKER_REGISTRY
deploy/do.sh ps
```
`deploy/do.env` / `deploy/.env` hold the environment (ports, registry, host folder mounts) consumed by the
compose files.

## Architecture notes

**Custom DB layer, no JPA/Hibernate.** Every persisted entity implements `KaraokeDbTable`
(`model/KaraokeDbTable.kt`): plain Kotlin data classes with raw JDBC (`Connection`/`KaraokeConnection`), reflection
is used to diff an entity's in-memory state against the row in Postgres on `save()`. The resulting field-level diff
(`RecordDiff`) is what actually gets turned into an `UPDATE ... SET` statement, and it is also broadcast as an
`SseNotification.recordChange` event so the `webvue3` admin UI (subscribed over SSE/websocket) sees live changes
made by other users/processes without polling. This recordhash/diff mechanism (see `deploy/recordhash_*.sql`) is
also how state gets synced between the LOCAL and SERVER databases.

**Dual database targets.** `Connection.kt` exposes `local()` / `remote()` (and `virtual()`) connection factories —
the app can be pointed at a local dev Postgres or the production server DB. `KaraokeProperties`/`SettingsDTOdigest`
and the `*_sync` tables coordinate propagating changes between them (see `autoUpdateRemoteSettings`,
`monitoringRemoteSettingsSync`, `allowUpdateRemote/Local` flags in `KaraokeProperties.kt`).

**Async job pipeline (`KaraokeProcess*`).** Long-running work (ffmpeg/`melt` rendering, Demucs source separation,
Sheetsage key/BPM/chord detection, file copy/symlink operations) is modeled as a `KaraokeProcess` row with a
`KaraokeProcessTypes` enum and run by `KaraokeProcessWorker`/`KaraokeProcessThread` as an OS subprocess
(`ProcessBuilder`). The worker parses the subprocess's stdout with regexes to extract progress percentage (ffmpeg
`time=`/`Duration:`, Sheetsage's `NN%|` progress bars) and reports progress back to the frontend via SSE. Jobs have
a priority and run on a managed pool — this queue is the backbone of the whole rendering pipeline, not a generic
task runner.

**MLT video generation (`mlt/` package).** Building a karaoke video means generating an MLT XML project (consumed
by the `melt` CLI from the MLT multimedia framework). `MltGenerator`/`MltProp`/`MltPropBuilder` build up
named producers/tractors/filters; `mlt/mko/*` ("Mlt Karaoke Object") are higher-level element builders for each
visual layer of the karaoke video — scrolling song text (`MkoSongText`/`MkoScroller`), chords/fingerboard
(`MkoChords`, `MkoFingerboard`, `MkoChordBoard`), melody notes/tabs, counters, horizon line, header, splash
screen, watermark, background. `KaraokeProperties.kt` holds the ~150 tunable rendering settings (fonts, colors,
offsets, timings) these builders read, persisted to a base64-encoded properties file
(`/sm-karaoke/system/Karaoke.properties`) and editable through the Properties UI/API rather than recompiling.

**LLM-assisted lyrics/chords search (`llm/` package).** `LyricsFinderService` orchestrates web search (via
SearXNG, see `SEARXNG_BASE_URL`) + scraping to find lyrics, using LangChain4j + Ollama agents/tools
(`Agents.kt`/`Tools.kt`). `UtilsPlaywright.kt` drives a real browser (Playwright/Selenium) for sources that need
JS rendering or an authenticated session (e.g. Yandex Music login state is saved/replayed from disk).

**Frontend ↔ backend wiring.** `webvue3` is a Vuex-modules-per-entity SPA (one `store.js` per domain area: Songs,
Authors, Pictures, Processes, Properties, Publish, Users, plus filter/modal sub-stores) talking to `karaoke-app`'s
`/api/...` REST endpoints (`controllers/ApiController.kt`, ~3400 lines, one route per song field/action —
mostly "save this field" style endpoints) and `controllers/MainController.kt` for page-level/utility actions.
Live updates use SockJS/STOMP over websocket (`assets/js/sockjs.js`, `stomp.js`) for things like the
`ProcessWorker`/`BackendConsole` components showing job progress and backend console output in real time.
Auth is OAuth2/OIDC against the authorization server embedded in `karaoke-app`
(`config/AuthorizationServerConfig.kt`, `config/SecurityConfig.kt`), consumed on the frontend via `oidc-client-ts`
(`services/AuthService.js`, `views/AuthView.vue` + `CallbackView.vue`).

**Storage.** Generated media files (audio stems, videos, pictures) live in MinIO-compatible object storage,
accessed through `services/StorageApiClient.kt` / `services/KaraokeStorageService.kt` and the corresponding
`StorageController`/`docker-compose-storage.yml`.

## karaoke-public (Vue SPA)

`karaoke-public/` — новый публичный фронтенд (Vue 3 + Vite, Bootstrap 5), заменяющий Thymeleaf-шаблоны
`karaoke-web`. Сборка и запуск:
```
cd deploy && bash do.sh build_start_public   # сборка Docker-образа (node→nginx) + рестарт контейнера на порту 7907
```

**Ключевые паттерны таблиц** (ZakromaView, SearchView):

- `table-layout: fixed` работает корректно **только при наличии явной ширины таблицы** (`width: Npx`). Без неё
  `colgroup` не фиксирует колонки — ширины заголовков расходятся между строками-иконками и строками-датами.
- Колонки платформ в `<colgroup>`: 22px каждая (16 штук = 352px). Ширина поля «Композиция» подбирается так,
  чтобы сумма всех колонок равнялась ширине таблицы.
- `display: flex` на `<td>` делает ячейку ниже высоты строки — использовать `text-align: center; vertical-align: middle`.
- Sponsr-иконка всегда вне `v-if="sett.onAir"` (рядом с полем Композиция), стили ячейки:
  `border-top-width:0; border-left-width:0; border-right-width:0; text-align:center; vertical-align:middle`.
- Строки с датой публикации вместо иконок: `<td v-else colspan="16">`.
- Фон скролл-контейнера / таблицы результатов: `background: #d5e6ff`.

**Bootstrap 5:** для `<select>` нужен класс `form-select` (не `form-control`) — иначе стрелка не отображается.

**Текущие размеры:**
- ZakromaView: внешний div 800px, таблица 780px (25 трек + 378 название + 25 sponsr + 16×22 платформ).
- SearchView: внешний div 900px, таблица 880px (100 автор + 35 год + 115 альбом + 25 трек + 228 название + 25 sponsr + 16×22 платформ).

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

Local dev / deployment (`deploy/do.sh`, a single entrypoint wrapping gradle + docker compose).
**Важно: все команды do.sh запускать из директории `deploy/` или передавать полный путь.**
```
cd deploy

bash do.sh build                  # gradle build jars + build all docker images
bash do.sh build_app              # build only the karaoke-app image
bash do.sh build_start_app        # rebuild karaoke-app and (re)start containers
bash do.sh build_start_web        # rebuild karaoke-web and (re)start
bash do.sh build_start_webvue3    # rebuild webvue3 and (re)start
bash do.sh build_start_public     # rebuild karaoke-public (Vue→nginx) and (re)start (порт 7907)
bash do.sh start [all]            # start containers (add "all" to also (re)start the DATABASE)
bash do.sh stop [all]
bash do.sh start_db / stop_db
bash do.sh push / pull            # push/pull images to/from DOCKER_REGISTRY
bash do.sh ps
```
`deploy/do.env` / `deploy/.env` hold the environment (ports, registry, host folder mounts) consumed by the
compose files.

**Правила сборки karaoke-public** (Vue 3 + Vite → Docker/nginx):
```
# Полный цикл пересборки и рестарта (локально):
cd /home/nsa/Karaoke/deploy && bash do.sh build_start_public

# Только пересборка npm (без Docker):
cd /home/nsa/Karaoke/karaoke-public && nvm use v25.7.0 && npm run build
```

## Деплой на продакшн-сервер (79.174.95.69)

Серверные файлы (`do.sh`, `docker-compose-*.yml`, `80to8897`) хранятся в `deploy/web-server-deploy/deploy/`
и синхронизируются на сервер через rsync. **Не редактировать файлы напрямую на сервере.**

**Обновление karaoke-web** (gradle build → Docker Hub push → pull на сервере):
```
cd /home/nsa/Karaoke/deploy && bash deploy_web.sh
```

**Обновление karaoke-public** (Docker build → Docker Hub push → pull на сервере):
```
cd /home/nsa/Karaoke/deploy && bash deploy_public.sh
```

**Синхронизация серверных конфигов** (при изменении do.sh / docker-compose / nginx):
```
cd /home/nsa/Karaoke/deploy
rsync -av --exclude='do.env' web-server-deploy/deploy/ root@79.174.95.69:Karaoke/deploy/
# nginx конфиг karaoke-public:
scp karaoke-public/nginx_karaoke-public.conf root@79.174.95.69:Karaoke/deploy/nginx_karaoke-public.conf
# Применить изменения nginx (если менялся 80to8897):
ssh root@79.174.95.69 "nginx -t && systemctl reload nginx"
```

**Архитектура на сервере:**
- nginx (443/80) → karaoke-public (порт 7907) — публичный фронтенд
- karaoke-public nginx → karaoke-web:7799 — проксирует `/api/public` и `/api/storage`
- karaoke-web (порт 8897) — остаётся запущенным как API-бэкенд

**Важные детали сервера:**
- Docker-сеть называется `deploy_karaokenet` (не `karaokenet`) — учитывать в новых docker-compose файлах
- Серверный `do.env`: содержит `PUBLIC_PATH_TO_NGINX_CONF=/root/Karaoke/deploy/nginx_karaoke-public.conf`
- `do.env` на сервере **не перезаписывать** через rsync — содержит секреты (DB пароли, порты)

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

**Размеры картинок в хранилище (бакет `karaoke`):**
- Альбом: полноразмерная 400×400, превью 50×50
- Автор: полноразмерная 1000×400, превью 125×50

Имена файлов формируются в `Pictures.storageFileName` / `Pictures.storageFileNamePreview`.
Для создания превью используется `resizeBufferedImage()` из `UtilsPictures.kt`.
Подход с base64 (`picture_full` в БД) используется только в `webvue3` admin — в публичном API
(`karaoke-public`) картинки отдаются как URL. **Новый код не должен использовать base64.**

**Публичные эндпоинты картинок** (`PublicApiController.kt` в `karaoke-web`):

- `GET /api/public/picture?file=<path>` — отдаёт файл из MinIO по имени. Если запрошен превью
  (`.preview.author.png` / `.preview.album.png`) и его нет — создаёт из полноразмерного,
  кэширует в MinIO. Автор: `newW=125, newH=50`; альбом: `newW=50, newH=50`.

- `GET /api/public/song-picture/{id}` — composite-баннер песни 800×194 (чёрный фон):
  альбом 154×154 @ (20, 20), автор 385×154 @ (294, 20). Читает полноразмерные PNG из MinIO,
  кэширует результат как `song_banner_{id}.png`. Параметры взяты из `getVKPictureBase64()`
  в `UtilsPictures.kt`. Используется на `SongView.vue` вместо устаревшего `vkPictureBase64`.

**Правило при загрузке картинок без base64:** передавать `ignoreUseInList = false` в
`Pictures.getPictureByName()` — иначе подтянется тяжёлое поле `picture_full` из БД.

## Git — что НЕ добавлять в репозиторий

- `deploy/ollama_data/` — содержит SSH-ключи (`id_ed25519`) и большие модели Ollama. Уже в `.gitignore`.
- `karaoke-public/dist/`, `webvue3/dist/` — артефакты сборки. Уже в `.gitignore` через `dist/`.
- `karaoke-public/node_modules/`, `webvue3/node_modules/` — зависимости npm. Уже в `.gitignore`.
- `deploy/.env`, `deploy/do.env` — секреты (пароли, порты). Уже в `.gitignore`.

При коммите всегда проверяй `git status` перед `git add` — особенно следи за папками
`ollama_data/`, `dist/`, `node_modules/` и любыми файлами с ключами/паролями.

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

**Тег SKIP — страница "удалено по требованию правообладателя":**

Если `tags` песни содержит `SKIP`, публичные страницы показывают заглушку:
- `karaoke-web`: `MainController.kt` `song()` возвращает шаблон `song-removed.html` вместо `song.html`.
- `karaoke-public`: `SettingsPublicDto` содержит `contentRemoved: Boolean` (из `tags`, теги наружу не утекают);
  `SongView.vue` показывает тёмную карточку через `v-else-if="currentSong && currentSong.contentRemoved"`.

**Полноэкранный фон в Vue:** CSS на компоненте не красит `body` — боковые поля остаются белыми.
Решение: `document.body.style.background` в watcher + сброс в `beforeUnmount` (см. `SongView.vue`).

**Текущие размеры:**
- ZakromaView: внешний div 800px, таблица 780px (25 трек + 378 название + 25 sponsr + 16×22 платформ).
- SearchView: внешний div 900px, таблица 880px (100 автор + 35 год + 115 альбом + 25 трек + 228 название + 25 sponsr + 16×22 платформ).

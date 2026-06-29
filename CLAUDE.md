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
bash do.sh build_start_web        # rebuild karaoke-web and (re)start
bash do.sh build_start_public     # rebuild karaoke-public (Vue→nginx) and (re)start (порт 7907)
bash do.sh start [all]            # start containers (add "all" to also (re)start the DATABASE)
bash do.sh stop [all]
bash do.sh start_db / stop_db
bash do.sh push / pull            # push/pull images to/from DOCKER_REGISTRY
bash do.sh ps
```
`deploy/do.env` / `deploy/.env` hold the environment (ports, registry, host folder mounts) consumed by the
compose files.

**Сборка и запуск karaoke-app** (сборка и запуск из разных папок):
```
cd ~/Karaoke/deploy && ./do.sh build_app
cd /sm-karaoke/system/deploy && ./do.sh start_app    # обычный запуск
cd /sm-karaoke/system/deploy && ./do.sh start_app2   # с выводом в консоль (для отладки)
```

**Dockerfile karaoke-app (`deploy/karaoke-app/Dockerfile`) — особенности:**
- Использует BuildKit cache mounts (`--mount=type=cache`) для apt и Playwright-браузеров — повторные сборки
  не скачивают пакеты заново.
- `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright` — браузеры хранятся в `/ms-playwright` в образе. При установке
  явно передаётся `PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright` (кэш BuildKit), затем `cp` в `/ms-playwright`.
- Docker CE установлен внутри образа намеренно: karaoke-app запускает `docker run` (Demucs, KeyBPMFinder)
  и `docker compose` (MLT) через `ProcessBuilder` из кода (`KaraokeProcess.kt`, `Settings.kt`).
- `ip-api.com`, `ipapi.co`, `ipapi.is` из Docker-контейнера возвращают 403 или 502 — не использовать.
- Для проверки ВПН используется `api.country.is` (работает из Docker без ограничений). Сравнивается с настройкой `vpnHomeCountry` (по умолчанию `"RU"`). Для сервера в Германии установить `vpnHomeCountry = "DE"` через интерфейс настроек. Если страна != `vpnHomeCountry` → ВПН включён.

**Dockerfile для node/nginx (webvue3, karaoke-public, karaoke-webvue) — паттерны:**
- Build stage: `node:22-alpine` (LTS, минимальный). **Не** `node:latest` — недетерминированный.
- npm BuildKit cache: `RUN --mount=type=cache,target=/root/.npm npm install` — повторные сборки не скачивают пакеты.
- Production stage: `nginx:stable` (Debian-based). **Не** `nginx:alpine` — docker-compose использует `/bin/bash -c "exec nginx..."`, в alpine нет bash → контейнер упадёт.
- karaoke-web: `eclipse-temurin:22-jre-jammy` (JRE, не JDK — Spring Boot fat jar не требует компилятора, ~200MB меньше).

**Сборка и запуск webvue3** (сборка и запуск из разных папок):
```
cd ~/Karaoke/deploy && ./do.sh build_webvue3
cd /sm-karaoke/system/deploy && ./do.sh start_webvue3
```

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
После запуска обязательно проверить:
1. В логах **нет** `EOF` / `400 Bad request` при push слоёв — иначе пуш не удался.
2. На сервере: `Status: Downloaded newer image` (не `Image is up to date`) — иначе сервер получил старый образ.
3. Финальная проверка содержимого `application.yml` в контейнере:
   ```
   ssh root@79.174.95.69 "docker exec karaoke-web bash -c 'cd /tmp && jar xf /app.jar BOOT-INF/classes/application.yml && cat BOOT-INF/classes/application.yml'"
   ```

**Если push через ВПН не удался** (тяжёлые базовые слои ~170MB падают по EOF):
Попросить пользователя запустить `cd /home/nsa/Karaoke/deploy && bash deploy_web.sh` вручную без ВПН.
При небольших изменениях (только app.jar, без смены базового Docker-образа) пуш через ВПН обычно проходит
успешно, т.к. тяжёлые слои уже есть на Docker Hub.

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
# ВАЖНО: rsync обновляет /root/Karaoke/deploy/80to8897, но nginx читает из /etc/nginx/sites-enabled/80to8897
# Это ОТДЕЛЬНЫЙ файл (не симлинк), его нужно скопировать вручную:
ssh root@79.174.95.69 "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"
```

**Архитектура на сервере:**
- nginx (443/80) → `/api/*` → karaoke-web (порт 8897) напрямую
- nginx (443/80) → `/song` + User-Agent `vkShare` → karaoke-web (порт 8897) — минимальный Thymeleaf: `<title>` + `<img>` в body
- nginx (443/80) → `/` → karaoke-public (порт 7907) — публичный фронтенд
- karaoke-web (порт 8897) — API-бэкенд (публичный API + синхронизация LOCAL↔SERVER БД)

**Важно:** весь `/api/` и `/changerecords` должны идти напрямую на karaoke-web (8897), **минуя** karaoke-public.
karaoke-app отправляет POST-запросы синхронизации БД на `/changerecords` и `/api/...`; если они попадают на karaoke-public
(nginx/SPA), он возвращает 405. В `80to8897` `location /api/` и `location /changerecords` стоят выше `location /`.

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

**Синхронизация LOCAL↔SERVER (`Utils.kt`, `updateRemoteDatabaseFromLocalDatabase`).**
Функция сравнивает хэши 18858+ записей двух БД. Два критичных паттерна производительности:
- Сравнение хэшей — через `associateBy { it.id }` + Map lookup (O(n)), **не** через вложенные `.any`/`.none`
  по спискам (O(n²) — при 18858 записях даёт 3+ минуты задержки).
- Загрузка записей для diff — пакетно через `loadListFromDbByIds` / `getAuthorsByIds` / `getPicturesByIds`
  (`WHERE id IN (...)`), **не** по одной через `loadFromDbById` в цикле (N+1 запросов по сети).

**Async job pipeline (`KaraokeProcess*`).** Long-running work (ffmpeg/`melt` rendering, Demucs source separation,
Sheetsage key/BPM/chord detection, file copy/symlink operations) is modeled as a `KaraokeProcess` row with a
`KaraokeProcessTypes` enum and run by `KaraokeProcessWorker`/`KaraokeProcessThread` as an OS subprocess
(`ProcessBuilder`). The worker parses the subprocess's stdout with regexes to extract progress percentage (ffmpeg
`time=`/`Duration:`, Sheetsage's `NN%|` progress bars) and reports progress back to the frontend via SSE. Jobs have
a priority and run on a managed pool — this queue is the backbone of the whole rendering pipeline, not a generic
task runner.

**Генерация текста табулатуры (`getFormattedNotes()` в `Settings.kt`).**
Функция строит HTML-табулатуру: 6 строк струн + строка нот + строка слогов. Все заполнители — **только ASCII**:
`-` вместо `⎼` (U+23BC), `||` вместо `‖` (U+2016). Unicode-символы рендерятся через font fallback с другой
шириной и ломают выравнивание в браузере. Структура строки струны: `"E||-"` (начало) + лады/черты + `"-||"` (конец).
Кэш хранится в `formattedTextTabs` в БД, пересчитывается при сохранении маркеров.
JS-зеркало функции — `getFormattedNotes()` в `webvue3/src/components/Songs/edit/SubsEdit.vue`.
`getNotesBody()` — plain text для описаний, Unicode там допустим, не трогать.

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

**Поиск нового альбома на Яндекс.Музыке (`searchLastAlbumYm3` / `checkLastAlbumYm` в `Utils.kt`).**
Функция возвращает `AlbumSearchResult` (sealed class): `Success`, `VpnBlocked`, `AuthExpired`, `BotDetected`, `Unknown`.

**Предварительная проверка ВПН (`isVpnActive()` в `Utils.kt`).**
Перед вызовом `checkLastAlbumYm()` в `KaraokeProcessWorker` вызывается `isVpnActive()` — лёгкий HTTP-запрос
к `api.country.is` (запасной: `ipapi.co/country/`). Если страна не `RU` — Playwright не запускается,
в лог выводится `"Проверка нового альбома пропущена — ВПН включён"`. При исключении (сервис недоступен)
возвращает `false` (не блокирует поиск). `ip-api.com` из Docker-контейнера возвращает 403 — не использовать.

Диагностика причины неудачи внутри `searchLastAlbumYm3`:
- **VPN**: HTML содержит `"недоступна в вашем регионе"` → сообщение "Отключите ВПН".
- **Авторизация**: `page.url()` после навигации содержит `passport.yandex` или `id.yandex` → сообщение "Переавторизуйтесь".
- **Unknown (резерв)**: запрос к `api.country.is`; если страна не `RU` — тоже VPN с кодом региона; иначе — сообщение об изменении кода страницы с `page.title()` и URL для диагностики.
HTML страницы в лог не выводится.

Коды `reason` из `checkLastAlbumYm` (обрабатываются в `KaraokeProcessWorker`):
- `-3` — VPN или авторизация: таймаут **не** увеличивается, `requestNewSongLastSuccessAuthor` **не** меняется (следующая попытка — тот же автор, тот же интервал).
- `-2` — нет автора в списке: таймаут не меняется, `requestNewSongLastSuccessTimeMs` обновляется.
- `-1` — бот-детект и прочие ошибки: таймаут увеличивается на `requestNewSongTimeoutIncreaseMs` (до 1 часа).
- `0` — успех без нового альбома; `1` — найден новый альбом: `requestNewSongLastSuccessAuthor` обновляется → следующий вызов берёт следующего автора.

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
**Base64 в `tbl_pictures` не используется нигде.** Поле `picture_full` всегда `""` — картинки
хранятся только в MinIO. Setter `Pictures.full` загружает файл и превью в MinIO, в БД пишет `""`.
`PicturesDTO` содержит `previewUrl` и `fullUrl` (вместо base64 `preview`/`full`).

**Nginx routing webvue3:** `location /api` → `karaoke-app:8899`. Поэтому для работы в admin-
контексте эндпоинт картинок — `GET /api/picture/file?file=<path>` в `ApiController.kt`, а не
`/api/public/picture` из `PublicApiController.kt` (karaoke-web). Эти два эндпоинта идентичны по логике.

**Эндпоинты картинок:**

- `GET /api/picture/file?file=<path>` (`ApiController.kt`, karaoke-app) — для **admin (webvue3)**:
  отдаёт файл из MinIO. Если запрошен превью и его нет — создаёт из полноразмерного, кэширует.
  Автор: `newW=125, newH=50`; альбом: `newW=50, newH=50`.

- `POST /api/song/picturealbum` и `POST /api/song/pictureauthor` (`ApiController.kt`, karaoke-app) —
  возвращают строку-URL `/api/picture/file?file=...` для показа картинки альбома/автора в `SongEdit.vue`.
  **Важно:** использовать `/api/picture/file`, не `/api/public/picture` — иначе картинки не отобразятся
  в webvue3 (nginx не проксирует `/api/public/picture` на karaoke-app). В `SongEdit.vue` переменные
  `imageAuthorBase64` / `imageAlbumBase64` теперь хранят URL, а не base64 — `:src` принимает оба формата.

- `GET /api/public/picture?file=<path>` (`PublicApiController.kt`, karaoke-web) — для **karaoke-public**:
  та же логика. Если запрошен превью и его нет — создаёт из полноразмерного, кэширует в MinIO.

- `GET /api/public/song-picture/{id}` — composite-баннер песни 800×194 (чёрный фон):
  альбом 154×154 @ (20, 20), автор 385×154 @ (294, 20). Читает полноразмерные PNG из MinIO,
  кэширует результат как `song_banner_{id}.png`. Параметры взяты из `getVKPictureBase64()`
  в `UtilsPictures.kt`. Используется на `SongView.vue` вместо устаревшего `vkPictureBase64`.

- `GET /api/public/song-vk-image/{id}` — VK-превью песни 537×240px (чёрный фон): альбом слева,
  автор справа, название песни жёлтым текстом Roboto Black с авторазмером внизу. Картинка
  отдаётся всегда (без условий по статусу/VK-ссылке). Если хоть одного логотипа нет в MinIO —
  redirect на `/KARAOKE_LOGO.png` (без кэша, следующий запрос повторит проверку).
  Кэш: `/tmp/vk_{id}.png`. Шрифт: `karaoke-web/src/main/resources/Roboto-Black.ttf` (бандлится в JAR).
  **Важно:** пути MinIO строить через `settings.author`, а не `albumPic.storageFileName` — поле
  `author` в записи Pictures может отличаться по регистру от `Settings.author`.
  **`song.html` (Thymeleaf для VK-бота):** минимальный шаблон — только `<title>` и `<img th:src="/api/public/song-vk-image/{id}">` в body.
  Никаких og: тегов — VK-бот находит `<img>` напрямую, заголовок берёт из `<title>`.
  **Не добавлять** Bootstrap/JS/CSS/og:-теги/getVKPictureBase64() — `picture_full` в БД всегда `""`, NPE при попытке декодировать пустой base64.
  **Кэш VK:** если ссылка раньше расшаривалась (даже без картинки), VK кэширует старый результат. Диагностика: в nginx-логах виден успешный запрос картинки от vkShare-бота, но в посте её нет. Стандартного инструмента сброса кэша у VK нет (vk.com/dev/pages упразднён). Обходные пути: подождать (VK обновляет кэш автоматически через некоторое время), или публиковать ссылку заново — повторная попытка нередко инициирует новый краул.

**Правило при загрузке картинок:** всегда использовать `ignoreUseInList = false` в
`Pictures.getPictureByName()` / `loadList()` — поле `picture_full` в БД теперь пустое, но
аннотация `useInList = false` оставлена намеренно, чтобы не тянуть это поле в списочных запросах.

**HealthReport (`HealthReport.kt`).** Проверяет состояние файлов каждой песни по локациям (диск, MinIO, удалённое хранилище). Ключевое правило:
- Видеофайлы (`VIDEO_SONGVERSION_1080P`, `VIDEO_SONGVERSION_720P`) проверяются **только при `idStatus >= 6`**. При статусе < 6 наличие видеофайлов на диске/хранилище не считается ошибкой и не приводит к их удалению.
- `canBe = false` + файл существует → ERROR + action на удаление. Не трогать эту логику для видео при статусе < 6.
- API: `POST /song/healthReportList` — получить список; `POST /song/executeHealthReportActions` — применить исправления.

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

**Двойной дизайн (классический + современный):**

В `karaoke-public` реализованы два параллельных дизайна. Выбор хранится в `localStorage`.

Структура:
```
src/
├── composables/useDesign.js          # design ('classic'|'modern') + theme ('light'|'system'|'dark') в localStorage
├── style.css                          # CSS-переменные --km-* для обеих тем
├── views/
│   ├── HomeView.vue / ZakromaView.vue / SearchView.vue / SongView.vue  # тонкие обёртки v-if design==='modern'
│   ├── classic/  Home/Zakroma/Search/SongClassic.vue  # оригинальный дизайн без изменений
│   └── modern/   Home/Zakroma/Search/SongModern.vue   # новый дизайн
└── App.vue                            # .modernScreen (full-width) vs .nonHomeScreen (centred) по design
```

Ключевые паттерны нового дизайна:
- `useDesign()` возвращает реактивные `design`, `theme`, `applyTheme()`. Тема применяется через `data-theme` на `:root`.
- Переключатель дизайна — только на главной: кнопка **✨ Новый дизайн** в `HomeClassic`, pill-toggle «Классика | Новый» в `HomeModern`.
- Переключатель темы (☀ / ⬡ / 🌙) — в хедере каждой Modern-страницы.
- Адаптивность: таблицы платформ скрыты на мобильных (`@media max-width: 768px`), вместо них — карточки `.km-cards`.
- CSS-переменные: `--km-bg`, `--km-card`, `--km-accent`, `--km-accent2`, `--km-border`, `--km-text`, `--km-text2`, `--km-hover`, `--km-input`, `--km-header`. Все Modern-компоненты используют только их.
- `SongModern`: видео — адаптивный `aspect-ratio: 16/9` iframe без фиксированной ширины; hero-баннер с overlay.

## Онлайн-плеер Karaoke (`/player/:id`)

Браузерный плеер, воспроизводящий karaoke визуально идентично MLT-рендеру. Открывается из `SongEdit.vue` кнопкой `▶` в шапке (header-column-1, метод `openPlayer()`).

**Файлы:**
- `webvue3/src/views/PlayerView.vue` — тонкая Vue-обёртка (`position:fixed; top:0; left:0; width:100vw; height:100vh`)
- `webvue3/src/player/KaraokePlayer.js` — чистый JS, весь код плеера (без Vue-зависимостей)
- `webvue3/src/App.vue` — для роутов `/player/*` скрывает sidebar/layout, рендерит только `<router-view>`

**Бэкенд-эндпоинты** (добавлены в конец `ApiController.kt`):
- `GET /api/song/{id}/fileminus.mp3` — аккомпанимент FLAC→MP3 с кешем (рядом с flac)
- `GET /api/song/{id}/filevoice.mp3` — вокал FLAC→MP3 с кешем
- `GET /api/song/{id}/playerdata` — JSON: `{songName, author, album, bpm, markers, audioAccompanimentUrl, audioVocalsUrl}`
  - `markers` = `settings.sourceMarkersList` (`List<List<SourceMarker>>`)

**Ключевые детали `SourceMarker` и `Markertype`:**
- Значения `markertype` — строго **lowercase**: `"syllables"`, `"endofline"`, `"newline"`, `"endofsyllable"`
- `ENDOFSYLLABLES.value = "endofsyllable"` (без "s" на конце!) — важно для парсера
- Конец слога определяется временем следующего маркера типа `syllables` или `endofline`

**Механика Canvas-рендера:**
- Сплэш-экран: первые 5 сек (`timeSplashScreenLengthMs = 5000`)
- Заголовок: появляется после сплэша, исчезает за 4 полтакта до первой строки-после-паузы, возвращается в конце
- Счётчик тактов: 4→3→2→1→0 перед строками у которых предшествует `newline`-маркер; `halfNoteLengthMs = 60000/BPM*2`
- Счётчик цвета: 4/3=красный, 2/1=жёлтый, 0=зелёный
- Заливка плавная: `fillW = xStart + (xEnd - xStart) * (ct - syllableStart) / syllableDuration`
- Horizon lines: верхняя и нижняя, Y = `H/2 ± lineHeight*0.58`; цвет: `['rgb(0,200,0)', 'rgb(200,0,0)', 'rgb(0,100,200)'][voiceIdx % 3]`
- Голос 0: белый `#ffffff`, bold; голос 1: жёлтый `rgb(255,255,155)`, italic bold
- Спетая строка: полная заливка `rgb(255,128,0)` + серый текст `rgba(255,255,255,0.35)`

**Audio (Web Audio API):**
- `await audioCtx.resume()` обязателен перед `source.start()` — Chrome блокирует без user gesture
- Два `AudioBufferSource` + два `GainNode` → `destination`; seek — пересоздание источников с `offset`

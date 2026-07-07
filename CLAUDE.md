# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Как устроена документация проекта.** Этот файл — компактная durable-карта: что за проект, модули,
> команды сборки/деплоя, обзор архитектуры и ключевые инварианты. Подробная **история фич и «ловушек»**
> (датированные записи) вынесена в `docs/architecture-notes-archive.md` (не грузится автоматически —
> читать вручную при необходимости) и продублирована memory-файлами в
> `~/.claude/projects/-home-nsa-Karaoke/memory/` (индекс — `MEMORY.md`, подтягиваются on-demand).

## What this is

"Karaoke" (svoemesto) is a self-hosted pipeline that automates production of karaoke videos: it finds/fetches song
audio, lyrics, chords and metadata, runs audio analysis and source separation, generates MLT (the `melt`/MLT
multimedia framework) project files describing the on-screen karaoke composition (scrolling lyrics, chords,
fingerboard, watermark, counters, splash screen, etc.), renders the videos via external CLI tools, and publishes
them. A Vue 3 admin SPA drives the whole pipeline against a Kotlin/Spring Boot backend.

## Modules / layout

- `karaoke-app` — the core engine. Spring Boot (Kotlin) app containing almost all domain logic: DB models, the
  MLT project generator, the async job-processing queue, AI/web-search lyrics finder, browser automation, storage
  client, OAuth2 authorization server. Everything else depends on it. **Runs only on the local admin machine —
  never deployed to the production server** (`deploy/web-server-deploy/deploy/` has no `docker-compose-app.yml`).
  Anything that must also work in production (settings read by `karaoke-web`, etc.) cannot rely on an HTTP call
  to `karaoke-app`; it needs to live in Postgres instead (see `tbl_public_settings` below).
- `karaoke-web` — thin Spring Boot module (`implementation(project(":karaoke-app"))`) exposing the public-facing
  website endpoints (song pages, stats, web events) and a websocket config. Reuses `karaoke-app`'s DB models and
  services directly.
- `karaoke-db` — standalone plain-Java Gradle module containing only `Main.java`; not wired into the app's data
  access (which talks to Postgres directly via raw JDBC from `karaoke-app`). Legacy/placeholder — don't assume it's
  load-bearing.
- `karaoke-vue` — legacy Vue app, abandoned; only `src/assets` remain, no real source. Not part of the build.
- `webvue3` — the current admin frontend: Vue 3 + Vite, Bootstrap-vue-next, Vuex. This is what's actively
  developed for the UI today.
- `karaoke-public` — the current public-facing SPA (Vue 3 + Vite, Bootstrap 5), replacing the legacy Thymeleaf
  templates of `karaoke-web`.
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
integration checks that hit live network services / require a real browser and credentials — not a CI safe-guard
suite (mostly `@Disabled` or require env setup).

Frontend (`webvue3`, Vue 3 + Vite, Node):
```
cd webvue3 && npm install && npm run dev      # dev server;  npm run build / npm run preview
```
`karaoke-public` собирается через Docker (см. do.sh ниже) или локально `nvm use v25.7.0 && npm run build`.
No lint/test script is configured in either frontend's `package.json`.

Local dev / deployment (`deploy/do.sh`, a single entrypoint wrapping gradle + docker compose).
**Важно: все команды do.sh запускать из директории `deploy/` или передавать полный путь.**
```
cd deploy
bash do.sh build                  # gradle build jars + build all docker images
bash do.sh build_app              # build only the karaoke-app image
bash do.sh build_web              # build only the karaoke-web image
bash do.sh build_start_public     # rebuild karaoke-public (Vue→nginx) and (re)start (порт 7907)
bash do.sh build_webvue3          # rebuild webvue3 image
bash do.sh build_demucs / push_demucs
bash do.sh start [all] / stop [all] / start_db / stop_db / push / pull / ps
```
`deploy/do.env` / `deploy/.env` hold the environment (ports, registry, host folder mounts).

**`build_start_web` / `build_start_webvue3` из `deploy/` НЕ перезапускают реальный локальный
контейнер правильно** — см. ниже «Сборка/запуск локальных контейнеров» (`karaoke-app`, `karaoke-web`,
`webvue3` живут в отдельной рантайм-папке `/sm-karaoke/system/deploy`, не в `~/Karaoke/deploy`).

**Очередь (взаимное исключение) gradle-сборок (`deploy/build-lock.sh`).** Все jar-сборки
(`karaoke-app`/`karaoke-web`) идут через `${GRADLE} clean <module>:bootJar` — параллельный запуск двух над
одним репозиторием ломается (`clean` стирает общий `build/`, gradle держит эксклюзивный лок на `.gradle/`).
`deploy/build-lock.sh` (source-ится в `do.sh`) сериализует сборки через `flock` + пробу `pgrep gradle-wrapper.jar`
+ **guard прямо в `gradlew`** (маркер `karaoke build-lock guard` — при апгрейде wrapper вносить заново). При
неизменных исходниках сборка **пропускается** по sha256-отпечатку в `.build/<module>.stamp`; форс — `FORCE=1`
или `--force`. **Детали** → memory `project_build_lock_queue.md` и архив.

**Сборка/запуск локальных контейнеров (`karaoke-app`, `karaoke-web`, `webvue3`) — ВСЕГДА из разных
папок.** Реальный локальный контейнер поднимается из `/sm-karaoke/system/deploy` (свой `do.sh`, свои
`docker-compose-*-new-comp.yml`) — это не то же самое, что git-репозиторий `~/Karaoke/deploy`.
**Никогда не использовать однокомандные `build_start_app`/`build_start_web`/`build_start_webvue3`
целиком из `~/Karaoke/deploy`** — они гоняют не тот compose-файл (репозиторный, а не `-new-comp.yml`
из `/sm-karaoke/system/deploy`), даже если по имени контейнера выглядит рабочим. Исключение —
`karaoke-public`: у него нет пары в `/sm-karaoke/system/deploy`, поэтому `build_start_public` одной
командой из `~/Karaoke/deploy` — корректно.
```
cd ~/Karaoke/deploy && ./do.sh build_app        # или build_web / build_webvue3 — сборка из репо
cd /sm-karaoke/system/deploy && ./do.sh start_app     # обычный запуск (или start_web / start_webvue3)
cd /sm-karaoke/system/deploy && ./do.sh start_app2    # с выводом в консоль (отладка, только app)
```

**Особенности Docker-образов** (детали → memory `project_dockerfile_karaoke_app.md`):
- `deploy/karaoke-app/Dockerfile`: BuildKit cache mounts (apt + Playwright); `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright`;
  Docker CE установлен внутри образа намеренно (karaoke-app запускает `docker run`/`docker compose` из кода).
- **Проверка ВПН** (durable-инвариант): `ip-api.com`/`ipapi.co`/`ipapi.is` из Docker отдают 403/502 — **не
  использовать**. Использовать `api.country.is`. Сравнение с настройкой `vpnHomeCountry` (дефолт `"RU"`; для
  сервера в Германии — `"DE"`). Страна != `vpnHomeCountry` → ВПН включён.
- node/nginx-образы (webvue3/karaoke-public): build-stage `node:22-alpine` (**не** `node:latest`), npm BuildKit
  cache; production-stage `nginx:stable` (**не** `nginx:alpine` — compose использует `/bin/bash`, в alpine его нет).
  karaoke-web — `eclipse-temurin:22-jre-jammy` (JRE, не JDK).

## Деплой на продакшн-сервер (79.174.95.69)

Серверные файлы (`do.sh`, `docker-compose-*.yml`, `80to8897`) хранятся в `deploy/web-server-deploy/deploy/`
и синхронизируются на сервер через rsync. **Не редактировать файлы напрямую на сервере.**

**Ловушка деплоя:** `deploy_web.sh` синхронизирует **только** jar/образ karaoke-web (gradle → push → pull). Он
**не трогает** `docker-compose-web.yml`/`80to8897`/`do.sh` на сервере. Если фикс требует одновременно новый код
**и** новую env-переменную/конфиг nginx — сначала выполнить «Синхронизацию серверных конфигов» (ниже), иначе
контейнер пересоздастся по старому compose без новой переменной. Проверять реальное значение:
`docker exec karaoke-web env | grep <VAR>`. (Детали → memory `feedback_deploy_web_config_sync.md`.)

```
# karaoke-web (gradle build → Docker Hub push → pull на сервере):
cd /home/nsa/Karaoke/deploy && bash deploy_web.sh
# karaoke-public:
cd /home/nsa/Karaoke/deploy && bash deploy_public.sh
```
После `deploy_web.sh` проверить: (1) в логах push **нет** `EOF`/`400 Bad request`; (2) на сервере `Status:
Downloaded newer image` (не `Image is up to date`); (3) содержимое `application.yml` в контейнере:
```
ssh root@79.174.95.69 "docker exec karaoke-web bash -c 'cd /tmp && jar xf /app.jar BOOT-INF/classes/application.yml && cat BOOT-INF/classes/application.yml'"
```
**Не** прятать вывод сборки за `| tail -N` (молчаливый провал gradle) — грепать `BUILD SUCCESSFUL`. Если push
через ВПН падает по EOF на тяжёлых базовых слоях — попросить пользователя запустить `deploy_web.sh` вручную без ВПН.

**Синхронизация серверных конфигов** (при изменении do.sh / docker-compose / nginx):
```
cd /home/nsa/Karaoke/deploy
rsync -av --exclude='do.env' web-server-deploy/deploy/ root@79.174.95.69:Karaoke/deploy/
scp karaoke-public/nginx_karaoke-public.conf root@79.174.95.69:Karaoke/deploy/nginx_karaoke-public.conf
# ВАЖНО: nginx читает /etc/nginx/sites-enabled/80to8897 — ОТДЕЛЬНЫЙ файл (не симлинк), копировать вручную:
ssh root@79.174.95.69 "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"
```

**Архитектура на сервере:**
- karaoke-app на сервере **не разворачивается вовсе** — только БД, karaoke-web, karaoke-public, storage.
- nginx (443/80): `/` → karaoke-public (7907); `/api/*` и `/changerecords` → karaoke-web (8897) **напрямую,
  минуя** karaoke-public (иначе SPA возвращает 405 на POST синхронизации БД — в `80to8897` `location /api/` и
  `/changerecords` стоят **выше** `location /`); `/song` + User-Agent `vkShare` → karaoke-web (минимальный Thymeleaf).
- Docker-сеть называется `deploy_karaokenet` (не `karaokenet`) — учитывать в новых compose-файлах.
- Серверный `do.env` **не перезаписывать** через rsync — содержит секреты (DB пароли, порты).

## Architecture notes

> Здесь — **durable-карта**: как устроены ключевые подсистемы, плюс инварианты-«правила». Датированная
> история конкретных фич и «ловушек» — в `docs/architecture-notes-archive.md` (не грузится автоматически)
> и в memory-файлах.

**Custom DB layer, no JPA/Hibernate.** Каждая персистентная сущность реализует `KaraokeDbTable`
(`model/KaraokeDbTable.kt`): plain Kotlin data class + raw JDBC (`Connection`/`KaraokeConnection`); на `save()`
reflection диффит in-memory состояние против строки в Postgres. Полученный field-level дифф (`RecordDiff`)
превращается в `UPDATE ... SET` и одновременно рассылается как `SseNotification.recordChange` — так admin-UI
(`webvue3`, подписан по SSE/websocket) видит live-изменения без polling. Этот же recordhash/diff-механизм
(`deploy/recordhash_*.sql`) синхронизирует LOCAL и SERVER БД.

**SSE-уведомления (`SseNotificationService`).** Один эндпоинт `GET /api/subscribe` (чистый `EventSource`/
`EventSourcePolyfill`, **не** SockJS/STOMP — эти библиотеки лежат в репо, но нигде не используются). Подписка
ключуется `UserKey(userId, tabId)`: `tabId` генерирует фронт (`getTabId()` в `webvue3/src/lib/utils.js`,
`sessionStorage`, передаётся и в query-параметре подписки, и в заголовке `X-Tab-Id` на каждом XHR через
`TabIdFilter`/`TabIdContext`) — у каждой вкладки/компьютера своё независимое соединение, а не одно на всё
приложение. `send()` различает broadcast (`recordChange`/`log`/`processWorkerState` и т.п. — всем вкладкам) и
адресную доставку (`MESSAGE`/`ERROR` — только вкладке, инициировавшей запрос, если её `tabId` известен из
контекста). Heartbeat — независимый `@Scheduled(fixedRate = 15_000)` (`@EnableScheduling` в
`KaraokeAppApplication`), шлёт SSE-comment `:ping`, **не завязан на `KaraokeProcessWorker`**: раньше
единственным «пингом» было сообщение `DUMMY` из цикла воркера — без запущенной очереди соединение переставало
получать трафик и рвалось прокси/клиентом по таймауту простоя.

**Dual database targets.** `Connection.kt` даёт фабрики `local()`/`remote()`/`virtual()` — приложение можно
направить на локальный dev-Postgres или на прод-БД. `KaraokeProperties`/`SettingsDTOdigest` и `*_sync`-таблицы
координируют распространение изменений (`autoUpdateRemoteSettings`, `monitoringRemoteSettingsSync`,
`allowUpdateRemote/Local`). Per-request `Connection.local()/remote()` **обязательно** закрывать (обёртка `withDb`
в контроллерах с `target=local|remote`) — иначе утечка соединений → «too many clients».

**Универсальный движок sync LOCAL↔SERVER** (`sync/SyncTarget.kt`, `Utils.kt`). `updateDatabases()` работает по
списку `SyncRegistry.all` (`SyncTarget<T>` на сущность: `settings`, `pictures`, `authors`, `siteusers`, `events`,
`siteplaylists`, `siteplaylistitems`, `songassignments`, `songassignmentdrafts`) через generic-функцию
`collectSyncOps()`. Разрешения — **8 флагов на сущность** (`sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`).
`SyncOperation` = INSERT/UPDATE/DELETE (над целью) + MOVE (удалить перенесённое из источника). Направление
`oneClickDirection` per-entity. Возврат — `SyncResult(created, updated, deleted, moved)`.
**REST-контракт:** `GET /api/sync/entities`, `POST /api/sync/run` (`key`, `direction=PUSH|PULL`, опц. `id`),
`POST /api/sync/oneclick`, `POST /api/sync/setflag`. **UI:** `webvue3/components/Sync/SyncTable.vue` + `store.js`
(две группы столбцов push/pull, по 4 чекбокса-флага). **Детали, перф-паттерны (O(n) хэши, пакетная загрузка,
чанкование «2 оси»), ловушки (Timestamp-биндинг, nullable-колонки, `last_update useInDiff=false`)** → memory
`project_universal_db_sync.md` + архив.

**Async job pipeline (`KaraokeProcess*`).** Тяжёлая работа (ffmpeg/`melt`-рендер, Demucs, Sheetsage key/BPM/chords,
copy/symlink) моделируется строкой `KaraokeProcess` с enum `KaraokeProcessTypes`, исполняется
`KaraokeProcessWorker`/`KaraokeProcessThread` как OS-подпроцесс (`ProcessBuilder`). Воркер парсит stdout регексами
(ffmpeg `time=`/`Duration:`, Sheetsage `NN%|`) → прогресс по SSE. Задания имеют приоритет и **thread-лейны**
(`threadId` группирует в независимые последовательные очереди; константы `THREAD_LANE_HEAVY_RENDER=0`,
`THREAD_LANE_LIGHT_BACKGROUND=-1`, `THREAD_LANE_REMOTE_STORE_UPLOAD=-2`). «Функциональные» типы
(`KEY_BPM_FROM_FILE`, `UPLOAD_TO_LOCAL/REMOTE_STORE`) вызывают Kotlin-функцию напрямую (`runFunctionWithArgs`).
Ограничение CPU — 3 слоя (docker `--cpus` / `MLT_CPU_LIMIT` env для `docker compose` / `docker update` на живой
контейнер). **Детали (forceStop, cpu-limit, stop-loop, per-thread UI-прогресс)** → соответствующие memory + архив.

**Прогресс загрузки файлов (`CountingInputStream`).** MinIO Java SDK (8.6.0) не имеет `ProgressListener`, но
`PutObjectArgs.stream()` принимает `InputStream` — прогресс через `CountingInputStream`
(`karaoke-app/.../CountingInputStream.kt`, `FilterInputStream`-счётчик), которым оборачивается файл. Для удалённой
загрузки (`StorageApiClient`/WebClient multipart) переопределён `ByteArrayResource.getInputStream()` — та же
обёртка. Прогресс пишется в `KaraokeProcessThread.percentage`; существующий SSE-механизм доносит до UI.

**MLT video generation (пакет `mlt/`).** Сборка видео = генерация MLT XML-проекта (для `melt` CLI).
`MltGenerator`/`MltProp`/`MltPropBuilder` строят named producers/tractors/filters; `mlt/mko/*` («Mlt Karaoke
Object») — билдеры визуальных слоёв (текст `MkoSongText`/`MkoScroller`, аккорды/гриф `MkoChords`/`MkoFingerboard`/
`MkoChordBoard`, ноты/табы, счётчики, горизонт, хедер, сплэш, watermark, фон). `KaraokeProperties.kt` держит
~150 настраиваемых параметров рендера (шрифты, цвета, отступы, тайминги), персистятся в base64-properties-файл
(`/sm-karaoke/system/Karaoke.properties`), редактируются через Properties UI/API без перекомпиляции.

**LLM-assisted lyrics/chords search (пакет `llm/`).** `LyricsFinderService` оркестрирует web-поиск (SearXNG,
`SEARXNG_BASE_URL`) + скрейпинг, используя LangChain4j + Ollama (`Agents.kt`/`Tools.kt`). `UtilsPlaywright.kt`
драйвит реальный браузер (Playwright/Selenium) для источников с JS-рендером или авторизованной сессией (логин
Яндекс.Музыки сохраняется/переигрывается с диска).

**Поиск нового альбома на Яндекс.Музыке (`searchLastAlbumYm3`/`checkLastAlbumYm` в `Utils.kt`).** Возвращает
sealed `AlbumSearchResult`: `Success`/`VpnBlocked`/`AuthExpired`/`BotDetected`/`Unknown`. Перед вызовом —
`isVpnActive()` (лёгкий запрос к `api.country.is`; страна != `RU` → Playwright не запускается). Коды `reason` в
`KaraokeProcessWorker`: `-3` VPN/авторизация (таймаут и автор не меняются), `-2` нет автора (таймаут не меняется),
`-1` бот-детект/ошибки (таймаут +`requestNewSongTimeoutIncreaseMs`, до часа), `0` успех без нового альбома,
`1` найден новый (обновляется `requestNewSongLastSuccessAuthor`). **Детали диагностики** → memory
`project_album_search_diagnostics.md`.

**Frontend ↔ backend wiring.** `webvue3` — Vuex-modules-per-entity SPA (по `store.js` на домен: Songs, Authors,
Pictures, Processes, Properties, Publish, Users, Stats, Sync, SongEditor…) поверх REST `karaoke-app`
(`controllers/ApiController.kt` ~3400 строк — по маршруту на поле/действие; `MainController.kt` — страничные/
утилитарные). Live-обновления — SSE (`EventSourcePolyfill`, `/api/subscribe`, см. «SSE-уведомления» выше).
Auth — OAuth2/OIDC против
authorization server внутри `karaoke-app` (`config/AuthorizationServerConfig.kt`, `SecurityConfig.kt`), на фронте —
`oidc-client-ts` (`services/AuthService.js`, `AuthView.vue`/`CallbackView.vue`).
**Vuex-паттерны (Songs/store.js):** мутации — только sync-присвоение `state`; весь async (XHR, dispatch) — в
actions; HR-запросы таблицы — через очередь (`HR_MAX_CONCURRENT=3`); удаление/правки песни — через SSE
(`recordDelete`/`recordChange`), не локальным рендерингом. Фильтры табличных вьюшек (Songs/Users/Authors/
Pictures/Properties/Processes/Publish) персистятся через связку Vuex-модуль `<Entity>/filter/store.js` +
`setWebvueProp`/`getWebvueProp` (сервер-сайд key/value, переживает переход между вьюшками и `F5`) — новый
фильтр всегда заводить по этому шаблону, не локальным `data()` компонента. **Детали** → memory
(`feedback_vuex_async_mutations`, `feedback_hr_request_queue`, `project_delete_song_fix`,
`project_process_worker_progress_ui_per_thread`, `project_webvue3_filter_persistence`).

**Storage (MinIO).** Сгенерированная медиа (аудио-стемы, видео, картинки) живёт в MinIO-совместимом объектном
хранилище (`services/StorageApiClient.kt` / `KaraokeStorageService.kt`, `StorageController`/
`docker-compose-storage.yml`). **Размеры картинок:** альбом 400×400 + превью 50×50; автор 1000×400 + превью
125×50 (имена — `Pictures.storageFileName`/`storageFileNamePreview`). Base64 в `tbl_pictures` не используется —
`picture_full` всегда `""`, картинки только в MinIO (`PicturesDTO` несёт `previewUrl`/`fullUrl`). **Эндпоинты
картинок:** admin (webvue3) — `GET /api/picture/file?file=<path>` (karaoke-app; автосоздание превью при
отсутствии); public — `GET /api/public/picture` (karaoke-web); composite-баннер песни — `GET
/api/public/song-picture/{id}` (800×194); VK-превью — `GET /api/public/song-vk-image/{id}` (537×240).

### Ключевые инварианты (правила, нарушение которых уже ломало прод)

- **karaoke-web Settings trap.** В `karaoke-web` **нельзя** трогать `Settings.rootFolder` / любые `*NameFlac`-
  геттеры — они тянут инициализацию `ConstantsKt`/`Connection` из `karaoke-app`, которой в karaoke-web нет →
  `IllegalStateException`/`NoClassDefFoundError` роняет процесс на любой следующий запрос. Читать стемы/картинки
  из karaoke-web — только через MinIO (`fetchFromMinIO`/`existsInMinIO`), никогда через локальные пути.
- **MTU black-hole на проде.** Любой исходящий HTTPS с прод-контейнера karaoke-web на внешний хост (MinIO
  89.125.103.63, Yandex SmartCaptcha и т.п.) виснет из-за MTU-дропа больших TLS-пакетов. Обход: HTTP через
  host-nginx под алиасом `minio-proxy` (env-переменная с дефолтом на прямой адрес + `location` в `80to8897`).
  **Для любого нового внешнего API из karaoke-web — сразу закладывать этот паттерн.** (Admin-машина не затронута.)
- **URL-кодирование `?file=`.** Любой `?file=<storageFileName>` **обязан** оборачивать значение в
  `URLEncoder.encode(value, UTF_8)` — иначе `&`/`#` в имени автора/альбома («Чиж & Co») обрывают query → 404,
  картинка молча не показывается. Серверные посегментные фетчи MinIO — не трогать (кодируют посегментно).
- **Jackson `is*`-поля.** Kotlin `val isX: Boolean` Jackson сериализует в JSON-ключ **без** `is` (`"x"`).
  Фронт читает `user.premium`, `dto.original` — не `isPremium`/`isOriginal` (иначе тихо `undefined`). Новый
  `data class ...Dto` с булевым полем сразу называть без `is`-префикса.
- **reflection-loader и nullable-колонки.** `KaraokeDbTable.loadList` кидает NPE на `SQL NULL` в строковой/
  timestamp-колонке, если Kotlin-поле объявлено non-null (`String`/`Timestamp` вместо `String?`/`Timestamp?`).
  `Int`/`Long` безопасны (`getLong()` даёт `0` на NULL). Любая новая модель над таблицей с nullable-строками —
  объявляет их nullable.
- **Тег SKIP.** `tags` содержит `SKIP` → публичные страницы показывают заглушку «удалено по требованию
  правообладателя» (`song-removed.html` в karaoke-web; `SettingsPublicDto.contentRemoved` в karaoke-public, теги
  наружу не утекают).
- **HealthReport видео.** Видеофайлы (`VIDEO_SONGVERSION_1080P/720P`) проверяются **только при `idStatus >= 6`**.
  При статусе < 6 их наличие на диске/в хранилище не ошибка и не приводит к удалению.
- **`karaoke-app` не на проде.** Разворачивается только на admin-машине → HTTP-вызовы к нему из karaoke-web в
  проде не работают; всё нужное проду живёт в Postgres (`tbl_public_settings`). `KaraokeWebService.init{}`
  дополнительно инициализирует `SNS` (иначе первый INSERT через `KaraokeDbTable` из karaoke-web упал бы).
- **webvue3 nginx `/api`.** `proxy_pass` на docker-сервис — через переменную + `resolver 127.0.0.11`, не
  литералом (литерал роняет nginx, если целевой контейнер не поднят при старте).
- **do.sh не должен печатать секреты.** Никакой `echo`/`cat`/`printenv` с `DOCKER_PASSWORD` и другими
  токенами/паролями в `do.sh` (ни в репо, ни в рантайм-копии `/sm-karaoke/system/deploy/do.sh`) — попадает в
  открытом виде в лог/переписку с ассистентом. Секреты живут только в `do.env`/`.env` (в `.gitignore`).

## Git — что НЕ добавлять в репозиторий

- `deploy/ollama_data/` — SSH-ключи (`id_ed25519`) и большие модели Ollama. Уже в `.gitignore`.
- `karaoke-public/dist/`, `webvue3/dist/` — артефакты сборки (`dist/`). Уже в `.gitignore`.
- `karaoke-public/node_modules/`, `webvue3/node_modules/`. Уже в `.gitignore`.
- `deploy/.env`, `deploy/do.env` — секреты (пароли, порты). Уже в `.gitignore`.

При коммите всегда проверяй `git status` перед `git add` — особенно `ollama_data/`, `dist/`, `node_modules/` и
любые файлы с ключами/паролями.

## Публичные модули (karaoke-public / плеер / аккаунт) — карта

Детальные истории этих фич — в `docs/architecture-notes-archive.md` (Часть 2) и в memory. Здесь — карта +
инварианты.

- **karaoke-public (Vue SPA).** Публичный фронт (Vue 3 + Vite, Bootstrap 5), заменяет Thymeleaf `karaoke-web`.
  **Двойной дизайн** classic/modern (`composables/useDesign.js`, выбор в localStorage; тонкие view-обёртки
  `v-if design==='modern'`, каталоги `views/classic|modern`, CSS-переменные `--km-*`). Таблицы Закрома/Поиск —
  `table-layout: fixed` с явной шириной. Сборка — `nvm use v25.7.0`. Детали → memory `project_karaoke_public_views`,
  `project_dual_design`, `project_zakroma_*`.
- **Онлайн-плеер (`/player/:id`).** Браузерный плеер, визуально идентичный MLT-рендеру. **ДВЕ намеренные копии**
  `KaraokePlayer.js` (`webvue3/src/player/` — admin, читает локальные файлы `/api/song/{id}/...`; и
  `karaoke-public/src/player/` — публичный, читает MinIO). **Любую правку логики плеера вносить в обе копии.**
  Публичный плеер гейтится: admin — клиентски по `idStatus>=3`; public — доступ по `onAir`/премиуму + **секретный
  жест** разблокировки (тройной Shift-клик по «Тональность»; вся логика на бэкенде, токен, эндпоинты отдают 404
  без токена). Детали → memory `project_karaoke_player`, `project_player_*`, `project_webvue3_player_button`.
- **Регистрация/авторизация site users (`/login`, `/register`, `/account`).** Отдельно от админских `tbl_users` —
  таблица `tbl_site_users` (+ `tbl_site_user_tokens`). Токен сессии **персистентный** (не JWT):
  `SiteUserTokenService` на каждый запрос делает живой SELECT (бан/logout мгновенны). Защита —
  `SiteAuthInterceptor` (не Spring Security chain). Премиум — `is_premium` + независимый «вечный»
  `is_permanent_premium` → вычисляемый `isEffectivePremium` (единая точка проверки доступа). Капча — Yandex
  SmartCaptcha (ключи в `tbl_public_settings`, fail-open). Детали → memory `project_site_user_auth`,
  `project_captcha_mtu_proxy`.
- **Онлайн-редактор разметки (`/account/editor`) с модерацией.** Упрощённый публичный аналог `SubsEdit.vue`:
  пользователь расставляет слоговые маркеры, админ модерирует (workflow `assigned→in_progress→submitted→
  approved/rejected`). **ДВЕ таблицы** (sync-движок пишет строку целиком по одному направлению):
  `tbl_song_assignments` (конверт+вердикт админа, LOCAL_TO_SERVER) и `tbl_song_assignment_drafts` (правки
  пользователя, SERVER_TO_LOCAL). Композитный статус — `SongAssignmentStatus.resolve(...)` с timestamp'ами.
  Доступ к разделу — отдельная роль `is_editor` на `SiteUser` (не факт наличия assignment'а), включается
  админом в webvue3 по образцу премиума; гейтинг на фронте (кнопка/редирект) и **обязательно** на бэкенде
  (`PublicSongEditorController`, иначе снятие роли не отзывает доступ к уже выданным заданиям). Детали →
  memory `project_song_editor`, `project_site_user_editor_role`.
- **«Избранное» и «Плейлисты» + плейлист автора.** `tbl_site_playlists`(+`_items`); «Избранное» = плейлист с
  `is_favorites=true`. Не-премиум: только «Избранное». Гейтинг по премиуму — плейлисты **скрываются**, не
  удаляются. Плеер плейлиста = тот же `/player/:id` в iframe с `?pl=1` (очередь/авто-переход внутри iframe,
  токен следующего трека «точно в срок»). Плейлист автора — динамический read-only. Детали → memory
  `project_favorites_playlists`.

## `tbl_public_settings` и ручные SQL-миграции

`tbl_public_settings` (`deploy/karaoke-db/07_public_settings.sql`) — маленькая key/value таблица в Postgres для
настроек, нужных сервисам **на сервере** (сейчас — ключи капчи). Не путать с `KaraokeProperties` (~150 файловых
настроек рендера, только на admin-машине). Всё, что должно работать в проде, — через `tbl_public_settings` с
паттерном `target=local|remote` (`PublicSettingsController.kt`, раздел «Настройки сайта» в webvue3).

**Новые таблицы `deploy/karaoke-db/*.sql` и `ALTER TABLE` — применять вручную и на LOCAL, и на серверной БД
(79.174.95.69:8832) отдельно** (миграция сама на сервер не попадает):
```
docker exec -i -u postgres karaoke-db psql -d karaoke < migration.sql    # LOCAL
```
**На проде роли `postgres` НЕТ** — узнать реальную роль: `docker exec karaoke-db env | grep '^POSTGRES_USER='`
(узкий grep, не полный env — иначе утечёт пароль), передать `-U <роль>`. Флаг `-i` обязателен (иначе heredoc не
долетает до psql). Любая правка схемы таблицы, участвующей в recordhash-diff (`tbl_events` →
`deploy/recordhash_events.sql`; `tbl_site_users` — триггер `update_tbl_site_users_recordhash`, инлайн в
`06_site_users.sql`/`12_site_user_limits.sql`/`13_site_user_editor_role.sql`), обязана обновить и функцию
триггера (+ разовый `UPDATE ... SET recordhash = ...` для существующих строк), иначе `SyncTarget.listHashes()`
не увидит новую колонку как diff и LOCAL↔SERVER sync молча игнорирует изменение поля.

## Сетевое окружение машины администратора (Claude Code через VPN)

На машине администратора настроен process-scoped split-tunnel: собственный сетевой трафик Claude Code
(API-запросы, авторизация, телеметрия, WebFetch) идёт через VLESS-VPN, а команды, которые Claude Code
выполняет через Bash-тул (git, npm, docker, curl и т.п.), идут обычным маршрутом, без VPN.

- Headless `xray-core` (пакет `xray-server`) — systemd-сервис `xray.service` (`enabled`). HTTP-inbound (не socks —
  Claude Code не поддерживает SOCKS) на `127.0.0.1:1081`, VLESS-outbound наружу.
- `~/.bashrc`: функция `claude()` выставляет `HTTPS_PROXY`/`HTTP_PROXY=http://127.0.0.1:1081`; `claude --novpn`
  запускает без прокси.
- Глобальный `PreToolUse`-хук на `Bash` (`~/.claude/hooks/strip-proxy.sh`) переписывает каждую Bash-команду в
  `env -u HTTPS_PROXY -u HTTP_PROXY -- bash -c '<original>'`, чтобы дочерние процессы не наследовали proxy.
  Экранирование — через `jq -nr --arg c "$command" '$c | @sh'` (флаг `-r` обязателен).
- Отдельно установлен `goxray-gui` (system-wide TUN, другая задача) — не пересекается, трогать не нужно.

## Язык сессий и суб-агентов Claude Code

Пользователь работает на русском — это распространяется и на имена сессий, и на сообщения суб-агентов.

- **Имена сессий — по-русски.** Отображаемое имя в `claude --resume` берётся из последней записи `custom-title`
  в `.jsonl`-файле сессии (`~/.claude/projects/-home-nsa-Karaoke/<sessionId>.jsonl`) — перекрывает
  автогенерируемый `ai-title`. Переименование = дописать в конец файла строку
  `{"type":"custom-title","customTitle":"<имя>","sessionId":"<id>"}` (JSON с `ensure_ascii=False`).
- **Суб-агенты — тоже по-русски.** Поле `"language": "Russian"` в `~/.claude/settings.json` применяется только к
  основному циклу и **не** пробрасывается в суб-агентов (Agent/Task/Workflow). Единственный надёжный канал —
  добавлять «Отвечай на русском языке» **прямо в промпт каждого запускаемого агента**.

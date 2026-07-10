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

**Голосовые уведомления (`deploy/announce.sh`).** Вместо системного «дзынь» — `announce "текст" [голос 1-5]
[скорость]` синтезирует речь через Silero TTS (нейросеть, оффлайн, venv `~/.venvs/karaoke-tts`, модель в
`~/.cache/silero-tts/`) и проигрывает в фоне, не блокируя скрипт; фолбэк — RHVoice → системный бип. Голоса:
1=aidar, 2=baya (по умолчанию), 3=kseniya, 4=xenia, 5=eugene. Скорость — `x-slow|slow|medium|fast|x-fast`.
Ударение — `+` перед гласной прямо в тексте (`г+олоса`). `announce.sh` подключается через `source` в оба
`do.sh` (репо и рантайм-копия) и в `deploy_web.sh`/`deploy_public.sh` (озвучивают успешный pull+restart на
сервере). **Ловушка:** модель Silero не только падает на фразах без единой кириллической буквы, но и молча
проглатывает латинские слова/акронимы даже в смешанных русско-английских фразах (без исключения) — поэтому
`deploy/tts/silero_say.py` транслитерирует ЛЮБЫЕ латинские слова в кириллицу перед синтезом, а не только
когда во всей фразе нет кириллицы. **Ловушка 2:** невалидный номер голоса (не 1-5, например паразитный
`"0"`) роняет синтез с ненулевым кодом возврата, а `set -e` в `deploy_web.sh`/`deploy_public.sh` из-за
этого валит exit code всего скрипта в 1 — даже если сам деплой (push/pull/restart) уже полностью успешно
прошёл. При диагностике «деплой упал» — всегда сверяться с реальными логами (`Downloaded newer image`,
`Started ...Application`), не только с exit code обёртки. **Детали** → memory
`project_do_sh_voice_announce.md`.

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

**Права Claude Code на сборку/перезапуск/деплой** (обновлено 2026-07-09; детали и история →
memory `feedback_no_container_restarts.md` + `feedback_deploy_permission.md`):
- `karaoke-app` — самостоятельная **сборка** разрешена без спроса (и `./gradlew :karaoke-app:
  compileKotlin`, и полноценный `do.sh build_app`, пересобирающий образ). **Перезапуск контейнера**
  (`start_app`/`start_app2`) — всегда только пользователь, никогда не выполнять самому, даже по
  прямому «делай».
- `karaoke-web`, `karaoke-public`, `webvue3` — локально разрешены и сборка, и перезапуск без
  спроса. Механизм не меняется — см. выше «Сборка/запуск локальных контейнеров»: `build_X` из
  `~/Karaoke/deploy`, затем `start_X` из `/sm-karaoke/system/deploy` как две отдельные команды
  (кроме `karaoke-public` — там `build_start_public` одной командой из `~/Karaoke/deploy` корректно).
- Деплой `karaoke-web`/`karaoke-public` на продакшн-сервер (`deploy_web.sh`, `deploy_public.sh`,
  любые правки `web-server-deploy/`, прямые DDL/DML к серверной БД) — только с согласия пользователя
  или по его прямому указанию, каждый раз заново (старое разрешение не переносится на новое
  действие). `karaoke-app` на сервере не разворачивается вовсе — вопрос деплоя для него не стоит.

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
списку `SyncRegistry.all` (`SyncTarget<T>` на сущность: `settings`, `pictures`, `authors`, `dictionaries`,
`news`, `siteusers`, `events`, `siteplaylists`, `siteplaylistitems`, `songassignments`, `songassignmentdrafts`,
`pricetariffs`, `subscriptions`, `chatmessages`) через generic-функцию `collectSyncOps()`. Разрешения — **8
флагов на сущность** (`sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`); для append-only сущностей
(`chatmessages`) `pull_move` держать выключенным осознанно — MOVE удаляет строку из источника (PROD) после
переноса, что для чата означало бы стирание переписки с сервера.
`SyncOperation` = INSERT/UPDATE/DELETE (над целью) + MOVE (удалить перенесённое из источника). Направление
`oneClickDirection` per-entity. Возврат — `SyncResult(created, updated, deleted, moved)`.
**REST-контракт:** `GET /api/sync/entities`, `POST /api/sync/run` (`key`, `direction=PUSH|PULL`, опц. `id`),
`POST /api/sync/oneclick`, `POST /api/sync/setflag`. **UI:** `webvue3/components/Sync/SyncTable.vue` + `store.js`
(две группы столбцов push/pull, по 4 чекбокса-флага). **Детали, перф-паттерны (O(n) хэши, пакетная загрузка,
чанкование «2 оси»), ловушки (Timestamp-биндинг, nullable-колонки, `last_update useInDiff=false`)** → memory
`project_universal_db_sync.md` + архив.

**Словари (`tbl_dictionaries`, `model/Dictionary.kt`).** Раньше «Слова с Ё»/«Censored»/«Sync Ids»
хранились в текстовых файлах `/sm-karaoke/system/*.txt` через интерфейс `TextFileDictionary`;
теперь — единая таблица БД (`KaraokeDbTable`), синхронизируется LOCAL→SERVER как
`DictionariesSyncTarget` (key=`dictionaries`). `TextFileDictionary` и классы `Yo/Censored/
SyncIdsDictionary` оставлены тонким фасадом над `Dictionary.*` — вызывающий код
(`Extentions.censored()`, `Utils.replaceSymbolsInSong`, `Settings.getWhereList`) не менялся.
Разовый перенос старых `.txt` в БД — `POST /api/dictionaries/importfromfiles`. **Ловушка:** Kotlin
поддерживает вложенные `/* */`-комментарии — путь вида `/sm-karaoke/system/*.txt` внутри
`/** KDoc */` содержит `/*` и обрывает комментарий («Unclosed comment» + каскад ошибок по всем
зависимым файлам); в line-комментариях (`//`, `--`) проблемы нет. Админ-раздел «Словари» в webvue3
(`/dictionaries`, `DictionariesController` — `list/names/create/update/delete`, только LOCAL) даёт
полноценный CRUD по образцу Authors (фильтр, сортировка) + Tariffs (inline-добавление/удаление); имя
словаря выбирается select'ом из уже существующих значений, свободный ввод не допускается. Уникальность
пары `dict_name`+`dict_value` гарантирована `UNIQUE`-индексом в БД; при этом `KaraokeDbTable.save()`
молча глотает исключение конфликта на UPDATE (см. инвариант reflection-loader ниже), поэтому
контроллер проверяет конфликт сам, до вызова `save()`. **Детали** → memory
`project_dictionaries_migration.md`.

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

**Автоматизация публикации в Telegram-канал (Фаза 1 — отлов ссылки, `services/TelegramApiClient.kt`/
`TelegramUpdatesConsumer.kt`).** Раньше пользователь вручную создавал отложенный пост в Telegram (метка `-`
в `id_telegram_*`) и вручную вставлял ссылку после выхода. Теперь фоновый демон-поток (паттерн — как
`KaraokeProcessWorker`, но не блокирует HTTP/event-поток; автозапуск на `ApplicationReadyEvent`, флаг
`KaraokeProperties.telegramPollingEnabled`) long-polling'ом `getUpdates` ловит вышедший `channel_post` канала
и сам пишет `message_id` в песню через штатный `Settings.saveToDb()`. **Сопоставление поста с песней/версией —
без отдельного маркера**: пост уже содержит `linkSM` (`https://sm-karaoke.ru/song?id=<id>`) и явный разделитель
версии — `Settings.parseTelegramPostSongId`/`parseTelegramPostSongVersion` (companion `Settings.kt`) извлекают
их напрямую. Работа из России: Telegram периодически недоступен без VPN — `TelegramApiClient` реализует
авто-fallback «сначала напрямую → при ошибке через HTTP-прокси (`telegramProxyUrl`) → периодическая попытка
вернуться на прямой путь»; прокси — отдельный docker-сервис `karaoke-telegram-proxy` (`deploy/docker-compose-
telegram-proxy.yml`, `xray-core` с VLESS-outbound), конфиг с реальным VLESS вне git —
`/sm-karaoke/system/telegram-proxy/config.json`. Фаза 2 (постинг видео в момент эфира, снимает лимит Telegram
в 100 отложенных публикаций) — спроектирована, не реализована. **Детали** → memory
`project_telegram_publish_automation.md`.

**Мониторинг ключевых моментов проекта (пакет `monitor/`, кнопка-светофор в хедере webvue3).**
Обобщение подсистемы `HealthReport` (см. ниже), но НЕ привязанное к конкретной песне `Settings` —
системные проверки состояния проекта в целом. `MonitorRegistry.checks` — список `object :
MonitorCheck` (сейчас 6: горизонт запланированных постов в Telegram, доступность прод-сервера,
остановленная очередь рендера, выключенный Telegram-поллинг, непрочитанные сообщения в чате от
пользователей, задания онлайн-редактора «на проверке» (`SubmittedAssignmentsCheck`, смотрит remote);
добавление новой проверки — одна строка в реестре). `MonitoringService`
(`@Scheduled` раз в минуту) прогоняет их, хранит снапшот в
памяти, рассылает по SSE (`MONITOR_ALERTS`, broadcast всем вкладкам). Состояние «прочитано» — по
хэшу содержимого (severity+заголовок+текст, БЕЗ изменчивых чисел вроде «N минут» — те в отдельном
поле `detail`, иначе алерт «мигал» бы read/unread на каждом тике), персистится в
`KaraokeProperty monitorDismissed`. «Решить проблему» — ре-деривация resolve-действия по ключу из
свежего снапшота (лямбда не сериализуется в DTO, тот же паттерн, что и у `HealthReport`). REST —
`/api/monitor/{alerts,resolve,markRead,markUnread,reset}`. **Детали** → memory
`project_monitoring_subsystem.md`.

**Frontend ↔ backend wiring.** `webvue3` — Vuex-modules-per-entity SPA (по `store.js` на домен: Songs, Authors,
Pictures, Processes, Properties, Publish, Stats, Sync, SongEditor…) поверх REST `karaoke-app`
(`controllers/ApiController.kt` ~3400 строк — по маршруту на поле/действие; `MainController.kt` — страничные/
утилитарные). Live-обновления — SSE (`EventSourcePolyfill`, `/api/subscribe`, см. «SSE-уведомления» выше).
`webvue3` (admin) не гейтится авторизацией — `SecurityConfig.kt` пускает всё (`permitAll()`, кроме
`/api/private/**`); отдельного admin-логина (старые `tbl_users`/OAuth2 `AuthorizationServerConfig`) в проекте
больше нет — см. «Ключевые инварианты» ниже.
**Vuex-паттерны (Songs/store.js):** мутации — только sync-присвоение `state`; весь async (XHR, dispatch) — в
actions; HR-запросы таблицы — через очередь (`HR_MAX_CONCURRENT=3`); удаление/правки песни — через SSE
(`recordDelete`/`recordChange`), не локальным рендерингом. Фильтры табличных вьюшек (Songs/Authors/
Pictures/Properties/Processes/Publish) персистятся через связку Vuex-модуль `<Entity>/filter/store.js` +
`setWebvueProp`/`getWebvueProp` (сервер-сайд key/value, переживает переход между вьюшками и `F5`) — новый
фильтр всегда заводить по этому шаблону, не локальным `data()` компонента. Сортировка таблицы по клику на
заголовок столбца (там, где таблица уже на `<b-table>`) — встроенный механизм bootstrap-vue-next: `sortable:
true` на нужных полях в `fields` + `v-model:sort-by` (data `sortBy: []`) на `<b-table>`, без ручной sort-логики
(первый пример — `SiteUsers/SiteUsersTable.vue`). **Детали** → memory
(`feedback_vuex_async_mutations`, `feedback_hr_request_queue`, `project_delete_song_fix`,
`project_process_worker_progress_ui_per_thread`, `project_webvue3_filter_persistence`,
`project_webvue3_table_sorting`).

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
  Ловушка шире, чем только `rootFolder`: любое обращение к top-level `WORKING_DATABASE`/`ConstantsKt` из
  нового karaoke-app кода рискованно в karaoke-web (прод-инцидент 2026-07-09 — перенос словарей Ё/Censored
  из файлов в БД случайно завёл `censored()`/`replaceSymbolsInSong()` в эту ловушку, `/api/public/zakroma`
  падал 500). Защита для словарей — `TextFileDictionary.dict` обёрнут в `try { ... } catch (e: Throwable) {
  emptyList() }` (важно `Throwable`, не `Exception` — `NoClassDefFoundError` это `Error`). **Второй вариант
  той же ловушки — коллизия имён.** В `karaoke-web` есть СВОЙ `com.svoemesto.karaokeweb.WORKING_DATABASE`
  (свой `Connection`, env-флаг `WEB_WORK_ON_SERVER`) — использовать всегда именно его, никогда
  `com.svoemesto.karaokeapp.WORKING_DATABASE` (тот на проде резолвится в LOCAL, т.к. флаги karaoke-app там
  не выставлены). Аналогично `KSS_APP`/`SAC_APP` (lateinit из `karaoke-app.services`) в karaoke-web ВСЕГДА
  не инициализированы (нет `@ComponentScan` до этого пакета) — использовать конструкторные Spring-бины
  `KaraokeStorageService`/`StorageApiClient` (как в `PublicPlaylistController`), не глобалы. Детали → memory
  `project_karaoke_web_settings_trap.md`.
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
- **`apiGet`/`apiPost` (karaoke-public) возвращают тело напрямую, НЕ `{status, body}`.** В отличие от
  `authGet`/`authPost` (`services/authApi.js`, для приватных `/api/public/account/**`), публичный клиент
  `services/api.js` резолвит уже распарсенным JSON без обёртки. Код по образцу `chatApi.js`
  (`const { status, body } = await fetchXxx()`), написанный поверх `apiGet`, молча получает
  `status=undefined` и всегда уходит в ветку «ошибка/пусто» — без ошибок в консоли, просто пустая лента.
  Так было с первой версией `NewsView.vue`/`NewsBell.vue` (лента новостей и колокольчик молчаливо не
  показывали данные, пока не поймали на скриншоте). Для любого нового ПУБЛИЧНОГО (без токена) эндпоинта в
  karaoke-public — потреблять результат `apiGet` напрямую, не деструктурировать `{status, body}`.
- **reflection-loader и nullable-колонки.** `KaraokeDbTable.loadList` кидает NPE на `SQL NULL` в строковой/
  timestamp-колонке, если Kotlin-поле объявлено non-null (`String`/`Timestamp` вместо `String?`/`Timestamp?`).
  Для `Int?`/`Long?` загрузчик (после фикса 2026-07-09) правильно возвращает настоящий `null` при
  `rs.wasNull()` — **до фикса** молча подменял `NULL` на `0`, что ломало `.save()` (мнимый diff → попытка
  записать строку `"null"` в числовую колонку → тихо проваленный `UPDATE`, ошибка проглатывается). Любая
  новая модель над таблицей с nullable-колонками (строка, timestamp, число) — объявляет соответствующее
  Kotlin-поле nullable. **Тот же swallow бьёт и по UNIQUE-конфликтам**: `try/catch` вокруг
  `ps.executeUpdate()` в `save()` глотает любое SQL-исключение, включая нарушение уникального индекса
  при редактировании записи в конфликтующее состояние — `save()` не бросает и не сигнализирует об
  ошибке. Для сущности с `UNIQUE`-индексом, редактируемой через UI, — проверять конфликт (`get*`-поиск
  по новым значениям, сравнить id) в контроллере **до** вызова `.save()`, не полагаться на `try/catch`
  вокруг него (см. `DictionariesController.update()`).
- **Identity-sequence дрейф после `SERVER_TO_LOCAL` sync + самолечение в `createDbInstance`.** Sync
  вставляет строки с явным серверным `id` через `INSERT ... OVERRIDING SYSTEM VALUE` — это **не двигает**
  локальную `GENERATED ALWAYS AS IDENTITY`-последовательность, поэтому у любой такой таблицы (плейлисты,
  site users, subscriptions, song assignments и т.п.) `nextval()` рано или поздно попадает на уже занятый
  id. `KaraokeDbTable.createDbInstance()` при конфликте PK должен сам обнаружить дрейф и рестартнуть
  sequence — но делал это через **два `executeQuery()` на одном `Statement`**, а второй запрос по
  контракту JDBC закрывает `ResultSet` первого, из-за чего самолечение никогда не срабатывало и вставка
  молча проваливалась (баг вскрылся на «Избранном» — сердечко «загоралось», но не сохранялось; чинено
  2026-07-09, раздельные `Statement` на каждый запрос). **Правило:** никогда не переиспользовать один
  `Statement` для нескольких последовательных `executeQuery()`, если оба `ResultSet` нужны одновременно.
- **Тег SKIP.** `tags` содержит `SKIP` → публичные страницы показывают заглушку «удалено по требованию
  правообладателя» (`song-removed.html` в karaoke-web; `SettingsPublicDto.contentRemoved` в karaoke-public, теги
  наружу не утекают).
- **Новая таблица с recordhash-триггером ≠ она уже участвует в sync.** Наличие триггера/колонки `recordhash`
  в SQL-миграции не означает, что таблица подтягивается «Синхронизацией в 1 клик» — для этого сущность обязана
  быть явно добавлена в `SyncRegistry.all` (`sync/SyncTarget.kt`) + получить свои 8 флагов
  `sync_<key>_*_allowed` в `KaraokeProperties.kt`. Без этого LOCAL и SERVER копии таблицы расходятся молча:
  дашборды на `target=local` показывают устаревшие/неполные данные даже сразу после «1 клика», хотя сам подсчёт
  корректен — просто исходные строки никогда не приезжали. Так было с `tbl_subscriptions` (заведена в БД, но
  не в `SyncRegistry` — дашборд статистики «Монетизация» врал на LOCAL) — исправлено 2026-07-09.
- **HealthReport видео.** Видеофайлы (`VIDEO_SONGVERSION_1080P/720P`) проверяются **только при `idStatus >= 6`**.
  При статусе < 6 их наличие на диске/в хранилище не ошибка и не приводит к удалению.
- **`karaoke-app` не на проде.** Разворачивается только на admin-машине → HTTP-вызовы к нему из karaoke-web в
  проде не работают; всё нужное проду живёт в Postgres (`tbl_public_settings`). `KaraokeWebService.init{}`
  дополнительно инициализирует `SNS` (иначе первый INSERT через `KaraokeDbTable` из karaoke-web упал бы).
- **webvue3 nginx `/api`.** `proxy_pass` на docker-сервис — через переменную + `resolver 127.0.0.11`, не
  литералом (литерал роняет nginx, если целевой контейнер не поднят при старте).
- **Админских логинов (`tbl_users`) в проекте больше нет.** Разделы «Пользователи»/«Login» в webvue3, модель
  `Users`/`UsersDto`, `UsersController`, `CustomUserDetailsService` и закомментированный
  `AuthorizationServerConfig.kt` удалены; таблица `tbl_users` снесена и на LOCAL, и на сервере. `SecurityConfig.kt`
  остаётся (пускает всё через `permitAll()`, кроме `/api/private/**`) — его бин `passwordEncoder()` использует
  `SiteUser.checkPassword/setPassword` (через `karaoke-web` контроллеры). Всё, что раньше называлось
  «Пользователи», теперь означает только «Пользователи сайта» (`tbl_site_users`).
- **do.sh не должен печатать секреты.** Никакой `echo`/`cat`/`printenv` с `DOCKER_PASSWORD` и другими
  токенами/паролями в `do.sh` (ни в репо, ни в рантайм-копии `/sm-karaoke/system/deploy/do.sh`) — попадает в
  открытом виде в лог/переписку с ассистентом. Секреты живут только в `do.env`/`.env` (в `.gitignore`).
- **CSS grid с фиксированными колонками и длинным текстом.** `white-space: nowrap` без
  `overflow: hidden; text-overflow: ellipsis` на самой grid-ячейке не обрезает контент — он визуально
  наезжает на соседнюю колонку вместо переноса (`grid-template-columns` с px-шириной не клипует
  содержимое сам по себе). Любая новая строка-грид с колонками фиксированной ширины и переменной длины
  текста (лейблы событий, теги и т.п.) — сразу с ellipsis на каждой узкой текстовой колонке.
- **`<select>` в плотных таблицах/формах webvue3.** Без `appearance: none` (+ `-webkit-`/`-moz-`)
  нативный `<select>` рисует свою ОС-рамку/паддинг/высоту поверх заданных CSS-стилей — раздувает высоту
  строки таблицы и не совпадает по бордеру с соседними `<input>`. Ширину `<select>` **никогда** не
  подбирать формулой под интринсик-ширину соседнего `<input>` (`width:100%`, `20ch`, px на глаз — не
  совпадают, т.к. реальная ширина `<input>` без явного `size`/`width` считается непрозрачным UA-
  алгоритмом). Единственный надёжный способ — задать **один и тот же явный `width`** одним CSS-правилом
  сразу на оба типа полей (общий класс), чтобы совпадение было гарантированным, а не подогнанным на глаз.
- **Круглая кнопка очистки в `<Entity>FilterModal.vue`.** Ряд фильтра — поле (`.xxx-input-field`) +
  кнопка «X» (`.xxx-button-clear-field`, `margin-left: -10px`, специально сдвинута к полю). Если у
  поля не задана явная ширина (`width: fit-content`), оно растягивается на весь 200px-контейнер, и
  кнопка наезжает на текст. Фикс — `width: calc(100% - 18px)` на `-input-field` (10px под сдвиг кнопки
  + 8px зазора), как в `Songs/filter/SongsFilterModal.vue`; для select-полей рядом добавить
  `select.xxx-input-field { appearance: none; ... }`. Любой новый `FilterModal.vue` — копировать этот
  CSS-блок сразу, не `width: fit-content`.
- **Прод-сервер: диск заполняется старыми Docker-образами.** `do.sh` пушит образы всегда под
  один и тот же тег (`APP_VERSION`/`BUILD_VERSION` статичны, фактически всегда `:1`) — каждый
  новый деплой оставляет предыдущий слой образа untagged/dangling; без регулярной чистки диск
  (40GB) заполняется до 100% за несколько месяцев деплоев, Postgres перестаёт писать (`No space
  left on device`) и весь сайт падает (инцидент 2026-07-09 — 131 образ, из них 3 в реальном
  использовании). **Защита уже установлена — не создавать заново:** systemd-таймер
  `karaoke-docker-prune.timer` на сервере (ежедневно 04:15 МСК, `docker image prune -a -f`,
  безопасен — не трогает образы используемых контейнеров), юниты и скрипт —
  `deploy/web-server-deploy/deploy/{prune-images.sh,karaoke-docker-prune.service,karaoke-docker-prune.timer}`,
  синхронизируются rsync'ом вместе с остальными серверными конфигами. Проверка состояния:
  `ssh root@79.174.95.69 "systemctl status karaoke-docker-prune.timer"`. Если на проде снова
  «сайт не отвечает»/DB-ошибки — сначала `df -h /` на сервере, до глубокой диагностики кода.
  Детали → memory `project_prod_disk_full_incident`.

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
  `table-layout: fixed` с явной шириной. Сборка — `nvm use v25.7.0`. Закрома (`ZakromaView.vue`) — быстрый
  клиентский фильтр по названию песни (sticky-панель под шапкой, `computed filteredZakroma` над Vuex-геттером
  `zakroma`, без запроса к бэку); watch на готовность плеера/плейлистов остаётся на исходном `zakroma`, не на
  отфильтрованном — иначе каждая нажатая клавиша дёргала бы сеть. Детали → memory `project_karaoke_public_views`,
  `project_dual_design`, `project_zakroma_*`.
- **Поиск (`SearchView.vue`) с алиасами авторов.** У каждого автора (`tbl_authors.aliases`,
  текст, `;`-разделитель, напр. `Владимир Ткаченко;Владимир Кучеренко`) может быть список
  алиасов — имена солистов/участников группы, по которым фанаты ищут вместо названия
  коллектива («цой» находит «КИНО»). `Author.resolveByTerm()` (karaoke-app) резолвит термин в
  реальные имена авторов + СОВПАВШИЕ алиасы (лёгкий raw-SELECT, безопасен для вызова из
  karaoke-web — не задевает karaoke-web Settings trap). `Settings.getWhereList` ищет песни по
  набору имён через ключ `author_in`, разделитель `Settings.AUTHOR_IN_DELIMITER = "\u0001"`
  (ASCII SOH, чтобы не пересечься с `;`/`,`/пробелами в именах). Фолбэк на старое строгое
  `author=`, если резолвинг не дал совпадений — не ломает поиск по авторам без записи в
  `tbl_authors`. `SettingsPublicDto.authorAlias` — какой именно алиас совпал (пусто при
  совпадении по реальному имени), рендерится в `SearchView.vue` курсивом мельче в скобках
  (несколько — через запятую). Колонки «Исполнитель»/«Альбом» в таблице поиска — с переносом
  текста (не ellipsis), под длинный текст просто расширены, а не обрезаны. Редактирование
  алиасов — webvue3 (Авторы → колонка «Алиасы» → модалка `AuthorAliasesModal.vue`, список
  add/remove). Заполнено веб-поиском для всех 122 авторов (78 получили хотя бы один алиас,
  seed — `deploy/karaoke-db/author_aliases_seed.sql`, часть помечена «ТРЕБУЕТ ПРОВЕРКИ» —
  желательна ручная перепроверка). Детали → memory `project_author_aliases`.
- **Онлайн-плеер (`/player/:id`).** Браузерный плеер, визуально идентичный MLT-рендеру. **ДВЕ намеренные копии**
  `KaraokePlayer.js` (`webvue3/src/player/` — admin, читает локальные файлы `/api/song/{id}/...`; и
  `karaoke-public/src/player/` — публичный, читает MinIO). **Любую правку логики плеера вносить в обе копии.**
  Публичный плеер гейтится: admin — клиентски по `idStatus>=3`; public — доступ по `onAir`/премиуму + **секретный
  жест** разблокировки (тройной Shift-клик по «Тональность»; вся логика на бэкенде, токен, эндпоинты отдают 404
  без токена). Регулировка скорости воспроизведения (0.5x–3x) — живой `AudioParam` на уже играющих
  `AudioBufferSourceNode` (без рестарта источников), позиция считается через якорь `_rateAnchorPos` вместо
  прямого `currentTime-startedAt`. Громкость/якорь/скорость персистентны не только в рамках плейлиста, но и
  глобально в `localStorage['karaoke-player-settings']` — подхватываются при открытии плеера на любой другой
  песне. Бейдж скорости в углу канваса при rate≠1x; клик по видео-области = play/pause (анимация значка по
  центру, курсор — дефолтный, НЕ pointer). Меню публичного плеера намеренно урезано до одного пункта
  «Скорость» (без «Открыть файл»/«Экспорт аудио» — те остаются только в admin-копии). Детали → memory
  `project_karaoke_player`, `project_player_*`, `project_webvue3_player_button`.
- **Демо-режим онлайн-плеера.** Не-премиум/анонимный посетитель при готовом контенте (не в эфире/не
  подписан) вместо paywall получает короткий фрагмент вместо отказа. Фрагмент = «куплет группы 0»
  (`SETTING GROUP|N`, дефолт группа 0) минус 5 сек отступа с фейд-ином — до конца куплета (смена
  группы/принудительная пустая строка); `Settings.demoFragmentStartSeconds/EndSeconds/
  FadeInSeconds` (karaoke-app). Обрезка — **на сервере**, `Mp3Trimmer.trimToRange` (karaoke-web,
  чистый JVM-парсер mp3-фреймов, без ffmpeg, работает с VBR) — полный файл физически не покидает
  сервер; `playerData()` дополнительно перебазирует тайминги маркеров под обрезанный файл.
  `PublicPlayerController.access()` сам решает demo/full — фронту не нужно различать точки входа.
  Публичный плеер (только `karaoke-public`, НЕ admin-копия) рисует водяной знак «ДЕМО», фейдит
  аудио на входе/выходе фрагмента, по окончании показывает DOM-оверлей (не Vue-модалку — общего
  контекста между iframe-встройкой и отдельной вкладкой `/player/:id` нет) с переходом на страницу
  песни. Плейлисты в демо намеренно не участвуют (и так премиум-фича). Детали → memory
  `project_demo_mode`.
- **Регистрация/авторизация site users (`/login`, `/register`, `/account`).** Отдельная от админки система —
  таблица `tbl_site_users` (+ `tbl_site_user_tokens`); админских логинов (`tbl_users`) в проекте больше нет. Токен сессии **персистентный** (не JWT):
  `SiteUserTokenService` на каждый запрос делает живой SELECT (бан/logout мгновенны). Защита —
  `SiteAuthInterceptor` (не Spring Security chain). Премиум — `is_premium` + независимый «вечный»
  `is_permanent_premium` → вычисляемый `isEffectivePremium` (единая точка проверки доступа). Капча — Yandex
  SmartCaptcha (ключи в `tbl_public_settings`, fail-open). Админ-таблица «Пользователи сайта» (webvue3,
  `SiteUsersTable.vue`) показывает `isEffectivePremium` золотой монеткой сразу после ID (колонка живёт
  под JSON-ключом `effectivePremium` — Jackson `isX`→`x` — и отражает премиум по ЛЮБОМУ источнику: флаг,
  вечный грант, непросроченный Sponsr/сайт-премиум), плюс остальные поля `SiteUserDto`, которых раньше не
  было в таблице (даты истечения премиума, `is_editor`, причина бана, персональные лимиты) — бэкенд их уже
  отдавал, менять не пришлось. Детали → memory `project_site_user_auth`, `project_captcha_mtu_proxy`,
  `project_site_users_table_premium_column`.
- **Онлайн-редактор разметки (`/account/editor`) с модерацией.** Упрощённый публичный аналог `SubsEdit.vue`:
  пользователь расставляет слоговые маркеры для ВСЕХ голосов песни (задание = вся песня целиком), админ
  модерирует (workflow `assigned→in_progress→submitted→approved/rejected`). **ДВЕ таблицы** (sync-движок
  пишет строку целиком по одному направлению), **ОБЕ `SERVER_TO_LOCAL`** — `tbl_song_assignments` (конверт+
  вердикт админа) и `tbl_song_assignment_drafts` (правки пользователя, JSON-массивы по голосам). Реальный
  рабочий цикл (назначить→работа→апрув) часто идёт ЦЕЛИКОМ на PROD — `assign`/`approve`/`reject`/`delete`
  (`SongEditorController`) все target-aware (local|remote): статус задания пишется в ТУ ЖЕ БД, откуда
  прочитан (жёсткий `local` на `reject` — реальный баг, чинен 2026-07-08, ломал возврат на доработку для
  remote-заданий); единственное, что всегда пишется в LOCAL — само изменение песни в `tbl_settings`
  (karaoke-app рендерит только с локального диска). `approve()` дополнительно пушит изменённую песню на
  сервер (`updateRemoteSettingFromLocalDatabase`, тот же вызов, что кнопка «Обновить на сервере» в
  `SongEdit.vue`), под тем же предохранителем `allowUpdateRemote`. Пока задание `submitted` и админ ещё
  не вынес вердикт — пользователь может отозвать его обратно в работу (`/tasks/{id}/recall`, статус
  строго проверяется на сервере). Композитный статус — `SongAssignmentStatus.resolve(...)` с
  timestamp'ами. Доступ к разделу — отдельная роль `is_editor` на `SiteUser` (не факт наличия
  assignment'а), включается админом в webvue3 по образцу премиума; гейтинг на фронте (кнопка/редирект) и
  **обязательно** на бэкенде (`PublicSongEditorController`, иначе снятие роли не отзывает доступ к уже
  выданным заданиям). Назначение из карточки/таблицы песен (webvue3) — кнопка «Назначить»/«Назначено»
  (только `idStatus<3`, список — только `is_editor`-пользователи; источник local/server —
  `KaraokeProperty editorAssignmentDefaultTarget`); если у песни уже есть маркеры, `assign()` спрашивает
  подтверждение на их очистку В ЗАДАНИИ (не в самой песне). Задания «на проверке» видны админу сразу —
  светофор (`SubmittedAssignmentsCheck`, см. выше) + числовой бейдж на пункте меню «Задания редактора»
  в `App.vue` (`POST /api/songeditor/submittedcount`, опрос раз в 20с, по образцу бейджа чата). Детали →
  memory `project_song_editor`, `project_site_user_editor_role`.
- **«Избранное» и «Плейлисты» + плейлист автора.** `tbl_site_playlists`(+`_items`); «Избранное» = плейлист с
  `is_favorites=true`. Не-премиум: только «Избранное». Гейтинг по премиуму — плейлисты **скрываются**, не
  удаляются. Плеер плейлиста = тот же `/player/:id` в iframe с `?pl=1` (очередь/авто-переход внутри iframe,
  токен следующего трека «точно в срок»). Плейлист автора — динамический read-only. Иконки
  `FavoriteIcon.vue`/`PlaylistIcon.vue` (изначально только в таблицах Закрома/Поиска) переиспользованы и на
  странице песни (`SongView.vue`) через опциональный проп `label` — размещены прямо в сетке карточки
  метаданных (доп. grid-item рядом с «Тональность»/«Темп»), не отдельным блоком. На странице песни (в
  отличие от таблиц) нет автоматического наполнения `usePlaylistMembership` — обязателен ручной вызов
  `.load([id])` при смене текущей песни, иначе иконки зависают в `off`. Детали → memory
  `project_favorites_playlists`, `project_song_page_favorites_playlist`.
- **«Чат с автором проекта».** Личная переписка премиум-пользователей сайта с автором — один
  непрерывный тред на пользователя (append-only), `tbl_site_chat_messages`. Изначально жил только
  на PROD (пользователи пишут через karaoke-web, автор правит из webvue3 через `target=remote`, без
  sync), позже заведён в универсальный sync-движок (`chatmessages`, SERVER_TO_LOCAL) — см. выше.
  Пользователь пишет из `/account/chat` (karaoke-public) — раздел целиком premium-only (и чтение
  истории, и отправка, и счётчик непрочитанных гейтятся `isEffectivePremium`; без премиума —
  карточка-апселл вместо ленты, `fetchMessages()` не вызывается вовсе). Автор — из раздела «Чат» в
  webvue3: двухпанельный вид (список тредов слева + поиск
  пользователя по email/имени для начала диалога вручную, не только ответом на уже написавшего),
  переключатель `target` для локальной отладки. Live-обновление — **не SSE** (сообщения от юзеров
  создаёт `karaoke-web`, а не `karaoke-app` — `save()` не шлёт уведомление), а polling с обеих
  сторон + плавающий индикатор непрочитанных на каждой стороне (глобальный синий бейдж в паблике —
  виден на любой странице, кроме самого чата и плеера; отдельная кнопка в шапке webvue3 рядом со
  светофором `MonitorLight`). Дизайн — единый мессенджер-стиль (Telegram/WhatsApp) в обоих
  интерфейсах: композер прибит к низу окна, textarea авто-растёт до 1/4 экрана, фон — логотип сайта
  бесшовной «кирпичной» плиткой в шахматном порядке (SVG с base64-встроенным PNG — data-URI SVG со
  ссылкой на ВНЕШНИЙ файл внутри `background-image` браузеры не подгружают). Детали → memory
  `project_chat_with_author.md`.
  **Автоприветствие при первом премиуме.** `SiteUser.sendWelcomePremiumMessageIfNeeded()` (флаг
  `welcomeMessageSent`/`welcome_message_sent`) шлёт в этот чат от лица автора одноразовое
  приветственное сообщение при первом переходе пользователя в `isEffectivePremium` — вызывается из
  трёх мест получения премиума (оплата подписки на сайт через ЮKassa, акционная 100%-подписка,
  Sponsr-sync); подписка на песню (`SCOPE_SONG`) не триггерит, т.к. не даёт `isEffectivePremium`.
  Миграция задним числом ставит флаг уже действующим премиум-пользователям, чтобы им не пришло
  сообщение при ближайшем продлении/повторном Sponsr-sync. Детали → memory
  `project_welcome_premium_message.md`.
- **«Новости».** Табличная лента + ненавязчивое всплывающее уведомление о новом контенте/функционале
  (новая песня в эфире/премиуме, новая фича). `tbl_news` (`title/body/category/link/publish_at`) готовится
  на LOCAL из webvue3 (раздел «Новости») и уезжает на прод штатным sync-движком (`news`,
  LOCAL_TO_SERVER, как `dictionaries`) — в отличие от Чата, никогда не пишется напрямую в PROD.
  «Опубликовано» — не отдельный статус и не `@Scheduled`-джоба, а вычисляемое условие
  `publish_at <= now()` (тот же приём, что `Settings.onAir`): подготовленная и заранее
  синхронизированная на прод новость сама «выходит» в момент `publish_at`, публичные запросы просто
  фильтруют по времени. Публичное чтение — `PublicNewsController` (karaoke-web, БЕЗ
  `SiteAuthInterceptor` — новости видны и анонимам). Уведомление — `NewsBell.vue` (karaoke-public,
  глобальный плавающий колокольчик + авто-исчезающий тост, по образцу `ChatUnreadBadge.vue`, но виден
  ВСЕМ посетителям, не только залогиненным): «прочитано» — не на сервере, а `last-seen id` в
  `localStorage` (работает и для анонимов), позиция `top:64px; right:74px`, чтобы не перекрываться ни с
  шапкой, ни с уже существующим `ChatUnreadBadge`. Скрыт на `/news` и на плеере. Детали → memory
  `project_news_feature.md`.
- **Монетизация (подписки).** Три способа премиум-доступа — везде термин **«подписка»**, не «покупка»:
  Sponsr-sync (`SponsrSyncService`, karaoke-app), подписка на сайт (периодическая, автопродление по
  умолчанию, `SubscriptionRenewalScheduler` в karaoke-web) и подписка на песню (бессрочная, гейт в
  `PublicPlayerController.subscribedToSong`). Единый платёжный конвейер через ЮKassa (`tbl_price_tariffs`/
  `tbl_promo_rules`/`tbl_subscriptions`), redirect-оплата + вебхук (статус всегда перезапрашивается у
  ЮKassa, не из тела). Инвариант: наружу никогда не отдаётся файл/аудио — только онлайн-плеер; меню
  «Открыть/Сохранить файл» скрыто в публичной копии плеера. `PaymentService.hasCredentials()` безопасно
  блокирует кнопку «Оплатить» на фронте, пока ключи ЮKassa не заданы. Оферта — `/oferta`. ЮKassa одобрена
  и работает на проде с 2026-07-09. **Автопродление подписки на сайт временно отключено**
  (`saveMethod=false` в `PublicSubscriptionController.kt`) — сохранение платёжных данных даёт `403` от
  ЮKassa, требует отдельного разрешения в их кабинете; вернуть флаг, когда разрешение получено.
  **Идемпотентность повторного клика (scope=SITE):** `PublicSubscriptionController.create()` перед
  созданием новой подписки ищет уже существующий незавершённый (`PENDING`) заказ пользователя на тот же
  тариф (`Subscription.findPendingSite`) и, если платёж по нему у ЮKassa всё ещё `pending`, возвращает
  тот же `confirmationUrl` вместо создания дубля — до фикса 2026-07-09 двойной клик «Оформить» плодил
  отдельные PENDING-подписки и отдельные платежи в ЮKassa. Детали →
  memory `project_monetization_subscriptions`, `project_site_subscription_double_click_duplicate`.
- **Корзина** — пакетная покупка нескольких подписок на песни одним заказом (второй путь параллельно
  мгновенной покупке через золотую иконку плеера). `tbl_cart_items`, `tbl_subscriptions.order_id` (общий
  для всех позиций заказа), акция `CART_BULK_PERCENT` (скидка от N штук в заказе, отличается от `NTH_FREE`
  тем, что считает только текущий заказ, а не все покупки пользователя). Детали → memory
  `project_cart_checkout`.
- **Постоянная скидка** — процент на `SiteUser`, выставляется вручную админом, суммируется ПОВЕРХ
  результата любой акции (не конкурирует с `tbl_promo_rules`) и применяется к любому заказу.
  `PriceService.applyPersonalDiscount()` — единая точка для обеих веток `computePrice`/`computeCartPrice`.
  Детали → memory `project_personal_discount`.

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

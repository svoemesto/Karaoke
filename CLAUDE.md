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

**Важно: `deploy_web.sh` синхронизирует ТОЛЬКО jar/образ karaoke-web (gradle build → push → pull).
Он не трогает `docker-compose-web.yml`/`80to8897`/`do.sh` на сервере.** Если фикс требует одновременно
новый код И новую переменную окружения/конфиг nginx — сначала (или вместе) выполнить
«Синхронизацию серверных конфигов» ниже, иначе контейнер пересоздастся по старому compose-файлу без
новой переменной, и баг будет выглядеть неисправленным даже после успешного редеплоя. После рестарта
контейнера проверять реальное значение переменной: `docker exec karaoke-web env | grep <VAR>`.

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
- karaoke-app на сервере **не разворачивается вовсе** — только БД, karaoke-web, karaoke-public, storage
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

**Demucs на GPU (`argsDemucs2`/`argsDemucs5` в `Settings.kt`, `deploy/karaoke-app/DockerfileDemucs`).**
Локальная машина (`karaoke-app`) имеет физическую видеокарту (RTX 4060 Ti) и уже настроенный GPU
passthrough в Docker (`nvidia-container-toolkit` на хосте, `/var/run/docker.sock` смонтирован в
`karaoke-app` через реальный **`docker-compose-app-new-comp.yml`** — не через `deploy/docker-compose-app.yml`
из репозитория, это два разных файла, реально используемый лежит вне репо в `/sm-karaoke/system/deploy`).
`argsDemucs2()`/`argsDemucs5()` принимают параметр `device: String = "cuda"` (по умолчанию GPU) и добавляют
`--gpus all` в команду `docker run`, только когда `device == "cuda"`; сами shell-скрипты `demucs2`/`demucs5`/
`demucs7` (`deploy/karaoke-app/files/`) принимают параметр `-device cpu|cuda` (по умолчанию `cpu` внутри
скрипта — обратная совместимость, если параметр не передан). `deploy/karaoke-app/DockerfileDemucs` —
восстановленный (рецепт сборки образа `svoemestodev/demucs:latest` изначально нигде не был закоммичен;
реконструирован через `docker history` уже работавшего образа) и теперь закоммиченный Dockerfile;
`do.sh build_demucs`/`push_demucs` его собирают/пушат. Две version-специфичные ловушки при апгрейде GPU,
на будущее:
- **torch 1.13.1+cu117 не поддерживает архитектуру Ada Lovelace (RTX 40xx)** — `torch.stft` (используется в
  спектрограмме Demucs) падает с `RuntimeError: cuFFT error: CUFFT_INTERNAL_ERROR`. CUDA 11.8 добавила
  поддержку Ada, поэтому используется `torch==2.1.2+cu118`/`torchaudio==2.1.2+cu118` с отдельного индекса
  `download.pytorch.org/whl/cu118` (сам Demucs на закреплённом коммите `requirements.txt` разрешает
  `torch>=1.8.1`, `torchaudio<2.2` — верхней границы на torch там нет, апгрейд безопасен).
- **torchaudio ≥2.1 требует явно установленного `soundfile`**, иначе падает с `TypeError: save() got an
  unexpected keyword argument 'encoding'`. Причина: `demucs/audio_legacy.py` форсирует старый (deprecated)
  backend API torchaudio для версий ≥2.1 (`os.environ["TORCHAUDIO_USE_BACKEND_DISPATCHER"] = "0"` +
  `importlib.reload`), а под старым API без зарегистрированного бэкенда (`soundfile`/`sox_io`) `torchaudio`
  подставляет пустышку `save()`, не принимающую `encoding`/`bits_per_sample`. `soundfile` уже перечислен в
  `demucs/requirements.txt`, но `pip install -e .` (через `setup.py`) его не подтягивает — установлен явно
  в `DockerfileDemucs`.

**Поиск "оригинала" при добавлении файлов из папки (`doCreateFromFolder` в `ApiController.kt`,
`findDuplicateOriginal()` в `Utils.kt`).** После `Settings.createFromPath()` для каждой новой песни ищется
уже существующая в базе песня с тем же названием без учёта содержимого в скобках (regex `\([^)]*\)`,
регистронезависимо) — сначала у того же автора, если не найдено — среди всех авторов. Кандидатом считается
любая песня с непустым `sourceText` (`idStatus` не важен); при нескольких совпадениях берётся запись с
наименьшим `id`. Если оригинал найден — в новую песню копируются `sourceText`/`resultText`/`sourceMarkers`,
`rootId` = id оригинала, `idStatus` = 1. Если не найден — в отдельном потоке (`kotlin.concurrent.thread`,
не блокируя HTTP-ответ) запускается тот же `getSearXNGSearch()`, что и по кнопке "Найти текст песни" в
`SongEdit.vue`. Отдельная от этого функция `findAndFillDublicates()` (кнопка "Найти и обработать дубликаты
песен автора") — более узкий ручной инструмент: ищет только у одного автора и только среди песен со
статусом DONE (6), не трогать её при доработке `findDuplicateOriginal()`. Копирование полей + `rootId` +
`idStatus` вынесено в общую `applyDuplicateOriginal(newSettings, original)` (`Utils.kt`) — переиспользуется
и здесь, и в ручной кнопке ниже.

**Ручной поиск оригинала на форме песни + модалка "Похожие версии песни" (`SongEdit.vue`,
`FamilySongsModal.vue`).** Два независимых инструмента на форме редактирования песни, оба про одну и ту
же задачу — переиспользование уже готового текста/маркеров из связанной песни:
- Кнопка с иконкой `icon_find_original.svg` в подвале (рядом с "Найти текст песни") видна только при
  `song.idStatus === 0`. По клику (через `CustomConfirm`) дёргает `POST /api/song/findoriginal` —
  та же логика `findDuplicateOriginal()`/`applyDuplicateOriginal()`, что при импорте из папки, только
  для одной, уже существующей песни. Если оригинал не найден — синхронно (блокируя HTTP-ответ, как и
  старая кнопка "Найти текст песни") запускает `getSearXNGSearch()`.
- Кнопка "Похожие версии песни" открывает `FamilySongsModal.vue` — список песен из той же "семьи".
  Бэкенд: `findFamilySongIds()` (`Utils.kt`) собирает ключи `{currentId, currentRootId}` (без нулей) и
  ищет все песни, у которых `id` или `root_id` входит в этот набор (сама текущая песня из результата
  SQL исключается, но контроллер `GET /api/song/familysongs` добавляет её обратно вручную, чтобы показать
  в общем списке для контекста). Разница длительности считается через `Settings.ms` (кэшируемое в
  `song_ms`, ffprobe) — **не** через `songLengthMs` (это максимум маркера `END`, ломается на песнях без
  разметки, а тут кандидаты как раз часто без неё). Сортировка — по году (`sortedBy { it.year }`).
  "Оригинал" в списке — строка с `id == rootId текущей песни` (или `id` самой песни, если у неё
  `rootId == 0`, т.е. она сама корень) — жирный шрифт + жёлтый бейдж. Текущая песня — задизейблена
  (серый текст, не кликается, у `select()` ранний `return` по `song.current`). Клик по любой другой
  строке переиспользует существующий `POST /api/song/copyfieldsfromanother` с фиксированным набором
  `SOURCE_TEXT;RESULT_TEXT;SOURCE_MARKERS` (тот же эндпоинт, что у кнопки "Скопировать поля из другой
  песни") — отдельного copy-эндпоинта под модалку не заводили.
- **Ловушка (уже наступали раньше на `SiteUserDto.isPremium`):** `FamilySongDto` изначально имел поля
  `isOriginal`/`isCurrent` — Jackson по бин-конвенции сериализует `val isX: Boolean` в JSON-ключ без
  префикса `is` (`"original"`/`"current"`), а фронтенд читал `s.isOriginal`/`s.isCurrent` → всегда
  `undefined` → бейдж/жирный/disabled тихо не работали, без единой ошибки в консоли или логах. Исправлено
  переименованием полей без `is`-префикса (`original`, `current`) на обеих сторонах. **Правило на будущее:
  новый Kotlin `data class ...Dto` с булевым полем сразу называть без префикса `is`.**
- **CSS-ловушка:** `.fsm-table td { text-align: center }` (класс+тег, специфичность 0,1,1) перебивал
  одиночный класс `.fsm-col-left { text-align: left }` (0,1,0) на колонках "Альбом"/"Название" — понадобился
  более специфичный селектор `.fsm-table td.fsm-col-left`. Заголовок (`th`) — везде по центру, тело
  таблицы — по центру, кроме `Альбом`/`Название` (по левому краю).

**Thread-лейны (`threadId`) в `KaraokeProcess`/`KaraokeProcessWorker`.** `threadId` группирует задания в
независимые последовательные очереди: `KaraokeProcess.getProcessesToStart()` выбирает не более одного
`WAITING`-задания на каждый `thread_id` (SQL `ROW_NUMBER() OVER (PARTITION BY thread_id ...)`), а
`KaraokeProcessWorker.doStart()` не стартует новый поток для `threadId`, пока предыдущий с этим же `threadId`
ещё `isAlive` — задания с одинаковым `threadId` гарантированно выполняются строго друг за другом. Именованные
константы — `KaraokeProcess.THREAD_LANE_HEAVY_RENDER = 0` (MELT_*, DEMUCS*, SHEETSAGE и т.п. — тяжёлые задачи,
нельзя гнать параллельно), `THREAD_LANE_LIGHT_BACKGROUND = -1` (SmartCopy, `UPLOAD_TO_LOCAL_STORE` — быстрые
фоновые задачи), `THREAD_LANE_REMOTE_STORE_UPLOAD = -2` (`UPLOAD_TO_REMOTE_STORE` — сетевая загрузка может быть
медленной, отдельный лейн, чтобы не блокировать лёгкий `-1`). Задания редактирования конкретной песни (MELT_LYRICS
и т.п. из `webvue3`) используют свой `threadId`, приходящий из фронтенда — не путать с этими тремя зарезервированными
именованными константами.

**"Функциональные" задания (`runFunctionWithArgs`) — типы `KEY_BPM_FROM_FILE` (последний шаг),
`UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE`.** В отличие от остальных типов, эти не запускают OS-подпроцесс,
а вызывают Kotlin-функцию напрямую внутри `KaraokeProcessThread.run()`. Диспетчеризация двухуровневая: строка
`args[0][0] == "runFunctionWithArgs"` отличает функциональную строку от подпроцессной (нужно, т.к. `KEY_BPM_FROM_FILE`
через `separate()` разбивается на несколько дочерних строк с одним и тем же `type`, где только последняя —
функциональная); а вот какую именно функцию вызвать — решается по `karaokeProcess.type`
(`KaraokeProcessTypes.valueOf(...)`), а не по строке. Параметры (`args[2..]`) кодируются как `"key=value"` и
парсятся `parseRunFunctionWithArgsParams()` (`Utils.kt`) в `Map<String, String>` — именованный доступ вместо
позиционных индексов; `executeGetKeyBpmFromFile`/`executeUploadToLocalStore`/`executeUploadToRemoteStore`
принимают эту map и возвращают `Boolean` успеха (в отличие от `false`-по-умолчанию раньше, отсутствие `Settings`
теперь корректно приводит к статусу `ERROR`, а не молчаливому `DONE`). Вся эта ветка обёрнута в `try/catch`
(зеркально ветке подпроцессов) — исключение больше не оставляет запись зависшей в `WORKING` навсегда.

**Прогресс загрузки файлов (`uploadToLocalStore`/`uploadToRemoteStore`).** MinIO Java SDK (8.6.0) не имеет
`ProgressListener`, но `PutObjectArgs.stream()` принимает обычный `InputStream` — прогресс получается через
`CountingInputStream` (`karaoke-app/.../CountingInputStream.kt`, простой `FilterInputStream`-счётчик байт),
которым оборачивается файл перед вызовом `KaraokeStorageService.uploadFile(file: InputStream, size)`. Для
удалённой загрузки (`StorageApiClient`/WebClient multipart, `services/StorageApiClient.kt`) файл по-прежнему
читается целиком в память (`Files.readAllBytes`), но `ByteArrayResource.getInputStream()` переопределён так,
чтобы возвращать ту же обёртку-счётчик — WebClient читает `Resource` поблочно при сериализации multipart-тела,
так что прогресс отражает реальную скорость передачи по сети (с обратным давлением), а не мгновенное чтение из
памяти. Прогресс пишется в `KaraokeProcessThread.percentage` (`String`, `"---"` = неизвестно) — существующий
механизм (`getDiff()`/`save()`/SSE в `doStart()`) сам доносит изменения до UI, ничего дополнительно дёргать
не нужно.

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

**Vuex-паттерны в Songs/store.js:**
- **Мутации — только sync.** Весь async (XHR, dispatch) — только в actions. Мутации содержат исключительно
  синхронное присвоение `state.*`. Если нужно разбить действие на «установить ID сразу» + «установить данные
  после загрузки» — делать две отдельных мутации (пример: `setCurrentSongIdOnly` + `setCurrentSongData`).
- **Action `setCurrentSongId` полностью async.** Загружает полный объект `Song` через POST `/api/song` (если
  нет в `songPages`) внутри action с `await`, возвращает Promise. Вызывающий код может `await dispatch(...)`.
- **HR-запросы из SongsTable идут через очередь** (`_enqueueHrRequest` / `_processHrQueue`, лимит
  `HR_MAX_CONCURRENT=3`). Это не даёт 50 одновременным запросам заполнить browser connection pool (~6 слотов
  HTTP/1.1) и заблокировать `/api/song`. При клике на песню: `hrQueue = []` → `await dispatch('setCurrentSongId')`
  → `isSongEditVisible = true` → `updateHealthReportForCurrentPage()` (возобновление очереди для оставшихся).
- **`state.songsDigest`** — lightweight дайджесты (без полного тела Song), загружаются через
  POST `/api/songsdigests`. Полный объект Song грузится лениво при клике в action `setCurrentSongId`.
- **HR-обновление таблицы** идёт через SSE-нотификацию (`SseNotification.healthReports`) с бэкенда,
  обрабатывается в `App.vue` → мутация `healthReportMessageByUserEvent`. Action `setCurrentSongHealthReports`
  обновляет `state.currentSongHealthReports` только если `currentSongId === currId` (не перетирает чужими данными).

**Storage.** Generated media files (audio stems, videos, pictures) live in MinIO-compatible object storage,
accessed through `services/StorageApiClient.kt` / `services/KaraokeStorageService.kt` and the corresponding
`StorageController`/`docker-compose-storage.yml`.

**MinIO на отдельном сервере (89.125.103.63) — MTU-проблема и обязательный паттерн для karaoke-web.**
Docker-контейнер karaoke-web на сервере (79.174.95.69) имеет MTU bridge=1500, физический ens3=1450.
Java MinIO SDK внутри Docker зависает навсегда при обращении к 89.125.103.63:9000 из-за дропа пакетов.
**Правило:** в `karaoke-web` никогда не использовать `KaraokeStorageService` для чтения/проверки файлов
из MinIO напрямую. Вместо этого — HTTP через nginx-прокси на хосте (порт 80 `80to8897`):
- `fetchFromMinIO(storageKey)` — GET через `http://minio-proxy/minio/karaoke/$encodedPath` → `ByteArray?`
- `existsInMinIO(storageKey)` — HEAD через тот же путь → `Boolean`
- Для браузерного контента — 302 редирект на `/minio/karaoke/$encodedPath` (nginx HTTPS)
`minio-proxy` резолвится в `172.17.0.1` (Docker gateway = хост) через `extra_hosts` в `docker-compose-web.yml`.
Nginx порт 80 имеет `server_name ... minio-proxy` чтобы запросы с `Host: minio-proxy` не уходили
в nginx `default_server` (symlink `/etc/nginx/sites-enabled/default` присутствует на сервере).
`Host` — restricted header в Java `HttpURLConnection`, нельзя переопределить через `setRequestProperty`.
Реализация: `PublicApiController.fetchFromMinIO()`, `PublicPlayerController.existsInMinIO()`/`fetchFromMinIO()`.

**Тот же MTU-баг проявляется на ЛЮБОМ исходящем HTTPS-вызове karaoke-web на внешний хост, не только
MinIO** — подтверждено вторым независимым случаем: регистрация на сайте падала с "капча не пройдена"
из-за `SslHandshakeTimeoutException` при обращении к `smartcaptcha.yandexcloud.net` напрямую из
контейнера (TLS handshake — один из немногих видов трафика с пакетами >1450 байт, поэтому именно на
нём проявляется чёрная дыра, а не на мелких внутрисетевых HTTP-запросах). Фикс — тот же паттерн,
переиспользован тот же алиас `minio-proxy`: новый `location /smartcaptcha/` в блоке `server_name ...
minio-proxy` (`80to8897`), проксирующий на `https://smartcaptcha.yandexcloud.net/` (литеральный
`proxy_pass` тут безопасен — резолвится системным DNS хоста при старте, не Docker embedded DNS);
`captcha.proxy-url` в `application.yml` (дефолт — прямой адрес Yandex, для локальной разработки, там
MTU-проблемы нет) + `CAPTCHA_PROXY_URL=http://minio-proxy/smartcaptcha` только в серверном
`docker-compose-web.yml`. **Для любого нового внешнего API, которое должен дёргать karaoke-web с
прод-сервера — сразу закладывать этот paттерн (env-переменная с дефолтом на прямой адрес + host-nginx
`location` под алиасом `minio-proxy`), не дожидаясь, пока баг проявится в проде.**

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

**Repair/RepairAll в HealthReport идут через очередь `KaraokeProcess`, не напрямую.** В
`actionsLocalStorage`/`actionsRemoteStorage` загрузка файла в хранилище (случаи «файл есть на диске, но
отсутствует в хранилище», «устарел», «восстановление через временный файл») ставит задачу
`KaraokeProcess.createProcess(action = UPLOAD_TO_LOCAL_STORE/UPLOAD_TO_REMOTE_STORE, doWait = true,
prior = -2, threadId = THREAD_LANE_LIGHT_BACKGROUND/THREAD_LANE_REMOTE_STORE_UPLOAD)`, а не вызывает
`storageService.uploadFile`/`storageApiClient.uploadFile` напрямую в потоке HTTP-запроса — иначе
`repairAll` (шлёт запросы без ожидания друг друга) блокирует поток контроллера на время сетевой
загрузки. Удаление файла из хранилища (ветка `!canBe`) осталось синхронным — быстрая metadata-операция.
Ветка «восстановление из другого хранилища через временный файл» использует context-флаг
`deleteAfterUpload=true` — временный файл удаляется самой асинхронной задачей после успешной загрузки
(`executeUploadToLocalStore`/`executeUploadToRemoteStore` в `Utils.kt`), а не отдельной синхронной
лямбдой сразу после постановки в очередь (которая раньше удаляла файл до того, как воркер успевал его
прочитать).

**Дедупликация `KaraokeProcess.createProcess()` для `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE`
учитывает `karaokeFileType`, а не только `(settings_id, process_type, thread_id)`** — иначе `repairAll`
для одной песни с несколькими проблемными файлами (например, не хватает и вокала, и аккомпанимента)
затирал задачу одного файла задачей другого при одинаковых `process_type`/`thread_id`. Ключ дедупликации
и in-progress-проверки — `process_args LIKE '%karaokeFileType=X%'`, **не** `pathToFile` (у временного
файла из ветки restore каждый пересчёт генерирует новый случайный путь — поиск по нему никогда не найдёт
уже запущенную задачу). Заголовки `CREATING`/`WAITING`/`WORKING` считаются «в процессе» (статус
`IN_PROGRESS`, "Уже есть задание на загрузку файла"), `DONE`/`ERROR` — нет: завершённые записи
`tbl_processes` не удаляются (см. `KaraokeProcessWorker.kt`, `delete()` после DONE закомментирован),
поэтому фильтр по статусу обязателен, иначе после первой же успешной загрузки файл вечно показывал бы
«уже есть задание».

**`description` у `HealthReport` обязан включать location-суффикс** (`"${description}/${location.name}"`,
добавляется один раз в диспетчере `actions()`, строки ~42-132). Без этого local- и remote-варианты одной
и той же проблемы одного файла (`karaokeFileType`) неотличимы по `(type, status, description)`, и
`HealthReport.getHealthReport()` при двух почти одновременных repair-запросах (typично для `RepairAll`,
который не ждёт ответа между запросами) резолвит **оба** запроса в одну и ту же (первую по списку
locations) запись — на практике: `RepairAll` создавал только `UPLOAD_TO_LOCAL_STORE`-задачи, а
`UPLOAD_TO_REMOTE_STORE` появлялись только при повторном клике, когда local-проблема уже пропадала из
списка ошибок. Тот же паттерн `"${karaokeFileType.name}/${x.name}"` уже применялся для
`PICTURE_PUBLICATION`/`SongVersion` — не изобретать новый.

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
- `GET /api/song/{id}/fileminus.mp3` / `filevoice.mp3` / `filebass.mp3` / `filedrums.mp3` — стемы FLAC→MP3 с кешем (рядом с flac)
- `GET /api/song/{id}/playerdata` — JSON: `{songName, author, album, bpm, markers, audioAccompanimentUrl, audioVocalsUrl, audioBassUrl, audioDrumsUrl, exportBaseName}`
  - `markers` = `settings.sourceMarkersList` (`List<List<SourceMarker>>`)
- `GET /api/song/{id}/playerfile` — экспорт `.smkaraoke` (ZIP: manifest.json + все доступные стемы + картинки)
- `pushMp3ToStorage()` — после каждой конвертации FLAC→MP3 в admin-эндпоинтах лениво заливает стем в MinIO
  (бакет `karaoke`), ключ строго через `KaraokeFileType.suffix`/`.extention`
  (`"${settings.storageFileName}${fileType.suffix}.${fileType.extention}"` — `suffix` уже содержит точку,
  например `.accompaniment`, **не** дефис). Это единственный канал, которым наполняется MinIO для
  публичного плеера — пока админ ни разу не открыл песню в `webvue3`-плеере, стемов в MinIO нет.

**Секретный публичный плеер (`karaoke-public`, скрытый механизм — не документировать в клиентском коде/UI):**
- Триггер: тройной клик с зажатым Shift по полю "Тональность" на странице песни (`SongClassic.vue`/`SongModern.vue`,
  метод `onMetaClick('key', $event)`). На полях Альбом/Исполнитель/Год висят точно такие же обработчики-приманки
  (`onMetaClick('album'|'author'|'year', ...)`), чтобы реальное поле-триггер нельзя было вычислить по коду страницы.
- Вся логика подсчёта кликов/окна/порога — **на бэкенде** (`karaoke-web`, `PlayerGestureUnlockService`,
  in-memory `ConcurrentHashMap`, НЕ в БД — чтобы не тянуть это в LOCAL↔SERVER синхронизацию и не оставлять
  следов в бэкапах): `TARGET_FIELD="key"`, `REQUIRED_CLICKS=3`, `CLICK_WINDOW_MS=1500`, `TOKEN_TTL_MS=30мин`.
  Клики шлются на уже существующий `POST /api/public/events` (`eventType=clickToLink, linkType=songMeta`),
  который теперь возвращает `{ok, meta}` — `meta` содержит токен только когда жест распознан.
- `/player/:id` в `karaoke-public` (роут + все бэкенд-эндпоинты `PublicPlayerController`,
  `/api/public/player/{id}/...`) требуют `?token=` (провалидирован `PlayerGestureUnlockService.validateToken`).
  Без токена или с чужим/просроченным — везде **404**, не 401/403, чтобы сам факт существования механизма
  не был виден по ответам API. Токен кладётся в `sessionStorage` (`kp_token_{id}`) и открывается в новой
  вкладке (`window.open`, не `router.push` — иначе плеер наследует classic/modern layout-обёртку и не
  занимает весь экран, см. `isPlayerPage` в `App.vue`).
- **Критично:** `karaoke-web` не может трогать `Settings.rootFolder`/любые `*NameFlac` геттеры — они тянут
  инициализацию `karaoke-app`'s `ConstantsKt`/`Connection.kt` (`Delegates.notNull<Boolean>()`, инициализируется
  только в `KaraokeAppService.init{}`, а этот `@Service`-бин в `karaoke-web` никогда не создаётся — нет
  `@ComponentScan` до `com.svoemesto.karaokeapp.services`). Результат — `IllegalStateException` →
  `NoClassDefFoundError` (закешированный сбой) на **любой** следующий запрос, роняет весь процесс.
  Этот же класс проблем уже отмечен комментарием в `SettingsPublicDto.kt`. Поэтому `PublicPlayerController`
  читает стемы и картинки **исключительно** через `KaraokeStorageService` (MinIO), никогда не через
  локальные пути.

**Меню плеера (гамбургер, `_buildUI()`/`_buildMenu()`):**
- Пункты-действия ("Открыть файл...", "Сохранить файл") и пункт-подменю ("Экспорт аудио..." → Голос/
  Минусовка/Бас/Ударные) визуально различаются: у подменю — стрелка `▸` и hover, у действий — просто hover.
  Разделитель между действиями и подменю.
- Подменю открывается **и по наведению** (чистый CSS `:hover`), **и по клику** (класс `kp-submenu-open`,
  для тач-устройств) — оба механизма работают одновременно, без JS-таймеров mouseenter/mouseleave.
- "Сохранить файл" и экспорт стемов используют общий `_saveBlob()` (`showSaveFilePicker` с фолбэком на
  `<a download>`).

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

**Процедурный фон (`_generateStarfield` / `_renderBackground` в `KaraokePlayer.js`):**
- Текстура: OffscreenCanvas 4096×4096, генерируется один раз при init (до загрузки данных)
- 6 палитр по `songId % 6`: cool blue, crimson, dark teal, fire orange, purple multi, warm amber
- Туманности: кластеры 65–150 радиальных градиентов → органические облака; тёмные ядра для поглощения
- Звёзды: ~3600 (tiny/medium/bright с glow); seeded RNG через xorshift32
- Viewport 1920×1080 из 4096×4096 (1:1 пикселей, нет upscaling → нет артефактов)
- Движение: triangle-wave через `performance.now()` (периоды 541с X, 379с Y) — **всегда движется**, не зависит от аудио
- **Фон всегда alpha=1** — fade-in/fade-out применяются поверх него отдельным чёрным оверлеем

**Критичный порядок init() в KaraokePlayer.js:**
- `_buildUI()` → `_generateStarfield()` → `_startRenderLoop()` → async data load → `_ready = true`
- Guard в `_renderFrame` — **`!this._ready`**, НЕ `!this.data`!
- Причина: `this.data` выставляется до `_parseMarkers()`, между ними рендер-луп успевает закешировать
  `_computeVoiceLayout` с пустым `voiceLines=[]` → 1 голос вместо 2.
  `_ready=true` выставляется только после `_buildWaveforms()`, когда `voiceLines` уже заполнен.

**Скролл текста после последней строки (`_buildScrollLines()`):** всегда вызывать с `quickEnd=true`
(и для `this.voiceLines` — многоголосый рендер, и для `this.lines` — одноголосый, `nVoices<=1`).
Хедер выезжает обратно сразу после конца последней строки текста (`_getHeaderSlide`, `showStart =
lastTextLine.endTime`), а не в конце трека. Хвостовые пустые строки-заполнители, растянутые на весь
оставшийся хвост трека (`quickEnd=false`), не дают тексту физически проскроллиться на нужные ~11
строк за окно возврата хедера (4 halfNote) — последняя строка остаётся видна во время фейда хедера.
`quickEnd=true` даёт гарантированные `TRAILING=14` пустых строк сразу после конца последней строки,
не зависящие от длины хвоста трека.

**Логотип (`_getLogoAlpha`/`_getEndSequenceAlphas`/`_renderLogo`) — только пока песня не загружена.**
`KARAOKE_LOGO.png` (бокс 40%×40%, `_drawImageFit`) показывается с константной alpha=1 **только** в ветке
`!this._ready` в `_renderFrame` (нет песни вообще — ни через сайт, ни через локальный `.smkaraoke`-файл;
не важно, идёт ли ещё сетевой запрос или файл просто не выбран). Как только `_ready===true`, логотип по
умолчанию не рисуется — вместо него сразу и статично виден сплэш-скрин (см. ниже), без какого-либо fade-in.

`_getLogoAlpha(audioTime)` при загруженной песне отвечает только за один короткий момент: fade-in
логотипа за 0.5с до конца трека (`this.duration - FADE`). Всё, что происходит после естественного конца
трека, не может управляться через `dt`/`audioTime` — `_onEnded()` сбрасывает `dt` в 0 одновременно со
сбросом состояния, поэтому нужен отдельный таймер на реальном времени: `_onEnded()` пишет
`this._endFadeStartedAt = Date.now()`, и `_getEndSequenceAlphas()` на каждый кадр считает
`elapsed = (Date.now() - this._endFadeStartedAt) / 1000` и проигрывает последовательность:
**hold 3с** (логотип полностью виден) → **0.5с fade-out логотипа** → **0.5с fade-in сплэша** → сброс
`_endFadeStartedAt = null` (дальше — обычное idle-состояние: сплэш статично виден, логотипа нет). Это
резкий cut между слоями (сначала гаснет логотип, потом отдельно проявляется сплэш), а не кроссфейд.
Любое действие пользователя (`_play()`, `_seekToDisplayTime()`) немедленно обнуляет
`_endFadeStartedAt`, отменяя недоигранную анимацию.

`_renderSplash()` больше не делает fade-in (раньше `ct<FADE ? ct/FADE : ...` перекрывался логотипом в
idle) — сплэш всегда статично виден на полной альфе с самого начала, только fade-out в конце сплэша (переход
в караоке) остаётся. Параметр `alphaOverride` (домножается на fade-out) — это как раз канал, которым
`_getEndSequenceAlphas()` управляет фазой "fade-in сплэша" после конца трека.

**Шрифт строки "Key:.../bpm:..." на сплэше:** должен быть `FiraSansExtraCondensed` (400, обычный) —
как в Kotlin/MLT-рендере (`splashstartChordDescriptionFont` в `KaraokeProperties.kt`), **не** Roboto.
Файл шрифта грузится через `FontFace` из `/fonts/FiraSansExtraCondensed-Medium.ttf` в обоих копиях
плеера. Раньше в `karaoke-public` эта строка визуально отличалась от `webvue3`, т.к. внешний
Google Fonts `<link>` в `index.html` (нужен для остального UI сайта) случайно подсовывал canvas'у
настоящий Roboto-400, а `webvue3` синтезировал 400 из единственного загруженного начертания 900 —
оба были неверны относительно эталона.

**Экспорт `.smkaraoke` дублируется на бэкенде дважды намеренно:** `ApiController.playerfile` в
`karaoke-app` читает стемы с локального диска (admin), `PublicPlayerController.playerFile` в
`karaoke-web` — те же стемы, но из MinIO (публичный плеер). Держать оба варианта в синхронизации
руками при изменении формата manifest.json/структуры ZIP.

**`webvue3/src/player/` и `karaoke-public/src/player/` — намеренно две копии** `KaraokePlayer.js`
(не общий пакет). Различаются в 3 местах: конструктор принимает `token` 4-м параметром, URL-шаблон
`apiBase` (без `/song/` в karaoke-public), `_getPlayerFileUrl()`. Любое исправление логики плеера
нужно вносить в оба файла.

## Регистрация/авторизация пользователей сайта (`/login`, `/register`, `/account`)

Отдельная от админских логинов (`tbl_users`) сущность — посетители публичного сайта, таблица
`tbl_site_users` (+ `tbl_site_user_tokens`, `deploy/karaoke-db/06_site_users.sql`).

**Токен сессии — персистентный, не JWT.** `SiteUserTokenService` (karaoke-web, чистый JDBC) на каждый
защищённый запрос делает живой SELECT и проверяет `revoked`/`expires_at`/`is_banned` — бан и logout
действуют мгновенно, без ожидания истечения TTL. Защищённые эндпоинты (`/api/public/account/**`,
`/api/public/auth/me|logout`) идут через `SiteAuthInterceptor` (`HandlerInterceptor`), не через Spring
Security filter chain.

**Публичный backend** — `karaoke-web/controllers/PublicAuthController.kt` (`register/login/logout/me/config`)
и `PublicAccountController.kt` (`profile/change-password`). **Админский CRUD** —
`karaoke-app/controllers/SiteUsersController.kt` (`digest/byId/update/ban/unban/delete`), параметр
`target=local|remote` явно выбирает `Connection.local()`/`Connection.remote()` — новый паттерн для ручных
API (раньше выбор БД был только в фоновых sync-джобах). Поле «Премиум» в карточке (webvue3) — обычный
редактируемый чекбокс, временная замена для будущей автоматической Sponsr-сверки (см. ниже).

**Jackson-сериализация булевых `is*`-полей.** В Kotlin data class поле `val isPremium: Boolean` генерирует
геттер `isPremium()`, а Jackson по бин-конвенции отбрасывает префикс `is` при сериализации в JSON — на
выходе ключ `"premium"`, а не `"isPremium"` (аналогично `isBanned` → `"banned"`, уже учтено в
`webvue3/SiteUserEdit.vue`: `siteUserCurrent.banned`, не `.isBanned`). Любой новый фронтенд-код, читающий
такое поле из ответа `SiteUserDto` (или любого другого DTO с булевым `is*`-полем), должен обращаться к
нему без префикса `is` (`user.premium`, не `user.isPremium`) — иначе значение будет тихо `undefined`
и UI-условие всегда ложно, без ошибок в консоли.

**Капча — Yandex SmartCaptcha**, ключи в `tbl_public_settings` (не в файловых `KaraokeProperties` — см.
ниже почему), `karaoke-web/services/CaptchaConfigService.kt` читает их напрямую через `WORKING_DATABASE`.
`YandexCaptchaValidationService` — fail-open (не блокирует регистрацию), если серверный ключ пуст —
осознанное поведение, чтобы регистрация не ломалась намертво до настройки ключей. Прямой HTTPS-вызов
`smartcaptcha.yandexcloud.net` из контейнера на проде виснет из-за MTU black-hole (см. ниже, тот же баг,
что у MinIO) — обход через `captcha.proxy-url`/`CAPTCHA_PROXY_URL` и nginx `location /smartcaptcha/`.

**Поле «Имя» (`displayName`) обязательно** и при регистрации, и при сохранении профиля в личном
кабинете — валидация на бэкенде (`PublicAuthController.register`, `PublicAccountController.updateProfile`:
`displayName.isNullOrBlank()` → `400 {"error":"display_name_required"}`) и клиентская проверка на
фронтенде (`RegisterView.vue`, `AccountView.vue`).

**`karaoke-app` никогда не разворачивается на продакшн-сервере** (см. `## Modules / layout`) — только
на машине администратора. Из этого следует: `KaraokeDbTable.createDbInstance()`
(`model/KaraokeDbTable.kt`) вызывает `SNS.send(...)` **без try/catch**, а `SNS` обычно инициализируется
в `KaraokeAppService.init{}`, которого в karaoke-web нет (нет `@ComponentScan` до
`com.svoemesto.karaokeapp.services`) — первый же `INSERT` через любую `KaraokeDbTable`-модель из
karaoke-web (например, регистрация нового `SiteUser`) уронил бы процесс. Фикс уже внесён —
`karaoke-web/services/KaraokeWebService.kt` дополнительно инициализирует
`SNS = SseNotificationService(objectMapper)` в своём `init{}`. Не убирать при рефакторинге этого файла.

**`tbl_public_settings`** (`deploy/karaoke-db/07_public_settings.sql`) — маленькая key/value таблица в
Postgres специально для настроек, нужных сервисам на сервере (сейчас — только ключи капчи). Не путать
с `KaraokeProperties` (~150 файловых настроек рендеринга, `/sm-karaoke/system/Karaoke.properties`,
существуют только на машине администратора) — для всего, что должно работать в проде, использовать
только `tbl_public_settings` с тем же паттерном `target=local|remote`
(`karaoke-app/controllers/PublicSettingsController.kt`, раздел «Настройки сайта» в webvue3). При
добавлении новой таблицы в `deploy/karaoke-db/*.sql` — применять её и на локальной, и на серверной БД
(79.174.95.69:8832) отдельно, миграция сама на сервер не попадает.

**`webvue3/nginx_webvue3.conf`** — прокси на `karaoke-app` в `location /api` задан через переменную
(`set $karaoke_app_upstream ...; proxy_pass $karaoke_app_upstream/api;`) + `resolver 127.0.0.11 valid=10s
ipv6=off;` (docker embedded DNS), не литералом. Литерал резолвится один раз при старте nginx и роняет
его насмерть (`emerg: host not found`), если контейнер `karaoke-app` в этот момент не поднят — с
переменной резолв ленивый, на каждый запрос, поэтому webvue3 переживает независимый рестарт karaoke-app.
Тот же приём использовать для любого нового `proxy_pass` на другой docker-сервис в этом файле.

## Сетевое окружение машины администратора (Claude Code через VPN)

На машине администратора настроен process-scoped split-tunnel: собственный сетевой трафик Claude Code
(API-запросы, авторизация, телеметрия, WebFetch) идёт через VLESS-VPN, а команды, которые Claude Code
выполняет через Bash-тул (git, npm, docker, curl и т.п.), идут обычным маршрутом, без VPN.

- Headless `xray-core` (пакет `xray-server`) — systemd-сервис `xray.service` (`enabled`, переживает
  перезагрузку), конфиг в `/etc/xray/config/{inbounds,outbounds,routing}.json`. HTTP-inbound (не socks
  — Claude Code не поддерживает SOCKS-прокси) на `127.0.0.1:1081`, VLESS-outbound наружу.
- `~/.bashrc`: функция `claude()` выставляет `HTTPS_PROXY`/`HTTP_PROXY=http://127.0.0.1:1081` перед
  запуском реального бинарника; `claude --novpn` запускает вообще без прокси.
- Глобальный `PreToolUse`-хук на тул `Bash` (`~/.claude/hooks/strip-proxy.sh`, зарегистрирован в
  `~/.claude/settings.json`) переписывает каждую Bash-команду в `env -u HTTPS_PROXY -u HTTP_PROXY ...
  -- bash -c '<original>'`, чтобы дочерние процессы не наследовали proxy. Экранирование оригинальной
  команды — обязательно через `jq -nr --arg c "$command" '$c | @sh'` (флаг `-r` обязателен, иначе
  двойное JSON-экранирование ломает команды с `&&`/`|`/кавычками).
- На машине отдельно установлен `goxray-gui` (system-wide TUN, другая задача пользователя) — не
  пересекается с этой настройкой, трогать не нужно.

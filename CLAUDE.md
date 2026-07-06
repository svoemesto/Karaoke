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

**Синхронизация LOCAL↔SERVER — универсальный движок (`sync/SyncTarget.kt`, `Utils.kt`).**
`updateDatabases()` больше не содержит по блоку кода на каждую сущность — единственная generic-функция
`collectSyncOps()` работает по списку `SyncRegistry.all` (`SyncTarget<T>` на сущность: `settings`,
`pictures`, `authors`, `siteusers`, `events`). Два критичных паттерна производительности (не потеряны
при рефакторинге):
- Сравнение хэшей — через `associateBy { it.id }` + Map lookup (O(n)), **не** через вложенные `.any`/`.none`
  по спискам (O(n²) — при 18858+ записях даёт 3+ минуты задержки).
- Загрузка записей для diff — пакетно через `target.loadByIds(ids, database)` (`WHERE id IN (...)`),
  **не** по одной в цикле (N+1 запросов по сети).
- Пустой список хэшей от `fromDatabase` (не `null`, а именно 0 записей) — `collectSyncOps` возвращает
  `false`, что прерывает **всю** операцию целиком (не только эту сущность): без этой защиты `idsToDelete`
  включил бы вообще все записи `toDatabase` — случайный обрыв сети во время запроса хэшей иначе привёл
  бы к полному удалению целевой таблицы.

`GenericKaraokeDbTableSyncTarget<T: KaraokeDbTable>` реализует все методы через уже существующие generic
reflection-хелперы `KaraokeDbTable.Companion` (`getListHashes`/`loadByIds`/`getDiff`/`delete`) — новую
сущность для sync достаточно зарегистрировать в `SyncRegistry.all`, если она реализует `KaraokeDbTable`
и её поля аннотированы `@KaraokeDbTableField`. Единственное исключение — `SettingsSyncTarget`: `Settings`
теперь формально реализует `KaraokeDbTable` (интерфейс требует всего 6 instance-членов — `database`/
`storageService`/`storageApiClient`/`id`/`getTableName()`/`toDTO()`/`getSqlToInsert()`; `id` стал
`override var` с get/set в `fields`-карту, `save()` — просто `= saveToDb()`), но её generic reflection-
методы **не используются** для sync — её поля намеренно не аннотированы `@KaraokeDbTableField`
(виртуальные diff-поля `status`/`color`/`processColorXxx` с `recordDiffRealField=false`, тяжёлые
side-effect геттеры `ms`/`rootFolder`, вся `tbl_settings_sync`-инфраструктура несовместимы с
reflection-подходом). `SettingsSyncTarget` — bespoke-обёртка поверх уже существующих
`Settings.listHashes`/`loadListFromDbByIds`/`getDiff`/`getSqlToInsert(sync)`/`deleteFromDb`, с тем же
фильтром автопуша (`shouldPush`), что и раньше в `saveToDb()`.

**Ловушка: generic reflection-loader (`KaraokeDbTable.loadList`) падает с NPE на nullable-в-БД
колонках, если Kotlin-поле объявлено non-null.** `rs.getString(...)`/`rs.getTimestamp(...)` возвращают
настоящий `null` на `SQL NULL`, и `property.setter.call(entity, null)` кидает
`"Parameter specified as non-null is null"`, если поле объявлено как `var x: String = ""` вместо
`String?` — этот путь ловит только `PSQLException`, не `NullPointerException`, поэтому ошибка долетает
до контроллера как 500. Столкнулись на `WebEvent` (`tbl_events`, 250k+ строк реальной статистики) —
`link_name`/`event_type`/`rest_name`/`rest_parameters`/`link_type`/`song_version`/`referer`/`last_update`
не имеют `NOT NULL` в БД и реально бывают `NULL` (например `link_name` заполнен только для событий
кликов по ссылкам) — все они должны быть `String?`/`Timestamp?`. Числовые поля (`Int`/`Long`) от этого
не страдают: `ResultSet.getLong()`/`getInt()` возвращают примитивный `0` на `SQL NULL`, а не Kotlin
`null` — `song_id: Long` (не `Long?`) можно было оставить non-null. Любая новая `KaraokeDbTable`-модель
над таблицей, где строковые/timestamp-колонки допускают `NULL` — должна объявлять их nullable.

**Новые сущности sync — `SiteUser`/`WebEvent`.** `SiteUser` уже реализовывал `KaraokeDbTable` и имел
`recordhash`-триггер в БД — включён в реестр "бесплатно". Для статистики создана новая модель
`model/WebEvent.kt` (только для sync, не путать с `StatBySong.kt`/`StatsByEvents` — тот object с ручным
JDBC обслуживает вьюху "Статистика" и не трогается). `deploy/karaoke-db/03_events.sql` был не
синхронизирован с фактической схемой (не хватало `referer`) — приведён в соответствие с
`deploy/recordhash_events.sql` (который ссылается на `NEW.referer`).

**Направления one-click sync** (`SyncTarget.oneClickDirection`): `settings`/`pictures`/`authors` —
`LOCAL_TO_SERVER`; `siteusers`/`events` — `SERVER_TO_LOCAL`.

**Флаги операций per-direction (`SyncOperation`, `sync/SyncTarget.kt`).** Разрешение синхронизации задаётся
не одним флагом на направление, а **8 флагами на сущность** — `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed`
(40 `KaraokeProperty` всего, `KaraokeProperties.kt`). `SyncOperation` = INSERT/UPDATE/DELETE (операции над
**ЦЕЛЬЮ**: добавить/изменить/зеркально удалить отсутствующее в источнике) + **MOVE** («перемещение»: после
подтверждённого переноса удалить перенесённые строки из **ИСТОЧНИКА**). `DELETE` (удаление в цели) и `MOVE`
(удаление в источнике) — про разные БД и по смыслу взаимоисключающи. Направление считается разрешённым
(кнопка активна, проверка 403 в `/api/sync/run`) если включена **хотя бы одна** операция —
`isAllowed(direction)` теперь derived-обёртка над `isOperationAllowed(direction, op)`. Дефолт всех флагов
`false`, **кроме** `events`/pull: `insert/update/move = true, delete = false` (перелив статистики с сервера
на LOCAL + очистка сервера «из коробки»). Это **отдельный** механизм от старых
`allowUpdateRemote`/`allowUpdateLocal` (те по-прежнему гейтят только автопуш `Settings` при сохранении и
мониторинг `tbl_settings_sync` в `KaraokeProcessWorker`).

**Реализация move (`collectSyncOps`/`updateDatabases` в `Utils.kt`).** `collectSyncOps` выводит `direction`
из `toDatabase.name` (`"SERVER"` → push), гейтит блоки INSERT/UPDATE/DELETE по флагам и в режиме move
копит **безопасный** набор для удаления из источника в `listToDeleteFromSource` = реально вставленные +
реально изменённые (прошедшие `shouldPush`) + уже идентичные (равный `recordhash`). Строку, которая
различается, но не перенесена (insert/update выключены), НЕ удаляет — защита от потери данных. Удаление из
источника флашится в `updateDatabases` **после** записи в цель: если источник = SERVER (кейс events pull) —
зашифрованный `DELETE` на `changerecords` (тот же payload `dataDelete`, что зеркальное удаление, но по
строкам источника); иначе (источник = LOCAL) — JDBC по `connFrom`. Возврат `updateDatabases`/`runEntitySync`
— `data class SyncResult(created, updated, deleted, moved)` (component1..4 сохраняет совместимость
legacy-деструктуризации `val (c,u,d) = ...`). **Ни SQL-миграции, ни деплоя на прод не требуется** — флаги в
файловых `KaraokeProperties` (локальны), удаление серверных строк — по уже существующему `changerecords`.

**Чанкование операций синхронизации — «2 оси» (`SyncTarget.rowChunkSize` + `SyncRegistry.DELETE_CHUNK_SIZE`,
`updateDatabases` в `Utils.kt`).** Размер пачки диктуется числом **байт на единицу**, а это по операциям
разные величины, но честных осей всего две (а не матрица table×operation):
- **`rowChunkSize`** (per-table в `SyncTarget`) — для полнострочных READ (`loadByIds`, `SELECT * WHERE id IN`),
  INSERT (`dataCreate`) и UPDATE (`dataUpdate`): их payload ∝ весу строки. Значения: `settings` 25 (самые
  тяжёлые — `source_text`/`result_text`/`source_markers`/`formatted_text_tabs`), `pictures` 50, `authors`/
  `siteusers` 500, `events` 100 (снижен с 500 2026-07-05: `SELECT * WHERE id IN (500)` по remote-
  соединению давал `Read timed out` — часть строк `tbl_events` несёт заметный текст
  `rest_parameters`/`user_agent`/`referer`, а крупные ответы на пути к серверной БД чувствительны к
  размеру пакета; по 100 каждая пачка — отдельный небольшой запрос со своим окном таймаута).
  `SettingsSyncTarget.loadByIds` и `GenericKaraokeDbTableSyncTarget.loadByIds`
  сами бьют список id на пачки по `rowChunkSize`; в `updateDatabases` CREATE/UPDATE-флаши берут
  `rowChunk = min(rowChunkSize)` по синхронизируемым `keys`. **Зачем:** один `SELECT *` на сотни тяжёлых
  настроек по сети упирался в `socketTimeout=30` удалённого соединения (`Connection.remote()`,
  `jdbc:...?socketTimeout=30`) → `PSQLException: Read timed out`. Низкоуровневые `Settings.loadListFromDbByIds`/
  `KaraokeDbTable.loadByIds` намеренно оставлены простыми (один `IN`-запрос) — у них есть локальные
  вызыватели (`ApiController`), которые не таймятся; чанкование живёт в слое sync, где и per-table размер.
- **`DELETE_CHUNK_SIZE = 200`** (общий в `SyncRegistry`) — для зеркального удаления в цели и move-удаления
  из источника: payload `DELETE ... WHERE id=X` крошечный (~40 байт) у любой таблицы, поэтому один большой
  размер. Прежние «чанки по 10» (`if ("pictures" in keys) 1 else 10`) давали ~16900 HTTP-запросов на
  очистку 169k событий при move — с 200 на порядок меньше round-trip'ов. UPDATE отдельного размера не
  получает — едет на `rowChunk` (консервативно). Легаси `setSettingsToSyncRemoteTable` (`tbl_settings_sync`,
  автопуш `Settings.saveToDb`) — отдельный путь, свой `chunkedSize=10`, этой схемой не затронут.

**Ловушка: биндинг `java.sql.Timestamp` в рукописном JDBC UPDATE.** В LOCAL-ветке UPDATE движка
(`Utils.kt`, `collectSyncOps`→`updateDatabases`) `PreparedStatement`-параметры биндятся по типу
`recordDiffValueNew`. Ветка `else -> ps.setString(...)` для `Timestamp` **ломала** UPDATE: `setString`
явно указывает тип параметра `varchar`, а колонка `timestamp` → `PSQLException: column "last_update" is of
type timestamp without time zone but expression is of type character varying`. Нужен явный
`is Timestamp -> ps.setTimestamp(index, v)` (+ `Boolean`/`Double`/`Float`/`null`) — точь-в-точь как в уже
проверенном блоке `KaraokeDbTable.save()` (строки ~74-82). INSERT-путь (`getSqlToInsert`) от этого не страдал:
там значение встраивается строковым литералом `'...'` в текст SQL — у литерала тип `unknown`, Postgres неявно
приводит его к `timestamp`; `setString` такой поблажки не даёт. Правило: любой рукописный `ps.set*` по diff/
reflection обязан покрывать `Timestamp` явной веткой, иначе timestamp-колонки падают на UPDATE (не на INSERT).

**Ловушка: авто-`now()` триггерные колонки (`last_update`) не должны участвовать в diff.** `tbl_events`
несёт **два** `BEFORE UPDATE`-триггера: `update_last_updated_events_trigger` (ставит `last_update = now()`)
и `update_recordhash_events_trigger` (пересчитывает `recordhash`, в который `last_update` **не** входит —
`recordhash_events.sql`). Если в модели поле помечено `useInDiff=true` (дефолт `@KaraokeDbTableField`), то
при любом апдейте строки по другой причине diff тянет `last_update`, а на pull LOCAL-триггер тут же
перезапишет его в `now()` — значение бессмысленно и на сходимость не влияет, но зашумляет UPDATE. Фикс:
`@KaraokeDbTableField(name = "last_update", useInDiff = false)` в `WebEvent.kt` — зеркалит DB-инвариант
(поле вне `recordhash` ⇒ вне diff). Правило: колонка, авто-управляемая триггером `now()` и исключённая из
`recordhash`, должна быть `useInDiff=false`.

**Диагностика «бесконечной» реконсиляции events (2026-07-05) — не всегда рассинхрон функции хэша.**
При первом успешном запуске events-sync казалось, что sync вечно переписывает ~251k строк с diff только по
`last_update`. Прямая проверка (`recordhash` stored vs пересчёт по текущей функции — на **обеих** БД + кросс-
сверка `id|recordhash` LOCAL vs SERVER) показала: функции `update_tbl_events_recordhash()` на LOCAL и SERVER
**идентичны**, обе БД самосогласованы, для пересекающихся id хэши совпадают 1:1. Реальная причина «шторма» —
varchar-баг выше блокировал UPDATE, а как только он был исправлен, прошла **разовая** реконсиляция (move
подчистил сервер 251551 → 169357 и далее). Бэкфилл `recordhash` на проде **не понадобился**. Урок: прежде
чем гнать тяжёлый `UPDATE ... SET recordhash=...` по проду, проверить хэши напрямую — расхождение может быть
транзиентным (переходным), а не структурным.

**API**: `GET /api/sync/entities` (сущности с `allowPush`/`allowPull`/`oneClickDirection` + 8 булевых
`pushInsert/…/pullMove`), `POST /api/sync/run` (`key`, `direction=PUSH|PULL`, опц. `id`; результат c `moved`),
`POST /api/sync/oneclick`, `POST /api/sync/setflag` (`key`, `direction`, `operation`, `value` — переключение
одного флага, наименование ключа инкапсулировано в бэкенде через `operationPropertyKey`). Старые точечные эндпоинты (`/utils/updateremotesettingsfromlocaldatabase`,
`/utils/updateremotepicturefromlocaldatabase`, `/utils/tosync` — per-record кнопки в `SongEdit.vue`) и
старые bulk-обёртки (`updateRemoteDatabaseFromLocalDatabase`/`updateLocalDatabaseFromRemoteDatabase`,
всё ещё дергаемые из `Settings.saveToDb()`/`KaraokeProcessWorker`) не изменены — внутри транслируют
старые bool-флаги в `keys: Set<String>` нового generic `updateDatabases()`.

**webvue3**: `components/Sync/SyncTable.vue` + `store.js` — таблица сущностей с двумя группами столбцов
«→ Server (push)»/«← Local (pull)», в каждой по 4 чекбокса-флага (Доб/Изм/Уд/Пер, `@change` →
`setSyncFlagPromise` → `/api/sync/setflag`, ответ заменяет одну строку в списке без перезагрузки),
кнопками запуска «→ Server»/«← Local» (disabled по `allowPush`/`allowPull`) и кнопкой «Синхронизация в
1 клик»; `showResultAlert` показывает и «перемещено (удалено из источника): N». Таблица шире прежней
колонки `HomeView` — поэтому `.home` расширен `500px→780px`, а верхние контролы обёрнуты в
`.home-controls` (фикс. `500px`, центрированы через `align-items:center` у `.home-wrapper`), чтобы синие
кнопки не растянулись; сам `SyncTable` идёт на всю ширину (`width:100%` + `overflow-x:auto`). Встроен в
`HomeView.vue` взамен старых 6 хардкод-кнопок (`updateRemoteSettings/Pictures/Authors`,
`updateLocalSettings/Pictures/Authors` — методы и `data`-поля `allowUpdateRemote`/`allowUpdateLocal`
удалены из `HomeView.vue` как мёртвый код; сами actions в `Songs/store.js` оставлены нетронутыми —
использовались только этим блоком, но их удаление не входило в объём рефакторинга).

**Async job pipeline (`KaraokeProcess*`).** Long-running work (ffmpeg/`melt` rendering, Demucs source separation,
Sheetsage key/BPM/chord detection, file copy/symlink operations) is modeled as a `KaraokeProcess` row with a
`KaraokeProcessTypes` enum and run by `KaraokeProcessWorker`/`KaraokeProcessThread` as an OS subprocess
(`ProcessBuilder`). The worker parses the subprocess's stdout with regexes to extract progress percentage (ffmpeg
`time=`/`Duration:`, Sheetsage's `NN%|` progress bars) and reports progress back to the frontend via SSE. Jobs have
a priority and run on a managed pool — this queue is the backbone of the whole rendering pipeline, not a generic
task runner.

**Устаревший workflow "MP3 Karaoke"/"MP3 Lyrics" — закомментирован по всему стеку, готовится к
полному удалению.** `KaraokeProcessTypes.FF_MP3_KAR`/`FF_MP3_LYR` (ffmpeg-конвертация минусовки/
оригинала в mp3 в папки `MP3_Karaoke`/`MP3_Lyrics`) и связанные с ними `KaraokeFileType.MP3_STORE_SONG`/
`MP3_STORE_ACCOMPANIMENT` признаны неактуальными. Закомментированы (не удалены — построчный `//`,
чтобы было легко довершить удаление позже), но нигде физически не убраны:
- enum-записи в `KaraokeProcessTypes.kt`/`KaraokeFileType.kt`;
- геттеры `pathToFileMP3Lyrics`/`pathToFileMP3Karaoke`/`pathToFolderMP3Lyrics`/`pathToFolderMP3Karaoke`
  и две строки в `renameFilesIfDiff()` в `Settings.kt`, функции `createProcessMp3Song`/
  `createProcessMp3Accompaniment`/`doMP3Karaoke`/`doMP3Lyrics`, два вызова в `createFromPath()`;
- ветки `when` в `KaraokeProcess.kt` (сборка ffmpeg-команды) и в `HealthReport.kt` (проверка/
  восстановление файлов — `when (karaokeFileType)` там statement, не exhaustive, поэтому удаление
  веток без `else` безопасно);
- 4 эндпоинта в `ApiController.kt` (`/song/mp3karaoke`, `/song/mp3lyrics`,
  `/songs/createmp3karaokeall`, `/songs/createmp3lyricsall`);
- кнопки в `webvue3` (`SongEdit.vue`, `SongsTable.vue`) — скрыты HTML-комментарием, JS-методы и
  Vuex actions (`createMP3Karaoke(ForAll)Promise` и т.п.) оставлены нетронутыми как недостижимый код.
`accompanimentNameMp3` в `Settings.kt` **не трогать** — используется другим, ещё активным типом
`MP3_ACCOMPANIMENT`/`FF_MP3_ACCOMPANIMENT`, не путать эти два похожих имени.

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
  (серый текст, не кликается, у `select()` ранний `return` по `song.current`).
- **Ручной поиск по (части) названия в заголовке модалки** (поле ввода + кнопка "Найти", `POST
  /api/song/searchoriginal`) — на случай, когда нужной песни нет в "семье" по `id`/`root_id`. В отличие
  от точного совпадения в `findDuplicateOriginal()`, здесь — **вхождение подстроки**: нормализованное
  имя песни (`normalizeSongNameForSearch()`, `Utils.kt`) должно *содержать* нормализованный поисковый
  запрос, а не совпадать с ним целиком. Нормализация — без учёта содержимого в скобках, знаков
  препинания и различия "е"/"ё" (например, для песни «Будь как дома, Осёл...» сработает запрос
  «как дома осел»). Класс пунктуации реализован через Kotlin `Regex("""[^\p{L}\p{Nd}\s]""")`
  (Unicode-aware), а **не** через Postgres `REGEXP_REPLACE(..., '\w', ...)` — в дефолтной C-локали
  Postgres `\w` распознаёт только ASCII-буквы и вырезал бы кириллицу вместе со знаками препинания.
  Поэтому оба поиска (`findDuplicateOriginal()` и `searchSongsByNormalizedName()`) сравнивают имена
  в Kotlin после загрузки лёгкого `(id, song_name)` из БД, а не через SQL `WHERE`. Ищет только среди
  песен с непустым `source_text`, по всем авторам (не только текущему). `findAndFillDublicates()`
  (кнопка "Найти и обработать дубликаты песен автора") этой нормализацией не затронута — намеренно, см.
  выше про неё же.
- **Клик по строке (и из списка "семьи", и из ручного поиска) — `POST /api/song/selectfamilysong`**
  (`applyFamilySongSelection()`, `Utils.kt`), отдельный от общего `/api/song/copyfieldsfromanother`.
  Копирует `SOURCE_TEXT`/`RESULT_TEXT`/`SOURCE_MARKERS` безусловно. `статус` — условно, не перетирая
  уже осмысленно выставленное значение: `NONE`(0) переводится в `TEXT_CREATE`(1) **только если он ещё
  `NONE`**. `root_id`, напротив, переписывается **безусловно** — это явный осознанный выбор
  пользователя (клик по конкретной строке), поэтому должен побеждать даже уже ранее заданный
  `root_id` текущей песни. Значение — `root_id` выбранной строки, если он у неё уже задан (выбранная
  песня сама часть другой семьи — привязываемся к её настоящему корню, а не к промежуточному узлу),
  иначе `id` самой выбранной строки (она и есть корень). Эндпоинт возвращает итоговые
  `rootId`/`idStatus` (`SelectFamilySongResultDto`),
  фронтенд (`SongEdit.vue`, `selectFamilySong()`) сразу пишет их в `this.song.rootId`/`this.song.idStatus`
  — по образцу уже существующего `setStatus()` (прямая мутация реактивного `song` в обход Vuex-мутаций,
  устоявшийся для этого конкретного компонента паттерн) — форма обновляется без перезагрузки песни.
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
- **Визуальная пометка низкого статуса.** `FamilySongDto` несёт `idStatus`; строки с `idStatus < 3`
  получают светло-серый фон (`.fsm-row-low-status`, `#f0f0f0`). Класс объявлен в CSS **перед**
  `.fsm-row-disabled`/`.fsm-row-original` — при равной специфичности (все три — одиночные классы)
  побеждает объявленный позже, поэтому у "оригинала"/текущей песни с низким статусом сохраняется их
  собственная (жёлтая/серая) подсветка, а не серый фон низкого статуса.

**Акустическая сверка двух песен в модалке "Похожие версии песни" (`WaveformCompare.kt`,
`FamilySongsModal.vue`).** Отдельно от разницы длительностей (`diffSeconds`) — определить, переизданием
(«как есть»/ремастер) какой песни является текущая. Колонка «Сверка»: кнопка «Сверить все» в `<th>`
колонки + «Сверить» в каждой строке (кроме текущей); результат — процент схожести и дельта сдвига в мс.
- **Алгоритм** (`WaveformCompare.compareWaveforms`, self-contained object, package
  `com.svoemesto.karaokeapp`): ffmpeg декодирует **вокальный** стем (`settings.vocalsNameFlac`) в
  моно-PCM 8 кГц во временный raw (`ffmpeg -v error -y -i <p> -ac 1 -ar 8000 -f s16le <tmp>`), затем
  RMS-огибающая 100 Гц (10 мс/кадр). Вокал, а не микс — пики фраз выразительнее и устойчивее к ремастеру;
  фолбэк на полный микс (`fileAbsolutePath`), если у любой из двух нет вокального FLAC (`stemUsed` в DTO).
- **Метрика — нормированная кросс-корреляция** (Пирсон: вычитание среднего + деление на СКО ⇒ инвариантна
  к уровню/EQ ⇒ ремастер той же записи даёт ~100 %). Пик → similarity %, argmax лаг → дельта; параболическая
  интерполяция по 3 точкам вокруг пика → суб-кадровая точность (~1–5 мс). Окно поиска лага
  `maxLag = max(45с, |diffДлит|+20с)`. Огибающие кэшируются (`ConcurrentHashMap`, ключ `path|mtime|frameRate`)
  — при «Сверить все» текущая песня декодируется один раз.
- **Знак дельты**: `A`=текущая, `B`=кандидат; пик при `A[i]≈B[i-L]` ⇒ `deltaMs = L·10мс` и
  `time_current = time_candidate + deltaMs/1000` (положительная дельта = у текущей добавлена тишина в начале).
- **API**: `POST /api/song/comparewaveform` (`id`, `idAnother`) → `WaveformCompareResultDto`
  (`similarityPercent`, `deltaMs`, `stemUsed`, `ok`, `error` — DTO объявлен в `WaveformCompare.kt`).
- **Сдвиг маркеров при выборе строки**: `/api/song/selectfamilysong` получил опциональный `deltaMs: Long?`;
  `applyFamilySongSelection(settings, another, deltaMs=null)` при заданной дельте вызывает
  `shiftMarkersAndFixEnd(json, deltaMs, settings.ms)` (`Utils.kt`) — сдвигает все маркеры на дельту
  (`max(0, time+delta)`) и сажает END-маркер (`markertype==Markertype.SETTING.value && label=="END"`) на
  реальную длительность текущей песни (`currentMs/1000`). Парсинг/сериализация — как в геттере
  `Settings.sourceMarkersList` (`Json`+`ListSerializer(ListSerializer(SourceMarker.serializer()))`, фолбэк на
  одиночный список). **Без `deltaMs` (строку не сверяли) — прежнее поведение**, полная обратная совместимость.
- **Frontend**: `Songs/store.js` — action `compareWaveformPromise`, `deltaMs` в `selectFamilySongPromise`.
  `FamilySongsModal.vue` — `compareResults` (реактивная карта `id → {status, similarityPercent, deltaMs,
  stemUsed}`), `compareAll` с конкуррентностью 3, `select()` эмитит `{id, deltaMs}` (дельта только при
  `status==='done'`). Кнопки сверки — `@click.stop` (иначе сработает выбор строки). `SongEdit.vue` —
  `selectFamilySong({id, deltaMs})`. **В браузере на реальных данных ещё не проверено** — при тихом/зашумлённом
  вокале или сдвиге больше 45 с править частоту огибающей / диапазон лага в `WaveformCompare.kt`.

**Пакетная автопривязка оригинала по аудио-сверке (`autoAssignOriginalByWaveform` в `Utils.kt`, эндпоинт
`/songs/autoassignoriginalall`).** Автоматический аналог ручного сценария модалки «Похожие версии песни»
для песен со `id_status=1` (TEXT_CREATE) и `root_id<>0`: найти «семью», акустически сверить с каждым
размеченным кандидатом, выбрать максимальный `similarityPercent`, и если он `>= threshold` (по умолчанию 85%) —
применить кандидата и перевести песню в статус `2` (TEXT_CHECK).
- `autoAssignOriginalByWaveform(settings, database, storageService, storageApiClient, threshold=85): AutoOriginalResult`:
  `findFamilySongIds` → `loadListFromDbByIds` → фильтр кандидатов с непустыми маркерами
  (`sourceMarkersList.any { it.isNotEmpty() }` — из пустого копировать нечего, экономит ffmpeg-декод) →
  `WaveformCompare.compareWaveforms` (оставить `ok`) → `maxByOrNull { similarityPercent }`.
- **Ключевой нюанс — «серверный Сохранить».** `applyFamilySongSelection(settings, best, deltaMs=cmp.deltaMs)`
  копирует текст/маркеры (со сдвигом; END уже сажается на `settings.ms` внутри `shiftMarkersAndFixEnd` — отдельный
  `addEndMarker` НЕ нужен), но пересчитывает **только** `resultText`. Кнопка «Сохранить» в `SubsEdit.vue` (через
  `Settings.setSourceMarkers`) пересчитывает ещё 3 поля — поэтому после `applyFamilySongSelection` вручную
  доигрываем: `resultText=getText()`, `formattedTextSong=getTextFormatted()`, `formattedTextTabs=getFormattedNotes()`,
  `formattedTextChords=getFormattedChords()` + запись `.srt` по каждому голосу (`convertMarkersToSrt` →
  `<rootFolder>/<fileName>.voice{n+1}.srt` + `chmod 666`). Затем `fields[ID_STATUS]="2"` и один `saveToDb()`.
- Эндпоинт `POST /songs/autoassignoriginalall` (опц. `author` + `threshold=85`) — фоновый `kotlin.concurrent.thread`,
  `SELECT id FROM tbl_settings WHERE id_status=1 AND root_id<>0 [AND LOWER(song_author)=LOWER(?)] ORDER BY id`.
  **Колонка автора в БД — `song_author`, НЕ `author`.** Пустой/нет `author` → все авторы (возможность оставлена
  намеренно). Лог в консоль через `songLogLabel(settings)` = `автор / год / альбом / «название» (id=…)` — и для
  текущей песни, и для песни-источника (в `reason`). Итог — SSE-тост. Только admin-машина (karaoke-app на прод
  не деплоится) — запись `.srt` на локальный диск ок.
- **Frontend**: кнопка «Автопривязать оригинал по аудио (статус 1 → 2)» на `HomeView.vue` в блоке с полем «Автор»
  (`:disabled="!author"`, паттерн `CustomConfirm → dispatch`). Кнопка «Найти и обработать дубликаты песен автора»
  скрыта (закомментирована, неактуальный код), её методы `markDublicates`/`doMarkDublicates` оставлены. Action
  `autoAssignOriginalAllPromise(ctx, payload)` в `Songs/store.js` шлёт `params:{author}` только если author непустой.
  **В браузере / на реальных данных ещё не проверено.**

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

**Ограничение CPU тяжёлых заданий (`resourceLimitsEnabled` + `cpuLimitPercent*` в `KaraokeProperties.kt`/
`Karaoke.kt`, хелперы в `Utils.kt`).** Единственный ранее существовавший механизм управления ресурсами —
`renice` на PID (`setProcessPriority`) — декоративен для докеризованных заданий: реницится CLI
`docker`/`docker compose`, а не сам процесс внутри контейнера. Поэтому для реального ограничения нужны два
разных механизма, в зависимости от того, как запускается тяжёлый шаг:
- **`DEMUCS2`/`DEMUCS5`/Key-BPM Finder — `docker run` (не compose)** — флаг `--cpus <N>`, `N = nproc * percent / 100`
  (`dockerCpusFlag()`), вставляется в argv тем же способом, что и уже существовавший `gpuFlags` в
  `Settings.argsDemucs2/5`. **Ловушка:** `docker --cpus 0` означает «без ограничения», а не «ноль» — значение
  никогда не форматируется в буквальный `0`/`0.0`, минимум `0.05`.
- **`MELT_*` (`MELT_LYRICS`/`MELT_KARAOKE`/`MELT_CHORDS`/`MELT_TABS`) — `docker compose run`, НЕ `--cpus`.**
  Первая версия этой фичи вставляла `dockerCpusFlag()` в argv точно так же, как для `docker run` — это
  оказалось нерабочим в принципе: `docker compose run` (проверено `--help` и прямым воспроизведением)
  **не поддерживает флаг `--cpus` вообще**, падает `unknown flag: --cpus` при любом запуске, если
  `resourceLimitsEnabled=true`. Правильный механизм — `deploy.resources.limits.cpus` в самом
  `docker-compose.yaml` (Docker Compose применяет его даже без Swarm), значение которого приходит через
  интерполяцию переменной окружения `${MLT_CPU_LIMIT:-0}`. Реализация:
  - `Utils.kt`: `dockerCpusEnvValue(percent)` — то же вычисление, что у `dockerCpusFlag`, но возвращает
    голую строку значения (не список argv-флагов) для env-переменной.
  - `KaraokeProcess.kt`: в `createProcess()` для всех четырёх типов `envs = mapOf("MLT_CPU_LIMIT" to
    dockerCpusEnvValue(...))` — используется уже существовавший механизм `KaraokeProcess.envs`
    (`ProcessBuilder.environment().putAll(...)` в `KaraokeProcessWorker`), тот же, что раньше только для
    GPU-параметров Demucs. `docker compose` читает переменную из окружения самого CLI-процесса.
  - **`/sm-karaoke/system/mlt-docker/docker-compose.yaml` — системный файл ВНЕ git-репозитория** (путь
    захардкожен в `KaraokeProcess.kt`). Требует ручного добавления `deploy: resources: limits: cpus:
    "${MLT_CPU_LIMIT:-0}"` — эта правка не проезжает через `git`/деплой, при пересоздании файла на новой
    машине её нужно будет внести заново вручную.
  - Встроенный голый ffmpeg-шаг 720p-транскода внутри `MELT_LYRICS`/`MELT_KARAOKE` (см. ниже) не затронут
    этим багом — он не через `docker compose`, использует `cpulimitPrefix()`, argv-обёртка работала верно
    с самого начала.
- **Голые ffmpeg/shell-скрипты** (`SHEETSAGE`/`SHEETSAGE2` — декодирование FLAC→WAV и сам `sheetsage.sh`,
  `FF_720_KAR`/`FF_720_LYR`, а также встроенный ffmpeg-шаг 720p-транскода **внутри** `MELT_LYRICS`/
  `MELT_KARAOKE`) — обёртка `cpulimit -l <N> -i -- <команда>` (`cpulimitPrefix()`). `cpulimit -l` — это
  процент **одного** ядра, поэтому для той же семантики «процент от всего хоста», что у `--cpus`,
  `N = nproc * percent` (не `/100`). Флаг `-i`/`--include-children` обязателен — `sheetsage.sh` форкает
  дочерние процессы, без него cpulimit ограничил бы только сам верхний процесс. Требует пакета `cpulimit`
  в `deploy/karaoke-app/Dockerfile` (добавлен, но нужна пересборка образа, чтобы бинарник появился реально).
- `cpuLimitPercentForType()` — единая точка маппинга `KaraokeProcessTypes → Karaoke.cpuLimitPercentXxx`.
  Обе хелпер-функции сами проверяют глобальный тумблер `Karaoke.resourceLimitsEnabled` и возвращают пустой
  список флагов, если он выключен — вызывающему коду проверять его отдельно не нужно.
- `MELT_LYRICS`/`MELT_KARAOKE` содержат **два** тяжёлых шага каждый (docker-рендер MLT + встроенный голый
  ffmpeg 720p-транскод) — оба оборачиваются одним и тем же процентом этого типа. `MELT_CHORDS`/`MELT_TABS`
  содержат только docker-шаг. `KEY_BPM_FROM_FILE` — пайплайн смешанный (docker-шаг + финальный
  «функциональный» `runFunctionWithArgs`, см. выше) — лимит применяется только к docker-шагу.
- **Три слоя применения лимита, каждый закрывает свой момент времени в жизни задания:**
  1. **При создании** (`createProcess()`) — процент читается один раз в момент постановки в очередь,
     запекается в `process_args`/`process_envs`. Исходный (и единственный до фиксов ниже) механизм.
  2. **При реальном старте** (`refreshArgvCpuLimit()`/`refreshEnvCpuLimit()`, `Utils.kt`, вызываются из
     `KaraokeProcessThread.run()` в `KaraokeProcessWorker.kt`, прямо перед `ProcessBuilder(args).start()`).
     **Ловушка, из-за которой это понадобилось:** задание может простоять в очереди `WAITING` долго —
     настройки успевают измениться, а значение из п.1 так и остаётся старым. Обе функции сначала снимают
     уже имевшуюся обёртку (какая бы версия настроек её ни породила), затем накладывают актуальную по
     текущим `Karaoke.resourceLimitsEnabled`/`cpuLimitPercent*`:
     - `refreshArgvCpuLimit(type, args)` — argv-варианты. Распознаёт шаг по структуре, не по типу задания
       (важно: у `MELT_LYRICS`/`MELT_KARAOKE` под одним `process_type` бывает ~15 разных шагов — docker-
       compose, chmod, mkdir, cp, rm, ln, голый ffmpeg — трогать нужно только настоящий тяжёлый шаг):
       `args[0]=="docker" && args[1]=="run"` → снять/переставить `--cpus <N>` (DEMUCS2/DEMUCS5/
       KEY_BPM_FROM_FILE); `args[0]=="cpulimit"` или (после снятия обёртки) `args[0]=="ffmpeg"`/
       оканчивается на `"sheetsage.sh"` → снять/наложить `cpulimit -l <N> -i --` (SHEETSAGE/SHEETSAGE2/
       FF_720_KAR/FF_720_LYR, встроенный 720p-транскод внутри MELT_LYRICS/KARAOKE). `docker compose` и
       голые filesystem-команды (`chmod`/`mkdir`/`cp`/`rm`/`ln`/`mv`) не подходят ни под один паттерн —
       возвращаются как есть.
     - `refreshEnvCpuLimit(type, envs)` — env-вариант (MELT-докер-compose шаг, `MLT_CPU_LIMIT`).
       Обновляет значение, только если ключ уже присутствовал в `envs` — он есть исключительно у
       docker-compose шага (остальные split-шаги того же job получают пустые `envs` при `createProcess()`).
  3. **Уже выполняющийся** прямо сейчас контейнер — `applyLiveCpuLimitToRunningProcesses()` (`Utils.kt`),
     вызывается из `ApiController.setProperty()` при любом изменении `resourceLimitsEnabled` или
     `cpuLimitPercent*` (включая клик по 🐢/🐇 в шапке). Ищет `WORKING`-задания докеризованных типов,
     определяет контейнер (`docker ps --filter ancestor=...` для MELT/`svoemestodev/melt:latest` и
     Key-BPM/`svoemestodev/keybpmfinder:latest`; фиксированное имя `demucs` для DEMUCS2/5) и применяет
     `docker update` — п.1/п.2 тут бессильны, аргументы уже переданы в `ProcessBuilder.start()`.
     **Ловушка:** `docker update --cpus 0` — **no-op** (проверено на Docker 29.6.1: команда завершается
     успешно, `NanoCpus` не меняется), хотя `"0"` — обычная семантика docker'а для «без ограничения» у
     **новых** контейнеров (`--cpus` при `docker run`/`compose`). Для снятия лимита с уже запущенного
     нужен отдельный флаг `--cpu-quota -1` (сбрасывает cgroup quota/period в unlimited — подтверждено
     `docker stats`: нагрузка сразу возвращается к полной). Функция сама выбирает нужный флаг
     (`--cpu-quota -1` при вычисленном значении `"0"`, иначе `--cpus <N>`).
  - Голых (не докеризованных) ffmpeg/sheetsage-задач слой 3 не касается — `cpulimit -l` запечён в argv
    уже стартовавшего процесса, живого способа его поменять нет; они подхватывают новый процент только
    при следующем запуске (через слой 2).
  - Слои 2 и 3 полностью автоматизировали то, что раньше (до этого фикса) требовало ручного
    SQL-бэкафилла `process_envs` у уже стоящих в очереди `WAITING`-заданий при каждой смене процента —
    это исправление больше не нужно для будущих изменений настройки, слой 2 закрывает его на старте
    сам. Разовый бэкафилл старых `WAITING`-записей, накопившихся ДО появления слоёв 2/3, делался вручную
    (SQL `UPDATE tbl_processes SET process_envs=...`) и актуален только как факт истории, не как
    процедура на будущее.
- Переключатель «с ограничениями»/«безлимит» в шапке `webvue3` — `components/Common/ResourceLimitToggle.vue`,
  читает/пишет `resourceLimitsEnabled` через уже существующие `/api/properties/getproperty`/`setproperty`
  (добавлен только недостающий action `getPropertyValuePromise` в `Properties/store.js`). Кнопка — квадратная,
  без текста, иконки 🐢 (лимит включён) / 🐇 (безлимит), меняются только по клику; подсказка при наведении —
  через `title`. Расположена в `App.vue` вплотную справа от кнопки старт/стоп: обе обёрнуты в
  `.start-stop-and-limit-group`, где `:deep(.process_worker) { margin-right: 0 }` гасит собственный
  `margin: 0 10px` компонента `ProcessWorker.vue` только с этой стороны. Проценты по типам заданий
  отдельного UI не получили — 12 новых `KaraokeProperty` автоматически показываются в существующей
  генерик-таблице `PropertiesTable.vue` (`/properties`).

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

**Маркер конца песни (`addEndMarker()` в `SubsEdit.vue`) — не отдельный `markertype`, а
`markertype: 'setting'` + `label: 'END'` по конвенции.** Его `time` — источник истины для
`Settings.songLengthMs` (`Settings.kt`, максимум по всем голосам), от которого зависят тайминги
рендеринга, фейды, MLT-проект, экспорт субтитров. `save()` вызывает `addEndMarker()` перед отправкой
на бэкенд; функция ищет END-маркер по всему массиву `sourceMarkers` (не только по последнему
элементу — более ранняя версия проверяла только последний элемент массива и из-за `&&` вместо `||`
в условии никогда не обновляла время уже существующего END-маркера). Если END-маркера нет — создаётся
новый, с визуальным регионом (`createRegionMarker()`), добавляется в массив и сортируется
(`sortSourceMarkers()`). Если есть, но `time` отличается от реальной длительности аудио
(`this.ws.getDuration()`, WaveSurfer) больше чем на `0.05` сек — время исправляется и вызывается
`redrawMarkers()` (тот же паттерн обновления visual-регионов, что и в `doMarkersDec/Inc/First()`).
Все стемы (voice/minus/song/drums/bass) одной песни имеют одинаковую длительность, поэтому сверка не
зависит от того, какой стем сейчас загружен в плеере (`this.sound`).

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

**Прогресс заданий в шапке (`Common/ProcessWorker.vue`, `Processes/store.js`) — состояние по threadId,
не общее.** В шапке `App.vue` два экземпляра одного `ProcessWorker.vue` (`includedThreadId="[0]"` —
ветка 0/`THREAD_LANE_HEAVY_RENDER`, `excludedThreadId="[0]"` — все остальные ветки). Прогресс хранится
в `state.workingProcessByThreadId` — карта `threadId → process`, а не единый объект на весь фронтенд:
мутация `updateProcessByUserEvent` (реагирует на SSE `RECORD_CHANGE`/`tbl_processes`) при статусе, отличном
от `DONE`/`ERROR`, кладёт запись по ключу `threadId`; при `DONE`/`ERROR` — удаляет ключ. Геттер
`getWorkingProcessForThreads(includedThreadId, excludedThreadId)` берёт из этой карты подходящую под
фильтр запись компонента. **Важно:** карта — единственный источник правды, компонент не должен кешировать
последнее значение локально (`data.currentWorkingProcess` — старый, уже убранный паттерн) — при общем
на весь фронтенд состоянии такой кеш не сбрасывался, когда завершалась именно "своя" ветка, а другая
оставалась активна, и блок прогресса зависал на последнем значении навечно после завершения всех заданий
ветки. `ERROR` считается терминальным статусом наравне с `DONE` — упавшее с ошибкой задание тоже должно
переставать показывать прогресс.

**Удаление песни (`SongEdit.vue` → `deleteCurrentSong`) — через SSE `recordDelete`, не локально.**
Удаление идёт тем же SSE-паттерном, что и правки: `deleteCurrentSong` — **action** (не мутация:
XHR в мутации — антипаттерн), делает `POST /api/song/delete` + `dispatch('setCurrentSongId',
previousSongId || nextSongId)` для перехода к соседней песне (`prev/next` берутся из
`song.idPrevious/idNext` в `setCurrentSongData`). Строку из таблицы (`<b-table :items="songsDigest">`
в `SongsTable.vue`) убирает **не** сам action, а SSE-обработчик: бэкенд `Settings.deleteFromDb()`
шлёт `recordDelete`/`tbl_settings` → `App.vue` → action/мутация `deleteSongByUserEvent` (`Songs/store.js`)
делает `splice` по `id` из `state.songsDigest` (образец — `updateSongByUserEvent`). Аналогично
`deletePublishDigestByUserEvent` (`Publish/store.js`) очищает ячейку `csrCell.settingsDTO=null` в
`publishDigest`. **Ловушка (была):** после рефакторинга `setCurrentSongId` (мутация→action)
`deleteCurrentSong` осталась мутацией с `state.commit(...)` (у `state` нет `.commit` → `TypeError`),
splice шёл по неиспользуемому таблицей `state.songPages`, а SSE-цепочка удаления была мёртвой
заглушкой (action коммитил `addSongByUserEvent`, мутация — только `console.log`) — песня удалялась на
бэкенде, но не исчезала из UI. Не рендерить удаление вручную в action — единый источник правды это SSE.

**Даты публикаций при удалении: НЕ пересчитываются.** delete-action (`deleteCurrentSong`,
`Songs/store.js`) намеренно **не** вызывает `/song/setpublishdatetimetoauthor` — удаление песни не
трогает даты публикаций остальных песен автора. (Ранее вызывал; убрано по требованию — удаление в
середине графика больше не сдвигает даты соседей.) Функция `Settings.setPublishDateTimeToAuthor`
(переназначает даты всем песням автора с `id > startSettings.id`, старт-дата + 1 день на каждую)
остаётся, но дёргается **только** ручной кнопкой «Установить даты публикации автору». Опциональный
параметр `skipPublished` у неё сейчас инертен (единственный вызывающий — ручная кнопка — шлёт без
параметра, дефолт `false`); задел на случай, если пересчёт при удалении когда-нибудь вернут в виде
«не трогать `onAir`-песни».

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

**Правило выше теперь закреплено кодом (2026-07-06).** Раньше бин `KaraokeStorageService` в karaoke-web
(`KaraokeStorageServiceImplWeb.kt`) всё равно строил рабочий прямой `MinioClient` на
`http://${STORAGE_CONTAINER_NAME}:9000` — и при старте `KaraokeWebService.init{}` дёргал
`deleteAllEmptyBuckets()`, что роняло контекст в crash-loop (контейнера `karaoke-storage` в прод-сети
`deploy_karaokenet` больше нет, MinIO уехал на 89.125.103.63 → `UnknownHostException`). Приведено в
порядок: бин karaoke-web стал **заглушкой** — `MinioClient` не создаётся вовсе, все ~20 методов бросают
`UnsupportedOperationException` (на живых публичных путях они и не вызывались — бин лишь «плумбинг»-параметр
в companion-методы моделей karaoke-app). `deleteAllEmptyBuckets()` из `init{}` убран. Публичный
`StorageController` (`/api/storage/**`, `permitAll`) — единственный реальный потребитель прямого
`MinioClient` в karaoke-web — **удалён** как мёртвый (никем не роутился/не вызывался) и архитектурно неверный
S3-шлюз в обход minio-proxy. `StorageApiClientWeb`/`WebClientConfig` в karaoke-web оставлены (бин
`StorageApiClient` всё ещё нужен для DI-плумбинга).

**Remote-хранилище из admin karaoke-app — прямой `MinioClient`, а не HTTP через прод-web (2026-07-06).**
`StorageApiClientImpl` (`karaoke-app/.../services/StorageApiClient.kt`) раньше был HTTP-прослойкой
(`WebClient` на `https://sm-karaoke.ru/api/storage` → прод-`StorageController` → прод-`MinioClient`).
Она сломалась после переезда MinIO на 89.125.103.63 (прод-web до него не достучаться) — все remote
`exists`/`upload` отдавали **500**, HealthReport/`UPLOAD_TO_REMOTE_STORE` не работали. Теперь karaoke-app
(admin-машина) ходит в remote-хранилище **напрямую** через `MinioClient` к `89.125.103.63:9000` —
endpoint из новой property `storage.remote-endpoint` (env `STORAGE_REMOTE_ENDPOINT`, дефолт — новый
сервер), креды — те же `storage.key`/`storage.secret` (совпадают с локальными `minio_key`/`minio_secret`).
Интерфейс `StorageApiClient` и все вызывающие места (`HealthReport`, `Utils.executeUploadToRemoteStore`)
не менялись; `Mono`-методы обёрнуты в `Mono.fromCallable`, чтобы ошибка всплывала на `.block()` под уже
существующим try/catch вызывающего кода. `smKaraokeWebClient` (WebClient-бин в karaoke-app
`WebClientConfig`) стал orphan — оставлен как безвредный. **Важно про порядок:** прямой доступ karaoke-app
к MinIO нужно чинить ДО удаления прод-`StorageController` — тогда удаление контроллера безопасно, т.к.
karaoke-app больше не дёргает `sm-karaoke.ru/api/storage`. И admin karaoke-app, и его Docker-контейнер
свободно достают `89.125.103.63:9000` (MTU black-hole — проблема только прод-сервера 79.174.95.69, не
admin-машины), подписанный S3 работает с теми же кредами.

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

**Ловушка: ленивый `Mono` remote-удаления в HealthReport (`storageApiClient.deleteFile` без `.block()`).**
`StorageApiClient` (remote MinIO) — **реактивный** интерфейс: его методы возвращают `Mono` (`Mono.fromCallable`),
который ничего не делает без подписки. В action-лямбдах `HealthReport.kt` (ветки удаления remote-файла —
«файл неактуальный» и `!canBe`) `storageApiClient.deleteFile(...)` вызывался **без `.block()`** → remote-файл
фактически не удалялся. Дальше `executeUploadToRemoteStore` с гардом `if (existsInLocalFileSystem &&
!existsInRemoteStorage)` видел старый файл на месте → **пропускал загрузку**, и Repair «неактуального» файла
зацикливался (та же ошибка при каждом повторном репорте). Local-путь работал: `KaraokeStorageService`
(`storageService`) — **синхронный** интерфейс (прямые типы возврата, `deleteFile: Unit`), выполняется сразу.
Фикс: `.block()` + try/catch (по образцу `Utils.kt:251`) в обеих remote-ветках — try/catch обязателен, т.к.
`executeSolutionActions()` гоняет лямбды `forEach { action() }`, и исключение оборвало бы следующую лямбду
постановки upload-задания. **Правило:** любой Mono-метод `StorageApiClient` в императивном коде обязан
заканчиваться `.block()`; `storageService` в `.block()` не нуждается.

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

**Исполнитель UPLOAD получает точный `storageFileName`/`bucketName` из HealthReport, а не пересчитывает
его сам.** `executeUploadToLocalStore`/`executeUploadToRemoteStore` (`Utils.kt`) раньше вычисляли ключ
хранилища по формуле `"${settings.storageFileName}${fileType.suffix}.${fileType.extention}"`, где
`settings.storageFileName = "$author/$year - $album/$fileName"` основан на имени **песни**. Это верно
только для аудио-стемов `MP3_*`, но **ломало все 4 картинки** (`PICTURE_ALBUM`/`PICTURE_ALBUM_PREVIEW` —
ключ `"$author/$year - $album/$author - $year - $album<suffix>.png"`; `PICTURE_AUTHOR`/
`PICTURE_AUTHOR_PREVIEW` — `"$author/$author<suffix>.png"`): Repair заливал файл по неверному ключу
(рядом с именем песни), правильный ключ оставался пустым → HealthReport снова видел отсутствие → снова
ставил задачу (бесконечное «Repair не работает» + мусор в бакете). Только эти 9 типов вообще грузятся в
хранилище (5×`MP3_*` + 4 картинки) — у них есть `LOCAL_STORAGE`/`REMOTE_STORAGE` в
`KaraokeFileType.locations`; остальные 14 типов (сырой FLAC, видео, PROJECT-файлы, `PICTURE_PUBLICATION`,
`PICTURE_SONGVERSION`) только `LOCAL_FILESYSTEM` — их это не касалось. Фикс: HealthReport протаскивает уже
вычисленные `storageFileName`+`storageBucketName` через `context` задачи (все 6 вызовов `createProcess`) →
в args (`KaraokeProcess.kt`, ветки `UPLOAD_TO_LOCAL_STORE`/`UPLOAD_TO_REMOTE_STORE`) → `executeUpload*`
берёт `params["storageFileName"]`/`params["bucketName"]` напрямую, с фолбэком на старую формулу для уже
стоящих в очереди задач. Правило на будущее: любой новый тип файла с ключом хранилища, отличным от
`storageFileName + suffix`, работает без правок исполнителя — он больше не угадывает ключ.

**`description` у `HealthReport` обязан включать location-суффикс** (`"${description}/${location.name}"`,
добавляется один раз в диспетчере `actions()`, строки ~42-132). Без этого local- и remote-варианты одной
и той же проблемы одного файла (`karaokeFileType`) неотличимы по `(type, status, description)`, и
`HealthReport.getHealthReport()` при двух почти одновременных repair-запросах (typично для `RepairAll`,
который не ждёт ответа между запросами) резолвит **оба** запроса в одну и ту же (первую по списку
locations) запись — на практике: `RepairAll` создавал только `UPLOAD_TO_LOCAL_STORE`-задачи, а
`UPLOAD_TO_REMOTE_STORE` появлялись только при повторном клике, когда local-проблема уже пропадала из
списка ошибок. Тот же паттерн `"${karaokeFileType.name}/${x.name}"` уже применялся для
`PICTURE_PUBLICATION`/`SongVersion` — не изобретать новый.

**Раздел "Статистика" (`webvue3`) — `target=local|remote` + серверная пагинация.**
`StatsController.kt` (`/api/stats/by-song`, `/api/webevents`) поддерживает тот же `target=local|remote`
паттерн, что `SiteUsersController`/`PublicSettingsController` (общий `resolveDb(target)`). **В отличие**
от них — не client-side пагинация уже загруженного списка (как в `SiteUsersTable.vue`, где `b-pagination`
просто листает целиком присланный `siteUsersDigest`), а настоящая серверная: обе таблицы ("Статистика по
песням" по `tbl_events`+`tbl_settings`, "Последние события" по `tbl_events`) потенциально тяжёлые (тысячи
строк), полный список за раз не тянется. Оба эндпоинта принимают `page`/`pageSize`, считают
`offset = (page-1)*pageSize`, добавляют `LIMIT/OFFSET` к SQL и возвращают `{items, totalCount}` (было —
голый массив); `totalCount` — отдельный лёгкий `COUNT(*)` запрос (`StatsByEvents.getStatBySongCount()`/
`getWebEventsCount()`), а не побочный продукт основного запроса (`count(*) over()` дал бы 0 на пустой
странице за пределами данных).

**Ловушка: `target=local|remote` эндпоинты обязаны ЗАКРЫВАТЬ соединение (обёртка `withDb`).**
`resolveDb(target) = if (target=="remote") Connection.remote() else Connection.local()` создаёт **новый
объект `Connection`** на каждый HTTP-запрос, а `KaraokeConnection.getConnection()` (`KaraokeConnection.kt`)
открывает и кэширует в этом объекте **собственное физическое JDBC-соединение**. Ручные JDBC-функции
(`StatsByEvents` в `StatBySong.kt` и т.п.) закрывают только `ResultSet`/`Statement`, но **не `connection`**
— без явного `close()` соединение висит до обрыва. Дашборд `/stats` дёргает ~11 эндпоинтов за загрузку →
каждая загрузка оставляла ~11 висящих соединений → через несколько обновлений пул Postgres
(`max_connections=100`) исчерпан → `FATAL: sorry, too many clients already` (забиваются даже
reserved-слоты суперюзера, `psql` тоже перестаёт пускать). **Не путать с `WORKING_DATABASE`** — это
shared-синглтон (одно кэшированное соединение на весь app), его закрывать НЕ нужно, утечки там нет; течёт
именно per-request `Connection.local()/remote()`. Фикс (2026-07-06): во всех трёх контроллерах
(`StatsController`/`SiteUsersController`/`PublicSettingsController`) добавлена приватная обёртка
`withDb(target) { db -> ... }`, закрывающая `db.getConnection()?.close()` в `finally` — одно соединение
на запрос (закрывается сразу; потокобезопасно для параллельных вызовов дашборда — shared connection тут
был бы небезопасен). `withDb` намеренно дублируется per-controller, как и `resolveDb`. **Правило: любой
новый эндпоинт karaoke-app с per-request `Connection.local()/remote()` писать через `withDb`, не напрямую
`resolveDb(target)`.**

- **Детерминированный `ORDER BY` обязателен при постраничной выборке.** У `getStatBySong()` было
  `order by total desc` — при множестве песен с одинаковым `total` (типично много нулей) Postgres не
  гарантирует стабильный порядок между двумя `LIMIT/OFFSET` запросами, из-за чего строки могли бы
  повторяться на разных страницах или пропадать. Добавлен тайбрейкер `, tbl_events.song_id asc`.
  Аналогично `getWebEvents()`: `order by last_update desc` → `order by last_update desc, id desc`
  (у `tbl_events` есть PK `id`).
- **`promisedXMLHttpRequest` (`webvue3/src/lib/utils.js`) не сериализует `params` в query-string для
  GET-запросов** — `xhr.send(getParamStringToSend(params))` шлёт их телом, которое для GET браузером не
  используется. Поэтому все параметры GET-запросов (`target`, `page`, `pageSize`) собираются вручную в
  URL-строке (`` `/api/stats/by-song?target=${...}&page=${...}&pageSize=${...}` ``), а не через
  `params: {...}` — этот паттерн уже был в старом `loadWebEvents` (`?limit=${limit}`), теперь применён
  последовательно везде в `components/Stats/store.js`. Учитывать при любом новом GET-эндпоинте.
- **Фронтенд (`StatsView.vue`):** номер страницы каждой из двух таблиц — локальные `data`
  (`statsBySongPage`/`webEventsPage`), не Vuex — как и в `SiteUsersTable.vue`. `b-pagination`
  (`bootstrap-vue-next`, стандартный `v-model`/`modelValue`) забинжена не напрямую на `data`-поле, а на
  computed-обёртку с сеттером (`statsBySongPageModel`/`webEventsPageModel`) — сеттер и обновляет номер
  страницы, и сразу дёргает перезагрузку с сервера. Программные сбросы страницы на 1 (смена БД, смена
  размера страницы) пишут в исходное `data`-поле напрямую, в обход computed-сеттера, и сами вызывают
  reload одним явным вызовом — если бы они шли через computed-сеттер, смена с page=1 на page=1 не
  всегда дала бы повторный reload (нет изменения значения), а смена с другой страницы дала бы двойной
  запрос (реактивный из сеттера + явный).

**Реальный IP посетителя, привязка событий к пользователю, события плеера (`tbl_events`).**
`referer` в `tbl_events` годами показывал `172.18.0.1` для всех событий — не баг nginx (прод
`80to8897` уже правильно ставил `X-Real-IP`/`X-Forwarded-For`), а то, что единственная точка
INSERT (`MainController.doRegisterEvent()`, karaoke-web) брала `request.remoteHost` — голый
TCP-peer, который из-за Docker port-publish NAT (`docker-compose-web.yml`) всегда равен адресу
docker-шлюза. Плюс `referer` вообще не писался для `clickToLink`/`play` — только для `callRest`.

- **`ClientIpResolver`** (`karaoke-web/.../util/ClientIpResolver.kt`) — читает `X-Forwarded-For`
  (первый адрес в цепочке) → `X-Real-IP` → фолбэк `request.remoteHost`, если заголовков нет вовсе
  (чистый dev без nginx перед приложением). `doRegisterEvent()` резолвит IP/User-Agent один раз в
  начале функции и пишет их во **все** ветки через общий приватный хелпер `insertEvent()` (убрал
  4-кратное копипаст-строительство INSERT) — `clickToLink`/`play` теперь тоже получают `client_ip`,
  которого раньше не было вовсе. **`referer` для `callRest` переосмыслен (2026-07-06):** раньше туда
  писался `clientIp` (дубль `client_ip`); теперь — настоящий внешний источник перехода
  (`document.referrer`, см. ниже «Страна по IP + источник перехода»). Пишется только если пришёл
  непустым; клиентское значение больше не дублирует IP.
- **Новые колонки `tbl_events`**: `client_ip`/`anon_id` (`varchar(64)`, nullable), `user_agent`
  (`text`, nullable), `site_user_id` (`bigint NOT NULL DEFAULT 0`, **не** `Long?` в
  `WebEvent.kt`!). Та же причина, что уже описана выше для `song_id`: `rs.getLong()` возвращает
  примитивный `0` на SQL `NULL`, а generic reflection-loader (`KaraokeDbTable.kt`) не различает
  `Long`/`Long?` по `classifier` — nullable тип тут не решил бы проблему, а замаскировал бы её.
  `0` = аноним. Любое изменение схемы `tbl_events` обязано попасть в `deploy/recordhash_events.sql`
  (md5-функция `update_tbl_events_recordhash()` + разовый backfill `UPDATE`) — иначе LOCAL↔SERVER
  sync тихо перестаёт видеть diff именно по новым колонкам.
- **`site_user_id`** резолвится опционально (без 401) через новый общий
  `karaoke-web/.../services/SiteUserResolver.kt` (читает `Authorization: Bearer`, `null` если нет
  токена/невалиден) — используется и в `PublicApiController.events()`, и в
  `PublicPlayerController` (заменил там приватный дублирующий `resolveSiteUser()`). Значение
  передаётся в `doRegisterEvent()` отдельным параметром функции, **не** через клиентский `data`-map
  — так его нельзя подделать с фронта. `anon_id`, наоборот, берётся из `data["anonId"]` как есть
  (просто bucketing-ключ для анонимов, без секьюрити-последствий).
- **`EventTypes.kt`** (`karaoke-app/.../model/`) — чистые enum'ы `EventType`/`RestName`/
  `LinkType`/`PlayerAction` с полем `dbValue` (не `.name`/ordinal — существующие 250k+ строк
  остаются источником истины без миграции данных), заменили голые строковые литералы, разбросанные
  по `MainController.kt`/агрегации. Без зависимостей на `Settings`/сервисы — безопасно
  использовать в karaoke-web по той же причине, что и `WebEvent.kt`.
- **Новый `event_type = "player"`** — события онлайн-плеера: `link_type` = `PlayerAction`
  (`shown`/`play`/`pause`/`seek`/`export`), `song_id`, `link_name` переиспользован под деталь
  действия (ключ стема при `export`, позиция в секундах при `seek`). `shown` пишется **на бэкенде**
  внутри `PublicPlayerController.access()` (при `canWatch=true`) — фронту достаточно передать
  `anonId` query-параметром. **`shown` (бывш. `open`, переименовано 2026-07-06) — это НЕ «пользователь
  запустил плеер», а «плеер был встроен/показан на просмотренной странице песни»:** в UX плеер (iframe
  `/player/:id`) встраивается автоматически во `watch(currentSong)` (`usePlayerAccess.checkAccess()`),
  отдельного действия «открыть» нет, поэтому событие пишется при заходе на страницу с доступным onAir-
  плеером, а не при реальном запуске (реальный запуск — `play`). Историческое значение мигрировано
  `UPDATE tbl_events SET link_type='shown' WHERE event_type='player' AND link_type='open'` на LOCAL и
  PROD (link_type входит в recordhash — `BEFORE UPDATE`-триггер пересчитал его сам; миграцию применять
  идентично на обеих БД, иначе sync разъедётся). Enum `PlayerAction.SHOWN("shown")` (`EventTypes.kt`),
  агрегация `p_shown`/`cntPlayerShown`/подпись «Плеер: показан» (`StatBySong.kt`), колонка «Показ»
  (`StatsView.vue`). `play`/`pause`/`seek`/`export` — трекинг **только в
  `karaoke-public/src/player/KaraokePlayer.js`** (НЕ в admin-копии `webvue3/src/player/`, там нет
  анонимного посетителя и пути к `/api/public/events`):
  - play/pause — в `_togglePlay()`, не внутри `_play()`/`_pause()` самих по себе: те два метода
    также вызываются внутренне из `_seekTo()`/`_seekToDisplayTime()` для возобновления
    воспроизведения после перемотки — это не новое пользовательское намерение "play" и уже
    покрыто отдельным seek-событием, трекинг там задвоил бы счётчик.
  - seek — единая точка `_seekToDisplayTime(dt)` (сходятся все 3 внешних триггера: клик по
    прогресс-бару, `interaction` от обеих waveform-дорожек), debounce 400мс на всякий случай
    (двойной `interaction` от `wsAcc`+`wsVoc` на одно действие).
  - Все вызовы — тем же тихим fire-and-forget паттерном, что и остальной `tracking.js`
    (`apiPost(...).catch(() => {})`): без консоли, без UI-индикаторов, трекинг не должен быть
    заметен пользователю.
  - `anon_id` во всех обычных трекинг-вызовах переиспользует тот же localStorage UUID (`kp_cid`),
    что уже использовался только для скрытого жеста разблокировки плеера (`trackMetaClick`,
    поле `clientId` в его payload) — **не переименовывать** это поле, гейт-логика жеста его читает
    по имени; для остальных событий используется отдельное поле `anonId` с тем же значением.
- **Дедуп агрегации.** `karaoke-web/Stat.kt` лишился `getWebEvents()`/`getStatBySong()` (были
  побайтово идентичны `karaoke-app/.../model/StatBySong.kt`'s `StatsByEvents`) — легаси
  Thymeleaf-роуты `MainController.doStatBySong()`/`doWebEvents()` теперь тоже дёргают
  `com.svoemesto.karaokeapp.model.StatsByEvents` напрямую (поля DTO совпадают по именам с тем, что
  ждут `statbysong.html`/`webevents.html` — шаблоны не менялись). `karaoke-web/WebEvent.kt`
  (простой display-класс, не путать с `KaraokeDbTable`-моделью в karaoke-app) удалён как мёртвый.
  `karaoke-web/Stat.kt` теперь `object` (было `class` с неиспользуемым конструктором) — держит
  только счётчики `getCountSongsExclusive/OnAir/InCollection`, у которых аналога в karaoke-app нет.
- **Локальный dev-nginx** (`karaoke-public/nginx_karaoke-public.conf`) приведён к тем же
  `X-Real-IP`/`X-Forwarded-For`, что и прод (`80to8897`) — раньше ставил нестандартный
  `X-Forwarded-For-Client`, который никто не читал.
- **Деплой на прод — миграция обязана применяться ДО или ОДНОВРЕМЕННО с новым `karaoke-web`, не
  после.** `insertEvent()` безусловно пишет `client_ip` в каждый INSERT — если колонки ещё нет на
  сервере, вообще все события (клики, callRest, плеер) начинают падать с `column does not exist`.

**Счётчики главной страницы (`GET /api/public/stats`, `karaoke-web/Stat.kt`, обновлено 2026-07-06).**
`StatBySong.getCountSongsInCollection()`/`getCountSongsExclusive()` считают не по датам
публикации/`id_sponsr`, а по `id_status`: «Песен в коллекции» = `id_status >= 3` (дошла минимум до
`PROJECT_CREATE`, см. `Settings.status` в `karaoke-app`), «Эксклюзивно по подписке» (лейбл
переименован из «Эксклюзивно на Sponsr» в `HomeModern.vue`/`HomeClassic.vue`/legacy `main.html`) =
`exclusive = true AND id_status >= 3`. `getCountSongsOnAir()` не менялся (даты публикации). **Ключи
ответа API/Vuex-стора (`onSponsr`, `exclusive`) и имена функций сознательно не переименованы** — их
названия теперь не отражают точную семантику (`onSponsr` давно не про `id_sponsr`), но переименование
потянуло бы правки сразу в Kotlin (`Stat.kt`/`PublicApiController.kt`/`MainController.kt`) и Vue
(`stats.js`/оба Home-компонента/`main.html`) без функциональной необходимости — учитывать при следующей
правке этого куска, не удивляться несовпадению имени ключа и смысла.

**Побочная находка при сквозной проверке (не связана с этим рефакторингом, но чинится тем же
`setval`):** identity-sequence может отстать от реальных данных таблицы (`tbl_events_id_seq` на
LOCAL БД имел `last_value=557` при `max(id)=250342` — видимо, после восстановления/импорта данных
через явные `id` без продвижения sequence), из-за чего INSERT периодически падает с `duplicate key
value violates unique constraint`. Диагностика: `select last_value from <table>_id_seq` vs
`select max(id) from <table>`. Фикс: `SELECT setval('<table>_id_seq', (SELECT MAX(id) FROM
<table>))` — безопасная операция, только продвигает счётчик, данные не трогает. Стоит иметь в виду
для любой другой `KaraokeDbTable`-таблицы, если появятся похожие случайные "id already exists".

**Дашборд статистики webvue3 + расширенный трекинг (2026-07-05).** Крупная доработка сбора и
отображения статистики. Ключевые моменты:

- **Атрибуция ко ВСЕМ событиям.** Раньше просмотры страниц (`callRest`) писались анонимно.
  Теперь `PublicApiController.stats/zakroma/songs/song` принимают `@RequestParam anonId` и передают
  `siteUserResolver.resolve(request)?.id ?: 0` в `doRegisterEvent` (как раньше делал только
  `/events`). На клиенте `karaoke-public/services/api.js:apiGet` **автоматически** подмешивает
  `anonId` (из нового `services/clientId.js`) + `Authorization` во все публичные GET. `getAnonId`
  вынесен в `clientId.js`, чтобы не было цикла импортов `api.js ↔ tracking.js` (`tracking.js` его
  реэкспортирует — на него завязан `usePlayerAccess`).
- **Новые типы событий** (без миграции схемы — переиспользуют generic-колонки `tbl_events`):
  `EventType.ENGAGEMENT` (время на странице), `EventType.UI` (`link_type`=navigate|theme|scroll),
  `PlayerAction.PROGRESS`/`ENDED` (`EventTypes.kt`). Ветки в `MainController.doRegisterEvent`.
  Клиент: `trackPageEngagement` шлётся через `fetch(..., {keepalive:true})` — **НЕ**
  `navigator.sendBeacon` (тот не умеет ставить `Authorization`, событие потеряло бы `site_user_id`).
  Composable `useEngagementTracking` (время видимости + скролл-вехи 25/50/75/100) в 4 view-обёртках;
  тема — в `useDesign` watch; навигация — `router.afterEach`; плеер — `_onEnded`/`_trackProgress`
  (только копия `karaoke-public/player/`, гейт `_mode==='api'`, вехи с флагами и переармированием в
  `_seekTo`).
- **Все переходы по ссылкам** идут через `PlatformLink` (song/zakroma/search × classic/modern, все
  платформы tg/max/sponsr/vk/dzen с корректным `songVersion`) и `SocialLinks` — уже трекались.
  Единственный найденный пробел — sponsr-CTA «Оформить подписку» на странице песни (обычный
  `<a href>`): добавлен `onSponsrClick` → `trackLinkToSong('sponsr', id, 'all')` в
  `SongClassic`/`SongModern`.
- **Backend агрегации** — `StatsByEvents` (`model/StatBySong.kt`, ручной JDBC): `getSummary`,
  `getEventsTimeSeries(mode = all|type|detail)`, `getEventsByType`, `getEventsDetailed` (→
  `DetailCountDto` с `eventType` для drill-down), `getChannelBreakdown` (**динамически** по
  `link_name` для `clickToLink/linkToSong`, boosty исключён, max/sponsr появляются сами),
  `getTopUsers`, `getWebEvents`. Последний переписан в **2 прохода** (убран N+1 `loadFromDbById` →
  один `IN`-запрос имён песен) и **создаёт строку для ЛЮБОГО типа** (раньше молча пропускал
  `engagement`/`ui`/`play` и `player/progress|ended`), отдаёт **все сырые колонки** `tbl_events`.
  Общий SQL-фрагмент детали — константа `DETAIL_CASE` (кардинальность `link_name` для player/seek и
  engagement намеренно НЕ берётся — только `link_type`, иначе тысячи значений). Эндпоинты
  `StatsController`: `/api/stats/summary|timeseries|by-type|channels|by-detail|top-users|user-events`,
  фильтры `eventType/days/siteUserId` в `/api/webevents`.
- **Frontend** — `webvue3/components/Stats/*` на `vue3-apexcharts` (единственная новая зависимость,
  регистрация в `main.js`): `KpiCards`, `TimeSeriesChart` (селектор Всего/По типам/Детально),
  `TypeChannelBreakdown` (донат типов — **клик по сегменту** через `dataPointSelection` эмитит сырой
  `event_type` → фильтрует `DetailBreakdown`; бар каналов), `DetailBreakdown` (горизонт. бар,
  фильтр по выбранному типу + сброс), `TopUsersTable`+`UserEventsModal` (drill-down по пользователю),
  топ песен (**+7 колонок player-действий**: открытие/старт/пауза/перемотка/экспорт/прогресс/
  завершение), лог событий (**все поля БД**). Все GET — вручную собранной query-string (квирк
  `promisedXMLHttpRequest`).
- **Индекс** `tbl_events_site_user_id_index` (partial, `WHERE site_user_id>0`, вне `recordhash`)
  создан на LOCAL и PROD — для `getTopUsers`/drill-down.
- **Где живёт:** трекинг (`karaoke-web` + `karaoke-public`) **задеплоен на прод**. Дашборд-API
  `/api/stats/*` и таблицы — **только `karaoke-app` + `webvue3` (админ-машина, на прод НЕ
  деплоятся)**; чтобы увидеть дашборд — локальный ребилд/рестарт `karaoke-app` + ребилд `webvue3`.

**Страна по IP + источник перехода в статистике (2026-07-06).** Два новых атрибута посетителя в
дашборде статистики. **Схема `tbl_events` НЕ менялась, `recordhash_events.sql` НЕ менялся — миграции
на прод нет.**

- **Источник перехода (внешний referer).** Настоящий внешний источник — это `document.referrer`
  браузера (HTTP-заголовок `Referer` на API-запросе SPA бесполезен — всегда сам сайт). Снимается
  **один раз** при загрузке SPA (`karaoke-public/src/services/entryReferrer.js`, только кросс-домен,
  `consumeEntryReferrer()` отдаёт значение единожды и очищает), подмешивается в `apiGet`
  (`api.js`) как `?referrer=`. karaoke-web (`PublicApiController` stats/zakroma/songs/song +
  `MainController.doRegisterEvent` ветка `CALL_REST`) пишет его в колонку `referer` — которая раньше
  **впустую** дублировала `client_ip`. Агрегация `StatsByEvents.getTopReferrers` (`GET
  /api/stats/referrers`) фильтрует `referer like 'http%'` — легаси-строки (там лежат старые IP до
  переосмысления) отсекаются. Только заход-лендинг: внутренние SPA-переходы referer не несут.
- **Страна по IP.** Резолв через `api.country.is/<ip>` (единственный гео-сервис, работающий из
  Docker; поддерживает произвольный IP в пути) — `karaoke-app/services/GeoIpService.kt` (кэш
  in-memory + таблица `tbl_ip_country`, `deploy/karaoke-db/08_ip_country.sql`). **Кэш-таблица —
  ТОЛЬКО на LOCAL админ-БД, на прод НЕ применять** (страна нужна только в админ-дашборде); все
  обращения к кэшу — через `Connection.local()` независимо от `target`. Пустая строка "" —
  валидный закэшированный результат «не определено» (приватный IP/сервис не ответил), чтобы не
  долбить сервис повторно. **Страна НЕ хранится в `tbl_events`** — выводится на лету:
  `getCountryBreakdown` (`GET /api/stats/countries`) группирует `GROUP BY client_ip` в SQL (уник.
  IP — сотни-тысячи, не 250k), сводит IP→страна в Kotlin. `resolveMany(ips, maxFetch)` ограничивает
  число внешних резолвов за вызов (лог 100, география 150) — на холодном кэше ответ не блокируется
  на минуты, кэш наполняется за несколько обновлений. `WebEventDto.country`/`songName` добавлены,
  `getWebEvents` тащит их 2-проходно (тем же `IN`-запросом, что и имена песен). `countryLabel()` —
  ISO→рус рядом с `channelLabel()`.
- **Анонимы в топе пользователей + древовидный drill-down.** `getTopUsers`/`getTopUsersCount`
  теперь включают анонимов (бакет по `anon_id` среди `site_user_id=0`), залогиненные первыми
  (`order by kind asc, cnt desc`); `TopUserDto.anonId`, drill-down по анониму
  (`buildEventsWhere`/`getWebEvents(anonId)`, `GET /api/stats/user-events?anonId=`). Модалка
  `UserEventsModal.vue` — переключатель «Дерево/Таблица» (дерево по умолчанию): группирует события
  по «странице» (`song:{songId}` / `rest:{restName}`) — все действия клиента на одной странице =
  листья одной ветки. Грузит одним запросом (`pageSize=2000`, без постраничности), «показаны
  последние N» если больше. Фронт-блок `GeoReferrers.vue` (bar стран + таблица источников),
  столбцы «Страна»/«Источник» в логе. Сборка webvue3/karaoke-public — только `nvm use v25.7.0`.

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
- **Готовность песни к воспроизведению — единственная точка `PublicPlayerController.stemsReady(settings)`.**
  Возвращает `true`, только когда `settings.idStatus >= 3` **И** в MinIO есть оба стема
  (`MP3_ACCOMPANIMENT` + `MP3_VOCAL`) **И** `sourceMarkersList` непустой. Проверка `idStatus` стоит первой —
  дешёвая (чтение `fields`-карты, без сети), short-circuit избегает лишних HEAD-запросов в MinIO при статусе
  < 3. `stemsReady` вызывается только из `access()`, которая при `canWatch = ready && (onAir || premium)`
  выдаёт токен доступа; `playerData`/`playerFile` гейтятся этим токеном (`authorized()`), поэтому правки
  готовности достаточно в одном месте. Порог `>= 3` — литерал (устоявшийся стиль, ср. `HealthReport.kt`),
  отдельной enum-константы нет. `idStatus`-геттер (`Settings.kt`, `fields[...]?.toLongOrNull() ?: 0L`) —
  чистое чтение карты, не задевает опасную инициализацию `ConstantsKt`/`Connection` (см. пункт выше про
  `rootFolder`/`*NameFlac`), безопасен из karaoke-web.

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

**`is_permanent_premium` — независимый от `is_premium` флаг "вечного" премиума.** Если выставлен,
пользователь считается премиумным всегда, даже если `is_premium` не установлен — задел на будущее: когда
`is_premium` начнёт выставляться автоматической Sponsr-сверкой, вручную выданные "вечные" премиумы не
потеряются. Оба флага редактируются в админке независимо (два отдельных чекбокса в `SiteUserEdit.vue` +
колонка в `SiteUsersTable.vue`), ни один не подменяет другой. `SiteUser.kt` — сырое поле `isPermanentPremium`
(БД-колонка) плюс **не-БД** вычисляемое свойство `isEffectivePremium = isPremium || isPermanentPremium`,
единая точка правды для всех проверок доступа. Единственное место в `karaoke-web`, где решается
премиум-доступ — `PublicPlayerController.isPremiumUser()` (доступ к плееру при `onAir=false`, экспорт
стемов, `.smkaraoke`) — использует `isEffectivePremium`, закрывает сразу все три ветки (`access()`,
`playerData()`, `playerFile()`). `SiteUserDto` несёт оба сырых флага (для админки) и отдельно вычисленный
`isEffectivePremium` (для бейджа «🪙 Премиум» на публичном сайте — фронтенд не дублирует OR-логику).

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
(79.174.95.69:8832) отдельно, миграция сама на сервер не попадает. То же самое для `ALTER TABLE`
существующих таблиц (например, `is_permanent_premium` в `tbl_site_users`) — применять вручную через
`docker exec -i -u postgres karaoke-db psql -d karaoke` на каждой БД. **На проде роли `postgres` не
существует** — реальное имя роли нужно узнать через `docker exec karaoke-db env | grep '^POSTGRES_USER='`
(узкий grep, не полный дамп env — иначе утечёт пароль) и передать `-U <тот_user>` в psql. `docker exec`
обязательно с флагом `-i`, иначе heredoc не долетает до psql (команда завершается без ошибки, но ничего
не выполняет).

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

## Язык сессий и суб-агентов Claude Code

Пользователь работает на русском — это распространяется и на имена сессий, и на сообщения суб-агентов.

- **Имена сессий — по-русски.** Отображаемое имя сессии в `claude --resume` берётся из последней записи
  `custom-title` в её `.jsonl`-файле (`~/.claude/projects/-home-nsa-Karaoke/<sessionId>.jsonl`) — она
  перекрывает автогенерируемый `ai-title` (обычно английский kebab-case). Переименование = дописать в
  конец файла строку `{"type":"custom-title","customTitle":"<имя>","sessionId":"<id>"}` (JSON с
  `ensure_ascii=False`). 2026-07-05 так переименованы 32 старых сессии.
- **Суб-агенты — тоже по-русски.** Поле `"language": "Russian"` в `~/.claude/settings.json` применяется
  только к основному циклу и НЕ пробрасывается в суб-агентов (Agent/Task/Workflow). Директива в
  `~/.claude/CLAUDE.md` встроенных агентов (`general-purpose`, `Explore`, `Plan` и т.п.) тоже не
  переключает — проверено. Единственный надёжный канал: добавлять указание «Отвечай на русском языке»
  **прямо в промпт каждого запускаемого агента**, т.к. их системный промпт не редактируется.

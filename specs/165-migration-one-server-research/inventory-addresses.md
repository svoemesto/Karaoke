# Inventory адресов и хостов двухсерверной топологии

> **Спека**: 165-migration-one-server-research
> **Дата**: 2026-09-23
> **Автор**: research-субагент
> **Статус**: инвентаризация (read-only, ничего не менялось)

## Контекст миграции

- Старый прод: `188.119.64.111` (`sm-karaoke.ru`).
- Старый storage (remote MinIO): `89.125.103.63` (порт 9000).
- Новый единый хост: `188.127.240.124` (hostname `sm-karaoke`).
- Admin-машина `nsa-i9` (karaoke-app + LOCAL Postgres) — **остаётся отдельной**.
- `minio-proxy` — nginx на проде, проксирующий remote MinIO (обход MTU).
- two-DB sync: admin LOCAL Postgres ↔ prod Postgres через `DB_REMOTE_HOST`.

**Важно**: новый IP `188.127.240.124` в репозитории **не встречается ни разу** — миграция ещё не начата.

---

## Секция 1. IP-адреса

| Файл:строка | Что зашито | Менять? | На что |
|---|---|---|---|
| `deploy/.env:12` | `PROD_HOST=188.119.64.111` | ДА | `188.127.240.124` |
| `deploy/.env:23` | `DB_REMOTE_HOST=188.119.64.111` | ДА | `188.127.240.124` (порт 5433 не меняется) |
| `deploy/deploy_web.sh:16` | fallback `ssh root@${PROD_HOST:-188.119.64.111}` | ДА | `188.127.240.124` |
| `deploy/deploy_public.sh:16` | fallback `ssh root@${PROD_HOST:-188.119.64.111}` | ДА | `188.127.240.124` |
| `tools/analyze-prod-incident.sh:36` | `PROD_HOST="188.119.64.111"` | ДА | `188.127.240.124` |
| `tools/analyze-prod-incident.sh:12` | комментарий с `188.119.64.111` | ДА (док) | обновить текст |
| `deploy/web-server-deploy/deploy/80to8897:7` | `proxy_pass http://89.125.103.63:9000/` (443 SSL, /minio/) | ДА | `http://127.0.0.1:8890/` (MinIO теперь локально) |
| `deploy/web-server-deploy/deploy/80to8897:8` | `proxy_set_header Host 89.125.103.63` | ДА | `127.0.0.1:8890` (или `$host`) |
| `deploy/web-server-deploy/deploy/80to8897:123` | `proxy_pass http://89.125.103.63:9000/` (80, /minio/) | ДА | `http://127.0.0.1:8890/` |
| `deploy/web-server-deploy/deploy/80to8897:124` | `proxy_set_header Host 89.125.103.63` | ДА | `127.0.0.1:8890` (или `$host`) |
| `deploy/karaoke-web/minio-proxy-local.conf:5` | комментарий упоминает `89.125.103.63` | ДА (док) | `188.127.240.124:8890` |
| `deploy/web-server-deploy/deploy/nginx.conf:22` | `allow 95.31.39.146;` (PG stream) | ПРОВЕРИТЬ | allowlist admin-IP; если не изменился — оставить |
| `deploy/web-server-deploy/deploy/nginx.conf:23` | `allow 185.26.28.109;` | ПРОВЕРИТЬ | то же |
| `deploy/Настройка сервера.md:15,16,20,21,22,23,34,45,74,75,84,85` | доки `scp ... root@${PROD_HOST:-188.119.64.111}` | ДА (док) | `188.127.240.124` |
| `deploy/karaoke-db/38_song_share_links.sql:25` | комментарий `ssh root@${PROD_HOST:-188.119.64.111}` | ДА (док) | обновить |
| `deploy/karaoke-db/40_site_user_can_self_assign_tasks.sql:8` | комментарий с `188.119.64.111` | ДА (док) | обновить |
| `deploy/karaoke-db/41_db_indexes_verification.sql:31` | комментарий с `188.119.64.111` | ДА (док) | обновить |
| `deploy/karaoke-db/45_site_user_can_work_with_skipped.sql:13` | комментарий с `188.119.64.111` | ДА (док) | обновить |
| `deploy/karaoke-db/08,09,10,12,13,14,15,16,17,19,20,21,22 *.sql` | комментарии с устаревшим прод-IP `79.174.95.69:8832` | ОПЦИОНАЛЬНО (док) | отметить как устаревшее / убрать |
| `karaoke-app/src/main/kotlin/.../services/StorageApiClient.kt:135` | `@Value("${storage.remote-endpoint:http://89.125.103.63:9000}")` | ДА | default `http://188.127.240.124:8890` |
| `karaoke-app/src/main/kotlin/.../services/StorageApiClient.kt:115` | комментарий с `89.125.103.63:9000` | ДА (док) | обновить |
| `karaoke-app/src/main/resources/application.yml:40` | `db-remote-host: ${DB_REMOTE_HOST:188.119.64.111}` | ДА | default `188.127.240.124` |
| `karaoke-app/src/main/resources/application.yml:57` | `remote-endpoint: ${STORAGE_REMOTE_ENDPOINT:http://89.125.103.63:9000}` | ДА | default `http://188.127.240.124:8890` |
| `karaoke-app/.../Connection.kt:127` | комментарий `дефолт 188.119.64.111:8832` | ДА (док) | обновить |
| `karaoke-web/src/main/resources/application.yml:37` | `db-remote-host: ${DB_REMOTE_HOST:188.119.64.111}` | ДА | default `188.127.240.124` |
| `karaoke-web/.../services/WebKaraokeStorageServiceImpl.kt:15` | комментарий `89.125.103.63` | ДА (док) | обновить |
| `karaoke-web/.../controllers/PublicOgSongController.kt:522` | комментарий `http://89.125.103.63:9000/` | ДА (док) | обновить |
| `docs/ops/log-correlation.md:7,89,92,95,102,169,196,226,232,233,260,275,276` | доки `ssh root@188.119.64.111` | ДА (док) | `188.127.240.124` |
| `docs/architecture-notes.md:88` | исторический комментарий `ssh root@188.119.64.111` | НЕТ (история Pass 318) | оставить как исторический факт |
| `webvue3/nginx_webvue3.conf:33,34` | `http://10.0.1.7:8899/apis` (устаревший LAN-IP) | ОТДЕЛЬНАЯ ЗАДАЧА | на `karaoke-app:8899` (см. `/api` ниже) |
| `deploy/new_comp/sm-karaoke-system/deploy/nginx.conf:33,34` | `http://192.168.8.104:8898/api` | НЕТ (архив) | устаревшая копия new_comp |
| `.idea/dataSources.xml:11,15` | `karaoke@79.174.95.69:8832` | НЕТ (IDE) | локальный конфиг IDE |

---

## Секция 2. Переменные окружения и properties

| Переменная / property | Где задаётся | Где читается | Новое значение |
|---|---|---|---|
| `PROD_HOST` | `deploy/.env:12` | `deploy/deploy_web.sh:16`, `deploy/deploy_public.sh:16` (fallback) | `188.127.240.124` |
| `DB_REMOTE_HOST` | `deploy/.env:23`; `deploy/web-server-deploy/deploy/docker-compose-web.yml:24` | `karaoke-app/application.yml:40`; `karaoke-web/application.yml:37`; `KaraokeAppService.kt:41,51`; `KaraokeWebService.kt`; `Connection.kt`; `ProdContainerCheck.kt:9,218` | `188.127.240.124` |
| `DB_REMOTE_PORT` | `deploy/.env:24` (=5433); `deploy/docker-compose-app.yml:32` (хардкод 5433); prod compose:25 | `application.yml` обоих модулей; `Connection.kt:162` | `5433` (не меняется — nginx stream на новом хосте) |
| `WORK_ON_SERVER` | prod `docker-compose-web.yml:10`=1; local `docker-compose-web.yml:10`=0; `new_comp app`=0 | `application.yml:35/32`; `KaraokeAppService.kt:36`; `KaraokeWebService.kt` | не меняется |
| `STORAGE_REMOTE_ENDPOINT` | **нигде не задаётся в deploy** (только default в коде) | `karaoke-app/application.yml:57`; `StorageApiClient.kt:135` | добавить в `deploy/.env`: `http://188.127.240.124:8890` |
| `STORAGE_PROXY_URL` | prod `docker-compose-web.yml:45`=`http://minio-proxy`; local:28=`http://karaoke-minio-proxy` | `karaoke-web/application.yml:79` → `storage.proxy-url`; `PublicApiController.kt:70`; `PublicPlayerController.kt:90`; `PublicStemJobController.kt:50` | `http://minio-proxy` (если nginx-обход остаётся) ИЛИ `http://karaoke-storage:9000` (если MTU больше не мешает) — РЕШИТЬ |
| `STORAGE_PORT_HOST` | `deploy/.env:55`=8890; prod `.env:27`=8890 | `application.yml` обоих модулей; `docker-compose-storage.yml:11` | `8890` (теперь реально нужен на проде) |
| `STORAGE_CONTAINER_NAME` | `deploy/.env:59`; prod `.env:31` = `karaoke-storage` | `application.yml` | без изменений |
| `STORAGE_PORT_INSIDE_CONTAINER` | `.env:56`/prod:28 = 9000 | `docker-compose-storage.yml` | 9000 |
| `CAPTCHA_PROXY_URL` | prod `docker-compose-web.yml:28`=`http://minio-proxy/smartcaptcha` | `karaoke-web/application.yml:51` | не обязательно менять (nginx-прокси остаётся), но после объединения MTU-обход не нужен |
| `YOOKASSA_API_URL` | prod `docker-compose-web.yml:31`=`http://minio-proxy/yookassa` | `karaoke-web/application.yml:57` | то же |
| `PUBLIC_SITE_URL` | default `application.yml:45`=`https://sm-karaoke.ru` | `PublicCartController`, `PublicShareController`, `PublicSubscriptionController` | без изменений (домен) |
| `VK_ID_ADMIN_API_URL` | `deploy/do.env:14`; prod `do.env:20` = `http://nsa-i9:8898` | `karaoke-web/application.yml:105`; `PublicVkIdAuthController.kt:67` | **НЕ ТРОГАТЬ** (admin-машина) |
| `VK_ADMIN_API_URL` | default `application.yml:96`=`http://nsa-i9:8898` | `PublicVkAuthController.kt:50` | **НЕ ТРОГАТЬ** (admin-машина) |
| `VK_REDIRECT_URI` / `VK_ID_REDIRECT_URI` | `deploy/do.env:10,13`; prod `do.env:16,19` = `https://sm-karaoke.ru/...` | `karaoke-web/application.yml:94,104` | без изменений |
| `stemJobsWebInternalUrl` | runtime `/sm-karaoke/system/Karaoke.properties` = `https://sm-karaoke.ru` | `Karaoke.kt:346`; `StemJobPollScheduler.kt`; `StemJobProcessing.kt:226` | без изменений (домен); файл runtime, НЕ в репо |
| `storage.remote-endpoint` (property) | default в `application.yml:57` | `StorageApiClient.kt:135` | см. `STORAGE_REMOTE_ENDPOINT` |
| `storage.proxy-url` (property) | `application.yml:79` (default `http://minio-proxy`) | `PublicApiController`/`PublicPlayerController`/`PublicStemJobController` | см. `STORAGE_PROXY_URL` |

**Замечание (латентный баг)**: prod-копия `deploy/web-server-deploy/deploy/.env` **не содержит** `DB_REMOTE_HOST`, хотя prod `docker-compose-web.yml:24` его передаёт. Пустое значение перекрывает default в `application.yml`. На проде `remote()` в karaoke-web не используется, но при миграции стоит определить явно.

---

## Секция 3. nginx-конфиги

### `deploy/web-server-deploy/deploy/80to8897` (прод, главный)

| Location | Сейчас | Меняется? |
|---|---|---|
| `/minio/` (server 443, строки 6-21) | `proxy_pass http://89.125.103.63:9000/`; `Host 89.125.103.63` | ДА → `127.0.0.1:8890` |
| `/minio/` (server 80, строки 122-133) | `proxy_pass http://89.125.103.63:9000/`; `Host 89.125.103.63` | ДА → `127.0.0.1:8890` |
| `@minio_404` | кэш 404 | НЕТ |
| `/api/public/zakroma/stream` | `127.0.0.1:8897` (karaoke-web) | НЕТ |
| `/api/` | `127.0.0.1:8897` | НЕТ |
| `/changerecords` | `127.0.0.1:8897` | НЕТ |
| `/song` | `127.0.0.1:7907` (karaoke-public) | НЕТ |
| `/` | `127.0.0.1:7907` | НЕТ |
| `/smartcaptcha/` | `https://smartcaptcha.yandexcloud.net/` | НЕТ |
| `/yookassa/` | `https://api.yookassa.ru/v3/` | НЕТ |
| redirect `listen 80 /` | `301 https://sm-karaoke.ru` | НЕТ (домен) |
| `server_name` | `sm-karaoke.ru www.sm-karaoke.ru` (+ `minio-proxy` на 80) | НЕТ |

### `deploy/web-server-deploy/deploy/nginx.conf` (хостовый nginx прода)

| Блок | Сейчас | Меняется? |
|---|---|---|
| `stream / upstream postgresql_backend` | `server 127.0.0.1:8832;` | НЕТ |
| `stream server listen 5433` | proxy на upstream, `allow 95.31.39.146; allow 185.26.28.109; deny all;` | ПРОВЕРИТЬ allowlist admin-машины |
| `map $http_user_agent $vk_parser_backend` | `127.0.0.1:7907` / `127.0.0.1:8897` | НЕТ |

### Прочие nginx

| Файл | Location/upstream | Меняется? |
|---|---|---|
| `karaoke-public/nginx_karaoke-public.conf:36,49,57` | `/api/public`, `/api/replacesymbolsinsong`, `/api/storage` → `http://karaoke-web:7799` | НЕТ |
| `karaoke-public/nginx_karaoke-public.conf:73-74` | `/minio/` → `http://minio-proxy` (docker alias; на проде перехватывается хостовым nginx раньше) | НЕТ, если `minio-proxy` остаётся |
| `deploy/karaoke-web/minio-proxy-local.conf:11` | `/minio/` → `http://karaoke-storage:9000/` (локальный сайдкар) | НЕТ |
| `webvue3/nginx_webvue3.conf:33-34` | `/apis` → `http://10.0.1.7:8899/apis` (устаревший IP) | ОТДЕЛЬНАЯ ЗАДАЧА (не топология) |
| `webvue3/nginx_webvue3.conf:53-58` | `/api` → `$karaoke_app_upstream` = `http://karaoke-app:8899` | НЕТ |
| `deploy/new_comp/.../nginx.conf:33-34` | `http://192.168.8.104:8898/api` | НЕТ (архив) |

---

## Секция 4. Скрипты деплоя

| Файл | Что поправить |
|---|---|
| `deploy/.env` | `PROD_HOST` → `188.127.240.124`; `DB_REMOTE_HOST` → `188.127.240.124`; добавить `STORAGE_REMOTE_ENDPOINT=http://188.127.240.124:8890` |
| `deploy/deploy_web.sh:16` | fallback-IP → `188.127.240.124`. **Баг**: скрипт не source'ит `.env`/`do.env`, поэтому `PROD_HOST` берётся только из окружения, иначе fallback |
| `deploy/deploy_public.sh:16` | то же, fallback-IP |
| `tools/analyze-prod-incident.sh:12,36` | `PROD_HOST` const и комментарий → `188.127.240.124`; проверить `prod path` `/var/log/nginx`, `docker logs` |
| `deploy/Настройка сервера.md` | все `scp`/`ssh` инструкции → новый хост; добавить шаг установки MinIO-контейнера (`docker-compose-storage.yml`) на прод |
| `deploy/web-server-deploy/deploy/.env` | теперь реально задействуются `STORAGE_FOLDER`, `STORAGE_PORT_HOST=8890`, `STORAGE_CONTAINER_NAME=karaoke-storage`; путь `STORAGE_FOLDER=/root/Karaoke/storage` может отличаться на новом хосте |
| `deploy/web-server-deploy/deploy/do.sh` | уже содержит `do_start_storage`/`do_stop_storage` (строки 82-91) и включает storage в `do_ps` — MinIO теперь поднимается на том же хосте |
| `deploy/karaoke-db-backup.sh` | без IP (читает `.env`, `docker exec karaoke-db`) — не меняется |
| `deploy/docker-compose-app.yml:32` | `DB_REMOTE_PORT=5433` — оставить; `DB_REMOTE_HOST` тут не задан (берётся из `.env` admin-машины) |
| `deploy/web-server-deploy/deploy/docker-compose-storage.yml` | MinIO-контейнер для нового единого хоста; `ports: ${STORAGE_PORT_HOST}:9000` (8890:9000) — уже корректен |
| `deploy/do.sh` | IP не содержит; но для admin-машины стоит задать `STORAGE_REMOTE_ENDPOINT` (иначе используется новый default в application.yml) |

**Прецедент путей**: `deploy_web.sh`/`deploy_public.sh` делают `cd Karaoke/deploy`, а prod-доки говорят о `/sm-karaoke/system/deploy` (см. specs 289). На новом хосте путь деплоя нужно зафиксировать.

---

## Секция 5. Java/Kotlin-код — хардкод

**Хардкод IP-адресов в Kotlin: ОТСУТСТВУЕТ.** Все адреса приходят через `@Value`/`application.yml` (см. Секцию 2). Найдены только:

1. **Дефолты в `application.yml`** (не «код», но зашиты в jar-ресурс):
   - `karaoke-app/.../application.yml:40` — `db-remote-host: ...188.119.64.111`
   - `karaoke-app/.../application.yml:57` — `remote-endpoint: ...89.125.103.63:9000`
   - `karaoke-web/.../application.yml:37` — `db-remote-host: ...188.119.64.111`
   - `karaoke-app/.../StorageApiClient.kt:135` — дублирующий default `http://89.125.103.63:9000` в `@Value`.

2. **Доменные имена `sm-karaoke.ru`** (не IP; при переезде на тот же домен менять не нужно):
   - `karaoke-app/config/WebClientConfig.kt:18` — `baseUrl("https://sm-karaoke.ru/api/storage")` (legacy-бин от старой HTTP-прослойки; `StorageApiClientImpl` ходит напрямую через MinioClient).
   - `karaoke-web/config/WebClientConfig.kt:26` — `baseUrl("https://sm-karaoke.ru/api/storage")` (`@Primary`, используется `StorageApiClientWeb`).
   - `karaoke-app/services/SyncRemoteClient.kt:31` — `URL = "https://sm-karaoke.ru/changerecords"`.
   - `karaoke-app/monitor/checks/ProdContainerCheck.kt:56` — `PING_URL = "https://sm-karaoke.ru/"`; SSH-диагностика использует `DB_REMOTE_HOST` (строка 218).
   - `karaoke-app/Constants.kt:110` — `URL_PREFIX_SM`.
   - `karaoke-app/services/{Vk,News,Telegram}TemplateService.kt` — `https://sm-karaoke.ru/song?id={id}`.
   - `karaoke-web/controllers/PublicOgSongController.kt:107,518,525` — canonical, `FALLBACK_LOGO_URL`, `MINIO_BASE_URL`.
   - `karaoke-app/model/Song.kt:4772,4949` — текст с `sm-karaoke.ru`.
   - `karaoke-app/src/main/resources/templates/{publications,unpublications}.html` — ссылки `sm-karaoke.ru`.
   - Runtime `/sm-karaoke/system/Karaoke.properties` — `stemJobsWebInternalUrl = "https://sm-karaoke.ru"` (файл вне репо, менять не нужно).

3. **Комментарии/KDoc с IP** (см. Секцию 1): `StorageApiClient.kt:115`, `Connection.kt:127`, `WebKaraokeStorageServiceImpl.kt:15`, `PublicOgSongController.kt:522`.

**Вывод**: в Kotlin править нужно только два дефолта в `application.yml` (+ дублирующий default в `StorageApiClient.kt`) и два комментария с IP. Логика доступа к MinIO/karaoke-web завязана на `storage.remote-endpoint`, `storage.proxy-url`, `DB_REMOTE_HOST` — то есть на конфиг, а не на код.

---

## Секция 6. Выводы

### Критично поменять (блокеры миграции)

1. **`deploy/web-server-deploy/deploy/80to8897:7,8,123,124`** — `/minio/` больше не ходит на `89.125.103.63`; переключить на локальный MinIO `127.0.0.1:8890` (оба server-блока: 443 и 80).
2. **`deploy/.env:12,23`** — `PROD_HOST` и `DB_REMOTE_HOST` → `188.127.240.124`. Это ядро two-DB sync и деплой-скриптов.
3. **`karaoke-app/src/main/resources/application.yml:57` + `karaoke-app/.../StorageApiClient.kt:135`** — default `storage.remote-endpoint` → `http://188.127.240.124:8890` (admin-машина ходит в remote MinIO напрямую). Дополнительно задать `STORAGE_REMOTE_ENDPOINT` в `deploy/.env`, чтобы не полагаться на default.
4. **`karaoke-app/application.yml:40` + `karaoke-web/application.yml:37`** — default `db-remote-host` → `188.127.240.124`.
5. **Развернуть MinIO на новом едином хосте**: `deploy/web-server-deploy/deploy/docker-compose-storage.yml` (`STORAGE_PORT_HOST=8890` → `127.0.0.1:8890` для nginx). Раньше контейнер жил на storage-сервере.
6. **Скрипты**: `deploy/deploy_web.sh:16`, `deploy/deploy_public.sh:16`, `tools/analyze-prod-incident.sh:36` — адрес нового хоста; проверить allowlist `nginx.conf:22-23`.
7. **`STORAGE_PROXY_URL`** (prod `docker-compose-web.yml:45`): решить, оставлять ли обход через `minio-proxy` (теперь MTU-проблемы на одном хосте нет) или указать напрямую `http://karaoke-storage:9000`.

### НЕ трогать (admin-машина остаётся отдельной)

- `VK_ID_ADMIN_API_URL` / `VK_ADMIN_API_URL` = `http://nsa-i9:8898` (`deploy/do.env:14`, prod `do.env:20`, `application.yml:96,105`, дефолты в `PublicVkAuthController.kt:50`, `PublicVkIdAuthController.kt:67`).
- Все упоминания `nsa-i9` в документации и мониторинге.
- Локальные docker-compose admin-машины: `deploy/docker-compose.yml`, `docker-compose-app.yml`, `docker-compose-web.yml:28` (`http://karaoke-minio-proxy`), `deploy/karaoke-web/minio-proxy-local.conf`.
- Admin LOCAL Postgres: `DB_LOCAL_POSTGRES_*`, `localhost:8832`, `karaoke-db:5432`.
- `PUBLIC_SITE_URL`, `VK_*_REDIRECT_URI`, `stemJobsWebInternalUrl`, доменные `sm-karaoke.ru` в коде и шаблонах.

### Отдельные (не блокеры этой миграции) задачи

- `webvue3/nginx_webvue3.conf:33-34` — устаревший `10.0.1.7` вместо `karaoke-app:8899`.
- `deploy/karaoke-db/*.sql` и `knowledge/*.md` с ещё более старым IP `79.174.95.69:8832` — исторические комментарии, желательно пометить устаревшими.
- `.idea/dataSources.xml` — локальный IDE-конфиг, не деплой.
- Prod `.env` не содержит `DB_REMOTE_HOST` — определить явно.

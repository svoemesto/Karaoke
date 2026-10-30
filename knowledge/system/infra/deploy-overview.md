# Component: deploy (Docker + nginx + scripts)

> **Домен**: system (infrastructure)
> **Компонента**: обзор deploy/ — конфигурация деплоя проекта.

## Назначение

`deploy/` содержит Dockerfiles, docker-compose
конфигурации, nginx конфиги, скрипты деплоя, .env. Деплой
происходит на **двух машинах**:

1. **Admin-машина** (`nsa-i9`): `karaoke-app` + `karaoke-web` (admin) +
   `karaoke-public` (admin) + `webvue3` + БД.
2. **Прод-сервер** (`188.127.240.124`): `karaoke-web` (прод) +
   `karaoke-public` (публичный) + **локальный MinIO** + БД + host-nginx.

## Прод — один хост (с 2026-09-23)

> **Переезд 2026-09-23** (wayfinder #165, спека #178): прод-сервер и
> storage-сервер **объединены в один хост** `188.127.240.124`
> (`sm-karaoke.ru`, Ubuntu 26.04). Раньше MinIO жил на отдельном хосте
> `89.125.103.63`; теперь — локальный контейнер, доступ через host-nginx
> `/minio/` → `127.0.0.1:8890`. Канон deploy для нового хоста —
> `deploy/prod-single-host/` (старый `deploy/web-server-deploy/` — deprecated).

## Корни deploy

| Путь | Где | Назначение |
|---|---|---|
| `~/Karaoke/deploy/` | Admin-машина | **Основной** deploy отсюда |
| `deploy/prod-single-host/` | Репо | **Канон** однохостового прода (из реального `/root/Karaoke/deploy`) |
| `deploy/prod-single-host/registry/` | Репо | **Приватный registry**: `config.json`, `htpasswd` (не в git), `images.lock`, `RUNBOOK.md` |
| `deploy/web-server-deploy/deploy/` | Репо (legacy) | Устаревшая двуххостовая копия, **deprecated** — не использовать |

Каждая содержит свои `do.sh`, `docker-compose-*.yml`,
`nginx.conf`, `.env` (с реальными секретами в `do.env`, в git не трекаются).

## Главный скрипт: `do.sh`

**Файл**: `deploy/do.sh` — единая точка входа для всех операций.

**Команды** (по grep `^function\|^[a-z_]*\s*()\s*{`):

- `build_*` — сборка образов.
- `start_*` — запуск контейнеров.
- `stop_*` / `restart_*` — управление.
- `karaoke-app`, `karaoke-web`, `karaoke-public`, `webvue3`, `minio` и
  др.

**NB**: точная сигнатура — Pass 343+ (нужно grep'нуть).

## Docker конфигурации

### Dockerfiles (8 штук)

| Dockerfile | Что |
|---|---|
| `deploy/karaoke-app/Dockerfile` | **Текущий** karaoke-app |
| `deploy/karaoke-app/DockerfileBackup`, `Backup2`, `Old`, `Full`, `Demucs` | Старые версии (для отката) |
| `deploy/karaoke-public/Dockerfile` | karaoke-public |
| `deploy/karaoke-webvue3/Dockerfile` | webvue3 (admin UI) |
| `deploy/web-server-deploy/deploy/karaoke-web/Dockerfile` | karaoke-web (прод) |

NB: `Dockerfile*` несколько штук = эксперименты с разными образами
(JDK vs JRE, full vs slim, и т.д.). Текущий — `Dockerfile` без
суффикса.

### docker-compose-*.yml

| Файл | Назначение |
|---|---|
| `docker-compose-app.yml` | karaoke-app + БД + storage на admin |
| `docker-compose-public.yml` | karaoke-public на admin (для dev) |
| `docker-compose-web.yml` | karaoke-web на прод |
| `docker-compose-database.yml` | Только БД |
| `docker-compose-storage.yml` | MinIO на прод (2026-09-23: **локальный контейнер** на том же хосте) |
| `docker-compose-whisper.yml` | Whisper ASR service |
| `docker-compose-telegram-proxy.yml` | Telegram SOCKS proxy |
| `tracker-docker-compose.yml` | OpenProject tracker (для задач) |
| `prod-single-host/docker-compose-registry.yml` | Приватный registry (Zot) на прод-хосте, `127.0.0.1:5000` |

**Лимит соединений БД.** Контейнер `karaoke-db` поднимается с
`max_connections=300` (переопределяется `DB_MAX_CONNECTIONS`; параметр задан в
`docker-compose-database.yml` и `prod-single-host/docker-compose-database.yml`).
Приложение держит по одному физическому соединению на поток (см.
[local-ADR-0009](../../adr/local-0009-thread-local-connection-lifecycle.md) и
[домен persistence](../../domains/persistence/domain.md)), поэтому запас по
лимиту — страховка на время, пока не решено, что делать с каналами потоков,
завершаемых пулом. Признак исчерпания: `FATAL: sorry, too many clients
already` в логах, очередь `KaraokeProcess` не отвечает (`GET
/process/isworking` → `false`), сайт отдаёт 5xx.

**Проверка состояния** (число занятых соединений на сервере БД):

```sql
SELECT count(*) FROM pg_stat_activity;
```

Сравнить с `max_connections`. Устойчивое состояние — число соединений не
растёт от прогона к прогону: одноразовые фоновые потоки обязаны закрывать своё
соединение по завершении (см. [домен persistence](../../domains/persistence/domain.md),
§ «Соединение на поток»). Ответ «перезапустить БД» проблему **не** решает:
канал закрывается в приложении, а не на стороне БД.

**Предел памяти контейнера `karaoke-app`** (BR-630, спека 676 / KARAOKE-245).

`karaoke-app` живёт **на admin-машине `nsa-i9`** (на прод-хосте его нет).
До 2026-10-30 у контейнера не было ни предела памяти, ни дампов heap:
`-Xmx8g` жил только в **неотслеживаемом** `deploy/.env`, а в инциденте
2026-10-23 JVM без cgroup-лимита вытесняла память хоста — хост уходил в
swap, погиб HttpClient-монитор, и 5+ часов держался ложный CRITICAL
«прод недоступен» при живом прод-сайте.

Теперь лимиты объявлены в **отслеживаемой** конфигурации
(`deploy/docker-compose-app.yml`):

| Параметр | Значение | Где |
|---|---|---|
| Предел памяти контейнера | `mem_limit: 8g` | compose |
| Максимум heap JVM | `-Xmx6g` | compose, **после** `${APP_JAVA_OPTS}` |
| Дамп состояния heap при OOM | `-XX:+HeapDumpOnOutOfMemoryError`, `-XX:HeapDumpPath=/dumps` | compose |
| Том дампов | `./karaoke-db/dumps:/dumps` | compose (тот же каталог, что у `karaoke-db`, BR-046) |
| Пороги алерта загрузки heap | `MONITOR_HEAP_WARN_PERCENT=75`, `MONITOR_HEAP_CRITICAL_PERCENT=90` | compose (`environment:`) |

**Порядок `-Xmx6g` после `${APP_JAVA_OPTS}`** — принципиален: последний
`-Xmx` побеждает (`java -Xmx8g -Xmx6g` → `MaxHeapSize` = 6 GiB), поэтому
устаревшее `-Xmx8g` из неотслеживаемого `deploy/.env` перекрывается, а
связь «heap 6 ГБ < предел контейнера 8 ГБ» видна и проверяется из
репозитория. Значение в `deploy/.env` само по себе не влияет на лимит.

**Политика хранения дампов** — `deploy/heap-dump-retention.sh`: последние
**2** дампа, суммарный лимит тома **20 ГБ**, вытеснение по возрасту,
защита 60 минут (недописанный дамп не удаляется). Работает **без
установки** (`bash deploy/heap-dump-retention.sh`); systemd-юниты
(`deploy/heap-dump-retention.{service,timer}`) — необязательная
автоматизация, их установка и включение — действие владельца. Политика
трогает **только** `*.hprof`: каталог общий с SQL-дампами БД, они не
вытесняются никогда.

**Проверяется автоматически**: `tools/check-memory-guardrails.sh` (быстрый
guard, в pre-commit и CI) — предел памяти, `-Xmx` и его отношение к
пределу, порядок флагов, флаги дампа, том, пороги и значения retention-политики.
Ручной `tools/check-deploy-hardening.sh` делегирует ему же правило BR-630.

## nginx конфигурации

- `deploy/web-server-deploy/deploy/nginx.conf` — главный.
- `deploy/karaoke-web/minio-proxy-local.conf` — MinIO proxy.

**Паттерн**: nginx как **path-proxy** для всех внешних
сервисов. Обход MTU black-hole, SigV4-подмена, rate limiting.

## Скрипты деплоя

| Скрипт | Что |
|---|---|
| `deploy_public.sh` | Деплой karaoke-public на прод |
| `deploy_web.sh` | Деплой karaoke-web на прод |
| `do.sh build_start_public` | Сборка + запуск karaoke-public |
| `karaoke-db-backup.sh` | Бэкап БД. Пароль — через stdin + одноразовый `.pgpass` в контейнере (BR-539, пакет #371); без пароля `pg_dump --no-password` падал с `fe_sendauth: no password supplied` и дампов не было вообще |
| `tracker-db-backup.sh` | Бэкап БД трекера (OpenProject). Пароль — через stdin + одноразовый `.pgpass` в контейнере, не через argv (BR-552) |
| `announce.sh` | Анонс в Telegram/VK после деплоя |
| `prune-images.sh` | Очистка старых Docker images + контроль свободного места (порог 85 %, BR-546) |
| `analyze-prod-incident.sh` | Анализ prod incident |

## Приватный registry (с 2026-10-11)

Образы проекта больше не берутся с Docker Hub: они живут в своём registry на том же
прод-хосте. Подробности — [registry.md](registry.md); здесь только место в раскладке.

| Что | Где |
|---|---|
| Контейнер `karaoke-registry` (Zot v2.1.22) | `deploy/prod-single-host/docker-compose-registry.yml`, слушает `127.0.0.1:5000` |
| Наружу | host-nginx `deploy/prod-single-host/5443to5000` → HTTPS `sm-karaoke.ru:5443`, тот же сертификат домена |
| Конфиг, креды, пины | `deploy/prod-single-host/registry/{config.json,htpasswd,images.lock}` (монтируются как каталог `/etc/zot:ro`) |
| Данные | `/sm-karaoke/system/registry` (bind-mount, вне docker-конфайнмента) |
| Значения в `do.env` | `REGISTRY_HOST`, `REGISTRY_USER`, `REGISTRY_PASSWORD`, `DOCKER_REGISTRY=sm-karaoke.ru:5443/karaoke`, `DUAL_PUSH` |

Сборка на admin берёт базовые образы из нашего registry (`FROM
sm-karaoke.ru:5443/library/…`), прод получает свои образы вендорингом
(`mirror/**`), сторонние образы переносятся `tools/registry-vendor.sh` по
`registry/images.lock`. Guard R-04/R-05 (`tools/check-docker-image-tags.sh`)
расширен под registry-квалифицированные имена.

## Hardening S2 (аудит #336, пакет PKG-S2-08 / OpenProject #371)

Пакет закрыл 15 записей аудита по deploy-контуру. Инварианты, которые нельзя
откатывать (guard: `tools/check-deploy-hardening.sh`, 50 проверок):

| Тема | Инвариант | BR |
|---|---|---|
| Секреты | `WHISPER_API_KEY` / `SEARXNG_SECRET` — только из `deploy/do.env` (`${VAR:?…}`, fail-closed); шаблон — `deploy/do.env.example`; в `searxng-settings/settings.yml` — плейсхолдер `ultrasecretkey` (entrypoint заменяет его при старте) | 040 |
| Push | каждый `docker image push` и `do_registry_login` проверяют код возврата; «Image is up to date» в `deploy_*.sh` — **ошибка деплоя** (`exit 1`), а не WARN | 043 |
| Бэкапы | пароль БД передаётся в `pg_dump`; блокировка — `ExecStart=flock -n <lock> <script>` на весь прогон (не `ExecStartPre`) | 539, 543 |
| Сеть | БД/UI локальных сервисов публикуются только на `127.0.0.1`; у трекера убран `network_mode: host`, `DATABASE_URL` — по имени сервиса; порт БД new_comp-контура — loopback | 542, 556 |
| TLS | `ssl_protocols TLSv1.2 TLSv1.3`, `server_tokens off` в обоих nginx; серверный TLS Postgres включается `DB_SSL_ON=on` (по умолчанию off = прежнее поведение), клиент — `DB_REMOTE_SSL_MODE` (default `prefer`); целевое состояние — `require`/`verify-full` + HTTPS для MinIO | 544, 545, 557 |
| Логи | в каждом сервисе прод-compose `logging: json-file, max-size 50m, max-file 5`; `prune-images.sh` падает при занятости диска ≥ 85 % | 546 |
| Compose | `environment:` — список (`- VAR=value`), не смесь отображения и списка | 554 |
| Watcher | исполняется только `<uuid>.sh`, файл переносится в приватный каталог 700 (закрыто окно TOCTOU), проверяются владелец/права/размер; общая директория не должна быть group/other-writable | 555 |
| Миграции | каждый файл `deploy/karaoke-db/` идемпотентен (`IF NOT EXISTS`), потому что каталог монтируется в `/docker-entrypoint-initdb.d` и выполняется по порядку с `ON_ERROR_STOP=1`; миграция `18` создаёт `tbl_authors` (её не создавал ни один файл каталога) и bootstrap'ит recordhash-триггер | 039 |
| Каскады БД | `tbl_subscriptions.site_user_id` — `ON DELETE RESTRICT` (в `15` для новых БД + миграция `51` для существующих): платёжная история не удаляется вместе с пользователем | 041 |
| recordhash авторов | миграция `46` не должна переопределять recordhash-функцию: канонический набор — 17 колонок в фиксированном порядке, сама миграция самодостаточна и с backfill | 534 |

Что **осталось за владельцем** (зафиксировано в `specs/371-pkg-s2-08/report.md`):
сертификаты для TLS Postgres/MinIO и выбор «TLS vs SSH/WireGuard-туннель»;
судьба платёжной истории при удалении пользователя сайта; сервисный
пользователь для `karaoke_watcher.sh` и удаление bind-mount
`/sm-karaoke/system` из контейнеров, которым он не нужен; ротация и удаление
секретов, попавших в историю git.

## Hardening S3 (аудит #336, пакет PKG-S3-21 / OpenProject #393, ветка `611-pkg-s3-21`)

Продолжение волны E — 15 записей по deploy-контуру, миграциям и compose. Поведение
прода по умолчанию не меняется: все переключатели (TLS, порты, пароли) получили те
же значения, но заданы явно и проверяются guard'ом.

| Тема | Инвариант | BR |
|---|---|---|
| Тег образа | единый `IMAGE_TAG`: `deploy/do.sh` собирает и пушит `${BUILD_VERSION}` (из `deploy/.version` или окружения), `deploy_web.sh` / `deploy_public.sh` передают тег на прод по ssh (`BUILD_VERSION=<тег> bash do.sh pull_web`), прод-`do.sh` экспортирует `IMAGE_TAG`, compose подставляет `${IMAGE_TAG:-1}`; предыдущий тег — в `.version.prev` для отката | 042 |
| recordhash `tbl_site_users` | канонический `deploy/recordhash_site_users.sql` (19 колонок); миграции 12/13/14/17/21/40/45 повторяют ту же формулу и сами добавляют все колонки, чтобы backfill работал на чистой БД; `56` доставляет формулу на существующие БД | 044 |
| GIN-индекс | индекс строится по **колонке** (`01`/`02`), `02` пересоздаёт его через DROP+CREATE, `54` — на существующих БД; прежний индекс по константе `'result_text'` (в `tbl_settings`, а поиск идёт по `tbl_songs`) не работал | 045 |
| Скрипты БД | `restore_db` / `create_clear_db`: mount `./karaoke-db/dumps:/dumps`, `-U ${DB_LOCAL_POSTGRES_USER}`, `exec -i` (не `-it`), проверка кода возврата **до** `announce` | 046, 047 |
| Статусы песен | `UPDATE … WHERE id_status >= 3` переводил незавершённые песни (3/4/5) в READY → `WHERE id_status IN (3, 4, 5)` — повторный запуск действительно no-op | 535 |
| INSERT песни | перенос `album_id` (и поздних колонок) закрыт пакетом PKG-S2-10 (BR-108); здесь — регрессия в guard'е | 536 |
| Каскады хранилища | `tbl_stem_jobs.site_user_id` — `ON DELETE RESTRICT` (в `22` + миграция `55`); удаление — приложением (`StemJobCleanup` по `expires_at`), объекты MinIO не остаются сиротами | 537 |
| Триггеры | `44` обещала `CREATE OR REPLACE TRIGGER`, а делала `CREATE TRIGGER` → падала при повторе; теперь `DROP TRIGGER IF EXISTS` перед `CREATE TRIGGER`, шапка приведена в соответствие | 538 |
| Лимиты контейнеров | у прод-`karaoke-web` healthcheck (HTTP 200 `/api/public/authors-tiles`), `mem_limit`/`cpus` и mount `./karaoke-db/dumps:/dumps` для `-XX:HeapDumpPath=/dumps` | 547 |
| Лимиты памяти `karaoke-app` | на admin-машине: `mem_limit: 8g` + `-Xmx6g` (после `${APP_JAVA_OPTS}` — последний `-Xmx` побеждает), дамп heap при OOM на томе `./karaoke-db/dumps`, пороги алерта 75 %/90 %; retention-политика — последние 2 / ≤ 20 ГБ / по возрасту; guard — `tools/check-memory-guardrails.sh` (pre-commit + CI) | 630 |
| TTS-модель | sha256 модели зафиксирован; загрузка во временный файл + `os.replace` после сверки, проверка перед `load_pickle` (прежняя загрузка по pickle без сверки) | 548 |
| Заголовки публичного SPA | `X-Frame-Options` / `nosniff` / `Referrer-Policy` в server и в каждом location со своими `add_header`; guard расширен на этот файл (см. [security-headers.md](../../guidelines/security-headers.md)) | 549 |
| Размер тела | `client_max_body_size 200m` на публичном контуре — как `spring.servlet.multipart` и host-nginx (было `1024m`) | 550 |
| new_comp `do.sh` | `set -euo pipefail`, проверка наличия обоих env-файлов; ошибки compose валят скрипт (`source .env` без проверки и без fail-fast) | 559 |
| new_comp compose | `restart: unless-stopped`, healthcheck, `mem_limit`/`cpus` у app/web, `restart` у webvue/webvue3 | 560 |

Полные сводки «было → стало» по всем 30 записям этих двух пакетов — в
[`audit-336-registry.md`](audit-336-registry.md) (карточки PKG-S2-08 и PKG-S3-21).

## Секреты

- `deploy/web-server-deploy/deploy/do.env` — **продакшн секреты**
  (Postgres password, MinIO key/secret, Docker Hub PAT,
  YOOKASSA_SHOP_ID/SECRET, VK access_token, Telegram bot token).
- **НЕ должен трекаться** git (Constitution VIII, `.gitignore`).

## Архитектурные решения

### Решение 1: Single docker-compose per deploy

Каждая машина имеет **один docker-compose** для всех своих
сервисов. Это упрощает lifecycle.

### Решение 2: nginx как path-proxy (см. ADR-0003)

Все внешние вызовы (MinIO, Telegram, VK, YooKassa) — через
nginx. Это решает MTU black-hole.

### Решение 3: Hardcoded secrets в `do.env` (KNOWN RISK)

`do.env` НЕ трекается git (Constitution VIII). Но всё ещё
**уязвим** — если файл утечёт, все креденшелы скомпрометированы.
TODO: Vault, SOPS, или другое решение (Pass 343+).

## Известные TODO

- [ ] **Каждый docker-compose** — детальный services/networks/volumes.
- [ ] **Каждый nginx.conf** — locations, upstreams, rate limits.
- [ ] **`do.sh`** — полная сигнатура всех команд.
- [ ] **Hardcoded secrets в `do.env`** — замена на Vault/SOPS.
- [ ] **`DockerfileBackup*`** — какие из них актуальны, какие — мусор.

## Связь с другими компонентами

- **AGENTS.md** — define deploy workflow.
- **Constitution.md** — запрет на коммит секретов.

## Changelog

- **Спека 676** (2026-10-30, KARAOKE-245, BR-630): раздел о пределе памяти контейнера `karaoke-app` (8 ГБ / 6 ГБ), дампах heap и retention-политике; строка BR-630 в таблице Hardening S3. Автор: agent (Karaoke).

- **Pass 382** (2026-10-24, PKG-S2-08 / OpenProject #371): раздел «Hardening S2» —
  инварианты по секретам, push, бэкапам, сети, TLS, логам, compose и watcher'у;
  guard `tools/check-deploy-hardening.sh`.
- **Pass 540** (2026-10-11): Приватный контейнерный registry на прод-хосте (Zot,
  `127.0.0.1:5000` + host-nginx `5443to5000`); сборка и прод переведены на
  `sm-karaoke.ru:5443`; guard тегов расширен под registry-квалифицированные имена.
  issue #286 / wayfinder #274; подробности — [registry.md](registry.md).
- **Pass 440** (2026-09-23): Переезд прод+MinIO на один хост (`188.127.240.124`).
  Канон — `deploy/prod-single-host/`; `web-server-deploy/` deprecated. wayfinder #165 / спека #178.
- **Pass 354** (2026-09-09): Initial. Автор: agent (Karaoke).
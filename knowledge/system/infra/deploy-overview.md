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

Что **осталось за владельцем** (зафиксировано в `specs/371-pkg-s2-08/report.md`):
сертификаты для TLS Postgres/MinIO и выбор «TLS vs SSH/WireGuard-туннель»;
судьба платёжной истории при удалении пользователя сайта; сервисный
пользователь для `karaoke_watcher.sh` и удаление bind-mount
`/sm-karaoke/system` из контейнеров, которым он не нужен; ротация и удаление
секретов, попавших в историю git.

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
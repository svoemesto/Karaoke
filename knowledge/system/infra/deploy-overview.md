# Component: deploy (Docker + nginx + scripts)

> **Домен**: system (infrastructure)
> **Компонента**: обзор deploy/ — конфигурация деплоя проекта.

## Назначение

`deploy/` содержит 178 файлов: Dockerfiles, docker-compose
конфигурации, nginx конфиги, скрипты деплоя, .env. Деплой
происходит на **двух машинах**:

1. **Admin-машина** (`nsa-i9`): `karaoke-app` + `karaoke-web` (admin) +
   `karaoke-public` (admin) + `webvue3` + `minio-storage` + БД.
2. **Прод-сервер**: `karaoke-web` (прод) + `karaoke-public` (публичный) +
   MinIO (отдельный хост) + БД.

## Два корня deploy

| Путь | Где | Назначение |
|---|---|---|
| `~/Karaoke/deploy/` | Admin-машина | **Основной** deploy отсюда |
| `~/Karaoke/deploy/web-server-deploy/deploy/` | Прод | **Прод-сервер** (отдельная копия) |

Это исторически сложилось — две разные папки для разных машин.
Каждая содержит свои `do.sh`, `docker-compose-*.yml`,
`nginx.conf`, `.env` (с реальными секретами в `do.env`).

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
| `docker-compose-storage.yml` | MinIO на прод (отдельный хост) |
| `docker-compose-whisper.yml` | Whisper ASR service |
| `docker-compose-telegram-proxy.yml` | Telegram SOCKS proxy |
| `tracker-docker-compose.yml` | OpenProject tracker (для задач) |

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
| `karaoke-db-backup.sh` | Бэкап БД |
| `announce.sh` | Анонс в Telegram/VK после деплоя |
| `prune-images.sh` | Очистка старых Docker images |
| `analyze-prod-incident.sh` | Анализ prod incident |

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

- **Pass 354** (2026-09-09): Initial. Автор: agent (Karaoke).
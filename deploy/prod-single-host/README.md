# deploy/prod-single-host — канон деплоя на одном хосте

> **Статус**: Active. Заменяет `deploy/web-server-deploy/` (deprecated).
> **Контекст**: wayfinder #165, спека исполнения #178 (`specs/439-migration-one-server-exec/`).
> **Хост**: `sm-karaoke.ru` = `188.127.240.124` (Ubuntu 26.04, 6 vCPU, 3.8 ГБ, 985 ГБ).

Раскладка **одного** сервера: прод (`karaoke-web`, `karaoke-public`, PostgreSQL) +
**локальный MinIO** + host-nginx (TLS/прокси).

## Раскладка

```
/root/Karaoke/deploy/                  # этот каталог (do.sh, compose, nginx, бэкапы)
  ├── do.sh, do.env, .env              # do.env/.env — реальные секреты (scp, НЕ в git)
  ├── docker-compose-{database,storage,web,public}.yml
  ├── nginx.conf                       # host-nginx: stream{5433} + http
  ├── 80to8897                         # sites-enabled:443/:80 (sm-karaoke.ru)
  ├── karaoke-db/                      # init-скрипты Postgres (docker-entrypoint-initdb.d)
  ├── karaoke-db-backup.{sh,service,timer}   # бэкап 05:00, retention 7 дн
  └── karaoke-docker-prune.{service,timer}   # prune 04:15

/sm-karaoke/system/Караоке-db          # Postgres data (DB_FOLDER)
/sm-karaoke/system/Караоке-storage     # MinIO data (STORAGE_FOLDER)
/sm-karaoke/system/dumps               # дампы БД
/etc/keys/www.sm-karaoke.ru.{crt,key}  # TLS fullchain + key
/etc/nginx/nginx.conf                  # = nginx.conf отсюда
/etc/nginx/sites-enabled/80to8897      # = 80to8897 отсюда
```

## Отличия от legacy (двуххостового) deploy

| Аспект | Было | Стало |
|---|---|---|
| MinIO | отдельный хост `89.125.103.63` | локальный контейнер, `127.0.0.1:8890` |
| Образ MinIO | `minio/minio:latest` (Hub 404) | `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` |
| `/minio/` в nginx | `89.125.103.63:9000` | `127.0.0.1:8890` + HTTP-кэш 24 ч |
| `karaoke-web`/`karaoke-public` | порт наружу | loopback (наружу — host-nginx) |
| stream allow-list (5433) | несколько IP | только admin `185.26.28.109` |
| `DB_REMOTE_HOST` | `188.119.64.111` | `188.127.240.124` |
| heap `karaoke-web` | `-Xmx1048m` | `-Xmx1200m` (3.8 ГБ RAM) |
| `docker-compose` | v1 пакет | враппер `exec docker compose "$@"` |

## Порядок развёртывания

Пошагово — `specs/165-migration-one-server-research/cutover-runbook.md`.
Кратко: установить Docker+nginx+swap → каталоги → `scp` секретов и `/etc/keys`
→ скопировать сюда → `docker network create deploy_karaokenet` → `do.sh start_db`,
`start_storage` → bulk MinIO → restore БД → `start_web`, `start_public` →
`tools/migration-smoke.sh 188.127.240.124`.

## Важно

- `do.sh` требует команду `docker-compose` в `$PATH` (на Ubuntu 26.04 — враппер
  на `docker compose`).
- `do.sh` использует `${COMPOSE}` из `which docker-compose`; `do.env` — из этого каталога.
- Секреты (`do.env`, `.env`) НЕ коммитятся (`.gitignore`). Шаблоны — `*.example`.
- `deploy/new_comp/` — устаревший черновик, не использовать.

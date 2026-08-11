# Docker deploy (локальное окружение Karaoke)

> **Status**: active
> **Feature Key**: docker-deploy
> **Last Updated**: 2026-08-11 (Pass 170, воспроизводимая настройка Linux Mint 22.2)

## Что делает

Поднимает **9 локальных контейнеров** (8 обязательных + 1 опциональный `ollama`)
для полноценной локальной разработки проекта Karaoke. Включает бэкенд-приложение
(`karaoke-app`), публичный бэкенд (`karaoke-web`), админку (`karaoke-webvue3`),
публичный фронтенд (`karaoke-public`), поисковые движки (`searxng`, `fourget`),
PostgreSQL (`karaoke-db`) и MinIO-совместимое хранилище (`karaoke-storage`).
Воспроизводимая настройка через `specs/170-mint-dev-setup/setup-mint.sh` +
`deploy/do.env.template`.

## Зачем

Karaoke — self-pipeline. Разработчик на своей машине должен иметь полный
набор сервисов, идентичный (по возможности) прод-окружению, чтобы:
- видеть, как правка кода влияет на UI и API;
- тестировать локально без VPN/сервера;
- воспроизводимо поднимать среду на новой машине (онбординг);
- работать с PostgreSQL, MinIO, поисковыми движками, как в проде.

## Как работает

### Архитектура

```
┌──────────────────── HOST (Linux Mint 22.2 / Ubuntu 24.04) ────────────────────┐
│                                                                               │
│  ┌────────────┐  ┌────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │ karaoke-db │  │karaoke-    │  │ karaoke-app │  │ karaoke-web │            │
│  │ PostgreSQL │  │storage     │  │ Spring Boot │  │ Spring Boot │            │
│  │ :5432      │  │ MinIO      │  │ :8900       │  │ :8090       │            │
│  └────────────┘  │ :9000/:9001│  └─────────────┘  └─────────────┘            │
│                  └────────────┘         │                  │                  │
│  ┌────────────┐  ┌────────────┐         │                  │                  │
│  │ karaoke-   │  │ karaoke-   │         ▼                  ▼                  │
│  │ webvue3    │  │ public     │  ┌─────────────┐  ┌──────────────┐           │
│  │ nginx+vue3 │  │ nginx+vue3 │  │ searxng     │  │ fourget      │           │
│  │ :7906      │  │ :8888      │  │ :8888→8080  │  │ :8889→80     │           │
│  └────────────┘  └────────────┘  └─────────────┘  └──────────────┘           │
│                                                                               │
│  bind-mounts: /sm-karaoke/system/Караоке, ...-1/2/3, ...-store, ...-db        │
│  Docker network: karaokenet (external)                                       │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Управление

- **`deploy/do.sh`** — единая точка входа:
  - `bash do.sh start all` — поднимает 7 контейнеров из `docker-compose.yml`
    + `docker-compose-database.yml` (НЕ поднимает MinIO — он в отдельном
    compose-файле).
  - `bash do.sh stop all` — останавливает те же 7 контейнеров.
  - `bash do.sh pull` — `docker compose pull` для всех compose-файлов.
  - `bash do.sh build_app` / `build_web` / `build_webvue3` / `build_public` —
    пересборка конкретного образа (см. `AGENTS.md` → «Ограничения агента»:
    на машинах не `dev-pc`/`dev` агенту запрещено `build_start_app`).
- **MinIO** поднимается отдельной командой:
  `docker compose -f deploy/docker-compose-storage.yml up -d`
- **Ollama** — опционально, через `docker compose -f deploy/docker-compose-ollama.yml up -d`.

### Воспроизводимая настройка

- **`specs/170-mint-dev-setup/setup-mint.sh`** — идемпотентный bash-скрипт
  для Linux Mint 22.2. Проверяет ОС, ставит apt-пакеты + Node 22 + Docker CE,
  поднимает контейнеры, запускает smoke-test.
- **`deploy/do.env.template`** — env-шаблон, копируется в `do.env` (НЕ в
  гите) и заполняется реальными значениями.

## Инварианты

### Секреты и git-гигиена (NON-NEGOTIABLE — Принцип VIII)

- `deploy/do.env` **НИКОГДА** не в гите (`.gitignore: /deploy/do.env`).
- `deploy/do.env.template` — **коммитится**, но без реальных секретов.
- pre-commit-check `git ls-files | grep -iE '(do\.env|\.env)$'` MUST быть пусто.
- `DOCKER_PASSWORD`, `STORAGE_KEY`, `STORAGE_SECRET`, `DB_LOCAL_POSTGRES_PASSWORD`,
  `STEMJOBS_INTERNAL_SECRET` — **никогда** не в шаблоне; только `<SET-ME>`.

### Build / runtime

- **Образы** — НЕ собирать на dev-машинах без явного запроса (Constitution
  «Ограничения агента»: только пользователь пересобирает `karaoke-app`).
  Для поднятия достаточно `bash do.sh pull` (Docker Hub).
- **`ENABLE_APP_GPU=0`** на dev-машинах без nvidia passthrough (см.
  `AGENTS.md` → «Dockerfile-ловушки»).
- **Пути в `do.env`** — абсолютные, привязаны к хосту; bind-mounts
  создаются `setup-mint.sh` через `sudo mkdir -p`.
- **`docker-compose.yml:51-56`** — критичный комментарий про `APP_JAVA_OPTS=""`:
  пустая СТРОКА, не `[]`. Иначе java воспримет пустой аргумент перед `-jar`
  как main-класс и упадёт "Could not find or load main class".

### Сеть

- Все контейнеры в `external`-сети `karaokenet` (создаётся
  `deploy/docker-compose.yml`: `networks.karaokenet.external: true`).
  Если сеть не создана — `docker network create karaokenet`.

## Известные ловушки

### 1. `nginx:alpine` падает на `docker compose up` (см. `AGENTS.md`)

В `docker-compose-webvue.yml` / `webvue3.Dockerfile` используется
`/bin/bash -c ...`. В `nginx:alpine` нет bash (только `ash` через BusyBox) —
контейнер стартует, но скрипт не работает. Решение: `nginx:stable` (Debian).

### 2. `node:latest` — недетерминированная версия

В `Dockerfile` фронтендов **зафиксировать** `node:22-alpine` (LTS).
`node:latest` ломает reproducibility: разные билды в разные дни дают
разные версии npm-зависимостей.

### 3. `JDK` вместо `JRE` в проде

`karaoke-app` и `karaoke-web` используют `eclipse-temurin:22-jre-jammy`
(JRE, не JDK). Spring Boot fat jar не требует компилятора; JDK-образ
+200 МБ и security-расширения впустую.

### 4. `redirectErrorStream(false)` блокирует ffmpeg/ffmpeg-демucs

В `ProcessBuilder` для long-running процессов **всегда** `redirectErrorStream(true)`.
Иначе буфер stderr (~64 KB) переполняется и подпроцесс блокируется.

### 5. MinIO требует `XDG_RUNTIME_DIR`

Docker-CE под Linux Mint иногда не прокидывает `$XDG_RUNTIME_DIR` в контейнер.
Симптом: `karaoke-storage` падает при старте. Решение: добавить в
`docker-compose-storage.yml`:
```yaml
environment:
  - XDG_RUNTIME_DIR=/tmp
```

### 6. Postgres init занимает 10-30 секунд

На свежем `DB_FOLDER` (volume) Postgres запускает `initdb` + entrypoint-initdb.d.
`docker compose up` возвращает 0, но `psql` падает с "connection refused".
Решение: `setup-mint.sh` делает retry-loop 5×3 сек (smoke-test `check_postgres_ready`).

### 7. IP-API в контейнере возвращает 403/502

`ip-api.com`, `ipapi.co`, `ipapi.is` из Docker отдают 403/502. Для проверки
VPN в `karaoke-app` используется `api.country.is` (см. `AGENTS.md`).

### 8. Linux Mint 22.2 — `nodejs` из apt = v20, не v22

`apt install nodejs` на Ubuntu 24.04 = Node 20.x LTS. Нужен 22 LTS. Решение:
`setup-mint.sh` ставит Node 22 через NodeSource (research.md R-001).

### 9. Linux Mint 22.2 — `docker.io` из apt устарел

`apt install docker.io` = Docker 20.10 + docker-compose v1 (legacy Python).
Не совместим с `docker compose` v2 в `do.sh`. Решение: Docker CE из
`download.docker.com` (research.md R-002).

### 10. После смены `DB_LOCAL_POSTGRES_PASSWORD` Postgres не стартует

Если `do.env` имеет новый пароль, а `DB_FOLDER` (volume) инициализирован со
старым — auth не сходится. Решение: либо откатить пароль к старому, либо
снести `DB_FOLDER` (ОСТОРОЖНО — необратимо).

## Ссылки

- [Спека `specs/170-mint-dev-setup/spec.md`](../../specs/170-mint-dev-setup/spec.md) — основная спека воспроизводимой настройки.
- [Plan `specs/170-mint-dev-setup/plan.md`](../../specs/170-mint-dev-setup/plan.md) — Implementation Plan (10 решений R-001..R-010).
- [Quickstart `specs/170-mint-dev-setup/quickstart.md`](../../specs/170-mint-dev-setup/quickstart.md) — 5 шагов + smoke-test + troubleshooting.
- [Setup-скрипт `specs/170-mint-dev-setup/setup-mint.sh`](../../specs/170-mint-dev-setup/setup-mint.sh) — идемпотентный bash.
- [Env-шаблон `deploy/do.env.template`](../../deploy/do.env.template) — переменные окружения без секретов.
- [AGENTS.md → Dockerfile-ловушки](../../AGENTS.md) — ловушки `nginx:alpine`, `node:latest`, JDK vs JRE.
- [AGENTS.md → Ограничения агента](../../AGENTS.md) — что агенту разрешено/запрещено на этой машине.
- [Constitution → Принцип VIII](../../.specify/memory/constitution.md) — секреты и git-гигиена.
- [`deploy/do.sh`](../../deploy/do.sh) — единая точка входа для build/start/stop.
- [`deploy/docker-compose.yml`](../../deploy/docker-compose.yml) — основной compose (7 контейнеров).
- [`deploy/docker-compose-storage.yml`](../../deploy/docker-compose-storage.yml) — MinIO.
- [`deploy/docker-compose-database.yml`](../../deploy/docker-compose-database.yml) — PostgreSQL.
- [`docs/onboarding.md`](../onboarding.md) — общий setup для любого AI-агента.

# Совместимость прод-стека Karaoke с Ubuntu 26.04 LTS

> **Спека**: 165-migration-one-server-research
> **Дата**: 2026-09-23
> **Целевой хост**: `sm-karaoke` (188.127.240.124), hostname `sm-karaoke.ru`
> **ОС**: Ubuntu 26.04 LTS (Resolute Raccoon), codename `resolute`, kernel `7.0.0-22-generic`
> **Метод**: research (только чтение; сервер не изменялся)

## 1. Обзор

Проведён анализ совместимости прод-стека Karaoke (Docker Compose + host-nginx)
с Ubuntu 26.04 LTS. Docker на хосте ещё не установлен (`docker: command not found`,
`docker-compose: command not found`).

**Главные выводы:**

1. **Docker Engine 29.x официально поддерживает Ubuntu 26.04 (resolute)** —
   пакеты `docker-ce` / `docker-ce-cli` / `containerd.io` / `docker-compose-plugin`
   присутствуют в `download.docker.com` с суффиксом `~ubuntu.26.04~resolute`.
2. **`do.sh` НЕ сломается**, если поставить `docker-compose` как совместимый
   бинарь/симлинк на `docker compose`. Проект вызывает `$(which docker-compose)`
   и `${COMPOSE} -f ... up -d`; плагин compose v2 поддерживает этот синтаксис.
3. **Пакет `docker-compose` (v1) отсутствует** и в Ubuntu 26.04, и в официальном
   Docker-репо (проверено: на jammy его тоже нет). Единственный штатный путь —
   `docker-compose-v2` (Ubuntu universe) или `docker-compose-plugin` (Docker repo),
   плюс симлинк `docker-compose -> docker compose`.
4. **Критический риск вне ОС**: образ `minio/minio` **удалён с Docker Hub**
   (репозиторий minio/minio архивирован 2026-04-25; тег `latest` отдаёт 404/401).
   Compose-файлы `docker-compose-storage.yml` ссылаются именно на него. Это
   блокер запуска storage — нужно менять источник образа (Quay `quay.io/minio/minio`
   доступен, 200).

## 2. Текущие образы и версии (из репозитория)

### 2.1 Базовые образы Dockerfile

| Файл | Базовый образ | Тег | Правило |
|---|---|---|---|
| `deploy/karaoke-app/Dockerfile` | `openjdk` (builder) | `17-ea-jdk-slim` | — |
| `deploy/karaoke-app/Dockerfile` | `ubuntu` (runtime) | `22.04` | — |
| `deploy/karaoke-web/Dockerfile` | `eclipse-temurin` | `22-jre-jammy` | — |
| `deploy/web-server-deploy/deploy/karaoke-web/Dockerfile` | `eclipse-temurin` | `17-jre-jammy` | — |
| `deploy/karaoke-public/Dockerfile` | `node` / `nginx` | `22-alpine` / `stable` | R-05 / R-04 |
| `deploy/karaoke-webvue3/Dockerfile` | `node` / `nginx` | `22-alpine` / `stable` | R-05 / R-04 |
| `deploy/karaoke-app/DockerfileFull` (не текущий) | `nvidia/cuda`, `mltframework/melt`, `openjdk`, `ubuntu` | `12.6.2-base-ubuntu22.04`, `latest`, `17-jdk-slim`, `22.04` | — |

Все базовые образы, кроме `minio/minio`, подтверждены в registry (HTTP 200):
`library/openjdk:17-ea-jdk-slim`, `library/eclipse-temurin:22-jre-jammy`,
`library/eclipse-temurin:17-jre-jammy`, `library/nginx:stable`,
`library/node:22-alpine`, `library/postgres:16`, `luuul/4get:1.0.44`,
`searxng/searxng:latest`.

### 2.2 Образы в docker-compose

| Образ | Где | Registry-статус |
|---|---|---|
| `${DOCKER_REGISTRY}/karaoke-app:${BUILD_VERSION}` | `docker-compose.yml`, `docker-compose-app.yml` | приватный `svoemestodev`, нужен `docker login` |
| `${DOCKER_REGISTRY}/karaoke-web:${BUILD_VERSION}` | `docker-compose-web.yml`, `docker-compose.yml` | приватный |
| `${DOCKER_REGISTRY}/karaoke-webvue3:${BUILD_VERSION}` | `docker-compose-webvue3.yml` | приватный |
| `${DOCKER_REGISTRY}/karaoke-public:${BUILD_VERSION}` | `docker-compose-public.yml` | приватный |
| `postgres:16` | `docker-compose-database.yml` | OK (200) |
| `nginx:stable` | `docker-compose-web.yml` (сервис `karaoke-minio-proxy`) | OK (200) |
| `searxng/searxng:latest` | `docker-compose-app.yml`, `docker-compose.yml` | OK (200) |
| `luuul/4get:1.0.44` | `docker-compose-app.yml`, `docker-compose.yml` | OK (200) |
| **`minio/minio:latest`** | `docker-compose-storage.yml`, `new_comp/.../docker-compose-storage-new-comp.yml` | **404 — образ удалён с Docker Hub** |
| `svoemestodev/demucs:latest` | запускается `docker run` из karaoke-app | приватный |

### 2.3 Реестр и секреты

- `deploy/do.env` → `DOCKER_REGISTRY=svoemestodev`, `DOCKER_PASSWORD=<PAT>`.
- `deploy/web-server-deploy/deploy/do.env` → тот же registry.
- Это **приватный Docker Hub registry**, на новом хосте обязателен
  `docker login` (или `docker login -u svoemestodev`), иначе `pull` образов
  `karaoke-*` из compose упадёт с `denied`.

### 2.4 Как проектные скрипты вызывают Compose

`deploy/do.sh`:
```bash
DOCKER=$(which docker)          # строка 36
COMPOSE=$(which docker-compose) # строка 37
...
${COMPOSE} -f ${DEPLOY_DIR}/docker-compose.yml ${DATABASE} up -d
${COMPOSE} -f ... pull / down / ps / rm / config
```
Аналогично `deploy/web-server-deploy/deploy/do.sh` (строки 12-13).

Ключевые факты:
- Вызывается именно **`docker-compose`** (бинарь из `$PATH`), а не `docker compose`.
- `do.sh` не проверяет существование `docker-compose` — если `which` пуст,
  `COMPOSE` пустой и команды выполняются как `-f file up -d` (ошибка).
- `do.sh` также вызывает `${DOCKER} image build`, `${DOCKER} login`, `${DOCKER} exec`.
- Локальный эталон (nsa-i9) имеет `/usr/local/bin/docker-compose` — 3-строчную
  обёртку `exec docker compose "$@"`, а реальный бинарь — CLI-плагин
  `/usr/libexec/docker/cli-plugins/docker-compose` (Compose v5.5.1).
  **Это тот самый паттерн, который нужно воспроизвести на `sm-karaoke`.**

## 3. Доступные пакеты на новом хосте

`apt-cache policy` на `sm-karaoke` (universe/main, resolute):

| Пакет | Candidate | Источник |
|---|---|---|
| `docker.io` | `29.1.3-0ubuntu4.1` | `resolute-updates/universe`, `resolute-security/universe` |
| `docker-compose` (v1) | **(none)** | отсутствует |
| `docker-compose-v2` | `2.40.3+ds1-0ubuntu1` | `resolute/universe` |
| `containerd` | `2.2.2-0ubuntu1` | `resolute/main` |
| `nginx` | `1.28.3-2ubuntu1.5` | `resolute-updates/main` |
| `libnginx-mod-stream` | `1.28.3-2ubuntu1.5` | доступен |
| `nftables` | `1.1.6-1` | доступен |
| `iptables` | `1.8.11-2ubuntu3` | доступен |
| `apparmor` | `5.0.0~beta1-0ubuntu7` (active) | `resolute/main` |

Официальный Docker-репо (`download.docker.com/linux/ubuntu`, dist `resolute/stable`):

| Пакет | Версия |
|---|---|
| `docker-ce` | `5:29.3.1-1~ubuntu.26.04~resolute` |
| `docker-ce-cli` | `5:29.3.1-1~ubuntu.26.04~resolute` |
| `containerd.io` | `2.2.2-1~ubuntu.26.04~resolute` |
| `docker-buildx-plugin` | `0.31.1-1~ubuntu.26.04~resolute` |
| `docker-compose-plugin` | `5.0.2-1~ubuntu.26.04~resolute` |
| `docker-compose` (v1) | **(отсутствует)** |

`docker-compose-plugin` кладёт бинарь в `/usr/libexec/docker/cli-plugins/docker-compose`
(проверено распаковкой deb) — **никакого `/usr/bin/docker-compose` не создаётся**.
Ubuntu-пакет `docker-compose-v2` кладёт ровно то же:
`/usr/libexec/docker/cli-plugins/docker-compose`.

Зависимости `docker-ce` (официальный репо): `containerd.io (>= 1.7.27)`,
`docker-ce-cli`, `iptables`, **`nftables`**, `libnftables1`, `libsystemd0`.
`docker.io` (Ubuntu) зависит от `iptables` и `libnftables1`, рекомендует
`apparmor`, `apparmor` active.

## 4. Известные проблемы Ubuntu 26.04

### 4.1 Docker Compose v1 отсутствует (подтверждено)

`docker-compose` (Python v1) прекратил поддержку и отсутствует:
- в Ubuntu 26.04 (`Candidate: (none)`),
- в официальном Docker-репо для resolute,
- в официальном Docker-репо для jammy (тоже нет).

Доступны только Compose v2 как CLI-плагин. **Прямая замена «пакет
`docker-compose` → `/usr/bin/docker-compose`» невозможна.**

### 4.2 cgroup v2

Хост уже работает на cgroup v2: `stat -fc %T /sys/fs/cgroup` → `cgroup2fs`.
Для Docker Engine 29.x и Compose v2 это штатный режим, проблем не ожидается.
Важно: compose-файлы имеют `version: "3.9"` (устаревшая top-level версия).
Compose v2 её принимает с warning (`the attribute version is obsolete`).

### 4.3 iptables / nftables

- На хосте `nft` и `iptables` ещё не установлены в `PATH` (пакеты доступны).
- Docker 29.x поддерживает **только `iptables-nft` и `iptables-legacy`**.
  Правила, созданные `nft` напрямую, Docker **не поддерживает**; правила
  фильтрации нужно добавлять в цепочку `DOCKER-USER` через iptables.
- `docker-ce` явно тянет `nftables` как зависимость — на 26.04 это штатно.
- Если в проекте есть host-firewall правила — держать их в iptables,
  не в чистом nft.

### 4.4 AppArmor

AppArmor **включён** (`systemctl is-active apparmor` → active, securityfs
смонтирован). Для `postgres:16`, `nginx:stable`, `minio` и `searxng` это
обычно не мешает (профили для контейнеров генерирует Docker сам). Если
`minio`/`postgres` начнут падать с `apparmor="DENIED"`, смотреть
`dmesg`/`journalctl -k` и при необходимости использовать `--security-opt
apparmor=unconfined` (только как точечный workaround). Ubuntu 26.04 несёт
`apparmor 5.0.0~beta1` — бета-мажор, возможны новые ограничения.

### 4.5 Репозиторий minio/minio удалён (критично, вне ОС)

- `minio/minio` на Docker Hub: `hub.docker.com/v2/repositories/minio/minio/...`
  → **404 object not found**; registry-манифест → 401 (репозиторий недоступен).
- GitHub `minio/minio` **архивирован 2026-04-25** (read-only).
- `docker-compose-storage.yml` и `new_comp/.../docker-compose-storage-new-comp.yml`
  ссылаются на `minio/minio:latest` → `docker compose pull` упадёт.
- Доступная альтернатива: **`quay.io/minio/minio`** (Quay API 200, manifest
  `latest` 200; свежие теги вида `RELEASE.2025-09-07T...hotfix`).
  Официальная документация MinIO теперь указывает Quay.
- Рекомендация: заменить `image: minio/minio:latest` на
  `image: quay.io/minio/minio:latest` (или фиксированный RELEASE-тег) и
  обновить `check-docker-image-tags.sh`, если он валидирует registry.

### 4.6 MTU / сеть

- Хост: `ens3`, MTU 1500, default route через `188.127.240.254`.
- IPv6 глобально не настроен (только DNS AAAA-ответы); DNS `getent ahostsv4`
  корректно резолвит IPv4. Внешние registry доступны (401 на `/v2/` — норма).
- MTU black-hole в проекте обходится через nginx path-proxy
  (`minio-proxy`, `yookassa-proxy`, `telegram-proxy`). На новом односерверном
  стенде MinIO локальный — обход MTU для storage **не нужен**. Для внешних
  API (YooKassa, Telegram) nginx-proxy остаётся полезным.

### 4.7 Прочее, обнаруженное на хосте

- Хост: 6 CPU, 3.8 GiB RAM, диск `/dev/vda2` 985G (использовано 2.8G).
  `APP_JAVA_OPTS` в `.env` поднят до heap 8g — **на 3.8 GiB RAM это превышение**,
  нужно пересмотреть перед запуском `karaoke-*` (иначе OOM).
- Нет `/dev/nvidia*`, `nvidia-smi` отсутствует; есть `/dev/dri/card0`.
  `do.env` содержит `ENABLE_APP_GPU=1` → `do.sh` подключит
  `docker-compose-app.gpu.yml` с `driver: nvidia` → **упадёт** без
  NVIDIA runtime. Для этого хоста выставить `ENABLE_APP_GPU=0`.
- `unattended-upgrades` active — авто-обновления могут перезапускать сервисы.
- `/etc/apt/sources.list.d/` пока не содержит Docker-репо.

## 5. Рекомендация по установке (Docker / Compose / nginx)

### 5.1 Docker Engine — официальный репо (рекомендуется)

Использовать официальный репозиторий Docker: он даёт 29.3.x, официально
поддерживает `resolute` и поставляет `docker-compose-plugin` v5.0.2.
Ubuntu-пакет `docker.io` (29.1.3) — как fallback, если по каким-то причинам
нельзя использовать сторонний репо; он тоже свежий (29.x).

Порядок:
```bash
sudo apt update && sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: resolute
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

### 5.2 Обеспечение `docker-compose` (ключ к совместимости do.sh)

`do.sh` ищет `docker-compose` в `$PATH`. Создать совместимую обёртку/симлинк
(как на эталонном nsa-i9):

```bash
# вариант A — симлинк на бинарь плагина
sudo ln -sf /usr/libexec/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose

# вариант B — обёртка (ровно как на nsa-i9)
sudo tee /usr/local/bin/docker-compose <<'EOF'
#!/bin/bash
# Wrapper: docker-compose -> docker compose (v2 plugin)
exec docker compose "$@"
EOF
sudo chmod +x /usr/local/bin/docker-compose
```

После этого `which docker-compose` вернёт путь, а `${COMPOSE} -f ... up -d`
будет работать без правок `do.sh`. Проверить: `docker-compose version`.

**Вывод: `do.sh` ломаться не должен** — при условии установки v2-плагина и
создания `docker-compose` в `$PATH`. Без обёртки `COMPOSE` будет пустым, и
скрипт сломается.

### 5.3 nginx (host-nginx для TLS/прокси)

Ubuntu-пакет: `nginx 1.28.3`. Для host-nginx из
`web-server-deploy/deploy/nginx.conf` **обязателен модуль `stream`**
(блок `stream { listen 5433; ... }` — проброс PostgreSQL). Штатный
`nginx-core` модуль stream не всегда включает — поставить:
```bash
sudo apt install -y nginx libnginx-mod-stream
sudo systemctl enable --now nginx
```
Также в конфиге нужен `client_max_body_size 20M` (уже присутствует) и
проверить, что `server_tokens`/TLS-сертификаты на месте.

### 5.4 Дополнительно

```bash
sudo apt install -y mc rclone   # опционально, как в «Настройка сервера.md»
```

## 6. Риски

| # | Риск | Влияние | Митигация |
|---|---|---|---|
| R1 | `minio/minio` удалён с Docker Hub | Высокое: storage не стартует | Перейти на `quay.io/minio/minio:<RELEASE>`; зафиксировать тег; обновить guard-скрипт и knowledge |
| R2 | `docker-compose` v1 нет | Высокое: `do.sh` не найдёт COMPOSE | Симлинк/обёртка `docker-compose -> docker compose` (см. 5.2) |
| R3 | `ENABLE_APP_GPU=1` без NVIDIA | Высокое: `do_start_app` падает | Выставить `ENABLE_APP_GPU=0` в `do.env` на этом хосте |
| R4 | Heap 8g при 3.8 GiB RAM | Высокое: OOM/JVM не стартует | Уменьшить `APP_JAVA_OPTS -Xmx` (например, 2-3g) |
| R5 | Ресурсы: 6 CPU / 3.8 GiB на весь стек (app+web+webvue3+public+db+minio+searxng+4get) | Среднее: нехватка RAM/CPU | Проверить лимиты, возможно, вынести часть сервисов; следить за swap |
| R6 | Приватный registry без login | Среднее: `pull` denied | `docker login` с `svoemestodev` + PAT из `do.env` |
| R7 | Compose v2 warning на `version: "3.9"` | Низкое (косметика) | Обновить compose-файлы, убрав top-level `version` |
| R8 | nftables vs iptables | Низкое/среднее | Docker 29 поддерживает iptables-nft; firewall-правила — в `DOCKER-USER` |
| R9 | AppArmor 5.0.0~beta1 | Низкое | Следить за `dmesg`; при DENIED — точечный `apparmor=unconfined` |
| R10 | `openjdk:17-ea-jdk-slim` — EA-сборка, old | Низкое (сборка, не рантайм) | Плановая замена на `eclipse-temurin:17-jdk-jammy` (вне этой задачи) |
| R11 | Kernel 7.0.0 — очень новый | Низкое/неизвестное | Docker 29 + cgroup v2 штатны; следить за обновлениями |
| R12 | `unattended-upgrades` active | Низкое | Окно автообновлений может перезапускать docker/nginx |

## 7. Чеклист установки

- [ ] Проверить hostname/ресурсы: `hostname`, `nproc`, `free -h`, `df -h /`.
- [ ] Установить Docker по официальному репо (5.1); проверить `docker --version`.
- [ ] Установить `docker-compose-plugin`; создать `docker-compose` в `/usr/local/bin` (5.2).
- [ ] Проверить `which docker-compose` и `docker-compose version`.
- [ ] Установить `nginx` + `libnginx-mod-stream`; `nginx -t`.
- [ ] `sudo usermod -aG docker <user>` (если запуск не от root).
- [ ] `docker network create karaokenet` (все compose используют external network).
- [ ] `docker login` в `svoemestodev` (PAT из `do.env`).
- [ ] **Заменить `minio/minio:latest` на `quay.io/minio/minio:<tag>` (R1)**.
- [ ] `ENABLE_APP_GPU=0` на этом хосте (R3).
- [ ] Пересмотреть `APP_JAVA_OPTS` под 3.8 GiB RAM (R4).
- [ ] Скопировать `deploy/` (+ `do.env`, `.env`, `karaoke-db/`), каталоги данных
      (`/sm-karaoke/*`) и дамп БД; `do.sh start_db restore_db`.
- [ ] Поднять storage (с новым образом), app/web/webvue3/public по `do.sh`.
- [ ] Проверить `docker compose ps`, health, TLS через host-nginx.
- [ ] Проверить логи: `docker logs karaoke-*`, `journalctl -u nginx`, `dmesg | grep -i apparmor`.
- [ ] Обновить `knowledge/` (ADR/депл-overview) по итогам миграции; зафиксировать
      расхождение Ubuntu 26.04 vs «Настройка сервера.md» (там Ubuntu 22.04).

## 8. Источники

- Репозиторий: `deploy/do.sh`, `deploy/web-server-deploy/deploy/do.sh`,
  `deploy/docker-compose-*.yml`, `deploy/*/Dockerfile`, `deploy/do.env`,
  `deploy/Настройка сервера.md`, `deploy/web-server-deploy/deploy/nginx.conf`,
  `knowledge/guidelines/architecture-conventions.md` (R-04, R-05),
  `knowledge/system/infra/deploy-overview.md`.
- Хост `sm-karaoke`: `/etc/os-release`, `apt-cache policy`, `dpkg-deb -c`,
  `stat -fc %T /sys/fs/cgroup`, `ip link`, `systemctl is-active`.
- Docker docs: «Install Docker Engine on Ubuntu» (resolute 26.04 в списке
  поддерживаемых), «Packet filtering and firewalls» (iptables-nft/legacy).
- Docker registry: `download.docker.com/linux/ubuntu/dists/resolute/...`,
  `hub.docker.com/v2/repositories/...`, `quay.io/api/v1/repository/minio/minio`.

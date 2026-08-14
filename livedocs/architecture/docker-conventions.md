---
status: Active
slug: docker-conventions
type: topic
related:
  - ../architecture/L1-system-context.md
  - ../architecture/L2-containers.md
---

# Docker — конвенции для образов Karaoke

> Drill-down из `AGENTS.md` секции «Dockerfile-ловушки» + Constitution.
> Этот LiveDoc — полная версия. В AGENTS.md осталась только короткая ссылка.

## Главные правила

### 1. `nginx:alpine` НЕЛЬЗЯ

`docker-compose` использует `/bin/bash -c ...`, а в `nginx:alpine` нет bash
(только `ash` через BusyBox). Контейнер запускается, но скрипт не работает.

**Решение**: `nginx:stable` (Debian).

### 2. `node:latest` НЕЛЬЗЯ

`node:latest` — недетерминирован (меняется между docker pull'ами). Может
привести к тому, что на проде соберётся другой код, чем локально.

**Решение**: `node:22-alpine` (LTS, детерминирован).

### 3. `karaoke-app`: JRE, не JDK

**`eclipse-temurin:22-jre-jammy`** (JRE, не JDK — Spring Boot fat jar не
требует компилятора).

JDK внутри контейнера увеличивает размер образа на ~300 MB без пользы.

### 4. Docker CE внутри `karaoke-app` намеренно

Приложение само запускает `docker run`/`docker compose` через `ProcessBuilder`
(для Demucs, для обновлений других контейнеров). Поэтому внутри `karaoke-app`
образа установлен Docker CE.

НЕ удалять! Это не баг, это feature.

### 5. Две папки deploy

На admin-машине есть ДВЕ директории `deploy/`:

- `~/Karaoke/deploy` — для **build** (gradle + docker images).
- `/sm-karaoke/system/deploy` — для **start/stop** контейнеров.

Большинство команд требуют `cd` в обе. Исключение — `build_start_public`
(одной командой).

## IP-сервисы (VPN detection)

`ip-api.com`, `ipapi.co`, `ipapi.is` из Docker возвращают **403/502**. Это
из-за того, что контейнер идёт через VPN, а эти сервисы блокируют VPN-трафик.

**Решение**: `api.country.is` — работает из Docker без проблем.

## Переменные окружения

### IP серверов через env, не hardcode

```bash
# ❌ НЕПРАВИЛЬНО (захардкожен IP)
rsync -avz root@188.119.64.111:/path/ /local/

# ✅ ПРАВИЛЬНО (через env)
rsync -avz root@${PROD_HOST:-188.119.64.111}:/path/ /local/
```

`PROD_HOST` / `DB_REMOTE_HOST` — переменные окружения (см. Constitution § VIII.5).

## docker-compose

- **Сеть на сервере**: `deploy_karaokenet` (НЕ `karaokenet`).
- **nginx 80to8897**: отдельный файл, не симлинк. При rsync обновляется в
  `/root/Karaoke/deploy/`, но nginx читает из `/etc/nginx/sites-enabled/80to8897`.
  Нужно копировать вручную:
  ```bash
  ssh root@${PROD_HOST} "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"
  ```

## Что НЕ коммитить

Полный список — Constitution § VIII.2. Главное:
- `deploy/.env`, `deploy/do.env` (секреты).
- `*.key`, `*.pem`, `*.p12`, `*.pfx` (SSL-сертификаты).
- `deploy/ollama_data/`, `dist/`, `node_modules/`.

Проверка: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` MUST быть пусто.

## Связанные LiveDocs

- Architecture: [L1-system-context.md](L1-system-context.md), [L2-containers.md](L2-containers.md)
- Constitution: § VIII «Секреты и git-гигиена»

## Код

- docker-compose: `deploy/docker-compose.yml`, `deploy/docker-compose.public.yml`
- Dockerfile'ы: `deploy/karaoke-app/Dockerfile`, `deploy/karaoke-web/Dockerfile`, и т.д.

## История

- Создан: 2026-08-14 (мигрировано из `AGENTS.md` v1.7.1)
- Последнее обновление: 2026-08-14
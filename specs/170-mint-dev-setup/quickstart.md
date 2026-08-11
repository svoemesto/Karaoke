# Quickstart: Воспроизводимая настройка Linux Mint для Karaoke

> **Phase 1** of `/speckit.plan` для `specs/170-mint-dev-setup/`.
> Цель — runnable validation guide, который доказывает, что спека работает end-to-end.

## Что это

После применения спеки `specs/170-mint-dev-setup/` на чистой машине с
Linux Mint 22.2 Zara разработчик должен получить **9 работающих контейнеров**
(8 обязательных + 1 опциональный `ollama`) и доступ к web-интерфейсам
проекта Karaoke.

## Prerequisites

Перед запуском `setup-mint.sh` пользователь должен иметь:

1. **Чистая установка Linux Mint 22.2 Zara** (или Ubuntu 24.04 Noble, как dev-база).
   Проверить: `cat /etc/os-release | grep -E '^(VERSION|PRETTY_NAME|ID)'`.
2. **Не-root пользователь с `sudo`**. Проверить: `id -Gn | grep -qw sudo`.
3. **Доступ в интернет** (apt repos, `deb.nodesource.com`, `download.docker.com`,
   `github.com`, `registry-1.docker.io`).
4. **GitHub credentials** (для `git clone` и `git push`):
   - Либо `gh auth login` выполнен заранее;
   - Либо SSH-ключ добавлен через `gh ssh add`;
   - Либо `git config credential.helper store` + PAT в `~/.git-credentials`.
5. **~5 ГБ свободного места на диске** (Docker-образы + node_modules +
   Postgres data + MinIO data).

## Шаги

### Шаг 1. Клонировать репозиторий

```bash
cd ~
git clone https://github.com/svoemesto/Karaoke.git
cd Karaoke
git checkout 170-mint-dev-setup
```

После этого в `~/Karaoke/specs/170-mint-dev-setup/setup-mint.sh` и
`~/Karaoke/deploy/do.env.template` — нужные артефакты.

### Шаг 2. Создать `do.env` из шаблона

```bash
cd ~/Karaoke/deploy
cp do.env.template do.env
chmod 600 do.env  # Принцип VIII: секреты
nano do.env   # или vim/code
```

**Обязательно заполнить** (подставить свои значения):
- `DOCKER_REGISTRY=svоеместо` (если другой — изменить)
- `STORAGE_KEY=<случайная-строка-16+ символов>`
- `STORAGE_SECRET=<случайная-строка-32+ символов>`
- `DB_LOCAL_POSTGRES_PASSWORD=<случайный-пароль>`
- `DB_LOCAL_POSTGRES_USER=postgres`
- `STEMJOBS_INTERNAL_SECRET=<случайная-строка>`
- `APP_FOLDER_HOST=/sm-karaoke/system/Караоке` (или ваш путь)
- `APP_FOLDER_K1=/sm-karaoke/system/Караоке-1` (или ваш путь)
- `APP_FOLDER_K2=/sm-karaoke/system/Караоке-2` (или ваш путь)
- `APP_FOLDER_K3=/sm-karaoke/system/Караоке-3` (или ваш путь)
- `APP_FOLDER_SYSTEM=/sm-karaoke/system` (или ваш путь)
- `APP_FOLDER_STORE=/sm-karaoke/system/Караоке-store` (или ваш путь)
- `STORAGE_FOLDER=/sm-karaoke/system/Караоке-storage` (или ваш путь)
- `DB_FOLDER=/sm-karaoke/system/Караоке-db` (или ваш путь)
- `WEBVUE3_PATH_TO_NGINX_CONF=/sm-karaoke/system/Karaoke/webvue3/nginx.conf`
  (или относительный путь от deploy — см. R-004 research.md)
- `PUBLIC_PATH_TO_NGINX_CONF=/sm-karaoke/system/Karaoke/karaoke-public/nginx.conf`

**Можно оставить пустыми** на чисто локальной dev-машине:
- `DOCKER_PASSWORD` (нужен только для `do.sh push`)
- `DB_SERVER_*` (используется в проде)
- `DB_REMOTE_HOST` (используется в проде)
- `SILERO_*` (используется для TTS-уведомлений)
- `WEB_FOLDER_HOST` (если `karaoke-web` запускается без bind-mounts)

**НЕ ТРОГАТЬ**:
- `BUILD_VERSION` — авто-вычисляется в `do.sh:19-26`
- `ENABLE_APP_GPU=0` (на dev без nvidia; см. AGENTS.md)
- `APP_JAVA_OPTS=""` и `WEB_JAVA_OPTS=""` — пустые строки,
  НЕ убирать кавычки (см. `docker-compose.yml:51-56`)
- Все `*_PORT_*` — дефолты безопасные

Сохранить `do.env` и **не коммитить** (он в `.gitignore` — verify:
`git check-ignore deploy/do.env` должно вернуть `deploy/do.env`).

### Шаг 3. Запустить `setup-mint.sh`

```bash
cd ~/Karaoke
bash specs/170-mint-dev-setup/setup-mint.sh
```

Скрипт:
1. Проверит, что ОС — Linux Mint 22.2 Zara (fail-fast на других).
2. Проверит наличие `sudo` и `git`.
3. Поставит системные зависимости через `apt install` (идемпотентно).
4. Поставит Node 22 LTS через NodeSource.
5. Поставит Docker CE из `download.docker.com`.
6. Добавит пользователя в группу `docker` (попросит перелогиниться).
7. Настроит `git config blame.ignoreRevsFile .git-blame-ignore-revs`.
8. Создаст папки для bind-mounts (`sudo mkdir -p` + `chown`).
9. Выполнит `cd deploy && bash do.sh pull` (скачает Docker-образы).
10. Выполнит `bash do.sh start all` (поднимет 7 контейнеров кроме MinIO).
11. Поднимет MinIO отдельной командой.
12. Опционально поднимет Ollama (если `setup-mint.sh --with-ollama`).
13. Запустит smoke-test (Шаг 4).

Время выполнения: **30-60 минут** (большая часть — скачивание образов).

### Шаг 4. Smoke-test (валидация)

После `setup-mint.sh` автоматически запускается smoke-test. Если падает —
скрипт печатает, какой конкретно эндпоинт не отвечает, и предлагает
`docker logs <container>` для диагностики.

**Ручной smoke-test** (если хочется перепроверить):

```bash
# 1. Все 8+ контейнеров в статусе Up
docker ps --format "{{.Names}}\t{{.Status}}" | grep -E "^(karaoke-app|karaoke-web|karaoke-webvue3|karaoke-public|searxng|fourget|karaoke-db|karaoke-storage)\b"

# Ожидаемый вывод: 8 строк, все со статусом "Up" и uptime > 30 сек.

# 2. Web-интерфейсы отвечают 200
curl -s -o /dev/null -w "webvue3: %{http_code}\n" http://localhost:7906
curl -s -o /dev/null -w "karaoke-public: %{http_code}\n" http://localhost:8888
curl -s -o /dev/null -w "searxng: %{http_code}\n" http://localhost:8888
curl -s -o /dev/null -w "MinIO health: %{http_code}\n" http://localhost:9001/minio/health/live

# Ожидаемый вывод: все 4 кода 200 (или 3xx — MinIO console может
# редиректить на /minio/; главное — не 5xx и не connection refused).

# 3. Postgres подключается
PGPASSWORD=$(grep DB_LOCAL_POSTGRES_PASSWORD deploy/do.env | cut -d= -f2) \
  psql -h localhost -U postgres -d karaoke -c "SELECT 1 AS smoke_test;"

# Ожидаемый вывод: одна строка "smoke_test = 1"

# 4. MinIO bucket доступен
mc alias set local http://localhost:9000 \
  $(grep STORAGE_KEY deploy/do.env | cut -d= -f2) \
  $(grep STORAGE_SECRET deploy/do.env | cut -d= -f2)
mc ls local/   # Должен показать пустой список (или служебные папки)

# 5. AI-агент понимает контекст (опционально)
# В opencode спросить: "какие контейнеры запущены локально?"
# Ожидаемый ответ: 8+ имён контейнеров + порты.
```

**Pass criteria**: все 4 группы проверок зелёные. Если хоть одна красная —
см. секцию Troubleshooting ниже.

### Шаг 5. Rollback (если нужно снести)

```bash
# Остановить контейнеры (без удаления данных)
cd ~/Karaoke/deploy
bash do.sh stop all
docker compose -f docker-compose-storage.yml down
# Опционально — Ollama:
docker compose -f docker-compose-ollama.yml down

# Снести данные (ОСТОРОЖНО — необратимо!)
sudo rm -rf /sm-karaoke/system/Караоке-db
sudo rm -rf /sm-karaoke/system/Караоке-storage

# Полный снос включая Docker-образы
docker rmi $(docker images -q svоеместо/* 2>/dev/null) 2>/dev/null
```

## Troubleshooting

### «port is already allocated» при `do.sh start all`

Какой-то порт занят. Найти:
```bash
ss -tlnp | grep -E ':(5432|7905|7906|8888|8889|9000|9001)\s'
```
Убить процесс или изменить `*_PORT_HOST` в `do.env`.

### Postgres не стартует, лог `permission denied for PGDATA`

Обычно после смены `DB_LOCAL_POSTGRES_PASSWORD` в `do.env` — Postgres
видит старые данные, но auth не сходится. Решение: либо откатить
пароль к старому значению, либо снести `DB_FOLDER` (см. Rollback).

### MinIO не стартует: «`XDG_RUNTIME_DIR` is invalid»

Docker-ce под Linux Mint иногда не прокидывает `$XDG_RUNTIME_DIR` в
контейнер. Добавить в `docker-compose-storage.yml`:
```yaml
environment:
  - XDG_RUNTIME_DIR=/tmp
```
Или запускать MinIO с `sudo systemctl restart docker` после установки.

### `docker compose config` падает с «env file not found»

Скорее всего `do.env` не лежит в `~/Karaoke/deploy/`. Проверить:
```bash
ls -la ~/Karaoke/deploy/do.env
```
Если нет — вернуться к Шагу 2.

### Karaoke-app падает с «`Could not find or load main class`»

Это известная ловушка (см. AGENTS.md, `docker-compose.yml:51-56`):
`APP_JAVA_OPTS` или `WEB_JAVA_OPTS` — **пустая строка**, не `[]`.
В `do.env` они должны быть `APP_JAVA_OPTS=""` (с кавычками), не пустыми
без кавычек.

### `setup-mint.sh` падает на «apt install docker-ce»

Возможные причины:
1. Уже стоит snap-docker — `sudo snap remove docker`.
2. Уже стоит docker.io — `sudo apt remove docker.io docker-compose`,
   потом повторить.
3. `download.docker.com` недоступен — проверить
   `curl -I https://download.docker.com/linux/ubuntu/dists/noble/Release`.

### «git: 'credential.helper' is not a git command» при клонировании

Старая версия git. На Mint 22.2 обычно 2.43+, но если нет:
```bash
sudo apt install -y git
git --version   # должно быть 2.40+
```

## Что дальше

После успешного smoke-test:
- Прочитать `docs/onboarding.md` (общий setup для любого AI-агента);
- Прочитать `AGENTS.md` (правила для opencode);
- Настроить AI-агента (Шаг 4 в `docs/onboarding.md`);
- Запустить pre-commit: `pip3 install pre-commit && pre-commit install && pre-commit run --all-files`;
- Сделать первый PR (Шаг 8 в `docs/onboarding.md`).

## Связанные артефакты

- [`spec.md`](./spec.md) — основная спека.
- [`research.md`](./research.md) — 9+1 решений (R-001..R-010) по техническим вопросам.
- [`plan.md`](./plan.md) — этот план реализации.
- [`../setup-mint.sh`](./setup-mint.sh) — bash-скрипт для воспроизводимой настройки.
- [`../../deploy/do.env.template`](../../deploy/do.env.template) — шаблон env-файла.
- [`../../docs/features/docker-deploy.md`](../../docs/features/docker-deploy.md) — per-feature документ (создаётся в Phase 1 /tasks).

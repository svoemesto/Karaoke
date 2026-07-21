# Деплой

> **Status**: active
> **Last Updated**: 2026-07-21 (PR #24, 012-development-md-rewrite)

## Серверы

### Local dev-машина (admin)

Содержит полный пайплайн: `karaoke-app`, `karaoke-web`, `webvue3`, `karaoke-public`, MinIO,
Postgres, Ollama, SearXNG, demucs-docker, keybpmfinder-docker, stemjob-docker,
rendermp4-preview-docker, telegram-proxy.

Запуск: `cd ~/Karaoke/deploy && bash do.sh build_app && cd /sm-karaoke/system/deploy && bash do.sh start_app`
(две разные папки — git-репо и рантайм).

### Прод-сервер (`<PROD_SERVER_IP>`)

Содержит ТОЛЬКО: `karaoke-web`, `karaoke-public`, MinIO, Postgres, nginx.
**`karaoke-app` на проде НЕ разворачивается** — он только для admin-машины.

nginx (443/80):
- `/` → `karaoke-public` (7907);
- `/api/*` и `/changerecords` → `karaoke-web` (8897) **напрямую, минуя** karaoke-public
  (иначе SPA возвращает 405 на POST синхронизации БД — в `80to8897` `location /api/` и
  `/changerecords` стоят **выше** `location /`);
- `/song` + User-Agent `vkShare` → `karaoke-web` (минимальный Thymeleaf).

## Сборка и деплой

### `karaoke-web` (gradle → Docker Hub → pull на сервере)

```bash
cd ~/Karaoke/deploy && bash deploy_web.sh
```

После успеха проверить:
1. В логах push **нет** `EOF`/`400 Bad request`.
2. На сервере: `Status: Downloaded newer image` (не `Image is up to date`).
3. Содержимое `application.yml`:
   ```bash
   ssh root@<PROD_SERVER_IP> "docker exec karaoke-web bash -c 'cd /tmp && jar xf /app.jar BOOT-INF/classes/application.yml && cat BOOT-INF/classes/application.yml'"
   ```
4. `docker exec karaoke-web env | grep <VAR>` — реальные env-переменные.

### `karaoke-public`

```bash
cd ~/Karaoke/deploy && bash deploy_public.sh
```

После успеха проверить: `Status: Downloaded newer image`.

### Синхронизация серверных конфигов (do.sh / docker-compose / nginx)

```bash
cd ~/Karaoke/deploy
rsync -av --exclude='do.env' web-server-deploy/deploy/ root@<PROD_SERVER_IP>:Karaoke/deploy/
scp karaoke-public/nginx_karaoke-public.conf root@<PROD_SERVER_IP>:Karaoke/deploy/nginx_karaoke-public.conf
ssh root@<PROD_SERVER_IP> "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"
```

**ВАЖНО**: nginx читает `/etc/nginx/sites-enabled/80to8897` — **ОТДЕЛЬНЫЙ файл** (не симлинк),
копировать вручную.

**`deploy_web.sh` НЕ трогает `docker-compose-web.yml`/`80to8897`/`do.sh` на сервере.** Если фикс
требует одновременно новый код **и** новую env-переменную/конфиг nginx — сначала выполнить
«Синхронизацию серверных конфигов», иначе контейнер пересоздастся по старому compose без новой переменной.

**`deploy/.env` / `do.env` НЕ перезаписывать** через rsync — содержат секреты (DB пароли, порты).

## Сборка/запуск локальных контейнеров

**ВСЕГДА из разных папок.** Реальный локальный контейнер поднимается из
`/sm-karaoke/system/deploy` (свой `do.sh`, свои `docker-compose-*-new-comp.yml`) — это не то же
самое, что git-репозиторий `~/Karaoke/deploy`. **Никогда не использовать однокомандные
`build_start_app`/`build_start_web`/`build_start_webvue3` целиком из `~/Karaoke/deploy`**
— они гоняют не тот compose-файл (репозиторный, а не `-new-comp.yml` из `/sm-karaoke/system/deploy`).
Исключение — `karaoke-public`: у него нет пары в `/sm-karaoke/system/deploy`, поэтому
`build_start_public` одной командой из `~/Karaoke/deploy` — корректно.

```bash
cd ~/Karaoke/deploy && ./do.sh build_app          # или build_web / build_webvue3 — сборка из репо
cd /sm-karaoke/system/deploy && ./do.sh start_app   # обычный запуск (или start_web / start_webvue3)
cd /sm-karaoke/system/deploy && ./do.sh start_app2  # с выводом в консоль (отладка, только app)
```

## Права на сборку/перезапуск/деплой

- **`karaoke-app`**: **сборка** разрешена без спроса (и `./gradlew :karaoke-app:compileKotlin`,
  и `do.sh build_app`). **Перезапуск контейнера** (`start_app`/`start_app2`) — **только пользователь**.
- **`karaoke-web`**, **`karaoke-public`**, **`webvue3`**: локально разрешены и сборка, и перезапуск
  без спроса. `karaoke-public` — одной командой `build_start_public` из `~/Karaoke/deploy`.
- **Деплой `karaoke-web`/`karaoke-public` на прод-сервер** (`deploy_web.sh`, `deploy_public.sh`,
  любые правки `web-server-deploy/`, прямые DDL/DML к серверной БД) — **только с согласия пользователя**,
  каждый раз заново (старое разрешение не переносится на новое действие).

## Особенности Docker-образов

- `deploy/karaoke-app/Dockerfile`: BuildKit cache mounts (apt + Playwright);
  `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright`; Docker CE установлен внутри образа намеренно
  (karaoke-app запускает `docker run`/`docker compose` из кода).
- **Проверка доступности внешних сервисов** (durable-инвариант): `ip-api.com`/`ipapi.co`/`ipapi.is`
  из Docker отдают 403/502 — **не использовать**. Использовать `api.country.is`.
- node/nginx-образы (webvue3/karaoke-public): build-stage `node:22-alpine` (**не** `node:latest`),
  npm BuildKit cache; production-stage `nginx:stable` (**не** `nginx:alpine` — compose использует
  `/bin/bash`, в alpine его нет). karaoke-web — `eclipse-temurin:22-jre-jammy` (JRE, не JDK).

## Docker-сеть

Серверная сеть называется `deploy_karaokenet` (не `karaokenet`) — учитывать в новых compose-файлах.

## Очередь gradle-сборок

`deploy/build-lock.sh` сериализует сборки через `flock` + пробу `pgrep gradle-wrapper.jar` +
**guard прямо в `gradlew`**. При неизменных исходниках сборка **пропускается** по sha256-отпечатку
в `.build/<module>.stamp`; форс — `FORCE=1` или `--force`.

## Ловушки

- **VPN+push**: если push через VPN падает по EOF на тяжёлых базовых слоях — запустить
  `deploy_web.sh` вручную без прокси.
- **Не прятать вывод сборки** за `| tail -N` (молчаливый провал gradle) — грепать
  `BUILD SUCCESSFUL`.
- **MTU black-hole на проде**: любой исходящий HTTPS с прод-контейнера `karaoke-web` на внешний хост
  (MinIO `<MINIO_IP>`, Yandex SmartCaptcha) виснет из-за MTU-дропа больших TLS-пакетов. Обход:
  HTTP через host-nginx под алиасом `minio-proxy`. **Для любого нового внешнего API из karaoke-web —
  сразу закладывать этот паттерн.** (Admin-машина не затронута.)

## См. также

- [docs/architecture-notes-archive.md](./architecture-notes-archive.md) — dated-история фич
- [docs/invariants.md](./invariants.md) — ключевые инварианты
- [docs/public-modules.md](./public-modules.md) — карта публичных модулей
- [docs/database.md](./database.md) — `tbl_public_settings`, ручные SQL-миграции

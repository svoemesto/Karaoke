# Component: do.sh (deploy entry point)

> **Домен**: system (infrastructure)
> **Компонента**: главный скрипт деплоя `deploy/do.sh`.

## Назначение

`deploy/do.sh` — **единая точка входа** для всех операций deploy/build/start/stop на admin-машине. 40 функций. Bash.

Публикация и получение контейнерных образов (спек 540 / issue #286) идут через **свой
registry** — см. [registry.md](registry.md). Логин оформлен одной функцией
`do_registry_login`: сервер указывается явно (`REGISTRY_HOST`), пароль подаётся через
stdin. Раньше логин шёл **без сервера** (уходил в Docker Hub, а имя пользователя
подставлялось из `DOCKER_REGISTRY` — это namespace Hub, не хост), и пароль попадал в
`argv`. Пока `DUAL_PUSH=1`, `do_push_*` дополнительно зеркалит образ в Hub
(`do_push_hub`) — это окно перехода, не штатный режим.

## Архитектура

### Группы функций (по KDoc/usage)

**Build (~12 функций)**:

- `do_build` — главная точка (все образы).
- `build_jars` — собирает Gradle bootJar.
- `build_images` — собирает Docker images.
- `do_build_app`, `do_build_app_nocache` — karaoke-app.
- `do_build_demucs` — образ с Demucs.
- `do_build_web` — karaoke-web.
- `do_build_webvue` / `do_build_webvue3` — старая/новая admin UI.
- `do_build_public` — karaoke-public.
- `do_build_frontend_image` — общий helper для webvue3/public: ретраи при сетевых
  ошибках и проброс `NODE_IMAGE`/`NGINX_IMAGE` (OpenProject #327).

**Build + start (~6 функций)**:

- `build_start_app`, `build_start_web`, `build_start_webvue`,
  `build_start_webvue3`, `build_start_public` — собрать И запустить.

**Start/Stop (~10 функций)**:

- `do_start`, `do_stop` — главные.
- `do_load` — загрузить initial data.
- `do_start_db`, `do_stop_db` — только БД.
- `do_start_web`, `do_stop_web` — только web.
- `do_start_webvue`, `do_stop_webvue` — только webvue.
- `do_start_webvue3`, `do_stop_webvue3` — только webvue3.
- `do_start_public`, `do_stop_public` — только public.

**Utility (~10 функций, см. файл)**:

- Health checks.
- Image prune.
- Logs.

**NB**: точная сигнатура — см. исходник `deploy/do.sh`.

## Переменные (из `do.env`, вне git)

| Переменная | Смысл |
|---|---|
| `REGISTRY_HOST` | хост:порт **для логина** (напр. `sm-karaoke.ru:5443`) |
| `REGISTRY_USER`, `REGISTRY_PASSWORD` | креды проекта |
| `DOCKER_REGISTRY` | префикс имён образов проекта (`sm-karaoke.ru:5443/karaoke`) |
| `DUAL_PUSH` | `1` — зеркалить в Docker Hub, `0` — только свой registry |
| `DOCKER_HUB_USER`, `DOCKER_HUB_PASSWORD` | namespace-архив Hub (нужен, только пока `DUAL_PUSH=1`) |
| `NODE_IMAGE` | переопределение базового node-образа для webvue3/public (по умолчанию registry-образ) |
| `NGINX_IMAGE` | переопределение базового nginx-образа для webvue3/public (по умолчанию registry-образ) |
| `FRONTEND_BUILD_ATTEMPTS` | число попыток сборки фронтенда при сетевой ошибке (по умолчанию 3) |

Шаблон — `deploy/prod-single-host/do.env.example`.

## Устойчивость сборки фронтендов (OpenProject #327)

Dockerfile'ы `deploy/karaoke-webvue3/Dockerfile` и
`deploy/karaoke-public/Dockerfile`:

- базовые образы вынесены в `ARG NODE_IMAGE` / `ARG NGINX_IMAGE` (дефолты —
  registry-образы `sm-karaoke.ru:5443/library/...`);
- зависимости ставятся `npm ci --prefer-offline --no-audit --no-fund` с
  cache-mount `/root/.npm` и `fetch-retries`/`fetch-timeout`.

`do_build_frontend_image` делает до `FRONTEND_BUILD_ATTEMPTS` попыток, но только
если в логе есть сетевая ошибка (`ETIMEDOUT`, `timeout awaiting response headers`,
`failed to do request`, `Could not resolve host`, `npm error network` и т.п.).
Ошибка Dockerfile/кода повтор не запускает.

При недоступном registry локальная сборка не требует `docker tag`:

```bash
cd deploy
NODE_IMAGE=node:22-alpine NGINX_IMAGE=nginx:stable ./do.sh build_webvue3
```

## Использование

```bash
# Из директории deploy/
cd deploy
./do.sh build_start_app    # собрать и запустить karaoke-app
./do.sh stop               # остановить всё
./do.sh build_public       # собрать karaoke-public
```

**NB**: путь к `do.env` (секреты) — `web-server-deploy/deploy/do.env`
(для прода) или `do.env` локально.

## Секреты не в argv (OpenProject #382, BR-551)

Логин в registry **везде** оформлен одной функцией `do_registry_login`:
сервер указывается явно (`REGISTRY_HOST`), пользователь — `REGISTRY_USER`,
пароль подаётся **на stdin** (`--password-stdin`). Пароль в `argv`
(`docker login --password …`) виден любому пользователю машины — это
запрещено правилом «Секреты не передаются через argv» в
[architecture-conventions.md](../../guidelines/architecture-conventions.md).

Устаревшая копия `deploy/web-server-deploy/deploy/do.sh` (DEPRECATED, см.
[`deploy-overview.md`](deploy-overview.md)) приведена к тому же контракту:
своя функция `do_registry_login` (тот же `--password-stdin`, тот же fallback на
`DOCKER_HUB_USER`/`DOCKER_PASSWORD` для старого `do.env`) **и** проверки кодов
возврата: неудачный логин или `pull` больше не «продолжает» сценарий — контейнер
не поднимается на старом образе (BR-551).

Проверка: guard `tools/check-secret-in-argv.sh` (хука `secret-in-argv-guard`).

## Guard деплой-пути: `tools/check-do-sh-guards.sh` (OpenProject #379, BR-571)

Инструмент доказывает, что деплой-путь не может быть «молча зелёным»: опыт A
(заведомо падающая сборка обязана дать ненулевой код) и опыт B (отсутствующий
образ на проде не должен сносить контейнер, только `--prod`).

Опыт A выполняется на **копии** `deploy/` во временном каталоге: «зломанный»
Dockerfile (`FROM scratch` + `RUN exit 1`) пишется в копию, а отслеживаемый
`deploy/karaoke-public/Dockerfile` **не меняется вообще**. Раньше скрипт
переписывал отслеживаемый файл и восстанавливал его в конце, а `trap` на
восстановление не был установлен — при Ctrl-C/SIGTERM в репозитории оставался
сломанный Dockerfile и неотслеживаемый `.guardtest.bak`. Guard дополнительно
проверяет, что падение вызвано именно Dockerfile (не невалидным тегом образа) —
иначе опыт ничего не доказывает.

## Известные TODO

- [ ] **Полная сигнатура** каждой из 40 функций — Pass 343+.
- [ ] **Какие команды поддерживают `*_nocache` / `*_nohup`** —
      варианты флагов.
- [ ] **Какие docker-compose-*.yml соответствуют каким командам**.
- [ ] **Health check endpoints** — где определены.
- [ ] **Error handling** — exit code strategy.

## Связь с другими компонентами

- **[deploy-overview.md](deploy-overview.md)** — общая структура
  deploy/.
- **AGENTS.md** — workflow для admin-машины.

## Changelog

- **Pass 540** (2026-10-11): логин переведён на явный сервер + `--password-stdin`,
  добавлены логины в `pull_*`, введено окно dual-push (`DUAL_PUSH`), `demucs` больше
  не тегируется `latest`. issue #286 / wayfinder #274.
- **Pass 355** (2026-09-09): Initial. Автор: agent (Karaoke).

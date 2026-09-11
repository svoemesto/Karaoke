# Local ADR-0010: рецепты адаптации к песочнице DSH (workspace-write)

* **Status**: Accepted
* **Date**: 2026-09-11
* **Deciders**: команда Karaoke (Pass 367)
* **Issue**: governance amendment (sandbox recipes)

> **English version**: TBD
>
> **Note**: это **local** ADR — описывает operational-паттерны работы агента в DSH-окружении (а не глобальное архитектурное решение).

## Context

Агенты Karaoke работают в DSH-сессиях с файловой политикой **workspace-write**:

- **writable**: `/home/nsa/Karaoke/**`, `/tmp`, runtime-данные внутри workspace.
- **read-only**: `$HOME/.gradle/wrapper/dists/`, `$HOME/.docker/`, `$HOME/.npm/`, `$HOME/.cache/`, `$HOME/.kotlin/` (если есть).

Часть сборки (Gradle wrapper, Docker buildx, npm cache, prettier/eslint cache) исторически пытается писать в эти read-only пути → падает с `read-only file system`. До этого ADR прецеденты были двух типов:

1. **В `specs/305/plan.md`**: «обходные пути» упомянуты как lessons learned (GRADLE_USER_HOME).
2. **В `specs/361/`, `specs/363/`, `specs/302/tasks.md`**: операция помечалась `DEFERRED — sandbox restriction`, а пользователь должен был выполнить её вручную на своей машине.

Прецедент (Pass 367): агент не мог выполнить `docker build` без full-access. Реальная причина — Docker CLI жёстко прописывает `$HOME/.docker/buildx/activity/` независимо от `DOCKER_CONFIG`. Изучение показало, что **обход есть** (`docker --config=...`), но он не был задокументирован.

## Проблема

Агент в workspace-write режиме **должен уметь** самостоятельно выполнять:

- Backend compile / ktlint / bootJar.
- Docker build / перезапуск контейнеров / просмотр логов.
- npm install / lint / prettier / vite build.

…без эскалации прав до `danger-full-access`, потому что в 90% случаев блокировка имеет технический обход через workspace-аналог.

## Decision

Принять **workspace-аналоги** как стандартные обходные пути для всех блокировок DSH-sandbox. Зафиксировать рецепты в [`tools/check-sandbox-ready.sh`](../../tools/check-sandbox-ready.sh) (pre-flight скрипт, печатает готовые команды) и продублировать в [`AGENTS.md`](../../AGENTS.md) § «Sandbox DSH: границы и fallback-пути».

### Таблица блокировок и обходов

| Read-only путь | Что пытается писать | Workspace-аналог | Рецепт |
|---|---|---|---|
| `$HOME/.gradle/wrapper/dists/` | Gradle wrapper при первой загрузке дистрибутива | `/home/nsa/Karaoke/.gradle/` | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (уже было) |
| `$HOME/.docker/` (включая `buildx/activity/`) | Docker CLI: каждый `build` пишет `.tmp-builderXXXX` в activity | `/home/nsa/Karaoke/.docker/` (создаём при первом запуске) | `docker --config=/home/nsa/Karaoke/.docker` (новое) |
| `$HOME/.npm/` | npm при install/build | `webvue3/node_modules/.cache/`, `karaoke-public/node_modules/.cache/` | Использовать локальный `node_modules/.cache` (уже было фактически) |
| `$HOME/.cache/` (prettier, eslint) | prettier/eslint при прогоне | `node_modules/.cache/` в каждом subproject | `--cache <project>/node_modules/.cache` (новое, advisory) |
| `$HOME/.kotlin/` | Kotlin daemon | `/home/nsa/Karaoke/.gradle/kotlin/` | Уже подхватывается через `GRADLE_USER_HOME` |
| SSH к прод-серверу | `Connection.remote()` → реальный хост | **нет** (это внешний сервис) | Только по явному согласию пользователя на каждое действие (Constitution § Ограничения) |

### Когда эскалировать на `danger-full-access`

**Только если** выполнены **все три** условия:

1. Операция изменяет состояние **вне** workspace (например, прод-БД, файл на сервере, `/etc/...`).
2. Нет технического обхода через workspace-аналог.
3. Задача не может быть выполнена отложенно без потери смысла.

**Не эскалировать** для (всё есть workspace-обход):

- Gradle compile / ktlint / bootJar.
- Docker build / restart / logs.
- npm / pnpm / vite build / lint / prettier.
- psql к **локальной** `karaoke-db` БД (контейнер доступен через docker).

### DEFERRED-формат (для tasks.md)

Если sandbox всё-таки блокирует **и** обхода нет, запись в `tasks.md`:

```markdown
- [ ] TNNN [VERIFY] DEFERRED-sandbox: <операция>. Команда для пользователя: `<cmd>`. Ожидаемый результат: <exit 0 / new artefact>.
```

Не оставлять DEFERRED без явной команды — это превращает «агент не смог» в «план для пользователя».

## Альтернативы

- **Не адаптироваться, всегда эскалировать на full-access**: rejected — ломает непрерывность работы, отвлекает владельца, не использует преимущество workspace-write (writable внутри проекта).
- **Завести отдельный subagent с full-access под каждую сборку**: rejected — ломает контекст, не масштабируется, требует постоянной ротации.
- **Bind-mount `~/.docker/buildx/activity/` поверх read-only через `mount --bind`**: rejected — требует sudo, недоступно в DSH-sandbox.
- **`docker buildx create --driver docker-container --use`**: rejected — на стадии `create` docker CLI всё равно пишет activity-файл; рецепт не обходит блокировку.

## Consequences

### Positive

- Агент может пройти полный pipeline (compile → ktlint → bootJar → docker build → restart) без единой эскалации прав.
- Не нужно ждать владельца для ручного `docker build` в каждой спеке (Pass 367 вёрифицировал: `docker --config=Karaoke/.docker build` работает на alpine sanity-test).
- Полная сборка `webvue3` (npm + prettier + vite build) работает из коробки — workspace-local cache не пишет в `$HOME/.cache`.

### Negative

- Агент должен помнить про `--config=Karaoke/.docker` для каждой docker-команды. Решение: helper `check-sandbox-ready.sh` печатает готовые команды + при `source` экспортирует функцию-обёртку `docker()`.
- Workspace `Karaoke/.gradle/` и `Karaoke/.docker/` становятся **stateful** — они растут со временем. Решение: они уже под `.gitignore` (gradle точно; docker надо проверить — см. ADR ниже).

### Neutral

- Дополнительный helper `tools/check-sandbox-ready.sh` — новый инструмент в проекте. Документация в `tools/README.md`.

## References

- [`AGENTS.md`](../../AGENTS.md) § «Sandbox DSH: границы и fallback-пути» (Pass 367)
- [`tools/check-sandbox-ready.sh`](../../tools/check-sandbox-ready.sh) — pre-flight скрипт
- [`tools/README.md`](../../tools/README.md) — индекс скриптов
- [`docs/architecture-notes.md`](../../docs/architecture-notes.md) — changelog Pass 367
- Прецедент: `specs/305-replace-systemerr-with-logger/plan.md` (workaround GRADLE_USER_HOME)
- Прецедент: `specs/361-playlists-membership-uri-length/report.md` (DEFERRED Docker build)
- Прецедент: `specs/363-album-type-filter-reset-on-album-open/report.md` (DEFERRED Docker build)
- Прецедент: `specs/302-fix-censored-name-loss/tasks.md` (DEFERRED все gradle/docker задачи — теперь unblocked)

## История

- 2026-09-11: создан (Pass 367). Прошёл верификацию на этой же машине: gradle compile/ktlint/bootJar SUCCESSFUL, docker build SUCCESSFUL на alpine sanity-test, npm lint/prettier/vite build SUCCESSFUL, docker daemon reachable через `/var/run/docker.sock`. Все блокировки имеют workspace-обход.
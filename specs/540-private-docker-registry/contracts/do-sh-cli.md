# Contract: командный интерфейс `do.sh`

**Спека**: [spec.md](../spec.md) (FR-001, FR-004, FR-012, FR-015) |
**План**: [plan.md](../plan.md) (D11, D12) | **Модель**: [data-model.md](../data-model.md) § E6

Контракт фиксирует **внешнее поведение** точки входа деплоя после правки: какие
переменные читаются из `do.env`, что делает каждая команда, что считается успехом и
как переключается окно dual-push. Файлы под контрактом: `deploy/do.sh` (admin),
`deploy/prod-single-host/do.sh` (прод), `deploy/deploy_web.sh`, `deploy/deploy_public.sh`,
`deploy/prod-single-host/do.env.example`.

---

## 1. Переменные (`do.env`, вне git; шаблон — `do.env.example`)

| Переменная | Пример | Назначение |
|---|---|---|
| `REGISTRY_HOST` | `sm-karaoke.ru:5443` | хост:порт **для логина** (без пути) |
| `REGISTRY_USER` | `karaoke` | имя учётной записи проекта |
| `REGISTRY_PASSWORD` | `…` | секрет; только в `do.env` |
| `DOCKER_REGISTRY` | `sm-karaoke.ru:5443/karaoke` | префикс имён образов проекта |
| `DUAL_PUSH` | `1` \| `0` | окно параллельной публикации (FR-015) |
| `DOCKER_HUB_USER` | `svoemestodev` | namespace Hub (архив) — нужен, только пока `DUAL_PUSH=1` |
| `DOCKER_HUB_PASSWORD` | `…` | PAT Hub; дефолт — старое значение `DOCKER_PASSWORD` |

**Правила.**
1. `REGISTRY_HOST` и путь в `DOCKER_REGISTRY` разделены **намеренно**: логин идёт на
   хост, тег — на `хост/namespace`.
2. `DOCKER_PASSWORD` сохраняется как **совместимый псевдоним** для
   `DOCKER_HUB_PASSWORD` (`${DOCKER_HUB_PASSWORD:-${DOCKER_PASSWORD:-}}`) — чтобы
   существующие `do.env` не ломались при первом применении.
3. Ни одна из переменных не печатается в вывод (Constitution VIII.5, запрет на печать
   секретов в `do.sh`).

---

## 2. Логин

Единственная форма логина в обоих `do.sh` (сейчас в `deploy/do.sh` она повторена
**6 раз** в неверном виде, в прод-`do.sh` — 2 раза):

```bash
# БЫЛО (дефект №1 карты): без SERVER → логин уходит в Docker Hub,
# --username = namespace svoemestodev, пароль виден в ps.
${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}

# СТАЛО:
do_registry_login() {
  [ -n "${REGISTRY_HOST:-}" ] || { echo "ERROR: REGISTRY_HOST не задан" >&2; return 1; }
  printf '%s' "${REGISTRY_PASSWORD}" \
    | ${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin
}
```

| Правило | Причина |
|---|---|
| Сервер указывается явно (`REGISTRY_HOST`) | без SERVER docker логинится в Docker Hub |
| Пароль через `--password-stdin`, не `--password` | пароль в argv виден в `ps` (документация Docker) |
| Ошибка логина = остановка команды (`set -e`-совместимо, `return 1`) | иначе push упадёт позже и невнятно |
| Логин идемпотентен | docker перезаписывает запись в `DOCKER_CONFIG/config.json` |

`DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (Pass 373) уже экспортируется самим
`do.sh:28-30`; каталог в `.gitignore` (`.docker/`) — креды не попадут в git.

---

## 3. Команды

### 3.1 Публикация (admin, `deploy/do.sh`)

| Команда | Поведение |
|---|---|
| `push` | `do_registry_login`; push `karaoke-app`, `karaoke-web`, `karaoke-webvue3` с тегом `${BUILD_VERSION}` |
| `push_app` | login; push `karaoke-app:${BUILD_VERSION}` |
| `push_web` | login; push `karaoke-web:${BUILD_VERSION}` |
| `push_webvue3` | login; push `karaoke-webvue3:${BUILD_VERSION}` |
| `push_public` | login; push `karaoke-public:${BUILD_VERSION}` |
| `push_demucs` | login; push `karaoke/demucs:${BUILD_VERSION}` (тег `latest` отменён) |

При `DUAL_PUSH=1` каждая команда дополнительно:

```bash
do_hub_login                                   # docker login docker.io -u "${DOCKER_HUB_USER}"
${DOCKER} tag "${DOCKER_REGISTRY}/<name>:<tag>" "${DOCKER_HUB_USER}/<name>:<tag>"
${DOCKER} push "${DOCKER_HUB_USER}/<name>:<tag>"
```

Порядок при `DUAL_PUSH=1`: **сначала наш registry, потом Hub**. Если Hub недоступен,
наш push уже состоялся и прод может деплоиться; падение Hub не должно блокировать
переход (`|| echo "WARN: Hub push failed (окно dual-push)"` — не `exit 1`).
При `DUAL_PUSH=0` ветка Hub не выполняется вовсе (SC-008).

### 3.2 Получение (admin, `deploy/do.sh`)

| Команда | Поведение |
|---|---|
| `pull`, `pull_app`, `pull_web`, `pull_webvue3` | `do_registry_login`, затем `docker-compose … pull` |
| `rmi` | удаление локальных тегов по `${DOCKER_REGISTRY}/…` (после переезда — разовая уборка старых `svoemestodev/*` руками) |

Сегодня `do_pull_app/web/webvue3` **не логинятся вовсе** (дефект №2 карты) — для
закрытого registry это недопустимо (FR-002).

### 3.3 Прод (`deploy/prod-single-host/do.sh`)

| Команда | Поведение |
|---|---|
| `pull_web` | `do_registry_login`; `docker pull "${DOCKER_REGISTRY}/karaoke-web:1"`; `do_start_web` |
| `pull_public` | `do_registry_login`; `docker pull "${DOCKER_REGISTRY}/karaoke-public:1"`; `do_start_public` |
| `start_web` / `start_public` / `start_db` / `start_storage` | без изменений (compose `up -d`) |

`deploy_web.sh` / `deploy_public.sh` на admin вызывают
`ssh root@${PROD_HOST} "cd Karaoke/deploy && bash do.sh pull_web"` — то есть контракт
прода обязан остаться совместимым по именам команд; меняется только тело `pull_*`.

---

## 4. Окно dual-push: что считается успехом

Счётчик окна (FR-015, решение #280 — «до двух успешных прод-деплоев, максимум месяц»)
увеличивается **не** на push, а на **успешное развёртывание прода**:

1. `deploy_web.sh` завершился с кодом 0; **и**
2. `do.sh pull_web` на проде завершился с кодом 0; **и**
3. `docker-compose up -d` поднял контейнер, `docker inspect` показывает образ из
   нашего registry; **и**
4. smoke (`tools/registry-smoke.sh`) — PASS.

Состояния и переходы — [data-model.md](../data-model.md) § E6. Переключение
`DUAL_PUSH=0` — ручное действие владельца (не автоматика): правило «до двух деплоев»
— это решение о переходе, а не таймер.

---

## 5. Коды возврата

| Код | Когда |
|---|---|
| 0 | команда выполнена; для `pull_*` — образ скачан и контейнер поднят |
| ≠0 | нет `REGISTRY_HOST`; ошибка логина; ошибка push/pull |
| ≠0 при `DUAL_PUSH=1` и недоступном Hub | **только** для явной команды `push_hub` (если появится); обычные `push_*` печатают WARN и возвращают 0 |

---

## 6. Совместимость и миграция значений

| Артефакт | Было | Стало |
|---|---|---|
| `deploy/prod-single-host/do.env.example` | `DOCKER_REGISTRY=svoemestodev`, `DOCKER_PASSWORD=CHANGE_ME` | `REGISTRY_HOST`, `REGISTRY_USER`, `REGISTRY_PASSWORD`, `DOCKER_REGISTRY`, `DUAL_PUSH=1`, `DOCKER_HUB_USER`, `DOCKER_HUB_PASSWORD` |
| Реальный `/root/Karaoke/deploy/do.env` и `~/Karaoke/deploy/do.env` | значения Hub | новые значения — правит владелец на каждой машине (файл вне git) |

**Критерии приёмки контракта.**

1. `grep -c 'login --username' deploy/do.sh deploy/prod-single-host/do.sh` → `0`.
2. `grep -n 'password-stdin' deploy/do.sh deploy/prod-single-host/do.sh` → есть во всех
   точках логина.
3. `bash -n deploy/do.sh deploy/prod-single-host/do.sh` → 0.
4. `DUAL_PUSH=0` ⟹ в выводе `push_*` нет ни одной строки, адресующей Docker Hub
   (проверяется по `docker push` в dry-run-режиме или ревью diff'а).
5. Пароль не встречается в выводе: `… | grep -c "${REGISTRY_PASSWORD}"` → 0.

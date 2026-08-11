# Research: Воспроизводимая настройка Linux Mint для проекта Karaoke

> **Phase 0** of `/speckit.plan` для `specs/170-mint-dev-setup/`.
> Цель — закрыть все `NEEDS CLARIFICATION` из Technical Context и зафиксировать
> технические решения, от которых зависит `setup-mint.sh` и `do.env.template`.

## Контекст

Спека требует артефакт `specs/170-mint-dev-setup/setup-mint.sh` (bash-скрипт,
идемпотентный, для Linux Mint 22.2 Zara / Ubuntu 24.04 Noble base) и
`deploy/do.env.template` (env-файл, без реальных секретов). Скрипт должен
поднимать 9 локальных контейнеров: `karaoke-app`, `karaoke-web`, `karaoke-webvue3`,
`karaoke-public`, `searxng`, `fourget`, `karaoke-db`, `karaoke-storage`,
опционально `ollama`. `do.env` в репо отсутствует — `do.sh` через `set -a` +
`source do.env` ожидает полный набор переменных окружения, иначе падает
(см. `deploy/do.sh:8-29`).

## R-001. Установка Node.js 22 LTS на Linux Mint 22.2

**Decision**: ставить Node 22.x через **официальный репозиторий NodeSource**
(`deb.nodesource.com`), не через `apt install nodejs` (там идёт Node 20.x на
Ubuntu 24.04, не 22). После установки — `npx` (не глобальный), и не ставить
глобально ничего больше, чтобы не конфликтовать с apt-managed Node.

**Rationale**:
- AGENTS.md и `docs/onboarding.md` явно требуют Node 22 LTS.
- `apt install nodejs` на Ubuntu 24.04 = Node 20.x (LTS для noble) — не 22.
  Подтверждено: на этой машине (Linux Mint 22.2 Zara) `node` сейчас вообще
  не установлен, что **только подтверждает** — настройка с нуля ещё не делалась.
- NodeSource репо: `curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -`
  + `sudo apt install -y nodejs` (включает npm 10).
- Не использовать `nvm` для скрипта: `nvm` — user-space, требует доп. шагов
  (`source ~/.nvm/nvm.sh` в каждом shell), а для dev-машины системный Node
  проще и идемпотентнее.

**Alternatives considered**:
- **nvm** — отвергнут: state в `~/.nvm`, плохо идемпотентен для скрипта, требует
  интерактивной оболочки, ломает systemd-сервисы.
- **fnm** — отвергнут: хорош, но менее распространён, не входит в apt-репы,
  требует curl-установки отдельным шагом.
- **Сборка из исходников** — отвергнут: 30+ минут компиляции, не нужно.
- **Node 20.x** — отвергнут: webvue3 / karaoke-public `package.json`
  могут требовать Node 22; `onboarding.md` явно говорит "Node 22 (LTS)".

## R-002. Установка Docker CE на Linux Mint 22.2

**Decision**: ставить **Docker CE** из официального репозитория
`download.docker.com` (НЕ `apt install docker.io` — там устаревший
docker v20 без compose v2 plugin). Compose v2 идёт отдельным пакетом
`docker-compose-plugin`, доступен как `docker compose` (с пробелом).

**Rationale**:
- AGENTS.md явно требует "Docker 24+ (с docker compose v2)".
- `apt install docker.io` на Ubuntu 24.04 = docker v20.10 + docker-compose
  v1.x (legacy python). Не совместим с `docker compose` (v2) в `do.sh`.
- Mint 22.2 = Ubuntu 24.04 noble — `download.docker.com` поддерживает noble.
- Избегать **snap** версии Docker: AGENTS.md прямо не запрещает, но
  snap-версия Docker плохо работает с bind-mounts на `/sm-karaoke/system`
  (требует `system-files` interface, настраивается отдельно).

**Alternatives considered**:
- **apt install docker.io** — отвергнут: устаревший, нет compose v2.
- **snap install docker** — отвергнут: bind-mounts проблемные,
  не идемпотентно для `apt`/`dpkg` чеков, плохая совместимость с
  systemd-managed службами.
- **Podman вместо Docker** — отвергнут: `docker-compose.yml` использует
  `driver: nvidia` (deploy.resources), `extra_hosts: host.docker.internal`
  — Podman-эмуляция работает не полностью, не стоит рисковать.
- **Rootless Docker** — отвергнут: требует доп. настройки, не
  перекрывает `docker socket`-монтирование внутри `karaoke-app`.

**Ловушка**: после установки нужно **добавить пользователя в группу `docker`**
(`sudo usermod -aG docker $USER`) и либо перелогиниться, либо
`newgrp docker`. Скрипт должен явно об этом предупредить.

## R-003. Проверка версии Linux Mint (fail-fast на неподдерживаемой)

**Decision**: скрипт проверяет `/etc/os-release` и падает, если
`VERSION != "22.2 (Zara)"` ИЛИ `ID != "linuxmint"`. Тест — в самом
начале скрипта, до любых `apt install`.

**Rationale**:
- На этой машине (Linux Mint 22.2 Zara) команды apt-установки специфичны.
  Например, `apt install python3-pip` на Ubuntu 22.04 идёт с другими deps,
  чем на 24.04; `nodejs` (v20 vs v12); `melt` доступен через разные пакеты.
- Mint 21.x = Ubuntu 22.04; Mint 22.x = Ubuntu 24.04. Они **не**
  взаимозаменяемы для скрипта.
- Fail-fast экономит время: пользователь сразу видит «Mint 22.2 нужен»,
  а не «apt failed 5 packages» в середине установки.
- Edge case в спеке (User Story 2, acceptance #3) **требует** fail-fast.

**Alternatives considered**:
- **Поддерживать 21.x и 22.x** — отвергнут: удваивает объём скрипта, требует
  двойного тестирования; спека — **первая** версия, лучше 1 ОС, но правильно.
- **Поддерживать 23.x** — отвергнут: 23.x ещё не вышел на момент 2026-08-11
  (edge case forward-compat не покрыт).
- **Generic Linux detection** — отвергнут: apt-команды всё равно различаются
  между дистрибутивами, generic-подход не работает.

## R-004. Минимальный набор переменных в `do.env.template`

**Decision**: полный набор переменных, которые `do.sh` ожидает получить
через `source do.env` под `set -a`. Все **реальные** секреты — пустые
строки или `<SET-ME>`, все **пути** — placeholder вида `<ABSOLUTE-PATH>`,
все **порты** — реальные (не секрет).

**Rationale**: `deploy/do.sh:8-29`:
```bash
set -a
...
source "${DEPLOY_DIR}/do.env"
set +a
```
Любая **отсутствующая** переменная, на которую ссылается `${VAR}` в
`docker-compose*.yml`, даст ошибку `docker compose config` ИЛИ `up`
с невнятным сообщением. Шаблон должен покрыть **все** ссылки.

**Список обязательных переменных** (из `docker-compose*.yml` + `do.sh`):

### Секреты (ОБЯЗАТЕЛЬНО заполнить на новой машине, в шаблоне — `<SET-ME>`)
- `DOCKER_REGISTRY` — `svoemesto` (по умолчанию, можно изменить на свой)
- `DOCKER_PASSWORD` — `<SET-ME>` (Docker Hub PAT, опционально если не пушить)
- `STORAGE_KEY` — `<SET-ME>` (MinIO access key, локально — любой)
- `STORAGE_SECRET` — `<SET-ME>` (MinIO secret key)
- `DB_LOCAL_POSTGRES_USER` — `postgres` (по умолчанию)
- `DB_LOCAL_POSTGRES_PASSWORD` — `<SET-ME>` (если оставить пустым — Postgres
  требует обязательно; `docker-compose-database.yml:6-7` ссылается на
  `${DB_LOCAL_POSTGRES_PASSWORD}` без дефолта, упадёт)
- `DB_SERVER_POSTGRES_USER` — `<SET-ME>` (используется в проде, на локалке
  можно оставить пустым, но переменная должна быть задана)
- `DB_SERVER_POSTGRES_PASSWORD` — `<SET-ME>` (аналогично)
- `DB_REMOTE_HOST` — `<SET-ME>` (хост удалённой БД, для локалки пусто)
- `STEMJOBS_INTERNAL_SECRET` — `<SET-ME>` (внутренний токен между app и web)
- `SILERO_*` — используется в `announce.sh` (опционально, TTS-уведомления)

### Пути (ОБЯЗАТЕЛЬНО заполнить абсолютными путями)
- `APP_FOLDER_HOST`, `APP_FOLDER_K1`, `APP_FOLDER_K2`, `APP_FOLDER_K3` —
  bind-mounts для `karaoke-app` (см. `docker-compose.yml:27-30`). Дефолт
  в существующем setup — `/sm-karaoke/system/Караоке` + `/Караоке-1/2/3`.
- `APP_FOLDER_SYSTEM` — `/sm-karaoke/system`
- `APP_FOLDER_STORE` — `/sm-karaoke/system/Караоке-store` (стемы, видео)
- `APP_FOLDER_IN_CONTAINER` — `/Караоке` (внутри контейнера)
- `STORAGE_FOLDER` — `/sm-karaoke/system/Караоке-storage` (MinIO volume)
- `DB_FOLDER` — `/sm-karaoke/system/Караоке-db` (Postgres data dir)
- `WEB_FOLDER_HOST`, `WEB_FOLDER_IN_CONTAINER` — для `karaoke-web`
  (используется в `docker-compose-web.yml`, на локалке можно оставить дефолт)
- `WEBVUE_PATH_TO_NGINX_CONF` — путь к nginx-конфигу для `karaoke-webvue`
  (legacy) — `./karaoke-webvue/nginx.conf` или аналогично
- `WEBVUE3_PATH_TO_NGINX_CONF` — путь к nginx-конфигу для `karaoke-webvue3`
  (текущая админка) — `./webvue3/nginx.conf` (см. структуру `webvue3/`)
- `PUBLIC_PATH_TO_NGINX_CONF` — путь к nginx-конфигу для `karaoke-public`
  — `./karaoke-public/nginx.conf`

### Порты (дефолты безопасные — оставить как есть)
- `APP_PORT_HOST=8900`, `APP_PORT_INSIDE_CONTAINER=8080`
- `WEB_PORT_HOST=8090`, `WEB_PORT_INSIDE_CONTAINER=8090`
- `DB_PORT_HOST=5432`, `DB_PORT_INSIDE_CONTAINER=5432`
- `STORAGE_PORT_HOST=9000`, `STORAGE_PORT_INSIDE_CONTAINER=9000`
- `STORAGE_CONSOLE_PORT_HOST=9001`, `STORAGE_CONSOLE_PORT_INSIDE_CONTAINER=9001`

### Прочие (дефолты OK, но должны быть в файле)
- `BUILD_VERSION` — автогенерируется в `do.sh:19-26` (если пуст — читает
  `deploy/.version`), можно оставить пустым
- `ENABLE_APP_GPU=0` — для dev-машины без nvidia passthrough (см.
  `AGENTS.md`: «Докер-ловушки», `do.sh:31-36`)
- `APP_JAVA_OPTS=""`, `WEB_JAVA_OPTS=""` — пустые строки; **строка**, не
  список, иначе java воспримет пустой аргумент как main-класс
  (см. `docker-compose.yml:51-56` — подробный комментарий)
- `DATABASE=""` — внутренняя, вычисляется в `do.sh`; НЕ в шаблоне
- `APP_GPU_COMPOSE_FILE=""` — внутренняя, вычисляется в `do.sh`; НЕ в шаблоне
- `BL_*` — build-lock внутренние; НЕ в шаблоне
- `WORK_IN_CONTAINER` — НЕ в шаблоне (выставляется в compose-файлах как `1`)

**Ловушка**: `do.sh` под `set -a` экспортирует **все** переменные из `do.env`,
и они утекают в `gradle` и `docker` процессы (см. `AGENTS.md` → `set -a` без
`set +a`). Поэтому шаблон НЕ должен содержать переменных, которые **не нужны**
(никаких закомментированных экспериментов). Минимально-достаточный набор.

**Alternatives considered**:
- **Копировать `do.env` с реального dev-pc** — отвергнут: Principle VIII.1
  (секреты НЕ в гите); шаблон — единственный безопасный путь.
- **Загружать секреты через Vault/1Password CLI** — отвергнут: out of scope,
  добавляет внешнюю зависимость.
- **Хранить секреты в env пользователя (`~/.bashrc`)** — отвергнут: не
  переносимо между машинами, `do.sh` всё равно читает `do.env` под `set -a`.

## R-005. Стратегия проверки идемпотентности `setup-mint.sh`

**Decision**: для каждого устанавливаемого пакета — проверка через
`dpkg-query -W -f='${Status}' <pkg>` (стандартный способ), для Docker —
`command -v docker && docker --version`, для пользователя в группе `docker` —
`id -nG "$USER" | tr ' ' '\n' | grep -qx docker`. Если всё OK — пропуск
с сообщением «already installed», иначе — установка.

**Rationale**:
- Скрипт должен быть **идемпотентным** (FR-013). Например, повторный запуск
  не должен заново ставить Docker, если он уже стоит.
- `dpkg-query` — канонический способ для apt-managed пакетов, работает
  даже если `apt` ещё не успел обновить индекс.
- `id -nG` для группы — POSIX-совместимо, не требует `getent group`.

**Alternatives considered**:
- **`which <cmd>`** — отвергнут: не POSIX, на Mint 22.2 иногда отсутствует
  в PATH при login-shell vs non-login shell.
- **`command -v <cmd>`** — годится для бинарников, но **не** для apt-пакетов
  (если apt-пакет поставлен, но бинарник в `/usr/sbin` — `command -v` его
  не найдёт в non-root shell).
- **`apt list --installed <pkg>`** — медленный, дёргает apt-кеш; `dpkg-query`
  работает по локальной БД dpkg.

## R-006. Порядок поднятия контейнеров (race conditions)

**Decision**: поднимать контейнеры в порядке, который избегает race conditions:
1. Сначала `karaoke-db` (Postgres инициализируется 10-30 сек);
2. Затем `karaoke-storage` (MinIO стартует быстро, 3-5 сек);
3. Затем `karaoke-app` (зависит от MinIO и Postgres, см.
   `docker-compose.yml:9-22` — extra_hosts, env-переменные);
4. Затем `karaoke-web`, `karaoke-webvue3`, `karaoke-public` (зависимы от
   `karaoke-app` через `depends_on`);
5. Затем `searxng` и `fourget` (независимы, можно в любой момент).

`do.sh start all` уже поднимает `docker-compose.yml` + `docker-compose-database.yml`
в одном вызове — `depends_on` внутри compose гарантирует порядок **внутри** одного
файла, но не **между** файлами. Скрипт **двухшаговый**: сначала `start all` (db +
app+web+searxng+fourget), потом отдельный `docker compose -f docker-compose-storage.yml up -d` (MinIO).

**Rationale**:
- Postgres в `karaoke-db` контейнере **не** принимает соединения, пока
  `initdb` не закончен. Если `karaoke-app` стартует раньше — падает
  с `Connection refused` в цикле, потом всё-таки подключается (Spring retry),
  но логи захлёбываются спамом.
- MinIO на Linux Mint без nvidia passthrough стартует за 3-5 сек; в нём
  нет критичных init-скриптов, но `karaoke-app` использует MinIO при
  инициализации Spring beans, поэтому `depends_on` обязателен.
- `searxng` и `fourget` — независимые search-бэкенды, нужны только
  при поиске текстов/обложек; их можно поднимать последними.

**Alternatives considered**:
- **`depends_on` в кастомном compose-файле** — отвергнут: spec говорит
  использовать существующий `do.sh start all` (FR-006), а не плодить
  новые compose-файлы.
- **Healthcheck-based wait** — отвергнут: Postgres healthcheck в
  `docker-compose-database.yml` не настроен; добавлять его в общую
  спеке было бы out of scope.
- **`wait-for-it.sh`** — отвергнут: это разовая задача, спека-скрипт
  идёт через простой `sleep` + `docker logs` retry в smoke-test (FR-008).

## R-007. Smoke-test после `docker compose up` — retry-loop на БД

**Decision**: smoke-test (FR-008) делает 5 попыток с `sleep 3` между ними
для каждого эндпоинта, потому что Postgres init может занимать 10-30 сек
на первой инициализации (создание `PGDATA`, прогон
`docker-entrypoint-initdb.d/*.sql` из `deploy/karaoke-db/`).

**Rationale**:
- `docker compose up -d` возвращает 0 сразу после старта процессов, **не**
  дожидаясь готовности.
- `psql -h localhost -U postgres -c '\l'` на свежем Postgres контейнере
  может упасть с "connection refused" в первые 10-15 сек.
- Спека (FR-008) требует, чтобы smoke-test был зелёным после поднятия —
  retry-loop с понятным сообщением «БД инициализируется, попытка 1/5...»
  — это и user-friendly, и идемпотентно.

**Alternatives considered**:
- **`docker compose wait <service>`** — отвергнут: docker compose v2 не
  имеет команды `wait` (только `docker wait` для одного контейнера,
  не compose).
- **Polling healthcheck** — отвергнут: healthcheck для Postgres не
  настроен в `docker-compose-database.yml`.
- **Бесконечный `until`** — отвергнут: без timeout скрипт может
  зависнуть навечно; 5 попыток × 3 сек = 15 сек max, этого достаточно.

## R-008. Rollback-стратегия: что сносить и в каком порядке

**Decision**: rollback (FR-009) делается в обратном порядке: сначала
контейнеры приложения, потом БД и MinIO (чтобы случайно не снести данные
с активным контейнером). Для полного сноса окружения:
```bash
bash deploy/do.sh stop all
docker compose -f deploy/docker-compose-storage.yml down
# Снести данные (ОСТОРОЖНО — необратимо):
sudo rm -rf /sm-karaoke/system/Караоке-db /sm-karaoke/system/Караоке-storage
```

**Rationale**:
- `do.sh stop all` останавливает контейнеры в `docker-compose.yml` +
  `docker-compose-database.yml`, но **не** MinIO (он в отдельном файле).
- `docker compose down` без `-v` сохраняет volumes; `down -v` сносит
  анонимные volumes, но **не** named bind-mounts (как у нас).
- Удаление `DB_FOLDER` = удаление всех данных Postgres. **Необратимо**.
  Скрипт спрашивает подтверждение `read -p "Точно удалить данные? (yes/no) "`.
- При rollback Docker-образы **не** удаляются — они могут быть переиспользованы
  при повторной настройке. Удаление образов = отдельная команда
  `docker rmi $(docker images -q $DOCKER_REGISTRY/*)` (вне scope спеки).

**Alternatives considered**:
- **Снести всё включая Docker-образы** — отвергнут: слишком агрессивно;
  пользователь, возможно, хочет переустановить ОС, а не вообще всё.
- **Снапшоты BTRFS/ZFS перед установкой** — отвергнут: не входит в
  `setup-mint.sh`, требует root на этапе разметки диска, out of scope.
- **Просто `docker compose down`** — отвергнут: НЕ удаляет контейнеры
  `karaoke-app` (т.к. он не в `docker-compose.yml`); нужны обе команды.

## R-009. Per-feature документ `docs/features/docker-deploy.md` — что туда писать

**Decision**: создать/обновить `docs/features/docker-deploy.md` (per FR-009
Constitution: правка docker-стека = обновление per-feature документа в том
же PR). Документ должен покрывать:
1. Что такое docker-стек Karaoke (8-9 контейнеров, bind-mounts, сеть `karaokenet`).
2. Зачем: «чтобы локально поднимать полное окружение для разработки».
3. Как работает: `do.sh start all` + отдельный MinIO, env-шаблон, секреты.
4. Инварианты: `do.env` НЕ в гите, `do.env.template` — коммитится; bind-mounts
   на абсолютных путях; `ENABLE_APP_GPU=0` на dev без nvidia; `nginx:stable`,
   не `alpine`.
5. Ловушки: 4-5 штук (см. `AGENTS.md` → «Dockerfile-ловушки»: `nginx:alpine`,
   `node:latest`, `JDK` вместо `JRE` в проде, `ip-api.com` 403 из Docker,
   `redirectErrorStream(false)` блокирует процесс).
6. Ссылки на эту спеку + `setup-mint.sh` + `do.env.template`.

**Rationale**:
- Per FR-009 Constitution, FR-009 спеки, и Q&A «Как добавить per-feature документ».
- Без этого документа новый разработчик не найдёт единой карты docker-стека —
  придётся читать `docker-compose*.yml` + `do.sh` + `AGENTS.md` + Constitution.

**Alternatives considered**:
- **Объединить с `docs/features/dual-db-sync.md`** — отвергнут: разные темы
  (sync vs deploy), `docker-deploy.md` шире.
- **Не создавать документ** — отвергнут: нарушает FR-009 (Constitution) и FR-024 (чеклист).
- **Положить в `docs/onboarding.md`** — отвергнут: `onboarding.md` — общий
  setup для любого AI-агента, не per-feature; лучше вынести отдельно.

## Итоги

Все 9 исследований завершены. Никаких `NEEDS CLARIFICATION` не осталось.
Готово к **Phase 1: Design & Contracts**.

**Главные решения для Phase 1**:
- `setup-mint.sh` — bash, идемпотентный, ~250 строк, разделы: (1) OS-check,
  (2) sudo-check, (3) apt-установки, (4) Docker, (5) Node 22, (6) git-config,
  (7) clone-or-update repo, (8) bind-mount папок, (9) `do.env` copy from template,
  (10) `do.sh pull` + `start all` + MinIO, (11) smoke-test, (12) rollback instructions.
- `do.env.template` — текстовый файл ~40-50 строк, с комментариями `#` (хотя `do.sh`
  через `set -a` source-ит, и `set -a` экспортирует все присваивания; комментарии
  `#` — это присваивание с именем `#`, что утечёт в env как `set -a` + source
  → переменная `#` = `comment`. **Ловушка**: комментарии `# ...` под `set -a`
  ПРИВОДЯТ к ошибке `bash: ...: command not found` или экспорту мусора.
  **Решение**: использовать shell-комментарии внутри `<<'EOF' ... EOF` heredoc
  НЕ получится — `do.sh` ожидает KEY=VALUE. **Альтернатива**: использовать
  пустые строки и `### Комментарий` (без `#` в начале строки, иначе
  `set -a` воспримет `###` как имя переменной с пустым значением; на
  практике `#` в начале — это `set -a` интерпретирует как `command #`
  с пустым значением переменной `#`, что безвредно). Финальный вариант:
  комментарии в **виде отдельных строк, начинающихся с `# ` — bash
  пропускает их как комментарии даже под `set -a` потому что `#` —
  это не присваивание, а синтаксическая ошибка, которую `source` пропускает
  при `set -a` НЕ подавляя ошибки. Нужно протестировать!
  → **Action item для Phase 1**: R-010 ниже.
- data-model.md — **skip** (нет data-entities, проект инфраструктурный).
- contracts/ — **skip** (нет external interfaces, проект внутренний).
- quickstart.md — **создать**, документирует runnable validation scenarios
  (smoke-test из FR-008, плюс rollback из R-008).

## R-010. (открыто во время research) Как добавлять комментарии в do.env под `set -a`

**Decision** (требует проверки в Phase 1): bash `set -a; source file; set +a`
позволяет `#` в начале строки без поломки. **Ловушка**: `set -a` НЕ
включает комментарии в экспорт (комментарии — это синтаксис, не
присваивание). Проверить: `bash -c 'set -a; source /tmp/x.env; set +a; env | grep -v "^[A-Z_]*=" | head'`,
где `/tmp/x.env` содержит `# comment` + `FOO=bar`. Должно вывести только `FOO=bar`.

**Action**: перед коммитом `do.env.template` выполнить этот тест и
зафиксировать результат в `do.env.template` README-комментарии (если
комментарии работают) ИЛИ удалить все `#` (если нет).

**Статус**: resolved в Phase 1 (проверкой).

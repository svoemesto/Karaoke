# Tasks: Свой приватный Docker-registry на sm-karaoke.ru

**Input**: Design documents from `/specs/540-private-docker-registry/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required для
user stories), [research.md](./research.md), [data-model.md](./data-model.md),
[contracts/](./contracts/) (4 файла), [quickstart.md](./quickstart.md)

**Tests**: отдельных тест-задач нет — спека не запрашивает TDD, и в проекте нет
инфраструктурного тест-фреймворка (CI docker-образы не собирает, проверено:
`.github/workflows/lint.yml` запускает только guard). Роль проверок играют сценарии
[quickstart.md](./quickstart.md) (Q1–Q12) и `--selftest` guard'а; они оформлены как
обычные задачи, потому что без них требования FR-005/FR-006/FR-011/FR-016 и SC-002/
SC-005/SC-006/SC-009 не проверяются ничем.

**Терминология**: «образ» = **контейнерный образ** (spec.md § Key Entities).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 / US4 (карта — spec.md § User Scenarios)
- Exact file paths are included in every task

## Path Conventions

Проект — монорепозиторий с раздельными канонами деплоя (см. plan.md § Project
Structure):

- `deploy/` — admin-машина (`nsa-i9`), сборка и публикация;
- `deploy/prod-single-host/` — канон прод-хоста (`sm-karaoke.ru` = `/root/Karaoke/deploy/`);
- `tools/` — guard'ы и приёмка;
- `knowledge/`, `docs/features/` — SSoT-документация;
- `specs/540-private-docker-registry/` — артефакты этой спеки.

**Границы (Constitution + слово владельца):**
задачи, помеченные **`(по слову владельца)`**, меняют прод-хост
(`188.127.240.124`) и выполняются только по отдельному согласию на каждое действие;
коммитов и деплоя без слова владельца нет. Работа — в ветке
`540-private-docker-registry`. Любые `docker`-команды — с
`DOCKER_CONFIG=/home/nsa/Karaoke/.docker`; перезапуск контейнеров — только через
`deploy/do.sh`, `docker restart` запрещён.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: артефакты registry в репозитории. Прод не затрагивается — всё
создаётся и проверяется на admin.

- [X] T001 Создать `deploy/prod-single-host/registry/config.json` на основе `.scratch/karaoke/registry/stand/config.json` (рабочий конфиг, принят Zot v2.1.22 без правок) с четырьмя изменениями: `http.realm = "karaoke-registry"`, `log.level = "info"` (вместо `debug`), третий retention-блок для `mirror/**` (`deleteUntagged: true`, без `keepTags`), один upstream `https://registry-1.docker.io` с обязательным `content: [{"prefix": "library/**"}]`; блоки `ghcr.io`/`quay.io` из черновика разведки не переносить — `deploy/prod-single-host/registry/config.json`
- [X] T002 [P] Создать `deploy/prod-single-host/registry/htpasswd.example` (формат `user:$2b$…`, без реальных секретов) и добавить `/deploy/prod-single-host/registry/htpasswd` в `.gitignore`; проверить `git ls-files | grep -i htpasswd` → пусто — `deploy/prod-single-host/registry/htpasswd.example`, `.gitignore`
- [X] T003 [P] Создать `deploy/prod-single-host/registry/images.lock` с шапкой `upstream_ref | our_ref | digest | checked_at` и заготовками строк для восьми сторонних образов (см. `contracts/image-naming.md` § 3.2): `ollama/ollama`, `hwdsl2/whisper-server`, `searxng/searxng`, `luuul/4get`, `xtls/xray-core`, `minio/minio`, `nvidia/cuda`, `library/postgres` — `deploy/prod-single-host/registry/images.lock`
- [X] T004 Создать `deploy/prod-single-host/docker-compose-registry.yml`: сервис `karaoke-registry`, образ `ghcr.io/project-zot/zot:v2.1.22`, `ports: ["127.0.0.1:5000:5000"]`, `restart: unless-stopped`, bind-mount `/sm-karaoke/system/registry:/var/lib/registry`, `./registry/config.json:/etc/zot/config.json:ro`, `./registry/htpasswd:/etc/zot/htpasswd:ro`, healthcheck на `GET /v2/` с ожиданием `401` — `deploy/prod-single-host/docker-compose-registry.yml`
- [X] T005 [P] Создать серверный блок host-nginx `deploy/prod-single-host/5443to5000` строго по `contracts/registry-transport.md` § 2: `listen 5443 ssl`, `server_name sm-karaoke.ru`, существующий сертификат `/etc/keys/www.sm-karaoke.ru.{crt,key}`, `client_max_body_size 0`, `chunked_transfer_encoding on`, `proxy_request_buffering off`, `location /v2/` → `http://127.0.0.1:5000` с `proxy_set_header Host $http_host` и таймаутами 900 с, `location /` → 404 (FR-009 — защищённый канал; FR-012 — внедрение не требует перезапуска прод-сервисов) — `deploy/prod-single-host/5443to5000`
- [X] T006 [P] Создать `deploy/prod-single-host/registry/README.md` — раскладка registry на хосте (каталог данных, монтируемые файлы, порты, откуда что копируется) — FR-001 (registry на нашем собственном хосте) — `deploy/prod-single-host/registry/README.md`
- [X] T007 [P] Создать каркас `deploy/prod-single-host/registry/RUNBOOK.md` с тремя разделами-заголовками (выдача и отзыв доступа; восстановление содержимого; срок сертификата) — каркас под FR-011 (процедура восстановления) и FR-016 (срок сертификата), наполнение — в задачах US2/US4 — `deploy/prod-single-host/registry/RUNBOOK.md`
- [X] T008 Обновить `deploy/prod-single-host/README.md`: в раскладку добавить `registry/` (config, htpasswd, images.lock, RUNBOOK), `docker-compose-registry.yml`, `5443to5000` и том `/sm-karaoke/system/registry` (FR-001) — `deploy/prod-single-host/README.md`

**Checkpoint**: `docker compose -f deploy/prod-single-host/docker-compose-registry.yml config` проходит; `docker run --rm -v "$PWD/deploy/prod-single-host/registry:/c:ro" ghcr.io/project-zot/zot:v2.1.22 verify /c/config.json` принимает файл; `python3 -m json.tool < deploy/prod-single-host/registry/config.json` не падает.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: общий контракт логина и общие инструменты, без которых не работает ни
одна история; плюс доказательство, что артефакты Setup рабочие.

**⚠️ CRITICAL**: до завершения этой фазы ни одна user story не проверяема.

- [X] T009 В `deploy/do.sh` добавить функцию `do_registry_login` (`printf '%s' "${REGISTRY_PASSWORD}" | ${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin`) и заменить ею **все 6** вхождений `${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}` (строки 325, 334, 341, 348, 355, 362), а также добавить логин в `do_pull_app`/`do_pull_web`/`do_pull_webvue3`, которые сейчас не логинятся вовсе — `deploy/do.sh`
- [X] T010 [P] В `deploy/prod-single-host/do.sh` заменить два логина `--username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}` (в `pull_web`, `pull_public`) на ту же форму `do_registry_login` — `deploy/prod-single-host/do.sh`
- [X] T011 [P] Обновить `deploy/prod-single-host/do.env.example`: `REGISTRY_HOST=sm-karaoke.ru:5443`, `REGISTRY_USER=karaoke`, `REGISTRY_PASSWORD=CHANGE_ME`, `DOCKER_REGISTRY=sm-karaoke.ru:5443/karaoke`, `DUAL_PUSH=1`, `DOCKER_HUB_USER=svoemestodev`, `DOCKER_HUB_PASSWORD=CHANGE_ME` (FR-015 — переключатель окна dual-push) — `deploy/prod-single-host/do.env.example`
- [X] T012 [P] Создать `tools/registry-smoke.sh` — приёмка по `contracts/registry-transport.md` § 5 и `data-model.md` § E3: матрица `401/401/401` без кредов, `401` с неверным паролем, `200` с верным, `404` на `/`, отзыв доступа даёт `401` без перезапуска; инвариант I-2 проверяется исполнимо: для каждого репозитория `library/*` в нашем registry дайджест манифеста сверяется с манифестом того же тега в `registry-1.docker.io` — расхождение означает нашу запись в read-only зоне (нарушение); принимает `<host:port>` аргументом — `tools/registry-smoke.sh`
- [X] T013 [P] Создать `tools/registry-vendor.sh` — вендоринг по `images.lock` (`pull` → `tag` → `push` в `mirror/**`) и режим `--verify` (сверка дайджеста в registry с записью в lock-файле); пароль через stdin, без печати секретов — `tools/registry-vendor.sh`
- [X] T014 Стенд-репетиция на admin: поднять `karaoke-registry` в одноразовом каталоге `/tmp/registry-rehearsal` с тем же `config.json` и сгенерированным `htpasswd` (bcrypt через `python3 -W ignore -c "import crypt"` — **только admin**, на проде Python ≥ 3.13 без модуля `crypt`), затем `bash tools/registry-smoke.sh localhost:<порт>` → все проверки PASS — `deploy/prod-single-host/docker-compose-registry.yml`, `deploy/prod-single-host/registry/config.json`

**Checkpoint**: `registry-smoke.sh` зелёный на стенде; `bash -n deploy/do.sh deploy/prod-single-host/do.sh` — чисто; `grep -c 'login --username' deploy/do.sh deploy/prod-single-host/do.sh` → `0`.

---

## Phase 3: User Story 1 — Деплой перестаёт зависеть от Docker Hub (P1) 🎯 MVP

**Goal**: полный цикл «сборка на admin → публикация → развёртывание прода» проходит
через наш registry, без единого обращения к Docker Hub за образами проекта.

**Independent Test**: [quickstart.md](./quickstart.md) Q2 (push/pull ≥5 ГБ через
прокси), Q5 (нет внешних реестров в пути прода), Q10 (прод не остановлен), Q12
(окно dual-push работает) — плюс `docker inspect` на проде показывает наш registry.

- [X] T015 [P] [US1] Добавить в `deploy/do.sh` ветку `DUAL_PUSH` во все `do_push_*`: при `DUAL_PUSH=1` после успешного push в наш registry — `docker tag` в `${DOCKER_HUB_USER}/…` и push в Hub с `WARN` (не `exit 1`) при недоступности Hub; при `DUAL_PUSH=0` ветка Hub не выполняется вовсе (SC-008) — `deploy/do.sh`
- [X] T016 [P] [US1] Обновить тексты шагов и проверки в `deploy/deploy_web.sh` и `deploy/deploy_public.sh`: «Push в Docker Hub» → «Push в registry», после `pull_*` на проде проверять `Status: Downloaded newer image` (не `Image is up to date`) (FR-015) — `deploy/deploy_web.sh`, `deploy/deploy_public.sh`
- [X] T017 [US1] Проверка FR-005/FR-006 на стенде: push образа ≥5 ГБ **при включённом sync** и pull его обратно через nginx-прокси; зафиксировать, что `Location` в ответе `POST /v2/…/blobs/uploads/` относительный (начинается с `/v2/`), что нет `503` из ловушки `onDemand` без `content`, и что pull-through `library/postgres:16` продолжает работать; **edge case «обрыв публикации большого образа»**: прервать push большого слоя на середине и убедиться, что прежний тег по-прежнему отдаёт прежний манифест, а «полуобраз» прод-хосту не виден; **и FR-017 по существу, без прода**: собрать на стенде пробный образ с `FROM localhost:<стенд>/library/node:22-alpine` — pull-through обязан обслужить сборку (FR-005, FR-006, FR-017) — сценарий Q2, `quickstart.md`
- [X] T018 [US1] **(по слову владельца)** Развернуть registry на проде, не трогая работающие сервисы: `scp` содержимого `deploy/prod-single-host/*` в `/root/Karaoke/deploy/`, создать `/sm-karaoke/system/registry`, положить `registry/htpasswd` (0600), `docker compose -f docker-compose-registry.yml up -d` (**новый** контейнер), положить `5443to5000` в `/etc/nginx/sites-enabled/`, `nginx -t && systemctl reload nginx`; существующие четыре контейнера не перезапускаются (FR-012, SC-003) — сценарий Q10, `deploy/prod-single-host/`
- [X] T019 [US1] **(по слову владельца)** Прописать реальные значения в `/root/Karaoke/deploy/do.env` на проде и в `~/Karaoke/deploy/do.env` на admin (`REGISTRY_HOST`, `REGISTRY_USER`, `REGISTRY_PASSWORD`, `DOCKER_REGISTRY`, `DUAL_PUSH=1`, `DOCKER_HUB_*`); шаблон — `deploy/prod-single-host/do.env.example`; значения в git не попадают
- [X] T020 [US1] **(по слову владельца)** Первый прод-деплой в окне dual-push: `bash deploy/deploy_web.sh` → push в наш registry **и** в Hub, pull с нашего на проде; подтвердить `docker inspect --format '{{.Config.Image}}' karaoke-web` → имя с `sm-karaoke.ru:5443/` — сценарий Q5, `deploy/deploy_web.sh`
- [X] T021 [US1] **(по слову владельца)** Второй прод-деплой — закрывает счётчик окна (решение #280: до двух успешных развёртываний прода) — `deploy/deploy_web.sh`, `deploy/deploy_public.sh`
- [X] T022 [US1] **(по слову владельца)** Закрыть окно: `DUAL_PUSH=0` в `do.env` на admin и на проде; namespace `svoemestodev` в Hub **не удалять** — он остаётся read-only архивом отката (SC-008) — `deploy/prod-single-host/do.env.example`
- [X] T023 [US1] Валидация истории: прогнать Q5 и Q12 из `quickstart.md` — ноль внешних реестров в `image:` и в `FROM`, `docker manifest inspect` в обоих источниках во время окна, лог Zot без обращений к `docker.io/…/karaoke/*` — `quickstart.md`

**Checkpoint**: SC-001 (цикл без обращения к внешнему реестру), SC-002 (push 5.4 ГБ),
SC-003 (прод не остановлен), SC-008 (после окна — ноль внешних реестров);
RSS `docker stats --no-stream karaoke-registry` ≤ **150 МиБ** в покое (замер стенда:
49.9 МиБ покой, 114.3 МиБ пик на push 5.36 ГБ).

---

## Phase 4: User Story 2 — Доступ выдаётся и отзывается по проектам (P2)

**Goal**: один комплект учётных данных на проект; выдача и отзыв — **одной процедурой**
(FR-004) за не более чем одну минуту (SC-004), без остановки registry; анонимного
доступа нет.

**Independent Test**: [quickstart.md](./quickstart.md) Q3 — матрица `401/401/200`,
изоляция namespace, отзыв даёт `401` без перезапуска контейнера.

- [X] T024 [US2] Сгенерировать `htpasswd` на admin (bcrypt: `python3 -W ignore -c "import crypt; print('karaoke:'+crypt.crypt('…', crypt.mksalt(crypt.METHOD_BLOWFISH)))"`), положить на стенд и (по слову владельца) на прод в `/root/Karaoke/deploy/registry/htpasswd` с правами 0600; в репозиторий попадает только `htpasswd.example` — `deploy/prod-single-host/registry/htpasswd.example`
- [X] T025 [US2] Написать в `RUNBOOK.md` процедуру выдачи и отзыва доступа: «выдать = строка в `htpasswd` + политика в `config.json`; отозвать = удалить строку», с явной отметкой, что Zot перечитывает оба файла на лету и перезапуск не нужен, а вся процедура укладывается в минуту (FR-004 — «одна процедура», SC-004 — «не более чем одна минута»); bcrypt генерировать **только на admin** (на проде нет модуля `crypt`) — `deploy/prod-single-host/registry/RUNBOOK.md`
- [X] T026 [US2] Проверка FR-002/FR-003/FR-004 и SC-004/SC-006: `bash tools/registry-smoke.sh <host:port>` — анонимные `401` на `/v2/`, `/v2/_catalog` и `/v2/<name>/manifests/<tag>`, верный пароль `200`, после удаления строки из `htpasswd` — снова `401` **без перезапуска**; приёмка запускается **с admin-машины** (внешняя попытка), а не с localhost прода — SC-006 требует проверки снаружи, а не рассуждением — сценарий Q3, `tools/registry-smoke.sh`
- [X] T027 [US2] Проверка сценария внешнего временного пользователя (US2-3): завести синтетический второй проект — свой namespace (`mirror/**` или отдельный префикс) и свой кред; убедиться, что он не видит `karaoke/**`, и что снятие строки из `htpasswd` оставляет запросы `401`; порядок выдачи и полного снятия доступа описать в `RUNBOOK.md`; **edge case «одновременная публикация из двух проектов»** — выполнить два параллельных push в разные namespace и убедиться, что публикации не мешают друг другу и не смешиваются — `deploy/prod-single-host/registry/RUNBOOK.md`

**Checkpoint**: US1 и US2 работают независимо: доступ можно выдать/отозвать, не
трогая публикацию образов, и наоборот.

---

## Phase 5: User Story 3 — Базовые и сторонние образы тоже доступны изнутри (P3)

**Goal**: прод и сборка получают готовые образы из нашего registry с
зафиксированными версиями; внешний реестр перестаёт быть обязательным звеном.

**Independent Test**: [quickstart.md](./quickstart.md) Q4 (guard ловит намеренную
поломку после смены имён), Q6 (`registry-vendor.sh --verify` зелёный, все
`mirror/**` с дайджестом).

- [X] T028 [US3] Расширить `tools/check-docker-image-tags.sh`: нормализовать имя (срезать registry-хост и порт, отрезать `@sha256:`, взять последний компонент пути + тег), добавить режим `--selftest` с набором из `contracts/tag-guard.md` § 5.1, и заменить вывод «N/A» на FAIL, если нормализованное имя `nginx`/`node` не было распознано — `tools/check-docker-image-tags.sh`
- [X] T029 [P] [US3] Переключить `FROM` боевых Dockerfile'ов на наш registry: `openjdk:17-ea-jdk-slim` и `ubuntu:22.04` → `<registry>/library/…` (`deploy/karaoke-app/Dockerfile`), `eclipse-temurin:22-jre-jammy` (`deploy/karaoke-web/Dockerfile`), `node:22-alpine` и `nginx:stable` (`deploy/karaoke-public/Dockerfile`, `deploy/karaoke-webvue3/Dockerfile`), `nvidia/cuda:12.6.2-base-ubuntu22.04` → `<registry>/mirror/nvidia/cuda:…@sha256:…` (`deploy/karaoke-app/DockerfileDemucs`) — `deploy/karaoke-*/Dockerfile*`
- [X] T030 [US3] Вендоринг **восьми** сторонних образов в `mirror/**` через `tools/registry-vendor.sh` (шесть из решения #279: `ollama`, `whisper-server:cuda`, `searxng`, `4get`, `xray-core`, `demucs`; плюс `minio` по FR-007/US3-1 и `nvidia/cuda` по FR-017), с фиксацией дайджестов в lock-файле; формат — `<tag>@sha256:<digest>` — `deploy/prod-single-host/registry/images.lock`, `tools/registry-vendor.sh`.
  **Закрыто 2026-10-11: закреплены все 8.** `minio` вендорить было неоткуда — `quay.io/minio/minio`
  отдаёт 401 на любой тег, в Docker Hub репозитория нет; `docker save` с прода не понадобился —
  на admin нашлась побайтово та же копия (совпали `.Id` = `sha256:14cea493…` и `Created`).
  Пин проставлен дайджестом **нашего registry** (`sha256:a1a8bd4a…`, платформенный манифест),
  а не quay-дайджестом `sha256:14cea493…` (индекс multi-arch) — по индексу `docker pull` получил бы 404.
  Оба дайджеста и объяснение записаны в `images.lock` § «История». `registry-vendor.sh --verify` →
  **OK=8 FAIL=0 SKIP=0**. `image:` прода переключается на пин в T018–T022 (owner-гейт) — `deploy/do.sh`, `deploy/karaoke-*/Dockerfile*`
- [X] T033 [P] [US3] Переключить сторонние образы в compose-файлах admin на `sm-karaoke.ru:5443/mirror/…@sha256:…`: `searxng/searxng`, `luuul/4get` (`deploy/docker-compose.yml`, `deploy/docker-compose-app.yml`), `ollama/ollama` (`deploy/docker-compose-ollama.yml`), `hwdsl2/whisper-server:cuda` (`deploy/docker-compose-whisper.yml`), `ghcr.io/xtls/xray-core` (`deploy/docker-compose-telegram-proxy.yml`) — `deploy/docker-compose*.yml` (FR-008 — фиксированные версии; FR-018 — служебный namespace с сохранением пути)
- [X] T034 [US3] Переключить базовые образы прода на вендоренные пины: `postgres:16` → `<registry>/mirror/library/postgres:<пин>@sha256:…` (`deploy/prod-single-host/docker-compose-database.yml`), `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` → `<registry>/mirror/minio/minio:…@sha256:…` (`deploy/prod-single-host/docker-compose-storage.yml`); **в пути прода не должно остаться ни одного внешнего имени** (FR-007, инвариант I-1) — `deploy/prod-single-host/docker-compose-{database,storage}.yml`
- [X] T035 [US3] Убрать плавающий тег `latest` у своего образа: в `do_build_demucs` и `do_push_demucs` тег `${DOCKER_REGISTRY}/demucs:latest` → `${DOCKER_REGISTRY}/demucs:${BUILD_VERSION}` (FR-008 — плавающие метки недопустимы) — `deploy/do.sh`
- [X] T036 [US3] Валидация истории: прогон Q5 и Q6 из `quickstart.md` — каждый `FROM` боевого Dockerfile начинается с нашего registry, каждое `image:` в путях прода и сборки либо `${DOCKER_REGISTRY}/karaoke/…`, либо `sm-karaoke.ru:5443/mirror/…@sha256:…`; `bash tools/registry-vendor.sh --verify` зелёный — `quickstart.md`

**Checkpoint**: SC-008 (доля внешних реестров в пути доставки — ноль), SC-009 (guard
ловит подставной тег и после смены имён), FR-008/FR-018 (пины и сохранение
исходного пути).

---

## Phase 6: User Story 4 — Registry обслуживается без сюрпризов (P4)

**Goal**: известно, что делать при потере registry, и владелец заранее знает срок
TLS-сертификата, от которого зависит доступность.

**Independent Test**: [quickstart.md](./quickstart.md) Q8 (репетиция восстановления
по документу), Q9 (напоминание о сертификате срабатывает), Q1/Q7 (retention делает
ровно то, что заявлено).

- [X] T037 [US4] Проверить retention на стенде: остановить контейнер, прогнать `zot verify-feature retention <config>` с `storage.retention.dryRun: true` и примонтированным каталогом данных (на **живом** проде эта команда требует остановки сервера — «the zot server must be stopped before running this command»), убедиться, что тег прод-образа и все теги `mirror/**` в списке на удаление отсутствуют (FR-010, инвариант I-4) — сценарии Q1 и Q7, `deploy/prod-single-host/registry/config.json`
- [X] T038 [P] [US4] Создать `tools/check-cert-expiry.sh`: проверка `notAfter` для `sm-karaoke.ru:443` **и** `sm-karaoke.ru:5443` (у registry свой путь трафика), порог по умолчанию 45 суток, `--warn-days N`, exit 1 при превышении порога (FR-016, SC-007) — `tools/check-cert-expiry.sh`
- [X] T039 [US4] Дописать в `RUNBOOK.md` раздел про сертификат: три даты (окно ручного продления у reg.ru, `notAfter = 2027-04-03T10:34:27Z` = 13:34 МСК, «продление — ручной `scp` + `systemctl reload nginx`, автоматики нет»), инструкцию установки суточного таймера с `check-cert-expiry.sh` на **admin** (канал `announce` — TTS + `notify-send`), и предупреждение, что при просрочке nginx продолжит работать, а docker валит handshake, то есть **встанет деплой** — `deploy/prod-single-host/registry/RUNBOOK.md`
- [X] T040 [US4] Проверка канала напоминания (SC-007) — **намеренно**: `bash tools/check-cert-expiry.sh sm-karaoke.ru:443 sm-karaoke.ru:5443 --warn-days 400` → exit 1 и видимое/слышимое предупреждение; при штатном пороге 45 → exit 0 — сценарий Q9, `tools/check-cert-expiry.sh`
- [X] T041 [US4] Написать в `RUNBOOK.md` процедуру восстановления (FR-011): пересоздать контейнер из `docker-compose-registry.yml` + `config.json` + `htpasswd`; восстановить свои образы `do.sh push_*`; восстановить сторонние `tools/registry-vendor.sh` по `images.lock`; принять `tools/registry-smoke.sh`; отметить риск, что после закрытия окна dual-push единственная копия — в registry; **отдельным абзацем — edge case «registry недоступен в момент пересоздания контейнера на проде»**: работающие контейнеры не падают, пересоздание не выполняется, повтор — после восстановления registry — `deploy/prod-single-host/registry/RUNBOOK.md`
- [X] T042 [US4] Репетиция восстановления на стенде (SC-005): удалить каталог данных, поднять контейнер заново **строго по RUNBOOK**, восстановить образы, `registry-smoke.sh` → PASS; зафиксировать фактическое время и уложиться в целевое «не более одного рабочего дня» — сценарий Q8, `deploy/prod-single-host/registry/RUNBOOK.md`

**Checkpoint**: US4 закрывает эксплуатацию, не блокируя US1–US3; процедура
восстановления проверена практикой, а не описана.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: SSoT-документация (FR-013), per-feature документ (R-32/FR-009),
отчётность по issue #286. Всё — в том же PR, что и правки кода.

- [X] T043 [P] Создать `knowledge/system/infra/registry.md` — компонента «приватный registry»: раскладка, модель доступа (namespace = проект, один кред, отзыв строкой), retention, процедура восстановления, зависимость от срока сертификата; форма — как у `deploy-overview.md` (title, `> **Домен**`, `> **Компонента**`, секции, Changelog); термин — **контейнерный образ**, не «образ» (коллизия с ADR `local-0003-shared-minio-image-cache.md`) — `knowledge/system/infra/registry.md` (FR-013)
- [X] T044 [P] Обновить `knowledge/system/infra/deploy-overview.md` (registry в раскладке `deploy/`: `docker-compose-registry.yml`, `registry/`, том `/sm-karaoke/system/registry`, порт 5443 — сегодня registry там не упомянут вовсе), `knowledge/system/infra/do-sh.md` (логин host/user/`--password-stdin`, `DUAL_PUSH`, команды `push_*`/`pull_*`) и `knowledge/system/README.md` (listing `infra/`: 15 → 16 документов); в текстах — термин **контейнерный образ** — FR-013
- [X] T045 [P] Создать `docs/features/private-docker-registry.md` (R-32/FR-009, Constitution §VI): шесть обязательных секций — «Что делает», «Зачем», «Как работает», «Инварианты», «Известные ловушки», «Ссылки»; статус `active`; слаг = имя файла; в «Известные ловушки» — ловушка `onDemand` без `content`, запрет записи в `library/**`, отсутствие модуля `crypt` на проде, требование остановки сервера для `verify-feature retention`; термин — **контейнерный образ**; добавить строку в `docs/features/README.md` — `docs/features/private-docker-registry.md`
- [X] T046 Прогон документационных гейтов (FR-013, R-32): `python3 tools/lint-knowledge.py`, `bash tools/check-knowledge-structure.sh` (ожидание 9/9), `bash tools/check-feature-doc.sh docs/features/private-docker-registry.md` (exit 0) — сценарий Q11, `quickstart.md`
- [X] T047 Финальный сквозной прогон `quickstart.md` (Q1–Q12) и проверка гигиены: `bash tools/check-docker-image-tags.sh` (+ `--selftest`), `bash -n` по изменённым `*.sh`, `git ls-files | grep -iE 'htpasswd$|\.env$|do\.env$'` → пусто, `git status` без неожиданных файлов (FR-013, R-32/FR-009) — `quickstart.md`
- [X] T048 Git-гейт (Tier-1, AGENTS.md § CI-gate для master): `git push -u origin 540-private-docker-registry`, `gh pr create --base master`, `gh pr checks` → **7/7 PASS** (включая `tools/check-docker-image-tags.sh --quiet` из `.github/workflows/lint.yml`); merge — только после зелёного CI и слова владельца, без `--delete-branch` — `.github/workflows/lint.yml`
- [ ] T049 (report.md готов; шаги трекера — после merge) Оформить `specs/540-private-docker-registry/report.md` (REQUIRED артефакт) и выполнить шаги OpenProject-workflow после merge: `bash tools/tracker.sh add-comment 286 --file specs/540-private-docker-registry/report.md`, затем `bash tools/tracker.sh mark-review 286` — `specs/540-private-docker-registry/report.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей, стартует сразу.
- **Foundational (Phase 2)**: зависит от Setup — **блокирует все истории**.
- **US1 (Phase 3)**: зависит от Foundational. Без него нет ни публикации, ни прод-деплоя.
- **US2 (Phase 4)**: зависит от Foundational (нужен работающий registry и контракт
  логина); независим от US1 по существу — доступ можно настраивать параллельно.
- **US3 (Phase 5)**: зависит от Foundational и от развёрнутого registry (T018):
  вендоринг пушит в наш registry, а `FROM` указывает на него.
- **US4 (Phase 6)**: зависит от Setup (конфиг с retention) и Foundational (smoke);
  от US1/US3 не зависит, кроме факта наполнения registry для репетиции (T041/T042
  используют то, что залито в T030).
- **Polish (Phase 7)**: после всех историй, в том же PR.

### User Story Dependencies

- **US1 (P1)** — MVP. Независима, но её результат (работающий registry с
  push/pull) — предпосылка US3.
- **US2 (P2)** — независима; проверяется своей матрицей авторизации.
- **US3 (P3)** — зависит от US1 (нужен registry, куда вендорить и откуда тянуть).
- **US4 (P4)** — независима по проверкам, но репетиция восстановления осмысленна
  после того, как есть что восстанавливать (US3).

### Внутри историй

- **T028 (guard) → T029 (смена `FROM`) → T031 (намеренная поломка) → T036 (валидация)**:
  поломка проверяется именно на новых registry-квалифицированных именах, поэтому идёт
  после смены `FROM`: FR-014 следует за FR-017, а не наоборот.
- **T032 (пересборка)** — после T029 (новые базы) и T030 (вендоренный
  `nvidia/cuda` для `DockerfileDemucs`); требует **уже развёрнутого** registry
  (T018, T019), поэтому раньше прод-этапа выполнена быть не может — предусловие
  названо в тексте задачи явно.
- T033/T034 (compose) — после T030 (нужны дайджесты для пинов).
- T042 (репетиция) — после T041 (процедура написана) и T030 (есть что заливать).

### Ключевые зависимости между задачами

- T004, T005 → T014 (стенду нужны compose и конфиг).
- T009, T010 (логин) → T019, T020, T030 (любой push/pull).
- T012 (smoke) → T014, T026, T042.
- T013 (vendor) → T030, T032, T042.
- T018 (прод-развёртывание) → T020, T021, T030, T032.
- T028 (guard) → T029 (смена `FROM`) → T031 (намеренная поломка) → T036.

### Прод-гейт (по слову владельца)

Задачи **T018, T019, T020, T021, T022 и T024** меняют прод-хост и выполняются только
по отдельному согласию на каждое действие (Tier-1 Machine-Specific). До них вся работа
идёт на admin и в репозитории.

Отдельно **T032** (пересборка на новых базах): прод она не меняет — сборка идёт
на admin, — но требует **уже развёрнутого** registry (T018, T019) и потому не может
выполняться раньше. Это предусловие названо в тексте задачи явно, а не подразумевается.

### Соответствие плану

| Фаза задач | Этап плана |
|---|---|
| Phase 1 Setup | Этап A, пп. 1–9 |
| Phase 2 Foundational | Этап A пп. 6–8 + Этап B пп. 11–12 |
| Phase 3 US1 | Этап B п. 12 + Этапы D/E (пп. 20–25) |
| Phase 4 US2 | Этап A п. 2 + RUNBOOK (пп. 2, 9, 27) |
| Phase 5 US3 | Этап A п. 7 + Этап C (пп. 14–19), включая задачу пересборки (п. 15) |
| Phase 6 US4 | Этапы A/G + Этап B п. 13 + quickstart Q1/Q7/Q8/Q9 |
| Phase 7 Polish | Этапы F и G (пп. 26–29) |

**Осознанный перенос (находка I2 из `analyze`).** Репетиция восстановления стоит в
плане в Этапе B (п. 13, «до прода»), а в задачах — в US4 (T042). Перенос намеренный: к
моменту репетиции должно быть **что** восстанавливать, то есть вендоринг из T030
(Этап C). План и задачи не расходятся по существу — меняется только место проверки.

**Расхождение «шесть ↔ восемь» снято в контракте.** Assumptions спеки приведены словом
владельца к **восьми** образам (2026-10-11); FR/SC не менялись. Нумерация пунктов плана
в таблице — после вставки задачи пересборки в Этап C.

---

## Parallel Opportunities

- **Setup**: T002, T003, T005, T006, T007 — разные файлы, независимы.
- **Foundational**: T010, T011, T012, T013 — разные файлы; T014 зависит от T012.
- **US3**: T029 (Dockerfile'ы) и T033 (compose admin) — разные файлы; T034 трогает
  прод-compose, отдельный файл. T032 (пересборка) параллельной быть не может: она
  запускает сборки, а сборки на одном демоне сериализуются.
- **Polish**: T043, T044, T045 — разные каталоги документации.

```bash
# Пример: Setup-параллель (разные файлы, без зависимостей)
Task: "T002 htpasswd.example + .gitignore"
Task: "T003 images.lock — заготовки восьми пинов"
Task: "T005 серверный блок nginx 5443to5000"
Task: "T006 registry/README.md"
Task: "T007 каркас registry/RUNBOOK.md"

# Пример: US3-параллель
Task: "T029 FROM → наш registry в четырёх Dockerfile + DockerfileDemucs"
Task: "T033 compose admin: сторонние образы → mirror/**"
```

**Параллелизм в этой спеке ограничен осознанно**: один долгоживущий субагент, одна
ветка `540-private-docker-registry`; параллельные `docker`-операции на одном
Docker-демоне конфликтуют (Pass 379, принцип IX.3) — задачи с `[P]` означают
«независимые файлы», а не «одновременный запуск сборок».

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup — артефакты registry в репо.
2. Phase 2: Foundational — контракт логина, инструменты, стенд-репетиция.
3. Phase 3: US1 — push/pull через прокси, развёртывание на проде (по слову
   владельца), окно dual-push, закрытие окна.
4. **STOP и VALIDATE**: Q2, Q5, Q10, Q12.
5. После этого деплой перестаёт зависеть от Docker Hub — цель работы достигнута.

### Incremental Delivery

1. Setup + Foundational → фундамент (никому не видно, проверяется стендом).
2. US1 → прод перестаёт зависеть от Hub (MVP).
3. US2 → доступ выдаётся и отзывается одной процедурой (≤1 мин, без остановки registry).
4. US3 → сборка и прод берут базовые/сторонние образы изнутри.
5. US4 → эксплуатация: retention, восстановление, напоминание о сертификате.
6. Polish → SSoT, per-feature документ, отчёт по #286.

### Порядок внутри US1

Код (`deploy/do.sh`, `deploy_*.sh`) → проверка на стенде (T017) → прод (T018–T021) →
закрытие окна (T022) → валидация (T023). Прод-шаги не начинать, пока T017 не зелёный.

---

## Notes

- `[P]` = разные файлы, нет зависимости от незавершённых задач.
- Каждая user story заканчивается проверяемым инкрементом (сценарии `quickstart.md`).
- Коммит — только по слову владельца; один PR на спеку (ветка
  `540-private-docker-registry`), поэтому per-feature документ и знание обновляются в
  том же PR (R-32).
- `docker restart` запрещён; контейнеры — только через `deploy/do.sh`.
- Секреты (`do.env`, `htpasswd`) в git не попадают: `git ls-files | grep -iE
  'htpasswd$|\.env$|do\.env$'` → пусто.
- Не трогать: `deploy/web-server-deploy/**` (deprecated),
  `deploy/karaoke-app/Dockerfile{Backup,Backup2,Old,Full}` (исключены guard'ом),
  ADR `knowledge/adr/local-0003-shared-minio-image-cache.md` (там «образ» = медиа).
- Спека — контракт: если по ходу выяснится необходимость правки FR/SC (например,
  оставить MinIO на quay.io) — это вопрос владельцу, а не задача implement. Расхождение
  «шесть ↔ восемь» образов уже снято в Assumptions спеки словом владельца (2026-10-11).
- **T048 (Git-гейт) и T050 (закрытие issue)** — не «работа над фичей»: первый требует
  зелёного CI 7/7 и слова владельца на merge, второй выполняется после ревью владельца
  и **вне** implement-цепочки.

---

## После implement (вне цепочки — по ревью владельца)

- [ ] T050 Закрыть issue #286: `bash tools/tracker.sh close-issue 286` — шаг 6 workflow из
  `spec.md` § OpenProject Tracking; выполняется **после** приёмки владельцем, а не
  исполнителем — `specs/540-private-docker-registry/report.md`

# Research: софт для собственного приватного Docker-registry (проект Karaoke)

> **Дата исследования**: 2026-10-11.
> **Автор**: research-сессия (субагент), по заданию родительского агента.
> **Артефакт**: разовый research-документ в `.scratch/karaoke/registry/` (не SSoT, в `knowledge/` не переносится без отдельного решения).
> **Статус фактов**: всё, что помечено «измерено», измерено 2026-10-11 через публичные registry-API; всё, что помечено «на диске», прочитано из репозитория `/home/nsa/Karaoke`.
> **Ничего не устанавливалось и не запускалось**: только чтение репозитория, HTTP-запросы к публичным registry-API и чтение документации.

---

## Вопрос

Нужен **свой приватный Docker-registry** на своём сервере для проекта Karaoke, вместо Docker Hub (`svoemestodev`).

Требуется решить:

1. **Какой софт** поставить (CNCF `distribution`/`registry:2|3`, Zot, Harbor, GitLab CE, Nexus OSS, Artifactory OSS, Gitea/Forgejo) — с учётом того, что на прод-хосте **всего ~3.8 ГБ RAM**, и там уже работают `karaoke-web` (heap 1200m), `karaoke-public`, `karaoke-storage` (MinIO), `karaoke-db` (Postgres), `karaoke-minio-proxy` и host-nginx.
2. **Pull-through cache**: чтобы базовые образы (`postgres:16`, `nginx:stable`, `node:22-alpine`, `ubuntu:22.04`, `eclipse-temurin:22-jre-jammy`, `quay.io/minio/minio`, `ghcr.io/xtls/xray-core`, `searxng/searxng`, `luuul/4get`) тоже не тянулись напрямую с Docker Hub — и чтобы это работало для namespace `library/*`.
3. **Auth и TLS** за уже существующим host-nginx, **без** `insecure-registries`.
4. **Эксплуатацию**: GC, retention, бэкап, обновление, мониторинг, реальный объём на диске под 4 образа с историей версий.
5. **Рабочий поток push (admin-машина `nsa-i9`) / pull (прод)**, и что придётся править в `deploy/do.sh` / `deploy/do.env`.
6. **Безопасность**: анонимный pull или закрытый registry, где хранить креды.

---

## Метод

### Что прочитано на диске (репозиторий `/home/nsa/Karaoke`)

| Файл | Что взято |
|---|---|
| `deploy/do.sh` | функции `do_build_*`, `do_push*`, `do_pull*`, `do_rmi`, `do_ps`; загрузка секретов `set -a; source do.env; set +a` (строки 50–67); `DOCKER_CONFIG`, `GRADLE_USER_HOME` |
| `deploy/do.env` | переменные `DOCKER_REGISTRY=svoemestodev`, `DOCKER_PASSWORD` (значения не читались) |
| `deploy/*.yml`, `deploy/prod-single-host/*.yml` | все `image:` (полный инвентарь ниже) |
| `deploy/*/Dockerfile` | все `FROM` |
| `deploy/prod-single-host/{README.md,nginx.conf,80to8897,.env.example,do.env.example,do.sh}` | раскладка прода, host-nginx (`stream{}` + `http{}`), vhost `sm-karaoke.ru`, лимит `client_max_body_size 20M`, «3.8 ГБ RAM нового хоста» |
| `.gitignore` | `deploy/do.env` и `deploy/.env` исключены из git (строки 31–41) |
| `grep -rn -i 'registry' knowledge/ docs/` | в `knowledge/` **нет** ни одного документа про container registry (совпадения — только `MonitorRegistry`, `SyncRegistry` и т.п.) |

### Первоисточники (документация и код проектов)

- CNCF Distribution: [Configuration](https://distribution.github.io/distribution/about/configuration/), [Deploy a registry server](https://distribution.github.io/distribution/about/deploying/), [Registry as a pull through cache](https://distribution.github.io/distribution/recipes/mirror/), [Garbage collection](https://distribution.github.io/distribution/about/garbage-collection/), [Authenticate proxy with nginx](https://distribution.github.io/distribution/recipes/nginx/), [Test an insecure registry](https://distribution.github.io/distribution/about/insecure/), [config-example.yml](https://github.com/distribution/distribution/blob/main/cmd/registry/config-example.yml), релиз [v3.1.2](https://github.com/distribution/distribution/releases/tag/v3.1.2).
- Zot: [Configuring zot](https://zotregistry.dev/v2.1.22/admin-guide/admin-configuration/), [Mirroring](https://zotregistry.dev/v2.1.22/articles/mirroring/), [Retention Policies](https://zotregistry.dev/v2.1.22/articles/retention/), [Authentication and Authorization](https://zotregistry.dev/v2.1.22/articles/authn-authz/), [Using Docker with zot](https://zotregistry.dev/v2.1.22/articles/docker/), исходники `pkg/api/config/config.go` (tag `v2.1.22`), релизы через GitHub API.
- Harbor: [Installation Prerequisites](https://goharbor.io/docs/2.13.0/install-config/installation-prereqs/), [Configure Proxy Cache](https://goharbor.io/docs/2.13.0/administration/configure-proxy-cache/).
- GitLab: [installation requirements](https://docs.gitlab.com/install/requirements/).
- Sonatype Nexus: [System Requirements](https://help.sonatype.com/en/sonatype-nexus-repository-system-requirements.html).
- Gitea: [Container Registry](https://docs.gitea.com/usage/packages/container).
- Docker Engine: [docker login](https://docs.docker.com/reference/cli/docker/login/).
- Certbot: [User Guide](https://eff-certbot.readthedocs.io/en/stable/using.html).

### Измерения (2026-10-11)

Размеры образов измерены напрямую через registry HTTP API (`/v2/<name>/manifests/<ref>` с анонимным токеном `auth.docker.io` / `ghcr.io`): суммированы размеры **сжатых** слоёв + config-блоб для `linux/amd64`. Это ровно то, что registry хранит у себя на диске.

### Границы метода

- `web_search` в этой сессии **недоступен** (провайдер без API-ключа) — работал только прямой `web_fetch`/`curl` по известным URL. Поиск «с нуля» не выполнялся.
- Сайт Sonatype отдаёт страницу с гигантской навигацией; содержимое удалось извлечь только скриптом (см. цитаты ниже).
- JFrog: `jfrog.com/help/...system-requirements` → 404 (`docs.jfrog.com/administration/docs/system-requirements`), `jfrog.com/community/open-source/` не отдался за 25 с. **Требования Artifactory в этой сессии не подтверждены — «не найдено».**
- Уровень зрелости Zot в CNCF: страница [cncf.io/projects/zot](https://www.cncf.io/projects/zot/) существует, но рендерится JS, и конкретный уровень (Sandbox/Incubating) из HTML **не подтверждён — «не найдено»**.
- Docker daemon и контейнеры не трогались; `deploy/` и `knowledge/` не изменялись.

---

## Факты

### 1. Инвентарь: что вообще должно лежать в registry

**Свои образы** (собираются на admin-машине, поставляются на прод):

| Образ | Размер (по заданию) | Где нужен |
|---|---|---|
| `karaoke-app` | ~5.4 ГБ | dev + прод (админка/инки) |
| `karaoke-web` | ~1.0 ГБ | прод |
| `karaoke-public` | ~242 МБ | прод |
| `karaoke-webvue3` | ~245 МБ | dev/admin |
| `demucs` (собирается `do_build_demucs` из `deploy/karaoke-app/DockerfileDemucs`, тег `${DOCKER_REGISTRY}/demucs:latest`) | измерено: **4787.6 МБ** сжатых слоёв | admin (GPU) |
| **Итого свои** | **≈ 6.9 ГБ** (без demucs) / ≈ 11.7 ГБ (с demucs) | |

**Базовые образы** (полный список собран с диска: `grep -rn 'image:' deploy/*.yml deploy/prod-single-host/*.yml` + `grep -n '^FROM' deploy/*/Dockerfile`):

| Образ | Где используется | Реестр | Сжатый размер amd64 (измерено) |
|---|---|---|---|
| `postgres:16` | `docker-compose-database.yml` | docker.io/library | **160.3 МБ** |
| `postgres:13-alpine` | `deploy/tracker-docker-compose.yml` | docker.io/library | **106.8 МБ** |
| `nginx:stable` | `Dockerfile` karaoke-public, karaoke-webvue3; `docker-compose-web.yml:53` | docker.io/library | **63.6 МБ** |
| `node:22-alpine` | `Dockerfile` karaoke-public, karaoke-webvue3 (build-stage) | docker.io/library | **60.7 МБ** |
| `eclipse-temurin:22-jre-jammy` | `Dockerfile` karaoke-web | docker.io/library | **94.9 МБ** |
| `ubuntu:22.04` | `Dockerfile` karaoke-app (runner) | docker.io/library | **29.8 МБ** |
| `openjdk:17-ea-jdk-slim` | `Dockerfile` karaoke-app (builder) | docker.io | 401 Unauthorized (не измерено) |
| `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | `prod-single-host/docker-compose-storage.yml` | **quay.io** | не измерено (401) |
| `minio/minio:latest` | `deploy/docker-compose-storage.yml` | docker.io | 401 (в `prod-single-host/README.md` зафиксировано: «Hub 404») |
| `openproject/community:13` | `deploy/tracker-docker-compose.yml` | docker.io | **725.4 МБ** |
| `searxng/searxng:latest` | `docker-compose-app.yml:71`, `docker-compose.yml:59` | docker.io | **98.3 МБ** |
| `luuul/4get:1.0.44` | `docker-compose-app.yml:88`, `docker-compose.yml:80` | docker.io | **45.7 МБ** |
| `ollama/ollama:latest` | `docker-compose-ollama.yml` | docker.io | **3779.4 МБ** |
| `ghcr.io/xtls/xray-core:latest` | `docker-compose-telegram-proxy.yml` | **ghcr.io** | **22.5 МБ** |
| `hwdsl2/whisper-server:cuda` | `docker-compose-whisper.yml:14,70` | docker.io | **3326.5 МБ** |
| `svoemestodev/demucs:latest` | подтягивается | docker.io (user) | **4787.6 МБ** |

> **Вывод по инвентарю**: тяжёлые образы (`ollama` 3.78 ГБ, `whisper-server:cuda` 3.33 ГБ, `demucs` 4.79 ГБ, `openproject/community` 0.73 ГБ) нужны **admin-машине / другим проектам**, а не прод-хосту `sm-karaoke.ru`. На проде из внешних registry реально нужны: `postgres:16`, `nginx:stable` и `quay.io/minio/minio` — суммарно **≈ 0.25 ГБ** (без MinIO). Это важно для выбора: pull-through cache на проде почти ничего не весит, но и большой пользы не даёт.

### 2. Ограничение хоста

- Прод-хост `188.127.240.124` = `sm-karaoke.ru`: Ubuntu 26.04, 6 vCPU, **3.8 ГБ RAM**, 985 ГБ диска (`deploy/prod-single-host/README.md`).
- Heap `karaoke-web` на этом хосте уже ужат до `-Xmx1200m` именно из-за 3.8 ГБ (`deploy/prod-single-host/.env.example`, комментарий «Heap karaoke-web: 3.8 ГБ RAM нового хоста (wayfinder #170). НЕ 8g … НЕ 1048m»).
- Значит, бюджет на новый сервис — **единицы сотен МБ RSS**, а не гигабайты.

### 3. Сравнительная таблица кандидатов

| Кандидат | Мин. RAM (первоисточник) | Образ(ы) на диске | Pull-through `docker.io` | Pull-through `ghcr.io`/`quay.io` | Auth | TLS | GC / retention | Лицензия | Сложность (1–10) | Влезает в 3.8 ГБ рядом с текущими |
|---|---|---|---|---|---|---|---|---|---|---|
| **`distribution` `registry:2/3`** | **не найдено** (в docs требований нет) | `registry:3` = **20.5 МБ** (измерено); один Go-процесс | Да, документировано: `proxy.remoteurl: https://registry-1.docker.io` + `registry-mirrors` на клиенте | **Нет**: «It's currently possible to mirror only one upstream registry at a time», демон «supports only mirrors of Docker Hub» ([mirror](https://distribution.github.io/distribution/recipes/mirror/)) | `htpasswd` (**только bcrypt**) или token; встроенного UI нет | Нативно TLS/Let's Encrypt, либо терминирование на nginx | `registry garbage-collect [--delete-untagged]` — **stop-the-world**, требует остановки/read-only; встроенного retention **нет**; для удаления нужно `storage.delete.enabled: true` | Apache-2.0 ([репозиторий](https://github.com/distribution/distribution)) | **3** | **Да** (десятки МБ) |
| **Zot** | **не найдено** (в docs Zot требований к RAM/CPU нет; замеров проект не публикует) | `ghcr.io/project-zot/zot:v2.1.22` = **75.6 МБ** (измерено); один статический Go-бинарник, `Entrypoint=/usr/local/bin/zot-linux-amd64`, `Cmd=serve /etc/zot/config.json` | Да, документировано отдельным разделом «Using zot as a Docker pull-through cache» + `registry-mirrors` ([docker guide](https://zotregistry.dev/v2.1.22/articles/docker/)) | **Да, несколько upstream одновременно** в одном процессе (`extensions.sync.registries[]`), `onDemand: true` ([mirroring](https://zotregistry.dev/v2.1.22/articles/mirroring/)) | `htpasswd` (bcrypt, с hot-reload файла), LDAP, Bearer/OIDC, mTLS, API-keys; анонимные политики на уровне репозитория | Нативно TLS или терминирование на nginx | `gc` **inline** (без остановки), `storage.retention.policies` — keepTags по regex/count/времени, `dryRun`, `zot verify-feature retention` | Apache-2.0 ([repo](https://github.com/project-zot/zot), `license: Apache-2.0`) | **4** | **Да** (десятки–сотни МБ, точный замер — открытый вопрос) |
| Harbor | **4 ГБ RAM, 2 CPU, 40 ГБ диска — минимум**; рекомендация 8 ГБ/4 CPU ([prereqs](https://goharbor.io/docs/2.13.0/install-config/installation-prereqs/)) | ~11 контейнеров; измерено 10 из них = **678.5 МБ** сжатых | Да, но **один registry endpoint на проект** (proxy cache project); upstream'ы: Docker Hub, quay, GHCR, ECR, ACR, GCR, Artifactory ([proxy cache](https://goharbor.io/docs/2.13.0/administration/configure-proxy-cache/)) | Да — **отдельным proxy-проектом на каждый upstream** | Полноценная (local DB, LDAP, OIDC, robot accounts), UI | Нативно | GC + tag retention (для proxy-проектов **дефолт 7 дней**) | Apache-2.0 | **7** | **Нет** — минимум 4 ГБ при 3.8 ГБ на весь хост |
| GitLab CE (встроенный registry) | **16 ГБ RAM / 8 vCPU — baseline**; «в constrained-окружении — минимум 8 ГБ» ([requirements](https://docs.gitlab.com/install/requirements/)) | Omnibus-пакет + PostgreSQL + Redis + Gitaly + registry | **не найдено** (в docs registry зеркалирование docker.io не описано) | не найдено | Полная (GitLab-аккаунты) | Требуется домен + TLS | Встроенный cleanup, но живёт внутри GitLab | MIT (CE) | **9** | **Нет** (8 ГБ минимум) |
| Nexus Repository OSS | XS-профиль: **1 узел, 4 vCPU, 16 GiB RAM**; «allocate up to two-thirds of available RAM to Nexus Repository»; Java 21 требуется; H2 (≤200k запросов/сутки, **контейнерные деплои с H2 не поддерживаются**) или внешний PostgreSQL ([system requirements](https://help.sonatype.com/en/sonatype-nexus-repository-system-requirements.html)) | Java/Tomcat + PostgreSQL + blob store | Да, репозиторий типа `docker (proxy)` | Через отдельные proxy-репозитории | Realms, анонимный доступ, токены | Нативно/через прокси | Cleanup policies | EPL-1.0 (Community Edition) | **8** | **Нет** (16 GiB минимум) |
| Artifactory OSS | **не найдено** — официальная страница требований по документированным URL отдаёт 404 | — | — | — | — | — | — | — | — | **Не проверено** |
| Gitea/Forgejo (package registry) | **не найдено** (в docs требований к RAM нет; проект позиционируется как лёгкий Go-сервис) | один Go-бинарник | **не найдено**: раздел Container Registry в docs описывает только login/push/pull, **никакого проксирования внешних registry не заявлено** ([docs](https://docs.gitea.com/usage/packages/container)) | не найдено | Basic/token, PAT при 2FA | Через прокси или нативно | Нет tag-retention как у Zot | MIT | **5** | Вероятно да, но **нет pull-through** → задача №2 не решается |
| **Вариант «registry как pull-through cache»** (`registry:3` + `proxy.remoteurl`) | то же, что distribution | 20.5 МБ | Да, документировано | Нет (только docker.io) | htpasswd | Нативно/через nginx | Только `--delete-untagged` + TTL-очистка (`proxy.ttl`) | Apache-2.0 | **3** | **Да** |

Пояснения к таблице:

- «Сложность» — экспертная сводная оценка объёма работ по развёртыванию, обновлению, бэкапу и отладке (1 — один контейнер и один конфиг, 10 — многосервисный стек с БД и внешними зависимостями). Это **оценка автора**, а не цитата.
- У `distribution` «минимальные требования к RAM» в docs действительно отсутствуют: страница [Deploy a registry server](https://distribution.github.io/distribution/about/deploying/) описывает только Docker/TLS/auth/storage, никаких цифр CPU/RAM. То же у Zot: страницы [admin-configuration](https://zotregistry.dev/v2.1.22/admin-guide/admin-configuration/) и статьи не содержат требований к RAM.
- `registry:3` — актуальная ветка: последний релиз [v3.1.2 от 2026-09-24](https://github.com/distribution/distribution/releases/tag/v3.1.2), в т.ч. исправление CVE-2026-85747 в pull-through cache. Путь конфига в v3 — `/etc/distribution/config.yml` (в v2 был `/etc/docker/registry/config.yml`); это подтверждается `Cmd = ["/etc/distribution/config.yml"]` из манифеста образа (измерено).
- Образ `registry:3` по умолчанию имеет `OTEL_TRACES_EXPORTER=none` (измерено из config-блоба), т.е. мусорных попыток отправить трейсы на `localhost:4318` не будет — важно для слабого хоста.

### 4. Pull-through cache: детали, которые решают

**Distribution (`registry:2/3`)**

- Настройка: секция `proxy` в конфиге — `remoteurl: https://registry-1.docker.io` (+ опционально `username`/`password`, `ttl: 168h`).
- **Один upstream на инстанс**: «It's currently possible to mirror only one upstream registry at a time» ([mirror](https://distribution.github.io/distribution/recipes/mirror/)).
- **Демон**: `registry-mirrors` в `/etc/docker/daemon.json`; URL зеркала обязан быть корнем домена. «Currently Docker daemon supports only mirrors of Docker Hub. It is not possible to run the Docker daemon against a pull through cache with another upstream registry» (там же).
- **Про `library/*`**: официальные образы Docker Hub на уровне registry-API адресуются как `library/<name>`. Проверено эмпирически 2026-10-11: `auth.docker.io` со scope `repository:library/postgres:pull` → 200, со scope `repository:postgres:pull` → **401**. Значит, при `proxy.remoteurl: https://registry-1.docker.io` зеркало обязано отдавать путь `/v2/library/postgres/manifests/16` — и `docker pull postgres:16` через `registry-mirrors` попадёт именно туда (демон сам нормализует `postgres` → `library/postgres`). То есть **`library/*` работает**, это штатный сценарий документированного зеркала.
- **Очистка**: «For the scheduler to clean up old entries, `delete` must be enabled in the registry configuration» — то есть `storage.delete.enabled: true`, иначе TTL-очистка кэша не работает ([mirror](https://distribution.github.io/distribution/recipes/mirror/)).
- **GC**: `registry garbage-collect [--dry-run] [--delete-untagged] [--quiet] /path/to/config.yml`; фазы mark/sweep; **«You should ensure that the registry is in read-only mode or not running at all»** — это stop-the-world GC ([garbage collection](https://distribution.github.io/distribution/about/garbage-collection/)). Удаление тегов делается через API (`DELETE /v2/<name>/manifests/<tag|digest>`), само по себе это лишь снимает ссылку; место освобождает только GC.

**Zot**

- **Несколько upstream одновременно** в одном процессе: `extensions.sync.registries[]`, у каждого `urls[]`, `onDemand`, `pollInterval`, `content[].prefix/destination/tags.regex`. В официальном примере «Multiple registries with on-demand mirroring» одновременно настроены `https://k8s.gcr.io` и `https://index.docker.io` ([mirroring](https://zotregistry.dev/v2.1.22/articles/mirroring/)).
- **Режимы**: `onDemand: true` без `pollInterval` и без `content` → «sync is enabled in on demand mode for any image» (таблица режимов там же). Именно это и нужно для pull-through.
- **Docker Hub**: «Because Docker Hub rate-limits pulls and does not support catalog listing, do not use polled mirroring with Docker Hub. Use only on-demand mirroring with Docker Hub».
- **Важно (v2.1.22)**: «sync preserves upstream media types and digests. To sync Docker-format images … enable `http.compat` as `["docker2s2"]`. Without this compatibility setting, zot rejects Docker schema 2 content». Для pull-through Docker Hub это **обязательно**.
- **Docker как клиент**: у Zot есть отдельный документированный сценарий [«Using Docker with zot»](https://zotregistry.dev/v2.1.22/articles/docker/): на классическом docker (не containerd-snapshotter) — `registry-mirrors` в `daemon.json` → `docker pull ubuntu:22.04` сначала спрашивает zot. Для upstream'ов **кроме** docker.io классический `registry-mirrors` не работает (это ограничение самого Docker, см. выше); варианты — containerd `hosts.toml` в `/etc/docker/certs.d/<registry>/` с `override_path = true`, либо явные префиксные pull'ы.
- **Retention**: `storage.retention.policies[]` (`repositories` glob, `keepTags` с `patterns`/`mostRecentlyPushedCount`/`mostRecentlyPulledCount`/`pushedWithin`/`pulledWithin`, `deleteUntagged`, `keepUntagged`), плюс `dryRun` и `zot verify-feature retention` для проверки без изменений ([retention](https://zotregistry.dev/v2.1.22/articles/retention/)).
- **GC inline**: «Garbage collection in zot is an inline feature meaning that it is not necessary to take the registry offline» ([admin-configuration](https://zotregistry.dev/v2.1.22/admin-guide/admin-configuration/)).

**Harbor**

- Proxy cache есть, но «a proxy cache project … connects to a target registry using a registry endpoint» — **один upstream на проект**; чтобы покрыть docker.io + ghcr.io + quay.io, нужно **три proxy-проекта**. Дефолтная retention для proxy-проекта — **7 дней** ([proxy cache](https://goharbor.io/docs/2.13.0/administration/configure-proxy-cache/)).

**Gitea/Nexus/Artifactory**: pull-through docker.io не заявлен (Gitea — точно нет по docs; Nexus/Artifactory — не проверялись детально, см. «не найдено»).

### 5. Аутентификация и TLS

- **Distribution**: нативный basic auth через `htpasswd`, но «The distribution registry **only** supports htpasswd credentials in bcrypt format, so if you omit the `-B` option … all authentication attempts will fail». Генерация: `docker run --rm --entrypoint htpasswd httpd:2 -Bbn user pass > auth/htpasswd`. Требует TLS: «You cannot use authentication with authentication schemes that send credentials as clear text» ([deploying](https://distribution.github.io/distribution/about/deploying/)). Есть также token-auth (нужен внешний token-сервер — избыточно).
- **Zot**: `htpasswd` (`http.auth.htpasswd.path` — ключ подтверждён исходником `pkg/api/config/config.go`: поле `HTPasswd AuthHTPasswd{Path string}`), bcrypt, файл перечитывается на лету (watcher в `pkg/api/htpasswd.go`); LDAP, Bearer/OIDC, mTLS; авторизация — `http.accessControl.repositories.<glob>.anonymousPolicy/policies` ([authn-authz](https://zotregistry.dev/v2.1.22/articles/authn-authz/)); пример анонимного read — `examples/config-anonymous-authz.json` в репозитории.
- **За существующим host-nginx**: официальный рецепт [«Authenticate proxy with nginx»](https://distribution.github.io/distribution/recipes/nginx/) прямо говорит: «requires that you move TLS termination from the Registry to the proxy itself», и даёт ключевые ручки:
  - `client_max_body_size 0;` — «disable any limits to avoid HTTP 413 for large image uploads»;
  - `chunked_transfer_encoding on;` — «required to avoid HTTP 411»;
  - `proxy_set_header Host $http_host;` — «required for docker client's sake»;
  - `proxy_read_timeout 900;`
  - и предупреждение: не выставлять registry на `localhost:5000` без аутентификации.
  Это **прямо релевантно нашему `nginx.conf`**: в `http{}` уже стоит `client_max_body_size 20M;`, что для образа 5.4 ГБ даст 413 — на уровне `server{}` для registry это надо переопределить нулём.
- **`insecure-registries` не нужен и вреден**: `distribution` docs: «It's not possible to use an insecure registry with basic authentication» ([insecure](https://distribution.github.io/distribution/about/insecure/)). С публичным CA (Let's Encrypt) и корректным `Host`-заголовком всё работает по HTTPS без правки `daemon.json` на клиентах.
- **DNS/TLS для поддомена**: `repository.sm-karaoke.ru` делегирован на `ns5/ns6.hosting.reg.ru`, A-запись ещё не заведена. Certbot умеет получать и **сам прописывать** сертификат в конфиг nginx: «To obtain a certificate and also install it, use the `certbot run` command … This is done by automatically modifying the configuration of your server in order to use any certificate» ([certbot](https://eff-certbot.readthedocs.io/en/stable/using.html)). Для HTTP-01 challenge нужен `location /.well-known/acme-challenge/` на 80-м порту.

### 6. Операционка: GC, retention, бэкап, обновление, мониторинг, диск

| Аспект | `distribution` | Zot |
|---|---|---|
| GC | CLI `registry garbage-collect`, **stop-the-world** | **inline**, конфигом `gc/gcDelay/gcInterval` |
| Retention (сколько версий держать) | **нет** — только `--delete-untagged` | **есть**: keepTags/keepUntagged по regex, count, времени; dry-run |
| Удалённый триггер | только API-вызовы + внешний cron | встроенный планировщик |
| Обновление | заменить образ (данные на volume) | заменить образ (данные на volume) |
| Мониторинг | metrics по `/debug/prometheus` (`http.debug.prometheus`) | `extensions.metrics` + опционально node-exporter |
| Бэкап | файловый/rsync каталога данных или `registry` + API-дамп | то же (файлы) |
| UI | нет | `extensions.ui` (опционально) |
| Размер образа | 20.5 МБ | 75.6 МБ |

**Оценка места под 4 своих образа с историей версий.**

- Один «версионный набор» (app + web + public + webvue3) по заданию: `5.4 + 1.0 + 0.242 + 0.245` ≈ **6.9 ГБ** — это размер *несжатых* образов, как их показывает `docker images`.
- Registry хранит то, что ему **залили при push**, то есть **сжатые** слои. Точной цифры для наших образов нет (замер возможен только после первого push) → округлённо ожидаем **3.5–6 ГБ** на первый набор; **точное число — открытый вопрос**.
- **Механика дедупликации**: registry адресует слои по content-addressable digest, и «as long as a layer is referenced by one manifest, it cannot be garbage collected» ([garbage collection](https://distribution.github.io/distribution/about/garbage-collection/)). Значит новая версия образа добавляет на диск **только изменившиеся слои**, а не все 5.4 ГБ заново. Для наших сборок (Spring Boot jar в `karaoke-web`, jar в `karaoke-app`) дельта между версиями — это в основном один большой слой-артефакт.
- Грубая вилка при дельте 20–50 % на версию:

| История | Свои образы в registry (сжато) |
|---|---|
| 1 версия | ≈ 3.5–6 ГБ |
| 5 версий | ≈ 7–14 ГБ |
| 10 версий | ≈ 10–21 ГБ |
| + pull-through cache базовых для прода (`postgres:16` 160 МБ, `nginx:stable` 64 МБ, MinIO — не измерено) | ≈ +0.25 ГБ; `ollama`/`whisper`/`demucs` в кэш прода **не попадут** (по требованию, on-demand) |

- Диск прод-хоста — **985 ГБ** (`deploy/prod-single-host/README.md`), так что место не является ограничением; ограничение — **RAM**. При этом `karaoke-docker-prune.timer` на проде чистит dangling-образы, а registry-данные лежат вне docker-конфайнмента (bind mount) и под эту чистку не попадают — retention придётся настраивать явно.

### 7. Рабочий поток push/pull и что придётся править

**Как сейчас (прочитано в `deploy/do.sh`)**

```bash
function do_push_app() {
  echo "Pushing APP"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  announce "П+ушинг апп"
}
```

То же в `do_push`, `do_push_demucs`, `do_push_web`, `do_push_webvue3`, `do_push_public`. `do_pull*` (`do_pull_app/web/webvue3`) **вообще не логинятся**, а просто делают `docker-compose pull`.

Что здесь важно:

1. `docker login --username svoemestodev --password ...` **без указания SERVER** → логин уходит в Docker Hub, а `--username` здесь — это имя namespace `svoemestodev`, а не реальный пользователь (совпадение с `DOCKER_REGISTRY` не случайно). Для своего registry это надо разделить: host registry, имя пользователя, пароль.
2. `--password` — небезопасная форма (пароль в argv, виден в `ps`); в doc Docker [docker login](https://docs.docker.com/reference/cli/docker/login/) описан `[SERVER]` и предпочтительный ввод пароля через stdin/credential store; заметим также, что «For Docker Hub, the docker login command uses a device code flow by default, unless the --username flag is specified».
3. **Имена образов** — ключевой момент. Docker считает первый компонент имени **хостом** registry только если в нём есть `.`/`:` или это `localhost`. Сейчас `svoemestodev/karaoke-app` — это namespace Docker Hub. После переезда префикс должен стать, например, `repository.sm-karaoke.ru/karaoke`, и тогда тег `repository.sm-karaoke.ru/karaoke/karaoke-app:123` корректно уйдёт в наш registry. **Ничего, кроме значения `DOCKER_REGISTRY`, для этого менять не нужно** — во всех compose-файлах уже используется `${DOCKER_REGISTRY}/karaoke-*:${BUILD_VERSION}` (проверено `grep`: `deploy/docker-compose{,-app,-web,-webvue3,-public}.yml`, `deploy/prod-single-host/docker-compose-{web,public}.yml`).
4. **Как переменная попадает в compose**: `do.sh` делает `set -a; … source ${DEPLOY_DIR}/do.env; set +a` (строки 50–67), то есть `DOCKER_REGISTRY` экспортируется в окружение, и `docker-compose` подставляет его сам. Значит достаточно поменять `DOCKER_REGISTRY` в `do.env` — на admin-машине это `~/Karaoke/deploy/do.env`, на проде `/root/Karaoke/deploy/do.env` (файлы **вне git**, `.gitignore:37`; шаблон — `deploy/prod-single-host/do.env.example`).
5. **Pull на проде**: `do_pull_web`/`do_pull_public` в `deploy/prod-single-host/do.sh` делают `docker login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}` и потом `docker pull` — эту строку нужно переписать так же, как push. Ещё вариант — вообще не логиниться на проде, если в registry разрешён анонимный read (см. §8).
6. `do_rmi` удаляет локальные образы по имени `${DOCKER_REGISTRY}/...` — после смены префикса старые теги (`svoemestodev/...`) останутся висеть локально; разовая уборка нужна.
7. `deploy/web-server-deploy/` (deprecated) содержит те же строки — трогать по README не нужно.

**Черновик правок `do.sh`** (файл в этом исследовании не менялся — только предложение):

```bash
# do.env (admin и прод) — было: DOCKER_REGISTRY=svoemestodev
REGISTRY_HOST=repository.sm-karaoke.ru
REGISTRY_USER=karaoke
REGISTRY_PASSWORD=CHANGE_ME
DOCKER_REGISTRY=${REGISTRY_HOST}/karaoke

# do.sh: вместо ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin <<<"${REGISTRY_PASSWORD}"
```

### 8. Безопасность

- **Credential storage**: `docker login` пишет креды в `$HOME/.docker/config.json`; если credential store не настроен, Docker хранит их **base64**, а не шифрует («If you don't configure a credential store, Docker stores credentials in the config.json file in a base64-encoded format. This method is less secure…») — [docker login](https://docs.docker.com/reference/cli/docker/login/). В проекте уже есть правило `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (Pass 373), т.е. конфиг с кредами может лежать в проектном каталоге — **этот каталог нельзя коммитить** (проверить `.gitignore` отдельно, сейчас там `deploy/do.env`, но не `.docker/`).
- **Секреты в git**: `.gitignore` содержит `/deploy/do.env` и `/deploy/.env` (строки 36–41), шаблоны — `*.example`. Исторический инцидент с утечкой зафиксирован в `AGENTS.md` (Pass 374) — новый `REGISTRY_PASSWORD` туда же, только в `do.env`.
- **Что будет, если registry станет публично доступен**: в `distribution` анонимного режима «только чтение» нет — auth либо включён для всего, либо выключен для всего; «A properly secured registry should return 401 when the /v2/ endpoint is hit without credentials» ([deploying](https://distribution.github.io/distribution/about/deploying/)). У **Zot** анонимный доступ описывается политикой per-repository (`anonymousPolicy: ["read"]`, см. пример `examples/config-anonymous-authz.json`), то есть можно разрешить анонимный pull для публичных репозиториев и закрыть остальные. Практический риск: чужой человек, узнав имя образа, скачает наш прод-код (в `karaoke-app` лежит собранный jar). Поэтому для наших образов — **только аутентификация**; анонимный read допустим максимум для кэша базовых образов.
- **`insecure-registries` не использовать**: он отключает проверку TLS целиком и, по документации distribution, несовместим с basic auth. Публичный сертификат Let's Encrypt на поддомене — дешевле и не требует правки демона на обоих хостах.
- **Публично доступный порт registry — только 443 через host-nginx**; сам контейнер слушает `127.0.0.1:5000` (как уже сделано для MinIO: «MinIO слушает loopback (127.0.0.1:8890)»).
- **Ограничение по IP не поможет** для admin-машины: её адрес плавает (в `nginx.conf` их уже три: `185.26.28.109`, `83.137.50.231`, `185.26.28.199`). Значит, базовая защита — именно auth, а не allow-list.

---

## Рекомендация

**Ставить Zot (`ghcr.io/project-zot/zot:v2.1.22`) — один контейнер на прод-хосте за существующим host-nginx, с htpasswd-аутентификацией, включённым `http.compat: ["docker2s2"]`, sync-расширением в режиме `onDemand` для `docker.io` (и, при необходимости, `ghcr.io`/`quay.io`) и retention-политикой. `insecure-registries` не заводить.**

Почему именно так, под наши 3.8 ГБ и 4 образа:

1. **Единственный кандидат, который одновременно (а) лёгкий, (б) умеет несколько upstream в одном процессе, (в) имеет встроенный retention и GC без остановки.** Distribution умеет только один upstream на инстанс и не имеет retention; Harbor/Nexus/GitLab официально требуют **4 / 16 / 16 ГБ** — это больше, чем весь наш хост, и рядом с `karaoke-web` (heap 1200m) они физически не встают.
2. **Безопасность без компромиссов**: публичный TLS-сертификат на `repository.sm-karaoke.ru` (certbot), закрытые push и pull, никакого `insecure-registries`, никакой правки docker-демона ради доверия к registry.
3. **Эксплуатация дешевле**: `zot verify-feature retention` (dry-run) + inline GC вместо «останови registry, запусти `garbage-collect`» — на одном хосте с продом остановка registry на время GC означает недоступность pull'ов.
4. **Размер**: 75.6 МБ образ против 678 МБ у Harbor-стека; один процесс вместо одиннадцати.
5. **Цена решения честно названа**: официальных цифр RAM у Zot нет (как и у distribution) — точный RSS измеряется на стенде (`docker stats`) после запуска; это первый пункт открытых вопросов, и если он окажется неприемлемым, **план Б** — `registry:3` (20.5 МБ, документированный `proxy.remoteurl`), но тогда: отдельный инстанс на каждый upstream, retention реализуется своим cron-скриптом, а GC требует кратковременной остановки registry.

Что **не** выбирать: Harbor, Nexus, GitLab CE (не влезают), Gitea/Forgejo (не решает pull-through), Artifactory OSS (не проверено, официальная документация недоступна).

---

## Черновик конфигурации

> Ниже — файлы-черновики для ревью владельцем. В этом исследовании **ничего из этого не создавалось и не запускалось**. Пути соответствуют раскладке `deploy/prod-single-host/` и каталогу данных `/sm-karaoke/system/…`.

### 0. Предпосылки

- DNS: A-запись `repository.sm-karaoke.ru` → `188.127.240.124` (делегирование на `ns5/ns6.hosting.reg.ru` уже есть).
- Контейнеры прода получают внешний доступ через host-nginx; сеть `deploy_karaokenet` уже создана (`deploy/prod-single-host/README.md`).

### 1. `deploy/prod-single-host/docker-compose-registry.yml`

```yaml
services:
  karaoke-registry:
    image: ghcr.io/project-zot/zot:v2.1.22
    container_name: karaoke-registry
    restart: unless-stopped
    # Наружу не публикуем: TLS и доступ — через host-nginx на 127.0.0.1:5000.
    ports:
      - "127.0.0.1:5000:5000"
    volumes:
      - /sm-karaoke/system/registry:/var/lib/registry
      - ./registry/config.json:/etc/zot/config.json:ro
      - ./registry/htpasswd:/etc/zot/htpasswd:ro
    # В образе zot Cmd = ["serve", "/etc/zot/config.json"], User = 0 (измерено из манифеста).
    # Volume /sm-karaoke/system/registry должен быть доступен пользователю root (как и у остальных
    # каталогов /sm-karaoke/system/Караоке-* на этом хосте).
```

### 2. `deploy/prod-single-host/registry/config.json`

```json
{
  "distSpecVersion": "1.1.1",
  "storage": {
    "rootDirectory": "/var/lib/registry",
    "gc": true,
    "gcDelay": "2h",
    "gcInterval": "24h",
    "retention": {
      "dryRun": false,
      "delay": "24h",
      "policies": [
        {
          "repositories": ["karaoke/**"],
          "deleteUntagged": true,
          "keepTags": [
            {
              "mostRecentlyPushedCount": 10,
              "pushedWithin": "2160h"
            }
          ]
        },
        {
          "repositories": ["**"],
          "deleteUntagged": true,
          "keepUntagged": { "pulledWithin": "720h" }
        }
      ]
    }
  },
  "http": {
    "address": "0.0.0.0",
    "port": "5000",
    "realm": "karaoke-registry",
    "compat": ["docker2s2"],
    "auth": {
      "htpasswd": { "path": "/etc/zot/htpasswd" },
      "failDelay": 5
    }
  },
  "log": { "level": "info" },
  "extensions": {
    "sync": {
      "enable": true,
      "registries": [
        {
          "urls": ["https://registry-1.docker.io"],
          "onDemand": true,
          "tlsVerify": true,
          "maxRetries": 3,
          "retryDelay": "5m",
          "syncTimeout": "2h"
        },
        {
          "urls": ["https://ghcr.io"],
          "onDemand": true,
          "tlsVerify": true,
          "maxRetries": 3,
          "retryDelay": "5m"
        },
        {
          "urls": ["https://quay.io"],
          "onDemand": true,
          "tlsVerify": true,
          "maxRetries": 3,
          "retryDelay": "5m"
        }
      ]
    }
  }
}
```

Замечания к конфигу (каждый пункт опирается на цитату из docs):

- `http.compat: ["docker2s2"]` — обязателен, иначе Zot «rejects Docker schema 2 content instead of converting it to OCI» ([mirroring](https://zotregistry.dev/v2.1.22/articles/mirroring/)).
- `onDemand: true` без `pollInterval`/`content` — «sync is enabled in on demand mode for any image» (таблица режимов там же). Именно это делает возможным `registry-mirrors` для docker.io.
- Для `ghcr.io`/`quay.io` классический `registry-mirrors` не сработает (ограничение Docker, см. §4) — либо containerd `hosts.toml`, либо явные pull'ы с префиксом. Если решим ограничиться прод-нуждами, эти два блока можно убрать вовсе.
- Политика `karaoke/**` — «first policy it matches», поэтому порядок важен; политика `**` — «default policy», рекомендованная самой документацией.
- **Не задавать `destination`/`stripPrefix`** для docker.io: путь upstream должен сохраниться (`library/postgres`), иначе `registry-mirrors` промахнётся. Это **требует проверки на стенде** (см. открытые вопросы).

### 3. htpasswd

```bash
# bcrypt обязателен. Файл кладём рядом с конфигом, права 0600, в git не коммитим.
docker run --rm --entrypoint htpasswd httpd:2 -Bbn karaoke 'CHANGE_ME' \
  > deploy/prod-single-host/registry/htpasswd
chmod 600 deploy/prod-single-host/registry/htpasswd
```

### 4. `/etc/nginx/sites-enabled/repository.sm-karaoke.ru` (host-nginx)

```nginx
# ACME + редирект на HTTPS
server {
    listen 80;
    server_name repository.sm-karaoke.ru;

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    http2 on;
    server_name repository.sm-karaoke.ru;

    ssl_certificate     /etc/keys/repository.sm-karaoke.ru.crt;
    ssl_certificate_key /etc/keys/repository.sm-karaoke.ru.key;

    # ВАЖНО: в http{} нашего nginx.conf стоит `client_max_body_size 20M;`
    # (deploy/prod-single-host/nginx.conf). Для образа 5.4 ГБ это даст 413,
    # поэтому здесь лимит снимается — ровно как в официальном рецепте
    # distribution «disable any limits to avoid HTTP 413 for large image uploads».
    client_max_body_size 0;

    # Обязательно для docker push (иначе HTTP 411) — официальный рецепт distribution.
    chunked_transfer_encoding on;

    # Не буферизовать 5.4 ГБ на диск в /var/lib/nginx/tmp.
    proxy_request_buffering off;

    location /v2/ {
        proxy_pass http://127.0.0.1:5000;
        proxy_http_version 1.1;

        # Host обязателен «for docker client's sake»; X-Forwarded-* —
        # иначе registry отдаёт redirect'ы на внутренние имена и http.
        proxy_set_header Host              $http_host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_read_timeout 900;
        proxy_send_timeout 900;
        proxy_connect_timeout 10s;
    }

    # /v2/_catalog и прочие API-пути живут под /v2/, отдельного location не нужно.
    location / {
        return 404;
    }
}
```

Аутентификацию делает **сам Zot** (htpasswd), а не nginx: в этом случае nginx не «съедает» заголовок `Docker-Distribution-Api-Version`, и не нужен `map $upstream_http_docker_distribution_api_version` из рецепта.

### 5. Сертификат

```bash
# после появления A-записи
certbot certonly --webroot -w /var/www/html -d repository.sm-karaoke.ru
# затем в nginx.conf указать полученные файлы (или /etc/letsencrypt/live/... — симлинки)
nginx -t && systemctl reload nginx
```

### 6. Клиенты: mirror базовых образов с Docker Hub

На **проде** и (если нужно) на **admin-машине** — `/etc/docker/daemon.json`:

```json
{
  "registry-mirrors": ["https://repository.sm-karaoke.ru"]
}
```

```bash
systemctl restart docker
```

Это документированный сценарий Zot ([Using Docker with zot](https://zotregistry.dev/v2.1.22/articles/docker/)) и distribution ([mirror](https://distribution.github.io/distribution/recipes/mirror/)). Работает **только для Docker Hub**; `insecure-registries` добавлять **не нужно** — сертификат публичный.

### 7. Правки `deploy/do.sh` / `deploy/do.env` (черновик, не применено)

```bash
# --- deploy/do.env (admin) и /root/Karaoke/deploy/do.env (прод) ---
REGISTRY_HOST=repository.sm-karaoke.ru
REGISTRY_USER=karaoke
REGISTRY_PASSWORD=CHANGE_ME
DOCKER_REGISTRY=${REGISTRY_HOST}/karaoke     # было: svoemestodev
```

```bash
# --- deploy/do.sh ---
# 1) вместо ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
#    (встречается 6 раз: строки 325, 334, 341, 348, 355, 362):
${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin <<<"${REGISTRY_PASSWORD}"

# 2) do_pull_app / do_pull_web / do_pull_webvue3 сейчас НЕ логинятся вообще.
#    Для закрытого registry добавить логин и в них (либо разрешить анонимный read
#    только для кэша базовых образов и оставить pull без логина).

# 3) разовая уборка старых локальных тегов после переезда:
#    docker images | grep svoemestodev | awk '{print $1":"$2}' | xargs -r docker rmi
```

### 8. Проверка после развёртывания

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://repository.sm-karaoke.ru/v2/         # ожидаем 401
docker login repository.sm-karaoke.ru -u karaoke
docker pull repository.sm-karaoke.ru/karaoke/karaoke-web:1
docker stats --no-stream karaoke-registry                                              # фактический RSS
curl -s https://repository.sm-karaoke.ru/v2/_catalog -u karaoke                          # список репозиториев
du -sh /sm-karaoke/system/registry                                                       # фактический объём
```

---

## Открытые вопросы

1. **RAM-профиль выбранного registry неизвестен ни по одному первоисточнику.** Ни у `distribution`, ни у Zot официальных требований к памяти нет («не найдено»). Нужен замер `docker stats` после развёртывания — если RSS окажется неприемлемым для 3.8 ГБ, переключаемся на план Б (`registry:3`, 20.5 МБ). **Решение владельца: кто и когда делает замер.**
2. **Сохраняется ли upstream-путь при `onDemand` без `destination`** (чтобы `registry-mirrors` действительно попадал в `/v2/library/postgres/...`). Таблица режимов в [mirroring](https://zotregistry.dev/v2.1.22/articles/mirroring/) это допускает, примера с docker.io без `destination` в docs нет. Проверяется одним `docker pull` после развёртывания; если путь не сохраняется — либо явный `destination`, либо containerd `hosts.toml` с `override_path = true`.
3. **Анонимный pull или полностью закрытый registry** — продуктовое решение. Аргументы за закрытый: в `karaoke-app` лежит собранный jar с бизнес-логикой. Аргументы за анонимный read для кэша базовых образов: прод сможет тянуть `postgres:16`/`nginx:stable` без хранения пароля registry в `do.env` прода.
4. **DNS и сертификат**: A-запись `repository.sm-karaoke.ru` у reg.ru ещё не заведена; certbot нужно запускать после её появления. Кто владелец домена и кто заводит запись — не выяснено.
5. **Точный объём registry** после первого push (сжатые слои наших образов) неизвестен — ожидаемо 3.5–6 ГБ на набор, точное число измеряется `du -sh` и `GET /v2/_catalog` + API размеров.
6. **Vendoring vs pull-through для базовых образов**: прод использует всего три внешних образа (`postgres:16`, `nginx:stable`, `quay.io/minio/minio`). Альтернатива зеркалу — один раз запушить их в наш registry под своими тегами и убрать docker.io из прод-пути совсем (тогда pull-through нужен только на admin-машине). Это уменьшает зависимость прода от интернета, но добавляет ручной шаг обновления базовых образов.
7. **Что делать с большими образами admin-машины** (`demucs` 4.79 ГБ, `ollama` 3.78 ГБ, `whisper-server:cuda` 3.33 ГБ) — кладём ли мы их в тот же registry (на проде они не нужны, но admin-машина — второй потребитель) или держим Docker Hub для них.
8. **Artifactory OSS** — требования и сам факт наличия бесплатной редакции с Docker-репозиториями **не подтверждены**: официальные URL отдают 404, сайт `jfrog.com` за 25 с не отдался. Если владелец считает этот вариант значимым — нужен отдельный заход.
9. **Уровень зрелости Zot в CNCF** (Sandbox/Incubating) не подтверждён первоисточником (страница рендерится JS). Лицензия Apache-2.0 и авторство Linux Foundation по футеру документации подтверждены.
10. **Нужен ли UI** (`extensions.ui` у Zot) — влияет на то, надо ли вообще включать расширения `search`/`ui` (они добавляют память и индексирование CVE) или оставить registry «чистым».
11. **Бэкап registry**: данных в нём — только образы (воспроизводимы из git + сборки). Нужно решение: считать registry невосстановимым ресурсом (не бэкапить) или включить каталог `/sm-karaoke/system/registry` в существующую схему бэкапов (`karaoke-db-backup.timer` бэкапит только БД).
12. **Судьба Docker Hub namespace `svoemestodev`**: остаётся ли он как публичное зеркало/резерв или отключается после переезда (там же лежат `demucs:latest` и старые версии).

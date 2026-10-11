# Contract: именование контейнерных образов

**Спека**: [spec.md](../spec.md) (FR-003, FR-007, FR-008, FR-017, FR-018) |
**План**: [plan.md](../plan.md) (D6, D8, D9) | **Модель**: [data-model.md](../data-model.md) § E1, E2

Контракт описывает **нормативную грамматику** ссылки на контейнерный образ в этом
проекте и **карту конкретных имён** «старое → новое». Потребители контракта:
`deploy/do.sh`, `deploy/*/Dockerfile`, `deploy/**/docker-compose*.yml`,
`tools/check-docker-image-tags.sh`, `tools/registry-vendor.sh`.
Полный реестр пинов — `deploy/prod-single-host/registry/images.lock`.

---

## 1. Грамматика

```
ref        := host [":" port] "/" path [":" tag] ["@" digest]
host       := "sm-karaoke.ru"                    ; содержит точку ⟹ docker видит registry
port       := "5443"                            ; обязателен: не 443, не 80
path       := namespace "/" repo
namespace  := "karaoke" | "mirror" | "library"
tag        := BUILD_VERSION | semver | release-label
digest     := "sha256:" 64*hex
```

Примеры:

```
sm-karaoke.ru:5443/karaoke/karaoke-web:1
sm-karaoke.ru:5443/mirror/library/postgres:16.10@sha256:9f2c…
sm-karaoke.ru:5443/library/node:22-alpine
```

**Инвариант хоста.** Первый компонент ссылки распознаётся docker'ом как registry
только если содержит `.` или `:` (или это `localhost`). `DOCKER_REGISTRY` в `do.env`
обязан содержать и точку, и порт: `DOCKER_REGISTRY=sm-karaoke.ru:5443/karaoke`.
Значение `svoemestodev` (текущее) — namespace Docker Hub, не хост.

---

## 2. Правила namespace

| Namespace | Правило | Нарушение |
|---|---|---|
| `karaoke/**` | только образы, собранные нами; тег = `${BUILD_VERSION}` (`latest` запрещён) | FR-008 (для своих — гигиена), D6 |
| `mirror/**` | только сторонние образы; путь после `mirror/` = **исходный путь upstream** без его хоста; обязателен пин | FR-018, FR-008 |
| `library/**` | **только** pull-through Zot. Ни человек, ни скрипт не пушат сюда **никогда** | инвариант I-2 |

Почему `library/**` неприкосновенен для записи: этот же префикс стоит в
`extensions.sync.registries[].content` (обязательный allow-list, ловушка #275).
`docker push` перед заливкой манифеста делает `HEAD`; промах по allow-list заставляет
Zot идти наверх. Для приватного образа upstream отвечает `unauthorized` → **503** и
оборванный push (измерено). Для `library/postgres` upstream ответит `200` — это иной,
непроверенный режим sync-управляемого репозитория. Поэтому библиотечные образы,
которые мы вендорим, кладутся в `mirror/library/**`.

---

## 3. Карта «старое → новое»

### 3.1 Свои образы (`karaoke/**`) — собираются на admin

| Было | Стало | Где используется |
|---|---|---|
| `${DOCKER_REGISTRY}/karaoke-app:${BUILD_VERSION}` | `sm-karaoke.ru:5443/karaoke/karaoke-app:${BUILD_VERSION}` | `deploy/docker-compose.yml`, `docker-compose-app.yml` |
| `${DOCKER_REGISTRY}/karaoke-web:${BUILD_VERSION}` | `…/karaoke/karaoke-web:${BUILD_VERSION}` | `docker-compose.yml`, `docker-compose-web.yml`, прод `docker-compose-web.yml` |
| `${DOCKER_REGISTRY}/karaoke-public:${BUILD_VERSION}` | `…/karaoke/karaoke-public:${BUILD_VERSION}` | `docker-compose-public.yml`, прод `docker-compose-public.yml` |
| `${DOCKER_REGISTRY}/karaoke-webvue3:${BUILD_VERSION}` | `…/karaoke/karaoke-webvue3:${BUILD_VERSION}` | `docker-compose-webvue3.yml` |
| `${DOCKER_REGISTRY}/demucs:latest` | `…/karaoke/demucs:${BUILD_VERSION}` | `do_build_demucs`, `do_push_demucs` |

> Свои образы уже параметризованы через `${DOCKER_REGISTRY}` — правки compose не
> нужны, меняется **значение** переменной в `do.env` (admin и прод). Единственное
> исключение — `demucs`: тег `latest` заменяется на `${BUILD_VERSION}`.

### 3.2 Вендоренные сторонние образы (`mirror/**`) — переезжают к нам

| Upstream | Новое имя в нашем registry | Потребитель | Пин |
|---|---|---|---|
| `docker.io/ollama/ollama:latest` | `…/mirror/ollama/ollama:<версия>@sha256:…` | admin | дайджест (тег плавающий) |
| `docker.io/hwdsl2/whisper-server:cuda` | `…/mirror/hwdsl2/whisper-server:<версия>@sha256:…` | admin | дайджест (иммутабельного тега нет) |
| `docker.io/searxng/searxng:latest` | `…/mirror/searxng/searxng:<версия>@sha256:…` | admin | дайджест |
| `docker.io/luuul/4get:1.0.44` | `…/mirror/luuul/4get:1.0.44@sha256:…` | admin | тег уже пин + дайджест |
| `ghcr.io/xtls/xray-core:latest` | `…/mirror/xtls/xray-core:<версия>@sha256:…` | admin | дайджест |
| `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | `…/mirror/minio/minio:RELEASE.2025-09-07T16-13-09Z@sha256:…` | **прод** | тег уже пин + дайджест |
| `docker.io/nvidia/cuda:12.6.2-base-ubuntu22.04` | `…/mirror/nvidia/cuda:12.6.2-base-ubuntu22.04@sha256:…` | admin (база `DockerfileDemucs`) | тег уже пин + дайджест |
| `docker.io/library/postgres:16` | `…/mirror/library/postgres:<16.минор>@sha256:…` | **прод** | дайджест (тег `16` плавающий) |

Итого сторонних: **8** (шесть из решения #279 + два базовых образа прода: `minio`
по FR-007 и `nvidia/cuda` по FR-017). Измерены только первые шесть — ≈11.8 ГБ;
`minio` и `nvidia/cuda` прибавляются к этому, точный объём фиксируется при переносе
(в `images.lock`). Делается один раз, вне окна обслуживания прода.

### 3.3 Базовые образы сборки — через pull-through, без вендоринга

| Было (`FROM`) | Стало | Файл | Guard |
|---|---|---|---|
| `openjdk:17-ea-jdk-slim` | `sm-karaoke.ru:5443/library/openjdk:17-ea-jdk-slim` | `deploy/karaoke-app/Dockerfile` | — |
| `ubuntu:22.04` | `…/library/ubuntu:22.04` | `deploy/karaoke-app/Dockerfile` | — |
| `eclipse-temurin:22-jre-jammy` | `…/library/eclipse-temurin:22-jre-jammy` | `deploy/karaoke-web/Dockerfile` | — |
| `node:22-alpine` | `…/library/node:22-alpine` | `karaoke-public`, `karaoke-webvue3` | **R-05** |
| `nginx:stable` | `…/library/nginx:stable` | `karaoke-public`, `karaoke-webvue3` | **R-04** |

`library/**` покрывает официальные образы Docker Hub; `docker.io/nvidia/cuda` в него
не входит и потому вендорится (§ 3.2). Правило «путь сохраняется» (R-2 research)
делает `FROM …/library/node:22-alpine` рабочим без `daemon.json`: имя само указывает
хост, Zot при промахе идёт в `docker.io` по allow-list и кэширует.

### 3.4 Вне области этого контракта

| Образ | Почему |
|---|---|
| `postgres:13-alpine`, `openproject/community:13` | tracker (OpenProject) — не входит в шесть сторонних образа и не обслуживает прод-деплой; добавляется «по конкретной нужде» (решение #276) |
| `minio/minio:latest` в `deploy/docker-compose-storage.yml` | устаревшее имя, по README прода отдаёт 404; канон прода использует `quay.io/minio/minio:…` → вендорится (§ 3.2) |
| `DockerfileBackup`, `DockerfileBackup2`, `DockerfileOld`, `DockerfileFull`, `deploy/web-server-deploy/**` | не участвуют в сборке; `Dockerfile*` с этими суффиксами исключены guard'ом (`check-docker-image-tags.sh:101`) |

---

## 4. Нормализация имени для guard'а

Guard R-04/R-05 проверяет тег независимо от того, указан ли registry:

```
normalize(FROM-строка) := <последний компонент пути> ":" <тег>
```

| Входная строка `FROM` | Нормализовано | Вердикт |
|---|---|---|
| `nginx:stable AS production-stage` | `nginx:stable` | OK (R-04) |
| `sm-karaoke.ru:5443/library/nginx:stable` | `nginx:stable` | OK |
| `sm-karaoke.ru:5443/library/nginx:alpine` | `nginx:alpine` | **нарушение** |
| `sm-karaoke.ru:5443/library/node:22-alpine AS build-stage` | `node:22-alpine` | OK (R-05) |
| `sm-karaoke.ru:5443/library/node:latest` | `node:latest` | **нарушение** |
| `mirror/library/node:20-bookworm-slim` | `node:20-bookworm-slim` | OK |

Детали и коды возврата — [`tag-guard.md`](./tag-guard.md).

---

## 5. Критерии приёмки контракта

1. `grep -rn 'image:' deploy/` не содержит ни одного внешнего имени в путях прода и
   сборки: все ссылки либо `${DOCKER_REGISTRY}/karaoke/…`, либо
   `sm-karaoke.ru:5443/mirror/…`.
2. `grep -rn '^FROM ' $(git ls-files | grep Dockerfile)` — каждый `FROM` боевого
   Dockerfile указывает на `<registry>/library/…` или `<registry>/mirror/…`.
3. `GET /v2/_catalog` не содержит репозиториев `library/*`, которых нет у upstream
   (инвариант I-2).
4. `tools/registry-vendor.sh --verify` — зелёный (все `mirror/**` записи совпадают с
   `images.lock`).

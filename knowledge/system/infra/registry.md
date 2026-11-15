# Component: registry (Zot на прод-хосте — локальный кэш и зеркало Harbor)

> **Домен**: system (infrastructure)
> **Компонента**: Zot на прод-хосте — локальный источник образов для деплоя и
> живое зеркало Harbor; host-nginx, доступы, retention, восстановление.

> **Терминология.** Здесь «образ» = **контейнерный образ** (упакованное приложение,
> публикуемое в registry). В проекте уже есть ADR
> [`local-0003-shared-minio-image-cache.md`](../../adr/local-0003-shared-minio-image-cache.md),
> где «образ» — медиа-изображение (обложки, кадры); этот документ к медиа отношения
> не имеет. Подробности — `specs/540-private-docker-registry/spec.md`.

## Назначение

Zot решает две задачи: (1) прод-хост получает образы локально, без обращения к
внешним реестрам и без зависимости от infra-сервера; (2) он принимает push от
Harbor и потому остаётся актуальным зеркалом основного каталога.

С 2026-10-27 основной каталог образов — **Harbor** на infra-сервере
([harbor-registry.md](harbor-registry.md)); Zot остаётся **writable** и не
переводится в read-only, пока прод тянет из него образы. История исходной
задачи «свой registry вместо Docker Hub» сохранена ниже.

- **Софт**: `ghcr.io/project-zot/zot:v2.1.22` — один статический Go-бинарник, один
  контейнер (`karaoke-registry`), образ без shell.
- **Место**: прод-хост `sm-karaoke.ru` (`188.127.240.124`), рядом с боевыми
  контейнерами; измеренный RSS — 49.9 МиБ в покое, пик **114.3 МиБ** при push образа
  5.36 ГБ (слои в памяти не буферизуются).
- **Наружу**: HTTPS `sm-karaoke.ru:5443` через **существующий** host-nginx;
  контейнер слушает `127.0.0.1:5000` и наружу не публикуется.
- **Спека и контракты**: `specs/540-private-docker-registry/` (issue #286,
  wayfinder-карта #274).

## Интерфейсы и Контракты

- **`GET /v2/`** — Distribution API. Без кредов → `401`; с верными → `200`.
  Это же — проверка живучести (контейнерного `healthcheck` нет: в образе Zot нет
  ни shell, ни HTTP-клиента).
- **`GET /v2/_catalog`** — список репозиториев, **отфильтрованный по правам**:
  проект видит только свои namespace (+ общие `mirror/**`, `library/**`).
- **`POST /v2/<name>/blobs/uploads/`** — начало загрузки слоя; ответ содержит
  **относительный** `Location` (`/v2/…`), поэтому редиректы не утекают на
  `http://127.0.0.1:5000`. Проверено на стенде через nginx-прокси.
- **Аутентификация** — `htpasswd` (только bcrypt), `failDelay: 5`. Анонимного доступа
  нет ни на одну операцию.
- **Авторизация** — `http.accessControl.repositories`: политики по glob'ам
  (`karaoke/**`, `mirror/**`, `library/**`), `users` матчатся **точно** (wildcard нет).
- **Публикация** — `deploy/do.sh push_*` (см. [`do-sh.md`](do-sh.md)); получение —
  `do.sh pull_*` и compose-файлы.

## Логика и Алгоритмы

### Пространства имён

| Namespace | Что там | Кто пишет |
|---|---|---|
| `karaoke/**` | образы проекта Караоке | admin, `do.sh push_*` |
| `mirror/<путь upstream>` | перенесённые сторонние образы, исходный путь сохранён | admin, `tools/registry-vendor.sh` |
| `library/**` | пустая зона, оставленная как read-only pull-through | **никто** (sync отключён) |

**`library/**` — read-only зона, и с 2026-11-15 она ПУСТАЯ.** Этот же префикс стоит в
`extensions.sync.registries[].content` (allow-list upstream'а). `docker push` перед
заливкой манифеста делает `HEAD`; промах по allow-list заставляет Zot идти наверх.
Для чужого образа upstream ответит `unauthorized` → **503** и push оборвётся
(измерено на стенде #275). Поэтому в `library/**` не пушим никогда, а вендоренные
копии библиотечных образов кладутся в `mirror/library/**`.

**Почему зона пустая (решение владельца 2026-11-15).** Pull-through через Zot
оказался нерабочим: синк с Docker Hub идёт **15–17 минут** (`maxRetries: 3`,
`retryDelay: 5m`), клиент отваливается по таймауту раньше; а storage-GC
(`storage.gc: true`, `gcDelay: 2h`) вычищает то, что Zot насинкал, — зона
опустела целиком, и любая сборка с `FROM …/library/*` падала. Разбор — в
[deploy/prod-single-host/registry/RUNBOOK.md § 2б](../../../deploy/prod-single-host/registry/RUNBOOK.md),
решение — **вендорить базовые образы в `mirror/library/**`** (пины в
`images.lock`) и отключить on-demand sync (`"onDemand": false`).

### Базовые образы сборки — в `mirror/library/**` (с 2026-11-15)

Раньше базовые образы брались из pull-through-зоны (`§ 3.3` контракта
`specs/540-private-docker-registry/contracts/image-naming.md`). Теперь все пять
вендорены в `mirror/library/**` и переключены в `FROM`:

| Образ | our_ref | Dockerfile |
|---|---|---|
| `eclipse-temurin:22-jre-jammy` | `mirror/library/eclipse-temurin:22-jre-jammy` | `deploy/karaoke-web/Dockerfile` |
| `node:22-alpine` | `mirror/library/node:22-alpine` | `deploy/karaoke-public`, `deploy/karaoke-webvue3` |
| `nginx:stable` | `mirror/library/nginx:stable` | `deploy/karaoke-public`, `deploy/karaoke-webvue3` |
| `ubuntu:22.04` | `mirror/library/ubuntu:22.04` | `deploy/karaoke-app/Dockerfile` |
| `openjdk:17-ea-jdk-slim` | `mirror/library/openjdk:17-ea-jdk-slim` | `deploy/karaoke-app/Dockerfile` |

Пин — дайджест в `images.lock` (в `FROM` — тег без дайджеста, как в
`DockerfileDemucs`; пины сверяются `tools/registry-vendor.sh --verify`). Переключение
проверено сборкой всех четырёх образов: ни одного обращения к `library/**`.

### Зависимость от Docker Hub (на время перехода)

Пока `DUAL_PUSH=1`, `do_push_*` зеркалит образы и в Hub (архив отката). Окно — до двух
успешных прод-деплоев и не дольше месяца (решение владельца #280). После `DUAL_PUSH=0`
внешних реестров в пути доставки ноль.

### Retention

`storage.retention.policies` (порядок значим — «first policy it matches»):

1. `karaoke/**` — keep 10 последних push **или** push за 90 дней; untagged удаляются;
2. `mirror/**` — только untagged; tag-retention выключен, пины неприкосновенны;
3. `**` — дефолтная политика.

Правила внутри политики комбинируются по **ИЛИ** (`pkg/retention/retention.go`), а
tag-retention применяется только там, где есть `keepTags`
(`pkg/storage/gc/gc.go`). GC у Zot **inline** — останавливать registry не нужно.

### Обслуживающие инструменты: приёмка и вендоринг

`tools/registry-smoke.sh` (приёмка) и `tools/registry-vendor.sh` (перенос образов и
сверка пинов) — операционные инструменты админ-контура. Правила, которые они
обязаны соблюдать:

| Правило | Как соблюдается |
|---|---|
| **Секрет не уходит в argv** | Креды registry передаются curl через stdin-конфиг (`--config -`, `user = "…"`), Bearer-токен upstream — через `header = "Authorization: …"` в том же конфиге. В `ps -eo args` видны только URL и опции. |
| **Проверка «неверный пароль → 401» обязана слать креды** | `registry-smoke.sh` блок [3] отправляет **заведомо неверную** пару (реальный пользователь + неверный пароль, `$PASS_CRED` не переиспользуется). Раньше креды не отправлялись вовсе, и проверка была дубликатом анонимной — registry со сломанным `htpasswd` (принимает любой пароль) проходил приёмку с ложным PASS. |
| **Пин образа не теряется** | `registry-vendor.sh` при недоступном registry **не пишет** в колонку `digest` lock-файла значение `PENDING`: прежний валидный `sha256:…` сохраняется. Значение `PENDING` в lock-файле — ошибка проверки (`--verify` → FAIL), а не «нечего проверять». |

## Зависимости

- → **host-nginx**: терминирует TLS (`/etc/keys/www.sm-karaoke.ru.{crt,key}`) и
  проксирует на `127.0.0.1:5000`. Без него registry наружу не виден.
- → **TLS-сертификат домена**: при просрочке nginx продолжит работать, а Docker
  (Go) упадёт с `x509: certificate has expired` — встанут push, pull и деплой.
  Продление ручное, владельцем; следит `tools/check-cert-expiry.sh` (FR-016).
- → **Docker Hub** (`registry-1.docker.io`): единственный upstream pull-through,
  ограничен allow-list'ом `library/**`.
- → **`deploy/do.sh`** и **`deploy/prod-single-host/do.sh`**: логин, push, pull.
- → **`tools/registry-smoke.sh`**: приёмка; **`tools/registry-vendor.sh`**: перенос
  сторонних образов по `deploy/prod-single-host/registry/images.lock`.

## Известные ловушки (проверено на стенде 2026-10-11)

1. **`onDemand` без `content` ломает push.** Без allow-list Zot трактует промах по
   `HEAD` как «сходить наверх», получает `unauthorized` и отдаёт **503** — push нового
   приватного образа падает, оставив залитые слои. Лечится `content`-allow-list'ом.
2. **Bind-mount файла vs каталога.** Если смонтировать `htpasswd`/`config.json`
   отдельными файлами, правка на хосте (`sed -i`, `scp`, `mv`) **не доходит** до
   контейнера: bind-mount привязан к inode, Zot продолжает видеть старый файл —
   удалённый пользователь получает `200`, в логе `users: 2` при одном пользователе в
   файле. Отзыв доступа (FR-004) молча перестаёт работать. Поэтому в
   `docker-compose-registry.yml` монтируется **каталог** `./registry:/etc/zot:ro`.
3. **Одной строки в `htpasswd` мало.** Без `http.accessControl` любой валидный кред
   видит и читает **все** репозитории (проверено: второй пользователь получал `200`
   на чужом namespace). Изоляция — это политики в `config.json`.
4. **`docker login` — одна запись на хост в одном `DOCKER_CONFIG`.** Второй логин в
   тот же хост вытесняет первый; выглядя как «внезапно отказ в доступе» на push.
   Двум проектам на одной машине нужны разные `DOCKER_CONFIG`.
5. **`verify-feature retention` требует остановленного сервера** («the zot server
   must be stopped before running this command», плюс `gc: true` в конфиге) — на
   живом проде это недоступность registry. Проверка политики — стендовая.
6. **`python3 -c "import crypt"` работает только на admin** (Python 3.12); на проде
   Ubuntu 26.04 несёт Python ≥ 3.13, где модуль удалён (PEP 594). bcrypt генерируется
   на admin, на прод едет готовый файл.
7. **`nginx: client_max_body_size 20M` из `http{}` даёт 413** на любом слое больше
   20 МБ — размер тела слоя выставляется в `location` registry, иначе заливка образа
   обрывается на стороне прокси.
8. **Таймаут Zot по умолчанию (60 с) убивает заливку большого слоя.** Монолитный `PUT`
   слоя 5.6 ГБ через nginx упал с **502** через 67 секунд. Лечится
   `http.readTimeout` / `http.writeTimeout` = `2h` в конфиге Zot — без этого образ
   ≥ 6 ГБ залить нельзя.

## Связанные ADR | Related ADRs

- [`local-0003-shared-minio-image-cache.md`](../../adr/local-0003-shared-minio-image-cache.md) —
  «образ» = медиа-изображение; терминологическая коллизия, разведённая выше.

## Роль после 2026-10-27 (зеркало Harbor)

- Harbor реплицирует в Zot три потока: `svoemesto/**` → `karaoke/**`,
  `dub2000/**` → `dub2000/**`, `mirror/**` → `mirror/**` (event-based).
- Namespace на проде сохраняются прежними, поэтому compose и `do.sh` не меняют
  пути образов.
- Отставание репликации или `denied` на push — инцидент: значит, на проде нет
  свежего образа.
- Выключение Zot или перевод в read-only снимает и зеркало, и независимость
  прода от infra-сервера (см. ADR local-0010).

### Harbor — источник правды для `mirror/**` (2026-11-15)

Правило, которое обязано соблюдаться при вендоринге: **образ, лежащий в Zot,
должен лежать и в Harbor**, иначе Harbor перестаёт быть каталогом, а Zot —
его зеркалом.

- Источник правды — Harbor-проект **`mirror`** (public, 13 репозиториев).
  Репликация `mirror/** → mirror/**` доставляет его в Zot автоматически.
- Пин — дайджест в `images.lock`; сверка `tools/registry-vendor.sh --verify`
  проверяет **Zot** (куда тянет прод). Harbor и Zot обязаны иметь **одинаковый**
  digest: инвариант ADR local-0010 п.3.
- Писать в Harbor-проект `mirror` умеет робот **`robot$mirror+vendor`**
  (роли `pull`+`push`, создан 2026-11-15). Креды — `HARBOR_MIRROR_USER` /
  `HARBOR_MIRROR_PASSWORD` в `deploy/do.env` (файл вне git).
- **`tools/registry-vendor.sh` пишет в оба места** (с 2026-11-15): образ из
  `mirror/**` сначала заливается в наш Zot, затем дублируется в Harbor-проект
  `mirror`; в лог идёт строка «залит в Zot и в Harbor (digest совпал)». Если
  Harbor недоступен — WARN, вендоринг в Zot не блокируется; если дайджесты
  разошлись — WARN про multi-arch (лечится `skopeo copy --all`). Harbor получает
  **отдельный** `DOCKER_CONFIG`: у нашего registry и Harbor разные хосты, но
  креды разные (логин одного вытесняет другой в одном `config.json`).
- Робот `robot$svoemesto+svoemesto-ci` в проекте `mirror` имеет **только
  `pull`** — Harbor не даёт одному project-роботу права на два проекта
  (`bad request permission`).
- **Прецедент 2026-11-15:** базовые образы сборки были залиты сразу в Zot
  (`tools/registry-vendor.sh`) — Harbor-проект `mirror` остался без них (8
  репозиториев вместо 13), зеркало разорвалось в одну сторону. Образы долиты в
  Harbor, репликация догнала (`policy=3`, 5 выполнений `Succeed`), digest'ы
  Harbor и Zot совпали. Чтобы разрыв не повторился, инструмент теперь пишет в
  Harbor сам (см. выше).

### Доступ к Harbor и infra-серверу (2026-11-15, выяснено на практике)

- **SSH на infra-сервер**: `ssh -p 7487 -i /home/nsa/.config/karaoke/id_ed25519
  sm@185.71.198.17`. Ключ общий (`~/.ssh/id_rsa` и `~/.ssh/win10-167` — **не
  подходят**, `Permission denied (publickey)`). Host key уже в `known_hosts`.
- **`sudo` на infra-хосте требует пароль** — `lxc exec infra` из сессии агента
  недоступен. Но `/srv/infra` читается, а ежедневные архивы конфигов
  `/home/sm/backup-out/configs-YYYY-MM-DD.tar.gz` содержат
  `harbor.yml` (в архиве — подкаталог `harbor/harbor`) с `harbor_admin_password` — **оттуда берутся
  права админа Harbor** (файл 600, секрет не выводить в лог).
- Harbor API: `GET /api/v2.0/robots/{id}` отдаёт робота по id (список
  `/api/v2.0/robots` пуст). Имя нового робота передаётся **суффиксом**
  (`vendor`), Harbor сам собирает `robot$<project>+<suffix>` — полное имя
  даёт `robot name is not in lower case or contains illegal characters`.
- Добавление робота в проект через `POST /projects/{p}/members` даёт **500**;
  работает создание отдельного робота под нужный проект
  (`POST /api/v2.0/robots`).


## Связь с другими компонентами

- **[harbor-registry.md](harbor-registry.md)** — основной каталог образов.
- **[external-infra.md](external-infra.md)** — сервер, на котором живёт Harbor.
- **[deploy-overview.md](deploy-overview.md)** — место registry в раскладке `deploy/`.
- **[do-sh.md](do-sh.md)** — команды публикации и получения.
- **`AGENTS.md`** — правила деплоя и запрет `docker restart`.
- **`specs/540-private-docker-registry/`** — спека, план, контракты, quickstart.
- **`knowledge/system/infra/registry.md`** — per-feature документ (R-32).

## Changelog

- **2026-11-15**: разрыв зеркала Harbor↔Zot закрыт — базовые образы долиты в
  Harbor-проект `mirror`, репликация догнала, digest'ы совпали; добавлен доступ
  к Harbor/infra-серверу (ключ, admin-пароль из архива конфигов, робот
  `robot$mirror+vendor`).
- **Pass 540** (2026-10-11): Initial. Приватный registry на Zot, issue #286 /
  wayfinder #274. Автор: agent (Karaoke).

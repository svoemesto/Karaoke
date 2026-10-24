# Component: registry (приватный контейнерный registry)

> **Домен**: system (infrastructure)
> **Компонента**: приватный registry контейнерных образов на прод-хосте — Zot,
> host-nginx, доступы, retention, восстановление.

> **Терминология.** Здесь «образ» = **контейнерный образ** (упакованное приложение,
> публикуемое в registry). В проекте уже есть ADR
> [`local-0003-shared-minio-image-cache.md`](../../adr/local-0003-shared-minio-image-cache.md),
> где «образ» — медиа-изображение (обложки, кадры); этот документ к медиа отношения
> не имеет. Подробности — `specs/540-private-docker-registry/spec.md`.

## Назначение

Заменить Docker Hub (`svoemestodev`) своим приватным registry, чтобы образы проекта
не зависели от чужого аккаунта и чужих лимитов, а прод-хост получал их без обращения
к внешним реестрам.

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
| `library/**` | кэш pull-through из `docker.io` | **только** Zot (sync) |

**`library/**` — read-only зона.** Этот же префикс стоит в
`extensions.sync.registries[].content` (allow-list upstream'а). `docker push` перед
заливкой манифеста делает `HEAD`; промах по allow-list заставляет Zot идти наверх.
Для чужого образа upstream ответит `unauthorized` → **503** и push оборвётся
(измерено на стенде #275). Поэтому в `library/**` не пушим никогда, а вендоренные
копии библиотечных образов кладутся в `mirror/library/**`.

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
обязаны соблюдать (аудит #336, OpenProject #379):

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

## Связанные ADR | Related ADRs

- [`local-0003-shared-minio-image-cache.md`](../../adr/local-0003-shared-minio-image-cache.md) —
  «образ» = медиа-изображение; терминологическая коллизия, разведённая выше.

## Связь с другими компонентами

- **[deploy-overview.md](deploy-overview.md)** — место registry в раскладке `deploy/`.
- **[do-sh.md](do-sh.md)** — команды публикации и получения.
- **`AGENTS.md`** — правила деплоя и запрет `docker restart`.
- **`specs/540-private-docker-registry/`** — спека, план, контракты, quickstart.
- **`docs/features/private-docker-registry.md`** — per-feature документ (R-32).

## Changelog

- **OpenProject #379** (2026-10-24, аудит #336, BR-593/BR-594): правила
  обслуживающих инструментов приёмки и вендоринга — креды не передаются через
  argv (curl `--config -`), проверка «неверный пароль → 401» реально отправляет
  неверную пару кредов, `PENDING` больше не затирает пин в `images.lock`
  (прежний `sha256:…` сохраняется, а `PENDING` в lock-файле — ошибка
  проверки, не `skip`).
- **Pass 540** (2026-10-11): Initial. Приватный registry на Zot, issue #286 /
  wayfinder #274. Автор: agent (Karaoke).

# Feature: Приватный контейнерный registry на sm-karaoke.ru (spec 540, OpenProject #286)

> **Status**: active
> **Feature Key**: private-docker-registry
> **Last Updated**: 2026-10-11
> **Spec**: [specs/540-private-docker-registry/spec.md](../../specs/540-private-docker-registry/spec.md)
> **Created**: 2026-10-11
> **История**: wayfinder-карта #274 (закрыта, 11 тикетов), issue исполнения #286.

## Что делает

Держит **контейнерные образы** проекта на нашем собственном хосте и раздаёт их
сборке (admin-машина) и проду (`sm-karaoke.ru`). Реализация — **Zot v2.1.22** в одном
контейнере `karaoke-registry`: слушает `127.0.0.1:5000`, наружу публикуется через
существующий host-nginx на порту **5443** под **существующим** сертификатом домена.
Доступ закрыт целиком: `htpasswd` (bcrypt) + политики по namespace; анонимного
доступа нет ни на одну операцию, включая чтение.

> Терминология: здесь «образ» — **контейнерный образ**. В ADR
> [`local-0003-shared-minio-image-cache.md`](../../knowledge/adr/local-0003-shared-minio-image-cache.md)
> «образ» — медиа-изображение (обложки, кадры); это разные вещи.

## Зачем

Образы проекта публиковались в **Docker Hub под чужим namespace** `svoemestodev`:
чужие лимиты и чужой аккаунт были единой точкой отказа деплоя, а собранный jar с
бизнес-логикой лежал в публичном (по факту) реестре. Свой registry снимает зависимость
от внешнего сервиса, даёт контроль цепочки поставок и разграничение доступа по
проектам (в перспективе — для нескольких проектов машины).

## Как работает

**Пространства имён** (граница изоляции и признак происхождения):

| Namespace | Что там | Кто пишет |
|---|---|---|
| `karaoke/**` | наши образы (`karaoke-app`, `karaoke-web`, `karaoke-public`, `karaoke-webvue3`, `demucs`) | `deploy/do.sh push_*` |
| `mirror/<путь upstream>` | перенесённые сторонние образы, исходный путь сохранён (`mirror/library/postgres`, `mirror/ollama/ollama`, …) | `tools/registry-vendor.sh` по `images.lock` |
| `library/**` | кэш pull-through из `docker.io` | **только** Zot (sync), люди не пушат |

**Поток.** Сборка на admin: `do.sh build_*` (базовые образы берутся `FROM
sm-karaoke.ru:5443/library/…`) → `do.sh push_*` (логин `REGISTRY_HOST` +
`--password-stdin`, push в `karaoke/**`; пока `DUAL_PUSH=1` — ещё и зеркало в Hub) →
`deploy_web.sh` / `deploy_public.sh` → на проде `do.sh pull_web` тянет из
`sm-karaoke.ru:5443/karaoke/**` и пересоздаёт контейнер.

**Доступ.** Выдача — строка в `htpasswd` (bcrypt, генерируется только на admin) плюс
политика проекта в `config.json` (`http.accessControl.repositories.<проект>/**`).
Отзыв — удаление строки. Выдача и отзыв — **одна процедура** (FR-004), укладывается в
**одну минуту** (SC-004), registry при этом не останавливается: оба файла Zot
перечитывает **на лету**, без перезапуска — при условии, что смонтирован **каталог**
`registry/`, а не отдельные файлы (см. ловушки).

**Пины.** Каждый сторонний образ зафиксирован в
[`deploy/prod-single-host/registry/images.lock`](../../deploy/prod-single-host/registry/images.lock)
как `upstream_ref → наш ref + @sha256:<digest>`; проверка — `registry-vendor.sh --verify`.

**Рост хранилища.** Retention Zot: `karaoke/**` — 10 последних push или 90 дней;
`mirror/**` — только untagged (пины неприкосновенны). Данные лежат вне
docker-конфайнмента, поэтому `karaoke-docker-prune.timer` их не касается.

## Инварианты

1. **Ни один прод-контейнер не получает образ от внешнего реестра** — все ссылки в
   compose прода ведут на `sm-karaoke.ru:5443/{karaoke,mirror}/**`.
2. **В `library/**` нет ни одной созданной нами записи** — это read-only зона
   pull-through; вендоренные копии библиотечных образов идут в `mirror/library/**`.
3. **Каждый сторонний образ в `mirror/**` имеет пин** (`@sha256:`) и запись в `images.lock`.
4. **Тег действующего прод-образа не может быть удалён retention** — прод тянет тег,
   который перепушивается каждым деплоем (самый свежий).
5. **Анонимный запрос снаружи не даёт ни одной операции** — `/v2/`, `/v2/_catalog`,
   `/v2/<name>/manifests/<tag>` без кредов → `401`.
6. `library/**` не пушится никем, кроме sync; `docker restart` к registry не применяется.

## Известные ловушки

1. **`onDemand` без `content` ломает push.** Без allow-list'а Zot считает промах по
   `HEAD` поводом «сходить наверх», получает `unauthorized` от `docker.io` и отдаёт
   **503** — публикация нового приватного образа падает, оставив залитые слои.
   Поэтому в конфиге обязателен `content: [{"prefix": "library/**"}]`.
2. **Таймаут Zot по умолчанию (60 с) убивает заливку большого слоя.** Монолитный
   `PUT` слоя 5.6 ГБ через nginx упал с **502** через 67 секунд. Лечится
   `http.readTimeout` / `http.writeTimeout` = `2h`. Без этого FR-005 (образ ≥ 6 ГБ)
   недостижим.
3. **Bind-mount файла vs каталога.** Если смонтировать `htpasswd`/`config.json`
   отдельными файлами, правка на хосте (`sed -i`, `scp`, `mv`) до контейнера **не
   доходит** (bind-mount привязан к inode): удалённый пользователь продолжает
   получать `200`. Поэтому монтируется каталог `./registry:/etc/zot:ro`.
4. **Одного `htpasswd` мало.** Без `http.accessControl` любой валидный кред видит и
   читает все репозитории. Изоляция — это политики в `config.json`.
5. **`nginx: client_max_body_size 20M` из `http{}`** даёт `413` на любом слое больше
   20 МБ — в блоке registry он переопределён нулём (без этого push 5.4 ГБ невозможен).
6. **`docker login` — одна запись на хост в одном `DOCKER_CONFIG`**: логин второго
   проекта вытесняет первый, и push падает «access denied». Двум проектам на одной
   машине — разные `DOCKER_CONFIG`.
7. **`python3 -c "import crypt"` есть только на admin** (Python 3.12); на проде
   Ubuntu 26.04 — Python ≥ 3.13 с удалённым модулем. bcrypt генерируется на admin.
8. **`verify-feature retention` требует остановленного сервера** — на живом проде это
   недоступность registry; проверка политики стендовая.
9. **Просрочка сертификата останавливает деплой** (Docker валит handshake), хотя nginx
   продолжает работать. Напоминание — `tools/check-cert-expiry.sh` (порог 45 суток).

## Ссылки

- [Спецификация](../../specs/540-private-docker-registry/spec.md)
- [План](../../specs/540-private-docker-registry/plan.md)
- [Задачи](../../specs/540-private-docker-registry/tasks.md)
- [Контракт имён](../../specs/540-private-docker-registry/contracts/image-naming.md)
- [Контракт транспорта](../../specs/540-private-docker-registry/contracts/registry-transport.md)
- [Контракт команд do.sh](../../specs/540-private-docker-registry/contracts/do-sh-cli.md)
- [Контракт guard'а тегов](../../specs/540-private-docker-registry/contracts/tag-guard.md)
- [Quickstart (проверки)](../../specs/540-private-docker-registry/quickstart.md)
- [Компонента registry в knowledge](../../knowledge/system/infra/registry.md)
- [Раскладка deploy](../../knowledge/system/infra/deploy-overview.md)
- [do.sh](../../knowledge/system/infra/do-sh.md)
- [RUNBOOK эксплуатации](../../deploy/prod-single-host/registry/RUNBOOK.md)

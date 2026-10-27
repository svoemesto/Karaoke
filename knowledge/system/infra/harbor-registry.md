# Component: Harbor — основной каталог контейнерных образов

> **Домен**: system (infrastructure)
> **Компонента**: Harbor v2.14.6 на infra-сервере (`registry.sm-karaoke.ru`) —
> проекты, робот-аккаунты, репликация в Zot, миграция образов.

## Назначение

Основной каталог образов проекта и второй организации: UI, RBAC по проектам,
робот-аккаунты для CI, retention. Прод при этом продолжает тянуть образы из
Zot на своём хосте — Harbor не находится в пути доставки на прод.

## Интерфейсы и Контракты

- **Docker Registry API v2** — `https://registry.sm-karaoke.ru/v2/…`; TLS
  Let's Encrypt, терминирует host-nginx, дальше внутренний nginx и Harbor.
- **Harbor API v2.0** — `/api/v2.0/projects`, `/repositories`, `/artifacts`,
  `/registries`, `/replication/policies`, `/replication/executions`.
- **UI** — `https://registry.sm-karaoke.ru`, вход по учётке проекта.
- **Токен-сервис** — `/service/token?service=harbor-registry&scope=…`;
  для публичных проектов токен выдаётся и анонимно.

## Логика и Алгоритмы

### Проекты и доступ

| Проект | Видимость | Кто пишет | Кто читает |
|---|---|---|---|
| `svoemesto` | private | владелец, робот `robot$svoemesto+svoemesto-ci` | участники проекта |
| `dub2000` | private | владелец организации, робот `robot$dub2000+dub2000-ci` | участники проекта |
| `mirror` | **public** | только admin | все (pull) |
| `library` | public | admin | все (pull) |

Робот-аккаунт имеет права только на свой проект. Имя робота содержит `$`:
в `.env` его нужно писать в одинарных кавычках, иначе bash подставит пустую
переменную и логин упадёт с `401`.

### Репликация Harbor → Zot (живое зеркало)

Три политики, триггер **event-based** (push артефакта):

| Источник (Harbor) | Назначение (Zot) | Учётка Zot |
|---|---|---|
| `svoemesto/**` | `karaoke/**` | `karaoke` |
| `dub2000/**` | `dub2000/**` | `dub2000` |
| `mirror/**` | `mirror/**` | `karaoke` |

Замена namespace — `dest_namespace_replace_count: 1`, поэтому пути на проде
остаются прежними. Ошибки репликации (`401`) обычно означают неверные креды
endpoint'а; лечится `PUT /registries/{id}` с точным секретом.

### Миграция образов

- `skopeo copy --all` — сохраняет OCI-индекс и digest.
- Образы, залитые `docker push` (single-platform), могут иметь digest,
  отличный от Zot; для пинов использовать `skopeo --all`.
- Для отсутствующего в Harbor образа допускается отдельный тег (пример:
  `storage-proxy:0.1.0-f1466a0`), существующие теги не перезаписываются.

## Зависимости

- → **host-nginx** infra-сервера: TLS и проксирование на внутренний nginx.
- → **LXD-контейнер `infra`**: Harbor, его Postgres и Redis.
- → **Zot на прод-хосте**: цель репликации и локальный источник для деплоя.
- → **Бэкап**: `pg_dump` Harbor входит в ежедневный бэкап; блобы не бэкапятся,
 они уже в Zot.

## Известные ловушки

1. **Приватный `mirror`** — чужая учётка получает `401`, клиент показывает
   «no such manifest». Проект `mirror` должен оставаться public.
2. **`$` в имени робота** — обязательны одинарные кавычки в `.env`.
3. **`docker push` vs `skopeo --all`** — разные digest для multi-arch.
4. **GC окно** — во время сборки мусора push недоступен, pull работает;
   окно объявляется заранее.
5. **Креды endpoint'а репликации** — при создании через shell JSON секрет
   может исказиться подстановкой; создавать через JSON-файл/API.

## Связанные ADR | Related ADRs

- [`local-0010-harbor-primary-registry.md`](../../adr/local-0010-harbor-primary-registry.md)

## Связь с другими компонентами

- **[registry.md](registry.md)** — Zot на прод-хосте: зеркало и локальный кэш.
- **[external-infra.md](external-infra.md)** — сервер, на котором живёт Harbor.
- **[deploy-overview.md](deploy-overview.md)** — место registry в деплое.

## Changelog

- **2026-10-27**: Initial. Harbor развёрнут, образы `karaoke/*` и `mirror/*`
  перенесены, репликация в Zot включена.

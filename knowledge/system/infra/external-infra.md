# Component: infra-сервер (LXD-контейнер `infra`)

> **Домен**: system (infrastructure)
> **Компонента**: сторонний сервер `msk-f896` (dub2000), LXD-контейнер `infra`,
> сервисы Forgejo/Plane/Harbor, host-nginx, бэкапы и мониторинг.

## Назначение

Площадка для сервисов, которые не должны занимать прод-хост: git-хостинг,
трекер и каталог образов. Прод (`sm-karaoke.ru`) остаётся отдельным и продолжает
тянуть образы из своего локального Zot.

## Интерфейсы и Контракты

- **Домен `sm-karaoke.ru`** — наш; DNS-зоной управляем мы, TLS-сертификат
  (Let's Encrypt, SAN `git`/`tracker`/`registry`) продлевается на этом же хосте.
- **TLS** терминирует host-nginx dub2000; конфиг — файл в его каталоге
  `/etc/sellper/ingress187/`, правки только in-place (bind-mount файла иначе
  не доезжает).
- **Сервисы и адреса**:
  * `https://git.sm-karaoke.ru` — Forgejo;
  * `https://tracker.sm-karaoke.ru` — Plane;
  * `https://registry.sm-karaoke.ru` — Harbor;
  * внутренний nginx контейнера проксирует на `127.0.0.1` соответствующих портов.
- **SSH**: `sm@185.71.198.17` порт 7487, ключ `id_ed25519`; root-операции через
  `sudo`; внутри — `lxc exec infra`.

## Логика и Алгоритмы

### Раскладка

- LXD 6.10 LTS, пул `dir` на SSD, `lxdbr0` `10.77.0.0/24`, контейнер `infra`
  (16 vCPU, 32 ГБ, `security.nesting=true`).
- Docker внутри контейнера; Plane, Harbor и Forgejo — compose-стеки.
- Толстые данные — HDD `/srv/infra` (bind-mount с `/mnt/backup/infra`):
  блобы Harbor, вложения Plane. БД — SSD.
- Резервный канал доступа к 443 — внутренний адрес `10.77.0.1` (host-nginx);
  job-контейнеры CI получают его через `--add-host` (только на сервере).

### Бэкапы

Ежедневно 03:30 UTC (systemd-таймер): `forgejo dump`, `pg_dump` Plane и Harbor,
конфиги, вложения Plane, host-nginx и сертификаты. Хранение — 7 дней на сервере,
14 дней на `nsa-i9` (забор в 07:30 МСК по cron). Дампы и конфиги — права `600`.

### Мониторинг

Каждые 6 часов: срок сертификата, заполнение дисков, статус контейнеров,
свежесть бэкапа. Пишет в journal; внешних оповещений нет (решение владельца).

## Зависимости

- → **host-nginx dub2000**: TLS и маршрутизация трёх доменов.
- → **`nsa-i9`**: забор бэкапов; CI-раннер живёт там, не здесь.
- → **Прод-хост**: Zot как цель репликации и локальный кэш образов.

## Известные ловушки

1. **Bind-mount файла и `sed -i`** — правка заменяет inode, контейнер видит
   старый файл; писать in-place.
2. **`/srv/infra`** — путь вне каталога бэкапов; в бэкапы не включать.
3. **Docker-пул адресов** — после множества отменённых job'ов сети кончаются;
   на сервере не держать раннер и чистить `docker network prune`.
4. **SSH host key** стороннего сервера сверяется владельцем; не отключать
   проверку молча.

## Связанные ADR | Related ADRs

- [`local-0010-harbor-primary-registry.md`](../../adr/local-0010-harbor-primary-registry.md)
- [`local-0011-forgejo-git-hosting-and-ci.md`](../../adr/local-0011-forgejo-git-hosting-and-ci.md)
- [`local-0012-plane-tracker-migration.md`](../../adr/local-0012-plane-tracker-migration.md)
- [`local-0013-infra-backups-and-monitoring.md`](../../adr/local-0013-infra-backups-and-monitoring.md)

## Связь с другими компонентами

- **[registry.md](registry.md)** — Zot на прод-хосте.
- **[harbor-registry.md](harbor-registry.md)** — Harbor на этом сервере.
- **[ci-tools.md](ci-tools.md)** — где исполняется CI.

## Changelog

- **2026-10-27**: Initial. Forgejo, Plane, Harbor, бэкапы и мониторинг
  развёрнуты на infra-сервере.

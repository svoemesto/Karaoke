# Cutover Runbook — переезд sm-karaoke.ru на один сервер

> **Статус**: решение зафиксировано (wayfinder #171). Исполнение — отдельной спекой.
> **Артефакт решения**: тикет OpenProject #171, карта #165.
> **Источники фактов**: `inventory-addresses.md`, `minio-transfer.md`,
> `ubuntu-2604-compat.md`, `tls-certificate.md`, `ram-profile.md` (эта же директория).

## Топология

| Роль | Старый | Новый |
|---|---|---|
| Прод | `188.119.64.111` (`karaoke-prod`) | `188.127.240.124` (`sm-karaoke`) |
| Storage (MinIO) | `89.125.103.63` | тот же новый хост (локально) |
| Admin | `nsa-i9` (public `185.26.28.109`) | без изменений |
| DNS | reg.ru, NS `ns5/ns6.hosting.reg.ru` | — |

Объёмы: БД **483 МБ** (26 549 песен, 86 юзеров); MinIO бакет `karaoke`
**59 707 объектов / 444.45 GiB**; дампы БД `~126 МБ`/день (retention 7 дней).

## Решения (вход из #165/#171)

- БД: **dump/restore** (готовый `karaoke-db-backup.sh`, `--clean --create --if-exists`);
  физические файлы — fallback.
- Новый хост: Ubuntu 26.04, Docker 29.x + враппер `docker-compose`→`docker compose`,
  host-nginx 1.28.3 + `libnginx-mod-stream`, swap 8 ГБ, `vm.swappiness=10`.
- MinIO: `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`, loopback `127.0.0.1:8890`
  (API) / `8891` (console).
- Раскладка: `/root/Karaoke/deploy` (канон `deploy/prod-single-host/`),
  данные в `/sm-karaoke/system/{Караоке-db,Караоке-storage,dumps}`.
- TLS: переиспользуем GlobalSign **fullchain** (до 2027-04-03) в `/etc/keys/`.
- DNS: TTL apex/www → 300 за 24–48 ч; в окне A → `188.127.240.124`;
  `mail`/MX/SPF/TXT/PTR не трогаем.
- `-Xmx1200m` для `karaoke-web` (было `-Xmx1048m`).

## Разделение ролей (Q45)

| Шаг | Кто |
|---|---|
| Подготовка нового хоста, bulk MinIO, restore БД, подъём контейнеров, smoke по IP | **агент** |
| Остановка старых прод-контейнеров (`188.119.64.111`) | **владелец** (или явное согласие агента) |
| Смена A-записей в панели reg.ru | **владелец** |
| Выключение старых серверов (T+7 дн) | **владелец** |

## Подготовка (T−48 ч … T−1 ч)

1. **TTL**: владелец понижает A `sm-karaoke.ru` и A `www.sm-karaoke.ru` до `300`.
2. **Новый хост**: Docker + враппер, nginx + mod-stream, `/etc/keys/` (fullchain+key),
   swap 8 ГБ, `swappiness=10`, `vm.swappiness`/`ulimit -n` тюнинг.
3. **Канон deploy**: скопировать `/root/Karaoke/deploy` со старого прода в
   `deploy/prod-single-host/` (репо) и на новый хост в `/root/Karaoke/deploy`.
   Правки: образ MinIO → Quay (#172/#177), `image`/порты/пути, `ENABLE_APP_GPU=0`.
4. **Секреты**: `scp` `do.env`/`.env` (в git не трекаются), `docker login svoemestodev`.
5. **Bulk MinIO** (#167): `mc mirror --preserve --overwrite` с нового хоста,
   ~6.1 ч (замерено ~180–200 Мбит/с), параллельность 8–12. Не копировать `.minio.sys`.
6. **Проверить** новый MinIO: `mc ls` видит 59 707 объектов / 444.45 GiB.
7. **Дамп БД** со старого прода + **restore** на новом хосте (Postgres 16).
   Проверка: songs=26 549, users=86, размер ~483 МБ, recordhash-триггеры на месте.
8. **Поднять контейнеры** на новом хосте: `karaoke-db`, `karaoke-storage`,
   `karaoke-web`, `karaoke-public`; host-nginx.
9. **Smoke по IP** (до DNS): `curl --resolve sm-karaoke.ru:443:188.127.240.124 …`
   (полный чеклист — #174).
10. **Systemd-таймеры**: `karaoke-db-backup.timer` (05:00), `karaoke-docker-prune.timer`.

## Окно cutover (ночь, ≤20 мин)

| # | Действие | Команда / где | Ожидание |
|---|---|---|---|
| 1 | Заморозить писателей | владелец: остановить `karaoke-web` на старом проде; на admin — публикацию/рендер | запись прекращена |
| 2 | Финальный инкремент MinIO | `mc mirror --overwrite --remove <src> <dst>` + `mc diff` | пусто, 2–5 мин |
| 3 | Финальный дамп БД | `karaoke-db-backup.sh` на старом проде | новый `.sql.gz` |
| 4 | Restore БД на новом | `docker exec -i karaoke-db psql … < dump` | БД свежая |
| 5 | Рестарт нового стека | `do.sh start_web` / `start_public` / `start_db` / `start_storage` | контейнеры up |
| 6 | Smoke по IP | `curl --resolve …` (#174) | все проверки ок |
| 7 | DNS | владелец: A apex+www → `188.127.240.124` | — |
| 8 | Мониторинг | `access.log` старого прода ~2 ч | трафик прекратился |

**Точка невозврата** — шаг 7 (DNS). После неё откат = вернуть A + перенести
данные, записанные на новом хосте.

## Проверка после cutover (T+0 … T+4 ч)

- `https://sm-karaoke.ru/health` = 200; apex и www.
- Публичные страницы/картинки/аудио, Закрома-stream, SSE `/changerecords`.
- Платежи YooKassa (тест), SmartCaptcha, VK/VK ID OAuth, stemjobs.
- `count(tbl_songs)`, `count(tbl_site_users)`, размер бакета/объектов.
- Логи нового стека: нет OOM, circuit breaker не сработал.

## Rollback

1. Вернуть A apex+www на `188.119.64.111` (старый прод жив).
2. Владелец поднимает старые контейнеры.
3. Данные, записанные на новом хосте за окно проверки, — вручную (БД/MinIO).
4. Корень отката — короткое окно проверки (2–4 ч).

## Вывод из эксплуатации (T+7 дней)

- Владелец выключает `188.119.64.111` и `89.125.103.63` (не удалять).
- Вернуть TTL apex/www `3600`.
- Обновить `knowledge/` (`02-containers.md`, `deploy-overview.md`,
  `storage/domain.md`, `architecture-notes.md`) — см. #172.

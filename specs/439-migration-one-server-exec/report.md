# Report: Исполнение переезда sm-karaoke.ru на один сервер (Pass 441, #178)

**Issue**: OpenProject #178 (wayfinder-карта #165)
**Branch**: `441-migration-execution-report`
**Дата**: 2026-09-24
**Статус**: переезд выполнен, сайт живёт на новом хосте; старые серверы — standby.

## Итог

Прод-сайт `sm-karaoke.ru` переехал с двух серверов (`188.119.64.111` прод +
`89.125.103.63` storage) на **один** хост `188.127.240.124` (`sm-karaoke.ru`,
Ubuntu 26.04, 6 vCPU, 3.8 ГБ RAM, 985 ГБ HDD).

## Хронология

| Шаг | Когда | Результат |
|---|---|---|
| Wayfinder-карта #165 (12 тикетов) | 2026-09-23 | путь расчищен; PR #531 |
| Phase 0/1: канон `deploy/prod-single-host/` + sync-фиксы | 2026-09-23 | PR #532 |
| Phase 2: подготовка хоста (Docker, nginx, swap, секреты, TLS) | 2026-09-23 | готово |
| Phase 3: bulk MinIO 444.47 GiB | 2026-09-23 → 24 | 11 ч 50 мин, `rc=0` |
| Phase 3: restore БД из дампа | 2026-09-23 | 26 549/86 |
| Phase 4: подъём 4 контейнеров + smoke по IP | 2026-09-23 | 15/15 PASS |
| Phase 7: knowledge + smoke-фикс | 2026-09-23 | PR #533 |
| **Cutover** (заморозка, инкремент, финальный дамп, DNS) | 2026-09-24 | сайт на новом IP |

## Что сделано

1. **Хост подготовлен**: Docker Engine 29.1.3 + враппер `docker-compose`→`docker compose`
   (Ubuntu 26.04 без v1-пакета), host-nginx 1.28.3 + `libnginx-mod-stream`,
   swap 8 ГБ (`swappiness=10`), `rsync`, `mc`. Секреты (`do.env`/`.env`, права 600)
   перенесены `scp`, TLS-fullchain (2 серта) в `/etc/keys/`, `docker login`.
2. **MinIO**: bulk `mc mirror` 444.47 GiB / 59 707 объектов за 11 ч 50 мин;
   после — инкремент (догнаны новые объекты). Итог: **445 GiB / 59 735 объектов**
   (= источник). Образ `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`
   (`minio/minio` удалён с Docker Hub). Loopback `127.0.0.1:8890`, public-политика
   `download` выставлена.
3. **БД**: финальный дамп замороженного прода → чистый restore →
   **26 557 песен / 86 юзеров / 24 recordhash-триггера** (= замороженный прод).
4. **Cutover**: старый `karaoke-web` остановлен (запись заморожена, `tbl_events`
   64→64); MinIO догнан; БД восстановлена; новый стек поднят; smoke **15/15** по
   IP; владелец переключил A-записи на `188.127.240.124`.
5. **DNS**: `sm-karaoke.ru` и `www.sm-karaoke.ru` → `188.127.240.124`
   (подтверждено на @ns5, @1.1.1.1, @8.8.8.8). TLS валиден (GlobalSign, SAN
   apex+www, до 2027-04-03).

## Проверки (финал)

| Проверка | Результат |
|---|---|
| `migration-smoke.sh sm-karaoke.ru` | **15/15 PASS** |
| `migration-smoke.sh 188.127.240.124` | **15/15 PASS** |
| `curl https://sm-karaoke.ru/` remote IP | `188.127.240.124` |
| `tbl_songs` / `tbl_site_users` / rh-триггеры | 26 557 / 86 / 24 (= прод) |
| MinIO local = source | 445 GiB / 59 735 |
| Контейнеры | `karaoke-db`, `karaoke-storage`, `karaoke-web`, `karaoke-public` — `Up` |
| Остаточный трафик старого прода | ~1 запрос/75 с, затухает (TTL 3600) |

## Найдено и исправлено по ходу

- **Баг**: `deploy/docker-compose-app.yml` не пробрасывал `DB_REMOTE_HOST` →
  правки `.env` игнорировались; добавлен проброс, дефолты `application.yml`
  (app+web) → `188.127.240.124`.
- Репо-копия `deploy/web-server-deploy/` устарела vs реальный прод → канон
  собран из реального `/root/Karaoke/deploy`.
- `do.sh` на Ubuntu 26.04 требует враппер `docker-compose` (v1-пакет отсутствует).
- `minio/minio` удалён с Docker Hub (404) → Quay.
- `mc mirror --parallel` не существует → `--max-workers`.

## Не сделано / отложено

- **Phase 6 (admin sync)**: `deploy/.env` на admin обновлён
  (`PROD_HOST`/`DB_REMOTE_HOST` → `188.127.240.124`), но `karaoke-app` **не
  перезапускался** (по согласию владельца). Требуется `do.sh stop_app && start_app`
  + проверка «Синхронизации в 1 клик».
- **Phase 8**: старые серверы `188.119.64.111` и `89.125.103.63` — выключить
  владельцу через ≥7 дней (rollback-окно). Пока работают в standby.
- TTL A-записей оставлен 3600 (решение владельца).

## Артефакты

- `deploy/prod-single-host/` — канон однохостового деплоя (PR #532).
- `knowledge/system/{infra/deploy-overview,02-containers}.md`,
  `knowledge/domains/storage/domain.md`, `docs/architecture-notes.md` (PR #533).
- `tools/migration-smoke.sh` — авто-smoke (15 проверок).
- `specs/165-migration-one-server-research/` — research + runbook + checklist.
- `specs/439-migration-one-server-exec/` — спека исполнения.

## Rollback

A-записи вернуть на `188.119.64.111` (старый прод в standby, БД/`karaoke-public`
живы). Данные, записанные на новом хосте за время проверки, — вручную. Старый
`karaoke-web` при необходимости поднять `do.sh start_web`.

# Implementation Plan: Исполнение переезда sm-karaoke.ru на один сервер

**Branch**: `439-migration-one-server-exec` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

## Summary

Исполнение wayfinder-карты #165 по runbook
`specs/165-migration-one-server-research/cutover-runbook.md`: подготовка нового
хоста `188.127.240.124` (Docker/nginx/swap/каталоги/секреты), bulk-перенос
MinIO 446 ГБ, restore БД, подъём стека, smoke по IP; затем cutover с
заморозкой, финальным инкрементом, DNS и приёмкой. Плюс PR-изменения конфигов
(`deploy/prod-single-host/`, `application.yml`, `docker-compose-app.yml`) и
обновление `knowledge/`.

## Technical Context

**Environment**: Ubuntu 26.04 LTS (новый хост), Docker Engine 29.x, Compose v2
**Primary components**: host-nginx 1.28.3 + `libnginx-mod-stream`, MinIO
`quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`, PostgreSQL 16,
`karaoke-web` (`-Xmx1200m`), `karaoke-public`
**Data**: БД 483 МБ (26 549 песен / 86 юзеров); MinIO 59 707 объектов / 444.45 GiB
**Testing**: `tools/migration-smoke.sh` (15 проверок), SQL-сверки, `mc diff`
**Constraints**: окно заморозки ≤20 мин; старая инфраструктура ≥7 дней standby;
Docker Hub `minio/minio` недоступен (404) → Quay
**Scale/Scope**: одна миграция инфраструктуры; без изменений бизнес-логики

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (см. spec.md).
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅ без
  согласия, restart `karaoke-app` ❌ без согласия, прод-контейнеры ❌ без согласия,
  прод deploy/DNS ❌ только владелец. Соблюдается: операционные шаги — за владельцем.
- **Tier-1 Hard Gate — Secrets** — `do.env`/`.env` не в git; `scp` вручную.
- **Tier-1 Hard Gate — Git CI-gate** — ветка + PR + CI.
- **Tier-1 Hard Gate — Knowledge SSoT** — обновление `knowledge/` (FR-012).

Нарушений нет.

## Разделение ролей (из runbook)

| Шаг | Кто |
|---|---|
| Подготовка нового хоста, bulk MinIO, restore, подъём, smoke по IP | агент |
| Правки конфигов в репо (`prod-single-host`, `application.yml`, compose) | агент (PR) |
| Остановка старых прод-контейнеров | владелец |
| `do.sh stop_app && start_app` на admin (sync) | владелец / по согласию |
| DNS в reg.ru | владелец |
| Гашение старых серверов (T+7 дн) | владелец |

## Project Structure

```text
deploy/prod-single-host/                      # NEW: канон deploy нового хоста
  README.md                                   # раскладка + порядок
  do.sh                                       # из реального прода
  docker-compose-database.yml                 # DB_FOLDER=/sm-karaoke/system/Караоке-db
  docker-compose-storage.yml                  # image → quay.io/minio/...:RELEASE...
  docker-compose-web.yml                       # DB_REMOTE_HOST, STORAGE_PROXY_URL
  docker-compose-public.yml
  nginx.conf                                  # host-nginx: stream{5433} + http
  80to8897                                    # sites-enabled (локальный MinIO)
  karaoke-db-backup.{sh,service,timer}        # из прода
  karaoke-docker-prune.{service,timer}
  prune-images.sh
  do.env.example                              # без секретов
  .env.example                                # без секретов
karaoke-app/src/main/resources/application.yml   # MODIFY: db-remote-host default
karaoke-web/src/main/resources/application.yml   # MODIFY: db-remote-host default
deploy/docker-compose-app.yml                    # MODIFY: + DB_REMOTE_HOST проброс
deploy/new_comp/README.md                        # MODIFY: deprecated-маркер
knowledge/system/02-containers.md                # MODIFY: один хост
knowledge/system/infra/deploy-overview.md        # MODIFY: один хост
knowledge/domains/storage/domain.md              # MODIFY: MinIO локальный
docs/architecture-notes.md                       # MODIFY: запись переезда
tools/migration-smoke.sh                         # EXISTS (PR #531)
```

## Phase 0 — Канон deploy в репо (PR, безопасно)

1. Создать `deploy/prod-single-host/` из реального прода `/root/Karaoke/deploy`
   (снят по SSH), с правками:
   - `docker-compose-storage.yml`: `image: quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`;
     порты `127.0.0.1:8890:9000`, `127.0.0.1:8891:9001`.
   - `docker-compose-database.yml`: `DB_FOLDER=/sm-karaoke/system/Караоке-db`.
   - `80to8897`: `/minio/` → `127.0.0.1:8890` + HTTP-кэш 24 ч/404-5мин (оба server).
   - `nginx.conf`: stream allow-list = только `185.26.28.109`; убрать мёртвый `map`.
   - `do.env`/`.env` → `.example` (без секретов); реальные — `scp` на хост.
2. `README.md` с раскладкой и порядком.
3. Пометить `deploy/new_comp/` deprecated.
4. PR #1.

**Checkpoint**: `shellcheck`/`bash -n` на скриптах; `docker compose config` (где
возможно, без запуска); ревью владельца.

## Phase 1 — Правки sync-конфигов (PR, безопасно)

5. `deploy/docker-compose-app.yml`: `+ - DB_REMOTE_HOST=${DB_REMOTE_HOST}`.
6. `application.yml` (app + web): default `db-remote-host` → `188.127.240.124`.
7. PR #2 (можно объединить с Phase 0).

**Checkpoint**: `:karaoke-app:compileKotlin :karaoke-web:compileKotlin`; ktlint.

## Phase 2 — Подготовка нового хоста (операционно, агент)

8. Установить Docker Engine 29.x, враппер `docker-compose`, nginx +
   `libnginx-mod-stream`; `docker login svoemestodev`.
9. Swap 8 ГБ + `vm.swappiness=10`; `ulimit -n` тюнинг.
10. Каталоги `/sm-karaoke/system/{Караоке-db,Караоке-storage,dumps}`.
11. `scp` `do.env`/`.env` (реальные); `/etc/keys/` fullchain+key.
12. Скопировать `deploy/prod-single-host/*` → `/root/Karaoke/deploy/`.
13. `docker network create deploy_karaokenet`.

**Checkpoint**: `do.sh ps` (пусто), `nginx -t` (после сертификата).

## Phase 3 — Перенос данных (операционно, агент)

14. Bulk MinIO: `mc mirror --preserve --overwrite` (~6.1 ч).
15. Проверка: `mc ls --recursive | wc -l` = 59 707; `mc du` ≈ 444.45 GiB.
16. Дамп прод-БД (`karaoke-db-backup.sh`), `scp` дампа, restore на новом хосте.
17. Проверка БД: songs=26 549, users=86, recordhash-триггеры.

**Checkpoint**: `mc diff` (только ожидаемый хвост), SQL-сверки.

## Phase 4 — Подъём и предварительный smoke (операционно, агент)

18. `do.sh start_db`, `start_storage`; проверить MinIO локально.
19. `do.sh start_web`, `start_public`.
20. `tools/migration-smoke.sh 188.127.240.124` → 15/15 PASS.
21. Systemd-таймеры backup/prune.

**Checkpoint**: SC-001 (до DNS), SC-005.

## Phase 5 — Cutover (окно, владелец + агент)

22. Владелец: TTL → 300 (T−48…24 ч).
23. Владелец: остановить старые `karaoke-web` + заморозить admin-публикацию.
24. Агент: финальный `mc mirror --overwrite --remove` + `mc diff` (пусто).
25. Агент: финальный дамп + restore.
26. Агент: рестарт нового стека; smoke по IP.
27. Владелец: DNS A apex+www → `188.127.240.124`.
28. Наблюдение: `access.log` старого хоста ~2 ч.

**Checkpoint**: SC-003 (окно ≤20 мин), SC-004.

## Phase 6 — Admin/sync (по согласию)

29. Владелец/по согласию: `do.sh stop_app && start_app` на admin.
30. Ручная «Синхронизация в 1 клик»; проверить `AutoOneClickSyncScheduler`.

**Checkpoint**: SC-006.

## Phase 7 — Knowledge & docs (PR)

31. Обновить `knowledge/system/02-containers.md`, `deploy-overview.md`,
    `storage/domain.md`, `docs/architecture-notes.md` (однохостовая топология).
32. PR #3.

**Checkpoint**: knowledge-линтеры зелёные; SC-007.

## Phase 8 — Вывод из эксплуатации (владелец, T+7 дн)

33. Владелец выключает старые серверы; TTL обратно 3600.

## Dependencies

Phase 0/1 (PR) → Phase 2 → 3 → 4 → 5 → 6 → 7 → 8.

## Risks

- **OOM** на 3.8 ГБ → `-Xmx1200m` + swap 8 ГБ (FR-007); мониторинг первые 24 ч.
- **Обрыв bulk MinIO** → идемпотентный `mc mirror`, повтор; `mc diff` — критерий.
- **Секрет в git** → `.example` только; реальные — `scp`.
- **Окно затягивается** → bulk/restore/smoke сделаны заранее; в окне только инкремент+DNS.

## Ready for implementation

Phase 0/1 (PR) и Phase 2-4 (подготовка/данные) — стартуют без owner-gate.
Phase 5+ — по согласию владельца (cutover/DNS/старые контейнеры).

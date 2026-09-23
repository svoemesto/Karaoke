# Tasks: Исполнение переезда sm-karaoke.ru на один сервер (Pass 439, #178)

**Input**: `specs/439-migration-one-server-exec/{spec,plan}.md`
**Runbook**: `specs/165-migration-one-server-research/cutover-runbook.md`

## Phase 0: Канон deploy в репо (PR, безопасно)

- [ ] T001 Создать `deploy/prod-single-host/` из реального прода (`/root/Karaoke/deploy`):
  `do.sh`, `docker-compose-{database,storage,web,public}.yml`, `nginx.conf`,
  `80to8897`, `karaoke-db-backup.{sh,service,timer}`,
  `karaoke-docker-prune.{service,timer}`, `prune-images.sh`,
  `nginx_karaoke-public.conf`, `karaoke-db/` (init-скрипты).
- [ ] T002 `docker-compose-storage.yml`: образ →
  `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`; порты
  `127.0.0.1:8890:9000`, `127.0.0.1:8891:9001`.
- [ ] T003 `docker-compose-database.yml`: `DB_FOLDER=/sm-karaoke/system/Караоке-db`.
- [ ] T004 `80to8897`: `/minio/` → `127.0.0.1:8890` + HTTP-кэш 24 ч/404-5мин (оба server).
- [ ] T005 `nginx.conf`: stream allow-list = только `185.26.28.109`; убрать мёртвый `map`.
- [ ] T006 `do.env.example`/`.env.example` без секретов; реальные — `scp`.
- [ ] T007 `README.md` — раскладка и порядок.
- [ ] T008 Пометить `deploy/new_comp/` deprecated.

**Checkpoint**: `bash -n` на скриптах; ревью.

## Phase 1: Правки sync-конфигов (PR, безопасно)

- [ ] T009 `deploy/docker-compose-app.yml`: `+ - DB_REMOTE_HOST=${DB_REMOTE_HOST}`.
- [ ] T010 `karaoke-app/.../application.yml`: default `db-remote-host` → `188.127.240.124`.
- [ ] T011 `karaoke-web/.../application.yml`: default `db-remote-host` → `188.127.240.124`.

**Checkpoint**: `:karaoke-app:compileKotlin :karaoke-web:compileKotlin`; ktlint.

## Phase 2: Подготовка нового хоста (операционно, агент)

- [ ] T012 Docker Engine 29.x + враппер `docker-compose`; `docker login svoemestodev`.
- [ ] T013 nginx 1.28.3 + `libnginx-mod-stream`; `/etc/keys/` (fullchain+key).
- [ ] T014 swap 8 ГБ, `vm.swappiness=10`, `ulimit -n` тюнинг.
- [ ] T015 Каталоги `/sm-karaoke/system/{Караоке-db,Караоке-storage,dumps}`.
- [ ] T016 `scp` `do.env`/`.env`; `deploy/prod-single-host/*` → `/root/Karaoke/deploy/`.
- [ ] T017 `docker network create deploy_karaokenet`.

## Phase 3: Перенос данных (операционно, агент)

- [ ] T018 Bulk MinIO: `mc mirror --preserve --overwrite` (~6.1 ч).
- [ ] T019 Проверка: 59 707 объектов / ≈444.45 GiB.
- [ ] T020 Дамп прод-БД + restore на новом хосте.
- [ ] T021 Проверка БД: songs=26 549, users=86, recordhash-триггеры.

## Phase 4: Подъём и предварительный smoke (операционно, агент)

- [ ] T022 `do.sh start_db`, `start_storage`, `start_web`, `start_public`.
- [ ] T023 `tools/migration-smoke.sh 188.127.240.124` → 15/15 PASS.
- [ ] T024 Systemd-таймеры backup/prune.

## Phase 5: Cutover (окно, владелец + агент)

- [ ] T025 [OWNER] TTL → 300 (T−48…24 ч).
- [ ] T026 [OWNER] Остановить старые `karaoke-web`; заморозить admin-публикацию.
- [ ] T027 [AGENT] Финальный `mc mirror --overwrite --remove` + `mc diff` (пусто).
- [ ] T028 [AGENT] Финальный дамп + restore.
- [ ] T029 [AGENT] Рестарт нового стека; smoke по IP.
- [ ] T030 [OWNER] DNS A apex+www → `188.127.240.124`.
- [ ] T031 [AGENT] Наблюдение `access.log` старого хоста ~2 ч.

## Phase 6: Admin/sync (по согласию)

- [ ] T032 [OWNER/согласие] `do.sh stop_app && start_app` на admin.
- [ ] T033 Ручная синхронизация; проверить `AutoOneClickSyncScheduler`.

## Phase 7: Knowledge & docs (PR)

- [ ] T034 Обновить `knowledge/system/02-containers.md`, `deploy-overview.md`,
  `knowledge/domains/storage/domain.md`, `docs/architecture-notes.md`.

## Phase 8: Вывод из эксплуатации (владелец, T+7)

- [ ] T035 [OWNER] Выключить старые серверы; TTL → 3600.

## Dependencies

Phase 0/1 → 2 → 3 → 4 → 5 → 6 → 7 → 8.

## Готово к исполнению

Phase 0/1 — в этом PR (`439`). Phase 2+ — по шагам, с owner-gate на Phase 5+.

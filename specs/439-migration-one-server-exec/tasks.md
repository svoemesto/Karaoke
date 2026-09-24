# Tasks: Исполнение переезда sm-karaoke.ru на один сервер (Pass 439, #178)

**Input**: `specs/439-migration-one-server-exec/{spec,plan}.md`
**Runbook**: `specs/165-migration-one-server-research/cutover-runbook.md`

## Phase 0: Канон deploy в репо (PR, безопасно)

- [x] T001 Создать `deploy/prod-single-host/` из реального прода (`/root/Karaoke/deploy`): done (PR #532).
- [x] T002 `docker-compose-storage.yml`: образ → `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`; loopback. done.
- [x] T003 `docker-compose-database.yml`: `DB_FOLDER=/sm-karaoke/system/Караоке-db`. done.
- [x] T004 `80to8897`: `/minio/` → `127.0.0.1:8890` + HTTP-кэш. done.
- [x] T005 `nginx.conf`: stream allow только `185.26.28.109`; убран мёртвый `map`. done.
- [x] T006 `do.env.example`/`.env.example` без секретов. done.
- [x] T007 `README.md`. done.
- [x] T008 `deploy/new_comp/` deprecated. done.

## Phase 1: Правки sync-конфигов (PR, безопасно)

- [x] T009 `docker-compose-app.yml`: `+ DB_REMOTE_HOST=${DB_REMOTE_HOST}`. done.
- [x] T010 `karaoke-app/.../application.yml`: default → `188.127.240.124`. done.
- [x] T011 `karaoke-web/.../application.yml`: default → `188.127.240.124`. done.

## Phase 2: Подготовка нового хоста (операционно, агент)

- [x] T012 Docker 29.1.3 + враппер `docker-compose`; `docker login`. done.
- [x] T013 nginx 1.28.3 + `libnginx-mod-stream`; `/etc/keys/` (fullchain). done.
- [x] T014 swap 8 ГБ, `swappiness=10`. done.
- [x] T015 Каталоги `/sm-karaoke/system/{Караоке-db,Караоке-storage,dumps}`. done.
- [x] T016 `scp` `do.env`/`.env`; deploy-файлы. done.
- [x] T017 `docker network create deploy_karaokenet`. done.

## Phase 3: Перенос данных (операционно, агент)

- [x] T018 Bulk MinIO 444.47 GiB, 11 ч 50 мин, rc=0. done.
- [x] T019 Проверка + инкремент: 445 GiB / 59 735 объектов. done.
- [x] T020 Дамп прод-БД + restore. done.
- [x] T021 Проверка БД: 26 557 / 86 / 24 rh-триггера. done.

## Phase 4: Подъём и предварительный smoke (операционно, агент)

- [x] T022 4 контейнера подняты. done.
- [x] T023 `migration-smoke.sh 188.127.240.124` → 15/15. done.
- [x] T024 Systemd-таймеры backup/prune. done.

## Phase 5: Cutover (окно, владелец + агент)

- [x] T025 TTL оставлен 3600 (решение владельца).
- [x] T026 Старый `karaoke-web` остановлен; запись заморожена. done.
- [x] T027 Инкремент `mc mirror` + проверка (все объекты на месте). done.
- [x] T028 Финальный дамп + restore (26 557/86). done.
- [x] T029 Рестарт нового стека; smoke 15/15 по IP. done.
- [x] T030 DNS A apex+www → `188.127.240.124` (владелец). done.
- [x] T031 Остаточный трафик ~1 req/75s, затухает (TTL 3600). done.

## Phase 6: Admin/sync (по согласию)

- [ ] T032 `do.sh stop_app && start_app` на admin — **отложено** (нужно согласие).
- [ ] T033 Ручная синхронизация + `AutoOneClickSyncScheduler` — после T032.

## Phase 7: Knowledge & docs (PR)

- [x] T034 Обновлены knowledge + architecture-notes (PR #533). done.

## Phase 8: Вывод из эксплуатации (владелец, T+7)

- [ ] T035 [OWNER] Выключить старые серверы (standby ≥7 дней).

## Dependencies

Phase 0/1 → 2 → 3 → 4 → 5 → 6 → 7 → 8.

## Готово к исполнению

Phase 0–5, 7 — выполнены. Phase 6 отложена (нужен рестарт `karaoke-app`).
Phase 8 — за владельцем (T+7).

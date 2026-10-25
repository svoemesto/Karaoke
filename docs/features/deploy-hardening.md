# Feature: Hardening deploy-контура (PKG-S2-08, аудит #336, OpenProject #371)

> **Status**: active
> **Feature Key**: deploy-hardening
> **Last Updated**: 2026-10-25
> **Spec**: [specs/371-pkg-s2-08/report.md](../../specs/371-pkg-s2-08/report.md),
> [specs/393-pkg-s3-21/report.md](../../specs/393-pkg-s3-21/report.md)
> **Created**: 2026-10-24
> **История**: аудит #336 (порции P-70, P-71, P-72, P-73; 15 записей
> BR-039, BR-040, BR-041, BR-043, BR-534, BR-539, BR-542, BR-543, BR-544,
> BR-545, BR-546, BR-554, BR-555, BR-556, BR-557) → пакет PKG-S2-08,
> OpenProject #371, ветка `579-pkg-s2-08`.
> **Продолжение**: волна E, пакет PKG-S3-21 (15 S3-записей BR-042, BR-044,
> BR-045, BR-046, BR-047, BR-535, BR-536, BR-537, BR-538, BR-547, BR-548,
> BR-549, BR-550, BR-559, BR-560), OpenProject #393, ветка `611-pkg-s3-21`.

## Что делает

Пакет закрывает 15 S2-находок аудита в deploy-контуре: миграции,
секреты, платёжную историю, push-ошибки, бэкапы, сетевую экспозицию,
TLS, ротацию логов, compose-окружение и исполнение скриптов на хосте.
Поведение прода по умолчанию **не меняется**: все переключатели
(TLS, порты, пароли) имеют те же значения, что были, но заданы явно и
проверяются guard'ом.

| BR | Было | Стало |
|---|---|---|
| 039 | `02` повторно добавлял `result_text`/индекс; `18` делал `ALTER TABLE tbl_authors`, которой никто не создавал | идемпотентные `IF NOT EXISTS` + bootstrap `tbl_authors` и recordhash-триггера в `18` |
| 040 | `WHISPER_API_KEY` / `SEARXNG_SECRET` в git (рабочие) | `${VAR:?…}` из `deploy/do.env`, шаблон `deploy/do.env.example` |
| 041 | `tbl_subscriptions.site_user_id ON DELETE CASCADE` | `ON DELETE RESTRICT` (в `15` для новых БД + миграция `51` для существующих) |
| 043 | push логина/образа не проверялся, «up to date» = WARN | `|| return 1` на каждом push/логине; «up to date» — `exit 1` |
| 534 | `46` переопределяла recordhash-функцию и теряла 5 колонок | 17 колонок, канонический порядок, самодостаточная миграция, backfill |
| 539 | `pg_dump --no-password` без пароля → дампов нет | пароль по stdin в одноразовый `.pgpass`, дамп пишется через `.tmp` + `mv` |
| 542 | трекер в `network_mode: host` (БД 5435 и UI 8080 на 0.0.0.0) | bridge-сеть, порты только на `127.0.0.1`, `DATABASE_URL` по имени сервиса |
| 543 | `ExecStartPre=flock` отпускал блокировку до старта бэкапа | `ExecStart=/usr/bin/flock -n … script` |
| 544 | `ssl_protocols TLSv1 TLSv1.1 …`, `server_tokens off` закомментирован | `TLSv1.2 TLSv1.3`, `server_tokens off` в обоих nginx |
| 545 | stream-прокси 5433/9000 без TLS, Postgres `ssl=off` | управляемый `DB_SSL_ON` (default off), `ssl_cert_file`/`ssl_key_file`, описан TLS-вариант stream-прокси |
| 546 | у прод-контейнеров нет ротации логов | `logging: json-file, max-size 50m, max-file 5` у всех 4 сервисов + порог 85 % в `prune-images.sh` |
| 554 | `environment:` — смесь отображения и списка, переменные БД не доходили | список `- VAR=value`, как в основной схеме |
| 555 | watcher исполнял любой `.sh` из общей с контейнерами директории (`chmod +x` на месте) | только `<uuid>.sh`, перенос в приватный каталог 700, проверки владельца/прав/размера |
| 556 | БД new_comp-контура слушала `0.0.0.0:8832` | `127.0.0.1:8832:5432` |
| 557 | JDBC без `sslmode`, MinIO по `http://` | `DB_REMOTE_SSL_MODE` (default `prefer`, целевое `verify-full`), `DB_SSL_ON`, требование HTTPS для MinIO зафиксировано |
| 042 | сборка тегировала `:${BUILD_VERSION}`, а прод тянул литерал `:1` | `IMAGE_TAG` — единый тег: `deploy_*.sh` передаёт его на прод по ssh, compose подставляет `${IMAGE_TAG:-1}`; предыдущий тег — в `.version.prev` для откката |
| 044 | семь миграций `tbl_site_users` держали каждая свой набор колонок; повторный прогон старой сужал md5 | канонический `deploy/recordhash_site_users.sql` (19 колонок) + все семь миграций повторяют его целиком, каждая сама добавляет колонки; миграция `56` доставляет формулу на существующие БД |
| 045 | GIN-индекс построен по константе `'result_text'` (и на `tbl_settings`, а поиск идёт по `tbl_songs`) | индекс по колонке в `01`/`02`, `02` пересоздаёт его через DROP+CREATE, `54` пересоздаёт на существующих БД |
| 046 | `restore_db`/`create_clear_db` искали `/dumps` без mount, `-U postgres`, `exec -it`, не проверяли код возврата | mount `./karaoke-db/dumps:/dumps`, `-U ${DB_LOCAL_POSTGRES_USER}`, `exec -i`, проверка кода возврата до `announce` |
| 047 | `minio/minio:latest` и устаревшие `MINIO_ACCESS_KEY/SECRET_KEY` | пин тегом + `@sha256` (registry-зеркало, как прод), `MINIO_ROOT_USER/PASSWORD` в локальном и прод-compose |
| 535 | `UPDATE … WHERE id_status >= 3` переводил незавершённые песни (3/4/5) в READY | `WHERE id_status IN (3, 4, 5)` — повторный запуск действительно no-op |
| 536 | INSERT песни не переносил `album_id` (и поздние колонки) | закрыто пакетом PKG-S2-10 (BR-108), здесь — регрессия в guard'е |
| 537 | `tbl_stem_jobs.site_user_id ON DELETE CASCADE` → объекты MinIO остаются сиротами | `ON DELETE RESTRICT` в `22` + миграция `55`; удаление — приложение (сначала объекты) |
| 538 | `44` обещала `CREATE OR REPLACE TRIGGER`, а делала `CREATE TRIGGER` → падала при повторе | `DROP TRIGGER IF EXISTS` перед `CREATE TRIGGER`, шапка приведена в соответствие |
| 547 | у прод-`karaoke-web` нет healthcheck/лимитов, `-XX:HeapDumpPath=/dumps` без mount | healthcheck (HTTP 200 `/api/public/authors-tiles`), `mem_limit`/`cpus`, mount `./karaoke-db/dumps:/dumps` |
| 548 | TTS-модель качалась без сверки и грузилась через pickle | sha256 модели зафиксирован, загрузка во временный файл + `os.replace` после сверки, проверка перед `load_pickle` |
| 549 | у SPA-nginx публичного сайта нет защитных заголовков | `X-Frame-Options`/`nosniff`/`Referrer-Policy` в server и в каждом location со своими `add_header`; guard расширен на этот файл |
| 550 | `client_max_body_size 1024m` на публичном контуре | `200m` — как `spring.servlet.multipart` и host-nginx |
| 559 | new_comp `do.sh`: `source .env` без проверки, без fail-fast | `set -euo pipefail`, проверка наличия обоих env-файлов, ошибки compose валят скрипт |
| 560 | new_comp compose: нет `restart`/healthcheck/лимитов | `restart: unless-stopped`, healthcheck, `mem_limit`/`cpus` у app/web, `restart` у webvue/webvue3 |

## Зачем

Находки аудита #336 — не стиль, а работающие дефекты:

- **бэкапов прод-БД не существовало** (BR-539): `pg_dump --no-password`
  против `scram-sha-256` из конфигурации доступа (`pg_hba`) образа `postgres:16` падал с
  `fe_sendauth: no password supplied`, скрипт удалял пустой дамп;
- **платёжная история удалялась вместе с пользователем** (BR-041):
  каскад уносил `PAID`-записи с `yookassa_payment_id`, нужные для чеков
  54-ФЗ и сверки с ЮKassa;
- **«деплой успешен» при недоставленном образе** (BR-043): неудачный
  push не валил скрипт, прод тянул предыдущий `:1`, а «Image is up to
  date» печаталось как WARN;
- **БД и UI трекера слушали все интерфейсы** admin-машины, у которой
  публичные IP (BR-542/BR-556);
- **прод-данные и пароль БД шли через интернет открытым текстом**
  (BR-545/BR-557): stream-прокси без TLS + `sslmode=prefer` (драйвер
  молча деградирует в plaintext);
- **исполнение кода на хосте из общего с контейнерами каталога**
  (BR-555).

## Как работает

- **Миграции**: `deploy/karaoke-db/` монтируется в
  `/docker-entrypoint-initdb.d`; entrypoint выполняет файлы по порядку с
  `ON_ERROR_STOP=1`. Поэтому каждый файл идемпотентен, а `18` создаёт
  `tbl_authors` (её не создавал ни один файл каталога). Миграция `51`
  пересоздаёт FK `tbl_subscriptions.site_user_id` с `RESTRICT` на уже
  существующих БД (применять вручную на LOCAL и PROD отдельно).
- **Секреты**: `deploy/do.sh` подгружает `deploy/do.env` через
  `set -a`, compose подставляет `${WHISPER_API_KEY}` /
  `${SEARXNG_SECRET}`. `${VAR:?…}` — fail-closed: без значения
  `docker compose up` падает с понятным сообщением. SearXNG: в
  `settings.yml` лежит плейсхолдер `ultrasecretkey`, entrypoint образа
  заменяет его на `$SEARXNG_SECRET` (файл в bind-mount, см. ловушки).
- **Push/деплой**: каждая `docker image push` и `do_registry_login`
  проверяют код возврата; `deploy_web.sh` / `deploy_public.sh` считают
  «Image is up to date» ошибкой доставки (`exit 1`).
- **Бэкапы**: `karaoke-db-backup.sh` кладёт пароль в одноразовый
  `.pgpass` внутри контейнера (stdin, umask 077), `PGPASSFILE` — в
  окружении `pg_dump`; секрет не виден в `ps`. Блокировку трекера
  держит `flock` вокруг всего прогона.
- **Сеть/TLS**: порты локальных сервисов — `127.0.0.1:<host>:<container>`;
  серверный TLS Postgres включается `DB_SSL_ON=on` +
  `deploy/prod-single-host/certs/postgres/`, клиент — `DB_REMOTE_SSL_MODE`.
- **Логи**: `logging.max-size/max-file` у каждого сервиса прод-compose;
  `prune-images.sh` дополнительно валит юнит при занятости диска ≥ 85 %.
- **Тег образа**: `deploy/do.sh` собирает и пушит `${BUILD_VERSION}` (из `deploy/.version`
  или окружения); `deploy_web.sh`/`deploy_public.sh` передают этот тег на прод
  (`BUILD_VERSION=<тег> bash do.sh pull_web`), прод-`do.sh` экспортирует `IMAGE_TAG`, а
  compose-файлы подставляют `${IMAGE_TAG:-1}`. Предыдущий тег сохраняется в `.version.prev`.
- **recordhash `tbl_site_users`**: канонический `deploy/recordhash_site_users.sql`; миграции
  12/13/14/17/21/40/45 повторяют ту же формулу и сами добавляют все 10 колонок, чтобы их
  бэкфилл работал на чистой БД; `56` доставляет формулу на существующие БД.
- **Каскады и хранилище**: `tbl_stem_jobs.site_user_id` — `RESTRICT` (удаление пользователя с
  заданиями отклоняется; объекты и строки убирает `StemJobCleanup` по `expires_at`).
- **Watcher**: `createScriptForHost` кладёт `<uuid>.sh` в
  `/sm-karaoke/system/scriptsFromDocker`; watcher принимает только это
  имя, переносит файл в `/tmp/karaoke-script-exec` (700), проверяет
  владельца/права/размер и только потом запускает.

## Инварианты

1. Любой файл `deploy/karaoke-db/*.sql` MUST быть идемпотентным и MUST NOT
   падать на БД, созданной из предыдущих файлов каталога.
2. В отслеживаемых файлах репозитория MUST NOT быть значений
   `WHISPER_API_KEY`, `SEARXNG_SECRET` и прочих секретов (`grep` по
   `deploy/` — пусто).
3. `recordhash`-функция `tbl_authors` MUST покрывать все 17 колонок в
   каноническом порядке, зафиксированном в `46` и `18`.
4. Денежные таблицы MUST NOT терять историю при удалении смежной сущности
   (`ON DELETE RESTRICT`, не `CASCADE`).
5. Каждый `docker push`/логин в `do.sh` MUST проверять код возврата;
   «up to date» в деплое MUST быть ошибкой.
6. Пароли MUST NOT попадать в argv (ни `docker exec -e`, ни URL в
   командной строке).
7. Порты БД/UI локальных сервисов MUST публиковаться только на
   `127.0.0.1`.
8. `ssl_protocols` MUST NOT содержать TLS 1.0/1.1; `server_tokens off`
   MUST быть включён.
9. У каждого сервиса прод-compose MUST быть `logging` с `max-size` и
   `max-file`.
10. `environment:` в compose MUST быть либо списком, либо отображением —
    без смешения.
11. Watcher MUST NOT исполнять файл из общей директории: только
    `<uuid>.sh` после переноса в приватный каталог.

12. Тег образа MUST быть один на сборку, push и pull: литерал `:1` в прод-скриптах и
    compose запрещён; предыдущий тег MUST сохраняться для откката.
13. `recordhash`-функция `tbl_site_users` MUST совпадать с
    `deploy/recordhash_site_users.sql` во всех миграциях, которые её переопределяют.
14. GIN-индекс `idx_gin_result_text` MUST строиться по колонке `result_text`, а не по
    строковому литералу.
15. FK на владельца у таблиц, которым соответствуют объекты MinIO, MUST быть
    `ON DELETE RESTRICT`, а не `CASCADE`.
16. Образы инфраструктуры в отслеживаемых compose MUST быть запинены (тег + digest);
    креды MinIO — `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`.
17. Скрипт, читающий `.env`, MUST падать при отсутствии файла (никакой работы на пустых
    значениях); у сервисов new_comp MUST быть политика перезапуска и cgroup-потолок.

Все 17 инвариантов проверяют `bash tools/check-deploy-hardening.sh`
(95 проверок, по одной строке PASS/FAIL на каждую запись пакета; `--repo
PATH` — чужой checkout, `--self-test` — проверка самих правил),
`python3 tools/check-recordhash-consistency.py` (инвариант 13) и
`python3 tools/check-security-headers.py` (BR-549, файл SPA-nginx).

## Известные ловушки

- **`searxng-settings/settings.yml` мутируется entrypoint'ом.** Каталог
  смонтирован bind-mount'ом, а entrypoint делает `sed` плейсхолдера
  `ultrasecretkey` на `$SEARXNG_SECRET` прямо в файле — после старта
  контейнера **хостовый** файл содержит реальный секрет. Не коммитить
  его в таком состоянии: `git checkout -- deploy/searxng-settings/settings.yml`;
  guard ловит отличие `secret_key` от плейсхолдера.
- **`environment`-смесь не является ошибкой YAML.** `WORK_IN_CONTAINER: 1`
  + список ниже склеивается в одно значение, и compose молча не передаёт
  ни одной переменной. Проверять `docker compose config`.
- **«Image is up to date» — не успех.** При неизменном digest (например,
  сборка не менялась) деплой теперь считается неуспешным: артефакт не
  доставлен. Отличать от настоящего успеха — по `Downloaded newer image`.
- **`15_monetization.sql` не переигрывается** (`CREATE TABLE IF NOT
  EXISTS`) — на существующей БД `RESTRICT` появляется только после
  применения `51`.
- **Смена порядка колонок в recordhash-функции** делает хэши несравнимыми
  между LOCAL и SERVER: каждая сторона считает свой md5, sync видит
  расхождение по всем строкам. Менять набор/порядок можно только вместе
  на обеих БД и с backfill.
- **`docker exec -e PGPASSWORD=…`** светит пароль в `ps`/`docker inspect` —
  использовать stdin + `.pgpass`.
- **Повторное применение старой миграции `tbl_site_users` — регресс.** До BR-044 каждая из
  семи миграций знала только «свои» колонки: прогон `12`/`13`/… после `45` молча сужал md5.
  Теперь все семь объявляют полный набор, но порядок колонок менять всё равно нельзя —
  только в конец и с backfill на обеих БД.
- **`tbl_songs_sync` и `album_id`.** Поздние колонки песни должны попадать и в INSERT, и в
  sync-таблицу: иначе push падает (`column … does not exist`) или изменение не уезжает.
  Регрессия закрыта guard'ом (BR-536).
- **MinIO-образ тянется из нашего registry.** `minio/minio` удалён с Docker Hub (404), пин —
  платформенный манифест зеркала (`sha256:a1a8bd4a…`); на машине без доступа к registry
  `docker compose pull` для storage не сработает — это ожидаемо.
- **Healthcheck `karaoke-web` дергает `/api/public/authors-tiles`**, а не `/stats`:
  `/stats` пишет событие в `tbl_events` (побочный эффект каждые 30 с). Эндпоинта
  `/actuator/health` в `karaoke-web` нет (нет зависимости actuator).
- **`/dumps` нужен и web-контейнеру** (`-XX:HeapDumpPath`), и БД (`restore_db`): дампы —
  персональные данные, каталог `deploy/karaoke-db/dumps/` в `.gitignore` (держится
  `.gitkeep`). Не коммитить дампы.
- **Каталог watcher'а общий с контейнерами**: пока контейнерный процесс —
  root на bind-mount, права каталога его не останавливают. Полное
  решение (сервисный пользователь + вынос mount'ов) — за владельцем.

## Ссылки

- [specs/371-pkg-s2-08/report.md](../../specs/371-pkg-s2-08/report.md) — отчёт пакета S2-08.
- [specs/393-pkg-s3-21/report.md](../../specs/393-pkg-s3-21/report.md) — отчёт пакета S3-21.
- [knowledge/system/infra/sql-migrations.md](../../knowledge/system/infra/sql-migrations.md) — Решения 5 и 6.
- [knowledge/system/infra/sql-migrations-monetization.md](../../knowledge/system/infra/sql-migrations-monetization.md) — миграция 51.
- [knowledge/system/infra/deploy-overview.md](../../knowledge/system/infra/deploy-overview.md) — раздел «Hardening S2».
- [knowledge/domains/persistence/domain.md](../../knowledge/domains/persistence/domain.md) — инвариант recordhash.
- [knowledge/guidelines/security-headers.md](../../knowledge/guidelines/security-headers.md) — соседний пакет PKG-S2-09 (заголовки).
- `tools/check-deploy-hardening.sh` — guard пакета.
